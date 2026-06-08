package to.ottomot.driftd.core.notify

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import to.ottomot.driftd.MainActivity
import to.ottomot.driftd.PushNotificationTapStore
import to.ottomot.driftd.R
import to.ottomot.driftd.core.chat.SquadChatAllMention
import to.ottomot.driftd.core.network.dto.CircleChatMessageDto
import to.ottomot.driftd.core.network.dto.DirectMessageDto
import to.ottomot.driftd.ChatImageUrlDisplay
import to.ottomot.driftd.ottoUserIdsEqual

/**
 * Level-3 engagement while the app is in the foreground. In-thread: light haptic only
 * (matches iOS). Outside that thread: throttled tone + banner + stronger vibration.
 */
object EngagementFeedbackAndroid {
    fun maybeSquadInThreadEngagement(
        app: Application,
        dto: CircleChatMessageDto,
        myUserId: String?,
        focusedChatCircleId: String?,
    ) {
        val me = myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (ottoUserIdsEqual(dto.senderUserId, me)) return
        if ((dto.messageType ?: "") == "system") return
        if (!foreground()) return

        val fid = focusedChatCircleId?.trim()?.takeIf { it.isNotEmpty() }
        val inSameThread = fid != null && ottoUserIdsEqual(fid, dto.circleId)
        if (!inSameThread) return

        val mentionMe = dto.mentions.orEmpty().any { ottoUserIdsEqual(it.userId, me) }
        val mentionAll = dto.mentions.orEmpty().any { it.userId == SquadChatAllMention.USER_ID }
        val replyToMe =
            dto.replyTo?.senderUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { ottoUserIdsEqual(it, me) } == true
        if (!mentionMe && !mentionAll && !replyToMe) return

        lightHaptic(app)
    }

    fun maybeSquadForegroundAlert(
        app: Application,
        dto: CircleChatMessageDto,
        myUserId: String?,
        focusedChatCircleId: String?,
        squadDisplayName: String?,
        soundEffectsEnabled: Boolean = true,
        isMuted: Boolean = false,
    ) {
        val me = myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (ottoUserIdsEqual(dto.senderUserId, me)) return
        if ((dto.messageType ?: "") == "system") return
        if (!foreground()) return

        val (outcome, projectedSuppressed) =
            ChatEngagementThrottle.evaluateSquadMessage(
                circleId = dto.circleId,
                pushType = "circle.chat.new_message",
                focusedChatCircleId = focusedChatCircleId,
                isMuted = isMuted,
            )
        if (outcome == ChatEngagementAlertOutcome.Suppress) return

        val conversationKey = ChatEngagementThrottle.squadConversationKey(dto.circleId)
        val squadLabel = squadDisplayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Squad"
        val preview = squadMessagePreview(dto)
        val senderName =
            dto.sender?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "New message"
        val baseBody = "$squadLabel: $preview"

        when (outcome) {
            ChatEngagementAlertOutcome.FullAlert -> {
                ChatEngagementThrottle.recordFullAlert(conversationKey, pushType = "circle.chat.new_message")
                val playTone = soundEffectsEnabled && !isMuted
                postOrUpdateForegroundChatNotification(
                    app = app,
                    conversationKey = conversationKey,
                    title = senderName,
                    body = baseBody,
                    playSound = playTone,
                    pushType = "circle.chat.new_message",
                    userInfo =
                        mapOf(
                            "type" to "circle.chat.new_message",
                            "circleId" to dto.circleId,
                            "messageId" to dto.id,
                            "source" to "websocket-fallback",
                        ),
                )
                if (playTone) {
                    strongHaptic(app)
                }
            }
            ChatEngagementAlertOutcome.SilentBannerUpdate -> {
                ChatEngagementThrottle.recordSilentUpdate(conversationKey, pushType = "circle.chat.new_message")
                postOrUpdateForegroundChatNotification(
                    app = app,
                    conversationKey = conversationKey,
                    title = senderName,
                    body = ChatEngagementThrottle.bannerBody(baseBody, projectedSuppressed),
                    playSound = false,
                    pushType = "circle.chat.new_message",
                    userInfo =
                        mapOf(
                            "type" to "circle.chat.new_message",
                            "circleId" to dto.circleId,
                            "messageId" to dto.id,
                            "source" to "websocket-fallback",
                        ),
                )
            }
            ChatEngagementAlertOutcome.Suppress -> Unit
        }
    }

