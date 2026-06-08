import CoreLocation
import SwiftUI

// MARK: - Design tokens

enum DriveSessionPalette {
    /// Session active / route / FAB glow / Done CTA
    static let sessionPurple = Color(red: 0.52, green: 0.38, blue: 0.88)
    /// Recording in progress
    static let recordingGreen = Color(red: 0.28, green: 0.86, blue: 0.42)
    /// Live sharing
    static let sharingRed = Color(red: 0.95, green: 0.32, blue: 0.36)
    /// Idle pill dot
    static let idleMuted = Color.white.opacity(0.35)
    /// Go Live icon tint
    static let goLivePink = Color(red: 0.98, green: 0.38, blue: 0.58)
}

// MARK: - Session model

enum DriveSessionKind: String, Equatable {
    case quick
    case route
    case live
}

struct DriveSessionMetrics: Equatable {
    var distanceMeters: Double = 0
    var maxSpeedMph: Double = 0
    var speedSampleCount: Int = 0
    var speedSumMph: Double = 0
    var recordedPath: [DrivePathSample] = []

    var avgSpeedMph: Double {
        guard speedSampleCount > 0 else { return 0 }
        return speedSumMph / Double(speedSampleCount)
    }

    mutating func ingest(location: CLLocation, speedMph: Double, movementMode: FriendMovementMode) {
        if movementMode == .driving {
            maxSpeedMph = max(maxSpeedMph, speedMph)
            speedSumMph += speedMph
            speedSampleCount += 1
        }
        if let last = recordedPath.last {
            let delta = CLLocation(latitude: last.coordinate.latitude, longitude: last.coordinate.longitude)
                .distance(from: location)
            if delta >= 18 { distanceMeters += delta }
            if delta < 18 { return }
        }
        recordedPath.append(DrivePathSample(location: location, speedMph: speedMph))
        if recordedPath.count > 800 {
            recordedPath.removeFirst(recordedPath.count - 800)
        }
    }
}

struct DriveSessionRouteProgress: Equatable {
    var routeId: String
    var routeName: String
    var completedCheckpointIndexes: Set<Int>
    var totalCheckpoints: Int
    var currentProgress: Double

    var completedCount: Int { completedCheckpointIndexes.count }
}

struct DriveSession: Equatable {
    var id: UUID
    var kind: DriveSessionKind
    var isRecording: Bool
    var isSharing: Bool
    var routeId: String?
    var routeName: String?
    var sharingCircleIDs: Set<String>
    var startedAt: Date
    var metrics: DriveSessionMetrics
    var routeProgress: DriveSessionRouteProgress?
    var backendDriveId: String?
    var backendRouteSessionId: String?

    var hasActiveSession: Bool { true }

    static func quick(
        saveToProfile: Bool,
        shareLive: Bool = false,
        sharingCircleIDs: Set<String> = []
    ) -> DriveSession {
        DriveSession(
            id: UUID(),
            kind: .quick,
            isRecording: saveToProfile,
            isSharing: shareLive,
            routeId: nil,
            routeName: nil,
            sharingCircleIDs: sharingCircleIDs,
            startedAt: Date(),
            metrics: DriveSessionMetrics(),
            routeProgress: nil,
            backendDriveId: nil,
            backendRouteSessionId: nil
        )
    }
}

// MARK: - Route drive session (moved from MapScreen)

struct RouteDriveSessionState {
    var sessionId: String
    var activeRouteId: String
    var driveId: String?
    var status: String
    var armedAt: Date?
    var startedAt: Date?
    var endedAt: Date?
    var completedWaypointIndexes: Set<Int>
    var currentProgress: Double
    var previousRouteDriveLocation: CLLocation?
    var currentLocation: CLLocation?
    var currentSpeedMph: Double
    var maxSpeedMph: Double
    var avgSpeedMph: Double
    var speedSampleCount: Int
    var lastTriggeredWaypointIndex: Int?
    var lastRouteProgressMeters: Double?
    var stopReason: String?

    var isArmed: Bool { status == "armed" }
    var isActive: Bool { status == "active" }

    init(dto: RouteDriveSessionDTO, routeId: String, currentLocation: CLLocation? = nil) {
        sessionId = dto.id
        activeRouteId = routeId
        driveId = dto.driveId
        status = dto.status
        armedAt = dto.armedAt
        startedAt = dto.startedAt
        endedAt = dto.endedAt
        completedWaypointIndexes = Set(dto.completedWaypointIndexes)
        currentProgress = dto.currentProgress
        previousRouteDriveLocation = nil
        self.currentLocation = currentLocation
        currentSpeedMph = dto.currentSpeedMph
        maxSpeedMph = dto.maxSpeedMph
        avgSpeedMph = dto.avgSpeedMph
        speedSampleCount = dto.currentSpeedMph > 0 ? 1 : 0
        lastTriggeredWaypointIndex = dto.lastTriggeredWaypointIndex
        lastRouteProgressMeters = nil
        stopReason = dto.stopReason
    }
}

// MARK: - Pill presentation

enum DriveSessionPillPresentation: Equatable {
    case idle
    case pausedSharing
    case recording(timeText: String, distanceText: String)
    case route(name: String, completed: Int, total: Int)
    case sharing(squadSummary: String, viewerCount: Int?, remainingText: String?)
    case recordingAndSharing(
        timeText: String,
        distanceText: String,
        squadSummary: String,
        viewerCount: Int?,
        remainingText: String?
    )

    /// Stop is handled by the map bottom drive dock when a session is active.
    var showsStopButton: Bool { false }

    /// Single status dot / tab indicator color for this session presentation.
    var statusIndicatorColor: Color? {
        switch self {
        case .idle: return nil
        case .recording: return DriveSessionPalette.recordingGreen
        case .route: return DriveSessionPalette.sessionPurple
        case .sharing, .pausedSharing, .recordingAndSharing: return DriveSessionPalette.sharingRed
        }
    }

    var mapTabIndicatorColor: Color? { statusIndicatorColor }

    /// Pill stroke / glow accent — one color per presentation (matches status dot).
    var pillBorderColor: Color {
        switch self {
        case .idle: return Color.white.opacity(0.2)
        case .recording: return DriveSessionPalette.recordingGreen
        case .route: return DriveSessionPalette.sessionPurple
        case .sharing, .pausedSharing, .recordingAndSharing: return DriveSessionPalette.sharingRed
        }
    }
}

/// Map-tab UI hooks for route drive events raised from global location ingestion.
struct RouteDriveFeedbackEvent: Equatable {
    enum Kind {
        case activated
        case checkpointReached(isFinish: Bool)
        case completed(summary: DriveCompleteSummary)
        case stopped(summary: DriveCompleteSummary?)
        case activationFailed
    }

    let kind: Kind
    let id: UUID

    init(kind: Kind) {
        self.kind = kind
        id = UUID()
    }

    static func == (lhs: RouteDriveFeedbackEvent, rhs: RouteDriveFeedbackEvent) -> Bool {
        lhs.id == rhs.id
    }
}

struct DriveSessionCompletionPayload: Equatable {
    var driveId: String?
    var kind: DriveSessionKind
    var routeName: String?
    var routeCoordinates: [CLLocationCoordinate2D]
    var checkpointCoordinates: [CLLocationCoordinate2D]
    var distanceMeters: Double
    var driveTimeSeconds: TimeInterval
    var averageSpeedMph: Double
    var maxSpeedMph: Double
    var completedCheckpoints: Int
    var totalCheckpoints: Int
    var completionReason: String
}
