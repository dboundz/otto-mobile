import CoreLocation
import MapboxMaps

struct RouteBuilderLineRenderState: Equatable {
    let fingerprint: String
    let coordinates: [CLLocationCoordinate2D]
}

@MainActor
final class RouteBuilderImperativeRouteLineController {
    static let sourceID = "route-builder-line"
    private static let glowLayerID = "\(sourceID)-glow"
    private static let mainLayerID = "\(sourceID)-main"
    private static let coreLayerID = "\(sourceID)-core"

    private weak var mapboxMap: MapboxMap?
    private var isInstalled = false
    private var lastAppliedFingerprint: String?

    func attach(map: MapboxMap) {
        mapboxMap = map
        installIfNeeded(on: map)
    }

    func update(_ state: RouteBuilderLineRenderState, diagnostics: RouteBuilderPerfDiagnostics?) {
        guard let map = mapboxMap else { return }
        installIfNeeded(on: map)
        guard isInstalled else { return }

        guard state.fingerprint != lastAppliedFingerprint else { return }

        guard state.coordinates.count >= 2 else {
            clearLine(on: map)
            return
        }

        let feature = Feature(geometry: Geometry(LineString(state.coordinates)))
        do {
            try map.updateGeoJSONSource(withId: Self.sourceID, geoJSON: .feature(feature))
        } catch {
            // Style/source may not be ready yet; retry on next map-loaded or fingerprint change.
            return
        }
        lastAppliedFingerprint = state.fingerprint
        diagnostics?.recordMapLineBuild(pointCount: state.coordinates.count)
        OttoRouteBuilderDebugLog.routeLineUpdated(
            pointCount: state.coordinates.count,
            fingerprint: state.fingerprint
        )
    }

    private func installIfNeeded(on map: MapboxMap) {
        guard !isInstalled else { return }

        var source = GeoJSONSource(id: Self.sourceID)
        source.data = .feature(Feature(geometry: Geometry(LineString([]))))

        let palette = RouteMapLinePalette.livePurple
        let glow = RouteMapLineLayers.glowLayer(
            sourceID: Self.sourceID,
            layerID: Self.glowLayerID,
            palette: palette
        )
        let main = RouteMapLineLayers.mainLayer(
            sourceID: Self.sourceID,
            layerID: Self.mainLayerID,
            palette: palette
        )
        let core = RouteMapLineLayers.coreLayer(
            sourceID: Self.sourceID,
            layerID: Self.coreLayerID,
            palette: palette
        )

        do {
            try map.addSource(source)
            try map.addLayer(glow)
            try map.addLayer(main)
            try map.addLayer(core)
            isInstalled = true
        } catch {
            // Style may not be ready yet; retries on next line update.
        }
    }

    private func clearLine(on map: MapboxMap) {
        let empty = Feature(geometry: Geometry(LineString([])))
        map.updateGeoJSONSource(withId: Self.sourceID, geoJSON: .feature(empty))
        lastAppliedFingerprint = nil
    }
}