    fun maybeDirectInThreadEngagement(
        app: Application,
        dto: DirectMessageDto,
        myUserId: String?,
        focusedConversationId: String?,
    ) {
        val me = myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (ottoUserIdsEqual(dto.senderUserId, me)) return
        if (!foreground()) return

        val conv = focusedConversationId?.trim()?.takeIf { it.isNotEmpty() }
        val inSame = conv != null && ottoUserIdsEqual(conv, dto.conversationId)
        if (!inSame) return

        val replyToMe =
            dto.replyTo?.senderUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { ottoUserIdsEqual(it, me) } == true
        if (replyToMe) lightHaptic(app)
    }

    fun maybeDirectForegroundAlert(
        app: Application,
        dto: DirectMessageDto,
        myUserId: String?,
        focusedConversationId: String?,
        senderDisplayName: String?,
        soundEffectsEnabled: Boolean = true,
    ) {
        val me = myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (ottoUserIdsEqual(dto.senderUserId, me)) return
        if (!foreground()) return

        val (outcome, projectedSuppressed) =
            ChatEngagementThrottle.evaluateDirectMessage(
                conversationId = dto.conversationId,
                focusedConversationId = focusedConversationId,
            )
        if (outcome == ChatEngagementAlertOutcome.Suppress) return

        val conversationKey = ChatEngagementThrottle.directConversationKey(dto.conversationId)
        val preview = directMessagePreview(dto)
        val senderName =
            senderDisplayName?.trim()?.takeIf { it.isNotEmpty() } ?: "New message"

        when (outcome) {
            ChatEngagementAlertOutcome.FullAlert -> {
                ChatEngagementThrottle.recordFullAlert(conversationKey, pushType = "direct.message")
                postOrUpdateForegroundChatNotification(
                    app = app,
                    conversationKey = conversationKey,
                    title = senderName,
                    body = preview,
                    playSound = soundEffectsEnabled,
                    pushType = "direct.message",
                    userInfo =
                        mapOf(
                            "type" to "direct.message",
                            "conversationId" to dto.conversationId,
                            "messageId" to dto.id,
                            "source" to "websocket-fallback",
                        ),
                )
                if (soundEffectsEnabled) {
                    strongHaptic(app)
                }
            }
            ChatEngagementAlertOutcome.SilentBannerUpdate -> {
                ChatEngagementThrottle.recordSilentUpdate(conversationKey, pushType = "direct.message")
                postOrUpdateForegroundChatNotification(
                    app = app,
                    conversationKey = conversationKey,
                    title = senderName,
                    body = ChatEngagementThrottle.bannerBody(preview, projectedSuppressed),
                    playSound = false,
                    pushType = "direct.message",
                    userInfo =
                        mapOf(
                            "type" to "direct.message",
                            "conversationId" to dto.conversationId,
                            "messageId" to dto.id,
                            "source" to "websocket-fallback",
                        ),
                )
            }
            ChatEngagementAlertOutcome.Suppress -> Unit
        }
    }

    /** @deprecated Use [maybeSquadInThreadEngagement] + [maybeSquadForegroundAlert]. */
    fun maybeSquadRealtime(
        app: Application,
        dto: CircleChatMessageDto,
        myUserId: String?,
        focusedCircleId: String?,
        soundEffectsEnabled: Boolean = true,
        suppressMentionBundledTone: Boolean = false,
    ) {
        maybeSquadInThreadEngagement(app, dto, myUserId, focusedCircleId)
    }

    /** @deprecated Use [maybeDirectInThreadEngagement] + [maybeDirectForegroundAlert]. */
    fun maybeDirectRealtime(
        app: Application,
        dto: DirectMessageDto,
        myUserId: String?,
        focusedConversationId: String?,
        dmOverlayVisible: Boolean,
        soundEffectsEnabled: Boolean = true,
    ) {
        if (dmOverlayVisible) {
            maybeDirectInThreadEngagement(app, dto, myUserId, focusedConversationId)
        } else {
            maybeDirectForegroundAlert(
                app,
                dto,
                myUserId,
                focusedConversationId,
                senderDisplayName = dto.sender?.displayName,
                soundEffectsEnabled = soundEffectsEnabled,
            )
        }
    }

    fun evaluateForegroundPushThrottle(
        pushType: String?,
        circleId: String?,
        conversationId: String?,
        focusedChatCircleId: String?,
        focusedConversationId: String?,
        isMuted: Boolean,
    ): ChatEngagementAlertOutcome {
        val type = pushType?.trim().orEmpty()
        return when (type) {
            "circle.chat.mention", "circle.chat.reply", "circle.chat.new_message" -> {
                val cid = circleId?.trim().orEmpty()
                if (cid.isEmpty()) {
                    ChatEngagementAlertOutcome.FullAlert
                } else {
                    ChatEngagementThrottle.evaluateSquadMessage(
                        circleId = cid,
                        pushType = type,
                        focusedChatCircleId = focusedChatCircleId,
                        isMuted = isMuted,
                    ).first
                }
            }
            "direct.message" -> {
                val conv = conversationId?.trim().orEmpty()
                if (conv.isEmpty()) {
                    ChatEngagementAlertOutcome.FullAlert
                } else {
                    ChatEngagementThrottle.evaluateDirectMessage(
                        conversationId = conv,
                        focusedConversationId = focusedConversationId,
                    ).first
                }
            }
            else -> ChatEngagementAlertOutcome.FullAlert
        }
    }

