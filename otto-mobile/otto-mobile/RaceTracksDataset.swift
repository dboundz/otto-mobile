import Foundation
import Combine
import CoreLocation
import os

/// US race / track locations for map overlays (schema matches server `/static/us_tracks_dataset.json`).
struct RaceTrackRecord: Identifiable, Codable, Equatable {
    var id: String { "\(name)|\(city)|\(state)" }
    let name: String
    let type: [String]
    let city: String
    let state: String
    let lat: Double
    let lng: Double
}

extension RaceTrackRecord {
    var coordinate: CLLocationCoordinate2D? {
        guard lat.isFinite, lng.isFinite else { return nil }
        guard (-90 ... 90).contains(lat), (-180 ... 180).contains(lng) else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return coordinate
    }

    var locationLine: String {
        "\(city), \(state)"
    }

    var formattedTypes: String {
        type
            .map { $0.replacingOccurrences(of: "_", with: " ") }
            .map { $0.capitalized }
            .joined(separator: " · ")
    }
}

enum RaceTracksDatasetConfig {
    /// Minimum time between network refreshes. Change to `3600` for hourly.
    nonisolated static let minimumRefreshInterval: TimeInterval = 24 * 60 * 60

    /// HTTPS JSON served from Otto backend static files.
    static var remoteURL: URL? {
        if let s = Bundle.main.object(forInfoDictionaryKey: "USRaceTracksRemoteURL") as? String,
           let url = URL(string: s), !s.isEmpty {
            return url
        }
        return URL(string: "https://api.ottomot.to/static/us_tracks_dataset.json")
    }

    private enum Keys {
        static let lastSuccessfulFetch = "otto.raceTracks.lastSuccessfulFetch"
    }

    static var lastSuccessfulFetchDate: Date? {
        get { UserDefaults.standard.object(forKey: Keys.lastSuccessfulFetch) as? Date }
        set { UserDefaults.standard.set(newValue, forKey: Keys.lastSuccessfulFetch) }
    }
}

/// Loads cached US track points and refreshes from `RaceTracksDatasetConfig.remoteURL`.
@MainActor
final class RaceTracksDatasetStore: ObservableObject {
    @Published private(set) var tracks: [RaceTrackRecord] = []

    private let cacheURL: URL
    private let decoder = JSONDecoder()
    private let refreshInterval: TimeInterval

    init(refreshInterval: TimeInterval = RaceTracksDatasetConfig.minimumRefreshInterval) {
        self.refreshInterval = refreshInterval
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("otto", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.cacheURL = dir.appendingPathComponent("us_tracks_dataset.cache.json")

        tracks = Self.loadCached(at: cacheURL) ?? []
    }

    /// Call after launch / foreground. Fetches when cache is empty or stale.
    func refreshIfStale() async {
        guard let remote = RaceTracksDatasetConfig.remoteURL else { return }
        if !tracks.isEmpty,
           let last = RaceTracksDatasetConfig.lastSuccessfulFetchDate,
           Date().timeIntervalSince(last) < refreshInterval {
            return
        }
        var request = URLRequest(url: remote)
        request.timeoutInterval = 25
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, !(200 ... 299).contains(http.statusCode) {
                OttoLog.app.debug("Race tracks remote fetch HTTP \(http.statusCode); keeping existing data")
                return
            }
            guard !data.isEmpty else {
                OttoLog.app.debug("Race tracks remote fetch returned empty body; keeping existing data")
                return
            }
            let decoded = try decoder.decode([RaceTrackRecord].self, from: data)
            tracks = decoded
            do {
                try Self.writeAtomically(data: data, to: cacheURL)
                RaceTracksDatasetConfig.lastSuccessfulFetchDate = Date()
            } catch {
                OttoLog.app.debug("Race tracks cache write failed (in-memory data still updated): \(String(describing: error))")
            }
            OttoLog.app.info("Race tracks dataset refreshed (\(decoded.count) tracks)")
        } catch {
            OttoLog.app.debug("Race tracks remote fetch failed: \(String(describing: error))")
        }
    }

    /// Forces a download when online (ignores freshness). Useful for pull-to-refresh later.
    func refreshNow() async {
        RaceTracksDatasetConfig.lastSuccessfulFetchDate = nil
        await refreshIfStale()
    }

    private static func loadCached(at url: URL) -> [RaceTrackRecord]? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        guard let data = try? Data(contentsOf: url), !data.isEmpty else {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        guard let tracks = try? JSONDecoder().decode([RaceTrackRecord].self, from: data) else {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        return tracks
    }

    private static func writeAtomically(data: Data, to url: URL) throws {
        let tmp = url.deletingLastPathComponent().appendingPathComponent(".\(UUID().uuidString).tmp")
        try data.write(to: tmp, options: [.atomic])
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
        try FileManager.default.moveItem(at: tmp, to: url)
    }
}
