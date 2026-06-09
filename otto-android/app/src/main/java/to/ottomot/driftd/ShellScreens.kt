@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package to.ottomot.driftd

import android.location.Location
import android.os.Build
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import sh.calvin.reorderable.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.CameraState
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.removeOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.removeOnMoveListener
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.core.data.PhotoUploadClientTargetMaxBytes
import to.ottomot.driftd.core.race.RaceTrackRecord
import to.ottomot.driftd.core.session.circlesSortedByRecentAccess
import to.ottomot.driftd.core.race.coordinateOrNull
import to.ottomot.driftd.core.event.eventHasVenueCoordinates
import to.ottomot.driftd.core.event.isEligibleForMapDisplay
import to.ottomot.driftd.core.event.parseEventInstant
import to.ottomot.driftd.core.event.eventVenueLatLng
import to.ottomot.driftd.core.location.LocationFix
import to.ottomot.driftd.core.location.MovementModeIosParity
import to.ottomot.driftd.core.event.isWithinEventCheckInWindow
import to.ottomot.driftd.core.event.EVENT_CHECK_IN_RADIUS_METERS
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.event.EventListSectionedPresentation
import to.ottomot.driftd.core.event.compareEventsForMainList
import to.ottomot.driftd.core.event.sortEventsForSectionedList
import to.ottomot.driftd.core.event.eventStartsAtSortKey
import to.ottomot.driftd.core.event.squadChatPinnedEventHighlightPhrase
import to.ottomot.driftd.core.chat.SquadChatAllMention
import to.ottomot.driftd.map.effectiveMapLayerCircleIds
import to.ottomot.driftd.map.eventProximityGroupsForMap
import to.ottomot.driftd.map.normalizePresenceMovementMode
import to.ottomot.driftd.map.PRESENCE_WORLD_VIEW_LAT
import to.ottomot.driftd.map.PRESENCE_WORLD_VIEW_LNG
import to.ottomot.driftd.map.PRESENCE_WORLD_VIEW_ZOOM
import to.ottomot.driftd.map.presenceRequiresWorldView
import to.ottomot.driftd.map.TravelSurface
import to.ottomot.driftd.map.MapTravelSurfaceSampler
import to.ottomot.driftd.map.TravelSurfaceTracker
import to.ottomot.driftd.map.sampleTravelSurface
import to.ottomot.driftd.map.MapDiscoveryMarkerKind
import to.ottomot.driftd.map.MapDiscoveryMarkerLOD
import to.ottomot.driftd.map.MapDiscoveryMarkerLODView
import to.ottomot.driftd.map.OttoMapEventBeaconMarkerContent
import to.ottomot.driftd.map.OttoMapRaceTrackMarkerContent
import to.ottomot.driftd.map.OttoMapSavedPlaceMarkerContent
import to.ottomot.driftd.map.visibleLatitudeDeltaDegrees
import to.ottomot.driftd.core.location.OttoLocationPermissions
import to.ottomot.driftd.core.permissions.activityRecognitionGranted
import to.ottomot.driftd.core.permissions.canRepromptFineLocationAtRuntime
import to.ottomot.driftd.core.permissions.fineLocationGranted
import to.ottomot.driftd.core.permissions.shouldOpenFineLocationAppSettings
import to.ottomot.driftd.core.permissions.shouldRequestActivityRecognition
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.ui.squad.SquadInviteHaptics
import to.ottomot.driftd.ui.squad.inviteNameSearchFromContacts
import to.ottomot.driftd.ui.squad.isPhonePrimarySquadInviteQuery
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.DrivingStatsDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.core.network.dto.GarageCarDto
import to.ottomot.driftd.core.network.dto.InviteLinkResolveDto
import to.ottomot.driftd.core.network.dto.MyPendingCircleInvite
import to.ottomot.driftd.core.network.dto.NextUpEventDismissalDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.SavedPlaceDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.network.dto.DriveStatsVisibilitySetting
import to.ottomot.driftd.core.network.dto.resolvedDriveStatsVisibility
import to.ottomot.driftd.core.network.dto.canAccessRoutes
import java.time.Duration
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import to.ottomot.driftd.core.network.dto.CircleChatDriveAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatPlaceAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatEventAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatLinkPreviewDto
import to.ottomot.driftd.core.network.dto.ChatMessageReactionDto
import to.ottomot.driftd.core.network.dto.CircleChatMentionSpanDto
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.SquadGridLeaderDto
import to.ottomot.driftd.core.network.dto.SquadGridMetricDto
import to.ottomot.driftd.core.media.ChatPreparedVideoUpload
import to.ottomot.driftd.core.media.ChatVideoUploadPrep
import to.ottomot.driftd.core.media.ChatVideoUploadState
import to.ottomot.driftd.core.media.saveChatPhotoToGallery
import to.ottomot.driftd.core.network.dto.DirectMessageDto
import to.ottomot.driftd.core.network.dto.asCircleChatMessageForBubble
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import to.ottomot.driftd.ui.components.EventListSectionedLazyColumn
import to.ottomot.driftd.ui.components.MapMarkerDetailContent
import to.ottomot.driftd.ui.components.MapMarkerDetailSheet
import to.ottomot.driftd.ui.components.formatMapMarkerDistanceMeters
import to.ottomot.driftd.ui.components.openMapMarkerDirections
import to.ottomot.driftd.ui.components.shareMapMarkerText
import to.ottomot.driftd.ui.components.OttoFullscreenDialog
import to.ottomot.driftd.ui.components.OttoFullscreenDarkTopAppBar
import to.ottomot.driftd.ui.components.OttoFullscreenOpaqueTopBar
import to.ottomot.driftd.ui.components.OttoFullscreenOverlay
import to.ottomot.driftd.ui.components.OttoFullscreenScrollColumn
import to.ottomot.driftd.ui.components.OttoFullscreenScrollContent
import to.ottomot.driftd.ui.components.MapSquadLayerVisibilityTrailing
import to.ottomot.driftd.ui.components.MapSquadTrackGroupedList
import to.ottomot.driftd.ui.components.MapSquadTrackListItem
import to.ottomot.driftd.ui.components.MapSquadTrackNavigateTrailing
import to.ottomot.driftd.ui.components.OttoTabbedPager
import to.ottomot.driftd.ui.components.OttoToggleSettingCard
import to.ottomot.driftd.ui.components.SquadShareListRow
import to.ottomot.driftd.ui.dialog.OttoCenteredChoiceDialog
import to.ottomot.driftd.ui.dialog.OttoEducationDialog
import to.ottomot.driftd.ui.dialog.OttoEducationLocationHero
import to.ottomot.driftd.ui.dialog.OttoEducationShieldHero
import to.ottomot.driftd.ui.dialog.OttoShareWithSquadAnnotatedTitle
import to.ottomot.driftd.ui.dialog.OttoSquadChatShareHeroGraphic

/**
 * Held by [OttoShellTabContent] so leaving the Map tab does not drop camera position or follow mode.
 * (The map composable is removed from the tree when another tab is selected.)
 */
internal class MapPaneSessionState {
    var lastCamera: OttoMapCamera? = null
    var followDevice: Boolean = true
    var followedPresenceUserId: String? = null
    /** When set, map camera fits all active sharers for this squad and keeps updating as they move. */
    var followedSquadId: String? = null
    val chatSharedPlacePeekMarkers: MutableList<SavedPlaceDto> = mutableListOf()

    fun registerChatSharedPlacePeekMarker(
        place: SavedPlaceDto,
        savedPlaces: List<SavedPlaceDto>,
    ) {
        if (savedPlaces.any { it.id == place.id }) return
        chatSharedPlacePeekMarkers.removeAll { it.id == place.id }
        chatSharedPlacePeekMarkers.add(place)
    }

    fun clearChatSharedPlacePeekMarkers() {
        chatSharedPlacePeekMarkers.clear()
    }
}

internal data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

internal data class OttoMapCamera(
    val target: LatLng,
    val zoom: Float,
    val bearing: Float = 0f,
    val tilt: Float = 0f,
)

internal typealias CreateSquadScopedEventWithCircle =
    suspend (
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
    ) -> Result<EventDto>

internal typealias ShareCreatedSquadEventToChat =
    suspend (
        circleId: String,
        eventId: String,
    ) -> Result<Unit>

internal typealias UpdateSquadScopedEvent =
    suspend (
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
    ) -> Result<Unit>

@Composable
internal fun OttoShellTabContent(
    tab: OttoMainTab,
    ui: OttoShellUiState,
    onOpenEventDetail: (String) -> Unit,
    onDismissEventDetail: () -> Unit,
    onSubmitEventRsvp: (String, String) -> Unit,
    onSubmitEventCheckIn: (String) -> Unit,
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    onApplyEventAttachedSquads: (String, List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit = { _, _ -> },
    onOpenCircleDetail: (String) -> Unit,
    onDismissCircleDetail: () -> Unit,
    onSquadChatUnreadPositionChanged: (String, Boolean, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onSendCircleChat: (String, ChatPendingComposerAttachment?) -> Unit,
    onSendCircleChatVideo: (String, ChatSendVideoAttachment) -> Unit = { _, _ -> },
    onCancelCircleChatVideoUpload: (String) -> Unit = {},
    onPostCircleChatReaction: (String, String) -> Unit,
    onSetCircleChatReplyTo: (CircleChatMessageDto) -> Unit,
    onClearCircleChatReplyTo: () -> Unit,
    onBeginCircleChatEdit: (String) -> Unit = {},
    onCancelCircleChatEdit: () -> Unit = {},
    onDeleteCircleChatMessage: (String) -> Unit = {},
    onFetchOlderCircleChatForQuoteJump: (String) -> Unit = {},
    onLoadNextUpEventDismissals: (String, List<String>) -> Unit = { _, _ -> },
    onDismissNextUpEventBanner: (String, String, String) -> Unit = { _, _, _ -> },
    onInviteByPhone: (String, String) -> Unit,
    onAddMemberByUserId: (String, String) -> Unit,
    onCreateInviteLink: (String) -> Unit,
    onCreateSquad: (String) -> Unit,
    onRedeemInspect: (String) -> Unit,
    onRedeemAccept: (String) -> Unit,
    onRespondPendingInvite: (String, Boolean) -> Unit,
    onDismissSquadsSnack: () -> Unit,
    onCreateSquadScopedEvent: CreateSquadScopedEventWithCircle,
    onShareCreatedSquadEventToChat: ShareCreatedSquadEventToChat,
    onUpdateSquadScopedEvent: UpdateSquadScopedEvent,
    onDeleteSquadScopedEvent: suspend (String) -> Result<Unit>,
    onSquadEventGeocodeWarning: () -> Unit,
    onToggleAutoCheckIn: (Boolean) -> Unit,
    onToggleShowPublicGoingEventsOnProfile: (Boolean) -> Unit,
    onSetDriveStatsVisibility: (DriveStatsVisibilitySetting) -> Unit,
    onSetSoundEffects: (Boolean) -> Unit,
    onMapScopeSelected: (String) -> Unit,
    onRetryPresenceOnly: () -> Unit,
    onMapSharingChanged: (Boolean) -> Unit,
    onMapSharingPermissionRevoked: () -> Unit = {},
    onMapSharingOptions: (durationMinutes: Int?, whileDrivingOnly: Boolean, saveDrive: Boolean) -> Unit,
    onMapShareSaveDrive: (Boolean) -> Unit,
    onStartQuickDrive: (Boolean, Boolean, Set<String>) -> Boolean = { _, _, _ -> false },
    onSetRecordDriveOnStartEnabled: (Boolean) -> Unit = {},
    onSelectSharingCar: (String?) -> Unit = {},
    onEnsureLiveDriveSession: (Boolean) -> Unit = {},
    onStopDriveSession: () -> Unit = {},
    onStopLiveSharingOnly: () -> Unit = {},
    onSetDriveSessionSaveEnabled: (Boolean) -> Unit = {},
    driveSessionPillPresentation: (Long, String?, Int?) -> DriveSessionPillPresentation = { _, _, _ ->
        DriveSessionPillPresentation.Idle
    },
    formatDriveSessionDuration: (Long, Long) -> String = { _, _ -> "00:00" },
    formatDriveSessionDistance: (Double) -> String = { "0.0 mi" },
    formatDriveSessionTopSpeed: (Double) -> String = { "—" },
    onAcknowledgeSharingSafetyDisclaimer: () -> Unit,
    onExtendMapSharing: (durationMinutes: Int) -> Unit,
    onMapLayerShowSavedPlaces: (Boolean) -> Unit,
    onMapLayerShowEvents: (Boolean) -> Unit,
    onMapLayerShowRaceTracks: (Boolean) -> Unit,
    onMapLayerShowTraffic: (Boolean) -> Unit,
    onMapLayerCircleVisible: (String, Boolean) -> Unit,
    onSignOut: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onSaveMapAccent: (String) -> Unit,
    onDeleteAccountConfirmed: () -> Unit,
    onMessageContact: (String) -> Unit,
    /** Squads tab → DMs sub-tab prefetch (does not open full-screen overlay). */
    onSquadsPrefetchDirectMessages: () -> Unit = {},
    /** Open DM inbox overlay (e.g. Profile → Messages). */
    onOpenDirectMessages: () -> Unit = {},
    /** Open DM thread full-screen (Squads DM list tap, etc.). */
    onOpenDirectConversationFullScreen: (DirectConversationDto) -> Unit = { _ -> },
    squadsChromeIdx: Int,
    onSquadsChromeIdxChange: (Int) -> Unit,
    onDismissGarageSnack: () -> Unit,
    onSaveMapPlace: (String, Double, Double, String?) -> Unit,
    onRenameSavedPlace: (String, String) -> Unit,
    onDeleteSavedPlace: (String) -> Unit,
    onDismissSavedPlacesSnack: () -> Unit,
    /** Map person sheet → open squad detail (should switch to Squads tab at shell level). */
    onMapOpenSquadDetail: (String) -> Unit = {},
    /** Map person sheet → start/open DM with peer (shared squad required). */
    onMapMessagePresencePeer: (String) -> Unit = {},
    /** Map person sheet → full profile for someone else. */
    onMapViewPeerProfile: (String) -> Unit = {},
    /** Map person sheet → Profile tab for signed-in user. */
    onMapNavigateToOwnProfileTab: () -> Unit = {},
    onAddGarageCar: (String, String, String?, String, Int?, String?, String?, Boolean, ByteArray?, String?) -> Unit,
    onPatchGarageCar: (String, String?, String?, String?, String?, Int?, String?, String?, Boolean?, ByteArray?, String?) -> Unit,
    onDeleteGarageCar: (String) -> Unit,
    onGarageCarPhotoPicked: (String, ByteArray, String) -> Unit,
    onReorderGarageCars: (List<String>) -> Unit = { },
    onAvatarPhotoPicked: (ByteArray, String) -> Unit,
    onProfileNavigateToGarage: () -> Unit = {},
    onReplayMarketingOnboarding: () -> Unit = {},
    squadsToolbarCreateTicks: Int = 0,
    garageToolbarAddTicks: Int = 0,
    onConsumeSquadsToolbarCreateTicks: () -> Unit = {},
    onConsumeGarageToolbarAddTicks: () -> Unit = {},
    onEventsSearchRadiusMiles: (Int) -> Unit = {},
    onEventsRefresh: () -> Unit = {},
    squadsPullRefreshing: Boolean = false,
    onSquadsPullRefresh: () -> Unit = {},
    onRefreshSquadGrid: (String) -> Unit = {},
    onOpenCircleMemberProfile: (String) -> Unit = {},
    onChatProfileMessagePeer: (String) -> Unit,
    onChatProfileViewPeer: (String) -> Unit,
    onChatProfileOpenSquad: (String) -> Unit,
    onNavigateToOwnProfileTab: () -> Unit,
    onPreviewProfileLevelUp: (Int) -> Unit = {},
    onSchedulePreviewProfileLevelUpNotification: () -> Unit = {},
    onKickCircleMember: (circleId: String, userId: String) -> Unit = { _, _ -> },
    onPatchCircleMemberRole: (circleId: String, userId: String, role: String) -> Unit = { _, _, _ -> },
    onConsumePendingMapPresenceFollow: () -> Unit = {},
    onConsumePendingMapCoordinateFocus: () -> Unit = {},
    onOpenEventLocationOnMap: (Double, Double, String) -> Unit = { _, _, _ -> },
    onPrefetchChatAttachmentEvents: (Set<String>) -> Unit = {},
    onRetryPendingDriveSave: (String) -> Unit = {},
    onDeletePendingDriveArchive: (String) -> Unit = {},
    onReloadPendingDriveArchives: () -> Unit = {},
    onOpenProfileDrive: (DriveDto) -> Unit = {},
    onOpenProfileRoute: (SavedRouteDto) -> Unit = {},
    onCreateProfileRoute: () -> Unit = {},
    onOpenProfilePlace: (SavedPlaceDto) -> Unit = {},
    onShareProfileDrive: (DriveDto) -> Unit = {},
    onDeleteProfileDrive: (String) -> Unit = {},
    onDeleteProfileRoute: (String) -> Unit = {},
    onRenameProfileDrive: suspend (DriveDto, String) -> Boolean = { _, _ -> false },
    onRenameProfileRoute: suspend (SavedRouteDto, String) -> Boolean = { _, _ -> false },
    onRenameProfilePlace: (String, String) -> Unit = { _, _ -> },
    onDeleteProfilePlace: (String) -> Unit = {},
    profileDriveDetailContent: @Composable (DriveDto, () -> Unit) -> Unit = { _, onClose -> onClose() },
    profileRouteDetailContent: @Composable (SavedRouteDto, () -> Unit) -> Unit = { _, onClose -> onClose() },
    onOpenSharedDrive: (CircleChatDriveAttachmentDto, String?) -> Unit = { _, _ -> },
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
    onCreateMapRoute: () -> Unit = {},
    onOpenMapRoute: (SavedRouteDto) -> Unit = {},
    onEditMapRoute: (SavedRouteDto) -> Unit = {},
    onDeleteMapRoute: (String) -> Unit = {},
    onRenameMapRoute: suspend (SavedRouteDto, String) -> Boolean = { _, _ -> false },
    modifier: Modifier = Modifier,
) {
    val mapSession = remember { MapPaneSessionState() }
    var hasMountedMap by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == OttoMainTab.Map) {
            hasMountedMap = true
        }
    }
    Box(modifier) {
        if (hasMountedMap) {
            OttoMapPresencePane(
                ui = ui,
                circles = ui.circles,
                mapSession = mapSession,
                onScopeSelected = onMapScopeSelected,
                onRetryPresence = onRetryPresenceOnly,
                onSharingToggle = onMapSharingChanged,
                onSharingPermissionRevoked = onMapSharingPermissionRevoked,
                onMapSharingOptions = onMapSharingOptions,
                onMapShareSaveDrive = onMapShareSaveDrive,
                onStartQuickDrive = onStartQuickDrive,
                onSetRecordDriveOnStartEnabled = onSetRecordDriveOnStartEnabled,
                onSelectSharingCar = onSelectSharingCar,
                onEnsureLiveDriveSession = onEnsureLiveDriveSession,
                onStopDriveSession = onStopDriveSession,
                onStopLiveSharingOnly = onStopLiveSharingOnly,
                onSetDriveSessionSaveEnabled = onSetDriveSessionSaveEnabled,
                driveSessionPillPresentation = driveSessionPillPresentation,
                formatDriveSessionDuration = formatDriveSessionDuration,
                formatDriveSessionDistance = formatDriveSessionDistance,
                formatDriveSessionTopSpeed = formatDriveSessionTopSpeed,
                onAcknowledgeSharingSafetyDisclaimer = onAcknowledgeSharingSafetyDisclaimer,
                onExtendMapSharing = onExtendMapSharing,
                onMapLayerShowSavedPlaces = onMapLayerShowSavedPlaces,
                onMapLayerShowEvents = onMapLayerShowEvents,
                onMapLayerShowRaceTracks = onMapLayerShowRaceTracks,
                onMapLayerShowTraffic = onMapLayerShowTraffic,
                onMapLayerCircleVisible = onMapLayerCircleVisible,
                onSaveMapPlace = onSaveMapPlace,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
                onDismissSavedPlacesSnack = onDismissSavedPlacesSnack,
                onMapOpenSquadDetail = onMapOpenSquadDetail,
                onMapMessagePresencePeer = onMapMessagePresencePeer,
                onMapViewPeerProfile = onMapViewPeerProfile,
                onMapNavigateToOwnProfileTab = onMapNavigateToOwnProfileTab,
                onOpenEventDetail = onOpenEventDetail,
                onSubmitEventRsvp = onSubmitEventRsvp,
                onPrefetchDirectMessages = onSquadsPrefetchDirectMessages,
                postEventShareToChat = postEventShareToChat,
                postMapMarkerShareToChat = postMapMarkerShareToChat,
                onApplyEventAttachedSquads = onApplyEventAttachedSquads,
                onConsumePendingMapPresenceFollow = onConsumePendingMapPresenceFollow,
                onConsumePendingMapCoordinateFocus = onConsumePendingMapCoordinateFocus,
                onCreateMapRoute = onCreateMapRoute,
                onOpenMapRoute = onOpenMapRoute,
                onEditMapRoute = onEditMapRoute,
                onDeleteMapRoute = onDeleteMapRoute,
                onRenameMapRoute = onRenameMapRoute,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .rootTabVisibility(isSelected = tab == OttoMainTab.Map),
            )
        }

        when (tab) {
            OttoMainTab.Squads ->
                OttoSquadsPane(
                circles = ui.circles,
                squadLastAccessedAtByCircleId = ui.squadLastAccessedAtByCircleId,
                circleDetailUi = ui.circleDetailUi,
                events = ui.events,
                eventDetailUi = ui.eventDetailUi,
                myUserId = ui.me?.id,
                meUser = ui.me,
                deviceLocationFix = ui.deviceLocationFix,
                contacts = ui.contacts,
                squadsInitialLoading =
                    (!ui.coreFeedsLoadAttempted || ui.refreshing) && ui.circles.isEmpty(),
                squadsLoadFailed = ui.squadsLoadFailed,
                pendingInvites = ui.pendingInvites,
                squadsSnack = ui.squadsSnack,
                invitePreview = ui.invitePreview,
                onOpenCircle = onOpenCircleDetail,
                onDismissCircleDetail = onDismissCircleDetail,
                onSquadChatUnreadPositionChanged = onSquadChatUnreadPositionChanged,
                onCreateSquad = onCreateSquad,
                onRedeemInspect = onRedeemInspect,
                onRedeemAccept = onRedeemAccept,
                onRespondInvite = onRespondPendingInvite,
                onDismissSnack = onDismissSquadsSnack,
                onSendChat = onSendCircleChat,
                onSendChatVideo = onSendCircleChatVideo,
                onCancelCircleChatVideoUpload = onCancelCircleChatVideoUpload,
                onPostCircleChatReaction = onPostCircleChatReaction,
                onSetCircleChatReplyTo = onSetCircleChatReplyTo,
                onClearCircleChatReplyTo = onClearCircleChatReplyTo,
                onBeginCircleChatEdit = onBeginCircleChatEdit,
                onCancelCircleChatEdit = onCancelCircleChatEdit,
                onDeleteCircleChatMessage = onDeleteCircleChatMessage,
                onFetchOlderCircleChatForQuoteJump = onFetchOlderCircleChatForQuoteJump,
                onLoadNextUpEventDismissals = onLoadNextUpEventDismissals,
                onDismissNextUpEventBanner = onDismissNextUpEventBanner,
                onInviteByPhone = onInviteByPhone,
                onAddMemberByUserId = onAddMemberByUserId,
                onCreateInviteLink = onCreateInviteLink,
                onCreateSquadScopedEvent = onCreateSquadScopedEvent,
                onShareCreatedSquadEventToChat = onShareCreatedSquadEventToChat,
                onUpdateSquadScopedEvent = onUpdateSquadScopedEvent,
                onDeleteSquadScopedEvent = onDeleteSquadScopedEvent,
                onSquadEventGeocodeWarning = onSquadEventGeocodeWarning,
                directMessagesUi = ui.directMessages,
                onSquadsPrefetchDirectMessages = onSquadsPrefetchDirectMessages,
                onOpenDirectConversationFullScreen = onOpenDirectConversationFullScreen,
                squadsChromeIdx = squadsChromeIdx,
                onSquadsChromeIdxChange = onSquadsChromeIdxChange,
                onOpenEventDetail = onOpenEventDetail,
                onDismissEventDetail = onDismissEventDetail,
                onOpenEventLocationOnMap = onOpenEventLocationOnMap,
                onSubmitEventRsvp = onSubmitEventRsvp,
                onSubmitEventCheckIn = onSubmitEventCheckIn,
                postEventShareToChat = postEventShareToChat,
                postMapMarkerShareToChat = postMapMarkerShareToChat,
                onApplyEventAttachedSquads = onApplyEventAttachedSquads,
                pendingSquadChatFocusTick = ui.pendingSquadChatFocusTick,
                pendingSquadChatFocusCircleId = ui.pendingSquadChatFocusCircleId,
                onToggleAutoCheckIn = onToggleAutoCheckIn,
                onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                squadsToolbarCreateTicks = squadsToolbarCreateTicks,
                onConsumeSquadsToolbarCreateTicks = onConsumeSquadsToolbarCreateTicks,
                squadsPullRefreshing = squadsPullRefreshing,
                onSquadsPullRefresh = onSquadsPullRefresh,
                unreadChatCountByCircleId = ui.unreadChatCountByCircleId,
                unreadDirectMessageCountByConversationId = ui.unreadDirectMessageCountByConversationId,
                nextUpDismissalsByCircleId = ui.nextUpEventDismissalsByCircleId,
                presenceMembersByCircleId = ui.presenceMembersByCircleId,
                onRefreshSquadGrid = onRefreshSquadGrid,
                onOpenMemberProfile = onOpenCircleMemberProfile,
                onChatProfileMessagePeer = onChatProfileMessagePeer,
                onChatProfileViewPeer = onChatProfileViewPeer,
                onChatProfileOpenSquad = onChatProfileOpenSquad,
                onNavigateToOwnProfileTab = onNavigateToOwnProfileTab,
                onKickCircleMember = onKickCircleMember,
                onPatchCircleMemberRole = onPatchCircleMemberRole,
                chatAttachmentHydratedEventsById = ui.chatAttachmentHydratedEventsById,
                onPrefetchChatAttachmentEvents = onPrefetchChatAttachmentEvents,
                onOpenSharedDrive = onOpenSharedDrive,
                onOpenSharedPlace = onOpenSharedPlace,
                eventRsvpSubmittingEventId = ui.eventRsvpSubmittingEventId,
                modifier = Modifier.fillMaxSize(),
            )
            OttoMainTab.Events ->
                OttoEventsPane(
                events = ui.events,
                communityEvents = ui.communityEvents,
                squadFeedEvents = ui.squadFeedEvents,
                squadGoingEvents = ui.squadGoingEvents,
                deviceLocationFix = ui.deviceLocationFix,
                selectedDistanceMiles = ui.selectedEventDistanceMiles,
                onSelectedDistanceChange = onEventsSearchRadiusMiles,
                detailUi = ui.eventDetailUi,
                circles = ui.circles,
                contacts = ui.contacts,
                meUser = ui.me,
                dmConversations = ui.directMessages.conversations,
                onPrefetchDirectMessages = onSquadsPrefetchDirectMessages,
                onOpenEvent = onOpenEventDetail,
                onDismissDetail = onDismissEventDetail,
                onOpenEventLocationOnMap = onOpenEventLocationOnMap,
                onRsvp = onSubmitEventRsvp,
                onCheckIn = onSubmitEventCheckIn,
                onToggleAutoCheckIn = onToggleAutoCheckIn,
                onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                postEventShareToChat = postEventShareToChat,
                postMapMarkerShareToChat = postMapMarkerShareToChat,
                onApplyEventAttachedSquads = onApplyEventAttachedSquads,
                pendingSquadChatFocusTick = ui.pendingSquadChatFocusTick,
                pendingEventsMyEventsFocusTick = ui.pendingEventsMyEventsFocusTick,
                presenceMembersByCircleId = ui.presenceMembersByCircleId,
                modifier = Modifier.fillMaxSize(),
            )
            OttoMainTab.Map ->
                Unit
            OttoMainTab.Garage ->
                OttoGaragePane(
                cars = ui.garageCars,
                garageSnack = ui.garageSnack,
                onDismissGarageSnack = onDismissGarageSnack,
                onAddGarageCar = onAddGarageCar,
                onPatchGarageCar = onPatchGarageCar,
                onDeleteGarageCar = onDeleteGarageCar,
                onGarageCarPhotoPicked = onGarageCarPhotoPicked,
                onReorderGarage = onReorderGarageCars,
                garageToolbarAddTicks = garageToolbarAddTicks,
                onConsumeGarageToolbarAddTicks = onConsumeGarageToolbarAddTicks,
                modifier = Modifier.fillMaxSize(),
            )
            OttoMainTab.Profile ->
                OttoProfilePane(
                ui = ui,
                onSignOut = onSignOut,
                onToggleAutoCheckIn = onToggleAutoCheckIn,
                onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                onSetDriveStatsVisibility = onSetDriveStatsVisibility,
                onSetSoundEffects = onSetSoundEffects,
                onSaveDisplayName = onSaveDisplayName,
                onSaveMapAccent = onSaveMapAccent,
                onDeleteAccountConfirmed = onDeleteAccountConfirmed,
                onMessageContact = onMessageContact,
                onOpenDirectMessages = onOpenDirectMessages,
                onAvatarPhotoPicked = onAvatarPhotoPicked,
                onNavigateToGarage = onProfileNavigateToGarage,
                onReplayMarketingOnboarding = onReplayMarketingOnboarding,
                onOpenEventDetail = onOpenEventDetail,
                onReloadPendingDriveArchives = onReloadPendingDriveArchives,
                onRetryPendingDriveSave = onRetryPendingDriveSave,
                onDeletePendingDriveArchive = onDeletePendingDriveArchive,
                onOpenProfileDrive = onOpenProfileDrive,
                onOpenProfileRoute = onOpenProfileRoute,
                onCreateProfileRoute = onCreateProfileRoute,
                onOpenProfilePlace = onOpenProfilePlace,
                onShareProfileDrive = onShareProfileDrive,
                onDeleteProfileDrive = onDeleteProfileDrive,
                onDeleteProfileRoute = onDeleteProfileRoute,
                onRenameProfileDrive = onRenameProfileDrive,
                onRenameProfileRoute = onRenameProfileRoute,
                onRenameProfilePlace = onRenameProfilePlace,
                onDeleteProfilePlace = onDeleteProfilePlace,
                profileDriveDetailContent = profileDriveDetailContent,
                profileRouteDetailContent = profileRouteDetailContent,
                onPreviewProfileLevelUp = onPreviewProfileLevelUp,
                onSchedulePreviewProfileLevelUpNotification = onSchedulePreviewProfileLevelUpNotification,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (tab == OttoMainTab.Profile) {
            ui.eventDetailUi?.let { detail ->
                BackHandler(onBack = onDismissEventDetail)
                EventDetailOverlay(
                    detailUi = detail,
                    circles = ui.circles,
                    contacts = ui.contacts,
                    meUser = ui.me,
                    dmConversations = ui.directMessages.conversations,
                    onPrefetchDirectMessages = onSquadsPrefetchDirectMessages,
                    sourceCircleId = null,
                    presenceMembersByCircleId = ui.presenceMembersByCircleId,
                    deviceLocationFix = ui.deviceLocationFix,
                    onClose = onDismissEventDetail,
                    onOpenEventLocationOnMap = onOpenEventLocationOnMap,
                    onRsvp = onSubmitEventRsvp,
                    onCheckIn = onSubmitEventCheckIn,
                    onUpdateSquadEvent = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        Result.failure(UnsupportedOperationException())
                    },
                    onDeleteSquadEvent = { Result.failure(UnsupportedOperationException()) },
                    onToggleAutoCheckIn = onToggleAutoCheckIn,
                    onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                    postEventShareToChat = postEventShareToChat,
                    pendingSquadChatFocusTick = ui.pendingSquadChatFocusTick,
                    onEventAssociationsSaved = { squads ->
                        onApplyEventAttachedSquads(detail.eventId, squads)
                    },
                )
            }
        }
    }
}

private fun Modifier.rootTabVisibility(isSelected: Boolean): Modifier =
    this
        .alpha(if (isSelected) 1f else 0f)
        .zIndex(if (isSelected) 1f else 0f)

internal enum class SquadChromeSection(val labelRes: Int) {
    SquadList(R.string.squads_subtab_squads),
    Dms(R.string.squads_subtab_dms),
    Invites(R.string.squads_subtab_invites),
}

@Composable
internal fun OttoIosUnderlineTabBar(
    labelResIds: List<Int>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    selectedLabelWeight: FontWeight = FontWeight.SemiBold,
    unselectedLabelWeight: FontWeight = FontWeight.Medium,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        labelResIds.forEachIndexed { i, resId ->
            val sel = i == selectedIdx
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(resId),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (sel) selectedLabelWeight else unselectedLabelWeight,
                        ),
                    color =
                        if (sel) selectedColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth(0.72f),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        thickness = 3.dp,
                        color =
                            if (sel) selectedColor else Color.Transparent,
                    )
                }
            }
        }
    }
}

private sealed interface SquadSubtabTrailing {
    data object None : SquadSubtabTrailing

    data object UnreadDot : SquadSubtabTrailing

    data class InviteCount(
        val n: Int,
    ) : SquadSubtabTrailing
}

@Composable
private fun SquadIosTabBar(
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    squadsTrailing: SquadSubtabTrailing,
    dmsTrailing: SquadSubtabTrailing,
    invitesTrailing: SquadSubtabTrailing,
) {
    val trailings = listOf(squadsTrailing, dmsTrailing, invitesTrailing)
    val accentUnreadGreen = Color(0xFF34C759)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SquadChromeSection.entries.forEachIndexed { i, section ->
            val sel = i == selectedIdx
            val trailing = trailings[i]
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(section.labelRes),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            ),
                        color =
                            if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    when (trailing) {
                        is SquadSubtabTrailing.InviteCount ->
                            if (trailing.n > 0) {
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .background(accentUnreadGreen)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${trailing.n.coerceAtMost(9)}",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                            ),
                                        color = Color.White,
                                    )
                                }
                            }
                        SquadSubtabTrailing.UnreadDot ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(accentUnreadGreen),
                            )
                        SquadSubtabTrailing.None -> Unit
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth(0.72f),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        thickness = 3.dp,
                        color =
                            if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                }
            }
        }
    }
}

internal fun sharedCircleNameWithPeer(
    circles: List<CircleDto>,
    myUserId: String?,
    otherUserId: String?,
): String? {
    val oid = otherUserId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val mid = myUserId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return circles
        .firstOrNull { c ->
            val members = c.members.orEmpty()
            members.any { ottoUserIdsEqual(it.userId, oid) } &&
                members.any { ottoUserIdsEqual(it.userId, mid) }
        }
        ?.name
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

@Composable
private fun SquadMemberAvatarOverlap(
    userIds: List<String>,
    contacts: List<UserDto>,
    meUser: UserDto?,
) {
    val ids = userIds.take(4)
    if (ids.isEmpty()) return
    val avatarSize = 32.dp
    val step = 16.dp
    val overlapSlots = kotlin.math.max(ids.size - 1, 0)
    val totalW = avatarSize + step * overlapSlots
    Box(modifier = Modifier.height(avatarSize).width(totalW)) {
        ids.forEachIndexed { i, uid ->
            val peer = contacts.find { ottoUserIdsEqual(it.id, uid) } ?: meUser?.takeIf { ottoUserIdsEqual(it.id, uid) }
            val accentPeer = mapAccentComposeColor(peer?.mapAccentKey)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = step * i)
                        .size(avatarSize)
                        .zIndex(i.toFloat())
                        .border(
                            width = 2.dp,
                            color = Color.Black,
                            shape = CircleShape,
                        )
                        .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                UserProfileAvatar(
                    displayName = peer?.displayName,
                    userId = uid,
                    avatarUrl = peer?.avatarUrl,
                    mapAccentKey = peer?.mapAccentKey,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    textColor = Color.White,
                )
            }
        }
    }
}

@Composable
internal fun DmConversationListRow(
    conversation: DirectConversationDto,
    circles: List<CircleDto>,
    myUserId: String?,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    hasUnread: Boolean = false,
    unreadCount: Int = if (hasUnread) 1 else 0,
) {
    val ctx = LocalContext.current
    val other = conversation.otherUser
    val peerName =
        other?.displayName?.takeIf { it.isNotBlank() }
            ?: shortenId(conversation.id)
    val subtitle = sharedCircleNameWithPeer(circles, myUserId, other?.id)
    val accent = mapAccentComposeColor(other?.mapAccentKey)
    val timeLabel = formatDmConversationTime(conversation.lastMessageAt)

    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .border(2.dp, accent, CircleShape)
                            .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    UserProfileAvatar(
                        displayName = other?.displayName,
                        userId = other?.id ?: conversation.id,
                        avatarUrl = other?.avatarUrl,
                        mapAccentKey = other?.mapAccentKey,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    peerName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { sub ->
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val previewLine = directInboxPreviewLine(conversation, myUserId)
                if (previewLine.isNotBlank()) {
                    Text(
                        previewLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!selectionMode) {
                    if (timeLabel.isNotBlank()) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (unreadCount > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

private fun dmComposeMateRows(
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    meUserId: String?,
    blockedPeerIds: Set<String> = emptySet(),
): List<Pair<String, String>> {
    val me = meUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val byId = LinkedHashMap<String, String>()
    for (c in circles) {
        for (m in c.members.orEmpty()) {
            val uid = m.userId.trim().takeIf { it.isNotEmpty() } ?: continue
            if (ottoUserIdsEqual(uid, me)) continue
            if (!canDirectMessagePresencePeer(circles, me, uid, blockedPeerIds)) continue
            if (byId.containsKey(uid)) continue
            val dn =
                contacts.find { ottoUserIdsEqual(it.id, uid) }?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: shortenId(uid)
            byId[uid] = dn
        }
    }
    return byId.entries.map { it.toPair() }.sortedBy { it.second.lowercase(Locale.US) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewDmComposeFullscreen(
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    myUserId: String?,
    meUser: UserDto? = null,
    directConversations: List<DirectConversationDto>,
    onDismiss: () -> Unit,
    onSubmitRecipient: (recipientUserId: String) -> Unit,
    onOpenExistingConversation: (DirectConversationDto) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val blockedPeerIds =
        remember(meUser) {
            meUser?.blockedUserIds.orEmpty().mapNotNull { it.trim().takeIf { x -> x.isNotEmpty() } }.toSet()
        }
    val mates =
        remember(circles, contacts, myUserId, blockedPeerIds) {
            dmComposeMateRows(circles, contacts, myUserId, blockedPeerIds)
        }
    val qTrim = query.trim().lowercase(Locale.US)
    val filteredMates =
        remember(mates, qTrim) {
            if (qTrim.isEmpty()) {
                mates
            } else {
                mates.filter { (_, name) -> name.lowercase(Locale.US).contains(qTrim) }
            }
        }

    OttoFullscreenOverlay(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dm_compose_title)) },
                navigationIcon = {
                    TextButton(onClick = onDismiss, enabled = !submitting) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    value = query,
                    onValueChange = { query = it },
                    enabled = !submitting,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.dm_compose_search_people)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.dm_compose_suggested),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (filteredMates.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dm_compose_no_matches),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(filteredMates, key = { it.first }) { (uid, name) ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable(enabled = !submitting) {
                                    submitting = true
                                    onSubmitRecipient(uid)
                                },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserProfileAvatar(
                                displayName = name,
                                userId = uid,
                                avatarUrl = contacts.find { ottoUserIdsEqual(it.id, uid) }?.avatarUrl,
                                mapAccentKey = contacts.find { ottoUserIdsEqual(it.id, uid) }?.mapAccentKey,
                                modifier = Modifier.size(44.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textColor = Color.White,
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                sharedCircleNameWithPeer(circles, myUserId, uid)?.let { sub ->
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.NavigateNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.dm_compose_recent),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (directConversations.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dm_compose_no_recent),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(directConversations, key = { it.id }) { conv ->
                    val other = conv.otherUser ?: return@items
                    val oid = other.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@items
                    val title = other.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: shortenId(oid)
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable(enabled = !submitting) { onOpenExistingConversation(conv) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserProfileAvatar(
                                displayName = title,
                                userId = oid,
                                avatarUrl = other.avatarUrl,
                                mapAccentKey = other.mapAccentKey,
                                modifier = Modifier.size(44.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textColor = Color.White,
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = directInboxPreviewLine(conv, myUserId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.NavigateNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

internal fun directInboxPreviewLine(
    conversation: DirectConversationDto,
    myUserId: String?,
): String {
    val lm = conversation.lastMessage ?: return ""
    val sid = lm.senderUserId?.trim().orEmpty()
    val me = myUserId?.trim().orEmpty()
    val body = lm.bodyPreview?.trim().orEmpty()
    val img = lm.hasImage
    val vid = lm.hasVideoAttachment
    return when {
        sid.isNotEmpty() && me.isNotEmpty() && ottoUserIdsEqual(sid, me) ->
            when {
                vid && body.isEmpty() -> "You: Video"
                img && body.isEmpty() -> "You: Photo"
                else -> "You: $body"
            }
        vid && body.isEmpty() -> "Video"
        img && body.isEmpty() -> "Photo"
        else -> body
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OttoSquadsPane(
    circles: List<CircleDto>,
    squadLastAccessedAtByCircleId: Map<String, Double>,
    circleDetailUi: CircleDetailUi?,
    events: List<EventDto>,
    eventDetailUi: EventDetailUi?,
    myUserId: String?,
    meUser: UserDto?,
    deviceLocationFix: LocationFix?,
    contacts: List<UserDto>,
    squadsInitialLoading: Boolean,
    squadsLoadFailed: Boolean,
    pendingInvites: List<MyPendingCircleInvite>,
    squadsSnack: String?,
    invitePreview: InviteLinkResolveDto?,
    onOpenCircle: (String) -> Unit,
    onDismissCircleDetail: () -> Unit,
    onSquadChatUnreadPositionChanged: (String, Boolean, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onCreateSquad: (String) -> Unit,
    onRedeemInspect: (String) -> Unit,
    onRedeemAccept: (String) -> Unit,
    onRespondInvite: (String, Boolean) -> Unit,
    onDismissSnack: () -> Unit,
    onSendChat: (String, ChatPendingComposerAttachment?) -> Unit,
    onSendChatVideo: (String, ChatSendVideoAttachment) -> Unit = { _, _ -> },
    onCancelCircleChatVideoUpload: (String) -> Unit = {},
    onInviteByPhone: (String, String) -> Unit,
    onAddMemberByUserId: (String, String) -> Unit,
    onCreateInviteLink: (String) -> Unit,
    onCreateSquadScopedEvent: CreateSquadScopedEventWithCircle,
    onShareCreatedSquadEventToChat: ShareCreatedSquadEventToChat,
    onUpdateSquadScopedEvent: UpdateSquadScopedEvent,
    onDeleteSquadScopedEvent: suspend (String) -> Result<Unit>,
    onSquadEventGeocodeWarning: () -> Unit,
    onPostCircleChatReaction: (String, String) -> Unit = { _, _ -> },
    onSetCircleChatReplyTo: (CircleChatMessageDto) -> Unit = {},
    onClearCircleChatReplyTo: () -> Unit = {},
    onBeginCircleChatEdit: (String) -> Unit = {},
    onCancelCircleChatEdit: () -> Unit = {},
    onDeleteCircleChatMessage: (String) -> Unit = {},
    onFetchOlderCircleChatForQuoteJump: (String) -> Unit = {},
    onLoadNextUpEventDismissals: (String, List<String>) -> Unit = { _, _ -> },
    onDismissNextUpEventBanner: (String, String, String) -> Unit = { _, _, _ -> },
    directMessagesUi: DirectMessagesOverlayUi,
    onSquadsPrefetchDirectMessages: () -> Unit,
    onOpenDirectConversationFullScreen: (DirectConversationDto) -> Unit,
    squadsChromeIdx: Int,
    onSquadsChromeIdxChange: (Int) -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onDismissEventDetail: () -> Unit,
    onOpenEventLocationOnMap: (Double, Double, String) -> Unit = { _, _, _ -> },
    onSubmitEventRsvp: (String, String) -> Unit,
    onSubmitEventCheckIn: (String) -> Unit,
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    onApplyEventAttachedSquads: (String, List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit = { _, _ -> },
    pendingSquadChatFocusTick: Long = 0L,
    pendingSquadChatFocusCircleId: String? = null,
    onToggleAutoCheckIn: (Boolean) -> Unit,
    onToggleShowPublicGoingEventsOnProfile: (Boolean) -> Unit = {},
    squadsToolbarCreateTicks: Int,
    onConsumeSquadsToolbarCreateTicks: () -> Unit,
    squadsPullRefreshing: Boolean,
    onSquadsPullRefresh: () -> Unit,
    unreadChatCountByCircleId: Map<String, Int>,
    unreadDirectMessageCountByConversationId: Map<String, Int>,
    nextUpDismissalsByCircleId: Map<String, List<NextUpEventDismissalDto>> = emptyMap(),
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    onRefreshSquadGrid: (String) -> Unit = {},
    onOpenMemberProfile: (String) -> Unit = {},
    onChatProfileMessagePeer: (String) -> Unit,
    onChatProfileViewPeer: (String) -> Unit,
    onChatProfileOpenSquad: (String) -> Unit,
    onNavigateToOwnProfileTab: () -> Unit,
    onKickCircleMember: (circleId: String, userId: String) -> Unit = { _, _ -> },
    onPatchCircleMemberRole: (circleId: String, userId: String, role: String) -> Unit = { _, _, _ -> },
    chatAttachmentHydratedEventsById: Map<String, EventDto> = emptyMap(),
    onPrefetchChatAttachmentEvents: (Set<String>) -> Unit = {},
    onOpenSharedDrive: (CircleChatDriveAttachmentDto, String?) -> Unit = { _, _ -> },
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
    eventRsvpSubmittingEventId: String? = null,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newSquadName by rememberSaveable { mutableStateOf("") }

    val invitesCount = pendingInvites.size
    val squadsSubtabDot = remember(unreadChatCountByCircleId) {
        unreadChatCountByCircleId.values.any { it > 0 }
    }
    val dmsSubtabDot = remember(unreadDirectMessageCountByConversationId) {
        unreadDirectMessageCountByConversationId.values.any { it > 0 }
    }
    val squadsTrailing =
        if (squadsSubtabDot) SquadSubtabTrailing.UnreadDot else SquadSubtabTrailing.None
    val dmsTrailing =
        if (dmsSubtabDot) SquadSubtabTrailing.UnreadDot else SquadSubtabTrailing.None
    val invitesTrailing =
        if (invitesCount > 0) {
            SquadSubtabTrailing.InviteCount(invitesCount)
        } else {
            SquadSubtabTrailing.None
        }

    LaunchedEffect(squadsToolbarCreateTicks) {
        if (squadsToolbarCreateTicks > 0) {
            showCreateDialog = true
            onConsumeSquadsToolbarCreateTicks()
        }
    }

    val filteredCircles =
        remember(circles, searchQuery, squadLastAccessedAtByCircleId) {
            val recencySorted =
                circlesSortedByRecentAccess(circles, squadLastAccessedAtByCircleId)
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                recencySorted
            } else {
                recencySorted.filter { it.name.contains(q, ignoreCase = true) }
            }
        }

    val sectionIdx = squadsChromeIdx.coerceIn(0, SquadChromeSection.entries.lastIndex)
    val section = SquadChromeSection.entries[sectionIdx]

    var dmSearchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(section) {
        if (section == SquadChromeSection.Dms) {
            onSquadsPrefetchDirectMessages()
        }
    }

    val filteredDmConversations =
        remember(directMessagesUi.conversations, dmSearchQuery, circles, myUserId) {
            val q = dmSearchQuery.trim().lowercase()
            if (q.isEmpty()) {
                directMessagesUi.conversations
            } else {
                directMessagesUi.conversations.filter { conv ->
                    val name =
                        conv.otherUser?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                            ?: shortenId(conv.id)
                    val sub = sharedCircleNameWithPeer(circles, myUserId, conv.otherUser?.id).orEmpty()
                    val preview = directInboxPreviewLine(conv, myUserId).lowercase(Locale.US)
                    name.lowercase(Locale.US).contains(q) ||
                        sub.lowercase(Locale.US).contains(q) ||
                        preview.contains(q)
                }
            }
        }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SquadIosTabBar(
                selectedIdx = sectionIdx,
                onSelect = onSquadsChromeIdxChange,
                squadsTrailing = squadsTrailing,
                dmsTrailing = dmsTrailing,
                invitesTrailing = invitesTrailing,
            )
            OttoTabbedPager(
                pageCount = SquadChromeSection.entries.size,
                selectedIdx = sectionIdx,
                onSelect = onSquadsChromeIdxChange,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (SquadChromeSection.entries[page]) {
                    SquadChromeSection.SquadList -> {
                        PullToRefreshBox(
                            isRefreshing = squadsPullRefreshing,
                            onRefresh = onSquadsPullRefresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                OutlinedTextField(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.squads_search_placeholder)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                )
                            }
                            items(items = filteredCircles, key = { it.id }) { circle ->
                                ElevatedCard(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    OttoSquadRow(
                                        circle = circle,
                                        contacts = contacts,
                                        meUser = meUser,
                                        myUserId = myUserId,
                                        unreadChatCount = unreadChatCountForCircle(unreadChatCountByCircleId, circle.id),
                                        modifier = Modifier.clickable { onOpenCircle(circle.id) },
                                    )
                                }
                            }
                            if (circles.isNotEmpty() && filteredCircles.isEmpty()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squads_search_no_results),
                                        icon = Icons.Outlined.Search,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 24.dp),
                                    )
                                }
                            }
                            if (circles.isEmpty() && squadsInitialLoading) {
                                item {
                                    LoadingTabMessage(
                                        text = stringResource(R.string.squads_loading),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 24.dp),
                                    )
                                }
                            } else if (circles.isEmpty() && squadsLoadFailed) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squads_load_failed_title),
                                        body = stringResource(R.string.squads_load_failed_body),
                                        icon = Icons.Outlined.ErrorOutline,
                                        actionLabel = stringResource(R.string.retry),
                                        onAction = onSquadsPullRefresh,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 24.dp),
                                    )
                                }
                            } else if (circles.isEmpty()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.empty_squads_extended),
                                        icon = Icons.Outlined.Groups,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }
                        }
                    }

                    SquadChromeSection.Dms -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                OutlinedTextField(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    value = dmSearchQuery,
                                    onValueChange = { dmSearchQuery = it },
                                    singleLine = true,
                                    placeholder = {
                                        Text(stringResource(R.string.squads_dms_search_placeholder))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                )
                            }
                            directMessagesUi.threadSnack?.takeIf { it.isNotBlank() }?.let { err ->
                                item {
                                    Text(
                                        err,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            when {
                                directMessagesUi.listLoading &&
                                    directMessagesUi.conversations.isEmpty() -> {
                                    item {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 220.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                !directMessagesUi.listLoading &&
                                    directMessagesUi.conversations.isEmpty() -> {
                                    item {
                                        EmptyTabMessage(
                                            text = stringResource(R.string.messages_conversations_empty),
                                            icon = Icons.Outlined.Forum,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                                        )
                                    }
                                }

                                filteredDmConversations.isEmpty() -> {
                                    item {
                                        EmptyTabMessage(
                                            text = stringResource(R.string.squads_search_no_results),
                                            icon = Icons.Outlined.Search,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                                        )
                                    }
                                }

                                else -> {
                                    items(
                                        items = filteredDmConversations,
                                        key = { it.id },
                                    ) { conv ->
                                        DmConversationListRow(
                                            conversation = conv,
                                            circles = circles,
                                            myUserId = myUserId,
                                            unreadCount = unreadDirectMessageCountByConversationId[conv.id] ?: 0,
                                            onClick = {
                                                onOpenDirectConversationFullScreen(conv)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SquadChromeSection.Invites -> {
                        PullToRefreshBox(
                            isRefreshing = squadsPullRefreshing,
                            onRefresh = onSquadsPullRefresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                            if (pendingInvites.isNotEmpty()) {
                                item {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pending_invites_heading),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        pendingInvites.forEach { inv ->
                                            Column(Modifier.padding(bottom = 12.dp)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                        Text(
                                                            inv.circleName,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        inv.circleDescription
                                                            ?.trim()
                                                            ?.takeIf { it.isNotEmpty() }
                                                            ?.let { d ->
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(
                                                                    d,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    maxLines = 4,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        TextButton(onClick = { onRespondInvite(inv.inviteId, false) }) {
                                                            Text(stringResource(R.string.invite_decline))
                                                        }
                                                        Button(onClick = { onRespondInvite(inv.inviteId, true) }) {
                                                            Text(stringResource(R.string.invite_accept))
                                                        }
                                                    }
                                                }
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                }
                            }
                            if (pendingInvites.isEmpty()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squads_invites_empty),
                                        icon = Icons.Outlined.PersonAdd,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }

            squadsSnack?.takeIf { it.isNotBlank() }?.let { msg ->
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onDismissSnack) { Text(stringResource(R.string.dismiss_snack)) }
                    }
                }
            }
        }

        circleDetailUi?.let { detail ->
            val stackedEvent = eventDetailUi
            BackHandler(enabled = stackedEvent == null) { onDismissCircleDetail() }

            Surface(
                modifier =
                    Modifier
                        .fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    CircleDetailOverlay(
                        detailUi = detail,
                        allCircles = circles,
                        allUpcomingEvents = events,
                        myUserId = myUserId,
                        meUser = meUser,
                        contacts = contacts,
                        presenceMembersByCircleId = presenceMembersByCircleId,
                        nextUpDismissals = nextUpDismissalsByCircleId[detail.circleId].orEmpty(),
                        onClose = onDismissCircleDetail,
                        onSendChat = onSendChat,
                        onSendChatVideo = onSendChatVideo,
                        onCancelCircleChatVideoUpload = onCancelCircleChatVideoUpload,
                        onInviteByPhone = onInviteByPhone,
                        onAddMemberByUserId = onAddMemberByUserId,
                        onCreateInviteLink = onCreateInviteLink,
                        onOpenEventDetail = onOpenEventDetail,
                        onPostChatReaction = onPostCircleChatReaction,
                        onSetChatReplyTo = onSetCircleChatReplyTo,
                        onClearChatReplyTo = onClearCircleChatReplyTo,
                        onBeginCircleChatEdit = onBeginCircleChatEdit,
                        onCancelCircleChatEdit = onCancelCircleChatEdit,
                        onDeleteCircleChatMessage = onDeleteCircleChatMessage,
                        onFetchOlderCircleChatForQuoteJump = onFetchOlderCircleChatForQuoteJump,
                        onLoadNextUpEventDismissals = onLoadNextUpEventDismissals,
                        onDismissNextUpEventBanner = onDismissNextUpEventBanner,
                        onRefreshSquadGrid = onRefreshSquadGrid,
                        onOpenMemberProfile = onOpenMemberProfile,
                        onCreateSquadScopedEvent = onCreateSquadScopedEvent,
                        onShareCreatedSquadEventToChat = onShareCreatedSquadEventToChat,
                        onSquadEventGeocodeWarning = onSquadEventGeocodeWarning,
                        onChatProfileMessagePeer = onChatProfileMessagePeer,
                        onChatProfileViewPeer = onChatProfileViewPeer,
                        onChatProfileOpenSquad = onChatProfileOpenSquad,
                        onNavigateToOwnProfileTab = onNavigateToOwnProfileTab,
                        onKickCircleMember = onKickCircleMember,
                        onPatchCircleMemberRole = onPatchCircleMemberRole,
                        onApplyEventAttachedSquads = onApplyEventAttachedSquads,
                        chatAttachmentHydratedEventsById = chatAttachmentHydratedEventsById,
                        onPrefetchChatAttachmentEvents = onPrefetchChatAttachmentEvents,
                        onOpenSharedDrive = onOpenSharedDrive,
                        onOpenSharedPlace = onOpenSharedPlace,
                        onSquadChatUnreadPositionChanged = onSquadChatUnreadPositionChanged,
                        unreadChatCountByCircleId = unreadChatCountByCircleId,
                        eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                        onSubmitEventRsvp = onSubmitEventRsvp,
                        pendingSquadChatFocusTick = pendingSquadChatFocusTick,
                        pendingSquadChatFocusCircleId = pendingSquadChatFocusCircleId,
                    )

                    stackedEvent?.let { evUi ->
                        BackHandler(onBack = onDismissEventDetail)
                        EventDetailOverlay(
                            detailUi = evUi,
                            circles = circles,
                            contacts = contacts,
                            meUser = meUser,
                            dmConversations = directMessagesUi.conversations,
                            onPrefetchDirectMessages = onSquadsPrefetchDirectMessages,
                            sourceCircleId = detail.circleId.takeIf { it.isNotBlank() },
                            presenceMembersByCircleId = presenceMembersByCircleId,
                            deviceLocationFix = deviceLocationFix,
                            onClose = onDismissEventDetail,
                            onOpenEventLocationOnMap = onOpenEventLocationOnMap,
                            onRsvp = onSubmitEventRsvp,
                            onCheckIn = onSubmitEventCheckIn,
                            onUpdateSquadEvent = onUpdateSquadScopedEvent,
                            onDeleteSquadEvent = onDeleteSquadScopedEvent,
                            onToggleAutoCheckIn = onToggleAutoCheckIn,
                            onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                            postEventShareToChat = postEventShareToChat,
                            pendingSquadChatFocusTick = pendingSquadChatFocusTick,
                            onEventAssociationsSaved = { squads ->
                                onApplyEventAttachedSquads(evUi.eventId, squads)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.squad_create_title)) },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newSquadName,
                    onValueChange = { newSquadName = it },
                    label = { Text(stringResource(R.string.squad_create_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newSquadName.trim().length >= 2,
                    onClick = {
                        onCreateSquad(newSquadName.trim())
                        newSquadName = ""
                        showCreateDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private enum class SquadDetailSection(
    val labelRes: Int,
) {
    Chat(R.string.squad_detail_tab_chat),
    Events(R.string.squad_detail_tab_events),
    Grid(R.string.squad_detail_tab_grid),
}

private fun squadGridRankRingBrush(rank: Int): Brush =
    when (rank) {
        1 ->
            Brush.linearGradient(
                listOf(
                    Color(0xFFFFEB6B),
                    Color(0xFFF5A614),
                    Color(0xFFE0800D).copy(alpha = 0.9f),
                ),
            )
        2 ->
            Brush.linearGradient(
                listOf(
                    Color(0xFFE0DAFA),
                    Color(0xFF9E8CE6),
                    Color(0xFF7366B8),
                ),
            )
        else ->
            Brush.linearGradient(
                listOf(
                    Color(0xFFF29E61),
                    Color(0xFFC7733D),
                    Color(0xFF8C5224),
                ),
            )
    }

private fun squadGridRankCapsuleColor(rank: Int): Color =
    when (rank) {
        1 -> Color(0xFFF5C42E)
        2 -> Color(0xFFB8AEE0)
        else -> Color(0xFFD18552)
    }

private fun squadGridMetricBackgroundRes(metricKey: String): Int? =
    when (metricKey) {
        "distance_driven" -> R.drawable.otto_grid_milesdriven
        "top_speed" -> R.drawable.otto_grid_peakvelocity
        "messages_posted" -> R.drawable.otto_grid_radiotraffic
        "events_attended" -> R.drawable.otto_grid_checkins
        else -> null
    }

@Composable
private fun SquadDetailIosTabBar(
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    chatUnreadCount: Int = 0,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SquadDetailSection.entries.forEachIndexed { i, tab ->
            val sel = i == selectedIdx
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            ),
                        color =
                            if (sel) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                            },
                        maxLines = 1,
                    )
                    if (tab == SquadDetailSection.Chat && !sel && chatUnreadCount > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (chatUnreadCount > 99) "99+" else chatUnreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .height(1.5.dp)
                        .fillMaxWidth(0.72f),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        thickness = 1.5.dp,
                        color =
                            if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SquadGridSection(
    detailUi: CircleDetailUi,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = detailUi.squadGrid
    val loading = detailUi.squadGridLoading
    val err = detailUi.squadGridError
    val metrics = grid?.metrics.orEmpty()
    val pageEmpty = grid != null && !loading && metrics.all { it.leaders.isNullOrEmpty() }

    PullToRefreshBox(
        isRefreshing = loading && grid != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            loading && grid == null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            !err.isNullOrBlank() && grid == null ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            pageEmpty -> {
                OttoEmptyState(
                    title = stringResource(R.string.grid_empty_title),
                    body = stringResource(R.string.grid_empty_subtitle),
                    icon = Icons.Outlined.Speed,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                )
            }
            else -> {
                val metricKeysSignature = metrics.joinToString { it.key }
                var gridCardsRevealed by remember(metricKeysSignature) { mutableStateOf(false) }
                LaunchedEffect(metricKeysSignature) {
                    gridCardsRevealed = false
                    delay(1)
                    gridCardsRevealed = true
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, top = 15.dp, end = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    metrics.forEachIndexed { index, metric ->
                        AnimatedVisibility(
                            visible = gridCardsRevealed,
                            enter =
                                fadeIn(
                                    animationSpec =
                                        tween(420, delayMillis = index * 55, easing = FastOutSlowInEasing),
                                ) +
                                    slideInVertically(
                                        animationSpec =
                                            tween(420, delayMillis = index * 55, easing = FastOutSlowInEasing),
                                        initialOffsetY = { it / 8 },
                                    ),
                        ) {
                            SquadGridMetricCardCompose(
                                metric = metric,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquadGridMetricCardCompose(
    metric: SquadGridMetricDto,
) {
    val leaders = metric.leaders.orEmpty()
    val primary = MaterialTheme.colorScheme.primary
    val bgRes = squadGridMetricBackgroundRes(metric.key)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = primary.copy(alpha = 0.11f),
                    spotColor = Color.Black.copy(alpha = 0.38f),
                )
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 0.85.dp,
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.08f),
                                    primary.copy(alpha = 0.15f),
                                ),
                        ),
                    shape = RoundedCornerShape(18.dp),
                ),
    ) {
        when (bgRes) {
            null ->
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.055f)),
                )
            else ->
                Image(
                    painter = painterResource(bgRes),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                SquadGridMetricIcon(metricKey = metric.key)
                Column(Modifier.weight(1f)) {
                    Text(
                        metric.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        metric.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
            }
            // Match iOS SquadGridMetricCard: VStack spacing 10 + podium .padding(top, 1) + HStack .padding(top, 4)
            Spacer(Modifier.height(10.dp))
            if (leaders.isEmpty()) {
                Text(
                    stringResource(R.string.grid_metric_no_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                SquadGridPodiumRow(
                    metricKey = metric.key,
                    unit = metric.unit,
                    leaders = leaders,
                )
            }
        }
    }
}

@Composable
private fun SquadGridMetricIcon(metricKey: String) {
    val icon =
        when (metricKey) {
            "distance_driven" -> Icons.Outlined.Route
            "top_speed" -> Icons.Outlined.Speed
            "messages_posted" -> Icons.Outlined.Forum
            "events_attended" -> Icons.Outlined.ConfirmationNumber
            else -> Icons.Filled.Star
        }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun SquadGridPodiumRow(
    metricKey: String,
    unit: String,
    leaders: List<SquadGridLeaderDto>,
) {
    val top = leaders.take(3)
    Row(
        Modifier
            .fillMaxWidth()
            // iOS SquadGridPodium: .padding(.top, 4) on HStack; outer .padding(.top, 1) on podium → 5dp total
            .padding(top = 5.dp),
        verticalAlignment = Alignment.Bottom,
        // iOS HStack(alignment: .bottom, spacing: 7)
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (top.size) {
            1 -> {
                Spacer(Modifier.weight(0.1f))
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[0],
                    emphasisHero = true,
                    modifier = Modifier.weight(0.8f),
                )
                Spacer(Modifier.weight(0.1f))
            }
            2 -> {
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[1],
                    emphasisHero = false,
                    modifier = Modifier.weight(1f),
                )
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[0],
                    emphasisHero = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }
            else -> {
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[1],
                    emphasisHero = false,
                    modifier = Modifier.weight(1f),
                )
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[0],
                    emphasisHero = true,
                    modifier = Modifier.weight(1f),
                )
                SquadGridLeaderPodiumSlot(
                    metricKey = metricKey,
                    unit = unit,
                    leader = top[2],
                    emphasisHero = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val SquadGridPodiumAvatarAlignHeight = 108.dp

@Composable
private fun SquadGridHeroPulseGlow(
    size: Dp,
    glowColor: Color,
) {
    val infinite = rememberInfiniteTransition(label = "squadGridHeroPulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.22f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseGlow",
    )
    Box(
        Modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = pulseAlpha), Color.Transparent),
                ),
            ),
    )
}

@Composable
private fun SquadGridLeaderPodiumSlot(
    metricKey: String,
    unit: String,
    leader: SquadGridLeaderDto,
    emphasisHero: Boolean,
    modifier: Modifier = Modifier,
) {
    val avatarSize = if (emphasisHero) 86.dp else 56.dp
    val ringStroke = if (emphasisHero) 3.2.dp else 2.1.dp
    val ringOuter = avatarSize + ringStroke * 2
    val tierColor = profileTierComposeColor(leader.progressionTier)
    val ringBrush = squadGridRankRingBrush(leader.rank)
    val capsuleColor = squadGridRankCapsuleColor(leader.rank)
    val pulseHero = emphasisHero && leader.rank == 1

    val ambientRank =
        when (leader.rank) {
            1 -> Color(0xFFFFD84A).copy(alpha = if (emphasisHero) 0.48f else 0.36f)
            2 -> Color(0xFFB8A8FF).copy(alpha = 0.32f)
            else -> Color(0xFFE88A52).copy(alpha = 0.28f)
        }
    val display =
        leader.displayName
            .trim()
            .substringBefore(" ")
            .ifBlank { leader.displayName.trim() }
    // iOS SquadGridPodium: badgeLift = hero ? 18 : 16 (pt above ring top)
    val badgeLift = if (emphasisHero) 18.dp else 16.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(SquadGridPodiumAvatarAlignHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                if (pulseHero) {
                    SquadGridHeroPulseGlow(
                        size = ringOuter + 34.dp,
                        glowColor = capsuleColor,
                    )
                }

                if (leader.rank == 1) {
                    Box(
                        modifier =
                            Modifier
                                .size(ringOuter + 10.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    val n = 7
                                    val cell = size.width / n
                                    for (row in 0 until n) {
                                        for (col in 0 until n) {
                                            if ((row + col) % 2 == 0) {
                                                drawRect(
                                                    color = Color.White.copy(alpha = 0.035f),
                                                    topLeft =
                                                        Offset(
                                                            col * cell,
                                                            row * cell,
                                                        ),
                                                    size = Size(cell, cell),
                                                )
                                            }
                                        }
                                    }
                                },
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(ringOuter + 18.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(ambientRank, Color.Transparent),
                                ),
                            ),
                )

                Box(
                    modifier =
                        Modifier
                            .size(ringOuter)
                            .border(ringStroke, ringBrush, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(avatarSize)
                                .clip(CircleShape),
                    ) {
                        UserProfileAvatar(
                            displayName = display,
                            userId = leader.userId,
                            avatarUrl = leader.avatarUrl,
                            mapAccentKey = leader.mapAccentKey,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = capsuleColor.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFF0A0A10)),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = -badgeLift),
                ) {
                    Text(
                        "${leader.rank}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                gridStatValueFormatted(metricKey, unit, leader.value),
                style =
                    if (emphasisHero) {
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                display,
                style =
                    if (emphasisHero) {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    } else {
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                    },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun gridStatValueFormatted(
    metricKey: String,
    unit: String,
    value: Double,
): String =
    when (metricKey) {
        "distance_driven" -> {
            if (value < 10) {
                String.format(Locale.US, "%.1f %s", value, unit)
            } else {
                "${value.roundToInt()} $unit"
            }
        }
        "top_speed" -> {
            val mph = value.roundToInt()
            if (mph > 0) "$mph $unit" else "—"
        }
        else -> "${value.roundToInt()} $unit"
    }

private sealed interface SquadChatTimelineItem {
    data class DaySeparator(
        val label: String,
    ) : SquadChatTimelineItem

    /** Server `messageType: \"system\"` (e.g. someone joined the squad). */
    data class SystemNotice(
        val msg: CircleChatMessageDto,
    ) : SquadChatTimelineItem

    data class Bubble(
        val msg: CircleChatMessageDto,
    ) : SquadChatTimelineItem
}

private fun isSystemCircleChatMessage(msg: CircleChatMessageDto): Boolean =
    msg.messageType?.trim()?.equals("system", ignoreCase = true) == true

private fun squadChatTimelineItems(
    messages: List<CircleChatMessageDto>,
    todayLabel: String,
    yesterdayLabel: String,
): List<SquadChatTimelineItem> {
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val sorted =
        messages.sortedWith(
            compareBy<CircleChatMessageDto> {
                squadChatInstant(it.createdAt)?.toEpochMilli() ?: Long.MIN_VALUE
            },
        )

    fun dayLabel(day: LocalDate): String =
        when (day) {
            today.minusDays(1) -> yesterdayLabel
            today ->
                todayLabel
            else ->
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .format(day.atStartOfDay(zone).toInstant().atZone(zone))
        }

    return buildList {
        var prevDay: LocalDate? = null
        sorted.forEach { msg ->
            val inst = squadChatInstant(msg.createdAt)
            val day = inst?.atZone(zone)?.toLocalDate()
            if (day != null && day != prevDay) {
                add(SquadChatTimelineItem.DaySeparator(dayLabel(day)))
                prevDay = day
            }
            add(
                if (isSystemCircleChatMessage(msg)) {
                    SquadChatTimelineItem.SystemNotice(msg)
                } else {
                    SquadChatTimelineItem.Bubble(msg)
                },
            )
        }
    }
}

private fun newestBubbleInTimeline(timeline: List<SquadChatTimelineItem>): CircleChatMessageDto? {
    for (i in timeline.indices.reversed()) {
        when (val row = timeline[i]) {
            is SquadChatTimelineItem.Bubble -> return row.msg
            else -> Unit
        }
    }
    return null
}

/**
 * iOS `reconcileScrollStateAfterNewMessage`: initial jump to bottom, then auto-scroll only for own
 * sends or while pinned to latest — not on every peer message (avoids LazyList crashes while typing).
 */
@Composable
private fun ChatTimelineAutoScrollEffect(
    timeline: List<SquadChatTimelineItem>,
    listState: LazyListState,
    conversationKey: String,
    myUserId: String?,
    pinThresholdPx: Float,
    isHistoryLoading: Boolean,
    onJumpReadyChange: (Boolean) -> Unit,
) {
    var didInitialScroll by remember(conversationKey) { mutableStateOf(false) }
    val newestBubbleId =
        remember(timeline) {
            newestBubbleInTimeline(timeline)?.id
        }
    LaunchedEffect(conversationKey) {
        didInitialScroll = false
        onJumpReadyChange(false)
    }
    LaunchedEffect(conversationKey, timeline.size, newestBubbleId, isHistoryLoading) {
        if (timeline.isEmpty()) {
            onJumpReadyChange(false)
            return@LaunchedEffect
        }
        val index = timeline.lastIndex
        val settling = !didInitialScroll || isHistoryLoading
        if (settling) {
            listState.scrollToChatLatestBottom(index, animate = false)
            if (!isHistoryLoading) {
                didInitialScroll = true
                onJumpReadyChange(true)
            } else {
                onJumpReadyChange(false)
            }
            return@LaunchedEffect
        }
        val newest = newestBubbleInTimeline(timeline) ?: return@LaunchedEffect
        val isOwn = squadChatMessageOwnedUserBubble(newest, myUserId)
        if (isOwn || listState.isPinnedToLatestChat(pinThresholdPx)) {
            listState.scrollToChatLatestBottom(index, animate = true)
        }
    }
}

private fun squadChatInstant(raw: String?): Instant? {
    val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(t))
    } catch (_: DateTimeException) {
        try {
            Instant.parse(t)
        } catch (_: Exception) {
            null
        }
    }
}

private const val CHAT_MESSAGE_EDIT_WINDOW_MS = 120_000L

private fun squadChatMessageOwnedUserBubble(
    msg: CircleChatMessageDto,
    myUserId: String?,
): Boolean {
    if (myUserId.isNullOrBlank()) return false
    if (!ottoUserIdsEqual(msg.senderUserId, myUserId)) return false
    return (msg.messageType ?: "user") == "user"
}

private fun squadChatMessageEditEligible(
    msg: CircleChatMessageDto,
    myUserId: String?,
): Boolean {
    if (!squadChatMessageOwnedUserBubble(msg, myUserId)) return false
    if (!msg.imageUrl.isNullOrBlank()) return false
    if (msg.videoAttachment != null) return false
    if (msg.eventAttachment != null || msg.driveAttachment != null || msg.placeAttachment != null) return false
    val created = squadChatInstant(msg.createdAt) ?: return false
    return Duration.between(created, Instant.now()).toMillis() <= CHAT_MESSAGE_EDIT_WINDOW_MS
}

private fun formatDmConversationTime(raw: String?): String {
    val inst = squadChatInstant(raw) ?: return ""
    return try {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(inst)
    } catch (_: Exception) {
        ""
    }
}

internal fun mapAccentComposeColor(raw: String?): Color {
    return when ((raw ?: "").trim().lowercase()) {
        "lime" -> Color(0xFFACF26D)
        "mint" -> Color(0xFF26A699)
        "sky" -> Color(0xFF5AC8FA)
        "blue" -> Color(0xFF0A84FF)
        "violet", "purple" -> Color(0xFFBF5AF2)
        "amber", "orange" -> Color(0xFFFFAB40)
        "rose" -> Color(0xFFFF6B94)
        "coral" -> Color(0xFFFF7F50)
        else -> Color(0xFF30D158)
    }
}

private data class SquadMentionPickerState(
    val visible: Boolean,
    val anchor: Int,
    val filter: String,
)

private fun computeSquadMentionPickerState(draft: String): SquadMentionPickerState {
    val at = draft.lastIndexOf('@')
    if (at < 0) return SquadMentionPickerState(false, -1, "")
    if (at > 0) {
        val prev = draft[at - 1]
        if (!prev.isWhitespace() && prev != '\n') {
            return SquadMentionPickerState(false, -1, "")
        }
    }
    val tail = draft.substring(at + 1)
    val delimIx = tail.indexOfFirst { it.isWhitespace() || it == '\n' }
    if (delimIx == 0) {
        return SquadMentionPickerState(false, -1, "")
    }
    val query = if (delimIx < 0) tail else tail.substring(0, delimIx)
    // Completed @mention (picker insert or typed name + space) — not an active query.
    if (delimIx >= 0) {
        return SquadMentionPickerState(false, -1, "")
    }
    return SquadMentionPickerState(true, at, query)
}

private fun replaceMentionTokenForSquadChat(
    draft: String,
    anchor: Int,
    displayName: String,
): String {
    if (anchor < 0 || anchor >= draft.length) return draft
    val after = anchor + 1
    var end = after
    while (end < draft.length) {
        val ch = draft[end]
        if (ch.isWhitespace() || ch == '\n') break
        end++
    }
    val insert = "@${displayName.trim()} "
    return buildString {
        append(draft, 0, anchor)
        append(insert)
        append(draft, end, draft.length)
    }
}

private val ChatComposerEditAccent = Color(0xFFBF5AF2)

@Composable
private fun ChatComposerEditContextCard(
    previewText: String,
    onDismissEdit: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A1C).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ChatComposerEditAccent.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp),
                tint = ChatComposerEditAccent,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.chat_editing_banner),
                    style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ChatComposerEditAccent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismissEdit,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.chat_cancel_edit),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private const val CHAT_SEND_PHOTO_MAX_BYTES = PhotoUploadClientTargetMaxBytes

private fun prepareChatSendPhotoAttachment(
    context: Context,
    uri: Uri,
): Result<ChatSendPhotoAttachment> =
    runCatching {
        val cr = context.contentResolver
        val declaredMime = cr.getType(uri)?.trim().orEmpty().ifBlank { "image/jpeg" }
        val rawBytes =
            cr.openInputStream(uri)?.use { it.readBytes() }
                ?: error(context.getString(R.string.chat_attachment_read_failed))
        if (rawBytes.isEmpty()) {
            error(context.getString(R.string.chat_attachment_read_failed))
        }
        val normalizedMime =
            when {
                declaredMime.contains("png", ignoreCase = true) -> "image/png"
                declaredMime.contains("webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
        val (bytes, contentType) =
            if (rawBytes.size <= CHAT_SEND_PHOTO_MAX_BYTES) {
                rawBytes to normalizedMime
            } else {
                val jpeg =
                    compressChatPhotoToJpegUnderLimit(context, uri)
                        ?: error(context.getString(R.string.chat_attachment_too_large))
                if (jpeg.size > CHAT_SEND_PHOTO_MAX_BYTES) {
                    error(context.getString(R.string.chat_attachment_too_large))
                }
                jpeg to "image/jpeg"
            }
        ChatSendPhotoAttachment(bytes = bytes, contentType = contentType)
    }

private fun decodeBitmapMaxSide(
    context: Context,
    uri: Uri,
    maxSide: Int,
): Bitmap? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxSide || bounds.outHeight / sampleSize > maxSide) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun compressChatPhotoToJpegUnderLimit(
    context: Context,
    uri: Uri,
): ByteArray? {
    var maxSide = 1600
    var attempts = 0
    while (attempts < 5 && maxSide >= 480) {
        attempts++
        val bmp = decodeBitmapMaxSide(context, uri, maxSide) ?: return null
        var quality = 88
        try {
            while (quality >= 38) {
                val stream = java.io.ByteArrayOutputStream()
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)) break
                val out = stream.toByteArray()
                if (out.size <= CHAT_SEND_PHOTO_MAX_BYTES) return out
                quality -= 9
            }
        } finally {
            bmp.recycle()
        }
        maxSide = maxSide * 3 / 4
    }
    return null
}

@Composable
private fun OttoChatComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    sendBusy: Boolean,
    placeholder: @Composable () -> Unit,
    onSend: () -> Unit,
    showAttachButton: Boolean = true,
    enabledAttachmentActions: Set<ChatComposerAttachmentAction> = ChatComposerAttachmentAction.squadChatActions,
    pendingAttachment: ChatPendingComposerAttachment? = null,
    isLoadingLocationAttachment: Boolean = false,
    onClearPendingAttachment: (() -> Unit)? = null,
    onAttachmentAction: (ChatComposerAttachmentAction) -> Unit = {},
    replyBannerKey: String? = null,
    replyBanner: (@Composable () -> Unit)? = null,
    isEditMode: Boolean = false,
    editPreviewText: String? = null,
    editBaselineTrimmed: String? = null,
    onCancelEdit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val trimmed = value.trim()
    val baseline = editBaselineTrimmed?.trim().orEmpty()
    val isEdit = isEditMode && onCancelEdit != null
    val canSubmit =
        !sendBusy &&
            if (isEdit) {
                trimmed.isNotEmpty() && trimmed != baseline
            } else {
                trimmed.isNotEmpty() || pendingAttachment != null
            }
    var attachmentTrayVisible by remember { mutableStateOf(false) }
    val sortedAttachmentActions =
        remember(enabledAttachmentActions) {
            ChatComposerAttachmentAction.entries.filter { enabledAttachmentActions.contains(it) }
        }
    LaunchedEffect(pendingAttachment?.id) {
        val id = pendingAttachment?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        focusRequester?.requestFocus()
    }
    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor =
                if (isEdit) ChatComposerEditAccent.copy(alpha = 0.42f) else Color.Transparent,
            focusedBorderColor = if (isEdit) ChatComposerEditAccent else Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        )
    val enabledSendTint = ChatComposerEditAccent
    val disabledSendTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(Modifier.fillMaxWidth()) {
        if (replyBanner != null) {
            if (replyBannerKey != null) {
                key(replyBannerKey) {
                    replyBanner.invoke()
                }
            } else {
                replyBanner.invoke()
            }
        }
        AnimatedVisibility(
            visible = isEdit,
            enter = fadeIn(tween(160)) + expandVertically(tween(180), expandFrom = Alignment.Top),
            exit = fadeOut(tween(100)) + shrinkVertically(),
        ) {
            ChatComposerEditContextCard(
                previewText = editPreviewText.orEmpty(),
                onDismissEdit = { onCancelEdit?.invoke() },
            )
        }
        if (!isEdit && pendingAttachment != null && onClearPendingAttachment != null) {
            ChatComposerPendingAttachmentChip(
                attachment = pendingAttachment,
                onRemove = onClearPendingAttachment,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = attachmentTrayVisible && showAttachButton && !isEdit,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            ChatComposerAttachmentTrayBar(
                actions = sortedAttachmentActions,
                isLoadingLocation = isLoadingLocationAttachment,
                onAction = { action ->
                    attachmentTrayVisible = false
                    onAttachmentAction(action)
                },
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showAttachButton) {
                ChatComposerAttachmentToggleButton(
                    trayVisible = attachmentTrayVisible,
                    onClick = { attachmentTrayVisible = !attachmentTrayVisible },
                )
            }
            OutlinedTextField(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                value = value,
                onValueChange = onValueChange,
                enabled = !sendBusy,
                placeholder = placeholder,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                colors = fieldColors,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
            )
            IconButton(
                onClick = onSend,
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription =
                        if (isEdit) {
                            stringResource(R.string.accessibility_save_message_edit)
                        } else {
                            stringResource(R.string.squad_chat_send)
                        },
                    tint = if (!canSubmit) disabledSendTint else enabledSendTint,
                )
            }
        }

        if (sendBusy) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OttoChatComposerBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    sendBusy: Boolean,
    placeholder: @Composable () -> Unit,
    onSend: () -> Unit,
    showAttachButton: Boolean = true,
    enabledAttachmentActions: Set<ChatComposerAttachmentAction> = ChatComposerAttachmentAction.squadChatActions,
    pendingAttachment: ChatPendingComposerAttachment? = null,
    isLoadingLocationAttachment: Boolean = false,
    onClearPendingAttachment: (() -> Unit)? = null,
    onAttachmentAction: (ChatComposerAttachmentAction) -> Unit = {},
    replyBannerKey: String? = null,
    replyBanner: (@Composable () -> Unit)? = null,
    isEditMode: Boolean = false,
    editPreviewText: String? = null,
    editBaselineTrimmed: String? = null,
    onCancelEdit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val trimmed = value.text.trim()
    val baseline = editBaselineTrimmed?.trim().orEmpty()
    val isEdit = isEditMode && onCancelEdit != null
    val canSubmit =
        !sendBusy &&
            if (isEdit) {
                trimmed.isNotEmpty() && trimmed != baseline
            } else {
                trimmed.isNotEmpty() || pendingAttachment != null
            }
    var attachmentTrayVisible by remember { mutableStateOf(false) }
    val sortedAttachmentActions =
        remember(enabledAttachmentActions) {
            ChatComposerAttachmentAction.entries.filter { enabledAttachmentActions.contains(it) }
        }
    LaunchedEffect(pendingAttachment?.id) {
        val id = pendingAttachment?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        focusRequester?.requestFocus()
    }
    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor =
                if (isEdit) ChatComposerEditAccent.copy(alpha = 0.42f) else Color.Transparent,
            focusedBorderColor = if (isEdit) ChatComposerEditAccent else Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        )
    val enabledSendTint = ChatComposerEditAccent
    val disabledSendTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(Modifier.fillMaxWidth()) {
        if (replyBanner != null) {
            if (replyBannerKey != null) {
                key(replyBannerKey) {
                    replyBanner.invoke()
                }
            } else {
                replyBanner.invoke()
            }
        }
        AnimatedVisibility(
            visible = isEdit,
            enter = fadeIn(tween(160)) + expandVertically(tween(180), expandFrom = Alignment.Top),
            exit = fadeOut(tween(100)) + shrinkVertically(),
        ) {
            ChatComposerEditContextCard(
                previewText = editPreviewText.orEmpty(),
                onDismissEdit = { onCancelEdit?.invoke() },
            )
        }
        if (!isEdit && pendingAttachment != null && onClearPendingAttachment != null) {
            ChatComposerPendingAttachmentChip(
                attachment = pendingAttachment,
                onRemove = onClearPendingAttachment,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = attachmentTrayVisible && showAttachButton && !isEdit,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            ChatComposerAttachmentTrayBar(
                actions = sortedAttachmentActions,
                isLoadingLocation = isLoadingLocationAttachment,
                onAction = { action ->
                    attachmentTrayVisible = false
                    onAttachmentAction(action)
                },
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showAttachButton) {
                ChatComposerAttachmentToggleButton(
                    trayVisible = attachmentTrayVisible,
                    onClick = { attachmentTrayVisible = !attachmentTrayVisible },
                )
            }
            OutlinedTextField(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                value = value,
                onValueChange = onValueChange,
                enabled = !sendBusy,
                placeholder = placeholder,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                colors = fieldColors,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
            )
            IconButton(
                onClick = onSend,
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = if (isEdit) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription =
                        if (isEdit) {
                            stringResource(R.string.accessibility_save_message_edit)
                        } else {
                            stringResource(R.string.squad_chat_send)
                        },
                    tint = if (!canSubmit) disabledSendTint else enabledSendTint,
                )
            }
        }

        if (sendBusy) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

private fun mergeChatEvent(
    squadScopedEvents: List<EventDto>,
    allUpcomingEvents: List<EventDto>,
    chatAttachmentHydration: Map<String, EventDto>,
    attachment: CircleChatEventAttachmentDto?,
): EventDto? {
    val aid =
        attachment?.eventId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    fun matches(e: EventDto) = e.id.trim().equals(aid, ignoreCase = true)
    return squadScopedEvents.firstOrNull { matches(it) }
        ?: allUpcomingEvents.firstOrNull { matches(it) }
        ?: chatAttachmentHydration.entries.firstOrNull { it.key.trim().equals(aid, ignoreCase = true) }?.value
}

private fun CircleChatLinkPreviewDto.linkActionUrl(): String? =
    (finalUrl ?: url)?.trim()?.takeIf { it.isNotBlank() }

private fun CircleChatLinkPreviewDto.isRenderable(): Boolean =
    status == "ready" && linkActionUrl() != null

private fun Context.openExternalHttpUrl(raw: String) {
    try {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme.isNullOrBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
    }
}

private data class ChatBodyLink(
    val start: Int,
    val end: Int,
    val url: String,
)

private const val ChatMentionAnnotationTag = "MENTION_USER_ID"
private const val ChatUrlAnnotationTag = "URL"

private val ChatBodyUrlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>()]+)""")

private fun chatBodyLinks(body: String): List<ChatBodyLink> =
    ChatBodyUrlRegex.findAll(body).mapNotNull { match ->
        val raw = match.value.trim()
        val trimmed = raw.trimEnd('.', ',', '!', '?', ';', ':')
        if (trimmed.isBlank()) return@mapNotNull null
        val end = match.range.first + trimmed.length
        val url =
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        ChatBodyLink(start = match.range.first, end = end, url = url)
    }.toList()

private fun chatBodyWithMentionsAndLinks(
    body: String,
    mentions: List<CircleChatMentionSpanDto>,
    displayNameByUserId: Map<String, String>,
    baseColor: Color,
    mentionColor: Color,
    linkColor: Color,
): AnnotatedString {
    val sortedMentions =
        mentions
            .filter { it.length > 0 && it.start >= 0 && it.start + it.length <= body.length }
            .sortedBy { it.start }
    val base =
        buildAnnotatedString {
            var sourceCursor = 0
            for (mention in sortedMentions) {
                if (mention.start < sourceCursor) continue
                if (mention.start > sourceCursor) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(body.substring(sourceCursor, mention.start))
                    }
                }
                val label =
                    displayNameByUserId[mention.userId]?.trim()?.takeIf { it.isNotEmpty() }
                        ?: body.substring(mention.start + 1, mention.start + mention.length).trimStart()
                val mentionStart = length
                withStyle(
                    SpanStyle(
                        color = mentionColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append("@")
                    append(label)
                }
                addStringAnnotation(ChatMentionAnnotationTag, mention.userId, mentionStart, length)
                sourceCursor = mention.start + mention.length
            }
            if (sourceCursor < body.length) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(body.substring(sourceCursor))
                }
            }
        }
    val links = chatBodyLinks(base.text)
    if (links.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        for (link in links) {
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
                link.start,
                link.end,
            )
            addStringAnnotation(ChatUrlAnnotationTag, link.url, link.start, link.end)
        }
    }
}

@Composable
private fun ChatBodyText(
    text: AnnotatedString,
    style: TextStyle,
    onOpenUrl: (String) -> Unit,
    onOpenMentionUserId: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, long-press on the message body runs here (child [pointerInput] wins over parent [combinedClickable]). */
    onLongPress: (() -> Unit)? = null,
) {
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        modifier =
            modifier.pointerInput(text, onLongPress) {
                detectTapGestures(
                    onLongPress =
                        onLongPress?.let { handler ->
                            { _: Offset ->
                                handler()
                            }
                        },
                    onTap = { offset ->
                        val charOffset =
                            layoutResult
                                ?.getOffsetForPosition(offset)
                                ?: return@detectTapGestures
                        text.getStringAnnotations(ChatUrlAnnotationTag, charOffset, charOffset)
                            .firstOrNull()
                            ?.let {
                                onOpenUrl(it.item)
                                return@detectTapGestures
                            }
                        text.getStringAnnotations(ChatMentionAnnotationTag, charOffset, charOffset)
                            .firstOrNull()
                            ?.let { onOpenMentionUserId(it.item) }
                    },
                )
            },
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun SquadChatRichEventCard(
    mergedEvent: EventDto?,
    attachment: CircleChatEventAttachmentDto,
    eventRsvpSubmittingEventId: String?,
    onSubmitEventRsvp: (String, String) -> Unit,
    onViewEvent: () -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val title =
        mergedEvent?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: attachment.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.squad_chat_event_untitled)

    val locationLabel =
        mergedEvent?.let { shortAddress(it).trim() }?.takeIf { it.isNotEmpty() }
            ?: attachment.addressLabel?.trim()?.takeIf { it.isNotEmpty() }

    val startInstant =
        remember(mergedEvent?.startsAt, attachment.startsAt, mergedEvent?.id) {
            squadChatInstant(mergedEvent?.startsAt) ?: squadChatInstant(attachment.startsAt)
        }

    val dayLabel =
        startInstant?.atZone(zoneId)?.format(
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()),
        )
    val clockLabel =
        startInstant?.let {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId).format(it)
        }

    val metaLine =
        remember(locationLabel, dayLabel, clockLabel) {
            listOfNotNull(locationLabel, dayLabel, clockLabel)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
        }

    val cardShape = RoundedCornerShape(12.dp)
    val viewTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    val bannerUrl =
        remember(mergedEvent?.bannerImage?.url, attachment.bannerImageUrl) {
            (mergedEvent?.bannerImage?.url ?: attachment.bannerImageUrl)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { MediaUrlResolver.resolve(it)?.toString() }
        }
    val bannerTopShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    Surface(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .padding(top = 4.dp)
                .clip(cardShape)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    cardShape,
                ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
        shape = cardShape,
    ) {
        Column {
            if (bannerUrl != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(bannerTopShape)
                            .clickable(onClick = onViewEvent),
                ) {
                    AsyncImage(
                        model = ottoImageRequest(LocalContext.current, bannerUrl),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onViewEvent),
                    )
                    if (metaLine.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            metaLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onViewEvent),
                        )
                    }
                }
            }

            mergedEvent?.let { ev ->
                Spacer(Modifier.height(7.dp))
                ChatEventRsvpStrip(
                    event = ev,
                    eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                    onRsvp = onSubmitEventRsvp,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.squad_chat_view_event_arrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = viewTint,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onViewEvent)
                        .padding(vertical = 3.dp),
            )
            }
        }
    }
}

@Composable
private fun SquadChatUnavailableShareCard(
    sharedHeader: String,
    deletedMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    sharedByFirstName: String,
    messageCreatedAt: String?,
) {
    val timeText =
        squadChatInstant(messageCreatedAt)?.let { inst ->
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(inst)
        } ?: ""
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .padding(top = 4.dp)
                .clip(cardShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), cardShape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
        shape = cardShape,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$sharedByFirstName $sharedHeader",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timeText.isNotBlank()) {
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
                Text(deletedMessage, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f))
            }
        }
    }
}

@Composable
private fun SquadChatRichPlaceCard(
    attachment: CircleChatPlaceAttachmentDto,
    messageId: String,
    sharedByFirstName: String,
    messageCreatedAt: String?,
    onViewPlace: () -> Unit,
) {
    val placeTeal = Color(0xFF00A5AA)
    val timeText =
        squadChatInstant(messageCreatedAt)?.let { inst ->
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(inst)
        } ?: ""
    val mapPreviewUrl =
        attachment.mapPreviewUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }

    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .padding(top = 4.dp)
                .clip(cardShape)
                .clickable(onClick = onViewPlace)
                .border(1.dp, placeTeal.copy(alpha = 0.42f), cardShape),
        color = Color.White.copy(alpha = 0.055f),
        shape = cardShape,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = placeTeal.copy(alpha = 0.22f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = placeTeal,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.chat_place_shared_header, sharedByFirstName),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timeText.isNotBlank()) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val mapPreviewDensity = LocalDensity.current
            var mapPreviewHeight by remember(messageId, attachment.latitude, attachment.longitude) { mutableStateOf(118.dp) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        mapPreviewHeight =
                            with(mapPreviewDensity) {
                                maxOf(118.dp, size.width.toDp() * 0.44f)
                            }
                    },
            ) {
                if (mapPreviewUrl != null) {
                    AsyncImage(
                        model = mapPreviewUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(mapPreviewHeight)
                                .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(mapPreviewHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.map_point_saved),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                attachment.name?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                attachment.addressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { address ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.42f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            address,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.chat_place_view_on_map),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = placeTeal.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SquadChatRichDriveCard(
    attachment: CircleChatDriveAttachmentDto,
    sharedByFirstName: String,
    messageCreatedAt: String?,
    onViewDrive: () -> Unit,
) {
    val title = DriveDisplayNaming.squadChatListTitle(attachment.title)
    val distanceText = formatDriveDistanceMiles(attachment.distanceMeters)
    val completedText = formatDriveCompletedAt(attachment.completedAt ?: messageCreatedAt)
    val timeText =
        squadChatInstant(messageCreatedAt)?.let { inst ->
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(inst)
        } ?: ""
    val mapSourceId = "chat-drive-${attachment.driveId}-${messageCreatedAt.orEmpty()}"

    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .padding(top = 4.dp)
                .clip(cardShape)
                .clickable(onClick = onViewDrive)
                .border(1.dp, Color(0xFF7B3DFF).copy(alpha = 0.42f), cardShape),
        color = Color.White.copy(alpha = 0.055f),
        shape = cardShape,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = Color(0xFF7B3DFF).copy(alpha = 0.22f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        OttoDriveSteeringIcon(size = 14.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.chat_drive_shared_header, sharedByFirstName),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timeText.isNotBlank()) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val mapPreviewDensity = LocalDensity.current
            var mapPreviewHeight by remember(attachment.driveId, mapSourceId) { mutableStateOf(118.dp) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        mapPreviewHeight =
                            with(mapPreviewDensity) {
                                maxOf(118.dp, size.width.toDp() * 0.44f)
                            }
                    },
            ) {
                ChatDriveMapPreviewHero(
                    attachment = attachment,
                    height = mapPreviewHeight,
                    lineSourceId = mapSourceId,
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (completedText.isNotBlank()) {
                    Text(
                        completedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f),
                        maxLines = 2,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Route,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        distanceText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.chat_drive_stat_distance),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.48f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.chat_drive_view_summary_arrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF7B3DFF).copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun SquadChatLinkPreviewTail(
    preview: CircleChatLinkPreviewDto,
    openUrl: () -> Unit,
) {
    val thumb =
        preview.imageUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }

    val url = preview.linkActionUrl().orEmpty()
    val footerLeft =
        preview.siteName?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching { Uri.parse(url).host }.getOrNull()
            ?: "Link"

    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .padding(top = 6.dp)
                .clip(shape)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    shape,
                )
                .clickable(onClick = openUrl),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        shape = shape,
    ) {
        Column {
            if (thumb != null) {
                val topRounding = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                val portraitThumb = ChatLinkPreviewDisplay.usesPortraitThumbnail(preview)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (portraitThumb) {
                                    Modifier.aspectRatio(ChatLinkPreviewDisplay.portraitAspectRatio)
                                } else {
                                    Modifier.height(ChatLinkPreviewDisplay.defaultThumbnailHeightDp.dp)
                                },
                            )
                            .clip(topRounding),
                ) {
                    AsyncImage(
                        model = ottoImageRequest(LocalContext.current, thumb),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(Modifier.padding(12.dp)) {
                preview.title
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                preview.description
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        footerLeft,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.squad_chat_open_link),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatComposerReplyBanner(
    authorLabel: String,
    snippet: String,
    onCancel: () -> Unit,
    onTapReplyTo: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (onTapReplyTo != null) {
                        Modifier.clickable(
                            onClick = onTapReplyTo,
                            onClickLabel = stringResource(R.string.chat_reply_quote_go_to_message),
                            role = Role.Button,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                stringResource(R.string.chat_replying_to, authorLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                snippet,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.accessibility_cancel_reply),
            )
        }
    }
}

@Composable
private fun SquadChatReplyQuoteRow(
    replyTo: to.ottomot.driftd.core.network.dto.ChatReplyPreviewDto,
    mine: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val quoteAccent = mapAccentComposeColor(replyTo.sender?.mapAccentKey)
    val name =
        replyTo.sender
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.squads_owner_unknown)
    val trimmed = replyTo.body.trim()
    val quoted =
        when {
            trimmed.isNotEmpty() -> trimmed
            replyTo.videoAttachment != null -> stringResource(R.string.chat_reply_video)
            !replyTo.imageUrl.isNullOrBlank() -> stringResource(ChatImageUrlDisplay.replySnippetResId(replyTo.imageUrl))
            else -> ""
        }
    if (quoted.isEmpty() && replyTo.imageUrl.isNullOrBlank() && replyTo.videoAttachment == null) return
    Row(
        modifier =
            Modifier
                .padding(bottom = 8.dp)
                .widthIn(max = 280.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(
                            onClick = onClick,
                            onClickLabel = stringResource(R.string.chat_reply_quote_go_to_message),
                            role = Role.Button,
                        )
                    } else {
                        Modifier
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(quoteAccent),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = quoteAccent,
            )
            if (quoted.isNotEmpty()) {
                Text(
                    quoted,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (mine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquadChatReactionsRow(
    reactions: List<ChatMessageReactionDto>,
    mine: Boolean,
    onTap: (() -> Unit)? = null,
) {
    if (reactions.isEmpty()) return
    val tallies =
        reactions
            .groupingBy { it.emoji }
            .eachCount()
            .entries
            .sortedBy { it.key }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp)
                .padding(top = 6.dp)
                .then(
                    if (onTap != null) {
                        Modifier.clickable(
                            onClick = onTap,
                            onClickLabel = stringResource(R.string.accessibility_squad_chat_open_reactions),
                        )
                    } else {
                        Modifier
                    },
                )
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement =
            if (mine) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        tallies.forEach { (emoji, count) ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(emoji, style = MaterialTheme.typography.labelMedium)
                    if (count > 1) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SquadChatSystemNoticeRow(body: String) {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        ) {
            Text(
                trimmed,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SquadChatBubbleTile(
    msg: CircleChatMessageDto,
    myUserId: String?,
    meUser: UserDto?,
    squadScopedEvents: List<EventDto>,
    allUpcomingEvents: List<EventDto>,
    chatAttachmentHydration: Map<String, EventDto>,
    eventRsvpSubmittingEventId: String?,
    onSubmitEventRsvp: (String, String) -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onOpenSharedDrive: (CircleChatDriveAttachmentDto) -> Unit = {},
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
    onLongPress: ((CircleChatMessageDto) -> Unit)? = null,
    onReactionsTap: ((CircleChatMessageDto) -> Unit)? = null,
    onTapReplyQuote: ((String) -> Unit)? = null,
    memberDisplayNamesByUserId: Map<String, String> = emptyMap(),
    contacts: List<UserDto> = emptyList(),
    onTapPeerAvatar: ((String) -> Unit)? = null,
    onCancelVideoUpload: ((String) -> Unit)? = null,
) {
    val senderUserId = (msg.senderUserId as String?)?.trim().orEmpty()
    val avatarUserId = senderUserId.ifEmpty { "chat-${msg.id}" }
    val senderDisplayName =
        resolveSquadMemberDisplayName(
            userId = senderUserId,
            sender = msg.sender,
            memberDisplayNamesByUserId = memberDisplayNamesByUserId,
            contacts = contacts,
            meUser = meUser,
        )
    val senderContact =
        contacts.find { ottoUserIdsEqual(it.id, senderUserId) }
            ?: meUser?.takeIf { ottoUserIdsEqual(it.id, senderUserId) }
    val senderAvatarUrl = msg.sender?.avatarUrl ?: senderContact?.avatarUrl
    val senderMapAccentKey = msg.sender?.mapAccentKey ?: senderContact?.mapAccentKey
    val mine = !myUserId.isNullOrBlank() && ottoUserIdsEqual(senderUserId, myUserId)
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val mergedEvt =
        remember(msg.eventAttachment?.eventId, squadScopedEvents, allUpcomingEvents, chatAttachmentHydration) {
            mergeChatEvent(squadScopedEvents, allUpcomingEvents, chatAttachmentHydration, msg.eventAttachment)
        }

    val accent = mapAccentComposeColor(senderMapAccentKey)
    val zoneId = ZoneId.systemDefault()
    fun fmtTime(inst: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId).format(inst)

    val bodyNonBlank = msg.body.isNotBlank()
    val videoAttachment = msg.videoAttachment
    val attachmentImage =
        msg.imageUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }
    var fullscreenPhotoUrl by remember(msg.id, attachmentImage) { mutableStateOf<String?>(null) }
    val uploadStates by ChatVideoUploadState.pendingByClientMessageId.collectAsState()
    val clientMessageId = msg.clientMessageId?.trim().orEmpty()
    val pendingUpload = clientMessageId.takeIf { it.isNotEmpty() }?.let { uploadStates[it] }
    val isVideoUploadPending = msg.id.startsWith("pending-") && videoAttachment != null

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (!mine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .then(
                                    if (onTapPeerAvatar != null && senderUserId.isNotEmpty()) {
                                        Modifier.clickable(
                                            onClickLabel = stringResource(R.string.map_member_profile_view_profile),
                                            onClick = { onTapPeerAvatar.invoke(senderUserId) },
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .border(
                                    width = 2.dp,
                                    color =
                                        accent.copy(alpha = 0.55f),
                                    shape = CircleShape,
                                )
                                .clip(CircleShape)
                                .background(accent),
                    ) {
                        UserProfileAvatar(
                            displayName = senderDisplayName,
                            userId = avatarUserId,
                            avatarUrl = senderAvatarUrl,
                            mapAccentKey = senderMapAccentKey,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column {
                        Text(
                            senderDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                        val inst = squadChatInstant(msg.createdAt)
                        if (inst != null) {
                            Text(
                                fmtTime(inst),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (bodyNonBlank || attachmentImage != null || videoAttachment != null) {
                Surface(
                    shape =
                        RoundedCornerShape(
                            topStart = if (mine) 22.dp else 20.dp,
                            topEnd = if (mine) 20.dp else 22.dp,
                            bottomStart = 22.dp,
                            bottomEnd = 22.dp,
                        ),
                    color =
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        Modifier.widthIn(min = 0.dp, max = 320.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        msg.replyTo?.let { rt ->
                            val parentId = msg.replyToMessageId?.trim().orEmpty()
                            val canJump =
                                parentId.isNotEmpty() &&
                                    !ottoUserIdsEqual(parentId, msg.id) &&
                                    onTapReplyQuote != null
                            SquadChatReplyQuoteRow(
                                replyTo = rt,
                                mine = mine,
                                onClick =
                                    if (canJump) {
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTapReplyQuote.invoke(parentId)
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                        if (bodyNonBlank) {
                            val trimmedBody = msg.body.trim()
                            val baseColor =
                                if (mine) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            val mentionColor =
                                if (mine) Color(0xFFC8E6C9) else Color(0xFF2E7D32)
                            val ms = msg.mentions.orEmpty()
                            val linkColor =
                                if (mine) Color.White else MaterialTheme.colorScheme.primary
                            ChatBodyText(
                                text =
                                    chatBodyWithMentionsAndLinks(
                                        body = trimmedBody,
                                        mentions = ms,
                                        displayNameByUserId = memberDisplayNamesByUserId,
                                        baseColor = baseColor,
                                        mentionColor = mentionColor,
                                        linkColor = linkColor,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                onOpenUrl = { ctx.openExternalHttpUrl(it) },
                                onOpenMentionUserId = { userId ->
                                    if (userId != SquadChatAllMention.USER_ID) {
                                        onTapPeerAvatar?.invoke(userId)
                                    }
                                },
                                onLongPress =
                                    onLongPress?.let { lp ->
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lp.invoke(msg)
                                        }
                                    },
                            )
                        }
                        attachmentImage?.let { url ->
                            if (bodyNonBlank) Spacer(Modifier.height(8.dp))
                            ChatFeedPhotoAttachmentView(
                                url = url,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    fullscreenPhotoUrl = url
                                },
                                onLongPress =
                                    onLongPress?.let { lp ->
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lp.invoke(msg)
                                        }
                                    },
                            )
                        }
                        videoAttachment?.let { att ->
                            if (bodyNonBlank || attachmentImage != null) Spacer(Modifier.height(8.dp))
                            ChatFeedVideoAttachmentView(
                                attachment = att,
                                messageId = msg.id,
                                localThumbnail = pendingUpload?.thumbnailBitmap,
                                uploadProgress = pendingUpload?.progress,
                                isUploadPending = isVideoUploadPending,
                                onCancelUpload =
                                    if (isVideoUploadPending && clientMessageId.isNotEmpty() && onCancelVideoUpload != null) {
                                        { onCancelVideoUpload.invoke(clientMessageId) }
                                    } else {
                                        null
                                    },
                                onLongPress =
                                    onLongPress?.let { lp ->
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lp.invoke(msg)
                                        }
                                    },
                            )
                        }
                    }
                }
            }

            msg.eventAttachment?.let { att ->
                val firstName = senderDisplayName.split(" ").firstOrNull() ?: senderDisplayName
                if (att.isParentDeleted) {
                    SquadChatUnavailableShareCard(
                        sharedHeader = "shared an event",
                        deletedMessage = "This event was deleted",
                        icon = Icons.Outlined.CalendarMonth,
                        sharedByFirstName = firstName,
                        messageCreatedAt = msg.createdAt,
                    )
                } else {
                    SquadChatRichEventCard(
                        mergedEvent = mergedEvt,
                        attachment = att,
                        eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                        onSubmitEventRsvp = onSubmitEventRsvp,
                        onViewEvent = { onOpenEventDetail(att.eventId) },
                    )
                }
            }

            msg.driveAttachment?.let { att ->
                val firstName = senderDisplayName.split(" ").firstOrNull() ?: senderDisplayName
                if (att.isParentDeleted) {
                    SquadChatUnavailableShareCard(
                        sharedHeader = "shared a drive",
                        deletedMessage = "This drive was deleted",
                        icon = Icons.Outlined.DirectionsCar,
                        sharedByFirstName = firstName,
                        messageCreatedAt = msg.createdAt,
                    )
                } else {
                    SquadChatRichDriveCard(
                        attachment = att,
                        sharedByFirstName = firstName,
                        messageCreatedAt = msg.createdAt,
                        onViewDrive = { onOpenSharedDrive(att) },
                    )
                }
            }

            msg.placeAttachment?.let { att ->
                val firstName = senderDisplayName.split(" ").firstOrNull() ?: senderDisplayName
                if (att.isParentDeleted) {
                    SquadChatUnavailableShareCard(
                        sharedHeader = "shared a place",
                        deletedMessage = "This place was deleted",
                        icon = Icons.Outlined.LocationOn,
                        sharedByFirstName = firstName,
                        messageCreatedAt = msg.createdAt,
                    )
                } else {
                    SquadChatRichPlaceCard(
                        attachment = att,
                        messageId = msg.id,
                        sharedByFirstName = firstName,
                        messageCreatedAt = msg.createdAt,
                        onViewPlace = { onOpenSharedPlace(att, msg.id) },
                    )
                }
            }

            msg.linkPreview?.let { lp ->
                when (lp.status) {
                    "pending" ->
                        Text(
                            stringResource(R.string.squad_chat_preview_loading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )

                    "failed" ->
                        Text(
                            stringResource(R.string.squad_chat_preview_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )

                    else -> Unit
                }
                if (lp.isRenderable()) {
                    val resolved = lp.linkActionUrl().orEmpty()
                    SquadChatLinkPreviewTail(preview = lp) {
                        ctx.openExternalHttpUrl(resolved)
                    }
                }
            }

            SquadChatReactionsRow(
                msg.reactions,
                mine,
                onTap =
                    if (onReactionsTap != null && msg.reactions.isNotEmpty()) {
                        { onReactionsTap.invoke(msg) }
                    } else {
                        null
                    },
            )

            if (mine) {
                val inst = squadChatInstant(msg.createdAt)
                if (inst != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        fmtTime(inst),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }

    fullscreenPhotoUrl?.let { url ->
        ChatFullscreenPhotoDialog(
            url = url,
            onDismiss = { fullscreenPhotoUrl = null },
        )
    }
}

@Composable
private fun SquadChatTimelineRow(
    row: SquadChatTimelineItem,
    myUserId: String?,
    meUser: UserDto?,
    squadScopedEvents: List<EventDto>,
    allUpcomingEvents: List<EventDto>,
    chatAttachmentHydration: Map<String, EventDto>,
    eventRsvpSubmittingEventId: String?,
    onSubmitEventRsvp: (String, String) -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onOpenSharedDrive: (CircleChatDriveAttachmentDto) -> Unit = {},
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
    onLongPressBubble: ((CircleChatMessageDto) -> Unit)? = null,
    onReactionsTap: ((CircleChatMessageDto) -> Unit)? = null,
    onTapReplyQuote: ((String) -> Unit)? = null,
    memberDisplayNamesByUserId: Map<String, String> = emptyMap(),
    contacts: List<UserDto> = emptyList(),
    onTapPeerAvatar: ((String) -> Unit)? = null,
    onCancelVideoUpload: ((String) -> Unit)? = null,
) {
    when (row) {
        is SquadChatTimelineItem.DaySeparator ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        is SquadChatTimelineItem.SystemNotice ->
            SquadChatSystemNoticeRow(row.msg.body)

        is SquadChatTimelineItem.Bubble ->
            SquadChatBubbleTile(
                msg = row.msg,
                myUserId = myUserId,
                meUser = meUser,
                squadScopedEvents = squadScopedEvents,
                allUpcomingEvents = allUpcomingEvents,
                chatAttachmentHydration = chatAttachmentHydration,
                eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                onSubmitEventRsvp = onSubmitEventRsvp,
                onOpenEventDetail = onOpenEventDetail,
                onOpenSharedDrive = onOpenSharedDrive,
                onOpenSharedPlace = onOpenSharedPlace,
                onLongPress = onLongPressBubble,
                onReactionsTap = onReactionsTap,
                onTapReplyQuote = onTapReplyQuote,
                memberDisplayNamesByUserId = memberDisplayNamesByUserId,
                contacts = contacts,
                onTapPeerAvatar = onTapPeerAvatar,
                onCancelVideoUpload = onCancelVideoUpload,
            )
    }
}

@Composable
private fun SquadAddMemberRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    onClick = onClick,
                    onClickLabel = stringResource(R.string.accessibility_squad_add_member),
                ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.squad_add_member),
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

@Composable
private fun SquadMembersListRow(
    memberUserId: String,
    contacts: List<UserDto>,
    meUser: UserDto? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    subtitleOverride: String? = null,
    showNavigateChevron: Boolean = true,
    presenceStatusDotColor: Color? = null,
    roleBadge: String? = null,
) {
    val user =
        contacts.find { ottoUserIdsEqual(it.id, memberUserId) }
            ?: meUser?.takeIf { ottoUserIdsEqual(it.id, memberUserId) }
    val displayName =
        resolveSquadMemberDisplayName(
            userId = memberUserId,
            sender = null,
            memberDisplayNamesByUserId = emptyMap(),
            contacts = contacts,
            meUser = meUser,
        )
    val accent = mapAccentComposeColor(user?.mapAccentKey)
    val updatedUnknown = stringResource(R.string.squad_member_updated_unknown)
    val updatedSubtitle =
        subtitleOverride
            ?: (relativeAgoFromIso(user?.lastPresenceAt)?.let { ago ->
                stringResource(R.string.squad_member_updated_format, ago)
            } ?: updatedUnknown)

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .border(2.dp, accent, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape),
            ) {
                UserProfileAvatar(
                    displayName = displayName,
                    userId = memberUserId,
                    avatarUrl = user?.avatarUrl,
                    mapAccentKey = user?.mapAccentKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(presenceStatusDotColor ?: PresenceLifecycleDotColors.Offline)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    displayName,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                roleBadge?.takeIf { it.isNotBlank() }?.let { badge ->
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }
            }
            Text(
                updatedSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showNavigateChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val SquadInviteAccent = Color(0xFFB340FF)

@Composable
private fun SquadInviteActionButton(
    title: String,
    busyTitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    OutlinedCard(
        onClick = {
            SquadInviteHaptics.buttonTap(haptic, view)
            onClick()
        },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = Color.White.copy(alpha = 0.05f),
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.dp,
                    color = SquadInviteAccent,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = SquadInviteAccent,
                )
            }
            Text(
                if (isBusy) busyTitle else title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignupInviteBalanceCard(
    remaining: Int?,
    isLoading: Boolean,
    earnAtNextLevelCount: Int?,
    nextLevelDisplayName: String?,
    modifier: Modifier = Modifier,
) {
    val earnMorePurple = Color(0xFFB340FF)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                isLoading && remaining == null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SquadInviteAccent,
                        )
                        Text(
                            stringResource(R.string.squad_signup_invites_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                remaining != null -> {
                    Text(
                        when {
                            remaining == 0 ->
                                stringResource(R.string.squad_signup_invites_none)
                            remaining == 1 ->
                                stringResource(R.string.squad_signup_invites_available_one, remaining)
                            else ->
                                stringResource(R.string.squad_signup_invites_available_other, remaining)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (remaining > 0) {
                                Color.White
                            } else {
                                Color(0xFFF2994A)
                            },
                    )
                    Text(
                        stringResource(R.string.squad_signup_invites_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    if (remaining == 0) {
                        val earnCount = earnAtNextLevelCount?.takeIf { it > 0 }
                        val levelName = nextLevelDisplayName?.trim()?.takeIf { it.isNotEmpty() }
                        if (earnCount != null && levelName != null) {
                            Text(
                                if (earnCount == 1) {
                                    stringResource(
                                        R.string.squad_signup_invites_earn_at_level_one,
                                        earnCount,
                                        levelName,
                                    )
                                } else {
                                    stringResource(
                                        R.string.squad_signup_invites_earn_at_level_other,
                                        earnCount,
                                        levelName,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = earnMorePurple,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Members roster + invite block for [SquadNotificationSettingsDialog] (iOS Add to Squad parity). */
@Composable
internal fun SquadNotificationSettingsMembersSection(
    circle: CircleDto,
    contacts: List<UserDto>,
    meUser: UserDto?,
    myUserId: String?,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
    allCircles: List<CircleDto>,
    inviteUi: SquadSettingsInviteUi,
    onPrefetchInvite: () -> Unit,
    onCopyInviteLink: () -> Unit,
    onInviteBySmsShare: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onInviteLookupUser: (userId: String, phone: String) -> Unit,
    onAddMember: (userId: String) -> Unit,
    onInviteViaSms: (phone: String) -> Unit,
    onOpenMemberProfile: (String) -> Unit,
    settingsSectionTitleColor: Color,
) {
    val c = circle
    val ownerFlag =
        !myUserId.isNullOrBlank() && ottoUserIdsEqual(c.ownerId, myUserId)
    val inviteIntoViewRequester =
        remember(c.id) { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var phoneDraft by rememberSaveable(c.id) { mutableStateOf("") }

    val memberUserIds =
        remember(c.members) {
            c.members.orEmpty().map { it.userId }.toSet()
        }
    val nameSearchMatches =
        remember(contacts, phoneDraft, memberUserIds, myUserId) {
            inviteNameSearchFromContacts(contacts, phoneDraft, myUserId, memberUserIds)
        }
    val squadMateIds =
        remember(allCircles, myUserId) {
            squadMatesFromAllCircles(allCircles, contacts, myUserId).map { it.userId }.toSet()
        }
    val shareBusy = inviteUi.busy
    val shareSignupInviteActionsEnabled = inviteUi.signupInviteRemaining?.let { it > 0 } == true
    val copyActionEnabled =
        shareSignupInviteActionsEnabled &&
            shareBusy != SquadShareInviteBusy.SMS &&
            shareBusy != SquadShareInviteBusy.COPY
    val smsActionEnabled =
        shareSignupInviteActionsEnabled &&
            shareBusy != SquadShareInviteBusy.COPY &&
            shareBusy != SquadShareInviteBusy.SMS

    val sortedMembers =
        remember(c.id, c.members, c.ownerId) {
            (c.members ?: emptyList()).sortedByDescending { member ->
                when {
                    ottoUserIdsEqual(member.userId, c.ownerId) -> 2
                    member.role.equals("admin", ignoreCase = true) -> 1
                    else -> 0
                }
            }
        }
    val ownerWho =
        when {
            !myUserId.isNullOrBlank() && ottoUserIdsEqual(c.ownerId, myUserId) ->
                stringResource(R.string.squads_created_by_you)
            else ->
                contacts
                    .find { ottoUserIdsEqual(it.id, c.ownerId) }
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: shortenId(c.ownerId)
        }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.squad_detail_tab_members).uppercase(Locale.getDefault()),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
            color = settingsSectionTitleColor,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        if (ownerFlag) {
            SquadAddMemberRow(
                onClick = {
                    scope.launch {
                        inviteIntoViewRequester.bringIntoView()
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        Text(
            stringResource(
                R.string.squads_created_by_format,
                ownerWho,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color =
                MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 2.dp)) {
                sortedMembers.forEachIndexed { idx, member ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    horizontal = 12.dp,
                                ),
                            color =
                                MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.08f),
                        )
                    }
                    SquadMembersListRow(
                        memberUserId = member.userId,
                        contacts = contacts,
                        meUser = meUser,
                        presenceStatusDotColor =
                            presenceMembersByCircleId[c.id]
                                ?.firstOrNull {
                                    ottoUserIdsEqual(it.userId, member.userId)
                                }
                                ?.let { presenceLifecycleDotColor(it) },
                        onClick = { onOpenMemberProfile(member.userId) },
                        roleBadge =
                            when {
                                ottoUserIdsEqual(member.userId, c.ownerId) ->
                                    stringResource(R.string.squad_member_role_owner)
                                member.role.equals("admin", ignoreCase = true) ->
                                    stringResource(R.string.squad_member_role_admin)
                                else -> null
                            },
                    )
                }
            }
        }

        if (squadCanInvite(myUserId, c)) {
            LaunchedEffect(c.id, inviteUi.signupInviteRemaining) {
                val remaining = inviteUi.signupInviteRemaining
                if (remaining != null && remaining > 0) {
                    onPrefetchInvite()
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(inviteIntoViewRequester),
            ) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.squad_add_to_squad_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.squad_add_to_squad_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(14.dp))
                SignupInviteBalanceCard(
                    remaining = inviteUi.signupInviteRemaining,
                    isLoading = inviteUi.signupInviteBalanceLoading,
                    earnAtNextLevelCount = inviteUi.signupInviteEarnAtNextLevelCount,
                    nextLevelDisplayName = inviteUi.signupInviteNextLevelDisplayName,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SquadInviteActionButton(
                        title = stringResource(R.string.squad_invite_copy_link),
                        busyTitle = stringResource(R.string.squad_invite_copying),
                        icon = Icons.Outlined.Link,
                        isBusy = shareBusy == SquadShareInviteBusy.COPY,
                        enabled = copyActionEnabled,
                        onClick = onCopyInviteLink,
                        modifier = Modifier.weight(1f),
                    )
                    SquadInviteActionButton(
                        title = stringResource(R.string.squad_invite_by_sms),
                        busyTitle = stringResource(R.string.squad_invite_opening_sms),
                        icon = Icons.AutoMirrored.Outlined.Message,
                        isBusy = shareBusy == SquadShareInviteBusy.SMS,
                        enabled = smsActionEnabled,
                        onClick = onInviteBySmsShare,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.squad_invite_search_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(13.dp),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(13.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = SquadInviteAccent,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = phoneDraft,
                        onValueChange = {
                            phoneDraft = it
                            onSearchQueryChanged(it)
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.squad_invite_search_placeholder),
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = SquadInviteAccent,
                            ),
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inviteUi.lookupLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = SquadInviteAccent,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SquadInviteAccent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (isPhonePrimarySquadInviteQuery(phoneDraft)) {
                    val lookupUser = inviteUi.lookupUser
                    if (lookupUser != null) {
                        SquadInviteSearchResultCard(
                            title = lookupUser.displayName,
                            subtitle = lookupUser.phoneNumber ?: phoneDraft.trim(),
                            badge = stringResource(R.string.squad_invite_on_driftd),
                            actionLabel =
                                if (ottoUserIdsEqual(inviteUi.workingUserId, lookupUser.id)) {
                                    stringResource(R.string.squad_invite_sending)
                                } else {
                                    stringResource(R.string.squad_invite_phone_button)
                                },
                            actionBusy = ottoUserIdsEqual(inviteUi.workingUserId, lookupUser.id),
                            memberAlreadyIn =
                                c.members.orEmpty().any { ottoUserIdsEqual(it.userId, lookupUser.id) },
                            inSquadLabel = stringResource(R.string.squad_invite_in_squad),
                            onAction = {
                                onInviteLookupUser(
                                    lookupUser.id,
                                    lookupUser.phoneNumber ?: phoneDraft.trim(),
                                )
                            },
                        )
                    } else if (inviteUi.lookupAttempted && phoneDraft.isNotBlank()) {
                        SquadInviteSearchResultCard(
                            title = phoneDraft.trim(),
                            subtitle = stringResource(R.string.squad_invite_not_on_driftd),
                            badge = null,
                            actionLabel =
                                if (inviteUi.smsInviteOpening) {
                                    stringResource(R.string.squad_invite_opening_sms)
                                } else {
                                    stringResource(R.string.squad_invite_via_sms)
                                },
                            actionBusy = inviteUi.smsInviteOpening,
                            memberAlreadyIn = false,
                            inSquadLabel = "",
                            onAction = { onInviteViaSms(phoneDraft.trim()) },
                        )
                    }
                } else if (phoneDraft.trim().length >= 2) {
                    if (nameSearchMatches.isEmpty()) {
                        Text(
                            stringResource(R.string.squad_invite_name_no_matches_extended),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    } else {
                        nameSearchMatches.forEach { user ->
                            val subtitle =
                                if (squadMateIds.contains(user.id)) {
                                    stringResource(R.string.squad_invite_from_your_squads)
                                } else {
                                    stringResource(R.string.squad_invite_on_driftd)
                                }
                            SquadInviteSearchResultCard(
                                title = user.displayName,
                                subtitle = subtitle,
                                badge = stringResource(R.string.squad_invite_on_driftd),
                                actionLabel =
                                    if (ottoUserIdsEqual(inviteUi.workingUserId, user.id)) {
                                        stringResource(R.string.squad_invite_adding)
                                    } else {
                                        stringResource(R.string.squad_add_member_button)
                                    },
                                actionBusy = ottoUserIdsEqual(inviteUi.workingUserId, user.id),
                                memberAlreadyIn =
                                    c.members.orEmpty().any { ottoUserIdsEqual(it.userId, user.id) },
                                inSquadLabel = stringResource(R.string.squad_invite_in_squad),
                                onAction = { onAddMember(user.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                inviteUi.statusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SquadInviteSearchResultCard(
    title: String,
    subtitle: String,
    badge: String?,
    actionLabel: String,
    actionBusy: Boolean,
    memberAlreadyIn: Boolean,
    inSquadLabel: String,
    onAction: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                badge?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            if (memberAlreadyIn) {
                Text(
                    inSquadLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF34C759),
                )
            } else {
                Button(
                    onClick = {
                        SquadInviteHaptics.buttonTap(haptic, view)
                        onAction()
                    },
                    enabled = !actionBusy,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SquadInviteAccent,
                            contentColor = Color.White,
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (actionBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

private data class SquadChatReactionSheetRow(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val mapAccentKey: String?,
    val emoji: String,
)

private fun squadChatReactionSheetRows(
    reactions: List<ChatMessageReactionDto>,
    memberDisplayNamesByUserId: Map<String, String>,
    contacts: List<UserDto>,
): List<SquadChatReactionSheetRow> {
    val locale = Locale.getDefault()
    return reactions
        .map { reaction ->
            val uid = reaction.userId.trim()
            val label =
                resolveSquadMemberDisplayName(
                    userId = uid,
                    sender = reaction.user,
                    memberDisplayNamesByUserId = memberDisplayNamesByUserId,
                    contacts = contacts,
                )
            val avatar =
                reaction.user?.avatarUrl
                    ?.let { MediaUrlResolver.resolve(it)?.toString() }
                    ?: contacts
                        .find { ottoUserIdsEqual(it.id, uid) }
                        ?.avatarUrl
                        ?.let { MediaUrlResolver.resolve(it)?.toString() }
            val accentKey =
                reaction.user?.mapAccentKey
                    ?: contacts.find { ottoUserIdsEqual(it.id, uid) }?.mapAccentKey
            SquadChatReactionSheetRow(
                userId = uid,
                displayName = label,
                avatarUrl = avatar,
                mapAccentKey = accentKey,
                emoji = reaction.emoji,
            )
        }
        .sortedBy { it.displayName.lowercase(locale) }
}

@Composable
private fun SquadChatReactionParticipantRow(row: SquadChatReactionSheetRow) {
    val accent = mapAccentComposeColor(row.mapAccentKey)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .border(2.dp, accent, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape),
            ) {
                UserProfileAvatar(
                    displayName = row.displayName,
                    userId = row.userId,
                    avatarUrl = row.avatarUrl,
                    mapAccentKey = row.mapAccentKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            row.displayName,
            modifier = Modifier.weight(1f),
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            row.emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

private fun squadChatReactionsSheetContentMaxHeight(rowCount: Int): Dp {
    val n = rowCount.coerceAtLeast(1)
    val base = 96.dp
    val perRow = 68.dp
    return minOf(620.dp, base + perRow * n)
}

@Composable
private fun SquadChatMessageReactionsSheetContent(
    reactions: List<ChatMessageReactionDto>,
    memberDisplayNamesByUserId: Map<String, String>,
    contacts: List<UserDto>,
) {
    val rows =
        remember(reactions, memberDisplayNamesByUserId, contacts) {
            squadChatReactionSheetRows(reactions, memberDisplayNamesByUserId, contacts)
        }
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = squadChatReactionsSheetContentMaxHeight(rows.size))
            .verticalScroll(scroll)
            .ottoBottomSheetContent()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.squad_chat_reactions_sheet_title),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp, bottom = 8.dp),
        )
        for (row in rows) {
            SquadChatReactionParticipantRow(row)
        }
    }
}

internal data class SquadMateNamePick(
    val userId: String,
    val displayName: String,
)

internal fun squadMatesFromAllCircles(
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    myUserId: String?,
): List<SquadMateNamePick> {
    val meId =
        myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val byId = LinkedHashMap<String, String>()
    for (c in circles) {
        for (m in c.members.orEmpty()) {
            val uid = m.userId.trim().takeIf { it.isNotEmpty() } ?: continue
            if (ottoUserIdsEqual(uid, meId)) continue
            if (byId.containsKey(uid)) continue
            val dn =
                contacts
                    .find { ottoUserIdsEqual(it.id, uid) }
                    ?.displayName
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                    ?: shortenId(uid)
            byId[uid] = dn
        }
    }
    return byId.entries
        .map { SquadMateNamePick(userId = it.key, displayName = it.value) }
        .sortedBy { it.displayName.lowercase(Locale.US) }
}

/** Digits-only (10+) → phone invite; any letter → name search across squad mates (iOS parity). */
internal fun isSquadInvitePhoneQuery(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return false
    if (t.any { it.isLetter() }) return false
    return t.count { it.isDigit() } >= 10
}

internal fun squadMateNameSearchMatches(
    picks: List<SquadMateNamePick>,
    query: String,
): List<SquadMateNamePick> {
    val q = query.trim()
    if (q.length < 2 || isSquadInvitePhoneQuery(q)) return emptyList()
    return picks.filter { it.displayName.contains(q, ignoreCase = true) }
}

private fun squadGoingCountForEvent(
    event: EventDto,
    circle: CircleDto,
    meUser: UserDto?,
): Int {
    val squadMemberIds = circle.members.orEmpty().map { it.userId }.toSet()
    val goingIds =
        event.contactsGoing
            .orEmpty()
            .map { it.id }
            .filter { it.isNotBlank() && it in squadMemberIds }
            .toMutableSet()
    val meUserId = meUser?.id
    if (event.currentUserRsvp == OttoShellUiState.RsvpGoing && meUserId != null && meUserId in squadMemberIds) {
        goingIds.add(meUserId)
    }
    return goingIds.size
}

@Composable
private fun SquadChatPinnedUpcomingEventCard(
    event: EventDto,
    circle: CircleDto,
    meUser: UserDto?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pinnedSubtitleTick by remember(event.id) { mutableIntStateOf(0) }
    LaunchedEffect(event.id) {
        while (true) {
            delay(30_000)
            pinnedSubtitleTick++
        }
    }
    val zone = ZoneId.systemDefault()
    val goingCount = squadGoingCountForEvent(event, circle, meUser)
    val highlightPhrase =
        remember(pinnedSubtitleTick, event.id, event.startsAt, event.endsAt) {
            squadChatPinnedEventHighlightPhrase(event, Instant.now(), zone)
        }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val statusGreen = Color(0xFF34C759)
    val subtitleAnnotated =
        buildAnnotatedString {
            if (highlightPhrase != null) {
                withStyle(SpanStyle(color = statusGreen)) {
                    append(highlightPhrase)
                }
                withStyle(SpanStyle(color = muted)) {
                    append(" · $goingCount going")
                }
            } else {
                withStyle(SpanStyle(color = muted)) {
                    append(formatIsoForDisplay(event.startsAt) + " · $goingCount going")
                }
            }
        }
    ElevatedCard(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.squad_detail_next_up_pin) + ": " + event.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitleAnnotated,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.dismiss_snack),
                    tint = muted,
                )
            }
        }
    }
}

/** Squad detail: title row shown in [OttoShell] TopAppBar (iOS-style inline header). */
@Composable
internal fun SquadCircleDetailShellTopBarTitle(
    circle: CircleDto?,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    onOpenSquadSettings: (() -> Unit)? = null,
) {
    if (circle == null) {
        Text(
            text = stringResource(R.string.squads_loading),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val avatarUrl =
        circle.photoUrl
            ?.let { MediaUrlResolver.resolve(it) }
            ?.toString()
    val presenceMembers = presenceMembersForCircleId(presenceMembersByCircleId, circle.id)
    val memberSummary = squadMemberPresenceSummary(
        memberCount = circle.members.orEmpty().size,
        presenceMembers = presenceMembers,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = ottoImageRequest(LocalContext.current, avatarUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (onOpenSquadSettings != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                onClick = onOpenSquadSettings,
                                onClickLabel = stringResource(R.string.accessibility_squad_settings),
                                role = Role.Button,
                            )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                circle.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                memberSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** DM overlay/thread: title row in [OttoShell] TopAppBar (mirrors [SquadCircleDetailShellTopBarTitle]). */
@Composable
internal fun DmShellTopBarTitle(
    dm: DirectMessagesOverlayUi,
    circles: List<CircleDto>,
    myUserId: String?,
) {
    if (dm.selectedConversationId == null) {
        Text(
            text = stringResource(R.string.messages_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val conversation =
        dm.conversations.firstOrNull { ottoUserIdsEqual(it.id, dm.selectedConversationId) }
    val other = conversation?.otherUser
    val peerName =
        dm.threadTitle.trim().takeIf { it.isNotEmpty() }
            ?: other?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: shortenId(dm.selectedConversationId.orEmpty())
    val subtitle = sharedCircleNameWithPeer(circles, myUserId, other?.id)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            UserProfileAvatar(
                displayName = other?.displayName,
                userId = other?.id ?: dm.selectedConversationId.orEmpty(),
                avatarUrl = other?.avatarUrl,
                mapAccentKey = other?.mapAccentKey,
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                textColor = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                peerName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { sub ->
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun DmShellTopBarActions(
    dm: DirectMessagesOverlayUi,
    myUserId: String?,
    meUser: UserDto?,
    onCompose: () -> Unit,
    onReportConcern: () -> Unit,
    onBlockDmPeer: (String) -> Unit,
    onUnblockDmPeer: (String) -> Unit,
) {
    val peerUserId =
        remember(dm.selectedConversationId, dm.conversations) {
            val cid = dm.selectedConversationId?.trim()?.takeIf { it.isNotBlank() } ?: return@remember null
            dm.conversations.firstOrNull { ottoUserIdsEqual(it.id, cid) }
                ?.otherUser?.id?.trim()?.takeIf { it.isNotBlank() }
        }
    val blockedPeerIds =
        remember(meUser) {
            meUser?.blockedUserIds.orEmpty().mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
        }
    var actionsExpanded by remember(dm.selectedConversationId) { mutableStateOf(false) }
    var confirmBlockDmPeer by remember(dm.selectedConversationId) { mutableStateOf(false) }
    val mySafe = myUserId?.trim()?.takeIf { it.isNotBlank() }
    val pid = peerUserId

    if (dm.selectedConversationId == null) {
        IconButton(onClick = onCompose) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.dm_compose_new_message_cd),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    if (pid != null && mySafe != null && !ottoUserIdsEqual(pid, mySafe)) {
        Box {
            IconButton(onClick = { actionsExpanded = true }) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = stringResource(R.string.messages_overflow_menu),
                )
            }
            DropdownMenu(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.safety_report_concern)) },
                    onClick = {
                        actionsExpanded = false
                        onReportConcern()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Forum, contentDescription = null)
                    },
                )
                val alreadyBlocked = blockedPeerIds.any { ottoUserIdsEqual(it, pid) }
                if (alreadyBlocked) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.safety_unblock_member)) },
                        onClick = {
                            actionsExpanded = false
                            onUnblockDmPeer(pid)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.safety_block_member)) },
                        onClick = {
                            actionsExpanded = false
                            confirmBlockDmPeer = true
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Block, contentDescription = null)
                        },
                    )
                }
            }
        }
    }

    if (confirmBlockDmPeer && pid != null) {
        AlertDialog(
            onDismissRequest = { confirmBlockDmPeer = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBlockDmPeer = false
                        onBlockDmPeer(pid)
                    },
                ) {
                    Text(stringResource(R.string.safety_block_member))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockDmPeer = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.safety_block_confirm_title)) },
            text = { Text(stringResource(R.string.safety_block_confirm_message)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CircleDetailOverlay(
    detailUi: CircleDetailUi,
    allCircles: List<CircleDto>,
    allUpcomingEvents: List<EventDto>,
    myUserId: String?,
    meUser: UserDto?,
    contacts: List<UserDto>,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    nextUpDismissals: List<NextUpEventDismissalDto> = emptyList(),
    chatAttachmentHydratedEventsById: Map<String, EventDto> = emptyMap(),
    onPrefetchChatAttachmentEvents: (Set<String>) -> Unit = {},
    eventRsvpSubmittingEventId: String? = null,
    onSubmitEventRsvp: (String, String) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    onSendChat: (String, ChatPendingComposerAttachment?) -> Unit,
    onSendChatVideo: (String, ChatSendVideoAttachment) -> Unit = { _, _ -> },
    onCancelCircleChatVideoUpload: (String) -> Unit = {},
    onInviteByPhone: (String, String) -> Unit,
    onAddMemberByUserId: (String, String) -> Unit,
    onCreateInviteLink: (String) -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onPostChatReaction: (messageId: String, emoji: String) -> Unit,
    onSetChatReplyTo: (CircleChatMessageDto) -> Unit,
    onClearChatReplyTo: () -> Unit,
    onBeginCircleChatEdit: (String) -> Unit,
    onCancelCircleChatEdit: () -> Unit,
    onDeleteCircleChatMessage: (String) -> Unit,
    onFetchOlderCircleChatForQuoteJump: (String) -> Unit = {},
    onLoadNextUpEventDismissals: (String, List<String>) -> Unit = { _, _ -> },
    onDismissNextUpEventBanner: (String, String, String) -> Unit = { _, _, _ -> },
    onRefreshSquadGrid: (String) -> Unit = {},
    onOpenMemberProfile: (String) -> Unit = {},
    onCreateSquadScopedEvent: CreateSquadScopedEventWithCircle,
    onShareCreatedSquadEventToChat: ShareCreatedSquadEventToChat,
    onSquadEventGeocodeWarning: () -> Unit,
    onChatProfileMessagePeer: (String) -> Unit,
    onChatProfileViewPeer: (String) -> Unit,
    onChatProfileOpenSquad: (String) -> Unit,
    onNavigateToOwnProfileTab: () -> Unit,
    onKickCircleMember: (circleId: String, userId: String) -> Unit = { _, _ -> },
    onPatchCircleMemberRole: (circleId: String, userId: String, role: String) -> Unit = { _, _, _ -> },
    onApplyEventAttachedSquads: (String, List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit = { _, _ -> },
    onOpenSharedDrive: (CircleChatDriveAttachmentDto, String?) -> Unit = { _, _ -> },
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
    onSquadChatUnreadPositionChanged: (circleId: String, chatTabVisible: Boolean, pinnedToBottom: Boolean, lastReadMessageId: String?) -> Unit = { _, _, _, _ -> },
    unreadChatCountByCircleId: Map<String, Int> = emptyMap(),
    pendingSquadChatFocusTick: Long = 0L,
    pendingSquadChatFocusCircleId: String? = null,
) {
    val circle = detailUi.circle
    var squadEventsSubTab by rememberSaveable(detailUi.circleId) { mutableIntStateOf(0) }
    var showAddSquadEvent by rememberSaveable(detailUi.circleId) { mutableStateOf(false) }
    var pendingShareCreatedEvent by remember(detailUi.circleId) { mutableStateOf<EventDto?>(null) }
    var shareCreatedEventBusy by remember(detailUi.circleId) { mutableStateOf(false) }
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val hideChatPinnedEvent = configuration.screenWidthDp <= 360 || configuration.screenHeightDp <= 700
    var chatDraft by rememberSaveable(detailUi.circleId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var showComposerEventSheet by rememberSaveable(detailUi.circleId) { mutableStateOf(false) }
    var showSquadKlipyGifPicker by rememberSaveable(detailUi.circleId) { mutableStateOf(false) }
    var pendingChatAttachment by remember(detailUi.circleId) { mutableStateOf<ChatPendingComposerAttachment?>(null) }
    var isLoadingLocationAttachment by remember(detailUi.circleId) { mutableStateOf(false) }
    var showChatLocationPrimer by remember(detailUi.circleId) { mutableStateOf(false) }
    var showChatLocationDenied by remember(detailUi.circleId) { mutableStateOf(false) }
    var squadChatAttachError by remember(detailUi.circleId) { mutableStateOf<String?>(null) }
    var squadChatVideoLimitDialog by remember(detailUi.circleId) { mutableStateOf<String?>(null) }
    val locationReader = remember(ctx) { ctx.applicationContext.appContainer().approximateLocationReader }

    val todayLbl = stringResource(R.string.squad_detail_separator_today)
    val yesterdayLbl = stringResource(R.string.squad_detail_separator_yesterday)
    val timeline =
        remember(detailUi.chatMessages, todayLbl, yesterdayLbl) {
            squadChatTimelineItems(detailUi.chatMessages, todayLbl, yesterdayLbl)
        }

    val chatListState = rememberLazyListState()
    val circleChatScrollScope = rememberCoroutineScope()

    fun attachSquadLocationIfAuthorized() {
        beginChatComposerLocationAttachment(
            scope = circleChatScrollScope,
            context = ctx,
            locationReader = locationReader,
            onLoadingChanged = { isLoadingLocationAttachment = it },
            onSuccess = { pendingChatAttachment = it },
            onError = { squadChatAttachError = it },
        )
    }

    fun beginSquadLocationAttachmentFlow() {
        val fineGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            fineGranted || coarseGranted -> attachSquadLocationIfAuthorized()
            else -> showChatLocationPrimer = true
        }
    }

    var pendingQuoteJumpId by remember(detailUi.circleId) { mutableStateOf<String?>(null) }
    LaunchedEffect(timeline, pendingQuoteJumpId) {
        val target = pendingQuoteJumpId ?: return@LaunchedEffect
        val tIdx =
            timeline.indexOfFirst { row ->
                row is SquadChatTimelineItem.Bubble &&
                    ottoUserIdsEqual(row.msg.id, target)
            }
        if (tIdx >= 0) {
            chatListState.animateScrollToItem(tIdx)
            pendingQuoteJumpId = null
        }
    }
    var squadChatJumpReady by remember(detailUi.circleId) { mutableStateOf(false) }
    val squadDensity = LocalDensity.current
    val squadPinThresholdPx =
        remember(squadDensity.density) {
            chatPinToLatestThresholdPx(squadDensity.density)
        }
    val pickSquadChatPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            circleChatScrollScope.launch {
                squadChatAttachError = null
                val prep = withContext(Dispatchers.IO) { prepareChatSendPhotoAttachment(ctx, uri) }
                prep.fold(
                    onSuccess = {
                        pendingChatAttachment =
                            ChatPendingComposerAttachment(
                                kind = ChatPendingComposerAttachmentKind.Photo,
                                photo = it,
                            )
                    },
                    onFailure = { e ->
                        squadChatAttachError =
                            e.message?.takeIf { m -> m.isNotBlank() }
                                ?: ctx.getString(R.string.chat_attachment_read_failed)
                    },
                )
            }
        }
    val pickSquadChatVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            circleChatScrollScope.launch {
                squadChatAttachError = null
                val validation = withContext(Dispatchers.IO) { ChatVideoUploadPrep.validate(ctx, uri) }
                if (validation.isFailure) {
                    squadChatVideoLimitDialog =
                        validation.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                            ?: ctx.getString(R.string.chat_attachment_read_failed)
                    return@launch
                }
                val prep = withContext(Dispatchers.IO) { ChatVideoUploadPrep.prepare(ctx, uri) }
                prep.fold(
                    onSuccess = {
                        pendingChatAttachment =
                            ChatPendingComposerAttachment(
                                kind = ChatPendingComposerAttachmentKind.Video,
                                video = it,
                            )
                    },
                    onFailure = { e ->
                        squadChatVideoLimitDialog =
                            e.message?.takeIf { m -> m.isNotBlank() }
                                ?: ctx.getString(R.string.chat_attachment_read_failed)
                    },
                )
            }
        }
    LaunchedEffect(detailUi.chatEditingMessageId, detailUi.circleId) {
        if (!detailUi.chatEditingMessageId.isNullOrBlank()) {
            pendingChatAttachment = null
            squadChatAttachError = null
        }
    }
    KlipyGifPickerSheet(
        visible = showSquadKlipyGifPicker,
        customerId = myUserId.orEmpty(),
        onSelect = { selection, searchQuery ->
            pendingChatAttachment =
                ChatPendingComposerAttachment(
                    kind = ChatPendingComposerAttachmentKind.KlipyGif,
                    klipyGif = selection,
                    klipySearchQuery = searchQuery,
                )
            squadChatAttachError = null
            showSquadKlipyGifPicker = false
        },
        onDismiss = { showSquadKlipyGifPicker = false },
    )
    ChatTimelineAutoScrollEffect(
        timeline = timeline,
        listState = chatListState,
        conversationKey = detailUi.circleId,
        myUserId = myUserId,
        pinThresholdPx = squadPinThresholdPx,
        isHistoryLoading = detailUi.chatLoading,
        onJumpReadyChange = { squadChatJumpReady = it },
    )

    val officialSquadEvents =
        remember(detailUi.squadScopedEvents) {
            detailUi.squadScopedEvents.sortedWith(compareBy { eventStartsAtSortKey(it) })
        }
    val featuredBrowseEvents =
        remember(allUpcomingEvents) {
            val now = Instant.now()
            allUpcomingEvents
                .filter { event -> eventCheckInEndsAtInstant(event)?.let { !it.isBefore(now) } ?: false }
                .sortedWith(compareBy { eventStartsAtSortKey(it) })
        }
    val squadEvents = if (squadEventsSubTab == 0) officialSquadEvents else featuredBrowseEvents

    LaunchedEffect(detailUi.chatMessages, detailUi.squadScopedEvents, allUpcomingEvents, detailUi.circleId) {
        val ids =
            detailUi.chatMessages.mapNotNull { m ->
                m.eventAttachment?.eventId?.trim()?.takeIf { it.isNotEmpty() }
            }.toSet()
        onPrefetchChatAttachmentEvents(ids)
    }

    val pinnedEvent =
        remember(detailUi.circleId, detailUi.squadScopedEvents, allUpcomingEvents, circle, meUser) {
            val nonNullCircle = circle ?: return@remember null
            val merged = LinkedHashMap<String, EventDto>()
            for (e in allUpcomingEvents) {
                merged[e.id] = e
            }
            for (e in detailUi.squadScopedEvents) {
                merged[e.id] = e
            }
            val now = Instant.now()
            val weekEnd = now.plus(Duration.ofDays(7))
            val forThisSquad =
                merged.values
                    .asSequence()
                    .filter { eventIsNextUpPinnedForCircle(it, detailUi.circleId) }
                    .filter { eventQualifiesForSquadNextUpPin(it, nonNullCircle, meUser) }
            forThisSquad
                .filter { !it.startsAt.isNullOrBlank() }
                .filter { isWithinEventCheckInWindow(it, now) }
                .minByOrNull { eventStartsAtSortKey(it) }
                ?: forThisSquad
                    .filter { !it.startsAt.isNullOrBlank() }
                    .filter { eventStartsAtSortKey(it).isAfter(now) }
                    .filter { !eventStartsAtSortKey(it).isAfter(weekEnd) }
                    .minByOrNull { eventStartsAtSortKey(it) }
        }
    var autoHiddenPinnedEventId by rememberSaveable(detailUi.circleId) { mutableStateOf<String?>(null) }
    val nowForPinnedVisibility = Instant.now()
    val pinnedZone = ZoneId.systemDefault()
    val visiblePinnedEvent =
        visibleNextUpEvent(
            event = pinnedEvent,
            dismissals = nextUpDismissals,
            autoHiddenEventId = autoHiddenPinnedEventId,
            now = nowForPinnedVisibility,
            zone = pinnedZone,
        )
    var renderedPinnedEvent by remember(detailUi.circleId) { mutableStateOf<EventDto?>(null) }
    var pinnedEventPresented by remember(detailUi.circleId) { mutableStateOf(false) }

    LaunchedEffect(visiblePinnedEvent?.id) {
        if (visiblePinnedEvent != null) {
            renderedPinnedEvent = visiblePinnedEvent
            pinnedEventPresented = true
        } else {
            pinnedEventPresented = false
            delay(260)
            renderedPinnedEvent = null
        }
    }

    LaunchedEffect(detailUi.circleId, pinnedEvent?.id) {
        val event = pinnedEvent ?: return@LaunchedEffect
        onLoadNextUpEventDismissals(detailUi.circleId, listOf(event.id))
        delay(30_000)
        autoHiddenPinnedEventId = event.id
    }

    val jumpCircleChatToQuotedMessage: (String) -> Unit =
        remember(timeline, circleChatScrollScope, chatListState, detailUi.circleId, onFetchOlderCircleChatForQuoteJump) {
            { rawId ->
                val parentId = rawId.trim()
                if (parentId.isNotEmpty()) {
                    val tIdx =
                        timeline.indexOfFirst { row ->
                            row is SquadChatTimelineItem.Bubble &&
                                ottoUserIdsEqual(row.msg.id, parentId)
                        }
                    if (tIdx >= 0) {
                        circleChatScrollScope.launch {
                            chatListState.animateScrollToItem(tIdx)
                        }
                    } else {
                        pendingQuoteJumpId = parentId
                        onFetchOlderCircleChatForQuoteJump(detailUi.circleId)
                    }
                }
            }
        }

    var detailSectionIdx by rememberSaveable(detailUi.circleId) {
        mutableIntStateOf(SquadDetailSection.Chat.ordinal)
    }

    LaunchedEffect(pendingSquadChatFocusTick, pendingSquadChatFocusCircleId, detailUi.circleId) {
        if (pendingSquadChatFocusTick <= 0L) return@LaunchedEffect
        val target = pendingSquadChatFocusCircleId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (ottoUserIdsEqual(detailUi.circleId, target)) {
            detailSectionIdx = SquadDetailSection.Chat.ordinal
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (circle == null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.squad_detail_missing), color = MaterialTheme.colorScheme.error)
            }
        } else {
            val c = circle
            val composerHaptic = LocalHapticFeedback.current
            val detailSection =
                SquadDetailSection.entries[detailSectionIdx.coerceIn(0, SquadDetailSection.entries.lastIndex)]
            LaunchedEffect(
                detailSection,
                detailUi.chatMessages,
                detailUi.circleId,
                chatListState.firstVisibleItemIndex,
                chatListState.layoutInfo.totalItemsCount,
            ) {
                val isChat = detailSection == SquadDetailSection.Chat
                val pinned = chatListState.isPinnedToLatestChat(squadPinThresholdPx)
                val lastId = detailUi.chatMessages.lastOrNull()?.id
                onSquadChatUnreadPositionChanged(detailUi.circleId, isChat, pinned, lastId)
            }
            LaunchedEffect(detailSection, detailUi.circleId) {
                if (detailSection == SquadDetailSection.Grid) {
                    onRefreshSquadGrid(detailUi.circleId)
                }
            }
            var longPressMessage by remember(detailUi.circleId) { mutableStateOf<CircleChatMessageDto?>(null) }
            var reactionsDetailMessage by remember(detailUi.circleId) { mutableStateOf<CircleChatMessageDto?>(null) }
            val chatReactionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var mentionPickerVisible by remember(detailUi.circleId) { mutableStateOf(false) }
            var mentionAnchor by remember(detailUi.circleId) { mutableIntStateOf(-1) }
            var mentionFilter by remember(detailUi.circleId) { mutableStateOf("") }
            var pendingDeleteCircleMessage by remember(detailUi.circleId) { mutableStateOf<CircleChatMessageDto?>(null) }
            var prevCircleChatEditingId by remember(detailUi.circleId) { mutableStateOf<String?>(null) }
            val squadChatEditFocusRequester = remember(detailUi.circleId) { FocusRequester() }
            LaunchedEffect(detailUi.chatEditingMessageId, detailUi.chatMessages, detailUi.circleId) {
                val id = detailUi.chatEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
                if (id != null) {
                    val msg =
                        detailUi.chatMessages.find { m -> ottoUserIdsEqual(m.id, id) }
                            ?: return@LaunchedEffect
                    chatDraft = TextFieldValue(msg.body, TextRange(msg.body.length))
                } else if (prevCircleChatEditingId != null) {
                    chatDraft = TextFieldValue("")
                }
                prevCircleChatEditingId = id
            }
            LaunchedEffect(detailUi.chatEditingMessageId) {
                if (!detailUi.chatEditingMessageId.isNullOrBlank()) {
                    delay(80)
                    squadChatEditFocusRequester.requestFocus()
                }
            }
            val squadEditingId = detailUi.chatEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
            val squadEditingMessage =
                squadEditingId?.let { eid ->
                    detailUi.chatMessages.find { m -> ottoUserIdsEqual(m.id, eid) }
                }
            val squadEditPreviewRaw = squadEditingMessage?.body.orEmpty()
            val squadEditBaselineTrimmed = squadEditPreviewRaw.trim()

            val allMentionLabel = stringResource(R.string.squad_mention_all_label)
            val memberDisplayNamesByUserId =
                remember(c.id, c.members, contacts, allMentionLabel) {
                    val base =
                        (c.members ?: emptyList()).mapNotNull { m ->
                            val uid = m.userId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                            val dn =
                                contacts
                                    .find { ottoUserIdsEqual(it.id, uid) }
                                    ?.displayName
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: return@mapNotNull null
                            uid to dn
                        }.toMap()
                    base + (SquadChatAllMention.USER_ID to allMentionLabel)
                }

            Box(
                Modifier
                    .fillMaxSize(),
            ) {
            Column(Modifier.fillMaxSize()) {
                SquadDetailIosTabBar(
                    selectedIdx = detailSectionIdx,
                    onSelect = { detailSectionIdx = it },
                    chatUnreadCount = unreadChatCountForCircle(unreadChatCountByCircleId, detailUi.circleId),
                )

                HorizontalDivider(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                )

                OttoTabbedPager(
                    pageCount = SquadDetailSection.entries.size,
                    selectedIdx = detailSectionIdx,
                    onSelect = { detailSectionIdx = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    retainOffscreenPages = true,
                ) { page ->
                    when (SquadDetailSection.entries[page]) {
                    SquadDetailSection.Chat ->
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize()) {
                            if (detailUi.chatLoading && detailUi.chatMessages.isEmpty()) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Spacer(Modifier.height(12.dp))
                            }

                            AnimatedVisibility(
                                visible = !hideChatPinnedEvent && pinnedEventPresented && renderedPinnedEvent != null,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                renderedPinnedEvent?.let { ev ->
                                    SquadChatPinnedUpcomingEventCard(
                                        event = ev,
                                        circle = c,
                                        meUser = meUser,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        onOpen = { onOpenEventDetail(ev.id) },
                                        onDismiss = {
                                            autoHiddenPinnedEventId = ev.id
                                            onDismissNextUpEventBanner(
                                                detailUi.circleId,
                                                ev.id,
                                                nextUpDismissalContextForEvent(ev, Instant.now(), pinnedZone),
                                            )
                                        },
                                    )
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                LazyColumn(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .alpha(
                                                if (squadChatJumpReady || timeline.isEmpty()) 1f else 0f,
                                            ),
                                    state = chatListState,
                                    contentPadding = PaddingValues(bottom = 12.dp, top = 4.dp),
                                ) {
                                itemsIndexed(timeline, key = { ix, row ->
                                    when (row) {
                                        is SquadChatTimelineItem.Bubble -> row.msg.id + "-$ix"
                                        is SquadChatTimelineItem.SystemNotice -> row.msg.id + "-$ix"
                                        is SquadChatTimelineItem.DaySeparator -> "sep-$ix-${row.label}"
                                    }
                                }) { _, row ->
                                    SquadChatTimelineRow(
                                        row = row,
                                        myUserId = myUserId,
                                        meUser = meUser,
                                        squadScopedEvents = squadEvents,
                                        allUpcomingEvents = allUpcomingEvents,
                                        chatAttachmentHydration = chatAttachmentHydratedEventsById,
                                        eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                                        onSubmitEventRsvp = onSubmitEventRsvp,
                                        onOpenEventDetail = onOpenEventDetail,
                                        onOpenSharedDrive = { att ->
                                            if (!att.isParentDeleted) {
                                                onOpenSharedDrive(att, detailUi.circleId)
                                            }
                                        },
                                        onOpenSharedPlace = { att, messageId ->
                                            if (!att.isParentDeleted) {
                                                onOpenSharedPlace(att, messageId)
                                            }
                                        },
                                        onLongPressBubble = { msg -> longPressMessage = msg },
                                        onReactionsTap = { msg -> reactionsDetailMessage = msg },
                                        onTapReplyQuote = jumpCircleChatToQuotedMessage,
                                        memberDisplayNamesByUserId = memberDisplayNamesByUserId,
                                        contacts = contacts,
                                        onTapPeerAvatar = { uid ->
                                            if (!myUserId.isNullOrBlank() && ottoUserIdsEqual(uid, myUserId)) {
                                                onNavigateToOwnProfileTab()
                                            } else {
                                                onChatProfileViewPeer(uid)
                                            }
                                        },
                                        onCancelVideoUpload = onCancelCircleChatVideoUpload,
                                    )
                                }
                            }
                                if (!squadChatJumpReady && timeline.isNotEmpty()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                detailUi.chatSnack?.takeIf { it.isNotBlank() }?.let { err ->
                                    Text(
                                        err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                }
                                squadChatAttachError?.takeIf { it.isNotBlank() }?.let { err ->
                                    Text(
                                        err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                }

                                if (mentionPickerVisible) {
                                    val locale = Locale.getDefault()
                                    val fl = mentionFilter.lowercase(locale)
                                    val includeAll =
                                        SquadChatAllMention.WIRE_LABEL.startsWith(fl)
                                    val memberIds =
                                        (c.members ?: emptyList()).map { it.userId.trim() }.filter { it.isNotEmpty() }.toSet()
                                    val candidates =
                                        contacts
                                            .asSequence()
                                            .filter { u -> memberIds.any { mid -> ottoUserIdsEqual(mid, u.id) } }
                                            .filter { u ->
                                                myUserId.isNullOrBlank() || !ottoUserIdsEqual(u.id, myUserId)
                                            }
                                            .filter { u ->
                                                mentionFilter.isEmpty() ||
                                                    u.displayName.contains(mentionFilter, ignoreCase = true)
                                            }
                                            .sortedBy { it.displayName.lowercase(locale) }
                                            .toList()
                                    if (includeAll || candidates.isNotEmpty()) {
                                        val mentionRows =
                                            (if (includeAll) 1 else 0) + candidates.size
                                        val visibleMentionRowCap = 5
                                        val mentionListHeight =
                                            56.dp *
                                                minOf(visibleMentionRowCap, mentionRows)
                                        Surface(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(mentionListHeight)
                                                    .padding(bottom = 6.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            tonalElevation = 2.dp,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                if (includeAll) {
                                                    items(listOf("all"), key = { "all_row" }) { _ ->
                                                        Column(Modifier.fillMaxWidth()) {
                                                            Row(
                                                                Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        val newText =
                                                                            replaceMentionTokenForSquadChat(
                                                                                chatDraft.text,
                                                                                mentionAnchor,
                                                                                SquadChatAllMention.WIRE_LABEL,
                                                                            )
                                                                        chatDraft =
                                                                            TextFieldValue(
                                                                                newText,
                                                                                TextRange(newText.length),
                                                                            )
                                                                        mentionPickerVisible = false
                                                                    }
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            ) {
                                                                Box(
                                                                    modifier =
                                                                        Modifier
                                                                            .size(40.dp)
                                                                            .clip(CircleShape)
                                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Icon(
                                                                        Icons.Outlined.Groups,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(22.dp),
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                    )
                                                                }
                                                                Text(
                                                                    allMentionLabel,
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                            HorizontalDivider(
                                                                Modifier.padding(horizontal = 12.dp),
                                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                            )
                                                        }
                                                    }
                                                }
                                                items(candidates, key = { it.id }) { u ->
                                                    Row(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                val newText =
                                                                    replaceMentionTokenForSquadChat(
                                                                        chatDraft.text,
                                                                        mentionAnchor,
                                                                        u.displayName.trim(),
                                                                    )
                                                                chatDraft =
                                                                    TextFieldValue(
                                                                        newText,
                                                                        TextRange(newText.length),
                                                                    )
                                                                mentionPickerVisible = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    ) {
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .size(40.dp)
                                                                    .clip(CircleShape),
                                                        ) {
                                                            UserProfileAvatar(
                                                                displayName = u.displayName,
                                                                userId = u.id,
                                                                avatarUrl = u.avatarUrl,
                                                                mapAccentKey = u.mapAccentKey,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        }
                                                        Text(
                                                            u.displayName.trim(),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    HorizontalDivider(
                                                        Modifier.padding(horizontal = 12.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OttoChatComposerBar(
                                    value = chatDraft,
                                    onValueChange = { new ->
                                        squadChatAttachError = null
                                        chatDraft = new
                                        val st = computeSquadMentionPickerState(new.text)
                                        mentionPickerVisible = st.visible
                                        mentionAnchor = st.anchor
                                        mentionFilter = st.filter
                                    },
                                    sendBusy = detailUi.chatSendBusy,
                                    showAttachButton = detailUi.chatEditingMessageId.isNullOrBlank(),
                                    enabledAttachmentActions = ChatComposerAttachmentAction.squadChatActions,
                                    pendingAttachment = pendingChatAttachment,
                                    isLoadingLocationAttachment = isLoadingLocationAttachment,
                                    onClearPendingAttachment =
                                        if (pendingChatAttachment != null) {
                                            { pendingChatAttachment = null }
                                        } else {
                                            null
                                        },
                                    onAttachmentAction = { action ->
                                        when (action) {
                                            ChatComposerAttachmentAction.Photo ->
                                                pickSquadChatPhoto.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                )
                                            ChatComposerAttachmentAction.Gif -> {
                                                if (KlipyConfiguration.isConfigured) {
                                                    showSquadKlipyGifPicker = true
                                                } else {
                                                    squadChatAttachError = ctx.getString(R.string.klipy_picker_unavailable)
                                                }
                                            }
                                            ChatComposerAttachmentAction.Video ->
                                                pickSquadChatVideo.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                                )
                                            ChatComposerAttachmentAction.Location -> beginSquadLocationAttachmentFlow()
                                            ChatComposerAttachmentAction.CreateEvent -> showComposerEventSheet = true
                                        }
                                    },
                                    replyBannerKey = detailUi.chatReplyTo?.id,
                                    replyBanner =
                                        detailUi.chatReplyTo?.let { target ->
                                            @Composable {
                                                val auth =
                                                    target.sender
                                                        ?.displayName
                                                        ?.trim()
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?: stringResource(R.string.squads_owner_unknown)
                                                val snip =
                                                    when {
                                                        target.body.trim().isNotEmpty() -> target.body.trim()
                                                        target.videoAttachment != null ->
                                                            stringResource(R.string.chat_reply_video)
                                                        !target.imageUrl.isNullOrBlank() ->
                                                            stringResource(ChatImageUrlDisplay.replySnippetResId(target.imageUrl))
                                                        else -> ""
                                                    }
                                                if (snip.isNotEmpty()) {
                                                    ChatComposerReplyBanner(
                                                        authorLabel = auth,
                                                        snippet = snip,
                                                        onCancel = onClearChatReplyTo,
                                                        onTapReplyTo = {
                                                            composerHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            jumpCircleChatToQuotedMessage(target.id)
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    isEditMode = !detailUi.chatEditingMessageId.isNullOrBlank(),
                                    editPreviewText = squadEditPreviewRaw,
                                    editBaselineTrimmed = squadEditBaselineTrimmed,
                                    onCancelEdit =
                                        if (!detailUi.chatEditingMessageId.isNullOrBlank()) {
                                            onCancelCircleChatEdit
                                        } else {
                                            null
                                        },
                                    focusRequester = squadChatEditFocusRequester,
                                    placeholder = {
                                        Text(
                                            stringResource(
                                                R.string.squad_detail_message_to_format,
                                                c.name,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onSend =
                                        saveSquadChat@{
                                            val t = chatDraft.text.trim()
                                            val editing = !detailUi.chatEditingMessageId.isNullOrBlank()
                                            val attachment = pendingChatAttachment?.takeIf { !editing }
                                            if (t.isEmpty() && attachment == null) return@saveSquadChat
                                            if (editing && t == squadEditBaselineTrimmed) return@saveSquadChat
                                            onSendChat(chatDraft.text, attachment)
                                            if (!editing) {
                                                chatDraft = TextFieldValue("")
                                                pendingChatAttachment = null
                                            }
                                        },
                                )
                            }
                            }

                            val showSquadJumpToLatest by remember(chatListState, squadChatJumpReady, squadPinThresholdPx, timeline.size) {
                                derivedStateOf {
                                    squadChatJumpReady &&
                                        timeline.isNotEmpty() &&
                                        !chatListState.isPinnedToLatestChat(squadPinThresholdPx)
                                }
                            }
                            ChatJumpToLatestFloatingButton(
                                visible = showSquadJumpToLatest,
                                onClick = {
                                    circleChatScrollScope.launch {
                                        if (timeline.isNotEmpty()) {
                                            chatListState.scrollToChatLatestBottom(
                                                timeline.lastIndex,
                                                animate = true,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }

                    SquadDetailSection.Events ->
                        Column(Modifier.fillMaxSize()) {
                            FilledTonalButton(
                                onClick = { showAddSquadEvent = true },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.squad_add_event))
                                }
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = squadEventsSubTab == 0,
                                    onClick = { squadEventsSubTab = 0 },
                                    label = { Text(stringResource(R.string.squad_events_tab_squad)) },
                                )
                                FilterChip(
                                    selected = squadEventsSubTab == 1,
                                    onClick = { squadEventsSubTab = 1 },
                                    label = { Text(stringResource(R.string.squad_events_tab_featured)) },
                                )
                            }
                            when {
                                detailUi.squadScopedEventsLoading && squadEvents.isEmpty() ->
                                    Box(
                                        Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }

                                squadEvents.isEmpty() ->
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squad_detail_events_empty),
                                        icon = Icons.Outlined.CalendarMonth,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )

                                else ->
                                    EventListSectionedLazyColumn(
                                        events = sortEventsForSectionedList(squadEvents),
                                        presentation = EventListSectionedPresentation.Compact,
                                        onEventClick = { onOpenEventDetail(it.id) },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                        horizontalPadding = 14.dp,
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                    ) { event, groupedInSection ->
                                        OttoEventRow(
                                            event = event,
                                            goingCountOverride = squadGoingCountForEvent(event, c, meUser),
                                            showBanner = false,
                                            groupedInSection = groupedInSection,
                                        )
                                    }
                            }
                        }

                    SquadDetailSection.Grid ->
                        SquadGridSection(
                            detailUi = detailUi,
                            onRefresh = { onRefreshSquadGrid(detailUi.circleId) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            longPressMessage?.let { lp ->
                val ownBubble = squadChatMessageOwnedUserBubble(lp, myUserId)
                val canEdit = squadChatMessageEditEligible(lp, myUserId)
                ChatMessageActionsDialog(
                    onDismissRequest = { longPressMessage = null },
                    onReply = { onSetChatReplyTo(lp) },
                    onReaction = { em -> onPostChatReaction(lp.id, em) },
                    onEdit =
                        if (canEdit) {
                            { onBeginCircleChatEdit(lp.id) }
                        } else {
                            null
                        },
                    onDelete =
                        if (ownBubble) {
                            { pendingDeleteCircleMessage = lp }
                        } else {
                            null
                        },
                )
            }
            ChatDeleteSystemAlertEffect(
                victimMessageId = pendingDeleteCircleMessage?.id,
                title = stringResource(R.string.chat_delete_message_confirm_title),
                message = stringResource(R.string.chat_delete_message_confirm_body),
                deleteLabel = stringResource(R.string.chat_delete_message),
                cancelLabel = stringResource(R.string.chat_cancel_edit),
                onDelete = {
                    pendingDeleteCircleMessage?.let { onDeleteCircleChatMessage(it.id) }
                },
                onDismiss = { pendingDeleteCircleMessage = null },
            )
            reactionsDetailMessage?.let { msg ->
                ModalBottomSheet(
                    onDismissRequest = { reactionsDetailMessage = null },
                    sheetState = chatReactionsSheetState,
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                ) {
                    SquadChatMessageReactionsSheetContent(
                        reactions = msg.reactions,
                        memberDisplayNamesByUserId = memberDisplayNamesByUserId,
                        contacts = contacts,
                    )
                }
            }
            }
        }

        if (circle != null) {
            AddSquadEventBottomSheet(
                visible = showAddSquadEvent,
                squadDisplayName =
                    detailUi.circle?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.squad_chat_event_untitled),
                onDismiss = { showAddSquadEvent = false },
                onCreatedEventAwaitingSharePrompt = { pendingShareCreatedEvent = it },
                onSubmit = { payload ->
                    val pin = geocodeEventEditorAddressIfResolvable(ctx, payload.address)
                    if (payload.address.geocodeQuery() != null && pin == null) {
                        onSquadEventGeocodeWarning()
                        return@AddSquadEventBottomSheet Result.failure(IllegalArgumentException("Could not resolve event address"))
                    }
                    onCreateSquadScopedEvent(
                        detailUi.circleId,
                        payload.name,
                        payload.description,
                        payload.startsAt,
                        payload.endsAt,
                        payload.address.label,
                        payload.address.streetAddress,
                        payload.address.city,
                        payload.address.region,
                        payload.address.postalCode,
                        pin?.first,
                        pin?.second,
                        payload.imageBytes,
                        payload.imageContentType,
                    ).map { SquadEventSubmitOutcome.Created(it) }
                },
            )
            AddSquadEventBottomSheet(
                visible = showComposerEventSheet,
                squadDisplayName =
                    detailUi.circle?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.squad_chat_event_untitled),
                onDismiss = { showComposerEventSheet = false },
                onCreatedEventAwaitingSharePrompt = { created ->
                    pendingChatAttachment =
                        ChatPendingComposerAttachment(
                            kind = ChatPendingComposerAttachmentKind.Event,
                            event = created,
                            eventPreviewUrl = chatComposerEventPreviewUrl(created),
                        )
                },
                onSubmit = { payload ->
                    val pin = geocodeEventEditorAddressIfResolvable(ctx, payload.address)
                    if (payload.address.geocodeQuery() != null && pin == null) {
                        onSquadEventGeocodeWarning()
                        return@AddSquadEventBottomSheet Result.failure(IllegalArgumentException("Could not resolve event address"))
                    }
                    onCreateSquadScopedEvent(
                        detailUi.circleId,
                        payload.name,
                        payload.description,
                        payload.startsAt,
                        payload.endsAt,
                        payload.address.label,
                        payload.address.streetAddress,
                        payload.address.city,
                        payload.address.region,
                        payload.address.postalCode,
                        pin?.first,
                        pin?.second,
                        payload.imageBytes,
                        payload.imageContentType,
                    ).map { SquadEventSubmitOutcome.Created(it) }
                },
            )
        }

        ChatComposerLocationPermissionHost(
            showLocationPrimer = showChatLocationPrimer,
            showLocationDeniedModal = showChatLocationDenied,
            onDismissLocationPrimer = { showChatLocationPrimer = false },
            onDismissLocationDenied = { showChatLocationDenied = false },
            onLocationPermissionResult = { granted ->
                if (granted) {
                    attachSquadLocationIfAuthorized()
                } else {
                    showChatLocationDenied = true
                }
            },
        )

        pendingShareCreatedEvent?.let { created ->
            val squadChatLabel =
                detailUi.circle?.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.squad_chat_event_untitled)
            OttoCenteredChoiceDialog(
                visible = true,
                busy = shareCreatedEventBusy,
                onDismissRequest = {
                    if (!shareCreatedEventBusy) pendingShareCreatedEvent = null
                },
                onCloseClick = {
                    if (!shareCreatedEventBusy) pendingShareCreatedEvent = null
                },
                hero = { OttoSquadChatShareHeroGraphic() },
                title = { OttoShareWithSquadAnnotatedTitle(squadChatLabel) },
                body = {
                    Text(
                        text = stringResource(R.string.squad_event_share_body),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = Color.White.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                },
                primaryLabel = stringResource(R.string.squad_event_share_confirm),
                primaryLeadingIcon = Icons.Outlined.Forum,
                onPrimaryClick = {
                    circleChatScrollScope.launch {
                        shareCreatedEventBusy = true
                        val ok =
                            onShareCreatedSquadEventToChat(detailUi.circleId, created.id).isSuccess
                        shareCreatedEventBusy = false
                        if (ok) pendingShareCreatedEvent = null
                    }
                },
                secondaryLabel = stringResource(R.string.squad_event_share_skip),
                secondaryLeadingIcon = Icons.Outlined.Groups,
                onSecondaryClick = {
                    if (!shareCreatedEventBusy) pendingShareCreatedEvent = null
                },
                footerMessage = stringResource(R.string.squad_event_share_footer),
            )
        }

        squadChatVideoLimitDialog?.let { message ->
            AlertDialog(
                onDismissRequest = { squadChatVideoLimitDialog = null },
                title = { Text(stringResource(R.string.chat_video_limit_title)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { squadChatVideoLimitDialog = null }) {
                        Text(stringResource(R.string.squad_invite_link_pilot_ok))
                    }
                },
            )
        }
    }
}

internal fun normalizedSquadMemberRole(
    circle: CircleDto,
    userId: String,
): String {
    if (ottoUserIdsEqual(circle.ownerId, userId)) return "owner"
    return circle.members.orEmpty()
        .find { m -> ottoUserIdsEqual(m.userId, userId) }
        ?.role
        ?.trim()
        ?.lowercase(Locale.US)
        ?: "member"
}

internal data class SquadPeerManagementUiState(
    val promoteToAdmin: Boolean,
    val demoteAdmin: Boolean,
    val removeFromSquad: Boolean,
)

internal fun squadPeerManagementUiState(
    circle: CircleDto?,
    myUserId: String?,
    peerUserId: String,
): SquadPeerManagementUiState? {
    if (circle == null || myUserId.isNullOrBlank()) return null
    if (ottoUserIdsEqual(peerUserId, myUserId)) return null
    val viewer = normalizedSquadMemberRole(circle, myUserId)
    val target = normalizedSquadMemberRole(circle, peerUserId)
    if (viewer != "owner" && viewer != "admin") return null
    val promoteToAdmin = viewer == "owner" && target == "member"
    val demoteAdmin = viewer == "owner" && target == "admin"
    val removeFromSquad =
        target != "owner" && !(viewer == "admin" && target == "admin")
    if (!promoteToAdmin && !demoteAdmin && !removeFromSquad) return null
    return SquadPeerManagementUiState(
        promoteToAdmin = promoteToAdmin,
        demoteAdmin = demoteAdmin,
        removeFromSquad = removeFromSquad,
    )
}

private fun squadCanInvite(
    myUserId: String?,
    circle: CircleDto,
): Boolean {
    if (myUserId.isNullOrBlank()) return false
    if (ottoUserIdsEqual(circle.ownerId, myUserId)) return true
    return circle.members.orEmpty().any { m ->
        ottoUserIdsEqual(m.userId, myUserId) && (m.role == "admin" || m.role == "owner")
    }
}

@Composable
internal fun OttoSquadRow(
    circle: CircleDto,
    contacts: List<UserDto>,
    meUser: UserDto?,
    myUserId: String?,
    modifier: Modifier = Modifier,
    showTrailingChevron: Boolean = true,
    hasUnreadChat: Boolean = false,
    unreadChatCount: Int = if (hasUnreadChat) 1 else 0,
    subtitleOverride: String? = null,
) {
    val avatarUrl =
        circle.photoUrl?.let { MediaUrlResolver.resolve(it) }?.toString()

    val memberCount = circle.members.orEmpty().size
    val subtitleText =
        subtitleOverride
            ?: pluralStringResource(R.plurals.squads_member_count, memberCount, memberCount)

    val memberIds =
        circle.members.orEmpty().map { it.userId }.distinct().take(4)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model =
                    ottoImageRequest(LocalContext.current, avatarUrl),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                circle.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SquadMemberAvatarOverlap(memberIds, contacts, meUser)
            if (unreadChatCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent),
                )
            }
            if (showTrailingChevron) {
                Icon(
                    Icons.AutoMirrored.Outlined.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private val EVENT_DISTANCE_PRESET_MILES = listOf(25, 50, 100)

private fun snapEventDistanceSlider(raw: Float): Float {
    var v = raw.coerceIn(5f, 200f)
    for (p in listOf(25f, 50f, 100f)) {
        if (kotlin.math.abs(v - p) <= 2f) return p
    }
    return kotlin.math.round(v)
}

private data class EventDtoWithDistance(
    val event: EventDto,
    val miles: Double,
)

private fun eventsWithinRadiusMiles(
    events: List<EventDto>,
    fix: LocationFix,
    radiusMiles: Int,
): List<EventDtoWithDistance> {
    val maxMeters = radiusMiles * 1609.34
    return events
        .mapNotNull { ev ->
            if (ev.adminOnly == true) {
                val coords = ev.location?.coordinates
                if (coords != null && coords.size >= 2) {
                    val out = FloatArray(1)
                    Location.distanceBetween(fix.latitude, fix.longitude, coords[1], coords[0], out)
                    return@mapNotNull EventDtoWithDistance(ev, out[0].toDouble() / 1609.34)
                }
                return@mapNotNull EventDtoWithDistance(ev, 0.0)
            }
            val coords = ev.location?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val lng = coords[0]
            val lat = coords[1]
            val out = FloatArray(1)
            Location.distanceBetween(fix.latitude, fix.longitude, lat, lng, out)
            val m = out[0].toDouble()
            if (m > maxMeters) return@mapNotNull null
            EventDtoWithDistance(ev, m / 1609.34)
        }.sortedWith(compareBy { eventStartsAtSortKey(it.event) })
}

private fun mergeUpcomingEventsWithDistance(
    featured: List<EventDtoWithDistance>,
    community: List<EventDtoWithDistance>,
): List<EventDtoWithDistance> {
    val byId = LinkedHashMap<String, EventDtoWithDistance>()
    for (item in featured) {
        byId[item.event.id] = item
    }
    for (item in community) {
        if (!byId.containsKey(item.event.id)) {
            byId[item.event.id] = item
        }
    }
    return byId.values.sortedWith(compareBy { eventStartsAtSortKey(it.event) })
}

private fun formatEventListDistanceMiles(miles: Double): String =
    if (miles < 10) {
        String.format("%.1f mi away (approx.)", miles)
    } else {
        String.format("%.0f mi away (approx.)", miles)
    }

/** Calmer Events-screen palette — muted passive accents. */
private val EventsSelectedPillPurple = Color(0xFF6832CC)
private val EventsMetaMuted = Color.White.copy(alpha = 0.50f)
private val EventsLocationTextMuted = Color.White.copy(alpha = 0.56f)
private val EventsLocationIconMuted = Color.White.copy(alpha = 0.38f)
private val EventsBadgeFill = Color.Black.copy(alpha = 0.78f)
private val EventsBadgeBorder = Color(0xFF7B3DFF).copy(alpha = 0.50f)
private val EventsBadgeText = Color(0xFFAB8AF0)
private val EventsCommunityBadgeBorder = Color(0xFF00BCD4).copy(alpha = 0.50f)
private val EventsCommunityBadgeText = Color(0xFF4DD0E1)
private val EventsCommunityTypePillColor = Color(0xFF00BCD4).copy(alpha = 0.88f)

@Composable
private fun OttoEventsListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onClick),
    ) {
        content()
    }
}

@Composable
private fun EventDistancePillButton(
    title: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "eventDistPillScale",
    )
    val haptic = LocalHapticFeedback.current
    val shape = CircleShape
    val labelColor = if (selected) Color.White else Color.White.copy(alpha = 0.68f)
    Box(
        modifier =
            modifier
                .height(28.dp)
                .scale(scale)
                .clip(shape)
                .background(
                    if (selected) EventsSelectedPillPurple else Color.White.copy(alpha = 0.08f),
                )
                .then(
                    if (!selected) {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                    } else {
                        Modifier
                    },
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (title != null) {
                if (icon != null) {
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OttoEventsUpcomingTabContent(
    allSourceEvents: List<EventDto>,
    filtered: List<EventDtoWithDistance>,
    locationGranted: Boolean,
    deviceLocationFix: LocationFix?,
    clampedDistance: Int,
    isCustomDistance: Boolean,
    onSelectedDistanceChange: (Int) -> Unit,
    onShowCustomDistanceSheet: () -> Unit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distanceByEventId = remember(filtered) { filtered.associate { it.event.id to it.miles } }

    when {
        !locationGranted ->
            EmptyTabMessage(
                text = stringResource(R.string.events_location_required),
                icon = Icons.Outlined.LocationOn,
                modifier = modifier,
            )

        deviceLocationFix == null ->
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.events_finding_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        filtered.isEmpty() ->
            EmptyTabMessage(
                text =
                    if (allSourceEvents.isEmpty()) {
                        stringResource(R.string.events_upcoming_empty_in_range, clampedDistance)
                    } else {
                        stringResource(R.string.events_none_in_range, clampedDistance)
                    },
                icon = Icons.Outlined.CalendarMonth,
                modifier = modifier,
            )

        else ->
            EventListSectionedLazyColumn(
                events = filtered.map { it.event },
                presentation = EventListSectionedPresentation.Featured,
                onEventClick = { onOpenEvent(it.id) },
                modifier = modifier,
                hasListHeader = true,
                contentPadding = PaddingValues(bottom = 16.dp),
                header = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.events_search_within),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (m in EVENT_DISTANCE_PRESET_MILES) {
                                EventDistancePillButton(
                                    title = "$m mi",
                                    icon = null,
                                    selected = !isCustomDistance && clampedDistance == m,
                                    onClick = { onSelectedDistanceChange(m) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            EventDistancePillButton(
                                title = stringResource(R.string.events_custom),
                                icon = Icons.Outlined.Tune,
                                selected = isCustomDistance,
                                onClick = onShowCustomDistanceSheet,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
            ) { event, groupedInSection ->
                OttoEventRow(
                    event = event,
                    distanceMiles = distanceByEventId[event.id],
                    showBanner = event.eventType != "community",
                    groupedInSection = groupedInSection,
                )
            }
    }
}

@Composable
private fun OttoEventsFeaturedTabContent(
    events: List<EventDto>,
    filtered: List<EventDtoWithDistance>,
    locationGranted: Boolean,
    deviceLocationFix: LocationFix?,
    clampedDistance: Int,
    isCustomDistance: Boolean,
    onSelectedDistanceChange: (Int) -> Unit,
    onShowCustomDistanceSheet: () -> Unit,
    onOpenEvent: (String) -> Unit,
    showEventBanner: Boolean = true,
    emptyWhenNoEventsRes: Int = R.string.empty_events,
    modifier: Modifier = Modifier,
) {
    val distanceByEventId = remember(filtered) { filtered.associate { it.event.id to it.miles } }
    val presentation =
        if (showEventBanner) {
            EventListSectionedPresentation.Featured
        } else {
            EventListSectionedPresentation.Compact
        }

    when {
        !locationGranted ->
            EmptyTabMessage(
                text = stringResource(R.string.events_location_required),
                icon = Icons.Outlined.LocationOn,
                modifier = modifier,
            )

        deviceLocationFix == null ->
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.events_finding_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        filtered.isEmpty() ->
            EmptyTabMessage(
                text =
                    if (events.isEmpty()) {
                        stringResource(emptyWhenNoEventsRes)
                    } else {
                        stringResource(R.string.events_none_in_range, clampedDistance)
                    },
                icon = Icons.Outlined.CalendarMonth,
                modifier = modifier,
            )

        else ->
            EventListSectionedLazyColumn(
                events = filtered.map { it.event },
                presentation = presentation,
                onEventClick = { onOpenEvent(it.id) },
                modifier = modifier,
                hasListHeader = true,
                contentPadding = PaddingValues(bottom = 16.dp),
                header = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.events_search_within),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (m in EVENT_DISTANCE_PRESET_MILES) {
                                EventDistancePillButton(
                                    title = "$m mi",
                                    icon = null,
                                    selected = !isCustomDistance && clampedDistance == m,
                                    onClick = { onSelectedDistanceChange(m) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            EventDistancePillButton(
                                title = stringResource(R.string.events_custom),
                                icon = Icons.Outlined.Tune,
                                selected = isCustomDistance,
                                onClick = onShowCustomDistanceSheet,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
            ) { event, groupedInSection ->
                OttoEventRow(
                    event = event,
                    distanceMiles = distanceByEventId[event.id],
                    showBanner = showEventBanner,
                    groupedInSection = groupedInSection,
                )
            }
    }
}

@Composable
private fun OttoEventsSquadsTabContent(
    squadFeedEvents: List<EventDto>,
    circles: List<CircleDto>,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSquadNames = circles.size > 1
    when {
        circles.isEmpty() ->
            EmptyTabMessage(
                text = stringResource(R.string.events_squads_empty_no_squads),
                icon = Icons.Outlined.Groups,
                modifier = modifier,
            )

        squadFeedEvents.isEmpty() ->
            EmptyTabMessage(
                text = stringResource(R.string.events_squads_empty_no_events),
                icon = Icons.Outlined.CalendarMonth,
                modifier = modifier,
            )

        else ->
            EventListSectionedLazyColumn(
                events = sortEventsForSectionedList(squadFeedEvents),
                presentation = EventListSectionedPresentation.Compact,
                onEventClick = { onOpenEvent(it.id) },
                modifier = modifier,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) { event, groupedInSection ->
                val squadNameLine =
                    if (showSquadNames) {
                        event.circleId
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { id -> circles.find { c -> ottoUserIdsEqual(c.id, id) }?.name }
                    } else {
                        null
                    }
                OttoEventRow(
                    event = event,
                    squadNameLine = squadNameLine,
                    showBanner = false,
                    groupedInSection = groupedInSection,
                )
            }
    }
}

@Composable
private fun OttoEventsMyEventsTabContent(
    events: List<EventDto>,
    squadGoingEvents: List<EventDto>,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val myEvents =
        remember(events, squadGoingEvents) {
            mergeMyEvents(events, squadGoingEvents)
        }
    if (myEvents.isEmpty()) {
        OttoEmptyState(
            title = stringResource(R.string.events_mine_empty_title),
            body = stringResource(R.string.events_mine_empty_message),
            icon = Icons.Outlined.CalendarMonth,
            modifier = modifier,
        )
    } else {
        EventListSectionedLazyColumn(
            events = sortEventsForSectionedList(myEvents),
            presentation = EventListSectionedPresentation.Compact,
            onEventClick = { onOpenEvent(it.id) },
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) { event, groupedInSection ->
            OttoEventRow(
                event = event,
                showBanner = false,
                groupedInSection = groupedInSection,
            )
        }
    }
}

private fun mergeMyEvents(
    events: List<EventDto>,
    squadGoingEvents: List<EventDto>,
): List<EventDto> {
    val byId = LinkedHashMap<String, EventDto>()
    events
        .filter { event ->
            event.visibility == "public" &&
                event.currentUserRsvp in
                setOf(OttoShellUiState.RsvpGoing, OttoShellUiState.RsvpInterested)
        }.forEach { byId[it.id] = it }
    squadGoingEvents.forEach { byId[it.id] = it }
    return byId.values.sortedWith(::compareEventsForMainList)
}

/** Events sub-tab index: Upcoming=0, Squads=1, Mine=2. */
private const val EVENTS_SUB_TAB_UPCOMING = 0
private const val EVENTS_SUB_TAB_SQUADS = 1
private const val EVENTS_SUB_TAB_MINE = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OttoEventsPane(
    events: List<EventDto>,
    communityEvents: List<EventDto>,
    squadFeedEvents: List<EventDto>,
    squadGoingEvents: List<EventDto>,
    deviceLocationFix: LocationFix?,
    selectedDistanceMiles: Int,
    onSelectedDistanceChange: (Int) -> Unit,
    detailUi: EventDetailUi?,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    meUser: UserDto?,
    dmConversations: List<DirectConversationDto>,
    onPrefetchDirectMessages: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onDismissDetail: () -> Unit,
    onOpenEventLocationOnMap: (Double, Double, String) -> Unit = { _, _, _ -> },
    onRsvp: (String, String) -> Unit,
    onCheckIn: (String) -> Unit,
    onToggleAutoCheckIn: (Boolean) -> Unit,
    onToggleShowPublicGoingEventsOnProfile: (Boolean) -> Unit = {},
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    onApplyEventAttachedSquads: (String, List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit = { _, _ -> },
    pendingSquadChatFocusTick: Long = 0L,
    pendingEventsMyEventsFocusTick: Long = 0L,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var locationGranted by remember { mutableStateOf(fineLocationGranted(ctx)) }
    var eventsLocationPrimerVisible by remember { mutableStateOf(false) }
    var showEventsLocationDeniedModal by remember { mutableStateOf(false) }
    var didOfferEventsLocationPrimer by rememberSaveable { mutableStateOf(false) }
    val eventsLocationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            locationGranted = granted
            if (granted) {
                ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
            } else {
                showEventsLocationDeniedModal = true
            }
        }
    val clampedDistance = selectedDistanceMiles.coerceIn(5, 200)
    val isCustomDistance = EVENT_DISTANCE_PRESET_MILES.none { it == clampedDistance }
    val filtered =
        remember(events, deviceLocationFix, clampedDistance, locationGranted) {
            val fix = deviceLocationFix
            if (!locationGranted || fix == null) {
                emptyList()
            } else {
                eventsWithinRadiusMiles(events, fix, clampedDistance)
            }
        }
    val communityFiltered =
        remember(communityEvents, deviceLocationFix, clampedDistance, locationGranted) {
            val fix = deviceLocationFix
            if (!locationGranted || fix == null) {
                emptyList()
            } else {
                eventsWithinRadiusMiles(communityEvents, fix, clampedDistance)
            }
        }
    val upcomingMergedFiltered =
        remember(filtered, communityFiltered) {
            mergeUpcomingEventsWithDistance(filtered, communityFiltered)
        }
    var showCustomSheet by remember { mutableStateOf(false) }
    var sheetDraft by remember { mutableFloatStateOf(clampedDistance.toFloat()) }
    var lastSnapHaptic by remember { mutableIntStateOf(-1) }
    val distanceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val distanceHaptic = LocalHapticFeedback.current
    var eventsSubTab by rememberSaveable("eventsSubTab_v2") { mutableIntStateOf(EVENTS_SUB_TAB_UPCOMING) }

    LaunchedEffect(pendingEventsMyEventsFocusTick) {
        if (pendingEventsMyEventsFocusTick > 0L) {
            eventsSubTab = EVENTS_SUB_TAB_MINE
        }
    }

    LaunchedEffect(showCustomSheet) {
        if (showCustomSheet) {
            sheetDraft = clampedDistance.toFloat()
            lastSnapHaptic = -1
        }
    }

    LaunchedEffect(locationGranted, didOfferEventsLocationPrimer) {
        if (!locationGranted && !didOfferEventsLocationPrimer) {
            didOfferEventsLocationPrimer = true
            eventsLocationPrimerVisible = true
        } else if (locationGranted) {
            ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OttoIosUnderlineTabBar(
                labelResIds =
                    listOf(
                        R.string.events_tab_upcoming,
                        R.string.events_tab_squads,
                        R.string.events_tab_mine,
                    ),
                selectedIdx = eventsSubTab,
                onSelect = { eventsSubTab = it },
                selectedLabelWeight = FontWeight.Bold,
            )

            OttoTabbedPager(
                pageCount = 3,
                selectedIdx = eventsSubTab,
                onSelect = { eventsSubTab = it },
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    EVENTS_SUB_TAB_UPCOMING ->
                        OttoEventsUpcomingTabContent(
                            allSourceEvents = events + communityEvents,
                            filtered = upcomingMergedFiltered,
                            locationGranted = locationGranted,
                            deviceLocationFix = deviceLocationFix,
                            clampedDistance = clampedDistance,
                            isCustomDistance = isCustomDistance,
                            onSelectedDistanceChange = onSelectedDistanceChange,
                            onShowCustomDistanceSheet = { showCustomSheet = true },
                            onOpenEvent = onOpenEvent,
                            modifier = Modifier.fillMaxSize(),
                        )

                    EVENTS_SUB_TAB_SQUADS ->
                        OttoEventsSquadsTabContent(
                            squadFeedEvents = squadFeedEvents,
                            circles = circles,
                            onOpenEvent = onOpenEvent,
                            modifier = Modifier.fillMaxSize(),
                        )

                    else ->
                        OttoEventsMyEventsTabContent(
                            events = events + communityEvents,
                            squadGoingEvents = squadGoingEvents,
                            onOpenEvent = onOpenEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
        }

        if (eventsSubTab == EVENTS_SUB_TAB_UPCOMING && showCustomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCustomSheet = false },
                sheetState = distanceSheetState,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        stringResource(R.string.events_distance_sheet_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "${sheetDraft.toInt()} mi",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sheetDraft,
                        onValueChange = { raw ->
                            val snapped = snapEventDistanceSlider(raw)
                            sheetDraft = snapped
                            val sp = listOf(25, 50, 100)
                            for (snap in sp) {
                                if (kotlin.math.abs(snapped - snap) < 1.15f) {
                                    if (lastSnapHaptic != snap) {
                                        lastSnapHaptic = snap
                                        distanceHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    return@Slider
                                }
                            }
                        },
                        valueRange = 5f..200f,
                        colors =
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thumbColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            onSelectedDistanceChange(sheetDraft.roundToInt().coerceIn(5, 200))
                            showCustomSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.events_apply))
                    }
                }
            }
        }

        if (eventsLocationPrimerVisible) {
            OttoEducationDialog(
                visible = true,
                busy = false,
                onDismissRequest = {},
                onCloseClick = {},
                hero = { OttoEducationLocationHero() },
                title = stringResource(R.string.events_location_primer_title),
                body = stringResource(R.string.events_location_primer_body),
                bulletSectionTitle = null,
                bullets = emptyList(),
                footer = stringResource(R.string.events_location_primer_footer),
                primaryLabel = stringResource(R.string.events_location_primer_continue),
                onPrimaryClick = {
                    eventsLocationPrimerVisible = false
                    eventsLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                allowsUnconfirmedDismiss = false,
            )
        }

        if (showEventsLocationDeniedModal) {
            OttoEducationDialog(
                visible = true,
                busy = false,
                onDismissRequest = { showEventsLocationDeniedModal = false },
                onCloseClick = { showEventsLocationDeniedModal = false },
                hero = { OttoEducationLocationHero() },
                title = stringResource(R.string.events_location_permission_modal_title),
                body = stringResource(R.string.events_location_permission_modal_body),
                bulletSectionTitle = null,
                bullets = emptyList(),
                footer = null,
                primaryLabel = stringResource(R.string.location_permission_enable),
                onPrimaryClick = {
                    runCatching {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        ctx.startActivity(intent)
                    }
                    showEventsLocationDeniedModal = false
                },
                secondaryLabel = stringResource(R.string.location_permission_modal_dismiss),
                onSecondaryClick = { showEventsLocationDeniedModal = false },
            )
        }

        detailUi?.let { detail ->
            BackHandler(onBack = onDismissDetail)
            EventDetailOverlay(
                detailUi = detail,
                circles = circles,
                contacts = contacts,
                meUser = meUser,
                dmConversations = dmConversations,
                onPrefetchDirectMessages = onPrefetchDirectMessages,
                sourceCircleId = null,
                presenceMembersByCircleId = presenceMembersByCircleId,
                deviceLocationFix = deviceLocationFix,
                onClose = onDismissDetail,
                onOpenEventLocationOnMap = onOpenEventLocationOnMap,
                onRsvp = onRsvp,
                onCheckIn = onCheckIn,
                onUpdateSquadEvent = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                onDeleteSquadEvent = { Result.failure(UnsupportedOperationException()) },
                onToggleAutoCheckIn = onToggleAutoCheckIn,
                onToggleShowPublicGoingEventsOnProfile = onToggleShowPublicGoingEventsOnProfile,
                postEventShareToChat = postEventShareToChat,
                pendingSquadChatFocusTick = pendingSquadChatFocusTick,
                onEventAssociationsSaved = { squads ->
                    onApplyEventAttachedSquads(detail.eventId, squads)
                },
            )
        }
    }
}

private fun eventListLocationText(event: EventDto, tbd: String): String {
    val label = event.address?.label?.trim().orEmpty()
    if (label.isNotEmpty()) return label
    val cityLine =
        listOfNotNull(event.address?.city, event.address?.region)
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .joinToString(separator = ", ")
    if (cityLine.isNotBlank()) return cityLine
    return tbd
}

private val OttoEventRowCardShape = RoundedCornerShape(16.dp)

private val OttoEventRowTextStyle =
    TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
private fun OttoFixedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        content()
    }
}

@Composable
private fun OttoEventListDateBadge(
    monthAbbr: String,
    dayText: String,
    isCommunityEvent: Boolean = false,
) {
    val badgeShape = RoundedCornerShape(8.dp)
    val badgeBorder = if (isCommunityEvent) EventsCommunityBadgeBorder else EventsBadgeBorder
    val badgeText = if (isCommunityEvent) EventsCommunityBadgeText else EventsBadgeText
    Box(
        modifier =
            Modifier
                .size(width = 42.dp, height = 54.dp)
                .background(EventsBadgeFill, badgeShape)
                .border(width = 1.dp, color = badgeBorder, shape = badgeShape)
                .clip(badgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                monthAbbr,
                style = OttoEventRowTextStyle,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeText,
                maxLines = 1,
            )
            Text(
                dayText,
                style = OttoEventRowTextStyle,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = badgeText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OttoEventRowTypePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .background(Color.White.copy(alpha = 0.055f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = color,
        )
        Text(
            label,
            style = OttoEventRowTextStyle,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun OttoEventBannerHeader(ev: EventDto) {
    val bannerUrl =
        ev.bannerImage?.url?.let { MediaUrlResolver.resolve(it) }?.toString()

    if (bannerUrl != null) {
        AsyncImage(
            model = ottoImageRequest(LocalContext.current, bannerUrl),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OttoEventRowStatusCallout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = color,
        )
        Text(
            label,
            style = OttoEventRowTextStyle,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

private enum class EventListTimingCallout {
    HappeningNow,
    ;

    companion object {
        fun forStartInstant(startInstant: Instant?, zone: ZoneId): EventListTimingCallout? {
            val start = startInstant?.atZone(zone) ?: return null
            val now = ZonedDateTime.now(zone)
            if (!start.isAfter(now)) return HappeningNow
            return null
        }
    }

    @Composable
    fun label(): String = stringResource(R.string.events_happening_now)

    fun color(): Color = Color(0xFF4CAF50).copy(alpha = 0.88f)
}

@Composable
private fun OttoEventRowContent(
    monthAbbr: String,
    dayText: String,
    eventName: String,
    locationLine: String,
    timingCallout: EventListTimingCallout?,
    isUserGoing: Boolean,
    squadNameLine: String?,
    distanceMiles: Double?,
    goingCount: Int,
    isCommunityEvent: Boolean = false,
    showCommunityTypePill: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OttoEventListDateBadge(
            monthAbbr = monthAbbr,
            dayText = dayText,
            isCommunityEvent = isCommunityEvent,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (timingCallout != null || isUserGoing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    timingCallout?.let { callout ->
                        OttoEventRowStatusCallout(
                            icon = Icons.Outlined.Schedule,
                            label = callout.label(),
                            color = callout.color(),
                        )
                    }
                    if (isUserGoing) {
                        OttoEventRowStatusCallout(
                            icon = Icons.Filled.Star,
                            label = stringResource(R.string.events_row_you_going),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                        )
                    }
                }
            }
            if (showCommunityTypePill) {
                OttoEventRowTypePill(
                    icon = Icons.Outlined.Groups,
                    label = stringResource(R.string.events_row_type_community),
                    color = EventsCommunityTypePillColor,
                )
            }
            Text(
                eventName,
                style = OttoEventRowTextStyle,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.96f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            squadNameLine?.takeIf { it.isNotBlank() }?.let { squadName ->
                Text(
                    squadName,
                    style = OttoEventRowTextStyle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = EventsMetaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = EventsLocationIconMuted,
                )
                Text(
                    locationLine,
                    modifier = Modifier.weight(1f),
                    style = OttoEventRowTextStyle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = EventsLocationTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            distanceMiles?.let { dm ->
                Text(
                    formatEventListDistanceMiles(dm),
                    style = OttoEventRowTextStyle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = EventsMetaMuted,
                    maxLines = 1,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "$goingCount",
                style = OttoEventRowTextStyle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
            )
            Text(
                stringResource(R.string.events_row_going),
                style = OttoEventRowTextStyle,
                fontSize = 11.sp,
                color = EventsMetaMuted,
            )
        }
    }
}

@Composable
private fun OttoEventRow(
    event: EventDto,
    distanceMiles: Double? = null,
    goingCountOverride: Int? = null,
    squadNameLine: String? = null,
    showBanner: Boolean = true,
    groupedInSection: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val bannerUrl =
        if (showBanner) {
            event.bannerImage?.url?.let { MediaUrlResolver.resolve(it) }?.toString()
        } else {
            null
        }
    val zone = remember { ZoneId.systemDefault() }
    val startInstant = remember(event.startsAt) { event.startsAt?.let { parseEventInstant(it) } }
    val timingCallout =
        remember(startInstant, zone) {
            EventListTimingCallout.forStartInstant(startInstant, zone)
        }
    val isUserGoing = event.currentUserRsvp == OttoShellUiState.RsvpGoing
    val zonedStart = startInstant?.atZone(zone)
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("dd", Locale.getDefault()) }
    val monthAbbr =
        zonedStart?.format(monthFmt)?.uppercase(Locale.getDefault()).orEmpty().ifEmpty { "—" }
    val dayText = zonedStart?.format(dayFmt).orEmpty().ifEmpty { "–" }
    val tbd = stringResource(R.string.events_location_tbd)
    val locationLine = remember(event, tbd) { eventListLocationText(event, tbd) }
    val goingCount = goingCountOverride ?: (event.rsvpCounts?.going ?: 0)
    val isCommunityEvent = event.eventType == "community"
    val showCommunityTypePill = isCommunityEvent && !showBanner

    OttoFixedFontScale {
        if (showBanner) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(OttoEventRowCardShape)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = OttoEventRowCardShape,
                        )
                        .then(modifier),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (bannerUrl != null) {
                        AsyncImage(
                            model = ottoImageRequest(ctx, bannerUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                                                Color.Black.copy(alpha = 0.88f),
                                            ),
                                        start = Offset.Zero,
                                        end = Offset(800f, 900f),
                                    ),
                                ),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.14f),
                                        Color.Black.copy(alpha = 0.96f),
                                    ),
                                ),
                            ),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(88.dp)
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0f to Color.Transparent,
                                            0.5f to Color.Black.copy(alpha = 0.54f),
                                            1f to Color.Black.copy(alpha = 0.97f),
                                        ),
                                ),
                            ),
                )

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(12.dp),
                ) {
                    OttoEventRowContent(
                        monthAbbr = monthAbbr,
                        dayText = dayText,
                        eventName = event.name,
                        locationLine = locationLine,
                        timingCallout = timingCallout,
                        isUserGoing = isUserGoing,
                        squadNameLine = squadNameLine,
                        distanceMiles = distanceMiles,
                        goingCount = goingCount,
                        isCommunityEvent = isCommunityEvent,
                        showCommunityTypePill = showCommunityTypePill,
                    )
                }
            }
        } else {
            if (groupedInSection) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .then(modifier),
                ) {
                    OttoEventRowContent(
                        monthAbbr = monthAbbr,
                        dayText = dayText,
                        eventName = event.name,
                        locationLine = locationLine,
                        timingCallout = timingCallout,
                        isUserGoing = isUserGoing,
                        squadNameLine = squadNameLine,
                        distanceMiles = distanceMiles,
                        goingCount = goingCount,
                        isCommunityEvent = isCommunityEvent,
                        showCommunityTypePill = showCommunityTypePill,
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(OttoEventRowCardShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = OttoEventRowCardShape,
                            )
                            .background(Color.White.copy(alpha = 0.055f))
                            .padding(12.dp)
                            .then(modifier),
                ) {
                    OttoEventRowContent(
                        monthAbbr = monthAbbr,
                        dayText = dayText,
                        eventName = event.name,
                        locationLine = locationLine,
                        timingCallout = timingCallout,
                        isUserGoing = isUserGoing,
                        squadNameLine = squadNameLine,
                        distanceMiles = distanceMiles,
                        goingCount = goingCount,
                        isCommunityEvent = isCommunityEvent,
                        showCommunityTypePill = showCommunityTypePill,
                    )
                }
            }
        }
    }
}

@Composable
private fun OttoGaragePane(
    cars: List<GarageCarDto>,
    garageSnack: String?,
    onDismissGarageSnack: () -> Unit,
    onAddGarageCar: (String, String, String?, String, Int?, String?, String?, Boolean, ByteArray?, String?) -> Unit,
    onPatchGarageCar: (String, String?, String?, String?, String?, Int?, String?, String?, Boolean?, ByteArray?, String?) -> Unit,
    onDeleteGarageCar: (String) -> Unit,
    onGarageCarPhotoPicked: (String, ByteArray, String) -> Unit,
    onReorderGarage: (List<String>) -> Unit = { },
    garageToolbarAddTicks: Int,
    onConsumeGarageToolbarAddTicks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingPhotoCarId by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val cropGaragePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val carId = pendingPhotoCarId
            pendingPhotoCarId = null
            if (carId == null) return@rememberLauncherForActivityResult
            val outUri = OttoImageCrop.parseOutputUri(result.resultCode, result.data) ?: return@rememberLauncherForActivityResult
            val mime = ctx.contentResolver.getType(outUri) ?: "image/jpeg"
            val bytes =
                ctx.contentResolver.openInputStream(outUri)?.use { inp -> inp.readBytes() }
                    ?: return@rememberLauncherForActivityResult
            if (bytes.isNotEmpty()) {
                onGarageCarPhotoPicked(carId, bytes, mime)
            }
        }

    val pickGaragePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (pendingPhotoCarId == null) return@rememberLauncherForActivityResult
            if (uri == null) {
                pendingPhotoCarId = null
                return@rememberLauncherForActivityResult
            }
            try {
                cropGaragePhoto.launch(OttoImageCrop.uCropIntent(ctx, uri, 16f, 9f))
            } catch (e: Exception) {
                Log.e("OttoImageCrop", "Garage uCrop launch failed", e)
                pendingPhotoCarId = null
            }
        }

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GarageCarDto?>(null) }
    var deleteTarget by remember { mutableStateOf<GarageCarDto?>(null) }

    LaunchedEffect(garageToolbarAddTicks) {
        if (garageToolbarAddTicks > 0) {
            showAdd = true
            onConsumeGarageToolbarAddTicks()
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredCars =
        remember(cars, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                cars
            } else {
                cars.filter { car ->
                    val blob =
                        buildString {
                            append(garageCarTitle(car)).append(' ')
                            append(car.make).append(' ')
                            append(car.model).append(' ')
                            car.year?.let { append(it).append(' ') }
                            append(car.color.orEmpty())
                        }.lowercase()
                    blob.contains(q)
                }
            }
        }

    val canReorderGarage = searchQuery.isBlank() && cars.size > 1

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.garage_search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )

            garageSnack?.takeIf { it.isNotBlank() }?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onDismissGarageSnack) {
                        Text(stringResource(R.string.dismiss_snack))
                    }
                }
            }

            if (canReorderGarage) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.garage_drag_to_reorder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (cars.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    EmptyTabMessage(
                        text = stringResource(R.string.empty_garage),
                        icon = Icons.Outlined.DirectionsCar,
                    )
                }
            } else if (filteredCars.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    EmptyTabMessage(
                        text = stringResource(R.string.garage_search_no_results),
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else if (canReorderGarage) {
                val lazyListState = rememberLazyListState()
                val view = LocalView.current
                val reorderState =
                    rememberReorderableLazyListState(lazyListState) { from, to ->
                        val next = filteredCars.toMutableList()
                        next.add(to.index, next.removeAt(from.index))
                        onReorderGarage(next.map { it.id })
                        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    }
                val rowGap by animateDpAsState(
                    targetValue = if (reorderState.isAnyItemDragging) 26.dp else 16.dp,
                    label = "garageReorderGap",
                )
                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(rowGap),
                ) {
                    items(items = filteredCars, key = { it.id }) { car ->
                        ReorderableItem(reorderState, key = car.id) { isDragging ->
                            val dragElevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "garageDrag",
                            )
                            Surface(
                                shadowElevation = dragElevation,
                                color = Color.Transparent,
                            ) {
                                GarageCarCard(
                                    car = car,
                                    onEdit = { editing = car },
                                    onDelete = { deleteTarget = car },
                                    onPickPhoto = {
                                        pendingPhotoCarId = car.id
                                        pickGaragePhoto.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                    dragHandle = {
                                        Surface(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(end = 10.dp, bottom = 96.dp)
                                                    .draggableHandle(),
                                            shape = RoundedCornerShape(percent = 50),
                                            color = Color.Black.copy(alpha = 0.55f),
                                        ) {
                                            Box(
                                                modifier = Modifier.size(40.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Filled.DragHandle,
                                                    contentDescription = stringResource(R.string.garage_drag_handle_cd),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items = filteredCars, key = { it.id }) { car ->
                        GarageCarCard(
                            car = car,
                            onEdit = { editing = car },
                            onDelete = { deleteTarget = car },
                            onPickPhoto = {
                                pendingPhotoCarId = car.id
                                pickGaragePhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
            }
        }

        if (showAdd) {
            GarageCarEditorSheet(
                title = stringResource(R.string.garage_dialog_add_title),
                initial = null,
                onDismiss = { showAdd = false },
                onConfirm = { nick, mk, makeId, md, yr, col, logoSlug, pr, photoBytes, photoMime ->
                    onAddGarageCar(nick, mk, makeId, md, yr, col, logoSlug, pr, photoBytes, photoMime)
                    showAdd = false
                },
            )
        }

        editing?.let { car ->
            GarageCarEditorSheet(
                title = stringResource(R.string.garage_dialog_edit_title),
                initial = car,
                onDismiss = { editing = null },
                onConfirm = { nick, mk, makeId, md, yr, col, logoSlug, pr, photoBytes, photoMime ->
                    onPatchGarageCar(car.id, nick, mk, makeId, md, yr, col, logoSlug, pr, photoBytes, photoMime)
                    editing = null
                },
            )
        }

        deleteTarget?.let { car ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.garage_delete_vehicle)) },
                text = { Text(garageCarTitle(car), style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteGarageCar(car.id)
                            deleteTarget = null
                        },
                    ) {
                        Text(stringResource(R.string.garage_delete_vehicle))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.garage_dialog_cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarageCarEditorSheet(
    title: String,
    initial: GarageCarDto?,
    onDismiss: () -> Unit,
    onConfirm: (
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        year: Int?,
        color: String?,
        logoSlug: String?,
        primary: Boolean,
        photoBytes: ByteArray?,
        photoMime: String?,
    ) -> Unit,
) {
    val ctx = LocalContext.current
    val isEditing = initial != null
    val scrollState = rememberScrollState()
    val pillShape = RoundedCornerShape(22.dp)
    val pillGray = Color(0xFF3A3A3C)
    val cardBg = Color(0xFF1C1C1E)
    val menuBg = Color(0xFF2C2C2E)

    var nickname by rememberSaveable { mutableStateOf(initial?.nickname.orEmpty()) }
    var make by rememberSaveable { mutableStateOf(initial?.make.orEmpty()) }
    var makeId by rememberSaveable {
        mutableStateOf(
            initial?.makeId.orEmpty().ifBlank {
                GarageCarBrandCatalog.brandMatchingMakeName(ctx, initial?.make.orEmpty())?.id.orEmpty()
            },
        )
    }
    var logoSlug by rememberSaveable {
        mutableStateOf(
            initial?.logoSlug.orEmpty().ifBlank {
                CarBrandLogoCatalog.resolvedLogoSlug(
                    logoSlug = null,
                    makeId = initial?.makeId,
                    makeName = initial?.make.orEmpty(),
                    context = ctx,
                ).orEmpty()
            },
        )
    }
    var model by rememberSaveable { mutableStateOf(initial?.model.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(initial?.year?.toString().orEmpty()) }
    var color by rememberSaveable { mutableStateOf(initial?.color.orEmpty()) }
    var primary by rememberSaveable { mutableStateOf(initial?.isPrimary == true) }
    var selectedPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedPhotoMime by remember { mutableStateOf<String?>(null) }

    val brands =
        remember(initial?.id, initial?.make) {
            GarageCarBrandCatalog.brandsForEditor(ctx, initial?.make)
        }
    var makePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(initial?.id) {
        nickname = initial?.nickname.orEmpty()
        make = initial?.make.orEmpty()
        makeId =
            initial?.makeId.orEmpty().ifBlank {
                GarageCarBrandCatalog.brandMatchingMakeName(ctx, initial?.make.orEmpty())?.id.orEmpty()
            }
        logoSlug =
            initial?.logoSlug.orEmpty().ifBlank {
                CarBrandLogoCatalog.resolvedLogoSlug(
                    logoSlug = null,
                    makeId = initial?.makeId,
                    makeName = initial?.make.orEmpty(),
                    context = ctx,
                ).orEmpty()
            }
        model = initial?.model.orEmpty()
        year = initial?.year?.toString().orEmpty()
        color = initial?.color.orEmpty()
        primary = initial?.isPrimary == true
        selectedPhotoBytes = null
        selectedPhotoMime = null
        makePickerOpen = false
    }

    val cropEditorPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val outUri =
                OttoImageCrop.parseOutputUri(result.resultCode, result.data)
                    ?: return@rememberLauncherForActivityResult
            val mime = ctx.contentResolver.getType(outUri) ?: "image/jpeg"
            val bytes =
                ctx.contentResolver.openInputStream(outUri)?.use { it.readBytes() }
                    ?: return@rememberLauncherForActivityResult
            if (bytes.isNotEmpty()) {
                selectedPhotoMime = mime
                selectedPhotoBytes = bytes
            }
        }

    val pickEditorPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                cropEditorPhoto.launch(OttoImageCrop.uCropIntent(ctx, uri, 16f, 9f))
            } catch (e: Exception) {
                Log.e("OttoImageCrop", "Garage editor uCrop launch failed", e)
            }
        }

    val yearInt =
        year
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.toIntOrNull()

    fun submit() {
        onConfirm(
            nickname.trim(),
            make.trim(),
            makeId.trim().takeIf { it.isNotEmpty() },
            model.trim(),
            yearInt,
            color.trim().takeIf { it.isNotEmpty() },
            logoSlug.trim().takeIf { it.isNotEmpty() },
            primary,
            selectedPhotoBytes,
            selectedPhotoMime,
        )
    }

    val selectedBrand = remember(makeId) { GarageCarBrandCatalog.brandForMakeId(ctx, makeId) }

    val valid = make.trim().isNotEmpty() && model.trim().isNotEmpty()

    val darkTfColors =
        TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.45f),
            cursorColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
        )

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = false,
        includeIme = true,
        topBar = {
            OttoFullscreenOpaqueTopBar(
                modifier = Modifier,
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = pillGray, contentColor = Color.White),
                    shape = pillShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.garage_dialog_cancel), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (valid) submit() },
                    enabled = valid,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = pillGray,
                            contentColor = Color.White,
                            disabledContainerColor = pillGray.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f),
                        ),
                    shape = pillShape,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.garage_dialog_save), fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        stringResource(R.string.garage_section_car_photo),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable {
                                pickEditorPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                    ) {
                        when {
                            selectedPhotoBytes != null -> {
                                val bmp =
                                    remember(selectedPhotoBytes) {
                                        selectedPhotoBytes?.let { bytes ->
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        }
                                    }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            initial?.photo?.url != null -> {
                                val resolved =
                                    MediaUrlResolver.resolve(initial.photo!!.url)?.toString()
                                if (!resolved.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ottoImageRequest(ctx, resolved),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            else -> {
                                Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Outlined.AddAPhoto,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isEditing) {
                                            stringResource(R.string.garage_change_photo)
                                        } else {
                                            stringResource(R.string.garage_add_photo)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(R.string.garage_photo_crop_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.garage_section_car_info),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg),
                    ) {
                        TextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            placeholder = { Text(stringResource(R.string.garage_dialog_nickname)) },
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                ),
                            colors = darkTfColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Box(Modifier.fillMaxWidth()) {
                            TextField(
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                value = make,
                                onValueChange = {},
                                placeholder = { Text(stringResource(R.string.garage_make_placeholder)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                },
                                colors = darkTfColors,
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Button,
                                            onClick = { makePickerOpen = true },
                                        ),
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        TextField(
                            value = model,
                            onValueChange = { value ->
                                model = value
                                CarBrandLogoCatalog.suggestedLogoSlug(ctx, makeId, value)?.let { logoSlug = it }
                            },
                            placeholder = { Text(stringResource(R.string.garage_dialog_model)) },
                            singleLine = true,
                            colors = darkTfColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selectedBrand?.hasLogoPickerOptions == true) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            GarageBrandLogoPickerRow(
                                brand = selectedBrand,
                                selectedSlug = logoSlug,
                                onSelect = { logoSlug = it },
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        TextField(
                            value = year,
                            onValueChange = { year = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = { Text(stringResource(R.string.garage_editor_year)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = darkTfColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        TextField(
                            value = color,
                            onValueChange = { color = it },
                            placeholder = { Text(stringResource(R.string.garage_dialog_color)) },
                            singleLine = true,
                            colors = darkTfColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (isEditing) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.garage_editor_primary_car),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                )
                                Switch(
                                    checked = primary,
                                    onCheckedChange = { primary = it },
                                    enabled = initial?.isPrimary != true,
                                )
                            }
                        }
                    }
                }
            }
            if (makePickerOpen) {
                val scrimInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(40f)
                            .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = scrimInteraction,
                                    indication = null,
                                ) { makePickerOpen = false },
                    )
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 420.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = menuBg,
                        shadowElevation = 8.dp,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.garage_make_placeholder),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = Color.White,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 340.dp),
                            ) {
                                items(brands, key = { it.id }) { brand ->
                                    Text(
                                        text = brand.name,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    make = brand.name
                                                    makeId = if (brand.id.startsWith("legacy-")) "" else brand.id
                                                    logoSlug =
                                                        CarBrandLogoCatalog.defaultLogoSlug(ctx, makeId).orEmpty()
                                                    makePickerOpen = false
                                                }
                                                .padding(horizontal = 20.dp, vertical = 14.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private const val GarageCardLogoDisplayScale = 0.7f

@Composable
private fun GarageCarBrandLogoBadge(
    logoUrl: String,
    size: Dp,
    modifier: Modifier = Modifier,
    containerHeight: Dp? = null,
) {
    val resolvedContainerHeight = containerHeight ?: (size + 12.dp)
    val baseImageSize = containerHeight?.let { (it - 12.dp).coerceAtLeast(20.dp) } ?: size
    val imageSize = (baseImageSize * GarageCardLogoDisplayScale).coerceAtLeast(14.dp)
    Surface(
        modifier = modifier.height(resolvedContainerHeight),
        color = Color.Black.copy(alpha = 0.275f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            GarageBrandLogoThumb(
                logoUrl = logoUrl,
                size = imageSize,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

@Composable
private fun GarageBrandLogoThumb(
    logoUrl: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ottoCarBrandLogoImageRequest(LocalContext.current, logoUrl),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun GarageBrandLogoPickerRow(
    brand: GarageCarBrand,
    selectedSlug: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.garage_brand_logo),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.55f),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            brand.logoPickerOptions().forEach { (slug, label) ->
                val selected = selectedSlug == slug
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) Color(0xFF6E3DFF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) Color(0xFF6E3DFF) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(slug) }
                            .padding(8.dp),
                ) {
                    CarBrandLogoCatalog.logoUrl(slug)?.let { url ->
                        GarageBrandLogoThumb(logoUrl = url, size = 44.dp)
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun garageCarTitle(car: GarageCarDto): String {
    val nick = car.nickname?.trim().orEmpty()
    if (nick.isNotEmpty()) return nick
    return listOf(car.make, car.model)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "Vehicle" }
}

@Composable
private fun GarageCarCardNoPhotoBackdrop(
    carId: String,
    brandLogoUrl: String?,
    readOnly: Boolean,
) {
    val seed = kotlin.math.abs(carId.hashCode()) % 3
    val gradientColors =
        when (seed) {
            0 -> listOf(Color(0xFF3B0D52), Color(0xFF050508))
            1 -> listOf(Color(0xFF141438), Color(0xFF520D1C))
            else -> listOf(Color(0xFF0D2333), Color(0xFF050508))
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center,
    ) {
        if (brandLogoUrl != null) {
            GarageCarBrandLogoBadge(
                logoUrl = brandLogoUrl,
                size = 72.dp,
                modifier = Modifier.offset(x = (-52).dp),
            )
        } else {
            Icon(
                if (readOnly) Icons.Outlined.DirectionsCar else Icons.Outlined.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(if (readOnly) 44.dp else 48.dp),
                tint = Color.White.copy(alpha = 0.16f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GarageCarCard(
    car: GarageCarDto,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    dragHandle: (@Composable BoxScope.() -> Unit)? = null,
) {
    val cardShape = RoundedCornerShape(16.dp)
    val heroHeight = 186.dp
    var menuExpanded by remember(car.id) { mutableStateOf(false) }
    val ctx = LocalContext.current
    val img = car.photo?.url?.let { MediaUrlResolver.resolve(it)?.toString() }
    val brandLogoUrl =
        remember(car.logoSlug, car.makeId, car.make) {
            CarBrandLogoCatalog.logoUrl(
                CarBrandLogoCatalog.resolvedLogoSlug(car.logoSlug, car.makeId, car.make, ctx),
            )
        }
    val detailsLine =
        remember(car.year, car.make, car.model) {
            buildString {
                car.year?.let { append(it).append(' ') }
                append(car.make).append(' ').append(car.model)
            }.trim()
        }
    var copyBlockHeight by remember(car.id) { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val cardContent: @Composable () -> Unit = {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(cardShape),
        ) {
            if (img != null) {
                AsyncImage(
                    model = ottoImageRequest(ctx, img),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                GarageCarCardNoPhotoBackdrop(
                    carId = car.id,
                    brandLogoUrl = brandLogoUrl,
                    readOnly = readOnly,
                )
            }

            if (img != null && brandLogoUrl != null) {
                GarageCarBrandLogoBadge(
                    logoUrl = brandLogoUrl,
                    size = 34.dp,
                    containerHeight = copyBlockHeight.takeIf { it > 0.dp },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (car.isPrimary == true) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.275f),
                        shape = RoundedCornerShape(percent = 50),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White,
                            )
                            Text(
                                stringResource(R.string.garage_primary_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (!readOnly) {
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.48f),
                        ) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.MoreHoriz,
                                    contentDescription = stringResource(R.string.accessibility_garage_card_menu),
                                    tint = Color.White,
                                )
                            }
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.garage_edit_vehicle)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.garage_change_photo)) },
                                onClick = {
                                    menuExpanded = false
                                    onPickPhoto()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.garage_delete_vehicle)) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                },
                            )
                        }
                    }
                }
            }

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .onSizeChanged {
                            copyBlockHeight = with(density) { it.height.toDp() }
                        },
                color = Color.Black.copy(alpha = 0.275f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        garageCarTitle(car),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        detailsLine,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.92f),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            dragHandle?.invoke(this)
        }
    }

    if (onClick != null) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth().clip(cardShape),
            shape = cardShape,
            onClick = onClick,
        ) {
            cardContent()
        }
    } else {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
        ) {
            cardContent()
        }
    }
}

private fun profileFormatDriveDurationSeconds(totalSec: Double): String {
    val s = totalSec.toLong().coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun profilePublicEventWhenLabel(raw: String?): String {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    val inst =
        squadChatInstant(trimmed) ?: runCatching { Instant.parse(trimmed) }.getOrNull()
            ?: return trimmed
    val z = inst.atZone(ZoneId.systemDefault())
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())
    return z.format(fmt)
}

/** Relative time like "21h ago", or null if missing or unparsable. */
private fun relativeAgoFromIso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val trimmed = iso.trim()
    val inst =
        try {
            squadChatInstant(trimmed) ?: Instant.parse(trimmed)
        } catch (_: Exception) {
            return null
        }
    val d = Duration.between(inst, Instant.now())
    if (d.isNegative) return null
    val hrs = d.toHours()
    val mins = d.toMinutes() % 60
    val days = d.toDays()
    return when {
        days >= 14 -> "${days}d ago"
        hrs >= 48 -> "${days}d ago"
        hrs > 0 -> "${hrs}h ago"
        else -> "${mins}m ago"
    }
}

private fun profileRelativeLastDriveLabel(
    iso: String?,
    unknownLabel: String,
): String {
    return relativeAgoFromIso(iso) ?: unknownLabel
}

private fun Context.shareOttoProfileLine(line: String) {
    try {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, line)
                },
                null,
            ),
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun ProfileStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tileBg = Color.White.copy(alpha = 0.055f)
    val iconPurple = Color(0xFFAF52DE)
    val statWhite = Color.White
    val labelGrey = Color.White.copy(alpha = 0.55f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tileBg,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconPurple,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = statWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = labelGrey,
                maxLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileNavRowCard(
    title: String,
    subtitle: String?,
    trailing: String?,
    onClick: () -> Unit,
    leadingIcon: ImageVector,
    leadingTint: Color = Color(0xFFAF52DE),
    destructive: Boolean = false,
) {
    val rowShape = RoundedCornerShape(14.dp)
    val titleColor = if (destructive) Color(0xFFEB5545) else Color.White
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(rowShape)
                .clickable(onClick = onClick),
        shape = rowShape,
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (destructive) Color(0xFFEB5545) else leadingTint,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            trailing?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ProfileLegalFooter(modifier: Modifier = Modifier) {
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    Text(
        text = "Driftd $year © Otto Motto, LLC v.${BuildConfig.VERSION_NAME}",
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.35f),
        textAlign = TextAlign.Center,
    )
}

private object OttoLegalLinks {
    const val PRIVACY = "https://driftd.com/privacy"
    /** Play/App Store listings use `/tos`; site redirects to `/terms` if needed. */
    const val TERMS = "https://driftd.com/tos"
}

@Composable
private fun ProfileSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun ProfileMapAccentPickerDialog(
    currentKey: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0A0A10),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.settings_map_pin_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OttoShellViewModel.MapAccentPaletteKeys.chunked(4).forEach { rowKeys ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            rowKeys.forEach { key ->
                                val selected = currentKey == key
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(mapAccentComposeColor(key))
                                            .then(
                                                if (selected) {
                                                    Modifier.border(3.dp, Color.White, CircleShape)
                                                } else {
                                                    Modifier.border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                                },
                                            )
                                            .clickable {
                                                onSelect(key)
                                                onDismiss()
                                            },
                                ) { }
                            }
                            repeat(4 - rowKeys.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_cancel), color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OttoProfilePane(
    ui: OttoShellUiState,
    onSignOut: () -> Unit,
    onToggleAutoCheckIn: (Boolean) -> Unit,
    onToggleShowPublicGoingEventsOnProfile: (Boolean) -> Unit,
    onSetDriveStatsVisibility: (DriveStatsVisibilitySetting) -> Unit,
    onSetSoundEffects: (Boolean) -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onSaveMapAccent: (String) -> Unit,
    onDeleteAccountConfirmed: () -> Unit,
    onMessageContact: (String) -> Unit,
    onOpenDirectMessages: () -> Unit,
    onAvatarPhotoPicked: (ByteArray, String) -> Unit,
    onNavigateToGarage: () -> Unit,
    onReplayMarketingOnboarding: () -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onRetryPendingDriveSave: (String) -> Unit = {},
    onDeletePendingDriveArchive: (String) -> Unit = {},
    onReloadPendingDriveArchives: () -> Unit = {},
    onOpenProfileDrive: (DriveDto) -> Unit = {},
    onOpenProfileRoute: (SavedRouteDto) -> Unit = {},
    onCreateProfileRoute: () -> Unit = {},
    onOpenProfilePlace: (SavedPlaceDto) -> Unit = {},
    onShareProfileDrive: (DriveDto) -> Unit = {},
    onDeleteProfileDrive: (String) -> Unit = {},
    onDeleteProfileRoute: (String) -> Unit = {},
    onRenameProfileDrive: suspend (DriveDto, String) -> Boolean = { _, _ -> false },
    onRenameProfileRoute: suspend (SavedRouteDto, String) -> Boolean = { _, _ -> false },
    onRenameProfilePlace: (String, String) -> Unit = { _, _ -> },
    onDeleteProfilePlace: (String) -> Unit = {},
    profileDriveDetailContent: @Composable (DriveDto, () -> Unit) -> Unit = { _, onClose -> onClose() },
    profileRouteDetailContent: @Composable (SavedRouteDto, () -> Unit) -> Unit = { _, onClose -> onClose() },
    onPreviewProfileLevelUp: (Int) -> Unit = {},
    onSchedulePreviewProfileLevelUpNotification: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val me = ui.me
    val hasRoutesAccess = me?.canAccessRoutes() == true
    LaunchedEffect(Unit) { onReloadPendingDriveArchives() }
    val pendingDrives =
        remember(ui.pendingDriveArchives) {
            ui.pendingDriveArchives.sortedByDescending { it.endedAt }
        }
    val profileDrives = remember(ui.drives) { sortedProfileDrives(profileDrivesForDisplay(ui.drives)) }
    val myRoutes =
        remember(ui.routes, me?.id, hasRoutesAccess) {
            if (!hasRoutesAccess) {
                emptyList()
            } else {
            val uid = me?.id?.trim().orEmpty()
            if (uid.isEmpty()) {
                emptyList()
            } else {
                sortedProfileRoutes(ui.routes.filter { ottoUserIdsEqual(it.createdByUserId, uid) })
            }
            }
        }
    val profilePlaces = remember(ui.savedPlaces) { sortedProfilePlaces(ui.savedPlaces) }
    var showAllProfileDrives by remember { mutableStateOf(false) }
    var showAllProfileRoutes by remember { mutableStateOf(false) }
    var showAllProfilePlaces by remember { mutableStateOf(false) }
    var previewDrivePendingDelete by remember { mutableStateOf<DriveDto?>(null) }
    var previewRoutePendingDelete by remember { mutableStateOf<SavedRouteDto?>(null) }
    var previewDriveRenameTarget by remember { mutableStateOf<DriveDto?>(null) }
    var previewRouteRenameTarget by remember { mutableStateOf<SavedRouteDto?>(null) }
    var previewDriveRenameDraft by remember { mutableStateOf("") }
    var previewRouteRenameDraft by remember { mutableStateOf("") }
    var previewDriveRenameSaving by remember { mutableStateOf(false) }
    var previewRouteRenameSaving by remember { mutableStateOf(false) }
    var previewPlacePendingDelete by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var previewPlaceRenameTarget by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var previewPlaceRenameDraft by remember { mutableStateOf("") }
    var previewDriveRenameError by remember { mutableStateOf<String?>(null) }
    var selectedPendingArchive by remember { mutableStateOf<PendingDriveArchiveDto?>(null) }
    var previewRouteRenameError by remember { mutableStateOf<String?>(null) }
    val profileScope = rememberCoroutineScope()
    var displayNameDraft by remember { mutableStateOf("") }
    LaunchedEffect(me?.id, me?.displayName) {
        displayNameDraft = me?.displayName.orEmpty()
    }

    var deleteDialogOpen by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmText by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNameEdit by rememberSaveable { mutableStateOf(false) }
    var showProgressionTiers by remember { mutableStateOf(false) }
    var showMapAccentPicker by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    LaunchedEffect(showSettings) {
        if (!showSettings) showMapAccentPicker = false
    }

    val cropAvatarPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val outUri = OttoImageCrop.parseOutputUri(result.resultCode, result.data) ?: return@rememberLauncherForActivityResult
            val mime = ctx.contentResolver.getType(outUri) ?: "image/jpeg"
            val bytes =
                ctx.contentResolver.openInputStream(outUri)?.use { inp -> inp.readBytes() }
                    ?: return@rememberLauncherForActivityResult
            if (bytes.isNotEmpty()) {
                onAvatarPhotoPicked(bytes, mime)
            }
        }

    val pickAvatarPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                cropAvatarPhoto.launch(OttoImageCrop.uCropIntent(ctx, uri, 1f, 1f))
            } catch (e: Exception) {
                Log.e("OttoImageCrop", "Avatar uCrop launch failed", e)
            }
        }

    val primaryCar =
        remember(ui.garageCars) {
            ui.garageCars.firstOrNull { it.isPrimary == true }
                ?: ui.garageCars.firstOrNull()
        }

    val driveStatsVisible = ui.stats?.driveStatsVisible != false
    val pr = if (driveStatsVisible) ui.stats?.progression else null
    val progressBar =
        remember(pr?.progress, pr?.pointsIntoLevel, pr?.pointsRequiredForLevel) {
            val pct = pr?.progress?.toFloat()
            when {
                pct != null -> pct.coerceIn(0f, 1f)
                pr?.pointsRequiredForLevel != null &&
                    pr.pointsRequiredForLevel!! > 0 &&
                    pr.pointsIntoLevel != null ->
                    (pr.pointsIntoLevel!!.toFloat() / pr.pointsRequiredForLevel!!.toFloat())
                        .coerceIn(0f, 1f)

                else -> 0f
            }
        }

    var profileHeroOverflowOpen by rememberSaveable { mutableStateOf(false) }
    var profileHeroXpReveal by remember { mutableStateOf(false) }
    LaunchedEffect(me?.id, pr?.tierId) {
        profileHeroXpReveal = false
        delay(48)
        profileHeroXpReveal = true
    }
    val animatedProfileXp by animateFloatAsState(
        targetValue = if (profileHeroXpReveal) progressBar else 0f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "profileHeroXp",
    )

    val dashLabel = stringResource(R.string.profile_stat_no_data)
    val lastDriveUnknown = stringResource(R.string.profile_last_drive_unknown)
    val eventsNone = stringResource(R.string.profile_stat_events_none)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            me == null ->
                Text(
                    stringResource(R.string.profile_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )

            else -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = Color(0xFF07080B),
                            contentColor = Color.White,
                        ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    val tierIdStr = pr?.tierId
                    val tierPalette =
                        if (driveStatsVisible) profileTierPalette(tierIdStr) else profileTierPaletteHiddenDriveStats()
                    val tierGlow =
                        if (driveStatsVisible) profileTierComposeColor(tierIdStr) else Color.White.copy(alpha = 0.48f)
                    Box(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                tierGlow.copy(alpha = 0.10f),
                                                Color.Transparent,
                                                Color.Transparent,
                                                tierPalette.accentDeep.copy(alpha = 0.08f),
                                            ),
                                        start = Offset(0f, 0f),
                                        end = Offset(980f, 640f),
                                    ),
                                ),
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.05f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.26f),
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .border(
                                    width = 1.dp,
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    tierGlow.copy(alpha = 0.40f),
                                                    Color.White.copy(alpha = 0.08f),
                                                    tierPalette.accentDeep.copy(alpha = 0.26f),
                                                ),
                                            start = Offset(0f, 0f),
                                            end = Offset(880f, 520f),
                                        ),
                                    shape = RoundedCornerShape(28.dp),
                                ),
                        )

                        Row(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 8.dp)
                                    .zIndex(3f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileHeroToolbarIconButton(
                                icon = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.profile_share_profile),
                                onClick = { ctx.shareOttoProfileLine("${me.displayName.trim()} • Driftd") },
                            )

                            Box {
                                ProfileHeroToolbarIconButton(
                                    icon = Icons.Outlined.MoreHoriz,
                                    contentDescription = stringResource(R.string.profile_overflow_cd),
                                    onClick = { profileHeroOverflowOpen = true },
                                )

                                DropdownMenu(
                                    expanded = profileHeroOverflowOpen,
                                    onDismissRequest = { profileHeroOverflowOpen = false },
                                    containerColor = Color(0xFF121318),
                                ) {
                                    DropdownMenuItem(
                                        text =
                                            {
                                                Text(
                                                    stringResource(R.string.profile_menu_edit_photo),
                                                )
                                            },
                                        onClick = {
                                            profileHeroOverflowOpen = false
                                            pickAvatarPhoto.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text =
                                            {
                                                Text(
                                                    stringResource(R.string.profile_menu_edit_name),
                                                )
                                            },
                                        onClick = {
                                            profileHeroOverflowOpen = false
                                            showNameEdit = true
                                        },
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(1f)
                                .padding(horizontal = 18.dp)
                                .padding(top = 44.dp, bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .drawBehind {
                                            val c =
                                                Offset(
                                                    size.width / 2f,
                                                    size.height / 2f,
                                                )
                                            val rx = kotlin.math.min(size.width, size.height) / 2f
                                            drawCircle(
                                                brush =
                                                    Brush.radialGradient(
                                                        colors =
                                                            listOf(
                                                                tierGlow.copy(alpha = 0.34f),
                                                                Color.Transparent,
                                                            ),
                                                        center = c,
                                                        radius = rx * 1.9f,
                                                    ),
                                                radius = rx * 1.28f,
                                                center = c,
                                            )
                                        }
                                        .size(118.dp)
                                        .background(profileTierAvatarRingBrush(tierIdStr), CircleShape)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF121318))
                                        .clip(CircleShape),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            onClickLabel = stringResource(R.string.profile_change_photo),
                                            role = Role.Button,
                                            onClick = {
                                                pickAvatarPhoto.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                )
                                            },
                                        ),
                                ) {
                                    UserProfileAvatar(
                                        displayName = me.displayName,
                                        userId = me.id,
                                        avatarUrl = me.avatarUrl,
                                        mapAccentKey = me.mapAccentKey,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        textStyle =
                                            MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Black,
                                            ),
                                    )
                                }
                            }

                            Text(
                                me.displayName,
                                modifier =
                                    Modifier
                                        .padding(top = 14.dp)
                                        .widthIn(max = 320.dp),
                                style =
                                    MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.25).sp,
                                        lineHeight = 38.sp,
                                    ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )

                            if (driveStatsVisible) {
                                pr?.let { p ->
                                    val tierColor = profileTierComposeColor(p.tierId)
                                    Column(
                                        modifier =
                                            Modifier
                                                .padding(top = 14.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(role = Role.Button) { showProgressionTiers = true },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Image(
                                                painter = painterResource(progressionLevelBadgeRes(p.level)),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                contentScale = ContentScale.Fit,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                profileProgressionOrdinalLabel(p),
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                color = tierColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = stringResource(R.string.progression_inline_sep),
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                color = Color.White.copy(alpha = 0.38f),
                                            )
                                            Text(
                                                stringResource(R.string.profile_level_format, p.level ?: 1),
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                color = Color.White.copy(alpha = 0.76f),
                                            )
                                            Icon(
                                                Icons.AutoMirrored.Outlined.NavigateNext,
                                                contentDescription = stringResource(R.string.progression_show_tiers_cd),
                                                tint = Color.White.copy(alpha = 0.42f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        PremiumProfileXpBar(
                                            progress = animatedProfileXp,
                                            tierId = p.tierId,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                        )

                                        Text(
                                            profileProgressionPointsCaption(p),
                                            modifier = Modifier.padding(top = 8.dp),
                                            style =
                                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = tierColor,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.profile_drive_stats_private_note),
                                    modifier = Modifier.padding(top = 14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.62f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (me != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.profile_my_garage),
                    modifier = Modifier.weight(1f),
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Row(
                    modifier = Modifier.clickable { onNavigateToGarage() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.profile_view_arrow),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (primaryCar != null) {
                GarageCarCard(
                    car = primaryCar,
                    readOnly = true,
                    onClick = onNavigateToGarage,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGarage() },
                ) {
                    Text(
                        stringResource(R.string.profile_my_garage_empty),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val s = ui.stats
            val milesVal =
                if (s != null && s.totalMilesDriven.isFinite()) {
                    "%.0f mi".format(s.totalMilesDriven)
                } else dashLabel

            val timeVal = s?.let { profileFormatDriveDurationSeconds(it.totalDriveTimeSeconds) } ?: dashLabel

            val avgVal =
                s?.takeIf { it.avgSpeedMph.isFinite() }?.let {
                    "${"%.0f".format(it.avgSpeedMph)} mph"
                } ?: dashLabel

            val topVal =
                s?.takeIf { it.topSpeedMph.isFinite() }?.let {
                    "${"%.0f".format(it.topSpeedMph)} mph"
                } ?: dashLabel

            val lastDriveVal =
                s?.lastDriveAt?.let { iso ->
                    profileRelativeLastDriveLabel(iso, lastDriveUnknown)
                } ?: dashLabel

            val eventsVal =
                if (s == null) dashLabel else if (s.eventsAttended <= 0) eventsNone else "${s.eventsAttended}"

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.profile_stats_heading),
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )

                    Spacer(Modifier.height(12.dp))

                    when {
                        s == null -> {
                            Text(
                                stringResource(R.string.profile_stats_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                        s.driveStatsVisible == false -> {
                            Text(
                                stringResource(R.string.profile_stats_peer_private),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                        else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProfileStatTile(
                                    Icons.Outlined.Route,
                                    milesVal,
                                    stringResource(R.string.profile_stat_miles),
                                    Modifier.weight(1f),
                                )
                                ProfileStatTile(
                                    Icons.Outlined.Schedule,
                                    timeVal,
                                    stringResource(R.string.profile_stat_drive_time),
                                    Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProfileStatTile(
                                    Icons.Outlined.Speed,
                                    avgVal,
                                    stringResource(R.string.profile_stat_avg_speed),
                                    Modifier.weight(1f),
                                )
                                ProfileStatTile(
                                    Icons.Outlined.Speed,
                                    topVal,
                                    stringResource(R.string.profile_stat_top_speed),
                                    Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProfileStatTile(
                                    Icons.Outlined.Schedule,
                                    lastDriveVal,
                                    stringResource(R.string.profile_stat_last_drive),
                                    Modifier.weight(1f),
                                )
                                ProfileStatTile(
                                    Icons.Outlined.ConfirmationNumber,
                                    eventsVal,
                                    stringResource(R.string.profile_stat_events),
                                    Modifier.weight(1f),
                                )
                            }
                        }
                        }
                    }
                }
            }

            if (pendingDrives.isNotEmpty() || profileDrives.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        ProfileListSectionHeader(
                            title = stringResource(R.string.profile_my_drives_heading),
                            count = pendingDrives.size + profileDrives.size,
                            showViewAll = (pendingDrives.size + profileDrives.size) > PROFILE_LIST_PREVIEW_LIMIT,
                            onViewAll = { showAllProfileDrives = true },
                        )
                        Spacer(Modifier.height(12.dp))
                        for (archive in pendingDrives.take(PROFILE_LIST_PREVIEW_LIMIT)) {
                            ProfilePendingDriveInteractiveRow(
                                archive = archive,
                                onOpen = { selectedPendingArchive = archive },
                                onRetry = { onRetryPendingDriveSave(archive.id) },
                                onDelete = { onDeletePendingDriveArchive(archive.id) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            )
                        }
                        val savedPreviewLimit =
                            (PROFILE_LIST_PREVIEW_LIMIT - pendingDrives.size).coerceAtLeast(0)
                        for (drive in profileDrives.take(savedPreviewLimit)) {
                            ProfileInteractiveDriveRow(
                                drive = drive,
                                onOpen = { onOpenProfileDrive(drive) },
                                onShare = { onShareProfileDrive(drive) },
                                onRename = {
                                    previewDriveRenameTarget = drive
                                    previewDriveRenameDraft = DriveDisplayNaming.listTitle(drive)
                                    previewDriveRenameError = null
                                },
                                onDelete = { previewDrivePendingDelete = drive },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            }

            if (hasRoutesAccess) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        ProfileListSectionHeader(
                            title = stringResource(R.string.profile_my_routes_heading),
                            count = myRoutes.size,
                            showViewAll = myRoutes.size > PROFILE_LIST_PREVIEW_LIMIT,
                            onViewAll = { showAllProfileRoutes = true },
                        )
                        Spacer(Modifier.height(12.dp))
                        CreateRouteListRow(
                            onClick = onCreateProfileRoute,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                        for (route in myRoutes.take(PROFILE_LIST_PREVIEW_LIMIT)) {
                            ProfileInteractiveRouteRow(
                                route = route,
                                onOpen = { onOpenProfileRoute(route) },
                                onRename = {
                                    previewRouteRenameTarget = route
                                    previewRouteRenameDraft = route.name.trim()
                                    previewRouteRenameError = null
                                },
                                onDelete = { previewRoutePendingDelete = route },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            }

            if (profilePlaces.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        ProfileListSectionHeader(
                            title = stringResource(R.string.profile_my_places_heading),
                            count = profilePlaces.size,
                            showViewAll = profilePlaces.size > PROFILE_LIST_PREVIEW_LIMIT,
                            onViewAll = { showAllProfilePlaces = true },
                        )
                        Spacer(Modifier.height(12.dp))
                        for (place in profilePlaces.take(PROFILE_LIST_PREVIEW_LIMIT)) {
                            ProfileInteractivePlaceRow(
                                place = place,
                                onOpen = { onOpenProfilePlace(place) },
                                onRename = {
                                    previewPlaceRenameTarget = place
                                    previewPlaceRenameDraft = place.name
                                },
                                onDelete = { previewPlacePendingDelete = place },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            }

            if (ui.profilePublicGoingEvents.isNotEmpty()) {
                val profileCtxScroll = LocalContext.current
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.profile_public_events_heading),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        for (ev in ui.profilePublicGoingEvents) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onOpenEventDetail(ev.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.055f),
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                    ) {
                                        val bUrl =
                                            ev.bannerImageUrl?.let { MediaUrlResolver.resolve(it)?.toString() }
                                        if (!bUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ottoImageRequest(profileCtxScroll, bUrl),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Outlined.CalendarMonth,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.45f),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ev.name,
                                            style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            profilePublicEventWhenLabel(ev.startsAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.55f),
                                        )
                                        val loc = ev.addressLabel?.trim().orEmpty()
                                        if (loc.isNotEmpty()) {
                                            Text(
                                                loc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.40f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Outlined.NavigateNext,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ProfileNavRowCard(
                title = stringResource(R.string.profile_settings),
                subtitle = null,
                trailing = null,
                onClick = { showSettings = true },
                leadingIcon = Icons.Outlined.Settings,
            )

            ProfileNavRowCard(
                title = stringResource(R.string.sign_out),
                subtitle = null,
                trailing = null,
                onClick = onSignOut,
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                destructive = true,
            )

            ProfileLegalFooter()

            ui.profileSnack?.takeIf { it.isNotBlank() }?.let { snack ->
                Text(snack, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showProgressionTiers) {
        OttoFullscreenDialog(
            onDismissRequest = { showProgressionTiers = false },
            topBar = {
                OttoFullscreenOpaqueTopBar {
                    IconButton(onClick = { showProgressionTiers = false }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.progression_back_cd),
                            tint = Color.White,
                        )
                    }
                }
            },
        ) { contentPadding ->
            ProgressionTiersFullScreen(
                contentPadding = contentPadding,
                onPreviewLevelUp = onPreviewProfileLevelUp,
                onSchedulePreviewNotification = onSchedulePreviewProfileLevelUpNotification,
            )
        }
    }

    if (showAllProfileDrives) {
        ProfileDrivesFullScreenList(
            pendingArchives = pendingDrives,
            drives = profileDrives,
            onDismiss = { showAllProfileDrives = false },
            onDeleteDrive = onDeleteProfileDrive,
            onShareDrive = onShareProfileDrive,
            onRenameDrive = onRenameProfileDrive,
            onRetryPendingDriveSave = onRetryPendingDriveSave,
            onDeletePendingDriveArchive = onDeletePendingDriveArchive,
            driveDetailContent = profileDriveDetailContent,
        )
    }

    selectedPendingArchive?.let { archive ->
        AlertDialog(
            onDismissRequest = { selectedPendingArchive = null },
            title = { Text(stringResource(R.string.drive_pending_summary_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(archive.title)
                    Text(pendingDriveRowSubtitle(archive))
                    Text(
                        stringResource(R.string.drive_pending_not_saved_badge),
                        color = Color(0xFFFF9500),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRetryPendingDriveSave(archive.id)
                    selectedPendingArchive = null
                }) {
                    Text(stringResource(R.string.drive_pending_retry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPendingArchive = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    if (showAllProfileRoutes) {
        ProfileRoutesFullScreenList(
            routes = myRoutes,
            onDismiss = { showAllProfileRoutes = false },
            onCreateRoute = onCreateProfileRoute,
            onOpenRoute = onOpenProfileRoute,
            onDeleteRoute = onDeleteProfileRoute,
            onRenameRoute = onRenameProfileRoute,
        )
    }

    if (showAllProfilePlaces) {
        ProfilePlacesFullScreenList(
            places = profilePlaces,
            onDismiss = { showAllProfilePlaces = false },
            onOpenPlace = onOpenProfilePlace,
            onDeletePlace = onDeleteProfilePlace,
            onRenamePlace = onRenameProfilePlace,
        )
    }

    previewPlacePendingDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { previewPlacePendingDelete = null },
            title = { Text(stringResource(R.string.marker_detail_delete_place_title)) },
            text = { Text(place.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfilePlace(place.id)
                        previewPlacePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.marker_detail_action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewPlacePendingDelete = null }) {
                    Text(stringResource(R.string.marker_detail_cancel))
                }
            },
        )
    }

    previewPlaceRenameTarget?.let { place ->
        AlertDialog(
            onDismissRequest = { previewPlaceRenameTarget = null },
            title = { Text(stringResource(R.string.profile_list_rename)) },
            text = {
                OutlinedTextField(
                    value = previewPlaceRenameDraft,
                    onValueChange = { previewPlaceRenameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = previewPlaceRenameDraft.trim()
                        if (draft.isNotEmpty()) {
                            onRenameProfilePlace(place.id, draft)
                            previewPlaceRenameTarget = null
                        }
                    },
                    enabled = previewPlaceRenameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.drive_summary_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewPlaceRenameTarget = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    previewDrivePendingDelete?.let { drive ->
        AlertDialog(
            onDismissRequest = { previewDrivePendingDelete = null },
            title = { Text(stringResource(R.string.drive_summary_delete_title)) },
            text = { Text(stringResource(R.string.drive_summary_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfileDrive(drive.id)
                        previewDrivePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.drive_summary_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewDrivePendingDelete = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    previewRoutePendingDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { previewRoutePendingDelete = null },
            title = { Text(stringResource(R.string.profile_delete_route_title)) },
            text = { Text(stringResource(R.string.profile_delete_route_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfileRoute(route.id)
                        previewRoutePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.profile_delete_route_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewRoutePendingDelete = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    previewDriveRenameTarget?.let { drive ->
        AlertDialog(
            onDismissRequest = {
                if (!previewDriveRenameSaving) {
                    previewDriveRenameTarget = null
                    previewDriveRenameError = null
                }
            },
            title = { Text(stringResource(R.string.drive_summary_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drive_summary_rename_message))
                    OutlinedTextField(
                        value = previewDriveRenameDraft,
                        onValueChange = { previewDriveRenameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.drive_summary_rename_hint)) },
                        singleLine = true,
                        enabled = !previewDriveRenameSaving,
                    )
                    previewDriveRenameError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = previewDriveRenameDraft.trim()
                        if (draft.isEmpty() || previewDriveRenameSaving) return@TextButton
                        profileScope.launch {
                            previewDriveRenameSaving = true
                            previewDriveRenameError = null
                            val ok = onRenameProfileDrive(drive, draft)
                            previewDriveRenameSaving = false
                            if (ok) {
                                previewDriveRenameTarget = null
                            } else {
                                previewDriveRenameError = ctx.getString(R.string.drive_rename_error)
                            }
                        }
                    },
                    enabled = previewDriveRenameDraft.trim().isNotEmpty() && !previewDriveRenameSaving,
                ) {
                    Text(stringResource(R.string.drive_summary_rename))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        previewDriveRenameTarget = null
                        previewDriveRenameError = null
                    },
                    enabled = !previewDriveRenameSaving,
                ) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    previewRouteRenameTarget?.let { route ->
        AlertDialog(
            onDismissRequest = {
                if (!previewRouteRenameSaving) {
                    previewRouteRenameTarget = null
                    previewRouteRenameError = null
                }
            },
            title = { Text(stringResource(R.string.profile_rename_route_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_rename_route_message))
                    OutlinedTextField(
                        value = previewRouteRenameDraft,
                        onValueChange = { previewRouteRenameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_rename_route_hint)) },
                        singleLine = true,
                        enabled = !previewRouteRenameSaving,
                    )
                    previewRouteRenameError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = previewRouteRenameDraft.trim()
                        if (draft.isEmpty() || previewRouteRenameSaving) return@TextButton
                        profileScope.launch {
                            previewRouteRenameSaving = true
                            previewRouteRenameError = null
                            val ok = onRenameProfileRoute(route, draft)
                            previewRouteRenameSaving = false
                            if (ok) {
                                previewRouteRenameTarget = null
                            } else {
                                previewRouteRenameError = ctx.getString(R.string.profile_rename_route_error)
                            }
                        }
                    },
                    enabled = previewRouteRenameDraft.trim().isNotEmpty() && !previewRouteRenameSaving,
                ) {
                    Text(stringResource(R.string.drive_summary_rename))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        previewRouteRenameTarget = null
                        previewRouteRenameError = null
                    },
                    enabled = !previewRouteRenameSaving,
                ) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    if (showNameEdit && me != null) {
        AlertDialog(
            onDismissRequest = { showNameEdit = false },
            title = { Text(stringResource(R.string.profile_display_name_label)) },
            text = {
                OutlinedTextField(
                    value = displayNameDraft,
                    onValueChange = { displayNameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    enabled = !ui.profileSaving,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveDisplayName(displayNameDraft.trim())
                        showNameEdit = false
                    },
                    enabled = displayNameDraft.trim().isNotEmpty() && !ui.profileSaving,
                ) {
                    Text(stringResource(R.string.profile_display_name_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameEdit = false }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    val expectedHint = stringResource(R.string.profile_delete_confirm_hint)
    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                deleteDialogOpen = false
                deleteConfirmText = ""
            },
            title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.profile_delete_confirm_body, expectedHint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteConfirmText.trim() == expectedHint) {
                            onDeleteAccountConfirmed()
                            deleteDialogOpen = false
                            deleteConfirmText = ""
                        }
                    },
                    enabled = deleteConfirmText.trim() == expectedHint,
                ) {
                    Text(stringResource(R.string.profile_delete_account))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        deleteConfirmText = ""
                    },
                ) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    if (showSettings && me != null) {
        val captionMuted = Color.White.copy(alpha = 0.62f)
        val rowIconTint = Color.White.copy(alpha = 0.90f)
        OttoFullscreenDialog(
            onDismissRequest = { if (!ui.profileSaving) showSettings = false },
            topBar = {
                OttoFullscreenDarkTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.profile_settings),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = { showSettings = false },
                            enabled = !ui.profileSaving,
                        ) {
                            Text(stringResource(R.string.profile_settings_done), color = Color.White)
                        }
                    },
                )
            },
        ) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.Black,
                                    Color(0xFF0F0A14),
                                    Color.Black,
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(900f, 1600f),
                            ),
                        ),
                )
                OttoFullscreenScrollContent(
                    contentPadding = contentPadding,
                    extraBottom = 40.dp,
                ) {
                        item(key = "map") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_map),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !ui.profileSaving) { showMapAccentPicker = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = rowIconTint,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_map_pin_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                    )
                                    Text(
                                        stringResource(R.string.settings_map_pin_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = captionMuted,
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(mapAccentComposeColor(me.mapAccentKey)),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        }

                        item(key = "events") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_events),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.settings_auto_check_in),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                )
                                Switch(
                                    checked = me.autoEventCheckInEnabled != false,
                                    onCheckedChange = onToggleAutoCheckIn,
                                    enabled = !ui.profileSaving,
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                )
                            }
                            Text(
                                stringResource(R.string.settings_auto_check_in_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = captionMuted,
                                modifier = Modifier.padding(top = 8.dp),
                            )

                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.settings_show_public_events),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                )
                                Switch(
                                    checked = me.showPublicGoingEventsOnProfile != false,
                                    onCheckedChange = onToggleShowPublicGoingEventsOnProfile,
                                    enabled = !ui.profileSaving,
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                )
                            }
                            Text(
                                stringResource(R.string.settings_show_public_events_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = captionMuted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        }

                        item(key = "drive_stats") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_drive_stats),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            val sel = me.resolvedDriveStatsVisibility()
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DriveStatsVisibilitySetting.entries.forEach { opt ->
                                    val label =
                                        when (opt) {
                                            DriveStatsVisibilitySetting.PUBLIC ->
                                                stringResource(R.string.settings_drive_stats_public_label)
                                            DriveStatsVisibilitySetting.SQUADS ->
                                                stringResource(R.string.settings_drive_stats_squads_label)
                                            DriveStatsVisibilitySetting.PRIVATE ->
                                                stringResource(R.string.settings_drive_stats_private_label)
                                        }
                                    FilterChip(
                                        selected = sel == opt,
                                        onClick = { onSetDriveStatsVisibility(opt) },
                                        label = { Text(label) },
                                        enabled = !ui.profileSaving,
                                    )
                                }
                            }
                            val caption =
                                when (sel) {
                                    DriveStatsVisibilitySetting.PUBLIC ->
                                        stringResource(R.string.settings_drive_stats_caption_public)
                                    DriveStatsVisibilitySetting.SQUADS ->
                                        stringResource(R.string.settings_drive_stats_caption_squads)
                                    DriveStatsVisibilitySetting.PRIVATE ->
                                        stringResource(R.string.settings_drive_stats_caption_private)
                                }
                            Text(
                                caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = captionMuted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        }

                        item(key = "otto_intro") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_otto_intro),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !ui.profileSaving) {
                                            onReplayMarketingOnboarding()
                                            showSettings = false
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = rowIconTint,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.profile_replay_marketing_onboarding),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                    )
                                    Text(
                                        stringResource(R.string.profile_replay_marketing_onboarding_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = captionMuted,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        }

                        item(key = "sound") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_sound),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.settings_sound_effects),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                )
                                Switch(
                                    checked = ui.soundEffectsEnabled,
                                    onCheckedChange = onSetSoundEffects,
                                    enabled = !ui.profileSaving,
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                )
                            }
                            Text(
                                stringResource(R.string.settings_sound_effects_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = captionMuted,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        }

                        item(key = "legal") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.settings_section_legal),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            try {
                                                ctx.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(OttoLegalLinks.PRIVACY)),
                                                )
                                            } catch (_: Throwable) {
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = rowIconTint, modifier = Modifier.size(22.dp))
                                Text(
                                    stringResource(R.string.settings_privacy_policy),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            try {
                                                ctx.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(OttoLegalLinks.TERMS)),
                                                )
                                            } catch (_: Throwable) {
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = rowIconTint,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    stringResource(R.string.settings_terms_of_use),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            try {
                                                ctx.startActivity(
                                                    Intent(Intent.ACTION_SENDTO).apply {
                                                        data =
                                                            Uri.parse(
                                                                "mailto:legal@ottomot.to?subject=${Uri.encode("Driftd — Report a concern")}",
                                                            )
                                                    },
                                                )
                                            } catch (_: Throwable) {
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Forum, contentDescription = null, tint = rowIconTint, modifier = Modifier.size(22.dp))
                                Text(
                                    stringResource(R.string.settings_report_safety_concern),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        }

                        item(key = "delete_account") {
                        ProfileSettingsCard {
                            Text(
                                stringResource(R.string.profile_delete_account),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF24444).copy(alpha = 0.95f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.settings_delete_account_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.68f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    deleteDialogOpen = true
                                    showSettings = false
                                },
                                enabled = !ui.profileSaving,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF4444).copy(alpha = 0.22f),
                                        contentColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    stringResource(R.string.profile_delete_account),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                        }

                        if (ui.profileSaving) {
                            item(key = "saving") {
                            LinearProgressIndicator(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            }
                        }

                        item(key = "settings_bottom_spacer") {
                            Spacer(Modifier.height(8.dp))
                        }
                }
            }
        }
    }

    if (showMapAccentPicker && me != null) {
        ProfileMapAccentPickerDialog(
            currentKey = me.mapAccentKey,
            onDismiss = { showMapAccentPicker = false },
            onSelect = { key -> onSaveMapAccent(key) },
        )
    }
}


private sealed class MapDurationChoice {
    data class Fixed(
        val minutes: Int,
        val labelRes: Int,
    ) : MapDurationChoice()

    data object EndOfDay : MapDurationChoice()
}

private val mapDurationChoices: List<MapDurationChoice> =
    listOf(
        MapDurationChoice.Fixed(30, R.string.map_duration_30m),
        MapDurationChoice.Fixed(60, R.string.map_duration_1h),
        MapDurationChoice.Fixed(120, R.string.map_duration_2h),
        MapDurationChoice.Fixed(240, R.string.map_duration_4h),
        MapDurationChoice.Fixed(480, R.string.map_duration_8h),
        MapDurationChoice.EndOfDay,
    )

/** iOS SharingDurationPreset.endOfDay: seconds until [Calendar dateInterval of .day].end, min 60s. */
private fun computeMinutesUntilStartOfNextDayInDefaultZone(): Int {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val startOfNextDay = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    val secs = Duration.between(now, startOfNextDay).seconds
    return (maxOf(60L, secs) / 60L).toInt().coerceAtLeast(1)
}

private fun MapDurationChoice.resolveToTimerMinutes(): Int? =
    when (this) {
        is MapDurationChoice.Fixed -> minutes
        MapDurationChoice.EndOfDay -> computeMinutesUntilStartOfNextDayInDefaultZone()
    }

private fun mapDurationChoiceIndexForSavedMinutes(saved: Int?): Int {
    val choices = mapDurationChoices
    if (saved != null) {
        val endM = computeMinutesUntilStartOfNextDayInDefaultZone()
        if (kotlin.math.abs(saved - endM) <= 2) {
            choices.indexOfFirst { it is MapDurationChoice.EndOfDay }.takeIf { it >= 0 }?.let { return it }
        }
        choices.indexOfFirst { it is MapDurationChoice.Fixed && (it as MapDurationChoice.Fixed).minutes == saved }
            .takeIf { it >= 0 }
            ?.let { return it }
    }
    return choices.indexOfFirst { it is MapDurationChoice.Fixed && (it as MapDurationChoice.Fixed).minutes == 60 }
        .takeIf { it >= 0 } ?: 0
}

@Composable
private fun mapDurationChoiceLabel(choice: MapDurationChoice): String =
    when (choice) {
        is MapDurationChoice.Fixed -> stringResource(choice.labelRes)
        MapDurationChoice.EndOfDay -> stringResource(R.string.map_duration_end_of_day)
    }

/**
 * iOS overlays the signed-in user from device GPS (`MapScreen.visibleFriends`), even when the presence API omits them.
 */
private fun plottedPresenceOverlayingDeviceSelf(
    presenceMembers: List<PresenceMemberDto>,
    me: UserDto?,
    deviceFix: LocationFix?,
    mapSharingLocation: Boolean,
    mapPresenceCircleId: String,
    deviceMovementMode: String?,
    fineLocationGranted: Boolean,
): List<PresenceMemberDto> {
    val plotted = presenceMembers.filter { geoLatLngOrNull(it.lat, it.lng) != null }.toMutableList()
    if (!fineLocationGranted) return plotted
    val meId = me?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return plotted
    val fix = deviceFix?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() } ?: return plotted

    val speedMph = ((fix.speedMps ?: 0f).toDouble() * 2.23694)
    val speedMps = (fix.speedMps ?: 0f).toDouble().coerceAtLeast(0.0)
    val movementMode =
        if (mapSharingLocation && !deviceMovementMode.isNullOrBlank()) {
            deviceMovementMode
        } else {
            MovementModeIosParity.inferMovementModeFromSpeedMps(speedMps)
        }
    val idx = plotted.indexOfFirst { ottoUserIdsEqual(it.userId, meId) }
    if (idx >= 0) {
        plotted[idx] =
            plotted[idx].copy(
                lat = fix.latitude,
                lng = fix.longitude,
                speedMph = speedMph,
                movementMode = movementMode,
                isActive = mapSharingLocation,
                updatedAt = Instant.now().toString(),
            )
    } else {
        plotted +=
            PresenceMemberDto(
                userId = meId,
                circleId = mapPresenceCircleId.trim(),
                isActive = mapSharingLocation,
                speedMph = speedMph,
                movementMode = movementMode,
                lat = fix.latitude,
                lng = fix.longitude,
                updatedAt = Instant.now().toString(),
            )
    }
    return plotted
}

private fun plottedPresenceForSquadBounds(
    squadId: String,
    presenceMembersInSquad: List<PresenceMemberDto>,
    me: UserDto?,
    deviceFix: LocationFix?,
    mapSharingLocation: Boolean,
    mapPresenceCircleId: String,
    deviceMovementMode: String?,
    fineLocationGranted: Boolean,
): List<PresenceMemberDto> {
    val sharingThisSquad =
        mapSharingLocation && ottoUserIdsEqual(mapPresenceCircleId.trim(), squadId.trim())
    return plottedPresenceOverlayingDeviceSelf(
        presenceMembers = presenceMembersInSquad,
        me = me,
        deviceFix = deviceFix,
        mapSharingLocation = sharingThisSquad,
        mapPresenceCircleId = squadId,
        deviceMovementMode = if (sharingThisSquad) deviceMovementMode else null,
        fineLocationGranted = fineLocationGranted,
    )
}

private const val PRESENCE_MOTION_MIN_ANIMATION_MS = 900L
private const val PRESENCE_MOTION_MAX_ANIMATION_MS = 6_500L
private const val PRESENCE_MOTION_INTERVAL_MULTIPLIER = 1.12
private const val PRESENCE_MOTION_SNAP_DEGREES = 0.05

private data class PresenceMotionTrack(
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val startMs: Long,
    val endMs: Long,
    val sourceUpdatedAtMs: Long?,
    val sourceUpdatedAt: String?,
    val wallUpdatedAtMs: Long,
) {
    fun positionAt(nowMs: Long): LatLng {
        if (endMs <= startMs || nowMs >= endMs) return LatLng(endLat, endLng)
        if (nowMs <= startMs) return LatLng(startLat, startLng)
        val t = ((nowMs - startMs).toDouble() / (endMs - startMs).toDouble()).coerceIn(0.0, 1.0)
        return LatLng(
            latitude = startLat + (endLat - startLat) * t,
            longitude = startLng + (endLng - startLng) * t,
        )
    }
}

private fun parsePresenceUpdatedAtMillis(value: String?): Long? =
    value
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }

private fun shouldSnapPresenceMotion(
    from: LatLng,
    to: LatLng,
): Boolean =
    abs(from.latitude - to.latitude) > PRESENCE_MOTION_SNAP_DEGREES ||
        abs(from.longitude - to.longitude) > PRESENCE_MOTION_SNAP_DEGREES

@Composable
private fun rememberSmoothedPresenceMembers(
    members: List<PresenceMemberDto>,
    meUserId: String?,
): List<PresenceMemberDto> {
    val tracks = remember { mutableStateMapOf<String, PresenceMotionTrack>() }
    var frameNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val sourceKey =
        remember(members, meUserId) {
            members.joinToString("|") { m ->
                "${m.userId}:${m.lat}:${m.lng}:${m.updatedAt}:${m.isActive}:${m.movementMode}:${m.speedMph}"
            } + "|me=${meUserId.orEmpty()}"
        }

    LaunchedEffect(sourceKey) {
        val nowMs = System.currentTimeMillis()
        val activeIds = members.mapNotNull { it.userId.trim().takeIf { id -> id.isNotEmpty() } }.toSet()
        tracks.keys.retainAll(activeIds)

        members.forEach { member ->
            val id = member.userId.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val target = geoLatLngOrNull(member.lat, member.lng)
            val me = meUserId?.trim()?.takeIf { it.isNotEmpty() }
            val isSelf = me != null && ottoUserIdsEqual(id, me)
            if (target == null || (!isSelf && !member.isActive)) {
                tracks.remove(id)
                return@forEach
            }

            val previous = tracks[id]
            if (
                previous != null &&
                    previous.endLat == target.latitude &&
                    previous.endLng == target.longitude &&
                    previous.sourceUpdatedAt == member.updatedAt
            ) {
                return@forEach
            }

            val current = previous?.positionAt(nowMs) ?: target
            val sourceUpdatedAtMs = parsePresenceUpdatedAtMillis(member.updatedAt)
            val observedIntervalMs =
                when {
                    previous?.sourceUpdatedAtMs != null &&
                        sourceUpdatedAtMs != null &&
                        sourceUpdatedAtMs > previous.sourceUpdatedAtMs ->
                        sourceUpdatedAtMs - previous.sourceUpdatedAtMs
                    previous != null -> nowMs - previous.wallUpdatedAtMs
                    else -> 0L
                }
            val durationMs =
                if (isSelf && !member.isActive) {
                    0L
                } else if (previous == null || shouldSnapPresenceMotion(current, target)) {
                    0L
                } else {
                    (observedIntervalMs * PRESENCE_MOTION_INTERVAL_MULTIPLIER)
                        .toLong()
                        .coerceIn(PRESENCE_MOTION_MIN_ANIMATION_MS, PRESENCE_MOTION_MAX_ANIMATION_MS)
                }
            tracks[id] =
                PresenceMotionTrack(
                    startLat = current.latitude,
                    startLng = current.longitude,
                    endLat = target.latitude,
                    endLng = target.longitude,
                    startMs = nowMs,
                    endMs = nowMs + durationMs,
                    sourceUpdatedAtMs = sourceUpdatedAtMs,
                    sourceUpdatedAt = member.updatedAt,
                    wallUpdatedAtMs = nowMs,
                )
        }

        while (true) {
            val maxEndMs = tracks.values.maxOfOrNull { it.endMs } ?: break
            val current = System.currentTimeMillis()
            frameNowMs = current
            if (current >= maxEndMs) break
            withFrameMillis { }
        }
    }

    return remember(members, frameNowMs, meUserId) {
        members.map { member ->
            val id = member.userId.trim()
            val position = tracks[id]?.positionAt(frameNowMs) ?: return@map member
            member.copy(lat = position.latitude, lng = position.longitude)
        }
    }
}

/**
 * While follow-me is on, pans the map each frame toward [followTarget]. In drive navigation mode,
 * applies pitched zoom + smoothly interpolated heading (iOS drive camera parity).
 */
@Composable
private fun MapDeviceFollowCameraEffect(
    mapViewportState: MapViewportState,
    followTarget: LatLng?,
    enabled: Boolean,
    mapsKeyOk: Boolean,
    driveNavigationActive: Boolean,
    deviceFix: LocationFix?,
    driveFollowChrome: MapDriveCamera.DriveFollowChromeInsets?,
    onProgrammaticCameraMove: () -> Unit,
) {
    val latestDriveFollowChrome by rememberUpdatedState(driveFollowChrome)
    val latestTarget by rememberUpdatedState(followTarget)
    val latestEnabled by rememberUpdatedState(enabled && mapsKeyOk)
    val latestDriveNavigation by rememberUpdatedState(driveNavigationActive)
    val latestDeviceFix by rememberUpdatedState(deviceFix)
    val latestOnProgrammaticCameraMove by rememberUpdatedState(onProgrammaticCameraMove)
    var wasDriveNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(latestDriveNavigation, latestEnabled, mapsKeyOk, latestTarget) {
        if (!mapsKeyOk || !latestEnabled) return@LaunchedEffect
        val target = latestTarget ?: return@LaunchedEffect
        when {
            latestDriveNavigation && !wasDriveNavigation -> {
                val bearing = MapDriveCamera.driveBearing(latestDeviceFix, null, 0f)
                val padding =
                    latestDriveFollowChrome?.let { MapDriveCamera.driveFollowPadding(it) }
                latestOnProgrammaticCameraMove()
                mapViewportState.easeToDriveFollowCamera(target, bearing, padding)
            }
            !latestDriveNavigation && wasDriveNavigation -> {
                val zoom = mapViewportState.cameraState?.zoom ?: MapDriveCamera.DRIVE_ZOOM
                mapViewportState.easeToFlatFollowCamera(target, zoom)
            }
        }
        wasDriveNavigation = latestDriveNavigation
    }

    LaunchedEffect(Unit) {
        var renderedLat = latestTarget?.latitude
        var renderedLng = latestTarget?.longitude
        var renderedBearing = 0f
        var previousFix: LocationFix? = null
        var wasDriveNavigationLoop = false
        var lastFrameTimeMs: Long? = null
        var cachedDrivePadding: com.mapbox.maps.EdgeInsets? = null
        var cachedDrivePaddingKey = Long.MIN_VALUE

        while (isActive) {
            var frameDeltaMs = 16L
            withFrameMillis { frameTimeMs ->
                frameDeltaMs =
                    lastFrameTimeMs?.let { (frameTimeMs - it).coerceIn(1L, 100L) } ?: 16L
                lastFrameTimeMs = frameTimeMs
            }
            if (latestEnabled) {
                val target = latestTarget
                val fix = latestDeviceFix
                val driveNavigation = latestDriveNavigation
                if (target != null) {
                    if (driveNavigation) {
                        if (!wasDriveNavigationLoop) {
                            renderedLat = target.latitude
                            renderedLng = target.longitude
                            renderedBearing = MapDriveCamera.driveBearing(fix, previousFix, renderedBearing)
                            cachedDrivePadding = null
                            cachedDrivePaddingKey = Long.MIN_VALUE
                        }
                        val targetBearing =
                            MapDriveCamera.driveBearing(fix, previousFix, renderedBearing)
                        val currentLat = renderedLat ?: target.latitude
                        val currentLng = renderedLng ?: target.longitude
                        val positionAlpha =
                            MapDriveCamera.smoothAlpha(
                                frameDeltaMs,
                                MapDriveCamera.DRIVE_POSITION_SMOOTH_PER_FRAME_60HZ,
                            )
                        val bearingAlpha =
                            MapDriveCamera.smoothAlpha(
                                frameDeltaMs,
                                MapDriveCamera.DRIVE_BEARING_SMOOTH_PER_FRAME_60HZ.toDouble(),
                            ).toFloat()
                        val newLat =
                            MapDriveCamera.interpolate(currentLat, target.latitude, positionAlpha)
                        val newLng =
                            MapDriveCamera.interpolate(currentLng, target.longitude, positionAlpha)
                        val newBearing =
                            MapDriveCamera.interpolateBearing(
                                renderedBearing,
                                targetBearing,
                                bearingAlpha,
                            )
                        if (
                            MapDriveCamera.shouldStepDriveCamera(
                                currentLat = currentLat,
                                currentLng = currentLng,
                                currentBearing = renderedBearing,
                                newLat = newLat,
                                newLng = newLng,
                                newBearing = newBearing,
                            )
                        ) {
                            renderedLat = newLat
                            renderedLng = newLng
                            renderedBearing = newBearing
                            val chrome = latestDriveFollowChrome
                            val paddingKey =
                                chrome?.let { MapDriveCamera.driveFollowPaddingStableKey(it) }
                                    ?: 0L
                            if (paddingKey != cachedDrivePaddingKey) {
                                cachedDrivePaddingKey = paddingKey
                                cachedDrivePadding =
                                    chrome?.let { MapDriveCamera.driveFollowPadding(it) }
                            }
                            latestOnProgrammaticCameraMove()
                            mapViewportState.moveToDriveFollowCamera(
                                target = LatLng(newLat, newLng),
                                zoom = MapDriveCamera.DRIVE_ZOOM,
                                bearing = newBearing,
                                pitch = MapDriveCamera.DRIVE_PITCH_DEGREES,
                                padding = cachedDrivePadding,
                            )
                        }
                        previousFix = fix
                    } else {
                        lastFrameTimeMs = null
                        if (wasDriveNavigationLoop) {
                            renderedLat = target.latitude
                            renderedLng = target.longitude
                            renderedBearing = 0f
                            previousFix = null
                            cachedDrivePadding = null
                            cachedDrivePaddingKey = Long.MIN_VALUE
                        }
                        val zoom = mapViewportState.cameraState?.zoom ?: MapDriveCamera.DRIVE_ZOOM
                        mapViewportState.jumpToLatLngZoom(target, zoom)
                    }
                    wasDriveNavigationLoop = driveNavigation
                }
            }
        }
    }
}

/** Match iOS `keepInactiveOnMapFor`: peer pins linger briefly after `isActive` goes false (people sheet only lists active sharers). */
private const val MAP_PEER_INACTIVE_SHARING_GRACE_SEC = 30L

private fun currentlySharingMembersForSheet(
    plotted: List<PresenceMemberDto>,
    meUserId: String?,
    contacts: List<UserDto>,
): List<PresenceMemberDto> {
    fun sortKey(m: PresenceMemberDto): String {
        val u = contacts.find { ottoUserIdsEqual(it.id, m.userId) }
        return u?.displayName?.trim()?.lowercase().orEmpty().ifBlank { m.userId.lowercase() }
    }
    return plotted
        .filter { it.isActive }
        .sortedWith(compareBy({ !ottoUserIdsEqual(it.userId, meUserId) }, { sortKey(it) }))
}

internal fun presenceMembersForCircleId(
    membersByCircleId: Map<String, List<PresenceMemberDto>>,
    circleId: String?,
): List<PresenceMemberDto> {
    val id = circleId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return membersByCircleId.entries.firstOrNull { (key, _) -> ottoUserIdsEqual(key, id) }?.value.orEmpty()
}

internal fun mapSquadActiveSharingCount(
    circleId: String?,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
): Int =
    presenceMembersForCircleId(presenceMembersByCircleId, circleId)
        .count { it.isActive && geoLatLngOrNull(it.lat, it.lng) != null }

internal fun sortedCirclesForMapSquadList(
    circles: List<CircleDto>,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
): List<CircleDto> =
    circles.sortedWith(
        compareByDescending<CircleDto> { circle ->
            mapSquadActiveSharingCount(circle.id, presenceMembersByCircleId)
        }.thenBy { it.name.lowercase() },
    )

internal fun squadMemberPresenceSummary(
    memberCount: Int,
    presenceMembers: List<PresenceMemberDto>,
    mutedColor: Color = Color.Unspecified,
): AnnotatedString {
    val now = Instant.now()
    val onlineCount =
        presenceMembers.count { member ->
            member.inApp != false && !presencePayloadStale(member.updatedAt, now)
        }
    val sharingCount =
        presenceMembers.count { member ->
            member.isActive && geoLatLngOrNull(member.lat, member.lng) != null
        }
    val memberLabel = if (memberCount == 1) "member" else "members"
    return buildAnnotatedString {
        if (mutedColor != Color.Unspecified) {
            withStyle(SpanStyle(color = mutedColor)) {
                append("$memberCount $memberLabel")
                if (onlineCount > 0) {
                    append(" · $onlineCount online")
                }
            }
        } else {
            append("$memberCount $memberLabel")
            if (onlineCount > 0) {
                append(" · $onlineCount online")
            }
        }
        if (sharingCount > 0) {
            withStyle(SpanStyle(color = Color(0xFF34C759))) {
                append(" · $sharingCount sharing")
            }
        }
    }
}

private fun presenceMatchesPeopleSharingSearch(
    member: PresenceMemberDto,
    contacts: List<UserDto>,
    query: String,
): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val user = contacts.find { ottoUserIdsEqual(it.id, member.userId) }
    val name = user?.displayName?.lowercase().orEmpty()
    val handle = user?.handle?.lowercase().orEmpty()
    return name.contains(q) || handle.contains(q) || member.userId.lowercase().contains(q)
}

@Composable
private fun presenceSharingUpdateLabel(updatedAt: String?): String {
    val justNow = stringResource(R.string.map_presence_updated_just_now)
    if (updatedAt.isNullOrBlank()) return justNow
    val inst =
        runCatching { Instant.parse(updatedAt.trim()) }.getOrNull() ?: return justNow
    val secs = Duration.between(inst, Instant.now()).seconds.coerceAtLeast(0)
    if (secs < 60) return justNow
    val mid =
        when {
            secs >= 86400 -> "${secs / 86400}d"
            secs >= 3600 -> "${secs / 3600}h"
            else -> "${secs / 60}m"
        }
    return stringResource(R.string.map_presence_updated_ago, mid)
}

@Composable
private fun OttoMapSideFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border: BorderStroke =
        BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    styledLikeDrive: Boolean = false,
    active: Boolean = false,
) {
    val driveBorder =
        BorderStroke(
            width = if (active) 2.5.dp else 1.5.dp,
            color =
                DriveSessionColors.sessionPurple.copy(
                    alpha = if (active) 0.85f else 0.45f,
                ),
        )
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .size(48.dp)
                .then(
                    if (styledLikeDrive) {
                        Modifier.shadow(
                            elevation = if (active) 10.dp else 6.dp,
                            shape = CircleShape,
                            ambientColor =
                                DriveSessionColors.sessionPurple.copy(
                                    alpha = if (active) 0.45f else 0.25f,
                                ),
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = CircleShape,
        tonalElevation = if (styledLikeDrive) 0.dp else 3.dp,
        color =
            if (styledLikeDrive) {
                Color.Black.copy(alpha = 0.86f)
            } else {
                containerColor
            },
        border = if (styledLikeDrive) driveBorder else border,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (styledLikeDrive) Color.White else iconTint,
            )
        }
    }
}

@Composable
private fun OttoMapDriveStyleFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .size(64.dp)
                .shadow(
                    elevation = if (active) 14.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor =
                        DriveSessionColors.sessionPurple.copy(
                            alpha = if (active) 0.45f else 0.25f,
                        ),
                ),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.86f),
        border =
            BorderStroke(
                width = if (active) 2.5.dp else 1.5.dp,
                color =
                    DriveSessionColors.sessionPurple.copy(
                        alpha = if (active) 0.85f else 0.45f,
                    ),
            ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

internal fun presenceMemberAvatarLabel(member: PresenceMemberDto, contacts: List<UserDto>, me: UserDto?): Pair<String, String?> {
    val u =
        contacts.find { ottoUserIdsEqual(it.id, member.userId) }
            ?: me?.takeIf { ottoUserIdsEqual(it.id, member.userId) }
    val name =
        u
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: shortenId(member.userId)
    return Pair(name, u?.avatarUrl)
}

@Composable
private fun squadCircleProfileSubtitle(
    circle: CircleDto,
    contacts: List<UserDto>,
    myUserId: String?,
): String {
    val trimmedDescription =
        circle.description
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "Created from iOS app" }
    if (trimmedDescription != null) {
        return trimmedDescription
    }
    val ownerWho =
        when {
            !myUserId.isNullOrBlank() && ottoUserIdsEqual(circle.ownerId, myUserId) ->
                stringResource(R.string.squads_created_by_you)
            else ->
                contacts
                    .find { ottoUserIdsEqual(it.id, circle.ownerId) }
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: shortenId(circle.ownerId)
        }
    return stringResource(R.string.squads_created_by_format, ownerWho)
}

@Composable
private fun MapAudienceCreatedByLine(ownerId: String, contacts: List<UserDto>, me: UserDto?) {
    val ownerNameLabel =
        when {
            me != null && ottoUserIdsEqual(ownerId, me.id) -> stringResource(R.string.squads_created_by_you)
            else ->
                contacts
                    .find { ottoUserIdsEqual(it.id, ownerId) }
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.squads_owner_unknown)
        }
    Text(
        stringResource(R.string.squads_created_by_format, ownerNameLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}



private fun sharedSquadsWithPeer(
    circles: List<CircleDto>,
    meUserId: String?,
    peerUserId: String,
): List<CircleDto> {
    val me = meUserId?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val peer = peerUserId.trim().takeIf { it.isNotBlank() } ?: return emptyList()
    return circles
        .filter { circle ->
            val members = circle.members.orEmpty()
            members.any { ottoUserIdsEqual(it.userId, me) } &&
                members.any { ottoUserIdsEqual(it.userId, peer) }
        }
        .sortedBy { it.name.lowercase(Locale.US) }
}

private fun canDirectMessagePresencePeer(
    circles: List<CircleDto>,
    meUserId: String?,
    peerUserId: String,
    blockedPeerIds: Set<String> = emptySet(),
): Boolean {
    if (blockedPeerIds.any { ottoUserIdsEqual(it, peerUserId) }) return false
    return sharedSquadsWithPeer(circles, meUserId, peerUserId).isNotEmpty()
}

@Composable
private fun mapPresenceMemberMovementSubtitle(member: PresenceMemberDto): String? {
    if (!member.isActive) return null
    val mode = normalizePresenceMovementMode(member)
    val label =
        stringResource(
            when (mode) {
                "driving" -> R.string.map_cluster_movement_driving
                "walking" -> R.string.map_cluster_movement_walking
                else -> R.string.map_member_profile_sharing_location
            },
        )
    val spd = (member.speedMph ?: 0.0).roundToInt().coerceAtLeast(0)
    return if (spd > 0) "$label · $spd mph" else label
}

@Composable
private fun MapMemberProfileActionTile(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    emphasize: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (iconTint, textColor, surfaceColor) =
        when {
            !enabled ->
                Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                )

            emphasize ->
                Triple(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                )

            else ->
                Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                )
        }

    Surface(
        modifier =
            modifier
                .height(94.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

/** Map hit-testing order: child [clickable] wins over parent [combinedClickable] for taps. */
private fun presenceOrStubForChatPeer(
    userId: String,
    preferredCircleId: String,
    roster: List<PresenceMemberDto>?,
): PresenceMemberDto {
    val uid = userId.trim()
    val pref = preferredCircleId.trim().ifEmpty { "chat" }
    if (uid.isEmpty()) {
        return PresenceMemberDto(
            userId = "",
            circleId = pref,
            isActive = false,
            inApp = null,
            speedMph = null,
            movementMode = null,
            lat = null,
            lng = null,
            updatedAt = null,
        )
    }
    roster?.firstOrNull { ottoUserIdsEqual(it.userId, uid) }?.let { return it }
    return PresenceMemberDto(
        userId = uid,
        circleId = pref,
        isActive = false,
        inApp = null,
        speedMph = null,
        movementMode = null,
        lat = null,
        lng = null,
        updatedAt = null,
    )
}

private fun presenceFromMapForUser(
    userId: String,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
): PresenceMemberDto? {
    val uid = userId.trim()
    if (uid.isEmpty()) return null
    presenceMembersByCircleId.values.forEach { list ->
        list.firstOrNull { ottoUserIdsEqual(it.userId, uid) }?.let { return it }
    }
    return null
}

private fun presenceOrStubForDmPeer(
    userId: String,
    circles: List<CircleDto>,
    myUserId: String?,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
    fallbackCircleId: String,
): PresenceMemberDto {
    val uid = userId.trim()
    presenceFromMapForUser(uid, presenceMembersByCircleId)?.let { return it }
    val shared =
        sharedSquadsWithPeer(circles, myUserId, uid)
            .firstOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    val cid =
        shared
            ?: fallbackCircleId.trim().ifEmpty { "dm" }
    return PresenceMemberDto(
        userId = uid,
        circleId = cid,
        isActive = false,
        inApp = null,
        speedMph = null,
        movementMode = null,
        lat = null,
        lng = null,
        updatedAt = null,
    )
}

internal fun squadMemberPresenceOrStubForCircle(
    userId: String,
    circleId: String,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
): PresenceMemberDto {
    val uid = userId.trim()
    val cid = circleId.trim().takeIf { it.isNotEmpty() } ?: "squad"
    presenceMembersByCircleId[cid]
        ?.firstOrNull { ottoUserIdsEqual(it.userId, uid) }
        ?.let { return it }
    return PresenceMemberDto(
        userId = uid,
        circleId = cid,
        isActive = false,
        inApp = null,
        speedMph = null,
        movementMode = null,
        lat = null,
        lng = null,
        updatedAt = null,
    )
}

@Composable
internal fun MapMemberProfileSheetContent(
    member: PresenceMemberDto,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    meUser: UserDto?,
    myUserId: String?,
    onMessage: () -> Unit,
    onViewProfile: () -> Unit,
    onOpenSharedSquad: (CircleDto) -> Unit,
    squadManagementCircleId: String? = null,
    onKickCircleMember: (circleId: String, userId: String) -> Unit = { _, _ -> },
    onPatchCircleMemberRole: (circleId: String, userId: String, role: String) -> Unit = { _, _, _ -> },
) {
    val blockedPeerIds =
        remember(meUser) {
            meUser?.blockedUserIds.orEmpty().mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
        }
    val isSelf = !myUserId.isNullOrBlank() && ottoUserIdsEqual(member.userId, myUserId)
    val canDm =
        !isSelf &&
            canDirectMessagePresencePeer(circles, myUserId, member.userId, blockedPeerIds)
    val shared =
        remember(circles, myUserId, member.userId) {
            sharedSquadsWithPeer(circles, myUserId, member.userId)
        }
    val (displayName, rawAvatar) = presenceMemberAvatarLabel(member, contacts, meUser)
    val peerContact =
        contacts.find { ottoUserIdsEqual(it.id, member.userId) }
            ?: meUser?.takeIf { ottoUserIdsEqual(it.id, member.userId) }
    val accent = mapAccentComposeColor(peerContact?.mapAccentKey)
    val movement = mapPresenceMemberMovementSubtitle(member)
    val statusDot = presenceLifecycleDotColor(member)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .ottoBottomSheetContent(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(92.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(88.dp)
                        .border(2.dp, accent, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                        .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                UserProfileAvatar(
                    displayName = displayName,
                    userId = member.userId,
                    avatarUrl = rawAvatar,
                    mapAccentKey = peerContact?.mapAccentKey,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(statusDot)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }

        Text(
            displayName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier =
                Modifier
                    .padding(top = 10.dp)
                    .padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        movement?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
        }

        if (shared.isNotEmpty()) {
            Text(
                stringResource(R.string.map_member_profile_member_of),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                shared.forEach { circle ->
                    ElevatedCard(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                onOpenSharedSquad(circle)
                            },
                    ) {
                        OttoSquadRow(
                            circle = circle,
                            contacts = contacts,
                            meUser = meUser,
                            myUserId = myUserId,
                            showTrailingChevron = true,
                            subtitleOverride = squadCircleProfileSubtitle(circle, contacts, myUserId),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapMemberProfileActionTile(
                title = stringResource(R.string.map_member_profile_message),
                icon = Icons.Outlined.Forum,
                enabled = canDm,
                emphasize = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (canDm) onMessage()
                },
            )
            MapMemberProfileActionTile(
                title = stringResource(R.string.map_member_profile_view_profile),
                icon = Icons.Outlined.Person,
                enabled = true,
                emphasize = true,
                modifier = Modifier.weight(1f),
                onClick = onViewProfile,
            )
        }

        squadManagementCircleId?.let { cid ->
            circles.find { ottoUserIdsEqual(it.id, cid) }?.let { squadCircle ->
                SquadPeerProfileManagementSection(
                    circle = squadCircle,
                    myUserId = myUserId,
                    peerUserId = member.userId,
                    peerDisplayName = displayName,
                    onPromoteAdmin = {
                        onPatchCircleMemberRole(squadCircle.id, member.userId, "admin")
                    },
                    onDemoteAdmin = {
                        onPatchCircleMemberRole(squadCircle.id, member.userId, "member")
                    },
                    onRemoveFromSquad = {
                        onKickCircleMember(squadCircle.id, member.userId)
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileHeroToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PeerProfileElevatedHeroCard(
    displayName: String,
    userId: String,
    avatarUrl: String?,
    mapAccentKey: String?,
    stats: DrivingStatsDto?,
    showChatButton: Boolean,
    onChat: () -> Unit,
    onProgressionClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    heroOverflow: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val driveStatsVisible = stats?.driveStatsVisible != false
    val pr = if (driveStatsVisible) stats?.progression else null
    val progressBar =
        remember(pr?.progress, pr?.pointsIntoLevel, pr?.pointsRequiredForLevel) {
            val pct = pr?.progress?.toFloat()
            when {
                pct != null -> pct.coerceIn(0f, 1f)
                pr?.pointsRequiredForLevel != null &&
                    pr.pointsRequiredForLevel!! > 0 &&
                    pr.pointsIntoLevel != null ->
                    (pr.pointsIntoLevel!!.toFloat() / pr.pointsRequiredForLevel!!.toFloat())
                        .coerceIn(0f, 1f)

                else -> 0f
            }
        }

    var profileHeroXpReveal by remember { mutableStateOf(false) }
    LaunchedEffect(userId, pr?.tierId) {
        profileHeroXpReveal = false
        delay(48)
        profileHeroXpReveal = true
    }
    val animatedProfileXp by animateFloatAsState(
        targetValue = if (profileHeroXpReveal) progressBar else 0f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "peerProfileHeroXp",
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = Color(0xFF07080B),
                contentColor = Color.White,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        val tierIdStr = pr?.tierId
        val tierPalette =
            if (driveStatsVisible) profileTierPalette(tierIdStr) else profileTierPaletteHiddenDriveStats()
        val tierGlow =
            if (driveStatsVisible) profileTierComposeColor(tierIdStr) else Color.White.copy(alpha = 0.48f)
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    tierGlow.copy(alpha = 0.10f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    tierPalette.accentDeep.copy(alpha = 0.08f),
                                ),
                            start = Offset(0f, 0f),
                            end = Offset(980f, 640f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.26f),
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        tierGlow.copy(alpha = 0.40f),
                                        Color.White.copy(alpha = 0.08f),
                                        tierPalette.accentDeep.copy(alpha = 0.26f),
                                    ),
                                start = Offset(0f, 0f),
                                end = Offset(880f, 520f),
                            ),
                        shape = RoundedCornerShape(28.dp),
                    ),
            )

            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .zIndex(3f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    ProfileHeroToolbarIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.accessibility_squad_back),
                        onClick = onBack,
                    )
                } else {
                    Spacer(Modifier.width(40.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileHeroToolbarIconButton(
                        icon = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.profile_share_profile),
                        onClick = { ctx.shareOttoProfileLine("${displayName.trim()} • Driftd") },
                    )
                    heroOverflow?.invoke()
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .padding(horizontal = 18.dp)
                    .padding(top = 44.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .drawBehind {
                                val c =
                                    Offset(
                                        size.width / 2f,
                                        size.height / 2f,
                                    )
                                val rx = kotlin.math.min(size.width, size.height) / 2f
                                drawCircle(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    tierGlow.copy(alpha = 0.34f),
                                                    Color.Transparent,
                                                ),
                                            center = c,
                                            radius = rx * 1.9f,
                                        ),
                                    radius = rx * 1.28f,
                                    center = c,
                                )
                            }
                            .size(118.dp)
                            .background(profileTierAvatarRingBrush(tierIdStr), CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF121318))
                            .clip(CircleShape),
                ) {
                    UserProfileAvatar(
                        displayName = displayName,
                        userId = userId,
                        avatarUrl = avatarUrl,
                        mapAccentKey = mapAccentKey,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        textStyle =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                            ),
                    )
                }

                Text(
                    displayName,
                    modifier =
                        Modifier
                            .padding(top = 14.dp)
                            .widthIn(max = 320.dp),
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.25).sp,
                            lineHeight = 38.sp,
                        ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                if (driveStatsVisible) {
                    pr?.let { p ->
                        val tierColor = profileTierComposeColor(p.tierId)
                        Column(
                            modifier =
                                Modifier
                                    .padding(top = 14.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { onProgressionClick() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Image(
                                    painter = painterResource(progressionLevelBadgeRes(p.level)),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    profileProgressionOrdinalLabel(p),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = tierColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.progression_inline_sep),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = Color.White.copy(alpha = 0.38f),
                                )
                                Text(
                                    stringResource(R.string.profile_level_format, p.level ?: 1),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = Color.White.copy(alpha = 0.76f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = stringResource(R.string.progression_show_tiers_cd),
                                    tint = Color.White.copy(alpha = 0.42f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            PremiumProfileXpBar(
                                progress = animatedProfileXp,
                                tierId = p.tierId,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                            )

                            Text(
                                profileProgressionPointsCaption(p),
                                modifier = Modifier.padding(top = 8.dp),
                                style =
                                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = tierColor,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.profile_drive_stats_private_note),
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                    )
                }

                if (showChatButton) {
                    Surface(
                        modifier =
                            Modifier
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable(role = Role.Button, onClick = onChat),
                        color = Color.White.copy(alpha = 0.12f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Forum,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.profile_peer_chat),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquadPeerProfileManagementSection(
    circle: CircleDto,
    myUserId: String?,
    peerUserId: String,
    peerDisplayName: String,
    onPromoteAdmin: () -> Unit,
    onDemoteAdmin: () -> Unit,
    onRemoveFromSquad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = squadPeerManagementUiState(circle, myUserId, peerUserId) ?: return
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmPromoteAdmin by remember { mutableStateOf(false) }
    var confirmDemoteAdmin by remember { mutableStateOf(false) }
    val danger = Color(0xFFFF453A)
    val peerLabel = peerDisplayName.trim().ifEmpty { stringResource(R.string.squad_peer_fallback_display_name) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 16.dp)
            .ottoBottomSheetContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        if (state.promoteToAdmin) {
            OutlinedButton(
                onClick = { confirmPromoteAdmin = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.squad_peer_make_admin))
            }
        }
        if (state.demoteAdmin) {
            OutlinedButton(
                onClick = { confirmDemoteAdmin = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.squad_peer_remove_admin))
            }
        }
        if (state.removeFromSquad) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, danger.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .background(danger.copy(alpha = 0.07f))
                    .clickable { confirmRemove = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = danger.copy(alpha = 0.92f),
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.squad_peer_remove_from_squad),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = danger.copy(alpha = 0.95f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.squad_peer_remove_from_squad_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.NavigateNext,
                    contentDescription = null,
                    tint = danger.copy(alpha = 0.42f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.squad_peer_remove_confirm_title)) },
            text = { Text(stringResource(R.string.squad_peer_remove_from_squad_subtitle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemoveFromSquad()
                    },
                ) {
                    Text(
                        stringResource(R.string.squad_peer_remove_confirm_action),
                        color = danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmPromoteAdmin) {
        AlertDialog(
            onDismissRequest = { confirmPromoteAdmin = false },
            title = { Text(stringResource(R.string.squad_peer_make_admin_confirm_title)) },
            text = {
                Text(stringResource(R.string.squad_peer_make_admin_confirm_body, peerLabel))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPromoteAdmin = false
                        onPromoteAdmin()
                    },
                ) {
                    Text(stringResource(R.string.squad_peer_make_admin_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPromoteAdmin = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmDemoteAdmin) {
        AlertDialog(
            onDismissRequest = { confirmDemoteAdmin = false },
            title = { Text(stringResource(R.string.squad_peer_demote_admin_confirm_title)) },
            text = {
                Text(stringResource(R.string.squad_peer_demote_admin_confirm_body, peerLabel))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDemoteAdmin = false
                        onDemoteAdmin()
                    },
                ) {
                    Text(
                        stringResource(R.string.squad_peer_demote_admin_confirm_action),
                        color = danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDemoteAdmin = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPeerProfileFullscreenOverlay(
    overlay: MapPeerProfileOverlayUi,
    contacts: List<UserDto>,
    circles: List<CircleDto>,
    myUserId: String?,
    meUser: UserDto? = null,
    onDismiss: () -> Unit,
    onChatPeer: (String) -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onReportConcern: () -> Unit = {},
    onBlockPeer: (String) -> Unit = {},
    onUnblockPeer: (String) -> Unit = {},
) {
    BackHandler(onBack = onDismiss)
    val peerId = overlay.userId
    val peerContact = contacts.find { ottoUserIdsEqual(it.id, peerId) }
    val displayName =
        peerContact
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: overlay.seedDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: shortenId(peerId)
    val avatarUrl = peerContact?.avatarUrl ?: overlay.seedAvatarUrl
    val accentMapKey = peerContact?.mapAccentKey ?: overlay.seedMapAccentKey
    val blockedPeerIds =
        remember(meUser) {
            meUser?.blockedUserIds.orEmpty().mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
        }
    val canDm =
        remember(peerId, circles, myUserId, blockedPeerIds) {
            canDirectMessagePresencePeer(circles, myUserId, peerId, blockedPeerIds)
        }
    val isPeerSelf = !myUserId.isNullOrBlank() && ottoUserIdsEqual(peerId, myUserId)
    var peerOverflowExpanded by remember(peerId) { mutableStateOf(false) }
    var confirmBlockPeer by remember(peerId) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var showProgressionTiers by remember { mutableStateOf(false) }
    var peerGarageSheet by remember { mutableStateOf(false) }
    val peerGarageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val primaryCar =
        remember(overlay.garageCars) {
            overlay.garageCars.firstOrNull { it.isPrimary == true }
                ?: overlay.garageCars.firstOrNull()
        }

    val dashLabel = stringResource(R.string.profile_stat_no_data)
    val lastDriveUnknown = stringResource(R.string.profile_last_drive_unknown)
    val eventsNone = stringResource(R.string.profile_stat_events_none)

    OttoFullscreenOverlay(
        topBar = {},
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (confirmBlockPeer) {
                AlertDialog(
                    onDismissRequest = { confirmBlockPeer = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmBlockPeer = false
                                onBlockPeer(peerId)
                            },
                        ) {
                            Text(stringResource(R.string.safety_block_member))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmBlockPeer = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                    title = { Text(stringResource(R.string.safety_block_confirm_title)) },
                    text = { Text(stringResource(R.string.safety_block_confirm_message)) },
                )
            }

            Box(Modifier.weight(1f)) {
                if (overlay.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    val profileCtxScroll = LocalContext.current
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PeerProfileElevatedHeroCard(
                            displayName = displayName,
                            userId = peerId,
                            avatarUrl = avatarUrl,
                            mapAccentKey = accentMapKey,
                            stats = overlay.stats,
                            showChatButton = canDm,
                            onChat = { onChatPeer(peerId) },
                            onProgressionClick = { showProgressionTiers = true },
                            onBack = onDismiss,
                            heroOverflow =
                                if (!isPeerSelf) {
                                    {
                                        Box {
                                            ProfileHeroToolbarIconButton(
                                                icon = Icons.Outlined.MoreHoriz,
                                                contentDescription = stringResource(R.string.messages_overflow_menu),
                                                onClick = { peerOverflowExpanded = true },
                                            )
                                            DropdownMenu(
                                                expanded = peerOverflowExpanded,
                                                onDismissRequest = { peerOverflowExpanded = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.safety_report_concern)) },
                                                    onClick = {
                                                        peerOverflowExpanded = false
                                                        onReportConcern()
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Outlined.Forum, contentDescription = null)
                                                    },
                                                )
                                                val alreadyBlocked =
                                                    blockedPeerIds.any { ottoUserIdsEqual(it, peerId) }
                                                if (alreadyBlocked) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.safety_unblock_member)) },
                                                        onClick = {
                                                            peerOverflowExpanded = false
                                                            onUnblockPeer(peerId)
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                                                        },
                                                    )
                                                } else {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.safety_block_member)) },
                                                        onClick = {
                                                            peerOverflowExpanded = false
                                                            confirmBlockPeer = true
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.Block, contentDescription = null)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                        )

                        overlay.loadError?.takeIf { it.isNotBlank() }?.let { err ->
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.profile_peer_garage_heading, displayName),
                                modifier = Modifier.weight(1f),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Row(
                                modifier = Modifier.clickable { peerGarageSheet = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.profile_view_arrow),
                                    style =
                                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        if (primaryCar != null) {
                            GarageCarCard(
                                car = primaryCar,
                                readOnly = true,
                                onClick = { peerGarageSheet = true },
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 1.dp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { peerGarageSheet = true },
                            ) {
                                Text(
                                    stringResource(R.string.profile_peer_garage_empty),
                                    modifier = Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val s = overlay.stats
                        val milesVal =
                            if (s != null && s.totalMilesDriven.isFinite()) {
                                "%.0f mi".format(s.totalMilesDriven)
                            } else dashLabel

                        val timeVal = s?.let { profileFormatDriveDurationSeconds(it.totalDriveTimeSeconds) } ?: dashLabel

                        val avgVal =
                            s?.takeIf { it.avgSpeedMph.isFinite() }?.let {
                                "${"%.0f".format(it.avgSpeedMph)} mph"
                            } ?: dashLabel

                        val topVal =
                            s?.takeIf { it.topSpeedMph.isFinite() }?.let {
                                "${"%.0f".format(it.topSpeedMph)} mph"
                            } ?: dashLabel

                        val lastDriveVal =
                            s?.lastDriveAt?.let { iso ->
                                profileRelativeLastDriveLabel(iso, lastDriveUnknown)
                            } ?: dashLabel

                        val eventsVal =
                            if (s == null) dashLabel else if (s.eventsAttended <= 0) eventsNone else "${s.eventsAttended}"

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.profile_stats_heading),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                )

                                Spacer(Modifier.height(12.dp))

                                when {
                                    s == null -> {
                                        Text(
                                            stringResource(R.string.profile_stats_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.55f),
                                        )
                                    }
                                    s.driveStatsVisible == false -> {
                                        Text(
                                            stringResource(R.string.profile_stats_peer_private),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.55f),
                                        )
                                    }
                                    else -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                ProfileStatTile(
                                                    Icons.Outlined.Route,
                                                    milesVal,
                                                    stringResource(R.string.profile_stat_miles),
                                                    Modifier.weight(1f),
                                                )
                                                ProfileStatTile(
                                                    Icons.Outlined.Schedule,
                                                    timeVal,
                                                    stringResource(R.string.profile_stat_drive_time),
                                                    Modifier.weight(1f),
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                ProfileStatTile(
                                                    Icons.Outlined.Speed,
                                                    avgVal,
                                                    stringResource(R.string.profile_stat_avg_speed),
                                                    Modifier.weight(1f),
                                                )
                                                ProfileStatTile(
                                                    Icons.Outlined.Speed,
                                                    topVal,
                                                    stringResource(R.string.profile_stat_top_speed),
                                                    Modifier.weight(1f),
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                ProfileStatTile(
                                                    Icons.Outlined.Schedule,
                                                    lastDriveVal,
                                                    stringResource(R.string.profile_stat_last_drive),
                                                    Modifier.weight(1f),
                                                )
                                                ProfileStatTile(
                                                    Icons.Outlined.ConfirmationNumber,
                                                    eventsVal,
                                                    stringResource(R.string.profile_stat_events),
                                                    Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (overlay.publicGoingEvents.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        stringResource(R.string.profile_peer_public_events_heading),
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    for (ev in overlay.publicGoingEvents) {
                                        Surface(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 10.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { onOpenEventDetail(ev.id) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.White.copy(alpha = 0.055f),
                                        ) {
                                            Row(
                                                Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color.White.copy(alpha = 0.08f)),
                                                ) {
                                                    val bUrl =
                                                        ev.bannerImageUrl?.let { MediaUrlResolver.resolve(it)?.toString() }
                                                    if (!bUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = ottoImageRequest(profileCtxScroll, bUrl),
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop,
                                                        )
                                                    } else {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Outlined.CalendarMonth,
                                                                contentDescription = null,
                                                                tint = Color.White.copy(alpha = 0.45f),
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        ev.name,
                                                        style =
                                                            MaterialTheme.typography.bodyLarge.copy(
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        profilePublicEventWhenLabel(ev.startsAt),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.55f),
                                                    )
                                                    val loc = ev.addressLabel?.trim().orEmpty()
                                                    if (loc.isNotEmpty()) {
                                                        Text(
                                                            loc,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.White.copy(alpha = 0.40f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showProgressionTiers) {
        OttoFullscreenDialog(
            onDismissRequest = { showProgressionTiers = false },
            topBar = {
                OttoFullscreenOpaqueTopBar {
                    IconButton(onClick = { showProgressionTiers = false }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.progression_back_cd),
                            tint = Color.White,
                        )
                    }
                }
            },
        ) { contentPadding ->
            ProgressionTiersFullScreen(contentPadding = contentPadding)
        }
    }

    if (peerGarageSheet) {
        ModalBottomSheet(
            onDismissRequest = { peerGarageSheet = false },
            sheetState = peerGarageSheetState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().ottoBottomSheetContent(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MapSheetHeader(
                        title = stringResource(R.string.profile_peer_garage_heading, displayName),
                        onDone = { peerGarageSheet = false },
                    )
                }
                if (overlay.garageCars.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.profile_peer_garage_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(overlay.garageCars, key = { it.id }) { car ->
                        GarageCarCard(car = car, readOnly = true)
                    }
                }
            }
        }
    }
}


private data class QuickDriveShareStart(
    val saveToProfile: Boolean,
    val circleIds: Set<String>,
)

private data class PendingDrivePermissionStart(
    val saveToProfile: Boolean,
    val shareLive: Boolean,
    val circleIds: Set<String>,
)

private sealed interface MapMarkerDetailPeek {
    data class SavedPlace(val place: SavedPlaceDto) : MapMarkerDetailPeek

    data class Event(
        val primary: EventDto,
        val siblings: List<EventDto>,
    ) : MapMarkerDetailPeek

    data class RaceTrack(val track: RaceTrackRecord) : MapMarkerDetailPeek
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OttoMapPresencePane(
    ui: OttoShellUiState,
    circles: List<CircleDto>,
    mapSession: MapPaneSessionState,
    onScopeSelected: (String) -> Unit,
    onRetryPresence: () -> Unit,
    onSharingToggle: (Boolean) -> Unit,
    onSharingPermissionRevoked: () -> Unit = {},
    onMapSharingOptions: (durationMinutes: Int?, whileDrivingOnly: Boolean, saveDrive: Boolean) -> Unit,
    onMapShareSaveDrive: (Boolean) -> Unit,
    onStartQuickDrive: (Boolean, Boolean, Set<String>) -> Boolean,
    onSetRecordDriveOnStartEnabled: (Boolean) -> Unit,
    onSelectSharingCar: (String?) -> Unit,
    onEnsureLiveDriveSession: (Boolean) -> Unit,
    onStopDriveSession: () -> Unit,
    onStopLiveSharingOnly: () -> Unit,
    onSetDriveSessionSaveEnabled: (Boolean) -> Unit,
    driveSessionPillPresentation: (Long, String?, Int?) -> DriveSessionPillPresentation,
    formatDriveSessionDuration: (Long, Long) -> String,
    formatDriveSessionDistance: (Double) -> String,
    formatDriveSessionTopSpeed: (Double) -> String,
    onAcknowledgeSharingSafetyDisclaimer: () -> Unit,
    onExtendMapSharing: (durationMinutes: Int) -> Unit,
    onMapLayerShowSavedPlaces: (Boolean) -> Unit,
    onMapLayerShowEvents: (Boolean) -> Unit,
    onMapLayerShowRaceTracks: (Boolean) -> Unit,
    onMapLayerShowTraffic: (Boolean) -> Unit,
    onMapLayerCircleVisible: (String, Boolean) -> Unit,
    onSaveMapPlace: (String, Double, Double, String?) -> Unit,
    onRenameSavedPlace: (String, String) -> Unit,
    onDeleteSavedPlace: (String) -> Unit,
    onDismissSavedPlacesSnack: () -> Unit,
    onMapOpenSquadDetail: (String) -> Unit,
    onMapMessagePresencePeer: (String) -> Unit,
    onMapViewPeerProfile: (String) -> Unit,
    onMapNavigateToOwnProfileTab: () -> Unit,
    onOpenEventDetail: (String) -> Unit,
    onSubmitEventRsvp: (String, String) -> Unit,
    onPrefetchDirectMessages: () -> Unit,
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    onApplyEventAttachedSquads: (String, List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit,
    onConsumePendingMapPresenceFollow: () -> Unit,
    onConsumePendingMapCoordinateFocus: () -> Unit = {},
    onCreateMapRoute: () -> Unit = {},
    onOpenMapRoute: (SavedRouteDto) -> Unit = {},
    onEditMapRoute: (SavedRouteDto) -> Unit = {},
    onDeleteMapRoute: (String) -> Unit = {},
    onRenameMapRoute: suspend (SavedRouteDto, String) -> Boolean = { _, _ -> false },
    modifier: Modifier = Modifier,
) {
    var sharingSheetVisible by rememberSaveable { mutableStateOf(false) }
    var placesSheetVisible by rememberSaveable { mutableStateOf(false) }
    var peopleSharingSheetVisible by rememberSaveable { mutableStateOf(false) }
    var layersSheetVisible by rememberSaveable { mutableStateOf(false) }
    var clusterMembersPick by remember { mutableStateOf<List<PresenceMemberDto>?>(null) }
    var mapMarkerDetailPeek by remember { mutableStateOf<MapMarkerDetailPeek?>(null) }
    var mapEventPeekGroupKey by remember { mutableStateOf<String?>(null) }

    var savedPlacePendingDelete by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var startDriveSheetVisible by rememberSaveable { mutableStateOf(false) }
    var routesMenuVisible by rememberSaveable { mutableStateOf(false) }
    var driveControlsSheetVisible by rememberSaveable { mutableStateOf(false) }
    var isQuickDriveDockVisible by rememberSaveable { mutableStateOf(false) }
    var stopDriveConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var pendingGoLiveAfterStartSheet by rememberSaveable { mutableStateOf(false) }
    var pendingQuickDriveShareStart by remember { mutableStateOf<QuickDriveShareStart?>(null) }
    var pendingDrivePermissionStart by remember { mutableStateOf<PendingDrivePermissionStart?>(null) }
    var pendingDriveOnlyAfterSafety by remember { mutableStateOf<PendingDrivePermissionStart?>(null) }
    var showDriveBackgroundLocationPrimer by remember { mutableStateOf(false) }
    var shareLocationDraft by rememberSaveable { mutableStateOf(false) }
    var shareCircleIdsDraft by remember { mutableStateOf(setOf<String>()) }
    var pendingShareLiveFromControls by rememberSaveable { mutableStateOf(false) }

    val sharingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val routesMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val peopleSharingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val layersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clusterPickSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mapMarkerDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var durationChoiceIndex by remember { mutableIntStateOf(1) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var whileDrivingOnlyDraft by remember { mutableStateOf(false) }
    var saveDriveDraft by remember { mutableStateOf(false) }
    var audienceIdDraft by remember { mutableStateOf("") }
    var sharingSafetyDialogVisible by rememberSaveable { mutableStateOf(false) }
    var sharingSquadRequiredDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingSafetySharingCircleId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(sharingSheetVisible) {
        if (!sharingSheetVisible) {
            durationMenuExpanded = false
            return@LaunchedEffect
        }
        durationChoiceIndex = mapDurationChoiceIndexForSavedMinutes(ui.mapShareDurationMinutes)
        whileDrivingOnlyDraft = ui.mapShareWhileDrivingOnly
        saveDriveDraft = ui.mapShareSaveDrive
        val presence = ui.mapPresenceCircleId.trim()
        audienceIdDraft =
            if (ui.mapSharingLocation && presence.isNotEmpty()) {
                presence
            } else {
                ""
            }
    }

    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun showSnack(msg: String) {
        scope.launch { snackHost.showSnackbar(msg) }
    }

    val sharingSortedCircles =
        remember(circles, ui.squadLastAccessedAtByCircleId) {
            circlesSortedByRecentAccess(circles, ui.squadLastAccessedAtByCircleId)
        }

    var saveDialogOpen by remember { mutableStateOf(false) }
    var savePlaceNameDraft by rememberSaveable { mutableStateOf("") }
    var savePlaceCoordinate by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var savePlaceAddressDraft by remember { mutableStateOf<String?>(null) }
    var mapLongPressCoordinate by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapPlaceActionSheetOpen by remember { mutableStateOf(false) }
    var mapPlaceIsResolving by remember { mutableStateOf(false) }
    var mapPlaceNameDraft by remember { mutableStateOf("") }
    var mapPlaceAddressDraft by remember { mutableStateOf<String?>(null) }
    var adhocPlaceSharePayload by remember { mutableStateOf<MapMarkerSharePayload?>(null) }
    var adhocShareToChatOpen by remember { mutableStateOf(false) }
    val mapPlaceActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(saveDialogOpen) {
        if (saveDialogOpen && savePlaceCoordinate == null) {
            savePlaceNameDraft = ""
            savePlaceAddressDraft = null
        }
    }

    LaunchedEffect(ui.me?.id) {
        if (ui.me?.id.isNullOrBlank()) {
            mapSession.clearChatSharedPlacePeekMarkers()
        }
    }

    LaunchedEffect(ui.pendingSquadChatFocusTick) {
        if (ui.pendingSquadChatFocusTick > 0L) {
            mapMarkerDetailPeek = null
            mapEventPeekGroupKey = null
            mapPlaceActionSheetOpen = false
            adhocShareToChatOpen = false
        }
    }

    var renameTarget by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val scrollSheet = rememberScrollState()

    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val onMapLongPressHandler =
        rememberUpdatedState<(Double, Double) -> Unit> { lat, lng ->
            if (!ui.me?.id.isNullOrBlank()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                mapLongPressCoordinate = lat to lng
                savePlaceCoordinate = lat to lng
                mapPlaceNameDraft = ""
                mapPlaceAddressDraft = null
                mapPlaceIsResolving = true
                mapPlaceActionSheetOpen = true
                scope.launch {
                    val label = MapPlaceLabelResolver.resolve(ctx, lat, lng)
                    mapPlaceIsResolving = false
                    label.name?.trim()?.takeIf { it.isNotEmpty() }?.let { mapPlaceNameDraft = it }
                    mapPlaceAddressDraft = label.addressSummary
                }
            }
        }

    val mapsKeyOk = BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()
    val sharingSafetyPrefs =
        remember(ctx) { ctx.getSharedPreferences("otto_map_preferences", Context.MODE_PRIVATE) }
    val sharingSafetyUserId = ui.me?.id?.trim().orEmpty()
    val sharingSafetyPrefsKey = remember(sharingSafetyUserId) {
        "sharingSafetyDisclaimerAcknowledged:${sharingSafetyUserId.ifBlank { "anonymous" }}"
    }
    var localSharingSafetyAcknowledged by rememberSaveable(sharingSafetyPrefsKey) {
        mutableStateOf(sharingSafetyPrefs.getBoolean(sharingSafetyPrefsKey, false))
    }
    LaunchedEffect(ui.me?.sharingSafetyDisclaimerAcknowledged) {
        if (ui.me?.sharingSafetyDisclaimerAcknowledged == true && !localSharingSafetyAcknowledged) {
            localSharingSafetyAcknowledged = true
            sharingSafetyPrefs.edit().putBoolean(sharingSafetyPrefsKey, true).apply()
        }
    }

    var mapLocationPrimerVisible by remember { mutableStateOf(false) }
    var showMapLocationDeniedModal by remember { mutableStateOf(false) }
    var mapLocationPermissionResponded by rememberSaveable { mutableStateOf(false) }
    var showSharingLocationDeniedModal by remember { mutableStateOf(false) }
    var showSharingActivityDeniedModal by remember { mutableStateOf(false) }
    var pendingPermissionSharingCircleId by rememberSaveable { mutableStateOf<String?>(null) }
    var fineGranted by remember {
        mutableStateOf(fineLocationGranted(ctx))
    }
    var activityGranted by remember {
        mutableStateOf(activityRecognitionGranted(ctx))
    }

    fun finishPendingDrivePermissionStart(degraded: Boolean) {
        val pending = pendingDrivePermissionStart ?: return
        pendingDrivePermissionStart = null
        if (degraded) {
            showSnack(ctx.getString(R.string.drive_background_location_foreground_only_toast))
        }
        onSetRecordDriveOnStartEnabled(pending.saveToProfile)
        onStartQuickDrive(pending.saveToProfile, pending.shareLive, pending.circleIds)
    }

    fun continueToBackgroundLocationPrimerIfNeeded(start: PendingDrivePermissionStart) {
        pendingDrivePermissionStart = start
        when {
            OttoLocationPermissions.backgroundLocationGranted(ctx) ->
                finishPendingDrivePermissionStart(degraded = false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fineGranted ->
                showDriveBackgroundLocationPrimer = true
            else -> finishPendingDrivePermissionStart(degraded = true)
        }
    }

    fun completeEnableSharing(resolvedCircleId: String) {
        val pendingQuickDrive = pendingQuickDriveShareStart
        if (pendingQuickDrive != null) {
            pendingQuickDriveShareStart = null
            pendingPermissionSharingCircleId = null
            continueToBackgroundLocationPrimerIfNeeded(
                PendingDrivePermissionStart(
                    saveToProfile = pendingQuickDrive.saveToProfile,
                    shareLive = true,
                    circleIds = pendingQuickDrive.circleIds,
                ),
            )
            return
        }
        val choice = mapDurationChoices[durationChoiceIndex.coerceIn(0, mapDurationChoices.lastIndex)]
        onMapSharingOptions(choice.resolveToTimerMinutes(), whileDrivingOnlyDraft, saveDriveDraft)
        onScopeSelected(resolvedCircleId)
        onSharingToggle(true)
    }

    val activityRecognitionPerm =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            activityGranted = granted
            val pendingCircleId = pendingPermissionSharingCircleId
            if (granted && !pendingCircleId.isNullOrBlank()) {
                pendingPermissionSharingCircleId = null
                completeEnableSharing(pendingCircleId)
            } else if (!granted) {
                showSharingActivityDeniedModal = true
            }
        }

    val mapPrimerLocationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            mapLocationPermissionResponded = true
            fineGranted = ok
            if (ok) {
                ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
            } else {
                showMapLocationDeniedModal = true
            }
        }

    val sharingLocationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            fineGranted = ok
            ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
            val pendingDrive = pendingDrivePermissionStart
            val pendingCircleId = pendingPermissionSharingCircleId
            if (ok && pendingDrive != null && pendingCircleId.isNullOrBlank()) {
                if (pendingDrive.shareLive) {
                    val anchor =
                        pendingDrive.circleIds.firstOrNull { id ->
                            id.isNotBlank() && circles.any { it.id == id }
                        }
                    if (anchor != null) {
                        pendingQuickDriveShareStart =
                            QuickDriveShareStart(pendingDrive.saveToProfile, pendingDrive.circleIds)
                        pendingPermissionSharingCircleId = anchor
                        activityGranted = activityRecognitionGranted(ctx)
                        when {
                            activityGranted -> {
                                pendingPermissionSharingCircleId = null
                                completeEnableSharing(anchor)
                            }
                            shouldRequestActivityRecognition(ctx) ->
                                activityRecognitionPerm.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            else -> {
                                pendingPermissionSharingCircleId = null
                                showSharingActivityDeniedModal = true
                            }
                        }
                    }
                } else {
                    continueToBackgroundLocationPrimerIfNeeded(pendingDrive)
                }
                return@rememberLauncherForActivityResult
            }
            if (ok && !pendingCircleId.isNullOrBlank()) {
                activityGranted = activityRecognitionGranted(ctx)
                when {
                    activityGranted -> {
                        pendingPermissionSharingCircleId = null
                        completeEnableSharing(pendingCircleId)
                    }
                    shouldRequestActivityRecognition(ctx) -> {
                        activityRecognitionPerm.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    else -> {
                        pendingPermissionSharingCircleId = null
                        showSharingActivityDeniedModal = true
                    }
                }
            } else {
                if (shouldOpenFineLocationAppSettings(ctx)) {
                    pendingPermissionSharingCircleId = null
                    pendingDrivePermissionStart = null
                    runCatching {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        ctx.startActivity(intent)
                    }
                } else {
                    showSharingLocationDeniedModal = true
                }
            }
        }

    val driveBackgroundLocationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            val degraded = !OttoLocationPermissions.backgroundLocationGranted(ctx)
            finishPendingDrivePermissionStart(degraded = degraded)
        }

    LaunchedEffect(mapsKeyOk, fineGranted, mapLocationPermissionResponded) {
        if (!mapsKeyOk || fineGranted) return@LaunchedEffect
        if (!mapLocationPermissionResponded) {
            mapLocationPrimerVisible = true
        } else {
            showMapLocationDeniedModal = true
        }
    }

    LaunchedEffect(fineGranted) {
        if (fineGranted) {
            ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
        }
    }

    DisposableEffect(lifecycleOwner, ui.mapSharingLocation) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    fineGranted = fineLocationGranted(ctx)
                    activityGranted = activityRecognitionGranted(ctx)
                    if (ui.mapSharingLocation && (!fineGranted || !activityGranted)) {
                        onSharingPermissionRevoked()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.mapSharingLocation, fineGranted, activityGranted) {
        if (ui.mapSharingLocation && (!fineGranted || !activityGranted)) {
            onSharingPermissionRevoked()
        }
    }

    fun continueEnableSharingAfterLocation(resolvedCircleId: String) {
        activityGranted = activityRecognitionGranted(ctx)
        when {
            activityGranted -> {
                pendingPermissionSharingCircleId = null
                completeEnableSharing(resolvedCircleId)
            }
            shouldRequestActivityRecognition(ctx) -> {
                pendingPermissionSharingCircleId = resolvedCircleId
                activityRecognitionPerm.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            else -> {
                pendingPermissionSharingCircleId = null
                showSharingActivityDeniedModal = true
            }
        }
    }

    fun finishEnableSharing(resolvedCircleId: String) {
        when {
            !fineGranted -> {
                pendingPermissionSharingCircleId = resolvedCircleId
                if (canRepromptFineLocationAtRuntime(ctx)) {
                    sharingLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    showSharingLocationDeniedModal = true
                }
            }
            else -> continueEnableSharingAfterLocation(resolvedCircleId)
        }
    }

    fun finishEnableSharingForQuickDrive(anchorCircleId: String) {
        when {
            !fineGranted -> {
                pendingPermissionSharingCircleId = anchorCircleId
                if (canRepromptFineLocationAtRuntime(ctx)) {
                    sharingLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    showSharingLocationDeniedModal = true
                }
            }
            else -> continueEnableSharingAfterLocation(anchorCircleId)
        }
    }

    fun beginQuickDriveAfterPermissions(start: PendingDrivePermissionStart) {
        when {
            !fineGranted -> {
                pendingDrivePermissionStart = start
                if (canRepromptFineLocationAtRuntime(ctx)) {
                    sharingLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    showSharingLocationDeniedModal = true
                }
            }
            start.shareLive -> {
                val anchor =
                    start.circleIds.firstOrNull { id ->
                        id.isNotBlank() && circles.any { it.id == id }
                    } ?: return
                pendingQuickDriveShareStart = QuickDriveShareStart(start.saveToProfile, start.circleIds)
                finishEnableSharingForQuickDrive(anchor)
            }
            else -> continueToBackgroundLocationPrimerIfNeeded(start)
        }
    }

    fun beginQuickDriveShareAfterPermissions(saveToProfile: Boolean, circleIds: Set<String>) {
        val first =
            circleIds.firstOrNull { id ->
                id.isNotBlank() && circles.any { it.id == id }
            } ?: return
        pendingQuickDriveShareStart = QuickDriveShareStart(saveToProfile, circleIds)
        finishEnableSharingForQuickDrive(first)
    }

    fun attemptQuickDriveStart(recordDrive: Boolean) {
        if (ui.hasActiveDriveSession) {
            showSnack("End your current drive first")
            return
        }
        if (!shareLocationDraft) {
            val start = PendingDrivePermissionStart(recordDrive, false, emptySet())
            if (!localSharingSafetyAcknowledged) {
                pendingDriveOnlyAfterSafety = start
                sharingSafetyDialogVisible = true
                return
            }
            beginQuickDriveAfterPermissions(start)
            return
        }
        if (circles.isEmpty()) {
            showSnack(ctx.getString(R.string.map_sharing_no_squads_hint))
            return
        }
        if (shareCircleIdsDraft.isEmpty()) {
            sharingSquadRequiredDialogVisible = true
            return
        }
        if (!localSharingSafetyAcknowledged) {
            pendingQuickDriveShareStart = QuickDriveShareStart(recordDrive, shareCircleIdsDraft)
            sharingSafetyDialogVisible = true
            return
        }
        beginQuickDriveShareAfterPermissions(recordDrive, shareCircleIdsDraft)
    }

    fun applySharing(enable: Boolean) {
        if (!enable) {
            sharingSheetVisible = false
            onSharingToggle(false)
            return
        }
        val resolved =
            audienceIdDraft.trim().takeIf { id ->
                id.isNotBlank() && circles.any { it.id == id }
            }
        if (resolved == null) {
            return
        }
        sharingSheetVisible = false
        pendingSafetySharingCircleId = resolved
        sharingSafetyDialogVisible = true
    }

    val plotted =
        remember(
            ui.presenceMembers,
            ui.me?.id,
            ui.deviceLocationFix,
            ui.mapSharingLocation,
            ui.mapPresenceCircleId,
            ui.deviceMovementMode,
            fineGranted,
        ) {
            plottedPresenceOverlayingDeviceSelf(
                presenceMembers = ui.presenceMembers,
                me = ui.me,
                deviceFix = ui.deviceLocationFix,
                mapSharingLocation = ui.mapSharingLocation,
                mapPresenceCircleId = ui.mapPresenceCircleId,
                deviceMovementMode = ui.deviceMovementMode,
                fineLocationGranted = fineGranted,
            )
        }

    val meUserId = ui.me?.id

    val friendsSharingLocationCount =
        remember(plotted, meUserId) {
            plotted
                .asSequence()
                .filter { geoLatLngOrNull(it.lat, it.lng) != null }
                .filter { it.isActive }
                .filter { meUserId.isNullOrBlank() || !ottoUserIdsEqual(it.userId, meUserId) }
                .distinctBy { it.userId.trim() }
                .count()
        }

    val peerLastSharingAt = remember { mutableStateMapOf<String, Instant>() }
    LaunchedEffect(plotted, meUserId) {
        val idsInPlotted =
            plotted.mapNotNull { it.userId.trim().takeIf { id -> id.isNotEmpty() } }.toSet()
        val me = meUserId?.trim()?.takeIf { it.isNotEmpty() }
        plotted.forEach { m ->
            val id = m.userId.trim()
            if (id.isEmpty()) return@forEach
            if (me != null && ottoUserIdsEqual(id, me)) return@forEach
            if (m.isActive) {
                peerLastSharingAt[id] = Instant.now()
            }
        }
        peerLastSharingAt.keys.retainAll { it in idsInPlotted }
    }

    val rawPlottedVisibleOnMap =
        plotted.filter { m ->
            if (geoLatLngOrNull(m.lat, m.lng) == null) return@filter false
            val id = m.userId.trim()
            if (id.isEmpty()) return@filter false
            val me = meUserId?.trim()?.takeIf { it.isNotEmpty() }
            if (me != null && ottoUserIdsEqual(id, me)) {
                return@filter fineGranted
            }
            if (m.isActive) return@filter true
            val last = peerLastSharingAt[id] ?: return@filter false
            Duration.between(last, Instant.now()).seconds <= MAP_PEER_INACTIVE_SHARING_GRACE_SEC
        }
    val plottedVisibleOnMap = rememberSmoothedPresenceMembers(rawPlottedVisibleOnMap, meUserId)

    val mapEffectiveCircleIds =
        remember(ui.circles, ui.mapPresenceCircleId, ui.mapLayerSelectedCircleIds) {
            effectiveMapLayerCircleIds(
                ui.circles,
                ui.mapPresenceCircleId,
                ui.mapLayerSelectedCircleIds,
            )
        }

    val mapEvents =
        remember(ui.events, ui.communityEvents, ui.mapLayerShowUpcomingEvents) {
            if (!ui.mapLayerShowUpcomingEvents) {
                emptyList()
            } else {
                (ui.events + ui.communityEvents).distinctBy { it.id }.filter { ev ->
                    isEligibleForMapDisplay(ev)
                }
            }
        }

    val eventAnchorGroups =
        remember(mapEvents) {
            eventProximityGroupsForMap(mapEvents)
        }

    val deviceLatLng =
        ui.deviceLocationFix
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
            ?.let { LatLng(it.latitude, it.longitude) }

    val defaultLatLng =
        when {
            meUserId != null && deviceLatLng != null -> deviceLatLng
            else ->
                plottedVisibleOnMap.firstOrNull()?.let { m -> geoLatLngOrNull(m.lat, m.lng) }
                    ?: ui.savedPlaces.firstOrNull()?.let { sp -> geoLatLngOrNull(sp.latitude, sp.longitude) }
                    ?: deviceLatLng
                    ?: LatLng(37.7749, -122.4194)
        }

    val defaultZoom =
        when {
            meUserId != null && deviceLatLng != null -> 17f
            plottedVisibleOnMap.size > 1 -> 5f
            plottedVisibleOnMap.size == 1 -> 15f
            ui.savedPlaces.isNotEmpty() -> 14f
            deviceLatLng != null -> 16f
            else -> 4f
        }

    val mapViewportState =
        rememberMapViewportState {
            setCameraOptions((mapSession.lastCamera ?: OttoMapCamera(defaultLatLng, defaultZoom)).toMapboxCameraOptions())
        }

    var followDeviceCamera by remember(mapSession) { mutableStateOf(mapSession.followDevice) }
    var followedPresenceUserId by remember(mapSession) { mutableStateOf(mapSession.followedPresenceUserId) }
    var followedSquadId by remember(mapSession) { mutableStateOf(mapSession.followedSquadId) }
    /** True after [plottedVisibleOnMap] has included the followed peer at least once (avoids clearing follow before presence loads). */
    var hasSeenFollowedPeerInPlotted by remember { mutableStateOf(false) }
    var didNonGpsInitialCamera by remember { mutableStateOf(false) }
    var didGpsUserFocus by remember { mutableStateOf(false) }

    LaunchedEffect(ui.pendingMapPresenceFollow?.nonce) {
        val pending = ui.pendingMapPresenceFollow ?: return@LaunchedEffect
        val targetUserId = pending.userId.trim()
        onConsumePendingMapPresenceFollow()
        if (targetUserId.isEmpty()) return@LaunchedEffect
        followDeviceCamera = false
        followedSquadId = null
        followedPresenceUserId = targetUserId
    }

    LaunchedEffect(ui.pendingMapCoordinateFocus?.nonce, mapEvents) {
        val pending = ui.pendingMapCoordinateFocus ?: return@LaunchedEffect
        val lat = pending.latitude
        val lng = pending.longitude
        if (!lat.isFinite() || !lng.isFinite()) {
            onConsumePendingMapCoordinateFocus()
            return@LaunchedEffect
        }
        onConsumePendingMapCoordinateFocus()
        followDeviceCamera = false
        followedPresenceUserId = null
        followedSquadId = null
        didGpsUserFocus = true
        didNonGpsInitialCamera = true
        mapViewportState.easeToLatLngZoom(LatLng(lat, lng), 15.0, 420)
        pending.eventId?.let { eventId ->
            val event =
                resolveEventForMapPeek(
                    eventId = eventId,
                    snapshot = pending.eventSnapshot,
                    mapEvents = mapEvents,
                    events = ui.events,
                    communityEvents = ui.communityEvents,
                    squadFeedEvents = ui.squadFeedEvents,
                    hydratedById = ui.chatAttachmentHydratedEventsById,
                )
            if (event != null) {
                mapEventPeekGroupKey = eventId
                mapMarkerDetailPeek = MapMarkerDetailPeek.Event(event, emptyList())
            }
        } ?: pending.savedPlaceSnapshot?.let { snapshot ->
            mapMarkerDetailPeek = MapMarkerDetailPeek.SavedPlace(snapshot)
            mapSession.registerChatSharedPlacePeekMarker(snapshot, ui.savedPlaces)
        } ?: pending.savedPlaceId?.let { savedPlaceId ->
            ui.savedPlaces.firstOrNull { it.id == savedPlaceId }?.let { place ->
                mapMarkerDetailPeek = MapMarkerDetailPeek.SavedPlace(place)
            }
        }
    }

    LaunchedEffect(followedPresenceUserId) {
        hasSeenFollowedPeerInPlotted = false
    }

    DisposableEffect(mapViewportState, followDeviceCamera, followedPresenceUserId, followedSquadId) {
        onDispose {
            mapSession.lastCamera = mapViewportState.cameraState?.toOttoMapCamera() ?: mapSession.lastCamera
            mapSession.followDevice = followDeviceCamera
            mapSession.followedPresenceUserId = followedPresenceUserId
            mapSession.followedSquadId = followedSquadId
        }
    }

    var mapTypeSatellite by rememberSaveable { mutableStateOf(false) }

    val mapCameraState = mapViewportState.cameraState
    var mapMarkerLodLatitudeDelta by remember { mutableStateOf(0.075) }
    val flushMapMarkerLodFromCamera =
        rememberUpdatedState(newValue = {
            val cs = mapViewportState.cameraState
            if (cs != null) {
                val delta = visibleLatitudeDeltaDegrees(cs.zoom, cs.center.latitude())
                if (kotlin.math.abs(mapMarkerLodLatitudeDelta - delta) > 0.00005) {
                    mapMarkerLodLatitudeDelta = delta
                }
            }
        })
    val isActiveRouteDriveRecordingForLod =
        ui.mapRouteSessionActive &&
            ui.activeDriveSession?.kind == DriveSessionKind.ROUTE

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(mapViewportState, isActiveRouteDriveRecordingForLod) {
        val cameraFlow =
            snapshotFlow {
                mapViewportState.cameraState?.let { cs ->
                    visibleLatitudeDeltaDegrees(cs.zoom, cs.center.latitude())
                }
            }.filterNotNull()
        if (isActiveRouteDriveRecordingForLod) {
            cameraFlow.collect { delta ->
                if (kotlin.math.abs(mapMarkerLodLatitudeDelta - delta) > 0.00005) {
                    mapMarkerLodLatitudeDelta = delta
                }
            }
        } else {
            cameraFlow
                .sample(200)
                .distinctUntilChanged { old, new -> kotlin.math.abs(old - new) <= 0.00005 }
                .collect { delta ->
                    mapMarkerLodLatitudeDelta = delta
                }
        }
    }
    val presenceGroups =
        rememberPresenceProximityGroups(
            plotted = plottedVisibleOnMap,
            cameraZoom = mapCameraState?.zoom?.toFloat() ?: mapSession.lastCamera?.zoom ?: defaultZoom,
            latitudeCenter = mapCameraState?.center?.latitude() ?: mapSession.lastCamera?.target?.latitude ?: defaultLatLng.latitude,
            meUserId = meUserId,
        )
    val mapMarkerContext = LocalContext.current
    val brandLogoUrlsByUserId =
        remember(
            plottedVisibleOnMap,
            ui.selectedSharingCarId,
            ui.garageCars,
            ui.activeDriveSession,
            ui.mapSharingLocation,
            ui.mapRouteSessionActive,
        ) {
            mapPresenceBrandLogoUrlByUserId(
                members = plottedVisibleOnMap,
                meId = ui.me?.id,
                selectedSharingCarId = ui.selectedSharingCarId,
                garageCars = ui.garageCars,
                showsSelfLogo = showsSelfDriveBrandLogoOnMap(ui),
                context = mapMarkerContext,
            )
        }

    val anchorPresenceKey =
        remember(plottedVisibleOnMap) { plottedVisibleOnMap.joinToString { "${it.userId}_${it.lat}_${it.lng}" } }
    val anchorPlacesKey =
        remember(ui.savedPlaces) { ui.savedPlaces.joinToString { it.id } }
    val deviceAnchorKey =
        remember(deviceLatLng) {
            deviceLatLng?.let { "${it.latitude}_${it.longitude}" } ?: ""
        }

    val travelSurfaceTracker = remember { TravelSurfaceTracker() }
    var travelSurfacesByUserId by remember { mutableStateOf<Map<String, TravelSurface>>(emptyMap()) }
    val travelSurfacePresenceKey =
        remember(plottedVisibleOnMap) {
            plottedVisibleOnMap.joinToString("|") { member ->
                "${member.userId}_${member.lat}_${member.lng}_${member.speedMph}_${member.isActive}"
            }
        }

    LaunchedEffect(travelSurfacePresenceKey) {
        if (!MapTravelSurfaceSampler.WATER_SURFACE_DETECTION_ENABLED) return@LaunchedEffect
        while (true) {
            val moving =
                plottedVisibleOnMap.filter { member ->
                    member.isActive && (member.speedMph ?: 0.0) >= MapTravelSurfaceSampler.MIN_SPEED_MPH_FOR_BOAT
                }
            val activeIds =
                moving
                    .mapNotNull { it.userId.trim().takeIf { id -> id.isNotEmpty() } }
                    .toSet()
            travelSurfaceTracker.removeUsersNotIn(activeIds)
            val nowMs = System.currentTimeMillis()
            for (member in moving) {
                val userId = member.userId.trim().takeIf { it.isNotEmpty() } ?: continue
                if (!travelSurfaceTracker.shouldSample(userId, nowMs)) continue
                val lat = member.lat ?: continue
                val lng = member.lng ?: continue
                if (!lat.isFinite() || !lng.isFinite()) continue
                travelSurfaceTracker.markSampled(userId, nowMs)
                sampleTravelSurface(
                    latitude = lat,
                    longitude = lng,
                    speedMph = member.speedMph ?: 0.0,
                    scope = scope,
                ) { instantaneous ->
                    travelSurfaceTracker.ingest(userId, instantaneous)
                    travelSurfacesByUserId = travelSurfaceTracker.snapshot()
                }
            }
            delay(MapTravelSurfaceSampler.sampleThrottleMs())
        }
    }

    val deviceFixAvailable = meUserId != null && deviceLatLng != null

    LaunchedEffect(
        deviceFixAvailable,
        anchorPresenceKey,
        anchorPlacesKey,
        deviceAnchorKey,
        ui.pendingMapCoordinateFocus?.nonce,
    ) {
        if (ui.pendingMapCoordinateFocus != null) return@LaunchedEffect
        if (mapSession.lastCamera != null) return@LaunchedEffect
        if (deviceFixAvailable && !didGpsUserFocus) {
            mapViewportState.easeToLatLngZoom(deviceLatLng!!, 17.0, 450)
            didGpsUserFocus = true
            return@LaunchedEffect
        }
        if (!didNonGpsInitialCamera && !didGpsUserFocus) {
            val presenceCoords = plottedVisibleOnMap.mapNotNull { m -> geoLatLngOrNull(m.lat, m.lng) }
            when {
                presenceCoords.size == 1 -> {
                    mapViewportState.easeToLatLngZoom(presenceCoords.first(), 15.0, 400)
                }
                presenceCoords.size > 1 -> {
                    runCatching { mapViewportState.easeToLatLngBounds(presenceCoords, 64.0, 500) }
                }
                ui.savedPlaces.isNotEmpty() -> {
                    val p = ui.savedPlaces.first()
                    val anchor =
                        geoLatLngOrNull(p.latitude, p.longitude) ?: LatLng(37.7749, -122.4194)
                    mapViewportState.easeToLatLngZoom(anchor, 14.0, 400)
                }
                deviceLatLng != null -> {
                    mapViewportState.easeToLatLngZoom(deviceLatLng!!, 16.0, 400)
                }
                else -> {}
            }
            didNonGpsInitialCamera = true
        }
    }

    val audienceLabel =
        circles.find { it.id == ui.mapPresenceCircleId }?.name
            ?: stringResource(R.string.map_scope_squad_unknown)

    val movementMode =
        remember(ui.deviceMovementMode, ui.deviceLocationFix, ui.mapSharingLocation) {
            if (ui.mapSharingLocation && ui.deviceMovementMode != null) {
                ui.deviceMovementMode
            } else {
                MovementModeIosParity.inferMovementModeFromSpeedMps(
                    (ui.deviceLocationFix?.speedMps ?: 0f).toDouble().coerceAtLeast(0.0),
                )
            }
        }
    val sharingSessionActive = ui.mapSharingLocation
    val sharingPaused =
        sharingSessionActive && ui.mapShareWhileDrivingOnly && movementMode != "driving"

    var driveSessionNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.hasActiveDriveSession) {
        if (!ui.hasActiveDriveSession) return@LaunchedEffect
        while (true) {
            driveSessionNowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val drivePillPresentation =
        driveSessionPillPresentation(
            driveSessionNowMs,
            ui.activeDriveSession?.routeName,
            friendsSharingLocationCount.takeIf { it > 0 },
        )
    val driveSessionStartedAtMs =
        ui.activeDriveSession?.startedAtMs ?: driveSessionNowMs
    val driveSessionTimeText =
        when (val presentation = drivePillPresentation) {
            is DriveSessionPillPresentation.Recording -> presentation.timeText
            is DriveSessionPillPresentation.RecordingAndSharing -> presentation.timeText
            else -> formatDriveSessionDuration(driveSessionStartedAtMs, driveSessionNowMs)
        }
    val driveSessionDistanceText =
        when (val presentation = drivePillPresentation) {
            is DriveSessionPillPresentation.Recording -> presentation.distanceText
            is DriveSessionPillPresentation.RecordingAndSharing -> presentation.distanceText
            else ->
                formatDriveSessionDistance(
                    ui.activeDriveSession?.metrics?.distanceMeters ?: 0.0,
                )
        }
    val driveSessionTopSpeedText =
        formatDriveSessionTopSpeed(
            maxOf(
                ui.activeDriveSession?.metrics?.maxSpeedMph ?: 0.0,
                0.0,
            ),
        )
    val driveControlsShareLive = ui.mapSharingLocation
    val driveControlsSaveDrive =
        ui.activeDriveSession?.isRecording ?: ui.mapShareSaveDrive
    val routeCheckpointText =
        ui.activeDriveSession?.routeProgress?.let { progress ->
            "${progress.completedCount}/${progress.totalCheckpoints} checkpoints"
        }

    val isQuickDriveSessionActive =
        isQuickDriveDockVisible &&
            ui.activeDriveSession?.kind == DriveSessionKind.QUICK
    val isRouteDriveSessionOnMap =
        ui.mapRouteSessionActive &&
            ui.activeDriveSession?.kind == DriveSessionKind.ROUTE
    val usesDriveCameraPitch =
        isQuickDriveSessionActive ||
            ui.mapSharingLocation ||
            isRouteDriveSessionOnMap
    val isActiveRouteDriveRecording = isRouteDriveSessionOnMap
    val activeRouteForMapDrive =
        remember(
            ui.routes,
            ui.activeDriveSession?.routeId,
            ui.activeDriveSession?.routeProgress?.routeId,
        ) {
            val routeId =
                ui.activeDriveSession?.routeId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ui.activeDriveSession?.routeProgress?.routeId?.trim()?.takeIf { it.isNotEmpty() }
            routeId?.let { id -> ui.routes.find { ottoUserIdsEqual(it.id, id) } }
        }
    val driveVisibleMapHeightMeters =
        remember(mapMarkerLodLatitudeDelta) {
            MapDriveHorizonDepth.visibleMapHeightMeters(mapMarkerLodLatitudeDelta)
        }
    val driveHorizonDeviceLatLng = if (usesDriveCameraPitch) deviceLatLng else null
    val quickDriveDockStatusText =
        if (isQuickDriveSessionActive) {
            val session = ui.activeDriveSession
            val time = formatDriveSessionDuration(driveSessionStartedAtMs, driveSessionNowMs)
            val distance =
                formatDriveSessionDistance(session?.metrics?.distanceMeters ?: 0.0)
            val speed = formatDriveSessionTopSpeed(session?.metrics?.maxSpeedMph ?: 0.0)
            "Recording • $time • $distance • $speed"
        } else {
            stringResource(R.string.drive_launch_dock_ready)
        }

    val isLiveDriveSessionActive = ui.mapSharingLocation
    val liveDriveDockStatusText =
        if (isLiveDriveSessionActive) {
            val session = ui.activeDriveSession
            val time = formatDriveSessionDuration(driveSessionStartedAtMs, driveSessionNowMs)
            val distance =
                formatDriveSessionDistance(session?.metrics?.distanceMeters ?: 0.0)
            val speed = formatDriveSessionTopSpeed(session?.metrics?.maxSpeedMph ?: 0.0)
            "Sharing live • $time • $distance • $speed"
        } else {
            stringResource(R.string.drive_launch_dock_ready)
        }

    var mapDriveDockHeightPx by remember { mutableIntStateOf(0) }
    var mapDriveDockCompactHeightPx by remember { mutableIntStateOf(0) }
    var mapViewportSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val mapOverlayBottomPad = 12.dp
    val mapDriveDockHeight = with(LocalDensity.current) { mapDriveDockHeightPx.toDp() }
    val mapDriveDockCompactHeight = with(LocalDensity.current) { mapDriveDockCompactHeightPx.toDp() }
    val driveDockVisible = isQuickDriveDockVisible || ui.mapSharingLocation
    val mapFabDockHeight =
        when {
            shareLocationDraft && mapDriveDockCompactHeightPx > 0 -> mapDriveDockCompactHeight
            mapDriveDockHeightPx > 0 -> mapDriveDockHeight
            else -> null
        }
    val mapFabBottomInset =
        mapOverlayBottomPad +
            if (driveDockVisible && mapFabDockHeight != null) {
                mapFabDockHeight
            } else if (driveDockVisible) {
                132.dp
            } else {
                0.dp
            }
    val mapSideFabSize = 48.dp
    val mapDriveFabSize = 64.dp
    val mapFabSpacing = 10.dp
    val mapFabStackApprox =
        mapSideFabSize + mapFabSpacing + mapSideFabSize + mapFabSpacing + mapDriveFabSize
    val mapSideFabDriveCenterOffset = (mapDriveFabSize - mapSideFabSize) / 2
    val snackbarBottomPad = mapFabBottomInset + mapFabStackApprox + 12.dp

    val bellFabSize = 48.dp
    val topPillEndGap =
        when {
            sharingPaused -> 12.dp
            sharingSessionActive -> 10.dp
            else -> 8.dp
        }
    val topBarBellReserve = bellFabSize + topPillEndGap

    val mePinCoords =
        plottedVisibleOnMap
            .firstOrNull { meUserId != null && ottoUserIdsEqual(it.userId, meUserId) }
            ?.let { geoLatLngOrNull(it.lat, it.lng) }

    LaunchedEffect(usesDriveCameraPitch) {
        if (usesDriveCameraPitch) {
            followDeviceCamera = true
            followedPresenceUserId = null
            followedSquadId = null
        }
    }

    var isApplyingDriveCameraUpdate by remember { mutableStateOf(false) }
    var programmaticCameraGeneration by remember { mutableIntStateOf(0) }
    val markProgrammaticCameraMove: () -> Unit = {
        programmaticCameraGeneration++
        val generation = programmaticCameraGeneration
        isApplyingDriveCameraUpdate = true
        scope.launch {
            delay(450)
            if (programmaticCameraGeneration == generation) {
                isApplyingDriveCameraUpdate = false
            }
        }
    }

    val density = LocalDensity.current
    val screenFallbackMapHeightPx =
        with(density) {
            LocalConfiguration.current.screenHeightDp.dp.toPx()
        }
    val mapViewportHeightPx =
        if (mapViewportSizePx.height > 0) {
            mapViewportSizePx.height.toFloat()
        } else {
            screenFallbackMapHeightPx
        }
    val driveFollowChrome =
        if (!usesDriveCameraPitch) {
            null
        } else {
            with(density) {
                val effectiveMapDriveDockHeightPx =
                    when {
                        shareLocationDraft && mapDriveDockCompactHeightPx > 0 ->
                            mapDriveDockCompactHeightPx.toFloat()
                        mapDriveDockHeightPx > 0 -> mapDriveDockHeightPx.toFloat()
                        driveDockVisible ->
                            MapDriveCamera.DRIVE_DOCK_HEIGHT_FALLBACK_DP.dp.toPx()
                        else -> 0f
                    }
                MapDriveCamera.DriveFollowChromeInsets(
                    mapViewportHeightPx = mapViewportHeightPx,
                    mapDriveDockHeightPx = effectiveMapDriveDockHeightPx,
                    mapOverlayBottomPadPx = mapOverlayBottomPad.toPx(),
                )
            }
        }
    val driveMapPaddingKey =
        if (usesDriveCameraPitch && driveFollowChrome != null) {
            MapDriveCamera.driveFollowPaddingStableKey(driveFollowChrome)
        } else {
            0L
        }

    MapDeviceFollowCameraEffect(
        mapViewportState = mapViewportState,
        followTarget = mePinCoords ?: deviceLatLng,
        enabled =
            followDeviceCamera &&
                followedPresenceUserId == null &&
                followedSquadId == null,
        mapsKeyOk = mapsKeyOk,
        driveNavigationActive = usesDriveCameraPitch,
        deviceFix = ui.deviceLocationFix,
        driveFollowChrome = driveFollowChrome,
        onProgrammaticCameraMove = markProgrammaticCameraMove,
    )

    val followedCameraSignal =
        remember(followedPresenceUserId, rawPlottedVisibleOnMap) {
            followedPresenceUserId?.let { fid ->
                rawPlottedVisibleOnMap.firstOrNull { ottoUserIdsEqual(it.userId, fid) }
                    ?.let { "${it.lat}_${it.lng}_${it.updatedAt}" }
            }.orEmpty()
        }

    LaunchedEffect(followedCameraSignal) {
        if (!mapsKeyOk || followedPresenceUserId.isNullOrBlank() || !followedSquadId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val fid = followedPresenceUserId ?: return@LaunchedEffect
        val m = rawPlottedVisibleOnMap.firstOrNull { ottoUserIdsEqual(it.userId, fid) } ?: return@LaunchedEffect
        val ll = geoLatLngOrNull(m.lat, m.lng) ?: return@LaunchedEffect
        mapViewportState.easeToLatLngZoom(ll, 17.0, 360)
    }

    val squadBoundsPlotted =
        remember(
            followedSquadId,
            ui.presenceMembersByCircleId,
            ui.me?.id,
            ui.deviceLocationFix,
            ui.mapSharingLocation,
            ui.mapPresenceCircleId,
            ui.deviceMovementMode,
            fineGranted,
        ) {
            val sid = followedSquadId?.trim()?.takeIf { it.isNotEmpty() } ?: return@remember emptyList()
            plottedPresenceForSquadBounds(
                squadId = sid,
                presenceMembersInSquad = presenceMembersForCircleId(ui.presenceMembersByCircleId, sid),
                me = ui.me,
                deviceFix = ui.deviceLocationFix,
                mapSharingLocation = ui.mapSharingLocation,
                mapPresenceCircleId = ui.mapPresenceCircleId,
                deviceMovementMode = ui.deviceMovementMode,
                fineLocationGranted = fineGranted,
            )
        }

    val squadBoundsSignal =
        remember(squadBoundsPlotted, followedSquadId) {
            followedSquadId?.let {
                squadBoundsPlotted
                    .filter { it.isActive }
                    .mapNotNull { m -> geoLatLngOrNull(m.lat, m.lng) }
                    .sortedBy { "${it.latitude}_${it.longitude}" }
                    .joinToString("|") { "${it.latitude}_${it.longitude}" }
            }.orEmpty()
        }

    LaunchedEffect(squadBoundsSignal, followedSquadId, mapsKeyOk) {
        if (!mapsKeyOk || followedSquadId.isNullOrBlank() || followedPresenceUserId != null) {
            return@LaunchedEffect
        }
        val coords =
            squadBoundsPlotted
                .filter { it.isActive }
                .mapNotNull { m -> geoLatLngOrNull(m.lat, m.lng) }
        if (coords.isEmpty()) return@LaunchedEffect
        when {
            coords.size == 1 -> {
                mapViewportState.easeToLatLngZoom(coords.first(), 15.0, 380)
            }
            presenceRequiresWorldView(coords.map { it.latitude to it.longitude }) -> {
                mapViewportState.easeToLatLngZoom(
                    LatLng(PRESENCE_WORLD_VIEW_LAT, PRESENCE_WORLD_VIEW_LNG),
                    PRESENCE_WORLD_VIEW_ZOOM,
                    420,
                )
            }
            else -> {
                runCatching { mapViewportState.easeToLatLngBounds(coords, 112.0, 420) }
            }
        }
    }

    LaunchedEffect(circles, followedSquadId) {
        val sid = followedSquadId ?: return@LaunchedEffect
        if (circles.none { it.id == sid }) {
            followedSquadId = null
        }
    }

    LaunchedEffect(rawPlottedVisibleOnMap, plotted, followedPresenceUserId, hasSeenFollowedPeerInPlotted) {
        val fid = followedPresenceUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val present = rawPlottedVisibleOnMap.any { m -> ottoUserIdsEqual(m.userId, fid) }
        when {
            present -> hasSeenFollowedPeerInPlotted = true
            hasSeenFollowedPeerInPlotted -> followedPresenceUserId = null
            else -> Unit
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { mapViewportSizePx = it },
    ) {
        if (mapsKeyOk && !ui.isRouteBuilderPresented) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                scaleBar = {},
                style = {
                    MapStyle(style = if (mapTypeSatellite) Style.SATELLITE_STREETS else Style.DARK)
                },
            ) {
                DisposableMapEffect(Unit) { mapView ->
                    val moveListener =
                        object : OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                if (isApplyingDriveCameraUpdate) return
                                followDeviceCamera = false
                                followedPresenceUserId = null
                                followedSquadId = null
                            }

                            override fun onMove(detector: MoveGestureDetector): Boolean = false

                            override fun onMoveEnd(detector: MoveGestureDetector) {
                                flushMapMarkerLodFromCamera.value()
                            }
                        }
                    val longClickListener =
                        OnMapLongClickListener { point ->
                            onMapLongPressHandler.value(point.latitude(), point.longitude())
                            true
                        }
                    mapView.gestures.addOnMoveListener(moveListener)
                    mapView.gestures.addOnMapLongClickListener(longClickListener)
                    onDispose {
                        mapView.gestures.removeOnMoveListener(moveListener)
                        mapView.gestures.removeOnMapLongClickListener(longClickListener)
                    }
                }

                DisposableMapEffect(usesDriveCameraPitch, driveMapPaddingKey) { mapView ->
                    val edgeInsets =
                        if (usesDriveCameraPitch && driveFollowChrome != null) {
                            MapDriveCamera.driveFollowPadding(driveFollowChrome)
                        } else {
                            com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0)
                        }
                    mapView.mapboxMap.setCamera(
                        com.mapbox.maps.CameraOptions.Builder().padding(edgeInsets).build(),
                    )
                    onDispose {
                        mapView.mapboxMap.setCamera(
                            com.mapbox.maps.CameraOptions.Builder()
                                .padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0))
                                .build(),
                        )
                    }
                }

                MapboxTrafficMapEffect(showTraffic = ui.mapLayerShowTraffic)

                if (isActiveRouteDriveRecording) {
                    activeRouteForMapDrive?.let { route ->
                        val routeLine =
                            remember(route.id) {
                                lineCoordinatesFromSavedRoute(route)
                            }
                        if (routeLine.size >= 2) {
                            RouteMapLineMapEffect(
                                sourceId = "map-active-route-drive-line",
                                lineCoordinates = routeLine,
                            )
                        }
                        val routeMapPoints =
                            remember(route.id) {
                                mapPointsFromRoutePoints(route.points, route.id)
                            }
                        val completedWaypointIndexes =
                            ui.activeDriveSession?.routeProgress?.completedCheckpointIndexes
                                ?: emptySet()
                        routeMapPoints.forEachIndexed { index, point ->
                            val pt = Point.fromLngLat(point.lng, point.lat)
                            val distanceMeters =
                                driveHorizonDeviceLatLng?.let { anchor ->
                                    MapDriveCamera.distanceMeters(
                                        anchor.latitude,
                                        anchor.longitude,
                                        point.lat,
                                        point.lng,
                                    )
                                }
                            if (!MapDriveHorizonDepth.shouldShowRouteMarker(point.markerType, distanceMeters)) {
                                return@forEachIndexed
                            }
                            val useDriveHorizon = usesDriveCameraPitch && distanceMeters != null
                            val horizonScale =
                                if (useDriveHorizon) {
                                    MapDriveHorizonDepth.horizonScale(
                                        distanceMeters!!,
                                        driveVisibleMapHeightMeters,
                                    )
                                } else {
                                    1f
                                }
                            key("route-drive-${route.id}-${point.id}") {
                                ViewAnnotation(
                                    options =
                                        routeMapMarkerAnnotationOptions(
                                            pt,
                                            markerType = point.markerType,
                                            tieBreaker = index,
                                            distanceMeters = distanceMeters,
                                            useDriveHorizon = useDriveHorizon,
                                        ),
                                ) {
                                    RouteMapMarkerView(
                                        markerType = point.markerType,
                                        isCompleted = point.isCompleted(completedWaypointIndexes),
                                        scale = horizonScale,
                                    )
                                }
                            }
                        }
                    }
                }

                val selectedSavedPlaceId =
                    (mapMarkerDetailPeek as? MapMarkerDetailPeek.SavedPlace)?.place?.id
                val chatSharedPlacePeekMarkersNeedingMapPin =
                    mapSession.chatSharedPlacePeekMarkers.filter { peek ->
                        ui.savedPlaces.none { it.id == peek.id }
                    }

                if (ui.mapLayerShowSavedPlaces) {
                    ui.savedPlaces.forEach { place ->
                        val pos = geoLatLngOrNull(place.latitude, place.longitude)
                        if (pos != null) {
                            key(
                                MapDiscoveryMarkerLOD.annotationRefreshId(
                                    place.id,
                                    MapDiscoveryMarkerKind.SavedPlace,
                                    mapMarkerLodLatitudeDelta,
                                ),
                            ) {
                                ViewAnnotation(
                                    options = mapDiscoveryMarkerAnnotationOptions(pos.toMapboxPoint()),
                                ) {
                                    MapDiscoveryMarkerLODView(
                                        kind = MapDiscoveryMarkerKind.SavedPlace,
                                        latitudeDelta = mapMarkerLodLatitudeDelta,
                                        modifier =
                                            Modifier.clickable {
                                                mapMarkerDetailPeek = MapMarkerDetailPeek.SavedPlace(place)
                                            },
                                    ) { pinScale ->
                                        OttoMapSavedPlaceMarkerContent(
                                            isSelected = selectedSavedPlaceId == place.id,
                                            pinScale = pinScale,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                chatSharedPlacePeekMarkersNeedingMapPin.forEach { place ->
                    val pos = geoLatLngOrNull(place.latitude, place.longitude)
                    if (pos != null) {
                        key(
                            MapDiscoveryMarkerLOD.annotationRefreshId(
                                "peek:${place.id}",
                                MapDiscoveryMarkerKind.SavedPlace,
                                mapMarkerLodLatitudeDelta,
                            ),
                        ) {
                            ViewAnnotation(
                                options = mapDiscoveryMarkerAnnotationOptions(pos.toMapboxPoint()),
                            ) {
                                MapDiscoveryMarkerLODView(
                                    kind = MapDiscoveryMarkerKind.SavedPlace,
                                    latitudeDelta = mapMarkerLodLatitudeDelta,
                                    modifier =
                                        Modifier.clickable {
                                            mapMarkerDetailPeek = MapMarkerDetailPeek.SavedPlace(place)
                                        },
                                ) { pinScale ->
                                    OttoMapSavedPlaceMarkerContent(
                                        isSelected = selectedSavedPlaceId == place.id,
                                        pinScale = pinScale,
                                    )
                                }
                            }
                        }
                    }
                }

                if (ui.mapLayerShowUpcomingEvents) {
                    eventAnchorGroups.forEach { grp ->
                        key(
                            MapDiscoveryMarkerLOD.annotationRefreshId(
                                grp.id,
                                MapDiscoveryMarkerKind.Event,
                                mapMarkerLodLatitudeDelta,
                            ),
                        ) {
                            val pos = geoLatLngOrNull(grp.anchorLat, grp.anchorLng)
                            if (pos != null) {
                                val showCheckInCircle =
                                    grp.events.any {
                                        it.currentUserRsvp == OttoShellUiState.RsvpGoing &&
                                            it.currentUserCheckIn == null
                                    }
                                if (showCheckInCircle) {
                                    PolygonAnnotation(points = listOf(pos.radiusPolygonPoints(EVENT_CHECK_IN_RADIUS_METERS))) {
                                        fillColor =
                                            Color(
                                                red = 160f / 255f,
                                                green = 32f / 255f,
                                                blue = 240f / 255f,
                                                alpha = 0x26 / 255f,
                                            )
                                    }
                                }
                                ViewAnnotation(
                                    options = mapDiscoveryMarkerAnnotationOptions(pos.toMapboxPoint()),
                                ) {
                                    MapDiscoveryMarkerLODView(
                                        kind = MapDiscoveryMarkerKind.Event,
                                        latitudeDelta = mapMarkerLodLatitudeDelta,
                                        modifier =
                                            Modifier.clickable {
                                                val sorted =
                                                    grp.events.sortedBy { ev ->
                                                        parseEventInstant(ev.startsAt.orEmpty()) ?: java.time.Instant.MAX
                                                    }
                                                val primary = sorted.first()
                                                mapEventPeekGroupKey = grp.id
                                                mapMarkerDetailPeek =
                                                    MapMarkerDetailPeek.Event(primary, sorted.drop(1))
                                            },
                                    ) { pinScale ->
                                        OttoMapEventBeaconMarkerContent(
                                            group = grp,
                                            isSelected = mapEventPeekGroupKey == grp.id,
                                            pinScale = pinScale,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (ui.mapLayerShowRaceTracks) {
                    ui.raceTracks.forEach { track ->
                        key(
                            MapDiscoveryMarkerLOD.annotationRefreshId(
                                track.stableId,
                                MapDiscoveryMarkerKind.RaceTrack,
                                mapMarkerLodLatitudeDelta,
                            ),
                        ) {
                            val coords = track.coordinateOrNull()
                            val pos = coords?.let { geoLatLngOrNull(it.first, it.second) }
                            if (coords != null && pos != null) {
                                ViewAnnotation(
                                    options = mapDiscoveryMarkerAnnotationOptions(pos.toMapboxPoint()),
                                ) {
                                    MapDiscoveryMarkerLODView(
                                        kind = MapDiscoveryMarkerKind.RaceTrack,
                                        latitudeDelta = mapMarkerLodLatitudeDelta,
                                        modifier =
                                            Modifier.clickable {
                                                mapMarkerDetailPeek = MapMarkerDetailPeek.RaceTrack(track)
                                            },
                                    ) { pinScale ->
                                        OttoMapRaceTrackMarkerContent(
                                            isSelected =
                                                (mapMarkerDetailPeek as? MapMarkerDetailPeek.RaceTrack)?.track?.stableId == track.stableId,
                                            pinScale = pinScale,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                presenceGroups.forEach { group ->
                    val isSelfOnlyCluster =
                        group.members.size == 1 &&
                            ottoUserIdsEqual(group.members.first().userId, meUserId)
                    val presenceDistanceMeters =
                        driveHorizonDeviceLatLng?.let { anchor ->
                            MapDriveCamera.distanceMeters(
                                anchor.latitude,
                                anchor.longitude,
                                group.anchorLat,
                                group.anchorLng,
                            )
                        }
                    val usePresenceDriveHorizon = usesDriveCameraPitch && presenceDistanceMeters != null
                    if (
                        usePresenceDriveHorizon &&
                        !isSelfOnlyCluster &&
                        !MapDriveHorizonDepth.shouldShowPresenceMarker(presenceDistanceMeters)
                    ) {
                        return@forEach
                    }
                    val presenceHorizonScale =
                        when {
                            isSelfOnlyCluster -> 1f
                            usePresenceDriveHorizon ->
                                MapDriveHorizonDepth.horizonScale(
                                    presenceDistanceMeters!!,
                                    driveVisibleMapHeightMeters,
                                    minScale = MapDriveHorizonDepth.PRESENCE_MIN_SCALE,
                                )
                            else -> 1f
                        }
                    val presenceMarkerContentKey =
                        remember(
                            group.id,
                            group.members,
                            brandLogoUrlsByUserId,
                            ui.activeDriveSession?.id,
                            ui.selectedSharingCarId,
                        ) {
                            buildString {
                                append(group.id)
                                append('|')
                                append(ui.activeDriveSession?.id.orEmpty())
                                append('|')
                                append(ui.selectedSharingCarId)
                                group.members.forEach { member ->
                                    append('|')
                                    append(member.userId.trim())
                                    append(':')
                                    append(brandLogoUrlsByUserId[member.userId.trim()].orEmpty())
                                }
                            }
                        }
                    androidx.compose.runtime.key(presenceMarkerContentKey) {
                        ViewAnnotation(
                            options =
                                mapPresenceMarkerAnnotationOptions(
                                    Point.fromLngLat(group.anchorLng, group.anchorLat),
                                    tieBreaker = group.id.hashCode(),
                                    distanceMeters = presenceDistanceMeters,
                                    useDriveHorizon = usePresenceDriveHorizon,
                                ),
                        ) {
                            Box(
                                Modifier.clickable {
                                    if (group.members.size == 1) {
                                        val member = group.members.first()
                                        if (ottoUserIdsEqual(ui.me?.id, member.userId)) {
                                            onMapNavigateToOwnProfileTab()
                                        } else {
                                            onMapViewPeerProfile(member.userId)
                                        }
                                    } else {
                                        clusterMembersPick = group.members
                                    }
                                },
                            ) {
                                PresenceClusterMarkerContent(
                                    group,
                                    ui.contacts,
                                    ui.me,
                                    travelSurfacesByUserId = travelSurfacesByUserId,
                                    markerScale = presenceHorizonScale,
                                    brandLogoUrlsByUserId = brandLogoUrlsByUserId,
                                )
                            }
                        }
                    }
                }
            }
        } else if (ui.isRouteBuilderPresented) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            PresenceListFallback(
                plotted.ifEmpty { ui.presenceMembers },
                Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            snackHost,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomPad),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp, start = 10.dp, end = 10.dp),
        ) {
            val sharingPillDensity = LocalDensity.current
            var sharingPillMaxWidth by remember { mutableStateOf(168.dp) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        sharingPillMaxWidth =
                            with(sharingPillDensity) {
                                (size.width.toDp() - (topBarBellReserve * 2)).coerceAtLeast(168.dp)
                            }
                    },
            ) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DriveSessionStatusPill(
                        presentation = drivePillPresentation,
                        onTap = {
                            if (drivePillPresentation is DriveSessionPillPresentation.Idle) {
                                startDriveSheetVisible = true
                            } else {
                                driveControlsSheetVisible = true
                            }
                        },
                        onStop = { stopDriveConfirmationVisible = true },
                        modifier = Modifier.widthIn(max = sharingPillMaxWidth),
                    )
                }

            }

            if (mapsKeyOk &&
                plottedVisibleOnMap.isEmpty() &&
                ui.savedPlaces.isEmpty() &&
                ui.presenceMembers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.map_hints_no_pins),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ui.presenceError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onRetryPresence) {
                    Text(stringResource(R.string.retry))
                }
            }

            ui.savedPlacesSnack?.takeIf { it.isNotBlank() }?.let { message ->
                val addedMessage = stringResource(R.string.marker_detail_added_to_places)
                val isSuccess = message == addedMessage
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onDismissSavedPlacesSnack) {
                        Text(stringResource(R.string.dismiss_snack))
                    }
                }
            }

            if (!mapsKeyOk) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.map_add_api_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (mapsKeyOk) {
            val locationTrackingAccent =
                followDeviceCamera &&
                    deviceLatLng != null &&
                    followedPresenceUserId == null &&
                    followedSquadId == null
            OttoMapSideFab(
                onClick = {
                    followDeviceCamera = true
                    followedPresenceUserId = null
                    followedSquadId = null
                    scope.launch {
                        val target =
                            mePinCoords
                                ?: plottedVisibleOnMap
                                    .firstOrNull()
                                    ?.let { geoLatLngOrNull(it.lat, it.lng) }
                                ?: defaultLatLng
                        if (usesDriveCameraPitch && driveFollowChrome != null) {
                            val bearing =
                                MapDriveCamera.driveBearing(ui.deviceLocationFix, null, 0f)
                            markProgrammaticCameraMove()
                            mapViewportState.easeToDriveFollowCamera(
                                target,
                                bearing,
                                MapDriveCamera.driveFollowPadding(driveFollowChrome),
                            )
                        } else {
                            mapViewportState.easeToLatLngZoom(target, 17.0, 420)
                        }
                    }
                },
                icon = Icons.Outlined.MyLocation,
                contentDescription = stringResource(R.string.map_accessibility_recenter_map),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 12.dp,
                            bottom = mapFabBottomInset + mapSideFabDriveCenterOffset,
                        ),
                styledLikeDrive = true,
                active = locationTrackingAccent,
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = mapFabBottomInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(mapFabSpacing),
            ) {
                val findSharingCd =
                    if (friendsSharingLocationCount > 0) {
                        stringResource(
                            R.string.map_accessibility_find_sharing_with_count,
                            friendsSharingLocationCount,
                        )
                    } else {
                        stringResource(R.string.map_accessibility_find_sharing)
                    }
                if (friendsSharingLocationCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color(0xFF43A047),
                            ) {
                                Text(
                                    text =
                                        if (friendsSharingLocationCount > 99) {
                                            "99+"
                                        } else {
                                            friendsSharingLocationCount.toString()
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        },
                    ) {
                        OttoMapSideFab(
                            onClick = { peopleSharingSheetVisible = true },
                            icon = Icons.Outlined.Groups,
                            contentDescription = findSharingCd,
                            styledLikeDrive = true,
                        )
                    }
                } else {
                    OttoMapSideFab(
                        onClick = { peopleSharingSheetVisible = true },
                        icon = Icons.Outlined.Groups,
                        contentDescription = findSharingCd,
                        styledLikeDrive = true,
                    )
                }

                OttoMapSideFab(
                    onClick = { layersSheetVisible = true },
                    icon = Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.map_accessibility_map_layers),
                    styledLikeDrive = true,
                )

                val driveSessionActive = ui.hasActiveDriveSession
                OttoMapDriveStyleFab(
                    onClick = {
                        if (driveSessionActive) {
                            driveControlsSheetVisible = true
                        } else {
                            startDriveSheetVisible = true
                        }
                    },
                    icon = Icons.Outlined.DirectionsCar,
                    contentDescription =
                        if (driveSessionActive) {
                            "Drive controls"
                        } else {
                            stringResource(R.string.map_accessibility_drive_recording)
                        },
                    active = driveSessionActive,
                )
            }

            if (isQuickDriveDockVisible) {
                var recordDriveDraft by remember(ui.recordDriveOnStartEnabled) {
                    mutableStateOf(ui.recordDriveOnStartEnabled)
                }
                val driveDockExpandedMaxHeightDp =
                    if (shareLocationDraft && mapViewportSizePx.height > 0) {
                        with(LocalDensity.current) { mapViewportSizePx.height.toDp() }
                    } else {
                        null
                    }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { mapDriveDockHeightPx = it.height },
                ) {
                    DriveLaunchDock(
                        mode = DriveLaunchDockMode.Quick,
                        isSessionActive = isQuickDriveSessionActive,
                        statusText = quickDriveDockStatusText,
                        recordDrive = recordDriveDraft,
                        onRecordDriveChange = { recordDriveDraft = it },
                        shareLocation = shareLocationDraft,
                        onShareLocationChange = { shareLocationDraft = it },
                        shareCircleIds = shareCircleIdsDraft,
                        onShareCircleIdsChange = { shareCircleIdsDraft = it },
                        circles = sharingSortedCircles,
                        garageCars = ui.garageCars,
                        selectedSharingCarId = ui.selectedSharingCarId,
                        onSelectSharingCar = if (ui.showsDriveCarPicker) onSelectSharingCar else null,
                        onStartDrive = { attemptQuickDriveStart(recordDriveDraft) },
                        onStopDrive = { stopDriveConfirmationVisible = true },
                        onCancel = {
                            isQuickDriveDockVisible = false
                            mapDriveDockHeightPx = 0
                            mapDriveDockCompactHeightPx = 0
                            shareLocationDraft = false
                            shareCircleIdsDraft = emptySet()
                        },
                        expandedMaxHeightDp = driveDockExpandedMaxHeightDp,
                        onCompactDockHeightChanged = { mapDriveDockCompactHeightPx = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (ui.mapSharingLocation) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { mapDriveDockHeightPx = it.height },
                ) {
                    DriveLaunchDock(
                        mode = DriveLaunchDockMode.Live,
                        isSessionActive = true,
                        statusText = liveDriveDockStatusText,
                        garageCars = ui.garageCars,
                        selectedSharingCarId = ui.selectedSharingCarId,
                        onSelectSharingCar = if (ui.showsDriveCarPicker) onSelectSharingCar else null,
                        onStartDrive = {},
                        onStopDrive = { stopDriveConfirmationVisible = true },
                        onCancel = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (layersSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { layersSheetVisible = false },
            sheetState = layersSheetState,
        ) {
            val layersScroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(layersScroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .ottoBottomSheetContent(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapSheetHeader(
                    title = stringResource(R.string.map_layers_sheet_title),
                    onDone = { layersSheetVisible = false },
                    doneLabel = stringResource(R.string.map_layers_done),
                )
                Text(
                    stringResource(R.string.map_layers_section_events),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OttoToggleSettingCard(
                    title = stringResource(R.string.map_layers_show_events),
                    checked = ui.mapLayerShowUpcomingEvents,
                    onCheckedChange = onMapLayerShowEvents,
                )
                OttoToggleSettingCard(
                    title = stringResource(R.string.map_layers_show_race_tracks),
                    checked = ui.mapLayerShowRaceTracks,
                    onCheckedChange = onMapLayerShowRaceTracks,
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.map_layers_section_places),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OttoToggleSettingCard(
                    title = stringResource(R.string.map_layers_show_places),
                    checked = ui.mapLayerShowSavedPlaces,
                    onCheckedChange = onMapLayerShowSavedPlaces,
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.map_layers_section_squads),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val layerSquadItems =
                    remember(circles, ui.presenceMembersByCircleId) {
                        sortedCirclesForMapSquadList(circles, ui.presenceMembersByCircleId).map { circle ->
                            val activeCount =
                                mapSquadActiveSharingCount(circle.id, ui.presenceMembersByCircleId)
                            MapSquadTrackListItem(
                                circle = circle,
                                name = circle.name,
                                subtitle =
                                    if (activeCount > 0) {
                                        ctx.getString(R.string.map_squad_track_sharing_count, activeCount)
                                    } else {
                                        ctx.getString(R.string.map_squad_track_active_none)
                                    },
                            )
                        }
                    }
                MapSquadTrackGroupedList(
                    items = layerSquadItems,
                    rowTrailing = { item ->
                        val implicit = ui.mapLayerSelectedCircleIds.isEmpty()
                        val checked =
                            if (implicit) {
                                true
                            } else {
                                ui.mapLayerSelectedCircleIds.contains(item.circle.id)
                            }
                        MapSquadLayerVisibilityTrailing(
                            checked = checked,
                            onCheckedChange = { on -> onMapLayerCircleVisible(item.circle.id, on) },
                        )
                    },
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.map_layers_section_style),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OttoToggleSettingCard(
                    title = stringResource(R.string.map_layers_show_traffic),
                    checked = ui.mapLayerShowTraffic,
                    onCheckedChange = onMapLayerShowTraffic,
                )
                OttoToggleSettingCard(
                    title = stringResource(R.string.map_layers_satellite_mode),
                    checked = mapTypeSatellite,
                    onCheckedChange = { mapTypeSatellite = it },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    clusterMembersPick?.let { members ->
        ModalBottomSheet(
            onDismissRequest = { clusterMembersPick = null },
            sheetState = clusterPickSheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .ottoBottomSheetContent(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapSheetHeader(
                    title = stringResource(R.string.map_cluster_sheet_title),
                    onDone = { clusterMembersPick = null },
                    doneLabel = stringResource(R.string.map_layers_done),
                )
                members.forEach { m ->
                    val mode = normalizePresenceMovementMode(m)
                    val modeLabel =
                        stringResource(
                            when (mode) {
                                "driving" -> R.string.map_cluster_movement_driving
                                "walking" -> R.string.map_cluster_movement_walking
                                else -> R.string.map_cluster_movement_idle
                            },
                        )
                    val spd = (m.speedMph ?: 0.0).roundToInt().coerceAtLeast(0)
                    val title = presenceMemberAvatarLabel(m, ui.contacts, ui.me).first
                    ElevatedCard(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                clusterMembersPick = null
                                if (ottoUserIdsEqual(ui.me?.id, m.userId)) {
                                    onMapNavigateToOwnProfileTab()
                                } else {
                                    onMapViewPeerProfile(m.userId)
                                }
                            },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "$modeLabel · $spd mph",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.NavigateNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    mapMarkerDetailPeek?.let { peek ->
        ModalBottomSheet(
            onDismissRequest = {
                mapMarkerDetailPeek = null
                mapEventPeekGroupKey = null
            },
            sheetState = mapMarkerDetailSheetState,
            containerColor = to.ottomot.driftd.ui.components.MarkerDetailColors.sheetBackground,
            dragHandle = null,
        ) {
            val distanceFromMe =
                remember(peek, deviceLatLng) {
                    when (peek) {
                        is MapMarkerDetailPeek.SavedPlace ->
                            markerDistanceFromDevice(peek.place.latitude, peek.place.longitude, deviceLatLng)
                        is MapMarkerDetailPeek.Event -> {
                            val coords = peek.primary.location?.coordinates
                            if (coords != null && coords.size >= 2) {
                                markerDistanceFromDevice(coords[1], coords[0], deviceLatLng)
                            } else {
                                null
                            }
                        }
                        is MapMarkerDetailPeek.RaceTrack ->
                            peek.track.coordinateOrNull()?.let { (lat, lng) ->
                                markerDistanceFromDevice(lat, lng, deviceLatLng)
                            }
                    }
                }
            val detailContent =
                when (peek) {
                    is MapMarkerDetailPeek.SavedPlace -> MapMarkerDetailContent.SavedPlace(peek.place)
                    is MapMarkerDetailPeek.Event -> MapMarkerDetailContent.Event(peek.primary, peek.siblings)
                    is MapMarkerDetailPeek.RaceTrack -> MapMarkerDetailContent.RaceTrack(peek.track)
                }
            MapMarkerDetailSheet(
                content = detailContent,
                distanceFromMe = distanceFromMe,
                rsvpSubmittingEventId = ui.eventRsvpSubmittingEventId,
                refreshedEvents = ui.events + ui.communityEvents + ui.squadFeedEvents,
                circles = circles,
                contacts = ui.contacts,
                dmConversations = ui.directMessages.conversations,
                meUser = ui.me,
                onDone = {
                    mapMarkerDetailPeek = null
                    mapEventPeekGroupKey = null
                },
                onDismissSheet = {
                    mapMarkerDetailPeek = null
                    mapEventPeekGroupKey = null
                },
                onEditSavedPlace = { place ->
                    renameTarget = place
                    renameDraft = place.name
                },
                onRemoveSavedPlace = { place ->
                    savedPlacePendingDelete = place
                },
                onSaveMapPlace = onSaveMapPlace,
                onOpenEventDetail = onOpenEventDetail,
                onSubmitEventRsvp = onSubmitEventRsvp,
                onPrefetchDirectMessages = onPrefetchDirectMessages,
                postEventShareToChat = postEventShareToChat,
                postMapMarkerShareToChat = postMapMarkerShareToChat,
                pendingSquadChatFocusTick = ui.pendingSquadChatFocusTick,
                ownedSavedPlaceIds = ui.savedPlaces.map { it.id }.toSet(),
                onEventAssociationsSaved = onApplyEventAttachedSquads,
            )
        }
    }

    savedPlacePendingDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { savedPlacePendingDelete = null },
            title = { Text(stringResource(R.string.marker_detail_delete_place_title)) },
            text = { Text(stringResource(R.string.marker_detail_delete_place_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSavedPlace(place.id)
                        savedPlacePendingDelete = null
                        mapMarkerDetailPeek = null
                    },
                ) {
                    Text(stringResource(R.string.marker_detail_action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { savedPlacePendingDelete = null }) {
                    Text(stringResource(R.string.marker_detail_cancel))
                }
            },
        )
    }

    if (saveDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                saveDialogOpen = false
                savePlaceCoordinate = null
                savePlaceAddressDraft = null
            },
            title = { Text(stringResource(R.string.places_save_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = savePlaceNameDraft,
                        onValueChange = { savePlaceNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.places_save_dialog_hint)) },
                        singleLine = true,
                    )
                    savePlaceAddressDraft?.trim()?.takeIf { it.isNotEmpty() }?.let { address ->
                        Text(
                            address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = savePlaceNameDraft.trim()
                        if (t.isNotEmpty()) {
                            val pinned = savePlaceCoordinate
                            val target =
                                pinned?.let { (lat, lng) -> Point.fromLngLat(lng, lat) }
                                    ?: mapViewportState.cameraState?.center
                                    ?: defaultLatLng.toMapboxPoint()
                            onSaveMapPlace(t, target.latitude(), target.longitude(), savePlaceAddressDraft)
                            saveDialogOpen = false
                            savePlaceCoordinate = null
                            savePlaceAddressDraft = null
                        }
                    },
                    enabled = savePlaceNameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.places_save_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        saveDialogOpen = false
                        savePlaceCoordinate = null
                        savePlaceAddressDraft = null
                    },
                ) {
                    Text(stringResource(R.string.garage_dialog_cancel))
                }
            },
        )
    }

    if (mapPlaceActionSheetOpen) {
        val coords = mapLongPressCoordinate
        if (coords != null) {
            val (lat, lng) = coords
            val sharePayload =
                mapMarkerSharePayloadForAdhocPlace(
                    name = mapPlaceNameDraft.trim().takeIf { it.isNotEmpty() },
                    addressSummary = mapPlaceAddressDraft,
                    latitude = lat,
                    longitude = lng,
                )
            MapPlaceLongPressActionSheet(
                sheetState = mapPlaceActionSheetState,
                isResolving = mapPlaceIsResolving,
                previewName = mapPlaceNameDraft.takeIf { it.isNotBlank() },
                previewAddress = mapPlaceAddressDraft,
                payload = sharePayload,
                onDismiss = {
                    mapPlaceActionSheetOpen = false
                    if (!saveDialogOpen && !adhocShareToChatOpen) {
                        mapLongPressCoordinate = null
                        savePlaceCoordinate = null
                    }
                },
                onShareToChat = {
                    adhocPlaceSharePayload = sharePayload
                    mapPlaceActionSheetOpen = false
                    adhocShareToChatOpen = true
                },
                onSave = {
                    savePlaceNameDraft = mapPlaceNameDraft
                    savePlaceAddressDraft = mapPlaceAddressDraft
                    mapPlaceActionSheetOpen = false
                    saveDialogOpen = true
                },
            )
        }
    }

    adhocPlaceSharePayload?.let { payload ->
        MapMarkerShareFlowSheets(
            payload = payload,
            shareSquadActionsOpen = false,
            onShareSquadActionsOpenChange = {},
            shareToChatSheetOpen = adhocShareToChatOpen,
            onShareToChatSheetOpenChange = { open ->
                adhocShareToChatOpen = open
                if (!open) {
                    adhocPlaceSharePayload = null
                    mapLongPressCoordinate = null
                    savePlaceCoordinate = null
                }
            },
            circles = circles,
            contacts = ui.contacts,
            dmConversations = ui.directMessages.conversations,
            meUser = ui.me,
            onPrefetchDirectMessages = onPrefetchDirectMessages,
            postMapMarkerShareToChat = postMapMarkerShareToChat,
            pendingSquadChatFocusTick = ui.pendingSquadChatFocusTick,
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.places_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.places_rename_dialog_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = renameDraft.trim()
                        if (t.isNotEmpty()) {
                            onRenameSavedPlace(target.id, t)
                            renameTarget = null
                        }
                    },
                    enabled = renameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.places_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.garage_dialog_cancel))
                }
            },
        )
    }

    if (routesMenuVisible) {
        RoutesMenuSheet(
            sheetState = routesMenuSheetState,
            routes = ui.routes,
            myUserId = ui.me?.id,
            onDismiss = { routesMenuVisible = false },
            onCreateRoute = onCreateMapRoute,
            onSelectRoute = { route ->
                routesMenuVisible = false
                onOpenMapRoute(route)
            },
            onEditRoute = onEditMapRoute,
            onDeleteRoute = onDeleteMapRoute,
            onRenameRoute = onRenameMapRoute,
        )
    }

    if (startDriveSheetVisible) {
        StartDriveSheet(
            onQuickDrive = {
                startDriveSheetVisible = false
                if (ui.hasActiveDriveSession) {
                    showSnack("End your current drive first")
                    return@StartDriveSheet
                }
                shareLocationDraft = false
                shareCircleIdsDraft = emptySet()
                isQuickDriveDockVisible = true
            },
            onRouteDrive = {
                startDriveSheetVisible = false
                routesMenuVisible = true
            },
            onGoLive = {
                startDriveSheetVisible = false
                if (circles.isEmpty()) {
                    showSnack(ctx.getString(R.string.map_sharing_need_squad))
                    return@StartDriveSheet
                }
                isQuickDriveDockVisible = false
                pendingGoLiveAfterStartSheet = true
                saveDriveDraft = false
                onMapShareSaveDrive(false)
                sharingSheetVisible = true
            },
            onCancel = { startDriveSheetVisible = false },
        )
    }

    if (stopDriveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { stopDriveConfirmationVisible = false },
            title = { Text(stringResource(R.string.drive_stop_confirmation_title)) },
            text = { Text(stringResource(R.string.drive_stop_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopDriveConfirmationVisible = false
                        isQuickDriveDockVisible = false
                        mapDriveDockHeightPx = 0
                        onStopDriveSession()
                    },
                ) {
                    Text(stringResource(R.string.drive_stop_confirmation_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { stopDriveConfirmationVisible = false }) {
                    Text(stringResource(R.string.drive_stop_confirmation_keep_driving))
                }
            },
        )
    }

    if (driveControlsSheetVisible) {
        DriveControlsSheet(
            presentation = drivePillPresentation,
            startedAtMs = driveSessionStartedAtMs,
            timeText = driveSessionTimeText,
            distanceText = driveSessionDistanceText,
            topSpeedText = driveSessionTopSpeedText,
            shareLive = driveControlsShareLive,
            onShareLiveChange = { enabled ->
                if (enabled) {
                    if (circles.isEmpty()) {
                        showSnack(ctx.getString(R.string.map_sharing_need_squad))
                        return@DriveControlsSheet
                    }
                    pendingShareLiveFromControls = true
                    driveControlsSheetVisible = false
                    sharingSheetVisible = true
                } else {
                    onStopLiveSharingOnly()
                }
            },
            saveDrive = driveControlsSaveDrive,
            onSaveDriveChange = onSetDriveSessionSaveEnabled,
            routeName = ui.activeDriveSession?.routeName,
            routeCheckpointText = routeCheckpointText,
            onAddSquad = {
                driveControlsSheetVisible = false
                sharingSheetVisible = true
            },
            onStopDrive = {
                driveControlsSheetVisible = false
                stopDriveConfirmationVisible = true
            },
            onDismiss = { driveControlsSheetVisible = false },
        )
    }

    if (mapLocationPrimerVisible) {
        fun dismissMapPrimer() {
            mapLocationPrimerVisible = false
        }
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = {},
            onCloseClick = {},
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.map_location_primer_title),
            body = stringResource(R.string.map_location_primer_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = stringResource(R.string.map_location_primer_footer),
            primaryLabel = stringResource(R.string.map_location_primer_continue),
            onPrimaryClick = {
                dismissMapPrimer()
                mapPrimerLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            allowsUnconfirmedDismiss = false,
        )
    }

    if (showMapLocationDeniedModal) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = { showMapLocationDeniedModal = false },
            onCloseClick = { showMapLocationDeniedModal = false },
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.location_permission_map_modal_title),
            body = stringResource(R.string.location_permission_map_modal_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = null,
            primaryLabel = stringResource(R.string.location_permission_enable),
            onPrimaryClick = {
                runCatching {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", ctx.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    ctx.startActivity(intent)
                }
                showMapLocationDeniedModal = false
            },
            secondaryLabel = stringResource(R.string.location_permission_modal_dismiss),
            onSecondaryClick = { showMapLocationDeniedModal = false },
        )
    }

    if (showDriveBackgroundLocationPrimer) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = {},
            onCloseClick = {},
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.drive_background_location_primer_title),
            body = stringResource(R.string.drive_background_location_primer_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = stringResource(R.string.drive_background_location_primer_footer),
            primaryLabel = stringResource(R.string.drive_background_location_primer_continue),
            onPrimaryClick = {
                showDriveBackgroundLocationPrimer = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    driveBackgroundLocationPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    finishPendingDrivePermissionStart(degraded = false)
                }
            },
            allowsUnconfirmedDismiss = false,
        )
    }

    if (sharingSquadRequiredDialogVisible) {
        AlertDialog(
            onDismissRequest = { sharingSquadRequiredDialogVisible = false },
            title = { Text(stringResource(R.string.map_sharing_choose_squad_title)) },
            text = { Text(stringResource(R.string.map_sharing_choose_squad_message)) },
            confirmButton = {
                TextButton(onClick = { sharingSquadRequiredDialogVisible = false }) {
                    Text(stringResource(R.string.map_drive_recording_ok))
                }
            },
        )
    }

    if (sharingSafetyDialogVisible) {
        val safetyBullets =
            listOf(
                Icons.Outlined.Lock to stringResource(R.string.map_sharing_safety_obey_laws),
                Icons.Outlined.Search to stringResource(R.string.map_sharing_safety_stay_attentive),
                Icons.Outlined.Close to stringResource(R.string.map_sharing_safety_no_app_driving),
                Icons.Outlined.DirectionsCar to stringResource(R.string.map_sharing_safety_no_reckless),
            )
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = {
                pendingSafetySharingCircleId = null
                pendingGoLiveAfterStartSheet = false
                pendingShareLiveFromControls = false
                pendingQuickDriveShareStart = null
                pendingDriveOnlyAfterSafety = null
                sharingSafetyDialogVisible = false
            },
            onCloseClick = {
                pendingSafetySharingCircleId = null
                pendingGoLiveAfterStartSheet = false
                pendingShareLiveFromControls = false
                pendingQuickDriveShareStart = null
                pendingDriveOnlyAfterSafety = null
                sharingSafetyDialogVisible = false
            },
            hero = { OttoEducationShieldHero() },
            title = stringResource(R.string.map_sharing_safety_title),
            body = stringResource(R.string.map_sharing_safety_body),
            bulletSectionTitle = stringResource(R.string.map_sharing_safety_section),
            bullets = safetyBullets,
            footer = stringResource(R.string.map_sharing_safety_footer),
            primaryLabel = stringResource(R.string.map_sharing_safety_enable),
            onPrimaryClick = {
                sharingSafetyPrefs.edit().putBoolean(sharingSafetyPrefsKey, true).apply()
                localSharingSafetyAcknowledged = true
                onAcknowledgeSharingSafetyDisclaimer()
                val pendingDriveOnly = pendingDriveOnlyAfterSafety
                if (pendingDriveOnly != null) {
                    pendingDriveOnlyAfterSafety = null
                    sharingSafetyDialogVisible = false
                    beginQuickDriveAfterPermissions(pendingDriveOnly)
                    return@OttoEducationDialog
                }
                val pendingQuickDrive = pendingQuickDriveShareStart
                if (pendingQuickDrive != null) {
                    sharingSafetyDialogVisible = false
                    beginQuickDriveShareAfterPermissions(
                        pendingQuickDrive.saveToProfile,
                        pendingQuickDrive.circleIds,
                    )
                    return@OttoEducationDialog
                }
                val resolved = pendingSafetySharingCircleId
                val goLive = pendingGoLiveAfterStartSheet
                val shareFromControls = pendingShareLiveFromControls
                pendingSafetySharingCircleId = null
                pendingGoLiveAfterStartSheet = false
                pendingShareLiveFromControls = false
                sharingSafetyDialogVisible = false
                if (!resolved.isNullOrBlank()) {
                    if (goLive) {
                        val choice = mapDurationChoices[durationChoiceIndex.coerceIn(0, mapDurationChoices.lastIndex)]
                        onMapSharingOptions(choice.resolveToTimerMinutes(), whileDrivingOnlyDraft, saveDriveDraft)
                    }
                    finishEnableSharing(resolved)
                    if (goLive || shareFromControls) {
                        onEnsureLiveDriveSession(saveDriveDraft)
                    }
                    if (shareFromControls) {
                        driveControlsSheetVisible = true
                    }
                }
            },
            secondaryLabel = stringResource(R.string.map_sharing_safety_not_now),
            onSecondaryClick = {
                pendingSafetySharingCircleId = null
                pendingGoLiveAfterStartSheet = false
                pendingShareLiveFromControls = false
                pendingQuickDriveShareStart = null
                sharingSafetyDialogVisible = false
            },
        )
    }

    if (showSharingLocationDeniedModal) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = { showSharingLocationDeniedModal = false },
            onCloseClick = { showSharingLocationDeniedModal = false },
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.location_permission_sharing_modal_title),
            body = stringResource(R.string.location_permission_sharing_modal_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = null,
            primaryLabel = stringResource(R.string.location_permission_enable),
            onPrimaryClick = {
                showSharingLocationDeniedModal = false
                if (canRepromptFineLocationAtRuntime(ctx)) {
                    sharingLocationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    runCatching {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        ctx.startActivity(intent)
                    }
                }
            },
            secondaryLabel = stringResource(R.string.location_permission_modal_dismiss),
            onSecondaryClick = { showSharingLocationDeniedModal = false },
        )
    }

    if (showSharingActivityDeniedModal) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = { showSharingActivityDeniedModal = false },
            onCloseClick = { showSharingActivityDeniedModal = false },
            hero = { OttoEducationShieldHero() },
            title = stringResource(R.string.activity_permission_sharing_modal_title),
            body = stringResource(R.string.activity_permission_sharing_modal_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = null,
            primaryLabel = stringResource(R.string.location_permission_enable),
            onPrimaryClick = {
                runCatching {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", ctx.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    ctx.startActivity(intent)
                }
                showSharingActivityDeniedModal = false
            },
            secondaryLabel = stringResource(R.string.location_permission_modal_dismiss),
            onSecondaryClick = { showSharingActivityDeniedModal = false },
        )
    }

    if (sharingSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sharingSheetVisible = false },
            sheetState = sharingSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            val durationPick = mapDurationChoices[durationChoiceIndex.coerceIn(0, mapDurationChoices.lastIndex)]
            val extendResolvedMinutes = durationPick.resolveToTimerMinutes()
            val sessionHasAutoExpiry =
                ui.mapSharingLocation && (ui.mapShareDurationMinutes ?: 0) > 0
            val canExtendSession =
                sessionHasAutoExpiry &&
                    extendResolvedMinutes != null &&
                    extendResolvedMinutes > 0
            val actionBrush =
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
            val startSharingColor = SavedRouteListIconColors.startButton

            Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollSheet)
                        .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                MapSheetHeader(
                    title = stringResource(R.string.map_sharing_sheet_title),
                    onDone = { sharingSheetVisible = false },
                    doneLabel = stringResource(R.string.map_sharing_done),
                )

                Text(
                    stringResource(R.string.map_sharing_duration_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { durationMenuExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFAF52DE),
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                mapDurationChoiceLabel(durationPick),
                                modifier = Modifier.weight(1f),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = durationMenuExpanded,
                        onDismissRequest = { durationMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        mapDurationChoices.forEachIndexed { index, choice ->
                            DropdownMenuItem(
                                text = { Text(mapDurationChoiceLabel(choice)) },
                                onClick = {
                                    durationChoiceIndex = index
                                    durationMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.map_sharing_duration_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    stringResource(R.string.map_sharing_when_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { whileDrivingOnlyDraft = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border =
                            BorderStroke(
                                if (!whileDrivingOnlyDraft) 2.dp else 1.dp,
                                if (!whileDrivingOnlyDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                        color =
                            if (!whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Outlined.AllInclusive,
                                contentDescription = null,
                                tint =
                                    if (!whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.map_sharing_share_now_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                stringResource(R.string.map_sharing_share_now_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        onClick = { whileDrivingOnlyDraft = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border =
                            BorderStroke(
                                if (whileDrivingOnlyDraft) 2.dp else 1.dp,
                                if (whileDrivingOnlyDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                        color =
                            if (whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Outlined.DirectionsCar,
                                contentDescription = null,
                                tint =
                                    if (whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.map_sharing_while_driving_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                stringResource(R.string.map_sharing_while_driving_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (ui.showsDriveCarPicker) {
                    DriveCarPickerSection(
                        garageCars = ui.garageCars,
                        selectedSharingCarId = ui.selectedSharingCarId,
                        onSelectSharingCar = onSelectSharingCar,
                    )
                }

                Text(
                    stringResource(R.string.map_sharing_drive_history_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OttoToggleSettingCard(
                    title = stringResource(R.string.map_sharing_save_drive_title),
                    checked = saveDriveDraft,
                    onCheckedChange = { checked ->
                        saveDriveDraft = checked
                        onMapShareSaveDrive(checked)
                    },
                    icon = Icons.Outlined.Route,
                    helperText = stringResource(R.string.map_sharing_save_drive_helper),
                )

                Text(
                    stringResource(R.string.map_sharing_with_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (circles.isEmpty()) {
                    Text(
                        stringResource(R.string.map_sharing_no_squads_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                sharingSortedCircles.forEach { circle ->
                    val selected = circle.id == audienceIdDraft
                    Surface(
                        onClick = { audienceIdDraft = circle.id },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                        border =
                            BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            ),
                    ) {
                        SquadShareListRow(
                            squadName = circle.name,
                            photoUrl = circle.photoUrl,
                            memberCount = circle.members?.size ?: 0,
                            avatarSize = 44.dp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            trailingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (selected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider()

            Column(
                Modifier
                    .fillMaxWidth()
                    .ottoBottomSheetContent()
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sessionHasAutoExpiry) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                        Text(
                            stringResource(R.string.map_sharing_e2e_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        )
                    }

                    val extendLabel = mapDurationChoiceLabel(durationPick)

                    Button(
                        onClick = {
                            val mins = extendResolvedMinutes ?: return@Button
                            onExtendMapSharing(mins)
                        },
                        enabled = canExtendSession,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(actionBrush, RoundedCornerShape(16.dp))
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.map_sharing_extend, extendLabel),
                                style =
                                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }

                    Button(
                        onClick = { applySharing(enable = false) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color(0xFFFF5252),
                            ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.map_sharing_stop),
                            style =
                                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (ui.mapSharingLocation) {
                                applySharing(enable = false)
                                return@Button
                            }
                            val resolved =
                                audienceIdDraft.trim().takeIf { id ->
                                    id.isNotBlank() && circles.any { it.id == id }
                                }
                            if (resolved == null) {
                                sharingSquadRequiredDialogVisible = true
                                return@Button
                            }
                            applySharing(enable = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(startSharingColor, RoundedCornerShape(16.dp))
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.map_sharing_start),
                                style =
                                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (peopleSharingSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { peopleSharingSheetVisible = false },
            sheetState = peopleSharingSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            val sharingListBase =
                remember(plotted, meUserId, ui.contacts) {
                    currentlySharingMembersForSheet(plotted, meUserId, ui.contacts)
                }
            var peopleSearchQuery by remember { mutableStateOf("") }
            var squadSearchQuery by remember { mutableStateOf("") }
            var peopleSharingSheetTab by rememberSaveable { mutableIntStateOf(0) }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .ottoBottomSheetContent()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                MapSheetHeader(
                    title = stringResource(R.string.map_people_sharing_title),
                    onDone = { peopleSharingSheetVisible = false },
                    doneLabel = stringResource(R.string.map_sharing_done),
                )
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = peopleSharingSheetTab) {
                    Tab(
                        selected = peopleSharingSheetTab == 0,
                        onClick = { peopleSharingSheetTab = 0 },
                        text = { Text(stringResource(R.string.map_people_sharing_tab_people)) },
                    )
                    Tab(
                        selected = peopleSharingSheetTab == 1,
                        onClick = { peopleSharingSheetTab = 1 },
                        text = { Text(stringResource(R.string.map_people_sharing_tab_squads)) },
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (peopleSharingSheetTab) {
                    0 -> {
                        OutlinedTextField(
                            value = peopleSearchQuery,
                            onValueChange = { peopleSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.map_search_people_sharing)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        )
                        Spacer(Modifier.height(16.dp))

                        val filtered =
                            remember(sharingListBase, peopleSearchQuery) {
                                sharingListBase.filter {
                                    presenceMatchesPeopleSharingSearch(it, ui.contacts, peopleSearchQuery)
                                }
                            }

                        if (filtered.isEmpty()) {
                            OttoEmptyState(
                                title = stringResource(R.string.map_no_one_sharing_title),
                                body =
                                    if (sharingListBase.isEmpty()) {
                                        stringResource(R.string.map_no_one_sharing_body)
                                    } else {
                                        stringResource(R.string.map_no_sharing_search_match)
                                    },
                                icon = Icons.Outlined.LocationOn,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 260.dp),
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    ),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    filtered.forEachIndexed { index, member ->
                                        SquadMembersListRow(
                                            memberUserId = member.userId,
                                            contacts = ui.contacts,
                                            meUser = ui.me,
                                            subtitleOverride = presenceSharingUpdateLabel(member.updatedAt),
                                            showNavigateChevron = false,
                                            presenceStatusDotColor = presenceLifecycleDotColor(member),
                                            onClick = {
                                                peopleSharingSheetVisible = false
                                                clusterMembersPick = null
                                                scope.launch {
                                                    delay(180)
                                                    val ll = geoLatLngOrNull(member.lat, member.lng)
                                                    if (ll == null) {
                                                        showSnack(ctx.getString(R.string.map_presence_follow_no_coords))
                                                    } else {
                                                        followDeviceCamera = false
                                                        followedSquadId = null
                                                        followedPresenceUserId = member.userId
                                                        mapViewportState.easeToLatLngZoom(ll, 17.0, 380)
                                                    }
                                                }
                                            },
                                        )
                                        if (index < filtered.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = squadSearchQuery,
                            onValueChange = { squadSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.map_squad_track_search_squads)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        )
                        Spacer(Modifier.height(16.dp))

                        val filteredSquads =
                            remember(circles, squadSearchQuery, ui.presenceMembersByCircleId) {
                                val q = squadSearchQuery.trim().lowercase()
                                val matched =
                                    if (q.isEmpty()) {
                                        circles
                                    } else {
                                        circles.filter { it.name.lowercase().contains(q) }
                                    }
                                sortedCirclesForMapSquadList(matched, ui.presenceMembersByCircleId)
                            }

                        when {
                            circles.isEmpty() -> {
                                OttoEmptyState(
                                    title = stringResource(R.string.map_squad_track_no_squads),
                                    icon = Icons.Outlined.Groups,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 220.dp),
                                )
                            }
                            filteredSquads.isEmpty() -> {
                                OttoEmptyState(
                                    title = stringResource(R.string.map_no_sharing_search_match),
                                    icon = Icons.Outlined.Search,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 220.dp),
                                )
                            }
                            else -> {
                                val trackSquadItems =
                                    filteredSquads.map { circle ->
                                        val activeCount =
                                            mapSquadActiveSharingCount(circle.id, ui.presenceMembersByCircleId)
                                        MapSquadTrackListItem(
                                            circle = circle,
                                            name = circle.name,
                                            subtitle =
                                                if (activeCount > 0) {
                                                    stringResource(R.string.map_squad_track_sharing_count, activeCount)
                                                } else {
                                                    stringResource(R.string.map_squad_track_active_none)
                                                },
                                        )
                                    }
                                MapSquadTrackGroupedList(
                                    items = trackSquadItems,
                                    onRowClick = { item ->
                                        peopleSharingSheetVisible = false
                                        clusterMembersPick = null
                                        followDeviceCamera = false
                                        followedPresenceUserId = null
                                        followedSquadId = item.circle.id
                                        onMapLayerCircleVisible(item.circle.id, true)
                                    },
                                    rowTrailing = { item ->
                                        MapSquadTrackNavigateTrailing(
                                            isTracked = followedSquadId == item.circle.id,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (placesSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { placesSheetVisible = false },
            sheetState = placesSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .ottoBottomSheetContent()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                MapSheetHeader(
                    title = stringResource(R.string.places_sheet_title),
                    onDone = { placesSheetVisible = false },
                    doneLabel = stringResource(R.string.map_sharing_done),
                )
                Text(
                    stringResource(R.string.places_sheet_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = {
                        savePlaceCoordinate = null
                        savePlaceAddressDraft = null
                        saveDialogOpen = true
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.places_save_this_spot))
                }

                Spacer(Modifier.height(14.dp))

                if (ui.savedPlaces.isEmpty()) {
                    EmptyTabMessage(
                        text =
                            stringResource(
                                if (ui.presenceMembers.isEmpty()) {
                                    R.string.map_empty_no_shares
                                } else {
                                    R.string.map_hints_no_pins
                                },
                            ),
                        icon = Icons.Outlined.LocationOn,
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                } else {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = ui.savedPlaces, key = { it.id }) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val target = geoLatLngOrNull(p.latitude, p.longitude) ?: return@FilterChip
                                        followDeviceCamera = false
                                        followedPresenceUserId = null
                                        followedSquadId = null
                                        scope.launch {
                                            mapViewportState.easeToLatLngZoom(target, 16.0, 380)
                                        }
                                    },
                                    label = {
                                        Text(
                                            p.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 160.dp),
                                        )
                                    },
                                )
                                IconButton(
                                    onClick = {
                                        renameTarget = p
                                        renameDraft = p.name
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.places_rename_cd),
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSavedPlace(p.id) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.places_remove_cd),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(placesSheetVisible) {
        if (placesSheetVisible) {
            placesSheetState.show()
        }
    }

    LaunchedEffect(peopleSharingSheetVisible) {
        if (peopleSharingSheetVisible) {
            peopleSharingSheetState.show()
        }
    }

    LaunchedEffect(sharingSheetVisible) {
        if (sharingSheetVisible) {
            sharingSheetState.show()
        }
    }

}



@Composable
private fun PresenceListFallback(members: List<PresenceMemberDto>, modifier: Modifier = Modifier) {
    if (members.isEmpty()) {
        EmptyTabMessage(
            text = stringResource(R.string.map_empty_no_shares),
            icon = Icons.Outlined.LocationOn,
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = members, key = { "${it.userId}-${it.updatedAt}" }) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.map_member_id, shortenId(p.userId)), style = MaterialTheme.typography.titleSmall)
                    val lat = p.lat
                    val lng = p.lng
                    Text(
                        if (lat != null && lng != null) {
                            stringResource(R.string.map_lat_lng, lat, lng)
                        } else {
                            stringResource(R.string.map_no_fix)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.map_sharing_active, p.isActive),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    OttoEmptyState(
        title = text,
        body = body,
        icon = icon,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
private fun LoadingTabMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ContactChip(
    user: UserDto,
    onMessage: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserProfileAvatar(
                    displayName = user.displayName,
                    userId = user.id,
                    avatarUrl = user.avatarUrl,
                    mapAccentKey = user.mapAccentKey,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(user.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            TextButton(onClick = onMessage) {
                Text(stringResource(R.string.profile_contact_chat))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DirectMessagesOverlay(
    dm: DirectMessagesOverlayUi,
    myUserId: String?,
    meUser: UserDto? = null,
    circles: List<CircleDto> = emptyList(),
    contacts: List<UserDto> = emptyList(),
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    allUpcomingEventsForChat: List<EventDto> = emptyList(),
    chatAttachmentHydratedEventsById: Map<String, EventDto> = emptyMap(),
    onPrefetchChatAttachmentEvents: (Set<String>) -> Unit = {},
    eventRsvpSubmittingEventId: String? = null,
    onSubmitEventRsvp: (String, String) -> Unit = { _, _ -> },
    onOpenEventDetail: (String) -> Unit = {},
    onClose: () -> Unit,
    onSelectConversation: (DirectConversationDto) -> Unit,
    onBackThread: () -> Unit,
    onSend: (String, ChatPendingComposerAttachment?) -> Unit,
    onSendVideo: (String, ChatSendVideoAttachment) -> Unit = { _, _ -> },
    onCancelDirectChatVideoUpload: (String) -> Unit = {},
    onSetThreadReplyTo: (DirectMessageDto) -> Unit = {},
    onClearThreadReplyTo: () -> Unit = {},
    onPostDmReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onBeginDirectThreadEdit: (String) -> Unit = {},
    onCancelDirectThreadEdit: () -> Unit = {},
    onDeleteDirectThreadMessage: (String) -> Unit = {},
    onFetchOlderDirectChatForQuoteJump: (String) -> Unit = {},
    onChatProfileMessagePeer: (String) -> Unit = {},
    onChatProfileViewPeer: (String) -> Unit = {},
    onChatProfileOpenSquad: (String) -> Unit = {},
    onNavigateToOwnProfileTab: () -> Unit = {},
    onReportConcern: () -> Unit = {},
    onBlockDmPeer: (String) -> Unit = {},
    onUnblockDmPeer: (String) -> Unit = {},
    onOpenSharedPlace: (CircleChatPlaceAttachmentDto, String) -> Unit = { _, _ -> },
) {
    BackHandler {
        when {
            dm.selectedConversationId != null -> onBackThread()
            else -> onClose()
        }
    }

    val threadComposeKey = dm.selectedConversationId.orEmpty().ifBlank { "_conv_list_" }
    var threadComposeDraft by rememberSaveable(threadComposeKey) { mutableStateOf("") }
    val ctx = LocalContext.current
    val dmOverlayScope = rememberCoroutineScope()
    var showDmKlipyGifPicker by rememberSaveable(threadComposeKey) { mutableStateOf(false) }
    var pendingDmAttachment by remember(threadComposeKey) { mutableStateOf<ChatPendingComposerAttachment?>(null) }
    var isLoadingDmLocationAttachment by remember(threadComposeKey) { mutableStateOf(false) }
    var showDmLocationPrimer by remember(threadComposeKey) { mutableStateOf(false) }
    var showDmLocationDenied by remember(threadComposeKey) { mutableStateOf(false) }
    var dmAttachError by remember(threadComposeKey) { mutableStateOf<String?>(null) }
    var dmChatVideoLimitDialog by remember(threadComposeKey) { mutableStateOf<String?>(null) }
    val dmLocationReader = remember(ctx) { ctx.applicationContext.appContainer().approximateLocationReader }

    fun attachDmLocationIfAuthorized() {
        beginChatComposerLocationAttachment(
            scope = dmOverlayScope,
            context = ctx,
            locationReader = dmLocationReader,
            onLoadingChanged = { isLoadingDmLocationAttachment = it },
            onSuccess = { pendingDmAttachment = it },
            onError = { dmAttachError = it },
        )
    }

    fun beginDmLocationAttachmentFlow() {
        val fineGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            fineGranted || coarseGranted -> attachDmLocationIfAuthorized()
            else -> showDmLocationPrimer = true
        }
    }
    val pickDmChatPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (dm.selectedConversationId.isNullOrBlank()) return@rememberLauncherForActivityResult
            dmOverlayScope.launch {
                dmAttachError = null
                val prep = withContext(Dispatchers.IO) { prepareChatSendPhotoAttachment(ctx, uri) }
                prep.fold(
                    onSuccess = {
                        pendingDmAttachment =
                            ChatPendingComposerAttachment(
                                kind = ChatPendingComposerAttachmentKind.Photo,
                                photo = it,
                            )
                    },
                    onFailure = { e ->
                        dmAttachError =
                            e.message?.takeIf { m -> m.isNotBlank() }
                                ?: ctx.getString(R.string.chat_attachment_read_failed)
                    },
                )
            }
        }
    val pickDmChatVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (dm.selectedConversationId.isNullOrBlank()) return@rememberLauncherForActivityResult
            dmOverlayScope.launch {
                dmAttachError = null
                val validation = withContext(Dispatchers.IO) { ChatVideoUploadPrep.validate(ctx, uri) }
                if (validation.isFailure) {
                    dmChatVideoLimitDialog =
                        validation.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                            ?: ctx.getString(R.string.chat_attachment_read_failed)
                    return@launch
                }
                val prep = withContext(Dispatchers.IO) { ChatVideoUploadPrep.prepare(ctx, uri) }
                prep.fold(
                    onSuccess = {
                        pendingDmAttachment =
                            ChatPendingComposerAttachment(
                                kind = ChatPendingComposerAttachmentKind.Video,
                                video = it,
                            )
                    },
                    onFailure = { e ->
                        dmChatVideoLimitDialog =
                            e.message?.takeIf { m -> m.isNotBlank() }
                                ?: ctx.getString(R.string.chat_attachment_read_failed)
                    },
                )
            }
        }
    LaunchedEffect(dm.threadEditingMessageId, threadComposeKey) {
        if (!dm.threadEditingMessageId.isNullOrBlank()) {
            pendingDmAttachment = null
            dmAttachError = null
        }
    }
    KlipyGifPickerSheet(
        visible = showDmKlipyGifPicker,
        customerId = myUserId.orEmpty(),
        onSelect = { selection, searchQuery ->
            pendingDmAttachment =
                ChatPendingComposerAttachment(
                    kind = ChatPendingComposerAttachmentKind.KlipyGif,
                    klipyGif = selection,
                    klipySearchQuery = searchQuery,
                )
            dmAttachError = null
            showDmKlipyGifPicker = false
        },
        onDismiss = { showDmKlipyGifPicker = false },
    )

    // Header chrome lives in [OttoShell] TopAppBar (same slot/UX as squad detail).
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        dm.threadSnack?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        if (dm.selectedConversationId == null) {
            when {
                dm.listLoading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.messages_loading_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                dm.conversations.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.messages_thread_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items = dm.conversations, key = { it.id }) { conv ->
                            val label =
                                conv.otherUser?.displayName?.takeIf { it.isNotBlank() }
                                    ?: shortenId(conv.id)
                            ElevatedCard(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectConversation(conv) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(label, style = MaterialTheme.typography.titleSmall)
                                    conv.lastMessageAt?.takeIf { it.isNotBlank() }?.let { lm ->
                                        Text(
                                            lm,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val conversationId = dm.selectedConversationId.orEmpty()
            val dmComposerHaptic = LocalHapticFeedback.current
            var pendingDeleteDmBubble by remember(conversationId) { mutableStateOf<CircleChatMessageDto?>(null) }
            var prevDmEditingId by remember(conversationId) { mutableStateOf<String?>(null) }
            LaunchedEffect(dm.threadEditingMessageId, dm.messages, conversationId) {
                val id = dm.threadEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
                if (id != null) {
                    val body = dm.messages.find { m -> ottoUserIdsEqual(m.id, id) }?.body?.trim().orEmpty()
                    threadComposeDraft = body
                } else if (prevDmEditingId != null) {
                    threadComposeDraft = ""
                }
                prevDmEditingId = id
            }
            val dmThreadEditFocusRequester = remember(conversationId) { FocusRequester() }
            LaunchedEffect(dm.threadEditingMessageId, conversationId) {
                if (!dm.threadEditingMessageId.isNullOrBlank()) {
                    delay(80)
                    dmThreadEditFocusRequester.requestFocus()
                }
            }
            val dmEditingId = dm.threadEditingMessageId?.trim()?.takeIf { it.isNotBlank() }
            val dmEditingMessage =
                dmEditingId?.let { eid ->
                    dm.messages.find { m -> ottoUserIdsEqual(m.id, eid) }
                }
            val dmEditPreviewRaw = dmEditingMessage?.body.orEmpty()
            val dmEditBaselineTrimmed = dmEditPreviewRaw.trim()
            val todayLbl = stringResource(R.string.squad_detail_separator_today)
            val yesterdayLbl = stringResource(R.string.squad_detail_separator_yesterday)
            val circleMessages =
                remember(dm.messages, conversationId) {
                    dm.messages.map { it.asCircleChatMessageForBubble(conversationId) }
                }
            val timeline =
                remember(circleMessages, todayLbl, yesterdayLbl) {
                    squadChatTimelineItems(circleMessages, todayLbl, yesterdayLbl)
                }
            LaunchedEffect(dm.messages, allUpcomingEventsForChat, chatAttachmentHydratedEventsById, conversationId) {
                val ids =
                    dm.messages.mapNotNull { m ->
                        m.eventAttachment?.eventId?.trim()?.takeIf { it.isNotEmpty() }
                    }.toSet()
                onPrefetchChatAttachmentEvents(ids)
            }
            val dmChatListState = rememberLazyListState()
            val dmChatScrollScope = rememberCoroutineScope()
            var pendingDmQuoteJumpId by remember(conversationId) { mutableStateOf<String?>(null) }
            LaunchedEffect(timeline, pendingDmQuoteJumpId) {
                val target = pendingDmQuoteJumpId ?: return@LaunchedEffect
                val tIdx =
                    timeline.indexOfFirst { row ->
                        row is SquadChatTimelineItem.Bubble &&
                            ottoUserIdsEqual(row.msg.id, target)
                    }
                if (tIdx >= 0) {
                    dmChatListState.animateScrollToItem(tIdx)
                    pendingDmQuoteJumpId = null
                }
            }
            var dmChatJumpReady by remember(conversationId) { mutableStateOf(false) }
            val dmDensity = LocalDensity.current
            val dmPinThresholdPx =
                remember(dmDensity.density) {
                    chatPinToLatestThresholdPx(dmDensity.density)
                }
            ChatTimelineAutoScrollEffect(
                timeline = timeline,
                listState = dmChatListState,
                conversationKey = conversationId,
                myUserId = myUserId,
                pinThresholdPx = dmPinThresholdPx,
                isHistoryLoading = dm.threadLoading,
                onJumpReadyChange = { dmChatJumpReady = it },
            )

            val jumpDmChatToQuotedMessage: (String) -> Unit =
                remember(timeline, dmChatScrollScope, dmChatListState, conversationId, onFetchOlderDirectChatForQuoteJump) {
                    { rawId ->
                        val parentId = rawId.trim()
                        if (parentId.isNotEmpty()) {
                            val tIdx =
                                timeline.indexOfFirst { row ->
                                    row is SquadChatTimelineItem.Bubble &&
                                        ottoUserIdsEqual(row.msg.id, parentId)
                                }
                            if (tIdx >= 0) {
                                dmChatScrollScope.launch {
                                    dmChatListState.animateScrollToItem(tIdx)
                                }
                            } else {
                                pendingDmQuoteJumpId = parentId
                                onFetchOlderDirectChatForQuoteJump(conversationId)
                            }
                        }
                    }
                }

            var longPressMessage by remember(conversationId) { mutableStateOf<CircleChatMessageDto?>(null) }
            var reactionsDetailMessage by remember(conversationId) { mutableStateOf<CircleChatMessageDto?>(null) }
            val dmReactionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            dm.threadLoading && dm.messages.isEmpty() ->
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                            !dm.threadLoading && dm.messages.isEmpty() -> {
                                Text(
                                    stringResource(R.string.messages_thread_empty),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            else ->
                                Box(Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .alpha(
                                                    if (dmChatJumpReady || timeline.isEmpty()) 1f else 0f,
                                                ),
                                        state = dmChatListState,
                                        contentPadding = PaddingValues(bottom = 12.dp, top = 4.dp),
                                    ) {
                                    itemsIndexed(
                                        timeline,
                                        key = { ix, row ->
                                            when (row) {
                                                is SquadChatTimelineItem.Bubble -> row.msg.id + "-$ix"
                                                is SquadChatTimelineItem.SystemNotice -> row.msg.id + "-$ix"
                                                is SquadChatTimelineItem.DaySeparator -> "sep-$ix-${row.label}"
                                            }
                                        },
                                    ) { _, row ->
                                        SquadChatTimelineRow(
                                            row = row,
                                            myUserId = myUserId,
                                            meUser = meUser,
                                            squadScopedEvents = emptyList(),
                                            allUpcomingEvents = allUpcomingEventsForChat,
                                            chatAttachmentHydration = chatAttachmentHydratedEventsById,
                                            eventRsvpSubmittingEventId = eventRsvpSubmittingEventId,
                                            onSubmitEventRsvp = onSubmitEventRsvp,
                                            onOpenEventDetail = onOpenEventDetail,
                                            onOpenSharedPlace = { att, messageId ->
                                                if (!att.isParentDeleted) {
                                                    onOpenSharedPlace(att, messageId)
                                                }
                                            },
                                            onLongPressBubble = { msg -> longPressMessage = msg },
                                            onReactionsTap = { msg -> reactionsDetailMessage = msg },
                                            onTapReplyQuote = jumpDmChatToQuotedMessage,
                                            memberDisplayNamesByUserId = emptyMap(),
                                            contacts = contacts,
                                            onTapPeerAvatar = { userId ->
                                                if (!myUserId.isNullOrBlank() && ottoUserIdsEqual(userId, myUserId)) {
                                                    onNavigateToOwnProfileTab()
                                                } else {
                                                    onChatProfileViewPeer(userId)
                                                }
                                            },
                                            onCancelVideoUpload = onCancelDirectChatVideoUpload,
                                        )
                                    }
                                    }
                                    if (!dmChatJumpReady && timeline.isNotEmpty()) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    }
                                }
                        }
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        dmAttachError?.takeIf { it.isNotBlank() }?.let { err ->
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        OttoChatComposerBar(
                            value = threadComposeDraft,
                            onValueChange = {
                                dmAttachError = null
                                threadComposeDraft = it
                            },
                            sendBusy = dm.sendBusy,
                            showAttachButton = dm.threadEditingMessageId.isNullOrBlank(),
                            enabledAttachmentActions = ChatComposerAttachmentAction.directChatActions,
                            pendingAttachment = pendingDmAttachment,
                            isLoadingLocationAttachment = isLoadingDmLocationAttachment,
                            onClearPendingAttachment =
                                if (pendingDmAttachment != null) {
                                    { pendingDmAttachment = null }
                                } else {
                                    null
                                },
                            onAttachmentAction = { action ->
                                when (action) {
                                    ChatComposerAttachmentAction.Photo ->
                                        pickDmChatPhoto.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    ChatComposerAttachmentAction.Gif -> {
                                        if (KlipyConfiguration.isConfigured) {
                                            showDmKlipyGifPicker = true
                                        } else {
                                            dmAttachError = ctx.getString(R.string.klipy_picker_unavailable)
                                        }
                                    }
                                    ChatComposerAttachmentAction.Video ->
                                        pickDmChatVideo.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                        )
                                    ChatComposerAttachmentAction.Location -> beginDmLocationAttachmentFlow()
                                    ChatComposerAttachmentAction.CreateEvent -> Unit
                                }
                            },
                            replyBannerKey = dm.threadReplyTo?.id,
                            replyBanner =
                                dm.threadReplyTo?.let { target ->
                                    @Composable {
                                        val auth =
                                            target.sender
                                                ?.displayName
                                                ?.trim()
                                                ?.takeIf { it.isNotEmpty() }
                                                ?: stringResource(R.string.squads_owner_unknown)
                                        val snip =
                                            when {
                                                !target.body.isNullOrBlank() && target.body.trim().isNotEmpty() ->
                                                    target.body.trim()
                                                target.videoAttachment != null ->
                                                    stringResource(R.string.chat_reply_video)
                                                !target.imageUrl.isNullOrBlank() ->
                                                    stringResource(ChatImageUrlDisplay.replySnippetResId(target.imageUrl))
                                                else -> ""
                                            }
                                        if (snip.isNotEmpty()) {
                                            ChatComposerReplyBanner(
                                                authorLabel = auth,
                                                snippet = snip,
                                                onCancel = onClearThreadReplyTo,
                                                onTapReplyTo = {
                                                    dmComposerHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    jumpDmChatToQuotedMessage(target.id)
                                                },
                                            )
                                        }
                                    }
                                },
                            isEditMode = !dm.threadEditingMessageId.isNullOrBlank(),
                            editPreviewText = dmEditPreviewRaw,
                            editBaselineTrimmed = dmEditBaselineTrimmed,
                            onCancelEdit =
                                if (!dm.threadEditingMessageId.isNullOrBlank()) {
                                    onCancelDirectThreadEdit
                                } else {
                                    null
                                },
                            focusRequester = dmThreadEditFocusRequester,
                            placeholder = {
                                Text(
                                    stringResource(R.string.messages_placeholder),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onSend =
                                saveDmThread@{
                                    val t = threadComposeDraft.trim()
                                    val editing = !dm.threadEditingMessageId.isNullOrBlank()
                                    val attachment = pendingDmAttachment?.takeIf { !editing }
                                    if (t.isEmpty() && attachment == null) return@saveDmThread
                                    if (editing && t == dmEditBaselineTrimmed) return@saveDmThread
                                    onSend(threadComposeDraft, attachment)
                                    if (!editing) {
                                        threadComposeDraft = ""
                                        pendingDmAttachment = null
                                    }
                                },
                        )
                    }
                }

                val showDmJumpToLatest by remember(dmChatListState, dmChatJumpReady, dmPinThresholdPx, timeline.size, dm.threadLoading) {
                    derivedStateOf {
                        dmChatJumpReady &&
                            timeline.isNotEmpty() &&
                            !dm.threadLoading &&
                            !dmChatListState.isPinnedToLatestChat(dmPinThresholdPx)
                    }
                }
                ChatJumpToLatestFloatingButton(
                    visible = showDmJumpToLatest,
                    onClick = {
                        dmChatScrollScope.launch {
                            if (timeline.isNotEmpty()) {
                                dmChatListState.scrollToChatLatestBottom(
                                    timeline.lastIndex,
                                    animate = true,
                                )
                            }
                        }
                    },
                    applyNavigationBarsPadding = false,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                longPressMessage?.let { lp ->
                    val ownBubble = squadChatMessageOwnedUserBubble(lp, myUserId)
                    val canEdit = squadChatMessageEditEligible(lp, myUserId)
                    ChatMessageActionsDialog(
                        onDismissRequest = { longPressMessage = null },
                        onReply = {
                            val d = dm.messages.find { m -> m.id == lp.id }
                            if (d != null) onSetThreadReplyTo(d)
                        },
                        onReaction = { em -> onPostDmReaction(lp.id, em) },
                        onEdit =
                            if (canEdit) {
                                { onBeginDirectThreadEdit(lp.id) }
                            } else {
                                null
                            },
                        onDelete =
                            if (ownBubble) {
                                { pendingDeleteDmBubble = lp }
                            } else {
                                null
                            },
                    )
                }
                ChatDeleteSystemAlertEffect(
                    victimMessageId = pendingDeleteDmBubble?.id,
                    title = stringResource(R.string.chat_delete_message_confirm_title),
                    message = stringResource(R.string.chat_delete_message_confirm_body),
                    deleteLabel = stringResource(R.string.chat_delete_message),
                    cancelLabel = stringResource(R.string.chat_cancel_edit),
                    onDelete = {
                        pendingDeleteDmBubble?.let { onDeleteDirectThreadMessage(it.id) }
                    },
                    onDismiss = { pendingDeleteDmBubble = null },
                )
                reactionsDetailMessage?.let { msg ->
                    ModalBottomSheet(
                        onDismissRequest = { reactionsDetailMessage = null },
                        sheetState = dmReactionsSheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() },
                    ) {
                        SquadChatMessageReactionsSheetContent(
                            reactions = msg.reactions,
                            memberDisplayNamesByUserId = emptyMap(),
                            contacts = contacts,
                        )
                    }
                }
                ChatComposerLocationPermissionHost(
                    showLocationPrimer = showDmLocationPrimer,
                    showLocationDeniedModal = showDmLocationDenied,
                    onDismissLocationPrimer = { showDmLocationPrimer = false },
                    onDismissLocationDenied = { showDmLocationDenied = false },
                    onLocationPermissionResult = { granted ->
                        if (granted) {
                            attachDmLocationIfAuthorized()
                        } else {
                            showDmLocationDenied = true
                        }
                    },
                )
                dmChatVideoLimitDialog?.let { message ->
                    AlertDialog(
                        onDismissRequest = { dmChatVideoLimitDialog = null },
                        title = { Text(stringResource(R.string.chat_video_limit_title)) },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = { dmChatVideoLimitDialog = null }) {
                                Text(stringResource(R.string.squad_invite_link_pilot_ok))
                            }
                        },
                    )
                }
            }
        }
    }
}


private fun markerDistanceFromDevice(
    lat: Double?,
    lng: Double?,
    device: LatLng?,
): String? {
    if (device == null || lat == null || lng == null) return null
    if (!lat.isFinite() || !lng.isFinite()) return null
    val results = FloatArray(1)
    android.location.Location.distanceBetween(device.latitude, device.longitude, lat, lng, results)
    return formatMapMarkerDistanceMeters(results[0].toDouble())
}

private fun resolveEventForMapPeek(
    eventId: String,
    snapshot: EventDto?,
    mapEvents: List<EventDto>,
    events: List<EventDto>,
    communityEvents: List<EventDto>,
    squadFeedEvents: List<EventDto>,
    hydratedById: Map<String, EventDto>,
): EventDto? {
    if (snapshot != null && snapshot.id == eventId) return snapshot
    mapEvents.firstOrNull { it.id == eventId }?.let { return it }
    events.firstOrNull { it.id == eventId }?.let { return it }
    communityEvents.firstOrNull { it.id == eventId }?.let { return it }
    squadFeedEvents.firstOrNull { it.id == eventId }?.let { return it }
    hydratedById[eventId]?.let { return it }
    return null
}

private fun formatIsoForDisplay(raw: String?): String {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return "—"
    val instant = parseEventInstant(trimmed)
    if (instant != null) {
        return instant
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }
    return trimmed
        .replace("T", " ")
        .substringBefore(".")
        .removeSuffix("Z")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?: "—"
}

internal fun shortAddress(event: EventDto): String {
    val label = event.address?.label?.trim().orEmpty()
    if (label.isNotEmpty()) return label
    val cityLine = listOfNotNull(event.address?.city, event.address?.region).joinToString()
    if (cityLine.isNotBlank()) return cityLine
    return event.visibility?.trim().orEmpty()
}

/**
 * Builds a Maps [LatLng] only when values are finite and within geographic bounds.
 * Invalid numbers (NaN from deserialization, bogus API payloads) crash [LatLng] and surface as Compose
 * measure-time failures under [androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads].
 */
internal fun geoLatLngOrNull(lat: Double?, lng: Double?): LatLng? {
    if (lat == null || lng == null) return null
    if (!lat.isFinite() || !lng.isFinite()) return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return LatLng(lat, lng)
}

private fun LatLng.toMapboxPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun OttoMapCamera.toMapboxCameraOptions(): CameraOptions =
    CameraOptions.Builder()
        .center(target.toMapboxPoint())
        .zoom(zoom.toDouble())
        .bearing(bearing.toDouble())
        .pitch(tilt.toDouble())
        .build()

private fun CameraState.toOttoMapCamera(): OttoMapCamera =
    OttoMapCamera(
        target = LatLng(center.latitude(), center.longitude()),
        zoom = zoom.toFloat(),
        bearing = bearing.toFloat(),
        tilt = pitch.toFloat(),
    )

/** Instant camera move — used for per-frame follow-me panning (smooth path comes from marker interpolation). */
private fun MapViewportState.jumpToLatLngZoom(
    target: LatLng,
    zoom: Double,
) {
    setCameraOptions(
        CameraOptions.Builder()
            .center(target.toMapboxPoint())
            .zoom(zoom)
            .bearing(0.0)
            .pitch(0.0)
            .padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0))
            .build(),
    )
}

/** Pitched drive follow — apply [EdgeInsets] directly (iOS parity; do not use single-point cameraForCoordinates). */
private fun MapViewportState.moveToDriveFollowCamera(
    target: LatLng,
    zoom: Double,
    bearing: Float,
    pitch: Double,
    padding: com.mapbox.maps.EdgeInsets?,
) {
    val builder =
        CameraOptions.Builder()
            .center(target.toMapboxPoint())
            .zoom(zoom)
            .bearing(bearing.toDouble())
            .pitch(pitch)
    padding?.let { builder.padding(it) }
    setCameraOptions(builder.build())
}

private fun MapViewportState.easeToDriveFollowCamera(
    target: LatLng,
    bearing: Float,
    padding: com.mapbox.maps.EdgeInsets?,
    durationMs: Long = MapDriveCamera.DRIVE_CAMERA_TRANSITION_MS,
) {
    val builder =
        CameraOptions.Builder()
            .center(target.toMapboxPoint())
            .zoom(MapDriveCamera.DRIVE_ZOOM)
            .bearing(bearing.toDouble())
            .pitch(MapDriveCamera.DRIVE_PITCH_DEGREES)
    padding?.let { builder.padding(it) }
    easeTo(
        builder.build(),
        mapAnimationOptions {
            duration(durationMs)
        },
    )
}

private fun MapViewportState.easeToFlatFollowCamera(
    target: LatLng,
    zoom: Double,
    durationMs: Long = MapDriveCamera.DRIVE_CAMERA_TRANSITION_MS,
) {
    easeTo(
        CameraOptions.Builder()
            .center(target.toMapboxPoint())
            .zoom(zoom)
            .bearing(0.0)
            .pitch(0.0)
            .padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0))
            .build(),
        mapAnimationOptions {
            duration(durationMs)
        },
    )
}

private fun MapViewportState.easeToLatLngZoom(
    target: LatLng,
    zoom: Double,
    durationMs: Long,
) {
    easeTo(
        CameraOptions.Builder()
            .center(target.toMapboxPoint())
            .zoom(zoom)
            .build(),
        mapAnimationOptions {
            duration(durationMs)
        },
    )
}

private suspend fun MapViewportState.easeToLatLngBounds(
    targets: List<LatLng>,
    padding: Double,
    durationMs: Long,
) {
    if (targets.isEmpty()) return
    val camera =
        cameraForCoordinates(
            targets.map { it.toMapboxPoint() },
            coordinatesPadding = EdgeInsets(padding, padding, padding, padding),
        )
    easeTo(
        camera,
        mapAnimationOptions {
            duration(durationMs)
        },
    )
}

private fun LatLng.radiusPolygonPoints(
    radiusMeters: Double,
    segments: Int = 72,
): List<Point> {
    val earthRadiusMeters = 6_371_000.0
    val latRad = Math.toRadians(latitude)
    val lngRad = Math.toRadians(longitude)
    val angularDistance = radiusMeters / earthRadiusMeters
    return (0..segments).map { index ->
        val bearing = 2.0 * Math.PI * index / segments
        val pointLat =
            kotlin.math.asin(
                kotlin.math.sin(latRad) * kotlin.math.cos(angularDistance) +
                    kotlin.math.cos(latRad) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing),
            )
        val pointLng =
            lngRad +
                kotlin.math.atan2(
                    kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(latRad),
                    kotlin.math.cos(angularDistance) - kotlin.math.sin(latRad) * kotlin.math.sin(pointLat),
                )
        Point.fromLngLat(Math.toDegrees(pointLng), Math.toDegrees(pointLat))
    }
}

/** Sum unread counts for [circleId] across map keys (handles mismatched id casing/whitespace). */
internal fun unreadChatCountForCircle(
    map: Map<String, Int>,
    circleId: String,
): Int = map.entries.filter { ottoUserIdsEqual(it.key, circleId) }.sumOf { it.value }

/** Trimmed, case-insensitive match for user ids from API vs session (avoids "me" showing as a raw id). */
internal fun ottoUserIdsEqual(a: String?, b: String?): Boolean {
    val ta = a?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val tb = b?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return ta.equals(tb, ignoreCase = true)
}

internal fun shortenId(raw: String): String =
    if (raw.length <= 10) {
        raw
    } else {
        raw.take(6) + "…"
    }
