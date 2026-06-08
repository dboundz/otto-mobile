import Foundation

enum ChatEngagementAlertOutcome: Equatable {
    case suppress
    case fullAlert
    case silentBannerUpdate(suppressedCount: Int)
}

@MainActor
enum ChatEngagementThrottle {
    private static let cooldownTiers: [TimeInterval] = [15, 30, 45, 60]
    private static let idleDecayInterval: TimeInterval = 60

    private struct ConversationNotificationState {
        var lastAlertAt: Date?
        var lastActivityAt: Date?
        var cooldownUntil: Date?
        var suppressedCount: Int = 0
        var tierIndex: Int = 0
    }

    private static var stateByKey: [String: ConversationNotificationState] = [:]

    static func squadConversationKey(circleId: String) -> String {
        let trimmed = circleId.trimmingCharacters(in: .whitespacesAndNewlines)
        return "squad:\(trimmed)"
    }

    static func directConversationKey(conversationId: String) -> String {
        let trimmed = conversationId.trimmingCharacters(in: .whitespacesAndNewlines)
        return "dm:\(trimmed)"
    }

    static func foregroundNotificationIdentifier(forConversationKey key: String) -> String {
        "otto-chat-foreground-\(key.replacingOccurrences(of: ":", with: "-"))"
    }

    static func evaluateSquadMessage(
        circleId: String,
        pushType: String = "circle.chat.new_message",
        focusedChatCircleId: String?,
        now: Date = Date()
    ) -> ChatEngagementAlertOutcome {
        let trimmedCircle = circleId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedCircle.isEmpty else { return .suppress }

        if focusedChatCircleId == trimmedCircle {
            return .suppress
        }
        return evaluateCooldown(forKey: squadConversationKey(circleId: trimmedCircle), now: now)
    }

    static func evaluateDirectMessage(
        conversationId: String,
        focusedConversationId: String?,
        now: Date = Date()
    ) -> ChatEngagementAlertOutcome {
        let trimmedConversation = conversationId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedConversation.isEmpty else { return .suppress }

        if focusedConversationId == trimmedConversation {
            return .suppress
        }
        return evaluateCooldown(forKey: directConversationKey(conversationId: trimmedConversation), now: now)
    }

    static func evaluatePushPresentation(
        type: String,
        circleId: String?,
        conversationId: String?,
        now: Date = Date()
    ) -> ChatEngagementAlertOutcome {
        switch type {
        case "circle.chat.mention", "circle.chat.reply", "circle.chat.new_message":
            guard let circleId else { return .fullAlert }
            return evaluateSquadMessage(
                circleId: circleId,
                pushType: type,
                focusedChatCircleId: PushFocusBridge.activeChatCircleId,
                now: now
            )
        case "direct.message":
            guard let conversationId else { return .fullAlert }
            return evaluateDirectMessage(
                conversationId: conversationId,
                focusedConversationId: PushFocusBridge.activeDirectConversationId,
                now: now
            )
        default:
            return .fullAlert
        }
    }

    static func recordFullAlert(
        forConversationKey key: String,
        pushType: String = "circle.chat.new_message",
        now: Date = Date()
    ) {
        var state = stateByKey[key] ?? ConversationNotificationState()
        applyIdleDecay(state: &state, now: now)
        let tiered = usesTieredCooldown(pushType: pushType)
        let duration = cooldownDuration(tierIndex: state.tierIndex, tiered: tiered)
        state.lastAlertAt = now
        state.lastActivityAt = now
        state.cooldownUntil = now.addingTimeInterval(duration)
        if tiered, state.suppressedCount > 0 {
            state.tierIndex = min(state.tierIndex + 1, cooldownTiers.count - 1)
        }
        state.suppressedCount = 0
        stateByKey[key] = state
    }

    static func recordSilentUpdate(
        forConversationKey key: String,
        pushType: String = "circle.chat.new_message",
        now: Date = Date()
    ) {
        var state = stateByKey[key] ?? ConversationNotificationState()
        applyIdleDecay(state: &state, now: now)
        state.suppressedCount += 1
        state.lastActivityAt = now
        let tiered = usesTieredCooldown(pushType: pushType)
        if state.cooldownUntil == nil {
            state.cooldownUntil = now.addingTimeInterval(cooldownDuration(tierIndex: state.tierIndex, tiered: tiered))
        }
        stateByKey[key] = state
    }

    static func bannerBody(base: String, suppressedCount: Int) -> String {
        guard suppressedCount > 0 else { return base }
        return "\(base) (+\(suppressedCount) more)"
    }

    /// Clears in-memory throttle state (unit tests).
    static func resetForTesting() {
        stateByKey.removeAll()
    }

    /// Current tier index for a conversation (unit tests).
    static func tierIndexForTesting(conversationKey: String) -> Int {
        stateByKey[conversationKey]?.tierIndex ?? 0
    }

    /// Cooldown deadline for a conversation (unit tests).
    static func cooldownUntilForTesting(conversationKey: String) -> Date? {
        stateByKey[conversationKey]?.cooldownUntil
    }

    private static func usesTieredCooldown(pushType: String) -> Bool {
        pushType == "circle.chat.new_message"
    }

    private static func cooldownDuration(tierIndex: Int, tiered: Bool) -> TimeInterval {
        if tiered {
            return cooldownTiers[min(max(tierIndex, 0), cooldownTiers.count - 1)]
        }
        return cooldownTiers[0]
    }

    private static func applyIdleDecay(state: inout ConversationNotificationState, now: Date) {
        guard state.tierIndex > 0, let lastActivity = state.lastActivityAt else { return }
        let idleSeconds = now.timeIntervalSince(lastActivity)
        let stepsDown = min(state.tierIndex, Int(idleSeconds / idleDecayInterval))
        guard stepsDown > 0 else { return }
        state.tierIndex -= stepsDown
        state.suppressedCount = 0
    }

    private static func evaluateCooldown(forKey key: String, now: Date) -> ChatEngagementAlertOutcome {
        var state = stateByKey[key] ?? ConversationNotificationState()
        applyIdleDecay(state: &state, now: now)
        state.lastActivityAt = now
        stateByKey[key] = state

        if let cooldownUntil = state.cooldownUntil, now < cooldownUntil {
            return .silentBannerUpdate(suppressedCount: state.suppressedCount + 1)
        }
        return .fullAlert
    }
}
