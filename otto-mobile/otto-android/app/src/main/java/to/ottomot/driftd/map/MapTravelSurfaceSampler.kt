package to.ottomot.driftd.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.ottomot.driftd.BuildConfig

fun sampleTravelSurface(
    latitude: Double,
    longitude: Double,
    speedMph: Double,
    scope: CoroutineScope,
    onResult: (TravelSurface) -> Unit,
) {
    val token = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
    scope.launch {
        val surface =
            MapTravelSurfaceTilequery.sample(
                latitude = latitude,
                longitude = longitude,
                speedMph = speedMph,
                accessToken = token,
            )
        withContext(Dispatchers.Main.immediate) {
            onResult(surface)
        }
    }
}
