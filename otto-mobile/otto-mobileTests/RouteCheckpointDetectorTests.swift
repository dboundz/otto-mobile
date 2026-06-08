import CoreLocation
import XCTest
@testable import otto_mobile

final class RouteCheckpointDetectorTests: XCTestCase {
    func testOutboundPassDoesNotTriggerReturnCheckpoint() {
        let road = outAndBackRoadCoordinates()
        let points = outAndBackRoutePoints()
        let outboundMidpoint = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date()
        )
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date().addingTimeInterval(-2)
        )
        let contexts = RouteCheckpointDetector.checkpointRouteContexts(
            routeCoordinates: routeCoordinates(from: points),
            roadCoordinates: road
        )
        let driverProgress = RouteCheckpointDetector.driverRouteProgress(
            location: outboundMidpoint.coordinate,
            roadCoordinates: road,
            lastRouteProgressMeters: 400
        )

        XCTAssertFalse(
            RouteCheckpointDetector.shouldTriggerCheckpoint(
                index: 2,
                coordinates: routeCoordinates(from: points),
                location: outboundMidpoint,
                previousLocation: previous,
                speedMetersPerSecond: 12,
                completedIndexes: [0, 1],
                checkpointContexts: contexts,
                driverProgressMeters: driverProgress
            )
        )
    }

    func testReturnPassTriggersReturnCheckpoint() {
        let road = outAndBackRoadCoordinates()
        let points = outAndBackRoutePoints()
        let returnMidpoint = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 180,
            speed: 12,
            timestamp: Date()
        )
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.015, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 180,
            speed: 12,
            timestamp: Date().addingTimeInterval(-2)
        )
        let contexts = RouteCheckpointDetector.checkpointRouteContexts(
            routeCoordinates: routeCoordinates(from: points),
            roadCoordinates: road
        )
        let outboundArc = contexts[1]?.arcLengthMeters ?? 0
        let returnArc = contexts[2]?.arcLengthMeters ?? 0
        XCTAssertGreaterThan(returnArc, outboundArc + 500)

        let driverProgress = RouteCheckpointDetector.driverRouteProgress(
            location: returnMidpoint.coordinate,
            roadCoordinates: road,
            lastRouteProgressMeters: returnArc - 100
        )

        XCTAssertTrue(
            RouteCheckpointDetector.shouldTriggerCheckpoint(
                index: 2,
                coordinates: routeCoordinates(from: points),
                location: returnMidpoint,
                previousLocation: previous,
                speedMetersPerSecond: 12,
                completedIndexes: [0, 1],
                checkpointContexts: contexts,
                driverProgressMeters: driverProgress
            )
        )
    }

    func testSimpleOneWayRouteStillTriggers() {
        let road = oneWayRoadCoordinates()
        let points = oneWayRoutePoints()
        let checkpointLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date()
        )
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date().addingTimeInterval(-2)
        )

        let result = RouteCheckpointDetector.evaluate(
            routePoints: points,
            roadCoordinates: road,
            location: checkpointLocation,
            previousLocation: previous,
            speedMetersPerSecond: 12,
            completedIndexes: [0],
            lastRouteProgressMeters: 400
        )

        XCTAssertEqual(result.newlyTriggeredIndexes, [1])
    }

    func testLowSpeedStartCheckpointStillTriggers() {
        let road = oneWayRoadCoordinates()
        let points = oneWayRoutePoints()
        let startLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: -1,
            speed: 0,
            timestamp: Date()
        )

        let result = RouteCheckpointDetector.evaluate(
            routePoints: points,
            roadCoordinates: road,
            location: startLocation,
            previousLocation: nil,
            speedMetersPerSecond: 0,
            completedIndexes: [],
            lastRouteProgressMeters: nil
        )

        XCTAssertEqual(result.newlyTriggeredIndexes, [0])
    }

    func testIdenticalCheckpointCoordinatesDisambiguateByProgressAndDirection() {
        let road = outAndBackRoadCoordinates()
        let points = outAndBackRoutePoints()
        let contexts = RouteCheckpointDetector.checkpointRouteContexts(
            routeCoordinates: routeCoordinates(from: points),
            roadCoordinates: road
        )
        let outboundArc = contexts[1]?.arcLengthMeters ?? 0
        let returnArc = contexts[2]?.arcLengthMeters ?? 0
        XCTAssertGreaterThan(returnArc, outboundArc + 500)
        let outboundBearing = contexts[1]?.segmentBearingDegrees ?? 0
        let returnBearing = contexts[2]?.segmentBearingDegrees ?? 0
        XCTAssertNotEqual(outboundBearing, returnBearing, accuracy: 1)

        let startArc = contexts[0]?.arcLengthMeters ?? 0
        let finishArc = contexts[3]?.arcLengthMeters ?? 0
        XCTAssertGreaterThan(finishArc, startArc + 500)
    }

    func testOutboundAtSharedStartFinishDoesNotTriggerFinish() {
        let road = outAndBackRoadCoordinates()
        let points = loopRoutePointsWithSharedStartFinish()
        let startLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date()
        )
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 0,
            speed: 12,
            timestamp: Date().addingTimeInterval(-2)
        )
        let contexts = RouteCheckpointDetector.checkpointRouteContexts(
            routeCoordinates: routeCoordinates(from: points),
            roadCoordinates: road
        )
        let driverProgress = RouteCheckpointDetector.driverRouteProgress(
            location: startLocation.coordinate,
            roadCoordinates: road,
            lastRouteProgressMeters: 0
        )

        XCTAssertFalse(
            RouteCheckpointDetector.shouldTriggerCheckpoint(
                index: 1,
                coordinates: routeCoordinates(from: points),
                location: startLocation,
                previousLocation: previous,
                speedMetersPerSecond: 12,
                completedIndexes: [0],
                checkpointContexts: contexts,
                driverProgressMeters: driverProgress
            )
        )
    }

    func testReturnAtSharedStartFinishTriggersFinish() {
        let road = outAndBackRoadCoordinates()
        let points = loopRoutePointsWithSharedStartFinish()
        let finishLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 180,
            speed: 12,
            timestamp: Date()
        )
        let previous = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            course: 180,
            speed: 12,
            timestamp: Date().addingTimeInterval(-2)
        )
        let contexts = RouteCheckpointDetector.checkpointRouteContexts(
            routeCoordinates: routeCoordinates(from: points),
            roadCoordinates: road
        )
        let finishArc = contexts[1]?.arcLengthMeters ?? 0
        let driverProgress = RouteCheckpointDetector.driverRouteProgress(
            location: finishLocation.coordinate,
            roadCoordinates: road,
            lastRouteProgressMeters: finishArc - 100
        )

        let result = RouteCheckpointDetector.evaluate(
            routePoints: points,
            roadCoordinates: road,
            location: finishLocation,
            previousLocation: previous,
            speedMetersPerSecond: 12,
            completedIndexes: [0],
            lastRouteProgressMeters: finishArc - 100
        )

        XCTAssertEqual(result.newlyTriggeredIndexes, [1])
        XCTAssertTrue(result.didTriggerFinalWaypoint)
    }

    private func routeCoordinates(from points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        points.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
    }

    private func outAndBackRoadCoordinates() -> [CLLocationCoordinate2D] {
        [
            CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.015, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.02, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.015, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
        ]
    }

    private func loopRoutePointsWithSharedStartFinish() -> [RoutePointDTO] {
        [
            RoutePointDTO(lat: 37.0, lng: -122.0, markerType: "start"),
            RoutePointDTO(lat: 37.0, lng: -122.0, markerType: "finish"),
        ]
    }

    private func outAndBackRoutePoints() -> [RoutePointDTO] {
        [
            RoutePointDTO(lat: 37.0, lng: -122.0, markerType: "start"),
            RoutePointDTO(lat: 37.01, lng: -122.0, markerType: "waypoint"),
            RoutePointDTO(lat: 37.01, lng: -122.0, markerType: "waypoint"),
            RoutePointDTO(lat: 37.0, lng: -122.0, markerType: "finish"),
        ]
    }

    private func oneWayRoadCoordinates() -> [CLLocationCoordinate2D] {
        [
            CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.005, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.01, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.015, longitude: -122.0),
            CLLocationCoordinate2D(latitude: 37.02, longitude: -122.0),
        ]
    }

    private func oneWayRoutePoints() -> [RoutePointDTO] {
        [
            RoutePointDTO(lat: 37.0, lng: -122.0, markerType: "start"),
            RoutePointDTO(lat: 37.01, lng: -122.0, markerType: "waypoint"),
            RoutePointDTO(lat: 37.02, lng: -122.0, markerType: "finish"),
        ]
    }
}
