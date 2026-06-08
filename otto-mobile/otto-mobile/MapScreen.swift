import CoreLocation
import MapboxMaps
import MapKit
import os
import SwiftUI
import Combine
import Foundation
import Turf
import UIKit

/// One shared timer — per-view `Timer.publish…autoconnect()` can be recreated on every render and stack subscriptions.
private enum MapScreenPresenceTimer {
    #if targetEnvironment(simulator)
    /// Simulator: less presence traffic + lighter main-queue load while MapKit is already heavy.
    static let tick = Timer.publish(every: 12, on: .main, in: .common).autoconnect()
    #else
    static let tick = Timer.publish(every: 5, on: .main, in: .common).autoconnect()
    #endif
}

private enum MapScreenSmoothingTimer {
    /// Smooth friend marker movement between presence/GPS updates.
    static let tick = Timer.publish(every: 0.20, on: .main, in: .common).autoconnect()
}

private enum SharingDurationPreset: Hashable {
    case minutes(Int)
    case hours(Int)
    case endOfDay

    var title: String {
        switch self {
        case .minutes(let minutes): return "\(minutes) min"
        case .hours(let hours): return hours == 1 ? "1 hour" : "\(hours) hours"
        case .endOfDay: return "End of day"
        }
    }

    func seconds(from now: Date = Date()) -> TimeInterval {
        switch self {
        case .minutes(let minutes):
            return TimeInterval(minutes * 60)
        case .hours(let hours):
            return TimeInterval(hours * 60 * 60)
        case .endOfDay:
            let endOfDay = Calendar.current.dateInterval(of: .day, for: now)?.end
                ?? Calendar.current.startOfDay(for: now).addingTimeInterval(24 * 60 * 60)
            return max(60, endOfDay.timeIntervalSince(now))
        }
    }
}

private struct DriveLinePoint: Identifiable, Hashable {
    let id = UUID()
    let latitude: Double
    let longitude: Double
    let markerType: DriveLineMarkerType

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

private struct DriveLine {
    let id: String?
    let name: String
    let colorKey: DriveLineColorKey
    let points: [DriveLinePoint] // user-selected waypoints
    let roadCoordinates: [CLLocationCoordinate2D] // snapped road path
}

private struct SelectedRouteMapPoint: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let markerType: String?
    let index: Int
}

struct DriveChatShareContext: Identifiable {
    let id: String
    let previewTitle: String
    let previewDistanceMeters: Double
    let previewDriveTimeSeconds: TimeInterval
    let previewCompletedAt: Date
    let lockedCircleID: String?
    let mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput?

    init(
        driveId: String,
        previewTitle: String,
        previewDistanceMeters: Double,
        previewDriveTimeSeconds: TimeInterval,
        previewCompletedAt: Date = Date(),
        lockedCircleID: String? = nil,
        mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput? = nil
    ) {
        self.id = driveId
        self.previewTitle = previewTitle
        self.previewDistanceMeters = previewDistanceMeters
        self.previewDriveTimeSeconds = previewDriveTimeSeconds
        self.previewCompletedAt = previewCompletedAt
        self.lockedCircleID = lockedCircleID
        self.mapPreviewSnapshotInput = mapPreviewSnapshotInput
    }
}

private struct FriendProximityGroup: Identifiable {
    let members: [FriendLocation]
    let coordinate: CLLocationCoordinate2D

    var id: String {
        members.map(\.id).sorted().joined(separator: "|")
    }
}

private struct FriendDwellState {
    var anchor: CLLocationCoordinate2D
    var enteredAt: Date
    var lastSeenAt: Date
}

private struct PresenceSnapshot {
    let coordinate: CLLocationCoordinate2D
    let speedMph: Int
    let isActive: Bool
}

private struct EventRadiusOverlay: Identifiable {
    let id: String
    let polygon: Polygon
}

private struct MapViewportLayoutSizeKey: PreferenceKey {
    static var defaultValue: CGSize = CGSize(width: 390, height: 700)

    static func reduce(value: inout CGSize, nextValue: () -> CGSize) {
        value = nextValue()
    }
}

private enum MapCameraFollowMode: Equatable {
    case manual
    case followSelf
    case followFriend(String)
    case followSquad(String)

    var followedFriendID: String? {
        guard case .followFriend(let id) = self else { return nil }
        return id
    }

    var followedSquadID: String? {
        guard case .followSquad(let id) = self else { return nil }
        return id
    }

    var isFollowingSelf: Bool {
        if case .followSelf = self { return true }
        return false
    }

    var isFollowingAnyTarget: Bool {
        self != .manual
    }
}

private enum DriveLineColorKey: String, CaseIterable {
    case violet
    case pink
    case cyan
    case amber
    case mint
    case orange

    var primary: Color {
        switch self {
        case .violet: return Color(red: 0.66, green: 0.31, blue: 1.0)
        case .pink: return Color(red: 0.98, green: 0.24, blue: 0.70)
        case .cyan: return Color(red: 0.12, green: 0.82, blue: 1.0)
        case .amber: return Color(red: 1.0, green: 0.68, blue: 0.20)
        case .mint: return Color(red: 0.22, green: 0.92, blue: 0.68)
        case .orange: return Color(red: 1.0, green: 0.45, blue: 0.20)
        }
    }

    var secondary: Color {
        switch self {
        case .violet: return Color(red: 0.90, green: 0.45, blue: 1.0)
        case .pink: return Color(red: 1.0, green: 0.36, blue: 0.82)
        case .cyan: return Color(red: 0.46, green: 0.96, blue: 1.0)
        case .amber: return Color(red: 1.0, green: 0.84, blue: 0.40)
        case .mint: return Color(red: 0.56, green: 1.0, blue: 0.84)
        case .orange: return Color(red: 1.0, green: 0.62, blue: 0.34)
        }
    }
}

private enum DriveLineMarkerType: String, CaseIterable {
    case waypoint
    case stop
    case eat
    case gas

    var label: String {
        switch self {
        case .waypoint: return "Checkpoint"
        case .stop: return "Stop"
        case .eat: return "Eat"
        case .gas: return "Gas"
        }
    }

    var symbol: String {
        switch self {
        case .waypoint: return "circle.fill"
        case .stop: return "mappin.and.ellipse"
        case .eat: return "fork.knife"
        case .gas: return "fuelpump.fill"
        }
    }

    var color: Color {
        switch self {
        case .waypoint: return .purple
        case .stop: return .orange
        case .eat: return .mint
        case .gas: return .blue
        }
    }

    var next: DriveLineMarkerType {
        let all = Self.allCases
        guard let idx = all.firstIndex(of: self) else { return .waypoint }
        return all[(idx + 1) % all.count]
    }
}

private enum DriveLineWizardStep {
    case selectStart
    case addWaypoints
    case selectFinish
}

private enum PendingDriveStartContinuation: Equatable {
    case goLive
    case quickDrive
    case routeDrive(SavedRouteDTO)
}

