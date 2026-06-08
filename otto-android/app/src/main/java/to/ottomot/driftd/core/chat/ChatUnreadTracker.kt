package to.ottomot.driftd.core.chat

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.DirectMessageDto

class ChatUnreadTracker(
    private val cursorStore: ChatReadCursorStore,
) {
    private val _unreadCountByCircleId = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountByCircleId: StateFlow<Map<String, Int>> = _unreadCountByCircleId.asStateFlow()

    private val _unreadCountByConversationId = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountByConversationId: StateFlow<Map<String, Int>> = _unreadCountByConversationId.asStateFlow()

    val totalChatUnreadCount: Int
        get() = _unreadCountByCircleId.value.values.sum() + _unreadCountByConversationId.value.values.sum()

    private var currentUserId: String = ""
    var squadChatTabVisibleCircleId: String? = null
    var directThreadVisibleConversationId: String? = null

    fun bind(currentUserIdRaw: String) {
        val trimmed = currentUserIdRaw.trim()
        if (trimmed == currentUserId) return
        currentUserId = trimmed
        cursorStore.bind(if (trimmed.isEmpty()) null else trimmed)
    }

    fun clearAll() {
        _unreadCountByCircleId.value = emptyMap()
        _unreadCountByConversationId.value = emptyMap()
        cursorStore.clearAll()
    }

    fun setSquadChatTabVisible(circleId: String?, visible: Boolean) {
        val key = circleId?.trim()?.takeIf { it.isNotEmpty() }
        squadChatTabVisibleCircleId =
            when {
                visible && key != null -> key
                !visible && (squadChatTabVisibleCircleId == key || key == null) -> null
                else -> squadChatTabVisibleCircleId
            }
    }

    fun setDirectThreadVisible(conversationId: String?, visible: Boolean) {
        val key = conversationId?.trim()?.takeIf { it.isNotEmpty() }
        directThreadVisibleConversationId =
            when {
                visible && key != null -> key
                !visible && (directThreadVisibleConversationId == key || key == null) -> null
                else -> directThreadVisibleConversationId
            }
    }

    fun markSquadReadIfChatTabVisible(circleId: String, messages: List<CircleChatMessageDto>) {
        val key = circleId.trim().takeIf { it.isNotEmpty() } ?: return
        if (squadChatTabVisibleCircleId != key) return
        val newest = messages.lastOrNull()
        if (newest == null) {
            _unreadCountByCircleId.update { it - key }
            return
        }
        cursorStore.markRead(
            thread = ChatReadThreadKey(ChatReadThreadKind.Squad, key),
            messageId = newest.id,
            readAt = ChatUnreadCounting.messageCreatedAt(newest) ?: Instant.EPOCH,
        )
        _unreadCountByCircleId.update { it - key }
    }

    fun markDirectReadIfThreadVisible(conversationId: String, messages: List<DirectMessageDto>) {
        val key = conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        if (directThreadVisibleConversationId != key) return
        val newest = messages.lastOrNull()
        if (newest == null) {
            _unreadCountByConversationId.update { it - key }
            return
        }
        cursorStore.markRead(
            thread = ChatReadThreadKey(ChatReadThreadKind.Direct, key),
            messageId = newest.id,
            readAt = ChatUnreadCounting.messageCreatedAt(newest) ?: Instant.EPOCH,
        )
        _unreadCountByConversationId.update { it - key }
    }

    fun recomputeSquad(circleId: String, messages: List<CircleChatMessageDto>) {
        val key = circleId.trim().takeIf { it.isNotEmpty() } ?: return
        val count =
            ChatUnreadCounting.squadUnreadCount(
                messages = messages,
                cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Squad, key)),
                currentUserId = currentUserId,
                trackingStartedAt = cursorStore.trackingStartedAt,
            )
        _unreadCountByCircleId.update { map ->
            if (count > 0) map + (key to count) else map - key
        }
    }

    fun recomputeDirect(conversationId: String, messages: List<DirectMessageDto>) {
        val key = conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        val count =
            ChatUnreadCounting.directUnreadCount(
                messages = messages,
                cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Direct, key)),
                currentUserId = currentUserId,
                trackingStartedAt = cursorStore.trackingStartedAt,
            )
        _unreadCountByConversationId.update { map ->
            if (count > 0) map + (key to count) else map - key
        }
    }

    fun recomputeDirectFromPreview(conversation: DirectConversationDto) {
        val key = conversation.id.trim().takeIf { it.isNotEmpty() } ?: return
        val count =
            ChatUnreadCounting.previewUnreadEstimate(
                lastMessageAt = conversation.lastMessageAt,
                lastMessageSenderUserId = ChatUnreadCounting.directPreviewSender(conversation),
                cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Direct, key)),
                currentUserId = currentUserId,
                trackingStartedAt = cursorStore.trackingStartedAt,
            )
        _unreadCountByConversationId.update { map ->
            if (count > 0) map + (key to count) else map - key
        }
    }

    fun recomputeAll(
        squadMessagesByCircleId: Map<String, List<CircleChatMessageDto>>,
        directMessagesByConversationId: Map<String, List<DirectMessageDto>>,
        directConversations: List<DirectConversationDto>,
    ) {
        val trackingStartedAt = cursorStore.trackingStartedAt
        val squads = mutableMapOf<String, Int>()
        squadMessagesByCircleId.forEach { (circleId, messages) ->
            val key = circleId.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val count =
                ChatUnreadCounting.squadUnreadCount(
                    messages = messages,
                    cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Squad, key)),
                    currentUserId = currentUserId,
                    trackingStartedAt = trackingStartedAt,
                )
            if (count > 0) squads[key] = count
        }

        val directs = mutableMapOf<String, Int>()
        directConversations.forEach { conversation ->
            val key = conversation.id.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val cached = directMessagesByConversationId[key].orEmpty()
            val count =
                if (cached.isEmpty()) {
                    ChatUnreadCounting.previewUnreadEstimate(
                        lastMessageAt = conversation.lastMessageAt,
                        lastMessageSenderUserId = ChatUnreadCounting.directPreviewSender(conversation),
                        cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Direct, key)),
                        currentUserId = currentUserId,
                        trackingStartedAt = trackingStartedAt,
                    )
                } else {
                    ChatUnreadCounting.directUnreadCount(
                        messages = cached,
                        cursor = cursorStore.cursor(ChatReadThreadKey(ChatReadThreadKind.Direct, key)),
                        currentUserId = currentUserId,
                        trackingStartedAt = trackingStartedAt,
                    )
                }
            if (count > 0) directs[key] = count
        }

        _unreadCountByCircleId.value = squads
        _unreadCountByConversationId.value = directs
    }

    fun maybeMarkSquadReadIfEligible(
        circleId: String,
        messages: List<CircleChatMessageDto>,
        isPinnedToBottom: Boolean = false,
        lastReadMessageId: String? = null,
    ) {
        markSquadReadIfChatTabVisible(circleId, messages)
    }

    fun maybeMarkDirectReadIfEligible(
        conversationId: String,
        messages: List<DirectMessageDto>,
        isPinnedToBottom: Boolean = false,
        lastReadMessageId: String? = null,
    ) {
        markDirectReadIfThreadVisible(conversationId, messages)
    }

    fun handleSquadMessageUpsert(
        circleId: String,
        messages: List<CircleChatMessageDto>,
        isPinnedToBottom: Boolean = false,
        lastReadMessageId: String? = null,
    ) {
        maybeMarkSquadReadIfEligible(circleId, messages)
        val key = circleId.trim()
        if (squadChatTabVisibleCircleId == key) return
        recomputeSquad(circleId, messages)
    }

    fun handleDirectMessageUpsert(
        conversationId: String,
        messages: List<DirectMessageDto>,
        isPinnedToBottom: Boolean = false,
        lastReadMessageId: String? = null,
    ) {
        maybeMarkDirectReadIfEligible(conversationId, messages)
        val key = conversationId.trim()
        if (directThreadVisibleConversationId == key) return
        recomputeDirect(conversationId, messages)
    }
}
