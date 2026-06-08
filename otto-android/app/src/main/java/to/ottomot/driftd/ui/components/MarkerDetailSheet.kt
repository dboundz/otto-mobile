package to.ottomot.driftd.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import to.ottomot.driftd.EventShareFlowSheets
import to.ottomot.driftd.MapMarkerShareFlowSheets
import to.ottomot.driftd.MapMarkerSharePayload
import to.ottomot.driftd.mapMarkerSharePayloadForRaceTrack
import to.ottomot.driftd.mapMarkerSharePayloadForSavedPlace
import to.ottomot.driftd.OttoShellUiState
import to.ottomot.driftd.R
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.EventAttachedSquadDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.SavedPlaceDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.race.RaceTrackRecord
import to.ottomot.driftd.core.race.coordinateOrNull
import to.ottomot.driftd.core.event.parseEventInstant
import to.ottomot.driftd.shortAddress
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent

object MarkerDetailSheetHeight {
    private const val CAP_DP = 720
    private const val FLOOR_DP = 280

    fun estimatedHeightDp(model: MarkerDetailSheetModel): Dp {
        var height = 8 + 52
        height += 96
        height += 16

        if (model.infoItems.isNotEmpty()) {
            val columns = if (model.infoItems.size <= 1) 1 else 2
            val rows = ceil(model.infoItems.size.toDouble() / columns.toDouble()).toInt()
            height += rows * 72 + 28
            height += 16
        }

        if (model.actions.isNotEmpty()) {
            val actionRows = ceil(model.actions.size.toDouble() / 2.0).toInt()
            height += actionRows * 72
            height += 16
        }

        if (model.upcomingEvents.isNotEmpty()) {
            height += 28
            val visibleCount = minOf(model.upcomingEvents.size, 2)
            height += visibleCount * 64
            height += maxOf(0, visibleCount - 1) * 8
            height += 16
        }

        if (model.showAddToPlacesFooter) {
            height += 52
            height += 16
        }

        height += 24
        return maxOf(FLOOR_DP, minOf(height, CAP_DP)).dp
    }
}

object MarkerDetailColors {
    val sheetBackground = Color(0xFF060609)
    val cardFill = Color.White.copy(alpha = 0.06f)
    val cardStroke = Color.White.copy(alpha = 0.10f)
    val secondaryActionFill = Color.White.copy(alpha = 0.055f)
    val labelSecondary = Color.White.copy(alpha = 0.45f)
    val textSecondary = Color.White.copy(alpha = 0.78f)
    val eventPink = Color(0xFFF63887)
    val raceTrackOrange = Color(0xFFFFA658)
    val savedPlaceTeal = Color(0xFF00A5AA)
}

enum class MarkerDetailType {
    SavedPlace,
    Event,
    RaceTrack,
}

enum class MarkerDetailActionStyle {
    Primary,
    Secondary,
    Destructive,
}

data class MarkerInfoItem(
    val icon: @Composable () -> Unit,
    val label: String,
    val value: String,
)

data class MarkerDetailAction(
    val id: String,
    val title: String,
    val icon: @Composable () -> Unit,
    val style: MarkerDetailActionStyle,
    val enabled: Boolean = true,
)

data class UpcomingEventItem(
    val id: String,
    val title: String,
    val monthAbbrev: String,
    val dayText: String,
    val timeLine: String,
    val goingCount: Int,
)

data class MarkerDetailSheetModel(
    val markerType: MarkerDetailType,
    val accentColor: Color,
    val headerTitle: String,
    val icon: @Composable () -> Unit,
    val title: String,
    val subtitle: String?,
    val categoryLabel: String?,
    val locationLine: String?,
    val statusChip: String?,
    val infoItems: List<MarkerInfoItem>,
    val actions: List<MarkerDetailAction>,
    val upcomingEvents: List<UpcomingEventItem> = emptyList(),
    val mapMarkerSharePayload: MapMarkerSharePayload? = null,
    val showEventShareFooter: Boolean = false,
    val showAddToPlacesFooter: Boolean = false,
)

sealed interface MapMarkerDetailContent {
    data class SavedPlace(val place: SavedPlaceDto) : MapMarkerDetailContent

    data class Event(
        val primary: EventDto,
        val siblings: List<EventDto> = emptyList(),
    ) : MapMarkerDetailContent

    data class RaceTrack(val track: RaceTrackRecord) : MapMarkerDetailContent
}

