package to.ottomot.driftd.map

import kotlin.math.cos
import kotlin.math.pow
import to.ottomot.driftd.core.network.dto.PresenceMemberDto

internal fun clusteringThresholdMeters(
    zoom: Float,
    latitudeCenterDegrees: Double,
): Float {
    // Mirrors MapScreen.zoomAwareGroupingThresholdMeters() (~7% of approximate visible height).
    val latSafe =
        latitudeCenterDegrees
            .takeIf { it.isFinite() }
            ?: 0.0
    val metersPerPixelMaxZoom =
        156543.03392 * kotlin.math.abs(cos(Math.toRadians(latSafe))).coerceAtLeast(0.2)
    val approximateScreenHeightPx = 640f
    val metersPerPixel = metersPerPixelMaxZoom / 2f.pow(zoom.coerceIn(4f, 21f))
    val visibleHeight = metersPerPixel * approximateScreenHeightPx
    val derived = (visibleHeight * 0.07f).toDouble()
    return derived.coerceIn(8.0, 46.0).toFloat()
}

internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val earthRadius = 6371000.0
    val r1 = Math.toRadians(lat1)
    val r2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h =
        kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(r1) * kotlin.math.cos(r2) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    return (earthRadius * c).toFloat()
}

internal const val PRESENCE_WORLD_VIEW_MIN_SPREAD_METERS = 8_000_000.0
internal const val PRESENCE_WORLD_VIEW_ZOOM = 1.25
internal const val PRESENCE_WORLD_VIEW_LAT = 20.0
internal const val PRESENCE_WORLD_VIEW_LNG = 0.0

internal fun maxPairwiseHaversineMeters(coordinates: List<Pair<Double, Double>>): Double {
    if (coordinates.size < 2) return 0.0
    var maxDistance = 0.0
    for (i in coordinates.indices) {
        for (j in i + 1 until coordinates.size) {
            val (lat1, lng1) = coordinates[i]
            val (lat2, lng2) = coordinates[j]
            maxDistance =
                maxOf(
                    maxDistance,
                    haversineMeters(lat1, lng1, lat2, lng2).toDouble(),
                )
        }
    }
    return maxDistance
}

internal fun presenceRequiresWorldView(coordinates: List<Pair<Double, Double>>): Boolean =
    maxPairwiseHaversineMeters(coordinates) > PRESENCE_WORLD_VIEW_MIN_SPREAD_METERS

internal data class PresenceProximityGroup(
    val members: List<PresenceMemberDto>,
    val anchorLat: Double,
    val anchorLng: Double,
) {
    val id: String get() = members.map { it.userId }.sorted().joinToString("|")
}

internal fun groupNearbyPresence(
    members: List<PresenceMemberDto>,
    thresholdMeters: Float,
    meUserId: String?,
): List<PresenceProximityGroup> {
    if (members.isEmpty()) return emptyList()

    val withCoords =
        members.mapNotNull { m ->
            val lat = m.lat ?: return@mapNotNull null
            val lng = m.lng ?: return@mapNotNull null
            Triple(m, lat, lng)
        }

    val groups = mutableListOf<MutableList<Triple<PresenceMemberDto, Double, Double>>>()
    for (item in withCoords) {
        val idx =
            groups.indexOfFirst { g ->
                g.any { (_, glat, glng) ->
                    haversineMeters(item.second, item.third, glat, glng) <= thresholdMeters
                }
            }
        if (idx >= 0) groups[idx].add(item)
        else groups.add(mutableListOf(item))
    }

    return groups.map { g ->
        val membersOnly = g.map { it.first }.distinctBy { it.userId }
        val peers = g.filter { meUserId == null || it.first.userId != meUserId }
        val coordsForCenter = if (peers.isEmpty()) g else peers
        val anchorLat = coordsForCenter.map { it.second }.average()
        val anchorLng = coordsForCenter.map { it.third }.average()
        PresenceProximityGroup(
            members = membersOnly,
            anchorLat = anchorLat,
            anchorLng = anchorLng,
        )
    }.sortedBy { it.id }
}
