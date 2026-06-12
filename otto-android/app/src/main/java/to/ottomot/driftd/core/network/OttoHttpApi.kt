package to.ottomot.driftd.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import to.ottomot.driftd.core.network.dto.AddCircleMemberRequestDto
import to.ottomot.driftd.core.network.dto.PatchCircleMemberRoleRequestDto
import to.ottomot.driftd.core.network.dto.AppendDrivePointsDto
import to.ottomot.driftd.core.network.dto.AppendDrivePointsResponseDto
import to.ottomot.driftd.core.network.dto.AuthVerifyResponseDto
import to.ottomot.driftd.core.network.dto.ChatReactionEmojiBodyDto
import to.ottomot.driftd.core.network.dto.CircleChatMessagesResponseDto
import to.ottomot.driftd.core.network.dto.CircleChatSingleMessageDto
import to.ottomot.driftd.core.network.dto.CircleSharedItemsListResponseDto
import to.ottomot.driftd.core.network.dto.CircleSharedItemsSummaryResponseDto
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.CircleInviteDto
import to.ottomot.driftd.core.network.dto.CircleInviteRespondDto
import to.ottomot.driftd.core.network.dto.CompleteSignupRequestDto
import to.ottomot.driftd.core.network.dto.CheckSignupInviteRequestDto
import to.ottomot.driftd.core.network.dto.CircleInviteRespondRequestDto
import to.ottomot.driftd.core.network.dto.CreateDirectConversationRequestDto
import to.ottomot.driftd.core.network.dto.CreateCircleRequestDto
import to.ottomot.driftd.core.network.dto.CreateEventRequestDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.DrivePathPointsResponseDto
import to.ottomot.driftd.core.network.dto.PatchDriveRequestDto
import to.ottomot.driftd.core.network.dto.CreateRouteRequestDto
import to.ottomot.driftd.core.network.dto.PatchRouteRequestDto
import to.ottomot.driftd.core.network.dto.RouteDriveSessionDto
import to.ottomot.driftd.core.network.dto.RouteDriveSessionRequestDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.core.network.dto.SharedWithMeRoutesResponseDto
import to.ottomot.driftd.core.network.dto.DeleteAccountRequestDto
import to.ottomot.driftd.core.network.dto.DirectConversationSingleDto
import to.ottomot.driftd.core.network.dto.DirectConversationsResponseDto
import to.ottomot.driftd.core.network.dto.DirectMessagesResponseDto
import to.ottomot.driftd.core.network.dto.DirectSingleMessageDto
import to.ottomot.driftd.core.network.dto.DriveEndDto
import to.ottomot.driftd.core.network.dto.DriveLineDto
import to.ottomot.driftd.core.network.dto.DriveStartDto
import to.ottomot.driftd.core.network.dto.DrivingStatsDto
import to.ottomot.driftd.core.network.dto.EventCheckInRequestDto
import to.ottomot.driftd.core.network.dto.EventCheckInResultDto
import to.ottomot.driftd.core.network.dto.AdminSquadsResponseDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.PatchSquadAssociationsRequestDto
import to.ottomot.driftd.core.network.dto.SquadAssociationsResponseDto
import to.ottomot.driftd.core.network.dto.EventRsvpRequestDto
import to.ottomot.driftd.core.network.dto.FrequentChatContactsResponseDto
import to.ottomot.driftd.core.network.dto.GarageCarDto
import to.ottomot.driftd.core.network.dto.GarageReorderRequestDto
import to.ottomot.driftd.core.network.dto.InviteCircleByPhoneDto
import to.ottomot.driftd.core.network.dto.InviteLinkAcceptDto
import to.ottomot.driftd.core.network.dto.InviteLinkCreateRequestDto
import to.ottomot.driftd.core.network.dto.InviteLinkCreateResponseDto
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.core.network.dto.LeaveCircleResponseDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalRequestDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalResponseDto
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalsResponseDto
import to.ottomot.driftd.core.network.dto.PatchEventRequestDto
import to.ottomot.driftd.core.network.dto.PatchMeTimeZoneRequestDto
import to.ottomot.driftd.core.network.dto.PatchMeTimeZoneResponseDto
import to.ottomot.driftd.core.network.dto.ProgressionEventRequestDto
import to.ottomot.driftd.core.network.dto.ProgressionEventResponseDto
import to.ottomot.driftd.core.network.dto.PublicMemberProfileDto
import to.ottomot.driftd.core.network.dto.PresenceCircleResponseDto
import to.ottomot.driftd.core.network.dto.RegisterDeviceRequestDto
import to.ottomot.driftd.core.network.dto.RegisterDeviceResponseDto
import to.ottomot.driftd.core.network.dto.PresencePostResponseDto
import to.ottomot.driftd.core.network.dto.PresenceUpdateDto
import to.ottomot.driftd.core.network.dto.RequestOtpRequest
import to.ottomot.driftd.core.network.dto.CreateSavedPlaceRequestDto
import to.ottomot.driftd.core.network.dto.SavedPlaceDto
import to.ottomot.driftd.core.network.dto.PatchCircleChatMessageDto
import to.ottomot.driftd.core.network.dto.PatchCircleRequestDto
import to.ottomot.driftd.core.network.dto.PatchDirectMessageDto
import to.ottomot.driftd.core.network.dto.SendCircleChatMessageDto
import to.ottomot.driftd.core.network.dto.ChatVideoAttachmentDto
import to.ottomot.driftd.core.network.dto.ChatVideoUploadUrlsRequestDto
import to.ottomot.driftd.core.network.dto.ChatVideoUploadUrlsResponseDto
import to.ottomot.driftd.core.network.dto.SendCircleChatVideoMessageDto
import to.ottomot.driftd.core.network.dto.SendDirectChatVideoMessageDto
import to.ottomot.driftd.core.network.dto.SendDirectMessageDto
import to.ottomot.driftd.core.network.dto.SignupInviteBalanceDto
import to.ottomot.driftd.core.network.dto.SquadGridResponseDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.network.dto.UserLookupByPhoneResponseDto
import to.ottomot.driftd.core.network.dto.VerifyOtpRequest
import to.ottomot.driftd.core.network.dto.VerifyOtpResponseDto

