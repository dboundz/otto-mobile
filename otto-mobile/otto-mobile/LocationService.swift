import CoreLocation
import CoreMotion
import Foundation
import Combine
import os

enum MotionPermissionState: Equatable {
    case notDetermined
    case authorized
    case denied
    case restricted
}

/// Product-driven location session needs; applied idempotently by [LocationService.applyDesiredState].
struct LocationSessionNeeds: Equatable {
    var gps = false
    var motion = false
    var freshDisplay = false

    static let none = LocationSessionNeeds()
}

/// Location updates are delivered frequently (especially in Simulator). Publishing every fix rebuilds `Map` + annotations and can freeze the UI.
@MainActor
final class LocationService: NSObject, ObservableObject {
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published private(set) var motionAuthorizationStatus: MotionPermissionState = .notDetermined
    @Published var lastLocation: CLLocation?
    @Published var speedMetersPerSecond: Double = 0
    @Published private(set) var movementMode: FriendMovementMode = .unknown

    /// Newest GPS sample (for presence / networking). Updated on every fix; not throttled.
    private(set) var latestSample: CLLocation?
    /// Newest speed sample for networking; does not trigger SwiftUI updates.
    private(set) var latestSpeedMetersPerSecond: Double = 0

    private let locationManager = CLLocationManager()
    private var activityManager: CMMotionActivityManager?
    private let motionPermissionPromptAttemptedKey = "otto.motionPermissionPromptAttempted"
    private var locationUpdateCount = 0
    private var lastPublishedLocation: CLLocation?
    private var lastPublishDate: Date?
    private var liveSampleHandler: ((CLLocation, Double) -> Void)?
    private var routeDriveSampleHandler: ((CLLocation, Double) -> Void)?
    private var lastLiveSampleHandlerDate: Date?
    private var lastLiveSampleHandlerLocation: CLLocation?
    private var liveDisplayEnabled = false
    private var appliedSessionNeeds = LocationSessionNeeds.none
    private var gpsUpdatesRunning = false
    private var motionUpdatesRunning = false
    /// Bumps on live-display GPS ticks so SwiftUI refreshes when [latestSample] moves ahead of throttled [lastLocation].
    @Published private(set) var mapLocationDisplayTick: UInt = 0
    private var isMonitoringSignificantLocationChanges = false
    private let eventCheckInRegionPrefix = "otto.event."
    private var singleLocationContinuation: CheckedContinuation<CLLocation?, Never>?
    /// Fired with raw Mongo `eventId` when the user enters a monitored check-in region.
    var onEnterEventCheckInRegion: ((String) -> Void)?

    override init() {
        super.init()
        locationManager.delegate = self
        #if targetEnvironment(simulator)
        locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        locationManager.distanceFilter = 25
        #else
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = 5
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.showsBackgroundLocationIndicator = true
        #endif
        locationManager.activityType = .automotiveNavigation
        authorizationStatus = locationManager.authorizationStatus
        OttoLog.location.info("LocationService init auth=\(OttoLog.describeAuth(self.authorizationStatus))")
    }

    func requestPermissionIfNeeded() {
        guard authorizationStatus == .notDetermined else { return }
        OttoLog.location.info("Requesting when-in-use location authorization")
        locationManager.requestWhenInUseAuthorization()
    }

    func requestBackgroundPermissionIfNeeded() {
        guard authorizationStatus == .authorizedWhenInUse else { return }
        OttoLog.location.info("Requesting always location authorization for background sharing")
        locationManager.requestAlwaysAuthorization()
    }

    func startUpdatingLocation() {
        guard authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways else {
            OttoLog.location.info("startUpdatingLocation skipped; auth=\(OttoLog.describeAuth(self.authorizationStatus))")
            return
        }
        OttoLog.location.info(
            "startUpdatingLocation() auth=\(OttoLog.describeAuth(self.authorizationStatus)) backgroundAllowed=\(self.authorizationStatus == .authorizedAlways)"
        )
        locationManager.startUpdatingLocation()
        gpsUpdatesRunning = true
        startSignificantLocationMonitoringIfNeeded()
    }

