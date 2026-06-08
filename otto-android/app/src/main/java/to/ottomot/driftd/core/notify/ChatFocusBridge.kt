package to.ottomot.driftd.core.notify

/** Mirrors iOS `PushFocusBridge` for foreground push / FCM throttle decisions. */
object ChatFocusBridge {
    @Volatile
    var activeChatCircleId: String? = null

    @Volatile
    var activeDirectConversationId: String? = null
}
