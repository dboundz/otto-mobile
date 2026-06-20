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
    private const val LIVE_LOCATION_MIN_ANIMATION_MS = 250L
    private const val LIVE_LOCATION_MAX_ANIMATION_MS = 2_000L
    private const val LIVE_LOCATION_POOR_ACCURACY_METERS = 100f
    private const val LIVE_LOCATION_SUSPICIOUS_ACCURACY_METERS = 50f
    private const val LIVE_LOCATION_STATIONARY_SPEED_MPS = 0.75
    private const val LIVE_LOCATION_STATIONARY_JITTER_METERS = 4.0

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

    data class LiveLocationFrame(
        val latitude: Double,
        val longitude: Double,
        val bearing: Float,
        val isAnimating: Boolean,
    )

    class LiveLocationSmoothingController {
        private var previousFix: LocationFix? = null
        private var lastReceiveMs: Long? = null
        private var animationStartMs: Long? = null
        private var animationDurationMs: Long = LIVE_LOCATION_MIN_ANIMATION_MS
        private var startLat: Double? = null
        private var startLng: Double? = null
        private var targetLat: Double? = null
        private var targetLng: Double? = null
        private var startBearing = 0f
        private var targetBearing = 0f
        private var lastFixSourceKey: String? = null
        private var lastDriveMode = false
        var renderedLat: Double? = null
            private set
        var renderedLng: Double? = null
            private set
        var renderedBearing = 0f
            private set

        val currentTargetLat: Double?
            get() = targetLat

        val currentTargetLng: Double?
            get() = targetLng

        val currentTargetBearing: Float
            get() = targetBearing

        fun clear() {
            previousFix = null
            lastReceiveMs = null
            animationStartMs = null
            startLat = null
            startLng = null
            targetLat = null
            targetLng = null
            startBearing = 0f
            targetBearing = 0f
            lastFixSourceKey = null
            lastDriveMode = false
            renderedLat = null
            renderedLng = null
            renderedBearing = 0f
        }

        fun reset(
            fix: LocationFix,
            bearing: Float,
            nowMs: Long,
            isDriveMode: Boolean,
        ) {
            previousFix = fix
            lastReceiveMs = nowMs
            animationStartMs = null
            animationDurationMs = LIVE_LOCATION_MIN_ANIMATION_MS
            startLat = fix.latitude
            startLng = fix.longitude
            targetLat = fix.latitude
            targetLng = fix.longitude
            startBearing = bearing
            targetBearing = bearing
            lastFixSourceKey = fix.sourceKey()
            lastDriveMode = isDriveMode
            renderedLat = fix.latitude
            renderedLng = fix.longitude
            renderedBearing = bearing
        }

        fun onLocationUpdate(
            fix: LocationFix,
            isDriveMode: Boolean,
            nowMs: Long,
        ): Boolean {
            if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return false
            val sourceKey = fix.sourceKey()
            if (sourceKey == lastFixSourceKey && isDriveMode == lastDriveMode && previousFix != null) {
                return false
            }
            if ((fix.accuracyMeters ?: 0f) > LIVE_LOCATION_POOR_ACCURACY_METERS && previousFix != null) {
                return false
            }
            if (isSuspiciousJump(fix, nowMs)) return false

            val currentFrame = frame(nowMs)
            val currentLat = currentFrame?.latitude ?: renderedLat
            val currentLng = currentFrame?.longitude ?: renderedLng
            val nextBearing =
                if (isDriveMode) {
                    driveBearing(fix, previousFix, targetBearing)
                } else {
                    0f
                }
            if (currentLat == null || currentLng == null) {
                reset(fix, nextBearing, nowMs, isDriveMode)
                return true
            }

            val distanceToTarget = distanceMeters(currentLat, currentLng, fix.latitude, fix.longitude)
            val speed = (fix.speedMps ?: 0f).coerceAtLeast(0f).toDouble()
            if (speed < LIVE_LOCATION_STATIONARY_SPEED_MPS &&
                distanceToTarget < LIVE_LOCATION_STATIONARY_JITTER_METERS
            ) {
                previousFix = fix
                lastReceiveMs = nowMs
                animationStartMs = null
                startLat = currentLat
                startLng = currentLng
                targetLat = currentLat
                targetLng = currentLng
                startBearing = renderedBearing
                targetBearing = nextBearing
                renderedLat = currentLat
                renderedLng = currentLng
                renderedBearing = nextBearing
                lastFixSourceKey = sourceKey
                lastDriveMode = isDriveMode
                return true
            }

            animationStartMs = nowMs
            animationDurationMs =
                sampleIntervalMs(fix, nowMs)
                    .coerceIn(LIVE_LOCATION_MIN_ANIMATION_MS, LIVE_LOCATION_MAX_ANIMATION_MS)
            startLat = currentLat
            startLng = currentLng
            targetLat = fix.latitude
            targetLng = fix.longitude
            startBearing = currentFrame?.bearing ?: renderedBearing
            targetBearing = nextBearing
            renderedLat = currentLat
            renderedLng = currentLng
            renderedBearing = currentFrame?.bearing ?: renderedBearing
            previousFix = fix
            lastReceiveMs = nowMs
            lastFixSourceKey = sourceKey
            lastDriveMode = isDriveMode
            return true
        }

        fun frame(nowMs: Long): LiveLocationFrame? {
            val targetLatitude = targetLat
            val targetLongitude = targetLng
            if (targetLatitude == null || targetLongitude == null) {
                val lat = renderedLat
                val lng = renderedLng
                return if (lat != null && lng != null) {
                    LiveLocationFrame(lat, lng, renderedBearing, isAnimating = false)
                } else {
                    null
                }
            }
            val startedAt = animationStartMs
            val fromLat = startLat
            val fromLng = startLng
            if (startedAt == null || fromLat == null || fromLng == null || animationDurationMs <= 0L) {
                renderedLat = targetLatitude
                renderedLng = targetLongitude
                renderedBearing = targetBearing
                return LiveLocationFrame(targetLatitude, targetLongitude, renderedBearing, isAnimating = false)
            }
            val progress =
                ((nowMs - startedAt).toDouble() / animationDurationMs.toDouble()).coerceIn(0.0, 1.0)
            val lat = interpolate(fromLat, targetLatitude, progress)
            val lng = interpolate(fromLng, targetLongitude, progress)
            val bearingProgress = subtleEaseInOut(progress).toFloat()
            val bearing = interpolateBearing(startBearing, targetBearing, bearingProgress)
            renderedLat = lat
            renderedLng = lng
            renderedBearing = bearing
            if (progress >= 1.0) {
                animationStartMs = null
                startLat = targetLatitude
                startLng = targetLongitude
                startBearing = targetBearing
            }
            return LiveLocationFrame(lat, lng, bearing, isAnimating = progress < 1.0)
        }

        private fun sampleIntervalMs(
            fix: LocationFix,
            nowMs: Long,
        ): Long {
            val previousElapsedMs = previousFix?.elapsedRealtimeNanos?.let { it / 1_000_000L }
            val fixElapsedMs = fix.elapsedRealtimeNanos?.let { it / 1_000_000L }
            if (previousElapsedMs != null && fixElapsedMs != null) {
                val delta = fixElapsedMs - previousElapsedMs
                if (delta in 50L..10_000L) return delta
            }
            val receiveDelta = lastReceiveMs?.let { nowMs - it }
            if (receiveDelta != null && receiveDelta >= 50L) return receiveDelta
            return 1_000L
        }

        private fun isSuspiciousJump(
            fix: LocationFix,
            nowMs: Long,
        ): Boolean {
            val previous = previousFix ?: return false
            val accuracy = fix.accuracyMeters ?: 0f
            if (accuracy <= LIVE_LOCATION_SUSPICIOUS_ACCURACY_METERS) return false
            val distance = distanceMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
            val intervalSeconds = sampleIntervalMs(fix, nowMs).coerceAtLeast(250L) / 1000.0
            val speed =
                max(
                    (fix.speedMps ?: 0f).coerceAtLeast(0f).toDouble(),
                    (previous.speedMps ?: 0f).coerceAtLeast(0f).toDouble(),
                )
            val plausibleDistance = max(200.0, speed * intervalSeconds * 4.0 + 100.0)
            return distance > plausibleDistance
        }

        private fun LocationFix.sourceKey(): String =
            listOf(
                elapsedRealtimeNanos,
                revision,
                latitude,
                longitude,
                speedMps,
                accuracyMeters,
                bearingDegrees,
            ).joinToString(separator = ":")
    }

    private fun subtleEaseInOut(progress: Double): Double {
        val t = progress.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

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
