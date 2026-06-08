package to.ottomot.driftd.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.R

private val EventPinWidth = 56.dp
private val EventPinHeight = 84.dp

@Composable
fun OttoMapEventMarkerContent(
    isSelected: Boolean = false,
    pinScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val totalScale = pinScale * if (isSelected) 1.06f else 1f
    Image(
        painter = painterResource(R.drawable.map_point_event),
        contentDescription = null,
        modifier =
            modifier
                .width(EventPinWidth)
                .height(EventPinHeight)
                .graphicsLayer {
                    scaleX = totalScale
                    scaleY = totalScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
    )
}
