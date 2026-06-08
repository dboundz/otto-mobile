import CoreLocation
import XCTest
@testable import otto_mobile

final class RoutePolylineDisplayOptimizerTests: XCTestCase {
    func testDownsamplePreservesEndpoints() {
        let coordinates = (0..<500).map { index in
            CLLocationCoordinate2D(latitude: 37.0 + Double(index) * 0.001, longitude: -122.0)
        }

        let sampled = RoutePolylineDisplayOptimizer.downsample(coordinates, maxCount: 50)
        XCTAssertLessThanOrEqual(sampled.count, 51)
        XCTAssertEqual(sampled.first?.latitude, coordinates.first?.latitude)
        XCTAssertEqual(sampled.last?.latitude, coordinates.last?.latitude)
    }

    func testDisplayBudgetDecreasesWithZoomedOutTier() {
        let coordinates = (0..<10_000).map { index in
            CLLocationCoordinate2D(latitude: 37.0 + Double(index) * 0.00001, longitude: -122.0)
        }

        let regional = RoutePolylineDisplayOptimizer.displayCoordinates(
            from: coordinates,
            lodTier: .regional
        )
        let icon = RoutePolylineDisplayOptimizer.displayCoordinates(
            from: coordinates,
            lodTier: .icon
        )
        let street = RoutePolylineDisplayOptimizer.displayCoordinates(
            from: coordinates,
            lodTier: .street
        )

        XCTAssertLessThanOrEqual(regional.count, RoutePolylineDisplayOptimizer.Budget.regionalMaxPoints + 1)
        XCTAssertLessThanOrEqual(icon.count, RoutePolylineDisplayOptimizer.Budget.iconMaxPoints + 1)
        XCTAssertLessThanOrEqual(street.count, RoutePolylineDisplayOptimizer.Budget.streetMaxPoints + 1)
        XCTAssertLessThan(regional.count, street.count)
    }

    func testMarkerLODTierBoundaries() {
        let regionalMin = (5 * RouteAutoCheckpointGenerator.Options.metersPerMile) / 111_000
        let streetMax = (1_000 * 0.3048) / 111_000

        XCTAssertEqual(
            RouteBuilderMapMarkerLODTier.from(
                latitudeDelta: regionalMin * 2,
                regionalMinLatitudeDelta: regionalMin,
                streetMaxLatitudeDelta: streetMax
            ),
            .regional
        )
        XCTAssertEqual(
            RouteBuilderMapMarkerLODTier.from(
                latitudeDelta: streetMax,
                regionalMinLatitudeDelta: regionalMin,
                streetMaxLatitudeDelta: streetMax
            ),
            .street
        )
    }

    func testExtremeRegionalSpanUsesContinentalBudget() {
        let regionalMin = (5 * RouteAutoCheckpointGenerator.Options.metersPerMile) / 111_000
        let coordinates = (0..<5_000).map { index in
            CLLocationCoordinate2D(latitude: 37.0 + Double(index) * 0.00001, longitude: -122.0)
        }

        let continental = RoutePolylineDisplayOptimizer.displayCoordinates(
            from: coordinates,
            lodTier: .regional,
            latitudeDelta: regionalMin * 100,
            regionalMinLatitudeDelta: regionalMin
        )

        XCTAssertLessThanOrEqual(
            continental.count,
            RoutePolylineDisplayOptimizer.Budget.regionalContinentalMaxPoints + 1
        )
    }
}
