package to.ottomot.driftd.core.notify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.annotation.RawRes
import to.ottomot.driftd.R

/**
 * Channel ids are chosen client-side in [OttoFirebaseMessagingService] / [OttoNotificationSounds] (FCM is data-only).
 */
object OttoNotificationChannels {
    const val GENERAL = "otto_general"
    const val CHAT_NEW_MESSAGE = "otto_chat_new_message_v2"
    const val LEVEL3 = "otto_level3_v2"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(GENERAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    GENERAL,
                    context.getString(R.string.notification_channel_general_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_general_desc)
                    enableVibration(true)
                },
            )
        }
        if (nm.getNotificationChannel(CHAT_NEW_MESSAGE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHAT_NEW_MESSAGE,
                    context.getString(R.string.notification_channel_chat_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_chat_desc)
                    enableVibration(true)
                    setSound(
                        OttoNotificationSounds.soundUri(context, R.raw.otto_newmessage),
                        notificationAudioAttributes(),
                    )
                },
            )
        }
        if (nm.getNotificationChannel(LEVEL3) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    LEVEL3,
                    context.getString(R.string.notification_channel_level3_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_level3_desc)
                    enableVibration(true)
                    setSound(
                        OttoNotificationSounds.soundUri(context, R.raw.otto_locationshared),
                        notificationAudioAttributes(),
                    )
                },
            )
        }
    }

    private fun notificationAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}

object OttoNotificationSounds {
    /** Foreground WebSocket chat fallback — matches iOS [EngagementFeedback]. */
    @RawRes
    val foregroundChatToneRes: Int = R.raw.otto_newmessage

    fun channelIdForPushType(pushType: String?): String =
        when (pushType?.trim()) {
            "circle.chat.new_message",
            "circle.chat.mention",
            "circle.chat.reply",
            "direct.message",
            -> OttoNotificationChannels.CHAT_NEW_MESSAGE
            "presence.location_started" -> OttoNotificationChannels.LEVEL3
            else -> OttoNotificationChannels.GENERAL
        }

    @RawRes
    fun rawResForPushType(pushType: String?): Int? =
        when (pushType?.trim()) {
            "circle.chat.new_message",
            "circle.chat.mention",
            "circle.chat.reply",
            "direct.message",
            -> R.raw.otto_newmessage
            "presence.location_started" -> R.raw.otto_locationshared
            else -> null
        }

    fun channelIdForFcmSound(fcmSound: String?): String =
        when (fcmSound?.trim()) {
            "otto_newmessage" -> OttoNotificationChannels.CHAT_NEW_MESSAGE
            "otto_locationshared" -> OttoNotificationChannels.LEVEL3
            "default" -> OttoNotificationChannels.GENERAL
            else -> OttoNotificationChannels.GENERAL
        }

    @RawRes
    fun rawResForFcmSound(fcmSound: String?): Int? =
        when (fcmSound?.trim()) {
            "otto_newmessage" -> R.raw.otto_newmessage
            "otto_locationshared" -> R.raw.otto_locationshared
            else -> null
        }

    fun soundUri(
        context: Context,
        @RawRes resId: Int,
    ): Uri {
        val resources = context.resources
        return Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/" +
                "${resources.getResourceTypeName(resId)}/${resources.getResourceEntryName(resId)}",
        )
    }

    fun playBundledTone(
        app: Application,
        @RawRes resId: Int,
    ) {
        try {
            val player = MediaPlayer.create(app, resId) ?: return
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (_: Throwable) {
        }
    }
}
