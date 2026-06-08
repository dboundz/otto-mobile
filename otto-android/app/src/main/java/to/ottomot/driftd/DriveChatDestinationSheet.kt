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
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.CircleDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriveChatDestinationSheet(
    sheetState: SheetState,
    context: DriveChatShareContext,
    circles: List<CircleDto>,
    onDismiss: () -> Unit,
    onShareToChat: suspend (circleId: String, context: DriveChatShareContext) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    var selectedCircleId by remember(context.lockedCircleId, circles) {
        mutableStateOf(context.lockedCircleId?.trim().orEmpty() ?: "")
    }
    var sending by remember { mutableStateOf(false) }

    val available =
        remember(circles, context.lockedCircleId) {
            val locked = context.lockedCircleId?.trim()?.takeIf { it.isNotEmpty() }
            if (locked != null) {
                circles.filter { it.id.trim() == locked }
            } else {
                circles
            }
        }

    val selectedName =
        available.find { it.id.trim() == selectedCircleId.trim() }?.name
            ?: stringResource(R.string.squad_chat_event_untitled)

    val distance = formatDriveDistanceMiles(context.previewDistanceMeters)
    val duration = formatDriveDurationSeconds(context.previewDriveTimeSeconds.toDouble())
    val canSend = selectedCircleId.isNotBlank() && !sending

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
                    OttoDriveSteeringIcon(size = 22.dp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.drive_chat_post_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.drive_chat_post_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.settings_cancel), tint = Color.White.copy(alpha = 0.72f))
                }
            }

            Spacer(Modifier.height(16.dp))

            DriveSharePreviewCard(title = context.previewTitle, meta = "$distance · $duration")

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.drive_chat_squad_section),
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
                        val selected = selectedCircleId.trim() == id
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) Color(0xFF7B3DFF).copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.055f),
                                    )
                                    .clickable { selectedCircleId = id }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                circle.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                modifier = Modifier.weight(1f),
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
                        onShareToChat(selectedCircleId.trim(), context)
                        sending = false
                        onDismiss()
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
                            stringResource(R.string.drive_chat_choose_squad)
                        } else {
                            stringResource(R.string.drive_chat_share_to, selectedName)
                        },
                    )
                }
            }
        }
    }
}
