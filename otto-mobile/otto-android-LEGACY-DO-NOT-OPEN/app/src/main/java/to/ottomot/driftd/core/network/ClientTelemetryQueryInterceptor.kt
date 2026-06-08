package to.ottomot.driftd.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Appends `app_platform` and `app_version` to every HTTP request URL so backend access logs
 * can attribute traffic. Query names match the iOS API client.
 */
class ClientTelemetryQueryInterceptor(
    private val platform: String,
    private val appVersion: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val nextUrl =
            original.url.newBuilder().apply {
                replaceQueryParameter(APP_PLATFORM_KEY, platform)
                replaceQueryParameter(APP_VERSION_KEY, appVersion)
            }.build()
        return chain.proceed(original.newBuilder().url(nextUrl).build())
    }

    private fun okhttp3.HttpUrl.Builder.replaceQueryParameter(name: String, value: String) {
        removeAllQueryParameters(name)
        addQueryParameter(name, value)
    }

    companion object {
        const val APP_PLATFORM_KEY: String = "app_platform"
        const val APP_VERSION_KEY: String = "app_version"
    }
}
