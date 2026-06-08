import SwiftUI

enum EventSharedWithPresentation {
    static let avatarSize: CGFloat = 52
    static let avatarOverlap: CGFloat = -12
    static let maxVisibleAvatars = 4

    static func squadInitials(for name: String) -> String {
        let parts = name.split(separator: " ")
        let letters = parts.prefix(2).compactMap(\.first)
        let joined = letters.map(String.init).joined()
        return joined.isEmpty ? "?" : joined.uppercased()
    }

    static func accentColor(squadID: String, circles: [DriveCircle]) -> Color {
        if let circle = circles.first(where: { $0.id == squadID }) {
            return circle.accentColor
        }
        return MapAccentPalette.color(fromStableSeed: squadID)
    }

    static func resolvedPhotoURL(
        for squad: EventAttachedSquadDTO,
        circles: [DriveCircle]
    ) -> String? {
        if let circle = circles.first(where: { $0.id == squad.id }),
           let photoUrl = circle.photoUrl,
           !photoUrl.isEmpty {
            return photoUrl
        }
        if let photoUrl = squad.photoUrl, !photoUrl.isEmpty {
            return photoUrl
        }
        return nil
    }

    static func memberCount(
        for squad: EventAttachedSquadDTO,
        circles: [DriveCircle]
    ) -> Int {
        circles.first(where: { $0.id == squad.id })?.members.count ?? 0
    }

    static func totalMemberCount(
        squads: [EventAttachedSquadDTO],
        circles: [DriveCircle]
    ) -> Int {
        squads.reduce(0) { partial, squad in
            partial + memberCount(for: squad, circles: circles)
        }
    }

    static func squadCountLabel(_ count: Int) -> String {
        count == 1
            ? String(localized: "event_shared_with_squad_count_one")
            : String(format: String(localized: "event_shared_with_squad_count_format"), count)
    }

    static func memberCountLabel(_ count: Int) -> String {
        count == 1
            ? String(localized: "event_shared_with_member_count_one")
            : String(format: String(localized: "event_shared_with_member_count_format"), count)
    }
}

struct EventSharedSquadAvatar: View {
    let squad: EventAttachedSquadDTO
    var photoUrl: String?
    var accentColor: Color
    var size: CGFloat = EventSharedWithPresentation.avatarSize

    var body: some View {
        ZStack {
            if let photoUrl, let url = APIConfig.imageFetchURL(from: photoUrl) {
                CachedAsyncImage(
                    url: url,
                    storageKey: resolvedImageCacheKey(sourceUrlString: photoUrl)
                ) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        placeholder
                    @unknown default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay {
            Circle()
                .strokeBorder(Color.black.opacity(0.55), lineWidth: 2)
        }
        .overlay {
            Circle()
                .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
        }
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var placeholder: some View {
        ZStack {
            Circle()
                .fill(accentColor)
            Text(EventSharedWithPresentation.squadInitials(for: squad.name))
                .font(.system(size: max(13, size * 0.30), weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
        }
    }

    private func resolvedImageCacheKey(sourceUrlString: String) -> String? {
        let canonical = sourceUrlString.split(separator: "?").first.map(String.init) ?? sourceUrlString
        return "\(squad.id)|\(canonical)"
    }
}

struct EventSharedWithAvatarStack: View {
    let squads: [EventAttachedSquadDTO]
    var circles: [DriveCircle] = []

    private var visibleSquads: [EventAttachedSquadDTO] {
        Array(squads.prefix(EventSharedWithPresentation.maxVisibleAvatars))
    }

    private var overflowCount: Int {
        max(0, squads.count - EventSharedWithPresentation.maxVisibleAvatars)
    }

    var body: some View {
        HStack(spacing: EventSharedWithPresentation.avatarOverlap) {
            ForEach(visibleSquads) { squad in
                EventSharedSquadAvatar(
                    squad: squad,
                    photoUrl: EventSharedWithPresentation.resolvedPhotoURL(for: squad, circles: circles),
                    accentColor: EventSharedWithPresentation.accentColor(squadID: squad.id, circles: circles)
                )
            }

            if overflowCount > 0 {
                overflowBadge
            }
        }
        .accessibilityElement(children: .ignore)
    }

    private var overflowBadge: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.06))
            Circle()
                .strokeBorder(
                    Color.white.opacity(0.34),
                    style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])
                )
            Text("+\(overflowCount)")
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(.white.opacity(0.88))
        }
        .frame(
            width: EventSharedWithPresentation.avatarSize,
            height: EventSharedWithPresentation.avatarSize
        )
        .overlay {
            Circle()
                .strokeBorder(Color.black.opacity(0.55), lineWidth: 2)
        }
    }
}

