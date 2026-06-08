import Combine
import Foundation

@MainActor
final class ChatUnreadTracker: ObservableObject {
    @Published private(set) var unreadCountByCircleID: [String: Int] = [:]
    @Published private(set) var unreadCountByConversationID: [String: Int] = [:]

    var totalChatUnreadCount: Int {
        unreadCountByCircleID.values.reduce(0, +) + unreadCountByConversationID.values.reduce(0, +)
    }

    private let cursorStore = ChatReadCursorStore()

    init() {}

    nonisolated deinit {}

    private var currentUserID = ""
    private(set) var squadChatTabVisibleCircleID: String?
    private(set) var directThreadVisibleConversationID: String?

    func markRead(thread: ChatReadThreadKey, messageId: String, at readAt: Date) {
        cursorStore.markRead(thread: thread, messageId: messageId, at: readAt)
    }

    func bind(currentUserID rawUserID: String) {
        let trimmed = rawUserID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed != currentUserID else { return }
        currentUserID = trimmed
        cursorStore.bind(userID: trimmed.isEmpty ? nil : trimmed)
    }

    func clearAll() {
        unreadCountByCircleID = [:]
        unreadCountByConversationID = [:]
        cursorStore.clearAll()
    }

    func setSquadChatTabVisible(circleID: String?, isVisible: Bool) {
        let key = circleID?.trimmingCharacters(in: .whitespacesAndNewlines)
        if isVisible, let key, !key.isEmpty {
            squadChatTabVisibleCircleID = key
        } else if !isVisible, squadChatTabVisibleCircleID == key || key == nil {
            squadChatTabVisibleCircleID = nil
        }
    }

