import CoreLocation
import Foundation

extension Notification.Name {
    static let ottoCarPlayCanonicalStateConfigured = Notification.Name("ottoCarPlayCanonicalStateConfigured")
    static let ottoCarPlayPhoneAppReady = Notification.Name("ottoCarPlayPhoneAppReady")
}

@MainActor
final class OttoCarPlayAppBridge {
    static let shared = OttoCarPlayAppBridge()

    private var configuredAppState: AppState?
    private var configuredLocationService: LocationService?
    private var configuredRaceTracksDatasetStore: RaceTracksDatasetStore?
    private var fallbackAppState: AppState?
    private var fallbackLocationService: LocationService?
    private var fallbackRaceTracksDatasetStore: RaceTracksDatasetStore?
    private var phoneAppPreparedForCarPlay = false
    private var phoneAppSceneActivatedForCarPlay = false
    private var didPostPhoneAppReadyForCarPlay = false

    private init() {}

    var isConfigured: Bool {
        configuredAppState != nil &&
            configuredLocationService != nil &&
            configuredRaceTracksDatasetStore != nil
    }

    var isPhoneAppReadyForCarPlay: Bool {
        isConfigured && phoneAppPreparedForCarPlay && phoneAppSceneActivatedForCarPlay
    }

    var configuredAppStateIfAvailable: AppState? {
        configuredAppState
    }

    var configuredLocationServiceIfAvailable: LocationService? {
        configuredLocationService
    }

    var configuredRaceTracksDatasetStoreIfAvailable: RaceTracksDatasetStore? {
        configuredRaceTracksDatasetStore
    }

    func configure(
        appState: AppState,
        locationService: LocationService,
        raceTracksDatasetStore: RaceTracksDatasetStore
    ) {
        if let fallbackAppState, fallbackAppState !== appState {
            appState.adoptCarPlayStartedDriveIfNeeded(from: fallbackAppState)
            appState.isCarPlayMapConnected = fallbackAppState.isCarPlayMapConnected
            appState.isCarPlayMapActive = fallbackAppState.isCarPlayMapActive
            fallbackAppState.clearRouteDriveSessionState()
            fallbackAppState.activeDriveSession = nil
            fallbackAppState.routeDriveFeedbackEvent = nil
        }
        configuredAppState = appState
        configuredLocationService = locationService
        configuredRaceTracksDatasetStore = raceTracksDatasetStore
        fallbackAppState = nil
        fallbackLocationService = nil
        fallbackRaceTracksDatasetStore = nil
        syncCarPlayLocationNeeds()
        NotificationCenter.default.post(name: .ottoCarPlayCanonicalStateConfigured, object: nil)
        notifyPhoneAppReadyForCarPlayIfNeeded()
    }

    func markPhoneAppPreparedForCarPlay() {
        phoneAppPreparedForCarPlay = true
        notifyPhoneAppReadyForCarPlayIfNeeded()
    }

    func markPhoneAppSceneActivatedForCarPlay() {
        phoneAppSceneActivatedForCarPlay = true
        notifyPhoneAppReadyForCarPlayIfNeeded()
    }

    func resetPhoneAppReadinessForCarPlay() {
        phoneAppPreparedForCarPlay = false
        phoneAppSceneActivatedForCarPlay = false
        didPostPhoneAppReadyForCarPlay = false
    }

    private func notifyPhoneAppReadyForCarPlayIfNeeded() {
        guard isPhoneAppReadyForCarPlay, !didPostPhoneAppReadyForCarPlay else { return }
        didPostPhoneAppReadyForCarPlay = true
        NotificationCenter.default.post(name: .ottoCarPlayPhoneAppReady, object: nil)
    }

    func isCurrentAppState(_ state: AppState) -> Bool {
        if let configuredAppState {
            return configuredAppState === state
        }
        if let fallbackAppState {
            return fallbackAppState === state
        }
        return false
    }

    var appState: AppState {
        if let configuredAppState {
            return configuredAppState
        }
        if let fallbackAppState {
            return fallbackAppState
        }
        let state = AppState()
        fallbackAppState = state
        return state
    }

    var locationService: LocationService {
        if let configuredLocationService {
            return configuredLocationService
        }
        if let fallbackLocationService {
            return fallbackLocationService
        }
        let service = LocationService()
        fallbackLocationService = service
        return service
    }

    var raceTracksDatasetStore: RaceTracksDatasetStore {
        if let configuredRaceTracksDatasetStore {
            return configuredRaceTracksDatasetStore
        }
        if let fallbackRaceTracksDatasetStore {
            return fallbackRaceTracksDatasetStore
        }
        let store = RaceTracksDatasetStore()
        fallbackRaceTracksDatasetStore = store
        return store
    }

    func markCarPlayMapConnected(_ connected: Bool) {
        appState.isCarPlayMapConnected = connected
        if !connected {
            appState.isCarPlayMapActive = false
        }
        syncCarPlayLocationNeeds()
    }

    func markCarPlayMapActive(_ active: Bool) {
        appState.isCarPlayMapActive = active
        syncCarPlayLocationNeeds()
    }

    func syncCarPlayLocationNeeds() {
        guard appState.isCarPlayMapActive else {
            if !appState.hasActiveDriveSession,
               !appState.isMapScreenActive,
               !appState.isEventsScreenActive,
               !appState.isRouteBuilderPresented {
                locationService.applyDesiredState(.none)
            }
            return
        }

        // This only starts updates if permission is already granted; it never requests permission.
        locationService.applyDesiredState(
            LocationSessionNeeds(gps: true, motion: false, freshDisplay: true)
        )
    }
}
