package to.ottomot.driftd

import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.RegisterDeviceRequestDto
import to.ottomot.driftd.core.notify.ChatEngagementAlertOutcome
import to.ottomot.driftd.core.notify.ChatFocusBridge
import to.ottomot.driftd.core.notify.EngagementFeedbackAndroid
import to.ottomot.driftd.core.notify.OttoNotificationChannels
import to.ottomot.driftd.core.notify.OttoNotificationSounds

/**
 * Handles FCM: token refresh → re-register with Otto API; messages → system notification + tap routing via [PushNotificationTapStore].
 */
class OttoFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val SQUAD_GROUP_KEY_PREFIX = "otto.squad."

        private fun squadNotificationGroupKey(circleId: String): String =
            "$SQUAD_GROUP_KEY_PREFIX${circleId.trim()}"

        /** Stable id for the collapsed stack header; must not reuse per-message ids. */
        private fun squadGroupSummaryNotificationId(circleId: String): Int =
            ("otto.squad.summary.${circleId.trim()}").hashCode()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val registration = token.trim()
        if (registration.length < 32) return

        val app = applicationContext as OttoApplication
        scope.launch {
            val authToken = app.container.sessionRepository.authTokenState.value?.trim()
            if (authToken.isNullOrEmpty()) return@launch

            app.container.dataRepository.registerPushDevice(
                RegisterDeviceRequestDto.forAndroidFcm(
                    token = registration,
                    applicationId = BuildConfig.APPLICATION_ID,
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                ),
            ).onFailure { }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        OttoNotificationChannels.ensureCreated(applicationContext)

        val fromNotification = message.notification
        val title =
            fromNotification?.title?.takeIf { it.isNotBlank() }
                ?: message.data["title"]?.takeIf { it.isNotBlank() }
                ?: return
        val body =
            fromNotification?.body?.takeIf { it.isNotBlank() }
                ?: message.data["body"]?.takeIf { it.isNotBlank() }
                ?: ""

        val pushType = message.data["type"]?.trim()?.takeIf { it.isNotEmpty() }
        val circleId = message.data["circleId"]?.trim()?.takeIf { it.isNotEmpty() }
        val conversationId = message.data["conversationId"]?.trim()?.takeIf { it.isNotEmpty() }
        val chatIconBadgeCount =
            message.data["chatIconBadgeCount"]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val ottoApp = applicationContext as OttoApplication
        val silentReactionTypes = setOf("circle.chat.reaction", "direct.message.reaction")
        val forceSilent = pushType != null && pushType in silentReactionTypes
        val suppressSoundFromMute =
            ottoApp.container.sessionRepository.shouldSuppressSquadChatPushSoundSync(circleId, pushType)
        val soundEffectsEnabled = ottoApp.container.sessionRepository.soundEffectsEnabledState.value

        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val throttleOutcome =
            if (foreground && pushType != null) {
                EngagementFeedbackAndroid.evaluateForegroundPushThrottle(
                    pushType = pushType,
                    circleId = circleId,
                    conversationId = conversationId,
                    focusedChatCircleId = ChatFocusBridge.activeChatCircleId,
                    focusedConversationId = ChatFocusBridge.activeDirectConversationId,
                    isMuted = suppressSoundFromMute,
                )
            } else {
                ChatEngagementAlertOutcome.FullAlert
            }
        if (foreground && throttleOutcome == ChatEngagementAlertOutcome.Suppress) {
            return
        }
        if (foreground) {
            EngagementFeedbackAndroid.recordThrottleOutcome(
                pushType = pushType,
                circleId = circleId,
                conversationId = conversationId,
                outcome = throttleOutcome,
            )
        }

        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PushNotificationTapStore.EXTRA_FROM_PUSH, true)
                for ((k, v) in message.data) {
                    putExtra(k, v)
                }
                if (message.messageId != null) {
                    putExtra("gcm.message_id", message.messageId)
                }
            }

        val pending =
            PendingIntent.getActivity(
                this,
                (message.messageId ?: "${title}_$body").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationId = (message.messageId ?: "${title}_$body").hashCode()

        val suppressSound =
            forceSilent ||
                suppressSoundFromMute ||
                !soundEffectsEnabled ||
                (foreground && throttleOutcome == ChatEngagementAlertOutcome.SilentBannerUpdate)

        val fcmSound = fromNotification?.sound?.trim()?.takeIf { it.isNotEmpty() }
        val channelId =
            fcmSound?.let { OttoNotificationSounds.channelIdForFcmSound(it) }
                ?: OttoNotificationSounds.channelIdForPushType(pushType)
        val toneRes =
            OttoNotificationSounds.rawResForPushType(pushType)
                ?: fcmSound?.let { OttoNotificationSounds.rawResForFcmSound(it) }
        val playBundledToneForeground =
            foreground &&
                !suppressSound &&
                throttleOutcome == ChatEngagementAlertOutcome.FullAlert &&
                toneRes != null
        val largeIcon =
            runCatching {
                BitmapFactory.decodeResource(resources, R.drawable.ic_otto_notification_branded)
            }.getOrNull()

        val notifBuilder =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_otto_notification)
                .apply {
                    if (largeIcon != null) {
                        setLargeIcon(largeIcon)
                    }
                }
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        if (suppressSound || playBundledToneForeground) {
            notifBuilder.setSilent(true)
        }
        chatIconBadgeCount?.let { notifBuilder.setNumber(it) }
        if (circleId != null) {
            notifBuilder.setGroup(squadNotificationGroupKey(circleId))
        }
        val notif = notifBuilder.build()

        val nm = NotificationManagerCompat.from(this)
        nm.notify(notificationId, notif)
        if (playBundledToneForeground) {
            OttoNotificationSounds.playBundledTone(ottoApp, toneRes!!)
        }
        if (circleId != null) {
            val summary =
                NotificationCompat.Builder(this, OttoNotificationChannels.GENERAL)
                    .setSmallIcon(R.drawable.ic_otto_notification)
                    .apply {
                        if (largeIcon != null) {
                            setLargeIcon(largeIcon)
                        }
                    }
                    .setContentTitle(getString(R.string.notification_squad_group_summary_title))
                    .setContentText(getString(R.string.notification_squad_group_summary_text))
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(squadNotificationGroupKey(circleId))
                    .setGroupSummary(true)
                    .setSilent(true)
                    .build()
            nm.notify(squadGroupSummaryNotificationId(circleId), summary)
        }
    }
}
