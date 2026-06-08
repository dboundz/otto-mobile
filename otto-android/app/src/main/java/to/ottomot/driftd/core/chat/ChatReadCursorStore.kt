package to.ottomot.driftd.core.chat

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

enum class ChatReadThreadKind {
    Squad,
    Direct,
}

data class ChatReadThreadKey(
    val kind: ChatReadThreadKind,
    val id: String,
) {
    val storageKey: String
        get() =
            when (kind) {
                ChatReadThreadKind.Squad -> "squad:$id"
                ChatReadThreadKind.Direct -> "direct:$id"
            }
}

data class ChatReadCursor(
    val lastReadMessageId: String,
    /** Epoch milliseconds for Gson persistence. */
    val lastReadAtEpochMs: Long,
) {
    val lastReadAt: Instant
        get() = Instant.ofEpochMilli(lastReadAtEpochMs)
}

class ChatReadCursorStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var userId: String? = null
    private var cursors: MutableMap<String, ChatReadCursor> = mutableMapOf()
    var trackingStartedAt: Instant? = null
        private set

    fun bind(userIdRaw: String?) {
        val trimmed = userIdRaw?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == userId) return
        userId = trimmed
        loadFromDisk()
        ensureTrackingEpochIfNeeded()
    }

    fun cursor(thread: ChatReadThreadKey): ChatReadCursor? = cursors[thread.storageKey]

    fun markRead(thread: ChatReadThreadKey, messageId: String, readAt: Instant) {
        val messageID = messageId.trim().takeIf { it.isNotEmpty() } ?: return
        val key = thread.storageKey
        val existing = cursors[key]
        if (existing != null) {
            if (existing.lastReadAt.isAfter(readAt)) return
            if (existing.lastReadAt == readAt && existing.lastReadMessageId >= messageID) return
        }
        cursors[key] = ChatReadCursor(lastReadMessageId = messageID, lastReadAtEpochMs = readAt.toEpochMilli())
        persistToDisk()
    }

    fun clearAll() {
        cursors.clear()
        persistToDisk()
    }

    private fun prefsKey(): String? {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$PREFS_KEY_PREFIX.$SCHEMA_VERSION.$uid"
    }

    private fun loadFromDisk() {
        cursors.clear()
        trackingStartedAt = null
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        trackingEpochPrefsKey(uid)?.let { epochKey ->
            val epochMs = prefs.getLong(epochKey, Long.MIN_VALUE)
            if (epochMs != Long.MIN_VALUE) {
                trackingStartedAt = Instant.ofEpochMilli(epochMs)
            }
        }
        val key = prefsKey() ?: return
        val json = prefs.getString(key, null) ?: return
        val type = object : TypeToken<Map<String, ChatReadCursor>>() {}.type
        val decoded: Map<String, ChatReadCursor>? = runCatching { gson.fromJson<Map<String, ChatReadCursor>>(json, type) }.getOrNull()
        if (decoded != null) {
            cursors.putAll(decoded)
        }
    }

    private fun persistToDisk() {
        val key = prefsKey() ?: return
        val json = gson.toJson(cursors)
        prefs.edit { putString(key, json) }
    }

    private fun trackingEpochPrefsKey(uid: String): String? =
        "$TRACKING_EPOCH_KEY_PREFIX.$TRACKING_EPOCH_SCHEMA_VERSION.$uid"

    private fun ensureTrackingEpochIfNeeded() {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            trackingStartedAt = null
            return
        }
        if (trackingStartedAt != null) return
        val now = Instant.now()
        trackingStartedAt = now
        trackingEpochPrefsKey(uid)?.let { key ->
            prefs.edit { putLong(key, now.toEpochMilli()) }
        }
    }

    companion object {
        private const val PREFS_NAME = "otto_chat_read_cursors"
        private const val PREFS_KEY_PREFIX = "otto.chatReadCursors"
        private const val SCHEMA_VERSION = "v1"
        private const val TRACKING_EPOCH_KEY_PREFIX = "otto.chatUnreadTrackingStartedAt"
        private const val TRACKING_EPOCH_SCHEMA_VERSION = "v1"
    }
}
