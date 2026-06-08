package to.ottomot.driftd

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.ui.components.ShareSheetActionRow
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPlaceLongPressActionSheet(
    sheetState: SheetState,
    isResolving: Boolean,
    previewName: String?,
    previewAddress: String?,
    payload: MapMarkerSharePayload,
    onDismiss: () -> Unit,
    onShareToChat: () -> Unit,
    onSave: () -> Unit,
) {
    val ctx = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.map_place_action_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isResolving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                        Text(
                            stringResource(R.string.map_place_action_resolving),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                } else {
                    previewName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 2,
                        )
                    }
                    previewAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { address ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                address,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 3,
                            )
                        }
                    }
                    if (previewName.isNullOrBlank() && previewAddress.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.map_place_action_resolving_fallback),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareSheetActionRow(
                    title = stringResource(R.string.event_share_to_squad_chat),
                    subtitle = stringResource(R.string.event_share_to_squad_chat_subtitle),
                    icon = Icons.Outlined.Forum,
                    onClick = onShareToChat,
                )
                ShareSheetActionRow(
                    title = stringResource(R.string.event_share_external),
                    subtitle = stringResource(R.string.event_share_external_subtitle),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, payload.title)
                                putExtra(Intent.EXTRA_TEXT, payload.externalShareText)
                            }
                        ctx.startActivity(Intent.createChooser(intent, payload.title))
                    },
                )
                ShareSheetActionRow(
                    title = stringResource(R.string.map_place_action_save),
                    subtitle = stringResource(R.string.map_place_action_save_subtitle),
                    icon = Icons.Outlined.Bookmark,
                    onClick = onSave,
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    }
}
