package com.github.premnirmal.ticker.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate of the 保存復元 automation contract — a switch that is ON, and a token that is OFF.
 *
 * ## What v2 changed, and why it had to
 *
 * v1 shipped every app closed: the switch defaulted to false and a caller also had to present a
 * secret 白い熊 had pasted from this app's settings into the caller's. **A pasted secret cannot
 * survive a wipe**, and the case this family now exists to serve is 応用管理 restoring apps *and
 * their data* onto a clean phone, where nothing has been configured and nobody has pasted anything.
 * A gate that only works once the phone is already set up is no gate for setting the phone up.
 *
 * So the switch defaults ON, the token is opt-in, and the identity check that actually matters
 * moved to the data door, which can see who is calling
 * ([com.github.premnirmal.ticker.automation.AutomationCallers]).
 *
 * Switch, flag and token live in the device-local Export/Import prefs file ([SettingsExport.EXIM_PREFS]),
 * which is never part of an export ZIP — so the token can never travel inside a backup.
 *
 * ## Why every write here is `commit()`
 *
 * **This gate now fails OPEN.** With the default flipped to true, a write that never reaches disk
 * does not fall back to "off" — it falls back to ON. And 応用管理 force-stops an app the instant it
 * replies to an import, with `Process.killProcess`: a `SIGKILL`, which leaves an in-flight `apply()`
 * nowhere to land. Turning an app off is the one action 白い熊 has for shutting a sister app out,
 * and it is the action most likely to be running near a force-stop, so a lost `setEnabled(false)`
 * would silently reopen the door. A lost token is the same shape from the other side: 白い熊 may
 * already have pasted the value into a caller, and nothing surfaces the mismatch — the caller simply
 * begins failing `ERROR:bad token`. All three writes are tiny and infrequent, so synchronous costs
 * nothing anyone waits on.
 */
object AutomationAuth {

    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24
    private const val ABBREV_EDGE = 8
    private const val HEX = "0123456789abcdef"
    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_MASK = 0x0F
    private const val NIBBLE_BITS = 4

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsExport.EXIM_PREFS, Context.MODE_PRIVATE)

    /**
     * The whole gate, in one function — `null` to proceed, otherwise the exact `ERROR:` line to
     * answer with.
     *
     * One place deliberately: two checks written out at each entry point is how "disabled" and
     * "bad token" drift apart across forty-two apps. The two stay distinct in the reply because
     * they debug differently.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !isEnabled(context) -> "ERROR:automation disabled"
        isTokenRequired(context) && !matches(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /**
     * **Default true.** Every app answers automation out of the box; the switch remains only so
     * 白い熊 can shut one app out, because a feature that can be turned on but never off is one
     * he cannot retreat from.
     */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    @SuppressLint("ApplySharedPref")
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    /**
     * **Default false.** A token is an extra a caller may be asked for, not the gate.
     *
     * When this is off a token that arrives anyway is **ignored, never refused** — see [refuse].
     */
    fun isTokenRequired(context: Context): Boolean = prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    @SuppressLint("ApplySharedPref")
    fun setTokenRequired(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, required).commit()
    }

    /** The token, generated lazily on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: regenerate(context)

    /** Mints a fresh token — every copy already pasted into a sister app has to be updated. */
    @SuppressLint("ApplySharedPref")
    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { byte ->
            val v = byte.toInt() and BYTE_MASK
            "${HEX[v ushr NIBBLE_BITS]}${HEX[v and NIBBLE_MASK]}"
        }
        prefs(context).edit().putString(KEY_TOKEN, token).commit()
        return token
    }

    /**
     * Constant-time comparison — plain `==` would leak the matching prefix through timing.
     *
     * Only ever consulted when [isTokenRequired] is on; a token sent to an app that does not want
     * one never reaches here.
     */
    fun matches(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows in place of the full token. */
    fun abbreviate(token: String): String =
        if (token.length <= ABBREV_EDGE * 2) token else token.take(ABBREV_EDGE) + "…" + token.takeLast(ABBREV_EDGE)
}
