import Foundation
import SwiftUI

/// Pure scroll decision helpers shared by `ChatStore` and unit tests.
enum ChatScrollLogic {
    enum AppearDecision: Equatable {
        case noReposition
        case reposition(ScrollIntent)
    }

    static func unreadCountBelowLastRead(messageIDs: [String], lastReadMessageId: String?) -> Int {
        guard !messageIDs.isEmpty else { return 0 }
        guard let lastReadMessageId else { return messageIDs.count }
        guard let index = messageIDs.firstIndex(of: lastReadMessageId) else { return messageIDs.count }
        return max(0, messageIDs.count - index - 1)
    }

    @discardableResult
    static func applyUserScrollIntent(_ intent: ScrollIntent, to scrollState: inout ConversationScrollState) -> Int {
        if case .scrollToBottom = intent {
            scrollState.hasUserScrollAnchor = false
            scrollState.lastVisibleMessageId = nil
        }
        scrollState.pendingScrollIntent = intent
        scrollState.pendingScrollIntentSource = .userAction
        scrollState.intentAppearToken = nil
        scrollState.intentRevision &+= 1
        return scrollState.intentRevision
    }

    static func shouldDeferRestoreIntent(scrollState: ConversationScrollState) -> Bool {
        if scrollState.isSettlingScrollPosition { return true }
        if case .scrollToBottom(animated: false) = scrollState.pendingScrollIntent { return true }
        return false
    }

    /// Only record a scroll-up anchor when the user dragged the transcript — not when composer/keyboard inset changed.
    static func shouldRecordUserScrollAnchor(
        wasPinnedToBottom: Bool,
        isPinnedToBottom: Bool,
        isScrollUserInteracting: Bool
    ) -> Bool {
        guard wasPinnedToBottom, !isPinnedToBottom else { return false }
        return isScrollUserInteracting
    }

    static func shouldTrustPinnedStateForNoReposition(scrollState: ConversationScrollState) -> Bool {
        scrollState.isPinnedToBottom && scrollState.isMountedScrollGeometryVerified
    }

    static func shouldMarkBottomScrollIntentHandled(
        isLayoutReady: Bool,
        distanceFromBottom: CGFloat
    ) -> Bool {
        ChatUIKitScrollPinning.isBottomSentinelVisible(
            distanceFromBottom: distanceFromBottom,
            isLayoutReady: isLayoutReady
        )
    }

