package to.ottomot.driftd.routebuilder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

internal const val ROUTE_BUILDER_MARKER_LAYER_ID = "route-builder-marker-symbols"

/**
 * Route Builder marker renderer backed by Mapbox symbol annotations.
 *
 * ViewAnnotations were reliable for drive previews, but flaky for editor markers after checkpoint
 * replacement. Keep one annotation manager for the MapView lifetime and only replace its contents.
 */
@Composable
internal fun RouteBuilderPointAnnotationMapEffect(
    markers: List<RouteBuilderMapMarkerSnapshot>,
    onMarkerLayerReady: () -> Unit = {},
) {
    val markersState = rememberUpdatedState(markers)
    val layerReadyState = rememberUpdatedState(onMarkerLayerReady)
    val controllerState = remember { mutableStateOf<RouteBuilderPointAnnotationController?>(null) }
    val markersKey =
        markers.joinToString(separator = "|") { marker ->
            "${marker.refreshId}:${marker.lat},${marker.lng}:${marker.markerType}:${marker.isAutoShape}"
        }

    DisposableMapEffect(Unit) { mapView ->
        val manager =
            mapView.annotations.createPointAnnotationManager(
                AnnotationConfig(layerId = ROUTE_BUILDER_MARKER_LAYER_ID),
            )
        manager.iconAllowOverlap = true
        manager.iconIgnorePlacement = true
        val bitmaps =
            RouteBuilderMapMarkerBitmaps(
                appContext = mapView.context.applicationContext,
                density = mapView.context.resources.displayMetrics.density,
            )
        val controller = RouteBuilderPointAnnotationController(manager, bitmaps)
        controllerState.value = controller
        mapView.mapboxMap.getStyle {
            layerReadyState.value()
            controller.sync(markersState.value)
        }
        onDispose {
            controllerState.value = null
            manager.deleteAll()
            mapView.annotations.removeAnnotationManager(manager)
        }
    }

    DisposableEffect(markersKey) {
        controllerState.value?.sync(markersState.value)
        onDispose { }
    }
}

private class RouteBuilderPointAnnotationController(
    private val manager: PointAnnotationManager,
    private val bitmaps: RouteBuilderMapMarkerBitmaps,
) {
    fun sync(markers: List<RouteBuilderMapMarkerSnapshot>) {
        manager.deleteAll()
        val options = markers.mapNotNull(bitmaps::toPointAnnotationOptions)
        val created =
            if (options.isNotEmpty()) {
                manager.create(options).size
            } else {
                0
            }
        RouteBuilderMarkerDebugLog.annotationSync(created)
    }
}
