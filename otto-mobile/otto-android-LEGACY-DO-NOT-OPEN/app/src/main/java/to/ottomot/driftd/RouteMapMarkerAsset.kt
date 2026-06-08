package to.ottomot.driftd

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal object RouteMapMarkerAsset {
    fun usesCenteredMarker(markerType: String?): Boolean =
        markerType == null || markerType == "path"

    @DrawableRes
    fun drawableRes(markerType: String?, isCompleted: Boolean = false): Int =
        when (markerType) {
            "start" -> R.drawable.map_route_start
            "waypoint" -> if (isCompleted) R.drawable.map_route_checkpoint_passed else R.drawable.map_route_checkpoint
            "stop" -> R.drawable.map_route_stop
            "finish" -> R.drawable.map_route_finish
            else -> R.drawable.map_route_point
        }

    @StringRes
    fun contentDescriptionRes(markerType: String?): Int =
        when (markerType) {
            "start" -> R.string.route_map_marker_start
            "waypoint" -> R.string.route_map_marker_checkpoint
            "stop" -> R.string.route_map_marker_stop
            "finish" -> R.string.route_map_marker_finish
            else -> R.string.route_map_marker_path
        }
}
