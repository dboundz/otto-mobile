package to.ottomot.driftd.routebuilder

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import to.ottomot.driftd.map.visibleLatitudeDeltaDegrees
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator

enum class RouteBuilderMapMarkerLodTier {
    REGIONAL,
    STREET,
    ;

    companion object {
        fun from(latitudeDelta: Double): RouteBuilderMapMarkerLodTier =
            if (latitudeDelta > RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA) {
                REGIONAL
            } else {
                STREET
            }
    }
}

/** Route Builder editor marker LOD, matching iOS dot/pin/scale behavior. */
object RouteBuilderMarkerLod {
    fun markerPresentation(
        point: RouteBuilderPoint,
        lodTier: RouteBuilderMapMarkerLodTier,
    ): RouteBuilderMapMarkerPresentation =
        if (point.type == RouteBuilderPointType.START || point.type == RouteBuilderPointType.FINISH) {
            RouteBuilderMapMarkerPresentation.ENDPOINT_PIN
        } else if (lodTier == RouteBuilderMapMarkerLodTier.REGIONAL) {
            RouteBuilderMapMarkerPresentation.DOT
        } else {
            RouteBuilderMapMarkerPresentation.PIN
        }

    fun markerPinScale(
        point: RouteBuilderPoint,
        latitudeDelta: Double,
    ): Float {
        if (!usesZoomAwareSubtleScale(point)) return 1f
        val close = RouteBuilderConstants.PIN_FULL_SIZE_MAX_LATITUDE_DELTA
        if (latitudeDelta <= close) return 1f
        val far = RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA
        val range = far - close
        if (range <= 0.0) return RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE
        val t = ((far - latitudeDelta) / range).coerceIn(0.0, 1.0).toFloat()
        return RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE +
            t * (1f - RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE)
    }

    fun markerRefreshId(
        point: RouteBuilderPoint,
        latitudeDelta: Double,
        lodTier: RouteBuilderMapMarkerLodTier,
    ): String =
        when (markerPresentation(point, lodTier)) {
            RouteBuilderMapMarkerPresentation.ENDPOINT_PIN -> point.id
            RouteBuilderMapMarkerPresentation.DOT -> "${point.id}-dot-$lodTier"
            RouteBuilderMapMarkerPresentation.PIN -> "${point.id}-pin-$lodTier-${pinScaleBucket(point, latitudeDelta)}"
        }

    fun pinScaleBucket(
        point: RouteBuilderPoint,
        latitudeDelta: Double,
    ): Int = pinScaleBucketForScale(markerPinScale(point, latitudeDelta))

    fun pinScaleBucketForScale(scale: Float): Int = (scale * 10f).roundToInt()

    fun secondaryPinScaleBucket(latitudeDelta: Double): Int {
        val syntheticWaypoint =
            RouteBuilderPoint(
                lat = 0.0,
                lng = 0.0,
                type = RouteBuilderPointType.WAYPOINT,
            )
        return pinScaleBucket(syntheticWaypoint, latitudeDelta)
    }

    fun zoomForCloseStreetLevel(latitudeCenter: Double): Double =
        zoomForLatitudeDelta(RouteBuilderConstants.CLOSE_ZOOM_LATITUDE_DELTA, latitudeCenter)

    fun zoomForLatitudeDelta(
        latitudeDelta: Double,
        latitudeCenter: Double,
    ): Double {
        val cosLat = abs(cos(Math.toRadians(latitudeCenter))).coerceAtLeast(0.2)
        val screenHeight = 640.0
        val metersPerPixel = (latitudeDelta * 111_000.0) / screenHeight
        return ln(156543.03392 * cosLat / metersPerPixel) / ln(2.0)
    }

    fun latitudeDeltaFromCamera(
        zoom: Double,
        latitudeCenter: Double,
    ): Double = visibleLatitudeDeltaDegrees(zoom, latitudeCenter)

    fun milesPerLatitudeDelta(): Double = 111_000.0 / RouteAutoCheckpointGenerator.Options.METERS_PER_MILE

    private fun usesZoomAwareSubtleScale(point: RouteBuilderPoint): Boolean =
        when (point.type) {
            RouteBuilderPointType.PATH,
            RouteBuilderPointType.WAYPOINT,
            RouteBuilderPointType.STOP,
            -> true
            RouteBuilderPointType.START,
            RouteBuilderPointType.FINISH,
            -> false
        }
}
