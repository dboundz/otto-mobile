import CoreLocation
import XCTest
@testable import otto_mobile

final class RoutePolylineIndexTests: XCTestCase {
    func testIndexedProjectionMatchesNaiveProjectionOnStraightLine() {
        let line = straightPolyline(lengthMeters: 25_000, pointCount: 500)
        let query = CLLocationCoordinate2D(
            latitude: line[120].latitude + 0.0004,
            longitude: line[120].longitude + 0.0004
        )

        let indexed = RoutePolylineIndex(lineCoordinates: line).projectOntoPolyline(query)
        let naive = RouteMapGeometry.projectOntoPolyline(query, onto: line)

        XCTAssertNotNil(indexed)
        XCTAssertNotNil(naive)
        XCTAssertEqual(indexed?.segmentIndex, naive?.segmentIndex)
        XCTAssertEqual(indexed?.distanceMeters ?? 0, naive?.distanceMeters ?? 0, accuracy: 1.0)
    }

    func testPreferredArcLengthSearchUsesNearbySegment() {
        let line = straightPolyline(lengthMeters: 12_000, pointCount: 120)
        let index = RoutePolylineIndex(lineCoordinates: line)
        let targetArcLength = RouteMapGeometry.polylineTotalLength(line) * 0.55
        let query = RouteMapGeometry.coordinateAtArcLength(targetArcLength, on: line) ?? line[line.count / 2]

        let projection = index.projectOntoPolyline(
            CLLocationCoordinate2D(
                latitude: query.latitude + 0.0008,
                longitude: query.longitude + 0.0008
            ),
            preferredArcLength: targetArcLength
        )

        XCTAssertNotNil(projection)
        XCTAssertEqual(projection?.arcLengthMeters ?? 0, targetArcLength, accuracy: 250)
    }

    func testIndexedProjectionMatchesNaiveProjectionOnDenseCurvyLine() {
        let line = curvyPolyline()
        let query = CLLocationCoordinate2D(
            latitude: line[410].latitude + 0.00012,
            longitude: line[410].longitude - 0.00008
        )

        let indexed = RoutePolylineIndex(lineCoordinates: line).projectOntoPolyline(query)
        let naive = RouteMapGeometry.projectOntoPolyline(query, onto: line)

        XCTAssertNotNil(indexed)
        XCTAssertNotNil(naive)
        XCTAssertEqual(indexed?.segmentIndex, naive?.segmentIndex)
        XCTAssertEqual(indexed?.distanceMeters ?? 0, naive?.distanceMeters ?? 0, accuracy: 1.0)
    }

    func testPreferredArcLengthSearchChoosesClosestForwardProjection() {
        let line = curvyPolyline()
        let index = RoutePolylineIndex(lineCoordinates: line)
        let targetArcLength = RouteMapGeometry.polylineTotalLength(line) * 0.63
        let query = RouteMapGeometry.coordinateAtArcLength(targetArcLength + 90, on: line) ?? line[420]

        let projection = index.projectOntoPolyline(
            CLLocationCoordinate2D(
                latitude: query.latitude + 0.00006,
                longitude: query.longitude - 0.00004
            ),
            preferredArcLength: targetArcLength,
            searchWindowMeters: 220
        )

        XCTAssertNotNil(projection)
        XCTAssertGreaterThanOrEqual(projection?.arcLengthMeters ?? 0, targetArcLength - 60)
        XCTAssertLessThanOrEqual(projection?.arcLengthMeters ?? 0, targetArcLength + 220)
        XCTAssertLessThan(projection?.distanceMeters ?? .greatestFiniteMagnitude, 20)
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

    private func curvyPolyline() -> [CLLocationCoordinate2D] {
        let start = CLLocationCoordinate2D(latitude: 37.42, longitude: -122.08)
        return (0..<720).map { index in
            let t = Double(index)
            return CLLocationCoordinate2D(
                latitude: start.latitude + t * 0.000018 + sin(t / 18.0) * 0.00016,
                longitude: start.longitude + t * 0.000021 + cos(t / 23.0) * 0.00013
            )
        }
    }
}
