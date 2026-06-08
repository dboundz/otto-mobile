import Foundation
import Combine
import UIKit
import os

@MainActor
final class SquadChatThreadStore: ObservableObject {
    let circleID: String

    @Published var messages: [CircleChatMessageDTO] = []
    @Published var isLoadingMessages = false
    @Published var isLoadingOlderMessages = false
    @Published private(set) var isRefreshingMessages = false
    @Published var hasMoreOlderMessages = true
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published var isChatAtBottom = true
    @Published var scrollRevision = 0
    @Published var scrollToMessageId: String?
    @Published var scrollToMessageRevision = 0
    @Published var shouldScrollOnNextChange = false
    @Published var shouldAnimateNextScroll = true
    @Published var scrollState = ConversationScrollState.initial
    @Published var draft = "" {
        didSet {
            guard !isCopyingFromState, draft != oldValue else { return }
            state?.draft = draft
        }
    }
    @Published var replyDraft = ChatReplyDraft.empty {
        didSet {
            guard !isCopyingFromState, replyDraft != oldValue else { return }
            state?.replyDraft = replyDraft
        }
    }
    @Published var isSendingMessage = false {
        didSet {
            guard !isCopyingFromState, isSendingMessage != oldValue else { return }
            state?.isSendingMessage = isSendingMessage
        }
    }
    @Published var pendingAttachments: [ChatPendingComposerAttachment] = [] {
        didSet {
            guard !isCopyingFromState else { return }
            state?.pendingAttachments = pendingAttachments
        }
    }
    @Published var editingMessageId: String? {
        didSet {
            guard !isCopyingFromState, editingMessageId != oldValue else { return }
            state?.editingMessageId = editingMessageId
        }
    }

    private var pollingTask: Task<Void, Never>?
    private var backgroundRefreshTask: Task<Void, Never>?
    private var state: SquadChatConversationState?
    private var cancellables: Set<AnyCancellable> = []
    private var isCopyingFromState = false

    init(circleID: String) {
        self.circleID = circleID
    }

    func activateChatTab(appState: AppState) {
        bind(to: appState)
        appState.setSquadChatTabVisible(circleID: circleID, isVisible: true)
        _ = seedFromCacheIfAvailable(appState: appState)
        appState.chatStore.markSquadReadIfChatTabVisible(circleID: circleID)
        appState.publishChatUnreadFromStore()
        let needsForceHeadRevalidate = appState.chatStore.squadTranscriptNeedsForceHeadRevalidate(circleID: circleID)
        let shouldRefresh = needsForceHeadRevalidate
            || appState.chatStore.squadShouldRefreshFromNetwork(
                circleID: circleID,
                messagesEmpty: messages.isEmpty
            )
        if backgroundRefreshTask == nil, shouldRefresh {
            let showSpinner = messages.isEmpty
            backgroundRefreshTask = Task { [weak self] in
                guard let self else { return }
                await self.refreshFromNetwork(
                    appState: appState,
                    showLoadingSpinner: showSpinner,
                    force: needsForceHeadRevalidate
                )
                await MainActor.run {
                    self.backgroundRefreshTask = nil
                }
            }
        }
        reconcilePolling(isRealtimeConnected: appState.isChatRealtimeConnected, appState: appState)
    }

    func deactivateChatTab(appState: AppState) {
        detachChatTab(appState: appState)
    }

    /// Chat → Events/Grid: preserve scroll pin/anchor; stop polling only.
    func pauseChatTab(appState: AppState) {
        if !messages.isEmpty {
            appState.replaceSquadChatTranscript(forCircleID: circleID, messages: messages)
        }
        appState.setSquadChatTabVisible(circleID: circleID, isVisible: false)
        appState.chatStore.refreshSquadUnread(circleID: circleID)
        appState.publishChatUnreadFromStore()
        stopPolling()
    }

    /// Leave squad detail: persist transcript and stop polling; keep scroll + fetch metadata.
    func detachChatTab(appState: AppState) {
        if !messages.isEmpty {
            appState.replaceSquadChatTranscript(forCircleID: circleID, messages: messages)
        }
        appState.setSquadChatTabVisible(circleID: circleID, isVisible: false)
        appState.chatStore.refreshSquadUnread(circleID: circleID)
        appState.publishChatUnreadFromStore()
        backgroundRefreshTask?.cancel()
        backgroundRefreshTask = nil
        stopPolling()
    }

