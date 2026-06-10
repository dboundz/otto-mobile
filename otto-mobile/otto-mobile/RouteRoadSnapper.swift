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

// MARK: - Mapbox Directions (turn-by-turn route drive)

enum TurnByTurnRouteServiceError: LocalizedError {
    case insufficientWaypoints
    case missingAccessToken
    case invalidResponse
    case noRoutes
    case httpStatus(Int)

    var errorDescription: String? {
        switch self {
        case .insufficientWaypoints:
            return "At least two route points are required."
        case .missingAccessToken:
            return "Mapbox access token is missing."
        case .invalidResponse:
            return "Directions response was invalid."
        case .noRoutes:
            return "No driving route was found."
        case .httpStatus(let code):
            return "Directions request failed (\(code))."
        }
    }
}

struct TurnByTurnRouteService: NavigationRouteProviding {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchRoute(waypoints: [CLLocationCoordinate2D]) async throws -> NavigationRoute {
        guard waypoints.count >= 2 else { throw TurnByTurnRouteServiceError.insufficientWaypoints }
        guard let token = MapboxAccessToken.current, !token.isEmpty else {
            throw TurnByTurnRouteServiceError.missingAccessToken
        }

        let coordinatePath = waypoints
            .map { String(format: "%.6f,%.6f", $0.longitude, $0.latitude) }
            .joined(separator: ";")
        var components = URLComponents(string: "https://api.mapbox.com/directions/v5/mapbox/driving/\(coordinatePath)")!
        components.queryItems = [
            URLQueryItem(name: "geometries", value: "geojson"),
            URLQueryItem(name: "overview", value: "full"),
            URLQueryItem(name: "steps", value: "true"),
            URLQueryItem(name: "voice_instructions", value: "true"),
            URLQueryItem(name: "banner_instructions", value: "true"),
            URLQueryItem(name: "voice_units", value: "imperial"),
            URLQueryItem(name: "annotations", value: "distance,duration"),
            URLQueryItem(name: "access_token", value: token),
        ]

        guard let url = components.url else { throw TurnByTurnRouteServiceError.invalidResponse }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw TurnByTurnRouteServiceError.httpStatus(http.statusCode)
        }
        return try Self.parseResponse(data: data, fallbackFinish: waypoints.last!)
    }

    static func parseResponse(data: Data, fallbackFinish: CLLocationCoordinate2D) throws -> NavigationRoute {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let routes = json["routes"] as? [[String: Any]],
              let route = routes.first else {
            throw TurnByTurnRouteServiceError.noRoutes
        }

        let totalDistance = route["distance"] as? Double ?? 0
        let totalDuration = route["duration"] as? Double ?? 0
        let geometry = route["geometry"] as? [String: Any]
        let coordinates = parseGeoJSONCoordinates(geometry?["coordinates"] as? [[Double]])
        guard coordinates.count >= 2 else { throw TurnByTurnRouteServiceError.invalidResponse }

        let polylineIndex = RoutePolylineIndex(lineCoordinates: coordinates)
        let legsJSON = route["legs"] as? [[String: Any]] ?? []
        var legs: [NavigationLeg] = []
        for legJSON in legsJSON {
            let legDistance = legJSON["distance"] as? Double ?? 0
            let legDuration = legJSON["duration"] as? Double ?? 0
            let stepsJSON = legJSON["steps"] as? [[String: Any]] ?? []
            let steps = stepsJSON.compactMap { parseStep($0, polylineIndex: polylineIndex) }
            legs.append(NavigationLeg(steps: steps, distanceMeters: legDistance, durationSeconds: legDuration))
        }
        legs = NavigationInstructionLabeling.relabelLegsForStopPoints(legs)

        return NavigationRoute(
            coordinates: coordinates,
            legs: legs,
            totalDistanceMeters: totalDistance,
            totalDurationSeconds: totalDuration,
            finishCoordinate: coordinates.last ?? fallbackFinish
        )
    }

    private static func parseStep(
        _ stepJSON: [String: Any],
        polylineIndex: RoutePolylineIndex
    ) -> NavigationStep? {
        let distance = stepJSON["distance"] as? Double ?? 0
        let duration = stepJSON["duration"] as? Double ?? 0
        let name = stepJSON["name"] as? String
        let maneuverJSON = stepJSON["maneuver"] as? [String: Any] ?? [:]
        let maneuverType = maneuverJSON["type"] as? String ?? "turn"
        let maneuverModifier = maneuverJSON["modifier"] as? String
        let maneuverInstruction = maneuverJSON["instruction"] as? String ?? "Continue"
        let geometry = stepJSON["geometry"] as? [String: Any]
        let geometryCoordinates = parseGeoJSONCoordinates(geometry?["coordinates"] as? [[Double]])

        let maneuverCoordinate: CLLocationCoordinate2D
        if let location = maneuverJSON["location"] as? [Double], location.count >= 2 {
            maneuverCoordinate = CLLocationCoordinate2D(latitude: location[1], longitude: location[0])
        } else if let first = geometryCoordinates.first {
            maneuverCoordinate = first
        } else {
            return nil
        }

        let maneuverArcLength = polylineIndex.projectOntoPolyline(maneuverCoordinate)?.arcLengthMeters ?? 0
        let voiceJSON = stepJSON["voiceInstructions"] as? [[String: Any]] ?? []
        let voiceInstructions = voiceJSON.compactMap { entry -> NavigationVoiceInstruction? in
            guard let announcement = entry["announcement"] as? String else { return nil }
            return NavigationVoiceInstruction(
                distanceAlongStepMeters: entry["distanceAlongGeometry"] as? Double ?? 0,
                announcement: NavigationSSMLCleaner.plainText(from: announcement)
            )
        }
        let instruction = NavigationSSMLCleaner.plainText(from: maneuverInstruction)
        return NavigationStep(
            instruction: instruction,
            name: name?.isEmpty == false ? name : nil,
            distanceMeters: distance,
            durationSeconds: duration,
            maneuver: NavigationManeuver(type: maneuverType, modifier: maneuverModifier, instruction: instruction),
            maneuverCoordinate: maneuverCoordinate,
            voiceInstructions: voiceInstructions,
            geometryCoordinates: geometryCoordinates,
            maneuverArcLengthMeters: maneuverArcLength
        )
    }

    private static func parseGeoJSONCoordinates(_ raw: [[Double]]?) -> [CLLocationCoordinate2D] {
        (raw ?? []).compactMap { pair in
            guard pair.count >= 2 else { return nil }
            let coordinate = CLLocationCoordinate2D(latitude: pair[1], longitude: pair[0])
            guard CLLocationCoordinate2DIsValid(coordinate), pair[0].isFinite, pair[1].isFinite else { return nil }
            return coordinate
        }
    }
}
