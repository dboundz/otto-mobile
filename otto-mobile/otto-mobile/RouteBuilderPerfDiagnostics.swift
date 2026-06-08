import Combine
import CoreLocation
import Foundation
import MapboxMaps
import SwiftUI

@MainActor
final class RouteBuilderPerfDiagnostics: ObservableObject {
    static weak var active: RouteBuilderPerfDiagnostics?

    @Published private(set) var cameraEventsPerSec = 0
    @Published private(set) var bodyRebuildsPerSec = 0
    @Published private(set) var viewportUpdatesPerSec = 0
    @Published private(set) var stateWritesPerSec = 0
    @Published private(set) var mapLineBuildsPerSec = 0

    @Published var roadPointCount = 0
    @Published var displayPointCount = 0
    @Published var markerCount = 0
    @Published var visibleMarkerCount = 0
    @Published var lodTierLabel = "—"
    @Published var latitudeDeltaLabel = "—"
    @Published var lightweightRendering = false
    @Published var isGesturing = false
    @Published var lastDisplayRefreshMs = 0.0
    @Published var lastStateWriteReason = "—"
    @Published var bottleneckHint = "Collecting…"

    private var cameraTimestamps: [Date] = []
    private var bodyRebuildTimestamps: [Date] = []
    private var viewportTimestamps: [Date] = []
    private var stateWriteTimestamps: [Date] = []
    private var mapLineBuildTimestamps: [Date] = []
    private var refreshTimer: Timer?
    private var lastPublishedAt = Date.distantPast
    private var pendingSnapshot: PendingSnapshot?

    private struct PendingSnapshot {
        var roadPoints: Int
        var displayPoints: Int
        var markers: Int
        var visibleMarkers: Int
        var lodTierLabel: String
        var latitudeDeltaLabel: String
        var lightweightRendering: Bool
    }

    func activate() {
        Self.active = self
        refreshTimer?.invalidate()
        refreshTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.recomputeRates()
                self?.publishPendingSnapshotIfDue(force: false)
            }
        }
    }

    func deactivate() {
        if Self.active === self {
            Self.active = nil
        }
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    func recordCameraEvent() {
        stamp(&cameraTimestamps)
    }

    func recordBodyRebuild() {
        stamp(&bodyRebuildTimestamps)
    }

    func recordViewportUpdate() {
        stamp(&viewportTimestamps)
    }

    func recordStateWrite(_ reason: String) {
        lastStateWriteReason = reason
        stamp(&stateWriteTimestamps)
    }

    func recordMapLineBuild(pointCount: Int) {
        displayPointCount = pointCount
        stamp(&mapLineBuildTimestamps)
    }

    func recordDisplayRefresh(durationMs: Double, outputCount: Int) {
        lastDisplayRefreshMs = durationMs
        displayPointCount = outputCount
    }

    func setGesturing(_ active: Bool) {
        isGesturing = active
        adjustRefreshTimer(forGesturing: active)
        if !active {
            publishPendingSnapshotIfDue(force: true)
        }
    }

    func updateSnapshot(
        roadPoints: Int,
        displayPoints: Int,
        markers: Int,
        visibleMarkers: Int,
        lodTier: RouteBuilderMapMarkerLODTier,
        latitudeDelta: Double,
        lightweightRendering: Bool
    ) {
        pendingSnapshot = PendingSnapshot(
            roadPoints: roadPoints,
            displayPoints: displayPoints,
            markers: markers,
            visibleMarkers: visibleMarkers,
            lodTierLabel: String(describing: lodTier),
            latitudeDeltaLabel: String(format: "%.5f° (~%.1f mi)", latitudeDelta, latitudeDelta * 69),
            lightweightRendering: lightweightRendering
        )
        publishPendingSnapshotIfDue(force: !isGesturing)
    }

    private func adjustRefreshTimer(forGesturing gesturing: Bool) {
        refreshTimer?.invalidate()
        let interval = gesturing ? 0.25 : 1.0
        refreshTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.recomputeRates()
                self?.publishPendingSnapshotIfDue(force: false)
            }
        }
    }

    private func publishPendingSnapshotIfDue(force: Bool) {
        guard let pendingSnapshot else { return }
        let minInterval = isGesturing ? 0.25 : 1.0
        guard force || Date().timeIntervalSince(lastPublishedAt) >= minInterval else { return }

        roadPointCount = pendingSnapshot.roadPoints
        displayPointCount = pendingSnapshot.displayPoints
        markerCount = pendingSnapshot.markers
        visibleMarkerCount = pendingSnapshot.visibleMarkers
        lodTierLabel = pendingSnapshot.lodTierLabel
        latitudeDeltaLabel = pendingSnapshot.latitudeDeltaLabel
        lightweightRendering = pendingSnapshot.lightweightRendering
        lastPublishedAt = Date()
    }

    private func stamp(_ bucket: inout [Date]) {
        let now = Date()
        bucket.append(now)
        trim(bucket: &bucket, to: now)
    }

    private func recomputeRates() {
        let now = Date()
        trim(bucket: &cameraTimestamps, to: now)
        trim(bucket: &bodyRebuildTimestamps, to: now)
        trim(bucket: &viewportTimestamps, to: now)
        trim(bucket: &stateWriteTimestamps, to: now)
        trim(bucket: &mapLineBuildTimestamps, to: now)

        cameraEventsPerSec = cameraTimestamps.count
        bodyRebuildsPerSec = bodyRebuildTimestamps.count
        viewportUpdatesPerSec = viewportTimestamps.count
        stateWritesPerSec = stateWriteTimestamps.count
        mapLineBuildsPerSec = mapLineBuildTimestamps.count
        bottleneckHint = inferBottleneck()
    }

    private func trim(bucket: inout [Date], to now: Date) {
        let cutoff = now.addingTimeInterval(-1)
        if let firstIndex = bucket.firstIndex(where: { $0 >= cutoff }) {
            bucket.removeFirst(firstIndex)
        } else {
            bucket.removeAll()
        }
    }

    private func inferBottleneck() -> String {
        if bodyRebuildsPerSec >= 20 && viewportUpdatesPerSec >= 20 {
            return "Likely: `$viewport` binding updates every camera frame → full SwiftUI rebuild"
        }
        if viewportUpdatesPerSec >= 20 && bodyRebuildsPerSec <= 5 {
            return "Viewport binding active but body stable — Mapbox GPU / basemap likely bottleneck"
        }
        if bodyRebuildsPerSec >= 20 && cameraEventsPerSec >= 20 && viewportUpdatesPerSec < 5 {
            return "Likely: SwiftUI body rebuilds track camera events (check @State writes)"
        }
        if mapLineBuildsPerSec >= 10 {
            return "Likely: Imperative map line update (\(displayPointCount) pts) \(mapLineBuildsPerSec)/s"
        }
        if displayPointCount > 1_500 && isGesturing {
            return "Likely: Large display polyline (\(displayPointCount) pts) while gesturing"
        }
        if displayPointCount > 1_500 && !isGesturing {
            return "Note: Full geometry on map (\(displayPointCount) pts) — editor shell should stay isolated via Equatable map host"
        }
        if roadPointCount > 8_000 {
            return "Note: Full geometry \(roadPointCount) pts — edit/snap may cost even if map is OK"
        }
        if markerCount > 40 {
            return "Likely: Many map annotations (\(markerCount))"
        }
        if cameraEventsPerSec >= 5 && bodyRebuildsPerSec <= 5 && mapLineBuildsPerSec <= 3 && viewportUpdatesPerSec <= 3 {
            if lightweightRendering {
                return "Likely: Mapbox GPU (lightweight mode active — line + \(visibleMarkerCount) markers)"
            }
            return "Likely: Mapbox GPU render (SwiftUI stable — try lightweight regional mode)"
        }
        if stateWritesPerSec >= 8 {
            return "Likely: Frequent @State writes — \(lastStateWriteReason)"
        }
        if bodyRebuildsPerSec <= 3 && cameraEventsPerSec <= 3 && !isGesturing {
            return "Idle — pan/zoom to capture rates"
        }
        return "Mixed load — compare camera vs body vs map-line rates while panning"
    }
}

