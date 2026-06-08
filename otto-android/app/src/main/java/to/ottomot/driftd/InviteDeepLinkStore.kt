package to.ottomot.driftd

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hosts invite links arriving via `ACTION_VIEW` until Otto shell consumes them once. */
object InviteDeepLinkStore {
    @Volatile private var pending: String? = null
    private val _deeplinkSignals = MutableStateFlow(0L)

    /** Bumps whenever a new VIEW intent arrives with `data`; use for `LaunchedEffect` re-triggering. */
    val deeplinkSignals: StateFlow<Long> = _deeplinkSignals.asStateFlow()

    private val _acceptRefreshSignals = MutableStateFlow(0L)

    /** Bumps after a squad invite link is accepted in [OttoRoot]; [OttoShell] reloads feeds. */
    val acceptRefreshSignals: StateFlow<Long> = _acceptRefreshSignals.asStateFlow()

    fun notifyInviteAcceptSuccess() {
        _acceptRefreshSignals.update { it + 1L }
    }

    fun offer(context: Context, intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val s = uri.toString().trim().ifEmpty { return }
        pending = s
        PendingSquadInviteStore.persist(context, uri)
        _deeplinkSignals.update { it + 1L }
    }

    /** Returns and clears any pending deeplink URI string (thread-safe for single consumer). */
    fun consume(): String? {
        val p = pending
        pending = null
        return p
    }
}
