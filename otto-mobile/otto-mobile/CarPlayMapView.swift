import CoreLocation
import Combine
import CarPlay
import MapboxMaps
import MapKit
import Network
import os
import SwiftUI
import UIKit

private final class CarPlayDisplayLinkTicker: NSObject, ObservableObject {
    var onFrame: (() -> Void)?
    private var displayLink: CADisplayLink?

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(handleFrame))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        onFrame = nil
    }

    @objc private func handleFrame() {
        onFrame?()
    }
}

private struct CarPlayLiveLocationFrame {
    let coordinate: CLLocationCoordinate2D
    let bearing: CGFloat
    let isAnimating: Bool
}

private struct CarPlayLiveLocationAnimationController {
    private static let minimumDuration: TimeInterval = 0.25
    private static let maximumDuration: TimeInterval = 2.0
    private static let poorAccuracyThreshold: CLLocationAccuracy = 100
    private static let suspiciousAccuracyThreshold: CLLocationAccuracy = 50
    private static let stationarySpeedThreshold: CLLocationSpeed = 0.75
    private static let stationaryJitterThresholdMeters: CLLocationDistance = 4

    private var previousSample: CLLocation?
    private var lastReceiveTime: Date?
    private var animationStartTime: Date?
    private var animationDuration: TimeInterval = minimumDuration
    private var animationStartCoordinate: CLLocationCoordinate2D?
    private var animationTargetCoordinate: CLLocationCoordinate2D?
    private var animationStartBearing: CGFloat = 0
    private var animationTargetBearing: CGFloat = 0
    private(set) var renderedCoordinate: CLLocationCoordinate2D?
    private(set) var renderedBearing: CGFloat = 0

    var targetCoordinate: CLLocationCoordinate2D? {
        animationTargetCoordinate
    }

    var targetBearing: CGFloat {
        animationTargetBearing
    }

    mutating func reset(to location: CLLocation, bearing: CGFloat, now: Date = Date()) {
        previousSample = location
        lastReceiveTime = now
        animationStartTime = nil
        animationDuration = Self.minimumDuration
        animationStartCoordinate = location.coordinate
        animationTargetCoordinate = location.coordinate
        animationStartBearing = bearing
        animationTargetBearing = bearing
        renderedCoordinate = location.coordinate
        renderedBearing = bearing
    }

    mutating func clear() {
        previousSample = nil
        lastReceiveTime = nil
        animationStartTime = nil
        animationStartCoordinate = nil
        animationTargetCoordinate = nil
        animationStartBearing = 0
        animationTargetBearing = 0
        renderedCoordinate = nil
        renderedBearing = 0
    }

    mutating func push(
        location: CLLocation,
        isDriveMode: Bool,
        now: Date = Date()
    ) {
        guard location.coordinate.latitude.isFinite,
              location.coordinate.longitude.isFinite else {
            return
        }
        if location.horizontalAccuracy > Self.poorAccuracyThreshold, previousSample != nil {
            return
        }
        if isSuspiciousJump(to: location) {
            return
        }

        let currentFrame = frame(at: now)
        guard let currentCoordinate = currentFrame?.coordinate ?? renderedCoordinate else {
            let initialBearing = resolvedBearing(for: location, isDriveMode: isDriveMode)
            reset(to: location, bearing: initialBearing, now: now)
            return
        }

        let targetBearing = isDriveMode
            ? resolvedBearing(for: location, isDriveMode: true)
            : 0
        let distanceToTarget = CLLocation(latitude: currentCoordinate.latitude, longitude: currentCoordinate.longitude)
            .distance(from: location)
        let speed = max(location.speed, 0)
        if speed < Self.stationarySpeedThreshold, distanceToTarget < Self.stationaryJitterThresholdMeters {
            previousSample = location
            lastReceiveTime = now
            animationStartTime = nil
            animationStartCoordinate = currentCoordinate
            animationTargetCoordinate = currentCoordinate
            animationStartBearing = renderedBearing
            animationTargetBearing = targetBearing
            renderedCoordinate = currentCoordinate
            renderedBearing = targetBearing
            return
        }

        let rawDuration = sampleInterval(for: location, now: now)
        animationStartTime = now
        animationDuration = min(Self.maximumDuration, max(Self.minimumDuration, rawDuration))
        animationStartCoordinate = currentCoordinate
        animationTargetCoordinate = location.coordinate
        animationStartBearing = currentFrame?.bearing ?? renderedBearing
        animationTargetBearing = targetBearing
        renderedCoordinate = currentCoordinate
        renderedBearing = currentFrame?.bearing ?? renderedBearing
        previousSample = location
        lastReceiveTime = now
    }

    mutating func frame(at now: Date = Date()) -> CarPlayLiveLocationFrame? {
        guard let target = animationTargetCoordinate else {
            return renderedCoordinate.map {
                CarPlayLiveLocationFrame(coordinate: $0, bearing: renderedBearing, isAnimating: false)
            }
        }
        guard let start = animationStartCoordinate,
              let startedAt = animationStartTime,
              animationDuration > 0 else {
            renderedCoordinate = target
            renderedBearing = animationTargetBearing
            return CarPlayLiveLocationFrame(coordinate: target, bearing: renderedBearing, isAnimating: false)
        }
        let progress = min(1, max(0, now.timeIntervalSince(startedAt) / animationDuration))
        let coordinate = CLLocationCoordinate2D(
            latitude: start.latitude + ((target.latitude - start.latitude) * progress),
            longitude: start.longitude + ((target.longitude - start.longitude) * progress)
        )
        let headingProgress = subtleEaseInOut(progress)
        let bearing = OttoMapboxCamera.interpolateBearing(
            from: animationStartBearing,
            to: animationTargetBearing,
            factor: headingProgress
        )
        renderedCoordinate = coordinate
        renderedBearing = bearing
        if progress >= 1 {
            animationStartTime = nil
            animationStartCoordinate = target
            animationStartBearing = animationTargetBearing
        }
        return CarPlayLiveLocationFrame(coordinate: coordinate, bearing: bearing, isAnimating: progress < 1)
    }

    private func resolvedBearing(for location: CLLocation, isDriveMode: Bool) -> CGFloat {
        guard isDriveMode else { return 0 }
        return OttoMapboxCamera.driveBearing(
            from: location,
            previous: previousSample,
            fallback: animationTargetBearing
        )
    }

    private func sampleInterval(for location: CLLocation, now: Date) -> TimeInterval {
        if let previousSample {
            let timestampDelta = location.timestamp.timeIntervalSince(previousSample.timestamp)
            if timestampDelta.isFinite, timestampDelta > 0.05, timestampDelta < 10 {
                return timestampDelta
            }
        }
        if let lastReceiveTime {
            let receiveDelta = now.timeIntervalSince(lastReceiveTime)
            if receiveDelta.isFinite, receiveDelta > 0.05 {
                return receiveDelta
            }
        }
        return 1.0
    }

    private func isSuspiciousJump(to location: CLLocation) -> Bool {
        guard let previousSample else { return false }
        let distance = location.distance(from: previousSample)
        let interval = max(0.25, sampleInterval(for: location, now: Date()))
        let speed = max(max(location.speed, 0), max(previousSample.speed, 0))
        let plausibleDistance = max(200, speed * interval * 4 + 100)
        return location.horizontalAccuracy > Self.suspiciousAccuracyThreshold &&
            distance > plausibleDistance
    }
}

private func subtleEaseInOut(_ progress: TimeInterval) -> CGFloat {
    let t = min(1, max(0, progress))
    return CGFloat(t * t * (3 - 2 * t))
}

struct CarPlayHostSurfaceState: Equatable {
    var windowBounds: CGRect = .zero
    var viewBounds: CGRect = .zero
    var windowAttached = false
    var generation = 0
    var reason = "initial"

    var isReady: Bool {
        windowAttached &&
            windowBounds.width > 0 &&
            windowBounds.height > 0 &&
            viewBounds.width > 0 &&
            viewBounds.height > 0
    }

    var logSummary: String {
        "ready=\(isReady) attached=\(windowAttached) window=\(windowBounds) view=\(viewBounds) generation=\(generation) reason=\(reason)"
    }
}

@MainActor
final class CarPlayMapController: NSObject, ObservableObject, CPSearchTemplateDelegate {
    private static let nativeGuidanceBackgroundColor = UIColor(white: 0.08, alpha: 0.95)

    weak var interfaceController: CPInterfaceController?
    weak var mapTemplate: CPMapTemplate?
    var fullMapHostReloadHandler: ((String) -> Void)?
    private(set) var isFullMapHostReloadInProgress = false
    private var currentMapHostInstanceID: String?
    private weak var appState: AppState?
    private weak var locationService: LocationService?
    private var fullMapHostReloadAttempts = 0
    private var destinationSearchTask: Task<Void, Never>?
    private var destinationResultByItemID: [ObjectIdentifier: NavigationSearchResultDTO] = [:]
    private var latestDestinationSearchQuery = ""
    private var latestDestinationSearchResults: [NavigationSearchResultDTO] = []
    private var navigationSession: CPNavigationSession?
    private var navigationSessionRouteID: String?
    private var lastManeuverSignature: String?

    @Published private(set) var recenterGeneration = 0
    @Published private(set) var focusRequest: CarPlayMapFocusRequest?
    @Published private(set) var zoomRequest: CarPlayMapZoomRequest?
    @Published private(set) var showPublicPresenceLayer = false
    @Published private(set) var showTrafficLayer = true
    @Published private(set) var showUpcomingEventsLayer = true
    @Published private(set) var showRaceTracksLayer = true
    @Published private(set) var showSavedPlacesLayer = true
    @Published private(set) var visibleCircleLayerIDs: Set<String> = []
    @Published private(set) var projectedDestinationRoute: SavedRouteDTO?
    @Published private(set) var hostSurfaceState = CarPlayHostSurfaceState()

    func requestRecenter() {
        recenterGeneration &+= 1
    }

    func requestZoom(delta: Int) {
        zoomRequest = CarPlayMapZoomRequest(delta: delta)
    }

    func updateHostSurface(
        windowBounds: CGRect,
        viewBounds: CGRect,
        windowAttached: Bool,
        reason: String
    ) {
        let geometryChanged = hostSurfaceState.windowBounds != windowBounds ||
            hostSurfaceState.viewBounds != viewBounds ||
            hostSurfaceState.windowAttached != windowAttached
        let isNewHostInstall = reason == "did-connect" || (reason.hasPrefix("reload-") && !reason.hasSuffix("-layout"))
        hostSurfaceState = CarPlayHostSurfaceState(
            windowBounds: windowBounds,
            viewBounds: viewBounds,
            windowAttached: windowAttached,
            generation: geometryChanged || isNewHostInstall ? hostSurfaceState.generation + 1 : hostSurfaceState.generation,
            reason: reason
        )
        print("[CarPlayMap] Host surface \(hostSurfaceState.logSummary)")
    }

