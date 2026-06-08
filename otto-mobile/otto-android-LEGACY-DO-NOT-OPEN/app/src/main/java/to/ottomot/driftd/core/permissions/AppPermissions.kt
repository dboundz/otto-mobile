package to.ottomot.driftd.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Mirrors iOS [Info.plist] usage strings for runtime checks. Declared rationale strings live in
 * `strings.xml` keys `permission_rationale_*`.
 */

// --- Location (NSLocationWhenInUse) ---

fun fineLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

fun coarseLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * True when the system can still show a runtime prompt (including Android 12+ upgrade from approximate
 * to precise). False when the user must use Settings (e.g. approximate-only with no re-prompt path).
 */
fun canRepromptFineLocationAtRuntime(context: Context): Boolean {
    if (fineLocationGranted(context)) return false
    val activity = context as? ComponentActivity ?: return true
    if (
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    ) {
        return true
    }
    return !coarseLocationGranted(context)
}

/** Precise denied with approximate granted and no in-app re-prompt — open app Settings. */
fun shouldOpenFineLocationAppSettings(context: Context): Boolean {
    if (fineLocationGranted(context)) return false
    val activity = context as? ComponentActivity ?: return false
    if (
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    ) {
        return false
    }
    return coarseLocationGranted(context)
}

// --- Activity recognition (NSMotionUsageDescription) ---

fun activityRecognitionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) == PackageManager.PERMISSION_GRANTED
}

fun shouldRequestActivityRecognition(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return !activityRecognitionGranted(context)
}

// --- Notifications (UIBackgroundModes remote-notification parity) ---

fun postNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

fun shouldRequestPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return !postNotificationsGranted(context)
}
