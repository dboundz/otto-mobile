package to.ottomot.driftd.core.chat

import java.time.Instant
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto

class ChatUnreadCountingTest {
    @Test
    fun messageWithMissingSenderIsNotUnread() {
        assertFalse(
            ChatUnreadCounting.isMessageUnread(
                messageId = "msg1",
                createdAt = Instant.parse("2026-06-06T20:00:00Z"),
                senderUserId = null,
                isDeleted = false,
                cursor = null,
                currentUserId = "me",
                trackingStartedAt = null,
            ),
        )
    }

    @Test
    fun squadUnreadCountSkipsMessagesWithMissingSender() {
        val messages =
            listOf(
                Gson().fromJson(
                    """
                    {
                      "_id": "msg1",
                      "circleId": "circle1",
                      "senderUserId": null,
                      "sender": null,
                      "body": "system-ish row",
                      "createdAt": "2026-06-06T20:00:00Z"
                    }
                    """.trimIndent(),
                    CircleChatMessageDto::class.java,
                ),
                CircleChatMessageDto(
                    id = "msg2",
                    circleId = "circle1",
                    senderUserId = "other",
                    sender = null,
                    body = "hello",
                    createdAt = "2026-06-06T20:01:00Z",
                ),
            )

        assertEquals(
            1,
            ChatUnreadCounting.squadUnreadCount(
                messages = messages,
                cursor = null,
                currentUserId = "me",
                trackingStartedAt = null,
            ),
        )
    }
}
