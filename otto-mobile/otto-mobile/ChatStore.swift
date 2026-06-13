import Combine
import Foundation
import os
import SwiftUI
import UIKit

enum ChatConversationID: Hashable, Codable {
    case squad(String)
    case direct(String)
}

struct ChatReplyDraft: Codable, Equatable {
    var messageId: String?
    var authorName: String?
    var snippet: String?
    var avatarURL: String?

    var isEmpty: Bool { messageId?.isEmpty != false }

    static let empty = ChatReplyDraft(messageId: nil, authorName: nil, snippet: nil, avatarURL: nil)
}

enum ScrollIntent: Equatable {
    case none
    case scrollToBottom(animated: Bool)
    case restore(anchorMessageId: String, anchor: UnitPoint)
    case scrollToMessage(messageId: String, animated: Bool)
}

enum ChatScrollIntentSource: String, Equatable {
    case appear
    case refresh
    case loadConversation
    case userAction
    case pagination
    case newMessage
}

enum ChatScrollAppearDecisionKind: Equatable {
    case noReposition
    case reposition
}

enum SquadChatFetchPolicy {
    /// Background revalidate interval for squad transcripts already in cache.
    static let refreshTTL: TimeInterval = 120
    /// Page size for squad chat network fetch / older-message pagination.
    static let networkPageSize = 50

    static func shouldRefreshFromNetwork(
        lastFetchedAt: Date?,
        messagesEmpty: Bool,
        messageCount: Int = 0,
        lastNetworkFetchCount: Int? = nil,
        transcriptStartsWithMemberJoinedSystemMessage: Bool = false,
        now: Date = Date()
    ) -> Bool {
        if messagesEmpty { return true }
        if let lastFetch = lastNetworkFetchCount, messageCount < lastFetch { return true }
        if messageCount < networkPageSize,
           lastNetworkFetchCount == nil,
           transcriptStartsWithMemberJoinedSystemMessage {
            return true
        }
        guard let lastFetchedAt else { return true }
        return now.timeIntervalSince(lastFetchedAt) > refreshTTL
    }
}

struct ConversationScrollState: Equatable {
    /// Durable conversation state lives here, but mounted scroll geometry does not become trusted
    /// until the current UIScrollView proves its bottom position after layout settles.
    var isPinnedToBottom = true
    var hasUserScrollAnchor = false
    var lastVisibleMessageId: String?
    var lastReadMessageId: String?
    var pendingScrollIntent: ScrollIntent?
    var pendingScrollIntentSource: ChatScrollIntentSource?
    var didInitialScrollToBottom = false
    var isSettlingScrollPosition = false
    var programmaticScrollInProgress = false
    var lastKnownMessageCount = 0
    var lastKnownNewestMessageId: String?
    var intentRevision = 0
    var appearToken = 0
    var intentAppearToken: Int?
    var lastAppearDecision: ChatScrollAppearDecisionKind?
    var ownerConversationId: String?
    var scrollViewInstanceId: UUID?
    var mountedConversationId: String?
    var isMountedScrollGeometryVerified = false

    static let initial = ConversationScrollState()
}

@MainActor
final class SquadChatConversationState: ObservableObject {
    let circleID: String

    @Published var messages: [CircleChatMessageDTO]
    @Published var isLoadingMessages = false
    @Published var isLoadingOlderMessages = false
    @Published var hasMoreOlderMessages = true
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published var isChatAtBottom = true
    @Published var scrollRevision = 0
    @Published var scrollToMessageId: String?
    @Published var scrollToMessageRevision = 0
    @Published var shouldScrollOnNextChange = false
    @Published var shouldAnimateNextScroll = true
    @Published var draft = ""
    @Published var replyDraft = ChatReplyDraft.empty
    @Published var isSendingMessage = false
    @Published var pendingAttachments: [ChatPendingComposerAttachment] = []
    @Published var scrollState = ConversationScrollState.initial
    @Published var editingMessageId: String?
    var lastFetchedAt: Date?
    var hasLoadedOnce = false
    /// Count returned by the most recent successful head fetch (before any visible-window trim).
    var lastNetworkFetchCount: Int?

    init(circleID: String, messages: [CircleChatMessageDTO] = []) {
        self.circleID = circleID
        self.messages = messages.sorted { $0.createdAt < $1.createdAt }
    }
}

@MainActor
final class DirectChatConversationState: ObservableObject {
    let recipientUserID: String
    var recipientName: String
    var recipientAvatarURL: String?
    var recipientMapAccentKey: String?

    @Published var conversation: DirectConversationDTO?
    @Published var messages: [DirectMessageDTO]
    @Published var isLoading = true
    @Published var isLoadingOlderMessages = false
    @Published var hasMoreOlderMessages = true
    @Published var errorMessage: String?
    @Published var isChatAtBottom = true
    @Published var scrollRevision = 0
    @Published var scrollToMessageId: String?
    @Published var scrollToMessageRevision = 0
    @Published var shouldScrollOnNextChange = false
    @Published var shouldAnimateNextScroll = true
    @Published var draft = ""
    @Published var replyDraft = ChatReplyDraft.empty
    @Published var isSending = false
    @Published var pendingAttachments: [ChatPendingComposerAttachment] = []
    @Published var scrollState = ConversationScrollState.initial
    @Published var editingMessageId: String?

    init(
        recipientUserID: String,
        recipientName: String,
        recipientAvatarURL: String?,
        recipientMapAccentKey: String? = nil,
        conversation: DirectConversationDTO? = nil,
        messages: [DirectMessageDTO] = []
    ) {
        self.recipientUserID = recipientUserID
        self.recipientName = recipientName
        self.recipientAvatarURL = recipientAvatarURL
        self.recipientMapAccentKey = recipientMapAccentKey
        self.conversation = conversation
        self.messages = messages.sorted { $0.createdAt < $1.createdAt }
        self.isLoading = messages.isEmpty
    }
}

@MainActor
final class ChatStore: ObservableObject {
    @Published private(set) var latestCircleChatMessage: CircleChatMessageDTO?
    @Published private(set) var latestDirectMessage: DirectMessageDTO?
    @Published private(set) var activeConversationID: ChatConversationID?

    let unreadTracker = ChatUnreadTracker()

    var squadUnreadCountsByCircleID: [String: Int] { unreadTracker.unreadCountByCircleID }
    var unreadDirectMessageCountsByConversationID: [String: Int] { unreadTracker.unreadCountByConversationID }
    var totalChatUnreadCount: Int { unreadTracker.totalChatUnreadCount }

    private var squadStatesByCircleID: [String: SquadChatConversationState] = [:]
    private var directStatesByConversationID: [String: DirectChatConversationState] = [:]
    private var directStatesByUserID: [String: DirectChatConversationState] = [:]
    private var directConversationsByUserID: [String: DirectConversationDTO] = [:]
    private var previousActiveConversationID: ChatConversationID?
    private var cancellables: Set<AnyCancellable> = []
    private var isCacheLoaded = false
    private var persistTask: Task<Void, Never>?
    private let database = ChatDatabase.shared

    private static let maxCachedMessagesPerConversation = 500
    private static let initialHydrationLimit = 100
    private static let cacheFileName = "otto-chat-store-cache-v1.json"

    init() {
        unreadTracker.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    deinit {
        persistTask?.cancel()
        cancellables.removeAll()
    }

    func bindUnreadTracking(currentUserID: String) {
        unreadTracker.bind(currentUserID: currentUserID)
    }

    func setSquadChatTabVisible(circleID: String?, isVisible: Bool) {
        unreadTracker.setSquadChatTabVisible(circleID: circleID, isVisible: isVisible)
    }

    func setDirectThreadVisible(conversationID: String?, isVisible: Bool) {
        unreadTracker.setDirectThreadVisible(conversationID: conversationID, isVisible: isVisible)
    }

    func markSquadReadIfChatTabVisible(circleID: String) {
        let state = squadState(circleID: circleID)
        unreadTracker.markSquadReadIfChatTabVisible(circleID: circleID, messages: state.messages)
    }

    func refreshSquadUnread(circleID: String) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        unreadTracker.recomputeSquad(circleID: key, messages: squadState(circleID: key).messages)
    }

    var visibleSquadChatTabCircleID: String? { unreadTracker.squadChatTabVisibleCircleID }

    func markDirectReadIfThreadVisible(conversationID: String) {
        let state = directState(conversationID: conversationID)
        unreadTracker.markDirectReadIfThreadVisible(conversationID: conversationID, messages: state.messages)
    }

    func reconcileUnreadState(currentUserID: String) {
        bindUnreadTracking(currentUserID: currentUserID)
        loadDiskCacheIfNeeded()
        unreadTracker.recomputeAll(
            squadMessagesByCircleID: squadStatesByCircleID.mapValues(\.messages),
            directMessagesByConversationID: directStatesByConversationID.mapValues(\.messages),
            directConversations: Array(directConversationsByUserID.values)
        )
    }

    func recomputeDirectUnreadFromPreview(_ conversation: DirectConversationDTO) {
        unreadTracker.recomputeDirectFromPreview(
            conversationID: conversation.id,
            lastMessageAt: conversation.lastMessageAt,
            lastMessageSenderUserID: conversation.lastMessage?.senderUserId
        )
    }

    func squadState(circleID rawCircleID: String) -> SquadChatConversationState {
        loadDiskCacheIfNeeded()
        let circleID = rawCircleID.trimmingCharacters(in: .whitespacesAndNewlines)
        if let state = squadStatesByCircleID[circleID] {
            return state
        }
        let messages = database.recentSquadMessages(circleID: circleID, limit: Self.initialHydrationLimit)
        let state = SquadChatConversationState(circleID: circleID, messages: messages)
        state.hasMoreOlderMessages = database.metadata(kind: .squad, conversationID: circleID)?.hasMoreOlderMessages ?? (messages.count >= Self.initialHydrationLimit)
        observe(state)
        squadStatesByCircleID[circleID] = state
        return state
    }

