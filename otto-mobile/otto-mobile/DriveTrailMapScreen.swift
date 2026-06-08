import CoreLocation
import MapboxMaps
import MapKit
import SwiftUI

/// Full-screen interactive map for a completed drive: speed-gradient GPS trail and start/finish markers.
struct DriveTrailMapScreen: View {
    let drive: DriveDTO
    let circleId: String?
    let onClose: () -> Void

    @State private var pathSamples: [DrivePathSample] = []
    @State private var isLoading = true
    @State private var viewport: Viewport
    @State private var markerLODLatitudeDelta: Double = 0.035
    @State private var latestMarkerLODRegion: MKCoordinateRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431),
        span: MKCoordinateSpan(latitudeDelta: 0.035, longitudeDelta: 0.035)
    )
    @State private var markerLODDebounceTask: Task<Void, Never>?

    private let metrics: DriveSummaryDisplayMetrics

    private var routeMapPoints: [RouteMapPointModel] {
        if let route = drive.route {
            let points = route.toMapPoints()
            if !points.isEmpty { return points }
        }
        return RouteMapGeometry.trailEndpointMapPoints(
            from: pathSamples,
            idPrefix: "drive-trail-\(drive.id)"
        )
    }

    private var completedWaypointIndexes: Set<Int> {
        Set(drive.route?.completedWaypointIndexes ?? [])
    }

    private var visibleRouteMapPoints: [RouteMapPointModel] {
        if RouteMapMarkerLOD.usesWideRegionalLightweightRendering(latitudeDelta: markerLODLatitudeDelta) {
            return routeMapPoints.filter { $0.markerType == "start" || $0.markerType == "finish" }
        }
        return routeMapPoints
    }

    private var hasDrawableTrail: Bool {
        DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)
    }

    private var routeSnapshotLineCoordinates: [CLLocationCoordinate2D] {
        drive.route?.toMapLineCoordinates() ?? []
    }

    private var hasRouteSnapshotLine: Bool {
        routeSnapshotLineCoordinates.count >= 2
    }

    private var trailStartCoordinate: CLLocationCoordinate2D? {
        let trailCoordinates = DriveSpeedGradient.pathCoordinates(from: pathSamples)
        if let first = trailCoordinates.first { return first }
        return routeMapPoints.first(where: { $0.markerType == "start" })?.coordinate
    }

    init(
        drive: DriveDTO,
        circleId: String?,
        onClose: @escaping () -> Void
    ) {
        self.drive = drive
        self.circleId = circleId
        self.onClose = onClose
        self.metrics = DriveSummaryDisplayMetrics(drive: drive)
        _viewport = State(initialValue: Self.initialViewport(for: drive))
    }

    var body: some View {
        ZStack {
            mapLayer

            VStack {
                topChrome
                Spacer()
                    .allowsHitTesting(false)
                HStack(alignment: .bottom) {
                    trailTargetButton
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 10)
                bottomChrome
            }

            if isLoading {
                loadingOverlay
            } else if !hasDrawableTrail && !hasRouteSnapshotLine {
                emptyTrailOverlay
            }
        }
        .background(Color.black.ignoresSafeArea())
        .task(id: drive.id) {
            await loadPathSamples()
        }
    }

    // MARK: - Map

    private var mapLayer: some View {
        OttoMapboxMapView(
            viewport: $viewport,
            allowsInteraction: true,
            onCameraChanged: { region in
                scheduleMarkerLODUpdate(from: region)
            },
            onUserGesture: {},
            onMapLoaded: {}
        ) {
            if hasDrawableTrail {
                RouteSpeedGradientMapContent(
                    sourceID: "drive-trail-\(drive.id)",
                    samples: pathSamples
                )
            } else if hasRouteSnapshotLine {
                RouteMapLineMapContent(
                    sourceID: "drive-trail-route-\(drive.id)",
                    coordinates: routeSnapshotLineCoordinates,
                    palette: .livePurple
                )
            }

            ForEvery(visibleRouteMapPoints) { point in
                MapViewAnnotation(coordinate: point.coordinate) {
                    routeMapPointMarker(point)
                        .id(
                            RouteMapMarkerLOD.annotationRefreshID(
                                pointID: point.id,
                                markerType: point.markerType,
                                latitudeDelta: markerLODLatitudeDelta
                            )
                        )
                }
                .allowOverlap(true)
                .priority(RouteMapGeometry.mapMarkerOverlapPriority(
                    for: point.coordinate,
                    markerType: point.markerType,
                    tieBreaker: point.index
                ))
            }
        }
        .ignoresSafeArea()
    }

    private func routeMapPointMarker(_ point: RouteMapPointModel) -> some View {
        RouteMapMarkerLODView(
            markerType: point.markerType,
            isCompleted: point.isCompleted(in: completedWaypointIndexes),
            latitudeDelta: markerLODLatitudeDelta
        )
    }

    private func scheduleMarkerLODUpdate(from region: MKCoordinateRegion) {
        latestMarkerLODRegion = region
        markerLODDebounceTask?.cancel()
        markerLODDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            applyMarkerLODSettle(from: latestMarkerLODRegion)
        }
    }

    private func applyMarkerLODSettle(from region: MKCoordinateRegion) {
        let settledDelta = OttoMapboxCamera.visibleLatitudeDeltaDegrees(for: region)
        guard abs(markerLODLatitudeDelta - settledDelta) > 0.00005 else { return }
        markerLODLatitudeDelta = settledDelta
    }

    // MARK: - Chrome

    private let chromeEdgeInset: CGFloat = 12

    private var topChrome: some View {
        ZStack {
            titleDateLabels
                .frame(maxWidth: .infinity)

            HStack {
                closeButton
                Spacer(minLength: 0)
                Color.clear
                    .frame(width: 36, height: 36)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, chromeEdgeInset)
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background {
            topBarBackground
                .ignoresSafeArea(edges: [.horizontal, .top])
        }
    }

    private var closeButton: some View {
        Button {
            onClose()
        } label: {
            Image(systemName: "xmark")
                .font(.body.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Color.white.opacity(0.12))
                .clipShape(Circle())
        }
        .accessibilityLabel("Close")
    }

    private var titleDateLabels: some View {
        VStack(alignment: .center, spacing: 4) {
            Text(metrics.listTitle)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
            Text(metrics.timestampText)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.white.opacity(0.58))
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .padding(.horizontal, 52)
    }

    private var topBarBackground: some View {
        Rectangle()
            .fill(Color.black.opacity(0.78))
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(height: 1)
            }
    }

    private var trailTargetButton: some View {
        Button {
            recenterOnTrailStart()
        } label: {
            Image(systemName: "scope")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0.86), Color.black.opacity(0.86)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(trailStartCoordinate == nil)
        .opacity(trailStartCoordinate == nil ? 0.45 : 1)
        .accessibilityLabel("Recenter on full route")
    }

    private var bottomChrome: some View {
        VStack(spacing: 10) {
            DriveSpeedGradientLegend()
            trailStatsRow
        }
        .padding(10)
        .background(chromeCardBackground)
        .padding(.horizontal, chromeEdgeInset)
        .padding(.bottom, chromeEdgeInset)
    }

    private var chromeCardBackground: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color.black.opacity(0.78))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            }
    }

    private var trailStatsRow: some View {
        HStack(spacing: 6) {
            DriveSummaryCompactStatTile(title: "Distance", value: metrics.distanceText, icon: "road.lanes")
            DriveSummaryCompactStatTile(title: "Drive Time", value: metrics.durationText, icon: "timer")
            DriveSummaryCompactStatTile(title: "Average Pace", value: metrics.averageSpeedText, icon: "speedometer")
            DriveSummaryCompactStatTile(title: "Samples", value: metrics.samplesText, icon: "point.3.connected.trianglepath.dotted")
        }
    }

    private var loadingOverlay: some View {
        VStack {
            Spacer()
            ProgressView()
                .controlSize(.large)
                .tint(.white)
            Spacer()
        }
        .allowsHitTesting(false)
    }

    private var emptyTrailOverlay: some View {
        VStack(spacing: 8) {
            Spacer()
            Text("Trail not available")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.85))
            Text("Not enough GPS samples to draw this drive.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.5))
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(.horizontal, 32)
        .allowsHitTesting(false)
    }

    // MARK: - Data

    private func loadPathSamples() async {
        await MainActor.run { isLoading = true }

        do {
            let points = try await APIClient.shared.fetchDrivePoints(
                driveId: drive.id,
                circleId: circleId
            )
            let samples = points.compactMap(DrivePathSample.from)
            await MainActor.run {
                pathSamples = samples
                if DriveSpeedGradient.hasUsableSpeedPathData(samples) {
                    applyInitialViewport(samples: samples)
                } else {
                    applyViewportForRouteSnapshot()
                }
                isLoading = false
            }
        } catch {
            await MainActor.run {
                pathSamples = []
                applyViewportForRouteSnapshot()
                isLoading = false
            }
        }
    }

    private static let defaultFallbackCenter = CLLocationCoordinate2D(
        latitude: 30.2672,
        longitude: -97.7431
    )

    private static func initialViewport(for drive: DriveDTO) -> Viewport {
        viewport(for: drive, extraCoordinates: [])
    }

    @MainActor
    private func applyViewportForRouteSnapshot() {
        let region = Self.viewportRegion(for: drive, extraCoordinates: [])
        applyMarkerLODSettle(from: region)
        viewport = OttoMapboxCamera.viewport(for: region)
    }

    private static func viewport(
        for drive: DriveDTO,
        extraCoordinates: [CLLocationCoordinate2D]
    ) -> Viewport {
        OttoMapboxCamera.viewport(for: viewportRegion(for: drive, extraCoordinates: extraCoordinates))
    }

    private static func viewportRegion(
        for drive: DriveDTO,
        extraCoordinates: [CLLocationCoordinate2D]
    ) -> MKCoordinateRegion {
        let routeCoordinates = drive.route?.toMapLineCoordinates() ?? []
        let markerCoordinates = (drive.route?.toMapPoints() ?? []).map(\.coordinate)
        let boundsCoordinates = routeCoordinates + markerCoordinates + extraCoordinates
        let fallback = RouteMapGeometry.centroid(
            of: boundsCoordinates,
            fallback: defaultFallbackCenter
        )

        guard boundsCoordinates.count >= 2 else {
            return MKCoordinateRegion(
                center: defaultFallbackCenter,
                span: MKCoordinateSpan(latitudeDelta: 0.035, longitudeDelta: 0.035)
            )
        }

        return RouteMapGeometry.regionToFit(
            boundsCoordinates,
            fallback: fallback,
            paddingFactor: 2.4,
            minimumDelta: 0.018
        )
    }

    @MainActor
    private func applyInitialViewport(samples: [DrivePathSample]) {
        let trailCoordinates = DriveSpeedGradient.pathCoordinates(from: samples)
        let markerCoordinates = routeMapPoints.map(\.coordinate)
        let boundsCoordinates = trailCoordinates + markerCoordinates

        guard boundsCoordinates.count >= 2 else {
            applyViewportForRouteSnapshot()
            return
        }

        let fallback = RouteMapGeometry.centroid(
            of: boundsCoordinates,
            fallback: Self.defaultFallbackCenter
        )
        let region = RouteMapGeometry.regionToFit(
            boundsCoordinates,
            fallback: fallback,
            paddingFactor: 2.4,
            minimumDelta: 0.018
        )
        applyMarkerLODSettle(from: region)
        viewport = OttoMapboxCamera.viewport(for: region)
    }

    @MainActor
    private func recenterOnTrailStart() {
        if DriveSpeedGradient.hasUsableSpeedPathData(pathSamples) {
            applyInitialViewport(samples: pathSamples)
        } else {
            applyViewportForRouteSnapshot()
        }
    }
}
