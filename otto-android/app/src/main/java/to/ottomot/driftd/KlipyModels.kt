package to.ottomot.driftd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class KlipyGifSelection(
    val slug: String,
    val title: String,
    val previewUrl: String,
    val sendUrl: String,
    val width: Int,
    val height: Int,
)

data class KlipyGifItem(
    val id: Long,
    val slug: String,
    val title: String,
    val previewUrl: String,
    val sendUrl: String,
    val width: Int,
    val height: Int,
) {
    val selection: KlipyGifSelection
        get() =
            KlipyGifSelection(
                slug = slug,
                title = title,
                previewUrl = previewUrl,
                sendUrl = sendUrl,
                width = width,
                height = height,
            )
}

data class KlipyGifListPage(
    val items: List<KlipyGifItem>,
    val hasMore: Boolean,
)

enum class KlipyConfiguration {
    ;

    companion object {
        val appKey: String
            get() = BuildConfig.KLIPY_APP_KEY.trim()

        val isConfigured: Boolean
            get() = appKey.isNotEmpty()
    }
}

class KlipyApiException(message: String) : IOException(message)

object KlipyAPIClient {
    private val baseUrl = "https://api.klipy.com".toHttpUrl()
    private val jsonMediaType = "application/json".toMediaType()
    private val defaultHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()

    suspend fun fetchTrending(
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int = 24,
        httpClient: OkHttpClient = defaultHttpClient,
    ): KlipyGifListPage =
        fetchList(
            pathComponents = listOf("api", "v1", KlipyConfiguration.appKey, "gifs", "trending"),
            queryParams =
                listQueryParams(
                    customerId = customerId,
                    locale = locale,
                    page = page,
                    perPage = perPage,
                    searchQuery = null,
                ),
            httpClient = httpClient,
        )

    suspend fun search(
        query: String,
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int = 24,
        httpClient: OkHttpClient = defaultHttpClient,
    ): KlipyGifListPage {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return fetchTrending(customerId = customerId, locale = locale, page = page, perPage = perPage, httpClient = httpClient)
        }
        return fetchList(
            pathComponents = listOf("api", "v1", KlipyConfiguration.appKey, "gifs", "search"),
            queryParams =
                listQueryParams(
                    customerId = customerId,
                    locale = locale,
                    page = page,
                    perPage = perPage,
                    searchQuery = trimmed,
                ),
            httpClient = httpClient,
        )
    }

    suspend fun reportShare(
        slug: String,
        customerId: String,
        searchQuery: String?,
        httpClient: OkHttpClient = defaultHttpClient,
    ) {
        if (!KlipyConfiguration.isConfigured) return
        val cleanSlug = slug.trim()
        if (cleanSlug.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                var builder = baseUrl.newBuilder()
                listOf("api", "v1", KlipyConfiguration.appKey, "gifs", "share", cleanSlug).forEach {
                    builder = builder.addPathSegment(it)
                }
                val bodyJson =
                    JsonObject().apply {
                        addProperty("customer_id", customerId.ifBlank { "otto-anonymous" })
                        addProperty("q", searchQuery?.trim().orEmpty())
                    }
                val request =
                    Request
                        .Builder()
                        .url(builder.build())
                        .header("Content-Type", "application/json")
                        .post(bodyJson.toString().toRequestBody(jsonMediaType))
                        .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    fun defaultLocale(): String = Locale.getDefault().country.lowercase().ifBlank { "us" }

    internal fun parseListResponse(body: String): KlipyGifListPage {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("result")?.asBoolean != true) {
            throw KlipyApiException("GIF search failed. Try again.")
        }
        val payload = root.getAsJsonObject("data") ?: throw KlipyApiException("Couldn't load GIFs. Try again.")
        val records = payload.getAsJsonArray("data")
        val items =
            records
                ?.mapNotNull { element -> parseItem(element.asJsonObject) }
                .orEmpty()
        val currentPage = payload.get("current_page")?.asInt ?: 1
        if (items.isEmpty() && currentPage == 1) {
            throw KlipyApiException("No GIFs found.")
        }
        val perPage = payload.get("per_page")?.asInt ?: 24
        val hasMore = payload.get("has_next")?.asBoolean ?: (items.size >= perPage)
        return KlipyGifListPage(items = items, hasMore = hasMore)
    }

    private suspend fun fetchList(
        pathComponents: List<String>,
        queryParams: List<Pair<String, String>>,
        httpClient: OkHttpClient,
    ): KlipyGifListPage {
        if (!KlipyConfiguration.isConfigured) {
            throw KlipyApiException("GIF search isn't available right now.")
        }
        return withContext(Dispatchers.IO) {
            var builder = baseUrl.newBuilder()
            pathComponents.forEach { builder = builder.addPathSegment(it) }
            queryParams.forEach { (name, value) -> builder = builder.addQueryParameter(name, value) }
            val request =
                Request
                    .Builder()
                    .url(builder.build())
                    .header("Accept", "application/json")
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw KlipyApiException("GIF search failed. Try again.")
                }
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    throw KlipyApiException("Couldn't load GIFs. Try again.")
                }
                parseListResponse(responseBody)
            }
        }
    }

    private fun listQueryParams(
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int,
        searchQuery: String?,
    ): List<Pair<String, String>> =
        buildList {
            add("page" to maxOf(1, page).toString())
            add("per_page" to perPage.coerceIn(1, 50).toString())
            add("customer_id" to customerId.ifBlank { "otto-anonymous" })
            add("locale" to locale.ifBlank { "us" })
            add("content_filter" to "medium")
            add("format_filter" to "gif,webp")
            if (searchQuery != null) {
                add("q" to searchQuery)
            }
        }

    private fun parseItem(record: JsonObject): KlipyGifItem? {
        val slug = record.get("slug")?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val file = record.getAsJsonObject("file")
        val previewAsset =
            file?.asset("sm", "gif")
                ?: file?.asset("sm", "webp")
                ?: file?.asset("md", "gif")
                ?: file?.asset("md", "webp")
        val sendAsset =
            file?.asset("md", "gif")
                ?: file?.asset("md", "webp")
                ?: file?.asset("hd", "gif")
                ?: file?.asset("hd", "webp")
                ?: previewAsset
        val previewUrl = previewAsset?.url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val sendUrl = sendAsset?.url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return KlipyGifItem(
            id = record.get("id")?.asLong ?: slug.hashCode().toLong(),
            slug = slug,
            title = record.get("title")?.asString?.trim().orEmpty(),
            previewUrl = previewUrl,
            sendUrl = sendUrl,
            width = sendAsset.width ?: previewAsset.width ?: 200,
            height = sendAsset.height ?: previewAsset.height ?: 200,
        )
    }

    private data class KlipyAsset(
        val url: String?,
        val width: Int?,
        val height: Int?,
    )

    private fun JsonObject.asset(size: String, format: String): KlipyAsset? {
        val asset = getAsJsonObject(size)?.getAsJsonObject(format) ?: return null
        return KlipyAsset(
            url = asset.get("url")?.asString,
            width = asset.get("width")?.asInt,
            height = asset.get("height")?.asInt,
        )
    }
}
