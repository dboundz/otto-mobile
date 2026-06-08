import CoreLocation
import Foundation
import MapKit

struct RouteCheckpointDetectionResult {
    let newlyTriggeredIndexes: [Int]
    let completedIndexes: Set<Int>
    let currentProgress: Double
    let lastTriggeredWaypointIndex: Int?
    let didTriggerFinalWaypoint: Bool
    let nearestRouteIndex: Int?
    let updatedRouteProgressMeters: Double?
}

struct CheckpointRouteContext: Equatable {
    let arcLengthMeters: Double
    let segmentBearingDegrees: Double
}

enum RouteCheckpointDetector {
    static func routeCheckpointTotal(pointCount: Int) -> Int {
        max(pointCount, 1)
    }

    static func evaluate(
        routePoints: [RoutePointDTO],
        roadCoordinates: [CLLocationCoordinate2D],
        location: CLLocation,
        previousLocation: CLLocation?,
        speedMetersPerSecond: Double,
        completedIndexes: Set<Int>,
        lastRouteProgressMeters: Double? = nil
    ) -> RouteCheckpointDetectionResult {
        let coordinates = routePoints.compactMap { point -> CLLocationCoordinate2D? in
            let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
            guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { return nil }
            return coordinate
        }
        guard !coordinates.isEmpty else {
            return RouteCheckpointDetectionResult(
                newlyTriggeredIndexes: [],
                completedIndexes: completedIndexes,
                currentProgress: 0,
                lastTriggeredWaypointIndex: completedIndexes.max(),
                didTriggerFinalWaypoint: false,
                nearestRouteIndex: nil,
                updatedRouteProgressMeters: lastRouteProgressMeters
            )
        }

        let usesRouteProgress = roadCoordinates.count >= 2
        let checkpointContexts = usesRouteProgress
            ? checkpointRouteContexts(routeCoordinates: coordinates, roadCoordinates: roadCoordinates)
            : [:]
        let driverProgress = usesRouteProgress
            ? driverRouteProgress(
                location: location.coordinate,
                roadCoordinates: roadCoordinates,
                lastRouteProgressMeters: lastRouteProgressMeters
            )
            : nil

        let nextIndex = coordinates.indices.first { !completedIndexes.contains($0) }
        var updatedCompleted = completedIndexes
        var newlyTriggered: [Int] = []

        if let nextIndex, shouldTriggerCheckpoint(
            index: nextIndex,
            coordinates: coordinates,
            location: location,
            previousLocation: previousLocation,
            speedMetersPerSecond: speedMetersPerSecond,
            completedIndexes: completedIndexes,
            checkpointContexts: checkpointContexts,
            driverProgressMeters: driverProgress
        ) {
            updatedCompleted.insert(nextIndex)
            newlyTriggered.append(nextIndex)
        }

        let progress = Double(updatedCompleted.count) / Double(coordinates.count)
        let finalIndex = coordinates.count - 1
        let nearestIndex = driverProgress.flatMap {
            nearestRouteIndex(on: roadCoordinates, arcLengthMeters: $0)
        } ?? nearestRouteIndex(on: roadCoordinates, from: location.coordinate)

        return RouteCheckpointDetectionResult(
            newlyTriggeredIndexes: newlyTriggered,
            completedIndexes: updatedCompleted,
            currentProgress: min(1, max(0, progress)),
            lastTriggeredWaypointIndex: newlyTriggered.last ?? updatedCompleted.max(),
            didTriggerFinalWaypoint: newlyTriggered.contains(finalIndex),
            nearestRouteIndex: nearestIndex,
            updatedRouteProgressMeters: driverProgress ?? lastRouteProgressMeters
        )
    }

    static func indicatesDriveMovement(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode
    ) -> Bool {
        if movementMode == .driving { return true }
        if speedMetersPerSecond >= 2.7 { return true } // about 6 mph
        if location.speed >= 2.7 { return true }
        return false
    }

