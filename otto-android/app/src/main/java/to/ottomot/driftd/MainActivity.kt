package to.ottomot.driftd

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import to.ottomot.driftd.car.OttoCarAppService
import to.ottomot.driftd.debug.DebugAndroidAuto
import to.ottomot.driftd.ui.theme.OttoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleDebugAndroidAutoIntent(intent)
        clearLauncherNotificationMarker()
        InviteDeepLinkStore.offer(this, intent)
        PushNotificationTapStore.offerFromIntent(intent)
        val container = applicationContext.appContainer()
        lifecycle.addObserver(container.deviceLocationTracker)
        enableEdgeToEdge()
        setContent {
            OttoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    OttoRoot(
                        container = container,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clearLauncherNotificationMarker()
        reconcileAndroidAutoPhoneMapState()
    }

    private fun reconcileAndroidAutoPhoneMapState() {
        val bridge = applicationContext.appContainer().androidAutoDriveStateBridge
        if (bridge.carSessionActive.value && !isCarAppServiceRunning()) {
            bridge.setCarSessionActive(false)
        }
    }

    private fun isCarAppServiceRunning(): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { service ->
            service.service.className == OttoCarAppService::class.java.name
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugAndroidAutoIntent(intent)
        clearLauncherNotificationMarker()
        setIntent(intent)
        InviteDeepLinkStore.offer(this, intent)
        PushNotificationTapStore.offerFromIntent(intent)
    }

    private fun clearLauncherNotificationMarker() {
        NotificationManagerCompat.from(this).cancelAll()
    }

    private fun handleDebugAndroidAutoIntent(intent: Intent?) {
        if (intent?.action != DebugAndroidAuto.ACTION_START_ROUTE_DRIVE) return
        DebugAndroidAuto.markPendingStartRouteDrive(applicationContext)
    }
}
