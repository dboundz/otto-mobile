import Foundation

enum ChatReadThreadKey: Hashable, Codable {
    case squad(String)
    case direct(String)

    var storageKey: String {
        switch self {
        case .squad(let id):
            return "squad:\(id)"
        case .direct(let id):
            return "direct:\(id)"
        }
    }
}

struct ChatReadCursor: Codable, Equatable {
    var lastReadMessageId: String
    var lastReadAt: Date
}

final class ChatReadCursorStore {
    private static let schemaVersion = "v1"
    private static let trackingEpochSchemaVersion = "v1"
    private var userID: String?
    private var cursors: [String: ChatReadCursor] = [:]
    private(set) var trackingStartedAt: Date?

    func bind(userID rawUserID: String?) {
        let trimmed = rawUserID?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard trimmed != userID else { return }
        userID = trimmed.isEmpty ? nil : trimmed
        loadFromDisk()
        ensureTrackingEpochIfNeeded()
    }

    func cursor(for thread: ChatReadThreadKey) -> ChatReadCursor? {
        cursors[thread.storageKey]
    }

    func markRead(thread: ChatReadThreadKey, messageId: String, at readAt: Date) {
        let messageID = messageId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !messageID.isEmpty else { return }
        let key = thread.storageKey
        if let existing = cursors[key],
           existing.lastReadAt > readAt
           || (existing.lastReadAt == readAt && existing.lastReadMessageId >= messageID) {
            return
        }
        cursors[key] = ChatReadCursor(lastReadMessageId: messageID, lastReadAt: readAt)
        persistToDisk()
    }

    func clearAll() {
        cursors = [:]
        persistToDisk()
    }

    private func defaultsKey() -> String? {
        guard let userID, !userID.isEmpty else { return nil }
        return "otto.chatReadCursors.\(Self.schemaVersion).\(userID)"
    }

    private func loadFromDisk() {
        cursors = [:]
        trackingStartedAt = nil
        guard let userID, !userID.isEmpty else { return }
        if let epochKey = trackingEpochDefaultsKey(),
           let epoch = UserDefaults.standard.object(forKey: epochKey) as? Date {
            trackingStartedAt = epoch
        }
        guard let key = defaultsKey(),
              let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([String: ChatReadCursor].self, from: data) else {
            return
        }
        cursors = decoded
    }

    private func persistToDisk() {
        guard let key = defaultsKey(),
              let data = try? JSONEncoder().encode(cursors) else {
            return
        }
        UserDefaults.standard.set(data, forKey: key)
    }

    private func trackingEpochDefaultsKey() -> String? {
        guard let userID, !userID.isEmpty else { return nil }
        return "otto.chatUnreadTrackingStartedAt.\(Self.trackingEpochSchemaVersion).\(userID)"
    }

    private func ensureTrackingEpochIfNeeded() {
        guard let userID, !userID.isEmpty else {
            trackingStartedAt = nil
            return
        }
        if trackingStartedAt != nil { return }
        let now = Date()
        trackingStartedAt = now
        if let key = trackingEpochDefaultsKey() {
            UserDefaults.standard.set(now, forKey: key)
        }
    }
}

enum ChatUnreadCounting {
    static func isMessageUnread(
        messageID: String,
        createdAt: Date,
        senderUserID: String,
        isDeleted: Bool,
        cursor: ChatReadCursor?,
        currentUserID: String,
        trackingStartedAt: Date?
    ) -> Bool {
        guard !isDeleted else { return false }
        guard senderUserID != currentUserID else { return false }
        if let trackingStartedAt, createdAt <= trackingStartedAt { return false }
        guard let cursor else { return true }
        if createdAt > cursor.lastReadAt { return true }
        if createdAt < cursor.lastReadAt { return false }
        return messageID > cursor.lastReadMessageId
    }

    static func squadUnreadCount(
        messages: [CircleChatMessageDTO],
        cursor: ChatReadCursor?,
        currentUserID: String,
        trackingStartedAt: Date?
    ) -> Int {
        messages.reduce(into: 0) { count, message in
            if isMessageUnread(
                messageID: message.id,
                createdAt: message.createdAt,
                senderUserID: message.resolvedSenderUserId,
                isDeleted: message.deletedAt != nil,
                cursor: cursor,
                currentUserID: currentUserID,
                trackingStartedAt: trackingStartedAt
            ) {
                count += 1
            }
        }
    }

    static func directUnreadCount(
        messages: [DirectMessageDTO],
        cursor: ChatReadCursor?,
        currentUserID: String,
        trackingStartedAt: Date?
    ) -> Int {
        messages.reduce(into: 0) { count, message in
            if isMessageUnread(
                messageID: message.id,
                createdAt: message.createdAt,
                senderUserID: message.senderUserId,
                isDeleted: message.deletedAt != nil,
                cursor: cursor,
                currentUserID: currentUserID,
                trackingStartedAt: trackingStartedAt
            ) {
                count += 1
            }
        }
    }

    /// Minimum unread estimate from inbox preview when transcript cache is empty.
    static func previewUnreadEstimate(
        lastMessageAt: Date?,
        lastMessageSenderUserID: String?,
        cursor: ChatReadCursor?,
        currentUserID: String,
        trackingStartedAt: Date?
    ) -> Int {
        guard let lastMessageAt,
              let sender = lastMessageSenderUserID?.trimmingCharacters(in: .whitespacesAndNewlines),
              !sender.isEmpty,
              sender != currentUserID else {
            return 0
        }
        if let trackingStartedAt, lastMessageAt <= trackingStartedAt { return 0 }
        guard let cursor else { return 1 }
        if lastMessageAt > cursor.lastReadAt { return 1 }
        if lastMessageAt < cursor.lastReadAt { return 0 }
        return 1
    }
}
