package to.ottomot.driftd.routebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier

class RouteBuilderCheckpointRegenerationTests {
    @Test
    fun computePendingAfterEndpointChange_doesNotStripWaypointsFromState() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 10)
        val start = RouteBuilderPoint(lat = road.first().first, lng = road.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = road.last().first, lng = road.last().second, type = RouteBuilderPointType.FINISH)
        val waypoints =
            listOf(
                RouteBuilderPoint(lat = 37.01, lng = -122.0, type = RouteBuilderPointType.WAYPOINT),
                RouteBuilderPoint(lat = 37.02, lng = -122.0, type = RouteBuilderPointType.WAYPOINT),
            )
        val state =
            RouteBuilderScreenState(
                points = listOf(start) + waypoints + listOf(finish),
                roadCoordinates = road,
                didSnapToRoad = true,
                hasCompletedGuidedGeneration = true,
                activeCheckpointSpacingMeters = 804.672,
                selectedCheckpointDensity = CheckpointDensityTier.RECOMMENDED,
            )
        val waypointCountBefore = state.points.count { it.type == RouteBuilderPointType.WAYPOINT }
        val pending = RouteBuilderCheckpointRegeneration.computePendingAfterEndpointChange(state)
        assertNotNull(pending)
        assertEquals(2, waypointCountBefore)
        assertEquals(2, state.checkpointCount)
        assertEquals(2, state.points.count { it.type == RouteBuilderPointType.WAYPOINT })
    }

    @Test
    fun computePendingAfterEndpointChange_returnsNullWhenNoCheckpointsAndNoGuidedGeneration() {
        val road = straightPolyline(lengthMeters = 5_000.0, pointCount = 4)
        val start = RouteBuilderPoint(lat = road.first().first, lng = road.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = road.last().first, lng = road.last().second, type = RouteBuilderPointType.FINISH)
        val state =
            RouteBuilderScreenState(
                points = listOf(start, finish),
                roadCoordinates = road,
                didSnapToRoad = true,
                hasCompletedGuidedGeneration = false,
                activeCheckpointSpacingMeters = null,
            )
        val pending = RouteBuilderCheckpointRegeneration.computePendingAfterEndpointChange(state)
        assertEquals(null, pending)
    }

    @Test
    fun computePendingAfterEndpointChange_infersSpacingWhenCheckpointsExistWithoutActiveSpacing() {
        val road = straightPolyline(lengthMeters = 4_828.0, pointCount = 6)
        val start = RouteBuilderPoint(lat = road.first().first, lng = road.first().second, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = road.last().first, lng = road.last().second, type = RouteBuilderPointType.FINISH)
        val waypoints =
            listOf(
                RouteBuilderPoint(lat = 37.01, lng = -122.0, type = RouteBuilderPointType.WAYPOINT),
                RouteBuilderPoint(lat = 37.02, lng = -122.0, type = RouteBuilderPointType.WAYPOINT),
            )
        val state =
            RouteBuilderScreenState(
                points = listOf(start) + waypoints + listOf(finish),
                roadCoordinates = road,
                didSnapToRoad = true,
                hasCompletedGuidedGeneration = true,
                activeCheckpointSpacingMeters = null,
            )
        val pending = RouteBuilderCheckpointRegeneration.computePendingAfterEndpointChange(state)
        assertNotNull(pending)
        assertNotNull(pending?.spacingMeters)
    }
}
