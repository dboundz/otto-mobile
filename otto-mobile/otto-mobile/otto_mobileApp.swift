//
//  otto_mobileApp.swift
//  otto-mobile
//
//  Created by Darren on 4/22/26.
//

import os
import SwiftUI
import UIKit
import UserNotifications

extension Notification.Name {
    static let didRegisterForRemoteNotifications = Notification.Name("didRegisterForRemoteNotifications")
    static let retryPushDeviceRegistration = Notification.Name("otto.retryPushDeviceRegistration")
    static let didTapRemoteNotification = Notification.Name("didTapRemoteNotification")
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static var pendingNotificationUserInfo: [AnyHashable: Any]?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        OttoAnalytics.configure()
        UNUserNotificationCenter.current().delegate = self
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(handleDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(handleWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        if let remote = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            PushDiagnostics.logRemoteNotificationReceived(remote, source: "cold-start")
        }
        return true
    }

    @objc private func handleDidEnterBackground() {
        PushDiagnostics.logAppLifecycle("didEnterBackground")
    }

    @objc private func handleWillEnterForeground() {
        PushDiagnostics.logAppLifecycle("willEnterForeground")
        NotificationCenter.default.post(name: .retryPushDeviceRegistration, object: nil)
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        NotificationCenter.default.post(
            name: .didRegisterForRemoteNotifications,
            object: token
        )
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        OttoLog.push.error("APNs registration failed: \(String(describing: error))")
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any]
    ) async -> UIBackgroundFetchResult {
        PushDiagnostics.logRemoteNotificationReceived(userInfo, source: "didReceiveRemoteNotification")
        return .noData
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        PushDiagnostics.logRemoteNotificationReceived(userInfo, source: "notification-tap")
        AppDelegate.pendingNotificationUserInfo = userInfo
        NotificationCenter.default.post(
            name: .didTapRemoteNotification,
            object: userInfo
        )
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        let info = notification.request.content.userInfo
        let type = Self.pushPayloadString(info["type"]) ?? "unknown"
        let circleId = Self.pushPayloadString(info["circleId"])
        let messageId = Self.pushPayloadString(info["messageId"])
        let state = await MainActor.run { UIApplication.shared.applicationState }
        if let messageId,
           PushDiagnostics.wasMessagePresentationHandled(messageId: messageId) {
            PushDiagnostics.logWillPresentDecision(
                type: type,
                appState: state,
                circleId: circleId,
                options: []
            )
            return []
        }
        let options = await MainActor.run {
            Self.presentationOptions(for: info, type: type, appState: state)
        }
        if Self.digestAlertTypes.contains(type),
           notification.request.content.title.isEmpty,
           notification.request.content.body.isEmpty,
           let alert = Self.alertContent(from: info),
           !options.isEmpty {
            Self.postDigestForegroundFallbackNotification(type: type, alert: alert, userInfo: info)
        }
        if let messageId, !options.isEmpty {
            PushDiagnostics.markMessagePresentationHandled(messageId: messageId)
        }
        PushDiagnostics.logWillPresentDecision(
            type: type,
            appState: state,
            circleId: circleId,
            options: options
        )
        return options
    }

    @MainActor
    private static let digestAlertTypes: Set<String> = [
        "event.events_today",
        "event.check_in",
        "event.auto_check_in",
        "circle.event.invited",
        "profile.progression.level_up",
    ]

    @MainActor
    private static func presentationOptions(
        for info: [AnyHashable: Any],
        type: String,
        appState: UIApplication.State
    ) -> UNNotificationPresentationOptions {
        guard type != "unknown" else {
            return [.banner, .list, .sound]
        }

        // Event digests / invites: always surface banner + sound (not chat-throttled).
        if digestAlertTypes.contains(type) {
            var options: UNNotificationPresentationOptions = [.banner, .list, .sound]
            if !AppState.isSoundEffectsEnabled {
                options.remove(.sound)
            }
            return options
        }

        let silentReactionTypes: Set<String> = ["circle.chat.reaction", "direct.message.reaction"]
        if silentReactionTypes.contains(type) {
            if appState == .active {
                if type == "direct.message.reaction",
                   let conv = pushPayloadString(info["conversationId"]),
                   conv == PushFocusBridge.activeDirectConversationId {
                    return []
                }
                if type == "circle.chat.reaction",
                   let cid = pushPayloadString(info["circleId"]),
                   cid == PushFocusBridge.activeChatCircleId {
                    return []
                }
                let opts: UNNotificationPresentationOptions = [.banner, .list]
                return applySquadMuteToPresentation(options: opts, userInfo: info, pushType: type)
            }
            return applySquadMuteToPresentation(options: [.banner, .list], userInfo: info, pushType: type)
        }

        let level3Types: Set<String> = [
            "circle.chat.mention",
            "circle.chat.reply",
            "circle.chat.new_message",
            "direct.message",
            "presence.location_started",
        ]
        guard level3Types.contains(type) else {
            return [.banner, .list, .sound]
        }

        let state = appState
        guard state == .active else {
            return applySquadMuteToPresentation(
                options: [.banner, .list, .sound],
                userInfo: info,
                pushType: type
            )
        }

        if type == "direct.message",
           let conv = pushPayloadString(info["conversationId"]),
           conv == PushFocusBridge.activeDirectConversationId {
            return []
        }

        if (type == "circle.chat.mention" || type == "circle.chat.reply" || type == "circle.chat.new_message"),
           let cid = pushPayloadString(info["circleId"]),
           cid == PushFocusBridge.activeChatCircleId {
            return []
        }

        let throttleOutcome = ChatEngagementThrottle.evaluatePushPresentation(
            type: type,
            circleId: pushPayloadString(info["circleId"]),
            conversationId: pushPayloadString(info["conversationId"])
        )
        switch throttleOutcome {
        case .suppress:
            return []
        case .fullAlert:
            let conversationKey = conversationKeyForPush(type: type, info: info)
            if let conversationKey {
                ChatEngagementThrottle.recordFullAlert(forConversationKey: conversationKey, pushType: type)
            }
            var options: UNNotificationPresentationOptions = [.banner, .list, .sound]
            options = applySquadMuteToPresentation(options: options, userInfo: info, pushType: type)
            if !AppState.isSoundEffectsEnabled {
                options.remove(.sound)
            }
            return options
        case .silentBannerUpdate:
            let conversationKey = conversationKeyForPush(type: type, info: info)
            if let conversationKey {
                ChatEngagementThrottle.recordSilentUpdate(forConversationKey: conversationKey, pushType: type)
            }
            return applySquadMuteToPresentation(options: [.banner, .list], userInfo: info, pushType: type)
        }
    }

    @MainActor
    private static func conversationKeyForPush(type: String, info: [AnyHashable: Any]) -> String? {
        switch type {
        case "circle.chat.mention", "circle.chat.reply", "circle.chat.new_message":
            guard let cid = pushPayloadString(info["circleId"]) else { return nil }
            return ChatEngagementThrottle.squadConversationKey(circleId: cid)
        case "direct.message":
            guard let conv = pushPayloadString(info["conversationId"]) else { return nil }
            return ChatEngagementThrottle.directConversationKey(conversationId: conv)
        default:
            return nil
        }
    }

    /// Strips `.sound` when per-squad mute applies (local UserDefaults).
    private static func applySquadMuteToPresentation(
        options: UNNotificationPresentationOptions,
        userInfo: [AnyHashable: Any],
        pushType: String
    ) -> UNNotificationPresentationOptions {
        let cid = pushPayloadString(userInfo["circleId"])
        guard SquadNotificationMuteStore.shouldSuppressChatNotificationSound(circleId: cid, pushType: pushType) else {
            return options
        }
        var result = options
        result.remove(.sound)
        return result
    }

    private static func pushPayloadString(_ value: Any?) -> String? {
        if let s = value as? String {
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }
        if let s = value as? NSString {
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : String(t)
        }
        if let n = value as? NSNumber {
            return n.stringValue
        }
        return nil
    }

    private struct DigestAlertContent {
        let title: String
        let body: String
    }

    private static func alertContent(from userInfo: [AnyHashable: Any]) -> DigestAlertContent? {
        if let aps = userInfo["aps"] as? [String: Any] {
            if let alert = aps["alert"] as? [String: Any] {
                let title = pushPayloadString(alert["title"]) ?? ""
                let body = pushPayloadString(alert["body"]) ?? ""
                if !title.isEmpty || !body.isEmpty {
                    return DigestAlertContent(
                        title: title.isEmpty ? "Events today" : title,
                        body: body
                    )
                }
            }
            if let alert = aps["alert"] as? String, !alert.isEmpty {
                return DigestAlertContent(title: "Events today", body: alert)
            }
        }
        let title = pushPayloadString(userInfo["title"])
        let body = pushPayloadString(userInfo["body"])
        if title != nil || body != nil {
            return DigestAlertContent(title: title ?? "Events today", body: body ?? "")
        }
        return nil
    }

    @MainActor
    private static func postDigestForegroundFallbackNotification(
        type: String,
        alert: DigestAlertContent,
        userInfo: [AnyHashable: Any]
    ) {
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.body
        content.sound = .default
        var merged = userInfo.reduce(into: [String: Any]()) { partial, entry in
            if let key = entry.key as? String {
                partial[key] = entry.value
            }
        }
        merged["type"] = type
        merged["source"] = "digest-foreground-fallback"
        content.userInfo = merged
        let request = UNNotificationRequest(
            identifier: "digest-foreground-fallback-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                OttoLog.push.error("Digest foreground fallback notification failed: \(String(describing: error))")
            }
        }
    }
}

