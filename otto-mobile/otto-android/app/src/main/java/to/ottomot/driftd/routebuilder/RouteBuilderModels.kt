package to.ottomot.driftd.routebuilder

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Immutable
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng
import java.util.UUID
import kotlin.math.roundToLong

enum class RouteBuilderPointType(val rawValue: String) {
    START("start"),
    PATH("path"),
    WAYPOINT("waypoint"),
    STOP("stop"),
    FINISH("finish"),
    ;

    val displayTitle: String
        get() =
            when (this) {
                START -> "Start"
                PATH -> "Path Marker"
                WAYPOINT -> "Checkpoint"
                STOP -> "Stop"
                FINISH -> "Finish"
            }

    val dotColor: Color
        get() =
            when (this) {
                START -> RouteBuilderMarkerColors.startButton
                PATH -> RouteBuilderMarkerColors.pathPurple
                WAYPOINT -> RouteBuilderMarkerColors.checkpointBlue
                STOP -> RouteBuilderMarkerColors.stopRed
                FINISH -> Color.White
            }
}

data class RouteBuilderPoint(
    val id: String = UUID.randomUUID().toString(),
    val lat: Double,
    val lng: Double,
    val type: RouteBuilderPointType,
    val isAutoShape: Boolean = false,
) {
    val coordinate: RouteLatLng get() = lat to lng

    val isLockedIntentional: Boolean get() = !isAutoShape

    val displayTitle: String
        get() = if (isAutoShape) "Route Bend" else type.displayTitle

    fun affectsRouting(activeDragPointId: String? = null): Boolean =
        when (type) {
            RouteBuilderPointType.WAYPOINT,
            RouteBuilderPointType.STOP,
            -> false
            RouteBuilderPointType.PATH ->
                if (isAutoShape) {
                    id == activeDragPointId
                } else {
                    true
                }
            RouteBuilderPointType.START,
            RouteBuilderPointType.FINISH,
            -> true
        }
}

data class RouteBuilderEditSnapshot(
    val intentionalPoints: List<RouteBuilderPoint>,
    val roadCoordinates: List<RouteLatLng>,
    val distanceMeters: Double,
    val travelSeconds: Double,
    val didSnapToRoad: Boolean,
    val lastSnapTurnCoordinates: List<RouteLatLng>,
    val selectedCheckpointDensity: CheckpointDensityTier,
) {
    companion object {
        fun capture(
            points: List<RouteBuilderPoint>,
            roadCoordinates: List<RouteLatLng>,
            distanceMeters: Double,
            travelSeconds: Double,
            didSnapToRoad: Boolean,
            lastSnapTurnCoordinates: List<RouteLatLng>,
            selectedCheckpointDensity: CheckpointDensityTier,
        ): RouteBuilderEditSnapshot =
            RouteBuilderEditSnapshot(
                intentionalPoints = points.filter { !it.isAutoShape },
                roadCoordinates = roadCoordinates,
                distanceMeters = distanceMeters,
                travelSeconds = travelSeconds,
                didSnapToRoad = didSnapToRoad,
                lastSnapTurnCoordinates = lastSnapTurnCoordinates,
                selectedCheckpointDensity = selectedCheckpointDensity,
            )
    }
}

enum class RouteBuilderMapMarkerPresentation {
    ENDPOINT_PIN,
    DOT,
    PIN,
}

@Immutable
data class RouteBuilderMapMarkerSnapshot(
    val id: String,
    val lat: Double,
    val lng: Double,
    val markerType: String,
    val isAutoShape: Boolean,
    val presentation: RouteBuilderMapMarkerPresentation,
    val pinScale: Float,
    val dotColor: Color,
    val accessibilityTitle: String,
    val refreshId: String,
    val originalIndex: Int,
)

data class RouteEditBaseline(
    val name: String,
    val pointSignatures: List<String>,
    val roadSignatures: List<String>,
    val distanceMeters: Double,
    val travelSeconds: Double,
) {
    companion object {
        fun capture(
            name: String,
            points: List<RouteBuilderPoint>,
            roadCoordinates: List<RouteLatLng>,
            distanceMeters: Double,
            travelSeconds: Double,
        ): RouteEditBaseline =
            RouteEditBaseline(
                name = name.trim(),
                pointSignatures = points.filter { !it.isAutoShape }.map(::pointSignature),
                roadSignatures = roadCoordinates.map(::coordinateSignature),
                distanceMeters = roundedDistance(distanceMeters),
                travelSeconds = travelSeconds.roundToLong().toDouble(),
            )

        private fun pointSignature(point: RouteBuilderPoint): String =
            "${point.type.rawValue}|${coordinateSignature(point.coordinate)}"

        private fun coordinateSignature(coordinate: RouteLatLng): String =
            String.format("%.5f,%.5f", coordinate.lat, coordinate.lng)

        private fun roundedDistance(value: Double): Double = (value * 10).roundToLong() / 10.0
    }
}

data class RouteBuilderCameraRegion(
    val centerLat: Double,
    val centerLng: Double,
    val latitudeDelta: Double,
    val longitudeDelta: Double,
)

@Immutable
data class RouteBuilderCameraTarget(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
)

object RouteBuilderConstants {
    /** Minimum drive distance — 1,000 ft in meters. */
    const val MINIMUM_ROUTE_DRIVE_DISTANCE_METERS = 304.8

    /** Close street-level zoom (~0.5 mi visible). */
    const val CLOSE_ZOOM_LATITUDE_DELTA = 0.008

    /** Above ~5 mi visible span — checkpoint, stop, and path markers render as dots. */
    val REGIONAL_DOT_MIN_LATITUDE_DELTA: Double =
        (5 * RouteAutoCheckpointGenerator.Options.METERS_PER_MILE) / 111_000.0

    /** ~1,000 ft visible span — secondary marker pins reach full size at or below this. */
    val PIN_FULL_SIZE_MAX_LATITUDE_DELTA: Double = (1000 * 0.3048) / 111_000.0

    const val MAX_UNDO_STACK = 50
    const val ROUTE_BUILDER_EDUCATION_SEEN_KEY = "otto.routeBuilderEducationSeen"
    const val SUBTLE_MARKER_MIN_SCALE = 0.55f
    const val ROUTE_MARKER_LONG_PRESS_EXCLUSION_METERS = 55.0
}

object RouteBuilderMarkerColors {
    val startButton = Color(red = 0.06f, green = 0.50f, blue = 0.24f)
    val startAccent = Color(red = 0.24f, green = 0.68f, blue = 0.38f)
    val finishButton = Color(red = 0.55f, green = 0.36f, blue = 0.96f)
    val checkpointBlue = Color(red = 0.22f, green = 0.52f, blue = 0.98f)
    val stopRed = Color(red = 0.95f, green = 0.23f, blue = 0.21f)
    val pathPurple = finishButton
}
