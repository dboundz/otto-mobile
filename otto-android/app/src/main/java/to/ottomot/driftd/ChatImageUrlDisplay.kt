package to.ottomot.driftd

import androidx.annotation.StringRes

private val chatDirectImageExtensions = setOf("gif", "webp", "png", "jpg", "jpeg")

/** iOS parity: [`ChatImageURLDisplay`](mobile/otto-mobile/otto-mobile/ChatOutgoingImageURLNormalizer.swift). */
object ChatImageUrlDisplay {

    fun isAnimatedImageUrl(urlString: String?): Boolean {
        val raw = urlString?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val lower = raw.lowercase()
        if (lower.contains("static.klipy.com")) return true
        val path =
            runCatching {
                java.net.URI(raw).path?.lowercase().orEmpty()
            }.getOrDefault(lower)
        if (path.endsWith(".gif") || path.contains(".gif?")) return true
        if (path.endsWith(".webp") || path.contains(".webp?")) return true
        return false
    }

    @StringRes
    fun replySnippetResId(imageUrl: String?): Int =
        if (isAnimatedImageUrl(imageUrl)) {
            R.string.chat_reply_gif
        } else {
            R.string.chat_reply_photo
        }
}

data class ChatOutgoingImagePayload(
    val body: String,
    val imageUrl: String?,
    val klipyShare: KlipyShareContext? = null,
)

data class KlipyShareContext(
    val slug: String,
    val searchQuery: String?,
)

object ChatOutgoingImageUrlNormalizer {
    fun normalize(
        draft: String,
        pendingAttachment: ChatPendingComposerAttachment? = null,
    ): ChatOutgoingImagePayload {
        if (pendingAttachment?.kind == ChatPendingComposerAttachmentKind.KlipyGif) {
            val selection = pendingAttachment.klipyGif
            val sendUrl = selection?.sendUrl?.trim().orEmpty()
            if (sendUrl.isNotEmpty()) {
                return ChatOutgoingImagePayload(
                    body = draft.trim(),
                    imageUrl = sendUrl,
                    klipyShare =
                        KlipyShareContext(
                            slug = selection?.slug.orEmpty(),
                            searchQuery = pendingAttachment.klipySearchQuery,
                        ),
                )
            }
        }
        promoteDirectImageUrl(draft.trim())?.let { promoted ->
            return ChatOutgoingImagePayload(body = promoted.body, imageUrl = promoted.imageUrl)
        }
        return ChatOutgoingImagePayload(body = draft.trim(), imageUrl = null)
    }

    private data class Promoted(val body: String, val imageUrl: String)

    private fun promoteDirectImageUrl(trimmed: String): Promoted? {
        if (trimmed.isEmpty()) return null
        val url = extractSingleImageUrl(trimmed) ?: return null
        if (!isDirectImageUrl(url)) return null
        var body = trimmed.replace(url, "").trim()
        return Promoted(body = body, imageUrl = url)
    }

    private fun extractSingleImageUrl(text: String): String? {
        val pattern =
            Regex(
                pattern = """https?://[^\s]+""",
                option = RegexOption.IGNORE_CASE,
            )
        val matches = pattern.findAll(text).toList()
        if (matches.size != 1) return null
        val url = matches.first().value.trimEnd('.', ',', ';', ')', ']')
        val before = text.substring(0, matches.first().range.first).trim()
        val after = text.substring(matches.first().range.last + 1).trim()
        val extra = (before + after).trim()
        if (extra.contains("http://", ignoreCase = true) || extra.contains("https://", ignoreCase = true)) {
            return null
        }
        return url
    }

    private fun isDirectImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val path =
            runCatching {
                java.net.URI(url).path?.lowercase().orEmpty()
            }.getOrDefault(lower)
        return chatDirectImageExtensions.any { ext ->
            path.endsWith(".$ext") || path.contains(".$ext?")
        }
    }
}
