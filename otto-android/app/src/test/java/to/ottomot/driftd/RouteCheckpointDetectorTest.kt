package to.ottomot.driftd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.routebuilder.engine.RouteLatLng

class RouteCheckpointDetectorTest {
    @Test
    fun outboundPassDoesNotTriggerReturnCheckpoint() {
        val road = outAndBackRoadCoordinates()
        val points = outAndBackRoutePoints()
        val outboundMidpoint =
            RouteDriveLocationSample(
                latitude = 37.01,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 0.0,
            )
        val previous =
            RouteDriveLocationSample(
                latitude = 37.005,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 0.0,
            )
        val contexts =
            RouteCheckpointDetector.checkpointRouteContexts(
                routeCoordinates = routeCoordinates(points),
                roadCoordinates = road,
            )
        val driverProgress =
            RouteCheckpointDetector.driverRouteProgress(
                location = outboundMidpoint.latitude to outboundMidpoint.longitude,
                roadCoordinates = road,
                lastRouteProgressMeters = 400.0,
            )

        assertFalse(
            RouteCheckpointDetector.shouldTriggerCheckpoint(
                index = 2,
                coordinates = routeCoordinates(points),
                location = outboundMidpoint,
                previousLocation = previous,
                speedMetersPerSecond = 12.0,
                completedIndexes = setOf(0, 1),
                checkpointContexts = contexts,
                driverProgressMeters = driverProgress,
            ),
        )
    }

    @Test
    fun returnPassTriggersReturnCheckpoint() {
        val road = outAndBackRoadCoordinates()
        val points = outAndBackRoutePoints()
        val returnMidpoint =
            RouteDriveLocationSample(
                latitude = 37.01,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 180.0,
            )
        val previous =
            RouteDriveLocationSample(
                latitude = 37.015,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 180.0,
            )
        val contexts =
            RouteCheckpointDetector.checkpointRouteContexts(
                routeCoordinates = routeCoordinates(points),
                roadCoordinates = road,
            )
        val outboundArc = contexts[1]?.arcLengthMeters ?: 0.0
        val returnArc = contexts[2]?.arcLengthMeters ?: 0.0
        assertGreater(returnArc, outboundArc + 500.0)

        val driverProgress =
            RouteCheckpointDetector.driverRouteProgress(
                location = returnMidpoint.latitude to returnMidpoint.longitude,
                roadCoordinates = road,
                lastRouteProgressMeters = returnArc - 100.0,
            )

        assertTrue(
            RouteCheckpointDetector.shouldTriggerCheckpoint(
                index = 2,
                coordinates = routeCoordinates(points),
                location = returnMidpoint,
                previousLocation = previous,
                speedMetersPerSecond = 12.0,
                completedIndexes = setOf(0, 1),
                checkpointContexts = contexts,
                driverProgressMeters = driverProgress,
            ),
        )
    }

    @Test
    fun simpleOneWayRouteStillTriggers() {
        val road = oneWayRoadCoordinates()
        val points = oneWayRoutePoints()
        val checkpointLocation =
            RouteDriveLocationSample(
                latitude = 37.01,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 0.0,
            )
        val previous =
            RouteDriveLocationSample(
                latitude = 37.005,
                longitude = -122.0,
                speedMps = 12.0,
                accuracyMeters = 10.0,
                bearingDegrees = 0.0,
            )

        val result =
            RouteCheckpointDetector.evaluate(
                routePoints = points,
                roadCoordinates = road,
                location = checkpointLocation,
                previousLocation = previous,
                speedMetersPerSecond = 12.0,
                completedIndexes = setOf(0),
                lastRouteProgressMeters = 400.0,
            )

        assertEquals(listOf(1), result.newlyTriggeredIndexes)
    }

    @Test
    fun lowSpeedStartCheckpointStillTriggers() {
        val road = oneWayRoadCoordinates()
        val points = oneWayRoutePoints()
        val startLocation =
            RouteDriveLocationSample(
                latitude = 37.0,
                longitude = -122.0,
                speedMps = 0.0,
                accuracyMeters = 10.0,
                bearingDegrees = null,
            )

        val result =
            RouteCheckpointDetector.evaluate(
                routePoints = points,
                roadCoordinates = road,
                location = startLocation,
                previousLocation = null,
                speedMetersPerSecond = 0.0,
                completedIndexes = emptySet(),
                lastRouteProgressMeters = null,
            )

        assertEquals(listOf(0), result.newlyTriggeredIndexes)
    }

    @Test
    fun identicalCheckpointCoordinatesDisambiguateByProgressAndDirection() {
        val road = outAndBackRoadCoordinates()
        val points = outAndBackRoutePoints()
        val contexts =
            RouteCheckpointDetector.checkpointRouteContexts(
                routeCoordinates = routeCoordinates(points),
                roadCoordinates = road,
            )
        val outboundArc = contexts[1]?.arcLengthMeters ?: 0.0
        val returnArc = contexts[2]?.arcLengthMeters ?: 0.0
        assertGreater(returnArc, outboundArc + 500.0)
        val outboundBearing = contexts[1]?.segmentBearingDegrees ?: 0.0
        val returnBearing = contexts[2]?.segmentBearingDegrees ?: 0.0
        assertNotEquals(outboundBearing, returnBearing, 1.0)

        val startArc = contexts[0]?.arcLengthMeters ?: 0.0
        val finishArc = contexts[3]?.arcLengthMeters ?: 0.0
        assertGreater(finishArc, startArc + 500.0)
    }

    private fun routeCoordinates(points: List<RoutePointDto>): List<RouteLatLng> =
        points.map { it.lat to it.lng }

    private fun outAndBackRoadCoordinates(): List<RouteLatLng> =
        listOf(
            37.0 to -122.0,
            37.005 to -122.0,
            37.01 to -122.0,
            37.015 to -122.0,
            37.02 to -122.0,
            37.015 to -122.0,
            37.01 to -122.0,
            37.005 to -122.0,
            37.0 to -122.0,
        )

    private fun outAndBackRoutePoints(): List<RoutePointDto> =
        listOf(
            RoutePointDto(lat = 37.0, lng = -122.0, markerType = "start"),
            RoutePointDto(lat = 37.01, lng = -122.0, markerType = "waypoint"),
            RoutePointDto(lat = 37.01, lng = -122.0, markerType = "waypoint"),
            RoutePointDto(lat = 37.0, lng = -122.0, markerType = "finish"),
        )

    private fun oneWayRoadCoordinates(): List<RouteLatLng> =
        listOf(
            37.0 to -122.0,
            37.005 to -122.0,
            37.01 to -122.0,
            37.015 to -122.0,
            37.02 to -122.0,
        )

    private fun oneWayRoutePoints(): List<RoutePointDto> =
        listOf(
            RoutePointDto(lat = 37.0, lng = -122.0, markerType = "start"),
            RoutePointDto(lat = 37.01, lng = -122.0, markerType = "waypoint"),
            RoutePointDto(lat = 37.02, lng = -122.0, markerType = "finish"),
        )
}

private fun assertGreater(actual: Double, expected: Double) {
    assertTrue("Expected $actual > $expected", actual > expected)
}

private fun assertNotEquals(
    expected: Double,
    actual: Double,
    delta: Double,
) {
    assertTrue(
        "Expected values to differ by more than $delta (expected=$expected actual=$actual)",
        kotlin.math.abs(expected - actual) > delta,
    )
}
