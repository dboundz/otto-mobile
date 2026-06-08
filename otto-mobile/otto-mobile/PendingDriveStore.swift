import CoreLocation
import Foundation

struct PendingDrivePathSample: Codable, Equatable {
    let lat: Double
    let lng: Double
    let speedMph: Double
    let capturedAt: Date?

    init(_ sample: DrivePathSample) {
        lat = sample.coordinate.latitude
        lng = sample.coordinate.longitude
        speedMph = sample.speedMph
        capturedAt = sample.capturedAt
    }

    var drivePathSample: DrivePathSample {
        DrivePathSample(
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
            speedMph: speedMph,
            capturedAt: capturedAt
        )
    }
}

struct PendingDriveArchive: Codable, Identifiable, Equatable {
    let id: UUID
    let createdAt: Date
    let expiresAt: Date
    var retryCount: Int
    let failurePhase: String
    let kind: String
    let title: String
    let startedAt: Date
    let endedAt: Date
    let distanceMeters: Double
    let maxSpeedMph: Double
    let avgSpeedMph: Double
    let backendDriveId: String?
    let circleId: String?
    let sharedCircleIds: [String]
    let routeId: String?
    let routeName: String?
    let pathSamples: [PendingDrivePathSample]

    var pathDriveSamples: [DrivePathSample] {
        pathSamples.map(\.drivePathSample)
    }

    var isExpired: Bool {
        Date() >= expiresAt
    }

    var daysUntilExpiry: Int {
        max(0, Int(ceil(expiresAt.timeIntervalSince(Date()) / 86_400)))
    }
}

struct PendingDriveArchiveInput {
    let failurePhase: String
    let kind: DriveSessionKind
    let title: String
    let startedAt: Date
    let endedAt: Date
    let distanceMeters: Double
    let maxSpeedMph: Double
    let avgSpeedMph: Double
    let backendDriveId: String?
    let circleId: String?
    let sharedCircleIds: [String]
    let routeId: String?
    let routeName: String?
    let pathSamples: [DrivePathSample]
}

enum PendingDriveStore {
    static let retentionInterval: TimeInterval = 3 * 24 * 3_600
    private static let fileName = "pending-drive-archives.json"

    private static var fileURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent(fileName, isDirectory: false)
    }

    static func load() -> [PendingDriveArchive] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let archives = try? decoder.decode([PendingDriveArchive].self, from: data) else { return [] }
        return archives.filter { !$0.isExpired }
    }

    static func save(_ archives: [PendingDriveArchive]) {
        let live = archives.filter { !$0.isExpired }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(live) else { return }
        let directory = fileURL.deletingLastPathComponent()
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? data.write(to: fileURL, options: [.atomic])
    }

    static func purgeExpired(from archives: [PendingDriveArchive]) -> [PendingDriveArchive] {
        archives.filter { !$0.isExpired }
    }

    static func makeArchive(from input: PendingDriveArchiveInput, retryCount: Int = 0) -> PendingDriveArchive? {
        guard input.distanceMeters > 0 || input.pathSamples.count >= 2 else { return nil }
        let now = Date()
        return PendingDriveArchive(
            id: UUID(),
            createdAt: now,
            expiresAt: now.addingTimeInterval(retentionInterval),
            retryCount: retryCount,
            failurePhase: input.failurePhase,
            kind: input.kind.rawValue,
            title: input.title,
            startedAt: input.startedAt,
            endedAt: input.endedAt,
            distanceMeters: input.distanceMeters,
            maxSpeedMph: input.maxSpeedMph,
            avgSpeedMph: input.avgSpeedMph,
            backendDriveId: input.backendDriveId,
            circleId: input.circleId,
            sharedCircleIds: input.sharedCircleIds,
            routeId: input.routeId,
            routeName: input.routeName,
            pathSamples: input.pathSamples.map(PendingDrivePathSample.init)
        )
    }

    static func clearAll() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
