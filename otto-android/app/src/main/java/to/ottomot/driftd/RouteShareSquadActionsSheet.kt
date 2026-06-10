package to.ottomot.driftd

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.ui.components.ShareSheetActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteShareSquadActionsSheet(
    sheetState: SheetState,
    route: SavedRouteDto,
    onDismiss: () -> Unit,
    onShareToChat: () -> Unit,
    canShare: Boolean = true,
) {
    val ctx = LocalContext.current
    val title = route.name.trim().ifEmpty { stringResource(R.string.saved_route_detail_title) }
    val meta = routeSharePreviewMeta(route)
    val shareText = routeShareExternalText(route)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.route_share_actions_title),
                style = MaterialTheme.typography.titleLarge,
            )

            RouteSharePreviewCard(title = title, meta = meta)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.route_share_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canShare) {
                    ShareSheetActionRow(
                        title = stringResource(R.string.route_share_to_squad_chat),
                        subtitle = stringResource(R.string.route_share_to_squad_chat_subtitle),
                        icon = Icons.Outlined.Forum,
                        onClick = {
                            onDismiss()
                            onShareToChat()
                        },
                    )
                    ShareSheetActionRow(
                        title = stringResource(R.string.route_share_external),
                        subtitle = stringResource(R.string.route_share_external_subtitle),
                        icon = Icons.Outlined.Share,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                            ctx.startActivity(Intent.createChooser(intent, title))
                        },
                    )
                } else {
                    Text(
                        stringResource(R.string.route_share_owner_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        }
    }
}

@Composable
internal fun RouteSharePreviewCard(title: String, meta: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color(0xFF7B3DFF).copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.035f),
                        ),
                    ),
                )
                .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF7B3DFF).copy(alpha = 0.18f))
                    .padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.Route,
                contentDescription = null,
                tint = Color(0xFF7B3DFF),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        }
    }
}
