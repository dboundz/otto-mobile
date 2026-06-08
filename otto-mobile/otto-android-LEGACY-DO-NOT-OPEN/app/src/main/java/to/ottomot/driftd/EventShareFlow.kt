package to.ottomot.driftd

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import to.ottomot.driftd.appContainer
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.EventAttachedSquadDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.UserDto
import java.util.Locale

internal fun eventShareDmContactsFromCircles(
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    myUserId: String?,
): List<UserDto> {
    if (myUserId.isNullOrBlank()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<UserDto>()
    val locale = Locale.getDefault()
    for (circle in circles) {
        for (m in circle.members.orEmpty()) {
            val uid = m.userId.trim()
            if (uid.isEmpty() || uid == myUserId) continue
            if (seen.add(uid)) {
                contacts.find { it.id == uid }?.let { out.add(it) }
            }
        }
    }
    out.sortWith(compareBy { it.displayName.trim().lowercase(locale) })
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventShareFlowSheets(
    event: EventDto?,
    shareSquadActionsOpen: Boolean,
    onShareSquadActionsOpenChange: (Boolean) -> Unit,
    shareToChatSheetOpen: Boolean,
    onShareToChatSheetOpenChange: (Boolean) -> Unit,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    dmConversations: List<DirectConversationDto>,
    meUser: UserDto?,
    lockedCircleId: String? = null,
    actionBusy: Boolean = false,
    onPrefetchDirectMessages: () -> Unit = {},
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    pendingSquadChatFocusTick: Long = 0L,
    onEventAssociationsSaved: (List<EventAttachedSquadDto>) -> Unit,
) {
    LaunchedEffect(pendingSquadChatFocusTick) {
        if (pendingSquadChatFocusTick > 0L) {
            onShareSquadActionsOpenChange(false)
            onShareToChatSheetOpenChange(false)
        }
    }
    val ctx = LocalContext.current
    val dataRepository = remember(ctx) { ctx.applicationContext.appContainer().dataRepository }
    val shareSquadActionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareToChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(shareToChatSheetOpen) {
        if (shareToChatSheetOpen) {
            onPrefetchDirectMessages()
        }
    }

    val circlesForShareSheet =
        remember(circles, lockedCircleId) {
            val lock = lockedCircleId?.trim()?.takeIf { it.isNotEmpty() }
            if (lock != null) circles.filter { it.id == lock } else circles
        }

    val dmContactsForShare =
        remember(circles, contacts, meUser?.id, lockedCircleId) {
            val lock = lockedCircleId?.trim()?.takeIf { it.isNotEmpty() }
            if (lock != null) {
                emptyList()
            } else {
                eventShareDmContactsFromCircles(circles, contacts, meUser?.id)
            }
        }

    if (shareSquadActionsOpen && event != null) {
        EventShareSquadActionsSheet(
            sheetState = shareSquadActionsSheetState,
            event = event,
            circles = circles,
            lockedCircleId = lockedCircleId,
            onDismiss = { onShareSquadActionsOpenChange(false) },
            onShareToChat = {
                onShareSquadActionsOpenChange(false)
                onShareToChatSheetOpenChange(true)
            },
            onFetchAdminSquads = {
                dataRepository.fetchAdminSquads().getOrElse { emptyList() }
            },
            onFetchAssociations = { eventId ->
                dataRepository.fetchEventSquadAssociations(eventId).getOrElse { emptyList() }
            },
            onSaveAssociations = { eventId, squadIds ->
                dataRepository.patchEventSquadAssociations(eventId, squadIds)
            },
            onAssociationsSaved = onEventAssociationsSaved,
        )
    }

    if (shareToChatSheetOpen && event != null) {
        EventShareToChatSheet(
            sheetState = shareToChatSheetState,
            event = event,
            circlesAvailable = circlesForShareSheet,
            contacts = contacts,
            dmContactsForShare = dmContactsForShare,
            dmConversations = dmConversations,
            meUser = meUser,
            circlesForDmSubtitle = circles,
            busy = actionBusy,
            onDismiss = { onShareToChatSheetOpenChange(false) },
            onPost = { circleIds, dmPeerIds, message ->
                postEventShareToChat(event.id, circleIds, dmPeerIds, message)
            },
        )
    }
}
