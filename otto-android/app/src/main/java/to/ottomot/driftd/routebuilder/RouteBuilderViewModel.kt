package to.ottomot.driftd.routebuilder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator
import to.ottomot.driftd.routebuilder.engine.RouteAutoPathPointGenerator
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry
import to.ottomot.driftd.routebuilder.engine.RoutePolylineIndex
import to.ottomot.driftd.routebuilder.engine.RoutePolylineProjection
import to.ottomot.driftd.routebuilder.engine.RoutePolylineTurnExtractor
import to.ottomot.driftd.routebuilder.engine.RouteRoadSnapper
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng
import kotlin.math.abs
import kotlin.math.min

@Immutable
data class RouteBuilderMapContentState(
    val lineFingerprint: String,
    val lineCoordinates: List<Point>,
    val markers: List<RouteBuilderMapMarkerSnapshot>,
    val allowsInteraction: Boolean,
)

/** Structural marker identity — add/remove/move/type changes (excludes pinScale). */
fun RouteBuilderMapContentState.markersFingerprint(): String =
    markers.joinToString("|") { "${it.id};${it.lat};${it.lng};${it.markerType};${it.refreshId}" }

/** Marker appearance changes only when presentation or pin-scale bucket refresh IDs change. */
fun RouteBuilderMapContentState.markersAppearanceFingerprint(): String = markersFingerprint()

data class RouteBuilderScreenState(
    val editingRouteId: String? = null,
    val routeName: String = "New Route",
    val points: List<RouteBuilderPoint> = emptyList(),
    val roadCoordinates: List<RouteLatLng> = emptyList(),
    val distanceMeters: Double = 0.0,
    val travelSeconds: Double = 0.0,
    val isSnapping: Boolean = false,
    val didSnapToRoad: Boolean = false,
    val isSaving: Boolean = false,
    val isManualMode: Boolean = false,
    val isEditMode: Boolean = false,
    val isRunningGuidedGeneration: Boolean = false,
    val hasCompletedGuidedGeneration: Boolean = false,
    val guidedBackToStartStep: Boolean = false,
    val selectedCheckpointDensity: CheckpointDensityTier = CheckpointDensityTier.RECOMMENDED,
    val activeCheckpointSpacingMeters: Double? = null,
    val movingPointId: String? = null,
    val markerActionPointId: String? = null,
    val routePointPendingDeleteId: String? = null,
    val isShowingRouteNamePrompt: Boolean = false,
    val routeNameDraft: String = "",
    val isShowingDiscardChangesAlert: Boolean = false,
    val showRouteEducation: Boolean = false,
    val showLocationPrimer: Boolean = false,
    val errorMessage: String? = null,
    val userToastMessage: String? = null,
    val mapContainerWidthPx: Float = 0f,
    val mapContainerHeightPx: Float = 0f,
    val mapBottomSafeAreaPx: Float = 0f,
    val bottomSheetVisibleHeightPx: Float = 0f,
    val crosshairPlacementCoordinate: RouteLatLng? = null,
    val cameraRegion: RouteBuilderCameraRegion = defaultCameraRegion(),
    val mapMarkerLodTier: RouteBuilderMapMarkerLodTier = RouteBuilderMapMarkerLodTier.STREET,
    val mapVisibleLatitudeDelta: Double = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
    val programmaticCameraTarget: RouteBuilderCameraTarget? = null,
    val didApplyUserLocationRecenter: Boolean = false,
    val canUndoEditSession: Boolean = false,
    val saveIsActive: Boolean = false,
    val uiState: RouteBuilderUiState = RouteBuilderUiState.SET_START,
    val mapContent: RouteBuilderMapContentState = RouteBuilderMapContentState("", emptyList(), emptyList(), true),
    val bottomSheetHeightDp: Int = 270,
    val recenterEnabled: Boolean = false,
    val markerDebugSnapshot: RouteBuilderMarkerDebugSnapshot? = null,
) {
    val checkpointCount: Int get() = points.count { it.type == RouteBuilderPointType.WAYPOINT }
    val showsEditSessionUndo: Boolean get() = uiState == RouteBuilderUiState.EDIT_ROUTE || uiState == RouteBuilderUiState.MANUAL_PLOT
    val isMapInteractionDisabled: Boolean get() = uiState == RouteBuilderUiState.GENERATING_ROUTE
    val canDecreaseCheckpointDensity: Boolean get() = canStepCheckpointDensity(-1)
    val canIncreaseCheckpointDensity: Boolean get() = canStepCheckpointDensity(1)

    private fun canStepCheckpointDensity(direction: Int): Boolean {
        if (roadCoordinates.size < 2) return false
        val options = sortedCheckpointSpacingOptions()
        if (options.size <= 1) return false
        val nextIndex = currentCheckpointSpacingIndex(options) + direction
        if (nextIndex !in options.indices) return false
        return options[nextIndex].spacingMeters != options[currentCheckpointSpacingIndex(options)].spacingMeters
    }

    private fun sortedCheckpointSpacingOptions(): List<RouteAutoCheckpointGenerator.IntervalOption> =
        RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates).sortedByDescending { it.spacingMeters }

    private fun currentCheckpointSpacingIndex(options: List<RouteAutoCheckpointGenerator.IntervalOption>): Int {
        activeCheckpointSpacingMeters?.let { active ->
            options.indexOfFirst { it.spacingMeters == active }.takeIf { it >= 0 }?.let { return it }
        }
        if (options.isEmpty()) return 0
        val currentCount = checkpointCount
        return options.withIndex().minWithOrNull(
            compareBy<IndexedValue<RouteAutoCheckpointGenerator.IntervalOption>> {
                abs(it.value.checkpointCount - currentCount)
            }.thenBy { it.index },
        )?.index ?: 0
    }
}

sealed class RouteBuilderEvent {
    data object Dismiss : RouteBuilderEvent()

    data class RouteSaved(val route: SavedRouteDto) : RouteBuilderEvent()

    data class RequestLocationPermission(val continueAfterGrant: Boolean = true) : RouteBuilderEvent()

    data object RequestLocationSync : RouteBuilderEvent()

    data class RouteBuilderPresented(val presented: Boolean) : RouteBuilderEvent()
}