@main
struct otto_mobileApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var appState = AppState()
    @StateObject private var locationService = LocationService()
    @StateObject private var raceTracksDatasetStore = RaceTracksDatasetStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .environmentObject(locationService)
                .environmentObject(raceTracksDatasetStore)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    appState.handleIncomingURL(url)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        appState.handleIncomingURL(url)
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: .didRegisterForRemoteNotifications)) { notification in
                    guard let token = notification.object as? String else { return }
                    appState.didReceivePushDeviceToken(token)
                }
                .onReceive(NotificationCenter.default.publisher(for: .retryPushDeviceRegistration)) { _ in
                    Task { await appState.ensurePushDeviceTokenRegisteredWithBackend() }
                }
                .onReceive(NotificationCenter.default.publisher(for: .didTapRemoteNotification)) { notification in
                    guard let userInfo = notification.object as? [AnyHashable: Any] else { return }
                    appState.handleRemoteNotificationTap(userInfo)
                }
                .onAppear {
                    if let userInfo = AppDelegate.pendingNotificationUserInfo {
                        AppDelegate.pendingNotificationUserInfo = nil
                        appState.handleRemoteNotificationTap(userInfo)
                    }
                    Task { await raceTracksDatasetStore.refreshIfStale() }
                    if let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                        OttoLog.app.info("Otto iOS start version=\(v) baseURL=\(APIConfig.baseURL.absoluteString)")
                    } else {
                        OttoLog.app.info("Otto iOS start baseURL=\(APIConfig.baseURL.absoluteString)")
                    }
                    PushDiagnostics.logAppLifecycle("app-onAppear")
                }
        }
    }
}
