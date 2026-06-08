import CoreLocation
import UIKit

enum ChatComposerLocationAttachmentLoader {
    enum Error: LocalizedError, Equatable {
        case permissionDenied
        case locationUnavailable

        var errorDescription: String? {
            switch self {
            case .permissionDenied:
                return String(localized: "chat_composer_location_permission_denied")
            case .locationUnavailable:
                return String(localized: "chat_composer_location_unavailable")
            }
        }
    }

    private static let maxFixAgeSeconds: TimeInterval = 300
    private static let waitTimeoutSeconds: TimeInterval = 8

    @MainActor
    static func buildPendingAttachment(locationService: LocationService) async throws -> ChatPendingComposerAttachment {
        let auth = locationService.authorizationStatus
        switch auth {
        case .denied, .restricted:
            throw Error.permissionDenied
        case .notDetermined:
            throw Error.permissionDenied
        default:
            break
        }

        guard let fix = try await resolveCurrentFix(locationService: locationService) else {
            throw Error.locationUnavailable
        }

        let coordinate = fix.coordinate
        guard CLLocationCoordinate2DIsValid(coordinate) else {
            throw Error.locationUnavailable
        }

        let geocoded = await MapCoordinateReverseGeocoder.reverseGeocode(at: coordinate)
        let payload = MapMarkerSharePayload.adhocPlace(
            name: geocoded.name,
            addressSummary: geocoded.address,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )

        let mapJPEG = await PlaceMapSnapshotGenerator.jpegData(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )
        let previewImage = mapJPEG.flatMap { UIImage(data: $0) }

        return ChatPendingComposerAttachment(
            kind: .place(payload),
            previewImage: previewImage,
            mapPreviewJPEG: mapJPEG
        )
    }

    @MainActor
    private static func resolveCurrentFix(locationService: LocationService) async throws -> CLLocation? {
        if let existing = freshFix(from: locationService) {
            return existing
        }

        let priorNeeds = locationService.makeDiagnosticsSnapshot().appliedNeeds
        locationService.applyDesiredState(
            LocationSessionNeeds(gps: true, motion: false, freshDisplay: true)
        )
        defer {
            locationService.applyDesiredState(priorNeeds)
        }

        let deadline = Date().addingTimeInterval(waitTimeoutSeconds)
        while Date() < deadline {
            if let fix = freshFix(from: locationService) {
                return fix
            }
            try await Task.sleep(nanoseconds: 200_000_000)
        }
        return freshFix(from: locationService)
    }

    @MainActor
    private static func freshFix(from locationService: LocationService) -> CLLocation? {
        let candidates = [
            locationService.displayLocation,
            locationService.latestSample,
            locationService.lastLocation,
        ].compactMap { $0 }

        let now = Date()
        for location in candidates {
            guard CLLocationCoordinate2DIsValid(location.coordinate) else { continue }
            if now.timeIntervalSince(location.timestamp) <= maxFixAgeSeconds {
                return location
            }
        }
        return candidates.first
    }
}
