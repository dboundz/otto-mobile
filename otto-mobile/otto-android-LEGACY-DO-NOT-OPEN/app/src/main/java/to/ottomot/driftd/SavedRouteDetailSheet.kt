package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.core.network.dto.SavedRouteDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedRouteDetailSheet(
    sheetState: SheetState,
    route: SavedRouteDto,
    onDismiss: () -> Unit,
) {
    val waypointCount = route.points.orEmpty().count { (it.markerType ?: "path") != "path" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF07080B),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .ottoBottomSheetContent(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.saved_route_detail_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.settings_cancel), tint = Color.White.copy(alpha = 0.72f))
                }
            }

            Spacer(Modifier.height(12.dp))

            ProfileSectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF7B3DFF).copy(alpha = 0.18f))
                            .padding(12.dp),
                    ) {
                        Icon(Icons.Outlined.Route, contentDescription = null, tint = Color(0xFF7B3DFF))
                    }
                    Column {
                        Text(route.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Text(savedRouteSubtitle(route), color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                        if (waypointCount > 0) {
                            Text(
                                stringResource(R.string.saved_route_waypoints, waypointCount),
                                color = Color(0xFF7B3DFF),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                DriveRouteMapPreviewFromSavedRoute(route)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    }
}
