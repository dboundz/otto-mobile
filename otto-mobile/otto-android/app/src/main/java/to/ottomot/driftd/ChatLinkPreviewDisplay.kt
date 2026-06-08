package to.ottomot.driftd

import android.net.Uri
import to.ottomot.driftd.core.network.dto.CircleChatLinkPreviewDto

/** Shared link-preview thumbnail sizing and Instagram detection for chat cards. */
object ChatLinkPreviewDisplay {
    /** Default OG thumbnail height (wide-short frame for generic links). */
    val defaultThumbnailHeightDp = 148f

    /** Instagram feed portrait width:height. */
    const val portraitAspectRatio = 4f / 5f

    fun isInstagramStyleLink(url: String?, siteName: String?): Boolean {
        val site = siteName?.trim().orEmpty()
        if (site.contains("instagram", ignoreCase = true)) return true
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        return isInstagramPublicUrl(raw)
    }

    fun usesPortraitThumbnail(preview: CircleChatLinkPreviewDto): Boolean {
        val url = preview.finalUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: preview.url?.trim()?.takeIf { it.isNotEmpty() }
        return isInstagramStyleLink(url, preview.siteName)
    }

    private fun isInstagramPublicUrl(rawUrl: String): Boolean {
        val uri =
            runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.replace(Regex("^www\\."), "")?.lowercase() ?: return false
        if (host != "instagram.com") return false
        return parseInstagramPublicPath(uri.path.orEmpty()) != null
    }

    /** Matches backend `parseInstagramPublicPath` — post, reel, reels, or IGTV. */
    private fun parseInstagramPublicPath(pathname: String): Pair<String, String>? {
        val match =
            Regex("""^/(p|reel|reels|tv)/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)
                .find(pathname) ?: return null
        val kind = match.groupValues[1].lowercase()
        val shortcode = match.groupValues[2]
        return kind to shortcode
    }
}
