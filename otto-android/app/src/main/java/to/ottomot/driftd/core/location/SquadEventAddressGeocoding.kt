package to.ottomot.driftd.core.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Best-effort forward geocode for squad event pins (parity with iOS `SquadEventAddressGeocoding`). */
suspend fun geocodeSquadEventAddressIfResolvable(
    context: Context,
    query: String,
): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 4) return@withContext null
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val list = geocoder.getFromLocationName(trimmed, 1) ?: return@withContext null
            val a = list.firstOrNull() ?: return@withContext null
            val lat = a.latitude
            val lng = a.longitude
            if (!lat.isFinite() || !lng.isFinite()) return@withContext null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@withContext null
            lat to lng
        }.getOrNull()
    }
