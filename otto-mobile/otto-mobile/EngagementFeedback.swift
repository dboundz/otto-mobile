import UIKit

/// In-thread haptics only while the app is active. Visible chat alerts come from APNs (`willPresent` / OS).
enum EngagementFeedback {
    @MainActor
    static func handleSquadChatThreadEngagementIfNeeded(
        _ message: CircleChatMessageDTO,
        currentUserId: String,
        focusedCircleId: String?
    ) {
        guard UIApplication.shared.applicationState == .active else { return }
        guard message.resolvedSenderUserId != currentUserId else { return }
        guard message.messageType != "system" else { return }
        guard focusedCircleId == message.circleId else { return }

        let mentionForMe = message.mentions.contains { $0.userId == currentUserId }
        let mentionAll = message.mentions.contains { $0.userId == SquadChatAllMention.userId }
        let replyToMe = message.replyTo?.senderUserId == currentUserId
        guard mentionForMe || mentionAll || replyToMe else { return }

        lightImpact()
    }

    @MainActor
    static func handleDirectThreadEngagementIfNeeded(
        _ message: DirectMessageDTO,
        currentUserId: String,
        focusedConversationId: String?
    ) {
        guard UIApplication.shared.applicationState == .active else { return }
        guard message.senderUserId != currentUserId else { return }
        guard focusedConversationId == message.conversationId else { return }
        let replyToMe = message.replyTo?.senderUserId == currentUserId
        guard replyToMe else { return }

        lightImpact()
    }

    @MainActor
    private static func lightImpact() {
        let gen = UIImpactFeedbackGenerator(style: .light)
        gen.prepare()
        if #available(iOS 13.0, *) {
            gen.impactOccurred(intensity: 0.62)
        } else {
            gen.impactOccurred()
        }
    }
}
