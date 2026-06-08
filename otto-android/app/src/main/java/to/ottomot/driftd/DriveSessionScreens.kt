package to.ottomot.driftd

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.outlined.LocationOn
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.util.Locale
import to.ottomot.driftd.core.network.dto.GarageCarDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import to.ottomot.driftd.ui.components.OttoToggleSettingCard
import to.ottomot.driftd.ui.components.SharingSquadPickerSection

@Composable
fun MapSheetHeader(
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    doneLabel: String = "Done",
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            TextButton(
                onClick = onDone,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    doneLabel,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
fun DriveSessionStatusPill(
    presentation: DriveSessionPillPresentation,
    onTap: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionIsActive = presentation !is DriveSessionPillPresentation.Idle
    val pillBorderColor = presentation.pillBorderColor()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .shadow(12.dp, CircleShape, ambientColor = pillBorderColor.copy(alpha = 0.18f))
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable(onClick = onTap)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DriveSessionStatusDots(presentation)
            if (sessionIsActive) {
                DriveSessionActivePillContent(presentation)
            } else {
                DriveSessionIdlePillContent()
            }
        }

        if (presentation.showsStopButton) {
            TextButton(
                onClick = onStop,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
            ) {
                Text(
                    "Stop",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Red,
                )
            }
        }
    }
}

@Composable
private fun DriveSessionStatusDots(presentation: DriveSessionPillPresentation) {
    val color =
        presentation.statusIndicatorColor()
            ?: DriveSessionColors.idleMuted
    StatusDot(color)
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun DriveSessionIdlePillContent() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "No Active Drive",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                "Tap to start",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        Icon(
            Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DriveSessionActivePillContent(presentation: DriveSessionPillPresentation) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                driveSessionPillPrimaryTitle(presentation),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            driveSessionPillSecondaryLine(presentation),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (presentation is DriveSessionPillPresentation.PausedSharing) {
            Text(
                "Not live until driving is detected",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.52f),
            )
        }
    }
}

private fun driveSessionPillPrimaryTitle(presentation: DriveSessionPillPresentation): String =
    when (presentation) {
        DriveSessionPillPresentation.Idle -> "No Active Drive"
        DriveSessionPillPresentation.PausedSharing -> "Sharing paused"
        is DriveSessionPillPresentation.Recording -> "Recording Drive"
        is DriveSessionPillPresentation.Route -> "Route Drive"
        is DriveSessionPillPresentation.Sharing -> "Sharing Live"
        is DriveSessionPillPresentation.RecordingAndSharing -> "Recording + Sharing"
    }

private fun driveSessionPillSecondaryLine(presentation: DriveSessionPillPresentation): String =
    when (presentation) {
        DriveSessionPillPresentation.Idle -> "Tap to start"
        DriveSessionPillPresentation.PausedSharing -> "Session active"
        is DriveSessionPillPresentation.Recording ->
            "${presentation.timeText} • ${presentation.distanceText}"
        is DriveSessionPillPresentation.Route ->
            "${presentation.name} · ${presentation.completed}/${presentation.total} checkpoints"
        is DriveSessionPillPresentation.Sharing -> {
            buildList {
                add(presentation.squadSummary)
                presentation.viewerCount?.takeIf { it > 0 }?.let { add(it.toString()) }
                presentation.remainingText?.let { add(it) }
            }.joinToString(" · ")
        }
        is DriveSessionPillPresentation.RecordingAndSharing -> {
            val tail =
                buildList {
                    add(presentation.squadSummary)
                    presentation.viewerCount?.takeIf { it > 0 }?.let { add(it.toString()) }
                    presentation.remainingText?.let { add(it) }
                }.joinToString(" · ")
            "${presentation.timeText} • ${presentation.distanceText} · $tail"
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDriveSheet(
    onQuickDrive: () -> Unit,
    onRouteDrive: () -> Unit,
    onGoLive: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .ottoBottomSheetContent(),
        ) {
            MapSheetHeader(
                title = "Start Drive",
                onDone = onCancel,
            )
            StartDriveOptionRow(
                icon = Icons.Outlined.DirectionsCar,
                backgroundColor = DriveSessionColors.sessionPurple,
                title = "Quick Drive",
                subtitle = "Hit the road without a planned route and just drive",
                onClick = onQuickDrive,
            )
            StartDriveOptionRow(
                icon = Icons.Outlined.Route,
                backgroundColor = SavedRouteListIconColors.startAccent,
                title = "Route Drive",
                subtitle = "Drive a planned route with checkpoints and navigation",
                onClick = onRouteDrive,
            )
            StartDriveOptionRow(
                icon = Icons.Outlined.Sensors,
                backgroundColor = DriveSessionColors.goLivePink,
                title = "Go Live",
                subtitle = "Broadcast your drive and live location to your Squads",
                onClick = onGoLive,
            )
        }
    }
}

