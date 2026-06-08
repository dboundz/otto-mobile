package to.ottomot.driftd.core.media

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ChatVideoUploadState {
    data class PendingUpload(
        val clientMessageId: String,
        val progress: Float,
        val isFailed: Boolean,
        val thumbnailBitmap: Bitmap? = null,
    )

    private val _pendingByClientMessageId = MutableStateFlow<Map<String, PendingUpload>>(emptyMap())
    val pendingByClientMessageId: StateFlow<Map<String, PendingUpload>> = _pendingByClientMessageId.asStateFlow()

    fun register(clientMessageId: String, thumbnailBitmap: Bitmap?) {
        _pendingByClientMessageId.update { current ->
            val existing = current[clientMessageId] ?: PendingUpload(clientMessageId, 0f, false, thumbnailBitmap)
            current + (clientMessageId to existing.copy(thumbnailBitmap = thumbnailBitmap ?: existing.thumbnailBitmap))
        }
    }

    fun setProgress(clientMessageId: String, progress: Float) {
        _pendingByClientMessageId.update { current ->
            val existing = current[clientMessageId] ?: PendingUpload(clientMessageId, 0f, false)
            current + (clientMessageId to existing.copy(progress = progress.coerceIn(0f, 1f), isFailed = false))
        }
    }

    fun markFailed(clientMessageId: String) {
        _pendingByClientMessageId.update { current ->
            val existing = current[clientMessageId] ?: PendingUpload(clientMessageId, 0f, true)
            current + (clientMessageId to existing.copy(isFailed = true))
        }
    }

    fun clear(clientMessageId: String) {
        _pendingByClientMessageId.update { it - clientMessageId }
    }
}
