package to.ottomot.driftd

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.UserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapMarkerShareFlowSheets(
    payload: MapMarkerSharePayload?,
    shareSquadActionsOpen: Boolean,
    onShareSquadActionsOpenChange: (Boolean) -> Unit,
    shareToChatSheetOpen: Boolean,
    onShareToChatSheetOpenChange: (Boolean) -> Unit,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    dmConversations: List<DirectConversationDto>,
    meUser: UserDto?,
    actionBusy: Boolean = false,
    onPrefetchDirectMessages: () -> Unit = {},
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    pendingSquadChatFocusTick: Long = 0L,
) {
    LaunchedEffect(pendingSquadChatFocusTick) {
        if (pendingSquadChatFocusTick > 0L) {
            onShareSquadActionsOpenChange(false)
            onShareToChatSheetOpenChange(false)
        }
    }
    val shareSquadActionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareToChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(shareToChatSheetOpen) {
        if (shareToChatSheetOpen) {
            onPrefetchDirectMessages()
        }
    }

    val dmContactsForShare =
        remember(circles, contacts, meUser?.id) {
            eventShareDmContactsFromCircles(circles, contacts, meUser?.id)
        }

    if (shareSquadActionsOpen && payload != null) {
        MapMarkerShareSquadActionsSheet(
            sheetState = shareSquadActionsSheetState,
            payload = payload,
            onDismiss = { onShareSquadActionsOpenChange(false) },
            onShareToChat = {
                onShareSquadActionsOpenChange(false)
                onShareToChatSheetOpenChange(true)
            },
        )
    }

    if (shareToChatSheetOpen && payload != null) {
        MapMarkerShareToChatSheet(
            sheetState = shareToChatSheetState,
            payload = payload,
            circlesAvailable = circles,
            contacts = contacts,
            dmContactsForShare = dmContactsForShare,
            dmConversations = dmConversations,
            meUser = meUser,
            circlesForDmSubtitle = circles,
            busy = actionBusy,
            onDismiss = { onShareToChatSheetOpenChange(false) },
            onPost = { circleIds, dmPeerIds, message ->
                postMapMarkerShareToChat(payload, circleIds, dmPeerIds, message)
            },
        )
    }
}
