package to.ottomot.driftd

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.ui.components.ShareSheetActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapMarkerShareSquadActionsSheet(
    sheetState: SheetState,
    payload: MapMarkerSharePayload,
    onDismiss: () -> Unit,
    onShareToChat: () -> Unit,
) {
    val ctx = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.event_share_squad_actions_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.event_share_section_share),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShareSheetActionRow(
                    title = stringResource(R.string.event_share_to_squad_chat),
                    subtitle = stringResource(R.string.event_share_to_squad_chat_subtitle),
                    icon = Icons.Outlined.Forum,
                    onClick = {
                        onDismiss()
                        onShareToChat()
                    },
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
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        }
    }
}
