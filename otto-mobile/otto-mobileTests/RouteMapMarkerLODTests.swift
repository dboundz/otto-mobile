import XCTest
import MapKit
@testable import otto_mobile

final class RouteMapMarkerLODTests: XCTestCase {
    private var driveRouteLatitudeDelta: Double {
        OttoMapboxCamera.visibleLatitudeDeltaDegrees(
            for: MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
                span: OttoMapboxCamera.driveTrackingSpan
            )
        )
    }

    func testDriveTrackingSpanDoesNotUseWideRegionalLightweightRendering() {
        XCTAssertFalse(
            RouteMapMarkerLOD.usesWideRegionalLightweightRendering(latitudeDelta: driveRouteLatitudeDelta)
        )
    }

    func testDriveTrackingSpanShowsCheckpointPinsNotOnlyEndpoints() {
        XCTAssertEqual(
            RouteMapMarkerLOD.presentation(markerType: "waypoint", latitudeDelta: driveRouteLatitudeDelta),
            .pin
        )
    }

    func testWideRegionalDoesNotStripWhenShowingAllRouteMarkers() {
        let wideLatitudeDelta = RouteMapMarkerLOD.regionalDotMinLatitudeDelta
            * RouteMapMarkerLOD.wideRegionalSpanMultiplier
            * 1.5
        XCTAssertTrue(
            RouteMapMarkerLOD.usesWideRegionalLightweightRendering(latitudeDelta: wideLatitudeDelta)
        )
        XCTAssertFalse(
            RouteMapMarkerLOD.shouldStripToStartFinishOnly(
                shouldShowAllRouteMarkers: true,
                latitudeDelta: wideLatitudeDelta
            )
        )
        XCTAssertTrue(
            RouteMapMarkerLOD.shouldStripToStartFinishOnly(
                shouldShowAllRouteMarkers: false,
                latitudeDelta: wideLatitudeDelta
            )
        )
    }

    func testWideRegionalShowsCheckpointDotsWhenRouteSelectedOnMap() {
        let wideLatitudeDelta = RouteMapMarkerLOD.regionalDotMinLatitudeDelta
            * RouteMapMarkerLOD.wideRegionalSpanMultiplier
            * 1.5
        XCTAssertEqual(
            RouteMapMarkerLOD.presentation(markerType: "waypoint", latitudeDelta: wideLatitudeDelta),
            .dot
        )
        XCTAssertFalse(
            RouteMapMarkerLOD.shouldStripToStartFinishOnly(
                shouldShowAllRouteMarkers: true,
                latitudeDelta: wideLatitudeDelta
            )
        )
    }

    func testStableRouteDriveAnnotationIDOmitsScaleBucket() {
        let stable = RouteMapMarkerLOD.annotationRefreshID(
            pointID: "route-1-3",
            markerType: "waypoint",
            latitudeDelta: driveRouteLatitudeDelta,
            stableForRouteDrive: true
        )
        let full = RouteMapMarkerLOD.annotationRefreshID(
            pointID: "route-1-3",
            markerType: "waypoint",
            latitudeDelta: driveRouteLatitudeDelta,
            stableForRouteDrive: false
        )
        XCTAssertEqual(stable, "route-1-3-pin")
        XCTAssertNotEqual(stable, full)
        XCTAssertTrue(full.contains("pin-"))
    }
}