    @discardableResult
    func requestFullMapHostReload(reason: String) -> Bool {
        guard let fullMapHostReloadHandler else {
            print("[CarPlayMap] Full map host reload skipped missing handler reason=\(reason)")
            return false
        }
        let ignoresReloadCap = reason == "canonical-app-state"
        guard ignoresReloadCap || fullMapHostReloadAttempts < 2 else {
            print("[CarPlayMap] Full map host reload skipped after cap reason=\(reason)")
            return false
        }
        if !ignoresReloadCap {
            fullMapHostReloadAttempts += 1
        }
        isFullMapHostReloadInProgress = true
        print("[CarPlayMap] Requesting full map host reload reason=\(reason)")
        fullMapHostReloadHandler(reason)
        return true
    }

    func completeFullMapHostReload() {
        isFullMapHostReloadInProgress = false
    }

    func noteMapHostRenderedSuccessfully() {
        fullMapHostReloadAttempts = 0
        isFullMapHostReloadInProgress = false
    }

    func noteMapHostAppeared(id: String) {
        currentMapHostInstanceID = id
    }

    func isCurrentMapHost(id: String) -> Bool {
        currentMapHostInstanceID == id
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

    func popToRootTemplate(completion: (() -> Void)? = nil) {
        DispatchQueue.main.async { [weak self] in
            guard let interfaceController = self?.interfaceController else {
                completion?()
                return
            }
            interfaceController.popToRootTemplate(animated: true) { _, _ in
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

    func replaceTopTemplate(with template: CPTemplate) {
        DispatchQueue.main.async { [weak self] in
            guard let interfaceController = self?.interfaceController else { return }
            interfaceController.popTemplate(animated: true) { _, _ in
                interfaceController.pushTemplate(template, animated: true, completion: nil)
            }
        }
    }

    func presentDestinationLookupTemplate() {
        destinationSearchTask?.cancel()
        destinationResultByItemID.removeAll()
        latestDestinationSearchQuery = ""
        latestDestinationSearchResults = []
        let template = CPSearchTemplate()
        template.delegate = self
        pushTemplate(template)
    }

    nonisolated func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        updatedSearchText searchText: String,
        completionHandler: @escaping ([CPListItem]) -> Void
    ) {
        Task { @MainActor in
            destinationSearchTask?.cancel()
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            latestDestinationSearchQuery = query
            if query.isEmpty {
                latestDestinationSearchResults = NavigationDestinationRecentsStore.load().map(\.navigationSearchResult)
                completionHandler(recentDestinationItems())
                return
            }
            destinationSearchTask = Task { @MainActor in
                do {
                    let focus = carPlayLocationFocus()
                    let results = try await APIClient.shared.navigationSearch(
                        query: query,
                        latitude: focus?.latitude,
                        longitude: focus?.longitude,
                        limit: 3
                    )
                    guard !Task.isCancelled else { return }
                    latestDestinationSearchResults = results
                    completionHandler(searchResultItems(results, emptyQuery: query))
                } catch {
                    guard !Task.isCancelled else { return }
                    latestDestinationSearchResults = []
                    completionHandler([
                        CPListItem(
                            text: "Search unavailable",
                            detailText: "Check your connection and try again."
                        )
                    ])
                }
            }
        }
    }

    nonisolated func searchTemplate(
        _ searchTemplate: CPSearchTemplate,
        selectedResult item: CPListItem,
        completionHandler: @escaping () -> Void
    ) {
        Task { @MainActor in
            completionHandler()
            guard let result = destinationResultByItemID[ObjectIdentifier(item)] else { return }
            await selectCarPlayDestination(result)
        }
    }

    nonisolated func searchTemplateSearchButtonPressed(_ searchTemplate: CPSearchTemplate) {
        Task { @MainActor in
            await presentSubmittedDestinationResults()
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

    private func carPlayLocationFocus() -> CLLocationCoordinate2D? {
        guard let locationService else { return nil }
        let location = locationService.latestSample ?? locationService.lastLocation
        guard let coordinate = location?.coordinate, CLLocationCoordinate2DIsValid(coordinate) else {
            return nil
        }
        return coordinate
    }

    private func recentDestinationItems() -> [CPListItem] {
        let recents = NavigationDestinationRecentsStore.load()
        guard !recents.isEmpty else {
            return [
                CPListItem(
                    text: "No recent destinations",
                    detailText: "Search for a destination to add one."
                )
            ]
        }
        return searchResultItems(
            recents.map { $0.navigationSearchResult },
            emptyQuery: ""
        )
    }

    private func presentSubmittedDestinationResults() async {
        destinationSearchTask?.cancel()
        let query = latestDestinationSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            pushDestinationResultsTemplate(
                title: "Recent",
                results: NavigationDestinationRecentsStore.load().map(\.navigationSearchResult),
                emptyItem: CPListItem(text: "No recent destinations", detailText: "Search for a destination to add one.")
            )
            return
        }

        let results: [NavigationSearchResultDTO]
        if !latestDestinationSearchResults.isEmpty {
            results = latestDestinationSearchResults
        } else {
            do {
                let focus = carPlayLocationFocus()
                results = try await APIClient.shared.navigationSearch(
                    query: query,
                    latitude: focus?.latitude,
                    longitude: focus?.longitude,
                    limit: 6
                )
                latestDestinationSearchResults = results
            } catch {
                pushDestinationResultsTemplate(
                    title: "Results",
                    results: [],
                    emptyItem: CPListItem(text: "Search unavailable", detailText: "Check your connection and try again.")
                )
                return
            }
        }

        pushDestinationResultsTemplate(
            title: query,
            results: results,
            emptyItem: CPListItem(text: "Couldn’t find \(query)", detailText: "Try another search.")
        )
    }

    private func pushDestinationResultsTemplate(
        title: String,
        results: [NavigationSearchResultDTO],
        emptyItem: CPListItem
    ) {
        destinationResultByItemID.removeAll()
        let items =
            results.isEmpty
                ? [emptyItem]
                : results.map { destinationListItem(for: $0, usesHandler: true) }
        let section = CPListSection(items: items)
        let template = CPListTemplate(title: title, sections: [section])
        pushTemplate(template)
    }

    private func searchResultItems(
        _ results: [NavigationSearchResultDTO],
        emptyQuery query: String
    ) -> [CPListItem] {
        destinationResultByItemID.removeAll()
        guard !results.isEmpty else {
            return [
                CPListItem(
                    text: "Couldn’t find \(query)",
                    detailText: "Try another search."
                )
            ]
        }
        return results.map { destinationListItem(for: $0, usesHandler: false) }
    }

    private func destinationListItem(
        for result: NavigationSearchResultDTO,
        usesHandler: Bool
    ) -> CPListItem {
        let detail = result.address?.isEmpty == false ? result.address : "Select destination"
        let item = CPListItem(text: result.name, detailText: detail)
        item.accessoryType = .disclosureIndicator
        destinationResultByItemID[ObjectIdentifier(item)] = result
        if usesHandler {
            item.handler = { [weak self] _, completion in
                Task { @MainActor in
                    completion()
                    await self?.selectCarPlayDestination(result)
                }
            }
        }
        return item
    }

    private func selectCarPlayDestination(_ destination: NavigationSearchResultDTO) async {
        let coordinate = destination.coordinate
        guard CLLocationCoordinate2DIsValid(coordinate) else { return }
        NavigationDestinationRecentsStore.save(destination)
        guard let appState else {
            popToRootTemplate { [weak self] in
                self?.presentCarPlayAlert(title: "Driftd unavailable")
            }
            return
        }
        guard !appState.hasActiveDriveSession else {
            popToRootTemplate { [weak self] in
                self?.presentCarPlayAlert(title: "End your current drive first")
            }
            return
        }
        guard let currentLocation = locationService?.latestSample ?? locationService?.lastLocation else {
            popToRootTemplate { [weak self] in
                self?.presentCarPlayAlert(title: "Location unavailable")
            }
            return
        }
        let start = currentLocation.coordinate
        do {
            let route = try await APIClient.shared.navigationRoute(
                name: destination.name,
                start: start,
                destination: coordinate
            )
            let savedRoute = try await APIClient.shared.createNavigationDestinationRoute(from: route)
            let session = try await APIClient.shared.startRouteDriveSession(routeId: savedRoute.id)
            var routeSession = RouteDriveSessionState(
                dto: session,
                routeId: savedRoute.id,
                currentLocation: currentLocation
            )
            appState.applyStartCheckpointIfNeeded(to: &routeSession, route: savedRoute, location: currentLocation)
            appState.beginRouteDriveSession(
                route: savedRoute,
                shareLive: false,
                routeSession: routeSession,
                recordToProfile: true
            )
            appState.activeRouteDriveUsesAdhocCarPlayDestination = true
            OttoLog.app.info(
                "carplay_route_drive_started routeId=\(savedRoute.id) sessionId=\(routeSession.sessionId) status=\(routeSession.status)"
            )
            appState.recordDriveOnStartEnabled = true
            appState.requestMapTabRouteFocus(route: savedRoute, startDrive: false)
            projectedDestinationRoute = nil
            popToRootTemplate()
        } catch {
            popToRootTemplate { [weak self] in
                self?.presentCarPlayAlert(title: "Couldn’t start route drive")
            }
        }
    }

    private func makeProjectedDestinationRoute(from route: NavigationRouteResponseDTO) -> SavedRouteDTO {
        SavedRouteDTO(
            id: "carplay-destination-\(UUID().uuidString)",
            createdByUserId: "carplay",
            name: route.name,
            points: [
                RoutePointDTO(lat: route.start.lat, lng: route.start.lng, markerType: "start"),
                RoutePointDTO(lat: route.destination.lat, lng: route.destination.lng, markerType: "finish"),
            ],
            roadCoordinates: route.roadCoordinates,
            distanceMeters: route.distanceMeters,
            etaSeconds: route.etaSeconds,
            createdAt: nil,
            updatedAt: nil
        )
    }

    private func focusRequest(for route: SavedRouteDTO) -> (center: CLLocationCoordinate2D, latitudeDelta: CLLocationDegrees)? {
        let roadCoordinates: [CLLocationCoordinate2D] = carPlayCoordinates(from: route.roadCoordinates)
        let routeCoordinates: [CLLocationCoordinate2D] = roadCoordinates.isEmpty ? carPlayCoordinates(from: route.points) : roadCoordinates
        guard !routeCoordinates.isEmpty else { return nil }
        let minLat: CLLocationDegrees = routeCoordinates.map { $0.latitude }.min() ?? routeCoordinates[0].latitude
        let maxLat: CLLocationDegrees = routeCoordinates.map { $0.latitude }.max() ?? routeCoordinates[0].latitude
        let minLng: CLLocationDegrees = routeCoordinates.map { $0.longitude }.min() ?? routeCoordinates[0].longitude
        let maxLng: CLLocationDegrees = routeCoordinates.map { $0.longitude }.max() ?? routeCoordinates[0].longitude
        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2,
            longitude: (minLng + maxLng) / 2
        )
        let delta = Swift.max(Swift.max(maxLat - minLat, maxLng - minLng), 0.01) * 1.35
        return (center, min(delta, 1.2))
    }

    private func carPlayCoordinates(from points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        points.compactMap { point in
            let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
            return CLLocationCoordinate2DIsValid(coordinate) ? coordinate : nil
        }
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

    func requestFocus(on coordinate: CLLocationCoordinate2D, latitudeDelta: CLLocationDegrees? = nil) {
        focusRequest = CarPlayMapFocusRequest(coordinate: coordinate, latitudeDelta: latitudeDelta)
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

    func syncNativeNavigation(
        isActive: Bool,
        route: SavedRouteDTO?,
        guidance: TurnByTurnGuidanceState?
    ) {
        print(
            "[CarPlayNav] Sync requested nativeActive=\(isActive) " +
                "routeId=\(route?.id ?? "nil") routeStatus=\(appState?.activeRouteDriveSession?.status ?? "nil") " +
                "driveKind=\(appState?.activeDriveSession?.kind.rawValue ?? "nil") guidance=\(carPlayGuidanceLogSummary(guidance))"
        )
        guard isActive, let route else {
            endNativeNavigationIfNeeded(reason: "inactive")
            return
        }
        if navigationSession == nil || navigationSessionRouteID != route.id {
            startNativeNavigationSession(route: route)
        }
        guard let navigationSession else {
            logNativeNavigationSuppressed(route: route, guidance: guidance, reason: "missing_session")
            return
        }
        guard let guidance else {
            logNativeNavigationSuppressed(route: route, guidance: nil, reason: "missing_guidance")
            return
        }
        switch guidance.phase {
        case .arrived:
            endNativeNavigationIfNeeded(reason: "arrived")
            return
        case .loading, .navigating, .offRoute, .failed:
            break
        }
        let maneuver = makeCarPlayManeuver(from: guidance)
        let signature = carPlayManeuverSignature(guidance: guidance, routeID: route.id)
        if signature != lastManeuverSignature || navigationSession.upcomingManeuvers.isEmpty {
            navigationSession.upcomingManeuvers = [maneuver]
            lastManeuverSignature = signature
        }
        let estimates = makeTravelEstimates(from: guidance)
        navigationSession.updateEstimates(estimates, for: maneuver)
        logNativeNavigationState(route: route, session: navigationSession, maneuver: maneuver, guidance: guidance, estimates: estimates)
    }

    func endNativeNavigationIfNeeded(reason: String) {
        guard let navigationSession else {
            print("[CarPlayNav] Native navigation already inactive reason=\(reason)")
            return
        }
        print("[CarPlayNav] Ending native navigation reason=\(reason) session=\(navigationSession)")
        navigationSession.cancelTrip()
        self.navigationSession = nil
        navigationSessionRouteID = nil
        lastManeuverSignature = nil
    }

    private func startNativeNavigationSession(route: SavedRouteDTO) {
        guard let mapTemplate else {
            print("[CarPlayNav] Cannot start native navigation: missing CPMapTemplate routeId=\(route.id)")
            return
        }
        let trip = makeCarPlayTrip(for: route)
        let session = mapTemplate.startNavigationSession(for: trip)
        navigationSession = session
        navigationSessionRouteID = route.id
        lastManeuverSignature = nil
        print("[CarPlayNav] Navigation session started")
        print("[CarPlayNav] Start routeId=\(route.id) routeStatus=\(appState?.activeRouteDriveSession?.status ?? "nil") nativeObject=CPNavigationSession")
        print("[CarPlayNav] Session: \(session)")
        print("[CarPlayNav] Trip Name: \(route.name)")
        print("[CarPlayNav] Maneuver count: \(session.upcomingManeuvers.count)")
    }

    private func makeCarPlayTrip(for route: SavedRouteDTO) -> CPTrip {
        let coordinates = carPlayCoordinates(from: route.roadCoordinates.isEmpty ? route.points : route.roadCoordinates)
        let originCoordinate = coordinates.first
            ?? route.points.first.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
            ?? CLLocationCoordinate2D(latitude: 0, longitude: 0)
        let destinationCoordinate = coordinates.last
            ?? route.points.last.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
            ?? originCoordinate
        let originItem = MKMapItem(placemark: MKPlacemark(coordinate: originCoordinate))
        originItem.name = "Current Location"
        let destinationItem = MKMapItem(placemark: MKPlacemark(coordinate: destinationCoordinate))
        destinationItem.name = route.name
        let distanceText = Measurement(value: route.distanceMeters, unit: UnitLength.meters)
            .formatted(.measurement(width: .abbreviated, usage: .road))
        let durationFormatter = DateComponentsFormatter()
        durationFormatter.unitsStyle = .abbreviated
        durationFormatter.allowedUnits = [.hour, .minute]
        let durationText = durationFormatter.string(from: carPlayEstimatedDurationSeconds(distanceMeters: route.distanceMeters, durationSeconds: route.etaSeconds)) ?? ""
        let choice = CPRouteChoice(
            summaryVariants: [route.name],
            additionalInformationVariants: [distanceText, durationText].filter { !$0.isEmpty },
            selectionSummaryVariants: [route.name]
        )
        return CPTrip(origin: originItem, destination: destinationItem, routeChoices: [choice])
    }

    private func makeCarPlayManeuver(from guidance: TurnByTurnGuidanceState) -> CPManeuver {
        let maneuver = CPManeuver()
        let instruction = guidance.nextInstruction.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallbackInstruction = String(localized: "turn_by_turn_continue")
        maneuver.instructionVariants = [instruction.isEmpty ? fallbackInstruction : instruction]
        if #available(iOS 15.4, *) {
            maneuver.cardBackgroundColor = Self.nativeGuidanceBackgroundColor
            print("[CarPlayNav] CPManeuver cardBackgroundColor assigned color=ottoDark")
        } else {
            print("[CarPlayNav] CPManeuver cardBackgroundColor unavailable iOS<15.4")
        }
        print("[CarPlayNav] Creating CPManeuver \(carPlayGuidanceLogSummary(guidance)) cardColor=ottoDark")
        guard guidance.nextManeuver?.type != "depart" || appState?.activeRouteDriveSession?.isActive == true else {
            let configuration = UIImage.SymbolConfiguration(pointSize: 30, weight: .semibold)
            maneuver.symbolImage = UIImage(systemName: "location.north.fill", withConfiguration: configuration)
                ?? UIImage(systemName: "arrow.up", withConfiguration: configuration)
            return maneuver
        }
        let systemName = NavigationManeuverIcon.systemImageName(for: guidance.nextManeuver)
        let configuration = UIImage.SymbolConfiguration(pointSize: 30, weight: .semibold)
        maneuver.symbolImage = UIImage(systemName: systemName, withConfiguration: configuration)
            ?? UIImage(systemName: "arrow.up", withConfiguration: configuration)
        return maneuver
    }

    private func makeTravelEstimates(from guidance: TurnByTurnGuidanceState) -> CPTravelEstimates {
        CPTravelEstimates(
            distanceRemaining: carPlayDistanceMeasurement(meters: guidance.distanceToManeuverMeters),
            timeRemaining: carPlayEstimatedDurationSeconds(
                distanceMeters: guidance.remainingDistanceMeters,
                durationSeconds: guidance.remainingDurationSeconds
            )
        )
    }

    private func carPlayDistanceMeasurement(meters: Double) -> Measurement<UnitLength> {
        let clampedMeters = max(0, meters)
        if Locale.current.usesMetricSystem {
            return Measurement(value: clampedMeters, unit: UnitLength.meters)
        }
        return Measurement(value: clampedMeters, unit: UnitLength.meters).converted(to: .miles)
    }

    private func carPlayEstimatedDurationSeconds(
        distanceMeters: Double,
        durationSeconds: TimeInterval?
    ) -> TimeInterval {
        if let durationSeconds, durationSeconds > 0 {
            return durationSeconds
        }
        let fallbackMetersPerSecond = 13.4 // ~30 mph, avoids a zero-minute native estimate before route guidance loads.
        return max(60, max(0, distanceMeters) / fallbackMetersPerSecond)
    }

    private func carPlayManeuverSignature(guidance: TurnByTurnGuidanceState, routeID: String) -> String {
        "\(routeID)|\(guidance.currentStepIndex)|\(guidance.nextInstruction)|\(guidance.nextManeuver?.type ?? "nil")|\(guidance.nextManeuver?.modifier ?? "nil")"
    }

    private func logNativeNavigationSuppressed(
        route: SavedRouteDTO,
        guidance: TurnByTurnGuidanceState?,
        reason: String
    ) {
        print(
            "[CarPlayNav] Suppressed native navigation update reason=\(reason) " +
                "routeId=\(route.id) tripName=\(route.name) routeStatus=\(appState?.activeRouteDriveSession?.status ?? "nil") " +
                "guidance=\(carPlayGuidanceLogSummary(guidance))"
        )
    }

    private func logNativeNavigationState(
        route: SavedRouteDTO,
        session: CPNavigationSession,
        maneuver: CPManeuver,
        guidance: TurnByTurnGuidanceState,
        estimates: CPTravelEstimates
    ) {
        print("[CarPlayNav] Navigation Session Active routeId=\(route.id) tripName=\(route.name)")
        print("[CarPlayNav] Native renderer=CPMapTemplate/CPNavigationSession guidance=\(carPlayGuidanceLogSummary(guidance))")
        print("[CarPlayNav] Maneuver count: \(session.upcomingManeuvers.count)")
        print("[CarPlayNav] Distance remaining: \(estimates.distanceRemaining)")
        print("[CarPlayNav] Time remaining: \(estimates.timeRemaining)")
        print("[CarPlayNav] Step \(guidance.currentStepIndex + 1) Distance \(guidance.distanceToManeuverMeters) Instruction \(guidance.nextInstruction)")
        for (index, queuedManeuver) in session.upcomingManeuvers.enumerated() {
            print("[CarPlayNav] Maneuver \(index + 1) Instruction variants: \(queuedManeuver.instructionVariants)")
            print("[CarPlayNav] Maneuver \(index + 1) Has symbol image: \(queuedManeuver.symbolImage != nil)")
        }
        print("[CarPlayNav] Current maneuver instruction variants: \(maneuver.instructionVariants)")
        print("[CarPlayNav] Current maneuver symbol present: \(maneuver.symbolImage != nil)")
    }

    private func carPlayGuidanceLogSummary(_ guidance: TurnByTurnGuidanceState?) -> String {
        guard let guidance else { return "nil" }
        let maneuver = guidance.nextManeuver
        return "phase=\(carPlayGuidancePhaseName(guidance)) step=\(guidance.currentStepIndex + 1)/\(guidance.totalSteps) " +
            "maneuverType=\(maneuver?.type ?? "nil") modifier=\(maneuver?.modifier ?? "nil") " +
            "instruction=\"\(guidance.nextInstruction)\" distanceMeters=\(Int(guidance.distanceToManeuverMeters.rounded()))"
    }

    private func carPlayGuidancePhaseName(_ guidance: TurnByTurnGuidanceState?) -> String {
        guard let guidance else { return "nil" }
        switch guidance.phase {
        case .loading: return "loading"
        case .navigating: return "navigating"
        case .offRoute: return "offRoute"
        case .arrived: return "arrived"
        case .failed: return "failed"
        }
    }
}

struct CarPlayMapFocusRequest: Identifiable, Equatable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
    let latitudeDelta: CLLocationDegrees?

    static func == (lhs: CarPlayMapFocusRequest, rhs: CarPlayMapFocusRequest) -> Bool {
        lhs.id == rhs.id
    }
}

struct CarPlayMapZoomRequest: Identifiable, Equatable {
    let id = UUID()
    let delta: Int

    static func == (lhs: CarPlayMapZoomRequest, rhs: CarPlayMapZoomRequest) -> Bool {
        lhs.id == rhs.id
    }
}

struct NavigationRecentDestination: Codable, Hashable {
    static let storageKey = "otto.carplay.recentDestinations"
    static let maxStoredCount = 6

    let id: String
    let name: String
    let address: String?
    let latitude: Double
    let longitude: Double
    let source: String?

    init(destination: NavigationSearchResultDTO) {
        id = destination.id
        name = destination.name
        address = destination.address
        latitude = destination.latitude
        longitude = destination.longitude
        source = destination.source
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var navigationSearchResult: NavigationSearchResultDTO {
        NavigationSearchResultDTO(
            id: id,
            name: name,
            address: address,
            latitude: latitude,
            longitude: longitude,
            confidence: nil,
            source: source
        )
    }
}

enum NavigationDestinationRecentsStore {
    static func load() -> [NavigationRecentDestination] {
        guard let data = UserDefaults.standard.data(forKey: NavigationRecentDestination.storageKey),
              let recents = try? JSONDecoder().decode([NavigationRecentDestination].self, from: data) else {
            return []
        }
        return recents.filter { CLLocationCoordinate2DIsValid($0.coordinate) }
    }

    static func save(_ destination: NavigationSearchResultDTO) {
        let recent = NavigationRecentDestination(destination: destination)
        var recents = load()
        recents.removeAll { existing in
            existing.id == recent.id ||
                existing.name.caseInsensitiveCompare(recent.name) == .orderedSame ||
                (abs(existing.latitude - recent.latitude) < 0.000001 &&
                    abs(existing.longitude - recent.longitude) < 0.000001)
        }
        recents.insert(recent, at: 0)
        recents = Array(recents.prefix(NavigationRecentDestination.maxStoredCount))
        if let data = try? JSONEncoder().encode(recents) {
            UserDefaults.standard.set(data, forKey: NavigationRecentDestination.storageKey)
        }
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
    private static let usesNativeCarPlayGuidanceCards = true

    private enum Metrics {
        static let scale: CGFloat = 0.70
        static let driveFollowBaseAnchorYFraction: CGFloat = 0.68
        static let driveFollowWideAnchorYFraction: CGFloat = 0.62
        static let driveFollowBottomSafetyMargin: CGFloat = 96
        static let passiveSpeedTailDuration: TimeInterval = 2.25
        static let passiveSpeedTailMinimumSpeedMph = 15.0
        static let passiveSpeedTailMaximumDistanceMeters = 80.0
        static let passiveSpeedTailMinimumDistanceMeters = 4.0
        static let topDownFollowSpan = MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
        static let controlsHorizontalPadding: CGFloat = 13
        static let controlsBottomPadding: CGFloat = 20
        static let controlsStackSpacing: CGFloat = 7
        static let standardButtonSize: CGFloat = 39
        static let driveButtonSize: CGFloat = 45
        static let standardButtonFont: Font = .system(size: 13, weight: .bold)
        static let driveButtonFont: Font = .system(size: 16, weight: .bold)
        static let readyOverlayTopPadding: CGFloat = 24
        static let readyOverlayLeadingPadding: CGFloat = 24
        static let viewportResizeEpsilon: CGFloat = 1
        static let ornamentOptions = OrnamentOptions(
            scaleBar: ScaleBarViewOptions(
                position: .topLeading,
                margins: CGPoint(x: 14, y: -118),
                visibility: .visible,
                useMetricUnits: Locale.current.usesMetricSystem
            )
        )
    }

    @ObservedObject var appState: AppState
    @ObservedObject var locationService: LocationService
    @ObservedObject var raceTracksDatasetStore: RaceTracksDatasetStore
    @ObservedObject var controller: CarPlayMapController
    @StateObject private var followDisplayLink = CarPlayDisplayLinkTicker()
    private let mapHostInstanceID = String(UUID().uuidString.prefix(8))

    @State private var viewport: Viewport
    @State private var followsUser = true
    @State private var currentLatitudeDelta = 0.012
    @State private var mapboxMap: MapboxMap?
    @State private var mapReadinessState: MapReadinessState = .uninitialized
    @State private var lastSuccessfulRenderAt: Date?
    @State private var lastStyleLoadedAt: Date?
    @State private var lastMapLoadedAt: Date?
    @State private var lastRenderFrameAt: Date?
    @State private var lastSourceDataLoadedAt: Date?
    @State private var lastResourceRequestAt: Date?
    @State private var didLogFirstRenderFrame = false
    @State private var didLogFirstLoadedSourceData = false
    @State private var resourceRequestLogCount = 0
    @State private var mapLocationDisplayRevision: UInt = 0
    @State private var isNetworkAvailable = true
    @State private var networkMonitor: NWPathMonitor?
    @State private var networkMonitorQueue: DispatchQueue?
    @State private var isMapRecoveryInFlight = false
    @State private var mapViewportLayoutSize: CGSize = .zero
    @State private var isDriveCameraPitchEngaged = false
    @State private var liveLocationAnimation = CarPlayLiveLocationAnimationController()
    @State private var driveCameraTargetCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraRenderedCoordinate: CLLocationCoordinate2D?
    @State private var driveCameraTargetBearing: CGFloat = 0
    @State private var driveCameraRenderedBearing: CGFloat = 0
    @State private var passiveSpeedTailSamples: [DrivePathSample] = []
    @State private var targetPresenceCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var renderedPresenceCoordinates: [String: CLLocationCoordinate2D] = [:]
    @State private var lastMapHazardRefreshAt: Date = .distantPast
    @State private var carPlayMapMountGeneration = 0
    @State private var carPlayMapLoaded = false
    @State private var carPlayMapRecoveryAttempt = 0
    @State private var carPlayMapRecoveryTask: Task<Void, Never>?
    @State private var activeHostSurfaceGeneration: Int?
    @State private var followZoomOffsetSteps = 0
    @State private var carPlaySmoothingTask: Task<Void, Never>?
    @State private var carPlaySmoothingTick = 0
    @State private var initialNativeNavigationSyncTask: Task<Void, Never>?
    @State private var hasCompletedInitialNativeNavigationSync = false
    @State private var lastCarPlayRouteSampleTimestamp: Date?
    @State private var didRequestNavigationEndHostReload = false

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
        ZStack(alignment: .topLeading) {
            if controller.hostSurfaceState.isReady {
                OttoMapboxMapView(
                    viewport: $viewport,
                    surfaceLogTag: "CarPlayMap",
                    allowsInteraction: true,
                    onCameraChanged: { region in
                        currentLatitudeDelta = stableLatitudeDelta(forObservedRegion: region)
                    },
                    onUserGesture: {
                        recenterOnUserIfAvailable(force: true)
                    },
                    onMapLoaded: {
                        handleCarPlayMapLoaded()
                    },
                    onStyleLoaded: {
                        handleCarPlayStyleLoaded()
                    },
                    onRenderFrameFinished: {
                        handleCarPlayRenderFrameFinished()
                    },
                    onSourceDataLoaded: { loaded in
                        handleCarPlaySourceDataLoaded(loaded: loaded)
                    },
                    onResourceRequest: { summary in
                        handleCarPlayResourceRequest(summary)
                    },
                    onMapLoadingError: { message in
                        handleCarPlayMapLoadingError(message)
                    },
                    onMapboxMapReady: { map in
                        handleCarPlayMapReady(map)
                    },
                    ornamentOptions: Metrics.ornamentOptions
                ) {
                    mapContent
                }
                .id(carPlayMapMountGeneration)
                .ignoresSafeArea()
                .background(Color.black)
                .overlay {
                    Rectangle()
                        .fill(.black.opacity(0.12))
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                }
            } else {
                Color.black
                    .ignoresSafeArea()
            }

            if shouldShowCarPlayNavigationOverlay {
                carPlayNavigationOverlay
            }
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
        .onAppear {
            followDisplayLink.onFrame = {
                stepFollowCameraSmoothing()
            }
            followDisplayLink.start()
            startCarPlaySmoothingLoop()
            handleCarPlayMapViewAppeared()
            scheduleInitialNativeCarPlayNavigationSync()
            logCarPlayReadyOverlayVisibility(shouldShowCarPlayReadyOverlay, reason: "appear")
            logCarPlayNavigationOverlayState(reason: "appear")
        }
        .onChange(of: controller.hostSurfaceState) { _, surface in
            handleCarPlayHostSurfaceChanged(surface)
        }
        .onChange(of: projectedGuidanceDebugSignature) { _, signature in
            print("[CarPlay] projected guidance \(signature)")
            logCarPlayNavigationOverlayState(reason: "guidance-signature")
            syncNativeCarPlayNavigationAfterInitialDelay()
        }
        .onChange(of: shouldShowCarPlayReadyOverlay) { _, visible in
            logCarPlayReadyOverlayVisibility(visible, reason: "state-change")
        }
        .onChange(of: shouldShowCarPlayNavigationOverlay) { _, _ in
            logCarPlayNavigationOverlayState(reason: "overlay-visibility")
        }
        .onChange(of: carPlayNavigationOverlayIdentity) { _, identity in
            print("[CarPlayNav] Overlay identity changed \(identity)")
            logCarPlayNavigationOverlayState(reason: "overlay-identity")
        }
        .onDisappear {
            print("[CarPlayMap] map view disappeared host=\(mapHostInstanceID)")
            let isHostReload = controller.isFullMapHostReloadInProgress
            let isStaleHost = !controller.isCurrentMapHost(id: mapHostInstanceID)
            carPlayMapRecoveryTask?.cancel()
            carPlayMapRecoveryTask = nil
            stopCarPlayNetworkMonitoring()
            initialNativeNavigationSyncTask?.cancel()
            initialNativeNavigationSyncTask = nil
            hasCompletedInitialNativeNavigationSync = false
            if isHostReload || isStaleHost {
                print("[CarPlayMap] map view disappeared during host replacement host=\(mapHostInstanceID) stale=\(isStaleHost)")
            } else {
                controller.endNativeNavigationIfNeeded(reason: "map_disappear")
            }
            stopCarPlaySmoothingLoop()
            followDisplayLink.stop()
        }
        .onReceive(locationService.mapLocationDisplayTicks) { tick in
            mapLocationDisplayRevision = tick
            ingestCarPlayRouteDriveSampleIfNeeded()
            syncPresenceSmoothingTargets()
            updatePassiveSpeedTail()
            syncFollowCameraMode(forceFollowOnDriveStart: false)
        }
        .onChange(of: carPlaySmoothingTick) { _, _ in
            stepPresenceSmoothing()
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
            followZoomOffsetSteps = 0
            viewport = OttoMapboxCamera.viewport(
                for: MKCoordinateRegion(
                    center: request.coordinate,
                    span: MKCoordinateSpan(
                        latitudeDelta: request.latitudeDelta ?? 0.012,
                        longitudeDelta: request.latitudeDelta ?? 0.012
                    )
                )
            )
        }
        .onChange(of: controller.zoomRequest) { _, request in
            guard let request else { return }
            applyCarPlayZoom(delta: request.delta)
        }
        .onChange(of: controller.showTrafficLayer) { _, showTraffic in
            guard let mapboxMap else { return }
            MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: showTraffic)
        }
        .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
            syncLayerPreferencesFromPhone()
        }
        .onReceive(NotificationCenter.default.publisher(for: .ottoCarPlayCanonicalStateConfigured)) { _ in
            handleCanonicalCarPlayStateConfigured()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            ensureCarPlayMapLoaded(reason: "app-foreground")
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            ensureCarPlayMapLoaded(reason: "app-active")
        }
        .onChange(of: appState.hasActiveDriveSession) { _, isActive in
            if isActive {
                didRequestNavigationEndHostReload = false
                followsUser = true
                passiveSpeedTailSamples = []
                ensureCarPlayMapLoaded(reason: "drive-start")
            } else {
                requestCarPlayHostReloadAfterNavigationEnded(reason: "drive-session-inactive")
            }
            syncFollowCameraMode(forceFollowOnDriveStart: isActive)
            syncNativeCarPlayNavigationAfterInitialDelay()
        }
        .onChange(of: appState.activeRouteDriveSession?.status) { _, status in
            handleRouteDriveStatusChanged(status)
        }
    }

    private var shouldShowCarPlayTurnByTurnOverlay: Bool {
        guard isUsingCurrentBridgeAppState,
              isProjectedRouteDriveActive,
              let guidance = appState.turnByTurnGuidance else { return false }
        switch guidance.phase {
        case .loading, .navigating, .offRoute, .failed:
            return true
        case .arrived:
            return false
        }
    }

    private var isProjectedRouteDriveActive: Bool {
        guard isUsingCurrentBridgeAppState else { return false }
        if appState.activeRouteDriveSession != nil { return true }
        return appState.activeDriveSession?.kind == .route && appState.activeRouteDriveRoute != nil
    }

    private var isUsingCurrentBridgeAppState: Bool {
        OttoCarPlayAppBridge.shared.isCurrentAppState(appState)
    }

    private var shouldUseNativeCarPlayNavigation: Bool {
        guard isUsingCurrentBridgeAppState else {
            return false
        }
        guard Self.usesNativeCarPlayGuidanceCards else {
            return false
        }
        if let routeSession = appState.activeRouteDriveSession {
            return routeSession.isActive
        }
        guard appState.activeDriveSession?.kind == .route,
              appState.activeRouteDriveRoute != nil,
              let guidance = appState.turnByTurnGuidance else {
            return false
        }
        switch guidance.phase {
        case .navigating, .offRoute, .failed, .arrived:
            return true
        case .loading:
            return false
        }
    }

    private var shouldShowCarPlayReadyOverlay: Bool {
        guard isUsingCurrentBridgeAppState else { return false }
        return appState.activeRouteDriveSession?.isArmed == true && appState.activeRouteDriveRoute != nil
    }

    private var shouldShowCarPlayGuidanceOverlay: Bool {
        false
    }

    private var shouldShowCarPlayNavigationOverlay: Bool {
        shouldShowCarPlayReadyOverlay
    }

    private var carPlayReadyOverlayWidth: CGFloat {
        guard mapViewportLayoutSize.width > 0 else { return 320 }
        return min(max(mapViewportLayoutSize.width * 0.38, 280), 360)
    }

    private var projectedGuidanceDebugSignature: String {
        let routeStatus = appState.activeRouteDriveSession?.status ?? "nil"
        let driveKind = appState.activeDriveSession?.kind.rawValue ?? "nil"
        let hasRoute = appState.activeRouteDriveRoute != nil
        let guidancePhase: String
        if let guidance = appState.turnByTurnGuidance {
            switch guidance.phase {
            case .loading: guidancePhase = "loading"
            case .navigating: guidancePhase = "navigating"
            case .offRoute: guidancePhase = "offRoute"
            case .arrived: guidancePhase = "arrived"
            case .failed: guidancePhase = "failed"
            }
        } else {
            guidancePhase = "nil"
        }
        let guidanceIdentity = appState.turnByTurnGuidance.map { guidance in
            [
                "\(guidance.currentStepIndex)",
                guidance.nextManeuver?.type ?? "nil",
                guidance.nextManeuver?.modifier ?? "nil",
                guidance.nextInstruction,
                "\(Int(guidance.distanceToManeuverMeters.rounded()))"
            ].joined(separator: "|")
        } ?? "nil"
        let visible = nativeCarPlayGuidance != nil
        return "nativeVisible=\(visible) nativeAllowed=\(shouldUseNativeCarPlayNavigation) nativeSuppression=\(nativeCarPlayNavigationSuppressionReason ?? "none") customOverlay=\(carPlayNavigationOverlayMode) overlayIdentity=\(carPlayNavigationOverlayIdentity) readyOverlay=\(shouldShowCarPlayReadyOverlay) routeStatus=\(routeStatus) driveKind=\(driveKind) hasRoute=\(hasRoute) guidance=\(guidancePhase) guidanceIdentity=\(guidanceIdentity)"
    }

    private func syncNativeCarPlayNavigation() {
        print(
            "[CarPlayNav] Native decision allowed=\(shouldUseNativeCarPlayNavigation) " +
                "suppression=\(nativeCarPlayNavigationSuppressionReason ?? "none") overlay=\(carPlayNavigationOverlayMode)"
        )
        controller.syncNativeNavigation(
            isActive: shouldUseNativeCarPlayNavigation,
            route: appState.activeRouteDriveRoute,
            guidance: nativeCarPlayGuidance
        )
    }

    private func syncNativeCarPlayNavigationAfterInitialDelay() {
        if hasCompletedInitialNativeNavigationSync {
            syncNativeCarPlayNavigation()
        } else {
            scheduleInitialNativeCarPlayNavigationSync()
        }
    }

    private func handleRouteDriveStatusChanged(_ status: String?) {
        logCarPlayNavigationOverlayState(reason: "route-status-\(status ?? "nil")")
        guard status != nil else {
            syncFollowCameraMode(forceFollowOnDriveStart: false)
            syncNativeCarPlayNavigation()
            requestCarPlayHostReloadAfterNavigationEnded(reason: "route-status-nil")
            return
        }
        didRequestNavigationEndHostReload = false
        if status == "armed" {
            controller.endNativeNavigationIfNeeded(reason: "armed-pre-navigation")
        }
        followsUser = true
        ensureCarPlayMapLoaded(reason: "route-navigation-start")
        syncFollowCameraMode(forceFollowOnDriveStart: true)
        syncNativeCarPlayNavigationAfterInitialDelay()
    }

    private func requestCarPlayHostReloadAfterNavigationEnded(reason: String) {
        guard !didRequestNavigationEndHostReload else {
            print("[CarPlayMap] Navigation-end host reload already requested reason=\(reason)")
            return
        }
        guard !isProjectedRouteDriveActive,
              !shouldShowCarPlayNavigationOverlay else {
            print(
                "[CarPlayMap] Navigation-end host reload skipped reason=\(reason) " +
                    "active=\(isProjectedRouteDriveActive) overlay=\(carPlayNavigationOverlayMode)"
            )
            return
        }
        didRequestNavigationEndHostReload = true
        controller.endNativeNavigationIfNeeded(reason: "navigation-ended-\(reason)")
        print("[CarPlayMap] Restoring map after navigation ended reason=\(reason)")
        followsUser = true
        if appState.hasActiveDriveSession {
            syncFollowCameraMode(forceFollowOnDriveStart: false)
        } else if let location = locationService.latestSample ?? locationService.lastLocation {
            if isDriveCameraPitchEngaged {
                isDriveCameraPitchEngaged = false
                driveCameraTargetBearing = 0
                driveCameraRenderedBearing = 0
            }
            syncFollowCameraTarget(from: location)
            liveLocationAnimation.reset(to: location, bearing: 0)
            driveCameraRenderedCoordinate = liveLocationAnimation.renderedCoordinate ?? location.coordinate
            applyFollowCameraViewport(
                center: location.coordinate,
                bearing: 0,
                isDriveMode: false,
                animated: true
            )
        } else {
            disengageDriveCameraPitch()
        }
        ensureCarPlayMapLoaded(reason: "navigation-ended-\(reason)")
    }

    private func handleCanonicalCarPlayStateConfigured() {
        let canonicalState = OttoCarPlayAppBridge.shared.appState
        guard canonicalState !== appState else {
            syncNativeCarPlayNavigationAfterInitialDelay()
            return
        }
        print("[CarPlayMap] Canonical app state configured; reloading CarPlay host onto phone state")
        controller.requestFullMapHostReload(reason: "canonical-app-state")
    }

    private func scheduleInitialNativeCarPlayNavigationSync() {
        guard initialNativeNavigationSyncTask == nil else { return }
        initialNativeNavigationSyncTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            guard !Task.isCancelled else { return }
            hasCompletedInitialNativeNavigationSync = true
            initialNativeNavigationSyncTask = nil
            syncNativeCarPlayNavigation()
        }
    }

    private var nativeCarPlayGuidance: TurnByTurnGuidanceState? {
        guard shouldUseNativeCarPlayNavigation else {
            return nil
        }
        if let guidance = appState.turnByTurnGuidance, shouldShowCarPlayTurnByTurnOverlay {
            return guidance
        }
        guard let route = appState.activeRouteDriveRoute else {
            return nil
        }
        let instruction = String(localized: "turn_by_turn_loading")
        return TurnByTurnGuidanceState(
            phase: .loading,
            nextInstruction: instruction,
            nextManeuver: NavigationManeuver(type: "depart", modifier: nil, instruction: instruction),
            distanceToManeuverMeters: route.distanceMeters,
            currentRoadName: nil,
            remainingDistanceMeters: route.distanceMeters,
            remainingDurationSeconds: TimeInterval(route.etaSeconds ?? 0),
            eta: Date().addingTimeInterval(TimeInterval(route.etaSeconds ?? 0)),
            currentStepIndex: 0,
            totalSteps: 1
        )
    }

    private var carPlayOverlayGuidance: TurnByTurnGuidanceState? {
        nil
    }

    private var nativeCarPlayNavigationSuppressionReason: String? {
        guard !Self.usesNativeCarPlayGuidanceCards, isProjectedRouteDriveActive else { return nil }
        if appState.activeRouteDriveSession?.isArmed == true {
            return "armed_custom_overlay"
        }
        if appState.activeRouteDriveSession?.isActive == true || shouldShowCarPlayGuidanceOverlay {
            return "guidance_custom_overlay"
        }
        return "custom_overlay_policy"
    }

    private var carPlayNavigationOverlayMode: String {
        if shouldShowCarPlayReadyOverlay {
            return "ready"
        }
        return "hidden"
    }

    private var carPlayNavigationOverlayIdentity: String {
        guard let guidance = carPlayOverlayGuidance else {
            return carPlayNavigationOverlayMode
        }
        return [
            carPlayNavigationOverlayMode,
            "\(guidance.currentStepIndex)",
            guidance.nextManeuver?.type ?? "nil",
            guidance.nextManeuver?.modifier ?? "nil",
            guidance.nextInstruction,
            "\(Int(guidance.distanceToManeuverMeters.rounded()))",
        ].joined(separator: "|")
    }

    private var carPlayNavigationOverlay: some View {
        Group {
            if shouldShowCarPlayReadyOverlay {
                ProjectedDriveNavigationCard(waitingForDriveStart: true)
            }
        }
            .frame(width: carPlayReadyOverlayWidth, alignment: .leading)
            .padding(.top, Metrics.readyOverlayTopPadding)
            .padding(.leading, Metrics.readyOverlayLeadingPadding)
            .id(carPlayNavigationOverlayIdentity)
            .transition(.opacity)
            .accessibilityIdentifier("carplay_navigation_guidance_overlay")
    }

    private func recalculateCarPlayRouteGuidance() {
        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        appState.turnByTurnNavigationManager.recalculate(from: location)
    }

    private func retryCarPlayRouteGuidance() {
        guard let route = appState.activeRouteDriveRoute,
              let location = locationService.latestSample ?? locationService.lastLocation else { return }
        appState.turnByTurnNavigationManager.start(
            route: route,
            at: location,
            completedIndexes: appState.activeRouteDriveSession?.completedWaypointIndexes ?? []
        )
    }

    private func logCarPlayReadyOverlayVisibility(_ visible: Bool, reason: String) {
        print(
            "[CarPlayNav] Ready overlay visible=\(visible) reason=\(reason) " +
                "routeStatus=\(appState.activeRouteDriveSession?.status ?? "nil") " +
                "nativeAllowed=\(shouldUseNativeCarPlayNavigation) overlay=\(carPlayNavigationOverlayMode)"
        )
    }

    private func logCarPlayNavigationOverlayState(reason: String) {
        print(
            "[CarPlayNav] Overlay state reason=\(reason) mode=\(carPlayNavigationOverlayMode) " +
                "ready=\(shouldShowCarPlayReadyOverlay) guidance=\(carPlayGuidanceLogSummary(carPlayOverlayGuidance)) " +
                "nativeAllowed=\(shouldUseNativeCarPlayNavigation) suppression=\(nativeCarPlayNavigationSuppressionReason ?? "none")"
        )
    }

    private func carPlayGuidanceLogSummary(_ guidance: TurnByTurnGuidanceState?) -> String {
        guard let guidance else { return "nil" }
        let maneuver = guidance.nextManeuver
        return "phase=\(carPlayGuidancePhaseName(guidance)) step=\(guidance.currentStepIndex + 1)/\(guidance.totalSteps) " +
            "maneuverType=\(maneuver?.type ?? "nil") modifier=\(maneuver?.modifier ?? "nil") " +
            "instruction=\"\(guidance.nextInstruction)\" distanceMeters=\(Int(guidance.distanceToManeuverMeters.rounded()))"
    }

    private func carPlayGuidancePhaseName(_ guidance: TurnByTurnGuidanceState?) -> String {
        guard let guidance else { return "nil" }
        switch guidance.phase {
        case .loading: return "loading"
        case .navigating: return "navigating"
        case .offRoute: return "offRoute"
        case .arrived: return "arrived"
        case .failed: return "failed"
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
                let members = group.members
                let singleFriend = members.count == 1 ? members.first : nil
                let isCurrentUser = singleFriend.map { isSelfPresenceFriend($0) } ?? false
                let brandLogoURL = singleFriend.flatMap { presenceBrandLogoURL(for: $0) }
                let avatarFallbackUsers = appState.allUsers
                let currentUserID = appState.currentUserID
                let horizonScale = carPlayMarkerScale(
                    presenceHorizonScale(
                        for: group.coordinate,
                        isCurrentUser: isCurrentUser
                    )
                )
                let overlapPriority = presenceOverlapPriority(for: group.coordinate, tieBreaker: group.id.hashValue)

                MapViewAnnotation(coordinate: group.coordinate) {
                    MapPresenceBouncyMarkerContainer {
                        if let friend = singleFriend {
                            MapPresenceFriendAnnotationView(
                                friend: friend,
                                isCurrentUser: isCurrentUser,
                                brandLogoURL: brandLogoURL,
                                dwellText: nil,
                                avatarFallbackUsers: avatarFallbackUsers,
                                travelSurface: .land,
                                horizonScale: horizonScale,
                                showsPresenceStatusDot: false
                            )
                        } else {
                            MapPresenceCompositeFriendAnnotationView(
                                members: members,
                                currentUserID: currentUserID,
                                dwellText: nil,
                                avatarFallbackUsers: avatarFallbackUsers,
                                horizonScale: horizonScale
                            )
                        }
                    }
                }
                .allowOverlap(true)
                .ignoreCameraPadding(true)
                .allowOverlapWithPuck(appState.hasActiveDriveSession)
                .priority(overlapPriority)
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
        _ = mapLocationDisplayRevision
        return (locationService.latestSample ?? locationService.lastLocation)?.coordinate
    }

    private var followedVehicleCoordinate: CLLocationCoordinate2D? {
        guard followsUser else { return userCoordinate }
        return driveCameraRenderedCoordinate ?? driveCameraTargetCoordinate ?? userCoordinate
    }

    private var markerLODLatitudeDelta: Double {
        currentLatitudeDelta
    }

    private func carPlayMarkerScale(_ scale: CGFloat) -> CGFloat {
        scale * Metrics.scale
    }

    private func ingestCarPlayRouteDriveSampleIfNeeded() {
        guard appState.activeRouteDriveSession != nil else { return }
        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        guard CLLocationCoordinate2DIsValid(location.coordinate) else { return }
        if lastCarPlayRouteSampleTimestamp == location.timestamp {
            return
        }
        if appState.activeRouteDriveSession?.currentLocation?.timestamp == location.timestamp {
            lastCarPlayRouteSampleTimestamp = location.timestamp
            return
        }
        lastCarPlayRouteSampleTimestamp = location.timestamp
        let speed = locationService.effectiveSpeedMetersPerSecond()
        let movementMode = locationService.movementMode
        Task { @MainActor in
            await appState.ingestDriveSessionSample(
                location: location,
                speedMetersPerSecond: speed,
                movementMode: movementMode
            )
        }
    }

    private func handleCarPlayMapViewAppeared() {
        print("[CarPlayMap] map view appeared host=\(mapHostInstanceID)")
        controller.noteMapHostAppeared(id: mapHostInstanceID)
        guard isUsingCurrentBridgeAppState else {
            print("[CarPlayMap] map view using retired app state; requesting canonical reload host=\(mapHostInstanceID)")
            logCarPlayNavigationOverlayState(reason: "retired-app-state")
            controller.endNativeNavigationIfNeeded(reason: "retired-app-state")
            controller.requestFullMapHostReload(reason: "canonical-app-state")
            return
        }
        startCarPlayNetworkMonitoring()
        appState.requestLocationSessionSync()
        syncLayerPreferencesFromPhone()
        syncPresenceSmoothingTargets()
        syncFollowCameraMode(forceFollowOnDriveStart: true)
        ensureValidCarPlayCamera(reason: "appear")
        beginCarPlayMapCreationIfHostReady(reason: "appear")
        Task { await refreshMapHazardsIfNeeded(force: true) }
    }

    private func startCarPlaySmoothingLoop() {
        guard carPlaySmoothingTask == nil else { return }
        carPlaySmoothingTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 200_000_000)
                guard !Task.isCancelled else { return }
                carPlaySmoothingTick &+= 1
            }
        }
    }