    static func checkpointRouteContexts(
        routeCoordinates: [CLLocationCoordinate2D],
        roadCoordinates: [CLLocationCoordinate2D]
    ) -> [Int: CheckpointRouteContext] {
        guard roadCoordinates.count >= 2 else { return [:] }
        let totalLength = RouteMapGeometry.polylineTotalLength(roadCoordinates)
        var rawArcLengths: [Int: Double] = [:]
        for (index, coordinate) in routeCoordinates.enumerated() {
            if let projection = RouteMapGeometry.projectOntoPolyline(coordinate, onto: roadCoordinates) {
                rawArcLengths[index] = projection.arcLengthMeters
            }
        }

        var contexts: [Int: CheckpointRouteContext] = [:]
        for index in routeCoordinates.indices {
            let lowerBound = index > 0 ? (contexts[index - 1]?.arcLengthMeters ?? rawArcLengths[index - 1] ?? 0) : 0
            var upperBound = index < routeCoordinates.count - 1
                ? (rawArcLengths[index + 1] ?? totalLength)
                : totalLength
            if upperBound < lowerBound {
                upperBound = totalLength
            }
            let projections = RouteMapGeometry.allProjectionsOntoPolyline(
                routeCoordinates[index],
                lineCoordinates: roadCoordinates
            )
            let constrained = projections.filter {
                $0.arcLengthMeters >= lowerBound - 1 && $0.arcLengthMeters <= upperBound + 1
            }
            let chosen = chooseCheckpointProjection(
                from: constrained,
                coordinate: routeCoordinates[index],
                lineCoordinates: roadCoordinates,
                lowerBound: lowerBound,
                index: index,
                isFinalCheckpoint: index == routeCoordinates.count - 1
            )
            if let chosen {
                contexts[index] = CheckpointRouteContext(
                    arcLengthMeters: chosen.arcLengthMeters,
                    segmentBearingDegrees: chosen.segmentBearingDegrees
                )
            }
        }
        return contexts
    }

    static func driverRouteProgress(
        location: CLLocationCoordinate2D,
        roadCoordinates: [CLLocationCoordinate2D],
        lastRouteProgressMeters: Double?
    ) -> Double? {
        guard roadCoordinates.count >= 2 else { return nil }
        let projections = RouteMapGeometry.allProjectionsOntoPolyline(location, lineCoordinates: roadCoordinates)
        guard !projections.isEmpty else { return lastRouteProgressMeters }

        guard let lastRouteProgressMeters else {
            return projections.min(by: { $0.distanceMeters < $1.distanceMeters })?.arcLengthMeters
        }

        let windowBack: Double = 50
        let windowForward: Double = 350
        let inWindow = projections.filter {
            $0.arcLengthMeters >= lastRouteProgressMeters - windowBack
                && $0.arcLengthMeters <= lastRouteProgressMeters + windowForward
        }
        let globalBest = projections.min(by: { $0.distanceMeters < $1.distanceMeters })

        let chosenArc: Double
        if let globalBest {
            if let inWindowBest = inWindow.min(by: {
                if abs($0.distanceMeters - $1.distanceMeters) > 1 {
                    return $0.distanceMeters < $1.distanceMeters
                }
                return abs($0.arcLengthMeters - lastRouteProgressMeters) < abs($1.arcLengthMeters - lastRouteProgressMeters)
            }), inWindowBest.distanceMeters <= globalBest.distanceMeters + 25 {
                chosenArc = inWindowBest.arcLengthMeters
            } else if globalBest.arcLengthMeters >= lastRouteProgressMeters - 30 {
                chosenArc = globalBest.arcLengthMeters
            } else {
                chosenArc = lastRouteProgressMeters
            }
        } else {
            chosenArc = lastRouteProgressMeters
        }

        return max(lastRouteProgressMeters - 30, chosenArc)
    }

