package to.ottomot.driftd.core.location

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import to.ottomot.driftd.core.permissions.activityRecognitionGranted

/**
 * Subscribes to periodic activity recognition updates (Play Services) while map sharing is on.
 * Feeds [MovementModeIosParity] alongside GPS speed, matching iOS `CMMotionActivityManager` usage.
 */
internal class ActivityRecognitionPresenceSupport(
    private val application: Application,
) {
    companion object {
        private const val REQUEST_CODE = 91042
        private const val ACTION = "to.ottomot.driftd.ACTION_ACTIVITY_UPDATES"
    }

    @Volatile
    var latestSnapshot: ActivityRecognitionSnapshot? = null
        private set

    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ticks: SharedFlow<Unit> = _ticks.asSharedFlow()

    private var started = false

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION).setPackage(application.packageName)
        val mutability =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        PendingIntent.getBroadcast(
            application,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION) return
                val result = ActivityRecognitionResult.extractResult(intent) ?: return
                latestSnapshot =
                    MovementModeIosParity.snapshotFromDetectedActivities(result.probableActivities)
                _ticks.tryEmit(Unit)
            }
        }

    fun start() {
        if (started) return
        if (!activityRecognitionGranted(application)) {
            latestSnapshot = null
            return
        }
        started = true
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ActivityRecognition.getClient(application)
            .requestActivityUpdates(3_000L, pendingIntent)
            .addOnFailureListener {
                latestSnapshot = null
                stop()
            }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            application.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        runCatching { ActivityRecognition.getClient(application).removeActivityUpdates(pendingIntent) }
        latestSnapshot = null
    }
}
