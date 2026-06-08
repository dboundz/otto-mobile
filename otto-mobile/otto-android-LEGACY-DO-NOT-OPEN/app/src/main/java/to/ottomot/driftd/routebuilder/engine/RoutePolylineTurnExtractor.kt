package to.ottomot.driftd.routebuilder.engine

import kotlin.math.PI
import kotlin.math.abs
import to.ottomot.driftd.core.event.haversineMeters

/**
 * Derives turn-like coordinates from a saved road polyline without re-calling directions.
 */
object RoutePolylineTurnExtractor {
    private const val MINIMUM_TURN_ANGLE_DEGREES = 28.0
    private const val MINIMUM_ARC_SPACING_METERS = 40.0
    private const val FINISH_BUFFER_METERS = RouteAutoPathPointGenerator.Options.FINISH_BUFFER_METERS

    fun turnCoordinates(along: List<RouteLatLng>): List<RouteLatLng> {
        if (along.size < 3) return emptyList()

        val totalLength = RoutePolylineGeometry.polylineTotalLength(along)
        if (totalLength <= FINISH_BUFFER_METERS * 2) return emptyList()

        val sampleStride = maxOf(1, along.size / 500)
        val cumulative = ArrayList<Double>(along.size)
        cumulative.add(0.0)
        for (segmentIndex in 0 until along.size - 1) {
            val start = along[segmentIndex]
            val end = along[segmentIndex + 1]
            cumulative.add(
                cumulative[segmentIndex] +
                    haversineMeters(start.lat, start.lng, end.lat, end.lng),
            )
        }

        val results = mutableListOf<RouteLatLng>()
        var lastTurnArcLength = -Double.MAX_VALUE
        var index = sampleStride

        while (index < along.size - sampleStride) {
            val previous = along[index - sampleStride]
            val current = along[index]
            val next = along[index + sampleStride]

            val incoming = RoutePolylineGeometry.bearingBetween(from = previous, to = current)
            val outgoing = RoutePolylineGeometry.bearingBetween(from = current, to = next)
            val turnAngle = normalizedAngleDelta(startDegrees = incoming, endDegrees = outgoing)

            if (turnAngle >= MINIMUM_TURN_ANGLE_DEGREES) {
                val arcLength = cumulative[index]
                if (arcLength > FINISH_BUFFER_METERS &&
                    arcLength < totalLength - FINISH_BUFFER_METERS &&
                    arcLength - lastTurnArcLength >= MINIMUM_ARC_SPACING_METERS
                ) {
                    results.add(current)
                    lastTurnArcLength = arcLength
                }
            }

            index += sampleStride
        }

        return results
    }

    private fun normalizedAngleDelta(startDegrees: Double, endDegrees: Double): Double {
        var delta = abs(endDegrees - startDegrees) % 360.0
        if (delta > 180.0) delta = 360.0 - delta
        return delta
    }
}
