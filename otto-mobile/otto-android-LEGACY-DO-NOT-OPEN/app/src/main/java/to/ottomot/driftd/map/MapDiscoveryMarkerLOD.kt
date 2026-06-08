package to.ottomot.driftd.map

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class MapDiscoveryMarkerKind {
    Event,
    RaceTrack,
    SavedPlace,
}

enum class MapDiscoveryMarkerPresentation {
    Dot,
    Pin,
}

object MapDiscoveryMarkerLOD {
    /** Miles of visible span above which discovery markers render as colored dots (pin below this). */
    private const val REGIONAL_DOT_MIN_MILES = 60.0

    /** Above ~60 mi visible span — discovery markers render as colored dots. */
    val regionalDotMinLatitudeDelta: Double = (REGIONAL_DOT_MIN_MILES * 1609.344) / 111_000.0

    /** ~1 mi visible span — discovery pins reach full size at or below this. */
    val pinFullSizeMaxLatitudeDelta: Double = (1 * 1609.344) / 111_000.0

    /** Pin scale at the regional dot→pin boundary; ramps linearly to 1.0 by ~1 mi. */
    const val PIN_MIN_SCALE = 0.55f

    fun presentation(latitudeDelta: Double): MapDiscoveryMarkerPresentation =
        if (latitudeDelta > regionalDotMinLatitudeDelta) {
            MapDiscoveryMarkerPresentation.Dot
        } else {
            MapDiscoveryMarkerPresentation.Pin
        }

    fun pinScale(latitudeDelta: Double): Float {
        val far = regionalDotMinLatitudeDelta
        val close = pinFullSizeMaxLatitudeDelta
        if (latitudeDelta <= close) return 1f
        val range = far - close
        if (range <= 0) return PIN_MIN_SCALE
        val t = min(1.0, max(0.0, (far - latitudeDelta) / range))
        return PIN_MIN_SCALE + t.toFloat() * (1f - PIN_MIN_SCALE)
    }

    fun dotColor(kind: MapDiscoveryMarkerKind): Color =
        when (kind) {
            MapDiscoveryMarkerKind.Event -> Color(0xFFF63887)
            MapDiscoveryMarkerKind.RaceTrack -> Color(0xFFFFA658)
            MapDiscoveryMarkerKind.SavedPlace -> Color(0xFF00A5AA)
        }

    /** Stable ViewAnnotation identity — tier only; pin scale updates in-place (no remount). */
    fun annotationRefreshId(
        id: String,
        kind: MapDiscoveryMarkerKind,
        latitudeDelta: Double,
    ): String =
        when (presentation(latitudeDelta)) {
            MapDiscoveryMarkerPresentation.Dot -> "$id-${kind.name}-dot"
            MapDiscoveryMarkerPresentation.Pin -> {
                val lod =
                    if (latitudeDelta <= pinFullSizeMaxLatitudeDelta) {
                        "full"
                    } else {
                        "scale"
                    }
                "$id-${kind.name}-pin-$lod"
            }
        }
}

internal fun visibleLatitudeDeltaDegrees(
    zoom: Double,
    latitudeCenterDegrees: Double,
): Double {
    val latSafe = latitudeCenterDegrees.takeIf { it.isFinite() } ?: 0.0
    val cosLat =
        kotlin.math
            .abs(kotlin.math.cos(Math.toRadians(latSafe)))
            .coerceAtLeast(0.2)
    val metersPerPixel = 156543.03392 * cosLat / 2.0.pow(zoom.coerceIn(4.0, 21.0))
    val approximateScreenHeightPx = 640.0
    val visibleHeightMeters = metersPerPixel * approximateScreenHeightPx
    return visibleHeightMeters / 111_000.0
}
