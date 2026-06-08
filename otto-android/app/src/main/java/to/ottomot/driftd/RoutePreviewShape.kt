package to.ottomot.driftd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Canvas route preview matching iOS `RoutePreviewShape` (including empty-data fallback curve). */
@Composable
internal fun RoutePreviewHero(
    coordinates: List<LatLngPair>,
    modifier: Modifier = Modifier,
    height: Dp = 124.dp,
    cornerRadius: Dp = 22.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.035f)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val insetX = 24.dp.toPx()
            val insetY = 20.dp.toPx()
            val drawWidth = size.width - insetX * 2
            val drawHeight = size.height - insetY * 2
            if (drawWidth <= 0f || drawHeight <= 0f) return@Canvas

            val points =
                routePreviewNormalizedPoints(
                    coordinates = coordinates,
                    width = drawWidth,
                    height = drawHeight,
                ).map { point ->
                    Offset(insetX + point.x, insetY + point.y)
                }
            if (points.size < 2) return@Canvas

            val path =
                Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }

            drawPath(
                path = path,
                color = DriveSessionColors.sessionPurple.copy(alpha = 0.34f),
                style =
                    Stroke(
                        width = 13.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(
                path = path,
                color = DriveSessionColors.sessionPurple,
                style =
                    Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }
}

private fun routePreviewNormalizedPoints(
    coordinates: List<LatLngPair>,
    width: Float,
    height: Float,
): List<Offset> {
    val valid =
        coordinates.filter { coord ->
            coord.lat.isFinite() && coord.lng.isFinite()
        }
    if (valid.size < 2) {
        return routePreviewFallbackPoints(width, height)
    }

    val minLat = valid.minOf { it.lat }
    val maxLat = valid.maxOf { it.lat }
    val minLng = valid.minOf { it.lng }
    val maxLng = valid.maxOf { it.lng }
    val latRange = max(maxLat - minLat, 0.000001)
    val lngRange = max(maxLng - minLng, 0.000001)

    return valid.map { coordinate ->
        val x = ((coordinate.lng - minLng) / lngRange * width).toFloat()
        val y = ((1 - (coordinate.lat - minLat) / latRange) * height).toFloat()
        Offset(x, y)
    }
}

private fun routePreviewFallbackPoints(width: Float, height: Float): List<Offset> =
    listOf(
        Offset(0f, height * 0.72f),
        Offset(width * 0.34f, height * 0.48f),
        Offset(width * 0.68f, height * 0.58f),
        Offset(width, height * 0.28f),
    )

internal fun formatDriveCompleteDistanceValue(meters: Double): String {
    val miles = meters / 1609.344
    return String.format(java.util.Locale.US, "%.1f", miles)
}

internal fun formatDriveCompleteDuration(seconds: Long): String {
    val totalMinutes = max(0, (seconds / 60.0).roundToInt())
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${max(1, minutes)}m"
    }
}
