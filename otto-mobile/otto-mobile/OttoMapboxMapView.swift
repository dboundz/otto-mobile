import CoreLocation
import MapboxMaps
import MapKit
import SwiftUI
import UIKit

enum OttoMapboxCamera {
    /// Pitched third-person chase view while driving (more tilt than Mapbox default 45°).
    static let drivePitchDegrees: CGFloat = 60
    /// Tighter than normal follow-me so the pitched view feels closer behind the user (~zoom 17.5).
    static let driveTrackingSpan = MKCoordinateSpan(latitudeDelta: 0.0028, longitudeDelta: 0.0028)
    /// Screen Y fraction from the top where the user coordinate sits during drive follow (0.8 ≈ bottom 20%).
    static let driveUserAnchorYFraction: CGFloat = 0.80
    /// Duration for entering/exiting pitched drive follow (pitch, padding, bearing reset).
    static let driveCameraTransitionDuration: TimeInterval = 0.55
    /// Ignore stale course values at parking-lot speeds.
    static let minimumDriveCourseSpeedMetersPerSecond: CLLocationSpeed = 2.0

    /// Top camera padding so the user's location renders near the bottom of the map.
    static func driveFollowEdgeInsets(mapHeight: CGFloat) -> SwiftUI.EdgeInsets {
        let height = max(mapHeight, 320)
        let topPadding = height * max(0, 2 * driveUserAnchorYFraction - 1)
        return EdgeInsets(top: topPadding, leading: 0, bottom: 0, trailing: 0)
    }

    static func viewport(
        for region: MKCoordinateRegion,
        bearing: CGFloat = 0,
        pitch: CGFloat = 0,
        followPadding: SwiftUI.EdgeInsets? = nil
    ) -> Viewport {
        var result = Viewport.camera(
            center: region.center,
            zoom: zoomLevel(for: region),
            bearing: bearing,
            pitch: pitch
        )
        if let followPadding {
            result = result.padding(followPadding)
        }
        return result
    }

    static func driveBearing(
        from location: CLLocation,
        previous: CLLocation?,
        fallback: CGFloat = 0
    ) -> CGFloat {
        if location.speed >= minimumDriveCourseSpeedMetersPerSecond, location.course >= 0 {
            return normalizedBearing(CGFloat(location.course))
        }
        if let previous, location.distance(from: previous) >= 3 {
            return normalizedBearing(
                bearingDegrees(from: previous.coordinate, to: location.coordinate)
            )
        }
        return normalizedBearing(fallback)
    }

    static func interpolateBearing(from current: CGFloat, to target: CGFloat, factor: CGFloat) -> CGFloat {
        let delta = shortPathBearingDelta(from: current, to: target)
        return normalizedBearing(current + delta * factor)
    }

    static func shortPathBearingDelta(from: CGFloat, to: CGFloat) -> CGFloat {
        var delta = (to - from).truncatingRemainder(dividingBy: 360)
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }
        return delta
    }

    private static func normalizedBearing(_ bearing: CGFloat) -> CGFloat {
        var value = bearing.truncatingRemainder(dividingBy: 360)
        if value < 0 { value += 360 }
        return value
    }

    private static func bearingDegrees(
        from: CLLocationCoordinate2D,
        to: CLLocationCoordinate2D
    ) -> CGFloat {
        let lat1 = from.latitude * .pi / 180
        let lat2 = to.latitude * .pi / 180
        let dLon = (to.longitude - from.longitude) * .pi / 180
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        let radians = atan2(y, x)
        return CGFloat(radians * 180 / .pi)
    }

    static func zoomLevel(for region: MKCoordinateRegion) -> CGFloat {
        let longitudeDelta = max(0.000001, region.span.longitudeDelta)
        let zoom = log2(360.0 / longitudeDelta)
        return CGFloat(min(22, max(0, zoom)))
    }

    static func region(for cameraState: CameraState) -> MKCoordinateRegion {
        let longitudeDelta = 360.0 / pow(2.0, cameraState.zoom)
        let latitudeDelta = longitudeDelta / max(0.2, cos(cameraState.center.latitude * .pi / 180))
        return MKCoordinateRegion(
            center: cameraState.center,
            span: MKCoordinateSpan(
                latitudeDelta: max(0.000001, latitudeDelta),
                longitudeDelta: max(0.000001, longitudeDelta)
            )
        )
    }

    /// Approximate visible latitude span for marker LOD — mirrors Android `visibleLatitudeDeltaDegrees`.
    static func visibleLatitudeDeltaDegrees(
        zoom: Double,
        latitudeCenterDegrees: Double,
        approximateScreenHeightPx: Double = 640
    ) -> Double {
        let latSafe = latitudeCenterDegrees.isFinite ? latitudeCenterDegrees : 0
        let cosLat = max(0.2, abs(cos(latSafe * .pi / 180)))
        let clampedZoom = min(21, max(4, zoom))
        let metersPerPixel = 156_543.03392 * cosLat / pow(2.0, clampedZoom)
        let visibleHeightMeters = metersPerPixel * approximateScreenHeightPx
        return visibleHeightMeters / 111_000.0
    }

    static func visibleLatitudeDeltaDegrees(for region: MKCoordinateRegion) -> Double {
        visibleLatitudeDeltaDegrees(
            zoom: Double(zoomLevel(for: region)),
            latitudeCenterDegrees: region.center.latitude
        )
    }
}

