package to.ottomot.driftd.map

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MapTravelSurfaceTilequery {
    private const val TILESET_ID = "mapbox.mapbox-streets-v8"
    private const val QUERY_RADIUS_METERS = 25
    private const val MAX_ROAD_DISTANCE_METERS = 15.0
    private const val MAX_WATERWAY_DISTANCE_METERS = 35.0
    private const val MAX_POLYGON_WATER_DISTANCE_METERS = 1.0

    suspend fun sample(
        latitude: Double,
        longitude: Double,
        speedMph: Double,
        accessToken: String,
    ): TravelSurface =
        withContext(Dispatchers.IO) {
            val token = accessToken.trim()
            if (token.isEmpty()) return@withContext TravelSurface.Land

            val url =
                buildString {
                    append("https://api.mapbox.com/v4/")
                    append(TILESET_ID)
                    append("/tilequery/")
                    append(longitude)
                    append(',')
                    append(latitude)
                    append(".json?radius=")
                    append(QUERY_RADIUS_METERS)
                    append("&layers=water,waterway,road&limit=50&access_token=")
                    append(token)
                }

            val body =
                runCatching {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        requestMethod = "GET"
                        useCaches = true
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        connection.disconnect()
                        return@runCatching null
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }.also {
                        connection.disconnect()
                    }
                }.getOrNull()

            if (body.isNullOrBlank()) {
                return@withContext TravelSurface.Land
            }

            val classified = classifyTilequeryBody(body)
            MapTravelSurfaceSampler.instantaneousSurface(
                speedMph = speedMph,
                onWater = classified.first,
                onRoad = classified.second,
            )
        }

    private fun classifyTilequeryBody(body: String): Pair<Boolean, Boolean> {
        var onWater = false
        var onRoad = false
        val features = JSONObject(body).optJSONArray("features") ?: return false to false
        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val tilequery = feature.optJSONObject("properties")?.optJSONObject("tilequery") ?: continue
            val layer = tilequery.optString("layer").lowercase()
            if (layer.isEmpty()) continue
            val geometry = tilequery.optString("geometry").lowercase()
            val distance = tilequery.optDouble("distance", Double.MAX_VALUE)

            if (MapTravelSurfaceSampler.isWaterLayer(layer)) {
                if (geometry == "polygon" && distance <= MAX_POLYGON_WATER_DISTANCE_METERS) {
                    onWater = true
                } else if (geometry == "linestring" && distance <= MAX_WATERWAY_DISTANCE_METERS) {
                    onWater = true
                }
            }
            if (MapTravelSurfaceSampler.isRoadLayer(layer) && distance <= MAX_ROAD_DISTANCE_METERS) {
                onRoad = true
            }
        }
        return onWater to onRoad
    }
}
