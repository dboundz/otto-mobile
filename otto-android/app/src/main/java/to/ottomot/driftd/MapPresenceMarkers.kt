package to.ottomot.driftd

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.SuccessResult
import coil.size.Size
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.ottomot.driftd.core.network.MediaUrlResolver
import kotlin.math.max
import kotlin.math.roundToInt
import to.ottomot.driftd.core.network.dto.GarageCarDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.map.PresenceProximityGroup
import to.ottomot.driftd.map.clusteringThresholdMeters
import to.ottomot.driftd.map.groupNearbyPresence
import to.ottomot.driftd.map.TravelSurface
import to.ottomot.driftd.map.MapTravelSurfaceSampler
import to.ottomot.driftd.map.normalizePresenceMovementMode

internal fun showsSelfDriveBrandLogoOnMap(state: OttoShellUiState): Boolean {
    if (state.activeDriveSession != null) return true
    if (state.mapRouteSessionActive) return true
    if (state.mapSharingLocation) return true
    return false
}

internal fun mapPresenceBrandLogoUrlByUserId(
    members: List<PresenceMemberDto>,
    meId: String?,
    selectedSharingCarId: String,
    garageCars: List<GarageCarDto>,
    showsSelfLogo: Boolean,
    context: Context,
): Map<String, String> {
    val urls = linkedMapOf<String, String>()
    for (member in members) {
        resolveMapPresenceBrandLogoUrl(
            member = member,
            meId = meId,
            selectedSharingCarId = selectedSharingCarId,
            garageCars = garageCars,
            showsSelfLogo = showsSelfLogo,
            context = context,
        )?.let { urls[member.userId.trim()] = it }
    }
    return urls
}

private fun resolveMapPresenceBrandLogoUrl(
    member: PresenceMemberDto,
    meId: String?,
    selectedSharingCarId: String,
    garageCars: List<GarageCarDto>,
    showsSelfLogo: Boolean,
    context: Context,
): String? {
    val isSelf = meId != null && ottoUserIdsEqual(member.userId, meId)
    if (isSelf) {
        if (!showsSelfLogo) return null
        val carId = selectedSharingCarId.trim()
        if (carId.isEmpty()) return null
        val car = garageCars.find { it.id.trim() == carId } ?: run {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "MapPresenceMarkers",
                    "Map pin: selected carId=$carId not found in garage (${garageCars.size} cars)",
                )
            }
            return null
        }
        val slug =
            CarBrandLogoCatalog.resolvedLogoSlug(
                car.logoSlug,
                car.makeId,
                car.make,
                context,
            )
        val url = CarBrandLogoCatalog.logoUrl(slug)
        if (BuildConfig.DEBUG && url == null) {
            android.util.Log.d(
                "MapPresenceMarkers",
                "Map pin: drive active, car=${car.id}, but logo URL unresolved (slug=$slug)",
            )
        }
        return url
    }
    if (!member.isActive) return null
    val slug = member.logoSlug?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return CarBrandLogoCatalog.logoUrl(slug)
}

@Composable
private fun PresenceBrandLogoBadge(logoUrl: String) {
    AsyncImage(
        model = ottoCarBrandLogoImageRequest(LocalContext.current, logoUrl),
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun rememberPresenceProximityGroups(
    plotted: List<PresenceMemberDto>,
    cameraZoom: Float,
    latitudeCenter: Double,
    meUserId: String?,
): List<PresenceProximityGroup> =
    remember(plotted, cameraZoom, latitudeCenter, meUserId) {
        val thresh = clusteringThresholdMeters(cameraZoom, latitudeCenter)
        groupNearbyPresence(plotted, thresh, meUserId)
    }

internal fun userProfileAvatarLetter(displayName: String?, userId: String): String {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
    if (name != null) {
        return name.first().uppercaseChar().toString()
    }
    val id = userId.trim()
    for (ch in id) {
        if (ch.isLetterOrDigit()) return ch.uppercaseChar().toString()
    }
    return "?"
}

/**
 * Map annotations should not own avatar loading; preload avatars so marker content paints
 * consistently when Mapbox remeasures view annotations.
 */
private suspend fun Context.decodePresenceAvatarBitmaps(
    members: List<PresenceMemberDto>,
    contacts: List<UserDto>,
    me: UserDto?,
    maxMarkerEdgeDp: Int,
): Map<String, ImageBitmap?> =
    withContext(Dispatchers.IO) {
        val px =
            (maxMarkerEdgeDp * resources.displayMetrics.density).roundToInt().coerceIn(96, 512)
        members.associate { m ->
            val (_, raw) = presenceMemberAvatarLabel(m, contacts, me)
            val resolved = raw?.let { MediaUrlResolver.resolve(it)?.toString() }
            if (resolved.isNullOrBlank()) {
                m.userId to null
            } else {
                val req =
                    ottoImageRequest(this@decodePresenceAvatarBitmaps, resolved)
                        .newBuilder()
                        .size(Size(px, px))
                        // Keep marker avatar bitmaps software-backed for map annotation rendering.
                        .allowHardware(false)
                        .build()
                val bitmap =
                    when (
                        val r =
                            this@decodePresenceAvatarBitmaps.imageLoader.execute(req)
                    ) {
                        is SuccessResult -> r.drawable?.toBitmapOrNull()?.asImageBitmap()
                        else -> null
                    }
                m.userId to bitmap
            }
        }
    }

/** iOS AvatarView: bold white initial on accent; cropped photo when available after decode. */
@Composable
private fun PresenceMapAvatarFill(
    displayName: String,
    userId: String,
    accent: Color,
    boxSizeDp: Dp,
    preloadedBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val letter = userProfileAvatarLetter(displayName, userId)
    val initialsSp: TextUnit = max(12f, boxSizeDp.value * 0.32f).sp
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            preloadedBitmap != null ->
                Image(
                    bitmap = preloadedBitmap,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            else ->
                Box(
                    Modifier.fillMaxSize().background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = initialsSp,
                        maxLines = 1,
                    )
                }
        }
    }
}

