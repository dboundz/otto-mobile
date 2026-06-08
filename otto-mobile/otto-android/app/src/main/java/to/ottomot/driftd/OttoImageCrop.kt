package to.ottomot.driftd

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * iOS parity: [OttoImageCropperSheet] — open Yalantis uCrop after Photo Picker with a fixed aspect ratio.
 *
 * Photo Picker [Uri]s are copied into cache, then **normalized** to an upright JPEG before uCrop.
 * That avoids OEM-specific decoder/EXIF behavior (wrong rotation, mixed formats saved as `.jpg`, etc.).
 */
object OttoImageCrop {
    private const val cacheSubdir = "otto_crop"

    /** Longest edge before uCrop; keeps memory and OEM bitmap limits predictable. */
    private const val maxSourceSidePx = 4096

    private data class DecodedBitmap(
        val bitmap: Bitmap,
        /** When false, bitmap came from [ImageDecoder]; orientation is treated as already applied. */
        val applyExifTransform: Boolean,
    )

    /**
     * Photo Picker / content [Uri]s often cannot be read inside uCrop (extras are not always permission-granted).
     * Copy into app cache first and point uCrop at our [FileProvider] source.
     */
    fun uCropIntent(
        context: Context,
        sourceUri: Uri,
        aspectX: Float,
        aspectY: Float,
    ): Intent {
        val dir = File(context.cacheDir, cacheSubdir).apply { mkdirs() }

        val localSource = File.createTempFile("pick_in_", ".jpg", dir)
        if (!normalizePickedImageToJpeg(context, sourceUri, localSource)) {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                localSource.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not read picked image")
        }

        val sourceLocalUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localSource,
            )

        val destFile = File.createTempFile("crop_out_", ".jpg", dir)
        val destUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile,
            )
        val options =
            UCrop.Options().apply {
                setCompressionQuality(88)
                setHideBottomControls(false)
                setMaxBitmapSize(maxSourceSidePx)
            }
        return UCrop.of(sourceLocalUri, destUri)
            .withAspectRatio(aspectX, aspectY)
            .withOptions(options)
            .getIntent(context)
            .also { intent ->
                intent.setClass(context, OttoUCropActivity::class.java)
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
    }

    fun parseOutputUri(
        resultCode: Int,
        data: Intent?,
    ): Uri? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return UCrop.getOutput(data)
    }

    /**
     * Writes a display-oriented RGB JPEG to [destJpeg].
     * @return false if normalization failed (caller may fall back to raw copy).
     */
    private fun normalizePickedImageToJpeg(
        context: Context,
        sourceUri: Uri,
        destJpeg: File,
    ): Boolean {
        val dir = destJpeg.parentFile ?: return false
        val rawFile = File.createTempFile("pick_raw_", ".dat", dir)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                rawFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            val exif = ExifInterface(rawFile)
            val decoded = decodeForNormalize(rawFile) ?: return false
            val bitmap =
                if (decoded.applyExifTransform) {
                    applyExifOrientation(decoded.bitmap, exif)
                } else {
                    decoded.bitmap
                }
            var ok = false
            try {
                FileOutputStream(destJpeg).use { out ->
                    ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
            } finally {
                bitmap.recycle()
            }
            ok
        } catch (_: Throwable) {
            false
        } finally {
            rawFile.delete()
        }
    }

    private fun decodeForNormalize(file: File): DecodedBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(file)?.let { DecodedBitmap(it, applyExifTransform = false) }
            } else {
                null
            }
        }
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSourceSidePx) {
            sample *= 2
        }
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.let {
            return DecodedBitmap(it, applyExifTransform = true)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(file)?.let { DecodedBitmap(it, applyExifTransform = false) }
        } else {
            null
        }
    }

    private fun decodeWithImageDecoder(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val w = info.size.width
                val h = info.size.height
                val longEdge = max(w, h)
                if (longEdge > maxSourceSidePx) {
                    val scale = maxSourceSidePx.toFloat() / longEdge
                    val tw = max(1, (w * scale).roundToInt())
                    val th = max(1, (h * scale).roundToInt())
                    decoder.setTargetSize(tw, th)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyExifOrientation(
        bitmap: Bitmap,
        exif: ExifInterface,
    ): Bitmap {
        val matrix = exifTransformationMatrix(exif)
        if (matrix.isIdentity) return bitmap
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    private fun exifTransformationMatrix(exif: ExifInterface): Matrix {
        val orientation =
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
        }
        return m
    }
}
