package to.ottomot.driftd.core.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import to.ottomot.driftd.DrivePathSample
import to.ottomot.driftd.DriveSpeedGradient
import to.ottomot.driftd.core.event.compareEventsForMainList
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.network.OttoHttpApi
import to.ottomot.driftd.core.network.dto.AddCircleMemberRequestDto
import to.ottomot.driftd.core.network.dto.AppendDrivePointsDto
import to.ottomot.driftd.core.network.dto.AppendDrivePointsResponseDto
import to.ottomot.driftd.core.network.dto.ChatReactionEmojiBodyDto
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.CircleChatMentionSpanDto
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.CircleMembersUpdatedDto
import to.ottomot.driftd.core.network.dto.CircleInviteDto
import to.ottomot.driftd.core.network.dto.CircleInviteRespondRequestDto
import to.ottomot.driftd.core.network.dto.CreateCircleRequestDto
import to.ottomot.driftd.core.network.dto.PatchCircleRequestDto
import to.ottomot.driftd.core.network.dto.PatchDriveRequestDto
import to.ottomot.driftd.core.network.dto.CreateRouteRequestDto
import to.ottomot.driftd.core.network.dto.PatchRouteRequestDto
import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.core.network.dto.CreateEventAddressBodyDto
import to.ottomot.driftd.core.network.dto.CreateEventLatLngBodyDto
import to.ottomot.driftd.core.network.dto.CreateEventRequestDto
import to.ottomot.driftd.core.network.dto.CreateSavedPlaceRequestDto
import to.ottomot.driftd.core.network.dto.CreateDirectConversationRequestDto
import to.ottomot.driftd.core.network.dto.DeleteAccountRequestDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.DriveEndDto
import to.ottomot.driftd.core.network.dto.DrivePointCaptureDto
import to.ottomot.driftd.core.network.dto.DirectMessageDto
import to.ottomot.driftd.core.network.dto.DriveLineDto
import to.ottomot.driftd.core.network.dto.EventCheckInRequestDto
import to.ottomot.driftd.core.network.dto.AdminSquadDto
import to.ottomot.driftd.core.network.dto.EventAttachedSquadDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.PatchSquadAssociationsRequestDto
import to.ottomot.driftd.core.network.dto.EventRsvpRequestDto
import to.ottomot.driftd.core.network.dto.GarageCarDto
import to.ottomot.driftd.core.network.dto.GarageReorderRequestDto
import to.ottomot.driftd.core.network.dto.ProfileLevelUpDto
import to.ottomot.driftd.core.network.dto.ProgressionEventRequestDto
import to.ottomot.driftd.core.network.dto.DriveStartDto
import to.ottomot.driftd.core.network.dto.InviteCircleByPhoneDto
import to.ottomot.driftd.core.network.dto.InviteLinkAcceptDto
import to.ottomot.driftd.core.network.dto.SignupInviteBalanceDto
import to.ottomot.driftd.core.network.dto.InviteLinkCreateRequestDto
import to.ottomot.driftd.core.network.dto.InviteLinkCreateResponseDto
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.core.network.dto.MyPendingCircleInvite
import to.ottomot.driftd.core.network.dto.PatchCircleChatMessageDto
import to.ottomot.driftd.core.network.dto.PatchCircleMemberRoleRequestDto
import to.ottomot.driftd.core.network.dto.PatchDirectMessageDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalRequestDto
import to.ottomot.driftd.core.network.dto.PatchEventRequestDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.PresenceUpdateDto
import to.ottomot.driftd.core.network.dto.RegisterDeviceRequestDto
import to.ottomot.driftd.core.network.dto.ChatVideoAttachmentDto
import to.ottomot.driftd.core.network.dto.ChatVideoUploadUrlsRequestDto
import to.ottomot.driftd.core.network.dto.SendCircleChatVideoMessageDto
import to.ottomot.driftd.core.network.dto.SendDirectChatVideoMessageDto
import to.ottomot.driftd.core.network.dto.SendCircleChatMessageDto
import to.ottomot.driftd.core.network.dto.SendDirectMessageDto
import to.ottomot.driftd.core.network.dto.DriveStatsVisibilitySetting
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.network.dto.UserProfileRealtimeDto

/** Backend `limit` maximum for circle + direct message list endpoints (Zod max 100). */
internal const val CHAT_MESSAGES_API_MAX_LIMIT = 100

