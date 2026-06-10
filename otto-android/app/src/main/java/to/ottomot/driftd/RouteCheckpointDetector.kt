package to.ottomot.driftd

import android.location.Location
import to.ottomot.driftd.core.event.haversineMeters
import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry
import to.ottomot.driftd.routebuilder.engine.RoutePolylineProjection
import kotlin.math.abs

data class RouteCheckpointDetectionResult(
    val newlyTriggeredIndexes: List<Int>,
    val completedIndexes: Set<Int>,
    val currentProgress: Double,
    val lastTriggeredWaypointIndex: Int?,
    val didTriggerFinalWaypoint: Boolean,
    val nearestRouteIndex: Int?,
    val updatedRouteProgressMeters: Double?,
)

data class CheckpointRouteContext(
    val arcLengthMeters: Double,
    val segmentBearingDegrees: Double,
)

object RouteCheckpointDetector {
    fun routeCheckpointTotal(pointCount: Int): Int = maxOf(pointCount, 1)

    fun evaluate(
        routePoints: List<RoutePointDto>,
        roadCoordinates: List<RouteLatLng>,
        location: RouteDriveLocationSample,
        previousLocation: RouteDriveLocationSample?,
        speedMetersPerSecond: Double,
        completedIndexes: Set<Int>,
        lastRouteProgressMeters: Double? = null,
    ): RouteCheckpointDetectionResult {
        val coordinates =
            routePoints.mapNotNull { point ->
                if (!point.lat.isFinite() || !point.lng.isFinite()) return@mapNotNull null
                point.lat to point.lng
            }
        if (coordinates.isEmpty()) {
            return RouteCheckpointDetectionResult(
                newlyTriggeredIndexes = emptyList(),
                completedIndexes = completedIndexes,
                currentProgress = 0.0,
                lastTriggeredWaypointIndex = completedIndexes.maxOrNull(),
                didTriggerFinalWaypoint = false,
                nearestRouteIndex = null,
                updatedRouteProgressMeters = lastRouteProgressMeters,
            )
        }

        val usesRouteProgress = roadCoordinates.size >= 2
        val checkpointContexts =
            if (usesRouteProgress) {
                checkpointRouteContexts(coordinates, roadCoordinates)
            } else {
                emptyMap()
            }
        val driverProgress =
            if (usesRouteProgress) {
                driverRouteProgress(
                    location = location.latitude to location.longitude,
                    roadCoordinates = roadCoordinates,
                    lastRouteProgressMeters = lastRouteProgressMeters,
                )
            } else {
                null
            }

        val nextIndex = coordinates.indices.firstOrNull { !completedIndexes.contains(it) }
        var updatedCompleted = completedIndexes
        val newlyTriggered = mutableListOf<Int>()

        if (nextIndex != null &&
            shouldTriggerCheckpoint(
                index = nextIndex,
                coordinates = coordinates,
                location = location,
                previousLocation = previousLocation,
                speedMetersPerSecond = speedMetersPerSecond,
                completedIndexes = completedIndexes,
                checkpointContexts = checkpointContexts,
                driverProgressMeters = driverProgress,
            )
        ) {
            updatedCompleted = completedIndexes + nextIndex
            newlyTriggered.add(nextIndex)
        }

        val progress = updatedCompleted.size.toDouble() / coordinates.size.toDouble()
        val finalIndex = coordinates.lastIndex
        val nearestIndex =
            driverProgress?.let { nearestRouteIndex(roadCoordinates, it) }
                ?: nearestRouteIndex(roadCoordinates, location.latitude, location.longitude)

        return RouteCheckpointDetectionResult(
            newlyTriggeredIndexes = newlyTriggered,
            completedIndexes = updatedCompleted,
            currentProgress = progress.coerceIn(0.0, 1.0),
            lastTriggeredWaypointIndex = newlyTriggered.lastOrNull() ?: updatedCompleted.maxOrNull(),
            didTriggerFinalWaypoint = newlyTriggered.contains(finalIndex),
            nearestRouteIndex = nearestIndex,
            updatedRouteProgressMeters = driverProgress ?: lastRouteProgressMeters,
        )
    }

    fun indicatesDriveMovement(
        location: RouteDriveLocationSample,
        speedMetersPerSecond: Double,
        movementMode: String?,
    ): Boolean {
        if (movementMode == "driving") return true
        if (speedMetersPerSecond >= 2.7) return true
        if ((location.speedMps) >= 2.7) return true
        return false
    }

