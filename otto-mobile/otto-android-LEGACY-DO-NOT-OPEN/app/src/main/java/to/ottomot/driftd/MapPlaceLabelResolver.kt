package to.ottomot.driftd

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class MapPlaceLabel(
    val name: String?,
    val addressSummary: String?,
    val placeKind: String = "coordinates",
)

/** Best-effort reverse geocode for map long-press share/save labels. */
object MapPlaceLabelResolver {
    suspend fun resolve(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): MapPlaceLabel =
        withContext(Dispatchers.IO) {
            if (!latitude.isFinite() || !longitude.isFinite()) {
                return@withContext MapPlaceLabel(name = null, addressSummary = null)
            }
            if (!Geocoder.isPresent()) {
                return@withContext MapPlaceLabel(name = null, addressSummary = null)
            }
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    ?: return@withContext MapPlaceLabel(name = null, addressSummary = null)
                val feature = address.featureName?.trim()?.takeIf { it.isNotEmpty() }
                val thoroughfare = address.thoroughfare?.trim()?.takeIf { it.isNotEmpty() }
                val locality = address.locality?.trim()?.takeIf { it.isNotEmpty() }
                val admin = address.adminArea?.trim()?.takeIf { it.isNotEmpty() }
                val lineParts = listOfNotNull(thoroughfare, locality, admin)
                val addressLine =
                    address.getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: lineParts.joinToString(", ").takeIf { it.isNotEmpty() }
                val name = feature ?: thoroughfare ?: locality
                val kind =
                    if (name != null || addressLine != null) {
                        "address"
                    } else {
                        "coordinates"
                    }
                MapPlaceLabel(name = name, addressSummary = addressLine, placeKind = kind)
            }.getOrElse {
                MapPlaceLabel(name = null, addressSummary = null)
            }
        }
}
