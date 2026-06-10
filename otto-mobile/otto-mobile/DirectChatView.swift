import Combine
import SwiftUI
import CoreLocation
import PhotosUI
import UIKit
import os

private func isDirectChatFetchError(_ message: String?) -> Bool {
    message == "Could not load messages."
}

private enum DirectChatActiveSheet: Identifiable {
    case profile(FriendLocation)
    case event(CircleChatMessageDTO.EventAttachmentDTO)

    var id: String {
        switch self {
        case .profile(let f): return "profile-\(f.id)"
        case .event(let e): return "event-\(e.eventId)"
        }
    }
}

/// Isolated transcript so unrelated `AppState` publishes do not rebuild every message row while scrolling.
private struct DirectChatTranscriptList: View, Equatable {
    let messages: [DirectMessageDTO]
    let isLoadingOlderMessages: Bool
    let showsEmptyIntro: Bool
    let emptyIntro: AnyView
    let longPressMessageID: String?
    let longPressChromeLiftY: CGFloat
    let currentUserID: String
    let oldestPrefetchMessageIDs: Set<String>
    let pendingUploadProgressByClientMessageId: [String: Double]
    let messageRow: (DirectMessageDTO) -> AnyView
    let onMessageAppear: (DirectMessageDTO) -> Void

    static func == (lhs: DirectChatTranscriptList, rhs: DirectChatTranscriptList) -> Bool {
        lhs.messages == rhs.messages
            && lhs.isLoadingOlderMessages == rhs.isLoadingOlderMessages
            && lhs.showsEmptyIntro == rhs.showsEmptyIntro
            && lhs.longPressMessageID == rhs.longPressMessageID
            && lhs.longPressChromeLiftY == rhs.longPressChromeLiftY
            && lhs.currentUserID == rhs.currentUserID
            && lhs.oldestPrefetchMessageIDs == rhs.oldestPrefetchMessageIDs
            && lhs.pendingUploadProgressByClientMessageId == rhs.pendingUploadProgressByClientMessageId
    }

    var body: some View {
        ZStack {
            if longPressMessageID != nil {
                Color.black.opacity(0.45)
                    .zIndex(1)
                    .allowsHitTesting(false)
            }

            LazyVStack(spacing: 14) {
                if showsEmptyIntro {
                    emptyIntro
                }

                if isLoadingOlderMessages {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }

                ForEach(messages) { message in
                    messageRow(message)
                        .id(message.id)
                        .offset(y: longPressMessageID == message.id ? longPressChromeLiftY : 0)
                        .scaleEffect(
                            longPressMessageID == message.id ? 1.03 : 1.0,
                            anchor: message.senderUserId == currentUserID ? .trailing : .leading
                        )
                        .shadow(
                            color: .black.opacity(longPressMessageID == message.id ? 0.5 : 0),
                            radius: longPressMessageID == message.id ? 20 : 0,
                            y: 9
                        )
                        .zIndex(longPressMessageID == message.id ? 2 : 0)
                        .onAppear {
                            onMessageAppear(message)
                        }
                }
            }
        }
    }
}

private struct DirectMessageRow<EventCard: View, PlaceCard: View>: View {
    let message: DirectMessageDTO
    let recipientName: String
    let recipientAvatarURL: String?
    let recipientAccentColor: Color
    let currentUserID: String
    var localVideoThumbnail: UIImage? = nil
    var videoUploadProgress: Double? = nil
    var videoUploadPhase: ChatVideoUploadCoordinator.Phase? = nil
    @Binding var longPressMessage: DirectMessageDTO?
    @Binding var reactionsDetailMessage: DirectMessageDTO?
    @Binding var suppressAttachmentNavigationForMessageID: String?
    @Binding var longPressChromeLiftY: CGFloat
    @Binding var directActiveSheet: DirectChatActiveSheet?
    let onAvatarTap: () -> Void
    let onDoubleTapHeart: () -> Void
    let onJumpToQuotedMessage: (String) -> Void
    var onRemovePendingMessage: ((String) -> Void)? = nil
    let reportsFrame: Bool
    @ViewBuilder var eventCard: (Bool) -> EventCard
    @ViewBuilder var placeCard: (Bool) -> PlaceCard

    private func rowTimeText(_ date: Date) -> String {
        ChatRowTimeFormatter.string(from: date)
    }

    var body: some View {
        let isMine = message.senderUserId == currentUserID
        let suppressEventAttachmentNav = suppressAttachmentNavigationForMessageID == message.id

        HStack(alignment: .top, spacing: isMine ? 8 : 12) {
            if isMine { Spacer(minLength: 44) }

            if !isMine {
                Button(action: onAvatarTap) {
                    AvatarView(
                        name: recipientName,
                        avatarUrl: recipientAvatarURL,
                        size: 34,
                        accentColor: recipientAccentColor,
                        accentRingWidth: 2,
                        whiteRingWidth: 0
                    )
                }
                .buttonStyle(.plain)
            }

            VStack(alignment: isMine ? .trailing : .leading, spacing: isMine ? 4 : 8) {
                if !isMine {
                    HStack(spacing: 6) {
                        Text(recipientName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(recipientAccentColor)
                        Text(rowTimeText(message.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.45))
                    }
                }

                VStack(alignment: isMine ? .trailing : .leading, spacing: 8) {
                    HStack(alignment: .top, spacing: 0) {
                        if isMine { Spacer(minLength: 0) }
                        ZStack {
                            ChatMessageTextBubble(
                                bodyText: message.body,
                                imageURLString: message.imageUrl,
                                videoAttachment: message.videoAttachment,
                                localVideoThumbnail: localVideoThumbnail,
                                videoUploadProgress: videoUploadProgress,
                                videoUploadPhase: videoUploadPhase,
                                isVideoUploadPending: message.id.hasPrefix("pending-"),
                                onCancelVideoUpload: message.id.hasPrefix("pending-") ? {
                                    onRemovePendingMessage?(message.id)
                                } : nil,
                                isMine: isMine,
                                replyQuote: ChatMessageReplyQuote(replyTo: message.replyTo),
                                messageId: message.id,
                                isTextSelectable: longPressMessage?.id == message.id,
                                onLongPress: {
                                    ChatMessageActionFeedback.dismissKeyboard()
                                    withAnimation(.easeOut(duration: 0.12)) {
                                        longPressChromeLiftY = 0
                                        longPressMessage = message
                                    }
                                    suppressAttachmentNavigationForMessageID = message.id
                                    let id = message.id
                                    Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                                        if suppressAttachmentNavigationForMessageID == id {
                                            suppressAttachmentNavigationForMessageID = nil
                                        }
                                    }
                                },
                                onDoubleTapHeart: onDoubleTapHeart,
                                onTapReplyQuote: message.replyTo != nil && !(message.replyToMessageId ?? "").isEmpty
                                    ? {
                                        guard let parentId = message.replyToMessageId,
                                              !parentId.isEmpty,
                                              parentId != message.id else { return }
                                        onJumpToQuotedMessage(parentId)
                                    }
                                    : nil
                            )
                        }
                        .conditionalChatMessageFrameReporting(messageId: message.id, enabled: reportsFrame)
                        if !isMine { Spacer(minLength: 0) }
                    }
                    .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)

                    if ChatMessageTextBubble.messageHasRichTail(
                        linkPreview: message.linkPreview,
                        eventAttachment: message.eventAttachment,
                        placeAttachment: message.placeAttachment
                    ) {
                        VStack(alignment: .leading, spacing: 8) {
                            ChatLinkPreviewCard(
                                preview: message.linkPreview,
                                messageId: message.id,
                                fixedWidth: ChatMessageTextBubble.standardLayoutWidth,
                                onOttoEventDeepLink: { ref in
                                    directActiveSheet = .event(
                                        CircleChatMessageDTO.EventAttachmentDTO(
                                            inferredEventRef: ref,
                                            squadCircleId: nil
                                        )
                                    )
                                },
                                onLongPress: {
                                    ChatMessageActionFeedback.dismissKeyboard()
                                    withAnimation(.easeOut(duration: 0.12)) {
                                        longPressChromeLiftY = 0
                                        longPressMessage = message
                                    }
                                    suppressAttachmentNavigationForMessageID = message.id
                                    let id = message.id
                                    Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                                        if suppressAttachmentNavigationForMessageID == id {
                                            suppressAttachmentNavigationForMessageID = nil
                                        }
                                    }
                                },
                                onDoubleTapHeart: onDoubleTapHeart
                            )
                            eventCard(suppressEventAttachmentNav)
                            placeCard(suppressEventAttachmentNav)
                        }
                        .frame(
                            width: ChatMessageTextBubble.standardLayoutWidth,
                            alignment: .leading
                        )
                        .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
                    }
                }

                if !message.reactions.isEmpty {
                    ChatMessageReactionsStrip(
                        reactions: message.reactions,
                        alignment: isMine ? .trailing : .leading,
                        onTap: {
                            reactionsDetailMessage = message
                        }
                    )
                }
            }

