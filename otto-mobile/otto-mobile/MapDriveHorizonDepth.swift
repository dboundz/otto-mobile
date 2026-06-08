import CoreLocation
import Foundation

/// Map-proportional marker scale and distance-based overlap priority during pitched drive follow.
enum MapDriveHorizonDepth {
    /// Checkpoints farther than this from the user are not shown on the map tab route layer.
    static let checkpointVisibleMaxDistanceMeters: Double = 1609.344

    static let routeMinScale: CGFloat = 0.55
    static let presenceMinScale: CGFloat = 0.50
    private static let scaleQuantizationStep: CGFloat = 0.05
    private static let endpointPriorityBoost = 200_000_000
    private static let presencePriorityBoost = 150_000_000
    private static let priorityBase = 1_000_000

    /// Visible north–south span in meters from the same latitude-delta signal as marker LOD.
    static func visibleMapHeightMeters(latitudeDelta: Double) -> Double {
        guard latitudeDelta.isFinite, latitudeDelta > 0 else { return 111_000 }
        return max(50, latitudeDelta * 111_000)
    }

    static func horizonScale(
        distanceMeters: Double,
        visibleMapHeightMeters: Double,
        minScale: CGFloat = routeMinScale
    ) -> CGFloat {
        guard distanceMeters.isFinite, visibleMapHeightMeters.isFinite, visibleMapHeightMeters > 0 else {
            return 1
        }
        let t = min(1, max(0, distanceMeters / visibleMapHeightMeters))
        let minS = Double(minScale)
        let raw = max(minS, 1.0 - t * (1.0 - minS))
        return quantizeScale(CGFloat(raw))
    }

    static func driveRouteOverlapPriority(
        distanceMeters: Double,
        markerType: String?,
        tieBreaker: Int = 0
    ) -> Int {
        guard distanceMeters.isFinite else { return tieBreaker }
        var priority = priorityBase - Int(distanceMeters.rounded()) + tieBreaker
        if markerType == "start" || markerType == "finish" {
            priority += endpointPriorityBoost
        }
        return priority
    }

    static func drivePresenceOverlapPriority(
        distanceMeters: Double,
        tieBreaker: Int = 0
    ) -> Int {
        driveRouteOverlapPriority(
            distanceMeters: distanceMeters,
            markerType: nil,
            tieBreaker: tieBreaker
        ) + presencePriorityBoost
    }

    static func distanceMeters(
        from user: CLLocation,
        to coordinate: CLLocationCoordinate2D
    ) -> Double {
        user.distance(
            from: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        )
    }

    /// Waypoints only; start/finish/stop are always eligible for display.
    static func shouldShowRouteMarker(
        markerType: String?,
        distanceMeters: Double?
    ) -> Bool {
        guard markerType == "waypoint" else { return true }
        guard let distanceMeters, distanceMeters.isFinite else { return true }
        return distanceMeters <= checkpointVisibleMaxDistanceMeters
    }

    /// Presence pins during pitched drive follow — same ~1 mi horizon as route checkpoints.
    static func shouldShowPresenceMarker(distanceMeters: Double?) -> Bool {
        guard let distanceMeters, distanceMeters.isFinite else { return true }
        return distanceMeters <= checkpointVisibleMaxDistanceMeters
    }

    private static func quantizeScale(_ scale: CGFloat) -> CGFloat {
        let step = scaleQuantizationStep
        return (scale / step).rounded() * step
    }
}
