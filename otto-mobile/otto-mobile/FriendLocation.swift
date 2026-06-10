import CoreLocation
import Foundation
import SwiftUI

/// Maps presence `inApp` to the avatar/dot color used across Otto (map, squads, events, sheets).
enum DriverPresenceStatus: Equatable {
    /// `inApp: true` (or omitted legacy) while the presence payload is fresh.
    case inAppForeground
    /// `inApp: false` — app backgrounded but still installed / session not torn down.
    case appBackground
    /// No usable presence, stale heartbeat, or unknown.
    case offline

    var color: Color {
        switch self {
        case .inAppForeground: return .green
        case .appBackground: return .yellow
        case .offline: return .gray
        }
    }

    var label: String {
        switch self {
        case .inAppForeground: return "Active"
        case .appBackground: return "Background"
        case .offline: return "Offline"
        }
    }
}

enum FriendMovementMode: Equatable {
    case driving
    case walking
    case unknown

    var apiValue: String {
        switch self {
        case .driving: return "driving"
        case .walking: return "walking"
        case .unknown: return "unknown"
        }
    }
}

struct FriendLocation: Identifiable {
    let id: String
    let name: String
    let avatarName: String
    /// HTTPS/HTTP URL from the API (e.g. after `POST /api/users/:id/avatar`).
    let avatarUrl: String?
    let car: String
    let clubRole: String
    let lastRun: String
    let coordinate: CLLocationCoordinate2D
    let speedMph: Int
    /// True when the peer has the app in the foreground (in-app heartbeat), not merely sharing.
    let isOnline: Bool
    /// True when the peer is sharing live location with this circle.
    let isActive: Bool
    let accentColor: Color
    var movementMode: FriendMovementMode = .unknown
    var lastUpdatedAt: Date? = nil
    /// From last squad/public presence payload: `true` = foreground (`inApp` true/legacy), `false` = background (`inApp` false), `nil` = unknown/offline/stale.
    var lastPresenceInApp: Bool? = nil
    /// Resolved S3 car-brands slug from presence when live sharing with a garage car selected.
    var brandLogoSlug: String? = nil
}

extension FriendLocation {
    var initials: String {
        let parts = name.split(separator: " ")
        let letters = parts.prefix(2).compactMap { $0.first }
        return letters.map(String.init).joined()
    }

    var statusLabel: String {
        presenceStatus.label
    }

    var presenceStatus: DriverPresenceStatus {
        switch lastPresenceInApp {
        case true: return .inAppForeground
        case false: return .appBackground
        case nil: return .offline
        }
    }

    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> FriendLocation {
        guard id == patch.id else { return self }
        return FriendLocation(
            id: id,
            name: patch.displayName,
            avatarName: patch.displayName,
            avatarUrl: patch.avatarUrl,
            car: car,
            clubRole: clubRole,
            lastRun: lastRun,
            coordinate: coordinate,
            speedMph: speedMph,
            isOnline: isOnline,
            isActive: isActive,
            accentColor: MapAccentPalette.resolvedColor(mapAccentKey: patch.mapAccentKey, userId: patch.id),
            movementMode: movementMode,
            lastUpdatedAt: lastUpdatedAt,
            lastPresenceInApp: lastPresenceInApp,
            brandLogoSlug: brandLogoSlug
        )
    }
}
