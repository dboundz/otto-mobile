import CoreLocation
import MapboxMaps
import MapKit
import SwiftUI
import UIKit

// MARK: - Models

struct RouteMapPointModel: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let markerType: String?
    let index: Int

    func isCompleted(in completedIndexes: Set<Int>) -> Bool {
        guard markerType == "waypoint" else { return false }
        return completedIndexes.contains(index)
    }
}

enum RouteMapLinePalette {
    case livePurple
    case builderCyan

    var glowStyleColor: StyleColor {
        switch self {
        case .livePurple: return StyleColor(UIColor(red: 123 / 255, green: 61 / 255, blue: 255 / 255, alpha: 1))
        case .builderCyan: return StyleColor(UIColor(red: 0, green: 234 / 255, blue: 255 / 255, alpha: 1))
        }
    }

    var mainStyleColor: StyleColor {
        switch self {
        case .livePurple: return StyleColor(UIColor(red: 123 / 255, green: 61 / 255, blue: 255 / 255, alpha: 1))
        case .builderCyan: return StyleColor(UIColor(red: 0, green: 184 / 255, blue: 255 / 255, alpha: 1))
        }
    }

    var coreStyleColor: StyleColor {
        switch self {
        case .livePurple: return StyleColor(UIColor(red: 232 / 255, green: 217 / 255, blue: 255 / 255, alpha: 1))
        case .builderCyan: return StyleColor(UIColor(red: 232 / 255, green: 255 / 255, blue: 255 / 255, alpha: 1))
        }
    }

    var glowOpacity: Double {
        switch self {
        case .livePurple: return 0.42
        case .builderCyan: return 0.62
        }
    }

    var glowBlur: Double {
        switch self {
        case .livePurple: return 4
        case .builderCyan: return 3
        }
    }

    var glowWidth: Double {
        switch self {
        case .livePurple: return 15
        case .builderCyan: return 17
        }
    }

    var mainWidth: Double {
        switch self {
        case .livePurple: return 9
        case .builderCyan: return 11
        }
    }

    var coreWidth: Double {
        switch self {
        case .livePurple: return 3
        case .builderCyan: return 4
        }
    }

    var glowEmissive: Double {
        switch self {
        case .livePurple: return 1.5
        case .builderCyan: return 1.8
        }
    }

    var lineEmissive: Double {
        switch self {
        case .livePurple: return 2
        case .builderCyan: return 2.4
        }
    }
}

// MARK: - Marker assets

enum MapMarkerHaptics {
    static func longPressRecognized() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }
}

/// UI colors aligned with route map pin assets (`map-route-start`, `map-route-finish`).
enum RouteMapMarkerColors {
    /// Forest green from the start pin — darker and less neon than system `.green`.
    static let startButton = Color(red: 0.06, green: 0.50, blue: 0.24)
    /// Start accent for labels, chips, and checkmarks on dark sheet backgrounds.
    static let startAccent = Color(red: 0.24, green: 0.68, blue: 0.38)
    /// Purple finish CTA — distinct from the white finish map pin.
    static let finishButton = Color(red: 0.55, green: 0.36, blue: 0.96)
    /// Checkpoint pin / edit-sheet accent blue.
    static let checkpointBlue = Color(red: 0.22, green: 0.52, blue: 0.98)
    /// Stop pin / edit-sheet accent red.
    static let stopRed = Color(red: 0.95, green: 0.23, blue: 0.21)
    /// Path / shape-route accent purple (matches finish CTA family).
    static let pathPurple = finishButton
    /// Map discovery event pin / regional dot pink (sampled from `map-point-event`).
    static let discoveryEventPink = Color(red: 0.966, green: 0.219, blue: 0.530)
    /// Map discovery racetrack pin / regional dot orange (sampled from `map-point-track`).
    static let discoveryRaceTrackOrange = Color(red: 0.999, green: 0.650, blue: 0.346)
    /// Map discovery saved place pin / regional dot teal (sampled from `map-point-saved`).
    static let discoverySavedPlaceTeal = Color(red: 0.000, green: 0.649, blue: 0.668)
}

enum RouteMapMarkerAsset {
    /// Inner glyph for path / shape-handle pins (spatial layout).
    static let pathInnerSymbolName = "arrow.up.left.and.arrow.down.right"

    static func assetName(markerType: String?, isCompleted: Bool = false) -> String {
        switch markerType {
        case "start": return "map-route-start"
        case "path": return "map-route-point"
        case "waypoint": return isCompleted ? "map-route-checkpoint-passed" : "map-route-checkpoint"
        case "stop": return "map-route-stop"
        case "finish": return "map-route-finish"
        default: return "map-route-point"
        }
    }

    /// Path markers use a centered circular badge (not a bottom-anchored pin).
    static func usesCenteredMarker(markerType: String?) -> Bool {
        markerType == "path" || markerType == nil
    }

    static func accessibilityLabel(markerType: String?) -> String {
        switch markerType {
        case "start": return "Start"
        case "waypoint": return "Checkpoint"
        case "stop": return "Stop"
        case "finish": return "Finish"
        default: return "Path marker"
        }
    }
}

