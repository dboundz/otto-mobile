import CoreLocation
import SwiftUI

struct RoutePreviewShape: Shape {
    let coordinates: [CLLocationCoordinate2D]

    func path(in rect: CGRect) -> Path {
        let points = Self.normalizedPoints(coordinates: coordinates, in: rect.size)
        var path = Path()
        guard let first = points.first else { return path }
        path.move(to: CGPoint(x: rect.minX + first.x, y: rect.minY + first.y))
        for point in points.dropFirst() {
            path.addLine(to: CGPoint(x: rect.minX + point.x, y: rect.minY + point.y))
        }
        return path
    }

    static func normalizedPoints(coordinates: [CLLocationCoordinate2D], in size: CGSize) -> [CGPoint] {
        let valid = coordinates.filter(Self.isValidCoordinate)
        guard valid.count >= 2 else { return fallbackPoints(in: size) }
        return normalizedPoints(coordinates: valid, in: size, normalizingAgainst: valid)
    }

    static func normalizedPoints(
        coordinates: [CLLocationCoordinate2D],
        in size: CGSize,
        normalizingAgainst referenceCoordinates: [CLLocationCoordinate2D]
    ) -> [CGPoint] {
        let valid = coordinates.filter(Self.isValidCoordinate)
        let reference = referenceCoordinates.filter(Self.isValidCoordinate)
        guard !valid.isEmpty else { return [] }
        guard reference.count >= 2 else { return fallbackPoints(in: size) }

        let minLat = reference.map(\.latitude).min() ?? 0
        let maxLat = reference.map(\.latitude).max() ?? 1
        let minLng = reference.map(\.longitude).min() ?? 0
        let maxLng = reference.map(\.longitude).max() ?? 1
        let latRange = max(maxLat - minLat, 0.000001)
        let lngRange = max(maxLng - minLng, 0.000001)

        return valid.map { coordinate in
            let x = (coordinate.longitude - minLng) / lngRange * size.width
            let y = (1 - (coordinate.latitude - minLat) / latRange) * size.height
            return CGPoint(x: x, y: y)
        }
    }

    private static func isValidCoordinate(_ coordinate: CLLocationCoordinate2D) -> Bool {
        CLLocationCoordinate2DIsValid(coordinate) && coordinate.latitude.isFinite && coordinate.longitude.isFinite
    }

    private static func fallbackPoints(in size: CGSize) -> [CGPoint] {
        [
            CGPoint(x: 0, y: size.height * 0.72),
            CGPoint(x: size.width * 0.34, y: size.height * 0.48),
            CGPoint(x: size.width * 0.68, y: size.height * 0.58),
            CGPoint(x: size.width, y: size.height * 0.28)
        ]
    }
}
