package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.Locale
import to.ottomot.driftd.map.MapDiscoveryMarkerLOD
import to.ottomot.driftd.map.MapDiscoveryMarkerPresentation

@Composable
internal fun OttoMapHazardMarkerLODView(
    type: String,
    latitudeDelta: Double,
    modifier: Modifier = Modifier,
) {
    when (MapDiscoveryMarkerLOD.presentation(latitudeDelta)) {
        MapDiscoveryMarkerPresentation.Dot ->
            Box(
                modifier = modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .shadow(2.dp, CircleShape)
                        .background(mapHazardMarkerColor(type), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                )
            }
        MapDiscoveryMarkerPresentation.Pin ->
            OttoMapHazardMarkerView(
                type = type,
                scale = MapDiscoveryMarkerLOD.pinScale(latitudeDelta),
                modifier = modifier,
            )
    }
}

@Composable
private fun OttoMapHazardMarkerView(
    type: String,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val markerSize = 48.dp * scale
    val badgeSize = 28.dp * scale
    val iconSize = 16.dp * scale
    Box(
        modifier = modifier.size(markerSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(badgeSize)
                    .shadow(3.dp * scale, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(mapHazardMarkerColor(type), CircleShape)
                    .border(1.dp * scale, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = mapHazardMarkerIcon(type),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = mapHazardGlyphColor(type),
            )
        }
    }
}

internal fun mapHazardMarkerColor(type: String): Color =
    when (type.lowercase(Locale.US)) {
        "police" -> Color(0xFF1E5EFF)
        "traffic" -> Color(0xFFFFA000)
        "crash" -> Color(0xFFE53935)
        else -> Color(0xFFFDD835)
    }

internal fun mapHazardMarkerIcon(type: String): ImageVector =
    when (type.lowercase(Locale.US)) {
        "police" -> Icons.Outlined.PersonPin
        "traffic" -> Icons.Outlined.Speed
        "crash" -> Icons.Outlined.DirectionsCar
        else -> Icons.Outlined.Warning
    }

private fun mapHazardGlyphColor(type: String): Color =
    when (type.lowercase(Locale.US)) {
        "hazard" -> Color(0xFF1F1F1F)
        else -> Color.White
    }

internal fun mapHazardAnnotationRefreshId(
    id: String,
    type: String,
    latitudeDelta: Double,
): String =
    when (MapDiscoveryMarkerLOD.presentation(latitudeDelta)) {
        MapDiscoveryMarkerPresentation.Dot -> "$id-${type.lowercase(Locale.US)}-hazard-dot"
        MapDiscoveryMarkerPresentation.Pin -> "$id-${type.lowercase(Locale.US)}-hazard-pin"
    }
