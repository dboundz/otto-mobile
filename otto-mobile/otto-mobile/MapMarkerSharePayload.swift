import SwiftUI

struct MapMarkerSharePayload: Equatable {
    enum PreviewKind: Equatable {
        case savedPlace
        case raceTrack
    }

    let title: String
    let subtitle: String?
    let latitude: Double?
    let longitude: Double?
    let savedPlaceId: String?
    let externalShareText: String
    let previewKind: PreviewKind

    static func savedPlace(
        id: String? = nil,
        name: String,
        addressSummary: String?,
        latitude: Double,
        longitude: Double
    ) -> MapMarkerSharePayload {
        MapMarkerSharePayload(
            title: name,
            subtitle: addressSummary,
            latitude: latitude,
            longitude: longitude,
            savedPlaceId: id,
            externalShareText: shareText(title: name, subtitle: addressSummary, lat: latitude, lng: longitude),
            previewKind: .savedPlace
        )
    }

    static func adhocPlace(
        name: String?,
        addressSummary: String?,
        latitude: Double,
        longitude: Double
    ) -> MapMarkerSharePayload {
        let title = (name?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? (addressSummary?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? String(localized: "chat_place_attachment_fallback_title")
        return MapMarkerSharePayload(
            title: title,
            subtitle: addressSummary,
            latitude: latitude,
            longitude: longitude,
            savedPlaceId: nil,
            externalShareText: shareText(title: title, subtitle: addressSummary, lat: latitude, lng: longitude),
            previewKind: .savedPlace
        )
    }

    static func raceTrack(
        name: String,
        locationLine: String?,
        latitude: Double?,
        longitude: Double?
    ) -> MapMarkerSharePayload {
        MapMarkerSharePayload(
            title: name,
            subtitle: locationLine,
            latitude: latitude,
            longitude: longitude,
            savedPlaceId: nil,
            externalShareText: shareText(title: name, subtitle: locationLine, lat: latitude, lng: longitude),
            previewKind: .raceTrack
        )
    }

    private static func shareText(title: String, subtitle: String?, lat: Double?, lng: Double?) -> String {
        var lines = [title]
        if let subtitle, !subtitle.isEmpty { lines.append(subtitle) }
        if let lat, let lng {
            lines.append(String(format: "%.5f, %.5f", lat, lng))
        }
        return lines.joined(separator: "\n")
    }
}
