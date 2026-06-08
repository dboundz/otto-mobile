package to.ottomot.driftd.core.media

import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

object ChatVideoS3Uploader {
    private val client = OkHttpClient()

    fun putBytes(
        uploadUrl: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: (Float) -> Unit,
    ) {
        val body =
            object : RequestBody() {
                override fun contentType() = contentType.toMediaType()

                override fun contentLength() = bytes.size.toLong()

                override fun writeTo(sink: BufferedSink) {
                    var written = 0L
                    val chunk = 8192
                    var offset = 0
                    while (offset < bytes.size) {
                        val toWrite = minOf(chunk, bytes.size - offset)
                        sink.write(bytes, offset, toWrite)
                        offset += toWrite
                        written += toWrite
                        onProgress((written.toFloat() / bytes.size.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        put(uploadUrl, body)
    }

    fun putFile(
        uploadUrl: String,
        file: File,
        contentType: String,
        onProgress: (Float) -> Unit,
    ) {
        val total = file.length().coerceAtLeast(1L)
        val body =
            object : RequestBody() {
                override fun contentType() = contentType.toMediaType()

                override fun contentLength() = file.length()

                override fun writeTo(sink: BufferedSink) {
                    file.source().use { source ->
                        var written = 0L
                        val buffer = okio.Buffer()
                        while (true) {
                            val read = source.read(buffer, 8192)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            written += read
                            onProgress((written.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        put(uploadUrl, body)
    }

    private fun put(uploadUrl: String, body: RequestBody) {
        val request =
            Request.Builder()
                .url(uploadUrl)
                .put(body)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Upload failed (${response.code}).")
        }
    }
}
