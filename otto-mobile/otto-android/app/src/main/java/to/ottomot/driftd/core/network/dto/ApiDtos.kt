package to.ottomot.driftd.core.network.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

// --- Circles ---

data class CircleMemberDto(
    val userId: String,
    val role: String,
)

data class CircleDto(
    @SerializedName("_id") val id: String,
    val name: String,
    val description: String?,
    val ownerId: String,
    val members: List<CircleMemberDto>?,
    val photoUrl: String?,
)

data class CircleMembersUpdatedDto(
    val circle: CircleDto?,
    val users: List<UserDto>?,
)

data class CircleInviteDto(
    @SerializedName("_id") val id: String,
    val circleId: String,
    val phoneNumber: String,
    val invitedByUserId: String,
    val status: String?,
)

// --- Squad Grid (circle-scoped standings) ---

data class SquadGridLeaderDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val mapAccentKey: String?,
    val progressionTier: String?,
    val progressionLevel: Int?,
    val rank: Int,
    val value: Double,
)

data class SquadGridMetricDto(
    val key: String,
    val label: String,
    val subtitle: String,
    val unit: String,
    val leaders: List<SquadGridLeaderDto>?,
)

data class SquadGridResponseDto(
    val circleId: String,
    val range: String,
    val metrics: List<SquadGridMetricDto>?,
)

// --- Events ---

data class EventAddressDto(
    val label: String?,
    val street1: String?,
    val street2: String?,
    val city: String?,
    val region: String?,
    val postalCode: String?,
    val country: String?,
)

data class EventBannerImageDto(
    val url: String,
    val aspectRatio: Double?,
)

data class EventLocationDto(
    val type: String?,
    val coordinates: List<Double>?,
)

data class EventRsvpCountsDto(
    val interested: Int?,
    val going: Int?,
    val notGoing: Int?,
)

