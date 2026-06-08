import CoreLocation
import MapKit

/// Camera framing for map presence / squad follow (distinct from local route preview caps).
enum MapPresenceCamera {
    static let worldViewMinSpreadMeters = 8_000_000.0
    static let worldViewCenter = CLLocationCoordinate2D(latitude: 20, longitude: 0)
    static let worldViewRegion = MKCoordinateRegion(
        center: worldViewCenter,
        span: MKCoordinateSpan(latitudeDelta: 120, longitudeDelta: 360)
    )
    static let singlePinSpan = MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)

    static func maxPairwiseHaversineMeters(_ coordinates: [CLLocationCoordinate2D]) -> Double {
        let valid = coordinates.filter(isValidCoordinate)
        guard valid.count >= 2 else { return 0 }
        var maxDistance = 0.0
        for i in 0 ..< valid.count {
            for j in (i + 1) ..< valid.count {
                let distance = haversineMeters(
                    lat1: valid[i].latitude,
                    lon1: valid[i].longitude,
                    lat2: valid[j].latitude,
                    lon2: valid[j].longitude
                )
                maxDistance = max(maxDistance, distance)
            }
        }
        return maxDistance
    }

    static func requiresWorldView(_ coordinates: [CLLocationCoordinate2D]) -> Bool {
        maxPairwiseHaversineMeters(coordinates) > worldViewMinSpreadMeters
    }

    static func regionForPresenceCoordinates(
        _ coordinates: [CLLocationCoordinate2D],
        paddingFactor: Double = 2.2,
        minimumDelta: Double = 0.016
    ) -> MKCoordinateRegion? {
        let valid = coordinates.filter(isValidCoordinate)
        guard !valid.isEmpty else { return nil }
        if requiresWorldView(valid) {
            return worldViewRegion
        }
        if valid.count == 1 {
            return MKCoordinateRegion(center: valid[0], span: singlePinSpan)
        }

        var minLat = valid[0].latitude
        var maxLat = minLat
        var minLon = valid[0].longitude
        var maxLon = minLon
        for coordinate in valid.dropFirst() {
            minLat = min(minLat, coordinate.latitude)
            maxLat = max(maxLat, coordinate.latitude)
            minLon = min(minLon, coordinate.longitude)
            maxLon = max(maxLon, coordinate.longitude)
        }
        let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        guard isValidCoordinate(center) else { return nil }

        var latDelta = max((maxLat - minLat) * paddingFactor, minimumDelta)
        var lonDelta = max((maxLon - minLon) * paddingFactor, minimumDelta)
        let latitudeZoomEquivalentLongitudeDelta =
            latDelta * max(0.2, cos(center.latitude * .pi / 180))
        lonDelta = max(lonDelta, latitudeZoomEquivalentLongitudeDelta)
        return MKCoordinateRegion(center: center, span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta))
    }

    private static func isValidCoordinate(_ coordinate: CLLocationCoordinate2D) -> Bool {
        CLLocationCoordinate2DIsValid(coordinate)
            && coordinate.latitude.isFinite
            && coordinate.longitude.isFinite
    }

    private static func haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let earthRadiusMeters = 6_371_000.0
        let r1 = lat1 * Double.pi / 180
        let r2 = lat2 * Double.pi / 180
        let dLat = (lat2 - lat1) * Double.pi / 180
        let dLon = (lon2 - lon1) * Double.pi / 180
        let a =
            sin(dLat / 2) * sin(dLat / 2) +
            cos(r1) * cos(r2) * sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