    private func stopCarPlaySmoothingLoop() {
        carPlaySmoothingTask?.cancel()
        carPlaySmoothingTask = nil
    }

    private func handleCarPlayHostSurfaceChanged(_ surface: CarPlayHostSurfaceState) {
        print("[CarPlayMap] Host surface changed \(surface.logSummary)")
        beginCarPlayMapCreationIfHostReady(reason: "host-surface-\(surface.reason)")
    }

    private func beginCarPlayMapCreationIfHostReady(reason: String) {
        let surface = controller.hostSurfaceState
        guard surface.isReady else {
            print("[CarPlayMap] Waiting for host surface reason=\(reason) \(surface.logSummary)")
            mapReadinessState = .uninitialized
            return
        }
        guard activeHostSurfaceGeneration != surface.generation || mapReadinessState == .uninitialized else {
            return
        }
        activeHostSurfaceGeneration = surface.generation
        print("[CarPlayMap] Creating Mapbox map reason=\(reason) \(surface.logSummary)")
        carPlayMapLoaded = false
        mapReadinessState = .initializingMapbox
        mapboxMap = nil
        lastSuccessfulRenderAt = nil
        lastStyleLoadedAt = nil
        lastMapLoadedAt = nil
        lastRenderFrameAt = nil
        lastSourceDataLoadedAt = nil
        lastResourceRequestAt = nil
        didLogFirstRenderFrame = false
        didLogFirstLoadedSourceData = false
        resourceRequestLogCount = 0
        isMapRecoveryInFlight = false
        carPlayMapRecoveryAttempt = 0
        ensureValidCarPlayCamera(reason: reason)
        scheduleCarPlayMapLoadWatchdog(reason: "host-ready-\(reason)", delayNanoseconds: 8_000_000_000)
    }

