import CoreLocation
import Foundation

// MARK: - Route drive lifecycle (global GPS ingestion)

extension AppState {
    func clearRouteDriveSessionState() {
        activeRouteDriveSession = nil
        activeRouteDriveRoute = nil
        resetRouteDrivePathSamples()
        isActivatingRouteDriveSession = false
        lastRouteDriveProgressWriteAt = .distantPast
        routeDriveProgressTask?.cancel()
        routeDriveProgressTask = nil
    }

    func ingestRouteDriveLocationSample(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode
    ) async {
        guard let route = activeRouteDriveRoute,
              let session = activeRouteDriveSession,
              session.activeRouteId == route.id else { return }

        let speedMps = max(speedMetersPerSecond, location.speed >= 0 ? location.speed : 0, 0)

        if session.isArmed {
            guard !isActivatingRouteDriveSession else { return }
            guard RouteCheckpointDetector.indicatesDriveMovement(
                location: location,
                speedMetersPerSecond: speedMps,
                movementMode: movementMode
            ) else { return }
            await activateRouteDriveSession(route: route, location: location, speedMps: speedMps)
            return
        }

        guard session.isActive else { return }
        await updateActiveRouteDriveSession(
            route: route,
            location: location,
            speedMps: speedMps,
            forceWrite: false
        )
    }

