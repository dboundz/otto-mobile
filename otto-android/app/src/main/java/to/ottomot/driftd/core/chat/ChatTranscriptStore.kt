package to.ottomot.driftd.core.chat

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.DirectMessageDto
import to.ottomot.driftd.ottoUserIdsEqual

/**
 * In-memory squad + DM chat transcripts with per-user disk persistence.
 * Mirrors iOS [ChatStore] transcript responsibilities (not scroll/draft state).
 */
class ChatTranscriptStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var userId: String? = null

    private val squadByCircleId = mutableMapOf<String, List<CircleChatMessageDto>>()
    private val directByConversationId = mutableMapOf<String, List<DirectMessageDto>>()
    private val squadLastFetchedAtMs = mutableMapOf<String, Long>()
    private val directLastFetchedAtMs = mutableMapOf<String, Long>()

    fun bind(userIdRaw: String?) {
        val trimmed = userIdRaw?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == userId) return
        userId = trimmed
        squadByCircleId.clear()
        directByConversationId.clear()
        squadLastFetchedAtMs.clear()
        directLastFetchedAtMs.clear()
        loadFromDisk()
    }

    fun clearAll() {
        squadByCircleId.clear()
        directByConversationId.clear()
        squadLastFetchedAtMs.clear()
        directLastFetchedAtMs.clear()
        val key = prefsKey()
        if (key != null) {
            prefs.edit { remove(key) }
        }
    }

    fun squadMessages(circleId: String): List<CircleChatMessageDto> =
        squadByCircleId[circleId.trim()]?.sortedBy { it.createdAt }.orEmpty()

    fun directMessages(conversationId: String): List<DirectMessageDto> =
        directByConversationId[conversationId.trim()]?.sortedBy { it.createdAt }.orEmpty()

    fun squadLastFetchedAt(circleId: String): Long? = squadLastFetchedAtMs[circleId.trim()]

    fun directLastFetchedAt(conversationId: String): Long? = directLastFetchedAtMs[conversationId.trim()]

    fun shouldRefreshSquadFromNetwork(circleId: String): Boolean =
        shouldRefreshFromNetwork(
            lastFetchedAtMs = squadLastFetchedAtMs[circleId.trim()],
            messagesEmpty = squadMessages(circleId).isEmpty(),
        )

    fun shouldRefreshDirectFromNetwork(conversationId: String): Boolean =
        shouldRefreshFromNetwork(
            lastFetchedAtMs = directLastFetchedAtMs[conversationId.trim()],
            messagesEmpty = directMessages(conversationId).isEmpty(),
        )

    fun upsertSquadMessage(dto: CircleChatMessageDto) {
        val circleId = dto.circleId.trim().takeIf { it.isNotEmpty() } ?: return
        val existing = squadByCircleId[circleId].orEmpty()
        val deleted = !dto.deletedAt.isNullOrBlank()
        val next =
            if (deleted) {
                existing.filterNot { ottoUserIdsEqual(it.id, dto.id) }
            } else {
                val withoutPending =
                    dto.clientMessageId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { clientId ->
                            existing.filterNot {
                                it.id.startsWith("pending-") && it.clientMessageId == clientId
                            }
                        } ?: existing
                withoutPending.filterNot { ottoUserIdsEqual(it.id, dto.id) } + dto
            }
        squadByCircleId[circleId] = next.sortedBy { it.createdAt }
        persistToDisk()
    }

    fun upsertDirectMessage(dto: DirectMessageDto) {
        val conversationId = dto.conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        val normalized = dto.copy(messageType = dto.messageType ?: "user")
        val existing = directByConversationId[conversationId].orEmpty()
        val deleted = !normalized.deletedAt.isNullOrBlank()
        val next =
            if (deleted) {
                existing.filterNot { ottoUserIdsEqual(it.id, normalized.id) }
            } else {
                val withoutPending =
                    normalized.clientMessageId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { clientId ->
                            existing.filterNot {
                                it.id.startsWith("pending-") && it.clientMessageId == clientId
                            }
                        } ?: existing
                withoutPending.filterNot { ottoUserIdsEqual(it.id, normalized.id) } + normalized
            }
        directByConversationId[conversationId] = next.sortedBy { it.createdAt }
        persistToDisk()
    }

    fun replaceSquadMessages(
        circleId: String,
        messages: List<CircleChatMessageDto>,
    ) {
        val cid = circleId.trim().takeIf { it.isNotEmpty() } ?: return
        squadByCircleId[cid] = messages.sortedBy { it.createdAt }
        squadLastFetchedAtMs[cid] = System.currentTimeMillis()
        persistToDisk()
    }

    fun replaceDirectMessages(
        conversationId: String,
        messages: List<DirectMessageDto>,
    ) {
        val cid = conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        directByConversationId[cid] = messages.sortedBy { it.createdAt }
        directLastFetchedAtMs[cid] = System.currentTimeMillis()
        persistToDisk()
    }

    fun reconcileSquadMessages(
        circleId: String,
        fetched: List<CircleChatMessageDto>,
    ) {
        fetched.forEach { upsertSquadMessage(it) }
        val cid = circleId.trim()
        squadLastFetchedAtMs[cid] = System.currentTimeMillis()
        persistToDisk()
    }

    fun reconcileDirectMessages(
        conversationId: String,
        fetched: List<DirectMessageDto>,
    ) {
        fetched.forEach { upsertDirectMessage(it) }
        val cid = conversationId.trim()
        directLastFetchedAtMs[cid] = System.currentTimeMillis()
        persistToDisk()
    }

    private fun shouldRefreshFromNetwork(
        lastFetchedAtMs: Long?,
        messagesEmpty: Boolean,
    ): Boolean {
        if (messagesEmpty) return true
        val last = lastFetchedAtMs ?: return true
        return System.currentTimeMillis() - last > REFRESH_TTL_MS
    }

    private data class DiskSnapshot(
        val squadByCircleId: Map<String, List<CircleChatMessageDto>> = emptyMap(),
        val directByConversationId: Map<String, List<DirectMessageDto>> = emptyMap(),
        val squadLastFetchedAtMs: Map<String, Long> = emptyMap(),
        val directLastFetchedAtMs: Map<String, Long> = emptyMap(),
    )

    private fun prefsKey(): String? {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$PREFS_KEY_PREFIX.$SCHEMA_VERSION.$uid"
    }

    private fun loadFromDisk() {
        val key = prefsKey() ?: return
        val json = prefs.getString(key, null) ?: return
        val type = object : TypeToken<DiskSnapshot>() {}.type
        val decoded =
            runCatching { gson.fromJson<DiskSnapshot>(json, type) }.getOrNull() ?: return
        squadByCircleId.putAll(decoded.squadByCircleId)
        directByConversationId.putAll(decoded.directByConversationId)
        squadLastFetchedAtMs.putAll(decoded.squadLastFetchedAtMs)
        directLastFetchedAtMs.putAll(decoded.directLastFetchedAtMs)
    }

    private fun persistToDisk() {
        val key = prefsKey() ?: return
        val snapshot =
            DiskSnapshot(
                squadByCircleId = squadByCircleId.mapValues { (_, msgs) -> msgs.takeLast(MAX_PERSISTED_MESSAGES) },
                directByConversationId = directByConversationId.mapValues { (_, msgs) -> msgs.takeLast(MAX_PERSISTED_MESSAGES) },
                squadLastFetchedAtMs = squadLastFetchedAtMs.toMap(),
                directLastFetchedAtMs = directLastFetchedAtMs.toMap(),
            )
        prefs.edit { putString(key, gson.toJson(snapshot)) }
    }

    companion object {
        private const val PREFS_NAME = "otto_chat_transcripts"
        private const val PREFS_KEY_PREFIX = "otto.chatTranscripts"
        private const val SCHEMA_VERSION = "v1"
        private const val REFRESH_TTL_MS = 120_000L
        private const val MAX_PERSISTED_MESSAGES = 100
    }
}
