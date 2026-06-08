package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.DirectConversationDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.ui.components.SquadShareListRow
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapMarkerShareToChatSheet(
    sheetState: SheetState,
    payload: MapMarkerSharePayload,
    circlesAvailable: List<CircleDto>,
    contacts: List<UserDto>,
    dmContactsForShare: List<UserDto>,
    dmConversations: List<DirectConversationDto>,
    meUser: UserDto?,
    circlesForDmSubtitle: List<CircleDto>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPost: (List<String>, List<String>, String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val myUserId = meUser?.id?.trim()?.takeIf { it.isNotEmpty() }
        val isPlaceShare = payload.previewKind == MapMarkerSharePreviewKind.SavedPlace

        var message by remember(payload.externalShareText) { mutableStateOf("") }
        var selectedCircleId by remember(payload.externalShareText) { mutableStateOf<String?>(null) }
        var dmPick by remember(payload.externalShareText) { mutableStateOf<Set<String>>(emptySet()) }

        var shareRecipientTabIdx by rememberSaveable(payload.externalShareText, "mkShareTab") { mutableStateOf(0) }
        val tabIdx = shareRecipientTabIdx.coerceIn(0, 1)

        val shareDmConversationRows = remember(dmContactsForShare) { dmContactsForShare }

        Column(
            Modifier
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.event_share_sheet_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(10.dp))
            MapMarkerSharePreviewCard(payload)
            Spacer(Modifier.height(12.dp))

            OttoIosUnderlineTabBar(
                labelResIds =
                    listOf(
                        R.string.squads_subtab_squads,
                        R.string.squads_subtab_dms,
                    ),
                selectedIdx = tabIdx,
                onSelect = { shareRecipientTabIdx = it },
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
            ) {
                when (tabIdx) {
                    0 -> {
                        item {
                            if (circlesAvailable.isEmpty()) {
                                OttoEmptyState(
                                    title = stringResource(R.string.event_share_sheet_need_squad),
                                    icon = Icons.Outlined.Groups,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 160.dp),
                                )
                            }
                        }
                        items(items = circlesAvailable, key = { it.id }) { c ->
                            val sel = selectedCircleId == c.id
                            ElevatedCard(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 6.dp)
                                        .clickable(enabled = !busy) {
                                            selectedCircleId = c.id
                                        },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SquadShareListRow(
                                        squadName = c.name,
                                        photoUrl = c.photoUrl,
                                        memberCount = c.members?.size ?: 0,
                                        modifier = Modifier.weight(1f),
                                    )
                                    androidx.compose.material3.RadioButton(
                                        selected = sel,
                                        onClick = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            if (dmContactsForShare.isEmpty()) {
                                OttoEmptyState(
                                    title = stringResource(R.string.messages_conversations_empty),
                                    icon = Icons.Outlined.Groups,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 160.dp),
                                )
                            }
                        }
                        items(items = shareDmConversationRows, key = { it.id }) { user ->
                            val peerId = user.id.trim()
                            val sel = dmPick.contains(peerId)
                            val name =
                                user.displayName.trim().takeIf { it.isNotEmpty() }
                                    ?: peerId
                            ElevatedCard(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 6.dp)
                                        .clickable(enabled = !busy && peerId.isNotEmpty()) {
                                            dmPick =
                                                if (sel) dmPick - peerId else dmPick + peerId
                                        },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                                    Checkbox(checked = sel, onCheckedChange = null)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = message,
                enabled = !busy,
                onValueChange = { if (it.length <= 500) message = it },
                placeholder = { Text(stringResource(R.string.event_share_sheet_hint)) },
                minLines = 3,
            )
            Spacer(Modifier.height(14.dp))

            val canPost =
                (isPlaceShare || message.trim().isNotEmpty()) &&
                    (selectedCircleId != null || dmPick.isNotEmpty()) &&
                    !busy

            Button(
                onClick = {
                    onPost(listOfNotNull(selectedCircleId), dmPick.toList(), message)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canPost,
            ) {
                Text(stringResource(R.string.event_share_post))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MapMarkerSharePreviewCard(payload: MapMarkerSharePayload) {
    val accent =
        when (payload.previewKind) {
            MapMarkerSharePreviewKind.SavedPlace -> Color(0xFF00A5AA)
            MapMarkerSharePreviewKind.RaceTrack -> Color(0xFFFFA658)
        }
    val context = LocalContext.current
    val lat = payload.latitude
    val lng = payload.longitude
    val shouldLoadMapPreview =
        lat != null &&
            lng != null &&
            payload.previewKind == MapMarkerSharePreviewKind.SavedPlace
    var previewBitmap by remember(lat, lng, payload.previewKind) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(lat, lng, payload.previewKind) {
        previewBitmap = null
        if (!shouldLoadMapPreview) return@LaunchedEffect
        val jpegBytes =
            PlaceMapSnapshotGenerator.jpegBytes(
                lat!!,
                lng!!,
                BuildConfig.MAPBOX_ACCESS_TOKEN,
                context.resources,
            )
        previewBitmap =
            jpegBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (shouldLoadMapPreview) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.map_point_saved),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!shouldLoadMapPreview) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when (payload.previewKind) {
                        MapMarkerSharePreviewKind.SavedPlace ->
                            Image(
                                painter = painterResource(R.drawable.map_point_saved),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        MapMarkerSharePreviewKind.RaceTrack ->
                            Icon(
                                painter = painterResource(R.drawable.map_point_track),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(28.dp),
                            )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    payload.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                payload.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { subtitle ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.72f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
