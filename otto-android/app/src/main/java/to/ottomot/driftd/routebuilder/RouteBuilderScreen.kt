package to.ottomot.driftd.routebuilder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.mapbox.maps.MapboxMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.ottomot.driftd.DriveSessionColors
import to.ottomot.driftd.R
import to.ottomot.driftd.ui.dialog.OttoEducationDialog
import to.ottomot.driftd.ui.dialog.OttoEducationLocationHero

@Composable
fun RouteBuilderScreen(
    viewModel: RouteBuilderViewModel,
    locationGranted: Boolean,
    locationNotDetermined: Boolean,
    userLat: Double?,
    userLng: Double?,
    onDismiss: () -> Unit,
    onRouteSaved: (to.ottomot.driftd.core.network.dto.SavedRouteDto) -> Unit,
    onRequestLocationSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var mapWidthPx by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableFloatStateOf(0f) }
    var bottomSheetPx by remember { mutableFloatStateOf(0f) }
    var mapboxMap by remember { mutableStateOf<MapboxMap?>(null) }

    val sheetHeightPxForPlacement =
        if (bottomSheetPx > 0f) {
            bottomSheetPx
        } else {
            with(density) { state.bottomSheetHeightDp.dp.toPx() }
        }

    LaunchedEffect(
        mapboxMap,
        mapWidthPx,
        mapHeightPx,
        sheetHeightPxForPlacement,
        state.uiState,
    ) {
        refreshCrosshairPlacement(
            mapboxMap,
            mapWidthPx,
            mapHeightPx,
            sheetHeightPxForPlacement,
            viewModel,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onLocationPermissionResult(granted, userLat, userLng)
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RouteBuilderEvent.Dismiss -> onDismiss()
                is RouteBuilderEvent.RouteSaved -> onRouteSaved(event.route)
                RouteBuilderEvent.RequestLocationSync -> onRequestLocationSync()
                is RouteBuilderEvent.RequestLocationPermission -> {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                is RouteBuilderEvent.RouteBuilderPresented -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        viewModel.onAppear()
        onDispose { viewModel.onDisappear() }
    }

    LaunchedEffect(locationGranted, locationNotDetermined, userLat, userLng) {
        viewModel.updateLocationContext(locationGranted, locationNotDetermined, userLat, userLng)
    }

    LaunchedEffect(mapWidthPx, mapHeightPx, bottomSheetPx) {
        viewModel.updateMapLayout(mapWidthPx, mapHeightPx, bottomSafeAreaPx = 0f, bottomSheetVisibleHeightPx = bottomSheetPx)
    }

    state.userToastMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearUserToast()
        }
    }

    val mapInputs =
        remember(state.mapContent, state.programmaticCameraTarget, state.isMapInteractionDisabled) {
            RouteBuilderMapInputs(
                mapContent = state.mapContent,
                programmaticCameraTarget = state.programmaticCameraTarget,
                allowsInteraction = !state.isMapInteractionDisabled,
            )
        }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        RouteBuilderMapLayer(
            mapInputs = mapInputs,
            onCameraChanged = viewModel::onCameraChanged,
            onGestureStarted = viewModel::onGestureStarted,
            onGestureEnded = {
                viewModel.onGestureEnded()
                refreshCrosshairPlacement(
                    mapboxMap,
                    mapWidthPx,
                    mapHeightPx,
                    sheetHeightPxForPlacement,
                    viewModel,
                )
            },
            onMapLongPress = viewModel::handleMapLongPress,
            onMarkerLongPress = viewModel::handleMarkerLongPress,
            onMapboxMapReady = { mapboxMap = it },
            markerDebugInputs = viewModel::markerDebugInputs,
            onMarkerDebugSnapshotUpdated = viewModel::updateMarkerDebugSnapshot,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        mapWidthPx = it.width.toFloat()
                        mapHeightPx = it.height.toFloat()
                    },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(142.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent)))
                .zIndex(1f),
        )

        val sheetHeightPx = sheetHeightPxForPlacement
        val crosshair = RouteBuilderPlacement.crosshairCenter(mapWidthPx, mapHeightPx, sheetHeightPx, bottomSafeAreaPx = 0f)
        val crosshairHalfPx = with(density) { (RouteBuilderPlacementCrosshairSizeDp.dp / 2f).toPx() }
        RouteBuilderPlacementCrosshair(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (crosshair.first - crosshairHalfPx).roundToInt(),
                            (crosshair.second - crosshairHalfPx).roundToInt(),
                        )
                    }
                    .zIndex(2f),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .zIndex(3f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::attemptClose) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
            Text(
                if (state.editingRouteId == null) stringResource(R.string.route_builder_new_title) else state.routeName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (state.showsEditSessionUndo) {
                TextButton(onClick = viewModel::undoEditSessionChange, enabled = state.canUndoEditSession) {
                    Text(stringResource(R.string.route_builder_undo), color = Color.White)
                }
            }
            TextButton(onClick = viewModel::beginSaveFlow, enabled = state.saveIsActive && !state.isSaving) {
                Text(stringResource(R.string.route_builder_save), color = Color(0xFF7B3DFF), fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            onClick = { viewModel.recenterOnUser(userLat, userLng, locationGranted) },
            enabled = state.recenterEnabled,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = (state.bottomSheetHeightDp + 18).dp)
                    .zIndex(2f),
            shape = CircleShape,
            color = Color(0xFF1A1B22),
        ) {
            Icon(
                Icons.Outlined.MyLocation,
                contentDescription = stringResource(R.string.route_builder_recenter_map),
                tint = if (state.recenterEnabled) Color.White else Color.White.copy(0.35f),
                modifier = Modifier.padding(12.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .onSizeChanged { bottomSheetPx = it.height.toFloat() }
                .zIndex(3f),
        ) {
            RouteBuilderBottomSheet(
                uiState = state.uiState,
                checkpointCount = state.checkpointCount,
                isInteractionDisabled = state.isMapInteractionDisabled,
                canDecreaseCheckpointDensity = state.canDecreaseCheckpointDensity,
                canIncreaseCheckpointDensity = state.canIncreaseCheckpointDensity,
                isMovingPoint = state.movingPointId != null,
                onSetStart = {
                    refreshCrosshairPlacement(mapboxMap, mapWidthPx, mapHeightPx, sheetHeightPxForPlacement, viewModel)
                    viewModel.placeStartFromGuidedFlow()
                },
                onSetFinish = {
                    refreshCrosshairPlacement(mapboxMap, mapWidthPx, mapHeightPx, sheetHeightPxForPlacement, viewModel)
                    viewModel.placeFinishForGuidedFlow()
                },
                onBuildManually = viewModel::enterManualMode,
                onBackFromSetFinish = viewModel::goBackFromSetFinish,
                onBackFromRouteReady = viewModel::goBackFromRouteReady,
                onLooksGood = viewModel::enterEditModeFromRouteReady,
                onFewerCheckpoints = { viewModel.stepCheckpointDensity(-1) },
                onMoreCheckpoints = { viewModel.stepCheckpointDensity(1) },
                onShapeRoute = {
                    refreshCrosshairPlacement(
                        mapboxMap,
                        mapWidthPx,
                        mapHeightPx,
                        sheetHeightPxForPlacement,
                        viewModel,
                    )
                    viewModel.shapeRoute()
                },
                onAddCheckpoint = {
                    refreshCrosshairPlacement(
                        mapboxMap,
                        mapWidthPx,
                        mapHeightPx,
                        sheetHeightPxForPlacement,
                        viewModel,
                    )
                    viewModel.addCheckpoint()
                },
                onAddStop = {
                    refreshCrosshairPlacement(
                        mapboxMap,
                        mapWidthPx,
                        mapHeightPx,
                        sheetHeightPxForPlacement,
                        viewModel,
                    )
                    viewModel.addStop()
                },
                onMoveHere = viewModel::moveSelectedPointToCenterPin,
                onCancelMove = viewModel::cancelMovePoint,
            )
        }
    }

    RouteBuilderDialogs(state, viewModel)
}

private fun refreshCrosshairPlacement(
    mapboxMap: MapboxMap?,
    mapWidthPx: Float,
    mapHeightPx: Float,
    sheetHeightPx: Float,
    viewModel: RouteBuilderViewModel,
) {
    val coordinate =
        RouteBuilderPlacement.resolveCrosshairCoordinate(
            mapboxMap = mapboxMap,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            sheetVisibleHeightPx = sheetHeightPx,
            bottomSafeAreaPx = 0f,
        )
    viewModel.updateCrosshairPlacementCoordinate(coordinate)
}

@Composable
private fun RouteBuilderDialogs(state: RouteBuilderScreenState, viewModel: RouteBuilderViewModel) {
    if (state.showRouteEducation) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = viewModel::dismissEducationNotNow,
            onCloseClick = viewModel::dismissEducationNotNow,
            hero = {
                Box(
                    Modifier.size(64.dp).background(Brush.linearGradient(listOf(Color(0xFF7B2CE8), Color(0xFFB73BFF))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Route, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            },
            title = stringResource(R.string.route_builder_education_title),
            body = stringResource(R.string.route_builder_education_body),
            bulletSectionTitle = stringResource(R.string.route_builder_education_section),
            bullets =
                listOf(
                    Icons.Outlined.MyLocation to stringResource(R.string.route_builder_education_zoom),
                    Icons.Outlined.Route to stringResource(R.string.route_builder_education_endpoints),
                    Icons.Outlined.Route to stringResource(R.string.route_builder_education_turns),
                    Icons.Outlined.Route to stringResource(R.string.route_builder_education_checkpoints),
                ),
            footer = stringResource(R.string.route_builder_education_footer),
            primaryLabel = stringResource(R.string.route_builder_education_got_it),
            onPrimaryClick = viewModel::dismissEducation,
            secondaryLabel = stringResource(R.string.route_builder_education_not_now),
            onSecondaryClick = viewModel::dismissEducationNotNow,
            allowsUnconfirmedDismiss = true,
        )
    }

    if (state.showLocationPrimer) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = {},
            onCloseClick = {},
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.route_builder_location_primer_title),
            body = stringResource(R.string.route_builder_location_primer_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = stringResource(R.string.route_builder_location_primer_footer),
            primaryLabel = stringResource(R.string.route_builder_location_primer_continue),
            onPrimaryClick = { viewModel.dismissLocationPrimer(requestAuth = true) },
            allowsUnconfirmedDismiss = false,
        )
    }

    state.markerActionPointId?.let { pointId ->
        val point = state.points.firstOrNull { it.id == pointId } ?: return@let
        AlertDialog(
            onDismissRequest = viewModel::cancelMarkerAction,
            title = { Text(point.displayTitle) },
            confirmButton = {
                TextButton(onClick = { viewModel.beginMoveMarker(pointId) }) {
                    Text("Move")
                }
            },
            dismissButton = {
                Row {
                    if (point.type != RouteBuilderPointType.START && point.type != RouteBuilderPointType.FINISH) {
                        TextButton(onClick = { viewModel.requestDeleteMarker(pointId) }) {
                            Text("Delete")
                        }
                    }
                    TextButton(onClick = viewModel::cancelMarkerAction) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            },
        )
    }

    if (state.isShowingDiscardChangesAlert) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDiscard,
            title = { Text(stringResource(R.string.route_builder_discard_title)) },
            text = { Text(stringResource(R.string.route_builder_discard_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscard) {
                    Text(stringResource(R.string.route_builder_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDiscard) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (state.isShowingRouteNamePrompt) {
        var draft by remember(state.routeNameDraft) { mutableStateOf(state.routeNameDraft) }
        AlertDialog(
            onDismissRequest = viewModel::cancelRouteNamePrompt,
            title = { Text(stringResource(R.string.route_builder_name_prompt_title)) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRouteNameDraft(draft)
                    viewModel.attemptSaveNamedRoute()
                }) {
                    Text(stringResource(R.string.route_builder_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRouteNamePrompt) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.route_builder_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.map_drive_recording_ok))
                }
            },
        )
    }

    state.routePointPendingDeleteId?.let { pointId ->
        val point = state.points.firstOrNull { it.id == pointId } ?: return@let
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteMarker,
            title = { Text(stringResource(R.string.route_builder_delete_point_title)) },
            text = { Text(stringResource(R.string.route_builder_delete_point_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteMarker) {
                    Text(stringResource(R.string.drive_summary_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteMarker) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}
