package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.ui.components.SquadShareListRow
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteChatDestinationSheet(
    sheetState: SheetState,
    route: SavedRouteDto,
    circles: List<CircleDto>,
    lockedCircleId: String? = null,
    onDismiss: () -> Unit,
    onShareToChat: suspend (circleId: String, route: SavedRouteDto) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    var selectedCircleId by remember(lockedCircleId, circles) {
        mutableStateOf(lockedCircleId?.trim().orEmpty())
    }
    var sending by remember { mutableStateOf(false) }

    val available =
        remember(circles, lockedCircleId) {
            val locked = lockedCircleId?.trim()?.takeIf { it.isNotEmpty() }
            if (locked != null) {
                circles.filter { ottoUserIdsEqual(it.id, locked) }
            } else {
                circles
            }
        }

    val selectedName =
        available.find { ottoUserIdsEqual(it.id, selectedCircleId) }?.name
            ?: stringResource(R.string.squad_chat_event_untitled)

    val distanceMeters = route.distanceMeters ?: 0.0
    val distance = formatRouteDistanceMiles(distanceMeters)
    val minutes = route.etaSeconds?.let { maxOf(1, (it / 60.0).toInt()) } ?: 1
    val meta = "$distance · $minutes min"
    val canSend = selectedCircleId.isNotBlank() && !sending
    val locked = lockedCircleId?.trim()?.isNotEmpty() == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
                .ottoBottomSheetContent(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF7B3DFF).copy(alpha = 0.22f))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SavedRouteListIcon(size = 22.dp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.route_chat_post_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.route_chat_post_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_cancel),
                        tint = Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(14.dp),
            ) {
                Text(
                    route.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.route_chat_squad_section),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(8.dp))

            if (available.isEmpty()) {
                Text(
                    stringResource(R.string.drive_chat_no_squads),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.055f))
                            .padding(14.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { circle ->
                        val id = circle.id.trim()
                        val selected = ottoUserIdsEqual(selectedCircleId, id)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) Color(0xFF7B3DFF).copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.055f),
                                    )
                                    .clickable(enabled = !locked) { selectedCircleId = id }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SquadShareListRow(
                                squadName = circle.name,
                                photoUrl = circle.photoUrl,
                                memberCount = circle.members?.size ?: 0,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (selected) "●" else "○",
                                color = if (selected) Color(0xFF7B3DFF) else Color.White.copy(alpha = 0.28f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!canSend) return@Button
                    sending = true
                    scope.launch {
                        onShareToChat(selectedCircleId.trim(), route)
                        sending = false
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(
                        if (selectedCircleId.isBlank()) {
                            stringResource(R.string.route_chat_choose_squad)
                        } else {
                            stringResource(R.string.route_chat_share_to, selectedName)
                        },
                    )
                }
            }
        }
    }
}

private fun formatRouteDistanceMiles(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles >= 10) "%.0f mi".format(miles) else "%.1f mi".format(miles)
}