struct OttoMapboxMapView<Content: MapboxMaps.MapContent>: View {
    @Binding var viewport: Viewport
    let allowsInteraction: Bool
    let onCameraChanged: (MKCoordinateRegion) -> Void
    let onUserGesture: () -> Void
    let onGestureEnd: () -> Void
    let onMapLoaded: () -> Void
    let onMapboxMapReady: ((MapboxMap) -> Void)?
    let onMapTap: ((CLLocationCoordinate2D) -> Void)?
    let onMapLongPress: ((CLLocationCoordinate2D) -> Void)?
    let content: () -> Content

    @State private var didReportMapReady = false

    init(
        viewport: Binding<Viewport>,
        allowsInteraction: Bool,
        onCameraChanged: @escaping (MKCoordinateRegion) -> Void,
        onUserGesture: @escaping () -> Void,
        onGestureEnd: @escaping () -> Void = {},
        onMapLoaded: @escaping () -> Void = {},
        onMapboxMapReady: ((MapboxMap) -> Void)? = nil,
        onMapTap: ((CLLocationCoordinate2D) -> Void)? = nil,
        onMapLongPress: ((CLLocationCoordinate2D) -> Void)? = nil,
        @MapboxMaps.MapContentBuilder content: @escaping () -> Content
    ) {
        _viewport = viewport
        self.allowsInteraction = allowsInteraction
        self.onCameraChanged = onCameraChanged
        self.onUserGesture = onUserGesture
        self.onGestureEnd = onGestureEnd
        self.onMapLoaded = onMapLoaded
        self.onMapboxMapReady = onMapboxMapReady
        self.onMapTap = onMapTap
        self.onMapLongPress = onMapLongPress
        self.content = content
    }

    var body: some View {
        MapReader { proxy in
            MapboxMaps.Map(viewport: $viewport) {
                content()
                if let onMapTap {
                    TapInteraction { context in
                        onMapTap(context.coordinate)
                        return true
                    }
                }
                if let onMapLongPress {
                    LongPressInteraction { context in
                        onMapLongPress(context.coordinate)
                        return true
                    }
                }
            }
            .mapStyle(.standard(lightPreset: .night))
            .gestureOptions(gestureOptions)
            .gestureHandlers(
                MapGestureHandlers(
                    onBegin: { _ in
                        onUserGesture()
                    },
                    onEnd: { _, _ in
                        onGestureEnd()
                    }
                )
            )
            .onCameraChanged { event in
                onCameraChanged(OttoMapboxCamera.region(for: event.cameraState))
            }
            .onMapLoaded { _ in
                onMapLoaded()
                reportMapReadyIfNeeded(map: proxy.map)
            }
            .onAppear {
                Self.configureAccessTokenIfNeeded()
            }
            .onDisappear {
                didReportMapReady = false
            }
        }
    }

    private func reportMapReadyIfNeeded(map: MapboxMap?) {
        guard let map, !didReportMapReady else { return }
        didReportMapReady = true
        onMapboxMapReady?(map)
    }

    private var gestureOptions: GestureOptions {
        GestureOptions(
            panEnabled: allowsInteraction,
            pinchEnabled: allowsInteraction,
            rotateEnabled: allowsInteraction,
            simultaneousRotateAndPinchZoomEnabled: allowsInteraction,
            pinchZoomEnabled: allowsInteraction,
            pinchPanEnabled: allowsInteraction,
            pitchEnabled: false,
            doubleTapToZoomInEnabled: allowsInteraction,
            doubleTouchToZoomOutEnabled: allowsInteraction,
            quickZoomEnabled: allowsInteraction
        )
    }

    private static func configureAccessTokenIfNeeded() {
        guard
            let token = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String,
            !token.isEmpty,
            !token.contains("$(")
        else {
            return
        }
        MapboxOptions.accessToken = token
    }
}

struct OttoMapboxEventPreview: View {
    let coordinate: CLLocationCoordinate2D
    let title: String

    @State private var viewport: Viewport

    init(coordinate: CLLocationCoordinate2D, title: String) {
        self.coordinate = coordinate
        self.title = title
        _viewport = State(
            initialValue: OttoMapboxCamera.viewport(
                for: MKCoordinateRegion(
                center: coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
            )
            )
        )
    }

    var body: some View {
        OttoMapboxMapView(
            viewport: $viewport,
            allowsInteraction: false,
            onCameraChanged: { _ in },
            onUserGesture: {}
        ) {
            MapViewAnnotation(coordinate: coordinate) {
                OttoMapEventMarker(isSelected: false)
                    .accessibilityLabel(title)
            }
            .allowOverlap(true)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .overlay {
            Rectangle()
                .fill(.black.opacity(0.12))
                .allowsHitTesting(false)
        }
    }
}
