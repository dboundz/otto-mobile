import CoreLocation
import Combine
import CarPlay
import MapboxMaps
import MapKit
import SwiftUI

private enum CarPlayMapSmoothingTimer {
    static let tick = Timer.publish(every: 0.20, on: .main, in: .common).autoconnect()
}

@MainActor
final class CarPlayMapController: ObservableObject {
    weak var interfaceController: CPInterfaceController?
    private weak var appState: AppState?
    private weak var locationService: LocationService?

    @Published private(set) var recenterGeneration = 0
    @Published private(set) var focusRequest: CarPlayMapFocusRequest?
    @Published private(set) var showPublicPresenceLayer = false
    @Published private(set) var showTrafficLayer = true
    @Published private(set) var showUpcomingEventsLayer = true
    @Published private(set) var showRaceTracksLayer = true
    @Published private(set) var showSavedPlacesLayer = true
    @Published private(set) var visibleCircleLayerIDs: Set<String> = []

    func requestRecenter() {
        recenterGeneration &+= 1
    }

    func configure(appState: AppState, locationService: LocationService) {
        self.appState = appState
        self.locationService = locationService
    }

    func presentTemplate(_ template: CPTemplate) {
        DispatchQueue.main.async { [weak self] in
            self?.interfaceController?.presentTemplate(template, animated: true, completion: nil)
        }
    }

    func dismissPresentedTemplate() {
        DispatchQueue.main.async { [weak self] in
            self?.interfaceController?.dismissTemplate(animated: true, completion: nil)
        }
    }

    func pushTemplate(_ template: CPTemplate) {
        DispatchQueue.main.async { [weak self] in
            self?.interfaceController?.pushTemplate(template, animated: true, completion: nil)
        }
    }

    func popTemplate(completion: (() -> Void)? = nil) {
        DispatchQueue.main.async { [weak self] in
            guard let interfaceController = self?.interfaceController else {
                completion?()
                return
            }
            interfaceController.popTemplate(animated: true) { _, _ in
                completion?()
            }
        }
    }

    func replacePresentedTemplate(with template: CPTemplate) {
        DispatchQueue.main.async { [weak self] in
            guard let interfaceController = self?.interfaceController else { return }
            interfaceController.dismissTemplate(animated: true) { _, _ in
                DispatchQueue.main.async {
                    interfaceController.presentTemplate(template, animated: true, completion: nil)
                }
            }
        }
    }

    func presentHazardReportTemplate() {
        let buttons = MapHazardType.allCases.map { type in
            CPGridButton(
                titleVariants: [type.title],
                image: carPlayHazardGridImage(for: type)
            ) { [weak self] _ in
                self?.hazardReportSelectionHaptic()
                Task { @MainActor in
                    await self?.submitCarPlayHazardReport(type)
                }
            }
        }
        let template = CPGridTemplate(title: "What do you see?", gridButtons: buttons)
        template.leadingNavigationBarButtons = [
            CPBarButton(title: "Cancel") { [weak self] _ in
                self?.popTemplate()
            }
        ]
        pushTemplate(template)
    }