class RouteBuilderViewModel(
    private val repository: OttoDataRepository,
    private val prefs: SharedPreferences,
    private val accessToken: String,
) : ViewModel() {
    private val roadSnapper = RouteRoadSnapper(accessToken)
    private val _state = MutableStateFlow(RouteBuilderScreenState())
    val state: StateFlow<RouteBuilderScreenState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RouteBuilderEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RouteBuilderEvent> = _events.asSharedFlow()

    private var editBaseline: RouteEditBaseline? = null
    private var editUndoStack = ArrayDeque<RouteBuilderEditSnapshot>()
    private var lastSnapTurnCoordinates: List<RouteLatLng> = emptyList()
    private var roadPolylineIndex: RoutePolylineIndex? = null
    private var pendingCheckpointRegeneration: PendingCheckpointRegeneration? = null
    private var snapJob: Job? = null
    private var polylineIndexJob: Job? = null
    private var autoPathBootstrapJob: Job? = null
    private var cameraSettleJob: Job? = null
    private var initialCenter: RouteLatLng = DEFAULT_FALLBACK_CENTER
    private var latestLocationNotDetermined: Boolean? = null
    /** Live camera from Mapbox — updated every frame without invalidating UI state. */
    private var latestCameraRegion: RouteBuilderCameraRegion = defaultCameraRegion()

    fun openNewRoute(
        centerLat: Double,
        centerLng: Double,
    ) {
        initialCenter = centerLat to centerLng
        val zoom = RouteBuilderMarkerLod.zoomForCloseStreetLevel(centerLat)
        val region =
            RouteBuilderCameraRegion(
                centerLat = centerLat,
                centerLng = centerLng,
                latitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
                longitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
            )
        editBaseline = null
        editUndoStack.clear()
        lastSnapTurnCoordinates = emptyList()
        roadPolylineIndex = null
        pendingCheckpointRegeneration = null
        latestCameraRegion = region
        _state.value =
            RouteBuilderScreenState(
                routeName = "New Route",
                cameraRegion = region,
                mapVisibleLatitudeDelta = region.latitudeDelta,
                mapMarkerLodTier = RouteBuilderMapMarkerLodTier.from(region.latitudeDelta),
                programmaticCameraTarget = RouteBuilderCameraTarget(centerLat, centerLng, zoom),
            ).recomputed()
        emitPresented(true)
        _events.tryEmit(RouteBuilderEvent.RequestLocationSync)
        presentRouteBuilderPrimersIfNeeded()
    }

    fun openEditRoute(route: SavedRouteDto) {
        val routePoints =
            route.points.orEmpty().mapNotNull { dto ->
                val type = RouteBuilderPointType.entries.firstOrNull { it.rawValue == dto.markerType } ?: RouteBuilderPointType.PATH
                if (!dto.lat.isFinite() || !dto.lng.isFinite()) return@mapNotNull null
                RouteBuilderPoint(lat = dto.lat, lng = dto.lng, type = type)
            }
        val roadCoords =
            route.roadCoordinates.orEmpty().mapNotNull { dto ->
                if (!dto.lat.isFinite() || !dto.lng.isFinite()) null else dto.lat to dto.lng
            }
        val displayCoords = if (roadCoords.size >= 2) roadCoords else routePoints.map { it.lat to it.lng }
        val center = centerCoordinate(displayCoords)
        val region = cappedRouteFitRegion(displayCoords, center)
        val hasStart = routePoints.any { it.type == RouteBuilderPointType.START }
        val hasFinish = routePoints.any { it.type == RouteBuilderPointType.FINISH }
        val opensInEditMode = hasStart && hasFinish
        val waypointCount = routePoints.count { it.type == RouteBuilderPointType.WAYPOINT }
        val inferredSpacing = inferredCheckpointSpacingMeters(waypointCount, roadCoords)
        initialCenter = center
        editUndoStack.clear()
        lastSnapTurnCoordinates = emptyList()
        roadPolylineIndex = null
        pendingCheckpointRegeneration = null
        editBaseline =
            RouteEditBaseline.capture(
                name = route.name,
                points = routePoints,
                roadCoordinates = roadCoords,
                distanceMeters = route.distanceMeters ?: 0.0,
                travelSeconds = route.etaSeconds ?: 0.0,
            )
        latestCameraRegion = region
        _state.value =
            RouteBuilderScreenState(
                editingRouteId = route.id,
                routeName = route.name,
                points = routePoints,
                roadCoordinates = roadCoords,
                distanceMeters = route.distanceMeters ?: 0.0,
                travelSeconds = route.etaSeconds ?: 0.0,
                didSnapToRoad = roadCoords.size >= 2,
                activeCheckpointSpacingMeters = inferredSpacing,
                isManualMode = opensInEditMode,
                isEditMode = opensInEditMode,
                cameraRegion = region,
                mapVisibleLatitudeDelta = region.latitudeDelta,
                mapMarkerLodTier = RouteBuilderMapMarkerLodTier.from(region.latitudeDelta),
                programmaticCameraTarget =
                    RouteBuilderCameraTarget(
                        lat = region.centerLat,
                        lng = region.centerLng,
                        zoom = RouteBuilderMarkerLod.zoomForLatitudeDelta(region.latitudeDelta, region.centerLat),
                    ),
            ).recomputed()
        scheduleRoadPolylineIndexBuild(bootstrapAutoPathAfterIndex = true)
        viewModelScope.launch {
            delay(450)
            scheduleCameraSettle(force = true)
        }
        emitPresented(true)
        if (!routeBuilderEducationSeen()) {
            presentRouteBuilderPrimersIfNeeded()
        }
    }

    fun onAppear() {
        emitPresented(true)
        captureInitialEditBaselineIfNeeded()
    }

    fun onDisappear() {
        snapJob?.cancel()
        polylineIndexJob?.cancel()
        autoPathBootstrapJob?.cancel()
        cameraSettleJob?.cancel()
        emitPresented(false)
    }

    fun updateMapLayout(
        widthPx: Float,
        heightPx: Float,
        bottomSafeAreaPx: Float,
        bottomSheetVisibleHeightPx: Float,
    ) {
        _state.update {
            it.copy(
                mapContainerWidthPx = widthPx,
                mapContainerHeightPx = heightPx,
                mapBottomSafeAreaPx = bottomSafeAreaPx,
                bottomSheetVisibleHeightPx = bottomSheetVisibleHeightPx,
            )
        }
    }

    fun updateCrosshairPlacementCoordinate(coordinate: RouteLatLng?) {
        _state.update { it.copy(crosshairPlacementCoordinate = coordinate) }
    }

    fun onCameraChanged(
        centerLat: Double,
        centerLng: Double,
        zoom: Double,
    ) {
        val placementRegion = RouteBuilderPlacement.cameraRegionFromMapbox(centerLat, centerLng, zoom)
        latestCameraRegion = placementRegion
        if (_state.value.programmaticCameraTarget != null) {
            _state.update { it.copy(programmaticCameraTarget = null) }
        }
    }

    fun onGestureStarted() {
        cameraSettleJob?.cancel()
        autoPathBootstrapJob?.cancel()
    }

    fun onGestureEnded() {
        _state.update {
            it.copy(
                cameraRegion = latestCameraRegion,
                programmaticCameraTarget = null,
            )
        }
        scheduleCameraSettle(force = false)
    }

    fun updateLocationContext(
        locationGranted: Boolean,
        locationNotDetermined: Boolean,
        userLat: Double?,
        userLng: Double?,
    ) {
        latestLocationNotDetermined = locationNotDetermined
        val recenterEnabled = locationGranted && userLat != null && userLng != null
        _state.update { it.copy(recenterEnabled = recenterEnabled).recomputed() }
        if (_state.value.editingRouteId == null && !locationNotDetermined) {
            applyNewRouteUserLocationIfAvailable(userLat, userLng, locationGranted)
        }
        if (
            routeBuilderEducationSeen() &&
            !_state.value.showRouteEducation &&
            _state.value.editingRouteId == null
        ) {
            maybePresentLocationPrimer(locationNotDetermined)
        }
    }

    fun dismissEducation() {
        prefs.edit().putBoolean(RouteBuilderConstants.ROUTE_BUILDER_EDUCATION_SEEN_KEY, true).apply()
        _state.update { it.copy(showRouteEducation = false).recomputed() }
        if (_state.value.editingRouteId == null) {
            latestLocationNotDetermined?.let(::maybePresentLocationPrimer)
        }
    }

    fun dismissEducationNotNow() {
        dismissEducation()
    }

    fun dismissLocationPrimer(requestAuth: Boolean) {
        _state.update { it.copy(showLocationPrimer = false).recomputed() }
        if (requestAuth) {
            _events.tryEmit(RouteBuilderEvent.RequestLocationPermission())
            _events.tryEmit(RouteBuilderEvent.RequestLocationSync)
        }
    }

    fun onLocationPermissionResult(
        granted: Boolean,
        userLat: Double?,
        userLng: Double?,
    ) {
        if (granted) {
            _events.tryEmit(RouteBuilderEvent.RequestLocationSync)
        }
        applyNewRouteUserLocationIfAvailable(userLat, userLng, granted)
    }

    fun recenterOnUser(
        userLat: Double?,
        userLng: Double?,
        locationGranted: Boolean,
    ) {
        if (!locationGranted) return
        _events.tryEmit(RouteBuilderEvent.RequestLocationSync)
        val lat = userLat ?: return
        val lng = userLng ?: return
        recenterMap(lat, lng, markUserRecenter = false)
    }

    fun attemptClose() {
        if (hasUnsavedChanges()) {
            _state.update { it.copy(isShowingDiscardChangesAlert = true).recomputed() }
        } else {
            dismiss()
        }
    }

    fun confirmDiscard() {
        _state.update { it.copy(isShowingDiscardChangesAlert = false).recomputed() }
        dismiss()
    }

    fun cancelDiscard() {
        _state.update { it.copy(isShowingDiscardChangesAlert = false).recomputed() }
    }

    fun dismiss() {
        _events.tryEmit(RouteBuilderEvent.Dismiss)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null).recomputed() }
    }

    fun clearUserToast() {
        _state.update { it.copy(userToastMessage = null).recomputed() }
    }

    fun markerDebugInputs(): RouteBuilderMarkerDebugInputs? {
        if (!BuildConfig.DEBUG) return null
        return RouteBuilderMarkerDebugInputs(
            screenState = _state.value,
            lastSnapTurnCount = lastSnapTurnCoordinates.size,
        )
    }

    fun updateMarkerDebugSnapshot(snapshot: RouteBuilderMarkerDebugSnapshot) {
        if (!BuildConfig.DEBUG) return
        _state.update { it.copy(markerDebugSnapshot = snapshot) }
    }

    fun placeStartFromGuidedFlow() {
        _state.update { it.copy(guidedBackToStartStep = false).recomputed() }
        placePoint(RouteBuilderPointType.START)
    }

    fun placeFinishForGuidedFlow() {
        val triggerGuided = !_state.value.isManualMode && !_state.value.isEditMode
        placePoint(RouteBuilderPointType.FINISH, triggerGuidedGeneration = triggerGuided)
    }

    fun enterManualMode() {
        editUndoStack.clear()
        _state.update {
            it.copy(
                isManualMode = true,
                isEditMode = false,
                hasCompletedGuidedGeneration = false,
                isRunningGuidedGeneration = false,
            ).recomputed()
        }
    }

    fun enterEditModeFromRouteReady() {
        editUndoStack.clear()
        _state.update {
            it.copy(isEditMode = true, isManualMode = true).recomputed()
        }
    }

    fun goBackFromSetFinish() {
        _state.update { current ->
            current.copy(
                guidedBackToStartStep = true,
                points =
                    current.points.filter {
                        it.type != RouteBuilderPointType.FINISH && !it.isAutoShape
                    },
                roadCoordinates = emptyList(),
                didSnapToRoad = false,
                distanceMeters = 0.0,
                travelSeconds = 0.0,
                isRunningGuidedGeneration = false,
                hasCompletedGuidedGeneration = false,
            ).recomputed()
        }
        lastSnapTurnCoordinates = emptyList()
        roadPolylineIndex = null
    }

    fun goBackFromRouteReady() {
        editUndoStack.clear()
        pendingCheckpointRegeneration = null
        lastSnapTurnCoordinates = emptyList()
        _state.update { current ->
            current.copy(
                hasCompletedGuidedGeneration = false,
                guidedBackToStartStep = false,
                selectedCheckpointDensity = CheckpointDensityTier.RECOMMENDED,
                activeCheckpointSpacingMeters = null,
                points =
                    current.points.filter {
                        it.type != RouteBuilderPointType.WAYPOINT &&
                            !it.isAutoShape &&
                            it.type != RouteBuilderPointType.FINISH
                    },
                roadCoordinates = emptyList(),
                didSnapToRoad = false,
                distanceMeters = 0.0,
                travelSeconds = 0.0,
                isRunningGuidedGeneration = false,
            ).recomputed()
        }
        roadPolylineIndex = null
    }

    fun placePoint(type: RouteBuilderPointType) {
        placePoint(type, triggerGuidedGeneration = false)
    }

    fun shapeRoute() = placePoint(RouteBuilderPointType.PATH)

    fun addCheckpoint() = placePoint(RouteBuilderPointType.WAYPOINT)

    fun addStop() = placePoint(RouteBuilderPointType.STOP)

    fun stepCheckpointDensity(direction: Int) {
        val options = RouteAutoCheckpointGenerator.viableIntervals(_state.value.roadCoordinates).sortedByDescending { it.spacingMeters }
        if (options.size <= 1) return
        val currentIndex = currentCheckpointSpacingIndex(options)
        val nextIndex = currentIndex + direction
        if (nextIndex !in options.indices) return
        val next = options[nextIndex]
        if (next.spacingMeters == options[currentIndex].spacingMeters) return
        replaceAutoCheckpoints(next.spacingMeters)
        syncSelectedCheckpointDensity(next)
    }

    fun undoEditSessionChange() {
        val snapshot = editUndoStack.removeLastOrNull() ?: return
        snapJob?.cancel()
        _state.update {
            it.copy(
                isSnapping = false,
                isRunningGuidedGeneration = false,
                movingPointId = null,
                markerActionPointId = null,
                routePointPendingDeleteId = null,
            ).recomputed()
        }
        applyEditSnapshot(snapshot)
    }

    fun cancelMovePoint() {
        _state.update { it.copy(movingPointId = null).recomputed() }
    }

    fun moveSelectedPointToCenterPin() {
        val movingId = _state.value.movingPointId ?: return
        val index = _state.value.points.indexOfFirst { it.id == movingId }
        if (index < 0) return
        if (_state.value.showsEditSessionUndo) {
            recordEditUndoSnapshotBeforeChange()
        }
        val original = _state.value.points[index]
        val raw = currentPlacementCoordinate()
        val newCoordinate = snappedCoordinateForPlacement(original.type, raw)
        applyMovedPoint(original, index, newCoordinate)
    }

    fun handleMarkerLongPress(pointId: String) {
        if (_state.value.uiState == RouteBuilderUiState.GENERATING_ROUTE) return
        _state.update { it.copy(markerActionPointId = pointId).recomputed() }
    }

    fun beginMoveMarker(pointId: String) {
        _state.update {
            it.copy(
                movingPointId = pointId,
                markerActionPointId = null,
                userToastMessage = "Move the map under the crosshair, then tap Move Here",
            ).recomputed()
        }
    }

    fun requestDeleteMarker(pointId: String) {
        _state.update {
            it.copy(
                routePointPendingDeleteId = pointId,
                markerActionPointId = null,
            ).recomputed()
        }
    }

    fun cancelMarkerAction() {
        _state.update { it.copy(markerActionPointId = null).recomputed() }
    }

    fun confirmDeleteMarker() {
        val pendingId = _state.value.routePointPendingDeleteId ?: return
        val point = _state.value.points.firstOrNull { it.id == pendingId } ?: run {
            _state.update { it.copy(routePointPendingDeleteId = null).recomputed() }
            return
        }
        if (point.type == RouteBuilderPointType.START || point.type == RouteBuilderPointType.FINISH) {
            _state.update { it.copy(routePointPendingDeleteId = null).recomputed() }
            return
        }
        if (_state.value.showsEditSessionUndo) {
            recordEditUndoSnapshotBeforeChange()
        }
        val nextPoints = _state.value.points.filter { it.id != pendingId }
        _state.update {
            it.copy(
                points = nextPoints,
                routePointPendingDeleteId = null,
                movingPointId = it.movingPointId?.takeUnless { id -> id == pendingId },
                markerActionPointId = null,
            ).recomputed()
        }
        if (shouldRebuildRouteAfterPointMutation(point.type)) {
            rebuildRoadPath()
        }
    }

    fun cancelDeleteMarker() {
        _state.update { it.copy(routePointPendingDeleteId = null).recomputed() }
    }

    fun handleMapLongPress(
        lat: Double,
        lng: Double,
    ) = Unit

    fun beginSaveFlow() {
        if (!validateSavePreconditions()) return
        if (_state.value.editingRouteId == null) {
            val trimmed = _state.value.routeName.trim()
            _state.update {
                it.copy(
                    isShowingRouteNamePrompt = true,
                    routeNameDraft = if (trimmed == "New Route") "" else trimmed,
                ).recomputed()
            }
        } else {
            viewModelScope.launch { saveRoute(_state.value.routeName) }
        }
    }

    fun updateRouteNameDraft(value: String) {
        _state.update { it.copy(routeNameDraft = value).recomputed() }
    }

    fun cancelRouteNamePrompt() {
        _state.update { it.copy(isShowingRouteNamePrompt = false).recomputed() }
    }

    fun attemptSaveNamedRoute() {
        if (!validateSavePreconditions()) return
        val trimmed = _state.value.routeNameDraft.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { saveRoute(trimmed) }
    }

    private suspend fun saveRoute(name: String) {
        applyPendingCheckpointRegenerationIfNeeded()
        val current = _state.value
        if (!current.saveIsActive || current.isSaving) return
        if (current.isSnapping) {
            _state.update { it.copy(userToastMessage = "Wait for the route to finish updating").recomputed() }
            return
        }
        if (pendingCheckpointRegeneration != null) {
            applyPendingCheckpointRegenerationIfNeeded()
        }
        val ready = _state.value
        val snapped = if (ready.roadCoordinates.size >= 2) ready.roadCoordinates else ready.points.map { it.lat to it.lng }
        val savePoints = RouteBuilderSavePayload.intentionalPoints(ready.points)
        val pointDtos =
            savePoints.map {
                RoutePointDto(lat = it.lat, lng = it.lng, markerType = it.type.rawValue)
            }
        val typeCounts = savePoints.groupingBy { it.type.rawValue }.eachCount()
        RouteBuilderMarkerDebugLog.saveRoute(
            pointCount = pointDtos.size,
            waypointCount = savePoints.count { it.type == RouteBuilderPointType.WAYPOINT },
            stopCount = savePoints.count { it.type == RouteBuilderPointType.STOP },
            pathCount = savePoints.count { it.type == RouteBuilderPointType.PATH },
            typeCounts = typeCounts,
        )
        val roadDtos =
            snapped.map {
                RoutePointDto(lat = it.lat, lng = it.lng, markerType = RouteBuilderPointType.PATH.rawValue)
            }
        if (pointDtos.size < 2 || roadDtos.size < 2) {
            _state.update { it.copy(errorMessage = "Add a start and finish before saving.").recomputed() }
            return
        }
        _state.update { it.copy(isSaving = true, routeName = name.trim(), isShowingRouteNamePrompt = false).recomputed() }
        val result =
            if (ready.editingRouteId != null) {
                repository.updateRoute(
                    routeId = ready.editingRouteId,
                    name = name.trim(),
                    points = pointDtos,
                    roadCoordinates = roadDtos,
                    distanceMeters = ready.distanceMeters,
                    etaSeconds = ready.travelSeconds,
                )
            } else {
                repository.createRoute(
                    name = name.trim(),
                    points = pointDtos,
                    roadCoordinates = roadDtos,
                    distanceMeters = ready.distanceMeters,
                    etaSeconds = ready.travelSeconds,
                )
            }
        result.fold(
            onSuccess = { saved ->
                val apiPoints = saved.points.orEmpty()
                val savedTypeCounts =
                    apiPoints
                        .mapNotNull { it.markerType }
                        .groupingBy { it }
                        .eachCount()
                RouteBuilderMarkerDebugLog.savedRouteFromApi(
                    routeId = saved.id,
                    waypointCount = apiPoints.count { it.markerType == RouteBuilderPointType.WAYPOINT.rawValue },
                    pointCount = apiPoints.size,
                    typeCounts = savedTypeCounts,
                )
                editUndoStack.clear()
                if (ready.editingRouteId != null) {
                    val savedPoints =
                        apiPoints.mapNotNull { dto ->
                            val type = RouteBuilderPointType.entries.firstOrNull { it.rawValue == dto.markerType } ?: return@mapNotNull null
                            RouteBuilderPoint(lat = dto.lat, lng = dto.lng, type = type)
                        }
                    val savedRoad = saved.roadCoordinates.orEmpty().map { it.lat to it.lng }
                    editBaseline =
                        RouteEditBaseline.capture(
                            name = saved.name,
                            points = savedPoints,
                            roadCoordinates = savedRoad,
                            distanceMeters = saved.distanceMeters ?: 0.0,
                            travelSeconds = saved.etaSeconds ?: 0.0,
                        )
                }
                _events.tryEmit(RouteBuilderEvent.RouteSaved(saved))
                dismiss()
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = saveRouteErrorMessage(error),
                    ).recomputed()
                }
            },
        )
    }

    private fun placePoint(
        type: RouteBuilderPointType,
        triggerGuidedGeneration: Boolean,
    ) {
        val current = _state.value
        if (current.isSnapping) {
            _state.update { it.copy(userToastMessage = "Wait for the route to finish updating").recomputed() }
            return
        }
        if (
            type != RouteBuilderPointType.START &&
            type != RouteBuilderPointType.FINISH &&
            !current.didSnapToRoad
        ) {
            _state.update { it.copy(userToastMessage = "Set start and finish to build a route first").recomputed() }
            return
        }
        if (current.showsEditSessionUndo) {
            recordEditUndoSnapshotBeforeChange()
        }
        val raw = currentPlacementCoordinate()
        val coordinate = snappedCoordinateForPlacement(type, raw)
        applyPlacedPoint(type, coordinate, triggerGuidedGeneration)
    }

    private fun applyPlacedPoint(
        type: RouteBuilderPointType,
        coordinate: RouteLatLng,
        triggerGuidedGeneration: Boolean,
    ) {
        val point = RouteBuilderPoint(lat = coordinate.lat, lng = coordinate.lng, type = type)
        val currentPoints = _state.value.points.toMutableList()
        when (type) {
            RouteBuilderPointType.START -> {
                val index = currentPoints.indexOfFirst { it.type == RouteBuilderPointType.START }
                if (index >= 0) currentPoints[index] = point else currentPoints.add(0, point)
            }
            RouteBuilderPointType.FINISH -> {
                val index = currentPoints.indexOfFirst { it.type == RouteBuilderPointType.FINISH }
                if (index >= 0) currentPoints[index] = point else currentPoints.add(point)
            }
            RouteBuilderPointType.PATH,
            RouteBuilderPointType.WAYPOINT,
            RouteBuilderPointType.STOP,
            -> {
                val line = routeLineCoordinates(_state.value)
                currentPoints +=
                    if (line.size >= 2) {
                        val projection = projectOntoRouteLine(point.coordinate)
                        orderRoutePoints(
                            currentPoints + point,
                            projectedPositions = projection?.let { mapOf(point.id to it.arcLengthMeters) } ?: emptyMap(),
                        ).let { ordered -> ordered.filterNot { it.id == point.id } + point }
                    } else {
                        listOf(point)
                    }
                val finishIndex = currentPoints.indexOfFirst { it.type == RouteBuilderPointType.FINISH }
                if (line.size < 2 && finishIndex >= 0) {
                    currentPoints.removeAt(currentPoints.lastIndexOf(point))
                    currentPoints.add(finishIndex, point)
                } else if (line.size < 2) {
                    currentPoints.add(point)
                } else {
                    // already ordered above
                }
            }
        }
        val ordered =
            if (type == RouteBuilderPointType.PATH || type == RouteBuilderPointType.WAYPOINT || type == RouteBuilderPointType.STOP) {
                val projection = projectOntoRouteLine(point.coordinate)
                orderRoutePoints(
                    _state.value.points + point,
                    projectedPositions = projection?.let { mapOf(point.id to it.arcLengthMeters) } ?: emptyMap(),
                )
            } else {
                when (type) {
                    RouteBuilderPointType.START -> {
                        val mutable = _state.value.points.filter { it.type != RouteBuilderPointType.START }.toMutableList()
                        mutable.add(0, point)
                        mutable
                    }
                    RouteBuilderPointType.FINISH -> {
                        val mutable = _state.value.points.filter { it.type != RouteBuilderPointType.FINISH }.toMutableList()
                        mutable.add(point)
                        mutable
                    }
                    else -> currentPoints
                }
            }
        _state.update {
            it.copy(
                points = ordered,
                userToastMessage = placementToastFor(type),
            ).recomputed()
        }
        if (type == RouteBuilderPointType.START || type == RouteBuilderPointType.FINISH) {
            prepareCheckpointRegenerationAfterEndpointChange()
        }
        when {
            triggerGuidedGeneration -> startGuidedRouteGeneration()
            shouldRebuildRouteAfterPointMutation(type) -> rebuildRoadPath()
        }
    }

    private fun applyMovedPoint(
        original: RouteBuilderPoint,
        index: Int,
        newCoordinate: RouteLatLng,
    ) {
        val activeDragPointId: String?
        val nextPoints =
            if (original.isAutoShape) {
                val locked = _state.value.points.filter { !it.isAutoShape }
                val dragged =
                    original.copy(lat = newCoordinate.lat, lng = newCoordinate.lng, isAutoShape = true)
                val combined = locked + dragged
                activeDragPointId = dragged.id
                val projection = projectOntoRouteLine(newCoordinate)
                if (projection != null) {
                    orderRoutePoints(combined, projectedPositions = mapOf(dragged.id to projection.arcLengthMeters))
                } else {
                    orderRoutePoints(combined)
                }
            } else {
                activeDragPointId = null
                val moved = original.copy(lat = newCoordinate.lat, lng = newCoordinate.lng)
                val mutable = _state.value.points.toMutableList()
                mutable[index] = moved
                val projection = projectOntoRouteLine(newCoordinate)
                if (projection != null) {
                    orderRoutePoints(mutable, projectedPositions = mapOf(moved.id to projection.arcLengthMeters))
                } else {
                    orderRoutePoints(mutable)
                }
            }
        _state.update { it.copy(points = nextPoints, movingPointId = null).recomputed() }
        if (original.type == RouteBuilderPointType.START || original.type == RouteBuilderPointType.FINISH) {
            prepareCheckpointRegenerationAfterEndpointChange()
        }
        when {
            shouldRebuildRouteAfterPointMutation(original.type) ->
                rebuildRoadPath(activeDragPointId = activeDragPointId)
            original.type == RouteBuilderPointType.WAYPOINT || original.type == RouteBuilderPointType.STOP ->
                _state.update { it.copy(points = orderRoutePoints(it.points)).recomputed() }
        }
    }

    private fun rebuildRoadPath(activeDragPointId: String? = null) {
        snapJob?.cancel()
        val routingInput = routingCoordinates(activeDragPointId)
        if (routingInput.size < 2) {
            _state.update {
                it.copy(
                    roadCoordinates = routingInput,
                    distanceMeters = 0.0,
                    travelSeconds = 0.0,
                    isSnapping = false,
                    didSnapToRoad = false,
                ).recomputed()
            }
            lastSnapTurnCoordinates = emptyList()
            roadPolylineIndex = null
            pendingCheckpointRegeneration = null
            if (_state.value.isRunningGuidedGeneration) {
                _state.update { s -> s.copy(isRunningGuidedGeneration = false, isManualMode = true).recomputed() }
            }
            return
        }
        _state.update {
            it.copy(
                roadCoordinates = emptyList(),
                didSnapToRoad = false,
                isSnapping = true,
            ).recomputed()
        }
        snapJob =
            viewModelScope.launch {
                val result = roadSnapper.buildRoute(routingInput)
                lastSnapTurnCoordinates = result.turnManeuverCoordinates
                _state.update {
                    it.copy(
                        roadCoordinates = result.coordinates,
                        distanceMeters = result.distanceMeters,
                        travelSeconds = result.travelTimeSeconds,
                        didSnapToRoad = result.didSnapToRoad,
                        isSnapping = false,
                    ).recomputed()
                }
                syncAutoPathPoints(result.turnManeuverCoordinates, source = "snap")
                scheduleRoadPolylineIndexBuild()
                if (_state.value.isRunningGuidedGeneration) {
                    handleGuidedGenerationComplete()
                } else {
                    applyPendingCheckpointRegenerationIfNeeded()
                }
            }
    }

    private fun startGuidedRouteGeneration() {
        _state.update { it.copy(isRunningGuidedGeneration = true).recomputed() }
        rebuildRoadPath()
    }

    private fun handleGuidedGenerationComplete() {
        val current = _state.value
        if (!current.didSnapToRoad) {
            _state.update {
                it.copy(
                    isRunningGuidedGeneration = false,
                    isManualMode = true,
                    errorMessage = "Couldn't build a route along roads. Try building manually or adjust your start and finish.",
                ).recomputed()
            }
            return
        }
        if (current.distanceMeters < RouteBuilderConstants.MINIMUM_ROUTE_DRIVE_DISTANCE_METERS) {
            _state.update {
                it.copy(
                    isRunningGuidedGeneration = false,
                    isManualMode = true,
                    errorMessage = "Route drive distance must be at least 1,000 feet. Adjust your start or finish.",
                ).recomputed()
            }
            return
        }
        val start = current.points.firstOrNull { it.type == RouteBuilderPointType.START }
        val finish = current.points.firstOrNull { it.type == RouteBuilderPointType.FINISH }
        if (RouteBuilderPlacementSanity.isImplausibleGuidedSpan(start?.coordinate, finish?.coordinate)) {
            lastSnapTurnCoordinates = emptyList()
            roadPolylineIndex = null
            pendingCheckpointRegeneration = null
            _state.update {
                it.copy(
                    points = it.points.filter { it.type != RouteBuilderPointType.FINISH },
                    roadCoordinates = emptyList(),
                    didSnapToRoad = false,
                    distanceMeters = 0.0,
                    travelSeconds = 0.0,
                    isRunningGuidedGeneration = false,
                    hasCompletedGuidedGeneration = false,
                    isManualMode = false,
                    errorMessage = "Finish looks too far from start. Zoom in on the map and set finish again.",
                ).recomputed()
            }
            return
        }
        applyCheckpointsForDensity(CheckpointDensityTier.RECOMMENDED, recordUndo = false)
        _state.update {
            it.copy(
                isRunningGuidedGeneration = false,
                hasCompletedGuidedGeneration = true,
                selectedCheckpointDensity = CheckpointDensityTier.RECOMMENDED,
            ).recomputed()
        }
        fitCameraToRouteForMarkerVisibility()
        viewModelScope.launch {
            val line = routeLineCoordinates(_state.value)
            if (line.size >= 2) {
                roadPolylineIndex = RoutePolylineIndex(line)
            }
            bootstrapAutoPathPointsIfNeeded()
        }
        scheduleCameraSettle(force = true)
        scheduleAutoPathBootstrapIfZoomedIn()
    }

    private fun applyCheckpointsForDensity(
        tier: CheckpointDensityTier,
        recordUndo: Boolean = true,
    ) {
        val road = _state.value.roadCoordinates
        val turnCount = lastSnapTurnCoordinates.size
        val densityOption =
            RouteAutoCheckpointGenerator.densityOption(tier = tier, roadCoordinates = road, turnCount = turnCount)
        val spacingMeters =
            densityOption?.spacingMeters
                ?: if (tier == CheckpointDensityTier.RECOMMENDED) {
                    RouteAutoCheckpointGenerator.recommendedDefaultInterval(road, turnCount)?.spacingMeters
                } else {
                    null
                }
                ?: RouteAutoCheckpointGenerator.viableIntervals(road).maxByOrNull { it.checkpointCount }?.spacingMeters
                ?: adaptiveCheckpointSpacingMeters(road)
                ?: return
        replaceAutoCheckpoints(spacingMeters, recordUndo)
        _state.update {
            it.copy(selectedCheckpointDensity = densityOption?.tier ?: tier).recomputed()
        }
    }

    private fun replaceAutoCheckpoints(
        spacingMeters: Double,
        recordUndo: Boolean = true,
    ) {
        val road = _state.value.roadCoordinates
        if (road.size < 2) return
        val generated = RouteAutoCheckpointGenerator.generate(road, spacingMeters)
        if (generated.isEmpty()) {
            RouteBuilderMarkerDebugLog.checkpointGen(added = 0, totalWp = _state.value.checkpointCount, spacingMeters = spacingMeters)
            return
        }
        if (recordUndo && _state.value.showsEditSessionUndo) {
            recordEditUndoSnapshotBeforeChange()
        }
        val newWaypoints = generated.map { RouteBuilderPoint(lat = it.lat, lng = it.lng, type = RouteBuilderPointType.WAYPOINT) }
        _state.update {
            it.copy(
                points = orderRoutePoints(it.points.filter { p -> p.type != RouteBuilderPointType.WAYPOINT } + newWaypoints),
                activeCheckpointSpacingMeters = spacingMeters,
            ).recomputed()
        }
        if (BuildConfig.DEBUG && newWaypoints.isNotEmpty()) {
            _state.update {
                it.copy(
                    userToastMessage = "${newWaypoints.size} checkpoints added on route",
                ).recomputed()
            }
        }
        RouteBuilderMarkerDebugLog.checkpointGen(
            added = newWaypoints.size,
            totalWp = _state.value.checkpointCount,
            spacingMeters = spacingMeters,
        )
    }

    private fun prepareCheckpointRegenerationAfterEndpointChange() {
        pendingCheckpointRegeneration =
            RouteBuilderCheckpointRegeneration.computePendingAfterEndpointChange(_state.value)
        // Keep existing waypoints visible until replacement completes after snap (avoid save/display gap).
    }

    private fun applyPendingCheckpointRegenerationIfNeeded() {
        val pending = pendingCheckpointRegeneration ?: return
        pendingCheckpointRegeneration = null
        val current = _state.value
        if (current.roadCoordinates.size < 2 || !current.didSnapToRoad) return
        if (pending.spacingMeters != null) {
            regenerateCheckpointsPreservingSpacing(pending.spacingMeters)
        } else {
            applyCheckpointsForDensity(pending.densityTier, recordUndo = false)
        }
    }

    private fun regenerateCheckpointsPreservingSpacing(spacing: Double) {
        val road = _state.value.roadCoordinates
        if (road.size < 2) return
        val intervals = RouteAutoCheckpointGenerator.viableIntervals(road)
        if (intervals.isEmpty()) {
            _state.update { it.copy(activeCheckpointSpacingMeters = null).recomputed() }
            return
        }
        val spacingToUse =
            intervals.firstOrNull { it.spacingMeters == spacing }?.spacingMeters
                ?: intervals.minByOrNull { abs(it.spacingMeters - spacing) }?.spacingMeters
                ?: run {
                    _state.update { it.copy(activeCheckpointSpacingMeters = null).recomputed() }
                    return
                }
        replaceAutoCheckpoints(spacingToUse, recordUndo = false)
        intervals.firstOrNull { it.spacingMeters == spacingToUse }?.let { syncSelectedCheckpointDensity(it) }
    }

    private fun syncSelectedCheckpointDensity(interval: RouteAutoCheckpointGenerator.IntervalOption) {
        val road = _state.value.roadCoordinates
        val turnCount = lastSnapTurnCoordinates.size
        val recommended =
            RouteAutoCheckpointGenerator.densityOption(
                tier = CheckpointDensityTier.RECOMMENDED,
                roadCoordinates = road,
                turnCount = turnCount,
            )
        if (recommended?.spacingMeters == interval.spacingMeters) {
            _state.update { it.copy(selectedCheckpointDensity = CheckpointDensityTier.RECOMMENDED).recomputed() }
            return
        }
        val tier =
            RouteAutoCheckpointGenerator.densityOptions(road, turnCount)
                .firstOrNull { it.spacingMeters == interval.spacingMeters }
                ?.tier ?: CheckpointDensityTier.RECOMMENDED
        _state.update { it.copy(selectedCheckpointDensity = tier).recomputed() }
    }

    private fun syncAutoPathPoints(
        turnCoordinates: List<RouteLatLng>,
        source: String = "directions",
    ) {
        val road = _state.value.roadCoordinates
        if (road.size < 2) return
        var effectiveTurns = turnCoordinates
        var autoCoordinates =
            RouteAutoPathPointGenerator.autoTurnPathCoordinates(
                turnCoordinates = effectiveTurns,
                roadCoordinates = road,
                polylineIndex = roadPolylineIndex,
            )
        if (autoCoordinates.isEmpty()) {
            val polylineTurns = RoutePolylineTurnExtractor.turnCoordinates(along = road)
            if (polylineTurns.isNotEmpty()) {
                effectiveTurns = polylineTurns
                lastSnapTurnCoordinates = polylineTurns
                autoCoordinates =
                    RouteAutoPathPointGenerator.autoTurnPathCoordinates(
                        turnCoordinates = effectiveTurns,
                        roadCoordinates = road,
                        polylineIndex = roadPolylineIndex,
                    )
                RouteBuilderMarkerDebugLog.autoPathBootstrap("polyline-fallback")
            }
        }
        val preserved = _state.value.points.filter { !it.isAutoShape }
        val autoPoints =
            autoCoordinates.map {
                RouteBuilderPoint(lat = it.lat, lng = it.lng, type = RouteBuilderPointType.PATH, isAutoShape = true)
            }
        RouteBuilderMarkerDebugLog.syncAutoPath(source, effectiveTurns.size, autoPoints.size)
        _state.update { it.copy(points = orderRoutePoints(preserved + autoPoints)).recomputed() }
    }

    private fun scheduleRoadPolylineIndexBuild(bootstrapAutoPathAfterIndex: Boolean = false) {
        polylineIndexJob?.cancel()
        val line = routeLineCoordinates(_state.value)
        if (line.size < 2) {
            roadPolylineIndex = null
            return
        }
        polylineIndexJob =
            viewModelScope.launch {
                val index = withContext(Dispatchers.Default) { RoutePolylineIndex(line) }
                roadPolylineIndex = index
                if (bootstrapAutoPathAfterIndex && _state.value.editingRouteId != null) {
                    bootstrapAutoPathPointsIfNeeded()
                }
            }
    }

    private fun scheduleCameraSettle(force: Boolean) {
        cameraSettleJob?.cancel()
        cameraSettleJob =
            viewModelScope.launch {
                if (!force) delay(200)
                val latitudeDelta = latestCameraRegion.latitudeDelta
                val settledTier = RouteBuilderMapMarkerLodTier.from(latitudeDelta)
                _state.update {
                    val appearanceChanged =
                        settledTier != it.mapMarkerLodTier ||
                            RouteBuilderMarkerLod.secondaryPinScaleBucket(latitudeDelta) !=
                            RouteBuilderMarkerLod.secondaryPinScaleBucket(it.mapVisibleLatitudeDelta)
                    val next =
                        it.copy(
                            cameraRegion = latestCameraRegion,
                            mapMarkerLodTier = settledTier,
                            mapVisibleLatitudeDelta = latitudeDelta,
                        )
                    if (appearanceChanged) next.withRebuiltMapContent() else next
                }
                scheduleAutoPathBootstrapIfZoomedIn()
            }
    }

    private fun scheduleAutoPathBootstrapIfZoomedIn() {
        if (_state.value.mapMarkerLodTier == RouteBuilderMapMarkerLodTier.REGIONAL) return
        if (_state.value.points.any { it.isAutoShape }) return
        autoPathBootstrapJob?.cancel()
        autoPathBootstrapJob =
            viewModelScope.launch {
                delay(350)
                bootstrapAutoPathPointsIfNeeded()
            }
    }

    private fun bootstrapAutoPathPointsIfNeeded() {
        val current = _state.value
        if (current.roadCoordinates.size < 2 || !current.didSnapToRoad) return
        if (current.points.any { it.isAutoShape }) return
        if (current.editingRouteId != null) {
            RouteBuilderMarkerDebugLog.autoPathBootstrap("polyline-local")
            val turns = RoutePolylineTurnExtractor.turnCoordinates(along = current.roadCoordinates)
            lastSnapTurnCoordinates = turns
            syncAutoPathPoints(turns, source = "polyline-local")
            return
        }
        val routingInput = routingCoordinates()
        if (routingInput.size < 2) return
        RouteBuilderMarkerDebugLog.autoPathBootstrap("directions-api")
        viewModelScope.launch {
            val result = roadSnapper.buildRoute(routingInput)
            lastSnapTurnCoordinates = result.turnManeuverCoordinates
            syncAutoPathPoints(result.turnManeuverCoordinates, source = "directions-api")
        }
    }

    private fun applyEditSnapshot(snapshot: RouteBuilderEditSnapshot) {
        _state.update {
            it.copy(
                points = snapshot.intentionalPoints,
                roadCoordinates = snapshot.roadCoordinates,
                distanceMeters = snapshot.distanceMeters,
                travelSeconds = snapshot.travelSeconds,
                didSnapToRoad = snapshot.didSnapToRoad,
                selectedCheckpointDensity = snapshot.selectedCheckpointDensity,
            ).recomputed()
        }
        lastSnapTurnCoordinates = snapshot.lastSnapTurnCoordinates
        if (snapshot.didSnapToRoad && snapshot.roadCoordinates.size >= 2) {
            syncAutoPathPoints(snapshot.lastSnapTurnCoordinates, source = "undo-restore")
            scheduleRoadPolylineIndexBuild()
        } else if (routingCoordinates().size >= 2) {
            rebuildRoadPath()
        } else {
            scheduleRoadPolylineIndexBuild()
        }
    }

    private fun recordEditUndoSnapshotBeforeChange() {
        if (!_state.value.showsEditSessionUndo || _state.value.isSaving) return
        editUndoStack.addLast(
            RouteBuilderEditSnapshot.capture(
                points = _state.value.points,
                roadCoordinates = _state.value.roadCoordinates,
                distanceMeters = _state.value.distanceMeters,
                travelSeconds = _state.value.travelSeconds,
                didSnapToRoad = _state.value.didSnapToRoad,
                lastSnapTurnCoordinates = lastSnapTurnCoordinates,
                selectedCheckpointDensity = _state.value.selectedCheckpointDensity,
            ),
        )
        while (editUndoStack.size > RouteBuilderConstants.MAX_UNDO_STACK) {
            editUndoStack.removeFirst()
        }
        _state.update { it.copy(canUndoEditSession = editUndoStack.isNotEmpty()).recomputed() }
    }

    private fun presentRouteBuilderPrimersIfNeeded() {
        val current = _state.value
        if (current.showRouteEducation || current.showLocationPrimer) return
        if (!routeBuilderEducationSeen()) {
            _state.update { it.copy(showRouteEducation = true).recomputed() }
        }
    }

    fun maybePresentLocationPrimer(locationNotDetermined: Boolean) {
        val current = _state.value
        if (current.showRouteEducation || current.showLocationPrimer) return
        if (!locationNotDetermined) return
        _state.update { it.copy(showLocationPrimer = true).recomputed() }
    }

    private fun routeBuilderEducationSeen(): Boolean =
        prefs.getBoolean(RouteBuilderConstants.ROUTE_BUILDER_EDUCATION_SEEN_KEY, false)

    private fun applyNewRouteUserLocationIfAvailable(
        userLat: Double?,
        userLng: Double?,
        locationGranted: Boolean,
    ) {
        if (_state.value.editingRouteId != null) return
        if (_state.value.didApplyUserLocationRecenter) return
        if (!locationGranted) return
        val lat = userLat ?: return
        val lng = userLng ?: return
        recenterMap(lat, lng, markUserRecenter = true)
    }

    private fun recenterMap(
        lat: Double,
        lng: Double,
        markUserRecenter: Boolean,
    ) {
        val zoom = RouteBuilderMarkerLod.zoomForCloseStreetLevel(lat)
        val region =
            RouteBuilderCameraRegion(
                centerLat = lat,
                centerLng = lng,
                latitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
                longitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
            )
        latestCameraRegion = region
        _state.update {
            it.copy(
                cameraRegion = region,
                mapVisibleLatitudeDelta = region.latitudeDelta,
                mapMarkerLodTier = RouteBuilderMapMarkerLodTier.from(region.latitudeDelta),
                programmaticCameraTarget = RouteBuilderCameraTarget(lat, lng, zoom),
                didApplyUserLocationRecenter = markUserRecenter || it.didApplyUserLocationRecenter,
            ).recomputed()
        }
        scheduleCameraSettle(force = true)
    }

    private fun captureInitialEditBaselineIfNeeded() {
        if (_state.value.editingRouteId != null || editBaseline != null) return
        val current = _state.value
        editBaseline =
            RouteEditBaseline.capture(
                name = current.routeName,
                points = current.points,
                roadCoordinates = current.roadCoordinates,
                distanceMeters = current.distanceMeters,
                travelSeconds = current.travelSeconds,
            )
    }

    private fun hasUnsavedChanges(): Boolean {
        val baseline = editBaseline ?: return true
        val current = _state.value
        val snapshot =
            RouteEditBaseline.capture(
                name = current.routeName,
                points = current.points,
                roadCoordinates = current.roadCoordinates,
                distanceMeters = current.distanceMeters,
                travelSeconds = current.travelSeconds,
            )
        return snapshot != baseline
    }

    private fun validateSavePreconditions(): Boolean {
        val current = _state.value
        if (current.saveIsActive && !current.isSaving) return true
        val message =
            when {
                current.isRunningGuidedGeneration -> "Wait for route generation to finish, then try again."
                current.isSnapping -> "Wait for the route to finish snapping to the road, then try again."
                current.points.size >= 2 && current.distanceMeters <= 0 ->
                    "Route distance is still calculating. Try again in a moment."
                current.distanceMeters < RouteBuilderConstants.MINIMUM_ROUTE_DRIVE_DISTANCE_METERS ->
                    "Route drive distance must be at least 1,000 feet."
                else -> null
            }
        if (message != null) {
            _state.update { it.copy(errorMessage = message).recomputed() }
        }
        return false
    }

    /** Live camera from Mapbox updates — iOS `placementCameraRegion` parity (not settle-delayed [cameraRegion]). */
    private fun placementCameraRegion(): RouteBuilderCameraRegion = latestCameraRegion

    private fun currentPlacementCoordinate(): RouteLatLng {
        val current = _state.value
        current.crosshairPlacementCoordinate?.let { return it }
        val region = placementCameraRegion()
        if (current.mapContainerWidthPx <= 0f || current.mapContainerHeightPx <= 0f) {
            return region.centerLat to region.centerLng
        }
        val sheetHeightPx = current.bottomSheetVisibleHeightPx
        if (sheetHeightPx <= 0f) {
            return region.centerLat to region.centerLng
        }
        val crosshair = crosshairCenterPx(current.mapContainerWidthPx, current.mapContainerHeightPx, sheetHeightPx, bottomSafeAreaPx = 0f)
        return RouteBuilderPlacement.coordinateAtCrosshair(
            region = region,
            mapWidthPx = current.mapContainerWidthPx,
            mapHeightPx = current.mapContainerHeightPx,
            crosshairX = crosshair.first,
            crosshairY = crosshair.second,
        )
    }

    private fun routingCoordinates(activeDragPointId: String? = null): List<RouteLatLng> =
        orderRoutePoints(_state.value.points.filter { it.affectsRouting(activeDragPointId) }).map { it.coordinate }

    private fun projectOntoRouteLine(coordinate: RouteLatLng): RoutePolylineProjection? {
        val line = routeLineCoordinates(_state.value)
        roadPolylineIndex?.let { return it.projectOntoPolyline(coordinate) }
        return RoutePolylineGeometry.allProjectionsOntoPolyline(coordinate, line).minByOrNull { it.distanceMeters }
    }

    private fun snappedCoordinateForPlacement(
        type: RouteBuilderPointType,
        raw: RouteLatLng,
    ): RouteLatLng {
        return routeBuilderCoordinateForPlacement(
            type = type,
            raw = raw,
            hasRouteLine = routeLineCoordinates(_state.value).size >= 2,
        ) { coordinate ->
            projectOntoRouteLine(coordinate)?.coordinate
        }
    }

    private fun placementToastFor(type: RouteBuilderPointType): String? =
        when (type) {
            RouteBuilderPointType.PATH -> "Path point added — route recalculating"
            RouteBuilderPointType.WAYPOINT -> "Checkpoint added on route"
            RouteBuilderPointType.STOP -> "Stop added on route"
            else -> null
        }

    private fun shouldRebuildRouteAfterPointMutation(type: RouteBuilderPointType): Boolean =
        when (type) {
            RouteBuilderPointType.START,
            RouteBuilderPointType.FINISH,
            RouteBuilderPointType.PATH,
            -> true
            RouteBuilderPointType.WAYPOINT,
            RouteBuilderPointType.STOP,
            -> false
        }

    private fun orderRoutePoints(
        candidatePoints: List<RouteBuilderPoint>,
        projectedPositions: Map<String, Double> = emptyMap(),
    ): List<RouteBuilderPoint> {
        val line = routeLineCoordinates(_state.value)
        if (candidatePoints.size < 2 || line.size < 2) return candidatePoints
        val start = candidatePoints.firstOrNull { it.type == RouteBuilderPointType.START }
        val finish = candidatePoints.firstOrNull { it.type == RouteBuilderPointType.FINISH }
        val interior = candidatePoints.filter { it.type != RouteBuilderPointType.START && it.type != RouteBuilderPointType.FINISH }
        val sortedInterior =
            interior.withIndex().sortedWith(
                compareBy<IndexedValue<RouteBuilderPoint>> { entry ->
                    projectedPositions[entry.value.id]
                        ?: projectOntoRouteLine(entry.value.coordinate)?.arcLengthMeters
                        ?: entry.index.toDouble()
                },
            ).map { it.value }
        return listOfNotNull(start) + sortedInterior + listOfNotNull(finish)
    }

    private fun currentCheckpointSpacingIndex(options: List<RouteAutoCheckpointGenerator.IntervalOption>): Int {
        _state.value.activeCheckpointSpacingMeters?.let { active ->
            options.indexOfFirst { it.spacingMeters == active }.takeIf { it >= 0 }?.let { return it }
        }
        if (options.isEmpty()) return 0
        val currentCount = _state.value.checkpointCount
        return options.withIndex().minWithOrNull(
            compareBy<IndexedValue<RouteAutoCheckpointGenerator.IntervalOption>> {
                abs(it.value.checkpointCount - currentCount)
            }.thenBy { it.index },
        )?.index ?: 0
    }

    private fun RouteBuilderScreenState.recomputed(): RouteBuilderScreenState {
        val hasStart = points.any { it.type == RouteBuilderPointType.START }
        val hasFinish = points.any { it.type == RouteBuilderPointType.FINISH }
        val hasRoutePath = roadCoordinates.size >= 2 && didSnapToRoad
        val ui =
            RouteBuilderUiStateResolver.resolve(
                RouteBuilderUiStateResolver.Inputs(
                    hasStart = hasStart,
                    hasFinish = hasFinish,
                    isRunningGuidedGeneration = isRunningGuidedGeneration,
                    isSnapping = isSnapping,
                    hasRoutePath = hasRoutePath,
                    checkpointCount = checkpointCount,
                    isManualMode = isManualMode,
                    isEditMode = isEditMode,
                    hasCompletedGuidedGeneration = hasCompletedGuidedGeneration,
                    guidedBackToStartStep = guidedBackToStartStep,
                ),
            )
        val canSave =
            hasStart &&
                hasFinish &&
                hasRoutePath &&
                distanceMeters >= RouteBuilderConstants.MINIMUM_ROUTE_DRIVE_DISTANCE_METERS &&
                !isSnapping &&
                !isRunningGuidedGeneration
        val saveActive =
            canSave &&
                (editingRouteId == null || hasUnsavedChanges())
        return copy(
            uiState = ui,
            saveIsActive = saveActive,
            canUndoEditSession = editUndoStack.isNotEmpty(),
            bottomSheetHeightDp = bottomBarContentHeightDp(ui),
        ).withRebuiltMapContent(
            allowsInteraction = ui != RouteBuilderUiState.GENERATING_ROUTE,
        )
    }

    private fun RouteBuilderScreenState.withRebuiltMapContent(
        allowsInteraction: Boolean = mapContent.allowsInteraction,
    ): RouteBuilderScreenState {
        val markers = RouteBuilderMapMarkerSnapshots.buildMarkerSnapshots(this)
        if (BuildConfig.DEBUG) {
            val byType = markers.groupingBy { it.markerType }.eachCount()
            RouteBuilderMarkerDebugLog.mapMarkers(
                total = markers.size,
                waypointCount = markers.count { it.markerType == "waypoint" },
                markersByType = byType,
            )
        }
        val line = if (roadCoordinates.size >= 2) roadCoordinates else points.map { it.lat to it.lng }
        val linePoints = line.map { Point.fromLngLat(it.lng, it.lat) }
        val nextLineFingerprint = lineFingerprint(line)
        val nextMapContent =
            RouteBuilderMapContentState(
                lineFingerprint = nextLineFingerprint,
                lineCoordinates = linePoints,
                markers = markers,
                allowsInteraction = allowsInteraction,
            )
        return copy(mapContent = nextMapContent)
    }

    /** After guided generation, fit the route at street-friendly zoom so checkpoint pins are visible. */
    private fun fitCameraToRouteForMarkerVisibility() {
        val line = routeLineCoordinates(_state.value)
        if (line.size < 2) return
        val center = centerCoordinate(line)
        val cappedRegion = cappedRouteFitRegion(line, center)
        val zoom = RouteBuilderMarkerLod.zoomForLatitudeDelta(cappedRegion.latitudeDelta, center.lat)
        latestCameraRegion = cappedRegion
        _state.update {
            it.copy(
                cameraRegion = cappedRegion,
                mapVisibleLatitudeDelta = cappedRegion.latitudeDelta,
                mapMarkerLodTier = RouteBuilderMapMarkerLodTier.from(cappedRegion.latitudeDelta),
                programmaticCameraTarget = RouteBuilderCameraTarget(center.lat, center.lng, zoom),
            ).recomputed()
        }
    }

    /** Street-friendly route bbox (~3 mi max span) so interior markers and auto path bends render. */
    private fun cappedRouteFitRegion(
        coordinates: List<RouteLatLng>,
        center: RouteLatLng,
    ): RouteBuilderCameraRegion {
        val fit = regionToFit(coordinates, center)
        val maxVisibleLatitudeDelta = 0.045
        val cappedLatDelta = min(fit.latitudeDelta, maxVisibleLatitudeDelta)
        val cappedLngDelta = min(fit.longitudeDelta, maxVisibleLatitudeDelta)
        return fit.copy(latitudeDelta = cappedLatDelta, longitudeDelta = cappedLngDelta)
    }

    private fun adaptiveCheckpointSpacingMeters(road: List<RouteLatLng>): Double? {
        if (road.size < 2) return null
        val totalLength = RoutePolylineGeometry.polylineTotalLength(road)
        val maxArcLength =
            maxOf(
                0.0,
                totalLength - RouteAutoCheckpointGenerator.Options.FINISH_BUFFER_METERS,
            )
        if (maxArcLength < 80.0) return null
        // Short routes: half-mile tiers may yield zero checkpoints — space ~2 along the driveable arc.
        val spacing = maxArcLength / 3.0
        val generated = RouteAutoCheckpointGenerator.generate(road, spacing)
        return spacing.takeIf { generated.isNotEmpty() }
    }

    private fun emitPresented(presented: Boolean) {
        _events.tryEmit(RouteBuilderEvent.RouteBuilderPresented(presented))
    }

    companion object {
        private val DEFAULT_FALLBACK_CENTER = 37.7749 to -122.4194

        fun factory(
            repository: OttoDataRepository,
            context: Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    RouteBuilderViewModel(
                        repository = repository,
                        prefs = context.applicationContext.getSharedPreferences("otto_route_builder", Context.MODE_PRIVATE),
                        accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN,
                    ) as T
            }
    }
}

