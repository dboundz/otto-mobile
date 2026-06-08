package to.ottomot.driftd.routebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.event.haversineMeters

class RouteBuilderPlacementTests {
    @Test
    fun coordinateAtCrosshair_differsWhenPlacementRegionCenterMoves() {
        val mapWidthPx = 400f
        val mapHeightPx = 800f
        val sheetHeightPx = 200f
        val (crosshairX, crosshairY) =
            RouteBuilderPlacement.crosshairCenter(
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                sheetVisibleHeightPx = sheetHeightPx,
                bottomSafeAreaPx = 0f,
            )
        val zoomedOut =
            RouteBuilderCameraRegion(
                centerLat = 30.0,
                centerLng = -81.0,
                latitudeDelta = 0.5,
                longitudeDelta = 0.5,
            )
        val zoomedIn =
            RouteBuilderCameraRegion(
                centerLat = 30.02,
                centerLng = -80.98,
                latitudeDelta = 0.01,
                longitudeDelta = 0.01,
            )
        val atZoomedOut =
            RouteBuilderPlacement.coordinateAtCrosshair(
                region = zoomedOut,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                crosshairX = crosshairX,
                crosshairY = crosshairY,
            )
        val atZoomedIn =
            RouteBuilderPlacement.coordinateAtCrosshair(
                region = zoomedIn,
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                crosshairX = crosshairX,
                crosshairY = crosshairY,
            )
        assertNotEquals(atZoomedOut.first, atZoomedIn.first, 1e-6)
        assertNotEquals(atZoomedOut.second, atZoomedIn.second, 1e-6)
    }

    @Test
    fun isImplausibleGuidedSpan_flagsContinentalSeparation() {
        val start = 30.0 to -81.0
        val finishLocal = 30.02 to -80.98
        val finishContinental = 39.0 to -105.0
        assertFalse(RouteBuilderPlacementSanity.isImplausibleGuidedSpan(start, finishLocal))
        assertTrue(RouteBuilderPlacementSanity.isImplausibleGuidedSpan(start, finishContinental))
        val localMeters = haversineMeters(start.first, start.second, finishLocal.first, finishLocal.second)
        assertTrue(localMeters < RouteBuilderPlacementSanity.MAX_GUIDED_START_FINISH_METERS)
    }

    @Test
    fun coordinateForPlacement_keepsPathRawButSnapsCheckpointsAndStops() {
        val raw = 37.12 to -122.12
        val projected = 37.0 to -122.0
        val project: (Pair<Double, Double>) -> Pair<Double, Double>? = { projected }

        assertEquals(
            raw,
            routeBuilderCoordinateForPlacement(RouteBuilderPointType.PATH, raw, hasRouteLine = true, project),
        )
        assertEquals(
            projected,
            routeBuilderCoordinateForPlacement(RouteBuilderPointType.WAYPOINT, raw, hasRouteLine = true, project),
        )
        assertEquals(
            projected,
            routeBuilderCoordinateForPlacement(RouteBuilderPointType.STOP, raw, hasRouteLine = true, project),
        )
        assertEquals(
            raw,
            routeBuilderCoordinateForPlacement(RouteBuilderPointType.WAYPOINT, raw, hasRouteLine = false, project),
        )
    }
}
