package to.ottomot.driftd.routebuilder

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.ottomot.driftd.BuildConfig

private const val LOG_TAG = "RouteBuilderMarkers"

/** DEBUG-only marker diagnostics for Route Builder map sync. */
data class RouteBuilderMarkerDebugInputs(
    val screenState: RouteBuilderScreenState,
    val lastSnapTurnCount: Int,
)

data class RouteBuilderMarkerDebugSnapshot(
    val pointsCount: Int = 0,
    val syncedFeatureCount: Int = 0,
    val lastSnapTurnCount: Int = 0,
    val pointsAutoShapeCount: Int = 0,
    val pointsStart: Int = 0,
    val pointsFinish: Int = 0,
    val pointsWaypoint: Int = 0,
    val pointsStop: Int = 0,
    val pointsPathUser: Int = 0,
    val pointsPathAuto: Int = 0,
    val markersStart: Int = 0,
    val markersFinish: Int = 0,
    val markersWaypoint: Int = 0,
    val markersStop: Int = 0,
    val markersPathUser: Int = 0,
    val markersPathAuto: Int = 0,
    val imageInstallResults: Map<String, String> = emptyMap(),
    val sampleWaypointPinScale: Float? = null,
    val sampleWaypointPresentation: String? = null,
    val waypointProbeHitCount: Int? = null,
    val pathAutoProbeHitCount: Int? = null,
    val lastMissingStyleImage: String? = null,
) {
    fun overlayLines(): List<String> =
        listOf(
            "points=$pointsCount synced=$syncedFeatureCount turns=$lastSnapTurnCount autoShape=$pointsAutoShapeCount",
            "  start=$pointsStart finish=$pointsFinish wp=$pointsWaypoint stop=$pointsStop path=$pointsPathUser pathAuto=$pointsPathAuto",
            "  mStart=$markersStart mFinish=$markersFinish mWp=$markersWaypoint mStop=$markersStop mPath=$markersPathUser mPathAuto=$markersPathAuto",
            "sample wp scale=${sampleWaypointPinScale ?: "?"} pres=${sampleWaypointPresentation ?: "?"}",
            "markers: PointAnnotation sync (wp=${waypointProbeHitCount ?: "?"} pathAuto=${pathAutoProbeHitCount ?: "?"})",
        )

    private fun formatImageResults(): String =
        listOf("start", "finish", "waypoint", "stop", "path")
            .joinToString(" ") { type ->
                val id = "otto-rb-$type"
                "$type=${imageInstallResults[id] ?: "?"}"
            }
}

internal object RouteBuilderMarkerDebugLog {
    fun sync(
        pointsCount: Int,
        syncedCount: Int,
        markersByType: Map<String, Int>,
        autoShapeInPoints: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            LOG_TAG,
            "sync points=$pointsCount synced=$syncedCount autoShape=$autoShapeInPoints markers=$markersByType",
        )
    }

    fun addImage(
        imageId: String,
        ok: Boolean,
        error: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "addImage $imageId ${if (ok) "ok" else "err:$error"}")
    }

    fun autoPathBootstrap(source: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "autoPathBootstrap source=$source")
    }

    fun syncAutoPath(
        source: String,
        turnCount: Int,
        autoPointCount: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "syncAutoPath source=$source turns=$turnCount autoPoints=$autoPointCount")
    }

    fun styleImageMissing(imageId: String) {
        if (!BuildConfig.DEBUG) return
        Log.w(LOG_TAG, "StyleImageMissing id=$imageId")
    }

    fun layerProbe(
        label: String,
        lat: Double,
        lng: Double,
        hitCount: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "probe@$label lat=$lat lng=$lng hits=$hitCount")
    }

    fun checkpointGen(
        added: Int,
        totalWp: Int,
        spacingMeters: Double,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "checkpointGen added=$added totalWp=$totalWp spacing=$spacingMeters")
    }

    fun mapMarkers(
        total: Int,
        waypointCount: Int,
        markersByType: Map<String, Int>,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "mapMarkers total=$total wp=$waypointCount byType=$markersByType")
    }

    fun annotationSync(options: Int) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, "annotationSync options=$options")
    }

    fun saveRoute(
        pointCount: Int,
        waypointCount: Int,
        stopCount: Int,
        pathCount: Int,
        typeCounts: Map<String, Int>,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            LOG_TAG,
            "saveRoute points=$pointCount wp=$waypointCount stop=$stopCount path=$pathCount types=$typeCounts",
        )
    }

    fun savedRouteFromApi(
        routeId: String,
        waypointCount: Int,
        pointCount: Int,
        typeCounts: Map<String, Int>,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            LOG_TAG,
            "savedRouteFromApi id=$routeId points=$pointCount wp=$waypointCount types=$typeCounts",
        )
    }
}

@Composable
internal fun RouteBuilderMarkerDebugOverlay(
    snapshot: RouteBuilderMarkerDebugSnapshot?,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG || snapshot == null) return
    Column(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        snapshot.overlayLines().forEach { line ->
            Text(
                text = line,
                color = Color(0xFF7CFF9E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp,
            )
        }
    }
}

internal fun buildMarkerDebugSnapshotFromState(
    state: RouteBuilderScreenState,
    lastSnapTurnCount: Int,
    syncedFeatureCount: Int,
    imageInstallResults: Map<String, String>,
    waypointProbeHitCount: Int?,
    pathAutoProbeHitCount: Int?,
    lastMissingStyleImage: String?,
): RouteBuilderMarkerDebugSnapshot {
    val points = state.points
    val markers = state.mapContent.markers
    val sampleWaypoint = markers.firstOrNull { it.markerType == "waypoint" }
    return RouteBuilderMarkerDebugSnapshot(
        pointsCount = points.size,
        syncedFeatureCount = syncedFeatureCount,
        lastSnapTurnCount = lastSnapTurnCount,
        pointsAutoShapeCount = points.count { it.isAutoShape },
        pointsStart = points.count { it.type == RouteBuilderPointType.START },
        pointsFinish = points.count { it.type == RouteBuilderPointType.FINISH },
        pointsWaypoint = points.count { it.type == RouteBuilderPointType.WAYPOINT },
        pointsStop = points.count { it.type == RouteBuilderPointType.STOP },
        pointsPathUser = points.count { it.type == RouteBuilderPointType.PATH && !it.isAutoShape },
        pointsPathAuto = points.count { it.type == RouteBuilderPointType.PATH && it.isAutoShape },
        markersStart = markers.count { it.markerType == "start" },
        markersFinish = markers.count { it.markerType == "finish" },
        markersWaypoint = markers.count { it.markerType == "waypoint" },
        markersStop = markers.count { it.markerType == "stop" },
        markersPathUser = markers.count { it.markerType == "path" && !it.isAutoShape },
        markersPathAuto = markers.count { it.markerType == "path" && it.isAutoShape },
        imageInstallResults = imageInstallResults,
        sampleWaypointPinScale = sampleWaypoint?.pinScale,
        sampleWaypointPresentation = sampleWaypoint?.presentation?.name,
        waypointProbeHitCount = waypointProbeHitCount,
        pathAutoProbeHitCount = pathAutoProbeHitCount,
        lastMissingStyleImage = lastMissingStyleImage,
    )
}