struct RouteMapMarkerView: View {
    let markerType: String?
    let isCompleted: Bool
    var scale: CGFloat = 1
    var subdued: Bool = false
    /// When true, render a compact pin for Mapbox `.bottom` view-annotation anchor (Route Builder).
    var usesBottomAnnotationAnchor: Bool = false
    var onLongPress: (() -> Void)? = nil

    private var pinWidth: CGFloat { 56 * scale }
    private var pinHeight: CGFloat { 84 * scale }
    private var pinFrameWidth: CGFloat { max(pinWidth, 72 * scale) }
    private var pinFrameHeight: CGFloat { pinHeight * 2 }
    private var centeredMarkerSize: CGFloat { 48 * scale }
    private var centeredFrameSize: CGFloat { max(centeredMarkerSize, 44 * scale) }

    var body: some View {
        Group {
            if RouteMapMarkerAsset.usesCenteredMarker(markerType: markerType) {
                centeredMarkerImage
            } else if usesBottomAnnotationAnchor {
                pinMarkerImage
                    .shadow(color: .black.opacity(subdued ? 0.22 : 0.4), radius: 4 * scale, y: 2 * scale)
                    .frame(width: pinFrameWidth, height: pinHeight)
            } else {
                VStack(spacing: 0) {
                    pinMarkerImage
                        .shadow(color: .black.opacity(subdued ? 0.22 : 0.4), radius: 4 * scale, y: 2 * scale)
                    Spacer(minLength: 0)
                }
                .frame(width: pinFrameWidth, height: pinFrameHeight)
            }
        }
        .opacity(subdued ? 0.72 : 1)
        .contentShape(Rectangle())
        .modifier(RouteMapMarkerLongPressModifier(onLongPress: onLongPress))
        .accessibilityLabel(RouteMapMarkerAsset.accessibilityLabel(markerType: markerType))
        .accessibilityAddTraits(onLongPress == nil ? [] : .isButton)
    }

    private var centeredMarkerImage: some View {
        Image(RouteMapMarkerAsset.assetName(markerType: markerType))
            .resizable()
            .scaledToFit()
            .frame(width: centeredMarkerSize, height: centeredMarkerSize)
            .shadow(color: .black.opacity(subdued ? 0.22 : 0.4), radius: 3 * scale, y: 1 * scale)
            .frame(width: centeredFrameSize, height: centeredFrameSize)
    }

    private var pinMarkerImage: some View {
        Image(RouteMapMarkerAsset.assetName(markerType: markerType, isCompleted: isCompleted))
            .resizable()
            .scaledToFit()
            .frame(width: pinWidth, height: pinHeight)
    }
}

private struct RouteMapMarkerLongPressModifier: ViewModifier {
    let onLongPress: (() -> Void)?

    func body(content: Content) -> some View {
        if let onLongPress {
            content.highPriorityGesture(
                LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                    MapMarkerHaptics.longPressRecognized()
                    onLongPress()
                }
            )
        } else {
            content
        }
    }
}

// MARK: - Route marker LOD (Route Builder + Map route drive parity)

enum RouteMapMarkerLODPresentation: Equatable {
    case endpointPin
    case dot
    case pin
}

enum RouteMapMarkerLOD {
    /// Above ~5 mi visible span — checkpoint/stop render as colored dots (Route Builder parity).
    static let regionalDotMinLatitudeDelta = (5 * 1609.344) / 111_000
    /// ~1,000 ft visible span — checkpoint/stop pins reach full size at or below this.
    static let pinFullSizeMaxLatitudeDelta = (1_000 * 0.3048) / 111_000
    static let subtlePinScale: CGFloat = 0.55
    static let wideRegionalSpanMultiplier: Double = 12

    static func tier(latitudeDelta: Double) -> RouteBuilderMapMarkerLODTier {
        RouteBuilderMapMarkerLODTier.from(
            latitudeDelta: latitudeDelta,
            regionalMinLatitudeDelta: regionalDotMinLatitudeDelta,
            streetMaxLatitudeDelta: pinFullSizeMaxLatitudeDelta
        )
    }

    static func usesWideRegionalLightweightRendering(latitudeDelta: Double) -> Bool {
        tier(latitudeDelta: latitudeDelta) == .regional
            && latitudeDelta > regionalDotMinLatitudeDelta * wideRegionalSpanMultiplier
    }

    /// When true, wide-zoom browse mode may show only start/finish (checkpoints/stops hidden).
    static func shouldStripToStartFinishOnly(
        shouldShowAllRouteMarkers: Bool,
        latitudeDelta: Double
    ) -> Bool {
        !shouldShowAllRouteMarkers
            && usesWideRegionalLightweightRendering(latitudeDelta: latitudeDelta)
    }

    static func presentation(markerType: String?, latitudeDelta: Double) -> RouteMapMarkerLODPresentation {
        if markerType == "start" || markerType == "finish" {
            return .endpointPin
        }
        if tier(latitudeDelta: latitudeDelta) == .regional {
            return .dot
        }
        return .pin
    }

    static func usesZoomAwareSubtleScale(markerType: String?) -> Bool {
        markerType == "waypoint" || markerType == "stop" || markerType == "path"
    }

