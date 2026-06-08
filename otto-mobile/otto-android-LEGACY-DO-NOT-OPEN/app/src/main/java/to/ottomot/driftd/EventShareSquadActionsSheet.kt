package to.ottomot.driftd

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.ottomot.driftd.ui.components.OttoSquadToggleSettingCard
import to.ottomot.driftd.ui.components.ShareSheetActionRow
import to.ottomot.driftd.core.network.dto.AdminSquadDto
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.EventAttachedSquadDto
import to.ottomot.driftd.core.network.dto.EventDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventShareSquadActionsSheet(
    sheetState: SheetState,
    event: EventDto,
    circles: List<CircleDto>,
    lockedCircleId: String?,
    onDismiss: () -> Unit,
    onShareToChat: () -> Unit,
    onFetchAdminSquads: suspend () -> List<AdminSquadDto>,
    onFetchAssociations: suspend (String) -> List<EventAttachedSquadDto>,
    onSaveAssociations: suspend (String, List<String>) -> Result<List<EventAttachedSquadDto>>,
    onAssociationsSaved: (List<EventAttachedSquadDto>) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adminSquads by remember { mutableStateOf<List<AdminSquadDto>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var togglingSquadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isSquadNative = event.visibility?.equals("circle", ignoreCase = true) == true
    val shareUrl = eventPublicWebsiteUrl(event)

    LaunchedEffect(event.id) {
        loading = true
        errorMessage = null
        runCatching {
            val squads = onFetchAdminSquads()
            val attached = onFetchAssociations(event.id)
            adminSquads = squads
            selectedIds = attached.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
        }.onFailure {
            adminSquads = emptyList()
            errorMessage = ctx.getString(R.string.event_share_squad_actions_load_error)
        }
        loading = false
    }

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
                if (!isSquadNative) {
                    ShareSheetActionRow(
                        title = stringResource(R.string.event_share_external),
                        subtitle = stringResource(R.string.event_share_external_subtitle),
                        icon = Icons.Outlined.Share,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, event.name)
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                }
                            ctx.startActivity(Intent.createChooser(intent, event.name))
                        },
                    )
                }
            }

            if (!isSquadNative && adminSquads.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.event_add_to_squads),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.event_add_to_squads_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    errorMessage?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        adminSquads.forEach { squad ->
                            val checked = selectedIds.contains(squad.id)
                            val circle = circles.find { it.id.trim() == squad.id.trim() }
                            OttoSquadToggleSettingCard(
                                squadName = squad.name,
                                photoUrl = circle?.photoUrl ?: squad.photoUrl,
                                memberCount = circle?.members?.size ?: 0,
                                checked = checked,
                                onCheckedChange = { on ->
                                    if (togglingSquadIds.contains(squad.id)) return@OttoSquadToggleSettingCard
                                    val alreadyOn = selectedIds.contains(squad.id)
                                    if (alreadyOn == on) return@OttoSquadToggleSettingCard
                                    scope.launch {
                                        val previous = selectedIds
                                        selectedIds =
                                            if (on) {
                                                selectedIds + squad.id
                                            } else {
                                                selectedIds - squad.id
                                            }
                                        togglingSquadIds = togglingSquadIds + squad.id
                                        errorMessage = null
                                        onSaveAssociations(event.id, selectedIds.toList())
                                            .onSuccess { updated ->
                                                selectedIds =
                                                    updated.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
                                                onAssociationsSaved(updated)
                                                val toastRes =
                                                    if (on) {
                                                        R.string.event_squad_association_added_format
                                                    } else {
                                                        R.string.event_squad_association_removed_format
                                                    }
                                                Toast
                                                    .makeText(
                                                        ctx,
                                                        ctx.getString(toastRes, squad.name),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                            .onFailure {
                                                selectedIds = previous
                                                errorMessage =
                                                    ctx.getString(R.string.event_share_squad_actions_save_error)
                                            }
                                        togglingSquadIds = togglingSquadIds - squad.id
                                    }
                                },
                                trailingText = squad.role.orEmpty().takeIf { it.isNotBlank() },
                                enabled = !togglingSquadIds.contains(squad.id),
                            )
                        }
                        Text(
                            stringResource(R.string.event_add_to_squads_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
