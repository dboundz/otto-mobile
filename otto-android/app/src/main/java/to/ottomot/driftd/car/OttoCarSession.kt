package to.ottomot.driftd.car

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
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
import to.ottomot.driftd.ACTION_ANDROID_AUTO_NAVIGATE
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.NavigationIntentKind
import to.ottomot.driftd.NavigationIntentRequest
import to.ottomot.driftd.OttoShellViewModel
import to.ottomot.driftd.appContainer
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.parseNavigationIntentRequest

class OttoCarSession : Session() {
    private val viewModelStore = ViewModelStore()
    private val mapboxAccessToken = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
    private var viewModel: OttoShellViewModel? = null
    private var dataRepository: OttoDataRepository? = null
    private var mapScreen: OttoCarMapScreen? = null
    private val androidAutoStyleUri = Style.DARK

    // Must install on the Session instance (field initializer), not inside onCreateScreen.
    // mapboxMapInstaller().install() registers for Session.onCreate; calling install later can
    // miss that callback and never invoke MapboxCarMap.setup/setSurfaceCallback (black map).
    private val mapboxCarMap: MapboxCarMap =
        mapboxMapInstaller()
            .install { carContext ->
                Log.d("AndroidAutoMap", "Configuring MapInitOptions for Android Auto")
                validateAndroidAutoMapboxConfig(reason = "map-install")
                MapInitOptions(
                    context = carContext,
                    styleUri = androidAutoStyleUri,
                    mapName = "android-auto",
                )
            }

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d("AndroidAutoMap", "Screen created")
        validateAndroidAutoMapboxConfig(reason = "screen-create")
        Log.d(
            "OttoCarAppService",
            "Android Auto Session.onCreateScreen action=${intent.action} data=${intent.data}",
        )
        Log.d(
            "OttoCarMapObserver",
            "Android Auto onCreateScreen action=${intent.action} data=${intent.data} mapboxTokenPresent=${mapboxAccessToken.isNotEmpty()}",
        )
        logAndroidAutoIntentExtras("onCreateScreen", intent)
        val carMap = mapboxCarMap
        val container = carContext.applicationContext.appContainer()
        dataRepository = container.dataRepository
        val vm =
            ViewModelProvider(
                viewModelStore,
                OttoShellViewModel.factory(
                    container = container,
                    androidAutoDriveBridgeMode = AndroidAutoDriveBridgeMode.Consume,
                ),
            )[OttoShellViewModel::class.java]
        viewModel = vm

        val bridge = container.androidAutoDriveStateBridge
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    bridge.setCarSessionActive(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    bridge.setCarSessionActive(false)
                    Log.d("AndroidAutoMap", "Car session inactive")
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    bridge.setCarSessionActive(false)
                    vm.setAndroidAutoMapActive(false)
                    carMap.clearObservers()
                    viewModelStore.clear()
                }
            },
        )

        val mapScreen =
            OttoCarMapScreen(
                carContext = carContext,
                mapboxCarMap = carMap,
                viewModel = vm,
                dataRepository = container.dataRepository,
            )
        this.mapScreen = mapScreen
        val navigationRequest = intent.toAndroidAutoNavigationRequest()
        Log.d(
            "OttoCarMapObserver",
            "Android Auto onCreateScreen resolvedNavigationRequest=${navigationRequest != null}",
        )
        return if (navigationRequest != null) {
            OttoCarNavigationIntentScreen(
                carContext = carContext,
                request = navigationRequest,
                viewModel = vm,
                dataRepository = container.dataRepository,
                mapScreen = mapScreen,
            )
        } else {
            mapScreen
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(
            "OttoCarMapObserver",
            "Android Auto onNewIntent action=${intent.action} data=${intent.data}",
        )
        logAndroidAutoIntentExtras("onNewIntent", intent)
        val navigationRequest = intent.toAndroidAutoNavigationRequest()
        Log.d(
            "OttoCarMapObserver",
            "Android Auto onNewIntent resolvedNavigationRequest=${navigationRequest != null}",
        )
        val screen = navigationRequest?.let { createNavigationIntentScreen(it) } ?: return
        carContext.getCarService(ScreenManager::class.java).push(screen)
    }

    private fun createNavigationIntentScreen(request: AndroidAutoNavigationIntentRequest): Screen? {
        val vm = viewModel ?: return null
        val repository = dataRepository ?: return null
        val currentMapScreen = mapScreen ?: return null
        return OttoCarNavigationIntentScreen(
            carContext = carContext,
            request = request,
            viewModel = vm,
            dataRepository = repository,
            mapScreen = currentMapScreen,
        )
    }

    private fun validateAndroidAutoMapboxConfig(reason: String): Boolean {
        val tokenReady = mapboxAccessToken.isNotBlank()
        val styleReady = androidAutoStyleUri.isNotBlank()
        Log.d("AndroidAutoMap", "Mapbox token present: $tokenReady")
        Log.d("AndroidAutoMap", "Style URI: $androidAutoStyleUri")
        Log.d(
            "AndroidAutoMap",
            "Config validation reason=$reason tokenReady=$tokenReady styleReady=$styleReady",
        )
        if (tokenReady) {
            MapboxOptions.accessToken = mapboxAccessToken
        }
        return tokenReady && styleReady
    }

    private fun logAndroidAutoIntentExtras(reason: String, intent: Intent) {
        if (intent.action != ACTION_CAR_NAVIGATE) return
        val extras = intent.extras
        val summary =
            extras
                ?.keySet()
                ?.sorted()
                ?.joinToString { key ->
                    val value = extras.get(key)
                    "$key=${value.androidAutoExtraTypeSummary()}"
                }
                ?.ifBlank { "none" }
                ?: "none"
        Log.d("OttoCarMapObserver", "Android Auto intent extras reason=$reason keys=$summary")
    }

    private fun Any?.androidAutoExtraTypeSummary(): String =
        when (this) {
            null -> "null"
            is String -> "String(len=$length)"
            is CharSequence -> "${javaClass.simpleName}(len=$length)"
            is Boolean,
            is Number,
            -> javaClass.simpleName
            is android.os.Parcelable -> "Parcelable(${javaClass.name})"
            is java.io.Serializable -> "Serializable(${javaClass.name})"
            else -> javaClass.name
        }
}

internal typealias AndroidAutoNavigationIntentKind = NavigationIntentKind
internal typealias AndroidAutoNavigationIntentRequest = NavigationIntentRequest

internal const val ACTION_CAR_NAVIGATE = ACTION_ANDROID_AUTO_NAVIGATE

internal fun Intent.toAndroidAutoNavigationRequest(): AndroidAutoNavigationIntentRequest? =
    parseAndroidAutoNavigationRequest(action = action, dataString = data?.toString())

internal fun parseAndroidAutoNavigationRequest(
    action: String?,
    dataString: String?,
): AndroidAutoNavigationIntentRequest? {
    return parseNavigationIntentRequest(
        action = action,
        dataString = dataString,
        acceptedNavigateActions = setOf(ACTION_CAR_NAVIGATE),
        acceptsSearchAction = true,
    )
}