private fun defaultCameraRegion(): RouteBuilderCameraRegion =
    RouteBuilderCameraRegion(
        centerLat = 37.7749,
        centerLng = -122.4194,
        latitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
        longitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
    )

private fun routeLineCoordinates(state: RouteBuilderScreenState): List<RouteLatLng> =
    if (state.roadCoordinates.size >= 2) state.roadCoordinates else state.points.map { it.lat to it.lng }

internal fun routeBuilderCoordinateForPlacement(
    type: RouteBuilderPointType,
    raw: RouteLatLng,
    hasRouteLine: Boolean,
    projectOntoRouteLine: (RouteLatLng) -> RouteLatLng?,
): RouteLatLng {
    if (!hasRouteLine) return raw
    return when (type) {
        RouteBuilderPointType.WAYPOINT,
        RouteBuilderPointType.STOP,
        -> projectOntoRouteLine(raw) ?: raw
        RouteBuilderPointType.PATH,
        RouteBuilderPointType.START,
        RouteBuilderPointType.FINISH,
        -> raw
    }
}

private fun lineFingerprint(line: List<RouteLatLng>): String =
    "${line.size}-${line.firstOrNull()?.lat}-${line.firstOrNull()?.lng}-${line.lastOrNull()?.lat}-${line.lastOrNull()?.lng}"

