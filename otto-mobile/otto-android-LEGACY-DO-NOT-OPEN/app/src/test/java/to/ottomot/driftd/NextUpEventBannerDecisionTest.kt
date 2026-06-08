package to.ottomot.driftd

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.CircleMemberDto
import to.ottomot.driftd.core.network.dto.EventContactRsvpDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalDto
import to.ottomot.driftd.core.network.dto.UserDto

class NextUpEventBannerDecisionTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-05-09T16:00:00Z")

    @Test
    fun showsEligibleEventWithoutDismissal() {
        val event = event("evt1", "2026-05-10T20:00:00Z")

        assertSame(event, visibleNextUpEvent(event, emptyList(), null, now, zone))
    }

    @Test
    fun hidesBeforeEventDayAfterPreEventDismissal() {
        val event = event("evt1", "2026-05-10T20:00:00Z")

        assertNull(
            visibleNextUpEvent(
                event,
                listOf(dismissal("evt1", NextUpDismissalPreEvent)),
                null,
                now,
                zone,
            ),
        )
    }

    @Test
    fun reappearsOnEventDayAfterPreEventDismissal() {
        val event = event("evt1", "2026-05-10T20:00:00Z")
        val eventDayNow = Instant.parse("2026-05-10T14:00:00Z")

        assertSame(
            event,
            visibleNextUpEvent(
                event,
                listOf(dismissal("evt1", NextUpDismissalPreEvent)),
                null,
                eventDayNow,
                zone,
            ),
        )
    }

    @Test
    fun hidesAfterEventDayDismissal() {
        val event = event("evt1", "2026-05-10T20:00:00Z")
        val eventDayNow = Instant.parse("2026-05-10T14:00:00Z")

        assertNull(
            visibleNextUpEvent(
                event,
                listOf(dismissal("evt1", NextUpDismissalEventDay)),
                null,
                eventDayNow,
                zone,
            ),
        )
    }

    @Test
    fun doesNotShowAfterEventEnd() {
        val event =
            event(
                id = "evt1",
                startsAt = "2026-05-09T12:00:00Z",
                endsAt = "2026-05-09T13:00:00Z",
            )

        assertNull(visibleNextUpEvent(event, emptyList(), null, now, zone))
    }

    @Test
    fun autoHideDoesNotPersistDismissalContext() {
        val event = event("evt1", "2026-05-10T20:00:00Z")

        assertNull(visibleNextUpEvent(event, emptyList(), "evt1", now, zone))
    }

    @Test
    fun resolvesDismissalContextUsingCalendarDay() {
        val event = event("evt1", "2026-05-10T03:30:00Z")
        val sameLocalDay = Instant.parse("2026-05-10T02:00:00Z")

        assertEquals(NextUpDismissalEventDay, nextUpDismissalContextForEvent(event, sameLocalDay, zone))
    }

    @Test
    fun nextUpPinRejectsOtherCircleScopedEvent() {
        val otherSquad = event("evt1", "2026-05-10T20:00:00Z").copy(circleId = "circleB")
        assertFalse(eventIsNextUpPinnedForCircle(otherSquad, "circleA"))
    }

    @Test
    fun nextUpPinAcceptsMatchingCircleScopedEvent() {
        val mine = event("evt1", "2026-05-10T20:00:00Z").copy(circleId = "circleA")
        assertTrue(eventIsNextUpPinnedForCircle(mine, "circleA"))
    }

    @Test
    fun nextUpPinAcceptsPublicWhenCircleIdNull() {
        val pub =
            event("evt1", "2026-05-10T20:00:00Z").copy(
                circleId = null,
                visibility = "public",
            )
        assertTrue(eventIsNextUpPinnedForCircle(pub, "circleA"))
    }

    @Test
    fun squadNextUpQualifiesThisSquadCircleEventEvenWithZeroGoing() {
        val c = squadCircle("circleA", listOf("u1"))
        val e =
            event("evt1", "2026-05-10T20:00:00Z").copy(
                visibility = "circle",
                circleId = "circleA",
                contactsGoing = emptyList(),
                contactsRsvps = emptyList(),
            )
        assertTrue(eventQualifiesForSquadNextUpPin(e, c, null))
    }

    @Test
    fun squadNextUpRejectsPublicWithOnlyMemberRsvp() {
        val c = squadCircle("circleA", listOf("u1", "u2"))
        val pub =
            event("evt1", "2026-05-10T20:00:00Z").copy(
                circleId = null,
                visibility = "public",
                contactsGoing = listOf(user("u2")),
                contactsRsvps = emptyList(),
                currentUserRsvp = "going",
            )
        assertFalse(eventQualifiesForSquadNextUpPin(pub, c, user("u1")))
    }

    @Test
    fun squadNextUpAcceptsPublicWhenOfficiallyAttached() {
        val c = squadCircle("circleA", listOf("u1", "u2"))
        val pub =
            event("evt1", "2026-05-10T20:00:00Z").copy(
                circleId = null,
                visibility = "public",
                isOfficialForCircle = true,
            )
        assertTrue(eventQualifiesForSquadNextUpPin(pub, c, user("u1")))
    }

    @Test
    fun squadNextUpAcceptsPublicFromAttachedSquadsList() {
        val c = squadCircle("circleA", listOf("u1"))
        val pub =
            event("evt1", "2026-05-10T20:00:00Z").copy(
                circleId = null,
                visibility = "public",
                attachedSquads =
                    listOf(
                        to.ottomot.driftd.core.network.dto.EventAttachedSquadDto(
                            id = "circleA",
                            name = "Squad",
                            photoUrl = null,
                            addedByUserId = "u1",
                            addedByDisplayName = "Darren",
                        ),
                    ),
            )
        assertTrue(eventQualifiesForSquadNextUpPin(pub, c, null))
    }

    private fun squadCircle(id: String, memberIds: List<String>) =
        CircleDto(
            id = id,
            name = "Squad",
            description = null,
            ownerId = memberIds.firstOrNull() ?: "x",
            members = memberIds.map { CircleMemberDto(userId = it, role = "member") },
            photoUrl = null,
        )

    private fun user(id: String) =
        UserDto(
            id = id,
            displayName = "U",
            handle = "u",
            avatarUrl = null,
            mapAccentKey = null,
            phoneNumber = null,
            vehicle = null,
            lastPresenceAt = null,
            autoEventCheckInEnabled = null,
            sharingSafetyDisclaimerAcknowledged = null,
            showPublicGoingEventsOnProfile = null,
            driveStatsVisibility = null,
        )

    private fun event(
        id: String,
        startsAt: String,
        endsAt: String? = null,
    ) = EventDto(
        id = id,
        slug = null,
        visibility = "circle",
        circleId = "circle1",
        name = "Event",
        description = null,
        startsAt = startsAt,
        endsAt = endsAt,
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

    private fun dismissal(
        eventId: String,
        context: String,
    ) = NextUpEventDismissalDto(
        userId = "user1",
        squadId = "circle1",
        circleId = "circle1",
        eventId = eventId,
        dismissedAt = "2026-05-09T16:00:00Z",
        dismissedContext = context,
    )
}