    static func pinScale(markerType: String?, latitudeDelta: Double) -> CGFloat {
        if markerType == "start" || markerType == "finish" {
            return 1
        }
        guard usesZoomAwareSubtleScale(markerType: markerType) else { return 1 }
        return zoomAwarePinScale(latitudeDelta: latitudeDelta)
    }

    static func zoomAwarePinScale(latitudeDelta: Double) -> CGFloat {
        let far = regionalDotMinLatitudeDelta
        let close = pinFullSizeMaxLatitudeDelta
        if latitudeDelta <= close { return 1 }
        let range = far - close
        guard range > 0 else { return subtlePinScale }
        let t = min(1, max(0, (far - latitudeDelta) / range))
        return subtlePinScale + CGFloat(t) * (1 - subtlePinScale)
    }

    static func dotColor(markerType: String?) -> Color {
        switch markerType {
        case "waypoint": return RouteMapMarkerColors.checkpointBlue
        case "stop": return RouteMapMarkerColors.stopRed
        case "path": return RouteMapMarkerColors.pathPurple
        default: return RouteMapMarkerColors.pathPurple
        }
    }

    static func annotationRefreshID(
        pointID: String,
        markerType: String?,
        latitudeDelta: Double,
        stableForRouteDrive: Bool = false
    ) -> String {
        let mode = presentation(markerType: markerType, latitudeDelta: latitudeDelta)
        if stableForRouteDrive {
            switch mode {
            case .endpointPin:
                return pointID
            case .dot:
                return "\(pointID)-dot"
            case .pin:
                return "\(pointID)-pin"
            }
        }
        switch mode {
        case .endpointPin:
            return pointID
        case .dot:
            return "\(pointID)-dot-\(tier(latitudeDelta: latitudeDelta))"
        case .pin:
            let bucket = Int((pinScale(markerType: markerType, latitudeDelta: latitudeDelta) * 10).rounded())
            return "\(pointID)-pin-\(tier(latitudeDelta: latitudeDelta))-\(bucket)"
        }
    }
}

struct RouteMapMarkerDotView: View {
    let color: Color
    var subdued: Bool = false
    var onLongPress: (() -> Void)? = nil

    private let dotSize: CGFloat = 12
    private let frameSize: CGFloat = 44

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: dotSize, height: dotSize)
            .overlay(
                Circle()
                    .stroke(Color.white.opacity(0.92), lineWidth: 1.5)
            )
            .shadow(color: .black.opacity(subdued ? 0.22 : 0.4), radius: 2, y: 1)
            .opacity(subdued ? 0.72 : 1)
            .frame(width: frameSize, height: frameSize)
            .contentShape(Rectangle())
            .modifier(RouteMapMarkerLongPressModifier(onLongPress: onLongPress))
    }
}

struct RouteMapMarkerLODView: View {
    let markerType: String?
    let isCompleted: Bool
    let latitudeDelta: Double
    var horizonScale: CGFloat = 1
    var subdued: Bool = false
    var onLongPress: (() -> Void)? = nil

    var body: some View {
        switch RouteMapMarkerLOD.presentation(markerType: markerType, latitudeDelta: latitudeDelta) {
        case .dot:
            RouteMapMarkerDotView(
                color: RouteMapMarkerLOD.dotColor(markerType: markerType),
                subdued: subdued,
                onLongPress: onLongPress
            )
            .scaleEffect(horizonScale)
        case .endpointPin, .pin:
            RouteMapMarkerView(
                markerType: markerType,
                isCompleted: isCompleted,
                scale: RouteMapMarkerLOD.pinScale(markerType: markerType, latitudeDelta: latitudeDelta) * horizonScale,
                subdued: subdued,
                onLongPress: onLongPress
            )
        }
    }
}

// MARK: - Map discovery marker LOD (Events, racetracks, saved places)

enum MapDiscoveryMarkerKind: Equatable {
    case event
    case raceTrack
    case savedPlace
}

enum MapDiscoveryMarkerLOD {
    /// Above ~40 mi visible span — discovery markers render as colored dots.
    static let regionalDotMinLatitudeDelta = (40 * 1609.344) / 111_000
    /// ~1 mi visible span — discovery pins reach full size at or below this.
    static let pinFullSizeMaxLatitudeDelta = (1 * 1609.344) / 111_000
    /// Pin scale at the ~40 mi dot→pin boundary; ramps linearly to 1.0 by ~1 mi.
    static let pinMinScale: CGFloat = 0.55

    static func presentation(latitudeDelta: Double) -> RouteMapMarkerLODPresentation {
        latitudeDelta > regionalDotMinLatitudeDelta ? .dot : .pin
    }

    static func pinScale(latitudeDelta: Double) -> CGFloat {
        let far = regionalDotMinLatitudeDelta
        let close = pinFullSizeMaxLatitudeDelta
        if latitudeDelta <= close { return 1 }
        let range = far - close
        guard range > 0 else { return pinMinScale }
        let t = min(1, max(0, (far - latitudeDelta) / range))
        return pinMinScale + CGFloat(t) * (1 - pinMinScale)
    }