    func presentDriveControlsTemplate() {
        guard let appState else { return }
        if appState.hasActiveDriveSession {
            let endDrive = CPAlertAction(title: "End Drive", style: .destructive) { [weak self] _ in
                Task { @MainActor in
                    await self?.stopActiveDriveFromCarPlay()
                }
            }
            let cancel = CPAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                self?.dismissPresentedTemplate()
            }
            presentTemplate(
                CPActionSheetTemplate(
                    title: "End Drive?",
                    message: "Stop the active drive from CarPlay?",
                    actions: [endDrive, cancel]
                )
            )
        } else {
            let ok = CPAlertAction(title: "OK", style: .cancel) { [weak self] _ in
                self?.dismissPresentedTemplate()
            }
            presentTemplate(
                CPAlertTemplate(
                    titleVariants: ["Start on iPhone"],
                    actions: [ok]
                )
            )
        }
    }

    private func replacePresentedTemplateWithCarPlayAlert(title: String, actionTitle: String = "OK") {
        let ok = CPAlertAction(title: actionTitle, style: .cancel) { [weak self] _ in
            self?.dismissPresentedTemplate()
        }
        replacePresentedTemplate(
            with: CPAlertTemplate(
                titleVariants: [title],
                actions: [ok]
            )
        )
    }

    private func presentCarPlayAlert(title: String, actionTitle: String = "OK") {
        let ok = CPAlertAction(title: actionTitle, style: .cancel) { [weak self] _ in
            self?.dismissPresentedTemplate()
        }
        presentTemplate(
            CPAlertTemplate(
                titleVariants: [title],
                actions: [ok]
            )
        )
    }

    private func submitCarPlayHazardReport(_ type: MapHazardType) async {
        guard let appState, let locationService else { return }
        let location = locationService.latestSample ?? locationService.lastLocation
        guard let coordinate = location?.coordinate, CLLocationCoordinate2DIsValid(coordinate) else {
            appState.activeToast = AppToast(text: "Location unavailable", systemImage: "location.slash.fill")
            popTemplate { [weak self] in
                self?.presentCarPlayAlert(title: "Location unavailable")
            }
            return
        }
        popTemplate()
        _ = await appState.reportMapHazard(type: type, coordinate: coordinate)
    }

    private func carPlayHazardGridImage(for type: MapHazardType) -> UIImage {
        let configuration = UIImage.SymbolConfiguration(pointSize: 34, weight: .semibold)
        guard let symbol = UIImage(systemName: type.systemImage, withConfiguration: configuration) else {
            return UIImage()
        }
        let size = CGSize(width: 48, height: 48)
        let maxSymbolSize = CGSize(width: 38, height: 38)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            let tintedSymbol = symbol.withTintColor(carPlayHazardColor(for: type), renderingMode: .alwaysOriginal)
            let symbolSize = tintedSymbol.size
            let scale = min(maxSymbolSize.width / symbolSize.width, maxSymbolSize.height / symbolSize.height, 1)
            let fittedSize = CGSize(width: symbolSize.width * scale, height: symbolSize.height * scale)
            let symbolRect = CGRect(
                x: (size.width - fittedSize.width) / 2,
                y: (size.height - fittedSize.height) / 2,
                width: fittedSize.width,
                height: fittedSize.height
            )
            tintedSymbol.draw(in: symbolRect)
        }.withRenderingMode(.alwaysOriginal)
    }

    private func carPlayHazardColor(for type: MapHazardType) -> UIColor {
        switch type {
        case .police:
            return UIColor(red: 0.20, green: 0.48, blue: 1.00, alpha: 1)
        case .traffic:
            return UIColor(red: 1.00, green: 0.54, blue: 0.12, alpha: 1)
        case .crash:
            return UIColor(red: 0.90, green: 0.12, blue: 0.16, alpha: 1)
        case .hazard:
            return .systemYellow
        }
    }

    private func hazardReportSelectionHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        if #available(iOS 13.0, *) {
            generator.impactOccurred(intensity: 0.78)
        } else {
            generator.impactOccurred()
        }
    }

    private func stopActiveDriveFromCarPlay() async {
        guard let appState, let locationService else { return }
        dismissPresentedTemplate()
        let location = locationService.latestSample ?? locationService.lastLocation
        if appState.activeRouteDriveSession != nil {
            _ = await appState.stopRouteDriveSession(location: location)
        } else {
            _ = await appState.stopDriveSession(location: location)
            await appState.refreshRecentDrives()
        }
    }

    func requestFocus(on coordinate: CLLocationCoordinate2D) {
        focusRequest = CarPlayMapFocusRequest(coordinate: coordinate)
    }

    func syncLayerPreferences(defaultCircleIDs: Set<String>) {
        let preferences = OttoMapLayerPreferences.current(defaultCircleIDs: defaultCircleIDs)
        showPublicPresenceLayer = preferences.showPublicPresenceLayer
        showTrafficLayer = preferences.showTrafficLayer
        showUpcomingEventsLayer = preferences.showUpcomingEventsLayer
        showRaceTracksLayer = preferences.showRaceTracksLayer
        showSavedPlacesLayer = preferences.showSavedPlacesLayer
        visibleCircleLayerIDs = preferences.visibleCircleLayerIDs
    }
}

struct CarPlayMapFocusRequest: Identifiable, Equatable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D

    static func == (lhs: CarPlayMapFocusRequest, rhs: CarPlayMapFocusRequest) -> Bool {
        lhs.id == rhs.id
    }
}

private struct CarPlayPresenceGroup: Identifiable {
    let members: [FriendLocation]
    let coordinate: CLLocationCoordinate2D

    var id: String {
        members.map(\.id).sorted().joined(separator: "|")
    }
}

private struct CarPlayAnchoredUpcomingEvent: Identifiable {
    let id: String
    let event: EventDTO
    let coordinate: CLLocationCoordinate2D
}

private struct CarPlayAnchoredUpcomingEventGroup: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let events: [EventDTO]
}

struct CarPlayMapView: View {
    private enum Metrics {
        static let scale: CGFloat = 0.70
        static let driveFollowBaseAnchorYFraction: CGFloat = 0.68
        static let driveFollowWideAnchorYFraction: CGFloat = 0.62
        static let driveFollowBottomSafetyMargin: CGFloat = 96
        static let passiveSpeedTailDuration: TimeInterval = 2.25
        static let passiveSpeedTailMinimumSpeedMph = 15.0
        static let passiveSpeedTailMaximumDistanceMeters = 80.0
        static let passiveSpeedTailMinimumDistanceMeters = 4.0
        static let controlsHorizontalPadding: CGFloat = 13
        static let controlsBottomPadding: CGFloat = 20
        static let controlsStackSpacing: CGFloat = 7
        static let standardButtonSize: CGFloat = 39
        static let driveButtonSize: CGFloat = 45
        static let standardButtonFont: Font = .system(size: 13, weight: .bold)
        static let driveButtonFont: Font = .system(size: 16, weight: .bold)
    }

