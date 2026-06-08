import CoreLocation
import SwiftUI
import UIKit

/// Single entry point for map-driven preview sheets (people, clusters, saved places, events).
enum MapPreviewSession: Identifiable {
    case savedPlace(SavedPlaceDTO)
    case clusterPick(UUID, [FriendLocation])
    case upcomingEvent(primary: EventDTO, siblings: [EventDTO] = [])
    case raceTrack(RaceTrackRecord)

    var id: String {
        switch self {
        case .savedPlace(let p):
            return "place:\(p.id)"
        case .clusterPick(let uuid, let members):
            return "cluster:\(uuid.uuidString):\(members.map(\.id).sorted().joined(separator: ","))"
        case .upcomingEvent(let primary, let siblings):
            let siblingIDs = siblings.map(\.id).sorted().joined(separator: ",")
            return "event:\(primary.id):\(siblingIDs)"
        case .raceTrack(let track):
            return "racetrack:\(track.id)"
        }
    }
}

struct PresentedPeerProfileFocus: Identifiable {
    let id: String
}

// MARK: - User (mini profile)

private enum MapPersonSheetStyle {
    /// Active / live accent similar to the product mock (~#2ECC71).
    static let liveGreen = Color(red: 0.11, green: 0.73, blue: 0.45)
    static let sheetBackground = Color(red: 0.11, green: 0.11, blue: 0.12)
    static let cardBackground = Color.white.opacity(0.09)
}

