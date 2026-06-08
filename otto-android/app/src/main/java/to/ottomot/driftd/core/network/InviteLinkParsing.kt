package to.ottomot.driftd.core.network

import android.net.Uri

/**
 * Normalizes invite input to a bare code (matches iOS-style URL handling).
 * Accepts `driftd.com/invite/{code}`, legacy `/invite-links/{hex}`, or a raw code/token.
 */
object InviteLinkParsing {
    private val legacyHexPathRegex = Regex("""/invite-links/([a-fA-F0-9]{8,})""")
    private val personalInvitePathRegex = Regex("""/invite/([^/?#]+)""")

    fun normalizeInviteToken(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return t
        parseInviteDeepLink(t)?.first?.let { return it }
        legacyHexPathRegex.find(t)?.groupValues?.get(1)?.let { return it }
        return t
    }

    /** Returns invite code/token and optional squad id from a deep link URI string. */
    fun parseInviteDeepLink(raw: String): Pair<String, String?>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val uri =
            runCatching { Uri.parse(trimmed) }.getOrNull()
                ?: return legacyHexPathRegex.find(trimmed)?.let { it.groupValues[1] to null }

        val path = uri.path.orEmpty()
        legacyHexPathRegex.find(path)?.groupValues?.get(1)?.let { token ->
            return token to squadQuery(uri)
        }
        personalInvitePathRegex.find(path)?.groupValues?.get(1)?.let { code ->
            val decoded = Uri.decode(code).trim()
            if (decoded.isNotEmpty()) return decoded to squadQuery(uri)
        }

        if (!trimmed.contains("/")) {
            return trimmed to null
        }
        return null
    }

    private fun squadQuery(uri: Uri): String? {
        return uri.getQueryParameter("squad")?.trim()?.takeIf { it.isNotEmpty() }
            ?: uri.getQueryParameter("circleId")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
