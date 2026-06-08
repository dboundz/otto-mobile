package to.ottomot.driftd.core.event

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import to.ottomot.driftd.core.network.dto.EventDto

private val flexibleIsoFormatter = DateTimeFormatter.ISO_DATE_TIME

fun parseEventInstant(raw: String): Instant? =
    try {
        Instant.from(flexibleIsoFormatter.parse(raw.trim()))
    } catch (_: DateTimeException) {
        try {
            Instant.parse(raw.trim())
        } catch (_: Exception) {
            null
        }
    }

fun eventStartsAtSortKey(event: EventDto): Instant =
    event.startsAt?.let { parseEventInstant(it) } ?: Instant.MAX

/** Matches iOS [eventListSort]: in-progress first, then upcoming by start; started events newest-first. */
fun compareEventsForMainList(
    lhs: EventDto,
    rhs: EventDto,
): Int {
    val now = Instant.now()
    val lhsStart = eventStartsAtSortKey(lhs)
    val rhsStart = eventStartsAtSortKey(rhs)
    val lhsStarted = !lhsStart.isAfter(now)
    val rhsStarted = !rhsStart.isAfter(now)
    if (lhsStarted != rhsStarted) {
        return if (lhsStarted) -1 else 1
    }
    if (lhsStarted && rhsStarted) {
        return rhsStart.compareTo(lhsStart)
    }
    return lhsStart.compareTo(rhsStart)
}

fun eventCheckInEndsAtInstant(event: EventDto): Instant? {
    val start = event.startsAt?.let { parseEventInstant(it) } ?: return null
    val explicitEnd = event.endsAt?.let { parseEventInstant(it) }
    return explicitEnd ?: start.plusSeconds(7200)
}

fun isWithinEventCheckInWindow(
    event: EventDto,
    now: Instant = Instant.now(),
): Boolean {
    val start = event.startsAt?.let { parseEventInstant(it) } ?: return false
    val end = eventCheckInEndsAtInstant(event) ?: return false
    return !now.isBefore(start) && !now.isAfter(end)
}

/** iOS parity: pinned squad-chat event subtitle replaces the date with green status when in window / today / soon. */
fun squadChatPinnedEventHighlightPhrase(
    event: EventDto,
    now: Instant,
    zone: ZoneId,
): String? {
    val start = event.startsAt?.let { parseEventInstant(it) } ?: return null
    val end = eventCheckInEndsAtInstant(event) ?: return null
    if (!now.isBefore(start) && !now.isAfter(end)) {
        return "Happening now."
    }
    if (now.isBefore(start)) {
        val minutesUntil = ChronoUnit.MINUTES.between(now, start).coerceAtLeast(0)
        if (minutesUntil > 0 && minutesUntil <= 5 * 60) {
            val h = (minutesUntil / 60).toInt()
            val m = (minutesUntil % 60).toInt()
            return when {
                h > 0 -> "Starts in ${h}h ${m}m"
                m > 0 -> "Starts in ${m}m"
                else -> "Starts in 1m"
            }
        }
        if (start.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return "Happening today."
        }
    }
    return null
}

fun eventHasVenueCoordinates(event: EventDto): Boolean {
    val c = event.location?.coordinates ?: return false
    if (c.size < 2) return false
    val lng = c[0]
    val lat = c[1]
    return lat in -90.0..90.0 && lng in -180.0..180.0
}

/** Map layer beacons: has coordinates, not ended, and starts within the display horizon. */
fun isEligibleForMapDisplay(
    event: EventDto,
    now: Instant = Instant.now(),
    horizonDays: Long = 14,
): Boolean {
    if (!eventHasVenueCoordinates(event)) return false
    val end = eventCheckInEndsAtInstant(event) ?: return false
    if (!end.isAfter(now)) return false
    val start = eventStartsAtSortKey(event)
    if (start == Instant.MAX) return false
    val horizon = now.plus(horizonDays, ChronoUnit.DAYS)
    return !start.isAfter(horizon)
}

/** Matches iOS [EventDetailView] manual check-in distance gate. */
const val EVENT_CHECK_IN_RADIUS_METERS = 150.0

/** GeoJSON order: longitude, latitude. */
fun openMeetLocationInMaps(
    context: Context,
    label: String,
    lat: Double?,
    lng: Double?,
) {
    val uri =
        if (lat != null && lng != null && lat.isFinite() && lng.isFinite()) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(label)}")
        }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
    }
}

fun eventVenueLatLng(event: EventDto): Pair<Double, Double>? {
    val c = event.location?.coordinates ?: return null
    if (c.size < 2 || !eventHasVenueCoordinates(event)) return null
    val lng = c[0]
    val lat = c[1]
    return lat to lng
}

fun haversineMeters(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val r1 = Math.toRadians(lat1)
    val r2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lng2 - lng1)
    val a =
        kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(r1) * kotlin.math.cos(r2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusMeters * c
}