struct MapUserPreviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    let friend: FriendLocation
    let lastUpdateCompact: String
    let distanceCardValue: String?
    let sharedCircles: [DriveCircle]

    @State private var isPresentingFullProfile = false

    private var isSelf: Bool { friend.id == appState.currentUserID }
    private var canDirectMessage: Bool { appState.canDirectMessage(userID: friend.id) }

    private var activityHeadline: String {
        switch friend.movementMode {
        case .driving: return "Driving"
        case .walking: return "Walking"
        case .unknown: return friend.isActive ? "Live" : friend.statusLabel
        }
    }

    private var directionsURL: URL {
        let lat = friend.coordinate.latitude
        let lon = friend.coordinate.longitude
        return URL(string: "http://maps.apple.com/?daddr=\(lat),\(lon)&dirflg=d")!
    }

    var body: some View {
        MemberProfileActionSheet(friend: friend, sharedCircles: sharedCircles)
    }

    private var profileAvatar: some View {
        AvatarView(
            name: friend.name,
            avatarUrl: friend.avatarUrl,
            size: 72,
            accentColor: friend.accentColor,
            accentRingWidth: 2,
            whiteRingWidth: 1
        )
    }

    private func statCard(icon: String, value: String, caption: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(MapPersonSheetStyle.liveGreen)
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .minimumScaleFactor(0.75)
                .multilineTextAlignment(.center)
            Text(caption)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .frame(maxWidth: .infinity, minHeight: 102)
        .padding(.vertical, 10)
        .padding(.horizontal, 2)
        .background(MapPersonSheetStyle.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var speedValue: String {
        guard shouldShowMph else { return "—" }
        return "\(friend.speedMph) mph"
    }

    private var shouldShowMph: Bool {
        friend.movementMode == .driving && friend.isActive && friend.speedMph >= 8
    }
}

/// Optional squad-roster context when opening someone’s sheet from the active squad detail UI.
struct SquadMemberProfileContext: Equatable {
    let circleId: String
    /// `owner`, `admin`, or `member`.
    let viewerRoleLowercased: String
    /// `owner`, `admin`, or `member` for [friend].
    let targetRoleLowercased: String

    /// Approximate extra sheet height for roster-management rows (`MemberProfileActionSheet`), for detent sizing.
    func estimatedManagementChromeExtraPoints() -> CGFloat {
        let viewer = viewerRoleLowercased
        let target = targetRoleLowercased
        let promote = viewer == "owner" && target == "member"
        let demote = viewer == "owner" && target == "admin"
        let removeVisible: Bool = {
            guard target != "owner" else { return false }
            if viewer == "admin", target == "admin" { return false }
            return viewer == "owner" || viewer == "admin"
        }()
        var total: CGFloat = 28
        var segments = 0
        if promote {
            total += 82
            segments += 1
        }
        if demote {
            total += 82
            segments += 1
        }
        if removeVisible {
            total += 104
            segments += 1
        }
        guard segments > 0 else { return 0 }
        total += CGFloat(max(0, segments - 1)) * 12
        return total
    }
}

struct MemberProfileActionSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    let friend: FriendLocation
    let sharedCircles: [DriveCircle]
    /// When non-nil (opened from squad detail), admins/owners can manage roster via PATCH/DELETE + realtime roster refresh.
    var squadContext: SquadMemberProfileContext? = nil

    @State private var isPresentingFullProfile = false
    @State private var squadActionsBusy = false
    @State private var confirmRemoveFromSquad = false
    @State private var confirmPromoteToAdmin = false
    @State private var confirmDemoteAdmin = false

    private var isSelf: Bool { friend.id == appState.currentUserID }
    private var canDirectMessage: Bool { appState.canDirectMessage(userID: friend.id) }

    private var showPromoteToAdmin: Bool {
        guard let c = squadContext else { return false }
        return c.viewerRoleLowercased == "owner" && c.targetRoleLowercased == "member"
    }

    private var showDemoteAdmin: Bool {
        guard let c = squadContext else { return false }
        return c.viewerRoleLowercased == "owner" && c.targetRoleLowercased == "admin"
    }

    private var showRemoveFromSquad: Bool {
        guard let c = squadContext else { return false }
        let viewer = c.viewerRoleLowercased
        let target = c.targetRoleLowercased
        guard target != "owner" else { return false }
        if viewer == "admin", target == "admin" { return false }
        return viewer == "owner" || viewer == "admin"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Capsule()
                        .fill(Color.white.opacity(0.35))
                        .frame(width: 36, height: 4)
                        .padding(.top, 8)

                    VStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .stroke(friend.accentColor, lineWidth: 2)
                                .frame(width: 90, height: 90)
                            Circle()
                                .stroke(Color.white.opacity(0.84), lineWidth: 1)
                                .frame(width: 86, height: 86)
                            AvatarView(
                                name: friend.name,
                                avatarUrl: friend.avatarUrl,
                                size: 80,
                                accentColor: friend.accentColor,
                                accentRingWidth: 0,
                                whiteRingWidth: 0
                            )
                        }
                        .shadow(color: friend.accentColor.opacity(0.35), radius: 10, y: 2)
                        .overlay(alignment: .bottomTrailing) {
                            Circle()
                                .fill(friend.presenceStatus.color)
                                .frame(width: 16, height: 16)
                                .overlay(Circle().stroke(Color.black, lineWidth: 2))
                                .offset(x: -5, y: -5)
                        }

                        VStack(spacing: 3) {
                            Text(friend.name)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.white)
                            if let movementSummary {
                                Text(movementSummary)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.purple)
                            }
                        }
                    }
                    .padding(.top, 14)
                    .frame(height: 146)

                    if !sharedCircles.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Member of")
                                .font(.caption2.weight(.bold))
                                .textCase(.uppercase)
                                .foregroundStyle(.white.opacity(0.45))
                                .padding(.horizontal, 16)

                            VStack(spacing: 6) {
                                ForEach(sharedCircles) { circle in
                                    Button {
                                        dismiss()
                                        appState.requestCircleFocus(circleID: circle.id)
                                    } label: {
                                        MemberProfileSquadRow(circle: circle)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 14)
                        }
                        .padding(.top, 8)
                        .padding(.bottom, 12)
                    }

                    HStack(spacing: 10) {
                        actionTile(
                            title: "Message",
                            systemImage: "bubble.left.fill",
                            isEnabled: canDirectMessage
                        ) {
                            dismiss()
                            appState.requestDirectMessageFocus(userID: friend.id)
                        }

                        actionTile(
                            title: "View Profile",
                            systemImage: "person.fill",
                            isEnabled: true
                        ) {
                            isPresentingFullProfile = true
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 4)

                    if showPromoteToAdmin || showDemoteAdmin || showRemoveFromSquad {
                        VStack(alignment: .leading, spacing: 12) {
                            if showPromoteToAdmin {
                                squadSecondaryManageButton(
                                    title: "Make squad admin",
                                    systemImage: "shield.lefthalf.fill",
                                    busy: squadActionsBusy
                                ) {
                                    confirmPromoteToAdmin = true
                                }
                            }

                            if showDemoteAdmin {
                                squadSecondaryManageButton(
                                    title: "Remove admin privileges",
                                    systemImage: "shield.slash",
                                    busy: squadActionsBusy
                                ) {
                                    confirmDemoteAdmin = true
                                }
                            }

                            if showRemoveFromSquad {
                                removeFromSquadDangerButton(enabled: !squadActionsBusy)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.bottom, 22)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .top)
            }
            .scrollIndicators(.visible)
            .background(Color(red: 0.025, green: 0.025, blue: 0.035).ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $isPresentingFullProfile) {
                ProfileScreen(
                    profileUserID: isSelf ? nil : friend.id,
                    onUserBlocked: { dismiss() },
                    onOpenOwnGarage: isSelf
                        ? {
                            isPresentingFullProfile = false
                            DispatchQueue.main.async {
                                dismiss()
                                appState.requestGarageTabFocus()
                            }
                        }
                        : nil
                )
                    .environmentObject(appState)
                    .presentationDetents([.large])
            }
            .alert("Remove from squad?", isPresented: $confirmRemoveFromSquad) {
                Button("Cancel", role: .cancel) {}
                Button("Remove", role: .destructive) {
                    Task {
                        guard let squadContext else { return }
                        squadActionsBusy = true
                        defer { squadActionsBusy = false }
                        await appState.removeMember(from: squadContext.circleId, userID: friend.id)
                        await MainActor.run { dismiss() }
                    }
                }
            } message: {
                Text("They’ll lose access to this squad’s chat, events, grid, and members.")
            }
            .alert("Make squad admin?", isPresented: $confirmPromoteToAdmin) {
                Button("Cancel", role: .cancel) {}
                Button("Make admin") {
                    Task {
                        guard let squadContext else { return }
                        squadActionsBusy = true
                        defer { squadActionsBusy = false }
                        await appState.setCircleMemberRole(
                            circleID: squadContext.circleId,
                            userID: friend.id,
                            role: "admin"
                        )
                        await MainActor.run { dismiss() }
                    }
                }
            } message: {
                Text(
                    "\(friend.name) will be able to manage members and squad settings as an admin."
                )
            }
            .alert("Remove admin privileges?", isPresented: $confirmDemoteAdmin) {
                Button("Cancel", role: .cancel) {}
                Button("Remove admin", role: .destructive) {
                    Task {
                        guard let squadContext else { return }
                        squadActionsBusy = true
                        defer { squadActionsBusy = false }
                        await appState.setCircleMemberRole(
                            circleID: squadContext.circleId,
                            userID: friend.id,
                            role: "member"
                        )
                        await MainActor.run { dismiss() }
                    }
                }
            } message: {
                Text("\(friend.name) will become a regular member and lose admin abilities for this squad.")
            }
        }
    }

    private func squadSecondaryManageButton(
        title: String,
        systemImage: String,
        busy: Bool,
        action: @escaping () -> Void,
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: ceil(20 * 1.2), weight: .semibold))
                    .foregroundStyle(Color.purple.opacity(0.92))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                if busy {
                    ProgressView().tint(.white.opacity(0.85))
                } else {
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.38))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 17)
            .frame(minHeight: 52 * 1.2)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(busy)
        .opacity(busy ? 0.55 : 1)
    }

    private func removeFromSquadDangerButton(enabled: Bool) -> some View {
        Button {
            confirmRemoveFromSquad = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: ceil(18 * 1.2), weight: .semibold))
                    .foregroundStyle(Color.red.opacity(0.92))
                VStack(alignment: .leading, spacing: 5) {
                    Text("Remove from Squad")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.red.opacity(0.95))
                    Text("They’ll lose access to this squad’s chat, events, grid, and members.")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.48))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.red.opacity(0.42))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 17)
            .frame(minHeight: 72 * 1.2)
            .background(Color.red.opacity(0.07))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.red.opacity(0.22), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.55)
    }

    @ViewBuilder
    private func actionTile(
        title: String,
        systemImage: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 9) {
                Image(systemName: systemImage)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(isEnabled ? Color.purple : Color.white.opacity(0.25))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isEnabled ? Color.purple : Color.white.opacity(0.35))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 78)
            .background(Color.white.opacity(0.055))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }

    private func actionBackground(isEnabled: Bool) -> some View {
        RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(Color.white.opacity(isEnabled ? 0.07 : 0.04))
    }

    private var movementSummary: String? {
        guard friend.isActive else { return nil }
        let label: String
        switch friend.movementMode {
        case .driving:
            label = "On route"
        case .walking:
            label = "Walking"
        case .unknown:
            label = "Sharing location"
        }
        guard friend.speedMph > 0 else { return label }
        return "\(label) · \(friend.speedMph) mph"
    }
}