    /// Full scroll reset — reserved for logout / explicit cache clear.
    func teardownChatTab(appState: AppState) {
        detachChatTab(appState: appState)
        appState.chatStore.resetSquadScrollSession(circleID: circleID, reason: "teardown")
    }

    @available(*, deprecated, message: "Use deactivateChatTab when leaving Chat tab.")
    func deactivate(appState: AppState) {
        deactivateChatTab(appState: appState)
    }

    @discardableResult
    func seedFromCacheIfAvailable(appState: AppState) -> Bool {
        bind(to: appState)
        guard messages.isEmpty else { return false }
        let snapshot =
            state?.messages.isEmpty == false ? state?.messages : appState.cachedSquadChatMessages(forCircleID: circleID)
        guard let snapshot, !snapshot.isEmpty else { return false }
        state?.messages = snapshot
        state?.isLoadingMessages = false
        state?.errorMessage = nil
        copyFromState()
        return true
    }

    func sceneBecameActive(appState: AppState) {
        appState.connectChatRealtimeIfNeeded()
        _ = seedFromCacheIfAvailable(appState: appState)
        if messages.isEmpty, isLoadingMessages {
            isLoadingMessages = false
        }
        let needsForceHeadRevalidate = appState.chatStore.squadTranscriptNeedsForceHeadRevalidate(circleID: circleID)
        let shouldRefresh = needsForceHeadRevalidate
            || appState.chatStore.squadShouldRefreshFromNetwork(
                circleID: circleID,
                messagesEmpty: messages.isEmpty
            )
        if shouldRefresh {
            Task { [weak self] in
                guard let self else { return }
                await self.refreshFromNetwork(
                    appState: appState,
                    showLoadingSpinner: self.messages.isEmpty,
                    force: needsForceHeadRevalidate
                )
            }
        }
        reconcilePolling(isRealtimeConnected: appState.isChatRealtimeConnected, appState: appState)
    }

    func reconcilePolling(isRealtimeConnected: Bool, appState: AppState) {
        if isRealtimeConnected {
            stopPolling()
            statusMessage = nil
        } else {
            startPolling(appState: appState)
        }
    }

    func requestScrollToMessage(_ messageId: String, appState: AppState) {
        bind(to: appState)
        appState.chatStore.requestSquadScrollToMessage(circleID: circleID, messageID: messageId)
        copyFromState()
    }

    func jumpToQuotedMessage(_ messageId: String, appState: AppState) {
        bind(to: appState)
        let trimmed = messageId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if messages.contains(where: { $0.id == trimmed }) {
            requestScrollToMessage(trimmed, appState: appState)
            return
        }
        guard hasMoreOlderMessages, !isLoadingOlderMessages else { return }
        Task { [weak self] in
            guard let self else { return }
            await self.loadOlderPageForQuoteJump(appState: appState, targetMessageID: trimmed)
        }
    }

    private func loadOlderPageForQuoteJump(appState: AppState, targetMessageID: String) async {
        guard let before = appState.chatStore.prepareSquadOlderMessagesLoad(circleID: circleID) else { return }
        copyFromState()
        do {
            let pageLimit = 50
            let older = try await APIClient.shared.fetchCircleChatMessages(
                circleId: circleID,
                limit: pageLimit,
                before: before
            )
            await MainActor.run {
                appState.chatStore.finishSquadOlderMessagesLoad(
                    circleID: circleID,
                    olderMessages: older,
                    pageLimit: pageLimit
                )
                self.copyFromState()
                if self.messages.contains(where: { $0.id == targetMessageID }) {
                    self.requestScrollToMessage(targetMessageID, appState: appState)
                }
            }
        } catch {
            await MainActor.run {
                appState.chatStore.failSquadOlderMessagesLoad(circleID: circleID)
                self.copyFromState()
            }
        }
    }

    func requestScrollToLatest(appState: AppState, animated: Bool = true) {
        appState.chatStore.requestSquadScrollToLatest(circleID: circleID, animated: animated)
        copyFromState()
    }

    func conversationBecameVisible(appState: AppState) {
        appState.chatStore.squadConversationBecameVisible(circleID: circleID)
        copyFromState()
    }

    func scrollViewDidAppear(appState: AppState, preserveScrollViewOffset: Bool, scrollViewInstanceId: UUID) {
        appState.chatStore.squadScrollViewDidAppear(
            circleID: circleID,
            preserveScrollViewOffset: preserveScrollViewOffset,
            scrollViewInstanceId: scrollViewInstanceId
        )
        copyFromState()
    }

