import CoreLocation
import UIKit

/// Route geometry for generating a static map JPEG at drive-share time (client-side Mapbox Static Images).
struct DriveMapPreviewSnapshotInput: Sendable {
    struct Point: Sendable {
        let lat: Double
        let lng: Double
        let type: String?
    }

    let roadCoordinates: [Point]
    let routePoints: [Point]
    let pathSamples: [DrivePathSample]

    init?(route: DriveRouteDTO?, pathSamples: [DrivePathSample] = []) {
        roadCoordinates = route?.roadCoordinates.map { Point(lat: $0.lat, lng: $0.lng, type: nil) } ?? []
        routePoints = route?.points.map { Point(lat: $0.lat, lng: $0.lng, type: $0.markerType) } ?? []
        self.pathSamples = pathSamples
        guard Self.hasDrawableLine(roadCoordinates: roadCoordinates, routePoints: routePoints, pathSamples: pathSamples) else {
            return nil
        }
    }

    init(roadCoordinates: [Point], routePoints: [Point], pathSamples: [DrivePathSample]) {
        self.roadCoordinates = roadCoordinates
        self.routePoints = routePoints
        self.pathSamples = pathSamples
    }

    var hasPathSamples: Bool {
        pathSamples.filter {
            CLLocationCoordinate2DIsValid($0.coordinate)
                && $0.coordinate.latitude.isFinite
                && $0.coordinate.longitude.isFinite
        }.count >= 2
    }

    static func hasDrawableLine(
        roadCoordinates: [Point],
        routePoints: [Point],
        pathSamples: [DrivePathSample]
    ) -> Bool {
        if DriveSpeedGradient.hasUsableSpeedPathData(pathSamples) {
            return true
        }
        return DriveRouteMapSnapshotGenerator.lineCoordinates(
            pathSamples: pathSamples,
            roadCoordinates: roadCoordinates,
            routePoints: routePoints
        ).count >= 2
    }
}

enum DriveMapPreviewSnapshotResolver {
    /// Enriches preloaded snapshot input with fetched GPS trail when needed.
    static func resolve(
        preloaded: DriveMapPreviewSnapshotInput?,
        driveId: String,
        circleId: String?
    ) async -> DriveMapPreviewSnapshotInput? {
        if let preloaded, preloaded.hasPathSamples {
            return preloaded
        }

        var fetchedSamples: [DrivePathSample] = []
        do {
            let points = try await APIClient.shared.fetchDrivePoints(driveId: driveId, circleId: circleId)
            fetchedSamples = points.compactMap(DrivePathSample.from)
        } catch {
            fetchedSamples = []
        }

        if var preloaded {
            if fetchedSamples.isEmpty, !preloaded.hasPathSamples,
               !DriveMapPreviewSnapshotInput.hasDrawableLine(
                   roadCoordinates: preloaded.roadCoordinates,
                   routePoints: preloaded.routePoints,
                   pathSamples: []
               ) {
                return nil
            }
            preloaded = DriveMapPreviewSnapshotInput(
                roadCoordinates: preloaded.roadCoordinates,
                routePoints: preloaded.routePoints,
                pathSamples: fetchedSamples
            )
            return preloaded
        }

        if fetchedSamples.count >= 2 {
            return DriveMapPreviewSnapshotInput(route: nil, pathSamples: fetchedSamples)
        }

        do {
            let drive = try await APIClient.shared.fetchDrive(driveId: driveId, circleId: circleId)
            return DriveMapPreviewSnapshotInput(route: drive.route, pathSamples: fetchedSamples)
        } catch {
            return nil
        }
    }
}

enum MapboxAccessToken {
    static var current: String? {
        (Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty
    }
}

enum DriveRouteMapSnapshotGenerator {
    fileprivate typealias Point = DriveMapPreviewSnapshotInput.Point

    private static let staticWidth = 640
    private static let staticHeight = 280
    private static let staticPadding = 48
    private static let polylineMaxPoints = 100
    private static let gradientSegmentMaxCount = 50
    private static let routeLineColor = "7B3DFF"
    private static let routeLineWidth = 5

