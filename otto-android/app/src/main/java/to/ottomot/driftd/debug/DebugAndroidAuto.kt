package to.ottomot.driftd.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.appContainer

internal object DebugAndroidAuto {
    const val ACTION_START_ROUTE_DRIVE = "to.ottomot.driftd.debug.START_ROUTE_DRIVE"
    private const val TAG = "DebugAndroidAuto"

    private val _startRouteDriveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startRouteDriveRequests = _startRouteDriveRequests.asSharedFlow()

    fun register(applicationContext: Context) {
        if (!BuildConfig.DEBUG) return
        val receiver = DebugAndroidAutoReceiver()
        val filter = IntentFilter(ACTION_START_ROUTE_DRIVE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            applicationContext.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "Registered debug Android Auto route-drive broadcast receiver")
    }

    fun requestStartRouteDrive() {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "Queueing debug Android Auto route-drive request")
        _startRouteDriveRequests.tryEmit(Unit)
    }

    fun markPendingStartRouteDrive(applicationContext: Context) {
        if (!BuildConfig.DEBUG) return
        applicationContext.appContainer().debugPendingAndroidAutoRouteDrive = true
        requestStartRouteDrive()
    }

    private class DebugAndroidAutoReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?,
        ) {
            if (!BuildConfig.DEBUG || intent?.action != ACTION_START_ROUTE_DRIVE) return
            val appContext = context?.applicationContext ?: return
            Log.d(TAG, "Received debug Android Auto route-drive broadcast")
            markPendingStartRouteDrive(appContext)
        }
    }
}
