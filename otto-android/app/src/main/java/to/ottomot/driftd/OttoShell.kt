package to.ottomot.driftd

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import to.ottomot.driftd.routebuilder.RouteBuilderScreen
import to.ottomot.driftd.routebuilder.RouteBuilderViewModel
import to.ottomot.driftd.core.location.isFreshForRouteBuilderCenter
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoShellBottomBar
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoShellScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import to.ottomot.driftd.ui.onboarding.MarketingOnboardingScreen

/**
 * Bottom tabs match iOS RootTab order: Squads → Events → **Map** (center) → Garage → Profile.
 * Cold start opens **Squads** on Android (iOS may differ).
 */
internal enum class OttoMainTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Squads(R.string.tab_squads, Icons.Outlined.Groups),
    Events(R.string.tab_events, Icons.Outlined.CalendarMonth),
    Map(R.string.tab_map, Icons.Outlined.Map),
    Garage(R.string.tab_garage, Icons.Outlined.DirectionsCar),
    Profile(R.string.tab_profile, Icons.Outlined.AccountCircle),
}

@Composable
private fun OttoMapTabNavIcon(
    selected: Boolean,
    presentation: DriveSessionPillPresentation,
    contentDescription: String,
) {
    val scheme = MaterialTheme.colorScheme
    val iconTint = if (selected) scheme.primary else scheme.onSurfaceVariant
    val indicatorColor = presentation.mapTabIndicatorColor()
    Box {
        Icon(
            Icons.Outlined.Map,
            contentDescription = contentDescription,
            tint = iconTint,
        )
        if (indicatorColor != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-2).dp)
                    .size(9.dp)
                    .background(indicatorColor, CircleShape)
                    .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.4f)), CircleShape),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OttoShell(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val vm: OttoShellViewModel = viewModel(factory = OttoShellViewModel.factory(container))
    val ui by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current

    var selectedTab by remember { mutableStateOf(OttoMainTab.Squads) }
    var squadsChromeIdx by rememberSaveable { mutableIntStateOf(SquadChromeSection.SquadList.ordinal) }

    var squadsToolbarCreateTicks by remember { mutableIntStateOf(0) }
    var garageToolbarAddTicks by remember { mutableIntStateOf(0) }
    var driveChatDestinationContext by remember { mutableStateOf<DriveChatShareContext?>(null) }
    var routeChatDestinationRoute by remember { mutableStateOf<SavedRouteDto?>(null) }

    val scope = rememberCoroutineScope()
    val onboardingCompleted by container.sessionRepository.marketingOnboardingCompletedState.collectAsStateWithLifecycle()
    var replayMarketingOnboarding by remember { mutableStateOf(false) }
    val showMarketingOnboarding = !onboardingCompleted || replayMarketingOnboarding

    val deeplinkBump by InviteDeepLinkStore.deeplinkSignals.collectAsStateWithLifecycle()
    val inviteAcceptBump by InviteDeepLinkStore.acceptRefreshSignals.collectAsStateWithLifecycle()
    val pushTapBump by PushNotificationTapStore.signals.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> vm.notifyAppInForegroundForPresence(true)
                    Lifecycle.Event.ON_STOP -> vm.notifyAppInForegroundForPresence(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.notifyAppInForegroundForPresence(false)
        }
    }
    val shouldKeepScreenAwake =
        remember(
            ui.mapSharingLocation,
            ui.liveDriveRecordingActive,
            ui.mapRouteSessionActive,
            ui.hasActiveDriveSession,
        ) {
            ui.hasActiveDriveSession || ui.liveDriveRecordingActive || ui.mapRouteSessionActive
        }
    DisposableEffect(lifecycleOwner, shouldKeepScreenAwake) {
        val activity = ctx.findActivityForKeepScreenOn() ?: return@DisposableEffect onDispose { }
        val lifecycle = lifecycleOwner.lifecycle
        fun applyKeepScreenOnFlag() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && shouldKeepScreenAwake) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val keepScreenObserver =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> applyKeepScreenOnFlag()
                    Lifecycle.Event.ON_PAUSE ->
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else -> Unit
                }
            }
        lifecycle.addObserver(keepScreenObserver)
        applyKeepScreenOnFlag()
        onDispose {
            lifecycle.removeObserver(keepScreenObserver)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(deeplinkBump) {
        // Squad invite links are resolved in OttoRoot from PendingSquadInviteStore.
        InviteDeepLinkStore.consume()
    }

    LaunchedEffect(inviteAcceptBump) {
        if (inviteAcceptBump > 0L) {
            vm.refreshCoreFeedsAfterExternalInviteAccept()
        }
    }

    LaunchedEffect(pushTapBump) {
        val data = PushNotificationTapStore.consume() ?: return@LaunchedEffect
        val type = data["type"]?.trim() ?: return@LaunchedEffect
        when (type) {
            "event.check_in",
            "event.auto_check_in",
            "circle.event.invited",
            "event.events_today",
            -> selectedTab = OttoMainTab.Events
            "direct.message",
            "direct.message.reaction",
            "circle.chat.reply",
            "circle.chat.new_message",
            "circle.chat.mention",
            "circle.chat.reaction",
            "circle.invite.received",
            "circle.member.added",
            -> selectedTab = OttoMainTab.Squads
            "presence.location_started" -> selectedTab = OttoMainTab.Map
            else -> Unit
        }
        if (type == "circle.invite.received") {
            squadsChromeIdx = SquadChromeSection.Invites.ordinal
        }
        vm.handlePushNotificationRouting(data)
    }

    LaunchedEffect(ui.pendingSquadsInvitesFocusTick) {
        if (ui.pendingSquadsInvitesFocusTick > 0L) {
            selectedTab = OttoMainTab.Squads
            squadsChromeIdx = SquadChromeSection.Invites.ordinal
        }
    }

    LaunchedEffect(ui.pendingSquadChatFocusTick) {
        if (ui.pendingSquadChatFocusTick > 0L) {
            val circleId =
                ui.pendingSquadChatFocusCircleId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@LaunchedEffect
            selectedTab = OttoMainTab.Squads
            val alreadyOnSquad =
                ui.circleDetailUi?.circleId?.let { current ->
                    ottoUserIdsEqual(current, circleId)
                } == true
            if (!alreadyOnSquad) {
                vm.openCircleDetail(circleId)
            }
        }
    }

    LaunchedEffect(ui.pendingMapCoordinateFocus?.nonce) {
        if (ui.pendingMapCoordinateFocus != null) {
            selectedTab = OttoMainTab.Map
        }
    }

    LaunchedEffect(ui.invitePreview) {
        if (ui.invitePreview != null) {
            selectedTab = OttoMainTab.Squads
        }
    }

    LaunchedEffect(selectedTab) {
        val mapTabActive = selectedTab == OttoMainTab.Map
        vm.setMapPresencePollingActive(mapTabActive)
        vm.setMapForegroundLocationActive(mapTabActive)
        // Fullscreen peer profile (map tap or squad grid). Clear when the bottom tab changes only —
        // never tie this to circleDetailUi or chat/grid updates cancel the dialog immediately.
        vm.dismissMapPeerProfileOverlay()
    }

    LaunchedEffect(selectedTab, ui.circleDetailUi) {
        when (selectedTab) {
            OttoMainTab.Events,
            OttoMainTab.Profile -> Unit
            OttoMainTab.Squads ->
                if (ui.circleDetailUi == null) {
                    vm.dismissEventDetail()
                }
            else -> vm.dismissEventDetail()
        }
        if (selectedTab != OttoMainTab.Squads) {
            vm.dismissCircleDetail()
        }
        if (selectedTab != OttoMainTab.Profile) {
            vm.dismissDirectMessagesOverlay()
        }
    }

    val squadsNavChatBadge = remember(ui.unreadChatCountByCircleId, ui.unreadDirectMessageCountByConversationId) {
        ui.totalChatUnreadCount
    }
    val squadsNavInviteDot = remember(ui.pendingInvites) { ui.pendingInvites.isNotEmpty() }

    val squadEventGeocodeWarning = stringResource(R.string.squad_add_event_geocode_warning)

    LaunchedEffect(ui.userToastMessage) {
        val message = ui.userToastMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        android.widget.Toast
            .makeText(ctx, message, android.widget.Toast.LENGTH_SHORT)
            .show()
        vm.dismissUserToast()
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .ottoShellScaffold(),
            containerColor = scheme.background,
            topBar = {
                val eventDetailUsesInlineChrome = ui.eventDetailUi != null
                if (selectedTab != OttoMainTab.Map && !eventDetailUsesInlineChrome) {
                    val dmInlineHeader = ui.directMessages.visible
                    val squadInlineHeader =
                        selectedTab == OttoMainTab.Squads &&
                            ui.circleDetailUi != null &&
                            !dmInlineHeader
                    if (dmInlineHeader) {
                        TopAppBar(
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = scheme.background,
                                    scrolledContainerColor = scheme.background,
                                    navigationIconContentColor = scheme.onSurface,
                                    titleContentColor = scheme.onSurface,
                                    actionIconContentColor = scheme.onSurface,
                                ),
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        if (ui.directMessages.selectedConversationId != null) {
                                            vm.backFromDirectThread()
                                        } else {
                                            vm.dismissDirectMessagesOverlay()
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription =
                                            stringResource(R.string.accessibility_squad_back),
                                    )
                                }
                            },
                            title = {
                                DmShellTopBarTitle(
                                    dm = ui.directMessages,
                                    circles = ui.circles,
                                    myUserId = ui.me?.id,
                                )
                            },
                            actions = {
                                DmShellTopBarActions(
                                    dm = ui.directMessages,
                                    myUserId = ui.me?.id,
                                    meUser = ui.me,
                                    onCompose = vm::showNewDmComposeSheet,
                                    onReportConcern = {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_SENDTO).apply {
                                                    data =
                                                        Uri.parse(
                                                            "mailto:legal@ottomot.to?subject=${Uri.encode("Driftd — Report a concern")}",
                                                        )
                                                },
                                            )
                                        }
                                    },
                                    onBlockDmPeer = { peerId ->
                                        vm.blockPeerUser(peerId) { vm.backFromDirectThread() }
                                    },
                                    onUnblockDmPeer = vm::unblockPeerUser,
                                )
                            },
                        )
                    } else if (squadInlineHeader) {
                        TopAppBar(
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = scheme.background,
                                    scrolledContainerColor = scheme.background,
                                    navigationIconContentColor = scheme.onSurface,
                                    titleContentColor = scheme.onSurface,
                                    actionIconContentColor = scheme.onSurface,
                                ),
                            navigationIcon = {
                                IconButton(onClick = { vm.dismissCircleDetail() }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription =
                                            stringResource(R.string.accessibility_squad_back),
                                    )
                                }
                            },
                            title = {
                                val detail = ui.circleDetailUi
                                SquadCircleDetailShellTopBarTitle(
                                    circle = detail?.circle,
                                    presenceMembersByCircleId = ui.presenceMembersByCircleId,
                                    onOpenSquadSettings =
                                        detail?.takeIf { it.circle != null }?.let { d ->
                                            { vm.requestSquadNotificationSettings(d.circleId) }
                                        },
                                )
                            },
                            actions = {
                                if (ui.circleDetailUi?.circle != null) {
                                    IconButton(
                                        onClick = {
                                            vm.requestSquadNotificationSettings(ui.circleDetailUi!!.circleId)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription =
                                                stringResource(R.string.accessibility_squad_settings),
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        TopAppBar(
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = scheme.background,
                                    scrolledContainerColor = scheme.background,
                                    navigationIconContentColor = scheme.onSurface,
                                    titleContentColor = scheme.onSurface,
                                    actionIconContentColor = scheme.onSurface,
                                ),
                            title = {
                                Text(
                                    text = stringResource(selectedTab.labelRes),
                                    style =
                                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                )
                            },
                            actions = {
                                when (selectedTab) {
                                    OttoMainTab.Squads ->
                                        when (SquadChromeSection.entries.getOrNull(squadsChromeIdx)) {
                                            SquadChromeSection.SquadList ->
                                                IconButton(onClick = { squadsToolbarCreateTicks++ }) {
                                                    Icon(
                                                        Icons.Outlined.Add,
                                                        contentDescription =
                                                            stringResource(R.string.accessibility_create_squad),
                                                    )
                                                }
                                            SquadChromeSection.Dms ->
                                                IconButton(onClick = vm::showNewDmComposeSheet) {
                                                    Icon(
                                                        Icons.Outlined.Edit,
                                                        contentDescription =
                                                            stringResource(R.string.dm_compose_new_message_cd),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            else -> Unit
                                        }
                                    OttoMainTab.Garage ->
                                        IconButton(onClick = { garageToolbarAddTicks++ }) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription =
                                                    stringResource(R.string.accessibility_create_vehicle),
                                            )
                                        }
                                    else ->
                                        IconButton(onClick = { vm.refreshAll() }, enabled = !ui.refreshing) {
                                            Icon(
                                                Icons.Outlined.Refresh,
                                                contentDescription = stringResource(R.string.refresh),
                                            )
                                        }
                                }
                            },
                        )
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .ottoShellBottomBar(),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    NavigationBar(
                        containerColor = scheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        val mapTabPresentation =
                            vm.driveSessionPillPresentation(
                                routeName = ui.activeDriveSession?.routeName,
                                viewerCount = null,
                            )
                        OttoMainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                icon =
                                    {
                                        val cd = stringResource(tab.labelRes)
                                        when {
                                            tab == OttoMainTab.Squads && (squadsNavChatBadge > 0 || squadsNavInviteDot) -> {
                                                BadgedBox(
                                                    badge = {
                                                        if (squadsNavChatBadge > 0) {
                                                            Badge(
                                                                containerColor = Color(0xFF34C759),
                                                            ) {
                                                                Text(
                                                                    squadsNavChatBadge.coerceAtMost(9).toString(),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color.White,
                                                                )
                                                            }
                                                        } else {
                                                            Badge(
                                                                containerColor = Color(0xFF34C759),
                                                            )
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        tab.icon,
                                                        contentDescription = cd,
                                                    )
                                                }
                                            }
                                            tab == OttoMainTab.Map -> {
                                                OttoMapTabNavIcon(
                                                    selected = tab == selectedTab,
                                                    presentation = mapTabPresentation,
                                                    contentDescription = cd,
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    tab.icon,
                                                    contentDescription = cd,
                                                )
                                            }
                                        }
                                    },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF8860D0),
                                        selectedTextColor = Color(0xFF8860D0),
                                        unselectedIconColor = scheme.onSurfaceVariant,
                                        unselectedTextColor = scheme.onSurfaceVariant,
                                        indicatorColor = Color(0xFF8860D0).copy(alpha = 0.14f),
                                    ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (ui.refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                OttoShellTabContent(
                    tab = selectedTab,
                    ui = ui,
                    onOpenEventDetail = vm::openEventDetail,
                    onDismissEventDetail = vm::dismissEventDetail,
                    onSubmitEventRsvp = vm::submitEventRsvp,
                    onSubmitEventCheckIn = vm::submitEventCheckIn,
                    onPrefetchChatAttachmentEvents = vm::prefetchChatAttachmentEvents,
                    postEventShareToChat = vm::postEventShareToChat,
                    postMapMarkerShareToChat = vm::postMapMarkerShareToChat,
                    onApplyEventAttachedSquads = vm::applyEventAttachedSquads,
                    onOpenCircleDetail = vm::openCircleDetail,
                    onDismissCircleDetail = vm::dismissCircleDetail,
                    onSquadChatUnreadPositionChanged = vm::onSquadChatUnreadPositionChanged,
                    onSendCircleChat = vm::sendCircleChatWithAttachment,
                    onSendCircleChatVideo = vm::sendCircleChatVideo,
                    onCancelCircleChatVideoUpload = vm::cancelCircleChatVideoUpload,
                    onPostCircleChatReaction = vm::postCircleChatReaction,
                    onSetCircleChatReplyTo = vm::setCircleChatReplyTo,
                    onClearCircleChatReplyTo = { vm.setCircleChatReplyTo(null) },
                    onBeginCircleChatEdit = vm::beginCircleChatEdit,
                    onCancelCircleChatEdit = vm::cancelCircleChatEdit,
                    onDeleteCircleChatMessage = vm::deleteCircleChatMessage,
                    onFetchOlderCircleChatForQuoteJump = vm::fetchOlderCircleChatForQuoteJump,
                    onLoadNextUpEventDismissals = vm::loadNextUpEventDismissals,
                    onDismissNextUpEventBanner = vm::dismissNextUpEventBanner,
                    onInviteByPhone = vm::inviteByPhoneForCircle,
                    onAddMemberByUserId = vm::addMemberByUserIdForCircle,
                    onCreateInviteLink = vm::createShareLinkForCircle,
                    onCreateSquad = vm::createSquad,
                    onRedeemInspect = vm::redeemInviteInspect,
                    onRedeemAccept = { vm.redeemInviteAccept(it) },
                    onRespondPendingInvite = vm::respondPendingInvite,
                    onDismissSquadsSnack = vm::dismissSquadsSnack,
                    onCreateSquadScopedEvent = vm::createSquadScopedEvent,
                    onShareCreatedSquadEventToChat = vm::shareCreatedSquadEventToChat,
                    onUpdateSquadScopedEvent = vm::updateSquadScopedEvent,
                    onDeleteSquadScopedEvent = vm::deleteSquadScopedEvent,
                    onSquadEventGeocodeWarning = { vm.postSquadsSnack(squadEventGeocodeWarning) },
                    onToggleAutoCheckIn = vm::toggleAutoCheckIn,
                    onToggleShowPublicGoingEventsOnProfile = vm::toggleShowPublicGoingEventsOnProfile,
                    onSetDriveStatsVisibility = vm::setDriveStatsVisibility,
                    onSetSoundEffects = vm::setSoundEffectsEnabled,
                    onMapScopeSelected = vm::setPresenceScope,
                    onRetryPresenceOnly = vm::refreshPresenceCircle,
                    onMapSharingChanged = vm::setMapLocationSharing,
                    onMapSharingPermissionRevoked = vm::setMapLocationSharingDisabledByPermissionRevoke,
                    onMapSharingOptions = vm::setMapSharingOptions,
                    onMapShareSaveDrive = vm::setMapShareSaveDrive,
                    onStartQuickDrive = { save, share, ids -> vm.startQuickDrive(save, share, ids) },
                    onStartRouteDrive = { route, save, share, ids ->
                        vm.startRouteDrive(route, save, share, ids)
                    },
                    onClearMapSelectedRoute = vm::clearMapSelectedRoute,
                    onConsumeRouteDriveFeedback = vm::consumeRouteDriveFeedback,
                    onSetRecordDriveOnStartEnabled = vm::setRecordDriveOnStartEnabled,
                    onSelectSharingCar = vm::selectSharingCar,
                    onRetryPendingDriveSave = vm::retryPendingDriveSave,
                    onDeletePendingDriveArchive = vm::deletePendingDriveArchive,
                    onReloadPendingDriveArchives = vm::reloadPendingDriveArchives,
                    onEnsureLiveDriveSession = vm::ensureLiveDriveSession,
                    onStopDriveSession = vm::stopDriveSession,
                    onStopLiveSharingOnly = vm::stopLiveSharingOnly,
                    onSetDriveSessionSaveEnabled = vm::setDriveSessionSaveEnabled,
                    driveSessionPillPresentation = vm::driveSessionPillPresentation,
                    formatDriveSessionDuration = vm::formatDriveSessionDuration,
                    formatDriveSessionDistance = vm::formatDriveSessionDistance,
                    formatDriveSessionTopSpeed = vm::formatDriveSessionTopSpeed,
                    onAcknowledgeSharingSafetyDisclaimer = vm::acknowledgeSharingSafetyDisclaimer,
                    onExtendMapSharing = vm::extendMapSharingSession,
                    onMapLayerShowSavedPlaces = vm::setMapLayerShowSavedPlaces,
                    onMapLayerShowEvents = vm::setMapLayerShowUpcomingEvents,
                    onMapLayerShowRaceTracks = vm::setMapLayerShowRaceTracks,
                    onMapLayerShowTraffic = vm::setMapLayerShowTraffic,
                    onMapLayerCircleVisible = vm::setMapLayerCircleVisible,
                    onSignOut = vm::signOut,
                    onSquadsPrefetchDirectMessages = vm::prefetchDirectConversationsForSquadsTab,
                    onOpenDirectMessages = vm::openDirectMessagesOverlay,
                    onOpenDirectConversationFullScreen = vm::openDirectThreadFullScreen,
                    squadsChromeIdx = squadsChromeIdx,
                    onSquadsChromeIdxChange = { squadsChromeIdx = it },
                    squadsPullRefreshing = ui.squadsPullRefreshing,
                    onSquadsPullRefresh = vm::refreshSquadsListPullToRefresh,
                    onRefreshSquadGrid = vm::refreshSquadGrid,
                    onOpenCircleMemberProfile = vm::openMapPeerProfileOverlay,
                    onChatProfileMessagePeer = vm::startDirectWithContact,
                    onChatProfileViewPeer = vm::openMapPeerProfileOverlay,
                    onChatProfileOpenSquad = { circleId ->
                        selectedTab = OttoMainTab.Squads
                        vm.openCircleDetail(circleId)
                    },
                    onNavigateToOwnProfileTab = { selectedTab = OttoMainTab.Profile },
                    onPreviewProfileLevelUp = vm::previewProfileLevelUpModal,
                    onSchedulePreviewProfileLevelUpNotification = {
                        vm.schedulePreviewProfileLevelUpNotification()
                    },
                    onKickCircleMember = vm::kickCircleMemberFromPeerProfile,
                    onPatchCircleMemberRole = vm::patchCircleMemberRoleFromPeerProfile,
                    onSaveDisplayName = vm::saveProfileDisplayName,
                    onFetchPersonalInviteLink = vm::fetchPersonalInviteLink,
                    onSaveMapAccent = vm::saveMapAccentKey,
                    onDeleteAccountConfirmed = vm::deleteAccountConfirmed,
                    onMessageContact = vm::startDirectWithContact,
                    onDismissGarageSnack = vm::dismissGarageSnack,
                    onSaveMapPlace = vm::saveMapPlace,
                    onRenameSavedPlace = vm::renameSavedPlace,
                    onDeleteSavedPlace = vm::deleteSavedPlace,
                    onDismissSavedPlacesSnack = vm::dismissSavedPlacesSnack,
                    onMapOpenSquadDetail = { circleId ->
                        selectedTab = OttoMainTab.Squads
                        vm.openCircleDetail(circleId)
                    },
                    onMapMessagePresencePeer = vm::startDirectWithContact,
                    onMapViewPeerProfile = vm::openMapPeerProfileOverlay,
                    onMapNavigateToOwnProfileTab = { selectedTab = OttoMainTab.Profile },
                    onAddGarageCar = vm::addGarageCar,
                    onPatchGarageCar = vm::patchGarageCar,
                    onDeleteGarageCar = vm::deleteGarageCar,
                    onGarageCarPhotoPicked = vm::uploadGarageCarPhoto,
                    onReorderGarageCars = vm::reorderGarageCars,
                    onAvatarPhotoPicked = vm::uploadProfileAvatarPhoto,
                    onProfileNavigateToGarage = { selectedTab = OttoMainTab.Garage },
                    onReplayMarketingOnboarding = {
                        replayMarketingOnboarding = true
                    },
                    squadsToolbarCreateTicks = squadsToolbarCreateTicks,
                    garageToolbarAddTicks = garageToolbarAddTicks,
                    onConsumeSquadsToolbarCreateTicks = { squadsToolbarCreateTicks = 0 },
                    onConsumeGarageToolbarAddTicks = { garageToolbarAddTicks = 0 },
                    onEventsSearchRadiusMiles = vm::setSelectedEventDistance,
                    onEventsRefresh = vm::refreshAll,
                    onConsumePendingMapPresenceFollow = vm::consumePendingMapPresenceFollow,
                    onConsumePendingMapCoordinateFocus = vm::consumePendingMapCoordinateFocus,
                    onOpenEventLocationOnMap = vm::openEventLocationOnMap,
                    onOpenProfileDrive = { drive -> vm.openDriveSummarySheet(drive, isOwner = true) },
                    onOpenProfileRoute = vm::openSavedRouteOnMap,
                    onCreateProfileRoute = vm::openRouteBuilderForProfile,
                    onOpenProfilePlace = vm::openProfilePlaceOnMap,
                    onShareProfileDrive = { drive ->
                        driveChatShareContextFor(drive)?.let(vm::presentDriveShare)
                    },
                    onShareProfileRoute = vm::presentRouteShare,
                    onDeleteProfileDrive = { driveId ->
                        scope.launch { vm.deleteDrive(driveId) }
                    },
                    onDeleteProfileRoute = { routeId ->
                        scope.launch { vm.deleteRoute(routeId) }
                    },
                    onRenameProfileDrive = { drive, title ->
                        vm.renameDrive(drive.id, title).isSuccess
                    },
                    onRenameProfileRoute = { route, name ->
                        vm.renameRoute(route, name).isSuccess
                    },
                    onRenameProfilePlace = vm::renameSavedPlace,
                    onDeleteProfilePlace = vm::deleteSavedPlace,
                    onCreateMapRoute = vm::openRouteBuilderForMap,
                    onOpenMapRoute = vm::openSavedRouteOnMap,
                    onEditMapRoute = vm::openRouteBuilderEdit,
                    onDeleteMapRoute = { routeId -> scope.launch { vm.deleteRoute(routeId) } },
                    onRenameMapRoute = { route, name -> vm.renameRoute(route, name).isSuccess },
                    onShareMapRoute = vm::presentRouteShare,
                    profileDriveDetailContent = { drive, onClose ->
                        Dialog(
                            onDismissRequest = onClose,
                            properties =
                                DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnClickOutside = true,
                                ),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                DriveSummaryScreen(
                                    drive = drive,
                                    isOwner = true,
                                    garageCars = ui.garageCars,
                                    onDismiss = onClose,
                                    onDriveUpdated = vm::onDriveUpdatedInSummary,
                                    onDriveDeleted = onClose,
                                    onPresentShare = vm::presentDriveShare,
                                    onPatchGarageCar = { carId -> vm.patchDriveGarageCar(drive.id, carId) },
                                    onRename = { title ->
                                        vm.renameDrive(drive.id, title).also { result ->
                                            result.onSuccess(vm::onDriveUpdatedInSummary)
                                        }
                                    },
                                    onDelete = { vm.deleteDrive(drive.id) },
                                    onFetchPathSamples = { driveId, circleId ->
                                        vm.fetchDrivePathSamples(driveId, circleId)
                                    },
                                )
                            }
                        }
                    },
                    profileRouteDetailContent = { route, onClose ->
                        val routeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        SavedRouteDetailSheet(
                            sheetState = routeSheetState,
                            route = route,
                            onDismiss = onClose,
                        )
                    },
                    onOpenSharedDrive = { att, circleId ->
                        scope.launch {
                            vm.openSharedDriveSummary(
                                driveId = att.driveId,
                                circleId = circleId,
                                isOwner = false,
                            )
                        }
                    },
                    onOpenSharedRoute = { att ->
                        vm.openSharedRouteFromChat(att)
                        selectedTab = OttoMainTab.Map
                    },
                    onOpenSharedPlace = { att, messageId ->
                        vm.openSharedPlaceFromChat(att, messageId)
                        selectedTab = OttoMainTab.Map
                    },
                    onFetchCircleSharedItemsSummary = vm::fetchCircleSharedItemsSummary,
                    onFetchCircleSharedItems = vm::fetchCircleSharedItems,
                    onOpenSharedGalleryRoute = { item, circleId ->
                        scope.launch {
                            vm.openSharedGalleryRoute(circleId, item)
                        }
                    },
                    onOpenSharedGalleryPlace = { item, circleId ->
                        vm.openSharedGalleryPlace(circleId, item)
                        selectedTab = OttoMainTab.Map
                    },
                    onOpenSharedGalleryLink = { item ->
                        item.linkUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(raw),
                                    ),
                                )
                            }
                        }
                    },
                    onDeleteSharedGalleryMessage = vm::deleteCircleChatMessageAwait,
                    modifier = Modifier.fillMaxSize(),
                )
                if (ui.directMessages.visible) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = scheme.background,
                    ) {
                        DirectMessagesOverlay(
                            dm = ui.directMessages,
                            myUserId = ui.me?.id,
                            meUser = ui.me,
                            circles = ui.circles,
                            contacts = ui.contacts,
                            presenceMembersByCircleId = ui.presenceMembersByCircleId,
                            allUpcomingEventsForChat = ui.events + ui.communityEvents,
                            chatAttachmentHydratedEventsById = ui.chatAttachmentHydratedEventsById,
                            onPrefetchChatAttachmentEvents = vm::prefetchChatAttachmentEvents,
                            eventRsvpSubmittingEventId = ui.eventRsvpSubmittingEventId,
                            onSubmitEventRsvp = vm::submitEventRsvp,
                            onOpenEventDetail = vm::openEventDetail,
                            onClose = vm::dismissDirectMessagesOverlay,
                            onSelectConversation = vm::selectDirectConversation,
                            onBackThread = vm::backFromDirectThread,
                            onSend = vm::sendDirectMessageWithAttachment,
                            onSendVideo = vm::sendDirectChatVideo,
                            onCancelDirectChatVideoUpload = vm::cancelDirectChatVideoUpload,
                            onSetThreadReplyTo = vm::setDirectThreadReplyTo,
                            onClearThreadReplyTo = { vm.setDirectThreadReplyTo(null) },
                            onPostDmReaction = vm::postDirectMessageReaction,
                            onBeginDirectThreadEdit = vm::beginDirectThreadEdit,
                            onCancelDirectThreadEdit = vm::cancelDirectThreadEdit,
                            onDeleteDirectThreadMessage = vm::deleteDirectThreadMessage,
                            onFetchOlderDirectChatForQuoteJump = vm::fetchOlderDirectChatForQuoteJump,
                            onChatProfileMessagePeer = vm::startDirectWithContact,
                            onChatProfileViewPeer = { userId ->
                                vm.dismissDirectMessagesOverlay()
                                vm.openMapPeerProfileOverlay(userId)
                            },
                            onChatProfileOpenSquad = { circleId ->
                                vm.dismissDirectMessagesOverlay()
                                selectedTab = OttoMainTab.Squads
                                vm.openCircleDetail(circleId)
                            },
                            onNavigateToOwnProfileTab = {
                                vm.dismissDirectMessagesOverlay()
                                selectedTab = OttoMainTab.Profile
                            },
                            onReportConcern = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_SENDTO).apply {
                                            data =
                                                Uri.parse(
                                                    "mailto:legal@ottomot.to?subject=${Uri.encode("Driftd — Report a concern")}",
                                                )
                                        },
                                    )
                                }
                            },
                            onBlockDmPeer = { peerId ->
                                vm.blockPeerUser(peerId) { vm.backFromDirectThread() }
                            },
                            onUnblockDmPeer = vm::unblockPeerUser,
                            onOpenSharedPlace = { att, messageId ->
                                vm.openSharedPlaceFromChat(att, messageId)
                                vm.dismissDirectMessagesOverlay()
                                selectedTab = OttoMainTab.Map
                            },
                        )
                    }
                }
                }
            }
        }

        ui.bannerError?.takeIf { it.isNotBlank() }?.let { err ->
            val suppressForInlineSquadsError =
                ui.squadsLoadFailed &&
                    selectedTab == OttoMainTab.Squads &&
                    ui.circles.isEmpty()
            if (!suppressForInlineSquadsError) {
            AlertDialog(
                onDismissRequest = vm::dismissBannerError,
                title = { Text(stringResource(R.string.app_error_dialog_title)) },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { vm.refreshAll() }) {
                        Text(stringResource(R.string.retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = vm::dismissBannerError) {
                        Text(stringResource(R.string.dismiss_snack))
                    }
                },
            )
            }
        }

        val shellCtx = LocalContext.current
        ui.squadNotificationSettingsCircleId?.let { nid ->
            val squad = ui.circles.find { ottoUserIdsEqual(it.id, nid) }
            SquadNotificationSettingsDialog(
                circleId = nid,
                squad = squad,
                contacts = ui.contacts,
                meUser = ui.me,
                myUserId = ui.me?.id,
                presenceMembersByCircleId = ui.presenceMembersByCircleId,
                allCircles = ui.circles,
                squadName =
                    squad?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.squad_chat_heading),
                memberSubtitle = stringResource(R.string.squads_members_format, squad?.members?.size ?: 0),
                sessionRepository = shellCtx.appContainer().sessionRepository,
                inviteUi = ui.squadSettingsInvite,
                squadSettingsToast = ui.squadSettingsToast,
                onDismissSquadSettingsToast = vm::dismissSquadSettingsToast,
                onDismiss = vm::dismissSquadNotificationSettingsDialog,
                onRenameSquad = { cid, name, done -> vm.renameSquadFromSettings(cid, name, done) },
                onLeaveSquad = vm::submitSquadLeaveFromSettings,
                onPrefetchSquadInvite = vm::prefetchSquadShareInviteLink,
                inviteViewModel = vm,
                onSquadInviteSearchChanged = vm::onSquadSettingsInviteSearchChanged,
                onInviteSquadLookupUser = { cid, uid, phone ->
                    vm.inviteSquadMemberByUserFromSettings(cid, uid, phone)
                },
                onAddSquadMemberFromSettings = vm::addSquadMemberFromSettings,
                onMemberProfileMessage = vm::startDirectWithContact,
                onMemberProfileViewFullProfile = vm::openMapPeerProfileOverlay,
                onMemberProfileOpenSquad = { circleId ->
                    vm.dismissSquadNotificationSettingsDialog()
                    selectedTab = OttoMainTab.Squads
                    vm.openCircleDetail(circleId)
                },
                onNavigateToOwnProfileTab = {
                    vm.dismissSquadNotificationSettingsDialog()
                    selectedTab = OttoMainTab.Profile
                },
                onKickCircleMember = vm::kickCircleMemberFromPeerProfile,
                onPatchCircleMemberRole = vm::patchCircleMemberRoleFromPeerProfile,
            )
        }

        if (ui.showNewDmCompose) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = scheme.background,
            ) {
                NewDmComposeFullscreen(
                        circles = ui.circles,
                        contacts = ui.contacts,
                        myUserId = ui.me?.id,
                        meUser = ui.me,
                        directConversations = ui.directMessages.conversations,
                        onDismiss = { vm.dismissNewDmComposeSheet() },
                        onSubmitRecipient = { rid -> vm.submitNewDmCompose(rid) },
                        onOpenExistingConversation = { conv ->
                            vm.dismissNewDmComposeSheet()
                            vm.openDirectThreadFullScreen(conv)
                        },
                    )
            }
        }

        ui.mapPeerProfileOverlay?.let { overlay ->
            Dialog(
                onDismissRequest = { vm.dismissMapPeerProfileOverlay() },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnClickOutside = true,
                    ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MapPeerProfileFullscreenOverlay(
                        overlay = overlay,
                        contacts = ui.contacts,
                        circles = ui.circles,
                        myUserId = ui.me?.id,
                        meUser = ui.me,
                        onDismiss = vm::dismissMapPeerProfileOverlay,
                        onChatPeer = { peerId ->
                            vm.dismissMapPeerProfileOverlay()
                            vm.startDirectWithContact(peerId)
                        },
                        onOpenEventDetail = { eventId ->
                            vm.dismissMapPeerProfileOverlay()
                            vm.openEventDetail(eventId)
                        },
                        onReportConcern = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_SENDTO).apply {
                                        data =
                                            Uri.parse(
                                                "mailto:legal@ottomot.to?subject=${Uri.encode("Driftd — Report a concern")}",
                                            )
                                    },
                                )
                            }
                        },
                        onBlockPeer = { peerId -> vm.blockPeerUser(peerId) { vm.dismissMapPeerProfileOverlay() } },
                        onUnblockPeer = vm::unblockPeerUser,
                    )
                }
            }
        }

        ui.driveSummarySheet?.let { sheet ->
            Dialog(
                onDismissRequest = vm::dismissDriveSummarySheet,
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnClickOutside = true,
                    ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DriveSummaryScreen(
                        drive = sheet.drive,
                        isOwner = sheet.isOwner,
                        garageCars = ui.garageCars,
                        lockedShareCircleId = sheet.lockedShareCircleId,
                        onDismiss = vm::dismissDriveSummarySheet,
                        onDriveUpdated = vm::onDriveUpdatedInSummary,
                        onDriveDeleted = vm::dismissDriveSummarySheet,
                        onPresentShare = vm::presentDriveShare,
                        onPatchGarageCar = { carId -> vm.patchDriveGarageCar(sheet.drive.id, carId) },
                        onRename = { title ->
                            vm.renameDrive(sheet.drive.id, title).also { result ->
                                result.onSuccess(vm::onDriveUpdatedInSummary)
                            }
                        },
                        onDelete = { vm.deleteDrive(sheet.drive.id) },
                        onFetchPathSamples = { driveId, circleId ->
                            vm.fetchDrivePathSamples(driveId, circleId)
                        },
                    )
                }
            }
        }

        ui.activeProfileLevelUp?.let { levelUp ->
            ProfileLevelUpModal(
                levelUp = levelUp,
                onContinue = vm::dismissProfileLevelUp,
            )
        }

        ui.driveCompleteSummary?.let { summary ->
            DriveCompleteSheet(
                summary = summary,
                onViewSummary = {
                    scope.launch {
                        if (!vm.viewDriveSummaryFromComplete()) {
                            android.widget.Toast
                                .makeText(
                                    ctx,
                                    ctx.getString(R.string.drive_complete_summary_error),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                },
                onDone = vm::dismissDriveComplete,
            )
        }

        ui.driveShareContext?.let { shareContext ->
            val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            DriveShareSquadActionsSheet(
                sheetState = shareSheetState,
                context = shareContext,
                onDismiss = vm::dismissDriveShare,
                onShareToChat = {
                    driveChatDestinationContext = shareContext
                    vm.dismissDriveShare()
                },
            )
        }

        driveChatDestinationContext?.let { chatContext ->
            val chatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            DriveChatDestinationSheet(
                sheetState = chatSheetState,
                context = chatContext,
                circles = ui.circles,
                onDismiss = { driveChatDestinationContext = null },
                onShareToChat = { circleId, context ->
                    vm.shareDriveToSquadChat(circleId, context).also { result ->
                        if (result.isSuccess) driveChatDestinationContext = null
                    }
                },
            )
        }

        ui.routeShareRoute?.let { route ->
            val routeShareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            RouteShareSquadActionsSheet(
                sheetState = routeShareSheetState,
                route = route,
                onDismiss = vm::dismissRouteShare,
                onShareToChat = {
                    routeChatDestinationRoute = route
                    vm.dismissRouteShare()
                },
            )
        }

        routeChatDestinationRoute?.let { route ->
            val routeChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            RouteChatDestinationSheet(
                sheetState = routeChatSheetState,
                route = route,
                circles = ui.circles,
                onDismiss = { routeChatDestinationRoute = null },
                onShareToChat = { circleId, sharedRoute ->
                    vm.shareRouteToSquadChat(circleId, sharedRoute).also { result ->
                        if (result.isSuccess) routeChatDestinationRoute = null
                    }
                },
            )
        }

        ui.savedRouteDetail?.let { routeSheet ->
            val routeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            SavedRouteDetailSheet(
                sheetState = routeSheetState,
                route = routeSheet.route,
                onDismiss = vm::dismissSavedRouteDetail,
            )
        }

        ui.routeBuilderEntry?.let { entry ->
            val routeBuilderKey =
                when (entry) {
                    is RouteBuilderEntry.New -> "route_builder_new"
                    is RouteBuilderEntry.Edit -> "route_builder_edit_${entry.route.id}"
                }
            val routeBuilderVm: RouteBuilderViewModel =
                viewModel(
                    key = routeBuilderKey,
                    factory = RouteBuilderViewModel.factory(container.dataRepository, ctx),
                )
            val locationGranted =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            LaunchedEffect(entry) {
                when (entry) {
                    is RouteBuilderEntry.New -> {
                        routeBuilderVm.openNewRoute(entry.centerLat, entry.centerLng)
                        if (locationGranted) {
                            vm.requestLocationSyncForRouteBuilder()
                        }
                    }
                    is RouteBuilderEntry.Edit -> routeBuilderVm.openEditRoute(entry.route)
                }
            }
            val shellActivity = ctx.findActivityForKeepScreenOn()
            val locationNotDetermined =
                !locationGranted &&
                    (
                        shellActivity == null ||
                            !ActivityCompat.shouldShowRequestPermissionRationale(
                                shellActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                    )
            val userFix = ui.deviceLocationFix?.takeIf { it.isFreshForRouteBuilderCenter() }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(200f)
                    .background(Color.Black),
            ) {
                RouteBuilderScreen(
                    viewModel = routeBuilderVm,
                    locationGranted = locationGranted,
                    locationNotDetermined = locationNotDetermined,
                    userLat = userFix?.latitude,
                    userLng = userFix?.longitude,
                    onDismiss = vm::dismissRouteBuilder,
                    onRouteSaved = vm::onRouteBuilderSaved,
                    onRequestLocationSync = vm::requestLocationSyncForRouteBuilder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showMarketingOnboarding) {
            MarketingOnboardingScreen(
                onFinished = {
                    val wasReplay = replayMarketingOnboarding
                    replayMarketingOnboarding = false
                    if (!wasReplay) {
                        scope.launch {
                            container.sessionRepository.setMarketingOnboardingCompleted(true)
                        }
                    }
                    // First install: core feeds often load behind the intro carousel; refresh when it dismisses.
                    vm.refreshAll()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun Context.findActivityForKeepScreenOn(): Activity? {
    var context = this
    while (true) {
        if (context is Activity) return context
        if (context is ContextWrapper) {
            context = context.baseContext
            continue
        }
        return null
    }
}
