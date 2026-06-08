import CoreLocation
import Foundation

enum RouteAutoPathPointGenerator {
    enum Options {
        static let finishBufferMeters = 100.0
    }

    static func autoTurnPathCoordinates(
        turnCoordinates: [CLLocationCoordinate2D],
        roadCoordinates: [CLLocationCoordinate2D],
        polylineIndex: RoutePolylineIndex? = nil
    ) -> [CLLocationCoordinate2D] {
        guard roadCoordinates.count >= 2 else { return [] }

        let index = polylineIndex ?? RoutePolylineIndex(lineCoordinates: roadCoordinates)
        let totalLength = RouteMapGeometry.polylineTotalLength(roadCoordinates)
        let projected = turnCoordinates.compactMap { turnCoordinate -> (coordinate: CLLocationCoordinate2D, arcLength: Double)? in
            guard let projection = index.projectOntoPolyline(turnCoordinate) else { return nil }
            let arcLength = projection.arcLengthMeters
            guard arcLength > Options.finishBufferMeters,
                  arcLength < totalLength - Options.finishBufferMeters else {
                return nil
            }
            return (projection.coordinate, arcLength)
        }

        return projected
            .sorted { $0.arcLength < $1.arcLength }
            .map(\.coordinate)
    }
}
