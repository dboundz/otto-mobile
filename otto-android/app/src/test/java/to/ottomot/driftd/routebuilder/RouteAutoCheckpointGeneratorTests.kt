package to.ottomot.driftd.routebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry

class RouteAutoCheckpointGeneratorTests {
    @Test
    fun coordinateAtArcLengthRoundTrip() {
        val line = straightPolyline(lengthMeters = 4_000.0, pointCount = 5)
        val target = 2_000.0
        val coordinate =
            RoutePolylineGeometry.coordinateAtArcLength(target, line)
                ?: error("Expected coordinate at arc length")
        val projection =
            RoutePolylineGeometry.projectOntoPolyline(coordinate, line)
                ?: error("Expected projection")
        assertEquals(target, projection.arcLengthMeters, 1.0)
    }

    @Test
    fun straightPolylineGeneratesHalfMileCheckpoints() {
        val line = straightPolyline(lengthMeters = 4_828.0, pointCount = 6)
        val generated = RouteAutoCheckpointGenerator.generate(roadCoordinates = line)
        assertEquals(5, generated.size)
    }

    @Test
    fun shortRouteGeneratesNoCheckpoints() {
        val line = straightPolyline(lengthMeters = 300.0, pointCount = 2)
        val generated = RouteAutoCheckpointGenerator.generate(roadCoordinates = line)
        assertTrue(generated.isEmpty())
        assertFalse(RouteAutoCheckpointGenerator.canOfferAutoCheckpoints(roadCoordinates = line))
    }

    @Test
    fun canOfferAutoCheckpointsWhenViableIntervalExists() {
        val line = straightPolyline(lengthMeters = 1_500.0, pointCount = 3)
        assertTrue(RouteAutoCheckpointGenerator.canOfferAutoCheckpoints(roadCoordinates = line))
    }

    @Test
    fun viableIntervalsOnlyIncludeOptionsThatProduceCheckpoints() {
        val shortLine = straightPolyline(lengthMeters = 1_500.0, pointCount = 3)
        val shortIntervals = RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates = shortLine)
        assertEquals(listOf(0.5), shortIntervals.map { it.miles })

        val longLine = straightPolyline(lengthMeters = 20_000.0, pointCount = 8)
        val longIntervals = RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates = longLine)
        assertTrue(longIntervals.any { it.miles == 0.5 })
        assertTrue(longIntervals.any { it.miles == 1.0 })
        assertTrue(longIntervals.any { it.miles == 2.0 })
        assertFalse(longIntervals.any { it.miles == 100.0 })
    }

    @Test
    fun intervalLabelFormatting() {
        assertEquals("½ mile", RouteAutoCheckpointGenerator.intervalLabel(0.5))
        assertEquals("1 mile", RouteAutoCheckpointGenerator.intervalLabel(1.0))
        assertEquals("5 miles", RouteAutoCheckpointGenerator.intervalLabel(5.0))
    }

    @Test
    fun targetCheckpointCountScalesWithRouteLength() {
        assertEquals(2, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 1.0))
        assertEquals(3, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 3.0))
        assertEquals(5, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 8.0))
        assertEquals(7, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 20.0))
        assertEquals(9, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 40.0))
        assertEquals(11, RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles = 80.0))
    }

    @Test
    fun recommendedDefaultPrefersReasonableSpacingOnShortRoute() {
        val line = straightPolyline(lengthMeters = 3_219.0, pointCount = 5)
        val recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates = line)
        assertNotNull(recommended)
        assertTrue(recommended!!.miles in listOf(0.5, 1.0))
        assertTrue((recommended.checkpointCount) >= 2)
        assertTrue((recommended.checkpointCount) <= 3)
    }

    @Test
    fun recommendedDefaultPrefersSparseIntervalOnLongRoute() {
        val line = straightPolyline(lengthMeters = 40_000.0, pointCount = 8)
        val recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates = line)
        assertEquals(5.0, recommended?.miles)
    }

    @Test
    fun recommendedDefaultOnEpicRouteAvoidsHalfMile() {
        val line = straightPolyline(lengthMeters = 96_000.0, pointCount = 10)
        val recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates = line)
        assertEquals(5.0, recommended?.miles)
        assertTrue((recommended?.checkpointCount ?: 0) <= 12)
    }

    @Test
    fun recommendedDefaultFallsBackToSparsestWhenAllExceedCap() {
        val line = straightPolyline(lengthMeters = 250_000.0, pointCount = 12)
        val recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates = line)
        assertEquals(100.0, recommended?.miles)
    }

    @Test
    fun densityTierCountsIncreaseMonotonically() {
        val line = straightPolyline(lengthMeters = 32_000.0, pointCount = 10)
        val options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates = line, turnCount = 3)
        assertFalse(options.isEmpty())
        val counts = options.map { it.checkpointCount }
        for (index in 1 until counts.size) {
            assertTrue(counts[index - 1] <= counts[index])
        }
    }

    @Test
    fun complexityIncreasesRecommendedCheckpointCount() {
        val line = straightPolyline(lengthMeters = 16_000.0, pointCount = 8)
        val straight =
            RouteAutoCheckpointGenerator.recommendedDefaultInterval(
                roadCoordinates = line,
                turnCount = 0,
            )
        val complex =
            RouteAutoCheckpointGenerator.recommendedDefaultInterval(
                roadCoordinates = line,
                turnCount = 48,
            )
        assertNotNull(straight)
        assertNotNull(complex)
        assertTrue((complex?.checkpointCount ?: 0) >= (straight?.checkpointCount ?: 0))
    }

    @Test
    fun densityOptionsIncludeRecommendedTier() {
        val line = straightPolyline(lengthMeters = 8_000.0, pointCount = 6)
        val options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates = line)
        assertTrue(options.any { it.tier == CheckpointDensityTier.RECOMMENDED })
    }

    @Test
    fun adjacentTierStepsThroughOrderedTiers() {
        assertEquals(
            CheckpointDensityTier.FEWER,
            RouteAutoCheckpointGenerator.adjacentTier(fromTier = CheckpointDensityTier.RECOMMENDED, direction = -1),
        )
        assertEquals(
            CheckpointDensityTier.MORE,
            RouteAutoCheckpointGenerator.adjacentTier(fromTier = CheckpointDensityTier.RECOMMENDED, direction = 1),
        )
        assertEquals(null, RouteAutoCheckpointGenerator.adjacentTier(fromTier = CheckpointDensityTier.FEWER, direction = -1))
        assertEquals(null, RouteAutoCheckpointGenerator.adjacentTier(fromTier = CheckpointDensityTier.MAXIMUM, direction = 1))
    }

    @Test
    fun adjacentDensityOptionStepsThroughUniqueSpacings() {
        val line = straightPolyline(lengthMeters = 32_000.0, pointCount = 10)
        val options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates = line, turnCount = 3)
        val start =
            options.firstOrNull { it.tier == CheckpointDensityTier.RECOMMENDED }
                ?: error("Expected recommended tier")

        val denser =
            RouteAutoCheckpointGenerator.adjacentDensityOption(
                fromTier = start.tier,
                roadCoordinates = line,
                turnCount = 3,
                direction = 1,
            )
        assertNotNull(denser)
        assertTrue((denser?.checkpointCount ?: 0) > start.checkpointCount)

        val sparser =
            RouteAutoCheckpointGenerator.adjacentDensityOption(
                fromTier = start.tier,
                roadCoordinates = line,
                turnCount = 3,
                direction = -1,
            )
        assertNotNull(sparser)
        assertTrue((sparser?.checkpointCount ?: Int.MAX_VALUE) < start.checkpointCount)
    }
}
