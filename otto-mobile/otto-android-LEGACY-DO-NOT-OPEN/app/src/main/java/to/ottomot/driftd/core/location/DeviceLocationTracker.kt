package to.ottomot.driftd.core.location

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Subscribes to fused location updates when [ACCESS_FINE_LOCATION] is granted. Updates are removed
 * on [onStop] because Android does not request background location.
 *
 * While the Map tab is foreground ([setMapForegroundActive]), requests are tightened to mirror iOS
 * map pin refresh (~5 m / high accuracy) so the user's marker moves when they are not live-sharing.
 */
class DeviceLocationTracker internal constructor(
    private val application: Application,
) : DefaultLifecycleObserver {
    private val fused =
        LocationServices.getFusedLocationProviderClient(application)

    private val _lastFix = MutableStateFlow<LocationFix?>(null)
    val lastFix: StateFlow<LocationFix?> = _lastFix.asStateFlow()

    /**
     * [listening] is true only after [requestLocationUpdates] succeeds. [startPending] covers the gap
     * between firing the Task and its success/failure: without this, [onStop] could call
     * [removeLocationUpdates] while registration is still in flight (common when the user only grants
     * foreground location — short background/blur cycles — and Play Services is still connecting).
     */
    private val listenerLock = Any()

    @Volatile
    private var listening = false

    @Volatile
    private var startPending = false

    @Volatile
    private var mapForegroundActive = false

    @Volatile
    private var appInForeground = true

    /**
     * Incremented when starting a registration attempt and when tearing it down so completion
     * callbacks from superseded attempts are ignored ([stopListening] must not be followed by a stale
     * `onComplete` that flips [listening] back on).
     */
    private var registrationGeneration: Int = 0

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                publishFix(loc.toLocationFix())
            }
        }

    fun setAppInForeground(inForeground: Boolean) {
        val changed =
            synchronized(listenerLock) {
                val c = appInForeground != inForeground
                appInForeground = inForeground
                c
            }
        if (!changed) return
        if (inForeground && mapForegroundActive) {
            restartListening()
        } else if (!inForeground) {
            stopListening()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (mapForegroundActive) {
            restartListening()
        } else {
            tryStartListening()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val keepListening =
            synchronized(listenerLock) {
                mapForegroundActive && appInForeground
            }
        if (!keepListening) {
            stopListening()
        }
    }

    /** Map tab selected — use high-accuracy fused updates for the local pin (iOS `distanceFilter = 5`). */
    fun setMapForegroundActive(active: Boolean) {
        val changed =
            synchronized(listenerLock) {
                val c = mapForegroundActive != active
                mapForegroundActive = active
                c
            }
        if (changed) {
            restartListening()
        }
    }

    /** Publishes a one-shot fix (e.g. map-tab poll while not sharing). */
    fun publishFix(fix: LocationFix, force: Boolean = false) {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        val stamped =
            if (force) {
                fix.copy(revision = System.nanoTime())
            } else {
                fix
            }
        val current = _lastFix.value
        if (force || current != stamped) {
            _lastFix.value = stamped
        }
    }

    fun tryStartListening() {
        if (!fineLocationGranted()) return
        val generation =
            synchronized(listenerLock) {
                if (listening || startPending) return
                startPending = true
                ++registrationGeneration
                registrationGeneration
            }
        startListeningWithGeneration(generation)
    }

    private fun restartListening() {
        stopListening()
        tryStartListening()
    }

    private fun startListeningWithGeneration(generation: Int) {
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                loc?.let { publishFix(it.toLocationFix()) }
            }
            val request = buildLocationRequest()
            fused
                .requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper(),
                ).addOnCompleteListener { task ->
                    val ok = task.isSuccessful
                    synchronized(listenerLock) {
                        if (generation != registrationGeneration) return@addOnCompleteListener
                        startPending = false
                        listening = ok
                    }
                    if (!ok) {
                        runCatching { fused.removeLocationUpdates(callback) }
                    }
                }
        } catch (_: SecurityException) {
            synchronized(listenerLock) {
                if (generation == registrationGeneration) {
                    startPending = false
                    listening = false
                }
            }
        } catch (_: Throwable) {
            synchronized(listenerLock) {
                if (generation == registrationGeneration) {
                    startPending = false
                    listening = false
                }
            }
        }
    }

    private fun buildLocationRequest(): LocationRequest {
        val mapMode = mapForegroundActive
        val priority =
            if (mapMode) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                PRIORITY_BALANCED
            }
        val intervalMs = if (mapMode) MAP_UPDATE_INTERVAL_MS else UPDATE_INTERVAL_MS
        val minIntervalMs = if (mapMode) MAP_MIN_INTERVAL_MS else MIN_INTERVAL_MS
        val minDistanceM = if (mapMode) MAP_MIN_DISTANCE_M else MIN_DISTANCE_M
        val maxDelayMs = if (mapMode) MAP_MAX_DELAY_MS else MAX_DELAY_MS
        return LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setMaxUpdateDelayMillis(maxDelayMs)
            .build()
    }

    private fun stopListening() {
        val shouldRemove =
            synchronized(listenerLock) {
                registrationGeneration++
                val hadWork = listening || startPending
                startPending = false
                listening = false
                hadWork
            }
        if (!shouldRemove) return
        runCatching { fused.removeLocationUpdates(callback) }
    }

    private fun fineLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PRIORITY_BALANCED: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY

        /** Target interval between fused deliveries (milliseconds). */
        const val UPDATE_INTERVAL_MS: Long = 10_000L

        const val MIN_INTERVAL_MS: Long = 4_000L

        /** Skip micro-moves between updates unless the user moves this far (meters). */
        const val MIN_DISTANCE_M: Float = 15f

        const val MAX_DELAY_MS: Long = 60_000L

        const val MAP_UPDATE_INTERVAL_MS: Long = 5_000L

        const val MAP_MIN_INTERVAL_MS: Long = 2_000L

        const val MAP_MIN_DISTANCE_M: Float = 5f

        const val MAP_MAX_DELAY_MS: Long = 15_000L
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
