package to.ottomot.driftd.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float?,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val elapsedRealtimeNanos: Long? = null,
    /** Bumped on forced publishes so [StateFlow] collectors refresh even when lat/lng are unchanged. */
    val revision: Long = 0L,
)

private const val ROUTE_BUILDER_LOCATION_MAX_AGE_NANOS = 2L * 60L * 1_000_000_000L

fun LocationFix.isFreshForRouteBuilderCenter(
    nowElapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos(),
): Boolean {
    val fixElapsed = elapsedRealtimeNanos ?: return false
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    val ageNanos = nowElapsedRealtimeNanos - fixElapsed
    return ageNanos in 0..ROUTE_BUILDER_LOCATION_MAX_AGE_NANOS
}

/** Coarse/high-accuracy reads via Play Services fused client. Caller flow must request runtime permission first. */
@SuppressLint("MissingPermission")
class ApproximateLocationReader internal constructor(
    private val application: Application,
) {
    private fun hasAnyLocationPermission(): Boolean {
        val fine =
            ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun currentLatLngOrNull(): Pair<Double, Double>? =
        if (!hasAnyLocationPermission()) {
            null
        } else
        try {
            val fused = LocationServices.getFusedLocationProviderClient(application)
            val cancellation = CancellationTokenSource()
            val loc =
                fused
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .await()
            if (loc == null) {
                null
            } else {
                loc.latitude to loc.longitude
            }
        } catch (_: Throwable) {
            null
        }

    /** Used for live sharing / drive path — prefers a fresh high-accuracy fix (may return null if permission denied). */
    suspend fun currentFixHighAccuracyOrNull(): LocationFix? =
        if (!hasAnyLocationPermission()) {
            null
        } else
        try {
            val fused = LocationServices.getFusedLocationProviderClient(application)
            val cancellation = CancellationTokenSource()
            val loc =
                fused
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?: return null
            loc.toLocationFix()
        } catch (_: Throwable) {
            null
        }

    /** Route Builder centering: prefer fresh high accuracy; accept last known only when it is recent. */
    suspend fun currentFixHighAccuracyOrLastKnownOrNull(): LocationFix? {
        if (!hasAnyLocationPermission()) return null
        return try {
            val fused = LocationServices.getFusedLocationProviderClient(application)
            val cancellation = CancellationTokenSource()
            val loc =
                fused
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?.toLocationFix()
                    ?.takeIf { it.isFreshForRouteBuilderCenter() }
            if (loc != null) return loc
            fused.lastLocation.await()
                ?.toLocationFix()
                ?.takeIf { it.isFreshForRouteBuilderCenter() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun Location.toLocationFix(): LocationFix =
        LocationFix(
            latitude = latitude,
            longitude = longitude,
            speedMps = if (hasSpeed()) speed else null,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
}
