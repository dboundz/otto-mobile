package to.ottomot.driftd.core.network

import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response

class AuthHeaderInterceptor(
    private val authToken: StateFlow<String?>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token =
            authToken.value
                ?.trim()
                .orEmpty()

        val request =
            if (token.isEmpty()) {
                chain.request()
            } else {
                chain.request()
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }

        return chain.proceed(request)
    }
}
