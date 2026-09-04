package com.github.premnirmal.ticker.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val EXTRA_TOKEN = "token"
private const val EXTRA_PATH = "path"
private const val EXTRA_ITEMS = "items"
private const val EXTRA_PROGRESS_ACTION = "progress_action"
private const val EXTRA_REPLY_ACTION = "reply_action"
private const val EXTRA_REPLY_PACKAGE = "reply_package"
private const val EXTRA_REPLY_ID = "reply_id"
private const val EXTRA_RESULT = "result"
private const val ACTION_EXPORT_SUFFIX = ".action.EXPORT_STATE"
private const val ACTION_LIST_SUFFIX = ".action.LIST_CATEGORIES"
private const val ACTION_CANCEL_SUFFIX = ".action.CANCEL_EXPORT"
private const val MIME_ZIP = "application/zip"
private const val MIN_PROGRESS_MS = 500L
private const val PART_SUFFIX = ".part"
private const val KB = 1024L
private const val MB = KB * KB
private const val GB = MB * KB
private const val PRIMARY_VOLUME = "primary"
private const val LOG = "StateExport"

/** The export runs off the broadcast thread — the receiver instance is long gone by the time it ends. */
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

/** A failure with a short, stable reason for the `ERROR:<reason>` reply line. */
private class AutomationError(val reason: String) : Exception(reason)

/**
 * The one export this process is running, and the flag that stops it.
 *
 * Process-local and **never persisted**, deliberately: a persisted "export in progress" flag wedges
 * the app for good after a single crash, and every later request answers `ERROR:export already
 * running` with no way back but killing the process. This is released in a `finally`, and the work
 * it guards is bounded — a handful of JSON categories and the font files — so the `finally` runs.
 */
private object RunningExport {

    private val current = AtomicReference<Handle?>(null)

    /**
     * @return false when another export is already running — §1 forbids two at once, which is also
     *   what makes a `CANCEL_EXPORT` with no `reply_id` unambiguous.
     */
    fun begin(handle: Handle): Boolean = current.compareAndSet(null, handle)

    fun end(handle: Handle) {
        current.compareAndSet(handle, null)
    }

    /**
     * Ask the running export to stop. A **silent no-op** when nothing is running or the id names a
     * different run: 自由作業盤 fires this whenever 白い熊 presses 中止, without knowing how far we
     * got, so a cancel that arrives after the export finished is the ordinary race and not an error.
     */
    fun cancel(replyId: String?) {
        val running = current.get() ?: return
        if (replyId != null && running.replyId != null && replyId != running.replyId) return
        running.cancelled = true
        Timber.i("$LOG: cancel requested for ${running.replyId}")
    }

    class Handle(val replyId: String?) {
        @Volatile
        var cancelled: Boolean = false
    }
}

