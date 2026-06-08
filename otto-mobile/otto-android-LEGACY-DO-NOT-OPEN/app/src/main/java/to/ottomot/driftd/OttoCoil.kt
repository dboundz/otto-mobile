package to.ottomot.driftd

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Default [ImageRequest] for thumbnails, banners, chat images, etc. Applies explicit cache
 * policies so feed media reuses Coil memory + disk even when callers forget to opt in.
 *
 * For http(s) strings, Coil's default keys use the literal URL — signed/query-token URLs that
 * change every refresh break the cache even though bytes are unchanged. We key memory + disk
 * cache by **[scheme][host][port][path]** (query + fragment stripped) so squad avatars and similar
 * media stay warm across list scroll and app restarts (global `ImageLoader` already enables disk LRU).
 */
fun ottoImageRequest(
    context: Context,
    data: Any?,
): ImageRequest {
    val builder =
        ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)

    val key = (data as? String)?.takeIf { it.isNotBlank() }?.let { stableHttpImageCacheKey(it) }
    if (!key.isNullOrBlank()) {
        builder.memoryCacheKey(key)
        builder.diskCacheKey(key)
    }

    return builder.build()
}

/**
 * Coil cache key aligned to the fetched resource identity, omitting volatile query (e.g. auth
 * tokens) and fragments that often rotate while the backing object is unchanged.
 */
internal fun stableHttpImageCacheKey(fullUrl: String): String {
    val http =
        fullUrl.trim().toHttpUrlOrNull()
            ?: return fullUrl.trim()
    return http
        .newBuilder()
        .host(http.host.lowercase(Locale.ROOT))
        .query(null)
        .fragment(null)
        .build()
        .toString()
}