    static func dotColor(for kind: MapDiscoveryMarkerKind) -> Color {
        switch kind {
        case .event: return RouteMapMarkerColors.discoveryEventPink
        case .raceTrack: return RouteMapMarkerColors.discoveryRaceTrackOrange
        case .savedPlace: return RouteMapMarkerColors.discoverySavedPlaceTeal
        }
    }

    private static func lodBucket(latitudeDelta: Double) -> String {
        switch presentation(latitudeDelta: latitudeDelta) {
        case .dot: return "dot"
        case .endpointPin, .pin:
            if latitudeDelta <= pinFullSizeMaxLatitudeDelta { return "full" }
            return "scale"
        }
    }

    static func annotationRefreshID(
        id: String,
        kind: MapDiscoveryMarkerKind,
        latitudeDelta: Double
    ) -> String {
        switch presentation(latitudeDelta: latitudeDelta) {
        case .dot:
            return "\(id)-\(kind)-dot"
        case .endpointPin, .pin:
            let bucket = Int((pinScale(latitudeDelta: latitudeDelta) * 10).rounded())
            return "\(id)-\(kind)-pin-\(lodBucket(latitudeDelta: latitudeDelta))-\(bucket)"
        }
    }
}

struct MapDiscoveryMarkerLODView<Pin: View>: View {
    let kind: MapDiscoveryMarkerKind
    let latitudeDelta: Double
    var clusterCount: Int? = nil
    @ViewBuilder var pinContent: (CGFloat) -> Pin

    var body: some View {
        switch MapDiscoveryMarkerLOD.presentation(latitudeDelta: latitudeDelta) {
        case .dot:
            ZStack(alignment: .topTrailing) {
                RouteMapMarkerDotView(color: MapDiscoveryMarkerLOD.dotColor(for: kind))
                if let clusterCount, clusterCount > 1 {
                    Text("\(clusterCount)")
                        .font(.system(size: 9, weight: .heavy, design: .rounded))
                        .foregroundStyle(.black)
                        .frame(minWidth: 16, minHeight: 16)
                        .padding(.horizontal, 3)
                        .background(
                            Circle()
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
                        )
                        .overlay(Circle().stroke(Color.black.opacity(0.35), lineWidth: 1))
                        .offset(x: 8, y: 2)
                        .allowsHitTesting(false)
                }
            }
        case .endpointPin, .pin:
            pinContent(MapDiscoveryMarkerLOD.pinScale(latitudeDelta: latitudeDelta))
        }
    }
}

// MARK: - Geometry helpers

enum RouteMapGeometry {
    /// Mapbox view-annotation priority so overlapping route markers stack naturally:
    /// the marker lower on screen (southern in north-up views) draws above the one above it.
    /// Start and finish always render above path, checkpoint, and stop markers.
    private static let presenceMarkerPriorityBoost = 150_000_000

    static func mapMarkerOverlapPriority(
        for coordinate: CLLocationCoordinate2D,
        markerType: String? = nil,
        tieBreaker: Int = 0
    ) -> Int {
        guard coordinate.latitude.isFinite else { return tieBreaker }
        let latMicrodegrees = Int((coordinate.latitude * 1_000_000).rounded())
        var priority = -latMicrodegrees + tieBreaker
        if markerType == "start" || markerType == "finish" {
            priority += 200_000_000
        }
        return priority
    }

    static func mapDiscoveryMarkerOverlapPriority(
        for coordinate: CLLocationCoordinate2D,
        tieBreaker: Int = 0
    ) -> Int {
        mapMarkerOverlapPriority(for: coordinate, tieBreaker: tieBreaker)
    }

    static func mapPresenceMarkerOverlapPriority(
        for coordinate: CLLocationCoordinate2D,
        tieBreaker: Int = 0
    ) -> Int {
        mapMarkerOverlapPriority(for: coordinate, tieBreaker: tieBreaker) + presenceMarkerPriorityBoost
    }

    static func validCoordinate(from point: RoutePointDTO) -> CLLocationCoordinate2D? {
        let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
        guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { return nil }
        return coordinate
    }

    static func coordinates(from points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        points.compactMap(validCoordinate)
    }

    static func lineCoordinates(road: [RoutePointDTO], points: [RoutePointDTO]) -> [CLLocationCoordinate2D] {
        let roadCoords = coordinates(from: road)
        if roadCoords.count >= 2 { return roadCoords }
        return coordinates(from: points)
    }

    static func startFinishMapPoints(routeID: String, points: [RoutePointDTO]) -> [RouteMapPointModel] {
        mapPoints(routeID: routeID, points: points, includeTypes: ["start", "finish"])
    }

    /// Start/finish markers at the actual driven GPS endpoints (Quick Drives without a saved route).
    static func trailEndpointMapPoints(from samples: [DrivePathSample], idPrefix: String) -> [RouteMapPointModel] {
        let valid = samples.filter {
            CLLocationCoordinate2DIsValid($0.coordinate)
                && $0.coordinate.latitude.isFinite
                && $0.coordinate.longitude.isFinite
        }
        guard valid.count >= 2, let first = valid.first, let last = valid.last else { return [] }
        return [
            RouteMapPointModel(
                id: "\(idPrefix)-trail-start",
                coordinate: first.coordinate,
                markerType: "start",
                index: 0
            ),
            RouteMapPointModel(
                id: "\(idPrefix)-trail-finish",
                coordinate: last.coordinate,
                markerType: "finish",
                index: 1
            ),
        ]
    }

