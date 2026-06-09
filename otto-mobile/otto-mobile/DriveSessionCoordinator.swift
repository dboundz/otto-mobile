import CoreLocation
import Foundation

// MARK: - Drive session lifecycle (AppState extension)

extension AppState {
    var hasActiveDriveSession: Bool {
        activeDriveSession != nil || isSharingEnabled || activeRouteDriveSession != nil
    }

    /// Continuous background GPS while a user-started drive session is active.
    var needsBackgroundLocationUpdates: Bool {
        activeDriveSession != nil || activeRouteDriveSession != nil
    }

    func driveSessionPillPresentation(
        now: Date,
        routeName: String?,
        viewerCount: Int?
    ) -> DriveSessionPillPresentation {
        let session = activeDriveSession
        let sharingActive = isSharingSessionActive
        let sharingPaused = sharingActive
            && sharingSessionMode == .drivingOnly
            && isDrivingOnlyBroadcastPaused
        let recording = session?.isRecording == true
            || (sharingActive && sharingSaveDriveEnabled && activeDriveID != nil)
            || activeRouteDriveSession != nil
        let routeActive = activeRouteDriveSession != nil || session?.kind == .route

        let remaining = sharingRemainingText(now: now)
        let squad = sharingAudienceLabel

        if !sharingActive && session == nil && !routeActive {
            return .idle
        }

        if sharingPaused && !recording && !routeActive {
            return .pausedSharing
        }

        let metrics = session?.metrics ?? localDriveSessionMetricsFallback()
        let start = session?.startedAt ?? sharingSessionStartedAt ?? Date()
        let timeText = formatDriveSessionDuration(from: start, now: now)
        let distanceText = formatDriveSessionDistance(
            metrics.distanceMeters > 0 ? metrics.distanceMeters : activeDriveDistanceMeters
        )

        if routeActive, let name = routeName ?? session?.routeName {
            let completed = session?.routeProgress?.completedCount
                ?? activeRouteDriveSession?.completedWaypointIndexes.count
                ?? 0
            let total = max(session?.routeProgress?.totalCheckpoints ?? completed, 1)
            if sharingActive && recording {
                return .recordingAndSharing(
                    timeText: timeText,
                    distanceText: distanceText,
                    squadSummary: squad,
                    viewerCount: viewerCount,
                    remainingText: remaining
                )
            }
            if sharingActive {
                if sharingPaused { return .pausedSharing }
                return .sharing(squadSummary: squad, viewerCount: viewerCount, remainingText: remaining)
            }
            return .route(name: name, completed: completed, total: total)
        }

        if sharingActive && recording {
            return .recordingAndSharing(
                timeText: timeText,
                distanceText: distanceText,
                squadSummary: squad,
                viewerCount: viewerCount,
                remainingText: remaining
            )
        }
        if sharingActive {
            if sharingPaused { return .pausedSharing }
            return .sharing(squadSummary: squad, viewerCount: viewerCount, remainingText: remaining)
        }
        if recording || session != nil {
            return .recording(timeText: timeText, distanceText: distanceText)
        }
        return .idle
    }

    private func localDriveSessionMetricsFallback() -> DriveSessionMetrics {
        var m = DriveSessionMetrics()
        m.distanceMeters = activeDriveDistanceMeters
        m.maxSpeedMph = activeDriveMaxSpeedMph
        return m
    }

    func sharingRemainingText(now: Date = Date()) -> String? {
        guard let remaining = sharingRemainingSeconds(now: now) else { return nil }
        let minutes = Int(ceil(remaining / 60))
        if minutes >= 60 {
            let hours = minutes / 60
            let mins = minutes % 60
            return mins > 0 ? "\(hours)h \(mins)m left" : "\(hours)h left"
        }
        return "\(max(1, minutes))m left"
    }

