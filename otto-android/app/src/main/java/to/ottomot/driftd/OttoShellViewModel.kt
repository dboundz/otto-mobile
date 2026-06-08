package to.ottomot.driftd

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.JsonObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import to.ottomot.driftd.core.chat.ChatReadCursorStore
import to.ottomot.driftd.core.chat.ChatTranscriptStore
import to.ottomot.driftd.core.chat.ChatUnreadTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.ottomot.driftd.core.auth.AuthRepository
import to.ottomot.driftd.core.config.OttoEndpoints
import to.ottomot.driftd.core.data.CHAT_MESSAGES_API_MAX_LIMIT
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.core.data.PhotoUploadMaxBytes
import to.ottomot.driftd.core.event.EVENT_CHECK_IN_RADIUS_METERS
import to.ottomot.driftd.core.race.RaceTrackRecord
import to.ottomot.driftd.core.race.RaceTracksDataset
import to.ottomot.driftd.core.event.compareEventsForMainList
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.event.eventHasVenueCoordinates
import to.ottomot.driftd.core.event.eventStartsAtSortKey
import to.ottomot.driftd.core.event.eventVenueLatLng
import to.ottomot.driftd.core.event.haversineMeters
import to.ottomot.driftd.core.event.isWithinEventCheckInWindow
import to.ottomot.driftd.core.OttoPhone
import to.ottomot.driftd.core.location.ActiveDriveLocationService
import to.ottomot.driftd.core.location.ApproximateLocationReader
import to.ottomot.driftd.core.location.LocationFix
import to.ottomot.driftd.core.location.MovementModeIosParity
import to.ottomot.driftd.core.location.OttoLocationPermissions
import to.ottomot.driftd.core.location.isFreshForRouteBuilderCenter
import to.ottomot.driftd.core.notify.ChatFocusBridge
import to.ottomot.driftd.core.audio.OttoTabSoundPlayer
import to.ottomot.driftd.core.notify.EngagementFeedbackAndroid
import to.ottomot.driftd.core.notify.TimeZoneSync
import to.ottomot.driftd.core.chat.SquadChatAllMention
import to.ottomot.driftd.core.chat.parseSquadMentionSpansUtf16
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.CircleChatSenderDto
import to.ottomot.driftd.core.network.dto.ChatVideoAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatEventAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatPlaceAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.ui.squad.isPhonePrimarySquadInviteQuery
import to.ottomot.driftd.ui.squad.isValidNorthAmericanPhoneNumber
import to.ottomot.driftd.ui.squad.normalizedSmsRecipientFromPhone
import to.ottomot.driftd.ui.squad.resolveShareInviteUrl
import to.ottomot.driftd.ui.squad.smsInviteLinkCacheKey
import to.ottomot.driftd.ui.squad.squadInviteSmsBody
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.hasActiveDirectThread
import to.ottomot.driftd.core.network.dto.DirectMessageDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.core.network.dto.DriveEndDto
import to.ottomot.driftd.core.network.dto.DriveLocationPointDto
import to.ottomot.driftd.core.network.dto.DrivePointCaptureDto
import to.ottomot.driftd.core.network.dto.DriveStartDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.DrivingStatsDto
import to.ottomot.driftd.core.network.dto.ProfileLevelUpDto
import to.ottomot.driftd.core.network.dto.GarageCarDto
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.core.network.dto.MyPendingCircleInvite
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.PresenceUpdateDto
import to.ottomot.driftd.core.network.dto.PublicGoingEventDto
import to.ottomot.driftd.core.network.dto.asEventStub
import to.ottomot.driftd.core.network.dto.RegisterDeviceRequestDto
import to.ottomot.driftd.core.network.dto.SavedPlaceDto
import to.ottomot.driftd.core.network.dto.SquadGridResponseDto
import to.ottomot.driftd.core.network.dto.DriveStatsVisibilitySetting
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.network.dto.canAccessRoutes
import to.ottomot.driftd.core.network.dto.UserProfileRealtimeDto
import to.ottomot.driftd.core.network.dto.withPatchedLeaders
import to.ottomot.driftd.core.network.dto.withPatchedProfile
import to.ottomot.driftd.core.network.userVisibleHttpMessage
import to.ottomot.driftd.core.network.InviteLinkParsing
import to.ottomot.driftd.core.realtime.OttoRealtimeCoordinator
import to.ottomot.driftd.map.effectiveMapLayerCircleIds
import to.ottomot.driftd.map.mergePresenceLists
import to.ottomot.driftd.map.mergePresenceMemberUpdate
import to.ottomot.driftd.core.session.SessionRepository
import to.ottomot.driftd.core.analytics.OttoAnalytics

data class EventDetailUi(
    val eventId: String,
    val event: EventDto?,
    val loadingDetail: Boolean,
    val detailError: String? = null,
    val actionBusy: Boolean = false,
    val snackMessage: String? = null,
)

enum class SquadShareInviteBusy {
    PREFETCH,
    COPY,
    SMS,
}

data class SquadSettingsInviteUi(
    val busy: SquadShareInviteBusy? = null,
    val busyCircleId: String? = null,
    val lookupUser: UserDto? = null,
    val lookupLoading: Boolean = false,
    val lookupAttempted: Boolean = false,
    val statusMessage: String? = null,
    val smsInviteOpening: Boolean = false,
    val workingUserId: String? = null,
    val signupInviteRemaining: Int? = null,
    val signupInviteBalanceLoading: Boolean = false,
    val signupInviteEarnAtNextLevelCount: Int? = null,
    val signupInviteNextLevelDisplayName: String? = null,
)

data class CircleDetailUi(
    val circleId: String,
    val circle: CircleDto?,
    val chatMessages: List<CircleChatMessageDto> = emptyList(),
    val chatLoading: Boolean = false,
    val chatSendBusy: Boolean = false,
    val chatSnack: String? = null,
    /** Composer reply target (long-press → Reply), cleared after send. */
    val chatReplyTo: CircleChatMessageDto? = null,
    /** Non-null while the composer is editing an existing message (PATCH on send). */
    val chatEditingMessageId: String? = null,
    /**
     * Upcoming events for this squad ([circleId]): private squad events plus public events where at least one
     * roster member RSVP'd going (matches `GET /api/events?circleId=…`). Fetched when opening squad detail.
     */
    val squadScopedEvents: List<EventDto> = emptyList(),
    val squadScopedEventsLoading: Boolean = false,
    val squadGrid: SquadGridResponseDto? = null,
    val squadGridLoading: Boolean = false,
    val squadGridError: String? = null,
)

data class DirectMessagesOverlayUi(
    val visible: Boolean = false,
    val listLoading: Boolean = false,
    val conversations: List<DirectConversationDto> = emptyList(),
    val selectedConversationId: String? = null,
    val threadTitle: String = "",
    val messages: List<DirectMessageDto> = emptyList(),
    val threadLoading: Boolean = false,
    val sendBusy: Boolean = false,
    val threadSnack: String? = null,
    val threadReplyTo: DirectMessageDto? = null,
    /** Non-null while the DM composer is editing an existing message. */
    val threadEditingMessageId: String? = null,
    /**
     * When true, leaving a thread shows the overlay inbox list. When false (e.g. opened from Squads → DMs),
     * leaving a thread closes the overlay so the Squads inbox stays the source of truth.
     */
    val returnToDmInboxOnThreadBack: Boolean = true,
)

data class MapPeerProfileOverlayUi(
    val userId: String,
    val garageCars: List<GarageCarDto> = emptyList(),
    val publicGoingEvents: List<PublicGoingEventDto> = emptyList(),
    val stats: DrivingStatsDto? = null,
    val loadError: String? = null,
    val loading: Boolean = true,
    /** Header when [OttoShellUiState.contacts] has no row (e.g. Squad Grid — use API fields). */
    val seedDisplayName: String? = null,
    val seedAvatarUrl: String? = null,
    val seedMapAccentKey: String? = null,
    /** When non-null, squad roster actions are shown on the peer profile overlay. */
    val squadManagementCircleId: String? = null,
)

data class PendingMapPresenceFollow(
    val nonce: Long = System.nanoTime(),
    val circleId: String,
    val userId: String,
)

data class PendingMapCoordinateFocus(
    val nonce: Long = System.nanoTime(),
    val latitude: Double,
    val longitude: Double,
    val eventId: String? = null,
    /** Event card for map peek when focus originates from event detail. */
    val eventSnapshot: EventDto? = null,
    val savedPlaceId: String? = null,
    val savedPlaceSnapshot: SavedPlaceDto? = null,
)

data class OttoShellUiState(
    val refreshing: Boolean = false,
    /** Squads tab pull-to-refresh indicator (does not use top [refreshing] bar). */
    val squadsPullRefreshing: Boolean = false,
    /** False until the first [loadCoreFeedsInternal] pass finishes (success or failure). */
    val coreFeedsLoadAttempted: Boolean = false,
    /** True when the last squads roster fetch failed with an empty list. */
    val squadsLoadFailed: Boolean = false,
    val bannerError: String? = null,
    val presenceError: String? = null,
    val circles: List<CircleDto> = emptyList(),
    /** Public featured listings (Events tab → Featured; squad detail Featured browse). */
    val events: List<EventDto> = emptyList(),
    /** Community / third-party listings (Events tab → Community). */
    val communityEvents: List<EventDto> = emptyList(),
    /** Official squad events aggregated across memberships (Events tab → Squads). */
    val squadFeedEvents: List<EventDto> = emptyList(),
    /** Squad-scoped events the user is going to (Events tab → My Events). */
    val squadGoingEvents: List<EventDto> = emptyList(),
    val garageCars: List<GarageCarDto> = emptyList(),
    /** Garage car id shown on map + sent as presence carId while driving/sharing. */
    val selectedSharingCarId: String = "",
    /** False for fixed OTP demo account (`555-555-5555`). */
    val showsDriveCarPicker: Boolean = true,
    val drives: List<DriveDto> = emptyList(),
    val routes: List<SavedRouteDto> = emptyList(),
    val driveSummarySheet: DriveSummarySheetUi? = null,
    val driveShareContext: DriveChatShareContext? = null,
    val savedRouteDetail: SavedRouteDetailSheetUi? = null,
    /** When non-null, Route Builder full-screen editor is open (iOS `AppState.isRouteBuilderPresented`). */
    val routeBuilderEntry: RouteBuilderEntry? = null,
    val isRouteBuilderPresented: Boolean = false,
    val contacts: List<UserDto> = emptyList(),
    val stats: DrivingStatsDto? = null,
    val me: UserDto? = null,
    val presenceMembers: List<PresenceMemberDto> = emptyList(),
    /**
     * Last REST (and realtime-updated) presence rows keyed by squad id. Used for squad bounds on the map
     * where merged [presenceMembers] drops per-squad attribution.
     */
    val presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    /** Selected squad id for map presence and sharing (`""` if none). */
    val mapPresenceCircleId: String = "",
    /** When true, periodically POST presence (+ optional drive telemetry) for live map. */
    val mapSharingLocation: Boolean = false,
    /**
     * True while a live drive telemetry session is in progress (mirrors VM private `activeDriveId`).
     */
    val liveDriveRecordingActive: Boolean = false,
    /**
     * Reserved for Android map route-builder parity with iOS `AppState.isMapRouteSessionActive`; wire when route UX exists.
     */
    val mapRouteSessionActive: Boolean = false,
    /**
     * When non-null and sharing starts, presence sharing auto-stops after this many minutes.
     * Null means manual stop only (“until I stop”).
     */
    val mapShareDurationMinutes: Int? = 60,
    /** When true, presence uploads only while speed indicates driving. */
    val mapShareWhileDrivingOnly: Boolean = false,
    /** Sharing sheet “Record this Drive” — records path/history while sharing (default off). */
    val mapShareSaveDrive: Boolean = false,
    /** Quick/route start dock record toggle (default on). */
    val recordDriveOnStartEnabled: Boolean = true,
    val pendingDriveArchives: List<PendingDriveArchiveDto> = emptyList(),
    /** Squads included in merged map presence; defaults to all memberships after feeds load and grows when new squads appear. */
    val mapLayerSelectedCircleIds: Set<String> = emptySet(),
    val mapLayerShowSavedPlaces: Boolean = true,
    val mapLayerShowUpcomingEvents: Boolean = true,
    val mapLayerShowRaceTracks: Boolean = true,
    val mapLayerShowTraffic: Boolean = true,
    val raceTracks: List<RaceTrackRecord> = emptyList(),
    /** Latest device GPS fix from fused updates (foreground); used to center the map when idle. */
    val deviceLocationFix: LocationFix? = null,
    /**
     * Local movement classification while [mapSharingLocation] (iOS parity: activity + speed + sticky driving).
     * Null when not sharing.
     */
    val deviceMovementMode: String? = null,
    /** Straight-line distance for Events tab (miles); persisted as [selected_event_distance], range 5…200. */
    val selectedEventDistanceMiles: Int = 50,
    /** Short UI tones (iOS Sound settings); persisted in DataStore. */
    val soundEffectsEnabled: Boolean = true,
    /** Squads tab recency sort — epoch seconds when each squad was last opened (iOS parity). */
    val squadLastAccessedAtByCircleId: Map<String, Double> = emptyMap(),
    val savedPlaces: List<SavedPlaceDto> = emptyList(),
    val savedPlacesSnack: String? = null,
    /** Short global confirmation after destructive deletes (iOS `AppToast` parity). */
    val userToastMessage: String? = null,
    /** Upcoming public events the signed-in user is “going” to (public profile payload). */
    val profilePublicGoingEvents: List<PublicGoingEventDto> = emptyList(),
    val eventDetailUi: EventDetailUi? = null,
    val circleDetailUi: CircleDetailUi? = null,
    /** When non-null, squad notification mute dialog is shown (opened from shell TopAppBar). */
    val squadNotificationSettingsCircleId: String? = null,
    val squadSettingsInvite: SquadSettingsInviteUi = SquadSettingsInviteUi(),
    /** Short toast while squad settings dialog is open (shown with dialog Activity context). */
    val squadSettingsToast: String? = null,
    /**
     * Fetched on demand for chat event attachments when the event is past the check-in window
     * and missing from [events] / squad-scoped lists (iOS parity).
     */
    val chatAttachmentHydratedEventsById: Map<String, EventDto> = emptyMap(),
    /** Non-null while PATCH RSVP is in flight (squad/DM chat or event detail). */
    val eventRsvpSubmittingEventId: String? = null,
    val pendingInvites: List<MyPendingCircleInvite> = emptyList(),
    /** Bumped when a squad-invite push is opened; [OttoShell] switches to Squads → Invites. */
    val pendingSquadsInvitesFocusTick: Long = 0,
    /** Bumped when the daily events digest push is opened; Events → My Events sub-tab. */
    val pendingEventsMyEventsFocusTick: Long = 0,
    /** After share-to-squad-chat post; [OttoShell] opens this squad on the Chat tab. */
    val pendingSquadChatFocusCircleId: String? = null,
    val pendingSquadChatFocusTick: Long = 0,
    val squadsSnack: String? = null,
    val invitePreview: InviteLinkResolveDto? = null,
    val profileSaving: Boolean = false,
    val profileSnack: String? = null,
    /** Maps tab add/edit/delete outcomes. */
    val garageSnack: String? = null,
    /** Profile tab → Messages fullscreen overlay */
    val directMessages: DirectMessagesOverlayUi = DirectMessagesOverlayUi(),
    /** Squads tab → New DM compose (1:1 recipient search). */
    val showNewDmCompose: Boolean = false,
    /** Map tap → View Profile (peers): fullscreen overlay backed by drives + stats fetch. */
    val mapPeerProfileOverlay: MapPeerProfileOverlayUi? = null,
    /**
     * iOS `unreadChatCountsByCircleID` — incremented on `circle.chat.message` from others when squad
     * detail is not open for that circle; cleared when opening the squad or while viewing it.
     */
    val unreadChatCountByCircleId: Map<String, Int> = emptyMap(),
    /** Per DM conversation unread message counts (device-local read cursors). */
    val unreadDirectMessageCountByConversationId: Map<String, Int> = emptyMap(),
    /**
     * One-shot when user opens a location-sharing push: Map tab focuses the sharer’s dot (iOS pending focus parity).
     */
    val pendingMapPresenceFollow: PendingMapPresenceFollow? = null,
    /** One-shot: Map tab centers on this coordinate (e.g. event detail map preview tap). */
    val pendingMapCoordinateFocus: PendingMapCoordinateFocus? = null,
    val nextUpEventDismissalsByCircleId: Map<String, List<NextUpEventDismissalDto>> = emptyMap(),
    /** Unified map drive session (quick / route / live); mirrors iOS `activeDriveSession`. */
    val activeDriveSession: DriveSessionState? = null,
    /** Post-stop drive summary overlay (iOS `driveCompleteSummary`). */
    val driveCompleteSummary: DriveCompleteSummary? = null,
    /** Full-screen level-up celebration (iOS `activeProfileLevelUp`). */
    val activeProfileLevelUp: ProfileLevelUpDto? = null,
) {
    val hasActiveDriveSession: Boolean
        get() = activeDriveSession != null || mapSharingLocation || mapRouteSessionActive

    val totalChatUnreadCount: Int
        get() =
            unreadChatCountByCircleId.values.sum() + unreadDirectMessageCountByConversationId.values.sum()

    companion object {
        val PublicPresenceChannelId: String = "public"

        internal const val RsvpInterested: String = "interested"

        internal const val RsvpGoing: String = "going"

        internal const val RsvpNotGoing: String = "not_going"
    }
}


/** Matches iOS `MapScreenPresenceTimer` on physical devices (5s). */
private const val MapPresencePollIntervalMs = 5_000L

/** iOS `sharingToastDedupWindow` (12s). */
private const val SharingToastDedupWindowMs = 12_000L

/** Map-tab GPS poll while not sharing — slightly faster than presence polling for a responsive self pin. */
private const val MapForegroundLocationPollIntervalMs = 2_000L

/** Optional image when sending squad or DM chat (multipart; same contract as iOS). */
data class ChatSendPhotoAttachment(
    val bytes: ByteArray,
    val contentType: String,
)

/** Prepared local video + thumbnail for presigned chat upload. */
data class ChatSendVideoAttachment(
    val prepared: to.ottomot.driftd.core.media.ChatPreparedVideoUpload,
)