    static func mapPoints(
        routeID: String,
        points: [RoutePointDTO],
        includeTypes: Set<String> = ["start", "waypoint", "stop", "finish"]
    ) -> [RouteMapPointModel] {
        points.enumerated().compactMap { index, point in
            guard let markerType = point.markerType, includeTypes.contains(markerType) else { return nil }
            guard let coordinate = validCoordinate(from: point) else { return nil }
            return RouteMapPointModel(
                id: "\(routeID)-\(index)",
                coordinate: coordinate,
                markerType: markerType,
                index: index
            )
        }
    }

    /// Fits the full route for embedded previews (line + markers), with extra padding so nothing is clipped.
    static func regionToFitRoutePreview(
        lineCoordinates: [CLLocationCoordinate2D],
        markerCoordinates: [CLLocationCoordinate2D],
        fallback: CLLocationCoordinate2D
    ) -> MKCoordinateRegion {
        let boundsCoordinates = (lineCoordinates + markerCoordinates).filter {
            CLLocationCoordinate2DIsValid($0) && $0.latitude.isFinite && $0.longitude.isFinite
        }
        return regionToFit(
            boundsCoordinates,
            fallback: fallback,
            paddingFactor: 3.6,
            minimumDelta: 0.028
        )
    }

    static func regionToFit(
        _ coordinates: [CLLocationCoordinate2D],
        fallback: CLLocationCoordinate2D,
        paddingFactor: Double = 2.0,
        minimumDelta: Double = 0.02
    ) -> MKCoordinateRegion {
        let valid = coordinates.filter {
            CLLocationCoordinate2DIsValid($0) && $0.latitude.isFinite && $0.longitude.isFinite
        }
        guard valid.count >= 2 else {
            return MKCoordinateRegion(
                center: fallback,
                span: MKCoordinateSpan(latitudeDelta: 0.035, longitudeDelta: 0.035)
            )
        }
        var minLat = valid[0].latitude
        var maxLat = minLat
        var minLng = valid[0].longitude
        var maxLng = minLng
        for coordinate in valid.dropFirst() {
            minLat = min(minLat, coordinate.latitude)
            maxLat = max(maxLat, coordinate.latitude)
            minLng = min(minLng, coordinate.longitude)
            maxLng = max(maxLng, coordinate.longitude)
        }
        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2,
            longitude: (minLng + maxLng) / 2
        )
        let latDelta = max((maxLat - minLat) * paddingFactor, minimumDelta)
        var lngDelta = max((maxLng - minLng) * paddingFactor, minimumDelta)
        let latitudeZoomEquivalentLongitudeDelta =
            latDelta * max(0.2, cos(center.latitude * .pi / 180))
        lngDelta = max(lngDelta, latitudeZoomEquivalentLongitudeDelta)
        return MKCoordinateRegion(center: center, span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lngDelta))
    }

    static func centroid(of coordinates: [CLLocationCoordinate2D], fallback: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        let valid = coordinates.filter {
            CLLocationCoordinate2DIsValid($0) && $0.latitude.isFinite && $0.longitude.isFinite
        }
        guard !valid.isEmpty else { return fallback }
        let sum = valid.reduce((lat: 0.0, lng: 0.0)) { partial, coordinate in
            (partial.lat + coordinate.latitude, partial.lng + coordinate.longitude)
        }
        return CLLocationCoordinate2D(
            latitude: sum.lat / Double(valid.count),
            longitude: sum.lng / Double(valid.count)
        )
    }

    static func polylineTotalLength(_ lineCoordinates: [CLLocationCoordinate2D]) -> CLLocationDistance {
        guard lineCoordinates.count >= 2 else { return 0 }
        var total: CLLocationDistance = 0
        for index in 0..<(lineCoordinates.count - 1) {
            let start = MKMapPoint(lineCoordinates[index])
            let end = MKMapPoint(lineCoordinates[index + 1])
            total += start.distance(to: end)
        }
        return total
    }

    static func coordinateAtArcLength(
        _ targetArcLength: Double,
        on lineCoordinates: [CLLocationCoordinate2D]
    ) -> CLLocationCoordinate2D? {
        guard lineCoordinates.count >= 2, targetArcLength >= 0 else { return nil }
        var cumulativeDistance: CLLocationDistance = 0

        for index in 0..<(lineCoordinates.count - 1) {
            let startCoordinate = lineCoordinates[index]
            let endCoordinate = lineCoordinates[index + 1]
            let start = MKMapPoint(startCoordinate)
            let end = MKMapPoint(endCoordinate)
            let segmentDistance = start.distance(to: end)
            guard segmentDistance > 0 else { continue }

            if cumulativeDistance + segmentDistance >= targetArcLength {
                let remaining = targetArcLength - cumulativeDistance
                let t = remaining / segmentDistance
                let segmentDX = end.x - start.x
                let segmentDY = end.y - start.y
                return MKMapPoint(
                    x: start.x + segmentDX * t,
                    y: start.y + segmentDY * t
                ).coordinate
            }
            cumulativeDistance += segmentDistance
        }

        if abs(targetArcLength - cumulativeDistance) <= 1 {
            return lineCoordinates.last
        }
        return nil
    }

    static func bearingBetween(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> Double {
        let lat1 = from.latitude * .pi / 180
        let lat2 = to.latitude * .pi / 180
        let deltaLon = (to.longitude - from.longitude) * .pi / 180
        let y = sin(deltaLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        let radians = atan2(y, x)
        let degrees = radians * 180 / .pi
        return degrees >= 0 ? degrees : degrees + 360
    }

    static func allProjectionsOntoPolyline(
        _ coordinate: CLLocationCoordinate2D,
        lineCoordinates: [CLLocationCoordinate2D]
    ) -> [RoutePolylineProjection] {
        guard lineCoordinates.count >= 2 else { return [] }
        let target = MKMapPoint(coordinate)
        var projections: [RoutePolylineProjection] = []
        var cumulativeDistance: CLLocationDistance = 0

        for index in 0..<(lineCoordinates.count - 1) {
            let startCoordinate = lineCoordinates[index]
            let endCoordinate = lineCoordinates[index + 1]
            let start = MKMapPoint(startCoordinate)
            let end = MKMapPoint(endCoordinate)
            let segmentDX = end.x - start.x
            let segmentDY = end.y - start.y
            let segmentLengthSquared = segmentDX * segmentDX + segmentDY * segmentDY
            let rawT: Double
            if segmentLengthSquared <= 0 {
                rawT = 0
            } else {
                rawT = ((target.x - start.x) * segmentDX + (target.y - start.y) * segmentDY) / segmentLengthSquared
            }
            let t = min(1, max(0, rawT))
            let projectedPoint = MKMapPoint(
                x: start.x + segmentDX * t,
                y: start.y + segmentDY * t
            )
            let segmentDistance = start.distance(to: end)
            let distance = target.distance(to: projectedPoint)
            let arcLength = cumulativeDistance + (segmentDistance * t)
            projections.append(
                RoutePolylineProjection(
                    coordinate: projectedPoint.coordinate,
                    distanceMeters: distance,
                    arcLengthMeters: arcLength,
                    segmentIndex: index,
                    segmentBearingDegrees: bearingBetween(from: startCoordinate, to: endCoordinate)
                )
            )
            cumulativeDistance += segmentDistance
        }
        return projections
    }

    static func projectOntoPolyline(
        _ coordinate: CLLocationCoordinate2D,
        onto lineCoordinates: [CLLocationCoordinate2D]
    ) -> RoutePolylineProjection? {
        allProjectionsOntoPolyline(coordinate, lineCoordinates: lineCoordinates)
            .min(by: { $0.distanceMeters < $1.distanceMeters })
    }

    static func projectOntoPolyline(
        _ coordinate: CLLocationCoordinate2D,
        onto lineCoordinates: [CLLocationCoordinate2D],
        preferredArcLength: Double?,
        searchWindowMeters: CLLocationDistance = 350
    ) -> RoutePolylineProjection? {
        let projections = allProjectionsOntoPolyline(coordinate, lineCoordinates: lineCoordinates)
        guard let preferredArcLength else {
            return projections.min(by: { $0.distanceMeters < $1.distanceMeters })
        }
        let inWindow = projections.filter {
            $0.arcLengthMeters >= preferredArcLength - 50 && $0.arcLengthMeters <= preferredArcLength + searchWindowMeters
        }
        if let best = inWindow.min(by: {
            abs($0.arcLengthMeters - preferredArcLength) < abs($1.arcLengthMeters - preferredArcLength)
        }) {
            return best
        }
        return projections.min(by: { $0.distanceMeters < $1.distanceMeters })
    }
}

struct RoutePolylineProjection: Equatable {
    let coordinate: CLLocationCoordinate2D
    let distanceMeters: CLLocationDistance
    let arcLengthMeters: Double
    let segmentIndex: Int
    let segmentBearingDegrees: Double
}

// MARK: - Map line layers

enum RouteMapLineLayers {
    static func source(id: String, coordinates: [CLLocationCoordinate2D]) -> GeoJSONSource {
        GeoJSONSource(id: id)
            .data(.feature(Feature(geometry: Geometry(LineString(coordinates)))))
    }

    static func glowLayer(sourceID: String, layerID: String, palette: RouteMapLinePalette) -> LineLayer {
        LineLayer(id: layerID, source: sourceID)
            .lineCap(.round)
            .lineJoin(.round)
            .lineColor(palette.glowStyleColor)
            .lineOpacity(palette.glowOpacity)
            .lineBlur(palette.glowBlur)
            .lineWidth(palette.glowWidth)
            .lineEmissiveStrength(palette.glowEmissive)
            .slot(.top)
    }

    static func mainLayer(sourceID: String, layerID: String, palette: RouteMapLinePalette) -> LineLayer {
        LineLayer(id: layerID, source: sourceID)
            .lineCap(.round)
            .lineJoin(.round)
            .lineColor(palette.mainStyleColor)
            .lineOpacity(1)
            .lineBlur(0)
            .lineWidth(palette.mainWidth)
            .lineEmissiveStrength(palette.lineEmissive)
            .slot(.top)
    }

    static func coreLayer(sourceID: String, layerID: String, palette: RouteMapLinePalette) -> LineLayer {
        LineLayer(id: layerID, source: sourceID)
            .lineCap(.round)
            .lineJoin(.round)
            .lineColor(palette.coreStyleColor)
            .lineOpacity(1)
            .lineBlur(0)
            .lineWidth(palette.coreWidth)
            .lineEmissiveStrength(palette.lineEmissive)
            .slot(.top)
    }
}

struct RouteMapLineMapContent: MapboxMaps.MapContent {
    let sourceID: String
    let coordinates: [CLLocationCoordinate2D]
    let palette: RouteMapLinePalette
    var simplifiedLayers: Bool = false

    var body: some MapboxMaps.MapContent {
        if coordinates.count >= 2 {
            RouteMapLineLayers.source(id: sourceID, coordinates: coordinates)
            if simplifiedLayers {
                RouteMapLineLayers.mainLayer(sourceID: sourceID, layerID: "\(sourceID)-main", palette: palette)
            } else {
                RouteMapLineLayers.glowLayer(sourceID: sourceID, layerID: "\(sourceID)-glow", palette: palette)
                RouteMapLineLayers.mainLayer(sourceID: sourceID, layerID: "\(sourceID)-main", palette: palette)
                RouteMapLineLayers.coreLayer(sourceID: sourceID, layerID: "\(sourceID)-core", palette: palette)
            }
        }
    }
}

// MARK: - Speed gradient driven path

enum RouteSpeedGradientLayers {
    // Parity with Android RouteMapMapboxLayers speed-gradient stack.
    private static let underStrokeColor = StyleColor(UIColor(red: 6 / 255, green: 10 / 255, blue: 18 / 255, alpha: 1))
    private static let underStrokeWidth = 9.1
    private static let underStrokeBlur = 1.0
    private static let underStrokeOpacity = 0.9
    private static let gradientLineWidth = 5.85

    static func source(id: String, coordinates: [CLLocationCoordinate2D]) -> GeoJSONSource {
        var source = GeoJSONSource(id: id)
        source.data = .feature(Feature(geometry: .lineString(LineString(coordinates))))
        source.lineMetrics = true
        return source
    }

    static func underStrokeLayer(sourceID: String, layerID: String) -> LineLayer {
        LineLayer(id: layerID, source: sourceID)
            .lineCap(.round)
            .lineJoin(.round)
            .lineColor(underStrokeColor)
            .lineOpacity(underStrokeOpacity)
            .lineBlur(underStrokeBlur)
            .lineWidth(underStrokeWidth)
            .slot(.top)
    }

    static func gradientLineLayer(sourceID: String, layerID: String, gradient: Exp) -> LineLayer {
        LineLayer(id: layerID, source: sourceID)
            .lineCap(.round)
            .lineJoin(.round)
            .lineGradient(gradient)
            .lineOpacity(1)
            .lineBlur(0)
            .lineWidth(gradientLineWidth)
            .lineEmissiveStrength(1)
            .slot(.top)
    }
}

struct RouteSpeedGradientMapContent: MapboxMaps.MapContent {
    let sourceID: String
    let samples: [DrivePathSample]

    var body: some MapboxMaps.MapContent {
        if DriveSpeedGradient.hasUsableSpeedPathData(samples) {
            let vertices = DriveSpeedGradient.buildRenderVertices(from: samples)
            if vertices.count >= 2,
               let gradient = DriveSpeedGradient.trailGradientExpression(vertices: vertices) {
                let coordinates = vertices.map(\.coordinate)
                RouteSpeedGradientLayers.source(id: sourceID, coordinates: coordinates)
                RouteSpeedGradientLayers.underStrokeLayer(
                    sourceID: sourceID,
                    layerID: "\(sourceID)-speed-under"
                )
                RouteSpeedGradientLayers.gradientLineLayer(
                    sourceID: sourceID,
                    layerID: "\(sourceID)-speed-line",
                    gradient: gradient
                )
            }
        }
    }
}

// MARK: - Drive route preview

enum DriveRouteMapPreviewCamera {
    static func viewport(
        lineCoordinates: [CLLocationCoordinate2D],
        pathSamples: [DrivePathSample],
        mapPoints: [RouteMapPointModel]
    ) -> Viewport {
        let previewLineCoordinates = DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)
            ? DriveSpeedGradient.pathCoordinates(from: pathSamples)
            : lineCoordinates
        let fallback = RouteMapGeometry.centroid(
            of: previewLineCoordinates,
            fallback: CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
        )
        let markerCoordinates = mapPoints.map(\.coordinate)
        let region = RouteMapGeometry.regionToFitRoutePreview(
            lineCoordinates: previewLineCoordinates,
            markerCoordinates: markerCoordinates,
            fallback: fallback
        )
        return OttoMapboxCamera.viewport(for: region)
    }
}