struct MapScreen: View {
    private static let currentUserTrackingSpan = MKCoordinateSpan(latitudeDelta: 0.006, longitudeDelta: 0.006)
    private static let defaultTrackingSpan = MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)

    /// Starting from a real region avoids blank/gray map states before GPS produces a fix.
    private static let fallbackRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
        span: MKCoordinateSpan(latitudeDelta: 0.075, longitudeDelta: 0.075)
    )
    private static let selectedRouteLineSourceID = "selected-route-line"
    private static let routeDistanceFormatter: MeasurementFormatter = {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .naturalScale
        formatter.unitStyle = .medium
        formatter.numberFormatter.maximumFractionDigits = 1
        return formatter
    }()

    private enum LayerPrefs {
        static let showDrives = "MapScreen.showDrivesLayer"
        static let showPublic = "MapScreen.showPublicCircleLayer"
        static let showMyPlaces = "MapScreen.showMyPlacesLayer"
        static let showEvents = "MapScreen.showEventsLayer"
        static let showRaceTracks = "MapScreen.showRaceTracksLayer"
        static let showTraffic = "MapScreen.showTrafficLayer"
        static let visibleCircleIDs = "MapScreen.visibleCircleLayerIDs"
        static let visibleDriveLineIDs = "MapScreen.visibleDriveLineIDs"
    }

    private static func mapLocationEducationDefaultsKey(userID: String) -> String {
        let slug = userID.trimmingCharacters(in: .whitespacesAndNewlines)
        return "otto.mapLocationEducationSeen." + (slug.isEmpty ? "anonymous" : slug)
    }

    let isActive: Bool
    @AppStorage(OttoDebugSettings.mapLocationOverlayKey) private var mapLocationDiagnosticsEnabled = false
    @AppStorage(OttoDebugSettings.routeCheckpointMapOverlayKey) private var routeCheckpointMapDebugEnabled = true
    @State private var checkpointDebugMapboxVisible: [Int: Bool] = [:]

    private let showAllMembersForNow = true
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @EnvironmentObject private var raceTracksDatasetStore: RaceTracksDatasetStore
    @State private var mapViewport: Viewport = OttoMapboxCamera.viewport(for: Self.fallbackRegion)
    @StateObject private var travelSurfaceTracker = TravelSurfaceTracker()
    @State private var mapboxMap: MapboxMap?
    @State private var wasMapboxRenderingSuspended = false
    @State private var mapCenterCoordinate: CLLocationCoordinate2D = Self.fallbackRegion.center
    @State private var currentLatitudeDelta: Double = Self.fallbackRegion.span.latitudeDelta
    @State private var markerLODLatitudeDelta: Double = OttoMapboxCamera.visibleLatitudeDeltaDegrees(
        for: Self.fallbackRegion
    )
    @State private var latestMarkerLODRegion: MKCoordinateRegion = Self.fallbackRegion
    @State private var lastObservedMapRegion: MKCoordinateRegion = Self.fallbackRegion
    @State private var markerLODDebounceTask: Task<Void, Never>?
    @State private var mapPreviewSession: MapPreviewSession?
    @State private var chatSharedPlacePeekMarkers: [SavedPlaceDTO] = []
    @State private var presentedPeerProfileFocus: PresentedPeerProfileFocus?
    @State private var isShowingCirclePicker = false
    @State private var sharingNow = Date()
    @State private var sharingDraftCircleIDs: Set<String> = []
    @State private var sharingDraftDurationSeconds: TimeInterval = 3600
    @State private var sharingDraftDurationPreset: SharingDurationPreset = .hours(1)
    @State private var sharingDraftMode: AppState.SharingSessionMode = .shareNow
    @State private var sharingDraftSaveDrive = false
    @State private var quickRouteRecordDriveDraft = true
    @State private var quickRouteShareLocationDraft = false
    @State private var quickRouteShareCircleIDsDraft: Set<String> = []
    @State private var pendingDriveStartContinuation: PendingDriveStartContinuation?
    @State private var isShowingDriveSafetyDisclaimer = false
    @State private var isShowingSharingSquadRequiredAlert = false
    @State private var showMapLocationPrimer = false
    @State private var pendingMapLocationPermission = false
    @State private var showMapLocationDeniedModal = false
    @State private var pendingSharingAfterLocationPermission = false
    @State private var pendingSharingAfterMotionPermission = false
    @State private var showSharingLocationDeniedModal = false
    @State private var showSharingMotionDeniedModal = false
    @State private var showDriveBackgroundLocationPrimer = false
    @State private var pendingBackgroundLocationForDrive = false
    @State private var didShowDriveForegroundOnlyBackgroundToast = false
    @State private var isShowingFriendSearch = false
    @State private var isShowingLayers = false
    @State private var isShowingRoutesMenu = false
    @State private var isShowingRouteBuilder = false
    @State private var preRouteBuilderMapViewport: Viewport?
    @State private var preRouteBuilderMapRegion: MKCoordinateRegion?
    @State private var pendingRouteBuilderMapRestoreRegion: MKCoordinateRegion?
    @State private var pendingRouteBuilderMapRestoreViewport: Viewport?
    @State private var didApplyRouteBuilderSavedRouteToMap = false
    @State private var selectedRoute: SavedRouteDTO?
    @State private var routeForEditing: SavedRouteDTO?
    @State private var isQuickDriveDockVisible = false
    @State private var routeToRename: SavedRouteDTO?
    @State private var routeNameDraft = ""
    @State private var routeToDelete: SavedRouteDTO?
    @State private var showRouteStartDistanceWarning = false
    @State private var isShowingStopDriveConfirmation = false
    @State private var driveCompleteSummary: DriveCompleteSummary?
    @State private var completedDriveForSummary: DriveDTO?
    @State private var isShowingStartDriveSheet = false
    @State private var isShowingDriveControls = false
    @State private var pendingRouteDriveAfterStartSheet = false
    @State private var pendingGoLiveAfterStartSheet = false
    @State private var driveChatShareContext: DriveChatShareContext?
    @State private var routeDriveSessionTask: Task<Void, Never>?
    @State private var isDriveCameraPitchEngaged = false
    @State private var driveCameraTargetCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraRenderedCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraTargetBearing: CGFloat = 0
    @State private var driveCameraRenderedBearing: CGFloat = 0
    @State private var driveCameraPreviousSample: CLLocation?
    @State private var isApplyingDriveCameraUpdate = false
    @State private var driveCameraProgrammaticMoveGeneration = 0
    @State private var mapViewportLayoutSize: CGSize = CGSize(width: 390, height: 700)
    @State private var driveDockLayoutHeight: CGFloat = 0
    @State private var isShowingDriveRecordingComingSoon = false
    @State private var isShowingDriveLineLibrary = false
    @State private var lastWidgetPlaceGeocodeAt: Date = .distantPast
    @State private var isBuildingDriveLine = false
    @State private var isSelectingFinishPoint = false
    @State private var driveLineDraftPoints: [DriveLinePoint] = []
    @State private var driveLineDraftRoadCoordinates: [CLLocationCoordinate2D] = []
    @State private var driveLineDraftDistanceMeters: Double = 0
    @State private var driveLineDraftTravelSeconds: TimeInterval = 0
    @State private var driveLineDraftColorKey: DriveLineColorKey = .violet
    @State private var isBuildingRoadPath = false
    @State private var activeDriveLine: DriveLine?
    @State private var savedDriveLines: [DriveLine] = []
    @State private var isShowingDriveNamePrompt = false
    @State private var driveLineNameDraft = ""
    @State private var hasAppliedInitialCamera = false
    @State private var isUsingFallbackCamera = false
    @State private var lastInvitePollAt: Date = .distantPast
    @State private var dwellByFriendID: [String: FriendDwellState] = [:]
    @State private var showDrivesLayer = false
    @State private var showDriveOverlays = false
    @State private var showDrivePointAnnotations = false
    @State private var showPublicCircleLayer = false
    @State private var showMyPlacesLayer = true
    @State private var showEventsLayer = true
    @State private var showRaceTracksLayer = true
    @State private var showTrafficLayer = true
    @State private var visibleCircleLayerIDs: Set<String> = []
    /// Tracks squad membership ids across roster refreshes so newly joined squads can default onto map layers.
    @State private var mapLayerKnownMembershipCircleIDs: Set<String> = []
    @State private var visibleDriveLineIDs: Set<String> = []
    @State private var cameraFollowMode: MapCameraFollowMode = .followSelf
    @State private var isProgrammaticCameraMove = false
    @State private var programmaticCameraMoveGeneration = 0
    /// Last region applied by Otto (focus controls, clamps). Used to detect user pan/zoom vs programmatic moves.
    @State private var lastProgrammaticMapRegion: MKCoordinateRegion?
    @State private var renderedFriendCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var targetFriendCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var previousVisibleFriendsByID: [String: FriendLocation] = [:]
    @State private var drivesToggleTask: Task<Void, Never>?
    @State private var isAdjustingZoomBounds = false
    @State private var lastPresenceSnapshotByFriendID: [String: PresenceSnapshot] = [:]
    @State private var lastLocationUpdateAtByFriendID: [String: Date] = [:]
    @State private var lastActiveSeenAtByFriendID: [String: Date] = [:]
    @State private var isShowingMapPlaceActionSheet = false
    @State private var isShowingSavePlaceSheet = false
    @State private var savePlaceTapCoordinate: CLLocationCoordinate2D?
    @State private var savePlaceNameDraft = ""
    @State private var savePlaceAddressLine: String?
    @State private var savePlacePoiCategory: String?
    /// Portable `placeKind` sent to API (`restaurant`, `gas_station`, `address`, `coordinates`, `other`).
    @State private var savePlaceKind = "coordinates"
    @State private var savePlaceIsResolving = false
    @State private var adhocPlaceSharePayload: MapMarkerSharePayload?
    @State private var isShowingAdhocPlaceShareChatSheet = false
    @State private var isSavingPlace = false

    private let staleLocationAfter: TimeInterval = 60
    private let squadFollowFreshnessInterval: TimeInterval = 90
    /// After someone stops sharing (`isActive == false`), keep their pin briefly so one flaky presence payload
    /// doesn't flicker; must stay short enough to match the “people sharing” sheet + badge (those require `isActive`).
    private let keepInactiveOnMapFor: TimeInterval = 30

    private var isFollowingUser: Bool { cameraFollowMode.isFollowingSelf }
    private var followedFriendID: String? { cameraFollowMode.followedFriendID }
    private var followedSquadID: String? { cameraFollowMode.followedSquadID }

    /// Route drive session active on the map tab (armed or recording — not gated on profile save).
    private var isRouteDriveSessionOnMap: Bool {
        appState.activeRouteDriveSession != nil
            && appState.activeDriveSession?.kind == .route
    }

    /// Pitched navigation camera during Quick Drive, Go Live, or route drive on the map tab.
    private var usesDriveCameraPitch: Bool {
        if isQuickDriveSessionActive { return true }
        if isLiveDriveSessionActive { return true }
        if isRouteDriveSessionOnMap { return true }
        return false
    }

    /// Route selected on map while a route drive session is armed or actively recording.
    private var isActiveRouteDriveRecording: Bool {
        guard let selectedRoute, let session = appState.activeRouteDriveSession else { return false }
        return session.activeRouteId == selectedRoute.id && (session.isArmed || session.isActive)
    }

    /// Keep all route markers visible whenever a route is selected on the map (no wide-zoom start/finish strip).
    private var shouldShowAllRouteMarkers: Bool {
        selectedRoute != nil
    }

    private var driveHorizonUserLocation: CLLocation? {
        guard usesDriveCameraPitch else { return nil }
        _ = locationService.mapLocationDisplayTick
        return locationService.latestSample ?? locationService.lastLocation
    }

    private var driveVisibleMapHeightMeters: Double {
        let latitudeDelta = usesDriveCameraPitch ? currentLatitudeDelta : markerLODLatitudeDelta
        return MapDriveHorizonDepth.visibleMapHeightMeters(latitudeDelta: latitudeDelta)
    }

    private func routePointHorizonScale(for coordinate: CLLocationCoordinate2D) -> CGFloat {
        guard let user = driveHorizonUserLocation else { return 1 }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: coordinate)
        return MapDriveHorizonDepth.horizonScale(
            distanceMeters: distance,
            visibleMapHeightMeters: driveVisibleMapHeightMeters
        )
    }

    private var showsRouteCheckpointMapDebug: Bool {
        routeCheckpointMapDebugEnabled
            && appState.canAccessInternalDebugTools
            && selectedRoute != nil
            && !shouldSuspendMapboxRendering
    }

    private var mapUserLocationForRouteMarkers: CLLocation? {
        _ = locationService.mapLocationDisplayTick
        return locationService.latestSample ?? locationService.lastLocation
    }

    private func shouldShowRouteMapPoint(_ point: SelectedRouteMapPoint) -> Bool {
        guard point.markerType == "waypoint" else { return true }
        guard let user = mapUserLocationForRouteMarkers else { return true }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: point.coordinate)
        return MapDriveHorizonDepth.shouldShowRouteMarker(
            markerType: point.markerType,
            distanceMeters: distance
        )
    }

    private var routeCheckpointDebugWaypoints: [SelectedRouteMapPoint] {
        guard let selectedRoute else { return [] }
        return selectedRoute.points.enumerated().compactMap { index, point -> SelectedRouteMapPoint? in
            guard point.markerType == "waypoint" else { return nil }
            guard let coordinate = coordinate(from: point) else { return nil }
            let mapPoint = SelectedRouteMapPoint(
                id: "\(selectedRoute.id)-debug-\(index)",
                coordinate: coordinate,
                markerType: point.markerType,
                index: index
            )
            guard shouldShowRouteMapPoint(mapPoint) else { return nil }
            return mapPoint
        }
    }

    private var selectedRouteMapPointIndices: Set<Int> {
        Set(selectedRouteMapPoints.map(\.index))
    }

    private func checkpointDebugSnapshot(for point: SelectedRouteMapPoint) -> RouteCheckpointMapDebugSnapshot {
        let user = driveHorizonUserLocation
        let distanceMeters = user.map { MapDriveHorizonDepth.distanceMeters(from: $0, to: point.coordinate) }
        return RouteCheckpointMapDebugSnapshot(
            pointIndex: point.index,
            distanceMeters: distanceMeters,
            horizonScale: routePointHorizonScale(for: point.coordinate),
            lodPresentation: RouteMapMarkerLOD.presentation(
                markerType: point.markerType,
                latitudeDelta: markerLODLatitudeDelta
            ),
            currentLatitudeDelta: currentLatitudeDelta,
            markerLODLatitudeDelta: markerLODLatitudeDelta,
            overlapPriority: routePointOverlapPriority(for: point),
            wouldStrip: RouteMapMarkerLOD.shouldStripToStartFinishOnly(
                shouldShowAllRouteMarkers: shouldShowAllRouteMarkers,
                latitudeDelta: markerLODLatitudeDelta
            ),
            inSelectedRouteMapPoints: selectedRouteMapPointIndices.contains(point.index),
            withinOneMile: MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType: point.markerType,
                distanceMeters: distanceMeters
            ),
            usesDriveCameraPitch: usesDriveCameraPitch
        )
    }

    private func routePointOverlapPriority(for point: SelectedRouteMapPoint) -> Int {
        if let user = driveHorizonUserLocation {
            let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: point.coordinate)
            return MapDriveHorizonDepth.driveRouteOverlapPriority(
                distanceMeters: distance,
                markerType: point.markerType,
                tieBreaker: point.index
            )
        }
        return RouteMapGeometry.mapMarkerOverlapPriority(
            for: point.coordinate,
            markerType: point.markerType,
            tieBreaker: point.index
        )
    }

    private func presenceHorizonScale(for coordinate: CLLocationCoordinate2D, isCurrentUser: Bool) -> CGFloat {
        if isCurrentUser { return 1 }
        guard let user = driveHorizonUserLocation else { return 1 }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: coordinate)
        return MapDriveHorizonDepth.horizonScale(
            distanceMeters: distance,
            visibleMapHeightMeters: driveVisibleMapHeightMeters,
            minScale: MapDriveHorizonDepth.presenceMinScale
        )
    }

    private func presenceOverlapPriority(for coordinate: CLLocationCoordinate2D, tieBreaker: Int) -> Int {
        if let user = driveHorizonUserLocation {
            let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: coordinate)
            return MapDriveHorizonDepth.drivePresenceOverlapPriority(
                distanceMeters: distance,
                tieBreaker: tieBreaker
            )
        }
        return RouteMapGeometry.mapPresenceMarkerOverlapPriority(
            for: coordinate,
            tieBreaker: tieBreaker
        )
    }

    private func shouldShowPresenceGroup(_ group: FriendProximityGroup) -> Bool {
        guard usesDriveCameraPitch else { return true }
        if group.members.count == 1, group.members.first?.id == appState.currentUserID {
            return true
        }
        guard let user = driveHorizonUserLocation else { return true }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: group.coordinate)
        return MapDriveHorizonDepth.shouldShowPresenceMarker(distanceMeters: distance)
    }

    private func isSelfPresenceFriend(_ friend: FriendLocation) -> Bool {
        let friendID = friend.id.trimmingCharacters(in: .whitespacesAndNewlines)
        let myID = appState.currentUserID.trimmingCharacters(in: .whitespacesAndNewlines)
        if !myID.isEmpty, friendID == myID { return true }
        if myID.isEmpty, friendID == "me" { return true }
        return false
    }

    private func presenceBrandLogoURL(for friend: FriendLocation) -> URL? {
        let url: URL?
        if isSelfPresenceFriend(friend) {
            url = appState.mapSelfBrandLogoURL
        } else {
            url = appState.peerBrandLogoURL(for: friend)
        }
        #if DEBUG
        if isSelfPresenceFriend(friend),
           appState.showsSelfDriveBrandLogoOnMap,
           !appState.selectedSharingCarID.isEmpty,
           url == nil
        {
            OttoLog.app.debug(
                "Map pin: drive active with selected carId=\(appState.selectedSharingCarID, privacy: .public) but brand logo URL is nil"
            )
        }
        #endif
        return url
    }

    private func presenceMarkerRefreshID(for friend: FriendLocation) -> String {
        let logo = presenceBrandLogoURL(for: friend)?.absoluteString ?? ""
        let driveSession = appState.activeDriveSession?.id.uuidString ?? ""
        let routeDrive =
            appState.activeRouteDriveSession.map { "\($0.sessionId)-\($0.status)" } ?? ""
        return "\(friend.id)|\(driveSession)|\(routeDrive)|\(appState.selectedSharingCarID)|\(logo)"
    }

    private func presenceMapAnnotationID(for group: FriendProximityGroup) -> String {
        if group.members.count == 1, let friend = group.members.first {
            return "\(group.id)|\(presenceMarkerRefreshID(for: friend))"
        }
        return "cluster-\(group.id)"
    }

    private var groupedVisibleFriendsForMap: [FriendProximityGroup] {
        groupedVisibleFriends.filter { shouldShowPresenceGroup($0) }
    }

    /// Programmatic drive-camera updates should not clear self-follow; only explicit map gestures do.
    private var suppressFollowCancellationForActiveDriveFollow: Bool {
        guard cameraFollowMode.isFollowingSelf else { return false }
        if usesDriveCameraPitch { return true }
        if isLiveDriveSessionActive { return true }
        return false
    }

    private var mapAllowsHitTesting: Bool {
        return isActive
    }

    private var shouldRunLiveMapTasks: Bool {
        guard isActive else { return false }
        #if targetEnvironment(simulator)
        return false
        #else
        return true
        #endif
    }

    /// Matches `LocationService.startUpdatingLocation()` — no device-backed self pin without usable authorization.
    private var isLocationAuthorizedForMapPin: Bool {
        switch locationService.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            return true
        case .notDetermined, .restricted, .denied:
            return false
        @unknown default:
            return false
        }
    }

    /// Circle/presence rows may omit `avatarUrl` for self; fall back to `AppState.allUsers` (profile roster).
    private func selfDisplayFieldsMergingProfile(myBase: FriendLocation?) -> (
        name: String,
        avatarName: String,
        avatarUrl: String?,
        accentColor: Color
    ) {
        let profile = appState.allUsers.first(where: { $0.id == appState.currentUserID })
        let name = myBase?.name ?? profile?.displayName ?? "You"
        let avatarName = myBase?.avatarName ?? profile?.displayName ?? "You"
        let trimmedBase = myBase?.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let trimmedProfile = profile?.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let avatarUrl: String? =
            !trimmedBase.isEmpty ? trimmedBase
            : (!trimmedProfile.isEmpty ? trimmedProfile : nil)
        let accent =
            myBase?.accentColor
            ?? MapAccentPalette.resolvedColor(mapAccentKey: profile?.mapAccentKey, userId: appState.currentUserID)
        return (name, avatarName, avatarUrl, accent)
    }

    private var currentCircleFriends: [FriendLocation] {
        var merged: [FriendLocation] = []
        if showPublicCircleLayer {
            merged.append(contentsOf: appState.publicPresenceMembers)
        }
        for circle in appState.circles where visibleCircleLayerIDs.contains(circle.id) {
            merged.append(contentsOf: circle.members)
        }
        var byID: [String: FriendLocation] = [:]
        for m in merged {
            byID[m.id] = preferredPresenceFriend(existing: byID[m.id], candidate: m)
        }
        return Array(byID.values)
    }

    private var allPresenceFriends: [FriendLocation] {
        var byID: [String: FriendLocation] = [:]
        for friend in appState.circles.flatMap({ $0.members }) {
            byID[friend.id] = preferredPresenceFriend(existing: byID[friend.id], candidate: friend)
        }
        return Array(byID.values)
    }

    private var visibleFriends: [FriendLocation] {
        _ = locationService.mapLocationDisplayTick
        let layeredMembers = currentCircleFriends
        let baseMembers = showAllMembersForNow
            ? layeredMembers
            : layeredMembers.filter(\.isActive)
        let now = Date()
        let filteredMembers = baseMembers.filter { member in
            if member.id == appState.currentUserID {
                guard isLocationAuthorizedForMapPin else { return false }
                if member.isActive { return true }
                if let lastActiveAt = lastActiveSeenAtByFriendID[member.id],
                   now.timeIntervalSince(lastActiveAt) <= keepInactiveOnMapFor
                {
                    return true
                }
                return false
            }
            if member.isActive { return true }
            if let lastActiveAt = lastActiveSeenAtByFriendID[member.id],
               now.timeIntervalSince(lastActiveAt) <= keepInactiveOnMapFor
            {
                return true
            }
            return false
        }

        var byID = Dictionary(
            filteredMembers.map { ($0.id, $0) },
            uniquingKeysWith: { existing, _ in existing }
        )

        if isLocationAuthorizedForMapPin,
           let coordinate = locationService.displayLocation?.coordinate
        {
            let myBase = byID[appState.currentUserID]
            let selfFields = selfDisplayFieldsMergingProfile(myBase: myBase)
            let me = FriendLocation(
                id: appState.currentUserID.isEmpty ? "me" : appState.currentUserID,
                name: selfFields.name,
                avatarName: selfFields.avatarName,
                avatarUrl: selfFields.avatarUrl,
                car: myBase?.car ?? "My Car",
                clubRole: myBase?.clubRole ?? "Driver",
                lastRun: myBase?.lastRun ?? "Now",
                coordinate: coordinate,
                speedMph: Int((locationService.displaySpeedMetersPerSecond() * 2.23694).rounded()),
                isOnline: true,
                isActive: appState.isPublishingLiveSharingPresence,
                accentColor: selfFields.accentColor,
                movementMode: locationService.movementMode,
                lastUpdatedAt: Date(),
                lastPresenceInApp: true
            )
            byID[me.id] = me
        }

        // Temporary mode: show all circle members on the map.
        // Long-term behavior should be sharing-only for non-self users.
        // Keep deterministic ordering so composite marker member slots stay stable.
        return Array(byID.values).sorted { $0.id < $1.id }
    }

    /// Peers (not self) currently publishing live location on the map — for the find-people control badge.
    private var friendsSharingLocationCount: Int {
        let myId = appState.currentUserID
        return visibleFriends.filter { friend in
            guard friend.isActive else { return false }
            if myId.isEmpty { return true }
            return friend.id != myId
        }.count
    }

    private var findPeopleSharingAccessibilityLabel: String {
        if friendsSharingLocationCount > 0 {
            return "Find people sharing, \(friendsSharingLocationCount) friends sharing location"
        }
        return "Find people sharing"
    }

    private var anchoredUpcomingEvents: [AnchoredUpcomingEvent] {
        appState.mapDiscoveryEvents
            .filter { $0.isEligibleForMapDisplay() }
            .compactMap { event in
            guard let coordinate = event.eventGeoCoordinate else { return nil }
            return AnchoredUpcomingEvent(id: event.id, event: event, coordinate: coordinate)
        }
    }

    private struct AnchoredUpcomingEvent: Identifiable {
        let id: String
        let event: EventDTO
        let coordinate: CLLocationCoordinate2D
    }

    /// Proximity-grouped anchors so overlapping events show one Otto beacon + count badge.
    private struct AnchoredUpcomingEventGroup: Identifiable {
        let id: String
        let coordinate: CLLocationCoordinate2D
        let events: [EventDTO]
    }

    private let anchoredEventClusterDistanceMeters: CLLocationDistance = 78

    private var anchoredUpcomingEventGroups: [AnchoredUpcomingEventGroup] {
        let sortedAnchored = anchoredUpcomingEvents.sorted { lhs, rhs in
            if lhs.coordinate.latitude != rhs.coordinate.latitude {
                return lhs.coordinate.latitude < rhs.coordinate.latitude
            }
            return lhs.coordinate.longitude < rhs.coordinate.longitude
        }
        var groups: [[AnchoredUpcomingEvent]] = []
        for item in sortedAnchored {
            if let idx = groups.firstIndex(where: { group in
                group.contains { existing in
                    let a = CLLocation(latitude: existing.coordinate.latitude, longitude: existing.coordinate.longitude)
                    let b = CLLocation(latitude: item.coordinate.latitude, longitude: item.coordinate.longitude)
                    return a.distance(from: b) <= anchoredEventClusterDistanceMeters
                }
            }) {
                groups[idx].append(item)
            } else {
                groups.append([item])
            }
        }

        return groups.map { members in
            let coords = members.map(\.coordinate)
            let c = centroid(for: coords)
            let evs = members.map(\.event).sorted { $0.startsAt < $1.startsAt }
            let idSignature = evs.map(\.id).sorted().joined(separator: ",")
            return AnchoredUpcomingEventGroup(id: idSignature, coordinate: c, events: evs)
        }
    }

    private func isEventBeaconPreviewActive(for group: AnchoredUpcomingEventGroup) -> Bool {
        switch mapPreviewSession {
        case .upcomingEvent(let primary, let siblings):
            let previewIDs = Set([primary.id] + siblings.map(\.id))
            return previewIDs == Set(group.events.map(\.id))
        default:
            return false
        }
    }

    private var visibleFriendsSignature: String {
        visibleFriends
            .sorted { $0.id < $1.id }
            .map { friend in
                let lat = String(format: "%.6f", friend.coordinate.latitude)
                let lng = String(format: "%.6f", friend.coordinate.longitude)
                return "\(friend.id):\(lat):\(lng):\(friend.speedMph):\(friend.movementMode)"
            }
            .joined(separator: "|")
    }

    private var currentCircleFriendsSignature: String {
        currentCircleFriends
            .sorted { $0.id < $1.id }
            .map { friend in
                let lat = String(format: "%.6f", friend.coordinate.latitude)
                let lng = String(format: "%.6f", friend.coordinate.longitude)
                return "\(friend.id):\(lat):\(lng):\(friend.speedMph):\(friend.isActive)"
            }
            .joined(separator: "|")
    }

    /// Presence + location signature driving squad “follow bounds” camera updates.
    private var followedSquadBoundsSignature: String {
        guard let sid = followedSquadID, !sid.isEmpty else { return "" }
        return "\(sid)|\(squadTrackingCoordinateSignature(for: sid))"
    }

    /// Squad *roster* changes (ids only) so `onChange` does not require `[DriveCircle]: Equatable`.
    private var mapSquadListIDSignature: String {
        appState.circles.map(\.id).sorted().joined(separator: "\u{1e}")
    }

    private var displayedFriends: [FriendLocation] {
        visibleFriends.map { friend in
            guard let rendered = renderedFriendCoordinates[friend.id] else { return friend }
            return FriendLocation(
                id: friend.id,
                name: friend.name,
                avatarName: friend.avatarName,
                avatarUrl: friend.avatarUrl,
                car: friend.car,
                clubRole: friend.clubRole,
                lastRun: friend.lastRun,
                coordinate: rendered,
                speedMph: friend.speedMph,
                isOnline: friend.isOnline,
                isActive: friend.isActive,
                accentColor: friend.accentColor,
                movementMode: friend.movementMode,
                lastUpdatedAt: friend.lastUpdatedAt,
                lastPresenceInApp: friend.lastPresenceInApp
            )
        }
    }

    private var currentlySharingFriends: [FriendLocation] {
        var byID = Dictionary(
            uniqueKeysWithValues: allPresenceFriends
                .filter(\.isActive)
                .map { ($0.id, $0) }
        )

        if let coordinate = locationService.displayLocation?.coordinate, appState.isPublishingLiveSharingPresence {
            let myBase = byID[appState.currentUserID]
                ?? allPresenceFriends.first(where: { $0.id == appState.currentUserID })
            let selfFields = selfDisplayFieldsMergingProfile(myBase: myBase)
            let me = FriendLocation(
                id: appState.currentUserID.isEmpty ? "me" : appState.currentUserID,
                name: selfFields.name,
                avatarName: selfFields.avatarName,
                avatarUrl: selfFields.avatarUrl,
                car: myBase?.car ?? "My Car",
                clubRole: myBase?.clubRole ?? "Driver",
                lastRun: myBase?.lastRun ?? "Now",
                coordinate: coordinate,
                speedMph: Int((locationService.effectiveSpeedMetersPerSecond() * 2.23694).rounded()),
                isOnline: true,
                isActive: true,
                accentColor: selfFields.accentColor,
                movementMode: locationService.movementMode,
                lastUpdatedAt: Date(),
                lastPresenceInApp: true
            )
            byID[me.id] = me
        }

        return Array(byID.values)
            .filter { $0.isActive }
            .sorted { lhs, rhs in
                if lhs.id == appState.currentUserID { return true }
                if rhs.id == appState.currentUserID { return false }
                return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
            }
    }

    private var sharingUpdateLabelsByFriendID: [String: String] {
        Dictionary(uniqueKeysWithValues: currentlySharingFriends.map { friend in
            guard let lastUpdate = lastPresenceUpdateDate(for: friend) else {
                return (friend.id, "Here just now")
            }
            let elapsed = Date().timeIntervalSince(lastUpdate)
            let label = elapsed < 60
                ? "Here just now"
                : "Here \(formatDwellDuration(elapsed)) ago"
            return (friend.id, label)
        })
    }

    private func preferredPresenceFriend(existing: FriendLocation?, candidate: FriendLocation) -> FriendLocation {
        guard let existing else { return candidate }
        if candidate.isActive != existing.isActive {
            return candidate.isActive ? candidate : existing
        }
        let existingUpdatedAt = existing.lastUpdatedAt ?? .distantPast
        let candidateUpdatedAt = candidate.lastUpdatedAt ?? .distantPast
        return candidateUpdatedAt >= existingUpdatedAt ? candidate : existing
    }

    private func lastPresenceUpdateDate(for friend: FriendLocation) -> Date? {
        lastLocationUpdateAtByFriendID[friend.id] ?? friend.lastUpdatedAt
    }

    private var isShowingPeerProfileSheet: Bool {
        presentedPeerProfileFocus != nil
    }

    private func openPeerProfile(userID: String) {
        presentedPeerProfileFocus = PresentedPeerProfileFocus(id: userID)
    }

    @ViewBuilder
    private func mapPreviewSheetContent(_ session: MapPreviewSession) -> some View {
        switch session {
        case .savedPlace(let place):
            MapMarkerDetailSheet(
                content: .savedPlace(place),
                distanceFromMe: distanceFromMeCardValue(for: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude))
            )
            .environmentObject(appState)
        case .clusterPick(_, let members):
            MapFriendClusterPickSheet(
                members: members,
                updateLabelsByFriendID: updateLabels(for: members)
            ) { picked in
                mapPreviewSession = nil
                openPeerProfile(userID: picked.id)
            }
        case .upcomingEvent(let primary, let siblings):
            MapMarkerDetailSheet(
                content: .event(primary: primary, siblings: siblings),
                distanceFromMe: primary.eventGeoCoordinate.flatMap { distanceFromMeCardValue(for: $0) }
            )
            .environmentObject(appState)
        case .raceTrack(let track):
            MapMarkerDetailSheet(
                content: .raceTrack(track),
                distanceFromMe: track.coordinate.flatMap { distanceFromMeCardValue(for: $0) }
            )
            .environmentObject(appState)
        }
    }

    private func mapPreviewBackground(for session: MapPreviewSession) -> Color {
        switch session {
        case .savedPlace, .clusterPick, .upcomingEvent, .raceTrack:
            return MarkerDetailStyle.sheetBackground
        }
    }

    private var selectedRaceTrackPreviewID: String? {
        if case .raceTrack(let track) = mapPreviewSession {
            return track.id
        }
        return nil
    }

    private var selectedSavedPlacePreviewID: String? {
        if case .savedPlace(let place) = mapPreviewSession {
            return place.id
        }
        return nil
    }

    private var chatSharedPlacePeekMarkersNeedingMapPin: [SavedPlaceDTO] {
        chatSharedPlacePeekMarkers.filter { peek in
            !appState.savedPlaces.contains(where: { $0.id == peek.id })
        }
    }

    private func registerChatSharedPlacePeekMarker(_ place: SavedPlaceDTO) {
        guard !appState.savedPlaces.contains(where: { $0.id == place.id }) else { return }
        chatSharedPlacePeekMarkers.removeAll { $0.id == place.id }
        chatSharedPlacePeekMarkers.append(place)
    }

    private func clearChatSharedPlacePeekMarkers() {
        chatSharedPlacePeekMarkers.removeAll()
    }

    private var plottableRaceTracks: [RaceTrackRecord] {
        raceTracksDatasetStore.tracks.compactMap { track in
            track.coordinate == nil ? nil : track
        }
    }

    private func updateLabels(for friends: [FriendLocation]) -> [String: String] {
        Dictionary(uniqueKeysWithValues: friends.map { friend in
            let compact = friendLastUpdateCompact(for: friend.id)
            let label = sharingUpdateLabelsByFriendID[friend.id]
                ?? (compact == "Just Now" ? "Here just now" : "Here \(compact)")
            return (friend.id, label)
        })
    }

    private func sharedCircles(with userID: String) -> [DriveCircle] {
        appState.circles
            .filter { circle in
                circle.members.contains { $0.id == userID }
            }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    /// Short form for the map person sheet stat card (e.g. `8s ago`, `4m ago`).
    private func friendLastUpdateCompact(for friendID: String) -> String {
        let fallbackUpdatedAt = allPresenceFriends.first(where: { $0.id == friendID })?.lastUpdatedAt
        guard let lastUpdate = lastLocationUpdateAtByFriendID[friendID] ?? fallbackUpdatedAt else {
            return "Just Now"
        }
        let seconds = Int(Date().timeIntervalSince(lastUpdate))
        if seconds < 0 { return "Just Now" }
        if seconds < 5 { return "Just Now" }
        if seconds < 60 { return "\(seconds)s ago" }
        let minutes = seconds / 60
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 48 { return "\(hours)h ago" }
        return "\(hours / 24)d ago"
    }

    /// Distance string for the sheet stat card (e.g. `1.2 mi`), without the word “away”.
    private func distanceFromMeCardValue(for coordinate: CLLocationCoordinate2D) -> String? {
        guard let me = locationService.latestSample ?? locationService.lastLocation else { return nil }
        let meters = me.distance(from: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude))
        if meters < 80 { return "Here" }
        if meters < 1000 { return String(format: "%.0f m", meters) }
        let miles = meters / 1609.34
        return String(format: "%.1f mi", miles)
    }

    private var groupedVisibleFriends: [FriendProximityGroup] {
        var groups: [[FriendLocation]] = []
        let thresholdMeters = zoomAwareGroupingThresholdMeters()

        for friend in displayedFriends {
            if let idx = groups.firstIndex(where: { group in
                group.contains { existing in
                    CLLocation(latitude: existing.coordinate.latitude, longitude: existing.coordinate.longitude)
                        .distance(from: CLLocation(latitude: friend.coordinate.latitude, longitude: friend.coordinate.longitude)) <= thresholdMeters
                }
            }) {
                groups[idx].append(friend)
            } else {
                groups.append([friend])
            }
        }

        return groups.map { members in
            // Keep composite pointer anchored to group peers when present (not biased to current user).
            let peerCoordinates = members
                .filter { $0.id != appState.currentUserID }
                .map(\.coordinate)
            let center = centroid(for: peerCoordinates.isEmpty ? members.map(\.coordinate) : peerCoordinates)
            return FriendProximityGroup(members: members, coordinate: center)
        }
        .sorted { $0.id < $1.id }
    }

    private var eventRadiusOverlays: [EventRadiusOverlay] {
        anchoredUpcomingEventGroups.compactMap { group in
            guard group.events.contains(where: { $0.currentUserRsvp == "going" && $0.currentUserCheckIn == nil }) else {
                return nil
            }
            return EventRadiusOverlay(
                id: group.id,
                polygon: radiusPolygon(center: group.coordinate, radiusMeters: AppState.eventCheckInRadiusMeters)
            )
        }
    }

    private var sharingUnavailableAlertBinding: Binding<Bool> {
        Binding(
            get: { appState.errorMessage != nil },
            set: { isPresented in
                if !isPresented { appState.errorMessage = nil }
            }
        )
    }

    /// Alerts, sheets, and toast — split from `body` so the type checker can finish in time.
    private var mapWithSheetsAlertsAndToast: some View {
        baseMapView
            .alert(mapErrorAlertTitle, isPresented: sharingUnavailableAlertBinding) {
                Button("OK", role: .cancel) {
                    appState.errorMessage = nil
                }
            } message: {
                Text(appState.errorMessage ?? "Unknown error.")
            }
            .alert("Name This Drive", isPresented: $isShowingDriveNamePrompt) {
                TextField("Drive name", text: $driveLineNameDraft)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    finalizeDriveLineWizard(named: driveLineNameDraft)
                }
            } message: {
                Text("Give this drive a name before saving.")
            }
            .alert("Drive recording", isPresented: $isShowingDriveRecordingComingSoon) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Drive recording is coming soon.")
            }
            .alert("Stop this drive?", isPresented: $isShowingStopDriveConfirmation) {
                Button("Keep Driving", role: .cancel) {}
                Button("Stop Drive", role: .destructive) {
                    performConfirmedStopDrive()
                }
            } message: {
                Text("Your active drive session will end immediately.")
            }
            .alert("Choose a squad", isPresented: $isShowingSharingSquadRequiredAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Select at least one squad before you start sharing.")
            }
            .alert("Rename Route", isPresented: Binding(
                get: { routeToRename != nil },
                set: { if !$0 { routeToRename = nil } }
            )) {
                TextField("Route name", text: $routeNameDraft)
                Button("Cancel", role: .cancel) {
                    routeToRename = nil
                }
                Button("Save") {
                    guard let route = routeToRename else { return }
                    Task { await renameSelectedRoute(route, to: routeNameDraft) }
                }
            } message: {
                Text("Give this route a new name.")
            }
            .confirmationDialog(
                "Delete this route?",
                isPresented: Binding(
                    get: { routeToDelete != nil },
                    set: { if !$0 { routeToDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete Route", role: .destructive) {
                    guard let route = routeToDelete else { return }
                    Task { await deleteSelectedRoute(route) }
                }
                Button("Cancel", role: .cancel) {
                    routeToDelete = nil
                }
            } message: {
                Text("This removes the route from your saved routes.")
            }
            .sheet(item: $mapPreviewSession) { session in
                Group {
                    switch session {
                    case .clusterPick:
                        mapPreviewSheetContent(session)
                            .presentationDetents([.medium, .large])
                    case .savedPlace, .upcomingEvent, .raceTrack:
                        mapPreviewSheetContent(session)
                    }
                }
                .presentationBackground(mapPreviewBackground(for: session))
            }
            .sheet(item: $presentedPeerProfileFocus) { focus in
                ProfileScreen(
                    profileUserID: focus.id == appState.currentUserID ? nil : focus.id,
                    onUserBlocked: { presentedPeerProfileFocus = nil }
                )
                    .environmentObject(appState)
                    .environmentObject(locationService)
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $isShowingFriendSearch) {
                MapFriendSearchSheet(
                    friends: currentlySharingFriends,
                    squads: appState.circles,
                    followedSquadID: followedSquadID,
                    updateLabelsByFriendID: sharingUpdateLabelsByFriendID,
                    onSelectFriend: { friend in
                        mapPreviewSession = nil
                        isShowingFriendSearch = false
                        Task { @MainActor in
                            // Wait for the search sheet to finish dismissing; otherwise Map camera updates during
                            // the transition can trip `endFollowModesIfUserAdjustedCamera` and cancel follow.
                            try? await Task.sleep(nanoseconds: 420_000_000)
                            revealMapLayerIfNeeded(for: friend)
                            // Markers come from `visibleFriends` → `circle.members`. Without a refresh, roster data
                            // can still show `isActive == false` / stale coords right after we reveal layers, so the
                            // person never appears on the map even though the list had them as sharing.
                            await refreshPresenceForCirclesContainingFriend(friend)
                            await Task.yield()
                            followFriend(friend)
                            try? await Task.sleep(nanoseconds: 120_000_000)
                            recenterOnFollowedFriendIfNeeded()
                        }
                    },
                    onSelectSquad: { circle in
                        mapPreviewSession = nil
                        visibleCircleLayerIDs.insert(circle.id)
                        isShowingFriendSearch = false
                        Task { @MainActor in
                            // Follow-squad camera must be driven by fresh active sharers, not the stale roster row
                            // that may have opened the sheet.
                            try? await Task.sleep(nanoseconds: 420_000_000)
                            guard shouldRunLiveMapTasks else { return }
                            await appState.refreshPresence(for: circle.id, showsStartedSharingToast: false)
                            await Task.yield()
                            cameraFollowMode = .followSquad(circle.id)
                            applyFollowedSquadCameraIfNeeded()
                        }
                    }
                )
            }
            .sheet(isPresented: $isShowingStartDriveSheet) {
                StartDriveSheet(
                    onQuickDrive: {
                        isShowingStartDriveSheet = false
                        if appState.hasActiveDriveSession {
                            appState.activeToast = AppToast(text: "End your current drive first", systemImage: "exclamationmark.triangle.fill")
                            return
                        }
                        dismissDriveLaunchDock()
                        isQuickDriveDockVisible = true
                    },
                    onRouteDrive: {
                        isShowingStartDriveSheet = false
                        dismissDriveLaunchDock()
                        pendingRouteDriveAfterStartSheet = true
                        isShowingRoutesMenu = true
                    },
                    onGoLive: {
                        isShowingStartDriveSheet = false
                        dismissDriveLaunchDock()
                        handleLiveDriveStart()
                    },
                    onCancel: { isShowingStartDriveSheet = false }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationBackground(Color.black)
            }
            .sheet(isPresented: $isShowingDriveControls) {
                DriveControlsSheet(
                    presentation: driveSessionPillPresentation,
                    startedAt: appState.activeDriveSession?.startedAt ?? appState.sharingSessionStartedAt ?? Date(),
                    timeText: driveSessionTimeText,
                    distanceText: driveSessionDistanceText,
                    topSpeedText: driveSessionTopSpeedText,
                    shareLive: Binding(
                        get: { appState.isSharingEnabled },
                        set: { enabled in
                            if enabled {
                                guard !appState.circles.isEmpty else {
                                    appState.activeToast = AppToast(
                                        text: "Create or join a squad to start sharing.",
                                        systemImage: "person.3.fill"
                                    )
                                    return
                                }
                                syncSharingDraftsFromSession()
                                isShowingDriveControls = false
                                isShowingCirclePicker = true
                            } else {
                                appState.stopLiveSharingOnly()
                            }
                        }
                    ),
                    saveDrive: Binding(
                        get: {
                            appState.activeDriveSession?.isRecording ?? appState.sharingSaveDriveEnabled
                        },
                        set: { appState.setDriveSessionSaveEnabled($0) }
                    ),
                    routeName: selectedRoute?.name ?? appState.activeDriveSession?.routeName,
                    routeCheckpointText: routeControlsCheckpointText,
                    onAddSquad: {
                        isShowingDriveControls = false
                        isShowingCirclePicker = true
                    },
                    onStopDrive: {
                        isShowingDriveControls = false
                        isShowingStopDriveConfirmation = true
                    },
                    onDismiss: { isShowingDriveControls = false }
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationBackground(Color.black)
            }
            .sheet(isPresented: $isShowingCirclePicker) {
                circlePickerSheet
                    .onAppear { syncSharingDraftsFromSession() }
            }
            .sheet(isPresented: $isShowingDriveLineLibrary) {
                driveLineLibrarySheet
            }
            .sheet(isPresented: $isShowingLayers) {
                layersSheet
            }
            .sheet(isPresented: $isShowingRoutesMenu) {
                if appState.hasRoutesAccess {
                    RoutesMenu(
                        onCreateRoute: {
                            routeForEditing = nil
                            presentRouteBuilderIfAllowed()
                        },
                        onSelectRoute: { route in
                            selectRouteForMap(route)
                        },
                        onEditRoute: { route in
                            presentRouteBuilderIfAllowed(editing: route)
                        },
                        onDeleteRoute: { route in
                            clearSelectedRouteIfNeeded(route)
                        }
                    )
                    .environmentObject(appState)
                }
            }
            .fullScreenCover(isPresented: $isShowingRouteBuilder, onDismiss: {
                restoreMapViewportAfterRouteBuilderIfNeeded()
                routeForEditing = nil
            }) {
                if appState.hasRoutesAccess {
                    if let routeForEditing {
                        RouteBuilderView(route: routeForEditing) { savedRoute in
                            applySavedRouteToMap(savedRoute)
                        }
                            .environmentObject(appState)
                            .environmentObject(locationService)
                    } else {
                        RouteBuilderView(initialCenter: newRouteBuilderInitialCenter) { savedRoute in
                            applySavedRouteToMap(savedRoute)
                        }
                            .environmentObject(appState)
                            .environmentObject(locationService)
                    }
                } else {
                    Color.clear
                        .onAppear { isShowingRouteBuilder = false }
                }
            }
            .sheet(isPresented: $isShowingMapPlaceActionSheet, onDismiss: {
                if !isShowingSavePlaceSheet, !isShowingAdhocPlaceShareChatSheet {
                    savePlaceTapCoordinate = nil
                    savePlaceIsResolving = false
                    savePlaceNameDraft = ""
                    savePlaceAddressLine = nil
                    savePlacePoiCategory = nil
                    savePlaceKind = "coordinates"
                }
            }) {
                if let sharePayload = buildAdhocPlaceSharePayload() {
                    MapPlaceLongPressActionSheet(
                        isResolving: savePlaceIsResolving,
                        previewName: savePlaceNameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? nil
                            : savePlaceNameDraft,
                        previewAddress: savePlaceAddressLine,
                        sharePayload: sharePayload,
                        onShareToChat: { beginAdhocPlaceShareToChatFromMapPlaceAction() },
                        onSave: { openSavePlaceSheetFromMapPlaceAction() }
                    )
                }
            }
            .sheet(isPresented: $isShowingSavePlaceSheet, onDismiss: {
                savePlaceTapCoordinate = nil
                savePlaceIsResolving = false
                savePlaceNameDraft = ""
                savePlaceAddressLine = nil
                savePlacePoiCategory = nil
                savePlaceKind = "coordinates"
                isSavingPlace = false
            }) {
                savePlaceSheet
            }
            .sheet(isPresented: $isShowingAdhocPlaceShareChatSheet, onDismiss: {
                adhocPlaceSharePayload = nil
            }) {
                if let payload = adhocPlaceSharePayload {
                    MapMarkerChatDestinationSheet(
                        payload: payload,
                        onPosted: { isShowingAdhocPlaceShareChatSheet = false }
                    )
                    .environmentObject(appState)
                }
            }
            .overlay {
                ZStack {
                    if showMapLocationPrimer {
                        OttoEducationDialog(
                            allowsUnconfirmedDismiss: false,
                            onDismissUnconfirmed: {},
                            hero: { OttoEducationLocationHero() },
                            title: eduLoc("map_location_primer_title"),
                            bodyText: eduLoc("map_location_primer_body"),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: eduLoc("map_location_primer_footer"),
                            primaryTitle: eduLoc("map_location_primer_continue"),
                            onPrimary: {
                                dismissMapLocationPrimer(requestAuth: true)
                            },
                            secondaryTitle: eduLoc("map_location_primer_not_now"),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if showMapLocationDeniedModal {
                        OttoEducationDialog(
                            onDismissUnconfirmed: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showMapLocationDeniedModal = false
                                }
                            },
                            hero: { OttoEducationLocationHero() },
                            title: eduLoc("location_permission_map_modal_title"),
                            bodyText: eduLoc("location_permission_map_modal_body"),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: nil,
                            primaryTitle: eduLoc("location_permission_enable"),
                            onPrimary: {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showMapLocationDeniedModal = false
                                }
                            },
                            secondaryTitle: eduLoc("location_permission_modal_dismiss"),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if isShowingDriveSafetyDisclaimer {
                        OttoEducationDialog(
                            onDismissUnconfirmed: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    isShowingDriveSafetyDisclaimer = false
                                }
                            },
                            hero: { OttoEducationShieldHero() },
                            title: eduLoc("drive_safety_title"),
                            bodyText: eduLoc("drive_safety_body"),
                            bulletSectionTitle: eduLoc("drive_safety_section"),
                            bullets: [
                                ("lock.fill", eduLoc("drive_safety_obey_laws")),
                                ("eye.fill", eduLoc("drive_safety_stay_attentive")),
                                ("iphone.slash", eduLoc("drive_safety_no_app_driving")),
                                ("flag.fill", eduLoc("drive_safety_no_reckless")),
                            ],
                            footer: eduLoc("drive_safety_footer"),
                            primaryTitle: eduLoc("drive_safety_continue"),
                            onPrimary: {
                                withAnimation(.spring(response: 0.36, dampingFraction: 0.88)) {
                                    isShowingDriveSafetyDisclaimer = false
                                }
                                continuePendingDriveStartAfterSafetyDisclaimer()
                            },
                            secondaryTitle: eduLoc("drive_safety_not_now"),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if showSharingLocationDeniedModal {
                        OttoEducationDialog(
                            onDismissUnconfirmed: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showSharingLocationDeniedModal = false
                                }
                            },
                            hero: { OttoEducationLocationHero() },
                            title: eduLoc("location_permission_sharing_modal_title"),
                            bodyText: eduLoc("location_permission_sharing_modal_body"),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: nil,
                            primaryTitle: eduLoc("location_permission_enable"),
                            onPrimary: {
                                enableLocationForSharingFromModal()
                            },
                            secondaryTitle: eduLoc("location_permission_modal_dismiss"),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if showSharingMotionDeniedModal {
                        OttoEducationDialog(
                            onDismissUnconfirmed: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showSharingMotionDeniedModal = false
                                }
                            },
                            hero: { OttoEducationMotionHero() },
                            title: eduLoc("motion_permission_sharing_modal_title"),
                            bodyText: eduLoc("motion_permission_sharing_modal_body"),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: nil,
                            primaryTitle: eduLoc("location_permission_enable"),
                            onPrimary: {
                                enableMotionForSharingFromModal()
                            },
                            secondaryTitle: eduLoc("location_permission_modal_dismiss"),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if showDriveBackgroundLocationPrimer {
                        OttoEducationDialog(
                            allowsUnconfirmedDismiss: false,
                            onDismissUnconfirmed: {},
                            hero: { OttoEducationLocationHero() },
                            title: eduLoc("drive_background_location_primer_title"),
                            bodyText: eduLoc("drive_background_location_primer_body"),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: eduLoc("drive_background_location_primer_footer"),
                            primaryTitle: eduLoc("drive_background_location_primer_continue"),
                            onPrimary: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showDriveBackgroundLocationPrimer = false
                                }
                                pendingBackgroundLocationForDrive = true
                                locationService.requestBackgroundPermissionIfNeeded()
                                if locationService.authorizationStatus == .authorizedAlways {
                                    pendingBackgroundLocationForDrive = false
                                    finishPendingDriveAfterBackgroundGate(degraded: false)
                                }
                            },
                            secondaryTitle: "",
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if let driveCompleteSummary {
                        DriveCompleteView(
                            summary: driveCompleteSummary,
                            onViewSummary: {
                                presentCompletedDriveSummary(from: driveCompleteSummary)
                            },
                            onDone: {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    self.driveCompleteSummary = nil
                                }
                            }
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                        .zIndex(10)
                    }
                }
            }
            .sheet(item: $driveChatShareContext) { context in
                DriveShareSquadActionsSheet(
                    context: context,
                    externalShareText: driveExternalShareText(for: context),
                    externalShareSubject: context.previewTitle,
                    canShare: true
                )
                .environmentObject(appState)
            }
            .sheet(item: $completedDriveForSummary) { drive in
                DriveSummaryScreen(
                    drive: drive,
                    isOwner: true,
                    garageCars: appState.garageCars,
                    onDriveUpdated: { updated in
                        appState.applyDriveUpdate(updated)
                        completedDriveForSummary = updated
                    },
                    onDriveDeleted: {
                        appState.removeDriveFromRecent(id: drive.id)
                        completedDriveForSummary = nil
                    }
                )
                .environmentObject(appState)
                .presentationDetents([.large])
                .presentationBackground(Color.black)
            }
    }

    private func presentDriveChatShare(for summary: DriveCompleteSummary?, driveId: String?) {
        guard let summary else { return }
        let resolvedDriveId: String? = {
            if let id = driveId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty { return id }
            if let id = summary.driveId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty { return id }
            return nil
        }()
        guard let driveId = resolvedDriveId else {
            appState.activeToast = AppToast(
                text: "Drive isn't ready to share yet",
                systemImage: "exclamationmark.triangle.fill"
            )
            return
        }
        let driveFromRecents = appState.recentDrives.first(where: { $0.id == driveId })
        driveChatShareContext = DriveChatShareContext(
            driveId: driveId,
            previewTitle: summary.routeName,
            previewDistanceMeters: summary.distanceMeters,
            previewDriveTimeSeconds: summary.driveTimeSeconds,
            previewCompletedAt: Date(),
            mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput(route: driveFromRecents?.route)
        )
    }

    private func driveExternalShareText(for context: DriveChatShareContext) -> String {
        let miles = context.previewDistanceMeters / 1609.344
        let distance = miles < 10 ? String(format: "%.1f mi", miles) : "\(Int(miles.rounded())) mi"
        let minutes = max(1, Int((context.previewDriveTimeSeconds / 60).rounded()))
        let time = minutes >= 60 ? "\(minutes / 60)h \(minutes % 60)m" : "\(minutes)m"
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy • h:mm a"
        return "\(context.previewTitle)\n\(formatter.string(from: context.previewCompletedAt))\n\(distance) • \(time)"
    }

    private var mapErrorAlertTitle: String {
        let message = appState.errorMessage ?? ""
        if message.localizedCaseInsensitiveContains("squad") || message.localizedCaseInsensitiveContains("share") {
            return "Sharing Unavailable"
        }
        if message.localizedCaseInsensitiveContains("event") || message.localizedCaseInsensitiveContains("check in") {
            return "Event Error"
        }
        if message.localizedCaseInsensitiveContains("photo") {
            return "Upload Failed"
        }
        return "Something Went Wrong"
    }

    var body: some View {
        mapWithRuntimeHandlers
    }

    private var mapWithAppearanceHandlers: AnyView {
        let lifecycle = mapWithSheetsAlertsAndToast
            .onAppear(perform: mapScreenOnAppear)
            .onDisappear(perform: mapScreenOnDisappear)
        let selectionChanges = lifecycle
            .onChange(of: isActive) { _, active in
                appState.isMapScreenActive = active
                if active {
                    if !applyPendingSavedPlaceMapFocusIfNeeded() {
                        mapScreenBecameActive()
                    }
                    maybePresentMapLocationPrimer()
                } else {
                    appState.requestLocationSessionSync()
                }
                #if DEBUG
                logMapboxSuspendDiagnostics(event: "isActive → \(active)")
                #endif
            }
            .onChange(of: isShowingRouteBuilder) { _, _ in
                reactToMapboxSuspendTransition()
            }
            .onChange(of: appState.isRouteBuilderPresented) { _, _ in
                reactToMapboxSuspendTransition()
            }
            .onChange(of: appState.selectedCircleID) { _, _ in mapScreenSelectedCircleIDChanged() }
            .onChange(of: appState.pendingLocationSharingFocus?.id) { _, _ in
                Task { await applyPendingLocationSharingFocusFromPushIfNeeded() }
            }
            .onChange(of: locationService.authorizationStatus) { _, newStatus in
                if appState.isSharingEnabled {
                    switch newStatus {
                    case .denied, .restricted:
                        appState.stopSharingSession(disabledReason: "permission_revoked")
                    default:
                        break
                    }
                }
                if pendingMapLocationPermission {
                    switch newStatus {
                    case .authorizedAlways, .authorizedWhenInUse:
                        pendingMapLocationPermission = false
                        appState.requestLocationSessionSync()
                    case .denied, .restricted:
                        pendingMapLocationPermission = false
                        withAnimation(.easeInOut(duration: 0.18)) {
                            showMapLocationDeniedModal = true
                        }
                    default:
                        break
                    }
                }
                guard pendingSharingAfterLocationPermission else { return }
                switch newStatus {
                case .authorizedAlways, .authorizedWhenInUse:
                    pendingSharingAfterLocationPermission = false
                    appState.requestLocationSessionSync()
                    if pendingContinuationRequiresMotion {
                        continueSharingAfterLocationAuthorized()
                    } else {
                        continueDriveAfterForegroundLocationAuthorized()
                    }
                case .denied, .restricted:
                    pendingSharingAfterLocationPermission = false
                    withAnimation(.easeInOut(duration: 0.18)) {
                        showSharingLocationDeniedModal = true
                    }
                default:
                    break
                }
                if pendingBackgroundLocationForDrive {
                    switch newStatus {
                    case .authorizedAlways:
                        pendingBackgroundLocationForDrive = false
                        finishPendingDriveAfterBackgroundGate(degraded: false)
                    case .authorizedWhenInUse, .denied, .restricted:
                        pendingBackgroundLocationForDrive = false
                        finishPendingDriveAfterBackgroundGate(degraded: true)
                    default:
                        break
                    }
                }
            }
            .onChange(of: locationService.motionAuthorizationStatus) { _, newStatus in
                if appState.isSharingEnabled {
                    switch newStatus {
                    case .denied, .restricted:
                        appState.stopSharingSession(disabledReason: "permission_revoked")
                    default:
                        break
                    }
                }
                guard pendingSharingAfterMotionPermission else { return }
                handleMotionPermissionStateForPendingSharing(newStatus)
            }
        let authAndSharingChanges = selectionChanges
            .onChange(of: appState.pendingMapRouteSelection?.id) { _, _ in
                _ = applyPendingMapRouteSelectionIfNeeded()
            }
            .onChange(of: appState.pendingMapFocus?.id) { _, _ in
                _ = applyPendingSavedPlaceMapFocusIfNeeded()
            }
            .onChange(of: appState.pendingSharingSheetPresentation) { _, _ in
                applyPendingSharingSheetIfNeeded()
            }
            .onChange(of: appState.isAuthenticated) { _, isAuthenticated in
                if isAuthenticated {
                    applyPendingSharingSheetIfNeeded()
                } else {
                    clearChatSharedPlacePeekMarkers()
                }
            }
        let withCirclesChange = authAndSharingChanges
            .onChange(of: mapSquadListIDSignature) { _, _ in
                if let sid = followedSquadID, !appState.circles.contains(where: { $0.id == sid }) {
                    cameraFollowMode = .manual
                }
                reconcileVisibleCircleLayersWithCirclesList()
            }
            .onChange(of: isBuildingDriveLine) { _, _ in syncMapRouteSessionActiveToAppState() }
            .onChange(of: activeDriveLine?.id) { _, _ in syncMapRouteSessionActiveToAppState() }
            .onChange(of: appState.activeRouteDriveSession?.activeRouteId) { _, _ in syncMapRouteSessionActiveToAppState() }
            .onChange(of: appState.activeRouteDriveSession?.sessionId) { _, _ in
                if appState.activeRouteDriveSession != nil {
                    cameraFollowMode = .followSelf
                    syncDriveCameraPitchState()
                }
            }
            .onChange(of: appState.activeRouteDriveSession?.status) { _, _ in syncDriveCameraPitchState() }
            .onChange(of: appState.routeDriveFeedbackEvent?.id) { _, _ in
                handleRouteDriveFeedbackEvent(appState.routeDriveFeedbackEvent)
            }
            .onChange(of: isQuickDriveSessionActive) { _, isActive in
                if isActive {
                    cameraFollowMode = .followSelf
                }
                syncDriveCameraPitchState()
            }
            .onChange(of: appState.isSharingEnabled) { _, isSharing in
                if isSharing {
                    cameraFollowMode = .followSelf
                }
                syncDriveCameraPitchState()
            }
        return AnyView(withCirclesChange)
    }

    private var mapWithLayerPreferenceHandlers: AnyView {
        AnyView(
            mapWithAppearanceHandlers
            .onChange(of: showPublicCircleLayer) { _, isOn in mapScreenShowPublicCircleLayerChanged(isOn: isOn) }
            .onChange(of: showMyPlacesLayer) { _, _ in mapScreenShowMyPlacesLayerChanged() }
            .onChange(of: showEventsLayer) { _, _ in mapScreenShowEventsLayerChanged() }
            .onChange(of: showRaceTracksLayer) { _, _ in mapScreenShowRaceTracksLayerChanged() }
            .onChange(of: showTrafficLayer) { _, _ in mapScreenShowTrafficLayerChanged() }
            .onChange(of: showDrivesLayer) { _, _ in mapScreenShowDrivesLayerChanged() }
            .onChange(of: visibleCircleLayerIDs) { _, _ in mapScreenVisibleCircleLayerIDsChanged() }
        )
    }

    private var mapWithLocationAndPreviewHandlers: AnyView {
        AnyView(
            mapWithLayerPreferenceHandlers
            .onChange(of: locationService.lastLocation) { _, latest in mapScreenLastLocationChanged(latest: latest) }
            .onChange(of: locationService.mapLocationDisplayTick) { _, _ in
                let latest = locationService.displayLocation
                mapScreenLastLocationChanged(latest: latest)
                syncMarkerSmoothingTargets()
                refreshTravelSurfaceSamples()
            }
            .task(id: currentCircleFriendsSignature) { reconcilePresenceFreshness() }
            .task(id: visibleFriendsSignature) {
                syncMarkerSmoothingTargets()
                recenterOnFollowedFriendIfNeeded()
                refreshTravelSurfaceSamples()
            }
            .task(id: followedSquadBoundsSignature) {
                applyFollowedSquadCameraIfNeeded()
            }
            .onChange(of: mapPreviewSession?.id) { old, new in mapScreenMapPreviewSessionChanged(old: old, new: new) }
        )
    }

    private var mapWithRuntimeHandlers: AnyView {
        AnyView(
            mapWithLocationAndPreviewHandlers
            .onReceive(MapScreenSmoothingTimer.tick) { _ in
                guard isActive else { return }
                stepMarkerSmoothing()
                stepDriveCameraSmoothing()
            }
            .onReceive(MapScreenPresenceTimer.tick) { _ in mapScreenPresenceTimerFired() }
        )
    }

    private func eduLoc(_ key: String) -> String {
        NSLocalizedString(key, bundle: .main, value: key, comment: "")
    }

    private func dismissMapLocationPrimer(requestAuth: Bool = false) {
        withAnimation(.easeInOut(duration: 0.18)) {
            showMapLocationPrimer = false
        }
        if requestAuth {
            requestMapLocationPermissionFromPrimer()
        }
    }

    private func maybePresentMapLocationPrimer() {
        guard isActive else { return }
        guard !showMapLocationPrimer else { return }
        guard !showMapLocationDeniedModal else { return }
        guard !isShowingCirclePicker else { return }
        switch locationService.authorizationStatus {
        case .notDetermined:
            withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
                showMapLocationPrimer = true
            }
        case .denied, .restricted:
            withAnimation(.easeInOut(duration: 0.18)) {
                showMapLocationDeniedModal = true
            }
        case .authorizedAlways, .authorizedWhenInUse:
            break
        @unknown default:
            break
        }
    }

    private func requestMapLocationPermissionFromPrimer() {
        switch locationService.authorizationStatus {
        case .notDetermined:
            pendingMapLocationPermission = true
            locationService.requestPermissionIfNeeded()
        case .authorizedAlways, .authorizedWhenInUse:
            appState.requestLocationSessionSync()
        case .denied, .restricted:
            pendingMapLocationPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showMapLocationDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private func presentDriveSafetyDisclaimer(for continuation: PendingDriveStartContinuation) {
        pendingDriveStartContinuation = continuation
        withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
            isShowingDriveSafetyDisclaimer = true
        }
    }

    private func continuePendingDriveStartAfterSafetyDisclaimer() {
        switch pendingDriveStartContinuation {
        case .quickDrive:
            guard validateQuickRouteShareSelectionIfNeeded() else { return }
            if quickRouteShareLocationDraft {
                attemptStartSharingAfterDriveSafetyDisclaimer()
            } else {
                attemptStartDriveLocationGateAfterSafetyDisclaimer()
            }
        case .routeDrive(let route):
            guard validateRouteDriveStartDistance(for: route) else { return }
            guard validateQuickRouteShareSelectionIfNeeded() else { return }
            if quickRouteShareLocationDraft {
                attemptStartSharingAfterDriveSafetyDisclaimer()
            } else {
                attemptStartDriveLocationGateAfterSafetyDisclaimer()
            }
        case .goLive:
            attemptStartSharingAfterDriveSafetyDisclaimer()
        case nil:
            break
        }
    }

    private var pendingContinuationRequiresBackgroundLocation: Bool {
        switch pendingDriveStartContinuation {
        case .quickDrive, .routeDrive:
            return true
        case .goLive, nil:
            return false
        }
    }

    private var pendingContinuationRequiresMotion: Bool {
        switch pendingDriveStartContinuation {
        case .goLive:
            return true
        case .quickDrive, .routeDrive:
            return quickRouteShareLocationDraft
        case nil:
            return false
        }
    }

    private func attemptStartDriveLocationGateAfterSafetyDisclaimer() {
        switch locationService.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            continueDriveAfterForegroundLocationAuthorized()
        case .notDetermined:
            pendingSharingAfterLocationPermission = true
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingLocationDeniedModal = true
            }
        case .denied, .restricted:
            pendingSharingAfterLocationPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingLocationDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private func continueDriveAfterForegroundLocationAuthorized() {
        continueToBackgroundLocationPrimerIfNeeded()
    }

    private func continueToBackgroundLocationPrimerIfNeeded() {
        guard pendingContinuationRequiresBackgroundLocation else {
            completePendingDriveStartAfterPermissions()
            return
        }
        switch locationService.authorizationStatus {
        case .authorizedAlways:
            finishPendingDriveAfterBackgroundGate(degraded: false)
        case .authorizedWhenInUse:
            withAnimation(.easeInOut(duration: 0.18)) {
                showDriveBackgroundLocationPrimer = true
            }
        case .denied, .restricted:
            finishPendingDriveAfterBackgroundGate(degraded: true)
        @unknown default:
            break
        }
    }

    private func finishPendingDriveAfterBackgroundGate(degraded: Bool) {
        if degraded {
            presentDriveForegroundOnlyBackgroundToastIfNeeded()
        }
        completePendingDriveStartAfterPermissions()
    }

    private func presentDriveForegroundOnlyBackgroundToastIfNeeded() {
        guard !didShowDriveForegroundOnlyBackgroundToast else { return }
        didShowDriveForegroundOnlyBackgroundToast = true
        appState.showToast(
            String(localized: "drive_background_location_foreground_only_toast"),
            icon: "location.slash.fill"
        )
    }

    private func completePendingDriveStartAfterPermissions() {
        switch pendingDriveStartContinuation {
        case .quickDrive:
            pendingDriveStartContinuation = nil
            performQuickDriveStart(shareLive: quickRouteShareLocationDraft)
        case .routeDrive(let route):
            pendingDriveStartContinuation = nil
            performRouteDriveStart(for: route, shareLive: quickRouteShareLocationDraft)
        case .goLive, nil:
            break
        }
    }

    private func attemptStartSharingAfterDriveSafetyDisclaimer() {
        switch locationService.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            continueSharingAfterLocationAuthorized()
        case .notDetermined:
            pendingSharingAfterLocationPermission = true
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingLocationDeniedModal = true
            }
        case .denied, .restricted:
            pendingSharingAfterLocationPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingLocationDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private func enableLocationForSharingFromModal() {
        switch locationService.authorizationStatus {
        case .notDetermined:
            locationService.requestPermissionIfNeeded()
        case .authorizedAlways, .authorizedWhenInUse:
            pendingSharingAfterLocationPermission = false
            if pendingContinuationRequiresMotion {
                continueSharingAfterLocationAuthorized()
            } else {
                continueDriveAfterForegroundLocationAuthorized()
            }
        case .denied, .restricted:
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
            pendingSharingAfterLocationPermission = false
        @unknown default:
            pendingSharingAfterLocationPermission = false
        }
        withAnimation(.easeInOut(duration: 0.18)) {
            showSharingLocationDeniedModal = false
        }
    }

    private func continueSharingAfterLocationAuthorized() {
        guard pendingContinuationRequiresMotion else {
            continueDriveAfterForegroundLocationAuthorized()
            return
        }
        guard locationService.hasAttemptedMotionPermissionPrompt else {
            pendingSharingAfterMotionPermission = true
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingMotionDeniedModal = true
            }
            return
        }
        locationService.refreshMotionAuthorizationStatus()
        switch locationService.motionAuthorizationStatus {
        case .authorized:
            locationService.startMotionActivityUpdatesIfAuthorized()
            if pendingContinuationRequiresBackgroundLocation {
                continueToBackgroundLocationPrimerIfNeeded()
            } else {
                completePendingShareFlowAfterPermissions()
            }
        case .notDetermined:
            pendingSharingAfterMotionPermission = true
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingMotionDeniedModal = true
            }
        case .denied, .restricted:
            pendingSharingAfterMotionPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingMotionDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private func enableMotionForSharingFromModal() {
        guard locationService.hasAttemptedMotionPermissionPrompt else {
            pendingSharingAfterMotionPermission = true
            locationService.requestMotionPermissionIfNeeded()
            pollMotionPermissionAfterEnable()
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingMotionDeniedModal = false
            }
            return
        }
        locationService.refreshMotionAuthorizationStatus()
        switch locationService.motionAuthorizationStatus {
        case .authorized:
            pendingSharingAfterMotionPermission = false
            locationService.startMotionActivityUpdatesIfAuthorized()
            if pendingContinuationRequiresBackgroundLocation {
                continueToBackgroundLocationPrimerIfNeeded()
            } else {
                completePendingShareFlowAfterPermissions()
            }
        case .notDetermined:
            pendingSharingAfterMotionPermission = true
            locationService.requestMotionPermissionIfNeeded()
            pollMotionPermissionAfterEnable()
        case .denied, .restricted:
            pendingSharingAfterMotionPermission = false
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        @unknown default:
            pendingSharingAfterMotionPermission = false
        }
        withAnimation(.easeInOut(duration: 0.18)) {
            showSharingMotionDeniedModal = false
        }
    }

    @discardableResult
    private func handleMotionPermissionStateForPendingSharing(_ state: MotionPermissionState) -> Bool {
        guard pendingSharingAfterMotionPermission else { return true }
        switch state {
        case .authorized:
            pendingSharingAfterMotionPermission = false
            locationService.startMotionActivityUpdatesIfAuthorized()
            if pendingContinuationRequiresBackgroundLocation {
                continueToBackgroundLocationPrimerIfNeeded()
            } else {
                completePendingShareFlowAfterPermissions()
            }
            return true
        case .denied, .restricted:
            pendingSharingAfterMotionPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showSharingMotionDeniedModal = true
            }
            return true
        case .notDetermined:
            return false
        }
    }

    private func pollMotionPermissionAfterEnable() {
        Task { @MainActor in
            for _ in 0..<12 {
                try? await Task.sleep(nanoseconds: 500_000_000)
                guard pendingSharingAfterMotionPermission else { return }
                locationService.refreshMotionAuthorizationStatus()
                if handleMotionPermissionStateForPendingSharing(locationService.motionAuthorizationStatus) {
                    return
                }
            }
        }
    }

    private func mapScreenOnAppear() {
        wasMapboxRenderingSuspended = shouldSuspendMapboxRendering
        OttoLog.map.info(
            "onAppear friends=\(self.visibleFriends.count) selectedCircle=\(appState.selectedCircleID) currentUserID=\(appState.currentUserID) hasInitialCamera=\(self.hasAppliedInitialCamera)"
        )
        if isActive {
            appState.isMapScreenActive = true
            appState.requestLocationSessionSync()
        }
        Task { await refreshInvitesIfNeeded(force: true) }
        if appState.isAuthenticated {
            Task { await appState.refreshUpcomingEvents() }
        }
        Task {
            await appState.refreshSavedPlaces()
            guard shouldRunLiveMapTasks else { return }
            await appState.refreshPresenceForSelectedCircle()
            await loadDriveLinesForSelectedCircle()
        }
        loadLayerPreferencesIfNeeded()
        quickRouteRecordDriveDraft = appState.recordDriveOnStartEnabled
        reconcileVisibleCircleLayersWithCirclesList()
        applyDriveLayerPreference(showDrivesLayer, animated: false)
        if applyPendingMapRouteSelectionIfNeeded() {
            // Drive Summary deep link: keep manual camera on the selected route.
        } else if applyPendingSavedPlaceMapFocusIfNeeded() {
            // User chose a saved place; don’t immediately snap back to GPS follow.
        } else if applyPendingSharingSheetIfNeeded() {
            // Deep link / widget: show sharing sheet instead of recentering.
        } else if appState.pendingLocationSharingFocus != nil {
            Task { await applyPendingLocationSharingFocusFromPushIfNeeded() }
        } else if !hasAppliedInitialCamera, cameraFollowMode.isFollowingSelf {
            recenterOnCurrentUser(force: false)
        }
        maybePresentMapLocationPrimer()
        syncMapRouteSessionActiveToAppState()
    }

    private func syncMapRouteSessionActiveToAppState() {
        let active = isBuildingDriveLine || activeDriveLine != nil || appState.activeRouteDriveSession != nil || appState.activeDriveSession != nil
        if appState.isMapRouteSessionActive != active {
            appState.isMapRouteSessionActive = active
        }
    }

    private func mapScreenOnDisappear() {
        OttoLog.map.info("onDisappear")
        drivesToggleTask?.cancel()
    }

    private func mapScreenBecameActive() {
        appState.requestLocationSessionSync()
        if cameraFollowMode.isFollowingSelf {
            recenterOnCurrentUser(force: false)
        } else {
            recenterOnFollowedFriendIfNeeded()
            applyFollowedSquadCameraIfNeeded()
        }
    }

    private func reactToMapboxSuspendTransition() {
        let suspended = shouldSuspendMapboxRendering
        let wasSuspended = wasMapboxRenderingSuspended
        wasMapboxRenderingSuspended = suspended

        #if DEBUG
        if wasSuspended != suspended {
            MapboxGLSuspendDebugLog.logTransition(
                suspended: suspended,
                localRouteBuilder: isShowingRouteBuilder,
                globalRouteBuilder: appState.isRouteBuilderPresented,
                isActive: isActive
            )
        } else {
            logMapboxSuspendDiagnostics(event: "suspend inputs changed (no transition)")
        }
        #endif

        if !wasSuspended, suspended {
            mapboxMap = nil
            clearChatSharedPlacePeekMarkers()
            return
        }
        guard wasSuspended, !suspended else { return }
        mapScreenMapboxRenderingResumed()
    }

    private func mapScreenMapboxRenderingResumed() {
        mapboxMap = nil
        syncMarkerSmoothingTargets()
        applyPendingRouteBuilderMapRestoreIfNeeded()
        if isActive {
            mapScreenBecameActive()
            guard shouldRunLiveMapTasks else { return }
            Task {
                await appState.refreshPresenceForSelectedCircle()
                await loadDriveLinesForSelectedCircle()
            }
        } else if cameraFollowMode.isFollowingSelf {
            recenterOnCurrentUser(force: false)
        } else {
            recenterOnFollowedFriendIfNeeded()
            applyFollowedSquadCameraIfNeeded()
        }
    }

    /// Consumes `AppState.pendingLocationSharingFocus` after a “started sharing” push (Map tab + follow sharer).
    private func applyPendingLocationSharingFocusFromPushIfNeeded() async {
        guard let focus = appState.consumePendingLocationSharingFocus() else { return }
        visibleCircleLayerIDs.insert(focus.circleID)
        await appState.refreshPresence(for: focus.circleID, showsStartedSharingToast: false)
        guard let friend = appState.circles.first(where: { $0.id == focus.circleID })?.members
            .first(where: { $0.id == focus.sharerUserID })
        else { return }
        revealMapLayerIfNeeded(for: friend)
        followFriend(friend)
    }

    private func mapScreenSelectedCircleIDChanged() {
        guard shouldRunLiveMapTasks else { return }
        Task {
            await appState.refreshPresenceForSelectedCircle()
            await loadDriveLinesForSelectedCircle()
        }
    }

    private func mapScreenShowPublicCircleLayerChanged(isOn: Bool) {
        persistLayerPreferences()
        guard isOn else { return }
        Task { await appState.refreshPublicPresence() }
    }

    private func mapScreenShowMyPlacesLayerChanged() {
        persistLayerPreferences()
    }

    private func mapScreenShowEventsLayerChanged() {
        persistLayerPreferences()
    }

    private func mapScreenShowRaceTracksLayerChanged() {
        persistLayerPreferences()
    }

    private func mapScreenShowTrafficLayerChanged() {
        persistLayerPreferences()
        if let mapboxMap {
            MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: showTrafficLayer)
        }
    }

    private func mapScreenShowDrivesLayerChanged() {
        persistLayerPreferences()
        applyDriveLayerPreference(showDrivesLayer, animated: true)
    }

    private func mapScreenVisibleCircleLayerIDsChanged() {
        persistLayerPreferences()
        guard shouldRunLiveMapTasks else { return }
        Task {
            for circleID in visibleCircleLayerIDs {
                await appState.refreshPresence(for: circleID, showsStartedSharingToast: true)
            }
        }
    }

    private func mapScreenLastLocationChanged(latest: CLLocation?) {
        if let latest, cameraFollowMode.isFollowingSelf {
            if usesDriveCameraPitch {
                syncDriveCameraTarget(from: latest)
            } else {
                recenterOnCurrentUser(force: true)
            }
        }

        guard shouldRunLiveMapTasks else { return }
        if appState.isSharingEnabled {
            Task {
                let loc = locationService.latestSample ?? latest
                guard let loc else { return }
                await appState.throttledRecordDrivePathSample(
                    location: loc,
                    speedMetersPerSecond: locationService.effectiveSpeedMetersPerSecond(),
                    movementMode: locationService.movementMode
                )
            }
        }
    }

    private func mapScreenMapPreviewSessionChanged(old: String?, new: String?) {
        guard new == nil else { return }
        // Closing a sheet must not reclaim camera ownership; only explicit follow/focus actions move the map.
    }

    private func mapScreenPresenceTimerFired() {
        guard shouldRunLiveMapTasks else { return }
        sharingNow = Date()
        Task {
            let visibleIDs =
                visibleCircleLayerIDs.isEmpty
                    ? Set(appState.circles.map(\.id).filter { !$0.isEmpty })
                    : visibleCircleLayerIDs
            for circleID in visibleIDs where !circleID.isEmpty {
                await appState.refreshPresence(for: circleID, showsStartedSharingToast: true)
            }
            if showPublicCircleLayer {
                await appState.refreshPublicPresence()
            }
            refreshTravelSurfaceSamples()
            let loc = locationService.latestSample ?? locationService.lastLocation
            let speed = locationService.effectiveSpeedMetersPerSecond()
            await appState.pushPresence(
                location: loc,
                speedMetersPerSecond: speed,
                movementMode: locationService.movementMode
            )
            await refreshInvitesIfNeeded()
            updateDwellStates()
            if let loc {
                await appState.throttledRecordDrivePathSample(
                    location: loc,
                    speedMetersPerSecond: speed,
                    movementMode: locationService.movementMode
                )
            }
        }
    }

    /// Unmount Mapbox only while Route Builder is open so two GL maps never render at once.
    /// Tab switches keep the map mounted (hidden via `rootTabVisibility` opacity).
    private var shouldSuspendMapboxRendering: Bool {
        isShowingRouteBuilder || appState.isRouteBuilderPresented
    }

    #if DEBUG
    private func logMapboxSuspendDiagnostics(event: String) {
        MapboxGLSuspendDebugLog.log(
            event: event,
            suspended: shouldSuspendMapboxRendering,
            localRouteBuilder: isShowingRouteBuilder,
            globalRouteBuilder: appState.isRouteBuilderPresented,
            isActive: isActive
        )
    }
    #endif

    private var baseMapView: some View {
        ZStack {
            if shouldSuspendMapboxRendering {
                Color.black
                    .ignoresSafeArea(edges: .top)
            } else {
                OttoMapboxMapView(
                    viewport: $mapViewport,
                    allowsInteraction: mapAllowsHitTesting
                ) { region in
                    scheduleObservedCameraChange(region)
                } onUserGesture: {
                    guard !isProgrammaticCameraMove, !isApplyingDriveCameraUpdate else { return }
                    cameraFollowMode = .manual
                } onMapLoaded: {
                    guard appState.pendingMapFocus == nil else { return }
                    guard cameraFollowMode.isFollowingSelf else { return }
                    recenterOnCurrentUser(force: true)
                } onMapboxMapReady: { map in
                    #if DEBUG
                    OttoRouteBuilderDebugLog.mapScreenTabMapMounted()
                    #endif
                    mapboxMap = map
                    MapboxTrafficLayerController.sync(map: map, showTraffic: showTrafficLayer)
                    refreshTravelSurfaceSamples()
                } onMapTap: { coordinate in
                    if isBuildingDriveLine {
                        handleDriveLineTap(at: coordinate)
                    }
                } onMapLongPress: { coordinate in
                    if isBuildingDriveLine {
                        handleDriveLineTap(at: coordinate)
                    } else {
                        guard appState.isAuthenticated, !appState.currentUserID.isEmpty else { return }
                        guard !isShowingMapPlaceActionSheet, !isShowingSavePlaceSheet, !isSavingPlace else { return }
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.prepare()
                        impact.impactOccurred()
                        beginMapPlaceActionFlow(at: coordinate)
                    }
                } content: {
                    mapboxNativeContent
                }
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(
                            key: MapViewportLayoutSizeKey.self,
                            value: proxy.size
                        )
                    }
                }
                .onPreferenceChange(MapViewportLayoutSizeKey.self) { mapViewportLayoutSize = $0 }
                .overlay {
                    Rectangle()
                        .fill(.black.opacity(0.12))
                        .allowsHitTesting(false)
                }
                .ignoresSafeArea(edges: .top)
                .onDisappear {
                    #if DEBUG
                    OttoRouteBuilderDebugLog.mapScreenTabMapUnmounted()
                    #endif
                }
            }

            if !isBuildingDriveLine, !shouldSuspendMapboxRendering {
                VStack {
                    Spacer()
                    HStack(alignment: .bottom) {
                        targetButton
                        Spacer()
                        VStack(spacing: 10) {
                            searchButton
                            layersButton
                            driveLineButton
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.bottom, mapFabOverlayBottomPadding)
                }
            }

            if isBuildingDriveLine {
                VStack {
                    Spacer()
                    driveLineWizardCard
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 0)
                }

                HStack {
                    Spacer()
                    driveLineEditButtons
                        .padding(.trailing, 14)
                        .padding(.bottom, 132)
                }
            }

            if mapLocationDiagnosticsEnabled, isActive, appState.canAccessInternalDebugTools, !shouldSuspendMapboxRendering {
                VStack {
                    Spacer()
                    HStack {
                        MapLocationDiagnosticsOverlay()
                            .padding(.leading, 10)
                            .padding(.bottom, 100)
                        Spacer(minLength: 0)
                    }
                }
                .allowsHitTesting(false)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if effectiveDriveDockBottomInset > 0 {
                Color.clear
                    .frame(height: effectiveDriveDockBottomInset)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.86), value: isDriveDockShareExpanded)
        .animation(.spring(response: 0.34, dampingFraction: 0.86), value: effectiveDriveDockBottomInset)
        .overlay(alignment: .bottom) {
            if isDriveLaunchDockVisible, !isBuildingDriveLine, !shouldSuspendMapboxRendering {
                driveLaunchDockOverlay(expandedMaxHeight: driveDockPanelMaxHeight)
                    .frame(maxWidth: .infinity)
                    .onPreferenceChange(DriveDockHeightKey.self) { height in
                        guard height > 0, isDriveLaunchDockVisible, !isDriveDockShareExpanded else { return }
                        driveDockLayoutHeight = height
                    }
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            topChrome
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 4)
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0.94), Color.black.opacity(0.6), Color.clear],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea(edges: .top)
                )
        }
    }

    /// Route markers stay visible in pitched drive follow (Mapbox otherwise culls outside camera padding).
    private func routeMapViewAnnotation<Content: View>(
        coordinate: CLLocationCoordinate2D,
        priority: Int,
        @ViewBuilder content: @escaping () -> Content
    ) -> MapViewAnnotation {
        MapViewAnnotation(coordinate: coordinate, content: content)
            .allowOverlap(true)
            .ignoreCameraPadding(true)
            .allowOverlapWithPuck(usesDriveCameraPitch)
            .priority(priority)
    }

    @MapboxMaps.MapContentBuilder
    private var mapboxNativeContent: some MapboxMaps.MapContent {
        if let liveDriveTrailSamples = activeLiveDriveTrailSamples {
            RouteSpeedGradientMapContent(
                sourceID: "map-live-drive-trail",
                samples: liveDriveTrailSamples
            )
        }

        RouteMapLineMapContent(
            sourceID: Self.selectedRouteLineSourceID,
            coordinates: selectedRouteLineCoordinates,
            palette: .livePurple
        )

        ForEvery(selectedRouteMapPoints) { point in
            routeMapViewAnnotation(
                coordinate: point.coordinate,
                priority: routePointOverlapPriority(for: point)
            ) {
                selectedRoutePointMarker(point)
                    .id(
                        RouteMapMarkerLOD.annotationRefreshID(
                            pointID: point.id,
                            markerType: point.markerType,
                            latitudeDelta: markerLODLatitudeDelta,
                            stableForRouteDrive: isRouteDriveSessionOnMap
                        )
                    )
            }
        }

        if showsRouteCheckpointMapDebug {
            ForEvery(routeCheckpointDebugWaypoints) { point in
                routeMapViewAnnotation(
                    coordinate: point.coordinate,
                    priority: Int.max - point.index
                ) {
                    RouteCheckpointMapDebugLabel(
                        snapshot: checkpointDebugSnapshot(for: point),
                        mapboxVisible: checkpointDebugMapboxVisible[point.index]
                    )
                    .id("route-checkpoint-debug-\(point.id)")
                }
                .onVisibilityChanged { visible in
                    checkpointDebugMapboxVisible[point.index] = visible
                    OttoLog.map.debug(
                        "Route checkpoint debug #\(point.index) mapboxVisible=\(visible)"
                    )
                }
            }
        }

        if showEventsLayer {
            PolygonAnnotationGroup(eventRadiusOverlays) { overlay in
                PolygonAnnotation(polygon: overlay.polygon)
            }
            .fillColor(UIColor.systemPurple)
            .fillOpacity(0.14)
            .fillOutlineColor(UIColor.systemPurple.withAlphaComponent(0.48))
        }

        if showMyPlacesLayer {
            ForEvery(appState.savedPlaces) { place in
                MapViewAnnotation(
                    coordinate: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
                ) {
                    Button {
                        mapPreviewSession = .savedPlace(place)
                    } label: {
                        MapDiscoveryMarkerLODView(
                            kind: .savedPlace,
                            latitudeDelta: markerLODLatitudeDelta
                        ) { pinScale in
                            OttoMapSavedPlaceMarker(
                                isSelected: selectedSavedPlacePreviewID == place.id,
                                pinScale: pinScale
                            )
                        }
                    }
                    .buttonStyle(.plain)
                    .id(
                        MapDiscoveryMarkerLOD.annotationRefreshID(
                            id: place.id,
                            kind: .savedPlace,
                            latitudeDelta: markerLODLatitudeDelta
                        )
                    )
                }
                .allowOverlap(true)
                .priority(
                    RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(
                        for: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
                    )
                )
            }
        }

        ForEvery(chatSharedPlacePeekMarkersNeedingMapPin) { place in
            MapViewAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
            ) {
                Button {
                    mapPreviewSession = .savedPlace(place)
                } label: {
                    MapDiscoveryMarkerLODView(
                        kind: .savedPlace,
                        latitudeDelta: markerLODLatitudeDelta
                    ) { pinScale in
                        OttoMapSavedPlaceMarker(
                            isSelected: selectedSavedPlacePreviewID == place.id,
                            pinScale: pinScale
                        )
                    }
                }
                .buttonStyle(.plain)
                .id(
                    MapDiscoveryMarkerLOD.annotationRefreshID(
                        id: "peek:\(place.id)",
                        kind: .savedPlace,
                        latitudeDelta: markerLODLatitudeDelta
                    )
                )
            }
            .allowOverlap(true)
            .priority(
                RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(
                    for: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
                )
            )
        }

        if showEventsLayer {
            ForEvery(anchoredUpcomingEventGroups) { group in
                MapViewAnnotation(coordinate: group.coordinate) {
                    Button {
                        let sorted = group.events.sorted { $0.startsAt < $1.startsAt }
                        if let primary = sorted.first {
                            mapPreviewSession = .upcomingEvent(primary: primary, siblings: Array(sorted.dropFirst()))
                        }
                    } label: {
                        MapDiscoveryMarkerLODView(
                            kind: .event,
                            latitudeDelta: markerLODLatitudeDelta,
                            clusterCount: group.events.count > 1 ? group.events.count : nil
                        ) { pinScale in
                            OttoMapEventMarker(
                                isSelected: isEventBeaconPreviewActive(for: group),
                                clusterCount: group.events.count > 1 ? group.events.count : nil,
                                isUserGoing: group.events.contains { $0.currentUserRsvp == "going" },
                                pinScale: pinScale
                            )
                        }
                    }
                    .buttonStyle(.plain)
                    .id(
                        MapDiscoveryMarkerLOD.annotationRefreshID(
                            id: group.id,
                            kind: .event,
                            latitudeDelta: markerLODLatitudeDelta
                        )
                    )
                }
                .allowOverlap(true)
                .priority(RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(for: group.coordinate))
            }
        }

        if showRaceTracksLayer {
            ForEvery(plottableRaceTracks) { track in
                if let coordinate = track.coordinate {
                    MapViewAnnotation(coordinate: coordinate) {
                        Button {
                            mapPreviewSession = .raceTrack(track)
                        } label: {
                            MapDiscoveryMarkerLODView(
                                kind: .raceTrack,
                                latitudeDelta: markerLODLatitudeDelta
                            ) { pinScale in
                                OttoMapRaceTrackMarker(
                                    isSelected: selectedRaceTrackPreviewID == track.id,
                                    pinScale: pinScale
                                )
                            }
                        }
                        .buttonStyle(.plain)
                        .id(
                            MapDiscoveryMarkerLOD.annotationRefreshID(
                                id: track.id,
                                kind: .raceTrack,
                                latitudeDelta: markerLODLatitudeDelta
                            )
                        )
                    }
                    .allowOverlap(true)
                    .priority(RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(for: coordinate))
                }
            }
        }

        ForEvery(groupedVisibleFriendsForMap) { group in
            MapViewAnnotation(coordinate: group.coordinate) {
                Button {
                    handleFriendGroupTap(group)
                } label: {
                    BouncyMarkerContainer {
                        if group.members.count == 1, let friend = group.members.first {
                            let isCurrentUser = isSelfPresenceFriend(friend)
                            let brandLogoURL = presenceBrandLogoURL(for: friend)
                            FriendAnnotationView(
                                friend: friend,
                                isCurrentUser: isCurrentUser,
                                brandLogoURL: brandLogoURL,
                                dwellText: statusLabel(for: friend.id),
                                travelSurface: travelSurfaceTracker.surface(for: friend.id),
                                horizonScale: presenceHorizonScale(
                                    for: group.coordinate,
                                    isCurrentUser: isCurrentUser
                                )
                            )
                        } else {
                            CompositeFriendAnnotationView(
                                members: group.members,
                                currentUserID: appState.currentUserID,
                                dwellText: statusLabel(for: group.members),
                                horizonScale: presenceHorizonScale(
                                    for: group.coordinate,
                                    isCurrentUser: false
                                )
                            )
                        }
                    }
                }
                .buttonStyle(.plain)
                .id(presenceMapAnnotationID(for: group))
            }
            .allowOverlap(true)
            .ignoreCameraPadding(usesDriveCameraPitch)
            .allowOverlapWithPuck(usesDriveCameraPitch)
            .priority(
                presenceOverlapPriority(
                    for: group.coordinate,
                    tieBreaker: group.id.hashValue
                )
            )
        }
    }

    private var selectedRouteLineCoordinates: [CLLocationCoordinate2D] {
        guard let selectedRoute else { return [] }
        let roadCoordinates = coordinates(from: selectedRoute.roadCoordinates)
        if roadCoordinates.count >= 2 { return roadCoordinates }
        return coordinates(from: selectedRoute.points)
    }

    private var selectedRouteMapPoints: [SelectedRouteMapPoint] {
        guard let selectedRoute else { return [] }
        let indexed = selectedRoute.points.enumerated().compactMap { index, point -> SelectedRouteMapPoint? in
            guard point.markerType != "path" else { return nil }
            guard let coordinate = coordinate(from: point) else { return nil }
            return SelectedRouteMapPoint(
                id: "\(selectedRoute.id)-\(index)",
                coordinate: coordinate,
                markerType: point.markerType,
                index: index
            )
        }
        let afterStrip: [SelectedRouteMapPoint]
        if RouteMapMarkerLOD.shouldStripToStartFinishOnly(
            shouldShowAllRouteMarkers: shouldShowAllRouteMarkers,
            latitudeDelta: markerLODLatitudeDelta
        ) {
            afterStrip = indexed.filter { $0.markerType == "start" || $0.markerType == "finish" }
        } else {
            afterStrip = indexed
        }
        return afterStrip.filter { shouldShowRouteMapPoint($0) }
    }

    private func selectedRoutePointMarker(_ point: SelectedRouteMapPoint) -> some View {
        let isCompleted = appState.activeRouteDriveSession?.completedWaypointIndexes.contains(point.index) == true
        return RouteMapMarkerLODView(
            markerType: point.markerType,
            isCompleted: isCompleted,
            latitudeDelta: markerLODLatitudeDelta,
            horizonScale: routePointHorizonScale(for: point.coordinate)
        )
    }

    private var topChrome: some View {
        HStack {
            Color.clear
                .frame(width: 32, height: 32)
            Spacer()

            sharingPill

            Spacer()

            Color.clear
                .frame(width: 32, height: 32)
        }
    }

    private func refreshInvitesIfNeeded(force: Bool = false) async {
        let now = Date()
        let interval: TimeInterval = 25
        guard force || now.timeIntervalSince(lastInvitePollAt) >= interval else { return }
        lastInvitePollAt = now
        await appState.refreshMyCircleInvites()
    }

    private func syncMarkerSmoothingTargets() {
        let previousByID = previousVisibleFriendsByID
        let currentIDs = Set(visibleFriends.map(\.id))
        targetFriendCoordinates = Dictionary(
            uniqueKeysWithValues: visibleFriends.map { ($0.id, $0.coordinate) }
        )
        renderedFriendCoordinates = renderedFriendCoordinates.filter { currentIDs.contains($0.key) }
        for friend in visibleFriends {
            if renderedFriendCoordinates[friend.id] == nil {
                // First appearance on map: place directly at current coordinate.
                renderedFriendCoordinates[friend.id] = friend.coordinate
                continue
            }

            // If someone just transitioned to active sharing, snap to live position immediately.
            if let previous = previousByID[friend.id], !previous.isActive && friend.isActive {
                renderedFriendCoordinates[friend.id] = friend.coordinate
            }

            // Self pin: use device GPS directly on Map (no smoothing lag) when not live-sharing.
            if friend.id == appState.currentUserID,
               isLocationAuthorizedForMapPin,
               !appState.isPublishingLiveSharingPresence
            {
                renderedFriendCoordinates[friend.id] = friend.coordinate
            }
        }
        previousVisibleFriendsByID = Dictionary(uniqueKeysWithValues: visibleFriends.map { ($0.id, $0) })
    }

    private func reconcilePresenceFreshness() {
        let now = Date()
        let currentIDs = Set(currentCircleFriends.map(\.id))

        for friend in currentCircleFriends {
            if friend.id == appState.currentUserID { continue }

            let previous = lastPresenceSnapshotByFriendID[friend.id]
            lastPresenceSnapshotByFriendID[friend.id] = PresenceSnapshot(
                coordinate: friend.coordinate,
                speedMph: friend.speedMph,
                isActive: friend.isActive
            )

            if friend.isActive {
                lastActiveSeenAtByFriendID[friend.id] = now
                if previous == nil {
                    lastLocationUpdateAtByFriendID[friend.id] = now
                    continue
                }

                let movedMeters = CLLocation(
                    latitude: previous!.coordinate.latitude,
                    longitude: previous!.coordinate.longitude
                ).distance(
                    from: CLLocation(latitude: friend.coordinate.latitude, longitude: friend.coordinate.longitude)
                )
                let speedChanged = abs(previous!.speedMph - friend.speedMph) >= 2
                let becameActive = previous!.isActive == false
                if movedMeters >= 7 || speedChanged || becameActive {
                    lastLocationUpdateAtByFriendID[friend.id] = now
                } else if lastLocationUpdateAtByFriendID[friend.id] == nil {
                    lastLocationUpdateAtByFriendID[friend.id] = now
                }
            }
        }

        // Keep dictionaries tidy when members disappear entirely.
        lastPresenceSnapshotByFriendID = lastPresenceSnapshotByFriendID.filter { currentIDs.contains($0.key) }
        lastLocationUpdateAtByFriendID = lastLocationUpdateAtByFriendID.filter { currentIDs.contains($0.key) }
        lastActiveSeenAtByFriendID = lastActiveSeenAtByFriendID.filter { currentIDs.contains($0.key) }
    }

    private func stepMarkerSmoothing() {
        guard !targetFriendCoordinates.isEmpty else { return }
        var next = renderedFriendCoordinates
        for (id, target) in targetFriendCoordinates {
            guard let current = next[id] else {
                next[id] = target
                continue
            }
            next[id] = CLLocationCoordinate2D(
                latitude: interpolate(current.latitude, target.latitude, factor: 0.30),
                longitude: interpolate(current.longitude, target.longitude, factor: 0.30)
            )
        }
        renderedFriendCoordinates = next
    }

    private func refreshTravelSurfaceSamples() {
        guard MapTravelSurfaceSampler.waterSurfaceDetectionEnabled else { return }
        guard isActive, let mapboxMap else { return }
        let movingFriends = displayedFriends.filter { friend in
            guard friend.isActive else { return false }
            return Double(friend.speedMph) >= MapTravelSurfaceSampler.minSpeedMphForBoat
        }
        let activeIDs = Set(movingFriends.map(\.id))
        travelSurfaceTracker.removeUsers(notIn: activeIDs)
        let now = Date()
        for friend in movingFriends {
            guard travelSurfaceTracker.shouldSample(userID: friend.id, now: now) else { continue }
            travelSurfaceTracker.markSampled(userID: friend.id, now: now)
            let userID = friend.id
            let speedMph = Double(friend.speedMph)
            let coordinate = friend.coordinate
            MapTravelSurfaceSampler.sample(
                mapboxMap: mapboxMap,
                coordinate: coordinate,
                speedMph: speedMph
            ) { instantaneous in
                Task { @MainActor in
                    travelSurfaceTracker.ingest(userID: userID, instantaneous: instantaneous)
                }
            }
        }
    }

    private var savePlaceSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                OttoMapSheetHeader(
                    title: "Save place",
                    onDone: { isShowingSavePlaceSheet = false },
                    doneDisabled: isSavingPlace
                )
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 8)

                Form {
                Section {
                    if savePlaceIsResolving {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("Looking up this location…")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    TextField("Place name", text: $savePlaceNameDraft)
                        .textInputAutocapitalization(.words)
                    if let addr = savePlaceAddressLine, !addr.isEmpty {
                        Text(addr)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } footer: {
                    Text(String(localized: "map_save_place_footer"))
                        .font(.caption2)
                }

                Section {
                    Button {
                        Task { await confirmSavePlaceFromMap() }
                    } label: {
                        if isSavingPlace {
                            HStack {
                                ProgressView()
                                Text("Saving…")
                            }
                            .frame(maxWidth: .infinity)
                        } else {
                            Text("Save to My places")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(
                        savePlaceIsResolving
                            || isSavingPlace
                            || savePlaceNameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    )
                }
                }
            }
        }
    }

    private func beginMapPlaceActionFlow(at coordinate: CLLocationCoordinate2D) {
        savePlaceTapCoordinate = coordinate
        savePlaceNameDraft = ""
        savePlaceAddressLine = nil
        savePlacePoiCategory = nil
        savePlaceKind = "coordinates"
        savePlaceIsResolving = true
        isShowingMapPlaceActionSheet = true
        Task {
            let resolved = await resolvePlaceLabel(at: coordinate)
            await MainActor.run {
                savePlaceIsResolving = false
                savePlaceKind = resolved.placeKind
                if let name = resolved.name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    savePlaceNameDraft = name
                }
                savePlaceAddressLine = resolved.address
                savePlacePoiCategory = resolved.category
            }
        }
    }

    private func openSavePlaceSheetFromMapPlaceAction() {
        isShowingMapPlaceActionSheet = false
        isShowingSavePlaceSheet = true
    }

    private func buildAdhocPlaceSharePayload() -> MapMarkerSharePayload? {
        guard let coordinate = savePlaceTapCoordinate else { return nil }
        let trimmedName = savePlaceNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        return MapMarkerSharePayload.adhocPlace(
            name: trimmedName.isEmpty ? nil : trimmedName,
            addressSummary: savePlaceAddressLine,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )
    }

    private func beginAdhocPlaceShareToChatFromMapPlaceAction() {
        guard let payload = buildAdhocPlaceSharePayload() else { return }
        adhocPlaceSharePayload = payload
        isShowingMapPlaceActionSheet = false
        isShowingAdhocPlaceShareChatSheet = true
    }

    private func placeKindFromMapItem(_ item: MKMapItem) -> String {
        guard let cat = item.pointOfInterestCategory else { return "other" }
        let r = cat.rawValue.lowercased()
        if r.contains("restaurant") || r.contains("food") || r.contains("cafe") || r.contains("bakery") || r.contains("brewery") {
            return "restaurant"
        }
        if r.contains("gas") || r.contains("fuel") || r.contains("evcharger") || r.contains("charging") {
            return "gas_station"
        }
        return "other"
    }

    /// Prefer a nearby restaurant / gas station POI; otherwise reverse-geocode for a suggested label.
    private func resolvePlaceLabel(at coordinate: CLLocationCoordinate2D) async -> (name: String?, category: String?, address: String?, placeKind: String) {
        let region = MKCoordinateRegion(
            center: coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.004, longitudeDelta: 0.004)
        )
        let poiRequest = MKLocalPointsOfInterestRequest(coordinateRegion: region)
        poiRequest.pointOfInterestFilter = MKPointOfInterestFilter(including: [.restaurant, .gasStation])
        do {
            let response = try await MKLocalSearch(request: poiRequest).start()
            let tapLocation = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
            let scored: [(MKMapItem, CLLocationDistance)] = response.mapItems.compactMap { item -> (MKMapItem, CLLocationDistance)? in
                let c = item.location.coordinate
                guard CLLocationCoordinate2DIsValid(c) else { return nil }
                let loc = CLLocation(latitude: c.latitude, longitude: c.longitude)
                return (item, tapLocation.distance(from: loc))
            }
            if let best = scored.min(by: { $0.1 < $1.1 }), best.1 <= 150 {
                let item = best.0
                let cat = item.pointOfInterestCategory?.rawValue
                let addr = item.address?.shortAddress ?? item.address?.fullAddress ?? item.name
                return (item.name, cat, addr, placeKindFromMapItem(item))
            }
        } catch {
            OttoLog.map.debug("POI lookup failed: \(String(describing: error))")
        }
        return await reverseGeocodeLabel(at: coordinate)
    }

    private func reverseGeocodeLabel(at coordinate: CLLocationCoordinate2D) async -> (name: String?, category: String?, address: String?, placeKind: String) {
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        guard let request = MKReverseGeocodingRequest(location: location) else {
            return (nil, nil, nil, "coordinates")
        }
        do {
            let items = try await request.mapItems
            guard let item = items.first else { return (nil, nil, nil, "coordinates") }

            func nonEmptyTrimmed(_ s: String?) -> String? {
                guard let t = s?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty else { return nil }
                return t
            }

            let shortAddr = nonEmptyTrimmed(item.address?.shortAddress)
            let fullAddr = nonEmptyTrimmed(item.address?.fullAddress)
            let itemName = nonEmptyTrimmed(item.name)

            let name = itemName ?? shortAddr
            let addr = fullAddr ?? shortAddr
            let kind = (itemName != nil || shortAddr != nil || fullAddr != nil) ? "address" : "coordinates"
            return (name, nil, addr, kind)
        } catch {
            return (nil, nil, nil, "coordinates")
        }
    }

    private func refreshSharingWidgetPlaceLabelIfNeeded() async {
        guard OttoSharingWidgetConfiguration.isEnabled else { return }
        guard appState.isAuthenticated else { return }
        guard let sample = locationService.latestSample else { return }
        let coord = sample.coordinate
        guard CLLocationCoordinate2DIsValid(coord) else { return }
        let now = Date()
        let shouldGeocode = await MainActor.run {
            guard now.timeIntervalSince(lastWidgetPlaceGeocodeAt) >= 600 else { return false }
            lastWidgetPlaceGeocodeAt = now
            return true
        }
        guard shouldGeocode else { return }
        let (_, _, addr, _) = await reverseGeocodeLabel(at: coord)
        let raw = addr?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty else { return }
        let parts = raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let label: String
        if parts.count >= 2 {
            label = "\(parts[0]), \(parts[1])"
        } else if let first = parts.first {
            label = String(first)
        } else {
            return
        }
        await MainActor.run {
            appState.updateWidgetPlaceLabelForSharingWidget(label)
        }
    }

    private func confirmSavePlaceFromMap() async {
        guard let coordinate = savePlaceTapCoordinate else { return }
        let trimmed = savePlaceNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        isSavingPlace = true
        do {
            _ = try await appState.createSavedPlace(
                name: trimmed,
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                placeKind: savePlaceKind,
                source: "ios",
                poiCategory: savePlacePoiCategory,
                addressSummary: savePlaceAddressLine
            )
            await MainActor.run {
                isSavingPlace = false
                isShowingSavePlaceSheet = false
            }
        } catch {
            await MainActor.run {
                isSavingPlace = false
                appState.errorMessage = "Couldn’t save this place. Try again."
            }
        }
    }

    private func interpolate(_ current: Double, _ target: Double, factor: Double) -> Double {
        current + ((target - current) * factor)
    }

    /// Split clusters naturally as users zoom in.
    private func zoomAwareGroupingThresholdMeters() -> CLLocationDistance {
        // Approximate visible map height in meters based on latitude span.
        let visibleHeightMeters = currentLatitudeDelta * 111_000
        let derived = visibleHeightMeters * 0.07
        return min(46, max(8, derived))
    }

    private func enforceZoomBoundsIfNeeded(for region: MKCoordinateRegion) {
        guard !isAdjustingZoomBounds else { return }
        // Allow close zoom so composites can split naturally; only clamp extreme *over-zoom-in*.
        // Do not cap zoom-out — a latitude max previously fought pinch-to-zoom-wide and felt like snapping back.
        let minDelta = 0.00018
        guard region.span.latitudeDelta < minDelta - 0.00001 else { return }
        let clampedDelta = minDelta

        isAdjustingZoomBounds = true
        let scale = region.span.longitudeDelta / max(region.span.latitudeDelta, 0.000001)
        let clampedRegion = MKCoordinateRegion(
            center: region.center,
            span: MKCoordinateSpan(
                latitudeDelta: clampedDelta,
                longitudeDelta: max(0.00018, clampedDelta * scale)
            )
        )
        mapViewport = OttoMapboxCamera.viewport(for: clampedRegion)
        lastProgrammaticMapRegion = clampedRegion
        lastObservedMapRegion = clampedRegion
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 120_000_000)
            isAdjustingZoomBounds = false
        }
    }

    private func applyDriveLayerPreference(_ isEnabled: Bool, animated: Bool) {
        drivesToggleTask?.cancel()
        if !isEnabled {
            showDrivePointAnnotations = false
            showDriveOverlays = false
            return
        }

        if animated {
            withAnimation(.easeInOut(duration: 0.12)) {
                showDriveOverlays = true
            }
        } else {
            showDriveOverlays = true
        }

        // Stagger expensive point annotations slightly after polylines to avoid one-frame map rebuild spikes.
        drivesToggleTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 220_000_000)
            guard !Task.isCancelled else { return }
            showDrivePointAnnotations = true
        }
    }

    private func loadLayerPreferencesIfNeeded() {
        let defaults = UserDefaults.standard
        showDrivesLayer = false
        showDriveOverlays = false
        showDrivePointAnnotations = false
        showPublicCircleLayer = false
        if defaults.object(forKey: LayerPrefs.showMyPlaces) != nil {
            showMyPlacesLayer = defaults.bool(forKey: LayerPrefs.showMyPlaces)
        }
        if defaults.object(forKey: LayerPrefs.showEvents) != nil {
            showEventsLayer = defaults.bool(forKey: LayerPrefs.showEvents)
        }
        if defaults.object(forKey: LayerPrefs.showRaceTracks) != nil {
            showRaceTracksLayer = defaults.bool(forKey: LayerPrefs.showRaceTracks)
        }
        if defaults.object(forKey: LayerPrefs.showTraffic) != nil {
            showTrafficLayer = defaults.bool(forKey: LayerPrefs.showTraffic)
        }
        if let savedCircleIDs = defaults.array(forKey: LayerPrefs.visibleCircleIDs) as? [String] {
            visibleCircleLayerIDs = Set(savedCircleIDs)
        }
        visibleDriveLineIDs = []
    }

    /// Keeps squad layer toggles aligned with membership: default is all squads on; new squads turn on automatically.
    private func reconcileVisibleCircleLayersWithCirclesList() {
        let valid = Set(appState.circles.map(\.id).filter { !$0.isEmpty })
        let defaults = UserDefaults.standard
        let rawSaved = defaults.array(forKey: LayerPrefs.visibleCircleIDs)
        let hasSavedKey = defaults.object(forKey: LayerPrefs.visibleCircleIDs) != nil
        let savedEmptyArray = (rawSaved as? [String])?.isEmpty == true

        if !hasSavedKey || savedEmptyArray {
            visibleCircleLayerIDs = valid
            mapLayerKnownMembershipCircleIDs = valid
            return
        }

        if mapLayerKnownMembershipCircleIDs.isEmpty {
            visibleCircleLayerIDs = visibleCircleLayerIDs.intersection(valid)
            if visibleCircleLayerIDs.isEmpty, !valid.isEmpty {
                visibleCircleLayerIDs = valid
            }
        } else {
            let added = valid.subtracting(mapLayerKnownMembershipCircleIDs)
            if !added.isEmpty {
                visibleCircleLayerIDs.formUnion(added)
            }
            visibleCircleLayerIDs = visibleCircleLayerIDs.intersection(valid)
        }
        mapLayerKnownMembershipCircleIDs = valid
    }

    private func persistLayerPreferences() {
        let defaults = UserDefaults.standard
        defaults.set(false, forKey: LayerPrefs.showDrives)
        defaults.set(false, forKey: LayerPrefs.showPublic)
        defaults.set(showMyPlacesLayer, forKey: LayerPrefs.showMyPlaces)
        defaults.set(showEventsLayer, forKey: LayerPrefs.showEvents)
        defaults.set(showRaceTracksLayer, forKey: LayerPrefs.showRaceTracks)
        defaults.set(showTrafficLayer, forKey: LayerPrefs.showTraffic)
        defaults.set(Array(visibleCircleLayerIDs), forKey: LayerPrefs.visibleCircleIDs)
        defaults.set([], forKey: LayerPrefs.visibleDriveLineIDs)
    }

    private var driveSessionPillPresentation: DriveSessionPillPresentation {
        appState.driveSessionPillPresentation(
            now: sharingNow,
            routeName: selectedRoute?.name ?? appState.activeDriveSession?.routeName,
            viewerCount: friendsSharingLocationCount > 0 ? friendsSharingLocationCount : nil
        )
    }

    private var sharingPill: some View {
        DriveSessionStatusPill(
            presentation: driveSessionPillPresentation,
            onTap: {
                if driveSessionPillPresentation == .idle {
                    isShowingStartDriveSheet = true
                } else {
                    syncSharingDraftsFromSession()
                    isShowingDriveControls = true
                }
            },
            onStop: {
                isShowingStopDriveConfirmation = true
            }
        )
    }

    private var driveSessionTimeText: String {
        let start = appState.activeDriveSession?.startedAt ?? appState.sharingSessionStartedAt ?? Date()
        return appState.formatDriveSessionDuration(from: start, now: sharingNow)
    }

    private var driveSessionDistanceText: String {
        let meters = appState.activeDriveSession?.metrics.distanceMeters ?? appState.activeDriveDistanceMeters
        return appState.formatDriveSessionDistance(meters)
    }

    private var driveSessionTopSpeedText: String {
        let mph = max(
            appState.activeDriveSession?.metrics.maxSpeedMph ?? 0,
            appState.activeDriveMaxSpeedMph
        )
        return mph > 0 ? "\(Int(mph.rounded())) mph" : "—"
    }

    private var targetButton: some View {
        let isLocationTrackingAccent = isFollowingUser && followedFriendID == nil && followedSquadID == nil
        return Button {
            cameraFollowMode = .followSelf
            if let location = locationService.latestSample ?? locationService.lastLocation {
                if usesDriveCameraPitch {
                    restoreDefaultDriveFollowCamera(from: location)
                } else {
                    let region = MKCoordinateRegion(
                        center: location.coordinate,
                        span: Self.currentUserTrackingSpan
                    )
                    setCameraRegion(region)
                }
            }
        } label: {
            Image(systemName: "scope")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(Color.black.opacity(0.86))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(
                        isLocationTrackingAccent ? Color.purple.opacity(0.85) : Color.white.opacity(0.12),
                        lineWidth: isLocationTrackingAccent ? 2 : 1
                    )
                )
        }
        .buttonStyle(.plain)
    }

    private var searchButton: some View {
        Button {
            isShowingFriendSearch = true
        } label: {
            Image(systemName: "person.2.fill")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(Color.black.opacity(0.86))
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                .overlay(alignment: .topTrailing) {
                    if friendsSharingLocationCount > 0 {
                        Text(friendsSharingLocationCount > 99 ? "99+" : "\(friendsSharingLocationCount)")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color(red: 0.26, green: 0.63, blue: 0.28)))
                            .offset(x: 6, y: -6)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(findPeopleSharingAccessibilityLabel)
    }

    private var driveLineButton: some View {
        let sessionActive = appState.hasActiveDriveSession
        return Button {
            if sessionActive {
                syncSharingDraftsFromSession()
                isShowingDriveControls = true
            } else {
                isShowingStartDriveSheet = true
            }
        } label: {
            Image(systemName: "steeringwheel")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(
                    Circle()
                        .fill(Color.black.opacity(0.86))
                        .overlay(
                            Circle()
                                .stroke(
                                    sessionActive
                                        ? DriveSessionPalette.sessionPurple.opacity(0.85)
                                        : DriveSessionPalette.sessionPurple.opacity(0.45),
                                    lineWidth: sessionActive ? 2.5 : 1.5
                                )
                        )
                        .shadow(color: DriveSessionPalette.sessionPurple.opacity(sessionActive ? 0.45 : 0.25), radius: sessionActive ? 14 : 8)
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(sessionActive ? "Drive controls" : "Start drive")
    }

    private var driveDockBottomInsetFallback: CGFloat {
        guard let mode = driveLaunchDockMode else { return 132 }
        if isDriveLaunchDockSessionActive(mode) {
            return 120
        }
        return 132
    }

    private var effectiveDriveDockBottomInset: CGFloat {
        guard isDriveLaunchDockVisible,
              !isBuildingDriveLine,
              !shouldSuspendMapboxRendering,
              !isDriveDockShareExpanded else {
            return 0
        }
        if driveDockLayoutHeight > 0 {
            return driveDockLayoutHeight
        }
        return driveDockBottomInsetFallback
    }

    /// Gap between map FAB stack and bottom of map content (above drive dock inset).
    private var mapFabOverlayBottomPadding: CGFloat {
        18
    }

    private var driveDockExpandedMaxHeight: CGFloat {
        let viewportHeight = mapViewportLayoutSize.height
        if viewportHeight > 0 { return viewportHeight }
        return UIScreen.main.bounds.height
    }

    private var driveDockPanelMaxHeight: CGFloat? {
        guard let mode = driveLaunchDockMode, !isDriveLaunchDockSessionActive(mode) else { return nil }
        switch mode {
        case .quick, .route:
            return driveDockExpandedMaxHeight
        case .live:
            return nil
        }
    }

    private var isDriveLaunchDockVisible: Bool {
        driveLaunchDockMode != nil
    }

    private var isDriveDockShareExpanded: Bool {
        guard !isBuildingDriveLine, !shouldSuspendMapboxRendering else { return false }
        guard isDriveLaunchDockVisible, quickRouteShareLocationDraft else { return false }
        guard let mode = driveLaunchDockMode, !isDriveLaunchDockSessionActive(mode) else { return false }
        switch mode {
        case .quick, .route:
            return true
        case .live:
            return false
        }
    }

    private func isDriveLaunchDockSessionActive(_ mode: DriveLaunchDockMode) -> Bool {
        switch mode {
        case .route(let route):
            return isRouteDriveSessionVisible(for: route)
        case .quick:
            return isQuickDriveSessionActive
        case .live:
            return isLiveDriveSessionActive
        }
    }

    private var driveLaunchDockMode: DriveLaunchDockMode? {
        if let selectedRoute {
            return .route(selectedRoute)
        }
        if isQuickDriveDockVisible {
            return .quick
        }
        if appState.isSharingEnabled {
            return .live
        }
        return nil
    }

    private var driveDockSortedCircles: [DriveCircle] {
        appState.circlesSortedByRecentAccess(appState.circles)
    }

    @ViewBuilder
    private func driveLaunchDockOverlay(expandedMaxHeight: CGFloat?) -> some View {
        if let mode = driveLaunchDockMode {
            switch mode {
            case .route(let route):
                DriveLaunchDock(
                    mode: .route(route),
                    isSessionActive: isRouteDriveSessionVisible(for: route),
                    recordDrive: $quickRouteRecordDriveDraft,
                    shareLocation: $quickRouteShareLocationDraft,
                    shareCircleIDs: $quickRouteShareCircleIDsDraft,
                    circles: driveDockSortedCircles,
                    showStartDistanceWarning: showRouteStartDistanceWarning,
                    routeMetadata: routeDetailMetadata(for: route),
                    isOwnedRoute: isOwnedRoute(route),
                    statusText: routeDriveSessionStatusText(for: route),
                    canManageRoute: isOwnedRoute(route) && appState.hasRoutesAccess,
                    onStartDrive: { handleStartDrive(for: route) },
                    onStopDrive: { isShowingStopDriveConfirmation = true },
                    onCancel: { cancelSelectedRouteFromMap() },
                    onManageRoute: {
                        presentRouteBuilderIfAllowed(editing: route)
                    },
                    expandedMaxHeight: expandedMaxHeight,
                    optionsMenu: { selectedRouteOptionsMenu(route) }
                )
            case .quick:
                DriveLaunchDock(
                    mode: .quick,
                    isSessionActive: isQuickDriveSessionActive,
                    recordDrive: $quickRouteRecordDriveDraft,
                    shareLocation: $quickRouteShareLocationDraft,
                    shareCircleIDs: $quickRouteShareCircleIDsDraft,
                    circles: driveDockSortedCircles,
                    statusText: quickDriveSessionStatusText,
                    onStartDrive: { handleQuickDriveStart() },
                    onStopDrive: { isShowingStopDriveConfirmation = true },
                    onCancel: { cancelQuickDriveDock() },
                    expandedMaxHeight: expandedMaxHeight,
                    optionsMenu: { EmptyView() }
                )
            case .live:
                DriveLaunchDock(
                    mode: .live,
                    isSessionActive: isLiveDriveSessionActive,
                    recordDrive: .constant(false),
                    statusText: liveDriveSessionStatusText,
                    onStartDrive: { handleLiveDriveStart() },
                    onStopDrive: { isShowingStopDriveConfirmation = true },
                    onCancel: {},
                    expandedMaxHeight: expandedMaxHeight,
                    optionsMenu: { EmptyView() }
                )
            }
        }
    }

    private var isQuickDriveSessionActive: Bool {
        guard isQuickDriveDockVisible else { return false }
        guard let session = appState.activeDriveSession else { return false }
        if case .quick = session.kind {
            return true
        }
        return false
    }

    private var quickDriveSessionStatusText: String {
        guard isQuickDriveSessionActive, let session = appState.activeDriveSession else {
            return "Ready when you are"
        }
        let time = appState.formatDriveSessionDuration(from: session.startedAt, now: sharingNow)
        let distance = appState.formatDriveSessionDistance(session.metrics.distanceMeters)
        let speed = Int(session.metrics.maxSpeedMph.rounded())
        let speedText = speed > 0 ? "\(speed) mph" : "— mph"
        return "Recording • \(time) • \(distance) • \(speedText)"
    }

    private func handleQuickDriveStart() {
        guard !appState.hasActiveDriveSession else {
            appState.activeToast = AppToast(text: "End your current drive first", systemImage: "exclamationmark.triangle.fill")
            return
        }
        if quickRouteShareLocationDraft {
            guard validateQuickRouteShareSelection() else { return }
        }
        presentDriveSafetyDisclaimer(for: .quickDrive)
    }

    private func performQuickDriveStart(shareLive: Bool) {
        guard appState.startQuickDrive(
            saveToProfile: quickRouteRecordDriveDraft,
            shareLive: shareLive,
            sharingCircleIDs: shareLive ? quickRouteShareCircleIDsDraft : []
        ) else { return }
        appState.recordDriveOnStartEnabled = quickRouteRecordDriveDraft
        TabSoundPlayer.shared.playStartDrive()
        playStartDriveHaptic()
        isQuickDriveDockVisible = true
        cameraFollowMode = .followSelf
        syncDriveCameraPitchState()
        appState.requestLocationSessionSync()
    }

    @discardableResult
    private func validateQuickRouteShareSelection() -> Bool {
        guard !appState.circles.isEmpty else {
            appState.activeToast = AppToast(
                text: "Create or join a squad to start sharing.",
                systemImage: "person.3.fill"
            )
            return false
        }
        guard !quickRouteShareCircleIDsDraft.isEmpty else {
            isShowingSharingSquadRequiredAlert = true
            return false
        }
        return true
    }

    @discardableResult
    private func validateQuickRouteShareSelectionIfNeeded() -> Bool {
        guard quickRouteShareLocationDraft else { return true }
        return validateQuickRouteShareSelection()
    }

    private func resetQuickRouteShareDrafts() {
        quickRouteShareLocationDraft = false
        quickRouteShareCircleIDsDraft = []
    }

    private func cancelQuickDriveDock() {
        isQuickDriveDockVisible = false
        resetQuickRouteShareDrafts()
    }

    private var isLiveDriveSessionActive: Bool {
        appState.isSharingEnabled
    }

    private var liveDriveSessionStatusText: String {
        guard isLiveDriveSessionActive else {
            return "Ready when you are"
        }
        let time = appState.formatDriveSessionDuration(
            from: appState.activeDriveSession?.startedAt ?? appState.sharingSessionStartedAt ?? sharingNow,
            now: sharingNow
        )
        let distance = appState.formatDriveSessionDistance(
            appState.activeDriveSession?.metrics.distanceMeters ?? appState.activeDriveDistanceMeters
        )
        let speedMph = Int(
            (appState.activeDriveSession?.metrics.maxSpeedMph ?? appState.activeDriveMaxSpeedMph).rounded()
        )
        let speedText = speedMph > 0 ? "\(speedMph) mph" : "— mph"
        return "Sharing live • \(time) • \(distance) • \(speedText)"
    }

    private func handleLiveDriveStart() {
        guard !appState.circles.isEmpty else {
            appState.activeToast = AppToast(
                text: "Create or join a squad to start sharing.",
                systemImage: "person.3.fill"
            )
            return
        }
        pendingGoLiveAfterStartSheet = true
        syncSharingDraftsFromSession()
        sharingDraftSaveDrive = false
        isShowingCirclePicker = true
    }

    /// Clears any route or quick-drive bottom dock before switching to another drive mode.
    private func dismissDriveLaunchDock() {
        showRouteStartDistanceWarning = false
        selectedRoute = nil
        isQuickDriveDockVisible = false
        resetQuickRouteShareDrafts()
    }

    private func performConfirmedStopDrive() {
        Task {
            await handleStopDriveSession()
        }
    }

    private func selectedRouteOptionsMenu(_ route: SavedRouteDTO) -> some View {
        Menu {
            if isOwnedRoute(route), appState.hasRoutesAccess {
                Button {
                    presentRouteBuilderIfAllowed(editing: route)
                } label: {
                    Label("Edit Route", systemImage: "pencil")
                }
                Button {
                    routeNameDraft = route.name
                    routeToRename = route
                } label: {
                    Label("Rename Route", systemImage: "text.cursor")
                }
                Button(role: .destructive) {
                    routeToDelete = route
                } label: {
                    Label("Delete Route", systemImage: "trash")
                }
            } else {
                Button {
                    appState.activeToast = AppToast(text: "Route details are coming soon", systemImage: "info.circle.fill")
                } label: {
                    Label("View Details", systemImage: "info.circle")
                }
                Button {
                    appState.activeToast = AppToast(text: "Saving a copy is coming soon", systemImage: "plus.square.on.square")
                } label: {
                    Label("Save Copy", systemImage: "plus.square.on.square")
                }
                Button {
                    appState.activeToast = AppToast(text: "Route reports are coming soon", systemImage: "exclamationmark.bubble.fill")
                } label: {
                    Label("Report Route", systemImage: "exclamationmark.bubble")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Circle().fill(Color.white.opacity(0.08)))
        }
        .buttonStyle(.plain)
    }

    private func selectRouteForMap(_ route: SavedRouteDTO) {
        dismissDriveLaunchDock()
        selectedRoute = route
        showRouteStartDistanceWarning = false
        isShowingRoutesMenu = false
        cameraFollowMode = .manual
        if let region = coordinateRegionToFit(coordinates: routeDisplayCoordinates(route), paddingFactor: 2.0) {
            setCameraRegion(region)
            hasAppliedInitialCamera = true
            isUsingFallbackCamera = false
        }
    }

    private func presentRouteBuilderIfAllowed(editing route: SavedRouteDTO? = nil) {
        guard appState.hasRoutesAccess else { return }
        routeForEditing = route
        captureMapViewportBeforeRouteBuilder()
        didApplyRouteBuilderSavedRouteToMap = false
        appState.prepareRouteBuilderPresentation()
        isShowingRouteBuilder = true
    }

    private func captureMapViewportBeforeRouteBuilder() {
        preRouteBuilderMapViewport = mapViewport
        preRouteBuilderMapRegion = currentUsableMapRegionForRouteBuilderRestore()
    }

    private func restoreMapViewportAfterRouteBuilderIfNeeded() {
        defer {
            preRouteBuilderMapViewport = nil
            preRouteBuilderMapRegion = nil
            didApplyRouteBuilderSavedRouteToMap = false
        }
        guard !didApplyRouteBuilderSavedRouteToMap else { return }
        if let region = preRouteBuilderMapRegion {
            pendingRouteBuilderMapRestoreRegion = region
            pendingRouteBuilderMapRestoreViewport = nil
        } else if let preRouteBuilderMapViewport {
            pendingRouteBuilderMapRestoreRegion = nil
            pendingRouteBuilderMapRestoreViewport = preRouteBuilderMapViewport
        }
        Task { @MainActor in
            applyPendingRouteBuilderMapRestoreIfNeeded()
        }
    }

    private func currentUsableMapRegionForRouteBuilderRestore() -> MKCoordinateRegion? {
        if isUsableMapCameraRegion(lastObservedMapRegion) {
            return lastObservedMapRegion
        }
        let span = latestMarkerLODRegion.span.latitudeDelta.isFinite && latestMarkerLODRegion.span.latitudeDelta > 0
            ? latestMarkerLODRegion.span
            : MKCoordinateSpan(
                latitudeDelta: max(currentLatitudeDelta, 0.000001),
                longitudeDelta: max(currentLatitudeDelta, 0.000001)
            )
        let region = MKCoordinateRegion(center: mapCenterCoordinate, span: span)
        return isUsableMapCameraRegion(region) ? region : nil
    }

    private func applyPendingRouteBuilderMapRestoreIfNeeded() {
        guard !shouldSuspendMapboxRendering else { return }
        if let region = pendingRouteBuilderMapRestoreRegion {
            pendingRouteBuilderMapRestoreRegion = nil
            pendingRouteBuilderMapRestoreViewport = nil
            applyRestoredMapRegionAfterRouteBuilder(region)
            return
        }
        guard let viewport = pendingRouteBuilderMapRestoreViewport else { return }
        pendingRouteBuilderMapRestoreViewport = nil
        mapViewport = viewport
    }

    private func applyRestoredMapRegionAfterRouteBuilder(_ region: MKCoordinateRegion) {
        guard isUsableMapCameraRegion(region) else { return }
        mapViewport = OttoMapboxCamera.viewport(for: region)
        mapCenterCoordinate = region.center
        currentLatitudeDelta = region.span.latitudeDelta
        latestMarkerLODRegion = region
        lastObservedMapRegion = region
        lastProgrammaticMapRegion = region
        applyMarkerLODSettle(from: region)
    }

    private var newRouteBuilderInitialCenter: CLLocationCoordinate2D {
        if let coordinate = locationService.latestSample?.coordinate ?? locationService.lastLocation?.coordinate,
           CLLocationCoordinate2DIsValid(coordinate) {
            return coordinate
        }
        if CLLocationCoordinate2DIsValid(mapCenterCoordinate) {
            return mapCenterCoordinate
        }
        return Self.fallbackRegion.center
    }

    private func applySavedRouteToMap(_ route: SavedRouteDTO) {
        guard selectedRoute?.id == route.id || routeForEditing?.id == route.id else { return }
        selectedRoute = route
        if let region = coordinateRegionToFit(coordinates: routeDisplayCoordinates(route), paddingFactor: 2.0) {
            didApplyRouteBuilderSavedRouteToMap = true
            setCameraRegion(region)
            hasAppliedInitialCamera = true
            isUsingFallbackCamera = false
        }
    }

    private func clearSelectedRouteIfNeeded(_ route: SavedRouteDTO) {
        guard selectedRoute?.id == route.id else { return }
        cancelSelectedRouteFromMap()
    }

    private func cancelSelectedRouteFromMap() {
        showRouteStartDistanceWarning = false
        guard selectedRoute != nil else { return }
        selectedRoute = nil
    }

    private var routeControlsCheckpointText: String? {
        guard let session = appState.activeRouteDriveSession else { return nil }
        let total = max(
            selectedRoute.map { RouteCheckpointDetector.routeCheckpointTotal(pointCount: $0.points.count) }
                ?? RouteCheckpointDetector.routeCheckpointTotal(pointCount: session.completedWaypointIndexes.count),
            1
        )
        return "\(session.completedWaypointIndexes.count)/\(total) checkpoints"
    }

    private func handleStopDriveSession() async {
        if appState.activeRouteDriveSession != nil {
            let location = locationService.latestSample ?? locationService.lastLocation
            await appState.stopRouteDriveSession(location: location)
            await MainActor.run {
                selectedRoute = nil
                syncMapRouteSessionActiveToAppState()
                syncDriveCameraPitchState()
            }
            return
        }
        let location = locationService.latestSample ?? locationService.lastLocation
        if let payload = await appState.stopDriveSession(location: location) {
            await appState.refreshRecentDrives()
            await MainActor.run {
                isQuickDriveDockVisible = false
                syncDriveCameraPitchState()
            }
            guard payload.kind == .quick else { return }
            driveCompleteSummary = buildDriveCompleteSummary(from: payload)
        }
    }

    private func buildDriveCompleteSummary(from payload: DriveSessionCompletionPayload) -> DriveCompleteSummary {
        DriveCompleteSummary(
            driveId: payload.driveId,
            routeName: payload.routeName ?? "Drive",
            routeCoordinates: payload.routeCoordinates,
            checkpointCoordinates: payload.checkpointCoordinates,
            distanceMeters: payload.distanceMeters,
            driveTimeSeconds: payload.driveTimeSeconds,
            averageSpeedMph: payload.averageSpeedMph,
            maxSpeedMph: payload.maxSpeedMph,
            completedCheckpoints: payload.completedCheckpoints,
            totalCheckpoints: payload.totalCheckpoints,
            completionReason: payload.completionReason
        )
    }

    private func presentCompletedDriveSummary(from summary: DriveCompleteSummary) {
        guard let driveId = summary.driveId?.trimmingCharacters(in: .whitespacesAndNewlines), !driveId.isEmpty else {
            appState.activeToast = AppToast(
                text: "Couldn't open drive summary.",
                systemImage: "exclamationmark.triangle.fill"
            )
            withAnimation(.easeInOut(duration: 0.2)) {
                driveCompleteSummary = nil
            }
            return
        }

        withAnimation(.easeInOut(duration: 0.2)) {
            driveCompleteSummary = nil
        }

        Task {
            do {
                let drive = try await APIClient.shared.fetchDrive(driveId: driveId)
                await MainActor.run {
                    completedDriveForSummary = drive
                }
            } catch {
                await MainActor.run {
                    appState.activeToast = AppToast(
                        text: "Couldn't open drive summary.",
                        systemImage: "exclamationmark.triangle.fill"
                    )
                }
            }
        }
    }

    private func handleStartDrive(for route: SavedRouteDTO) {
        guard validateRouteDriveStartDistance(for: route) else { return }
        if quickRouteShareLocationDraft {
            guard validateQuickRouteShareSelection() else { return }
        }
        presentDriveSafetyDisclaimer(for: .routeDrive(route))
    }

    @discardableResult
    private func validateRouteDriveStartDistance(for route: SavedRouteDTO) -> Bool {
        let currentLocation = locationService.latestSample ?? locationService.lastLocation
        guard appState.isWithinRouteStartDriveRange(route, currentLocation: currentLocation) else {
            showRouteStartDistanceWarning = true
            return false
        }
        showRouteStartDistanceWarning = false
        return true
    }

    private func performRouteDriveStart(for route: SavedRouteDTO, shareLive: Bool) {
        let currentLocation = locationService.latestSample ?? locationService.lastLocation
        guard appState.isWithinRouteStartDriveRange(route, currentLocation: currentLocation) else {
            showRouteStartDistanceWarning = true
            return
        }
        showRouteStartDistanceWarning = false
        TabSoundPlayer.shared.playStartDrive()
        routeDriveSessionTask?.cancel()
        routeDriveSessionTask = Task {
            do {
                let session = try await APIClient.shared.startRouteDriveSession(routeId: route.id)
                await MainActor.run {
                    if shareLive {
                        guard appState.startSharingForDriveStart(circleIDs: quickRouteShareCircleIDsDraft) else {
                            return
                        }
                    }
                    var state = RouteDriveSessionState(
                        dto: session,
                        routeId: route.id,
                        currentLocation: currentLocation
                    )
                    appState.applyStartCheckpointIfNeeded(to: &state, route: route, location: currentLocation)
                    appState.beginRouteDriveSession(
                        route: route,
                        shareLive: shareLive,
                        routeSession: state,
                        recordToProfile: quickRouteRecordDriveDraft
                    )
                    appState.recordDriveOnStartEnabled = quickRouteRecordDriveDraft
                    syncMapRouteSessionActiveToAppState()
                    playStartDriveHaptic()
                    appState.activeToast = AppToast(text: "Ready to drive", systemImage: "flag.fill")
                    applyMarkerLODSettle(
                        from: MKCoordinateRegion(
                            center: mapCenterCoordinate,
                            span: OttoMapboxCamera.driveTrackingSpan
                        ),
                        useDriveRouteLOD: true
                    )
                    cameraFollowMode = .followSelf
                    syncDriveCameraPitchState(from: currentLocation)
                }
            } catch {
                await MainActor.run {
                    appState.activeToast = AppToast(text: "Couldn’t start route drive", systemImage: "exclamationmark.triangle.fill")
                }
            }
        }
    }

    private func handleRouteDriveFeedbackEvent(_ event: RouteDriveFeedbackEvent?) {
        guard let event else { return }
        switch event.kind {
        case .activated:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            appState.activeToast = AppToast(text: "Drive started", systemImage: "location.north.fill")
            cameraFollowMode = .followSelf
            syncDriveCameraPitchState(from: locationService.latestSample ?? locationService.lastLocation)
        case .checkpointReached(let isFinish):
            if !isFinish {
                TabSoundPlayer.shared.playCheckpointComplete()
            }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            appState.activeToast = AppToast(
                text: isFinish ? "Finish reached" : "Checkpoint reached",
                systemImage: isFinish ? "flag.checkered" : "checkmark.circle.fill"
            )
        case .completed(let summary):
            TabSoundPlayer.shared.playRouteFinished()
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            selectedRoute = nil
            syncMapRouteSessionActiveToAppState()
            syncDriveCameraPitchState()
            driveCompleteSummary = summary
            appState.activeToast = AppToast(text: "Drive complete", systemImage: "steeringwheel")
        case .stopped(let summary):
            TabSoundPlayer.shared.playRouteFinished()
            selectedRoute = nil
            syncMapRouteSessionActiveToAppState()
            syncDriveCameraPitchState()
            if let summary {
                driveCompleteSummary = summary
            }
            appState.activeToast = AppToast(text: "Drive stopped", systemImage: "stop.circle.fill")
        case .activationFailed:
            appState.activeToast = AppToast(text: "Couldn’t activate drive", systemImage: "exclamationmark.triangle.fill")
        }
    }

    private func playStartDriveHaptic() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private var activeLiveDriveTrailSamples: [DrivePathSample]? {
        if let session = appState.activeRouteDriveSession, session.isActive,
           DriveSpeedGradient.hasUsableSpeedPathData(appState.routeDrivePathSamples) {
            return appState.routeDrivePathSamples
        }
        if let trail = appState.activeDriveSession?.metrics.recordedPath,
           DriveSpeedGradient.hasUsableSpeedPathData(trail) {
            return trail
        }
        if appState.isSharingEnabled,
           appState.sharingSaveDriveEnabled,
           DriveSpeedGradient.hasUsableSpeedPathData(appState.activeDrivePathTrail) {
            return appState.activeDrivePathTrail
        }
        return nil
    }

    private func speedMph(from location: CLLocation?) -> Double {
        let speedMps = max(locationService.effectiveSpeedMetersPerSecond(), location?.speed ?? 0, 0)
        return speedMps * 2.23694
    }

    private func isRouteDriveSessionVisible(for route: SavedRouteDTO) -> Bool {
        appState.activeRouteDriveSession?.activeRouteId == route.id
    }

    private func routeDriveSessionStatusText(for route: SavedRouteDTO) -> String {
        guard let session = appState.activeRouteDriveSession, session.activeRouteId == route.id else {
            return "Route visible on your map"
        }
        if session.isArmed {
            return "Ready to drive • waiting for movement"
        }
        let completed = session.completedWaypointIndexes.count
        let total = max(route.points.count, 1)
        let speed = Int(session.currentSpeedMph.rounded())
        return "Driving • \(completed)/\(total) checkpoints • \(speed) mph"
    }

    private func isWithinStartDriveRange(_ route: SavedRouteDTO) -> Bool {
        appState.isWithinRouteStartDriveRange(
            route,
            currentLocation: locationService.latestSample ?? locationService.lastLocation
        )
    }

    private func routeDisplayCoordinates(_ route: SavedRouteDTO) -> [CLLocationCoordinate2D] {
        let roadCoordinates = coordinates(from: route.roadCoordinates)
        if roadCoordinates.count >= 2 { return roadCoordinates }
        return coordinates(from: route.points)
    }

    private func coordinates(from points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        points.compactMap(coordinate(from:))
    }

    private func coordinate(from point: RoutePointDTO) -> CLLocationCoordinate2D? {
        let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
        guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { return nil }
        return coordinate
    }

    private func selectedRoutePointColor(_ markerType: String?) -> Color {
        switch markerType {
        case "start": return .green
        case "waypoint": return .blue
        case "stop": return .red
        case "finish": return .white
        default: return .purple
        }
    }

    private func selectedRoutePointSystemImage(_ markerType: String?) -> String {
        switch markerType {
        case "start": return "play.fill"
        case "waypoint": return "flag.fill"
        case "stop": return "octagon.fill"
        case "finish": return "flag.checkered"
        default: return "circle.fill"
        }
    }

    private func isOwnedRoute(_ route: SavedRouteDTO) -> Bool {
        route.createdByUserId == appState.currentUserID
    }

    private func routeDetailMetadata(for route: SavedRouteDTO) -> String {
        var parts: [String] = []
        if route.distanceMeters > 0 {
            parts.append(Self.routeDistanceFormatter.string(from: Measurement(value: route.distanceMeters, unit: UnitLength.meters)))
        }
        parts.append("\(route.points.count) points")
        if route.etaSeconds > 0 {
            parts.append("\(max(1, Int((route.etaSeconds / 60).rounded()))) min")
        }
        return parts.joined(separator: " • ")
    }

    private func renameSelectedRoute(_ route: SavedRouteDTO, to draftName: String) async {
        let trimmedName = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }
        do {
            let updated = try await APIClient.shared.updateRoute(
                routeId: route.id,
                name: trimmedName,
                points: route.points,
                roadCoordinates: route.roadCoordinates,
                distanceMeters: route.distanceMeters,
                etaSeconds: route.etaSeconds
            )
            if selectedRoute?.id == route.id {
                selectedRoute = updated
            }
            routeToRename = nil
            appState.activeToast = AppToast(text: "Route renamed", systemImage: "checkmark.circle.fill")
        } catch {
            appState.activeToast = AppToast(text: "Couldn’t rename route", systemImage: "exclamationmark.triangle.fill")
        }
    }

    private func deleteSelectedRoute(_ route: SavedRouteDTO) async {
        do {
            try await APIClient.shared.deleteRoute(routeId: route.id)
            clearSelectedRouteIfNeeded(route)
            routeToDelete = nil
            appState.presentDeletedToast(for: "Route")
        } catch {
            appState.presentDeleteFailedToast(for: "route")
        }
    }

    private var layersButton: some View {
        Button {
            isShowingLayers = true
        } label: {
            Image(systemName: "square.3.layers.3d")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(Color.black.opacity(0.86))
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var layersSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    OttoMapSheetHeader(title: "Layers", onDone: { isShowingLayers = false })

                    layersSheetSection(title: "Map") {
                        OttoToggleSettingCard(
                            title: "Show traffic",
                            isOn: $showTrafficLayer
                        )
                    }

                    layersSheetSection(title: "Events") {
                        OttoToggleSettingCard(
                            title: "Show upcoming events",
                            isOn: $showEventsLayer
                        )
                        OttoToggleSettingCard(
                            title: "Show race tracks",
                            isOn: $showRaceTracksLayer
                        )
                    }

                    layersSheetSection(title: "My places") {
                        OttoToggleSettingCard(
                            title: "Show saved places",
                            isOn: $showMyPlacesLayer
                        )
                    }

                    layersSheetSection(title: "Squads To Display") {
                        LazyVStack(spacing: 12) {
                            ForEach(appState.circles.sortedForMapSquadList()) { circle in
                                SquadToggleSettingCard(
                                    circle: circle,
                                    isOn: Binding(
                                        get: { visibleCircleLayerIDs.contains(circle.id) },
                                        set: { isOn in
                                            if isOn { visibleCircleLayerIDs.insert(circle.id) }
                                            else { visibleCircleLayerIDs.remove(circle.id) }
                                        }
                                    ),
                                    subtitle: circle.mapSharingStatusSubtitle
                                )
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color.black)
        }
        .presentationDetents([.medium, .large])
    }

    private func layersSheetSection<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(.white.opacity(0.56))
            content()
        }
    }

    private var driveLineLibrarySheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                OttoMapSheetHeader(title: "Drive Lines", onDone: { isShowingDriveLineLibrary = false })
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                List {
                Section {
                    Button {
                        isShowingDriveLineLibrary = false
                        beginDriveLineWizard()
                    } label: {
                        Label("Create New Drive Line", systemImage: "plus")
                            .font(.headline)
                    }
                    .listRowBackground(Color.white.opacity(0.04))
                }

                Section("Saved Drive Lines") {
                    if savedDriveLines.isEmpty {
                        Text("No saved drive lines yet.")
                            .foregroundStyle(.secondary)
                            .listRowBackground(Color.white.opacity(0.04))
                    } else {
                        ForEach(savedDriveLines, id: \.id) { line in
                            Button {
                                activeDriveLine = line
                                isShowingDriveLineLibrary = false
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(line.name)
                                            .foregroundStyle(.white)
                                        Text("\(line.points.count) points")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .listRowBackground(Color.white.opacity(0.04))
                        }
                    }
                }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.black)
        }
        .presentationDetents([.medium, .large])
    }

    private var driveLineWizardCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            if driveLineDraftPoints.count >= 2 {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "point.3.connected.trianglepath.dotted")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(driveLineDraftColorKey.primary)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(driveLineDraftPoints.count) points added")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        HStack(spacing: 8) {
                            Text("\(driveLineDraftDistanceMiles.formatted(.number.precision(.fractionLength(1)))) mi")
                            Text("·")
                            Text("\(driveLineDraftEtaMinutes) min")
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.82))
                    }

                    Spacer()

                    Button("Save Drive") {
                        driveLineNameDraft = activeDriveLine?.name ?? "Drive Line"
                        isShowingDriveNamePrompt = true
                    }
                    .frame(minWidth: 108)
                    .primaryCTAButtonStyle(horizontalPadding: 16, verticalPadding: 10)
                    .disabled(isBuildingRoadPath || driveLineDraftRoadCoordinates.count < 2)
                    .opacity((isBuildingRoadPath || driveLineDraftRoadCoordinates.count < 2) ? 0.6 : 1)
                }

                driveLineColorPickerRow
            } else {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "point.3.connected.trianglepath.dotted")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(driveLineDraftColorKey.primary)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(driveLineWizardStep == .selectStart ? "Create a Drive Line" : "Add Your Route Points")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Text(driveLineWizardInstruction)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.white.opacity(0.82))
                    }

                    Spacer()

                    Button("Cancel") {
                        cancelDriveLineWizard()
                    }
                    .frame(minWidth: 108)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.45))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.22), lineWidth: 0.8)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }

                if isBuildingRoadPath && driveLineWizardStep != .selectStart {
                    HStack(spacing: 8) {
                        ProgressView().scaleEffect(0.75)
                        Text("Snapping to roads...")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                driveLineColorPickerRow
            }
        }
        .frame(minHeight: driveLineDraftPoints.count >= 2 ? 0 : 150, alignment: .top)
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 10)
        .background(Color.black.opacity(0.96))
        .frame(maxWidth: .infinity)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18))
        .overlay(
            UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }

    private var driveLineColorPickerRow: some View {
        HStack(spacing: 10) {
            Text("Color")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
            ForEach(DriveLineColorKey.allCases, id: \.rawValue) { key in
                Button {
                    driveLineDraftColorKey = key
                } label: {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [key.secondary, key.primary],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 26, height: 26)
                        .overlay(
                            Circle()
                                .stroke(
                                    key == driveLineDraftColorKey ? Color.white.opacity(0.95) : Color.clear,
                                    lineWidth: 2.5
                                )
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 6)
        .padding(.bottom, 10)
    }

    private func driveLineMarkerLabel(
        index: Int,
        total: Int,
        markerType: DriveLineMarkerType
    ) -> String? {
        if index == 0 { return "Start" }
        if index == total - 1 { return "Finish" }
        if markerType == .waypoint { return nil }
        return markerType.label
    }

    @ViewBuilder
    private func driveLineMarkerBadge(
        index: Int,
        total: Int,
        defaultColor: Color
    ) -> some View {
        if index == 0 {
            Image(systemName: "flag.fill")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(defaultColor)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.95), lineWidth: 1.5))
        } else if index == total - 1 {
            Image(systemName: "flag.checkered")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.black)
                .frame(width: 28, height: 28)
                .background(Color.white)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.black.opacity(0.14), lineWidth: 1.2))
        } else {
            Text("\(index + 1)")
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 20, height: 20)
                .background(defaultColor)
                .clipShape(Circle())
        }
    }

    private var driveLineWizardInstruction: String {
        switch driveLineWizardStep {
        case .selectStart:
            return "Tap anywhere on the map to set your starting point."
        case .addWaypoints:
            return "Tap anywhere to add your next point."
        case .selectFinish:
            return "Tap map to place your FINISH point."
        }
    }

    private var driveLineEditButtons: some View {
        VStack(spacing: 10) {
            Button {
                isSelectingFinishPoint = false
                _ = driveLineDraftPoints.popLast()
                Task { await rebuildDraftRoadPath() }
            } label: {
                Image(systemName: "arrow.uturn.backward")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.black.opacity(0.88))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(isBuildingRoadPath)
            .opacity(isBuildingRoadPath ? 0.5 : 1)

            Button {
                isSelectingFinishPoint = false
                driveLineDraftPoints.removeAll()
                driveLineDraftRoadCoordinates.removeAll()
                driveLineDraftDistanceMeters = 0
                driveLineDraftTravelSeconds = 0
            } label: {
                Image(systemName: "xmark")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.black.opacity(0.88))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(isBuildingRoadPath)
            .opacity(isBuildingRoadPath ? 0.5 : 1)
        }
    }

    private var driveLineWizardStep: DriveLineWizardStep {
        if driveLineDraftPoints.isEmpty { return .selectStart }
        if isSelectingFinishPoint { return .selectFinish }
        return .addWaypoints
    }

    private var driveLineDraftDistanceMiles: Double {
        driveLineDraftDistanceMeters / 1609.344
    }

    private var driveLineDraftEtaMinutes: Int {
        max(1, Int((driveLineDraftTravelSeconds / 60).rounded()))
    }

    private func beginDriveLineWizard() {
        if let existing = activeDriveLine?.colorKey {
            driveLineDraftColorKey = existing
        } else {
            let all = DriveLineColorKey.allCases
            driveLineDraftColorKey = all[Int.random(in: 0..<all.count)]
        }
        driveLineDraftPoints = []
        driveLineDraftRoadCoordinates = []
        driveLineDraftDistanceMeters = 0
        driveLineDraftTravelSeconds = 0
        isSelectingFinishPoint = false
        isBuildingDriveLine = true
    }

    private func cancelDriveLineWizard() {
        isBuildingDriveLine = false
        isSelectingFinishPoint = false
        driveLineDraftPoints = []
        driveLineDraftRoadCoordinates = []
        driveLineDraftDistanceMeters = 0
        driveLineDraftTravelSeconds = 0
    }

    private func handleDriveLineTap(at coordinate: CLLocationCoordinate2D) {
        driveLineDraftPoints.append(
            DriveLinePoint(latitude: coordinate.latitude, longitude: coordinate.longitude, markerType: .waypoint)
        )
        Task {
            await rebuildDraftRoadPath()
        }
    }

    private func moveDriveLinePoint(at index: Int, to coordinate: CLLocationCoordinate2D) {
        guard driveLineDraftPoints.indices.contains(index) else { return }
        let markerType = driveLineDraftPoints[index].markerType
        driveLineDraftPoints[index] = DriveLinePoint(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            markerType: markerType
        )
        Task { await rebuildDraftRoadPath() }
    }

    private func insertDriveLinePoint(at index: Int, coordinate: CLLocationCoordinate2D) {
        let clampedIndex = max(1, min(index, driveLineDraftPoints.count))
        driveLineDraftPoints.insert(
            DriveLinePoint(latitude: coordinate.latitude, longitude: coordinate.longitude, markerType: .waypoint),
            at: clampedIndex
        )
        Task { await rebuildDraftRoadPath() }
    }

    private func finalizeDriveLineWizard(named draftName: String) {
        guard driveLineDraftPoints.count >= 2, driveLineDraftRoadCoordinates.count >= 2 else { return }
        let trimmedName = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        activeDriveLine = DriveLine(
            id: activeDriveLine?.id,
            name: trimmedName.isEmpty ? "Drive Line" : trimmedName,
            colorKey: driveLineDraftColorKey,
            points: driveLineDraftPoints,
            roadCoordinates: driveLineDraftRoadCoordinates
        )
        isBuildingDriveLine = false
        isSelectingFinishPoint = false
        Task { await persistActiveDriveLine() }
    }

    private func persistActiveDriveLine() async {
        guard let circleID = appState.selectedCircle?.id else { return }
        guard let line = activeDriveLine else { return }
        do {
            let saved: DriveLineDTO
            if let existingId = line.id {
                saved = try await APIClient.shared.updateDriveLine(
                    driveLineId: existingId,
                    name: line.name,
                    colorKey: line.colorKey.rawValue,
                    points: line.points.map { .init(lat: $0.latitude, lng: $0.longitude, markerType: $0.markerType.rawValue) },
                    roadCoordinates: line.roadCoordinates.map { .init(lat: $0.latitude, lng: $0.longitude, markerType: nil) },
                    distanceMeters: driveLineDraftDistanceMeters,
                    etaSeconds: driveLineDraftTravelSeconds
                )
            } else {
                saved = try await APIClient.shared.createDriveLine(
                    circleId: circleID,
                    name: line.name,
                    colorKey: line.colorKey.rawValue,
                    points: line.points.map { .init(lat: $0.latitude, lng: $0.longitude, markerType: $0.markerType.rawValue) },
                    roadCoordinates: line.roadCoordinates.map { .init(lat: $0.latitude, lng: $0.longitude, markerType: nil) },
                    distanceMeters: driveLineDraftDistanceMeters,
                    etaSeconds: driveLineDraftTravelSeconds
                )
            }
            activeDriveLine = DriveLine(
                id: saved.id,
                name: saved.name,
                colorKey: DriveLineColorKey(rawValue: saved.colorKey ?? "violet") ?? .violet,
                points: saved.points.map {
                    .init(
                        latitude: $0.lat,
                        longitude: $0.lng,
                        markerType: DriveLineMarkerType(rawValue: $0.markerType ?? "waypoint") ?? .waypoint
                    )
                },
                roadCoordinates: saved.roadCoordinates.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
            )
        } catch {
            // Keep local route even if network save fails.
            appState.activeToast = AppToast(
                text: "Drive line saved locally. Sync retrying in background.",
                systemImage: "exclamationmark.triangle.fill"
            )
        }
    }

    /// Squad drive-line overlays are deprecated (product refactor). Do not fetch `/api/drive-lines` for the map.
    private func loadDriveLinesForSelectedCircle() async {
        guard let circleID = appState.selectedCircle?.id, !circleID.isEmpty else { return }
        await MainActor.run {
            activeDriveLine = nil
            savedDriveLines = []
            visibleDriveLineIDs = []
            showDrivesLayer = false
            applyDriveLayerPreference(false, animated: false)
            persistLayerPreferences()
        }
    }

    private func cycleSavedMarkerType(at index: Int) {
        guard var line = activeDriveLine else { return }
        guard line.points.indices.contains(index) else { return }
        var updatedPoints = line.points
        let point = updatedPoints[index]
        updatedPoints[index] = DriveLinePoint(
            latitude: point.latitude,
            longitude: point.longitude,
            markerType: point.markerType.next
        )
        line = DriveLine(
            id: line.id,
            name: line.name,
            colorKey: line.colorKey,
            points: updatedPoints,
            roadCoordinates: line.roadCoordinates
        )
        activeDriveLine = line
        Task { await persistActiveDriveLine() }
    }

    private func undoLastDriveLinePoint() {
        isSelectingFinishPoint = false
        _ = driveLineDraftPoints.popLast()
        Task { await rebuildDraftRoadPath() }
    }

    private func clearAllDriveLinePoints() {
        isSelectingFinishPoint = false
        driveLineDraftPoints.removeAll()
        driveLineDraftRoadCoordinates.removeAll()
        driveLineDraftDistanceMeters = 0
        driveLineDraftTravelSeconds = 0
    }

    private func nearestDraftPointIndex(
        to coordinate: CLLocationCoordinate2D,
        maxDistanceMeters: CLLocationDistance
    ) -> Int? {
        guard !driveLineDraftPoints.isEmpty else { return nil }
        let target = MKMapPoint(coordinate)
        var bestIndex: Int?
        var bestDistance = CLLocationDistance.greatestFiniteMagnitude

        for (index, point) in driveLineDraftPoints.enumerated() {
            let candidate = MKMapPoint(point.coordinate)
            let distance = target.distance(to: candidate)
            if distance < bestDistance {
                bestDistance = distance
                bestIndex = index
            }
        }

        guard let bestIndex, bestDistance <= maxDistanceMeters else { return nil }
        return bestIndex
    }

    private func nearestDraftSegmentInsertionIndex(
        to coordinate: CLLocationCoordinate2D,
        maxDistanceMeters: CLLocationDistance
    ) -> Int? {
        guard driveLineDraftPoints.count >= 2 else { return nil }
        let target = MKMapPoint(coordinate)
        var bestInsertionIndex: Int?
        var bestDistance = CLLocationDistance.greatestFiniteMagnitude

        for idx in 0..<(driveLineDraftPoints.count - 1) {
            let a = MKMapPoint(driveLineDraftPoints[idx].coordinate)
            let b = MKMapPoint(driveLineDraftPoints[idx + 1].coordinate)
            let distance = distanceFromPoint(target, toSegmentFrom: a, to: b)
            if distance < bestDistance {
                bestDistance = distance
                bestInsertionIndex = idx + 1
            }
        }

        guard let bestInsertionIndex, bestDistance <= maxDistanceMeters else { return nil }
        return bestInsertionIndex
    }

    private func distanceFromPoint(
        _ p: MKMapPoint,
        toSegmentFrom a: MKMapPoint,
        to b: MKMapPoint
    ) -> CLLocationDistance {
        let abx = b.x - a.x
        let aby = b.y - a.y
        let apx = p.x - a.x
        let apy = p.y - a.y
        let ab2 = abx * abx + aby * aby
        if ab2 == 0 {
            return p.distance(to: a)
        }
        let t = max(0, min(1, (apx * abx + apy * aby) / ab2))
        let closest = MKMapPoint(x: a.x + abx * t, y: a.y + aby * t)
        return p.distance(to: closest)
    }

    private func rebuildDraftRoadPath() async {
        guard driveLineDraftPoints.count >= 2 else {
            driveLineDraftRoadCoordinates = driveLineDraftPoints.map(\.coordinate)
            driveLineDraftDistanceMeters = 0
            driveLineDraftTravelSeconds = 0
            return
        }
        isBuildingRoadPath = true
        defer { isBuildingRoadPath = false }

        let result = await RouteRoadSnapper.buildRoute(for: driveLineDraftPoints.map(\.coordinate))
        driveLineDraftRoadCoordinates = result.coordinates
        driveLineDraftDistanceMeters = result.distanceMeters
        driveLineDraftTravelSeconds = result.travelTimeSeconds
    }

    private var circlePickerSheet: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    OttoMapSheetHeader(title: "Sharing", onDone: { isShowingCirclePicker = false })
                    sharingDurationSection
                    sharingModeSection
                    DriveCarPickerRow()
                    sharingDriveHistorySection
                    sharingSquadsSection
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Rectangle()
                        .fill(Color.white.opacity(0.12))
                        .frame(height: 1)
                    sharingCTASection
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                        .padding(.bottom, 10)
                        .frame(maxWidth: .infinity)
                        .background(Color.black)
                }
                .background(Color.black.ignoresSafeArea(edges: .bottom))
            }
            .background(Color.black)
        }
        .presentationDetents([.large])
    }

    private var sharingDurationSection: some View {
        sharingSection(title: "Duration") {
            HStack(spacing: 14) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.purple)
                    .frame(width: 34, height: 34)

                Menu {
                    ForEach(sharingDurationOptions, id: \.self) { option in
                        Button(option.title) {
                            sharingDraftDurationPreset = option
                            sharingDraftDurationSeconds = option.seconds()
                        }
                    }
                } label: {
                    HStack {
                        Text(sharingDraftDurationTitle)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Spacer()
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.heavy))
                            .foregroundStyle(.white.opacity(0.7))
                    }
                }

                if appState.isSharingEnabled {
                    Button("Reset timer") {
                        appState.extendSharingSession(durationSeconds: sharingDraftDurationPreset.seconds())
                        sharingNow = Date()
                    }
                    .font(.caption.weight(.heavy))
                    .foregroundStyle(.purple)
                }
            }
            .padding(14)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            Text(appState.isSharingEnabled ? (sharingRemainingText() ?? "Ending soon") : "Sharing will automatically stop after this time")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.56))
        }
    }

    private var sharingModeSection: some View {
        sharingSection(title: "When to share") {
            HStack(spacing: 10) {
                sharingModeButton(
                    title: "Share now",
                    subtitle: "Share continuously",
                    systemImage: "car.fill",
                    mode: .shareNow
                )
                sharingModeButton(
                    title: "While driving",
                    subtitle: "Share while driving",
                    systemImage: "steeringwheel",
                    mode: .drivingOnly
                )
            }
        }
    }

    private var sharingDriveHistorySection: some View {
        sharingSection(title: "Drive history") {
            sharingSaveDriveCard
        }
    }

    private var sharingSaveDriveCard: some View {
        OttoToggleSettingCard(
            title: String(localized: "drive_record_toggle_title"),
            isOn: $sharingDraftSaveDrive,
            systemImage: "point.topleft.down.to.point.bottomright.curvepath.fill",
            helperText: String(localized: "drive_record_toggle_helper"),
            onChange: { enabled in
                appState.setSharingSaveDriveEnabled(enabled)
            }
        )
    }

    private var sharingSquadsSection: some View {
        sharingSection(title: "Share with") {
            let active = appState.isSharingSessionActive && !appState.sharingTiedToActiveDrive
            SharingSquadPickerSection(
                circles: driveDockSortedCircles,
                selectedCircleIDs: active
                    ? Binding(
                        get: { appState.sharingCircleIDs },
                        set: { _ in }
                    )
                    : $sharingDraftCircleIDs,
                isActiveSession: active,
                onRemoveFromActiveSession: active
                    ? { circleID in
                        appState.removeSquadFromSharingSession(circleID)
                        sharingNow = Date()
                    }
                    : nil
            )
        }
    }

    private var sharingCTASection: some View {
        VStack(spacing: 12) {
            if !appState.isSharingSessionActive || appState.sharingTiedToActiveDrive {
                Button {
                    guard !sharingDraftCircleIDs.isEmpty else {
                        isShowingSharingSquadRequiredAlert = true
                        return
                    }
                    isShowingCirclePicker = false
                    presentDriveSafetyDisclaimer(for: .goLive)
                } label: {
                    Label("Start Sharing", systemImage: "paperplane.fill")
                        .font(.headline.weight(.heavy))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(RouteMapMarkerColors.startButton)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
            } else {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "lock.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.42))
                        .padding(.top, 2)
                    Text("Location is encrypted in transit (TLS) and only shared with your selected squads.")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.48))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button {
                    appState.extendSharingSession(durationSeconds: sharingDraftDurationPreset.seconds())
                    sharingNow = Date()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.headline.weight(.bold))
                        Text("Extend \(sharingDraftDurationTitle)")
                            .font(.headline.weight(.heavy))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(sharingPrimaryGradient)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)

                Button {
                    appState.stopSharingSession()
                    sharingNow = Date()
                    isShowingCirclePicker = false
                } label: {
                    Text("Stop Sharing")
                        .font(.headline.weight(.heavy))
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .stroke(Color.white.opacity(0.12), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 2)
    }

    private func sharingSection<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(.white.opacity(0.56))
            content()
        }
    }

    private func sharingModeButton(
        title: String,
        subtitle: String,
        systemImage: String,
        mode: AppState.SharingSessionMode
    ) -> some View {
        let isSelected = sharingDraftMode == mode
        return Button {
            sharingDraftMode = mode
            if appState.isSharingEnabled {
                appState.updateSharingSessionMode(mode)
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(isSelected ? .purple : .white.opacity(0.8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.56))
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(isSelected ? Color.purple.opacity(0.18) : Color.white.opacity(0.055))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isSelected ? Color.purple.opacity(0.72) : Color.white.opacity(0.08), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var sharingDurationOptions: [SharingDurationPreset] {
        [
            .minutes(30),
            .hours(1),
            .hours(2),
            .hours(4),
            .hours(8),
            .endOfDay,
        ]
    }

    private var sharingPrimaryGradient: LinearGradient {
        LinearGradient(
            colors: [Color(red: 0.93, green: 0.09, blue: 0.94), Color(red: 0.13, green: 0.55, blue: 1.0)],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    private func sharingDurationTitle(_ seconds: TimeInterval) -> String {
        if seconds < 3600 {
            return "\(Int(seconds / 60)) min"
        }
        let hours = Int(seconds / 3600)
        return hours == 1 ? "1 hour" : "\(hours) hours"
    }

    private var sharingDraftDurationTitle: String {
        sharingDraftDurationPreset.title
    }

    private func sharingRemainingText() -> String? {
        guard let remaining = appState.sharingRemainingSeconds(now: sharingNow) else { return nil }
        let totalMinutes = max(1, Int(ceil(remaining / 60)))
        if totalMinutes < 60 {
            return "\(totalMinutes) min left"
        }
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if minutes == 0 {
            return hours == 1 ? "1 hour left" : "\(hours) hours left"
        }
        return "\(hours)h \(minutes)m left"
    }

    private func syncSharingDraftsFromSession() {
        if appState.isSharingSessionActive && !appState.sharingTiedToActiveDrive {
            sharingDraftCircleIDs = appState.sharingCircleIDs.isEmpty && !appState.selectedCircleID.isEmpty
                ? Set([appState.selectedCircleID])
                : appState.sharingCircleIDs
        } else {
            sharingDraftCircleIDs = []
        }
        sharingDraftDurationSeconds = appState.sharingDurationSeconds
        sharingDraftDurationPreset = sharingPreset(for: appState.sharingDurationSeconds)
        sharingDraftMode = appState.sharingSessionMode
        sharingDraftSaveDrive = appState.sharingSaveDriveEnabled
    }

    private func startSharingFromDrafts() {
        appState.setSharingSaveDriveEnabled(sharingDraftSaveDrive)
        if appState.startSharingSession(
            circleIDs: sharingDraftCircleIDs,
            durationSeconds: sharingDraftDurationPreset.seconds(),
            mode: sharingDraftMode
        ) {
            let startsLiveDrive = appState.activeDriveSession == nil
            appState.ensureLiveDriveSession(saveToProfile: sharingDraftSaveDrive)
            if startsLiveDrive {
                TabSoundPlayer.shared.playStartDrive()
                playStartDriveHaptic()
            }
            pendingGoLiveAfterStartSheet = false
            sharingNow = Date()
            isShowingCirclePicker = false
            isShowingDriveControls = false
            cameraFollowMode = .followSelf
            syncDriveCameraPitchState()
        }
    }

    private func completePendingShareFlowAfterPermissions() {
        switch pendingDriveStartContinuation {
        case .quickDrive:
            pendingDriveStartContinuation = nil
            performQuickDriveStart(shareLive: quickRouteShareLocationDraft)
        case .routeDrive(let route):
            pendingDriveStartContinuation = nil
            performRouteDriveStart(for: route, shareLive: quickRouteShareLocationDraft)
        case .goLive, nil:
            pendingDriveStartContinuation = nil
            startSharingFromDrafts()
        }
    }

    private func sharingPreset(for seconds: TimeInterval) -> SharingDurationPreset {
        let presets: [SharingDurationPreset] = [.minutes(30), .hours(1), .hours(2), .hours(4), .hours(8)]
        return presets.min { lhs, rhs in
            abs(lhs.seconds() - seconds) < abs(rhs.seconds() - seconds)
        } ?? .hours(1)
    }

    private func centerOnFriend(_ friend: FriendLocation, shouldFollow: Bool = false) {
        guard CLLocationCoordinate2DIsValid(friend.coordinate) else {
            OttoLog.map.warning("centerOnFriend skipped: invalid coordinate user=\(friend.id)")
            return
        }
        cameraFollowMode = shouldFollow ? .followFriend(friend.id) : .manual
        hasAppliedInitialCamera = true
        isUsingFallbackCamera = false
        let region = MKCoordinateRegion(
            center: friend.coordinate,
            span: Self.defaultTrackingSpan
        )
        setCameraRegion(region)
    }

    private func followFriend(_ friend: FriendLocation) {
        let latestFriend = displayedFriends.first(where: { $0.id == friend.id }) ?? friend
        centerOnFriend(latestFriend, shouldFollow: true)
    }

    private func isActivelySharingToSquad(_ squadID: String) -> Bool {
        guard appState.isPublishingLiveSharingPresence else { return false }
        let targets: Set<String> =
            appState.sharingCircleIDs.isEmpty && !appState.selectedCircleID.isEmpty
                ? [appState.selectedCircleID]
                : appState.sharingCircleIDs
        return targets.contains(squadID)
    }

    private func squadTrackingCoordinateSignature(for squadID: String) -> String {
        activeSharingFriendsInSquad(squadID: squadID)
            .map { friend in
                let lat = String(format: "%.6f", friend.coordinate.latitude)
                let lng = String(format: "%.6f", friend.coordinate.longitude)
                return "\(friend.id):\(lat):\(lng)"
            }
            .sorted()
            .joined(separator: "|")
    }

    private func activeSharingFriendsInSquad(squadID: String) -> [FriendLocation] {
        guard let circle = appState.circles.first(where: { $0.id == squadID }) else { return [] }
        let now = Date()
        let activeMembers = circle.members.filter { isUsableSquadFollowMember($0, now: now) }
        var result: [FriendLocation] = []
        for m in activeMembers {
            let displayed = displayedFriends.first(where: { $0.id == m.id }) ?? m
            if isUsableSquadFollowMember(displayed, now: now) {
                result.append(displayed)
            }
        }
        if isActivelySharingToSquad(squadID),
           isLocationAuthorizedForMapPin,
           let coordinate = locationService.displayLocation?.coordinate
        {
            guard CLLocationCoordinate2DIsValid(coordinate),
                  coordinate.latitude.isFinite,
                  coordinate.longitude.isFinite
            else { return result }
            let myBase =
                result.first(where: { $0.id == appState.currentUserID })
                ?? displayedFriends.first(where: { $0.id == appState.currentUserID })
            let selfFields = selfDisplayFieldsMergingProfile(myBase: myBase)
            let me = FriendLocation(
                id: appState.currentUserID.isEmpty ? "me" : appState.currentUserID,
                name: selfFields.name,
                avatarName: selfFields.avatarName,
                avatarUrl: selfFields.avatarUrl,
                car: myBase?.car ?? "My Car",
                clubRole: myBase?.clubRole ?? "Driver",
                lastRun: myBase?.lastRun ?? "Now",
                coordinate: coordinate,
                speedMph: Int((locationService.effectiveSpeedMetersPerSecond() * 2.23694).rounded()),
                isOnline: true,
                isActive: true,
                accentColor: selfFields.accentColor,
                movementMode: locationService.movementMode,
                lastUpdatedAt: Date(),
                lastPresenceInApp: true
            )
            if let idx = result.firstIndex(where: { $0.id == appState.currentUserID }) {
                result[idx] = me
            } else {
                result.append(me)
            }
        }
        return result
    }

    private func isUsableSquadFollowMember(_ friend: FriendLocation, now: Date = Date()) -> Bool {
        guard friend.isActive else { return false }
        guard CLLocationCoordinate2DIsValid(friend.coordinate),
              friend.coordinate.latitude.isFinite,
              friend.coordinate.longitude.isFinite
        else { return false }
        guard let updatedAt = friend.lastUpdatedAt else {
            // Older payloads may omit timestamps; keep active rows but never include inactive pseudo-roster pins.
            return friend.lastPresenceInApp != nil
        }
        return now.timeIntervalSince(updatedAt) <= squadFollowFreshnessInterval
    }

    private func coordinateRegionToFit(coordinates: [CLLocationCoordinate2D], paddingFactor: Double = 2.2) -> MKCoordinateRegion? {
        let validCoordinates = coordinates.filter {
            CLLocationCoordinate2DIsValid($0) && $0.latitude.isFinite && $0.longitude.isFinite
        }
        guard !validCoordinates.isEmpty else { return nil }
        if validCoordinates.count == 1 {
            let c = validCoordinates[0]
            return MKCoordinateRegion(center: c, span: Self.defaultTrackingSpan)
        }
        var minLat = validCoordinates[0].latitude
        var maxLat = minLat
        var minLon = validCoordinates[0].longitude
        var maxLon = minLon
        for coordinate in validCoordinates.dropFirst() {
            minLat = min(minLat, coordinate.latitude)
            maxLat = max(maxLat, coordinate.latitude)
            minLon = min(minLon, coordinate.longitude)
            maxLon = max(maxLon, coordinate.longitude)
        }
        let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        guard CLLocationCoordinate2DIsValid(center) else { return nil }
        var latDelta = max((maxLat - minLat) * paddingFactor, 0.016)
        var lonDelta = max((maxLon - minLon) * paddingFactor, 0.016)
        let latitudeZoomEquivalentLongitudeDelta =
            latDelta * max(0.2, cos(center.latitude * .pi / 180))
        lonDelta = max(lonDelta, latitudeZoomEquivalentLongitudeDelta)
        latDelta = min(latDelta, 0.36)
        lonDelta = min(lonDelta, 0.36)
        return MKCoordinateRegion(center: center, span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta))
    }

    private func applyFollowedSquadCameraIfNeeded() {
        guard let sid = followedSquadID, !sid.isEmpty else { return }
        guard !isBuildingDriveLine else { return }
        let friends = activeSharingFriendsInSquad(squadID: sid)
        let coords = friends.map(\.coordinate).filter { CLLocationCoordinate2DIsValid($0) }
        guard !coords.isEmpty, let region = MapPresenceCamera.regionForPresenceCoordinates(coords) else { return }
        setCameraRegion(region)
        hasAppliedInitialCamera = true
        isUsingFallbackCamera = false
    }

    private func revealMapLayerIfNeeded(for friend: FriendLocation) {
        let sharedCircleIDs = circleIDsWhereMember(friend.id)
        if !sharedCircleIDs.isEmpty {
            visibleCircleLayerIDs.formUnion(sharedCircleIDs)
        }
    }

    /// Squads that list this user as a member (used to turn on map layers + refresh roster positions).
    private func circleIDsWhereMember(_ userID: String) -> [String] {
        appState.circles
            .filter { circle in circle.members.contains { $0.id == userID } }
            .map(\.id)
    }

    /// Ensures `circle.members` / `isActive` / coordinates match the server before we rely on `visibleFriends` for markers.
    private func refreshPresenceForCirclesContainingFriend(_ friend: FriendLocation) async {
        for circleID in circleIDsWhereMember(friend.id) where !circleID.isEmpty {
            await appState.refreshPresence(for: circleID, showsStartedSharingToast: false)
        }
    }

    /// Mapbox fires camera updates during view rendering; defer @State writes to the next run loop.
    private func scheduleObservedCameraChange(_ region: MKCoordinateRegion) {
        guard !shouldSuspendMapboxRendering else { return }
        guard isUsableMapCameraRegion(region) else { return }
        lastObservedMapRegion = region
        guard shouldApplyCameraChange(region) else { return }
        let center = region.center
        let latitudeDelta = region.span.latitudeDelta
        Task { @MainActor in
            lastObservedMapRegion = region
            mapCenterCoordinate = center
            currentLatitudeDelta = latitudeDelta
            endFollowModesIfUserAdjustedCamera(region: region)
            enforceZoomBoundsIfNeeded(for: region)
        }
        scheduleMarkerLODUpdate(from: region)
    }

    /// Debounced marker LOD span — mirrors Android `mapVisibleLatitudeDelta` (200 ms after camera settles).
    private func scheduleMarkerLODUpdate(from region: MKCoordinateRegion) {
        latestMarkerLODRegion = region
        if selectedRoute != nil {
            markerLODDebounceTask?.cancel()
            markerLODDebounceTask = nil
            applyMarkerLODSettle(from: region)
            return
        }
        markerLODDebounceTask?.cancel()
        markerLODDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            applyMarkerLODSettle(from: latestMarkerLODRegion)
        }
    }

    private func applyMarkerLODSettle(from region: MKCoordinateRegion, useDriveRouteLOD: Bool = false) {
        let settledDelta: Double
        if useDriveRouteLOD {
            settledDelta = OttoMapboxCamera.visibleLatitudeDeltaDegrees(
                for: MKCoordinateRegion(center: region.center, span: OttoMapboxCamera.driveTrackingSpan)
            )
        } else {
            settledDelta = OttoMapboxCamera.visibleLatitudeDeltaDegrees(for: region)
        }
        guard abs(markerLODLatitudeDelta - settledDelta) > 0.00005 else { return }
        markerLODLatitudeDelta = settledDelta
    }

    /// Ends self / friend / squad follow when the user pans or zooms (not via Otto’s programmatic camera APIs).
    private func shouldApplyCameraChange(_ region: MKCoordinateRegion) -> Bool {
        guard isUsableMapCameraRegion(region) else { return false }
        let currentCenter = CLLocation(latitude: mapCenterCoordinate.latitude, longitude: mapCenterCoordinate.longitude)
        let nextCenter = CLLocation(latitude: region.center.latitude, longitude: region.center.longitude)
        let centerMovedMeters = currentCenter.distance(from: nextCenter)
        let spanChanged = abs(currentLatitudeDelta - region.span.latitudeDelta) > 0.00005
        return centerMovedMeters > 2 || spanChanged
    }

    private func isUsableMapCameraRegion(_ region: MKCoordinateRegion) -> Bool {
        let span = region.span
        guard CLLocationCoordinate2DIsValid(region.center) else { return false }
        guard region.center.latitude.isFinite, region.center.longitude.isFinite else { return false }
        guard span.latitudeDelta.isFinite, span.longitudeDelta.isFinite else { return false }
        guard span.latitudeDelta > 0, span.longitudeDelta > 0 else { return false }
        guard span.latitudeDelta < 120, span.longitudeDelta < 360 else { return false }
        return true
    }

    private func endFollowModesIfUserAdjustedCamera(region: MKCoordinateRegion) {
        guard hasAppliedInitialCamera else { return }
        guard !isProgrammaticCameraMove, !isAdjustingZoomBounds, !isApplyingDriveCameraUpdate else { return }
        guard !suppressFollowCancellationForActiveDriveFollow else { return }
        guard let baseline = lastProgrammaticMapRegion else { return }
        guard cameraFollowMode.isFollowingAnyTarget else { return }

        let baselineCenter = CLLocation(latitude: baseline.center.latitude, longitude: baseline.center.longitude)
        let observedCenter = CLLocation(latitude: region.center.latitude, longitude: region.center.longitude)
        let centerMovedMeters = observedCenter.distance(from: baselineCenter)

        let baseLatDelta = max(baseline.span.latitudeDelta, 1e-9)
        let spanRatio = region.span.latitudeDelta / baseLatDelta
        /// Ignore tiny floating noise; pinch-zoom changes span by much more than this.
        let spanChangedNoticeably = spanRatio > 1.04 || spanRatio < (1 / 1.04)

        let centerMovedNoticeably = centerMovedMeters > 45

        guard centerMovedNoticeably || spanChangedNoticeably else { return }

        cameraFollowMode = .manual
    }

    private func recenterOnFollowedFriendIfNeeded() {
        guard case .followFriend(let followedFriendID) = cameraFollowMode else { return }
        let friend =
            displayedFriends.first(where: { $0.id == followedFriendID })
            ?? allPresenceFriends.first(where: { $0.id == followedFriendID })
        guard let friend else { return }
        centerOnFriend(friend, shouldFollow: true)
    }

    private func recenterOnCurrentUser(force: Bool) {
        guard !isShowingPeerProfileSheet else {
            OttoLog.map.debug("recenterOnCurrentUser skipped: peer profile sheet open")
            return
        }
        guard force || !hasAppliedInitialCamera || isUsingFallbackCamera else { return }

        if let location = locationService.latestSample ?? locationService.lastLocation {
            if usesDriveCameraPitch {
                syncDriveCameraTarget(from: location)
            } else {
                let region = MKCoordinateRegion(
                    center: location.coordinate,
                    span: Self.currentUserTrackingSpan
                )
                setCameraRegion(region)
            }
            hasAppliedInitialCamera = true
            isUsingFallbackCamera = false
            OttoLog.map.info(
                "recenter: from GPS fix lat=\(location.coordinate.latitude) lng=\(location.coordinate.longitude)"
            )
            return
        }

        if force {
            setCameraRegion(Self.fallbackRegion)
            hasAppliedInitialCamera = true
            isUsingFallbackCamera = true
            OttoLog.map.info("recenter: using fallback region (no member, no GPS yet)")
        }
    }

    private func syncDriveCameraPitchState(from location: CLLocation? = nil) {
        if usesDriveCameraPitch {
            let enteringDrivePitch = !isDriveCameraPitchEngaged
            if enteringDrivePitch {
                cameraFollowMode = .followSelf
                if isRouteDriveSessionOnMap {
                    applyMarkerLODSettle(
                        from: MKCoordinateRegion(
                            center: mapCenterCoordinate,
                            span: OttoMapboxCamera.driveTrackingSpan
                        ),
                        useDriveRouteLOD: true
                    )
                }
            }
            guard !isDriveCameraPitchEngaged else { return }
            isDriveCameraPitchEngaged = true
            enterDriveCameraMode(from: location)
            return
        }
        guard isDriveCameraPitchEngaged else { return }
        isDriveCameraPitchEngaged = false
        resetDriveCameraSmoothing()
        flattenCameraToTopDown()
    }

    private func resetDriveCameraSmoothing() {
        driveCameraTargetCoordinate = nil
        driveCameraRenderedCoordinate = nil
        driveCameraTargetBearing = 0
        driveCameraRenderedBearing = 0
        driveCameraPreviousSample = nil
    }

    private func syncDriveCameraTarget(from location: CLLocation) {
        let previous = driveCameraPreviousSample
        driveCameraTargetCoordinate = location.coordinate
        driveCameraTargetBearing = OttoMapboxCamera.driveBearing(
            from: location,
            previous: previous,
            fallback: driveCameraTargetBearing
        )
        driveCameraPreviousSample = location
    }

    private func enterDriveCameraMode(from location: CLLocation? = nil) {
        let resolved = location ?? locationService.latestSample ?? locationService.lastLocation
        guard let resolved else {
            recenterOnCurrentUser(force: true)
            return
        }
        syncDriveCameraTarget(from: resolved)
        driveCameraRenderedCoordinate = resolved.coordinate
        driveCameraRenderedBearing = driveCameraTargetBearing
        applyDriveCameraViewport(
            center: resolved.coordinate,
            bearing: driveCameraRenderedBearing,
            animated: true
        )
    }

    private func recenterDriveCamera(from location: CLLocation, animated: Bool) {
        syncDriveCameraTarget(from: location)
        driveCameraRenderedCoordinate = location.coordinate
        driveCameraRenderedBearing = driveCameraTargetBearing
        applyDriveCameraViewport(
            center: location.coordinate,
            bearing: driveCameraTargetBearing,
            animated: animated
        )
    }

    /// Scope FAB during drive: re-engage pitched follow with default zoom, padding, and heading.
    private func restoreDefaultDriveFollowCamera(from location: CLLocation) {
        if !isDriveCameraPitchEngaged {
            isDriveCameraPitchEngaged = true
            enterDriveCameraMode(from: location)
        } else {
            recenterDriveCamera(from: location, animated: true)
        }
    }

    private func beginProgrammaticDriveCameraMove(duration: TimeInterval) {
        driveCameraProgrammaticMoveGeneration += 1
        let generation = driveCameraProgrammaticMoveGeneration
        isApplyingDriveCameraUpdate = true
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(max(duration, 0) * 1_000_000_000))
            if driveCameraProgrammaticMoveGeneration == generation {
                isApplyingDriveCameraUpdate = false
            }
        }
    }

    private func stepDriveCameraSmoothing() {
        guard usesDriveCameraPitch, isDriveCameraPitchEngaged, cameraFollowMode.isFollowingSelf else { return }
        guard let target = driveCameraTargetCoordinate else { return }

        let current = driveCameraRenderedCoordinate ?? target
        let newCoordinate = CLLocationCoordinate2D(
            latitude: interpolate(current.latitude, target.latitude, factor: 0.38),
            longitude: interpolate(current.longitude, target.longitude, factor: 0.38)
        )
        let newBearing = OttoMapboxCamera.interpolateBearing(
            from: driveCameraRenderedBearing,
            to: driveCameraTargetBearing,
            factor: 0.24
        )

        let movedMeters = CLLocation(latitude: current.latitude, longitude: current.longitude)
            .distance(from: CLLocation(latitude: newCoordinate.latitude, longitude: newCoordinate.longitude))
        let bearingDelta = abs(
            OttoMapboxCamera.shortPathBearingDelta(from: driveCameraRenderedBearing, to: newBearing)
        )
        guard movedMeters > 0.15 || bearingDelta > 0.2 else { return }

        driveCameraRenderedCoordinate = newCoordinate
        driveCameraRenderedBearing = newBearing

        let region = MKCoordinateRegion(
            center: newCoordinate,
            span: OttoMapboxCamera.driveTrackingSpan
        )
        beginProgrammaticDriveCameraMove(duration: 0.05)
        lastProgrammaticMapRegion = region
        lastObservedMapRegion = region
        mapViewport = OttoMapboxCamera.viewport(
            for: region,
            bearing: newBearing,
            pitch: OttoMapboxCamera.drivePitchDegrees,
            followPadding: driveFollowEdgeInsets
        )
    }

    private var driveFollowEdgeInsets: SwiftUI.EdgeInsets? {
        guard usesDriveCameraPitch, isDriveCameraPitchEngaged else { return nil }
        return OttoMapboxCamera.driveFollowEdgeInsets(mapHeight: mapViewportLayoutSize.height)
    }

    /// Returns to north-up top-down after a drive ends, even if the user panned away during the session.
    private func flattenCameraToTopDown() {
        let center = (locationService.latestSample ?? locationService.lastLocation)?.coordinate
            ?? mapCenterCoordinate
        let region = MKCoordinateRegion(
            center: center,
            span: MKCoordinateSpan(
                latitudeDelta: currentLatitudeDelta,
                longitudeDelta: max(currentLatitudeDelta, 0.000001)
            )
        )
        setCameraRegion(
            region,
            bearing: 0,
            pitch: 0,
            animated: true,
            useDriveFollowPadding: false,
            animationDuration: OttoMapboxCamera.driveCameraTransitionDuration
        )
    }

    private func applyDriveCameraViewport(
        center: CLLocationCoordinate2D,
        bearing: CGFloat,
        animated: Bool
    ) {
        if animated {
            beginProgrammaticDriveCameraMove(duration: OttoMapboxCamera.driveCameraTransitionDuration)
        }
        let region = MKCoordinateRegion(
            center: center,
            span: OttoMapboxCamera.driveTrackingSpan
        )
        setCameraRegion(
            region,
            bearing: bearing,
            pitch: OttoMapboxCamera.drivePitchDegrees,
            animated: animated,
            useDriveFollowPadding: true,
            animationDuration: animated ? OttoMapboxCamera.driveCameraTransitionDuration : nil
        )
    }

    private func setCameraRegion(
        _ region: MKCoordinateRegion,
        bearing: CGFloat = 0,
        pitch: CGFloat = 0,
        animated: Bool? = nil,
        useDriveFollowPadding: Bool = false,
        animationDuration: TimeInterval? = nil
    ) {
        isProgrammaticCameraMove = true
        programmaticCameraMoveGeneration += 1
        let generation = programmaticCameraMoveGeneration
        lastProgrammaticMapRegion = region
        lastObservedMapRegion = region
        let followPadding = useDriveFollowPadding
            ? OttoMapboxCamera.driveFollowEdgeInsets(mapHeight: mapViewportLayoutSize.height)
            : nil
        let viewport = OttoMapboxCamera.viewport(
            for: region,
            bearing: bearing,
            pitch: pitch,
            followPadding: followPadding
        )
        let shouldAnimate = animated ?? hasAppliedInitialCamera
        if shouldAnimate {
            let duration = animationDuration ?? (pitch > 0 ? OttoMapboxCamera.driveCameraTransitionDuration : 0.25)
            withViewportAnimation(.easeOut(duration: duration)) {
                mapViewport = viewport
            }
        } else {
            mapViewport = viewport
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 450_000_000)
            if programmaticCameraMoveGeneration == generation {
                isProgrammaticCameraMove = false
            }
        }
    }

    /// Presents the circle / duration sharing sheet when opened via `otto://share` (e.g. home screen widget).
    @discardableResult
    private func applyPendingSharingSheetIfNeeded() -> Bool {
        guard appState.isAuthenticated else { return false }
        guard appState.consumePendingSharingSheetPresentation() != nil else { return false }
        syncSharingDraftsFromSession()
        isShowingCirclePicker = true
        return true
    }

    /// Selects a saved route on the map when opened from Drive Summary.
    @discardableResult
    private func applyPendingMapRouteSelectionIfNeeded() -> Bool {
        guard let pending = appState.consumePendingMapRouteSelection() else { return false }
        selectRouteForMap(pending.route)
        return true
    }

    /// Centers the map on `appState.pendingMapFocus` if present (e.g. user tapped a row in My places).
    @discardableResult
    private func applyPendingSavedPlaceMapFocusIfNeeded() -> Bool {
        guard isActive else { return false }
        let previewFromPendingMapFocus = appState.pendingMapEventPreview
        guard let focus = appState.consumePendingMapFocus() else { return false }
        let coordinate = CLLocationCoordinate2D(latitude: focus.latitude, longitude: focus.longitude)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return false }
        let region = MKCoordinateRegion(center: coordinate, span: Self.defaultTrackingSpan)
        setCameraRegion(region)
        mapCenterCoordinate = coordinate
        hasAppliedInitialCamera = true
        isUsingFallbackCamera = false
        cameraFollowMode = .manual
        if let eventID = focus.eventID {
            let event = appState.resolvedEventForMapPeek(
                eventID: eventID,
                preview: previewFromPendingMapFocus
            )
            mapPreviewSession = event.map { .upcomingEvent(primary: $0, siblings: []) }
        } else if let snapshot = focus.savedPlaceSnapshot {
            mapPreviewSession = .savedPlace(snapshot)
            registerChatSharedPlacePeekMarker(snapshot)
        } else if let savedPlaceID = focus.savedPlaceID,
                  let place = appState.savedPlaces.first(where: { $0.id == savedPlaceID }) {
            mapPreviewSession = .savedPlace(place)
        } else {
            mapPreviewSession = nil
        }
        return true
    }

    private func handleFriendGroupTap(_ group: FriendProximityGroup) {
        guard !group.members.isEmpty else { return }
        if group.members.count == 1, let friend = group.members.first {
            centerOnFriend(friend)
            openPeerProfile(userID: friend.id)
            return
        }

        mapPreviewSession = .clusterPick(UUID(), group.members)
    }

    private func centroid(for coordinates: [CLLocationCoordinate2D]) -> CLLocationCoordinate2D {
        guard !coordinates.isEmpty else { return Self.fallbackRegion.center }
        let sum = coordinates.reduce((lat: 0.0, lng: 0.0)) { partial, coordinate in
            (partial.lat + coordinate.latitude, partial.lng + coordinate.longitude)
        }
        return CLLocationCoordinate2D(
            latitude: sum.lat / Double(coordinates.count),
            longitude: sum.lng / Double(coordinates.count)
        )
    }

    private func radiusPolygon(
        center: CLLocationCoordinate2D,
        radiusMeters: CLLocationDistance,
        segments: Int = 64
    ) -> Polygon {
        let latitudeRadians = center.latitude * .pi / 180
        let metersPerLatitudeDegree = 111_320.0
        let metersPerLongitudeDegree = max(1, metersPerLatitudeDegree * cos(latitudeRadians))
        var ring: [CLLocationCoordinate2D] = []
        ring.reserveCapacity(segments + 1)

        for idx in 0..<segments {
            let angle = (Double(idx) / Double(segments)) * 2 * .pi
            let latitude = center.latitude + (sin(angle) * radiusMeters / metersPerLatitudeDegree)
            let longitude = center.longitude + (cos(angle) * radiusMeters / metersPerLongitudeDegree)
            ring.append(CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        }
        if let first = ring.first {
            ring.append(first)
        }
        return Polygon([ring])
    }

    private func updateDwellStates() {
        let now = Date()
        let areaThresholdMeters: CLLocationDistance = 140
        let staleAfter: TimeInterval = 90

        var next = dwellByFriendID
        let others = visibleFriends.filter { $0.id != appState.currentUserID }
        let currentIDs = Set(others.map(\.id))

        for friend in others {
            if let existing = next[friend.id] {
                let movedMeters = CLLocation(latitude: existing.anchor.latitude, longitude: existing.anchor.longitude)
                    .distance(from: CLLocation(latitude: friend.coordinate.latitude, longitude: friend.coordinate.longitude))
                if movedMeters <= areaThresholdMeters {
                    next[friend.id] = FriendDwellState(
                        anchor: existing.anchor,
                        enteredAt: existing.enteredAt,
                        lastSeenAt: now
                    )
                } else {
                    next[friend.id] = FriendDwellState(
                        anchor: friend.coordinate,
                        enteredAt: now,
                        lastSeenAt: now
                    )
                }
            } else {
                next[friend.id] = FriendDwellState(
                    anchor: friend.coordinate,
                    enteredAt: now,
                    lastSeenAt: now
                )
            }
        }

        for (friendID, state) in next where !currentIDs.contains(friendID) && now.timeIntervalSince(state.lastSeenAt) > staleAfter {
            next.removeValue(forKey: friendID)
        }
        dwellByFriendID = next
    }

    private func dwellLabel(for friendID: String) -> String? {
        guard let state = dwellByFriendID[friendID] else { return nil }
        let elapsed = Date().timeIntervalSince(state.enteredAt)
        guard elapsed >= 60 else { return nil }
        return "here for \(formatDwellDuration(elapsed))"
    }

    private func dwellLabel(for members: [FriendLocation]) -> String? {
        let labels = members.compactMap { member -> (TimeInterval, String)? in
            guard let state = dwellByFriendID[member.id] else { return nil }
            let elapsed = Date().timeIntervalSince(state.enteredAt)
            guard elapsed >= 60 else { return nil }
            return (elapsed, "here for \(formatDwellDuration(elapsed))")
        }
        return labels.max(by: { $0.0 < $1.0 })?.1
    }

    private func staleUpdateLabel(for friendID: String) -> String? {
        guard let lastUpdate = lastLocationUpdateAtByFriendID[friendID] else { return nil }
        let elapsed = Date().timeIntervalSince(lastUpdate)
        guard elapsed >= staleLocationAfter else { return nil }
        return "Here \(formatDwellDuration(elapsed)) ago"
    }

    private func statusLabel(for friendID: String) -> String? {
        staleUpdateLabel(for: friendID) ?? dwellLabel(for: friendID)
    }

    private func statusLabel(for members: [FriendLocation]) -> String? {
        let stale = members.compactMap { member -> (TimeInterval, String)? in
            guard let lastUpdate = lastLocationUpdateAtByFriendID[member.id] else { return nil }
            let elapsed = Date().timeIntervalSince(lastUpdate)
            guard elapsed >= staleLocationAfter else { return nil }
            return (elapsed, "Here \(formatDwellDuration(elapsed)) ago")
        }
        if let mostStale = stale.max(by: { $0.0 < $1.0 })?.1 {
            return mostStale
        }
        return dwellLabel(for: members)
    }

    private func formatDwellDuration(_ elapsed: TimeInterval) -> String {
        let totalMinutes = Int(elapsed / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0 {
            return "\(hours) hr, \(minutes) min"
        }
        return "\(minutes) min"
    }
}

private struct FriendAnnotationView: View {
    @EnvironmentObject private var appState: AppState
    let friend: FriendLocation
    let isCurrentUser: Bool
    let brandLogoURL: URL?
    let dwellText: String?
    var travelSurface: TravelSurface = .land
    var horizonScale: CGFloat = 1

    var body: some View {
        VStack(spacing: 6) {
            if let dwellText {
                dwellChip(text: dwellText)
            }
            avatar
            if shouldShowMovementChip {
                movementChip
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 7)
        .scaleEffect(horizonScale)
    }

    private var avatar: some View {
        let size: CGFloat = isCurrentUser ? 50 : 46
        let logoSize: CGFloat = 28
        let logoHalf = logoSize / 2

        return ZStack(alignment: .top) {
            AvatarView(
                name: friend.name,
                avatarUrl: resolvedAvatarUrlForPeer(friend),
                size: size,
                accentColor: friend.accentColor,
                accentRingWidth: 4,
                whiteRingWidth: 2,
                shape: .roundedSquare(cornerRadius: 12)
            )
            .shadow(color: friend.accentColor.opacity(0.45), radius: 12, y: 3)
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(friend.presenceStatus.color)
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(.black, lineWidth: 1.5))
            }

            if let logoURL = brandLogoURL {
                CarBrandLogoMarkerBadge(url: logoURL)
                    .frame(width: logoSize, height: logoSize)
                    .offset(y: -logoHalf)
            }
        }
        .padding(.top, brandLogoURL != nil ? logoHalf : 0)
    }

    private var movementChip: some View {
        HStack(spacing: 5) {
            Image(systemName: movementSymbolName)
                .font(.caption2.weight(.bold))
                .foregroundStyle(friend.presenceStatus.color)
            Text(movementText)
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.black.opacity(0.8))
        .clipShape(Capsule())
    }

    private var isMovingFastEnoughForBoat: Bool {
        Double(friend.speedMph) >= MapTravelSurfaceSampler.minSpeedMphForBoat
    }

    private var movementSymbolName: String {
        if travelSurface == .water, isMovingFastEnoughForBoat {
            return "sailboat.fill"
        }
        switch friend.movementMode {
        case .driving:
            return "steeringwheel"
        case .walking:
            return "figure.walk"
        case .unknown:
            return isMovingFastEnoughForBoat ? "steeringwheel" : "person.crop.circle"
        }
    }

    private var movementText: String {
        switch friend.movementMode {
        case .driving, .walking:
            return "\(friend.speedMph) mph"
        case .unknown:
            return isMovingFastEnoughForBoat ? "\(friend.speedMph) mph" : ""
        }
    }

    private var shouldShowMovementChip: Bool {
        let showsKnownMode = friend.movementMode == .driving || friend.movementMode == .walking
        if isCurrentUser, !friend.isActive {
            return showsKnownMode || isMovingFastEnoughForBoat
        }
        guard friend.isActive else { return false }
        return showsKnownMode || isMovingFastEnoughForBoat
    }

    private func dwellChip(text: String) -> some View {
        HStack(spacing: 0) {
            Text(text)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.black.opacity(0.8))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.white.opacity(0.95))
        .clipShape(Capsule())
        .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
    }

    /// Roster rows may omit `avatarUrl`; match list UI by falling back to `AppState.allUsers`.
    private func resolvedAvatarUrlForPeer(_ friend: FriendLocation) -> String? {
        let trimmed = friend.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty { return friend.avatarUrl }
        guard let raw = appState.allUsers.first(where: { $0.id == friend.id })?.avatarUrl else { return nil }
        let profileTrimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return profileTrimmed.isEmpty ? nil : raw
    }

}