private fun contactAccent(
    member: PresenceMemberDto,
    contacts: List<UserDto>,
    me: UserDto?,
): Color {
    val u =
        contacts.find { ottoUserIdsEqual(it.id, member.userId) }
            ?: me?.takeIf { ottoUserIdsEqual(it.id, member.userId) }
    return mapAccentComposeColor(u?.mapAccentKey)
}

private data class CompositeRoles(
    val bottomLeft: PresenceMemberDto?,
    val bottomRight: PresenceMemberDto?,
    val top: PresenceMemberDto?,
    val hiddenCount: Int,
)

private fun compositeRoles(
    members: List<PresenceMemberDto>,
    meId: String?,
): CompositeRoles {
    val ordered =
        members.sortedWith(
            compareByDescending<PresenceMemberDto> { it.isActive }.thenBy { it.userId },
        )
    val me = meId?.let { id -> ordered.firstOrNull { ottoUserIdsEqual(it.userId, id) } }
    val (bottomLeft, bottomRight, top) =
        if (me != null) {
            val peers = ordered.filter { it.userId != me.userId }
            Triple(peers.getOrNull(0), me, peers.getOrNull(1))
        } else {
            Triple(ordered.getOrNull(0), ordered.getOrNull(1), ordered.getOrNull(2))
        }
    val visibleIds = listOfNotNull(bottomLeft, bottomRight, top).distinctBy { it.userId }
    val hidden = (members.size - visibleIds.size).coerceAtLeast(0)
    return CompositeRoles(bottomLeft, bottomRight, top, hidden)
}

@Composable
private fun DiamondPointer(
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val path =
            Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
        drawPath(path, color)
    }
}

@Composable
private fun CompositeAvatarBubble(
    member: PresenceMemberDto,
    contacts: List<UserDto>,
    me: UserDto?,
    preloadedBitmap: ImageBitmap?,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val (displayName, _) = presenceMemberAvatarLabel(member, contacts, me)
    val accent = contactAccent(member, contacts, me)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(3.dp, Color.White, shape),
        contentAlignment = Alignment.Center,
    ) {
        PresenceMapAvatarFill(
            displayName = displayName,
            userId = member.userId,
            accent = accent,
            boxSizeDp = size,
            modifier = Modifier.fillMaxSize(),
            preloadedBitmap = preloadedBitmap,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(presenceLifecycleDotColor(member))
                .border(width = 1.5.dp, color = Color.Black, shape = CircleShape),
        )
    }
}

