import Combine
import CoreLocation
import MapboxMaps
import UIKit

struct RouteBuilderNativePathDot: Equatable {
    let coordinate: CLLocationCoordinate2D
    let isAutoShape: Bool

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.isAutoShape == rhs.isAutoShape
            && lhs.coordinate.latitude == rhs.coordinate.latitude
            && lhs.coordinate.longitude == rhs.coordinate.longitude
    }
}

struct RouteBuilderNativePathDotsState: Equatable {
    let fingerprint: String
    let userDots: [CLLocationCoordinate2D]
    let autoDots: [CLLocationCoordinate2D]
}

@MainActor
final class RouteBuilderNativePathMarkersController {
    static let userSourceID = "route-builder-path-user-dots"
    static let autoSourceID = "route-builder-path-auto-dots"
    private static let userLayerID = "\(userSourceID)-layer"
    private static let autoLayerID = "\(autoSourceID)-layer"

    private static let userDotColor = StyleColor(UIColor.systemPurple)
    private static let autoDotColor = StyleColor(UIColor.systemPurple.withAlphaComponent(0.72))

    private weak var mapboxMap: MapboxMap?
    private var isInstalled = false
    private var lastAppliedFingerprint: String?

    func attach(map: MapboxMap) {
        mapboxMap = map
        installIfNeeded(on: map)
    }

    func update(_ state: RouteBuilderNativePathDotsState) {
        guard let map = mapboxMap else { return }
        installIfNeeded(on: map)

        guard state.fingerprint != lastAppliedFingerprint else { return }

        updateSource(id: Self.userSourceID, coordinates: state.userDots, on: map)
        updateSource(id: Self.autoSourceID, coordinates: state.autoDots, on: map)
        lastAppliedFingerprint = state.fingerprint
        OttoRouteBuilderDebugLog.pathDotsUpdated(
            userCount: state.userDots.count,
            autoCount: state.autoDots.count,
            fingerprint: state.fingerprint
        )
    }

    private func installIfNeeded(on map: MapboxMap) {
        guard !isInstalled else { return }

        let sources = [
            emptySource(id: Self.userSourceID),
            emptySource(id: Self.autoSourceID),
        ]
        let layers = [
            userPathDotLayer(),
            autoPathDotLayer(),
        ]

        do {
            for source in sources {
                try map.addSource(source)
            }
            for layer in layers {
                try map.addLayer(layer)
            }
            isInstalled = true
        } catch {
            // Style may not be ready yet; retries on next update.
        }
    }

    private func emptySource(id: String) -> GeoJSONSource {
        var source = GeoJSONSource(id: id)
        source.data = .featureCollection(FeatureCollection(features: []))
        return source
    }

    private func userPathDotLayer() -> CircleLayer {
        CircleLayer(id: Self.userLayerID, source: Self.userSourceID)
            .circleRadius(6)
            .circleColor(Self.userDotColor)
            .circleOpacity(1)
            .circleStrokeWidth(1.5)
            .circleStrokeColor(StyleColor(.white))
            .slot(.top)
    }

    private func autoPathDotLayer() -> CircleLayer {
        CircleLayer(id: Self.autoLayerID, source: Self.autoSourceID)
            .circleRadius(5)
            .circleColor(Self.autoDotColor)
            .circleOpacity(0.72)
            .circleStrokeWidth(1.5)
            .circleStrokeColor(StyleColor(.white.withAlphaComponent(0.92)))
            .slot(.top)
    }

    private func updateSource(id: String, coordinates: [CLLocationCoordinate2D], on map: MapboxMap) {
        let features = coordinates.map { coordinate in
            Feature(geometry: Geometry(Point(coordinate)))
        }
        map.updateGeoJSONSource(
            withId: id,
            geoJSON: .featureCollection(FeatureCollection(features: features))
        )
    }
}

@MainActor
final class RouteBuilderNativePathMarkersHolder: ObservableObject {
    let controller = RouteBuilderNativePathMarkersController()
}