    @ObservedObject var appState: AppState
    @ObservedObject var locationService: LocationService
    @ObservedObject var raceTracksDatasetStore: RaceTracksDatasetStore
    @ObservedObject var controller: CarPlayMapController

    @State private var viewport: Viewport
    @State private var followsUser = true
    @State private var currentLatitudeDelta = 0.02
    @State private var mapboxMap: MapboxMap?
    @State private var mapViewportLayoutSize: CGSize = .zero
    @State private var isDriveCameraPitchEngaged = false
    @State private var driveCameraTargetCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraRenderedCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraTargetBearing: CGFloat = 0
    @State private var driveCameraRenderedBearing: CGFloat = 0
    @State private var driveCameraPreviousSample: CLLocation?
    @State private var passiveSpeedTailSamples: [DrivePathSample] = []
    @State private var targetPresenceCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var renderedPresenceCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var lastMapHazardRefreshAt: Date = .distantPast

    init(
        appState: AppState,
        locationService: LocationService,
        raceTracksDatasetStore: RaceTracksDatasetStore,
        controller: CarPlayMapController
    ) {
        self.appState = appState
        self.locationService = locationService
        self.raceTracksDatasetStore = raceTracksDatasetStore
        self.controller = controller
        let center = locationService.latestSample?.coordinate
            ?? locationService.lastLocation?.coordinate
            ?? CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
        _viewport = State(
            initialValue: OttoMapboxCamera.viewport(
                for: MKCoordinateRegion(
                    center: center,
                    span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
                )
            )
        )
    }

