import Foundation

/// Payload for WebSocket `user.profile.updated` (`profile` object on the frame).
struct UserProfileRealtimePatchDTO: Decodable {
    let id: String
    let displayName: String
    let mapAccentKey: String?
    let avatarUrl: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case displayName
        case mapAccentKey
        case avatarUrl
    }
}

extension UserDTO {
    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> UserDTO? {
        guard id == patch.id else { return nil }
        return UserDTO(
            id: id,
            displayName: patch.displayName,
            handle: handle,
            avatarUrl: patch.avatarUrl,
            mapAccentKey: patch.mapAccentKey,
            phoneNumber: phoneNumber,
            vehicle: vehicle,
            lastPresenceAt: lastPresenceAt,
            autoEventCheckInEnabled: autoEventCheckInEnabled,
            sharingSafetyDisclaimerAcknowledged: sharingSafetyDisclaimerAcknowledged,
            showPublicGoingEventsOnProfile: showPublicGoingEventsOnProfile,
            driveStatsVisibility: driveStatsVisibility,
            routesAccessEnabled: routesAccessEnabled,
            blockedUserIds: blockedUserIds,
            timeZone: timeZone,
            timeZoneUpdatedAt: timeZoneUpdatedAt
        )
    }
}

extension CircleChatMessageDTO.SenderDTO {
    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> CircleChatMessageDTO.SenderDTO {
        guard id == patch.id else { return self }
        return CircleChatMessageDTO.SenderDTO(
            id: id,
            displayName: patch.displayName,
            avatarUrl: patch.avatarUrl,
            mapAccentKey: patch.mapAccentKey
        )
    }
}

extension CircleChatMessageDTO.ReplyPreviewDTO {
    init(
        id: String,
        body: String,
        imageUrl: String?,
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO? = nil,
        messageType: String?,
        systemKind: String?,
        senderUserId: String?,
        sender: CircleChatMessageDTO.SenderDTO?,
        deletedAt: Date?,
        createdAt: Date?
    ) {
        self.id = id
        self.body = body
        self.imageUrl = imageUrl
        self.videoAttachment = videoAttachment
        self.messageType = messageType
        self.systemKind = systemKind
        self.senderUserId = senderUserId
        self.sender = sender
        self.deletedAt = deletedAt
        self.createdAt = createdAt
    }

    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> CircleChatMessageDTO.ReplyPreviewDTO {
        let nextSender = sender?.applyingProfilePatch(patch) ?? sender
        return CircleChatMessageDTO.ReplyPreviewDTO(
            id: id,
            body: body,
            imageUrl: imageUrl,
            videoAttachment: videoAttachment,
            messageType: messageType,
            systemKind: systemKind,
            senderUserId: senderUserId,
            sender: nextSender,
            deletedAt: deletedAt,
            createdAt: createdAt
        )
    }
}

extension CircleChatMessageDTO.MessageReactionDTO {
    init(userId: String, emoji: String, user: CircleChatMessageDTO.SenderDTO?, createdAt: Date?) {
        self.userId = userId
        self.emoji = emoji
        self.user = user
        self.createdAt = createdAt
    }

    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> CircleChatMessageDTO.MessageReactionDTO {
        let nextUser = user?.applyingProfilePatch(patch) ?? user
        return CircleChatMessageDTO.MessageReactionDTO(
            userId: userId,
            emoji: emoji,
            user: nextUser,
            createdAt: createdAt
        )
    }
}

extension CircleChatMessageDTO {
    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> CircleChatMessageDTO {
        CircleChatMessageDTO(
            id: id,
            circleId: circleId,
            senderUserId: senderUserId,
            sender: sender?.applyingProfilePatch(patch),
            body: body,
            messageType: messageType,
            systemKind: systemKind,
            clientMessageId: clientMessageId,
            linkPreview: linkPreview,
            eventAttachment: eventAttachment,
            driveAttachment: driveAttachment,
            placeAttachment: placeAttachment,
            richAttachmentType: richAttachmentType,
            imageUrl: imageUrl,
            videoAttachment: videoAttachment,
            replyToMessageId: replyToMessageId,
            replyTo: replyTo?.applyingProfilePatch(patch),
            reactions: reactions.map { $0.applyingProfilePatch(patch) },
            mentions: mentions,
            createdAt: createdAt,
            deletedAt: deletedAt,
            editedAt: editedAt,
            updatedAt: updatedAt
        )
    }
}

extension DirectConversationDTO.UserSummaryDTO {
    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> DirectConversationDTO.UserSummaryDTO {
        guard id == patch.id else { return self }
        return DirectConversationDTO.UserSummaryDTO(
            id: id,
            displayName: patch.displayName,
            avatarUrl: patch.avatarUrl,
            mapAccentKey: patch.mapAccentKey
        )
    }
}

extension DirectConversationDTO {
    init(
        id: String,
        participantUserIds: [String],
        otherUser: UserSummaryDTO?,
        lastMessageAt: Date?,
        conversationType: String? = nil,
        lastMessage: LastMessagePreviewDTO? = nil
    ) {
        self.id = id
        self.participantUserIds = participantUserIds
        self.otherUser = otherUser
        self.lastMessageAt = lastMessageAt
        self.conversationType = conversationType
        self.lastMessage = lastMessage
    }

    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> DirectConversationDTO {
        DirectConversationDTO(
            id: id,
            participantUserIds: participantUserIds,
            otherUser: otherUser?.applyingProfilePatch(patch),
            lastMessageAt: lastMessageAt,
            conversationType: conversationType,
            lastMessage: lastMessage
        )
    }
}

extension DirectMessageDTO {
    func applyingProfilePatch(_ patch: UserProfileRealtimePatchDTO) -> DirectMessageDTO {
        DirectMessageDTO(
            id: id,
            conversationId: conversationId,
            senderUserId: senderUserId,
            sender: sender?.applyingProfilePatch(patch),
            body: body,
            imageUrl: imageUrl,
            videoAttachment: videoAttachment,
            clientMessageId: clientMessageId,
            linkPreview: linkPreview,
            eventAttachment: eventAttachment,
            replyToMessageId: replyToMessageId,
            replyTo: replyTo?.applyingProfilePatch(patch),
            reactions: reactions.map { $0.applyingProfilePatch(patch) },
            createdAt: createdAt,
            messageType: messageType,
            deletedAt: deletedAt,
            editedAt: editedAt,
            updatedAt: updatedAt
        )
    }
}