    fun checkpointRouteContexts(
        routeCoordinates: List<RouteLatLng>,
        roadCoordinates: List<RouteLatLng>,
    ): Map<Int, CheckpointRouteContext> {
        if (roadCoordinates.size < 2) return emptyMap()
        val totalLength = RoutePolylineGeometry.polylineTotalLength(roadCoordinates)
        val rawArcLengths = mutableMapOf<Int, Double>()
        routeCoordinates.forEachIndexed { index, coordinate ->
            RoutePolylineGeometry.projectOntoPolyline(coordinate, roadCoordinates)?.let { projection ->
                rawArcLengths[index] = projection.arcLengthMeters
            }
        }

        val contexts = mutableMapOf<Int, CheckpointRouteContext>()
        routeCoordinates.indices.forEach { index ->
            val lowerBound =
                if (index > 0) {
                    contexts[index - 1]?.arcLengthMeters ?: rawArcLengths[index - 1] ?: 0.0
                } else {
                    0.0
                }
            var upperBound =
                if (index < routeCoordinates.lastIndex) {
                    rawArcLengths[index + 1] ?: totalLength
                } else {
                    totalLength
                }
            if (upperBound < lowerBound) upperBound = totalLength
            val projections =
                RoutePolylineGeometry.allProjectionsOntoPolyline(
                    routeCoordinates[index],
                    roadCoordinates,
                ).filter {
                    it.arcLengthMeters >= lowerBound - 1.0 && it.arcLengthMeters <= upperBound + 1.0
                }
            val chosen =
                chooseCheckpointProjection(
                    constrained = projections,
                    coordinate = routeCoordinates[index],
                    lineCoordinates = roadCoordinates,
                    lowerBound = lowerBound,
                    index = index,
                    isFinalCheckpoint = index == routeCoordinates.lastIndex,
                )
            if (chosen != null) {
                contexts[index] =
                    CheckpointRouteContext(
                        arcLengthMeters = chosen.arcLengthMeters,
                        segmentBearingDegrees = chosen.segmentBearingDegrees,
                    )
            }
        }
        return contexts
    }

    fun driverRouteProgress(
        location: RouteLatLng,
        roadCoordinates: List<RouteLatLng>,
        lastRouteProgressMeters: Double?,
    ): Double? {
        if (roadCoordinates.size < 2) return null
        val projections = RoutePolylineGeometry.allProjectionsOntoPolyline(location, roadCoordinates)
        if (projections.isEmpty()) return lastRouteProgressMeters

        if (lastRouteProgressMeters == null) {
            return projections.minByOrNull { it.distanceMeters }?.arcLengthMeters
        }

        val windowBack = 50.0
        val windowForward = 350.0
        val inWindow =
            projections.filter {
                it.arcLengthMeters >= lastRouteProgressMeters - windowBack &&
                    it.arcLengthMeters <= lastRouteProgressMeters + windowForward
            }
        val globalBest = projections.minByOrNull { it.distanceMeters }

        val chosenArc =
            if (globalBest != null) {
                val inWindowBest =
                    inWindow.minWithOrNull(
                        compareBy<RoutePolylineProjection> { it.distanceMeters }
                            .thenBy { abs(it.arcLengthMeters - lastRouteProgressMeters) },
                    )
                if (inWindowBest != null && inWindowBest.distanceMeters <= globalBest.distanceMeters + 25.0) {
                    inWindowBest.arcLengthMeters
                } else if (globalBest.arcLengthMeters >= lastRouteProgressMeters - 30.0) {
                    globalBest.arcLengthMeters
                } else {
                    lastRouteProgressMeters
                }
            } else {
                lastRouteProgressMeters
            }
        return maxOf(lastRouteProgressMeters - 30.0, chosenArc)
    }

