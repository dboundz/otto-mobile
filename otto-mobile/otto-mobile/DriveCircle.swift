import Foundation
import SwiftUI

struct SquadPermissions: Codable, Equatable {
    var membersCanEditSettings: Bool
    var membersCanSendMessages: Bool
    var membersCanAddMembers: Bool
    var membersCanInviteViaLink: Bool
    var membersCanShareDriveLocation: Bool

    static let `default` = SquadPermissions(
        membersCanEditSettings: true,
        membersCanSendMessages: true,
        membersCanAddMembers: true,
        membersCanInviteViaLink: true,
        membersCanShareDriveLocation: true
    )

    enum CodingKeys: String, CodingKey {
        case membersCanEditSettings
        case membersCanSendMessages
        case membersCanAddMembers
        case membersCanInviteViaLink
        case membersCanShareDriveLocation
    }

    init(
        membersCanEditSettings: Bool = true,
        membersCanSendMessages: Bool = true,
        membersCanAddMembers: Bool = true,
        membersCanInviteViaLink: Bool = true,
        membersCanShareDriveLocation: Bool = true
    ) {
        self.membersCanEditSettings = membersCanEditSettings
        self.membersCanSendMessages = membersCanSendMessages
        self.membersCanAddMembers = membersCanAddMembers
        self.membersCanInviteViaLink = membersCanInviteViaLink
        self.membersCanShareDriveLocation = membersCanShareDriveLocation
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        membersCanEditSettings = try c.decodeIfPresent(Bool.self, forKey: .membersCanEditSettings) ?? true
        membersCanSendMessages = try c.decodeIfPresent(Bool.self, forKey: .membersCanSendMessages) ?? true
        membersCanAddMembers = try c.decodeIfPresent(Bool.self, forKey: .membersCanAddMembers) ?? true
        membersCanInviteViaLink = try c.decodeIfPresent(Bool.self, forKey: .membersCanInviteViaLink) ?? true
        membersCanShareDriveLocation = try c.decodeIfPresent(Bool.self, forKey: .membersCanShareDriveLocation) ?? true
    }
}

enum SquadPermissionAction {
    case editSettings
    case sendMessages
    case addMembers
    case inviteViaLink
    case shareDriveLocation
}

enum SquadPermissionResolver {
    static func role(for userId: String, in circle: DriveCircle) -> String {
        if circle.ownerId == userId { return "owner" }
        let raw = circle.members.first(where: { $0.id == userId })?.clubRole.lowercased() ?? "member"
        if raw == "owner" { return "owner" }
        if raw == "admin" { return "admin" }
        return "member"
    }

    static func isAdminOrOwner(_ userId: String, in circle: DriveCircle) -> Bool {
        let role = role(for: userId, in: circle)
        return role == "owner" || role == "admin"
    }

    static func canPerform(_ action: SquadPermissionAction, in circle: DriveCircle, userId: String) -> Bool {
        guard !userId.isEmpty else { return false }
        if isAdminOrOwner(userId, in: circle) { return true }
        switch action {
        case .editSettings:
            return circle.permissions.membersCanEditSettings
        case .sendMessages:
            return circle.permissions.membersCanSendMessages
        case .addMembers:
            return circle.permissions.membersCanAddMembers
        case .inviteViaLink:
            return circle.permissions.membersCanInviteViaLink
        case .shareDriveLocation:
            return circle.permissions.membersCanShareDriveLocation
        }
    }
}

struct DriveCircle: Identifiable {
    let id: String
    let name: String
    let subtitle: String
    let icon: String
    let accentColor: Color
    let ownerId: String
    let photoUrl: String?
    let permissions: SquadPermissions
    var members: [FriendLocation]
}
