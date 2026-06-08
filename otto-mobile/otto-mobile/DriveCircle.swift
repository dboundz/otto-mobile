import Foundation
import SwiftUI

struct DriveCircle: Identifiable {
    let id: String
    let name: String
    let subtitle: String
    let icon: String
    let accentColor: Color
    let ownerId: String
    let photoUrl: String?
    var members: [FriendLocation]
}