/** Reads squad feed, garage, drives, chat, invites, presence from Otto HTTP APIs (same surfaces as iOS). */
class OttoDataRepository internal constructor(
    private val api: OttoHttpApi,
    private val gson: Gson,
) {
    private fun plainUtf8RequestBody(value: String): RequestBody =
        value.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

    private companion object {
        /** Matches server photo upload limit (`otto-backend` photoUpload constant). */
        internal const val ChatPhotoUploadMaxBytes: Int = PhotoUploadMaxBytes

        /** Matches iOS `fetchEvents` limit for public and squad-scoped lists. */
        const val PublicEventsLimit = 100
        const val SquadScopedEventsLimit = 100

        /** Server `PROFILE_PROGRESSION_EVENT_TYPES.DAILY_LAUNCH`. */
        const val ProgressionDailyLaunchType: String = "daily_launch"

        fun jsonMongoString(raw: JsonElement?): String? {
            if (raw == null || raw.isJsonNull) return null
            if (raw.isJsonPrimitive) {
                val s = raw.asString.trim()
                return s.takeIf { it.isNotEmpty() }
            }
            if (raw.isJsonObject) {
                val o = raw.asJsonObject
                val oid =
                    o["\$oid"]
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.trim()
                if (!oid.isNullOrEmpty()) return oid
                val nested =
                    o["_id"]
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.trim()
                if (!nested.isNullOrEmpty()) return nested
            }
            return null
        }
    }

    suspend fun circles() = runCatching { api.fetchCircles() }

    suspend fun patchCircle(
        circleId: String,
        name: String,
    ) = runCatching {
        val trimmed = name.trim()
        api.patchCircle(circleId.trim(), PatchCircleRequestDto(name = trimmed))
    }

    suspend fun leaveCircle(circleId: String) =
        runCatching {
            api.leaveCircle(circleId.trim())
        }

    suspend fun squadGrid(
        circleId: String,
        range: String = "all_time",
    ) = runCatching { api.fetchSquadGrid(circleId, range) }

    suspend fun events(scope: String = "upcoming", visibility: String = "public", eventType: String? = null) =
        runCatching {
            api.fetchEvents(
                scope = scope,
                visibility = visibility,
                limit = 80,
                circleId = null,
                eventType = eventType,
            )
        }

    private fun activeOrUpcomingEvents(events: List<EventDto>): List<EventDto> {
        val now = Instant.now()
        return events
            .filter { event -> eventCheckInEndsAtInstant(event)?.let { !it.isBefore(now) } ?: false }
            .sortedWith(::compareEventsForMainList)
    }

    private suspend fun fetchActiveOrUpcomingEvents(
        visibility: String,
        limit: Int,
        circleId: String?,
        eventType: String? = null,
    ): List<EventDto> {
        val events =
            api.fetchEvents(
                scope = "all",
                visibility = visibility,
                limit = limit,
                circleId = circleId,
                eventType = eventType,
            )
        return activeOrUpcomingEvents(events)
    }

    private suspend fun fetchMergedOfficialSquadEvents(circleIds: List<String>): LinkedHashMap<String, EventDto> {
        val byId = LinkedHashMap<String, EventDto>()
        for (circleId in circleIds) {
            runCatching {
                fetchActiveOrUpcomingEvents(
                    visibility = "official",
                    limit = SquadScopedEventsLimit,
                    circleId = circleId,
                )
            }.onSuccess { list ->
                list.forEach { evt -> byId[evt.id] = evt }
            }
        }
        return byId
    }

    suspend fun allSquadUpcomingEvents(circleIds: List<String>): Result<List<EventDto>> =
        runCatching {
            activeOrUpcomingEvents(fetchMergedOfficialSquadEvents(circleIds).values.toList())
        }

    suspend fun squadGoingEvents(circleIds: List<String>): Result<List<EventDto>> =
        runCatching {
            val byId = LinkedHashMap<String, EventDto>()
            val now = Instant.now()
            for (circleId in circleIds) {
                runCatching {
                    api.fetchEvents(
                        scope = "all",
                        visibility = "circle",
                        limit = SquadScopedEventsLimit,
                        circleId = circleId,
                    )
                }.onSuccess { events ->
                    events
                        .filter { event ->
                            event.currentUserRsvp == "going" &&
                                eventCheckInEndsAtInstant(event)?.let { !it.isBefore(now) } == true
                        }.forEach { evt -> byId[evt.id] = evt }
                }
            }
            byId.values.sortedWith(::compareEventsForMainList)
        }

    suspend fun communityPublicEvents(): Result<List<EventDto>> =
        runCatching {
            fetchActiveOrUpcomingEvents(
                visibility = "public",
                limit = PublicEventsLimit,
                circleId = null,
                eventType = "community",
            )
        }

    suspend fun squadScopedUpcomingEvents(circleId: String): Result<List<EventDto>> =
        runCatching {
            fetchActiveOrUpcomingEvents(
                visibility = "official",
                limit = SquadScopedEventsLimit,
                circleId = circleId.trim(),
            )
        }

    suspend fun featuredPublicEvents(): Result<List<EventDto>> =
        runCatching {
            fetchActiveOrUpcomingEvents(
                visibility = "public",
                limit = PublicEventsLimit,
                circleId = null,
                eventType = "featured",
            )
        }

    suspend fun fetchAdminSquads(): Result<List<AdminSquadDto>> =
        runCatching { api.fetchAdminSquads().squads.orEmpty() }

    suspend fun fetchEventSquadAssociations(eventId: String): Result<List<EventAttachedSquadDto>> =
        runCatching {
            api.fetchEventSquadAssociations(eventId.trim()).squads.orEmpty()
        }

    suspend fun patchEventSquadAssociations(
        eventId: String,
        squadIds: List<String>,
    ): Result<List<EventAttachedSquadDto>> =
        runCatching {
            api.patchEventSquadAssociations(
                eventId.trim(),
                PatchSquadAssociationsRequestDto(squadIds = squadIds),
            ).squads.orEmpty()
        }

    /** Private squad event for [circleId] (`POST /api/events`, matches iOS `createEvent` + visibility `circle`). */
    suspend fun createEvent(
        name: String,
        description: String?,
        startsAt: Instant,
        endsAt: Instant,
        visibility: String,
        eventType: String? = null,
        circleId: String? = null,
        addressLabel: String?,
        streetAddress: String?,
        city: String?,
        region: String?,
        postalCode: String?,
        locationLat: Double?,
        locationLng: Double?,
    ): Result<EventDto> =
        runCatching {
            val label =
                addressLabel
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val street =
                streetAddress
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventCity =
                city
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventRegion =
                region
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventPostalCode =
                postalCode
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val addr =
                if (label != null || street != null || eventCity != null || eventRegion != null || eventPostalCode != null) {
                    CreateEventAddressBodyDto(
                        label = label,
                        street1 = street,
                        city = eventCity,
                        region = eventRegion,
                        postalCode = eventPostalCode,
                    )
                } else {
                    null
                }
            val loc =
                if (locationLat != null && locationLng != null) {
                    CreateEventLatLngBodyDto(lat = locationLat, lng = locationLng)
                } else {
                    null
                }
            api.createEvent(
                CreateEventRequestDto(
                    name = name.trim(),
                    startsAt = startsAt.toString(),
                    endsAt = endsAt.toString(),
                    visibility = visibility,
                    eventType = eventType?.trim()?.takeIf { it.isNotEmpty() },
                    circleId = circleId?.trim()?.takeIf { it.isNotEmpty() },
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    address = addr,
                    location = loc,
                ),
            )
        }

    suspend fun createCircleScopedEvent(
        circleId: String,
        name: String,
        description: String?,
        startsAt: Instant,
        endsAt: Instant,
        addressLabel: String?,
        streetAddress: String?,
        city: String?,
        region: String?,
        postalCode: String?,
        locationLat: Double?,
        locationLng: Double?,
    ): Result<EventDto> {
        val cid = circleId.trim()
        if (cid.isEmpty()) return Result.failure(IllegalArgumentException("missing circle"))
        return createEvent(
            name = name,
            description = description,
            startsAt = startsAt,
            endsAt = endsAt,
            visibility = "circle",
            circleId = cid,
            addressLabel = addressLabel,
            streetAddress = streetAddress,
            city = city,
            region = region,
            postalCode = postalCode,
            locationLat = locationLat,
            locationLng = locationLng,
        )
    }

    suspend fun createCircle(
        name: String,
        description: String?,
    ) = runCatching {
        api.createCircle(CreateCircleRequestDto(name = name, description = description))
    }

    suspend fun inviteCircleByPhone(
        circleId: String,
        phoneNumber: String,
    ) = runCatching {
        api.inviteCircleByPhone(circleId, InviteCircleByPhoneDto(phoneNumber = phoneNumber))
    }

    suspend fun addCircleMember(
        circleId: String,
        userId: String,
        role: String = "member",
    ) = runCatching {
        api.addCircleMember(circleId, AddCircleMemberRequestDto(userId = userId, role = role))
    }

    suspend fun patchCircleMemberRole(circleId: String, userId: String, role: String) =
        runCatching {
            api.patchCircleMemberRole(
                circleId.trim(),
                userId.trim(),
                PatchCircleMemberRoleRequestDto(role = role.trim()),
            )
        }

    suspend fun removeCircleMember(circleId: String, userId: String) =
        runCatching {
            api.removeCircleMember(circleId.trim(), userId.trim())
        }

    suspend fun circleInvites(circleId: String) = runCatching { api.fetchCircleInvites(circleId) }

    suspend fun lookupUserByPhone(phoneNumber: String) =
        runCatching {
            val response = api.lookupUserByPhone(phoneNumber.trim())
            if (response.found) response.user else null
        }

    suspend fun fetchMyCircleInvites(): Result<List<MyPendingCircleInvite>> =
        runCatching {
            val arr = api.fetchMyCircleInvites()
            parseMyCircleInviteArray(arr)
        }

    suspend fun respondCircleInvite(
        inviteId: String,
        accept: Boolean,
    ) = runCatching {
        api.respondToCircleInvite(
            inviteId,
            CircleInviteRespondRequestDto(action = if (accept) "accept" else "decline"),
        )
    }

    suspend fun createInviteLink(
        circleId: String,
        expiresInDays: Int? = null,
        phoneNumber: String? = null,
    ): Result<InviteLinkCreateResponseDto> =
        runCatching {
            api.createInviteLink(
                InviteLinkCreateRequestDto(
                    circleId = circleId,
                    expiresInDays = expiresInDays,
                    phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

    suspend fun fetchSignupInviteBalance(): Result<SignupInviteBalanceDto> =
        runCatching { api.fetchSignupInviteBalance() }

    suspend fun resolveInviteLink(token: String, squadId: String? = null) =
        runCatching { api.fetchInviteLink(token.trim(), squadId?.trim()?.takeIf { it.isNotEmpty() }) }

    suspend fun acceptInviteLink(token: String, circleId: String? = null): Result<InviteLinkAcceptDto> =
        runCatching {
            val payload =
                circleId?.trim()?.takeIf { it.isNotEmpty() }?.let { mapOf("circleId" to it) } ?: emptyMap()
            api.acceptInviteLink(token.trim(), payload)
        }

    suspend fun recordProgressionDailyLaunch() =
        runCatching {
            api.postProgressionEvent(ProgressionEventRequestDto(type = ProgressionDailyLaunchType))
        }

    suspend fun circleChatMessages(
        circleId: String,
        limit: Int = 80,
        after: String? = null,
        before: String? = null,
    ) = runCatching {
        val safeLimit = limit.coerceIn(1, CHAT_MESSAGES_API_MAX_LIMIT)
        api.fetchCircleChatMessages(circleId, safeLimit, after, before).messages
    }

    suspend fun sendCircleChat(
        circleId: String,
        body: String,
        clientMessageId: String,
        eventId: String? = null,
        driveId: String? = null,
        placeId: String? = null,
        placeLatitude: Double? = null,
        placeLongitude: Double? = null,
        placeName: String? = null,
        placeAddressSummary: String? = null,
        replyToMessageId: String? = null,
        mentions: List<CircleChatMentionSpanDto>? = null,
        photoBytes: ByteArray? = null,
        photoContentType: String? = null,
        mapPreviewBytes: ByteArray? = null,
        imageUrl: String? = null,
    ) = runCatching {
        val cid = circleId.trim()
        val trimmedBody = body.trim()
        val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val photo = photoBytes?.takeIf { it.isNotEmpty() }
        val mapPreview = mapPreviewBytes?.takeIf { it.isNotEmpty() }
        val trimmedPlaceId = placeId?.trim()?.takeIf { it.isNotEmpty() }
        val hasAdhocPlace = placeLatitude != null && placeLongitude != null
        if (photo != null && photo.size > ChatPhotoUploadMaxBytes) {
            error("Photo is too large (max 24 MB).")
        }
        if (mapPreview != null && mapPreview.size > ChatPhotoUploadMaxBytes) {
            error("Map preview is too large (max 24 MB).")
        }
        val result =
            if (photo != null || (driveId != null && mapPreview != null) || ((trimmedPlaceId != null || hasAdhocPlace) && mapPreview != null)) {
                val mediaType = (photoContentType ?: "image/jpeg").toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
                val filename =
                    when {
                        photoContentType?.contains("png", ignoreCase = true) == true -> "photo.png"
                        photoContentType?.contains("webp", ignoreCase = true) == true -> "photo.webp"
                        else -> "photo.jpg"
                    }
                val photoPart =
                    photo?.let {
                        MultipartBody.Part.createFormData(
                            "photo",
                            filename,
                            it.toRequestBody(mediaType),
                        )
                    }
                val mapPreviewPart =
                    mapPreview?.let {
                        MultipartBody.Part.createFormData(
                            "mapPreview",
                            "map-preview.jpg",
                            it.toRequestBody("image/jpeg".toMediaTypeOrNull()!!),
                        )
                    }
                api.sendCircleChatMessageMultipart(
                    circleId = cid,
                    body = plainUtf8RequestBody(trimmedBody),
                    clientMessageId = plainUtf8RequestBody(clientMessageId.trim()),
                    eventId = eventId?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    driveId = driveId?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    placeId = trimmedPlaceId?.let(::plainUtf8RequestBody),
                    placeLatitude = placeLatitude?.let { plainUtf8RequestBody(it.toString()) },
                    placeLongitude = placeLongitude?.let { plainUtf8RequestBody(it.toString()) },
                    placeName = placeName?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    placeAddressSummary = placeAddressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    mentions =
                        mentions
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { gson.toJson(it).let(::plainUtf8RequestBody) },
                    photo = photoPart,
                    mapPreview = mapPreviewPart,
                )
            } else {
                api.sendCircleChatMessage(
                    cid,
                    SendCircleChatMessageDto(
                        body = trimmedBody,
                        clientMessageId = clientMessageId,
                        eventId = eventId,
                        driveId = driveId,
                        placeId = trimmedPlaceId,
                        placeLatitude = placeLatitude,
                        placeLongitude = placeLongitude,
                        placeName = placeName,
                        placeAddressSummary = placeAddressSummary,
                        replyToMessageId = replyToMessageId,
                        mentions = mentions?.takeIf { it.isNotEmpty() },
                        imageUrl = trimmedImageUrl,
                    ),
                )
            }
        result.message ?: error("missing message")
    }

    suspend fun requestCircleChatVideoUploadUrls(
        circleId: String,
        videoContentType: String,
    ) = runCatching {
        api.requestCircleChatVideoUploadUrls(
            circleId.trim(),
            ChatVideoUploadUrlsRequestDto(videoContentType = videoContentType),
        )
    }

    suspend fun sendCircleChatVideoMessage(
        circleId: String,
        body: String,
        clientMessageId: String,
        videoAttachment: ChatVideoAttachmentDto,
        replyToMessageId: String? = null,
        mentions: List<CircleChatMentionSpanDto>? = null,
    ) = runCatching {
        api
            .sendCircleChatVideoMessage(
                circleId.trim(),
                SendCircleChatVideoMessageDto(
                    body = body.trim(),
                    clientMessageId = clientMessageId.trim(),
                    videoAttachment = videoAttachment,
                    replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() },
                    mentions = mentions?.takeIf { it.isNotEmpty() },
                ),
            ).message ?: error("missing message")
    }

    suspend fun fetchDrive(driveId: String, circleId: String? = null) =
        runCatching {
            api.fetchDrive(driveId.trim(), circleId?.trim()?.takeIf { it.isNotEmpty() })
        }

    suspend fun fetchDrivePathSamples(driveId: String, circleId: String? = null): Result<List<DrivePathSample>> =
        runCatching {
            api
                .fetchDrivePoints(
                    driveId = driveId.trim(),
                    circleId = circleId?.trim()?.takeIf { it.isNotEmpty() },
                ).points
                .mapNotNull(DriveSpeedGradient::from)
        }

    suspend fun patchDriveGarageCar(driveId: String, garageCarId: String?) =
        runCatching {
            api.patchDrive(driveId.trim(), PatchDriveRequestDto(garageCarId = garageCarId))
        }

    suspend fun patchDriveTitle(driveId: String, title: String) =
        runCatching {
            val trimmed = title.trim()
            require(trimmed.isNotEmpty())
            api.patchDrive(driveId.trim(), PatchDriveRequestDto(title = trimmed))
        }

    suspend fun deleteDrive(driveId: String) =
        runCatching {
            val response = api.deleteDrive(driveId.trim())
            if (!response.isSuccessful) {
                error("delete drive failed (${response.code()})")
            }
        }

    suspend fun fetchRoutes(limit: Int = 50) =
        runCatching { api.fetchRoutes(limit.coerceIn(1, 100)) }

    suspend fun deleteRoute(routeId: String) =
        runCatching {
            val response = api.deleteRoute(routeId.trim())
            if (!response.isSuccessful) {
                error("delete route failed (${response.code()})")
            }
        }

    suspend fun patchRoute(route: SavedRouteDto, name: String) =
        runCatching {
            api.patchRoute(
                routeId = route.id.trim(),
                body =
                    PatchRouteRequestDto(
                        name = name.trim(),
                        points = route.points.orEmpty(),
                        roadCoordinates = route.roadCoordinates.orEmpty(),
                        distanceMeters = route.distanceMeters ?: 0.0,
                        etaSeconds = route.etaSeconds ?: 0.0,
                    ),
            )
        }

    suspend fun createRoute(
        name: String,
        points: List<RoutePointDto>,
        roadCoordinates: List<RoutePointDto>,
        distanceMeters: Double,
        etaSeconds: Double,
    ) = runCatching {
        api.createRoute(
            body =
                CreateRouteRequestDto(
                    name = name.trim(),
                    points = points,
                    roadCoordinates = roadCoordinates,
                    distanceMeters = distanceMeters,
                    etaSeconds = etaSeconds,
                ),
        )
    }

    suspend fun updateRoute(
        routeId: String,
        name: String,
        points: List<RoutePointDto>,
        roadCoordinates: List<RoutePointDto>,
        distanceMeters: Double,
        etaSeconds: Double,
    ) = runCatching {
        api.patchRoute(
            routeId = routeId.trim(),
            body =
                PatchRouteRequestDto(
                    name = name.trim(),
                    points = points,
                    roadCoordinates = roadCoordinates,
                    distanceMeters = distanceMeters,
                    etaSeconds = etaSeconds,
                ),
        )
    }

    suspend fun patchCircleChatMessage(
        circleId: String,
        messageId: String,
        body: String,
        mentions: List<CircleChatMentionSpanDto>? = null,
    ) = runCatching {
        api
            .patchCircleChatMessage(
                circleId.trim(),
                messageId.trim(),
                PatchCircleChatMessageDto(
                    body = body.trim(),
                    mentions = mentions?.takeIf { it.isNotEmpty() },
                ),
            ).message ?: error("missing message")
    }

    suspend fun deleteCircleChatMessage(
        circleId: String,
        messageId: String,
    ) = runCatching {
        api.deleteCircleChatMessage(circleId.trim(), messageId.trim()).message ?: error("missing message")
    }

    suspend fun postCircleChatReaction(
        circleId: String,
        messageId: String,
        emoji: String,
    ) = runCatching {
        api
            .postCircleChatReaction(
                circleId.trim(),
                messageId.trim(),
                ChatReactionEmojiBodyDto(emoji = emoji.trim()),
            ).message ?: error("missing message")
    }

    suspend fun nextUpEventDismissals(
        circleId: String,
        eventIds: List<String> = emptyList(),
    ) = runCatching {
        val ids =
            eventIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(",")
                .takeIf { it.isNotEmpty() }
        api.fetchNextUpEventDismissals(circleId.trim(), ids).dismissals.orEmpty()
    }

    suspend fun dismissNextUpEventBanner(
        circleId: String,
        eventId: String,
        dismissedContext: String,
    ) = runCatching {
        api.dismissNextUpEventBanner(
            circleId.trim(),
            eventId.trim(),
            NextUpEventDismissalRequestDto(dismissedContext = dismissedContext),
        ).dismissal ?: error("missing dismissal")
    }

    suspend fun setAutoEventCheckIn(userId: String, enabled: Boolean) =
        runCatching {
            val body = JsonObject()
            body.addProperty("autoEventCheckInEnabled", enabled)
            api.patchUser(userId, body)
        }

    suspend fun setSharingSafetyDisclaimerAcknowledged(userId: String, acknowledged: Boolean) =
        runCatching {
            val body = JsonObject()
            body.addProperty("sharingSafetyDisclaimerAcknowledged", acknowledged)
            api.patchUser(userId, body)
        }

    suspend fun setShowPublicGoingEventsOnProfile(userId: String, enabled: Boolean) =
        runCatching {
            val body = JsonObject()
            body.addProperty("showPublicGoingEventsOnProfile", enabled)
            api.patchUser(userId, body)
        }

    suspend fun setDriveStatsVisibility(userId: String, visibility: DriveStatsVisibilitySetting) =
        runCatching {
            val body = JsonObject()
            body.addProperty("driveStatsVisibility", visibility.wireValue)
            api.patchUser(userId, body)
        }

    suspend fun publicMemberProfile(userId: String) = runCatching {
        api.fetchPublicMemberProfile(userId.trim())
    }

    suspend fun event(eventRef: String) = runCatching { api.fetchEvent(eventRef) }

    suspend fun updateCircleScopedEvent(
        eventId: String,
        name: String,
        description: String?,
        startsAt: Instant,
        endsAt: Instant,
        addressLabel: String?,
        streetAddress: String?,
        city: String?,
        region: String?,
        postalCode: String?,
        locationLat: Double?,
        locationLng: Double?,
    ): Result<EventDto> =
        runCatching {
            val label =
                addressLabel
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val street =
                streetAddress
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventCity =
                city
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventRegion =
                region
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val eventPostalCode =
                postalCode
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val addr =
                if (label != null || street != null || eventCity != null || eventRegion != null || eventPostalCode != null) {
                    CreateEventAddressBodyDto(
                        label = label,
                        street1 = street,
                        city = eventCity,
                        region = eventRegion,
                        postalCode = eventPostalCode,
                    )
                } else {
                    null
                }
            val loc =
                if (locationLat != null && locationLng != null) {
                    CreateEventLatLngBodyDto(lat = locationLat, lng = locationLng)
                } else {
                    null
                }
            api.patchEvent(
                eventId.trim(),
                PatchEventRequestDto(
                    name = name.trim(),
                    startsAt = startsAt.toString(),
                    endsAt = endsAt.toString(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    address = addr,
                    location = loc,
                ),
            )
        }

    suspend fun deleteEvent(eventId: String): Result<Unit> =
        runCatching {
            val response = api.deleteEvent(eventId.trim())
            if (!response.isSuccessful) {
                throw retrofit2.HttpException(response)
            }
        }

    suspend fun uploadEventBanner(
        eventId: String,
        imageBytes: ByteArray,
        filename: String = "event-banner.jpg",
        contentType: String = "image/jpeg",
    ): Result<EventDto> =
        runCatching {
            val media = contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(media)
            val part = MultipartBody.Part.createFormData("photo", filename.ifBlank { "event-banner.jpg" }, body)
            val aspect = (16.0 / 9.0).toString().toRequestBody("text/plain".toMediaTypeOrNull())
            api.uploadEventBanner(eventId.trim(), part, aspect)
        }

    suspend fun updateEventRsvp(
        eventId: String,
        status: String,
    ) = runCatching { api.updateEventRsvp(eventId, EventRsvpRequestDto(status)) }

    suspend fun postEventCheckIn(
        eventId: String,
        latitude: Double?,
        longitude: Double?,
    ) = runCatching {
        api.postEventCheckIn(
            eventId,
            EventCheckInRequestDto(
                method = "manual",
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    suspend fun garage(userId: String) = runCatching { api.fetchGarageCars(userId) }

    suspend fun drives(userId: String) = runCatching { api.fetchUserDrives(userId) }

    suspend fun drivingStats(userId: String) = runCatching { api.fetchDrivingStats(userId) }

    suspend fun contacts() = runCatching { api.fetchContacts() }

    suspend fun savedPlacesMine() = runCatching { api.fetchMySavedPlaces() }

    suspend fun createSavedPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        addressSummary: String? = null,
    ) = runCatching {
        api.createSavedPlace(
            CreateSavedPlaceRequestDto(
                name = name.trim(),
                latitude = latitude,
                longitude = longitude,
                addressSummary = addressSummary?.trim()?.takeIf { it.isNotEmpty() },
                source = "android",
            ),
        )
    }

    suspend fun deleteSavedPlace(placeId: String): Result<Unit> =
        runCatching {
            val resp = api.deleteSavedPlace(placeId)
            if (!resp.isSuccessful) {
                error(resp.errorBody()?.string().orEmpty().ifBlank { "Delete failed (${resp.code()})." })
            }
        }

    suspend fun renameSavedPlace(
        placeId: String,
        name: String,
    ) = runCatching {
        val body = JsonObject()
        body.addProperty("name", name.trim())
        api.patchSavedPlace(placeId, body)
    }

    /** Legacy `/api/drive-lines` squad overlays; not shown on the map (deprecated concept — kept for future refactors). */
    suspend fun driveLinesForCircle(
        circleId: String,
        limit: Int = 24,
    ) = runCatching { api.fetchDriveLinesForCircle(circleId, limit.coerceIn(1, 50)) }

    /**
     * Persist an FCM or other vendor token (`POST /api/notifications/devices`).
     * Wired for when Firebase Cloud Messaging supplies a registration token out-of-band.
     */
    suspend fun registerPushDevice(request: RegisterDeviceRequestDto): Result<Unit> =
        runCatching {
            api.registerPushDevice(request)
            Unit
        }

    suspend fun me() = runCatching { api.fetchMe() }

    suspend fun patchMeTimeZone(body: to.ottomot.driftd.core.network.dto.PatchMeTimeZoneRequestDto) =
        runCatching { api.patchMeTimeZone(body) }

    suspend fun presenceForCircle(circleId: String) = runCatching { api.fetchPresenceForCircle(circleId) }

    suspend fun pushPresence(payload: PresenceUpdateDto) =
        runCatching {
            api.updatePresence(payload)
        }

    suspend fun startDrivingSession(payload: DriveStartDto) =
        runCatching { api.startDrive(payload) }

    suspend fun appendDrivingPoints(
        driveId: String,
        points: List<DrivePointCaptureDto>,
    ): Result<AppendDrivePointsResponseDto> =
        runCatching {
            api.appendDrivePoints(driveId, AppendDrivePointsDto(points = points))
        }

    suspend fun endDrivingSession(
        driveId: String,
        payload: DriveEndDto,
    ) =
        runCatching { api.endDrive(driveId, payload) }

    suspend fun directConversations() = runCatching { api.fetchDirectConversations().conversations }

    suspend fun getOrCreateDirectConversation(recipientUserId: String) =
        runCatching {
            api
                .createDirectConversation(CreateDirectConversationRequestDto(recipientUserId = recipientUserId.trim()))
                .conversation
        }

    suspend fun directMessages(
        conversationId: String,
        limit: Int = 80,
        after: String? = null,
        before: String? = null,
    ) = runCatching {
        val safeLimit = limit.coerceIn(1, CHAT_MESSAGES_API_MAX_LIMIT)
        api
            .fetchDirectMessages(
                conversationId = conversationId,
                limit = safeLimit,
                after = after,
                before = before,
            ).messages
    }

    suspend fun sendDirectMessage(
        conversationId: String,
        body: String,
        clientMessageId: String,
        eventId: String? = null,
        placeId: String? = null,
        placeLatitude: Double? = null,
        placeLongitude: Double? = null,
        placeName: String? = null,
        placeAddressSummary: String? = null,
        replyToMessageId: String? = null,
        photoBytes: ByteArray? = null,
        photoContentType: String? = null,
        mapPreviewBytes: ByteArray? = null,
        imageUrl: String? = null,
    ) = runCatching {
        val convId = conversationId.trim()
        val trimmedBody = body.trim()
        val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val photo = photoBytes?.takeIf { it.isNotEmpty() }
        val mapPreview = mapPreviewBytes?.takeIf { it.isNotEmpty() }
        val trimmedPlaceId = placeId?.trim()?.takeIf { it.isNotEmpty() }
        val hasAdhocPlace = placeLatitude != null && placeLongitude != null
        if (photo != null && photo.size > ChatPhotoUploadMaxBytes) {
            error("Photo is too large (max 24 MB).")
        }
        if (mapPreview != null && mapPreview.size > ChatPhotoUploadMaxBytes) {
            error("Map preview is too large (max 24 MB).")
        }
        val result =
            if (photo != null || ((trimmedPlaceId != null || hasAdhocPlace) && mapPreview != null)) {
                val mediaType = (photoContentType ?: "image/jpeg").toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
                val filename =
                    when {
                        photoContentType?.contains("png", ignoreCase = true) == true -> "photo.png"
                        photoContentType?.contains("webp", ignoreCase = true) == true -> "photo.webp"
                        else -> "photo.jpg"
                    }
                val photoPart =
                    photo?.let {
                        MultipartBody.Part.createFormData(
                            "photo",
                            filename,
                            it.toRequestBody(mediaType),
                        )
                    }
                val mapPreviewPart =
                    mapPreview?.let {
                        MultipartBody.Part.createFormData(
                            "mapPreview",
                            "map-preview.jpg",
                            it.toRequestBody("image/jpeg".toMediaTypeOrNull()!!),
                        )
                    }
                api.sendDirectMessageMultipart(
                    conversationId = convId,
                    body = plainUtf8RequestBody(trimmedBody),
                    clientMessageId = plainUtf8RequestBody(clientMessageId.trim()),
                    eventId = eventId?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    placeId = trimmedPlaceId?.let(::plainUtf8RequestBody),
                    placeLatitude = placeLatitude?.let { plainUtf8RequestBody(it.toString()) },
                    placeLongitude = placeLongitude?.let { plainUtf8RequestBody(it.toString()) },
                    placeName = placeName?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    placeAddressSummary = placeAddressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let(::plainUtf8RequestBody),
                    photo = photoPart,
                    mapPreview = mapPreviewPart,
                )
            } else {
                api.sendDirectMessage(
                    conversationId = convId,
                    body =
                        SendDirectMessageDto(
                            body = trimmedBody,
                            clientMessageId = clientMessageId,
                            eventId = eventId,
                            placeId = trimmedPlaceId,
                            placeLatitude = placeLatitude,
                            placeLongitude = placeLongitude,
                            placeName = placeName,
                            placeAddressSummary = placeAddressSummary,
                            replyToMessageId = replyToMessageId,
                            imageUrl = trimmedImageUrl,
                        ),
                )
            }
        result.message ?: error("missing message")
    }

    suspend fun requestDirectChatVideoUploadUrls(
        conversationId: String,
        videoContentType: String,
    ) = runCatching {
        api.requestDirectChatVideoUploadUrls(
            conversationId.trim(),
            ChatVideoUploadUrlsRequestDto(videoContentType = videoContentType),
        )
    }

    suspend fun sendDirectChatVideoMessage(
        conversationId: String,
        body: String,
        clientMessageId: String,
        videoAttachment: ChatVideoAttachmentDto,
        replyToMessageId: String? = null,
    ) = runCatching {
        api
            .sendDirectChatVideoMessage(
                conversationId.trim(),
                SendDirectChatVideoMessageDto(
                    body = body.trim(),
                    clientMessageId = clientMessageId.trim(),
                    videoAttachment = videoAttachment,
                    replyToMessageId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() },
                ),
            ).message ?: error("missing message")
    }

    suspend fun patchDirectMessage(
        conversationId: String,
        messageId: String,
        body: String,
    ) = runCatching {
        api
            .patchDirectMessage(
                conversationId.trim(),
                messageId.trim(),
                PatchDirectMessageDto(body = body.trim()),
            ).message ?: error("missing message")
    }

    suspend fun deleteDirectMessage(
        conversationId: String,
        messageId: String,
    ) = runCatching {
        api.deleteDirectMessage(conversationId.trim(), messageId.trim()).message ?: error("missing message")
    }

    suspend fun postDirectMessageReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
    ) = runCatching {
        api
            .postDirectMessageReaction(
                conversationId.trim(),
                messageId.trim(),
                ChatReactionEmojiBodyDto(emoji = emoji.trim()),
            ).message ?: error("missing message")
    }

    suspend fun createGarageCar(
        userId: String,
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        year: Int?,
        color: String?,
        logoSlug: String?,
        isPrimary: Boolean?,
    ) = runCatching {
        api.createGarageCar(
            userId,
            garageCarJson(nickname, make, makeId, model, year, color, logoSlug, isPrimary),
        )
    }

    suspend fun patchGarageCar(
        userId: String,
        carId: String,
        nickname: String?,
        make: String?,
        makeId: String?,
        model: String?,
        year: Int?,
        color: String?,
        logoSlug: String?,
        isPrimary: Boolean?,
    ) = runCatching {
        val patch = JsonObject()
        nickname?.let { patch.addProperty("nickname", it.trim()) }
        make?.trim()?.takeIf { it.isNotEmpty() }?.let { patch.addProperty("make", it) }
        makeId?.trim()?.takeIf { it.isNotEmpty() }?.let { patch.addProperty("makeId", it) }
        model?.trim()?.takeIf { it.isNotEmpty() }?.let { patch.addProperty("model", it) }
        if (year != null) {
            patch.addProperty("year", year)
        }
        color?.trim()?.takeIf { it.isNotEmpty() }?.let { patch.addProperty("color", it) }
        logoSlug?.trim()?.takeIf { it.isNotEmpty() }?.let { patch.addProperty("logoSlug", it) }
        if (isPrimary != null) {
            patch.addProperty("isPrimary", isPrimary)
        }
        if (patch.entrySet().isEmpty()) error("nothing to patch")
        api.patchGarageCar(userId, carId, patch)
    }

    suspend fun deleteGarageCar(
        userId: String,
        carId: String,
    ): Result<Unit> =
        runCatching {
            val resp = api.deleteGarageCar(userId, carId)
            if (!resp.isSuccessful) {
                error(resp.errorBody()?.string().orEmpty().ifBlank { "Delete failed (${resp.code()})." })
            }
        }

    suspend fun reorderGarageCars(
        userId: String,
        orderedCarIds: List<String>,
    ): Result<List<GarageCarDto>> =
        runCatching {
            api.reorderGarageCars(userId.trim(), GarageReorderRequestDto(orderedCarIds.map { it.trim() }))
        }

    suspend fun uploadGarageCarPhoto(
        userId: String,
        carId: String,
        imageBytes: ByteArray,
        filename: String = "photo.jpg",
        contentType: String = "image/jpeg",
    ): Result<GarageCarDto> =
        runCatching {
            val media = contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(media)
            val part =
                MultipartBody.Part.createFormData(
                    "photo",
                    filename.ifBlank { "photo.jpg" },
                    body,
                )
            api.uploadGarageCarPhoto(userId, carId, part)
        }

    suspend fun uploadUserAvatar(
        userId: String,
        imageBytes: ByteArray,
        filename: String = "avatar.jpg",
        contentType: String = "image/jpeg",
    ) =
        runCatching {
            val media = contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(media)
            val part =
                MultipartBody.Part.createFormData(
                    "photo",
                    filename.ifBlank { "avatar.jpg" },
                    body,
                )
            api.uploadUserAvatar(userId, part)
        }

    suspend fun deleteUserAccount(userId: String): Result<Unit> =
        runCatching {
            val resp =
                api.deleteUserAccount(
                    userId,
                    DeleteAccountRequestDto(confirmation = "delete account"),
                )
            if (!resp.isSuccessful) {
                error(resp.errorBody()?.string().orEmpty().ifBlank { "Delete failed (${resp.code()})." })
            }
        }

    suspend fun patchUserDisplayName(
        userId: String,
        displayName: String,
    ) = runCatching {
        val body = JsonObject()
        body.addProperty("displayName", displayName.trim())
        api.patchUser(userId, body)
    }

    suspend fun patchUserMapAccent(
        userId: String,
        mapAccentKey: String,
    ) = runCatching {
        val body = JsonObject()
        body.addProperty("mapAccentKey", mapAccentKey.trim())
        api.patchUser(userId, body)
    }

    suspend fun blockUser(targetUserId: String) = runCatching { api.blockUser(targetUserId) }

    suspend fun unblockUser(targetUserId: String) = runCatching { api.unblockUser(targetUserId) }

    fun parseChatMessage(raw: JsonObject): CircleChatMessageDto? =
        runCatching { gson.fromJson(raw, CircleChatMessageDto::class.java) }.getOrNull()

    fun parsePresenceUpdate(raw: JsonObject): PresenceMemberDto? =
        runCatching { gson.fromJson(raw, PresenceMemberDto::class.java) }.getOrNull()

    /** Used when realtime delivers a hydrated message shaped like the HTTP payloads. */
    fun parseWsChatMessage(any: JsonObject): CircleChatMessageDto? = parseChatMessage(any)

    fun parseDirectMessage(raw: JsonObject): DirectMessageDto? =
        runCatching { gson.fromJson(raw, DirectMessageDto::class.java) }.getOrNull()

    fun parseUserProfileRealtime(profile: JsonObject): UserProfileRealtimeDto? =
        runCatching { gson.fromJson(profile, UserProfileRealtimeDto::class.java) }.getOrNull()

    fun parseProfileLevelUp(raw: JsonObject): ProfileLevelUpDto? =
        runCatching { gson.fromJson(raw, ProfileLevelUpDto::class.java) }.getOrNull()

    fun parseProfileLevelUp(jsonString: String): ProfileLevelUpDto? =
        runCatching {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return@runCatching null
            gson.fromJson(trimmed, ProfileLevelUpDto::class.java)
        }.getOrNull()

    fun parseCircleMembersUpdated(frame: JsonObject): CircleMembersUpdatedDto? =
        runCatching {
            val circleJson = frame["circle"]?.takeIf { it.isJsonObject } ?: return null
            val circle = gson.fromJson(circleJson, CircleDto::class.java)
            val usersJson = frame["users"]
            val users: List<UserDto> =
                when {
                    usersJson == null || usersJson.isJsonNull -> emptyList()
                    usersJson.isJsonArray ->
                        gson.fromJson(usersJson, Array<UserDto>::class.java)?.toList().orEmpty()
                    else -> emptyList()
                }
            CircleMembersUpdatedDto(circle = circle, users = users)
        }.getOrNull()

    private fun garageCarJson(
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        year: Int?,
        color: String?,
        logoSlug: String?,
        isPrimary: Boolean?,
    ): JsonObject {
        val o = JsonObject()
        nickname.trim().takeIf { it.isNotEmpty() }?.let { o.addProperty("nickname", it) }
        o.addProperty("make", make.trim())
        makeId?.trim()?.takeIf { it.isNotEmpty() }?.let { o.addProperty("makeId", it) }
        o.addProperty("model", model.trim())
        year?.let { o.addProperty("year", it) }
        color?.trim()?.takeIf { it.isNotEmpty() }?.let { o.addProperty("color", it) }
        logoSlug?.trim()?.takeIf { it.isNotEmpty() }?.let { o.addProperty("logoSlug", it) }
        isPrimary?.let { o.addProperty("isPrimary", it) }
        return o
    }

    private fun parseMyCircleInviteArray(raw: JsonArray): List<MyPendingCircleInvite> {
        val list = mutableListOf<MyPendingCircleInvite>()
        for (el in raw) {
            parseMyInviteRow(el)?.let { list += it }
        }
        return list
    }

    private fun parseMyInviteRow(raw: JsonElement): MyPendingCircleInvite? {
        if (!raw.isJsonObject) return null
        val obj = raw.asJsonObject
        val inviteId = jsonMongoString(obj["_id"]) ?: return null

        val circleEl = obj["circleId"]
        val circleObj =
            when {
                circleEl == null -> null
                circleEl.isJsonObject -> circleEl.asJsonObject
                else -> null
            }

        val circleId: String =
            if (circleObj != null) {
                jsonMongoString(circleObj["_id"]) ?: circleObj["id"]?.takeIf { it.isJsonPrimitive }?.asString
            } else {
                circleEl?.takeIf { it.isJsonPrimitive }?.asString
            }.orEmpty()
        val circleName: String =
            circleObj?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                ?: circleId.takeIf { it.isNotBlank() }
                ?: "Squad"

        val circleDesc =
            circleObj?.get("description")?.takeIf { it.isJsonPrimitive }?.asString

        val status =
            obj["status"]?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

        if (circleId.isBlank()) return null
        return MyPendingCircleInvite(
            inviteId = inviteId,
            circleId = circleId,
            circleName = circleName,
            circleDescription = circleDesc,
            status = status,
        )
    }
}