    func updateBottomVisibility(
        isPinned: Bool,
        appState: AppState,
        scrollViewInstanceId: UUID,
        isScrollUserInteracting: Bool = false
    ) {
        let didChange = appState.chatStore.updateSquadBottomVisibility(
            circleID: circleID,
            isPinned: isPinned,
            scrollViewInstanceId: scrollViewInstanceId,
            isScrollUserInteracting: isScrollUserInteracting
        )
        if didChange {
            copyFromState()
        }
    }

    func updateVisibleMessage(_ messageID: String, appState: AppState, scrollViewInstanceId: UUID) {
        let didChange = appState.chatStore.updateSquadVisibleMessage(
            circleID: circleID,
            messageID: messageID,
            scrollViewInstanceId: scrollViewInstanceId
        )
        if didChange {
            copyFromState()
        }
    }

    func markScrollIntentHandled(appState: AppState) {
        appState.chatStore.markSquadScrollIntentHandled(circleID: circleID)
        copyFromState()
    }

    func clearScrollSettle(appState: AppState) {
        appState.chatStore.clearSquadScrollSettle(circleID: circleID)
        copyFromState()
    }

    func clearComposerForSend(appState: AppState) {
        bind(to: appState)
        state?.draft = ""
        state?.pendingAttachments = []
        state?.replyDraft = .empty
        copyFromState()
    }

    func beginEditingMessage(_ message: CircleChatMessageDTO, appState: AppState) {
        bind(to: appState)
        state?.editingMessageId = message.id
        state?.draft = message.body
        state?.replyDraft = .empty
        copyFromState()
    }

    func cancelEditingMessage(appState: AppState, clearDraft: Bool = true) {
        bind(to: appState)
        state?.editingMessageId = nil
        if clearDraft {
            state?.draft = ""
        }
        copyFromState()
    }

    func loadOlderMessagesIfNeeded(appState: AppState, thresholdMessageID: String) {
        guard scrollState.didInitialScrollToBottom, scrollState.pendingScrollIntent == nil, scrollState.hasUserScrollAnchor else { return }
        guard messages.prefix(5).contains(where: { $0.id == thresholdMessageID }) else { return }
        guard let before = appState.chatStore.prepareSquadOlderMessagesLoad(circleID: circleID) else { return }
        copyFromState()
        Task { [circleID] in
            do {
                let pageLimit = 50
                let older = try await APIClient.shared.fetchCircleChatMessages(
                    circleId: circleID,
                    limit: pageLimit,
                    before: before
                )
                await MainActor.run {
                    appState.chatStore.finishSquadOlderMessagesLoad(
                        circleID: circleID,
                        olderMessages: older,
                        pageLimit: pageLimit
                    )
                    self.copyFromState()
                }
            } catch {
                await MainActor.run {
                    appState.chatStore.failSquadOlderMessagesLoad(circleID: circleID)
                    self.copyFromState()
                }
            }
        }
    }

    func pullToRefresh(appState: AppState) async {
        await refreshFromNetwork(appState: appState, showLoadingSpinner: false, force: true)
    }

