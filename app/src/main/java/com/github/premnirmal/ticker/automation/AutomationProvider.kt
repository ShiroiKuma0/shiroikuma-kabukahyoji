package com.github.premnirmal.ticker.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.github.premnirmal.ticker.settings.AutomationAuth
import com.github.premnirmal.ticker.settings.SettingsExport

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the v2 redesign.
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which
 * cannot survive the wipe this feature exists to recover from. A provider gets the caller's
 * identity from the framework for free — see [AutomationCallers] for what is actually checked, and
 * why a `shiroikuma.*` prefix would have been strictly *weaker* than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns. The bytes go through a
 * file descriptor the caller opened, and the terminal answer comes back on the broadcast the family
 * already proved on EMUI.
 *
 * ## `import` lives here and nowhere else
 *
 * It never gets a broadcast action. An import overwrites this app's data, and the §1 receiver next
 * door is `exported="true"` with no permission — an import there would let any app on the phone
 * wipe any sister app.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless this app asks for one.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything — and **without touching the
     * DI graph**.
     *
     * A `ContentProvider.onCreate` runs before `Application.onCreate`, so a `call()` can be
     * dispatched on a binder thread while the main thread is still building the Koin graph. That is
     * not an edge case, it is the clean-phone case: a provider call is what starts this process at
     * all. So everything here comes from the manifest, the `PackageManager` and a plain enum —
     * never from `SettingsExport`'s injected `StocksProvider`/`AppPreferences`, which is why the
     * category labels are read off [SettingsExport.Cat] directly rather than through any function
     * on that object.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * megabytes into an app that would reject them — which it cannot do if the header is buried
     * inside an encrypted archive.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val contains = SettingsExport.Cat.entries.joinToString(",") { "\"${ctx.getString(it.labelRes)}\"" }

        @Suppress("DEPRECATION")
        val versionCode = pkg.versionCode
        return "OK:" + """
            {"app_id":"${ctx.packageName}",
             "version_code":$versionCode,
             "version_name":"${pkg.versionName}",
             "format":$FORMAT,
             "min_format_readable":$MIN_FORMAT_READABLE,
             "requires_launch_first":false,
             "requires_permissions":[],
             "contains":[$contains]}
        """.trimIndent().replace("\n", "")
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // A refused start is answered, not thrown. `start` closes the descriptor itself on that
        // path, so there is deliberately no `dup.close()` here — closing it twice would be a
        // different bug in place of the leak.
        AutomationDataService.start(ctx, jobId, dup, importing, extras)?.let { why ->
            AutomationJobs.finish(jobId)
            return fail(why)
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** This app's archive format — [SettingsExport]'s own `VERSION`, which the ZIP manifest carries. */
        const val FORMAT = 1

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
