package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import to.ottomot.driftd.core.network.dto.CircleChatDriveAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatRouteAttachmentDto
import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.BuildConfig
@Composable
internal fun DriveRouteMapPreview(
    lineCoordinates: List<Point>,
    mapPoints: List<RouteMapPoint>,
    completedWaypointIndexes: Set<Int> = emptySet(),
    pathSamples: List<DrivePathSample> = emptyList(),
    height: Dp = 200.dp,
    lineSourceId: String = "drive-route-preview",
    useCyanPalette: Boolean = false,
    markerScale: Float = 0.55f,
    allowInteraction: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    if (lineCoordinates.size < 2 && !DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) {
        RouteMapPlaceholder(height, shape, modifier)
        return
    }

    val previewLine =
        remember(lineCoordinates, pathSamples) {
            if (DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) {
                DriveSpeedGradient.pathCoordinates(pathSamples)
            } else {
                lineCoordinates
            }
        }
    val palette = if (useCyanPalette) RouteMapLinePalette.BuilderCyan else RouteMapLinePalette.LivePurple
    val useSpeedGradient = DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)
    val mapViewportState = rememberMapViewportState()

    Box(
        modifier
            .height(height)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        if (BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()) {
            RouteMapFitCameraEffect(
                mapViewportState = mapViewportState,
                lineCoordinates = previewLine,
                mapPoints = mapPoints,
                paddingDp = 36.0,
            )
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                scaleBar = {},
                compass = {},
                attribution = {},
                style = { MapStyle(style = Style.DARK) },
            ) {
                RouteMapInteractionEffect(allowInteraction = allowInteraction)
                if (useSpeedGradient) {
                    RouteSpeedGradientMapEffect(
                        sourceId = "$lineSourceId-speed",
                        pathSamples = pathSamples,
                    )
                } else if (lineCoordinates.size >= 2) {
                    RouteMapLineMapEffect(
                        sourceId = lineSourceId,
                        lineCoordinates = lineCoordinates,
                        palette = palette,
                    )
                }

                mapPoints.forEachIndexed { index, point ->
                    val pt = Point.fromLngLat(point.lng, point.lat)
                    ViewAnnotation(
                        options = routeMapMarkerAnnotationOptions(pt, markerType = point.markerType, tieBreaker = index),
                    ) {
                        RouteMapMarkerView(
                            markerType = point.markerType,
                            isCompleted = point.isCompleted(completedWaypointIndexes),
                            scale = markerScale,
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0C10)),
            )
        }
    }
}

@Composable
private fun RouteMapPlaceholder(
    height: Dp,
    shape: RoundedCornerShape,
    modifier: Modifier,
) {
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Map,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(28.dp),
        )
    }
}

internal fun RouteMapPoint.isCompleted(completedWaypointIndexes: Set<Int>): Boolean =
    markerType == "waypoint" && completedWaypointIndexes.contains(index)

@Composable
internal fun DriveRouteMapPreviewFromDrive(
    drive: DriveDto,
    pathSamples: List<DrivePathSample> = emptyList(),
    height: Dp = 220.dp,
    lineSourceId: String = "drive-summary-${drive.id}",
    modifier: Modifier = Modifier,
) {
    val line = remember(drive.id, drive.route) { lineCoordinatesFromDrive(drive) }
    val points =
        remember(drive.id, drive.route, pathSamples) {
            val routePoints = mapPointsStartFinishFromDrive(drive, lineSourceId)
            routePoints.ifEmpty {
                mapPointsTrailEndpointsFromSamples(pathSamples, lineSourceId)
            }
        }
    val completed = remember(drive.route) {
        drive.route?.completedWaypointIndexes?.toSet() ?: emptySet()
    }
    DriveRouteMapPreview(
        lineCoordinates = line,
        mapPoints = points,
        completedWaypointIndexes = completed,
        pathSamples = pathSamples,
        height = height,
        lineSourceId = lineSourceId,
        modifier = modifier,
    )
}