    private func handleCarPlayMapLoaded() {
        print("[CarPlayMap] map loaded")
        lastMapLoadedAt = Date()
        carPlayMapLoaded = true
        if mapReadinessState != .tilesLoaded {
            mapReadinessState = .loadingTiles
            scheduleCarPlayMapLoadWatchdog(reason: "no-source-data", delayNanoseconds: 5_000_000_000)
        }
        if let mapboxMap {
            MapboxTrafficLayerController.sync(map: mapboxMap, showTraffic: controller.showTrafficLayer)
        }
        recenterOnUserIfAvailable(force: true)
    }

    private func handleCarPlayMapReady(_ map: MapboxMap) {
        print("[CarPlayMap] mapbox map ready")
        mapboxMap = map
        mapReadinessState = .loadingStyle
        print("[CarPlayMap] Loading style")
        MapboxTrafficLayerController.sync(map: map, showTraffic: controller.showTrafficLayer)
        appState.requestLocationSessionSync()
        syncFollowCameraMode(forceFollowOnDriveStart: true)
        ensureValidCarPlayCamera(reason: "map-ready")
        scheduleCarPlayMapLoadWatchdog(reason: "no-style-loaded", delayNanoseconds: 8_000_000_000)
    }

    private func handleCarPlayStyleLoaded() {
        mapReadinessState = .styleLoaded
        lastStyleLoadedAt = Date()
        print("[CarPlayMap] Style loaded")
        ensureValidCarPlayCamera(reason: "style-loaded")
        mapReadinessState = .loadingTiles
        scheduleCarPlayMapLoadWatchdog(reason: "no-render-frame", delayNanoseconds: 8_000_000_000)
    }

