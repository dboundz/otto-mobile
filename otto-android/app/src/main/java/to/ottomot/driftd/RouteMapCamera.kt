package to.ottomot.driftd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState

@Composable
internal fun RouteMapFitCameraEffect(
    mapViewportState: MapViewportState,
    lineCoordinates: List<Point>,
    mapPoints: List<RouteMapPoint>,
    paddingDp: Double = 44.0,
    bottomPaddingDp: Double = 0.0,
    recenterToken: Int = 0,
) {
    val fitCoordinates = cameraPointsForRoutePreview(lineCoordinates, mapPoints)
    LaunchedEffect(mapViewportState, fitCoordinates, bottomPaddingDp, recenterToken) {
        when (fitCoordinates.size) {
            0 -> Unit
            1 ->
                mapViewportState.setCameraOptions(
                    CameraOptions.Builder()
                        .center(fitCoordinates[0])
                        .zoom(14.0)
                        .build(),
                )
            else -> {
                val camera =
                    mapViewportState.cameraForCoordinates(
                        fitCoordinates,
                        coordinatesPadding =
                            EdgeInsets(
                                paddingDp,
                                paddingDp,
                                paddingDp + bottomPaddingDp,
                                paddingDp,
                            ),
                    )
                mapViewportState.setCameraOptions(camera)
            }
        }
    }
}
