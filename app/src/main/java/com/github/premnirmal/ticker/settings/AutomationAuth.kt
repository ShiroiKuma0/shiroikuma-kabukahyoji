package com.github.premnirmal.ticker.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate of the 保存復元 automation contract: sister apps (白い熊 自由作業盤 above all) trigger this
 * app's headless export with a broadcast carrying this token, and nothing happens unless the master
 * switch is on *and* the token matches.
 *
 * Switch and token live in the device-local Export/Import prefs file ([SettingsExport.EXIM_PREFS]),
 * which is never part of an export ZIP — so the token can never travel inside a backup.
 */
object AutomationAuth {

    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24
    private const val ABBREV_EDGE = 8
    private const val HEX = "0123456789abcdef"
    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_MASK = 0x0F
    private const val NIBBLE_BITS = 4

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsExport.EXIM_PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /** The token, generated lazily on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: regenerate(context)

    /** Mints a fresh token — every copy already pasted into a sister app has to be updated. */
    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { byte ->
            val v = byte.toInt() and BYTE_MASK
            "${HEX[v ushr NIBBLE_BITS]}${HEX[v and NIBBLE_MASK]}"
        }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /** Constant-time comparison — plain `==` would leak the matching prefix through timing. */
    fun matches(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows in place of the full token. */
    fun abbreviate(token: String): String =
        if (token.length <= ABBREV_EDGE * 2) token else token.take(ABBREV_EDGE) + "…" + token.takeLast(ABBREV_EDGE)
}
