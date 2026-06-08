import CoreLocation
import SwiftUI

/// Live location-pipeline readout for Map debugging (enable in Settings → Debug).
struct MapLocationDiagnosticsOverlay: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let snapshot = locationService.makeDiagnosticsSnapshot()
            let pinCoord = locationService.displayLocation?.coordinate

            VStack(alignment: .leading, spacing: 3) {
                Text("Location debug")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.yellow)

                diagRow("time", value: timeline.date.formatted(date: .omitted, time: .standard))
                diagRow("auth", value: authLabel(snapshot.authorizationStatus))
                diagRow("motion", value: motionLabel(snapshot.motionAuthorizationStatus))

                diagSection("Session flags")
                diagRow("mapActive", value: flag(appState.isMapScreenActive))
                diagRow("eventsActive", value: flag(appState.isEventsScreenActive))
                diagRow("routeSession", value: flag(appState.isMapRouteSessionActive))
                diagRow("sharing", value: flag(appState.isSharingEnabled))
                diagRow("livePresence", value: flag(appState.isPublishingLiveSharingPresence))

                diagSection("Hardware / needs")
                diagRow("gpsRunning", value: flag(snapshot.gpsRunning))
                diagRow("motionRunning", value: flag(snapshot.motionRunning))
                diagRow("liveDisplay", value: flag(snapshot.liveDisplayEnabled))
                diagRow(
                    "needs",
                    value: "gps:\(flag(snapshot.appliedNeeds.gps)) mot:\(flag(snapshot.appliedNeeds.motion)) ui:\(flag(snapshot.appliedNeeds.freshDisplay))"
                )
                diagRow("distFilter", value: String(format: "%.0fm", snapshot.distanceFilter))
                diagRow("fixes", value: "\(snapshot.locationUpdateCount)")
                diagRow("displayTick", value: "\(snapshot.mapLocationDisplayTick)")
                if let meters = snapshot.metersSinceLastPublish {
                    diagRow("Δ publish", value: String(format: "%.1f m", meters))
                }
                if let age = snapshot.latestSample.map({ Date().timeIntervalSince($0.timestamp) }) {
                    diagRow("fixAge", value: String(format: "%.1fs", age))
                    if age > 5 {
                        Text("⚠ GPS fix stale (>5s)")
                            .foregroundStyle(.orange)
                    }
                }

                diagSection("Movement")
                diagRow("mode", value: "\(snapshot.movementMode)")
                diagRow(
                    "speed",
                    value: String(
                        format: "latest %.1f mph · display %.1f mph",
                        snapshot.latestSpeedMetersPerSecond * 2.23694,
                        snapshot.displaySpeedMetersPerSecond * 2.23694
                    )
                )

                diagSection("Coordinates")
                diagRow("latest", value: formatLocation(snapshot.latestSample))
                diagRow("display", value: formatLocation(snapshot.displayLocation))
                diagRow("lastPub", value: formatLocation(snapshot.lastLocation))
                if let pinCoord {
                    diagRow(
                        "pin",
                        value: String(
                            format: "%.6f, %.6f",
                            pinCoord.latitude,
                            pinCoord.longitude
                        )
                    )
                }

                if !appState.isSharingEnabled {
                    Text("Motion chip needs sharing enabled")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(.white.opacity(0.5))
                        .padding(.top, 4)
                }
            }
            .font(.system(size: 10, design: .monospaced))
            .foregroundStyle(.white)
            .padding(10)
            .background(Color.black.opacity(0.82), in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.yellow.opacity(0.45), lineWidth: 1)
            )
            .frame(maxWidth: 320, alignment: .leading)
        }
    }

    private func diagSection(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 9, weight: .semibold, design: .monospaced))
            .foregroundStyle(.white.opacity(0.55))
            .padding(.top, 4)
    }

    private func diagRow(_ label: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text(label)
                .foregroundStyle(.white.opacity(0.55))
                .frame(width: 72, alignment: .leading)
            Text(value)
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func flag(_ on: Bool) -> String { on ? "yes" : "no" }

    private func authLabel(_ status: CLAuthorizationStatus) -> String {
        switch status {
        case .notDetermined: return "notDetermined"
        case .restricted: return "restricted"
        case .denied: return "denied"
        case .authorizedAlways: return "always"
        case .authorizedWhenInUse: return "whenInUse"
        @unknown default: return "unknown"
        }
    }

    private func motionLabel(_ status: MotionPermissionState) -> String {
        switch status {
        case .notDetermined: return "notDetermined"
        case .authorized: return "authorized"
        case .denied: return "denied"
        case .restricted: return "restricted"
        }
    }

    private func formatLocation(_ location: CLLocation?) -> String {
        guard let location else { return "—" }
        let age = Date().timeIntervalSince(location.timestamp)
        let c = location.coordinate
        return String(
            format: "%.6f, %.6f · %.0fs · ±%.0fm",
            c.latitude,
            c.longitude,
            age,
            location.horizontalAccuracy
        )
    }
}
