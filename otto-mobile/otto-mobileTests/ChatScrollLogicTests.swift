import SwiftUI
import XCTest
@testable import otto_mobile

@MainActor
final class ChatScrollLogicTests: XCTestCase {
    func testUnreadCountBelowLastReadWhenScrolledUp() {
        let ids = ["a", "b", "c", "d"]
        XCTAssertEqual(ChatScrollLogic.unreadCountBelowLastRead(messageIDs: ids, lastReadMessageId: "b"), 2)
        XCTAssertEqual(ChatScrollLogic.unreadCountBelowLastRead(messageIDs: ids, lastReadMessageId: "d"), 0)
        XCTAssertEqual(ChatScrollLogic.unreadCountBelowLastRead(messageIDs: ids, lastReadMessageId: nil), 4)
    }

    func testScrollAppearDecisionInitialOpen() {
        let scrollState = ConversationScrollState.initial
        let decision = ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: 3,
            newestMessageID: "m3",
            messageIDs: ["m1", "m2", "m3"],
            preserveScrollViewOffset: false
        )
        guard case .reposition(.scrollToBottom(let animated)) = decision else {
            return XCTFail("expected initial scroll to bottom")
        }
        XCTAssertFalse(animated)
    }

    func testScrollAppearDecisionPinnedTabReturnIsSilent() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = true
        scrollState.isMountedScrollGeometryVerified = true
        scrollState.lastKnownMessageCount = 3
        scrollState.lastKnownNewestMessageId = "m3"
        scrollState.lastReadMessageId = "m3"

        let decision = ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: 3,
            newestMessageID: "m3",
            messageIDs: ["m1", "m2", "m3"],
            preserveScrollViewOffset: false
        )
        XCTAssertEqual(decision, .noReposition)
    }

    func testScrollAppearDecisionFreshPinnedGeometryMustVerifyBottom() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = true
        scrollState.isMountedScrollGeometryVerified = false
        scrollState.lastKnownMessageCount = 3
        scrollState.lastKnownNewestMessageId = "m3"
        scrollState.lastReadMessageId = "m3"

        let decision = ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: 3,
            newestMessageID: "m3",
            messageIDs: ["m1", "m2", "m3"],
            preserveScrollViewOffset: false
        )

        guard case .reposition(.scrollToBottom(let animated)) = decision else {
            return XCTFail("expected fresh mounted geometry to verify bottom")
        }
        XCTAssertFalse(animated)
    }

    func testChangedStreamWithUnverifiedPinnedGeometryScrollsToBottom() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = true
        scrollState.isMountedScrollGeometryVerified = false
        scrollState.lastReadMessageId = "m3"
        scrollState.lastKnownMessageCount = 2
        scrollState.lastKnownNewestMessageId = "m3"

        let decision = ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: 3,
            newestMessageID: "m3",
            messageIDs: ["m1", "m2", "m3"],
            preserveScrollViewOffset: true
        )

        guard case .reposition(.scrollToBottom(let animated)) = decision else {
            return XCTFail("expected changed stream to verify untrusted pinned geometry")
        }
        XCTAssertFalse(animated)
    }

    func testScrollAppearDecisionRestoresAnchorWhenScrolledUp() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = false
        scrollState.hasUserScrollAnchor = true
        scrollState.lastVisibleMessageId = "m2"
        scrollState.lastKnownMessageCount = 3
        scrollState.lastKnownNewestMessageId = "m3"

        let decision = ChatScrollLogic.scrollAppearDecision(
            scrollState: scrollState,
            messageCount: 3,
            newestMessageID: "m3",
            messageIDs: ["m1", "m2", "m3"],
            preserveScrollViewOffset: false
        )
        guard case .reposition(.restore(let anchorId, let anchor)) = decision else {
            return XCTFail("expected restore")
        }
        XCTAssertEqual(anchorId, "m2")
        XCTAssertEqual(anchor, .center)
    }

    func testShouldDeferRestoreIntentDuringSettle() {
        var scrollState = ConversationScrollState.initial
        scrollState.isSettlingScrollPosition = true
        XCTAssertTrue(ChatScrollLogic.shouldDeferRestoreIntent(scrollState: scrollState))

        scrollState.isSettlingScrollPosition = false
        scrollState.pendingScrollIntent = .scrollToBottom(animated: false)
        XCTAssertTrue(ChatScrollLogic.shouldDeferRestoreIntent(scrollState: scrollState))
    }

    func testUserActionAlwaysBumpsIntentRevision() {
        var scrollState = ConversationScrollState.initial
        scrollState.intentRevision = 2

        let revision = ChatScrollLogic.applyUserScrollIntent(.scrollToBottom(animated: true), to: &scrollState)
        XCTAssertEqual(revision, 3)
        XCTAssertEqual(scrollState.intentRevision, 3)
        XCTAssertEqual(scrollState.pendingScrollIntentSource, .userAction)
        XCTAssertEqual(scrollState.pendingScrollIntent, .scrollToBottom(animated: true))
        XCTAssertNil(scrollState.intentAppearToken)
        XCTAssertFalse(scrollState.hasUserScrollAnchor)
        XCTAssertNil(scrollState.lastVisibleMessageId)

        scrollState.hasUserScrollAnchor = true
        scrollState.lastVisibleMessageId = "m1"
        _ = ChatScrollLogic.applyUserScrollIntent(.scrollToMessage(messageId: "m2", animated: true), to: &scrollState)
        XCTAssertEqual(scrollState.intentRevision, 4)
        XCTAssertTrue(scrollState.hasUserScrollAnchor)
        XCTAssertEqual(scrollState.lastVisibleMessageId, "m1")
    }

    func testReplyQuoteJumpUsesUserActionScrollIntent() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.lastAppearDecision = .noReposition

        _ = ChatScrollLogic.applyUserScrollIntent(
            .scrollToMessage(messageId: "quoted-parent", animated: true),
            to: &scrollState
        )

        XCTAssertEqual(scrollState.pendingScrollIntentSource, .userAction)
        XCTAssertEqual(
            scrollState.pendingScrollIntent,
            .scrollToMessage(messageId: "quoted-parent", animated: true)
        )
    }

    func testSquadFetchTTLSkipsNetworkWhenCacheFresh() {
        let now = Date()
        XCTAssertFalse(
            ChatScrollLogic.squadShouldRefreshFromNetwork(
                messagesEmpty: false,
                lastFetchedAt: now,
                now: now
            )
        )
    }

    func testSquadFetchTTLAlwaysRefreshesWhenEmpty() {
        let now = Date()
        XCTAssertTrue(
            ChatScrollLogic.squadShouldRefreshFromNetwork(
                messagesEmpty: true,
                lastFetchedAt: now,
                now: now
            )
        )
    }

    func testSquadFetchTTLRefreshesWhenStale() {
        let now = Date()
        let stale = now.addingTimeInterval(-(SquadChatFetchPolicy.refreshTTL + 1))
        XCTAssertTrue(
            ChatScrollLogic.squadShouldRefreshFromNetwork(
                messagesEmpty: false,
                lastFetchedAt: stale,
                now: now
            )
        )
    }

    func testSquadNetworkRefreshKeepsFullHistoryWhenOnlyJoinSystemMessageVisible() {
        let historyStart = Date(timeIntervalSince1970: 1_700_000_000)
        let joinDate = Date(timeIntervalSince1970: 1_800_000_000)
        let history = [
            SquadRefreshTestMessage(id: "old-1", createdAt: historyStart),
            SquadRefreshTestMessage(id: "old-2", createdAt: historyStart.addingTimeInterval(60)),
        ]
        let joinOnly = [SquadRefreshTestMessage(id: "join", createdAt: joinDate)]
        let reconciled = history + joinOnly

        let result = ChatScrollLogic.squadMessagesAfterNetworkRefresh(
            reconciled: reconciled,
            visibleMessages: joinOnly,
            hasUserScrollAnchor: false,
            hasMoreOlderMessages: false,
            initialPageSize: 50,
            createdAt: \.createdAt
        )

        XCTAssertEqual(result.map(\.id), reconciled.map(\.id))
    }

    func testSquadNetworkRefreshKeepsFullHistoryWhenScrollAnchorWithShortTranscript() {
        let historyStart = Date(timeIntervalSince1970: 1_700_000_000)
        let joinDate = Date(timeIntervalSince1970: 1_800_000_000)
        let history = [
            SquadRefreshTestMessage(id: "old-1", createdAt: historyStart),
            SquadRefreshTestMessage(id: "old-2", createdAt: historyStart.addingTimeInterval(60)),
        ]
        let joinOnly = [SquadRefreshTestMessage(id: "join", createdAt: joinDate)]
        let reconciled = history + joinOnly

        let result = ChatScrollLogic.squadMessagesAfterNetworkRefresh(
            reconciled: reconciled,
            visibleMessages: joinOnly,
            hasUserScrollAnchor: true,
            hasMoreOlderMessages: false,
            initialPageSize: 50,
            createdAt: \.createdAt
        )

        XCTAssertEqual(result.map(\.id), reconciled.map(\.id))
    }

    func testSquadNetworkRefreshForceFullTranscriptIgnoresScrollAnchor() {
        let older = Date(timeIntervalSince1970: 1_700_000_000)
        let visibleStart = Date(timeIntervalSince1970: 1_750_000_000)
        let newest = Date(timeIntervalSince1970: 1_800_000_000)
        let visible = (0..<50).map { index in
            SquadRefreshTestMessage(
                id: "visible-\(index)",
                createdAt: visibleStart.addingTimeInterval(TimeInterval(index))
            )
        }
        let reconciled = [
            SquadRefreshTestMessage(id: "older", createdAt: older),
        ] + visible

        let result = ChatScrollLogic.squadMessagesAfterNetworkRefresh(
            reconciled: reconciled,
            visibleMessages: visible,
            hasUserScrollAnchor: true,
            hasMoreOlderMessages: true,
            initialPageSize: 50,
            forceFullTranscript: true,
            createdAt: \.createdAt
        )

        XCTAssertEqual(result.map(\.id), reconciled.map(\.id))
    }

    func testSquadNetworkRefreshPreservesVisibleWindowWhenUserScrolledUp() {
        let older = Date(timeIntervalSince1970: 1_700_000_000)
        let visibleStart = Date(timeIntervalSince1970: 1_750_000_000)
        let newest = Date(timeIntervalSince1970: 1_800_000_000)
        let visible = (0..<50).map { index in
            SquadRefreshTestMessage(
                id: "visible-\(index)",
                createdAt: index == 0
                    ? visibleStart
                    : visibleStart.addingTimeInterval(TimeInterval(index))
            )
        }
        let reconciled = [
            SquadRefreshTestMessage(id: "older", createdAt: older),
        ] + visible

        let result = ChatScrollLogic.squadMessagesAfterNetworkRefresh(
            reconciled: reconciled,
            visibleMessages: visible,
            hasUserScrollAnchor: true,
            hasMoreOlderMessages: true,
            initialPageSize: 50,
            createdAt: \.createdAt
        )

        XCTAssertEqual(result.map(\.id), visible.map(\.id))
    }

    func testSquadFetchPolicyRevalidatesTruncatedJoinHead() {
        let now = Date()
        XCTAssertTrue(
            SquadChatFetchPolicy.shouldRefreshFromNetwork(
                lastFetchedAt: now,
                messagesEmpty: false,
                messageCount: 2,
                lastNetworkFetchCount: nil,
                transcriptStartsWithMemberJoinedSystemMessage: true,
                now: now
            )
        )
    }

    func testSquadFetchPolicyRevalidatesWhenLocalCountShrinksBelowLastFetch() {
        let now = Date()
        XCTAssertTrue(
            SquadChatFetchPolicy.shouldRefreshFromNetwork(
                lastFetchedAt: now,
                messagesEmpty: false,
                messageCount: 2,
                lastNetworkFetchCount: 50,
                now: now
            )
        )
    }

    func testShouldHideTranscriptDuringRestoreIntent() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.pendingScrollIntent = .restore(anchorMessageId: "m2", anchor: .center)
        XCTAssertTrue(
            ChatScrollLogic.shouldHideTranscriptForScrollSettle(
                isLoadingMessages: false,
                messagesEmpty: false,
                scrollState: scrollState
            )
        )
    }

    func testShouldHideTranscriptDuringInitialSettleWithCachedMessages() {
        var scrollState = ConversationScrollState.initial
        scrollState.isSettlingScrollPosition = true
        scrollState.pendingScrollIntent = .scrollToBottom(animated: false)
        XCTAssertFalse(
            ChatScrollLogic.shouldHideTranscriptForScrollSettle(
                isLoadingMessages: false,
                messagesEmpty: false,
                scrollState: scrollState
            )
        )
    }

    func testShouldShowLoadingOverlayOnlyWhileFetchingEmptyTranscript() {
        var scrollState = ConversationScrollState.initial
        scrollState.isSettlingScrollPosition = true
        XCTAssertTrue(
            ChatScrollLogic.shouldShowScrollSettleLoadingOverlay(
                isLoadingMessages: true,
                messagesEmpty: true,
                scrollState: scrollState
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldShowScrollSettleLoadingOverlay(
                isLoadingMessages: false,
                messagesEmpty: false,
                scrollState: scrollState
            )
        )
    }

    func testShouldNotHideTranscriptWhenPinnedReturnIsSilent() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = true
        scrollState.pendingScrollIntent = nil
        scrollState.isSettlingScrollPosition = false
        XCTAssertFalse(
            ChatScrollLogic.shouldHideTranscriptForScrollSettle(
                isLoadingMessages: false,
                messagesEmpty: false,
                scrollState: scrollState
            )
        )
    }

    func testJumpToLatestAffordanceHiddenBeforeInitialScroll() {
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: false,
                isScrollLayoutReady: true,
                distanceFromBottom: 500,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: false
            )
        )
    }

    func testJumpToLatestAffordanceHiddenWhenLayoutNotReady() {
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: false,
                distanceFromBottom: 500,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: false
            )
        )
    }

    func testJumpToLatestAffordanceHiddenWhenPinnedToBottom() {
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: true,
                distanceFromBottom: 0,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: false
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: true,
                distanceFromBottom: 132,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: false
            )
        )
    }

    func testJumpToLatestAffordanceShownWhenScrolledUp() {
        XCTAssertTrue(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: true,
                distanceFromBottom: 200,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: false
            )
        )
    }

    func testJumpToLatestAffordanceHiddenDuringScrollSettle() {
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: true,
                distanceFromBottom: 500,
                messagesEmpty: false,
                isHidingTranscriptForScrollSettle: true
            )
        )
    }

    func testJumpToLatestAffordanceHiddenWhenTranscriptEmpty() {
        XCTAssertFalse(
            ChatScrollLogic.shouldShowJumpToLatestAffordance(
                didInitialScrollToBottom: true,
                isScrollLayoutReady: true,
                distanceFromBottom: 500,
                messagesEmpty: true,
                isHidingTranscriptForScrollSettle: false
            )
        )
    }

    func testNewMessageScrollIntentNotDiscardedAfterSilentAppear() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.lastAppearDecision = .noReposition
        scrollState.pendingScrollIntent = .scrollToBottom(animated: true)
        scrollState.pendingScrollIntentSource = .newMessage

        XCTAssertNil(
            ChatScrollLogic.pendingScrollIntentDiscardReason(
                scrollState: scrollState,
                source: .newMessage,
                intent: .scrollToBottom(animated: true)
            )
        )
    }

    func testAppearScrollIntentDiscardedAfterSilentAppear() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.lastAppearDecision = .noReposition
        scrollState.pendingScrollIntent = .scrollToBottom(animated: false)
        scrollState.pendingScrollIntentSource = .appear

        XCTAssertEqual(
            ChatScrollLogic.pendingScrollIntentDiscardReason(
                scrollState: scrollState,
                source: .appear,
                intent: .scrollToBottom(animated: false)
            ),
            .afterNoRepositionAppear
        )
    }

    func testPaginationRestoreIntentNotDiscardedAfterSilentAppear() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.lastAppearDecision = .noReposition
        scrollState.isPinnedToBottom = false
        scrollState.hasUserScrollAnchor = true
        let intent = ScrollIntent.restore(anchorMessageId: "m2", anchor: .top)

        XCTAssertNil(
            ChatScrollLogic.pendingScrollIntentDiscardReason(
                scrollState: scrollState,
                source: .pagination,
                intent: intent
            )
        )
    }

    func testNewMessageScrollIntentDiscardedWithStaleAppearToken() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.lastAppearDecision = .noReposition
        scrollState.appearToken = 2
        scrollState.intentAppearToken = 1
        scrollState.pendingScrollIntent = .scrollToBottom(animated: true)
        scrollState.pendingScrollIntentSource = .newMessage

        XCTAssertEqual(
            ChatScrollLogic.pendingScrollIntentDiscardReason(
                scrollState: scrollState,
                source: .newMessage,
                intent: .scrollToBottom(animated: true)
            ),
            .staleAppearToken
        )
    }

    func testRestoreIntentDiscardedWhilePinned() {
        var scrollState = ConversationScrollState.initial
        scrollState.didInitialScrollToBottom = true
        scrollState.isPinnedToBottom = true
        let intent = ScrollIntent.restore(anchorMessageId: "m2", anchor: .center)

        XCTAssertEqual(
            ChatScrollLogic.pendingScrollIntentDiscardReason(
                scrollState: scrollState,
                source: .pagination,
                intent: intent
            ),
            .restoreWhilePinned
        )
    }

    func testShouldRecordUserScrollAnchorOnlyWhenInteracting() {
        XCTAssertFalse(
            ChatScrollLogic.shouldRecordUserScrollAnchor(
                wasPinnedToBottom: true,
                isPinnedToBottom: false,
                isScrollUserInteracting: false
            )
        )
        XCTAssertTrue(
            ChatScrollLogic.shouldRecordUserScrollAnchor(
                wasPinnedToBottom: true,
                isPinnedToBottom: false,
                isScrollUserInteracting: true
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldRecordUserScrollAnchor(
                wasPinnedToBottom: false,
                isPinnedToBottom: false,
                isScrollUserInteracting: true
            )
        )
    }

    func testPinnedStateTrustRequiresMountedGeometryVerification() {
        var scrollState = ConversationScrollState.initial
        scrollState.isPinnedToBottom = true
        scrollState.isMountedScrollGeometryVerified = false
        XCTAssertFalse(ChatScrollLogic.shouldTrustPinnedStateForNoReposition(scrollState: scrollState))

        scrollState.isMountedScrollGeometryVerified = true
        XCTAssertTrue(ChatScrollLogic.shouldTrustPinnedStateForNoReposition(scrollState: scrollState))

        scrollState.isPinnedToBottom = false
        XCTAssertFalse(ChatScrollLogic.shouldTrustPinnedStateForNoReposition(scrollState: scrollState))
    }

    func testBottomScrollIntentHandledOnlyWhenLayoutReadyAndBottomVisible() {
        XCTAssertFalse(
            ChatScrollLogic.shouldMarkBottomScrollIntentHandled(
                isLayoutReady: false,
                distanceFromBottom: 0
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldMarkBottomScrollIntentHandled(
                isLayoutReady: true,
                distanceFromBottom: 80
            )
        )
        XCTAssertTrue(
            ChatScrollLogic.shouldMarkBottomScrollIntentHandled(
                isLayoutReady: true,
                distanceFromBottom: 10
            )
        )
    }

    func testMaxContentOffsetAccountsForBottomInset() {
        let contentHeight: CGFloat = 1_200
        let boundsHeight: CGFloat = 700
        let bottomInset: CGFloat = 280
        let targetOffset = ChatUIKitScrollPinning.maxContentOffsetY(
            contentHeight: contentHeight,
            boundsHeight: boundsHeight,
            adjustedInsetTop: 0,
            adjustedInsetBottom: bottomInset
        )

        XCTAssertEqual(targetOffset, 780)
        XCTAssertEqual(
            ChatUIKitScrollPinning.distanceFromBottom(
                contentHeight: contentHeight,
                boundsHeight: boundsHeight,
                adjustedInsetTop: 0,
                adjustedInsetBottom: bottomInset,
                contentOffsetY: targetOffset
            ),
            0
        )
    }

    func testShouldCompensateScrollForBottomInsetChangeWhenPinned() {
        XCTAssertFalse(
            ChatScrollLogic.shouldCompensateScrollForBottomInsetChange(
                previousInsetBottom: nil,
                newInsetBottom: 120,
                lastPublishedDistance: 0,
                isScrollUserInteracting: false,
                isDecelerating: false
            )
        )
        XCTAssertTrue(
            ChatScrollLogic.shouldCompensateScrollForBottomInsetChange(
                previousInsetBottom: 80,
                newInsetBottom: 120,
                lastPublishedDistance: 12,
                isScrollUserInteracting: false,
                isDecelerating: false
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldCompensateScrollForBottomInsetChange(
                previousInsetBottom: 80,
                newInsetBottom: 120,
                lastPublishedDistance: 12,
                isScrollUserInteracting: true,
                isDecelerating: false
            )
        )
        XCTAssertFalse(
            ChatScrollLogic.shouldCompensateScrollForBottomInsetChange(
                previousInsetBottom: 80,
                newInsetBottom: 80.2,
                lastPublishedDistance: 0,
                isScrollUserInteracting: false,
                isDecelerating: false
            )
        )
    }
}

private struct SquadRefreshTestMessage {
    let id: String
    let createdAt: Date
}