private fun centerCoordinate(coordinates: List<RouteLatLng>): RouteLatLng {
    if (coordinates.isEmpty()) return 37.7749 to -122.4194
    val lat = coordinates.sumOf { it.lat } / coordinates.size
    val lng = coordinates.sumOf { it.lng } / coordinates.size
    return lat to lng
}

private fun regionToFit(
    coordinates: List<RouteLatLng>,
    fallback: RouteLatLng,
): RouteBuilderCameraRegion {
    if (coordinates.isEmpty()) {
        return RouteBuilderCameraRegion(
            centerLat = fallback.lat,
            centerLng = fallback.lng,
            latitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
            longitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
        )
    }
    var minLat = coordinates.first().lat
    var maxLat = minLat
    var minLng = coordinates.first().lng
    var maxLng = minLng
    coordinates.forEach { coord ->
        minLat = minOf(minLat, coord.lat)
        maxLat = maxOf(maxLat, coord.lat)
        minLng = minOf(minLng, coord.lng)
        maxLng = maxOf(maxLng, coord.lng)
    }
    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    val latitudeDelta = maxOf((maxLat - minLat) * 1.35, RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA)
    val longitudeDelta = maxOf((maxLng - minLng) * 1.35, RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA)
    return RouteBuilderCameraRegion(centerLat, centerLng, latitudeDelta, longitudeDelta)
}

