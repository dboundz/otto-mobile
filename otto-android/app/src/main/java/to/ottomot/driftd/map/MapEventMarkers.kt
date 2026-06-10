package to.ottomot.driftd.map

import androidx.compose.runtime.Composable
import to.ottomot.driftd.core.event.eventStartsAtSortKey
import to.ottomot.driftd.core.network.dto.EventDto

internal data class EventMapAnchorGroup(
    val id: String,
    val anchorLat: Double,
    val anchorLng: Double,
    val events: List<EventDto>,
)

internal fun eventProximityGroupsForMap(
    events: List<EventDto>,
    thresholdMeters: Double = 78.0,
): List<EventMapAnchorGroup> {
    if (events.isEmpty()) return emptyList()

    fun latLng(ev: EventDto): Pair<Double, Double>? {
        val c = ev.location?.coordinates ?: return null
        if (c.size < 2) return null
        val lng = c[0]
        val lat = c[1]
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    data class Row(
        val ev: EventDto,
        val lat: Double,
        val lng: Double,
    )

    val rows =
        events.mapNotNull { ev ->
            val ll = latLng(ev) ?: return@mapNotNull null
            Row(ev, ll.first, ll.second)
        }.sortedWith(compareBy<Row> { it.lat }.thenBy { it.lng }.thenBy { it.ev.id })

    val groups = mutableListOf<MutableList<Row>>()
    for (item in rows) {
        val idx =
            groups.indexOfFirst { group ->
                group.any { existing ->
                    to.ottomot.driftd.core.event.haversineMeters(
                        existing.lat,
                        existing.lng,
                        item.lat,
                        item.lng,
                    ) <= thresholdMeters
                }
            }
        if (idx >= 0) {
            groups[idx].add(item)
        } else {
            groups.add(mutableListOf(item))
        }
    }

    return groups.map { g ->
        val latAv = g.sumOf { it.lat } / g.size
        val lngAv = g.sumOf { it.lng } / g.size
        val evs =
            g.map { it.ev }.distinctBy { it.id }.sortedWith(compareBy { eventStartsAtSortKey(it) })
        EventMapAnchorGroup(
            id = evs.map { it.id }.sorted().joinToString("|"),
            anchorLat = latAv,
            anchorLng = lngAv,
            events = evs,
        )
    }
}

@Composable
internal fun OttoMapEventBeaconMarkerContent(
    group: EventMapAnchorGroup,
    isSelected: Boolean,
    pinScale: Float = 1f,
) {
    OttoMapEventMarkerContent(
        isSelected = isSelected,
        pinScale = pinScale,
    )
}