private struct CarBrandLogoMarkerBadge: View {
    let url: URL
    private let badgeSize: CGFloat = 28

    var body: some View {
        CachedAsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFit()
                    .shadow(color: .black.opacity(0.22), radius: 1.5, x: 0, y: 1)
            case .empty:
                Color.clear
            case .failure:
                Image(systemName: "car.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
            @unknown default:
                Color.clear
            }
        }
        .frame(width: badgeSize, height: badgeSize)
    }
}

private struct BouncyMarkerContainer<Content: View>: View {
    /// Start visible so Mapbox `MapViewAnnotation` never leaves pins at opacity 0 if `onAppear` is flaky.
    @State private var scale: CGFloat = 0.94
    @ViewBuilder let content: Content

    var body: some View {
        content
            .scaleEffect(scale)
            .onAppear {
                scale = 0.94
                withAnimation(.spring(response: 0.34, dampingFraction: 0.62, blendDuration: 0.12)) {
                    scale = 1.0
                }
            }
            .task {
                try? await Task.sleep(nanoseconds: 320_000_000)
                if scale < 0.999 {
                    scale = 1.0
                }
            }
            .onDisappear {
                scale = 0.94
            }
    }
}

private struct CompositeFriendAnnotationView: View {
    @EnvironmentObject private var appState: AppState
    let members: [FriendLocation]
    let currentUserID: String
    let dwellText: String?
    var horizonScale: CGFloat = 1

