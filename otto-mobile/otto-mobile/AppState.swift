import Foundation
import Combine
import SwiftUI
import CoreLocation
import UIKit
@preconcurrency import UserNotifications
import WidgetKit
import os

/// When set, the root UI should open the Map tab and `MapScreen` should center on this coordinate, then call `consumePendingMapFocus()`.
struct PendingMapFocus: Equatable {
    let id: UUID
    let latitude: Double
    let longitude: Double
    /// When set, `MapScreen` opens the event peek sheet after centering.
    var eventID: String? = nil
    /// When set, `MapScreen` opens the saved-place marker detail sheet after centering.
    var savedPlaceID: String? = nil
    /// Snapshot for place detail when the place is not in local `savedPlaces` (e.g. chat attachment).
    var savedPlaceSnapshot: SavedPlaceDTO? = nil
}

/// When set, the root UI opens the Map tab and `MapScreen` selects this route and fits the camera.
struct PendingMapRouteSelection: Equatable {
    let id: UUID
    let route: SavedRouteDTO
    /// When true, `MapScreen` starts a route drive session after selecting the route.
    var startDriveAfterFocus: Bool = false
}

struct PendingCircleFocus: Equatable {
    let id: UUID
    let circleID: String
    /// When true, squad detail should select the Chat tab (e.g. after share-to-chat).
    var openChatTab: Bool = false
}

struct PendingSquadsInvitesFocus: Equatable {
    let id: UUID
}

struct PendingProfileFocus: Equatable {
    let id: UUID
    let userID: String
}

struct PendingDirectMessageFocus: Equatable {
    let id: UUID
    /// Preferred route when known (e.g. push payload).
    let conversationID: String?
    /// Fallback when opening by sender / legacy flows.
    let userID: String?
}

struct PendingEventFocus: Equatable {
    let id: UUID
    let eventRef: String
}

struct PendingEventsMyEventsFocus: Equatable {
    let id: UUID
}

struct PendingLocationSharingFocus: Equatable {
    let id: UUID
    let circleID: String
    let sharerUserID: String
}

@MainActor
final class AppState: ObservableObject {
    static let publicPresenceCircleID = "public"
    private static let appGroupID = "group.otto.otto-mobile"
    private static var sharedDefaults: UserDefaults? { UserDefaults(suiteName: appGroupID) }
    /// Stored as `DeviceToken.environment` — always `production` (single prod API + `APNS_ENV=production`).
    /// Xcode debug builds may issue sandbox APNs tokens; the backend retries the sandbox gateway on `BadDeviceToken` without relabeling.
    private static var apnsEnvironment: String { "production" }

    enum SharingAudience: String, CaseIterable {
        case circles
        case `public`
        case onlyMe
    }

    enum SharingSessionMode: String, CaseIterable {
        case shareNow = "share_now"
        case drivingOnly = "driving_only"
    }

    /// After phone OTP, net-new users finish signup (invite code if required, then display name) before receiving a token.
    enum SignupAfterOtpStep: Equatable {
        case inviteCode
        case displayName
    }

    enum StorageKeys {
        static let authToken = "otto.authToken"
        static let authUserID = "otto.authUserID"
        static let sharingEnabled = "otto.sharingEnabled"
        static let sharingCircleIDs = "otto.sharingCircleIDs"
        static let sharingAudience = "otto.sharingAudience"
        static let sharingDurationSeconds = "otto.sharingDurationSeconds"
        static let sharingSessionStartedAt = "otto.sharingSessionStartedAt"
        static let sharingSessionMode = "otto.sharingSessionMode"
        static let sharingCarID = "otto.sharingCarID"
        static let garageCars = "otto.garageCars"
        static let soundEffectsEnabled = "otto.soundEffectsEnabled"
        static let autoEventCheckInEnabled = "otto.autoEventCheckInEnabled"
        static let sharingSafetyDisclaimerAcknowledged = "otto.sharingSafetyDisclaimerAcknowledged"
        static let showPublicGoingEventsOnProfile = "otto.showPublicGoingEventsOnProfile"
        static let driveStatsVisibility = "otto.driveStatsVisibility"
        static let marketingOnboardingCompleted = "otto.marketingOnboardingCompleted"
        static let squadLastAccessedAt = "otto.squadLastAccessedAt"
        static let routeBuilderEducationSeen = "otto.routeBuilderEducationSeen"
        static let pendingSquadInviteCode = "otto.pendingSquadInviteCode"
        static let pendingSquadInviteSquadId = "otto.pendingSquadInviteSquadId"
        static let recordDriveOnStartEnabled = "otto.recordDriveOnStartEnabled"
    }

    /// Matches backend default when the key is absent: automatic event check-ins are on.
    private static let defaultAutoEventCheckInEnabled = true
    /// Matches backend: show public “going” events on profile and web unless opted out.
    private static let defaultShowPublicGoingEventsOnProfile = true
    /// Matches backend default: drive stats visible to shared squad mates only.
    private static let defaultDriveStatsVisibility: DriveStatsVisibilitySetting = .squads
    /// Geofence + map check-in ring; keep aligned with `EVENT_CHECK_IN_RADIUS_METERS` on the server.
    static let eventCheckInRadiusMeters: CLLocationDistance = 150
    /// Map event beacons show when start is within this many days and the event has not ended.
    static let mapEventDisplayHorizonDays = 14
    private static let eventCheckInMonitoringHorizonSeconds: TimeInterval = 86400
    private static let maxMonitoredEventCheckInRegions = 20

    /// App Group defaults for sharing keys (home screen widget reads the same store).
    private static var sharingPersistenceDefaults: UserDefaults {
        sharedDefaults ?? .standard
    }

    /// Last `OttoSharingUserDefaultsKeys.sharingRevision` applied into memory (avoids stale UI after widget edits).
    private var lastReconciledSharingRevision: Double = 0
    private var lastMirroredDrivingOnlyPausedForWidget: Bool?

    /// When set, ``RootTabView`` selects the Garage tab (e.g. “My Garage” from profile).
    @Published private(set) var garageTabFocusRequest: UUID?
    /// When set, ``RootTabView`` selects the Map tab (e.g. `otto://map` from the sharing widget).
    @Published private(set) var mapTabOnlyRequest: UUID?
    /// When set, ``RootTabView`` selects the Map tab and ``MapScreen`` presents the location-sharing sheet (`otto://share`).
    @Published private(set) var pendingSharingSheetPresentation: UUID?

    @Published var isSharingEnabled = false
    /// Live sharing started from Quick/Route drive dock — no timer expiry until the drive stops.
    @Published private(set) var sharingTiedToActiveDrive = false
    @Published var sharingCircleIDs: Set<String> = []
    @Published var sharingAudience: SharingAudience = .circles
    @Published var sharingDurationSeconds: TimeInterval = 3600
    @Published var sharingSessionStartedAt: Date?
    @Published var sharingSessionMode: SharingSessionMode = .shareNow
    /// Sharing sheet “Record this Drive” — records path/history while sharing (default off).
    @Published var sharingSaveDriveEnabled = false
    /// While `sharingSessionMode == .drivingOnly`, true until motion reports `.driving` (in-vehicle / driving); stationary at a desk stays non-live.
    @Published private(set) var isDrivingOnlyBroadcastPaused = false
    func resetDrivingOnlyPauseForSharingStop() {
        drivingOnlyNotDrivingInactiveEmitted = false
        isDrivingOnlyBroadcastPaused = false
    }
    @Published var circles: [DriveCircle]
    @Published private var squadLastAccessedAtByID: [String: TimeInterval] = [:]
    @Published var selectedCircleID: String
    @Published var currentUserID: String = ""
    @Published private(set) var currentUser: UserDTO?
    /// From `GET /api/auth/me` — users you have blocked (server-enforced for DMs and contacts).
    @Published private(set) var blockedUserIDs: Set<String> = []
    @Published var allUsers: [UserDTO] = []
    @Published var contacts: [UserDTO] = []
    @Published var publicPresenceMembers: [FriendLocation] = []
    @Published var pendingInvitesByCircleID: [String: [CircleInviteDTO]] = [:]
    @Published var myCircleInvites: [CircleInviteDTO] = []
    @Published var garageCars: [GarageCar] = []
    @Published var selectedSharingCarID: String = ""

    var selectedSharingCar: GarageCar? {
        let selectedID = selectedSharingCarID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !selectedID.isEmpty else { return nil }
        return garageCars.first {
            $0.id.trimmingCharacters(in: .whitespacesAndNewlines) == selectedID
        }
    }

    var selectedSharingCarBrandLogoURL: URL? {
        selectedSharingCar?.brandLogoURL
    }

    var showsSelfDriveBrandLogoOnMap: Bool {
        if activeRouteDriveSession?.isActive == true || activeRouteDriveSession?.isArmed == true {
            return true
        }
        if activeDriveSession != nil { return true }
        if isSharingEnabled { return true }
        return false
    }

    var mapSelfBrandLogoURL: URL? {
        guard showsSelfDriveBrandLogoOnMap else { return nil }
        return selectedSharingCarBrandLogoURL
    }

    func peerBrandLogoURL(for member: FriendLocation) -> URL? {
        guard member.isActive else { return nil }
        return CarBrandLogoCatalog.logoURL(slug: member.brandLogoSlug)
    }
    @Published var recentDrives: [DriveDTO] = []
    @Published private(set) var pendingDriveArchives: [PendingDriveArchive] = []

    func replacePendingDriveArchives(_ archives: [PendingDriveArchive]) {
        pendingDriveArchives = archives
    }

    func updatePendingDriveArchives(_ update: (inout [PendingDriveArchive]) -> Void) {
        update(&pendingDriveArchives)
    }

    func resetActiveDriveTelemetryAfterStop() {
        activeDriveID = nil
        activeDriveDistanceMeters = 0
        activeDriveMaxSpeedMph = 0
        activeDrivePathTrail = []
        lastDriveLocationForDistance = nil
    }
    @Published var upcomingEvents: [EventDTO] = []
    @Published var communityEvents: [EventDTO] = []
    /// Squad/circle events the user is going to — merged into auto check-in geofence eligibility.
    @Published private(set) var squadGoingEventsForCheckIn: [EventDTO] = []
    /// Events opened from detail surfaces are watched too, so squad/chat/push entry points do not miss auto check-in.
    @Published private(set) var detailOpenedEventsForCheckIn: [EventDTO] = []
    private var foregroundAutoCheckInAttemptedEventIDs: Set<String> = []
    /// Fetched on demand for chat `eventAttachment` rows when the event is no longer in upcoming/squad lists (e.g. past check-in window).
    @Published private(set) var chatAttachmentHydratedEventsById: [String: EventDTO] = [:]
    private var chatAttachmentHydrationInFlight: Set<String> = []
    @Published var savedPlaces: [SavedPlaceDTO] = []
    let chatStore = ChatStore()
    @Published var latestCircleChatMessage: CircleChatMessageDTO?
    @Published var latestDirectMessage: DirectMessageDTO?
    @Published var unreadChatCountsByCircleID: [String: Int] = [:]
    @Published var directConversationsByUserID: [String: DirectConversationDTO] = [:]
    @Published var unreadDirectMessageCountsByConversationID: [String: Int] = [:]
    @Published var isChatRealtimeConnected = false
    @Published var chatRealtimeStatusMessage: String?
    /// Set from My places (and similar); cleared when `MapScreen` consumes it after centering.
    @Published private(set) var pendingMapFocus: PendingMapFocus?
    /// Optional event card for the map peek sheet when focus comes from event detail (cleared with focus).
    @Published private(set) var pendingMapEventPreview: EventDTO?
    /// Set from Drive Summary “View on Map”; cleared when `MapScreen` consumes it.
    @Published private(set) var pendingMapRouteSelection: PendingMapRouteSelection?
    /// Set from map/user sheets; the root switches to Circles and `CirclesScreen` consumes it to push detail.
    @Published private(set) var pendingCircleFocus: PendingCircleFocus?
    @Published private(set) var pendingSquadsInvitesFocus: PendingSquadsInvitesFocus?
    @Published private(set) var pendingProfileFocus: PendingProfileFocus?
    @Published private(set) var pendingDirectMessageFocus: PendingDirectMessageFocus?
    @Published private(set) var pendingEventFocus: PendingEventFocus?
    @Published private(set) var pendingEventsMyEventsFocus: PendingEventsMyEventsFocus?
    @Published private(set) var pendingLocationSharingFocus: PendingLocationSharingFocus?
    @Published var soundEffectsEnabled = true

    /// Readable from AppDelegate without an AppState instance.
    static var isSoundEffectsEnabled: Bool {
        if UserDefaults.standard.object(forKey: StorageKeys.soundEffectsEnabled) != nil {
            return UserDefaults.standard.bool(forKey: StorageKeys.soundEffectsEnabled)
        }
        return true
    }
    /// Mirrored from the server after `fetchMe`; defaults to on when unset locally or on the user record.
    @Published var autoEventCheckInEnabled = AppState.defaultAutoEventCheckInEnabled
    @Published var sharingSafetyDisclaimerAcknowledged = false
    @Published var routeBuilderEducationSeen = false
    /// When false, omit this user’s public “going” RSVPs from profile / web / public-event contact lists.
    @Published var showPublicGoingEventsOnProfile = AppState.defaultShowPublicGoingEventsOnProfile
    /// Who can see aggregated driving stats / progression on your profile (`fetchMe` / PATCH).
    @Published var driveStatsVisibility: DriveStatsVisibilitySetting = AppState.defaultDriveStatsVisibility
    @Published var errorMessage: String?
    /// True when the last squads roster fetch failed (empty cache).
    @Published var circlesFetchFailed = false
    /// True when the last featured events fetch failed with an empty cache.
    @Published var featuredEventsFetchFailed = false
    /// True when the last community events fetch failed with an empty cache.
    @Published var communityEventsFetchFailed = false
    @Published var activeToast: AppToast?
    @Published var activeProfileLevelUp: ProfileLevelUpDTO?
    /// Bumped when a non-preview level-up is shown so profile driving stats (progression) reload.
    @Published private(set) var profileProgressionRefreshTick: UInt = 0
    @Published var activeDriveID: String?
    @Published var activeDriveSession: DriveSession?
    @Published var activeRouteDriveSession: RouteDriveSessionState?
    /// Saved route geometry for the in-progress route drive (survives Map tab deselect / tab switches).
    @Published var activeRouteDriveRoute: SavedRouteDTO?
    @Published private(set) var routeDrivePathSamples: [DrivePathSample] = []
    @Published var routeDriveFeedbackEvent: RouteDriveFeedbackEvent?
    @Published private(set) var activeDrivePathTrail: [DrivePathSample] = []
    /// True while the main Map tab is visible; used to keep foreground-only location updates alive after permission is granted.
    @Published var isMapScreenActive = false
    /// True while Route Builder is presented from any entry point; MapScreen suspends GL to avoid dual Mapbox instances.
    @Published private(set) var isRouteBuilderPresented = false
    /// True while the Events tab is visible; keeps foreground GPS for distance sorting after the Events primer.
    @Published var isEventsScreenActive = false
    /// True while the Squads root tab is selected; chat read/unread focus follows this when threads stay warm off-screen.
    @Published var circlesRootTabIsSelected = true
    /// True while the map drive-line route builder has an in-progress line or is actively building (`MapScreen` canonical).
    @Published var isMapRouteSessionActive = false
    /// Bumped to re-run centralized location session sync from Map/Events after permission grants.
    @Published private(set) var locationSessionSyncTick: UInt = 0
    @Published var isAuthenticated: Bool = false
    @Published var authPhoneNumber: String = ""
    @Published var requiresOnboardingName: Bool = false
    /// Server-issued JWT between `verify-otp` and `complete-signup` for brand-new phone numbers.
    @Published var signupChallengeToken: String = ""
    /// When true, `complete-signup` must include an invite code (admin phones skip at verification time).
    @Published var signupNeedsInviteCode: Bool = false
    /// Non-nil while the user is completing invite/name steps before credentials exist.
    @Published var signupAfterOtpStep: SignupAfterOtpStep?
    var pendingSignupInviteCode: String = ""
    /// One-time full-screen marketing carousel after first authenticated boot; persists across logout.
    @Published private(set) var marketingOnboardingCompleted: Bool = false
    /// When true (e.g. from Settings), present the carousel again without resetting completion.
    @Published var marketingOnboardingReplayRequested: Bool = false
    @Published var pendingInviteToken: String?
    @Published var pendingInviteSquadId: String?
    @Published var squadInvitePrompt: InviteLinkResolveDTO?
    @Published var isAcceptingSquadInvitePrompt = false