    private func handleCarPlayRenderFrameFinished() {
        lastRenderFrameAt = Date()
        guard !didLogFirstRenderFrame else { return }
        didLogFirstRenderFrame = true
        print("[CarPlayMap] First render frame finished")
    }

    private func markCarPlayTilesLoaded(reason: String) {
        guard mapReadinessState != .tilesLoaded else {
            lastSuccessfulRenderAt = Date()
            return
        }
        lastSuccessfulRenderAt = Date()
        mapReadinessState = .tilesLoaded
        carPlayMapLoaded = true
        carPlayMapRecoveryAttempt = 0
        isMapRecoveryInFlight = false
        controller.noteMapHostRenderedSuccessfully()
        carPlayMapRecoveryTask?.cancel()
        carPlayMapRecoveryTask = nil
        print("[CarPlayMap] First tile/source complete reason=\(reason)")
    }

    private func handleCarPlaySourceDataLoaded(loaded: Bool?) {
        guard loaded == true else { return }
        if !didLogFirstLoadedSourceData {
            didLogFirstLoadedSourceData = true
            print("[CarPlayMap] Source data loaded")
        }
        lastSourceDataLoadedAt = Date()
        markCarPlayTilesLoaded(reason: "source-data-loaded")
    }

    private func handleCarPlayResourceRequest(_ summary: String) {
        lastResourceRequestAt = Date()
        guard resourceRequestLogCount < 12 else { return }
        resourceRequestLogCount += 1
        print("[CarPlayMap] Resource request \(resourceRequestLogCount): \(summary.prefix(500))")
    }

