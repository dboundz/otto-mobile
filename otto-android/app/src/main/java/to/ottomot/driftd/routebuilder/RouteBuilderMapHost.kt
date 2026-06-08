package to.ottomot.driftd.routebuilder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.gestures
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.RouteMapInteractionEffect
import to.ottomot.driftd.RouteMapLineMapEffect
import to.ottomot.driftd.RouteMapLinePalette

/**
 * Isolated map subtree — recomposes only when [RouteBuilderMapInputs] change.
 * Mirrors iOS `RouteBuilderMapHost.equatable()`.
 */
@Composable
fun RouteBuilderMapLayer(
    mapInputs: RouteBuilderMapInputs,
    onCameraChanged: (centerLat: Double, centerLng: Double, zoom: Double) -> Unit,
    onGestureStarted: () -> Unit,
    onGestureEnded: () -> Unit,
    onMapLongPress: (lat: Double, lng: Double) -> Unit,
    onMarkerLongPress: (pointId: String) -> Unit,
    onMapboxMapReady: (MapboxMap) -> Unit = {},
    markerDebugInputs: (() -> RouteBuilderMarkerDebugInputs?)? = null,
    onMarkerDebugSnapshotUpdated: ((RouteBuilderMarkerDebugSnapshot) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    RouteBuilderMapHost(
        mapContent = mapInputs.mapContent,
        programmaticCameraTarget = mapInputs.programmaticCameraTarget,
        allowsInteraction = mapInputs.allowsInteraction,
        onCameraChanged = onCameraChanged,
        onGestureStarted = onGestureStarted,
        onGestureEnded = onGestureEnded,
        onMapLongPress = onMapLongPress,
        onMarkerLongPress = onMarkerLongPress,
        onMapboxMapReady = onMapboxMapReady,
        markerDebugInputs = markerDebugInputs,
        onMarkerDebugSnapshotUpdated = onMarkerDebugSnapshotUpdated,
        modifier = modifier,
    )
}

@Composable
fun RouteBuilderMapHost(
    mapContent: RouteBuilderMapContentState,
    programmaticCameraTarget: RouteBuilderCameraTarget?,
    allowsInteraction: Boolean,
    onCameraChanged: (centerLat: Double, centerLng: Double, zoom: Double) -> Unit,
    onGestureStarted: () -> Unit,
    onGestureEnded: () -> Unit,
    onMapLongPress: (lat: Double, lng: Double) -> Unit,
    onMarkerLongPress: (pointId: String) -> Unit,
    onMapboxMapReady: (MapboxMap) -> Unit = {},
    markerDebugInputs: (() -> RouteBuilderMarkerDebugInputs?)? = null,
    onMarkerDebugSnapshotUpdated: ((RouteBuilderMarkerDebugSnapshot) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mapViewportState =
        rememberMapViewportState {
            programmaticCameraTarget?.let { target ->
                setCameraOptions(routeBuilderCameraOptions(target))
            }
        }
    val surfaceController = remember { RouteBuilderMapSurfaceController() }
    val onLongPress = rememberUpdatedState(onMapLongPress)
    val onMarkerLongPressState = rememberUpdatedState(onMarkerLongPress)
    val onGestureStartedState = rememberUpdatedState(onGestureStarted)
    val onGestureEndedState = rememberUpdatedState(onGestureEnded)
    val onCameraChangedState = rememberUpdatedState(onCameraChanged)
    val onMapboxMapReadyState = rememberUpdatedState(onMapboxMapReady)
    val allowsInteractionState = rememberUpdatedState(allowsInteraction)
    val markersState = rememberUpdatedState(mapContent.markers)
    val markerDebugInputsState = rememberUpdatedState(markerDebugInputs)
    val onMarkerDebugSnapshotUpdatedState = rememberUpdatedState(onMarkerDebugSnapshotUpdated)
    var markerLayerReady by remember { mutableStateOf(false) }
    var isZoomGestureActive by remember { mutableStateOf(false) }
    val displayLineCoordinates =
        remember(mapContent.lineCoordinates, isZoomGestureActive) {
            if (isZoomGestureActive) {
                mapContent.lineCoordinates.simplifiedForActiveZoom()
            } else {
                mapContent.lineCoordinates
            }
        }
    val displayLineFingerprint =
        if (displayLineCoordinates === mapContent.lineCoordinates) {
            mapContent.lineFingerprint
        } else {
            "${mapContent.lineFingerprint}:zoom:${displayLineCoordinates.size}"
        }

    SideEffect {
        surfaceController.onCameraChanged = { lat, lng, zoom ->
            onCameraChangedState.value(lat, lng, zoom)
        }
    }

    if (BuildConfig.DEBUG) {
        LaunchedEffect(
            mapContent.markersFingerprint(),
            mapContent.markersAppearanceFingerprint(),
        ) {
            val inputs = markerDebugInputsState.value?.invoke() ?: return@LaunchedEffect
            val snapshot =
                buildMarkerDebugSnapshotFromState(
                    state = inputs.screenState,
                    lastSnapTurnCount = inputs.lastSnapTurnCount,
                    syncedFeatureCount = mapContent.markers.size,
                    imageInstallResults = emptyMap(),
                    waypointProbeHitCount = mapContent.markers.count { it.markerType == "waypoint" },
                    pathAutoProbeHitCount = mapContent.markers.count { it.markerType == "path" && it.isAutoShape },
                    lastMissingStyleImage = null,
                )
            onMarkerDebugSnapshotUpdatedState.value?.invoke(snapshot)
        }
    }

    DisposableEffect(surfaceController) {
        onDispose { surfaceController.dispose() }
    }

    LaunchedEffect(programmaticCameraTarget) {
        val target = programmaticCameraTarget ?: return@LaunchedEffect
        mapViewportState.easeTo(
            routeBuilderCameraOptions(target),
            animationOptions =
                MapAnimationOptions.mapAnimationOptions {
                    duration(420L)
                },
        )
    }

    Box(modifier.fillMaxSize().testTag(RouteBuilderMarkerTestTags.routeBuilderMap)) {
        if (BuildConfig.MAPBOX_ACCESS_TOKEN.isBlank()) {
            return@Box
        }
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            scaleBar = {},
            compass = {},
            attribution = {},
            style = { MapStyle(style = Style.DARK) },
        ) {
            RouteMapInteractionEffect(allowInteraction = allowsInteraction)
            RouteBuilderPointAnnotationMapEffect(
                markers = mapContent.markers,
                onMarkerLayerReady = {
                    markerLayerReady = true
                },
            )
            if (displayLineCoordinates.size >= 2) {
                RouteMapLineMapEffect(
                    sourceId = "route-builder-line",
                    lineCoordinates = displayLineCoordinates,
                    palette = RouteMapLinePalette.LivePurple,
                    lineFingerprint = displayLineFingerprint,
                    belowLayerId = if (markerLayerReady) ROUTE_BUILDER_MARKER_LAYER_ID else null,
                )
            }
            DisposableMapEffect(Unit) { mapView ->
                surfaceController.bindIfNeeded(mapView)
                onMapboxMapReadyState.value(mapView.mapboxMap)
                onDispose { }
            }
            DisposableMapEffect(allowsInteraction) { mapView ->
                val longClickListener =
                    OnMapLongClickListener { point ->
                        if (!allowsInteractionState.value) return@OnMapLongClickListener true
                        val lat = point.latitude()
                        val lng = point.longitude()
                        val markerId =
                            nearestRouteBuilderMarkerId(
                                lat = lat,
                                lng = lng,
                                markers = markersState.value,
                            )
                        if (markerId != null) {
                            onMarkerLongPressState.value(markerId)
                        } else {
                            onLongPress.value(lat, lng)
                        }
                        true
                    }
                mapView.gestures.addOnMapLongClickListener(longClickListener)
                onDispose {
                    mapView.gestures.removeOnMapLongClickListener(longClickListener)
                }
            }
            DisposableMapEffect(Unit) { mapView ->
                val endListener =
                    object : OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {
                            onGestureStartedState.value()
                        }

                        override fun onMove(detector: MoveGestureDetector): Boolean = false

                        override fun onMoveEnd(detector: MoveGestureDetector) {
                            onGestureEndedState.value()
                        }
                    }
                mapView.gestures.addOnMoveListener(endListener)
                onDispose {
                    mapView.gestures.removeOnMoveListener(endListener)
                }
            }
            DisposableMapEffect(Unit) { mapView ->
                val scaleListener =
                    object : OnScaleListener {
                        override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                            isZoomGestureActive = true
                            onGestureStartedState.value()
                        }

                        override fun onScale(detector: StandardScaleGestureDetector) = Unit

                        override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                            isZoomGestureActive = false
                            onGestureEndedState.value()
                        }
                    }
                mapView.gestures.addOnScaleListener(scaleListener)
                onDispose {
                    mapView.gestures.removeOnScaleListener(scaleListener)
                }
            }
        }
    }
}

private fun routeBuilderCameraOptions(target: RouteBuilderCameraTarget): CameraOptions =
    CameraOptions.Builder()
        .center(Point.fromLngLat(target.lng, target.lat))
        .zoom(target.zoom)
        .build()

private fun List<Point>.simplifiedForActiveZoom(maxPoints: Int = 900): List<Point> {
    if (size <= maxPoints) return this
    val interiorCount = size - 2
    val maxInteriorCount = maxPoints - 2
    val stride = ((interiorCount + maxInteriorCount - 1) / maxInteriorCount).coerceAtLeast(1)
    val simplified = ArrayList<Point>((size / stride) + 2)
    simplified.add(first())
    var index = stride
    while (index < lastIndex) {
        simplified.add(this[index])
        index += stride
    }
    simplified.add(last())
    return simplified
}
