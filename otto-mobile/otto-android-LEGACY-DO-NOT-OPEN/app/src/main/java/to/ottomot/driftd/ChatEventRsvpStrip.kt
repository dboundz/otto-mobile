package to.ottomot.driftd

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import to.ottomot.driftd.core.event.eventCheckInEndsAtInstant
import to.ottomot.driftd.core.network.dto.EventDto

private data class ChatRsvpSeg(
    val status: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val chatRsvpSegments =
    listOf(
        ChatRsvpSeg(OttoShellUiState.RsvpGoing, R.string.event_rsvp_going, Icons.Filled.Check),
        ChatRsvpSeg(OttoShellUiState.RsvpInterested, R.string.event_rsvp_interested, Icons.AutoMirrored.Outlined.HelpOutline),
        ChatRsvpSeg(OttoShellUiState.RsvpNotGoing, R.string.event_rsvp_cant_go, Icons.Outlined.Cancel),
    )

internal fun chatEventRsvpInteractionsEnabled(event: EventDto): Boolean {
    val end = eventCheckInEndsAtInstant(event)
    return end == null || !Instant.now().isAfter(end)
}

private fun chatRsvpCount(event: EventDto, status: String): Int {
    val c = event.rsvpCounts
    return when (status) {
        OttoShellUiState.RsvpGoing -> c?.going ?: 0
        OttoShellUiState.RsvpInterested -> c?.interested ?: 0
        OttoShellUiState.RsvpNotGoing -> c?.notGoing ?: 0
        else -> 0
    }
}

@Composable
internal fun ChatEventRsvpStrip(
    event: EventDto,
    eventRsvpSubmittingEventId: String?,
    onRsvp: (eventId: String, status: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = chatEventRsvpInteractionsEnabled(event)
    val submitting =
        eventRsvpSubmittingEventId != null &&
            event.id.trim().equals(eventRsvpSubmittingEventId.trim(), ignoreCase = true)
    val view = LocalView.current
    val green = Color(0xFF34C759)
    val amber = Color(0xFFFFCC00)
    val red = Color(0xFFFF453A)
    val mutedCount =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (seg in chatRsvpSegments) {
            val selected = event.currentUserRsvp?.equals(seg.status, ignoreCase = true) == true
            val count = chatRsvpCount(event, seg.status)
            val tint =
                when (seg.status) {
                    OttoShellUiState.RsvpGoing -> if (selected) Color.White else green
                    OttoShellUiState.RsvpInterested -> if (selected) Color.White else amber
                    OttoShellUiState.RsvpNotGoing -> if (selected) Color.White else red
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clickable(
                            enabled = interactions && !submitting,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onRsvp(event.id, seg.status)
                            },
                        ),
                shape = RoundedCornerShape(8.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                    },
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (selected) 0.22f else 0.14f),
                    ),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(seg.icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(seg.labelRes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color =
                            if (selected) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                        color =
                            if (selected) {
                                Color.White.copy(alpha = 0.72f)
                            } else {
                                mutedCount
                            },
                    )
                }
            }
        }
    }
}
