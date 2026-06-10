package to.ottomot.driftd

import com.mapbox.geojson.Point
import to.ottomot.driftd.core.network.dto.CircleChatDriveAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatDriveRoutePointDto
import to.ottomot.driftd.core.network.dto.CircleChatRouteAttachmentDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.DriveRouteDto
import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import kotlin.math.max
import kotlin.math.min

data class RouteMapPoint(
    val id: String,
    val lat: Double,
    val lng: Double,
    val markerType: String?,
    val index: Int,
)

internal fun lineCoordinatesFromRoutePoints(
    roadCoordinates: List<RoutePointDto>?,
    routePoints: List<RoutePointDto>?,
): List<Point> {
    val road =
        roadCoordinates.orEmpty().map { Point.fromLngLat(it.lng, it.lat) }
    if (road.size >= 2) return road
    val path =
        routePoints.orEmpty()
            .filter { (it.markerType ?: "path") == "path" }
            .map { Point.fromLngLat(it.lng, it.lat) }
    if (path.size >= 2) return path
    return routePoints.orEmpty().map { Point.fromLngLat(it.lng, it.lat) }
}

internal fun lineCoordinatesFromChatAttachment(attachment: CircleChatDriveAttachmentDto): List<Point> {
    fun toPoints(coords: List<CircleChatDriveRoutePointDto>) =
        coords.map { Point.fromLngLat(it.lng, it.lat) }
    val road = toPoints(attachment.roadCoordinates)
    if (road.size >= 2) return road
    val path =
        attachment.routePoints
            .filter { (it.type ?: "path") == "path" }
            .let { toPoints(it) }
    if (path.size >= 2) return path
    return toPoints(attachment.routePoints)
}

internal fun lineCoordinatesFromRouteChatAttachment(attachment: CircleChatRouteAttachmentDto): List<Point> {
    fun toPoints(coords: List<CircleChatDriveRoutePointDto>) =
        coords.map { Point.fromLngLat(it.lng, it.lat) }
    val road = toPoints(attachment.roadCoordinates)
    if (road.size >= 2) return road
    val path =
        attachment.routePoints
            .filter { (it.type ?: "path") == "path" }
            .let { toPoints(it) }
    if (path.size >= 2) return path
    return toPoints(attachment.routePoints)
}

internal fun lineCoordinatesFromDrive(drive: DriveDto): List<Point> =
    lineCoordinatesFromRoutePoints(drive.route?.roadCoordinates, drive.route?.points)

internal fun hasDriveRoutePreview(
    drive: DriveDto,
    pathSamples: List<DrivePathSample> = emptyList(),
): Boolean {
    if (pathSamples.size >= 2) return true
    if (DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) return true
    return lineCoordinatesFromDrive(drive).size >= 2
}

internal fun lineCoordinatesFromSavedRoute(route: SavedRouteDto): List<Point> =
    lineCoordinatesFromRoutePoints(route.roadCoordinates, route.points)

internal fun mapPointsFromRoutePoints(
    routePoints: List<RoutePointDto>?,
    idPrefix: String,
): List<RouteMapPoint> {
    var waypointIndex = 0
    return routePoints.orEmpty().mapIndexedNotNull { offset, point ->
        val type = point.markerType ?: "path"
        if (type == "path") return@mapIndexedNotNull null
        val index =
            if (type == "waypoint" || type == "stop") {
                val i = waypointIndex
                waypointIndex += 1
                i
            } else {
                offset
            }
        RouteMapPoint(
            id = "$idPrefix-$offset",
            lat = point.lat,
            lng = point.lng,
            markerType = type,
            index = index,
        )
    }
}

internal fun mapPointsFromChatAttachment(
    attachment: CircleChatDriveAttachmentDto,
    idPrefix: String,
): List<RouteMapPoint> {
    var waypointIndex = 0
    return attachment.routePoints.mapIndexedNotNull { offset, point ->
        val type = point.type ?: "path"
        if (type == "path") return@mapIndexedNotNull null
        val index =
            if (type == "waypoint" || type == "stop") {
                val i = waypointIndex
                waypointIndex += 1
                i
            } else {
                offset
            }
        RouteMapPoint(
            id = "$idPrefix-$offset",
            lat = point.lat,
            lng = point.lng,
            markerType = type,
            index = index,
        )
    }
}

internal fun mapPointsFromRouteChatAttachment(
    attachment: CircleChatRouteAttachmentDto,
    idPrefix: String,
): List<RouteMapPoint> {
    var waypointIndex = 0
    return attachment.routePoints.mapIndexedNotNull { offset, point ->
        val type = point.type ?: "path"
        if (type == "path") return@mapIndexedNotNull null
        val index =
            if (type == "waypoint" || type == "stop") {
                val i = waypointIndex
                waypointIndex += 1
                i
            } else {
                offset
            }
        RouteMapPoint(
            id = "$idPrefix-$offset",
            lat = point.lat,
            lng = point.lng,
            markerType = type,
            index = index,
        )
    }
}