    func directState(
        recipientUserID rawUserID: String,
        recipientName: String,
        recipientAvatarURL: String?,
        recipientMapAccentKey: String? = nil
    ) -> DirectChatConversationState {
        loadDiskCacheIfNeeded()
        let userID = rawUserID.trimmingCharacters(in: .whitespacesAndNewlines)
        if let state = directStatesByUserID[userID] {
            state.recipientName = recipientName
            state.recipientAvatarURL = recipientAvatarURL
            state.recipientMapAccentKey = recipientMapAccentKey
            return state
        }
        let conversation = directConversationsByUserID[userID]
        let existingMessages: [DirectMessageDTO] = conversation.flatMap { existingConversation -> [DirectMessageDTO]? in
            let cached = directStatesByConversationID[existingConversation.id]?.messages
            if let cached, !cached.isEmpty {
                return cached
            }
            return database.recentDirectMessages(
                conversationID: existingConversation.id,
                limit: Self.initialHydrationLimit
            )
        } ?? []
        let state = DirectChatConversationState(
            recipientUserID: userID,
            recipientName: recipientName,
            recipientAvatarURL: recipientAvatarURL,
            recipientMapAccentKey: recipientMapAccentKey,
            conversation: conversation,
            messages: existingMessages
        )
        observe(state)
        directStatesByUserID[userID] = state
        if let conversation {
            directStatesByConversationID[conversation.id] = state
            state.hasMoreOlderMessages = database.metadata(kind: .direct, conversationID: conversation.id)?.hasMoreOlderMessages ?? (existingMessages.count >= Self.initialHydrationLimit)
        }
        return state
    }

    func cachedSquadMessages(circleID: String) -> [CircleChatMessageDTO]? {
        loadDiskCacheIfNeeded()
        let messages = squadStatesByCircleID[circleID]?.messages
        if messages?.isEmpty == false { return messages }
        let cached = database.recentSquadMessages(circleID: circleID, limit: Self.initialHydrationLimit)
        return cached.isEmpty ? nil : cached
    }

    func cachedDirectMessages(conversationID: String) -> [DirectMessageDTO]? {
        loadDiskCacheIfNeeded()
        let messages = directStatesByConversationID[conversationID]?.messages
        if messages?.isEmpty == false { return messages }
        let cached = database.recentDirectMessages(conversationID: conversationID, limit: Self.initialHydrationLimit)
        return cached.isEmpty ? nil : cached
    }

    func replaceSquadMessages(circleID: String, messages: [CircleChatMessageDTO]) {
        let state = squadState(circleID: circleID)
        state.messages = limited(messages.sorted { $0.createdAt < $1.createdAt })
        state.hasMoreOlderMessages = state.messages.count >= SquadChatFetchPolicy.networkPageSize
        database.upsertSquadMessages(state.messages)
        database.updateMetadata(kind: .squad, conversationID: circleID, hasMoreOlderMessages: state.hasMoreOlderMessages)
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
        markSquadNetworkFetchSucceeded(circleID: circleID)
        persistDiskCache()
    }

    func squadShouldRefreshFromNetwork(circleID: String, messagesEmpty: Bool) -> Bool {
        let state = squadState(circleID: circleID)
        return SquadChatFetchPolicy.shouldRefreshFromNetwork(
            lastFetchedAt: state.lastFetchedAt,
            messagesEmpty: messagesEmpty,
            messageCount: state.messages.count,
            lastNetworkFetchCount: state.lastNetworkFetchCount,
            transcriptStartsWithMemberJoinedSystemMessage: Self.squadTranscriptStartsWithMemberJoinedSystemMessage(
                state.messages
            )
        )
    }

    func squadTranscriptNeedsForceHeadRevalidate(circleID: String) -> Bool {
        let state = squadState(circleID: circleID)
        let count = state.messages.count
        let pageSize = SquadChatFetchPolicy.networkPageSize
        if count == 0 { return false }
        if let lastFetch = state.lastNetworkFetchCount, count < lastFetch { return true }
        if count < pageSize,
           Self.squadTranscriptStartsWithMemberJoinedSystemMessage(state.messages) {
            return true
        }
        return false
    }

    func noteSquadHeadNetworkFetch(circleID: String, fetchedCount: Int) {
        let state = squadState(circleID: circleID)
        state.lastNetworkFetchCount = fetchedCount
        state.hasMoreOlderMessages = fetchedCount >= SquadChatFetchPolicy.networkPageSize
        database.updateMetadata(
            kind: .squad,
            conversationID: circleID,
            hasMoreOlderMessages: state.hasMoreOlderMessages
        )
    }

    private static func squadTranscriptStartsWithMemberJoinedSystemMessage(
        _ messages: [CircleChatMessageDTO]
    ) -> Bool {
        guard let oldest = messages.first else { return false }
        return oldest.messageType == "system" && oldest.systemKind == "circle_member_joined"
    }

    func markSquadNetworkFetchSucceeded(circleID: String) {
        let state = squadState(circleID: circleID)
        state.lastFetchedAt = Date()
        state.hasLoadedOnce = true
    }

    func warmSquadChatTranscripts(circleIDs: [String]) async {
        loadDiskCacheIfNeeded()
        let sortedIDs = circleIDs.sorted { lhs, rhs in
            let lhsUnread = unreadTracker.unreadCountByCircleID[lhs] ?? 0
            let rhsUnread = unreadTracker.unreadCountByCircleID[rhs] ?? 0
            if lhsUnread != rhsUnread { return lhsUnread > rhsUnread }
            return lhs < rhs
        }
        let idsToWarm = sortedIDs.compactMap { circleID -> String? in
            let trimmed = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return nil }
            let cachedEmpty = cachedSquadMessages(circleID: trimmed)?.isEmpty != false
            guard squadShouldRefreshFromNetwork(circleID: trimmed, messagesEmpty: cachedEmpty) else { return nil }
            return trimmed
        }
        guard !idsToWarm.isEmpty else { return }

