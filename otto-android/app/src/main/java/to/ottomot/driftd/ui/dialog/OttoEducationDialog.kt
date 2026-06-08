package to.ottomot.driftd.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Purple-bordered education / disclaimer card: hero (optional), title, body, optional bullet list with section heading,
 * optional footer, primary gradient + bordered secondary. Shared by map location primer and sharing safety flows.
 */
@Composable
fun OttoEducationDialog(
    visible: Boolean,
    busy: Boolean,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    hero: (@Composable () -> Unit)? = null,
    title: String,
    body: String,
    bulletSectionTitle: String? = null,
    bullets: List<Pair<ImageVector, String>>,
    footer: String? = null,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String = "",
    onSecondaryClick: () -> Unit = {},
    allowsUnconfirmedDismiss: Boolean = true,
) {
    if (!visible) return

    val scroll = rememberScrollState()
    Dialog(
        onDismissRequest = {
            if (!busy && allowsUnconfirmedDismiss) onDismissRequest()
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = !busy && allowsUnconfirmedDismiss,
                dismissOnBackPress = !busy && allowsUnconfirmedDismiss,
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
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (allowsUnconfirmedDismiss) {
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
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        hero?.invoke()

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = Color.White.copy(alpha = 0.68f),
                                textAlign = TextAlign.Center,
                            )
                        }

                        val section = bulletSectionTitle?.trim()?.takeIf { it.isNotEmpty() }
                        if (section != null && bullets.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(13.dp),
                            ) {
                                Text(
                                    text = section,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFC06CFF),
                                )
                                bullets.forEach { (icon, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(30.dp)
                                                    .background(Color(0xFFAF52DE).copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = OttoCenteredChoiceDialogTokens.accentSoft,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.76f),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        val foot = footer?.trim()?.takeIf { it.isNotEmpty() }
                        if (foot != null) {
                            Text(
                                text = foot,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = Color.White.copy(alpha = 0.58f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (allowsUnconfirmedDismiss) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                onClick = onSecondaryClick,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
                            ) {
                                Text(
                                    text = secondaryLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 15.dp),
                                )
                            }

                            OttoEducationDialogPrimaryButton(
                                modifier = Modifier.weight(1f),
                                busy = busy,
                                primaryLabel = primaryLabel,
                                onPrimaryClick = onPrimaryClick,
                            )
                        }
                    } else {
                        OttoEducationDialogPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            busy = busy,
                            primaryLabel = primaryLabel,
                            onPrimaryClick = onPrimaryClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OttoEducationDialogPrimaryButton(
    modifier: Modifier = Modifier,
    busy: Boolean,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
) {
    Surface(
        onClick = onPrimaryClick,
        modifier = modifier,
        enabled = !busy,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(OttoCenteredChoiceDialogTokens.primaryGradient),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun OttoEducationShieldHero() {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7B2CE8), Color(0xFFB73BFF))),
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
fun OttoEducationLocationHero() {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7B2CE8), Color(0xFFB73BFF))),
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}
