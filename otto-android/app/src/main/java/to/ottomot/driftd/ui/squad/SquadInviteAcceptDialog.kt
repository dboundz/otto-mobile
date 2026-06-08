package to.ottomot.driftd.ui.squad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.R
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.ui.dialog.OttoEducationDialog

@Composable
fun SquadInviteAcceptDialog(
    resolve: InviteLinkResolveDto,
    isAccepting: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val squadName =
        resolve.circle?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.squad_invite_default_squad_name)
    val inviterName =
        resolve.invitedBy?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.squad_invite_default_inviter_name)

    OttoEducationDialog(
        visible = true,
        busy = isAccepting,
        onDismissRequest = onDecline,
        onCloseClick = onDecline,
        hero = {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(Color(0x387B3DFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = Color(0xFF7B3DFF),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = stringResource(R.string.squad_invite_prompt_title, squadName),
        body = stringResource(R.string.squad_invite_prompt_body, inviterName, squadName),
        bulletSectionTitle = null,
        bullets = emptyList(),
        footer = stringResource(R.string.squad_invite_prompt_footer),
        primaryLabel = stringResource(R.string.squad_invite_prompt_accept),
        onPrimaryClick = onAccept,
        secondaryLabel = stringResource(R.string.squad_invite_prompt_decline),
        onSecondaryClick = onDecline,
        allowsUnconfirmedDismiss = true,
    )
}
