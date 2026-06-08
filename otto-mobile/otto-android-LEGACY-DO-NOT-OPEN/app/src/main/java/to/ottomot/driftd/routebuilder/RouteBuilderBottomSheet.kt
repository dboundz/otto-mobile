package to.ottomot.driftd.routebuilder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.ottomot.driftd.R

private val SheetVerticalSpacing = 10.dp
private val CompactHelperLineHeight = 13.sp

@Composable
fun RouteBuilderBottomSheet(
    uiState: RouteBuilderUiState,
    checkpointCount: Int,
    isInteractionDisabled: Boolean,
    canDecreaseCheckpointDensity: Boolean,
    canIncreaseCheckpointDensity: Boolean,
    isMovingPoint: Boolean,
    onSetStart: () -> Unit,
    onSetFinish: () -> Unit,
    onBuildManually: () -> Unit,
    onBackFromSetFinish: () -> Unit,
    onBackFromRouteReady: () -> Unit,
    onLooksGood: () -> Unit,
    onFewerCheckpoints: () -> Unit,
    onMoreCheckpoints: () -> Unit,
    onShapeRoute: () -> Unit,
    onAddCheckpoint: () -> Unit,
    onAddStop: () -> Unit,
    onMoveHere: () -> Unit,
    onCancelMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF12131A))
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 14.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        when (uiState) {
            RouteBuilderUiState.SET_START -> GuidedSetStart(onSetStart)
            RouteBuilderUiState.SET_FINISH ->
                GuidedSetFinish(
                    onBack = onBackFromSetFinish,
                    onSetFinish = onSetFinish,
                    onBuildManually = onBuildManually,
                )
            RouteBuilderUiState.GENERATING_ROUTE -> GeneratingCopy()
            RouteBuilderUiState.ROUTE_READY ->
                RouteReadyContent(
                    checkpointCount = checkpointCount,
                    canDecrease = canDecreaseCheckpointDensity,
                    canIncrease = canIncreaseCheckpointDensity,
                    onBack = onBackFromRouteReady,
                    onLooksGood = onLooksGood,
                    onFewer = onFewerCheckpoints,
                    onMore = onMoreCheckpoints,
                )
            RouteBuilderUiState.MANUAL_PLOT ->
                ManualPlotContent(
                    isMovingPoint = isMovingPoint,
                    usesMoveEndpointLabels = false,
                    onSetStart = onSetStart,
                    onSetFinish = onSetFinish,
                    onShapeRoute = onShapeRoute,
                    onAddCheckpoint = onAddCheckpoint,
                    onAddStop = onAddStop,
                    onMoveHere = onMoveHere,
                    onCancelMove = onCancelMove,
                )
            RouteBuilderUiState.EDIT_ROUTE ->
                EditRouteContent(
                    isMovingPoint = isMovingPoint,
                    onSetStart = onSetStart,
                    onSetFinish = onSetFinish,
                    onShapeRoute = onShapeRoute,
                    onAddCheckpoint = onAddCheckpoint,
                    onAddStop = onAddStop,
                    onMoveHere = onMoveHere,
                    onCancelMove = onCancelMove,
                )
        }
    }
}

