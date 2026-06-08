import os
import SwiftUI
import Combine
import UIKit

extension Notification.Name {
    /// Posted when the user taps the Profile tab while it is already selected (e.g. pop garage nested in profile).
    static let ottoProfileTabReselected = Notification.Name("otto.profileTabReselected")
    /// Posted when the user taps the Squads tab while it is already selected (pop to squads list).
    static let ottoCirclesTabReselected = Notification.Name("otto.circlesTabReselected")
}

private enum RootBackgroundRefreshTimer {
    static let tick = Timer.publish(every: 5, on: .main, in: .common).autoconnect()
}

struct RootTabView: View {
    @State private var selectedTab: AppTab = .circles
    @State private var profileTabUserID: String?
    @State private var isKeyboardVisible = false
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    var body: some View {
        rootTabChrome
            .modifier(RootTabFocusRoutingModifier(
                selectedTab: $selectedTab,
                profileTabUserID: $profileTabUserID
            ))
            .modifier(RootTabSessionModifier(
                selectedTab: selectedTab,
                isKeyboardVisible: $isKeyboardVisible,
                scenePhase: scenePhase
            ))
    }

    /// Tab shell only — keeps `body` type-checkable (long modifier chains blow the compiler budget).
    private var rootTabChrome: some View {
        // Use a VStack, not a ZStack: full-screen `Map(…ignoresSafeArea())` (MapKit) often wins hit-testing
        // over a tab bar that was drawn “on top” in a ZStack, so taps to the bar never fire.
        //
        // Keep the tab bar as a VStack sibling (not root `safeAreaInset`): nested chat screens use
        // `.background(…ignoresSafeArea())`, so inset-based tab chrome buried the composer behind the bar.
        VStack(spacing: 0) {
            rootTabContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if !isKeyboardVisible {
                customTabBar
                    .zIndex(100)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.22), value: isKeyboardVisible)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .appToastOverlay(toast: appState.activeToast) {
            appState.activeToast = nil
        }
        .overlay { rootProfileLevelUpOverlay }
        .animation(.spring(response: 0.38, dampingFraction: 0.86), value: appState.activeProfileLevelUp)
    }

    @ViewBuilder
    private var rootProfileLevelUpOverlay: some View {
        if let levelUp = appState.activeProfileLevelUp {
            ProfileLevelUpModal(levelUp: levelUp) {
                appState.dismissProfileLevelUp()
            }
            .transition(.opacity.combined(with: .scale(scale: 0.96)))
            .zIndex(200)
        }
    }

    private var rootTabContent: some View {
        ZStack {
            MapScreen(isActive: selectedTab == .map)
                .rootTabVisibility(isSelected: selectedTab == .map)

            CirclesScreen()
                .rootTabVisibility(isSelected: selectedTab == .circles)

            EventsScreen(isActive: selectedTab == .events)
                .rootTabVisibility(isSelected: selectedTab == .events)

            NavigationStack {
                GarageScreen()
            }
            .rootTabVisibility(isSelected: selectedTab == .garage)

            ProfileScreen(
                profileUserID: profileTabUserID,
                onDismissPeerProfile: { profileTabUserID = nil }
            )
                .rootTabVisibility(isSelected: selectedTab == .profile)
        }
    }

