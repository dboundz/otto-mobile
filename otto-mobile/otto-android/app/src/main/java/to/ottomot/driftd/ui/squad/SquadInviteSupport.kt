package to.ottomot.driftd.ui.squad

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.ottoUserIdsEqual
import java.util.Locale

/** Public web invite base when API returns token without url (backend `buildInviteUrl` default). */
private const val PUBLIC_INVITE_WEB_BASE = "https://driftd.com"

/**
 * Resolves a shareable squad invite URL from API fields.
 * Prefers explicit [url]; falls back to `https://driftd.com/invite/{token}`.
 */
fun resolveShareInviteUrl(
    url: String?,
    token: String?,
): String? {
    url?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val code = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return "$PUBLIC_INVITE_WEB_BASE/invite/${Uri.encode(code)}"
}

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

suspend fun copyInviteLinkToClipboard(context: Context, url: String) {
    withContext(Dispatchers.Main.immediate) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@withContext
        val clipContext = context.findActivity() ?: context
        val clipboard =
            clipContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("squad_invite", trimmed))
    }
}

/** Light tap haptic for squad invite actions (iOS `SquadInviteSheetHaptics` parity). */
object SquadInviteHaptics {
    fun buttonTap(haptic: HapticFeedback, view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/** Phone-only lookup path; any letters → name search (iOS `isPhonePrimaryInviteQuery`). */
fun isPhonePrimarySquadInviteQuery(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return false
    if (t.any { it.isLetter() }) return false
    return isValidNorthAmericanPhoneNumber(t)
}

fun isValidNorthAmericanPhoneNumber(raw: String): Boolean {
    val digits = raw.filter { it.isDigit() }
    val normalized =
        when {
            digits.length == 11 && digits.first() == '1' -> digits.drop(1)
            digits.length == 10 -> digits
            else -> return false
        }
    if (normalized.length != 10) return false
    val chars = normalized.toCharArray()
    if (chars[0] !in '2'..'9') return false
    if (chars[3] !in '2'..'9') return false
    return true
}

fun normalizedSmsRecipientFromPhone(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length == 10 -> digits
        digits.length == 11 && digits.first() == '1' -> digits.drop(1)
        else -> null
    }
}

fun squadInviteSmsBody(url: String): String = "Join my squad on Driftd: $url"

fun smsInviteLinkCacheKey(circleId: String, phone: String): String =
    "${circleId.trim()}|${phone.trim()}"

fun inviteNameSearchFromContacts(
    contacts: List<UserDto>,
    query: String,
    myUserId: String?,
    memberUserIds: Set<String>,
): List<UserDto> {
    val q = query.trim()
    if (q.length < 2 || isPhonePrimarySquadInviteQuery(q)) return emptyList()
    val meId = myUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val digitsQuery = q.filter { it.isDigit() }
    return contacts
        .filter { user ->
            val uid = user.id.trim()
            if (ottoUserIdsEqual(uid, meId) || memberUserIds.any { ottoUserIdsEqual(it, uid) }) {
                return@filter false
            }
            val nameMatch =
                user.displayName.contains(q, ignoreCase = true)
            val phoneMatch =
                digitsQuery.isNotEmpty() &&
                    (user.phoneNumber?.filter { it.isDigit() }?.contains(digitsQuery) == true)
            nameMatch || phoneMatch
        }
        .sortedBy { it.displayName.lowercase(Locale.US) }
}

/**
 * Opens the system SMS composer with a pre-filled body.
 * @return false if no app could handle the intent.
 */
fun openSquadInviteSms(
    context: Context,
    body: String,
    recipientDigits: String? = null,
): Boolean {
    val trimmedBody = body.trim()
    if (trimmedBody.isEmpty()) return false
    val smsUri =
        if (!recipientDigits.isNullOrBlank()) {
            Uri.parse("smsto:$recipientDigits")
        } else {
            Uri.parse("sms:")
        }
    val launchContext = context.findActivity() ?: context
    val sendIntent =
        Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", trimmedBody)
            putExtra(Intent.EXTRA_TEXT, trimmedBody)
            if (launchContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    return try {
        launchContext.startActivity(Intent.createChooser(sendIntent, null))
        true
    } catch (_: ActivityNotFoundException) {
        try {
            val encoded = Uri.encode(trimmedBody)
            val fallbackUri =
                if (!recipientDigits.isNullOrBlank()) {
                    Uri.parse("smsto:$recipientDigits?body=$encoded")
                } else {
                    Uri.parse("sms:?body=$encoded")
                }
            val viewIntent =
                Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    if (launchContext !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            launchContext.startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    } catch (_: Exception) {
        false
    }
}