    /// Fetches a Mapbox Static Image using the app token and returns JPEG bytes for chat upload.
    static func jpegData(input: DriveMapPreviewSnapshotInput) async -> Data? {
        guard let token = MapboxAccessToken.current,
              let url = buildStaticImageURL(input: input, accessToken: token)
        else {
            return nil
        }

        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 12

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode),
                  let image = UIImage(data: data),
                  let jpeg = image.jpegData(compressionQuality: 0.84)
            else {
                return nil
            }
            return jpeg
        } catch {
            return nil
        }
    }

    static func buildStaticImageURL(input: DriveMapPreviewSnapshotInput, accessToken: String) -> URL? {
        var overlays = lineOverlays(for: input)
        guard !overlays.isEmpty else { return nil }
        overlays.append(contentsOf: startFinishPins(from: input))

        let overlaySegment = overlays.map { $0.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? $0 }
            .joined(separator: ",")
        var components = URLComponents()
        components.scheme = "https"
        components.host = "api.mapbox.com"
        components.path = "/styles/v1/mapbox/dark-v11/static/\(overlaySegment)/auto/\(staticWidth)x\(staticHeight)@2x"
        components.queryItems = [
            URLQueryItem(name: "padding", value: String(staticPadding)),
            URLQueryItem(name: "access_token", value: accessToken),
        ]
        return components.url
    }

    fileprivate static func lineCoordinates(
        pathSamples: [DrivePathSample],
        roadCoordinates: [Point],
        routePoints: [Point]
    ) -> [Point] {
        if DriveSpeedGradient.hasUsableSpeedPathData(pathSamples) {
            let coordinates = DriveSpeedGradient.pathCoordinates(from: pathSamples)
            let points = coordinates.map { Point(lat: $0.latitude, lng: $0.longitude, type: nil) }
            return downsample(points, maxCount: polylineMaxPoints)
        }
        if roadCoordinates.count >= 2 {
            return downsample(roadCoordinates, maxCount: polylineMaxPoints)
        }
        let path = routePoints.filter { ($0.type ?? "path") == "path" }
        if path.count >= 2 {
            return downsample(path, maxCount: polylineMaxPoints)
        }
        return downsample(routePoints, maxCount: polylineMaxPoints)
    }

    private static func lineOverlays(for input: DriveMapPreviewSnapshotInput) -> [String] {
        if DriveSpeedGradient.hasUsableSpeedPathData(input.pathSamples) {
            let segments = DriveSpeedGradient.buildGradientSegments(
                from: input.pathSamples,
                idPrefix: "share-speed",
                maxCount: gradientSegmentMaxCount
            )
            return segments.compactMap { segment in
                guard segment.coordinates.count >= 2 else { return nil }
                let points = segment.coordinates.map {
                    Point(lat: $0.latitude, lng: $0.longitude, type: nil)
                }
                let encoded = encodePolyline(points)
                let color = hexColor(segment.color)
                return "path-\(routeLineWidth)+\(color)-0.92(polyline(\(encoded)))"
            }
        }

        let line = lineCoordinates(
            pathSamples: input.pathSamples,
            roadCoordinates: input.roadCoordinates,
            routePoints: input.routePoints
        )
        guard line.count >= 2 else { return [] }
        let encoded = encodePolyline(line)
        return ["path-\(routeLineWidth)+\(routeLineColor)-0.92(polyline(\(encoded)))"]
    }

    private static func downsample(_ points: [Point], maxCount: Int) -> [Point] {
        RoutePolylineDisplayOptimizer.downsampleIndices(count: points.count, maxCount: maxCount).map { points[$0] }
    }

    private static func startFinishPins(from input: DriveMapPreviewSnapshotInput) -> [String] {
        let routePins = input.routePoints.compactMap { point -> String? in
            guard point.type == "start" || point.type == "finish" else { return nil }
            let label = point.type == "start" ? "a" : "b"
            return "pin-s-\(label)+\(routeLineColor)(\(point.lng),\(point.lat))"
        }
        if routePins.count >= 2 {
            return routePins
        }

        let valid = input.pathSamples.filter {
            CLLocationCoordinate2DIsValid($0.coordinate)
                && $0.coordinate.latitude.isFinite
                && $0.coordinate.longitude.isFinite
        }
        guard valid.count >= 2, let first = valid.first, let last = valid.last else { return routePins }
        return [
            "pin-s-a+\(routeLineColor)(\(first.coordinate.longitude),\(first.coordinate.latitude))",
            "pin-s-b+\(routeLineColor)(\(last.coordinate.longitude),\(last.coordinate.latitude))",
        ]
    }

    private static func hexColor(_ color: UIColor) -> String {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(format: "%02X%02X%02X", Int(red * 255), Int(green * 255), Int(blue * 255))
    }

    private static func encodePolyline(_ coordinates: [Point]) -> String {
        var lastLat = 0
        var lastLng = 0
        var result = ""
        for point in coordinates {
            let lat = Int((point.lat * 1e5).rounded())
            let lng = Int((point.lng * 1e5).rounded())
            result += encodeSignedVarint(lat - lastLat)
            result += encodeSignedVarint(lng - lastLng)
            lastLat = lat
            lastLng = lng
        }
        return result
    }

    private static func encodeSignedVarint(_ value: Int) -> String {
        var v = value < 0 ? ~(value << 1) : value << 1
        var encoded = ""
        while v >= 0x20 {
            encoded.append(Character(UnicodeScalar((0x20 | (v & 0x1f)) + 63)!))
            v >>= 5
        }
        encoded.append(Character(UnicodeScalar(v + 63)!))
        return encoded
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
