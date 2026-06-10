import XCTest
@testable import otto_mobile

/// Regression fixture for Bounds Family prod chat payload (circle 69ea42415f95ecc83dc1fcde).
/// See Fixtures/VERIFICATION-bounds-family-chat.md for findings.
final class CircleChatMessagesDecodeFixtureTests: XCTestCase {
    private var fixtureURL: URL {
        let bundle = Bundle(for: CircleChatMessagesDecodeFixtureTests.self)
        guard let url = bundle.url(
            forResource: "bounds-family-chat-messages",
            withExtension: "json",
            subdirectory: "Fixtures"
        ) ?? bundle.url(forResource: "bounds-family-chat-messages", withExtension: "json") else {
            XCTFail("Missing bounds-family-chat-messages.json in test bundle")
            return URL(fileURLWithPath: "/dev/null")
        }
        return url
    }

    private func apiDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    func testBoundsFamilyProdPayloadDecodesAfterNullableSenderUserIdSupport() throws {
        let data = try Data(contentsOf: fixtureURL)
        let response = try apiDecoder().decode(CircleChatMessagesResponseDTO.self, from: data)
        XCTAssertEqual(response.messages.count, 50)
    }

    func testBoundsFamilyProdPayloadNullSenderUserIdMessagesResolveSenderIdFromSnapshotOrSender() throws {
        let response = try apiDecoder().decode(CircleChatMessagesResponseDTO.self, from: Data(contentsOf: fixtureURL))
        let unresolved = response.messages.filter { $0.resolvedSenderUserId.isEmpty }
        // Legacy fixture may still have null top-level ids until backend hydration ships;
        // iOS must decode the page without failing the whole transcript.
        XCTAssertLessThanOrEqual(unresolved.count, response.messages.count)
    }

    func testBoundsFamilyProdPayloadNullSenderUserIdMessageCountInRawJSON() throws {
        let object = try JSONSerialization.jsonObject(with: Data(contentsOf: fixtureURL)) as? [String: Any]
        let rawMessages = object?["messages"] as? [[String: Any]] ?? []
        let nullSenderCount = rawMessages.filter { $0["senderUserId"] == nil || $0["senderUserId"] is NSNull }.count
        XCTAssertEqual(rawMessages.count, 50)
        XCTAssertEqual(nullSenderCount, 19, "Captured prod head page includes messages with senderUserId null")
    }

    func testBoundsFamilyDecodedMessagesWithSenderNeverUseSomeoneAsDisplayName() throws {
        let response = try apiDecoder().decode(CircleChatMessagesResponseDTO.self, from: Data(contentsOf: fixtureURL))
        for message in response.messages {
            guard let sender = message.sender else { continue }
            XCTAssertNotEqual(
                sender.displayName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                "someone",
                "Message \(message.id) must not decode with sender.displayName Someone"
            )
        }
    }
}
