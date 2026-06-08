package to.ottomot.driftd.routebuilder.engine

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Fast polyline projection for long routes (ported from iOS [RoutePolylineIndex]).
 */
class RoutePolylineIndex(
    val lineCoordinates: List<RouteLatLng>,
) {
    private val cumulativeDistances: List<Double>

    init {
        cumulativeDistances =
            if (lineCoordinates.size >= 2) {
                val cumulative = ArrayList<Double>(lineCoordinates.size)
                cumulative.add(0.0)
                for (index in 0 until lineCoordinates.size - 1) {
                    val start = lineCoordinates[index]
                    val end = lineCoordinates[index + 1]
                    cumulative.add(
                        cumulative[index] +
                            haversineSegmentMeters(start, end),
                    )
                }
                cumulative
            } else {
                emptyList()
            }
    }

    fun projectOntoPolyline(coordinate: RouteLatLng): RoutePolylineProjection? {
        if (lineCoordinates.size < 2) return null

        if (lineCoordinates.size <= 256) {
            return RoutePolylineGeometry.allProjectionsOntoPolyline(coordinate, lineCoordinates)
                .minByOrNull { it.distanceMeters }
        }

        var best: RoutePolylineProjection? = null
        val segmentCount = lineCoordinates.size - 1
        val coarseStride = maxOf(1, segmentCount / 200)

        var index = 0
        while (index < segmentCount) {
            val projection = projectionForSegmentStartingAt(index, coordinate)
            if (projection != null &&
                (best == null || projection.distanceMeters < best!!.distanceMeters)
            ) {
                best = projection
            }
            index += coarseStride
        }

        val coarseBest = best
            ?: return RoutePolylineGeometry.allProjectionsOntoPolyline(coordinate, lineCoordinates)
                .minByOrNull { it.distanceMeters }

        val refineStart = maxOf(0, coarseBest.segmentIndex - coarseStride)
        val refineEnd = minOf(segmentCount - 1, coarseBest.segmentIndex + coarseStride)
        var refinedBest = coarseBest
        for (segmentIndex in refineStart..refineEnd) {
            val projection = projectionForSegmentStartingAt(segmentIndex, coordinate) ?: continue
            if (projection.distanceMeters < refinedBest.distanceMeters) {
                refinedBest = projection
            }
        }
        return refinedBest
    }

    fun projectOntoPolyline(
        coordinate: RouteLatLng,
        preferredArcLength: Double?,
        searchWindowMeters: Double = 350.0,
    ): RoutePolylineProjection? {
        if (lineCoordinates.size < 2) return null
        if (preferredArcLength == null || cumulativeDistances.isEmpty()) {
            return projectOntoPolyline(coordinate)
        }

        val segmentIndex = segmentIndexForArcLength(preferredArcLength)
        val searchRadius = maxOf(8, (searchWindowMeters / 50.0).toInt())
        val start = maxOf(0, segmentIndex - searchRadius)
        val end = minOf(lineCoordinates.size - 2, segmentIndex + searchRadius)

        var best: RoutePolylineProjection? = null
        for (index in start..end) {
            val projection = projectionForSegmentStartingAt(index, coordinate) ?: continue
            if (projection.arcLengthMeters < preferredArcLength - 50.0 ||
                projection.arcLengthMeters > preferredArcLength + searchWindowMeters
            ) {
                continue
            }
            if (best == null ||
                kotlin.math.abs(projection.arcLengthMeters - preferredArcLength) <
                kotlin.math.abs(best!!.arcLengthMeters - preferredArcLength)
            ) {
                best = projection
            }
        }
        return best ?: projectOntoPolyline(coordinate)
    }

    private fun segmentIndexForArcLength(arcLength: Double): Int {
        if (cumulativeDistances.size < 2) return 0
        var low = 0
        var high = cumulativeDistances.size - 1
        while (low < high) {
            val mid = (low + high) / 2
            if (cumulativeDistances[mid] < arcLength) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return maxOf(0, minOf(lineCoordinates.size - 2, low - 1))
    }

    private fun projectionForSegmentStartingAt(
        index: Int,
        targetCoordinate: RouteLatLng,
    ): RoutePolylineProjection? {
        val target = mapPoint(targetCoordinate)
        if (index !in lineCoordinates.indices || index + 1 !in lineCoordinates.indices) {
            return null
        }

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
        val segmentDistance = haversineSegmentMeters(startCoordinate, endCoordinate)
        val arcLength = cumulativeDistances[index] + (segmentDistance * t)
        return RoutePolylineProjection(
            coordinate = projectedCoordinate,
            distanceMeters =
                to.ottomot.driftd.core.event.haversineMeters(
                    targetCoordinate.lat,
                    targetCoordinate.lng,
                    projectedCoordinate.lat,
                    projectedCoordinate.lng,
                ),
            arcLengthMeters = arcLength,
            segmentIndex = index,
            segmentBearingDegrees =
                RoutePolylineGeometry.bearingBetween(
                    from = startCoordinate,
                    to = endCoordinate,
                ),
        )
    }

    private fun haversineSegmentMeters(start: RouteLatLng, end: RouteLatLng): Double =
        to.ottomot.driftd.core.event.haversineMeters(start.lat, start.lng, end.lat, end.lng)

    private data class MapPoint(val x: Double, val y: Double)

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

    private companion object {
        private const val WORLD_WIDTH = 268_435_456.0
    }
}
