import CoreLocation
import XCTest
@testable import otto_mobile

final class RouteAutoCheckpointGeneratorTests: XCTestCase {
    func testCoordinateAtArcLengthRoundTrip() {
        let line = straightPolyline(lengthMeters: 4_000, pointCount: 5)
        let target = 2_000.0
        guard let coordinate = RouteMapGeometry.coordinateAtArcLength(target, on: line) else {
            return XCTFail("Expected coordinate at arc length")
        }
        guard let projection = RouteMapGeometry.projectOntoPolyline(coordinate, onto: line) else {
            return XCTFail("Expected projection")
        }
        XCTAssertEqual(projection.arcLengthMeters, target, accuracy: 1)
    }

    func testStraightPolylineGeneratesHalfMileCheckpoints() {
        let line = straightPolyline(lengthMeters: 4_828, pointCount: 6)
        let generated = RouteAutoCheckpointGenerator.generate(roadCoordinates: line)
        XCTAssertEqual(generated.count, 5)
    }

    func testShortRouteGeneratesNoCheckpoints() {
        let line = straightPolyline(lengthMeters: 300, pointCount: 2)
        let generated = RouteAutoCheckpointGenerator.generate(roadCoordinates: line)
        XCTAssertTrue(generated.isEmpty)
        XCTAssertFalse(RouteAutoCheckpointGenerator.canOfferAutoCheckpoints(roadCoordinates: line))
    }

    func testCanOfferAutoCheckpointsWhenViableIntervalExists() {
        let line = straightPolyline(lengthMeters: 1_500, pointCount: 3)
        XCTAssertTrue(RouteAutoCheckpointGenerator.canOfferAutoCheckpoints(roadCoordinates: line))
    }

    func testViableIntervalsOnlyIncludeOptionsThatProduceCheckpoints() {
        let shortLine = straightPolyline(lengthMeters: 1_500, pointCount: 3)
        let shortIntervals = RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates: shortLine)
        XCTAssertEqual(shortIntervals.map(\.miles), [0.5])

