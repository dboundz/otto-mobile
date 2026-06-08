package to.ottomot.driftd.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import to.ottomot.driftd.core.config.OttoEndpoints

/**
 * Builds a fetchable URL for images/media from API strings: absolute URLs, protocol-relative URLs,
 * root-relative paths, and localhost-style URLs remapped to the API authority (same intent as Swift
 * `APIConfig.imageFetchURL`).
 */
object MediaUrlResolver {
    fun resolve(
        raw: String?,
        apiBaseUrl: String = OttoEndpoints.httpBaseUrl,
    ): okhttp3.HttpUrl? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        val baseParsed = apiBaseUrl.toHttpUrlOrNull() ?: return null

        if (trimmed.startsWith("//")) {
            return "https:$trimmed".toHttpUrlOrNull()
        }

        if (trimmed.startsWith('/')) {
            return baseParsed.newBuilder().encodedPath(trimmed).build()
        }

        val url =
            trimmed.toHttpUrlOrNull()
                ?: return null

        if (url.scheme != "http" && url.scheme != "https") {
            return null
        }

        val host = url.host.lowercase()
        val isLoopback =
            host == "localhost" ||
                host == "127.0.0.1" ||
                host == "::1"

        if (!isLoopback) return url

        return baseParsed.newBuilder()
            .encodedPath(url.encodedPath)
            .encodedQuery(url.encodedQuery)
            .encodedFragment(url.encodedFragment)
            .build()
    }
}
