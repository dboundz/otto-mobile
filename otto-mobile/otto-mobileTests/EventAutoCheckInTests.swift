import XCTest
@testable import otto_mobile

final class EventAutoCheckInTests: XCTestCase {
    private var now: Date { Self.date("2026-06-05T18:00:00Z") }

    func testIsInEventCheckInWindow_duringEvent() {
        let event = makeEvent(
            startsAt: Self.date("2026-06-05T17:00:00Z"),
            endsAt: Self.date("2026-06-05T21:00:00Z"),
            rsvp: "going"
        )
        XCTAssertTrue(event.isInEventCheckInWindow)
    }

    func testIsInEventCheckInWindow_beforeStart() {
        let reference = Self.date("2026-06-05T16:00:00Z")
        let event = makeEvent(
            startsAt: Self.date("2026-06-05T17:00:00Z"),
            endsAt: Self.date("2026-06-05T21:00:00Z"),
            rsvp: "going"
        )
        XCTAssertFalse(reference >= event.startsAt && reference <= event.eventCheckInWindowEnd)
    }

    func testEventGeoCoordinate_decodesGeoJSON() {
        let event = makeEvent(
            startsAt: now,
            endsAt: now.addingTimeInterval(7200),
            rsvp: "going",
            withLocation: true
        )
        XCTAssertNotNil(event.eventGeoCoordinate)
    }

    func testHaversineWithinCheckInRadius() {
        let venueLat = 37.7749
        let venueLng = -122.4194
        let userLat = 37.7750
        let userLng = -122.4195
        let distance = Self.haversineMeters(
            lat1: userLat,
            lon1: userLng,
            lat2: venueLat,
            lon2: venueLng
        )
        XCTAssertLessThanOrEqual(distance, Double(AppState.eventCheckInRadiusMeters))
    }

    private static func haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let earthRadiusMeters = 6_371_000.0
        let r1 = lat1 * Double.pi / 180
        let r2 = lat2 * Double.pi / 180
        let dLat = (lat2 - lat1) * Double.pi / 180
        let dLon = (lon2 - lon1) * Double.pi / 180
        let a =
            sin(dLat / 2) * sin(dLat / 2) +
            cos(r1) * cos(r2) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private func makeEvent(
        startsAt: Date,
        endsAt: Date?,
        rsvp: String?,
        withLocation: Bool = true
    ) -> EventDTO {
        EventDTO(
            id: "event-auto-checkin-test",
            slug: nil,
            visibility: "public",
            eventType: "featured",
            circleId: nil,
            name: "Auto Check-In Test",
            description: nil,
            startsAt: startsAt,
            endsAt: endsAt,
            address: nil,
            location: withLocation ? decodedLocation() : nil,
            bannerImage: nil,
            rsvpCounts: nil,
            contactsGoing: [],
            contactsRsvps: nil,
            currentUserRsvp: rsvp,
            currentUserCheckIn: nil
        )
    }

    private func decodedLocation() -> EventDTO.LocationDTO {
        let data = Data(#"{"type":"Point","coordinates":[-122.4194,37.7749]}"#.utf8)
        return try! JSONDecoder().decode(EventDTO.LocationDTO.self, from: data)
    }

    private static func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)!
    }
}
