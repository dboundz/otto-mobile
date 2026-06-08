package to.ottomot.driftd.core.chat

import java.time.Instant
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.DirectMessageDto

object ChatUnreadCounting {
    fun parseInstant(raw: String?): Instant? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun messageCreatedAt(message: CircleChatMessageDto): Instant? = parseInstant(message.createdAt)

    fun messageCreatedAt(message: DirectMessageDto): Instant? = parseInstant(message.createdAt)

    fun isMessageUnread(
        messageId: String,
        createdAt: Instant?,
        senderUserId: String?,
        isDeleted: Boolean,
        cursor: ChatReadCursor?,
        currentUserId: String,
        trackingStartedAt: Instant?,
    ): Boolean {
        if (isDeleted) return false
        val sender = senderUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (sender == currentUserId) return false
        val created = createdAt ?: return false
        if (trackingStartedAt != null && !created.isAfter(trackingStartedAt)) return false
        val c = cursor ?: return true
        if (created.isAfter(c.lastReadAt)) return true
        if (created.isBefore(c.lastReadAt)) return false
        return messageId > c.lastReadMessageId
    }

    fun squadUnreadCount(
        messages: List<CircleChatMessageDto>,
        cursor: ChatReadCursor?,
        currentUserId: String,
        trackingStartedAt: Instant?,
    ): Int =
        messages.count { msg ->
            isMessageUnread(
                messageId = msg.id,
                createdAt = messageCreatedAt(msg),
                senderUserId = msg.senderUserId,
                isDeleted = msg.deletedAt != null,
                cursor = cursor,
                currentUserId = currentUserId,
                trackingStartedAt = trackingStartedAt,
            )
        }

    fun directUnreadCount(
        messages: List<DirectMessageDto>,
        cursor: ChatReadCursor?,
        currentUserId: String,
        trackingStartedAt: Instant?,
    ): Int =
        messages.count { msg ->
            val sender = msg.senderUserId?.trim().orEmpty().ifEmpty { msg.sender?.id.orEmpty() }
            isMessageUnread(
                messageId = msg.id,
                createdAt = messageCreatedAt(msg),
                senderUserId = sender,
                isDeleted = msg.deletedAt != null,
                cursor = cursor,
                currentUserId = currentUserId,
                trackingStartedAt = trackingStartedAt,
            )
        }

    fun previewUnreadEstimate(
        lastMessageAt: String?,
        lastMessageSenderUserId: String?,
        cursor: ChatReadCursor?,
        currentUserId: String,
        trackingStartedAt: Instant?,
    ): Int {
        val at = parseInstant(lastMessageAt) ?: return 0
        val sender = lastMessageSenderUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return 0
        if (sender == currentUserId) return 0
        if (trackingStartedAt != null && !at.isAfter(trackingStartedAt)) return 0
        val c = cursor ?: return 1
        if (at.isAfter(c.lastReadAt)) return 1
        if (at.isBefore(c.lastReadAt)) return 0
        return 1
    }

    fun directPreviewSender(conversation: DirectConversationDto): String? =
        conversation.lastMessage?.senderUserId?.trim()?.takeIf { it.isNotEmpty() }
}
