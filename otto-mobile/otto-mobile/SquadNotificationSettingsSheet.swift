import SwiftUI

/// Squad settings: role-aware (owner vs member), notifications, and leave squad (API-enforced).
struct SquadNotificationSettingsSheet: View {
    let circleId: String
    /// Subtitle under squad name (e.g. member counts); matches squad detail header.
    let memberSubtitle: String
    var onSuccessfullyLeftSquad: () -> Void = {}
    var onAddMember: () -> Void = {}
    var onMemberProfile: (FriendLocation) -> Void = { _ in }

    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var newMessagesChoice: SquadNotificationMuteChoice = .off
    @State private var mentionsChoice: SquadNotificationMuteChoice = .off

    @State private var showRenameSheet = false
    @State private var renameDraft = ""
    @State private var renameBusy = false

    @State private var confirmLeave = false
    @State private var leaveBusy = false

    @State private var showOwnershipTransferBlock = false

    private var circle: DriveCircle? {
        appState.circles.first(where: { $0.id == circleId })
    }

    private var displayName: String {
        circle?.name ?? "Squad"
    }

    private var isOwner: Bool {
        guard let circle else { return false }
        return circle.ownerId == appState.currentUserID
    }

    private var otherMemberCount: Int {
        guard let circle else { return 0 }
        return circle.members.filter { $0.id != appState.currentUserID }.count
    }

    private var circleMembers: [FriendLocation] {
        appState.circles.first(where: { $0.id == circleId })?.members ?? []
    }

