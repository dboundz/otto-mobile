import CoreLocation
import Foundation

/// Precomputed display polylines for each LOD bucket. Rebuilt when full `roadCoordinates` change.
struct RoutePolylineDisplayCache: Equatable {
    let sourceFingerprint: String
    let street: [CLLocationCoordinate2D]
    let icon: [CLLocationCoordinate2D]
    let regional: [CLLocationCoordinate2D]
    let continental: [CLLocationCoordinate2D]

    static func build(from fullCoordinates: [CLLocationCoordinate2D]) -> RoutePolylineDisplayCache {
        let fingerprint = RoutePolylineDisplayOptimizer.fingerprint(fullCoordinates)
        guard fullCoordinates.count >= 2 else {
            return RoutePolylineDisplayCache(
                sourceFingerprint: fingerprint,
                street: fullCoordinates,
                icon: fullCoordinates,
                regional: fullCoordinates,
                continental: fullCoordinates
            )
        }

        return RoutePolylineDisplayCache(
            sourceFingerprint: fingerprint,
            street: RoutePolylineDisplayOptimizer.downsample(
                fullCoordinates,
                maxCount: RoutePolylineDisplayOptimizer.Budget.streetMaxPoints
            ),
            icon: RoutePolylineDisplayOptimizer.downsample(
                fullCoordinates,
                maxCount: RoutePolylineDisplayOptimizer.Budget.iconMaxPoints
            ),
            regional: RoutePolylineDisplayOptimizer.downsample(
                fullCoordinates,
                maxCount: RoutePolylineDisplayOptimizer.Budget.regionalMaxPoints
            ),
            continental: RoutePolylineDisplayOptimizer.downsample(
                fullCoordinates,
                maxCount: RoutePolylineDisplayOptimizer.Budget.regionalContinentalMaxPoints
            )
        )
    }

    func displayCoordinates(
        for tier: RouteBuilderMapMarkerLODTier,
        latitudeDelta: Double,
        regionalMinLatitudeDelta: Double
    ) -> [CLLocationCoordinate2D] {
        guard street.count >= 2 || icon.count >= 2 || regional.count >= 2 else {
            return street
        }

        if tier == .regional, regionalMinLatitudeDelta > 0 {
            let ratio = latitudeDelta / regionalMinLatitudeDelta
            if ratio > 80 { return continental }
            if ratio > 15 { return downsample(regional, maxCount: RoutePolylineDisplayOptimizer.Budget.regionalWideMaxPoints) }
        }

        switch tier {
        case .street: return street
        case .icon: return icon
        case .regional: return regional
        }
    }

    private func downsample(_ coordinates: [CLLocationCoordinate2D], maxCount: Int) -> [CLLocationCoordinate2D] {
        RoutePolylineDisplayOptimizer.downsample(coordinates, maxCount: maxCount)
    }
}
