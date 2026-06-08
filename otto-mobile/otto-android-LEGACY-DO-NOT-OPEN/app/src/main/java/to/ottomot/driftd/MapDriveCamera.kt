package to.ottomot.driftd

import com.mapbox.maps.EdgeInsets
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import to.ottomot.driftd.core.location.LocationFix

/** Pitched navigation camera while driving (parity with iOS `OttoMapboxCamera`). */
internal object MapDriveCamera {
    const val DRIVE_PITCH_DEGREES = 60.0
    const val DRIVE_ZOOM = 17.5
    /** Screen Y fraction from top where the user sits during drive follow (iOS: 0.8 ≈ bottom 20%). */
    const val DRIVE_USER_ANCHOR_Y_FRACTION = 0.80
    const val DRIVE_CAMERA_TRANSITION_MS = 550L
    /** When [onDockHeightChanged] has not fired yet but the dock is visible. */
    const val DRIVE_DOCK_HEIGHT_FALLBACK_DP = 132.0
    /** iOS `stepDriveCameraSmoothing` — skip micro-updates that cause jitter. */
    const val DRIVE_MIN_CAMERA_MOVE_METERS = 0.15
    const val DRIVE_MIN_CAMERA_BEARING_DELTA_DEG = 0.2f
    /** Smoothing factor at 60 Hz (iOS per-frame constants). */
    const val DRIVE_POSITION_SMOOTH_PER_FRAME_60HZ = 0.38
    const val DRIVE_BEARING_SMOOTH_PER_FRAME_60HZ = 0.24f
    private const val REFERENCE_FRAME_MS = 1000.0 / 60.0
    /** Quantize chrome before map-surface padding effect re-runs. */
    private const val DRIVE_PADDING_QUANTIZE_PX = 8f
    private const val MIN_COURSE_SPEED_MPS = 2.0
    private const val MIN_MOVEMENT_BEARING_METERS = 3.0

    /**
     * Measured map-tab chrome for drive follow padding (px).
     * Dock band simulates iOS `safeAreaInset` shrinking the map above the drive dock.
     */
    data class DriveFollowChromeInsets(
        val mapViewportHeightPx: Float,
        val mapDriveDockHeightPx: Float,
        val mapOverlayBottomPadPx: Float,
    )

    /**
     * iOS-equivalent padding: top-only on effective map height (viewport minus dock band).
     * Matches `OttoMapboxCamera.driveFollowEdgeInsets` (top = 0.6 × height, bottom = 0).
     */
    fun driveFollowPadding(chrome: DriveFollowChromeInsets): EdgeInsets {
        val mapHeight = max(chrome.mapViewportHeightPx, 320f)
        val dockBandPx = (chrome.mapDriveDockHeightPx + chrome.mapOverlayBottomPadPx).coerceAtLeast(0f)
        val effectiveMapHeightPx = max(mapHeight - dockBandPx, 200f)
        val topPadding =
            effectiveMapHeightPx * max(0f, 2f * DRIVE_USER_ANCHOR_Y_FRACTION.toFloat() - 1f)
        return EdgeInsets(topPadding.toDouble(), 0.0, 0.0, 0.0)
    }

    fun driveBearing(
        fix: LocationFix?,
        previous: LocationFix?,
        fallback: Float = 0f,
    ): Float {
        if (fix == null) return normalizedBearing(fallback)
        val speed = fix.speedMps?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
        val bearing = fix.bearingDegrees
        if (speed >= MIN_COURSE_SPEED_MPS && bearing != null && bearing >= 0f) {
            return normalizedBearing(bearing)
        }
        if (previous != null) {
            val movedMeters =
                haversineMeters(
                    previous.latitude,
                    previous.longitude,
                    fix.latitude,
                    fix.longitude,
                )
            if (movedMeters >= MIN_MOVEMENT_BEARING_METERS) {
                return normalizedBearing(
                    bearingDegrees(
                        fromLat = previous.latitude,
                        fromLng = previous.longitude,
                        toLat = fix.latitude,
                        toLng = fix.longitude,
                    ),
                )
            }
        }
        return normalizedBearing(fallback)
    }

    /** Frame-rate-independent lerp factor matching [DRIVE_POSITION_SMOOTH_PER_FRAME_60HZ] at 60 Hz. */
    fun smoothAlpha(
        deltaMs: Long,
        perFrameFactorAt60Hz: Double,
    ): Double {
        if (deltaMs <= 0L) return perFrameFactorAt60Hz
        val clamped = perFrameFactorAt60Hz.coerceIn(0.0, 0.99)
        val dtSec = deltaMs / 1000.0
        val refSec = REFERENCE_FRAME_MS / 1000.0
        val rate = -ln(1.0 - clamped) / refSec
        return (1.0 - exp(-rate * dtSec)).coerceIn(0.0, 1.0)
    }

    fun driveFollowPaddingStableKey(chrome: DriveFollowChromeInsets): Long {
        fun q(value: Float): Long = (value / DRIVE_PADDING_QUANTIZE_PX).toLong()
        return (q(chrome.mapViewportHeightPx) shl 32) or
            (q(chrome.mapDriveDockHeightPx) shl 16) or
            q(chrome.mapOverlayBottomPadPx)
    }

    fun shouldStepDriveCamera(
        currentLat: Double,
        currentLng: Double,
        currentBearing: Float,
        newLat: Double,
        newLng: Double,
        newBearing: Float,
    ): Boolean {
        val movedMeters = distanceMeters(currentLat, currentLng, newLat, newLng)
        val bearingDelta = abs(shortPathBearingDelta(currentBearing, newBearing))
        return movedMeters > DRIVE_MIN_CAMERA_MOVE_METERS ||
            bearingDelta > DRIVE_MIN_CAMERA_BEARING_DELTA_DEG
    }

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double = haversineMeters(lat1, lon1, lat2, lon2)

    fun interpolateBearing(
        current: Float,
        target: Float,
        factor: Float,
    ): Float {
        val delta = shortPathBearingDelta(current, target)
        return normalizedBearing(current + delta * factor)
    }

    fun shortPathBearingDelta(
        from: Float,
        to: Float,
    ): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    fun interpolate(
        current: Double,
        target: Double,
        factor: Double,
    ): Double = current + (target - current) * factor

    private fun normalizedBearing(bearing: Float): Float {
        var value = bearing % 360f
        if (value < 0f) value += 360f
        return value
    }

    private fun bearingDegrees(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLng - fromLng)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return Math.toDegrees(atan2(y, x)).toFloat()
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
