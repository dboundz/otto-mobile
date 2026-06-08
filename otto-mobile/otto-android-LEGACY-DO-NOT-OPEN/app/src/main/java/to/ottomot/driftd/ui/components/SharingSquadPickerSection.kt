package to.ottomot.driftd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.R
import to.ottomot.driftd.core.network.dto.CircleDto

@Composable
fun SharingSquadPickerSection(
    circles: List<CircleDto>,
    selectedCircleIds: Set<String>,
    onToggleCircle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.map_sharing_with_section).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.56f),
        )

        if (circles.isEmpty()) {
            Text(
                stringResource(R.string.map_sharing_no_squads_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                circles.forEach { circle ->
                    val circleId = circle.id?.trim().orEmpty()
                    if (circleId.isEmpty()) return@forEach
                    val selected = selectedCircleIds.contains(circleId)
                    Surface(
                        onClick = { onToggleCircle(circleId) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.055f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SquadShareListRow(
                            squadName = circle.name.orEmpty(),
                            photoUrl = circle.photoUrl,
                            memberCount = circle.members?.size ?: 0,
                            avatarSize = 44.dp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            trailingContent = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(30.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color =
                                            if (selected) {
                                                Color(0xFFAF52DE)
                                            } else {
                                                Color.Transparent
                                            },
                                        border =
                                            BorderStroke(
                                                1.dp,
                                                Color.White.copy(alpha = 0.18f),
                                            ),
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        if (selected) {
                                            Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
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