    func refreshMotionAuthorizationStatus() {
        guard hasAttemptedMotionPermissionPrompt else {
            motionAuthorizationStatus = .notDetermined
            return
        }
        guard CMMotionActivityManager.isActivityAvailable() else {
            motionAuthorizationStatus = .restricted
            return
        }
        switch CMMotionActivityManager.authorizationStatus() {
        case .notDetermined:
            motionAuthorizationStatus = .notDetermined
        case .restricted:
            motionAuthorizationStatus = .restricted
        case .denied:
            motionAuthorizationStatus = .denied
        case .authorized:
            motionAuthorizationStatus = .authorized
        @unknown default:
            motionAuthorizationStatus = .restricted
        }
    }

    func requestMotionPermissionIfNeeded() {
        UserDefaults.standard.set(true, forKey: motionPermissionPromptAttemptedKey)
        guard CMMotionActivityManager.isActivityAvailable() else {
            motionAuthorizationStatus = .restricted
            return
        }
        guard motionAuthorizationStatus == .notDetermined else {
            refreshMotionAuthorizationStatus()
            return
        }
        OttoLog.location.info("Requesting motion activity authorization")
        startActivityUpdatesIfAvailable()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 700_000_000)
            self.refreshMotionAuthorizationStatus()
        }
    }

    func startMotionActivityUpdatesIfAuthorized() {
        guard hasAttemptedMotionPermissionPrompt else { return }
        refreshMotionAuthorizationStatus()
        guard motionAuthorizationStatus == .authorized else { return }
        startActivityUpdatesIfAvailable()
        motionUpdatesRunning = true
    }

    var hasAttemptedMotionPermissionPrompt: Bool {
        UserDefaults.standard.bool(forKey: motionPermissionPromptAttemptedKey)
    }

    /// Called after authorization changes so `ContentView` can re-sync session needs.
    var onAuthorizationChanged: (() -> Void)?

    /// Applies GPS, motion, and UI display needs (orchestrated from `ContentView`). Reconciles hardware every call so drift/missed starts cannot leave GPS off while the map is foreground.
    func applyDesiredState(_ needs: LocationSessionNeeds) {
        let previous = appliedSessionNeeds
        appliedSessionNeeds = needs

        if needs.gps {
            if !gpsUpdatesRunning {
                startUpdatingLocation()
            } else {
                // Idempotent re-assert after tab/permission races.
                locationManager.startUpdatingLocation()
            }
            applyMapForegroundLocationTuning(enabled: needs.freshDisplay)
        } else if gpsUpdatesRunning {
            stopGPSUpdates()
            applyMapForegroundLocationTuning(enabled: false)
        }

        if needs.motion {
            startMotionActivityUpdatesIfAuthorized()
        } else if motionUpdatesRunning {
            stopMotionActivityUpdates()
        }

        if needs.freshDisplay != liveDisplayEnabled {
            setLiveDisplayEnabled(needs.freshDisplay)
        }

        if needs != previous {
            OttoLog.location.info(
                "applyDesiredState gps=\(needs.gps) motion=\(needs.motion) freshDisplay=\(needs.freshDisplay) gpsRunning=\(self.gpsUpdatesRunning)"
            )
        }
    }

    /// Tighter updates on Map / sharing so short walks move the self pin (default `distanceFilter` is 5 m).
    private func applyMapForegroundLocationTuning(enabled: Bool) {
        #if !targetEnvironment(simulator)
        locationManager.distanceFilter = enabled ? 2 : 5
        #endif
    }

    func stopUpdatingLocation() {
        stopGPSUpdates()
        gpsUpdatesRunning = false
    }

    func stopMotionActivityUpdates() {
        OttoLog.location.info("stopMotionActivityUpdates()")
        activityManager?.stopActivityUpdates()
        motionUpdatesRunning = false
    }

    private func stopGPSUpdates() {
        OttoLog.location.info("stopGPSUpdates()")
        locationManager.stopUpdatingLocation()
        stopSignificantLocationMonitoringIfNeeded()
        gpsUpdatesRunning = false
    }

    func setLiveSampleHandler(_ handler: @escaping (CLLocation, Double) -> Void) {
        liveSampleHandler = handler
    }

    func setRouteDriveSampleHandler(_ handler: ((CLLocation, Double) -> Void)?) {
        routeDriveSampleHandler = handler
    }

    /// When the Map tab is visible or sharing is on, publish/display location on a lighter throttle.
    func setLiveDisplayEnabled(_ enabled: Bool) {
        guard liveDisplayEnabled != enabled else { return }
        liveDisplayEnabled = enabled
        guard enabled, let latestSample else { return }
        _ = publishThrottledDisplayLocation(latestSample, speed: latestSpeedMetersPerSecond, force: true)
        mapLocationDisplayTick &+= 1
    }

    /// Backward-compatible alias; prefer orchestrated [setLiveDisplayEnabled] via [applyDesiredState].
    func setMapForegroundDisplayEnabled(_ enabled: Bool) {
        setLiveDisplayEnabled(enabled)
    }

    /// Prefer the freshest sample while live display is enabled; otherwise use throttled [lastLocation].
    var displayLocation: CLLocation? {
        if liveDisplayEnabled, let latestSample {
            return latestSample
        }
        return lastLocation
    }

    func displaySpeedMetersPerSecond(staleAfter seconds: TimeInterval = 6) -> Double {
        guard let sample = displayLocation else { return 0 }
        if Date().timeIntervalSince(sample.timestamp) > seconds {
            return 0
        }
        if liveDisplayEnabled {
            return latestSpeedMetersPerSecond
        }
        return speedMetersPerSecond
    }

    func effectiveSpeedMetersPerSecond(staleAfter seconds: TimeInterval = 6) -> Double {
        guard let latestSample else { return 0 }
        if Date().timeIntervalSince(latestSample.timestamp) > seconds {
            return 0
        }
        return latestSpeedMetersPerSecond
    }

    /// Returns a recent fix or requests a one-shot location update for automatic event check-in.
    func resolveLocationForEventCheckIn() async -> CLLocation? {
        if let loc = latestSample ?? lastLocation {
            return loc
        }
        guard authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways else {
            return nil
        }
        return await withCheckedContinuation { continuation in
            singleLocationContinuation = continuation
            locationManager.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard let pending = self.singleLocationContinuation else { return }
                self.singleLocationContinuation = nil
                pending.resume(returning: self.latestSample ?? self.lastLocation)
            }
        }
    }

    private func resumeSingleLocationContinuationIfNeeded(with location: CLLocation?) {
        guard let pending = singleLocationContinuation else { return }
        singleLocationContinuation = nil
        pending.resume(returning: location)
    }

    func makeDiagnosticsSnapshot() -> LocationDiagnosticsSnapshot {
        let metersSincePublish: Double? = {
            guard let latestSample, let anchor = lastPublishedLocation else { return nil }
            return latestSample.distance(from: anchor)
        }()
        return LocationDiagnosticsSnapshot(
            authorizationStatus: authorizationStatus,
            motionAuthorizationStatus: motionAuthorizationStatus,
            gpsRunning: gpsUpdatesRunning,
            motionRunning: motionUpdatesRunning,
            liveDisplayEnabled: liveDisplayEnabled,
            appliedNeeds: appliedSessionNeeds,
            locationUpdateCount: locationUpdateCount,
            distanceFilter: locationManager.distanceFilter,
            mapLocationDisplayTick: mapLocationDisplayTick,
            movementMode: movementMode,
            latestSample: latestSample,
            displayLocation: displayLocation,
            lastLocation: lastLocation,
            latestSpeedMetersPerSecond: latestSpeedMetersPerSecond,
            displaySpeedMetersPerSecond: displaySpeedMetersPerSecond(),
            metersSinceLastPublish: metersSincePublish
        )
    }
}

