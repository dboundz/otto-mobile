package to.ottomot.driftd

import android.content.Context
import android.net.Uri
import to.ottomot.driftd.core.network.InviteLinkParsing

/** Persists squad invite deep links across process death and the auth gate. */
object PendingSquadInviteStore {
    private const val PREFS = "otto.pending_squad_invite"
    private const val KEY_CODE = "code"
    private const val KEY_SQUAD_ID = "squad_id"

    data class Pending(val code: String, val squadId: String?)

    fun persist(context: Context, uri: Uri) {
        val parsed = InviteLinkParsing.parseInviteDeepLink(uri.toString()) ?: return
        persist(context, parsed.first, parsed.second)
    }

    fun persist(context: Context, code: String, squadId: String?) {
        val trimmedCode = code.trim()
        if (trimmedCode.isEmpty()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, trimmedCode)
            .apply {
                val squad = squadId?.trim()?.takeIf { it.isNotEmpty() }
                if (squad != null) putString(KEY_SQUAD_ID, squad) else remove(KEY_SQUAD_ID)
            }
            .apply()
    }

    fun load(context: Context): Pending? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null)?.trim().orEmpty()
        if (code.isEmpty()) return null
        val squadId = prefs.getString(KEY_SQUAD_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
        return Pending(code = code, squadId = squadId)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
