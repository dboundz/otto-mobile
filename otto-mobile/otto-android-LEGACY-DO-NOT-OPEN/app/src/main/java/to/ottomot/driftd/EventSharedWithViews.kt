package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.EventAttachedSquadDto
import to.ottomot.driftd.ui.components.SquadShareListRow
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import kotlin.math.abs

private object EventSharedWithPresentation {
    val avatarSize = 52.dp
    val avatarOverlap = (-12).dp
    const val maxVisibleAvatars = 4

    fun squadInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
        return letters.joinToString("").ifEmpty { "?" }
    }

    fun resolvedPhotoURL(
        squad: EventAttachedSquadDto,
        circles: List<CircleDto>,
    ): String? {
        circles.firstOrNull { it.id == squad.id }?.photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        return squad.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun memberCount(
        squad: EventAttachedSquadDto,
        circles: List<CircleDto>,
    ): Int = circles.firstOrNull { it.id == squad.id }?.members?.size ?: 0

    fun totalMemberCount(
        squads: List<EventAttachedSquadDto>,
        circles: List<CircleDto>,
    ): Int = squads.sumOf { memberCount(it, circles) }

    fun accentColor(
        squadId: String,
    ): Color {
        val hash = abs(squadId.hashCode())
        val palette =
            listOf(
                Color(0xFF8B5CF6),
                Color(0xFF3B82F6),
                Color(0xFF10B981),
                Color(0xFFF59E0B),
            )
        return palette[hash % palette.size]
    }
}

@Composable
private fun EventSharedSquadAvatar(
    squad: EventAttachedSquadDto,
    photoUrl: String?,
    accentColor: Color,
    size: Dp = EventSharedWithPresentation.avatarSize,
) {
    val ctx = LocalContext.current
    val photoResolved = photoUrl?.let { MediaUrlResolver.resolve(it)?.toString() }

    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
    ) {
        if (!photoResolved.isNullOrBlank()) {
            AsyncImage(
                model = ottoImageRequest(ctx, photoResolved),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    EventSharedWithPresentation.squadInitials(squad.name),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = maxOf(13f, size.value * 0.30f).sp,
                )
            }
        }
    }
}

@Composable
private fun EventSharedWithAvatarStack(
    squads: List<EventAttachedSquadDto>,
    circles: List<CircleDto>,
) {
    val visibleSquads = squads.take(EventSharedWithPresentation.maxVisibleAvatars)
    val overflowCount = (squads.size - EventSharedWithPresentation.maxVisibleAvatars).coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventSharedWithPresentation.avatarOverlap),
    ) {
        visibleSquads.forEach { squad ->
            EventSharedSquadAvatar(
                squad = squad,
                photoUrl = EventSharedWithPresentation.resolvedPhotoURL(squad, circles),
                accentColor = EventSharedWithPresentation.accentColor(squad.id),
            )
        }

        if (overflowCount > 0) {
            Box(
                modifier =
                    Modifier
                        .size(EventSharedWithPresentation.avatarSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(2.dp, Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+$overflowCount",
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun EventSharedWithModule(
    squads: List<EventAttachedSquadDto>,
    circles: List<CircleDto>,
    onTap: () -> Unit,
) {
    val totalMembers = EventSharedWithPresentation.totalMemberCount(squads, circles)
    val squadCountLabel =
        pluralStringResource(
            R.plurals.event_shared_with_squad_count,
            squads.size,
            squads.size,
        )
    val accessibilitySummary =
        buildString {
            append(squadCountLabel)
            if (totalMembers > 0) {
                append(", ")
                append(
                    pluralStringResource(
                        R.plurals.event_shared_with_member_count,
                        totalMembers,
                        totalMembers,
                    ),
                )
            }
        }
    val accessibilityLabel = stringResource(R.string.event_shared_with_accessibility_label)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .semantics {
                        contentDescription = "$accessibilityLabel. $accessibilitySummary"
                    }
                    .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color(0xFFAF52DE),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(R.string.event_shared_with_section_label),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventSharedWithAvatarStack(squads = squads, circles = circles)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        squadCountLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    if (totalMembers > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.event_shared_with_member_count,
                                totalMembers,
                                totalMembers,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.38f),
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventSharedWithSheet(
    squads: List<EventAttachedSquadDto>,
    circles: List<CircleDto>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sortedSquads =
        remember(squads) {
            squads.sortedBy { it.name.lowercase() }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .ottoBottomSheetContent()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.event_shared_with_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.event_shared_with_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
                modifier =
                    Modifier
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 14.dp),
            )

            sortedSquads.forEachIndexed { index, squad ->
                SquadShareListRow(
                    squadName = squad.name,
                    photoUrl = EventSharedWithPresentation.resolvedPhotoURL(squad, circles),
                    memberCount = EventSharedWithPresentation.memberCount(squad, circles),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    avatarSize = 48.dp,
                )
                if (index < sortedSquads.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}