    private var orderedMembers: [FriendLocation] {
        members.sorted { lhs, rhs in
            if lhs.isActive != rhs.isActive { return lhs.isActive && !rhs.isActive }
            return lhs.id < rhs.id
        }
    }

    private var currentUserMember: FriendLocation? {
        orderedMembers.first { $0.id == currentUserID }
    }

    private var visibleMembers: [FriendLocation] {
        if let currentUserMember {
            return Array(orderedMembers.filter { $0.id != currentUserMember.id }.prefix(2)) + [currentUserMember]
        }
        return Array(orderedMembers.prefix(3))
    }

    private var topMember: FriendLocation? {
        if let currentUserMember {
            return visibleMembers.first { $0.id != currentUserMember.id && $0.id != bottomLeftMember?.id }
        }
        return visibleMembers.count >= 3 ? visibleMembers[2] : nil
    }

    private var bottomLeftMember: FriendLocation? {
        if let currentUserMember {
            return visibleMembers.first { $0.id != currentUserMember.id }
        }
        return visibleMembers.first
    }

    private var bottomRightMember: FriendLocation? {
        if let currentUserMember {
            return currentUserMember
        }
        if visibleMembers.count >= 2 {
            return visibleMembers[1]
        }
        return nil
    }

    private var hiddenCount: Int {
        max(0, members.count - visibleMembers.count)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            // Pointer sits behind the avatar tiles.
            DiamondPointer()
                .fill(Color.white)
                .frame(width: 16, height: 16)
                .offset(y: 5)

            ZStack(alignment: .topTrailing) {
                ZStack {
                    if let bottomLeftMember {
                        avatarBubble(for: bottomLeftMember, size: 46, cornerRadius: 13)
                            .position(x: 28, y: 52)
                    }

                    if let bottomRightMember, bottomRightMember.id != bottomLeftMember?.id {
                        avatarBubble(for: bottomRightMember, size: 46, cornerRadius: 13)
                            .position(x: 62, y: 52)
                    }

                    if let topMember, topMember.id != bottomLeftMember?.id, topMember.id != bottomRightMember?.id {
                        avatarBubble(for: topMember, size: 42, cornerRadius: 12)
                            .position(x: 45, y: 14)
                    }
                }
                .frame(width: 90, height: 76)

                if hiddenCount > 0 {
                    Text("+\(hiddenCount)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(Circle().fill(Color.black.opacity(0.9)))
                        .overlay(Circle().stroke(Color.white, lineWidth: 2))
                        .offset(x: 2, y: -2)
                }
            }
        }
        .compositingGroup()
        .scaleEffect(horizonScale)
    }