@Composable
private fun StartDriveOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .clickable(onClick = onClick)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveControlsSheet(
    presentation: DriveSessionPillPresentation,
    startedAtMs: Long,
    timeText: String,
    distanceText: String,
    topSpeedText: String,
    shareLive: Boolean,
    onShareLiveChange: (Boolean) -> Unit,
    saveDrive: Boolean,
    onSaveDriveChange: (Boolean) -> Unit,
    routeName: String?,
    routeCheckpointText: String?,
    onAddSquad: () -> Unit,
    onStopDrive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val startedLabel =
        remember(startedAtMs) {
            Instant.ofEpochMilli(startedAtMs)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        }
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
                .ottoBottomSheetContent(),
        ) {
            MapSheetHeader(
                title = driveControlsTitle(presentation),
                subtitle = "Started $startedLabel",
                onDone = onDismiss,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveStatCard("Time", timeText, Modifier.weight(1f))
                DriveStatCard("Distance", distanceText, Modifier.weight(1f))
                DriveStatCard("Top Speed", topSpeedText, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            OttoToggleSettingCard(
                title = "Share Live",
                checked = shareLive,
                onCheckedChange = onShareLiveChange,
                icon = Icons.Outlined.Share,
            )
            if (showsSaveDriveToggle(presentation)) {
                Spacer(Modifier.height(12.dp))
                OttoToggleSettingCard(
                    title = stringResource(R.string.drive_record_toggle_title),
                    checked = saveDrive,
                    onCheckedChange = onSaveDriveChange,
                    icon = Icons.Outlined.Download,
                    helperText = stringResource(R.string.drive_record_toggle_helper),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onAddSquad)
                        .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = Color.White)
                Text(
                    "Add to Squad",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
            if (routeName != null && routeCheckpointText != null) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.055f))
                            .padding(14.dp),
                ) {
                    Text(
                        "Route",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    Text(
                        routeName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        routeCheckpointText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.58f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStopDrive,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.82f),
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(18.dp),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(vertical = 17.dp),
            ) {
                Text(
                    "Stop Drive",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

@Composable
private fun DriveStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .padding(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.48f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
        )
    }
}

private fun driveControlsTitle(presentation: DriveSessionPillPresentation): String =
    when (presentation) {
        is DriveSessionPillPresentation.Recording,
        is DriveSessionPillPresentation.RecordingAndSharing,
        -> "Recording Drive"
        is DriveSessionPillPresentation.Route -> "Route Drive"
        is DriveSessionPillPresentation.Sharing,
        DriveSessionPillPresentation.PausedSharing,
        -> "Sharing Live"
        DriveSessionPillPresentation.Idle -> "Drive Session"
    }

private fun showsSaveDriveToggle(presentation: DriveSessionPillPresentation): Boolean =
    when (presentation) {
        is DriveSessionPillPresentation.Recording,
        is DriveSessionPillPresentation.Route,
        -> false
        is DriveSessionPillPresentation.Sharing,
        is DriveSessionPillPresentation.RecordingAndSharing,
        DriveSessionPillPresentation.PausedSharing,
        DriveSessionPillPresentation.Idle,
        -> true
    }

sealed class DriveLaunchDockMode {
    data class Route(val route: SavedRouteDto) : DriveLaunchDockMode()

    data object Quick : DriveLaunchDockMode()

    data object Live : DriveLaunchDockMode()
}

@Composable
fun DriveLaunchDock(
    mode: DriveLaunchDockMode,
    isSessionActive: Boolean,
    statusText: String,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showStartDistanceWarning: Boolean = false,
    routeMetadata: String? = null,
    isOwnedRoute: Boolean = false,
    canManageRoute: Boolean = false,
    onManageRoute: (() -> Unit)? = null,
    recordDrive: Boolean = true,
    onRecordDriveChange: ((Boolean) -> Unit)? = null,
    shareLocation: Boolean = false,
    onShareLocationChange: ((Boolean) -> Unit)? = null,
    shareCircleIds: Set<String> = emptySet(),
    onShareCircleIdsChange: ((Set<String>) -> Unit)? = null,
    circles: List<CircleDto> = emptyList(),
    garageCars: List<GarageCarDto> = emptyList(),
    selectedSharingCarId: String = "",
    onSelectSharingCar: ((String?) -> Unit)? = null,
    optionsMenu: @Composable () -> Unit = {},
    expandedMaxHeightDp: Dp? = null,
    onDockHeightChanged: ((Int) -> Unit)? = null,
    onCompactDockHeightChanged: ((Int) -> Unit)? = null,
) {
    val dockShape =
        RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
    val contentSpacing = if (isSessionActive) 10.dp else 18.dp
    val verticalPadding =
        if (isSessionActive) {
            PaddingValues(top = 16.dp, bottom = 12.dp)
        } else {
            PaddingValues(top = 12.dp, bottom = 14.dp)
        }
    val showsQuickRouteToggles =
        !isSessionActive &&
            (mode is DriveLaunchDockMode.Quick || mode is DriveLaunchDockMode.Route)
    val isShareLocationExpanded =
        showsQuickRouteToggles && shareLocation && onShareCircleIdsChange != null
    val scrollState = rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isShareLocationExpanded && expandedMaxHeightDp != null) {
                        Modifier.heightIn(max = expandedMaxHeightDp)
                    } else {
                        Modifier
                    },
                )
                .animateContentSize()
                .then(
                    if (onDockHeightChanged != null || onCompactDockHeightChanged != null) {
                        Modifier.onSizeChanged { size ->
                            onDockHeightChanged?.invoke(size.height)
                            if (!isShareLocationExpanded) {
                                onCompactDockHeightChanged?.invoke(size.height)
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .shadow(
                    elevation = 24.dp,
                    shape = dockShape,
                    ambientColor = Color.Black.copy(alpha = 0.38f),
                    spotColor = Color.Black.copy(alpha = 0.38f),
                )
                .clip(dockShape)
                .background(Color.Black)
                .padding(horizontal = 20.dp)
                .padding(verticalPadding),
    ) {
        if (!isSessionActive) {
            DriveLaunchDockHeader(
                mode = mode,
                routeMetadata = routeMetadata,
                isOwnedRoute = isOwnedRoute,
                optionsMenu = optionsMenu,
            )
            Spacer(modifier = Modifier.height(contentSpacing))

            val middleModifier =
                if (isShareLocationExpanded && expandedMaxHeightDp != null) {
                    Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                } else {
                    Modifier.fillMaxWidth()
                }
            Column(
                modifier = middleModifier,
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                DriveLaunchDockScrollableMiddle(
                    showStartDistanceWarning = showStartDistanceWarning,
                    showsQuickRouteToggles = showsQuickRouteToggles,
                    recordDrive = recordDrive,
                    onRecordDriveChange = onRecordDriveChange,
                    shareLocation = shareLocation,
                    onShareLocationChange = onShareLocationChange,
                    isShareLocationExpanded = isShareLocationExpanded,
                    shareCircleIds = shareCircleIds,
                    onShareCircleIdsChange = onShareCircleIdsChange,
                    circles = circles,
                    garageCars = garageCars,
                    selectedSharingCarId = selectedSharingCarId,
                    onSelectSharingCar = onSelectSharingCar,
                )
            }

            Spacer(modifier = Modifier.height(contentSpacing))
            DriveLaunchDockActions(
                mode = mode,
                isSessionActive = isSessionActive,
                onStartDrive = onStartDrive,
                onStopDrive = onStopDrive,
                onCancel = onCancel,
            )
            Spacer(modifier = Modifier.height(contentSpacing))
            DriveLaunchDockStatusFooter(
                mode = mode,
                statusText = statusText,
                isSessionActive = isSessionActive,
                canManageRoute = canManageRoute,
                onManageRoute = onManageRoute,
            )
        } else {
            DriveLaunchDockActions(
                mode = mode,
                isSessionActive = isSessionActive,
                onStartDrive = onStartDrive,
                onStopDrive = onStopDrive,
                onCancel = onCancel,
            )
            Spacer(modifier = Modifier.height(contentSpacing))
            DriveLaunchDockStatusFooter(
                mode = mode,
                statusText = statusText,
                isSessionActive = isSessionActive,
                canManageRoute = canManageRoute,
                onManageRoute = onManageRoute,
            )
        }
    }
}

@Composable
private fun DriveLaunchDockScrollableMiddle(
    showStartDistanceWarning: Boolean,
    showsQuickRouteToggles: Boolean,
    recordDrive: Boolean,
    onRecordDriveChange: ((Boolean) -> Unit)?,
    shareLocation: Boolean,
    onShareLocationChange: ((Boolean) -> Unit)?,
    isShareLocationExpanded: Boolean,
    shareCircleIds: Set<String>,
    onShareCircleIdsChange: ((Set<String>) -> Unit)?,
    circles: List<CircleDto>,
    garageCars: List<GarageCarDto> = emptyList(),
    selectedSharingCarId: String = "",
    onSelectSharingCar: ((String?) -> Unit)? = null,
) {
    if (onSelectSharingCar != null) {
        DriveCarPickerSection(
            garageCars = garageCars,
            selectedSharingCarId = selectedSharingCarId,
            onSelectSharingCar = onSelectSharingCar,
        )
    }
    if (showStartDistanceWarning) {
        DriveLaunchDistanceWarningBanner()
    }
    if (showsQuickRouteToggles && onRecordDriveChange != null) {
        OttoToggleSettingCard(
            title = stringResource(R.string.drive_record_toggle_title),
            checked = recordDrive,
            onCheckedChange = onRecordDriveChange,
            icon = Icons.Outlined.Route,
            helperText = stringResource(R.string.drive_record_toggle_helper),
        )
    }
    if (showsQuickRouteToggles && onShareLocationChange != null) {
        OttoToggleSettingCard(
            title = stringResource(R.string.drive_share_location_toggle_title),
            checked = shareLocation,
            onCheckedChange = onShareLocationChange,
            icon = Icons.Outlined.LocationOn,
            helperText = stringResource(R.string.drive_share_location_toggle_helper),
        )
    }
    if (isShareLocationExpanded && onShareCircleIdsChange != null) {
        SharingSquadPickerSection(
            circles = circles,
            selectedCircleIds = shareCircleIds,
            onToggleCircle = { circleId ->
                val next = shareCircleIds.toMutableSet()
                if (next.contains(circleId)) {
                    next.remove(circleId)
                } else {
                    next.add(circleId)
                }
                onShareCircleIdsChange(next)
            },
        )
    }
}

@Composable
private fun DriveLaunchDockHeader(
    mode: DriveLaunchDockMode,
    routeMetadata: String?,
    isOwnedRoute: Boolean,
    optionsMenu: @Composable () -> Unit,
) {
    when (mode) {
        is DriveLaunchDockMode.Route -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 7.dp)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFAF52DE)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            mode.route.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isOwnedRoute) {
                            Text(
                                "OWNER",
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFAF52DE).copy(alpha = 0.45f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                    routeMetadata?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                optionsMenu()
            }
        }
        DriveLaunchDockMode.Quick -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DriveSessionColors.sessionPurple),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_otto_steering_wheel),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.drive_launch_dock_quick_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.drive_launch_dock_quick_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DriveLaunchDockMode.Live -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DriveSessionColors.goLivePink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.map_start_drive_go_live_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.map_start_drive_go_live_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveLaunchDistanceWarningBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFF9800).copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = Color(0xFFFF9800),
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.drive_launch_dock_distance_warning),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun DriveLaunchDockActions(
    mode: DriveLaunchDockMode,
    isSessionActive: Boolean,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit,
    onCancel: () -> Unit,
) {
    if (isSessionActive) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Red.copy(alpha = 0.82f))
                    .clickable(onClick = onStopDrive)
                    .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.StopCircle, contentDescription = null, tint = Color.White)
            Text(
                stringResource(R.string.drive_launch_dock_stop_drive),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val startBackground =
                if (mode is DriveLaunchDockMode.Live) {
                    DriveSessionColors.goLivePink
                } else {
                    SavedRouteListIconColors.startButton
                }
            val startLabel =
                if (mode is DriveLaunchDockMode.Live) {
                    stringResource(R.string.map_start_drive_go_live_title)
                } else {
                    stringResource(R.string.drive_launch_dock_start_drive)
                }
            val startIcon =
                if (mode is DriveLaunchDockMode.Live) {
                    Icons.Outlined.Sensors
                } else {
                    Icons.Outlined.Navigation
                }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(startBackground)
                        .clickable(onClick = onStartDrive)
                        .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(startIcon, contentDescription = null, tint = Color.White)
                Text(
                    startLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
                Text(
                    stringResource(R.string.drive_launch_dock_cancel),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DriveLaunchDockStatusFooter(
    mode: DriveLaunchDockMode,
    statusText: String,
    isSessionActive: Boolean,
    canManageRoute: Boolean,
    onManageRoute: (() -> Unit)?,
) {
    val statusIconColor =
        when (mode) {
            is DriveLaunchDockMode.Route -> Color(0xFFAF52DE)
            DriveLaunchDockMode.Quick -> SavedRouteListIconColors.startAccent
            DriveLaunchDockMode.Live -> DriveSessionColors.goLivePink
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isSessionActive) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Red),
            )
        } else {
            when (mode) {
                is DriveLaunchDockMode.Route ->
                    Icon(Icons.Outlined.Route, contentDescription = null, tint = statusIconColor)
                DriveLaunchDockMode.Quick ->
                    Icon(
                        painter = painterResource(R.drawable.ic_otto_steering_wheel),
                        contentDescription = null,
                        tint = statusIconColor,
                        modifier = Modifier.size(18.dp),
                    )
                DriveLaunchDockMode.Live ->
                    Icon(Icons.Outlined.Sensors, contentDescription = null, tint = statusIconColor)
            }
        }
        Text(
            statusText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        if (!isSessionActive && mode is DriveLaunchDockMode.Route && onManageRoute != null) {
            Text(
                stringResource(R.string.drive_launch_dock_manage),
                modifier =
                    Modifier.clickable(
                        enabled = canManageRoute,
                        onClick = onManageRoute,
                    ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (canManageRoute) Color(0xFFAF52DE) else Color.Transparent,
            )
        }
    }
}

@Composable
internal fun DriveCarPickerSection(
    garageCars: List<GarageCarDto>,
    selectedSharingCarId: String,
    onSelectSharingCar: (String?) -> Unit,
) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.drive_car_picker_title).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.56f),
        )
        if (garageCars.isEmpty()) {
            Text(
                stringResource(R.string.drive_car_picker_empty),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.48f),
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DriveCarPickerChip(
                    title = stringResource(R.string.drive_car_picker_none),
                    logoUrl = null,
                    selected = selectedSharingCarId.isBlank(),
                    onClick = { onSelectSharingCar(null) },
                )
                garageCars.forEach { car ->
                    val logoUrl =
                        remember(car.id, car.logoSlug, car.makeId, car.make) {
                            CarBrandLogoCatalog.logoUrl(
                                CarBrandLogoCatalog.resolvedLogoSlug(
                                    car.logoSlug,
                                    car.makeId,
                                    car.make,
                                    ctx,
                                ),
                            )
                        }
                    DriveCarPickerChip(
                        title = driveCarPickerTitle(car),
                        logoUrl = logoUrl,
                        selected = selectedSharingCarId == car.id,
                        onClick = { onSelectSharingCar(car.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveCarPickerChip(
    title: String,
    logoUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .background(if (selected) Color(0xFFAF52DE).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.055f))
                .border(
                    width = 1.dp,
                    color = if (selected) Color(0xFFAF52DE).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!logoUrl.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.225f))
                        .padding(4.dp),
            ) {
                AsyncImage(
                    model = ottoImageRequest(ctx, logoUrl),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun driveCarPickerTitle(car: GarageCarDto): String {
    val nick = car.nickname?.trim().orEmpty()
    if (nick.isNotEmpty()) return nick
    return listOf(car.make, car.model)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "Vehicle" }
}
