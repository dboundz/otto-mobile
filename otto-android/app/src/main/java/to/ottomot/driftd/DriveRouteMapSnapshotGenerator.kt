package to.ottomot.driftd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.ottomot.driftd.core.network.dto.DriveRouteDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Route geometry for generating a static map JPEG at drive-share time (client-side Mapbox Static Images). */
data class DriveMapPreviewSnapshotInput(
    val roadCoordinates: List<Point>,
    val routePoints: List<Point>,
    val pathSamples: List<DrivePathSample> = emptyList(),
) {
    data class Point(
        val lat: Double,
        val lng: Double,
        val type: String? = null,
    )

    val hasPathSamples: Boolean
        get() = pathSamples.count { it.lat.isFinite() && it.lng.isFinite() } >= 2

    companion object {
        fun fromRoute(
            route: DriveRouteDto?,
            pathSamples: List<DrivePathSample> = emptyList(),
        ): DriveMapPreviewSnapshotInput? {
            val road = route?.roadCoordinates.orEmpty().map { Point(it.lat, it.lng) }
            val points = route?.points.orEmpty().map { Point(it.lat, it.lng, it.markerType) }
            if (!hasDrawableLine(road, points, pathSamples)) {
                return null
            }
            return DriveMapPreviewSnapshotInput(road, points, pathSamples)
        }

        fun fromSavedRoute(route: SavedRouteDto): DriveMapPreviewSnapshotInput? {
            val road = route.roadCoordinates.orEmpty().map { Point(it.lat, it.lng) }
            val points = route.points.orEmpty().map { Point(it.lat, it.lng, it.markerType) }
            if (!hasDrawableLine(road, points, emptyList())) {
                return null
            }
            return DriveMapPreviewSnapshotInput(road, points)
        }

        fun hasDrawableLine(
            roadCoordinates: List<Point>,
            routePoints: List<Point>,
            pathSamples: List<DrivePathSample>,
        ): Boolean {
            if (DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) return true
            return DriveRouteMapSnapshotGenerator.lineCoordinates(pathSamples, roadCoordinates, routePoints).size >= 2
        }
    }
}

object DriveMapPreviewSnapshotResolver {
    suspend fun resolve(
        preloaded: DriveMapPreviewSnapshotInput?,
        driveId: String,
        circleId: String?,
        dataRepository: to.ottomot.driftd.core.data.OttoDataRepository,
    ): DriveMapPreviewSnapshotInput? {
        if (preloaded?.hasPathSamples == true) {
            return preloaded
        }

        val fetchedSamples =
            dataRepository.fetchDrivePathSamples(driveId, circleId).getOrElse { emptyList() }

        if (preloaded != null) {
            val enriched =
                DriveMapPreviewSnapshotInput(
                    roadCoordinates = preloaded.roadCoordinates,
                    routePoints = preloaded.routePoints,
                    pathSamples = fetchedSamples,
                )
            if (!DriveMapPreviewSnapshotInput.hasDrawableLine(
                    enriched.roadCoordinates,
                    enriched.routePoints,
                    enriched.pathSamples,
                )
            ) {
                return null
            }
            return enriched
        }

        if (fetchedSamples.size >= 2) {
            return DriveMapPreviewSnapshotInput(
                roadCoordinates = emptyList(),
                routePoints = emptyList(),
                pathSamples = fetchedSamples,
            )
        }

        val drive = dataRepository.fetchDrive(driveId, circleId).getOrNull() ?: return null
        return DriveMapPreviewSnapshotInput.fromRoute(drive.route, fetchedSamples)
    }
}

object DriveRouteMapSnapshotGenerator {
    private const val STATIC_WIDTH = 640
    private const val STATIC_HEIGHT = 280
    private const val STATIC_PADDING = 48
    private const val POLYLINE_MAX_POINTS = 100
    private const val GRADIENT_SEGMENT_MAX_COUNT = 50
    private const val ROUTE_LINE_COLOR = "7B3DFF"
    private const val ROUTE_LINE_WIDTH = 5

