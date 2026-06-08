import CoreLocation
import MapKit

enum MapCoordinateReverseGeocoder {
    struct Result: Equatable {
        let name: String?
        let address: String?
    }

    static func reverseGeocode(at coordinate: CLLocationCoordinate2D) async -> Result {
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        guard let request = MKReverseGeocodingRequest(location: location) else {
            return Result(name: nil, address: nil)
        }
        do {
            let items = try await request.mapItems
            guard let item = items.first else {
                return Result(name: nil, address: nil)
            }

            func nonEmptyTrimmed(_ s: String?) -> String? {
                guard let t = s?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty else { return nil }
                return t
            }

            let shortAddr = nonEmptyTrimmed(item.address?.shortAddress)
            let fullAddr = nonEmptyTrimmed(item.address?.fullAddress)
            let itemName = nonEmptyTrimmed(item.name)

            let name = itemName ?? shortAddr
            let addr = fullAddr ?? shortAddr
            return Result(name: name, address: addr)
        } catch {
            return Result(name: nil, address: nil)
        }
    }
}
