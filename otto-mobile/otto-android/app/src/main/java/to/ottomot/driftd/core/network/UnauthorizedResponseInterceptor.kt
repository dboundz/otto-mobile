package to.ottomot.driftd.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import to.ottomot.driftd.core.session.SessionRepository

/**
 * When the server rejects our bearer token with 401, clear local credentials so [to.ottomot.driftd.OttoRoot]
 * returns to sign-in. Skips unauthenticated calls (no Authorization / empty bearer).
 */
class UnauthorizedResponseInterceptor(
    private val sessionRepository: SessionRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 401) return response
        if (!hadNonEmptyBearer(chain.request())) return response
        sessionRepository.clearCredentialsAsync()
        return response
    }
}

private fun hadNonEmptyBearer(request: Request): Boolean {
    val v = request.header("Authorization")?.trim().orEmpty()
    if (!v.startsWith("Bearer ", ignoreCase = true)) return false
    return v.length > 7 && v.substring(7).isNotBlank()
}