    private func avatarBubble(for friend: FriendLocation, size: CGFloat, cornerRadius: CGFloat) -> some View {
        AvatarView(
            name: friend.name,
            avatarUrl: resolvedAvatarUrlForPeer(friend),
            size: size,
            accentColor: friend.accentColor,
            accentRingWidth: 0,
            whiteRingWidth: 0,
            shape: .roundedSquare(cornerRadius: cornerRadius)
        )
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(Color.white, lineWidth: 3)
        )
    }

    private func resolvedAvatarUrlForPeer(_ friend: FriendLocation) -> String? {
        let trimmed = friend.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty { return friend.avatarUrl }
        guard let raw = appState.allUsers.first(where: { $0.id == friend.id })?.avatarUrl else { return nil }
        let profileTrimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return profileTrimmed.isEmpty ? nil : raw
    }
}

private struct DiamondPointer: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

private struct MapFriendSearchSheet: View {
    private enum TabSelection: Int, Hashable {
        case people
        case squads
    }

    let friends: [FriendLocation]
    let squads: [DriveCircle]
    let followedSquadID: String?
    let updateLabelsByFriendID: [String: String]
    let onSelectFriend: (FriendLocation) -> Void
    let onSelectSquad: (DriveCircle) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTab: TabSelection = .people
    @State private var peopleSearchText = ""
    @State private var squadSearchText = ""