    func isSquadChatTabVisible(circleID: String) -> Bool {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return false }
        return squadChatTabVisibleCircleID == key
    }

    func setDirectThreadVisible(conversationID: String?, isVisible: Bool) {
        let key = conversationID?.trimmingCharacters(in: .whitespacesAndNewlines)
        if isVisible, let key, !key.isEmpty {
            directThreadVisibleConversationID = key
        } else if !isVisible, directThreadVisibleConversationID == key || key == nil {
            directThreadVisibleConversationID = nil
        }
    }

    func unreadCount(forCircleID circleID: String) -> Int {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        return unreadCountByCircleID[key] ?? 0
    }

    func unreadCount(forConversationID conversationID: String) -> Int {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        return unreadCountByConversationID[key] ?? 0
    }

    func markSquadReadIfChatTabVisible(circleID: String, messages: [CircleChatMessageDTO]) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, squadChatTabVisibleCircleID == key else { return }
        guard let newest = messages.last else {
            unreadCountByCircleID.removeValue(forKey: key)
            return
        }
        cursorStore.markRead(thread: .squad(key), messageId: newest.id, at: newest.createdAt)
        unreadCountByCircleID.removeValue(forKey: key)
    }

    func markDirectReadIfThreadVisible(conversationID: String, messages: [DirectMessageDTO]) {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, directThreadVisibleConversationID == key else { return }
        guard let newest = messages.last else {
            unreadCountByConversationID.removeValue(forKey: key)
            return
        }
        cursorStore.markRead(thread: .direct(key), messageId: newest.id, at: newest.createdAt)
        unreadCountByConversationID.removeValue(forKey: key)
    }

    func recomputeSquad(circleID: String, messages: [CircleChatMessageDTO]) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let cursor = cursorStore.cursor(for: .squad(key))
        let count = ChatUnreadCounting.squadUnreadCount(
            messages: messages,
            cursor: cursor,
            currentUserID: currentUserID,
            trackingStartedAt: cursorStore.trackingStartedAt
        )
        if count > 0 {
            unreadCountByCircleID[key] = count
        } else {
            unreadCountByCircleID.removeValue(forKey: key)
        }
    }

    func recomputeDirect(conversationID: String, messages: [DirectMessageDTO]) {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let cursor = cursorStore.cursor(for: .direct(key))
        let count = ChatUnreadCounting.directUnreadCount(
            messages: messages,
            cursor: cursor,
            currentUserID: currentUserID,
            trackingStartedAt: cursorStore.trackingStartedAt
        )
        if count > 0 {
            unreadCountByConversationID[key] = count
        } else {
            unreadCountByConversationID.removeValue(forKey: key)
        }
    }

    func recomputeDirectFromPreview(
        conversationID: String,
        lastMessageAt: Date?,
        lastMessageSenderUserID: String?
    ) {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let cursor = cursorStore.cursor(for: .direct(key))
        let count = ChatUnreadCounting.previewUnreadEstimate(
            lastMessageAt: lastMessageAt,
            lastMessageSenderUserID: lastMessageSenderUserID,
            cursor: cursor,
            currentUserID: currentUserID,
            trackingStartedAt: cursorStore.trackingStartedAt
        )
        if count > 0 {
            unreadCountByConversationID[key] = count
        } else {
            unreadCountByConversationID.removeValue(forKey: key)
        }
    }

    func recomputeAll(
        squadMessagesByCircleID: [String: [CircleChatMessageDTO]],
        directMessagesByConversationID: [String: [DirectMessageDTO]],
        directConversations: [DirectConversationDTO]
    ) {
        let trackingStartedAt = cursorStore.trackingStartedAt
        var squads: [String: Int] = [:]
        for (circleID, messages) in squadMessagesByCircleID {
            let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty else { continue }
            let count = ChatUnreadCounting.squadUnreadCount(
                messages: messages,
                cursor: cursorStore.cursor(for: .squad(key)),
                currentUserID: currentUserID,
                trackingStartedAt: trackingStartedAt
            )
            if count > 0 { squads[key] = count }
        }

        var directs: [String: Int] = [:]
        for conversation in directConversations {
            let conversationID = conversation.id.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !conversationID.isEmpty else { continue }
            let cached = directMessagesByConversationID[conversationID] ?? []
            if cached.isEmpty {
                let previewCount = ChatUnreadCounting.previewUnreadEstimate(
                    lastMessageAt: conversation.lastMessageAt,
                    lastMessageSenderUserID: conversation.lastMessage?.senderUserId,
                    cursor: cursorStore.cursor(for: .direct(conversationID)),
                    currentUserID: currentUserID,
                    trackingStartedAt: trackingStartedAt
                )
                if previewCount > 0 { directs[conversationID] = previewCount }
            } else {
                let count = ChatUnreadCounting.directUnreadCount(
                    messages: cached,
                    cursor: cursorStore.cursor(for: .direct(conversationID)),
                    currentUserID: currentUserID,
                    trackingStartedAt: trackingStartedAt
                )
                if count > 0 { directs[conversationID] = count }
            }
        }

        unreadCountByCircleID = squads
        unreadCountByConversationID = directs
    }

    func maybeMarkSquadReadIfEligible(
        circleID: String,
        messages: [CircleChatMessageDTO],
        isPinnedToBottom: Bool = false,
        lastReadMessageId: String? = nil
    ) {
        markSquadReadIfChatTabVisible(circleID: circleID, messages: messages)
    }

    func maybeMarkDirectReadIfEligible(
        conversationID: String,
        messages: [DirectMessageDTO],
        isPinnedToBottom: Bool = false,
        lastReadMessageId: String? = nil
    ) {
        markDirectReadIfThreadVisible(conversationID: conversationID, messages: messages)
    }

    func handleSquadMessageUpsert(
        circleID: String,
        messages: [CircleChatMessageDTO],
        isPinnedToBottom: Bool = false,
        lastReadMessageId: String? = nil
    ) {
        maybeMarkSquadReadIfEligible(circleID: circleID, messages: messages)
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        if squadChatTabVisibleCircleID == key { return }
        recomputeSquad(circleID: circleID, messages: messages)
    }

    func handleDirectMessageUpsert(
        conversationID: String,
        messages: [DirectMessageDTO],
        isPinnedToBottom: Bool = false,
        lastReadMessageId: String? = nil
    ) {
        maybeMarkDirectReadIfEligible(conversationID: conversationID, messages: messages)
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        if directThreadVisibleConversationID == key { return }
        recomputeDirect(conversationID: conversationID, messages: messages)
    }
}
