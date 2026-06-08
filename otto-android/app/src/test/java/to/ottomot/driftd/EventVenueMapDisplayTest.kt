package to.ottomot.driftd

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.event.isEligibleForMapDisplay
import to.ottomot.driftd.core.network.dto.EventDto

class EventVenueMapDisplayTest {
    @Test
    fun eligibleForMapDisplay_futureWithinHorizonWithCoordinates() {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        val event =
            event(
                startsAt = "2026-05-30T12:00:00Z",
                endsAt = "2026-05-30T16:00:00Z",
            )
        assertTrue(isEligibleForMapDisplay(event, now = now))
    }

    @Test
    fun eligibleForMapDisplay_futureBeyondHorizonHidden() {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        val event =
            event(
                startsAt = "2026-06-10T12:00:00Z",
                endsAt = "2026-06-10T16:00:00Z",
            )
        assertFalse(isEligibleForMapDisplay(event, now = now))
    }

    @Test
    fun eligibleForMapDisplay_inProgressUntilEnd() {
        val now = Instant.parse("2026-05-23T14:00:00Z")
        val event =
            event(
                startsAt = "2026-05-23T12:00:00Z",
                endsAt = "2026-05-23T18:00:00Z",
            )
        assertTrue(isEligibleForMapDisplay(event, now = now))
    }

    @Test
    fun eligibleForMapDisplay_endedHidden() {
        val now = Instant.parse("2026-05-23T20:00:00Z")
        val event =
            event(
                startsAt = "2026-05-23T12:00:00Z",
                endsAt = "2026-05-23T18:00:00Z",
            )
        assertFalse(isEligibleForMapDisplay(event, now = now))
    }

    @Test
    fun eligibleForMapDisplay_withoutCoordinatesHidden() {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        val event =
            event(
                startsAt = "2026-05-30T12:00:00Z",
                endsAt = "2026-05-30T16:00:00Z",
                withCoordinates = false,
            )
        assertFalse(isEligibleForMapDisplay(event, now = now))
    }

    private fun event(
        startsAt: String,
        endsAt: String?,
        withCoordinates: Boolean = true,
    ) = EventDto(
        id = "event1",
        slug = null,
        visibility = "public",
        circleId = null,
        name = "Test Event",
        description = null,
        startsAt = startsAt,
        endsAt = endsAt,
        address = null,
        location =
            if (withCoordinates) {
                to.ottomot.driftd.core.network.dto.EventLocationDto(
                    type = "Point",
                    coordinates = listOf(-122.4194, 37.7749),
                )
            } else {
                null
            },
        bannerImage = null,
        rsvpCounts = null,
        contactsGoing = emptyList(),
        contactsRsvps = emptyList(),
        createdByUserId = null,
        currentUserRsvp = null,
        currentUserCheckIn = null,
    )
}
