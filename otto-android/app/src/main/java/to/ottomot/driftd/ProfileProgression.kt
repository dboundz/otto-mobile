package to.ottomot.driftd

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import to.ottomot.driftd.ui.components.OttoFullscreenScrollColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.ottomot.driftd.core.network.dto.ProfileProgressionDto
import kotlin.math.max

internal data class ProfileTierPalette(
    val accent: Color,
    val accentDeep: Color,
)

/** Neutral chrome when the viewer cannot see this user’s real progression tier. */
internal fun profileTierPaletteHiddenDriveStats(): ProfileTierPalette =
    ProfileTierPalette(
        accent = Color.White.copy(alpha = 0.55f),
        accentDeep = Color.White.copy(alpha = 0.30f),
    )

internal fun profileTierPalette(tierId: String?): ProfileTierPalette {
    return when (tierId?.lowercase()) {
        "rookie" ->
            ProfileTierPalette(
                accent = Color(0xFFC97A46),
                accentDeep = Color(0xFF7A3F18),
            )
        "qualifier" ->
            ProfileTierPalette(
                accent = Color(0xFFC8CCD2),
                accentDeep = Color(0xFF5F6B7A),
            )
        "runner" ->
            ProfileTierPalette(
                accent = Color(0xFFFFE066),
                accentDeep = Color(0xFFB8860B),
            )
        "pacer" ->
            ProfileTierPalette(
                accent = Color(0xFF2EB5FF),
                accentDeep = Color(0xFF0066CC),
            )
        "apex" ->
            ProfileTierPalette(
                accent = Color(0xFFB975FF),
                accentDeep = Color(0xFF6B2ECC),
            )
        "legend" ->
            ProfileTierPalette(
                accent = Color(0xFFFF5F94),
                accentDeep = Color(0xFFFF7A33),
            )
        else ->
            ProfileTierPalette(
                accent = Color(0xFF9C27B0),
                accentDeep = Color(0xFF5E137A),
            )
    }
}

internal fun profileTierComposeColor(tierId: String?): Color = profileTierPalette(tierId).accent

internal fun profileTierAvatarRingBrush(tierId: String?): Brush {
    return when (tierId?.lowercase()) {
        "legend" ->
            Brush.linearGradient(
                colors =
                    listOf(
                        Color(0xFFFF5FA2),
                        Color(0xFFFF6633),
                        Color(0xFFFFD966),
                        Color(0xFFFF3D8F),
                    ),
                start = Offset(0f, 0f),
                end = Offset(180f, 180f),
            )
        else -> {
            val p = profileTierPalette(tierId)
            Brush.linearGradient(
                colors = listOf(p.accent, p.accentDeep, Color.White.copy(alpha = 0.28f)),
                start = Offset(0f, 0f),
                end = Offset(140f, 140f),
            )
        }
    }
}