    /// Path length (meters) for the in-progress drive, from consecutive GPS fixes while sharing or drive session.
    var activeDriveDistanceMeters: Double = 0
    var activeDriveMaxSpeedMph: Double = 0
    var lastDriveLocationForDistance: CLLocation?
    private var lastKnownInviteCount: Int = 0
    private var lastKnownInviteIDs: [String] = []
    private var lastKnownActiveSharersByCircleID: [String: Set<String>] = [:]
    private var lastSharingToastAtByUserID: [String: Date] = [:]
    /// Drives self toasts for driving-only pause/resume; `suppressInitialDrivingOnlyPauseToast` avoids duplicating the start toast when the session begins not-yet-live.
    private var lastSelfDrivingOnlyPausedForToast: Bool?
    private var suppressInitialDrivingOnlyPauseToast = false
    /// While driving-only live presence is off (`movementMode != .driving`), emit inactive once per spell until driving is detected again.
    var drivingOnlyNotDrivingInactiveEmitted = false
    private var activeChatCircleID: String?
    private var activeDirectConversationID: String?
    private var chatSocketTask: URLSessionWebSocketTask?
    private var chatSocketReceiveTask: Task<Void, Never>?
    private var chatSocketDelegate: ChatSocketDelegate?
    private var chatSocketSession: URLSession?
    private var sessionUnauthorizedObserver: NSObjectProtocol?
    private var presenceSubscribedCircleIDs: Set<String> = []
    private var directSubscribedConversationIDs: Set<String> = []
    /// RAM-only timelines for instant revisit (WhatsApp-style); logout clears both. Always reconciled with fetch/socket.
    private var squadChatTranscriptByCircleID: [String: [CircleChatMessageDTO]] = [:]
    private var directMessageTranscriptByConversationID: [String: [DirectMessageDTO]] = [:]
    private var pendingPushDeviceToken: String?
    private let presenceDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static func parsePresenceDate(_ rawValue: String?) -> Date? {
        guard let rawValue else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: rawValue)
    }

    func circlesSortedByRecentAccess(_ source: [DriveCircle]) -> [DriveCircle] {
        source.enumerated().sorted { lhs, rhs in
            let lhsAccessedAt = squadLastAccessedAtByID[lhs.element.id] ?? 0
            let rhsAccessedAt = squadLastAccessedAtByID[rhs.element.id] ?? 0
            if lhsAccessedAt != rhsAccessedAt {
                return lhsAccessedAt > rhsAccessedAt
            }
            return lhs.offset < rhs.offset
        }.map(\.element)
    }

    func markCircleAccessed(_ circleID: String) {
        guard circles.contains(where: { $0.id == circleID }) else { return }
        squadLastAccessedAtByID[circleID] = Date().timeIntervalSince1970
        UserDefaults.standard.set(squadLastAccessedAtByID, forKey: StorageKeys.squadLastAccessedAt)
        cacheShareExtensionSquads()
    }

    private static let sharingToastDedupWindow: TimeInterval = 12
    /// Throttle for `appendDrivePoint` — GPS can fire many times per second; networking each fix freezes the main actor.
    var lastDrivePathNetworkAt: Date = .distantPast
    static let minDrivePathInterval: TimeInterval = 2.5
    private static let maxActiveDrivePathTrailCount = 500
    private static let minActiveDrivePathTrailDistanceMeters = 8.0

    var isActivatingRouteDriveSession = false
    var lastRouteDriveProgressWriteAt = Date.distantPast
    var routeDriveProgressTask: Task<Void, Never>?
    static let routeStartDriveRangeMeters = 500.0 * 0.3048
    private static let minRouteDrivePathSampleDistanceMeters = 8.0
    private static let maxRouteDrivePathSampleCount = 500

    /// Foreground pings (`inApp: true`, `isActive: false`) so squad mates see **Online** without coordinates.
    private var lastInAppPresenceHeartbeatAt: Date?
    private static let inAppPresenceHeartbeatMinInterval: TimeInterval = 45

    /// `init` and `MapScreen.onAppear` both call `refreshCircles()`; without coalescing, two full loads run when the API is up.
    private var refreshCirclesBusy = false
    private var refreshCirclesWaiters: [CheckedContinuation<Void, Never>] = []
    private var warmSquadChatTask: Task<Void, Never>?
    /// Once per app launch, refetch squad transcripts on first unread reconcile (kill-app messages while WS was offline).
    private var didRefreshSquadUnreadHeadsThisLaunch = false
    private struct CirclesLoadResult {
        let mapped: [DriveCircle]
        let users: [UserDTO]
    }

    init() {
        circles = []
        contacts = []
        selectedCircleID = ""
        OttoSharingPersistence.seedAppGroupFromStandardIfNeeded()
        let sp = Self.sharingPersistenceDefaults
        isSharingEnabled = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        if let savedCircleIDs = sp.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) as? [String] {
            sharingCircleIDs = Set(savedCircleIDs)
        }
        var restoredPublicSharing = false
        if let raw = sp.string(forKey: OttoSharingUserDefaultsKeys.sharingAudience),
           let mode = SharingAudience(rawValue: raw) {
            restoredPublicSharing = mode == .public
            sharingAudience = restoredPublicSharing ? .onlyMe : mode
        }
        if restoredPublicSharing {
            isSharingEnabled = false
            sharingCircleIDs.removeAll()
            sp.set(false, forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
            sp.set([], forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs)
            sp.set(SharingAudience.onlyMe.rawValue, forKey: OttoSharingUserDefaultsKeys.sharingAudience)
            OttoSharingPersistence.bumpRevision(in: sp)
            OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
        }
        let savedDuration = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        if savedDuration > 0 {
            sharingDurationSeconds = savedDuration
        }
        let savedStartedAt = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        if savedStartedAt > 0 {
            sharingSessionStartedAt = Date(timeIntervalSince1970: savedStartedAt)
        }
        if let rawMode = sp.string(forKey: OttoSharingUserDefaultsKeys.sharingSessionMode),
           let mode = SharingSessionMode(rawValue: rawMode) {
            sharingSessionMode = mode
        }
        if sp.object(forKey: OttoSharingUserDefaultsKeys.sharingSaveDriveEnabled) != nil {
            sharingSaveDriveEnabled = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingSaveDriveEnabled)
        }
        if sp.object(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused) != nil {
            isDrivingOnlyBroadcastPaused = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        }
        if isSharingEnabled, !sharingTiedToActiveDrive, sharingRemainingSeconds() == nil {
            isSharingEnabled = false
            sharingSessionStartedAt = nil
            sp.set(false, forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
            sp.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
            OttoSharingPersistence.bumpRevision(in: sp)
            OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
        }
        lastReconciledSharingRevision = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        if isSharingEnabled {
            lastMirroredDrivingOnlyPausedForWidget = sharingSessionMode == .drivingOnly && isDrivingOnlyBroadcastPaused
        } else {
            lastMirroredDrivingOnlyPausedForWidget = nil
        }
        selectedSharingCarID = UserDefaults.standard.string(forKey: StorageKeys.sharingCarID) ?? ""
        if UserDefaults.standard.object(forKey: StorageKeys.soundEffectsEnabled) != nil {
            soundEffectsEnabled = UserDefaults.standard.bool(forKey: StorageKeys.soundEffectsEnabled)
        }
        if UserDefaults.standard.object(forKey: StorageKeys.autoEventCheckInEnabled) != nil {
            autoEventCheckInEnabled = UserDefaults.standard.bool(forKey: StorageKeys.autoEventCheckInEnabled)
        } else {
            autoEventCheckInEnabled = Self.defaultAutoEventCheckInEnabled
        }
        sharingSafetyDisclaimerAcknowledged = UserDefaults.standard.bool(
            forKey: StorageKeys.sharingSafetyDisclaimerAcknowledged
        )
        routeBuilderEducationSeen = UserDefaults.standard.bool(forKey: StorageKeys.routeBuilderEducationSeen)
        if let savedInviteCode = UserDefaults.standard.string(forKey: StorageKeys.pendingSquadInviteCode),
           !savedInviteCode.isEmpty {
            pendingInviteToken = savedInviteCode
            pendingSignupInviteCode = savedInviteCode
        }
        pendingInviteSquadId = UserDefaults.standard.string(forKey: StorageKeys.pendingSquadInviteSquadId)
        if UserDefaults.standard.object(forKey: StorageKeys.showPublicGoingEventsOnProfile) != nil {
            showPublicGoingEventsOnProfile = UserDefaults.standard.bool(forKey: StorageKeys.showPublicGoingEventsOnProfile)
        } else {
            showPublicGoingEventsOnProfile = Self.defaultShowPublicGoingEventsOnProfile
        }
        if let raw = UserDefaults.standard.string(forKey: StorageKeys.driveStatsVisibility),
           let parsed = DriveStatsVisibilitySetting(rawValue: raw) {
            driveStatsVisibility = parsed
        } else {
            driveStatsVisibility = Self.defaultDriveStatsVisibility
        }
        marketingOnboardingCompleted = UserDefaults.standard.bool(forKey: StorageKeys.marketingOnboardingCompleted)
        if let data = UserDefaults.standard.data(forKey: StorageKeys.garageCars),
           let savedCars = try? JSONDecoder().decode([GarageCar].self, from: data) {
            garageCars = savedCars
            if selectedSharingCarID.isEmpty {
                selectedSharingCarID = savedCars.first(where: \.isPrimary)?.id ?? savedCars.first?.id ?? ""
            }
        }
        if let savedToken = UserDefaults.standard.string(forKey: StorageKeys.authToken), !savedToken.isEmpty {
            APIClient.shared.setAuthToken(savedToken)
            isAuthenticated = true
            currentUserID = UserDefaults.standard.string(forKey: StorageKeys.authUserID) ?? ""
            cacheShareExtensionAuth(token: savedToken, userID: currentUserID)
            Task { await restoreSessionFromToken() }
        }
        if let savedRecency = UserDefaults.standard.dictionary(forKey: StorageKeys.squadLastAccessedAt) as? [String: TimeInterval] {
            squadLastAccessedAtByID = savedRecency
        }
        reloadPendingDriveArchives()
        #if !targetEnvironment(simulator)
        if isAuthenticated {
            refreshCirclesAsync()
        }
        #endif

        sessionUnauthorizedObserver = NotificationCenter.default.addObserver(
            forName: .ottoSessionUnauthorized,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            // Delivered on `.main`; make the actor hop explicit for Swift concurrency diagnostics.
            MainActor.assumeIsolated {
                self?.logout()
            }
        }
    }

    deinit {
        if let sessionUnauthorizedObserver {
            NotificationCenter.default.removeObserver(sessionUnauthorizedObserver)
        }
    }

    var selectedCircle: DriveCircle? {
        circles.first { $0.id == selectedCircleID }
    }

    var sharingAudienceLabel: String {
        sharingSquadSummary(for: sharingCircleIDs)
    }

    var sharingExpiresAt: Date? {
        guard isSharingEnabled, !sharingTiedToActiveDrive, let sharingSessionStartedAt else { return nil }
        return sharingSessionStartedAt.addingTimeInterval(sharingDurationSeconds)
    }

    func sharingRemainingSeconds(now: Date = Date()) -> TimeInterval? {
        guard isSharingEnabled, !sharingTiedToActiveDrive else { return nil }
        guard let expiresAt = sharingExpiresAt else { return nil }
        let remaining = expiresAt.timeIntervalSince(now)
        return remaining > 0 ? remaining : nil
    }

    var isSharingSessionActive: Bool {
        guard isSharingEnabled else { return false }
        if sharingTiedToActiveDrive { return true }
        return sharingRemainingSeconds() != nil
    }

    func sharingSquadSummary(for circleIDs: Set<String>) -> String {
        let selected = circles.filter { circleIDs.contains($0.id) }
        if selected.count > 1 {
            return "\(selected.count) squads"
        }
        if let first = selected.first {
            return first.name
        }
        return selectedCircle?.name ?? "My Squad"
    }

    var effectiveSharingCircleIDs: [String] {
        guard isSharingSessionActive else { return [] }
        return Array(sharingCircleIDs)
    }

    /// `true` when the user should show as actively sharing (map pill / markers). `false` while a driving-only session has not yet detected driving (`movementMode != .driving`).
    var isPublishingLiveSharingPresence: Bool {
        guard isSharingEnabled else { return false }
        if sharingSessionMode == .drivingOnly, isDrivingOnlyBroadcastPaused { return false }
        return true
    }

    /// Internal map/settings debug UI (allowlisted phone numbers only).
    var canAccessInternalDebugTools: Bool {
        let phone = currentUser?.phoneNumber ?? authPhoneNumber
        return OttoDebugSettings.isInternalDebugToolsAllowed(phoneNumber: phone)
    }

    /// Garage car chips during drive flows; hidden for fixed OTP demo account.
    var showsDriveCarPicker: Bool {
        !OttoPhone.isDemoBypassPhone(currentUser?.phoneNumber ?? authPhoneNumber)
    }

    /// Map / profile ring color for the signed-in user (from `mapAccentKey` when loaded).
    /// Routes product access (open to all signed-in users).
    var hasRoutesAccess: Bool {
        true
    }

    var currentUserMapAccentColor: Color {
        MapAccentPalette.resolvedColor(
            mapAccentKey: allUsers.first(where: { $0.id == currentUserID })?.mapAccentKey,
            userId: currentUserID
        )
    }

    func sharedSquads(with userID: String) -> [DriveCircle] {
        guard userID != currentUserID else { return [] }
        return circles.filter { circle in
            circle.members.contains(where: { $0.id == userID }) &&
            circle.members.contains(where: { $0.id == currentUserID })
        }
    }

    func canDirectMessage(userID: String) -> Bool {
        guard !blockedUserIDs.contains(userID) else { return false }
        return !sharedSquads(with: userID).isEmpty
    }

    @discardableResult
    func blockUser(_ userID: String) async -> Bool {
        guard userID != currentUserID, !userID.isEmpty else { return false }
        do {
            let user = try await APIClient.shared.blockUser(targetUserId: userID)
            applyAutoCheckInPreferenceFromUser(user)
            blockedUserIDs = Set(user.resolvedBlockedUserIds)
            await refreshDirectConversations()
            await refreshContacts()
            return true
        } catch {
            errorMessage = "Couldn’t update block list."
            return false
        }
    }

    @discardableResult
    func unblockUser(_ userID: String) async -> Bool {
        guard userID != currentUserID, !userID.isEmpty else { return false }
        do {
            let user = try await APIClient.shared.unblockUser(targetUserId: userID)
            applyAutoCheckInPreferenceFromUser(user)
            blockedUserIDs = Set(user.resolvedBlockedUserIds)
            await refreshDirectConversations()
            await refreshContacts()
            return true
        } catch {
            errorMessage = "Couldn’t update block list."
            return false
        }
    }

    func selectCircle(_ circleID: String) {
        selectedCircleID = circleID
    }

    func setSharingEnabled(_ isEnabled: Bool) {
        if isEnabled {
            _ = startSharingSession(
                circleIDs: sharingCircleIDs.isEmpty && !selectedCircleID.isEmpty ? Set([selectedCircleID]) : sharingCircleIDs,
                durationSeconds: sharingDurationSeconds,
                mode: sharingSessionMode
            )
        } else {
            stopSharingSession()
        }
    }

    func setSoundEffectsEnabled(_ isEnabled: Bool) {
        soundEffectsEnabled = isEnabled
        UserDefaults.standard.set(isEnabled, forKey: StorageKeys.soundEffectsEnabled)
    }

    func requestMarketingOnboardingReplay() {
        marketingOnboardingReplayRequested = true
    }

    func completeMarketingOnboardingIfNeeded() {
        guard !marketingOnboardingCompleted else { return }
        marketingOnboardingCompleted = true
        UserDefaults.standard.set(true, forKey: StorageKeys.marketingOnboardingCompleted)
    }

    /// Called when the user finishes the carousel (Continue through Get Started, or Skip).
    func marketingOnboardingDidFinish(wasReplay: Bool) {
        if wasReplay {
            marketingOnboardingReplayRequested = false
        } else {
            completeMarketingOnboardingIfNeeded()
        }
    }

    func setAutoEventCheckInEnabled(_ enabled: Bool) {
        autoEventCheckInEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: StorageKeys.autoEventCheckInEnabled)
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        Task(priority: .utility) {
            do {
                try await APIClient.shared.updateUserAutoEventCheckIn(userId: currentUserID, enabled: enabled)
            } catch {
                await MainActor.run {
                    errorMessage = "Couldn’t save Auto Check-In. Try again."
                }
            }
        }
    }

    func acknowledgeRouteBuilderEducation() {
        routeBuilderEducationSeen = true
        UserDefaults.standard.set(true, forKey: StorageKeys.routeBuilderEducationSeen)
    }

    func setRouteBuilderPresented(_ presented: Bool) {
        guard isRouteBuilderPresented != presented else { return }
        isRouteBuilderPresented = presented
        #if DEBUG
        print("[AppState] isRouteBuilderPresented=\(presented)")
        #endif
    }

    /// Suspend the tab Mapbox map before Route Builder is presented (matches Map tab Manage timing).
    func prepareRouteBuilderPresentation() {
        setRouteBuilderPresented(true)
        requestLocationSessionSync()
    }

    func acknowledgeSharingSafetyDisclaimer() {
        sharingSafetyDisclaimerAcknowledged = true
        UserDefaults.standard.set(true, forKey: StorageKeys.sharingSafetyDisclaimerAcknowledged)
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        Task(priority: .utility) {
            do {
                try await APIClient.shared.updateUserSharingSafetyDisclaimerAcknowledged(
                    userId: currentUserID,
                    acknowledged: true
                )
            } catch {
                OttoLog.app.error("sharing safety acknowledgement sync failed: \(String(describing: error))")
            }
        }
    }

    func setShowPublicGoingEventsOnProfile(_ enabled: Bool) {
        showPublicGoingEventsOnProfile = enabled
        UserDefaults.standard.set(enabled, forKey: StorageKeys.showPublicGoingEventsOnProfile)
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        Task(priority: .utility) {
            do {
                try await APIClient.shared.updateUserShowPublicGoingEventsOnProfile(userId: currentUserID, enabled: enabled)
            } catch {
                await MainActor.run {
                    errorMessage = "Couldn’t save profile event sharing. Try again."
                }
            }
        }
    }

    func setDriveStatsVisibility(_ visibility: DriveStatsVisibilitySetting) {
        let previous = driveStatsVisibility
        driveStatsVisibility = visibility
        UserDefaults.standard.set(visibility.rawValue, forKey: StorageKeys.driveStatsVisibility)
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        Task(priority: .utility) {
            do {
                try await APIClient.shared.updateUserDriveStatsVisibility(userId: currentUserID, visibility: visibility)
                await MainActor.run {
                    if let idx = allUsers.firstIndex(where: { $0.id == currentUserID }) {
                        let u = allUsers[idx]
                        allUsers[idx] = UserDTO(
                            id: u.id,
                            displayName: u.displayName,
                            handle: u.handle,
                            avatarUrl: u.avatarUrl,
                            mapAccentKey: u.mapAccentKey,
                            phoneNumber: u.phoneNumber,
                            vehicle: u.vehicle,
                            lastPresenceAt: u.lastPresenceAt,
                            autoEventCheckInEnabled: u.autoEventCheckInEnabled,
                            sharingSafetyDisclaimerAcknowledged: u.sharingSafetyDisclaimerAcknowledged,
                            showPublicGoingEventsOnProfile: u.showPublicGoingEventsOnProfile,
                            driveStatsVisibility: visibility.rawValue,
                            routesAccessEnabled: u.routesAccessEnabled,
                            blockedUserIds: u.blockedUserIds,
                            timeZone: u.timeZone,
                            timeZoneUpdatedAt: u.timeZoneUpdatedAt
                        )
                        currentUser = allUsers[idx]
                    }
                }
            } catch {
                await MainActor.run {
                    driveStatsVisibility = previous
                    UserDefaults.standard.set(previous.rawValue, forKey: StorageKeys.driveStatsVisibility)
                    errorMessage = "Couldn’t save drive stats visibility. Try again."
                }
            }
        }
    }

    /// Shows a transient toast (same pipeline as sharing / squad toasts).
    func presentUserToast(text: String, systemImage: String) {
        activeToast = AppToast(text: text, systemImage: systemImage)
    }

    func presentDeletedToast(for item: String) {
        activeToast = AppToast.deleted(item)
    }

    func presentDeleteFailedToast(for item: String) {
        activeToast = AppToast.deleteFailed(item)
    }

    /// Home-screen icon badge count derived from in-app unread (squads + DMs).
    private var chatUnreadCountForApplicationIcon: Int {
        min(max(0, totalChatUnreadCount), 99_999)
    }

    /// Applies badge on the home-screen icon (local). Do not call from WebSocket message handlers.
    private func applyHomeScreenChatIconBadge(_ count: Int) {
        let clamped = min(max(0, count), 99_999)
        Task { @MainActor in
            if #available(iOS 17.0, *) {
                do {
                    try await UNUserNotificationCenter.current().setBadgeCount(clamped)
                } catch {
                    OttoLog.app.error("setBadgeCount failed: \(String(describing: error))")
                }
            } else {
                UIApplication.shared.applicationIconBadgeNumber = clamped
            }
        }
    }

    /// After read/reconcile: mirror in-app unread on the icon and sync server counter for the next chat push.
    func syncHomeScreenChatIconBadgeWithBackend() {
        let count = isAuthenticated ? chatUnreadCountForApplicationIcon : 0
        applyHomeScreenChatIconBadge(count)
        guard isAuthenticated, count >= 0 else { return }
        Task {
            do {
                try await APIClient.shared.patchMeChatIconBadge(count: count)
            } catch {
                OttoLog.app.error("chat icon badge sync failed: \(String(describing: error))")
            }
        }
    }

    /// Proactively warm squad chat transcripts after circles load (TTL-gated, unread squads first).
    func warmSquadChatTranscripts() async {
        guard isAuthenticated else { return }
        if let warmSquadChatTask {
            await warmSquadChatTask.value
            return
        }
        let circleIDs = circles.map(\.id)
        guard !circleIDs.isEmpty else { return }
        let task = Task { @MainActor in
            await chatStore.warmSquadChatTranscripts(circleIDs: circleIDs)
            for circleID in circleIDs {
                if let messages = chatStore.cachedSquadMessages(circleID: circleID), !messages.isEmpty {
                    squadChatTranscriptByCircleID[circleID] = messages
                }
            }
            publishChatUnreadFromStore()
        }
        warmSquadChatTask = task
        await task.value
        warmSquadChatTask = nil
    }

    /// Load persisted squad transcripts into memory before first squad open.
    func hydrateSquadChatCachesFromDisk() {
        chatStore.preloadDiskCacheIfNeeded()
        if !currentUserID.isEmpty {
            chatStore.reconcileUnreadState(currentUserID: currentUserID)
        }
        for circleID in circles.map(\.id) {
            if let messages = chatStore.cachedSquadMessages(circleID: circleID), !messages.isEmpty {
                squadChatTranscriptByCircleID[circleID] = messages
            }
        }
    }

    /// Refreshes unread counts from local cache, then fetches missing squad/DM heads from the network.
    func reconcileChatUnreadStateFromNetworkIfNeeded() async {
        guard isAuthenticated else { return }
        chatStore.bindUnreadTracking(currentUserID: currentUserID)
        chatStore.reconcileUnreadState(currentUserID: currentUserID)

        for conversation in directConversationsByUserID.values {
            if chatStore.cachedDirectMessages(conversationID: conversation.id)?.isEmpty != false {
                chatStore.recomputeDirectUnreadFromPreview(conversation)
            }
        }

        let forceSquadHeadRefresh = !didRefreshSquadUnreadHeadsThisLaunch
        didRefreshSquadUnreadHeadsThisLaunch = true

        await withTaskGroup(of: Void.self) { group in
            for circle in circles {
                let circleID = circle.id
                let cachedEmpty = chatStore.cachedSquadMessages(circleID: circleID)?.isEmpty != false
                let shouldFetch =
                    forceSquadHeadRefresh
                    || chatStore.squadShouldRefreshFromNetwork(circleID: circleID, messagesEmpty: cachedEmpty)
                guard shouldFetch else { continue }
                group.addTask { [weak self] in
                    guard let self else { return }
                    do {
                        let fetched = try await APIClient.shared.fetchCircleChatMessages(circleId: circleID, limit: 50)
                        await MainActor.run {
                            self.chatStore.replaceSquadMessages(circleID: circleID, messages: fetched)
                            self.chatStore.markSquadNetworkFetchSucceeded(circleID: circleID)
                            self.chatStore.unreadTracker.recomputeSquad(circleID: circleID, messages: fetched)
                        }
                    } catch {
                        OttoLog.api.error("Unread reconcile squad fetch failed circle=\(circleID): \(String(describing: error))")
                    }
                }
            }
            for conversation in directConversationsByUserID.values {
                let conversationID = conversation.id
                if chatStore.cachedDirectMessages(conversationID: conversationID)?.isEmpty != false {
                    group.addTask { [weak self] in
                        guard let self else { return }
                        do {
                            let fetched = try await APIClient.shared.fetchDirectMessages(conversationId: conversationID, limit: 50)
                            await MainActor.run {
                                self.chatStore.replaceDirectMessages(conversationID: conversationID, messages: fetched)
                                self.chatStore.unreadTracker.recomputeDirect(conversationID: conversationID, messages: fetched)
                            }
                        } catch {
                            OttoLog.api.error("Unread reconcile DM fetch failed conversation=\(conversationID): \(String(describing: error))")
                        }
                    }
                }
            }
        }

        publishChatUnreadFromStore()
    }

    private func applyAutoCheckInPreferenceFromUser(_ user: UserDTO) {
        currentUser = user
        TimeZoneSync.primeCacheFromServerTimeZone(user.timeZone)
        if !OttoDebugSettings.isInternalDebugToolsAllowed(phoneNumber: user.phoneNumber) {
            OttoDebugSettings.mapLocationOverlayEnabled = false
            OttoDebugSettings.routeBuilderPerfOverlayEnabled = false
            OttoDebugSettings.routeCheckpointMapOverlayEnabled = false
        }
        reconcileDemoDriveCarPickerState(phoneNumber: user.phoneNumber)
        autoEventCheckInEnabled = user.resolvedAutoEventCheckInEnabled
        UserDefaults.standard.set(autoEventCheckInEnabled, forKey: StorageKeys.autoEventCheckInEnabled)
        sharingSafetyDisclaimerAcknowledged = user.resolvedSharingSafetyDisclaimerAcknowledged
            || UserDefaults.standard.bool(forKey: StorageKeys.sharingSafetyDisclaimerAcknowledged)
        UserDefaults.standard.set(
            sharingSafetyDisclaimerAcknowledged,
            forKey: StorageKeys.sharingSafetyDisclaimerAcknowledged
        )
        blockedUserIDs = Set(user.resolvedBlockedUserIds)
        showPublicGoingEventsOnProfile = user.resolvedShowPublicGoingEventsOnProfile
        UserDefaults.standard.set(showPublicGoingEventsOnProfile, forKey: StorageKeys.showPublicGoingEventsOnProfile)
        driveStatsVisibility = user.resolvedDriveStatsVisibility
        UserDefaults.standard.set(driveStatsVisibility.rawValue, forKey: StorageKeys.driveStatsVisibility)
    }

    func applyEventCheckInResult(_ result: EventCheckInResultDTO) {
        if let hydrated = result.event {
            upsertUpcomingEvent(hydrated)
            upsertSquadGoingEventForCheckIn(hydrated)
            if hydrated.currentUserCheckIn != nil {
                detailOpenedEventsForCheckIn.removeAll { $0.id == hydrated.id }
            }
            return
        }
        guard let checkIn = result.checkIn else { return }
        if let idx = upcomingEvents.firstIndex(where: { $0.id == checkIn.eventId }) {
            upcomingEvents[idx] = upcomingEvents[idx].withCurrentUserCheckIn(checkIn)
        } else if let idx = communityEvents.firstIndex(where: { $0.id == checkIn.eventId }) {
            communityEvents[idx] = communityEvents[idx].withCurrentUserCheckIn(checkIn)
        }
        if let idx = squadGoingEventsForCheckIn.firstIndex(where: { $0.id == checkIn.eventId }) {
            squadGoingEventsForCheckIn[idx] = squadGoingEventsForCheckIn[idx].withCurrentUserCheckIn(checkIn)
        }
        detailOpenedEventsForCheckIn.removeAll { $0.id == checkIn.eventId }
        foregroundAutoCheckInAttemptedEventIDs.insert(checkIn.eventId)
    }

    var eventsEligibleForAutoCheckIn: [EventDTO] {
        var eventsByID: [String: EventDTO] = [:]
        for event in mapDiscoveryEvents + squadGoingEventsForCheckIn + detailOpenedEventsForCheckIn {
            eventsByID[event.id] = event
        }
        return Array(eventsByID.values)
    }

    func eventForAutoCheckIn(eventId: String) -> EventDTO? {
        let trimmed = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return eventsEligibleForAutoCheckIn.first(where: { $0.id == trimmed })
    }

    private func upsertSquadGoingEventForCheckIn(_ event: EventDTO) {
        guard event.currentUserRsvp == "going", event.eventCheckInWindowEnd >= Date() else {
            squadGoingEventsForCheckIn.removeAll { $0.id == event.id }
            return
        }
        if let index = squadGoingEventsForCheckIn.firstIndex(where: { $0.id == event.id }) {
            squadGoingEventsForCheckIn[index] = event
        } else {
            squadGoingEventsForCheckIn.append(event)
        }
        squadGoingEventsForCheckIn.sort { $0.startsAt < $1.startsAt }
    }

    func watchEventDetailForCheckIn(_ event: EventDTO, locationService: LocationService? = nil) {
        let shouldWatch =
            event.currentUserRsvp == "going"
            && event.currentUserCheckIn == nil
            && event.eventCheckInWindowEnd >= Date()
            && event.eventGeoCoordinate != nil

        if shouldWatch {
            if let index = detailOpenedEventsForCheckIn.firstIndex(where: { $0.id == event.id }) {
                detailOpenedEventsForCheckIn[index] = event
            } else {
                detailOpenedEventsForCheckIn.append(event)
            }
            detailOpenedEventsForCheckIn.sort { $0.startsAt < $1.startsAt }
        } else {
            detailOpenedEventsForCheckIn.removeAll { $0.id == event.id }
        }

        logAutoCheckInEligibility(event: event, source: "detail_open")
        if let locationService {
            refreshEventCheckInMonitoring(locationService: locationService)
        }
    }

    func refreshSquadGoingEventsForCheckIn() async {
        let circleIDs = circles.map(\.id).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        guard !circleIDs.isEmpty else {
            squadGoingEventsForCheckIn = []
            return
        }
        let now = Date()
        var eventsByID: [String: EventDTO] = [:]
        for circleID in circleIDs {
            do {
                let events = try await APIClient.shared.fetchEvents(
                    scope: "all",
                    limit: 100,
                    visibility: "official",
                    circleId: circleID
                )
                for event in events where event.currentUserRsvp == "going" && event.eventCheckInWindowEnd >= now {
                    eventsByID[event.id] = event
                }
            } catch {
                continue
            }
        }
        squadGoingEventsForCheckIn = eventsByID.values.sorted { $0.startsAt < $1.startsAt }
    }

    func refreshAutoCheckInCandidates() async {
        guard isAuthenticated else { return }
        await refreshUpcomingEvents()
        await refreshCommunityEvents()
        await refreshSquadGoingEventsForCheckIn()
    }

    func refreshEventCheckInMonitoring(locationService: LocationService) {
        guard isAuthenticated else {
            locationService.clearEventCheckInRegions()
            OttoLog.app.info("auto_checkin_watchlist skipped reason=not_authenticated")
            return
        }
        guard autoEventCheckInEnabled else {
            locationService.clearEventCheckInRegions()
            OttoLog.app.info("auto_checkin_watchlist skipped reason=pref_off")
            return
        }
        let now = Date()
        let horizon = now.addingTimeInterval(Self.eventCheckInMonitoringHorizonSeconds)
        let allCandidates = eventsEligibleForAutoCheckIn
        allCandidates.forEach { logAutoCheckInEligibility(event: $0, source: "watchlist_refresh") }
        let eligible = allCandidates
            .filter { event in
                guard event.currentUserRsvp == "going" else { return false }
                guard event.currentUserCheckIn == nil else { return false }
                guard event.eventGeoCoordinate != nil else { return false }
                guard event.startsAt <= horizon else { return false }
                guard event.eventCheckInWindowEnd > now else { return false }
                return true
            }
            .sorted { lhs, rhs in
                let lActive = lhs.isInEventCheckInWindow
                let rActive = rhs.isInEventCheckInWindow
                if lActive != rActive { return lActive && !rActive }
                return lhs.startsAt < rhs.startsAt
            }
        let monitoredEvents = Array(eligible.prefix(Self.maxMonitoredEventCheckInRegions))
        let droppedEvents = Array(eligible.dropFirst(Self.maxMonitoredEventCheckInRegions))
        let regions: [CLCircularRegion] = monitoredEvents.compactMap { event in
            guard let coord = event.eventGeoCoordinate else { return nil }
            let region = CLCircularRegion(
                center: coord,
                radius: Self.eventCheckInRadiusMeters,
                identifier: "otto.event.\(event.id)"
            )
            region.notifyOnEntry = true
            region.notifyOnExit = false
            return region
        }
        locationService.replaceMonitoredEventCheckInRegions(regions)
        OttoAnalytics.logAutoCheckInGeofenceRegistered(regionCount: regions.count)
        let snapshot = locationService.makeDiagnosticsSnapshot()
        OttoLog.app.info(
            "auto_checkin_watchlist candidates=\(allCandidates.count) eligible=\(eligible.count) monitored=\(monitoredEvents.count) dropped=\(droppedEvents.count) auth=\(OttoLog.describeAuth(snapshot.authorizationStatus)) gpsRunning=\(snapshot.gpsRunning)"
        )
        if !droppedEvents.isEmpty {
            OttoLog.app.info(
                "auto_checkin_watchlist dropped_due_to_region_cap ids=\(droppedEvents.map(\.id).joined(separator: ","))"
            )
        }
    }

    func handleEventCheckInRegionEntered(eventId: String, locationService: LocationService) async {
        logAutoCheckInLocationState(locationService: locationService, trigger: "geofence")
        OttoLog.app.info("auto_checkin_trigger trigger=geofence eventId=\(eventId)")
        OttoAnalytics.logAutoCheckInGeofenceEntered(eventID: eventId)
        await attemptAutomaticEventCheckIn(
            eventId: eventId,
            locationService: locationService,
            trigger: "geofence"
        )
    }

    func attemptForegroundAutoCheckInIfNeeded(locationService: LocationService) async {
        guard isAuthenticated else {
            OttoLog.app.info("auto_checkin_trigger trigger=foreground skipped reason=not_authenticated")
            return
        }
        guard autoEventCheckInEnabled else {
            OttoLog.app.info("auto_checkin_trigger trigger=foreground skipped reason=pref_off")
            return
        }
        logAutoCheckInLocationState(locationService: locationService, trigger: "foreground")
        guard let loc = locationService.latestSample ?? locationService.lastLocation else {
            OttoLog.app.info("auto_checkin_trigger trigger=foreground skipped reason=no_location")
            return
        }
        let candidates = eventsEligibleForAutoCheckIn.compactMap { event -> EventDTO? in
            logAutoCheckInEligibility(event: event, source: "foreground")
            guard event.currentUserRsvp == "going" else { return nil }
            guard event.currentUserCheckIn == nil else { return nil }
            guard event.isInEventCheckInWindow else { return nil }
            guard let coord = event.eventGeoCoordinate else { return nil }
            guard !foregroundAutoCheckInAttemptedEventIDs.contains(event.id) else { return nil }
            let distance = Self.haversineDistanceMeters(
                lat1: loc.coordinate.latitude,
                lon1: loc.coordinate.longitude,
                lat2: coord.latitude,
                lon2: coord.longitude
            )
            logAutoCheckInDistance(eventId: event.id, distance: distance, trigger: "foreground", location: loc)
            guard distance <= Double(Self.eventCheckInRadiusMeters) else { return nil }
            return event
        }
        OttoLog.app.info("auto_checkin_trigger trigger=foreground candidates=\(candidates.count)")
        for event in candidates {
            await attemptAutomaticEventCheckIn(
                eventId: event.id,
                locationService: locationService,
                trigger: "foreground",
                preferredLocation: loc
            )
        }
    }

    private func attemptAutomaticEventCheckIn(
        eventId: String,
        locationService: LocationService,
        trigger: String,
        preferredLocation: CLLocation? = nil
    ) async {
        guard isAuthenticated, autoEventCheckInEnabled else {
            logAutoCheckInSkipped(eventId: eventId, reason: "pref_off", trigger: trigger)
            return
        }
        guard let event = eventForAutoCheckIn(eventId: eventId) else {
            logAutoCheckInSkipped(eventId: eventId, reason: "not_in_list", trigger: trigger)
            return
        }
        guard event.currentUserRsvp == "going" else {
            logAutoCheckInSkipped(eventId: eventId, reason: "not_going", trigger: trigger)
            return
        }
        guard event.currentUserCheckIn == nil else {
            logAutoCheckInSkipped(eventId: eventId, reason: "already_checked_in", trigger: trigger)
            return
        }
        guard event.isInEventCheckInWindow else {
            logAutoCheckInSkipped(eventId: eventId, reason: "outside_window", trigger: trigger)
            return
        }
        guard let eventCoord = event.eventGeoCoordinate else {
            logAutoCheckInSkipped(eventId: eventId, reason: "no_venue_coords", trigger: trigger)
            return
        }
        let loc: CLLocation?
        if let preferredLocation {
            loc = preferredLocation
        } else {
            loc = await locationService.resolveLocationForEventCheckIn()
        }
        guard let loc else {
            logAutoCheckInSkipped(eventId: eventId, reason: "no_location", trigger: trigger)
            return
        }
        let distance = Self.haversineDistanceMeters(
            lat1: loc.coordinate.latitude,
            lon1: loc.coordinate.longitude,
            lat2: eventCoord.latitude,
            lon2: eventCoord.longitude
        )
        logAutoCheckInDistance(eventId: eventId, distance: distance, trigger: trigger, location: loc)
        guard distance <= Double(Self.eventCheckInRadiusMeters) else {
            logAutoCheckInSkipped(eventId: eventId, reason: "outside_radius", trigger: trigger)
            return
        }
        if trigger == "foreground" {
            foregroundAutoCheckInAttemptedEventIDs.insert(event.id)
        }
        do {
            let result = try await APIClient.shared.postEventCheckIn(
                eventId: eventId,
                method: "automatic",
                latitude: loc.coordinate.latitude,
                longitude: loc.coordinate.longitude,
                distanceMeters: distance
            )
            applyEventCheckInResult(result)
            if result.checkedIn, !result.alreadyCheckedIn {
                OttoAnalytics.logAutoCheckInSuccess(eventID: eventId, trigger: trigger)
                presentUserToast(text: "Checked in to \(event.name)", systemImage: "mappin.circle.fill")
            }
            OttoLog.app.info(
                "auto_checkin_api_result eventId=\(eventId) trigger=\(trigger) checkedIn=\(result.checkedIn) alreadyCheckedIn=\(result.alreadyCheckedIn)"
            )
            refreshEventCheckInMonitoring(locationService: locationService)
        } catch {
            logAutoCheckInSkipped(eventId: eventId, reason: "api_error", trigger: trigger)
            OttoLog.app.error("Automatic event check-in failed trigger=\(trigger): \(String(describing: error))")
        }
    }

    private func logAutoCheckInSkipped(eventId: String, reason: String, trigger: String) {
        OttoAnalytics.logAutoCheckInSkipped(eventID: eventId, reason: reason, trigger: trigger)
        OttoLog.app.info("Auto check-in skipped eventId=\(eventId) reason=\(reason) trigger=\(trigger)")
    }

    private func logAutoCheckInEligibility(event: EventDTO, source: String) {
        let reason: String = {
            if event.currentUserRsvp != "going" { return "not_going" }
            if event.currentUserCheckIn != nil { return "already_checked_in" }
            if event.eventGeoCoordinate == nil { return "no_venue_coords" }
            if !event.isInEventCheckInWindow { return "outside_window" }
            return "eligible"
        }()
        OttoLog.app.info(
            "auto_checkin_eligibility source=\(source) eventId=\(event.id) result=\(reason) autoEnabled=\(self.autoEventCheckInEnabled) rsvp=\(event.currentUserRsvp ?? "nil") checkedIn=\(event.currentUserCheckIn != nil) hasCoords=\(event.eventGeoCoordinate != nil) startsAt=\(event.startsAt.ISO8601Format()) windowEnd=\(event.eventCheckInWindowEnd.ISO8601Format())"
        )
    }

    private func logAutoCheckInLocationState(locationService: LocationService, trigger: String) {
        let snapshot = locationService.makeDiagnosticsSnapshot()
        let latestAge = snapshot.latestSample.map { Date().timeIntervalSince($0.timestamp) } ?? -1
        OttoLog.app.info(
            "auto_checkin_location_state trigger=\(trigger) auth=\(OttoLog.describeAuth(snapshot.authorizationStatus)) gpsRunning=\(snapshot.gpsRunning) liveDisplay=\(snapshot.liveDisplayEnabled) updates=\(snapshot.locationUpdateCount) latestFixAgeSeconds=\(latestAge)"
        )
    }

    private func logAutoCheckInDistance(eventId: String, distance: Double, trigger: String, location: CLLocation) {
        let fixAge = Date().timeIntervalSince(location.timestamp)
        OttoLog.app.info(
            "auto_checkin_distance eventId=\(eventId) trigger=\(trigger) distanceMeters=\(distance) radiusMeters=\(Self.eventCheckInRadiusMeters) fixAgeSeconds=\(fixAge) hAcc=\(location.horizontalAccuracy)"
        )
    }

    func postManualEventCheckIn(eventId: String, latitude: Double?, longitude: Double?) async {
        guard !currentUserID.isEmpty else { return }
        do {
            let result = try await APIClient.shared.postEventCheckIn(
                eventId: eventId,
                method: "manual",
                latitude: latitude,
                longitude: longitude,
                distanceMeters: nil
            )
            applyEventCheckInResult(result)
            if result.checkedIn, !result.alreadyCheckedIn {
                OttoAnalytics.logEventCheckIn(eventID: eventId)
                presentUserToast(text: "You’re checked in!", systemImage: "checkmark.circle.fill")
            }
        } catch {
            errorMessage = "Couldn’t check in. Try again."
        }
    }

    private static func haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let earthRadiusMeters = 6_371_000.0
        let r1 = lat1 * Double.pi / 180
        let r2 = lat2 * Double.pi / 180
        let dLat = (lat2 - lat1) * Double.pi / 180
        let dLon = (lon2 - lon1) * Double.pi / 180
        let a =
            sin(dLat / 2) * sin(dLat / 2) +
            cos(r1) * cos(r2) * sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
    func isCircleShared(_ circleID: String) -> Bool {
        sharingCircleIDs.contains(circleID)
    }

    func toggleSharing(for circleID: String) {
        if sharingCircleIDs.contains(circleID) {
            sharingCircleIDs.remove(circleID)
        } else {
            sharingCircleIDs.insert(circleID)
        }
        sharingAudience = .circles
        if isSharingEnabled, sharingCircleIDs.isEmpty {
            stopSharingSession()
            return
        }
        persistSharingState()
    }

    func setSharingAudience(_ audience: SharingAudience) {
        sharingAudience = audience == .public ? .onlyMe : audience
        switch audience {
        case .circles:
            if sharingCircleIDs.isEmpty, !selectedCircleID.isEmpty {
                sharingCircleIDs.insert(selectedCircleID)
            }
            isSharingEnabled = !sharingCircleIDs.isEmpty
        case .public, .onlyMe:
            sharingCircleIDs.removeAll()
            isSharingEnabled = false
        }
        persistSharingState()
    }

    func startSharingSession(
        circleIDs: Set<String>,
        durationSeconds: TimeInterval,
        mode: SharingSessionMode
    ) -> Bool {
        let targetCircleIDs = circleIDs.isEmpty && !selectedCircleID.isEmpty ? Set([selectedCircleID]) : circleIDs
        guard !targetCircleIDs.isEmpty else {
            errorMessage = "Choose at least one squad to share with."
            return false
        }
        sharingTiedToActiveDrive = false
        sharingAudience = .circles
        sharingCircleIDs = targetCircleIDs
        sharingDurationSeconds = max(durationSeconds, 60)
        sharingSessionMode = mode
        sharingSessionStartedAt = Date()
        drivingOnlyNotDrivingInactiveEmitted = false
        isSharingEnabled = true
        lastSelfDrivingOnlyPausedForToast = nil
        suppressInitialDrivingOnlyPauseToast = (mode == .drivingOnly)
        persistSharingState()
        OttoAnalytics.logLocationSharingEnabled()
        switch mode {
        case .shareNow:
            showToast("You're sharing live", icon: "paperplane.fill")
        case .drivingOnly:
            showToast("Sharing on — live when driving", icon: "car.side.fill")
        }
        if sharingSaveDriveEnabled, sharingSessionMode != .drivingOnly {
            Task { await startDriveIfNeeded(location: nil) }
        }
        return true
    }

    @discardableResult
    func startSharingForDriveStart(circleIDs: Set<String>) -> Bool {
        let targetCircleIDs = circleIDs
        guard !targetCircleIDs.isEmpty else {
            errorMessage = "Choose at least one squad to share with."
            return false
        }
        sharingAudience = .circles
        sharingCircleIDs = targetCircleIDs
        sharingSessionMode = .shareNow
        sharingSessionStartedAt = Date()
        sharingTiedToActiveDrive = true
        drivingOnlyNotDrivingInactiveEmitted = false
        isSharingEnabled = true
        lastSelfDrivingOnlyPausedForToast = nil
        suppressInitialDrivingOnlyPauseToast = false
        persistSharingState()
        OttoAnalytics.logLocationSharingEnabled()
        return true
    }

    func setSharingSaveDriveEnabled(_ enabled: Bool) {
        let wasEnabled = sharingSaveDriveEnabled
        sharingSaveDriveEnabled = enabled
        persistSharingState()
        guard isSharingEnabled else { return }
        if enabled, !wasEnabled, sharingSessionMode != .drivingOnly {
            Task { await startDriveIfNeeded(location: nil) }
        } else if !enabled, wasEnabled {
            Task { await stopActiveDrive(location: nil) }
        }
    }

    func extendSharingSession(durationSeconds: TimeInterval? = nil) {
        guard isSharingEnabled else { return }
        if let durationSeconds {
            sharingDurationSeconds = max(durationSeconds, 60)
        }
        sharingSessionStartedAt = Date()
        drivingOnlyNotDrivingInactiveEmitted = false
        persistSharingState()
    }

    func updateSharingSessionMode(_ mode: SharingSessionMode) {
        sharingSessionMode = mode
        drivingOnlyNotDrivingInactiveEmitted = false
        if mode != .drivingOnly {
            isDrivingOnlyBroadcastPaused = false
            lastSelfDrivingOnlyPausedForToast = nil
        } else {
            lastSelfDrivingOnlyPausedForToast = nil
            suppressInitialDrivingOnlyPauseToast = false
        }
        persistSharingState()
    }

    /// Clears live sharing started from Quick/Route drive dock when the drive session ends.
    func stopSharingForDriveSessionEnd() async {
        guard isSharingEnabled else { return }
        let stoppedCircles = Array(sharingCircleIDs)
        isSharingEnabled = false
        sharingTiedToActiveDrive = false
        sharingSessionStartedAt = nil
        persistSharingState()
        await markPresenceInactive(circleIDs: stoppedCircles)
    }

    func stopSharingSession(disabledReason: String = "user") {
        let stoppedCircleIDs = Array(sharingCircleIDs)
        let wasSharing = isSharingEnabled
        isSharingEnabled = false
        sharingTiedToActiveDrive = false
        sharingSessionStartedAt = nil
        drivingOnlyNotDrivingInactiveEmitted = false
        isDrivingOnlyBroadcastPaused = false
        lastSelfDrivingOnlyPausedForToast = nil
        suppressInitialDrivingOnlyPauseToast = false
        persistSharingState()
        if wasSharing {
            OttoAnalytics.logLocationSharingDisabled(reason: disabledReason)
        }
        showToast("Sharing stopped", icon: "stop.circle.fill")
        Task {
            await markPresenceInactive(circleIDs: stoppedCircleIDs)
            let shouldArchive = sharingSaveDriveEnabled || activeDriveSession?.isRecording == true
            var archiveInput: PendingDriveArchiveInput?
            if shouldArchive, activeDriveID != nil {
                let session = activeDriveSession
                let trail = session?.metrics.recordedPath ?? activeDrivePathTrail
                let distance = max(session?.metrics.distanceMeters ?? 0, activeDriveDistanceMeters)
                let trailMaxSpeed = trail.map(\.speedMph).max() ?? 0
                let maxSpeed = max(session?.metrics.maxSpeedMph ?? 0, activeDriveMaxSpeedMph, trailMaxSpeed)
                let kind = session?.kind ?? .live
                let startedAt = session?.startedAt ?? sharingSessionStartedAt ?? Date()
                let elapsed = Date().timeIntervalSince(startedAt)
                let avgSpeed = DriveAverageSpeed.resolvedMph(
                    storedAvg: session?.metrics.avgSpeedMph ?? 0,
                    distanceMeters: distance,
                    durationSeconds: elapsed
                )
                archiveInput = pendingArchiveInput(
                    failurePhase: "end",
                    kind: kind,
                    title: driveRecordingTitle(for: kind),
                    startedAt: startedAt,
                    distanceMeters: distance,
                    maxSpeedMph: maxSpeed,
                    avgSpeedMph: avgSpeed,
                    backendDriveId: activeDriveID,
                    routeId: session?.routeId,
                    routeName: session?.routeName,
                    pathSamples: trail
                )
            }
            await stopActiveDrive(location: nil, archiveOnFailure: archiveInput)
        }
    }

    func removeSquadFromSharingSession(_ circleID: String) {
        sharingCircleIDs.remove(circleID)
        Task { await markPresenceInactive(circleIDs: [circleID]) }
        if sharingCircleIDs.isEmpty {
            stopSharingSession()
        } else {
            persistSharingState()
        }
    }

    func selectSharingCar(_ carID: String) {
        guard showsDriveCarPicker else { return }
        selectedSharingCarID = carID
        persistSharingState()
    }

    private func reconcileDemoDriveCarPickerState(phoneNumber: String?) {
        guard OttoPhone.isDemoBypassPhone(phoneNumber) else { return }
        guard !selectedSharingCarID.isEmpty else { return }
        selectedSharingCarID = ""
        persistSharingState()
    }

    func refreshCirclesAsync(priority: TaskPriority = .utility) {
        guard isAuthenticated else { return }
        Task(priority: priority) { await refreshCircles() }
    }

    func refreshGarageAsync(priority: TaskPriority = .utility) {
        Task(priority: priority) { await refreshGarage() }
    }

    func refreshCircles() async {
        if refreshCirclesBusy {
            OttoLog.app.info("refreshCircles() coalesced — waiting for in-flight load")
            await withCheckedContinuation { refreshCirclesWaiters.append($0) }
            return
        }
        refreshCirclesBusy = true
        defer {
            refreshCirclesBusy = false
            let waiters = refreshCirclesWaiters
            refreshCirclesWaiters.removeAll()
            waiters.forEach { $0.resume() }
        }

        OttoLog.app.info(
            "refreshCircles() start currentUserID=\(self.currentUserID) isEmptyQuery=\(self.currentUserID.isEmpty)"
        )
        do {
            let currentUserIDSnapshot = currentUserID
            let seedUsers = allUsers
            let result = try await Task.detached(priority: .utility) {
                try await Self.loadCircles(
                    currentUserID: currentUserIDSnapshot,
                    seedUsers: seedUsers
                )
            }.value

            allUsers = result.users
            await applyCirclesFromAPI(result.mapped)
            await refreshContacts()
            circlesFetchFailed = false
            OttoLog.app.info(
                "refreshCircles() applied circles.count=\(self.circles.count) currentUserID=\(self.currentUserID) selectedCircleID=\(self.selectedCircleID)"
            )
        } catch {
            OttoLog.app.error("refreshCircles() failed: \(String(describing: error))")
            circlesFetchFailed = true
            circles = []
            selectedCircleID = ""
            sharingCircleIDs = []
            if isSharingEnabled {
                isSharingEnabled = false
                await stopActiveDrive(location: nil)
            }
            persistSharingState()
        }
    }

    private static func loadCircles(currentUserID: String, seedUsers: [UserDTO]) async throws -> CirclesLoadResult {
        let circleDTOs = try await APIClient.shared.fetchCircles(
            userId: currentUserID.isEmpty ? nil : currentUserID
        )
        let users = (try? await APIClient.shared.fetchUsers()) ?? seedUsers
        var usersByID = Dictionary(uniqueKeysWithValues: users.map { ($0.id, $0) })
        let userIDs = Set(circleDTOs.flatMap { dto in
            dto.members.map(\.userId) + [dto.ownerId]
        })
        let missingUserIDs = userIDs.filter { usersByID[$0] == nil }

        await withTaskGroup(of: (String, UserDTO?).self) { group in
            for userID in missingUserIDs {
                group.addTask {
                    let user = try? await APIClient.shared.fetchUser(id: userID)
                    return (userID, user)
                }
            }
            for await (userID, user) in group {
                if let user { usersByID[userID] = user }
            }
        }

        let mapped = circleDTOs.map { dto in
            let members = dto.members.map { member in
                Self.mapFriend(user: usersByID[member.userId], userID: member.userId, role: member.role)
            }
            return DriveCircle(
                id: dto.id,
                name: dto.name,
                subtitle: Self.subtitleForCircle(dto, owner: usersByID[dto.ownerId]),
                icon: Self.iconForCircle(name: dto.name),
                accentColor: MapAccentPalette.color(fromStableSeed: dto.id),
                ownerId: dto.ownerId,
                photoUrl: dto.photoUrl,
                members: members
            )
        }

        return CirclesLoadResult(mapped: mapped, users: Array(usersByID.values))
    }

    private static func subtitleForCircle(_ circle: CircleDTO, owner: UserDTO?) -> String {
        let trimmedDescription = circle.description?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let trimmedDescription,
           !trimmedDescription.isEmpty,
           trimmedDescription != "Created from iOS app" {
            return trimmedDescription
        }

        if let owner {
            return "Created by \(owner.displayName)"
        }

        return "Squad"
    }

    /// Replaces `circles` from the server (including an empty list). Never leaves stale demo data.
    private func applyCirclesFromAPI(_ mapped: [DriveCircle]) async {
        let previousShared = sharingCircleIDs
        circles = mapped

        if mapped.isEmpty {
            selectedCircleID = ""
            sharingCircleIDs = []
            if isSharingEnabled {
                isSharingEnabled = false
                await stopActiveDrive(location: nil)
            }
            persistSharingState()
            return
        }

        if !mapped.contains(where: { $0.id == selectedCircleID }) {
            selectedCircleID = mapped[0].id
        }
        sharingCircleIDs = Set(mapped.map(\.id).filter { previousShared.contains($0) })
        lastKnownActiveSharersByCircleID = Dictionary(
            uniqueKeysWithValues: mapped.map { circle in
                (
                    circle.id,
                    Set(circle.members.filter { $0.isActive && $0.id != currentUserID }.map(\.id))
                )
            }
        )
        if isSharingEnabled && sharingAudience == .circles && sharingCircleIDs.isEmpty {
            sharingCircleIDs.insert(selectedCircleID)
        }
        persistSharingState()

        if !currentUserID.isEmpty {
            #if !targetEnvironment(simulator)
            await refreshGarage()
            #endif
        }

        if isAuthenticated {
            subscribeChatRealtimeToCurrentCircles()
        }
        cacheShareExtensionSquads()
    }

    func refreshGarage() async {
        guard !currentUserID.isEmpty else {
            persistSharingState()
            return
        }
        do {
            let cars = try await APIClient.shared.fetchGarageCars(userId: currentUserID)
            garageCars = cars.map(garageCar(from:))

            if !garageCars.contains(where: { $0.id == selectedSharingCarID }) {
                selectedSharingCarID = garageCars.first(where: \.isPrimary)?.id ?? garageCars.first?.id ?? ""
            }
            if garageCars.isEmpty { selectedSharingCarID = "" }
            persistSharingState()
        } catch {
            // Keep previously cached garage data so add/select remains functional offline.
            persistSharingState()
        }
    }

    func refreshRecentDrives() async {
        guard !currentUserID.isEmpty else { return }
        do {
            recentDrives = try await APIClient.shared.fetchUserDrives(userId: currentUserID)
        } catch {
            // Keep the cached list if a refetch fails so profile doesn't flash empty.
        }
    }

    /// Keeps profile and other drive lists in sync after summary edits without a full refetch.
    @MainActor
    func applyDriveUpdate(_ updated: DriveDTO) {
        guard !updated.id.isEmpty else { return }
        if let index = recentDrives.firstIndex(where: { $0.id == updated.id }) {
            recentDrives = recentDrives.enumerated().map { offset, drive in
                offset == index ? updated : drive
            }
        }
    }

    @MainActor
    func removeDriveFromRecent(id driveID: String) {
        guard !driveID.isEmpty else { return }
        recentDrives = recentDrives.filter { $0.id != driveID }
    }

    func refreshUpcomingEvents() async {
        do {
            let now = Date()
            let fetched = try await APIClient.shared.fetchEvents(
                scope: "all",
                limit: 100,
                visibility: "public",
                eventType: "featured"
            )
                .filter { $0.eventCheckInWindowEnd >= now }
            var mergedByID: [String: EventDTO] = [:]
            for event in fetched {
                mergedByID[event.id] = event
            }
            let preservedGoing = (upcomingEvents + squadGoingEventsForCheckIn).filter { event in
                event.currentUserRsvp == "going"
                    && event.eventCheckInWindowEnd >= now
                    && event.currentUserCheckIn == nil
                    && mergedByID[event.id] == nil
            }
            for event in preservedGoing {
                mergedByID[event.id] = event
            }
            upcomingEvents = mergedByID.values.sorted { lhs, rhs in
                let lhsStarted = lhs.startsAt <= now
                let rhsStarted = rhs.startsAt <= now
                if lhsStarted != rhsStarted { return lhsStarted }
                if lhsStarted && rhsStarted { return lhs.startsAt > rhs.startsAt }
                return lhs.startsAt < rhs.startsAt
            }
            featuredEventsFetchFailed = false
        } catch {
            if upcomingEvents.isEmpty {
                featuredEventsFetchFailed = true
            }
        }
    }

    func refreshCommunityEvents() async {
        do {
            let now = Date()
            let events = try await APIClient.shared.fetchEvents(
                scope: "all",
                limit: 100,
                visibility: "public",
                eventType: "community"
            )
                .filter { $0.eventCheckInWindowEnd >= now }
                .sorted { lhs, rhs in
                    let lhsStarted = lhs.startsAt <= now
                    let rhsStarted = rhs.startsAt <= now
                    if lhsStarted != rhsStarted { return lhsStarted }
                    if lhsStarted && rhsStarted { return lhs.startsAt > rhs.startsAt }
                    return lhs.startsAt < rhs.startsAt
                }
            communityEvents = events
            communityEventsFetchFailed = false
        } catch {
            if communityEvents.isEmpty {
                communityEventsFetchFailed = true
            }
        }
    }

    var mapDiscoveryEvents: [EventDTO] {
        var eventsByID: [String: EventDTO] = [:]
        for event in upcomingEvents + communityEvents {
            eventsByID[event.id] = event
        }
        return Array(eventsByID.values)
    }

    /// Resolves `eventId` for chat cards: squad-scoped list, then global upcoming, then hydration cache.
    func resolvedEventForChatAttachment(eventId: String, squadEvents: [EventDTO]) -> EventDTO? {
        let id = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return nil }
        if let e = squadEvents.first(where: { $0.id == id }) { return e }
        if let e = upcomingEvents.first(where: { $0.id == id }) { return e }
        if let e = communityEvents.first(where: { $0.id == id }) { return e }
        return chatAttachmentHydratedEventsById[id]
    }

    /// Loads full events for attachment rows not covered by squad/upcoming lists (e.g. past events).
    func prefetchChatAttachmentEventsIfNeeded(eventIds: [String], squadEvents: [EventDTO]) {
        let trimmed = Set(eventIds.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })
        guard !trimmed.isEmpty else { return }
        Task { await hydrateChatAttachmentEventsMissing(from: trimmed, squadEvents: squadEvents) }
    }

    private func hydrateChatAttachmentEventsMissing(from ids: Set<String>, squadEvents: [EventDTO]) async {
        for rawId in ids {
            if resolvedEventForChatAttachment(eventId: rawId, squadEvents: squadEvents) != nil { continue }
            if chatAttachmentHydrationInFlight.contains(rawId) { continue }
            chatAttachmentHydrationInFlight.insert(rawId)
            defer { chatAttachmentHydrationInFlight.remove(rawId) }
            do {
                let fetched = try await APIClient.shared.fetchEvent(eventRef: rawId)
                let key = fetched.id.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !key.isEmpty else { continue }
                var next = chatAttachmentHydratedEventsById
                next[key] = fetched
                chatAttachmentHydratedEventsById = next
            } catch {
                // Keep attachment stub; avoid noisy toasts for history scroll.
            }
        }
    }

    @discardableResult
    func setEventRsvp(eventID: String, status: String) async -> EventDTO? {
        do {
            let updated = try await APIClient.shared.updateEventRsvp(eventId: eventID, status: status)
            let eid = eventID.trimmingCharacters(in: .whitespacesAndNewlines)
            if let index = upcomingEvents.firstIndex(where: { $0.id == eid || $0.id == updated.id }) {
                upcomingEvents[index] = updated
            } else if let index = communityEvents.firstIndex(where: { $0.id == eid || $0.id == updated.id }) {
                communityEvents[index] = updated
            } else if updated.eventType == "community" {
                communityEvents.append(updated)
                communityEvents.sort { $0.startsAt < $1.startsAt }
            } else {
                upcomingEvents.append(updated)
                upcomingEvents.sort { $0.startsAt < $1.startsAt }
            }
            upsertSquadGoingEventForCheckIn(updated)
            let hid = updated.id.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !hid.isEmpty else { return updated }
            var next = chatAttachmentHydratedEventsById
            next.removeValue(forKey: eventID)
            if eid != hid { next.removeValue(forKey: eid) }
            next[hid] = updated
            chatAttachmentHydratedEventsById = next
            return updated
        } catch {
            errorMessage = "Could not update event RSVP."
            return nil
        }
    }

    func upsertUpcomingEvent(_ event: EventDTO) {
        let isCommunity = event.eventType == "community"
        if isCommunity {
            if let index = communityEvents.firstIndex(where: { $0.id == event.id }) {
                communityEvents[index] = event
            } else {
                communityEvents.append(event)
            }
            communityEvents.sort { $0.startsAt < $1.startsAt }
            return
        }
        if let index = upcomingEvents.firstIndex(where: { $0.id == event.id }) {
            upcomingEvents[index] = event
        } else {
            upcomingEvents.append(event)
        }
        upcomingEvents.sort { $0.startsAt < $1.startsAt }
    }

    func addGarageCar(
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        yearText: String,
        color: String,
        logoSlug: String?,
        imageData: Data? = nil
    ) async {
        let trimmedNickname = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedMake = make.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedMakeId = makeId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedColor = color.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedLogoSlug = logoSlug?.trimmingCharacters(in: .whitespacesAndNewlines)
        let parsedYear = Int(yearText.trimmingCharacters(in: .whitespacesAndNewlines))

        guard !trimmedMake.isEmpty, !trimmedModel.isEmpty else {
            errorMessage = "Make and model are required."
            return
        }

        let localCar = GarageCar(
            id: UUID().uuidString,
            nickname: trimmedNickname,
            make: trimmedMake,
            makeId: trimmedMakeId?.isEmpty == false ? trimmedMakeId : nil,
            model: trimmedModel,
            year: parsedYear,
            color: trimmedColor.isEmpty ? nil : trimmedColor,
            logoSlug: trimmedLogoSlug?.isEmpty == false ? trimmedLogoSlug : nil,
            isPrimary: garageCars.isEmpty,
            sortOrder: nil,
            photoUrl: nil
        )
        garageCars.insert(localCar, at: 0)
        if selectedSharingCarID.isEmpty {
            selectedSharingCarID = localCar.id
        }
        persistSharingState()

        guard !currentUserID.isEmpty else {
            errorMessage = "Saved locally. Backend user not loaded yet."
            return
        }

        do {
            let created = try await APIClient.shared.createGarageCar(
                userId: currentUserID,
                nickname: trimmedNickname,
                make: trimmedMake,
                makeId: trimmedMakeId?.isEmpty == false ? trimmedMakeId : nil,
                model: trimmedModel,
                year: parsedYear,
                color: trimmedColor,
                logoSlug: trimmedLogoSlug?.isEmpty == false ? trimmedLogoSlug : nil
            )
            if let imageData {
                do {
                    _ = try await APIClient.shared.uploadGarageCarPhoto(
                        userId: currentUserID,
                        carId: created.id,
                        imageData: imageData
                    )
                } catch {
                    errorMessage = "Car saved, but photo upload failed."
                }
            }
            OttoAnalytics.logGarageCarAdded(hasPhoto: imageData != nil)
            await refreshGarage()
        } catch {
            errorMessage = "Saved locally. Backend sync failed."
        }
    }

    private func garageCar(from dto: GarageCarDTO) -> GarageCar {
        GarageCar(
            id: dto.id,
            nickname: dto.nickname ?? "",
            make: dto.make,
            makeId: dto.makeId,
            model: dto.model,
            year: dto.year,
            color: dto.color,
            logoSlug: dto.logoSlug,
            isPrimary: dto.isPrimary,
            sortOrder: dto.sortOrder,
            photoUrl: dto.photo?.url
        )
    }

    func reorderGarageCars(from source: IndexSet, to destination: Int) async {
        guard !currentUserID.isEmpty else { return }
        var next = garageCars
        next.move(fromOffsets: source, toOffset: destination)
        let ids = next.map(\.id)
        garageCars = next
        if let encodedGarage = try? JSONEncoder().encode(garageCars) {
            UserDefaults.standard.set(encodedGarage, forKey: StorageKeys.garageCars)
        }
        do {
            try await APIClient.shared.reorderGarageCars(userId: currentUserID, orderedCarIds: ids)
        } catch {
            errorMessage = "Couldn’t save garage order."
            await refreshGarage()
        }
    }

    func updateGarageCar(
        carID: String,
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        yearText: String,
        color: String,
        logoSlug: String?,
        isPrimary: Bool,
        imageData: Data? = nil
    ) async {
        let trimmedNickname = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedMake = make.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedMakeId = makeId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedColor = color.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedLogoSlug = logoSlug?.trimmingCharacters(in: .whitespacesAndNewlines)
        let parsedYear = Int(yearText.trimmingCharacters(in: .whitespacesAndNewlines))

        guard !trimmedMake.isEmpty, !trimmedModel.isEmpty else {
            errorMessage = "Make and model are required."
            return
        }
        guard !currentUserID.isEmpty else {
            errorMessage = "Current user is not loaded yet."
            return
        }

        do {
            _ = try await APIClient.shared.updateGarageCar(
                userId: currentUserID,
                carId: carID,
                nickname: trimmedNickname,
                make: trimmedMake,
                makeId: trimmedMakeId?.isEmpty == false ? trimmedMakeId : nil,
                model: trimmedModel,
                year: parsedYear,
                color: trimmedColor,
                logoSlug: trimmedLogoSlug?.isEmpty == false ? trimmedLogoSlug : nil,
                isPrimary: isPrimary
            )
            if let imageData {
                do {
                    _ = try await APIClient.shared.uploadGarageCarPhoto(
                        userId: currentUserID,
                        carId: carID,
                        imageData: imageData
                    )
                } catch {
                    errorMessage = "Car updated, but photo upload failed."
                }
            }
            await refreshGarage()
        } catch {
            errorMessage = "Couldn’t update car."
        }
    }

    func removeGarageCar(_ carID: String) async {
        garageCars.removeAll { $0.id == carID }
        if selectedSharingCarID == carID {
            selectedSharingCarID = garageCars.first?.id ?? ""
            if selectedSharingCarID.isEmpty {
                isSharingEnabled = false
            }
        }
        persistSharingState()

        guard !currentUserID.isEmpty else { return }
        do {
            try await APIClient.shared.deleteGarageCar(userId: currentUserID, carId: carID)
            await refreshGarage()
            presentDeletedToast(for: "Car")
        } catch {
            errorMessage = "Removed locally. Backend delete failed."
        }
    }

    func availableUsersForCircle(_ circleID: String) -> [UserDTO] {
        guard let circle = circles.first(where: { $0.id == circleID }) else { return allUsers }
        let existing = Set(circle.members.map(\.id))
        return allUsers.filter { !existing.contains($0.id) }
    }

    func availableContactsForCircle(_ circleID: String) -> [UserDTO] {
        guard let circle = circles.first(where: { $0.id == circleID }) else { return contacts }
        let existing = Set(circle.members.map(\.id))
        return contacts
            .filter { !existing.contains($0.id) }
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    func addMember(to circleID: String, userID: String) async {
        do {
            try await APIClient.shared.addMemberToCircle(circleId: circleID, userId: userID)
            await refreshCircles()
            await refreshContacts()
            await refreshInvites(for: circleID)
        } catch {
            errorMessage = "Failed to add member to squad."
        }
    }

    func removeMember(from circleID: String, userID: String) async {
        do {
            try await APIClient.shared.removeMemberFromCircle(circleId: circleID, userId: userID)
            await refreshCircles()
        } catch {
            errorMessage = "Failed to remove member from squad."
        }
    }

    func setCircleMemberRole(circleID: String, userID: String, role: String) async {
        do {
            try await APIClient.shared.patchCircleMemberRole(circleId: circleID, userId: userID, role: role)
            await refreshCircles()
        } catch {
            errorMessage = "Couldn’t update squad member role."
        }
    }

    @discardableResult
    func renameSquad(circleID: String, name: String) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            errorMessage = "Name must be at least 2 characters."
            return false
        }
        do {
            _ = try await APIClient.shared.patchCircle(circleId: circleID, name: trimmed)
            await refreshCircles()
            return true
        } catch {
            errorMessage = "Couldn’t rename squad."
            return false
        }
    }

    enum SquadLeaveResult {
        case left
        case squadDeleted
        case ownershipTransferRequired
        case failed
    }

    func leaveSquad(circleID: String) async -> SquadLeaveResult {
        do {
            let res = try await APIClient.shared.leaveCircle(circleId: circleID)
            await refreshCircles()
            if res.deleted == true {
                return .squadDeleted
            }
            return .left
        } catch is OttoLeaveCircleOwnershipRequiredError {
            return .ownershipTransferRequired
        } catch {
            errorMessage = "Couldn’t leave squad."
            return .failed
        }
    }

    func refreshInvites(for circleID: String) async {
        do {
            let invites = try await APIClient.shared.fetchCircleInvites(circleId: circleID)
            pendingInvitesByCircleID[circleID] = invites
        } catch {
            pendingInvitesByCircleID[circleID] = []
        }
    }

    func refreshContacts() async {
        do {
            contacts = try await APIClient.shared.fetchContacts()
        } catch {
            contacts = []
        }
    }

    func createCircleInviteLink(circleID: String) async -> String? {
        do {
            let response = try await APIClient.shared.createCircleInviteLink(circleId: circleID)
            return response.url
        } catch {
            errorMessage = "Failed to generate invite link."
            return nil
        }
    }

    @discardableResult
    func inviteMemberByPhone(circleID: String, phoneNumber: String) async -> Bool {
        let trimmed = phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        guard !currentUserID.isEmpty else {
            errorMessage = "Current user is not loaded yet."
            return false
        }

        do {
            try await APIClient.shared.inviteByPhone(
                circleId: circleID,
                phoneNumber: trimmed
            )
            await refreshInvites(for: circleID)
            await refreshMyCircleInvites()
            return true
        } catch {
            errorMessage = "Failed to send phone invite."
            return false
        }
    }

    func refreshMyCircleInvites() async {
        do {
            let invites = try await Task.detached(priority: .utility) {
                try await APIClient.shared.fetchMyCircleInvites()
            }.value
            let inviteIDs = invites.map(\.id)
            if invites.count > lastKnownInviteCount, let newest = invites.first {
                let inviter = newest.invitedByUser?.displayName ?? "a member"
                showToast("Squad invite from \(inviter)", icon: "bell.badge.fill")
            }
            if inviteIDs != lastKnownInviteIDs {
                myCircleInvites = invites
                lastKnownInviteIDs = inviteIDs
            }
            lastKnownInviteCount = invites.count
        } catch {
            if !myCircleInvites.isEmpty {
                myCircleInvites = []
            }
            lastKnownInviteCount = 0
            lastKnownInviteIDs = []
        }
    }

    func respondToCircleInvite(inviteID: String, accept: Bool) async {
        do {
            try await APIClient.shared.respondToCircleInvite(
                inviteID: inviteID,
                action: accept ? "accept" : "decline"
            )
            await refreshMyCircleInvites()
            if accept {
                await refreshCircles()
                OttoAnalytics.logSquadJoined(source: "pending_invite")
            }
        } catch {
            errorMessage = "Could not respond to invite."
        }
    }

    @discardableResult
    func updateCurrentUserDisplayName(_ displayName: String) async -> Bool {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Please enter your name."
            return false
        }
        guard !currentUserID.isEmpty else {
            errorMessage = "User session not ready. Try signing in again."
            return false
        }

        do {
            try await APIClient.shared.updateUserDisplayName(userId: currentUserID, displayName: trimmed)
            await refreshCircles()
            return true
        } catch {
            errorMessage = "Could not save your name."
            return false
        }
    }

    func createCircleOnServer(named name: String) async {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }

        guard !currentUserID.isEmpty else {
            errorMessage = "Couldn’t create squad: load your account from the server first (squads or users API)."
            return
        }

        do {
            _ = try await APIClient.shared.createCircle(name: trimmedName, ownerId: currentUserID)
            OttoAnalytics.logSquadCreated()
            await refreshCircles()
        } catch {
            errorMessage = "Couldn’t create squad on the server."
        }
    }

    func refreshPresenceForSelectedCircle() async {
        guard let selectedCircleID = selectedCircle?.id else { return }
        await refreshPresence(for: selectedCircleID)
    }

    func refreshPresenceForAllCircles() async {
        for circle in circles {
            await refreshPresence(for: circle.id)
        }
    }

    func refreshPresence(for circleID: String, showsStartedSharingToast: Bool = false) async {
        guard !circleID.isEmpty else { return }
        guard let circle = circles.first(where: { $0.id == circleID }) else { return }
        let previousActive = lastKnownActiveSharersByCircleID[circle.id]

        do {
            let response = try await APIClient.shared.fetchPresence(circleId: circle.id)
            let presenceByUserID = Dictionary(
                response.members.map { ($0.userId, $0) },
                uniquingKeysWith: { _, new in new }
            )
            var updatedCircle = circle

            updatedCircle.members = circle.members.map { member in
                guard let presence = presenceByUserID[member.id] else {
                    return FriendLocation(
                        id: member.id,
                        name: member.name,
                        avatarName: member.avatarName,
                        avatarUrl: member.avatarUrl,
                        car: member.car,
                        clubRole: member.clubRole,
                        lastRun: member.lastRun,
                        coordinate: member.coordinate,
                        speedMph: 0,
                        isOnline: false,
                        isActive: false,
                        accentColor: member.accentColor,
                        movementMode: member.movementMode,
                        lastUpdatedAt: member.lastUpdatedAt,
                        lastPresenceInApp: nil
                    )
                }
                return friendLocation(member, applying: presence)
            }

            if let index = circles.firstIndex(where: { $0.id == updatedCircle.id }) {
                circles[index] = updatedCircle
            }
            let currentActive = Set(updatedCircle.members.filter { $0.isActive && $0.id != currentUserID }.map(\.id))
            lastKnownActiveSharersByCircleID[updatedCircle.id] = currentActive
            guard showsStartedSharingToast else { return }
            guard let previousActive else { return }

            let newlyActiveIDs = currentActive.subtracting(previousActive)
            let now = Date()
            let dedupedNewlyActiveIDs = newlyActiveIDs.filter { userID in
                guard let lastAt = lastSharingToastAtByUserID[userID] else { return true }
                return now.timeIntervalSince(lastAt) >= Self.sharingToastDedupWindow
            }
            if !dedupedNewlyActiveIDs.isEmpty {
                let newlyActiveNames = updatedCircle.members
                    .filter { dedupedNewlyActiveIDs.contains($0.id) }
                    .map(\.name)
                if let first = newlyActiveNames.first {
                    if newlyActiveNames.count == 1 {
                        showToast("\(first) started sharing", icon: "dot.radiowaves.up.forward")
                    } else {
                        showToast("\(first) and \(newlyActiveNames.count - 1) others started sharing", icon: "dot.radiowaves.up.forward")
                    }
                    if soundEffectsEnabled {
                        TabSoundPlayer.shared.playUserSharing()
                    }
                    for userID in dedupedNewlyActiveIDs {
                        lastSharingToastAtByUserID[userID] = now
                    }
                }
            }
        } catch {
            OttoLog.app.error("refreshPresence failed circle=\(circleID) error=\(String(describing: error))")
        }
    }

    func refreshPublicPresence() async {
        do {
            let response = try await APIClient.shared.fetchPresence(circleId: Self.publicPresenceCircleID)
            if allUsers.isEmpty {
                allUsers = (try? await APIClient.shared.fetchUsers()) ?? []
            }
            let members = freshPresenceByUserID(response.members).values.compactMap { presence -> FriendLocation? in
                guard presence.userId != currentUserID else { return nil }
                guard let lat = presence.lat, let lng = presence.lng else { return nil }

                let user = allUsers.first(where: { $0.id == presence.userId })
                let displayName = user?.displayName ?? "Driver"
                let carName = user?.vehicle?.displayName
                    ?? [user?.vehicle?.make, user?.vehicle?.model]
                        .compactMap { $0 }
                        .joined(separator: " ")
                        .trimmingCharacters(in: .whitespaces)

                let presenceUpdatedAt = presence.updatedAt.flatMap { presenceDateFormatter.date(from: $0) }
                let previousMember = publicPresenceMembers.first(where: { $0.id == presence.userId })
                return FriendLocation(
                    id: presence.userId,
                    name: displayName,
                    avatarName: displayName,
                    avatarUrl: user?.avatarUrl,
                    car: carName.isEmpty ? "Unknown Car" : carName,
                    clubRole: "Public",
                    lastRun: "Now",
                    coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
                    speedMph: Int(presence.speedMph.rounded()),
                    isOnline: effectivePresenceInApp(presence),
                    isActive: presence.isActive,
                    accentColor: MapAccentPalette.resolvedColor(mapAccentKey: user?.mapAccentKey, userId: presence.userId),
                    movementMode: resolvedMovementMode(
                        apiMovementMode: presence.movementMode,
                        speedMph: presence.speedMph,
                        isActive: presence.isActive,
                        previous: previousMember?.movementMode
                    ),
                    lastUpdatedAt: presenceUpdatedAt,
                    lastPresenceInApp: true,
                    brandLogoSlug: brandLogoSlug(from: presence)
                )
            }
            publicPresenceMembers = members
        } catch {
            OttoLog.app.error("refreshPublicPresence failed: \(String(describing: error))")
        }
    }

    private func applyPresenceUpdate(_ presence: PresenceCircleResponseDTO.PresenceDTO) {
        if presence.circleId == Self.publicPresenceCircleID {
            applyPublicPresenceUpdate(presence)
            return
        }

        if presence.inApp == false {
            guard let circleIndex = circles.firstIndex(where: { $0.id == presence.circleId }) else { return }
            guard let memberIndex = circles[circleIndex].members.firstIndex(where: { $0.id == presence.userId }) else { return }
            let member = circles[circleIndex].members[memberIndex]
            circles[circleIndex].members[memberIndex] = FriendLocation(
                id: member.id,
                name: member.name,
                avatarName: member.avatarName,
                avatarUrl: member.avatarUrl,
                car: member.car,
                clubRole: member.clubRole,
                lastRun: member.lastRun,
                coordinate: member.coordinate,
                speedMph: 0,
                isOnline: false,
                isActive: false,
                accentColor: member.accentColor,
                movementMode: member.movementMode,
                lastUpdatedAt: member.lastUpdatedAt,
                lastPresenceInApp: false
            )
            var activeIDs = lastKnownActiveSharersByCircleID[presence.circleId] ?? []
            activeIDs.remove(presence.userId)
            lastKnownActiveSharersByCircleID[presence.circleId] = activeIDs
            return
        }

        guard let circleIndex = circles.firstIndex(where: { $0.id == presence.circleId }) else { return }
        guard let memberIndex = circles[circleIndex].members.firstIndex(where: { $0.id == presence.userId }) else { return }
        let member = circles[circleIndex].members[memberIndex]
        circles[circleIndex].members[memberIndex] = friendLocation(member, applying: presence)

        var activeIDs = lastKnownActiveSharersByCircleID[presence.circleId] ?? []
        if presence.isActive, presence.userId != currentUserID {
            activeIDs.insert(presence.userId)
        } else {
            activeIDs.remove(presence.userId)
        }
        lastKnownActiveSharersByCircleID[presence.circleId] = activeIDs
    }

    private func applyPublicPresenceUpdate(_ presence: PresenceCircleResponseDTO.PresenceDTO) {
        if presence.inApp == false {
            publicPresenceMembers.removeAll { $0.id == presence.userId }
            return
        }
        guard Self.lastPresenceInAppDot(from: presence) == true else {
            publicPresenceMembers.removeAll { $0.id == presence.userId }
            return
        }
        guard let lat = presence.lat, let lng = presence.lng else { return }
        let user = allUsers.first(where: { $0.id == presence.userId })
        let displayName = user?.displayName ?? "Driver"
        let carName = user?.vehicle?.displayName
            ?? [user?.vehicle?.make, user?.vehicle?.model]
                .compactMap { $0 }
                .joined(separator: " ")
                .trimmingCharacters(in: .whitespaces)
        let updatedAt = presence.updatedAt.flatMap { presenceDateFormatter.date(from: $0) }
        let member = FriendLocation(
            id: presence.userId,
            name: displayName,
            avatarName: displayName,
            avatarUrl: user?.avatarUrl,
            car: carName.isEmpty ? "Unknown Car" : carName,
            clubRole: "Public",
            lastRun: "Now",
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
            speedMph: Int(presence.speedMph.rounded()),
            isOnline: effectivePresenceInApp(presence),
            isActive: presence.isActive,
            accentColor: MapAccentPalette.resolvedColor(mapAccentKey: user?.mapAccentKey, userId: presence.userId),
            movementMode: resolvedMovementMode(
                apiMovementMode: presence.movementMode,
                speedMph: presence.speedMph,
                isActive: presence.isActive,
                previous: publicPresenceMembers.first(where: { $0.id == presence.userId })?.movementMode
            ),
            lastUpdatedAt: updatedAt,
            lastPresenceInApp: true,
            brandLogoSlug: brandLogoSlug(from: presence)
        )
        if let index = publicPresenceMembers.firstIndex(where: { $0.id == presence.userId }) {
            publicPresenceMembers[index] = member
        } else if presence.userId != currentUserID {
            publicPresenceMembers.append(member)
        }
    }

    private func friendLocation(
        _ member: FriendLocation,
        applying presence: PresenceCircleResponseDTO.PresenceDTO
    ) -> FriendLocation {
        let dot = Self.lastPresenceInAppDot(from: presence)
        if dot == nil {
            return FriendLocation(
                id: member.id,
                name: member.name,
                avatarName: member.avatarName,
                avatarUrl: member.avatarUrl,
                car: member.car,
                clubRole: member.clubRole,
                lastRun: member.lastRun,
                coordinate: member.coordinate,
                speedMph: 0,
                isOnline: false,
                isActive: false,
                accentColor: member.accentColor,
                movementMode: member.movementMode,
                lastUpdatedAt: member.lastUpdatedAt,
                lastPresenceInApp: nil
            )
        }
        let updatedAt = presence.updatedAt.flatMap { presenceDateFormatter.date(from: $0) }
        if presence.inApp == false {
            return FriendLocation(
                id: member.id,
                name: member.name,
                avatarName: member.avatarName,
                avatarUrl: member.avatarUrl,
                car: member.car,
                clubRole: member.clubRole,
                lastRun: member.lastRun,
                coordinate: member.coordinate,
                speedMph: 0,
                isOnline: false,
                isActive: false,
                accentColor: member.accentColor,
                movementMode: member.movementMode,
                lastUpdatedAt: updatedAt ?? member.lastUpdatedAt,
                lastPresenceInApp: false
            )
        }
        return FriendLocation(
            id: member.id,
            name: member.name,
            avatarName: member.avatarName,
            avatarUrl: member.avatarUrl,
            car: member.car,
            clubRole: member.clubRole,
            lastRun: member.lastRun,
            coordinate: CLLocationCoordinate2D(
                latitude: presence.lat ?? member.coordinate.latitude,
                longitude: presence.lng ?? member.coordinate.longitude
            ),
            speedMph: Int(presence.speedMph.rounded()),
            isOnline: effectivePresenceInApp(presence),
            isActive: presence.isActive,
            accentColor: member.accentColor,
            movementMode: resolvedMovementMode(
                apiMovementMode: presence.movementMode,
                speedMph: presence.speedMph,
                isActive: presence.isActive,
                previous: member.movementMode
            ),
            lastUpdatedAt: updatedAt,
            lastPresenceInApp: true,
            brandLogoSlug: brandLogoSlug(from: presence)
        )
    }

    private func brandLogoSlug(from presence: PresenceCircleResponseDTO.PresenceDTO) -> String? {
        guard presence.isActive else { return nil }
        guard let raw = presence.logoSlug?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return nil
        }
        return raw
    }

    private static func lastPresenceInAppDot(from presence: PresenceCircleResponseDTO.PresenceDTO) -> Bool? {
        if presence.inApp == false { return false }
        guard !isPresencePayloadStale(presence) else { return nil }
        return true
    }

    private static func isPresencePayloadStale(_ presence: PresenceCircleResponseDTO.PresenceDTO, maxAge: TimeInterval = 150) -> Bool {
        guard let raw = presence.updatedAt,
              let parsed = parsePresenceDate(raw) else { return true }
        return Date().timeIntervalSince(parsed) > maxAge
    }

    func avatarPresenceDotColor(forUserID userID: String) -> Color {
        if userID == currentUserID { return DriverPresenceStatus.inAppForeground.color }
        for circle in circles {
            if let member = circle.members.first(where: { $0.id == userID }) {
                return member.presenceStatus.color
            }
        }
        if let member = publicPresenceMembers.first(where: { $0.id == userID }) {
            return member.presenceStatus.color
        }
        return DriverPresenceStatus.offline.color
    }

    private func effectivePresenceInApp(_ presence: PresenceCircleResponseDTO.PresenceDTO) -> Bool {
        presence.inApp != false
    }

    private func freshPresenceByUserID(_ presences: [PresenceCircleResponseDTO.PresenceDTO]) -> [String: PresenceCircleResponseDTO.PresenceDTO] {
        let now = Date()
        let maxAge: TimeInterval = 150
        let pairs = presences.compactMap { presence -> (String, PresenceCircleResponseDTO.PresenceDTO)? in
            if !effectivePresenceInApp(presence) { return nil }
            if let rawUpdatedAt = presence.updatedAt,
               let updatedAt = presenceDateFormatter.date(from: rawUpdatedAt),
               now.timeIntervalSince(updatedAt) > maxAge {
                return nil
            }
            return (presence.userId, presence)
        }
        return Dictionary(pairs, uniquingKeysWith: { _, new in new })
    }

    private func emitSelfDrivingOnlyPauseToastsIfNeeded() {
        guard sharingSessionMode == .drivingOnly, isSharingEnabled else { return }
        let paused = isDrivingOnlyBroadcastPaused
        if suppressInitialDrivingOnlyPauseToast && paused {
            suppressInitialDrivingOnlyPauseToast = false
            lastSelfDrivingOnlyPausedForToast = paused
            return
        }
        if lastSelfDrivingOnlyPausedForToast == paused { return }
        lastSelfDrivingOnlyPausedForToast = paused
        if paused {
            showToast("Sharing paused", icon: "pause.circle.fill")
        } else {
            showToast("Sharing live", icon: "dot.radiowaves.left.and.right")
        }
    }

    func pushPresence(
        location: CLLocation?,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode = .unknown
    ) async {
        guard isSharingEnabled else { return }
        guard !currentUserID.isEmpty else { return }
        guard isSharingSessionActive else {
            stopSharingSession()
            return
        }

        // Driving-only: live only when `movementMode == .driving` (see `LocationService`). RootTabView also flushes on `movementMode` changes so walking pauses without waiting for GPS.
        let motionIndicatesDriving = movementMode == .driving

        if sharingSessionMode != .drivingOnly {
            isDrivingOnlyBroadcastPaused = false
        } else {
            isDrivingOnlyBroadcastPaused = !motionIndicatesDriving
        }
        emitSelfDrivingOnlyPauseToastsIfNeeded()
        persistDrivingOnlyPauseFlagForWidgetIfNeeded()

        if sharingSessionMode == .drivingOnly {
            if !motionIndicatesDriving {
                if !drivingOnlyNotDrivingInactiveEmitted {
                    drivingOnlyNotDrivingInactiveEmitted = true
                    let inactiveCircleIDs = effectiveSharingCircleIDs
                    if !inactiveCircleIDs.isEmpty {
                        await markPresenceInactive(circleIDs: inactiveCircleIDs)
                    }
                    await stopActiveDrive(location: location)
                }
                return
            }
            drivingOnlyNotDrivingInactiveEmitted = false
        }

        let targetCircleIDs = effectiveSharingCircleIDs
        guard !targetCircleIDs.isEmpty else { return }

        for (index, circleID) in targetCircleIDs.enumerated() {
            var payload: [String: Any] = [
                "userId": currentUserID,
                "circleId": circleID,
                "isActive": true,
                "inApp": true,
                "speedMph": speedMetersPerSecond * 2.23694,
                "movementMode": movementMode.apiValue,
                "trackDrivingStats": index == 0,
            ]
            if !selectedSharingCarID.isEmpty {
                payload["carId"] = selectedSharingCarID
            }

            if let location {
                payload["lat"] = location.coordinate.latitude
                payload["lng"] = location.coordinate.longitude
                payload["capturedAt"] = ISO8601DateFormatter().string(from: location.timestamp)
                if location.horizontalAccuracy >= 0 {
                    payload["accuracyMeters"] = location.horizontalAccuracy
                }
            }

            do {
                try await APIClient.shared.updatePresence(payload: payload)
            } catch {
                // Keep UX uninterrupted if API call fails.
            }
        }

        await startDriveIfNeeded(location: location)
    }

    /// Periodically mark the user as in the app for every squad they belong to (`isActive: false`, no coordinates).
    /// Call while `scenePhase == .active` and location sharing is off. Sharing uses `pushPresence` instead.
    func pushInAppPresenceHeartbeatsIfNeeded(scenePhase: ScenePhase, force: Bool = false) async {
        guard scenePhase == .active else { return }
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        guard !isSharingEnabled else { return }

        let now = Date()
        if !force,
           let last = lastInAppPresenceHeartbeatAt,
           now.timeIntervalSince(last) < Self.inAppPresenceHeartbeatMinInterval
        {
            return
        }

        let ids = Set(circles.map(\.id)).filter { !$0.isEmpty }
        guard !ids.isEmpty else { return }

        lastInAppPresenceHeartbeatAt = now

        for circleID in ids {
            do {
                try await APIClient.shared.updatePresence(payload: [
                    "circleId": circleID,
                    "isActive": false,
                    "inApp": true,
                    "speedMph": 0,
                    "movementMode": FriendMovementMode.unknown.apiValue,
                ])
            } catch {}
        }
    }

    /// Clears in-app presence for all squads when the app enters the background (matches Android `ON_STOP`).
    func pushOutOfAppPresenceHeartbeat() async {
        guard isAuthenticated, !currentUserID.isEmpty else { return }
        let ids = Set(circles.map(\.id)).filter { !$0.isEmpty }
        guard !ids.isEmpty else { return }
        for circleID in ids {
            do {
                try await APIClient.shared.updatePresence(payload: [
                    "circleId": circleID,
                    "isActive": false,
                    "inApp": false,
                    "speedMph": 0,
                    "movementMode": FriendMovementMode.unknown.apiValue,
                ])
            } catch {}
        }
    }

    func resetActiveDrivePathTrail() {
        activeDrivePathTrail = []
    }

    func resetRouteDrivePathSamples() {
        routeDrivePathSamples = []
    }

    func setRouteDrivePathSamples(_ samples: [DrivePathSample]) {
        routeDrivePathSamples = samples
    }

    func recordRouteDrivePathSample(location: CLLocation, speedMph: Double) {
        if let last = routeDrivePathSamples.last {
            let delta = CLLocation(latitude: last.coordinate.latitude, longitude: last.coordinate.longitude)
                .distance(from: location)
            if delta < Self.minRouteDrivePathSampleDistanceMeters {
                return
            }
        }

        routeDrivePathSamples.append(DrivePathSample(location: location, speedMph: speedMph))
        if routeDrivePathSamples.count > Self.maxRouteDrivePathSampleCount {
            routeDrivePathSamples.removeFirst(routeDrivePathSamples.count - Self.maxRouteDrivePathSampleCount)
        }
    }

    private func recordLocalActiveDrivePathSample(location: CLLocation, speedMph: Double) {
        if let last = activeDrivePathTrail.last {
            let delta = CLLocation(latitude: last.coordinate.latitude, longitude: last.coordinate.longitude)
                .distance(from: location)
            if delta < Self.minActiveDrivePathTrailDistanceMeters {
                return
            }
        }

        activeDrivePathTrail.append(DrivePathSample(location: location, speedMph: speedMph))
        if activeDrivePathTrail.count > Self.maxActiveDrivePathTrailCount {
            activeDrivePathTrail.removeFirst(activeDrivePathTrail.count - Self.maxActiveDrivePathTrailCount)
        }
    }

    func throttledRecordDrivePathSample(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode = .unknown
    ) async {
        let sessionRecording = activeDriveSession?.isRecording == true
        guard isSharingEnabled, sharingSaveDriveEnabled || sessionRecording else {
            if sessionRecording {
                await throttledRecordDrivePathSampleForSession(
                    location: location,
                    speedMetersPerSecond: speedMetersPerSecond,
                    movementMode: movementMode
                )
            }
            return
        }
        if sharingSessionMode == .drivingOnly, movementMode != .driving {
            return
        }
        guard activeDriveID != nil else {
            if sessionRecording {
                await startDriveRecordingIfNeeded(location: location, title: driveRecordingTitle(for: activeDriveSession?.kind ?? .quick))
            }
            return
        }
        let now = Date()
        guard now.timeIntervalSince(lastDrivePathNetworkAt) >= Self.minDrivePathInterval else { return }
        lastDrivePathNetworkAt = now
        await appendDrivePoint(
            location: location,
            speedMetersPerSecond: speedMetersPerSecond,
            movementMode: movementMode
        )
    }

    private func startDriveIfNeeded(location: CLLocation?) async {
        let wantsRecording = (isSharingEnabled && sharingSaveDriveEnabled) || activeDriveSession?.isRecording == true
        guard wantsRecording else { return }
        guard activeDriveID == nil else { return }
        guard !currentUserID.isEmpty else { return }

        do {
            let sharedCircleIds = Array(sharingCircleIDs)
            let driveCircleID = sharedCircleIds.first ?? selectedCircleID
            let drive = try await APIClient.shared.startDrive(
                userId: currentUserID,
                circleId: driveCircleID,
                sharingAudience: SharingAudience.circles.rawValue,
                sharedCircleIds: sharedCircleIds,
                title: "Live Drive Session",
                location: location.map { (lat: $0.coordinate.latitude, lng: $0.coordinate.longitude) }
            )
            activeDriveID = drive.id
            activeDriveDistanceMeters = 0
            activeDriveMaxSpeedMph = 0
            activeDrivePathTrail = []
            lastDriveLocationForDistance = location
            await refreshRecentDrives()
        } catch {
            // Presence can still continue even if drive session creation fails.
        }
    }

    func appendDrivePoint(location: CLLocation, speedMetersPerSecond: Double, movementMode: FriendMovementMode) async {
        guard let driveId = activeDriveID else { return }

        let speedMph = speedMetersPerSecond * 2.23694
        if movementMode == .driving {
            activeDriveMaxSpeedMph = max(activeDriveMaxSpeedMph, speedMph)
        }

        if let anchor = lastDriveLocationForDistance {
            activeDriveDistanceMeters += anchor.distance(from: location)
        }
        lastDriveLocationForDistance = location
        recordLocalActiveDrivePathSample(location: location, speedMph: speedMph)

        do {
            try await APIClient.shared.appendDrivePoint(
                driveId: driveId,
                lat: location.coordinate.latitude,
                lng: location.coordinate.longitude,
                speedMph: speedMph,
                heading: location.course >= 0 ? location.course : nil,
                accuracyMeters: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil
            )
        } catch {
            // Keep live sharing uninterrupted.
        }
    }

    func markPresenceInactive(circleIDs: [String]) async {
        guard !currentUserID.isEmpty else { return }
        for circleID in Set(circleIDs) where !circleID.isEmpty {
            do {
                try await APIClient.shared.updatePresence(payload: [
                    "userId": currentUserID,
                    "circleId": circleID,
                    "isActive": false,
                    "inApp": true,
                    "speedMph": 0,
                    "movementMode": FriendMovementMode.unknown.apiValue,
                ])
            } catch {
                // Presence will age out server-side if this best-effort stop fails.
            }
        }
    }

    private static func mapFriend(user: UserDTO?, userID: String, role: String) -> FriendLocation {
        let displayName = user?.displayName ?? "Driver"
        let carName = user?.vehicle?.displayName
            ?? [user?.vehicle?.make, user?.vehicle?.model]
                .compactMap { $0 }
                .joined(separator: " ")
                .trimmingCharacters(in: .whitespaces)

        return FriendLocation(
            id: userID,
            name: displayName,
            avatarName: displayName,
            avatarUrl: user?.avatarUrl,
            car: carName.isEmpty ? "Unknown Car" : carName,
            clubRole: role.capitalized,
            lastRun: "Recent drive",
            coordinate: Self.pseudoCoordinate(seed: userID),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: MapAccentPalette.resolvedColor(mapAccentKey: user?.mapAccentKey, userId: userID),
            movementMode: .unknown,
            lastUpdatedAt: Self.parsePresenceDate(user?.lastPresenceAt)
        )
    }

    private func movementMode(
        forSpeedMph speedMph: Double,
        isActive: Bool,
        previous: FriendMovementMode? = nil
    ) -> FriendMovementMode {
        guard isActive else { return .unknown }
        if speedMph >= 10 { return .driving }
        if speedMph > 1 && speedMph < 7 { return .walking }
        if previous == .driving { return .driving }
        return .unknown
    }

    private func resolvedMovementMode(
        apiMovementMode: String?,
        speedMph: Double,
        isActive: Bool,
        previous: FriendMovementMode? = nil
    ) -> FriendMovementMode {
        if let raw = apiMovementMode?.lowercased() {
            if raw == "driving" { return .driving }
            if raw == "walking" { return .walking }
        }
        return movementMode(forSpeedMph: speedMph, isActive: isActive, previous: previous)
    }

    private static func pseudoCoordinate(seed: String) -> CLLocationCoordinate2D {
        let hash = abs(seed.hashValue)
        let latOffset = Double(hash % 300) / 10_000.0
        let lngOffset = Double((hash / 300) % 300) / 10_000.0
        return CLLocationCoordinate2D(latitude: 37.7749 + latOffset, longitude: -122.4194 + lngOffset)
    }

    func updateMapAccentKey(_ key: String) async {
        guard MapAccentKey(rawValue: key) != nil else { return }
        guard !currentUserID.isEmpty else { return }
        do {
            try await APIClient.shared.updateUserMapAccentKey(userId: currentUserID, mapAccentKey: key)
            await refreshCircles()
        } catch {
            errorMessage = "Couldn’t update map color."
        }
    }

    func uploadProfilePhoto(_ imageData: Data) async {
        guard !currentUserID.isEmpty else { return }
        do {
            _ = try await APIClient.shared.uploadAvatar(userId: currentUserID, imageData: imageData)
            await refreshCircles()
        } catch {
            errorMessage = "Couldn’t upload profile photo."
        }
    }

    func uploadSquadPhoto(circleId: String, imageData: Data) async {
        guard !currentUserID.isEmpty else { return }
        do {
            _ = try await APIClient.shared.uploadCirclePhoto(circleId: circleId, imageData: imageData)
            await refreshCircles()
        } catch {
            errorMessage = "Couldn’t upload squad photo."
        }
    }

    private static func iconForCircle(name: String) -> String {
        let lowered = name.lowercased()
        if lowered.contains("night") { return "moon.stars.fill" }
        if lowered.contains("coffee") { return "cup.and.saucer.fill" }
        if lowered.contains("canyon") { return "mountain.2.fill" }
        return "person.3.fill"
    }

    func requestOpenMapTabOnly() {
        mapTabOnlyRequest = UUID()
    }

    func requestGarageTabFocus() {
        garageTabFocusRequest = UUID()
    }

    func consumeGarageTabFocusRequest() {
        garageTabFocusRequest = nil
    }

    func requestPresentSharingSheetFromDeepLink() {
        pendingSharingSheetPresentation = UUID()
    }

    @discardableResult
    func consumePendingSharingSheetPresentation() -> UUID? {
        let token = pendingSharingSheetPresentation
        pendingSharingSheetPresentation = nil
        return token
    }

    /// One-line place label for the home screen widget (city/area). Call from map/GPS after reverse geocode.
    func updateWidgetPlaceLabelForSharingWidget(_ label: String?) {
        guard OttoSharingWidgetConfiguration.isEnabled else { return }
        let trimmed = label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let sp = Self.sharingPersistenceDefaults
        let prev = sp.string(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel) ?? ""
        guard trimmed != prev else { return }
        if trimmed.isEmpty {
            sp.removeObject(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel)
        } else {
            sp.set(trimmed, forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel)
        }
        OttoSharingPersistence.bumpRevision(in: sp)
        OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
        lastReconciledSharingRevision = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        reloadSharingWidgetTimelines()
    }

    func consumeMapTabOnlyRequest() {
        mapTabOnlyRequest = nil
    }

    func requestLocationSessionSync() {
        locationSessionSyncTick &+= 1
    }

    /// Reloads sharing fields when the home screen widget bumps `OttoSharingUserDefaultsKeys.sharingRevision`.
    func applySharingPersistenceFromSuiteIfNeeded() {
        let sp = Self.sharingPersistenceDefaults
        let rev = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        guard rev > lastReconciledSharingRevision + 0.001 else { return }
        hydrateSharingSessionFromSuite(revision: rev)
    }

    private func hydrateSharingSessionFromSuite(revision: Double) {
        let sp = Self.sharingPersistenceDefaults
        isSharingEnabled = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        if let savedCircleIDs = sp.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) as? [String] {
            sharingCircleIDs = Set(savedCircleIDs)
        }
        if let raw = sp.string(forKey: OttoSharingUserDefaultsKeys.sharingAudience),
           let mode = SharingAudience(rawValue: raw) {
            sharingAudience = mode
        }
        let savedDuration = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        if savedDuration > 0 {
            sharingDurationSeconds = savedDuration
        }
        let savedStartedAt = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        sharingSessionStartedAt = savedStartedAt > 0 ? Date(timeIntervalSince1970: savedStartedAt) : nil
        if let rawMode = sp.string(forKey: OttoSharingUserDefaultsKeys.sharingSessionMode),
           let mode = SharingSessionMode(rawValue: rawMode) {
            sharingSessionMode = mode
        }
        if sp.object(forKey: OttoSharingUserDefaultsKeys.sharingSaveDriveEnabled) != nil {
            sharingSaveDriveEnabled = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingSaveDriveEnabled)
        }
        if sp.object(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused) != nil {
            isDrivingOnlyBroadcastPaused = sp.bool(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        } else {
            isDrivingOnlyBroadcastPaused = false
        }
        lastReconciledSharingRevision = revision
        if isSharingEnabled {
            lastMirroredDrivingOnlyPausedForWidget = sharingSessionMode == .drivingOnly && isDrivingOnlyBroadcastPaused
        } else {
            lastMirroredDrivingOnlyPausedForWidget = nil
        }
    }

    private func reloadSharingWidgetTimelines() {
        guard OttoSharingWidgetConfiguration.isEnabled else { return }
        WidgetCenter.shared.reloadTimelines(ofKind: OttoSharingWidgetKind.control)
    }

    private func persistDrivingOnlyPauseFlagForWidgetIfNeeded() {
        guard isSharingEnabled else {
            if lastMirroredDrivingOnlyPausedForWidget != nil {
                lastMirroredDrivingOnlyPausedForWidget = nil
                let sp = Self.sharingPersistenceDefaults
                sp.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
                UserDefaults.standard.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
                OttoSharingPersistence.bumpRevision(in: sp)
                OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
                lastReconciledSharingRevision = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
                reloadSharingWidgetTimelines()
            }
            return
        }
        let storedPauseFlag = sharingSessionMode == .drivingOnly && isDrivingOnlyBroadcastPaused
        guard lastMirroredDrivingOnlyPausedForWidget != storedPauseFlag else { return }
        lastMirroredDrivingOnlyPausedForWidget = storedPauseFlag
        let sp = Self.sharingPersistenceDefaults
        sp.set(storedPauseFlag, forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        UserDefaults.standard.set(storedPauseFlag, forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        OttoSharingPersistence.bumpRevision(in: sp)
        OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
        lastReconciledSharingRevision = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        reloadSharingWidgetTimelines()
    }

    func persistSharingState() {
        let sp = Self.sharingPersistenceDefaults
        sp.set(isSharingEnabled, forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        sp.set(Array(sharingCircleIDs), forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs)
        sp.set(sharingAudience.rawValue, forKey: OttoSharingUserDefaultsKeys.sharingAudience)
        sp.set(sharingDurationSeconds, forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        if let sharingSessionStartedAt {
            sp.set(sharingSessionStartedAt.timeIntervalSince1970, forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        } else {
            sp.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        }
        sp.set(sharingSessionMode.rawValue, forKey: OttoSharingUserDefaultsKeys.sharingSessionMode)
        sp.set(sharingSaveDriveEnabled, forKey: OttoSharingUserDefaultsKeys.sharingSaveDriveEnabled)
        sp.set(sharingAudienceLabel, forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary)
        UserDefaults.standard.set(selectedSharingCarID, forKey: StorageKeys.sharingCarID)
        if let encodedGarage = try? JSONEncoder().encode(garageCars) {
            UserDefaults.standard.set(encodedGarage, forKey: StorageKeys.garageCars)
        }
        OttoSharingPersistence.bumpRevision(in: sp)
        lastReconciledSharingRevision = sp.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        OttoSharingPersistence.mirrorSharingKeysToStandard(from: sp)
        reloadSharingWidgetTimelines()
        persistDrivingOnlyPauseFlagForWidgetIfNeeded()
    }

    func showToast(_ text: String, icon: String) {
        activeToast = AppToast(text: text, systemImage: icon)
    }

    private func cacheShareExtensionAuth(token: String, userID: String) {
        Self.sharedDefaults?.set(token, forKey: "authToken")
        Self.sharedDefaults?.set(userID, forKey: "currentUserID")
    }

    private func clearShareExtensionCache() {
        Self.sharedDefaults?.removeObject(forKey: "authToken")
        Self.sharedDefaults?.removeObject(forKey: "currentUserID")
        Self.sharedDefaults?.removeObject(forKey: "cachedSquads")
    }

    func requestPushNotificationsIfNeeded() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            Task { @MainActor in
                if settings.authorizationStatus == .authorized {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if let error {
                OttoLog.app.error("Push notification permission failed: \(String(describing: error))")
                return
            }
            guard granted else {
                OttoLog.app.info("Push notification permission denied")
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func didReceivePushDeviceToken(_ token: String) {
        pendingPushDeviceToken = token
        Task { await ensurePushDeviceTokenRegisteredWithBackend() }
    }

    /// Retries registration so a slow APNs token callback after login still reaches the backend.
    func ensurePushDeviceTokenRegisteredWithBackend(maxAttempts: Int = 6) async {
        for attempt in 1 ... max(1, maxAttempts) {
            let registered = await registerPendingPushDeviceTokenIfPossible()
            if registered { return }
            guard attempt < maxAttempts else { break }
            let delayNs = UInt64(attempt) * 500_000_000
            try? await Task.sleep(nanoseconds: delayNs)
            if pendingPushDeviceToken == nil, attempt >= 2 {
                await MainActor.run {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
        OttoLog.app.warning(
            "APNs device token registration exhausted retries authenticated=\(self.isAuthenticated) hasPendingToken=\(self.pendingPushDeviceToken != nil)"
        )
    }

    @discardableResult
    private func registerPendingPushDeviceTokenIfPossible() async -> Bool {
        guard isAuthenticated else { return false }
        guard let token = pendingPushDeviceToken, !token.isEmpty else { return false }
        let environment = Self.apnsEnvironment
        let bundleId = Bundle.main.bundleIdentifier ?? "otto.otto-mobile"
        do {
            try await APIClient.shared.registerPushDeviceToken(
                token: token,
                environment: environment,
                bundleId: bundleId,
                appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                timeZone: TimeZoneSync.systemIANAIdentifier
            )
            UserDefaults.standard.set(TimeZoneSync.systemIANAIdentifier, forKey: "otto.lastReportedTimeZone")
            let prefix = tokenPrefix(token)
            PushDiagnostics.logDeviceTokenRegistration(
                tokenPrefix: prefix,
                environment: environment,
                bundleId: bundleId,
                backendDeviceCount: nil
            )
            await auditRegisteredPushDevices(currentToken: token)
            OttoLog.app.info("Registered APNs device token with backend prefix=\(prefix)")
            return true
        } catch {
            OttoLog.app.error("APNs device token backend registration failed: \(String(describing: error))")
            return false
        }
    }

    private func tokenPrefix(_ token: String) -> String {
        let trimmed = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > 12 else { return trimmed }
        let head = trimmed.prefix(8)
        let tail = trimmed.suffix(4)
        return "\(head)…\(tail)"
    }

    private func auditRegisteredPushDevices(currentToken: String) async {
        do {
            let devices = try await APIClient.shared.fetchRegisteredPushDevices()
            let iosActive = devices.filter { $0.platform == "ios" && $0.disabledAt == nil }
            PushDiagnostics.logDeviceTokenRegistration(
                tokenPrefix: tokenPrefix(currentToken),
                environment: Self.apnsEnvironment,
                bundleId: Bundle.main.bundleIdentifier ?? "otto.otto-mobile",
                backendDeviceCount: iosActive.count
            )
            let summary = iosActive.map { device in
                let marker = device.matchesToken(currentToken) ? "*" : ""
                return "\(marker)\(device.platform)/\(device.environment) prefix=\(device.tokenPrefix) last=\(device.lastRegisteredAt ?? "unknown")"
            }.joined(separator: "; ")
            PushDiagnostics.logRegisteredDevicesAudit(
                summary.isEmpty ? "no active ios tokens" : "\(iosActive.count) active: \(summary)"
            )
        } catch {
            OttoLog.push.error("Device token audit failed: \(String(describing: error))")
        }
    }

    private func cacheShareExtensionSquads() {
        let squads = circlesSortedByRecentAccess(circles).map { circle in
            [
                "id": circle.id,
                "name": circle.name,
                "subtitle": "\(circle.members.count) \(circle.members.count == 1 ? "member" : "members")",
                "photoUrl": circle.photoUrl ?? "",
                "icon": circle.icon
            ]
        }
        if let data = try? JSONSerialization.data(withJSONObject: squads, options: []) {
            Self.sharedDefaults?.set(data, forKey: "cachedSquads")
        }
    }

    private func syncChatMirrorsFromStore() {
        latestCircleChatMessage = chatStore.latestCircleChatMessage
        latestDirectMessage = chatStore.latestDirectMessage
        unreadChatCountsByCircleID = chatStore.squadUnreadCountsByCircleID
        unreadDirectMessageCountsByConversationID = chatStore.unreadDirectMessageCountsByConversationID
    }

    func unreadDirectCount(for conversation: DirectConversationDTO) -> Int {
        chatStore.unreadTracker.unreadCount(forConversationID: conversation.id)
    }

    func unreadDirectCount(forOtherUserID userID: String) -> Int {
        guard let conversation = directConversationsByUserID[userID] else { return 0 }
        return unreadDirectCount(for: conversation)
    }

    var totalChatUnreadCount: Int {
        chatStore.totalChatUnreadCount
    }

    func reconcileChatUnreadState() {
        guard isAuthenticated else { return }
        chatStore.reconcileUnreadState(currentUserID: currentUserID)
        publishChatUnreadFromStore()
    }

    func publishChatUnreadFromStore() {
        syncChatMirrorsFromStore()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    func setCirclesRootTabSelected(_ selected: Bool) {
        guard circlesRootTabIsSelected != selected else { return }
        circlesRootTabIsSelected = selected
        if !selected {
            clearChatReadingFocus()
        }
    }

    func clearChatReadingFocus() {
        if let visibleCircleID = chatStore.visibleSquadChatTabCircleID {
            setSquadChatTabVisible(circleID: visibleCircleID, isVisible: false)
        }
        if activeDirectConversationID != nil {
            setActiveDirectConversation(conversationID: nil, otherUserID: nil)
        }
    }

    func reconcileChatUnreadStateAsync() {
        Task { @MainActor in
            reconcileChatUnreadState()
        }
    }

    func setActiveChatCircleID(_ circleID: String?) {
        activeChatCircleID = circleID
        PushFocusBridge.activeChatCircleId = circleID
        PushDiagnostics.logPushFocusBridgeChange(circleId: circleID, reason: "setActiveChatCircleID")
        chatStore.setActiveConversation(circleID.map { .squad($0) })
        syncChatMirrorsFromStore()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    func setSquadChatTabVisible(circleID: String?, isVisible: Bool) {
        chatStore.setSquadChatTabVisible(circleID: circleID, isVisible: isVisible)
        if isVisible, let circleID {
            setActiveChatCircleID(circleID)
        } else if activeChatCircleID == circleID {
            setActiveChatCircleID(nil)
        }
        syncChatMirrorsFromStore()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    func markSquadChatRead(circleID: String) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        chatStore.markSquadRead(circleID: key)
        syncChatMirrorsFromStore()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    func setActiveChatCircleIDAndMarkRead(_ circleID: String?) {
        setActiveChatCircleID(circleID)
        if let circleID {
            markSquadChatRead(circleID: circleID)
        }
    }

    func cachedSquadChatMessages(forCircleID circleID: String) -> [CircleChatMessageDTO]? {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return nil }
        if let msgs = chatStore.cachedSquadMessages(circleID: key), !msgs.isEmpty {
            return msgs
        }
        guard let msgs = squadChatTranscriptByCircleID[key], !msgs.isEmpty else { return nil }
        return msgs
    }

    func replaceSquadChatTranscript(forCircleID circleID: String, messages: [CircleChatMessageDTO]) {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let sorted = messages.sorted { $0.createdAt < $1.createdAt }
        squadChatTranscriptByCircleID[key] = sorted
        chatStore.replaceSquadMessages(circleID: key, messages: sorted)
    }

    func reconcileSquadChatTranscript(
        forCircleID circleID: String,
        fetchedMessages: [CircleChatMessageDTO],
        visibleMessages: [CircleChatMessageDTO] = []
    ) -> [CircleChatMessageDTO] {
        let key = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return [] }
        let merged = chatStore.reconcileSquadMessages(
            circleID: key,
            fetchedMessages: fetchedMessages,
            visibleMessages: visibleMessages
        )
        if merged.isEmpty {
            return cachedSquadChatMessages(forCircleID: key) ?? []
        }
        squadChatTranscriptByCircleID[key] = merged
        return merged
    }

    func upsertSquadChatTranscript(with message: CircleChatMessageDTO) {
        let key = message.circleId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        chatStore.upsertSquadMessage(message, currentUserID: currentUserID)
        squadChatTranscriptByCircleID[key] = chatStore.cachedSquadMessages(circleID: key) ?? []
        syncChatMirrorsFromStore()
    }

    private func mergedSquadChatTranscript(_ messages: [CircleChatMessageDTO], limit: Int) -> [CircleChatMessageDTO] {
        var byID: [String: CircleChatMessageDTO] = [:]
        for message in messages {
            byID[message.id] = message
        }
        let sorted = byID.values.sorted { $0.createdAt < $1.createdAt }
        guard sorted.count > limit else { return sorted }
        return Array(sorted.suffix(limit))
    }

    func cachedDirectMessages(conversationID: String) -> [DirectMessageDTO]? {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, let msgs = directMessageTranscriptByConversationID[key], !msgs.isEmpty else {
            return chatStore.cachedDirectMessages(conversationID: key)
        }
        return chatStore.cachedDirectMessages(conversationID: key) ?? msgs
    }

    func replaceDirectMessageTranscript(conversationID: String, messages: [DirectMessageDTO]) {
        let key = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let sorted = messages.sorted { $0.createdAt < $1.createdAt }
        directMessageTranscriptByConversationID[key] = sorted
        chatStore.replaceDirectMessages(conversationID: key, messages: sorted)
    }

    func upsertDirectMessageTranscript(_ message: DirectMessageDTO) {
        let key = message.conversationId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        chatStore.upsertDirectMessage(message, currentUserID: currentUserID)
        directMessageTranscriptByConversationID[key] = chatStore.cachedDirectMessages(conversationID: key) ?? []
        syncChatMirrorsFromStore()
    }

    func setActiveDirectConversation(conversationID: String?, otherUserID: String?) {
        activeDirectConversationID = conversationID
        PushFocusBridge.activeDirectConversationId = conversationID
        chatStore.setActiveConversation(conversationID.map { .direct($0) })
        chatStore.setDirectThreadVisible(conversationID: conversationID, isVisible: conversationID != nil)
        if let conversationID {
            chatStore.markDirectReadIfThreadVisible(conversationID: conversationID)
        }
        publishChatUnreadFromStore()
    }

    func clearDirectUnread(conversationID: String) {
        chatStore.clearDirectUnread(conversationID: conversationID)
        syncChatMirrorsFromStore()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    func registerDirectConversation(_ conversation: DirectConversationDTO) {
        guard let normalized = normalizedDirectConversation(conversation),
              let otherUserID = normalized.otherUser?.id else {
            DMNavigationDiagnostics.logRegisterDirectConversation(
                conversation: conversation,
                registered: false,
                reason: "missingOtherUserId"
            )
            return
        }
        directConversationsByUserID[otherUserID] = normalized
        chatStore.registerDirectConversation(normalized)
        subscribeDirectRealtime(conversationID: normalized.id)
        DMNavigationDiagnostics.logRegisterDirectConversation(conversation: normalized, registered: true)
    }

    func refreshDirectConversations() async {
        guard isAuthenticated else { return }
        do {
            let conversations = try await APIClient.shared.fetchDirectConversations()
            let previousLocal = directConversationsByUserID
            let serverDict: [String: DirectConversationDTO] = Dictionary(
                uniqueKeysWithValues: conversations.compactMap { conversation in
                    guard let normalized = normalizedDirectConversation(conversation),
                          let otherUserID = normalized.otherUser?.id else { return nil }
                    return (otherUserID, normalized)
                }
            )
            let serverConversationIDs = Set(serverDict.values.map(\.id))
            let localOnlyEmpty = previousLocal.values.filter { conversation in
                conversation.lastMessageAt == nil && !serverConversationIDs.contains(conversation.id)
            }
            var merged = serverDict
            for conversation in localOnlyEmpty {
                guard let normalized = normalizedDirectConversation(conversation),
                      let otherUserID = normalized.otherUser?.id else { continue }
                merged[otherUserID] = normalized
            }
            DMNavigationDiagnostics.logRefreshDirectConversationsReplaced(
                serverCount: conversations.count,
                droppedLocalOnlyCount: localOnlyEmpty.count,
                previousLocalCount: previousLocal.count,
                mergedLocalOnlyCount: merged.values.filter { $0.lastMessageAt == nil }.count
            )
            directConversationsByUserID = merged
            chatStore.replaceDirectConversations(Array(merged.values))
            subscribeDirectRealtimeToKnownConversations()
            reconcileChatUnreadState()
        } catch {
            OttoLog.app.error("refreshDirectConversations() failed: \(String(describing: error))")
        }
    }

    func subscribeDirectRealtime(conversationID: String) {
        guard !directSubscribedConversationIDs.contains(conversationID) else { return }
        guard chatSocketTask != nil else {
            connectChatRealtimeIfNeeded()
            return
        }
        sendChatSocketJSON([
            "type": "direct.subscribe",
            "conversationId": conversationID,
            "requestId": "direct-subscribe-\(conversationID)"
        ])
        directSubscribedConversationIDs.insert(conversationID)
    }

    func unsubscribeDirectRealtime(conversationID: String) {
        sendChatSocketJSON([
            "type": "direct.unsubscribe",
            "conversationId": conversationID,
            "requestId": "direct-unsubscribe-\(conversationID)"
        ])
        directSubscribedConversationIDs.remove(conversationID)
    }

    func connectChatRealtimeIfNeeded() {
        guard chatSocketTask == nil else {
            subscribeChatRealtimeToCurrentCircles()
            return
        }
        guard let token = APIClient.shared.authToken, !token.isEmpty else {
            chatRealtimeStatusMessage = "Live updates are using polling."
            return
        }

        var components = URLComponents(url: APIConfig.websocketURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "token", value: token)]
        guard let url = components?.url else { return }

        chatSocketDelegate = ChatSocketDelegate(label: "App chat realtime")
        let session = URLSession(configuration: .default, delegate: chatSocketDelegate, delegateQueue: nil)
        chatSocketSession = session
        let task = session.webSocketTask(with: url)
        chatSocketTask = task
        isChatRealtimeConnected = false
        chatRealtimeStatusMessage = nil
        #if DEBUG
        print("App chat WebSocket connecting: \(url.absoluteString.replacingOccurrences(of: token, with: "<token>"))")
        #endif
        task.resume()

        chatSocketReceiveTask = Task {
            await receiveChatRealtimeMessages(task)
        }
    }

    private func disconnectChatRealtime() {
        chatSocketReceiveTask?.cancel()
        chatSocketReceiveTask = nil
        chatSocketTask?.cancel(with: .goingAway, reason: nil)
        chatSocketTask = nil
        chatSocketSession?.invalidateAndCancel()
        chatSocketSession = nil
        chatSocketDelegate = nil
        presenceSubscribedCircleIDs.removeAll()
        directSubscribedConversationIDs.removeAll()
        isChatRealtimeConnected = false
    }

    private func subscribeChatRealtimeToCurrentCircles() {
        guard chatSocketTask != nil else { return }
        let circleIDs = Set(circles.map(\.id)).union([Self.publicPresenceCircleID])
        let stalePresenceSubscriptions = presenceSubscribedCircleIDs.subtracting(circleIDs)
        for circleID in stalePresenceSubscriptions {
            sendChatSocketJSON([
                "type": "presence.unsubscribe",
                "circleId": circleID,
                "requestId": "presence-unsubscribe-\(circleID)"
            ])
            presenceSubscribedCircleIDs.remove(circleID)
        }
        for circle in circles {
            sendChatSocketJSON([
                "type": "circle.chat.subscribe",
                "circleId": circle.id,
                "requestId": "subscribe-\(circle.id)"
            ])
            if !presenceSubscribedCircleIDs.contains(circle.id) {
                sendChatSocketJSON([
                    "type": "presence.subscribe",
                    "circleId": circle.id,
                    "requestId": "presence-subscribe-\(circle.id)"
                ])
                presenceSubscribedCircleIDs.insert(circle.id)
            }
        }
        if !presenceSubscribedCircleIDs.contains(Self.publicPresenceCircleID) {
            sendChatSocketJSON([
                "type": "presence.subscribe",
                "circleId": Self.publicPresenceCircleID,
                "requestId": "presence-subscribe-\(Self.publicPresenceCircleID)"
            ])
            presenceSubscribedCircleIDs.insert(Self.publicPresenceCircleID)
        }
    }

    private func subscribeDirectRealtimeToKnownConversations() {
        guard chatSocketTask != nil else { return }
        for conversation in directConversationsByUserID.values {
            subscribeDirectRealtime(conversationID: conversation.id)
        }
    }

    private func receiveChatRealtimeMessages(_ task: URLSessionWebSocketTask) async {
        while !Task.isCancelled {
            do {
                let socketMessage = try await task.receive()
                switch socketMessage {
                case .string(let text):
                    handleChatSocketPayload(Data(text.utf8))
                case .data(let data):
                    handleChatSocketPayload(data)
                @unknown default:
                    break
                }
            } catch {
                if !Task.isCancelled {
                    #if DEBUG
                    print("App chat WebSocket receive failed: \(error)")
                    #endif
                    chatSocketTask = nil
                    presenceSubscribedCircleIDs.removeAll()
                    directSubscribedConversationIDs.removeAll()
                    isChatRealtimeConnected = false
                    chatRealtimeStatusMessage = "Live updates are using polling."
                    Task {
                        try? await Task.sleep(nanoseconds: 5_000_000_000)
                        guard self.isAuthenticated else { return }
                        self.connectChatRealtimeIfNeeded()
                    }
                }
                return
            }
        }
    }

    private struct ChatSocketEnvelope: Decodable {
        let type: String
        let message: CircleChatMessageDTO?
        let directMessage: DirectMessageDTO?
        let presence: PresenceCircleResponseDTO.PresenceDTO?
        let levelUp: ProfileLevelUpDTO?
        let invite: CircleInviteDTO?
        let profile: UserProfileRealtimePatchDTO?
        let circle: CircleDTO?
        let rosterUsers: [UserDTO]?

        enum CodingKeys: String, CodingKey {
            case type
            case message
            case presence
            case levelUp
            case invite
            case profile
            case circle
            case users
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            type = try container.decode(String.self, forKey: .type)
            presence = try container.decodeIfPresent(PresenceCircleResponseDTO.PresenceDTO.self, forKey: .presence)
            levelUp = try container.decodeIfPresent(ProfileLevelUpDTO.self, forKey: .levelUp)
            invite = try container.decodeIfPresent(CircleInviteDTO.self, forKey: .invite)
            profile = try container.decodeIfPresent(UserProfileRealtimePatchDTO.self, forKey: .profile)
            circle = try container.decodeIfPresent(CircleDTO.self, forKey: .circle)
            rosterUsers = try container.decodeIfPresent([UserDTO].self, forKey: .users)
            if type.hasPrefix("direct.") {
                directMessage = try container.decodeIfPresent(DirectMessageDTO.self, forKey: .message)
                message = nil
            } else {
                message = try container.decodeIfPresent(CircleChatMessageDTO.self, forKey: .message)
                directMessage = nil
            }
        }
    }

    private func applyUserProfileRealtimePatch(_ patch: UserProfileRealtimePatchDTO) {
        let uid = patch.id
        if let idx = allUsers.firstIndex(where: { $0.id == uid }),
           let next = allUsers[idx].applyingProfilePatch(patch) {
            allUsers[idx] = next
        }
        if let idx = contacts.firstIndex(where: { $0.id == uid }),
           let next = contacts[idx].applyingProfilePatch(patch) {
            contacts[idx] = next
        }
        circles = circles.map { circle in
            var next = circle
            next.members = circle.members.map { member in
                member.id == uid ? member.applyingProfilePatch(patch) : member
            }
            return next
        }
        if let idx = publicPresenceMembers.firstIndex(where: { $0.id == uid }) {
            publicPresenceMembers[idx] = publicPresenceMembers[idx].applyingProfilePatch(patch)
        }
        for (circleId, msgs) in squadChatTranscriptByCircleID {
            squadChatTranscriptByCircleID[circleId] = msgs.map { $0.applyingProfilePatch(patch) }
        }
        for (conversationId, msgs) in directMessageTranscriptByConversationID {
            directMessageTranscriptByConversationID[conversationId] = msgs.map { $0.applyingProfilePatch(patch) }
        }
        directConversationsByUserID = Dictionary(uniqueKeysWithValues: directConversationsByUserID.map { key, conv in
            (key, conv.applyingProfilePatch(patch))
        })
        latestCircleChatMessage = latestCircleChatMessage.map { $0.applyingProfilePatch(patch) }
        latestDirectMessage = latestDirectMessage.map { $0.applyingProfilePatch(patch) }
        chatStore.applyUserProfilePatch(patch)
        syncChatMirrorsFromStore()
        objectWillChange.send()
    }

    private func applyCircleMembersRosterRealtime(circleDTO: CircleDTO, rosterUsers: [UserDTO]) {
        let previousMemberUserIds = Set(
            circles.first(where: { $0.id == circleDTO.id })?.members.map(\.id) ?? []
        )

        for u in rosterUsers {
            if let idx = allUsers.firstIndex(where: { $0.id == u.id }) {
                allUsers[idx] = u
            } else {
                allUsers.append(u)
            }
        }
        var usersByID = Dictionary(uniqueKeysWithValues: allUsers.map { ($0.id, $0) })
        for u in rosterUsers {
            usersByID[u.id] = u
        }

        let memberUserIds = Set(circleDTO.members.map(\.userId))
        let ownerId = circleDTO.ownerId
        let stillInSquad =
            (!currentUserID.isEmpty && memberUserIds.contains(currentUserID)) || ownerId == currentUserID

        if stillInSquad == false {
            if let idx = circles.firstIndex(where: { $0.id == circleDTO.id }) {
                circles.remove(at: idx)
                if selectedCircleID == circleDTO.id {
                    selectedCircleID = circles.first?.id ?? ""
                }
            }
            objectWillChange.send()
            subscribeChatRealtimeToCurrentCircles()
            cacheShareExtensionSquads()
            return
        }

        let mappedMembers = circleDTO.members.map { member in
            Self.mapFriend(user: usersByID[member.userId], userID: member.userId, role: member.role)
        }
        let owner = usersByID[ownerId]
        let updatedCircle = DriveCircle(
            id: circleDTO.id,
            name: circleDTO.name,
            subtitle: Self.subtitleForCircle(circleDTO, owner: owner),
            icon: Self.iconForCircle(name: circleDTO.name),
            accentColor: MapAccentPalette.color(fromStableSeed: circleDTO.id),
            ownerId: ownerId,
            photoUrl: circleDTO.photoUrl,
            members: mappedMembers
        )

        if let idx = circles.firstIndex(where: { $0.id == circleDTO.id }) {
            circles[idx] = updatedCircle
        } else {
            circles.append(updatedCircle)
            if selectedCircleID.isEmpty {
                selectedCircleID = updatedCircle.id
            }
        }
        objectWillChange.send()
        subscribeChatRealtimeToCurrentCircles()
        cacheShareExtensionSquads()

        let newMemberUserIds = memberUserIds.subtracting(previousMemberUserIds)
        if !newMemberUserIds.isEmpty {
            Task { await reconcileSquadChatAfterMembershipChange(circleID: circleDTO.id) }
        }
    }

    /// Fetches squad chat when roster grows so a missed WS still surfaces "{name} joined the squad".
    private func reconcileSquadChatAfterMembershipChange(circleID: String) async {
        let trimmed = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, circles.contains(where: { $0.id == trimmed }) else { return }
        let priorNewestID = chatStore.cachedSquadMessages(circleID: trimmed)?.last?.id
        do {
            let fetched = try await APIClient.shared.fetchCircleChatMessages(circleId: trimmed, limit: 50)
            _ = chatStore.reconcileSquadMessages(circleID: trimmed, fetchedMessages: fetched)
            squadChatTranscriptByCircleID[trimmed] = chatStore.cachedSquadMessages(circleID: trimmed) ?? []
            syncChatMirrorsFromStore()
            guard let newest = fetched.last,
                  newest.id != priorNewestID,
                  newest.messageType == "system",
                  newest.systemKind == "circle_member_joined",
                  newest.resolvedSenderUserId != currentUserID
            else { return }
            showToast(newest.body, icon: "person.crop.circle.badge.checkmark")
        } catch {
            OttoLog.api.error(
                "Squad chat reconcile after join failed circle=\(trimmed, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
        }
    }

    private func handleChatSocketPayload(_ data: Data) {
        guard let envelope = try? JSONDecoder().decode(ChatSocketEnvelope.self, from: data) else { return }
        switch envelope.type {
        case "ready":
            isChatRealtimeConnected = true
            chatRealtimeStatusMessage = nil
            subscribeChatRealtimeToCurrentCircles()
            subscribeDirectRealtimeToKnownConversations()
        case "circle.chat.subscribed", "presence.subscribed":
            isChatRealtimeConnected = true
            chatRealtimeStatusMessage = nil
        case "circle.chat.message", "circle.chat.updated":
            guard let message = envelope.message else { return }
            let inserted = chatStore.upsertSquadMessage(message, eventType: envelope.type, currentUserID: currentUserID)
            squadChatTranscriptByCircleID[message.circleId] = chatStore.cachedSquadMessages(circleID: message.circleId) ?? []
            syncChatMirrorsFromStore()
            if envelope.type == "circle.chat.message",
               message.messageType == "system",
               message.systemKind == "circle_member_joined",
               message.resolvedSenderUserId != currentUserID {
                showToast(message.body, icon: "person.crop.circle.badge.checkmark")
            }
            if envelope.type == "circle.chat.message" {
                EngagementFeedback.handleSquadChatThreadEngagementIfNeeded(
                    message,
                    currentUserId: currentUserID,
                    focusedCircleId: activeChatCircleID
                )
            }
        case "presence.updated":
            guard let presence = envelope.presence else { return }
            applyPresenceUpdate(presence)
        case "profile.progression.level_up":
            guard let levelUp = envelope.levelUp else { return }
            presentProfileLevelUp(levelUp)
        case "circle.invite.created":
            guard let invite = envelope.invite else { return }
            upsertIncomingCircleInvite(invite)
        case "user.profile.updated":
            guard let profile = envelope.profile else { return }
            applyUserProfileRealtimePatch(profile)
        case "circle.members.updated":
            guard let circleDTO = envelope.circle else { return }
            applyCircleMembersRosterRealtime(circleDTO: circleDTO, rosterUsers: envelope.rosterUsers ?? [])
        case "direct.message", "direct.updated":
            guard let message = envelope.directMessage else { return }
            let inserted = chatStore.upsertDirectMessage(message, eventType: envelope.type, currentUserID: currentUserID)
            directMessageTranscriptByConversationID[message.conversationId] = chatStore.cachedDirectMessages(conversationID: message.conversationId) ?? []
            syncChatMirrorsFromStore()
            if envelope.type == "direct.message", message.senderUserId != currentUserID {
                EngagementFeedback.handleDirectThreadEngagementIfNeeded(
                    message,
                    currentUserId: currentUserID,
                    focusedConversationId: activeDirectConversationID
                )
            }
        default:
            break
        }
    }

    private func upsertIncomingCircleInvite(_ invite: CircleInviteDTO) {
        guard invite.status == "pending" else { return }
        let existingIndex = myCircleInvites.firstIndex { $0.id == invite.id }
        if let existingIndex {
            myCircleInvites[existingIndex] = invite
        } else {
            myCircleInvites.insert(invite, at: 0)
            let inviter = invite.invitedByUser?.displayName ?? "a member"
            showToast("Squad invite from \(inviter)", icon: "bell.badge.fill")
        }
        lastKnownInviteIDs = myCircleInvites.map(\.id)
        lastKnownInviteCount = myCircleInvites.count
    }

    private func sendChatSocketJSON(_ payload: [String: String]) {
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: []),
              let text = String(data: data, encoding: .utf8) else { return }
        chatSocketTask?.send(.string(text)) { error in
            if let error {
                print("App chat WebSocket send failed: \(error)")
            }
        }
    }

    func dismissProfileLevelUp() {
        activeProfileLevelUp = nil
    }

    func previewProfileLevelUpModal() {
        presentProfileLevelUp(Self.previewProfileLevelUp(level: 16))
    }

    func previewProfileLevelUpModal(level: Int) {
        presentProfileLevelUp(Self.previewProfileLevelUp(level: level))
    }

    func schedulePreviewProfileLevelUpNotification() {
        schedulePreviewProfileLevelUpNotification(level: 16)
    }

    func schedulePreviewProfileLevelUpNotification(level: Int) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if let error {
                OttoLog.app.error("Preview notification permission failed: \(String(describing: error))")
                return
            }
            guard granted else { return }

            Task { @MainActor in
                let previewLevelUp = Self.previewProfileLevelUp(level: level)
                guard let levelUpObject = Self.notificationObject(for: previewLevelUp) else {
                    OttoLog.app.error("Preview notification levelUp payload failed to encode")
                    return
                }

                let content = UNMutableNotificationContent()
                content.title = "Level up"
                content.body = "You reached \(previewLevelUp.reachedDisplayName)"
                content.sound = .default
                content.userInfo = [
                    "type": "profile.progression.level_up",
                    "level": "\(previewLevelUp.progression.level)",
                    "tierId": previewLevelUp.progression.tierId,
                    "tierName": previewLevelUp.progression.tierName,
                    "levelUp": levelUpObject,
                ]

                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
                let request = UNNotificationRequest(
                    identifier: "preview-profile-progression-level-up-\(UUID().uuidString)",
                    content: content,
                    trigger: trigger
                )
                do {
                    try await UNUserNotificationCenter.current().add(request)
                } catch {
                    OttoLog.app.error("Preview notification scheduling failed: \(String(describing: error))")
                }
            }
        }
    }

    func handleRemoteNotificationTap(_ userInfo: [AnyHashable: Any]) {
        let type = userInfo["type"] as? String
        if type == "profile.progression.level_up" {
            guard let levelUp = decodeProfileLevelUp(from: userInfo["levelUp"]) else {
                OttoLog.app.error("Level-up push missing decodable levelUp payload")
                return
            }
            presentProfileLevelUp(levelUp)
            return
        }
        if type == "event.events_today" {
            navigateToEventsMyEvents()
            return
        }
        if type == "event.check_in" || type == "event.auto_check_in" || type == "circle.event.invited" {
            let rawRef: String? = {
                if let s = userInfo["eventRef"] as? String { return s }
                if let s = userInfo["eventId"] as? String { return s }
                if let s = userInfo["eventRef"] as? NSString { return s as String }
                if let s = userInfo["eventId"] as? NSString { return s as String }
                return nil
            }()
            let trimmed = rawRef?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !trimmed.isEmpty else {
                OttoLog.app.error("\(type ?? "?") push missing eventRef/eventId")
                return
            }
            navigateToEventDetail(eventRef: trimmed)
            return
        }
        if type == "direct.message" || type == "direct.message.reaction" {
            let senderId = pushNotificationStringValue(userInfo["senderUserId"])
            let conversationId = pushNotificationStringValue(userInfo["conversationId"])
            Task { @MainActor in
                if circles.isEmpty {
                    await refreshCircles()
                }
                await refreshDirectConversations()
                if let cid = conversationId,
                   let conv = directConversation(conversationID: cid),
                   let otherId = conv.otherUser?.id {
                    requestDirectMessageFocus(conversationID: cid, userID: otherId)
                } else if let senderId {
                    requestDirectMessageFocus(userID: senderId)
                } else {
                    OttoLog.app.error("direct.message push missing senderUserId and conversationId")
                }
            }
            return
        }
        if type == "circle.invite.received" {
            Task { @MainActor in
                await refreshMyCircleInvites()
                requestSquadsInvitesFocus()
            }
            return
        }
        if type == "circle.member.added" {
            guard let circleId = pushNotificationStringValue(userInfo["circleId"]) else {
                OttoLog.app.error("[push] circle.member.added missing circleId")
                return
            }
            Task { @MainActor in
                if !circles.contains(where: { $0.id == circleId }) {
                    await refreshCircles()
                }
                requestCircleFocus(circleID: circleId)
            }
            return
        }
        if type == "circle.chat.reply" || type == "circle.chat.new_message" || type == "circle.chat.mention"
            || type == "circle.chat.reaction"
        {
            guard let circleId = pushNotificationStringValue(userInfo["circleId"]) else {
                OttoLog.app.error("[push] squad chat \(type ?? "?") missing circleId")
                return
            }
            Task { @MainActor in
                if !circles.contains(where: { $0.id == circleId }) {
                    await refreshCircles()
                }
                requestCircleFocus(circleID: circleId)
            }
            return
        }
        if type == "presence.location_started" {
            guard let circleId = pushNotificationStringValue(userInfo["circleId"]),
                  let sharerId = pushNotificationStringValue(userInfo["userId"])
            else {
                OttoLog.app.error("presence.location_started push missing circleId or userId")
                return
            }
            Task { @MainActor in
                if !circles.contains(where: { $0.id == circleId }) {
                    await refreshCircles()
                }
                openMapForLocationSharingPush(circleID: circleId, sharerUserID: sharerId)
            }
            return
        }
    }

    /// Normalizes push `userInfo` values (string, NSString, or numeric ids) for deep-link routing.
    private func pushNotificationStringValue(_ value: Any?) -> String? {
        if let s = value as? String {
            let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let s = value as? NSString {
            let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : String(trimmed)
        }
        if let n = value as? NSNumber {
            return n.stringValue
        }
        return nil
    }

    private func presentProfileLevelUp(_ levelUp: ProfileLevelUpDTO) {
        activeProfileLevelUp = levelUp
        if levelUp.eventType != "preview" {
            profileProgressionRefreshTick &+= 1
        }
        TabSoundPlayer.shared.playLevelUp()
    }

    private func decodeProfileLevelUp(from value: Any?) -> ProfileLevelUpDTO? {
        guard let value else { return nil }
        do {
            let jsonObject: Any
            if let string = value as? String,
               let data = string.data(using: .utf8) {
                return try JSONDecoder().decode(ProfileLevelUpDTO.self, from: data)
            } else {
                jsonObject = normalizeNotificationJSONValue(value)
            }

            guard JSONSerialization.isValidJSONObject(jsonObject) else { return nil }
            let data = try JSONSerialization.data(withJSONObject: jsonObject, options: [])
            return try JSONDecoder().decode(ProfileLevelUpDTO.self, from: data)
        } catch {
            OttoLog.app.error("Failed to decode level-up push payload: \(String(describing: error))")
            return nil
        }
    }

    private func normalizeNotificationJSONValue(_ value: Any) -> Any {
        if let dict = value as? [AnyHashable: Any] {
            return Dictionary(uniqueKeysWithValues: dict.map { key, value in
                (String(describing: key), normalizeNotificationJSONValue(value))
            })
        }
        if let array = value as? [Any] {
            return array.map(normalizeNotificationJSONValue)
        }
        return value
    }

    private static func previewProfileLevelUp(level requestedLevel: Int) -> ProfileLevelUpDTO {
        let level = min(20, max(2, requestedLevel))
        let progression = previewProgression(level: level)
        let previousProgression = previewProgression(level: level - 1, pointsIntoLevel: 20)
        let nextProgression = level < 20 ? previewProgression(level: level + 1, pointsIntoLevel: 0) : nil

        return ProfileLevelUpDTO(
            eventType: "preview",
            pointsAwarded: max(10, progression.points - previousProgression.points),
            previousProgression: previousProgression,
            progression: progression,
            nextProgression: nextProgression,
            reachedDisplayName: previewDisplayName(for: progression),
            nextDisplayName: nextProgression.map(previewDisplayName(for:)),
            unlockedNewTier: progression.tierId != previousProgression.tierId
        )
    }

    private static func previewProgression(level: Int, pointsIntoLevel: Int = 240) -> ProfileProgressionDTO {
        let tier = previewTier(for: level)
        let isMaxLevel = level >= 20
        let currentLevelStartPoints = previewLevelStartPoints(level: level)
        let pointsRequired = isMaxLevel ? nil : tier.pointsPerLevel
        let cappedPointsIntoLevel = pointsRequired.map { min(max(0, pointsIntoLevel), max(0, $0 - 1)) } ?? 0
        let points = currentLevelStartPoints + cappedPointsIntoLevel

        return ProfileProgressionDTO(
            points: max(1, points),
            level: level,
            tierId: tier.id,
            tierName: tier.name,
            levelImageName: "Level\(level)",
            currentLevelStartPoints: currentLevelStartPoints,
            nextLevelAt: pointsRequired.map { currentLevelStartPoints + $0 },
            pointsIntoLevel: cappedPointsIntoLevel,
            pointsRequiredForLevel: pointsRequired,
            progress: pointsRequired.map { Double(cappedPointsIntoLevel) / Double($0) } ?? 1,
            isMaxLevel: isMaxLevel
        )
    }

    private static func previewLevelStartPoints(level: Int) -> Int {
        var total = 0
        for previewLevel in 1..<level {
            total += previewTier(for: previewLevel).pointsPerLevel ?? 0
        }
        return total
    }

    private static func previewTier(for level: Int) -> (id: String, name: String, minLevel: Int, pointsPerLevel: Int?) {
        switch level {
        case 1...4:
            return ("rookie", "Rookie", 1, 250)
        case 5...8:
            return ("qualifier", "Qualifier", 5, 500)
        case 9...12:
            return ("runner", "Runner", 9, 1000)
        case 13...16:
            return ("pacer", "Pacer", 13, 2000)
        case 17...19:
            return ("apex", "Apex", 17, 4000)
        default:
            return ("legend", "Legend", 20, nil)
        }
    }

    private static func previewDisplayName(for progression: ProfileProgressionDTO) -> String {
        guard !progression.isMaxLevel else { return progression.tierName }
        let tier = previewTier(for: progression.level)
        let ordinal = max(1, progression.level - tier.minLevel + 1)
        return "\(progression.tierName) \(previewRomanNumeral(ordinal))"
    }

    private static func previewRomanNumeral(_ value: Int) -> String {
        switch value {
        case 1: return "I"
        case 2: return "II"
        case 3: return "III"
        case 4: return "IV"
        default: return "\(value)"
        }
    }

    private static func notificationObject(for levelUp: ProfileLevelUpDTO) -> Any? {
        do {
            let data = try JSONEncoder().encode(levelUp)
            return try JSONSerialization.jsonObject(with: data, options: [])
        } catch {
            OttoLog.app.error("Failed to encode preview level-up payload: \(String(describing: error))")
            return nil
        }
    }

    func requestAuthOTP(phoneNumber: String) async -> Bool {
        let trimmed = phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Enter a valid US phone number."
            return false
        }
        errorMessage = nil
        signupChallengeToken = ""
        signupNeedsInviteCode = false
        signupAfterOtpStep = nil
        pendingSignupInviteCode = ""
        do {
            try await APIClient.shared.requestOTP(phoneNumber: trimmed)
            authPhoneNumber = trimmed
            return true
        } catch {
            errorMessage = "Failed to send verification code."
            return false
        }
    }

    func verifyAuthOTP(code: String) async {
        let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !authPhoneNumber.isEmpty else {
            errorMessage = "Request a verification code first."
            return
        }
        guard trimmedCode.count == 6 else {
            errorMessage = "Enter the 6-digit code."
            return
        }

        do {
            let dto = try await APIClient.shared.verifyOTP(
                phoneNumber: authPhoneNumber,
                code: trimmedCode
            )

            if let token = dto.token, let user = dto.user, let isNew = dto.isNewUser {
                APIClient.shared.setAuthToken(token)
                UserDefaults.standard.set(token, forKey: StorageKeys.authToken)
                UserDefaults.standard.set(user.id, forKey: StorageKeys.authUserID)
                cacheShareExtensionAuth(token: token, userID: user.id)
                currentUserID = user.id
                applyAutoCheckInPreferenceFromUser(user)
                signupChallengeToken = ""
                signupNeedsInviteCode = false
                signupAfterOtpStep = nil
                pendingSignupInviteCode = ""
                if isNew {
                    requiresOnboardingName = true
                    isAuthenticated = false
                } else {
                    requiresOnboardingName = false
                    await finishAuthenticatedSession()
                }
                return
            }

            if let challenge = dto.signupChallengeToken, !challenge.isEmpty {
                signupChallengeToken = challenge
                signupNeedsInviteCode = dto.needsInviteCode ?? false
                pendingSignupInviteCode = ""
                signupAfterOtpStep = signupNeedsInviteCode ? .inviteCode : .displayName
                return
            }

            errorMessage = "Unexpected sign-in response. Update the app or try again."
        } catch {
            errorMessage = "Verification failed. Check code and try again."
        }
    }

    func advanceSignupPastInvite(code rawCode: String) async {
        let trimmed = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        errorMessage = nil
        guard signupAfterOtpStep == .inviteCode else { return }
        guard signupNeedsInviteCode else {
            signupAfterOtpStep = .displayName
            return
        }
        guard !trimmed.isEmpty else {
            errorMessage = "Enter your invite code."
            return
        }
        guard !signupChallengeToken.isEmpty else {
            errorMessage = "Signup session expired. Request a new verification code."
            return
        }
        do {
            try await APIClient.shared.checkSignupInvite(
                signupChallengeToken: signupChallengeToken,
                inviteCode: trimmed
            )
            pendingSignupInviteCode = trimmed
            signupAfterOtpStep = .displayName
        } catch {
            let ns = error as NSError
            if ns.domain == "OttoAPI",
               let msg = ns.userInfo[NSLocalizedDescriptionKey] as? String,
               !msg.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            {
                errorMessage = msg.trimmingCharacters(in: .whitespacesAndNewlines)
                return
            }
            let desc = ns.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
            errorMessage = desc.isEmpty ? "Invalid or expired invite code." : desc
        }
    }

    func completeSignupWithDisplayName(_ displayName: String) async {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Please enter your name."
            return
        }
        guard signupAfterOtpStep == .displayName else {
            errorMessage = "Continue from the sign-in flow."
            return
        }
        guard !signupChallengeToken.isEmpty else {
            errorMessage = "Signup session expired. Request a new verification code."
            return
        }
        if signupNeedsInviteCode {
            let invite = pendingSignupInviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !invite.isEmpty else {
                errorMessage = "Enter your invite code."
                signupAfterOtpStep = .inviteCode
                return
            }
        }

        errorMessage = nil
        do {
            let invitePayload: String? = signupNeedsInviteCode
                ? pendingSignupInviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
                : nil
            let session = try await APIClient.shared.completeSignup(
                signupChallengeToken: signupChallengeToken,
                displayName: trimmed,
                inviteCode: invitePayload
            )
            APIClient.shared.setAuthToken(session.token)
            UserDefaults.standard.set(session.token, forKey: StorageKeys.authToken)
            UserDefaults.standard.set(session.user.id, forKey: StorageKeys.authUserID)
            cacheShareExtensionAuth(token: session.token, userID: session.user.id)
            currentUserID = session.user.id
            applyAutoCheckInPreferenceFromUser(session.user)
            signupChallengeToken = ""
            signupNeedsInviteCode = false
            signupAfterOtpStep = nil
            pendingSignupInviteCode = ""
            requiresOnboardingName = false
            OttoAnalytics.logSignUpComplete()
            if let invitePayload,
               let pendingToken = pendingInviteToken,
               !invitePayload.isEmpty,
               !pendingToken.isEmpty,
               invitePayload.caseInsensitiveCompare(pendingToken) == .orderedSame
            {
                // Signup redeemed the same code as a stored squad invite deep link; skip re-resolve.
                clearPendingSquadInvite()
            }
            await finishAuthenticatedSession()
        } catch {
            errorMessage = "Couldn’t finish signup. Check your invite code or try again."
        }
    }

    func completeOnboardingName(_ displayName: String) async {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Please enter your name."
            return
        }
        guard !currentUserID.isEmpty else {
            errorMessage = "User session not ready. Try signing in again."
            return
        }
        if await updateCurrentUserDisplayName(trimmed) {
            requiresOnboardingName = false
            OttoAnalytics.logOnboardingNameComplete()
            await finishAuthenticatedSession()
        }
    }

    func deleteAccount(confirmation: String) async {
        guard !currentUserID.isEmpty else {
            errorMessage = "User session not ready. Try signing in again."
            return
        }

        do {
            try await APIClient.shared.deleteAccount(userId: currentUserID, confirmation: confirmation)
            logout()
        } catch {
            errorMessage = "Couldn’t delete your account. Please try again."
        }
    }

    func logout() {
        OttoAnalytics.clearUserID()
        APIClient.shared.setAuthToken(nil)
        UserDefaults.standard.removeObject(forKey: StorageKeys.authToken)
        UserDefaults.standard.removeObject(forKey: StorageKeys.authUserID)
        clearShareExtensionCache()
        currentUserID = ""
        currentUser = nil
        isAuthenticated = false
        requiresOnboardingName = false
        signupChallengeToken = ""
        signupNeedsInviteCode = false
        signupAfterOtpStep = nil
        pendingSignupInviteCode = ""
        circles = []
        publicPresenceMembers = []
        selectedCircleID = ""
        sharingCircleIDs = []
        pendingInvitesByCircleID = [:]
        recentDrives = []
        clearPendingDriveArchives()
        savedPlaces = []
        pendingMapFocus = nil
        pendingMapEventPreview = nil
        pendingCircleFocus = nil
        pendingSquadsInvitesFocus = nil
        garageTabFocusRequest = nil
        pendingProfileFocus = nil
        pendingDirectMessageFocus = nil
        pendingEventFocus = nil
        pendingEventsMyEventsFocus = nil
        pendingLocationSharingFocus = nil
        pendingSharingSheetPresentation = nil
        latestCircleChatMessage = nil
        latestDirectMessage = nil
        chatAttachmentHydratedEventsById = [:]
        chatAttachmentHydrationInFlight.removeAll()
        squadGoingEventsForCheckIn = []
        foregroundAutoCheckInAttemptedEventIDs.removeAll()
        squadChatTranscriptByCircleID.removeAll()
        directMessageTranscriptByConversationID.removeAll()
        chatStore.clearAll()
        unreadChatCountsByCircleID = [:]
        directConversationsByUserID = [:]
        unreadDirectMessageCountsByConversationID = [:]
        activeDirectConversationID = nil
        activeChatCircleID = nil
        PushFocusBridge.activeChatCircleId = nil
        PushFocusBridge.activeDirectConversationId = nil
        disconnectChatRealtime()
        didRefreshSquadUnreadHeadsThisLaunch = false
        let sp = Self.sharingPersistenceDefaults
        sp.removeObject(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel)
        sp.removeObject(forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary)
        isSharingEnabled = false
        sharingTiedToActiveDrive = false
        sharingSessionStartedAt = nil
        isDrivingOnlyBroadcastPaused = false
        persistSharingState()
        autoEventCheckInEnabled = Self.defaultAutoEventCheckInEnabled
        UserDefaults.standard.removeObject(forKey: StorageKeys.autoEventCheckInEnabled)
        sharingSafetyDisclaimerAcknowledged = false
        UserDefaults.standard.removeObject(forKey: StorageKeys.sharingSafetyDisclaimerAcknowledged)
        showPublicGoingEventsOnProfile = Self.defaultShowPublicGoingEventsOnProfile
        UserDefaults.standard.removeObject(forKey: StorageKeys.showPublicGoingEventsOnProfile)
        driveStatsVisibility = Self.defaultDriveStatsVisibility
        UserDefaults.standard.removeObject(forKey: StorageKeys.driveStatsVisibility)
        blockedUserIDs = []
        activeDriveID = nil
        isMapScreenActive = false
        isEventsScreenActive = false
        isMapRouteSessionActive = false
        syncHomeScreenChatIconBadgeWithBackend()
    }

    private func restoreSessionFromToken() async {
        do {
            let me = try await APIClient.shared.fetchMe()
            currentUserID = me.id
            TimeZoneSync.primeCacheFromServerTimeZone(me.timeZone)
            chatStore.bindUnreadTracking(currentUserID: me.id)
            UserDefaults.standard.set(me.id, forKey: StorageKeys.authUserID)
            applyAutoCheckInPreferenceFromUser(me)
            if let token = APIClient.shared.authToken {
                cacheShareExtensionAuth(token: token, userID: me.id)
            }
            await finishAuthenticatedSession()
        } catch {
            logout()
        }
    }

    func handleIncomingURL(_ url: URL) {
        if url.scheme?.lowercased() == "otto" {
            let host = url.host?.lowercased() ?? ""
            if host == "map" {
                requestOpenMapTabOnly()
                return
            }
            if host == "share" {
                requestPresentSharingSheetFromDeepLink()
                return
            }
        }

        if let token = Self.inviteToken(from: url) {
            let squadId = Self.inviteSquadId(from: url)
            storePendingSquadInvite(code: token, squadId: squadId)
            Task { await processPendingSquadInviteIfNeeded() }
            return
        }

        if let userID = Self.memberID(from: url) {
            pendingProfileFocus = PendingProfileFocus(id: UUID(), userID: userID)
            return
        }

        if let eventRef = Self.eventRef(from: url) {
            navigateToEventDetail(eventRef: eventRef)
        }
    }

    private func storePendingSquadInvite(code: String, squadId: String?) {
        pendingInviteToken = code
        pendingInviteSquadId = squadId
        pendingSignupInviteCode = code
        UserDefaults.standard.set(code, forKey: StorageKeys.pendingSquadInviteCode)
        if let squadId, !squadId.isEmpty {
            UserDefaults.standard.set(squadId, forKey: StorageKeys.pendingSquadInviteSquadId)
        } else {
            UserDefaults.standard.removeObject(forKey: StorageKeys.pendingSquadInviteSquadId)
        }
    }

    private func clearPendingSquadInvite() {
        pendingInviteToken = nil
        pendingInviteSquadId = nil
        squadInvitePrompt = nil
        UserDefaults.standard.removeObject(forKey: StorageKeys.pendingSquadInviteCode)
        UserDefaults.standard.removeObject(forKey: StorageKeys.pendingSquadInviteSquadId)
    }

    func dismissSquadInvitePrompt() {
        clearPendingSquadInvite()
    }

    func acceptSquadInvitePrompt() async {
        guard let prompt = squadInvitePrompt else { return }
        let token = prompt.token
        isAcceptingSquadInvitePrompt = true
        defer { isAcceptingSquadInvitePrompt = false }
        do {
            // Squad is bound on the invite record; circleId is optional (legacy query fallback).
            try await APIClient.shared.acceptInviteLink(
                token: token,
                circleId: prompt.circle?.id ?? pendingInviteSquadId
            )
            clearPendingSquadInvite()
            OttoAnalytics.logSquadJoined(source: "invite_link")
            await refreshCircles()
        } catch {
            errorMessage = String(localized: "squad_invite_accept_failed_error")
        }
    }

    /// Resolves a stored squad invite link and presents [squadInvitePrompt]; join runs only from [acceptSquadInvitePrompt].
    func processPendingSquadInviteIfNeeded() async {
        await resolvePendingInviteIfPossible()
    }

    private func resolvePendingInviteIfPossible() async {
        guard isAuthenticated else { return }
        guard let token = pendingInviteToken, !token.isEmpty else { return }
        if squadInvitePrompt != nil { return }
        await refreshCircles()
        // Legacy deep links may still include ?squad=; new links encode squad on the invite record.
        let legacySquadId = pendingInviteSquadId
        if clearPendingSquadInviteIfAlreadyMember(matchingSquadId: legacySquadId) {
            return
        }
        do {
            let resolved = try await APIClient.shared.resolveInviteLink(
                token: token,
                squadId: legacySquadId
            )
            guard let circle = resolved.circle else {
                clearPendingSquadInvite()
                return
            }
            if circles.contains(where: { $0.id == circle.id }) {
                clearPendingSquadInvite()
                return
            }
            squadInvitePrompt = resolved
        } catch {
            // Signup may have redeemed the link and auto-joined; resolve then fails with no uses left.
            if clearPendingSquadInviteIfAlreadyMember(matchingSquadId: legacySquadId) {
                return
            }
            // Android parity: keep pending for a later retry; do not surface a generic Map alert here.
        }
    }

    /// Returns true when the pending deep link squad is already in `circles` and state was cleared.
    private func clearPendingSquadInviteIfAlreadyMember(matchingSquadId: String?) -> Bool {
        let squadId = matchingSquadId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !squadId.isEmpty else { return false }
        guard circles.contains(where: { $0.id == squadId }) else { return false }
        clearPendingSquadInvite()
        return true
    }

    private func finishAuthenticatedSession() async {
        isAuthenticated = true
        if !currentUserID.isEmpty {
            OttoAnalytics.setUserID(currentUserID)
        }
        await recordDailyLaunchProgressionEvent()
        requestPushNotificationsIfNeeded()
        await ensurePushDeviceTokenRegisteredWithBackend()
        await refreshCircles()
        await refreshDirectConversations()
        await reconcileChatUnreadStateFromNetworkIfNeeded()
        connectChatRealtimeIfNeeded()
        await refreshGarage()
        await refreshRecentDrives()
        await refreshUpcomingEvents()
        await refreshCommunityEvents()
        await refreshSquadGoingEventsForCheckIn()
        await refreshSavedPlaces()
        await processPendingSquadInviteIfNeeded()
        syncHomeScreenChatIconBadgeWithBackend()
    }

    private static func inviteSquadId(from url: URL) -> String? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let squad = components.queryItems?.first(where: { $0.name == "squad" || $0.name == "circleId" })?.value?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return squad.isEmpty ? nil : squad
    }

    private func recordDailyLaunchProgressionEvent() async {
        do {
            let response = try await APIClient.shared.recordProgressionEvent(type: "daily_launch")
            if let levelUp = response.levelUp {
                presentProfileLevelUp(levelUp)
            }
        } catch {
            OttoLog.app.error("Daily launch progression event failed: \(String(describing: error))")
        }
    }

    /// Opens the Map tab and centers on a saved place (or any coordinate). `MapScreen` consumes the pending value on appear.
    func requestMapTabCenteredOn(
        latitude: Double,
        longitude: Double,
        eventID: String? = nil,
        eventPreview: EventDTO? = nil,
        savedPlaceID: String? = nil,
        savedPlaceSnapshot: SavedPlaceDTO? = nil
    ) {
        let trimmedEventID = eventID.flatMap { id in
            let trimmed = id.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        let trimmedSavedPlaceID = savedPlaceID.flatMap { id in
            let trimmed = id.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        pendingMapEventPreview = eventPreview
        pendingMapFocus = PendingMapFocus(
            id: UUID(),
            latitude: latitude,
            longitude: longitude,
            eventID: trimmedEventID,
            savedPlaceID: trimmedSavedPlaceID,
            savedPlaceSnapshot: savedPlaceSnapshot
        )
    }

    func consumePendingMapFocus() -> PendingMapFocus? {
        let value = pendingMapFocus
        pendingMapFocus = nil
        pendingMapEventPreview = nil
        return value
    }

    /// Resolves an event for the map peek sheet (detail snapshot, feeds, hydration).
    func resolvedEventForMapPeek(eventID: String, preview: EventDTO?) -> EventDTO? {
        if let preview, preview.id == eventID { return preview }
        if let match = mapDiscoveryEvents.first(where: { $0.id == eventID }) { return match }
        if let match = upcomingEvents.first(where: { $0.id == eventID }) { return match }
        if let match = communityEvents.first(where: { $0.id == eventID }) { return match }
        return chatAttachmentHydratedEventsById[eventID]
    }

    func requestMapTabRouteFocus(route: SavedRouteDTO, startDrive: Bool = false) {
        pendingMapRouteSelection = PendingMapRouteSelection(
            id: UUID(),
            route: route,
            startDriveAfterFocus: startDrive
        )
    }

    func consumePendingMapRouteSelection() -> PendingMapRouteSelection? {
        let value = pendingMapRouteSelection
        pendingMapRouteSelection = nil
        return value
    }

    func requestCircleFocus(circleID: String) {
        guard circles.contains(where: { $0.id == circleID }) else { return }
        selectedCircleID = circleID
        pendingCircleFocus = PendingCircleFocus(id: UUID(), circleID: circleID, openChatTab: false)
    }

    /// Opens squad detail on the Chat tab (share-to-chat and similar).
    func requestSquadChatFocus(circleID: String) {
        guard circles.contains(where: { $0.id == circleID }) else { return }
        selectedCircleID = circleID
        pendingCircleFocus = PendingCircleFocus(id: UUID(), circleID: circleID, openChatTab: true)
    }

    func requestSquadsInvitesFocus() {
        pendingSquadsInvitesFocus = PendingSquadsInvitesFocus(id: UUID())
    }

    func consumePendingSquadsInvitesFocus() -> PendingSquadsInvitesFocus? {
        let value = pendingSquadsInvitesFocus
        pendingSquadsInvitesFocus = nil
        return value
    }

    func requestDirectMessageFocus(userID: String) {
        guard canDirectMessage(userID: userID) else { return }
        pendingDirectMessageFocus = PendingDirectMessageFocus(id: UUID(), conversationID: nil, userID: userID)
    }

    func requestDirectMessageFocus(conversationID: String, userID: String?) {
        let trimmedConversation = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedConversation.isEmpty else { return }
        pendingDirectMessageFocus = PendingDirectMessageFocus(
            id: UUID(),
            conversationID: trimmedConversation,
            userID: userID
        )
    }

    /// Resolved conversation for inbox / push routing (1:1 threads).
    func directConversation(conversationID: String) -> DirectConversationDTO? {
        let trimmed = conversationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return directConversationsByUserID.values.first { $0.id == trimmed }
    }

    /// Other participant for a cached conversation row (for navigation fallback).
    func directConversationRecipientUserID(conversationID: String) -> String? {
        guard let conversation = directConversation(conversationID: conversationID) else { return nil }
        if let otherUserID = conversation.otherUser?.id, !otherUserID.isEmpty {
            return otherUserID
        }
        return conversation.participantUserIds.first {
            !$0.isEmpty && $0 != currentUserID
        }
    }

    func normalizedDirectConversation(_ conversation: DirectConversationDTO) -> DirectConversationDTO? {
        if let otherUser = conversation.otherUser, !otherUser.id.isEmpty {
            return conversation
        }
        guard let otherUserID = conversation.participantUserIds.first(where: {
            !$0.isEmpty && $0 != currentUserID
        }) else {
            return nil
        }
        return DirectConversationDTO(
            id: conversation.id,
            participantUserIds: conversation.participantUserIds,
            otherUser: userSummaryForDirectOtherUser(userID: otherUserID),
            lastMessageAt: conversation.lastMessageAt,
            conversationType: conversation.conversationType,
            lastMessage: conversation.lastMessage
        )
    }

    private func userSummaryForDirectOtherUser(userID: String) -> DirectConversationDTO.UserSummaryDTO {
        if let user = allUsers.first(where: { $0.id == userID }) {
            return DirectConversationDTO.UserSummaryDTO(
                id: user.id,
                displayName: user.displayName,
                avatarUrl: user.avatarUrl,
                mapAccentKey: user.mapAccentKey
            )
        }
        if let member = circles.flatMap(\.members).first(where: { $0.id == userID }) {
            return DirectConversationDTO.UserSummaryDTO(
                id: member.id,
                displayName: member.name,
                avatarUrl: member.avatarUrl,
                mapAccentKey: nil
            )
        }
        return DirectConversationDTO.UserSummaryDTO(
            id: userID,
            displayName: nil,
            avatarUrl: nil,
            mapAccentKey: nil
        )
    }

    /// Threads returned by the server already exclude empty pairs; this also drops locally cached
    /// get-or-create rows until `lastMessageAt` reflects at least one message.
    var sortedDirectConversations: [DirectConversationDTO] {
        directConversationsByUserID.values
            .filter { $0.lastMessageAt != nil }
            .sorted {
                let lhs = $0.lastMessageAt ?? .distantPast
                let rhs = $1.lastMessageAt ?? .distantPast
                if lhs != rhs { return lhs > rhs }
                return $0.id < $1.id
            }
    }

    func consumePendingCircleFocus() -> PendingCircleFocus? {
        let value = pendingCircleFocus
        pendingCircleFocus = nil
        return value
    }

    func consumePendingProfileFocus() -> PendingProfileFocus? {
        let value = pendingProfileFocus
        pendingProfileFocus = nil
        return value
    }

    func consumePendingDirectMessageFocus() -> PendingDirectMessageFocus? {
        let value = pendingDirectMessageFocus
        pendingDirectMessageFocus = nil
        return value
    }

    func navigateToEventDetail(eventRef: String) {
        let trimmed = eventRef.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        pendingEventFocus = PendingEventFocus(id: UUID(), eventRef: trimmed)
    }

    /// Daily events digest push: Events tab → My Events sub-tab.
    func navigateToEventsMyEvents() {
        pendingEventsMyEventsFocus = PendingEventsMyEventsFocus(id: UUID())
    }

    func consumePendingEventsMyEventsFocus() -> PendingEventsMyEventsFocus? {
        let value = pendingEventsMyEventsFocus
        pendingEventsMyEventsFocus = nil
        return value
    }

    /// After a teammate starts sharing location (push). Opens Map tab via `RootTabView` + `MapScreen` consumes focus.
    func openMapForLocationSharingPush(circleID: String, sharerUserID: String) {
        let trimmedCircle = circleID.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedSharer = sharerUserID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedCircle.isEmpty, !trimmedSharer.isEmpty else { return }
        guard circles.contains(where: { $0.id == trimmedCircle }) else { return }
        selectedCircleID = trimmedCircle
        pendingLocationSharingFocus = PendingLocationSharingFocus(
            id: UUID(),
            circleID: trimmedCircle,
            sharerUserID: trimmedSharer
        )
    }

    func consumePendingLocationSharingFocus() -> PendingLocationSharingFocus? {
        let value = pendingLocationSharingFocus
        pendingLocationSharingFocus = nil
        return value
    }

    func consumePendingEventFocus() -> PendingEventFocus? {
        let value = pendingEventFocus
        pendingEventFocus = nil
        return value
    }

    func refreshSavedPlaces() async {
        guard !currentUserID.isEmpty else {
            savedPlaces = []
            return
        }
        do {
            savedPlaces = try await APIClient.shared.fetchMySavedPlaces()
        } catch {
            savedPlaces = []
        }
    }

    func createSavedPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        placeKind: String,
        source: String = "ios",
        poiCategory: String?,
        addressSummary: String?
    ) async throws -> SavedPlaceDTO {
        let created = try await APIClient.shared.createSavedPlace(
            name: name,
            latitude: latitude,
            longitude: longitude,
            placeKind: placeKind,
            source: source,
            poiCategory: poiCategory,
            addressSummary: addressSummary
        )
        await refreshSavedPlaces()
        return created
    }

    func deleteSavedPlace(placeId: String) async throws {
        try await APIClient.shared.deleteSavedPlace(placeId: placeId)
        await refreshSavedPlaces()
        presentDeletedToast(for: "Place")
    }

    private static func inviteToken(from url: URL) -> String? {
        let components = url.pathComponents
        if let idx = components.firstIndex(of: "invite"),
           components.indices.contains(idx + 1) {
            let segment = components[idx + 1]
            if segment == "links" {
                if components.indices.contains(idx + 2) {
                    let token = components[idx + 2]
                    return token.isEmpty ? nil : token
                }
                return nil
            }
            return segment.isEmpty ? nil : segment
        }
        if let idx = components.firstIndex(of: "invite-links"),
           components.indices.contains(idx + 1) {
            let token = components[idx + 1]
            return token.isEmpty ? nil : token
        }
        return nil
    }

    private static func memberID(from url: URL) -> String? {
        let components = url.pathComponents
        guard let idx = components.firstIndex(of: "m"),
              components.indices.contains(idx + 1) else { return nil }
        let value = components[idx + 1]
        return value.isEmpty ? nil : value
    }

    private static func eventRef(from url: URL) -> String? {
        let components = url.pathComponents
        guard let idx = components.firstIndex(of: "e"),
              components.indices.contains(idx + 1) else { return nil }
        let value = components[idx + 1]
        return value.isEmpty ? nil : value
    }
}

private final class ChatSocketDelegate: NSObject, URLSessionWebSocketDelegate {
    private let label: String

    init(label: String) {
        self.label = label
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        #if DEBUG
        print("\(label) opened protocol=\(`protocol` ?? "none")")
        #endif
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        #if DEBUG
        let reasonText = reason.flatMap { String(data: $0, encoding: .utf8) } ?? "none"
        print("\(label) closed code=\(closeCode.rawValue) reason=\(reasonText)")
        #endif
    }
}
