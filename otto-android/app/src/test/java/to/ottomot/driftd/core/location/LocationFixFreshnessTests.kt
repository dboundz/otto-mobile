package to.ottomot.driftd.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixFreshnessTests {
    @Test
    fun isFreshForRouteBuilderCenter_rejectsMissingTimestamp() {
        val fix =
            LocationFix(
                latitude = 40.7128,
                longitude = -74.0060,
                speedMps = null,
                accuracyMeters = null,
                bearingDegrees = null,
            )

        assertFalse(fix.isFreshForRouteBuilderCenter(nowElapsedRealtimeNanos = 10_000L))
    }

    @Test
    fun isFreshForRouteBuilderCenter_rejectsStaleFix() {
        val now = 300L * 1_000_000_000L
        val oldFix =
            LocationFix(
                latitude = 40.7128,
                longitude = -74.0060,
                speedMps = null,
                accuracyMeters = null,
                bearingDegrees = null,
                elapsedRealtimeNanos = 0L,
            )

        assertFalse(oldFix.isFreshForRouteBuilderCenter(nowElapsedRealtimeNanos = now))
    }

    @Test
    fun isFreshForRouteBuilderCenter_acceptsRecentFix() {
        val now = 300L * 1_000_000_000L
        val recentFix =
            LocationFix(
                latitude = 30.3322,
                longitude = -81.6557,
                speedMps = null,
                accuracyMeters = null,
                bearingDegrees = null,
                elapsedRealtimeNanos = now - 30L * 1_000_000_000L,
            )

        assertTrue(recentFix.isFreshForRouteBuilderCenter(nowElapsedRealtimeNanos = now))
    }
}
