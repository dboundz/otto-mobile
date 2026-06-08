import SwiftUI

struct OttoMapSheetHeader: View {
    let title: String
    var subtitle: String? = nil
    var onDone: () -> Void
    var doneDisabled: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: subtitle == nil ? 0 : 4) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button("Done", action: onDone)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .buttonStyle(.plain)
                    .disabled(doneDisabled)
            }

            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
        .padding(.top, 10)
        .padding(.bottom, 16)
    }
}

struct StartDriveSheet: View {
    var onQuickDrive: () -> Void
    var onRouteDrive: () -> Void
    var onGoLive: () -> Void
    var onCancel: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    OttoMapSheetHeader(title: "Start Drive", onDone: onCancel)

                    VStack(alignment: .leading, spacing: 0) {
                        driveOption(
                            icon: "steeringwheel",
                            backgroundColor: DriveSessionPalette.sessionPurple,
                            title: "Quick Drive",
                            subtitle: "Hit the road without a planned route and just drive",
                            action: onQuickDrive
                        )
                        driveOption(
                            icon: SavedRouteIcon.systemImageName,
                            backgroundColor: RouteMapMarkerColors.startAccent,
                            title: "Route Drive",
                            subtitle: "Drive a planned route with checkpoints and navigation",
                            action: onRouteDrive
                        )
                        driveOption(
                            icon: "dot.radiowaves.left.and.right",
                            backgroundColor: DriveSessionPalette.goLivePink,
                            title: "Go Live",
                            subtitle: "Broadcast your drive and live location to your Squads",
                            action: onGoLive
                        )
                    }
                }
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, OttoScreenChrome.bottomPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
        }
    }

    private func driveOption(
        icon: String,
        backgroundColor: Color,
        title: String,
        subtitle: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(backgroundColor))

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.58))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.35))
            }
            .padding(14)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 10)
    }
}

enum DriveLaunchDockMode: Equatable {
    case route(SavedRouteDTO)
    case quick
    case live
}

struct DriveDockHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct DriveLaunchDock<OptionsMenu: View>: View {
    @EnvironmentObject private var appState: AppState
    let mode: DriveLaunchDockMode
    let isSessionActive: Bool
    @Binding var recordDrive: Bool
    var shareLocation: Binding<Bool>? = nil
    var shareCircleIDs: Binding<Set<String>>? = nil
    var circles: [DriveCircle] = []
    var showStartDistanceWarning: Bool = false
    var routeMetadata: String?
    var isOwnedRoute: Bool = false
    var statusText: String
    var canManageRoute: Bool = false
    var onStartDrive: () -> Void
    var onStopDrive: () -> Void
    var onCancel: () -> Void
    var onManageRoute: (() -> Void)?
    var expandedMaxHeight: CGFloat? = nil
    @ViewBuilder var optionsMenu: () -> OptionsMenu

    private var showsRecordDriveToggle: Bool {
        !isSessionActive && (mode == .quick || {
            if case .route = mode { return true }
            return false
        }())
    }

    private var showsShareLocationToggle: Bool { showsRecordDriveToggle }