            if !isMine { Spacer(minLength: 44) }
        }
    }
}

private struct DirectChatComposerIsland: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    let recipientName: String
    @ObservedObject var state: DirectChatConversationState
    var composerFocused: FocusState<Bool>.Binding
    var onJumpToQuotedMessage: (String) -> Void

    @State private var attachmentLimitAlertMessage: String?
    @ObservedObject private var videoUploads = ChatVideoUploadCoordinator.shared

    private var canSend: Bool {
        if state.isSending { return false }
        if let editId = state.editingMessageId {
            let t = state.draft.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !t.isEmpty else { return false }
            let baseline =
                state.messages.first(where: { $0.id == editId })?.body
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return t != baseline
        }
        let hasText = !state.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasAttachment = !state.pendingAttachments.isEmpty
        return hasText || hasAttachment
    }

    private var editingPreviewBody: String {
        guard let id = state.editingMessageId else { return "" }
        return state.messages.first(where: { $0.id == id })?.body ?? ""
    }

    var body: some View {
        VStack(spacing: 0) {
            ChatComposerBar(
            placeholder: "Message \(recipientName)...",
            text: $state.draft,
            isSending: state.isSending,
            canSend: canSend,
            showsAttachmentButton: state.editingMessageId == nil,
            enabledAttachmentActions: ChatComposerAttachmentAction.directChatActions,
            pendingAttachments: $state.pendingAttachments,
            attachmentLimitAlertMessage: $attachmentLimitAlertMessage,
            onSend: {
                Task { await sendMessage() }
            },
            composerFocused: composerFocused,
            replyToAuthorName: state.replyDraft.authorName,
            replyToSnippet: state.replyDraft.snippet,
            replyToAvatarURL: state.replyDraft.avatarURL,
            onCancelReply: state.replyDraft.messageId == nil
                ? nil
                : {
                    state.replyDraft = .empty
                },
            onTapReplyTo: state.replyDraft.messageId == nil
                ? nil
                : {
                    composerFocused.wrappedValue = false
                    if let messageId = state.replyDraft.messageId {
                        onJumpToQuotedMessage(messageId)
                    }
                },
            isEditingMessage: state.editingMessageId != nil,
            editingPreviewText: state.editingMessageId != nil ? editingPreviewBody : nil,
            onCancelEditing: state.editingMessageId == nil
                ? nil
                : {
                    state.editingMessageId = nil
                    state.draft = ""
                },
            klipyCustomerId: appState.currentUserID
            )
            .environmentObject(locationService)
        }
        .onChange(of: state.editingMessageId) { _, new in
            if new != nil {
                composerFocused.wrappedValue = true
            }
        }
        .alert(
            "Video can't be attached",
            isPresented: Binding(
                get: { attachmentLimitAlertMessage != nil },
                set: { if !$0 { attachmentLimitAlertMessage = nil } }
            )
        ) {
            Button("OK", role: .cancel) {
                attachmentLimitAlertMessage = nil
            }
        } message: {
            Text(attachmentLimitAlertMessage ?? "")
        }
    }

    private func sendMessage() async {
        guard let conversation = state.conversation else { return }
        guard !state.isSending else { return }
        let body = state.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let savedAttachments = state.pendingAttachments
        let pendingAttachment = savedAttachments.first
        guard !body.isEmpty || pendingAttachment != nil else { return }
        if state.editingMessageId != nil, pendingAttachment != nil { return }
        if let eid = state.editingMessageId {
            let baseline =
                state.messages.first(where: { $0.id == eid })?.body
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if body == baseline { return }
        }
        state.isSending = true
        state.errorMessage = nil
        let replyId = state.replyDraft.messageId
        let editingId = state.editingMessageId
        if editingId == nil {
            state.draft = ""
            state.pendingAttachments = []
            state.replyDraft = .empty
        } else {
            state.replyDraft = .empty
        }
        do {
            if let editId = editingId {
                let message = try await APIClient.shared.patchDirectMessage(
                    conversationId: conversation.id,
                    messageId: editId,
                    body: body
                )
                state.editingMessageId = nil
                state.draft = ""
                appState.upsertDirectMessageTranscript(message)
            } else if let attachment = pendingAttachment, attachment.isVideo,
                      let pickerItem = attachment.pickerItem {
                let prepared = try await ChatPickerPreviewLoader.preparedVideo(from: pickerItem)
                let clientMessageId = UUID().uuidString
                let senderSummary = appState.allUsers.first(where: { $0.id == appState.currentUserID })
                let sender = senderSummary.map {
                    DirectConversationDTO.UserSummaryDTO(
                        id: $0.id,
                        displayName: $0.displayName,
                        avatarUrl: $0.avatarUrl,
                        mapAccentKey: $0.mapAccentKey
                    )
                }
                videoUploads.startDirectUpload(
                    conversationId: conversation.id,
                    prepared: prepared,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyId,
                    senderUserId: appState.currentUserID,
                    sender: sender,
                    onOptimisticMessage: { optimistic in
                        appState.chatStore.insertPendingDirectMessage(optimistic)
                    },
                    onComplete: { result in
                        switch result {
                        case .success(let message):
                            OttoAnalytics.logChatMessageSent(channel: "direct", attachmentType: "video")
                            appState.upsertDirectMessageTranscript(message)
                        case .failure(let error):
                            OttoLog.api.error("Direct chat video send failed conversation=\(conversation.id) error=\(String(describing: error))")
                        }
                        state.isSending = false
                    }
                )
                return
                return
            } else if let attachment = pendingAttachment, attachment.isPlace,
                      let payload = attachment.placePayload,
                      let lat = payload.latitude,
                      let lng = payload.longitude {
                let message = try await APIClient.shared.postDirectChatPlaceMessage(
                    conversationId: conversation.id,
                    body: body,
                    placeLatitude: lat,
                    placeLongitude: lng,
                    placeName: payload.title,
                    placeAddressSummary: payload.subtitle,
                    mapPreviewJPEGData: attachment.mapPreviewJPEG
                )
                OttoAnalytics.logChatMessageSent(channel: "direct", attachmentType: "place")
                appState.upsertDirectMessageTranscript(message)
            } else {
                let normalized = ChatOutgoingImageURLNormalizer.normalize(
                    draft: body,
                    pendingAttachment: pendingAttachment
                )
                let photoJPEG: Data?
                if normalized.imageUrl == nil,
                   let attachment = pendingAttachment, attachment.isPhoto,
                   let pickerItem = attachment.pickerItem {
                    photoJPEG = try await ChatPickerPreviewLoader.photoJPEG(from: pickerItem)
                } else {
                    photoJPEG = nil
                }
                guard !normalized.body.isEmpty || photoJPEG != nil || normalized.imageUrl != nil else { return }
                let message = try await APIClient.shared.sendDirectMessage(
                    conversationId: conversation.id,
                    body: normalized.body,
                    replyToMessageId: replyId,
                    photoJPEGData: photoJPEG,
                    imageUrl: normalized.imageUrl
                )
                if let share = normalized.klipyShare {
                    await KlipyAPIClient.reportShare(
                        slug: share.slug,
                        customerId: appState.currentUserID,
                        searchQuery: share.searchQuery
                    )
                }
                let attachmentType: String = {
                    if normalized.klipyShare != nil { return "gif" }
                    if let imageUrl = normalized.imageUrl {
                        return ChatImageURLDisplay.isAnimatedImageURL(imageUrl) ? "gif" : "image_url"
                    }
                    if photoJPEG != nil { return "photo" }
                    return "none"
                }()
                OttoAnalytics.logChatMessageSent(channel: "direct", attachmentType: attachmentType)
                appState.upsertDirectMessageTranscript(message)
            }
        } catch {
            OttoLog.api.error(
                "Direct chat send failed conversation=\(conversation.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            state.draft = body
            state.pendingAttachments = savedAttachments
            state.replyDraft.messageId = replyId
            if let id = replyId, let ref = state.messages.first(where: { $0.id == id }) {
                let isFromMe = ref.senderUserId == appState.currentUserID
                state.replyDraft.authorName = isFromMe
                    ? (appState.allUsers.first(where: { $0.id == appState.currentUserID })?.displayName ?? "You")
                    : recipientName
                let trimmed = ref.body.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    state.replyDraft.snippet = trimmed
                } else if ref.videoAttachment != nil {
                    state.replyDraft.snippet = "Video"
                } else if ref.imageUrl != nil {
                    state.replyDraft.snippet = ChatImageURLDisplay.replySnippet(for: ref.imageUrl)
                } else {
                    state.replyDraft.snippet = trimmed
                }
                state.replyDraft.avatarURL = isFromMe
                    ? appState.allUsers.first(where: { $0.id == appState.currentUserID })?.avatarUrl
                    : ref.sender?.avatarUrl
            }
            if let prepError = error as? ChatVideoUploadPrepError {
                attachmentLimitAlertMessage = prepError.errorDescription
            } else if let pickerError = error as? ChatPickerPreviewLoader.Error {
                attachmentLimitAlertMessage = pickerError.errorDescription
            } else {
                state.errorMessage = editingId == nil ? "Message failed. Try again." : "Couldn't update message."
            }
        }
        state.isSending = false
    }
}