    @MainActor
    func stopRouteDriveSession(location: CLLocation?) async -> DriveCompleteSummary? {
        guard let session = activeRouteDriveSession else { return nil }
        let route = activeRouteDriveRoute
        routeDriveProgressTask?.cancel()
        isActivatingRouteDriveSession = false

        let speedMph = max(location?.speed ?? 0, 0) * 2.23694
        let completed = Array(session.completedWaypointIndexes).sorted()
        let endedDto = try? await APIClient.shared.stopRouteDriveSession(
            sessionId: session.sessionId,
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completed,
            currentProgress: session.currentProgress,
            lastTriggeredWaypointIndex: session.lastTriggeredWaypointIndex
        )

        let summary: DriveCompleteSummary?
        if let route {
            summary = buildRouteDriveCompleteSummary(
                route: route,
                session: session,
                driveId: endedDto?.driveId ?? session.driveId,
                completedIndexes: session.completedWaypointIndexes,
                endedAt: Date(),
                reason: "stopped"
            )
        } else {
            summary = nil
        }

        clearRouteDriveSessionState()
        activeDriveSession = nil
        await refreshRecentDrives()

        if let summary {
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .stopped(summary: summary))
        }
        return summary
    }

    // MARK: - Private

    private func activateRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMps: Double
    ) async {
        guard let session = activeRouteDriveSession else { return }
        guard !isActivatingRouteDriveSession else { return }
        isActivatingRouteDriveSession = true
        routeDriveProgressTask?.cancel()

        let speedMph = speedMps * 2.23694
        do {
            let dto = try await APIClient.shared.activateRouteDriveSession(
                sessionId: session.sessionId,
                location: location,
                speedMph: speedMph,
                garageCarId: selectedSharingCarID
            )
            await MainActor.run {
                var state = RouteDriveSessionState(dto: dto, routeId: route.id, currentLocation: location)
                state.driveId = dto.driveId ?? state.driveId
                applyStartCheckpointIfNeeded(to: &state, route: route, location: location)
                activeRouteDriveSession = state
                recordLocalRouteDriveSpeedSample(location: location, speedMph: speedMph)
                setRouteDrivePathSamples([DrivePathSample(location: location, speedMph: speedMph)])
                routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .activated)
                isActivatingRouteDriveSession = false
            }
            await updateActiveRouteDriveSession(
                route: route,
                location: location,
                speedMps: speedMps,
                forceWrite: true
            )
        } catch {
            await MainActor.run {
                isActivatingRouteDriveSession = false
                routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .activationFailed)
            }
        }
    }

    private func updateActiveRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMps: Double,
        forceWrite: Bool
    ) async {
        guard var session = activeRouteDriveSession, session.isActive else { return }

        let detection = RouteCheckpointDetector.evaluate(
            routePoints: route.points,
            roadCoordinates: route.toMapLineCoordinates(),
            location: location,
            previousLocation: session.currentLocation ?? session.previousRouteDriveLocation,
            speedMetersPerSecond: speedMps,
            completedIndexes: session.completedWaypointIndexes,
            lastRouteProgressMeters: session.lastRouteProgressMeters
        )
        let speedMph = speedMps * 2.23694
        let hadTrigger = !detection.newlyTriggeredIndexes.isEmpty

        session.completedWaypointIndexes = detection.completedIndexes
        session.currentProgress = detection.currentProgress
        session.previousRouteDriveLocation = session.currentLocation
        session.currentLocation = location
        session.currentSpeedMph = speedMph
        session.maxSpeedMph = max(session.maxSpeedMph, speedMph)
        session.speedSampleCount += 1
        let previousSampleTotal = session.avgSpeedMph * Double(max(0, session.speedSampleCount - 1))
        session.avgSpeedMph = (previousSampleTotal + speedMph) / Double(max(1, session.speedSampleCount))
        session.lastTriggeredWaypointIndex = detection.lastTriggeredWaypointIndex
        session.lastRouteProgressMeters = detection.updatedRouteProgressMeters
        activeRouteDriveSession = session

        let checkpointTotal = RouteCheckpointDetector.routeCheckpointTotal(pointCount: route.points.count)
        syncRouteProgress(from: session, routeName: route.name, totalCheckpoints: checkpointTotal)
        appendRouteDrivePathSample(location: location, speedMph: speedMph)

        if hadTrigger {
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(
                kind: .checkpointReached(isFinish: detection.didTriggerFinalWaypoint)
            )
        }

        if detection.didTriggerFinalWaypoint {
            await completeRouteDriveSession(
                route: route,
                location: location,
                speedMph: speedMph,
                detection: detection
            )
            return
        }

        let shouldWrite = forceWrite || hadTrigger || Date().timeIntervalSince(lastRouteDriveProgressWriteAt) >= 5
        guard shouldWrite else { return }
        lastRouteDriveProgressWriteAt = Date()

        let sessionId = session.sessionId
        routeDriveProgressTask = Task {
            _ = try? await APIClient.shared.updateRouteDriveSessionProgress(
                sessionId: sessionId,
                location: location,
                speedMph: speedMph,
                completedWaypointIndexes: Array(detection.completedIndexes).sorted(),
                currentProgress: detection.currentProgress,
                lastTriggeredWaypointIndex: detection.lastTriggeredWaypointIndex,
                nearestRouteIndex: detection.nearestRouteIndex
            )
        }
    }

    private func completeRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMph: Double,
        detection: RouteCheckpointDetectionResult
    ) async {
        guard let session = activeRouteDriveSession else { return }
        routeDriveProgressTask?.cancel()

        let endedDto = try? await APIClient.shared.completeRouteDriveSession(
            sessionId: session.sessionId,
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: Array(detection.completedIndexes).sorted(),
            currentProgress: 1,
            lastTriggeredWaypointIndex: detection.lastTriggeredWaypointIndex
        )

        let summary = buildRouteDriveCompleteSummary(
            route: route,
            session: session,
            driveId: endedDto?.driveId ?? session.driveId,
            completedIndexes: detection.completedIndexes,
            endedAt: Date(),
            reason: "completed"
        )

        await MainActor.run {
            clearRouteDriveSessionState()
            activeDriveSession = nil
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .completed(summary: summary))
        }
        await refreshRecentDrives()
    }

    private func appendRouteDrivePathSample(location: CLLocation, speedMph: Double) {
        recordRouteDrivePathSample(location: location, speedMph: speedMph)
    }

    private func recordLocalRouteDriveSpeedSample(location: CLLocation, speedMph: Double) {
        guard var session = activeRouteDriveSession else { return }
        session.currentLocation = location
        session.currentSpeedMph = speedMph
        session.maxSpeedMph = max(session.maxSpeedMph, speedMph)
        if speedMph > 0 {
            session.speedSampleCount += 1
            let previousSampleTotal = session.avgSpeedMph * Double(max(0, session.speedSampleCount - 1))
            session.avgSpeedMph = (previousSampleTotal + speedMph) / Double(max(1, session.speedSampleCount))
        }
        activeRouteDriveSession = session
    }

    func buildRouteDriveCompleteSummary(
        route: SavedRouteDTO,
        session: RouteDriveSessionState,
        driveId: String?,
        completedIndexes: Set<Int>,
        endedAt: Date,
        reason: String
    ) -> DriveCompleteSummary {
        let startedAt = session.startedAt ?? endedAt
        let driveTime = max(0, endedAt.timeIntervalSince(startedAt))
        let averageSpeed = session.avgSpeedMph > 0 ? session.avgSpeedMph : session.currentSpeedMph
        let routeCoordinates = route.toMapLineCoordinates()
        let checkpointCoordinates = route.points.compactMap { point -> CLLocationCoordinate2D? in
            let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
            guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { return nil }
            return coordinate
        }
        return DriveCompleteSummary(
            driveId: driveId,
            routeName: route.name,
            routeCoordinates: routeCoordinates,
            checkpointCoordinates: checkpointCoordinates,
            distanceMeters: route.distanceMeters,
            driveTimeSeconds: driveTime,
            averageSpeedMph: averageSpeed,
            maxSpeedMph: session.maxSpeedMph,
            completedCheckpoints: completedIndexes.count,
            totalCheckpoints: route.points.count,
            completionReason: reason
        )
    }

    func applyStartCheckpointIfNeeded(
        to state: inout RouteDriveSessionState,
        route: SavedRouteDTO,
        location: CLLocation?
    ) {
        guard !route.points.isEmpty, !state.completedWaypointIndexes.contains(0) else { return }
        guard let location, let startCoordinate = routeStartCoordinate(route) else { return }
        let start = CLLocation(latitude: startCoordinate.latitude, longitude: startCoordinate.longitude)
        guard location.distance(from: start) <= Self.routeStartDriveRangeMeters else { return }
        state.completedWaypointIndexes.insert(0)
        state.currentProgress = Double(state.completedWaypointIndexes.count) / Double(max(route.points.count, 1))
        state.lastTriggeredWaypointIndex = max(state.lastTriggeredWaypointIndex ?? 0, 0)
    }

    func routeStartCoordinate(_ route: SavedRouteDTO) -> CLLocationCoordinate2D? {
        if let explicitStart = route.points.first(where: { $0.markerType == "start" }) {
            let coordinate = CLLocationCoordinate2D(latitude: explicitStart.lat, longitude: explicitStart.lng)
            guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
            return coordinate
        }
        guard let first = route.points.first else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: first.lat, longitude: first.lng)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return coordinate
    }

    func isWithinRouteStartDriveRange(_ route: SavedRouteDTO, currentLocation: CLLocation?) -> Bool {
        guard
            let startCoordinate = routeStartCoordinate(route),
            let currentLocation
        else { return false }
        let start = CLLocation(latitude: startCoordinate.latitude, longitude: startCoordinate.longitude)
        return currentLocation.distance(from: start) <= Self.routeStartDriveRangeMeters
    }
}
