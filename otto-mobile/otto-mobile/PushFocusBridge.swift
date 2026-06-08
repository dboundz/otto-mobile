import Foundation

/// Mirrored from `AppState` active chat scope so `AppDelegate` can suppress Level-3 push UI/sound
/// while the user is reading that exact thread (APNs still arrive in the foreground).
enum PushFocusBridge {
    static var activeChatCircleId: String?
    static var activeDirectConversationId: String?
}
