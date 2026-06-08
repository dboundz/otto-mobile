import SwiftUI

/// Squad row for share/map/destination pickers: avatar, name, and member count.
struct SquadShareListRow<Trailing: View>: View {
    let name: String
    var photoUrl: String?
    var icon: String = "person.3.fill"
    var memberCount: Int
    var avatarSize: CGFloat = 48
    var cacheStorageKey: String?
    @ViewBuilder var trailing: () -> Trailing

    init(
        name: String,
        photoUrl: String? = nil,
        icon: String = "person.3.fill",
        memberCount: Int,
        avatarSize: CGFloat = 48,
        cacheStorageKey: String? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }
    ) {
        self.name = name
        self.photoUrl = photoUrl
        self.icon = icon
        self.memberCount = memberCount
        self.avatarSize = avatarSize
        self.cacheStorageKey = cacheStorageKey
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: 12) {
            SquadAvatarView(
                name: name,
                imageUrl: photoUrl,
                icon: icon,
                size: avatarSize,
                cacheStorageKey: cacheStorageKey
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if memberCount > 0 {
                    Text(memberCountLine)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.62))
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)

            trailing()
        }
    }

    private var memberCountLine: String {
        memberCount == 1 ? "1 member" : "\(memberCount) members"
    }
}

extension SquadShareListRow where Trailing == EmptyView {
    init(
        name: String,
        photoUrl: String? = nil,
        icon: String = "person.3.fill",
        memberCount: Int,
        avatarSize: CGFloat = 48,
        cacheStorageKey: String? = nil
    ) {
        self.init(
            name: name,
            photoUrl: photoUrl,
            icon: icon,
            memberCount: memberCount,
            avatarSize: avatarSize,
            cacheStorageKey: cacheStorageKey,
            trailing: { EmptyView() }
        )
    }
}
