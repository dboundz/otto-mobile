package to.ottomot.driftd.core.event

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import to.ottomot.driftd.core.network.dto.EventDto

sealed class EventListSectionId {
    data object Today : EventListSectionId()

    data object ThisWeek : EventListSectionId()

    data object NextWeek : EventListSectionId()

    data object ThisMonth : EventListSectionId()

    data class CalendarMonth(val year: Int, val month: Int) : EventListSectionId()
}

data class EventListSectionGroup(
    val section: EventListSectionId,
    val items: List<EventDto>,
) {
    val id: String
        get() =
            when (section) {
                EventListSectionId.Today -> "today"
                EventListSectionId.ThisWeek -> "this-week"
                EventListSectionId.NextWeek -> "next-week"
                EventListSectionId.ThisMonth -> "this-month"
                is EventListSectionId.CalendarMonth -> "month-${section.year}-${section.month}"
            }
}

enum class EventListSectionedPresentation {
    Featured,
    Compact,
}

fun sortEventsForSectionedList(events: List<EventDto>): List<EventDto> =
    events.sortedBy { eventStartsAtSortKey(it) }

fun groupEventsByListSection(
    events: List<EventDto>,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<EventListSectionGroup> {
    val sorted = sortEventsForSectionedList(events)
    val groupsBySection = LinkedHashMap<EventListSectionId, MutableList<EventDto>>()

    for (event in sorted) {
        val startInstant = event.startsAt?.let(::parseEventInstant) ?: continue
        val section = eventListSection(startInstant = startInstant, now = now, zone = zone, locale = locale)
        groupsBySection.getOrPut(section) { mutableListOf() }.add(event)
    }

    return groupsBySection.entries.mapNotNull { (section, items) ->
        if (items.isEmpty()) null else EventListSectionGroup(section = section, items = items)
    }
}

fun eventListSection(
    startInstant: Instant,
    now: Instant,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
): EventListSectionId {
    val startDate = startInstant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    if (startDate == today) {
        return EventListSectionId.Today
    }

    val weekFields = WeekFields.of(locale)
    val startWeek = startDate.get(weekFields.weekOfWeekBasedYear())
    val startWeekYear = startDate.get(weekFields.weekBasedYear())
    val todayWeek = today.get(weekFields.weekOfWeekBasedYear())
    val todayWeekYear = today.get(weekFields.weekBasedYear())
    if (startWeek == todayWeek && startWeekYear == todayWeekYear) {
        return EventListSectionId.ThisWeek
    }

    val nextWeekDate = today.plusWeeks(1)
    val nextWeek = nextWeekDate.get(weekFields.weekOfWeekBasedYear())
    val nextWeekYear = nextWeekDate.get(weekFields.weekBasedYear())
    if (startWeek == nextWeek && startWeekYear == nextWeekYear) {
        return EventListSectionId.NextWeek
    }

    if (startDate.year == today.year && startDate.month == today.month) {
        return EventListSectionId.ThisMonth
    }

    return EventListSectionId.CalendarMonth(year = startDate.year, month = startDate.monthValue)
}

fun eventListCalendarMonthTitle(
    year: Int,
    month: Int,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): String {
    val nowYear = now.atZone(zone).year
    val pattern = if (year == nowYear) "MMMM" else "MMMM yyyy"
    val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
    val date = java.time.LocalDate.of(year, month, 1)
    return formatter.format(date).uppercase(locale)
}
