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

// MARK: - Turn-by-turn guidance (route drive)

struct NavigationManeuver: Equatable, Hashable {
    let type: String
    let modifier: String?
    let instruction: String
}

struct TurnByTurnGuidanceState: Equatable {
    enum Phase: Equatable {
        case loading
        case navigating
        case offRoute
        case arrived
        case failed(String)
    }

    let phase: Phase
    let nextInstruction: String
    let nextManeuver: NavigationManeuver?
    let distanceToManeuverMeters: Double
    let currentRoadName: String?
    let remainingDistanceMeters: Double
    let remainingDurationSeconds: TimeInterval
    let eta: Date
    let currentStepIndex: Int
    let totalSteps: Int
}

protocol NavigationRouteProviding {
    func fetchRoute(waypoints: [CLLocationCoordinate2D]) async throws -> NavigationRoute
}

protocol NavigationGuidancePublishing: AnyObject {
    var guidance: TurnByTurnGuidanceState? { get }
}

struct NavigationVoiceInstruction: Equatable {
    let distanceAlongStepMeters: Double
    let announcement: String
}

struct NavigationStep: Equatable {
    let instruction: String
    let name: String?
    let distanceMeters: Double
    let durationSeconds: TimeInterval
    let maneuver: NavigationManeuver
    let maneuverCoordinate: CLLocationCoordinate2D
    let voiceInstructions: [NavigationVoiceInstruction]
    let geometryCoordinates: [CLLocationCoordinate2D]
    let maneuverArcLengthMeters: Double

    func shouldSuppressArrivePresentation(
        stepIndex: Int,
        totalSteps: Int,
        distanceToFinalDestinationMeters: Double?,
        currentRoadName: String? = nil,
        hasPassedFinalTurn: Bool = true
    ) -> Bool {
        guard maneuver.type.lowercased() == "arrive" else { return false }
        guard stepIndex == totalSteps - 1 else { return true }
        if hasPassedFinalTurn, Self.roadName(currentRoadName, matches: name) { return false }
        guard let distanceToFinalDestinationMeters else { return true }
        return distanceToFinalDestinationMeters > TurnByTurnNavigationConstants.arrivePresentationDistanceMeters
    }

    private static func roadName(_ lhs: String?, matches rhs: String?) -> Bool {
        guard let lhs = normalizedRoadName(lhs), let rhs = normalizedRoadName(rhs) else { return false }
        return lhs == rhs
    }

    private static func normalizedRoadName(_ value: String?) -> String? {
        guard let value else { return nil }
        var normalized = value
            .lowercased()
            .replacingOccurrences(of: ".", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let suffixExpansions = [
            (" st", " street"),
            (" rd", " road"),
            (" dr", " drive"),
            (" ave", " avenue"),
            (" blvd", " boulevard"),
            (" ln", " lane"),
            (" ct", " court"),
            (" cir", " circle"),
            (" pkwy", " parkway")
        ]
        for (abbreviation, expanded) in suffixExpansions where normalized.hasSuffix(abbreviation) {
            normalized.replaceSubrange(
                normalized.index(normalized.endIndex, offsetBy: -abbreviation.count)..<normalized.endIndex,
                with: expanded
            )
            break
        }
        return normalized.isEmpty ? nil : normalized
    }
}

struct NavigationLeg: Equatable {
    let steps: [NavigationStep]
    let distanceMeters: Double
    let durationSeconds: TimeInterval
}

struct NavigationRoute: Equatable {
    let coordinates: [CLLocationCoordinate2D]
    let legs: [NavigationLeg]
    let totalDistanceMeters: Double
    let totalDurationSeconds: TimeInterval
    let finishCoordinate: CLLocationCoordinate2D

    var flattenedSteps: [NavigationStep] {
        legs.flatMap(\.steps)
    }

    var polylineIndex: RoutePolylineIndex {
        RoutePolylineIndex(lineCoordinates: coordinates)
    }
}

struct TurnByTurnOffRouteTracker {
    private(set) var consecutiveOffRouteSamples = 0

    mutating func recordSample(lateralDistanceMeters: Double) -> Bool {
        if lateralDistanceMeters > TurnByTurnNavigationConstants.offRouteDistanceMeters {
            consecutiveOffRouteSamples += 1
        } else {
            consecutiveOffRouteSamples = 0
        }
        return consecutiveOffRouteSamples >= TurnByTurnNavigationConstants.offRouteConsecutiveSamples
    }
}

struct TurnByTurnAnnouncementDeduper {
    private var announcedMessages: Set<String> = []

    mutating func reset() {
        announcedMessages.removeAll()
    }

    mutating func shouldSpeak(_ announcement: String) -> Bool {
        let key = Self.normalizedAnnouncementKey(announcement)
        guard !key.isEmpty, !announcedMessages.contains(key) else { return false }
        announcedMessages.insert(key)
        return true
    }

