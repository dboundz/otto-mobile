package to.ottomot.driftd.routebuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Outer crosshair box size (68pt base × 1.4 scale); matches iOS RouteBuilderPlacementCrosshair. */
const val RouteBuilderPlacementCrosshairSizeDp = 68f * 1.4f

@Composable
fun RouteBuilderPlacementCrosshair(modifier: Modifier = Modifier) {
    val scale = 1.4f
    val ringGreen = RouteBuilderMarkerColors.startAccent
    Box(
        modifier
            .size((68 * scale).dp)
            .shadow(4.dp * scale, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((26 * scale).dp)
                .border(3.dp * scale, Color.Black.copy(alpha = 0.88f), CircleShape),
        )
        Box(
            Modifier
                .size((24 * scale).dp)
                .border(2.dp * scale, Color.White.copy(alpha = 0.92f), CircleShape),
        )
        Box(
            Modifier
                .size(width = (34 * scale).dp, height = (4 * scale).dp)
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(1.dp * scale)),
        )
        Box(
            Modifier
                .size(width = (32 * scale).dp, height = (2 * scale).dp)
                .background(Color.White.copy(alpha = 0.95f)),
        )
        Box(
            Modifier
                .size(width = (4 * scale).dp, height = (34 * scale).dp)
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(1.dp * scale)),
        )
        Box(
            Modifier
                .size(width = (2 * scale).dp, height = (32 * scale).dp)
                .background(Color.White.copy(alpha = 0.95f)),
        )
        Box(
            Modifier
                .size((8 * scale).dp)
                .border(1.5.dp * scale, Color.Black.copy(alpha = 0.88f), CircleShape),
        )
        Box(
            Modifier
                .size((6 * scale).dp)
                .background(ringGreen, CircleShape),
        )
    }
}
