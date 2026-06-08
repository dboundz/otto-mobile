private data class MapDurationPreset(val minutes: Int?, val labelRes: Int)

private val mapDurationChoices: List<MapDurationPreset> =
    listOf(
        MapDurationPreset(60, R.string.map_duration_1h),
        MapDurationPreset(240, R.string.map_duration_4h),
        MapDurationPreset(480, R.string.map_duration_8h),
        MapDurationPreset(null, R.string.map_duration_until_stopped),
    )

@Composable
private fun OttoMapSideFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint)
        }
    }
}

private fun presenceMemberAvatarLabel(member: PresenceMemberDto, contacts: List<UserDto>, me: UserDto?): Pair<String, String?> {
    val u =
        contacts.find { it.id == member.userId } ?: me?.takeIf { it.id == member.userId }
    val name =
        u
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: shortenId(member.userId)
    return Pair(name, u?.avatarUrl)
}

@Composable
private fun MapAudienceCreatedByLine(ownerId: String, contacts: List<UserDto>, me: UserDto?) {
    val ownerNameLabel =
        when {
            me != null && ownerId == me.id -> stringResource(R.string.squads_created_by_you)
            else ->
                contacts
                    .find { it.id == ownerId }
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.squads_owner_unknown)
        }
    Text(
        stringResource(R.string.squads_created_by_format, ownerNameLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OttoMapPresencePane(
    ui: OttoShellUiState,
    circles: List<CircleDto>,
    onScopeSelected: (String) -> Unit,
    onRetryPresence: () -> Unit,
    onSharingToggle: (Boolean) -> Unit,
    onMapSharingOptions: (durationMinutes: Int?, whileDrivingOnly: Boolean, saveDrive: Boolean) -> Unit,
    onMapShareSaveDrive: (Boolean) -> Unit,
    onSaveMapPlace: (String, Double, Double) -> Unit,
    onRenameSavedPlace: (String, String) -> Unit,
    onDeleteSavedPlace: (String) -> Unit,
    onDismissSavedPlacesSnack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sharingSheetVisible by rememberSaveable { mutableStateOf(false) }
    var placesSheetVisible by rememberSaveable { mutableStateOf(false) }

    val sharingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var durationChoiceIndex by remember { mutableIntStateOf(1) }
    var whileDrivingOnlyDraft by remember { mutableStateOf(false) }
    var audienceIdDraft by rememberSaveable { mutableStateOf(OttoShellUiState.PublicPresenceChannelId) }

    LaunchedEffect(sharingSheetVisible) {
        if (!sharingSheetVisible) return@LaunchedEffect
        durationChoiceIndex =
            mapDurationChoices.indexOfFirst { it.minutes == ui.mapShareDurationMinutes }.takeIf { it >= 0 }
                ?: 1
        whileDrivingOnlyDraft = ui.mapShareWhileDrivingOnly
        audienceIdDraft = ui.mapPresenceCircleId
    }

    var durationMenuExpanded by remember { mutableStateOf(false) }
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun showSnack(msg: String) {
        scope.launch { snackHost.showSnackbar(msg) }
    }

    var saveDialogOpen by remember { mutableStateOf(false) }
    var savePlaceNameDraft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(saveDialogOpen) {
        if (saveDialogOpen) savePlaceNameDraft = ""
    }
    var renameTarget by remember { mutableStateOf<SavedPlaceDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val scrollSheet = rememberScrollState()

    val ctx = LocalContext.current
    var fineGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val locationPerm =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            fineGranted = ok
            if (ok) {
                onSharingToggle(true)
            }
        }

    fun applySharing(enable: Boolean) {
        sharingSheetVisible = false
        if (!enable) {
            onSharingToggle(false)
            return
        }

        val choice = mapDurationChoices[durationChoiceIndex.coerceIn(0, mapDurationChoices.lastIndex)]
        onMapSharingOptions(choice.minutes, whileDrivingOnlyDraft, saveDriveDraft)
        onScopeSelected(audienceIdDraft.ifBlank { OttoShellUiState.PublicPresenceChannelId })

        when {
            !fineGranted -> locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> onSharingToggle(true)
        }
    }

    val plotted =
        remember(ui.presenceMembers) {
            ui.presenceMembers.filter { m -> geoLatLngOrNull(m.lat, m.lng) != null }
        }

    val defaultLatLng =
        plotted.firstOrNull()?.let { m -> geoLatLngOrNull(m.lat, m.lng) }
            ?: ui.savedPlaces.firstOrNull()?.let { sp -> geoLatLngOrNull(sp.latitude, sp.longitude) }
            ?: LatLng(37.7749, -122.4194)

    val defaultZoom =
        when {
            plotted.size > 1 -> 5f
            plotted.size == 1 -> 11f
            ui.savedPlaces.isNotEmpty() -> 10f
            else -> 4f
        }

    val cameraPositionState =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(defaultLatLng, defaultZoom)
        }

    val savedPlaceMarkerIcon =
        remember {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        }

    var mapTypeSatellite by rememberSaveable { mutableStateOf(false) }

    val anchorPresenceKey =
        remember(plotted) { plotted.joinToString { "${it.userId}_${it.lat}_${it.lng}" } }
    val anchorPlacesKey =
        remember(ui.savedPlaces) { ui.savedPlaces.joinToString { it.id } }

    LaunchedEffect(anchorPresenceKey, anchorPlacesKey) {
        val presenceCoords = plotted.mapNotNull { m -> geoLatLngOrNull(m.lat, m.lng) }
        when {
            presenceCoords.size == 1 -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(presenceCoords.first(), 11f),
                    400,
                )
            }
            presenceCoords.size > 1 -> {
                val b = LatLngBounds.builder()
                presenceCoords.forEach { b.include(it) }
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(b.build(), 96),
                        500,
                    )
                }
            }
            ui.savedPlaces.isNotEmpty() -> {
                val p = ui.savedPlaces.first()
                val anchor =
                    geoLatLngOrNull(p.latitude, p.longitude) ?: LatLng(37.7749, -122.4194)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(anchor, 10f),
                    400,
                )
            }
            else -> {}
        }
    }

    val audienceLabel =
        if (ui.mapPresenceCircleId == OttoShellUiState.PublicPresenceChannelId) {
            stringResource(R.string.map_scope_public)
        } else {
            circles.find { it.id == ui.mapPresenceCircleId }?.name
                ?: stringResource(R.string.map_scope_squad_unknown)
        }

    val bottomChromePad = 88.dp
    val mapsKeyOk = BuildConfig.MAPS_API_KEY.isNotBlank()

    val meId = ui.me?.id
    val mePinCoords =
        plotted
            .firstOrNull { meId != null && it.userId == meId }
            ?.let { geoLatLngOrNull(it.lat, it.lng) }

    Box(modifier.fillMaxSize()) {
        if (mapsKeyOk) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties =
                    MapProperties(
                        mapType = if (mapTypeSatellite) MapType.HYBRID else MapType.NORMAL,
                    ),
                uiSettings =
                    MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = false,
                    ),
            ) {
                plotted.forEach { presence ->
                    val pos = geoLatLngOrNull(presence.lat, presence.lng) ?: return@forEach
                    val (displayName, avatarRaw) =
                        presenceMemberAvatarLabel(presence, ui.contacts, ui.me)
                    val avatar =
                        avatarRaw?.let { MediaUrlResolver.resolve(it)?.toString() }

                    val markerState = rememberMarkerState(position = pos)
                    LaunchedEffect(pos.latitude, pos.longitude) {
                        markerState.position = pos
                    }

                    MarkerComposable(
                        keys =
                            arrayOf(
                                presence.userId,
                                presence.updatedAt ?: "",
                                displayName,
                                avatar ?: "",
                            ),
                        state = markerState,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .padding(bottom = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .border(
                                            width = 2.dp,
                                            brush =
                                                Brush.linearGradient(
                                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                                                ),
                                            shape = CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!avatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model =
                                                ottoImageRequest(LocalContext.current, avatar),
                                            contentDescription = displayName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Text(
                                            displayName.take(1).uppercase(Locale.getDefault()),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                        )
                                    }
                                }
                            }

                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 4.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (presence.isActive) {
                                            Color(0xFFFFC107)
                                        } else {
                                            Color(0xFF9E9E9E)
                                        },
                                    ),
                            )
                        }
                    }
                }

                ui.savedPlaces.forEach { place ->
                    val pos = geoLatLngOrNull(place.latitude, place.longitude) ?: return@forEach
                    Marker(
                        state = MarkerState(position = pos),
                        title = place.name,
                        icon = savedPlaceMarkerIcon,
                    )
                }
            }
        } else {
            PresenceListFallback(
                plotted.ifEmpty { ui.presenceMembers },
                Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            snackHost,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomChromePad),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 10.dp, end = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { sharingSheetVisible = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val active = ui.mapSharingLocation
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (active) Color(0xFF43A047) else Color(0xFFE53935)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (active) {
                                        stringResource(R.string.map_sharing_pill_on)
                                    } else {
                                        stringResource(R.string.map_sharing_pill_off)
                                    },
                                    style =
                                        MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (active) audienceLabel else stringResource(R.string.map_sharing_pill_tap_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = stringResource(R.string.map_accessibility_open_sharing),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

            }

            if (mapsKeyOk &&
                plotted.isEmpty() &&
                ui.savedPlaces.isEmpty() &&
                ui.presenceMembers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.map_hints_no_pins),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ui.presenceError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onRetryPresence) {
                    Text(stringResource(R.string.retry))
                }
            }

            ui.savedPlacesSnack?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismissSavedPlacesSnack) {
                        Text(stringResource(R.string.dismiss_snack))
                    }
                }
            }

            if (!mapsKeyOk) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.map_add_api_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (mapsKeyOk) {
            val gradientBg =
                Brush.horizontalGradient(
                    listOf(Color(0xFFFF4DB8), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                )
            Surface(
                onClick = {
                    scope.launch {
                        val target =
                            mePinCoords
                                ?: plotted
                                    .firstOrNull()
                                    ?.let { geoLatLngOrNull(it.lat, it.lng) }
                                ?: defaultLatLng
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, 14f),
                            420,
                        )
                    }
                },
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = bottomChromePad)
                    .size(54.dp),
                shape = CircleShape,
                tonalElevation = 6.dp,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(gradientBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = stringResource(R.string.map_accessibility_recenter_map),
                        tint = Color.White,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = bottomChromePad),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OttoMapSideFab(
                    onClick = { placesSheetVisible = true },
                    icon = Icons.Outlined.Search,
                    contentDescription =
                        stringResource(R.string.map_accessibility_saved_places),
                )

                OttoMapSideFab(
                    onClick = {
                        mapTypeSatellite = !mapTypeSatellite
                        showSnack(
                            if (mapTypeSatellite) {
                                stringResource(R.string.map_layers_satellite)
                            } else {
                                stringResource(R.string.map_layers_standard)
                            },
                        )
                    },
                    icon = Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.map_accessibility_map_layers),
                )

                OttoMapSideFab(
                    onClick = { sharingSheetVisible = true },
                    icon = Icons.Outlined.DirectionsCar,
                    contentDescription = stringResource(R.string.map_accessibility_open_sharing),
                    iconTint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (saveDialogOpen) {
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = { Text(stringResource(R.string.places_save_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = savePlaceNameDraft,
                    onValueChange = { savePlaceNameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.places_save_dialog_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = savePlaceNameDraft.trim()
                        if (t.isNotEmpty()) {
                            val target = cameraPositionState.position.target
                            onSaveMapPlace(t, target.latitude, target.longitude)
                            saveDialogOpen = false
                        }
                    },
                    enabled = savePlaceNameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.places_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) {
                    Text(stringResource(R.string.garage_dialog_cancel))
                }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.places_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.places_rename_dialog_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = renameDraft.trim()
                        if (t.isNotEmpty()) {
                            onRenameSavedPlace(target.id, t)
                            renameTarget = null
                        }
                    },
                    enabled = renameDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.places_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.garage_dialog_cancel))
                }
            },
        )
    }

    val durationLabel =
        mapDurationChoices[durationChoiceIndex.coerceIn(0, mapDurationChoices.lastIndex)]
            .let { stringResource(it.labelRes) }

    if (sharingSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sharingSheetVisible = false },
            sheetState = sharingSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(scrollSheet)
                    .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.map_sharing_sheet_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    TextButton(onClick = { sharingSheetVisible = false }) {
                        Text(stringResource(R.string.map_sharing_done))
                    }
                }

                Text(
                    stringResource(R.string.map_sharing_duration_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExposedDropdownMenuBox(
                    expanded = durationMenuExpanded,
                    onExpandedChange = { durationMenuExpanded = !durationMenuExpanded },
                ) {
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        readOnly = true,
                        value = durationLabel,
                        onValueChange = {},
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(durationMenuExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                        },
                    )

                    ExposedDropdownMenu(
                        expanded = durationMenuExpanded,
                        onDismissRequest = { durationMenuExpanded = false },
                    ) {
                        mapDurationChoices.forEachIndexed { index, preset ->
                            DropdownMenuItem(
                                text = { Text(stringResource(preset.labelRes)) },
                                onClick = {
                                    durationChoiceIndex = index
                                    durationMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.map_sharing_duration_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    stringResource(R.string.map_sharing_when_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { whileDrivingOnlyDraft = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border =
                            BorderStroke(
                                if (!whileDrivingOnlyDraft) 2.dp else 1.dp,
                                if (!whileDrivingOnlyDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                        color =
                            if (!whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Outlined.AllInclusive,
                                contentDescription = null,
                                tint =
                                    if (!whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.map_sharing_share_now_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                stringResource(R.string.map_sharing_share_now_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        onClick = { whileDrivingOnlyDraft = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border =
                            BorderStroke(
                                if (whileDrivingOnlyDraft) 2.dp else 1.dp,
                                if (whileDrivingOnlyDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                        color =
                            if (whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Outlined.DirectionsCar,
                                contentDescription = null,
                                tint =
                                    if (whileDrivingOnlyDraft) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.map_sharing_while_driving_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                stringResource(R.string.map_sharing_while_driving_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.map_sharing_with_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    onClick = { audienceIdDraft = OttoShellUiState.PublicPresenceChannelId },
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                    border =
                        BorderStroke(
                            width = if (audienceIdDraft == OttoShellUiState.PublicPresenceChannelId) 2.dp else 1.dp,
                            color =
                                if (audienceIdDraft == OttoShellUiState.PublicPresenceChannelId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            tonalElevation = 0.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.TravelExplore,
                                    contentDescription = null,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.map_scope_public), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                stringResource(R.string.map_sharing_public_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color =
                                if (audienceIdDraft == OttoShellUiState.PublicPresenceChannelId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (audienceIdDraft == OttoShellUiState.PublicPresenceChannelId) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                circles.forEach { circle ->
                    val selected = circle.id == audienceIdDraft
                    val photoResolved = circle.photoUrl?.let { MediaUrlResolver.resolve(it)?.toString() }
                    Surface(
                        onClick = { audienceIdDraft = circle.id },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                        border =
                            BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                if (!photoResolved.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ottoImageRequest(ctx, photoResolved),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.Groups,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(circle.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                MapAudienceCreatedByLine(circle.ownerId, ui.contacts, ui.me)
                                val count = circle.members?.size ?: 0
                                if (count > 0) {
                                    Text(
                                        stringResource(R.string.squads_members_format, count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val actionBrush =
                    Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))

                Button(
                    onClick = {
                        if (ui.mapSharingLocation) applySharing(enable = false) else applySharing(enable = true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(actionBrush, RoundedCornerShape(16.dp))
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (ui.mapSharingLocation) stringResource(R.string.map_sharing_stop)
                            else stringResource(R.string.map_sharing_start),
                            style =
                                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (placesSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { placesSheetVisible = false },
            sheetState = placesSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.places_sheet_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    stringResource(R.string.places_sheet_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { saveDialogOpen = true },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.places_save_this_spot))
                }

                Spacer(Modifier.height(14.dp))

                if (ui.savedPlaces.isEmpty()) {
                    EmptyTabMessage(
                        text =
                            stringResource(
                                if (ui.presenceMembers.isEmpty()) {
                                    R.string.map_empty_no_shares
                                } else {
                                    R.string.map_hints_no_pins
                                },
                            ),
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                } else {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = ui.savedPlaces, key = { it.id }) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val target = geoLatLngOrNull(p.latitude, p.longitude) ?: return@FilterChip
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(target, 14f),
                                                380,
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            p.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 160.dp),
                                        )
                                    },
                                )
                                IconButton(
                                    onClick = {
                                        renameTarget = p
                                        renameDraft = p.name
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.places_rename_cd),
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSavedPlace(p.id) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.places_remove_cd),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(placesSheetVisible) {
        if (placesSheetVisible) {
            placesSheetState.show()
        }
    }

    LaunchedEffect(sharingSheetVisible) {
        if (sharingSheetVisible) {
            sharingSheetState.show()
        }
    }
}
