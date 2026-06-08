package to.ottomot.driftd.core.notify

import java.util.concurrent.ConcurrentHashMap

enum class ChatEngagementAlertOutcome {
    Suppress,
    FullAlert,
    SilentBannerUpdate,
}

/**
 * Per-conversation foreground alert cooldown with tier ladder [15, 30, 45, 60]s.
 * Escalates after burst silent updates; decays one tier per 60s idle.
 * Mentions, replies, and DMs always use 15s and never change tier.
 */
object ChatEngagementThrottle {
    private val cooldownTiersMs = longArrayOf(15_000L, 30_000L, 45_000L, 60_000L)
    private const val IDLE_DECAY_MS = 60_000L

    private data class ConversationNotificationState(
        var lastAlertAtMs: Long? = null,
        var lastActivityAtMs: Long? = null,
        var cooldownUntilMs: Long? = null,
        var suppressedCount: Int = 0,
        var tierIndex: Int = 0,
    )

    private val stateByKey = ConcurrentHashMap<String, ConversationNotificationState>()

    fun squadConversationKey(circleId: String): String {
        val trimmed = circleId.trim()
        return "squad:$trimmed"
    }

    fun directConversationKey(conversationId: String): String {
        val trimmed = conversationId.trim()
        return "dm:$trimmed"
    }

    fun foregroundNotificationId(conversationKey: String): Int =
        "otto.chat.foreground.$conversationKey".hashCode()

    fun evaluateSquadMessage(
        circleId: String,
        pushType: String = "circle.chat.new_message",
        focusedChatCircleId: String?,
        isMuted: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<ChatEngagementAlertOutcome, Int> {
        val trimmedCircle = circleId.trim()
        if (trimmedCircle.isEmpty()) return ChatEngagementAlertOutcome.Suppress to 0
        if (focusedChatCircleId != null &&
            focusedChatCircleId.trim().equals(trimmedCircle, ignoreCase = true)
        ) {
            return ChatEngagementAlertOutcome.Suppress to 0
        }
        if (isMuted) return ChatEngagementAlertOutcome.Suppress to 0
        return evaluateCooldown(squadConversationKey(trimmedCircle), nowMs)
    }

    fun evaluateDirectMessage(
        conversationId: String,
        focusedConversationId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<ChatEngagementAlertOutcome, Int> {
        val trimmedConversation = conversationId.trim()
        if (trimmedConversation.isEmpty()) return ChatEngagementAlertOutcome.Suppress to 0
        if (focusedConversationId != null &&
            focusedConversationId.trim().equals(trimmedConversation, ignoreCase = true)
        ) {
            return ChatEngagementAlertOutcome.Suppress to 0
        }
        return evaluateCooldown(directConversationKey(trimmedConversation), nowMs)
    }

    fun recordFullAlert(
        conversationKey: String,
        pushType: String = "circle.chat.new_message",
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val state = stateByKey.getOrPut(conversationKey) { ConversationNotificationState() }
        applyIdleDecay(state, nowMs)
        val tiered = usesTieredCooldown(pushType)
        val durationMs = cooldownDurationMs(state.tierIndex, tiered)
        state.lastAlertAtMs = nowMs
        state.lastActivityAtMs = nowMs
        state.cooldownUntilMs = nowMs + durationMs
        if (tiered && state.suppressedCount > 0) {
            state.tierIndex = minOf(state.tierIndex + 1, cooldownTiersMs.lastIndex)
        }
        state.suppressedCount = 0
        stateByKey[conversationKey] = state
    }

    fun recordSilentUpdate(
        conversationKey: String,
        pushType: String = "circle.chat.new_message",
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val state = stateByKey.getOrPut(conversationKey) { ConversationNotificationState() }
        applyIdleDecay(state, nowMs)
        state.suppressedCount += 1
        state.lastActivityAtMs = nowMs
        val tiered = usesTieredCooldown(pushType)
        if (state.cooldownUntilMs == null) {
            state.cooldownUntilMs = nowMs + cooldownDurationMs(state.tierIndex, tiered)
        }
        stateByKey[conversationKey] = state
    }

    fun bannerBody(base: String, suppressedCount: Int): String =
        if (suppressedCount > 0) {
            "$base (+$suppressedCount more)"
        } else {
            base
        }

    /** Clears in-memory throttle state (unit tests). */
    internal fun resetForTesting() {
        stateByKey.clear()
    }

    internal fun tierIndexForTesting(conversationKey: String): Int =
        stateByKey[conversationKey]?.tierIndex ?: 0

    internal fun cooldownUntilMsForTesting(conversationKey: String): Long? =
        stateByKey[conversationKey]?.cooldownUntilMs

    private fun usesTieredCooldown(pushType: String): Boolean =
        pushType.trim() == "circle.chat.new_message"

    private fun cooldownDurationMs(tierIndex: Int, tiered: Boolean): Long {
        if (!tiered) return cooldownTiersMs[0]
        val clamped = tierIndex.coerceIn(0, cooldownTiersMs.lastIndex)
        return cooldownTiersMs[clamped]
    }

    private fun applyIdleDecay(state: ConversationNotificationState, nowMs: Long) {
        if (state.tierIndex <= 0 || state.lastActivityAtMs == null) return
        val idleMs = nowMs - state.lastActivityAtMs!!
        val stepsDown = minOf(state.tierIndex, (idleMs / IDLE_DECAY_MS).toInt())
        if (stepsDown <= 0) return
        state.tierIndex -= stepsDown
        state.suppressedCount = 0
    }

    private fun evaluateCooldown(
        key: String,
        nowMs: Long,
    ): Pair<ChatEngagementAlertOutcome, Int> {
        val state = stateByKey.getOrPut(key) { ConversationNotificationState() }
        applyIdleDecay(state, nowMs)
        state.lastActivityAtMs = nowMs
        stateByKey[key] = state

        val cooldownUntil = state.cooldownUntilMs
        if (cooldownUntil != null && nowMs < cooldownUntil) {
            return ChatEngagementAlertOutcome.SilentBannerUpdate to (state.suppressedCount + 1)
        }
        return ChatEngagementAlertOutcome.FullAlert to 0
    }
}