struct LocationDiagnosticsSnapshot {
    let authorizationStatus: CLAuthorizationStatus
    let motionAuthorizationStatus: MotionPermissionState
    let gpsRunning: Bool
    let motionRunning: Bool
    let liveDisplayEnabled: Bool
    let appliedNeeds: LocationSessionNeeds
    let locationUpdateCount: Int
    let distanceFilter: Double
    let mapLocationDisplayTick: UInt
    let movementMode: FriendMovementMode
    let latestSample: CLLocation?
    let displayLocation: CLLocation?
    let lastLocation: CLLocation?
    let latestSpeedMetersPerSecond: Double
    let displaySpeedMetersPerSecond: Double
    let metersSinceLastPublish: Double?
}

extension LocationService: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let s = manager.authorizationStatus
        Task { @MainActor in
            OttoLog.location.info("Authorization changed to \(OttoLog.describeAuth(s))")
            self.authorizationStatus = s
            self.onAuthorizationChanged?()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let latest = locations.last else { return }
        Task { @MainActor in
            self.applyLocationSample(latest)
            self.resumeSingleLocationContinuationIfNeeded(with: latest)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            OttoLog.location.error("CLLocationManager failed: \(String(describing: error))")
            self.resumeSingleLocationContinuationIfNeeded(with: self.latestSample ?? self.lastLocation)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard region.identifier.hasPrefix("otto.event.") else { return }
        let eventId = String(region.identifier.dropFirst("otto.event.".count))
        Task { @MainActor in
            OttoLog.location.info("auto_checkin_trigger callback=didEnterRegion eventId=\(eventId)")
            self.onEnterEventCheckInRegion?(eventId)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        guard region.identifier.hasPrefix("otto.event.") else { return }
        guard state == .inside else { return }
        let eventId = String(region.identifier.dropFirst("otto.event.".count))
        Task { @MainActor in
            OttoLog.location.info("auto_checkin_trigger callback=didDetermineStateInside eventId=\(eventId)")
            self.onEnterEventCheckInRegion?(eventId)
        }
    }

    private func applyLocationSample(_ latest: CLLocation) {
        latestSample = latest
        let speed = max(latest.speed, 0)
        latestSpeedMetersPerSecond = speed
        locationUpdateCount += 1
        let now = Date()

        let didPublishDisplay = publishThrottledDisplayLocation(latest, speed: speed, force: false)
        if liveDisplayEnabled {
            // [latestSample] is not @Published; bump every fix so Map observes [displayLocation] changes.
            mapLocationDisplayTick &+= 1
        }
        _ = didPublishDisplay
        notifyLiveSampleHandlerIfNeeded(location: latest, speedMetersPerSecond: speed, now: now)
        if motionUpdatesRunning {
            // Core Motion owns movement mode while sharing.
        } else {
            let inferred = inferMovementMode(fromSpeedMps: speed)
            if movementMode != inferred {
                movementMode = inferred
            }
        }

        if locationUpdateCount == 1 {
            let c = latest.coordinate
            OttoLog.location.info(
                "First location fix lat=\(c.latitude) lng=\(c.longitude) hAcc=\(latest.horizontalAccuracy)"
            )
        } else if locationUpdateCount % 30 == 0 {
            let c = latest.coordinate
            OttoLog.location.debug(
                "Location sample #\(self.locationUpdateCount) lat=\(c.latitude) lng=\(c.longitude) publishedDisplay=\(didPublishDisplay)"
            )
        }
    }

    @discardableResult
    private func publishThrottledDisplayLocation(_ latest: CLLocation, speed: Double, force: Bool) -> Bool {
        let now = Date()
        let minInterval: TimeInterval = liveDisplayEnabled ? 0.75 : 1.75
        let minDistance: CLLocationDistance = liveDisplayEnabled ? 2 : 22

        let shouldPublish: Bool
        if force {
            shouldPublish = true
        } else if let anchor = lastPublishedLocation {
            let timeOK = lastPublishDate.map { now.timeIntervalSince($0) >= minInterval } ?? true
            let distOK = latest.distance(from: anchor) >= minDistance
            shouldPublish = timeOK || distOK
        } else {
            shouldPublish = true
        }

        if shouldPublish {
            lastLocation = latest
            speedMetersPerSecond = speed
            lastPublishedLocation = latest
            lastPublishDate = now
        }
        return shouldPublish
    }

    private func notifyLiveSampleHandlerIfNeeded(location: CLLocation, speedMetersPerSecond: Double, now: Date) {
        guard liveSampleHandler != nil || routeDriveSampleHandler != nil else { return }
        let minInterval: TimeInterval = 4
        let minDistance: CLLocationDistance = 18

        let elapsedOK = lastLiveSampleHandlerDate.map { now.timeIntervalSince($0) >= minInterval } ?? true
        let distanceOK = lastLiveSampleHandlerLocation.map { location.distance(from: $0) >= minDistance } ?? true
        guard elapsedOK || distanceOK else { return }

        lastLiveSampleHandlerDate = now
        lastLiveSampleHandlerLocation = location
        liveSampleHandler?(location, speedMetersPerSecond)
        routeDriveSampleHandler?(location, speedMetersPerSecond)
    }

    private func startActivityUpdatesIfAvailable() {
        guard CMMotionActivityManager.isActivityAvailable() else {
            motionAuthorizationStatus = .restricted
            return
        }
        if motionUpdatesRunning { return }
        let manager = activityManager ?? CMMotionActivityManager()
        activityManager = manager
        motionUpdatesRunning = true
        manager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let self, let activity else { return }
            self.refreshMotionAuthorizationStatus()
            let next: FriendMovementMode
            if activity.automotive {
                next = .driving
            } else if activity.walking || activity.running {
                next = .walking
            } else {
                let inferred = self.inferMovementMode(fromSpeedMps: self.latestSpeedMetersPerSecond)
                next = inferred == .unknown && self.movementMode == .driving ? .driving : inferred
            }
            if self.movementMode != next {
                self.movementMode = next
            }
        }
    }

    private func startSignificantLocationMonitoringIfNeeded() {
        guard !isMonitoringSignificantLocationChanges else { return }
        guard CLLocationManager.significantLocationChangeMonitoringAvailable() else { return }
        isMonitoringSignificantLocationChanges = true
        locationManager.startMonitoringSignificantLocationChanges()
        OttoLog.location.info("startMonitoringSignificantLocationChanges()")
    }

    private func stopSignificantLocationMonitoringIfNeeded() {
        guard isMonitoringSignificantLocationChanges else { return }
        isMonitoringSignificantLocationChanges = false
        locationManager.stopMonitoringSignificantLocationChanges()
        OttoLog.location.info("stopMonitoringSignificantLocationChanges()")
    }

    func clearEventCheckInRegions() {
        replaceMonitoredEventCheckInRegions([])
    }

    /// iOS allows a limited number of monitored regions; pass at most ~20.
    func replaceMonitoredEventCheckInRegions(_ regions: [CLCircularRegion]) {
        let manager = locationManager
        for region in manager.monitoredRegions where region.identifier.hasPrefix(eventCheckInRegionPrefix) {
            manager.stopMonitoring(for: region)
        }
        let selectedRegions = Array(regions.prefix(20))
        if regions.count > selectedRegions.count {
            OttoLog.location.info(
                "auto_checkin_geofence_registration droppedDueToSystemCap=\(regions.count - selectedRegions.count)"
            )
        }
        for region in selectedRegions {
            manager.startMonitoring(for: region)
            manager.requestState(for: region)
            OttoLog.location.info("auto_checkin_geofence_registration started id=\(region.identifier) radius=\(region.radius)")
        }
    }

    private func inferMovementMode(fromSpeedMps speed: Double) -> FriendMovementMode {
        if speed >= 4.5 { return .driving }  // ~10 mph
        if speed > 0.6 && speed < 3.0 { return .walking }
        return .unknown
    }
}
