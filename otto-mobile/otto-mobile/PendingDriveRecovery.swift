import CoreLocation
import Foundation

// MARK: - Pending drive local recovery

extension AppState {
    var recordDriveOnStartEnabled: Bool {
        get {
            if UserDefaults.standard.object(forKey: StorageKeys.recordDriveOnStartEnabled) != nil {
                return UserDefaults.standard.bool(forKey: StorageKeys.recordDriveOnStartEnabled)
            }
            return true
        }
        set {
            UserDefaults.standard.set(newValue, forKey: StorageKeys.recordDriveOnStartEnabled)
        }
    }

    func reloadPendingDriveArchives() {
        let archives = PendingDriveStore.purgeExpired(from: PendingDriveStore.load())
        replacePendingDriveArchives(archives)
        PendingDriveStore.save(archives)
    }

    func purgeExpiredPendingDrives() {
        reloadPendingDriveArchives()
    }

    func clearPendingDriveArchives() {
        replacePendingDriveArchives([])
        PendingDriveStore.clearAll()
    }

    @MainActor
    func archivePendingDrive(from input: PendingDriveArchiveInput) {
        guard let archive = PendingDriveStore.makeArchive(from: input) else { return }
        updatePendingDriveArchives { $0.insert(archive, at: 0) }
        PendingDriveStore.save(pendingDriveArchives)
        showToast(String(localized: "drive_pending_archived_toast"), icon: "externaldrive.badge.exclamationmark")
    }

    @MainActor
    func deletePendingDrive(localId: UUID) {
        updatePendingDriveArchives { archives in
            archives.removeAll { $0.id == localId }
        }
        PendingDriveStore.save(pendingDriveArchives)
    }

    func retryPendingDriveSave(localId: UUID) async {
        guard let index = pendingDriveArchives.firstIndex(where: { $0.id == localId }) else { return }
        let archive = pendingDriveArchives[index]
        guard !currentUserID.isEmpty else { return }

        let success = await uploadPendingDriveArchive(archive)
        await MainActor.run {
            if success {
                updatePendingDriveArchives { $0.remove(at: index) }
                PendingDriveStore.save(pendingDriveArchives)
                showToast(String(localized: "drive_pending_saved_toast"), icon: "checkmark.circle.fill")
                Task { await refreshRecentDrives() }
            } else {
                var updated = archive
                updated.retryCount += 1
                updatePendingDriveArchives { $0[index] = updated }
                PendingDriveStore.save(pendingDriveArchives)
                showToast(String(localized: "drive_pending_retry_failed_toast"), icon: "exclamationmark.triangle.fill")
            }
        }
    }

    private func uploadPendingDriveArchive(_ archive: PendingDriveArchive) async -> Bool {
        do {
            let driveId: String
            if let existing = archive.backendDriveId?.trimmingCharacters(in: .whitespacesAndNewlines), !existing.isEmpty {
                driveId = existing
            } else {
                let start = archive.pathSamples.first
                let drive = try await APIClient.shared.startDrive(
                    userId: currentUserID,
                    circleId: archive.circleId,
                    sharingAudience: SharingAudience.onlyMe.rawValue,
                    sharedCircleIds: archive.sharedCircleIds,
                    title: archive.title,
                    location: start.map { (lat: $0.lat, lng: $0.lng) }
                )
                driveId = drive.id
            }

            let samples = archive.pathDriveSamples
            if !samples.isEmpty {
                try await APIClient.shared.appendDrivePathSamples(driveId: driveId, samples: samples)
            }

            let end = archive.pathSamples.last
            try await APIClient.shared.endDrive(
                driveId: driveId,
                location: end.map { (lat: $0.lat, lng: $0.lng) },
                distanceMeters: archive.distanceMeters > 0 ? archive.distanceMeters : nil,
                maxSpeedMph: archive.maxSpeedMph > 0 ? archive.maxSpeedMph : nil,
                avgSpeedMph: archive.avgSpeedMph > 0 ? archive.avgSpeedMph : nil
            )
            return true
        } catch {
            return false
        }
    }

    @discardableResult
    func stopActiveDrive(
        location: CLLocation?,
        distanceMeters: Double? = nil,
        maxSpeedMph: Double? = nil,
        avgSpeedMph: Double? = nil,
        archiveOnFailure: PendingDriveArchiveInput? = nil
    ) async -> Bool {
        guard let driveId = activeDriveID else { return true }
        var resolvedDistance = distanceMeters ?? activeDriveDistanceMeters
        if resolvedDistance <= 0 {
            resolvedDistance = DriveSpeedGradient.polylineDistanceMeters(from: activeDrivePathTrail)
        }
        let resolvedMaxSpeed = max(maxSpeedMph ?? 0, activeDriveMaxSpeedMph)
        let trailMaxSpeed = activeDrivePathTrail.map(\.speedMph).max() ?? 0
        let resolvedMaxSpeedMph = max(resolvedMaxSpeed, trailMaxSpeed)
        let endLocation = location.map { (lat: $0.coordinate.latitude, lng: $0.coordinate.longitude) }

        defer {
            resetActiveDriveTelemetryAfterStop()
        }

        func endOnce() async throws {
            try await APIClient.shared.endDrive(
                driveId: driveId,
                location: endLocation,
                distanceMeters: resolvedDistance > 0 ? resolvedDistance : nil,
                maxSpeedMph: resolvedMaxSpeedMph > 0 ? resolvedMaxSpeedMph : nil,
                avgSpeedMph: avgSpeedMph
            )
        }

        do {
            try await endOnce()
            await refreshRecentDrives()
            return true
        } catch {
            do {
                try await endOnce()
                await refreshRecentDrives()
                return true
            } catch {
                if let archiveOnFailure {
                    await MainActor.run {
                        archivePendingDrive(from: archiveOnFailure)
                    }
                }
                return false
            }
        }
    }

    func pendingArchiveInput(
        failurePhase: String,
        kind: DriveSessionKind,
        title: String,
        startedAt: Date,
        endedAt: Date = Date(),
        distanceMeters: Double,
        maxSpeedMph: Double,
        avgSpeedMph: Double,
        backendDriveId: String?,
        routeId: String? = nil,
        routeName: String? = nil,
        pathSamples: [DrivePathSample]
    ) -> PendingDriveArchiveInput {
        let sharedCircleIds = Array(sharingCircleIDs)
        let driveCircleID = sharedCircleIds.first ?? selectedCircleID
        return PendingDriveArchiveInput(
            failurePhase: failurePhase,
            kind: kind,
            title: title,
            startedAt: startedAt,
            endedAt: endedAt,
            distanceMeters: distanceMeters,
            maxSpeedMph: maxSpeedMph,
            avgSpeedMph: avgSpeedMph,
            backendDriveId: backendDriveId,
            circleId: driveCircleID.isEmpty ? nil : driveCircleID,
            sharedCircleIds: sharedCircleIds,
            routeId: routeId,
            routeName: routeName,
            pathSamples: pathSamples
        )
    }
}
