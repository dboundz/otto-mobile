# One-off patch: swap OttoSquadsPane + insert iOS-aligned helpers before it.
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/to/ottomot/driftd/ShellScreens.kt"
text = path.read_text()
needle_start = "@Composable\nprivate fun OttoSquadsPane("
needle_end = "@Composable\nprivate fun CircleDetailOverlay("
s = text.index(needle_start)
e = text.index(needle_end, s)

NEW = r'''private enum class SquadChromeSection(val labelRes: Int) {
    SquadList(R.string.squads_subtab_squads),
    Dms(R.string.squads_subtab_dms),
    Invites(R.string.squads_subtab_invites),
}

@Composable
private fun SquadIosTabBar(
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SquadChromeSection.entries.forEachIndexed { i, tab ->
            val sel = i == selectedIdx
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color =
                        if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth(0.72f),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        thickness = 3.dp,
                        color =
                            if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SquadMemberAvatarOverlap(
    userIds: List<String>,
    contacts: List<UserDto>,
) {
    val ids = userIds.take(4)
    if (ids.isEmpty()) return
    val avatarSize = 32.dp
    val step = 16.dp
    val overlapSlots = kotlin.math.max(ids.size - 1, 0)
    val totalW = avatarSize + step * overlapSlots
    Box(modifier = Modifier.height(avatarSize).width(totalW)) {
        ids.forEachIndexed { i, uid ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = step * i)
                        .size(avatarSize)
                        .zIndex(i.toFloat())
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val url =
                    contacts
                        .find { it.id == uid }
                        ?.avatarUrl
                        ?.let { MediaUrlResolver.resolve(it)?.toString() }
                if (url != null) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OttoSquadsPane(
    circles: List<CircleDto>,
    circleDetailUi: CircleDetailUi?,
    myUserId: String?,
    contacts: List<UserDto>,
    pendingInvites: List<MyPendingCircleInvite>,
    squadsSnack: String?,
    invitePreview: InviteLinkResolveDto?,
    onOpenCircle: (String) -> Unit,
    onDismissCircleDetail: () -> Unit,
    onCreateSquad: (String) -> Unit,
    onRedeemInspect: (String) -> Unit,
    onRedeemAccept: (String) -> Unit,
    onRespondInvite: (String, Boolean) -> Unit,
    onDismissSnack: () -> Unit,
    onSendChat: (String) -> Unit,
    onInviteByPhone: (String, String) -> Unit,
    onCreateInviteLink: (String) -> Unit,
    onOpenDirectMessages: () -> Unit,
    squadsToolbarCreateTicks: Int,
    modifier: Modifier = Modifier,
) {
    var squadChromeIdx by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newSquadName by rememberSaveable { mutableStateOf("") }
    var redeemRaw by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(squadsToolbarCreateTicks) {
        if (squadsToolbarCreateTicks > 0) {
            showCreateDialog = true
        }
    }

    val filteredCircles =
        remember(circles, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                circles
            } else {
                circles.filter { it.name.contains(q, ignoreCase = true) }
            }
        }

    val sectionIdx = squadChromeIdx.coerceIn(0, SquadChromeSection.entries.lastIndex)
    val section = SquadChromeSection.entries[sectionIdx]

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (section) {
                    SquadChromeSection.SquadList -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                SquadIosTabBar(
                                    selectedIdx = sectionIdx,
                                    onSelect = { squadChromeIdx = it },
                                )
                            }
                            item {
                                OutlinedTextField(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.squads_search_placeholder)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                )
                            }
                            items(items = filteredCircles, key = { it.id }) { circle ->
                                ElevatedCard(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    OttoSquadRow(
                                        circle = circle,
                                        contacts = contacts,
                                        myUserId = myUserId,
                                        modifier = Modifier.clickable { onOpenCircle(circle.id) },
                                    )
                                }
                            }
                            if (circles.isNotEmpty() && filteredCircles.isEmpty()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squads_search_no_results),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 24.dp),
                                    )
                                }
                            }
                            if (circles.isEmpty()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.empty_squads_extended),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }
                    }

                    SquadChromeSection.Dms -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                SquadIosTabBar(
                                    selectedIdx = sectionIdx,
                                    onSelect = { squadChromeIdx = it },
                                )
                            }
                            item {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stringResource(R.string.squads_dms_heading),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        stringResource(R.string.squads_dms_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Button(onClick = onOpenDirectMessages) {
                                        Text(stringResource(R.string.squads_dms_open_button))
                                    }
                                }
                            }
                        }
                    }

                    SquadChromeSection.Invites -> {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            item {
                                SquadIosTabBar(
                                    selectedIdx = sectionIdx,
                                    onSelect = { squadChromeIdx = it },
                                )
                            }
                            if (pendingInvites.isNotEmpty()) {
                                item {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pending_invites_heading),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        pendingInvites.forEach { inv ->
                                            Column(Modifier.padding(bottom = 12.dp)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                        Text(
                                                            inv.circleName,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        inv.circleDescription
                                                            ?.trim()
                                                            ?.takeIf { it.isNotEmpty() }
                                                            ?.let { d ->
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(
                                                                    d,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    maxLines = 4,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        TextButton(onClick = { onRespondInvite(inv.inviteId, false) }) {
                                                            Text(stringResource(R.string.invite_decline))
                                                        }
                                                        Button(onClick = { onRespondInvite(inv.inviteId, true) }) {
                                                            Text(stringResource(R.string.invite_accept))
                                                        }
                                                    }
                                                }
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.squad_redeem_heading),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = redeemRaw,
                                        onValueChange = { redeemRaw = it },
                                        placeholder = { Text(stringResource(R.string.squad_redeem_hint)) },
                                        singleLine = true,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = redeemRaw.trim().isNotEmpty(),
                                        onClick = { onRedeemInspect(redeemRaw) },
                                    ) {
                                        Text(stringResource(R.string.squad_redeem_lookup))
                                    }
                                    invitePreview?.let { prev ->
                                        Spacer(Modifier.height(14.dp))
                                        ElevatedCard(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(
                                                    prev.circle?.name
                                                        ?: stringResource(R.string.squad_redeem_preview_unknown),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                                prev.invitedBy?.displayName?.let { dn ->
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        stringResource(R.string.squad_redeem_from_format, dn),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                prev.circle?.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(desc, style = MaterialTheme.typography.bodySmall)
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Button(onClick = { onRedeemAccept(prev.token.trim()) }) {
                                                    Text(stringResource(R.string.squad_redeem_join))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (pendingInvites.isEmpty() && invitePreview == null && redeemRaw.isBlank()) {
                                item {
                                    EmptyTabMessage(
                                        text = stringResource(R.string.squads_invites_empty),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            squadsSnack?.takeIf { it.isNotBlank() }?.let { msg ->
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onDismissSnack) { Text(stringResource(R.string.dismiss_snack)) }
                    }
                }
            }
        }

        circleDetailUi?.let { detail ->
            BackHandler(onBack = onDismissCircleDetail)
            CircleDetailOverlay(
                detailUi = detail,
                myUserId = myUserId,
                onClose = onDismissCircleDetail,
                onSendChat = onSendChat,
                onInviteByPhone = onInviteByPhone,
                onCreateInviteLink = onCreateInviteLink,
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.squad_create_title)) },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newSquadName,
                    onValueChange = { newSquadName = it },
                    label = { Text(stringResource(R.string.squad_create_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newSquadName.trim().length >= 2,
                    onClick = {
                        onCreateSquad(newSquadName.trim())
                        newSquadName = ""
                        showCreateDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

'''

path.write_text(text[:s] + NEW + text[e:])
print('patched', path)