data class EventCheckInDto(
    val id: String,
    val eventId: String,
    val userId: String,
    val method: String,
    val checkedInAt: String?,
    val distanceMeters: Double?,
    val source: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class EventContactRsvpDto(
    val status: String?,
    val respondedAt: String?,
    val user: UserDto?,
)

data class EventAttachedSquadDto(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val addedByUserId: String?,
    val addedByDisplayName: String?,
)

data class AdminSquadDto(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val role: String?,
)

data class AdminSquadsResponseDto(
    val squads: List<AdminSquadDto>?,
)

data class SquadAssociationsResponseDto(
    val squads: List<EventAttachedSquadDto>?,
)

data class PatchSquadAssociationsRequestDto(
    val squadIds: List<String>,
)

data class EventDto(
    @SerializedName("_id") val id: String,
    val slug: String?,
    val visibility: String?,
    val eventType: String? = null,
    val circleId: String?,
    val createdByUserId: String?,
    val name: String,
    val description: String?,
    val startsAt: String?,
    val endsAt: String?,
    val address: EventAddressDto?,
    val location: EventLocationDto?,
    val bannerImage: EventBannerImageDto?,
    val rsvpCounts: EventRsvpCountsDto?,
    val contactsGoing: List<UserDto>?,
    val contactsRsvps: List<EventContactRsvpDto>?,
    val currentUserRsvp: String?,
    val currentUserCheckIn: EventCheckInDto?,
    val attachedSquads: List<EventAttachedSquadDto>? = null,
    val isOfficialForCircle: Boolean? = null,
    val timeZone: String? = null,
    val adminOnly: Boolean? = null,
)

/** Request body for `POST /api/events` (matches iOS `APIClient.createEvent`). */
data class CreateEventRequestDto(
    val name: String,
    val startsAt: String,
    val visibility: String,
    val eventType: String? = null,
    val circleId: String? = null,
    val endsAt: String? = null,
    val description: String? = null,
    val address: CreateEventAddressBodyDto? = null,
    val location: CreateEventLatLngBodyDto? = null,
)

data class CreateEventAddressBodyDto(
    val label: String? = null,
    val street1: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
)

data class CreateEventLatLngBodyDto(
    val lat: Double,
    val lng: Double,
)

data class PatchEventRequestDto(
    val name: String,
    val startsAt: String,
    val endsAt: String? = null,
    val description: String? = null,
    val address: CreateEventAddressBodyDto? = null,
    val location: CreateEventLatLngBodyDto? = null,
)

data class EventRsvpRequestDto(
    val status: String,
)

data class EventCheckInRequestDto(
    val method: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceMeters: Double? = null,
)

data class EventCheckInResultDto(
    val checkedIn: Boolean,
    val alreadyCheckedIn: Boolean,
    val checkIn: EventCheckInDto?,
    val event: EventDto?,
)

data class NextUpEventDismissalDto(
    val userId: String?,
    val squadId: String?,
    val circleId: String?,
    val eventId: String,
    val dismissedAt: String?,
    val dismissedContext: String,
)

data class NextUpEventDismissalsResponseDto(
    val dismissals: List<NextUpEventDismissalDto>?,
)

data class NextUpEventDismissalResponseDto(
    val dismissal: NextUpEventDismissalDto?,
)

data class NextUpEventDismissalRequestDto(
    val dismissedContext: String,
)

/** Subset of `GET /api/public/m/:userId` — Gson ignores other JSON keys. */
data class PublicMemberProfileDto(
    val publicGoingEvents: List<PublicGoingEventDto>?,
)

data class PublicGoingEventDto(
    @SerializedName("_id") val id: String,
    val slug: String?,
    val name: String,
    val startsAt: String?,
    val addressLabel: String?,
    val bannerImageUrl: String?,
)

/** Minimal [EventDto] for event-detail navigation from profile public-going rows (iOS `asEventStub`). */
fun PublicGoingEventDto.asEventStub(): EventDto =
    EventDto(
        id = id,
        slug = slug,
        visibility = "public",
        circleId = null,
        createdByUserId = null,
        name = name,
        description = null,
        startsAt = startsAt,
        endsAt = null,
        address = addressLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { label ->
            EventAddressDto(
                label = label,
                street1 = null,
                street2 = null,
                city = null,
                region = null,
                postalCode = null,
                country = null,
            )
        },
        location = null,
        bannerImage = bannerImageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
            EventBannerImageDto(url = url, aspectRatio = null)
        },
        rsvpCounts = null,
        contactsGoing = emptyList(),
        contactsRsvps = null,
        currentUserRsvp = null,
        currentUserCheckIn = null,
        attachedSquads = null,
        isOfficialForCircle = null,
    )

// --- Garage ---

data class GarageCarPhotoDto(
    val url: String,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Double?,
)

data class GarageCarDto(
    @SerializedName("_id") val id: String,
    val nickname: String?,
    val make: String,
    val makeId: String? = null,
    val model: String,
    val year: Int?,
    val color: String?,
    val logoSlug: String? = null,
    val isPrimary: Boolean?,
    val sortOrder: Int? = null,
    val photo: GarageCarPhotoDto?,
)

data class GarageReorderRequestDto(
    val orderedCarIds: List<String>,
)

// --- Drives ---

data class RoutePointDto(
    val lat: Double,
    val lng: Double,
    @SerializedName("type") val markerType: String? = null,
)

data class DriveRouteDto(
    @SerializedName("_id") val id: String,
    val name: String? = null,
    val points: List<RoutePointDto>? = null,
    val roadCoordinates: List<RoutePointDto>? = null,
    val distanceMeters: Double? = null,
    val etaSeconds: Double? = null,
    val totalCheckpoints: Int? = null,
    val completedWaypointIndexes: List<Int>? = null,
    val completedCheckpoints: Int? = null,
    val completionReason: String? = null,
)

data class DriveDto(
    @SerializedName("_id") val id: String,
    val userId: String,
    val circleId: String?,
    val garageCarId: String? = null,
    val title: String? = null,
    val status: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val distanceMeters: Double? = null,
    val maxSpeedMph: Double? = null,
    val avgSpeedMph: Double? = null,
    val pointsCount: Int? = null,
    val sharingAudience: String? = null,
    val route: DriveRouteDto? = null,
    val garageCar: GarageCarDto? = null,
)

