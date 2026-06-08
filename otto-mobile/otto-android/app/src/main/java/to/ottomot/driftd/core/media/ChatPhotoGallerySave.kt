package to.ottomot.driftd.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.SuccessResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.ottomot.driftd.ottoImageRequest

sealed interface ChatPhotoSaveResult {
    data object Success : ChatPhotoSaveResult

    data class Error(val message: String) : ChatPhotoSaveResult
}

suspend fun saveChatPhotoToGallery(
    context: Context,
    url: String,
): ChatPhotoSaveResult =
    withContext(Dispatchers.IO) {
        try {
            val request =
                ottoImageRequest(context, url)
                    .newBuilder()
                    .allowHardware(false)
                    .build()
            val result = context.imageLoader.execute(request)
            val bitmap =
                (result as? SuccessResult)?.drawable?.toBitmapOrNull()
                    ?: return@withContext ChatPhotoSaveResult.Error("Couldn't load image")
            saveBitmapToGallery(context, bitmap)
        } catch (e: Exception) {
            ChatPhotoSaveResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
        }
    }

private fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
): ChatPhotoSaveResult {
    val filename = "Driftd_${System.currentTimeMillis()}.jpg"
    val contentValues =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Driftd")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

    val resolver = context.contentResolver
    val uri =
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return ChatPhotoSaveResult.Error("Something went wrong.")

    try {
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                resolver.delete(uri, null, null)
                return ChatPhotoSaveResult.Error("Something went wrong.")
            }
        } ?: run {
            resolver.delete(uri, null, null)
            return ChatPhotoSaveResult.Error("Something went wrong.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return ChatPhotoSaveResult.Success
    } catch (e: IOException) {
        resolver.delete(uri, null, null)
        return ChatPhotoSaveResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
    }
}
