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

private val RaceTrackPinWidth = 56.dp
private val RaceTrackPinHeight = 84.dp

@Composable
fun OttoMapRaceTrackMarkerContent(
    isSelected: Boolean,
    pinScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val totalScale = pinScale * if (isSelected) 1.06f else 1f
    Image(
        painter = painterResource(R.drawable.map_point_track),
        contentDescription = null,
        modifier =
            modifier
                .width(RaceTrackPinWidth)
                .height(RaceTrackPinHeight)
                .graphicsLayer {
                    scaleX = totalScale
                    scaleY = totalScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
    )
}