    fun recordThrottleOutcome(
        pushType: String?,
        circleId: String?,
        conversationId: String?,
        outcome: ChatEngagementAlertOutcome,
    ) {
        val key =
            when (pushType?.trim().orEmpty()) {
                "circle.chat.mention", "circle.chat.reply", "circle.chat.new_message" -> {
                    circleId?.trim()?.takeIf { it.isNotEmpty() }?.let { ChatEngagementThrottle.squadConversationKey(it) }
                }
                "direct.message" -> {
                    conversationId?.trim()?.takeIf { it.isNotEmpty() }?.let { ChatEngagementThrottle.directConversationKey(it) }
                }
                else -> null
            } ?: return
        when (outcome) {
            ChatEngagementAlertOutcome.FullAlert ->
                ChatEngagementThrottle.recordFullAlert(key, pushType = pushType?.trim().orEmpty().ifEmpty { "circle.chat.new_message" })
            ChatEngagementAlertOutcome.SilentBannerUpdate ->
                ChatEngagementThrottle.recordSilentUpdate(key, pushType = pushType?.trim().orEmpty().ifEmpty { "circle.chat.new_message" })
            ChatEngagementAlertOutcome.Suppress -> Unit
        }
    }

    private fun postOrUpdateForegroundChatNotification(
        app: Application,
        conversationKey: String,
        title: String,
        body: String,
        playSound: Boolean,
        pushType: String,
        userInfo: Map<String, String>,
    ) {
        OttoNotificationChannels.ensureCreated(app)
        val intent =
            Intent(app, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PushNotificationTapStore.EXTRA_FROM_PUSH, true)
                userInfo.forEach { (k, v) -> putExtra(k, v) }
            }
        val pending =
            PendingIntent.getActivity(
                app,
                conversationKey.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val channelId = OttoNotificationSounds.channelIdForPushType(pushType)
        val toneRes = OttoNotificationSounds.rawResForPushType(pushType)
        val playBundledTone = playSound && toneRes != null
        val builder =
            NotificationCompat.Builder(app, channelId)
                .setSmallIcon(R.drawable.ic_otto_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setOnlyAlertOnce(!playBundledTone)
                .setSilent(true)
        NotificationManagerCompat.from(app).notify(
            ChatEngagementThrottle.foregroundNotificationId(conversationKey),
            builder.build(),
        )
        if (playBundledTone) {
            OttoNotificationSounds.playBundledTone(app, toneRes!!)
        }
    }

    private fun squadMessagePreview(dto: CircleChatMessageDto): String {
        val body = dto.body.orEmpty().trim()
        if (body.isNotEmpty()) return body.take(120)
        if (dto.imageUrl != null) {
            return if (ChatImageUrlDisplay.isAnimatedImageUrl(dto.imageUrl)) "GIF" else "Photo"
        }
        if (dto.videoAttachment?.videoUrl != null) return "Video"
        if (dto.driveAttachment?.driveId != null) return "Drive"
        if (dto.placeAttachment != null) return dto.placeAttachment.notificationPreview()
        if (dto.eventAttachment?.eventId != null) return "Event"
        return "Message"
    }

    private fun directMessagePreview(dto: DirectMessageDto): String {
        val body = dto.body.orEmpty().trim()
        if (body.isNotEmpty()) return body.take(120)
        if (dto.imageUrl != null) {
            return if (ChatImageUrlDisplay.isAnimatedImageUrl(dto.imageUrl)) "GIF" else "Photo"
        }
        if (dto.videoAttachment?.videoUrl != null) return "Video"
        if (dto.placeAttachment != null) return dto.placeAttachment.notificationPreview()
        if (dto.eventAttachment?.eventId != null) return "Event"
        return "Message"
    }

    private fun foreground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun lightHaptic(app: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrate(app, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrateMs(app, 18)
        }
    }

    private fun strongHaptic(app: Application) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                vibrate(app, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                vibrate(app, VibrationEffect.createOneShot(48, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            else -> vibrateMs(app, 48)
        }
    }

    private fun vibrateMs(app: Application, ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrate(app, VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)?.vibrate(ms)
        }
    }

    private fun vibrate(app: Application, effect: VibrationEffect) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Vibrator::class.java)?.vibrate(effect)
            }
        } catch (_: SecurityException) {
        }
    }
}
