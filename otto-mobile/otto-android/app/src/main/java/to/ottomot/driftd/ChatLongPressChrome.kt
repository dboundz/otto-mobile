package to.ottomot.driftd

import android.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Matches iOS `ChatReactionEmojiBar.defaultEmojis`. */
internal object ChatReactionEmojiBarDefaults {
    val emojis: List<String> =
        listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥")
}

private val ChatChromePillFill = Color(0xFF1C1C1E).copy(alpha = 0.92f)
private val ChatChromePillBorder = Color.White.copy(alpha = 0.05f)
private val ReplyIconTint = Color(0xFFBF5AF2)
private val DeleteActionTint = Color(0xFFFF6969)

/**
 * Platform [AlertDialog] for delete confirmation (system chrome, horizontal action buttons on most devices).
 */
@Composable
internal fun ChatDeleteSystemAlertEffect(
    victimMessageId: String?,
    title: String,
    message: String,
    deleteLabel: String,
    cancelLabel: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    DisposableEffect(victimMessageId) {
        if (victimMessageId == null) {
            return@DisposableEffect onDispose { }
        }
        val dialog =
            AlertDialog
                .Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(cancelLabel) { _, _ -> }
                .setPositiveButton(deleteLabel) { _, _ -> onDelete() }
                .create()
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}

/**
 * Reply uses 2× the width of edit/delete (1 : 2 : 1). The whole cluster is **horizontally centered** in the
 * dialog (capped width on very wide surfaces so it does not hug the edges).
 */
@Composable
private fun ChatChromeReplyCenteredActionRow(
    onReply: () -> Unit,
    onDismissRequest: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val editFn = onEdit
    val deleteFn = onDelete
    val hasEdit = editFn != null
    val hasDelete = deleteFn != null
    val gap = 8.dp
    val clusterCap = 320.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var clusterW = minOf(maxWidth, clusterCap)
        var usable = clusterW - gap * 2
        var side = (usable / 4).coerceAtLeast(36.dp)
        var rowW = side * 4 + gap * 2
        if (rowW > maxWidth) {
            usable = maxWidth - gap * 2
            side = (usable / 4).coerceAtLeast(32.dp)
            rowW = side * 4 + gap * 2
        }
        val centerW = side * 2

        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .width(rowW)
                        .align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    hasEdit && hasDelete -> {
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(side),
                            label = stringResource(R.string.chat_edit_message),
                            icon = Icons.Outlined.Edit,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                editFn.invoke()
                                onDismissRequest()
                            },
                        )
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(centerW),
                            label = stringResource(R.string.chat_reply),
                            icon = Icons.AutoMirrored.Filled.Reply,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                onReply()
                                onDismissRequest()
                            },
                        )
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(side),
                            label = stringResource(R.string.chat_delete_message),
                            icon = Icons.Outlined.Delete,
                            iconTint = DeleteActionTint,
                            labelColor = DeleteActionTint,
                            onClick = {
                                deleteFn.invoke()
                                onDismissRequest()
                            },
                        )
                    }
                    hasEdit -> {
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(side),
                            label = stringResource(R.string.chat_edit_message),
                            icon = Icons.Outlined.Edit,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                editFn.invoke()
                                onDismissRequest()
                            },
                        )
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(centerW),
                            label = stringResource(R.string.chat_reply),
                            icon = Icons.AutoMirrored.Filled.Reply,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                onReply()
                                onDismissRequest()
                            },
                        )
                        Spacer(Modifier.width(side))
                    }
                    hasDelete -> {
                        Spacer(Modifier.width(side))
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(centerW),
                            label = stringResource(R.string.chat_reply),
                            icon = Icons.AutoMirrored.Filled.Reply,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                onReply()
                                onDismissRequest()
                            },
                        )
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(side),
                            label = stringResource(R.string.chat_delete_message),
                            icon = Icons.Outlined.Delete,
                            iconTint = DeleteActionTint,
                            labelColor = DeleteActionTint,
                            onClick = {
                                deleteFn.invoke()
                                onDismissRequest()
                            },
                        )
                    }
                    else -> {
                        Spacer(Modifier.width(side))
                        ChatChromeHorizontalActionCell(
                            modifier = Modifier.width(centerW),
                            label = stringResource(R.string.chat_reply),
                            icon = Icons.AutoMirrored.Filled.Reply,
                            iconTint = ReplyIconTint,
                            labelColor = Color.White,
                            onClick = {
                                onReply()
                                onDismissRequest()
                            },
                        )
                        Spacer(Modifier.width(side))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatChromeHorizontalActionCell(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    iconTint: Color,
    labelColor: Color,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Full-window Compose [Dialog] with emoji reactions and Reply. Avoids layout/root coordinate bugs from
 * positioning pills over [LazyColumn] rows.
 */
@Composable
internal fun ChatMessageActionsDialog(
    onDismissRequest: () -> Unit,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboard?.hide()
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ChatChromePillFill,
                border = BorderStroke(1.dp, ChatChromePillBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChatReactionEmojiBarDefaults.emojis.forEach { em ->
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onReaction(em)
                                            onDismissRequest()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(em, fontSize = 22.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(8.dp))
                    ChatChromeReplyCenteredActionRow(
                        onReply = onReply,
                        onDismissRequest = onDismissRequest,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}
