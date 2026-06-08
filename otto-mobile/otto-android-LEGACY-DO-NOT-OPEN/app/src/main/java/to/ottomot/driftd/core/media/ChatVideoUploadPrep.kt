package to.ottomot.driftd.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import to.ottomot.driftd.R
import to.ottomot.driftd.core.data.PhotoUploadClientTargetMaxBytes

data class ChatPreparedVideoUpload(
    val localVideoFile: File,
    val thumbnailJpeg: ByteArray,
    val thumbnailBitmap: Bitmap,
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fileSizeBytes: Long,
)

object ChatVideoUploadPrep {
    private const val MAX_BYTES = 250L * 1024L * 1024L
    private const val MAX_DURATION_MS = 60_000L
    private const val THUMB_MAX_SIDE = 1600
    private const val THUMB_QUALITY = 84

    /** Validates size and duration before copying the picked file into cache. */
    fun validate(context: Context, uri: Uri): Result<Unit> =
        runCatching {
            val (size, durationMs) = readVideoMetadata(context, uri)
            if (size <= 0) error(context.getString(R.string.chat_attachment_read_failed))
            enforceLimits(context, size, durationMs)
        }

    fun prepare(context: Context, uri: Uri): Result<ChatPreparedVideoUpload> =
        runCatching {
            val resolver = context.contentResolver
            val (size, durationMs) = readVideoMetadata(context, uri)
            if (size <= 0) error(context.getString(R.string.chat_attachment_read_failed))
            enforceLimits(context, size, durationMs)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val rotation =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1
                val (width, height) =
                    if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
                val frame =
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: error("Couldn't prepare video thumbnail.")
                val jpeg = compressThumbnail(frame)
                val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                val ext = if (mimeType.contains("quicktime")) "mov" else "mp4"
                val dest = File(context.cacheDir, "chat-video-${System.currentTimeMillis()}.$ext")
                resolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error(context.getString(R.string.chat_attachment_read_failed))
                ChatPreparedVideoUpload(
                    localVideoFile = dest,
                    thumbnailJpeg = jpeg,
                    thumbnailBitmap = frame,
                    durationSeconds = max(0.0, durationMs / 1000.0),
                    width = max(1, width),
                    height = max(1, height),
                    mimeType = mimeType,
                    fileSizeBytes = size,
                )
            } finally {
                retriever.release()
            }
        }

    private fun readVideoMetadata(context: Context, uri: Uri): Pair<Long, Long> {
        val resolver = context.contentResolver
        val size =
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?: return 0L to 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            size to durationMs
        } finally {
            retriever.release()
        }
    }

    private fun enforceLimits(context: Context, size: Long, durationMs: Long) {
        val tooLarge = size > MAX_BYTES
        val tooLong = durationMs > MAX_DURATION_MS
        if (tooLarge || tooLong) {
            error(limitMessage(context, tooLarge, tooLong))
        }
    }

    private fun limitMessage(context: Context, tooLarge: Boolean, tooLong: Boolean): String =
        when {
            tooLarge && tooLong -> context.getString(R.string.chat_video_too_large_and_long)
            tooLarge -> context.getString(R.string.chat_video_too_large)
            tooLong -> context.getString(R.string.chat_video_too_long)
            else -> context.getString(R.string.chat_attachment_read_failed)
        }

    private fun compressThumbnail(source: Bitmap): ByteArray {
        val maxSide = max(source.width, source.height).toFloat()
        val scale = min(1f, THUMB_MAX_SIDE / max(maxSide, 1f))
        val targetW = max(1, (source.width * scale).roundToInt())
        val targetH = max(1, (source.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        if (scaled !== source) scaled.recycle()
        val bytes = out.toByteArray()
        if (bytes.size > PhotoUploadClientTargetMaxBytes) {
            error("Video thumbnail is too large.")
        }
        return bytes
    }
}