    private static func chooseCheckpointProjection(
        from constrained: [RoutePolylineProjection],
        coordinate: CLLocationCoordinate2D,
        lineCoordinates: [CLLocationCoordinate2D],
        lowerBound: Double,
        index: Int,
        isFinalCheckpoint: Bool
    ) -> RoutePolylineProjection? {
        guard !constrained.isEmpty else {
            return RouteMapGeometry.projectOntoPolyline(coordinate, onto: lineCoordinates)
        }
        let bestDistance = constrained.map(\.distanceMeters).min() ?? 0
        let closestCandidates = constrained.filter { $0.distanceMeters <= bestDistance + 0.001 }
        if closestCandidates.count == 1 {
            return closestCandidates[0]
        }
        if isFinalCheckpoint {
            let advancedCandidates = closestCandidates.filter { $0.arcLengthMeters > lowerBound + 10 }
            return advancedCandidates.max(by: { $0.arcLengthMeters < $1.arcLengthMeters })
                ?? closestCandidates.max(by: { $0.arcLengthMeters < $1.arcLengthMeters })
        }
        if index == 0 || lowerBound <= 0 {
            return closestCandidates.min(by: { $0.arcLengthMeters < $1.arcLengthMeters })
        }
        let advancedCandidates = closestCandidates.filter { $0.arcLengthMeters > lowerBound + 10 }
        return advancedCandidates.max(by: { $0.arcLengthMeters < $1.arcLengthMeters })
            ?? closestCandidates.max(by: { $0.arcLengthMeters < $1.arcLengthMeters })
    }

    static func shouldTriggerCheckpoint(
        index: Int,
        coordinates: [CLLocationCoordinate2D],
        location: CLLocation,
        previousLocation: CLLocation?,
        speedMetersPerSecond: Double,
        completedIndexes: Set<Int>,
        checkpointContexts: [Int: CheckpointRouteContext] = [:],
        driverProgressMeters: Double? = nil
    ) -> Bool {
        let coordinate = coordinates[index]
        let checkpoint = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        let currentDistance = location.distance(from: checkpoint)
        let radius = triggerRadiusMeters(location: location, speedMetersPerSecond: speedMetersPerSecond)
        let segmentDistance = previousLocation.map {
            distanceFromCheckpoint(
                checkpoint: checkpoint,
                previousLocation: $0,
                currentLocation: location
            )
        } ?? currentDistance
        let closestDistance = min(currentDistance, segmentDistance)
        guard closestDistance <= radius else {
            return false
        }

        guard index > 0, let previousIndex = completedIndexes.max(), previousIndex == index - 1 else {
            return index == 0
        }
        let previousCoordinate = coordinates[previousIndex]
        let previous = CLLocation(latitude: previousCoordinate.latitude, longitude: previousCoordinate.longitude)
        let distanceFromPrevious = location.distance(from: previous)
        guard closestDistance <= distanceFromPrevious + gpsForgivenessMeters(location: location) else {
            return false
        }

        guard let checkpointContext = checkpointContexts[index], let driverProgressMeters else {
            return true
        }

        let forwardTolerance = progressForwardToleranceMeters(location: location, speedMetersPerSecond: speedMetersPerSecond)
        let overshootTolerance = progressOvershootToleranceMeters(location: location, speedMetersPerSecond: speedMetersPerSecond)
        guard driverProgressMeters >= checkpointContext.arcLengthMeters - forwardTolerance,
              driverProgressMeters <= checkpointContext.arcLengthMeters + overshootTolerance else {
            return false
        }

        guard shouldMatchTravelDirection(
            location: location,
            previousLocation: previousLocation,
            speedMetersPerSecond: speedMetersPerSecond,
            expectedBearingDegrees: checkpointContext.segmentBearingDegrees
        ) else {
            return false
        }

        return true
    }

    static func angularDifferenceDegrees(_ lhs: Double, _ rhs: Double) -> Double {
        let delta = abs(lhs - rhs).truncatingRemainder(dividingBy: 360)
        return delta > 180 ? 360 - delta : delta
    }

