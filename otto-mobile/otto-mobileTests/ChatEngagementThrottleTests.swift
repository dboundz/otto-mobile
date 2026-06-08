import XCTest
@testable import otto_mobile

@MainActor
final class ChatEngagementThrottleTests: XCTestCase {
    private let key = "squad:test-circle"
    private let t0 = Date(timeIntervalSince1970: 1_700_000_000)

    override func setUp() {
        super.setUp()
        ChatEngagementThrottle.resetForTesting()
    }

    override func tearDown() {
        ChatEngagementThrottle.resetForTesting()
        super.tearDown()
    }

    func testQuietThreadFirstAlertUses15sCooldown() {
        ChatEngagementThrottle.recordFullAlert(forConversationKey: key, pushType: "circle.chat.new_message", now: t0)

        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 0)
        XCTAssertEqual(
            ChatEngagementThrottle.cooldownUntilForTesting(conversationKey: key),
            t0.addingTimeInterval(15)
        )
    }

    func testBurstEscalatesTierAfterSilentUpdates() {
        ChatEngagementThrottle.recordFullAlert(forConversationKey: key, pushType: "circle.chat.new_message", now: t0)
        ChatEngagementThrottle.recordSilentUpdate(
            forConversationKey: key,
            pushType: "circle.chat.new_message",
            now: t0.addingTimeInterval(1)
        )
        let secondAlert = t0.addingTimeInterval(16)
        ChatEngagementThrottle.recordFullAlert(
            forConversationKey: key,
            pushType: "circle.chat.new_message",
            now: secondAlert
        )

        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 1)
        XCTAssertEqual(
            ChatEngagementThrottle.cooldownUntilForTesting(conversationKey: key),
            secondAlert.addingTimeInterval(15)
        )
    }

    func testIdle60sAtMaxTierDecaysOneStep() {
        var now = t0
        for _ in 0 ..< 3 {
            ChatEngagementThrottle.recordFullAlert(
                forConversationKey: key,
                pushType: "circle.chat.new_message",
                now: now
            )
            ChatEngagementThrottle.recordSilentUpdate(
                forConversationKey: key,
                pushType: "circle.chat.new_message",
                now: now.addingTimeInterval(1)
            )
            now = now.addingTimeInterval(16)
            ChatEngagementThrottle.recordFullAlert(
                forConversationKey: key,
                pushType: "circle.chat.new_message",
                now: now
            )
            ChatEngagementThrottle.recordSilentUpdate(
                forConversationKey: key,
                pushType: "circle.chat.new_message",
                now: now.addingTimeInterval(1)
            )
            now = now.addingTimeInterval(1)
        }
        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 3)

        let afterIdle = now.addingTimeInterval(61)
        let outcome = ChatEngagementThrottle.evaluateSquadMessage(
            circleId: "test-circle",
            pushType: "circle.chat.new_message",
            focusedChatCircleId: nil,
            now: afterIdle
        )
        XCTAssertEqual(outcome, .fullAlert)
        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 2)
    }

    func testMentionDoesNotEscalateTier() {
        ChatEngagementThrottle.recordFullAlert(forConversationKey: key, pushType: "circle.chat.new_message", now: t0)
        ChatEngagementThrottle.recordSilentUpdate(
            forConversationKey: key,
            pushType: "circle.chat.new_message",
            now: t0.addingTimeInterval(1)
        )
        ChatEngagementThrottle.recordFullAlert(
            forConversationKey: key,
            pushType: "circle.chat.new_message",
            now: t0.addingTimeInterval(16)
        )
        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 1)

        let mentionAt = t0.addingTimeInterval(32)
        ChatEngagementThrottle.recordFullAlert(forConversationKey: key, pushType: "circle.chat.mention", now: mentionAt)

        XCTAssertEqual(ChatEngagementThrottle.tierIndexForTesting(conversationKey: key), 1)
        XCTAssertEqual(
            ChatEngagementThrottle.cooldownUntilForTesting(conversationKey: key),
            mentionAt.addingTimeInterval(15)
        )
    }
}