struct RouteBuilderPerfOverlay: View {
    @ObservedObject var diagnostics: RouteBuilderPerfDiagnostics

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("Route Builder perf")
                .font(.caption2.weight(.bold))
                .foregroundStyle(.yellow)

            rateRow("camera/s", value: diagnostics.cameraEventsPerSec, warn: 25)
            rateRow("body/s", value: diagnostics.bodyRebuildsPerSec, warn: 15)
            rateRow("viewport/s", value: diagnostics.viewportUpdatesPerSec, warn: 15)
            rateRow("state/s", value: diagnostics.stateWritesPerSec, warn: 8)
            rateRow("mapLine/s", value: diagnostics.mapLineBuildsPerSec, warn: 8)

            section("Geometry")
            infoRow("road pts", value: "\(diagnostics.roadPointCount)")
            infoRow("display pts", value: "\(diagnostics.displayPointCount)")
            infoRow("markers", value: "\(diagnostics.visibleMarkerCount)/\(diagnostics.markerCount)")
            infoRow("LOD", value: diagnostics.lodTierLabel)
            infoRow("span", value: diagnostics.latitudeDeltaLabel)
            infoRow("lightweight", value: diagnostics.lightweightRendering ? "yes" : "no")
            infoRow("downsample", value: String(format: "%.2f ms", diagnostics.lastDisplayRefreshMs))
            infoRow("gesture", value: diagnostics.isGesturing ? "yes" : "no")
            infoRow("last @State", value: diagnostics.lastStateWriteReason)

            Text(diagnostics.bottleneckHint)
                .font(.system(size: 9, weight: .semibold, design: .monospaced))
                .foregroundStyle(hintColor)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
        }
        .font(.system(size: 10, design: .monospaced))
        .foregroundStyle(.white)
        .padding(10)
        .background(Color.black.opacity(0.82), in: RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.yellow.opacity(0.45), lineWidth: 1)
        )
        .frame(maxWidth: 340, alignment: .leading)
    }

    private var hintColor: Color {
        diagnostics.bodyRebuildsPerSec >= 15
            || diagnostics.mapLineBuildsPerSec >= 8
            || diagnostics.viewportUpdatesPerSec >= 15
            ? .orange
            : .white.opacity(0.85)
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 9, weight: .semibold, design: .monospaced))
            .foregroundStyle(.white.opacity(0.55))
            .padding(.top, 4)
    }

    private func rateRow(_ label: String, value: Int, warn: Int) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .foregroundStyle(.white.opacity(0.55))
                .frame(width: 72, alignment: .leading)
            Text("\(value)")
                .foregroundStyle(value >= warn ? Color.orange : .white)
            if value >= warn {
                Text("⚠")
                    .foregroundStyle(.orange)
            }
        }
    }

    private func infoRow(_ label: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text(label)
                .foregroundStyle(.white.opacity(0.55))
                .frame(width: 72, alignment: .leading)
            Text(value)
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct RouteBuilderBodyRebuildProbe: View {
    @ObservedObject var diagnostics: RouteBuilderPerfDiagnostics

    var body: some View {
        let _ = diagnostics.recordBodyRebuild()
        return Color.clear.frame(width: 0, height: 0)
    }
}
