import CoreLocation
import MapKit

enum RouteRoadSnapper {
    struct Result {
        let coordinates: [CLLocationCoordinate2D]
        let distanceMeters: Double
        let travelTimeSeconds: TimeInterval
        let didSnapToRoad: Bool
        let turnManeuverCoordinates: [CLLocationCoordinate2D]
    }

    private static let minimumTurnStepMeters = 25.0

    static func buildRoute(for points: [CLLocationCoordinate2D]) async -> Result {
        guard points.count >= 2 else {
            return Result(
                coordinates: points,
                distanceMeters: 0,
                travelTimeSeconds: 0,
                didSnapToRoad: false,
                turnManeuverCoordinates: []
            )
        }

        var allCoordinates: [CLLocationCoordinate2D] = []
        var allTurnCoordinates: [CLLocationCoordinate2D] = []
        var totalMeters: Double = 0
        var totalSeconds: TimeInterval = 0
        var didSnapAllSegments = true

        for pair in zip(points, points.dropFirst()) {
            let segment = await routeSegment(from: pair.0, to: pair.1)
            if allCoordinates.isEmpty {
                allCoordinates.append(contentsOf: segment.coordinates)
            } else {
                allCoordinates.append(contentsOf: segment.coordinates.dropFirst())
            }
            allTurnCoordinates.append(contentsOf: segment.turnManeuverCoordinates)
            totalMeters += segment.distanceMeters
            totalSeconds += segment.travelTimeSeconds
            didSnapAllSegments = didSnapAllSegments && segment.didSnapToRoad
        }

        return Result(
            coordinates: allCoordinates.isEmpty ? points : allCoordinates,
            distanceMeters: totalMeters,
            travelTimeSeconds: totalSeconds,
            didSnapToRoad: didSnapAllSegments,
            turnManeuverCoordinates: dedupeNearbyCoordinates(allTurnCoordinates, withinMeters: 40)
        )
    }

    private static func mapItem(for coordinate: CLLocationCoordinate2D) -> MKMapItem? {
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return MKMapItem(
            location: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude),
            address: nil
        )
    }

    private static func routeSegment(
        from start: CLLocationCoordinate2D,
        to end: CLLocationCoordinate2D
    ) async -> Result {
        guard let sourceItem = mapItem(for: start),
              let destinationItem = mapItem(for: end)
        else {
            return Result(
                coordinates: [start, end],
                distanceMeters: 0,
                travelTimeSeconds: 0,
                didSnapToRoad: false,
                turnManeuverCoordinates: []
            )
        }

        let request = MKDirections.Request()
        request.source = sourceItem
        request.destination = destinationItem
        request.transportType = .automobile
        request.requestsAlternateRoutes = false

        do {
            let response = try await MKDirections(request: request).calculate()
            guard let route = response.routes.first else {
                return Result(
                    coordinates: [start, end],
                    distanceMeters: 0,
                    travelTimeSeconds: 0,
                    didSnapToRoad: false,
                    turnManeuverCoordinates: []
                )
            }
            let polyline = route.polyline
            var coords = Array(repeating: CLLocationCoordinate2D(), count: polyline.pointCount)
            polyline.getCoordinates(&coords, range: NSRange(location: 0, length: polyline.pointCount))
            return Result(
                coordinates: coords.isEmpty ? [start, end] : coords,
                distanceMeters: route.distance,
                travelTimeSeconds: route.expectedTravelTime,
                didSnapToRoad: !coords.isEmpty,
                turnManeuverCoordinates: turnManeuverCoordinates(from: route)
            )
        } catch {
            return Result(
                coordinates: [start, end],
                distanceMeters: 0,
                travelTimeSeconds: 0,
                didSnapToRoad: false,
                turnManeuverCoordinates: []
            )
        }
    }

    private static func turnManeuverCoordinates(from route: MKRoute) -> [CLLocationCoordinate2D] {
        var coordinates: [CLLocationCoordinate2D] = []
        for (index, step) in route.steps.enumerated() {
            guard index > 0 else { continue }
            guard step.distance >= minimumTurnStepMeters else { continue }
            let instructions = step.instructions.lowercased()
            if instructions.contains("continue"), !instructions.contains("turn") {
                continue
            }
            guard step.polyline.pointCount > 0 else { continue }
            var coordinate = CLLocationCoordinate2D()
            step.polyline.getCoordinates(&coordinate, range: NSRange(location: 0, length: 1))
            guard CLLocationCoordinate2DIsValid(coordinate) else { continue }
            coordinates.append(coordinate)
        }
        return coordinates
    }

    private static func dedupeNearbyCoordinates(
        _ coordinates: [CLLocationCoordinate2D],
        withinMeters threshold: CLLocationDistance
    ) -> [CLLocationCoordinate2D] {
        var deduped: [CLLocationCoordinate2D] = []
        for coordinate in coordinates {
            let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
            let isDuplicate = deduped.contains { existing in
                let existingLocation = CLLocation(latitude: existing.latitude, longitude: existing.longitude)
                return location.distance(from: existingLocation) <= threshold
            }
            if !isDuplicate {
                deduped.append(coordinate)
            }
        }
        return deduped
    }
}
