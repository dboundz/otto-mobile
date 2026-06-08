import CoreLocation
import SwiftUI

// MARK: - Drive flavor badge

private struct DriveSummaryFlavor {
    let label: String
    let systemImage: String

    static func flavor(for date: Date) -> DriveSummaryFlavor {
        let hour = Calendar.current.component(.hour, from: date)
        switch hour {
        case 22...23, 0...4:
            return DriveSummaryFlavor(label: "Late Night Run", systemImage: "moon.fill")
        case 5...11:
            return DriveSummaryFlavor(label: "Morning Run", systemImage: "sunrise.fill")
        case 12...16:
            return DriveSummaryFlavor(label: "Afternoon Run", systemImage: "sun.max.fill")
        default:
            return DriveSummaryFlavor(label: "Evening Run", systemImage: "sunset.fill")
        }
    }
}

// MARK: - Profile-aligned chrome

private struct ProfileSectionCardChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.black.opacity(0.55))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private extension View {
    func profileSectionCardChrome() -> some View {
        modifier(ProfileSectionCardChrome())
    }

    func profileListItemChrome() -> some View {
        background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            }
    }
}

/// Matches My Drives / My Routes section headers on Profile.
private struct ProfileSectionHeader: View {
    let title: String
    var trailingText: String?

    var body: some View {
        HStack {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)
            Spacer()
            if let trailingText {
                Text(trailingText)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.purple)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.purple.opacity(0.18)))
            }
        }
    }
}

/// Matches the steering wheel badge on Profile drive list rows (slightly larger for summary).
private struct ProfileDriveListFlagBadge: View {
    var size: CGFloat = 56

    var body: some View {
        Image(systemName: "steeringwheel")
            .font(.system(size: size * 0.46, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(
                Circle().fill(
                    LinearGradient(
                        colors: [Color.purple, Color.blue.opacity(0.85)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            )
            .shadow(color: .purple.opacity(0.35), radius: 10, y: 3)
    }
}

// MARK: - Profile-style hero chrome

private struct DriveSummaryHeroGlassCircleChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background {
                Circle()
                    .fill(Color.white.opacity(0.065))
                    .overlay {
                        Circle().stroke(
                            LinearGradient(
                                colors: [Color.purple.opacity(0.42), Color.white.opacity(0.12)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                    }
            }
            .clipShape(Circle())
    }
}

// MARK: - Main screen

struct DriveSummaryScreen: View {
    let isOwner: Bool
    let garageCars: [GarageCar]
    let onDriveUpdated: (DriveDTO) -> Void
    var onDriveDeleted: (() -> Void)? = nil
    var lockedShareCircleID: String? = nil

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState
    @State private var currentDrive: DriveDTO
    @State private var isUpdatingGarageCar = false
    @State private var driveChatShareContext: DriveChatShareContext?
    @State private var isShowingRenameAlert = false
    @State private var isShowingDeleteConfirmation = false
    @State private var driveNameDraft = ""
    @State private var isRenamingDrive = false
    @State private var isDeletingDrive = false
    @State private var drivenPathSamples: [DrivePathSample] = []
    @State private var isShowingTrailMap = false

    init(
        drive: DriveDTO,
        isOwner: Bool,
        garageCars: [GarageCar],
        lockedShareCircleID: String? = nil,
        onDriveUpdated: @escaping (DriveDTO) -> Void,
        onDriveDeleted: (() -> Void)? = nil
    ) {
        self.isOwner = isOwner
        self.garageCars = garageCars
        self.lockedShareCircleID = lockedShareCircleID
        self.onDriveUpdated = onDriveUpdated
        self.onDriveDeleted = onDriveDeleted
        _currentDrive = State(initialValue: drive)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.black, Color(red: 0.05, green: 0.06, blue: 0.10), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    summaryHeroCard
                    metricsSection
                    routeSection
                    vehicleSection
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 20)
            }
            .scrollContentBackground(.hidden)
        }
        .background(Color.black.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            bottomActionBar
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 12)
                .background(Color.black.opacity(0.92))
        }
        .sheet(item: $driveChatShareContext) { context in
            DriveShareSquadActionsSheet(
                context: context,
                externalShareText: shareText,
                externalShareSubject: driveTitle,
                canShare: isOwner
            )
            .environmentObject(appState)
        }
        .alert("Rename drive", isPresented: $isShowingRenameAlert) {
            TextField("Drive name", text: $driveNameDraft)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                Task { await renameDrive() }
            }
            .disabled(driveNameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isRenamingDrive)
        } message: {
            Text("This updates how the drive appears on your profile and in squad chat shares.")
        }
        .task(id: currentDrive.id) {
            await refreshDriveFromServer()
            await loadDrivenPathSamples()
        }
        .fullScreenCover(isPresented: $isShowingTrailMap) {
            DriveTrailMapScreen(
                drive: currentDrive,
                circleId: lockedShareCircleID,
                onClose: { isShowingTrailMap = false }
            )
        }
        .confirmationDialog(
            "Delete this drive?",
            isPresented: $isShowingDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button("Delete Drive", role: .destructive) {
                Task { await deleteDrive() }
            }
            .disabled(isDeletingDrive)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes the drive from your profile. Shared copies in squad chat will show as deleted.")
        }
    }

    private func presentDriveChatShare() {
        guard isOwner else { return }
        guard currentDrive.status == "completed" else {
            appState.activeToast = AppToast(
                text: "Only completed drives can be shared",
                systemImage: "exclamationmark.triangle.fill"
            )
            return
        }
        let end = currentDrive.endTime ?? currentDrive.startTime
        let seconds = max(0, end.timeIntervalSince(currentDrive.startTime))
        driveChatShareContext = DriveChatShareContext(
            driveId: currentDrive.id,
            previewTitle: driveTitle,
            previewDistanceMeters: currentDrive.distanceMeters,
            previewDriveTimeSeconds: seconds,
            previewCompletedAt: end,
            lockedCircleID: lockedShareCircleID,
            mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput(route: currentDrive.route, pathSamples: drivenPathSamples)
        )
    }

    /// Profile `profileCard`-style hero: centered drive identity, glass toolbar controls.
    private var summaryHeroCard: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 12) {
                Color.clear.frame(height: 40)

                ProfileDriveListFlagBadge(size: 56)

                Text(driveTitle)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
                    .padding(.horizontal, 8)

                HStack(spacing: 6) {
                    Image(systemName: driveFlavor.systemImage)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.purple)
                    Text(driveFlavor.label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.purple)
                }