    func formatDriveSessionDuration(from start: Date, now: Date) -> String {
        let elapsed = max(0, Int(now.timeIntervalSince(start)))
        let hours = elapsed / 3600
        let minutes = (elapsed % 3600) / 60
        let seconds = elapsed % 60
        if hours > 0 {
            return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }

    func formatDriveSessionDistance(_ meters: Double) -> String {
        let miles = meters / 1609.344
        if miles < 0.1 { return "0.0 mi" }
        return String(format: "%.1f mi", miles)
    }

    @discardableResult
    func startQuickDrive(
        saveToProfile: Bool = true,
        shareLive: Bool = false,
        sharingCircleIDs: Set<String> = []
    ) -> Bool {
        guard activeDriveSession == nil else { return false }
        var sessionCircleIDs: Set<String> = []
        if shareLive {
            guard startSharingForDriveStart(circleIDs: sharingCircleIDs) else { return false }
            sessionCircleIDs = sharingCircleIDs
        }
        activeDriveSession = .quick(
            saveToProfile: saveToProfile,
            shareLive: shareLive,
            sharingCircleIDs: sessionCircleIDs
        )
        if saveToProfile {
            Task { await startDriveRecordingIfNeeded(location: nil, title: "Quick Drive") }
        }
        showToast("Drive started", icon: "steeringwheel")
        return true
    }

    func beginRouteDriveSession(
        route: SavedRouteDTO,
        shareLive: Bool,
        routeSession: RouteDriveSessionState,
        recordToProfile: Bool = true
    ) {
        let checkpointTotal = RouteCheckpointDetector.routeCheckpointTotal(pointCount: route.points.count)
        activeRouteDriveRoute = route
         resetRouteDrivePathSamples()
        activeDriveSession = DriveSession(
            id: UUID(),
            kind: .route,
            isRecording: recordToProfile,
            isSharing: shareLive,
            routeId: route.id,
            routeName: route.name,
            sharingCircleIDs: shareLive ? sharingCircleIDs : [],
            startedAt: Date(),
            metrics: DriveSessionMetrics(),
            routeProgress: DriveSessionRouteProgress(
                routeId: route.id,
                routeName: route.name,
                completedCheckpointIndexes: routeSession.completedWaypointIndexes,
                totalCheckpoints: max(checkpointTotal, 1),
                currentProgress: routeSession.currentProgress
            ),
            backendDriveId: routeSession.driveId,
            backendRouteSessionId: routeSession.sessionId
        )
        activeRouteDriveSession = routeSession
        if routeSession.isArmed {
            turnByTurnNavigationManager.speakReadyWhenYouAreNow()
        }
    }

    func syncRouteProgress(from routeSession: RouteDriveSessionState, routeName: String, totalCheckpoints: Int) {
        activeRouteDriveSession = routeSession
        guard var session = activeDriveSession else { return }
        session.routeProgress = DriveSessionRouteProgress(
            routeId: routeSession.activeRouteId,
            routeName: routeName,
            completedCheckpointIndexes: routeSession.completedWaypointIndexes,
            totalCheckpoints: max(totalCheckpoints, 1),
            currentProgress: routeSession.currentProgress
        )
        session.backendDriveId = routeSession.driveId
        session.metrics.maxSpeedMph = max(session.metrics.maxSpeedMph, routeSession.maxSpeedMph)
        activeDriveSession = session
    }

    func ingestDriveSessionSample(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode
    ) async {
        if var session = activeDriveSession {
            let speedMph = speedMetersPerSecond * 2.23694
            session.metrics.ingest(location: location, speedMph: speedMph, movementMode: movementMode)
            activeDriveSession = session
        }

        if activeRouteDriveSession != nil, activeRouteDriveRoute != nil {
            await ingestRouteDriveLocationSample(
                location: location,
                speedMetersPerSecond: speedMetersPerSecond,
                movementMode: movementMode
            )
        }

        let routeDriveRecordingActive = activeRouteDriveSession?.isActive == true

        if isSharingEnabled {
            await pushPresence(
                location: location,
                speedMetersPerSecond: speedMetersPerSecond,
                movementMode: movementMode
            )
            if !routeDriveRecordingActive {
                await throttledRecordDrivePathSample(
                    location: location,
                    speedMetersPerSecond: speedMetersPerSecond,
                    movementMode: movementMode
                )
            }
        } else if activeDriveSession?.isRecording == true, !routeDriveRecordingActive {
            await throttledRecordDrivePathSampleForSession(
                location: location,
                speedMetersPerSecond: speedMetersPerSecond,
                movementMode: movementMode
            )
        }
    }

    func setDriveSessionSaveEnabled(_ enabled: Bool) {
        if var session = activeDriveSession {
            session.isRecording = enabled
            activeDriveSession = session
            if enabled {
                Task { await startDriveRecordingIfNeeded(location: nil, title: driveRecordingTitle(for: session.kind)) }
            } else {
                Task { await stopActiveDrive(location: nil) }
            }
            return
        }
        setSharingSaveDriveEnabled(enabled)
    }

    func stopDriveSession(location: CLLocation?) async -> DriveSessionCompletionPayload? {
        let session = activeDriveSession
        let routeSession = activeRouteDriveSession

        if isSharingEnabled {
            await stopSharingForDriveSessionEnd()
        }

        if let session, session.isRecording {
            if activeDriveID == nil {
                if let backendId = session.backendDriveId, !backendId.isEmpty {
                    activeDriveID = backendId
                } else {
                    await startDriveRecordingIfNeeded(
                        location: location,
                        title: driveRecordingTitle(for: session.kind)
                    )
                }
            }
            activeDriveDistanceMeters = max(activeDriveDistanceMeters, session.metrics.distanceMeters)
            activeDriveMaxSpeedMph = max(activeDriveMaxSpeedMph, session.metrics.maxSpeedMph)
        }

        let driveId = session?.backendDriveId ?? activeDriveID
        let sessionMetrics = session?.metrics
        let trailSamples = sessionMetrics?.recordedPath ?? activeDrivePathTrail
        var distance = max(sessionMetrics?.distanceMeters ?? 0, activeDriveDistanceMeters)
        if distance <= 0 {
            distance = DriveSpeedGradient.polylineDistanceMeters(from: trailSamples)
        }
        let trailMaxSpeed = trailSamples.map(\.speedMph).max() ?? 0
        let maxSpeed = max(sessionMetrics?.maxSpeedMph ?? 0, activeDriveMaxSpeedMph, trailMaxSpeed)
        let elapsed = session.map { Date().timeIntervalSince($0.startedAt) } ?? 0
        let averageSpeed = DriveAverageSpeed.resolvedMph(
            storedAvg: sessionMetrics?.avgSpeedMph ?? 0,
            distanceMeters: distance,
            durationSeconds: elapsed
        )

        if activeDriveID != nil {
            let archiveInput: PendingDriveArchiveInput? = {
                guard let session, session.isRecording else { return nil }
                return pendingArchiveInput(
                    failurePhase: "end",
                    kind: session.kind,
                    title: driveRecordingTitle(for: session.kind),
                    startedAt: session.startedAt,
                    distanceMeters: distance,
                    maxSpeedMph: maxSpeed,
                    avgSpeedMph: averageSpeed,
                    backendDriveId: activeDriveID,
                    routeId: session.routeId,
                    routeName: session.routeName,
                    pathSamples: trailSamples
                )
            }()
            await stopActiveDrive(
                location: location,
                distanceMeters: distance,
                maxSpeedMph: maxSpeed,
                avgSpeedMph: averageSpeed > 0 ? averageSpeed : nil,
                archiveOnFailure: archiveInput
            )
        }

        let recordedCoordinates =
            session?.metrics.recordedPath.map(\.coordinate)
            ?? activeDrivePathTrail.map(\.coordinate)

        activeDriveSession = nil
        clearRouteDriveSessionState()

        if let session {
            OttoAnalytics.logDriveCompleted(
                kind: OttoAnalytics.analyticsDriveKind(session.kind),
                distanceMeters: distance
            )
        }

        guard let session else { return nil }

        TabSoundPlayer.shared.playRouteFinished()

        return DriveSessionCompletionPayload(
            driveId: driveId,
            kind: session.kind,
            routeName: session.routeName,
            routeCoordinates: recordedCoordinates,
            checkpointCoordinates: [],
            distanceMeters: distance,
            driveTimeSeconds: elapsed,
            averageSpeedMph: averageSpeed,
            maxSpeedMph: maxSpeed,
            completedCheckpoints: session.routeProgress?.completedCount ?? routeSession?.completedWaypointIndexes.count ?? 0,
            totalCheckpoints: session.routeProgress?.totalCheckpoints ?? 0,
            completionReason: "stopped"
        )
    }

    func persistCompletedDriveToProfile(from summary: DriveCompleteSummary) async -> String? {
        if let existingId = summary.driveId?.trimmingCharacters(in: .whitespacesAndNewlines), !existingId.isEmpty {
            await refreshRecentDrives()
            return existingId
        }

        guard !currentUserID.isEmpty else { return nil }

        let title = summary.routeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Quick Drive"
            : summary.routeName
        let startCoordinate = summary.routeCoordinates.first
        let endCoordinate = summary.routeCoordinates.last
        let pathSamples = summary.routeCoordinates.map {
            DrivePathSample(coordinate: $0, speedMph: summary.maxSpeedMph)
        }
        let endedAt = Date()
        let startedAt = endedAt.addingTimeInterval(-summary.driveTimeSeconds)

        func persistOnce() async throws -> String {
            let sharedCircleIds = Array(sharingCircleIDs)
            let driveCircleID = sharedCircleIds.first ?? selectedCircleID
            let drive = try await APIClient.shared.startDrive(
                userId: currentUserID,
                circleId: driveCircleID.isEmpty ? nil : driveCircleID,
                sharingAudience: SharingAudience.onlyMe.rawValue,
                sharedCircleIds: sharedCircleIds,
                title: title,
                location: startCoordinate.map { (lat: $0.latitude, lng: $0.longitude) }
            )
            if !pathSamples.isEmpty {
                try await APIClient.shared.appendDrivePathSamples(driveId: drive.id, samples: pathSamples)
            }
            try await APIClient.shared.endDrive(
                driveId: drive.id,
                location: endCoordinate.map { (lat: $0.latitude, lng: $0.longitude) },
                distanceMeters: summary.distanceMeters > 0 ? summary.distanceMeters : nil,
                maxSpeedMph: summary.maxSpeedMph > 0 ? summary.maxSpeedMph : nil,
                avgSpeedMph: summary.averageSpeedMph > 0 ? summary.averageSpeedMph : nil
            )
            return drive.id
        }

        do {
            let driveId = try await persistOnce()
            await refreshRecentDrives()
            return driveId
        } catch {
            do {
                let driveId = try await persistOnce()
                await refreshRecentDrives()
                return driveId
            } catch {
                let kind: DriveSessionKind = summary.totalCheckpoints > 0 ? .route : .quick
                await MainActor.run {
                    archivePendingDrive(
                        from: pendingArchiveInput(
                            failurePhase: "persist",
                            kind: kind,
                            title: title,
                            startedAt: startedAt,
                            endedAt: endedAt,
                            distanceMeters: summary.distanceMeters,
                            maxSpeedMph: summary.maxSpeedMph,
                            avgSpeedMph: summary.averageSpeedMph,
                            backendDriveId: nil,
                            routeName: summary.routeName,
                            pathSamples: pathSamples
                        )
                    )
                }
                return nil
            }
        }
    }

    func driveRecordingTitle(for kind: DriveSessionKind) -> String {
        switch kind {
        case .quick: return "Quick Drive"
        case .route: return "Route Drive"
        case .live: return "Live Drive Session"
        }
    }

    func startDriveRecordingIfNeeded(location: CLLocation?, title: String) async {
        guard activeDriveID == nil else { return }
        guard !currentUserID.isEmpty else { return }

        do {
            let sharedCircleIds = Array(sharingCircleIDs)
            let driveCircleID = sharedCircleIds.first ?? selectedCircleID
            let drive = try await APIClient.shared.startDrive(
                userId: currentUserID,
                circleId: driveCircleID.isEmpty ? nil : driveCircleID,
                sharingAudience: SharingAudience.onlyMe.rawValue,
                sharedCircleIds: sharedCircleIds,
                title: title,
                location: location.map { (lat: $0.coordinate.latitude, lng: $0.coordinate.longitude) }
            )
            activeDriveID = drive.id
            activeDriveDistanceMeters = 0
            activeDriveMaxSpeedMph = 0
            resetActiveDrivePathTrail()
            lastDriveLocationForDistance = location
            let sessionKind = activeDriveSession?.kind ?? .quick
            let routeID = activeDriveSession?.routeId
            OttoAnalytics.logDriveStarted(
                kind: OttoAnalytics.analyticsDriveKind(sessionKind),
                routeID: routeID
            )
            if var session = activeDriveSession {
                session.backendDriveId = drive.id
                activeDriveSession = session
            }
            await refreshRecentDrives()
        } catch {}
    }

    func ensureLiveDriveSession(saveToProfile: Bool) {
        guard activeDriveSession == nil else {
            if var session = activeDriveSession {
                session.isSharing = true
                session.isRecording = saveToProfile || session.isRecording
                activeDriveSession = session
            }
            return
        }
        activeDriveSession = DriveSession(
            id: UUID(),
            kind: .live,
            isRecording: saveToProfile,
            isSharing: true,
            routeId: nil,
            routeName: nil,
            sharingCircleIDs: sharingCircleIDs,
            startedAt: sharingSessionStartedAt ?? Date(),
            metrics: DriveSessionMetrics(),
            routeProgress: nil,
            backendDriveId: activeDriveID,
            backendRouteSessionId: nil
        )
    }

    func stopLiveSharingOnly() {
        let stoppedCircleIDs = Array(sharingCircleIDs)
        isSharingEnabled = false
        sharingSessionStartedAt = nil
        drivingOnlyNotDrivingInactiveEmitted = false
        resetDrivingOnlyPauseForSharingStop()
        persistSharingState()
        if var session = activeDriveSession {
            session.isSharing = false
            activeDriveSession = session
        }
        Task { await markPresenceInactive(circleIDs: stoppedCircleIDs) }
    }

    func throttledRecordDrivePathSampleForSession(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode
    ) async {
        let shouldRecord = activeDriveSession?.isRecording == true
        guard shouldRecord else { return }
        if activeDriveID == nil {
            await startDriveRecordingIfNeeded(
                location: location,
                title: driveRecordingTitle(for: activeDriveSession?.kind ?? .quick)
            )
        }
        guard activeDriveID != nil else { return }
        let now = Date()
        guard now.timeIntervalSince(lastDrivePathNetworkAt) >= Self.minDrivePathInterval else { return }
        lastDrivePathNetworkAt = now
        await appendDrivePoint(
            location: location,
            speedMetersPerSecond: speedMetersPerSecond,
            movementMode: movementMode
        )
    }
}