@Composable
fun MapMarkerDetailSheet(
    content: MapMarkerDetailContent,
    distanceFromMe: String?,
    rsvpSubmittingEventId: String?,
    refreshedEvents: List<EventDto>,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    dmConversations: List<DirectConversationDto>,
    meUser: UserDto?,
    onDone: () -> Unit,
    onDismissSheet: () -> Unit,
    onEditSavedPlace: (SavedPlaceDto) -> Unit,
    onRemoveSavedPlace: (SavedPlaceDto) -> Unit,
    onSaveMapPlace: (name: String, latitude: Double, longitude: Double, addressSummary: String?) -> Unit,
    onOpenEventDetail: (eventId: String) -> Unit,
    onSubmitEventRsvp: (eventId: String, status: String) -> Unit,
    onPrefetchDirectMessages: () -> Unit,
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    postMapMarkerShareToChat: (MapMarkerSharePayload, List<String>, List<String>, String) -> Unit,
    ownedSavedPlaceIds: Set<String> = emptySet(),
    pendingSquadChatFocusTick: Long = 0L,
    onEventAssociationsSaved: (eventId: String, List<EventAttachedSquadDto>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rsvpActionLabel = stringResource(R.string.marker_detail_action_rsvp)
    val goingActionLabel = stringResource(R.string.marker_detail_action_going)

    var shareSquadActionsOpen by remember(content) { mutableStateOf(false) }
    var shareToChatSheetOpen by remember(content) { mutableStateOf(false) }

    LaunchedEffect(pendingSquadChatFocusTick) {
        if (pendingSquadChatFocusTick > 0L) {
            shareSquadActionsOpen = false
            shareToChatSheetOpen = false
            onDismissSheet()
        }
    }

    var primaryEvent by remember(content) {
        mutableStateOf((content as? MapMarkerDetailContent.Event)?.primary)
    }
    var siblingEvents by remember(content) {
        mutableStateOf((content as? MapMarkerDetailContent.Event)?.siblings.orEmpty())
    }

    androidx.compose.runtime.LaunchedEffect(refreshedEvents, primaryEvent?.id) {
        val currentId = primaryEvent?.id ?: return@LaunchedEffect
        refreshedEvents.firstOrNull { it.id == currentId }?.let { updated ->
            primaryEvent = updated
        }
        if (siblingEvents.isNotEmpty()) {
            siblingEvents =
                siblingEvents.map { sibling ->
                    refreshedEvents.firstOrNull { it.id == sibling.id } ?: sibling
                }
        }
    }

    val model =
        remember(content, primaryEvent, siblingEvents, distanceFromMe, rsvpSubmittingEventId, rsvpActionLabel, goingActionLabel) {
            when (content) {
                is MapMarkerDetailContent.SavedPlace ->
                    buildSavedPlaceModel(
                        place = content.place,
                        distance = distanceFromMe,
                        isOwned = ownedSavedPlaceIds.contains(content.place.id),
                    )
                is MapMarkerDetailContent.Event -> {
                    val event = primaryEvent ?: content.primary
                    val isGoing = event.currentUserRsvp == OttoShellUiState.RsvpGoing
                    buildEventModel(
                        event = event,
                        siblings = if (primaryEvent == null) content.siblings else siblingEvents,
                        distance = distanceFromMe,
                        rsvpTitle = if (isGoing) goingActionLabel else rsvpActionLabel,
                        rsvpBusy = rsvpSubmittingEventId == event.id,
                    )
                }
                is MapMarkerDetailContent.RaceTrack ->
                    buildRaceTrackModel(content.track, distanceFromMe)
            }
        }

    val handleAction: (MarkerDetailAction) -> Unit = { action ->
        if (action.id == "share") {
            shareSquadActionsOpen = true
        } else when (content) {
            is MapMarkerDetailContent.SavedPlace -> {
                when (action.id) {
                    "directions" -> {
                        onDismissSheet()
                        openMapMarkerDirections(
                            ctx,
                            content.place.latitude,
                            content.place.longitude,
                            content.place.name,
                        )
                    }
                    "edit" -> onEditSavedPlace(content.place)
                    "remove" -> onRemoveSavedPlace(content.place)
                }
            }
            is MapMarkerDetailContent.Event -> {
                val event = primaryEvent ?: content.primary
                val coords = event.location?.coordinates
                when (action.id) {
                    "directions" ->
                        if (coords != null && coords.size >= 2) {
                            onDismissSheet()
                            openMapMarkerDirections(ctx, coords[1], coords[0], shortAddress(event))
                        }
                    "open_event" -> {
                        onDismissSheet()
                        onOpenEventDetail(event.id)
                    }
                    "rsvp" -> {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        val next =
                            if (event.currentUserRsvp == OttoShellUiState.RsvpGoing) {
                                "not_going"
                            } else {
                                OttoShellUiState.RsvpGoing
                            }
                        onSubmitEventRsvp(event.id, next)
                    }
                }
            }
            is MapMarkerDetailContent.RaceTrack -> {
                val coords = content.track.coordinateOrNull()
                when (action.id) {
                    "directions" ->
                        coords?.let { (lat, lng) ->
                            onDismissSheet()
                            openMapMarkerDirections(ctx, lat, lng, content.track.name)
                        }
                    "add_to_places" ->
                        coords?.let { (lat, lng) ->
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSaveMapPlace(content.track.name, lat, lng, content.track.locationLine)
                        }
                }
            }
        }
    }

    val handleAddToPlacesFooter: () -> Unit = {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        when (content) {
            is MapMarkerDetailContent.Event -> {
                val event = primaryEvent ?: content.primary
                val coords = event.location?.coordinates
                if (coords != null && coords.size >= 2) {
                    val label = shortAddress(event).takeIf { it.isNotBlank() } ?: event.name
                    onSaveMapPlace(event.name, coords[1], coords[0], label)
                }
            }
            is MapMarkerDetailContent.RaceTrack ->
                content.track.coordinateOrNull()?.let { (lat, lng) ->
                    onSaveMapPlace(content.track.name, lat, lng, content.track.locationLine)
                }
            else -> Unit
        }
    }

    val handleViewAllVenueEvents: () -> Unit = {
        val event = primaryEvent ?: (content as? MapMarkerDetailContent.Event)?.primary
        if (event != null) {
            onDismissSheet()
            onOpenEventDetail(event.id)
        }
    }

    val sheetHeight = MarkerDetailSheetHeight.estimatedHeightDp(model)

    Column(
        modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .background(MarkerDetailColors.sheetBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .ottoBottomSheetContent(),
    ) {
        MarkerDetailSheetHeader(title = model.headerTitle, onDone = onDone)
        Spacer(Modifier.height(16.dp))
        MarkerIdentityHeader(
            accentColor = model.accentColor,
            icon = model.icon,
            title = model.title,
            subtitle = model.subtitle,
            categoryLabel = model.categoryLabel,
            locationLine = model.locationLine,
            statusChip = model.statusChip,
        )
        if (model.infoItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MarkerInfoCard(accentColor = model.accentColor, items = model.infoItems)
        }
        if (model.actions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MarkerActionGrid(
                accentColor = model.accentColor,
                actions = model.actions,
                onAction = handleAction,
            )
        }
        if (model.upcomingEvents.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MarkerSectionHeader(
                title = stringResource(R.string.marker_detail_more_upcoming),
                trailingTitle = stringResource(R.string.marker_detail_view_all_venue_events),
                accentColor = model.accentColor,
                onTrailingClick = handleViewAllVenueEvents,
            )
            Spacer(Modifier.height(10.dp))
            model.upcomingEvents.take(2).forEach { item ->
                UpcomingEventRow(
                    item = item,
                    accentColor = model.accentColor,
                    onClick = {
                        val tapped =
                            siblingEvents.firstOrNull { it.id == item.id }
                                ?: (content as? MapMarkerDetailContent.Event)?.siblings?.firstOrNull { it.id == item.id }
                        if (tapped != null && primaryEvent != null) {
                            val current = primaryEvent!!
                            siblingEvents = (siblingEvents.filterNot { it.id == tapped.id } + current)
                                .sortedBy { parseEventInstant(it.startsAt.orEmpty()) ?: java.time.Instant.MAX }
                            primaryEvent = tapped
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (model.showAddToPlacesFooter) {
            Spacer(Modifier.height(8.dp))
            MarkerFooterButton(
                title = stringResource(R.string.marker_detail_action_add_to_places),
                icon = {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = model.accentColor)
                },
                accentOutline = true,
                accentColor = model.accentColor,
                onClick = handleAddToPlacesFooter,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (content is MapMarkerDetailContent.Event) {
        val shareEvent = primaryEvent ?: content.primary
        EventShareFlowSheets(
            event = shareEvent,
            shareSquadActionsOpen = shareSquadActionsOpen,
            onShareSquadActionsOpenChange = { shareSquadActionsOpen = it },
            shareToChatSheetOpen = shareToChatSheetOpen,
            onShareToChatSheetOpenChange = { shareToChatSheetOpen = it },
            circles = circles,
            contacts = contacts,
            dmConversations = dmConversations,
            meUser = meUser,
            lockedCircleId = null,
            actionBusy = rsvpSubmittingEventId == shareEvent.id,
            onPrefetchDirectMessages = onPrefetchDirectMessages,
            postEventShareToChat = postEventShareToChat,
            pendingSquadChatFocusTick = pendingSquadChatFocusTick,
            onEventAssociationsSaved = { squads -> onEventAssociationsSaved(shareEvent.id, squads) },
        )
    } else {
        MapMarkerShareFlowSheets(
            payload = model.mapMarkerSharePayload,
            shareSquadActionsOpen = shareSquadActionsOpen,
            onShareSquadActionsOpenChange = { shareSquadActionsOpen = it },
            shareToChatSheetOpen = shareToChatSheetOpen,
            onShareToChatSheetOpenChange = { shareToChatSheetOpen = it },
            circles = circles,
            contacts = contacts,
            dmConversations = dmConversations,
            meUser = meUser,
            onPrefetchDirectMessages = onPrefetchDirectMessages,
            postMapMarkerShareToChat = postMapMarkerShareToChat,
            pendingSquadChatFocusTick = pendingSquadChatFocusTick,
        )
    }
}

@Composable
private fun MarkerDetailSheetHeader(
    title: String,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            Text(
                title,
                modifier = Modifier.align(Alignment.Center),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.72f),
            )
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(
                    stringResource(R.string.marker_detail_done),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MarkerIdentityHeader(
    accentColor: Color,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    categoryLabel: String?,
    locationLine: String?,
    statusChip: String?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MarkerDetailColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            categoryLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            locationLine?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MarkerDetailColors.labelSecondary, modifier = Modifier.size(12.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = MarkerDetailColors.labelSecondary, maxLines = 2)
                }
            }
        }
        statusChip?.let {
            Text(
                it,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MarkerDetailColors.cardFill)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun MarkerInfoCard(
    accentColor: Color,
    items: List<MarkerInfoItem>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MarkerDetailColors.cardFill)
            .border(1.dp, MarkerDetailColors.cardStroke, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.size(16.dp)) { item.icon() }
                        Spacer(Modifier.height(6.dp))
                        Text(item.label.uppercase(Locale.getDefault()), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MarkerDetailColors.labelSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(item.value, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkerActionGrid(
    accentColor: Color,
    actions: List<MarkerDetailAction>,
    onAction: (MarkerDetailAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowActions.forEach { action ->
                    val bg =
                        when {
                            !action.enabled -> MarkerDetailColors.secondaryActionFill.copy(alpha = 0.5f)
                            action.style == MarkerDetailActionStyle.Primary -> accentColor
                            else -> MarkerDetailColors.secondaryActionFill
                        }
                    val fg =
                        when {
                            !action.enabled -> Color.White.copy(alpha = 0.35f)
                            action.style == MarkerDetailActionStyle.Primary -> Color.White
                            action.style == MarkerDetailActionStyle.Destructive -> Color(0xFFFF5252)
                            else -> Color.White.copy(alpha = 0.92f)
                        }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(1.dp, MarkerDetailColors.cardStroke, RoundedCornerShape(12.dp))
                            .clickable(enabled = action.enabled) { onAction(action) }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(22.dp)) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides fg,
                            ) {
                                action.icon()
                            }
                        }
                        Text(action.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowActions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MarkerSectionHeader(
    title: String,
    trailingTitle: String,
    accentColor: Color,
    onTrailingClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onTrailingClick) {
            Text(trailingTitle, color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UpcomingEventRow(
    item: UpcomingEventItem,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MarkerDetailColors.cardFill)
            .border(1.dp, MarkerDetailColors.cardStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.monthAbbrev, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f))
            Text(item.dayText, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.timeLine, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.62f))
        }
        Text(
            stringResource(R.string.marker_detail_going_count_format, item.goingCount),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f))
    }
}

@Composable
private fun MarkerFooterButton(
    title: String,
    icon: @Composable () -> Unit,
    accentOutline: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MarkerDetailColors.cardFill)
            .border(
                1.dp,
                if (accentOutline) accentColor.copy(alpha = 0.45f) else MarkerDetailColors.cardStroke,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(title, color = if (accentOutline) accentColor else Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.SemiBold)
    }
}

private fun buildSavedPlaceModel(place: SavedPlaceDto, distance: String?, isOwned: Boolean): MarkerDetailSheetModel {
    val info = buildList {
        distance?.let { add(infoDistance(it)) }
        add(infoCoordinates(place.latitude, place.longitude))
        if (isOwned) {
            add(
                MarkerInfoItem(
                    icon = { Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = MarkerDetailColors.savedPlaceTeal, modifier = Modifier.size(16.dp)) },
                    label = "Saved in",
                    value = "My Places",
                ),
            )
        }
    }
    val actions = buildList {
        add(
            MarkerDetailAction(
                "share",
                "Share",
                { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(22.dp)) },
                MarkerDetailActionStyle.Secondary,
            ),
        )
        add(
            MarkerDetailAction(
                "directions",
                "Directions",
                { Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(22.dp)) },
                MarkerDetailActionStyle.Primary,
            ),
        )
        if (isOwned) {
            add(MarkerDetailAction("edit", "Edit", { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Secondary))
            add(MarkerDetailAction("remove", "Remove", { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Destructive))
        }
    }
    return MarkerDetailSheetModel(
        markerType = MarkerDetailType.SavedPlace,
        accentColor = MarkerDetailColors.savedPlaceTeal,
        headerTitle = "Saved Place",
        icon = {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MarkerDetailColors.savedPlaceTeal, modifier = Modifier.size(28.dp))
        },
        title = place.name,
        subtitle = place.addressSummary,
        categoryLabel = place.placeKind?.replace('_', ' ')?.replaceFirstChar { it.titlecase() },
        locationLine = null,
        statusChip = null,
        infoItems = info,
        actions = actions,
        mapMarkerSharePayload =
            mapMarkerSharePayloadForSavedPlace(
                id = place.id,
                name = place.name,
                addressSummary = place.addressSummary,
                latitude = place.latitude,
                longitude = place.longitude,
            ),
    )
}

private fun buildRaceTrackModel(track: RaceTrackRecord, distance: String?): MarkerDetailSheetModel {
    val coords = track.coordinateOrNull()
    val info = buildList {
        distance?.let { add(infoDistance(it)) }
        coords?.let { (lat, lng) -> add(infoCoordinates(lat, lng)) }
        if (track.formattedTypes.isNotBlank()) {
            add(
                MarkerInfoItem(
                    icon = { Icon(Icons.Outlined.Navigation, contentDescription = null, tint = MarkerDetailColors.raceTrackOrange, modifier = Modifier.size(16.dp)) },
                    label = "Surface",
                    value = track.formattedTypes,
                ),
            )
        }
    }
    return MarkerDetailSheetModel(
        markerType = MarkerDetailType.RaceTrack,
        accentColor = MarkerDetailColors.raceTrackOrange,
        headerTitle = "Race Track",
        icon = {
            Icon(
                painter = painterResource(R.drawable.map_point_track),
                contentDescription = null,
                modifier = Modifier.size(width = 24.dp, height = 36.dp),
                tint = Color.Unspecified,
            )
        },
        title = track.name,
        subtitle = track.locationLine,
        categoryLabel = track.formattedTypes.takeIf { it.isNotBlank() },
        locationLine = null,
        statusChip = null,
        infoItems = info,
        actions =
            listOf(
                MarkerDetailAction("share", "Share", { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Secondary),
                MarkerDetailAction("directions", "Directions", { Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Primary, coords != null),
                MarkerDetailAction("add_to_places", "Add to My Places", { Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Secondary, coords != null),
            ),
        mapMarkerSharePayload =
            coords?.let { (lat, lng) ->
                mapMarkerSharePayloadForRaceTrack(
                    name = track.name,
                    locationLine = track.locationLine,
                    latitude = lat,
                    longitude = lng,
                )
            },
    )
}

private fun buildEventModel(
    event: EventDto,
    siblings: List<EventDto>,
    distance: String?,
    rsvpTitle: String,
    rsvpBusy: Boolean = false,
): MarkerDetailSheetModel {
    val coords = eventGeoCoordinate(event)
    val venue = shortAddress(event)
    val going = event.rsvpCounts?.going ?: 0
    val isGoing = event.currentUserRsvp == OttoShellUiState.RsvpGoing
    val info = buildList {
        distance?.let { add(infoDistance(it)) }
        coords?.let { (lat, lng) -> add(infoCoordinates(lat, lng)) }
        if (venue.isNotBlank()) {
            add(
                MarkerInfoItem(
                    icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MarkerDetailColors.eventPink, modifier = Modifier.size(16.dp)) },
                    label = "Venue",
                    value = venue,
                ),
            )
        }
        add(
            MarkerInfoItem(
                icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MarkerDetailColors.eventPink, modifier = Modifier.size(16.dp)) },
                label = "Date & Time",
                value = formatEventSchedule(event),
            ),
        )
        if (siblings.isNotEmpty()) {
            add(
                MarkerInfoItem(
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MarkerDetailColors.eventPink, modifier = Modifier.size(16.dp)) },
                    label = "Next events",
                    value = "${siblings.size + 1} total",
                ),
            )
        }
    }
    return MarkerDetailSheetModel(
        markerType = MarkerDetailType.Event,
        accentColor = MarkerDetailColors.eventPink,
        headerTitle = "Event",
        icon = {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MarkerDetailColors.eventPink, modifier = Modifier.size(28.dp))
        },
        title = event.name,
        subtitle = formatEventSchedule(event),
        categoryLabel = venue.takeIf { it.isNotBlank() },
        locationLine = venue.takeIf { it.isNotBlank() },
        statusChip = "$going going",
        infoItems = info,
        actions =
            listOf(
                MarkerDetailAction("share", "Share", { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Secondary),
                MarkerDetailAction("directions", "Directions", { Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Primary, coords != null),
                MarkerDetailAction("open_event", "Open Event", { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) }, MarkerDetailActionStyle.Secondary),
                MarkerDetailAction(
                    "rsvp",
                    rsvpTitle,
                    { Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    MarkerDetailActionStyle.Secondary,
                    enabled = !rsvpBusy,
                ),
            ),
        upcomingEvents = siblings.map { upcomingItem(it) },
    )
}

private fun infoDistance(value: String) =
    MarkerInfoItem(
        icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
        label = "Distance",
        value = value,
    )

private fun infoCoordinates(lat: Double, lng: Double) =
    MarkerInfoItem(
        icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
        label = "Coordinates",
        value = String.format(Locale.US, "%.5f°, %.5f°", lat, lng),
    )

private fun shareText(title: String, subtitle: String?, lat: Double, lng: Double): String =
    buildString {
        append(title)
        subtitle?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        append('\n')
        append(String.format(Locale.US, "%.5f, %.5f", lat, lng))
    }

private fun eventGeoCoordinate(event: EventDto): Pair<Double, Double>? {
    val coords = event.location?.coordinates ?: return null
    if (coords.size < 2) return null
    val lng = coords[0]
    val lat = coords[1]
    if (!lat.isFinite() || !lng.isFinite()) return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

private fun formatEventSchedule(event: EventDto): String {
    val instant = parseEventInstant(event.startsAt.orEmpty()) ?: return "—"
    val zoned = instant.atZone(ZoneId.systemDefault())
    val date = zoned.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault()))
    return date
}

private fun upcomingItem(event: EventDto): UpcomingEventItem {
    val instant = parseEventInstant(event.startsAt.orEmpty())
    val zoned = instant?.atZone(ZoneId.systemDefault())
    val month = zoned?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))?.uppercase(Locale.getDefault()).orEmpty()
    val day = zoned?.format(DateTimeFormatter.ofPattern("dd", Locale.getDefault())).orEmpty()
    val time = zoned?.format(DateTimeFormatter.ofPattern("EEE · h:mm a", Locale.getDefault())).orEmpty()
    return UpcomingEventItem(
        id = event.id,
        title = event.name,
        monthAbbrev = month,
        dayText = day,
        timeLine = time,
        goingCount = event.rsvpCounts?.going ?: 0,
    )
}

fun formatMapMarkerDistanceMeters(meters: Double): String =
    when {
        meters < 80 -> "Here"
        meters < 1000 -> String.format(Locale.US, "%.0f m", meters)
        else -> String.format(Locale.US, "%.1f mi", meters / 1609.34)
    }

fun openMapMarkerDirections(
    context: android.content.Context,
    lat: Double,
    lng: Double,
    label: String,
) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

fun shareMapMarkerText(
    context: android.content.Context,
    text: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.marker_detail_share)))
    }
}
