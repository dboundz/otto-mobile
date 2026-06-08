import CoreLocation
import UIKit

/// Static map JPEG for place chat attachments (Mapbox Static Images + composited saved-place pin).
enum PlaceMapSnapshotGenerator {
    private static let staticWidth = 640
    private static let staticHeight = 280
    private static let staticZoom = 18
    /// ~2× live map pin (`OttoMapSavedPlaceMarker` 56×84 pt) for chat preview legibility.
    private static let pinWidth: CGFloat = 112
    private static let pinHeight: CGFloat = 168

    static func jpegData(latitude: Double, longitude: Double) async -> Data? {
        guard CLLocationCoordinate2DIsValid(CLLocationCoordinate2D(latitude: latitude, longitude: longitude)),
              latitude.isFinite,
              longitude.isFinite,
              let token = MapboxAccessToken.current,
              let url = buildStaticImageURL(latitude: latitude, longitude: longitude, accessToken: token)
        else {
            return nil
        }

        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 12

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode),
                  let baseMap = UIImage(data: data),
                  let composited = compositeSavedPlacePin(on: baseMap),
                  let jpeg = composited.jpegData(compressionQuality: 0.84)
            else {
                return nil
            }
            return jpeg
        } catch {
            return nil
        }
    }

    static func buildStaticImageURL(latitude: Double, longitude: Double, accessToken: String) -> URL? {
        let lng = longitude
        let lat = latitude
        var components = URLComponents()
        components.scheme = "https"
        components.host = "api.mapbox.com"
        components.path =
            "/styles/v1/mapbox/dark-v11/static/\(lng),\(lat),\(staticZoom)/\(staticWidth)x\(staticHeight)@2x"
        components.queryItems = [
            URLQueryItem(name: "access_token", value: accessToken),
        ]
        return components.url
    }

    private static func compositeSavedPlacePin(on baseMap: UIImage) -> UIImage? {
        guard let pin = UIImage(named: "map-point-saved") else { return baseMap }

        let size = baseMap.size
        let format = UIGraphicsImageRendererFormat()
        format.scale = baseMap.scale
        format.opaque = true

        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            baseMap.draw(at: .zero)
            let pinRect = CGRect(
                x: (size.width - pinWidth) / 2,
                y: size.height / 2 - pinHeight,
                width: pinWidth,
                height: pinHeight
            )
            pin.draw(in: pinRect)
        }
    }
}