        let longLine = straightPolyline(lengthMeters: 20_000, pointCount: 8)
        let longIntervals = RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates: longLine)
        XCTAssertTrue(longIntervals.contains(where: { $0.miles == 0.5 }))
        XCTAssertTrue(longIntervals.contains(where: { $0.miles == 1 }))
        XCTAssertTrue(longIntervals.contains(where: { $0.miles == 2 }))
        XCTAssertFalse(longIntervals.contains(where: { $0.miles == 100 }))
    }

    func testIntervalLabelFormatting() {
        XCTAssertEqual(RouteAutoCheckpointGenerator.intervalLabel(miles: 0.5), "½ mile")
        XCTAssertEqual(RouteAutoCheckpointGenerator.intervalLabel(miles: 1), "1 mile")
        XCTAssertEqual(RouteAutoCheckpointGenerator.intervalLabel(miles: 5), "5 miles")
    }

    func testTargetCheckpointCountScalesWithRouteLength() {
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 1), 2)
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 3), 3)
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 8), 5)
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 20), 7)
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 40), 9)
        XCTAssertEqual(RouteAutoCheckpointGenerator.targetCheckpointCount(routeMiles: 80), 11)
    }

    func testRecommendedDefaultPrefersReasonableSpacingOnShortRoute() {
        let line = straightPolyline(lengthMeters: 3_219, pointCount: 5) // ~2 mi
        let recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates: line)
        XCTAssertNotNil(recommended)
        XCTAssertTrue([0.5, 1].contains(recommended?.miles))
        XCTAssertGreaterThanOrEqual(recommended?.checkpointCount ?? 0, 2)
        XCTAssertLessThanOrEqual(recommended?.checkpointCount ?? 0, 3)
    }

    func testRecommendedDefaultPrefersSparseIntervalOnLongRoute() {
        let line = straightPolyline(lengthMeters: 40_000, pointCount: 8) // ~25 mi
        let recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates: line)
        XCTAssertEqual(recommended?.miles, 5)
    }

    func testRecommendedDefaultOnEpicRouteAvoidsHalfMile() {
        let line = straightPolyline(lengthMeters: 96_000, pointCount: 10) // ~60 mi
        let recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates: line)
        XCTAssertEqual(recommended?.miles, 5)
        XCTAssertLessThanOrEqual(recommended?.checkpointCount ?? 0, 12)
    }

    func testRecommendedDefaultFallsBackToSparsestWhenAllExceedCap() {
        let line = straightPolyline(lengthMeters: 250_000, pointCount: 12) // ~155 mi
        let recommended = RouteAutoCheckpointGenerator.recommendedDefaultInterval(roadCoordinates: line)
        XCTAssertEqual(recommended?.miles, 100)
    }

    func testDensityTierCountsIncreaseMonotonically() {
        let line = straightPolyline(lengthMeters: 32_000, pointCount: 10) // ~20 mi
        let options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates: line, turnCount: 3)
        XCTAssertFalse(options.isEmpty)
        let counts = options.map(\.checkpointCount)
        for index in 1..<counts.count {
            XCTAssertLessThanOrEqual(counts[index - 1], counts[index])
        }
    }

    func testComplexityIncreasesRecommendedCheckpointCount() {
        let line = straightPolyline(lengthMeters: 16_000, pointCount: 8) // ~10 mi
        let straight = RouteAutoCheckpointGenerator.recommendedDefaultInterval(
            roadCoordinates: line,
            turnCount: 0
        )
        let complex = RouteAutoCheckpointGenerator.recommendedDefaultInterval(
            roadCoordinates: line,
            turnCount: 48
        )
        XCTAssertNotNil(straight)
        XCTAssertNotNil(complex)
        XCTAssertGreaterThanOrEqual(complex?.checkpointCount ?? 0, straight?.checkpointCount ?? 0)
    }

    func testDensityOptionsIncludeRecommendedTier() {
        let line = straightPolyline(lengthMeters: 8_000, pointCount: 6)
        let options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates: line)
        XCTAssertTrue(options.contains(where: { $0.tier == .recommended }))
    }

    func testAdjacentTierStepsThroughOrderedTiers() {
        XCTAssertEqual(
            RouteAutoCheckpointGenerator.adjacentTier(from: .recommended, direction: -1),
            .fewer
        )
        XCTAssertEqual(
            RouteAutoCheckpointGenerator.adjacentTier(from: .recommended, direction: 1),
            .more
        )
        XCTAssertNil(RouteAutoCheckpointGenerator.adjacentTier(from: .fewer, direction: -1))
        XCTAssertNil(RouteAutoCheckpointGenerator.adjacentTier(from: .maximum, direction: 1))
    }

    func testAdjacentDensityOptionStepsThroughUniqueSpacings() {
        let line = straightPolyline(lengthMeters: 32_000, pointCount: 10)
        let options = RouteAutoCheckpointGenerator.densityOptions(roadCoordinates: line, turnCount: 3)
        guard let start = options.first(where: { $0.tier == .recommended }) else {
            return XCTFail("Expected recommended tier")
        }

        let denser = RouteAutoCheckpointGenerator.adjacentDensityOption(
            from: start.tier,
            roadCoordinates: line,
            turnCount: 3,
            direction: 1
        )
        XCTAssertNotNil(denser)
        XCTAssertGreaterThan(denser?.checkpointCount ?? 0, start.checkpointCount)

        let sparser = RouteAutoCheckpointGenerator.adjacentDensityOption(
            from: start.tier,
            roadCoordinates: line,
            turnCount: 3,
            direction: -1
        )
        XCTAssertNotNil(sparser)
        XCTAssertLessThan(sparser?.checkpointCount ?? Int.max, start.checkpointCount)
    }

    private func straightPolyline(lengthMeters: Double, pointCount: Int) -> [CLLocationCoordinate2D] {
        precondition(pointCount >= 2)
        let start = CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0)
        let end = CLLocationCoordinate2D(
            latitude: start.latitude + (lengthMeters / 111_000),
            longitude: start.longitude
        )
        return (0..<pointCount).map { index in
            let fraction = Double(index) / Double(pointCount - 1)
            return CLLocationCoordinate2D(
                latitude: start.latitude + (end.latitude - start.latitude) * fraction,
                longitude: start.longitude + (end.longitude - start.longitude) * fraction
            )
        }
    }
}