@Composable
private fun SinglePresenceAvatar(
    presence: PresenceMemberDto,
    contacts: List<UserDto>,
    me: UserDto?,
    preloadedBitmap: ImageBitmap?,
    brandLogoUrl: String? = null,
) {
    val (displayName, _) = presenceMemberAvatarLabel(presence, contacts, me)
    val accent = contactAccent(presence, contacts, me)
    val isMe = me?.id != null && ottoUserIdsEqual(presence.userId, me.id)
    val size = if (isMe) 50.dp else 46.dp
    val corner = 12.dp
    val shape = RoundedCornerShape(corner)
    val logoSize = 28.dp
    val logoHalf = logoSize / 2
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = if (brandLogoUrl.isNullOrBlank()) 0.dp else logoHalf),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.45f),
                        spotColor = accent.copy(alpha = 0.45f),
                    )
                    .border(4.dp, accent, shape),
            ) {
                Box(
                    Modifier
                        .padding(2.dp)
                        .size(size)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    PresenceMapAvatarFill(
                        displayName = displayName,
                        userId = presence.userId,
                        accent = accent,
                        boxSizeDp = size,
                        preloadedBitmap = preloadedBitmap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 4.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(presenceLifecycleDotColor(presence))
                        .border(width = 1.5.dp, color = Color.Black, shape = CircleShape),
                )
            }
            if (!brandLogoUrl.isNullOrBlank()) {
                Box(Modifier.offset(y = -logoHalf)) {
                    PresenceBrandLogoBadge(logoUrl = brandLogoUrl)
                }
            }
        }
    }
}
@Composable
private fun MovementSpeedChip(
    member: PresenceMemberDto,
    travelSurface: TravelSurface = TravelSurface.Land,
) {
    if (!member.isActive) return
    val mode = normalizePresenceMovementMode(member)
    val speedMph = member.speedMph ?: 0.0
    val movingFastEnoughForBoat = speedMph >= MapTravelSurfaceSampler.MIN_SPEED_MPH_FOR_BOAT
    if (mode != "driving" && mode != "walking" && !movingFastEnoughForBoat) return

    val speed = speedMph.roundToInt().coerceAtLeast(0)
    val tint = presenceLifecycleDotColor(member)
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            movingFastEnoughForBoat && travelSurface == TravelSurface.Water ->
                Icon(
                    Icons.Outlined.DirectionsBoat,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            mode == "driving" || movingFastEnoughForBoat ->
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            else ->
                Icon(
                    Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
        }
        Text(
            "$speed mph",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SinglePresenceMarkerColumn(
    presence: PresenceMemberDto,
    contacts: List<UserDto>,
    me: UserDto?,
    avatarsByUserId: Map<String, ImageBitmap?>?,
    travelSurface: TravelSurface = TravelSurface.Land,
    brandLogoUrl: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        SinglePresenceAvatar(
            presence,
            contacts,
            me,
            preloadedBitmap = avatarsByUserId?.get(presence.userId),
            brandLogoUrl = brandLogoUrl,
        )
        MovementSpeedChip(presence, travelSurface = travelSurface)
    }
}

@Composable
private fun CompositePresenceMarkerColumn(
    group: PresenceProximityGroup,
    contacts: List<UserDto>,
    me: UserDto?,
    avatarsByUserId: Map<String, ImageBitmap?>?,
) {
    val meId = me?.id
    val roles = remember(group.members, meId) { compositeRoles(group.members, meId) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Box(Modifier.width(96.dp).height(80.dp)) {
            roles.bottomLeft?.let { m ->
                CompositeAvatarBubble(
                    m,
                    contacts,
                    me,
                    avatarsByUserId?.get(m.userId),
                    size = 46.dp,
                    cornerRadius = 13.dp,
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = 0.dp),
                )
            }
            roles.bottomRight?.let { m ->
                if (roles.bottomLeft?.userId != m.userId) {
                    CompositeAvatarBubble(
                        m,
                        contacts,
                        me,
                        avatarsByUserId?.get(m.userId),
                        size = 46.dp,
                        cornerRadius = 13.dp,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 0.dp),
                    )
                }
            }
            roles.top?.let { top ->
                val bl = roles.bottomLeft?.userId
                val br = roles.bottomRight?.userId
                if (top.userId != bl && top.userId != br) {
                    CompositeAvatarBubble(
                        top,
                        contacts,
                        me,
                        avatarsByUserId?.get(top.userId),
                        size = 42.dp,
                        cornerRadius = 12.dp,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp),
                    )
                }
            }
            if (roles.hiddenCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-2).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6000000))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+${roles.hiddenCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        DiamondPointer(modifier = Modifier.offset(y = (-7).dp))
    }
}

@Composable
internal fun PresenceClusterMarkerContent(
    group: PresenceProximityGroup,
    contacts: List<UserDto>,
    me: UserDto?,
    travelSurfacesByUserId: Map<String, TravelSurface> = emptyMap(),
    markerScale: Float = 1f,
    brandLogoUrlsByUserId: Map<String, String> = emptyMap(),
) {
    val ctx = LocalContext.current
    val avatarCacheKey =
        remember(group.members, contacts, me) {
            group.members.joinToString("|") { m ->
                val (_, url) = presenceMemberAvatarLabel(m, contacts, me)
                "${m.userId}:${url ?: ""}"
            }
        }
    var avatarsByUserId by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }

    LaunchedEffect(avatarCacheKey) {
        val decoded =
            ctx.decodePresenceAvatarBitmaps(
                members = group.members,
                contacts = contacts,
                me = me,
                maxMarkerEdgeDp = 52,
            )
        avatarsByUserId = avatarsByUserId + decoded
    }

    Box(Modifier.scale(markerScale)) {
        when (group.members.size) {
            1 -> {
                val p = group.members.first()
                SinglePresenceMarkerColumn(
                    p,
                    contacts,
                    me,
                    avatarsByUserId,
                    travelSurface = travelSurfacesByUserId[p.userId.trim()] ?: TravelSurface.Land,
                    brandLogoUrl = brandLogoUrlsByUserId[p.userId.trim()],
                )
            }
            else ->
                CompositePresenceMarkerColumn(group, contacts, me, avatarsByUserId)
        }
    }
}

