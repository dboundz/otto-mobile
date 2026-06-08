package to.ottomot.driftd.routebuilder

import com.mapbox.geojson.Point
import to.ottomot.driftd.routebuilder.engine.RouteLatLng

object RouteBuilderMapTestFixtures {
    fun seededMapContent(waypointCount: Int = 2): RouteBuilderMapContentState {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val start = RouteBuilderPoint(lat = road.first().first, lng = road.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = road.last().first, lng = road.last().second, type = RouteBuilderPointType.FINISH)
        val waypoints =
            (0 until waypointCount).map { index ->
                val fraction = (index + 1).toDouble() / (waypointCount + 1).toDouble()
                RouteBuilderPoint(
                    lat = start.lat + (finish.lat - start.lat) * fraction,
                    lng = start.lng + (finish.lng - start.lng) * fraction,
                    type = RouteBuilderPointType.WAYPOINT,
                )
            }
        val state =
            RouteBuilderScreenState(
                points = listOf(start) + waypoints + listOf(finish),
                roadCoordinates = road,
                didSnapToRoad = true,
                hasCompletedGuidedGeneration = true,
                mapVisibleLatitudeDelta = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
                mapMarkerLodTier = RouteBuilderMapMarkerLodTier.STREET,
            )
        val markers = RouteBuilderMapMarkerSnapshots.buildMarkerSnapshots(state)
        val line = road.map { Point.fromLngLat(it.second, it.first) }
        return RouteBuilderMapContentState(
            lineFingerprint = "${road.size}-${road.first().first}",
            lineCoordinates = line,
            markers = markers,
            allowsInteraction = true,
        )
    }

    fun seededCameraTarget(): RouteBuilderCameraTarget {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val centerLat = road.map { it.first }.average()
        val centerLng = road.map { it.second }.average()
        return RouteBuilderCameraTarget(
            lat = centerLat,
            lng = centerLng,
            zoom = 12.8,
        )
    }

    private fun straightPolyline(lengthMeters: Double, pointCount: Int): List<RouteLatLng> {
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
}
