import MapboxMaps
import UIKit

/// Live congestion overlay for the main Map tab (`mapbox-traffic-v1`).
@MainActor
enum MapboxTrafficLayerController {
    static let sourceID = "otto-map-traffic"
    private static let sourceURL = "mapbox://mapbox.mapbox-traffic-v1"
    private static let sourceLayer = "traffic"

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
        if showTraffic {
            install(on: map)
        } else {
            remove(from: map)
        }
    }

    private static func install(on map: MapboxMap) {
        remove(from: map)

        var source = VectorSource(id: sourceID)
        source.url = sourceURL

        do {
            try map.addSource(source)
            for style in styles {
                try map.addLayer(lineLayer(for: style))
            }
        } catch {
            // Style may not be ready yet; MapScreen retries on toggle and map ready.
        }
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
        layer.slot = .middle
        return layer
    }
}