data class PatchDriveRequestDto(
    val garageCarId: String? = null,
    val title: String? = null,
)

data class DrivePathPointDto(
    val lat: Double,
    val lng: Double,
    val speedMph: Double,
    val capturedAt: String? = null,
)

data class DrivePathPointsResponseDto(
    val driveId: String,
    val points: List<DrivePathPointDto>,
)

data class PatchRouteRequestDto(
    val name: String,
    val points: List<RoutePointDto>,
    val roadCoordinates: List<RoutePointDto>,
    val distanceMeters: Double,
    val etaSeconds: Double,
)

data class CreateRouteRequestDto(
    val name: String,
    val points: List<RoutePointDto>,
    val roadCoordinates: List<RoutePointDto>,
    val distanceMeters: Double,
    val etaSeconds: Double,
)

/** User saved routes (`GET /api/routes`). */
data class SavedRouteDto(
    @SerializedName("_id") val id: String,
    val createdByUserId: String,
    val name: String,
    val points: List<RoutePointDto>? = null,
    val roadCoordinates: List<RoutePointDto>? = null,
    val distanceMeters: Double? = null,
    val etaSeconds: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Squad-shared planned route overlays (`/api/drive-lines`). */
data class DriveLineCoordinateDto(
    val lat: Double,
    val lng: Double,
    val type: String? = null,
)

data class DriveLineDto(
    @SerializedName("_id") val id: String,
    val circleId: String,
    val name: String? = null,
    val colorKey: String? = null,
    val points: List<DriveLineCoordinateDto>? = null,
    val roadCoordinates: List<DriveLineCoordinateDto>? = null,
    val distanceMeters: Double? = null,
    val etaSeconds: Double? = null,
)

// --- Presence ---

data class PresenceCircleResponseDto(
    val members: List<PresenceMemberDto>?,
)

data class PresenceMemberDto(
    val userId: String,
    val circleId: String,
    val isActive: Boolean,
    /** When false, peer backgrounded / left the app. Omitted/null = in-app (older servers). */
    val inApp: Boolean? = null,
    val speedMph: Double?,
    /** Mirrors backend / iOS (`driving` | `walking` | `unknown`). */
    val movementMode: String? = null,
    val lat: Double?,
    val lng: Double?,
    val updatedAt: String?,
    val carId: String? = null,
    val logoSlug: String? = null,
)

// --- Driving stats ---

data class ProfileProgressionDto(
    val points: Int?,
    val level: Int?,
    val tierId: String?,
    val tierName: String?,
    val levelImageName: String?,
    val currentLevelStartPoints: Int?,
    val nextLevelAt: Int?,
    val pointsIntoLevel: Int?,
    val pointsRequiredForLevel: Int?,
    val progress: Double?,
    val isMaxLevel: Boolean?,
)

data class DrivingStatsDto(
    val userId: String,
    val driveCount: Int,
    val totalMilesDriven: Double,
    val totalDriveTimeSeconds: Double,
    val avgSpeedMph: Double,
    val topSpeedMph: Double,
    val accelerationScore: Int?,
    val lastDriveAt: String?,
    val circleSharingSeconds: Double,
    val publicSharingSeconds: Double,
    val milesDrivenWhileSharing: Double,
    val mostActiveTimeOfDay: String?,
    val eventsAttended: Int,
    /** When false, this viewer must not show real stats / progression (server redacts). Null = legacy visible. */
    val driveStatsVisible: Boolean? = null,
    val progression: ProfileProgressionDto?,
)

data class CreateCircleRequestDto(
    val name: String,
    val description: String? = null,
    val isPrivate: Boolean? = null,
)

data class PatchCircleRequestDto(
    val name: String? = null,
)

/** POST `/api/circles/:id/leave` — either remaining roster circle payload or dissolution markers. */
data class LeaveCircleResponseDto(
    val deleted: Boolean? = null,
    val circleId: String? = null,
    val circle: CircleDto? = null,
)

data class InviteCircleByPhoneDto(
    val phoneNumber: String,
)

data class AddCircleMemberRequestDto(
    val userId: String,
    val role: String = "member",
)

data class PatchCircleMemberRoleRequestDto(
    val role: String,
)

data class InviteLinkCreateRequestDto(
    val circleId: String,
    val expiresInDays: Int? = null,
    /** When set, mints a single-use squad signup code for SMS to this phone (iOS parity). */
    val phoneNumber: String? = null,
)

data class InviteLinkCreateResponseDto(
    val token: String,
    val url: String?,
    val circle: InviteLinkCircleStubDto?,
    val remainingUses: Int? = null,
    val personalRemainingUses: Int? = null,
)

data class SignupInviteBalanceDto(
    val remainingUses: Int,
    val maxUses: Int,
    val usesCount: Int,
    val invitesPerLevelUp: Int? = null,
    val nextLevelDisplayName: String? = null,
)

data class InviteLinkCircleStubDto(
    val id: String,
    val name: String,
)

data class InviteLinkResolveDto(
    val token: String,
    val circle: InviteLinkResolvedCircleDto?,
    val invitedBy: InviteLinkInviterStubDto?,
)

data class InviteLinkResolvedCircleDto(
    val id: String,
    val name: String,
    val description: String?,
)

data class InviteLinkInviterStubDto(
    val id: String,
    val displayName: String,
)

data class InviteLinkAcceptDto(
    val ok: Boolean?,
    val circleId: String?,
    val circleName: String?,
)

data class CircleInviteRespondRequestDto(
    val action: String,
)

data class CircleInviteRespondDto(
    val ok: Boolean?,
    val status: String?,
    val circleId: String?,
)

/** Populated from `/api/circle-invites/me` entries (flexible JSON). */
data class MyPendingCircleInvite(
    val inviteId: String,
    val circleId: String,
    val circleName: String,
    val circleDescription: String?,
    val status: String?,
)

// --- Presence / drives ---

data class PresenceUpdateDto(
    val circleId: String,
    val isActive: Boolean,
    /** False when the app is backgrounded — drop "online" immediately for this circle. Null omits field (backward compatible). */
    val inApp: Boolean? = null,
    val carId: String? = null,
    val speedMph: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyMeters: Double? = null,
    val movementMode: String? = null,
    val capturedAt: String? = null,
    val trackDrivingStats: Boolean? = null,
)

data class PresencePostResponseDto(
    val ok: Boolean?,
    val presence: PresenceMemberDto?,
)

data class DriveStartDto(
    val circleId: String?,
    val sharingAudience: String?,
    val sharedCircleIds: List<String>?,
    val title: String?,
    val startTime: String?,
    val startLocation: DriveLocationPointDto?,
)

data class DriveLocationPointDto(
    val lat: Double,
    val lng: Double,
)

data class AppendDrivePointsDto(
    val points: List<DrivePointCaptureDto>,
)

data class DrivePointCaptureDto(
    val lat: Double,
    val lng: Double,
    val speedMph: Double?,
    val heading: Double?,
    val accuracyMeters: Double?,
    val capturedAt: String,
)

data class AppendDrivePointsResponseDto(
    val ok: Boolean?,
    val inserted: Int?,
    val pointsCount: Int?,
)

data class DriveEndDto(
    val endTime: String?,
    val distanceMeters: Double?,
    val maxSpeedMph: Double?,
    val avgSpeedMph: Double?,
    val endLocation: DriveLocationPointDto?,
)

// --- Squad chat ---

data class CircleChatSenderDto(
    @SerializedName("_id") val id: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val mapAccentKey: String?,
)

/** WebSocket `user.profile.updated` (`profile` on the frame). */
data class UserProfileRealtimeDto(
    @SerializedName("_id") val id: String,
    val displayName: String,
    val mapAccentKey: String? = null,
    val avatarUrl: String? = null,
)

fun UserDto.withPatchedProfile(p: UserProfileRealtimeDto): UserDto {
    if (id != p.id) return this
    return copy(displayName = p.displayName, mapAccentKey = p.mapAccentKey, avatarUrl = p.avatarUrl)
}

fun CircleChatSenderDto?.patchForUser(p: UserProfileRealtimeDto): CircleChatSenderDto? {
    val s = this ?: return null
    if ((s.id ?: "").trim() != p.id) return s
    return s.copy(displayName = p.displayName, avatarUrl = p.avatarUrl, mapAccentKey = p.mapAccentKey)
}

fun ChatReplyPreviewDto.withPatchedProfile(p: UserProfileRealtimeDto): ChatReplyPreviewDto =
    copy(sender = sender.patchForUser(p))

fun ChatMessageReactionDto.withPatchedProfile(p: UserProfileRealtimeDto): ChatMessageReactionDto =
    copy(user = user.patchForUser(p))

fun CircleChatMessageDto.withPatchedProfile(p: UserProfileRealtimeDto): CircleChatMessageDto =
    copy(
        sender = sender.patchForUser(p),
        replyTo = replyTo?.withPatchedProfile(p),
        reactions = reactions.map { it.withPatchedProfile(p) },
    )

fun DirectMessageDto.withPatchedProfile(p: UserProfileRealtimeDto): DirectMessageDto =
    copy(
        sender = sender.patchForUser(p),
        replyTo = replyTo?.withPatchedProfile(p),
        reactions = reactions.map { it.withPatchedProfile(p) },
    )

fun DirectConversationDto.withPatchedProfile(p: UserProfileRealtimeDto): DirectConversationDto =
    copy(otherUser = otherUser.patchForUser(p))

fun SquadGridResponseDto.withPatchedLeaders(p: UserProfileRealtimeDto): SquadGridResponseDto =
    copy(
        metrics =
            metrics?.map { m ->
                m.copy(
                    leaders =
                        m.leaders?.map { l ->
                            if (l.userId == p.id) {
                                l.copy(
                                    displayName = p.displayName,
                                    avatarUrl = p.avatarUrl,
                                    mapAccentKey = p.mapAccentKey,
                                )
                            } else {
                                l
                            }
                        },
                )
            },
    )

data class CircleChatLinkPreviewDto(
    val status: String? = null,
    val url: String? = null,
    val finalUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val faviconUrl: String? = null,
)

data class CircleChatEventAttachmentDto(
    /** Always present once backend stored an attachment. */
    val eventId: String,
    /** Populated server-side later or merged client-side from [EventDto]. */
    val name: String? = null,
    val startsAt: String? = null,
    val addressLabel: String? = null,
    val bannerImageUrl: String? = null,
    val visibility: String? = null,
    val circleId: String? = null,
    val parentDeletedAt: String? = null,
) {
    val isParentDeleted: Boolean
        get() = !parentDeletedAt.isNullOrBlank()
}

data class CircleChatDriveRoutePointDto(
    val lat: Double,
    val lng: Double,
    val type: String? = null,
)

data class CircleChatDriveAttachmentDto(
    val driveId: String,
    val title: String? = null,
    val driveTypeLabel: String? = null,
    val completedAt: String? = null,
    val distanceMeters: Double? = null,
    val driveTimeSeconds: Int? = null,
    val markerCount: Int? = null,
    val routePoints: List<CircleChatDriveRoutePointDto> = emptyList(),
    val roadCoordinates: List<CircleChatDriveRoutePointDto> = emptyList(),
    val completedWaypointIndexes: List<Int> = emptyList(),
    val parentDeletedAt: String? = null,
    val mapPreviewUrl: String? = null,
) {
    val isParentDeleted: Boolean
        get() = !parentDeletedAt.isNullOrBlank()
}

data class CircleChatPlaceAttachmentDto(
    val placeId: String? = null,
    val name: String? = null,
    val addressSummary: String? = null,
    val latitude: Double,
    val longitude: Double,
    val parentDeletedAt: String? = null,
    val mapPreviewUrl: String? = null,
) {
    val isParentDeleted: Boolean
        get() = !parentDeletedAt.isNullOrBlank()

    fun displayTitle(): String {
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        addressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "Shared place"
    }

    /** Squad/DM push and foreground notification preview when the message has no text body. */
    fun notificationPreview(maxLength: Int = 120): String {
        if (isParentDeleted) return "Deleted place"
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedAddress = addressSummary?.trim()?.takeIf { it.isNotEmpty() }
        val preview =
            when {
                trimmedName != null && trimmedAddress != null -> "$trimmedName · $trimmedAddress"
                trimmedName != null -> trimmedName
                trimmedAddress != null -> trimmedAddress
                else -> "Shared place"
            }
        return if (preview.length > maxLength) preview.take(maxLength - 1) + "…" else preview
    }

    fun savedPlaceSnapshot(fallbackId: String): SavedPlaceDto =
        SavedPlaceDto(
            id = placeId?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackId,
            name = displayTitle(),
            latitude = latitude,
            longitude = longitude,
            placeKind = if (placeId.isNullOrBlank()) "coordinates" else "other",
            addressSummary = addressSummary,
        )
}

data class ChatMessageReactionDto(
    val userId: String,
    val emoji: String,
    val user: CircleChatSenderDto? = null,
    val createdAt: String? = null,
)

data class ChatReplyPreviewDto(
    @SerializedName("_id") val id: String,
    val body: String,
    val imageUrl: String? = null,
    val videoAttachment: ChatVideoAttachmentDto? = null,
    val messageType: String? = null,
    val systemKind: String? = null,
    val senderUserId: String? = null,
    val sender: CircleChatSenderDto? = null,
)

data class ChatVideoAttachmentDto(
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Double = 0.0,
    val width: Int = 1,
    val height: Int = 1,
    val mimeType: String = "video/mp4",
)

data class ChatVideoUploadUrlsRequestDto(
    val kind: String = "video",
    val videoContentType: String,
    val thumbnailContentType: String = "image/jpeg",
)

data class ChatVideoUploadPartDto(
    val uploadUrl: String,
    val fileUrl: String,
)

data class ChatVideoUploadUrlsResponseDto(
    val video: ChatVideoUploadPartDto,
    val thumbnail: ChatVideoUploadPartDto,
    val expiresIn: Int? = null,
)

data class SendCircleChatVideoMessageDto(
    val body: String,
    val clientMessageId: String,
    val videoAttachment: ChatVideoAttachmentDto,
    val replyToMessageId: String? = null,
    val mentions: List<CircleChatMentionSpanDto>? = null,
)

data class SendDirectChatVideoMessageDto(
    val body: String,
    val clientMessageId: String,
    val videoAttachment: ChatVideoAttachmentDto,
    val replyToMessageId: String? = null,
)

data class CircleChatMentionSpanDto(
    val userId: String,
    val start: Int,
    val length: Int,
)

data class CircleChatMessageDto(
    @SerializedName("_id") val id: String,
    val circleId: String,
    val senderUserId: String,
    val sender: CircleChatSenderDto?,
    val body: String,
    val messageType: String? = null,
    val systemKind: String? = null,
    val imageUrl: String? = null,
    val videoAttachment: ChatVideoAttachmentDto? = null,
    val clientMessageId: String? = null,
    val linkPreview: CircleChatLinkPreviewDto? = null,
    val eventAttachment: CircleChatEventAttachmentDto? = null,
    val driveAttachment: CircleChatDriveAttachmentDto? = null,
    val placeAttachment: CircleChatPlaceAttachmentDto? = null,
    val replyToMessageId: String? = null,
    val replyTo: ChatReplyPreviewDto? = null,
    val reactions: List<ChatMessageReactionDto> = emptyList(),
    /** UTF-16 spans in [body]; label resolved at display time from roster. */
    val mentions: List<CircleChatMentionSpanDto>? = null,
    val createdAt: String?,
    val updatedAt: String? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
)

data class CircleChatMessagesResponseDto(
    val messages: List<CircleChatMessageDto>,
)

data class CircleChatSingleMessageDto(
    val message: CircleChatMessageDto?,
)

data class SendCircleChatMessageDto(
    val body: String,
    val clientMessageId: String,
    val eventId: String? = null,
    val driveId: String? = null,
    val placeId: String? = null,
    val placeLatitude: Double? = null,
    val placeLongitude: Double? = null,
    val placeName: String? = null,
    val placeAddressSummary: String? = null,
    val replyToMessageId: String? = null,
    val mentions: List<CircleChatMentionSpanDto>? = null,
    val imageUrl: String? = null,
)

data class PatchCircleChatMessageDto(
    val body: String,
    val mentions: List<CircleChatMentionSpanDto>? = null,
)

data class ChatReactionEmojiBodyDto(val emoji: String)

// --- Direct chat ---

data class DirectLastMessagePreviewDto(
    val bodyPreview: String? = null,
    val senderUserId: String? = null,
    val hasImage: Boolean = false,
    val hasVideoAttachment: Boolean = false,
    val hasEventAttachment: Boolean = false,
)

data class DirectConversationDto(
    @SerializedName("_id") val id: String,
    val participantUserIds: List<String>?,
    val otherUser: CircleChatSenderDto?,
    val lastMessageAt: String?,
    val conversationType: String? = null,
    val lastMessage: DirectLastMessagePreviewDto? = null,
)

/** Inbox lists only threads where at least one message exists (server mirrors via [lastMessageAt]). */
fun DirectConversationDto.hasActiveDirectThread(): Boolean =
    lastMessageAt?.trim()?.isNotEmpty() == true

data class DirectConversationsResponseDto(val conversations: List<DirectConversationDto>)

data class CreateDirectConversationRequestDto(val recipientUserId: String)

data class DirectConversationSingleDto(val conversation: DirectConversationDto)

data class DirectMessageDto(
    @SerializedName("_id") val id: String,
    val conversationId: String,
    val senderUserId: String?,
    val sender: CircleChatSenderDto?,
    val body: String?,
    val messageType: String? = null,
    val imageUrl: String? = null,
    val videoAttachment: ChatVideoAttachmentDto? = null,
    val clientMessageId: String? = null,
    val replyToMessageId: String? = null,
    val replyTo: ChatReplyPreviewDto? = null,
    val reactions: List<ChatMessageReactionDto> = emptyList(),
    val createdAt: String?,
    val updatedAt: String? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val linkPreview: CircleChatLinkPreviewDto? = null,
    val eventAttachment: CircleChatEventAttachmentDto? = null,
    val placeAttachment: CircleChatPlaceAttachmentDto? = null,
)

data class DirectMessagesResponseDto(val messages: List<DirectMessageDto>)

data class DirectSingleMessageDto(val message: DirectMessageDto?)

data class SendDirectMessageDto(
    val body: String,
    val clientMessageId: String,
    val eventId: String? = null,
    val placeId: String? = null,
    val placeLatitude: Double? = null,
    val placeLongitude: Double? = null,
    val placeName: String? = null,
    val placeAddressSummary: String? = null,
    val replyToMessageId: String? = null,
    val imageUrl: String? = null,
) {
    fun toJsonObject(): JsonObject =
        JsonObject().apply {
            addProperty("body", body)
            addProperty("clientMessageId", clientMessageId)
            eventId?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("eventId", it) }
            placeId?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("placeId", it) }
            placeLatitude?.let { addProperty("placeLatitude", it) }
            placeLongitude?.let { addProperty("placeLongitude", it) }
            placeName?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("placeName", it) }
            placeAddressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("placeAddressSummary", it) }
            replyToMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("replyToMessageId", it) }
            imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("imageUrl", it) }
        }
}

