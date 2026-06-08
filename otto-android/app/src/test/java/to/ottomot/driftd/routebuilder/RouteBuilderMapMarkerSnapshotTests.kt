package to.ottomot.driftd.routebuilder

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.network.dto.CreateRouteRequestDto
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng

class RouteBuilderMapMarkerSnapshotTests {
    @Test
    fun mapPointsForDisplay_includesAllPointsAtRegionalZoom() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val state = seededEditorState(roadCoordinates = road, waypointCount = 2, latitudeDelta = 0.5)
        val displayed = RouteBuilderMapMarkerSnapshots.mapPointsForDisplay(state)
        assertEquals(state.points.size, displayed.size)
        assertEquals(2, displayed.count { it.first.type == RouteBuilderPointType.WAYPOINT })
    }

    @Test
    fun buildMarkerSnapshots_includesAllWaypointsAtAnyZoom() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val wideZoom = RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA * 2
        val state = seededEditorState(roadCoordinates = road, waypointCount = 2, latitudeDelta = wideZoom)
        val markers = RouteBuilderMapMarkerSnapshots.buildMarkerSnapshots(state)
        assertEquals(state.points.size, markers.size)
        assertEquals(2, markers.count { it.markerType == "waypoint" })
        markers.filter { it.markerType == "waypoint" }.forEach { marker ->
            assertEquals(RouteBuilderMapMarkerPresentation.DOT, marker.presentation)
            assertEquals(RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE, marker.pinScale)
        }
    }

    @Test
    fun buildMarkerSnapshots_scalesSecondaryPinsAtIconZoom() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val iconZoom =
            (RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA +
                RouteBuilderConstants.PIN_FULL_SIZE_MAX_LATITUDE_DELTA) / 2.0
        val state = seededEditorState(roadCoordinates = road, waypointCount = 1, latitudeDelta = iconZoom)

        val markers = RouteBuilderMapMarkerSnapshots.buildMarkerSnapshots(state)
        val waypoint = markers.first { it.markerType == "waypoint" }
        val start = markers.first { it.markerType == "start" }

        assertEquals(RouteBuilderMapMarkerPresentation.PIN, waypoint.presentation)
        assertTrue(waypoint.pinScale > RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE)
        assertTrue(waypoint.pinScale < 1f)
        assertEquals(RouteBuilderMapMarkerPresentation.ENDPOINT_PIN, start.presentation)
        assertEquals(1f, start.pinScale)
    }

    @Test
    fun buildMarkerSnapshots_parityAfterSimulatedCheckpointReplace() {
        val road = straightPolyline(lengthMeters = 4_828.0, pointCount = 6)
        val generated = RouteAutoCheckpointGenerator.generate(roadCoordinates = road)
        val start = RouteBuilderPoint(lat = road.first().first, lng = road.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = road.last().first, lng = road.last().second, type = RouteBuilderPointType.FINISH)
        val waypoints =
            generated.map {
                RouteBuilderPoint(lat = it.lat, lng = it.lng, type = RouteBuilderPointType.WAYPOINT)
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
        assertEquals(waypoints.size, markers.count { it.markerType == "waypoint" })
        assertEquals(state.points.size, markers.size)
    }

    @Test
    fun savePayload_includesAllNonAutoShapeWaypoints() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 8)
        val autoPath =
            RouteBuilderPoint(lat = 37.01, lng = -122.0, type = RouteBuilderPointType.PATH, isAutoShape = true)
        val state = seededEditorState(roadCoordinates = road, waypointCount = 2).copy(
            points =
                seededEditorState(roadCoordinates = road, waypointCount = 2).points + autoPath,
        )
        val savePoints = RouteBuilderSavePayload.intentionalPoints(state.points)
        assertEquals(4, savePoints.size)
        assertEquals(2, savePoints.count { it.type == RouteBuilderPointType.WAYPOINT })
        assertTrue(savePoints.none { it.isAutoShape })

        val gson = GsonBuilder().serializeNulls().create()
        val json =
            gson.toJson(
                CreateRouteRequestDto(
                    name = "Test",
                    points = RouteBuilderSavePayload.savePointDtos(state),
                    roadCoordinates = RouteBuilderSavePayload.roadCoordinateDtos(state),
                    distanceMeters = state.distanceMeters,
                    etaSeconds = state.travelSeconds,
                ),
            )
        assertTrue(json.contains("\"type\":\"waypoint\""))
        assertTrue(json.contains("\"type\":\"start\""))
        assertTrue(json.contains("\"type\":\"finish\""))
    }

    private fun seededEditorState(
        roadCoordinates: List<to.ottomot.driftd.routebuilder.engine.RouteLatLng>,
        waypointCount: Int,
        latitudeDelta: Double = RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA,
    ): RouteBuilderScreenState {
        val start = RouteBuilderPoint(lat = roadCoordinates.first().first, lng = roadCoordinates.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = roadCoordinates.last().first, lng = roadCoordinates.last().second, type = RouteBuilderPointType.FINISH)
        val waypoints =
            (0 until waypointCount).map { index ->
                val fraction = (index + 1).toDouble() / (waypointCount + 1).toDouble()
                val lat = start.lat + (finish.lat - start.lat) * fraction
                val lng = start.lng + (finish.lng - start.lng) * fraction
                RouteBuilderPoint(lat = lat, lng = lng, type = RouteBuilderPointType.WAYPOINT)
            }
        return RouteBuilderScreenState(
            points = listOf(start) + waypoints + listOf(finish),
            roadCoordinates = roadCoordinates,
            distanceMeters = 5_000.0,
            travelSeconds = 600.0,
            didSnapToRoad = true,
            hasCompletedGuidedGeneration = true,
            activeCheckpointSpacingMeters = 804.672,
            selectedCheckpointDensity = CheckpointDensityTier.RECOMMENDED,
            mapVisibleLatitudeDelta = latitudeDelta,
            mapMarkerLodTier = RouteBuilderMapMarkerLodTier.from(latitudeDelta),
        )
    }
}
