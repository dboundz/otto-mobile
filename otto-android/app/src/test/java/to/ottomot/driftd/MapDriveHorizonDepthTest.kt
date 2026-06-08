package to.ottomot.driftd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDriveHorizonDepthTest {
    @Test
    fun horizonScaleAtUserIsFullSize() {
        val scale =
            MapDriveHorizonDepth.horizonScale(
                distanceMeters = 0.0,
                visibleMapHeightMeters = 2_000.0,
            )
        assertEquals(1f, scale, 0.001f)
    }

    @Test
    fun horizonScaleDecreasesWithDistance() {
        val near =
            MapDriveHorizonDepth.horizonScale(
                distanceMeters = 200.0,
                visibleMapHeightMeters = 2_000.0,
            )
        val far =
            MapDriveHorizonDepth.horizonScale(
                distanceMeters = 1_800.0,
                visibleMapHeightMeters = 2_000.0,
            )
        assertTrue(near > far)
    }

    @Test
    fun horizonScaleIsMapProportional() {
        val range = 1_000.0
        val scaleAtHalfRange =
            MapDriveHorizonDepth.horizonScale(
                distanceMeters = range / 2,
                visibleMapHeightMeters = range,
            )
        val scaleAtHalfRangeDoubledVisible =
            MapDriveHorizonDepth.horizonScale(
                distanceMeters = range,
                visibleMapHeightMeters = range * 2,
            )
        assertEquals(scaleAtHalfRange, scaleAtHalfRangeDoubledVisible, 0.001f)
    }

    @Test
    fun driveOverlapPriorityNearerIsHigher() {
        val near = MapDriveHorizonDepth.driveRouteOverlapPriority(50.0)
        val far = MapDriveHorizonDepth.driveRouteOverlapPriority(500.0)
        assertTrue(near > far)
    }

    @Test
    fun endpointBoostBeatsCheckpointAtSameDistance() {
        val checkpoint = MapDriveHorizonDepth.driveRouteOverlapPriority(100.0, markerType = "waypoint")
        val finish = MapDriveHorizonDepth.driveRouteOverlapPriority(100.0, markerType = "finish")
        assertTrue(finish > checkpoint)
    }

    @Test
    fun checkpointWithinOneMileIsShown() {
        assertTrue(
            MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType = "waypoint",
                distanceMeters = 1_500.0,
            ),
        )
    }

    @Test
    fun checkpointBeyondOneMileIsHidden() {
        assertTrue(
            !MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType = "waypoint",
                distanceMeters = MapDriveHorizonDepth.CHECKPOINT_VISIBLE_MAX_DISTANCE_METERS + 100.0,
            ),
        )
    }

    @Test
    fun startFinishAlwaysShownRegardlessOfDistance() {
        assertTrue(
            MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType = "start",
                distanceMeters = 50_000.0,
            ),
        )
    }

    @Test
    fun presenceWithinOneMileIsShown() {
        assertTrue(MapDriveHorizonDepth.shouldShowPresenceMarker(1_500.0))
    }

    @Test
    fun presenceBeyondOneMileIsHidden() {
        assertTrue(
            !MapDriveHorizonDepth.shouldShowPresenceMarker(
                MapDriveHorizonDepth.CHECKPOINT_VISIBLE_MAX_DISTANCE_METERS + 100.0,
            ),
        )
    }
}
