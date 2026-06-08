package to.ottomot.driftd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.analytics.OttoAnalytics
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.core.notify.registerAndroidFcmTokenWithBackend
import to.ottomot.driftd.core.permissions.OttoLaunchRuntimePermissions
import to.ottomot.driftd.ui.auth.AuthGateMode
import to.ottomot.driftd.ui.auth.AuthGateScreen
import to.ottomot.driftd.R
import to.ottomot.driftd.ui.squad.SquadInviteAcceptDialog
import to.ottomot.driftd.ottoUserIdsEqual

@Composable
fun OttoRoot(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenNullable by container.sessionRepository.authTokenState.collectAsStateWithLifecycle()
    val requiresOnboardingName by
        container.sessionRepository.requiresOnboardingNameState.collectAsStateWithLifecycle()
    val deeplinkBump by InviteDeepLinkStore.deeplinkSignals.collectAsStateWithLifecycle()

    val modifierFill = modifier.fillMaxSize()

    OttoLaunchRuntimePermissions()

    val authMode =
        when {
            tokenNullable.isNullOrBlank() -> AuthGateMode.SignIn
            requiresOnboardingName -> AuthGateMode.CompleteProfile
            else -> null
        }

    var squadInvitePrompt by remember { mutableStateOf<InviteLinkResolveDto?>(null) }
    var isAcceptingSquadInvite by remember { mutableStateOf(false) }

    fun clearPendingSquadInvite() {
        PendingSquadInviteStore.clear(context)
        squadInvitePrompt = null
    }

    suspend fun resolvePendingSquadInviteIfNeeded() {
        if (authMode != null) return
        if (squadInvitePrompt != null) return
        val pending = PendingSquadInviteStore.load(context) ?: return
        val circles = container.dataRepository.circles().getOrNull().orEmpty()
        container.dataRepository.resolveInviteLink(pending.code, pending.squadId).fold(
            onSuccess = { resolved ->
                val circleId = resolved.circle?.id?.trim().orEmpty()
                if (circleId.isEmpty()) {
                    clearPendingSquadInvite()
                    return
                }
                if (circles.any { ottoUserIdsEqual(it.id, circleId) }) {
                    clearPendingSquadInvite()
                    return
                }
                squadInvitePrompt = resolved
            },
            onFailure = {
                // Keep pending invite so a later foreground retry can show the prompt.
            },
        )
    }

    LaunchedEffect(tokenNullable, authMode) {
        if (tokenNullable.isNullOrBlank()) return@LaunchedEffect
        if (authMode == AuthGateMode.SignIn) return@LaunchedEffect
        registerAndroidFcmTokenWithBackend(container)
    }

    LaunchedEffect(authMode, deeplinkBump) {
        resolvePendingSquadInviteIfNeeded()
    }

    Box(modifier = modifierFill) {
        if (authMode != null) {
            AuthGateScreen(
                repository = container.authRepository,
                mode = authMode,
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
        } else {
            OttoShell(container = container, modifier = Modifier.fillMaxSize())
        }

        squadInvitePrompt?.let { prompt ->
            SquadInviteAcceptDialog(
                resolve = prompt,
                isAccepting = isAcceptingSquadInvite,
                onAccept = {
                    val token = prompt.token.trim()
                    val circleId =
                        prompt.circle?.id?.trim()?.takeIf { it.isNotEmpty() }
                            ?: PendingSquadInviteStore.load(context)?.squadId
                    if (token.isEmpty() || circleId.isNullOrEmpty()) {
                        clearPendingSquadInvite()
                        return@SquadInviteAcceptDialog
                    }
                    scope.launch {
                        isAcceptingSquadInvite = true
                        container.dataRepository.acceptInviteLink(token, circleId).fold(
                            onSuccess = {
                                OttoAnalytics.logSquadJoined("invite_link")
                                clearPendingSquadInvite()
                                InviteDeepLinkStore.notifyInviteAcceptSuccess()
                            },
                            onFailure = {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.squad_invite_accept_failed),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                        )
                        isAcceptingSquadInvite = false
                    }
                },
                onDecline = { clearPendingSquadInvite() },
            )
        }
    }
}