data class PatchDirectMessageDto(val body: String)

/** Maps a direct message into [CircleChatMessageDto] so DM threads can reuse squad chat bubble UI. */
fun DirectMessageDto.asCircleChatMessageForBubble(conversationId: String): CircleChatMessageDto {
    val uid =
        senderUserId?.trim()?.takeIf { it.isNotEmpty() }
            ?: sender?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: "unknown"
    return CircleChatMessageDto(
        id = id,
        circleId = conversationId,
        senderUserId = uid,
        sender = sender,
        body = body?.trim().orEmpty(),
        messageType = messageType,
        systemKind = null,
        imageUrl = imageUrl,
        videoAttachment = videoAttachment,
        clientMessageId = clientMessageId,
        linkPreview = linkPreview,
        eventAttachment = eventAttachment,
        placeAttachment = placeAttachment,
        replyToMessageId = replyToMessageId,
        replyTo = replyTo,
        reactions = reactions,
        mentions = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        editedAt = editedAt,
        deletedAt = deletedAt,
    )
}

// --- Saved places (`/api/places`, platform-neutral) ---

data class SavedPlaceDto(
    @SerializedName("_id") val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeKind: String? = null,
    val addressSummary: String? = null,
    val source: String? = null,
)

data class CreateSavedPlaceRequestDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeKind: String? = null,
    val addressSummary: String? = null,
    val source: String = "android",
)

