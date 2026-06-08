package to.ottomot.driftd.core.notify

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatEngagementThrottleTest {
    private val key = "squad:test-circle"
    private val t0 = 1_700_000_000_000L

    @After
    fun tearDown() {
        ChatEngagementThrottle.resetForTesting()
    }

    @Test
    fun quietThreadFirstAlertUses15sCooldown() {
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = t0)

        assertEquals(0, ChatEngagementThrottle.tierIndexForTesting(key))
        assertEquals(t0 + 15_000L, ChatEngagementThrottle.cooldownUntilMsForTesting(key))
    }

    @Test
    fun burstEscalatesTierAfterSilentUpdates() {
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = t0)
        ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = t0 + 1_000L)
        val secondFull = t0 + 16_000L
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = secondFull)

        assertEquals(1, ChatEngagementThrottle.tierIndexForTesting(key))
        assertEquals(secondFull + 15_000L, ChatEngagementThrottle.cooldownUntilMsForTesting(key))

        ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = secondFull + 1_000L)
        val thirdFull = secondFull + 16_000L
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = thirdFull)

        assertEquals(2, ChatEngagementThrottle.tierIndexForTesting(key))
        assertEquals(thirdFull + 30_000L, ChatEngagementThrottle.cooldownUntilMsForTesting(key))
    }

    @Test
    fun idle60sAtMaxTierDecaysOneStep() {
        var now = t0
        repeat(3) {
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 16_000L
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 1_000L
        }
        assertEquals(3, ChatEngagementThrottle.tierIndexForTesting(key))

        val lastActivity = now
        val afterIdle = lastActivity + 60_000L + 1_000L
        val outcome =
            ChatEngagementThrottle.evaluateSquadMessage(
                circleId = "test-circle",
                pushType = "circle.chat.new_message",
                focusedChatCircleId = null,
                isMuted = false,
                nowMs = afterIdle,
            )
        assertEquals(ChatEngagementAlertOutcome.FullAlert, outcome.first)
        assertEquals(2, ChatEngagementThrottle.tierIndexForTesting(key))
    }

    @Test
    fun idle180sAtMaxTierReturnsToBaseline() {
        var now = t0
        repeat(3) {
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 16_000L
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 1_000L
        }
        assertEquals(3, ChatEngagementThrottle.tierIndexForTesting(key))

        ChatEngagementThrottle.evaluateSquadMessage(
            circleId = "test-circle",
            pushType = "circle.chat.new_message",
            focusedChatCircleId = null,
            isMuted = false,
            nowMs = now + 180_000L,
        )
        assertEquals(0, ChatEngagementThrottle.tierIndexForTesting(key))
    }

    @Test
    fun mentionDoesNotEscalateTier() {
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = t0)
        ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = t0 + 1_000L)
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = t0 + 16_000L)
        assertEquals(1, ChatEngagementThrottle.tierIndexForTesting(key))

        val mentionAt = t0 + 32_000L
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.mention", nowMs = mentionAt)

        assertEquals(1, ChatEngagementThrottle.tierIndexForTesting(key))
        assertEquals(mentionAt + 15_000L, ChatEngagementThrottle.cooldownUntilMsForTesting(key))
    }

    @Test
    fun tierCapsAt60s() {
        var now = t0
        repeat(3) {
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 16_000L
            ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = now)
            ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = now + 1_000L)
            now += 1_000L
        }
        assertEquals(3, ChatEngagementThrottle.tierIndexForTesting(key))

        val cappedAt = now + 16_000L
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = cappedAt)
        ChatEngagementThrottle.recordSilentUpdate(key, pushType = "circle.chat.new_message", nowMs = cappedAt + 1_000L)
        val beyondCap = cappedAt + 16_000L
        ChatEngagementThrottle.recordFullAlert(key, pushType = "circle.chat.new_message", nowMs = beyondCap)

        assertEquals(3, ChatEngagementThrottle.tierIndexForTesting(key))
        assertEquals(beyondCap + 60_000L, ChatEngagementThrottle.cooldownUntilMsForTesting(key))
    }
}