/**
 * The 保存復元 automation endpoint: 白い熊 自由作業盤 fires `<pkg>.action.EXPORT_STATE`,
 * `<pkg>.action.LIST_CATEGORIES` or `<pkg>.action.CANCEL_EXPORT`, this app exports itself
 * headlessly, and the outcome comes back as a fresh broadcast.
 *
 * This is the **unauthenticated** half of the automation surface, and in v2 that is deliberate: it
 * only ever writes where it was told to and reports what it did. Everything that moves data through
 * a caller-supplied descriptor — and `import`, which exists nowhere else — lives behind
 * [com.github.premnirmal.ticker.automation.AutomationProvider], which knows who is calling.
 *
 * Hard-won EMUI constraints — do not "improve" them: the reply is a plain broadcast carrying
 * `FLAG_INCLUDE_STOPPED_PACKAGES`; no `ResultReceiver`/`PendingIntent`/`Messenger` may ride along,
 * and the ordered-broadcast result channel is severed between third-party apps, so `setResultData`
 * is set for AOSP correctness only, never relied on.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        // CANCEL is fire-and-forget: it sends no reply of its own, because the one terminal reply
        // belongs to the export request it stopped. A refusal here is silent too — there is nobody
        // to report to.
        if (intent.action == app.packageName + ACTION_CANCEL_SUFFIX) {
            if (AutomationAuth.refuse(app, intent.getStringExtra(EXTRA_TOKEN)) == null) {
                RunningExport.cancel(intent.getStringExtra(EXTRA_REPLY_ID))
            }
            return
        }

        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)
        val pending = goAsync()
        val replied = AtomicBoolean(false)

        // Exactly one terminal reply per request: an async success and a synchronous error can never
        // both fire, and the broadcast stays open until that reply has gone out. A `val` lambda
        // rather than a local `fun` — co-located with a local-capturing anonymous object it can kill
        // AGP's lint analyser outright, and the robust shape costs nothing.
        val reply: (String) -> Unit = { result ->
            if (replied.compareAndSet(false, true)) {
                Timber.i("$LOG: $replyId <- ${result.lineSequence().first()}")
                sendReply(app, replyAction, replyPackage, replyId, result)
                runCatching { pending.setResultData(result) }
                pending.finish()
            }
        }

        scope.launch {
            reply(runCatching { handle(app, intent, replyPackage, replyId) }.getOrElse { errorLine(it) })
        }
    }

    private fun handle(context: Context, intent: Intent, replyPackage: String?, replyId: String?): String {
        // The whole gate in one call: the switch, then the token only if this app asks for one. A
        // token sent to an app that does not require one is ignored, never refused.
        AutomationAuth.refuse(context, intent.getStringExtra(EXTRA_TOKEN))?.let { return it }
        val pkg = context.packageName
        return when (intent.action) {
            pkg + ACTION_LIST_SUFFIX -> "OK:" + SettingsExport.categoryLines(context)
            pkg + ACTION_EXPORT_SUFFIX -> exportState(context, intent, replyPackage, replyId)
            else -> throw AutomationError("unknown action: ${intent.action}")
        }
    }

    /**
     * One ZIP, at one path, with the byte count this app measured — the caller cannot stat the file.
     *
     * Written to `<final-name>.part` and renamed only once the archive is closed and complete, so a
     * killed or cancelled export never leaves a file that is indistinguishable from a real backup.
     * 白い熊 keeps every app's backups in one directory sorted by date, where a truncated archive
     * would silently become "the latest backup" of this app.
     */
    private fun exportState(context: Context, intent: Intent, replyPackage: String?, replyId: String?): String {
        val selection = selectionFor(intent.getStringExtra(EXTRA_ITEMS))
        val target = resolveTarget(context, intent.getStringExtra(EXTRA_PATH))
        val progress = ProgressSender(context, intent.getStringExtra(EXTRA_PROGRESS_ACTION), replyPackage, replyId)

        val handle = RunningExport.Handle(replyId)
        if (!RunningExport.begin(handle)) throw AutomationError("export already running")
        try {
            val counting = CountingOutputStream(target.open())
            counting.use { SettingsExport.export(context, selection, it, progress::report) { handle.cancelled } }
            // The four obligations of a cancel, in order: the write loop above already unwound at an
            // entry boundary; the partial goes now; `ERROR:cancelled` is the terminal reply for the
            // ORIGINAL request, through the same single-fire guard so it cannot double-fire with a
            // success; and there is no foreground service or wakelock on this path to release,
            // because a settings-sized export finishes in seconds and never leaves the broadcast
            // window (§1's `goAsync()` case).
            if (handle.cancelled) {
                target.discard()
                return "ERROR:cancelled"
            }
            val bytes = counting.count.takeIf { it > 0L } ?: target.length()
            target.finish()
            progress.finish(selection.size)
            return "OK:${target.path}|$bytes|${humanSize(bytes)}|${selection.size} categories"
        } catch (t: Throwable) {
            // Whatever failed, the half-written archive does not survive it.
            target.discard()
            throw t
        } finally {
            RunningExport.end(handle)
        }
    }

    /** The selection named by `items`, or a refusal naming every id we do not recognise. */
    private fun selectionFor(items: String?): SettingsExport.Selection {
        val unknown = SettingsExport.unknownIds(items)
        if (unknown.isNotEmpty()) throw AutomationError("unknown category in items: ${unknown.joinToString(",")}")
        return SettingsExport.selectionOf(items)
    }

    /** Directory precedence: the `path` extra → the app's configured export directory → an error. */
    private fun resolveTarget(context: Context, path: String?): Target {
        val name = SettingsExport.exportFileName()
        if (!path.isNullOrBlank()) {
            if (canWriteAnywhere(context)) return fileTarget(File(path), name)
            // Without All-Files-Access we may only fall back to the configured SAF directory.
            if (SettingsExport.exportDir(context) == null) throw AutomationError("no-storage-access")
            Timber.w("$LOG: no all-files access — ignoring path=$path")
        }
        val dir = SettingsExport.exportDir(context) ?: throw AutomationError("no-directory")
        return safTarget(context, dir, name)
    }

    private fun fileTarget(dir: File, name: String): Target {
        if (!dir.isDirectory && !dir.mkdirs()) throw AutomationError("cannot create directory: $dir")
        val part = File(dir, name + PART_SUFFIX)
        val done = File(dir, name)
        return Target(
            path = done.absolutePath,
            open = { FileOutputStream(part) },
            length = { part.length() },
            finish = { if (!part.renameTo(done)) throw AutomationError("cannot rename $part") },
            discard = { part.delete() },
        )
    }

    private fun safTarget(context: Context, dir: DocumentFile, name: String): Target {
        val doc = dir.createFile(MIME_ZIP, name + PART_SUFFIX)
            ?: throw AutomationError("cannot create file in export directory")
        return Target(
            path = absolutePathOf(doc.uri).removeSuffix(PART_SUFFIX),
            open = { openDoc(context, doc.uri) },
            length = { doc.length() },
            finish = { renameDoc(context, doc.uri, name) },
            discard = { runCatching { doc.delete() } },
        )
    }

    private fun openDoc(context: Context, uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri) ?: throw AutomationError("no output stream")

    private fun renameDoc(context: Context, uri: Uri, name: String) {
        DocumentsContract.renameDocument(context.contentResolver, uri, name)
            ?: throw AutomationError("cannot rename to $name")
    }

    /** All-Files-Access on Android 11+, the legacy runtime permission below it. */
    private fun canWriteAnywhere(context: Context): Boolean =
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Best-effort filesystem path for a SAF document on primary storage — the reply must name a path. */
    private fun absolutePathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        val parts = docId.split(':', limit = 2)
        if (parts.size != 2 || parts[0] != PRIMARY_VOLUME) return uri.toString()
        return File(Environment.getExternalStorageDirectory(), parts[1]).absolutePath
    }

    private fun sendReply(context: Context, action: String?, pkg: String?, replyId: String?, result: String) {
        // Both, or nothing. Since API 26 an implicit broadcast reaches no manifest-declared receiver
        // at all, so `setPackage(null)` is not a wider send — it is no send.
        if (action.isNullOrEmpty() || pkg.isNullOrEmpty()) {
            Timber.w("$LOG: nowhere to reply — reply_action/reply_package missing")
            return
        }
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_REPLY_ID, replyId)
                putExtra(EXTRA_RESULT, result)
            },
        )
    }

    private fun errorLine(t: Throwable): String {
        Timber.w(t, "$LOG: request failed")
        return "ERROR:" + ((t as? AutomationError)?.reason ?: "${t.javaClass.simpleName}: ${t.message}")
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < KB -> "$bytes B"
        bytes < MB -> String.format(Locale.ROOT, "%.1f KB", bytes.toDouble() / KB)
        bytes < GB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / MB)
        else -> String.format(Locale.ROOT, "%.2f GB", bytes.toDouble() / GB)
    }

    /**
     * Where the single ZIP goes: how to open it, how to measure what landed, and how to make it
     * real — or make it go away.
     */
    private class Target(
        val path: String,
        val open: () -> OutputStream,
        val length: () -> Long,
        val finish: () -> Unit,
        val discard: () -> Unit,
    )

    /** What actually reached the file — the caller cannot stat it, so this app counts. */
    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {

        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    /**
     * Progress with real counts — never a percentage — throttled to one broadcast per 500 ms, with a
     * mandatory final one when the export completes.
     *
     * `item` carries the **category id** being written right now, which is how 自由作業盤 knows
     * which row to highlight: it cannot work that out from `current`, and an app that omits it puts
     * its count against the wrong row. `current` is the POSITION of the category being written
     * (`1` while the first is written), agreeing with the label beside it in `text`.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String?,
        private val replyId: String?,
    ) {

        private var lastMs = 0L

        fun report(current: Int, total: Int, id: String, label: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastMs < MIN_PROGRESS_MS) return
            lastMs = now
            send(current, total, id, context.getString(R.string.eim_auto_progress, current, total, label))
        }

        fun finish(total: Int) {
            lastMs = SystemClock.elapsedRealtime()
            send(total, total, null, context.getString(R.string.eim_auto_progress_done, total, total))
        }

        private fun send(current: Int, total: Int, id: String?, text: String) {
            // `progress_action` is useless without `reply_package`: every broadcast back must carry
            // `setPackage`, or it is not a weak progress broadcast but no progress broadcast at all.
            if (action.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            context.sendBroadcast(
                Intent(action).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("app", context.getString(R.string.app_name))
                    id?.let { putExtra("item", it) }
                    putExtra("text", text)
                    putExtra("current", current.toLong())
                    putExtra("total", total.toLong())
                    putExtra("unit", context.getString(R.string.eim_auto_progress_unit))
                },
            )
        }
    }
}