data class PatchMeTimeZoneRequestDto(
    val timeZone: String,
)

data class PatchMeTimeZoneResponseDto(
    val timeZone: String?,
    val timeZoneUpdatedAt: String?,
)

// --- Push device registration ---

/**
 * Mirrors `notificationRoutes.registerDeviceSchema`.
 *
 * **Android FCM:** always send [platform] = `"android"` and [bundleId] = [BuildConfig.APPLICATION_ID]
 * in JSON. The backend defaults missing `platform` to `"ios"`; Gson may omit Kotlin default
 * properties, so tokens were historically stored as iOS and never matched FCM sends.
 */
data class RegisterDeviceRequestDto(
    val token: String,
    val platform: String,
    val environment: String? = null,
    val bundleId: String? = null,
    val appVersion: String? = null,
    val deviceId: String? = null,
    val timeZone: String? = null,
) {
    companion object {
        /** FCM registration body aligned with iOS (explicit `platform` + `bundleId`). */
        fun forAndroidFcm(
            token: String,
            applicationId: String,
            environment: String = "production",
            appVersion: String? = null,
            deviceId: String? = null,
            timeZone: String? = null,
        ): RegisterDeviceRequestDto =
            RegisterDeviceRequestDto(
                token = token.trim(),
                platform = "android",
                environment = environment,
                bundleId = applicationId.trim(),
                appVersion = appVersion,
                deviceId = deviceId,
                timeZone = timeZone?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

data class RegisteredPushDeviceDto(
    val id: String? = null,
    val platform: String? = null,
    val environment: String? = null,
    val bundleId: String? = null,
    val lastRegisteredAt: String? = null,
)

data class RegisterDeviceResponseDto(
    val ok: Boolean? = false,
    val device: RegisteredPushDeviceDto? = null,
)

// --- Progression ---

/** Backend: `PROFILE_PROGRESSION_EVENT_TYPES.DAILY_LAUNCH` → `"daily_launch"`. */
data class ProgressionEventRequestDto(val type: String)

data class ProfileLevelUpDto(
    val eventType: String? = null,
    val pointsAwarded: Int? = null,
    val previousProgression: ProfileProgressionDto? = null,
    val progression: ProfileProgressionDto? = null,
    val nextProgression: ProfileProgressionDto? = null,
    val reachedDisplayName: String? = null,
    val nextDisplayName: String? = null,
    val unlockedNewTier: Boolean? = null,
)

data class ProgressionEventResponseDto(
    val awarded: Boolean?,
    val pointsAwarded: Int?,
    val profilePoints: Int?,
    val progression: ProfileProgressionDto?,
    val levelUp: ProfileLevelUpDto? = null,
)

// --- Account deletion ---

data class DeleteAccountRequestDto(
    /** Must equal `"delete account"`. */
    val confirmation: String,
)
