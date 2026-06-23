package to.ottomot.driftd.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.SuccessResult
import coil.size.Size
import com.mapbox.maps.Style
import java.util.Locale
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.R
import to.ottomot.driftd.RouteMapMarkerAsset
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.map.PresenceProximityGroup
import to.ottomot.driftd.mapAccentComposeColor
import to.ottomot.driftd.ottoCarBrandLogoImageRequest
import to.ottomot.driftd.ottoImageRequest
import to.ottomot.driftd.ottoUserIdsEqual
import to.ottomot.driftd.presenceMemberAvatarLabel
import kotlin.math.roundToInt

internal class OttoCarMapMarkerBitmaps(
    private val appContext: Context,
    private val onPresenceImageChanged: () -> Unit = {},
) {
    private val density = appContext.resources.displayMetrics.density
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val avatarLoadsInFlight = mutableSetOf<String>()
    private val brandLogoBitmaps = mutableMapOf<String, Bitmap>()
    private val brandLogoLoadsInFlight = mutableSetOf<String>()

    fun ensureImages(style: Style) {
        MarkerImage.entries.forEach { marker ->
            val bitmap = bitmap(marker)
            runCatching { style.removeStyleImage(marker.imageId) }
            style.addImage(marker.imageId, bitmap)
        }
    }

    fun ensurePresenceImage(
        style: Style,
        group: PresenceProximityGroup,
        contacts: List<UserDto>,
        me: UserDto?,
        brandLogoUrlsByUserId: Map<String, String> = emptyMap(),
    ): String {
        requestPresenceAvatars(group, contacts, me)
        requestBrandLogos(group, brandLogoUrlsByUserId)
        val imageId = presenceImageId(group, contacts, me, brandLogoUrlsByUserId)
        if (!bitmaps.containsKey(imageId)) {
            bitmaps[imageId] = drawPresenceGroup(group, contacts, me, brandLogoUrlsByUserId)
        }
        style.addImage(imageId, bitmaps.getValue(imageId))
        return imageId
    }

    fun ensureHazardImage(
        style: Style,
        type: String,
    ): String {
        val imageId = hazardImageId(type)
        style.addImage(imageId, bitmap(hazardMarkerImage(type)))
        return imageId
    }

    fun ensureDotImage(
        style: Style,
        cacheKey: String,
        color: Int,
        clusterCount: Int? = null,
    ): String {
        val imageId = "otto-car-dot-$cacheKey-${clusterCount ?: 0}"
        if (!bitmaps.containsKey(imageId)) {
            bitmaps[imageId] = drawDot(color, clusterCount)
        }
        style.addImage(imageId, bitmaps.getValue(imageId))
        return imageId
    }

    private fun bitmap(marker: MarkerImage): Bitmap =
        bitmaps.getOrPut(marker.imageId) {
            when (marker) {
                MarkerImage.SavedPlace -> loadDrawable(R.drawable.map_point_saved, PIN_WIDTH_DP, PIN_HEIGHT_DP)
                MarkerImage.Event -> loadDrawable(R.drawable.map_point_event, PIN_WIDTH_DP, PIN_HEIGHT_DP)
                MarkerImage.RaceTrack -> loadDrawable(R.drawable.map_point_track, PIN_WIDTH_DP, PIN_HEIGHT_DP)
                MarkerImage.RouteStart -> loadDrawable(RouteMapMarkerAsset.drawableRes("start"), ROUTE_PIN_WIDTH_DP, ROUTE_PIN_HEIGHT_DP)
                MarkerImage.RouteFinish -> loadDrawable(RouteMapMarkerAsset.drawableRes("finish"), ROUTE_PIN_WIDTH_DP, ROUTE_PIN_HEIGHT_DP)
                MarkerImage.RouteCheckpoint -> loadDrawable(RouteMapMarkerAsset.drawableRes("waypoint"), ROUTE_PIN_WIDTH_DP, ROUTE_PIN_HEIGHT_DP)
                MarkerImage.RouteCheckpointPassed -> loadDrawable(RouteMapMarkerAsset.drawableRes("waypoint", isCompleted = true), ROUTE_PIN_WIDTH_DP, ROUTE_PIN_HEIGHT_DP)
                MarkerImage.RouteStop -> loadDrawable(RouteMapMarkerAsset.drawableRes("stop"), ROUTE_PIN_WIDTH_DP, ROUTE_PIN_HEIGHT_DP)
                MarkerImage.RoutePoint -> loadDrawable(RouteMapMarkerAsset.drawableRes("path"), BADGE_DP, BADGE_DP)
                MarkerImage.PresenceSelf -> drawBadge(fill = 0xFF38BDF8.toInt(), glyph = null)
                MarkerImage.PresencePeer -> drawBadge(fill = 0xFF00A5AA.toInt(), glyph = null)
                MarkerImage.HazardPolice -> drawHazardMarker("police")
                MarkerImage.HazardTraffic -> drawHazardMarker("traffic")
                MarkerImage.HazardCrash -> drawHazardMarker("crash")
                MarkerImage.HazardGeneric -> drawHazardMarker("hazard")
            }
        }

    private fun loadDrawable(
        @DrawableRes drawableRes: Int,
        widthDp: Float,
        heightDp: Float,
    ): Bitmap {
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
        val drawable =
            AppCompatResources.getDrawable(appContext, drawableRes)
                ?: error("Missing drawable $drawableRes")
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawPresenceGroup(
        group: PresenceProximityGroup,
        contacts: List<UserDto>,
        me: UserDto?,
        brandLogoUrlsByUserId: Map<String, String>,
    ): Bitmap =
        if (group.members.size == 1) {
            drawSinglePresence(group.members.first(), contacts, me, brandLogoUrlsByUserId)
        } else {
            drawCompositePresence(group, contacts, me)
        }

    private fun drawSinglePresence(
        member: PresenceMemberDto,
        contacts: List<UserDto>,
        me: UserDto?,
        brandLogoUrlsByUserId: Map<String, String>,
    ): Bitmap {
        val isMe = me?.id != null && ottoUserIdsEqual(member.userId, me.id)
        val brandLogoUrl = brandLogoUrlsByUserId[member.userId.trim()]
        val hasBrandLogo = !brandLogoUrl.isNullOrBlank()
        val markerSize = ((if (isMe) 50f else 46f) * density).toInt()
        val frameWidth = (72f * density).toInt()
        val frameHeight = ((if (hasBrandLogo) 90f else 68f) * density).toInt()
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = frameWidth / 2f
        val logoSize = 30f * density
        val top = (if (hasBrandLogo) 25f else 5f) * density
        val left = centerX - markerSize / 2f
        val rect = RectF(left, top, left + markerSize, top + markerSize)
        val accent = presenceAccent(member, contacts, me)
        val (name, avatar) = presenceMemberAvatarLabel(member, contacts, me)
        drawRoundedAvatar(
            canvas = canvas,
            rect = rect,
            name = name,
            userId = member.userId,
            accent = accent,
            accentStroke = 4f * density,
            whiteStroke = 0f,
            cornerRadius = 12f * density,
            avatarBitmap = avatarBitmapFor(avatar),
        )
        if (hasBrandLogo) {
            val logoBitmap = brandLogoBitmapFor(brandLogoUrl)
            if (logoBitmap == null && BuildConfig.DEBUG) {
                Log.d("OttoCarMapMarkerBitmaps", "Presence brand logo pending url=$brandLogoUrl")
            }
            logoBitmap?.let { logo ->
                val logoRect =
                    RectF(
                        centerX - logoSize / 2f,
                        rect.top - logoSize * 0.72f,
                        centerX + logoSize / 2f,
                        rect.top + logoSize * 0.28f,
                    )
                drawBrandLogo(canvas, logo, logoRect)
            }
        }
        return bitmap
    }

    private fun drawCompositePresence(
        group: PresenceProximityGroup,
        contacts: List<UserDto>,
        me: UserDto?,
    ): Bitmap {
        // Match phone `CompositePresenceMarkerColumn` layout (96×80dp cluster + tucked lower-half pointer).
        val horizontalInset = 8f * density
        val bubbleSize = 46f * density
        val topBubbleSize = 42f * density
        val boxWidth = 96f * density
        val boxHeight = 80f * density
        val edgePad = 6f * density
        val pointerHeight = 8f * density
        val pointerOverlap = 1f * density
        val width = (boxWidth + edgePad * 2f).toInt()
        val height = (boxHeight + pointerHeight - pointerOverlap + 4f * density + edgePad).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val originX = edgePad
        val originY = edgePad
        val members = orderedCompositeMembers(group.members, me?.id)
        val bottomLeft = members.getOrNull(0)
        val bottomRight = members.getOrNull(1)
        val top = members.getOrNull(2)
        val hiddenCount = (group.members.distinctBy { it.userId }.size - members.size).coerceAtLeast(0)
        val bottomRowTop = originY + boxHeight - bubbleSize
        bottomLeft?.let {
            drawCompositeBubble(
                canvas = canvas,
                member = it,
                contacts = contacts,
                me = me,
                left = originX + horizontalInset,
                top = bottomRowTop,
                size = bubbleSize,
            )
        }
        bottomRight?.let {
            drawCompositeBubble(
                canvas = canvas,
                member = it,
                contacts = contacts,
                me = me,
                left = originX + boxWidth - horizontalInset - bubbleSize,
                top = bottomRowTop,
                size = bubbleSize,
            )
        }
        top?.let {
            drawCompositeBubble(
                canvas = canvas,
                member = it,
                contacts = contacts,
                me = me,
                left = originX + (boxWidth - topBubbleSize) / 2f,
                top = originY + 2f * density,
                size = topBubbleSize,
            )
        }
        if (hiddenCount > 0) {
            val cx = originX + boxWidth - 4f * density
            val cy = originY + 4f * density
            val radius = 14f * density
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6000000.toInt() }
            val stroke =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * density
                }
            canvas.drawCircle(cx, cy, radius, fill)
            canvas.drawCircle(cx, cy, radius, stroke)
            drawCenteredText(canvas, "+$hiddenCount", cx, cy, Color.WHITE, 11f * density, Typeface.BOLD)
        }
        drawPointerTail(
            canvas = canvas,
            cx = width / 2f,
            top = originY + boxHeight - pointerOverlap,
            width = 16f * density,
            height = pointerHeight,
        )
        return bitmap
    }

    private fun drawCompositeBubble(
        canvas: Canvas,
        member: PresenceMemberDto,
        contacts: List<UserDto>,
        me: UserDto?,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val rect = RectF(left, top, left + size, top + size)
        val (name, avatar) = presenceMemberAvatarLabel(member, contacts, me)
        drawRoundedAvatar(
            canvas = canvas,
            rect = rect,
            name = name,
            userId = member.userId,
            accent = presenceAccent(member, contacts, me),
            accentStroke = 0f,
            whiteStroke = 3f * density,
            cornerRadius = 13f * density,
            avatarBitmap = avatarBitmapFor(avatar),
        )
    }

    private fun drawRoundedAvatar(
        canvas: Canvas,
        rect: RectF,
        name: String,
        userId: String,
        accent: Int,
        accentStroke: Float,
        whiteStroke: Float,
        cornerRadius: Float,
        avatarBitmap: Bitmap?,
    ) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.withAlpha(0.36f) }
        canvas.drawRoundRect(rect.offsetCopy(0f, 2f * density), cornerRadius, cornerRadius, shadow)
        canvas.drawRoundRect(rect.expandCopy(1.5f * density), cornerRadius + 1.5f * density, cornerRadius + 1.5f * density, shadow)
        if (accentStroke > 0f) {
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                style = Paint.Style.STROKE
                strokeWidth = accentStroke
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, accentPaint)
        }
        val inner = RectF(rect).apply {
            inset(accentStroke + whiteStroke / 2f, accentStroke + whiteStroke / 2f)
        }
        if (avatarBitmap != null) {
            drawRoundedBitmap(canvas, avatarBitmap, inner, cornerRadius * 0.75f)
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawRoundRect(inner, cornerRadius * 0.75f, cornerRadius * 0.75f, fill)
            val letter = userProfileLetter(name, userId)
            drawCenteredText(canvas, letter, inner.centerX(), inner.centerY(), Color.WHITE, inner.height() * 0.42f, Typeface.BOLD)
        }
        if (whiteStroke > 0f) {
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = whiteStroke
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, white)
        }
    }

    private fun drawPointerTail(
        canvas: Canvas,
        cx: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        val path = Path().apply {
            moveTo(cx - width / 2f, top)
            lineTo(cx + width / 2f, top)
            lineTo(cx, top + height)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun drawHazardMarker(type: String): Bitmap {
        val framePx = (56f * density).toInt().coerceAtLeast(48)
        val radius = 24f * density
        val bitmap = Bitmap.createBitmap(framePx, framePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = framePx / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hazardColor(type) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8C000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(center, center + density, radius, shadow)
        canvas.drawCircle(center, center, radius, fill)
        canvas.drawCircle(center, center, radius, stroke)
        drawHazardGlyph(canvas, type, center, center)
        return bitmap
    }

    private fun drawDot(
        color: Int,
        clusterCount: Int?,
    ): Bitmap {
        val framePx = (44f * density).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(framePx + if ((clusterCount ?: 0) > 1) (18f * density).toInt() else 0, framePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = framePx / 2f
        val radius = 6f * density
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x66000000 }
        canvas.drawCircle(center, center + density, radius, shadow)
        canvas.drawCircle(center, center, radius, fill)
        canvas.drawCircle(center, center, radius, stroke)
        if ((clusterCount ?: 0) > 1) {
            val countCx = framePx.toFloat()
            val countCy = 11f * density
            val countRadius = 8f * density
            canvas.drawCircle(countCx, countCy, countRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
            canvas.drawCircle(
                countCx,
                countCy,
                countRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = 0x66000000
                    style = Paint.Style.STROKE
                    strokeWidth = density
                },
            )
            drawCenteredText(canvas, clusterCount.toString(), countCx, countCy, Color.BLACK, 9f * density, Typeface.BOLD)
        }
        return bitmap
    }

    private fun drawHazardGlyph(
        canvas: Canvas,
        type: String,
        cx: Float,
        cy: Float,
    ) {
        val glyph = when (type.lowercase(Locale.US)) {
            "police" -> "!"
            "traffic" -> "↯"
            "crash" -> "✕"
            else -> "!"
        }
        val color = if (type.lowercase(Locale.US) == "hazard") Color.BLACK else Color.WHITE
        val verticalOffset =
            when (type.lowercase(Locale.US)) {
                "traffic", "crash" -> -1f * density
                else -> 0f
            }
        drawCenteredText(canvas, glyph, cx, cy + verticalOffset, color, 30f * density, Typeface.BOLD)
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        color: Int,
        size: Float,
        style: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }
        val y = cy - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, cx, y, paint)
    }

    private fun RectF.offsetCopy(
        dx: Float,
        dy: Float,
    ): RectF = RectF(this).apply { offset(dx, dy) }

    private fun RectF.expandCopy(amount: Float): RectF = RectF(this).apply { inset(-amount, -amount) }

    private fun presenceAccent(
        member: PresenceMemberDto,
        contacts: List<UserDto>,
        me: UserDto?,
    ): Int {
        val user =
            contacts.find { ottoUserIdsEqual(it.id, member.userId) }
                ?: me?.takeIf { ottoUserIdsEqual(it.id, member.userId) }
        return mapAccentComposeColor(user?.mapAccentKey).toArgb()
    }

    private fun userProfileLetter(
        name: String,
        userId: String,
    ): String =
        name.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: userId.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            ?: "?"

    private fun orderedCompositeMembers(
        members: List<PresenceMemberDto>,
        meId: String?,
    ): List<PresenceMemberDto> {
        val ordered = members.distinctBy { it.userId }.sortedWith(compareByDescending<PresenceMemberDto> { it.isActive }.thenBy { it.userId })
        val me = meId?.let { id -> ordered.firstOrNull { ottoUserIdsEqual(it.userId, id) } }
        return if (me != null) {
            (ordered.filterNot { it.userId == me.userId }.take(2) + me).take(3)
        } else {
            ordered.take(3)
        }
    }

    private fun presenceImageId(
        group: PresenceProximityGroup,
        contacts: List<UserDto>,
        me: UserDto?,
        brandLogoUrlsByUserId: Map<String, String>,
    ): String {
        val memberKey =
            group.members
                .distinctBy { it.userId }
                .sortedBy { it.userId }
                .joinToString("_") { member ->
                    val (name, avatar) = presenceMemberAvatarLabel(member, contacts, me)
                    val accent = presenceAccent(member, contacts, me)
                    val avatarKey = resolvedAvatarUrl(avatar)?.let { url -> "$url:${cachedAvatarBitmap(url) != null}" }.orEmpty()
                    val logoUrl = brandLogoUrlsByUserId[member.userId.trim()].orEmpty()
                    val logoKey = logoUrl.takeIf { it.isNotBlank() }?.let { url -> "$url:${brandLogoBitmaps.containsKey(url)}" }.orEmpty()
                    val speedBucket = member.speedMph?.roundToInt()?.coerceAtLeast(0) ?: 0
                    "${member.userId}:${name}:$avatarKey:$logoKey:$accent:${member.isActive}:${member.inApp}:${speedBucket}:${member.movementMode}"
                }
        return "otto-car-presence-v3-${memberKey.hashCode()}"
    }

    private fun requestPresenceAvatars(
        group: PresenceProximityGroup,
        contacts: List<UserDto>,
        me: UserDto?,
    ) {
        group.members.forEach { member ->
            val (_, rawAvatar) = presenceMemberAvatarLabel(member, contacts, me)
            val url = resolvedAvatarUrl(rawAvatar) ?: return@forEach
            if (cachedAvatarBitmap(url) != null || !avatarLoadsInFlight.add(url)) return@forEach
            val request =
                ottoImageRequest(appContext, url)
                    .newBuilder()
                    .size(Size((72f * density).roundToInt(), (72f * density).roundToInt()))
                    .allowHardware(false)
                    .target(
                        onSuccess = { drawable ->
                            drawable.toBitmapOrNull(config = Bitmap.Config.ARGB_8888)
                                ?.softwareBitmapOrNull()
                                ?.let { sharedAvatarBitmaps.put(url, it) }
                            avatarLoadsInFlight.remove(url)
                            clearPresenceImages()
                            onPresenceImageChanged()
                        },
                        onError = {
                            avatarLoadsInFlight.remove(url)
                        },
                    ).build()
            appContext.imageLoader.enqueue(request)
        }
    }

    private fun avatarBitmapFor(rawAvatar: String?): Bitmap? =
        resolvedAvatarUrl(rawAvatar)?.let { cachedAvatarBitmap(it) }

    private fun cachedAvatarBitmap(url: String): Bitmap? {
        val bitmap = sharedAvatarBitmaps.get(url) ?: return null
        val softwareBitmap = bitmap.softwareBitmapOrNull()
        if (softwareBitmap == null) {
            sharedAvatarBitmaps.remove(url)
            return null
        }
        if (softwareBitmap !== bitmap) {
            sharedAvatarBitmaps.put(url, softwareBitmap)
        }
        return softwareBitmap
    }

    private fun requestBrandLogos(
        group: PresenceProximityGroup,
        brandLogoUrlsByUserId: Map<String, String>,
    ) {
        group.members.forEach { member ->
            val url = brandLogoUrlsByUserId[member.userId.trim()]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            if (brandLogoBitmaps.containsKey(url) || !brandLogoLoadsInFlight.add(url)) return@forEach
            val request =
                ottoCarBrandLogoImageRequest(appContext, url)
                    .newBuilder()
                    .size(Size.ORIGINAL)
                    .allowHardware(false)
                    .target(
                        onSuccess = { drawable ->
                            drawable.toBitmapOrNull(config = Bitmap.Config.ARGB_8888)
                                ?.softwareBitmapOrNull()
                                ?.let { brandLogoBitmaps[url] = it }
                            brandLogoLoadsInFlight.remove(url)
                            if (BuildConfig.DEBUG) {
                                Log.d("OttoCarMapMarkerBitmaps", "Presence brand logo loaded url=$url")
                            }
                            clearPresenceImages()
                            onPresenceImageChanged()
                        },
                        onError = {
                            brandLogoLoadsInFlight.remove(url)
                        },
                    )
                    .build()
            appContext.imageLoader.enqueue(request)
        }
    }

    private fun brandLogoBitmapFor(url: String?): Bitmap? {
        val key = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bitmap = brandLogoBitmaps[key] ?: return null
        val softwareBitmap = bitmap.softwareBitmapOrNull()
        if (softwareBitmap == null) {
            brandLogoBitmaps.remove(key)
            return null
        }
        if (softwareBitmap !== bitmap) {
            brandLogoBitmaps[key] = softwareBitmap
        }
        return softwareBitmap
    }

    private fun Bitmap.softwareBitmapOrNull(): Bitmap? =
        if (config == Bitmap.Config.HARDWARE) {
            runCatching { copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        } else {
            this
        }

    private fun resolvedAvatarUrl(rawAvatar: String?): String? =
        rawAvatar
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }
            ?.takeIf { it.isNotBlank() }

    private fun clearPresenceImages() {
        bitmaps.keys
            .filter { it.startsWith("otto-car-presence-") }
            .forEach { bitmaps.remove(it) }
    }

    private fun drawBrandLogo(
        canvas: Canvas,
        logo: Bitmap,
        rect: RectF,
    ) {
        val drawableLogo = logo.softwareBitmapOrNull() ?: return
        val src = Rect(0, 0, drawableLogo.width, drawableLogo.height)
        runCatching {
            canvas.drawBitmap(drawableLogo, src, RectF(rect), Paint(Paint.ANTI_ALIAS_FLAG))
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w("OttoCarMapMarkerBitmaps", "Skipping brand logo draw config=${logo.config}", error)
            }
        }
    }

    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dest: RectF,
        cornerRadius: Float,
    ) {
        val src = centerCropSource(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.save()
        val path =
            Path().apply {
                addRoundRect(dest, cornerRadius, cornerRadius, Path.Direction.CW)
            }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, src, dest, paint)
        canvas.restore()
    }

    private fun centerCropSource(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        if (width == height) return Rect(0, 0, width, height)
        return if (width > height) {
            val left = (width - height) / 2
            Rect(left, 0, left + height, height)
        } else {
            val top = (height - width) / 2
            Rect(0, top, width, top + width)
        }
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val clamped = alpha.coerceIn(0f, 1f)
        return Color.argb((clamped * 255).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))
    }

    private fun hazardMarkerImage(type: String): MarkerImage =
        when (type.lowercase(Locale.US)) {
            "police" -> MarkerImage.HazardPolice
            "traffic" -> MarkerImage.HazardTraffic
            "crash" -> MarkerImage.HazardCrash
            else -> MarkerImage.HazardGeneric
        }

    private fun hazardColor(type: String): Int =
        when (type.lowercase(Locale.US)) {
            "police" -> 0xFF337AFF.toInt()
            "traffic" -> 0xFFFF8A1F.toInt()
            "crash" -> 0xFFF3332E.toInt()
            else -> 0xFFFFC71F.toInt()
        }

    private fun drawBadge(
        fill: Int,
        glyph: Int?,
        glyphColor: Int = Color.WHITE,
    ): Bitmap {
        val framePx = (44f * density).toInt().coerceAtLeast(36)
        val badgeRadius = 14f * density
        val bitmap = Bitmap.createBitmap(framePx, framePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = framePx / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(center, center + density, badgeRadius, shadow)
        canvas.drawCircle(center, center, badgeRadius, fillPaint)
        canvas.drawCircle(center, center, badgeRadius, strokePaint)

        if (glyph != null) {
            AppCompatResources.getDrawable(appContext, glyph)?.let { raw ->
                val icon = DrawableCompat.wrap(raw.mutate())
                DrawableCompat.setTint(icon, glyphColor)
                val iconPx = (18f * density).toInt().coerceAtLeast(14)
                val left = ((framePx - iconPx) / 2f).toInt()
                val top = ((framePx - iconPx) / 2f).toInt()
                icon.setBounds(left, top, left + iconPx, top + iconPx)
                icon.draw(canvas)
            }
        }
        return bitmap
    }

    internal enum class MarkerImage(
        val imageId: String,
    ) {
        SavedPlace("otto-car-marker-saved-place"),
        Event("otto-car-marker-event"),
        RaceTrack("otto-car-marker-race-track"),
        RouteStart("otto-car-marker-route-start"),
        RouteFinish("otto-car-marker-route-finish"),
        RouteCheckpoint("otto-car-marker-route-checkpoint"),
        RouteCheckpointPassed("otto-car-marker-route-checkpoint-passed"),
        RouteStop("otto-car-marker-route-stop"),
        RoutePoint("otto-car-marker-route-point"),
        PresenceSelf("otto-car-marker-presence-self"),
        PresencePeer("otto-car-marker-presence-peer"),
        HazardPolice("otto-car-marker-hazard-police"),
        HazardTraffic("otto-car-marker-hazard-traffic"),
        HazardCrash("otto-car-marker-hazard-crash"),
        HazardGeneric("otto-car-marker-hazard-generic"),
    }

    internal companion object {
        const val PROPERTY_ICON = "ottoIcon"
        const val PROPERTY_SORT = "ottoSort"
        const val PROPERTY_SIZE = "ottoSize"

        private val sharedAvatarBitmaps =
            object : LruCache<String, Bitmap>(AVATAR_BITMAP_CACHE_KB) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = (value.byteCount / 1024).coerceAtLeast(1)
            }

        fun hazardImageId(type: String): String =
            when (type.lowercase(Locale.US)) {
                "police" -> MarkerImage.HazardPolice.imageId
                "traffic" -> MarkerImage.HazardTraffic.imageId
                "crash" -> MarkerImage.HazardCrash.imageId
                else -> MarkerImage.HazardGeneric.imageId
            }

        private const val PIN_WIDTH_DP = 56f
        private const val PIN_HEIGHT_DP = 84f
        private const val ROUTE_PIN_WIDTH_DP = 56f
        private const val ROUTE_PIN_HEIGHT_DP = 84f
        private const val BADGE_DP = 48f
        private const val AVATAR_BITMAP_CACHE_KB = 12 * 1024
    }
}