struct EventSharedWithModule: View {
    let squads: [EventAttachedSquadDTO]
    var circles: [DriveCircle] = []
    var onTap: () -> Void

    private var totalMembers: Int {
        EventSharedWithPresentation.totalMemberCount(squads: squads, circles: circles)
    }

    private var accessibilitySummary: String {
        var parts = [EventSharedWithPresentation.squadCountLabel(squads.count)]
        if totalMembers > 0 {
            parts.append(EventSharedWithPresentation.memberCountLabel(totalMembers))
        }
        return parts.joined(separator: ", ")
    }

    var body: some View {
        VStack(spacing: 0) {
            moduleDivider

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "person.2.fill")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.purple)
                    Text(String(localized: "event_added_to_squads_section_label"))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white.opacity(0.55))
                }

                Button(action: onTap) {
                    HStack(spacing: 14) {
                        EventSharedWithAvatarStack(squads: squads, circles: circles)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(EventSharedWithPresentation.squadCountLabel(squads.count))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                            if totalMembers > 0 {
                                Text(EventSharedWithPresentation.memberCountLabel(totalMembers))
                                    .font(.caption)
                                    .foregroundStyle(.white.opacity(0.55))
                            }
                        }

                        Spacer(minLength: 8)

                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.38))
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "event_shared_with_accessibility_label"))
                .accessibilityValue(accessibilitySummary)
                .accessibilityHint(String(localized: "event_shared_with_accessibility_hint"))
            }
            .padding(.vertical, 12)

            moduleDivider
        }
    }

    private var moduleDivider: some View {
        Rectangle()
            .fill(Color.white.opacity(0.08))
            .frame(height: 1)
    }
}

struct EventSharedWithSheet: View {
    @Environment(\.dismiss) private var dismiss
    let squads: [EventAttachedSquadDTO]
    var circles: [DriveCircle] = []

    private var sortedSquads: [EventAttachedSquadDTO] {
        squads.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(String(localized: "event_shared_with_sheet_subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.62))
                        .padding(.horizontal, 18)
                        .padding(.top, 4)
                        .padding(.bottom, 14)

                    LazyVStack(spacing: 0) {
                        ForEach(sortedSquads) { squad in
                            sharedSquadRow(squad)
                            if squad.id != sortedSquads.last?.id {
                                Rectangle()
                                    .fill(Color.white.opacity(0.08))
                                    .frame(height: 1)
                                    .padding(.leading, 78)
                            }
                        }
                    }
                }
            }
            .background(Color.black)
            .navigationTitle(String(localized: "event_shared_with_sheet_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                }
            }
        }
        .preferredColorScheme(.dark)
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func sharedSquadRow(_ squad: EventAttachedSquadDTO) -> some View {
        let memberCount = EventSharedWithPresentation.memberCount(for: squad, circles: circles)
        SquadShareListRow(
            name: squad.name,
            photoUrl: EventSharedWithPresentation.resolvedPhotoURL(for: squad, circles: circles),
            memberCount: memberCount,
            avatarSize: 48,
            cacheStorageKey: squad.id
        )
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
    }
}
