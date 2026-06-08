package to.ottomot.driftd.core.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import to.ottomot.driftd.core.network.dto.CircleChatMentionSpanDto

/** Must match `CIRCLE_CHAT_ALL_MENTION_USER_ID` in `otto-backend/src/constants/chatMentions.js`. */
object SquadChatAllMention {
    const val USER_ID: String = "0000000000000000000000a1"

    /** Lowercase token after `@`; must match backend validation for the sentinel id. */
    const val WIRE_LABEL: String = "all"
}

/**
 * Parses `@DisplayName` segments in [body] using UTF-16 indices (matches JS/Java `String.length`).
 * [Longest][displayNameByUserId] match wins at each `@` (duplicate display names: first map entry wins for ties).
 */
fun parseSquadMentionSpansUtf16(
    body: String,
    squadMemberIds: Set<String>,
    displayNameByUserId: Map<String, String>,
): List<CircleChatMentionSpanDto> {
    if (body.isEmpty() || squadMemberIds.isEmpty() || displayNameByUserId.isEmpty()) {
        return emptyList()
    }
    val out = mutableListOf<CircleChatMentionSpanDto>()
    var i = 0
    while (i < body.length) {
        if (body[i] != '@') {
            i++
            continue
        }
        val after = i + 1
        if (after >= body.length) {
            break
        }
        var bestUid: String? = null
        var bestLen = 0
        val ordered =
            displayNameByUserId.entries.sortedWith(
                compareBy({ it.key != SquadChatAllMention.USER_ID }, { it.key }),
            )
        for ((uid, rawName) in ordered) {
            if (!squadMemberIds.contains(uid) || rawName.isEmpty()) continue
            if (
                after + rawName.length <= body.length &&
                body.regionMatches(after, rawName, 0, rawName.length, ignoreCase = false)
            ) {
                if (rawName.length > bestLen) {
                    bestLen = rawName.length
                    bestUid = uid
                }
            }
        }
        if (bestUid != null && bestLen > 0) {
            val totalLen = 1 + bestLen
            out.add(CircleChatMentionSpanDto(userId = bestUid, start = i, length = totalLen))
            i += totalLen
        } else {
            i++
        }
    }
    return out
}

fun buildMentionStyledChatBody(
    body: String,
    mentions: List<CircleChatMentionSpanDto>,
    displayNameByUserId: Map<String, String>,
    baseColor: Color,
    mentionColor: Color,
): AnnotatedString {
    if (mentions.isEmpty() || body.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) {
                append(body)
            }
        }
    }
    val sorted =
        mentions
            .filter { it.length > 0 && it.start >= 0 && it.start + it.length <= body.length }
            .sortedBy { it.start }
    if (sorted.isEmpty()) return AnnotatedString(body)

    return buildAnnotatedString {
        var cursor = 0
        for (m in sorted) {
            if (m.start < cursor) continue
            if (m.start > cursor) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(body.substring(cursor, m.start))
                }
            }
            val label =
                displayNameByUserId[m.userId]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: body.substring(m.start + 1, m.start + m.length).trimStart()
            withStyle(
                SpanStyle(
                    color = mentionColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append("@")
                append(label)
            }
            cursor = m.start + m.length
        }
        if (cursor < body.length) {
            withStyle(SpanStyle(color = baseColor)) {
                append(body.substring(cursor))
            }
        }
    }
}
