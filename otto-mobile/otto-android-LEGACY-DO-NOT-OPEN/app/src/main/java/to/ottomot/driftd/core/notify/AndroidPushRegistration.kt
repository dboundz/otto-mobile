package to.ottomot.driftd.core.notify

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import to.ottomot.driftd.AppContainer
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.core.network.dto.RegisterDeviceRequestDto

private const val OttoFcmRegTag = "OttoFcmReg"

/**
 * Fetches the FCM registration token and posts it to Otto (`POST /api/notifications/devices`)
 * when the user has an auth session. Used on sign-in and after token refresh.
 */
suspend fun registerAndroidFcmTokenWithBackend(container: AppContainer) {
    val authToken = container.sessionRepository.authTokenState.value?.trim()
    if (authToken.isNullOrEmpty()) {
        if (BuildConfig.DEBUG) Log.d(OttoFcmRegTag, "skip: no auth token")
        return
    }

    val registration =
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .onFailure { e ->
                if (BuildConfig.DEBUG) Log.e(OttoFcmRegTag, "FirebaseMessaging.token failed", e)
            }
            .getOrNull()
            ?.trim()
            ?: run {
                if (BuildConfig.DEBUG) Log.e(OttoFcmRegTag, "skip: no FCM registration token (Play services / Firebase app?)")
                return
            }
    if (registration.length < 32) {
        if (BuildConfig.DEBUG) Log.e(OttoFcmRegTag, "skip: FCM token too short (${registration.length})")
        return
    }

    val ianaTimeZone = TimeZoneSync.systemIanaId()
    container.dataRepository
        .registerPushDevice(
            RegisterDeviceRequestDto.forAndroidFcm(
                token = registration,
                applicationId = BuildConfig.APPLICATION_ID,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                timeZone = ianaTimeZone,
            ),
        ).fold(
            onSuccess = {
                container.application
                    .getSharedPreferences("otto_time_zone_sync", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("otto.lastReportedTimeZone", ianaTimeZone)
                    .apply()
                if (BuildConfig.DEBUG) Log.d(OttoFcmRegTag, "registerPushDevice ok")
            },
            onFailure = { e ->
                if (BuildConfig.DEBUG) Log.e(OttoFcmRegTag, "registerPushDevice failed", e)
            },
        )
}
