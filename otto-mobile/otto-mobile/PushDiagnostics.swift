import Foundation
import os
import UIKit
import UserNotifications

/// Structured push/APNs diagnostics for Console.app (`category=Push`).
enum PushDiagnostics {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "otto.mobile",
        category: "Push"
    )

    private static var recentForegroundAlertMessageIds: [String: Date] = [:]
    private static let dedupWindow: TimeInterval = 45

    static func logAppLifecycle(_ name: String) {
        let state = UIApplication.shared.applicationState
        logger.info(
            "lifecycle \(name, privacy: .public) appState=\(describeAppState(state), privacy: .public) activeChat=\(PushFocusBridge.activeChatCircleId ?? "nil", privacy: .public) activeDM=\(PushFocusBridge.activeDirectConversationId ?? "nil", privacy: .public)"
        )
    }

    static func logPushFocusBridgeChange(circleId: String?, reason: String) {
        logger.info(
            "focusBridge circleId=\(circleId ?? "nil", privacy: .public) reason=\(reason, privacy: .public)"
        )
    }

    static func logRemoteNotificationReceived(
        _ userInfo: [AnyHashable: Any],
        source: String
    ) {
        let type = stringValue(userInfo["type"]) ?? "unknown"
        let circleId = stringValue(userInfo["circleId"]) ?? "nil"
        let messageId = stringValue(userInfo["messageId"]) ?? "nil"
        let state = UIApplication.shared.applicationState
        logger.info(
            "remoteReceived source=\(source, privacy: .public) type=\(type, privacy: .public) circleId=\(circleId, privacy: .public) messageId=\(messageId, privacy: .public) appState=\(describeAppState(state), privacy: .public) activeChat=\(PushFocusBridge.activeChatCircleId ?? "nil", privacy: .public)"
        )
    }

    static func logWillPresentDecision(
        type: String,
        appState: UIApplication.State,
        circleId: String?,
        options: UNNotificationPresentationOptions
    ) {
        logger.info(
            "willPresent type=\(type, privacy: .public) appState=\(describeAppState(appState), privacy: .public) pushCircleId=\(circleId ?? "nil", privacy: .public) activeChat=\(PushFocusBridge.activeChatCircleId ?? "nil", privacy: .public) opts=\(describePresentationOptions(options), privacy: .public)"
        )
    }

    static func logDeviceTokenRegistration(
        tokenPrefix: String,
        environment: String,
        bundleId: String,
        backendDeviceCount: Int?
    ) {
        if let backendDeviceCount {
            logger.info(
                "tokenRegistered prefix=\(tokenPrefix, privacy: .public) env=\(environment, privacy: .public) bundle=\(bundleId, privacy: .public) backendActiveIosTokens=\(backendDeviceCount)"
            )
        } else {
            logger.info(
                "tokenRegistered prefix=\(tokenPrefix, privacy: .public) env=\(environment, privacy: .public) bundle=\(bundleId, privacy: .public)"
            )
        }
    }

    static func logRegisteredDevicesAudit(_ summary: String) {
        logger.info("deviceTokenAudit \(summary, privacy: .public)")
    }

    static func wasMessagePresentationHandled(messageId: String) -> Bool {
        pruneDedupCache()
        let key = normalizedMessageId(messageId)
        guard !key.isEmpty else { return false }
        return recentForegroundAlertMessageIds[key] != nil
    }

    static func markMessagePresentationHandled(messageId: String) {
        pruneDedupCache()
        let key = normalizedMessageId(messageId)
        guard !key.isEmpty else { return }
        recentForegroundAlertMessageIds[key] = Date()
    }

    private static func normalizedMessageId(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func pruneDedupCache() {
        let cutoff = Date().addingTimeInterval(-dedupWindow)
        recentForegroundAlertMessageIds = recentForegroundAlertMessageIds.filter { $0.value > cutoff }
    }

    private static func stringValue(_ value: Any?) -> String? {
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

    private static func describeAppState(_ state: UIApplication.State) -> String {
        switch state {
        case .active: "active"
        case .inactive: "inactive"
        case .background: "background"
        @unknown default: "unknown(\(state.rawValue))"
        }
    }

    private static func describePresentationOptions(_ options: UNNotificationPresentationOptions) -> String {
        if options.isEmpty { return "none" }
        var parts: [String] = []
        if options.contains(.banner) { parts.append("banner") }
        if options.contains(.list) { parts.append("list") }
        if options.contains(.sound) { parts.append("sound") }
        if options.contains(.badge) { parts.append("badge") }
        return parts.joined(separator: "+")
    }
}
