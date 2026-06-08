package to.ottomot.driftd.core.location

import com.google.android.gms.location.DetectedActivity

/**
 * Mirrors iOS [LocationService] movement classification for local presence:
 * - Motion / activity when strong enough (automotive → driving; walking/running/on-foot → walking)
 * - Otherwise GPS speed fallback: ≥4.5 m/s (~10 mph) driving; (0.6, 3.0) m/s walking
 * - Sticky driving when inference is unknown but [previousMode] was driving (iOS activity handler)
 *
 * Remote presence merge in [to.ottomot.driftd.map.normalizePresenceMovementMode] still uses
 * server [movementMode] first, then mph heuristics for peers.
 */
internal object MovementModeIosParity {

    private const val DRIVING_MIN_SPEED_MPS = 4.5

    private const val WALKING_MIN_SPEED_MPS_EXCL = 0.6

    private const val WALKING_MAX_SPEED_MPS_EXCL = 3.0

    private const val MIN_ACTIVITY_CONFIDENCE = 40

    fun inferMovementModeFromSpeedMps(speedMps: Double): String {
        if (speedMps >= DRIVING_MIN_SPEED_MPS) return "driving"
        if (speedMps > WALKING_MIN_SPEED_MPS_EXCL && speedMps < WALKING_MAX_SPEED_MPS_EXCL) {
            return "walking"
        }
        return "unknown"
    }

    /**
     * @param previousResolvedMode last published local mode (`driving` / `walking` / `unknown`) for sticky driving
     */
    fun resolveLocalMovementMode(
        activity: ActivityRecognitionSnapshot?,
        speedMps: Double,
        previousResolvedMode: String?,
    ): String {
        val automotive = activity?.inVehicle == true
        val walkingOrRunning = activity?.onFoot == true
        if (automotive) return "driving"
        if (walkingOrRunning) return "walking"

        val inferred = inferMovementModeFromSpeedMps(speedMps)
        return if (inferred == "unknown" && previousResolvedMode == "driving") {
            "driving"
        } else {
            inferred
        }
    }

    fun snapshotFromDetectedActivities(activities: List<DetectedActivity>): ActivityRecognitionSnapshot? {
        if (activities.isEmpty()) return null
        val best = activities.maxByOrNull { it.confidence } ?: return null
        if (best.confidence < MIN_ACTIVITY_CONFIDENCE) {
            return ActivityRecognitionSnapshot(
                inVehicle = false,
                onFoot = false,
                dominantType = best.type,
                dominantConfidence = best.confidence,
            )
        }
        val inVehicle = best.type == DetectedActivity.IN_VEHICLE
        val onFoot =
            best.type == DetectedActivity.WALKING ||
                best.type == DetectedActivity.RUNNING ||
                best.type == DetectedActivity.ON_FOOT
        return ActivityRecognitionSnapshot(
            inVehicle = inVehicle,
            onFoot = onFoot,
            dominantType = best.type,
            dominantConfidence = best.confidence,
        )
    }
}

/** Last fused activity sample while recognition updates are active. */
internal data class ActivityRecognitionSnapshot(
    val inVehicle: Boolean,
    val onFoot: Boolean,
    val dominantType: Int,
    val dominantConfidence: Int,
)
