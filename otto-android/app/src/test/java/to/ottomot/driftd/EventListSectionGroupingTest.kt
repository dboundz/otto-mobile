package to.ottomot.driftd

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.event.EventListSectionId
import to.ottomot.driftd.core.event.groupEventsByListSection
import to.ottomot.driftd.core.event.sortEventsForSectionedList
import to.ottomot.driftd.core.network.dto.EventDto

class EventListSectionGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val locale = Locale.US
    private val now = Instant.parse("2026-05-26T12:00:00Z")

    @Test
    fun sortEventsForSectionedListAscending() {
        val later = event(id = "b", startsAt = "2026-05-28T12:00:00Z")
        val earlier = event(id = "a", startsAt = "2026-05-27T12:00:00Z")
        val sorted = sortEventsForSectionedList(listOf(later, earlier))
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun todaySectionOnlyWhenEventsExist() {
        val todayEvent = event(id = "today", startsAt = "2026-05-26T18:00:00Z")
        val tomorrowEvent = event(id = "week", startsAt = "2026-05-27T12:00:00Z")

        val withToday = groupEventsByListSection(listOf(todayEvent, tomorrowEvent), now = now, zone = zone, locale = locale)
        assertEquals(listOf(EventListSectionId.Today, EventListSectionId.ThisWeek), withToday.map { it.section })

        val withoutToday = groupEventsByListSection(listOf(tomorrowEvent), now = now, zone = zone, locale = locale)
        assertEquals(listOf(EventListSectionId.ThisWeek), withoutToday.map { it.section })
        assertFalse(withoutToday.any { it.section is EventListSectionId.Today })
    }

    @Test
    fun nextWeekUsesCalendarWeek() {
        val nextWeekEvent = event(id = "next", startsAt = "2026-06-02T12:00:00Z")
        val groups = groupEventsByListSection(listOf(nextWeekEvent), now = now, zone = zone, locale = locale)
        assertEquals(listOf(EventListSectionId.NextWeek), groups.map { it.section })
    }

    @Test
    fun futureMonthUsesMonthHeader() {
        val julyEvent = event(id = "july", startsAt = "2026-07-03T12:00:00Z")
        val groups = groupEventsByListSection(listOf(julyEvent), now = now, zone = zone, locale = locale)
        assertEquals(listOf(EventListSectionId.CalendarMonth(2026, 7)), groups.map { it.section })
    }

    @Test
    fun futureYearUsesYearInSectionId() {
        val januaryEvent = event(id = "jan", startsAt = "2027-01-15T12:00:00Z")
        val groups = groupEventsByListSection(listOf(januaryEvent), now = now, zone = zone, locale = locale)
        assertEquals(listOf(EventListSectionId.CalendarMonth(2027, 1)), groups.map { it.section })
    }

    @Test
    fun thisMonthAfterNextWeek() {
        val earlyNow = Instant.parse("2026-05-05T12:00:00Z")
        val thisMonthEvent = event(id = "month", startsAt = "2026-05-25T12:00:00Z")
        val groups = groupEventsByListSection(listOf(thisMonthEvent), now = earlyNow, zone = zone, locale = locale)
        assertTrue(groups.any { it.section is EventListSectionId.ThisMonth })
    }

    private fun event(
        id: String,
        startsAt: String,
    ) = EventDto(
        id = id,
        slug = null,
        visibility = "public",
        circleId = null,
        name = "Test Event",
        description = null,
        startsAt = startsAt,
        endsAt = null,
        address = null,
        location = null,
        bannerImage = null,
        rsvpCounts = null,
        contactsGoing = emptyList(),
        contactsRsvps = emptyList(),
        createdByUserId = null,
        currentUserRsvp = null,
        currentUserCheckIn = null,
    )
}
