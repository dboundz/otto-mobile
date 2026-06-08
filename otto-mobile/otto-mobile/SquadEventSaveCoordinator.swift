import CoreLocation
import Foundation
import MapKit
import os
import SwiftUI

// MARK: - Geocoding

enum EventAddressGeocoding {
    /// Best-effort forward geocode for map pin / check-in. `nil` when lookup fails or query too short.
    static func coordinateIfResolvable(_ query: String) async -> CLLocationCoordinate2D? {
        let ownedQuery = String(query)
        let trimmed = ownedQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let preview = String(trimmed.prefix(72))
        OttoLog.squadEvent.debug(
            "geocode begin queryLen=\(trimmed.count, privacy: .public) preview=\"\(preview, privacy: .public)\""
        )
        guard trimmed.count >= 4 else {
            OttoLog.squadEvent.debug("geocode skip: query too short len=\(trimmed.count, privacy: .public)")
            return nil
        }
        guard let request = MKGeocodingRequest(addressString: trimmed) else {
            OttoLog.squadEvent.warning("geocode skip: MKGeocodingRequest rejected address string")
            return nil
        }
        do {
            let mapItems = try await request.mapItems
            guard let location = mapItems.first?.location else {
                OttoLog.squadEvent.info("geocode no mapItems.first location resultCount=\(mapItems.count, privacy: .public)")
                return nil
            }
            let coord = location.coordinate
            guard CLLocationCoordinate2DIsValid(coord) else {
                OttoLog.squadEvent.warning("geocode invalid coordinate from MapKit")
                return nil
            }
            OttoLog.squadEvent.debug(
                "geocode ok lat=\(coord.latitude, privacy: .public) lon=\(coord.longitude, privacy: .public)"
            )
            return coord
        } catch {
            OttoLog.squadEvent.error("geocode failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }
}

typealias SquadEventAddressGeocoding = EventAddressGeocoding

// MARK: - Payload

/// Immutable values collected by `AddSquadEventSheet`. Kept as a small **class** so async hand-off from the sheet
/// avoids `@guaranteed` aggregate issues with large string / `Data` fields.
final class EventEditorSavePayload: @unchecked Sendable {
    let name: String
    let description: String
    let startsAt: Date
    let endsAt: Date
    let location: String
    let streetAddress: String
    let city: String
    let region: String
    let postalCode: String
    let imageData: Data?

    init(
        name: String,
        description: String,
        startsAt: Date,
        endsAt: Date,
        location: String,
        streetAddress: String,
        city: String,
        region: String,
        postalCode: String,
        imageData: Data?
    ) {
        self.name = name
        self.description = description
        self.startsAt = startsAt
        self.endsAt = endsAt
        self.location = location
        self.streetAddress = streetAddress
        self.city = city
        self.region = region
        self.postalCode = postalCode
        self.imageData = imageData
    }
}

typealias SquadEventSavePayload = EventEditorSavePayload

private struct SquadEventAddressLookupFailedError: Error {}

/// Result of saving from `AddSquadEventSheet`: new squad event vs edit-only save.
enum SquadEventSheetSaveOutcome: Sendable {
    case created(EventDTO)
    case updated
}

/// Synchronous completion used by `AddSquadEventSheet.onSave`. Passing `SquadEventSavePayload` through nested
/// `async throws` callee thunks arrives as `@guaranteed` and can crash in `swift_retain` before the coordinator runs.
typealias SquadEventSheetSaveCompletion = @Sendable (Result<SquadEventSheetSaveOutcome, Error>) -> Void

@MainActor
enum EventEditorSaveCoordinator {
    static func resolvedLocationPin(
        appState: AppState,
        payload: EventEditorSavePayload,
        logContext: String
    ) async throws -> (latitude: Double, longitude: Double)? {
        let geocodeQuery = [
            payload.location.nilIfPayloadEmpty,
            payload.streetAddress.nilIfPayloadEmpty,
            payload.city.nilIfPayloadEmpty,
            payload.region.nilIfPayloadEmpty,
            payload.postalCode.nilIfPayloadEmpty
        ]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")

        guard !geocodeQuery.isEmpty else {
            OttoLog.squadEvent.debug("\(logContext, privacy: .public) skip geocode (no address)")
            return nil
        }

        OttoLog.squadEvent.debug("\(logContext, privacy: .public) geocode branch addressLen=\(geocodeQuery.count, privacy: .public)")
        if let coord = await EventAddressGeocoding.coordinateIfResolvable(geocodeQuery) {
            OttoLog.squadEvent.debug(
                "\(logContext, privacy: .public) geocode pinned lat=\(coord.latitude, privacy: .public) lon=\(coord.longitude, privacy: .public)"
            )
            return (coord.latitude, coord.longitude)
        }

        OttoLog.squadEvent.info("\(logContext, privacy: .public) geocode miss")
        appState.presentUserToast(
            text: "Couldn’t place a map pin for that address — try a fuller street address.",
            systemImage: "mappin.slash.circle"
        )
        throw SquadEventAddressLookupFailedError()
    }
}

// MARK: - Coordinator

/// Central `@MainActor` persistence for squad-scoped event create / edit / delete.
/// Views should only collect input and call into this type (no network or geocode logic in `View` bodies).
@MainActor
enum SquadEventSaveCoordinator {

    static func applyEditedEvent(
        appState: AppState,
        eventID: String,
        payload: SquadEventSavePayload,
        onEventUpdated: ((EventDTO) -> Void)?
    ) async throws {
        let stableEventID = String(eventID)
        let stableName = payload.name
        let stableDescriptionText = payload.description
        let stableLocationText = payload.location
        let stableStreetAddressText = payload.streetAddress
        let stableCityText = payload.city
        let stableRegionText = payload.region
        let stablePostalCodeText = payload.postalCode
        let stableStartsAt = payload.startsAt
        let stableEndsAt = payload.endsAt
        let stableImageData = payload.imageData.map { Data($0) }

        let payloadPtr = UInt(bitPattern: Unmanaged.passUnretained(payload).toOpaque())
        OttoLog.squadEvent.debug(
            "applyEditedEvent begin eventID=\(stableEventID, privacy: .public) payloadPtr=0x\(String(payloadPtr, radix: 16, uppercase: false), privacy: .public) nameLen=\(stableName.count, privacy: .public) descLen=\(stableDescriptionText.count, privacy: .public) locLen=\(stableLocationText.count, privacy: .public) locPreview=\"\(String(stableLocationText.prefix(64)), privacy: .public)\" imageBytes=\(stableImageData?.count ?? 0, privacy: .public) start=\(stableStartsAt.timeIntervalSince1970, privacy: .public) end=\(stableEndsAt.timeIntervalSince1970, privacy: .public)"
        )

        let description = stableDescriptionText.isEmpty ? nil : stableDescriptionText
        let location = stableLocationText.isEmpty ? nil : stableLocationText
        let streetAddress = stableStreetAddressText.isEmpty ? nil : stableStreetAddressText
        let city = stableCityText.isEmpty ? nil : stableCityText
        let region = stableRegionText.isEmpty ? nil : stableRegionText
        let postalCode = stablePostalCodeText.isEmpty ? nil : stablePostalCodeText
        let locationPin = try await EventEditorSaveCoordinator.resolvedLocationPin(
            appState: appState,
            payload: payload,
            logContext: "applyEditedEvent"
        )

        do {
            var updated = try await APIClient.shared.updateEvent(
                eventId: stableEventID,
                name: stableName,
                description: description,
                startsAt: stableStartsAt,
                endsAt: stableEndsAt,
                addressLabel: location,
                streetAddress: streetAddress,
                city: city,
                region: region,
                postalCode: postalCode,
                location: locationPin
            )
            OttoLog.squadEvent.debug("applyEditedEvent updateEvent ok id=\(updated.id, privacy: .public)")
            if let stableImageData {
                updated = try await APIClient.shared.uploadEventBanner(eventId: stableEventID, imageData: stableImageData)
                OttoLog.squadEvent.debug(
                    "applyEditedEvent uploadEventBanner ok id=\(stableEventID, privacy: .public) bytes=\(stableImageData.count, privacy: .public)"
                )
            }
            appState.upsertUpcomingEvent(updated)
            onEventUpdated?(updated)
            OttoLog.squadEvent.debug("applyEditedEvent complete id=\(updated.id, privacy: .public)")
        } catch {
            OttoLog.squadEvent.error("applyEditedEvent failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }

    static func createEventInCircle(
        appState: AppState,
        circleID: String,
        payload: SquadEventSavePayload
    ) async throws -> EventDTO {
        OttoLog.squadEvent.debug(
            "createEventInCircle begin circleID=\(circleID, privacy: .public) nameLen=\(payload.name.count, privacy: .public) descLen=\(payload.description.count, privacy: .public) locLen=\(payload.location.count, privacy: .public) imageBytes=\(payload.imageData?.count ?? 0, privacy: .public)"
        )

        let description = payload.description.nilIfPayloadEmpty
        let location = payload.location.nilIfPayloadEmpty
        let streetAddress = payload.streetAddress.nilIfPayloadEmpty
        let city = payload.city.nilIfPayloadEmpty
        let region = payload.region.nilIfPayloadEmpty
        let postalCode = payload.postalCode.nilIfPayloadEmpty
        let locationPin = try await EventEditorSaveCoordinator.resolvedLocationPin(
            appState: appState,
            payload: payload,
            logContext: "createEventInCircle"
        )

        do {
            var event = try await APIClient.shared.createEvent(
                name: payload.name,
                description: description,
                startsAt: payload.startsAt,
                endsAt: payload.endsAt,
                addressLabel: location,
                streetAddress: streetAddress,
                city: city,
                region: region,
                postalCode: postalCode,
                location: locationPin,
                visibility: "circle",
                circleId: circleID
            )
            OttoLog.squadEvent.debug("createEventInCircle API createEvent ok id=\(event.id, privacy: .public)")
            if let imageData = payload.imageData {
                event = try await APIClient.shared.uploadEventBanner(eventId: event.id, imageData: Data(imageData))
                OttoLog.squadEvent.debug("createEventInCircle uploadEventBanner ok id=\(event.id, privacy: .public)")
            }
            appState.upsertUpcomingEvent(event)
            OttoLog.squadEvent.debug("createEventInCircle complete id=\(event.id, privacy: .public)")
            return event
        } catch {
            OttoLog.squadEvent.error("createEventInCircle failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }

    static func deleteEditedEvent(
        appState: AppState,
        eventID: String,
        onDeleted: ((String) -> Void)? = nil
    ) async throws {
        try await APIClient.shared.deleteEvent(eventId: eventID)
        appState.upcomingEvents.removeAll { $0.id == eventID }
        onDeleted?(eventID)
        appState.presentDeletedToast(for: "Event")
        OttoLog.squadEvent.debug("deleteEditedEvent completed id=\(eventID, privacy: .public)")
    }
}

private extension String {
    var nilIfPayloadEmpty: String? {
        isEmpty ? nil : self
    }
}
