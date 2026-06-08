package to.ottomot.driftd.core.notify

import android.content.Context
import to.ottomot.driftd.AppContainer
import to.ottomot.driftd.core.network.dto.PatchMeTimeZoneRequestDto
import java.time.ZoneId

/**
 * Reports the device IANA time zone when it changes (push register + foreground).
 * Mirrors iOS [TimeZoneSync].
 */
object TimeZoneSync {
    private const val PREFS_NAME = "otto_time_zone_sync"
    private const val KEY_LAST_REPORTED = "otto.lastReportedTimeZone"

    fun systemIanaId(): String = ZoneId.systemDefault().id

    suspend fun syncIfNeeded(container: AppContainer) {
        val authToken = container.sessionRepository.authTokenState.value?.trim()
        if (authToken.isNullOrEmpty()) return

        val current = systemIanaId()
        if (current.isEmpty()) return

        val prefs = container.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_LAST_REPORTED, null)
        if (cached == current) return

        container.dataRepository.patchMeTimeZone(PatchMeTimeZoneRequestDto(current)).fold(
            onSuccess = {
                prefs.edit().putString(KEY_LAST_REPORTED, current).apply()
            },
            onFailure = { /* retry next foreground */ },
        )
    }

    fun primeCacheFromServerTimeZone(context: Context, serverTimeZone: String?) {
        val tz = serverTimeZone?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_REPORTED)) {
            prefs.edit().putString(KEY_LAST_REPORTED, tz).apply()
        }
    }
}