    fun shouldTriggerCheckpoint(
        index: Int,
        coordinates: List<RouteLatLng>,
        location: RouteDriveLocationSample,
        previousLocation: RouteDriveLocationSample?,
        speedMetersPerSecond: Double,
        completedIndexes: Set<Int>,
        checkpointContexts: Map<Int, CheckpointRouteContext> = emptyMap(),
        driverProgressMeters: Double? = null,
    ): Boolean {
        val coordinate = coordinates[index]
        val currentDistance =
            haversineMeters(
                location.latitude,
                location.longitude,
                coordinate.first,
                coordinate.second,
            )
        val radius = triggerRadiusMeters(location, speedMetersPerSecond)
        val segmentDistance =
            previousLocation?.let { previous ->
                distanceFromCheckpoint(
                    checkpointLat = coordinate.first,
                    checkpointLng = coordinate.second,
                    previousLat = previous.latitude,
                    previousLng = previous.longitude,
                    currentLat = location.latitude,
                    currentLng = location.longitude,
                )
            } ?: currentDistance
        val closestDistance = minOf(currentDistance, segmentDistance)
        if (closestDistance > radius) return false

        if (index > 0) {
            val previousIndex = completedIndexes.maxOrNull()
            if (previousIndex != index - 1) return false
            val previousCoordinate = coordinates[previousIndex!!]
            val distanceFromPrevious =
                haversineMeters(
                    location.latitude,
                    location.longitude,
                    previousCoordinate.first,
                    previousCoordinate.second,
                )
            if (closestDistance > distanceFromPrevious + gpsForgivenessMeters(location)) {
                return false
            }
        } else if (index != 0) {
            return false
        }

        val checkpointContext = checkpointContexts[index]
        if (checkpointContext == null || driverProgressMeters == null) {
            return true
        }

        val forwardTolerance = progressForwardToleranceMeters(location, speedMetersPerSecond)
        val overshootTolerance = progressOvershootToleranceMeters(location, speedMetersPerSecond)
        if (driverProgressMeters < checkpointContext.arcLengthMeters - forwardTolerance ||
            driverProgressMeters > checkpointContext.arcLengthMeters + overshootTolerance
        ) {
            return false
        }

        return shouldMatchTravelDirection(
            location = location,
            previousLocation = previousLocation,
            speedMetersPerSecond = speedMetersPerSecond,
            expectedBearingDegrees = checkpointContext.segmentBearingDegrees,
        )
    }

    fun angularDifferenceDegrees(lhs: Double, rhs: Double): Double {
        val delta = abs(lhs - rhs) % 360.0
        return if (delta > 180.0) 360.0 - delta else delta
    }

    private fun chooseCheckpointProjection(
        constrained: List<RoutePolylineProjection>,
        coordinate: RouteLatLng,
        lineCoordinates: List<RouteLatLng>,
        lowerBound: Double,
        index: Int,
        isFinalCheckpoint: Boolean,
    ): RoutePolylineProjection? {
        if (constrained.isEmpty()) {
            return RoutePolylineGeometry.projectOntoPolyline(coordinate, lineCoordinates)
        }
        val bestDistance = constrained.minOf { it.distanceMeters }
        val closestCandidates = constrained.filter { it.distanceMeters <= bestDistance + 0.001 }
        if (closestCandidates.size == 1) return closestCandidates.first()
        if (isFinalCheckpoint) {
            val advancedCandidates = closestCandidates.filter { it.arcLengthMeters > lowerBound + 10.0 }
            return advancedCandidates.maxByOrNull { it.arcLengthMeters }
                ?: closestCandidates.maxByOrNull { it.arcLengthMeters }
        }
        if (index == 0 || lowerBound <= 0.0) {
            return closestCandidates.minByOrNull { it.arcLengthMeters }
        }
        val advancedCandidates = closestCandidates.filter { it.arcLengthMeters > lowerBound + 10.0 }
        return advancedCandidates.maxByOrNull { it.arcLengthMeters }
            ?: closestCandidates.maxByOrNull { it.arcLengthMeters }
    }

    private fun shouldMatchTravelDirection(
        location: RouteDriveLocationSample,
        previousLocation: RouteDriveLocationSample?,
        speedMetersPerSecond: Double,
        expectedBearingDegrees: Double,
    ): Boolean {
        val effectiveSpeed = maxOf(speedMetersPerSecond, location.speedMps, 0.0)
        if (effectiveSpeed < 1.5) return true

        val travelBearing =
            when {
                previousLocation != null -> {
                    val delta =
                        haversineMeters(
                            previousLocation.latitude,
                            previousLocation.longitude,
                            location.latitude,
                            location.longitude,
                        )
                    if (delta >= 5.0) {
                        RoutePolylineGeometry.bearingBetween(
                            previousLocation.latitude to previousLocation.longitude,
                            location.latitude to location.longitude,
                        )
                    } else {
                        location.bearingDegrees?.takeIf { it >= 0.0 }
                    }
                }
                location.bearingDegrees != null && location.bearingDegrees >= 0.0 && effectiveSpeed >= 2.7 ->
                    location.bearingDegrees
                else -> null
            }
        if (travelBearing == null) return true
        return angularDifferenceDegrees(travelBearing, expectedBearingDegrees) <= 90.0
    }