    private var filteredFriends: [FriendLocation] {
        let trimmedSearch = peopleSearchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSearch.isEmpty else { return friends }
        return friends.filter {
            $0.name.localizedCaseInsensitiveContains(trimmedSearch)
                || $0.clubRole.localizedCaseInsensitiveContains(trimmedSearch)
        }
    }
    private var filteredSquads: [DriveCircle] {
        let trimmedSearch = squadSearchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let matched: [DriveCircle]
        if trimmedSearch.isEmpty {
            matched = squads
        } else {
            matched = squads.filter { $0.name.localizedCaseInsensitiveContains(trimmedSearch) }
        }
        return matched.sortedForMapSquadList()
    }

    private func squadMapSubtitle(for circle: DriveCircle) -> String {
        circle.mapSharingStatusSubtitle
    }

    /// Larger, higher-contrast tab control than the system segmented picker.
    private struct PeopleSquadsToggle: View {
        @Binding var selection: MapFriendSearchSheet.TabSelection

        var body: some View {
            HStack(spacing: 6) {
                tabChip(title: "People", tab: .people)
                tabChip(title: "Squads", tab: .squads)
            }
            .padding(6)
            .frame(minHeight: 54)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.white.opacity(0.065))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.white.opacity(0.14), lineWidth: 1)
            )
        }

        private func tabChip(title: String, tab: MapFriendSearchSheet.TabSelection) -> some View {
            let selected = selection == tab
            return Button {
                withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) {
                    selection = tab
                }
            } label: {
                Text(title)
                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                    .foregroundStyle(selected ? .white : .white.opacity(0.52))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background {
                        if selected {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color(red: 0.58, green: 0.33, blue: 0.95),
                                            Color(red: 0.93, green: 0.32, blue: 0.55),
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .shadow(color: Color.purple.opacity(0.35), radius: 10, y: 3)
                        }
                    }
            }
            .buttonStyle(.plain)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    OttoMapSheetHeader(title: "Find on Map", onDone: { dismiss() })

                    PeopleSquadsToggle(selection: $selectedTab)
                        .padding(.bottom, 6)

                    switch selectedTab {
                    case .people:
                        OttoSearchBar(text: $peopleSearchText, placeholder: "Search people sharing", showsAction: false)

                        if filteredFriends.isEmpty {
                            UnifiedEmptyStateView(
                                title: "No One Sharing",
                                message: friends.isEmpty
                                    ? "People who are currently sharing will appear here."
                                    : "Try another search.",
                                systemImage: "location"
                            )
                            .frame(minHeight: 260)
                        } else {
                            MapPeopleListCard(
                                friends: filteredFriends,
                                updateLabelsByFriendID: updateLabelsByFriendID,
                                onSelect: onSelectFriend
                            )
                        }

                    case .squads:
                        OttoSearchBar(text: $squadSearchText, placeholder: "Search squads", showsAction: false)

                        if squads.isEmpty {
                            UnifiedEmptyStateView(
                                title: "No Squads Yet",
                                message: "Join or create a squad to track everyone sharing on the map.",
                                systemImage: "person.3"
                            )
                            .frame(minHeight: 220)
                        } else if filteredSquads.isEmpty {
                            UnifiedEmptyStateView(
                                title: "No Matches",
                                message: "Try another search.",
                                systemImage: "person.3"
                            )
                            .frame(minHeight: 220)
                        } else {
                            LazyVStack(spacing: 12) {
                                ForEach(filteredSquads) { circle in
                                    Button {
                                        onSelectSquad(circle)
                                    } label: {
                                        CircleRowCard(
                                            circle: circle,
                                            unreadCount: 0,
                                            subtitleOverride: squadMapSubtitle(for: circle),
                                            isTrackedOnMap: followedSquadID == circle.id
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, OttoScreenChrome.bottomPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
        }
        .presentationDetents([.medium, .large])
    }
}

#if DEBUG
private enum MapboxGLSuspendDebugLog {
    static func log(
        event: String,
        suspended: Bool,
        localRouteBuilder: Bool,
        globalRouteBuilder: Bool,
        isActive: Bool
    ) {
        print(
            "[MapScreen] \(event) → mapbox GL suspended=\(suspended) " +
            "localRB=\(localRouteBuilder) globalRB=\(globalRouteBuilder) isActive=\(isActive)"
        )
    }

    static func logTransition(
        suspended: Bool,
        localRouteBuilder: Bool,
        globalRouteBuilder: Bool,
        isActive: Bool
    ) {
        let verb = suspended ? "suspend (unmount tab map)" : "resume (remount tab map)"
        print(
            "[MapScreen] mapbox GL \(verb) " +
            "localRB=\(localRouteBuilder) globalRB=\(globalRouteBuilder) isActive=\(isActive)"
        )
    }
}
#endif
