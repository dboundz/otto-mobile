import XCTest
@testable import otto_mobile

final class EventListSectionGroupingTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        calendar.firstWeekday = 2
        return calendar
    }

    private var now: Date { Self.date("2026-05-26T12:00:00Z", calendar: calendar) }

    func testSortEventsForSectionedListAscending() {
        let later = makeEvent(id: "b", startsAt: Self.date("2026-05-28T12:00:00Z", calendar: calendar))
        let earlier = makeEvent(id: "a", startsAt: Self.date("2026-05-27T12:00:00Z", calendar: calendar))
        let sorted = sortEventsForSectionedList([later, earlier])
        XCTAssertEqual(sorted.map(\.id), ["a", "b"])
    }

    func testTodaySectionOnlyWhenEventsExist() {
        let todayEvent = makeEvent(id: "today", startsAt: Self.date("2026-05-26T18:00:00Z", calendar: calendar))
        let tomorrowEvent = makeEvent(id: "week", startsAt: Self.date("2026-05-27T12:00:00Z", calendar: calendar))

        let withToday = groupEventsByListSection([todayEvent, tomorrowEvent], now: now, calendar: calendar)
        XCTAssertEqual(withToday.map(\.section), [.today, .thisWeek])
        XCTAssertEqual(withToday.first?.items.map(\.id), ["today"])

        let withoutToday = groupEventsByListSection([tomorrowEvent], now: now, calendar: calendar)
        XCTAssertEqual(withoutToday.map(\.section), [.thisWeek])
        XCTAssertFalse(withoutToday.contains(where: { $0.section == .today }))
    }

    func testThisWeekExcludesToday() {
        let tomorrow = makeEvent(id: "tomorrow", startsAt: Self.date("2026-05-27T12:00:00Z", calendar: calendar))
        let groups = groupEventsByListSection([tomorrow], now: now, calendar: calendar)
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].section, .thisWeek)
        XCTAssertTrue(groups[0].title.contains("WEEK"))
    }

    func testNextWeekUsesCalendarWeek() {
        let nextWeekEvent = makeEvent(id: "next", startsAt: Self.date("2026-06-02T12:00:00Z", calendar: calendar))
        let groups = groupEventsByListSection([nextWeekEvent], now: now, calendar: calendar)
        XCTAssertEqual(groups.map(\.section), [.nextWeek])
    }

    func testThisMonthAfterNextWeek() {
        let earlyNow = Self.date("2026-05-05T12:00:00Z", calendar: calendar)
        let thisMonthEvent = makeEvent(id: "month", startsAt: Self.date("2026-05-25T12:00:00Z", calendar: calendar))
        let groups = groupEventsByListSection([thisMonthEvent], now: earlyNow, calendar: calendar)
        XCTAssertTrue(groups.contains(where: { $0.section == .thisMonth }))
    }

    func testFutureMonthUsesMonthHeader() {
        let julyEvent = makeEvent(id: "july", startsAt: Self.date("2026-07-03T12:00:00Z", calendar: calendar))
        let groups = groupEventsByListSection([julyEvent], now: now, calendar: calendar)
        XCTAssertEqual(groups.map(\.section), [.calendarMonth(year: 2026, month: 7)])
        XCTAssertEqual(
            groups[0].title,
            eventListSectionTitle(for: .calendarMonth(year: 2026, month: 7), now: now, calendar: calendar)
        )
    }

    func testFutureYearIncludesYearInHeader() {
        let januaryEvent = makeEvent(id: "jan", startsAt: Self.date("2027-01-15T12:00:00Z", calendar: calendar))
        let groups = groupEventsByListSection([januaryEvent], now: now, calendar: calendar)
        XCTAssertEqual(groups.map(\.section), [.calendarMonth(year: 2027, month: 1)])
        XCTAssertTrue(groups[0].title.contains("2027"))
    }

    private func makeEvent(id: String, startsAt: Date) -> EventDTO {
        EventDTO(
            id: id,
            slug: nil,
            visibility: "public",
            eventType: "featured",
            circleId: nil,
            name: "Test Event",
            description: nil,
            startsAt: startsAt,
            endsAt: nil,
            address: nil,
            location: nil,
            bannerImage: nil,
            rsvpCounts: nil,
            contactsGoing: [],
            contactsRsvps: nil,
            currentUserRsvp: nil,
            currentUserCheckIn: nil
        )
    }

    private static func date(_ iso: String, calendar: Calendar) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = calendar.timeZone
        return formatter.date(from: iso)!
    }
}