struct DriveRouteMapPreview: View {
    let lineCoordinates: [CLLocationCoordinate2D]
    let mapPoints: [RouteMapPointModel]
    let completedWaypointIndexes: Set<Int>
    var pathSamples: [DrivePathSample] = []
    var height: CGFloat = 200
    var markerScale: CGFloat = 0.55
    var lineSourceID: String = "drive-summary-route-line"
    var animateOnAppear: Bool = true

    @State private var viewport: Viewport
    @State private var hasAppeared = false

    init(
        lineCoordinates: [CLLocationCoordinate2D],
        mapPoints: [RouteMapPointModel],
        completedWaypointIndexes: Set<Int>,
        pathSamples: [DrivePathSample] = [],
        height: CGFloat = 200,
        markerScale: CGFloat = 0.55,
        lineSourceID: String = "drive-summary-route-line",
        animateOnAppear: Bool = true
    ) {
        self.lineCoordinates = lineCoordinates
        self.mapPoints = mapPoints
        self.completedWaypointIndexes = completedWaypointIndexes
        self.pathSamples = pathSamples
        self.height = height
        self.markerScale = markerScale
        self.lineSourceID = lineSourceID
        self.animateOnAppear = animateOnAppear

        _viewport = State(
            initialValue: DriveRouteMapPreviewCamera.viewport(
                lineCoordinates: lineCoordinates,
                pathSamples: pathSamples,
                mapPoints: mapPoints
            )
        )
    }

