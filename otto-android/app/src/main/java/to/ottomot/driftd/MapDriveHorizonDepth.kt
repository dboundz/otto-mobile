package to.ottomot.driftd

import kotlin.math.roundToInt

/** Map-proportional marker scale and distance-based overlap priority during pitched drive follow. */
internal object MapDriveHorizonDepth {
    /** Checkpoints farther than this from the user are not shown on the map route layer. */
    const val CHECKPOINT_VISIBLE_MAX_DISTANCE_METERS = 1609.344

    const val ROUTE_MIN_SCALE = 0.55f
    const val PRESENCE_MIN_SCALE = 0.5f

    private const val SCALE_QUANTIZATION_STEP = 0.05f
    private const val ENDPOINT_PRIORITY_BOOST = 200_000_000L
    private const val PRESENCE_PRIORITY_BOOST = 150_000_000L
    private const val PRIORITY_BASE = 1_000_000L

    fun visibleMapHeightMeters(latitudeDelta: Double): Double {
        if (!latitudeDelta.isFinite() || latitudeDelta <= 0) return 111_000.0
        return maxOf(50.0, latitudeDelta * 111_000.0)
    }

    fun horizonScale(
        distanceMeters: Double,
        visibleMapHeightMeters: Double,
        minScale: Float = ROUTE_MIN_SCALE,
    ): Float {
        if (!distanceMeters.isFinite() || !visibleMapHeightMeters.isFinite() || visibleMapHeightMeters <= 0) {
            return 1f
        }
        val t = (distanceMeters / visibleMapHeightMeters).coerceIn(0.0, 1.0)
        val minS = minScale.toDouble()
        val raw = maxOf(minS, 1.0 - t * (1.0 - minS))
        return quantizeScale(raw.toFloat())
    }

    fun driveRouteOverlapPriority(
        distanceMeters: Double,
        markerType: String? = null,
        tieBreaker: Int = 0,
    ): Long {
        if (!distanceMeters.isFinite()) return tieBreaker.toLong()
        var priority = PRIORITY_BASE - distanceMeters.roundToInt() + tieBreaker
        if (markerType == "start" || markerType == "finish") {
            priority += ENDPOINT_PRIORITY_BOOST
        }
        return priority
    }

    fun drivePresenceOverlapPriority(
        distanceMeters: Double,
        tieBreaker: Int = 0,
    ): Long = driveRouteOverlapPriority(distanceMeters, tieBreaker = tieBreaker) + PRESENCE_PRIORITY_BOOST

    /** Waypoints only; start/finish/stop are always eligible for display. */
    fun shouldShowRouteMarker(
        markerType: String?,
        distanceMeters: Double?,
    ): Boolean {
        if (markerType != "waypoint") return true
        if (distanceMeters == null || !distanceMeters.isFinite()) return true
        return distanceMeters <= CHECKPOINT_VISIBLE_MAX_DISTANCE_METERS
    }

    /** Presence pins during pitched drive follow — same ~1 mi horizon as route checkpoints. */
    fun shouldShowPresenceMarker(distanceMeters: Double?): Boolean {
        if (distanceMeters == null || !distanceMeters.isFinite()) return true
        return distanceMeters <= CHECKPOINT_VISIBLE_MAX_DISTANCE_METERS
    }

    private fun quantizeScale(scale: Float): Float {
        val step = SCALE_QUANTIZATION_STEP
        return (scale / step).roundToInt() * step
    }
}
