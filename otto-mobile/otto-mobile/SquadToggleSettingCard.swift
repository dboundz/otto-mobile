import SwiftUI

/// Card-styled toggle for squad rows in map layers and event admin flows.
struct SquadToggleSettingCard: View {
    let circle: DriveCircle
    @Binding var isOn: Bool
    var trailingText: String?
    var subtitle: String?
    var enabled: Bool = true
    var onChange: ((Bool) -> Void)?

    var body: some View {
        mapSquadToggleRow(
            name: circle.name,
            photoUrl: circle.photoUrl,
            icon: circle.icon,
            cacheStorageKey: "squadAvatar:\(circle.id)",
            subtitle: displaySubtitle
        )
    }

    private var displaySubtitle: String {
        if let subtitle { return subtitle }
        let count = circle.members.count
        return count == 1 ? "1 member" : "\(count) members"
    }

    @ViewBuilder
    private func mapSquadToggleRow(
        name: String,
        photoUrl: String?,
        icon: String,
        cacheStorageKey: String,
        subtitle: String
    ) -> some View {
        HStack(spacing: 12) {
            SquadAvatarView(
                name: name,
                imageUrl: photoUrl,
                icon: icon,
                size: 48,
                cacheStorageKey: cacheStorageKey
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.65))
                    .lineLimit(1)
            }

            Spacer()

            if let trailingText, !trailingText.isEmpty {
                Text(trailingText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(.purple)
                .disabled(!enabled)
                .onChange(of: isOn) { _, newValue in
                    onChange?(newValue)
                }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        }
        .opacity(enabled ? 1 : 0.6)
    }
}

/// Toggle card when only admin-squad fields are available (resolve member count from full circles).
struct SquadToggleSettingCardResolved: View {
    let squadId: String
    let name: String
    var photoUrl: String?
    var icon: String = "person.3.fill"
    var memberCount: Int
    @Binding var isOn: Bool
    var subtitle: String?
    var trailingText: String?
    var enabled: Bool = true

    var body: some View {
        HStack(spacing: 12) {
            SquadAvatarView(
                name: name,
                imageUrl: photoUrl,
                icon: icon,
                size: 48,
                cacheStorageKey: "squadAvatar:\(squadId)"
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(displaySubtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.65))
                    .lineLimit(1)
            }

            Spacer()

            if let trailingText, !trailingText.isEmpty {
                Text(trailingText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(.purple)
                .disabled(!enabled)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        }
        .opacity(enabled ? 1 : 0.6)
    }

    private var displaySubtitle: String {
        if let subtitle { return subtitle }
        return memberCount == 1 ? "1 member" : "\(memberCount) members"
    }
}
