package to.ottomot.driftd.core.network

import org.json.JSONObject
import retrofit2.HttpException

fun Throwable.userVisibleHttpMessage(default: String = "Something went wrong."): String {
    val he = this as? HttpException
    val fromBody =
        he
            ?.response()
            ?.errorBody()
            ?.use { body ->
                runCatching {
                    val json = JSONObject(body.string())
                    json.optString("error").trim().takeIf { it.isNotEmpty() }
                        ?: json.optJSONArray("error")?.let { arr ->
                            (0 until arr.length())
                                .mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
                                .joinToString(" ")
                                .takeIf { it.isNotEmpty() }
                        }
                }.getOrNull()
            }
    if (fromBody != null && !fromBody.looksLikeInternalError()) return fromBody
    val trimmed = message?.trim()?.takeIf { it.isNotEmpty() }
    trimmed?.takeUnless { it.looksLikeInternalError() }?.let { return it }
    return he?.let { "Request failed (${it.code()})." } ?: default
}

private fun String.looksLikeInternalError(): Boolean {
    val t = trim()
    if (t.isEmpty()) return true
    if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
        return true
    }
    val lower = t.lowercase()
    return lower.contains("mongo") ||
        lower.contains("e11000") ||
        lower.contains("duplicate key") ||
        lower.contains("objectid") ||
        lower.contains("internal server")
}