                Text(timestampText)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.62))
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 20)

            HStack {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.74))
                        .frame(width: 36, height: 36)
                        .modifier(DriveSummaryHeroGlassCircleChrome())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")

                Spacer()

                HStack(spacing: 8) {
                    if isOwner {
                        Button {
                            presentDriveChatShare()
                        } label: {
                            Image(systemName: "square.and.arrow.up")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.74))
                                .frame(width: 36, height: 36)
                                .modifier(DriveSummaryHeroGlassCircleChrome())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Share drive")

                        ownerMoreMenu
                    }
                }
            }
            .padding(12)
        }
        .background { summaryHeroBackground }
        .overlay { summaryHeroBorder }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private var summaryHeroBackground: some View {
        let rr = RoundedRectangle(cornerRadius: 28, style: .continuous)
        return ZStack {
            rr.fill(Color(red: 0.03, green: 0.034, blue: 0.055))
            rr.fill(
                LinearGradient(
                    colors: [
                        Color.purple.opacity(0.12),
                        Color.clear,
                        Color.blue.opacity(0.10),
                        Color.clear,
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            rr.fill(
                LinearGradient(
                    colors: [
                        Color.white.opacity(0.068),
                        Color.clear,
                        Color.black.opacity(0.28),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
    }

    private var summaryHeroBorder: some View {
        RoundedRectangle(cornerRadius: 28, style: .continuous)
            .strokeBorder(
                LinearGradient(
                    colors: [
                        Color.purple.opacity(0.44),
                        Color.white.opacity(0.085),
                        Color.blue.opacity(0.32),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                lineWidth: 1
            )
    }

    private var ownerMoreMenu: some View {
        Menu {
            Button("Rename") {
                driveNameDraft = driveTitle
                isShowingRenameAlert = true
            }
            Button("Delete", role: .destructive) {
                isShowingDeleteConfirmation = true
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.74))
                .frame(width: 36, height: 36)
                .modifier(DriveSummaryHeroGlassCircleChrome())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Drive options")
    }

    private var vehicleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            ProfileSectionHeader(title: "Vehicle")

            garageCarContent
        }
        .profileSectionCardChrome()
    }

    @ViewBuilder
    private var garageCarContent: some View {
        if let car = displayGarageCar {
            if isOwner {
                garageCarMenu {
                    garageCarCard(car: car)
                }
            } else {
                garageCarCard(car: car)
            }
        } else if isOwner, !garageCars.isEmpty {
            garageCarMenu {
                chooseCarPlaceholder
            }
        } else {
            chooseCarPlaceholder
        }
    }

    private func garageCarMenu<Label: View>(@ViewBuilder label: () -> Label) -> some View {
        Menu {
            Button("No car selected") {
                updateDriveGarageCar(nil)
            }
            ForEach(garageCars) { garageCar in
                Button(garageCar.displayName) {
                    updateDriveGarageCar(garageCar.id)
                }
            }
        } label: {
            label()
        }
        .buttonStyle(.plain)
        .disabled(isUpdatingGarageCar)
    }

    private var chooseCarPlaceholder: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(isOwner ? "Choose a car" : "Car not specified")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            Text(
                isOwner
                    ? (garageCars.isEmpty ? "Add a car in Garage first, then assign it here." : "Tap to pick which car you drove.")
                    : "This drive does not include a car yet."
            )
            .font(.caption)
            .foregroundStyle(.white.opacity(0.56))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .profileListItemChrome()
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func garageCarCard(car: GarageCar) -> some View {
        ZStack(alignment: .topTrailing) {
            GarageCarCard(car: car, canEdit: false, onEdit: {}, onDelete: {})
            if isUpdatingGarageCar {
                ProgressView()
                    .controlSize(.regular)
                    .tint(.white)
                    .padding(12)
            }
        }
    }

    private var routeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            ProfileSectionHeader(title: "Your Route")

            if hasRouteMapPreview {
                Button {
                    openOnMap()
                } label: {
                    DriveRouteMapPreview(
                        lineCoordinates: lineCoordinates,
                        mapPoints: mapPoints,
                        completedWaypointIndexes: completedWaypointIndexes,
                        pathSamples: drivenPathSamples,
                        height: 220,
                        lineSourceID: "drive-summary-\(currentDrive.id)-line"
                    )
                }
                .buttonStyle(.plain)
                .disabled(!canViewTrailOnMap)
                .accessibilityLabel("View drive on map")
                .accessibilityHint("Opens the full screen trail map")
            } else {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.white.opacity(0.04))
                    .frame(height: 220)
                    .overlay {
                        Text(routePreviewUnavailableText)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.45))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
            }
        }
        .profileSectionCardChrome()
    }

    private var metricsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Driving Stats")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                alignment: .leading,
                spacing: 10
            ) {
                driveStatTile(title: "Distance", value: distanceText, icon: "road.lanes")
                driveStatTile(title: "Drive Time", value: durationText, icon: "timer")
                driveStatTile(title: "Average Pace", value: averageSpeedText, icon: "speedometer")
                driveStatTile(title: "Samples", value: "\(currentDrive.pointsCount)", icon: "point.3.connected.trianglepath.dotted")
            }
        }
        .profileSectionCardChrome()
    }

    /// Matches `drivingStatTile` on Profile.
    private func driveStatTile(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.purple)
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white.opacity(0.55))
                .lineLimit(1)
        }
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: 86, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var bottomActionBar: some View {
        HStack(spacing: 10) {
            Button {
                openOnMap()
            } label: {
                Label("View on Map", systemImage: "map")
                    .font(.headline.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .primaryCTAButtonStyle(horizontalPadding: 16, verticalPadding: 16)
            .disabled(!canViewTrailOnMap)
            .opacity(canViewTrailOnMap ? 1 : 0.45)

            if isOwner {
                Button {
                    presentDriveChatShare()
                } label: {
                    OttoGlassIconButtonLabel(
                        systemImage: "square.and.arrow.up",
                        size: CGSize(width: 56, height: 56),
                        cornerRadius: 12,
                        font: .system(size: 17, weight: .bold)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Share drive")
            }
        }
    }

    private var canViewTrailOnMap: Bool {
        drivenPathSamples.count >= 2 || lineCoordinates.count >= 2
    }

    private var hasRouteMapPreview: Bool {
        canViewTrailOnMap
    }

    private var routePreviewUnavailableText: String {
        if currentDrive.route != nil {
            if currentDrive.pointsCount == 1 {
                return "GPS trail incomplete (1 sample). Route data could not be drawn."
            }
            return "Route preview unavailable"
        }
        if currentDrive.pointsCount == 1 {
            return "Not enough GPS trail to draw a map (1 sample recorded)."
        }
        return "No GPS trail recorded for this drive."
    }

    // MARK: - Actions

    private func openOnMap() {
        guard canViewTrailOnMap else { return }
        isShowingTrailMap = true
    }

    private func renameDrive() async {
        let trimmed = driveNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isOwner, !trimmed.isEmpty, !isRenamingDrive else { return }
        isRenamingDrive = true
        defer { isRenamingDrive = false }
        do {
            let updated = try await APIClient.shared.updateDriveTitle(
                driveId: currentDrive.id,
                title: trimmed
            )
            await MainActor.run {
                publishDriveUpdate(updated)
                isShowingRenameAlert = false
            }
        } catch {
            await MainActor.run {
                appState.errorMessage = "Couldn't rename this drive."
            }
        }
    }

    private func deleteDrive() async {
        guard isOwner, !isDeletingDrive else { return }
        isDeletingDrive = true
        defer { isDeletingDrive = false }
        do {
            try await APIClient.shared.deleteDrive(driveId: currentDrive.id)
            await MainActor.run {
                appState.presentDeletedToast(for: "Drive")
                onDriveDeleted?()
                dismiss()
            }
        } catch {
            await MainActor.run {
                appState.presentDeleteFailedToast(for: "drive")
            }
        }
    }

    private func updateDriveGarageCar(_ garageCarId: String?) {
        guard isOwner, !isUpdatingGarageCar else { return }
        isUpdatingGarageCar = true
        Task {
            do {
                let updated = try await APIClient.shared.updateDriveGarageCar(
                    driveId: currentDrive.id,
                    garageCarId: garageCarId
                )
                await MainActor.run {
                    publishDriveUpdate(updated)
                    isUpdatingGarageCar = false
                }
            } catch {
                await MainActor.run {
                    appState.errorMessage = "Couldn’t update the car for this drive."
                    isUpdatingGarageCar = false
                }
            }
        }
    }

    @MainActor
    private func publishDriveUpdate(_ updated: DriveDTO) {
        currentDrive = updated
        appState.applyDriveUpdate(updated)
        onDriveUpdated(updated)
    }

    // MARK: - Path samples

    private func refreshDriveFromServer() async {
        do {
            let refreshed = try await APIClient.shared.fetchDrive(
                driveId: currentDrive.id,
                circleId: lockedShareCircleID
            )
            await MainActor.run {
                currentDrive = refreshed
            }
        } catch {
            // Keep list payload when detail fetch fails.
        }
    }

    private func loadDrivenPathSamples() async {
        guard currentDrive.pointsCount >= 2 else {
            await MainActor.run { drivenPathSamples = [] }
            return
        }

        do {
            let points = try await APIClient.shared.fetchDrivePoints(
                driveId: currentDrive.id,
                circleId: lockedShareCircleID
            )
            let samples = points.compactMap(DrivePathSample.from)
            await MainActor.run {
                drivenPathSamples = samples
            }
        } catch {
            await MainActor.run {
                drivenPathSamples = []
            }
        }
    }

    // MARK: - Derived data

    private var displayMetrics: DriveSummaryDisplayMetrics {
        DriveSummaryDisplayMetrics(drive: currentDrive)
    }

    private var driveTitle: String {
        displayMetrics.listTitle
    }

    private var driveFlavor: DriveSummaryFlavor {
        DriveSummaryFlavor.flavor(for: currentDrive.endTime ?? currentDrive.startTime)
    }

    private var timestampText: String {
        displayMetrics.timestampText
    }

    private var shareText: String {
        "\(driveTitle)\n\(timestampText)\n\(distanceText) • \(durationText) • \(averageSpeedText)"
    }

    private var distanceText: String {
        displayMetrics.distanceText
    }

    private var durationText: String {
        displayMetrics.durationText
    }

    private var averageSpeedText: String {
        displayMetrics.averageSpeedText
    }

    private var lineCoordinates: [CLLocationCoordinate2D] {
        guard let route = currentDrive.route else { return [] }
        return route.toMapLineCoordinates()
    }

    private var mapPoints: [RouteMapPointModel] {
        if let route = currentDrive.route {
            let routePoints = route.toMapPoints()
            if !routePoints.isEmpty { return routePoints }
        }
        return RouteMapGeometry.trailEndpointMapPoints(
            from: drivenPathSamples,
            idPrefix: "drive-summary-\(currentDrive.id)"
        )
    }

    private var completedWaypointIndexes: Set<Int> {
        Set(currentDrive.route?.completedWaypointIndexes ?? [])
    }

    private var selectedGarageCar: GarageCar? {
        guard let id = currentDrive.garageCarId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
            return nil
        }
        return garageCars.first { $0.id == id }
    }

    private var displayGarageCar: GarageCar? {
        if let selectedGarageCar { return selectedGarageCar }
        guard let dto = currentDrive.garageCar else { return nil }
        return GarageCar(
            id: dto.id,
            nickname: dto.nickname ?? "",
            make: dto.make,
            makeId: dto.makeId,
            model: dto.model,
            year: dto.year,
            color: dto.color,
            logoSlug: dto.logoSlug,
            isPrimary: dto.isPrimary,
            sortOrder: dto.sortOrder,
            photoUrl: dto.photo?.url
        )
    }
}
