package to.ottomot.driftd

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Static map JPEG for place chat attachments (Mapbox Static Images + composited saved-place pin). */
object PlaceMapSnapshotGenerator {
    private const val STATIC_WIDTH = 640
    private const val STATIC_HEIGHT = 280
    private const val STATIC_ZOOM = 18
    /** ~2× live map pin (`OttoMapSavedPlaceMarkerContent` 56×84 dp) for chat preview legibility. */
    private const val PIN_WIDTH_PX = 224
    private const val PIN_HEIGHT_PX = 336

    suspend fun jpegBytes(
        latitude: Double,
        longitude: Double,
        accessToken: String?,
        resources: Resources,
    ): ByteArray? {
        if (!latitude.isFinite() || !longitude.isFinite() || accessToken.isNullOrBlank()) {
            return null
        }
        val url = buildStaticImageUrl(latitude, longitude, accessToken) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    useCaches = false
                }
                connection.inputStream.use { input ->
                    val baseMap = BitmapFactory.decodeStream(input) ?: return@withContext null
                    val composited = compositeSavedPlacePin(baseMap, resources)
                    ByteArrayOutputStream().use { out ->
                        if (!composited.compress(Bitmap.CompressFormat.JPEG, 84, out)) {
                            return@withContext null
                        }
                        out.toByteArray()
                    }
                }
            }.getOrNull()
        }
    }

    fun buildStaticImageUrl(latitude: Double, longitude: Double, accessToken: String): String? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return "https://api.mapbox.com/styles/v1/mapbox/dark-v11/static/" +
            "$longitude,$latitude,$STATIC_ZOOM/${STATIC_WIDTH}x${STATIC_HEIGHT}@2x" +
            "?access_token=$accessToken"
    }

    private fun compositeSavedPlacePin(baseMap: Bitmap, resources: Resources): Bitmap {
        val pin = BitmapFactory.decodeResource(resources, R.drawable.map_point_saved) ?: return baseMap
        val output = baseMap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        canvas.drawBitmap(baseMap, 0f, 0f, null)

        val centerX = baseMap.width / 2f
        val centerY = baseMap.height / 2f
        val pinLeft = centerX - PIN_WIDTH_PX / 2f
        val pinTop = centerY - PIN_HEIGHT_PX
        val pinRect =
            android.graphics.Rect(
                pinLeft.toInt(),
                pinTop.toInt(),
                (pinLeft + PIN_WIDTH_PX).toInt(),
                (pinTop + PIN_HEIGHT_PX).toInt(),
            )
        canvas.drawBitmap(pin, null, pinRect, null)
        return output
    }
}
