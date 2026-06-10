package to.ottomot.driftd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.SavedPlaceDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.ui.components.OttoFullscreenDialog
import to.ottomot.driftd.ui.components.OttoFullscreenDarkTopAppBar
import to.ottomot.driftd.ui.components.OttoFullscreenScrollContent
import java.time.Instant
import java.util.Locale

internal const val PROFILE_LIST_PREVIEW_LIMIT = 3
internal val ProfileListRowMinHeight = 86.dp
private val ProfileListRowCornerRadius = 16.dp

private val ProfileListPurple = Color(0xFF7B3DFF)

internal fun sortedProfileDrives(drives: List<DriveDto>): List<DriveDto> =
    drives.sortedByDescending { profileDriveSortInstant(it) }

internal fun sortedProfileRoutes(routes: List<SavedRouteDto>): List<SavedRouteDto> =
    routes.sortedByDescending { profileRouteSortInstant(it) }

internal fun sortedProfilePlaces(places: List<SavedPlaceDto>): List<SavedPlaceDto> =
    places.sortedBy { it.name.trim().lowercase(Locale.US) }

private fun profileDriveSortInstant(drive: DriveDto): Instant =
    parseProfileSortInstant(drive.endTime) ?: parseProfileSortInstant(drive.startTime) ?: Instant.EPOCH

private fun profileRouteSortInstant(route: SavedRouteDto): Instant =
    parseProfileSortInstant(route.updatedAt) ?: parseProfileSortInstant(route.createdAt) ?: Instant.EPOCH

private fun parseProfileSortInstant(raw: String?): Instant? =
    runCatching { Instant.parse(raw?.trim().orEmpty()) }.getOrNull()

@Composable
internal fun ProfileListSectionHeader(
    title: String,
    count: Int,
    showViewAll: Boolean,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        if (showViewAll) {
            Text(
                text = stringResource(R.string.event_crew_view_all),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ProfileListPurple,
                modifier = Modifier.clickable(onClick = onViewAll),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ProfileListPurple,
            modifier =
                Modifier
                    .background(ProfileListPurple.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun ProfilePendingDriveInteractiveRow(
    archive: PendingDriveArchiveDto,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier) {
        ProfilePendingDriveListRowContent(
            archive = archive,
            onClick = onOpen,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuExpanded = true
            },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drive_pending_retry_save)) },
                onClick = {
                    menuExpanded = false
                    onRetry()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drive_pending_delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ProfilePendingDriveListRowContent(
    archive: PendingDriveArchiveDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ProfileListItemSurface(onClick = onClick, onLongClick = onLongClick) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9500).copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    archive.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    stringResource(R.string.drive_pending_not_saved_badge),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFF9500),
                    modifier =
                        Modifier
                            .background(Color(0xFFFF9500).copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                pendingDriveRowSubtitle(archive),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.42f),
        )
    }
}

@Composable
internal fun ProfileInteractiveDriveRow(
    drive: DriveDto,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier) {
        ProfileDriveListRowContent(
            drive = drive,
            onClick = onOpen,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuExpanded = true
            },
        )
        ProfileDriveContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onRename = onRename,
            onShare = onShare,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun CreateRouteListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileListItemSurface(onClick = onClick, onLongClick = onClick, modifier = modifier.fillMaxWidth()) {
        SavedRouteListIcon(style = SavedRouteListIconStyle.Create)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.profile_create_route),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.profile_create_route_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ProfileInteractiveRouteRow(
    route: SavedRouteDto,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier) {
        ProfileRouteListRowContent(
            route = route,
            onClick = onOpen,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuExpanded = true
            },
        )
        ProfileRouteContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onShare = onShare,
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ProfileDriveListRowContent(
    drive: DriveDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ProfileListItemSurface(onClick = onClick, onLongClick = onLongClick) {
        ProfileDriveFlagBadge(size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                DriveDisplayNaming.listTitle(drive),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                profileDriveRowSubtitle(drive),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ProfileRouteListRowContent(
    route: SavedRouteDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ProfileListItemSurface(onClick = onClick, onLongClick = onLongClick) {
        SavedRouteListIcon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                route.name.trim().takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.saved_route_detail_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                savedRouteSubtitle(route),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ProfileDriveContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.profile_list_rename)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.profile_list_share)) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onShare()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drive_summary_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
private fun ProfileRouteContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.profile_list_rename)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.route_chat_share_action)) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onShare()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drive_summary_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
internal fun ProfileDrivePreviewRow(
    drive: DriveDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileDriveListRowContent(drive = drive, onClick = onClick, onLongClick = onClick)
}

@Composable
internal fun ProfileRoutePreviewRow(
    route: SavedRouteDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileRouteListRowContent(route = route, onClick = onClick, onLongClick = onClick)
}

@Composable
private fun ProfileFullScreenListBody(
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        OttoFullscreenScrollContent(
            contentPadding = contentPadding,
            extraBottom = 12.dp,
            horizontalPadding = 16.dp,
            content = content,
        )
    }
}

