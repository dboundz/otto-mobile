package to.ottomot.driftd

import androidx.compose.runtime.Composable
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.plugin.gestures.gestures

/** When false, map gestures are off so parent click handlers (e.g. open trail map) receive taps. */
@Composable
internal fun RouteMapInteractionEffect(allowInteraction: Boolean) {
    DisposableMapEffect(allowInteraction) { mapView ->
        mapView.gestures.updateSettings {
            setScrollEnabled(allowInteraction)
            setRotateEnabled(allowInteraction)
            setPinchToZoomEnabled(allowInteraction)
            setDoubleTapToZoomInEnabled(allowInteraction)
            setDoubleTouchToZoomOutEnabled(allowInteraction)
            setQuickZoomEnabled(allowInteraction)
        }
        onDispose { }
    }
}
