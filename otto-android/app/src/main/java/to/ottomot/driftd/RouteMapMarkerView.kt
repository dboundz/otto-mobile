package to.ottomot.driftd

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun RouteMapMarkerView(
    markerType: String?,
    isCompleted: Boolean = false,
    scale: Float = 1f,
    usesBottomAnnotationAnchor: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (RouteMapMarkerAsset.usesCenteredMarker(markerType)) {
        val markerSize = 48.dp * scale
        val frameSize = maxOf(markerSize, 44.dp * scale)
        Box(
            modifier
                .size(frameSize),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(RouteMapMarkerAsset.drawableRes(markerType, isCompleted)),
                contentDescription = stringResource(RouteMapMarkerAsset.contentDescriptionRes(markerType)),
                modifier =
                    Modifier
                        .size(markerSize)
                        .shadow(3.dp * scale, spotColor = Color.Black.copy(alpha = 0.35f)),
                contentScale = ContentScale.Fit,
            )
        }
        return
    }

    val markerWidth = 56.dp * scale
    val markerHeight = 84.dp * scale
    val frameWidth = maxOf(markerWidth, 72.dp * scale)

    if (usesBottomAnnotationAnchor) {
        // Route Builder: Mapbox `.bottom` anchor — compact frame so pin tip sits on the coordinate.
        Box(
            modifier
                .width(frameWidth)
                .height(markerHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(RouteMapMarkerAsset.drawableRes(markerType, isCompleted)),
                contentDescription = stringResource(RouteMapMarkerAsset.contentDescriptionRes(markerType)),
                modifier =
                    Modifier
                        .width(markerWidth)
                        .height(markerHeight)
                        .shadow(4.dp * scale, spotColor = Color.Black.copy(alpha = 0.4f)),
                contentScale = ContentScale.Fit,
            )
        }
        return
    }

    val frameHeight = markerHeight * 2

    // Map drive / preview: pin in the top half; CENTER anchor so pin tip sits on the coordinate.
    Box(
        modifier
            .width(frameWidth)
            .height(frameHeight),
    ) {
        Image(
            painter = painterResource(RouteMapMarkerAsset.drawableRes(markerType, isCompleted)),
            contentDescription = stringResource(RouteMapMarkerAsset.contentDescriptionRes(markerType)),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .width(markerWidth)
                    .height(markerHeight)
                    .shadow(4.dp * scale, spotColor = Color.Black.copy(alpha = 0.4f)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.align(Alignment.BottomCenter).height(markerHeight))
    }
}

@Composable
internal fun OttoDriveSteeringIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_otto_steering_wheel),
        contentDescription = contentDescription,
        modifier = if (modifier == Modifier) Modifier.size(size) else modifier,
        contentScale = ContentScale.Fit,
    )
}
