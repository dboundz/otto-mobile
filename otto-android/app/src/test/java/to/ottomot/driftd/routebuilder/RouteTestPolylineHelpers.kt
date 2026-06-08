package to.ottomot.driftd.routebuilder

import to.ottomot.driftd.routebuilder.engine.RouteLatLng

internal fun straightPolyline(lengthMeters: Double, pointCount: Int): List<RouteLatLng> {
    require(pointCount >= 2)
    val startLat = 37.0
    val startLng = -122.0
    val endLat = startLat + (lengthMeters / 111_000.0)
    val endLng = startLng
    return (0 until pointCount).map { index ->
        val fraction = index.toDouble() / (pointCount - 1).toDouble()
        val lat = startLat + (endLat - startLat) * fraction
        val lng = startLng + (endLng - startLng) * fraction
        lat to lng
    }
}