    private var customTabBar: some View {
        HStack(spacing: 0) {
            tabButton(
                .circles,
                title: "Squads",
                systemImage: "person.3",
                badgeCount: rootSquadsBadgeCount,
                showSecondaryDot: rootSquadsInviteIndicator
            )
            tabButton(.events, title: "Events", systemImage: "calendar")
            mapTabButton
            tabButton(.garage, title: "Garage", systemImage: "car")
            tabButton(.profile, title: "Profile", systemImage: "person.crop.circle")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.black.opacity(0.88))
        .overlay(Rectangle().stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private var rootSquadsBadgeCount: Int {
        appState.totalChatUnreadCount
    }

    private var rootSquadsInviteIndicator: Bool {
        !appState.myCircleInvites.isEmpty
    }

    private func tabButton(
        _ tab: AppTab,
        title: String,
        systemImage: String,
        badgeCount: Int = 0,
        showSecondaryDot: Bool = false
    ) -> some View {
        Button {
            if tab == .profile {
                profileTabUserID = nil
                if selectedTab == .profile {
                    NotificationCenter.default.post(name: .ottoProfileTabReselected, object: nil)
                }
            }
            if tab == .circles, selectedTab == .circles {
                NotificationCenter.default.post(name: .ottoCirclesTabReselected, object: nil)
            }
            selectedTab = tab
        } label: {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 4) {
                    Image(systemName: systemImage)
                        .font(.subheadline.weight(.semibold))
                    Text(title)
                        .font(.caption2)
                }
                .frame(maxWidth: .infinity)

                if badgeCount > 0 {
                    Text("\(min(badgeCount, 9))")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.green)
                        .clipShape(Capsule())
                        .offset(x: -18, y: -4)
                } else if showSecondaryDot {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 8, height: 8)
                        .offset(x: -16, y: -2)
                }
            }
            .foregroundStyle(selectedTab == tab ? Color.purple : Color.gray)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var mapTabButton: some View {
        Button {
            if selectedTab == .profile {
                profileTabUserID = nil
            }
            selectedTab = .map
        } label: {
            TimelineView(.periodic(from: .now, by: 1)) { timeline in
                mapTabButtonLabel(now: timeline.date)
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func mapTabButtonLabel(now: Date) -> some View {
        let tabColor = selectedTab == .map ? Color.purple : Color.gray
        let indicatorColor = appState
            .driveSessionPillPresentation(
                now: now,
                routeName: appState.activeDriveSession?.routeName,
                viewerCount: nil
            )
            .mapTabIndicatorColor
        // Match `tabButton` ZStack + offset so the dot isn’t clipped and sits “floating” like the Squads badge.
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 4) {
                Image(systemName: "map")
                    .font(.subheadline.weight(.semibold))
                Text("Map")
                    .font(.caption2)
            }
            .foregroundStyle(tabColor)
            .frame(maxWidth: .infinity)

            if let indicatorColor {
                Circle()
                    .fill(indicatorColor)
                    .frame(width: 9, height: 9)
                    .overlay(Circle().stroke(Color.black.opacity(0.4), lineWidth: 1.2))
                    .shadow(color: indicatorColor.opacity(0.65), radius: 3, y: 0)
                    .offset(x: -18, y: -4)
            }
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
    }
}

private struct ProfileLevelUpModal: View {
    let levelUp: ProfileLevelUpDTO
    let onContinue: () -> Void

    private var tierColor: Color {
        profileTierColor(levelUp.progression.tierId)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.74)
                .ignoresSafeArea()

            VStack(spacing: 22) {
                VStack(spacing: 8) {
                    Text("Level Up")
                        .font(.caption.weight(.heavy))
                        .textCase(.uppercase)
                        .tracking(6)
                        .foregroundStyle(tierColor)

                    VStack(spacing: 0) {
                        Text("You reached")
                            .font(.system(size: 38, weight: .heavy, design: .rounded))
                            .foregroundStyle(.white)
                        Text(levelUp.reachedDisplayName)
                            .font(.system(size: 42, weight: .heavy, design: .rounded))
                            .foregroundStyle(tierColor)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                }

                Image(levelUp.progression.levelBadgeAssetName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 230, height: 230)
                    .shadow(color: tierColor.opacity(0.75), radius: 28)

                if let nextProgression = levelUp.nextProgression {
                    nextUpRow(nextProgression)
                }

                Button(action: onContinue) {
                    Text("Continue")
                        .font(.headline.weight(.heavy))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(
                            LinearGradient(
                                colors: [Color(red: 0.20, green: 0.15, blue: 1.0), Color(red: 0.72, green: 0.20, blue: 0.88)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .shadow(color: Color.purple.opacity(0.45), radius: 16)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 40)
            .padding(.vertical, 34)
            .frame(maxWidth: 520)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.black.opacity(0.92))
                    .overlay(
                        LinearGradient(
                            colors: [tierColor.opacity(0.22), .clear],
                            startPoint: .top,
                            endPoint: .center
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(tierColor.opacity(0.56), lineWidth: 1.5)
            )
            .padding(.horizontal, 28)
        }
    }

    private func nextUpRow(_ progression: ProfileProgressionDTO) -> some View {
        let nextColor = profileTierColor(progression.tierId)
        return HStack(spacing: 14) {
            Image(progression.levelBadgeAssetName)
                .resizable()
                .scaledToFit()
                .frame(width: 54, height: 54)
                .shadow(color: nextColor.opacity(0.45), radius: 10)

            VStack(alignment: .leading, spacing: 2) {
                Text("Next up")
                    .font(.caption.weight(.heavy))
                    .textCase(.uppercase)
                    .tracking(3)
                    .foregroundStyle(.white.opacity(0.58))
                Text(levelUp.nextDisplayName ?? progression.tierName)
                    .font(.headline.weight(.heavy))
                    .foregroundStyle(.white)
                Text("Level \(progression.level)")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.62))
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }
}

private func profileTierColor(_ tierId: String) -> Color {
    switch tierId {
    case "rookie":
        return Color(red: 0.78, green: 0.48, blue: 0.27)
    case "qualifier":
        return Color(red: 0.78, green: 0.80, blue: 0.84)
    case "runner":
        return Color(red: 1.0, green: 0.84, blue: 0.22)
    case "pacer":
        return Color(red: 0.12, green: 0.66, blue: 1.0)
    case "apex":
        return Color(red: 0.66, green: 0.31, blue: 1.0)
    case "legend":
        return Color(red: 1.0, green: 0.36, blue: 0.46)
    default:
        return .purple
    }
}

private enum AppTab {
    case map
    case circles
    case events
    case garage
    case profile
}

private extension View {
    func rootTabVisibility(isSelected: Bool) -> some View {
        self
            .opacity(isSelected ? 1 : 0)
            .allowsHitTesting(isSelected)
            .accessibilityHidden(!isSelected)
            .zIndex(isSelected ? 1 : 0)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Root tab modifiers (split out of `body` for Swift type-checker limits)

private struct RootTabFocusRoutingModifier: ViewModifier {
    @Binding var selectedTab: AppTab
    @Binding var profileTabUserID: String?
    @EnvironmentObject private var appState: AppState

    func body(content: Content) -> some View {
        content
            .onChange(of: appState.pendingMapFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .map
            }
            .onChange(of: appState.pendingMapRouteSelection?.id) { _, routeSelectionID in
                guard routeSelectionID != nil else { return }
                selectedTab = .map
            }
            .onChange(of: appState.pendingLocationSharingFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .map
            }
            .onChange(of: appState.pendingSharingSheetPresentation) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .map
            }
            .onChange(of: appState.pendingCircleFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .circles
            }
            .onChange(of: appState.pendingSquadsInvitesFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .circles
            }
            .onChange(of: appState.pendingProfileFocus) { _, newFocus in
                guard let focus = newFocus else { return }
                profileTabUserID = focus.userID
                _ = appState.consumePendingProfileFocus()
                selectedTab = .profile
            }
            .onChange(of: appState.pendingDirectMessageFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .circles
            }
            .onChange(of: appState.pendingEventFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .events
            }
            .onChange(of: appState.pendingEventsMyEventsFocus) { _, newFocus in
                guard newFocus != nil else { return }
                selectedTab = .events
            }
            .onChange(of: appState.mapTabOnlyRequest) { _, newValue in
                guard newValue != nil else { return }
                selectedTab = .map
                appState.consumeMapTabOnlyRequest()
            }
            .onChange(of: appState.garageTabFocusRequest) { _, newValue in
                guard newValue != nil else { return }
                selectedTab = .garage
                appState.consumeGarageTabFocusRequest()
            }
    }
}

private struct RootTabSessionModifier: ViewModifier {
    let selectedTab: AppTab
    @Binding var isKeyboardVisible: Bool
    let scenePhase: ScenePhase
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    func body(content: Content) -> some View {
        content
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
                isKeyboardVisible = true
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                isKeyboardVisible = false
            }
            .onChange(of: selectedTab) { _, newTab in
                UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder),
                    to: nil,
                    from: nil,
                    for: nil
                )
                appState.setCirclesRootTabSelected(newTab == .circles)
                OttoLog.ui.info("Root tab: \(String(describing: newTab))")
            }
            .onAppear {
                appState.setCirclesRootTabSelected(selectedTab == .circles)
            }
            .onAppear(perform: configureLocationAndCheckInHandlers)
            .onChange(of: appState.isAuthenticated) { _, isAuthed in
                if isAuthed {
                    appState.refreshEventCheckInMonitoring(locationService: locationService)
                } else {
                    locationService.clearEventCheckInRegions()
                }
            }
            .onReceive(
                Publishers.CombineLatest4(
                    appState.$upcomingEvents,
                    appState.$communityEvents,
                    appState.$squadGoingEventsForCheckIn,
                    appState.$autoEventCheckInEnabled
                )
            ) { _, _, _, _ in
                guard appState.isAuthenticated else {
                    locationService.clearEventCheckInRegions()
                    return
                }
                appState.refreshEventCheckInMonitoring(locationService: locationService)
            }
            .onChange(of: appState.circles.map(\.id).joined(separator: "\u{1e}")) { _, _ in
                guard appState.isAuthenticated else { return }
                Task {
                    await appState.refreshSquadGoingEventsForCheckIn()
                    appState.refreshEventCheckInMonitoring(locationService: locationService)
                }
            }
            .onChange(of: locationService.movementMode) { _, newMode in
                handleMovementModeChange(newMode)
            }
            .onReceive(RootBackgroundRefreshTimer.tick) { _ in
                handleBackgroundRefreshTick()
            }
    }

    private func configureLocationAndCheckInHandlers() {
        locationService.setLiveSampleHandler { location, speedMetersPerSecond in
            Task { @MainActor in
                guard appState.isAuthenticated else { return }
                await appState.ingestDriveSessionSample(
                    location: location,
                    speedMetersPerSecond: speedMetersPerSecond,
                    movementMode: locationService.movementMode
                )
                await appState.attemptForegroundAutoCheckInIfNeeded(locationService: locationService)
            }
        }
        if appState.isAuthenticated {
            Task {
                await appState.refreshSquadGoingEventsForCheckIn()
                appState.refreshEventCheckInMonitoring(locationService: locationService)
            }
        }
        locationService.onEnterEventCheckInRegion = { eventId in
            Task { @MainActor in
                await appState.handleEventCheckInRegionEntered(
                    eventId: eventId,
                    locationService: locationService
                )
            }
        }
    }

    /// Driving-only presence keys off `movementMode`; flush immediately when motion updates.
    private func handleMovementModeChange(_ newMode: FriendMovementMode) {
        guard appState.isAuthenticated else { return }
        guard appState.isSharingEnabled, appState.sharingSessionMode == .drivingOnly else { return }
        Task { @MainActor in
            let loc = locationService.latestSample ?? locationService.lastLocation
            let speed = locationService.effectiveSpeedMetersPerSecond()
            await appState.pushPresence(
                location: loc,
                speedMetersPerSecond: speed,
                movementMode: newMode
            )
            if let loc {
                await appState.throttledRecordDrivePathSample(
                    location: loc,
                    speedMetersPerSecond: speed,
                    movementMode: newMode
                )
            }
        }
    }

    private func handleBackgroundRefreshTick() {
        guard appState.isAuthenticated else { return }
        guard selectedTab != .map else { return }
        guard selectedTab != .garage else { return }
        Task(priority: .utility) {
            await appState.pushInAppPresenceHeartbeatsIfNeeded(scenePhase: scenePhase)
            if appState.isChatRealtimeConnected {
                await appState.refreshPresenceForSelectedCircle()
            } else {
                await appState.refreshPresenceForAllCircles()
            }
            await appState.refreshMyCircleInvites()
            if appState.isSharingEnabled {
                let loc = locationService.latestSample ?? locationService.lastLocation
                let speed = locationService.effectiveSpeedMetersPerSecond()
                await appState.pushPresence(
                    location: loc,
                    speedMetersPerSecond: speed,
                    movementMode: locationService.movementMode
                )
                if let loc {
                    await appState.throttledRecordDrivePathSample(
                        location: loc,
                        speedMetersPerSecond: speed,
                        movementMode: locationService.movementMode
                    )
                }
            }
        }
    }
}
