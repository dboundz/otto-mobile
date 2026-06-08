import SwiftUI

struct MapMarkerChatDestinationSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let payload: MapMarkerSharePayload
    var onPosted: (() -> Void)? = nil

    @State private var recipientTab: RecipientTab = .squads
    @State private var selectedCircleID = ""
    @State private var selectedDMUserID = ""
    @State private var messageText = ""
    @State private var isSending = false
    @State private var previewMapJPEG: Data?

    private enum RecipientTab: String, CaseIterable {
        case squads
        case dms
    }

    private var availableCircles: [DriveCircle] {
        appState.circles
    }

    private var availableDMContacts: [DirectConversationDTO] {
        appState.sortedDirectConversations
    }

    private var selectedCircleName: String {
        availableCircles.first(where: { $0.id == selectedCircleID })?.name ?? "Squad"
    }

    private var canSend: Bool {
        guard !isSending else { return false }
        switch recipientTab {
        case .squads:
            return !selectedCircleID.isEmpty
        case .dms:
            return !selectedDMUserID.isEmpty
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    previewCard
                    messageField
                    recipientTabs
                    destinationsSection
                }
                .padding(18)
                .padding(.bottom, 92)
            }

            postButton
                .padding(.horizontal, 18)
                .padding(.top, 12)
                .padding(.bottom, 18)
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0), Color.black.opacity(0.96), Color.black],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea(edges: .bottom)
                )
        }
        .background(Color.black.ignoresSafeArea())
        .task {
            await loadPreviewSnapshot()
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.purple.opacity(0.25))
                Image(systemName: "bubble.left.fill")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.purple)
            }
            .frame(width: 38, height: 38)

            VStack(alignment: .leading, spacing: 2) {
                Text(String(localized: "map_marker_share_post_title"))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                Text(String(localized: "map_marker_share_post_subtitle"))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
            }

            Spacer()

            Button {
                dismiss()
            } label: {
                OttoGlassIconButtonLabel(
                    systemImage: "xmark",
                    size: CGSize(width: 32, height: 32),
                    cornerRadius: 16,
                    font: .system(size: 13, weight: .bold),
                    foregroundStyle: .white.opacity(0.72),
                    backgroundOpacity: 0.08,
                    strokeOpacity: 0
                )
            }
            .buttonStyle(.plain)
        }
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            mapPreviewHero
            VStack(alignment: .leading, spacing: 6) {
                Text(payload.title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                if let subtitle = payload.subtitle, !subtitle.isEmpty {
                    Label(subtitle, systemImage: "mappin")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.72))
                        .lineLimit(2)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        }
    }

    @ViewBuilder
    private var mapPreviewHero: some View {
        let height = max(96, (UIScreen.main.bounds.width - 64) * 0.34)
        Group {
            if let previewMapJPEG, let image = UIImage(data: previewMapJPEG) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    Color.white.opacity(0.04)
                    previewIcon
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private var previewIcon: some View {
        switch payload.previewKind {
        case .savedPlace:
            Image("map-point-saved")
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 54)
        case .raceTrack:
            Image("map-point-track")
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 36)
        }
    }

    private var messageField: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionLabel(String(localized: "map_marker_share_message_label"))
            TextField(String(localized: "map_marker_share_message_placeholder"), text: $messageText, axis: .vertical)
                .lineLimit(2...5)
                .foregroundStyle(.white)
                .padding(12)
                .background(Color.white.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var recipientTabs: some View {
        Picker("Recipient", selection: $recipientTab) {
            Text(String(localized: "squads_subtab_squads")).tag(RecipientTab.squads)
            Text(String(localized: "squads_subtab_dms")).tag(RecipientTab.dms)
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder
    private var destinationsSection: some View {
        switch recipientTab {
        case .squads:
            squadsSection
        case .dms:
            dmsSection
        }
    }

    private var squadsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel(String(localized: "map_marker_share_squad_label"))
            if availableCircles.isEmpty {
                Text(String(localized: "map_marker_share_need_squad"))
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.62))
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.055))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else {
                VStack(spacing: 8) {
                    ForEach(availableCircles) { circle in
                        squadDestinationRow(circle)
                    }
                }
            }
        }
    }

    private var dmsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel(String(localized: "squads_subtab_dms"))
            if availableDMContacts.isEmpty {
                Text(String(localized: "map_marker_share_need_dm"))
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.62))
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.055))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else {
                VStack(spacing: 8) {
                    ForEach(availableDMContacts) { conversation in
                        if let other = conversation.otherUser {
                            dmDestinationRow(conversation: conversation, other: other)
                        }
                    }
                }
            }
        }
    }

    private var postButton: some View {
        Button {
            beginPost()
        } label: {
            OttoGradientButtonLabel(
                title: isSending ? String(localized: "map_marker_share_posting") : postButtonTitle,
                systemImage: "paperplane.fill",
                height: 58
            )
        }
        .buttonStyle(.plain)
        .disabled(!canSend)
        .opacity(canSend ? 1 : 0.45)
    }

    private var postButtonTitle: String {
        switch recipientTab {
        case .squads:
            if selectedCircleID.isEmpty {
                return String(localized: "map_marker_share_choose_squad")
            }
            return String(format: String(localized: "map_marker_share_post_to_squad"), selectedCircleName)
        case .dms:
            if selectedDMUserID.isEmpty {
                return String(localized: "map_marker_share_choose_dm")
            }
            let name = availableDMContacts
                .first(where: { $0.otherUser?.id == selectedDMUserID })?
                .otherUser?
                .displayName ?? "DM"
            return String(format: String(localized: "map_marker_share_post_to_dm"), name)
        }
    }

    private func squadDestinationRow(_ circle: DriveCircle) -> some View {
        Button {
            selectedCircleID = circle.id
            selectedDMUserID = ""
        } label: {
            SquadShareListRow(
                name: circle.name,
                photoUrl: circle.photoUrl,
                icon: circle.icon,
                memberCount: circle.members.count,
                cacheStorageKey: "squadAvatar:\(circle.id)"
            ) {
                Image(systemName: selectedCircleID == circle.id ? "circle.inset.filled" : "circle")
                    .font(.title3)
                    .foregroundStyle(selectedCircleID == circle.id ? Color.purple : Color.white.opacity(0.28))
            }
            .padding(12)
            .background(Color.white.opacity(selectedCircleID == circle.id ? 0.075 : 0.045))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selectedCircleID == circle.id ? Color.purple.opacity(0.95) : Color.clear, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }

    private func dmDestinationRow(conversation: DirectConversationDTO, other: DirectConversationDTO.UserSummaryDTO) -> some View {
        let trimmed = other.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let displayName = trimmed.isEmpty ? other.id : trimmed
        return Button {
            selectedDMUserID = other.id
            selectedCircleID = ""
        } label: {
            HStack(spacing: 12) {
                AvatarView(
                    name: displayName,
                    avatarUrl: other.avatarUrl,
                    size: 44,
                    accentColor: MapAccentPalette.resolvedColor(mapAccentKey: other.mapAccentKey, userId: other.id),
                    accentRingWidth: 0,
                    whiteRingWidth: 0
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(displayName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
                Spacer()
                Image(systemName: selectedDMUserID == other.id ? "circle.inset.filled" : "circle")
                    .font(.title3)
                    .foregroundStyle(selectedDMUserID == other.id ? Color.purple : Color.white.opacity(0.28))
            }
            .padding(12)
            .background(Color.white.opacity(selectedDMUserID == other.id ? 0.075 : 0.045))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selectedDMUserID == other.id ? Color.purple.opacity(0.95) : Color.clear, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white.opacity(0.56))
    }

    private func loadPreviewSnapshot() async {
        guard let lat = payload.latitude, let lng = payload.longitude else { return }
        previewMapJPEG = await PlaceMapSnapshotGenerator.jpegData(latitude: lat, longitude: lng)
    }

    private func beginPost() {
        guard canSend else { return }
        guard let lat = payload.latitude, let lng = payload.longitude else { return }

        isSending = true
        let body = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        let mapJPEG = previewMapJPEG

        Task {
            do {
                switch recipientTab {
                case .squads:
                    let circleID = selectedCircleID.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !circleID.isEmpty else { return }
                    if let savedPlaceId = payload.savedPlaceId {
                        _ = try await APIClient.shared.postCircleChatPlaceMessage(
                            circleId: circleID,
                            body: body,
                            placeId: savedPlaceId,
                            mapPreviewJPEGData: mapJPEG
                        )
                    } else {
                        _ = try await APIClient.shared.postCircleChatPlaceMessage(
                            circleId: circleID,
                            body: body,
                            placeLatitude: lat,
                            placeLongitude: lng,
                            placeName: payload.title,
                            placeAddressSummary: payload.subtitle,
                            mapPreviewJPEGData: mapJPEG
                        )
                    }
                    await MainActor.run {
                        appState.requestSquadChatFocus(circleID: circleID)
                    }
                case .dms:
                    let userID = selectedDMUserID.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !userID.isEmpty else { return }
                    let conversation = try await APIClient.shared.getOrCreateDirectConversation(recipientUserId: userID)
                    if let savedPlaceId = payload.savedPlaceId {
                        _ = try await APIClient.shared.postDirectChatPlaceMessage(
                            conversationId: conversation.id,
                            body: body,
                            placeId: savedPlaceId,
                            mapPreviewJPEGData: mapJPEG
                        )
                    } else {
                        _ = try await APIClient.shared.postDirectChatPlaceMessage(
                            conversationId: conversation.id,
                            body: body,
                            placeLatitude: lat,
                            placeLongitude: lng,
                            placeName: payload.title,
                            placeAddressSummary: payload.subtitle,
                            mapPreviewJPEGData: mapJPEG
                        )
                    }
                    await MainActor.run {
                        appState.registerDirectConversation(conversation)
                        appState.requestDirectMessageFocus(conversationID: conversation.id, userID: userID)
                    }
                }

                await MainActor.run {
                    dismiss()
                    onPosted?()
                    appState.activeToast = AppToast(
                        text: String(localized: "map_marker_share_posted_toast"),
                        systemImage: "bubble.left.and.bubble.right.fill"
                    )
                }
            } catch {
                await MainActor.run {
                    isSending = false
                    appState.errorMessage = String(localized: "map_marker_share_post_failed")
                }
            }
        }
    }
}