    private var groupedMembers: (sharing: [FriendLocation], online: [FriendLocation], offline: [FriendLocation]) {
        let sortedMembers = circleMembers.sorted { lhs, rhs in
            func rank(_ m: FriendLocation) -> Int {
                switch m.clubRole.lowercased() {
                case "owner": return 0
                case "admin": return 1
                default: return 2
                }
            }
            let lr = rank(lhs)
            let rr = rank(rhs)
            if lr != rr { return lr < rr }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
        let sharing = sortedMembers.filter(\.isActive)
        let online = sortedMembers.filter { !$0.isActive && $0.isOnline }
        let offline = sortedMembers.filter { !$0.isActive && !$0.isOnline }
        return (sharing, online, offline)
    }

    private var memberSubtitleText: Text {
        guard circle != nil else {
            return Text(memberSubtitle)
                .foregroundStyle(.white.opacity(0.58))
        }
        let memberCount = circleMembers.count
        let onlineCount = circleMembers.filter { $0.isOnline || $0.isActive }.count
        let sharingCount = circleMembers.filter(\.isActive).count
        let memberLabel = memberCount == 1 ? "member" : "members"
        var base = "\(memberCount) \(memberLabel)"
        if onlineCount > 0 {
            base += " · \(onlineCount) online"
        }
        var subtitle = AttributedString(base)
        subtitle.foregroundColor = .white.opacity(0.58)
        guard sharingCount > 0 else { return Text(subtitle) }

        var separator = AttributedString(" · ")
        separator.foregroundColor = .white.opacity(0.58)
        var sharing = AttributedString("\(sharingCount) sharing")
        sharing.foregroundColor = .green
        subtitle.append(separator)
        subtitle.append(sharing)
        return Text(subtitle)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                SettingsSheetChrome.settingsBackgroundGradient
                    .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        squadHeaderRow
                            .padding(.bottom, 22)

                        if isOwner {
                            sectionTitle("Squad details")
                            squadNameRow
                                .padding(.bottom, 22)
                        }

                        sectionTitle("Notifications")
                        notificationsCard
                            .padding(.bottom, 28)

                        membersSection
                            .padding(.bottom, 28)

                        sectionTitle("Danger zone")
                            .foregroundStyle(Color.red.opacity(0.92))

                        leaveSquadRow
                            .padding(.top, 8)
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 10)
                    .padding(.bottom, 28)
                }
            }
            .navigationTitle("Squad settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(.white)
                }
            }
        }
        .onAppear {
            reloadMutes()
            renameDraft = displayName
            Task { await appState.refreshPresence(for: circleId) }
        }
        .sheet(isPresented: $showRenameSheet) {
            renameSheet
        }
        .alert("Leave this squad?", isPresented: $confirmLeave) {
            Button("Cancel", role: .cancel) {}
            Button("Leave", role: .destructive) {
                Task { await performLeave() }
            }
        } message: {
            Text("You’ll lose access to this squad’s chat, events, grid, and members.")
        }
        .alert("Cannot leave squad", isPresented: $showOwnershipTransferBlock) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("You must transfer ownership before leaving the squad.")
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption.weight(.semibold))
            .tracking(0.6)
            .foregroundStyle(.white.opacity(0.45))
            .padding(.bottom, 10)
    }

    private var squadHeaderRow: some View {
        HStack(alignment: .center, spacing: 14) {
            SquadAvatarView(
                name: displayName,
                imageUrl: circle?.photoUrl,
                icon: circle?.icon ?? "person.3.fill",
                size: 56,
                cacheStorageKey: "squadAvatar:\(circleId)"
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                memberSubtitleText
                    .font(.caption)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
    }

    private var squadNameRow: some View {
        Button {
            renameDraft = displayName
            showRenameSheet = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "pencil.circle.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(Color.purple.opacity(0.92))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Squad name")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(displayName)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.38))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(renameBusy || leaveBusy)
    }

    private var membersSection: some View {
        let grouped = groupedMembers
        return VStack(alignment: .leading, spacing: 0) {
            sectionTitle("Members")

            Button(action: onAddMember) {
                Label("Add Member", systemImage: "person.badge.plus")
                    .font(.body)
                    .foregroundStyle(Color.blue)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 18)
                    .background(Color.white.opacity(0.11))
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.bottom, 14)

            if !grouped.sharing.isEmpty {
                Text("Sharing")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.72))
                    .padding(.bottom, 8)
                VStack(spacing: 8) {
                    ForEach(grouped.sharing) { member in
                        settingsMemberRow(member)
                    }
                }
                Spacer().frame(height: 14)
            }

            Text("Online")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
                .padding(.bottom, 8)
            if grouped.online.isEmpty {
                Text("No one is online right now.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.5))
                    .padding(.vertical, 6)
            } else {
                VStack(spacing: 8) {
                    ForEach(grouped.online) { member in
                        settingsMemberRow(member)
                    }
                }
            }

            Spacer().frame(height: 14)

            Text("Offline")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
                .padding(.bottom, 8)
            if grouped.offline.isEmpty {
                Text("No offline members.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.5))
                    .padding(.vertical, 6)
            } else {
                VStack(spacing: 8) {
                    ForEach(grouped.offline) { member in
                        settingsMemberRow(member)
                    }
                }
            }
        }
    }

    private func settingsMemberRow(_ member: FriendLocation) -> some View {
        HStack(spacing: 12) {
            AvatarView(
                name: member.name,
                avatarUrl: member.avatarUrl,
                size: 42,
                accentColor: member.accentColor,
                accentRingWidth: 2,
                whiteRingWidth: 0
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(member.presenceStatus.color)
                    .frame(width: 11, height: 11)
                    .overlay {
                        Circle().stroke(.white, lineWidth: 2)
                    }
            }

            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .center, spacing: 8) {
                    Text(member.name)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                    if let badge = squadRoleBadge(for: member) {
                        Text(badge)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.white.opacity(0.92))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }
                Text(lastUpdatedText(for: member))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.55))
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture {
            onMemberProfile(member)
        }
    }

    private func squadRoleBadge(for member: FriendLocation) -> String? {
        switch member.clubRole.lowercased() {
        case "owner": return "Owner"
        case "admin": return "Admin"
        default: return nil
        }
    }

    private func lastUpdatedText(for member: FriendLocation) -> String {
        guard let lastUpdatedAt = member.lastUpdatedAt else {
            return member.isOnline || member.isActive ? "Updated just now" : "No recent update"
        }
        let seconds = max(0, Int(Date().timeIntervalSince(lastUpdatedAt)))
        if seconds < 5 { return "Updated just now" }
        if seconds < 60 { return "Updated \(seconds)s ago" }
        let minutes = seconds / 60
        if minutes < 60 { return "Updated \(minutes)m ago" }
        let hours = minutes / 60
        if hours < 48 { return "Updated \(hours)h ago" }
        return "Updated \(hours / 24)d ago"
    }

    private var notificationsCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            muteRow(
                title: "Mute new messages",
                choice: $newMessagesChoice,
                bucket: .newMessages
            )

            Rectangle()
                .fill(Color.white.opacity(0.10))
                .frame(height: 1)

            muteRow(
                title: "Mute replies / @-mentions",
                choice: $mentionsChoice,
                bucket: .mentionsAndReplies
            )
        }
        .settingsCardStyle()
    }

    private var leaveSquadRow: some View {
        Button {
            if isOwner && otherMemberCount > 0 {
                showOwnershipTransferBlock = true
                return
            }
            confirmLeave = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.red.opacity(0.92))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Leave squad")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.red.opacity(0.95))
                    Text("You’ll lose access to this squad’s chat, events, grid, and members.")
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
            .padding(.vertical, 14)
            .background(Color.red.opacity(0.07))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.red.opacity(0.22), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(leaveBusy || renameBusy)
        .opacity(leaveBusy ? 0.55 : 1)
    }

    private var renameSheet: some View {
        NavigationStack {
            ZStack {
                SettingsSheetChrome.settingsBackgroundGradient
                    .ignoresSafeArea()
                VStack(alignment: .leading, spacing: 14) {
                    TextField("Squad name", text: $renameDraft)
                        .textInputAutocapitalization(.words)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .foregroundStyle(.white)
                    Spacer()
                }
                .padding(18)
            }
            .navigationTitle("Squad name")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        showRenameSheet = false
                        renameDraft = displayName
                    }
                    .foregroundStyle(.white)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await saveRename() }
                    }
                    .foregroundStyle(.purple)
                    .disabled(renameBusy || renameDraft.trimmingCharacters(in: .whitespacesAndNewlines).count < 2)
                }
            }
        }
    }

    private func muteRow(
        title: String,
        choice: Binding<SquadNotificationMuteChoice>,
        bucket: SquadNotificationMuteBucket
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)

            Menu {
                ForEach(SquadNotificationMuteChoice.allCases) { option in
                    Button {
                        choice.wrappedValue = option
                        SquadNotificationMuteStore.saveChoice(option, circleId: circleId, bucket: bucket)
                    } label: {
                        HStack {
                            Text(option.displayTitle)
                            if option == choice.wrappedValue {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack {
                    Text(choice.wrappedValue.displayTitle)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.92))
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.45))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
    }

    private func reloadMutes() {
        newMessagesChoice = SquadNotificationMuteStore.loadChoice(circleId: circleId, bucket: .newMessages)
        mentionsChoice = SquadNotificationMuteStore.loadChoice(circleId: circleId, bucket: .mentionsAndReplies)
    }

    private func saveRename() async {
        let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return }
        renameBusy = true
        defer { renameBusy = false }
        let ok = await appState.renameSquad(circleID: circleId, name: trimmed)
        await MainActor.run {
            if ok {
                showRenameSheet = false
            }
        }
    }

    private func performLeave() async {
        leaveBusy = true
        defer { leaveBusy = false }
        let result = await appState.leaveSquad(circleID: circleId)
        await MainActor.run {
            switch result {
            case .ownershipTransferRequired:
                showOwnershipTransferBlock = true
            case .failed:
                break
            case .left, .squadDeleted:
                dismiss()
                onSuccessfullyLeftSquad()
            }
        }
    }
}
