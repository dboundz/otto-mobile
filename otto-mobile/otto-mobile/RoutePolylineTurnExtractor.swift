import CoreLocation
import Foundation
import MapKit

/// Derives turn-like coordinates from a saved road polyline without re-calling directions on editor open.
enum RoutePolylineTurnExtractor {
    private static let minimumTurnAngleDegrees = 28.0
    private static let minimumArcSpacingMeters = 40.0
    private static let finishBufferMeters = RouteAutoPathPointGenerator.Options.finishBufferMeters

    static func turnCoordinates(along roadCoordinates: [CLLocationCoordinate2D]) -> [CLLocationCoordinate2D] {
        guard roadCoordinates.count >= 3 else { return [] }

        let totalLength = RouteMapGeometry.polylineTotalLength(roadCoordinates)
        guard totalLength > finishBufferMeters * 2 else { return [] }

        let sampleStride = max(1, roadCoordinates.count / 500)
        var cumulative: [Double] = [0]
        cumulative.reserveCapacity(roadCoordinates.count)
        for segmentIndex in 0..<(roadCoordinates.count - 1) {
            let start = MKMapPoint(roadCoordinates[segmentIndex])
            let end = MKMapPoint(roadCoordinates[segmentIndex + 1])
            cumulative.append(cumulative[segmentIndex] + start.distance(to: end))
        }

        var results: [CLLocationCoordinate2D] = []
        var lastTurnArcLength = -Double.greatestFiniteMagnitude
        var index = sampleStride

        while index < roadCoordinates.count - sampleStride {
            let previous = roadCoordinates[index - sampleStride]
            let current = roadCoordinates[index]
            let next = roadCoordinates[index + sampleStride]

            let incoming = RouteMapGeometry.bearingBetween(from: previous, to: current)
            let outgoing = RouteMapGeometry.bearingBetween(from: current, to: next)
            let turnAngle = normalizedAngleDelta(from: incoming, to: outgoing)

            if turnAngle >= minimumTurnAngleDegrees {
                let arcLength = cumulative[index]
                if arcLength > finishBufferMeters,
                   arcLength < totalLength - finishBufferMeters,
                   arcLength - lastTurnArcLength >= minimumArcSpacingMeters {
                    results.append(current)
                    lastTurnArcLength = arcLength
                }
            }

            index += sampleStride
        }

        return results
    }

    private static func normalizedAngleDelta(from startDegrees: Double, to endDegrees: Double) -> Double {
        var delta = abs(endDegrees - startDegrees).truncatingRemainder(dividingBy: 360)
        if delta > 180 { delta = 360 - delta }
        return delta
    }
}
