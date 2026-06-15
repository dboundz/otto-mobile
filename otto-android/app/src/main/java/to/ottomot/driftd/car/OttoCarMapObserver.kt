package to.ottomot.driftd.car

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
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

internal class OttoCarMapObserver(
    context: Context,
    private val state: StateFlow<OttoShellUiState>,
) : MapboxCarMapObserver {
    private val appContext = context.applicationContext
    private val markerBitmaps =
        OttoCarMapMarkerBitmaps(appContext) {
            render(state.value, forceMarkers = true)
        }
    private var surface: MapboxCarMapSurface? = null
    private var collectJob: Job? = null
    private var previousFix = state.value.deviceLocationFix
    private var lastBearing = 0f
    private var lastMarkerFingerprint: MarkerRenderFingerprint? = null
    private var lastTemplateFingerprint: TemplateRenderFingerprint? = null
    private var diagnosticsStartElapsedMs = SystemClock.elapsedRealtime()
    private var didLogFirstFix = false
    private var didLogFirstStyle = false
    private var didLogFirstSelfMarker = false

    fun start(
        owner: LifecycleOwner,
        invalidate: () -> Unit,
    ) {
        stop()
        diagnosticsStartElapsedMs = SystemClock.elapsedRealtime()
        didLogFirstFix = false
        didLogFirstStyle = false
        didLogFirstSelfMarker = false
        logTiming("observer start")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        collectJob =
            scope.launch {
                state.collect { snapshot ->
                    render(snapshot)
                    val templateFingerprint = TemplateRenderFingerprint(snapshot.hasActiveDriveSession)
                    if (templateFingerprint != lastTemplateFingerprint) {
                        lastTemplateFingerprint = templateFingerprint
                        invalidate()
                    }
                }
            }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        lastTemplateFingerprint = null
    }

    override fun onAttached(mapboxCarMapSurface: MapboxCarMapSurface) {
        surface = mapboxCarMapSurface
        logTiming("surface attached")
        lastMarkerFingerprint = null
        render(state.value, forceMarkers = true)
    }

    override fun onDetached(mapboxCarMapSurface: MapboxCarMapSurface) {
        clearLayers(mapboxCarMapSurface)
        if (surface === mapboxCarMapSurface) {
            surface = null
        }
        lastMarkerFingerprint = null
        logTiming("surface detached")
    }

    fun recenterOnUser() {
        val snapshot = state.value
        val fix = snapshot.deviceLocationFix ?: return
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        surface?.mapSurface?.mapboxMap?.setCamera(cameraOptionsForFix(snapshot, fix))
    }

    private fun render(
        snapshot: OttoShellUiState,
        forceMarkers: Boolean = false,
    ) {
        val currentSurface = surface ?: return
        val map = currentSurface.mapSurface.mapboxMap
        val fix = snapshot.deviceLocationFix
        if (fix != null) {
            if (!didLogFirstFix) {
                didLogFirstFix = true
                logTiming("first device fix", "lat=${fix.latitude} lng=${fix.longitude} accuracy=${fix.accuracyMeters}")
            }
            val bearing = MapDriveCamera.driveBearing(fix, previousFix, lastBearing)
            lastBearing = bearing
            previousFix = fix
            map.setCamera(cameraOptionsForFix(snapshot, fix, bearing))
        }

        val markerFingerprint = markerRenderFingerprint(snapshot)
        if (!forceMarkers && markerFingerprint == lastMarkerFingerprint) return
        lastMarkerFingerprint = markerFingerprint

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
        }
    }

    private fun cameraOptionsForFix(
        snapshot: OttoShellUiState,
        fix: to.ottomot.driftd.core.location.LocationFix,
        bearing: Float = lastBearing,
    ): CameraOptions =
        CameraOptions.Builder()
            .center(Point.fromLngLat(fix.longitude, fix.latitude))
            .zoom(if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_ZOOM else ANDROID_AUTO_IDLE_ZOOM)
            .pitch(if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_PITCH_DEGREES else 0.0)
            .bearing(if (snapshot.hasActiveDriveSession) bearing.toDouble() else 0.0)
            .padding(if (snapshot.hasActiveDriveSession) ANDROID_AUTO_DRIVE_CAMERA_PADDING else ZERO_CAMERA_PADDING)
            .build()

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
        val iconSize = presenceIconSize(snapshot)
        val brandLogoUrlsByUserId =
            to.ottomot.driftd.mapPresenceBrandLogoUrlByUserId(
                members = presenceMembersForCarMap(snapshot),
                meId = snapshot.me?.id,
                selectedSharingCarId = snapshot.selectedSharingCarId,
                garageCars = snapshot.garageCars,
                showsSelfLogo = to.ottomot.driftd.showsSelfDriveBrandLogoOnMap(snapshot),
                context = appContext,
            )
        val features =
            groups.mapNotNull { group ->
                val imageId =
                    markerBitmaps.ensurePresenceImage(
                        style = style,
                        group = group,
                        contacts = snapshot.contacts,
                        me = snapshot.me,
                        brandLogoUrlsByUserId = brandLogoUrlsByUserId,
                    )
                val containsSelf = group.members.any { member -> isSelf(member, snapshot) }
                featureOrNull(
                    lng = group.anchorLng,
                    lat = group.anchorLat,
                    iconId = imageId,
                    sortKey = if (containsSelf) 52.0 else 45.0,
                    iconSize =
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
                            },
                )
            }
        if (features.isNotEmpty()) {
            runCatching { style.removeStyleLayer(LAYER_PRESENCE) }
        }
        installSymbolLayer(
            style,
            SOURCE_PRESENCE,
            LAYER_PRESENCE,
            features = features,
            iconAnchor = IconAnchor.CENTER,
        )
    }

    private fun presenceGroups(snapshot: OttoShellUiState) =
        groupNearbyPresence(
            members = presenceMembersForCarMap(snapshot),
            thresholdMeters = 34f,
            meUserId = snapshot.me?.id,
        )

    private fun presenceMembersForCarMap(snapshot: OttoShellUiState): List<PresenceMemberDto> {
        val plotted =
            snapshot.presenceMembers
                .filter { member ->
                    member.userId.trim().isNotEmpty() &&
                        member.lat?.isFinite() == true &&
                        member.lng?.isFinite() == true &&
                        member.isActive
                }.toMutableList()
        val me = snapshot.me
        val fix = snapshot.deviceLocationFix
        if (me != null && fix != null && fix.latitude.isFinite() && fix.longitude.isFinite()) {
            val self =
                PresenceMemberDto(
                    userId = me.id,
                    circleId = snapshot.mapPresenceCircleId.ifBlank { OttoShellUiState.PublicPresenceChannelId },
                    isActive = true,
                    inApp = true,
                    speedMph = fix.speedMps?.let { (it * 2.23694).toDouble() },
                    movementMode = snapshot.deviceMovementMode,
                    lat = fix.latitude,
                    lng = fix.longitude,
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
        val activeTrailCount: Int,
        val routeTrailCount: Int,
        val presence: List<String>,
        val hazardsHash: Int,
        val savedPlacesHash: Int,
        val eventsHash: Int,
        val raceTracksHash: Int,
        val selectedRouteHash: Int,
    )

    private data class TemplateRenderFingerprint(
        val hasActiveDriveSession: Boolean,
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
                ?.let { mapPointsFromSavedRouteForDrive(it.points, it.id).size }
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
        val line = activeRoute?.let { lineCoordinatesFromSavedRoute(it) }.orEmpty()
        if (line.size >= 2) {
            style.installRouteMapLine(SOURCE_ACTIVE_ROUTE, line)
        } else {
            style.removeRouteMapLine(SOURCE_ACTIVE_ROUTE)
        }

        val routePoints =
            activeRoute
                ?.let { mapPointsFromSavedRouteForDrive(it.points, it.id) }
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
        val distanceMeters = distanceMeters(fix.latitude, fix.longitude, lat, lng)
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
        val zoom = if (snapshot.hasActiveDriveSession) MapDriveCamera.DRIVE_ZOOM else ANDROID_AUTO_IDLE_ZOOM
        val lat = snapshot.deviceLocationFix?.latitude ?: snapshot.presenceMembers.firstOrNull { it.lat != null }?.lat ?: 0.0
        return visibleLatitudeDeltaDegrees(zoom, lat)
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
        val distanceMeters = distanceMeters(fix.latitude, fix.longitude, targetLat, targetLng)
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
        const val SOURCE_ROUTE_MARKERS = "otto-car-route-markers"
        const val LAYER_ROUTE_MARKERS = "otto-car-route-markers-layer"
        const val SOURCE_ROUTE_PIN_MARKERS = "otto-car-route-pin-markers"
        const val LAYER_ROUTE_PIN_MARKERS = "otto-car-route-pin-markers-layer"
        const val SOURCE_ACTIVE_ROUTE = "otto-car-active-route"
        const val SOURCE_ACTIVE_TRAIL = "otto-car-active-trail"
        const val ANDROID_AUTO_IDLE_ZOOM = 16.2
        val ANDROID_AUTO_DRIVE_CAMERA_PADDING = EdgeInsets(240.0, 0.0, 0.0, 0.0)
        val ZERO_CAMERA_PADDING = EdgeInsets(0.0, 0.0, 0.0, 0.0)
        const val ROUTE_REGIONAL_DOT_MIN_LATITUDE_DELTA = (5 * 1609.344) / 111_000.0
        const val ROUTE_PIN_FULL_SIZE_MAX_LATITUDE_DELTA = (1_000 * 0.3048) / 111_000.0
        const val ROUTE_SUBTLE_PIN_SCALE = 0.55
        const val ROUTE_PIN_BASE_ICON_SIZE = 0.36
        const val ROUTE_DOT_ICON_SIZE = 0.55
        const val PRESENCE_SCALE_FAR_LATITUDE_DELTA = (2 * 1609.344) / 111_000.0
        const val PRESENCE_SCALE_CLOSE_LATITUDE_DELTA = (800 * 0.3048) / 111_000.0
        const val PRESENCE_MIN_ICON_SIZE = 0.25
        const val PRESENCE_MAX_ICON_SIZE = 0.37
        const val PRESENCE_SCALE_STEP = 0.05
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
