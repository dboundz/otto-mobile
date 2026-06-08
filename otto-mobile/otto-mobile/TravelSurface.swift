import Combine
import CoreLocation
import Foundation
import MapboxMaps

enum TravelSurface: Equatable {
    case land
    case water
}

enum MapTravelSurfaceSampler {
    /// Boat-on-water chips on map presence markers. Disabled: each visible moving sharer polled
    /// Mapbox Tilequery every 2s per device (expensive; cosmetic only). Route Builder road snap is separate.
    static let waterSurfaceDetectionEnabled = false

    static let minSpeedMphForBoat = 2.0
    static let confirmationDuration: TimeInterval = 4.0
    static let sampleThrottle: TimeInterval = 2.0

    static func instantaneousSurface(speedMph: Double, onWater: Bool, onRoad: Bool) -> TravelSurface {
        guard speedMph >= minSpeedMphForBoat else { return .land }
        guard onWater, !onRoad else { return .land }
        return .water
    }

    static func classify(features: [QueriedRenderedFeature]) -> (onWater: Bool, onRoad: Bool) {
        var onWater = false
        var onRoad = false
        for feature in features {
            let sourceLayer = feature.queriedFeature.sourceLayer?.lowercased() ?? ""
            for layerId in feature.layers {
                let normalizedLayer = layerId.lowercased()
                if isWaterFeature(sourceLayer: sourceLayer, layerId: normalizedLayer) {
                    onWater = true
                }
                if isRoadFeature(sourceLayer: sourceLayer, layerId: normalizedLayer) {
                    onRoad = true
                }
            }
            if feature.layers.isEmpty {
                if isWaterFeature(sourceLayer: sourceLayer, layerId: "") {
                    onWater = true
                }
                if isRoadFeature(sourceLayer: sourceLayer, layerId: "") {
                    onRoad = true
                }
            }
        }
        return (onWater, onRoad)
    }

    private static func isWaterFeature(sourceLayer: String, layerId: String) -> Bool {
        isWaterLayer(sourceLayer) || isWaterLayer(layerId)
    }

    private static func isRoadFeature(sourceLayer: String, layerId: String) -> Bool {
        isRoadLayer(sourceLayer) || isRoadLayer(layerId)
    }

    static func isWaterLayer(_ layer: String) -> Bool {
        let tokens = ["water", "waterway", "lake", "ocean", "river"]
        return tokens.contains { layer.contains($0) }
    }

    static func isRoadLayer(_ layer: String) -> Bool {
        let tokens = ["road", "bridge", "highway", "street", "motorway", "trunk", "transportation"]
        return tokens.contains { layer.contains($0) }
    }

    static func sample(
        mapboxMap: MapboxMap,
        coordinate: CLLocationCoordinate2D,
        speedMph: Double,
        completion: @escaping (TravelSurface) -> Void
    ) {
        guard waterSurfaceDetectionEnabled else {
            completion(.land)
            return
        }
        // Mapbox Standard (`.standard`) does not expose basemap layers to `queryRenderedFeatures`.
        // Use Tilequery against streets data so water/road checks work at any zoom/style.
        _ = mapboxMap
        Task {
            let features = await MapboxTilequeryClient.fetchFeatures(at: coordinate) ?? []
            let classified = MapboxTilequeryClient.classifySurface(features: features)
            let instantaneous = MapTravelSurfaceSampler.instantaneousSurface(
                speedMph: speedMph,
                onWater: classified.onWater,
                onRoad: classified.onRoad
            )
            await MainActor.run {
                completion(instantaneous)
            }
        }
    }
}

@MainActor
final class TravelSurfaceHysteresis {
    private(set) var displayed: TravelSurface = .land
    private var pending: TravelSurface?
    private var pendingSince: Date?

    func reset() {
        displayed = .land
        pending = nil
        pendingSince = nil
    }

    func apply(instantaneous: TravelSurface, now: Date = Date()) {
        if instantaneous == displayed {
            pending = nil
            pendingSince = nil
            return
        }
        if pending == instantaneous {
            guard let since = pendingSince else { return }
            if now.timeIntervalSince(since) >= MapTravelSurfaceSampler.confirmationDuration {
                displayed = instantaneous
                pending = nil
                pendingSince = nil
            }
        } else {
            pending = instantaneous
            pendingSince = now
        }
    }
}

@MainActor
final class TravelSurfaceTracker: ObservableObject {
    @Published private(set) var surfacesByUserID: [String: TravelSurface] = [:]
    private var controllers: [String: TravelSurfaceHysteresis] = [:]
    private var lastSampleAt: [String: Date] = [:]

    func surface(for userID: String) -> TravelSurface {
        surfacesByUserID[userID] ?? .land
    }

    func removeUsers(notIn ids: Set<String>) {
        controllers = controllers.filter { ids.contains($0.key) }
        surfacesByUserID = surfacesByUserID.filter { ids.contains($0.key) }
        lastSampleAt = lastSampleAt.filter { ids.contains($0.key) }
    }

    func ingest(userID: String, instantaneous: TravelSurface, now: Date = Date()) {
        let controller: TravelSurfaceHysteresis
        if let existing = controllers[userID] {
            controller = existing
        } else {
            let created = TravelSurfaceHysteresis()
            controllers[userID] = created
            controller = created
        }
        controller.apply(instantaneous: instantaneous, now: now)
        surfacesByUserID[userID] = controller.displayed
    }

    func shouldSample(userID: String, now: Date = Date()) -> Bool {
        guard let last = lastSampleAt[userID] else { return true }
        return now.timeIntervalSince(last) >= MapTravelSurfaceSampler.sampleThrottle
    }

    func markSampled(userID: String, now: Date = Date()) {
        lastSampleAt[userID] = now
    }
}