    private var pathSamplesSignature: String {
        guard let first = pathSamples.first, let last = pathSamples.last else { return "empty" }
        return "\(pathSamples.count)-\(first.coordinate.latitude)-\(first.coordinate.longitude)-\(last.coordinate.latitude)-\(last.coordinate.longitude)"
    }

    private var lineCoordinatesSignature: String {
        guard let first = lineCoordinates.first, let last = lineCoordinates.last else { return "empty" }
        return "\(lineCoordinates.count)-\(first.latitude)-\(first.longitude)-\(last.latitude)-\(last.longitude)"
    }

    private func refitViewport() {
        viewport = DriveRouteMapPreviewCamera.viewport(
            lineCoordinates: lineCoordinates,
            pathSamples: pathSamples,
            mapPoints: mapPoints
        )
    }

    var body: some View {
        OttoMapboxMapView(
            viewport: $viewport,
            allowsInteraction: false,
            onCameraChanged: { _ in },
            onUserGesture: {},
            onMapLoaded: {}
        ) {
            if DriveSpeedGradient.hasUsableSpeedPathData(pathSamples) {
                RouteSpeedGradientMapContent(
                    sourceID: "\(lineSourceID)-speed",
                    samples: pathSamples
                )
            } else if lineCoordinates.count >= 2 {
                RouteMapLineMapContent(
                    sourceID: lineSourceID,
                    coordinates: lineCoordinates,
                    palette: .livePurple
                )
            }

            ForEvery(mapPoints) { point in
                MapViewAnnotation(coordinate: point.coordinate) {
                    RouteMapMarkerView(
                        markerType: point.markerType,
                        isCompleted: point.isCompleted(in: completedWaypointIndexes),
                        scale: markerScale
                    )
                    .scaleEffect(animateOnAppear && hasAppeared ? 1 : 0.4)
                    .opacity(animateOnAppear && hasAppeared ? 1 : 0)
                    .animation(
                        .spring(response: 0.34, dampingFraction: 0.72)
                            .delay(0.12 + Double(point.index) * 0.04),
                        value: hasAppeared
                    )
                }
                .allowOverlap(true)
                .priority(RouteMapGeometry.mapMarkerOverlapPriority(
                    for: point.coordinate,
                    markerType: point.markerType,
                    tieBreaker: point.index
                ))
            }
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .onAppear {
            guard animateOnAppear else {
                hasAppeared = true
                return
            }
            withAnimation(.easeOut(duration: 0.35)) {
                hasAppeared = true
            }
        }
        .onChange(of: pathSamplesSignature) { _, _ in
            refitViewport()
        }
        .onChange(of: lineCoordinatesSignature) { _, _ in
            refitViewport()
        }
    }
}

// MARK: - DTO bridges

extension SavedRouteDTO {
    func toMapLineCoordinates() -> [CLLocationCoordinate2D] {
        RouteMapGeometry.lineCoordinates(road: roadCoordinates, points: points)
    }

    func toMapPoints() -> [RouteMapPointModel] {
        RouteMapGeometry.mapPoints(routeID: id, points: points)
    }
}

extension DriveRouteDTO {
    func toMapLineCoordinates() -> [CLLocationCoordinate2D] {
        RouteMapGeometry.lineCoordinates(road: roadCoordinates, points: points)
    }

    func toMapPoints() -> [RouteMapPointModel] {
        RouteMapGeometry.mapPoints(routeID: id, points: points)
    }

    func toStartFinishMapPoints() -> [RouteMapPointModel] {
        RouteMapGeometry.startFinishMapPoints(routeID: id, points: points)
    }

    func toSavedRoute(createdByUserId: String) -> SavedRouteDTO {
        SavedRouteDTO(
            id: id,
            createdByUserId: createdByUserId,
            name: name,
            points: points,
            roadCoordinates: roadCoordinates,
            distanceMeters: distanceMeters,
            etaSeconds: etaSeconds,
            createdAt: nil,
            updatedAt: nil
        )
    }
}