    /** Fetches a Mapbox Static Image using the app token and returns JPEG bytes for chat upload. */
    suspend fun jpegData(input: DriveMapPreviewSnapshotInput): ByteArray? =
        withContext(Dispatchers.IO) {
            val token = BuildConfig.MAPBOX_ACCESS_TOKEN.trim().takeIf { it.isNotEmpty() } ?: return@withContext null
            val staticUrl = buildStaticImageUrl(input, token) ?: return@withContext null
            runCatching {
                val connection = (URL(staticUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    requestMethod = "GET"
                    useCaches = false
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@withContext null
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                ByteArrayOutputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 84, stream)) {
                        return@withContext null
                    }
                    stream.toByteArray()
                }
            }.getOrNull()
        }

    internal fun lineCoordinates(
        pathSamples: List<DrivePathSample>,
        roadCoordinates: List<DriveMapPreviewSnapshotInput.Point>,
        routePoints: List<DriveMapPreviewSnapshotInput.Point>,
    ): List<DriveMapPreviewSnapshotInput.Point> {
        if (DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) {
            val points =
                DriveSpeedGradient.pathCoordinates(pathSamples).map {
                    DriveMapPreviewSnapshotInput.Point(it.latitude(), it.longitude())
                }
            return downsample(points, POLYLINE_MAX_POINTS)
        }
        if (roadCoordinates.size >= 2) {
            return downsample(roadCoordinates, POLYLINE_MAX_POINTS)
        }
        val path = routePoints.filter { (it.type ?: "path") == "path" }
        if (path.size >= 2) {
            return downsample(path, POLYLINE_MAX_POINTS)
        }
        return downsample(routePoints, POLYLINE_MAX_POINTS)
    }

    internal fun buildStaticImageUrl(
        input: DriveMapPreviewSnapshotInput,
        accessToken: String,
    ): String? {
        val overlays = buildList {
            addAll(lineOverlays(input))
            addAll(startFinishPins(input))
        }
        if (overlays.isEmpty()) return null

        val overlaySegment =
            overlays.joinToString(",") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name())
                    .replace("+", "%20")
            }
        val query =
            buildString {
                append("padding=$STATIC_PADDING")
                append("&access_token=")
                append(URLEncoder.encode(accessToken, Charsets.UTF_8.name()))
            }
        return "https://api.mapbox.com/styles/v1/mapbox/dark-v11/static/" +
            "$overlaySegment/auto/${STATIC_WIDTH}x${STATIC_HEIGHT}@2x?$query"
    }

    private fun lineOverlays(input: DriveMapPreviewSnapshotInput): List<String> {
        if (DriveSpeedGradient.hasUsableSpeedPathData(input.pathSamples)) {
            return DriveSpeedGradient.buildGradientSegments(
                input.pathSamples,
                idPrefix = "share-speed",
                maxCount = GRADIENT_SEGMENT_MAX_COUNT,
            ).mapNotNull { segment ->
                if (segment.coordinates.size < 2) return@mapNotNull null
                val points =
                    segment.coordinates.map {
                        DriveMapPreviewSnapshotInput.Point(it.latitude(), it.longitude())
                    }
                val encoded = encodePolyline(points)
                val color = String.format("%06X", segment.color and 0xFFFFFF)
                "path-$ROUTE_LINE_WIDTH+$color-0.92(polyline($encoded))"
            }
        }

        val line = lineCoordinates(input.pathSamples, input.roadCoordinates, input.routePoints)
        if (line.size < 2) return emptyList()
        val encoded = encodePolyline(line)
        return listOf("path-$ROUTE_LINE_WIDTH+$ROUTE_LINE_COLOR-0.92(polyline($encoded))")
    }

    private fun downsample(
        points: List<DriveMapPreviewSnapshotInput.Point>,
        maxCount: Int,
    ): List<DriveMapPreviewSnapshotInput.Point> {
        if (points.size <= maxCount) return points
        val stride = ceil(points.size.toDouble() / maxCount.toDouble()).toInt().coerceAtLeast(1)
        val out = mutableListOf<DriveMapPreviewSnapshotInput.Point>()
        var index = 0
        while (index < points.size) {
            out += points[index]
            index += stride
        }
        val last = points.last()
        if (out.last().lat != last.lat || out.last().lng != last.lng) {
            out += last
        }
        return out
    }

    private fun startFinishPins(input: DriveMapPreviewSnapshotInput): List<String> {
        val routePins =
            input.routePoints.mapNotNull { point ->
                when (point.type) {
                    "start" -> "pin-s-a+$ROUTE_LINE_COLOR(${point.lng},${point.lat})"
                    "finish" -> "pin-s-b+$ROUTE_LINE_COLOR(${point.lng},${point.lat})"
                    else -> null
                }
            }
        if (routePins.size >= 2) return routePins

        val valid = input.pathSamples.filter { it.lat.isFinite() && it.lng.isFinite() }
        if (valid.size < 2) return routePins
        val first = valid.first()
        val last = valid.last()
        return listOf(
            "pin-s-a+$ROUTE_LINE_COLOR(${first.lng},${first.lat})",
            "pin-s-b+$ROUTE_LINE_COLOR(${last.lng},${last.lat})",
        )
    }

    private fun encodePolyline(coordinates: List<DriveMapPreviewSnapshotInput.Point>): String {
        var lastLat = 0
        var lastLng = 0
        val result = StringBuilder()
        for (point in coordinates) {
            val lat = (point.lat * 1e5).roundToInt()
            val lng = (point.lng * 1e5).roundToInt()
            result.append(encodeSignedVarint(lat - lastLat))
            result.append(encodeSignedVarint(lng - lastLng))
            lastLat = lat
            lastLng = lng
        }
        return result.toString()
    }

    private fun encodeSignedVarint(value: Int): String {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        val encoded = StringBuilder()
        while (v >= 0x20) {
            encoded.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        encoded.append((v + 63).toChar())
        return encoded.toString()
    }
}
