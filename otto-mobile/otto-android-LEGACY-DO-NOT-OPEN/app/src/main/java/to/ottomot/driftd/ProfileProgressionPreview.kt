package to.ottomot.driftd

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import to.ottomot.driftd.core.network.dto.ProfileLevelUpDto
import to.ottomot.driftd.core.network.dto.ProfileProgressionDto
import to.ottomot.driftd.core.notify.OttoNotificationChannels
import to.ottomot.driftd.core.notify.OttoNotificationSounds
import kotlin.math.max
import kotlin.math.min

private data class PreviewTier(
    val id: String,
    val name: String,
    val minLevel: Int,
    val pointsPerLevel: Int?,
)

/** DEBUG/local preview helpers mirroring iOS `AppState.previewProfileLevelUp`. */
internal object ProfileProgressionPreview {
    private val gson = Gson()

    internal data class PreviewOption(
        val level: Int,
        val label: String,
    )

    val previewOptions: List<PreviewOption> =
        listOf(
            PreviewOption(level = 2, label = "Rookie II"),
            PreviewOption(level = 5, label = "Qualifier I"),
            PreviewOption(level = 9, label = "Runner I"),
            PreviewOption(level = 13, label = "Pacer I"),
            PreviewOption(level = 17, label = "Apex I"),
            PreviewOption(level = 20, label = "Legend"),
        )

    fun previewProfileLevelUp(requestedLevel: Int): ProfileLevelUpDto {
        val level = min(20, max(2, requestedLevel))
        val progression = previewProgression(level)
        val previousProgression = previewProgression(level - 1, pointsIntoLevel = 20)
        val nextProgression = if (level < 20) previewProgression(level + 1, pointsIntoLevel = 0) else null
        val pointsAwarded = max(10, (progression.points ?: 0) - (previousProgression.points ?: 0))

        return ProfileLevelUpDto(
            eventType = "preview",
            pointsAwarded = pointsAwarded,
            previousProgression = previousProgression,
            progression = progression,
            nextProgression = nextProgression,
            reachedDisplayName = previewDisplayName(progression),
            nextDisplayName = nextProgression?.let(::previewDisplayName),
            unlockedNewTier = progression.tierId != previousProgression.tierId,
        )
    }

    fun scheduleLevelUpNotification(
        context: Context,
        level: Int = 17,
    ) {
        OttoNotificationChannels.ensureCreated(context)
        val levelUp = previewProfileLevelUp(level)
        val levelUpJson = gson.toJson(levelUp)
        Handler(Looper.getMainLooper()).postDelayed({
            postPreviewNotification(context, levelUp, levelUpJson)
        }, 3_000L)
    }

    private fun postPreviewNotification(
        context: Context,
        levelUp: ProfileLevelUpDto,
        levelUpJson: String,
    ) {
        val progression = levelUp.progression ?: return
        val title = "Level up"
        val body = "You reached ${levelUp.reachedDisplayName.orEmpty()}"

        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PushNotificationTapStore.EXTRA_FROM_PUSH, true)
                putExtra("type", "profile.progression.level_up")
                putExtra("level", progression.level?.toString().orEmpty())
                putExtra("tierId", progression.tierId.orEmpty())
                putExtra("tierName", progression.tierName.orEmpty())
                putExtra("levelUp", levelUpJson)
                putExtra("title", title)
                putExtra("body", body)
            }

        val pending =
            PendingIntent.getActivity(
                context,
                "preview-profile-progression-level-up".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val channelId = OttoNotificationSounds.channelIdForPushType("profile.progression.level_up")
        val notification =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_otto_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

        NotificationManagerCompat.from(context).notify(
            "preview-profile-progression-level-up".hashCode(),
            notification,
        )
    }

    private fun previewProgression(
        level: Int,
        pointsIntoLevel: Int = 240,
    ): ProfileProgressionDto {
        val tier = previewTier(level)
        val isMaxLevel = level >= 20
        val currentLevelStartPoints = previewLevelStartPoints(level)
        val pointsRequired = tier.pointsPerLevel
        val cappedPointsIntoLevel =
            pointsRequired?.let { min(max(0, pointsIntoLevel), max(0, it - 1)) } ?: 0
        val points = currentLevelStartPoints + cappedPointsIntoLevel

        return ProfileProgressionDto(
            points = max(1, points),
            level = level,
            tierId = tier.id,
            tierName = tier.name,
            levelImageName = "Level$level",
            currentLevelStartPoints = currentLevelStartPoints,
            nextLevelAt = pointsRequired?.let { currentLevelStartPoints + it },
            pointsIntoLevel = cappedPointsIntoLevel,
            pointsRequiredForLevel = pointsRequired,
            progress = pointsRequired?.let { cappedPointsIntoLevel.toDouble() / it.toDouble() } ?: 1.0,
            isMaxLevel = isMaxLevel,
        )
    }

    private fun previewLevelStartPoints(level: Int): Int {
        var total = 0
        for (previewLevel in 1 until level) {
            total += previewTier(previewLevel).pointsPerLevel ?: 0
        }
        return total
    }

    private fun previewTier(level: Int): PreviewTier =
        when (level) {
            in 1..4 -> PreviewTier("rookie", "Rookie", 1, 250)
            in 5..8 -> PreviewTier("qualifier", "Qualifier", 5, 500)
            in 9..12 -> PreviewTier("runner", "Runner", 9, 1000)
            in 13..16 -> PreviewTier("pacer", "Pacer", 13, 2000)
            in 17..19 -> PreviewTier("apex", "Apex", 17, 4000)
            else -> PreviewTier("legend", "Legend", 20, null)
        }

    private fun previewDisplayName(progression: ProfileProgressionDto): String {
        if (progression.isMaxLevel == true) {
            return progression.tierName.orEmpty().ifEmpty { "Legend" }
        }
        val level = progression.level ?: 1
        val tier = previewTier(level)
        val ordinal = max(1, level - tier.minLevel + 1)
        val roman = progressionRomanNumeral(ordinal)
        val tierName = progression.tierName.orEmpty().ifEmpty { tier.name }
        return "$tierName $roman"
    }

    private fun progressionRomanNumeral(value: Int): String =
        when (value) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            else -> value.toString()
        }
}
