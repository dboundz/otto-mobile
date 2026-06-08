package to.ottomot.driftd.map

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun MapDiscoveryMarkerDotView(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(44.dp),
        contentAlignment =
            Alignment.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .shadow(2.dp, CircleShape)
                .background(color, CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.92f), CircleShape),
        )
    }
}

@Composable
internal fun MapDiscoveryMarkerLODView(
    kind: MapDiscoveryMarkerKind,
    latitudeDelta: Double,
    modifier: Modifier = Modifier,
    pinContent: @Composable (pinScale: Float) -> Unit,
) {
    when (MapDiscoveryMarkerLOD.presentation(latitudeDelta)) {
        MapDiscoveryMarkerPresentation.Dot ->
            MapDiscoveryMarkerDotView(
                color = MapDiscoveryMarkerLOD.dotColor(kind),
                modifier = modifier,
            )
        MapDiscoveryMarkerPresentation.Pin ->
            Box(modifier = modifier) {
                pinContent(MapDiscoveryMarkerLOD.pinScale(latitudeDelta))
            }
    }
}
