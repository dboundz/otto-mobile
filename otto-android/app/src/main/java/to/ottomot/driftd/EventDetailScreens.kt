package to.ottomot.driftd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Patterns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.event.EVENT_CHECK_IN_RADIUS_METERS
import to.ottomot.driftd.core.event.eventHasVenueCoordinates
import to.ottomot.driftd.core.event.eventVenueLatLng
import to.ottomot.driftd.core.event.haversineMeters
import to.ottomot.driftd.core.event.openMeetLocationInMaps
import to.ottomot.driftd.core.event.isWithinEventCheckInWindow
import to.ottomot.driftd.core.event.parseEventInstant
import to.ottomot.driftd.core.location.LocationFix
import to.ottomot.driftd.map.OttoMapEventMarkerContent
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.CircleChatSenderDto
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.ui.components.SquadShareListRow
import to.ottomot.driftd.ui.dialog.OttoEducationDialog
import to.ottomot.driftd.ui.dialog.OttoEducationLocationHero

private fun shareEventPage(
    context: Context,
    eventName: String,
    url: String,
) {
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.event_share_url_subject, eventName))
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.event_share_url_text, eventName, url),
                    )
                },
                null,
            ),
        )
    } catch (_: Exception) {
    }
}

internal fun eventPublicWebsiteUrl(ev: EventDto): String {
    val ref = ev.slug?.trim()?.takeIf { it.isNotEmpty() } ?: ev.id.trim()
    return "https://driftd.com/e/$ref"
}

private fun syntheticDirectConversationForShare(user: UserDto): DirectConversationDto =
    DirectConversationDto(
        id = user.id.trim(),
        participantUserIds = null,
        otherUser =
            CircleChatSenderDto(
                id = user.id,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                mapAccentKey = user.mapAccentKey,
            ),
        lastMessageAt = null,
    )

private fun directConversationForShareRecipient(
    user: UserDto,
    dmConversations: List<DirectConversationDto>,
): DirectConversationDto =
    dmConversations.firstOrNull { it.otherUser?.id == user.id }
        ?: syntheticDirectConversationForShare(user)

private fun dmSharePeerUserId(conv: DirectConversationDto): String? =
    conv.otherUser?.id?.trim()?.takeIf { it.isNotBlank() }

/** With [collapsedMaxLines]; also used as a fallback when layout reports no overflow yet text is clearly long. */
private const val EventDescriptionReadMoreMinChars = 220

private const val EventDetailDescLinkTag = "EVENT_DETAIL_LINK"

private data class EventDescDetectedLink(
    val start: Int,
    val end: Int,
    val url: String,
)

