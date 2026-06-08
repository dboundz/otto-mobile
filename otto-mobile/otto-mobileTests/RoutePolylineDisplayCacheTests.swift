import CoreLocation
import XCTest
@testable import otto_mobile

final class RoutePolylineDisplayCacheTests: XCTestCase {
    func testCacheReturnsStableBucketArrays() {
        let full = (0..<5_000).map { index in
            CLLocationCoordinate2D(latitude: 37.0 + Double(index) * 0.00001, longitude: -122.0)
        }
        let cache = RoutePolylineDisplayCache.build(from: full)

        let first = cache.displayCoordinates(
            for: .icon,
            latitudeDelta: 0.02,
            regionalMinLatitudeDelta: 0.05
        )
        let second = cache.displayCoordinates(
            for: .icon,
            latitudeDelta: 0.02,
            regionalMinLatitudeDelta: 0.05
        )

        XCTAssertEqual(first.count, second.count)
        XCTAssertEqual(first.first?.latitude, second.first?.latitude)
        XCTAssertLessThanOrEqual(first.count, RoutePolylineDisplayOptimizer.Budget.iconMaxPoints + 1)
    }

    func testContinentalRegionalBucketUsedForVeryWideSpan() {
        let regionalMin = (5 * RouteAutoCheckpointGenerator.Options.metersPerMile) / 111_000
        let full = (0..<3_000).map { index in
            CLLocationCoordinate2D(latitude: 37.0 + Double(index) * 0.00001, longitude: -122.0)
        }
        let cache = RoutePolylineDisplayCache.build(from: full)

        let continental = cache.displayCoordinates(
            for: .regional,
            latitudeDelta: regionalMin * 100,
            regionalMinLatitudeDelta: regionalMin
        )

        XCTAssertLessThanOrEqual(
            continental.count,
            RoutePolylineDisplayOptimizer.Budget.regionalContinentalMaxPoints + 1
        )
    }

    func testFingerprintChangesWhenSourceGeometryChanges() {
        let a = RoutePolylineDisplayCache.build(from: [
            CLLocationCoordinate2D(latitude: 37, longitude: -122),
            CLLocationCoordinate2D(latitude: 37.1, longitude: -122.1),
        ])
        let b = RoutePolylineDisplayCache.build(from: [
            CLLocationCoordinate2D(latitude: 37, longitude: -122),
            CLLocationCoordinate2D(latitude: 37.2, longitude: -122.1),
        ])

        XCTAssertNotEqual(a.sourceFingerprint, b.sourceFingerprint)
    }
}
