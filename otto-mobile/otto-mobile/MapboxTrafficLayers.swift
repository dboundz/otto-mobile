import MapboxMaps
import UIKit

struct OttoMapLayerPreferences {
    private enum Keys {
        static let showDrives = "MapScreen.showDrivesLayer"
        static let showPublic = "MapScreen.showPublicCircleLayer"
        static let showMyPlaces = "MapScreen.showMyPlacesLayer"
        static let showEvents = "MapScreen.showEventsLayer"
        static let showRaceTracks = "MapScreen.showRaceTracksLayer"
        static let showTraffic = "MapScreen.showTrafficLayer"
        static let visibleCircleIDs = "MapScreen.visibleCircleLayerIDs"
        static let visibleDriveLineIDs = "MapScreen.visibleDriveLineIDs"
    }

    var showPublicPresenceLayer: Bool
    var showSavedPlacesLayer: Bool
    var showUpcomingEventsLayer: Bool
    var showRaceTracksLayer: Bool
    var showTrafficLayer: Bool
    var visibleCircleLayerIDs: Set<String>

    static func current(defaultCircleIDs: Set<String> = []) -> OttoMapLayerPreferences {
        let defaults = UserDefaults.standard
        let rawCircleIDs = defaults.array(forKey: Keys.visibleCircleIDs) as? [String]
        let savedCircleIDs = Set(rawCircleIDs ?? [])
        let hasSavedCircleIDs = defaults.object(forKey: Keys.visibleCircleIDs) != nil
        let circleIDs = (!hasSavedCircleIDs || savedCircleIDs.isEmpty)
            ? defaultCircleIDs
            : savedCircleIDs.intersection(defaultCircleIDs)

        return OttoMapLayerPreferences(
            showPublicPresenceLayer: defaults.object(forKey: Keys.showPublic) == nil
                ? false
                : defaults.bool(forKey: Keys.showPublic),
            showSavedPlacesLayer: defaults.object(forKey: Keys.showMyPlaces) == nil
                ? true
                : defaults.bool(forKey: Keys.showMyPlaces),
            showUpcomingEventsLayer: defaults.object(forKey: Keys.showEvents) == nil
                ? true
                : defaults.bool(forKey: Keys.showEvents),
            showRaceTracksLayer: defaults.object(forKey: Keys.showRaceTracks) == nil
                ? true
                : defaults.bool(forKey: Keys.showRaceTracks),
            showTrafficLayer: defaults.object(forKey: Keys.showTraffic) == nil
                ? true
                : defaults.bool(forKey: Keys.showTraffic),
            visibleCircleLayerIDs: circleIDs
        )
    }
}

/// Live congestion overlay for the main Map tab (`mapbox-traffic-v1`).
@MainActor
enum MapboxTrafficLayerController {
    static let sourceID = "otto-map-traffic"
    private static let sourceURL = "mapbox://mapbox.mapbox-traffic-v1"
    private static let sourceLayer = "traffic"
    private static var desiredTrafficByMapID: [ObjectIdentifier: Bool] = [:]

    private struct CongestionStyle {
        let suffix: String
        let value: String
        let color: UIColor
    }

    /// Omit `low` (green free-flow) — only show delays and worse (Android parity).
    private static let styles: [CongestionStyle] = [
        CongestionStyle(suffix: "moderate", value: "moderate", color: UIColor(red: 1, green: 200 / 255, blue: 0, alpha: 1)),
        CongestionStyle(suffix: "heavy", value: "heavy", color: UIColor(red: 1, green: 100 / 255, blue: 0, alpha: 1)),
        CongestionStyle(suffix: "severe", value: "severe", color: UIColor(red: 230 / 255, green: 55 / 255, blue: 55 / 255, alpha: 1)),
    ]

    static func sync(map: MapboxMap, showTraffic: Bool) {
        let mapID = ObjectIdentifier(map)
        desiredTrafficByMapID[mapID] = showTraffic
        if showTraffic {
            install(on: map, mapID: mapID, remainingRetries: 3)
        } else {
            remove(from: map)
        }
    }

    private static func install(on map: MapboxMap, mapID: ObjectIdentifier, remainingRetries: Int) {
        guard desiredTrafficByMapID[mapID] == true else { return }
        remove(from: map)

        var source = VectorSource(id: sourceID)
        source.url = sourceURL

        do {
            try map.addSource(source)
        } catch {
            logInstallFailure("source", error: error)
            scheduleRetryIfNeeded(on: map, mapID: mapID, remainingRetries: remainingRetries)
            return
        }

        for style in styles {
            do {
                try map.addLayer(lineLayer(for: style))
            } catch {
                logInstallFailure("layer \(style.suffix)", error: error)
                scheduleRetryIfNeeded(on: map, mapID: mapID, remainingRetries: remainingRetries)
                return
            }
        }

        scheduleRetryIfNeeded(on: map, mapID: mapID, remainingRetries: remainingRetries)
    }

    private static func scheduleRetryIfNeeded(on map: MapboxMap, mapID: ObjectIdentifier, remainingRetries: Int) {
        guard remainingRetries > 0 else { return }
        let delayNanoseconds: UInt64 = remainingRetries == 3 ? 250_000_000 : 750_000_000
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: delayNanoseconds)
            guard desiredTrafficByMapID[mapID] == true else { return }
            install(on: map, mapID: mapID, remainingRetries: remainingRetries - 1)
        }
    }

    private static func logInstallFailure(_ component: String, error: Error) {
        #if DEBUG
        print("[traffic] failed to install \(component): \(error)")
        #endif
    }

    private static func remove(from map: MapboxMap) {
        for style in styles {
            try? map.removeLayer(withId: "\(sourceID)-\(style.suffix)")
        }
        try? map.removeSource(withId: sourceID)
    }

    private static func lineLayer(for style: CongestionStyle) -> LineLayer {
        var layer = LineLayer(id: "\(sourceID)-\(style.suffix)", source: sourceID)
        layer.sourceLayer = sourceLayer
        layer.filter = Exp(.eq) {
            Exp(.get) { "congestion" }
            style.value
        }
        layer.lineCap = .constant(.round)
        layer.lineJoin = .constant(.round)
        layer.lineColor = .constant(StyleColor(style.color))
        layer.lineOpacity = .constant(0.92)
        layer.lineWidth = .constant(3)
        layer.lineOffset = .constant(2)
        layer.slot = .top
        return layer
    }
}
