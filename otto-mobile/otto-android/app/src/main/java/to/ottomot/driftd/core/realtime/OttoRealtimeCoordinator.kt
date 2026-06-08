package to.ottomot.driftd.core.realtime

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Otto realtime websocket (`/ws`): authenticates via `Authorization` (same okhttp client as Retrofit).
 * Subscribes to squad circle chat + presence channels; parses push frames for merging into VM state.
 */
class OttoRealtimeCoordinator internal constructor(
    private val client: OkHttpClient,
    wsRootUrl: String,
    parentScope: CoroutineScope,
) {
    sealed interface Incoming {
        data class CircleChatNew(val message: JsonObject) : Incoming

        data class CircleChatUpdated(val message: JsonObject) : Incoming

        data class PresenceUpdated(val presence: JsonObject) : Incoming

        data class DirectChatNew(val message: JsonObject) : Incoming

        data class DirectChatUpdated(val message: JsonObject) : Incoming

        data class UserProfileUpdated(val profile: JsonObject) : Incoming

        data class CircleMembersUpdated(val payload: JsonObject) : Incoming

        data class ProfileProgressionLevelUp(val levelUp: JsonObject) : Incoming

        data object Connected : Incoming

        data object Disconnected : Incoming
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + Dispatchers.IO)

    private val wsUrl: String =
        wsRootUrl
            .trim()
            .removeSuffix("/")
            .let { base ->
                if (base.endsWith("/ws")) base else "$base/ws"
            }

    private val socketLock = Any()
    private var webSocket: WebSocket? = null
    private var authTokenSnapshot: String? = null

    private val desiredChatIds = mutableSetOf<String>()
    private val desiredPresenceIds = mutableSetOf<String>()
    private val desiredDirectConversationIds = mutableSetOf<String>()
    private var activeChatSubs = mutableSetOf<String>()
    private var activePresenceSubs = mutableSetOf<String>()
    private var activeDirectSubs = mutableSetOf<String>()
    private val reconnectJob = AtomicReference<Job?>(null)

    var onIncoming: suspend (Incoming) -> Unit = {}

    /** HTTP 401 on WS handshake: clear session from here (same client as Retrofit). */
    var onUnauthorized: () -> Unit = {}

    companion object {
        const val OttoPublicPresenceCircleId: String = "public"
    }

    /** Connect or refresh token-bound socket when auth changes. Pass null on sign-out. */
    fun ensureConnected(authToken: String?) {
        val trimmed = authToken?.trim()?.takeIf { it.isNotEmpty() }
        synchronized(socketLock) {
            if (trimmed == null) {
                authTokenSnapshot = null
                closeSocketQuietly("logout")
                return
            }
            if (webSocket != null && authTokenSnapshot == trimmed) {
                return
            }
            authTokenSnapshot = trimmed
            openSocketLocked()
        }
    }

    fun shutdown() {
        reconnectJob.getAndSet(null)?.cancel()
        synchronized(socketLock) {
            authTokenSnapshot = null
            desiredChatIds.clear()
            desiredPresenceIds.clear()
            desiredDirectConversationIds.clear()
            activeChatSubs.clear()
            activePresenceSubs.clear()
            activeDirectSubs.clear()
            closeSocketQuietly("shutdown")
        }
    }

    /**
     * @param circleIds squads whose chat + presence realtime we follow (dedup server-side duplicates).
     * @param subscribePublicPresence when true subscribe `presence` for [OttoPublicPresenceCircleId].
     * @param directConversationIds DM threads to issue `direct.subscribe` for client-side merges.
     */
    fun syncCircleTargets(
        circleIds: List<String>,
        subscribePublicPresence: Boolean,
        directConversationIds: List<String> = emptyList(),
    ) {
        val chat = circleIds.filter { it.isNotBlank() }.distinct().toSet()

        synchronized(socketLock) {
            desiredChatIds.clear()
            desiredChatIds.addAll(chat)

            desiredPresenceIds.clear()
            desiredPresenceIds.addAll(chat)
            if (subscribePublicPresence) {
                desiredPresenceIds.add(OttoPublicPresenceCircleId)
            }

            desiredDirectConversationIds.clear()
            desiredDirectConversationIds.addAll(directConversationIds.filter { it.isNotBlank() }.distinct())

            applySubscriptionDiffLocked()
        }

        val token = synchronized(socketLock) { authTokenSnapshot }
        if (!token.isNullOrEmpty()) {
            ensureConnected(token)
        }
    }

    private fun applySubscriptionDiffLocked() {
        val ws = webSocket ?: return

        fun sendJson(map: LinkedHashMap<String, String>) {
            val payload =
                map.entries.joinToString(",") { (k, v) ->
                    "\"$k\":\"${v.replace("\"", "\\\"")}\""
                }
            ws.send("{$payload}")
        }

        val deadChat = activeChatSubs - desiredChatIds
        val addChat = desiredChatIds - activeChatSubs
        deadChat.forEach { cid ->
            sendJson(
                linkedMapOf(
                    "type" to "circle.chat.unsubscribe",
                    "circleId" to cid,
                    "requestId" to "cu-$cid-${UUID.randomUUID()}",
                ),
            )
            activeChatSubs.remove(cid)
        }
        addChat.forEach { cid ->
            sendJson(
                linkedMapOf(
                    "type" to "circle.chat.subscribe",
                    "circleId" to cid,
                    "requestId" to "cs-$cid-${UUID.randomUUID()}",
                ),
            )
            activeChatSubs.add(cid)
        }

        val deadPres = activePresenceSubs - desiredPresenceIds
        val addPres = desiredPresenceIds - activePresenceSubs
        deadPres.forEach { cid ->
            sendJson(
                linkedMapOf(
                    "type" to "presence.unsubscribe",
                    "circleId" to cid,
                    "requestId" to "pu-$cid-${UUID.randomUUID()}",
                ),
            )
            activePresenceSubs.remove(cid)
        }
        addPres.forEach { cid ->
            sendJson(
                linkedMapOf(
                    "type" to "presence.subscribe",
                    "circleId" to cid,
                    "requestId" to "ps-$cid-${UUID.randomUUID()}",
                ),
            )
            activePresenceSubs.add(cid)
        }

        val deadDir = activeDirectSubs - desiredDirectConversationIds
        val addDir = desiredDirectConversationIds - activeDirectSubs
        deadDir.forEach { convId ->
            sendJson(
                linkedMapOf(
                    "type" to "direct.unsubscribe",
                    "conversationId" to convId,
                    "requestId" to "du-$convId-${UUID.randomUUID()}",
                ),
            )
            activeDirectSubs.remove(convId)
        }
        addDir.forEach { convId ->
            sendJson(
                linkedMapOf(
                    "type" to "direct.subscribe",
                    "conversationId" to convId,
                    "requestId" to "ds-$convId-${UUID.randomUUID()}",
                ),
            )
            activeDirectSubs.add(convId)
        }
    }

    private fun openSocketLocked() {
        closeSocketQuietly("reopen")
        val token = authTokenSnapshot ?: return
        val request =
            Request
                .Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

        activeChatSubs.clear()
        activePresenceSubs.clear()
        activeDirectSubs.clear()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        scope.launch {
                            onIncoming(Incoming.Connected)
                            synchronized(socketLock) {
                                activeChatSubs.clear()
                                activePresenceSubs.clear()
                                activeDirectSubs.clear()
                                applySubscriptionDiffLocked()
                            }
                        }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        dispatch(text)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        dispatch(bytes.utf8())
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        val unauthorized = response?.code == 401
                        synchronized(socketLock) {
                            if (this@OttoRealtimeCoordinator.webSocket === webSocket) {
                                this@OttoRealtimeCoordinator.webSocket = null
                                activeChatSubs.clear()
                                activePresenceSubs.clear()
                                activeDirectSubs.clear()
                            }
                            if (unauthorized) {
                                authTokenSnapshot = null
                                reconnectJob.getAndSet(null)?.cancel()
                            }
                        }
                        if (unauthorized) {
                            onUnauthorized()
                        } else {
                            scheduleReconnect()
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        scope.launch { onIncoming(Incoming.Disconnected) }
                        synchronized(socketLock) {
                            if (this@OttoRealtimeCoordinator.webSocket === webSocket) {
                                this@OttoRealtimeCoordinator.webSocket = null
                                activeChatSubs.clear()
                                activePresenceSubs.clear()
                                activeDirectSubs.clear()
                            }
                        }
                        scheduleReconnect()
                    }
                },
            )
    }

    private fun dispatch(text: String) {
        val obj =
            runCatching {
                JsonParser.parseString(text).asJsonObject
            }.getOrNull() ?: return
        val type = obj["type"]?.takeIf { it.isJsonPrimitive }?.asString ?: return
        when (type) {
            "circle.chat.message", "circle.chat.sent" -> {
                obj["message"]?.takeIf { it.isJsonObject }?.let { raw ->
                    val messageObj = raw.asJsonObject
                    scope.launch { onIncoming(Incoming.CircleChatNew(messageObj)) }
                }
            }
            "circle.chat.updated" -> {
                obj["message"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.CircleChatUpdated(raw.asJsonObject)) }
                }
            }
            "presence.updated" -> {
                obj["presence"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.PresenceUpdated(raw.asJsonObject)) }
                }
            }
            "direct.message" -> {
                obj["message"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.DirectChatNew(raw.asJsonObject)) }
                }
            }
            "direct.updated" -> {
                obj["message"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.DirectChatUpdated(raw.asJsonObject)) }
                }
            }
            "user.profile.updated" -> {
                obj["profile"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.UserProfileUpdated(raw.asJsonObject)) }
                }
            }
            "circle.members.updated" -> {
                scope.launch { onIncoming(Incoming.CircleMembersUpdated(obj)) }
            }
            "profile.progression.level_up" -> {
                obj["levelUp"]?.takeIf { it.isJsonObject }?.let { raw ->
                    scope.launch { onIncoming(Incoming.ProfileProgressionLevelUp(raw.asJsonObject)) }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val tokenBefore = synchronized(socketLock) { authTokenSnapshot }
        if (tokenBefore.isNullOrEmpty()) return

        reconnectJob.updateAndGet { previous ->
            previous?.cancel()
            scope.launch {
                delay(4_500)
                synchronized(socketLock) {
                    val tokenAfter = authTokenSnapshot
                    if (!tokenAfter.isNullOrEmpty() && tokenBefore == tokenAfter) {
                        openSocketLocked()
                    }
                }
            }
        }
    }

    private fun closeSocketQuietly(reason: String) {
        reconnectJob.getAndSet(null)?.cancel()
        webSocket?.close(1000, reason.take(80))
        webSocket = null
        activeChatSubs.clear()
        activePresenceSubs.clear()
        activeDirectSubs.clear()
    }
}