@Composable
internal fun ChatRouteMapPreviewHero(
    attachment: CircleChatRouteAttachmentDto,
    height: Dp,
    lineSourceId: String,
    modifier: Modifier = Modifier,
) {
    val mapPreviewUrl = attachment.mapPreviewUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (mapPreviewUrl == null) {
        DriveRouteMapPreviewFromRouteAttachment(
            attachment = attachment,
            height = height,
            lineSourceId = lineSourceId,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val shape = RoundedCornerShape(12.dp)
    val ctx = LocalContext.current
    SubcomposeAsyncImage(
        model = ottoImageRequest(ctx, mapPreviewUrl),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        when (painter.state) {
            is coil.compose.AsyncImagePainter.State.Success ->
                SubcomposeAsyncImageContent(Modifier.fillMaxSize())
            is coil.compose.AsyncImagePainter.State.Error ->
                DriveRouteMapPreviewFromRouteAttachment(
                    attachment = attachment,
                    height = height,
                    lineSourceId = lineSourceId,
                    modifier = Modifier.fillMaxWidth(),
                )
            else -> RouteMapPlaceholder(height, shape, Modifier)
        }
    }
}

@Composable
internal fun DriveRouteMapPreviewFromRouteAttachment(
    attachment: CircleChatRouteAttachmentDto,
    height: Dp = 130.dp,
    lineSourceId: String,
    modifier: Modifier = Modifier,
) {
    val line = remember(attachment.routeId, attachment.roadCoordinates, attachment.routePoints) {
        lineCoordinatesFromRouteChatAttachment(attachment)
    }
    val points = remember(attachment.routeId, attachment.routePoints) {
        mapPointsFromRouteChatAttachment(attachment, lineSourceId)
            .filter { it.markerType == "start" || it.markerType == "finish" }
    }
    DriveRouteMapPreview(
        lineCoordinates = line,
        mapPoints = points,
        completedWaypointIndexes = emptySet(),
        height = height,
        lineSourceId = lineSourceId,
        markerScale = 0.52f,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ChatDriveMapPreviewHero(
    attachment: CircleChatDriveAttachmentDto,
    height: Dp,
    lineSourceId: String,
    modifier: Modifier = Modifier,
) {
    val mapPreviewUrl = attachment.mapPreviewUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (mapPreviewUrl == null) {
        DriveRouteMapPreviewFromAttachment(
            attachment = attachment,
            height = height,
            lineSourceId = lineSourceId,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val shape = RoundedCornerShape(12.dp)
    val ctx = LocalContext.current
    SubcomposeAsyncImage(
        model = ottoImageRequest(ctx, mapPreviewUrl),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        when (painter.state) {
            is coil.compose.AsyncImagePainter.State.Success ->
                SubcomposeAsyncImageContent(Modifier.fillMaxSize())
            is coil.compose.AsyncImagePainter.State.Error ->
                DriveRouteMapPreviewFromAttachment(
                    attachment = attachment,
                    height = height,
                    lineSourceId = lineSourceId,
                    modifier = Modifier.fillMaxWidth(),
                )
            else -> RouteMapPlaceholder(height, shape, Modifier)
        }
    }
}

@Composable
internal fun DriveRouteMapPreviewFromAttachment(
    attachment: CircleChatDriveAttachmentDto,
    height: Dp = 130.dp,
    lineSourceId: String,
    modifier: Modifier = Modifier,
) {
    val line = remember(attachment.driveId, attachment.roadCoordinates, attachment.routePoints) {
        lineCoordinatesFromChatAttachment(attachment)
    }
    val points = remember(attachment.driveId, attachment.routePoints) {
        mapPointsFromChatAttachment(attachment, lineSourceId)
            .filter { it.markerType == "start" || it.markerType == "finish" }
    }
    val completed = remember(attachment.completedWaypointIndexes) {
        attachment.completedWaypointIndexes.toSet()
    }
    DriveRouteMapPreview(
        lineCoordinates = line,
        mapPoints = points,
        completedWaypointIndexes = completed,
        height = height,
        lineSourceId = lineSourceId,
        markerScale = 0.52f,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DriveRouteMapPreviewFromSavedRoute(
    route: SavedRouteDto,
    height: Dp = 220.dp,
    lineSourceId: String = "saved-route-${route.id}",
) {
    val line = remember(route.id) { lineCoordinatesFromSavedRoute(route) }
    val points = remember(route.id) { mapPointsFromRoutePoints(route.points, lineSourceId) }
    DriveRouteMapPreview(
        lineCoordinates = line,
        mapPoints = points,
        height = height,
        lineSourceId = lineSourceId,
        useCyanPalette = true,
    )
}
