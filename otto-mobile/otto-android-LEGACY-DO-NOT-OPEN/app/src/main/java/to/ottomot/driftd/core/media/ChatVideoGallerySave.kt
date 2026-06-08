package to.ottomot.driftd.core.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ChatVideoSaveResult {
    data object Success : ChatVideoSaveResult

    data class Error(val message: String) : ChatVideoSaveResult
}

private val httpClient = OkHttpClient()

suspend fun saveChatVideoToGallery(
    context: Context,
    url: String,
): ChatVideoSaveResult =
    withContext(Dispatchers.IO) {
        try {
            val response =
                httpClient.newCall(Request.Builder().url(url).get().build()).execute()
            if (!response.isSuccessful) {
                return@withContext ChatVideoSaveResult.Error("Couldn't load video")
            }
            val body =
                response.body ?: return@withContext ChatVideoSaveResult.Error("Couldn't load video")
            val mime = videoMimeType(body.contentType()?.toString(), url)
            val extension = extensionForVideoMime(mime, url)
            val filename = "Driftd_${System.currentTimeMillis()}.$extension"
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Driftd")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext ChatVideoSaveResult.Error("Something went wrong.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext ChatVideoSaveResult.Error("Something went wrong.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                ChatVideoSaveResult.Success
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                ChatVideoSaveResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            ChatVideoSaveResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
        }
    }

private fun videoMimeType(contentType: String?, url: String): String {
    val fromHeader =
        contentType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.startsWith("video/") }
    if (fromHeader != null) return fromHeader
    return when {
        url.contains(".mov", ignoreCase = true) -> "video/quicktime"
        url.contains(".m4v", ignoreCase = true) -> "video/x-m4v"
        else -> "video/mp4"
    }
}

private fun extensionForVideoMime(mime: String, url: String): String =
    when (mime) {
        "video/quicktime" -> "mov"
        "video/x-m4v" -> "m4v"
        else ->
            when {
                url.contains(".mov", ignoreCase = true) -> "mov"
                url.contains(".m4v", ignoreCase = true) -> "m4v"
                else -> "mp4"
            }
    }
