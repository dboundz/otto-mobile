import CoreLocation
import Foundation
import SwiftUI

/// Reserved sentinel “user” for `@all` in squad chat. Must match `CIRCLE_CHAT_ALL_MENTION_USER_ID` in `otto-backend`.
enum SquadChatAllMention {
    static let userId = "0000000000000000000000a1"

    /// Lowercase after `@`; must match backend validation for the sentinel id.
    static let defaultWireLabel = "all"

    /// Shown in the @-mention list; inserts `@all ` into the composer (lowercase wire text).
    static func pickerMember() -> FriendLocation {
        FriendLocation(
            id: userId,
            name: "All",
            avatarName: "All",
            avatarUrl: nil,
            car: "",
            clubRole: "",
            lastRun: "",
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: .yellow,
            movementMode: .unknown
        )
    }
}