class OttoShellViewModel internal constructor(
    private val dataRepository: OttoDataRepository,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val approximateLocationReader: ApproximateLocationReader,
    private val container: AppContainer,
) : ViewModel() {

    private val realtime =
        OttoRealtimeCoordinator(
            client = container.okhttp,
            wsRootUrl = OttoEndpoints.webSocketUrl,
            parentScope = viewModelScope,
        )

    private val chatVideoUploadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val raceTracksDataset =
        RaceTracksDataset(
            context = container.application,
            httpClient = container.okhttp,
        )

    companion object {
        private const val TAG = "OttoShellViewModel"
        private const val QUOTE_JUMP_OLDER_PAGE_LIMIT = 50
        private const val MAP_PREFS_NAME = "otto_map_preferences"
        private const val KEY_MAP_SHARE_SAVE_DRIVE = "mapShareSaveDrive"
        private const val KEY_RECORD_DRIVE_ON_START = "recordDriveOnStartEnabled"
        private const val KEY_SELECTED_SHARING_CAR_ID = "selectedSharingCarId"
        private const val KEY_MAP_LAYER_SHOW_RACE_TRACKS = "mapLayerShowRaceTracks"
        private const val KEY_MAP_LAYER_SHOW_TRAFFIC = "mapLayerShowTraffic"

        internal val MapAccentPaletteKeys =
            listOf(
                "violet",
                "blue",
                "amber",
                "mint",
                "rose",
                "coral",
                "sky",
                "lime",
            )

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(OttoShellViewModel::class.java)) {
                        throw IllegalArgumentException("Unsupported ViewModel type: $modelClass")
                    }
                    return OttoShellViewModel(
                        dataRepository = container.dataRepository,
                        sessionRepository = container.sessionRepository,
                        authRepository = container.authRepository,
                        approximateLocationReader = container.approximateLocationReader,
                        container = container,
                    ) as T
                }
            }
    }

    private val readCursorStore = ChatReadCursorStore(container.application)
    private val transcriptStore = ChatTranscriptStore(container.application)
    private val unreadTracker = ChatUnreadTracker(readCursorStore)

    private var chatPollJob: Job? = null
    private var chatRealtimeConnected = false

    private val _state = MutableStateFlow(OttoShellUiState())
    val state: StateFlow<OttoShellUiState> = _state.asStateFlow()

    /** Squads push routing must not race cold-start [refreshAll]; concurrent loads can reorder state updates. */
    private val coreFeedsMutex = Mutex()

    private val shareInviteLinkByCircleId = mutableMapOf<String, String>()
    private val shareInviteFetchMutexes = mutableMapOf<String, Mutex>()
    private val smsInviteLinkByKey = mutableMapOf<String, String>()
    private var squadInviteLookupJob: Job? = null

    private var presenceJob: Job? = null

    /** iOS `lastKnownActiveSharersByCircleID` — diffed on map presence polls for started-sharing feedback. */
    private val lastKnownActiveSharersByCircleId = mutableMapOf<String, Set<String>>()

    /** iOS `lastSharingToastAtByUserID` — suppress duplicate started-sharing toasts within 12s. */
    private val lastSharingToastAtByUserId = mutableMapOf<String, Long>()

    /** Map tab foreground polling for `GET /api/presence/circle/...` (iOS map timer). */
    private var mapPresencePollJob: Job? = null

    /** High-accuracy GPS poll for the local map pin when not live-sharing (sharing uses [mapShareJob]). */
    private var mapDeviceLocationPollJob: Job? = null

    private var mapForegroundLocationActive = false

    private var mapShareJob: Job? = null

    private var inAppPresenceJob: Job? = null

    private var appInForegroundForPresence: Boolean = false

    private var mapShareExpiryJob: Job? = null
    private var mapShareSessionStartedAtMs: Long? = null
    /** Live sharing started from Quick/Route drive dock — no timer expiry until the drive stops. */
    private var sharingTiedToActiveDrive: Boolean = false
    private var activeDriveId: String? = null
    private var driveSessionSampleJob: Job? = null
    private var lastSessionMetricLat: Double? = null
    private var lastSessionMetricLng: Double? = null

    private fun syncLiveDriveRecordingActiveIntoState() {
        val on = activeDriveId != null
        _state.update { s ->
            if (s.liveDriveRecordingActive == on) s else s.copy(liveDriveRecordingActive = on)
        }
    }

    private var driveDistanceMeters: Double = 0.0
    private var driveMaxMph: Double = 0.0
    private var lastDriveLat: Double? = null
    private var lastDriveLng: Double? = null
    private var lastDrivePointNetworkAtMs: Long = 0L
    private val drivePathTrail = ArrayList<DrivePathSample>()
    private val maxDrivePathTrailCount = 500

    /**
     * While [OttoShellUiState.mapShareWhileDrivingOnly] and not driving, emit one inactive presence
     * payload per idle spell (matches iOS `drivingOnlyNotDrivingInactiveEmitted`).
     */
    private var drivingOnlyPauseInactiveSent: Boolean = false

    /** Last resolved local mode for sticky driving (iOS); cleared when map sharing stops. */
    private var previousLocalMovementMode: String? = null

    /** Fire `daily_launch` once per signed-in VM lifetime (until sign-out resets). */
    private var dailyLaunchPosted: Boolean = false

    /** One automatic retry when the first signed-in feed load fails with no squads (cold start). */
    private var coldStartCoreFeedsRetried: Boolean = false

    init {
        viewModelScope.launch {
            combine(
                unreadTracker.unreadCountByCircleId,
                unreadTracker.unreadCountByConversationId,
            ) { circles, directs ->
                circles to directs
            }.collect { (circles, directs) ->
                _state.update {
                    it.copy(
                        unreadChatCountByCircleId = circles,
                        unreadDirectMessageCountByConversationId = directs,
                    )
                }
            }
        }
        loadMapSharingPreferencesFromDisk()
        loadMapLayerPreferencesFromDisk()
        realtime.onIncoming = { incoming -> handleRealtime(incoming) }
        realtime.onUnauthorized = { sessionRepository.clearCredentialsAsync() }
        viewModelScope.launch {
            sessionRepository.authTokenState.collectLatest { token ->
                realtime.ensureConnected(token)
                if (token.isNullOrBlank()) {
                    dailyLaunchPosted = false
                    coldStartCoreFeedsRetried = false
                    _state.update { s ->
                        s.copy(
                            coreFeedsLoadAttempted = false,
                            refreshing = false,
                            squadsLoadFailed = false,
                        )
                    }
                    unreadTracker.clearAll()
                    transcriptStore.clearAll()
                    stopChatPolling()
                    chatRealtimeConnected = false
                    reconcileInAppPresenceHeartbeat()
                    return@collectLatest
                }
                transcriptStore.bind(sessionRepository.authUserIdState.value)
                refreshAll()
                reconcileInAppPresenceHeartbeat()
            }
        }
        viewModelScope.launch {
            container.deviceLocationTracker.lastFix.collect { fix ->
                _state.update { s -> s.copy(deviceLocationFix = fix) }
                if (_state.value.mapSharingLocation) {
                    recomputeDeviceMovementModeForMapSharing()
                }
            }
        }
        viewModelScope.launch {
            container.activityRecognitionPresenceSupport.ticks.collect {
                if (_state.value.mapSharingLocation) {
                    recomputeDeviceMovementModeForMapSharing()
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.selectedEventDistanceState.collect { miles ->
                _state.update { s -> s.copy(selectedEventDistanceMiles = miles) }
            }
        }
        viewModelScope.launch {
            sessionRepository.soundEffectsEnabledState.collect { on ->
                _state.update { s -> s.copy(soundEffectsEnabled = on) }
            }
        }
        viewModelScope.launch {
            sessionRepository.squadLastAccessedAtState.collect { recency ->
                _state.update { s -> s.copy(squadLastAccessedAtByCircleId = recency) }
            }
        }
    }

    override fun onCleared() {
        realtime.shutdown()
        stopChatPolling()
        mapPresencePollJob?.cancel()
        mapShareJob?.cancel()
        inAppPresenceJob?.cancel()
        mapShareExpiryJob?.cancel()
        container.activityRecognitionPresenceSupport.stop()
        super.onCleared()
    }

    private fun recomputeDeviceMovementModeForMapSharing() {
        if (!_state.value.mapSharingLocation) return
        val speedMps =
            (_state.value.deviceLocationFix?.speedMps ?: 0f).toDouble().coerceAtLeast(0.0)
        resolveAndStoreLocalMovement(speedMps)
    }

    /**
     * Updates [previousLocalMovementMode], [OttoShellUiState.deviceMovementMode], and returns the resolved mode.
     */
    private fun resolveAndStoreLocalMovement(speedMps: Double): String {
        val mode =
            MovementModeIosParity.resolveLocalMovementMode(
                container.activityRecognitionPresenceSupport.latestSnapshot,
                speedMps.coerceAtLeast(0.0),
                previousLocalMovementMode,
            )
        previousLocalMovementMode = mode
        _state.update { it.copy(deviceMovementMode = mode) }
        return mode
    }

    fun refreshAll() {
        viewModelScope.launch {
            loadCoreFeeds()
            refreshRaceTracksIfNeeded()
            loadPresence()
            loadPendingInvites()
        }
    }

    private suspend fun refreshRaceTracksIfNeeded() {
        raceTracksDataset.refreshIfStale()
        _state.update { it.copy(raceTracks = raceTracksDataset.tracks) }
    }

    fun dismissBannerError() {
        _state.update { it.copy(bannerError = null) }
    }

    /** Pull-to-refresh on Squads list — reloads squads-related feeds without the global top progress bar. */
    fun refreshSquadsListPullToRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(squadsPullRefreshing = true, squadsLoadFailed = false) }
            try {
                loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                if (!_state.value.directMessages.visible) {
                    refreshDirectConversationList()
                }
            } finally {
                _state.update { it.copy(squadsPullRefreshing = false) }
            }
        }
    }

    fun dismissSquadsSnack() {
        _state.update { it.copy(squadsSnack = null, invitePreview = null) }
    }

    fun postSquadsSnack(message: String) {
        val m = message.trim().ifBlank { return }
        _state.update { it.copy(squadsSnack = m) }
    }

    suspend fun createSquadScopedEvent(
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
        imageBytes: ByteArray?,
        imageContentType: String?,
    ): Result<EventDto> {
        val cid = circleId.trim()
        if (cid.isEmpty()) return Result.failure(IllegalArgumentException("missing circle"))
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return Result.failure(IllegalArgumentException("missing name"))
        if (!endsAt.isAfter(startsAt)) {
            return Result.failure(IllegalArgumentException("endsAt must be after startsAt"))
        }

        val outcome =
            dataRepository.createCircleScopedEvent(
                circleId = cid,
                name = trimmedName,
                description = description,
                startsAt = startsAt,
                endsAt = endsAt,
                addressLabel = addressLabel,
                streetAddress = streetAddress,
                city = city,
                region = region,
                postalCode = postalCode,
                locationLat = locationLat,
                locationLng = locationLng,
            )

        val withImage =
            outcome.fold(
                onSuccess = { ev ->
                    if (imageBytes != null) {
                        dataRepository.uploadEventBanner(
                            eventId = ev.id,
                            imageBytes = imageBytes,
                            contentType = imageContentType ?: "image/jpeg",
                        )
                    } else {
                        Result.success(ev)
                    }
                },
                onFailure = { Result.failure(it) },
            )

        withImage.fold(
            onSuccess = { ev ->
                _state.update { s ->
                    val detail = s.circleDetailUi?.takeIf { it.circleId == cid } ?: return@update s
                    val merged =
                        (detail.squadScopedEvents + ev)
                            .distinctBy { it.id }
                            .sortedWith(compareBy { eventStartsAtSortKey(it) })
                    s.copy(
                        circleDetailUi = detail.copy(squadScopedEvents = merged),
                        events =
                            if (s.events.none { it.id == ev.id }) {
                                s.events + ev
                            } else {
                                s.events
                            },
                    )
                }
            },
            onFailure = { },
        )

        return withImage
    }

    suspend fun shareCreatedSquadEventToChat(circleId: String, eventId: String): Result<Unit> {
        val cid =
            circleId.trim().takeIf { it.isNotEmpty() }
                ?: return Result.failure(IllegalArgumentException("missing circle"))
        val eid =
            eventId.trim().takeIf { it.isNotEmpty() }
                ?: return Result.failure(IllegalArgumentException("missing event"))
        return dataRepository
            .sendCircleChat(
                circleId = cid,
                body = "",
                clientMessageId = UUID.randomUUID().toString(),
                eventId = eid,
            )
            .fold(
                onSuccess = { msg ->
                    upsertChatMessage(msg.copy(messageType = msg.messageType ?: "user"))
                    Result.success(Unit)
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(
                            circleDetailUi =
                                s.circleDetailUi?.copy(
                                    chatSnack = e.userVisibleHttpMessage("Could not share event to chat."),
                                ),
                        )
                    }
                    Result.failure(e)
                },
            )
    }

    suspend fun shareDriveToSquadChat(circleId: String, context: DriveChatShareContext): Result<Unit> {
        val cid =
            circleId.trim().takeIf { it.isNotEmpty() }
                ?: return Result.failure(IllegalArgumentException("missing circle"))
        val did =
            context.driveId.trim().takeIf { it.isNotEmpty() }
                ?: return Result.failure(IllegalArgumentException("missing drive"))
        val mapPreviewBytes =
            DriveMapPreviewSnapshotResolver
                .resolve(
                    preloaded = context.mapPreviewSnapshotInput,
                    driveId = did,
                    circleId = context.lockedCircleId,
                    dataRepository = dataRepository,
                )?.let { input ->
                    DriveRouteMapSnapshotGenerator.jpegData(input)
                }
        return dataRepository
            .sendCircleChat(
                circleId = cid,
                body = "",
                clientMessageId = UUID.randomUUID().toString(),
                driveId = did,
                mapPreviewBytes = mapPreviewBytes,
            )
            .fold(
                onSuccess = { msg ->
                    upsertChatMessage(msg.copy(messageType = msg.messageType ?: "user"))
                    requestSquadChatFocusAfterShare(cid)
                    _state.update { it.copy(driveSummarySheet = null, driveShareContext = null) }
                    Result.success(Unit)
                },
                onFailure = { e ->
                    postSquadsSnack(e.userVisibleHttpMessage("Could not share drive to chat."))
                    Result.failure(e)
                },
            )
    }

    fun openDriveSummarySheet(
        drive: DriveDto,
        isOwner: Boolean,
        lockedShareCircleId: String? = null,
    ) {
        _state.update {
            it.copy(
                driveSummarySheet =
                    DriveSummarySheetUi(
                        drive = drive,
                        isOwner = isOwner,
                        lockedShareCircleId = lockedShareCircleId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
            )
        }
    }

    fun dismissDriveSummarySheet() {
        _state.update { it.copy(driveSummarySheet = null) }
    }

    fun presentDriveShare(context: DriveChatShareContext) {
        _state.update { it.copy(driveShareContext = context) }
    }

    fun dismissDriveShare() {
        _state.update { it.copy(driveShareContext = null) }
    }

    fun openSavedRouteDetail(route: SavedRouteDto) {
        _state.update { it.copy(savedRouteDetail = SavedRouteDetailSheetUi(route)) }
    }

    fun dismissSavedRouteDetail() {
        _state.update { it.copy(savedRouteDetail = null) }
    }

    fun routeBuilderInitialCenter(mapCenterLat: Double? = null, mapCenterLng: Double? = null): Pair<Double, Double> {
        val fix = _state.value.deviceLocationFix
        if (fix != null && fix.isFreshForRouteBuilderCenter()) {
            return fix.latitude to fix.longitude
        }
        if (mapCenterLat != null && mapCenterLng != null && mapCenterLat.isFinite() && mapCenterLng.isFinite()) {
            return mapCenterLat to mapCenterLng
        }
        return 37.7749 to -122.4194
    }

    fun openRouteBuilderNew(centerLat: Double, centerLng: Double) {
        _state.update {
            it.copy(
                routeBuilderEntry = RouteBuilderEntry.New(centerLat, centerLng),
                isRouteBuilderPresented = true,
                savedRouteDetail = null,
            )
        }
    }

    fun openRouteBuilderForProfile() {
        viewModelScope.launch {
            val fix = approximateLocationReader.currentFixHighAccuracyOrLastKnownOrNull()
            if (fix != null) {
                _state.update { s -> s.copy(deviceLocationFix = fix) }
                openRouteBuilderNew(fix.latitude, fix.longitude)
            } else {
                val (lat, lng) = routeBuilderInitialCenter()
                openRouteBuilderNew(lat, lng)
            }
        }
    }

    fun openRouteBuilderForMap(mapCenterLat: Double? = null, mapCenterLng: Double? = null) {
        viewModelScope.launch {
            val fix = approximateLocationReader.currentFixHighAccuracyOrLastKnownOrNull()
            if (fix != null) {
                _state.update { s -> s.copy(deviceLocationFix = fix) }
                openRouteBuilderNew(fix.latitude, fix.longitude)
            } else {
                val (lat, lng) = routeBuilderInitialCenter(mapCenterLat, mapCenterLng)
                openRouteBuilderNew(lat, lng)
            }
        }
    }

    fun openRouteBuilderEdit(route: SavedRouteDto) {
        _state.update {
            it.copy(
                routeBuilderEntry = RouteBuilderEntry.Edit(route),
                isRouteBuilderPresented = true,
                savedRouteDetail = null,
            )
        }
    }

    fun dismissRouteBuilder() {
        _state.update { it.copy(routeBuilderEntry = null, isRouteBuilderPresented = false) }
    }

    fun onRouteBuilderSaved(route: SavedRouteDto) {
        _state.update { s ->
            val routes =
                if (s.routes.any { ottoUserIdsEqual(it.id, route.id) }) {
                    s.routes.map { if (ottoUserIdsEqual(it.id, route.id)) route else it }
                } else {
                    listOf(route) + s.routes
                }
            s.copy(
                routes = routes,
                routeBuilderEntry = null,
                isRouteBuilderPresented = false,
                userToastMessage = container.application.getString(R.string.route_builder_saved_toast),
            )
        }
    }

    fun requestLocationSyncForRouteBuilder() {
        viewModelScope.launch {
            approximateLocationReader.currentFixHighAccuracyOrLastKnownOrNull()?.let { fix ->
                _state.update { s -> s.copy(deviceLocationFix = fix) }
            }
        }
    }

    suspend fun openSharedDriveSummary(
        driveId: String,
        circleId: String?,
        isOwner: Boolean,
    ) {
        dataRepository.fetchDrive(driveId, circleId)
            .onSuccess { drive -> openDriveSummarySheet(drive, isOwner, lockedShareCircleId = circleId) }
            .onFailure {
                _state.update { s ->
                    s.copy(
                        circleDetailUi =
                            s.circleDetailUi?.copy(
                                chatSnack = it.userVisibleHttpMessage("Couldn't open drive summary."),
                            ),
                    )
                }
            }
    }

    suspend fun patchDriveGarageCar(driveId: String, garageCarId: String?) =
        dataRepository.patchDriveGarageCar(driveId, garageCarId)

    suspend fun renameDrive(driveId: String, title: String): Result<DriveDto> =
        dataRepository.patchDriveTitle(driveId, title).also { result ->
            result.onSuccess { updated ->
                _state.update { s ->
                    s.copy(
                        drives = s.drives.map { if (ottoUserIdsEqual(it.id, updated.id)) updated else it },
                        driveSummarySheet =
                            s.driveSummarySheet
                                ?.takeIf { ottoUserIdsEqual(it.drive.id, updated.id) }
                                ?.copy(drive = updated),
                    )
                }
            }
        }

    suspend fun renameRoute(route: SavedRouteDto, name: String): Result<SavedRouteDto> =
        dataRepository.patchRoute(route, name).also { result ->
            result.onSuccess { updated ->
                _state.update { s ->
                    s.copy(
                        routes = s.routes.map { if (ottoUserIdsEqual(it.id, updated.id)) updated else it },
                        savedRouteDetail =
                            s.savedRouteDetail?.takeIf { ottoUserIdsEqual(it.route.id, updated.id) }
                                ?.copy(route = updated),
                    )
                }
            }
        }

    suspend fun fetchDrivePathSamples(driveId: String, circleId: String?): List<DrivePathSample> =
        dataRepository.fetchDrivePathSamples(driveId, circleId).getOrElse { emptyList() }

    suspend fun deleteDrive(driveId: String): Result<Unit> =
        dataRepository.deleteDrive(driveId).also { result ->
            if (result.isSuccess) {
                presentDeletedToast(
                    container.application.getString(R.string.otto_deleted_item_drive),
                )
                _state.update { s ->
                    s.copy(
                        drives = s.drives.filterNot { ottoUserIdsEqual(it.id, driveId) },
                        driveSummarySheet = null,
                    )
                }
            }
        }

    suspend fun deleteRoute(routeId: String): Result<Unit> =
        dataRepository.deleteRoute(routeId).also { result ->
            if (result.isSuccess) {
                presentDeletedToast(
                    container.application.getString(R.string.otto_deleted_item_route),
                )
                _state.update { s ->
                    s.copy(
                        routes = s.routes.filterNot { ottoUserIdsEqual(it.id, routeId) },
                        savedRouteDetail = s.savedRouteDetail?.takeUnless {
                            ottoUserIdsEqual(it.route.id, routeId)
                        },
                    )
                }
            }
        }

    fun onDriveUpdatedInSummary(updated: DriveDto) {
        _state.update { s ->
            val sheet =
                s.driveSummarySheet?.takeIf { ottoUserIdsEqual(it.drive.id, updated.id) }
                    ?.copy(drive = updated)
            s.copy(
                drives = s.drives.map { if (ottoUserIdsEqual(it.id, updated.id)) updated else it },
                driveSummarySheet = sheet,
            )
        }
    }

    suspend fun updateSquadScopedEvent(
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
        imageBytes: ByteArray?,
        imageContentType: String?,
    ): Result<Unit> {
        val trimmedName = name.trim()
        if (eventId.isBlank() || trimmedName.isEmpty()) return Result.failure(IllegalArgumentException("missing event"))
        if (!endsAt.isAfter(startsAt)) return Result.failure(IllegalArgumentException("endsAt must be after startsAt"))

        val outcome =
            dataRepository.updateCircleScopedEvent(
                eventId = eventId,
                name = trimmedName,
                description = description,
                startsAt = startsAt,
                endsAt = endsAt,
                addressLabel = addressLabel,
                streetAddress = streetAddress,
                city = city,
                region = region,
                postalCode = postalCode,
                locationLat = locationLat,
                locationLng = locationLng,
            ).fold(
                onSuccess = { updated ->
                    if (imageBytes != null) {
                        dataRepository.uploadEventBanner(
                            eventId = updated.id,
                            imageBytes = imageBytes,
                            contentType = imageContentType ?: "image/jpeg",
                        )
                    } else {
                        Result.success(updated)
                    }
                },
                onFailure = { Result.failure(it) },
            )

        outcome.onSuccess { ev ->
            _state.update { s ->
                val detail = s.circleDetailUi
                s.copy(
                    events = s.events.map { if (it.id == ev.id) ev else it },
                    eventDetailUi = s.eventDetailUi?.takeIf { it.eventId == ev.id }?.copy(event = ev),
                    circleDetailUi =
                        detail?.copy(
                            squadScopedEvents =
                                detail.squadScopedEvents
                                    .map { if (it.id == ev.id) ev else it }
                                    .sortedWith(compareBy { eventStartsAtSortKey(it) }),
                        ),
                )
            }
        }
        return outcome.map { }
    }

    suspend fun deleteSquadScopedEvent(eventId: String): Result<Unit> {
        val id = eventId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("missing event"))
        val outcome = dataRepository.deleteEvent(id)
        outcome.onSuccess {
            presentDeletedToast(
                container.application.getString(R.string.otto_deleted_item_event),
            )
            _state.update { s ->
                s.copy(
                    events = s.events.filterNot { it.id == id },
                    eventDetailUi = s.eventDetailUi?.takeIf { it.eventId != id },
                    circleDetailUi =
                        s.circleDetailUi?.let { detail ->
                            detail.copy(squadScopedEvents = detail.squadScopedEvents.filterNot { it.id == id })
                        },
                )
            }
        }
        return outcome
    }

    fun dismissGarageSnack() {
        _state.update { it.copy(garageSnack = null) }
    }

    fun dismissSavedPlacesSnack() {
        _state.update { it.copy(savedPlacesSnack = null) }
    }

    fun dismissUserToast() {
        _state.update { it.copy(userToastMessage = null) }
    }

    private fun presentDeletedToast(itemLabel: String) {
        val app = container.application
        _state.update {
            it.copy(
                userToastMessage =
                    app.getString(R.string.otto_item_deleted_format, itemLabel),
            )
        }
    }

    fun saveMapPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        addressSummary: String? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (!latitude.isFinite() || !longitude.isFinite()) return
        viewModelScope.launch {
            dataRepository.createSavedPlace(trimmed, latitude, longitude, addressSummary = addressSummary).fold(
                onSuccess = { dto ->
                    _state.update { s ->
                        s.copy(
                            savedPlaces = listOf(dto) + s.savedPlaces.filter { it.id != dto.id },
                            savedPlacesSnack = "Added to My Places",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            savedPlacesSnack = e.userVisibleHttpMessage("Could not save place."),
                        )
                    }
                },
            )
        }
    }

    fun deleteSavedPlace(placeId: String) {
        val id = placeId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            dataRepository.deleteSavedPlace(id).fold(
                onSuccess = {
                    presentDeletedToast(
                        container.application.getString(R.string.otto_deleted_item_place),
                    )
                    _state.update { s ->
                        s.copy(savedPlaces = s.savedPlaces.filter { it.id != id }, savedPlacesSnack = null)
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(savedPlacesSnack = e.userVisibleHttpMessage("Could not delete place."))
                    }
                },
            )
        }
    }

    fun renameSavedPlace(
        placeId: String,
        name: String,
    ) {
        val id = placeId.trim()
        val trimmed = name.trim()
        if (id.isEmpty() || trimmed.isEmpty()) return
        viewModelScope.launch {
            dataRepository.renameSavedPlace(id, trimmed).fold(
                onSuccess = { dto ->
                    _state.update { s ->
                        s.copy(
                            savedPlaces =
                                s.savedPlaces.map { existing ->
                                    if (existing.id == dto.id) dto else existing
                                },
                            savedPlacesSnack = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(savedPlacesSnack = e.userVisibleHttpMessage("Could not rename place."))
                    }
                },
            )
        }
    }

    fun registerAndroidPushDevice(fcmRegistrationToken: String) {
        val tok = fcmRegistrationToken.trim()
        if (tok.length < 32) return
        viewModelScope.launch {
            dataRepository.registerPushDevice(
                RegisterDeviceRequestDto.forAndroidFcm(
                    token = tok,
                    applicationId = BuildConfig.APPLICATION_ID,
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                ),
            ).onFailure { }
        }
    }

    fun consumeInviteDeepLink(rawUriOrToken: String) {
        val parsed = InviteLinkParsing.parseInviteDeepLink(rawUriOrToken)
        if (parsed != null) {
            redeemInviteInspect(parsed.first, parsed.second)
            return
        }
        redeemInviteInspect(rawUriOrToken, null)
    }

    fun uploadProfileAvatarPhoto(
        imageBytes: ByteArray,
        contentType: String,
    ) {
        if (imageBytes.size > PhotoUploadMaxBytes) {
            _state.update { it.copy(profileSnack = "Image too large (max 24 MB).") }
            return
        }
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@launch
            val ext =
                when {
                    contentType.contains("png", ignoreCase = true) -> "avatar.png"
                    contentType.contains("webp", ignoreCase = true) -> "avatar.webp"
                    else -> "avatar.jpg"
                }
            dataRepository.uploadUserAvatar(userId, imageBytes, ext, contentType).fold(
                onSuccess = { dto ->
                    _state.update {
                        it.copy(me = dto, profileSnack = "Photo updated.", profileSaving = false)
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(profileSnack = e.userVisibleHttpMessage("Could not upload photo."))
                    }
                },
            )
        }
    }

    fun uploadGarageCarPhoto(
        carId: String,
        imageBytes: ByteArray,
        contentType: String,
    ) {
        val cid = carId.trim()
        if (cid.isEmpty()) return
        if (imageBytes.size > PhotoUploadMaxBytes) {
            _state.update { it.copy(garageSnack = "Image too large (max 24 MB).") }
            return
        }
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@launch
            val ext =
                when {
                    contentType.contains("png", ignoreCase = true) -> "photo.png"
                    contentType.contains("webp", ignoreCase = true) -> "photo.webp"
                    else -> "photo.jpg"
                }
            dataRepository.uploadGarageCarPhoto(userId, cid, imageBytes, ext, contentType).fold(
                onSuccess = { dto ->
                    _state.update { s ->
                        s.copy(
                            garageCars =
                                s.garageCars.map { existing ->
                                    if (existing.id == dto.id) dto else existing
                                },
                            garageSnack = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(garageSnack = e.userVisibleHttpMessage("Could not upload car photo."))
                    }
                },
            )
        }
    }

    fun setInvitePreview(resolve: InviteLinkResolveDto?) {
        _state.update { it.copy(invitePreview = resolve) }
    }

    fun refreshPresenceCircle(showStartedSharingFeedback: Boolean = false) {
        presenceJob?.cancel()
        presenceJob =
            viewModelScope.launch {
                loadPresence(showStartedSharingFeedback = showStartedSharingFeedback)
            }
    }

    /**
     * iOS polls presence on an interval while the map is visible (`MapScreenPresenceTimer`).
     * Without this, Android relies only on websockets + manual refresh, so server logs show
     * far fewer `GET /api/presence/circle/...` calls than iOS.
     */
    internal fun setMapPresencePollingActive(active: Boolean) {
        mapPresencePollJob?.cancel()
        mapPresencePollJob = null
        if (!active) return
        mapPresencePollJob =
            viewModelScope.launch {
                while (isActive) {
                    val token = sessionRepository.authTokenState.value
                    if (!token.isNullOrBlank()) {
                        loadPresence(showStartedSharingFeedback = true)
                    }
                    delay(MapPresencePollIntervalMs)
                }
            }
    }

    /**
     * Keeps [OttoShellUiState.deviceLocationFix] fresh on the Map tab when the user is not sharing.
     * Live sharing already polls high-accuracy fixes in [setMapLocationSharing]; without this, the
     * map pin only moved when presence round-trips or on coarse 15 m fused updates.
     */
    internal fun setMapForegroundLocationActive(active: Boolean) {
        mapForegroundLocationActive = active
        container.deviceLocationTracker.setMapForegroundActive(active)
        mapDeviceLocationPollJob?.cancel()
        mapDeviceLocationPollJob = null
        if (!active) return
        container.deviceLocationTracker.tryStartListening()
        mapDeviceLocationPollJob =
            viewModelScope.launch {
                while (isActive) {
                    val fix = container.approximateLocationReader.currentFixHighAccuracyOrNull()
                    if (fix != null) {
                        container.deviceLocationTracker.publishFix(fix, force = true)
                    }
                    delay(MapForegroundLocationPollIntervalMs)
                }
            }
    }

    fun setPresenceScope(circleId: String) {
        _state.update { it.copy(mapPresenceCircleId = circleId) }
        refreshPresenceCircle()
    }

    fun consumePendingMapPresenceFollow() {
        _state.update { it.copy(pendingMapPresenceFollow = null) }
    }

    /**
     * Routes notification `data` payloads (FCM / tap intent extras) in line with iOS `handleRemoteNotificationTap`.
     */
    fun handlePushNotificationRouting(data: Map<String, String>) {
        val type = data["type"]?.trim()?.takeIf { it.isNotEmpty() } ?: return
        when (type) {
            "profile.progression.level_up" -> {
                val levelUp =
                    data["levelUp"]?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                        dataRepository.parseProfileLevelUp(raw)
                    }
                if (levelUp != null) {
                    presentProfileLevelUp(levelUp)
                } else {
                    viewModelScope.launch {
                        loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                    }
                }
            }
            "event.events_today" -> {
                _state.update {
                    it.copy(pendingEventsMyEventsFocusTick = it.pendingEventsMyEventsFocusTick + 1)
                }
            }
            "event.check_in",
            "event.auto_check_in",
            "circle.event.invited",
            -> {
                val ref =
                    data["eventRef"]?.trim()?.takeIf { it.isNotEmpty() }
                        ?: data["eventId"]?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return
                openEventDetail(ref)
            }
            "direct.message",
            "direct.message.reaction",
            -> {
                val conversationId = data["conversationId"]?.trim()?.takeIf { it.isNotEmpty() }
                val uid = data["senderUserId"]?.trim()?.takeIf { it.isNotEmpty() }
                if (conversationId == null && uid == null) return
                viewModelScope.launch {
                    loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                    if (conversationId != null) {
                        val dm = _state.value.directMessages
                        val alreadyViewing =
                            dm.visible &&
                                dm.selectedConversationId != null &&
                                ottoUserIdsEqual(dm.selectedConversationId, conversationId)
                        if (alreadyViewing) {
                            refreshOpenChatIfVisible()
                        } else {
                            openDirectThreadFullScreenFromPushConversationId(conversationId)
                        }
                    } else if (uid != null) {
                        startDirectWithContact(uid)
                    }
                }
            }
            "circle.invite.received" -> {
                viewModelScope.launch {
                    loadPendingInvites()
                    _state.update { it.copy(pendingSquadsInvitesFocusTick = it.pendingSquadsInvitesFocusTick + 1) }
                }
            }
            "circle.member.added" -> {
                val cid = data["circleId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return
                viewModelScope.launch {
                    coreFeedsMutex.withLock {
                        if (_state.value.circles.none { c -> ottoUserIdsEqual(c.id, cid) }) {
                            loadCoreFeedsInternal(updateGlobalRefreshingIndicator = false)
                        }
                    }
                    openCircleDetail(cid)
                }
            }
            "circle.chat.reply",
            "circle.chat.new_message",
            "circle.chat.mention",
            "circle.chat.reaction",
            -> {
                val cid = data["circleId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return
                viewModelScope.launch {
                    coreFeedsMutex.withLock {
                        if (_state.value.circles.none { c -> ottoUserIdsEqual(c.id, cid) }) {
                            loadCoreFeedsInternal(updateGlobalRefreshingIndicator = false)
                        }
                    }
                    val alreadyOnSquad =
                        _state.value.circleDetailUi?.circleId?.let { current ->
                            ottoUserIdsEqual(current, cid)
                        } == true
                    if (alreadyOnSquad) {
                        requestSquadChatFocusAfterShare(cid)
                        refreshOpenChatIfVisible()
                    } else {
                        openCircleDetail(cid)
                        requestSquadChatFocusAfterShare(cid)
                    }
                }
            }
            "presence.location_started" -> {
                val cid = data["circleId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return
                val uid = data["userId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return
                viewModelScope.launch {
                    coreFeedsMutex.withLock {
                        if (_state.value.circles.none { c -> ottoUserIdsEqual(c.id, cid) }) {
                            loadCoreFeedsInternal(updateGlobalRefreshingIndicator = false)
                        }
                    }
                    if (_state.value.circles.any { c -> ottoUserIdsEqual(c.id, cid) }) {
                        applyPresenceSharingPush(cid, uid)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun applyPresenceSharingPush(
        circleId: String,
        sharerUserId: String,
    ) {
        val cid = circleId.trim()
        val uid = sharerUserId.trim()
        _state.update { st ->
            val narrowedLayers = st.mapLayerSelectedCircleIds.isNotEmpty()
            val nextLayers =
                if (narrowedLayers) {
                    st.mapLayerSelectedCircleIds.toMutableSet().apply { add(cid) }
                } else {
                    st.mapLayerSelectedCircleIds
                }
            st.copy(
                mapPresenceCircleId = cid,
                mapLayerSelectedCircleIds = nextLayers,
                pendingMapPresenceFollow = PendingMapPresenceFollow(circleId = cid, userId = uid),
            )
        }
        refreshPresenceCircle()
    }

    private fun mapPreferences() =
        container.application.getSharedPreferences(MAP_PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadMapSharingPreferencesFromDisk() {
        val prefs = mapPreferences()
        val saveDrive = prefs.getBoolean(KEY_MAP_SHARE_SAVE_DRIVE, false)
        val recordOnStart = prefs.getBoolean(KEY_RECORD_DRIVE_ON_START, true)
        val selectedCar = prefs.getString(KEY_SELECTED_SHARING_CAR_ID, "").orEmpty()
        val pending = PendingDriveStore.load(container.application)
        _state.update {
            it.copy(
                mapShareSaveDrive = saveDrive,
                recordDriveOnStartEnabled = recordOnStart,
                selectedSharingCarId = selectedCar,
                pendingDriveArchives = pending,
            )
        }
    }

    private fun reconcileSelectedSharingCarId(
        cars: List<GarageCarDto>,
        current: String,
    ): String {
        val trimmed = current.trim()
        if (trimmed.isNotEmpty() && cars.any { it.id == trimmed }) return trimmed
        val next =
            cars.firstOrNull { it.isPrimary == true }?.id?.trim()?.takeIf { it.isNotEmpty() }
                ?: cars.firstOrNull()?.id?.trim().orEmpty()
        if (next != trimmed) {
            mapPreferences().edit().putString(KEY_SELECTED_SHARING_CAR_ID, next).apply()
        }
        return next
    }

    fun selectSharingCar(carId: String?) {
        if (!_state.value.showsDriveCarPicker) return
        val next = carId?.trim().orEmpty()
        mapPreferences().edit().putString(KEY_SELECTED_SHARING_CAR_ID, next).apply()
        _state.update { it.copy(selectedSharingCarId = next) }
    }

    private fun showsDriveCarPickerFor(phoneNumber: String?): Boolean =
        !OttoPhone.isDemoBypassPhone(phoneNumber)

    private fun reconcileDriveCarPickerForUser(phoneNumber: String?) {
        if (!showsDriveCarPickerFor(phoneNumber)) {
            mapPreferences().edit().putString(KEY_SELECTED_SHARING_CAR_ID, "").apply()
            _state.update {
                it.copy(
                    selectedSharingCarId = "",
                    showsDriveCarPicker = false,
                )
            }
            return
        }
        _state.update { it.copy(showsDriveCarPicker = true) }
    }

    private fun reconcileActiveDriveLocationService() {
        val active = _state.value.activeDriveSession != null
        val backgroundGranted =
            OttoLocationPermissions.backgroundLocationGranted(container.application)
        if (active && backgroundGranted) {
            ActiveDriveLocationService.start(container.application)
        } else {
            ActiveDriveLocationService.stop(container.application)
        }
    }

    private fun presenceCarIdForUpload(): String? {
        if (!_state.value.showsDriveCarPicker) return null
        return _state.value.selectedSharingCarId.trim().takeIf { it.isNotEmpty() }
    }

    fun reloadPendingDriveArchives() {
        val pending = PendingDriveStore.load(container.application)
        PendingDriveStore.save(container.application, pending)
        _state.update { it.copy(pendingDriveArchives = pending) }
    }

    fun setRecordDriveOnStartEnabled(enabled: Boolean) {
        mapPreferences().edit().putBoolean(KEY_RECORD_DRIVE_ON_START, enabled).apply()
        _state.update { it.copy(recordDriveOnStartEnabled = enabled) }
    }

    fun deletePendingDriveArchive(id: String) {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return
        val updated = _state.value.pendingDriveArchives.filterNot { it.id == trimmed }
        PendingDriveStore.save(container.application, updated)
        _state.update { it.copy(pendingDriveArchives = updated) }
    }

    fun retryPendingDriveSave(id: String) {
        viewModelScope.launch {
            val archive = _state.value.pendingDriveArchives.firstOrNull { it.id == id.trim() } ?: return@launch
            val uid = sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val success =
                runCatching {
                    val driveId =
                        archive.backendDriveId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: run {
                                val start = archive.pathSamples.firstOrNull()
                                dataRepository.startDrivingSession(
                                    DriveStartDto(
                                        circleId = archive.circleId ?: _state.value.circles.firstOrNull()?.id.orEmpty(),
                                        sharingAudience = "onlyMe",
                                        sharedCircleIds = archive.sharedCircleIds.ifEmpty {
                                            _state.value.circles.map { it.id }
                                        },
                                        title = archive.title,
                                        startTime = archive.startedAt,
                                        startLocation =
                                            start?.let {
                                                DriveLocationPointDto(lat = it.lat, lng = it.lng)
                                            },
                                    ),
                                ).getOrThrow().id
                            }
                    if (archive.pathSamples.isNotEmpty()) {
                        dataRepository.appendDrivingPoints(
                            driveId,
                            archive.pathSamples.map {
                                DrivePointCaptureDto(
                                    lat = it.lat,
                                    lng = it.lng,
                                    speedMph = it.speedMph,
                                    heading = null,
                                    accuracyMeters = null,
                                    capturedAt = it.capturedAt ?: Instant.now().toString(),
                                )
                            },
                        ).getOrThrow()
                    }
                    val end = archive.pathSamples.lastOrNull()
                    dataRepository.endDrivingSession(
                        driveId = driveId,
                        payload =
                            DriveEndDto(
                                endTime = archive.endedAt,
                                distanceMeters = archive.distanceMeters.takeIf { it > 0.0 },
                                maxSpeedMph = archive.maxSpeedMph.takeIf { it > 0.0 },
                                avgSpeedMph = archive.avgSpeedMph.takeIf { it > 0.0 },
                                endLocation =
                                    end?.let {
                                        DriveLocationPointDto(lat = it.lat, lng = it.lng)
                                    },
                            ),
                    ).getOrThrow()
                    true
                }.getOrDefault(false)
            if (success) {
                deletePendingDriveArchive(archive.id)
                dataRepository.drives(uid).onSuccess { list ->
                    _state.update { it.copy(drives = list) }
                }
                postSquadsSnack(container.application.getString(R.string.drive_pending_saved_toast))
            } else {
                val updated =
                    _state.value.pendingDriveArchives.map {
                        if (it.id == archive.id) it.copy(retryCount = it.retryCount + 1) else it
                    }
                PendingDriveStore.save(container.application, updated)
                _state.update { it.copy(pendingDriveArchives = updated) }
                postSquadsSnack(container.application.getString(R.string.drive_pending_retry_failed_toast))
            }
        }
    }

    private fun archivePendingDrive(
        failurePhase: String,
        kind: String,
        title: String,
        startedAt: Instant,
        endedAt: Instant,
        distanceMeters: Double,
        maxSpeedMph: Double,
        avgSpeedMph: Double,
        backendDriveId: String?,
        pathSamples: List<PendingDrivePathSampleDto>,
    ) {
        val archive =
            PendingDriveStore.makeArchive(
                failurePhase = failurePhase,
                kind = kind,
                title = title,
                startedAt = startedAt,
                endedAt = endedAt,
                distanceMeters = distanceMeters,
                maxSpeedMph = maxSpeedMph,
                avgSpeedMph = avgSpeedMph,
                backendDriveId = backendDriveId,
                circleId = _state.value.circles.firstOrNull()?.id,
                sharedCircleIds = _state.value.circles.map { it.id },
                pathSamples = pathSamples,
            ) ?: return
        val updated = listOf(archive) + _state.value.pendingDriveArchives
        PendingDriveStore.save(container.application, updated)
        _state.update { it.copy(pendingDriveArchives = updated) }
        postSquadsSnack(container.application.getString(R.string.drive_pending_archived_toast))
    }

    private fun loadMapLayerPreferencesFromDisk() {
        val prefs = mapPreferences()
        val showRaceTracks = prefs.getBoolean(KEY_MAP_LAYER_SHOW_RACE_TRACKS, true)
        val showTraffic = prefs.getBoolean(KEY_MAP_LAYER_SHOW_TRAFFIC, true)
        _state.update {
            it.copy(
                mapLayerShowRaceTracks = showRaceTracks,
                mapLayerShowTraffic = showTraffic,
                raceTracks = raceTracksDataset.tracks,
            )
        }
    }

    private fun persistMapShareSaveDrive(enabled: Boolean) {
        mapPreferences().edit().putBoolean(KEY_MAP_SHARE_SAVE_DRIVE, enabled).apply()
    }

    fun setMapSharingOptions(
        durationMinutes: Int?,
        whileDrivingOnly: Boolean,
        saveDrive: Boolean,
    ) {
        persistMapShareSaveDrive(saveDrive)
        _state.update {
            it.copy(
                mapShareDurationMinutes = durationMinutes,
                mapShareWhileDrivingOnly = whileDrivingOnly,
                mapShareSaveDrive = saveDrive,
            )
        }
    }

    fun setMapShareSaveDrive(enabled: Boolean) {
        val wasEnabled = _state.value.mapShareSaveDrive
        persistMapShareSaveDrive(enabled)
        _state.update { it.copy(mapShareSaveDrive = enabled) }
        if (!_state.value.mapSharingLocation) return
        viewModelScope.launch {
            if (enabled && !wasEnabled) {
                val loc =
                    approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                        it.latitude to it.longitude
                    }
                maybeStartDriveIfNeeded(loc)
            } else if (!enabled && wasEnabled) {
                val endLoc =
                    approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                        DriveLocationPointDto(lat = it.latitude, lng = it.longitude)
                    }
                endDrivingSessionQuietly(locationEnd = endLoc)
            }
        }
    }

    /**
     * Restarts the auto-stop timer from now using [durationMinutes] (matches iOS `extendSharingSession`).
     * No-op if not sharing or [durationMinutes] is not positive.
     */
    fun extendMapSharingSession(durationMinutes: Int) {
        if (!_state.value.mapSharingLocation) return
        if (durationMinutes <= 0) return
        mapShareExpiryJob?.cancel()
        _state.update { it.copy(mapShareDurationMinutes = durationMinutes) }
        mapShareExpiryJob =
            viewModelScope.launch {
                delay(durationMinutes * 60_000L)
                setMapLocationSharing(false)
            }
    }

    fun setMapLayerShowSavedPlaces(show: Boolean) {
        _state.update { it.copy(mapLayerShowSavedPlaces = show) }
    }

    fun setMapLayerShowUpcomingEvents(show: Boolean) {
        _state.update { it.copy(mapLayerShowUpcomingEvents = show) }
    }

    fun setMapLayerShowRaceTracks(show: Boolean) {
        mapPreferences().edit().putBoolean(KEY_MAP_LAYER_SHOW_RACE_TRACKS, show).apply()
        _state.update { it.copy(mapLayerShowRaceTracks = show) }
    }

    fun setMapLayerShowTraffic(show: Boolean) {
        mapPreferences().edit().putBoolean(KEY_MAP_LAYER_SHOW_TRAFFIC, show).apply()
        _state.update { it.copy(mapLayerShowTraffic = show) }
    }

    fun setSelectedEventDistance(miles: Int) {
        viewModelScope.launch {
            sessionRepository.setSelectedEventDistance(miles)
            loadCoreFeeds()
        }
    }

    fun setMapLayerCircleVisible(
        circleId: String,
        visible: Boolean,
    ) {
        val id = circleId.trim().ifBlank { return }
        _state.update { st ->
            val valid = st.circles.map { c -> c.id }.filter { cid -> cid.isNotBlank() }.toSet()
            if (id !in valid) return@update st
            var next = st.mapLayerSelectedCircleIds.toMutableSet()
            if (next.isEmpty()) {
                next.addAll(valid)
            }
            if (visible) {
                next.add(id)
            } else {
                next.remove(id)
            }
            val final =
                if (!visible && next.isEmpty()) {
                    emptySet()
                } else {
                    next
                }
            st.copy(mapLayerSelectedCircleIds = final)
        }
        refreshPresenceCircle(showStartedSharingFeedback = true)
    }

    fun notifyAppInForegroundForPresence(inForeground: Boolean) {
        if (!inForeground) {
            viewModelScope.launch {
                val tok = sessionRepository.authTokenState.value?.trim()
                if (tok.isNullOrEmpty()) return@launch
                val circleIds =
                    _state.value.circles
                        .mapNotNull { c -> c.id?.trim()?.takeIf { it.isNotBlank() } }
                        .distinct()
                for (id in circleIds) {
                    runCatching {
                        dataRepository.pushPresence(
                            PresenceUpdateDto(
                                circleId = id,
                                isActive = false,
                                inApp = false,
                                speedMph = 0.0,
                                movementMode = "unknown",
                                capturedAt = Instant.now().toString(),
                                trackDrivingStats = false,
                            ),
                        )
                    }
                }
            }
        }
        appInForegroundForPresence = inForeground
        container.deviceLocationTracker.setAppInForeground(inForeground)
        if (inForeground) {
            viewModelScope.launch { TimeZoneSync.syncIfNeeded(container) }
            refreshOpenChatIfVisible()
        }
        if (inForeground && mapForegroundLocationActive) {
            setMapForegroundLocationActive(true)
        }
        reconcileInAppPresenceHeartbeat()
    }

    private fun reconcileInAppPresenceHeartbeat() {
        val token = sessionRepository.authTokenState.value
        val sharing = _state.value.mapSharingLocation
        val shouldRun =
            appInForegroundForPresence &&
                !token.isNullOrBlank() &&
                !sharing

        if (!shouldRun) {
            inAppPresenceJob?.cancel()
            inAppPresenceJob = null
            return
        }
        if (inAppPresenceJob?.isActive == true) return

        inAppPresenceJob =
            viewModelScope.launch {
                while (isActive) {
                    val snap = _state.value
                    val circleIds =
                        snap.circles
                            .mapNotNull { c -> c.id?.trim()?.takeIf { it.isNotBlank() } }
                            .distinct()
                    val tok = sessionRepository.authTokenState.value
                    if (circleIds.isNotEmpty() &&
                        appInForegroundForPresence &&
                        !tok.isNullOrBlank() &&
                        !_state.value.mapSharingLocation
                    ) {
                        val capturedAt = Instant.now().toString()
                        for (id in circleIds) {
                            dataRepository.pushPresence(
                                PresenceUpdateDto(
                                    circleId = id,
                                    isActive = false,
                                    inApp = true,
                                    speedMph = 0.0,
                                    movementMode = "unknown",
                                    capturedAt = capturedAt,
                                    trackDrivingStats = false,
                                ),
                            )
                        }
                    }
                    delay(45_000L)
                }
            }
    }

    /** Toggle live presence + lightweight drive telemetry (mirrors shipping iOS “sharing”). */
    fun setMapLocationSharing(
        enabled: Boolean,
        disabledReason: String = "user",
    ) {
        drivingOnlyPauseInactiveSent = false
        mapShareJob?.cancel()
        mapShareExpiryJob?.cancel()
        if (!enabled) {
            sharingTiedToActiveDrive = false
            OttoAnalytics.logLocationSharingDisabled(disabledReason)
            container.activityRecognitionPresenceSupport.stop()
            previousLocalMovementMode = null
            mapShareSessionStartedAtMs = null
            _state.update { st ->
                val session = st.activeDriveSession?.copy(isSharing = false)
                st.copy(
                    mapSharingLocation = false,
                    deviceMovementMode = null,
                    activeDriveSession = session,
                )
            }
            viewModelScope.launch {
                if (_state.value.activeDriveSession?.isRecording != true) {
                    endDrivingSessionQuietly(
                        locationEnd =
                            approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                                DriveLocationPointDto(lat = it.latitude, lng = it.longitude)
                            },
                    )
                }
                markPresenceInactiveSweep()
                syncRealtimeSubscriptions()
                reconcileDriveSessionSampleJob()
            }
            reconcileInAppPresenceHeartbeat()
            if (mapForegroundLocationActive) {
                setMapForegroundLocationActive(true)
            }
            return
        }

        OttoAnalytics.logLocationSharingEnabled()
        mapShareSessionStartedAtMs = System.currentTimeMillis()
        _state.update { st ->
            val session =
                st.activeDriveSession?.copy(isSharing = true)
                    ?: st.activeDriveSession
            st.copy(mapSharingLocation = true, activeDriveSession = session)
        }
        previousLocalMovementMode = null
        container.activityRecognitionPresenceSupport.start()
        recomputeDeviceMovementModeForMapSharing()
        reconcileDriveSessionSampleJob()

        val duration = _state.value.mapShareDurationMinutes
        if (!sharingTiedToActiveDrive && duration != null && duration > 0) {
            mapShareExpiryJob =
                viewModelScope.launch {
                    delay(duration * 60_000L)
                    setMapLocationSharing(false)
                }
        }

        mapShareJob =
            viewModelScope.launch {
                syncRealtimeSubscriptions()
                while (isActive && _state.value.mapSharingLocation) {
                    val userId =
                        sessionRepository.authUserIdState.value
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    if (userId.isNullOrEmpty()) break

                    val fix = approximateLocationReader.currentFixHighAccuracyOrNull()

                    val speedMps = (fix?.speedMps ?: 0f).toDouble().coerceAtLeast(0.0)
                    val speedMph = speedMps * 2.23694
                    val movementMode = resolveAndStoreLocalMovement(speedMps)

                    ingestDriveSessionSample(fix, movementMode)

                    val circles = _state.value.circles
                    val postCircleIds = activeMapShareCircleIds()
                    if (postCircleIds.isEmpty()) {
                        delay(5_000L)
                        continue
                    }

                    val whileDrivingOnly = _state.value.mapShareWhileDrivingOnly
                    if (whileDrivingOnly && movementMode != "driving") {
                        if (!drivingOnlyPauseInactiveSent) {
                            drivingOnlyPauseInactiveSent = true
                            for (postCircleId in postCircleIds) {
                                dataRepository.pushPresence(
                                    PresenceUpdateDto(
                                        circleId = postCircleId,
                                        isActive = false,
                                        inApp = true,
                                        speedMph = 0.0,
                                        movementMode = "unknown",
                                        capturedAt = Instant.now().toString(),
                                        trackDrivingStats = false,
                                    ),
                                )
                            }
                        }
                        delay(5_000L)
                        continue
                    }
                    drivingOnlyPauseInactiveSent = false

                    fix?.let { f ->
                        val capturedIso = Instant.now().toString()
                        for (postCircleId in postCircleIds) {
                            val statsIdx =
                                if (postCircleId == OttoShellUiState.PublicPresenceChannelId) {
                                    -1
                                } else {
                                    circles.indexOfFirst { it.id == postCircleId }.takeIf { it >= 0 } ?: 0
                                }
                            dataRepository.pushPresence(
                                PresenceUpdateDto(
                                    circleId = postCircleId,
                                    isActive = true,
                                    inApp = true,
                                    carId = presenceCarIdForUpload(),
                                    speedMph = speedMph,
                                    lat = f.latitude,
                                    lng = f.longitude,
                                    accuracyMeters = f.accuracyMeters?.toDouble(),
                                    movementMode = movementMode,
                                    capturedAt = capturedIso,
                                    trackDrivingStats =
                                        statsIdx == 0 &&
                                            circles.isNotEmpty() &&
                                            movementMode == "driving",
                                ),
                            )
                        }
                    }

                    delay(5_000L)
                }
            }
        reconcileInAppPresenceHeartbeat()
    }

    private fun activeMapShareCircleIds(): List<String> {
        val snap = _state.value
        if (sharingTiedToActiveDrive) {
            val fromSession =
                snap.activeDriveSession
                    ?.sharingCircleIds
                    ?.mapNotNull { it.trim().takeIf { id -> id.isNotBlank() } }
                    .orEmpty()
            if (fromSession.isNotEmpty()) return fromSession
        }
        val rawScope = snap.mapPresenceCircleId.trim()
        if (rawScope.isEmpty()) return emptyList()
        return listOf(
            if (rawScope == OttoShellUiState.PublicPresenceChannelId) {
                OttoShellUiState.PublicPresenceChannelId
            } else {
                rawScope
            },
        )
    }

    fun startSharingForDriveStart(circleIds: Set<String>): Boolean {
        val targets = circleIds.mapNotNull { it.trim().takeIf { id -> id.isNotBlank() } }.toSet()
        if (targets.isEmpty()) return false
        sharingTiedToActiveDrive = true
        _state.update { it.copy(mapPresenceCircleId = targets.first()) }
        setMapLocationSharing(enabled = true)
        mapShareExpiryJob?.cancel()
        mapShareExpiryJob = null
        return true
    }

    fun setMapLocationSharingDisabledByPermissionRevoke() {
        setMapLocationSharing(enabled = false, disabledReason = "permission_revoked")
    }

    fun redeemInviteInspect(raw: String, squadId: String? = null) {
        viewModelScope.launch {
            val normalized = InviteLinkParsing.normalizeInviteToken(raw)
            if (normalized.isEmpty()) return@launch
            dataRepository.resolveInviteLink(normalized, squadId).fold(
                onSuccess = { dto -> _state.update { it.copy(invitePreview = dto) } },
                onFailure = { e ->
                    _state.update { it.copy(squadsSnack = e.userVisibleHttpMessage("Invite lookup failed.")) }
                },
            )
        }
    }

    fun redeemInviteAccept(rawToken: String) {
        viewModelScope.launch {
            val preview = _state.value.invitePreview
            val token =
                InviteLinkParsing
                    .normalizeInviteToken(rawToken)
                    .ifBlank { InviteLinkParsing.normalizeInviteToken(preview?.token ?: "") }
            if (token.isEmpty()) return@launch
            val circleId = preview?.circle?.id?.trim()?.takeIf { it.isNotEmpty() }
            dataRepository.acceptInviteLink(token, circleId).fold(
                onSuccess = {
                    OttoAnalytics.logSquadJoined("invite_link")
                    _state.update { it.copy(invitePreview = null, squadsSnack = "Joined squad") }
                    loadCoreFeeds()
                },
                onFailure = { e ->
                    _state.update { it.copy(squadsSnack = e.userVisibleHttpMessage("Could not join.")) }
                },
            )
        }
    }

    fun createSquad(nameRaw: String) {
        viewModelScope.launch {
            val name = nameRaw.trim().ifBlank { return@launch }
            dataRepository.createCircle(name = name, description = null).fold(
                onSuccess = {
                    OttoAnalytics.logSquadCreated()
                    _state.update { s -> s.copy(squadsSnack = "Squad created") }
                    loadCoreFeeds()
                },
                onFailure = { e ->
                    _state.update { it.copy(squadsSnack = e.userVisibleHttpMessage("Could not create squad.")) }
                },
            )
        }
    }

    fun inviteByPhoneForCircle(
        circleId: String,
        phoneDigits: String,
    ) {
        inviteSquadMemberByPhoneFromSettings(circleId, phoneDigits)
    }

    fun addMemberByUserIdForCircle(
        circleId: String,
        userId: String,
    ) {
        addSquadMemberFromSettings(circleId, userId)
    }

    @Deprecated("Use squad settings invite actions", ReplaceWith("resolveSquadShareInviteUrlForSettings"))
    fun createShareLinkForCircle(circleId: String) {
        prefetchSquadShareInviteLink(circleId)
    }

    fun onSquadSettingsInviteSearchChanged(circleId: String, query: String) {
        squadInviteLookupJob?.cancel()
        val cid = circleId.trim()
        if (cid.isEmpty()) return
        _state.update { s ->
            s.copy(
                squadSettingsInvite =
                    s.squadSettingsInvite.copy(
                        lookupUser = null,
                        lookupLoading = false,
                        lookupAttempted = false,
                        statusMessage = null,
                        workingUserId = null,
                    ),
            )
        }
        if (!isPhonePrimarySquadInviteQuery(query)) return
        val trimmed = query.trim()
        if (!isValidNorthAmericanPhoneNumber(trimmed)) return
        squadInviteLookupJob =
            viewModelScope.launch {
                delay(450)
                _state.update { s ->
                    s.copy(
                        squadSettingsInvite =
                            s.squadSettingsInvite.copy(
                                lookupLoading = true,
                                lookupAttempted = true,
                                lookupUser = null,
                                statusMessage = null,
                            ),
                    )
                }
                dataRepository.lookupUserByPhone(trimmed).fold(
                    onSuccess = { user ->
                        _state.update { s ->
                            s.copy(
                                squadSettingsInvite =
                                    s.squadSettingsInvite.copy(
                                        lookupLoading = false,
                                        lookupUser = user,
                                        statusMessage =
                                            if (user == null) {
                                                container.application.getString(
                                                    R.string.squad_invite_lookup_not_found,
                                                )
                                            } else {
                                                null
                                            },
                                    ),
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update { s ->
                            s.copy(
                                squadSettingsInvite =
                                    s.squadSettingsInvite.copy(
                                        lookupLoading = false,
                                        statusMessage =
                                            e.userVisibleHttpMessage(
                                                container.application.getString(
                                                    R.string.squad_invite_lookup_failed,
                                                ),
                                            ),
                                    ),
                            )
                        }
                    },
                )
            }
    }

    fun prefetchSquadShareInviteLink(circleId: String) {
        val cid = circleId.trim()
        if (cid.isEmpty()) return
        val remaining = _state.value.squadSettingsInvite.signupInviteRemaining
        if (remaining != null && remaining <= 0) return
        if (!shareInviteLinkByCircleId[cid].isNullOrBlank()) return
        viewModelScope.launch {
            setSquadInviteBusy(cid, SquadShareInviteBusy.PREFETCH)
            try {
                ensureSquadShareInviteLink(cid, showErrors = false)
            } finally {
                clearSquadInviteBusyIf(cid, SquadShareInviteBusy.PREFETCH)
            }
        }
    }

    fun refreshSignupInviteBalance() {
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    squadSettingsInvite =
                        s.squadSettingsInvite.copy(
                            signupInviteBalanceLoading = true,
                        ),
                )
            }
            dataRepository.fetchSignupInviteBalance().fold(
                onSuccess = { balance ->
                    val earnCount = balance.invitesPerLevelUp?.takeIf { it > 0 }
                    val nextLevelName =
                        balance.nextLevelDisplayName
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    signupInviteRemaining = balance.remainingUses,
                                    signupInviteBalanceLoading = false,
                                    signupInviteEarnAtNextLevelCount =
                                        earnCount?.takeIf { nextLevelName != null },
                                    signupInviteNextLevelDisplayName = nextLevelName,
                                ),
                        )
                    }
                },
                onFailure = {
                    _state.update { s ->
                        val invite = s.squadSettingsInvite
                        s.copy(
                            squadSettingsInvite =
                                invite.copy(
                                    signupInviteRemaining = invite.signupInviteRemaining ?: 0,
                                    signupInviteBalanceLoading = false,
                                ),
                        )
                    }
                },
            )
        }
    }

    private fun applySignupInviteRemainingFromLinkResponse(
        remainingUses: Int?,
        personalRemainingUses: Int? = null,
    ) {
        val balanceRemaining = personalRemainingUses ?: remainingUses ?: return
        _state.update { s ->
            s.copy(
                squadSettingsInvite =
                    s.squadSettingsInvite.copy(
                        signupInviteRemaining = maxOf(0, balanceRemaining),
                    ),
            )
        }
    }

    fun dismissSquadSettingsToast() {
        _state.update { it.copy(squadSettingsToast = null) }
    }

    fun presentSquadInviteSmsOpenFailed() {
        val message = container.application.getString(R.string.squad_invite_sms_unavailable)
        _state.update { s ->
            s.copy(squadSettingsInvite = s.squadSettingsInvite.copy(statusMessage = message))
        }
        presentSquadSettingsToast(message)
    }

    /**
     * Resolves the multi-use squad share URL for settings Copy / Invite by SMS.
     * Surfaces errors via [squadSettingsInvite] status + [squadSettingsToast].
     */
    suspend fun resolveSquadShareInviteUrlForSettings(
        circleId: String,
        action: SquadShareInviteBusy,
    ): Result<String> {
        val cid = circleId.trim()
        if (cid.isEmpty()) {
            return Result.failure(IllegalArgumentException("Missing squad id."))
        }
        shareInviteLinkByCircleId[cid]?.takeIf { it.isNotBlank() }?.let { return Result.success(it) }
        if (isSquadInviteBusy(cid, action)) {
            val busyMessage =
                container.application.getString(R.string.squad_invite_action_busy)
            presentSquadSettingsToast(busyMessage)
            return Result.failure(IllegalStateException(busyMessage))
        }
        setSquadInviteBusy(cid, action)
        return try {
            val url = ensureSquadShareInviteLink(cid, showErrors = true)
            if (url.isNullOrBlank()) {
                Result.failure(
                    IllegalStateException(
                        container.application.getString(R.string.squad_invite_link_create_failed),
                    ),
                )
            } else {
                Result.success(url)
            }
        } finally {
            clearSquadInviteBusyIf(cid, action)
        }
    }

    fun presentSquadSettingsCopied() {
        presentSquadSettingsToast(container.application.getString(R.string.squad_invite_copied))
    }

    /** SMS body for unknown-phone single-use invite; opens via caller with [openSquadInviteSms]. */
    suspend fun squadSmsInviteBodyForPhone(
        circleId: String,
        phoneRaw: String,
    ): Pair<String, String?>? {
        val cid = circleId.trim()
        val phone = phoneRaw.trim()
        if (cid.isEmpty() || !isValidNorthAmericanPhoneNumber(phone)) return null
        _state.update { s ->
            s.copy(
                squadSettingsInvite =
                    s.squadSettingsInvite.copy(
                        smsInviteOpening = true,
                        statusMessage =
                            container.application.getString(R.string.squad_invite_sms_opening),
                    ),
            )
        }
        val cacheKey = smsInviteLinkCacheKey(cid, phone)
        val url =
            smsInviteLinkByKey[cacheKey]?.takeIf { it.isNotBlank() }
                ?: dataRepository.createInviteLink(
                    circleId = cid,
                    expiresInDays = 14,
                    phoneNumber = phone,
                ).fold(
                    onSuccess = { dto ->
                        applySignupInviteRemainingFromLinkResponse(
                            dto.remainingUses,
                            dto.personalRemainingUses,
                        )
                        resolveShareInviteUrl(dto.url, dto.token)?.also {
                            smsInviteLinkByKey[cacheKey] = it
                        }
                    },
                    onFailure = { e ->
                        _state.update { s ->
                            s.copy(
                                squadSettingsInvite =
                                    s.squadSettingsInvite.copy(
                                        smsInviteOpening = false,
                                        statusMessage =
                                            e.userVisibleHttpMessage("Could not create invite link."),
                                    ),
                            )
                        }
                        null
                    },
                )
        _state.update { s ->
            s.copy(
                squadSettingsInvite =
                    s.squadSettingsInvite.copy(
                        smsInviteOpening = false,
                        statusMessage = if (url != null) null else s.squadSettingsInvite.statusMessage,
                    ),
            )
        }
        if (url.isNullOrBlank()) return null
        return squadInviteSmsBody(url) to normalizedSmsRecipientFromPhone(phone)
    }

    fun inviteSquadMemberByPhoneFromSettings(circleId: String, phoneRaw: String) {
        val cid = circleId.trim()
        val phone = phoneRaw.trim()
        if (cid.isEmpty() || !isValidNorthAmericanPhoneNumber(phone)) return
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    squadSettingsInvite =
                        s.squadSettingsInvite.copy(
                            workingUserId = "phone",
                            statusMessage =
                                container.application.getString(R.string.squad_invite_sending),
                        ),
                )
            }
            dataRepository.inviteCircleByPhone(cid, phone).fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage =
                                        container.application.getString(R.string.squad_invite_sent_waiting),
                                ),
                        )
                    }
                    loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage = e.userVisibleHttpMessage("Invite failed."),
                                ),
                        )
                    }
                },
            )
        }
    }

    fun inviteSquadMemberByUserFromSettings(
        circleId: String,
        userId: String,
        phoneNumber: String,
    ) {
        val cid = circleId.trim()
        val uid = userId.trim()
        if (cid.isEmpty() || uid.isEmpty()) return
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    squadSettingsInvite =
                        s.squadSettingsInvite.copy(
                            workingUserId = uid,
                            statusMessage =
                                container.application.getString(R.string.squad_invite_sending),
                        ),
                )
            }
            val phone =
                phoneNumber.trim().takeIf { it.isNotEmpty() }
                    ?: _state.value.squadSettingsInvite.lookupUser?.phoneNumber?.trim().orEmpty()
            if (phone.isEmpty()) return@launch
            dataRepository.inviteCircleByPhone(cid, phone).fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage =
                                        container.application.getString(R.string.squad_invite_sent_waiting),
                                ),
                        )
                    }
                    loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage = e.userVisibleHttpMessage("Invite failed."),
                                ),
                        )
                    }
                },
            )
        }
    }

    fun addSquadMemberFromSettings(circleId: String, userId: String) {
        val cid = circleId.trim()
        val uid = userId.trim()
        if (cid.isEmpty() || uid.isEmpty()) return
        val displayName =
            _state.value.contacts.find { ottoUserIdsEqual(it.id, uid) }?.displayName?.trim()
                ?: uid
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    squadSettingsInvite =
                        s.squadSettingsInvite.copy(
                            workingUserId = uid,
                            statusMessage = container.application.getString(R.string.squad_invite_adding),
                        ),
                )
            }
            dataRepository.addCircleMember(cid, uid).fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage =
                                        container.application.getString(
                                            R.string.squad_invite_member_joined_format,
                                            displayName,
                                        ),
                                ),
                        )
                    }
                    loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(
                            squadSettingsInvite =
                                s.squadSettingsInvite.copy(
                                    workingUserId = null,
                                    statusMessage = e.userVisibleHttpMessage("Could not add member."),
                                ),
                        )
                    }
                },
            )
        }
    }

    private fun presentSquadSettingsToast(message: String) {
        _state.update { it.copy(squadSettingsToast = message) }
    }

    private fun isSquadInviteBusy(circleId: String, busy: SquadShareInviteBusy): Boolean {
        val invite = _state.value.squadSettingsInvite
        return invite.busy == busy && invite.busyCircleId == circleId.trim()
    }

    private fun presentSquadInviteLinkError(throwable: Throwable? = null) {
        val message =
            throwable?.userVisibleHttpMessage(
                container.application.getString(R.string.squad_invite_link_create_failed),
            )
                ?: container.application.getString(R.string.squad_invite_link_create_failed)
        _state.update { s ->
            s.copy(
                squadSettingsInvite = s.squadSettingsInvite.copy(statusMessage = message),
            )
        }
        presentSquadSettingsToast(message)
    }

    private fun shareInviteMutex(circleId: String): Mutex =
        synchronized(shareInviteFetchMutexes) {
            shareInviteFetchMutexes.getOrPut(circleId) { Mutex() }
        }

    private fun presentUserToast(message: String) {
        _state.update { it.copy(userToastMessage = message) }
    }

    private suspend fun ensureSquadShareInviteLink(
        circleId: String,
        showErrors: Boolean = false,
    ): String? {
        val cid = circleId.trim()
        shareInviteLinkByCircleId[cid]?.takeIf { it.isNotBlank() }?.let { return it }
        return shareInviteMutex(cid).withLock {
            shareInviteLinkByCircleId[cid]?.takeIf { it.isNotBlank() }?.let { return@withLock it }
            dataRepository.createInviteLink(circleId = cid, expiresInDays = 14).fold(
                onSuccess = { dto ->
                    applySignupInviteRemainingFromLinkResponse(
                        dto.remainingUses,
                        dto.personalRemainingUses,
                    )
                    val url = resolveShareInviteUrl(dto.url, dto.token)
                    if (url != null) {
                        shareInviteLinkByCircleId[cid] = url
                    } else if (showErrors) {
                        presentSquadInviteLinkError()
                    }
                    url
                },
                onFailure = { e ->
                    refreshSignupInviteBalance()
                    if (showErrors) {
                        presentSquadInviteLinkError(e)
                    }
                    null
                },
            )
        }
    }

    private fun setSquadInviteBusy(circleId: String, busy: SquadShareInviteBusy) {
        _state.update { s ->
            s.copy(
                squadSettingsInvite =
                    s.squadSettingsInvite.copy(
                        busy = busy,
                        busyCircleId = circleId,
                    ),
            )
        }
    }

    private fun clearSquadInviteBusyIf(circleId: String, busy: SquadShareInviteBusy) {
        _state.update { s ->
            val invite = s.squadSettingsInvite
            if (invite.busy != busy || invite.busyCircleId != circleId) {
                s
            } else {
                s.copy(
                    squadSettingsInvite =
                        invite.copy(
                            busy = null,
                            busyCircleId = null,
                        ),
                )
            }
        }
    }

    fun respondPendingInvite(inviteId: String, accept: Boolean) {
        viewModelScope.launch {
            dataRepository.respondCircleInvite(inviteId = inviteId, accept = accept).fold(
                onSuccess = {
                    if (accept) {
                        OttoAnalytics.logSquadJoined("pending_invite")
                    }
                    _state.update { it.copy(squadsSnack = if (accept) "Joined squad" else "Invite declined") }
                    loadCoreFeeds()
                    loadPendingInvites()
                },
                onFailure = { e ->
                    _state.update { it.copy(squadsSnack = e.userVisibleHttpMessage()) }
                },
            )
        }
    }

    fun sendCircleChatWithAttachment(
        bodyRaw: String,
        attachment: ChatPendingComposerAttachment?,
    ) {
        when (attachment?.kind) {
            ChatPendingComposerAttachmentKind.Video -> {
                val prepared = attachment.video ?: return
                sendCircleChatVideo(bodyRaw, ChatSendVideoAttachment(prepared))
            }
            ChatPendingComposerAttachmentKind.Place -> sendCircleChatPlace(bodyRaw, attachment)
            ChatPendingComposerAttachmentKind.Event -> {
                val eventId = attachment.event?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return
                sendCircleChatEvent(bodyRaw, eventId)
            }
            ChatPendingComposerAttachmentKind.KlipyGif -> sendCircleChat(bodyRaw, pendingAttachment = attachment)
            ChatPendingComposerAttachmentKind.Photo -> sendCircleChat(bodyRaw, attachment.photo)
            null -> sendCircleChat(bodyRaw, null)
        }
    }

    fun sendDirectMessageWithAttachment(
        bodyRaw: String,
        attachment: ChatPendingComposerAttachment?,
    ) {
        when (attachment?.kind) {
            ChatPendingComposerAttachmentKind.Video -> {
                val prepared = attachment.video ?: return
                sendDirectChatVideo(bodyRaw, ChatSendVideoAttachment(prepared))
            }
            ChatPendingComposerAttachmentKind.Place -> sendDirectMessagePlace(bodyRaw, attachment)
            ChatPendingComposerAttachmentKind.KlipyGif -> sendDirectMessage(bodyRaw, pendingAttachment = attachment)
            ChatPendingComposerAttachmentKind.Photo -> sendDirectMessage(bodyRaw, attachment.photo)
            ChatPendingComposerAttachmentKind.Event, null -> sendDirectMessage(bodyRaw, null)
        }
    }

    private fun sendCircleChatPlace(
        bodyRaw: String,
        attachment: ChatPendingComposerAttachment,
    ) {
        viewModelScope.launch {
            val body = bodyRaw.trim()
            val payload = attachment.placePayload ?: return@launch
            val lat = payload.latitude
            val lng = payload.longitude
            if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) return@launch
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = true, chatSnack = null))
            }
            val replyToId =
                _state.value.circleDetailUi?.chatReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
            val clientMsg = UUID.randomUUID().toString()
            dataRepository
                .sendCircleChat(
                    circleId = circleId,
                    body = body,
                    clientMessageId = clientMsg,
                    replyToMessageId = replyToId,
                    placeLatitude = lat,
                    placeLongitude = lng,
                    placeName = payload.title,
                    placeAddressSummary = payload.subtitle,
                    mapPreviewBytes = attachment.mapPreviewBytes,
                ).fold(
                    onSuccess = { msg ->
                        OttoAnalytics.logChatMessageSent(channel = "squad", attachmentType = "place")
                        _state.update { s ->
                            s.copy(circleDetailUi = s.circleDetailUi?.copy(chatReplyTo = null))
                        }
                        upsertChatMessage(msg.copy(messageType = msg.messageType ?: "user"))
                    },
                    onFailure = { e ->
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.copy(
                                        chatSendBusy = false,
                                        chatSnack = e.userVisibleHttpMessage(),
                                    ),
                            )
                        }
                        return@launch
                    },
                )
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false))
            }
        }
    }

    private fun sendCircleChatEvent(
        bodyRaw: String,
        eventId: String,
    ) {
        viewModelScope.launch {
            val body = bodyRaw.trim()
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = true, chatSnack = null))
            }
            val replyToId =
                _state.value.circleDetailUi?.chatReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
            val clientMsg = UUID.randomUUID().toString()
            dataRepository
                .sendCircleChat(
                    circleId = circleId,
                    body = body,
                    clientMessageId = clientMsg,
                    replyToMessageId = replyToId,
                    eventId = eventId,
                ).fold(
                    onSuccess = { msg ->
                        OttoAnalytics.logChatMessageSent(channel = "squad", attachmentType = "event")
                        _state.update { s ->
                            s.copy(circleDetailUi = s.circleDetailUi?.copy(chatReplyTo = null))
                        }
                        upsertChatMessage(msg.copy(messageType = msg.messageType ?: "user"))
                    },
                    onFailure = { e ->
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.copy(
                                        chatSendBusy = false,
                                        chatSnack = e.userVisibleHttpMessage(),
                                    ),
                            )
                        }
                        return@launch
                    },
                )
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false))
            }
        }
    }

    private fun sendDirectMessagePlace(
        bodyRaw: String,
        attachment: ChatPendingComposerAttachment,
    ) {
        viewModelScope.launch {
            val body = bodyRaw.trim()
            val payload = attachment.placePayload ?: return@launch
            val lat = payload.latitude
            val lng = payload.longitude
            if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) return@launch
            val conversationId =
                _state.value.directMessages.selectedConversationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@launch
            _state.update {
                val dm = it.directMessages
                it.copy(directMessages = dm.copy(sendBusy = true, threadSnack = null))
            }
            val replyToId =
                _state.value.directMessages.threadReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
            val clientMsg = UUID.randomUUID().toString()
            dataRepository
                .sendDirectMessage(
                    conversationId = conversationId,
                    body = body,
                    clientMessageId = clientMsg,
                    replyToMessageId = replyToId,
                    placeLatitude = lat,
                    placeLongitude = lng,
                    placeName = payload.title,
                    placeAddressSummary = payload.subtitle,
                    mapPreviewBytes = attachment.mapPreviewBytes,
                ).fold(
                    onSuccess = { msg ->
                        OttoAnalytics.logChatMessageSent(channel = "direct", attachmentType = "place")
                        _state.update { s ->
                            val dm = s.directMessages
                            s.copy(directMessages = dm.copy(threadReplyTo = null))
                        }
                        mergeDirectThreadMessage(msg)
                    },
                    onFailure = { e ->
                        _state.update {
                            val dm = it.directMessages
                            it.copy(
                                directMessages =
                                    dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()),
                            )
                        }
                        return@launch
                    },
                )
            _state.update {
                val dm = it.directMessages
                it.copy(directMessages = dm.copy(sendBusy = false))
            }
        }
    }

    fun sendCircleChat(
        bodyRaw: String,
        photo: ChatSendPhotoAttachment? = null,
        pendingAttachment: ChatPendingComposerAttachment? = null,
    ) {
        viewModelScope.launch {
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val circle = _state.value.circleDetailUi?.circle ?: return@launch
            val editingId =
                _state.value.circleDetailUi?.chatEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
            val hasPhoto = photo?.bytes?.isNotEmpty() == true
            val normalized =
                if (!editingId.isNullOrBlank()) {
                    ChatOutgoingImagePayload(body = bodyRaw.trim(), imageUrl = null)
                } else {
                    ChatOutgoingImageUrlNormalizer.normalize(bodyRaw, pendingAttachment)
                }
            val body = normalized.body
            val imageUrl = normalized.imageUrl
            if (body.isEmpty() && !hasPhoto && imageUrl == null) return@launch
            val memberIds =
                circle.members.orEmpty().map { it.userId.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            memberIds.add(SquadChatAllMention.USER_ID)
            val contacts = _state.value.contacts
            val nameByUserId =
                memberIds
                    .filter { it != SquadChatAllMention.USER_ID }
                    .associateWith { uid ->
                        contacts.find { ottoUserIdsEqual(it.id, uid) }?.displayName?.trim().orEmpty()
                    }.filterValues { it.isNotEmpty() }
                    .toMutableMap()
            nameByUserId[SquadChatAllMention.USER_ID] = SquadChatAllMention.WIRE_LABEL
            var mentionSpans =
                parseSquadMentionSpansUtf16(body, memberIds, nameByUserId)
            val myId = _state.value.me?.id?.trim()?.takeIf { it.isNotBlank() }
            if (!myId.isNullOrBlank()) {
                mentionSpans = mentionSpans.filter { !ottoUserIdsEqual(it.userId, myId) }
            }
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = true, chatSnack = null))
            }
            if (!editingId.isNullOrBlank()) {
                dataRepository
                    .patchCircleChatMessage(
                        circleId = circleId,
                        messageId = editingId,
                        body = body,
                        mentions = mentionSpans.takeIf { it.isNotEmpty() },
                    ).fold(
                        onSuccess = { msg ->
                            _state.update { s ->
                                val cd = s.circleDetailUi
                                s.copy(
                                    circleDetailUi =
                                        cd?.copy(
                                            chatReplyTo = null,
                                            chatEditingMessageId = null,
                                        ),
                                )
                            }
                            mergeCircleChatMessage(msg.copy(messageType = msg.messageType ?: "user"), clearSendBusy = false)
                        },
                        onFailure = { e ->
                            _state.update { s ->
                                s.copy(
                                    circleDetailUi =
                                        s.circleDetailUi?.copy(
                                            chatSendBusy = false,
                                            chatSnack = e.userVisibleHttpMessage(),
                                        ),
                                )
                            }
                            return@launch
                        },
                    )
            } else {
                val replyToId =
                    _state.value.circleDetailUi?.chatReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
                val clientMsg = UUID.randomUUID().toString()
                dataRepository
                    .sendCircleChat(
                        circleId = circleId,
                        body = body,
                        clientMessageId = clientMsg,
                        replyToMessageId = replyToId,
                        mentions = mentionSpans.takeIf { it.isNotEmpty() },
                        photoBytes = photo?.bytes?.takeIf { it.isNotEmpty() },
                        photoContentType = photo?.contentType?.trim()?.takeIf { it.isNotEmpty() },
                        imageUrl = imageUrl,
                    ).fold(
                        onSuccess = { msg ->
                            OttoAnalytics.logChatMessageSent(
                                channel = "squad",
                                attachmentType =
                                    when {
                                        hasPhoto -> "photo"
                                        imageUrl != null ->
                                            if (ChatImageUrlDisplay.isAnimatedImageUrl(imageUrl)) {
                                                "gif"
                                            } else {
                                                "image_url"
                                            }
                                        else -> "none"
                                    },
                            )
                            normalized.klipyShare?.let { share ->
                                viewModelScope.launch {
                                    KlipyAPIClient.reportShare(
                                        slug = share.slug,
                                        customerId = myId.orEmpty(),
                                        searchQuery = share.searchQuery,
                                    )
                                }
                            }
                            _state.update { s ->
                                val cd = s.circleDetailUi
                                s.copy(
                                    circleDetailUi =
                                        cd?.copy(
                                            chatReplyTo = null,
                                        ),
                                )
                            }
                            upsertChatMessage(msg.copy(messageType = msg.messageType ?: "user"))
                        },
                        onFailure = { e ->
                            _state.update { s ->
                                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false, chatSnack = e.userVisibleHttpMessage()))
                            }
                            return@launch
                        },
                    )
            }
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false))
            }
        }
    }

    fun sendCircleChatVideo(
        bodyRaw: String,
        video: ChatSendVideoAttachment,
    ) {
        val clientMsg = UUID.randomUUID().toString()
        val prepared = video.prepared
        val job =
            viewModelScope.launch {
            val body = bodyRaw.trim()
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val circle = _state.value.circleDetailUi?.circle ?: return@launch
            val memberIds =
                circle.members.orEmpty().map { it.userId.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            memberIds.add(SquadChatAllMention.USER_ID)
            val contacts = _state.value.contacts
            val nameByUserId =
                memberIds
                    .filter { it != SquadChatAllMention.USER_ID }
                    .associateWith { uid ->
                        contacts.find { ottoUserIdsEqual(it.id, uid) }?.displayName?.trim().orEmpty()
                    }.filterValues { it.isNotEmpty() }
                    .toMutableMap()
            nameByUserId[SquadChatAllMention.USER_ID] = SquadChatAllMention.WIRE_LABEL
            var mentionSpans = parseSquadMentionSpansUtf16(body, memberIds, nameByUserId)
            val myId = _state.value.me?.id?.trim()?.takeIf { it.isNotBlank() }
            if (!myId.isNullOrBlank()) {
                mentionSpans = mentionSpans.filter { !ottoUserIdsEqual(it.userId, myId) }
            }
            val replyToId =
                _state.value.circleDetailUi?.chatReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
            val me = _state.value.me
            val optimistic =
                CircleChatMessageDto(
                    id = "pending-$clientMsg",
                    circleId = circleId,
                    senderUserId = myId.orEmpty(),
                    sender =
                        me?.let {
                            CircleChatSenderDto(
                                id = it.id,
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl,
                                mapAccentKey = it.mapAccentKey,
                            )
                        },
                    body = body,
                    messageType = "user",
                    videoAttachment =
                        ChatVideoAttachmentDto(
                            videoUrl = "",
                            thumbnailUrl = "",
                            durationSeconds = prepared.durationSeconds,
                            width = prepared.width,
                            height = prepared.height,
                            mimeType = prepared.mimeType,
                        ),
                    clientMessageId = clientMsg,
                    replyToMessageId = replyToId,
                    createdAt = java.time.Instant.now().toString(),
                )
            _state.update { s ->
                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = true, chatSnack = null, chatReplyTo = null))
            }
            mergeCircleChatMessage(optimistic, clearSendBusy = false)
            to.ottomot.driftd.core.media.ChatVideoUploadState.register(clientMsg, prepared.thumbnailBitmap)
            to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, 0f)
            val urls =
                dataRepository.requestCircleChatVideoUploadUrls(circleId, prepared.mimeType).getOrElse { e ->
                    to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                    _state.update { s ->
                        s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false, chatSnack = e.userVisibleHttpMessage()))
                    }
                    return@launch
                }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    to.ottomot.driftd.core.media.ChatVideoS3Uploader.putBytes(
                        uploadUrl = urls.thumbnail.uploadUrl,
                        bytes = prepared.thumbnailJpeg,
                        contentType = "image/jpeg",
                    ) { p -> to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, p * 0.08f) }
                    to.ottomot.driftd.core.media.ChatVideoS3Uploader.putFile(
                        uploadUrl = urls.video.uploadUrl,
                        file = prepared.localVideoFile,
                        contentType = prepared.mimeType,
                    ) { p -> to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, 0.08f + p * 0.84f) }
                }
                val attachment =
                    ChatVideoAttachmentDto(
                        videoUrl = urls.video.fileUrl,
                        thumbnailUrl = urls.thumbnail.fileUrl,
                        durationSeconds = prepared.durationSeconds,
                        width = prepared.width,
                        height = prepared.height,
                        mimeType = prepared.mimeType,
                    )
                dataRepository
                    .sendCircleChatVideoMessage(
                        circleId = circleId,
                        body = body,
                        clientMessageId = clientMsg,
                        videoAttachment = attachment,
                        replyToMessageId = replyToId,
                        mentions = mentionSpans.takeIf { it.isNotEmpty() },
                    ).fold(
                        onSuccess = { msg ->
                            if (!isActive) return@fold
                            OttoAnalytics.logChatMessageSent(
                                channel = "squad",
                                attachmentType = "video",
                            )
                            to.ottomot.driftd.core.media.ChatVideoUploadState.clear(clientMsg)
                            mergeCircleChatMessage(msg.copy(messageType = msg.messageType ?: "user"), clearSendBusy = true)
                        },
                        onFailure = { e ->
                            if (!isActive) return@fold
                            to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                            _state.update { s ->
                                s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false, chatSnack = e.userVisibleHttpMessage()))
                            }
                        },
                    )
            } catch (e: Exception) {
                if (!isActive) return@launch
                to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                _state.update { s ->
                    s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSendBusy = false, chatSnack = e.userVisibleHttpMessage()))
                }
            }
        }
        chatVideoUploadJobs[clientMsg] = job
        job.invokeOnCompletion { chatVideoUploadJobs.remove(clientMsg) }
    }

    fun cancelCircleChatVideoUpload(clientMessageId: String) {
        val id = clientMessageId.trim().takeIf { it.isNotEmpty() } ?: return
        chatVideoUploadJobs.remove(id)?.cancel()
        to.ottomot.driftd.core.media.ChatVideoUploadState.clear(id)
        _state.update { s ->
            val cd = s.circleDetailUi ?: return@update s
            s.copy(
                circleDetailUi =
                    cd.copy(
                        chatMessages =
                            cd.chatMessages.filterNot {
                                it.id == "pending-$id" ||
                                    (it.id.startsWith("pending-") && it.clientMessageId == id)
                            },
                        chatSendBusy = false,
                    ),
            )
        }
    }

    fun beginCircleChatEdit(messageId: String) {
        val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return
        _state.update { s ->
            val cd = s.circleDetailUi ?: return@update s
            s.copy(circleDetailUi = cd.copy(chatEditingMessageId = mid, chatReplyTo = null))
        }
    }

    fun cancelCircleChatEdit() {
        _state.update { s ->
            val cd = s.circleDetailUi ?: return@update s
            s.copy(circleDetailUi = cd.copy(chatEditingMessageId = null))
        }
    }

    fun deleteCircleChatMessage(messageId: String) {
        viewModelScope.launch {
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.deleteCircleChatMessage(circleId, mid).fold(
                onSuccess = { tomb ->
                    mergeCircleChatMessage(tomb.copy(messageType = tomb.messageType ?: "user"), clearSendBusy = false)
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSnack = e.userVisibleHttpMessage()))
                    }
                },
            )
        }
    }

    fun setCircleChatReplyTo(message: CircleChatMessageDto?) {
        _state.update { s ->
            val cd = s.circleDetailUi ?: return@update s
            s.copy(circleDetailUi = cd.copy(chatReplyTo = message))
        }
    }

    /** One older page when a reply quote targets a message not yet in the loaded transcript. */
    fun fetchOlderCircleChatForQuoteJump(circleId: String) {
        viewModelScope.launch {
            val cid = circleId.trim().takeIf { it.isNotEmpty() } ?: return@launch
            val detail =
                _state.value.circleDetailUi?.takeIf { ottoUserIdsEqual(it.circleId, cid) }
                    ?: return@launch
            val messages = detail.chatMessages
            if (messages.isEmpty() || messages.size < QUOTE_JUMP_OLDER_PAGE_LIMIT) return@launch
            val before =
                messages.firstOrNull()?.createdAt?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@launch
            dataRepository.circleChatMessages(
                circleId = cid,
                limit = QUOTE_JUMP_OLDER_PAGE_LIMIT,
                before = before,
            ).fold(
                onSuccess = { older ->
                    if (older.isEmpty()) return@fold
                    _state.update { s ->
                        val cd =
                            s.circleDetailUi?.takeIf { ottoUserIdsEqual(it.circleId, cid) }
                                ?: return@update s
                        val existingIds = cd.chatMessages.map { it.id }.toSet()
                        val prepended = older.filter { it.id !in existingIds }
                        if (prepended.isEmpty()) return@update s
                        s.copy(circleDetailUi = cd.copy(chatMessages = prepended + cd.chatMessages))
                    }
                },
                onFailure = { },
            )
        }
    }

    /** One older page when a DM reply quote targets a message not yet in the loaded thread. */
    fun fetchOlderDirectChatForQuoteJump(conversationId: String) {
        viewModelScope.launch {
            val cid = conversationId.trim().takeIf { it.isNotEmpty() } ?: return@launch
            val dm = _state.value.directMessages
            if (!ottoUserIdsEqual(dm.selectedConversationId, cid)) return@launch
            val messages = dm.messages
            if (messages.isEmpty() || messages.size < QUOTE_JUMP_OLDER_PAGE_LIMIT) return@launch
            val before =
                messages.firstOrNull()?.createdAt?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@launch
            dataRepository.directMessages(
                conversationId = cid,
                limit = QUOTE_JUMP_OLDER_PAGE_LIMIT,
                before = before,
            ).fold(
                onSuccess = { older ->
                    if (older.isEmpty()) return@fold
                    _state.update { s ->
                        val thread = s.directMessages
                        if (!ottoUserIdsEqual(thread.selectedConversationId, cid)) return@update s
                        val existingIds = thread.messages.map { it.id }.toSet()
                        val prepended = older.filter { it.id !in existingIds }
                        if (prepended.isEmpty()) return@update s
                        s.copy(
                            directMessages =
                                thread.copy(messages = prepended + thread.messages),
                        )
                    }
                },
                onFailure = { },
            )
        }
    }

    fun postCircleChatReaction(
        messageId: String,
        emoji: String,
    ) {
        viewModelScope.launch {
            val circleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return@launch
            val em = emoji.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.postCircleChatReaction(circleId, mid, em).fold(
                onSuccess = { msg -> mergeCircleChatMessage(msg.copy(messageType = msg.messageType ?: "user"), clearSendBusy = false) },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(circleDetailUi = s.circleDetailUi?.copy(chatSnack = e.userVisibleHttpMessage("Could not react.")))
                    }
                },
            )
        }
    }

    fun loadNextUpEventDismissals(
        circleId: String,
        eventIds: List<String>,
    ) {
        val cid = circleId.trim()
        val ids = eventIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (cid.isEmpty() || ids.isEmpty()) return
        viewModelScope.launch {
            dataRepository.nextUpEventDismissals(cid, ids)
                .onSuccess { records ->
                    _state.update { s ->
                        val existing = s.nextUpEventDismissalsByCircleId[cid].orEmpty()
                        val serverEventIds = records.map { it.eventId }.toSet()
                        s.copy(
                            nextUpEventDismissalsByCircleId =
                                s.nextUpEventDismissalsByCircleId +
                                    (cid to (existing.filterNot { it.eventId in serverEventIds } + records)),
                        )
                    }
                }
                .onFailure { e ->
                    Log.w("OttoShellViewModel", "Failed to load next-up dismissals", e)
                }
        }
    }

    fun dismissNextUpEventBanner(
        circleId: String,
        eventId: String,
        dismissedContext: String,
    ) {
        val cid = circleId.trim()
        val eid = eventId.trim()
        if (cid.isEmpty() || eid.isEmpty()) return
        val optimistic =
            NextUpEventDismissalDto(
                userId = resolveSelfUserId(),
                squadId = cid,
                circleId = cid,
                eventId = eid,
                dismissedAt = Instant.now().toString(),
                dismissedContext = dismissedContext,
            )
        _state.update { s ->
            val existing = s.nextUpEventDismissalsByCircleId[cid].orEmpty()
            s.copy(
                nextUpEventDismissalsByCircleId =
                    s.nextUpEventDismissalsByCircleId +
                        (cid to (existing.filterNot { it.eventId == eid } + optimistic)),
            )
        }
        viewModelScope.launch {
            dataRepository.dismissNextUpEventBanner(cid, eid, dismissedContext)
                .onSuccess { saved ->
                    _state.update { s ->
                        val existing = s.nextUpEventDismissalsByCircleId[cid].orEmpty()
                        s.copy(
                            nextUpEventDismissalsByCircleId =
                                s.nextUpEventDismissalsByCircleId +
                                    (cid to (existing.filterNot { it.eventId == eid } + saved)),
                        )
                    }
                }
                .onFailure { e ->
                    Log.w("OttoShellViewModel", "Failed to persist next-up dismissal", e)
                }
        }
    }

    fun dismissEventDetail() {
        _state.update { it.copy(eventDetailUi = null) }
    }

    fun requestMapTabCenteredOn(
        latitude: Double,
        longitude: Double,
        eventId: String? = null,
        eventSnapshot: EventDto? = null,
        savedPlaceId: String? = null,
        savedPlaceSnapshot: SavedPlaceDto? = null,
    ) {
        _state.update {
            it.copy(
                pendingMapCoordinateFocus =
                    PendingMapCoordinateFocus(
                        latitude = latitude,
                        longitude = longitude,
                        eventId = eventId?.trim()?.takeIf { id -> id.isNotEmpty() },
                        eventSnapshot = eventSnapshot,
                        savedPlaceId = savedPlaceId?.trim()?.takeIf { id -> id.isNotEmpty() },
                        savedPlaceSnapshot = savedPlaceSnapshot,
                    ),
            )
        }
    }

    fun openProfilePlaceOnMap(place: SavedPlaceDto) {
        requestMapTabCenteredOn(
            latitude = place.latitude,
            longitude = place.longitude,
            savedPlaceId = place.id,
            savedPlaceSnapshot = place,
        )
    }

    fun openSavedRouteOnMap(route: SavedRouteDto) {
        val start = startCoordinateForSavedRoute(route) ?: return
        requestMapTabCenteredOn(latitude = start.first, longitude = start.second)
        _state.update { it.copy(savedRouteDetail = SavedRouteDetailSheetUi(route)) }
    }

    fun consumePendingMapCoordinateFocus() {
        _state.update { it.copy(pendingMapCoordinateFocus = null) }
    }

    fun openEventLocationOnMap(latitude: Double, longitude: Double, eventId: String) {
        val snapshot =
            _state.value.eventDetailUi
                ?.takeIf { it.eventId == eventId }
                ?.event
        requestMapTabCenteredOn(latitude, longitude, eventId, eventSnapshot = snapshot)
        dismissEventDetail()
    }

    fun prefetchChatAttachmentEvents(eventIds: Set<String>) {
        if (eventIds.isEmpty()) return
        viewModelScope.launch {
            for (raw in eventIds) {
                val id = raw.trim()
                if (id.isEmpty()) continue
                val snap = _state.value
                val squad = snap.circleDetailUi?.squadScopedEvents.orEmpty()
                val global = snap.events
                val hydrated = snap.chatAttachmentHydratedEventsById
                val resolved =
                    squad.firstOrNull { it.id.equals(id, ignoreCase = true) }
                        ?: global.firstOrNull { it.id.equals(id, ignoreCase = true) }
                        ?: hydrated.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
                if (resolved != null) continue
                dataRepository.event(eventRef = id).fold(
                    onSuccess = { ev ->
                        val kid = ev.id.trim()
                        if (kid.isEmpty()) return@fold
                        _state.update { s ->
                            s.copy(
                                chatAttachmentHydratedEventsById =
                                    s.chatAttachmentHydratedEventsById + (kid to ev),
                            )
                        }
                    },
                    onFailure = { },
                )
            }
        }
    }

    fun openEventDetail(eventId: String) {
        viewModelScope.launch {
            val s = _state.value
            val cached =
                s.events.find { it.id == eventId || it.slug == eventId }
                    ?: s.communityEvents.find { it.id == eventId || it.slug == eventId }
                    ?: s.profilePublicGoingEvents.find { it.id == eventId || it.slug == eventId }?.asEventStub()
            _state.update {
                it.copy(
                    eventDetailUi =
                        EventDetailUi(
                            eventId = eventId,
                            event = cached,
                            loadingDetail = true,
                            detailError = null,
                            snackMessage = null,
                        ),
                )
            }
            dataRepository.event(eventRef = eventId).fold(
                onSuccess = { full ->
                    _state.update { s ->
                        val isCommunity = full.eventType == "community"
                        val nextEvents =
                            if (isCommunity) {
                                s.events
                            } else {
                                val mergedList = s.events.map { e -> if (e.id == full.id) full else e }
                                if (mergedList.any { it.id == full.id }) mergedList else mergedList + full
                            }
                        val nextCommunityEvents =
                            if (!isCommunity) {
                                s.communityEvents
                            } else {
                                val mergedList =
                                    s.communityEvents.map { e -> if (e.id == full.id) full else e }
                                if (mergedList.any { it.id == full.id }) mergedList else mergedList + full
                            }
                        s.copy(
                            events = nextEvents,
                            communityEvents = nextCommunityEvents,
                            eventDetailUi =
                                s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                    event = full,
                                    loadingDetail = false,
                                    detailError = null,
                                ),
                        )
                    }
                },
                onFailure = { e ->
                    val msg = e.userVisibleHttpMessage()
                    _state.update { s ->
                        s.copy(
                            eventDetailUi =
                                s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                    loadingDetail = false,
                                    detailError = msg,
                                    event = s.eventDetailUi.event ?: cached,
                                ),
                        )
                    }
                },
            )
        }
    }

    fun submitEventRsvp(
        eventId: String,
        status: String,
    ) {
        val eidTrim = eventId.trim()
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    eventRsvpSubmittingEventId = eidTrim.takeIf { it.isNotEmpty() },
                    eventDetailUi =
                        s.eventDetailUi?.takeIf { it.eventId == eventId }
                            ?.copy(actionBusy = true, snackMessage = null),
                )
            }
            dataRepository.updateEventRsvp(eventId = eventId, status = status).fold(
                onSuccess = { updated ->
                    _state.update { s ->
                        val hid = updated.id.trim()
                        val eid = eventId.trim()
                        val nextHydration =
                            s.chatAttachmentHydratedEventsById
                                .filterKeys { k ->
                                    !k.equals(eid, ignoreCase = true) &&
                                        !k.equals(hid, ignoreCase = true)
                                }
                                .toMutableMap()
                                .apply { put(hid, updated) }
                        val isCommunity = updated.eventType == "community"
                        val eventIdx =
                            s.events.indexOfFirst {
                                it.id == updated.id || it.id.equals(eid, ignoreCase = true)
                            }
                        val communityIdx =
                            s.communityEvents.indexOfFirst {
                                it.id == updated.id || it.id.equals(eid, ignoreCase = true)
                            }
                        val nextEvents =
                            when {
                                isCommunity -> s.events
                                eventIdx >= 0 ->
                                    s.events.toMutableList().apply { this[eventIdx] = updated }
                                else -> s.events + updated
                            }
                        val nextCommunityEvents =
                            when {
                                !isCommunity -> s.communityEvents
                                communityIdx >= 0 ->
                                    s.communityEvents.toMutableList().apply { this[communityIdx] = updated }
                                else -> s.communityEvents + updated
                            }
                        val nextSquadGoing = mergeSquadGoingEvents(s.squadGoingEvents, updated)
                        s.copy(
                            events = nextEvents,
                            communityEvents = nextCommunityEvents,
                            squadGoingEvents = nextSquadGoing,
                            chatAttachmentHydratedEventsById = nextHydration,
                            eventRsvpSubmittingEventId = null,
                            eventDetailUi =
                                s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                    event = updated,
                                    actionBusy = false,
                                    snackMessage = null,
                                ),
                        )
                    }
                    val openSquad = _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotEmpty() }
                    if (openSquad != null) {
                        reloadSquadScopedEventsForCircle(openSquad)
                    }
                },
                onFailure = { err ->
                    val msg = err.userVisibleHttpMessage()
                    _state.update { s ->
                        val cleared = s.copy(eventRsvpSubmittingEventId = null)
                        val detailOpen = s.eventDetailUi?.eventId == eventId
                        when {
                            detailOpen ->
                                cleared.copy(
                                    eventDetailUi =
                                        s.eventDetailUi?.copy(
                                            actionBusy = false,
                                            snackMessage = msg,
                                        ),
                                )
                            s.circleDetailUi != null ->
                                cleared.copy(
                                    circleDetailUi = s.circleDetailUi.copy(chatSnack = msg),
                                )
                            s.directMessages.visible ->
                                cleared.copy(
                                    directMessages = s.directMessages.copy(threadSnack = msg),
                                )
                            else -> cleared
                        }
                    }
                },
            )
        }
    }

    fun submitEventCheckIn(eventId: String) {
        viewModelScope.launch {
            val ev =
                _state.value.eventDetailUi?.takeIf { it.eventId == eventId }?.event
                    ?: return@launch
            if (!isWithinEventCheckInWindow(ev)) {
                _state.update { s ->
                    s.copy(
                        eventDetailUi =
                            s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                snackMessage = "Check-in opens when the event starts.",
                            ),
                    )
                }
                return@launch
            }

            val needGps = eventHasVenueCoordinates(ev)
            val coords =
                if (needGps) {
                    approximateLocationReader.currentLatLngOrNull()
                } else {
                    null
                }
            if (needGps && coords == null) {
                _state.update { s ->
                    s.copy(
                        eventDetailUi =
                            s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                snackMessage =
                                    "Could not read your location. Grant location access and try again.",
                            ),
                    )
                }
                return@launch
            }

            val venueLatLng =
                eventVenueLatLng(ev)
            if (
                needGps &&
                    coords != null &&
                    venueLatLng != null
            ) {
                val d =
                    haversineMeters(
                        coords.first,
                        coords.second,
                        venueLatLng.first,
                        venueLatLng.second,
                    )
                if (d > EVENT_CHECK_IN_RADIUS_METERS) {
                    _state.update { s ->
                        s.copy(
                            eventDetailUi =
                                s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                    snackMessage =
                                        "You’ll be able to check in when you’re closer to the event.",
                                ),
                        )
                    }
                    return@launch
                }
            }

            _state.update { s ->
                s.copy(
                    eventDetailUi =
                        s.eventDetailUi?.takeIf { it.eventId == eventId }
                            ?.copy(actionBusy = true, snackMessage = null),
                )
            }

            val lat = coords?.first
            val lng = coords?.second
            dataRepository.postEventCheckIn(eventId = eventId, latitude = lat, longitude = lng).fold(
                onSuccess = { result ->
                    if (result.checkedIn && !result.alreadyCheckedIn) {
                        OttoAnalytics.logEventCheckIn(eventId)
                    }
                    val merged =
                        result.event
                            ?: _state.value.eventDetailUi?.event?.let { base ->
                                val check =
                                    result.checkIn
                                        ?: base.currentUserCheckIn
                                base.copy(currentUserCheckIn = check)
                            }
                    if (merged != null) {
                        val note =
                            when {
                                result.alreadyCheckedIn -> "Already checked in."
                                result.checkedIn -> "Checked in."
                                else -> null
                            }
                        _state.update { s ->
                            s.copy(
                                events = s.events.map { e -> if (e.id == merged.id) merged else e },
                                communityEvents = s.communityEvents.map { e -> if (e.id == merged.id) merged else e },
                                eventDetailUi =
                                    s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                        event = merged,
                                        actionBusy = false,
                                        snackMessage = note,
                                    ),
                            )
                        }
                    } else {
                        _state.update { s ->
                            s.copy(
                                eventDetailUi =
                                    s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                                        actionBusy = false,
                                        snackMessage = "Unexpected check-in response.",
                                    ),
                            )
                        }
                    }
                },
                onFailure = { e ->
                    val msg = e.userVisibleHttpMessage()
                    _state.update { s ->
                        s.copy(
                            eventDetailUi =
                                s.eventDetailUi?.takeIf { it.eventId == eventId }
                                    ?.copy(
                                        actionBusy = false,
                                        snackMessage = msg,
                                    ),
                        )
                    }
                },
            )
        }
    }

    fun applyEventAttachedSquads(
        eventId: String,
        squads: List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>,
    ) {
        _state.update { s ->
            fun EventDto.withSquads() =
                if (id == eventId) {
                    copy(attachedSquads = squads)
                } else {
                    this
                }
            val updatedEvents = s.events.map { it.withSquads() }
            s.copy(
                events = updatedEvents,
                eventDetailUi =
                    s.eventDetailUi?.takeIf { it.eventId == eventId }?.copy(
                        event = s.eventDetailUi.event?.withSquads(),
                    ),
                circleDetailUi =
                    s.circleDetailUi?.copy(
                        squadScopedEvents =
                            s.circleDetailUi.squadScopedEvents.map { it.withSquads() },
                    ),
            )
        }
    }

    private fun eventDtoForShare(eventId: String): EventDto? {
        val trimmed = eventId.trim().ifBlank { return null }
        _state.value.eventDetailUi
            ?.takeIf { it.eventId == trimmed }
            ?.event
            ?.let { return it }
        return _state.value.events.firstOrNull { it.id == trimmed }
            ?: _state.value.communityEvents.firstOrNull { it.id == trimmed }
            ?: _state.value.squadFeedEvents.firstOrNull { it.id == trimmed }
            ?: _state.value.squadGoingEvents.firstOrNull { it.id == trimmed }
    }

    private fun requestSquadChatFocusAfterShare(circleId: String) {
        val trimmed = circleId.trim().takeIf { it.isNotEmpty() } ?: return
        _state.update {
            it.copy(
                pendingSquadChatFocusCircleId = trimmed,
                pendingSquadChatFocusTick = it.pendingSquadChatFocusTick + 1,
                eventDetailUi = null,
            )
        }
    }

    fun postEventShareToChat(
        eventId: String,
        circleIds: List<String>,
        dmUserIds: List<String>,
        body: String,
    ) {
        viewModelScope.launch {
            val trimmed = body.trim().ifEmpty { return@launch }
            val trimmedEventId = eventId.trim().ifBlank { return@launch }
            val ev = eventDtoForShare(trimmedEventId)
            val ids = circleIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val dms = dmUserIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty() && dms.isEmpty()) return@launch

            fun fail(message: String) {
                _state.update { s ->
                    s.copy(
                        eventDetailUi =
                            s.eventDetailUi?.copy(
                                actionBusy = false,
                                snackMessage = message,
                            ),
                    )
                }
                postSquadsSnack(message)
            }

            _state.update { s ->
                s.copy(
                    eventDetailUi =
                        s.eventDetailUi?.copy(
                            actionBusy = true,
                            snackMessage = null,
                        ),
                )
            }

            for (cid in ids) {
                val r =
                    dataRepository.sendCircleChat(
                        circleId = cid,
                        body = trimmed,
                        clientMessageId = UUID.randomUUID().toString(),
                        eventId = trimmedEventId,
                    )
                r.exceptionOrNull()?.let { err ->
                    fail(err.userVisibleHttpMessage("Could not post to squad chat."))
                    return@launch
                }
            }

            for (uid in dms) {
                val conversation =
                    dataRepository.getOrCreateDirectConversation(uid).getOrElse { err ->
                        fail(err.userVisibleHttpMessage("Could not open DM."))
                        return@launch
                    }
                val conversationId =
                    conversation.id.trim().takeIf { it.isNotEmpty() }
                        ?: continue
                val mr =
                    dataRepository.sendDirectMessage(
                        conversationId = conversationId,
                        body = trimmed,
                        clientMessageId = UUID.randomUUID().toString(),
                        eventId = trimmedEventId,
                    )
                if (mr.isFailure) {
                    val msg =
                        mr.exceptionOrNull()?.userVisibleHttpMessage("Could not send DM.")
                            ?: "Could not send DM."
                    fail(msg)
                    return@launch
                }
            }

            if (ev != null && ev.visibility?.trim()?.lowercase() != "circle") {
                _state.update { s ->
                    if (s.events.none { it.id == ev.id }) {
                        s.copy(events = s.events + ev)
                    } else {
                        s
                    }
                }
            }

            val squadNavId = ids.singleOrNull()
            if (squadNavId != null) {
                requestSquadChatFocusAfterShare(squadNavId)
            } else {
                _state.update { s ->
                    s.copy(
                        eventDetailUi =
                            s.eventDetailUi?.copy(
                                actionBusy = false,
                                snackMessage = "Posted to chat.",
                            ),
                    )
                }
            }
        }
    }

    fun postMapMarkerShareToChat(
        payload: MapMarkerSharePayload,
        circleIds: List<String>,
        dmUserIds: List<String>,
        caption: String,
    ) {
        viewModelScope.launch {
            val ids = circleIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val dms = dmUserIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty() && dms.isEmpty()) return@launch

            val lat = payload.latitude
            val lng = payload.longitude
            val usePlaceAttachment =
                payload.previewKind == MapMarkerSharePreviewKind.SavedPlace &&
                    lat != null &&
                    lng != null &&
                    lat.isFinite() &&
                    lng.isFinite()

            val trimmedCaption = caption.trim()
            if (!usePlaceAttachment) {
                val plainBody = trimmedCaption.ifEmpty { payload.externalShareText.trim() }
                if (plainBody.isEmpty()) return@launch
            }

            val mapPreviewBytes =
                if (usePlaceAttachment) {
                    PlaceMapSnapshotGenerator.jpegBytes(
                        lat!!,
                        lng!!,
                        BuildConfig.MAPBOX_ACCESS_TOKEN,
                        container.application.resources,
                    )
                } else {
                    null
                }

            for (cid in ids) {
                val r =
                    if (usePlaceAttachment) {
                        dataRepository.sendCircleChat(
                            circleId = cid,
                            body = trimmedCaption,
                            clientMessageId = UUID.randomUUID().toString(),
                            placeId = payload.savedPlaceId,
                            placeLatitude = if (payload.savedPlaceId == null) lat else null,
                            placeLongitude = if (payload.savedPlaceId == null) lng else null,
                            placeName = if (payload.savedPlaceId == null) payload.title else null,
                            placeAddressSummary = if (payload.savedPlaceId == null) payload.subtitle else null,
                            mapPreviewBytes = mapPreviewBytes,
                        )
                    } else {
                        dataRepository.sendCircleChat(
                            circleId = cid,
                            body = trimmedCaption.ifEmpty { payload.externalShareText.trim() },
                            clientMessageId = UUID.randomUUID().toString(),
                        )
                    }
                if (r.isFailure) {
                    postSquadsSnack(
                        r.exceptionOrNull()?.userVisibleHttpMessage("Could not post to squad chat.")
                            ?: "Could not post to squad chat.",
                    )
                    return@launch
                }
            }

            for (uid in dms) {
                val conversation =
                    dataRepository.getOrCreateDirectConversation(uid).getOrElse { err ->
                        postSquadsSnack(err.userVisibleHttpMessage("Could not open DM."))
                        return@launch
                    }
                val conversationId =
                    conversation.id.trim().takeIf { it.isNotEmpty() }
                        ?: continue
                val mr =
                    if (usePlaceAttachment) {
                        dataRepository.sendDirectMessage(
                            conversationId = conversationId,
                            body = trimmedCaption,
                            clientMessageId = UUID.randomUUID().toString(),
                            placeId = payload.savedPlaceId,
                            placeLatitude = if (payload.savedPlaceId == null) lat else null,
                            placeLongitude = if (payload.savedPlaceId == null) lng else null,
                            placeName = if (payload.savedPlaceId == null) payload.title else null,
                            placeAddressSummary = if (payload.savedPlaceId == null) payload.subtitle else null,
                            mapPreviewBytes = mapPreviewBytes,
                        )
                    } else {
                        dataRepository.sendDirectMessage(
                            conversationId = conversationId,
                            body = trimmedCaption.ifEmpty { payload.externalShareText.trim() },
                            clientMessageId = UUID.randomUUID().toString(),
                        )
                    }
                if (mr.isFailure) {
                    postSquadsSnack(
                        mr.exceptionOrNull()?.userVisibleHttpMessage("Could not send DM.")
                            ?: "Could not send DM.",
                    )
                    return@launch
                }
            }

            ids.singleOrNull()?.let { requestSquadChatFocusAfterShare(it) }
                ?: postSquadsSnack("Posted to chat.")
        }
    }

    fun openSharedPlaceFromChat(
        attachment: CircleChatPlaceAttachmentDto,
        messageId: String,
    ) {
        if (attachment.isParentDeleted) return
        val snapshot = attachment.savedPlaceSnapshot("chat:$messageId")
        requestMapTabCenteredOn(
            latitude = attachment.latitude,
            longitude = attachment.longitude,
            savedPlaceId = attachment.placeId?.trim()?.takeIf { it.isNotEmpty() },
            savedPlaceSnapshot = snapshot,
        )
    }

    fun signOut() {
        viewModelScope.launch {
            setMapLocationSharing(false)
            PendingDriveStore.clear(container.application)
            _state.update { it.copy(pendingDriveArchives = emptyList()) }
            authRepository.signOut()
        }
    }

    fun dismissCircleDetail() {
        unreadTracker.setSquadChatTabVisible(_state.value.circleDetailUi?.circleId, false)
        ChatFocusBridge.activeChatCircleId = null
        _state.update {
            it.copy(circleDetailUi = null, squadNotificationSettingsCircleId = null)
        }
        reconcileChatPolling()
    }

    fun renameSquadFromSettings(
        circleIdRaw: String,
        nameRaw: String,
        onFinished: (Boolean) -> Unit,
    ) {
        val trimmedName = nameRaw.trim()
        if (trimmedName.length < 2) {
            postSquadsSnack("Name must be at least 2 characters.")
            onFinished(false)
            return
        }
        val circleId = circleIdRaw.trim()
        if (circleId.isEmpty()) {
            onFinished(false)
            return
        }
        viewModelScope.launch {
            dataRepository.patchCircle(circleId, trimmedName).fold(
                onSuccess = {
                    refreshSquadsListPullToRefresh()
                    onFinished(true)
                },
                onFailure = { e ->
                    postSquadsSnack(e.userVisibleHttpMessage("Couldn't rename squad."))
                    onFinished(false)
                },
            )
        }
    }

    fun submitSquadLeaveFromSettings(circleIdRaw: String) {
        val circleId = circleIdRaw.trim()
        if (circleId.isEmpty()) return
        viewModelScope.launch {
            dataRepository.leaveCircle(circleId).fold(
                onSuccess = { res ->
                    dismissSquadNotificationSettingsDialog()
                    val viewing =
                        _state.value.circleDetailUi?.circleId?.trim()?.equals(circleId, ignoreCase = true) == true
                    if (viewing) {
                        dismissCircleDetail()
                    }
                    refreshSquadsListPullToRefresh()
                },
                onFailure = { e ->
                    postSquadsSnack(e.userVisibleHttpMessage())
                },
            )
        }
    }

    fun requestSquadNotificationSettings(circleId: String) {
        val trimmed = circleId.trim()
        if (trimmed.isEmpty()) return
        squadInviteLookupJob?.cancel()
        _state.update {
            it.copy(
                squadNotificationSettingsCircleId = trimmed,
                squadSettingsInvite = SquadSettingsInviteUi(),
                squadSettingsToast = null,
            )
        }
        refreshSignupInviteBalance()
    }

    fun dismissSquadNotificationSettingsDialog() {
        squadInviteLookupJob?.cancel()
        _state.update {
            it.copy(
                squadNotificationSettingsCircleId = null,
                squadSettingsInvite = SquadSettingsInviteUi(),
                squadSettingsToast = null,
            )
        }
    }

    fun refreshSquadGrid(circleIdRaw: String) {
        viewModelScope.launch {
            val circleId = circleIdRaw.trim()
            if (circleId.isEmpty()) return@launch
            _state.update { s ->
                s.copy(
                    circleDetailUi =
                        s.circleDetailUi?.takeIf { it.circleId == circleId }?.copy(
                            squadGridLoading = true,
                            squadGridError = null,
                        ),
                )
            }
            dataRepository.squadGrid(circleId).fold(
                onSuccess = { grid ->
                    _state.update { s ->
                        s.copy(
                            circleDetailUi =
                                s.circleDetailUi?.takeIf { it.circleId == circleId }?.copy(
                                    squadGrid = grid,
                                    squadGridLoading = false,
                                    squadGridError = null,
                                ),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(
                            circleDetailUi =
                                s.circleDetailUi?.takeIf { it.circleId == circleId }?.copy(
                                    squadGrid = null,
                                    squadGridLoading = false,
                                    squadGridError = e.userVisibleHttpMessage("Couldn’t load Grid."),
                                ),
                        )
                    }
                },
            )
        }
    }

    fun openCircleDetail(circleId: String) {
        viewModelScope.launch {
            val trimmed = circleId.trim().takeIf { it.isNotEmpty() } ?: return@launch
            val matched = _state.value.circles.find { c -> ottoUserIdsEqual(c.id, trimmed) }
            val canonicalCircleId = matched?.id ?: trimmed
            val cached = matched
            sessionRepository.markSquadAccessed(
                canonicalCircleId,
                _state.value.circles.mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet(),
            )
            val cachedChat = transcriptStore.squadMessages(canonicalCircleId)
            val chatNeedsNetwork = transcriptStore.shouldRefreshSquadFromNetwork(canonicalCircleId)
            _state.update {
                it.copy(
                    circleDetailUi =
                        CircleDetailUi(
                            circleId = canonicalCircleId,
                            circle = cached,
                            chatMessages = cachedChat,
                            chatLoading = chatNeedsNetwork,
                            chatSendBusy = false,
                            chatSnack = null,
                            squadScopedEvents = emptyList(),
                            squadScopedEventsLoading = true,
                            squadGrid = null,
                            squadGridLoading = false,
                            squadGridError = null,
                        ),
                    squadNotificationSettingsCircleId = null,
                )
            }

            kotlinx.coroutines.coroutineScope {
                val chatDef =
                    if (chatNeedsNetwork) {
                        async {
                            dataRepository.circleChatMessages(
                                circleId = canonicalCircleId,
                                limit = CHAT_MESSAGES_API_MAX_LIMIT,
                            )
                        }
                    } else {
                        null
                    }

                val squadEvDef = async { dataRepository.squadScopedUpcomingEvents(canonicalCircleId) }

                chatDef?.await()?.fold(
                    onSuccess = { msgs ->
                        transcriptStore.replaceSquadMessages(canonicalCircleId, msgs)
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.takeIf { ottoUserIdsEqual(it.circleId, canonicalCircleId) }?.copy(
                                        chatMessages = transcriptStore.squadMessages(canonicalCircleId),
                                        chatLoading = false,
                                    ),
                            )
                        }
                    },
                    onFailure = { _ ->
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.takeIf { it.circleId == canonicalCircleId }?.copy(
                                        chatLoading = false,
                                        chatSnack = "Could not load chat.",
                                    ),
                            )
                        }
                    },
                )

                squadEvDef.await().fold(
                    onSuccess = { scoped ->
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.takeIf { it.circleId == canonicalCircleId }?.copy(
                                        squadScopedEvents = scoped,
                                        squadScopedEventsLoading = false,
                                    ),
                            )
                        }
                    },
                    onFailure = { _ ->
                        _state.update { s ->
                            s.copy(
                                circleDetailUi =
                                    s.circleDetailUi?.takeIf { it.circleId == canonicalCircleId }?.copy(
                                        squadScopedEventsLoading = false,
                                    ),
                            )
                        }
                    },
                )
            }
        }
    }

    /** Refetch `/api/events?circleId=…` for the open squad overlay (pull-to-refresh, RSVP updates). */
    private suspend fun reloadSquadScopedEventsForCircle(circleId: String) {
        val cid = circleId.trim()
        if (cid.isEmpty()) return
        dataRepository.squadScopedUpcomingEvents(cid).fold(
            onSuccess = { scoped ->
                _state.update { s ->
                    s.copy(
                        circleDetailUi =
                            s.circleDetailUi?.takeIf { it.circleId == cid }?.copy(
                                squadScopedEvents = scoped,
                                squadScopedEventsLoading = false,
                            ),
                    )
                }
            },
            onFailure = { _ ->
                _state.update { s ->
                    s.copy(
                        circleDetailUi =
                            s.circleDetailUi?.takeIf { it.circleId == cid }?.copy(
                                squadScopedEventsLoading = false,
                            ),
                    )
                }
            },
        )
    }

    fun toggleAutoCheckIn(enabled: Boolean) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
            _state.update { it.copy(profileSaving = true, profileSnack = null) }
            dataRepository.setAutoEventCheckIn(userId, enabled).fold(
                onSuccess = { dto ->
                    _state.update {
                        it.copy(me = dto, profileSaving = false, profileSnack = "Preference saved.")
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            profileSaving = false,
                            profileSnack = e.userVisibleHttpMessage("Could not update preference."),
                        )
                    }
                },
            )
        }
    }

    fun acknowledgeSharingSafetyDisclaimer() {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
            dataRepository.setSharingSafetyDisclaimerAcknowledged(userId, true).fold(
                onSuccess = { dto ->
                    _state.update { it.copy(me = dto) }
                },
                onFailure = { e ->
                    Log.w("OttoShellVM", "Could not sync sharing safety acknowledgement", e)
                },
            )
        }
    }

    fun toggleShowPublicGoingEventsOnProfile(enabled: Boolean) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
            _state.update { it.copy(profileSaving = true, profileSnack = null) }
            dataRepository.setShowPublicGoingEventsOnProfile(userId, enabled).fold(
                onSuccess = { dto ->
                    _state.update { cur ->
                        cur.copy(
                            me = dto,
                            profileSaving = false,
                            profileSnack = "Preference saved.",
                        )
                    }
                    refreshProfilePublicGoingEvents()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            profileSaving = false,
                            profileSnack = e.userVisibleHttpMessage("Could not update preference."),
                        )
                    }
                },
            )
        }
    }

    fun setDriveStatsVisibility(visibility: DriveStatsVisibilitySetting) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
            _state.update { it.copy(profileSaving = true, profileSnack = null) }
            dataRepository.setDriveStatsVisibility(userId, visibility).fold(
                onSuccess = { dto ->
                    _state.update { cur ->
                        cur.copy(
                            me = dto,
                            profileSaving = false,
                            profileSnack = "Preference saved.",
                        )
                    }
                    val uid = resolveSelfUserId()
                    if (uid != null) {
                        dataRepository.drivingStats(uid).onSuccess { stats ->
                            _state.update { s -> s.copy(stats = stats) }
                        }
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            profileSaving = false,
                            profileSnack = e.userVisibleHttpMessage("Could not update preference."),
                        )
                    }
                },
            )
        }
    }

    private fun refreshProfilePublicGoingEvents() {
        viewModelScope.launch {
            val uid = resolveSelfUserId() ?: return@launch
            dataRepository.publicMemberProfile(uid).onSuccess { dto ->
                _state.update {
                    it.copy(profilePublicGoingEvents = dto.publicGoingEvents.orEmpty())
                }
            }
        }
    }

    private fun mergeDirectThreadMessage(message: DirectMessageDto) {
        mergeDirectThreadMessageRow(message, clearSendBusy = true)
    }

    private fun mergeDirectThreadMessageRow(
        message: DirectMessageDto,
        clearSendBusy: Boolean,
    ) {
        transcriptStore.upsertDirectMessage(message)
        mirrorDirectChatToOpenThread(message.conversationId, clearSendBusy, message)
    }

    private fun directMessageSenderId(dto: DirectMessageDto): String? =
        dto.senderUserId?.trim()?.takeIf { it.isNotEmpty() }
            ?: dto.sender?.id?.trim()?.takeIf { it.isNotEmpty() }

    private fun resolveSelfUserId(): String? =
        _state.value.me?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotEmpty() }

    /** Matches iOS squad chat unread rules (read cursors + Chat tab visibility). */
    private fun applyCircleChatUnreadFromRealtime(
        dto: CircleChatMessageDto,
        incrementIfNewFromOther: Boolean,
    ) {
        val me = resolveSelfUserId() ?: return
        if (ottoUserIdsEqual(dto.senderUserId, me)) return
        val circleId = dto.circleId.trim().takeIf { it.isNotEmpty() } ?: return
        val messages = transcriptStore.squadMessages(circleId)
        val viewingChat =
            unreadTracker.squadChatTabVisibleCircleId != null &&
                ottoUserIdsEqual(unreadTracker.squadChatTabVisibleCircleId, circleId)
        if (viewingChat) {
            val merged =
                if (messages.any { ottoUserIdsEqual(it.id, dto.id) }) {
                    messages
                } else {
                    messages + dto
                }
            unreadTracker.handleSquadMessageUpsert(
                circleId = circleId,
                messages = merged,
                isPinnedToBottom = true,
                lastReadMessageId = merged.lastOrNull()?.id,
            )
            return
        }
        if (!incrementIfNewFromOther) {
            val cached = if (messages.isNotEmpty()) messages else listOf(dto)
            unreadTracker.recomputeSquad(circleId, cached)
            return
        }
        val cached = messages.ifEmpty { listOf(dto) }
        unreadTracker.handleSquadMessageUpsert(
            circleId = circleId,
            messages = cached,
            isPinnedToBottom = false,
            lastReadMessageId = null,
        )
    }

    /** Matches iOS direct message unread rules. */
    private fun applyDirectUnreadFromRealtime(
        dto: DirectMessageDto,
        isNewMessage: Boolean,
    ) {
        val me = resolveSelfUserId() ?: return
        val sender = directMessageSenderId(dto) ?: return
        if (ottoUserIdsEqual(sender, me)) return
        val conversationId = dto.conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        val messages = transcriptStore.directMessages(conversationId)
        val viewing =
            unreadTracker.directThreadVisibleConversationId != null &&
                ottoUserIdsEqual(unreadTracker.directThreadVisibleConversationId, conversationId)
        if (viewing) {
            val merged =
                if (messages.any { ottoUserIdsEqual(it.id, dto.id) }) {
                    messages
                } else {
                    messages + dto
                }
            unreadTracker.handleDirectMessageUpsert(
                conversationId = conversationId,
                messages = merged,
                isPinnedToBottom = true,
                lastReadMessageId = merged.lastOrNull()?.id ?: dto.id,
            )
            return
        }
        if (!isNewMessage) {
            unreadTracker.recomputeDirect(conversationId, messages.ifEmpty { listOf(dto) })
            return
        }
        unreadTracker.handleDirectMessageUpsert(
            conversationId = conversationId,
            messages = (messages + dto).distinctBy { it.id },
            isPinnedToBottom = false,
            lastReadMessageId = null,
        )
    }

    fun onSquadChatUnreadPositionChanged(
        circleId: String,
        chatTabVisible: Boolean,
        pinnedToBottom: Boolean,
        lastReadMessageId: String?,
    ) {
        val id = circleId.trim().takeIf { it.isNotEmpty() } ?: return
        unreadTracker.setSquadChatTabVisible(id, chatTabVisible)
        ChatFocusBridge.activeChatCircleId = if (chatTabVisible) id else null
        reconcileChatPolling()
        if (!chatTabVisible) return
        val messages = transcriptStore.squadMessages(id).ifEmpty {
            _state.value.circleDetailUi?.takeIf { ottoUserIdsEqual(it.circleId, id) }?.chatMessages.orEmpty()
        }
        unreadTracker.handleSquadMessageUpsert(
            circleId = id,
            messages = messages,
            isPinnedToBottom = pinnedToBottom,
            lastReadMessageId = lastReadMessageId,
        )
    }

    fun onDirectChatUnreadPositionChanged(
        conversationId: String,
        threadVisible: Boolean,
        pinnedToBottom: Boolean,
        lastReadMessageId: String?,
    ) {
        val id = conversationId.trim().takeIf { it.isNotEmpty() } ?: return
        unreadTracker.setDirectThreadVisible(id, threadVisible)
        ChatFocusBridge.activeDirectConversationId = if (threadVisible) id else null
        reconcileChatPolling()
        if (!threadVisible) return
        val messages = transcriptStore.directMessages(id).ifEmpty {
            _state.value.directMessages.messages.takeIf {
                _state.value.directMessages.selectedConversationId?.let { sel -> ottoUserIdsEqual(sel, id) } == true
            }.orEmpty()
        }
        unreadTracker.handleDirectMessageUpsert(
            conversationId = id,
            messages = messages,
            isPinnedToBottom = pinnedToBottom,
            lastReadMessageId = lastReadMessageId,
        )
    }

    fun reconcileChatUnreadStateFromNetworkIfNeeded() {
        viewModelScope.launch {
            val me = resolveSelfUserId()?.trim()?.takeIf { it.isNotEmpty() } ?: return@launch
            unreadTracker.bind(me)
            transcriptStore.bind(me)
            val squadMessages =
                _state.value.circles.associate { circle ->
                    val cid = circle.id
                    cid to transcriptStore.squadMessages(cid)
                }
            val dm = _state.value.directMessages
            val directMessages =
                dm.conversations.associate { conv ->
                    conv.id to transcriptStore.directMessages(conv.id)
                }
            unreadTracker.recomputeAll(
                squadMessagesByCircleId = squadMessages,
                directMessagesByConversationId = directMessages,
                directConversations = dm.conversations,
            )
            for (circle in _state.value.circles) {
                val cid = circle.id
                if (transcriptStore.squadMessages(cid).isEmpty()) {
                    dataRepository.circleChatMessages(circleId = cid, limit = 50).onSuccess { msgs ->
                        transcriptStore.reconcileSquadMessages(cid, msgs)
                        unreadTracker.recomputeSquad(cid, transcriptStore.squadMessages(cid))
                    }
                }
            }
            for (conversation in dm.conversations) {
                if (transcriptStore.directMessages(conversation.id).isEmpty()) {
                    unreadTracker.recomputeDirectFromPreview(conversation)
                    dataRepository.directMessages(conversation.id).onSuccess { msgs ->
                        transcriptStore.reconcileDirectMessages(conversation.id, msgs)
                        unreadTracker.recomputeDirect(conversation.id, transcriptStore.directMessages(conversation.id))
                    }
                }
            }
        }
    }

    private suspend fun openDirectThreadFullScreenFromPushConversationId(conversationIdRaw: String) {
        val cid = conversationIdRaw.trim().takeIf { it.isNotEmpty() } ?: return
        refreshDirectConversationList()
        val conv = _state.value.directMessages.conversations.firstOrNull { ottoUserIdsEqual(it.id, cid) }
        if (conv != null) {
            openDirectThreadFullScreen(conv)
        }
    }

    private suspend fun refreshDirectConversationList() {
        _state.update {
            it.copy(directMessages = it.directMessages.copy(listLoading = true, threadSnack = null))
        }
        dataRepository.directConversations().fold(
            onSuccess = { list ->
                val active = list.filter { it.hasActiveDirectThread() }
                _state.update {
                    it.copy(directMessages = it.directMessages.copy(listLoading = false, conversations = active))
                }
            },
            onFailure = { e ->
                _state.update {
                    val dm = it.directMessages
                    it.copy(
                        directMessages =
                            dm.copy(
                                listLoading = false,
                                threadSnack = e.userVisibleHttpMessage("Could not load messages."),
                            ),
                    )
                }
            },
        )
    }

    fun openDirectMessagesOverlay() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    directMessages =
                        it.directMessages.copy(
                            visible = true,
                            threadSnack = null,
                            returnToDmInboxOnThreadBack = true,
                        ),
                )
            }
            syncRealtimeSubscriptions()
            refreshDirectConversationList()
        }
    }

    /** Loads DM threads for the Squads → DMs sub-tab (skips when full-screen DMs already open). */
    fun prefetchDirectConversationsForSquadsTab() {
        viewModelScope.launch {
            if (_state.value.directMessages.visible) return@launch
            refreshDirectConversationList()
        }
    }

    /** Opens full-screen DMs on the selected thread (from Squads tab list or elsewhere). */
    fun openDirectThreadFullScreen(conversation: DirectConversationDto) {
        _state.update {
            it.copy(
                directMessages =
                    it.directMessages.copy(
                        visible = true,
                        threadSnack = null,
                        /** Squads tab already lists threads; back should dismiss, not show duplicate inbox. */
                        returnToDmInboxOnThreadBack = false,
                    ),
            )
        }
        syncRealtimeSubscriptions()
        selectDirectConversation(conversation)
    }

    fun showNewDmComposeSheet() {
        _state.update { it.copy(showNewDmCompose = true) }
    }

    fun dismissNewDmComposeSheet() {
        _state.update { it.copy(showNewDmCompose = false) }
    }

    /** Creates/opens a 1:1 thread from compose, then opens the thread. */
    fun submitNewDmCompose(recipientUserId: String) {
        viewModelScope.launch {
            val rid = recipientUserId.trim().takeIf { it.isNotBlank() } ?: return@launch
            val conv =
                dataRepository.getOrCreateDirectConversation(rid).getOrElse { e ->
                    _state.update {
                        it.copy(
                            squadsSnack = e.userVisibleHttpMessage("Could not open chat."),
                            showNewDmCompose = false,
                        )
                    }
                    return@launch
                }
            _state.update { s ->
                val dm = s.directMessages
                val merged =
                    (listOf(conv) + dm.conversations.filterNot { c -> ottoUserIdsEqual(c.id, conv.id) })
                        .filter { it.hasActiveDirectThread() }
                s.copy(
                    showNewDmCompose = false,
                    squadsSnack = null,
                    directMessages =
                        dm.copy(
                            conversations = merged,
                            listLoading = false,
                        ),
                )
            }
            refreshDirectConversationList()
            openDirectThreadFullScreen(conv)
        }
    }

    fun dismissDirectMessagesOverlay() {
        unreadTracker.setDirectThreadVisible(_state.value.directMessages.selectedConversationId, false)
        _state.update {
            val dm = it.directMessages
            it.copy(
                directMessages =
                    DirectMessagesOverlayUi(
                        visible = false,
                        listLoading = false,
                        conversations = dm.conversations,
                    ),
            )
        }
        syncRealtimeSubscriptions()
        reconcileChatPolling()
    }

    fun openMapPeerProfileOverlay(
        userIdRaw: String,
        seedDisplayName: String? = null,
        seedAvatarUrl: String? = null,
        seedMapAccentKey: String? = null,
        squadManagementCircleId: String? = null,
    ) {
        val userId = userIdRaw.trim().takeIf { it.isNotBlank() } ?: return
        val squadCircle =
            squadManagementCircleId?.trim()?.takeIf { it.isNotEmpty() }
        _state.update {
            it.copy(
                mapPeerProfileOverlay =
                    MapPeerProfileOverlayUi(
                        userId = userId,
                        garageCars = emptyList(),
                        publicGoingEvents = emptyList(),
                        stats = null,
                        loadError = null,
                        loading = true,
                        seedDisplayName = seedDisplayName,
                        seedAvatarUrl = seedAvatarUrl,
                        seedMapAccentKey = seedMapAccentKey,
                        squadManagementCircleId = squadCircle,
                    ),
            )
        }
        refreshMapPeerProfileOverlay(userId)
    }

    fun kickCircleMemberFromPeerProfile(
        circleIdRaw: String,
        userIdRaw: String,
    ) {
        val circleId = circleIdRaw.trim()
        val userId = userIdRaw.trim()
        if (circleId.isEmpty() || userId.isEmpty()) return
        viewModelScope.launch {
            dataRepository.removeCircleMember(circleId, userId).fold(
                onSuccess = { circle ->
                    applyCircleMembersRosterRealtime(circle, emptyList())
                    dismissMapPeerProfileOverlay()
                },
                onFailure = { e ->
                    postSquadsSnack(e.userVisibleHttpMessage("Couldn’t remove squad member."))
                },
            )
        }
    }

    fun patchCircleMemberRoleFromPeerProfile(
        circleIdRaw: String,
        userIdRaw: String,
        roleRaw: String,
    ) {
        val circleId = circleIdRaw.trim()
        val userId = userIdRaw.trim()
        val role = roleRaw.trim().lowercase()
        if (circleId.isEmpty() || userId.isEmpty() || role.isEmpty()) return
        viewModelScope.launch {
            dataRepository.patchCircleMemberRole(circleId, userId, role).fold(
                onSuccess = { circle ->
                    applyCircleMembersRosterRealtime(circle, emptyList())
                    dismissMapPeerProfileOverlay()
                },
                onFailure = { e ->
                    postSquadsSnack(e.userVisibleHttpMessage("Couldn’t update squad role."))
                },
            )
        }
    }

    fun dismissMapPeerProfileOverlay() {
        _state.update { it.copy(mapPeerProfileOverlay = null) }
    }

    private fun refreshMapPeerProfileOverlay(userId: String) {
        viewModelScope.launch {
            val garageDeferred = async { dataRepository.garage(userId) }
            val statsDeferred = async { dataRepository.drivingStats(userId) }
            val publicDeferred = async { dataRepository.publicMemberProfile(userId) }
            val garageCars = garageDeferred.await().getOrElse { emptyList() }
            val stats = statsDeferred.await().getOrNull()
            val publicGoingEvents = publicDeferred.await().getOrNull()?.publicGoingEvents.orEmpty()
            _state.update { s ->
                val cur =
                    s.mapPeerProfileOverlay?.takeIf { it.userId == userId }
                        ?: return@update s
                s.copy(
                    mapPeerProfileOverlay =
                        cur.copy(
                            garageCars = garageCars,
                            publicGoingEvents = publicGoingEvents,
                            stats = stats,
                            loadError = null,
                            loading = false,
                        ),
                )
            }
        }
    }

    fun selectDirectConversation(conversation: DirectConversationDto) {
        val title =
            conversation.otherUser?.displayName?.takeIf { it.isNotBlank() }
                ?: _state.value.contacts
                    .find { u -> conversation.otherUser?.id?.let { oid -> oid == u.id } == true }
                    ?.displayName
                    .orEmpty()
                    .takeIf { it.isNotBlank() }
                ?: "Message"
        viewModelScope.launch {
            loadDirectThread(
                conversationId = conversation.id,
                title = title,
            )
        }
    }

    fun backFromDirectThread() {
        if (!_state.value.directMessages.returnToDmInboxOnThreadBack) {
            dismissDirectMessagesOverlay()
            return
        }
        _state.update {
            val dm = it.directMessages
            it.copy(
                directMessages =
                    dm.copy(
                        selectedConversationId = null,
                        threadTitle = "",
                        messages = emptyList(),
                        threadLoading = false,
                        sendBusy = false,
                        threadSnack = null,
                        threadReplyTo = null,
                        threadEditingMessageId = null,
                    ),
            )
        }
        syncRealtimeSubscriptions()
    }

    fun blockPeerUser(peerUserId: String, onSuccess: () -> Unit = {}) {
        val trimmed = peerUserId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            dataRepository.blockUser(trimmed).fold(
                onSuccess = { dto ->
                    _state.update { it.copy(me = dto) }
                    refreshDirectConversationList()
                    onSuccess()
                },
                onFailure = { e ->
                    val msg = e.userVisibleHttpMessage("Could not block user.")
                    _state.update {
                        it.copy(
                            directMessages = it.directMessages.copy(threadSnack = msg),
                            squadsSnack = msg,
                        )
                    }
                },
            )
        }
    }

    fun unblockPeerUser(peerUserId: String) {
        val trimmed = peerUserId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            dataRepository.unblockUser(trimmed).fold(
                onSuccess = { dto ->
                    _state.update { it.copy(me = dto) }
                    refreshDirectConversationList()
                },
                onFailure = { e ->
                    val msg = e.userVisibleHttpMessage("Could not unblock user.")
                    _state.update {
                        it.copy(
                            directMessages = it.directMessages.copy(threadSnack = msg),
                            squadsSnack = msg,
                        )
                    }
                },
            )
        }
    }

    private suspend fun loadDirectThread(
        conversationId: String,
        title: String,
    ) {
        unreadTracker.setDirectThreadVisible(conversationId, true)
        val cached = transcriptStore.directMessages(conversationId)
        val needsNetwork = transcriptStore.shouldRefreshDirectFromNetwork(conversationId)
        _state.update {
            it.copy(
                directMessages =
                    it.directMessages.copy(
                        selectedConversationId = conversationId,
                        threadTitle = title,
                        threadLoading = needsNetwork && cached.isEmpty(),
                        messages = cached,
                        threadSnack = null,
                        threadReplyTo = null,
                        threadEditingMessageId = null,
                    ),
            )
        }
        syncRealtimeSubscriptions()
        reconcileChatPolling()
        if (!needsNetwork && cached.isNotEmpty()) {
            unreadTracker.handleDirectMessageUpsert(
                conversationId = conversationId,
                messages = cached,
                isPinnedToBottom = true,
                lastReadMessageId = cached.lastOrNull()?.id,
            )
            return
        }
        dataRepository.directMessages(conversationId).fold(
            onSuccess = { msgs ->
                transcriptStore.replaceDirectMessages(conversationId, msgs)
                mirrorDirectChatToOpenThread(conversationId)
                unreadTracker.handleDirectMessageUpsert(
                    conversationId = conversationId,
                    messages = msgs,
                    isPinnedToBottom = true,
                    lastReadMessageId = msgs.lastOrNull()?.id,
                )
            },
            onFailure = { e ->
                _state.update {
                    val dm = it.directMessages
                    it.copy(
                        directMessages =
                            dm.copy(
                                threadLoading = false,
                                threadSnack = e.userVisibleHttpMessage("Could not load thread."),
                            ),
                    )
                }
            },
        )
    }

    fun sendDirectMessage(
        bodyRaw: String,
        photo: ChatSendPhotoAttachment? = null,
        pendingAttachment: ChatPendingComposerAttachment? = null,
    ) {
        viewModelScope.launch {
            val editingId =
                _state.value.directMessages.threadEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
            val hasPhoto = photo?.bytes?.isNotEmpty() == true
            val normalized =
                if (!editingId.isNullOrBlank()) {
                    ChatOutgoingImagePayload(body = bodyRaw.trim(), imageUrl = null)
                } else {
                    ChatOutgoingImageUrlNormalizer.normalize(bodyRaw, pendingAttachment)
                }
            val body = normalized.body
            val imageUrl = normalized.imageUrl
            if (body.isEmpty() && !hasPhoto && imageUrl == null) return@launch
            val conversationId =
                _state.value.directMessages.selectedConversationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@launch
            val myId = _state.value.me?.id?.trim().orEmpty()
            _state.update {
                val dm = it.directMessages
                it.copy(directMessages = dm.copy(sendBusy = true, threadSnack = null))
            }
            if (!editingId.isNullOrBlank()) {
                dataRepository.patchDirectMessage(conversationId, editingId, body).fold(
                    onSuccess = { msg ->
                        _state.update { s ->
                            val dm = s.directMessages
                            s.copy(
                                directMessages =
                                    dm.copy(
                                        threadReplyTo = null,
                                        threadEditingMessageId = null,
                                    ),
                            )
                        }
                        mergeDirectThreadMessageRow(msg, clearSendBusy = false)
                    },
                    onFailure = { e ->
                        _state.update {
                            val dm = it.directMessages
                            it.copy(
                                directMessages =
                                    dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()),
                            )
                        }
                    },
                )
            } else {
                val replyToId =
                    _state.value.directMessages.threadReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
                val clientMsg = UUID.randomUUID().toString()
                dataRepository
                    .sendDirectMessage(
                        conversationId,
                        body,
                        clientMsg,
                        replyToMessageId = replyToId,
                        photoBytes = photo?.bytes?.takeIf { it.isNotEmpty() },
                        photoContentType = photo?.contentType?.trim()?.takeIf { it.isNotEmpty() },
                        imageUrl = imageUrl,
                    ).fold(
                        onSuccess = { msg ->
                            OttoAnalytics.logChatMessageSent(
                                channel = "direct",
                                attachmentType =
                                    when {
                                        hasPhoto -> "photo"
                                        imageUrl != null ->
                                            if (ChatImageUrlDisplay.isAnimatedImageUrl(imageUrl)) {
                                                "gif"
                                            } else {
                                                "image_url"
                                            }
                                        else -> "none"
                                    },
                            )
                            normalized.klipyShare?.let { share ->
                                viewModelScope.launch {
                                    KlipyAPIClient.reportShare(
                                        slug = share.slug,
                                        customerId = myId,
                                        searchQuery = share.searchQuery,
                                    )
                                }
                            }
                            _state.update { s ->
                                val dm = s.directMessages
                                s.copy(directMessages = dm.copy(threadReplyTo = null))
                            }
                            mergeDirectThreadMessage(msg)
                        },
                        onFailure = { e ->
                            _state.update {
                                val dm = it.directMessages
                                it.copy(
                                    directMessages =
                                        dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()),
                                )
                            }
                        },
                    )
            }
            _state.update {
                val dm = it.directMessages
                it.copy(directMessages = dm.copy(sendBusy = false))
            }
        }
    }

    fun sendDirectChatVideo(
        bodyRaw: String,
        video: ChatSendVideoAttachment,
    ) {
        val clientMsg = UUID.randomUUID().toString()
        val prepared = video.prepared
        val job =
            viewModelScope.launch {
            val body = bodyRaw.trim()
            val conversationId =
                _state.value.directMessages.selectedConversationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@launch
            val replyToId =
                _state.value.directMessages.threadReplyTo?.id?.trim()?.takeIf { it.isNotBlank() }
            val myId = _state.value.me?.id?.trim().orEmpty()
            val me = _state.value.me
            val optimistic =
                DirectMessageDto(
                    id = "pending-$clientMsg",
                    conversationId = conversationId,
                    senderUserId = myId,
                    sender =
                        me?.let {
                            CircleChatSenderDto(
                                id = it.id,
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl,
                                mapAccentKey = it.mapAccentKey,
                            )
                        },
                    body = body,
                    messageType = "user",
                    videoAttachment =
                        ChatVideoAttachmentDto(
                            videoUrl = "",
                            thumbnailUrl = "",
                            durationSeconds = prepared.durationSeconds,
                            width = prepared.width,
                            height = prepared.height,
                            mimeType = prepared.mimeType,
                        ),
                    clientMessageId = clientMsg,
                    replyToMessageId = replyToId,
                    createdAt = java.time.Instant.now().toString(),
                )
            _state.update {
                val dm = it.directMessages
                it.copy(directMessages = dm.copy(sendBusy = true, threadSnack = null, threadReplyTo = null))
            }
            mergeDirectThreadMessageRow(optimistic, clearSendBusy = false)
            to.ottomot.driftd.core.media.ChatVideoUploadState.register(clientMsg, prepared.thumbnailBitmap)
            to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, 0f)
            val urls =
                dataRepository.requestDirectChatVideoUploadUrls(conversationId, prepared.mimeType).getOrElse { e ->
                    to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                    _state.update {
                        val dm = it.directMessages
                        it.copy(directMessages = dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()))
                    }
                    return@launch
                }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    to.ottomot.driftd.core.media.ChatVideoS3Uploader.putBytes(
                        uploadUrl = urls.thumbnail.uploadUrl,
                        bytes = prepared.thumbnailJpeg,
                        contentType = "image/jpeg",
                    ) { p -> to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, p * 0.08f) }
                    to.ottomot.driftd.core.media.ChatVideoS3Uploader.putFile(
                        uploadUrl = urls.video.uploadUrl,
                        file = prepared.localVideoFile,
                        contentType = prepared.mimeType,
                    ) { p -> to.ottomot.driftd.core.media.ChatVideoUploadState.setProgress(clientMsg, 0.08f + p * 0.84f) }
                }
                val attachment =
                    ChatVideoAttachmentDto(
                        videoUrl = urls.video.fileUrl,
                        thumbnailUrl = urls.thumbnail.fileUrl,
                        durationSeconds = prepared.durationSeconds,
                        width = prepared.width,
                        height = prepared.height,
                        mimeType = prepared.mimeType,
                    )
                dataRepository
                    .sendDirectChatVideoMessage(
                        conversationId = conversationId,
                        body = body,
                        clientMessageId = clientMsg,
                        videoAttachment = attachment,
                        replyToMessageId = replyToId,
                    ).fold(
                        onSuccess = { msg ->
                            if (!isActive) return@fold
                            OttoAnalytics.logChatMessageSent(
                                channel = "direct",
                                attachmentType = "video",
                            )
                            to.ottomot.driftd.core.media.ChatVideoUploadState.clear(clientMsg)
                            mergeDirectThreadMessage(msg)
                        },
                        onFailure = { e ->
                            if (!isActive) return@fold
                            to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                            _state.update {
                                val dm = it.directMessages
                                it.copy(directMessages = dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()))
                            }
                        },
                    )
            } catch (e: Exception) {
                if (!isActive) return@launch
                to.ottomot.driftd.core.media.ChatVideoUploadState.markFailed(clientMsg)
                _state.update {
                    val dm = it.directMessages
                    it.copy(directMessages = dm.copy(sendBusy = false, threadSnack = e.userVisibleHttpMessage()))
                }
            }
        }
        chatVideoUploadJobs[clientMsg] = job
        job.invokeOnCompletion { chatVideoUploadJobs.remove(clientMsg) }
    }

    fun cancelDirectChatVideoUpload(clientMessageId: String) {
        val id = clientMessageId.trim().takeIf { it.isNotEmpty() } ?: return
        chatVideoUploadJobs.remove(id)?.cancel()
        to.ottomot.driftd.core.media.ChatVideoUploadState.clear(id)
        _state.update { s ->
            val dm = s.directMessages
            s.copy(
                directMessages =
                    dm.copy(
                        messages =
                            dm.messages.filterNot {
                                it.id == "pending-$id" ||
                                    (it.id.startsWith("pending-") && it.clientMessageId == id)
                            },
                        sendBusy = false,
                    ),
            )
        }
    }

    fun beginDirectThreadEdit(messageId: String) {
        val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return
        _state.update {
            val dm = it.directMessages
            it.copy(directMessages = dm.copy(threadEditingMessageId = mid, threadReplyTo = null))
        }
    }

    fun cancelDirectThreadEdit() {
        _state.update {
            val dm = it.directMessages
            it.copy(directMessages = dm.copy(threadEditingMessageId = null))
        }
    }

    fun deleteDirectThreadMessage(messageId: String) {
        viewModelScope.launch {
            val conversationId =
                _state.value.directMessages.selectedConversationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@launch
            val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.deleteDirectMessage(conversationId, mid).fold(
                onSuccess = { tomb -> mergeDirectThreadMessageRow(tomb, clearSendBusy = false) },
                onFailure = { e ->
                    _state.update {
                        val dm = it.directMessages
                        it.copy(directMessages = dm.copy(threadSnack = e.userVisibleHttpMessage()))
                    }
                },
            )
        }
    }

    fun setDirectThreadReplyTo(message: DirectMessageDto?) {
        _state.update {
            val dm = it.directMessages
            it.copy(directMessages = dm.copy(threadReplyTo = message))
        }
    }

    fun postDirectMessageReaction(
        messageId: String,
        emoji: String,
    ) {
        viewModelScope.launch {
            val conversationId =
                _state.value.directMessages.selectedConversationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@launch
            val mid = messageId.trim().takeIf { it.isNotBlank() } ?: return@launch
            val em = emoji.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.postDirectMessageReaction(conversationId, mid, em).fold(
                onSuccess = { msg -> mergeDirectThreadMessageRow(msg, clearSendBusy = false) },
                onFailure = { e ->
                    _state.update {
                        val dm = it.directMessages
                        it.copy(directMessages = dm.copy(threadSnack = e.userVisibleHttpMessage("Could not react.")))
                    }
                },
            )
        }
    }

    fun startDirectWithContact(recipientUserIdRaw: String) {
        viewModelScope.launch {
            val recipientUserId = recipientUserIdRaw.trim().takeIf { it.isNotBlank() } ?: return@launch
            val contactName =
                _state.value.contacts.find { it.id == recipientUserId }?.displayName?.takeIf { it.isNotBlank() }

            val conv =
                dataRepository.getOrCreateDirectConversation(recipientUserId).getOrElse { e ->
                    _state.update {
                        it.copy(
                            directMessages =
                                it.directMessages.copy(
                                    visible = true,
                                    threadSnack = e.userVisibleHttpMessage("Could not open chat."),
                                ),
                        )
                    }
                    return@launch
                }

            _state.update {
                val dm = it.directMessages
                val mergedConvos =
                    (listOf(conv) + dm.conversations.filter { it.id != conv.id })
                        .filter { it.hasActiveDirectThread() }
                it.copy(
                    directMessages =
                        dm.copy(
                            visible = true,
                            conversations = mergedConvos,
                            listLoading = false,
                            threadSnack = null,
                            returnToDmInboxOnThreadBack = true,
                        ),
                )
            }

            val title =
                conv.otherUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: contactName
                    ?: "Message"

            loadDirectThread(
                conv.id,
                title,
            )
        }
    }

    fun saveProfileDisplayName(nameRaw: String) {
        viewModelScope.launch {
            val displayName = nameRaw.trim()
            if (displayName.isEmpty()) return@launch
            val userId =
                sessionRepository.authUserIdState.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch

            _state.update { it.copy(profileSaving = true, profileSnack = null) }

            dataRepository.patchUserDisplayName(userId = userId, displayName = displayName).fold(
                onSuccess = { dto ->
                    _state.update {
                        it.copy(me = dto, profileSaving = false, profileSnack = "Profile updated.")
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(profileSaving = false, profileSnack = e.userVisibleHttpMessage("Could not save name."))
                    }
                },
            )
        }
    }

    fun saveMapAccentKey(mapAccentKey: String) {
        viewModelScope.launch {
            val key = mapAccentKey.trim().takeIf { it.isNotEmpty() } ?: return@launch
            if (key !in MapAccentPaletteKeys) return@launch
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch

            _state.update { it.copy(profileSaving = true, profileSnack = null) }
            dataRepository.patchUserMapAccent(userId, key).fold(
                onSuccess = { dto ->
                    _state.update {
                        it.copy(me = dto, profileSaving = false, profileSnack = "Accent saved.")
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(profileSaving = false, profileSnack = e.userVisibleHttpMessage("Could not save accent."))
                    }
                },
            )
        }
    }

    fun deleteAccountConfirmed() {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.deleteUserAccount(userId).fold(
                onSuccess = {
                    dismissDirectMessagesOverlay()
                    setMapLocationSharing(false)
                    authRepository.signOut()
                },
                onFailure = { e ->
                    _state.update {
                        val snack = e.userVisibleHttpMessage("Could not delete account.")
                        it.copy(profileSnack = snack)
                    }
                },
            )
        }
    }

    fun addGarageCar(
        nicknameRaw: String,
        makeRaw: String,
        makeIdRaw: String?,
        modelRaw: String,
        yearRaw: Int?,
        colorRaw: String?,
        logoSlugRaw: String?,
        primary: Boolean,
        imageBytes: ByteArray? = null,
        imageContentType: String? = null,
    ) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val nickname = nicknameRaw.trim()
            val make = makeRaw.trim()
            val makeId = makeIdRaw?.trim()?.takeIf { it.isNotEmpty() }
            val model = modelRaw.trim()
            val logoSlug = logoSlugRaw?.trim()?.takeIf { it.isNotEmpty() }
            if (make.isEmpty() || model.isEmpty()) return@launch
            val colorTrim = colorRaw?.trim()?.takeIf { it.isNotEmpty() }
            dataRepository
                .createGarageCar(userId, nickname, make, makeId, model, yearRaw, colorTrim, logoSlug, primary)
                .fold(
                    onSuccess = { created ->
                        val photoBytes = imageBytes?.takeIf { it.isNotEmpty() }
                        OttoAnalytics.logGarageCarAdded(hasPhoto = photoBytes != null)
                        var photoSnack: String? = null
                        if (photoBytes != null) {
                            val ext =
                                when {
                                    imageContentType?.contains("png", ignoreCase = true) == true -> "photo.png"
                                    imageContentType?.contains("webp", ignoreCase = true) == true -> "photo.webp"
                                    else -> "photo.jpg"
                                }
                            val ct = imageContentType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                            dataRepository.uploadGarageCarPhoto(userId, created.id, photoBytes, ext, ct).fold(
                                onSuccess = { },
                                onFailure = { e ->
                                    photoSnack =
                                        e.userVisibleHttpMessage("Car added, but photo upload failed.")
                                },
                            )
                        }
                        _state.update { s ->
                            s.copy(garageSnack = photoSnack ?: "Car added")
                        }
                        loadCoreFeeds()
                    },
                    onFailure = { e ->
                        _state.update { it.copy(garageSnack = e.userVisibleHttpMessage("Could not add car.")) }
                    },
                )
        }
    }

    fun patchGarageCar(
        carId: String,
        nicknameRaw: String?,
        makeRaw: String?,
        makeIdRaw: String?,
        modelRaw: String?,
        yearRaw: Int?,
        colorRaw: String?,
        logoSlugRaw: String?,
        primaryRaw: Boolean?,
        imageBytes: ByteArray? = null,
        imageContentType: String? = null,
    ) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val cid = carId.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository
                .patchGarageCar(
                    userId = userId,
                    carId = cid,
                    nickname = nicknameRaw?.trim(),
                    make = makeRaw?.trim()?.takeIf { it.isNotEmpty() },
                    makeId = makeIdRaw?.trim()?.takeIf { it.isNotEmpty() },
                    model = modelRaw?.trim()?.takeIf { it.isNotEmpty() },
                    year = yearRaw,
                    color = colorRaw?.trim()?.takeIf { it.isNotEmpty() },
                    logoSlug = logoSlugRaw?.trim()?.takeIf { it.isNotEmpty() },
                    isPrimary = primaryRaw,
                ).fold(
                    onSuccess = {
                        val photoBytes = imageBytes?.takeIf { it.isNotEmpty() }
                        var photoSnack: String? = null
                        if (photoBytes != null) {
                            val ext =
                                when {
                                    imageContentType?.contains("png", ignoreCase = true) == true -> "photo.png"
                                    imageContentType?.contains("webp", ignoreCase = true) == true -> "photo.webp"
                                    else -> "photo.jpg"
                                }
                            val ct = imageContentType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                            dataRepository.uploadGarageCarPhoto(userId, cid, photoBytes, ext, ct).fold(
                                onSuccess = { },
                                onFailure = { e ->
                                    photoSnack =
                                        e.userVisibleHttpMessage("Car updated, but photo upload failed.")
                                },
                            )
                        }
                        _state.update { s ->
                            s.copy(garageSnack = photoSnack ?: "Car updated")
                        }
                        loadCoreFeeds()
                    },
                    onFailure = { e ->
                        _state.update { it.copy(garageSnack = e.userVisibleHttpMessage("Could not update car.")) }
                    },
                )
        }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.setSoundEffectsEnabled(enabled)
        }
    }

    fun deleteGarageCar(carId: String) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val cid = carId.trim().takeIf { it.isNotBlank() } ?: return@launch
            dataRepository.deleteGarageCar(userId, cid).fold(
                onSuccess = {
                    presentDeletedToast(
                        container.application.getString(R.string.otto_deleted_item_car),
                    )
                    _state.update { it.copy(garageSnack = null) }
                    loadCoreFeeds()
                },
                onFailure = { e ->
                    _state.update { it.copy(garageSnack = e.userVisibleHttpMessage("Could not remove car.")) }
                },
            )
        }
    }

    fun reorderGarageCars(orderedCarIds: List<String>) {
        viewModelScope.launch {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            val cur = _state.value.garageCars
            if (orderedCarIds.size != cur.size) return@launch
            if (orderedCarIds.toSet() != cur.map { it.id }.toSet()) return@launch
            dataRepository.reorderGarageCars(userId, orderedCarIds).fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(
                            garageCars = list,
                            selectedSharingCarId = reconcileSelectedSharingCarId(list, it.selectedSharingCarId),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(garageSnack = e.userVisibleHttpMessage("Could not save order.")) }
                    loadCoreFeeds(updateGlobalRefreshingIndicator = false)
                },
            )
        }
    }

    private suspend fun loadPendingInvites() {
        dataRepository.fetchMyCircleInvites().fold(
            onSuccess = { invites -> _state.update { it.copy(pendingInvites = invites) } },
            onFailure = { },
        )
    }

    private fun applyUserProfileFromRealtime(p: UserProfileRealtimeDto) {
        val pid = p.id.trim()
        if (pid.isEmpty()) return
        _state.update { s ->
            val nextCircleDetail =
                s.circleDetailUi?.let { cd ->
                    val msgs = cd.chatMessages.map { it.withPatchedProfile(p) }
                    val reply = cd.chatReplyTo?.withPatchedProfile(p)
                    val grid = cd.squadGrid?.withPatchedLeaders(p)
                    cd.copy(chatMessages = msgs, chatReplyTo = reply, squadGrid = grid)
                }
            s.copy(
                me = s.me?.withPatchedProfile(p),
                contacts = s.contacts.map { it.withPatchedProfile(p) },
                circleDetailUi = nextCircleDetail,
                directMessages = patchDirectMessagesOverlay(s.directMessages, p),
                mapPeerProfileOverlay =
                    s.mapPeerProfileOverlay?.let { o ->
                        if (o.userId != pid) {
                            o
                        } else {
                            o.copy(
                                seedDisplayName = p.displayName,
                                seedAvatarUrl = p.avatarUrl,
                                seedMapAccentKey = p.mapAccentKey,
                            )
                        }
                    },
            )
        }
    }

    private fun patchDirectMessagesOverlay(
        dm: DirectMessagesOverlayUi,
        p: UserProfileRealtimeDto,
    ): DirectMessagesOverlayUi {
        val convs = dm.conversations.map { it.withPatchedProfile(p) }
        val msgs = dm.messages.map { it.withPatchedProfile(p) }
        val reply = dm.threadReplyTo?.withPatchedProfile(p)
        return dm.copy(
            conversations = convs,
            messages = msgs,
            threadReplyTo = reply,
        )
    }

    private fun applyCircleMembersRosterRealtime(
        circle: CircleDto,
        users: List<UserDto>,
    ) {
        val myId = sessionRepository.authUserIdState.value?.trim().orEmpty()
        val memberIds = circle.members.orEmpty().map { it.userId }.toSet()
        val stillIn = myId.isNotEmpty() && (memberIds.contains(myId) || circle.ownerId == myId)
        _state.update { s ->
            val contactMap = s.contacts.associateBy { it.id }.toMutableMap()
            users.forEach { u -> contactMap[u.id] = u }
            val nextContacts = contactMap.values.toList()
            var nextMe = s.me
            users.forEach { u ->
                if (u.id == nextMe?.id) {
                    nextMe = u
                }
            }
            val nextCircles =
                if (!stillIn) {
                    s.circles.filter { it.id != circle.id }
                } else {
                    val has = s.circles.any { it.id == circle.id }
                    if (has) {
                        s.circles.map { if (it.id == circle.id) circle else it }
                    } else {
                        s.circles + circle
                    }
                }
            val nextMapPresenceCircleId =
                if (!stillIn && s.mapPresenceCircleId == circle.id) {
                    nextCircles.firstOrNull()?.id.orEmpty()
                } else {
                    s.mapPresenceCircleId
                }
            val nextDetail =
                s.circleDetailUi?.takeIf { it.circleId == circle.id }?.copy(circle = circle)
            val nextLayerIds =
                mergedMapLayerCircleIds(
                    previousCircles = s.circles,
                    nextCircles = nextCircles,
                    rawLayers = s.mapLayerSelectedCircleIds,
                )
            s.copy(
                contacts = nextContacts,
                me = nextMe,
                circles = nextCircles,
                mapPresenceCircleId = nextMapPresenceCircleId,
                mapLayerSelectedCircleIds = nextLayerIds,
                circleDetailUi = nextDetail,
            )
        }
        syncRealtimeSubscriptions()
    }

    private suspend fun handleRealtime(incoming: OttoRealtimeCoordinator.Incoming) {
        when (incoming) {
            is OttoRealtimeCoordinator.Incoming.CircleChatNew -> {
                val dto = dataRepository.parseChatMessage(incoming.message) ?: return
                upsertChatMessage(dto)
                applyCircleChatUnreadFromRealtime(dto, incrementIfNewFromOther = true)
                val focusedChatCircleId = unreadTracker.squadChatTabVisibleCircleId
                val squadName =
                    _state.value.circles.firstOrNull { ottoUserIdsEqual(it.id, dto.circleId) }?.name
                val isMuted =
                    sessionRepository.shouldSuppressSquadChatPushSoundSync(
                        dto.circleId,
                        "circle.chat.new_message",
                    )
                EngagementFeedbackAndroid.maybeSquadInThreadEngagement(
                    container.application,
                    dto,
                    sessionRepository.authUserIdState.value,
                    focusedChatCircleId,
                )
                EngagementFeedbackAndroid.maybeSquadForegroundAlert(
                    container.application,
                    dto,
                    sessionRepository.authUserIdState.value,
                    focusedChatCircleId,
                    squadDisplayName = squadName,
                    soundEffectsEnabled = _state.value.soundEffectsEnabled,
                    isMuted = isMuted,
                )
            }
            is OttoRealtimeCoordinator.Incoming.CircleChatUpdated -> {
                val dto = dataRepository.parseChatMessage(incoming.message) ?: return
                upsertChatMessage(dto)
                applyCircleChatUnreadFromRealtime(dto, incrementIfNewFromOther = false)
            }
            is OttoRealtimeCoordinator.Incoming.PresenceUpdated -> {
                val p = incoming.presence
                val circleIdJson = p["circleId"]?.takeIf { it.isJsonPrimitive }?.asString ?: return
                val s = _state.value
                val allowed =
                    effectiveMapLayerCircleIds(
                        circles = s.circles,
                        preferredScopeId = s.mapPresenceCircleId,
                        selectedLayerIds = s.mapLayerSelectedCircleIds,
                    )
                if (circleIdJson !in allowed) return
                val member = dataRepository.parsePresenceUpdate(p) ?: return
                _state.update { st ->
                    val byCircle = st.presenceMembersByCircleId.toMutableMap()
                    val curCircle = byCircle[circleIdJson].orEmpty()
                    val nextCircle =
                        if (member.inApp == false) {
                            curCircle.filter { it.userId != member.userId }
                        } else {
                            val others = curCircle.filter { it.userId != member.userId }
                            val prevSame = curCircle.firstOrNull { it.userId == member.userId }
                            others + mergePresenceMemberUpdate(prevSame, member)
                        }
                    byCircle[circleIdJson] = nextCircle
                    val merged = mergePresenceLists(byCircle.values.toList())
                    st.copy(
                        presenceMembers = merged,
                        presenceMembersByCircleId = byCircle,
                    )
                }
            }
            is OttoRealtimeCoordinator.Incoming.DirectChatNew -> {
                val dto = dataRepository.parseDirectMessage(incoming.message) ?: return
                mergeDirectThreadMessage(dto)
                applyDirectUnreadFromRealtime(dto, isNewMessage = true)
                val focusedConversationId = unreadTracker.directThreadVisibleConversationId
                EngagementFeedbackAndroid.maybeDirectInThreadEngagement(
                    container.application,
                    dto,
                    sessionRepository.authUserIdState.value,
                    focusedConversationId,
                )
                EngagementFeedbackAndroid.maybeDirectForegroundAlert(
                    container.application,
                    dto,
                    sessionRepository.authUserIdState.value,
                    focusedConversationId,
                    senderDisplayName = dto.sender?.displayName,
                    soundEffectsEnabled = _state.value.soundEffectsEnabled,
                )
            }
            is OttoRealtimeCoordinator.Incoming.DirectChatUpdated -> {
                val dto = dataRepository.parseDirectMessage(incoming.message) ?: return
                mergeDirectThreadMessage(dto)
                applyDirectUnreadFromRealtime(dto, isNewMessage = false)
            }
            is OttoRealtimeCoordinator.Incoming.UserProfileUpdated -> {
                val p = dataRepository.parseUserProfileRealtime(incoming.profile) ?: return
                applyUserProfileFromRealtime(p)
            }
            is OttoRealtimeCoordinator.Incoming.CircleMembersUpdated -> {
                val parsed = dataRepository.parseCircleMembersUpdated(incoming.payload) ?: return
                val c = parsed.circle ?: return
                applyCircleMembersRosterRealtime(c, parsed.users.orEmpty())
            }
            is OttoRealtimeCoordinator.Incoming.ProfileProgressionLevelUp -> {
                val levelUp = dataRepository.parseProfileLevelUp(incoming.levelUp) ?: return
                presentProfileLevelUp(levelUp)
            }
            OttoRealtimeCoordinator.Incoming.Connected -> {
                chatRealtimeConnected = true
                stopChatPolling()
                refreshOpenChatIfVisible()
            }
            OttoRealtimeCoordinator.Incoming.Disconnected -> {
                chatRealtimeConnected = false
                reconcileChatPolling()
            }
        }
    }

    private fun upsertChatMessage(dto: CircleChatMessageDto) {
        mergeCircleChatMessage(dto, clearSendBusy = true)
    }

    private fun mergeCircleChatMessage(
        dto: CircleChatMessageDto,
        clearSendBusy: Boolean,
    ) {
        transcriptStore.upsertSquadMessage(dto)
        mirrorSquadChatToOpenDetail(dto.circleId, clearSendBusy, dto)
    }

    private fun mirrorSquadChatToOpenDetail(
        circleId: String,
        clearSendBusy: Boolean = false,
        updatedDto: CircleChatMessageDto? = null,
    ) {
        _state.update { s ->
            val cd = s.circleDetailUi ?: return@update s
            if (!ottoUserIdsEqual(cd.circleId, circleId)) return@update s
            val messages = transcriptStore.squadMessages(circleId)
            val clearEdit =
                updatedDto?.let { msg ->
                    !msg.deletedAt.isNullOrBlank() &&
                        cd.chatEditingMessageId != null &&
                        ottoUserIdsEqual(cd.chatEditingMessageId, msg.id)
                } ?: false
            s.copy(
                circleDetailUi =
                    cd.copy(
                        chatMessages = messages,
                        chatSendBusy = if (clearSendBusy) false else cd.chatSendBusy,
                        chatEditingMessageId = if (clearEdit) null else cd.chatEditingMessageId,
                    ),
            )
        }
    }

    private fun mirrorDirectChatToOpenThread(
        conversationId: String,
        clearSendBusy: Boolean = false,
        updatedDto: DirectMessageDto? = null,
    ) {
        _state.update { s ->
            val dm = s.directMessages
            if (!dm.visible || dm.selectedConversationId == null) return@update s
            if (!ottoUserIdsEqual(dm.selectedConversationId, conversationId)) return@update s
            val messages = transcriptStore.directMessages(conversationId)
            val clearEdit =
                updatedDto?.let { msg ->
                    !msg.deletedAt.isNullOrBlank() &&
                        dm.threadEditingMessageId != null &&
                        ottoUserIdsEqual(dm.threadEditingMessageId, msg.id)
                } ?: false
            s.copy(
                directMessages =
                    dm.copy(
                        messages = messages,
                        threadLoading = false,
                        sendBusy = if (clearSendBusy) false else dm.sendBusy,
                        threadSnack = if (clearSendBusy) null else dm.threadSnack,
                        threadEditingMessageId = if (clearEdit) null else dm.threadEditingMessageId,
                    ),
            )
        }
    }

    private fun refreshOpenChatIfVisible() {
        viewModelScope.launch { pollVisibleChatOnce(force = true) }
    }

    private fun reconcileChatPolling() {
        val squadVisible = unreadTracker.squadChatTabVisibleCircleId != null
        val dmVisible = unreadTracker.directThreadVisibleConversationId != null
        if (!squadVisible && !dmVisible) {
            stopChatPolling()
            return
        }
        if (chatRealtimeConnected) {
            stopChatPolling()
            return
        }
        if (chatPollJob?.isActive == true) return
        chatPollJob =
            viewModelScope.launch {
                while (isActive && !chatRealtimeConnected) {
                    pollVisibleChatOnce(force = true)
                    delay(5_000L)
                    if (
                        unreadTracker.squadChatTabVisibleCircleId == null &&
                        unreadTracker.directThreadVisibleConversationId == null
                    ) {
                        break
                    }
                }
            }
    }

    private fun stopChatPolling() {
        chatPollJob?.cancel()
        chatPollJob = null
    }

    private suspend fun pollVisibleChatOnce(force: Boolean = false) {
        val squadId =
            unreadTracker.squadChatTabVisibleCircleId?.trim()?.takeIf { it.isNotEmpty() }
        if (squadId != null) {
            if (!force && chatRealtimeConnected) return
            if (!force && !transcriptStore.shouldRefreshSquadFromNetwork(squadId)) return
            dataRepository.circleChatMessages(circleId = squadId, limit = 50).fold(
                onSuccess = { msgs ->
                    transcriptStore.reconcileSquadMessages(squadId, msgs)
                    mirrorSquadChatToOpenDetail(squadId)
                    unreadTracker.recomputeSquad(squadId, transcriptStore.squadMessages(squadId))
                },
                onFailure = { },
            )
            return
        }
        val conversationId =
            unreadTracker.directThreadVisibleConversationId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return
        if (!force && chatRealtimeConnected) return
        if (!force && !transcriptStore.shouldRefreshDirectFromNetwork(conversationId)) return
        dataRepository.directMessages(conversationId).fold(
            onSuccess = { msgs ->
                transcriptStore.reconcileDirectMessages(conversationId, msgs)
                mirrorDirectChatToOpenThread(conversationId)
                unreadTracker.recomputeDirect(conversationId, transcriptStore.directMessages(conversationId))
            },
            onFailure = { },
        )
    }

    private fun warmChatTranscriptsIfNeeded() {
        viewModelScope.launch {
            val unreadSquads =
                _state.value.unreadChatCountByCircleId.filterValues { count -> count > 0 }.keys
            for (circle in _state.value.circles) {
                val cid = circle.id.trim().takeIf { it.isNotEmpty() } ?: continue
                val empty = transcriptStore.squadMessages(cid).isEmpty()
                val unread = unreadSquads.any { ottoUserIdsEqual(it, cid) }
                if (empty || unread) {
                    dataRepository.circleChatMessages(circleId = cid, limit = 50).onSuccess { msgs ->
                        transcriptStore.reconcileSquadMessages(cid, msgs)
                    }
                }
            }
            dataRepository.directConversations().onSuccess { list ->
                val active = list.filter { it.hasActiveDirectThread() }
                for (conv in active) {
                    val id = conv.id.trim().takeIf { it.isNotEmpty() } ?: continue
                    val empty = transcriptStore.directMessages(id).isEmpty()
                    val unread = (_state.value.unreadDirectMessageCountByConversationId[id] ?: 0) > 0
                    if (empty || unread) {
                        dataRepository.directMessages(id).onSuccess { msgs ->
                            transcriptStore.reconcileDirectMessages(id, msgs)
                        }
                    }
                }
            }
        }
    }

    private fun syncRealtimeSubscriptions() {
        val circles = _state.value.circles.map { it.id }.filter { it.isNotBlank() }
        val dm = _state.value.directMessages
        val directIds =
            dm.selectedConversationId
                ?.trim()
                ?.takeIf { id -> id.isNotBlank() && dm.visible }
                ?.let { listOf(it) }
                ?: emptyList()
        realtime.syncCircleTargets(
            circleIds = circles,
            subscribePublicPresence = true,
            directConversationIds = directIds,
        )
    }

    private suspend fun maybeStartDriveIfNeeded(location: Pair<Double, Double>?) {
        val userId =
            sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (!_state.value.mapSharingLocation) return
        if (!_state.value.mapShareSaveDrive) return
        if (activeDriveId != null) return
        val circles = _state.value.circles
        if (circles.isEmpty()) return

        val startLoc =
            location?.let { (lat, lng) ->
                DriveLocationPointDto(lat = lat, lng = lng)
            }

        val firstId = circles.first().id
        val sharedIds = circles.map { it.id }
        val start =
            DriveStartDto(
                circleId = firstId,
                sharingAudience = "circles",
                sharedCircleIds = sharedIds,
                title = "Live drive session",
                startTime = Instant.now().toString(),
                startLocation = startLoc,
            )
        val drive =
            dataRepository.startDrivingSession(start).getOrNull() ?: return
        activeDriveId = drive.id
        syncLiveDriveRecordingActiveIntoState()
        driveDistanceMeters = 0.0
        driveMaxMph = 0.0
        lastDriveLat = null
        lastDriveLng = null
        lastDrivePointNetworkAtMs = 0L
        drivePathTrail.clear()
    }

    private fun recordLocalDrivePathSample(lat: Double, lng: Double, speedMph: Double) {
        val last = drivePathTrail.lastOrNull()
        if (last != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(last.lat, last.lng, lat, lng, dist)
            if (dist[0] < 18f) return
        }
        drivePathTrail.add(DrivePathSample(lat = lat, lng = lng, speedMph = speedMph))
        if (drivePathTrail.size > maxDrivePathTrailCount) {
            drivePathTrail.removeAt(0)
        }
    }

    private suspend fun appendDriveSampleIfPossible(
        fix: LocationFix?,
        movementMode: String,
    ) {
        if (!_state.value.mapShareSaveDrive) return
        val driveId = activeDriveId ?: return
        val f = fix ?: return
        val now = System.currentTimeMillis()
        if (now - lastDrivePointNetworkAtMs < 2_500L) return
        lastDrivePointNetworkAtMs = now

        val speedMph = ((f.speedMps ?: 0f).toDouble() * 2.23694)
        if (movementMode == "driving") {
            driveMaxMph = kotlin.math.max(driveMaxMph, speedMph)
        }

        val plat = lastDriveLat
        val plng = lastDriveLng
        if (plat != null && plng != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(plat, plng, f.latitude, f.longitude, dist)
            driveDistanceMeters += dist[0].toDouble()
        }
        lastDriveLat = f.latitude
        lastDriveLng = f.longitude
        recordLocalDrivePathSample(f.latitude, f.longitude, speedMph)

        dataRepository.appendDrivingPoints(
            driveId = driveId,
            points =
                listOf(
                    DrivePointCaptureDto(
                        lat = f.latitude,
                        lng = f.longitude,
                        speedMph = speedMph,
                        heading = f.bearingDegrees?.toDouble(),
                        accuracyMeters = f.accuracyMeters?.toDouble(),
                        capturedAt = Instant.now().toString(),
                    ),
                ),
        )
    }

    private suspend fun endDrivingSessionQuietly(locationEnd: DriveLocationPointDto?) {
        val driveId = activeDriveId ?: return
        val session = _state.value.activeDriveSession
        val shouldArchive = session?.isRecording == true || _state.value.mapShareSaveDrive
        val sessionDistance = session?.metrics?.distanceMeters ?: 0.0
        val sessionMaxSpeed = session?.metrics?.maxSpeedMph ?: 0.0
        var distanceMeters = maxOf(driveDistanceMeters, sessionDistance)
        if (distanceMeters <= 0.0) {
            distanceMeters = DriveSpeedGradient.polylineDistanceMeters(drivePathTrail)
        }
        val trailMaxSpeed = drivePathTrail.maxOfOrNull { it.speedMph } ?: 0.0
        val maxMph = maxOf(driveMaxMph, sessionMaxSpeed, trailMaxSpeed)
        val elapsedSeconds =
            session?.let { maxOf(0L, (System.currentTimeMillis() - it.startedAtMs) / 1000) } ?: 0L
        val avgMph =
            resolvedDriveAverageSpeedMph(
                storedAvgMph = session?.metrics?.avgSpeedMph,
                distanceMeters = distanceMeters,
                durationSeconds = elapsedSeconds,
            )
        val pathSnapshot =
            drivePathTrail.map {
                PendingDrivePathSampleDto(
                    lat = it.lat,
                    lng = it.lng,
                    speedMph = it.speedMph,
                    capturedAt = Instant.now().toString(),
                )
            }
        val startedAt =
            session?.startedAtMs?.let { Instant.ofEpochMilli(it) }
                ?: Instant.now().minusSeconds(elapsedSeconds.coerceAtLeast(1L))
        val sessionKind = session?.kind?.name?.lowercase() ?: "live"
        val archiveTitle = session?.routeName ?: driveRecordingTitle(session?.kind ?: DriveSessionKind.LIVE)

        activeDriveId = null
        syncLiveDriveRecordingActiveIntoState()

        val maxMphRounded = maxMph.takeIf { it > 0.0 }
        val payload =
            DriveEndDto(
                endTime = Instant.now().toString(),
                distanceMeters = distanceMeters.takeIf { it > 0.0 },
                maxSpeedMph = maxMphRounded,
                avgSpeedMph = avgMph.takeIf { it > 0.0 },
                endLocation = locationEnd,
            )

        suspend fun attemptEnd(): Boolean =
            dataRepository.endDrivingSession(driveId = driveId, payload = payload).isSuccess

        val success =
            when {
                attemptEnd() -> true
                attemptEnd() -> true
                else -> false
            }

        driveDistanceMeters = 0.0
        driveMaxMph = 0.0
        lastDriveLat = null
        lastDriveLng = null
        drivePathTrail.clear()

        if (!success && shouldArchive) {
            archivePendingDrive(
                failurePhase = "end",
                kind = sessionKind,
                title = archiveTitle,
                startedAt = startedAt,
                endedAt = Instant.now(),
                distanceMeters = distanceMeters,
                maxSpeedMph = maxMph,
                avgSpeedMph = avgMph,
                backendDriveId = driveId,
                pathSamples = pathSnapshot,
            )
        }

        if (!success) return

        val uid =
            sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return
        kotlinx.coroutines.coroutineScope {
            val refreshedDrivesDef = async { dataRepository.drives(uid) }
            refreshedDrivesDef.await().fold(
                onSuccess = { list -> _state.update { it.copy(drives = list) } },
                onFailure = { },
            )
        }
    }

    private suspend fun markPresenceInactiveSweep() {
        val circleIds = activeMapShareCircleIds()
        if (circleIds.isEmpty()) return
        for (circleId in circleIds) {
            val payloadInactive =
                PresenceUpdateDto(
                    circleId = circleId,
                    isActive = false,
                    inApp = true,
                    speedMph = 0.0,
                    movementMode = "unknown",
                    capturedAt = Instant.now().toString(),
                    trackDrivingStats = false,
                )
            dataRepository.pushPresence(payloadInactive)
        }
    }

    private suspend fun loadPresence(showStartedSharingFeedback: Boolean = false) {
        val snap = _state.value
        val targets =
            effectiveMapLayerCircleIds(
                circles = snap.circles,
                preferredScopeId = snap.mapPresenceCircleId,
                selectedLayerIds = snap.mapLayerSelectedCircleIds,
            )
        if (targets.isEmpty()) {
            _state.update {
                it.copy(
                    presenceMembers = emptyList(),
                    presenceMembersByCircleId = emptyMap(),
                    presenceError = null,
                )
            }
            return
        }
        coroutineScope {
            val targetsList = targets.toList()
            val pairs =
                targetsList
                    .map { id ->
                        async { id to dataRepository.presenceForCircle(id) }
                    }
                    .awaitAll()

            val byCircle = linkedMapOf<String, List<PresenceMemberDto>>()
            val memberLists = mutableListOf<List<PresenceMemberDto>>()
            var firstError: String? = null
            for ((id, res) in pairs) {
                res.fold(
                    onSuccess = { body ->
                        val members = body.members.orEmpty()
                        byCircle[id] = members
                        memberLists += members
                    },
                    onFailure = { e ->
                        if (firstError == null) {
                            firstError =
                                e.message?.takeIf { m -> m.isNotBlank() } ?: "Could not load live locations."
                        }
                    },
                )
            }
            val merged = mergePresenceLists(memberLists)
            maybePresentStartedSharingFeedback(
                byCircle = byCircle,
                showFeedback = showStartedSharingFeedback,
            )
            _state.update {
                it.copy(
                    presenceMembers = merged,
                    presenceMembersByCircleId = byCircle,
                    presenceError = if (merged.isEmpty()) firstError else null,
                )
            }
        }
    }

    private fun maybePresentStartedSharingFeedback(
        byCircle: Map<String, List<PresenceMemberDto>>,
        showFeedback: Boolean,
    ) {
        val selfId =
            sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() }
                ?: return
        val newlyActiveIds = linkedSetOf<String>()
        for ((circleId, members) in byCircle) {
            val currentActive =
                members
                    .asSequence()
                    .filter { member -> member.isActive && !ottoUserIdsEqual(member.userId, selfId) }
                    .map { it.userId }
                    .toSet()
            if (showFeedback) {
                val previousActive = lastKnownActiveSharersByCircleId[circleId].orEmpty()
                newlyActiveIds += currentActive - previousActive
            }
            lastKnownActiveSharersByCircleId[circleId] = currentActive
        }
        if (!showFeedback || newlyActiveIds.isEmpty()) return

        val now = System.currentTimeMillis()
        val dedupedNewlyActiveIds =
            newlyActiveIds.filter { userId ->
                val lastAt = lastSharingToastAtByUserId[userId] ?: 0L
                now - lastAt >= SharingToastDedupWindowMs
            }
        if (dedupedNewlyActiveIds.isEmpty()) return

        val contacts = _state.value.contacts
        fun displayName(userId: String): String =
            contacts
                .find { contact -> ottoUserIdsEqual(contact.id, userId) }
                ?.displayName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Someone"

        val firstId = dedupedNewlyActiveIds.first()
        val message =
            if (dedupedNewlyActiveIds.size == 1) {
                container.application.getString(
                    R.string.map_sharing_started_toast,
                    displayName(firstId),
                )
            } else {
                container.application.getString(
                    R.string.map_sharing_started_toast_plural,
                    displayName(firstId),
                    dedupedNewlyActiveIds.size - 1,
                )
            }
        presentUserToast(message)
        if (_state.value.soundEffectsEnabled) {
            OttoTabSoundPlayer.playUserSharing(container.application)
        }
        for (userId in dedupedNewlyActiveIds) {
            lastSharingToastAtByUserId[userId] = now
        }
    }

    fun refreshCoreFeedsAfterExternalInviteAccept() {
        viewModelScope.launch { loadCoreFeeds() }
    }

    private suspend fun loadCoreFeeds(updateGlobalRefreshingIndicator: Boolean = true) {
        coreFeedsMutex.withLock {
            try {
                val retryColdStart =
                    loadCoreFeedsInternal(updateGlobalRefreshingIndicator)
                if (retryColdStart) {
                    delay(500)
                    loadCoreFeedsInternal(updateGlobalRefreshingIndicator)
                }
            } finally {
                withContext(NonCancellable) {
                    _state.update { s ->
                        s.copy(
                            refreshing = if (updateGlobalRefreshingIndicator) false else s.refreshing,
                            coreFeedsLoadAttempted = true,
                        )
                    }
                }
            }
        }
    }

    /**
     * Resolves the signed-in user id from session storage, with short retries via `GET /me` when
     * the shell opens immediately after auth (DataStore / interceptor can lag behind navigation).
     */
    private suspend fun resolveAuthenticatedUserId(): String? {
        fun storedUserId(): String? =
            sessionRepository.authUserIdState.value
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        storedUserId()?.let { return it }

        repeat(3) { attempt ->
            if (attempt > 0) delay(250L * attempt)
            val fromMe =
                dataRepository.me().getOrNull()?.let { dto ->
                    val tid = dto.id.takeIf { it.isNotBlank() }
                    val token = sessionRepository.authTokenState.value
                    if (!tid.isNullOrEmpty() && !token.isNullOrBlank()) {
                        sessionRepository.setCredentials(token = token, userId = tid)
                    }
                    tid
                }
            if (!fromMe.isNullOrBlank()) return fromMe
        }
        return storedUserId()
    }

    /** @return true when a single automatic cold-start retry should run (squads fetch failed, list empty). */
    private suspend fun loadCoreFeedsInternal(updateGlobalRefreshingIndicator: Boolean = true): Boolean {
        val userId = resolveAuthenticatedUserId()

        if (userId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    refreshing = if (updateGlobalRefreshingIndicator) false else it.refreshing,
                    coreFeedsLoadAttempted = true,
                    squadsLoadFailed = true,
                    bannerError = "Couldn't resolve your account. Sign out and sign in again.",
                    savedPlaces = emptyList(),
                )
            }
            unreadTracker.clearAll()
            return false
        }

        unreadTracker.bind(userId)
        val uid = userId
        if (updateGlobalRefreshingIndicator) {
            _state.update { it.copy(refreshing = true, bannerError = null, squadsLoadFailed = false) }
        }

        return try {
            coroutineScope {
            val circlesDef = async { dataRepository.circles() }
            val garageDef = async { dataRepository.garage(uid) }
            val placesDef = async { dataRepository.savedPlacesMine() }
            val drivesDef = async { dataRepository.drives(uid) }
            val statsDef = async { dataRepository.drivingStats(uid) }
            val contactsDef = async { dataRepository.contacts() }
            val meDef = async { dataRepository.me() }
            val publicProfileDef = async { dataRepository.publicMemberProfile(uid) }

            val criticalErrs = mutableListOf<String>()
            fun recordLoadError(
                label: String,
                error: Throwable,
                critical: Boolean,
            ) {
                Log.w(TAG, "Failed loading $label", error)
                if (critical) {
                    criticalErrs += "Couldn't load $label."
                }
            }
            fun <T> Result<T>.orEmpty(
                fallback: T,
                label: String,
                critical: Boolean = false,
            ): T =
                getOrElse { e ->
                    recordLoadError(label, e, critical)
                    fallback
                }

            val circlesResult = circlesDef.await()
            val circles =
                circlesResult.getOrElse { e ->
                    recordLoadError("squads", e, critical = true)
                    emptyList()
                }
            val featuredEventsResult = dataRepository.featuredPublicEvents()
            val communityEventsResult = dataRepository.communityPublicEvents()
            val squadFeedResult =
                dataRepository.allSquadUpcomingEvents(circles.map { it.id.trim() }.filter { it.isNotBlank() })
            val squadGoingResult =
                dataRepository.squadGoingEvents(circles.map { it.id.trim() }.filter { it.isNotBlank() })

            val events =
                featuredEventsResult.getOrElse { e ->
                    recordLoadError("featured events", e, critical = false)
                    emptyList()
                }
            val communityEvents =
                communityEventsResult.getOrElse { e ->
                    recordLoadError("community events", e, critical = false)
                    emptyList()
                }
            val squadFeedEvents =
                squadFeedResult.getOrElse { e ->
                    recordLoadError("squad events", e, critical = false)
                    emptyList()
                }
            val squadGoingEvents =
                squadGoingResult.getOrElse { e ->
                    recordLoadError("my squad events", e, critical = false)
                    emptyList()
                }
            val garageCars = garageDef.await().orEmpty(emptyList(), "garage")
            val savedPlaces = placesDef.await().orEmpty(emptyList(), "saved places")
            val drives = drivesDef.await().orEmpty(emptyList(), "recent drives")
            val meResult = meDef.await()
            val me = meResult.getOrNull()
            me?.timeZone?.let { TimeZoneSync.primeCacheFromServerTimeZone(container.application, it) }
            meResult.exceptionOrNull()?.let {
                recordLoadError("profile", it, critical = true)
            }
            val routesResult =
                if (me?.canAccessRoutes() == true) {
                    dataRepository.fetchRoutes()
                } else {
                    Result.success(emptyList())
                }
            val routes =
                routesResult.getOrNull()
                    ?.filter { ottoUserIdsEqual(it.createdByUserId, uid) }
                    .orEmpty()
            routesResult.exceptionOrNull()?.let {
                recordLoadError("routes", it, critical = false)
            }
            val statsResult = statsDef.await()
            val statsOptional = statsResult.getOrNull()
            statsResult.exceptionOrNull()?.let {
                recordLoadError("driving stats", it, critical = false)
            }
            val contacts = contactsDef.await().orEmpty(emptyList(), "contacts")
            val publicProfileResult = publicProfileDef.await()
            val profileGoingEvents = publicProfileResult.getOrNull()?.publicGoingEvents.orEmpty()
            publicProfileResult.exceptionOrNull()?.let {
                recordLoadError("public profile", it, critical = false)
            }

            val banner =
                criticalErrs.take(2).joinToString("\n").takeIf { s -> s.isNotEmpty() }

            if (
                banner != null &&
                circles.isEmpty() &&
                circlesResult.isFailure &&
                !coldStartCoreFeedsRetried
            ) {
                coldStartCoreFeedsRetried = true
                return@coroutineScope true
            }

            _state.update {
                val syncedCircleDetail =
                    it.circleDetailUi?.let { cd ->
                        circles.find { c -> ottoUserIdsEqual(c.id, cd.circleId) }?.let { fresh ->
                            cd.copy(circle = fresh)
                        } ?: cd
                    }
                val nextMapScope = coerceMapPresenceCircleId(circles, it.mapPresenceCircleId)
                val nextLayerIds =
                    mergedMapLayerCircleIds(
                        previousCircles = it.circles,
                        nextCircles = circles,
                        rawLayers = it.mapLayerSelectedCircleIds,
                    )
                it.copy(
                    refreshing = if (updateGlobalRefreshingIndicator) false else it.refreshing,
                    coreFeedsLoadAttempted = true,
                    squadsLoadFailed = circlesResult.isFailure && circles.isEmpty(),
                    bannerError = banner,
                    circles = circles,
                    mapPresenceCircleId = nextMapScope,
                    mapLayerSelectedCircleIds = nextLayerIds,
                    events = events,
                    communityEvents = communityEvents,
                    squadFeedEvents = squadFeedEvents,
                    squadGoingEvents = squadGoingEvents,
                    garageCars = garageCars,
                    selectedSharingCarId = reconcileSelectedSharingCarId(garageCars, it.selectedSharingCarId),
                    savedPlaces = savedPlaces,
                    drives = drives,
                    routes = routes,
                    stats = statsOptional,
                    contacts = contacts,
                    me = me ?: it.me,
                    showsDriveCarPicker = showsDriveCarPickerFor((me ?: it.me)?.phoneNumber),
                    profilePublicGoingEvents = profileGoingEvents,
                    circleDetailUi = syncedCircleDetail,
                )
            }
            reconcileDriveCarPickerForUser(_state.value.me?.phoneNumber)
            syncRealtimeSubscriptions()
            loadPendingInvites()
            maybePostDailyLaunch()
            reconcileInAppPresenceHeartbeat()
            val detailCircleId =
                _state.value.circleDetailUi?.circleId?.trim()?.takeIf { it.isNotBlank() }
            if (detailCircleId != null) {
                reloadSquadScopedEventsForCircle(detailCircleId)
            }
            reconcileChatUnreadStateFromNetworkIfNeeded()
            warmChatTranscriptsIfNeeded()
            false
        }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "loadCoreFeedsInternal failed unexpectedly", e)
            _state.update { s ->
                s.copy(
                    refreshing = if (updateGlobalRefreshingIndicator) false else s.refreshing,
                    coreFeedsLoadAttempted = true,
                    squadsLoadFailed = s.circles.isEmpty(),
                    bannerError = s.bannerError ?: "Couldn't load squads.",
                )
            }
            false
        }
    }

    private fun maybePostDailyLaunch() {
        if (dailyLaunchPosted) return
        dailyLaunchPosted = true
        viewModelScope.launch {
            dataRepository.recordProgressionDailyLaunch().onSuccess { response ->
                response.levelUp?.let { presentProfileLevelUp(it) }
            }
        }
    }

    fun presentProfileLevelUp(levelUp: ProfileLevelUpDto) {
        val progression = levelUp.progression
        _state.update { st ->
            val nextStats =
                if (progression != null && st.stats != null) {
                    st.stats.copy(progression = progression)
                } else {
                    st.stats
                }
            st.copy(activeProfileLevelUp = levelUp, stats = nextStats)
        }
        OttoTabSoundPlayer.playLevelUp(container.application)
    }

    fun dismissProfileLevelUp() {
        _state.update { it.copy(activeProfileLevelUp = null) }
    }

    fun previewProfileLevelUpModal(level: Int) {
        presentProfileLevelUp(ProfileProgressionPreview.previewProfileLevelUp(level))
    }

    fun schedulePreviewProfileLevelUpNotification(level: Int = 17) {
        ProfileProgressionPreview.scheduleLevelUpNotification(container.application, level)
    }

    /**
     * Map layers default to all squads; when membership grows, new squad ids are merged in.
     * If saved layers don't intersect the current roster (e.g. account switch), fall back to all valid squads.
     */
    private fun mergedMapLayerCircleIds(
        previousCircles: List<CircleDto>,
        nextCircles: List<CircleDto>,
        rawLayers: Set<String>,
    ): Set<String> {
        val validCircleIds = nextCircles.map { c -> c.id }.filter { id -> id.isNotBlank() }.toSet()
        val previousCircleIds =
            previousCircles.map { c -> c.id }.filter { id -> id.isNotBlank() }.toSet()
        val addedSquads = validCircleIds - previousCircleIds
        return when {
            validCircleIds.isEmpty() -> emptySet()
            rawLayers.isEmpty() -> validCircleIds
            else -> {
                val merged =
                    rawLayers.intersect(validCircleIds).toMutableSet().apply {
                        addAll(addedSquads)
                    }
                if (merged.isEmpty()) validCircleIds else merged
            }
        }
    }

    /** Keep map presence scope valid; preserve [OttoShellUiState.PublicPresenceChannelId] when selected. */
    private fun coerceMapPresenceCircleId(
        circles: List<CircleDto>,
        preferred: String,
    ): String {
        val t = preferred.trim()
        if (t == OttoShellUiState.PublicPresenceChannelId) {
            return OttoShellUiState.PublicPresenceChannelId
        }
        val valid = circles.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (t.isNotEmpty() && t in valid) {
            return t
        }
        return circles.firstOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
    }

    private fun mergeSquadGoingEvents(
        existing: List<EventDto>,
        updated: EventDto,
    ): List<EventDto> {
        if (updated.visibility != "circle") {
            return existing
        }
        val stillActive =
            eventCheckInEndsAtInstant(updated)?.isBefore(Instant.now()) != true
        if (updated.currentUserRsvp == OttoShellUiState.RsvpGoing && stillActive) {
            val next = existing.toMutableList()
            val idx = next.indexOfFirst { it.id == updated.id }
            if (idx >= 0) {
                next[idx] = updated
            } else {
                next.add(updated)
            }
            return next.sortedWith(::compareEventsForMainList)
        }
        return existing.filterNot { it.id == updated.id }
    }

    private fun startCoordinateForSavedRoute(route: SavedRouteDto): Pair<Double, Double>? {
        route.points.orEmpty()
            .firstOrNull { it.markerType == "start" && it.lat.isFinite() && it.lng.isFinite() }
            ?.let { return it.lat to it.lng }
        route.roadCoordinates.orEmpty()
            .firstOrNull { it.lat.isFinite() && it.lng.isFinite() }
            ?.let { return it.lat to it.lng }
        route.points.orEmpty()
            .firstOrNull { it.lat.isFinite() && it.lng.isFinite() }
            ?.let { return it.lat to it.lng }
        return null
    }

    // MARK: - Drive session lifecycle (iOS DriveSessionCoordinator parity)

    fun driveSessionPillPresentation(
        nowMs: Long = System.currentTimeMillis(),
        routeName: String? = null,
        viewerCount: Int? = null,
    ): DriveSessionPillPresentation {
        val snap = _state.value
        val session = snap.activeDriveSession
        val sharingActive =
            snap.mapSharingLocation && isSharingSessionActive(nowMs)
        val sharingPaused =
            sharingActive &&
                snap.mapShareWhileDrivingOnly &&
                snap.deviceMovementMode != "driving"
        val recording =
            session?.isRecording == true ||
                (sharingActive && snap.mapShareSaveDrive && activeDriveId != null) ||
                snap.mapRouteSessionActive
        val routeActive = snap.mapRouteSessionActive || session?.kind == DriveSessionKind.ROUTE

        val remaining = sharingRemainingText(nowMs)
        val squad =
            snap.circles.find { it.id == snap.mapPresenceCircleId }?.name
                ?: snap.mapPresenceCircleId.ifBlank { "Squad" }

        if (!sharingActive && session == null && !routeActive) {
            return DriveSessionPillPresentation.Idle
        }
        if (sharingPaused && !recording && !routeActive) {
            return DriveSessionPillPresentation.PausedSharing
        }

        val metrics = session?.metrics ?: localDriveSessionMetricsFallback()
        val startMs = session?.startedAtMs ?: mapShareSessionStartedAtMs ?: nowMs
        val timeText = formatDriveSessionDuration(fromMs = startMs, nowMs = nowMs)
        val distanceText =
            formatDriveSessionDistance(
                if (metrics.distanceMeters > 0) metrics.distanceMeters else driveDistanceMeters,
            )

        if (routeActive) {
            val name = routeName ?: session?.routeName
            if (name != null) {
                val completed = session?.routeProgress?.completedCount ?: 0
                val total = maxOf(session?.routeProgress?.totalCheckpoints ?: completed, 1)
                if (sharingActive && recording) {
                    return DriveSessionPillPresentation.RecordingAndSharing(
                        timeText = timeText,
                        distanceText = distanceText,
                        squadSummary = squad,
                        viewerCount = viewerCount,
                        remainingText = remaining,
                    )
                }
                if (sharingActive) {
                    if (sharingPaused) return DriveSessionPillPresentation.PausedSharing
                    return DriveSessionPillPresentation.Sharing(
                        squadSummary = squad,
                        viewerCount = viewerCount,
                        remainingText = remaining,
                    )
                }
                return DriveSessionPillPresentation.Route(
                    name = name,
                    completed = completed,
                    total = total,
                )
            }
        }

        if (sharingActive && recording) {
            return DriveSessionPillPresentation.RecordingAndSharing(
                timeText = timeText,
                distanceText = distanceText,
                squadSummary = squad,
                viewerCount = viewerCount,
                remainingText = remaining,
            )
        }
        if (sharingActive) {
            if (sharingPaused) return DriveSessionPillPresentation.PausedSharing
            return DriveSessionPillPresentation.Sharing(
                squadSummary = squad,
                viewerCount = viewerCount,
                remainingText = remaining,
            )
        }
        if (recording || session != null) {
            return DriveSessionPillPresentation.Recording(
                timeText = timeText,
                distanceText = distanceText,
            )
        }
        return DriveSessionPillPresentation.Idle
    }

    private fun localDriveSessionMetricsFallback(): DriveSessionMetrics =
        DriveSessionMetrics(
            distanceMeters = driveDistanceMeters,
            maxSpeedMph = driveMaxMph,
        )

    private fun sharingRemainingSeconds(nowMs: Long): Long? {
        if (!_state.value.mapSharingLocation) return null
        if (sharingTiedToActiveDrive) return null
        val durationMin = _state.value.mapShareDurationMinutes ?: return null
        if (durationMin <= 0) return null
        val started = mapShareSessionStartedAtMs ?: return null
        val elapsedSec = (nowMs - started).coerceAtLeast(0L) / 1000L
        val totalSec = durationMin * 60L
        val remaining = totalSec - elapsedSec
        return if (remaining > 0) remaining else null
    }

    private fun isSharingSessionActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!_state.value.mapSharingLocation) return false
        if (sharingTiedToActiveDrive) return true
        return sharingRemainingSeconds(nowMs) != null
    }

    fun sharingRemainingText(nowMs: Long = System.currentTimeMillis()): String? {
        if (sharingTiedToActiveDrive) return null
        val remaining = sharingRemainingSeconds(nowMs) ?: return null
        val minutes = kotlin.math.ceil(remaining / 60.0).toInt()
        if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            return if (mins > 0) "${hours}h ${mins}m left" else "${hours}h left"
        }
        return "${maxOf(1, minutes)}m left"
    }

    fun formatDriveSessionDuration(fromMs: Long, nowMs: Long): String {
        val elapsed = maxOf(0, ((nowMs - fromMs) / 1000L).toInt())
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatDriveSessionDistance(meters: Double): String {
        val miles = meters / 1609.344
        if (miles < 0.1) return "0.0 mi"
        return String.format("%.1f mi", miles)
    }

    fun formatDriveSessionTopSpeed(mph: Double): String =
        if (mph > 0) "${mph.roundToInt()} mph" else "—"

    fun startQuickDrive(
        saveToProfile: Boolean? = null,
        shareLive: Boolean = false,
        sharingCircleIds: Set<String> = emptySet(),
    ): Boolean {
        if (_state.value.activeDriveSession != null) return false
        val record = saveToProfile ?: _state.value.recordDriveOnStartEnabled
        val resolvedCircleIds =
            sharingCircleIds
                .mapNotNull { it.trim().takeIf { id -> id.isNotBlank() } }
                .toSet()
        if (shareLive && resolvedCircleIds.isEmpty()) return false
        resetSessionMetricTracking()
        _state.update {
            it.copy(
                activeDriveSession =
                    DriveSessionState.quick(
                        saveToProfile = record,
                        shareLive = shareLive,
                        sharingCircleIds = resolvedCircleIds,
                    ),
                mapPresenceCircleId =
                    if (shareLive && resolvedCircleIds.isNotEmpty()) {
                        resolvedCircleIds.first()
                    } else {
                        it.mapPresenceCircleId
                    },
            )
        }
        if (shareLive) {
            if (!startSharingForDriveStart(resolvedCircleIds)) {
                _state.update { it.copy(activeDriveSession = null) }
                return false
            }
        }
        OttoTabSoundPlayer.playStartDrive(container.application)
        if (record) {
            viewModelScope.launch {
                startDriveRecordingIfNeeded(
                    location = approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                        it.latitude to it.longitude
                    },
                    title = "Quick Drive",
                )
            }
        }
        reconcileDriveSessionSampleJob()
        reconcileActiveDriveLocationService()
        return true
    }

    fun ensureLiveDriveSession(saveToProfile: Boolean) {
        val snap = _state.value
        if (snap.activeDriveSession != null) {
            _state.update { st ->
                val session = st.activeDriveSession ?: return@update st
                st.copy(
                    activeDriveSession =
                        session.copy(
                            isSharing = true,
                            isRecording = saveToProfile || session.isRecording,
                        ),
                )
            }
            reconcileDriveSessionSampleJob()
            return
        }
        val circleIds =
            snap.circles.mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotBlank() } }.toSet()
        resetSessionMetricTracking()
        _state.update {
            it.copy(
                activeDriveSession =
                    DriveSessionState(
                        id = UUID.randomUUID().toString(),
                        kind = DriveSessionKind.LIVE,
                        isRecording = saveToProfile,
                        isSharing = true,
                        sharingCircleIds = circleIds,
                        startedAtMs = mapShareSessionStartedAtMs ?: System.currentTimeMillis(),
                        backendDriveId = activeDriveId,
                    ),
            )
        }
        if (saveToProfile) {
            viewModelScope.launch {
                startDriveRecordingIfNeeded(
                    location = approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                        it.latitude to it.longitude
                    },
                    title = "Live Drive Session",
                )
            }
        }
        reconcileDriveSessionSampleJob()
    }

    fun stopLiveSharingOnly() {
        drivingOnlyPauseInactiveSent = false
        sharingTiedToActiveDrive = false
        mapShareJob?.cancel()
        mapShareExpiryJob?.cancel()
        container.activityRecognitionPresenceSupport.stop()
        previousLocalMovementMode = null
        mapShareSessionStartedAtMs = null
        _state.update { st ->
            st.copy(
                mapSharingLocation = false,
                deviceMovementMode = null,
                activeDriveSession = st.activeDriveSession?.copy(isSharing = false),
            )
        }
        viewModelScope.launch {
            markPresenceInactiveSweep()
            syncRealtimeSubscriptions()
            reconcileDriveSessionSampleJob()
        }
        reconcileInAppPresenceHeartbeat()
        if (mapForegroundLocationActive) {
            setMapForegroundLocationActive(true)
        }
    }

    fun stopDriveSession() {
        viewModelScope.launch {
            val session = _state.value.activeDriveSession ?: run {
                resetSessionMetricTracking()
                _state.update {
                    it.copy(
                        activeDriveSession = null,
                        mapSharingLocation = false,
                        deviceMovementMode = null,
                    )
                }
                reconcileDriveSessionSampleJob()
                reconcileInAppPresenceHeartbeat()
                reconcileActiveDriveLocationService()
                if (mapForegroundLocationActive) {
                    setMapForegroundLocationActive(true)
                }
                return@launch
            }

            val wasSharing = _state.value.mapSharingLocation
            val trailSnapshot = ArrayList(drivePathTrail)
            val endLoc = cachedDriveEndLocation(trailSnapshot)
            var driveId =
                session.backendDriveId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: activeDriveId?.trim()?.takeIf { it.isNotEmpty() }

            val sessionMetrics = session.metrics
            var distance = maxOf(driveDistanceMeters, sessionMetrics.distanceMeters)
            if (distance <= 0.0) {
                distance = DriveSpeedGradient.polylineDistanceMeters(trailSnapshot)
            }
            val trailMaxSpeed = trailSnapshot.maxOfOrNull { it.speedMph } ?: 0.0
            val maxSpeed = maxOf(driveMaxMph, sessionMetrics.maxSpeedMph, trailMaxSpeed)
            val elapsedSeconds = maxOf(0L, (System.currentTimeMillis() - session.startedAtMs) / 1000)
            val averageSpeed =
                resolvedDriveAverageSpeedMph(
                    storedAvgMph = sessionMetrics.avgSpeedMph,
                    distanceMeters = distance,
                    durationSeconds = elapsedSeconds,
                )
            val routeCoordinates =
                trailSnapshot.map { LatLngPair(lat = it.lat, lng = it.lng) }

            val payload =
                DriveSessionCompletionPayload(
                    driveId = driveId,
                    kind = session.kind,
                    routeName = session.routeName ?: driveRecordingTitle(session.kind),
                    routeCoordinates = routeCoordinates,
                    checkpointCoordinates = emptyList(),
                    distanceMeters = distance,
                    driveTimeSeconds = elapsedSeconds,
                    averageSpeedMph = averageSpeed,
                    maxSpeedMph = maxSpeed,
                    completedCheckpoints = session.routeProgress?.completedCount ?: 0,
                    totalCheckpoints = session.routeProgress?.totalCheckpoints ?: 0,
                    completionReason = "stopped",
                )
            val summary = payload.toSummary(trailSnapshot)
            val showCompletionModal = session.kind == DriveSessionKind.QUICK

            if (wasSharing) {
                drivingOnlyPauseInactiveSent = false
                sharingTiedToActiveDrive = false
                mapShareJob?.cancel()
                mapShareExpiryJob?.cancel()
                container.activityRecognitionPresenceSupport.stop()
                previousLocalMovementMode = null
                mapShareSessionStartedAtMs = null
                syncRealtimeSubscriptions()
            }

            OttoTabSoundPlayer.playRouteFinished(container.application)
            OttoAnalytics.logDriveCompletedFromSession(session.kind, distance)
            resetSessionMetricTracking()
            _state.update {
                it.copy(
                    activeDriveSession = null,
                    mapSharingLocation = false,
                    deviceMovementMode = null,
                    driveCompleteSummary = if (showCompletionModal) summary else null,
                )
            }
            reconcileDriveSessionSampleJob()
            reconcileInAppPresenceHeartbeat()
            reconcileActiveDriveLocationService()
            if (mapForegroundLocationActive) {
                setMapForegroundLocationActive(true)
            }

            launch {
                if (wasSharing) {
                    markPresenceInactiveSweep()
                }
                if (session.isRecording && activeDriveId == null) {
                    val backendId = session.backendDriveId?.trim()?.takeIf { it.isNotEmpty() }
                    if (backendId != null) {
                        activeDriveId = backendId
                        driveId = backendId
                    } else {
                        startDriveRecordingIfNeeded(
                            location = endLoc?.let { it.lat to it.lng },
                            title = driveRecordingTitle(session.kind),
                        )
                        driveId = activeDriveId?.trim()?.takeIf { it.isNotEmpty() } ?: driveId
                    }
                }
                if (activeDriveId != null) {
                    driveId = activeDriveId?.trim()?.takeIf { it.isNotEmpty() } ?: driveId
                    endDrivingSessionQuietly(locationEnd = endLoc)
                }
                if (showCompletionModal) {
                    val resolvedDriveId = driveId?.trim()?.takeIf { it.isNotEmpty() }
                    if (!resolvedDriveId.isNullOrEmpty()) {
                        _state.update { st ->
                            val current = st.driveCompleteSummary ?: return@update st
                            if (current.driveId == resolvedDriveId) {
                                st
                            } else {
                                st.copy(driveCompleteSummary = current.copy(driveId = resolvedDriveId))
                            }
                        }
                    }
                    sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() }?.let { uid ->
                        dataRepository.drives(uid).onSuccess { list ->
                            _state.update { state -> state.copy(drives = list) }
                        }
                    }
                }
            }
        }
    }

    private fun cachedDriveEndLocation(trail: List<DrivePathSample>): DriveLocationPointDto? {
        _state.value.deviceLocationFix?.let { fix ->
            return DriveLocationPointDto(lat = fix.latitude, lng = fix.longitude)
        }
        trail.lastOrNull()?.let { sample ->
            return DriveLocationPointDto(lat = sample.lat, lng = sample.lng)
        }
        return null
    }

    fun dismissDriveComplete() {
        _state.update { it.copy(driveCompleteSummary = null) }
    }

    /** Route-drive checkpoint feedback — matches iOS MapScreen route session updates. */
    fun onRouteDriveCheckpointReached(isFinalWaypoint: Boolean) {
        if (isFinalWaypoint) return
        OttoTabSoundPlayer.playCheckpointComplete(container.application)
    }

    suspend fun viewDriveSummaryFromComplete(): Boolean {
        val summary = _state.value.driveCompleteSummary ?: return false
        val driveId = summary.driveId?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            dismissDriveComplete()
            return false
        }
        dismissDriveComplete()
        return dataRepository.fetchDrive(driveId, circleId = null).fold(
            onSuccess = { drive ->
                openDriveSummarySheet(drive, isOwner = true)
                true
            },
            onFailure = { false },
        )
    }

    fun setDriveSessionSaveEnabled(enabled: Boolean) {
        val session = _state.value.activeDriveSession
        if (session != null) {
            _state.update { st ->
                st.copy(activeDriveSession = session.copy(isRecording = enabled))
            }
            viewModelScope.launch {
                if (enabled) {
                    startDriveRecordingIfNeeded(
                        location = approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                            it.latitude to it.longitude
                        },
                        title = driveRecordingTitle(session.kind),
                    )
                } else {
                    endDrivingSessionQuietly(
                        locationEnd = approximateLocationReader.currentFixHighAccuracyOrNull()?.let {
                            DriveLocationPointDto(lat = it.latitude, lng = it.longitude)
                        },
                    )
                }
                reconcileDriveSessionSampleJob()
            }
            return
        }
        setMapShareSaveDrive(enabled)
    }

    private fun driveRecordingTitle(kind: DriveSessionKind): String =
        when (kind) {
            DriveSessionKind.QUICK -> "Quick Drive"
            DriveSessionKind.ROUTE -> "Route Drive"
            DriveSessionKind.LIVE -> "Live Drive Session"
        }

    private fun resetSessionMetricTracking() {
        lastSessionMetricLat = null
        lastSessionMetricLng = null
    }

    private fun reconcileDriveSessionSampleJob() {
        val session = _state.value.activeDriveSession
        val recordingOnly =
            session?.isRecording == true && !_state.value.mapSharingLocation
        if (!recordingOnly) {
            driveSessionSampleJob?.cancel()
            driveSessionSampleJob = null
            return
        }
        if (driveSessionSampleJob?.isActive == true) return
        driveSessionSampleJob =
            viewModelScope.launch {
                while (isActive &&
                    _state.value.activeDriveSession?.isRecording == true &&
                    !_state.value.mapSharingLocation
                ) {
                    val fix = approximateLocationReader.currentFixHighAccuracyOrNull()
                    val speedMps = (fix?.speedMps ?: 0f).toDouble().coerceAtLeast(0.0)
                    val movementMode = resolveAndStoreLocalMovement(speedMps)
                    ingestDriveSessionSample(fix, movementMode)
                    delay(5_000L)
                }
            }
    }

    private suspend fun ingestDriveSessionSample(
        fix: LocationFix?,
        movementMode: String,
    ) {
        val f = fix ?: return
        val speedMps = (f.speedMps ?: 0f).toDouble().coerceAtLeast(0.0)
        val speedMph = speedMps * 2.23694
        updateActiveDriveSessionMetrics(f, speedMph, movementMode)

        if (_state.value.mapSharingLocation) {
            val loc =
                f.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }?.let {
                    it.latitude to it.longitude
                }
            maybeStartDriveIfNeeded(loc)
            appendDriveSampleIfPossible(f, movementMode)
        } else if (_state.value.activeDriveSession?.isRecording == true) {
            appendDriveSampleForSession(f, movementMode)
        }
    }

    private fun updateActiveDriveSessionMetrics(
        fix: LocationFix,
        speedMph: Double,
        movementMode: String,
    ) {
        _state.update { st ->
            val session = st.activeDriveSession ?: return@update st
            var metrics = session.metrics
            if (movementMode == "driving") {
                metrics =
                    metrics.copy(
                        maxSpeedMph = maxOf(metrics.maxSpeedMph, speedMph),
                        speedSampleCount = metrics.speedSampleCount + 1,
                        speedSumMph = metrics.speedSumMph + speedMph,
                    )
            }
            val lastLat = lastSessionMetricLat
            val lastLng = lastSessionMetricLng
            if (lastLat != null && lastLng != null) {
                val dist = FloatArray(1)
                Location.distanceBetween(lastLat, lastLng, fix.latitude, fix.longitude, dist)
                if (dist[0] >= 18f) {
                    metrics = metrics.copy(distanceMeters = metrics.distanceMeters + dist[0].toDouble())
                }
            }
            lastSessionMetricLat = fix.latitude
            lastSessionMetricLng = fix.longitude
            st.copy(activeDriveSession = session.copy(metrics = metrics))
        }
    }

    private suspend fun startDriveRecordingIfNeeded(
        location: Pair<Double, Double>?,
        title: String,
    ) {
        if (activeDriveId != null) return
        val userId =
            sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val circles = _state.value.circles

        val startLoc =
            location?.let { (lat, lng) ->
                DriveLocationPointDto(lat = lat, lng = lng)
            }
        val sharedIds = circles.mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotBlank() } }
        val driveCircleId =
            _state.value.mapPresenceCircleId.trim().takeIf {
                it.isNotEmpty() && it != OttoShellUiState.PublicPresenceChannelId
            } ?: circles.firstOrNull()?.id?.trim()?.takeIf { it.isNotBlank() }

        val start =
            DriveStartDto(
                circleId = driveCircleId,
                sharingAudience = "onlyMe",
                sharedCircleIds = sharedIds,
                title = title,
                startTime = Instant.now().toString(),
                startLocation = startLoc,
            )
        val drive = dataRepository.startDrivingSession(start).getOrNull() ?: return
        activeDriveId = drive.id
        val sessionKind = _state.value.activeDriveSession?.kind ?: DriveSessionKind.QUICK
        val routeId =
            _state.value.activeDriveSession?.routeId?.trim()?.takeIf { it.isNotEmpty() }
                ?: _state.value.activeDriveSession?.routeProgress?.routeId?.trim()?.takeIf { it.isNotEmpty() }
        OttoAnalytics.logDriveStartedFromSession(sessionKind, routeId)
        syncLiveDriveRecordingActiveIntoState()
        driveDistanceMeters = 0.0
        driveMaxMph = 0.0
        lastDriveLat = null
        lastDriveLng = null
        lastDrivePointNetworkAtMs = 0L
        drivePathTrail.clear()
        _state.update { st ->
            val session = st.activeDriveSession ?: return@update st
            st.copy(activeDriveSession = session.copy(backendDriveId = drive.id))
        }
        val uid = userId
        viewModelScope.launch {
            dataRepository.drives(uid).fold(
                onSuccess = { list -> _state.update { it.copy(drives = list) } },
                onFailure = { },
            )
        }
    }

    private suspend fun appendDriveSampleForSession(
        fix: LocationFix,
        movementMode: String,
    ) {
        if (_state.value.activeDriveSession?.isRecording != true) return
        if (activeDriveId == null) {
            startDriveRecordingIfNeeded(
                location = fix.latitude to fix.longitude,
                title =
                    driveRecordingTitle(
                        _state.value.activeDriveSession?.kind ?: DriveSessionKind.QUICK,
                    ),
            )
        }
        if (activeDriveId == null) return
        val now = System.currentTimeMillis()
        if (now - lastDrivePointNetworkAtMs < 2_500L) return
        lastDrivePointNetworkAtMs = now

        val speedMph = ((fix.speedMps ?: 0f).toDouble() * 2.23694)
        if (movementMode == "driving") {
            driveMaxMph = maxOf(driveMaxMph, speedMph)
        }
        val plat = lastDriveLat
        val plng = lastDriveLng
        if (plat != null && plng != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(plat, plng, fix.latitude, fix.longitude, dist)
            driveDistanceMeters += dist[0].toDouble()
        }
        lastDriveLat = fix.latitude
        lastDriveLng = fix.longitude
        recordLocalDrivePathSample(fix.latitude, fix.longitude, speedMph)

        val driveId = activeDriveId ?: return
        dataRepository.appendDrivingPoints(
            driveId = driveId,
            points =
                listOf(
                    DrivePointCaptureDto(
                        lat = fix.latitude,
                        lng = fix.longitude,
                        speedMph = speedMph,
                        heading = fix.bearingDegrees?.toDouble(),
                        accuracyMeters = fix.accuracyMeters?.toDouble(),
                        capturedAt = Instant.now().toString(),
                    ),
                ),
        )
    }
}
