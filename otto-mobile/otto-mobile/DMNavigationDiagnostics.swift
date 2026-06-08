import Foundation
import os
import SwiftUI

/// Temporary compose → DM navigation diagnostics. Filter Console for `[dm-nav]`.
enum DMNavigationDiagnostics {
    private static var composeAttemptCounter = 0

    /// Last navigation branch label; shown on DEBUG Unavailable fallback only.
    private(set) static var lastBranchDescription = "none"

    static func nextComposeAttempt() -> Int {
        composeAttemptCounter += 1
        return composeAttemptCounter
    }

    static func idPrefix(_ raw: String?) -> String {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return "nil"
        }
        return String(trimmed.prefix(8))
    }

    static func logOpenNewDmSuccess(
        attempt: Int,
        recipientUserId: String,
        conversation: DirectConversationDTO
    ) {
        let participantPrefixes = conversation.participantUserIds.map { idPrefix($0) }.joined(separator: ",")
        log(
            "openNewDm attempt=\(attempt) recipient=\(idPrefix(recipientUserId)) conversation=\(idPrefix(conversation.id)) "
                + "hasOtherUser=\(conversation.otherUser != nil) otherUserId=\(idPrefix(conversation.otherUser?.id)) "
                + "participantUserIds=[\(participantPrefixes)] lastMessageAt=\(conversation.lastMessageAt != nil)"
        )
    }

    static func logOpenNewDmNavigate(
        attempt: Int,
        route: String,
        cacheHit: Bool,
        sheetPresented: Bool
    ) {
        log(
            "openNewDm attempt=\(attempt) navigate route=\(route) cacheHit=\(cacheHit) sheetPresented=\(sheetPresented)"
        )
    }

    static func logRegisterDirectConversation(
        conversation: DirectConversationDTO,
        registered: Bool,
        reason: String? = nil
    ) {
        if let reason {
            log(
                "registerDirectConversation registered=false conversation=\(idPrefix(conversation.id)) "
                    + "otherUserId=\(idPrefix(conversation.otherUser?.id)) reason=\(reason)"
            )
        } else {
            log(
                "registerDirectConversation registered=\(registered) conversation=\(idPrefix(conversation.id)) "
                    + "otherUserId=\(idPrefix(conversation.otherUser?.id))"
            )
        }
    }

    static func logRefreshDirectConversationsReplaced(
        serverCount: Int,
        droppedLocalOnlyCount: Int,
        previousLocalCount: Int,
        mergedLocalOnlyCount: Int
    ) {
        log(
            "refreshDirectConversations serverCount=\(serverCount) previousLocalCount=\(previousLocalCount) "
                + "droppedLocalOnly=\(droppedLocalOnlyCount) mergedLocalOnly=\(mergedLocalOnlyCount)"
        )
    }

    static func logNavigationBranch(_ branch: String, route: String, detail: String = "") {
        lastBranchDescription = branch
        if detail.isEmpty {
            log("navigation branch=\(branch) route=\(route)")
        } else {
            log("navigation branch=\(branch) route=\(route) \(detail)")
        }
    }

    static func logDirectConversationMiss(route: String, conversationID: String, appState: AppState) {
        let conv = appState.directConversation(conversationID: conversationID)
        let detail =
            "conversationId=\(idPrefix(conversationID)) cached=\(conv != nil) "
            + "hasOtherUser=\(conv?.otherUser != nil) localCount=\(appState.directConversationsByUserID.count)"
        logNavigationBranch("directConversation_miss", route: route, detail: detail)
    }

    private static func log(_ message: String) {
        OttoLog.chat.debug("[dm-nav] \(message, privacy: .public)")
    }
}

extension View {
    func dmNavBranchLogged(_ branch: String, route: String, detail: String = "") -> some View {
        onAppear {
            if detail.isEmpty {
                DMNavigationDiagnostics.logNavigationBranch(branch, route: route)
            } else {
                DMNavigationDiagnostics.logNavigationBranch(branch, route: route, detail: detail)
            }
        }
    }
}
