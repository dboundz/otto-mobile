package to.ottomot.driftd.car

import android.content.Context
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.mapbox.common.Cancelable
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.androidauto.MapboxCarMapObserver
import com.mapbox.maps.extension.androidauto.MapboxCarMapSurface
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.DriveSpeedGradient
import to.ottomot.driftd.MapDriveCamera
import to.ottomot.driftd.OttoShellUiState
import to.ottomot.driftd.RouteMapPoint
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.race.coordinateOrNull
import to.ottomot.driftd.installRouteMapLine
import to.ottomot.driftd.installRouteSpeedGradient
import to.ottomot.driftd.isRouteDriveCompleted
import to.ottomot.driftd.lineCoordinatesFromSavedRoute
import to.ottomot.driftd.map.MapDiscoveryMarkerKind
import to.ottomot.driftd.map.MapDiscoveryMarkerLOD
import to.ottomot.driftd.map.MapDiscoveryMarkerPresentation
import to.ottomot.driftd.map.eventProximityGroupsForMap
import to.ottomot.driftd.map.groupNearbyPresence
import to.ottomot.driftd.map.visibleLatitudeDeltaDegrees
import to.ottomot.driftd.mapPointsFromSavedRouteForDrive
import to.ottomot.driftd.ottoUserIdsEqual
import to.ottomot.driftd.core.event.eventVenueLatLng
import to.ottomot.driftd.removeRouteMapLine
import to.ottomot.driftd.removeRouteSpeedGradient
import to.ottomot.driftd.setOttoTrafficLayersVisible

private const val ANDROID_AUTO_MAP_TAG = "AndroidAutoMap"
private const val CAR_PRESENCE_MOTION_MIN_ANIMATION_MS = 900L
private const val CAR_PRESENCE_MOTION_MAX_ANIMATION_MS = 6_500L
private const val CAR_PRESENCE_MOTION_INTERVAL_MULTIPLIER = 1.12
private const val CAR_PRESENCE_MOTION_SNAP_DEGREES = 0.05

private data class CarPresenceMotionTrack(
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val startMs: Long,
    val endMs: Long,
    val sourceKey: String,
    val wallUpdatedAtMs: Long,
) {
    fun positionAt(nowMs: Long): Pair<Double, Double> {
        if (endMs <= startMs || nowMs >= endMs) return endLat to endLng
        if (nowMs <= startMs) return startLat to startLng
        val t = ((nowMs - startMs).toDouble() / (endMs - startMs).toDouble()).coerceIn(0.0, 1.0)
        return (startLat + (endLat - startLat) * t) to
            (startLng + (endLng - startLng) * t)
    }
}

private data class ProjectedViewportSize(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
)

private enum class MapReadinessState {
    Uninitialized,
    InitializingMapbox,
    LoadingStyle,
    StyleLoaded,
    LoadingTiles,
    TilesLoaded,
    Failed,
    Retrying,
}

private fun shouldSnapCarPresenceMotion(
    fromLat: Double,
    fromLng: Double,
    toLat: Double,
    toLng: Double,
): Boolean =
    kotlin.math.abs(fromLat - toLat) > CAR_PRESENCE_MOTION_SNAP_DEGREES ||
        kotlin.math.abs(fromLng - toLng) > CAR_PRESENCE_MOTION_SNAP_DEGREES

