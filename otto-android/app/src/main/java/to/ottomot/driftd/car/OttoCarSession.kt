package to.ottomot.driftd.car

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import kotlin.math.abs

class OttoCarSession : Session() {
    private val viewModelStore = ViewModelStore()
    private val mapboxAccessToken = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
    private var viewModel: OttoShellViewModel? = null
    private val androidAutoStyleUri = Style.DARK

    private fun createInstalledMapboxCarMap(): MapboxCarMap =
        mapboxMapInstaller()
            .install { carContext ->
                Log.d("AndroidAutoMap", "Map surface available")
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
        val carMap = createInstalledMapboxCarMap()
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

        val mapScreen =
            OttoCarMapScreen(
                carContext = carContext,
                mapboxCarMap = carMap,
                viewModel = vm,
                dataRepository = container.dataRepository,
            )
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

private const val ACTION_CAR_NAVIGATE = "androidx.car.app.action.NAVIGATE"

private fun Intent.toAndroidAutoNavigationRequest(): AndroidAutoNavigationIntentRequest? {
    val uri = data ?: return null
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "geo" || it == "geo.offline" } ?: return null
    val isNavigateAction = action == ACTION_CAR_NAVIGATE
    val isSearchAction = action == Intent.ACTION_VIEW
    if (!isNavigateAction && !isSearchAction) return null

    val query = uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
    val coordinates = uri.geoCoordinates()
    val requestedIntent = uri.getQueryParameter("intent")?.trim()?.lowercase()
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

private fun Uri.geoCoordinates(): Pair<Double, Double>? {
    val raw = schemeSpecificPart
        ?.substringBefore("?")
        ?.substringBefore("#")
        ?.trim()
        .orEmpty()
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
