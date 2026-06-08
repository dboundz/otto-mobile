package to.ottomot.driftd

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import to.ottomot.driftd.ui.insets.OttoWindowInsets.fullscreenDialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.GarageCarDto

@Composable
internal fun DriveSummaryScreen(
    drive: DriveDto,
    isOwner: Boolean,
    garageCars: List<GarageCarDto>,
    lockedShareCircleId: String? = null,
    onDismiss: () -> Unit,
    onDriveUpdated: (DriveDto) -> Unit,
    onDriveDeleted: () -> Unit,
    onPresentShare: (DriveChatShareContext) -> Unit,
    onPatchGarageCar: suspend (String?) -> Result<DriveDto>,
    onRename: suspend (String) -> Result<DriveDto>,
    onDelete: suspend () -> Result<Unit>,
    onFetchPathSamples: suspend (String, String?) -> List<DrivePathSample> = { _, _ -> emptyList() },
    onShareToast: (String) -> Unit = {},
) {
    var current by remember(drive.id) { mutableStateOf(drive) }
    var pathSamples by remember(drive.id) { mutableStateOf<List<DrivePathSample>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var garageUpdating by remember { mutableStateOf(false) }
    var showTrailMap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val title = DriveDisplayNaming.listTitle(current)
    val flavor = driveFlavorFromIso(current.endTime ?: current.startTime)
    val timestamp = formatDriveCompletedAt(current.endTime ?: current.startTime)
    val driveSeconds = driveTimeSecondsBetween(current.startTime, current.endTime).toDouble()
    val canViewTrailOnMap =
        remember(current.id, current.pointsCount, current.route, pathSamples) {
            (current.pointsCount ?: 0) >= 2 || hasDriveRoutePreview(current, pathSamples)
        }

    LaunchedEffect(current.id, current.pointsCount, lockedShareCircleId) {
        pathSamples =
            if ((current.pointsCount ?: 0) >= 2) {
                onFetchPathSamples(current.id, lockedShareCircleId)
            } else {
                emptyList()
            }
    }

    fun presentShare() {
        if (!current.status.equals("completed", ignoreCase = true)) {
            onShareToast(ctx.getString(R.string.drive_share_only_completed))
            return
        }
        onPresentShare(
            DriveChatShareContext(
                driveId = current.id,
                previewTitle = title,
                previewDistanceMeters = current.distanceMeters ?: 0.0,
                previewDriveTimeSeconds = driveTimeSecondsBetween(current.startTime, current.endTime),
                previewCompletedAtIso = current.endTime ?: current.startTime,
                lockedCircleId = lockedShareCircleId,
                mapPreviewSnapshotInput = DriveMapPreviewSnapshotInput.fromRoute(current.route, pathSamples),
            ),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color.Black, Color(0xFF0D0E18), Color.Black))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryHeroCard(
                title = title,
                flavor = flavor,
                timestamp = timestamp,
                isOwner = isOwner,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onBack = onDismiss,
                onShare = {
                    if (isOwner) presentShare() else shareExternal(ctx, title, current, driveSeconds)
                },
                onRename = {
                    menuOpen = false
                    renameDraft = title
                    renameOpen = true
                },
                onDelete = {
                    menuOpen = false
                    deleteOpen = true
                },
            )

            DriveMetricsSection(current, driveSeconds)
            DriveRouteSection(
                drive = current,
                pathSamples = pathSamples,
                canOpenTrailMap = canViewTrailOnMap,
                onOpenTrailMap = { showTrailMap = true },
            )
            DriveVehicleSection(
                drive = current,
                isOwner = isOwner,
                garageCars = garageCars,
                updating = garageUpdating,
                onSelectCar = { carId ->
                    garageUpdating = true
                    scope.launch {
                        onPatchGarageCar(carId)
                            .onSuccess { updated ->
                                current = updated
                                onDriveUpdated(updated)
                            }
                            .onFailure {
                                onShareToast(ctx.getString(R.string.drive_garage_update_error))
                            }
                        garageUpdating = false
                    }
                },
            )
        }

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .ottoBottomSheetContent(),
            color = Color.Black.copy(alpha = 0.92f),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { if (canViewTrailOnMap) showTrailMap = true },
                    enabled = canViewTrailOnMap,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.drive_summary_view_on_map),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (canViewTrailOnMap) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                    )
                }
                if (isOwner) {
                    DriveSummaryShareIconButton(onClick = { presentShare() })
                }
            }
        }
    }

    if (showTrailMap) {
        Dialog(
            onDismissRequest = { showTrailMap = false },
            properties = fullscreenDialogProperties(dismissOnClickOutside = false),
        ) {
            DriveTrailMapScreen(
                drive = current,
                onClose = { showTrailMap = false },
                onFetchPathSamples = onFetchPathSamples,
                lockedShareCircleId = lockedShareCircleId,
            )
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { if (!renaming) renameOpen = false },
            title = { Text(stringResource(R.string.drive_summary_rename_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.drive_summary_rename_message))
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.drive_summary_rename_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = true
                        scope.launch {
                            onRename(renameDraft.trim())
                                .onSuccess { updated ->
                                    current = updated
                                    onDriveUpdated(updated)
                                    renameOpen = false
                                }
                                .onFailure {
                                    onShareToast(ctx.getString(R.string.drive_rename_error))
                                }
                            renaming = false
                        }
                    },
                    enabled = renameDraft.trim().isNotEmpty() && !renaming,
                ) { Text(stringResource(R.string.squad_edit_event_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }, enabled = !renaming) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteOpen = false },
            title = { Text(stringResource(R.string.drive_summary_delete_title)) },
            text = { Text(stringResource(R.string.drive_summary_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = true
                        scope.launch {
                            onDelete()
                                .onSuccess {
                                    onDriveDeleted()
                                    onDismiss()
                                }
                                .onFailure {
                                    onShareToast(ctx.getString(R.string.drive_delete_error))
                                }
                            deleting = false
                        }
                    },
                    enabled = !deleting,
                ) { Text(stringResource(R.string.drive_summary_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }, enabled = !deleting) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun DriveSummaryShareIconButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
    ) {
        Icon(
            Icons.Outlined.Share,
            contentDescription = stringResource(R.string.drive_summary_share_cd),
            tint = Color.White,
        )
    }
}

private fun shareExternal(ctx: android.content.Context, title: String, drive: DriveDto, seconds: Double) {
    val distance = formatDriveDistanceMiles(drive.distanceMeters)
    val duration = formatDriveDurationSeconds(seconds)
    val text = driveShareExternalText(title, distance, duration)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    ctx.startActivity(Intent.createChooser(intent, title))
}

@Composable
private fun SummaryHeroCard(
    title: String,
    flavor: DriveSummaryFlavor,
    timestamp: String,
    isOwner: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF07080E),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                ProfileDriveFlagBadge(size = 56.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(driveFlavorIconVector(flavor.iconKind), contentDescription = null, tint = Color(0xFF7B3DFF), modifier = Modifier.size(18.dp))
                    Text(flavor.label, color = Color(0xFF7B3DFF), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                }
                if (timestamp.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(timestamp, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.74f))
                }
                Row {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.drive_summary_share_cd), tint = Color.White.copy(alpha = 0.74f))
                    }
                    if (isOwner) {
                        Box {
                            IconButton(onClick = { onMenuOpenChange(true) }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.drive_summary_options_cd), tint = Color.White.copy(alpha = 0.74f))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.drive_summary_rename)) },
                                    onClick = onRename,
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.drive_summary_delete)) },
                                    onClick = onDelete,
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileDriveFlagBadge(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7B3DFF), Color(0xFF3D5AFE)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.DirectionsCar,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun DriveMetricsSection(drive: DriveDto, driveSeconds: Double) {
    ProfileSectionCard {
        Text(
            stringResource(R.string.drive_summary_driving_stats),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveStatTile(Icons.Outlined.AddRoad, formatDriveDistanceMiles(drive.distanceMeters), stringResource(R.string.drive_summary_stat_distance), Modifier.weight(1f))
                DriveStatTile(Icons.Outlined.Timer, formatDriveDurationSeconds(driveSeconds), stringResource(R.string.drive_summary_stat_drive_time), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveStatTile(Icons.Outlined.Speed, formatDriveAverageSpeedMph(drive), stringResource(R.string.drive_summary_stat_avg_pace), Modifier.weight(1f))
                DriveStatTile(Icons.Outlined.Route, "${drive.pointsCount ?: 0}", stringResource(R.string.drive_summary_stat_samples), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DriveRouteSection(
    drive: DriveDto,
    pathSamples: List<DrivePathSample>,
    canOpenTrailMap: Boolean,
    onOpenTrailMap: () -> Unit,
) {
    ProfileSectionCard {
        Text(
            stringResource(R.string.drive_summary_your_route),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        if (hasDriveRoutePreview(drive, pathSamples)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                DriveRouteMapPreviewFromDrive(
                    drive = drive,
                    pathSamples = pathSamples,
                    height = 220.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                if (canOpenTrailMap) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenTrailMap,
                            ),
                    )
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.drive_summary_route_unavailable), color = Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun DriveVehicleSection(
    drive: DriveDto,
    isOwner: Boolean,
    garageCars: List<GarageCarDto>,
    updating: Boolean,
    onSelectCar: (String?) -> Unit,
) {
    ProfileSectionCard {
        Text(stringResource(R.string.drive_summary_vehicle), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        Spacer(Modifier.height(12.dp))
        val car = drive.garageCar ?: garageCars.find { it.id == drive.garageCarId }
        if (car != null) {
            if (isOwner) {
                var carMenu by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().clickable { carMenu = true }) {
                        GarageCarCard(car = car, readOnly = true)
                    }
                    if (updating) {
                        CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                    DropdownMenu(expanded = carMenu, onDismissRequest = { carMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.drive_summary_no_car_selected)) },
                            onClick = { carMenu = false; onSelectCar(null) },
                        )
                        garageCars.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(garageCarLabel(g)) },
                                onClick = { carMenu = false; onSelectCar(g.id) },
                            )
                        }
                    }
                }
            } else {
                GarageCarCard(car = car, readOnly = true)
            }
        } else if (isOwner && garageCars.isNotEmpty()) {
            var carMenu by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { carMenu = true },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.055f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.drive_summary_choose_car),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            stringResource(R.string.drive_summary_choose_car_hint),
                            color = Color.White.copy(alpha = 0.56f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (updating) {
                    CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                DropdownMenu(expanded = carMenu, onDismissRequest = { carMenu = false }) {
                    garageCars.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(garageCarLabel(g)) },
                            onClick = { carMenu = false; onSelectCar(g.id) },
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.055f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(14.dp),
            ) {
                Text(
                    if (isOwner) stringResource(R.string.drive_summary_choose_car) else stringResource(R.string.drive_summary_no_car_peer),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    if (isOwner) stringResource(R.string.drive_summary_no_car_owner) else stringResource(R.string.drive_summary_no_car_peer),
                    color = Color.White.copy(alpha = 0.56f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun garageCarLabel(car: GarageCarDto): String {
    val nick = car.nickname?.trim().orEmpty()
    if (nick.isNotEmpty()) return nick
    val year = car.year?.toString().orEmpty()
    return listOf(year, car.make, car.model).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun DriveStatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF7B3DFF), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        Text(label, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ProfileSectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        content()
    }
}