    func refreshFromNetwork(appState: AppState, showLoadingSpinner: Bool, force: Bool = false) async {
        guard !isRefreshingMessages else { return }
        if !force,
           !appState.chatStore.squadShouldRefreshFromNetwork(circleID: circleID, messagesEmpty: messages.isEmpty) {
            return
        }
        bind(to: appState)
        isRefreshingMessages = true
        let didShowLoadingSpinner = showLoadingSpinner && messages.isEmpty
        if didShowLoadingSpinner {
            state?.isLoadingMessages = true
            copyFromState()
        }
        state?.errorMessage = nil
        copyFromState()
        defer {
            isRefreshingMessages = false
            if didShowLoadingSpinner {
                state?.isLoadingMessages = false
                copyFromState()
            }
        }

        do {
            let fetched = try await APIClient.shared.fetchCircleChatMessages(circleId: circleID, limit: 50)
            appState.chatStore.noteSquadHeadNetworkFetch(circleID: circleID, fetchedCount: fetched.count)
            let reconciled = appState.reconcileSquadChatTranscript(
                forCircleID: circleID,
                fetchedMessages: fetched,
                visibleMessages: messages
            )
            if !reconciled.isEmpty || messages.isEmpty {
                state?.messages = ChatScrollLogic.squadMessagesAfterNetworkRefresh(
                    reconciled: reconciled,
                    visibleMessages: messages,
                    hasUserScrollAnchor: scrollState.hasUserScrollAnchor,
                    hasMoreOlderMessages: hasMoreOlderMessages,
                    initialPageSize: SquadChatFetchPolicy.networkPageSize,
                    forceFullTranscript: force,
                    createdAt: \.createdAt
                )
                copyFromState()
            }
            if !messages.isEmpty {
                appState.replaceSquadChatTranscript(forCircleID: circleID, messages: messages)
                appState.chatStore.squadConversationBecameVisible(circleID: circleID)
                appState.chatStore.markSquadReadIfChatTabVisible(circleID: circleID)
                appState.publishChatUnreadFromStore()
                copyFromState()
            }
            appState.chatStore.markSquadNetworkFetchSucceeded(circleID: circleID)
        } catch {
            OttoLog.api.error(
                "Squad chat refresh failed circle=\(self.circleID, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            if messages.isEmpty {
                state?.errorMessage = "Couldn't load squad chat."
                copyFromState()
            }
        }
    }

    func upsert(_ message: CircleChatMessageDTO, appState: AppState) {
        bind(to: appState)
        appState.upsertSquadChatTranscript(with: message)
        copyFromState()
    }

    private func startPolling(appState: AppState) {
        guard pollingTask == nil else { return }
        statusMessage = "Live updates are using polling."
        pollingTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.pollOnce(appState: appState)
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    private func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
    }

    private func pollOnce(appState: AppState) async {
        do {
            let fetched = try await APIClient.shared.fetchCircleChatMessages(circleId: circleID, limit: 50)
            fetched.forEach { upsert($0, appState: appState) }
        } catch {
            OttoLog.api.error(
                "Squad chat poll failed circle=\(self.circleID, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            if messages.isEmpty {
                state?.errorMessage = "Couldn't refresh squad chat."
                copyFromState()
            }
        }
    }

    private func bind(to appState: AppState) {
        let nextState = appState.chatStore.squadState(circleID: circleID)
        guard state !== nextState else { return }
        cancellables.removeAll()
        state = nextState
        nextState.objectWillChange
            .sink { [weak self] _ in
                Task { @MainActor in self?.copyFromState() }
            }
            .store(in: &cancellables)
        copyFromState()
    }

    private func copyFromState() {
        guard let state else { return }
        isCopyingFromState = true
        defer { isCopyingFromState = false }
        if messages != state.messages { messages = state.messages }
        if isLoadingMessages != state.isLoadingMessages { isLoadingMessages = state.isLoadingMessages }
        if isLoadingOlderMessages != state.isLoadingOlderMessages { isLoadingOlderMessages = state.isLoadingOlderMessages }
        if hasMoreOlderMessages != state.hasMoreOlderMessages { hasMoreOlderMessages = state.hasMoreOlderMessages }
        if errorMessage != state.errorMessage { errorMessage = state.errorMessage }
        if statusMessage != state.statusMessage { statusMessage = state.statusMessage }
        if isChatAtBottom != state.isChatAtBottom { isChatAtBottom = state.isChatAtBottom }
        if scrollRevision != state.scrollRevision { scrollRevision = state.scrollRevision }
        if scrollToMessageId != state.scrollToMessageId { scrollToMessageId = state.scrollToMessageId }
        if scrollToMessageRevision != state.scrollToMessageRevision { scrollToMessageRevision = state.scrollToMessageRevision }
        if shouldScrollOnNextChange != state.shouldScrollOnNextChange { shouldScrollOnNextChange = state.shouldScrollOnNextChange }
        if shouldAnimateNextScroll != state.shouldAnimateNextScroll { shouldAnimateNextScroll = state.shouldAnimateNextScroll }
        if scrollState != state.scrollState { scrollState = state.scrollState }
        if draft != state.draft { draft = state.draft }
        if replyDraft != state.replyDraft { replyDraft = state.replyDraft }
        if isSendingMessage != state.isSendingMessage { isSendingMessage = state.isSendingMessage }
        if pendingAttachments != state.pendingAttachments { pendingAttachments = state.pendingAttachments }
        if editingMessageId != state.editingMessageId { editingMessageId = state.editingMessageId }
    }
}
