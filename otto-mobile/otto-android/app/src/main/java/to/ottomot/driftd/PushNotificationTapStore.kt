package to.ottomot.driftd

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds FCM notification tap payloads until [OttoShell] consumes them once (iOS `didTapRemoteNotification` parity).
 */
object PushNotificationTapStore {
    const val EXTRA_FROM_PUSH: String = "otto_from_push"

    @Volatile private var pending: Map<String, String>? = null
    private val _signals = MutableStateFlow(0L)

    /** Bumps when MainActivity receives a push tap intent; drive a [LaunchedEffect] in OttoShell. */
    val signals: StateFlow<Long> = _signals.asStateFlow()

    fun offerFromIntent(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra(EXTRA_FROM_PUSH, false)) return
        val extras = intent.extras ?: return
        val map = HashMap<String, String>()
        for (key in extras.keySet()) {
            if (key == EXTRA_FROM_PUSH) continue
            val value = extras.getString(key)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            map[key] = value
        }
        if (map.isEmpty()) return
        pending = map
        _signals.update { it + 1L }
    }

    fun consume(): Map<String, String>? {
        val p = pending
        pending = null
        return p
    }
}
