import SwiftUI

extension DriveCircle {
    var mapActiveSharingMemberCount: Int {
        members.filter(\.isActive).count
    }

    var mapSharingStatusSubtitle: String {
        let activeCount = mapActiveSharingMemberCount
        if activeCount > 0 {
            return activeCount == 1 ? "1 person sharing" : "\(activeCount) people sharing"
        }
        return "No one sharing in this squad right now"
    }
}

extension Array where Element == DriveCircle {
    func sortedForMapSquadList() -> [DriveCircle] {
        sorted { lhs, rhs in
            let lhsCount = lhs.mapActiveSharingMemberCount
            let rhsCount = rhs.mapActiveSharingMemberCount
            if lhsCount != rhsCount { return lhsCount > rhsCount }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }
}

/// Shared squad row used in **Squads** and **Find on Map** so layout stays consistent.
struct CircleRowCard: View {
    let circle: DriveCircle
    var unreadCount = 0
    /// When set, replaces `circle.subtitle` (e.g. map-specific sharing counts).
    var subtitleOverride: String? = nil
    /// When true, shows a tracking check instead of the trailing chevron (map follow mode).
    var isTrackedOnMap = false

    var body: some View {
        HStack(spacing: 12) {
            SquadAvatarView(
                name: circle.name,
                imageUrl: circle.photoUrl,
                icon: circle.icon,
                size: 48,
                cacheStorageKey: "squadAvatar:\(circle.id)"
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(circle.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(subtitleOverride ?? memberCountLine)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.65))
                    .lineLimit(1)
            }

            Spacer()

            AvatarStack(members: circle.members)

            UnreadCountBadge(count: unreadCount)

            if isTrackedOnMap {
                Image(systemName: "checkmark.circle.fill")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.green)
                    .shadow(color: .green.opacity(0.35), radius: 4)
            } else {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.45))
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
    }

    private var memberCountLine: String {
        "\(circle.members.count) \(circle.members.count == 1 ? "member" : "members")"
    }
}

struct UnreadDot: View {
    let isVisible: Bool

    var body: some View {
        Circle()
            .fill(isVisible ? Color.green : Color.clear)
            .frame(width: 9, height: 9)
    }
}

struct UnreadCountBadge: View {
    let count: Int
    var cap: Int = 99

    var body: some View {
        if count > 0 {
            Text(count > cap ? "\(cap)+" : "\(count)")
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, count > 9 ? 6 : 5)
                .padding(.vertical, 3)
                .background(Color.green)
                .clipShape(Capsule())
        }
    }
}

/// Overlapping member avatars used in squad list rows (`CircleRowCard`).
struct AvatarStack: View {
    let members: [FriendLocation]

    var body: some View {
        HStack(spacing: -10) {
            ForEach(Array(members.prefix(4).enumerated()), id: \.element.id) { _, member in
                AvatarView(
                    name: member.name,
                    avatarUrl: member.avatarUrl,
                    size: 26,
                    accentColor: member.accentColor,
                    accentRingWidth: 1.5,
                    whiteRingWidth: 0
                )
                .overlay {
                    Circle().stroke(.background, lineWidth: 2)
                }
            }
        }
    }
}

struct CreateSquadListRow: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: "plus")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.purple)
                    .frame(width: 44, height: 44)
                    .background(Color.purple.opacity(0.18))
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text("Add Squad")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Text("Start a new crew")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.58))
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.35))
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add Squad")
    }
}
