package to.ottomot.driftd.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import to.ottomot.driftd.MainActivity
import to.ottomot.driftd.R
import to.ottomot.driftd.appContainer

/**
 * Foreground service while an active drive session needs background location (Android 10+ policy).
 */
class ActiveDriveLocationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDriveTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                ensureNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                applicationContext.appContainer().deviceLocationTracker.setDriveSessionActive(true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopDriveTracking()
        super.onDestroy()
    }

    private fun stopDriveTracking() {
        applicationContext.appContainer().deviceLocationTracker.setDriveSessionActive(false)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.drive_active_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.drive_active_notification_channel_desc)
            }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_otto_notification)
            .setContentTitle(getString(R.string.drive_active_notification_title))
            .setContentText(getString(R.string.drive_active_notification_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "otto_active_drive"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "to.ottomot.driftd.action.STOP_ACTIVE_DRIVE"

        fun start(context: Context) {
            val intent =
                Intent(context, ActiveDriveLocationService::class.java).apply {
                    action = "start"
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, ActiveDriveLocationService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }
}
