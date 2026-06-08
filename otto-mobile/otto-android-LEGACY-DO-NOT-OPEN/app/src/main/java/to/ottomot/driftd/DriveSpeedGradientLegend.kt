package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DriveSpeedGradientLegend(
    modifier: Modifier = Modifier,
) {
    val ticks = listOf(0, 50, 100, 150, 200)
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Speed color scale from 0 to 200 plus miles per hour"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DriveSpeedGradient.legendBrush())
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { mph ->
                Text(
                    text =
                        if (mph >= 200) {
                            stringResource(R.string.drive_speed_legend_max_mph)
                        } else {
                            stringResource(R.string.drive_speed_legend_mph, mph)
                        },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
