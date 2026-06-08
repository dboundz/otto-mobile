import CoreLocation
import Foundation

enum RouteBuilderMapMarkerLODTier: Equatable {
    case regional
    case icon
    case street

    static func from(
        latitudeDelta: Double,
        regionalMinLatitudeDelta: Double,
        streetMaxLatitudeDelta: Double
    ) -> RouteBuilderMapMarkerLODTier {
        if latitudeDelta > regionalMinLatitudeDelta { return .regional }
        if latitudeDelta <= streetMaxLatitudeDelta { return .street }
        return .icon
    }
}

enum RoutePolylineDisplayOptimizer {
    enum Budget {
        static let regionalMaxPoints = 400
        static let regionalWideMaxPoints = 150
        static let regionalContinentalMaxPoints = 64
        static let iconMaxPoints = 1_500
        static let streetMaxPoints = 3_000
    }

    static func downsample(
        _ coordinates: [CLLocationCoordinate2D],
        maxCount: Int
    ) -> [CLLocationCoordinate2D] {
        guard coordinates.count > maxCount, maxCount >= 2 else { return coordinates }
        return downsampleIndices(count: coordinates.count, maxCount: maxCount).map { coordinates[$0] }
    }

    static func downsampleIndices(count: Int, maxCount: Int) -> [Int] {
        guard count > maxCount, maxCount >= 2 else { return Array(0..<count) }

        let stride = Int(ceil(Double(count) / Double(maxCount)))
        var indices: [Int] = []
        indices.reserveCapacity(maxCount + 1)

        var index = 0
        while index < count {
            indices.append(index)
            index += stride
        }

        let last = count - 1
        if indices.last != last {
            indices.append(last)
        }

        return indices
    }

    static func maxPointBudget(for tier: RouteBuilderMapMarkerLODTier) -> Int {
        switch tier {
        case .regional: return Budget.regionalMaxPoints
        case .icon: return Budget.iconMaxPoints
        case .street: return Budget.streetMaxPoints
        }
    }

    static func maxPointBudget(
        for tier: RouteBuilderMapMarkerLODTier,
        latitudeDelta: Double,
        regionalMinLatitudeDelta: Double
    ) -> Int {
        let base = maxPointBudget(for: tier)
        guard tier == .regional, regionalMinLatitudeDelta > 0 else { return base }
        let ratio = latitudeDelta / regionalMinLatitudeDelta
        if ratio > 80 { return min(base, Budget.regionalContinentalMaxPoints) }
        if ratio > 15 { return min(base, Budget.regionalWideMaxPoints) }
        return base
    }

    static func displayCoordinates(
        from fullCoordinates: [CLLocationCoordinate2D],
        latitudeDelta: Double,
        regionalMinLatitudeDelta: Double,
        streetMaxLatitudeDelta: Double
    ) -> [CLLocationCoordinate2D] {
        guard fullCoordinates.count >= 2 else { return fullCoordinates }
        let tier = RouteBuilderMapMarkerLODTier.from(
            latitudeDelta: latitudeDelta,
            regionalMinLatitudeDelta: regionalMinLatitudeDelta,
            streetMaxLatitudeDelta: streetMaxLatitudeDelta
        )
        return downsample(fullCoordinates, maxCount: maxPointBudget(for: tier))
    }

    static func displayCoordinates(
        from fullCoordinates: [CLLocationCoordinate2D],
        lodTier: RouteBuilderMapMarkerLODTier
    ) -> [CLLocationCoordinate2D] {
        guard fullCoordinates.count >= 2 else { return fullCoordinates }
        return downsample(fullCoordinates, maxCount: maxPointBudget(for: lodTier))
    }

    static func displayCoordinates(
        from fullCoordinates: [CLLocationCoordinate2D],
        lodTier: RouteBuilderMapMarkerLODTier,
        latitudeDelta: Double,
        regionalMinLatitudeDelta: Double
    ) -> [CLLocationCoordinate2D] {
        guard fullCoordinates.count >= 2 else { return fullCoordinates }
        return downsample(
            fullCoordinates,
            maxCount: maxPointBudget(
                for: lodTier,
                latitudeDelta: latitudeDelta,
                regionalMinLatitudeDelta: regionalMinLatitudeDelta
            )
        )
    }

    static func fingerprint(_ coordinates: [CLLocationCoordinate2D]) -> String {
        guard let first = coordinates.first, let last = coordinates.last else { return "empty" }
        let mid = coordinates[coordinates.count / 2]
        return "\(coordinates.count)|\(coordinateToken(first))|\(coordinateToken(mid))|\(coordinateToken(last))"
    }

    private static func coordinateToken(_ coordinate: CLLocationCoordinate2D) -> String {
        String(format: "%.5f,%.5f", coordinate.latitude, coordinate.longitude)
    }
}
