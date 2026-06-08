package to.ottomot.driftd

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.Instant
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto

/**
 * Avatar / map marker dot semantics (iOS parity):
 * - **Foreground (green)**: fresh heartbeat with `inApp != false`
 * - **Background (yellow / amber)**: explicit `inApp == false`
 * - **Offline / unknown (gray)**: missing row, stale payload, or no usable `updatedAt`
 */
object PresenceLifecycleDotColors {
    val Foreground: Color = Color(0xFF43A047)
    val Background: Color = Color(0xFFFBBF24)
    val Offline: Color = Color(0xFF9E9E9E)

    internal const val STALE_AFTER_SECONDS: Long = 150
}

/**
 * Dot color from a live [PresenceMemberDto] (map, merged presence lists, profile sheet).
 */
fun presenceLifecycleDotColor(
    member: PresenceMemberDto,
    now: Instant = Instant.now(),
): Color {
    if (member.inApp == false) return PresenceLifecycleDotColors.Background
    if (presencePayloadStale(member.updatedAt, now)) return PresenceLifecycleDotColors.Offline
    return PresenceLifecycleDotColors.Foreground
}

/**
 * Resolves a dot for a [UserDto] id using squad + public presence maps (event crew, phone cards, etc.).
 * Current user is always **foreground** while viewing the app.
 */
fun userPresenceLifecycleDotColor(
    userId: String,
    meUserId: String?,
    circles: List<CircleDto>,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
    now: Instant = Instant.now(),
): Color {
    if (meUserId != null && ottoUserIdsEqual(userId, meUserId)) {
        return PresenceLifecycleDotColors.Foreground
    }
    for (circle in circles) {
        val cid = circle.id.trim()
        if (cid.isEmpty()) continue
        val row =
            presenceMembersByCircleId[cid]?.firstOrNull { m ->
                ottoUserIdsEqual(m.userId, userId)
            }
        if (row != null) return presenceLifecycleDotColor(row, now)
    }
    val publicId = OttoShellUiState.PublicPresenceChannelId
    val pubRow =
        presenceMembersByCircleId[publicId]?.firstOrNull { m ->
            ottoUserIdsEqual(m.userId, userId)
        }
    if (pubRow != null) return presenceLifecycleDotColor(pubRow, now)
    return PresenceLifecycleDotColors.Offline
}

private fun parsePresenceInstant(raw: String?): Instant? =
    raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun presencePayloadStale(
    updatedAt: String?,
    now: Instant,
    maxAgeSeconds: Long = PresenceLifecycleDotColors.STALE_AFTER_SECONDS,
): Boolean {
    if (updatedAt.isNullOrBlank()) return true
    val inst = parsePresenceInstant(updatedAt) ?: return true
    val secs = Duration.between(inst, now).seconds
    if (secs < 0) return false
    return secs > maxAgeSeconds
}
