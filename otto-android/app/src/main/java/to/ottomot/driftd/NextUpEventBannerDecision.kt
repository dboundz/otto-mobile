package to.ottomot.driftd

import java.time.Instant
import java.time.ZoneId
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.event.parseEventInstant
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalDto
import to.ottomot.driftd.core.network.dto.UserDto

internal const val NextUpDismissalPreEvent = "pre_event"
internal const val NextUpDismissalEventDay = "event_day"

/**
 * Squad chat "next up" pin must not show another circle's squad-scoped event.
 * Events with no [EventDto.circleId] (e.g. public) may appear in any squad's feed.
 */
internal fun eventIsNextUpPinnedForCircle(event: EventDto, circleIdRaw: String): Boolean {
    val circleId = circleIdRaw.trim()
    if (circleId.isEmpty()) return false
    val eventCircle = event.circleId?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    return eventCircle.equals(circleId, ignoreCase = true)
}

/** Count of squad members (including the signed-in user when RSVP is going) with RSVP "going". */
internal fun squadMembersGoingCountForNextUpPin(
    event: EventDto,
    circle: CircleDto,
    meUser: UserDto?,
): Int {
    val squadMemberIds =
        circle.members.orEmpty().map { it.userId.trim() }.filter { it.isNotEmpty() }.toSet()
    if (squadMemberIds.isEmpty()) return 0
    val goingIds = mutableSetOf<String>()
    event.contactsGoing.orEmpty().forEach { u ->
        val id = u.id.trim()
        if (id.isNotEmpty() && id in squadMemberIds) goingIds.add(id)
    }
    event.contactsRsvps.orEmpty().forEach { rsvp ->
        if (!rsvp.status.equals("going", ignoreCase = true)) return@forEach
        val uid = rsvp.user?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        if (uid in squadMemberIds) goingIds.add(uid)
    }
    val meId = meUser?.id?.trim()?.takeIf { it.isNotEmpty() }
    if (meId != null &&
        meId in squadMemberIds &&
        event.currentUserRsvp?.equals("going", ignoreCase = true) == true
    ) {
        goingIds.add(meId)
    }
    return goingIds.size
}

/** Official for this squad: native squad event or explicit admin attachment (not member RSVP). */
internal fun isEventOfficialForSquad(event: EventDto, circleIdRaw: String): Boolean {
    val circleId = circleIdRaw.trim()
    if (circleId.isEmpty()) return false
    if (event.isOfficialForCircle == true) return true
    val isThisSquadCircleEvent =
        event.visibility?.trim()?.equals("circle", ignoreCase = true) == true &&
            event.circleId?.trim()?.equals(circleId, ignoreCase = true) == true
    if (isThisSquadCircleEvent) return true
    return event.attachedSquads.orEmpty().any { it.id.trim().equals(circleId, ignoreCase = true) }
}

/**
 * Squad chat "Next up" pin: only official squad events (squad-created or admin-attached public).
 */
internal fun eventQualifiesForSquadNextUpPin(
    event: EventDto,
    circle: CircleDto,
    @Suppress("UNUSED_PARAMETER") meUser: UserDto?,
): Boolean {
    val circleId = circle.id.trim()
    if (circleId.isEmpty()) return false
    if (!eventIsNextUpPinnedForCircle(event, circleId)) return false
    return isEventOfficialForSquad(event, circleId)
}

internal fun isNextUpEventDay(
    event: EventDto,
    now: Instant,
    zone: ZoneId,
): Boolean {
    val start = event.startsAt?.let { parseEventInstant(it) } ?: return false
    return start.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
}

internal fun nextUpDismissalContextForEvent(
    event: EventDto,
    now: Instant,
    zone: ZoneId,
): String =
    if (isNextUpEventDay(event, now, zone)) {
        NextUpDismissalEventDay
    } else {
        NextUpDismissalPreEvent
    }

internal fun visibleNextUpEvent(
    event: EventDto?,
    dismissals: List<NextUpEventDismissalDto>,
    autoHiddenEventId: String?,
    now: Instant,
    zone: ZoneId,
): EventDto? {
    val candidate = event ?: return null
    val eventEnd = eventCheckInEndsAtInstant(candidate)
    if (eventEnd != null && eventEnd.isBefore(now)) return null
    if (candidate.id == autoHiddenEventId) return null
    val context = dismissals.lastOrNull { it.eventId == candidate.id }?.dismissedContext
    return when (context) {
        NextUpDismissalPreEvent ->
            if (isNextUpEventDay(candidate, now, zone)) candidate else null
        NextUpDismissalEventDay -> null
        else -> candidate
    }
}
