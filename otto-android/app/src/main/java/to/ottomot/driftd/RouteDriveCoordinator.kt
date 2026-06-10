package to.ottomot.driftd

import com.mapbox.geojson.Point
import java.time.Instant
import kotlinx.coroutines.Job
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.core.network.dto.RouteDriveLocationSampleDto
import to.ottomot.driftd.core.network.dto.RouteDriveSessionRequestDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto

private const val MAX_ROUTE_DRIVE_PATH_SAMPLES = 2_000
private const val ROUTE_DRIVE_PROGRESS_WRITE_INTERVAL_MS = 5_000L

internal class RouteDriveCoordinator(
    private val repository: OttoDataRepository,
) {
    private var progressWriteJob: Job? = null
    private var lastProgressWriteAtMs: Long = 0L
    private var isActivating = false

    fun cancelProgressJob() {
        progressWriteJob?.cancel()
        progressWriteJob = null
    }

    fun buildSessionRequest(
        location: RouteDriveLocationSample?,
        speedMph: Double,
        completedWaypointIndexes: List<Int>? = null,
        currentProgress: Double? = null,
        lastTriggeredWaypointIndex: Int? = null,
        nearestRouteIndex: Int? = null,
        garageCarId: String? = null,
    ): RouteDriveSessionRequestDto =
        RouteDriveSessionRequestDto(
            location =
                location?.let {
                    RouteDriveLocationSampleDto(
                        lat = it.latitude,
                        lng = it.longitude,
                        speedMph = speedMph.coerceAtLeast(0.0),
                        heading = it.bearingDegrees?.takeIf { bearing -> bearing >= 0.0 },
                        accuracyMeters = it.accuracyMeters?.takeIf { accuracy -> accuracy >= 0.0 },
                        capturedAt = Instant.now().toString(),
                    )
                },
            completedWaypointIndexes = completedWaypointIndexes,
            currentProgress = currentProgress,
            lastTriggeredWaypointIndex = lastTriggeredWaypointIndex,
            nearestRouteIndex = nearestRouteIndex,
            garageCarId = garageCarId?.trim()?.takeIf { it.isNotEmpty() },
        )

    suspend fun startSession(routeId: String) = repository.startRouteDriveSession(routeId)

    suspend fun activateSession(
        sessionId: String,
        location: RouteDriveLocationSample,
        speedMph: Double,
        garageCarId: String?,
    ) = repository.activateRouteDriveSession(
        sessionId,
        buildSessionRequest(
            location = location,
            speedMph = speedMph,
            garageCarId = garageCarId,
        ),
    )

    suspend fun updateProgress(
        sessionId: String,
        location: RouteDriveLocationSample,
        speedMph: Double,
        completedWaypointIndexes: List<Int>,
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?,
        nearestRouteIndex: Int?,
    ) = repository.updateRouteDriveSessionProgress(
        sessionId,
        buildSessionRequest(
            location = location,
            speedMph = speedMph,
            completedWaypointIndexes = completedWaypointIndexes,
            currentProgress = currentProgress,
            lastTriggeredWaypointIndex = lastTriggeredWaypointIndex,
            nearestRouteIndex = nearestRouteIndex,
        ),
    )

    suspend fun completeSession(
        sessionId: String,
        location: RouteDriveLocationSample?,
        speedMph: Double,
        completedWaypointIndexes: List<Int>,
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?,
    ) = repository.completeRouteDriveSession(
        sessionId,
        buildSessionRequest(
            location = location,
            speedMph = speedMph,
            completedWaypointIndexes = completedWaypointIndexes,
            currentProgress = currentProgress,
            lastTriggeredWaypointIndex = lastTriggeredWaypointIndex,
        ),
    )

    suspend fun stopSession(
        sessionId: String,
        location: RouteDriveLocationSample?,
        speedMph: Double,
        completedWaypointIndexes: List<Int>,
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?,
    ) = repository.stopRouteDriveSession(
        sessionId,
        buildSessionRequest(
            location = location,
            speedMph = speedMph,
            completedWaypointIndexes = completedWaypointIndexes,
            currentProgress = currentProgress,
            lastTriggeredWaypointIndex = lastTriggeredWaypointIndex,
        ),
    )

    fun buildCompleteSummary(
        route: SavedRouteDto,
        session: RouteDriveSessionState,
        driveId: String?,
        completedIndexes: Set<Int>,
        endedAtMs: Long,
        reason: String,
        pathSamples: List<DrivePathSample>,
    ): DriveCompleteSummary {
        val startedAtMs =
            session.startedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: endedAtMs
        val driveTimeSeconds = maxOf(0L, (endedAtMs - startedAtMs) / 1000L)
        val averageSpeed =
            if (session.avgSpeedMph > 0.0) session.avgSpeedMph else session.currentSpeedMph
        val routeCoordinates =
            lineCoordinatesFromSavedRoute(route).map { point ->
                LatLngPair(lat = point.latitude(), lng = point.longitude())
            }
        val checkpointCoordinates =
            route.points.orEmpty().mapNotNull { point ->
                if (!point.lat.isFinite() || !point.lng.isFinite()) return@mapNotNull null
                LatLngPair(lat = point.lat, lng = point.lng)
            }
        return DriveCompleteSummary(
            driveId = driveId,
            routeName = route.name,
            routeCoordinates = routeCoordinates,
            checkpointCoordinates = checkpointCoordinates,
            pathSamples = pathSamples,
            distanceMeters = route.distanceMeters ?: 0.0,
            driveTimeSeconds = driveTimeSeconds,
            averageSpeedMph = averageSpeed,
            maxSpeedMph = session.maxSpeedMph,
            completedCheckpoints = completedIndexes.size,
            totalCheckpoints = route.points.orEmpty().size,
            completionReason = reason,
        )
    }

    fun checkpointCoordinatesForSummary(
        route: SavedRouteDto,
        completedIndexes: Set<Int>,
    ): List<LatLngPair> =
        route.points.orEmpty().mapIndexedNotNull { index, point ->
            if (!completedIndexes.contains(index)) return@mapIndexedNotNull null
            if (!point.lat.isFinite() || !point.lng.isFinite()) return@mapIndexedNotNull null
            LatLngPair(lat = point.lat, lng = point.lng)
        }

    fun appendPathSample(
        existing: List<DrivePathSample>,
        location: RouteDriveLocationSample,
        speedMph: Double,
    ): List<DrivePathSample> {
        val updated = existing.toMutableList()
        updated.add(
            DrivePathSample(
                lat = location.latitude,
                lng = location.longitude,
                speedMph = speedMph,
            ),
        )
        if (updated.size > MAX_ROUTE_DRIVE_PATH_SAMPLES) {
            val overflow = updated.size - MAX_ROUTE_DRIVE_PATH_SAMPLES
            repeat(overflow) { updated.removeAt(0) }
        }
        return updated
    }

    fun recordSpeedSample(
        session: RouteDriveSessionState,
        location: RouteDriveLocationSample,
        speedMph: Double,
    ): RouteDriveSessionState {
        var updated =
            session.copy(
                currentLocation = location,
                currentSpeedMph = speedMph,
                maxSpeedMph = maxOf(session.maxSpeedMph, speedMph),
            )
        if (speedMph > 0.0) {
            val count = updated.speedSampleCount + 1
            val previousTotal = updated.avgSpeedMph * maxOf(0, updated.speedSampleCount)
            updated =
                updated.copy(
                    speedSampleCount = count,
                    avgSpeedMph = (previousTotal + speedMph) / count.toDouble(),
                )
        }
        return updated
    }

    fun shouldWriteProgress(
        forceWrite: Boolean,
        hadTrigger: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (forceWrite || hadTrigger) return true
        return nowMs - lastProgressWriteAtMs >= ROUTE_DRIVE_PROGRESS_WRITE_INTERVAL_MS
    }

    fun markProgressWritten(nowMs: Long = System.currentTimeMillis()) {
        lastProgressWriteAtMs = nowMs
    }

    fun resetProgressWriteClock() {
        lastProgressWriteAtMs = 0L
        isActivating = false
        cancelProgressJob()
    }

    fun setActivating(active: Boolean) {
        isActivating = active
    }

    fun isActivating(): Boolean = isActivating

    fun assignProgressJob(job: Job?) {
        progressWriteJob?.cancel()
        progressWriteJob = job
    }
}

internal fun mapPointsFromSavedRouteForDrive(
    routePoints: List<to.ottomot.driftd.core.network.dto.RoutePointDto>?,
    idPrefix: String,
): List<RouteMapPoint> =
    routePoints.orEmpty().mapIndexedNotNull { offset, point ->
        val type = point.markerType ?: "path"
        if (type == "path") return@mapIndexedNotNull null
        RouteMapPoint(
            id = "$idPrefix-$offset",
            lat = point.lat,
            lng = point.lng,
            markerType = type,
            index = offset,
        )
    }

internal fun RouteMapPoint.isRouteDriveCompleted(completedWaypointIndexes: Set<Int>): Boolean =
    completedWaypointIndexes.contains(index)
