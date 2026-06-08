package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.BuildConfig
@Composable
internal fun DriveTrailMapScreen(
    drive: DriveDto,
    onClose: () -> Unit,
    onFetchPathSamples: suspend (String, String?) -> List<DrivePathSample>,
    lockedShareCircleId: String? = null,
) {
    val title = DriveDisplayNaming.listTitle(drive)
    val timestamp = formatDriveCompletedAt(drive.endTime ?: drive.startTime)
    val driveSeconds = driveTimeSecondsBetween(drive.startTime, drive.endTime).toDouble()
    var pathSamples by remember(drive.id) { mutableStateOf<List<DrivePathSample>>(emptyList()) }
    var isLoading by remember(drive.id) { mutableStateOf(true) }

    LaunchedEffect(drive.id, drive.pointsCount, lockedShareCircleId) {
        isLoading = true
        pathSamples =
            if ((drive.pointsCount ?: 0) >= 2) {
                onFetchPathSamples(drive.id, lockedShareCircleId)
            } else {
                emptyList()
            }
        isLoading = false
    }

    val hasDrawableTrail = DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)
    val fallbackLine = remember(drive.id, drive.route) { lineCoordinatesFromDrive(drive) }
    val hasFallbackRoute = fallbackLine.size >= 2
    var recenterToken by remember(drive.id) { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7B3DFF))
                }
            }
            hasDrawableTrail -> {
                DriveTrailInteractiveMap(
                    pathSamples = pathSamples,
                    lineCoordinates = emptyList(),
                    mapPoints = mapPointsTrailEndpointsFromSamples(pathSamples, "drive-trail-${drive.id}"),
                    modifier = Modifier.fillMaxSize(),
                    markerScale = 0.72f,
                    recenterToken = recenterToken,
                )
            }
            hasFallbackRoute -> {
                DriveTrailInteractiveMap(
                    pathSamples = emptyList(),
                    lineCoordinates = fallbackLine,
                    mapPoints = mapPointsStartFinishFromDrive(drive, "drive-trail-fallback-${drive.id}"),
                    modifier = Modifier.fillMaxSize(),
                    markerScale = 0.72f,
                    recenterToken = recenterToken,
                )
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.drive_trail_unavailable_title),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.drive_trail_unavailable_body),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.settings_cancel), tint = Color.White)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                    if (timestamp.isNotBlank()) {
                        Text(
                            timestamp,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.58f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.size(36.dp))
            }

            Spacer(Modifier.weight(1f))

            if (!isLoading && (hasDrawableTrail || hasFallbackRoute)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = { recenterToken += 1 },
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.86f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.drive_trail_recenter_cd), tint = Color.White)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .ottoBottomSheetContent()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasDrawableTrail) {
                    DriveSpeedGradientLegend()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrailStatChip(Icons.Outlined.AddRoad, formatDriveDistanceMiles(drive.distanceMeters), stringResource(R.string.drive_summary_stat_distance), Modifier.weight(1f))
                    TrailStatChip(Icons.Outlined.Timer, formatDriveDurationSeconds(driveSeconds), stringResource(R.string.drive_summary_stat_drive_time), Modifier.weight(1f))
                    TrailStatChip(Icons.Outlined.Speed, formatDriveAverageSpeedMph(drive), stringResource(R.string.drive_summary_stat_avg_pace), Modifier.weight(1f))
                    TrailStatChip(Icons.Outlined.Route, "${drive.pointsCount ?: 0}", stringResource(R.string.drive_summary_stat_samples), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrailStatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF7B3DFF), modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.55f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun DriveTrailInteractiveMap(
    pathSamples: List<DrivePathSample>,
    mapPoints: List<RouteMapPoint>,
    modifier: Modifier = Modifier,
    lineCoordinates: List<Point> = emptyList(),
    markerScale: Float = 0.72f,
    recenterToken: Int = 0,
) {
    val useSpeedGradient = DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)
    val previewLine =
        remember(pathSamples, lineCoordinates) {
            if (useSpeedGradient) DriveSpeedGradient.pathCoordinates(pathSamples) else lineCoordinates
        }
    val sourceId = remember(pathSamples, lineCoordinates) { "drive-trail-map-${previewLine.size}-${mapPoints.size}" }
    val mapViewportState = rememberMapViewportState()

    Box(modifier) {
        if (BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()) {
            RouteMapFitCameraEffect(
                mapViewportState = mapViewportState,
                lineCoordinates = previewLine,
                mapPoints = mapPoints,
                paddingDp = 56.0,
                recenterToken = recenterToken,
            )
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                scaleBar = {},
                compass = {},
                attribution = {},
                style = { MapStyle(style = Style.DARK) },
            ) {
                RouteMapInteractionEffect(allowInteraction = true)
                if (useSpeedGradient) {
                    RouteSpeedGradientMapEffect(sourceId = "$sourceId-speed", pathSamples = pathSamples)
                }
                if (!useSpeedGradient && lineCoordinates.size >= 2) {
                    RouteMapLineMapEffect(sourceId = sourceId, lineCoordinates = lineCoordinates)
                }

                mapPoints.forEachIndexed { index, point ->
                    val pt = Point.fromLngLat(point.lng, point.lat)
                    ViewAnnotation(
                        options = routeMapMarkerAnnotationOptions(pt, markerType = point.markerType, tieBreaker = index),
                    ) {
                        RouteMapMarkerView(
                            markerType = point.markerType,
                            isCompleted = false,
                            scale = markerScale,
                        )
                    }
                }
            }
        }
    }
}
