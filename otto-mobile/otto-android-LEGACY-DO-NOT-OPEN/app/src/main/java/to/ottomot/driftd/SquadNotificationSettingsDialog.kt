package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.ottomot.driftd.ui.components.OttoFullscreenDarkTopAppBar
import to.ottomot.driftd.ui.components.OttoFullscreenDialog
import to.ottomot.driftd.ui.components.OttoFullscreenScrollColumn
import to.ottomot.driftd.ui.squad.copyInviteLinkToClipboard
import to.ottomot.driftd.ui.squad.openSquadInviteSms
import to.ottomot.driftd.ui.squad.squadInviteSmsBody
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.notify.SquadNotificationMuteBucket
import to.ottomot.driftd.core.notify.SquadNotificationMuteChoice
import to.ottomot.driftd.core.session.SessionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquadNotificationSettingsDialog(
    circleId: String,
    squad: CircleDto?,
    contacts: List<UserDto>,
    meUser: UserDto?,
    myUserId: String?,
    presenceMembersByCircleId: Map<String, List<PresenceMemberDto>>,
    allCircles: List<CircleDto>,
    squadName: String,
    memberSubtitle: String,
    sessionRepository: SessionRepository,
    inviteUi: SquadSettingsInviteUi,
    squadSettingsToast: String?,
    onDismissSquadSettingsToast: () -> Unit,
    inviteViewModel: OttoShellViewModel,
    onDismiss: () -> Unit,
    onRenameSquad: (circleId: String, name: String, onFinished: (Boolean) -> Unit) -> Unit,
    onLeaveSquad: (circleId: String) -> Unit,
    onPrefetchSquadInvite: (circleId: String) -> Unit,
    onSquadInviteSearchChanged: (circleId: String, query: String) -> Unit,
    onInviteSquadLookupUser: (circleId: String, userId: String, phone: String) -> Unit,
    onAddSquadMemberFromSettings: (circleId: String, userId: String) -> Unit,
    onMemberProfileMessage: (userId: String) -> Unit,
    onMemberProfileViewFullProfile: (userId: String) -> Unit,
    onMemberProfileOpenSquad: (circleId: String) -> Unit,
    onNavigateToOwnProfileTab: () -> Unit,
    onKickCircleMember: (circleId: String, userId: String) -> Unit,
    onPatchCircleMemberRole: (circleId: String, userId: String, role: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val mutes by sessionRepository.squadMuteFlow(circleId).collectAsStateWithLifecycle(initialValue = null to null)

    LaunchedEffect(squadSettingsToast) {
        val message = squadSettingsToast?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissSquadSettingsToast()
    }
    val authId by sessionRepository.authUserIdState.collectAsStateWithLifecycle(initialValue = null)

    val trimmedAuth = authId?.trim().orEmpty()
    val isOwner =
        squad != null &&
            trimmedAuth.isNotEmpty() &&
            ottoUserIdsEqual(squad.ownerId, trimmedAuth)
    val otherMemberCount =
        squad?.members.orEmpty().count { !ottoUserIdsEqual(it.userId, trimmedAuth) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember(squadName) { mutableStateOf(squadName) }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showTransferGate by remember { mutableStateOf(false) }

    var renameBusy by remember { mutableStateOf(false) }

    var memberProfileSheetUserId by remember(circleId) { mutableStateOf<String?>(null) }
    val memberProfileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        topBar = {
            OttoFullscreenDarkTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.squad_settings_title),
                        color = Color.White,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.profile_settings_done),
                            color = Color.White,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.Black,
                                Color(0xFF0F0A14),
                                Color.Black,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(900f, 1600f),
                        ),
                    ),
            )
            OttoFullscreenScrollColumn(
                contentPadding = contentPadding,
                horizontalPadding = 18.dp,
            ) {
                    SquadSettingsHeaderRow(
                        name = squadName,
                        subtitle = memberSubtitle,
                        squad = squad,
                        presenceMembers = presenceMembersForCircleId(presenceMembersByCircleId, circleId),
                    )

                    if (isOwner) {
                        SquadSettingsSectionTitle(stringResource(R.string.squad_settings_section_squad_details))
                        SquadNameSettingsRow(
                            currentName = squadName,
                            enabled = !renameBusy,
                            onClick = {
                                renameDraft = squadName
                                showRenameDialog = true
                            },
                        )
                        Spacer(Modifier.height(22.dp))
                    }

                    SquadSettingsSectionTitle(stringResource(R.string.squad_settings_section_notifications))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        SquadMutePickerBlock(
                            title = stringResource(R.string.squad_notif_mute_new_title),
                            raw = mutes.first,
                            onSelect = { choice ->
                                scope.launch {
                                    sessionRepository.setSquadMuteEncoded(
                                        circleId,
                                        SquadNotificationMuteBucket.NEW_MESSAGES,
                                        SquadNotificationMuteChoice.encode(choice),
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Spacer(Modifier.height(12.dp))
                        SquadMutePickerBlock(
                            title = stringResource(R.string.squad_notif_mute_mentions_title),
                            raw = mutes.second,
                            onSelect = { choice ->
                                scope.launch {
                                    sessionRepository.setSquadMuteEncoded(
                                        circleId,
                                        SquadNotificationMuteBucket.MENTIONS_AND_REPLIES,
                                        SquadNotificationMuteChoice.encode(choice),
                                    )
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    squad?.let { s ->
                        SquadNotificationSettingsMembersSection(
                            circle = s,
                            contacts = contacts,
                            meUser = meUser,
                            myUserId = myUserId,
                            presenceMembersByCircleId = presenceMembersByCircleId,
                            allCircles = allCircles,
                            inviteUi = inviteUi,
                            onPrefetchInvite = { onPrefetchSquadInvite(circleId) },
                            onCopyInviteLink = {
                                scope.launch {
                                    inviteViewModel
                                        .resolveSquadShareInviteUrlForSettings(
                                            circleId,
                                            SquadShareInviteBusy.COPY,
                                        )
                                        .onSuccess { url ->
                                            copyInviteLinkToClipboard(context, url)
                                            inviteViewModel.presentSquadSettingsCopied()
                                        }
                                }
                            },
                            onInviteBySmsShare = {
                                scope.launch {
                                    inviteViewModel
                                        .resolveSquadShareInviteUrlForSettings(
                                            circleId,
                                            SquadShareInviteBusy.SMS,
                                        )
                                        .onSuccess { url ->
                                            val body = squadInviteSmsBody(url)
                                            if (!openSquadInviteSms(context, body)) {
                                                inviteViewModel.presentSquadInviteSmsOpenFailed()
                                            }
                                        }
                                }
                            },
                            onSearchQueryChanged = { onSquadInviteSearchChanged(circleId, it) },
                            onInviteLookupUser = { uid, phone ->
                                onInviteSquadLookupUser(circleId, uid, phone)
                            },
                            onAddMember = { onAddSquadMemberFromSettings(circleId, it) },
                            onInviteViaSms = { phone ->
                                scope.launch {
                                    val payload =
                                        inviteViewModel.squadSmsInviteBodyForPhone(circleId, phone)
                                            ?: return@launch
                                    if (!openSquadInviteSms(context, payload.first, payload.second)) {
                                        inviteViewModel.presentSquadInviteSmsOpenFailed()
                                    }
                                }
                            },
                            onOpenMemberProfile = { memberProfileSheetUserId = it },
                            settingsSectionTitleColor = Color.White.copy(alpha = 0.45f),
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    SquadSettingsSectionTitle(
                        text = stringResource(R.string.squad_settings_section_danger_zone),
                        tint = Color(0xFFFF453A).copy(alpha = 0.92f),
                    )

                    Spacer(Modifier.height(8.dp))

                    LeaveSquadSettingsRow(
                        enabled = !renameBusy,
                        onClick = {
                            if (isOwner && otherMemberCount > 0) {
                                showTransferGate = true
                            } else {
                                showLeaveConfirm = true
                            }
                        },
                    )
                }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2C2C2E),
                    contentColor = Color.White,
                )
            }

            memberProfileSheetUserId?.let { sheetUid ->
                ModalBottomSheet(
                    onDismissRequest = { memberProfileSheetUserId = null },
                    sheetState = memberProfileSheetState,
                ) {
                    val presenceMember =
                        remember(sheetUid, circleId, presenceMembersByCircleId) {
                            squadMemberPresenceOrStubForCircle(
                                sheetUid,
                                circleId,
                                presenceMembersByCircleId,
                            )
                        }
                    MapMemberProfileSheetContent(
                        member = presenceMember,
                        circles = allCircles,
                        contacts = contacts,
                        meUser = meUser,
                        myUserId = myUserId,
                        onMessage = {
                            memberProfileSheetUserId = null
                            onMemberProfileMessage(sheetUid)
                        },
                        onViewProfile = {
                            memberProfileSheetUserId = null
                            if (!myUserId.isNullOrBlank() && ottoUserIdsEqual(sheetUid, myUserId)) {
                                onNavigateToOwnProfileTab()
                            } else {
                                onMemberProfileViewFullProfile(sheetUid)
                            }
                        },
                        onOpenSharedSquad = { circle ->
                            memberProfileSheetUserId = null
                            onMemberProfileOpenSquad(circle.id)
                        },
                        squadManagementCircleId = circleId,
                        onKickCircleMember = { cid, kickedUid ->
                            memberProfileSheetUserId = null
                            onKickCircleMember(cid, kickedUid)
                        },
                        onPatchCircleMemberRole = { cid, targetUid, role ->
                            memberProfileSheetUserId = null
                            onPatchCircleMemberRole(cid, targetUid, role)
                        },
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!renameBusy) showRenameDialog = false
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text(stringResource(R.string.squad_settings_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !renameBusy && renameDraft.trim().length >= 2,
                    onClick = {
                        renameBusy = true
                        onRenameSquad(circleId, renameDraft.trim()) { ok ->
                            renameBusy = false
                            if (ok) showRenameDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.garage_dialog_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !renameBusy,
                    onClick = { showRenameDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel), color = Color.White.copy(alpha = 0.85f))
                }
            },
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.72f),
            title = { Text(stringResource(R.string.squad_settings_leave_confirm_title)) },
            text = { Text(stringResource(R.string.squad_settings_leave_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveSquad(circleId)
                    },
                ) {
                    Text(stringResource(R.string.squad_settings_leave_confirm_delete), color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(android.R.string.cancel), color = Color.White.copy(alpha = 0.85f))
                }
            },
        )
    }

    if (showTransferGate) {
        AlertDialog(
            onDismissRequest = { showTransferGate = false },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.72f),
            title = { Text(stringResource(R.string.squad_settings_transfer_title)) },
            text = { Text(stringResource(R.string.squad_settings_transfer_body)) },
            confirmButton = {
                TextButton(onClick = { showTransferGate = false }) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }
}

@Composable
private fun SquadSettingsSectionTitle(
    text: String,
    tint: Color = Color.White.copy(alpha = 0.45f),
) {
    Text(
        text.uppercase(),
        style =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
        color = tint,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SquadSettingsHeaderRow(
    name: String,
    subtitle: String,
    squad: CircleDto?,
    presenceMembers: List<PresenceMemberDto>,
) {
    val ctx = LocalContext.current
    val avatarUrl =
        squad?.photoUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }
    val subtitleText =
        squad?.let {
            squadMemberPresenceSummary(
                memberCount = it.members.orEmpty().size,
                presenceMembers = presenceMembers,
                mutedColor = Color.White.copy(alpha = 0.58f),
            )
        }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = ottoImageRequest(ctx, avatarUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitleText ?: androidx.compose.ui.text.AnnotatedString(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.58f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SquadNameSettingsRow(
    currentName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.squad_settings_row_squad_name),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                currentName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 2,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun LeaveSquadSettingsRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val danger = Color(0xFFFF453A)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(danger.copy(alpha = 0.07f))
            .border(1.dp, danger.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ExitToApp,
            contentDescription = null,
            tint = danger.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.squad_settings_leave),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = danger.copy(alpha = 0.95f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.squad_settings_leave_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.48f),
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = danger.copy(alpha = 0.42f),
        )
    }
}

@Composable
private fun SquadMutePickerBlock(
    title: String,
    raw: String?,
    onSelect: (SquadNotificationMuteChoice) -> Unit,
) {
    val current = SquadNotificationMuteChoice.decodeStored(raw)
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        squadMuteChoiceLabel(current),
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", color = Color.White.copy(alpha = 0.45f))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for (opt in SquadNotificationMuteChoice.entries) {
                    DropdownMenuItem(
                        text = { Text(squadMuteChoiceLabel(opt)) },
                        onClick = {
                            expanded = false
                            onSelect(opt)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun squadMuteChoiceLabel(choice: SquadNotificationMuteChoice): String =
    when (choice) {
        SquadNotificationMuteChoice.OFF -> stringResource(R.string.squad_notif_mute_off)
        SquadNotificationMuteChoice.END_OF_DAY -> stringResource(R.string.squad_notif_mute_end_of_day)
        SquadNotificationMuteChoice.TWENTY_FOUR_HOURS -> stringResource(R.string.squad_notif_mute_24h)
        SquadNotificationMuteChoice.ONE_WEEK -> stringResource(R.string.squad_notif_mute_1w)
        SquadNotificationMuteChoice.ALWAYS -> stringResource(R.string.squad_notif_mute_always)
    }