private fun collectEventDescriptionLinks(plain: String): List<EventDescDetectedLink> {
    val links = mutableListOf<EventDescDetectedLink>()
    val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>()]+)""")
    urlRegex.findAll(plain).forEach { match ->
        val raw = match.value.trim()
        val trimmed = raw.trimEnd('.', ',', '!', '?', ';', ':')
        if (trimmed.isBlank()) return@forEach
        val end = match.range.first + trimmed.length
        val url =
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        links.add(EventDescDetectedLink(match.range.first, end, url))
    }

    val matcher = Patterns.EMAIL_ADDRESS.matcher(plain)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        val insideUrl = links.any { link -> start >= link.start && end <= link.end }
        if (insideUrl) continue
        val email = plain.substring(start, end)
        links.add(EventDescDetectedLink(start, end, "mailto:$email"))
    }

    val sorted = links.sortedBy { it.start }
    val kept = mutableListOf<EventDescDetectedLink>()
    for (candidate in sorted) {
        val overlaps =
            kept.any { existing ->
                candidate.start < existing.end && existing.start < candidate.end
            }
        if (overlaps) continue
        kept.add(candidate)
    }
    return kept
}

private fun buildEventDetailDescriptionAnnotated(
    plain: String,
    baseColor: Color,
    linkColor: Color,
): AnnotatedString {
    val links = collectEventDescriptionLinks(plain)
    if (links.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) {
                append(plain)
            }
        }
    }
    return buildAnnotatedString {
        var cursor = 0
        for (link in links.sortedBy { it.start }) {
            if (cursor < link.start) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(plain.substring(cursor, link.start))
                }
            }
            val slice = plain.substring(link.start, link.end)
            val spanStart = length
            withStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(slice)
            }
            addStringAnnotation(EventDetailDescLinkTag, link.url, spanStart, length)
            cursor = link.end
        }
        if (cursor < plain.length) {
            withStyle(SpanStyle(color = baseColor)) {
                append(plain.substring(cursor))
            }
        }
    }
}

@Composable
private fun EventDetailDescriptionText(
    plain: String,
    style: TextStyle,
    baseColor: Color,
    linkColor: Color,
    maxLines: Int,
    overflow: TextOverflow,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated =
        remember(plain, baseColor, linkColor) {
            buildEventDetailDescriptionAnnotated(plain, baseColor, linkColor)
        }
    var layoutResult by remember(plain) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style.copy(color = Color.Unspecified),
        modifier =
            modifier.pointerInput(annotated) {
                detectTapGestures { offset ->
                    val lr = layoutResult ?: return@detectTapGestures
                    val pos = lr.getOffsetForPosition(offset)
                    annotated
                        .getStringAnnotations(EventDetailDescLinkTag, pos, pos)
                        .firstOrNull()
                        ?.let { ann ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
                            } catch (_: Exception) {
                            }
                        }
                }
            },
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = {
            layoutResult = it
            onTextLayout(it)
        },
    )
}

private data class EventRsvpChoice(
    val status: String,
    val labelRes: Int,
    val icon: ImageVector,
    val sortOrder: Int,
)

private val EventDetailRsvpChoices =
    listOf(
        EventRsvpChoice(OttoShellUiState.RsvpGoing, R.string.event_rsvp_going, Icons.Filled.Check, 0),
        EventRsvpChoice(OttoShellUiState.RsvpInterested, R.string.event_rsvp_interested, Icons.AutoMirrored.Outlined.HelpOutline, 2),
        EventRsvpChoice(OttoShellUiState.RsvpNotGoing, R.string.event_rsvp_cant_go, Icons.Outlined.Cancel, 1),
    )

private val EventDetailRsvpRows =
    listOf(
        EventDetailRsvpChoices[0],
        EventDetailRsvpChoices[2],
        EventDetailRsvpChoices[1],
    )

private data class EventRsvpRosterEntry(
    val choice: EventRsvpChoice,
    val user: UserDto,
)

private fun canEditSquadEvent(
    event: EventDto,
    circles: List<CircleDto>,
    meUserId: String?,
    sourceCircleId: String?,
): Boolean {
    val uid = meUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    if (event.visibility?.equals("circle", ignoreCase = true) != true) return false
    if (event.createdByUserId == uid) return true
    val circleId = event.circleId?.trim()?.takeIf { it.isNotEmpty() } ?: sourceCircleId?.trim()?.takeIf { it.isNotEmpty() }
    val circle = circles.firstOrNull { it.id == circleId } ?: return false
    if (ottoUserIdsEqual(circle.ownerId, uid)) return true
    return circle.members.orEmpty().any { member ->
        ottoUserIdsEqual(member.userId, uid) && member.role.equals("admin", ignoreCase = true)
    }
}

@Composable
private fun EventRsvpChoiceButton(
    choice: EventRsvpChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val view = LocalView.current
    Surface(
        modifier =
            modifier
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedColor.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.18f else 0.08f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                choice.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.76f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(choice.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else Color.White.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun crewUsersForEventDetail(
    event: EventDto,
    meUser: UserDto?,
    squadMemberIds: Set<String>? = null,
): List<UserDto> = rsvpUsersForEventDetail(event, meUser, OttoShellUiState.RsvpGoing, squadMemberIds)

private fun rsvpUsersForEventDetail(
    event: EventDto,
    meUser: UserDto?,
    status: String,
    squadMemberIds: Set<String>? = null,
): List<UserDto> {
    val rsvpUsers =
        event.contactsRsvps
            ?.mapNotNull { rsvp ->
                rsvp.user?.takeIf { rsvp.status == status }
            }
            ?: event.contactsGoing.orEmpty().takeIf { status == OttoShellUiState.RsvpGoing }.orEmpty()
    val base =
        rsvpUsers
            .filter { it.id.isNotBlank() && (squadMemberIds == null || it.id in squadMemberIds) }
            .distinctBy { it.id }
            .toMutableList()
    if (event.currentUserRsvp == status &&
        meUser != null &&
        (squadMemberIds == null || meUser.id in squadMemberIds) &&
        base.none { it.id == meUser.id }
    ) {
        base.add(0, meUser)
    }
    return base
}

private fun rsvpCountForEventDetail(
    event: EventDto,
    choice: EventRsvpChoice,
    visibleUsers: List<UserDto>,
    squadMemberIds: Set<String>? = null,
): Int =
    if (squadMemberIds != null) {
        visibleUsers.size
    } else {
        when (choice.status) {
            OttoShellUiState.RsvpGoing -> event.rsvpCounts?.going ?: visibleUsers.size
            OttoShellUiState.RsvpInterested -> event.rsvpCounts?.interested ?: visibleUsers.size
            OttoShellUiState.RsvpNotGoing -> event.rsvpCounts?.notGoing ?: visibleUsers.size
            else -> visibleUsers.size
        }
    }

@Composable
private fun EventDetailFactRow(
    icon: ImageVector,
    title: String,
    value: String,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .heightIn(min = 50.dp)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EventCrewAvatarChip(
    user: UserDto,
    dotColor: Color,
    onOpenUserProfile: ((UserDto) -> Unit)? = null,
) {
    val accent = mapAccentComposeColor(user.mapAccentKey)
    Box(
        modifier =
            Modifier
                .size(62.dp)
                .then(
                    if (onOpenUserProfile != null) {
                        Modifier.clickable(
                            onClickLabel = stringResource(R.string.map_member_profile_view_profile),
                            onClick = { onOpenUserProfile(user) },
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .border(2.dp, accent, CircleShape),
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxSize()
                    .clip(CircleShape),
            ) {
                UserProfileAvatar(
                    displayName = user.displayName,
                    userId = user.id,
                    avatarUrl = user.avatarUrl,
                    mapAccentKey = user.mapAccentKey,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 3.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventRsvpRosterSheet(
    entries: List<EventRsvpRosterEntry>,
    circles: List<CircleDto>,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
    meUserId: String?,
    onOpenUserProfile: ((UserDto) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                stringResource(R.string.event_rsvp_roster_heading),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries.sortedWith(compareBy({ it.choice.sortOrder }, { it.user.displayName.lowercase() })), key = { it.user.id }) { entry ->
                    val dot =
                        userPresenceLifecycleDotColor(
                            userId = entry.user.id,
                            meUserId = meUserId,
                            circles = circles,
                            presenceMembersByCircleId = presenceMembersByCircleId,
                        )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (onOpenUserProfile != null) {
                                    Modifier.clickable(
                                        onClickLabel = stringResource(R.string.map_member_profile_view_profile),
                                        onClick = {
                                            onDismiss()
                                            onOpenUserProfile(entry.user)
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EventCrewAvatarChip(user = entry.user, dotColor = dot)
                        Text(
                            entry.user.displayName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                stringResource(entry.choice.labelRes),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
internal fun EventShareToChatSheet(
    sheetState: androidx.compose.material3.SheetState,
    event: EventDto,
    circlesAvailable: List<CircleDto>,
    contacts: List<UserDto>,
    dmContactsForShare: List<UserDto>,
    dmConversations: List<DirectConversationDto>,
    meUser: UserDto?,
    circlesForDmSubtitle: List<CircleDto>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPost: (List<String>, List<String>, String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val ctx = LocalContext.current
        val myUserId = meUser?.id?.trim()?.takeIf { it.isNotEmpty() }

        var message by remember(event.id) {
            mutableStateOf(ctx.getString(R.string.event_share_default_message, event.name))
        }
        var selectedCircleId by remember(event.id, circlesAvailable) {
            mutableStateOf<String?>(null)
        }
        var dmPick by remember(event.id) { mutableStateOf<Set<String>>(emptySet()) }

        var shareRecipientTabIdx by rememberSaveable(event.id, "evShareTab") { mutableStateOf(0) }
        val tabIdx = shareRecipientTabIdx.coerceIn(0, 1)
        var squadSearchQuery by rememberSaveable(event.id, "evSqS") { mutableStateOf("") }
        var dmSearchQuery by rememberSaveable(event.id, "evDmS") { mutableStateOf("") }

        val shareDmConversationRows =
            remember(dmContactsForShare, dmConversations) {
                dmContactsForShare.map { directConversationForShareRecipient(it, dmConversations) }
            }

        val filteredSquads =
            remember(circlesAvailable, squadSearchQuery) {
                val q = squadSearchQuery.trim()
                if (q.isEmpty()) {
                    circlesAvailable
                } else {
                    circlesAvailable.filter { it.name.contains(q, ignoreCase = true) }
                }
            }

        val filteredDmConversations =
            remember(shareDmConversationRows, dmSearchQuery, circlesForDmSubtitle, myUserId) {
                val q = dmSearchQuery.trim().lowercase(Locale.getDefault())
                if (q.isEmpty()) {
                    shareDmConversationRows
                } else {
                    shareDmConversationRows.filter { conv ->
                        val name =
                            conv.otherUser?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                                ?: shortenId(conv.id)
                        val sub =
                            sharedCircleNameWithPeer(circlesForDmSubtitle, myUserId, conv.otherUser?.id)
                                .orEmpty()
                        val preview = directInboxPreviewLine(conv, myUserId).lowercase(Locale.getDefault())
                        name.lowercase(Locale.getDefault()).contains(q) ||
                            sub.lowercase(Locale.getDefault()).contains(q) ||
                            preview.contains(q)
                    }
                }
            }

        Column(
            Modifier
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.event_share_sheet_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(10.dp))

            OttoIosUnderlineTabBar(
                labelResIds =
                    listOf(
                        R.string.squads_subtab_squads,
                        R.string.squads_subtab_dms,
                    ),
                selectedIdx = tabIdx,
                onSelect = { shareRecipientTabIdx = it },
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
            ) {
                when (tabIdx) {
                    0 -> {
                        item {
                            if (circlesAvailable.isEmpty()) {
                                OttoEmptyState(
                                    title = stringResource(R.string.event_share_sheet_need_squad),
                                    icon = Icons.Outlined.Groups,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 180.dp),
                                )
                            } else {
                                OutlinedTextField(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 8.dp),
                                    value = squadSearchQuery,
                                    onValueChange = { squadSearchQuery = it },
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
                        }
                        if (circlesAvailable.isNotEmpty()) {
                            if (filteredSquads.isEmpty()) {
                                item {
                                    OttoEmptyState(
                                        title = stringResource(R.string.squads_search_no_results),
                                        icon = Icons.Outlined.Search,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 180.dp),
                                    )
                                }
                            }
                            items(items = filteredSquads, key = { it.id }) { c ->
                                val sel = selectedCircleId == c.id
                                ElevatedCard(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 6.dp)
                                            .clickable(enabled = !busy) {
                                                selectedCircleId = c.id
                                            },
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SquadShareListRow(
                                            squadName = c.name,
                                            photoUrl = c.photoUrl,
                                            memberCount = c.members?.size ?: 0,
                                            modifier = Modifier.weight(1f),
                                        )
                                        androidx.compose.material3.RadioButton(
                                            selected = sel,
                                            onClick = null,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            if (dmContactsForShare.isEmpty()) {
                                OttoEmptyState(
                                    title = stringResource(R.string.messages_conversations_empty),
                                    icon = Icons.Outlined.Forum,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 180.dp),
                                )
                            } else {
                                OutlinedTextField(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 8.dp),
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
                        }

                        when {
                            dmContactsForShare.isEmpty() -> {}
                            filteredDmConversations.isEmpty() -> {
                                item {
                                    OttoEmptyState(
                                        title = stringResource(R.string.squads_search_no_results),
                                        icon = Icons.Outlined.Search,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 180.dp),
                                    )
                                }
                            }

                            else -> {
                                items(items = filteredDmConversations, key = { "${it.id}_${it.otherUser?.id}" }) { conv ->
                                    val pid = dmSharePeerUserId(conv)
                                    val sel = pid != null && dmPick.contains(pid)
                                    DmConversationListRow(
                                        conversation = conv,
                                        circles = circlesForDmSubtitle,
                                        myUserId = myUserId,
                                        selectionMode = true,
                                        selected = sel,
                                        onClick = {
                                            pid?.let { id ->
                                                if (!busy) {
                                                    dmPick = if (sel) dmPick - id else dmPick + id
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                value = message,
                enabled = !busy,
                onValueChange = { if (it.length <= 500) message = it },
                placeholder = {
                    Text(stringResource(R.string.event_share_sheet_hint))
                },
                minLines = 3,
            )

            Spacer(Modifier.height(14.dp))

            val canPost =
                message.trim().isNotEmpty() &&
                    (selectedCircleId != null || dmPick.isNotEmpty()) &&
                    !busy

            Button(
                onClick = {
                    onPost(
                        listOfNotNull(selectedCircleId),
                        dmPick.toList(),
                        message,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canPost,
            ) {
                Text(stringResource(R.string.event_share_post))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Event detail hero: date badge, title, location, and going count (mirrors iOS heroHeader). */
@Composable
internal fun EventDetailHeroHeader(
    event: EventDto,
    goingCount: Int,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val inst = remember(event.startsAt) { event.startsAt?.let { parseEventInstant(it) } }
    val zdt = remember(inst, zone) { inst?.atZone(zone) }
    val monthShort =
        zdt?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
            ?.uppercase(Locale.getDefault()) ?: "—"
    val dayNum =
        zdt?.format(DateTimeFormatter.ofPattern("dd", Locale.getDefault())) ?: "—"
    val addressLine = shortAddress(event)
    val purple = Color(0xFFBF5AF2)
    val badgeShape = RoundedCornerShape(12.dp)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .width(58.dp)
                    .height(76.dp)
                    .clip(badgeShape)
                    .background(Color.Black.copy(alpha = 0.50f))
                    .border(width = 1.3.dp, color = purple, shape = badgeShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                monthShort,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = purple,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dayNum,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = purple,
                maxLines = 1,
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                event.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    addressLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.055f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.event_going_pill_format, goingCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailOverlay(
    detailUi: EventDetailUi,
    circles: List<CircleDto>,
    contacts: List<UserDto>,
    meUser: UserDto?,
    dmConversations: List<DirectConversationDto>,
    onPrefetchDirectMessages: () -> Unit = {},
    sourceCircleId: String? = null,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>> = emptyMap(),
    deviceLocationFix: LocationFix? = null,
    onClose: () -> Unit,
    onOpenEventLocationOnMap: (Double, Double, String) -> Unit = { _, _, _ -> },
    onRsvp: (String, String) -> Unit,
    onCheckIn: (String) -> Unit,
    onUpdateSquadEvent: suspend (String, String, String?, Instant, Instant, String?, String?, String?, String?, String?, Double?, Double?, ByteArray?, String?) -> Result<Unit>,
    onDeleteSquadEvent: suspend (String) -> Result<Unit>,
    onToggleAutoCheckIn: (Boolean) -> Unit,
    postEventShareToChat: (String, List<String>, List<String>, String) -> Unit,
    pendingSquadChatFocusTick: Long = 0L,
    onToggleShowPublicGoingEventsOnProfile: (Boolean) -> Unit = {},
    onOpenUserProfile: ((UserDto) -> Unit)? = null,
    onEventAssociationsSaved: (List<to.ottomot.driftd.core.network.dto.EventAttachedSquadDto>) -> Unit = {},
) {
    val ctx = LocalContext.current
    var fineGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var eventDetailLocationPrimerVisible by remember { mutableStateOf(false) }
    var showEventDetailLocationDeniedModal by remember { mutableStateOf(false) }
    var pendingLocationCheckIn by rememberSaveable(detailUi.eventId) { mutableStateOf(false) }
    val requestPerm =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            fineGranted = granted
            if (granted && pendingLocationCheckIn) {
                pendingLocationCheckIn = false
                onCheckIn(detailUi.eventId)
            } else if (!granted) {
                pendingLocationCheckIn = false
                showEventDetailLocationDeniedModal = true
            }
        }

    var shareSquadActionsOpen by rememberSaveable(detailUi.eventId) { mutableStateOf(false) }
    var shareToChatSheetOpen by rememberSaveable(detailUi.eventId) { mutableStateOf(false) }
    var sharedWithSheetOpen by rememberSaveable(detailUi.eventId) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val zone = ZoneId.systemDefault()

    val eventModel = detailUi.event

    val lockedShareCircleId =
        remember(eventModel, sourceCircleId) {
            val e = eventModel ?: return@remember null
            if (e.visibility?.trim()?.equals("circle", ignoreCase = true) == true) {
                e.circleId?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                sourceCircleId?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize().clip(RectangleShape)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 18.dp)
                    .padding(top = 10.dp, bottom = 120.dp),
            ) {
                when {
                    detailUi.loadingDetail && detailUi.event == null ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    detailUi.event == null && detailUi.detailError != null ->
                        Text(detailUi.detailError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    detailUi.event == null ->
                        Text(stringResource(R.string.event_detail_loading))
                    else -> {
                        val event = detailUi.event!!
                        val eventId = detailUi.eventId
                        var descriptionExpanded by rememberSaveable(eventId) { mutableStateOf(false) }

                        val inst = remember(event.startsAt) { event.startsAt?.let { parseEventInstant(it) } }
                        val zdt = remember(inst, zone) { inst?.atZone(zone) }
                        val fullDate =
                            zdt?.format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
                            ) ?: "—"
                        val timeOnly =
                            zdt?.format(
                                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
                            ) ?: "—"

                        val addressLine = shortAddress(event)
                        val venue = remember(event.id) { eventVenueLatLng(event) }
                        val vLat = venue?.first
                        val vLng = venue?.second

                        val going = event.currentUserRsvp == OttoShellUiState.RsvpGoing
                        val checkedIn = event.currentUserCheckIn != null
                        val inWindow = isWithinEventCheckInWindow(event)
                        val needGps = eventHasVenueCoordinates(event)
                        val checkInDistanceMeters =
                            remember(deviceLocationFix, vLat, vLng) {
                                val fix = deviceLocationFix ?: return@remember null
                                val fixElapsed = fix.elapsedRealtimeNanos ?: return@remember null
                                val age = SystemClock.elapsedRealtimeNanos() - fixElapsed
                                if (age !in 0..30L * 1_000_000_000L) return@remember null
                                if (vLat == null || vLng == null) return@remember null
                                if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return@remember null
                                haversineMeters(fix.latitude, fix.longitude, vLat, vLng)
                            }
                        val showManualCheckIn =
                            going &&
                                !checkedIn &&
                                inWindow &&
                                needGps &&
                                fineGranted &&
                                checkInDistanceMeters != null &&
                                checkInDistanceMeters <= EVENT_CHECK_IN_RADIUS_METERS
                        val squadMemberIds =
                            remember(circles, sourceCircleId, event.circleId, event.visibility) {
                                val scopedCircleId =
                                    sourceCircleId?.trim()?.takeIf { it.isNotBlank() }
                                        ?: event.circleId
                                            ?.takeIf { event.visibility?.equals("circle", ignoreCase = true) == true }
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() }
                                scopedCircleId?.let { cid ->
                                    circles
                                        .firstOrNull { it.id == cid }
                                        ?.members
                                        .orEmpty()
                                        .map { it.userId }
                                        .toSet()
                                }
                            }
                        val rsvpRows =
                            remember(
                                event.id,
                                event.rsvpCounts,
                                event.contactsGoing,
                                event.contactsRsvps,
                                meUser?.id,
                                event.currentUserRsvp,
                                squadMemberIds,
                            ) {
                                EventDetailRsvpRows.map { choice ->
                                    val users = rsvpUsersForEventDetail(event, meUser, choice.status, squadMemberIds)
                                    Triple(choice, users, rsvpCountForEventDetail(event, choice, users, squadMemberIds))
                                }
                            }
                        val rsvpRosterEntries =
                            remember(rsvpRows) {
                                rsvpRows.flatMap { (choice, users, _) -> users.map { EventRsvpRosterEntry(choice, it) } }
                            }

                        var showRsvpRoster by rememberSaveable(eventId) { mutableStateOf(false) }
                        var showEditEvent by rememberSaveable(eventId) { mutableStateOf(false) }
                        val canEditEvent = canEditSquadEvent(event, circles, meUser?.id, sourceCircleId)

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onClose,
                                enabled = !detailUi.actionBusy,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.event_detail_back),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (canEditEvent) {
                                    IconButton(
                                        onClick = { showEditEvent = true },
                                        enabled = !detailUi.actionBusy,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.squad_edit_event_title),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { shareSquadActionsOpen = true },
                                    enabled = !detailUi.actionBusy,
                                ) {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = stringResource(R.string.event_share_squad_actions_title),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        val heroGoingCount =
                            rsvpRows.firstOrNull { it.first.status == OttoShellUiState.RsvpGoing }?.third
                                ?: (event.rsvpCounts?.going ?: 0)
                        EventDetailHeroHeader(
                            event = event,
                            goingCount = heroGoingCount,
                            zone = zone,
                        )
                        Spacer(Modifier.height(18.dp))

                        if (!event.attachedSquads.isNullOrEmpty()) {
                            EventSharedWithModule(
                                squads = event.attachedSquads.orEmpty(),
                                circles = circles,
                                onTap = { sharedWithSheetOpen = true },
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        val bannerUrl =
                            event.bannerImage?.url?.let { MediaUrlResolver.resolve(it) }?.toString()
                        if (bannerUrl != null) {
                            AsyncImage(
                                model = ottoImageRequest(ctx, bannerUrl),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(158.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.14f),
                                            RoundedCornerShape(14.dp),
                                        ),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        val descBody =
                            event.description?.trim()?.takeIf { it.isNotEmpty() }
                                ?: stringResource(R.string.event_detail_placeholder_description)
                        val descNorm =
                            remember(descBody) {
                                descBody.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
                            }
                        val collapsedMaxLines = 6
                        var needsReadMoreControl by remember(descNorm) {
                            mutableStateOf(descNorm.length >= EventDescriptionReadMoreMinChars)
                        }
                        val descBaseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        val descLinkColor = MaterialTheme.colorScheme.primary
                        Column(Modifier.fillMaxWidth()) {
                            EventDetailDescriptionText(
                                plain = descNorm,
                                style = MaterialTheme.typography.bodyMedium,
                                baseColor = descBaseColor,
                                linkColor = descLinkColor,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else collapsedMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { layout ->
                                    if (!descriptionExpanded) {
                                        needsReadMoreControl =
                                            layout.hasVisualOverflow ||
                                                descNorm.length >= EventDescriptionReadMoreMinChars
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (needsReadMoreControl) {
                                Text(
                                    stringResource(
                                        if (descriptionExpanded) {
                                            R.string.event_detail_show_less
                                        } else {
                                            R.string.event_detail_read_more
                                        },
                                    ),
                                    modifier =
                                        Modifier
                                            .padding(top = 6.dp)
                                            .clickable { descriptionExpanded = !descriptionExpanded },
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                EventDetailFactRow(
                                    icon = Icons.Outlined.CalendarMonth,
                                    title = stringResource(R.string.event_card_date_label),
                                    value = fullDate,
                                    chevron = false,
                                    onClick = {},
                                )
                                HorizontalDivider(
                                    Modifier.padding(start = 36.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                )
                                EventDetailFactRow(
                                    icon = Icons.Outlined.Schedule,
                                    title = stringResource(R.string.event_card_time_label),
                                    value = timeOnly,
                                    chevron = false,
                                    onClick = {},
                                )
                                HorizontalDivider(
                                    Modifier.padding(start = 36.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                )
                                EventDetailFactRow(
                                    icon = Icons.Outlined.LocationOn,
                                    title = stringResource(R.string.event_card_meet_label),
                                    value = addressLine,
                                    chevron = true,
                                    onClick = {
                                        openMeetLocationInMaps(ctx, addressLine, vLat, vLng)
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val mapsKeyOk = BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()
                        if (mapsKeyOk && vLat != null && vLng != null) {
                            val point = Point.fromLngLat(vLng, vLat)
                            val eventMapViewportState = rememberMapViewportState()
                            RouteMapFitCameraEffect(
                                mapViewportState = eventMapViewportState,
                                lineCoordinates = emptyList(),
                                mapPoints =
                                    listOf(
                                        RouteMapPoint(
                                            id = "event-${detailUi.eventId}-venue",
                                            lat = vLat,
                                            lng = vLng,
                                            markerType = null,
                                            index = 0,
                                        ),
                                    ),
                                paddingDp = 32.0,
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(165.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(14.dp),
                                        ),
                            ) {
                                MapboxMap(
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                    mapViewportState = eventMapViewportState,
                                    scaleBar = {},
                                    compass = {},
                                    attribution = {},
                                    style = {
                                        MapStyle(style = Style.DARK)
                                    },
                                ) {
                                    RouteMapInteractionEffect(allowInteraction = false)
                                    ViewAnnotation(
                                        options =
                                            viewAnnotationOptions {
                                                geometry(point)
                                                annotationAnchor {
                                                    anchor(ViewAnnotationAnchor.CENTER)
                                                }
                                                allowOverlap(true)
                                            },
                                    ) {
                                        OttoMapEventMarkerContent()
                                    }
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                onOpenEventLocationOnMap(vLat, vLng, detailUi.eventId)
                                            },
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.event_rsvp_roster_heading),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            if (rsvpRosterEntries.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.event_crew_view_all),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showRsvpRoster = true },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            for ((choice, users, count) in rsvpRows) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(choice.labelRes),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Text(
                                            "($count)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RectangleShape)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        if (users.isEmpty()) {
                                            Text(
                                                stringResource(R.string.event_rsvp_roster_empty_row),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(120.dp),
                                            )
                                        } else {
                                            for (u in users.take(8)) {
                                                val dot =
                                                    remember(u.id, meUser?.id, circles, presenceMembersByCircleId) {
                                                        userPresenceLifecycleDotColor(
                                                            userId = u.id,
                                                            meUserId = meUser?.id,
                                                            circles = circles,
                                                            presenceMembersByCircleId = presenceMembersByCircleId,
                                                        )
                                                    }
                                                EventCrewAvatarChip(
                                                    user = u,
                                                    dotColor = dot,
                                                    onOpenUserProfile = onOpenUserProfile,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            stringResource(R.string.event_check_in_heading),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    stringResource(R.string.event_auto_check_in_label),
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    stringResource(R.string.event_auto_check_in_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = meUser?.autoEventCheckInEnabled != false,
                                onCheckedChange = { v ->
                                    if (meUser != null) {
                                        onToggleAutoCheckIn(v)
                                    }
                                },
                                enabled = meUser != null && !detailUi.actionBusy,
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (showManualCheckIn) {
                            OutlinedButton(
                                onClick = { onCheckIn(eventId) },
                                enabled = !detailUi.actionBusy && !detailUi.loadingDetail,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(stringResource(R.string.event_check_in_button))
                            }
                        }

                        detailUi.snackMessage?.takeIf { it.isNotBlank() }?.let { snack ->
                            Spacer(Modifier.height(14.dp))
                            Text(
                                snack,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (showRsvpRoster) {
                            EventRsvpRosterSheet(
                                entries = rsvpRosterEntries,
                                circles = circles,
                                presenceMembersByCircleId = presenceMembersByCircleId,
                                meUserId = meUser?.id,
                                onOpenUserProfile = onOpenUserProfile,
                                onDismiss = { showRsvpRoster = false },
                            )
                        }
                        if (showEditEvent) {
                            AddSquadEventBottomSheet(
                                visible = true,
                                squadDisplayName =
                                    circles.firstOrNull { it.id == (event.circleId ?: sourceCircleId) }?.name
                                        ?: stringResource(R.string.squad_chat_event_untitled),
                                event = event,
                                onDismiss = { showEditEvent = false },
                                onDelete = {
                                    val result = onDeleteSquadEvent(event.id)
                                    if (result.isSuccess) onClose()
                                    result
                                },
                                onSubmit = { payload ->
                                    val pin = geocodeEventEditorAddressIfResolvable(ctx, payload.address)
                                    if (payload.address.geocodeQuery() != null && pin == null) {
                                        Result.failure(IllegalArgumentException("Could not resolve event address"))
                                    } else {
                                        onUpdateSquadEvent(
                                            event.id,
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
                                        ).map { SquadEventSubmitOutcome.Updated }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (detailUi.actionBusy) {
                LinearProgressIndicator(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }

            if (eventDetailLocationPrimerVisible) {
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
                        eventDetailLocationPrimerVisible = false
                        requestPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    allowsUnconfirmedDismiss = false,
                )
            }

            if (showEventDetailLocationDeniedModal) {
                OttoEducationDialog(
                    visible = true,
                    busy = false,
                    onDismissRequest = { showEventDetailLocationDeniedModal = false },
                    onCloseClick = { showEventDetailLocationDeniedModal = false },
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
                        showEventDetailLocationDeniedModal = false
                    },
                    secondaryLabel = stringResource(R.string.location_permission_modal_dismiss),
                    onSecondaryClick = { showEventDetailLocationDeniedModal = false },
                )
            }

            val ev = eventModel
            if (ev != null) {
                val cin = ev.currentUserCheckIn != null
                val rsvpInteractionsEnabled =
                    remember(ev.id, ev.startsAt, ev.endsAt) {
                        val end = eventCheckInEndsAtInstant(ev)
                        end == null || !Instant.now().isAfter(end)
                    }

                val eventBusy = detailUi.actionBusy || detailUi.loadingDetail

                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    Column {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (choice in EventDetailRsvpChoices) {
                                EventRsvpChoiceButton(
                                    choice = choice,
                                    selected = ev.currentUserRsvp == choice.status,
                                    enabled = !eventBusy && rsvpInteractionsEnabled,
                                    onClick = {
                                        onRsvp(detailUi.eventId, choice.status)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (cin) {
                            Text(
                                stringResource(R.string.event_checked_in_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp)
                                        .padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            }

            EventShareFlowSheets(
                event = detailUi.event,
                shareSquadActionsOpen = shareSquadActionsOpen,
                onShareSquadActionsOpenChange = { shareSquadActionsOpen = it },
                shareToChatSheetOpen = shareToChatSheetOpen,
                onShareToChatSheetOpenChange = { shareToChatSheetOpen = it },
                circles = circles,
                contacts = contacts,
                dmConversations = dmConversations,
                meUser = meUser,
                lockedCircleId = lockedShareCircleId,
                actionBusy = detailUi.actionBusy,
                onPrefetchDirectMessages = onPrefetchDirectMessages,
                postEventShareToChat = postEventShareToChat,
                pendingSquadChatFocusTick = pendingSquadChatFocusTick,
                onEventAssociationsSaved = onEventAssociationsSaved,
            )

            if (sharedWithSheetOpen && !detailUi.event?.attachedSquads.isNullOrEmpty()) {
                EventSharedWithSheet(
                    squads = detailUi.event!!.attachedSquads.orEmpty(),
                    circles = circles,
                    onDismiss = { sharedWithSheetOpen = false },
                )
            }
        }
    }
}

