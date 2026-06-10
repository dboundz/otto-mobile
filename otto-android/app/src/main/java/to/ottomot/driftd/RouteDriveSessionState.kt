package to.ottomot.driftd

import android.location.Location
import to.ottomot.driftd.core.location.LocationFix
import to.ottomot.driftd.core.network.dto.RouteDriveSessionDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto

const val ROUTE_START_DRIVE_RANGE_METERS = 500.0 * 0.3048

data class RouteDriveLocationSample(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double,
    val accuracyMeters: Double?,
    val bearingDegrees: Double?,
) {
    val speedMph: Double get() = speedMps * 2.23694

    companion object {
        fun fromFix(fix: LocationFix, speedMps: Double): RouteDriveLocationSample =
            RouteDriveLocationSample(
                latitude = fix.latitude,
                longitude = fix.longitude,
                speedMps = speedMps.coerceAtLeast(0.0),
                accuracyMeters = fix.accuracyMeters?.toDouble(),
                bearingDegrees = fix.bearingDegrees?.toDouble(),
            )
    }
}

data class RouteDriveSessionState(
    val sessionId: String,
    val activeRouteId: String,
    val driveId: String? = null,
    val status: String,
    val armedAt: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val completedWaypointIndexes: Set<Int> = emptySet(),
    val currentProgress: Double = 0.0,
    val previousRouteDriveLocation: RouteDriveLocationSample? = null,
    val currentLocation: RouteDriveLocationSample? = null,
    val currentSpeedMph: Double = 0.0,
    val maxSpeedMph: Double = 0.0,
    val avgSpeedMph: Double = 0.0,
    val speedSampleCount: Int = 0,
    val lastTriggeredWaypointIndex: Int? = null,
    val lastRouteProgressMeters: Double? = null,
    val stopReason: String? = null,
) {
    val isArmed: Boolean get() = status == "armed"
    val isActive: Boolean get() = status == "active"

    companion object {
        fun fromDto(
            dto: RouteDriveSessionDto,
            routeId: String,
            currentLocation: RouteDriveLocationSample? = null,
        ): RouteDriveSessionState =
            RouteDriveSessionState(
                sessionId = dto.id,
                activeRouteId = routeId,
                driveId = dto.driveId,
                status = dto.status,
                armedAt = dto.armedAt,
                startedAt = dto.startedAt,
                endedAt = dto.endedAt,
                completedWaypointIndexes = dto.completedWaypointIndexes.toSet(),
                currentProgress = dto.currentProgress,
                currentLocation = currentLocation,
                currentSpeedMph = dto.currentSpeedMph,
                maxSpeedMph = dto.maxSpeedMph,
                avgSpeedMph = dto.avgSpeedMph,
                speedSampleCount = if (dto.currentSpeedMph > 0) 1 else 0,
                lastTriggeredWaypointIndex = dto.lastTriggeredWaypointIndex,
                stopReason = dto.stopReason,
            )
    }
}

sealed class RouteDriveFeedbackKind {
    data object StartFailed : RouteDriveFeedbackKind()

    data object Armed : RouteDriveFeedbackKind()

    data object Activated : RouteDriveFeedbackKind()

    data class CheckpointReached(val isFinish: Boolean) : RouteDriveFeedbackKind()

    data class Completed(val summary: DriveCompleteSummary) : RouteDriveFeedbackKind()

    data class Stopped(val summary: DriveCompleteSummary?) : RouteDriveFeedbackKind()

    data object ActivationFailed : RouteDriveFeedbackKind()
}

data class RouteDriveFeedbackEvent(
    val id: Long = System.nanoTime(),
    val kind: RouteDriveFeedbackKind,
)

fun routeStartCoordinate(route: SavedRouteDto): Pair<Double, Double>? {
    route.points.orEmpty()
        .firstOrNull { it.markerType == "start" && it.lat.isFinite() && it.lng.isFinite() }
        ?.let { return it.lat to it.lng }
    route.points.orEmpty()
        .firstOrNull { it.lat.isFinite() && it.lng.isFinite() }
        ?.let { return it.lat to it.lng }
    return null
}

fun isWithinRouteStartDriveRange(
    route: SavedRouteDto,
    latitude: Double?,
    longitude: Double?,
): Boolean {
    val start = routeStartCoordinate(route) ?: return false
    val lat = latitude ?: return false
    val lng = longitude ?: return false
    if (!lat.isFinite() || !lng.isFinite()) return false
    val distanceMeters =
        to.ottomot.driftd.core.event.haversineMeters(
            start.first,
            start.second,
            lat,
            lng,
        )
    return distanceMeters <= ROUTE_START_DRIVE_RANGE_METERS
}