    static func normalizedAnnouncementKey(_ announcement: String) -> String {
        announcement
            .lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

enum TurnByTurnNavigationConstants {
    static let offRouteDistanceMeters = 60.0
    static let offRouteConsecutiveSamples = 4
    static let stepAdvanceDistanceMeters = 35.0
    static let arrivalDistanceMeters = 40.0
    static let arrivePresentationDistanceMeters = 120.0
    static let postTurnArrivalPresentationDistanceMeters = 35.0
    static let atManeuverVoiceDistanceMeters = 15.0
    static let voiceThresholdHalfMileMeters = 804.67
    static let voiceThresholdTwoTenthsMileMeters = 321.87
    static let voiceThresholdTwoHundredFeetMeters = 60.96
    static let voiceSpeedLeadMinimumMps = 2.5
    static let voiceSpeedFastRoadMps = 20.0
    static let voiceSpeedLeadCitySeconds = 12.0
    static let voiceSpeedLeadFastSeconds = 15.0
    static let voiceSpeedCloseSeconds = 6.0
}

enum NavigationVoiceThreshold: String, CaseIterable {
    case halfMile
    case twoTenthsMile
    case speedLead
    case speedClose
    case twoHundredFeet
    case atManeuver

    static let announcementOrder: [NavigationVoiceThreshold] = [
        .atManeuver,
        .speedClose,
        .twoHundredFeet,
        .speedLead,
        .twoTenthsMile,
        .halfMile
    ]

    var distanceMeters: Double {
        switch self {
        case .halfMile: return TurnByTurnNavigationConstants.voiceThresholdHalfMileMeters
        case .twoTenthsMile: return TurnByTurnNavigationConstants.voiceThresholdTwoTenthsMileMeters
        case .twoHundredFeet: return TurnByTurnNavigationConstants.voiceThresholdTwoHundredFeetMeters
        case .atManeuver: return TurnByTurnNavigationConstants.atManeuverVoiceDistanceMeters
        case .speedLead, .speedClose: return 0
        }
    }

    var toleranceMeters: Double {
        switch self {
        case .halfMile: return 80
        case .twoTenthsMile: return 40
        case .twoHundredFeet: return 20
        case .atManeuver: return 10
        case .speedLead: return 80
        case .speedClose: return 40
        }
    }

    func shouldAnnounce(distanceToManeuverMeters: Double, speedMps: Double) -> Bool {
        let distance = max(0, distanceToManeuverMeters)
        switch self {
        case .atManeuver:
            return distance <= distanceMeters + toleranceMeters
        case .twoHundredFeet, .twoTenthsMile, .halfMile:
            return distance <= distanceMeters + toleranceMeters
                && distance >= distanceMeters - toleranceMeters
        case .speedLead:
            guard speedMps >= TurnByTurnNavigationConstants.voiceSpeedLeadMinimumMps else { return false }
            let leadSeconds = speedMps >= TurnByTurnNavigationConstants.voiceSpeedFastRoadMps
                ? TurnByTurnNavigationConstants.voiceSpeedLeadFastSeconds
                : TurnByTurnNavigationConstants.voiceSpeedLeadCitySeconds
            let timeToManeuver = distance / speedMps
            return timeToManeuver <= leadSeconds
                && timeToManeuver > TurnByTurnNavigationConstants.voiceSpeedCloseSeconds
                && distance > TurnByTurnNavigationConstants.voiceThresholdTwoHundredFeetMeters
        case .speedClose:
            guard speedMps >= TurnByTurnNavigationConstants.voiceSpeedLeadMinimumMps else { return false }
            let timeToManeuver = distance / speedMps
            return timeToManeuver <= TurnByTurnNavigationConstants.voiceSpeedCloseSeconds
                && distance > TurnByTurnNavigationConstants.atManeuverVoiceDistanceMeters
        }
    }
}

enum TurnByTurnDistanceFormatter {
    static func formatMeters(_ meters: Double) -> String {
        let clamped = max(0, meters)
        if clamped >= 160.934 {
            let miles = clamped / 1609.34
            if miles >= 10 {
                return String(format: "%.0f mi", miles)
            }
            return String(format: "%.1f mi", miles)
        }
        let feet = clamped * 3.28084
        if feet >= 1000 {
            return String(format: "%.0f ft", feet)
        }
        return String(format: "%.0f ft", max(1, feet))
    }
}

enum NavigationSSMLCleaner {
    static func plainText(from value: String) -> String {
        value
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

enum NavigationInstructionLabeling {
    /// Rewrites Mapbox "destination" wording for intermediate route stops. Finish keeps destination copy.
    static func relabeledForStopPoint(_ text: String) -> String {
        var result = text
        let patterns: [(String, String)] = [
            (#"(?i)\byour destination\b"#, "your Stop Point"),
            (#"(?i)\bthe destination\b"#, "the Stop Point"),
            (#"(?i)\bdestination\b"#, "Stop Point"),
        ]
        for (pattern, replacement) in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern) else { continue }
            let range = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, range: range, withTemplate: replacement)
        }
        return result
    }

    static func relabelStepForStopPoint(_ step: NavigationStep) -> NavigationStep {
        NavigationStep(
            instruction: relabeledForStopPoint(step.instruction),
            name: step.name,
            distanceMeters: step.distanceMeters,
            durationSeconds: step.durationSeconds,
            maneuver: NavigationManeuver(
                type: step.maneuver.type,
                modifier: step.maneuver.modifier,
                instruction: relabeledForStopPoint(step.maneuver.instruction)
            ),
            maneuverCoordinate: step.maneuverCoordinate,
            voiceInstructions: step.voiceInstructions.map {
                NavigationVoiceInstruction(
                    distanceAlongStepMeters: $0.distanceAlongStepMeters,
                    announcement: relabeledForStopPoint($0.announcement)
                )
            },
            geometryCoordinates: step.geometryCoordinates,
            maneuverArcLengthMeters: step.maneuverArcLengthMeters
        )
    }

    static func relabelLegsForStopPoints(_ legs: [NavigationLeg]) -> [NavigationLeg] {
        guard legs.count > 1 else { return legs }
        return legs.enumerated().map { index, leg in
            guard index < legs.count - 1 else { return leg }
            return NavigationLeg(
                steps: leg.steps.map(relabelStepForStopPoint),
                distanceMeters: leg.distanceMeters,
                durationSeconds: leg.durationSeconds
            )
        }
    }
}