    var body: some View {
        ZStack {
            OttoMapboxMapView(
                viewport: $viewport,
                allowsInteraction: true,
                onCameraChanged: { region in
                    currentLatitudeDelta = region.span.latitudeDelta
                },
                onUserGesture: {
                    followsUser = false
                },
                onMapLoaded: {
                    if let mapboxMap {
                        MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: controller.showTrafficLayer)
                    }
                    recenterOnUserIfAvailable(force: true)
                },
                onMapboxMapReady: { map in
                    mapboxMap = map
                    MapboxTrafficLayerController.sync(map: map, showTraffic: controller.showTrafficLayer)
                }
            ) {
                mapContent
            }
            .ignoresSafeArea()
            .background(Color.black)
            .overlay {
                Rectangle()
                    .fill(.black.opacity(0.12))
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .onAppear { updateMapViewportLayoutSize(proxy.size) }
                        .onChange(of: proxy.size) { _, size in
                            updateMapViewportLayoutSize(size)
                        }
                }
            }

        }
        .onAppear {
            OttoCarPlayAppBridge.shared.markCarPlayMapActive(true)
            syncLayerPreferencesFromPhone()
            syncPresenceSmoothingTargets()
            syncDriveCameraMode(forceFollowOnDriveStart: true)
            Task { await refreshMapHazardsIfNeeded(force: true) }
        }
        .onDisappear {
            OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
        }
        .onChange(of: locationService.mapLocationDisplayTick) { _, _ in
            syncPresenceSmoothingTargets()
            updatePassiveSpeedTail()
            syncDriveCameraMode(forceFollowOnDriveStart: false)
        }
        .onReceive(CarPlayMapSmoothingTimer.tick) { _ in
            stepPresenceSmoothing()
            stepDriveCameraSmoothing()
            prunePassiveSpeedTail()
            Task { await refreshMapHazardsIfNeeded() }
        }
        .onChange(of: controller.recenterGeneration) { _, _ in
            followsUser = true
            recenterOnUserIfAvailable(force: true)
        }
        .onChange(of: controller.focusRequest) { _, request in
            guard let request else { return }
            followsUser = false
            disengageDriveCameraPitch()
            viewport = OttoMapboxCamera.viewport(
                for: MKCoordinateRegion(
                    center: request.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
                )
            )
        }
        .onChange(of: controller.showTrafficLayer) { _, showTraffic in
            guard let mapboxMap else { return }
            MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: showTraffic)
        }
        .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
            syncLayerPreferencesFromPhone()
        }
        .onChange(of: appState.hasActiveDriveSession) { _, isActive in
            if isActive {
                followsUser = true
                passiveSpeedTailSamples = []
            }
            syncDriveCameraMode(forceFollowOnDriveStart: isActive)
        }
    }

    @MapboxMaps.MapContentBuilder
    private var mapContent: some MapboxMaps.MapContent {
        if let samples = activeLiveDriveTrailSamples {
            RouteSpeedGradientMapContent(
                sourceID: "carplay-live-drive-trail",
                samples: samples
            )
        }

        if let samples = passiveSpeedTailRenderSamples {
            CarPlayPassiveSpeedTailMapContent(
                sourceID: "carplay-passive-speed-tail",
                samples: samples
            )
        }

        RouteMapLineMapContent(
            sourceID: "carplay-active-route-line",
            coordinates: activeRouteLineCoordinates,
            palette: .livePurple
        )

        ForEvery(activeRouteMapPoints) { point in
            MapViewAnnotation(coordinate: point.coordinate) {
                RouteMapMarkerLODView(
                    markerType: point.markerType,
                    isCompleted: point.isCompleted(in: completedRouteCheckpointIndexes),
                    latitudeDelta: markerLODLatitudeDelta,
                    horizonScale: carPlayMarkerScale(routePointHorizonScale(for: point.coordinate))
                )
                .id(
                    RouteMapMarkerLOD.annotationRefreshID(
                        pointID: point.id,
                        markerType: point.markerType,
                        latitudeDelta: markerLODLatitudeDelta,
                        stableForRouteDrive: appState.activeRouteDriveSession != nil
                    )
                )
                .accessibilityLabel(RouteMapMarkerAsset.accessibilityLabel(markerType: point.markerType))
            }
            .allowOverlap(true)
            .ignoreCameraPadding(true)
            .priority(routeMarkerPriority(for: point))
        }

        if !presenceGroups.isEmpty {
            ForEvery(presenceGroups) { group in
                MapViewAnnotation(coordinate: group.coordinate) {
                    MapPresenceBouncyMarkerContainer {
                        if group.members.count == 1, let friend = group.members.first {
                            let isCurrentUser = isSelfPresenceFriend(friend)
                            MapPresenceFriendAnnotationView(
                                friend: friend,
                                isCurrentUser: isCurrentUser,
                                brandLogoURL: presenceBrandLogoURL(for: friend),
                                dwellText: nil,
                                travelSurface: .land,
                                horizonScale: carPlayMarkerScale(
                                    presenceHorizonScale(
                                        for: group.coordinate,
                                        isCurrentUser: isCurrentUser
                                    )
                                )
                            )
                            .environmentObject(appState)
                        } else {
                            MapPresenceCompositeFriendAnnotationView(
                                members: group.members,
                                currentUserID: appState.currentUserID,
                                dwellText: nil,
                                horizonScale: carPlayMarkerScale(
                                    presenceHorizonScale(
                                        for: group.coordinate,
                                        isCurrentUser: false
                                    )
                                )
                            )
                            .environmentObject(appState)
                        }
                    }
                }
                .allowOverlap(true)
                .ignoreCameraPadding(true)
                .allowOverlapWithPuck(appState.hasActiveDriveSession)
                .priority(presenceOverlapPriority(for: group.coordinate, tieBreaker: group.id.hashValue))
            }
        }

        if controller.showSavedPlacesLayer {
            ForEvery(appState.savedPlaces) { place in
                MapViewAnnotation(
                    coordinate: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
                ) {
                    MapDiscoveryMarkerLODView(
                        kind: .savedPlace,
                        latitudeDelta: markerLODLatitudeDelta
                    ) { pinScale in
                        OttoMapSavedPlaceMarker(isSelected: false, pinScale: carPlayMarkerScale(pinScale))
                    }
                    .id(
                        MapDiscoveryMarkerLOD.annotationRefreshID(
                            id: place.id,
                            kind: .savedPlace,
                            latitudeDelta: markerLODLatitudeDelta
                        )
                    )
                    .accessibilityLabel(place.name)
                }
                .allowOverlap(true)
                .priority(
                    RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(
                        for: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)
                    )
                )
            }
        }

        ForEvery(appState.activeMapHazards) { hazard in
            MapViewAnnotation(coordinate: hazard.coordinate) {
                carPlayHazardMarker(type: hazard.type)
                .id(
                    OttoMapHazardMarkerLODView.annotationRefreshID(
                        id: hazard.id,
                        latitudeDelta: markerLODLatitudeDelta
                    )
                )
                .accessibilityLabel(hazard.type.alertTitle)
            }
            .allowOverlap(true)
            .allowOverlapWithPuck(false)
            .priority(
                RouteMapGeometry.mapHazardMarkerOverlapPriority(
                    for: hazard.coordinate,
                    tieBreaker: hazard.type.mapMarkerPriorityTieBreaker
                )
            )
        }

        if controller.showUpcomingEventsLayer {
            ForEvery(anchoredUpcomingEventGroups) { group in
                MapViewAnnotation(coordinate: group.coordinate) {
                    MapDiscoveryMarkerLODView(
                        kind: .event,
                        latitudeDelta: markerLODLatitudeDelta,
                        clusterCount: group.events.count > 1 ? group.events.count : nil
                    ) { pinScale in
                        OttoMapEventMarker(
                            isSelected: false,
                            clusterCount: group.events.count > 1 ? group.events.count : nil,
                            isUserGoing: group.events.contains { $0.currentUserRsvp == "going" },
                            pinScale: carPlayMarkerScale(pinScale)
                        )
                    }
                    .id(
                        MapDiscoveryMarkerLOD.annotationRefreshID(
                            id: group.id,
                            kind: .event,
                            latitudeDelta: markerLODLatitudeDelta
                        )
                    )
                    .accessibilityLabel(group.events.first?.name ?? "Upcoming event")
                }
                .allowOverlap(true)
                .priority(RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(for: group.coordinate))
            }
        }

        if controller.showRaceTracksLayer {
            ForEvery(plottableRaceTracks) { track in
                if let coordinate = track.coordinate {
                    MapViewAnnotation(coordinate: coordinate) {
                        MapDiscoveryMarkerLODView(
                            kind: .raceTrack,
                            latitudeDelta: markerLODLatitudeDelta
                        ) { pinScale in
                            OttoMapRaceTrackMarker(
                                isSelected: false,
                                pinScale: carPlayMarkerScale(pinScale)
                            )
                        }
                        .id(
                            MapDiscoveryMarkerLOD.annotationRefreshID(
                                id: track.id,
                                kind: .raceTrack,
                                latitudeDelta: markerLODLatitudeDelta
                            )
                        )
                        .accessibilityLabel(track.name)
                    }
                    .allowOverlap(true)
                    .priority(RouteMapGeometry.mapDiscoveryMarkerOverlapPriority(for: coordinate))
                }
            }
        }
    }

    private func carPlayHazardMarker(type: MapHazardType) -> some View {
        OttoMapHazardMarkerLODView(
            type: type,
            latitudeDelta: markerLODLatitudeDelta,
            scaleMultiplier: Metrics.scale
        )
    }

    private func refreshMapHazardsIfNeeded(force: Bool = false) async {
        appState.pruneExpiredMapHazards()
        let now = Date()
        guard force || now.timeIntervalSince(lastMapHazardRefreshAt) > 60 else { return }
        lastMapHazardRefreshAt = now
        let coordinate = userCoordinate ?? CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return }
        await appState.refreshMapHazards(near: coordinate)
    }

    private var userCoordinate: CLLocationCoordinate2D? {
        _ = locationService.mapLocationDisplayTick
        return (locationService.latestSample ?? locationService.lastLocation)?.coordinate
    }

    private var markerLODLatitudeDelta: Double {
        currentLatitudeDelta
    }

    private func carPlayMarkerScale(_ scale: CGFloat) -> CGFloat {
        scale * Metrics.scale
    }

    private func updateMapViewportLayoutSize(_ size: CGSize) {
        mapViewportLayoutSize = size
        refreshDriveCameraPaddingForCurrentSize()
    }

    private func refreshDriveCameraPaddingForCurrentSize() {
        guard appState.hasActiveDriveSession,
              isDriveCameraPitchEngaged,
              followsUser,
              let center = driveCameraRenderedCoordinate ?? driveCameraTargetCoordinate else {
            return
        }
        applyDriveCameraViewport(
            center: center,
            bearing: driveCameraRenderedBearing,
            animated: false
        )
    }

    private func carPlayDriveFollowEdgeInsets(mapSize: CGSize) -> SwiftUI.EdgeInsets {
        let width = max(mapSize.width, 480)
        let height = max(mapSize.height, 320)
        let aspectRatio = width / height
        let wideProgress = min(1, max(0, (aspectRatio - 1.6) / 0.9))
        let desiredAnchor = Metrics.driveFollowBaseAnchorYFraction
            - ((Metrics.driveFollowBaseAnchorYFraction - Metrics.driveFollowWideAnchorYFraction) * wideProgress)
        let bottomSafeAnchor = 1 - (Metrics.driveFollowBottomSafetyMargin / height)
        let anchor = min(desiredAnchor, max(0.55, bottomSafeAnchor))
        let topPadding = height * max(0, 2 * anchor - 1)
        return EdgeInsets(top: topPadding, leading: 0, bottom: 0, trailing: 0)
    }

    private func updatePassiveSpeedTail() {
        guard !appState.hasActiveDriveSession else {
            passiveSpeedTailSamples = []
            return
        }
        guard let location = locationService.latestSample ?? locationService.lastLocation,
              CLLocationCoordinate2DIsValid(location.coordinate),
              Date().timeIntervalSince(location.timestamp) <= Metrics.passiveSpeedTailDuration else {
            passiveSpeedTailSamples = []
            return
        }

        let speedMph = max(0, locationService.effectiveSpeedMetersPerSecond(staleAfter: 3) * 2.23694)
        guard speedMph >= Metrics.passiveSpeedTailMinimumSpeedMph else {
            passiveSpeedTailSamples = []
            return
        }

        let sample = DrivePathSample(location: location, speedMph: speedMph)
        if let last = passiveSpeedTailSamples.last {
            if last.capturedAt == sample.capturedAt {
                prunePassiveSpeedTail(now: Date())
                return
            }
            let distance = DriveSpeedGradient.coordinateDistanceMeters(last.coordinate, sample.coordinate)
            if distance < 0.75 {
                prunePassiveSpeedTail(now: Date())
                return
            }
        }

        passiveSpeedTailSamples.append(sample)
        prunePassiveSpeedTail(now: Date())
    }

    private func prunePassiveSpeedTail(now: Date = Date()) {
        guard !passiveSpeedTailSamples.isEmpty else { return }
        guard !appState.hasActiveDriveSession else {
            passiveSpeedTailSamples = []
            return
        }

        let cutoff = now.addingTimeInterval(-Metrics.passiveSpeedTailDuration)
        passiveSpeedTailSamples = passiveSpeedTailSamples.filter { sample in
            guard let capturedAt = sample.capturedAt else { return false }
            return capturedAt >= cutoff
        }

        while passiveSpeedTailDistanceMeters(passiveSpeedTailSamples) > Metrics.passiveSpeedTailMaximumDistanceMeters,
              passiveSpeedTailSamples.count > 2 {
            passiveSpeedTailSamples.removeFirst()
        }
    }

    private var passiveSpeedTailRenderSamples: [DrivePathSample]? {
        guard !appState.hasActiveDriveSession,
              passiveSpeedTailSamples.count >= 2,
              passiveSpeedTailDistanceMeters(passiveSpeedTailSamples) >= Metrics.passiveSpeedTailMinimumDistanceMeters,
              let latest = passiveSpeedTailSamples.last,
              latest.speedMph >= Metrics.passiveSpeedTailMinimumSpeedMph else {
            return nil
        }
        return passiveSpeedTailSamples
    }

    private func passiveSpeedTailDistanceMeters(_ samples: [DrivePathSample]) -> Double {
        guard samples.count >= 2 else { return 0 }
        var distance = 0.0
        for index in 1..<samples.count {
            distance += DriveSpeedGradient.coordinateDistanceMeters(
                samples[index - 1].coordinate,
                samples[index].coordinate
            )
        }
        return distance
    }

    private var anchoredUpcomingEvents: [CarPlayAnchoredUpcomingEvent] {
        appState.mapDiscoveryEvents
            .filter { $0.isEligibleForMapDisplay() }
            .compactMap { event in
                guard let coordinate = event.eventGeoCoordinate else { return nil }
                return CarPlayAnchoredUpcomingEvent(id: event.id, event: event, coordinate: coordinate)
            }
    }

    private let anchoredEventClusterDistanceMeters: CLLocationDistance = 78

    private var anchoredUpcomingEventGroups: [CarPlayAnchoredUpcomingEventGroup] {
        let sortedAnchored = anchoredUpcomingEvents.sorted { lhs, rhs in
            if lhs.coordinate.latitude != rhs.coordinate.latitude {
                return lhs.coordinate.latitude < rhs.coordinate.latitude
            }
            return lhs.coordinate.longitude < rhs.coordinate.longitude
        }
        var groups: [[CarPlayAnchoredUpcomingEvent]] = []
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
            return CarPlayAnchoredUpcomingEventGroup(id: idSignature, coordinate: c, events: evs)
        }
    }

    private var plottableRaceTracks: [RaceTrackRecord] {
        raceTracksDatasetStore.tracks.compactMap { track in
            track.coordinate == nil ? nil : track
        }
    }

    private func centroid(for coordinates: [CLLocationCoordinate2D]) -> CLLocationCoordinate2D {
        guard !coordinates.isEmpty else {
            return CLLocationCoordinate2D(latitude: 0, longitude: 0)
        }
        let lat = coordinates.reduce(0) { $0 + $1.latitude } / Double(coordinates.count)
        let lng = coordinates.reduce(0) { $0 + $1.longitude } / Double(coordinates.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    private var driveHorizonUserLocation: CLLocation? {
        guard appState.hasActiveDriveSession else { return nil }
        _ = locationService.mapLocationDisplayTick
        return locationService.latestSample ?? locationService.lastLocation
    }

    private var driveVisibleMapHeightMeters: Double {
        MapDriveHorizonDepth.visibleMapHeightMeters(latitudeDelta: currentLatitudeDelta)
    }

    private var activePresenceMembers: [FriendLocation] {
        var byID: [String: FriendLocation] = [:]
        if let selfMember {
            byID[selfMember.id] = selfMember
        }
        if controller.showPublicPresenceLayer {
            for member in appState.publicPresenceMembers where member.isActive {
                byID[member.id] = member
            }
        }
        for member in appState.circles
            .filter({ controller.visibleCircleLayerIDs.contains($0.id) })
            .flatMap(\.members) where member.isActive {
            byID[member.id] = member
        }
        return byID.values.sorted { lhs, rhs in
            if isSelfPresenceFriend(lhs) != isSelfPresenceFriend(rhs) {
                return isSelfPresenceFriend(lhs)
            }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    private var activePeerPresenceMembers: [FriendLocation] {
        activePresenceMembers.filter { !isSelfPresenceFriend($0) }
    }

    private func syncLayerPreferencesFromPhone() {
        let defaultCircleIDs = Set(appState.circles.map(\.id).filter { !$0.isEmpty })
        controller.syncLayerPreferences(defaultCircleIDs: defaultCircleIDs)
        if let mapboxMap {
            MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: controller.showTrafficLayer)
        }
    }

    private var displayedPresenceMembers: [FriendLocation] {
        activePresenceMembers.map { member in
            guard let rendered = renderedPresenceCoordinates[member.id] else { return member }
            return FriendLocation(
                id: member.id,
                name: member.name,
                avatarName: member.avatarName,
                avatarUrl: member.avatarUrl,
                car: member.car,
                clubRole: member.clubRole,
                lastRun: member.lastRun,
                coordinate: rendered,
                speedMph: member.speedMph,
                isOnline: member.isOnline,
                isActive: member.isActive,
                accentColor: member.accentColor,
                movementMode: member.movementMode,
                lastUpdatedAt: member.lastUpdatedAt,
                lastPresenceInApp: member.lastPresenceInApp,
                brandLogoSlug: member.brandLogoSlug
            )
        }
    }

    private var selfMember: FriendLocation? {
        guard let coordinate = userCoordinate else { return nil }
        let profile = appState.currentUser ?? appState.allUsers.first(where: { $0.id == appState.currentUserID })
        let userID = appState.currentUserID.isEmpty ? "me" : appState.currentUserID
        let name = profile?.displayName ?? "You"
        let speedMph = Int((locationService.effectiveSpeedMetersPerSecond() * 2.23694).rounded())
        return FriendLocation(
            id: userID,
            name: name,
            avatarName: name,
            avatarUrl: profile?.avatarUrl,
            car: appState.selectedSharingCar?.displayName ?? "Your car",
            clubRole: "You",
            lastRun: "Now",
            coordinate: coordinate,
            speedMph: max(0, speedMph),
            isOnline: true,
            isActive: appState.hasActiveDriveSession || appState.isSharingEnabled,
            accentColor: MapAccentPalette.resolvedColor(mapAccentKey: profile?.mapAccentKey, userId: userID),
            movementMode: locationService.movementMode,
            lastUpdatedAt: Date(),
            lastPresenceInApp: true,
            brandLogoSlug: nil
        )
    }

    private var presenceGroups: [CarPlayPresenceGroup] {
        var groups: [CarPlayPresenceGroup] = []
        let clusteringDistanceMeters: CLLocationDistance = 28
        for member in displayedPresenceMembers {
            if let index = groups.firstIndex(where: {
                MapDriveHorizonDepth.distanceMeters(
                    from: CLLocation(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude),
                    to: member.coordinate
                ) <= clusteringDistanceMeters
            }) {
                var members = groups[index].members
                members.append(member)
                let coordinate = averageCoordinate(for: members)
                groups[index] = CarPlayPresenceGroup(members: members, coordinate: coordinate)
            } else {
                groups.append(CarPlayPresenceGroup(members: [member], coordinate: member.coordinate))
            }
        }
        return groups.filter { shouldShowPresenceGroup($0) }
    }

    private func syncPresenceSmoothingTargets() {
        let members = activePresenceMembers
        let currentIDs = Set(members.map(\.id))
        targetPresenceCoordinates = Dictionary(uniqueKeysWithValues: members.map { ($0.id, $0.coordinate) })
        renderedPresenceCoordinates = renderedPresenceCoordinates.filter { currentIDs.contains($0.key) }
        for member in members where renderedPresenceCoordinates[member.id] == nil {
            renderedPresenceCoordinates[member.id] = member.coordinate
        }
    }

    private func stepPresenceSmoothing() {
        guard !targetPresenceCoordinates.isEmpty else { return }
        var next = renderedPresenceCoordinates
        for (id, target) in targetPresenceCoordinates {
            guard let current = next[id] else {
                next[id] = target
                continue
            }
            let distance = CLLocation(latitude: current.latitude, longitude: current.longitude)
                .distance(from: CLLocation(latitude: target.latitude, longitude: target.longitude))
            if distance < 0.25 {
                next[id] = target
            } else {
                next[id] = CLLocationCoordinate2D(
                    latitude: interpolate(current.latitude, target.latitude, factor: 0.30),
                    longitude: interpolate(current.longitude, target.longitude, factor: 0.30)
                )
            }
        }
        renderedPresenceCoordinates = next
    }

    private func averageCoordinate(for members: [FriendLocation]) -> CLLocationCoordinate2D {
        guard !members.isEmpty else {
            return CLLocationCoordinate2D(latitude: 0, longitude: 0)
        }
        let lat = members.reduce(0) { $0 + $1.coordinate.latitude } / Double(members.count)
        let lng = members.reduce(0) { $0 + $1.coordinate.longitude } / Double(members.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    private func shouldShowPresenceGroup(_ group: CarPlayPresenceGroup) -> Bool {
        guard appState.hasActiveDriveSession else { return true }
        if group.members.contains(where: isSelfPresenceFriend) { return true }
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
        if isSelfPresenceFriend(friend) {
            return appState.mapSelfBrandLogoURL
        }
        return appState.peerBrandLogoURL(for: friend)
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

    private var activeRouteLineCoordinates: [CLLocationCoordinate2D] {
        if let navigationLine = appState.navigationLineCoordinates, navigationLine.count >= 2 {
            return navigationLine
        }
        guard let route = appState.activeRouteDriveRoute else { return [] }
        let road = coordinates(from: route.roadCoordinates)
        if road.count >= 2 { return road }
        return coordinates(from: route.points)
    }

    private var activeRouteMapPoints: [RouteMapPointModel] {
        guard let route = appState.activeRouteDriveRoute else { return [] }
        let indexed = route.points.enumerated().compactMap { index, point -> RouteMapPointModel? in
            guard point.markerType != "path",
                  let coordinate = coordinate(from: point) else {
                return nil
            }
            return RouteMapPointModel(
                id: "\(route.id)-\(index)-\(point.markerType ?? "point")",
                coordinate: coordinate,
                markerType: point.markerType,
                index: index
            )
        }
        return indexed.filter { shouldShowRouteMapPoint($0) }
    }

    private var completedRouteCheckpointIndexes: Set<Int> {
        Set(appState.activeRouteDriveSession?.completedWaypointIndexes ?? [])
    }

    private func syncDriveCameraMode(forceFollowOnDriveStart: Bool) {
        guard appState.hasActiveDriveSession else {
            if isDriveCameraPitchEngaged {
                disengageDriveCameraPitch()
                flattenCameraToTopDown()
            } else if followsUser {
                recenterOnUserIfAvailable(force: false)
            }
            return
        }

        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        if forceFollowOnDriveStart {
            followsUser = true
        }
        guard followsUser else { return }
        if isDriveCameraPitchEngaged {
            syncDriveCameraTarget(from: location)
        } else {
            enterDriveCameraMode(from: location)
        }
    }

    private func enterDriveCameraMode(from location: CLLocation? = nil) {
        let resolved = location ?? locationService.latestSample ?? locationService.lastLocation
        guard let resolved else {
            recenterOnUserIfAvailable(force: true)
            return
        }
        isDriveCameraPitchEngaged = true
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

    private func stepDriveCameraSmoothing() {
        guard appState.hasActiveDriveSession, isDriveCameraPitchEngaged, followsUser else { return }
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
        applyDriveCameraViewport(
            center: newCoordinate,
            bearing: newBearing,
            animated: false
        )
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

    private func applyDriveCameraViewport(
        center: CLLocationCoordinate2D,
        bearing: CGFloat,
        animated: Bool
    ) {
        viewport = OttoMapboxCamera.viewport(
            for: MKCoordinateRegion(center: center, span: OttoMapboxCamera.driveTrackingSpan),
            bearing: bearing,
            pitch: OttoMapboxCamera.drivePitchDegrees,
            followPadding: carPlayDriveFollowEdgeInsets(mapSize: mapViewportLayoutSize)
        )
    }

    private func flattenCameraToTopDown() {
        let center = (locationService.latestSample ?? locationService.lastLocation)?.coordinate
            ?? userCoordinate
            ?? CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
        viewport = OttoMapboxCamera.viewport(
            for: MKCoordinateRegion(
                center: center,
                span: MKCoordinateSpan(
                    latitudeDelta: max(currentLatitudeDelta, 0.000001),
                    longitudeDelta: max(currentLatitudeDelta, 0.000001)
                )
            ),
            bearing: 0,
            pitch: 0
        )
    }

    private func disengageDriveCameraPitch() {
        isDriveCameraPitchEngaged = false
        driveCameraTargetCoordinate = nil
        driveCameraRenderedCoordinate = nil
        driveCameraTargetBearing = 0
        driveCameraRenderedBearing = 0
        driveCameraPreviousSample = nil
    }

    private func recenterOnUserIfAvailable(force: Bool) {
        guard force || followsUser else { return }
        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        guard appState.hasActiveDriveSession else {
            viewport = OttoMapboxCamera.viewport(
                for: MKCoordinateRegion(
                    center: location.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
                )
            )
            return
        }
        followsUser = true
        if isDriveCameraPitchEngaged {
            recenterDriveCamera(from: location, animated: true)
        } else {
            enterDriveCameraMode(from: location)
        }
    }

    private func routeMarkerPriority(for point: RouteMapPointModel) -> Int {
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

    private func routePointHorizonScale(for coordinate: CLLocationCoordinate2D) -> CGFloat {
        guard let user = driveHorizonUserLocation else { return 1 }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: coordinate)
        return MapDriveHorizonDepth.horizonScale(
            distanceMeters: distance,
            visibleMapHeightMeters: driveVisibleMapHeightMeters
        )
    }

    private func shouldShowRouteMapPoint(_ point: RouteMapPointModel) -> Bool {
        guard let user = driveHorizonUserLocation else { return true }
        let distance = MapDriveHorizonDepth.distanceMeters(from: user, to: point.coordinate)
        return MapDriveHorizonDepth.shouldShowRouteMarker(
            markerType: point.markerType,
            distanceMeters: distance
        )
    }

    private func coordinates(from points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        points.compactMap(coordinate(from:))
    }

    private func coordinate(from point: RoutePointDTO) -> CLLocationCoordinate2D? {
        let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return coordinate
    }

    private func interpolate(_ current: Double, _ target: Double, factor: Double) -> Double {
        current + ((target - current) * factor)
    }
}

private struct CarPlayUserLocationMarker: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.blue.opacity(0.22))
                .frame(width: 34, height: 34)
            Circle()
                .fill(Color.blue)
                .frame(width: 17, height: 17)
            Circle()
                .stroke(Color.white, lineWidth: 3)
                .frame(width: 17, height: 17)
        }
        .shadow(color: .black.opacity(0.28), radius: 6, x: 0, y: 3)
        .accessibilityLabel("Current location")
    }
}
