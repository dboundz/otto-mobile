import XCTest
import CoreLocation
@testable import otto_mobile

final class MapDriveHorizonDepthTests: XCTestCase {
    func testHorizonScaleAtUserIsFullSize() {
        let scale = MapDriveHorizonDepth.horizonScale(
            distanceMeters: 0,
            visibleMapHeightMeters: 2_000
        )
        XCTAssertEqual(scale, 1, accuracy: 0.001)
    }

    func testHorizonScaleDecreasesWithDistance() {
        let near = MapDriveHorizonDepth.horizonScale(
            distanceMeters: 200,
            visibleMapHeightMeters: 2_000
        )
        let far = MapDriveHorizonDepth.horizonScale(
            distanceMeters: 1_800,
            visibleMapHeightMeters: 2_000
        )
        XCTAssertGreaterThan(near, far)
    }

    func testHorizonScaleIsMapProportional() {
        let range = 1_000.0
        let atHalf = MapDriveHorizonDepth.horizonScale(
            distanceMeters: range / 2,
            visibleMapHeightMeters: range
        )
        let atHalfDoubledRange = MapDriveHorizonDepth.horizonScale(
            distanceMeters: range,
            visibleMapHeightMeters: range * 2
        )
        XCTAssertEqual(atHalf, atHalfDoubledRange, accuracy: 0.001)
    }

    func testDriveOverlapPriorityNearerIsHigher() {
        let near = MapDriveHorizonDepth.driveRouteOverlapPriority(distanceMeters: 50, markerType: nil)
        let far = MapDriveHorizonDepth.driveRouteOverlapPriority(distanceMeters: 500, markerType: nil)
        XCTAssertGreaterThan(near, far)
    }

    func testEndpointBoostBeatsCheckpointAtSameDistance() {
        let checkpoint = MapDriveHorizonDepth.driveRouteOverlapPriority(
            distanceMeters: 100,
            markerType: "waypoint"
        )
        let finish = MapDriveHorizonDepth.driveRouteOverlapPriority(
            distanceMeters: 100,
            markerType: "finish"
        )
        XCTAssertGreaterThan(finish, checkpoint)
    }

    func testCheckpointWithinOneMileIsShown() {
        XCTAssertTrue(
            MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType: "waypoint",
                distanceMeters: 1_500
            )
        )
    }

    func testCheckpointBeyondOneMileIsHidden() {
        XCTAssertFalse(
            MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType: "waypoint",
                distanceMeters: MapDriveHorizonDepth.checkpointVisibleMaxDistanceMeters + 100
            )
        )
    }

    func testStartFinishAlwaysShownRegardlessOfDistance() {
        XCTAssertTrue(
            MapDriveHorizonDepth.shouldShowRouteMarker(
                markerType: "start",
                distanceMeters: 50_000
            )
        )
    }

    func testPresenceWithinOneMileIsShown() {
        XCTAssertTrue(MapDriveHorizonDepth.shouldShowPresenceMarker(distanceMeters: 1_500))
    }

    func testPresenceBeyondOneMileIsHidden() {
        XCTAssertFalse(
            MapDriveHorizonDepth.shouldShowPresenceMarker(
                distanceMeters: MapDriveHorizonDepth.checkpointVisibleMaxDistanceMeters + 100
            )
        )
    }
}
