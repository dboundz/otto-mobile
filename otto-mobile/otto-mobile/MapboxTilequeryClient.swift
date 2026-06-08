import CoreLocation
import Foundation
import MapboxMaps

struct MapboxTilequeryFeature: Decodable, Equatable {
    let geometry: Geometry?
    let properties: Properties?

    struct Geometry: Decodable, Equatable {
        let type: String
        let coordinates: [Double]

        var pointCoordinate: CLLocationCoordinate2D? {
            guard coordinates.count >= 2 else { return nil }
            return CLLocationCoordinate2D(latitude: coordinates[1], longitude: coordinates[0])
        }
    }

    struct Properties: Decodable, Equatable {
        let tilequery: TilequeryMeta?
    }

    struct TilequeryMeta: Decodable, Equatable {
        let distance: Double
        let geometry: String?
        let layer: String?
    }
}

enum MapboxTilequeryClient {
    static let tilesetID = "mapbox.mapbox-streets-v8"
    static let queryRadiusMeters = 25
    static let maxRoadDistanceMeters = 15.0
    static let maxWaterwayDistanceMeters = 35.0
    static let maxPolygonWaterDistanceMeters = 1.0
    static let defaultFeatureLimit = 50
    static let defaultLayers = "water,waterway,road"

    struct SurfaceClassification: Equatable {
        let onWater: Bool
        let onRoad: Bool
    }

    struct NearestRoadResult {
        let coordinate: CLLocationCoordinate2D
        let distanceMeters: Double
    }

    private struct Response: Decodable {
        let features: [MapboxTilequeryFeature]
    }

    static func classifySurface(features: [MapboxTilequeryFeature]) -> SurfaceClassification {
        var onWater = false
        var onRoad = false
        for feature in features {
            guard let meta = feature.properties?.tilequery,
                  let layer = meta.layer?.lowercased() else { continue }
            let geometry = meta.geometry?.lowercased() ?? ""
            let distance = meta.distance

            if MapTravelSurfaceSampler.isWaterLayer(layer) {
                if geometry == "polygon", distance <= maxPolygonWaterDistanceMeters {
                    onWater = true
                } else if geometry == "linestring", distance <= maxWaterwayDistanceMeters {
                    onWater = true
                }
            }
            if MapTravelSurfaceSampler.isRoadLayer(layer), distance <= maxRoadDistanceMeters {
                onRoad = true
            }
        }
        return SurfaceClassification(onWater: onWater, onRoad: onRoad)
    }

    static func nearestRoadCoordinate(
        in features: [MapboxTilequeryFeature],
        maxDistanceMeters: Double = maxRoadDistanceMeters
    ) -> NearestRoadResult? {
        var best: NearestRoadResult?
        for feature in features {
            guard let meta = feature.properties?.tilequery,
                  let layer = meta.layer?.lowercased(),
                  MapTravelSurfaceSampler.isRoadLayer(layer),
                  meta.distance <= maxDistanceMeters,
                  let coordinate = feature.geometry?.pointCoordinate else { continue }
            if let currentBest = best {
                if meta.distance < currentBest.distanceMeters {
                    best = NearestRoadResult(coordinate: coordinate, distanceMeters: meta.distance)
                }
            } else {
                best = NearestRoadResult(coordinate: coordinate, distanceMeters: meta.distance)
            }
        }
        return best
    }

    static func decodeFeatures(from data: Data) -> [MapboxTilequeryFeature]? {
        guard let decoded = try? JSONDecoder().decode(Response.self, from: data) else { return nil }
        return decoded.features
    }

    static func fetchFeatures(at coordinate: CLLocationCoordinate2D) async -> [MapboxTilequeryFeature]? {
        guard let url = tilequeryURL(for: coordinate) else { return nil }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            return decodeFeatures(from: data)
        } catch {
            return nil
        }
    }

    static func tilequeryURL(for coordinate: CLLocationCoordinate2D) -> URL? {
        let token = MapboxOptions.accessToken
        guard !token.isEmpty else { return nil }

        var components = URLComponents(
            string: "https://api.mapbox.com/v4/\(tilesetID)/tilequery/\(coordinate.longitude),\(coordinate.latitude).json"
        )
        components?.queryItems = [
            URLQueryItem(name: "radius", value: String(queryRadiusMeters)),
            URLQueryItem(name: "layers", value: defaultLayers),
            URLQueryItem(name: "limit", value: String(defaultFeatureLimit)),
            URLQueryItem(name: "access_token", value: token),
        ]
        return components?.url
    }
}
