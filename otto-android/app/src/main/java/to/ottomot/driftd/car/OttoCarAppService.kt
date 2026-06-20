package to.ottomot.driftd.car

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import to.ottomot.driftd.BuildConfig

class OttoCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG || applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        Log.d("AndroidAutoMap", "Car session created")
        Log.d("OttoCarMapObserver", "Android Auto onCreateSession sessionInfo=$sessionInfo")
        return OttoCarSession()
    }

    @Deprecated("Use onCreateSession(SessionInfo)")
    override fun onCreateSession(): Session {
        Log.d("AndroidAutoMap", "Car session created")
        Log.d("OttoCarMapObserver", "Android Auto onCreateSession legacy")
        return OttoCarSession()
    }
}