@Composable
internal fun ProfileDrivesFullScreenList(
    pendingArchives: List<PendingDriveArchiveDto> = emptyList(),
    drives: List<DriveDto>,
    onDismiss: () -> Unit,
    onDeleteDrive: (String) -> Unit,
    onShareDrive: (DriveDto) -> Unit,
    onRenameDrive: suspend (DriveDto, String) -> Boolean,
    onRetryPendingDriveSave: (String) -> Unit = {},
    onDeletePendingDriveArchive: (String) -> Unit = {},
    driveDetailContent: @Composable (DriveDto, () -> Unit) -> Unit,
) {
    var selectedDrive by remember { mutableStateOf<DriveDto?>(null) }
    var drivePendingDelete by remember { mutableStateOf<DriveDto?>(null) }
    var driveRenameTarget by remember { mutableStateOf<DriveDto?>(null) }
    var driveRenameDraft by remember { mutableStateOf("") }
    var driveRenameSaving by remember { mutableStateOf(false) }
    var driveRenameError by remember { mutableStateOf<String?>(null) }
    var selectedPendingArchive by remember { mutableStateOf<PendingDriveArchiveDto?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        topBar = {
            OttoFullscreenDarkTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_my_drives_heading),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.progression_back_cd),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        ProfileFullScreenListBody(contentPadding) {
            items(pendingArchives, key = { "pending-${it.id}" }) { archive ->
                ProfilePendingDriveInteractiveRow(
                    archive = archive,
                    onOpen = { selectedPendingArchive = archive },
                    onRetry = { onRetryPendingDriveSave(archive.id) },
                    onDelete = { onDeletePendingDriveArchive(archive.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(drives, key = { it.id }) { drive ->
                ProfileInteractiveDriveRow(
                    drive = drive,
                    onOpen = { selectedDrive = drive },
                    onShare = { onShareDrive(drive) },
                    onRename = {
                        driveRenameTarget = drive
                        driveRenameDraft = DriveDisplayNaming.listTitle(drive)
                        driveRenameError = null
                    },
                    onDelete = { drivePendingDelete = drive },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    selectedDrive?.let { drive ->
        driveDetailContent(drive) { selectedDrive = null }
    }

    drivePendingDelete?.let { drive ->
        AlertDialog(
            onDismissRequest = { drivePendingDelete = null },
            title = { Text(stringResource(R.string.drive_summary_delete_title)) },
            text = { Text(stringResource(R.string.drive_summary_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDrive(drive.id)
                        drivePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.drive_summary_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { drivePendingDelete = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    driveRenameTarget?.let { drive ->
        AlertDialog(
            onDismissRequest = {
                if (!driveRenameSaving) {
                    driveRenameTarget = null
                    driveRenameError = null
                }
            },
            title = { Text(stringResource(R.string.drive_summary_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drive_summary_rename_message))
                    OutlinedTextField(
                        value = driveRenameDraft,
                        onValueChange = { driveRenameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.drive_summary_rename_hint)) },
                        singleLine = true,
                        enabled = !driveRenameSaving,
                    )
                    driveRenameError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = driveRenameDraft.trim()
                        if (draft.isEmpty() || driveRenameSaving) return@TextButton
                        scope.launch {
                            driveRenameSaving = true
                            driveRenameError = null
                            val ok = onRenameDrive(drive, draft)
                            driveRenameSaving = false
                            if (ok) {
                                driveRenameTarget = null
                            } else {
                                driveRenameError = ctx.getString(R.string.drive_rename_error)
                            }
                        }
                    },
                    enabled = driveRenameDraft.trim().isNotEmpty() && !driveRenameSaving,
                ) {
                    Text(
                        if (driveRenameSaving) {
                            stringResource(R.string.squad_edit_event_save)
                        } else {
                            stringResource(R.string.drive_summary_rename)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        driveRenameTarget = null
                        driveRenameError = null
                    },
                    enabled = !driveRenameSaving,
                ) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }
}

@Composable
internal fun ProfileRoutesFullScreenList(
    routes: List<SavedRouteDto>,
    onDismiss: () -> Unit,
    onCreateRoute: () -> Unit,
    onOpenRoute: (SavedRouteDto) -> Unit,
    onShareRoute: (SavedRouteDto) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onRenameRoute: suspend (SavedRouteDto, String) -> Boolean,
) {
    var routePendingDelete by remember { mutableStateOf<SavedRouteDto?>(null) }
    var routeRenameTarget by remember { mutableStateOf<SavedRouteDto?>(null) }
    var routeRenameDraft by remember { mutableStateOf("") }
    var routeRenameSaving by remember { mutableStateOf(false) }
    var routeRenameError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        topBar = {
            OttoFullscreenDarkTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_my_routes_heading),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.progression_back_cd),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        ProfileFullScreenListBody(contentPadding) {
            item(key = "create_route") {
                CreateRouteListRow(
                    onClick = {
                        onDismiss()
                        onCreateRoute()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
            items(routes, key = { it.id }) { route ->
                ProfileInteractiveRouteRow(
                    route = route,
                    onOpen = {
                        onDismiss()
                        onOpenRoute(route)
                    },
                    onShare = {
                        onDismiss()
                        onShareRoute(route)
                    },
                    onRename = {
                        routeRenameTarget = route
                        routeRenameDraft = route.name.trim()
                        routeRenameError = null
                    },
                    onDelete = { routePendingDelete = route },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    routePendingDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routePendingDelete = null },
            title = { Text(stringResource(R.string.profile_delete_route_title)) },
            text = { Text(stringResource(R.string.profile_delete_route_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRoute(route.id)
                        routePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.profile_delete_route_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { routePendingDelete = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }

    routeRenameTarget?.let { route ->
        AlertDialog(
            onDismissRequest = {
                if (!routeRenameSaving) {
                    routeRenameTarget = null
                    routeRenameError = null
                }
            },
            title = { Text(stringResource(R.string.profile_rename_route_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_rename_route_message))
                    OutlinedTextField(
                        value = routeRenameDraft,
                        onValueChange = { routeRenameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_rename_route_hint)) },
                        singleLine = true,
                        enabled = !routeRenameSaving,
                    )
                    routeRenameError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = routeRenameDraft.trim()
                        if (draft.isEmpty() || routeRenameSaving) return@TextButton
                        scope.launch {
                            routeRenameSaving = true
                            routeRenameError = null
                            val ok = onRenameRoute(route, draft)
                            routeRenameSaving = false
                            if (ok) {
                                routeRenameTarget = null
                            } else {
                                routeRenameError = ctx.getString(R.string.profile_rename_route_error)
                            }
                        }
                    },
                    enabled = routeRenameDraft.trim().isNotEmpty() && !routeRenameSaving,
                ) {
                    Text(stringResource(R.string.drive_summary_rename))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        routeRenameTarget = null
                        routeRenameError = null
                    },
                    enabled = !routeRenameSaving,
                ) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }
}

private val ProfileSavedPlaceTeal = Color(0xFF00A5AA)

@Composable
internal fun ProfilePlaceListRowContent(
    place: SavedPlaceDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileListItemSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ProfileSavedPlaceTeal.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profilePlaceSubtitle(place),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun profilePlaceSubtitle(place: SavedPlaceDto): String {
    place.addressSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    place.placeKind?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { c ->
        if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
    }?.let { return it }
    return String.format(Locale.US, "%.4f, %.4f", place.latitude, place.longitude)
}

@Composable
private fun ProfilePlaceContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.profile_list_rename)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.marker_detail_action_remove)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
internal fun ProfileInteractivePlaceRow(
    place: SavedPlaceDto,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier) {
        ProfilePlaceListRowContent(
            place = place,
            onClick = onOpen,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuExpanded = true
            },
        )
        ProfilePlaceContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun ProfilePlacesFullScreenList(
    places: List<SavedPlaceDto>,
    onDismiss: () -> Unit,
    onOpenPlace: (SavedPlaceDto) -> Unit,
    onDeletePlace: (String) -> Unit,
    onRenamePlace: (String, String) -> Unit,
) {
    var placePendingDelete by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var placeRenameTarget by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var placeRenameDraft by remember { mutableStateOf("") }

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        topBar = {
            OttoFullscreenDarkTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_my_places_heading),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.progression_back_cd),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        ProfileFullScreenListBody(contentPadding) {
            items(places, key = { it.id }) { place ->
                ProfileInteractivePlaceRow(
                    place = place,
                    onOpen = {
                        onDismiss()
                        onOpenPlace(place)
                    },
                    onRename = {
                        placeRenameTarget = place
                        placeRenameDraft = place.name
                    },
                    onDelete = { placePendingDelete = place },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    placePendingDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { placePendingDelete = null },
            title = { Text(stringResource(R.string.marker_detail_delete_place_title)) },
            text = { Text(place.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlace(place.id)
                        placePendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.marker_detail_action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { placePendingDelete = null }) {
                    Text(stringResource(R.string.marker_detail_cancel))
                }
            },
        )
    }

    placeRenameTarget?.let { place ->
        AlertDialog(
            onDismissRequest = { placeRenameTarget = null },
            title = { Text(stringResource(R.string.profile_list_rename)) },
            text = {
                OutlinedTextField(
                    value = placeRenameDraft,
                    onValueChange = { placeRenameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val draft = placeRenameDraft.trim()
                        if (draft.isNotEmpty()) {
                            onRenamePlace(place.id, draft)
                            placeRenameTarget = null
                        }
                    },
                    enabled = placeRenameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.drive_summary_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { placeRenameTarget = null }) {
                    Text(stringResource(R.string.event_detail_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileListItemSurface(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ProfileListRowMinHeight)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(ProfileListRowCornerRadius),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
