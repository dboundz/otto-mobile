package to.ottomot.driftd.core.race

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import to.ottomot.driftd.core.config.OttoEndpoints

data class RaceTrackRecord(
    val name: String,
    val type: List<String>,
    val city: String,
    val state: String,
    val lat: Double,
    val lng: Double,
) {
    val stableId: String
        get() = "$name|$city|$state"

    val locationLine: String
        get() = "$city, $state"

    val formattedTypes: String
        get() =
            type
                .map { it.replace('_', ' ') }
                .joinToString(" · ") { part ->
                    part.split(' ').joinToString(" ") { word ->
                        word.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                        }
                    }
                }
}

fun RaceTrackRecord.coordinateOrNull(): Pair<Double, Double>? {
    if (!lat.isFinite() || !lng.isFinite()) return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

/**
 * Server-backed US race track list with disk cache (mirrors iOS [RaceTracksDatasetStore]).
 */
class RaceTracksDataset(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val cacheFile = File(appContext.cacheDir, "otto/us_tracks_dataset.cache.json")
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var tracks: List<RaceTrackRecord> = loadCached()
        private set

    suspend fun refreshIfStale(forceWhenEmpty: Boolean = true) {
        val lastFetchMs = prefs.getLong(KEY_LAST_FETCH_MS, 0L)
        val stale =
            tracks.isEmpty() && forceWhenEmpty ||
                lastFetchMs == 0L ||
                System.currentTimeMillis() - lastFetchMs >= REFRESH_INTERVAL_MS
        if (!stale) return

        withContext(Dispatchers.IO) {
            runCatching {
                val url = remoteUrl()
                val request =
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val body = response.body?.string()?.trim().orEmpty()
                    if (body.isEmpty()) return@withContext
                    val type = object : TypeToken<List<RaceTrackRecord>>() {}.type
                    val decoded: List<RaceTrackRecord> = gson.fromJson(body, type) ?: emptyList()
                    tracks = decoded
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(body)
                    prefs.edit().putLong(KEY_LAST_FETCH_MS, System.currentTimeMillis()).apply()
                }
            }
        }
    }

    private fun loadCached(): List<RaceTrackRecord> {
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            val json = cacheFile.readText()
            if (json.isBlank()) return emptyList()
            val type = object : TypeToken<List<RaceTrackRecord>>() {}.type
            gson.fromJson<List<RaceTrackRecord>>(json, type) ?: emptyList()
        }.getOrElse {
            cacheFile.delete()
            emptyList()
        }
    }

    private fun remoteUrl(): String {
        val base = OttoEndpoints.httpBaseUrl.trimEnd('/')
        return "$base/static/us_tracks_dataset.json"
    }

    companion object {
        private const val PREFS_NAME = "otto_race_tracks"
        private const val KEY_LAST_FETCH_MS = "lastSuccessfulFetchMs"
        private val REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
    }
}
