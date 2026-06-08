import XCTest
@testable import otto_mobile

final class EventMapDisplayTests: XCTestCase {
    private var now: Date { Self.date("2026-05-23T12:00:00Z") }

    func testEligibleForMapDisplay_futureWithinHorizon() {
        let event = makeEvent(
            startsAt: Self.date("2026-05-30T12:00:00Z"),
            endsAt: Self.date("2026-05-30T16:00:00Z"),
            withLocation: true
        )
        XCTAssertTrue(event.isEligibleForMapDisplay(at: now))
    }

    func testEligibleForMapDisplay_futureBeyondHorizonHidden() {
        let event = makeEvent(
            startsAt: Self.date("2026-06-10T12:00:00Z"),
            endsAt: Self.date("2026-06-10T16:00:00Z"),
            withLocation: true
        )
        XCTAssertFalse(event.isEligibleForMapDisplay(at: now))
    }

    func testEligibleForMapDisplay_inProgressUntilEnd() {
        let reference = Self.date("2026-05-23T14:00:00Z")
        let event = makeEvent(
            startsAt: Self.date("2026-05-23T12:00:00Z"),
            endsAt: Self.date("2026-05-23T18:00:00Z"),
            withLocation: true
        )
        XCTAssertTrue(event.isEligibleForMapDisplay(at: reference))
    }

    func testEligibleForMapDisplay_endedHidden() {
        let reference = Self.date("2026-05-23T20:00:00Z")
        let event = makeEvent(
            startsAt: Self.date("2026-05-23T12:00:00Z"),
            endsAt: Self.date("2026-05-23T18:00:00Z"),
            withLocation: true
        )
        XCTAssertFalse(event.isEligibleForMapDisplay(at: reference))
    }

    func testEligibleForMapDisplay_withoutCoordinatesHidden() {
        let event = makeEvent(
            startsAt: Self.date("2026-05-30T12:00:00Z"),
            endsAt: Self.date("2026-05-30T16:00:00Z"),
            withLocation: false
        )
        XCTAssertFalse(event.isEligibleForMapDisplay(at: now))
    }

    private func makeEvent(startsAt: Date, endsAt: Date?, withLocation: Bool) -> EventDTO {
        EventDTO(
            id: "event1",
            slug: nil,
            visibility: "public",
            eventType: "featured",
            circleId: nil,
            name: "Test Event",
            description: nil,
            startsAt: startsAt,
            endsAt: endsAt,
            address: nil,
            location: withLocation ? decodedLocation() : nil,
            bannerImage: nil,
            rsvpCounts: nil,
            contactsGoing: [],
            contactsRsvps: nil,
            currentUserRsvp: nil,
            currentUserCheckIn: nil
        )
    }

    private func decodedLocation() -> EventDTO.LocationDTO {
        let data = Data(#"{"type":"Point","coordinates":[-122.4194,37.7749]}"#.utf8)
        return try! JSONDecoder().decode(EventDTO.LocationDTO.self, from: data)
    }

    private static func date(_ raw: String) -> Date {
        ISO8601DateFormatter().date(from: raw)!
    }
}