/** HTTP facade for Otto backends; paths aligned with APIClient on iOS. */
interface OttoHttpApi {
    @POST("api/auth/request-otp")
    suspend fun requestOtp(@Body body: RequestOtpRequest): Response<ResponseBody>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): VerifyOtpResponseDto

    @POST("api/auth/complete-signup")
    suspend fun completeSignup(@Body body: CompleteSignupRequestDto): AuthVerifyResponseDto

    @POST("api/auth/check-signup-invite")
    suspend fun checkSignupInvite(@Body body: CheckSignupInviteRequestDto): Response<ResponseBody>

    @GET("api/auth/me")
    suspend fun fetchMe(): UserDto

    @GET("api/users/lookup/by-phone")
    suspend fun lookupUserByPhone(
        @Query("phoneNumber") phoneNumber: String,
    ): UserLookupByPhoneResponseDto

    @PATCH("api/users/me/time-zone")
    suspend fun patchMeTimeZone(@Body body: PatchMeTimeZoneRequestDto): PatchMeTimeZoneResponseDto

    @GET("api/public/m/{userId}")
    suspend fun fetchPublicMemberProfile(
        @Path("userId") userId: String,
    ): PublicMemberProfileDto

    @GET("api/circles")
    suspend fun fetchCircles(): List<CircleDto>

    @GET("api/circles/{circleId}/grid")
    suspend fun fetchSquadGrid(
        @Path("circleId") circleId: String,
        @Query("range") range: String = "all_time",
    ): SquadGridResponseDto

    @POST("api/circles")
    suspend fun createCircle(@Body body: CreateCircleRequestDto): CircleDto

    @GET("api/circles/{circleId}/invites")
    suspend fun fetchCircleInvites(
        @Path("circleId") circleId: String,
    ): List<CircleInviteDto>

    @POST("api/circles/{circleId}/invites")
    suspend fun inviteCircleByPhone(
        @Path("circleId") circleId: String,
        @Body body: InviteCircleByPhoneDto,
    ): CircleInviteDto

    @POST("api/circles/{circleId}/members")
    suspend fun addCircleMember(
        @Path("circleId") circleId: String,
        @Body body: AddCircleMemberRequestDto,
    ): CircleDto

    @PATCH("api/circles/{circleId}/members/{userId}")
    suspend fun patchCircleMemberRole(
        @Path("circleId") circleId: String,
        @Path("userId") userId: String,
        @Body body: PatchCircleMemberRoleRequestDto,
    ): CircleDto

    @DELETE("api/circles/{circleId}/members/{userId}")
    suspend fun removeCircleMember(
        @Path("circleId") circleId: String,
        @Path("userId") userId: String,
    ): CircleDto

    @PATCH("api/circles/{circleId}")
    suspend fun patchCircle(
        @Path("circleId") circleId: String,
        @Body body: PatchCircleRequestDto,
    ): CircleDto

    @POST("api/circles/{circleId}/leave")
    suspend fun leaveCircle(
        @Path("circleId") circleId: String,
    ): LeaveCircleResponseDto

    @GET("api/circle-invites/me")
    suspend fun fetchMyCircleInvites(): JsonArray

    @POST("api/circle-invites/{inviteId}/respond")
    suspend fun respondToCircleInvite(
        @Path("inviteId") inviteId: String,
        @Body body: CircleInviteRespondRequestDto,
    ): CircleInviteRespondDto

    @POST("api/invite-links")
    suspend fun createInviteLink(@Body body: InviteLinkCreateRequestDto): InviteLinkCreateResponseDto

    @GET("api/invite-links/me/balance")
    suspend fun fetchSignupInviteBalance(): SignupInviteBalanceDto

    @GET("api/invite-links/{token}")
    suspend fun fetchInviteLink(
        @Path("token") token: String,
        @Query("squad") squadId: String? = null,
    ): InviteLinkResolveDto

    @POST("api/invite-links/{token}/accept")
    suspend fun acceptInviteLink(
        @Path("token") token: String,
        @Body body: Map<String, String>,
    ): InviteLinkAcceptDto

    @GET("api/chat/circles/{circleId}/messages")
    suspend fun fetchCircleChatMessages(
        @Path("circleId") circleId: String,
        @Query("limit") limit: Int,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null,
    ): CircleChatMessagesResponseDto

    @GET("api/chat/circles/{circleId}/shared-items/summary")
    suspend fun fetchCircleSharedItemsSummary(
        @Path("circleId") circleId: String,
    ): CircleSharedItemsSummaryResponseDto

    @GET("api/chat/circles/{circleId}/shared-items")
    suspend fun fetchCircleSharedItems(
        @Path("circleId") circleId: String,
        @Query("type") type: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): CircleSharedItemsListResponseDto

    @POST("api/chat/circles/{circleId}/messages/upload-urls")
    suspend fun requestCircleChatVideoUploadUrls(
        @Path("circleId") circleId: String,
        @Body body: ChatVideoUploadUrlsRequestDto,
    ): ChatVideoUploadUrlsResponseDto

    @POST("api/chat/circles/{circleId}/messages")
    suspend fun sendCircleChatVideoMessage(
        @Path("circleId") circleId: String,
        @Body body: SendCircleChatVideoMessageDto,
    ): CircleChatSingleMessageDto

    @POST("api/chat/circles/{circleId}/messages")
    suspend fun sendCircleChatMessage(
        @Path("circleId") circleId: String,
        @Body body: SendCircleChatMessageDto,
    ): CircleChatSingleMessageDto

    @Multipart
    @POST("api/chat/circles/{circleId}/messages")
    suspend fun sendCircleChatMessageMultipart(
        @Path("circleId") circleId: String,
        @Part("body") body: RequestBody,
        @Part("clientMessageId") clientMessageId: RequestBody,
        @Part("eventId") eventId: RequestBody?,
        @Part("driveId") driveId: RequestBody?,
        @Part("routeId") routeId: RequestBody?,
        @Part("placeId") placeId: RequestBody?,
        @Part("placeLatitude") placeLatitude: RequestBody?,
        @Part("placeLongitude") placeLongitude: RequestBody?,
        @Part("placeName") placeName: RequestBody?,
        @Part("placeAddressSummary") placeAddressSummary: RequestBody?,
        @Part("replyToMessageId") replyToMessageId: RequestBody?,
        @Part("mentions") mentions: RequestBody?,
        @Part photo: MultipartBody.Part?,
        @Part mapPreview: MultipartBody.Part?,
    ): CircleChatSingleMessageDto

    @PATCH("api/chat/circles/{circleId}/messages/{messageId}")
    suspend fun patchCircleChatMessage(
        @Path("circleId") circleId: String,
        @Path("messageId") messageId: String,
        @Body body: PatchCircleChatMessageDto,
    ): CircleChatSingleMessageDto

    @DELETE("api/chat/circles/{circleId}/messages/{messageId}")
    suspend fun deleteCircleChatMessage(
        @Path("circleId") circleId: String,
        @Path("messageId") messageId: String,
    ): CircleChatSingleMessageDto

    @POST("api/chat/circles/{circleId}/messages/{messageId}/reactions")
    suspend fun postCircleChatReaction(
        @Path("circleId") circleId: String,
        @Path("messageId") messageId: String,
        @Body body: ChatReactionEmojiBodyDto,
    ): CircleChatSingleMessageDto

    @GET("api/chat/circles/{circleId}/next-up-dismissals")
    suspend fun fetchNextUpEventDismissals(
        @Path("circleId") circleId: String,
        @Query("eventIds") eventIds: String? = null,
    ): NextUpEventDismissalsResponseDto

    @PUT("api/chat/circles/{circleId}/next-up-dismissals/{eventId}")
    suspend fun dismissNextUpEventBanner(
        @Path("circleId") circleId: String,
        @Path("eventId") eventId: String,
        @Body body: NextUpEventDismissalRequestDto,
    ): NextUpEventDismissalResponseDto

    @GET("api/direct/conversations")
    suspend fun fetchDirectConversations(): DirectConversationsResponseDto

    @POST("api/direct/conversations")
    suspend fun createDirectConversation(
        @Body body: CreateDirectConversationRequestDto,
    ): DirectConversationSingleDto

    @GET("api/direct/conversations/{conversationId}/messages")
    suspend fun fetchDirectMessages(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null,
    ): DirectMessagesResponseDto

    @POST("api/direct/conversations/{conversationId}/messages/upload-urls")
    suspend fun requestDirectChatVideoUploadUrls(
        @Path("conversationId") conversationId: String,
        @Body body: ChatVideoUploadUrlsRequestDto,
    ): ChatVideoUploadUrlsResponseDto

    @POST("api/direct/conversations/{conversationId}/messages")
    suspend fun sendDirectChatVideoMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendDirectChatVideoMessageDto,
    ): DirectSingleMessageDto

    @POST("api/direct/conversations/{conversationId}/messages")
    suspend fun sendDirectMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendDirectMessageDto,
    ): DirectSingleMessageDto

    @Multipart
    @POST("api/direct/conversations/{conversationId}/messages")
    suspend fun sendDirectMessageMultipart(
        @Path("conversationId") conversationId: String,
        @Part("body") body: RequestBody,
        @Part("clientMessageId") clientMessageId: RequestBody,
        @Part("eventId") eventId: RequestBody?,
        @Part("placeId") placeId: RequestBody?,
        @Part("placeLatitude") placeLatitude: RequestBody?,
        @Part("placeLongitude") placeLongitude: RequestBody?,
        @Part("placeName") placeName: RequestBody?,
        @Part("placeAddressSummary") placeAddressSummary: RequestBody?,
        @Part("replyToMessageId") replyToMessageId: RequestBody?,
        @Part photo: MultipartBody.Part?,
        @Part mapPreview: MultipartBody.Part?,
    ): DirectSingleMessageDto

    @PATCH("api/direct/conversations/{conversationId}/messages/{messageId}")
    suspend fun patchDirectMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: PatchDirectMessageDto,
    ): DirectSingleMessageDto

    @DELETE("api/direct/conversations/{conversationId}/messages/{messageId}")
    suspend fun deleteDirectMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    ): DirectSingleMessageDto

    @POST("api/direct/conversations/{conversationId}/messages/{messageId}/reactions")
    suspend fun postDirectMessageReaction(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: ChatReactionEmojiBodyDto,
    ): DirectSingleMessageDto

    @POST("api/progression/events")
    suspend fun postProgressionEvent(
        @Body body: ProgressionEventRequestDto,
    ): ProgressionEventResponseDto

    @PATCH("api/users/{userId}")
    suspend fun patchUser(
        @Path("userId") userId: String,
        @Body body: JsonObject,
    ): UserDto

    @POST("api/users/me/blocked-users/{targetUserId}")
    suspend fun blockUser(
        @Path("targetUserId") targetUserId: String,
    ): UserDto

    @DELETE("api/users/me/blocked-users/{targetUserId}")
    suspend fun unblockUser(
        @Path("targetUserId") targetUserId: String,
    ): UserDto

    @HTTP(method = "DELETE", path = "api/users/{userId}", hasBody = true)
    suspend fun deleteUserAccount(
        @Path("userId") userId: String,
        @Body body: DeleteAccountRequestDto,
    ): Response<ResponseBody>

    @Multipart
    @POST("api/users/{userId}/avatar")
    suspend fun uploadUserAvatar(
        @Path("userId") userId: String,
        @Part photo: MultipartBody.Part,
    ): UserDto

    @POST("api/presence")
    suspend fun updatePresence(@Body body: PresenceUpdateDto): PresencePostResponseDto

    @GET("api/events")
    suspend fun fetchEvents(
        @Query("scope") scope: String,
        @Query("visibility") visibility: String,
        @Query("limit") limit: Int,
        @Query("circleId") circleId: String? = null,
        @Query("eventType") eventType: String? = null,
    ): List<EventDto>

    @POST("api/events")
    suspend fun createEvent(
        @Body body: CreateEventRequestDto,
    ): EventDto

    @GET("api/events/{eventRef}")
    suspend fun fetchEvent(
        @Path("eventRef") eventRef: String,
        @Query("circleId") circleId: String? = null,
    ): EventDto

    @GET("api/me/admin-squads")
    suspend fun fetchAdminSquads(): AdminSquadsResponseDto

    @GET("api/events/{eventId}/squad-associations")
    suspend fun fetchEventSquadAssociations(
        @Path("eventId") eventId: String,
    ): SquadAssociationsResponseDto

    @PATCH("api/events/{eventId}/squad-associations")
    suspend fun patchEventSquadAssociations(
        @Path("eventId") eventId: String,
        @Body body: PatchSquadAssociationsRequestDto,
    ): SquadAssociationsResponseDto

    @PATCH("api/events/{eventId}")
    suspend fun patchEvent(
        @Path("eventId") eventId: String,
        @Body body: PatchEventRequestDto,
    ): EventDto

    @DELETE("api/events/{eventId}")
    suspend fun deleteEvent(
        @Path("eventId") eventId: String,
    ): Response<ResponseBody>

    @Multipart
    @POST("api/events/{eventId}/banner")
    suspend fun uploadEventBanner(
        @Path("eventId") eventId: String,
        @Part photo: MultipartBody.Part,
        @Part("aspectRatio") aspectRatio: RequestBody,
    ): EventDto

    @PUT("api/events/{eventId}/rsvp")
    suspend fun updateEventRsvp(
        @Path("eventId") eventId: String,
        @Body body: EventRsvpRequestDto,
    ): EventDto

    @POST("api/events/{eventId}/check-ins")
    suspend fun postEventCheckIn(
        @Path("eventId") eventId: String,
        @Body body: EventCheckInRequestDto,
    ): EventCheckInResultDto

    @GET("api/garage/{userId}/cars")
    suspend fun fetchGarageCars(
        @Path("userId") userId: String,
    ): List<GarageCarDto>

    @POST("api/garage/{userId}/cars")
    suspend fun createGarageCar(
        @Path("userId") userId: String,
        @Body body: JsonObject,
    ): GarageCarDto

    @PATCH("api/garage/{userId}/cars/{carId}")
    suspend fun patchGarageCar(
        @Path("userId") userId: String,
        @Path("carId") carId: String,
        @Body body: JsonObject,
    ): GarageCarDto

    @PUT("api/garage/{userId}/cars/reorder")
    suspend fun reorderGarageCars(
        @Path("userId") userId: String,
        @Body body: GarageReorderRequestDto,
    ): List<GarageCarDto>

    @DELETE("api/garage/{userId}/cars/{carId}")
    suspend fun deleteGarageCar(
        @Path("userId") userId: String,
        @Path("carId") carId: String,
    ): Response<ResponseBody>

    @Multipart
    @POST("api/garage/{userId}/cars/{carId}/photo")
    suspend fun uploadGarageCarPhoto(
        @Path("userId") userId: String,
        @Path("carId") carId: String,
        @Part photo: MultipartBody.Part,
    ): GarageCarDto

    @POST("api/drives/start")
    suspend fun startDrive(@Body body: DriveStartDto): DriveDto

    @POST("api/drives/{driveId}/points")
    suspend fun appendDrivePoints(
        @Path("driveId") driveId: String,
        @Body body: AppendDrivePointsDto,
    ): AppendDrivePointsResponseDto

    @POST("api/drives/{driveId}/end")
    suspend fun endDrive(
        @Path("driveId") driveId: String,
        @Body body: DriveEndDto,
    ): DriveDto

    @GET("api/drives/user/{userId}")
    suspend fun fetchUserDrives(
        @Path("userId") userId: String,
    ): List<DriveDto>

    @GET("api/drives/{driveId}")
    suspend fun fetchDrive(
        @Path("driveId") driveId: String,
        @Query("circleId") circleId: String? = null,
    ): DriveDto

    @GET("api/drives/{driveId}/points")
    suspend fun fetchDrivePoints(
        @Path("driveId") driveId: String,
        @Query("circleId") circleId: String? = null,
        @Query("limit") limit: Int = 1200,
    ): DrivePathPointsResponseDto

    @PATCH("api/drives/{driveId}")
    suspend fun patchDrive(
        @Path("driveId") driveId: String,
        @Body body: PatchDriveRequestDto,
    ): DriveDto

    @DELETE("api/drives/{driveId}")
    suspend fun deleteDrive(
        @Path("driveId") driveId: String,
    ): Response<ResponseBody>

    @GET("api/routes")
    suspend fun fetchRoutes(
        @Query("limit") limit: Int = 50,
    ): List<SavedRouteDto>

    @GET("api/routes/shared-with-me")
    suspend fun fetchSharedWithMeRoutes(
        @Query("limit") limit: Int = 50,
    ): SharedWithMeRoutesResponseDto

    @GET("api/routes/{routeId}")
    suspend fun fetchRoute(
        @Path("routeId") routeId: String,
        @Query("circleId") circleId: String? = null,
    ): SavedRouteDto

    @POST("api/routes")
    suspend fun createRoute(
        @Body body: CreateRouteRequestDto,
    ): SavedRouteDto

    @DELETE("api/routes/{routeId}")
    suspend fun deleteRoute(
        @Path("routeId") routeId: String,
    ): Response<ResponseBody>

    @PATCH("api/routes/{routeId}")
    suspend fun patchRoute(
        @Path("routeId") routeId: String,
        @Body body: PatchRouteRequestDto,
    ): SavedRouteDto

    @POST("api/routes/{routeId}/sessions/start")
    suspend fun startRouteDriveSession(
        @Path("routeId") routeId: String,
    ): RouteDriveSessionDto

    @POST("api/routes/sessions/{sessionId}/activate")
    suspend fun activateRouteDriveSession(
        @Path("sessionId") sessionId: String,
        @Body body: RouteDriveSessionRequestDto,
    ): RouteDriveSessionDto

    @PATCH("api/routes/sessions/{sessionId}/progress")
    suspend fun updateRouteDriveSessionProgress(
        @Path("sessionId") sessionId: String,
        @Body body: RouteDriveSessionRequestDto,
    ): RouteDriveSessionDto

    @POST("api/routes/sessions/{sessionId}/complete")
    suspend fun completeRouteDriveSession(
        @Path("sessionId") sessionId: String,
        @Body body: RouteDriveSessionRequestDto,
    ): RouteDriveSessionDto

    @POST("api/routes/sessions/{sessionId}/stop")
    suspend fun stopRouteDriveSession(
        @Path("sessionId") sessionId: String,
        @Body body: RouteDriveSessionRequestDto,
    ): RouteDriveSessionDto

    @GET("api/drives/user/{userId}/stats")
    suspend fun fetchDrivingStats(
        @Path("userId") userId: String,
    ): DrivingStatsDto

    @GET("api/contacts")
    suspend fun fetchContacts(): List<UserDto>

    @GET("api/me/frequent-chat-contacts")
    suspend fun fetchFrequentChatContacts(
        @Query("days") days: Int = 60,
        @Query("limit") limit: Int = 10,
    ): FrequentChatContactsResponseDto

    @GET("api/presence/circle/{circleId}")
    suspend fun fetchPresenceForCircle(
        @Path("circleId") circleId: String,
    ): PresenceCircleResponseDto

    @GET("api/places/mine")
    suspend fun fetchMySavedPlaces(): List<SavedPlaceDto>

    @POST("api/places")
    suspend fun createSavedPlace(
        @Body body: CreateSavedPlaceRequestDto,
    ): SavedPlaceDto

    @PATCH("api/places/{placeId}")
    suspend fun patchSavedPlace(
        @Path("placeId") placeId: String,
        @Body body: JsonObject,
    ): SavedPlaceDto

    @DELETE("api/places/{placeId}")
    suspend fun deleteSavedPlace(
        @Path("placeId") placeId: String,
    ): Response<ResponseBody>

    @GET("api/drive-lines/circle/{circleId}")
    suspend fun fetchDriveLinesForCircle(
        @Path("circleId") circleId: String,
        @Query("limit") limit: Int = 20,
    ): List<DriveLineDto>

    @POST("api/notifications/devices")
    suspend fun registerPushDevice(
        @Body body: RegisterDeviceRequestDto,
    ): RegisterDeviceResponseDto
}
