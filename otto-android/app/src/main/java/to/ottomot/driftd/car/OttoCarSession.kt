package to.ottomot.driftd.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.androidauto.MapboxCarMap
import com.mapbox.maps.extension.androidauto.mapboxMapInstaller
import to.ottomot.driftd.AndroidAutoDriveBridgeMode
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.OttoShellViewModel
import to.ottomot.driftd.appContainer

class OttoCarSession : Session() {
    private val viewModelStore = ViewModelStore()
    private val mapboxAccessToken = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
    private val mapboxCarMap: MapboxCarMap =
        if (mapboxAccessToken.isNotEmpty()) {
            MapboxOptions.accessToken = mapboxAccessToken
            createInstalledMapboxCarMap()
        } else {
            createInstalledMapboxCarMap()
        }
    private var viewModel: OttoShellViewModel? = null

    private fun createInstalledMapboxCarMap(): MapboxCarMap =
        mapboxMapInstaller()
            .install { carContext ->
                MapInitOptions(
                    context = carContext,
                    styleUri = Style.DARK,
                    mapName = "android-auto",
                )
            }

    override fun onCreateScreen(intent: Intent): Screen {
        val carMap = mapboxCarMap
        val container = carContext.applicationContext.appContainer()
        val vm =
            ViewModelProvider(
                viewModelStore,
                OttoShellViewModel.factory(
                    container = container,
                    androidAutoDriveBridgeMode = AndroidAutoDriveBridgeMode.Consume,
                ),
            )[OttoShellViewModel::class.java]
        viewModel = vm

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    vm.setAndroidAutoMapActive(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    vm.setAndroidAutoMapActive(false)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    carMap.clearObservers()
                    viewModelStore.clear()
                }
            },
        )

        val screen =
            OttoCarMapScreen(
                carContext = carContext,
                mapboxCarMap = carMap,
                viewModel = vm,
            )
        return screen
    }
}
