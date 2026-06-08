package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** UI colors aligned with iOS `RouteMapMarkerColors` route list treatment. */
internal object SavedRouteListIconColors {
    val startAccent = Color(red = 0.24f, green = 0.68f, blue = 0.38f)
    val startButton = Color(red = 0.06f, green = 0.50f, blue = 0.24f)

    val routeBackgroundGradient: Brush
        get() = Brush.linearGradient(listOf(startAccent, startButton))

    /** Matches profile drive list badge gradient (purple → blue). */
    val createBackgroundGradient: Brush
        get() = Brush.linearGradient(listOf(Color(0xFF7B3DFF), Color(0xFF3D5AFE).copy(alpha = 0.85f)))
}

internal enum class SavedRouteListIconStyle {
    Route,
    Create,
}

/** Filled-circle list icon for saved routes and the Create Route CTA. */
@Composable
internal fun SavedRouteListIcon(
    style: SavedRouteListIconStyle = SavedRouteListIconStyle.Route,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector
    val background: Brush =
        when (style) {
            SavedRouteListIconStyle.Route -> {
                icon = Icons.Outlined.Route
                SavedRouteListIconColors.routeBackgroundGradient
            }
            SavedRouteListIconStyle.Create -> {
                icon = Icons.Outlined.Add
                SavedRouteListIconColors.createBackgroundGradient
            }
        }
    val glyphSize = if (size >= 48.dp) 22.dp else size * 0.46f

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(glyphSize),
        )
    }
}