    private static func shouldMatchTravelDirection(
        location: CLLocation,
        previousLocation: CLLocation?,
        speedMetersPerSecond: Double,
        expectedBearingDegrees: Double
    ) -> Bool {
        let effectiveSpeed = max(speedMetersPerSecond, location.speed, 0)
        guard effectiveSpeed >= 1.5 else { return true }

        let travelBearing: Double?
        if let previousLocation {
            let delta = previousLocation.distance(from: location)
            if delta >= 5 {
                travelBearing = RouteMapGeometry.bearingBetween(
                    from: previousLocation.coordinate,
                    to: location.coordinate
                )
            } else {
                travelBearing = location.course >= 0 ? location.course : nil
            }
        } else if location.course >= 0, effectiveSpeed >= 2.7 {
            travelBearing = location.course
        } else {
            travelBearing = nil
        }

        guard let travelBearing else { return true }
        return angularDifferenceDegrees(travelBearing, expectedBearingDegrees) <= 90
    }

    private static func distanceFromCheckpoint(
        checkpoint: CLLocation,
        previousLocation: CLLocation,
        currentLocation: CLLocation
    ) -> CLLocationDistance {
        let checkpointPoint = MKMapPoint(checkpoint.coordinate)
        let previousPoint = MKMapPoint(previousLocation.coordinate)
        let currentPoint = MKMapPoint(currentLocation.coordinate)
        let abx = currentPoint.x - previousPoint.x
        let aby = currentPoint.y - previousPoint.y
        let apx = checkpointPoint.x - previousPoint.x
        let apy = checkpointPoint.y - previousPoint.y
        let ab2 = abx * abx + aby * aby
        guard ab2 > 0 else {
            return checkpoint.distance(from: currentLocation)
        }
        let t = max(0, min(1, (apx * abx + apy * aby) / ab2))
        let closest = MKMapPoint(x: previousPoint.x + abx * t, y: previousPoint.y + aby * t)
        return checkpointPoint.distance(to: closest)
    }

    private static func triggerRadiusMeters(location: CLLocation, speedMetersPerSecond: Double) -> CLLocationDistance {
        let accuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : 25
        let speedAllowance = max(speedMetersPerSecond, location.speed, 0) * 1.8
        return min(125, max(35, 35 + accuracy + speedAllowance))
    }

    private static func gpsForgivenessMeters(location: CLLocation) -> CLLocationDistance {
        let accuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : 25
        return min(80, max(25, accuracy * 1.5))
    }

    private static func progressForwardToleranceMeters(
        location: CLLocation,
        speedMetersPerSecond: Double
    ) -> CLLocationDistance {
        let accuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : 25
        let speedAllowance = max(speedMetersPerSecond, location.speed, 0) * 0.8
        return min(120, max(40, 40 + accuracy + speedAllowance))
    }

    private static func progressOvershootToleranceMeters(
        location: CLLocation,
        speedMetersPerSecond: Double
    ) -> CLLocationDistance {
        let accuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : 25
        let speedAllowance = max(speedMetersPerSecond, location.speed, 0) * 1.2
        return min(200, max(80, 80 + accuracy + speedAllowance))
    }

    private static func nearestRouteIndex(
        on roadCoordinates: [CLLocationCoordinate2D],
        from coordinate: CLLocationCoordinate2D
    ) -> Int? {
        guard !roadCoordinates.isEmpty else { return nil }
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        var bestIndex = 0
        var bestDistance = CLLocationDistance.greatestFiniteMagnitude
        for (index, routeCoordinate) in roadCoordinates.enumerated() {
            let routeLocation = CLLocation(latitude: routeCoordinate.latitude, longitude: routeCoordinate.longitude)
            let distance = location.distance(from: routeLocation)
            if distance < bestDistance {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private static func nearestRouteIndex(
        on roadCoordinates: [CLLocationCoordinate2D],
        arcLengthMeters: Double
    ) -> Int? {
        guard roadCoordinates.count >= 2 else { return nil }
        var cumulativeDistance: CLLocationDistance = 0
        for index in 0..<(roadCoordinates.count - 1) {
            let start = MKMapPoint(roadCoordinates[index])
            let end = MKMapPoint(roadCoordinates[index + 1])
            let segmentLength = start.distance(to: end)
            if arcLengthMeters <= cumulativeDistance + segmentLength {
                return index
            }
            cumulativeDistance += segmentLength
        }
        return roadCoordinates.count - 1
    }
}