@Composable
private fun GuidedSetStart(onSetStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetVerticalSpacing)) {
        Text(stringResource(R.string.route_builder_set_start_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        CompactHelperText(stringResource(R.string.route_builder_set_start_helper))
        PrimaryButton(
            title = stringResource(R.string.route_builder_set_start_cta),
            background = RouteBuilderMarkerColors.startButton,
            icon = Icons.Filled.PlayArrow,
            onClick = onSetStart,
        )
        Text(
            stringResource(R.string.route_builder_set_start_tip),
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GuidedSetFinish(
    onBack: () -> Unit,
    onSetFinish: () -> Unit,
    onBuildManually: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetVerticalSpacing)) {
        BackButton(onBack)
        Text(stringResource(R.string.route_builder_set_finish_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        CompactHelperText(stringResource(R.string.route_builder_set_finish_helper))
        PrimaryButton(
            title = stringResource(R.string.route_builder_set_finish_cta),
            background = RouteBuilderMarkerColors.finishButton,
            icon = Icons.Filled.Flag,
            onClick = onSetFinish,
        )
        TextButton(onClick = onBuildManually, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.route_builder_build_manually), color = RouteBuilderMarkerColors.finishButton, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GeneratingCopy() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.route_builder_generating_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        CompactHelperText(stringResource(R.string.route_builder_generating_subtitle))
        Text(stringResource(R.string.route_builder_generating_hint), color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun RouteReadyContent(
    checkpointCount: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onBack: () -> Unit,
    onLooksGood: () -> Unit,
    onFewer: () -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetVerticalSpacing)) {
        BackButton(onBack)
        Text(stringResource(R.string.route_builder_route_ready_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        CompactHelperText(
            if (checkpointCount > 0) {
                "${stringResource(R.string.route_builder_route_ready_body, checkpointCount)} ${stringResource(R.string.route_builder_route_ready_footer)}"
            } else {
                stringResource(R.string.route_builder_route_ready_body_none)
            },
        )
        PrimaryButton(
            title = stringResource(R.string.route_builder_looks_good),
            background = RouteBuilderMarkerColors.startButton,
            icon = Icons.Filled.Check,
            onClick = onLooksGood,
        )
        if (checkpointCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DensityStepButton(
                    title = stringResource(R.string.route_builder_fewer_checkpoints),
                    icon = Icons.Filled.Remove,
                    enabled = canDecrease,
                    onClick = onFewer,
                    modifier = Modifier.weight(1f),
                )
                DensityStepButton(
                    title = stringResource(R.string.route_builder_more_checkpoints),
                    icon = Icons.Filled.Add,
                    enabled = canIncrease,
                    onClick = onMore,
                    modifier = Modifier.weight(1f),
                    accent = RouteBuilderMarkerColors.finishButton,
                )
            }
        }
    }
}

@Composable
private fun ManualPlotContent(
    isMovingPoint: Boolean,
    usesMoveEndpointLabels: Boolean,
    onSetStart: () -> Unit,
    onSetFinish: () -> Unit,
    onShapeRoute: () -> Unit,
    onAddCheckpoint: () -> Unit,
    onAddStop: () -> Unit,
    onMoveHere: () -> Unit,
    onCancelMove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetVerticalSpacing)) {
        CompactHelperText(stringResource(R.string.route_builder_manual_plot_helper))
        if (isMovingPoint) {
            MoveControlsRow(onMoveHere, onCancelMove)
        } else {
            EditToolsSection(
                usesMoveEndpointLabels = usesMoveEndpointLabels,
                onSetStart = onSetStart,
                onSetFinish = onSetFinish,
                onShapeRoute = onShapeRoute,
                onAddCheckpoint = onAddCheckpoint,
                onAddStop = onAddStop,
            )
        }
        TipFooter()
    }
}

@Composable
private fun EditRouteContent(
    isMovingPoint: Boolean,
    onSetStart: () -> Unit,
    onSetFinish: () -> Unit,
    onShapeRoute: () -> Unit,
    onAddCheckpoint: () -> Unit,
    onAddStop: () -> Unit,
    onMoveHere: () -> Unit,
    onCancelMove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetVerticalSpacing)) {
        CompactHelperText(stringResource(R.string.route_builder_edit_route_helper))
        if (isMovingPoint) {
            MoveControlsRow(onMoveHere, onCancelMove)
        } else {
            EditToolsSection(
                usesMoveEndpointLabels = true,
                onSetStart = onSetStart,
                onSetFinish = onSetFinish,
                onShapeRoute = onShapeRoute,
                onAddCheckpoint = onAddCheckpoint,
                onAddStop = onAddStop,
            )
        }
        TipFooter()
    }
}

@Composable
private fun EditToolsSection(
    usesMoveEndpointLabels: Boolean,
    onSetStart: () -> Unit,
    onSetFinish: () -> Unit,
    onShapeRoute: () -> Unit,
    onAddCheckpoint: () -> Unit,
    onAddStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditActionCard(
                title = stringResource(R.string.route_builder_shape_route),
                subtitle = stringResource(R.string.route_builder_shape_route_helper),
                icon = Icons.Filled.Route,
                accent = RouteBuilderMarkerColors.pathPurple,
                onClick = onShapeRoute,
                modifier = Modifier.weight(1f),
            )
            EditActionCard(
                title = stringResource(R.string.route_builder_add_checkpoint),
                subtitle = stringResource(R.string.route_builder_add_checkpoint_helper),
                icon = Icons.Outlined.Flag,
                accent = RouteBuilderMarkerColors.checkpointBlue,
                onClick = onAddCheckpoint,
                modifier = Modifier.weight(1f),
            )
            EditActionCard(
                title = stringResource(R.string.route_builder_add_stop),
                subtitle = stringResource(R.string.route_builder_add_stop_helper),
                icon = Icons.Filled.Stop,
                accent = RouteBuilderMarkerColors.stopRed,
                onClick = onAddStop,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        EndpointMoveRow(
            usesMoveEndpointLabels = usesMoveEndpointLabels,
            onSetStart = onSetStart,
            onSetFinish = onSetFinish,
        )
    }
}

@Composable
private fun EndpointMoveRow(
    usesMoveEndpointLabels: Boolean,
    onSetStart: () -> Unit,
    onSetFinish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EndpointMoveButton(
            title =
                stringResource(
                    if (usesMoveEndpointLabels) {
                        R.string.route_builder_move_start_cta
                    } else {
                        R.string.route_builder_set_start_cta
                    },
                ),
            subtitle =
                if (usesMoveEndpointLabels) {
                    stringResource(R.string.route_builder_move_start_helper)
                } else {
                    null
                },
            icon = Icons.Filled.PlayArrow,
            iconColor = RouteBuilderMarkerColors.startAccent,
            subtitleColor = RouteBuilderMarkerColors.startAccent,
            backgroundColor = Color(red = 0.07f, green = 0.20f, blue = 0.16f),
            onClick = onSetStart,
            modifier = Modifier.weight(1f),
        )
        EndpointMoveButton(
            title =
                stringResource(
                    if (usesMoveEndpointLabels) {
                        R.string.route_builder_move_finish_cta
                    } else {
                        R.string.route_builder_set_finish_cta
                    },
                ),
            subtitle =
                if (usesMoveEndpointLabels) {
                    stringResource(R.string.route_builder_move_finish_helper)
                } else {
                    null
                },
            icon = Icons.Filled.SportsScore,
            iconColor = Color.White.copy(alpha = 0.88f),
            subtitleColor = Color.White.copy(alpha = 0.42f),
            backgroundColor = Color.White.copy(alpha = 0.08f),
            onClick = onSetFinish,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EndpointMoveButton(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    iconColor: Color,
    subtitleColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = if (subtitle == null) 14.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                subtitle?.let {
                    Text(it, color = subtitleColor, fontSize = 11.sp, lineHeight = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EditActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 13.sp,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                style =
                    TextStyle(
                        lineHeight = 11.sp,
                        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
                    ),
            )
        }
    }
}

@Composable
private fun CompactHelperText(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 13.sp,
        style =
            TextStyle(
                lineHeight = CompactHelperLineHeight,
                lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
            ),
    )
}

@Composable
private fun MoveControlsRow(
    onMoveHere: () -> Unit,
    onCancelMove: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            title = stringResource(R.string.route_builder_move_here),
            background = RouteBuilderMarkerColors.finishButton,
            icon = Icons.Filled.Check,
            onClick = onMoveHere,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancelMove, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_cancel), color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun PrimaryButton(
    title: String,
    background: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = background,
    ) {
        Row(
            Modifier.padding(vertical = 14.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun DensityStepButton(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = if (enabled) 0.08f else 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.12f else 0.06f)),
    ) {
        Row(
            Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) accent else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.route_builder_back), color = Color.White.copy(alpha = 0.72f))
    }
}

@Composable
private fun TipFooter() {
    Text(
        stringResource(R.string.route_builder_edit_route_tip),
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