    private var dockShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: 28,
            bottomLeadingRadius: 0,
            bottomTrailingRadius: 0,
            topTrailingRadius: 28,
            style: .continuous
        )
    }

    private var dockContentSpacing: CGFloat { isSessionActive ? 10 : 18 }

    private var isShareLocationExpanded: Bool {
        shareLocation?.wrappedValue == true
    }

    private var canAnimatePanelExpansion: Bool {
        expandedMaxHeight != nil && !isSessionActive
    }

    private var dockExpansionAnimation: Animation {
        .spring(response: 0.34, dampingFraction: 0.86)
    }

    @State private var measuredCollapsedHeight: CGFloat = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !isSessionActive {
                header
                    .padding(.bottom, dockContentSpacing)

                ScrollView(showsIndicators: false) {
                    preStartScrollableContent
                }
                .scrollDisabled(!isShareLocationExpanded)
                .frame(
                    maxHeight: isShareLocationExpanded && canAnimatePanelExpansion ? .infinity : nil,
                    alignment: .top
                )
                .layoutPriority(isShareLocationExpanded ? 1 : 0)

                preStartFooterBlock
            } else {
                VStack(alignment: .leading, spacing: dockContentSpacing) {
                    actionButtons
                    statusFooter
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, isSessionActive ? 16 : 12)
        .padding(.bottom, isSessionActive ? 12 : 14)
        .fixedSize(horizontal: false, vertical: shouldHugCollapsedHeight)
        .frame(
            maxWidth: .infinity,
            maxHeight: resolvedPanelMaxHeight,
            alignment: .top
        )
        .background {
            GeometryReader { proxy in
                Color.clear
                    .preference(key: DriveDockHeightKey.self, value: proxy.size.height)
                    .onChange(of: proxy.size.height) { _, height in
                        recordCollapsedHeightIfNeeded(height)
                    }
            }
        }
        .background(Color.black)
        .clipShape(dockShape)
        .overlay {
            dockShape
                .stroke(Color.white.opacity(0.11), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.38), radius: 24, y: -10)
        .animation(dockExpansionAnimation, value: isShareLocationExpanded)
    }

    private var resolvedPanelMaxHeight: CGFloat? {
        guard canAnimatePanelExpansion else { return nil }
        if isShareLocationExpanded {
            return expandedMaxHeight
        }
        return measuredCollapsedHeight > 0 ? measuredCollapsedHeight : nil
    }

    private var shouldHugCollapsedHeight: Bool {
        canAnimatePanelExpansion && !isShareLocationExpanded && measuredCollapsedHeight == 0
    }

    private func recordCollapsedHeightIfNeeded(_ height: CGFloat) {
        guard height > 0, !isShareLocationExpanded, canAnimatePanelExpansion else { return }
        guard let expandedMaxHeight, height < expandedMaxHeight * 0.85 else { return }
        measuredCollapsedHeight = height
    }

    private var preStartFooterBlock: some View {
        VStack(alignment: .leading, spacing: dockContentSpacing) {
            actionButtons
            statusFooter
        }
        .padding(.top, dockContentSpacing)
    }

    @ViewBuilder
    private var preStartScrollableContent: some View {
        VStack(alignment: .leading, spacing: dockContentSpacing) {
            if showStartDistanceWarning {
                distanceWarningBanner
            }
            DriveCarPickerRow()
            if showsRecordDriveToggle {
                recordDriveToggle
            }
            if showsShareLocationToggle, let shareLocation {
                shareLocationToggle(isOn: animatedShareLocationBinding(shareLocation))
            }
            if showsShareLocationToggle,
               isShareLocationExpanded,
               let shareCircleIDs {
                SharingSquadPickerSection(
                    circles: circles,
                    selectedCircleIDs: shareCircleIDs
                )
            }
        }
    }

    @ViewBuilder
    private var header: some View {
        switch mode {
        case .route(let route):
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(Color.purple)
                    .frame(width: 13, height: 13)
                    .padding(.top, 7)

                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Text(route.name)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        if isOwnedRoute {
                            Text("OWNER")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Capsule().fill(Color.purple.opacity(0.45)))
                        }
                    }

                    if let routeMetadata {
                        Text(routeMetadata)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                optionsMenu()
            }
        case .quick:
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "steeringwheel")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(DriveSessionPalette.sessionPurple))

                VStack(alignment: .leading, spacing: 6) {
                    Text("Quick Drive")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                    Text("Hit the road without a planned route and just drive")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)
            }
        case .live:
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(DriveSessionPalette.goLivePink))

                VStack(alignment: .leading, spacing: 6) {
                    Text("Go Live")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                    Text("Broadcast your drive and live location to your Squads")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)
            }
        }
    }

    private var recordDriveToggle: some View {
        OttoToggleSettingCard(
            title: String(localized: "drive_record_toggle_title"),
            isOn: $recordDrive,
            systemImage: "point.topleft.down.to.point.bottomright.curvepath.fill",
            helperText: String(localized: "drive_record_toggle_helper")
        )
    }

    private func animatedShareLocationBinding(_ shareLocation: Binding<Bool>) -> Binding<Bool> {
        Binding(
            get: { shareLocation.wrappedValue },
            set: { newValue in
                withAnimation(dockExpansionAnimation) {
                    shareLocation.wrappedValue = newValue
                }
            }
        )
    }

    private func shareLocationToggle(isOn: Binding<Bool>) -> some View {
        OttoToggleSettingCard(
            title: String(localized: "drive_share_location_toggle_title"),
            isOn: isOn,
            systemImage: "location.fill",
            helperText: String(localized: "drive_share_location_toggle_helper")
        )
    }

    private var distanceWarningBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.orange)
            Text("You need to be within 500 feet of this route's starting point before starting the drive.")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white.opacity(0.88))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    @ViewBuilder
    private var actionButtons: some View {
        if isSessionActive {
            Button(role: .destructive, action: onStopDrive) {
                Label("Stop Drive", systemImage: "stop.circle.fill")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.red.opacity(0.82))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
        } else {
            HStack(spacing: 14) {
                Button(action: onStartDrive) {
                    Label(startDriveActionTitle, systemImage: startDriveActionIcon)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(startDriveActionColor)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)

                Button(action: onCancel) {
                    Label("Cancel", systemImage: "xmark")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var statusFooter: some View {
        HStack(spacing: 10) {
            if isSessionActive {
                Circle()
                    .fill(Color.red)
                    .frame(width: 8, height: 8)
            } else {
                Image(systemName: statusIconName)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(statusIconColor)
            }
            Text(statusText)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .minimumScaleFactor(0.85)
            Spacer(minLength: 0)
            if !isSessionActive, case .route = mode, let onManageRoute {
                Button("Manage", action: onManageRoute)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.purple)
                    .disabled(!canManageRoute)
                    .opacity(canManageRoute ? 1 : 0)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, isSessionActive ? 10 : 12)
        .background(Color.white.opacity(0.045))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var startDriveActionTitle: String {
        switch mode {
        case .live: return "Go Live"
        case .route, .quick: return "Start Drive"
        }
    }

    private var startDriveActionIcon: String {
        switch mode {
        case .live: return "dot.radiowaves.left.and.right"
        case .route: return "location.north.fill"
        case .quick: return "location.north.fill"
        }
    }

    private var startDriveActionColor: Color {
        switch mode {
        case .live: return DriveSessionPalette.goLivePink
        case .route, .quick: return RouteMapMarkerColors.startButton
        }
    }

    private var statusIconName: String {
        switch mode {
        case .route: return SavedRouteIcon.systemImageName
        case .quick: return "steeringwheel"
        case .live: return "dot.radiowaves.left.and.right"
        }
    }

    private var statusIconColor: Color {
        switch mode {
        case .route: return .purple
        case .quick: return RouteMapMarkerColors.startAccent
        case .live: return DriveSessionPalette.goLivePink
        }
    }
}

struct DriveControlsSheet: View {
    let presentation: DriveSessionPillPresentation
    let startedAt: Date
    let timeText: String
    let distanceText: String
    let topSpeedText: String
    @Binding var shareLive: Bool
    @Binding var saveDrive: Bool
    var routeName: String?
    var routeCheckpointText: String?
    var onAddSquad: () -> Void
    var onStopDrive: () -> Void
    var onDismiss: () -> Void

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                OttoMapSheetHeader(
                    title: controlsTitle,
                    subtitle: "Started \(startedAt.formatted(date: .omitted, time: .shortened))",
                    onDone: onDismiss
                )

                HStack(spacing: 10) {
                    statCard(title: "Time", value: timeText)
                    statCard(title: "Distance", value: distanceText)
                    statCard(title: "Top Speed", value: topSpeedText)
                }

                OttoToggleSettingCard(
                    title: "Share Live",
                    isOn: $shareLive,
                    systemImage: "dot.radiowaves.left.and.right"
                )
                if showsSaveDriveToggle {
                    OttoToggleSettingCard(
                        title: String(localized: "drive_record_toggle_title"),
                        isOn: $saveDrive,
                        systemImage: "square.and.arrow.down.fill",
                        helperText: String(localized: "drive_record_toggle_helper")
                    )
                }

                Button(action: onAddSquad) {
                    Label("Add to Squad", systemImage: "person.2.fill")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)

                if let routeName, let routeCheckpointText {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Route")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white.opacity(0.45))
                        Text(routeName)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Text(routeCheckpointText)
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.58))
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.055))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom) {
            Button(role: .destructive, action: onStopDrive) {
                Text("Stop Drive")
                    .font(.headline.weight(.heavy))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(Color.red.opacity(0.82))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            .padding(.bottom, 10)
        }
    }

    private var controlsTitle: String {
        switch presentation {
        case .recording, .recordingAndSharing: return "Recording Drive"
        case .route: return "Route Drive"
        case .sharing, .pausedSharing: return "Sharing Live"
        case .idle: return "Drive Session"
        }
    }

    private var showsSaveDriveToggle: Bool {
        switch presentation {
        case .recording, .route:
            return false
        case .sharing, .recordingAndSharing, .pausedSharing, .idle:
            return true
        }
    }

    private func statCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.48))
            Text(value)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct DriveCarPickerRow: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        if appState.showsDriveCarPicker {
            pickerContent
        }
    }

    private var pickerContent: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(String(localized: "drive_car_picker_title"))
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(.white.opacity(0.56))

            if appState.garageCars.isEmpty {
                Text(String(localized: "drive_car_picker_empty"))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.white.opacity(0.48))
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        pickerChip(
                            title: String(localized: "drive_car_picker_none"),
                            logoURL: nil,
                            isSelected: appState.selectedSharingCarID.isEmpty
                        ) {
                            appState.selectSharingCar("")
                        }
                        ForEach(appState.garageCars) { car in
                            pickerChip(
                                title: car.displayName,
                                logoURL: car.brandLogoURL,
                                isSelected: appState.selectedSharingCarID == car.id
                            ) {
                                appState.selectSharingCar(car.id)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func pickerChip(
        title: String,
        logoURL: URL?,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let logoURL {
                    GarageCarBrandLogoBadge(url: logoURL, logoSize: 22)
                }
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(isSelected ? Color.purple.opacity(0.22) : Color.white.opacity(0.055))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(isSelected ? Color.purple.opacity(0.72) : Color.white.opacity(0.08), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
