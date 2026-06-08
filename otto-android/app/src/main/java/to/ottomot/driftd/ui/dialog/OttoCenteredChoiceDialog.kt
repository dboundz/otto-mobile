package to.ottomot.driftd.ui.dialog

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import to.ottomot.driftd.R

/**
 * Dimmed scrim + centered purple-bordered card with optional hero, title/body slots, gradient primary,
 * bordered secondary, optional info footer. Matches iOS `OttoCenteredChoiceDialog`.
 */
object OttoCenteredChoiceDialogTokens {
    val scrim = Color.Black.copy(alpha = 0.58f)
    val cardFill = Color(0xF216151C)
    val cardBorderPurple = Color(0xFFAF52DE).copy(alpha = 0.48f)
    val primaryGradient = listOf(Color(0xFF7834F4), Color(0xFFDA38F2))
    val accentSoft = Color(0xFFD28BFF)
    val accentStrong = Color(0xFFAF52DE)
}

@Composable
fun OttoCenteredChoiceDialog(
    visible: Boolean,
    busy: Boolean,
    onDismissRequest: () -> Unit,
    /** Close X — usually same as secondary / scrim dismiss. */
    onCloseClick: () -> Unit,
    hero: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    body: @Composable () -> Unit,
    primaryLabel: String,
    primaryLeadingIcon: ImageVector?,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryLeadingIcon: ImageVector?,
    onSecondaryClick: () -> Unit,
    footerMessage: String? = null,
) {
    if (!visible) return

    val scroll = rememberScrollState()
    Dialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = !busy,
                dismissOnBackPress = !busy,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(OttoCenteredChoiceDialogTokens.scrim)
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp),
                shape = RoundedCornerShape(28.dp),
                color = OttoCenteredChoiceDialogTokens.cardFill,
                border = BorderStroke(1.dp, OttoCenteredChoiceDialogTokens.cardBorderPurple),
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                            .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = onCloseClick,
                            enabled = !busy,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(android.R.string.cancel),
                                tint = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .padding(top = 4.dp, bottom = 20.dp)
                                .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        hero?.invoke()
                        title()
                        body()
                    }

                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(OttoCenteredChoiceDialogTokens.primaryGradient),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable(enabled = !busy, onClick = onPrimaryClick)
                                    .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (primaryLeadingIcon != null) {
                                        Icon(
                                            primaryLeadingIcon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = primaryLabel,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = !busy, onClick = onSecondaryClick)
                                    .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (secondaryLeadingIcon != null) {
                                    Icon(
                                        secondaryLeadingIcon,
                                        contentDescription = null,
                                        tint = OttoCenteredChoiceDialogTokens.accentSoft,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Text(
                                    text = secondaryLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    val footer = footerMessage?.trim()?.takeIf { it.isNotEmpty() }
                    if (footer != null) {
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.055f))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.92f),
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = footer,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OttoSquadChatShareHeroGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .heightIn(min = 92.dp)
                .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.9f),
            modifier =
                Modifier
                    .size(14.dp)
                    .offset(x = (-52).dp, y = (-34).dp),
        )
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.65f),
            modifier =
                Modifier
                    .size(11.dp)
                    .offset(x = 52.dp, y = (-28).dp),
        )
        Box(modifier = Modifier.size(76.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.55f), CircleShape)
                        .background(OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.14f), CircleShape),
            )
            Icon(
                Icons.Outlined.Groups,
                contentDescription = null,
                tint = OttoCenteredChoiceDialogTokens.accentSoft,
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = CircleShape,
                color = OttoCenteredChoiceDialogTokens.accentStrong.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
            ) {
                Icon(
                    Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .padding(7.dp)
                            .size(18.dp),
                )
            }
        }
    }
}

/** Title line: “Share with **Name**?” using app string resources. */
@Composable
fun OttoShareWithSquadAnnotatedTitle(squadDisplayName: String) {
    Text(
        text =
            buildAnnotatedString {
                append(stringResource(R.string.squad_event_share_title_prefix))
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(squadDisplayName)
                }
                append("?")
            },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
