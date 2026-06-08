package to.ottomot.driftd.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlin.math.roundToInt
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.DriveSessionKind

object OttoAnalytics {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isConfigured = false

    fun configure(context: Context) {
        if (isConfigured) return
        val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        firebaseAnalytics = analytics
        isConfigured = true
        if (BuildConfig.DEBUG) {
            analytics.setAnalyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        }
    }

    fun setUserID(userId: String) {
        if (userId.isBlank()) return
        firebaseAnalytics?.setUserId(userId)
        FirebaseCrashlytics.getInstance().setUserId(userId)
    }

    fun clearUserID() {
        firebaseAnalytics?.setUserId(null)
        FirebaseCrashlytics.getInstance().setUserId("")
    }

    fun logSignUpComplete() {
        logEvent("sign_up_complete")
    }

    fun logOnboardingNameComplete() {
        logEvent("onboarding_name_complete")
    }

    fun logLocationSharingEnabled() {
        logEvent(
            "location_sharing_enabled",
            bundleOf("source" to "map"),
        )
    }

    fun logLocationSharingDisabled(reason: String) {
        logEvent(
            "location_sharing_disabled",
            bundleOf("reason" to reason),
        )
    }

    fun logDriveStarted(
        kind: String,
        routeId: String? = null,
    ) {
        val params =
            Bundle().apply {
                putString("kind", kind)
                routeId?.trim()?.takeIf { it.isNotEmpty() }?.let { putString("route_id", it) }
            }
        logEvent("drive_started", params)
    }

    fun logDriveCompleted(
        kind: String,
        distanceMeters: Double,
    ) {
        val bucket = ((distanceMeters / 100.0).roundToInt() * 100).coerceAtLeast(0)
        logEvent(
            "drive_completed",
            bundleOf(
                "kind" to kind,
                "distance_meters" to bucket.toLong(),
            ),
        )
    }

    fun logEventCheckIn(eventId: String) {
        val trimmed = eventId.trim()
        if (trimmed.isEmpty()) return
        logEvent(
            "event_check_in",
            bundleOf("event_id" to trimmed),
        )
    }

    fun logSquadCreated() {
        logEvent("squad_created")
    }

    fun logSquadJoined(source: String) {
        logEvent(
            "squad_joined",
            bundleOf("source" to source),
        )
    }

    fun logChatMessageSent(
        channel: String,
        attachmentType: String,
    ) {
        logEvent(
            "chat_message_sent",
            bundleOf(
                "channel" to channel,
                "attachment_type" to attachmentType,
            ),
        )
    }

    fun logGarageCarAdded(hasPhoto: Boolean) {
        logEvent(
            "garage_car_added",
            bundleOf("has_photo" to if (hasPhoto) "true" else "false"),
        )
    }

    fun logRouteSaved() {
        logEvent("route_saved")
    }

    fun logDriveStartedFromSession(
        kind: DriveSessionKind,
        routeId: String? = null,
    ) {
        logDriveStarted(analyticsDriveKind(kind), routeId)
    }

    fun logDriveCompletedFromSession(
        kind: DriveSessionKind,
        distanceMeters: Double,
    ) {
        logDriveCompleted(analyticsDriveKind(kind), distanceMeters)
    }

    private fun analyticsDriveKind(kind: DriveSessionKind): String =
        when (kind) {
            DriveSessionKind.QUICK -> "quick"
            DriveSessionKind.ROUTE -> "route"
            DriveSessionKind.LIVE -> "quick"
        }

    private fun logEvent(
        name: String,
        params: Bundle? = null,
    ) {
        firebaseAnalytics?.logEvent(name, params)
    }

    private fun bundleOf(vararg pairs: Pair<String, Any>): Bundle =
        Bundle(pairs.size).apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putLong(key, value.toLong())
                    is Boolean -> putString(key, if (value) "true" else "false")
                    else -> putString(key, value.toString())
                }
            }
        }
}