    private func handleCarPlayMapLoadingError(_ message: String) {
        mapReadinessState = .failed
        print("[CarPlayMap] Failure reason: \(message)")
        scheduleCarPlayMapLoadWatchdog(reason: "map-loading-error", delayNanoseconds: 1_000_000_000)
    }

    private func scheduleCarPlayMapLoadWatchdog(reason: String, delayNanoseconds: UInt64) {
        carPlayMapRecoveryTask?.cancel()
        let generation = carPlayMapMountGeneration
        carPlayMapRecoveryTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: delayNanoseconds)
            guard !Task.isCancelled else { return }
            guard !isCarPlayMapHealthy else { return }
            guard generation == carPlayMapMountGeneration else { return }
            let stallStage = carPlayMapStallStage
            logCarPlayMapWatchdogDiagnostics(reason: reason, stallStage: stallStage)
            recoverStalledCarPlayMap(reason: "\(reason):\(stallStage)")
        }
    }

    private func recoverStalledCarPlayMap(reason: String) {
        guard !isMapRecoveryInFlight else {
            print("[CarPlayMap] map recovery skipped reason=\(reason) state=\(mapReadinessState.rawValue)")
            return
        }
        guard isNetworkAvailable else {
            print("[CarPlayMap] map recovery waiting for network reason=\(reason)")
            mapReadinessState = .failed
            return
        }
        guard carPlayMapRecoveryAttempt < 4 else {
            print("[CarPlayMap] map recovery gave up reason=\(reason)")
            logCarPlayMapWatchdogDiagnostics(reason: "gave-up-\(reason)", stallStage: carPlayMapStallStage)
            mapReadinessState = .failed
            recenterOnUserIfAvailable(force: true)
            return
        }
        isMapRecoveryInFlight = true
        carPlayMapRecoveryAttempt += 1
        mapReadinessState = .retrying
        print("[CarPlayMap] map recovery attempt=\(carPlayMapRecoveryAttempt) reason=\(reason)")
        carPlayMapLoaded = false
        appState.requestLocationSessionSync()
        syncFollowCameraMode(forceFollowOnDriveStart: true)
        let stallStage = carPlayMapStallStage
        if stallStage == "no-render-frame" {
            print("[CarPlayMap] Recovery action: full host reload")
            isMapRecoveryInFlight = false
            if controller.requestFullMapHostReload(reason: reason) {
                mapReadinessState = .initializingMapbox
            } else {
                controller.completeFullMapHostReload()
                mapReadinessState = .failed
                logCarPlayMapWatchdogDiagnostics(reason: "full-host-reload-unavailable-\(reason)", stallStage: stallStage)
            }
        } else if carPlayMapRecoveryAttempt == 4 {
            print("[CarPlayMap] Recovery action: final diagnostic failure")
            logCarPlayMapWatchdogDiagnostics(reason: "final-\(reason)", stallStage: stallStage)
            mapReadinessState = .failed
            isMapRecoveryInFlight = false
        } else if carPlayMapRecoveryAttempt == 1, didLogFirstRenderFrame, let mapboxMap {
            print("[CarPlayMap] Recovery action: reload style")
            mapReadinessState = .loadingStyle
            mapboxMap.loadStyle(.standard, reloadPolicy: .always) { error in
                Task { @MainActor in
                    isMapRecoveryInFlight = false
                    if let error {
                        handleCarPlayMapLoadingError("style reload failed: \(error.localizedDescription)")
                    } else {
                        scheduleCarPlayMapLoadWatchdog(reason: "style-reload", delayNanoseconds: 8_000_000_000)
                    }
                }
            }
        } else if carPlayMapRecoveryAttempt <= 2 {
            print("[CarPlayMap] Recovery action: recreate map instance")
            remountCarPlayMapInstance(reason: reason)
        } else {
            print("[CarPlayMap] Recovery action: full host reload")
            isMapRecoveryInFlight = false
            if controller.requestFullMapHostReload(reason: reason) {
                mapReadinessState = .initializingMapbox
            } else {
                controller.completeFullMapHostReload()
                mapReadinessState = .failed
                logCarPlayMapWatchdogDiagnostics(reason: "full-host-reload-unavailable-\(reason)", stallStage: stallStage)
            }
        }
    }

    private func remountCarPlayMapInstance(reason: String) {
        mapboxMap = nil
        carPlayMapLoaded = false
        lastSuccessfulRenderAt = nil
        lastStyleLoadedAt = nil
        lastMapLoadedAt = nil
        lastRenderFrameAt = nil
        lastSourceDataLoadedAt = nil
        lastResourceRequestAt = nil
        didLogFirstRenderFrame = false
        didLogFirstLoadedSourceData = false
        resourceRequestLogCount = 0
        carPlayMapMountGeneration &+= 1
        mapReadinessState = .initializingMapbox
        isMapRecoveryInFlight = false
        scheduleCarPlayMapLoadWatchdog(reason: "remount-\(carPlayMapRecoveryAttempt)-\(reason)", delayNanoseconds: 8_000_000_000)
    }

    private var carPlayMapStallStage: String {
        if lastStyleLoadedAt == nil { return "no-style-loaded" }
        if lastRenderFrameAt == nil { return "no-render-frame" }
        if lastSourceDataLoadedAt == nil { return "no-source-data" }
        if lastMapLoadedAt == nil { return "no-map-loaded" }
        return "stale-tiles"
    }

    private func logCarPlayMapWatchdogDiagnostics(reason: String, stallStage: String) {
        print(
            "[CarPlayMap] Watchdog reason=\(reason) stall=\(stallStage) " +
                "state=\(mapReadinessState.rawValue) attempt=\(carPlayMapRecoveryAttempt) " +
                "mapboxMap=\(mapboxMap != nil) carPlayMapLoaded=\(carPlayMapLoaded) " +
                "renderLogged=\(didLogFirstRenderFrame) sourceLogged=\(didLogFirstLoadedSourceData) " +
                "network=\(isNetworkAvailable) appState=\(UIApplication.shared.applicationState.rawValue) " +
                "viewportSize=\(mapViewportLayoutSize) surface={\(controller.hostSurfaceState.logSummary)} " +
                "styleAge=\(ageDescription(since: lastStyleLoadedAt)) " +
                "mapLoadedAge=\(ageDescription(since: lastMapLoadedAt)) " +
                "renderAge=\(ageDescription(since: lastRenderFrameAt)) " +
                "sourceAge=\(ageDescription(since: lastSourceDataLoadedAt)) " +
                "resourceAge=\(ageDescription(since: lastResourceRequestAt)) " +
                "resourceCount=\(resourceRequestLogCount)"
        )
    }

    private func ageDescription(since date: Date?) -> String {
        guard let date else { return "nil" }
        return String(format: "%.1fs", Date().timeIntervalSince(date))
    }

    private var isCarPlayMapHealthy: Bool {
        guard mapboxMap != nil else { return false }
        guard mapReadinessState == .tilesLoaded else { return false }
        guard let lastSuccessfulRenderAt else { return false }
        return Date().timeIntervalSince(lastSuccessfulRenderAt) <= 30
    }

    private func ensureCarPlayMapLoaded(reason: String) {
        print(
            "[CarPlayMap] ensureCarPlayMapLoaded reason=\(reason) state=\(mapReadinessState.rawValue) " +
                "network=\(isNetworkAvailable) foreground=\(UIApplication.shared.applicationState.rawValue)"
        )
        OttoMapboxRuntimeConfig.configureIfReady(tag: "CarPlayMap")
        ensureValidCarPlayCamera(reason: reason)
        guard controller.hostSurfaceState.isReady else {
            beginCarPlayMapCreationIfHostReady(reason: "ensure-\(reason)")
            return
        }
        guard isNetworkAvailable else {
            print("[CarPlayMap] Network available: false")
            return
        }
        if mapReadinessState == .initializingMapbox || mapReadinessState == .loadingStyle {
            print("[CarPlayMap] ensure skipped: initialization already in progress")
            return
        }
        guard !isCarPlayMapHealthy else { return }
        recoverStalledCarPlayMap(reason: reason)
    }

    private func startCarPlayNetworkMonitoring() {
        guard networkMonitor == nil else { return }
        let monitor = NWPathMonitor()
        let queue = DispatchQueue(label: "to.ottomot.driftd.carplay-map-network")
        monitor.pathUpdateHandler = { path in
            let available = path.status == .satisfied
            Task { @MainActor in
                let changed = isNetworkAvailable != available
                isNetworkAvailable = available
                print("[CarPlayMap] Network available: \(available)")
                if changed {
                    print("[CarPlayMap] Network changed: \(available ? "online" : "offline")")
                }
                if changed && available {
                    print("[CarPlayMap] Retrying map load after network restored")
                    ensureCarPlayMapLoaded(reason: "network-restored")
                }
            }
        }
        networkMonitor = monitor
        networkMonitorQueue = queue
        monitor.start(queue: queue)
    }

    private func stopCarPlayNetworkMonitoring() {
        networkMonitor?.cancel()
        networkMonitor = nil
        networkMonitorQueue = nil
    }

    private func ensureValidCarPlayCamera(reason: String) {
        let source: String
        let coordinate: CLLocationCoordinate2D
        if let user = userCoordinate, CLLocationCoordinate2DIsValid(user) {
            source = "user location"
            coordinate = user
        } else if let routeCoordinate = activeRouteLineCoordinates.first, CLLocationCoordinate2DIsValid(routeCoordinate) {
            source = "route start"
            coordinate = routeCoordinate
        } else {
            source = "fallback"
            coordinate = CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
        }
        print("[CarPlayMap] Camera source: \(source) reason=\(reason)")
        print("[CarPlayMap] Camera coordinate valid: \(CLLocationCoordinate2DIsValid(coordinate))")
        guard CLLocationCoordinate2DIsValid(coordinate) else { return }
        if mapboxMap?.cameraState.center.isValidForCarPlayCamera == true {
            return
        }
        viewport = OttoMapboxCamera.viewport(
            for: MKCoordinateRegion(
                center: coordinate,
                span: Metrics.topDownFollowSpan
            )
        )
    }

    private func updateMapViewportLayoutSize(_ size: CGSize) {
        guard size.width.isFinite, size.height.isFinite, size.width > 0, size.height > 0 else { return }
        let previousSize = mapViewportLayoutSize
        guard abs(previousSize.width - size.width) > Metrics.viewportResizeEpsilon ||
              abs(previousSize.height - size.height) > Metrics.viewportResizeEpsilon else { return }
        mapViewportLayoutSize = size
        refreshFollowCameraForCurrentSize()
    }

    private func applyCarPlayZoom(delta: Int) {
        guard delta != 0 else { return }
        if followsUser, let coordinate = followedVehicleCoordinate {
            followZoomOffsetSteps = min(3, max(-3, followZoomOffsetSteps + delta))
            driveCameraRenderedCoordinate = coordinate
            applyFollowCameraViewport(
                center: coordinate,
                bearing: appState.hasActiveDriveSession && isDriveCameraPitchEngaged ? driveCameraRenderedBearing : 0,
                isDriveMode: appState.hasActiveDriveSession && isDriveCameraPitchEngaged,
                animated: true
            )
            return
        }

        let cameraState = mapboxMap?.cameraState
        let center =
            cameraState?.center
            ?? userCoordinate
            ?? CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
        let currentZoom: Double
        if let cameraState {
            currentZoom = Double(cameraState.zoom)
        } else {
            currentZoom = Double(OttoMapboxCamera.zoomLevel(
                for: MKCoordinateRegion(center: center, span: Metrics.topDownFollowSpan)
            ))
        }
        let nextZoom = min(20.0, max(4.0, currentZoom + Double(delta)))
        let longitudeDelta = 360.0 / pow(2.0, nextZoom)
        let latitudeDelta = longitudeDelta / max(0.2, cos(center.latitude * .pi / 180))
        viewport = OttoMapboxCamera.viewport(
            for: MKCoordinateRegion(
                center: center,
                span: MKCoordinateSpan(latitudeDelta: latitudeDelta, longitudeDelta: longitudeDelta)
            )
        )
    }

    private func refreshFollowCameraForCurrentSize() {
        guard followsUser,
              let center = followedVehicleCoordinate else {
            return
        }
        driveCameraRenderedCoordinate = center
        let isDriveMode = appState.hasActiveDriveSession && isDriveCameraPitchEngaged
        applyFollowCameraViewport(
            center: center,
            bearing: isDriveMode ? driveCameraRenderedBearing : 0,
            isDriveMode: isDriveMode,
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
        _ = mapLocationDisplayRevision
        guard let coordinate = followedVehicleCoordinate else {
            return locationService.latestSample ?? locationService.lastLocation
        }
        return CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
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
            if followsUser, isSelfPresenceFriend(member) {
                return member
            }
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
        guard let coordinate = followedVehicleCoordinate else { return nil }
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
        return groups
    }

    private func syncPresenceSmoothingTargets() {
        let members = activePresenceMembers
        let currentIDs = Set(members.map(\.id))
        targetPresenceCoordinates = Dictionary(uniqueKeysWithValues: members.map { ($0.id, $0.coordinate) })
        renderedPresenceCoordinates = renderedPresenceCoordinates.filter { currentIDs.contains($0.key) }
        for member in members where renderedPresenceCoordinates[member.id] == nil {
            renderedPresenceCoordinates[member.id] = member.coordinate
        }
        if followsUser, let selfMember {
            renderedPresenceCoordinates[selfMember.id] = selfMember.coordinate
        }
    }

    private func stepPresenceSmoothing() {
        guard !targetPresenceCoordinates.isEmpty else { return }
        var next = renderedPresenceCoordinates
        for (id, target) in targetPresenceCoordinates {
            if followsUser, let selfMember, id == selfMember.id {
                next[id] = target
                continue
            }
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
        guard let route = appState.activeRouteDriveRoute ?? controller.projectedDestinationRoute else { return [] }
        let road = coordinates(from: route.roadCoordinates)
        if road.count >= 2 { return road }
        return coordinates(from: route.points)
    }

    private var activeRouteMapPoints: [RouteMapPointModel] {
        guard let route = appState.activeRouteDriveRoute ?? controller.projectedDestinationRoute else { return [] }
        let indexed = route.points.enumerated().compactMap { index, point -> RouteMapPointModel? in
            guard point.markerType != "path",
                  !(appState.activeRouteDriveUsesAdhocCarPlayDestination && point.markerType == "start"),
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

    private func syncFollowCameraMode(forceFollowOnDriveStart: Bool) {
        if forceFollowOnDriveStart {
            followsUser = true
        }
        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        guard followsUser else { return }

        if appState.hasActiveDriveSession {
            if isDriveCameraPitchEngaged {
                syncFollowCameraTarget(from: location)
            } else {
                enterDriveCameraMode(from: location)
            }
        } else {
            if isDriveCameraPitchEngaged {
                isDriveCameraPitchEngaged = false
                driveCameraRenderedBearing = 0
                driveCameraTargetBearing = 0
                liveLocationAnimation.reset(to: location, bearing: 0)
                let center = liveLocationAnimation.renderedCoordinate ?? location.coordinate
                driveCameraRenderedCoordinate = center
                applyFollowCameraViewport(
                    center: center,
                    bearing: 0,
                    isDriveMode: false,
                    animated: true
                )
            }
            syncFollowCameraTarget(from: location)
            if driveCameraRenderedCoordinate == nil {
                driveCameraRenderedCoordinate = location.coordinate
            }
        }
    }

    private func enterDriveCameraMode(from location: CLLocation? = nil) {
        let resolved = location ?? locationService.latestSample ?? locationService.lastLocation
        guard let resolved else {
            recenterOnUserIfAvailable(force: true)
            return
        }
        isDriveCameraPitchEngaged = true
        syncFollowCameraTarget(from: resolved)
        liveLocationAnimation.reset(to: resolved, bearing: driveCameraTargetBearing)
        driveCameraRenderedCoordinate = liveLocationAnimation.renderedCoordinate ?? resolved.coordinate
        driveCameraRenderedBearing = liveLocationAnimation.renderedBearing
        OttoLog.map.info(
            "carplay_drive_camera_enter routeActive=\(appState.activeRouteDriveSession != nil) spanLat=\(OttoMapboxCamera.driveTrackingSpan.latitudeDelta)"
        )
        applyFollowCameraViewport(
            center: resolved.coordinate,
            bearing: driveCameraRenderedBearing,
            isDriveMode: true,
            span: OttoMapboxCamera.driveTrackingSpan,
            animated: true
        )
    }

    private func recenterDriveCamera(from location: CLLocation, animated: Bool) {
        syncFollowCameraTarget(from: location)
        liveLocationAnimation.reset(to: location, bearing: driveCameraTargetBearing)
        driveCameraRenderedCoordinate = liveLocationAnimation.renderedCoordinate ?? location.coordinate
        driveCameraRenderedBearing = liveLocationAnimation.renderedBearing
        applyFollowCameraViewport(
            center: location.coordinate,
            bearing: driveCameraTargetBearing,
            isDriveMode: true,
            span: OttoMapboxCamera.driveTrackingSpan,
            animated: animated
        )
    }

    private func stepFollowCameraSmoothing() {
        guard followsUser else { return }
        guard let frame = liveLocationAnimation.frame() else { return }
        let isDriveMode = appState.hasActiveDriveSession && isDriveCameraPitchEngaged
        let newBearing = isDriveMode ? frame.bearing : 0
        let current = driveCameraRenderedCoordinate ?? frame.coordinate
        let movedMeters = CLLocation(latitude: current.latitude, longitude: current.longitude)
            .distance(from: CLLocation(latitude: frame.coordinate.latitude, longitude: frame.coordinate.longitude))
        let bearingDelta = abs(
            OttoMapboxCamera.shortPathBearingDelta(from: driveCameraRenderedBearing, to: newBearing)
        )
        guard frame.isAnimating || movedMeters > 0.15 || bearingDelta > 0.2 else { return }

        driveCameraRenderedCoordinate = frame.coordinate
        driveCameraRenderedBearing = newBearing
        applyFollowCameraViewport(
            center: frame.coordinate,
            bearing: newBearing,
            isDriveMode: isDriveMode,
            span: isDriveMode ? OttoMapboxCamera.driveTrackingSpan : nil,
            animated: false
        )
    }

    private func syncFollowCameraTarget(from location: CLLocation) {
        liveLocationAnimation.push(
            location: location,
            isDriveMode: appState.hasActiveDriveSession
        )
        driveCameraTargetCoordinate = liveLocationAnimation.targetCoordinate ?? location.coordinate
        driveCameraTargetBearing = appState.hasActiveDriveSession ? liveLocationAnimation.targetBearing : 0
        driveCameraRenderedCoordinate = liveLocationAnimation.renderedCoordinate ?? driveCameraRenderedCoordinate
        driveCameraRenderedBearing = appState.hasActiveDriveSession ? liveLocationAnimation.renderedBearing : 0
    }

    private func applyFollowCameraViewport(
        center: CLLocationCoordinate2D,
        bearing: CGFloat,
        isDriveMode: Bool,
        span: MKCoordinateSpan? = nil,
        animated: Bool
    ) {
        let cameraSpan = span ?? currentFollowCameraSpan()
        viewport = OttoMapboxCamera.viewport(
            for: MKCoordinateRegion(center: center, span: cameraSpan),
            bearing: bearing,
            pitch: isDriveMode ? OttoMapboxCamera.drivePitchDegrees : 0,
            followPadding: isDriveMode ? carPlayDriveFollowEdgeInsets(mapSize: mapViewportLayoutSize) : nil
        )
    }

    private func currentFollowCameraSpan() -> MKCoordinateSpan {
        let baseSpan: MKCoordinateSpan
        if appState.hasActiveDriveSession, isDriveCameraPitchEngaged {
            baseSpan = OttoMapboxCamera.driveTrackingSpan
        } else {
            baseSpan = Metrics.topDownFollowSpan
        }
        let scale = pow(0.5, Double(followZoomOffsetSteps))
        return MKCoordinateSpan(
            latitudeDelta: max(0.00035, min(0.08, baseSpan.latitudeDelta * scale)),
            longitudeDelta: max(0.00035, min(0.08, baseSpan.longitudeDelta * scale))
        )
    }

    private func stableLatitudeDelta(forObservedRegion region: MKCoordinateRegion) -> CLLocationDegrees {
        guard followsUser else { return region.span.latitudeDelta }
        return currentFollowCameraSpan().latitudeDelta
    }

    private func disengageDriveCameraPitch() {
        isDriveCameraPitchEngaged = false
        driveCameraTargetCoordinate = nil
        driveCameraRenderedCoordinate = nil
        driveCameraTargetBearing = 0
        driveCameraRenderedBearing = 0
        liveLocationAnimation.clear()
    }

    private func recenterOnUserIfAvailable(force: Bool) {
        guard force || followsUser else { return }
        guard let location = locationService.latestSample ?? locationService.lastLocation else { return }
        followsUser = true
        guard appState.hasActiveDriveSession else {
            syncFollowCameraTarget(from: location)
            liveLocationAnimation.reset(to: location, bearing: 0)
            driveCameraRenderedCoordinate = liveLocationAnimation.renderedCoordinate ?? location.coordinate
            driveCameraRenderedBearing = liveLocationAnimation.renderedBearing
            applyFollowCameraViewport(
                center: location.coordinate,
                bearing: 0,
                isDriveMode: false,
                animated: true
            )
            return
        }
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

private extension CLLocationCoordinate2D {
    var isValidForCarPlayCamera: Bool {
        CLLocationCoordinate2DIsValid(self) &&
            latitude.isFinite &&
            longitude.isFinite &&
            (abs(latitude) > 0.000001 || abs(longitude) > 0.000001)
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
