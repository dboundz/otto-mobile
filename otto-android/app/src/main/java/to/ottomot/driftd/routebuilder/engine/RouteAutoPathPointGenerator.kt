package to.ottomot.driftd.routebuilder.engine

/**
 * Places auto turn path markers along a snapped road polyline (ported from iOS).
 */
object RouteAutoPathPointGenerator {
    object Options {
        const val FINISH_BUFFER_METERS = 100.0
    }

    fun autoTurnPathCoordinates(
        turnCoordinates: List<RouteLatLng>,
        roadCoordinates: List<RouteLatLng>,
        polylineIndex: RoutePolylineIndex? = null,
    ): List<RouteLatLng> {
        if (roadCoordinates.size < 2) return emptyList()

        val index = polylineIndex ?: RoutePolylineIndex(roadCoordinates)
        val totalLength = RoutePolylineGeometry.polylineTotalLength(roadCoordinates)
        val projected =
            turnCoordinates.mapNotNull { turnCoordinate ->
                val projection = index.projectOntoPolyline(turnCoordinate) ?: return@mapNotNull null
                val arcLength = projection.arcLengthMeters
                if (arcLength <= Options.FINISH_BUFFER_METERS ||
                    arcLength >= totalLength - Options.FINISH_BUFFER_METERS
                ) {
                    return@mapNotNull null
                }
                projection.coordinate to arcLength
            }

        return projected
            .sortedBy { it.second }
            .map { it.first }
    }
}
