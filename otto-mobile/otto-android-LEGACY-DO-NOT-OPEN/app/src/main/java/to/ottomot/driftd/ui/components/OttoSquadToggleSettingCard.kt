package to.ottomot.driftd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.core.network.dto.CircleDto

private val OttoTogglePurple = Color(0xFFAF52DE)

@Composable
fun OttoSquadToggleSettingCard(
    circle: CircleDto,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    OttoSquadToggleSettingCard(
        squadName = circle.name,
        photoUrl = circle.photoUrl,
        memberCount = circle.members?.size ?: 0,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        trailingText = trailingText,
        enabled = enabled,
    )
}

@Composable
fun OttoSquadToggleSettingCard(
    squadName: String,
    photoUrl: String?,
    memberCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SquadShareListRow(
                squadName = squadName,
                photoUrl = photoUrl,
                memberCount = memberCount,
                modifier = Modifier.weight(1f),
            )
            if (!trailingText.isNullOrBlank()) {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = OttoTogglePurple,
                        checkedThumbColor = Color.White,
                        checkedBorderColor = OttoTogglePurple,
                    ),
            )
        }
    }
}
