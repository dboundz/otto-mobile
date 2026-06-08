package to.ottomot.driftd.routebuilder.engine

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import to.ottomot.driftd.core.event.haversineMeters

/** Lat/lng pair (`first` = latitude, `second` = longitude). */
typealias RouteLatLng = Pair<Double, Double>

val RouteLatLng.lat: Double get() = first
val RouteLatLng.lng: Double get() = second

data class RoutePolylineProjection(
    val coordinate: RouteLatLng,
    val distanceMeters: Double,
    val arcLengthMeters: Double,
    val segmentIndex: Int,
    val segmentBearingDegrees: Double,
)

/**
 * Polyline geometry helpers ported from iOS [RouteMapGeometry] (MapKit MKMapPoint projection).
 */
object RoutePolylineGeometry {
    private const val WORLD_WIDTH = 268_435_456.0

    private data class MapPoint(val x: Double, val y: Double)

    fun polylineTotalLength(lineCoordinates: List<RouteLatLng>): Double {
        if (lineCoordinates.size < 2) return 0.0
        var total = 0.0
        for (index in 0 until lineCoordinates.size - 1) {
            val start = lineCoordinates[index]
            val end = lineCoordinates[index + 1]
            total += segmentDistanceMeters(start, end)
        }
        return total
    }

    fun coordinateAtArcLength(
        targetArcLength: Double,
        lineCoordinates: List<RouteLatLng>,
    ): RouteLatLng? {
        if (lineCoordinates.size < 2 || targetArcLength < 0) return null
        var cumulativeDistance = 0.0

        for (index in 0 until lineCoordinates.size - 1) {
            val startCoordinate = lineCoordinates[index]
            val endCoordinate = lineCoordinates[index + 1]
            val start = mapPoint(startCoordinate)
            val end = mapPoint(endCoordinate)
            val segmentDistance = segmentDistanceMeters(startCoordinate, endCoordinate)
            if (segmentDistance <= 0) continue

            if (cumulativeDistance + segmentDistance >= targetArcLength) {
                val remaining = targetArcLength - cumulativeDistance
                val t = remaining / segmentDistance
                val segmentDX = end.x - start.x
                val segmentDY = end.y - start.y
                return coordinateFromMapPoint(
                    MapPoint(
                        x = start.x + segmentDX * t,
                        y = start.y + segmentDY * t,
                    ),
                )
            }
            cumulativeDistance += segmentDistance
        }

        if (kotlin.math.abs(targetArcLength - cumulativeDistance) <= 1.0) {
            return lineCoordinates.last()
        }
        return null
    }

    fun bearingBetween(from: RouteLatLng, to: RouteLatLng): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val deltaLon = Math.toRadians(to.lng - from.lng)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val radians = atan2(y, x)
        val degrees = Math.toDegrees(radians)
        return if (degrees >= 0) degrees else degrees + 360.0
    }

    fun allProjectionsOntoPolyline(
        coordinate: RouteLatLng,
        lineCoordinates: List<RouteLatLng>,
    ): List<RoutePolylineProjection> {
        if (lineCoordinates.size < 2) return emptyList()
        val target = mapPoint(coordinate)
        val projections = mutableListOf<RoutePolylineProjection>()
        var cumulativeDistance = 0.0

        for (index in 0 until lineCoordinates.size - 1) {
            val startCoordinate = lineCoordinates[index]
            val endCoordinate = lineCoordinates[index + 1]
            val start = mapPoint(startCoordinate)
            val end = mapPoint(endCoordinate)
            val segmentDX = end.x - start.x
            val segmentDY = end.y - start.y
            val segmentLengthSquared = segmentDX * segmentDX + segmentDY * segmentDY
            val rawT =
                if (segmentLengthSquared <= 0) {
                    0.0
                } else {
                    ((target.x - start.x) * segmentDX + (target.y - start.y) * segmentDY) / segmentLengthSquared
                }
            val t = rawT.coerceIn(0.0, 1.0)
            val projectedCoordinate =
                coordinateFromMapPoint(
                    MapPoint(
                        x = start.x + segmentDX * t,
                        y = start.y + segmentDY * t,
                    ),
                )
            val segmentDistance = segmentDistanceMeters(startCoordinate, endCoordinate)
            val distance =
                haversineMeters(
                    coordinate.lat,
                    coordinate.lng,
                    projectedCoordinate.lat,
                    projectedCoordinate.lng,
                )
            val arcLength = cumulativeDistance + (segmentDistance * t)
            projections.add(
                RoutePolylineProjection(
                    coordinate = projectedCoordinate,
                    distanceMeters = distance,
                    arcLengthMeters = arcLength,
                    segmentIndex = index,
                    segmentBearingDegrees = bearingBetween(from = startCoordinate, to = endCoordinate),
                ),
            )
            cumulativeDistance += segmentDistance
        }
        return projections
    }

    fun projectOntoPolyline(
        coordinate: RouteLatLng,
        lineCoordinates: List<RouteLatLng>,
    ): RoutePolylineProjection? =
        allProjectionsOntoPolyline(coordinate, lineCoordinates)
            .minByOrNull { it.distanceMeters }

    fun projectOntoPolyline(
        coordinate: RouteLatLng,
        lineCoordinates: List<RouteLatLng>,
        preferredArcLength: Double?,
        searchWindowMeters: Double = 350.0,
    ): RoutePolylineProjection? {
        val projections = allProjectionsOntoPolyline(coordinate, lineCoordinates)
        if (preferredArcLength == null) {
            return projections.minByOrNull { it.distanceMeters }
        }
        val inWindow =
            projections.filter {
                it.arcLengthMeters >= preferredArcLength - 50.0 &&
                    it.arcLengthMeters <= preferredArcLength + searchWindowMeters
            }
        val bestInWindow =
            inWindow.minWithOrNull(
                compareBy<RoutePolylineProjection> {
                    kotlin.math.abs(it.arcLengthMeters - preferredArcLength)
                },
            )
        if (bestInWindow != null) return bestInWindow
        return projections.minByOrNull { it.distanceMeters }
    }

    private fun segmentDistanceMeters(start: RouteLatLng, end: RouteLatLng): Double =
        haversineMeters(start.lat, start.lng, end.lat, end.lng)

    private fun mapPoint(coordinate: RouteLatLng): MapPoint {
        val x = (coordinate.lng + 180.0) / 360.0
        val sinLat = sin(Math.toRadians(coordinate.lat))
        val clampedSin = sinLat.coerceIn(-0.9999, 0.9999)
        val y = 0.5 - ln((1.0 + clampedSin) / (1.0 - clampedSin)) / (4.0 * PI)
        return MapPoint(x * WORLD_WIDTH, y * WORLD_WIDTH)
    }

    private fun coordinateFromMapPoint(point: MapPoint): RouteLatLng {
        val lng = point.x / WORLD_WIDTH * 360.0 - 180.0
        val expPart = exp((0.5 - point.y / WORLD_WIDTH) * 2.0 * PI)
        val lat = Math.toDegrees(atan(expPart)) * 2.0 - 90.0
        return lat to lng
    }
}
