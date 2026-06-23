package to.ottomot.driftd.car

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.appContainer

class OttoCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        Log.d("OttoCarAppService", "Android Auto CarAppService created debug=${BuildConfig.DEBUG}")
    }

    override fun onDestroy() {
        Log.d("OttoCarAppService", "Android Auto CarAppService destroyed")
        applicationContext.appContainer().androidAutoDriveStateBridge.setCarSessionActive(false)
        super.onDestroy()
    }

    override fun createHostValidator(): HostValidator {
        val validator =
            if (BuildConfig.DEBUG) {
                HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
            } else {
                HostValidator.Builder(applicationContext)
                    .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                    .build()
            }
        Log.d(
            "OttoCarAppService",
            "Android Auto createHostValidator debug=${BuildConfig.DEBUG} allowAllHosts=${BuildConfig.DEBUG}",
        )
        return validator
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        Log.d("AndroidAutoMap", "Car session created")
        Log.d("OttoCarAppService", "Android Auto onCreateSession sessionInfo=$sessionInfo")
        return OttoCarSession()
    }

    @Deprecated("Use onCreateSession(SessionInfo)")
    override fun onCreateSession(): Session {
        Log.d("AndroidAutoMap", "Car session created")
        Log.d("OttoCarAppService", "Android Auto onCreateSession legacy")
        return OttoCarSession()
    }
}
