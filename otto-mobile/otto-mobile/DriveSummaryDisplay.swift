import SwiftUI

enum DriveAverageSpeed {
    private static let metersPerSecondToMph = 2.23694

    static func resolvedMph(storedAvg: Double, distanceMeters: Double, durationSeconds: TimeInterval) -> Double {
        if storedAvg > 0 { return storedAvg }
        guard durationSeconds > 0, distanceMeters > 0 else { return 0 }
        return (distanceMeters / durationSeconds) * metersPerSecondToMph
    }

    static func resolvedMph(for drive: DriveDTO) -> Double {
        let duration = drive.endTime.map { max(0, $0.timeIntervalSince(drive.startTime)) } ?? 0
        return resolvedMph(storedAvg: drive.avgSpeedMph, distanceMeters: drive.distanceMeters, durationSeconds: duration)
    }
}

/// Formatted drive fields shared by Drive Summary and Drive Trail map chrome.
struct DriveSummaryDisplayMetrics {
    let listTitle: String
    let timestampText: String
    let distanceText: String
    let durationText: String
    let averageSpeedText: String
    let samplesText: String

    init(drive: DriveDTO) {
        listTitle = DriveDisplayNaming.listTitle(
            routeName: drive.route?.name,
            driveTitle: drive.title
        )
        timestampText = Self.formatTimestamp(drive.endTime ?? drive.startTime)
        distanceText = Self.formatDistance(drive.distanceMeters, drive: drive)
        durationText = Self.formatDuration(startTime: drive.startTime, endTime: drive.endTime)
        averageSpeedText = Self.formatAverageSpeed(Self.credibleAverageSpeedMph(for: drive))
        samplesText = "\(drive.pointsCount)"
    }

    /// When GPS samples are sparse, prefer distance ÷ duration over a single-sample speed reading.
    static func credibleAverageSpeedMph(for drive: DriveDTO) -> Double {
        let duration = drive.endTime.map { max(0, $0.timeIntervalSince(drive.startTime)) } ?? 0
        let implied = DriveAverageSpeed.resolvedMph(
            storedAvg: 0,
            distanceMeters: drive.distanceMeters,
            durationSeconds: duration
        )
        guard drive.pointsCount < 2 else {
            return DriveAverageSpeed.resolvedMph(for: drive)
        }
        if duration > 0, drive.distanceMeters > 0, implied > 0 {
            let stored = drive.avgSpeedMph
            if stored > 0, stored > implied * 3 {
                return implied
            }
            return implied > 0 ? implied : stored
        }
        return drive.avgSpeedMph
    }

    private static func formatTimestamp(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy • h:mm a"
        return formatter.string(from: date)
    }

    private static func formatDistance(_ distanceMeters: Double, drive: DriveDTO) -> String {
        let formatted = formatDistanceMiles(distanceMeters)
        guard drive.pointsCount < 2, drive.route != nil else { return formatted }
        if let route = drive.route, route.distanceMeters > 0 {
            let total = max(route.totalCheckpoints ?? route.points.count, 1)
            let completed = route.completedCheckpoints ?? route.completedWaypointIndexes?.count ?? 0
            let progress = Double(completed) / Double(total)
            if progress < 0.85, distanceMeters >= route.distanceMeters * 0.85 {
                let estimated = route.distanceMeters * progress
                return "\(formatDistanceMiles(estimated)) (est.)"
            }
        }
        return "\(formatted) (est.)"
    }

    private static func formatDistanceMiles(_ distanceMeters: Double) -> String {
        let miles = distanceMeters / 1609.344
        if miles < 10 { return String(format: "%.1f mi", miles) }
        return "\(Int(miles.rounded())) mi"
    }

    private static func formatDuration(startTime: Date, endTime: Date?) -> String {
        guard let endTime else { return "In progress" }
        let seconds = max(0, endTime.timeIntervalSince(startTime))
        let totalMinutes = max(1, Int((seconds / 60).rounded()))
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    private static func formatAverageSpeed(_ avgSpeedMph: Double) -> String {
        guard avgSpeedMph > 0 else { return "--" }
        return "\(Int(avgSpeedMph.rounded())) mph"
    }
}

/// Compact stat chip matching Drive Summary `driveStatTile` styling.
struct DriveSummaryCompactStatTile: View {
    let title: String
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Image(systemName: icon)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.purple)
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(title)
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(.white.opacity(0.55))
                .lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}