internal fun mapPointsFromDrive(drive: DriveDto, idPrefix: String): List<RouteMapPoint> =
    mapPointsFromRoutePoints(drive.route?.points, idPrefix)

internal fun mapPointsStartFinishFromDrive(drive: DriveDto, idPrefix: String): List<RouteMapPoint> =
    mapPointsFromDrive(drive, idPrefix).filter { it.markerType == "start" || it.markerType == "finish" }

/** Start/finish markers at the actual driven GPS endpoints (matches iOS trail map intent). */
internal fun mapPointsTrailEndpointsFromSamples(
    pathSamples: List<DrivePathSample>,
    idPrefix: String,
): List<RouteMapPoint> {
    val samples =
        pathSamples.filter { it.lat.isFinite() && it.lng.isFinite() }
    if (samples.size < 2) return emptyList()
    val first = samples.first()
    val last = samples.last()
    return listOf(
        RouteMapPoint("$idPrefix-trail-start", first.lat, first.lng, "start", 0),
        RouteMapPoint("$idPrefix-trail-finish", last.lat, last.lng, "finish", 1),
    )
}

internal fun cameraPointsForRoutePreview(
    line: List<Point>,
    markers: List<RouteMapPoint>,
): List<Point> {
    val all =
        buildList {
            addAll(line)
            markers.forEach { add(Point.fromLngLat(it.lng, it.lat)) }
        }
    return all.distinctBy { "${it.latitude()}_${it.longitude()}" }
}

internal fun boundsPaddingForPoints(points: List<Point>): Pair<Point, Point>? {
    if (points.isEmpty()) return null
    var minLat = points.first().latitude()
    var maxLat = minLat
    var minLng = points.first().longitude()
    var maxLng = minLng
    for (p in points.drop(1)) {
        minLat = min(minLat, p.latitude())
        maxLat = max(maxLat, p.latitude())
        minLng = min(minLng, p.longitude())
        maxLng = max(maxLng, p.longitude())
    }
    val padLat = max((maxLat - minLat) * 0.12, 0.002)
    val padLng = max((maxLng - minLng) * 0.12, 0.002)
    return Pair(
        Point.fromLngLat(minLng - padLng, minLat - padLat),
        Point.fromLngLat(maxLng + padLng, maxLat + padLat),
    )
}

internal data class DriveSummaryFlavor(
    val label: String,
    val iconKind: DriveFlavorIconKind,
)

internal enum class DriveFlavorIconKind {
    LateNight,
    Morning,
    Afternoon,
    Evening,
    Generic,
}

internal fun driveFlavorFromIso(endTimeIso: String?): DriveSummaryFlavor {
    val hour =
        runCatching {
            java.time.Instant.parse(endTimeIso?.trim().orEmpty())
                .atZone(java.time.ZoneId.systemDefault())
                .hour
        }.getOrElse { 12 }
    return when (hour) {
        in 22..23, in 0..4 ->
            DriveSummaryFlavor("Late Night Run", DriveFlavorIconKind.LateNight)
        in 5..11 ->
            DriveSummaryFlavor("Morning Run", DriveFlavorIconKind.Morning)
        in 12..16 ->
            DriveSummaryFlavor("Afternoon Run", DriveFlavorIconKind.Afternoon)
        else ->
            DriveSummaryFlavor("Evening Run", DriveFlavorIconKind.Evening)
    }
}

internal fun driveFlavorFromTypeLabel(label: String): DriveFlavorIconKind {
    val lower = label.trim().lowercase()
    return when {
        lower.contains("night") -> DriveFlavorIconKind.LateNight
        lower.contains("morning") -> DriveFlavorIconKind.Morning
        lower.contains("afternoon") -> DriveFlavorIconKind.Afternoon
        lower.contains("evening") -> DriveFlavorIconKind.Evening
        else -> DriveFlavorIconKind.Generic
    }
}

internal fun profileDrivesForDisplay(drives: List<DriveDto>): List<DriveDto> =
    drives.filter { drive ->
        val completed = drive.status.equals("completed", ignoreCase = true) || !drive.endTime.isNullOrBlank()
        val distance = drive.distanceMeters ?: 0.0
        completed && distance > 0 && isProfileEligibleDrive(drive)
    }

internal fun isProfileEligibleDrive(drive: DriveDto): Boolean {
    if (drive.route != null) return true
    if (drive.sharingAudience.equals("onlyMe", ignoreCase = true)) return true
    if ((drive.pointsCount ?: 0) >= 2) return true
    if (drive.sharingAudience.equals("circles", ignoreCase = true)) return false
    return true
}
