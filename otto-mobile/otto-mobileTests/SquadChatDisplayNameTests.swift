import CoreLocation
import SwiftUI
import XCTest
@testable import otto_mobile

final class SquadChatDisplayNameTests: XCTestCase {
    private let member = FriendLocation(
        id: "69ea30907f5fc4a635a65dce",
        name: "Darren Test",
        avatarName: "Darren Test",
        avatarUrl: nil,
        car: "Unknown Car",
        clubRole: "Member",
        lastRun: "Recent drive",
        coordinate: .init(latitude: 0, longitude: 0),
        speedMph: 0,
        isOnline: false,
        isActive: false,
        accentColor: .blue,
        movementMode: .unknown
    )

    func testMissingSenderDisplayNameFallsBackToSquadMember() {
        let sender = CircleChatMessageDTO.SenderDTO(
            id: member.id,
            displayName: "",
            avatarUrl: nil,
            mapAccentKey: nil
        )
        let name = SquadChatDisplayName.resolveSquadMemberDisplayName(
            userId: member.id,
            sender: sender,
            circleMembers: [member]
        )
        XCTAssertEqual(name, "Darren Test")
    }

    func testPlaceholderSomeoneFallsBackToSquadMember() {
        let sender = CircleChatMessageDTO.SenderDTO(
            id: member.id,
            displayName: "Someone",
            avatarUrl: nil,
            mapAccentKey: nil
        )
        let name = SquadChatDisplayName.resolveSquadMemberDisplayName(
            userId: member.id,
            sender: sender,
            circleMembers: [member]
        )
        XCTAssertEqual(name, "Darren Test")
    }

    func testSenderDisplayNameDecoderDoesNotDefaultToSomeone() throws {
        let json = """
        {"_id":"69ea30907f5fc4a635a65dce","displayName":null}
        """.data(using: .utf8)!
        let sender = try JSONDecoder().decode(CircleChatMessageDTO.SenderDTO.self, from: json)
        XCTAssertEqual(sender.displayName, "")
    }

    func testWirePayloadSomeoneIsIgnoredForDisplayResolution() {
        let sender = CircleChatMessageDTO.SenderDTO(
            id: member.id,
            displayName: "Someone",
            avatarUrl: nil,
            mapAccentKey: nil
        )
        XCTAssertNil(SquadChatDisplayName.normalized(sender.displayName))
        let resolved = SquadChatDisplayName.resolveSquadMemberDisplayName(
            userId: member.id,
            sender: sender,
            circleMembers: [member]
        )
        XCTAssertEqual(resolved, "Darren Test")
    }

    func testEmptySenderDisplayNameUsesRosterNotSomeoneFallbackWhenMemberKnown() {
        let sender = CircleChatMessageDTO.SenderDTO(
            id: member.id,
            displayName: "",
            avatarUrl: nil,
            mapAccentKey: nil
        )
        let resolved = SquadChatDisplayName.resolveSquadMemberDisplayName(
            userId: member.id,
            sender: sender,
            circleMembers: [member]
        )
        XCTAssertEqual(resolved, "Darren Test")
        XCTAssertNotEqual(resolved, SquadChatDisplayName.fallback)
    }

    func testOttoUserIdsEqualIgnoresCase() {
        XCTAssertTrue(ottoUserIdsEqual("AbC123", "abc123"))
    }
}