/** Premium profile XP bar; [progress] in 0f..1f. */
@Composable
internal fun PremiumProfileXpBar(
    progress: Float,
    tierId: String?,
    modifier: Modifier = Modifier,
) {
    val coerced = progress.coerceIn(0f, 1f)
    val brush =
        if (tierId?.lowercase() == "legend") {
            Brush.horizontalGradient(
                listOf(Color(0xFFFF5F94), Color(0xFFFF8A4A), Color(0xFFFFE08A)),
            )
        } else {
            val p = profileTierPalette(tierId)
            Brush.horizontalGradient(listOf(p.accent, p.accentDeep))
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.58f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(coerced)
                .background(brush),
        ) {
            if (coerced > 0.05f) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 3.dp)
                        .width(5.dp)
                        .fillMaxHeight(0.62f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@DrawableRes
internal fun progressionLevelBadgeRes(level: Int?): Int {
    val lv = (level ?: 1).coerceIn(1, 20)
    return when (lv) {
        1 -> R.drawable.progression_level_1
        2 -> R.drawable.progression_level_2
        3 -> R.drawable.progression_level_3
        4 -> R.drawable.progression_level_4
        5 -> R.drawable.progression_level_5
        6 -> R.drawable.progression_level_6
        7 -> R.drawable.progression_level_7
        8 -> R.drawable.progression_level_8
        9 -> R.drawable.progression_level_9
        10 -> R.drawable.progression_level_10
        11 -> R.drawable.progression_level_11
        12 -> R.drawable.progression_level_12
        13 -> R.drawable.progression_level_13
        14 -> R.drawable.progression_level_14
        15 -> R.drawable.progression_level_15
        16 -> R.drawable.progression_level_16
        17 -> R.drawable.progression_level_17
        18 -> R.drawable.progression_level_18
        19 -> R.drawable.progression_level_19
        else -> R.drawable.progression_level_20
    }
}

private fun progressionRomanNumeral(value: Int): String =
    when (value) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        else -> value.toString()
    }

/** Matches iOS `profileTierOrdinalText`. */
internal fun profileProgressionOrdinalLabel(pr: ProfileProgressionDto): String {
    val tierName = pr.tierName?.trim().orEmpty()
    val level = pr.level ?: 1
    if (pr.isMaxLevel == true) {
        return tierName.ifEmpty { "Legend" }
    }
    val tierStart =
        when (pr.tierId?.lowercase()) {
            "rookie" -> 1
            "qualifier" -> 5
            "runner" -> 9
            "pacer" -> 13
            "apex" -> 17
            else -> level
        }
    val ordinal = max(1, level - tierStart + 1)
    val roman = progressionRomanNumeral(ordinal)
    return if (tierName.isNotEmpty()) "$tierName $roman" else "Level $level"
}

internal fun profileProgressionPointsCaption(pr: ProfileProgressionDto): String {
    val pts = pr.points ?: 0
    if (pr.isMaxLevel == true) {
        return "$pts points · Final unlock"
    }
    val next = pr.nextLevelAt
    return if (next != null && next > 0) {
        "$pts / $next points"
    } else {
        "$pts points"
    }
}

private data class ProgressionTierInfo(
    val id: String,
    val name: String,
    val levelsText: String,
    val pointsPerLevelText: String,
    val totalTierPointsText: String?,
    val badgeLevel: Int,
)

@Composable
internal fun ProgressionTiersFullScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onPreviewLevelUp: ((Int) -> Unit)? = null,
    onSchedulePreviewNotification: (() -> Unit)? = null,
) {
    val tiers =
        remember {
            listOf(
                ProgressionTierInfo(
                    id = "rookie",
                    name = "Rookie",
                    levelsText = "Levels 1–4",
                    pointsPerLevelText = "250 XP per level",
                    totalTierPointsText = "1,000 total",
                    badgeLevel = 1,
                ),
                ProgressionTierInfo(
                    id = "qualifier",
                    name = "Qualifier",
                    levelsText = "Levels 5–8",
                    pointsPerLevelText = "500 XP per level",
                    totalTierPointsText = "2,000 total",
                    badgeLevel = 5,
                ),
                ProgressionTierInfo(
                    id = "runner",
                    name = "Runner",
                    levelsText = "Levels 9–12",
                    pointsPerLevelText = "1,000 XP per level",
                    totalTierPointsText = "4,000 total",
                    badgeLevel = 9,
                ),
                ProgressionTierInfo(
                    id = "pacer",
                    name = "Pacer",
                    levelsText = "Levels 13–16",
                    pointsPerLevelText = "2,000 XP per level",
                    totalTierPointsText = "8,000 total",
                    badgeLevel = 13,
                ),
                ProgressionTierInfo(
                    id = "apex",
                    name = "Apex",
                    levelsText = "Levels 17–19",
                    pointsPerLevelText = "4,000 XP per level",
                    totalTierPointsText = "12,000 total",
                    badgeLevel = 17,
                ),
                ProgressionTierInfo(
                    id = "legend",
                    name = "Legend",
                    levelsText = "Level 20",
                    pointsPerLevelText = "Final unlock",
                    totalTierPointsText = null,
                    badgeLevel = 20,
                ),
            )
        }

    val gradient =
        Brush.verticalGradient(
            colors =
                listOf(
                    Color(red = 0.01f, green = 0.01f, blue = 0.05f),
                    Color(red = 0.04f, green = 0.03f, blue = 0.12f),
                    Color.Black,
                ),
        )

    Box(
        Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        OttoFullscreenScrollColumn(
            contentPadding = contentPadding,
            horizontalPadding = 22.dp,
            extraBottom = 34.dp,
        ) {
            Spacer(Modifier.height(20.dp))
            ProgressionTiersHeader()
            Spacer(Modifier.height(10.dp))
            tiers.forEach { tier ->
                ProgressionTierCard(tier = tier)
                Spacer(Modifier.height(10.dp))
            }
            ProgressionHowItWorksCard()
            if (BuildConfig.DEBUG && onPreviewLevelUp != null && onSchedulePreviewNotification != null) {
                Spacer(Modifier.height(10.dp))
                ProgressionPreviewToolsCard(
                    onPreviewLevelUp = onPreviewLevelUp,
                    onSchedulePreviewNotification = onSchedulePreviewNotification,
                )
            }
        }
    }
}

