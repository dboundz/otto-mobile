package to.ottomot.driftd.core.notify

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Maps FCM `data.type` to a per-squad mute bucket. */
enum class SquadNotificationMuteBucket {
    NEW_MESSAGES,
    MENTIONS_AND_REPLIES,
    ;

    companion object {
        fun forPushType(type: String?): SquadNotificationMuteBucket? =
            when (type?.trim().orEmpty()) {
                "circle.chat.new_message" -> NEW_MESSAGES
                "circle.chat.mention", "circle.chat.reply", "circle.chat.reaction" -> MENTIONS_AND_REPLIES
                else -> null
            }

        fun preferenceKey(
            circleId: String,
            bucket: SquadNotificationMuteBucket,
        ): Preferences.Key<String> {
            val id = circleId.trim()
            return stringPreferencesKey(
                when (bucket) {
                    NEW_MESSAGES -> "otto.squadMute.v1.new.$id"
                    MENTIONS_AND_REPLIES -> "otto.squadMute.v1.mention.$id"
                },
            )
        }
    }
}

/** User-facing mute duration (includes Off). */
enum class SquadNotificationMuteChoice {
    OFF,
    END_OF_DAY,
    TWENTY_FOUR_HOURS,
    ONE_WEEK,
    ALWAYS,
    ;

    companion object {
        private fun expiryMillisFromRaw(raw: String): Long? {
            val trimmed = raw.trim()
            if (trimmed == "A") return null
            val idx = trimmed.indexOf(':')
            if (idx <= 0 || idx >= trimmed.length - 1) return null
            val epochSec = trimmed.substring(idx + 1).toDoubleOrNull() ?: return null
            return (epochSec * 1000.0).toLong()
        }

        fun decodeStored(raw: String?, nowMillis: Long = System.currentTimeMillis()): SquadNotificationMuteChoice {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return OFF
            if (trimmed == "A") return ALWAYS
            val idx = trimmed.indexOf(':')
            if (idx <= 0 || idx >= trimmed.length - 1) return OFF
            val prefix = trimmed.substring(0, idx)
            val exp = expiryMillisFromRaw(trimmed) ?: return OFF
            if (nowMillis >= exp) return OFF
            return when (prefix) {
                "EOD" -> END_OF_DAY
                "H24" -> TWENTY_FOUR_HOURS
                "W1" -> ONE_WEEK
                else -> OFF
            }
        }

        fun encode(choice: SquadNotificationMuteChoice, nowMillis: Long = System.currentTimeMillis()): String? {
            val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.US)
            return when (choice) {
                OFF -> null
                ALWAYS -> "A"
                END_OF_DAY -> {
                    cal.timeInMillis = nowMillis
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    "EOD:${cal.timeInMillis / 1000.0}"
                }
                TWENTY_FOUR_HOURS -> {
                    val exp = nowMillis + 86_400_000L
                    "H24:${exp / 1000.0}"
                }
                ONE_WEEK -> {
                    val exp = nowMillis + 86_400_000L * 7
                    "W1:${exp / 1000.0}"
                }
            }
        }

        fun isMuted(raw: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return false
            if (trimmed == "A") return true
            val exp = expiryMillisFromRaw(trimmed) ?: return false
            return nowMillis < exp
        }
    }
}

object SquadNotificationMuteEvaluator {
    fun shouldSuppressChatNotificationSound(
        prefs: Preferences,
        circleId: String?,
        pushType: String?,
    ): Boolean {
        val cid = circleId?.trim().orEmpty()
        if (cid.isEmpty()) return false
        val bucket = SquadNotificationMuteBucket.forPushType(pushType) ?: return false
        val key = SquadNotificationMuteBucket.preferenceKey(cid, bucket)
        val raw = prefs[key] ?: return false
        return SquadNotificationMuteChoice.isMuted(raw)
    }

    fun shouldSuppressMentionRealtimeTone(
        prefs: Preferences,
        circleId: String?,
    ): Boolean {
        val cid = circleId?.trim().orEmpty()
        if (cid.isEmpty()) return false
        val key = SquadNotificationMuteBucket.preferenceKey(cid, SquadNotificationMuteBucket.MENTIONS_AND_REPLIES)
        val raw = prefs[key] ?: return false
        return SquadNotificationMuteChoice.isMuted(raw)
    }
}
