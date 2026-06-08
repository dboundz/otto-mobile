package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

@Composable
internal fun DriveCompleteSheet(
    summary: DriveCompleteSummary,
    onViewSummary: () -> Unit,
    onDone: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDone,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
    ) {
        DriveCompleteSheetContent(
            summary = summary,
            onViewSummary = onViewSummary,
            onDone = onDone,
        )
    }
}

@Composable
private fun DriveCompleteSheetContent(
    summary: DriveCompleteSummary,
    onViewSummary: () -> Unit,
    onDone: () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasAppeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasAppeared = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 90f) {
                            onDone()
                        }
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, delta ->
                        dragOffset = max(0f, dragOffset + delta)
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                DriveCompleteCard(
                    summary = summary,
                    onViewSummary = onViewSummary,
                    onDone = onDone,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = if (hasAppeared) 1f else 0f
                                translationY = if (hasAppeared) dragOffset else 18f
                                scaleX = if (hasAppeared) 1f else 0.96f
                                scaleY = if (hasAppeared) 1f else 0.96f
                            },
                )
            }
        }
    }
}

@Composable
private fun DriveCompleteCard(
    summary: DriveCompleteSummary,
    onViewSummary: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    val distanceValue = formatDriveCompleteDistanceValue(summary.distanceMeters)
    val driveTimeLabel = formatDriveCompleteDuration(summary.driveTimeSeconds)
    val checkpointTotal = max(summary.totalCheckpoints, 1)
    val allPassed = summary.completedCheckpoints >= summary.totalCheckpoints && summary.totalCheckpoints > 0

    Surface(
        modifier =
            modifier
                .shadow(
                    elevation = 34.dp,
                    shape = shape,
                    ambientColor = DriveSessionColors.sessionPurple.copy(alpha = 0.25f),
                    spotColor = DriveSessionColors.sessionPurple.copy(alpha = 0.25f),
                ),
        shape = shape,
        color = Color.Black.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DriveSessionColors.sessionPurple.copy(alpha = 0.23f),
                            Color.Transparent,
                            Color(0xFF2563EB).copy(alpha = 0.08f),
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            ambientColor = DriveSessionColors.sessionPurple.copy(alpha = 0.9f),
                            spotColor = DriveSessionColors.sessionPurple.copy(alpha = 0.9f),
                        )
                        .clip(CircleShape)
                        .background(DriveSessionColors.sessionPurple.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    stringResource(R.string.drive_complete_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.drive_complete_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                )
            }

            RoutePreviewHero(
                coordinates = summary.routeCoordinates,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.055f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(146.dp),
                ) {
                    DriveCompleteStatCell(
                        icon = { Icon(Icons.Outlined.Route, null, tint = DriveSessionColors.sessionPurple) },
                        title = stringResource(R.string.drive_complete_stat_distance),
                        value = distanceValue,
                        unit = "mi",
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    )
                    DriveCompleteStatCell(
                        icon = { Icon(Icons.Outlined.AccessTime, null, tint = DriveSessionColors.sessionPurple) },
                        title = stringResource(R.string.drive_complete_stat_time),
                        value = driveTimeLabel,
                        unit = "",
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    )
                    DriveCompleteStatCell(
                        icon = { Icon(Icons.Outlined.Flag, null, tint = DriveSessionColors.sessionPurple) },
                        title = stringResource(R.string.drive_complete_stat_checkpoints),
                        value = "${summary.completedCheckpoints}/$checkpointTotal",
                        unit =
                            if (allPassed) {
                                stringResource(R.string.drive_complete_all_passed)
                            } else {
                                ""
                            },
                        unitHighlight = allPassed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onViewSummary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3326FF),
                                        Color(0xFFB833E0),
                                    ),
                                ),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.drive_complete_view_summary),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.92f)),
                ) {
                    Text(
                        stringResource(R.string.drive_complete_done),
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveCompleteStatCell(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    unitHighlight: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DriveSessionColors.sessionPurple.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unit.isNotEmpty()) {
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color =
                    if (unitHighlight) DriveSessionColors.sessionPurple
                    else Color.White.copy(alpha = 0.72f),
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}
