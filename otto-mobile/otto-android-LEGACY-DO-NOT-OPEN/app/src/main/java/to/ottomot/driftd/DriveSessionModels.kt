package to.ottomot.driftd

import androidx.compose.ui.graphics.Color

enum class DriveSessionKind { QUICK, ROUTE, LIVE }

data class LatLngPair(val lat: Double, val lng: Double)

data class DriveSessionCompletionPayload(
    val driveId: String?,
    val kind: DriveSessionKind,
    val routeName: String?,
    val routeCoordinates: List<LatLngPair>,
    val checkpointCoordinates: List<LatLngPair>,
    val distanceMeters: Double,
    val driveTimeSeconds: Long,
    val averageSpeedMph: Double,
    val maxSpeedMph: Double,
    val completedCheckpoints: Int,
    val totalCheckpoints: Int,
    val completionReason: String,
)

data class DriveCompleteSummary(
    val driveId: String?,
    val routeName: String,
    val routeCoordinates: List<LatLngPair>,
    val checkpointCoordinates: List<LatLngPair>,
    val pathSamples: List<DrivePathSample>,
    val distanceMeters: Double,
    val driveTimeSeconds: Long,
    val averageSpeedMph: Double,
    val maxSpeedMph: Double,
    val completedCheckpoints: Int,
    val totalCheckpoints: Int,
    val completionReason: String,
)

fun DriveSessionCompletionPayload.toSummary(pathSamples: List<DrivePathSample>): DriveCompleteSummary =
    DriveCompleteSummary(
        driveId = driveId,
        routeName = routeName?.trim()?.takeIf { it.isNotEmpty() } ?: "Drive",
        routeCoordinates = routeCoordinates,
        checkpointCoordinates = checkpointCoordinates,
        pathSamples = pathSamples,
        distanceMeters = distanceMeters,
        driveTimeSeconds = driveTimeSeconds,
        averageSpeedMph = averageSpeedMph,
        maxSpeedMph = maxSpeedMph,
        completedCheckpoints = completedCheckpoints,
        totalCheckpoints = totalCheckpoints,
        completionReason = completionReason,
    )

data class DriveSessionMetrics(
    val distanceMeters: Double = 0.0,
    val maxSpeedMph: Double = 0.0,
    val speedSampleCount: Int = 0,
    val speedSumMph: Double = 0.0,
) {
    val avgSpeedMph: Double
        get() = if (speedSampleCount > 0) speedSumMph / speedSampleCount else 0.0
}

data class DriveSessionRouteProgress(
    val routeId: String,
    val routeName: String,
    val completedCheckpointIndexes: Set<Int>,
    val totalCheckpoints: Int,
    val currentProgress: Double,
) {
    val completedCount: Int get() = completedCheckpointIndexes.size
}

data class DriveSessionState(
    val id: String,
    val kind: DriveSessionKind,
    val isRecording: Boolean,
    val isSharing: Boolean,
    val routeId: String? = null,
    val routeName: String? = null,
    val sharingCircleIds: Set<String> = emptySet(),
    val startedAtMs: Long = System.currentTimeMillis(),
    val metrics: DriveSessionMetrics = DriveSessionMetrics(),
    val routeProgress: DriveSessionRouteProgress? = null,
    val backendDriveId: String? = null,
    val backendRouteSessionId: String? = null,
) {
    companion object {
        fun quick(
            saveToProfile: Boolean,
            shareLive: Boolean = false,
            sharingCircleIds: Set<String> = emptySet(),
            id: String = java.util.UUID.randomUUID().toString(),
        ) =
            DriveSessionState(
                id = id,
                kind = DriveSessionKind.QUICK,
                isRecording = saveToProfile,
                isSharing = shareLive,
                sharingCircleIds = sharingCircleIds,
                startedAtMs = System.currentTimeMillis(),
            )
    }
}

sealed class DriveSessionPillPresentation {
    data object Idle : DriveSessionPillPresentation()
    data object PausedSharing : DriveSessionPillPresentation()
    data class Recording(val timeText: String, val distanceText: String) : DriveSessionPillPresentation()
    data class Route(val name: String, val completed: Int, val total: Int) : DriveSessionPillPresentation()
    data class Sharing(
        val squadSummary: String,
        val viewerCount: Int?,
        val remainingText: String?,
    ) : DriveSessionPillPresentation()
    data class RecordingAndSharing(
        val timeText: String,
        val distanceText: String,
        val squadSummary: String,
        val viewerCount: Int?,
        val remainingText: String?,
    ) : DriveSessionPillPresentation()

    /** Stop is handled by the map bottom drive dock when a session is active. */
    val showsStopButton: Boolean
        get() = false
}

/** Single status dot / tab indicator color for this session presentation. */
fun DriveSessionPillPresentation.statusIndicatorColor(): Color? =
    when (this) {
        DriveSessionPillPresentation.Idle -> null
        is DriveSessionPillPresentation.Recording -> DriveSessionColors.recordingGreen
        is DriveSessionPillPresentation.Route -> DriveSessionColors.sessionPurple
        is DriveSessionPillPresentation.Sharing,
        DriveSessionPillPresentation.PausedSharing,
        is DriveSessionPillPresentation.RecordingAndSharing,
        -> DriveSessionColors.sharingRed
    }

fun DriveSessionPillPresentation.mapTabIndicatorColor(): Color? = statusIndicatorColor()

/** Pill stroke / glow accent — one color per presentation (matches status dot). */
fun DriveSessionPillPresentation.pillBorderColor(): Color =
    when (this) {
        DriveSessionPillPresentation.Idle -> Color.White.copy(alpha = 0.2f)
        is DriveSessionPillPresentation.Recording -> DriveSessionColors.recordingGreen
        is DriveSessionPillPresentation.Route -> DriveSessionColors.sessionPurple
        is DriveSessionPillPresentation.Sharing,
        DriveSessionPillPresentation.PausedSharing,
        is DriveSessionPillPresentation.RecordingAndSharing,
        -> DriveSessionColors.sharingRed
    }

object DriveSessionColors {
    val sessionPurple = Color(0xFF8561E0)
    val recordingGreen = Color(0xFF47DB6B)
    val sharingRed = Color(0xFFF2525C)
    val goLivePink = Color(0xFFFA6194)
    val idleMuted = Color.White.copy(alpha = 0.35f)
}