    private fun distanceFromCheckpoint(
        checkpointLat: Double,
        checkpointLng: Double,
        previousLat: Double,
        previousLng: Double,
        currentLat: Double,
        currentLng: Double,
    ): Double {
        val segment = listOf(previousLat to previousLng, currentLat to currentLng)
        val projection =
            RoutePolylineGeometry.projectOntoPolyline(
                checkpointLat to checkpointLng,
                segment,
            )
        return projection?.distanceMeters
            ?: haversineMeters(checkpointLat, checkpointLng, currentLat, currentLng)
    }

    private fun triggerRadiusMeters(
        location: RouteDriveLocationSample,
        speedMetersPerSecond: Double,
    ): Double {
        val accuracy = location.accuracyMeters?.takeIf { it >= 0.0 } ?: 25.0
        val speedAllowance = maxOf(speedMetersPerSecond, location.speedMps, 0.0) * 1.8
        return minOf(125.0, maxOf(35.0, 35.0 + accuracy + speedAllowance))
    }

    private fun gpsForgivenessMeters(location: RouteDriveLocationSample): Double {
        val accuracy = location.accuracyMeters?.takeIf { it >= 0.0 } ?: 25.0
        return minOf(80.0, maxOf(25.0, accuracy * 1.5))
    }

    private fun progressForwardToleranceMeters(
        location: RouteDriveLocationSample,
        speedMetersPerSecond: Double,
    ): Double {
        val accuracy = location.accuracyMeters?.takeIf { it >= 0.0 } ?: 25.0
        val speedAllowance = maxOf(speedMetersPerSecond, location.speedMps, 0.0) * 0.8
        return minOf(120.0, maxOf(40.0, 40.0 + accuracy + speedAllowance))
    }

    private fun progressOvershootToleranceMeters(
        location: RouteDriveLocationSample,
        speedMetersPerSecond: Double,
    ): Double {
        val accuracy = location.accuracyMeters?.takeIf { it >= 0.0 } ?: 25.0
        val speedAllowance = maxOf(speedMetersPerSecond, location.speedMps, 0.0) * 1.2
        return minOf(200.0, maxOf(80.0, 80.0 + accuracy + speedAllowance))
    }

    private fun nearestRouteIndex(
        roadCoordinates: List<RouteLatLng>,
        arcLengthMeters: Double,
    ): Int? {
        if (roadCoordinates.size < 2) return null
        var cumulativeDistance = 0.0
        for (index in 0 until roadCoordinates.lastIndex) {
            val start = roadCoordinates[index]
            val end = roadCoordinates[index + 1]
            val segmentLength = haversineMeters(start.first, start.second, end.first, end.second)
            if (arcLengthMeters <= cumulativeDistance + segmentLength) {
                return index
            }
            cumulativeDistance += segmentLength
        }
        return roadCoordinates.lastIndex
    }

    private fun nearestRouteIndex(
        roadCoordinates: List<RouteLatLng>,
        latitude: Double,
        longitude: Double,
    ): Int? {
        if (roadCoordinates.isEmpty()) return null
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        roadCoordinates.forEachIndexed { index, coordinate ->
            val distance = haversineMeters(latitude, longitude, coordinate.first, coordinate.second)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }
}

fun savedRouteRoadLatLng(route: SavedRouteDto): List<RouteLatLng> {
    val road =
        route.roadCoordinates.orEmpty()
            .filter { it.lat.isFinite() && it.lng.isFinite() }
            .map { it.lat to it.lng }
    if (road.size >= 2) return road
    return route.points.orEmpty()
        .filter { (it.markerType ?: "path") == "path" && it.lat.isFinite() && it.lng.isFinite() }
        .map { it.lat to it.lng }
        .takeIf { it.size >= 2 }
        ?: route.points.orEmpty()
            .filter { it.lat.isFinite() && it.lng.isFinite() }
            .map { it.lat to it.lng }
}

fun applyStartCheckpointIfNeeded(
    state: RouteDriveSessionState,
    route: SavedRouteDto,
    location: RouteDriveLocationSample?,
): RouteDriveSessionState {
    val points = route.points.orEmpty()
    if (points.isEmpty() || state.completedWaypointIndexes.contains(0)) return state
    val loc = location ?: return state
    val start = routeStartCoordinate(route) ?: return state
    val distanceMeters =
        haversineMeters(
            start.first,
            start.second,
            loc.latitude,
            loc.longitude,
        )
    if (distanceMeters > ROUTE_START_DRIVE_RANGE_METERS) return state
    val completed = state.completedWaypointIndexes + 0
    return state.copy(
        completedWaypointIndexes = completed,
        currentProgress = completed.size.toDouble() / maxOf(points.size, 1).toDouble(),
        lastTriggeredWaypointIndex = maxOf(state.lastTriggeredWaypointIndex ?: 0, 0),
    )
}