private struct MemberProfileSquadRow: View {
    let circle: DriveCircle

    var body: some View {
        HStack(spacing: 12) {
            SquadAvatarView(name: circle.name, imageUrl: circle.photoUrl, icon: circle.icon, size: 42, cacheStorageKey: "squadAvatar:\(circle.id)")

            VStack(alignment: .leading, spacing: 3) {
                Text(circle.name)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(circle.subtitle)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        }
    }
}

// MARK: - Cluster picker

struct MapFriendClusterPickSheet: View {
    @Environment(\.dismiss) private var dismiss

    let members: [FriendLocation]
    let updateLabelsByFriendID: [String: String]
    let onPick: (FriendLocation) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    Text("Several people are close together. Pick one to see details.")
                        .font(.subheadline)
                        .foregroundStyle(Color.white.opacity(0.72))

                    MapPeopleListCard(
                        friends: members,
                        updateLabelsByFriendID: updateLabelsByFriendID,
                        onSelect: onPick
                    )
                }
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, OttoScreenChrome.bottomPadding)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("People here")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { dismiss() }
                        .foregroundStyle(.white)
                }
            }
        }
    }
}

// MARK: - Shared people list

struct MapPeopleListCard: View {
    let friends: [FriendLocation]
    let updateLabelsByFriendID: [String: String]
    let onSelect: (FriendLocation) -> Void

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(Array(friends.enumerated()), id: \.element.id) { index, friend in
                Button {
                    onSelect(friend)
                } label: {
                    MapSharingPersonRow(
                        friend: friend,
                        updateText: updateLabelsByFriendID[friend.id] ?? "Here just now"
                    )
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())

                if index < friends.count - 1 {
                    Divider().overlay(Color.white.opacity(0.08))
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }
}

struct MapSharingPersonRow: View {
    let friend: FriendLocation
    let updateText: String

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(
                name: friend.name,
                avatarUrl: friend.avatarUrl,
                size: 44,
                accentColor: friend.accentColor,
                accentRingWidth: 1.25,
                whiteRingWidth: 0
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(friend.presenceStatus.color)
                    .frame(width: 10, height: 10)
                    .overlay {
                        Circle().stroke(.black, lineWidth: 2)
                    }
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(friend.name)
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(updateText)
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.64))
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}