    /// Re-pin the transcript when bottom safe-area inset changes while the user was at the latest end.
    static func shouldCompensateScrollForBottomInsetChange(
        previousInsetBottom: CGFloat?,
        newInsetBottom: CGFloat,
        lastPublishedDistance: CGFloat,
        isScrollUserInteracting: Bool,
        isDecelerating: Bool
    ) -> Bool {
        guard !isScrollUserInteracting, !isDecelerating else { return false }
        guard let previousInsetBottom else { return false }
        guard abs(newInsetBottom - previousInsetBottom) > 0.5 else { return false }
        return ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: lastPublishedDistance)
    }

    static func shouldHideTranscriptForScrollSettle(
        isLoadingMessages: Bool,
        messagesEmpty: Bool,
        scrollState: ConversationScrollState
    ) -> Bool {
        if isLoadingMessages && messagesEmpty { return true }
        if messagesEmpty { return false }
        if case .restore = scrollState.pendingScrollIntent { return true }
        if scrollState.hasUserScrollAnchor && scrollState.isSettlingScrollPosition { return true }
        return false
    }

    /// Full-screen loading chrome only for true first fetch — not scroll settle with cached messages.
    static func shouldShowScrollSettleLoadingOverlay(
        isLoadingMessages: Bool,
        messagesEmpty: Bool,
        scrollState: ConversationScrollState
    ) -> Bool {
        isLoadingMessages && messagesEmpty
    }

    /// Jump-to-latest FAB visibility from local scroll geometry (matches Android `derivedStateOf` pinning).
    static func shouldShowJumpToLatestAffordance(
        didInitialScrollToBottom: Bool,
        isScrollLayoutReady: Bool,
        distanceFromBottom: CGFloat,
        messagesEmpty: Bool,
        isHidingTranscriptForScrollSettle: Bool
    ) -> Bool {
        guard didInitialScrollToBottom, isScrollLayoutReady, !messagesEmpty else { return false }
        guard !isHidingTranscriptForScrollSettle else { return false }
        return !ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: distanceFromBottom)
    }

    static func squadShouldRefreshFromNetwork(
        messagesEmpty: Bool,
        lastFetchedAt: Date?,
        messageCount: Int = 0,
        lastNetworkFetchCount: Int? = nil,
        transcriptStartsWithMemberJoinedSystemMessage: Bool = false,
        now: Date = Date()
    ) -> Bool {
        SquadChatFetchPolicy.shouldRefreshFromNetwork(
            lastFetchedAt: lastFetchedAt,
            messagesEmpty: messagesEmpty,
            messageCount: messageCount,
            lastNetworkFetchCount: lastNetworkFetchCount,
            transcriptStartsWithMemberJoinedSystemMessage: transcriptStartsWithMemberJoinedSystemMessage,
            now: now
        )
    }

    /// Applies a network refresh merge without dropping pre-existing history unless the user scrolled up.
    static func squadMessagesAfterNetworkRefresh<Message>(
        reconciled: [Message],
        visibleMessages: [Message],
        hasUserScrollAnchor: Bool,
        hasMoreOlderMessages: Bool,
        initialPageSize: Int,
        forceFullTranscript: Bool = false,
        createdAt: (Message) -> Date
    ) -> [Message] {
        if forceFullTranscript { return reconciled }
        let shouldPreserveVisibleWindow =
            visibleMessages.count >= initialPageSize
            && (
                hasUserScrollAnchor
                || hasMoreOlderMessages
            )
        guard shouldPreserveVisibleWindow,
              let oldestVisibleDate = visibleMessages.first.map(createdAt) else {
            return reconciled
        }
        return reconciled.filter { createdAt($0) >= oldestVisibleDate }
    }

    static func scrollAppearDecision(
        scrollState: ConversationScrollState,
        messageCount: Int,
        newestMessageID: String?,
        messageIDs: Set<String>,
        preserveScrollViewOffset: Bool
    ) -> AppearDecision {
        guard messageCount > 0 else { return .noReposition }

        if !scrollState.didInitialScrollToBottom {
            return .reposition(.scrollToBottom(animated: false))
        }

        let streamUnchanged = scrollState.lastKnownMessageCount == messageCount
            && scrollState.lastKnownNewestMessageId == newestMessageID

        if !preserveScrollViewOffset {
            if scrollState.hasUserScrollAnchor, let messageID = scrollState.lastVisibleMessageId {
                if messageIDs.contains(messageID) {
                    return .reposition(.restore(anchorMessageId: messageID, anchor: .center))
                }
                return .reposition(.scrollToBottom(animated: false))
            }
            if scrollState.isPinnedToBottom {
                if streamUnchanged {
                    return scrollState.isMountedScrollGeometryVerified
                        ? .noReposition
                        : .reposition(.scrollToBottom(animated: false))
                }
                return .reposition(.scrollToBottom(animated: false))
            }
            if !streamUnchanged {
                return restoreIntentForChangedStream(
                    scrollState: scrollState,
                    newestMessageID: newestMessageID,
                    messageIDs: messageIDs
                )
            }
            return .reposition(.scrollToBottom(animated: false))
        }

        if streamUnchanged {
            if shouldTrustPinnedStateForNoReposition(scrollState: scrollState),
               scrollState.lastReadMessageId == newestMessageID {
                return .noReposition
            }
            if scrollState.hasUserScrollAnchor, let messageID = scrollState.lastVisibleMessageId {
                if messageIDs.contains(messageID) {
                    return .reposition(.restore(anchorMessageId: messageID, anchor: .center))
                }
                return .reposition(.scrollToBottom(animated: false))
            }
            if scrollState.isPinnedToBottom {
                return scrollState.isMountedScrollGeometryVerified
                    ? .noReposition
                    : .reposition(.scrollToBottom(animated: false))
            }
        }

        return restoreIntentForChangedStream(
            scrollState: scrollState,
            newestMessageID: newestMessageID,
            messageIDs: messageIDs
        )
    }

    static func restoreIntentForChangedStream(
        scrollState: ConversationScrollState,
        newestMessageID: String?,
        messageIDs: Set<String>
    ) -> AppearDecision {
        if scrollState.isPinnedToBottom {
            if !scrollState.isMountedScrollGeometryVerified || scrollState.lastReadMessageId != newestMessageID {
                return .reposition(.scrollToBottom(animated: false))
            }
        }
        if scrollState.hasUserScrollAnchor, let messageID = scrollState.lastVisibleMessageId {
            if messageIDs.contains(messageID) {
                return .reposition(.restore(anchorMessageId: messageID, anchor: .center))
            }
            return .reposition(.scrollToBottom(animated: false))
        }
        return .noReposition
    }

    enum PendingScrollIntentDiscardReason: Equatable {
        case staleAppearToken
        case afterNoRepositionAppear
        case restoreWhilePinned
    }

    static func pendingScrollIntentDiscardReason(
        scrollState: ConversationScrollState,
        source: ChatScrollIntentSource,
        intent: ScrollIntent
    ) -> PendingScrollIntentDiscardReason? {
        guard intent != .none else { return nil }

        if source != .userAction {
            if let token = scrollState.intentAppearToken, token != scrollState.appearToken {
                return .staleAppearToken
            }
            // Empty chats appear with noReposition (messageCount == 0). When the first message
            // arrives we still need the pending scroll intent — do not discard before initial scroll.
            if scrollState.lastAppearDecision == .noReposition,
               scrollState.didInitialScrollToBottom,
               isLifecycleScrollIntentSource(source) {
                return .afterNoRepositionAppear
            }
        }

        if case .restore = intent, scrollState.isPinnedToBottom {
            return .restoreWhilePinned
        }

        return nil
    }

    private static func isLifecycleScrollIntentSource(_ source: ChatScrollIntentSource) -> Bool {
        switch source {
        case .appear, .refresh, .loadConversation:
            return true
        case .userAction, .pagination, .newMessage:
            return false
        }
    }
}