        let maxConcurrent = 3
        var index = 0
        while index < idsToWarm.count {
            let batchEnd = min(index + maxConcurrent, idsToWarm.count)
            await withTaskGroup(of: Void.self) { group in
                for circleID in idsToWarm[index..<batchEnd] {
                    group.addTask { [weak self] in
                        guard let self else { return }
                        do {
                            let fetched = try await APIClient.shared.fetchCircleChatMessages(circleId: circleID, limit: 50)
                            await MainActor.run {
                                self.replaceSquadMessages(circleID: circleID, messages: fetched)
                                self.unreadTracker.recomputeSquad(circleID: circleID, messages: fetched)
                            }
                        } catch {
                            OttoLog.api.error(
                                "Squad chat warm fetch failed circle=\(circleID, privacy: .public) error=\(String(describing: error), privacy: .public)"
                            )
                        }
                    }
                }
            }
            index = batchEnd
        }
    }

    func reconcileSquadMessages(
        circleID: String,
        fetchedMessages: [CircleChatMessageDTO],
        visibleMessages: [CircleChatMessageDTO] = []
    ) -> [CircleChatMessageDTO] {
        let state = squadState(circleID: circleID)
        let merged = mergedSquadMessages(state.messages + visibleMessages + fetchedMessages)
        if !merged.isEmpty {
            state.messages = merged
            database.upsertSquadMessages(state.messages)
            reconcileScrollStateAfterMessageSnapshot(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count
            )
        }
        persistDiskCache()
        return state.messages
    }

    func replaceDirectMessages(conversationID: String, messages: [DirectMessageDTO]) {
        let state = directState(conversationID: conversationID)
        state.messages = limited(messages.sorted { $0.createdAt < $1.createdAt })
        state.hasMoreOlderMessages = state.messages.count >= 50
        database.upsertDirectMessages(state.messages)
        database.updateMetadata(kind: .direct, conversationID: conversationID, hasMoreOlderMessages: state.hasMoreOlderMessages)
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
        persistDiskCache()
    }

    @discardableResult
    func upsertSquadMessage(
        _ message: CircleChatMessageDTO,
        eventType: String = "circle.chat.updated",
        currentUserID: String
    ) -> Bool {
        let state = squadState(circleID: message.circleId)
        if message.deletedAt != nil {
            state.messages.removeAll { $0.id == message.id }
            if state.editingMessageId == message.id {
                state.editingMessageId = nil
            }
            database.deleteChatMessage(kind: .squad, conversationID: message.circleId, messageID: message.id)
            reconcileScrollStateAfterMessageSnapshot(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count
            )
            persistDiskCache()
            return false
        }
        let isReplacement = state.messages.contains { $0.id == message.id }
        if let clientMessageId = message.clientMessageId, !clientMessageId.isEmpty {
            state.messages.removeAll {
                $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
            }
        }
        state.messages = mergedSquadMessages(state.messages + [message])
        database.upsertSquadMessages([message])
        latestCircleChatMessage = message
        if !isReplacement {
            let isOwnNewMessage = message.resolvedSenderUserId == currentUserID
            state.shouldScrollOnNextChange = isOwnNewMessage || state.isChatAtBottom
            state.shouldAnimateNextScroll = true
            state.scrollRevision += 1
            reconcileScrollStateAfterNewMessage(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count,
                isOwnMessage: isOwnNewMessage
            )
        } else {
            reconcileScrollStateAfterMessageSnapshot(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count
            )
        }
        if activeConversationID == .squad(message.circleId) {
            unreadTracker.handleSquadMessageUpsert(
                circleID: message.circleId,
                messages: state.messages,
                isPinnedToBottom: state.scrollState.isPinnedToBottom,
                lastReadMessageId: state.scrollState.lastReadMessageId
            )
        } else if eventType == "circle.chat.message", message.resolvedSenderUserId != currentUserID {
            unreadTracker.handleSquadMessageUpsert(
                circleID: message.circleId,
                messages: state.messages,
                isPinnedToBottom: false,
                lastReadMessageId: nil
            )
        } else if eventType == "circle.chat.updated" {
            unreadTracker.recomputeSquad(circleID: message.circleId, messages: state.messages)
        }
        persistDiskCache()
        return !isReplacement
    }

    func insertPendingSquadMessage(_ message: CircleChatMessageDTO) {
        let state = squadState(circleID: message.circleId)
        if let clientMessageId = message.clientMessageId, !clientMessageId.isEmpty {
            state.messages.removeAll {
                $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
            }
        }
        state.messages = mergedSquadMessages(state.messages + [message])
        state.shouldScrollOnNextChange = true
        state.shouldAnimateNextScroll = true
        state.scrollRevision += 1
        reconcileScrollStateAfterNewMessage(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count,
            isOwnMessage: true
        )
    }

    func removePendingSquadMessage(circleID: String, clientMessageId: String) {
        let state = squadState(circleID: circleID)
        state.messages.removeAll {
            $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
        }
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
    }

    func insertPendingDirectMessage(_ message: DirectMessageDTO) {
        let state = directState(conversationID: message.conversationId)
        if let clientMessageId = message.clientMessageId, !clientMessageId.isEmpty {
            state.messages.removeAll {
                $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
            }
        }
        state.messages = mergedDirectMessages(state.messages + [message])
        state.shouldScrollOnNextChange = true
        state.shouldAnimateNextScroll = true
        state.scrollRevision += 1
        reconcileScrollStateAfterNewMessage(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count,
            isOwnMessage: true
        )
    }

    func removePendingDirectMessage(conversationID: String, clientMessageId: String) {
        let state = directState(conversationID: conversationID)
        state.messages.removeAll {
            $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
        }
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
    }

    @discardableResult
    func upsertDirectMessage(
        _ message: DirectMessageDTO,
        eventType: String = "direct.updated",
        currentUserID: String
    ) -> Bool {
        let state = directState(conversationID: message.conversationId)
        if message.deletedAt != nil {
            state.messages.removeAll { $0.id == message.id }
            if state.editingMessageId == message.id {
                state.editingMessageId = nil
            }
            database.deleteChatMessage(kind: .direct, conversationID: message.conversationId, messageID: message.id)
            reconcileScrollStateAfterMessageSnapshot(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count
            )
            persistDiskCache()
            return false
        }
        let isReplacement = state.messages.contains { $0.id == message.id }
        if let clientMessageId = message.clientMessageId, !clientMessageId.isEmpty {
            state.messages.removeAll {
                $0.clientMessageId == clientMessageId && $0.id.hasPrefix("pending-")
            }
        }
        state.messages = mergedDirectMessages(state.messages + [message])
        database.upsertDirectMessages([message])
        latestDirectMessage = message
        if !isReplacement {
            let isOwnNewMessage = message.senderUserId == currentUserID
            state.shouldScrollOnNextChange = isOwnNewMessage || state.isChatAtBottom
            state.shouldAnimateNextScroll = true
            state.scrollRevision += 1
            reconcileScrollStateAfterNewMessage(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count,
                isOwnMessage: isOwnNewMessage
            )
        } else {
            reconcileScrollStateAfterMessageSnapshot(
                state: state,
                newestMessageID: state.messages.last?.id,
                messageCount: state.messages.count
            )
        }
        if activeConversationID == .direct(message.conversationId) {
            unreadTracker.handleDirectMessageUpsert(
                conversationID: message.conversationId,
                messages: state.messages,
                isPinnedToBottom: state.scrollState.isPinnedToBottom,
                lastReadMessageId: state.scrollState.lastReadMessageId
            )
        } else if eventType == "direct.message", message.senderUserId != currentUserID {
            unreadTracker.handleDirectMessageUpsert(
                conversationID: message.conversationId,
                messages: state.messages,
                isPinnedToBottom: false,
                lastReadMessageId: nil
            )
        } else if eventType == "direct.updated" {
            unreadTracker.recomputeDirect(conversationID: message.conversationId, messages: state.messages)
        }
        persistDiskCache()
        return !isReplacement
    }

    func registerDirectConversation(_ conversation: DirectConversationDTO) {
        guard let otherUserID = conversation.otherUser?.id, !otherUserID.isEmpty else { return }
        directConversationsByUserID[otherUserID] = conversation
        let state = directStatesByUserID[otherUserID] ?? DirectChatConversationState(
            recipientUserID: otherUserID,
            recipientName: conversation.otherUser?.displayName ?? "Friend",
            recipientAvatarURL: conversation.otherUser?.avatarUrl,
            recipientMapAccentKey: conversation.otherUser?.mapAccentKey,
            conversation: conversation
        )
        state.conversation = conversation
        state.recipientName = conversation.otherUser?.displayName ?? state.recipientName
        state.recipientAvatarURL = conversation.otherUser?.avatarUrl ?? state.recipientAvatarURL
        state.recipientMapAccentKey = conversation.otherUser?.mapAccentKey ?? state.recipientMapAccentKey
        if directStatesByUserID[otherUserID] == nil {
            observe(state)
            directStatesByUserID[otherUserID] = state
        }
        if state.messages.isEmpty {
            let cached = database.recentDirectMessages(conversationID: conversation.id, limit: Self.initialHydrationLimit)
            if !cached.isEmpty {
                state.messages = cached
                state.isLoading = false
            }
        }
        state.hasMoreOlderMessages = database.metadata(kind: .direct, conversationID: conversation.id)?.hasMoreOlderMessages ?? state.hasMoreOlderMessages
        directStatesByConversationID[conversation.id] = state
        if state.messages.isEmpty {
            unreadTracker.recomputeDirectFromPreview(
                conversationID: conversation.id,
                lastMessageAt: conversation.lastMessageAt,
                lastMessageSenderUserID: conversation.lastMessage?.senderUserId
            )
        } else {
            unreadTracker.recomputeDirect(conversationID: conversation.id, messages: state.messages)
        }
        persistDiskCache()
    }

    func replaceDirectConversations(_ conversations: [DirectConversationDTO]) {
        let serverConversationIDs = Set(conversations.map(\.id))
        let localOnlyEmpty = directConversationsByUserID.values.filter { conversation in
            conversation.lastMessageAt == nil && !serverConversationIDs.contains(conversation.id)
        }
        var merged: [String: DirectConversationDTO] = Dictionary(
            uniqueKeysWithValues: conversations.compactMap { conversation in
                guard let otherUserID = conversation.otherUser?.id, !otherUserID.isEmpty else { return nil }
                return (otherUserID, conversation)
            }
        )
        for conversation in localOnlyEmpty {
            guard let otherUserID = conversation.otherUser?.id, !otherUserID.isEmpty else { continue }
            merged[otherUserID] = conversation
        }
        directConversationsByUserID = merged
        merged.values.forEach(registerDirectConversation)
    }

    func setActiveConversation(_ id: ChatConversationID?) {
        activeConversationID = id
    }

    func markSquadRead(circleID: String) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let state = squadStatesByCircleID[key]
        if let newest = state?.messages.last {
            unreadTracker.markRead(thread: .squad(key), messageId: newest.id, at: newest.createdAt)
        }
        unreadTracker.recomputeSquad(circleID: key, messages: state?.messages ?? [])
    }

    func markDirectRead(conversationID: String) {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let state = directStatesByConversationID[key]
        if let newest = state?.messages.last {
            unreadTracker.markRead(thread: .direct(key), messageId: newest.id, at: newest.createdAt)
        }
        unreadTracker.recomputeDirect(conversationID: key, messages: state?.messages ?? [])
    }

    func clearDirectUnread(conversationID: String) {
        markDirectRead(conversationID: conversationID)
    }

    func clearAll() {
        squadStatesByCircleID.removeAll()
        directStatesByConversationID.removeAll()
        directStatesByUserID.removeAll()
        directConversationsByUserID.removeAll()
        unreadTracker.clearAll()
        latestCircleChatMessage = nil
        latestDirectMessage = nil
        activeConversationID = nil
        persistDiskCache(immediate: true)
    }

    func applyUserProfilePatch(_ patch: UserProfileRealtimePatchDTO) {
        for state in squadStatesByCircleID.values {
            state.messages = state.messages.map { $0.applyingProfilePatch(patch) }
        }
        for state in directStatesByConversationID.values {
            state.messages = state.messages.map { $0.applyingProfilePatch(patch) }
            state.conversation = state.conversation?.applyingProfilePatch(patch)
        }
        directConversationsByUserID = Dictionary(
            uniqueKeysWithValues: directConversationsByUserID.map { key, conversation in
                (key, conversation.applyingProfilePatch(patch))
            }
        )
        latestCircleChatMessage = latestCircleChatMessage.map { $0.applyingProfilePatch(patch) }
        latestDirectMessage = latestDirectMessage.map { $0.applyingProfilePatch(patch) }
        persistDiskCache()
    }

    func requestSquadScrollToMessage(circleID: String, messageID: String) {
        let state = squadState(circleID: circleID)
        setScrollIntent(.scrollToMessage(messageId: messageID, animated: true), source: .userAction, on: state)
        state.scrollToMessageId = messageID
        state.scrollToMessageRevision += 1
    }

    func requestDirectScrollToMessage(conversationID: String, messageID: String) {
        let state = directState(conversationID: conversationID)
        setScrollIntent(.scrollToMessage(messageId: messageID, animated: true), source: .userAction, on: state)
        state.scrollToMessageId = messageID
        state.scrollToMessageRevision += 1
    }

    func requestSquadScrollToLatest(circleID: String, animated: Bool = true) {
        let state = squadState(circleID: circleID)
        setScrollIntent(.scrollToBottom(animated: animated), source: .userAction, on: state)
    }

    func requestDirectScrollToLatest(conversationID: String, animated: Bool = true) {
        let state = directState(conversationID: conversationID)
        setScrollIntent(.scrollToBottom(animated: animated), source: .userAction, on: state)
    }

    func squadConversationBecameVisible(circleID: String, source: ChatScrollIntentSource = .refresh) {
        let state = squadState(circleID: circleID)
        applyRestoreIntentIfNeeded(
            on: state,
            messageIDs: Set(state.messages.map(\.id)),
            newestMessageID: state.messages.last?.id,
            hasMessages: !state.messages.isEmpty,
            source: source
        )
    }

    func squadScrollViewDidAppear(
        circleID: String,
        preserveScrollViewOffset requestedPreserve: Bool,
        scrollViewInstanceId: UUID
    ) {
        let state = squadState(circleID: circleID)
        if unreadTracker.isSquadChatTabVisible(circleID: circleID) {
            noteActiveConversation(.squad(circleID))
        }
        applyScrollViewDidAppear(
            on: state,
            conversationId: circleID,
            messageIDs: Set(state.messages.map(\.id)),
            requestedPreserveScrollViewOffset: requestedPreserve,
            scrollViewInstanceId: scrollViewInstanceId
        )
    }

    func directScrollViewDidAppear(
        conversationID: String,
        preserveScrollViewOffset requestedPreserve: Bool,
        scrollViewInstanceId: UUID
    ) {
        let state = directState(conversationID: conversationID)
        noteActiveConversation(.direct(conversationID))
        applyScrollViewDidAppear(
            on: state,
            conversationId: conversationID,
            messageIDs: Set(state.messages.map(\.id)),
            requestedPreserveScrollViewOffset: requestedPreserve,
            scrollViewInstanceId: scrollViewInstanceId
        )
    }

    func resetSquadScrollSession(circleID: String, reason: String) {
        resetScrollSession(on: squadState(circleID: circleID), conversationId: circleID, reason: reason)
    }

    func resetDirectScrollSession(conversationID: String, reason: String) {
        resetScrollSession(on: directState(conversationID: conversationID), conversationId: conversationID, reason: reason)
    }

    func directConversationBecameVisible(conversationID: String, source: ChatScrollIntentSource = .refresh) {
        let state = directState(conversationID: conversationID)
        applyRestoreIntentIfNeeded(
            on: state,
            messageIDs: Set(state.messages.map(\.id)),
            newestMessageID: state.messages.last?.id,
            hasMessages: !state.messages.isEmpty,
            source: source
        )
    }

    @discardableResult
    func updateSquadBottomVisibility(
        circleID: String,
        isPinned: Bool,
        scrollViewInstanceId: UUID,
        isScrollUserInteracting: Bool = false
    ) -> Bool {
        let state = squadState(circleID: circleID)
        return updateBottomVisibility(
            on: state,
            isPinned: isPinned,
            newestMessageID: state.messages.last?.id,
            scrollViewInstanceId: scrollViewInstanceId,
            isScrollUserInteracting: isScrollUserInteracting
        )
    }

    @discardableResult
    func updateDirectBottomVisibility(
        conversationID: String,
        isPinned: Bool,
        scrollViewInstanceId: UUID,
        isScrollUserInteracting: Bool = false
    ) -> Bool {
        let state = directState(conversationID: conversationID)
        return updateBottomVisibility(
            on: state,
            isPinned: isPinned,
            newestMessageID: state.messages.last?.id,
            scrollViewInstanceId: scrollViewInstanceId,
            isScrollUserInteracting: isScrollUserInteracting
        )
    }

    @discardableResult
    func updateSquadVisibleMessage(circleID: String, messageID: String, scrollViewInstanceId: UUID) -> Bool {
        let state = squadState(circleID: circleID)
        return updateVisibleMessage(on: state, messageID: messageID, scrollViewInstanceId: scrollViewInstanceId)
    }

    @discardableResult
    func updateDirectVisibleMessage(conversationID: String, messageID: String, scrollViewInstanceId: UUID) -> Bool {
        let state = directState(conversationID: conversationID)
        return updateVisibleMessage(on: state, messageID: messageID, scrollViewInstanceId: scrollViewInstanceId)
    }

    func markSquadScrollIntentHandled(circleID: String) {
        let state = squadState(circleID: circleID)
        markScrollIntentHandled(on: state)
        unreadTracker.markSquadReadIfChatTabVisible(circleID: circleID, messages: state.messages)
    }

    func markDirectScrollIntentHandled(conversationID: String) {
        let state = directState(conversationID: conversationID)
        markScrollIntentHandled(on: state)
        unreadTracker.markDirectReadIfThreadVisible(conversationID: conversationID, messages: state.messages)
    }

    func discardSquadPendingScrollIntent(circleID: String, reason: String) {
        discardPendingScrollIntent(on: squadState(circleID: circleID), conversationId: circleID, reason: reason)
    }

    func discardDirectPendingScrollIntent(conversationID: String, reason: String) {
        discardPendingScrollIntent(on: directState(conversationID: conversationID), conversationId: conversationID, reason: reason)
    }

    func squadValidatePendingScrollIntent(circleID: String, transcriptVisible: Bool) -> ScrollIntent? {
        validatePendingScrollIntent(on: squadState(circleID: circleID), transcriptVisible: transcriptVisible)
    }

    func directValidatePendingScrollIntent(conversationID: String, transcriptVisible: Bool) -> ScrollIntent? {
        validatePendingScrollIntent(on: directState(conversationID: conversationID), transcriptVisible: transcriptVisible)
    }

    func beginSquadProgrammaticScroll(circleID: String) {
        var scrollState = squadState(circleID: circleID).scrollState
        scrollState.programmaticScrollInProgress = true
        squadState(circleID: circleID).scrollState = scrollState
    }

    func endSquadProgrammaticScroll(circleID: String) {
        var scrollState = squadState(circleID: circleID).scrollState
        scrollState.programmaticScrollInProgress = false
        squadState(circleID: circleID).scrollState = scrollState
    }

    func beginDirectProgrammaticScroll(conversationID: String) {
        var scrollState = directState(conversationID: conversationID).scrollState
        scrollState.programmaticScrollInProgress = true
        directState(conversationID: conversationID).scrollState = scrollState
    }

    func endDirectProgrammaticScroll(conversationID: String) {
        var scrollState = directState(conversationID: conversationID).scrollState
        scrollState.programmaticScrollInProgress = false
        directState(conversationID: conversationID).scrollState = scrollState
    }

    func prepareSquadOlderMessagesLoad(circleID: String) -> Date? {
        let state = squadState(circleID: circleID)
        guard !state.isLoadingOlderMessages, state.hasMoreOlderMessages, let oldest = state.messages.first?.createdAt else {
            return nil
        }
        state.isLoadingOlderMessages = true
        return oldest
    }

    func finishSquadOlderMessagesLoad(circleID: String, olderMessages: [CircleChatMessageDTO], pageLimit: Int) {
        let state = squadState(circleID: circleID)
        let restoreAnchor = state.scrollState.lastVisibleMessageId ?? state.messages.first?.id
        state.isLoadingOlderMessages = false
        state.hasMoreOlderMessages = olderMessages.count >= pageLimit
        database.updateMetadata(kind: .squad, conversationID: circleID, hasMoreOlderMessages: state.hasMoreOlderMessages)
        guard !olderMessages.isEmpty else {
            persistDiskCache()
            return
        }
        database.upsertSquadMessages(olderMessages)
        state.messages = mergedSquadMessages(olderMessages + state.messages)
        if let restoreAnchor {
            setScrollIntent(.restore(anchorMessageId: restoreAnchor, anchor: .top), source: .pagination, on: state)
        }
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
        persistDiskCache()
    }

    func failSquadOlderMessagesLoad(circleID: String) {
        let state = squadState(circleID: circleID)
        state.isLoadingOlderMessages = false
    }

    func prepareDirectOlderMessagesLoad(conversationID: String) -> Date? {
        let state = directState(conversationID: conversationID)
        guard !state.isLoadingOlderMessages, state.hasMoreOlderMessages, let oldest = state.messages.first?.createdAt else {
            return nil
        }
        state.isLoadingOlderMessages = true
        return oldest
    }

    func finishDirectOlderMessagesLoad(conversationID: String, olderMessages: [DirectMessageDTO], pageLimit: Int) {
        let state = directState(conversationID: conversationID)
        let restoreAnchor = state.scrollState.lastVisibleMessageId ?? state.messages.first?.id
        state.isLoadingOlderMessages = false
        state.hasMoreOlderMessages = olderMessages.count >= pageLimit
        database.updateMetadata(kind: .direct, conversationID: conversationID, hasMoreOlderMessages: state.hasMoreOlderMessages)
        guard !olderMessages.isEmpty else {
            persistDiskCache()
            return
        }
        database.upsertDirectMessages(olderMessages)
        state.messages = mergedDirectMessages(olderMessages + state.messages)
        if let restoreAnchor {
            setScrollIntent(.restore(anchorMessageId: restoreAnchor, anchor: .top), source: .pagination, on: state)
        }
        reconcileScrollStateAfterMessageSnapshot(
            state: state,
            newestMessageID: state.messages.last?.id,
            messageCount: state.messages.count
        )
        persistDiskCache()
    }

    func failDirectOlderMessagesLoad(conversationID: String) {
        let state = directState(conversationID: conversationID)
        state.isLoadingOlderMessages = false
    }

    private func directState(conversationID: String) -> DirectChatConversationState {
        if let state = directStatesByConversationID[conversationID] {
            return state
        }
        let state = DirectChatConversationState(
            recipientUserID: "",
            recipientName: "Friend",
            recipientAvatarURL: nil,
            conversation: nil
        )
        observe(state)
        directStatesByConversationID[conversationID] = state
        return state
    }

    private func reconcileScrollStateAfterMessageSnapshot(
        state: SquadChatConversationState,
        newestMessageID: String?,
        messageCount: Int
    ) {
        var scrollState = state.scrollState
        scrollState.lastKnownMessageCount = messageCount
        scrollState.lastKnownNewestMessageId = newestMessageID
        state.scrollState = scrollState
        guard !scrollState.didInitialScrollToBottom, newestMessageID != nil else { return }
        enqueueInitialScrollToBottomIfNeeded(on: state)
    }

    private func reconcileScrollStateAfterMessageSnapshot(
        state: DirectChatConversationState,
        newestMessageID: String?,
        messageCount: Int
    ) {
        var scrollState = state.scrollState
        scrollState.lastKnownMessageCount = messageCount
        scrollState.lastKnownNewestMessageId = newestMessageID
        state.scrollState = scrollState
        guard !scrollState.didInitialScrollToBottom, newestMessageID != nil else { return }
        enqueueInitialScrollToBottomIfNeeded(on: state)
    }

    private func enqueueInitialScrollToBottomIfNeeded(on state: SquadChatConversationState) {
        guard !state.messages.isEmpty else { return }
        var scrollState = state.scrollState
        guard !scrollState.didInitialScrollToBottom else { return }
        scrollState.isSettlingScrollPosition = true
        state.scrollState = scrollState
        setScrollIntent(.scrollToBottom(animated: false), source: .refresh, on: state)
    }

    private func enqueueInitialScrollToBottomIfNeeded(on state: DirectChatConversationState) {
        guard !state.messages.isEmpty else { return }
        var scrollState = state.scrollState
        guard !scrollState.didInitialScrollToBottom else { return }
        scrollState.isSettlingScrollPosition = true
        state.scrollState = scrollState
        setScrollIntent(.scrollToBottom(animated: false), source: .refresh, on: state)
    }

    private func reconcileScrollStateAfterNewMessage(
        state: SquadChatConversationState,
        newestMessageID: String?,
        messageCount: Int,
        isOwnMessage: Bool
    ) {
        var scrollState = state.scrollState
        let shouldAutoScroll = isOwnMessage || scrollState.isPinnedToBottom
        scrollState.lastKnownMessageCount = messageCount
        scrollState.lastKnownNewestMessageId = newestMessageID
        state.scrollState = scrollState
        if shouldAutoScroll {
            let animated = !(scrollState.isPinnedToBottom || state.isChatAtBottom)
            setScrollIntent(.scrollToBottom(animated: animated), source: .newMessage, on: state)
        }
    }

    private func reconcileScrollStateAfterNewMessage(
        state: DirectChatConversationState,
        newestMessageID: String?,
        messageCount: Int,
        isOwnMessage: Bool
    ) {
        var scrollState = state.scrollState
        let shouldAutoScroll = isOwnMessage || scrollState.isPinnedToBottom
        scrollState.lastKnownMessageCount = messageCount
        scrollState.lastKnownNewestMessageId = newestMessageID
        state.scrollState = scrollState
        if shouldAutoScroll {
            let animated = !(scrollState.isPinnedToBottom || state.isChatAtBottom)
            setScrollIntent(.scrollToBottom(animated: animated), source: .newMessage, on: state)
        }
    }

    private enum ChatScrollAppearDecision {
        case noReposition
        case reposition(ScrollIntent)
    }

    private func scrollAppearDecision(
        scrollState: ConversationScrollState,
        messageCount: Int,
        newestMessageID: String?,
        messageIDs: Set<String>,
        preserveScrollViewOffset: Bool
    ) -> ChatScrollAppearDecision {
        switch ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: messageCount,
            newestMessageID: newestMessageID,
            messageIDs: messageIDs,
            preserveScrollViewOffset: preserveScrollViewOffset
        ) {
        case .noReposition:
            return .noReposition
        case .reposition(let intent):
            return .reposition(intent)
        }
    }

    private func applyScrollViewDidAppear(
        on state: SquadChatConversationState,
        conversationId: String,
        messageIDs: Set<String>,
        requestedPreserveScrollViewOffset: Bool,
        scrollViewInstanceId: UUID
    ) {
        applyScrollViewDidAppear(
            conversationId: conversationId,
            messageIDs: messageIDs,
            requestedPreserveScrollViewOffset: requestedPreserveScrollViewOffset,
            scrollViewInstanceId: scrollViewInstanceId,
            messageCount: state.messages.count,
            newestMessageID: state.messages.last?.id,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 },
            applyDecision: { decision, messageCount, newestMessageID in
                applyScrollAppearDecision(decision, on: state, messageCount: messageCount, newestMessageID: newestMessageID)
            }
        )
    }

    private func applyScrollViewDidAppear(
        on state: DirectChatConversationState,
        conversationId: String,
        messageIDs: Set<String>,
        requestedPreserveScrollViewOffset: Bool,
        scrollViewInstanceId: UUID
    ) {
        applyScrollViewDidAppear(
            conversationId: conversationId,
            messageIDs: messageIDs,
            requestedPreserveScrollViewOffset: requestedPreserveScrollViewOffset,
            scrollViewInstanceId: scrollViewInstanceId,
            messageCount: state.messages.count,
            newestMessageID: state.messages.last?.id,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 },
            applyDecision: { decision, messageCount, newestMessageID in
                applyScrollAppearDecision(decision, on: state, messageCount: messageCount, newestMessageID: newestMessageID)
            }
        )
    }

    private func applyScrollViewDidAppear(
        conversationId: String,
        messageIDs: Set<String>,
        requestedPreserveScrollViewOffset: Bool,
        scrollViewInstanceId: UUID,
        messageCount: Int,
        newestMessageID: String?,
        getScrollState: () -> ConversationScrollState,
        setScrollState: (ConversationScrollState) -> Void,
        applyDecision: (ChatScrollAppearDecision, Int, String?) -> Void
    ) {
        var scrollState = getScrollState()
        scrollState.appearToken &+= 1
        scrollState.ownerConversationId = conversationId

        let priorInstanceId = scrollState.scrollViewInstanceId
        let isFreshMountedGeometry = scrollState.mountedConversationId != conversationId
            || priorInstanceId != scrollViewInstanceId
        if isFreshMountedGeometry {
            scrollState.isMountedScrollGeometryVerified = false
        }
        let preserveScrollViewOffset = resolvePreserveScrollViewOffset(
            scrollState: scrollState,
            conversationId: conversationId,
            requestedPreserve: requestedPreserveScrollViewOffset,
            scrollViewInstanceId: scrollViewInstanceId
        )

        scrollState.scrollViewInstanceId = scrollViewInstanceId
        scrollState.mountedConversationId = conversationId
        setScrollState(scrollState)

        let decision = scrollAppearDecision(
            scrollState: getScrollState(),
            messageCount: messageCount,
            newestMessageID: newestMessageID,
            messageIDs: messageIDs,
            preserveScrollViewOffset: preserveScrollViewOffset
        )

        logScrollIntent(
            action: "scrollViewDidAppear",
            source: .appear,
            intent: decisionIntent(decision),
            scrollState: getScrollState(),
            streamChanged: streamChanged(scrollState: getScrollState(), messageCount: messageCount, newestMessageID: newestMessageID),
            preserveScrollViewOffset: preserveScrollViewOffset,
            preserveRequested: requestedPreserveScrollViewOffset,
            priorScrollViewInstanceId: priorInstanceId,
            activeScrollViewInstanceId: scrollViewInstanceId,
            activeConversationId: conversationId
        )

        applyDecision(decision, messageCount, newestMessageID)
    }

    private func decisionIntent(_ decision: ChatScrollAppearDecision) -> ScrollIntent? {
        switch decision {
        case .noReposition:
            return nil
        case .reposition(let intent):
            return intent
        }
    }

    private func resolvePreserveScrollViewOffset(
        scrollState: ConversationScrollState,
        conversationId: String,
        requestedPreserve: Bool,
        scrollViewInstanceId: UUID
    ) -> Bool {
        guard requestedPreserve else { return false }
        guard scrollState.ownerConversationId == conversationId else { return false }
        guard scrollState.mountedConversationId == conversationId else { return false }
        guard scrollState.scrollViewInstanceId == scrollViewInstanceId else { return false }
        guard scrollState.isMountedScrollGeometryVerified else { return false }
        guard scrollState.pendingScrollIntent == nil else { return false }
        return true
    }

    private func noteActiveConversation(_ conversationID: ChatConversationID) {
        if activeConversationID != conversationID {
            previousActiveConversationID = activeConversationID
            activeConversationID = conversationID
        }
    }

    private func resetScrollSession(
        on state: SquadChatConversationState,
        conversationId: String,
        reason: String
    ) {
        resetScrollSession(
            conversationId: conversationId,
            reason: reason,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 }
        )
    }

    private func resetScrollSession(
        on state: DirectChatConversationState,
        conversationId: String,
        reason: String
    ) {
        resetScrollSession(
            conversationId: conversationId,
            reason: reason,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 }
        )
    }

    private func resetScrollSession(
        conversationId: String,
        reason: String,
        getScrollState: () -> ConversationScrollState,
        setScrollState: (ConversationScrollState) -> Void
    ) {
        var scrollState = getScrollState()
        scrollState.pendingScrollIntent = nil
        scrollState.pendingScrollIntentSource = nil
        scrollState.isSettlingScrollPosition = false
        scrollState.programmaticScrollInProgress = false
        scrollState.scrollViewInstanceId = nil
        scrollState.mountedConversationId = nil
        scrollState.isMountedScrollGeometryVerified = false
        scrollState.appearToken &+= 1
        setScrollState(scrollState)
        logScrollIntent(
            action: "resetScrollSession",
            source: .appear,
            intent: nil,
            scrollState: scrollState,
            streamChanged: nil,
            preserveScrollViewOffset: nil,
            preserveRequested: nil,
            priorScrollViewInstanceId: nil,
            activeScrollViewInstanceId: nil,
            activeConversationId: conversationId,
            resetReason: reason
        )
    }

    private func geometryMatchesActiveScrollView(_ scrollState: ConversationScrollState, scrollViewInstanceId: UUID) -> Bool {
        scrollState.scrollViewInstanceId == scrollViewInstanceId
    }

    private func applyScrollAppearDecision(
        _ decision: ChatScrollAppearDecision,
        messageCount: Int,
        newestMessageID: String?,
        scrollState: inout ConversationScrollState,
        setIntent: (ScrollIntent) -> Void
    ) {
        switch decision {
        case .noReposition:
            scrollState.isSettlingScrollPosition = false
            scrollState.pendingScrollIntent = nil
            scrollState.pendingScrollIntentSource = nil
            scrollState.isMountedScrollGeometryVerified = true
            scrollState.lastKnownMessageCount = messageCount
            scrollState.lastKnownNewestMessageId = newestMessageID
            scrollState.lastAppearDecision = .noReposition
            clearScrollUpAnchorWhenPinned(on: &scrollState)
            if !scrollState.hasUserScrollAnchor {
                scrollState.lastVisibleMessageId = nil
            }
            logScrollIntent(
                action: "appearNoReposition",
                source: .appear,
                intent: nil,
                scrollState: scrollState,
                streamChanged: false,
                preserveScrollViewOffset: nil
            )
        case .reposition(let intent):
            scrollState.lastAppearDecision = .reposition
            scrollState.isSettlingScrollPosition = true
            setIntent(intent)
        }
    }

    private func applyScrollAppearDecision(
        _ decision: ChatScrollAppearDecision,
        on state: SquadChatConversationState,
        messageCount: Int,
        newestMessageID: String?
    ) {
        var scrollState = state.scrollState
        applyScrollAppearDecision(
            decision,
            messageCount: messageCount,
            newestMessageID: newestMessageID,
            scrollState: &scrollState
        ) { intent in
            setScrollIntent(intent, source: .appear, on: state)
        }
        switch decision {
        case .noReposition:
            state.scrollState = scrollState
        case .reposition:
            var merged = state.scrollState
            merged.lastAppearDecision = .reposition
            merged.isSettlingScrollPosition = true
            state.scrollState = merged
        }
    }

    private func applyScrollAppearDecision(
        _ decision: ChatScrollAppearDecision,
        on state: DirectChatConversationState,
        messageCount: Int,
        newestMessageID: String?
    ) {
        var scrollState = state.scrollState
        applyScrollAppearDecision(
            decision,
            messageCount: messageCount,
            newestMessageID: newestMessageID,
            scrollState: &scrollState
        ) { intent in
            setScrollIntent(intent, source: .appear, on: state)
        }
        switch decision {
        case .noReposition:
            state.scrollState = scrollState
        case .reposition:
            var merged = state.scrollState
            merged.lastAppearDecision = .reposition
            merged.isSettlingScrollPosition = true
            state.scrollState = merged
        }
    }

    private func streamChanged(
        scrollState: ConversationScrollState,
        messageCount: Int,
        newestMessageID: String?
    ) -> Bool {
        scrollState.lastKnownMessageCount != messageCount
            || scrollState.lastKnownNewestMessageId != newestMessageID
    }

    private func applyRestoreIntentIfNeeded(
        on state: SquadChatConversationState,
        messageIDs: Set<String>,
        newestMessageID: String?,
        hasMessages: Bool,
        source: ChatScrollIntentSource
    ) {
        applyRestoreIntentIfNeeded(
            messageCount: state.messages.count,
            messageIDs: messageIDs,
            newestMessageID: newestMessageID,
            hasMessages: hasMessages,
            source: source,
            scrollState: state.scrollState
        ) { scrollState in
            state.scrollState = scrollState
        } setIntent: { intent in
            setScrollIntent(intent, source: source, on: state)
        }
    }

    private func applyRestoreIntentIfNeeded(
        on state: DirectChatConversationState,
        messageIDs: Set<String>,
        newestMessageID: String?,
        hasMessages: Bool,
        source: ChatScrollIntentSource
    ) {
        applyRestoreIntentIfNeeded(
            messageCount: state.messages.count,
            messageIDs: messageIDs,
            newestMessageID: newestMessageID,
            hasMessages: hasMessages,
            source: source,
            scrollState: state.scrollState
        ) { scrollState in
            state.scrollState = scrollState
        } setIntent: { intent in
            setScrollIntent(intent, source: source, on: state)
        }
    }

    private func applyRestoreIntentIfNeeded(
        messageCount: Int,
        messageIDs: Set<String>,
        newestMessageID: String?,
        hasMessages: Bool,
        source: ChatScrollIntentSource,
        scrollState: ConversationScrollState,
        setScrollState: (ConversationScrollState) -> Void,
        setIntent: (ScrollIntent) -> Void
    ) {
        guard hasMessages else { return }
        if ChatScrollLogic.shouldDeferRestoreIntent(scrollState: scrollState) {
            return
        }
        guard scrollState.didInitialScrollToBottom else { return }

        let changed = streamChanged(
            scrollState: scrollState,
            messageCount: messageCount,
            newestMessageID: newestMessageID
        )
        guard changed else {
            logScrollIntent(
                action: "skipRestoreUnchangedStream",
                source: source,
                intent: nil,
                scrollState: scrollState,
                streamChanged: false,
                preserveScrollViewOffset: nil
            )
            return
        }

        if scrollState.isPinnedToBottom {
            if scrollState.lastReadMessageId != newestMessageID {
                setIntent(.scrollToBottom(animated: false))
            }
            return
        }

        if scrollState.hasUserScrollAnchor, let messageID = scrollState.lastVisibleMessageId {
            if messageIDs.contains(messageID) {
                setIntent(.restore(anchorMessageId: messageID, anchor: .center))
            } else {
                setIntent(.scrollToBottom(animated: false))
            }
        }
    }

    private func requestRestoreIntent(
        on state: SquadChatConversationState,
        messageIDs: Set<String>,
        newestMessageID: String?,
        hasMessages: Bool
    ) {
        applyRestoreIntentIfNeeded(
            on: state,
            messageIDs: messageIDs,
            newestMessageID: newestMessageID,
            hasMessages: hasMessages,
            source: .refresh
        )
    }

    private func requestRestoreIntent(
        on state: DirectChatConversationState,
        messageIDs: Set<String>,
        newestMessageID: String?,
        hasMessages: Bool
    ) {
        applyRestoreIntentIfNeeded(
            on: state,
            messageIDs: messageIDs,
            newestMessageID: newestMessageID,
            hasMessages: hasMessages,
            source: .refresh
        )
    }

    private func clearScrollUpAnchorWhenPinned(on scrollState: inout ConversationScrollState) {
        if scrollState.isPinnedToBottom {
            scrollState.hasUserScrollAnchor = false
            scrollState.lastVisibleMessageId = nil
        }
    }

    private func shouldIgnoreScrollGeometryUpdates(_ scrollState: ConversationScrollState) -> Bool {
        scrollState.isSettlingScrollPosition || scrollState.programmaticScrollInProgress
    }

    @discardableResult
    private func updateBottomVisibility(
        on state: SquadChatConversationState,
        isPinned: Bool,
        newestMessageID: String?,
        scrollViewInstanceId: UUID,
        isScrollUserInteracting: Bool
    ) -> Bool {
        var scrollState = state.scrollState
        guard geometryMatchesActiveScrollView(scrollState, scrollViewInstanceId: scrollViewInstanceId) else { return false }
        guard !shouldIgnoreScrollGeometryUpdates(scrollState) else { return false }
        if !scrollState.didInitialScrollToBottom || scrollState.pendingScrollIntent == .scrollToBottom(animated: false) {
            guard isPinned else { return false }
        }
        guard scrollState.isPinnedToBottom != isPinned || (isPinned && scrollState.lastReadMessageId != newestMessageID) else { return false }
        let wasPinnedToBottom = scrollState.isPinnedToBottom
        scrollState.isPinnedToBottom = isPinned
        if isPinned {
            scrollState.lastReadMessageId = newestMessageID
            scrollState.isMountedScrollGeometryVerified = true
            clearScrollUpAnchorWhenPinned(on: &scrollState)
        } else if ChatScrollLogic.shouldRecordUserScrollAnchor(
            wasPinnedToBottom: wasPinnedToBottom,
            isPinnedToBottom: isPinned,
            isScrollUserInteracting: isScrollUserInteracting
        ), scrollState.didInitialScrollToBottom, scrollState.pendingScrollIntent == nil {
            scrollState.hasUserScrollAnchor = true
        }
        state.scrollState = scrollState
        state.isChatAtBottom = isPinned
        unreadTracker.handleSquadMessageUpsert(
            circleID: state.circleID,
            messages: state.messages,
            isPinnedToBottom: scrollState.isPinnedToBottom,
            lastReadMessageId: scrollState.lastReadMessageId
        )
        return true
    }

    @discardableResult
    private func updateBottomVisibility(
        on state: DirectChatConversationState,
        isPinned: Bool,
        newestMessageID: String?,
        scrollViewInstanceId: UUID,
        isScrollUserInteracting: Bool
    ) -> Bool {
        var scrollState = state.scrollState
        guard geometryMatchesActiveScrollView(scrollState, scrollViewInstanceId: scrollViewInstanceId) else { return false }
        guard !shouldIgnoreScrollGeometryUpdates(scrollState) else { return false }
        if !scrollState.didInitialScrollToBottom || scrollState.pendingScrollIntent == .scrollToBottom(animated: false) {
            guard isPinned else { return false }
        }
        guard scrollState.isPinnedToBottom != isPinned || (isPinned && scrollState.lastReadMessageId != newestMessageID) else { return false }
        let wasPinnedToBottom = scrollState.isPinnedToBottom
        scrollState.isPinnedToBottom = isPinned
        if isPinned {
            scrollState.lastReadMessageId = newestMessageID
            scrollState.isMountedScrollGeometryVerified = true
            clearScrollUpAnchorWhenPinned(on: &scrollState)
        } else if ChatScrollLogic.shouldRecordUserScrollAnchor(
            wasPinnedToBottom: wasPinnedToBottom,
            isPinnedToBottom: isPinned,
            isScrollUserInteracting: isScrollUserInteracting
        ), scrollState.didInitialScrollToBottom, scrollState.pendingScrollIntent == nil {
            scrollState.hasUserScrollAnchor = true
        }
        state.scrollState = scrollState
        state.isChatAtBottom = isPinned
        if let conversationID = state.conversation?.id {
            unreadTracker.handleDirectMessageUpsert(
                conversationID: conversationID,
                messages: state.messages,
                isPinnedToBottom: scrollState.isPinnedToBottom,
                lastReadMessageId: scrollState.lastReadMessageId
            )
        }
        return true
    }

    @discardableResult
    private func updateVisibleMessage(on state: SquadChatConversationState, messageID: String, scrollViewInstanceId: UUID) -> Bool {
        var scrollState = state.scrollState
        guard geometryMatchesActiveScrollView(scrollState, scrollViewInstanceId: scrollViewInstanceId) else { return false }
        guard !shouldIgnoreScrollGeometryUpdates(scrollState) else { return false }
        guard scrollState.didInitialScrollToBottom, scrollState.pendingScrollIntent == nil, scrollState.hasUserScrollAnchor else { return false }
        guard scrollState.lastVisibleMessageId != messageID else { return false }
        scrollState.lastVisibleMessageId = messageID
        state.scrollState = scrollState
        return true
    }

    @discardableResult
    private func updateVisibleMessage(on state: DirectChatConversationState, messageID: String, scrollViewInstanceId: UUID) -> Bool {
        var scrollState = state.scrollState
        guard geometryMatchesActiveScrollView(scrollState, scrollViewInstanceId: scrollViewInstanceId) else { return false }
        guard !shouldIgnoreScrollGeometryUpdates(scrollState) else { return false }
        guard scrollState.didInitialScrollToBottom, scrollState.pendingScrollIntent == nil, scrollState.hasUserScrollAnchor else { return false }
        guard scrollState.lastVisibleMessageId != messageID else { return false }
        scrollState.lastVisibleMessageId = messageID
        state.scrollState = scrollState
        return true
    }

    private func setScrollIntent(_ intent: ScrollIntent, source: ChatScrollIntentSource, on state: SquadChatConversationState) {
        var scrollState = state.scrollState
        if source == .userAction {
            ChatScrollLogic.applyUserScrollIntent(intent, to: &scrollState)
            state.scrollState = scrollState
            logScrollIntent(
                action: "enqueue",
                source: source,
                intent: intent,
                scrollState: scrollState,
                streamChanged: nil,
                preserveScrollViewOffset: nil
            )
            return
        }
        if let pending = scrollState.pendingScrollIntent, scrollIntentsAreEquivalent(pending, intent) {
            return
        }
        scrollState.pendingScrollIntent = intent
        scrollState.pendingScrollIntentSource = source
        if source != .userAction {
            scrollState.intentAppearToken = scrollState.appearToken
        } else {
            scrollState.intentAppearToken = nil
        }
        scrollState.intentRevision &+= 1
        state.scrollState = scrollState
        logScrollIntent(
            action: "enqueue",
            source: source,
            intent: intent,
            scrollState: scrollState,
            streamChanged: nil,
            preserveScrollViewOffset: nil
        )
    }

    private func setScrollIntent(_ intent: ScrollIntent, source: ChatScrollIntentSource, on state: DirectChatConversationState) {
        var scrollState = state.scrollState
        if source == .userAction {
            ChatScrollLogic.applyUserScrollIntent(intent, to: &scrollState)
            state.scrollState = scrollState
            logScrollIntent(
                action: "enqueue",
                source: source,
                intent: intent,
                scrollState: scrollState,
                streamChanged: nil,
                preserveScrollViewOffset: nil
            )
            return
        }
        if let pending = scrollState.pendingScrollIntent, scrollIntentsAreEquivalent(pending, intent) {
            return
        }
        scrollState.pendingScrollIntent = intent
        scrollState.pendingScrollIntentSource = source
        if source != .userAction {
            scrollState.intentAppearToken = scrollState.appearToken
        } else {
            scrollState.intentAppearToken = nil
        }
        scrollState.intentRevision &+= 1
        state.scrollState = scrollState
        logScrollIntent(
            action: "enqueue",
            source: source,
            intent: intent,
            scrollState: scrollState,
            streamChanged: nil,
            preserveScrollViewOffset: nil
        )
    }

    private func validatePendingScrollIntent(
        on state: SquadChatConversationState,
        transcriptVisible: Bool
    ) -> ScrollIntent? {
        validatePendingScrollIntent(scrollState: state.scrollState, transcriptVisible: transcriptVisible) { scrollState in
            state.scrollState = scrollState
        }
    }

    private func validatePendingScrollIntent(
        on state: DirectChatConversationState,
        transcriptVisible: Bool
    ) -> ScrollIntent? {
        validatePendingScrollIntent(scrollState: state.scrollState, transcriptVisible: transcriptVisible) { scrollState in
            state.scrollState = scrollState
        }
    }

    private func validatePendingScrollIntent(
        scrollState: ConversationScrollState,
        transcriptVisible: Bool,
        setScrollState: (ConversationScrollState) -> Void
    ) -> ScrollIntent? {
        var scrollState = scrollState
        guard let intent = scrollState.pendingScrollIntent, intent != .none else { return nil }

        let source = scrollState.pendingScrollIntentSource ?? .appear
        if let discardReason = ChatScrollLogic.pendingScrollIntentDiscardReason(
            scrollState: scrollState,
            source: source,
            intent: intent
        ) {
            let action: String
            switch discardReason {
            case .staleAppearToken:
                action = "discardStaleAppearToken"
            case .afterNoRepositionAppear:
                action = "discardAfterNoRepositionAppear"
            case .restoreWhilePinned:
                action = "discardRestoreWhilePinned"
            }
            logScrollIntent(
                action: action,
                source: source,
                intent: intent,
                scrollState: scrollState,
                streamChanged: nil,
                preserveScrollViewOffset: nil,
                transcriptVisible: transcriptVisible
            )
            scrollState.pendingScrollIntent = nil
            scrollState.pendingScrollIntentSource = nil
            scrollState.isSettlingScrollPosition = false
            setScrollState(scrollState)
            return nil
        }

        logScrollIntent(
            action: "execute",
            source: source,
            intent: intent,
            scrollState: scrollState,
            streamChanged: nil,
            preserveScrollViewOffset: nil,
            transcriptVisible: transcriptVisible
        )
        return intent
    }

    private func logScrollIntent(
        action: String,
        source: ChatScrollIntentSource,
        intent: ScrollIntent?,
        scrollState: ConversationScrollState,
        streamChanged: Bool?,
        preserveScrollViewOffset: Bool?,
        transcriptVisible: Bool? = nil,
        preserveRequested: Bool? = nil,
        priorScrollViewInstanceId: UUID? = nil,
        activeScrollViewInstanceId: UUID? = nil,
        activeConversationId: String? = nil,
        resetReason: String? = nil
    ) {
        let intentLabel: String = {
            guard let intent else { return "nil" }
            switch intent {
            case .none: return "none"
            case .scrollToBottom(let animated): return "scrollToBottom(animated:\(animated))"
            case .restore(let id, let anchor): return "restore(\(id), \(String(describing: anchor)))"
            case .scrollToMessage(let id, let animated): return "scrollToMessage(\(id), animated:\(animated))"
            }
        }()
        var parts = [
            "action=\(action)",
            "source=\(source.rawValue)",
            "intent=\(intentLabel)",
            "pinned=\(scrollState.isPinnedToBottom)",
            "hasAnchor=\(scrollState.hasUserScrollAnchor)",
            "lastVisible=\(scrollState.lastVisibleMessageId ?? "nil")",
            "appearToken=\(scrollState.appearToken)",
            "intentToken=\(scrollState.intentAppearToken.map(String.init) ?? "nil")",
            "lastAppear=\(String(describing: scrollState.lastAppearDecision))",
            "settling=\(scrollState.isSettlingScrollPosition)",
            "programmatic=\(scrollState.programmaticScrollInProgress)",
            "ownerConversationId=\(scrollState.ownerConversationId ?? "nil")",
            "mountedConversationId=\(scrollState.mountedConversationId ?? "nil")",
            "storedScrollViewInstanceId=\(scrollState.scrollViewInstanceId?.uuidString ?? "nil")",
            "geometryVerified=\(scrollState.isMountedScrollGeometryVerified)",
        ]
        if let previousActiveConversationID {
            parts.append("previousConversationId=\(String(describing: previousActiveConversationID))")
        }
        if let activeConversationId {
            parts.append("activeConversationId=\(activeConversationId)")
        }
        if let priorScrollViewInstanceId {
            parts.append("priorScrollViewInstanceId=\(priorScrollViewInstanceId.uuidString)")
        }
        if let activeScrollViewInstanceId {
            parts.append("activeScrollViewInstanceId=\(activeScrollViewInstanceId.uuidString)")
        }
        if let preserveRequested {
            parts.append("preserveRequested=\(preserveRequested)")
        }
        if let streamChanged {
            parts.append("streamChanged=\(streamChanged)")
        }
        if let preserveScrollViewOffset {
            parts.append("preserveOffsetAllowed=\(preserveScrollViewOffset)")
        }
        if let resetReason {
            parts.append("resetReason=\(resetReason)")
        }
        if let transcriptVisible {
            parts.append("transcriptVisible=\(transcriptVisible)")
        }
        OttoLog.chat.debug("\(parts.joined(separator: " "))")
    }

    private func scrollIntentsAreEquivalent(_ lhs: ScrollIntent, _ rhs: ScrollIntent) -> Bool {
        switch (lhs, rhs) {
        case (.none, .none):
            return true
        case (.scrollToBottom(let leftAnimated), .scrollToBottom(let rightAnimated)):
            return leftAnimated == rightAnimated
        case (.restore(let leftID, let leftAnchor), .restore(let rightID, let rightAnchor)):
            return leftID == rightID && leftAnchor == rightAnchor
        case (.scrollToMessage(let leftID, let leftAnimated), .scrollToMessage(let rightID, let rightAnimated)):
            return leftID == rightID && leftAnimated == rightAnimated
        default:
            return false
        }
    }

    private func markScrollIntentHandled(on state: SquadChatConversationState) {
        var scrollState = state.scrollState
        if case .scrollToBottom = scrollState.pendingScrollIntent {
            scrollState.didInitialScrollToBottom = true
            scrollState.isPinnedToBottom = true
            scrollState.isMountedScrollGeometryVerified = true
            scrollState.lastReadMessageId = scrollState.lastKnownNewestMessageId ?? state.messages.last?.id
            clearScrollUpAnchorWhenPinned(on: &scrollState)
        }
        scrollState.pendingScrollIntent = nil
        scrollState.pendingScrollIntentSource = nil
        scrollState.isSettlingScrollPosition = false
        state.scrollState = scrollState
        state.isChatAtBottom = scrollState.isPinnedToBottom
    }

    private func markScrollIntentHandled(on state: DirectChatConversationState) {
        var scrollState = state.scrollState
        if case .scrollToBottom = scrollState.pendingScrollIntent {
            scrollState.didInitialScrollToBottom = true
            scrollState.isPinnedToBottom = true
            scrollState.isMountedScrollGeometryVerified = true
            scrollState.lastReadMessageId = scrollState.lastKnownNewestMessageId ?? state.messages.last?.id
            clearScrollUpAnchorWhenPinned(on: &scrollState)
        }
        scrollState.pendingScrollIntent = nil
        scrollState.pendingScrollIntentSource = nil
        scrollState.isSettlingScrollPosition = false
        state.scrollState = scrollState
        state.isChatAtBottom = scrollState.isPinnedToBottom
    }

    private func discardPendingScrollIntent(
        on state: SquadChatConversationState,
        conversationId: String,
        reason: String
    ) {
        discardPendingScrollIntent(
            conversationId: conversationId,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 },
            reason: reason
        )
        state.isChatAtBottom = state.scrollState.isPinnedToBottom
    }

    private func discardPendingScrollIntent(
        on state: DirectChatConversationState,
        conversationId: String,
        reason: String
    ) {
        discardPendingScrollIntent(
            conversationId: conversationId,
            getScrollState: { state.scrollState },
            setScrollState: { state.scrollState = $0 },
            reason: reason
        )
        state.isChatAtBottom = state.scrollState.isPinnedToBottom
    }

    private func discardPendingScrollIntent(
        conversationId: String,
        getScrollState: () -> ConversationScrollState,
        setScrollState: (ConversationScrollState) -> Void,
        reason: String
    ) {
        var scrollState = getScrollState()
        let intent = scrollState.pendingScrollIntent
        let source = scrollState.pendingScrollIntentSource ?? .appear
        scrollState.pendingScrollIntent = nil
        scrollState.pendingScrollIntentSource = nil
        scrollState.isSettlingScrollPosition = false
        scrollState.programmaticScrollInProgress = false
        setScrollState(scrollState)
        logScrollIntent(
            action: "discardPendingScrollIntent",
            source: source,
            intent: intent,
            scrollState: scrollState,
            streamChanged: nil,
            preserveScrollViewOffset: nil,
            activeConversationId: conversationId,
            resetReason: reason
        )
    }

    func clearSquadScrollSettle(circleID: String) {
        let state = squadState(circleID: circleID)
        var scrollState = state.scrollState
        scrollState.isSettlingScrollPosition = false
        state.scrollState = scrollState
    }

    func clearDirectScrollSettle(conversationID: String) {
        let state = directState(conversationID: conversationID)
        var scrollState = state.scrollState
        scrollState.isSettlingScrollPosition = false
        state.scrollState = scrollState
    }

    private func observe(_ state: SquadChatConversationState) {
        state.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    private func observe(_ state: DirectChatConversationState) {
        state.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    private func mergedSquadMessages(_ messages: [CircleChatMessageDTO]) -> [CircleChatMessageDTO] {
        var byID: [String: CircleChatMessageDTO] = [:]
        for message in messages {
            byID[message.id] = message
        }
        return limited(byID.values.sorted { $0.createdAt < $1.createdAt })
    }

    private func mergedDirectMessages(_ messages: [DirectMessageDTO]) -> [DirectMessageDTO] {
        var byID: [String: DirectMessageDTO] = [:]
        for message in messages {
            byID[message.id] = message
        }
        return limited(byID.values.sorted { $0.createdAt < $1.createdAt })
    }

    private func limited<Message>(_ messages: [Message]) -> [Message] {
        guard messages.count > Self.maxCachedMessagesPerConversation else { return messages }
        return Array(messages.suffix(Self.maxCachedMessagesPerConversation))
    }

    func preloadDiskCacheIfNeeded() {
        loadDiskCacheIfNeeded()
    }

    private func loadDiskCacheIfNeeded() {
        guard !isCacheLoaded else { return }
        isCacheLoaded = true
        guard let data = try? Data(contentsOf: Self.cacheURL) else { return }
        guard let snapshot = try? JSONDecoder.chatCache.decode(ChatStoreDiskSnapshot.self, from: data) else { return }
        database.upsertSquadMessages(snapshot.squadMessagesByCircleID.values.flatMap { $0 })
        database.upsertDirectMessages(snapshot.directMessagesByConversationID.values.flatMap { $0 })
        for (circleID, messages) in snapshot.squadMessagesByCircleID {
            let sqliteMessages = database.recentSquadMessages(circleID: circleID, limit: Self.initialHydrationLimit)
            let state = SquadChatConversationState(circleID: circleID, messages: sqliteMessages.isEmpty ? messages : sqliteMessages)
            state.draft = snapshot.squadDraftsByCircleID[circleID] ?? ""
            state.replyDraft = snapshot.squadRepliesByCircleID[circleID] ?? .empty
            observe(state)
            squadStatesByCircleID[circleID] = state
        }
        for conversation in snapshot.directConversationsByUserID.values {
            registerDirectConversation(conversation)
        }
        for (conversationID, messages) in snapshot.directMessagesByConversationID {
            let state = directState(conversationID: conversationID)
            let sqliteMessages = database.recentDirectMessages(conversationID: conversationID, limit: Self.initialHydrationLimit)
            state.messages = (sqliteMessages.isEmpty ? messages : sqliteMessages).sorted { $0.createdAt < $1.createdAt }
            state.isLoading = false
        }
        for (userID, draft) in snapshot.directDraftsByUserID {
            directStatesByUserID[userID]?.draft = draft
        }
        for (userID, reply) in snapshot.directRepliesByUserID {
            directStatesByUserID[userID]?.replyDraft = reply
        }
    }

    private func persistDiskCache(immediate: Bool = false) {
        persistTask?.cancel()
        if immediate {
            persistDiskCacheNow()
            return
        }
        persistTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.persistDiskCacheNow()
            }
        }
    }

    private func persistDiskCacheNow() {
        let snapshot = ChatStoreDiskSnapshot(
            squadMessagesByCircleID: squadStatesByCircleID.mapValues(\.messages),
            directMessagesByConversationID: directStatesByConversationID.mapValues(\.messages),
            directConversationsByUserID: directConversationsByUserID,
            squadDraftsByCircleID: squadStatesByCircleID.mapValues(\.draft).filter { !$0.value.isEmpty },
            directDraftsByUserID: directStatesByUserID.mapValues(\.draft).filter { !$0.value.isEmpty },
            squadRepliesByCircleID: squadStatesByCircleID.mapValues(\.replyDraft).filter { !$0.value.isEmpty },
            directRepliesByUserID: directStatesByUserID.mapValues(\.replyDraft).filter { !$0.value.isEmpty }
        )
        guard let data = try? JSONEncoder.chatCache.encode(snapshot) else { return }
        try? FileManager.default.createDirectory(
            at: Self.cacheURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: Self.cacheURL, options: [.atomic])
    }

    private static var cacheURL: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent(cacheFileName, isDirectory: false)
    }
}

private struct ChatStoreDiskSnapshot: Codable {
    var squadMessagesByCircleID: [String: [CircleChatMessageDTO]]
    var directMessagesByConversationID: [String: [DirectMessageDTO]]
    var directConversationsByUserID: [String: DirectConversationDTO]
    var squadDraftsByCircleID: [String: String]
    var directDraftsByUserID: [String: String]
    var squadRepliesByCircleID: [String: ChatReplyDraft]
    var directRepliesByUserID: [String: ChatReplyDraft]
}

private extension JSONEncoder {
    static var chatCache: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}

private extension JSONDecoder {
    static var chatCache: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
