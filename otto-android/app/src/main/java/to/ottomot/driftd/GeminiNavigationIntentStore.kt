package to.ottomot.driftd

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds Gemini/Assistant phone navigation intents until the authenticated shell can route them. */
internal object GeminiNavigationIntentStore {
    @Volatile private var pending: NavigationIntentRequest? = null
    private val _signals = MutableStateFlow(0L)

    val signals: StateFlow<Long> = _signals.asStateFlow()

    fun offer(intent: Intent?) {
        val request = intent?.toPhoneNavigationIntentRequest() ?: return
        pending = request
        _signals.update { it + 1L }
    }

    fun consume(): NavigationIntentRequest? {
        val request = pending
        pending = null
        return request
    }
}
