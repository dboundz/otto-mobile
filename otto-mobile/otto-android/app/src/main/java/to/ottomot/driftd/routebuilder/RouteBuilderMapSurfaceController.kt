package to.ottomot.driftd.routebuilder

import com.mapbox.common.Cancelable
import com.mapbox.maps.MapView

/** Camera subscription only — route line uses [to.ottomot.driftd.RouteMapLineMapEffect] in Compose. */
internal class RouteBuilderMapSurfaceController {
    private var boundMapView: MapView? = null
    private var cameraSubscription: Cancelable? = null

    var onCameraChanged: ((centerLat: Double, centerLng: Double, zoom: Double) -> Unit)? = null

    fun bindIfNeeded(mapView: MapView) {
        if (boundMapView === mapView) return
        boundMapView = mapView
        val mapboxMap = mapView.mapboxMap
        cameraSubscription?.cancel()
        cameraSubscription =
            mapboxMap.subscribeCameraChanged {
                val state = mapboxMap.cameraState
                onCameraChanged?.invoke(
                    state.center.latitude(),
                    state.center.longitude(),
                    state.zoom,
                )
            }
    }

    fun dispose() {
        cameraSubscription?.cancel()
        cameraSubscription = null
        boundMapView = null
    }
}