internal class OttoCarMapObserver(
    context: Context,
    private val state: StateFlow<OttoShellUiState>,
    private val visibleAreaProvider: () -> Rect? = { null },
) : MapboxCarMapObserver {
    private val appContext = context.applicationContext
    private val markerBitmaps =
        OttoCarMapMarkerBitmaps(appContext) {
            render(state.value, forceMarkers = true)
        }
    private var surface: MapboxCarMapSurface? = null
    private var observerScope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var followCameraJob: Job? = null
    private val followLocationSmoothing = MapDriveCamera.LiveLocationSmoothingController()
    private var followsUser = true
    private var followTargetLat: Double? = null
    private var followTargetLng: Double? = null
    private var followRenderedLat: Double? = null
    private var followRenderedLng: Double? = null
    private var followTargetBearing = 0f
    private var followRenderedBearing = 0f
    private var wasDriveFollowMode = false
    private var followZoomOffsetSteps = 0
    private var didApplyInitialCamera = false
    private var currentCameraZoom: Double? = null
    private var currentCameraLatitude: Double? = null
    private var currentCameraLongitude: Double? = null
    private var projectedViewport: ProjectedViewportSize = ProjectedViewportSize()
    private val presenceMotionTracks = mutableMapOf<String, CarPresenceMotionTrack>()
    private var lastMarkerFingerprint: MarkerRenderFingerprint? = null
    private var lastTemplateFingerprint: TemplateRenderFingerprint? = null
    private var diagnosticsStartElapsedMs = SystemClock.elapsedRealtime()
    private var didLogFirstFix = false
    private var didLogFirstStyle = false
    private var didLogFirstRenderFrame = false
    private var didLogFirstLoadedSourceData = false
    private var didLogFirstSelfMarker = false
    private var mapReadinessState = MapReadinessState.Uninitialized
    private var mapRecoveryAttempt = 0
    private var mapRecoveryJob: Job? = null
    private var isMapRecoveryInFlight = false
    private var lastSuccessfulRenderMs = 0L
    private var isNetworkAvailable = true
    private val mapboxEventSubscriptions = mutableListOf<Cancelable>()
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start(invalidate: () -> Unit): Boolean {
        if (observerScope != null) {
            logTiming("observer start skipped", "alreadyRunning=true")
            render(state.value, forceMarkers = true)
            return false
        }
        diagnosticsStartElapsedMs = SystemClock.elapsedRealtime()
        didLogFirstFix = false
        didLogFirstStyle = false
        didLogFirstRenderFrame = false
        didLogFirstLoadedSourceData = false
        didLogFirstSelfMarker = false
        didApplyInitialCamera = false
        mapReadinessState = MapReadinessState.InitializingMapbox
        mapRecoveryAttempt = 0
        lastSuccessfulRenderMs = 0L
        logTiming("observer start")
        startNetworkMonitoring()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        observerScope = scope
        collectJob =
            scope.launch {
                state.collect { snapshot ->
                    render(snapshot)
                    val templateFingerprint =
                        TemplateRenderFingerprint(
                            hasActiveDriveSession = snapshot.hasActiveDriveSession,
                            waitingForRouteDriveStart = snapshot.activeRouteDriveSession?.isArmed == true && snapshot.turnByTurnGuidance == null,
                            guidanceHash = snapshot.turnByTurnGuidance.hashCode(),
                        )
                    if (templateFingerprint != lastTemplateFingerprint) {
                        lastTemplateFingerprint = templateFingerprint
                        Log.d("AndroidAutoNav", "Invalidating Android Auto template because navigation state changed")
                        invalidate()
                    }
                }
            }
        followCameraJob = scope.launch { runFollowCameraLoop() }
        ensureAndroidAutoMapLoaded(reason = "observer-start")
        return true
    }

    fun stop() {
        if (observerScope == null) return
        logTiming("observer stop")
        mapRecoveryJob?.cancel()
        mapRecoveryJob = null
        clearMapboxEventSubscriptions()
        stopNetworkMonitoring()
        observerScope?.cancel()
        observerScope = null
        collectJob = null
        followCameraJob = null
        lastTemplateFingerprint = null
    }

    override fun onAttached(mapboxCarMapSurface: MapboxCarMapSurface) {
        surface = mapboxCarMapSurface
        Log.d(ANDROID_AUTO_MAP_TAG, "Map surface available")
        logTiming("surface attached")
        mapReadinessState = MapReadinessState.InitializingMapbox
        didApplyInitialCamera = false
        currentCameraZoom = null
        currentCameraLatitude = null
        currentCameraLongitude = null
        didLogFirstRenderFrame = false
        didLogFirstLoadedSourceData = false
        lastMarkerFingerprint = null
        subscribeMapboxHealthEvents(mapboxCarMapSurface)
        ensureAndroidAutoMapLoaded(reason = "surface-attached")
        render(state.value, forceMarkers = true)
    }

    override fun onDetached(mapboxCarMapSurface: MapboxCarMapSurface) {
        clearMapboxEventSubscriptions()
        mapRecoveryJob?.cancel()
        mapRecoveryJob = null
        isMapRecoveryInFlight = false
        if (surface === mapboxCarMapSurface) {
            surface = null
        }
        lastMarkerFingerprint = null
        didApplyInitialCamera = false
        mapReadinessState = MapReadinessState.Uninitialized
        logTiming("surface detached")
    }

    fun ensureAndroidAutoMapLoaded(reason: String) {
        Log.d(
            ANDROID_AUTO_MAP_TAG,
            "ensureAndroidAutoMapLoaded reason=$reason state=$mapReadinessState network=$isNetworkAvailable " +
                "surface=${surface != null} styleUri=${Style.DARK}",
        )
        val tokenReady = BuildConfig.MAPBOX_ACCESS_TOKEN.trim().isNotBlank()
        Log.d(ANDROID_AUTO_MAP_TAG, "Mapbox token present: $tokenReady")
        Log.d(ANDROID_AUTO_MAP_TAG, "Style URI: ${Style.DARK}")
        if (!tokenReady || Style.DARK.isBlank()) {
            mapReadinessState = MapReadinessState.Failed
            Log.w(ANDROID_AUTO_MAP_TAG, "Map load deferred: missing token or style")
            return
        }
        if (!isNetworkAvailable) {
            Log.d(ANDROID_AUTO_MAP_TAG, "Network available: false")
            return
        }
        if (mapReadinessState == MapReadinessState.InitializingMapbox ||
            mapReadinessState == MapReadinessState.LoadingStyle
        ) {
            Log.d(ANDROID_AUTO_MAP_TAG, "ensure skipped: initialization already in progress")
            scheduleMapLoadWatchdog(reason = "$reason:init-in-progress", delayMs = 8_000L)
            return
        }
        if (isMapHealthy()) return
        recoverAndroidAutoMap(reason)
    }

    private fun subscribeMapboxHealthEvents(mapboxCarMapSurface: MapboxCarMapSurface) {
        clearMapboxEventSubscriptions()
        val map = mapboxCarMapSurface.mapSurface.mapboxMap
        Log.d(ANDROID_AUTO_MAP_TAG, "Creating Mapbox map")
        Log.d(ANDROID_AUTO_MAP_TAG, "Waiting for style from MapInitOptions")
        mapReadinessState = MapReadinessState.LoadingStyle
        mapboxEventSubscriptions +=
            map.subscribeStyleLoaded {
                onAndroidAutoStyleReady(reason = "style-loaded")
            }
        mapboxEventSubscriptions +=
            map.subscribeMapLoaded {
                Log.d(ANDROID_AUTO_MAP_TAG, "Map loaded")
                mapReadinessState = MapReadinessState.LoadingTiles
                scheduleMapLoadWatchdog(reason = "map-loaded:no-source-data", delayMs = 5_000L)
            }
        mapboxEventSubscriptions +=
            map.subscribeRenderFrameFinished {
                handleAndroidAutoRenderFrameFinished()
            }
        mapboxEventSubscriptions +=
            map.subscribeSourceDataLoaded { event ->
                if (event.loaded == true) {
                    markAndroidAutoTilesLoaded(reason = "source-data-loaded")
                }
            }
        mapboxEventSubscriptions +=
            map.subscribeMapLoadingError { event ->
                mapReadinessState = MapReadinessState.Failed
                Log.w(ANDROID_AUTO_MAP_TAG, "Failure reason: ${event.message}")
                scheduleMapLoadWatchdog(reason = "map-loading-error", delayMs = 1_000L)
            }
        map.getStyle {
            onAndroidAutoStyleReady(reason = "get-style")
        }
        scheduleMapLoadWatchdog(reason = "surface-attached", delayMs = 8_000L)
    }

    private fun onAndroidAutoStyleReady(reason: String) {
        if (mapReadinessState == MapReadinessState.TilesLoaded) return
        mapReadinessState = MapReadinessState.StyleLoaded
        Log.d(ANDROID_AUTO_MAP_TAG, "Style loaded reason=$reason")
        ensureValidAndroidAutoCamera(reason = reason)
        mapReadinessState = MapReadinessState.LoadingTiles
        render(state.value, forceMarkers = true)
        scheduleMapLoadWatchdog(reason = reason, delayMs = 8_000L)
    }

    private fun clearMapboxEventSubscriptions() {
        mapboxEventSubscriptions.forEach { it.cancel() }
        mapboxEventSubscriptions.clear()
    }

    private fun handleAndroidAutoRenderFrameFinished() {
        if (!didLogFirstRenderFrame) {
            didLogFirstRenderFrame = true
            Log.d(ANDROID_AUTO_MAP_TAG, "First render frame finished")
        }
        markAndroidAutoMapRendered(reason = "render-frame-finished")
    }

    private fun markAndroidAutoMapRendered(reason: String) {
        val now = SystemClock.elapsedRealtime()
        lastSuccessfulRenderMs = now
        if (mapReadinessState != MapReadinessState.TilesLoaded) {
            mapReadinessState = MapReadinessState.TilesLoaded
            mapRecoveryAttempt = 0
            isMapRecoveryInFlight = false
            mapRecoveryJob?.cancel()
            mapRecoveryJob = null
            Log.d(ANDROID_AUTO_MAP_TAG, "First render complete reason=$reason")
        }
    }

    private fun markAndroidAutoTilesLoaded(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (!didLogFirstLoadedSourceData) {
            didLogFirstLoadedSourceData = true
            Log.d(ANDROID_AUTO_MAP_TAG, "Source data loaded")
        }
        lastSuccessfulRenderMs = now
        if (mapReadinessState != MapReadinessState.TilesLoaded) {
            mapReadinessState = MapReadinessState.TilesLoaded
            mapRecoveryAttempt = 0
            isMapRecoveryInFlight = false
            mapRecoveryJob?.cancel()
            mapRecoveryJob = null
            Log.d(ANDROID_AUTO_MAP_TAG, "First tile/source complete reason=$reason")
        }
    }

    private fun scheduleMapLoadWatchdog(
        reason: String,
        delayMs: Long,
    ) {
        val scope = observerScope ?: return
        mapRecoveryJob?.cancel()
        mapRecoveryJob =
            scope.launch {
                delay(delayMs)
                if (isMapHealthy()) return@launch
                recoverAndroidAutoMap(reason)
            }
    }

    private fun recoverAndroidAutoMap(reason: String) {
        if (isMapRecoveryInFlight) {
            Log.d(ANDROID_AUTO_MAP_TAG, "map recovery skipped reason=$reason state=$mapReadinessState")
            return
        }
        val currentSurface = surface
        if (currentSurface == null) {
            mapReadinessState = MapReadinessState.Uninitialized
            Log.d(ANDROID_AUTO_MAP_TAG, "map recovery waiting for surface reason=$reason")
            return
        }
        if (!isNetworkAvailable) {
            mapReadinessState = MapReadinessState.Failed
            Log.d(ANDROID_AUTO_MAP_TAG, "map recovery waiting for network reason=$reason")
            return
        }
        if (mapRecoveryAttempt >= 4) {
            mapReadinessState = MapReadinessState.Failed
            isMapRecoveryInFlight = false
            Log.w(ANDROID_AUTO_MAP_TAG, "map recovery gave up reason=$reason")
            return
        }
        isMapRecoveryInFlight = true
        mapRecoveryAttempt += 1
        mapReadinessState = MapReadinessState.Retrying
        val map = currentSurface.mapSurface.mapboxMap
        val action =
            when (mapRecoveryAttempt) {
                1 -> "reload style"
                2 -> "recreate map instance via style reload"
                3 -> "recreate map instance after delay via style reload"
                else -> "final style reload"
            }
        Log.d(
            ANDROID_AUTO_MAP_TAG,
            "map recovery attempt=$mapRecoveryAttempt reason=$reason action=$action",
        )
        val scope = observerScope
        if (mapRecoveryAttempt == 2) {
            subscribeMapboxHealthEvents(currentSurface)
        }
        if (mapRecoveryAttempt == 3 && scope != null) {
            scope.launch {
                delay(1_500L)
                reloadAndroidAutoStyle(map, reason = "delayed-retry")
            }
        } else {
            reloadAndroidAutoStyle(map, reason = reason)
        }
    }

    private fun reloadAndroidAutoStyle(
        map: com.mapbox.maps.MapboxMap,
        reason: String,
    ) {
        Log.d(ANDROID_AUTO_MAP_TAG, "Recovery action: reload style reason=$reason")
        mapReadinessState = MapReadinessState.LoadingStyle
        lastSuccessfulRenderMs = 0L
        mapRecoveryJob?.cancel()
        mapRecoveryJob =
            observerScope?.launch {
                delay(10_000L)
                if (isMapRecoveryInFlight && mapReadinessState == MapReadinessState.LoadingStyle) {
                    isMapRecoveryInFlight = false
                    recoverAndroidAutoMap("style-reload-timeout:$reason")
                }
            }
        map.loadStyle(Style.DARK) {
            isMapRecoveryInFlight = false
            mapReadinessState = MapReadinessState.StyleLoaded
            Log.d(ANDROID_AUTO_MAP_TAG, "Style loaded")
            ensureValidAndroidAutoCamera(reason = "style-reloaded")
            mapReadinessState = MapReadinessState.LoadingTiles
            render(state.value, forceMarkers = true)
            scheduleMapLoadWatchdog(reason = "style-reloaded", delayMs = 8_000L)
        }
    }

    private fun isMapHealthy(): Boolean {
        if (surface == null) return false
        if (mapReadinessState != MapReadinessState.TilesLoaded) return false
        return SystemClock.elapsedRealtime() - lastSuccessfulRenderMs <= 30_000L
    }

    private fun startNetworkMonitoring() {
        if (networkCallback != null) return
        val manager = connectivityManager ?: return
        isNetworkAvailable = manager.activeNetworkIsAvailable()
        Log.d(ANDROID_AUTO_MAP_TAG, "Network available: $isNetworkAvailable")
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkAvailability(true)
                }

                override fun onLost(network: Network) {
                    updateNetworkAvailability(manager.activeNetworkIsAvailable())
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    updateNetworkAvailability(manager.activeNetworkIsAvailable())
                }
            }
        networkCallback = callback
        runCatching {
            manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        }.onFailure { error ->
            Log.w(ANDROID_AUTO_MAP_TAG, "Network monitor unavailable: ${error.message}")
        }
    }

    private fun stopNetworkMonitoring() {
        val manager = connectivityManager ?: return
        val callback = networkCallback ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun updateNetworkAvailability(available: Boolean) {
        val changed = isNetworkAvailable != available
        isNetworkAvailable = available
        Log.d(ANDROID_AUTO_MAP_TAG, "Network available: $available")
        if (changed) {
            Log.d(ANDROID_AUTO_MAP_TAG, "Network changed: ${if (available) "online" else "offline"}")
        }
        if (changed && available) {
            Log.d(ANDROID_AUTO_MAP_TAG, "Retrying map load after network restored")
            observerScope?.launch { ensureAndroidAutoMapLoaded(reason = "network-restored") }
        }
    }

    private fun ConnectivityManager.activeNetworkIsAvailable(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun ensureValidAndroidAutoCamera(reason: String) {
        val snapshot = state.value
        val fix = snapshot.deviceLocationFix
        val routePoint =
            activeRouteForSnapshot(snapshot)
                ?.let { lineCoordinatesFromSavedRoute(it).firstOrNull() }
        val (source, lat, lng) =
            when {
                fix != null && fix.latitude.isFinite() && fix.longitude.isFinite() ->
                    Triple("user location", fix.latitude, fix.longitude)
                routePoint != null && routePoint.latitude().isFinite() && routePoint.longitude().isFinite() ->
                    Triple("route start", routePoint.latitude(), routePoint.longitude())
                didApplyInitialCamera &&
                    currentCameraLatitude?.isFinite() == true &&
                    currentCameraLongitude?.isFinite() == true ->
                    Triple("last known", currentCameraLatitude ?: ANDROID_AUTO_FALLBACK_LATITUDE, currentCameraLongitude ?: ANDROID_AUTO_FALLBACK_LONGITUDE)
                else ->
                    Triple("fallback", ANDROID_AUTO_FALLBACK_LATITUDE, ANDROID_AUTO_FALLBACK_LONGITUDE)
            }
        val valid = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
        Log.d(ANDROID_AUTO_MAP_TAG, "Camera source: $source reason=$reason")
        Log.d(ANDROID_AUTO_MAP_TAG, "Camera coordinate valid: $valid")
        if (!valid) return
        val map = surface?.mapSurface?.mapboxMap ?: return
        val current = map.cameraState.center
        val currentValid =
            current.latitude().isFinite() &&
                current.longitude().isFinite() &&
                (kotlin.math.abs(current.latitude()) > 0.000001 || kotlin.math.abs(current.longitude()) > 0.000001)
        if (currentValid) return
        map.setCamera(
            cameraOptionsForFollow(
                snapshot = snapshot,
                lat = lat,
                lng = lng,
                bearing = 0f,
                zoom = fixedFollowZoom(snapshot),
            ),
        )
        didApplyInitialCamera = true
        followRenderedLat = lat
        followRenderedLng = lng
        followRenderedBearing = 0f
        logCameraApplied("validated initial camera applied", snapshot, lat, lng, fixedFollowZoom(snapshot))
        updateCurrentCameraState(map)
    }

    fun recenterOnUser() {
        val snapshot = state.value
        val fix = snapshot.deviceLocationFix ?: return
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        val map = surface?.mapSurface?.mapboxMap ?: return
        followsUser = true
        followZoomOffsetSteps = 0
        updateFollowTarget(snapshot, fix)
        followLocationSmoothing.reset(
            fix = fix,
            bearing = if (snapshot.hasActiveDriveSession) followTargetBearing else 0f,
            nowMs = SystemClock.elapsedRealtime(),
            isDriveMode = snapshot.hasActiveDriveSession,
        )
        followRenderedLat = followLocationSmoothing.renderedLat ?: fix.latitude
        followRenderedLng = followLocationSmoothing.renderedLng ?: fix.longitude
        followRenderedBearing = if (snapshot.hasActiveDriveSession) followTargetBearing else 0f
        map.setCamera(
            cameraOptionsForFollow(
                snapshot = snapshot,
                lat = fix.latitude,
                lng = fix.longitude,
                bearing = followRenderedBearing,
                zoom = fixedFollowZoom(snapshot),
            ),
        )
        didApplyInitialCamera = true
        logCameraApplied("recenter camera applied", snapshot, fix.latitude, fix.longitude, fixedFollowZoom(snapshot))
        updateCurrentCameraState(map)
        refreshFollowPresenceLayer(snapshot)
    }

    fun zoomIn() {
        adjustZoom(1)
    }

    fun zoomOut() {
        adjustZoom(-1)
    }

    private fun adjustZoom(delta: Int) {
        if (delta == 0) return
        val map = surface?.mapSurface?.mapboxMap ?: return
        val snapshot = state.value
        if (followsUser) {
            followZoomOffsetSteps = (followZoomOffsetSteps + delta).coerceIn(-3, 3)
            val lat = followRenderedLat ?: followTargetLat ?: snapshot.deviceLocationFix?.latitude ?: currentCameraLatitude
            val lng = followRenderedLng ?: followTargetLng ?: snapshot.deviceLocationFix?.longitude ?: currentCameraLongitude
            if (lat?.isFinite() == true && lng?.isFinite() == true) {
                map.setCamera(
                    cameraOptionsForFollow(
                        snapshot = snapshot,
                        lat = lat,
                        lng = lng,
                        bearing = if (snapshot.hasActiveDriveSession) followRenderedBearing else 0f,
                        zoom = fixedFollowZoom(snapshot),
                    ),
                )
                updateCurrentCameraState(map)
                refreshFollowPresenceLayer(snapshot)
            }
            return
        }

        val cameraState = map.cameraState
        val nextZoom = (cameraState.zoom + delta.toDouble()).coerceIn(4.0, 20.0)
        map.setCamera(
            CameraOptions.Builder()
                .center(cameraState.center)
                .zoom(nextZoom)
                .pitch(cameraState.pitch)
                .bearing(cameraState.bearing)
                .padding(ZERO_CAMERA_PADDING)
                .build(),
        )
        updateCurrentCameraState(map)
        refreshFollowPresenceLayer(snapshot)
    }

    private fun render(
        snapshot: OttoShellUiState,
        forceMarkers: Boolean = false,
    ) {
        val currentSurface = surface ?: return
        val map = currentSurface.mapSurface.mapboxMap
        syncProjectedViewportSize()
        updateCurrentCameraState(map)
        val fix = snapshot.deviceLocationFix
        if (fix != null) {
            if (!didLogFirstFix) {
                didLogFirstFix = true
                logTiming("first device fix", "lat=${fix.latitude} lng=${fix.longitude} accuracy=${fix.accuracyMeters}")
            }
            updateFollowTarget(snapshot, fix)
        }
        applyFallbackInitialCameraIfNeeded(snapshot, map)

        val markerFingerprint = markerRenderFingerprint(snapshot)
        if (!forceMarkers && markerFingerprint == lastMarkerFingerprint) return

        map.getStyle { style ->
            if (!didLogFirstStyle) {
                didLogFirstStyle = true
                logTiming("first style callback")
            }
            markerBitmaps.ensureImages(style)
            style.setOttoTrafficLayersVisible(snapshot.mapLayerShowTraffic)
            installDiscoveryLayers(style, snapshot)
            installActiveRoute(style, snapshot)
            installActiveTrail(style, snapshot)
            installPresenceLayer(style, snapshot)
            installHazards(style, snapshot)
            logFirstSelfMarkerIfNeeded(snapshot)
            logMarkerRefresh(snapshot)
            lastMarkerFingerprint = markerFingerprint
        }
    }

    private fun cameraOptionsForFollow(
        snapshot: OttoShellUiState,
        lat: Double,
        lng: Double,
        bearing: Float,
        zoom: Double,
    ): CameraOptions =
        CameraOptions.Builder()
            .center(Point.fromLngLat(lng, lat))
            .zoom(zoom)
            .pitch(if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_PITCH_DEGREES else 0.0)
            .bearing(if (snapshot.hasActiveDriveSession) bearing.toDouble() else 0.0)
            .padding(if (snapshot.hasActiveDriveSession) androidAutoDriveFollowPadding() else ZERO_CAMERA_PADDING)
            .build()

    private suspend fun runFollowCameraLoop() {
        while (currentCoroutineContext().isActive) {
            delay(FOLLOW_CAMERA_FRAME_DELAY_MS)
            val nowMs = SystemClock.elapsedRealtime()
            stepFollowCamera(nowMs)
            if (hasActivePresenceMotion(nowMs)) {
                refreshFollowPresenceLayer(state.value)
            }
        }
    }

    private fun stepFollowCamera(nowMs: Long) {
        if (!followsUser) return
        val snapshot = state.value
        val currentSurface = surface ?: return
        val map = currentSurface.mapSurface.mapboxMap
        val viewportChanged = syncProjectedViewportSize()
        val frame = followLocationSmoothing.frame(nowMs) ?: return
        val isDriveMode = snapshot.hasActiveDriveSession

        if (isDriveMode && !wasDriveFollowMode) {
            currentCameraZoom = MapDriveCamera.DRIVE_ZOOM
        } else if (!isDriveMode && wasDriveFollowMode) {
            followRenderedBearing = 0f
        }

        val currentLat = followRenderedLat ?: frame.latitude
        val currentLng = followRenderedLng ?: frame.longitude
        val newLat = frame.latitude
        val newLng = frame.longitude
        val newBearing = if (isDriveMode) frame.bearing else 0f
        val shouldApply =
            viewportChanged ||
                !didApplyInitialCamera ||
                isDriveMode != wasDriveFollowMode ||
                frame.isAnimating ||
                MapDriveCamera.shouldStepDriveCamera(
                    currentLat = currentLat,
                    currentLng = currentLng,
                    currentBearing = followRenderedBearing,
                    newLat = newLat,
                    newLng = newLng,
                    newBearing = newBearing,
                )
        if (!shouldApply) return

        val zoom = fixedFollowZoom(snapshot)
        followRenderedLat = newLat
        followRenderedLng = newLng
        followRenderedBearing = newBearing
        wasDriveFollowMode = isDriveMode

        map.setCamera(
            cameraOptionsForFollow(
                snapshot = snapshot,
                lat = newLat,
                lng = newLng,
                bearing = newBearing,
                zoom = zoom,
            ),
        )
        if (!didApplyInitialCamera) {
            didApplyInitialCamera = true
            logCameraApplied("initial camera applied", snapshot, newLat, newLng, zoom)
        }
        updateCurrentCameraState(map)
        render(snapshot, forceMarkers = viewportChanged)
        if (!viewportChanged) {
            refreshFollowPresenceLayer(snapshot)
        }
    }

    private fun applyFallbackInitialCameraIfNeeded(
        snapshot: OttoShellUiState,
        map: com.mapbox.maps.MapboxMap,
    ) {
        if (didApplyInitialCamera) return
        val fix = snapshot.deviceLocationFix
        val routePoint =
            activeRouteForSnapshot(snapshot)
                ?.let { lineCoordinatesFromSavedRoute(it).firstOrNull() }
        val lat =
            fix?.latitude?.takeIf { it.isFinite() }
                ?: routePoint?.latitude()?.takeIf { it.isFinite() }
                ?: ANDROID_AUTO_FALLBACK_LATITUDE
        val lng =
            fix?.longitude?.takeIf { it.isFinite() }
                ?: routePoint?.longitude()?.takeIf { it.isFinite() }
                ?: ANDROID_AUTO_FALLBACK_LONGITUDE
        val zoom = if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_ZOOM else ANDROID_AUTO_IDLE_ZOOM
        map.setCamera(
            cameraOptionsForFollow(
                snapshot = snapshot,
                lat = lat,
                lng = lng,
                bearing = 0f,
                zoom = zoom,
            ),
        )
        followRenderedLat = lat
        followRenderedLng = lng
        followRenderedBearing = 0f
        snapshot.deviceLocationFix?.let { fix ->
            followLocationSmoothing.reset(
                fix = fix,
                bearing = 0f,
                nowMs = SystemClock.elapsedRealtime(),
                isDriveMode = snapshot.hasActiveDriveSession,
            )
        }
        didApplyInitialCamera = true
        logCameraApplied("fallback initial camera applied", snapshot, lat, lng, zoom)
        updateCurrentCameraState(map)
    }

    private fun updateFollowTarget(
        snapshot: OttoShellUiState,
        fix: to.ottomot.driftd.core.location.LocationFix,
    ) {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        followLocationSmoothing.onLocationUpdate(
            fix = fix,
            isDriveMode = snapshot.hasActiveDriveSession,
            nowMs = SystemClock.elapsedRealtime(),
        )
        followTargetLat = followLocationSmoothing.currentTargetLat ?: fix.latitude
        followTargetLng = followLocationSmoothing.currentTargetLng ?: fix.longitude
        followTargetBearing = if (snapshot.hasActiveDriveSession) followLocationSmoothing.currentTargetBearing else 0f
        if (followRenderedLat == null || followRenderedLng == null) {
            followRenderedLat = followLocationSmoothing.renderedLat ?: fix.latitude
            followRenderedLng = followLocationSmoothing.renderedLng ?: fix.longitude
        }
    }

    private fun fixedFollowZoom(snapshot: OttoShellUiState): Double {
        val base = if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_ZOOM else ANDROID_AUTO_IDLE_ZOOM
        return (base + followZoomOffsetSteps.toDouble()).coerceIn(4.0, 20.0)
    }

    private fun updateCurrentCameraState(map: com.mapbox.maps.MapboxMap) {
        val cameraState = map.cameraState
        currentCameraZoom = cameraState.zoom.takeIf { it.isFinite() } ?: currentCameraZoom
        currentCameraLatitude = cameraState.center.latitude().takeIf { it.isFinite() } ?: currentCameraLatitude
        currentCameraLongitude = cameraState.center.longitude().takeIf { it.isFinite() } ?: currentCameraLongitude
    }

    private fun syncProjectedViewportSize(): Boolean {
        val next = projectedViewportSize()
        if (next == projectedViewport) return false
        projectedViewport = next
        logTiming("projected viewport changed", "width=${next.widthPx} height=${next.heightPx}")
        return true
    }

    private fun projectedViewportSize(): ProjectedViewportSize {
        val visible = visibleAreaProvider()
        val width = visible?.width()?.takeIf { it > 0 } ?: 0
        val height = visible?.height()?.takeIf { it > 0 } ?: 0
        return ProjectedViewportSize(widthPx = width, heightPx = height)
    }

    private fun refreshFollowPresenceLayer(snapshot: OttoShellUiState) {
        val map = surface?.mapSurface?.mapboxMap ?: return
        map.getStyle { style ->
            markerBitmaps.ensureImages(style)
            installPresenceLayer(style, snapshot)
        }
    }

    private fun androidAutoDriveFollowPadding(): EdgeInsets =
        MapDriveCamera.driveFollowPadding(
            MapDriveCamera.DriveFollowChromeInsets(
                mapViewportHeightPx =
                    projectedViewport.heightPx
                        .takeIf { it > 0 }
                        ?.toFloat()
                        ?: ANDROID_AUTO_DRIVE_VIEWPORT_FALLBACK_PX,
                mapDriveDockHeightPx = 0f,
                mapOverlayBottomPadPx = 0f,
            ),
        )

    private fun installHazards(
        style: Style,
        snapshot: OttoShellUiState,
    ) {
        val latitudeDelta = markerLatitudeDelta(snapshot)
        val hazardPresentation = routeMarkerPresentation("path", latitudeDelta)
        val hazardBaseIconSize = presenceIconSize(snapshot) * HAZARD_TO_PRESENCE_ICON_SCALE
        val features =
            snapshot.activeMapHazards.mapNotNull { hazard ->
                val imageId =
                    if (hazardPresentation == MarkerPresentation.Dot) {
                        markerBitmaps.ensureDotImage(
                            style = style,
                            cacheKey = "hazard-${hazard.type}",
                            color = hazardColor(hazard.type),
                        )
                    } else {
                        markerBitmaps.ensureHazardImage(style, hazard.type)
                    }
                featureOrNull(
                    lng = hazard.longitude,
                    lat = hazard.latitude,
                    iconId = imageId,
                    sortKey = 80.0,
                    iconSize =
                        hazardBaseIconSize *
                            driveHorizonScale(
                                snapshot = snapshot,
                                lat = hazard.latitude,
                                lng = hazard.longitude,
                            ),
                )
            }
        if (features.isNotEmpty()) {
            runCatching { style.removeStyleLayer(LAYER_HAZARDS) }
        }
        installSymbolLayer(
            style = style,
            sourceId = SOURCE_HAZARDS,
            layerId = LAYER_HAZARDS,
            features = features,
            iconAnchor = IconAnchor.CENTER,
        )
    }

    private fun installDiscoveryLayers(
        style: Style,
        snapshot: OttoShellUiState,
    ) {
        val latitudeDelta = markerLatitudeDelta(snapshot)
        val discoveryPresentation = MapDiscoveryMarkerLOD.presentation(latitudeDelta)
        val discoveryScale =
            if (discoveryPresentation == MapDiscoveryMarkerPresentation.Pin) {
                MapDiscoveryMarkerLOD.pinScale(latitudeDelta).toDouble() * 0.72
            } else {
                1.0
            }
        installSymbolLayer(
            style,
            SOURCE_SAVED_PLACES,
            LAYER_SAVED_PLACES,
            features =
                if (snapshot.mapLayerShowSavedPlaces) {
                    snapshot.savedPlaces.mapNotNull {
                        val imageId =
                            if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) {
                                markerBitmaps.ensureDotImage(
                                    style = style,
                                    cacheKey = "saved-place",
                                    color = MapDiscoveryMarkerLOD.dotColor(MapDiscoveryMarkerKind.SavedPlace).toArgb(),
                                )
                            } else {
                                OttoCarMapMarkerBitmaps.MarkerImage.SavedPlace.imageId
                            }
                        featureOrNull(
                            lng = it.longitude,
                            lat = it.latitude,
                            iconId = imageId,
                            sortKey = 20.0,
                            iconSize =
                                discoveryScale *
                                    driveHorizonScale(
                                        snapshot = snapshot,
                                        lat = it.latitude,
                                        lng = it.longitude,
                                    ),
                        )
                    }
                } else {
                    emptyList()
                },
            iconAnchor = if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) IconAnchor.CENTER else IconAnchor.BOTTOM,
        )
        installSymbolLayer(
            style,
            SOURCE_EVENTS,
            LAYER_EVENTS,
            features =
                if (snapshot.mapLayerShowUpcomingEvents) {
                    eventProximityGroupsForMap(nearbyEventsForCarMap(snapshot))
                        .mapNotNull { group ->
                            val imageId =
                                if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) {
                                    markerBitmaps.ensureDotImage(
                                        style = style,
                                        cacheKey = "event",
                                        color = MapDiscoveryMarkerLOD.dotColor(MapDiscoveryMarkerKind.Event).toArgb(),
                                        clusterCount = group.events.size.takeIf { it > 1 },
                                    )
                                } else {
                                    OttoCarMapMarkerBitmaps.MarkerImage.Event.imageId
                                }
                            featureOrNull(
                                lng = group.anchorLng,
                                lat = group.anchorLat,
                                iconId = imageId,
                                sortKey = 18.0,
                                iconSize =
                                    discoveryScale *
                                        driveHorizonScale(
                                            snapshot = snapshot,
                                            lat = group.anchorLat,
                                            lng = group.anchorLng,
                                        ),
                            )
                        }
                } else {
                    emptyList()
                },
            iconAnchor = if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) IconAnchor.CENTER else IconAnchor.BOTTOM,
        )
        installSymbolLayer(
            style,
            SOURCE_RACE_TRACKS,
            LAYER_RACE_TRACKS,
            features =
                if (snapshot.mapLayerShowRaceTracks) {
                    nearbyRaceTracksForCarMap(snapshot)
                        .mapNotNull { it.coordinateOrNull() }
                        .mapNotNull { (lat, lng) ->
                            val imageId =
                                if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) {
                                    markerBitmaps.ensureDotImage(
                                        style = style,
                                        cacheKey = "race-track",
                                        color = MapDiscoveryMarkerLOD.dotColor(MapDiscoveryMarkerKind.RaceTrack).toArgb(),
                                    )
                                } else {
                                    OttoCarMapMarkerBitmaps.MarkerImage.RaceTrack.imageId
                                }
                            featureOrNull(
                                lng = lng,
                                lat = lat,
                                iconId = imageId,
                                sortKey = 16.0,
                                iconSize =
                                    discoveryScale *
                                        driveHorizonScale(
                                            snapshot = snapshot,
                                            lat = lat,
                                            lng = lng,
                                        ),
                            )
                        }
                } else {
                    emptyList()
                },
            iconAnchor = if (discoveryPresentation == MapDiscoveryMarkerPresentation.Dot) IconAnchor.CENTER else IconAnchor.BOTTOM,
        )
    }

    private fun installPresenceLayer(
        style: Style,
        snapshot: OttoShellUiState,
    ) {
        val groups = presenceGroups(snapshot)
        val iconSize = presenceIconSize(snapshot) * PRESENCE_ICON_RENDER_SCALE
        val brandLogoUrlsByUserId =
            to.ottomot.driftd.mapPresenceBrandLogoUrlByUserId(
                members = presenceMembersForCarMap(snapshot),
                meId = snapshot.me?.id,
                selectedSharingCarId = snapshot.selectedSharingCarId,
                garageCars = snapshot.garageCars,
                showsSelfLogo = to.ottomot.driftd.showsSelfDriveBrandLogoOnMap(snapshot),
                context = appContext,
            )
        val singleFeatures = mutableListOf<Feature>()
        val compositeFeatures = mutableListOf<Feature>()
        groups.forEach { group ->
            val imageId =
                markerBitmaps.ensurePresenceImage(
                    style = style,
                    group = group,
                    contacts = snapshot.contacts,
                    me = snapshot.me,
                    brandLogoUrlsByUserId = brandLogoUrlsByUserId,
                )
            val containsSelf = group.members.any { member -> isSelf(member, snapshot) }
            val baseSize =
                iconSize *
                    if (containsSelf) {
                        1.0
                    } else {
                        driveHorizonScale(
                            snapshot = snapshot,
                            lat = group.anchorLat,
                            lng = group.anchorLng,
                            minScale = PRESENCE_HORIZON_MIN_SCALE,
                        )
                    }
            val feature =
                featureOrNull(
                    lng = group.anchorLng,
                    lat = group.anchorLat,
                    iconId = imageId,
                    sortKey = if (containsSelf) 52.0 else 45.0,
                    iconSize = if (group.members.size > 1) baseSize * 1.2 else baseSize,
                ) ?: return@forEach
            if (group.members.size > 1) {
                compositeFeatures += feature
            } else {
                singleFeatures += feature
            }
        }
        installSymbolLayer(
            style,
            SOURCE_PRESENCE,
            LAYER_PRESENCE,
            features = singleFeatures,
            iconAnchor = IconAnchor.BOTTOM,
        )
        installSymbolLayer(
            style,
            SOURCE_PRESENCE_COMPOSITE,
            LAYER_PRESENCE_COMPOSITE,
            features = compositeFeatures,
            iconAnchor = IconAnchor.BOTTOM,
        )
    }

    private fun presenceGroups(snapshot: OttoShellUiState) =
        groupNearbyPresence(
            members = presenceMembersForCarMap(snapshot),
            thresholdMeters = 34f,
            meUserId = snapshot.me?.id,
        )

    private fun presenceMembersForCarMap(snapshot: OttoShellUiState): List<PresenceMemberDto> {
        val nowMs = SystemClock.elapsedRealtime()
        val plotted =
            snapshot.presenceMembers
                .filter { member ->
                    member.userId.trim().isNotEmpty() &&
                        member.lat?.isFinite() == true &&
                        member.lng?.isFinite() == true &&
                        member.isActive
                }.map { member ->
                    if (isSelf(member, snapshot)) {
                        member
                    } else {
                        smoothedPresenceMember(member, nowMs)
                    }
                }.toMutableList()
        val activePresenceIds = plotted.map { it.userId.trim() }.filter { it.isNotEmpty() }.toSet()
        presenceMotionTracks.keys.retainAll(activePresenceIds)
        val me = snapshot.me
        val fix = snapshot.deviceLocationFix
        if (me != null && fix != null && fix.latitude.isFinite() && fix.longitude.isFinite()) {
            val selfLat = if (followsUser) followRenderedLat ?: followTargetLat ?: fix.latitude else fix.latitude
            val selfLng = if (followsUser) followRenderedLng ?: followTargetLng ?: fix.longitude else fix.longitude
            val self =
                PresenceMemberDto(
                    userId = me.id,
                    circleId = snapshot.mapPresenceCircleId.ifBlank { OttoShellUiState.PublicPresenceChannelId },
                    isActive = true,
                    inApp = true,
                    speedMph = fix.speedMps?.let { (it * 2.23694).toDouble() },
                    movementMode = snapshot.deviceMovementMode,
                    lat = selfLat,
                    lng = selfLng,
                    updatedAt = null,
                    carId = snapshot.selectedSharingCarId.takeIf { it.isNotBlank() },
                    logoSlug = null,
                )
            val existing = plotted.indexOfFirst { member -> ottoUserIdsEqual(member.userId, me.id) }
            if (existing >= 0) {
                plotted[existing] = self
            } else {
                plotted.add(self)
            }
        }
        return plotted
    }

    private fun smoothedPresenceMember(
        member: PresenceMemberDto,
        nowMs: Long,
    ): PresenceMemberDto {
        val id = member.userId.trim().takeIf { it.isNotEmpty() } ?: return member
        val targetLat = member.lat?.takeIf { it.isFinite() } ?: return member
        val targetLng = member.lng?.takeIf { it.isFinite() } ?: return member
        val previous = presenceMotionTracks[id]
        val sourceKey = "${member.updatedAt}:${member.lat}:${member.lng}"
        if (
            previous != null &&
                previous.endLat == targetLat &&
                previous.endLng == targetLng &&
                previous.sourceKey == sourceKey
        ) {
            val position = previous.positionAt(nowMs)
            return member.copy(lat = position.first, lng = position.second)
        }

        val current = previous?.positionAt(nowMs) ?: (targetLat to targetLng)
        val observedIntervalMs = previous?.let { nowMs - it.wallUpdatedAtMs } ?: 0L
        val durationMs =
            if (
                previous == null ||
                    shouldSnapCarPresenceMotion(current.first, current.second, targetLat, targetLng)
            ) {
                0L
            } else {
                (observedIntervalMs * CAR_PRESENCE_MOTION_INTERVAL_MULTIPLIER)
                    .toLong()
                    .coerceIn(CAR_PRESENCE_MOTION_MIN_ANIMATION_MS, CAR_PRESENCE_MOTION_MAX_ANIMATION_MS)
            }
        val track =
            CarPresenceMotionTrack(
                startLat = current.first,
                startLng = current.second,
                endLat = targetLat,
                endLng = targetLng,
                startMs = nowMs,
                endMs = nowMs + durationMs,
                sourceKey = sourceKey,
                wallUpdatedAtMs = nowMs,
            )
        presenceMotionTracks[id] = track
        val position = track.positionAt(nowMs)
        return member.copy(lat = position.first, lng = position.second)
    }

    private fun hasActivePresenceMotion(nowMs: Long): Boolean = presenceMotionTracks.values.any { nowMs < it.endMs }

    private fun isSelf(
        member: PresenceMemberDto,
        snapshot: OttoShellUiState,
    ): Boolean {
        val me = snapshot.me?.id ?: return false
        return ottoUserIdsEqual(member.userId, me)
    }

    private data class MarkerRenderFingerprint(
        val trafficVisible: Boolean,
        val savedPlacesVisible: Boolean,
        val eventsVisible: Boolean,
        val raceTracksVisible: Boolean,
        val activeDrive: Boolean,
        val activeDriveKind: String?,
        val activeRouteId: String?,
        val activeRouteSessionId: String?,
        val activeRouteUsesAdhocDestination: Boolean,
        val activeTrailCount: Int,
        val routeTrailCount: Int,
        val presence: List<String>,
        val hazardsHash: Int,
        val savedPlacesHash: Int,
        val eventsHash: Int,
        val raceTracksHash: Int,
        val selectedRouteHash: Int,
        val navigationLineHash: Int,
        val projectedViewport: ProjectedViewportSize,
    )

    private data class TemplateRenderFingerprint(
        val hasActiveDriveSession: Boolean,
        val waitingForRouteDriveStart: Boolean,
        val guidanceHash: Int,
    )

    private fun markerRenderFingerprint(snapshot: OttoShellUiState): MarkerRenderFingerprint {
        val routeSession = snapshot.activeRouteDriveSession
        val driveSession = snapshot.activeDriveSession
        return MarkerRenderFingerprint(
            trafficVisible = snapshot.mapLayerShowTraffic,
            savedPlacesVisible = snapshot.mapLayerShowSavedPlaces,
            eventsVisible = snapshot.mapLayerShowUpcomingEvents,
            raceTracksVisible = snapshot.mapLayerShowRaceTracks,
            activeDrive = snapshot.hasActiveDriveSession,
            activeDriveKind = driveSession?.kind?.name,
            activeRouteId = driveSession?.routeId ?: routeSession?.activeRouteId,
            activeRouteSessionId = routeSession?.sessionId,
            activeRouteUsesAdhocDestination = snapshot.activeRouteDriveUsesAdhocAndroidAutoDestination,
            activeTrailCount = snapshot.activeDrivePathSamples.size,
            routeTrailCount = snapshot.routeDrivePathSamples.size,
            presence =
                presenceMembersForCarMap(snapshot).map { member ->
                    listOf(
                        member.userId,
                        coordinateBucket(member.lat),
                        coordinateBucket(member.lng),
                        member.movementMode.orEmpty(),
                        member.logoSlug.orEmpty(),
                        speedBucket(member.speedMph),
                    ).joinToString(":")
                }.sorted(),
            hazardsHash = snapshot.activeMapHazards.hashCode(),
            savedPlacesHash = if (snapshot.mapLayerShowSavedPlaces) snapshot.savedPlaces.hashCode() else 0,
            eventsHash =
                if (snapshot.mapLayerShowUpcomingEvents) {
                    (snapshot.events + snapshot.communityEvents + snapshot.squadFeedEvents).hashCode()
                } else {
                    0
                },
            raceTracksHash = if (snapshot.mapLayerShowRaceTracks) snapshot.raceTracks.hashCode() else 0,
            selectedRouteHash = activeRouteForSnapshot(snapshot).hashCode(),
            navigationLineHash = snapshot.navigationLineCoordinates.hashCode(),
            projectedViewport = projectedViewport,
        )
    }

    private fun coordinateBucket(value: Double?): String =
        value
            ?.takeIf { it.isFinite() }
            ?.let { ((it * 100_000.0).roundToLong()).toString() }
            .orEmpty()

    private fun speedBucket(value: Double?): String =
        value
            ?.takeIf { it.isFinite() }
            ?.let { (it / 5.0).roundToLong().toString() }
            .orEmpty()

    private fun logFirstSelfMarkerIfNeeded(snapshot: OttoShellUiState) {
        if (!BuildConfig.DEBUG || didLogFirstSelfMarker) return
        val selfGroup = presenceGroups(snapshot).firstOrNull { group -> group.members.any { member -> isSelf(member, snapshot) } }
        if (selfGroup != null) {
            didLogFirstSelfMarker = true
            logTiming("first self marker", "lat=${selfGroup.anchorLat} lng=${selfGroup.anchorLng}")
        }
    }

    private fun logTiming(
        event: String,
        detail: String = "",
    ) {
        if (!BuildConfig.DEBUG) return
        val elapsed = SystemClock.elapsedRealtime() - diagnosticsStartElapsedMs
        Log.d(
            "OttoCarMapObserver",
            "Android Auto timing +${elapsed}ms $event${if (detail.isBlank()) "" else " $detail"}",
        )
    }

    private fun logCameraApplied(
        event: String,
        snapshot: OttoShellUiState,
        lat: Double,
        lng: Double,
        zoom: Double,
    ) {
        if (!BuildConfig.DEBUG) return
        val map = surface?.mapSurface?.mapboxMap
        val camera = map?.cameraState
        logTiming(
            event,
            "lat=$lat lng=$lng zoom=$zoom hasDrive=${snapshot.hasActiveDriveSession} " +
                "cameraLat=${camera?.center?.latitude()} cameraLng=${camera?.center?.longitude()} cameraZoom=${camera?.zoom}",
        )
    }

    private fun logMarkerRefresh(snapshot: OttoShellUiState) {
        if (!BuildConfig.DEBUG) return
        val groups = presenceGroups(snapshot)
        val presence = groups.size
        val selfGroup = groups.firstOrNull { group -> group.members.any { member -> isSelf(member, snapshot) } }
        val selfLogoUrl =
            to.ottomot.driftd.mapPresenceBrandLogoUrlByUserId(
                members = presenceMembersForCarMap(snapshot),
                meId = snapshot.me?.id,
                selectedSharingCarId = snapshot.selectedSharingCarId,
                garageCars = snapshot.garageCars,
                showsSelfLogo = to.ottomot.driftd.showsSelfDriveBrandLogoOnMap(snapshot),
                context = appContext,
            )[snapshot.me?.id?.trim().orEmpty()]
        val eventGroups =
            if (snapshot.mapLayerShowUpcomingEvents) {
                eventProximityGroupsForMap(nearbyEventsForCarMap(snapshot)).size
            } else {
                0
            }
        val routeMarkers =
            activeRouteForSnapshot(snapshot)
                ?.let { routeMapPointsForCarMap(snapshot, it).size }
                ?: 0
        Log.d(
            "OttoCarMapObserver",
            "Android Auto markers refreshed presence=$presence hazards=${snapshot.activeMapHazards.size} " +
                "savedPlaces=${if (snapshot.mapLayerShowSavedPlaces) snapshot.savedPlaces.size else 0} " +
                "events=$eventGroups tracks=${if (snapshot.mapLayerShowRaceTracks) nearbyRaceTracksForCarMap(snapshot).size else 0} " +
                "routes=$routeMarkers hasDrive=${snapshot.hasActiveDriveSession} " +
                "driveKind=${snapshot.activeDriveSession?.kind} routeId=${snapshot.activeDriveSession?.routeId} " +
                "routeActive=${snapshot.activeRouteDriveSession?.isActive == true} " +
                "trail=${snapshot.activeDrivePathSamples.size} routeTrail=${snapshot.routeDrivePathSamples.size} " +
                "hasMe=${snapshot.me != null} hasFix=${snapshot.deviceLocationFix != null} " +
                "selfFeature=${selfGroup != null} selfLat=${selfGroup?.anchorLat} selfLng=${selfGroup?.anchorLng} " +
                "selectedCar=${snapshot.selectedSharingCarId} garageCars=${snapshot.garageCars.size} selfLogo=${!selfLogoUrl.isNullOrBlank()}",
        )
    }

    private fun installActiveRoute(
        style: Style,
        snapshot: OttoShellUiState,
    ) {
        val activeRoute = activeRouteForSnapshot(snapshot)
        val line =
            snapshot.navigationLineCoordinates
                ?.takeIf { it.size >= 2 }
                ?.map { Point.fromLngLat(it.lng, it.lat) }
                ?: activeRoute?.let { lineCoordinatesFromSavedRoute(it) }
                ?: emptyList()
        if (line.size >= 2) {
            style.installRouteMapLine(SOURCE_ACTIVE_ROUTE, line)
        } else {
            style.removeRouteMapLine(SOURCE_ACTIVE_ROUTE)
        }

        val routePoints =
            activeRoute
                ?.let { routeMapPointsForCarMap(snapshot, it) }
                .orEmpty()
        val completedWaypointIndexes =
            snapshot.activeRouteDriveSession?.completedWaypointIndexes
                ?: snapshot.activeDriveSession?.routeProgress?.completedCheckpointIndexes?.toSet()
                ?: emptySet()
        val latitudeDelta = markerLatitudeDelta(snapshot)
        val pinFeatures = mutableListOf<Feature>()
        val centerFeatures = mutableListOf<Feature>()
        routePoints.forEach { point ->
            val presentation = routeMarkerPresentation(point.markerType, latitudeDelta)
            val isCompleted = point.isRouteDriveCompleted(completedWaypointIndexes)
            val imageId =
                if (presentation == MarkerPresentation.Dot) {
                    markerBitmaps.ensureDotImage(
                        style = style,
                        cacheKey = "route-${point.markerType.orEmpty()}",
                        color = routeDotColor(point.markerType),
                    )
                } else {
                    routeMarkerImageId(point, isCompleted)
                }
            val feature =
                featureOrNull(
                    lng = point.lng,
                    lat = point.lat,
                    iconId = imageId,
                    sortKey = routeMarkerSortKey(point),
                    iconSize =
                        routeMarkerIconSize(point.markerType, latitudeDelta, presentation) *
                            driveHorizonScale(
                                snapshot = snapshot,
                                lat = point.lat,
                                lng = point.lng,
                            ),
                ) ?: return@forEach
            if (presentation == MarkerPresentation.Pin && routeMarkerUsesBottomAnchor(point.markerType)) {
                pinFeatures.add(feature)
            } else {
                centerFeatures.add(feature)
            }
        }
        if (centerFeatures.isNotEmpty()) {
            runCatching { style.removeStyleLayer(LAYER_ROUTE_MARKERS) }
        }
        if (pinFeatures.isNotEmpty()) {
            runCatching { style.removeStyleLayer(LAYER_ROUTE_PIN_MARKERS) }
        }
        installSymbolLayer(
            style,
            SOURCE_ROUTE_MARKERS,
            LAYER_ROUTE_MARKERS,
            features = centerFeatures,
            iconAnchor = IconAnchor.CENTER,
        )
        installSymbolLayer(
            style,
            SOURCE_ROUTE_PIN_MARKERS,
            LAYER_ROUTE_PIN_MARKERS,
            features = pinFeatures,
            iconAnchor = IconAnchor.BOTTOM,
        )
    }

    private fun installActiveTrail(
        style: Style,
        snapshot: OttoShellUiState,
    ) {
        val samples =
            if (snapshot.activeRouteDriveSession?.isActive == true &&
                DriveSpeedGradient.hasUsableSpeedPathData(snapshot.routeDrivePathSamples)
            ) {
                snapshot.routeDrivePathSamples
            } else {
                snapshot.activeDrivePathSamples
            }
        if (DriveSpeedGradient.hasUsableSpeedPathData(samples)) {
            style.installRouteSpeedGradient(SOURCE_ACTIVE_TRAIL, samples)
        } else {
            style.removeRouteSpeedGradient(SOURCE_ACTIVE_TRAIL)
        }
    }

    private fun clearLayers(mapboxCarMapSurface: MapboxCarMapSurface) {
        mapboxCarMapSurface.mapSurface.mapboxMap.getStyle { style ->
            listOf(
                SOURCE_HAZARDS to LAYER_HAZARDS,
                SOURCE_SAVED_PLACES to LAYER_SAVED_PLACES,
                SOURCE_EVENTS to LAYER_EVENTS,
                SOURCE_RACE_TRACKS to LAYER_RACE_TRACKS,
                SOURCE_PRESENCE to LAYER_PRESENCE,
                SOURCE_PRESENCE_COMPOSITE to LAYER_PRESENCE_COMPOSITE,
                SOURCE_ROUTE_MARKERS to LAYER_ROUTE_MARKERS,
                SOURCE_ROUTE_PIN_MARKERS to LAYER_ROUTE_PIN_MARKERS,
            ).forEach { (sourceId, layerId) ->
                removeLayerAndSource(style, layerId, sourceId)
            }
            style.removeRouteMapLine(SOURCE_ACTIVE_ROUTE)
            style.removeRouteSpeedGradient(SOURCE_ACTIVE_TRAIL)
            style.setOttoTrafficLayersVisible(false)
        }
    }

    private fun installSymbolLayer(
        style: Style,
        sourceId: String,
        layerId: String,
        features: List<Feature>,
        iconAnchor: IconAnchor = IconAnchor.BOTTOM,
    ) {
        if (features.isEmpty()) {
            removeLayerAndSource(style, layerId, sourceId)
            return
        }
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source != null) {
            source.featureCollection(collection)
            if (style.styleLayerExists(layerId)) return
        } else {
            style.addSource(geoJsonSource(sourceId) { featureCollection(collection) })
        }
        style.addLayer(symbolLayerFor(layerId, sourceId, iconAnchor))
    }

    private fun symbolLayerFor(
        layerId: String,
        sourceId: String,
        iconAnchor: IconAnchor,
    ) = symbolLayer(layerId, sourceId) {
        iconImage(get(OttoCarMapMarkerBitmaps.PROPERTY_ICON))
        iconAllowOverlap(true)
        iconIgnorePlacement(true)
        iconAnchor(iconAnchor)
        iconSize(get(OttoCarMapMarkerBitmaps.PROPERTY_SIZE))
        symbolSortKey(get(OttoCarMapMarkerBitmaps.PROPERTY_SORT))
    }

    private fun featureOrNull(
        lng: Double,
        lat: Double,
        iconId: String,
        sortKey: Double,
        iconSize: Double = 0.72,
    ): Feature? {
        val point = pointOrNull(lng, lat) ?: return null
        return Feature.fromGeometry(point).apply {
            addStringProperty(OttoCarMapMarkerBitmaps.PROPERTY_ICON, iconId)
            addNumberProperty(OttoCarMapMarkerBitmaps.PROPERTY_SORT, sortKey)
            addNumberProperty(OttoCarMapMarkerBitmaps.PROPERTY_SIZE, iconSize)
        }
    }

    private fun routeMarkerImageId(
        point: RouteMapPoint,
        isCompleted: Boolean,
    ): String =
        when (point.markerType?.lowercase(Locale.US)) {
            "start" -> OttoCarMapMarkerBitmaps.MarkerImage.RouteStart.imageId
            "finish" -> OttoCarMapMarkerBitmaps.MarkerImage.RouteFinish.imageId
            "waypoint" ->
                if (isCompleted) {
                    OttoCarMapMarkerBitmaps.MarkerImage.RouteCheckpointPassed.imageId
                } else {
                    OttoCarMapMarkerBitmaps.MarkerImage.RouteCheckpoint.imageId
                }
            "stop" -> OttoCarMapMarkerBitmaps.MarkerImage.RouteStop.imageId
            else -> OttoCarMapMarkerBitmaps.MarkerImage.RoutePoint.imageId
        }

    private fun activeRouteForSnapshot(snapshot: OttoShellUiState) =
        snapshot.mapSelectedRoute
            ?: snapshot.activeDriveSession
                ?.let { session ->
                    val routeId =
                        session.routeId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: session.routeProgress?.routeId?.trim()?.takeIf { it.isNotEmpty() }
                    routeId?.let { id -> snapshot.routes.find { it.id == id } }
                }

    private fun routeMapPointsForCarMap(
        snapshot: OttoShellUiState,
        route: to.ottomot.driftd.core.network.dto.SavedRouteDto,
    ): List<RouteMapPoint> =
        mapPointsFromSavedRouteForDrive(
            route.points,
            route.id,
            hideStartMarker = snapshot.activeRouteDriveUsesAdhocAndroidAutoDestination,
        )

    private fun nearbyEventsForCarMap(snapshot: OttoShellUiState) =
        (snapshot.events + snapshot.communityEvents + snapshot.squadFeedEvents)
            .filter { event ->
                val (lat, lng) = eventVenueLatLng(event) ?: return@filter false
                isWithinCarMapDiscoveryDistance(snapshot, lat, lng)
            }

    private fun nearbyRaceTracksForCarMap(snapshot: OttoShellUiState) =
        snapshot.raceTracks.filter { track ->
            val (lat, lng) = track.coordinateOrNull() ?: return@filter false
            isWithinCarMapDiscoveryDistance(snapshot, lat, lng)
        }

    private fun isWithinCarMapDiscoveryDistance(
        snapshot: OttoShellUiState,
        lat: Double,
        lng: Double,
    ): Boolean {
        val fix = snapshot.deviceLocationFix ?: return true
        val userLat = followRenderedLat ?: followTargetLat ?: fix.latitude
        val userLng = followRenderedLng ?: followTargetLng ?: fix.longitude
        val distanceMeters = distanceMeters(userLat, userLng, lat, lng)
        val maxMiles = snapshot.selectedEventDistanceMiles.coerceIn(5, 200)
        return distanceMeters <= maxMiles * METERS_PER_MILE
    }

    private fun routeMarkerSortKey(point: RouteMapPoint): Double =
        when (point.markerType?.lowercase(Locale.US)) {
            "start" -> 35.0
            "finish" -> 34.0
            "stop" -> 32.0
            "waypoint" -> 30.0
            else -> 25.0
        }

    private fun markerLatitudeDelta(snapshot: OttoShellUiState): Double {
        val zoom =
            currentCameraZoom
                ?: if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_ZOOM else ANDROID_AUTO_IDLE_ZOOM
        val lat =
            currentCameraLatitude
                ?: followRenderedLat
                ?: followTargetLat
                ?: snapshot.deviceLocationFix?.latitude
                ?: snapshot.presenceMembers.firstOrNull { it.lat != null }?.lat
                ?: 0.0
        return visibleLatitudeDeltaDegrees(
            zoom = zoom,
            latitudeCenterDegrees = lat,
            approximateScreenHeightPx = projectedViewport.heightPx.takeIf { it > 0 }?.toDouble() ?: 640.0,
        )
    }

    private fun presenceIconSize(snapshot: OttoShellUiState): Double {
        val latitudeDelta = markerLatitudeDelta(snapshot)
        val far = PRESENCE_SCALE_FAR_LATITUDE_DELTA
        val close = PRESENCE_SCALE_CLOSE_LATITUDE_DELTA
        val raw =
            when {
                latitudeDelta <= close -> PRESENCE_MAX_ICON_SIZE
                latitudeDelta >= far -> PRESENCE_MIN_ICON_SIZE
                else -> {
                    val t = ((far - latitudeDelta) / (far - close)).coerceIn(0.0, 1.0)
                    PRESENCE_MIN_ICON_SIZE + t * (PRESENCE_MAX_ICON_SIZE - PRESENCE_MIN_ICON_SIZE)
                }
            }
        return (kotlin.math.round(raw / PRESENCE_SCALE_STEP) * PRESENCE_SCALE_STEP).coerceIn(PRESENCE_MIN_ICON_SIZE, PRESENCE_MAX_ICON_SIZE)
    }

    private fun routeMarkerPresentation(
        markerType: String?,
        latitudeDelta: Double,
    ): MarkerPresentation {
        if (markerType == "start" || markerType == "finish") return MarkerPresentation.Pin
        return if (latitudeDelta > ROUTE_REGIONAL_DOT_MIN_LATITUDE_DELTA) MarkerPresentation.Dot else MarkerPresentation.Pin
    }

    private fun routeMarkerUsesBottomAnchor(markerType: String?): Boolean =
        when (markerType?.lowercase(Locale.US)) {
            "start", "finish", "waypoint", "stop" -> true
            else -> false
        }

    private fun routeMarkerIconSize(
        markerType: String?,
        latitudeDelta: Double,
        presentation: MarkerPresentation,
    ): Double {
        if (presentation == MarkerPresentation.Dot) return ROUTE_DOT_ICON_SIZE
        if (markerType == "start" || markerType == "finish") return ROUTE_PIN_BASE_ICON_SIZE
        val far = ROUTE_REGIONAL_DOT_MIN_LATITUDE_DELTA
        val close = ROUTE_PIN_FULL_SIZE_MAX_LATITUDE_DELTA
        if (latitudeDelta <= close) return ROUTE_PIN_BASE_ICON_SIZE
        val t = ((far - latitudeDelta) / (far - close)).coerceIn(0.0, 1.0)
        val scale = ROUTE_SUBTLE_PIN_SCALE + t * (1.0 - ROUTE_SUBTLE_PIN_SCALE)
        return ROUTE_PIN_BASE_ICON_SIZE * scale
    }

    private fun driveHorizonScale(
        snapshot: OttoShellUiState,
        lat: Double?,
        lng: Double?,
        minScale: Double = ROUTE_HORIZON_MIN_SCALE,
    ): Double {
        if (!snapshot.hasActiveDriveSession) return 1.0
        val fix = snapshot.deviceLocationFix ?: return 1.0
        val targetLat = lat?.takeIf { it.isFinite() } ?: return 1.0
        val targetLng = lng?.takeIf { it.isFinite() } ?: return 1.0
        val userLat = followRenderedLat ?: followTargetLat ?: fix.latitude
        val userLng = followRenderedLng ?: followTargetLng ?: fix.longitude
        val distanceMeters = distanceMeters(userLat, userLng, targetLat, targetLng)
        val visibleMapHeightMeters = (markerLatitudeDelta(snapshot) * 111_000.0).coerceAtLeast(50.0)
        val t = (distanceMeters / visibleMapHeightMeters).coerceIn(0.0, 1.0)
        val raw = (1.0 - t * (1.0 - minScale)).coerceAtLeast(minScale)
        return kotlin.math.round(raw / HORIZON_SCALE_STEP) * HORIZON_SCALE_STEP
    }

    private fun distanceMeters(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(fromLat, fromLng, toLat, toLng, results)
        return results[0].toDouble()
    }

    private fun routeDotColor(markerType: String?): Int =
        when (markerType?.lowercase(Locale.US)) {
            "waypoint" -> 0xFF2D7DFF.toInt()
            "stop" -> 0xFFE53935.toInt()
            else -> 0xFF8B5CF6.toInt()
        }

    private fun hazardColor(type: String): Int =
        when (type.lowercase(Locale.US)) {
            "police" -> 0xFF337AFF.toInt()
            "traffic" -> 0xFFFF8A1F.toInt()
            "crash" -> 0xFFF3332E.toInt()
            else -> 0xFFFFC71F.toInt()
        }

    private fun removeLayerAndSource(
        style: Style,
        layerId: String,
        sourceId: String,
    ) {
        runCatching { style.removeStyleLayer(layerId) }
        runCatching { style.removeStyleSource(sourceId) }
    }

    private fun pointOrNull(
        lng: Double,
        lat: Double,
    ): Point? {
        if (!lat.isFinite() || !lng.isFinite()) return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return Point.fromLngLat(lng, lat)
    }

    private companion object {
        const val SOURCE_HAZARDS = "otto-car-hazards"
        const val LAYER_HAZARDS = "otto-car-hazards-layer"
        const val SOURCE_SAVED_PLACES = "otto-car-saved-places"
        const val LAYER_SAVED_PLACES = "otto-car-saved-places-layer"
        const val SOURCE_EVENTS = "otto-car-events"
        const val LAYER_EVENTS = "otto-car-events-layer"
        const val SOURCE_RACE_TRACKS = "otto-car-race-tracks"
        const val LAYER_RACE_TRACKS = "otto-car-race-tracks-layer"
        const val SOURCE_PRESENCE = "otto-car-presence"
        const val LAYER_PRESENCE = "otto-car-presence-layer"
        const val SOURCE_PRESENCE_COMPOSITE = "otto-car-presence-composite"
        const val LAYER_PRESENCE_COMPOSITE = "otto-car-presence-composite-layer"
        const val SOURCE_ROUTE_MARKERS = "otto-car-route-markers"
        const val LAYER_ROUTE_MARKERS = "otto-car-route-markers-layer"
        const val SOURCE_ROUTE_PIN_MARKERS = "otto-car-route-pin-markers"
        const val LAYER_ROUTE_PIN_MARKERS = "otto-car-route-pin-markers-layer"
        const val SOURCE_ACTIVE_ROUTE = "otto-car-active-route"
        const val SOURCE_ACTIVE_TRAIL = "otto-car-active-trail"
        const val ANDROID_AUTO_IDLE_ZOOM = 16.2
        const val ANDROID_AUTO_FALLBACK_LATITUDE = 37.7749
        const val ANDROID_AUTO_FALLBACK_LONGITUDE = -122.4194
        const val FOLLOW_CAMERA_FRAME_DELAY_MS = 16L
        const val ANDROID_AUTO_DRIVE_VIEWPORT_FALLBACK_PX = 400f
        val ZERO_CAMERA_PADDING = EdgeInsets(0.0, 0.0, 0.0, 0.0)
        const val ROUTE_REGIONAL_DOT_MIN_LATITUDE_DELTA = (5 * 1609.344) / 111_000.0
        const val ROUTE_PIN_FULL_SIZE_MAX_LATITUDE_DELTA = (1_000 * 0.3048) / 111_000.0
        const val ROUTE_SUBTLE_PIN_SCALE = 0.55
        const val ROUTE_PIN_BASE_ICON_SIZE = 0.36
        const val ROUTE_DOT_ICON_SIZE = 0.55
        const val PRESENCE_SCALE_FAR_LATITUDE_DELTA = (2 * 1609.344) / 111_000.0
        const val PRESENCE_SCALE_CLOSE_LATITUDE_DELTA = (800 * 0.3048) / 111_000.0
        const val PRESENCE_MIN_ICON_SIZE = 0.4375
        const val PRESENCE_MAX_ICON_SIZE = 0.6475
        const val PRESENCE_SCALE_STEP = 0.05
        const val PRESENCE_ICON_RENDER_SCALE = 0.35
        const val HAZARD_TO_PRESENCE_ICON_SCALE = 0.60
        const val ROUTE_HORIZON_MIN_SCALE = 0.55
        const val PRESENCE_HORIZON_MIN_SCALE = 0.50
        const val HORIZON_SCALE_STEP = 0.05
        const val METERS_PER_MILE = 1609.344
    }

    private enum class MarkerPresentation {
        Dot,
        Pin,
    }
}
