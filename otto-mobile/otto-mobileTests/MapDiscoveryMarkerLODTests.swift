import XCTest
import MapKit
@testable import otto_mobile

final class MapDiscoveryMarkerLODTests: XCTestCase {
    private var dotBoundary: Double {
        MapDiscoveryMarkerLOD.regionalDotMinLatitudeDelta
    }

    private var fullSizeBoundary: Double {
        MapDiscoveryMarkerLOD.pinFullSizeMaxLatitudeDelta
    }

    func testPresentationUsesDotBeyondFortyMiles() {
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.presentation(latitudeDelta: dotBoundary * 1.01),
            .dot
        )
    }

    func testPresentationUsesPinAtFortyMilesAndCloser() {
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.presentation(latitudeDelta: dotBoundary),
            .pin
        )
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.presentation(latitudeDelta: dotBoundary * 0.5),
            .pin
        )
    }

    func testPinScaleAtZoomBoundaries() {
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.pinScale(latitudeDelta: fullSizeBoundary),
            1,
            accuracy: 0.001
        )
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.pinScale(latitudeDelta: fullSizeBoundary * 0.5),
            1,
            accuracy: 0.001
        )
        XCTAssertEqual(
            MapDiscoveryMarkerLOD.pinScale(latitudeDelta: dotBoundary),
            MapDiscoveryMarkerLOD.pinMinScale,
            accuracy: 0.001
        )
    }

    func testPinScaleInterpolatesBetweenFortyAndOneMile() {
        let midSpan = (dotBoundary + fullSizeBoundary) / 2
        let scale = MapDiscoveryMarkerLOD.pinScale(latitudeDelta: midSpan)
        XCTAssertGreaterThan(scale, MapDiscoveryMarkerLOD.pinMinScale)
        XCTAssertLessThan(scale, 1)
    }

    func testDotColorsAreDistinctPerKind() {
        XCTAssertNotEqual(
            MapDiscoveryMarkerLOD.dotColor(for: .event),
            MapDiscoveryMarkerLOD.dotColor(for: .raceTrack)
        )
        XCTAssertNotEqual(
            MapDiscoveryMarkerLOD.dotColor(for: .raceTrack),
            MapDiscoveryMarkerLOD.dotColor(for: .savedPlace)
        )
    }

    func testAnnotationRefreshIDChangesAcrossTiers() {
        let dotID = MapDiscoveryMarkerLOD.annotationRefreshID(
            id: "marker-1",
            kind: .event,
            latitudeDelta: dotBoundary * 2
        )
        let pinID = MapDiscoveryMarkerLOD.annotationRefreshID(
            id: "marker-1",
            kind: .event,
            latitudeDelta: fullSizeBoundary
        )
        XCTAssertNotEqual(dotID, pinID)
        XCTAssertTrue(dotID.contains("dot"))
        XCTAssertTrue(pinID.contains("pin"))
    }

    func testAnnotationRefreshIDBucketsPinScale() {
        let lowScaleID = MapDiscoveryMarkerLOD.annotationRefreshID(
            id: "marker-1",
            kind: .savedPlace,
            latitudeDelta: dotBoundary * 0.99
        )
        let highScaleID = MapDiscoveryMarkerLOD.annotationRefreshID(
            id: "marker-1",
            kind: .savedPlace,
            latitudeDelta: fullSizeBoundary * 2
        )
        XCTAssertNotEqual(lowScaleID, highScaleID)
    }

    func testVisibleLatitudeDeltaMatchesAndroidFormulaAtSampleZoom() {
        let delta = OttoMapboxCamera.visibleLatitudeDeltaDegrees(zoom: 10, latitudeCenterDegrees: 37)
        let cosLat = abs(cos(37.0 * Double.pi / 180.0))
        let metersPerPixel = 156_543.03392 * cosLat / pow(2.0, 10.0)
        let expected = metersPerPixel * 640.0 / 111_000.0
        XCTAssertEqual(delta, expected, accuracy: 0.0001)
    }

    func testVisibleLatitudeDeltaForRegionUsesZoomFromRegion() {
        let region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        )
        let fromRegion = OttoMapboxCamera.visibleLatitudeDeltaDegrees(for: region)
        let fromZoom = OttoMapboxCamera.visibleLatitudeDeltaDegrees(
            zoom: Double(OttoMapboxCamera.zoomLevel(for: region)),
            latitudeCenterDegrees: region.center.latitude
        )
        XCTAssertEqual(fromRegion, fromZoom, accuracy: 0.000001)
    }

    func testVisibleLatitudeDeltaClampsZoom() {
        let lowZoom = OttoMapboxCamera.visibleLatitudeDeltaDegrees(zoom: 1, latitudeCenterDegrees: 0)
        let clampedLow = OttoMapboxCamera.visibleLatitudeDeltaDegrees(zoom: 4, latitudeCenterDegrees: 0)
        XCTAssertEqual(lowZoom, clampedLow, accuracy: 0.000001)
    }
}