private fun crosshairCenterPx(
    mapWidth: Float,
    mapHeight: Float,
    sheetVisibleHeightPx: Float,
    bottomSafeAreaPx: Float,
): Pair<Float, Float> {
    val visibleMapBottom = maxOf(0f, mapHeight - sheetVisibleHeightPx - bottomSafeAreaPx)
    return mapWidth / 2f to visibleMapBottom / 2f
}

private fun coordinateAtCrosshair(
    region: RouteBuilderCameraRegion,
    mapWidth: Float,
    mapHeight: Float,
    crosshairCenter: Pair<Float, Float>,
): RouteLatLng {
    if (mapWidth <= 0f || mapHeight <= 0f) return region.centerLat to region.centerLng
    val mapCenterX = mapWidth / 2f
    val mapCenterY = mapHeight / 2f
    val offsetX = crosshairCenter.first - mapCenterX
    val offsetY = crosshairCenter.second - mapCenterY
    val latOffset = -offsetY * (region.latitudeDelta / mapHeight)
    val lngOffset = offsetX * (region.longitudeDelta / mapWidth)
    return (region.centerLat + latOffset) to (region.centerLng + lngOffset)
}

private fun bottomBarContentHeightDp(uiState: RouteBuilderUiState): Int =
    when (uiState) {
        RouteBuilderUiState.SET_START -> 270
        RouteBuilderUiState.SET_FINISH -> 340
        RouteBuilderUiState.GENERATING_ROUTE -> 100
        RouteBuilderUiState.ROUTE_READY -> 330
        RouteBuilderUiState.MANUAL_PLOT -> 400
        RouteBuilderUiState.EDIT_ROUTE -> 420
    }

private fun saveRouteErrorMessage(error: Throwable): String {
    val message = error.message?.trim().orEmpty()
    if (message.isNotEmpty()) return message
    return "Couldn't save this route. Try again."
}
