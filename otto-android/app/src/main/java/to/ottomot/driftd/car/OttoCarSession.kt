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
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.OttoShellViewModel
import to.ottomot.driftd.appContainer
import to.ottomot.driftd.core.data.OttoDataRepository
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

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
            "OttoCarMapObserver",
            "Android Auto onCreateScreen action=${intent.action} data=${intent.data} mapboxTokenPresent=${mapboxAccessToken.isNotEmpty()}",
        )
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
}

internal enum class AndroidAutoNavigationIntentKind {
    Navigation,
    Directions,
    AddStop,
    Search,
}

internal data class AndroidAutoNavigationIntentRequest(
    val query: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kind: AndroidAutoNavigationIntentKind,
) {
    val displayName: String
        get() =
            query?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(latitude, longitude)
                    .takeIf { it.size == 2 }
                    ?.joinToString(", ") { coordinate -> "%.5f".format(coordinate) }
                ?: ""

    val hasDestination: Boolean
        get() = displayName.isNotBlank()
}

internal const val ACTION_CAR_NAVIGATE = "androidx.car.app.action.NAVIGATE"

internal fun Intent.toAndroidAutoNavigationRequest(): AndroidAutoNavigationIntentRequest? =
    parseAndroidAutoNavigationRequest(action = action, dataString = data?.toString())

internal fun parseAndroidAutoNavigationRequest(
    action: String?,
    dataString: String?,
): AndroidAutoNavigationIntentRequest? {
    val rawUri = dataString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val scheme = rawUri.substringBefore(":", missingDelimiterValue = "").lowercase()
        .takeIf { it == "geo" || it == "geo.offline" } ?: return null
    val isNavigateAction = action == ACTION_CAR_NAVIGATE
    val isSearchAction = action == Intent.ACTION_VIEW
    if (!isNavigateAction && !isSearchAction) return null

    val queryParameters = rawUri.queryParameters()
    val query = queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
    val coordinates = rawUri.geoCoordinates()
    val requestedIntent = queryParameters["intent"]?.trim()?.lowercase()
    val kind =
        when {
            isSearchAction -> AndroidAutoNavigationIntentKind.Search
            requestedIntent == "add_a_stop" -> AndroidAutoNavigationIntentKind.AddStop
            requestedIntent == "directions" -> AndroidAutoNavigationIntentKind.Directions
            else -> AndroidAutoNavigationIntentKind.Navigation
        }
    val request =
        AndroidAutoNavigationIntentRequest(
            query = query,
            latitude = coordinates?.first,
            longitude = coordinates?.second,
            kind = kind,
        )
    return request.takeIf { it.hasDestination && scheme.isNotBlank() }
}

private fun String.queryParameters(): Map<String, String> {
    val query = substringAfter("?", missingDelimiterValue = "")
        .substringBefore("#")
        .takeIf { it.isNotEmpty() } ?: return emptyMap()
    return query
        .split("&")
        .mapNotNull { parameter ->
            val rawName = parameter.substringBefore("=", missingDelimiterValue = parameter)
            if (rawName.isEmpty()) return@mapNotNull null
            val rawValue = parameter.substringAfter("=", missingDelimiterValue = "")
            rawName.urlDecode() to rawValue.urlDecode()
        }.toMap()
}

private fun String.urlDecode(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())

private fun String.geoCoordinates(): Pair<Double, Double>? {
    val raw = substringAfter(":", missingDelimiterValue = "")
        .substringBefore("?")
        .substringBefore("#")
        .trim()
    val parts = raw.split(",")
    if (parts.size < 2) return null
    val latitude = parts[0].toDoubleOrNull() ?: return null
    val longitude = parts[1].toDoubleOrNull() ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return if (abs(latitude) < 0.000001 && abs(longitude) < 0.000001) {
        null
    } else {
        latitude to longitude
    }
}