struct DirectChatView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @Environment(\.dismiss) private var dismiss
    let recipientUserID: String
    let recipientName: String
    let recipientAvatarURL: String?
    let recipientAccentColor: Color
    /// When opening from the inbox, skip `getOrCreate` and load messages for this conversation only.
    var prefetchedConversation: DirectConversationDTO?

    private var chatState: DirectChatConversationState {
        appState.chatStore.directState(
            recipientUserID: recipientUserID,
            recipientName: recipientName,
            recipientAvatarURL: recipientAvatarURL
        )
    }

    @State private var directActiveSheet: DirectChatActiveSheet?
    @State private var longPressMessage: DirectMessageDTO?
    @State private var reactionsDetailMessage: DirectMessageDTO?
    @State private var directMessageFrames: [String: CGRect] = [:]
    @State private var longPressChromeLiftY: CGFloat = 0
    @State private var chatKeyboardOverlap: CGFloat = 0
    /// After a row long-press succeeds, ignore stray taps on the event card; see `suppressAttachmentNavigationForMessageID`.
    @State private var suppressAttachmentNavigationForMessageID: String?
    @State private var chatRsvpSubmittingEventId: String?
    @State private var chatStateRevision = 0
    @State private var scrollDistanceFromBottom: CGFloat = 0
    @State private var isScrollLayoutReady = false
    @State private var isScrollUserInteracting = false
    @State private var scrollSettleTrigger = 0
    @State private var scrollViewInstanceId = UUID()
    @State private var scrollViewHasAppearedOnce = false
    @State private var chatScrollHandle = ChatScrollViewHandle()
    @State private var directDeleteConfirm: DirectMessageDTO?
    @FocusState private var isComposerFocused: Bool
    @ObservedObject private var videoUploads = ChatVideoUploadCoordinator.shared

    private var bottomSentinelID: String {
        "chat-bottom-\(chatState.conversation?.id ?? recipientUserID)"
    }

    private var directChatScrollViewID: String {
        "direct-chat-scroll-\(chatState.conversation?.id ?? recipientUserID)"
    }

    private var recipientFriendLocation: FriendLocation {
        if let existing = appState.circles.flatMap(\.members).first(where: { $0.id == recipientUserID }) {
            return existing
        }
        return FriendLocation(
            id: recipientUserID,
            name: recipientName,
            avatarName: recipientName,
            avatarUrl: recipientAvatarURL,
            car: "Unknown Car",
            clubRole: "Member",
            lastRun: "Recent drive",
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: recipientAccentColor,
            movementMode: .unknown
        )
    }

    var body: some View {
        let _ = chatStateRevision
        VStack(spacing: 0) {
            if chatState.messages.isEmpty && chatState.isLoading {
                ProgressView()
                    .tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if chatState.messages.isEmpty, isDirectChatFetchError(chatState.errorMessage) {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_messages_title"),
                    message: String(localized: "fetch_error_refresh_body"),
                    systemImage: "exclamationmark.triangle",
                    actionTitle: String(localized: "fetch_error_refresh_action"),
                    action: {
                        Task { await loadConversation() }
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if chatState.messages.isEmpty, let errorMessage = chatState.errorMessage {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_messages_title"),
                    message: errorMessage,
                    systemImage: "bubble.left.and.exclamationmark.bubble.right"
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ZStack {
                    // Composer lives in `messageList` safeAreaInset (not a VStack sibling) so scroll-settle
                    // opacity hides transcript only; see `chatScrollSettleTranscriptVisibility`.
                    messageList

                    if let target = longPressMessage {
                        ChatMessageActionOverlay(
                            onDismiss: clearDirectLongPressChrome,
                            onReply: { beginDirectReply(to: target) },
                            onReaction: { emoji in
                                Task {
                                    await postDirectReaction(emoji: emoji, for: target.id)
                                }
                            },
                            onEdit: directCanEditText(target)
                                ? {
                                    chatState.editingMessageId = target.id
                                    chatState.draft = target.body
                                    chatState.replyDraft = .empty
                                }
                                : nil,
                            onDelete: directOwnUserBubble(target)
                                ? {
                                    directDeleteConfirm = target
                                    clearDirectLongPressChrome()
                                }
                                : nil
                        )
                        .zIndex(50)
                        .transition(.opacity)
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    ChatScrollToLatestFloatingButton(
                        visible: showScrollToLatestAffordance,
                        badgeCount: jumpToLatestBadgeCount,
                        bottomPadding: ChatScrollToLatestLayout.composerReservePoints,
                        action: {
                            guard let conversationID = chatState.conversation?.id else { return }
                            appState.chatStore.requestDirectScrollToLatest(
                                conversationID: conversationID,
                                animated: true
                            )
                        }
                    )
                    .zIndex(45)
                }
                .onChange(of: longPressMessage) { _, new in
                    guard new == nil else { return }
                    directMessageFrames = [:]
                    longPressChromeLiftY = 0
                }
            }
        }
        .background(Color.black.ignoresSafeArea())
        .chatNavigationInteractivePopSwipeEnabled()
        .navigationTitle(recipientName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Report concern") {
                        UIApplication.shared.open(WebsiteLinks.reportConcernMailto)
                    }
                    if appState.blockedUserIDs.contains(recipientUserID) {
                        Button("Unblock") {
                            Task { await unblockRecipient() }
                        }
                    } else {
                        Button("Block", role: .destructive) {
                            Task { await blockRecipient() }
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundStyle(.white.opacity(0.92))
                }
                .accessibilityLabel("Chat actions")
            }
        }
        .sheet(item: $directActiveSheet) { item in
            switch item {
            case .profile(let member):
                ProfileScreen(
                    profileUserID: member.id == appState.currentUserID ? nil : member.id,
                    onUserBlocked: { directActiveSheet = nil }
                )
                    .environmentObject(appState)
                    .environmentObject(locationService)
                    .presentationDetents([.large])
            case .event(let attachment):
                eventDetailForAttachment(attachment)
                    .presentationDetents([.large])
                    .presentationBackground(Color.black)
            }
        }
        .sheet(item: $reactionsDetailMessage) { msg in
            ChatMessageReactionsDetailSheet(
                reactions: msg.reactions,
                resolveDisplayName: { uid in
                    directReactionParticipantName(userId: uid, message: msg)
                }
            )
            .environmentObject(appState)
            .presentationDetents([chatMessageReactionsSheetDetent(reactionCount: msg.reactions.count)])
            .presentationBackground(Color(red: 0.025, green: 0.025, blue: 0.035))
        }
        .task {
            await loadConversation()
        }
        .onDisappear {
            if let conversationID = chatState.conversation?.id {
                appState.chatStore.resetDirectScrollSession(conversationID: conversationID, reason: "deactivate")
            }
            appState.setActiveDirectConversation(conversationID: nil, otherUserID: nil)
        }
        .onChange(of: appState.circlesRootTabIsSelected) { _, isSelected in
            syncDirectThreadVisibilityForRootTab(isSelected)
        }
        .onChange(of: appState.latestDirectMessage) { _, message in
            guard let message, message.conversationId == chatState.conversation?.id else { return }
            upsertMessage(message)
        }
        .onReceive(chatState.objectWillChange) { _ in
            chatStateRevision &+= 1
        }
        .onChange(of: chatState.messages.map(\.id).joined(separator: "\u{1e}")) { _, _ in
            let ids = chatState.messages.compactMap { $0.eventAttachment?.eventId }
            appState.prefetchChatAttachmentEventsIfNeeded(eventIds: ids, squadEvents: [])
        }
        .alert(
            "Delete this message?",
            isPresented: Binding(
                get: { directDeleteConfirm != nil },
                set: { if !$0 { directDeleteConfirm = nil } }
            )
        ) {
            Button("Delete", role: .destructive) {
                if let victim = directDeleteConfirm {
                    Task { await deleteDirectChatMessage(victim) }
                }
                directDeleteConfirm = nil
            }
            Button("Cancel", role: .cancel) {
                directDeleteConfirm = nil
            }
        } message: {
            Text("This cannot be undone.")
        }
    }

    private func directOwnUserBubble(_ message: DirectMessageDTO) -> Bool {
        message.senderUserId == appState.currentUserID && message.messageType == "user"
    }

    private func directCanEditText(_ message: DirectMessageDTO) -> Bool {
        guard directOwnUserBubble(message) else { return false }
        if let u = message.imageUrl, !u.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        guard message.eventAttachment == nil, message.placeAttachment == nil else { return false }
        if message.videoAttachment != nil { return false }
        return Date().timeIntervalSince(message.createdAt) <= 120
    }

    private func deleteDirectChatMessage(_ message: DirectMessageDTO) async {
        guard let conversationId = chatState.conversation?.id else { return }
        do {
            let tomb = try await APIClient.shared.deleteDirectMessage(
                conversationId: conversationId,
                messageId: message.id
            )
            appState.upsertDirectMessageTranscript(tomb)
        } catch {
            chatState.errorMessage = "Couldn't delete message."
        }
    }

    private var isHidingChatTranscriptForScrollSettle: Bool {
        ChatScrollLogic.shouldHideTranscriptForScrollSettle(
            isLoadingMessages: chatState.isLoading,
            messagesEmpty: chatState.messages.isEmpty,
            scrollState: chatState.scrollState
        )
    }

    private var showScrollSettleLoadingOverlay: Bool {
        ChatScrollLogic.shouldShowScrollSettleLoadingOverlay(
            isLoadingMessages: chatState.isLoading,
            messagesEmpty: chatState.messages.isEmpty,
            scrollState: chatState.scrollState
        )
    }

    private var jumpToLatestBadgeCount: Int? {
        let count = ChatScrollLogic.unreadCountBelowLastRead(
            messageIDs: chatState.messages.map(\.id),
            lastReadMessageId: chatState.scrollState.lastReadMessageId
        )
        return count > 0 ? count : nil
    }

    private var showScrollToLatestAffordance: Bool {
        ChatScrollLogic.shouldShowJumpToLatestAffordance(
            didInitialScrollToBottom: chatState.scrollState.didInitialScrollToBottom,
            isScrollLayoutReady: isScrollLayoutReady,
            distanceFromBottom: scrollDistanceFromBottom,
            messagesEmpty: chatState.messages.isEmpty,
            isHidingTranscriptForScrollSettle: isHidingChatTranscriptForScrollSettle
        )
    }

    private var chatPendingUploadProgressByClientMessageId: [String: Double] {
        Dictionary(
            uniqueKeysWithValues: videoUploads.pendingByClientMessageId.map { ($0.key, $0.value.progress) }
        )
    }

    private var directOldestPrefetchMessageIDs: Set<String> {
        Set(chatState.messages.prefix(5).map(\.id))
    }

    private var directScrollSettleTaskID: String {
        "\(chatState.scrollState.intentRevision)-\(scrollSettleTrigger)"
    }

    private var directScrollSettleWatchdogTaskID: String {
        guard chatState.scrollState.isSettlingScrollPosition, !chatState.messages.isEmpty else { return "idle" }
        return "watch-\(chatState.scrollState.intentRevision)-\(scrollSettleTrigger)"
    }

    private var messageList: some View {
        ZStack {
            ScrollViewReader { proxy in
                ScrollView {
                    ChatScrollDistanceFromBottomReporter(
                        distanceFromBottom: $scrollDistanceFromBottom,
                        isLayoutReady: $isScrollLayoutReady,
                        isScrollUserInteracting: $isScrollUserInteracting,
                        scrollViewHandle: chatScrollHandle
                    )
                        .frame(width: 0, height: 0)
                    VStack(spacing: 0) {
                        DirectChatTranscriptList(
                            messages: chatState.messages,
                            isLoadingOlderMessages: chatState.isLoadingOlderMessages,
                            showsEmptyIntro: chatState.messages.isEmpty,
                            emptyIntro: AnyView(
                                VStack(spacing: 10) {
                                    Button {
                                        directActiveSheet = .profile(recipientFriendLocation)
                                    } label: {
                                        AvatarView(
                                            name: recipientName,
                                            avatarUrl: recipientAvatarURL,
                                            size: 54,
                                            accentColor: recipientAccentColor
                                        )
                                    }
                                    .buttonStyle(.plain)
                                    Text("Message \(recipientName)")
                                        .font(.headline.weight(.semibold))
                                        .foregroundStyle(.white)
                                    Text("This is just between the two of you.")
                                        .font(.caption)
                                        .foregroundStyle(.white.opacity(0.65))
                                }
                                .padding(.top, 80)
                            ),
                            longPressMessageID: longPressMessage?.id,
                            longPressChromeLiftY: longPressChromeLiftY,
                            currentUserID: appState.currentUserID,
                            oldestPrefetchMessageIDs: directOldestPrefetchMessageIDs,
                            pendingUploadProgressByClientMessageId: chatPendingUploadProgressByClientMessageId,
                            messageRow: { AnyView(directMessageRow($0)) },
                            onMessageAppear: { message in
                                if let conversationID = chatState.conversation?.id {
                                    appState.chatStore.updateDirectVisibleMessage(
                                        conversationID: conversationID,
                                        messageID: message.id,
                                        scrollViewInstanceId: scrollViewInstanceId
                                    )
                                    if directOldestPrefetchMessageIDs.contains(message.id) {
                                        loadOlderDirectMessagesIfNeeded(
                                            conversationID: conversationID,
                                            thresholdMessageID: message.id
                                        )
                                    }
                                }
                            }
                        )
                        .equatable()

                        Color.clear
                            .frame(height: ChatScrollToLatestLayout.transcriptBottomPadding)
                        Color.clear
                            .frame(height: 1)
                            .id(bottomSentinelID)
                    }
                    .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                    .padding(.top, 18)
                    .chatScrollSettleTranscriptVisibility(isHidden: isHidingChatTranscriptForScrollSettle)
                }
                .defaultScrollAnchor(.bottom)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    VStack(spacing: 0) {
                        ChatKeyboardDismissZone(
                            height: ChatKeyboardDismissZoneMetrics.composerStripHeight,
                            isActive: ChatComposerKeyboardDismiss.isActive(
                                keyboardOverlap: chatKeyboardOverlap,
                                composerFocused: isComposerFocused
                            ),
                            onDismiss: {
                                ChatComposerKeyboardDismiss.dismiss(composerFocused: $isComposerFocused)
                            }
                        )

                        DirectChatComposerIsland(
                            recipientName: recipientName,
                            state: chatState,
                            composerFocused: $isComposerFocused,
                            onJumpToQuotedMessage: jumpToQuotedMessage
                        )
                    }
                    .chatComposerKeyboardLift($chatKeyboardOverlap)
                }
                .id(directChatScrollViewID)
                .scrollDismissesKeyboard(.never)
                .onAppear {
                    requestDirectScrollViewDidAppear()
                }
                .onChange(of: chatState.messages.isEmpty) { wasEmpty, isEmpty in
                    if wasEmpty, !isEmpty, chatState.scrollState.pendingScrollIntent != nil {
                        scrollSettleTrigger &+= 1
                    }
                }
                .onChange(of: scrollDistanceFromBottom) { oldDistance, newDistance in
                    if ChatUIKitScrollPinning.shouldUpdateBottomPinning(
                        oldDistance: oldDistance,
                        newDistance: newDistance,
                        newestMessageID: chatState.messages.last?.id,
                        lastReadMessageID: chatState.scrollState.lastReadMessageId
                    ) {
                        updateBottomPinning()
                    }
                    if isHidingChatTranscriptForScrollSettle,
                       isScrollLayoutReady,
                       chatState.scrollState.pendingScrollIntent != nil {
                        scrollSettleTrigger &+= 1
                    }
                }
                .task(id: directScrollSettleTaskID) {
                    await executePendingScrollIntent(proxy: proxy)
                }
                .task(id: directScrollSettleWatchdogTaskID) {
                    guard chatState.scrollState.isSettlingScrollPosition, !chatState.messages.isEmpty else { return }
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    guard !Task.isCancelled else { return }
                    if chatState.scrollState.isSettlingScrollPosition, let conversationID = chatState.conversation?.id {
                        appState.chatStore.clearDirectScrollSettle(conversationID: conversationID)
                        chatStateRevision &+= 1
                        if !chatState.scrollState.didInitialScrollToBottom {
                            appState.chatStore.markDirectScrollIntentHandled(conversationID: conversationID)
                            chatStateRevision &+= 1
                        }
                    }
                }
            }

            if showScrollSettleLoadingOverlay {
                ProgressView()
                    .tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.96))
                    .transition(.opacity)
            }

            ChatKeyboardDismissZone(
                height: ChatKeyboardDismissZoneMetrics.topStripHeight,
                isActive: ChatComposerKeyboardDismiss.isActive(
                    keyboardOverlap: chatKeyboardOverlap,
                    composerFocused: isComposerFocused
                ),
                onDismiss: {
                    ChatComposerKeyboardDismiss.dismiss(composerFocused: $isComposerFocused)
                }
            )
            .frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private func jumpToQuotedMessage(_ messageId: String) {
        isComposerFocused = false
        guard let conversationID = chatState.conversation?.id else { return }
        let trimmed = messageId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if chatState.messages.contains(where: { $0.id == trimmed }) {
            appState.chatStore.requestDirectScrollToMessage(conversationID: conversationID, messageID: trimmed)
            chatStateRevision &+= 1
            return
        }
        guard chatState.hasMoreOlderMessages, !chatState.isLoadingOlderMessages else { return }
        Task {
            guard let before = appState.chatStore.prepareDirectOlderMessagesLoad(conversationID: conversationID) else { return }
            chatStateRevision &+= 1
            do {
                let pageLimit = 50
                let older = try await APIClient.shared.fetchDirectMessages(
                    conversationId: conversationID,
                    limit: pageLimit,
                    before: before
                )
                await MainActor.run {
                    appState.chatStore.finishDirectOlderMessagesLoad(
                        conversationID: conversationID,
                        olderMessages: older,
                        pageLimit: pageLimit
                    )
                    chatStateRevision &+= 1
                    if chatState.messages.contains(where: { $0.id == trimmed }) {
                        appState.chatStore.requestDirectScrollToMessage(
                            conversationID: conversationID,
                            messageID: trimmed
                        )
                        chatStateRevision &+= 1
                    }
                }
            } catch {
                await MainActor.run {
                    appState.chatStore.failDirectOlderMessagesLoad(conversationID: conversationID)
                    chatStateRevision &+= 1
                }
            }
        }
    }

    private func requestDirectScrollViewDidAppear() {
        guard let conversationID = chatState.conversation?.id, !chatState.messages.isEmpty else { return }
        let preserveOffset = scrollViewHasAppearedOnce
        scrollViewHasAppearedOnce = true
        appState.chatStore.directScrollViewDidAppear(
            conversationID: conversationID,
            preserveScrollViewOffset: preserveOffset,
            scrollViewInstanceId: scrollViewInstanceId
        )
        chatStateRevision &+= 1
    }

    private func updateBottomPinning() {
        guard let conversationID = chatState.conversation?.id else { return }
        let isPinned = ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: scrollDistanceFromBottom)
        appState.chatStore.updateDirectBottomVisibility(
            conversationID: conversationID,
            isPinned: isPinned,
            scrollViewInstanceId: scrollViewInstanceId,
            isScrollUserInteracting: isScrollUserInteracting
        )
    }

    private func loadOlderDirectMessagesIfNeeded(conversationID: String, thresholdMessageID: String) {
        guard chatState.scrollState.didInitialScrollToBottom,
              chatState.scrollState.pendingScrollIntent == nil,
              chatState.scrollState.hasUserScrollAnchor else { return }
        guard chatState.messages.prefix(5).contains(where: { $0.id == thresholdMessageID }) else { return }
        guard let before = appState.chatStore.prepareDirectOlderMessagesLoad(conversationID: conversationID) else { return }
        Task {
            do {
                let pageLimit = 50
                let older = try await APIClient.shared.fetchDirectMessages(
                    conversationId: conversationID,
                    limit: pageLimit,
                    before: before
                )
                await MainActor.run {
                    appState.chatStore.finishDirectOlderMessagesLoad(
                        conversationID: conversationID,
                        olderMessages: older,
                        pageLimit: pageLimit
                    )
                }
            } catch {
                await MainActor.run {
                    appState.chatStore.failDirectOlderMessagesLoad(conversationID: conversationID)
                }
            }
        }
    }

    private func executePendingScrollIntent(proxy: ScrollViewProxy) async {
        guard let conversationID = chatState.conversation?.id else { return }
        let transcriptVisible = !isHidingChatTranscriptForScrollSettle
        guard let intent = appState.chatStore.directValidatePendingScrollIntent(
            conversationID: conversationID,
            transcriptVisible: transcriptVisible
        ), intent != .none else {
            if chatState.scrollState.isSettlingScrollPosition {
                appState.chatStore.clearDirectScrollSettle(conversationID: conversationID)
                chatStateRevision &+= 1
            }
            return
        }
        appState.chatStore.beginDirectProgrammaticScroll(conversationID: conversationID)
        defer { appState.chatStore.endDirectProgrammaticScroll(conversationID: conversationID) }

        await Task.yield()
        let requiresStableSettle = isHidingChatTranscriptForScrollSettle
        let intentSource = chatState.scrollState.pendingScrollIntentSource
        let context = ChatScrollIntentExecutor.Context(
            intent: intent,
            bottomSentinelID: bottomSentinelID,
            newestMessageID: chatState.messages.last?.id,
            anchorMessageIDs: Set(chatState.messages.map(\.id)),
            requiresStableSettle: requiresStableSettle,
            isPinnedToBottom: chatState.scrollState.isPinnedToBottom,
            intentSource: intentSource,
            scrollViewHandle: chatScrollHandle,
            distanceFromBottom: { scrollDistanceFromBottom },
            isLayoutReady: { isScrollLayoutReady }
        )
        let outcome = await ChatScrollIntentExecutor.execute(context: context, proxy: proxy)
        if outcome.shouldMarkHandled {
            appState.chatStore.markDirectScrollIntentHandled(conversationID: conversationID)
            chatStateRevision &+= 1
        } else {
            appState.chatStore.clearDirectScrollSettle(conversationID: conversationID)
            chatStateRevision &+= 1
            if !chatState.scrollState.didInitialScrollToBottom {
                appState.chatStore.markDirectScrollIntentHandled(conversationID: conversationID)
                chatStateRevision &+= 1
            }
        }
    }

    private func clearDirectLongPressChrome() {
        longPressMessage = nil
        longPressChromeLiftY = 0
        directMessageFrames = [:]
    }

    private func beginDirectReply(to message: DirectMessageDTO) {
        let isFromMe = message.senderUserId == appState.currentUserID
        let name = isFromMe
            ? (appState.allUsers.first(where: { $0.id == appState.currentUserID })?.displayName ?? "You")
            : recipientName
        chatState.replyDraft.messageId = message.id
        chatState.replyDraft.authorName = name
        let trimmed = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        chatState.replyDraft.snippet = trimmed.isEmpty && message.imageUrl != nil
            ? ChatImageURLDisplay.replySnippet(for: message.imageUrl)
            : trimmed
        if isFromMe {
            chatState.replyDraft.avatarURL = appState.allUsers.first(where: { $0.id == appState.currentUserID })?.avatarUrl
        } else {
            chatState.replyDraft.avatarURL = message.sender?.avatarUrl
        }
        Task { @MainActor in
            await Task.yield()
            isComposerFocused = true
        }
    }

    private func postDirectReaction(emoji: String, for messageId: String) async {
        guard let conversation = chatState.conversation else { return }
        do {
            let updated = try await APIClient.shared.setDirectMessageReaction(
                conversationId: conversation.id,
                messageId: messageId,
                emoji: emoji
            )
            upsertMessage(updated)
            clearDirectLongPressChrome()
        } catch {
            clearDirectLongPressChrome()
        }
    }

    private func directMessageRow(_ message: DirectMessageDTO) -> some View {
        let pendingUpload = videoUploads.pending(for: message.clientMessageId)
        return DirectMessageRow(
            message: message,
            recipientName: recipientName,
            recipientAvatarURL: recipientAvatarURL,
            recipientAccentColor: recipientAccentColor,
            currentUserID: appState.currentUserID,
            localVideoThumbnail: pendingUpload?.thumbnail,
            videoUploadProgress: pendingUpload?.progress,
            videoUploadPhase: pendingUpload?.phase,
            longPressMessage: $longPressMessage,
            reactionsDetailMessage: $reactionsDetailMessage,
            suppressAttachmentNavigationForMessageID: $suppressAttachmentNavigationForMessageID,
            longPressChromeLiftY: $longPressChromeLiftY,
            directActiveSheet: $directActiveSheet,
            onAvatarTap: { directActiveSheet = .profile(recipientFriendLocation) },
            onDoubleTapHeart: {
                Task {
                    await postDirectReaction(
                        emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                        for: message.id
                    )
                }
            },
            onJumpToQuotedMessage: { targetId in
                jumpToQuotedMessage(targetId)
            },
            onRemovePendingMessage: { messageId in
                guard let message = chatState.messages.first(where: { $0.id == messageId }),
                      let clientMessageId = message.clientMessageId,
                      let conversationId = chatState.conversation?.id else { return }
                ChatVideoUploadCoordinator.shared.cancel(clientMessageId: clientMessageId)
                appState.chatStore.removePendingDirectMessage(conversationID: conversationId, clientMessageId: clientMessageId)
            },
            reportsFrame: false,
            eventCard: { suppress in
                eventAttachmentCard(
                    message.eventAttachment,
                    message: message,
                    cardWidth: ChatMessageTextBubble.standardLayoutWidth,
                    suppressNavigation: suppress,
                    onNavigate: {
                        if let attachment = message.eventAttachment {
                            directActiveSheet = .event(attachment)
                        }
                    }
                )
            },
            placeCard: { suppress in
                placeAttachmentCard(
                    message.placeAttachment,
                    message: message,
                    cardWidth: ChatMessageTextBubble.standardLayoutWidth,
                    suppressNavigation: suppress,
                    onNavigate: {
                        if let attachment = message.placeAttachment {
                            openSharedPlaceOnMap(attachment: attachment, messageId: message.id)
                        }
                    }
                )
            }
        )
    }

    private func directReactionParticipantName(userId: String, message: DirectMessageDTO) -> String {
        if let hydrated = message.reactions.first(where: { $0.userId == userId })?.user?.displayName,
           let name = SquadChatDisplayName.normalized(hydrated) {
            return name
        }
        if userId == appState.currentUserID {
            return appState.allUsers.first(where: { $0.id == userId })?.displayName ?? "You"
        }
        if userId == recipientUserID {
            return recipientName
        }
        return SquadChatDisplayName.resolveSquadMemberDisplayName(
            userId: userId,
            contacts: appState.allUsers,
            currentUserID: appState.currentUserID,
            fallback: recipientName
        )
    }

    private func dmProfileDetent(for member: FriendLocation) -> PresentationDetent {
        let rowCount = max(1, appState.sharedSquads(with: member.id).count)
        let height = min(620, 354 + rowCount * 62)
        return .height(CGFloat(height))
    }

    private func blockRecipient() async {
        if await appState.blockUser(recipientUserID) {
            dismiss()
        }
    }

    private func unblockRecipient() async {
        _ = await appState.unblockUser(recipientUserID)
    }

    private func syncDirectThreadVisibilityForRootTab(_ circlesTabSelected: Bool) {
        guard let conversationID = chatState.conversation?.id else { return }
        if circlesTabSelected {
            appState.setActiveDirectConversation(conversationID: conversationID, otherUserID: recipientUserID)
        } else {
            appState.chatStore.resetDirectScrollSession(conversationID: conversationID, reason: "rootTabObscured")
            appState.setActiveDirectConversation(conversationID: nil, otherUserID: nil)
        }
    }

    private func loadConversation() async {
        chatState.errorMessage = nil
        if let pre = prefetchedConversation {
            chatState.conversation = pre
            appState.registerDirectConversation(pre)
            appState.setActiveDirectConversation(conversationID: pre.id, otherUserID: recipientUserID)

            let conversationId = pre.id
            if let snapshot = appState.cachedDirectMessages(conversationID: conversationId), !snapshot.isEmpty {
                chatState.messages = snapshot.sorted { $0.createdAt < $1.createdAt }
                chatState.isLoading = false
            } else {
                chatState.isLoading = true
            }

            do {
                let fresh = try await APIClient.shared.fetchDirectMessages(conversationId: conversationId)
                var byID = Dictionary(uniqueKeysWithValues: chatState.messages.map { ($0.id, $0) })
                for message in fresh {
                    byID[message.id] = message
                }
                chatState.messages = Array(byID.values).sorted { $0.createdAt < $1.createdAt }
                chatState.isLoading = false
                chatState.errorMessage = nil
                appState.replaceDirectMessageTranscript(conversationID: conversationId, messages: chatState.messages)
                appState.chatStore.directConversationBecameVisible(conversationID: conversationId, source: .loadConversation)
            } catch {
                chatState.isLoading = false
                if chatState.messages.isEmpty {
                    chatState.errorMessage = "Could not load messages."
                }
            }
            return
        }

        do {
            let conversation = try await APIClient.shared.getOrCreateDirectConversation(recipientUserId: recipientUserID)
            chatState.conversation = conversation
            appState.registerDirectConversation(conversation)
            appState.setActiveDirectConversation(conversationID: conversation.id, otherUserID: recipientUserID)

            let conversationId = conversation.id
            if let snapshot = appState.cachedDirectMessages(conversationID: conversationId), !snapshot.isEmpty {
                chatState.messages = snapshot.sorted { $0.createdAt < $1.createdAt }
                chatState.isLoading = false
            } else {
                chatState.isLoading = true
            }

            let fresh = try await APIClient.shared.fetchDirectMessages(conversationId: conversation.id)
            var byID = Dictionary(uniqueKeysWithValues: chatState.messages.map { ($0.id, $0) })
            for message in fresh {
                byID[message.id] = message
            }
            chatState.messages = byID.values.sorted { $0.createdAt < $1.createdAt }
            appState.replaceDirectMessageTranscript(conversationID: conversation.id, messages: chatState.messages)
            chatState.isLoading = false
            appState.chatStore.directConversationBecameVisible(conversationID: conversation.id, source: .loadConversation)
        } catch {
            chatState.isLoading = false
            chatState.errorMessage = chatState.messages.isEmpty ? "You can only message people who share a Squad with you." : nil
        }
    }

    private func upsertMessage(_ message: DirectMessageDTO) {
        appState.upsertDirectMessageTranscript(message)
    }

    private func submitDmChatAttachmentRsvp(eventId: String, status: String) {
        Task { @MainActor in
            chatRsvpSubmittingEventId = eventId
            await appState.setEventRsvp(eventID: eventId, status: status)
            chatRsvpSubmittingEventId = nil
        }
    }

    @ViewBuilder
    private func eventAttachmentCard(
        _ attachment: CircleChatMessageDTO.EventAttachmentDTO?,
        message: DirectMessageDTO,
        cardWidth: CGFloat = 320,
        suppressNavigation: Bool = false,
        onNavigate: @escaping () -> Void
    ) -> some View {
        if let attachment {
            ChatEventAttachmentPreviewCard(
                attachment: attachment,
                resolvedEvent: eventForAttachment(attachment),
                cardWidth: cardWidth,
                suppressNavigation: suppressNavigation,
                rsvpSubmitting: chatRsvpSubmittingEventId == attachment.eventId,
                meUser: appState.allUsers.first(where: { $0.id == appState.currentUserID }),
                onRsvp: { status in
                    submitDmChatAttachmentRsvp(eventId: attachment.eventId, status: status)
                },
                onNavigate: onNavigate,
                messageId: message.id,
                onLongPress: {
                    ChatMessageActionFeedback.dismissKeyboard()
                    withAnimation(.easeOut(duration: 0.12)) {
                        longPressChromeLiftY = 0
                        longPressMessage = message
                    }
                    suppressAttachmentNavigationForMessageID = message.id
                    let id = message.id
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                        if suppressAttachmentNavigationForMessageID == id {
                            suppressAttachmentNavigationForMessageID = nil
                        }
                    }
                },
                onDoubleTapHeart: {
                    Task {
                        await postDirectReaction(
                            emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                            for: message.id
                        )
                    }
                }
            )
        }
    }

    private func openSharedPlaceOnMap(
        attachment: CircleChatMessageDTO.PlaceAttachmentDTO,
        messageId: String
    ) {
        guard !attachment.isParentDeleted else { return }
        let snapshot = attachment.savedPlaceSnapshot(fallbackID: "chat:\(messageId)")
        appState.requestMapTabCenteredOn(
            latitude: attachment.latitude,
            longitude: attachment.longitude,
            savedPlaceID: attachment.placeId,
            savedPlaceSnapshot: snapshot
        )
    }

    @ViewBuilder
    private func placeAttachmentCard(
        _ attachment: CircleChatMessageDTO.PlaceAttachmentDTO?,
        message: DirectMessageDTO,
        cardWidth: CGFloat = 320,
        suppressNavigation: Bool = false,
        onNavigate: @escaping () -> Void
    ) -> some View {
        if let attachment {
            let senderName = SquadChatDisplayName.normalized(message.sender?.displayName) ?? recipientName
            let firstName = senderName.split(separator: " ").first.map(String.init) ?? senderName
            if attachment.isParentDeleted {
                ChatUnavailableShareAttachmentCard(
                    kind: .place,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    cardWidth: cardWidth
                )
            } else {
                ChatPlaceAttachmentPreviewCard(
                    attachment: attachment,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    messageId: message.id,
                    cardWidth: cardWidth,
                    suppressNavigation: suppressNavigation,
                    onLongPress: {
                        ChatMessageActionFeedback.dismissKeyboard()
                        withAnimation(.easeOut(duration: 0.12)) {
                            longPressChromeLiftY = 0
                            longPressMessage = message
                        }
                        suppressAttachmentNavigationForMessageID = message.id
                        let id = message.id
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                            if suppressAttachmentNavigationForMessageID == id {
                                suppressAttachmentNavigationForMessageID = nil
                            }
                        }
                    },
                    onDoubleTapHeart: {
                        Task {
                            await postDirectReaction(
                                emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                                for: message.id
                            )
                        }
                    },
                    onNavigate: onNavigate
                )
            }
        }
    }

    private func eventForAttachment(_ attachment: CircleChatMessageDTO.EventAttachmentDTO) -> EventDTO? {
        appState.resolvedEventForChatAttachment(eventId: attachment.eventId, squadEvents: [])
    }

    private func eventDetailForAttachment(_ attachment: CircleChatMessageDTO.EventAttachmentDTO) -> some View {
        let event = eventForAttachment(attachment)
        return EventDetailView(
            event: event ?? EventDTO(
                id: attachment.eventId,
                slug: nil,
                visibility: attachment.visibility,
                circleId: attachment.circleId,
                name: attachment.name ?? "Event",
                description: nil,
                startsAt: attachment.startsAt ?? Date(),
                endsAt: nil,
                address: EventDTO.AddressDTO(
                    label: attachment.addressLabel,
                    street1: nil,
                    street2: nil,
                    city: nil,
                    region: nil,
                    postalCode: nil,
                    country: nil
                ),
                location: nil,
                bannerImage: attachment.bannerImageUrl.map { EventDTO.BannerImageDTO(url: $0, aspectRatio: nil) },
                rsvpCounts: nil,
                contactsGoing: [],
                currentUserRsvp: nil,
                currentUserCheckIn: nil
            ),
            sourceCircleID: attachment.circleId
        )
        .environmentObject(appState)
        .environmentObject(locationService)
    }

}