@Composable
private fun ProgressionPreviewToolsCard(
    onPreviewLevelUp: (Int) -> Unit,
    onSchedulePreviewNotification: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFFEB3B).copy(alpha = 0.08f))
                .border(1.dp, Color(0xFFFFEB3B).copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Build,
                contentDescription = null,
                tint = Color(0xFFFFEB3B),
            )
            Text(
                "Temporary preview tools",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        Text(
            "Use these to preview the level-up modal and notification tap flow. Remove before release.",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.58f),
        )
        Text(
            "SHOW MODAL",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                ),
            color = Color.White.copy(alpha = 0.52f),
        )
        ProfileProgressionPreview.previewOptions.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { onPreviewLevelUp(option.level) }
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
                if (rowOptions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onSchedulePreviewNotification)
                    .padding(vertical = 12.dp, horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = Color.White,
                )
                Text(
                    "Send Apex I test notification",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProgressionTiersHeader() {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.progression_title),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                ),
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.progression_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun ProgressionTierCard(tier: ProgressionTierInfo) {
    val tierColor = profileTierComposeColor(tier.id)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(tierColor.copy(alpha = 0.16f), Color.Transparent),
                            ),
                    )
                }
                .border(1.dp, tierColor.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                .padding(start = 6.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = 4.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(tierColor),
        )
        Image(
            painter = painterResource(progressionLevelBadgeRes(tier.badgeLevel)),
            contentDescription = null,
            modifier =
                Modifier
                    .size(58.dp),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f)) {
            Text(
                tier.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tierColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tier.levelsText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color.White.copy(alpha = 0.13f)),
        )
        Column(
            modifier = Modifier.width(126.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                tier.pointsPerLevelText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = tierColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (tier.totalTierPointsText == null) "Status" else "Earning",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 8.sp,
                        letterSpacing = 0.8.sp,
                    ),
                color = Color.White.copy(alpha = 0.38f),
            )
            tier.totalTierPointsText?.let { total ->
                Text(
                    total,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class ProgressionPointRule(
    @androidx.annotation.StringRes val labelRes: Int,
    val points: Int,
)

private val progressionPointRules =
    listOf(
        ProgressionPointRule(R.string.progression_points_daily_launch, 20),
        ProgressionPointRule(R.string.progression_points_daily_squad_location_share, 40),
        ProgressionPointRule(R.string.progression_points_daily_first_chat_message, 20),
        ProgressionPointRule(R.string.progression_points_event_check_in_public, 100),
        ProgressionPointRule(R.string.progression_points_event_check_in_circle, 20),
        ProgressionPointRule(R.string.progression_points_signup_invite_redeemed, 150),
    )

@Composable
private fun ProgressionHowItWorksCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9C27B0).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFF9C27B0).copy(alpha = 0.62f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0).copy(alpha = 0.95f),
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.progression_how_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.progression_how_body),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            progressionPointRules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        stringResource(rule.labelRes),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Text(
                        stringResource(R.string.progression_points_format, rule.points),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF9C27B0).copy(alpha = 0.95f),
                    )
                }
            }
        }
    }
}
