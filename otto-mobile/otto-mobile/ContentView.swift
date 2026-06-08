//
//  ContentView.swift
//  otto-mobile
//
//  Created by Darren on 4/22/26.
//

import SwiftUI
import Foundation
import CoreLocation
import UIKit

struct ContentView: View {
    /// Same bitmap as `Launch Screen.storyboard` (`LaunchSplashArtwork.png`). `Image(_:)` + catalog was unreliable here; `UIImage` loads the bundled PNG consistently.
    private static let launchSplashUIImage: UIImage? = {
        if let img = UIImage(named: "LaunchSplashArtwork") { return img }
        guard let url = Bundle.main.url(forResource: "LaunchSplashArtwork", withExtension: "png"),
              let data = try? Data(contentsOf: url),
              let img = UIImage(data: data) else { return nil }
        return img
    }()

    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @EnvironmentObject private var raceTracksDatasetStore: RaceTracksDatasetStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var isPreparingAuthenticatedSession = false
    @State private var hasPreparedAuthenticatedSession = false
    @State private var prepareTask: Task<Void, Never>?
    private let minimumBootSplashDuration: TimeInterval = 1.25

    var body: some View {
        Group {
            if appState.isAuthenticated {
                ZStack {
                    RootTabView()
                        .allowsHitTesting(hasPreparedAuthenticatedSession && !shouldPresentMarketingOnboarding)

                    if isPreparingAuthenticatedSession || !hasPreparedAuthenticatedSession {
                        bootSplash
                            .transition(.opacity)
                    }

                    if shouldPresentMarketingOnboarding {
                        MarketingOnboardingView(
                            onFinished: { wasReplay in
                                appState.marketingOnboardingDidFinish(wasReplay: wasReplay)
                            }
                        )
                        .environmentObject(appState)
                        .transition(.opacity.combined(with: .scale(0.985)))
                        .zIndex(2)
                    }

                    if let squadInvite = appState.squadInvitePrompt {
                        SquadInviteAcceptDialog(
                            resolve: squadInvite,
                            isAccepting: appState.isAcceptingSquadInvitePrompt,
                            onAccept: {
                                Task { await appState.acceptSquadInvitePrompt() }
                            },
                            onDecline: {
                                appState.dismissSquadInvitePrompt()
                            }
                        )
                        .zIndex(3)
                    }
                }
            } else {
                AuthScreen()
            }
        }
        .animation(.easeOut(duration: 0.22), value: hasPreparedAuthenticatedSession)
        .animation(.spring(response: 0.48, dampingFraction: 0.9), value: shouldPresentMarketingOnboarding)
        .onAppear {
            locationService.onAuthorizationChanged = {
                syncLocationSession()
            }
            schedulePreparationIfNeeded()
            clearInternalDebugToolsIfNotAllowed()
            syncLocationSession()
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
            if scenePhase == .active {
                Task { await appState.reconcileChatUnreadStateFromNetworkIfNeeded() }
            }
        }
        .onChange(of: appState.isAuthenticated) { _, isAuthenticated in
            if isAuthenticated {
                schedulePreparationIfNeeded(force: true)
            } else {
                prepareTask?.cancel()
                isPreparingAuthenticatedSession = false
                hasPreparedAuthenticatedSession = false
            }
            clearInternalDebugToolsIfNotAllowed()
            syncLocationSession()
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
        }
        .onChange(of: hasPreparedAuthenticatedSession) { _, isReady in
            guard isReady, appState.isAuthenticated else { return }
            Task { await appState.processPendingSquadInviteIfNeeded() }
        }
        .onChange(of: appState.currentUserID) { _, _ in
            clearInternalDebugToolsIfNotAllowed()
        }
        .onChange(of: appState.isSharingEnabled) { _, _ in
            stopSharingIfRequiredPermissionsAreMissing()
            syncLocationSession()
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
        }
        .onChange(of: locationService.authorizationStatus) { _, _ in
            stopSharingIfRequiredPermissionsAreMissing()
            syncLocationSession()
        }
        .onChange(of: locationService.motionAuthorizationStatus) { _, _ in
            stopSharingIfRequiredPermissionsAreMissing()
            syncLocationSession()
        }
        .onChange(of: appState.locationSessionSyncTick) { _, _ in
            syncLocationSession()
        }
        .onChange(of: appState.isEventsScreenActive) { _, _ in
            syncLocationSession()
        }
        .onChange(of: appState.sharingSessionStartedAt) { _, _ in
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
        }
        .onChange(of: appState.activeDriveID) { _, _ in
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
        }
        .onChange(of: appState.isMapScreenActive) { _, _ in
            syncLocationSession()
        }
        .onChange(of: appState.isMapRouteSessionActive) { _, _ in
            syncLocationSession()
            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase)
        }
        .onChange(of: appState.isRouteBuilderPresented) { _, _ in
            syncLocationSession()
        }
        .onChange(of: scenePhase) { _, phase in
            handleScenePhaseChange(phase)
        }
        .overlay(alignment: .topLeading) {
            if appState.isAuthenticated, appState.isSharingEnabled {
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    Color.clear
                        .frame(width: 0, height: 0)
                        .onAppear {
                            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase, now: timeline.date)
                        }
                        .onChange(of: timeline.date) { _, newDate in
                            applyKeepScreenAwakeIdleTimerPolicy(scenePhase: scenePhase, now: newDate)
                        }
                }
            }
        }
    }

    /// Foreground-only; re-evaluated on a 1s tick while timed sharing is on so expiry restores the idle timer.
    private func applyKeepScreenAwakeIdleTimerPolicy(scenePhase: ScenePhase, now: Date = Date()) {
        let isForeground = scenePhase == .active
        let sharingSessionActive =
            appState.isSharingSessionActive
        let shouldKeepAwake =
            isForeground
            && appState.isAuthenticated
            && (sharingSessionActive || appState.activeDriveID != nil || appState.isMapRouteSessionActive || appState.activeDriveSession != nil)
        UIApplication.shared.isIdleTimerDisabled = shouldKeepAwake
    }

    private var shouldPresentMarketingOnboarding: Bool {
        guard appState.isAuthenticated, hasPreparedAuthenticatedSession else { return false }
        if appState.marketingOnboardingReplayRequested { return true }
        return !appState.marketingOnboardingCompleted
    }

    private var bootSplash: some View {
        ZStack {
            Color.black
            if let ui = Self.launchSplashUIImage {
                Image(uiImage: ui)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFill()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            }
        }
        .ignoresSafeArea()
        .overlay(alignment: .bottom) {
            ProgressView("Warming up the engine…")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.white.opacity(0.85))
                .tint(.white)
                .padding(.bottom, bootSplashLoaderBottomPadding)
        }
    }

    /// Matches `Launch Screen.storyboard` edge-to-edge artwork; loader sits in an overlay so it cannot shift the image.
    private var bootSplashLoaderBottomPadding: CGFloat {
        max(72, UIScreen.main.bounds.height * 0.16)
    }

    private func schedulePreparationIfNeeded(force: Bool = false) {
        guard appState.isAuthenticated else { return }
        guard force || !hasPreparedAuthenticatedSession else { return }
        prepareTask?.cancel()
        prepareTask = Task {
            await prepareAuthenticatedSession()
        }
    }

    private func prepareAuthenticatedSession() async {
        guard appState.isAuthenticated else { return }
        let startedAt = Date()
        await MainActor.run {
            isPreparingAuthenticatedSession = true
        }

        // Ensure core state is loaded before first tab/map render.
        await appState.refreshCircles()
        await appState.refreshMyCircleInvites()
        await appState.refreshAutoCheckInCandidates()
        appState.hydrateSquadChatCachesFromDisk()
        await appState.warmSquadChatTranscripts()

        await waitForMinimumBootSplashDuration(startedAt: startedAt)

        guard !Task.isCancelled else { return }
        await MainActor.run {
            hasPreparedAuthenticatedSession = true
            isPreparingAuthenticatedSession = false
        }
    }

    private func waitForMinimumBootSplashDuration(startedAt: Date) async {
        let elapsed = Date().timeIntervalSince(startedAt)
        guard elapsed < minimumBootSplashDuration else { return }
        let remainingNanos = UInt64((minimumBootSplashDuration - elapsed) * 1_000_000_000)
        try? await Task.sleep(nanoseconds: remainingNanos)
    }

    private func clearInternalDebugToolsIfNotAllowed() {
        guard !appState.canAccessInternalDebugTools else { return }
        OttoDebugSettings.mapLocationOverlayEnabled = false
        OttoDebugSettings.routeBuilderPerfOverlayEnabled = false
        OttoDebugSettings.routeCheckpointMapOverlayEnabled = false
    }

    private func handleScenePhaseChange(_ phase: ScenePhase) {
        syncLocationSession()
        applyKeepScreenAwakeIdleTimerPolicy(scenePhase: phase)
        if phase == .active {
            stopSharingIfRequiredPermissionsAreMissing()
            Task { await appState.reconcileChatUnreadStateFromNetworkIfNeeded() }
            appState.connectChatRealtimeIfNeeded()
            Task { await raceTracksDatasetStore.refreshIfStale() }
            appState.applySharingPersistenceFromSuiteIfNeeded()
            Task { await appState.pushInAppPresenceHeartbeatsIfNeeded(scenePhase: phase, force: true) }
            Task { await TimeZoneSync.syncIfNeeded(isAuthenticated: appState.isAuthenticated) }
            Task {
                await appState.refreshAutoCheckInCandidates()
                appState.refreshEventCheckInMonitoring(locationService: locationService)
                await appState.attemptForegroundAutoCheckInIfNeeded(locationService: locationService)
            }
            if appState.isAuthenticated, appState.squadInvitePrompt == nil,
               let token = appState.pendingInviteToken, !token.isEmpty
            {
                Task { await appState.processPendingSquadInviteIfNeeded() }
            }
        } else if phase == .background {
            if appState.needsBackgroundLocationUpdates,
               locationService.authorizationStatus == .authorizedWhenInUse
            {
                appState.showToast(
                    String(localized: "drive_background_location_foreground_only_toast"),
                    icon: "location.slash.fill"
                )
            }
            Task { await appState.pushOutOfAppPresenceHeartbeat() }
        }
    }

    private func syncLocationSession() {
        guard appState.isAuthenticated else {
            locationService.applyDesiredState(.none)
            return
        }

        // Fine-location prompts are deferred to Map/Events primers and drive flows.
        let foregroundLocationAllowed =
            scenePhase == .active
            && (
                locationService.authorizationStatus == .authorizedWhenInUse
                || locationService.authorizationStatus == .authorizedAlways
            )
        let backgroundDriveLocationAllowed =
            appState.needsBackgroundLocationUpdates
            && locationService.authorizationStatus == .authorizedAlways
        let needsGPS =
            foregroundLocationAllowed
            || backgroundDriveLocationAllowed
            || appState.isSharingEnabled
            || appState.needsBackgroundLocationUpdates
            || appState.isMapScreenActive
            || appState.isMapRouteSessionActive
            || appState.isEventsScreenActive
            || appState.isRouteBuilderPresented
        let needsMotion =
            appState.isSharingEnabled
            && locationService.hasAttemptedMotionPermissionPrompt
            && locationService.motionAuthorizationStatus == .authorized
        let needs = LocationSessionNeeds(
            gps: needsGPS,
            motion: needsMotion,
            freshDisplay: foregroundLocationAllowed
                || appState.isMapScreenActive
                || appState.isSharingEnabled
                || appState.isRouteBuilderPresented
        )
        locationService.applyDesiredState(needs)
    }

    private func stopSharingIfRequiredPermissionsAreMissing() {
        guard appState.isSharingEnabled else { return }
        let hasLocation: Bool
        switch locationService.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            hasLocation = true
        case .notDetermined, .restricted, .denied:
            hasLocation = false
        @unknown default:
            hasLocation = false
        }
        if locationService.motionAuthorizationStatus == .authorized {
            locationService.refreshMotionAuthorizationStatus()
        }
        let hasMotion = locationService.motionAuthorizationStatus == .authorized
        guard !hasLocation || !hasMotion else { return }
        appState.stopSharingSession()
        syncLocationSession()
    }
}
