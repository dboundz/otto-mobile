package to.ottomot.driftd.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.R

private val SavedPlacePinWidth = 56.dp
private val SavedPlacePinHeight = 84.dp
private val SavedPlacePinFrameWidth = 72.dp
private val SavedPlacePinFrameHeight = 168.dp

@Composable
fun OttoMapSavedPlaceMarkerContent(
    isSelected: Boolean = false,
    pinScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val totalScale = pinScale * if (isSelected) 1.06f else 1f
    Box(
        modifier
            .width(SavedPlacePinFrameWidth * totalScale)
            .height(SavedPlacePinFrameHeight * totalScale),
    ) {
        Image(
            painter = painterResource(R.drawable.map_point_saved),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .width(SavedPlacePinWidth * totalScale)
                    .height(SavedPlacePinHeight * totalScale)
                    .shadow(4.dp, spotColor = Color.Black.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.align(Alignment.BottomCenter).height(SavedPlacePinHeight * totalScale))
    }
}
