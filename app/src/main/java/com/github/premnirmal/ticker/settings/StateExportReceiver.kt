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
private const val MIME_ZIP = "application/zip"
private const val MIN_PROGRESS_MS = 500L
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
 * The 保存復元 automation endpoint: 白い熊 自由作業盤 (and any sister task) fires
 * `<pkg>.action.EXPORT_STATE` or `<pkg>.action.LIST_CATEGORIES` with an automation token, this app
 * exports itself headlessly, and the outcome comes back as a fresh broadcast.
 *
 * Hard-won EMUI constraints — do not "improve" them: the reply is a plain broadcast carrying
 * `FLAG_INCLUDE_STOPPED_PACKAGES`; no `ResultReceiver`/`PendingIntent`/`Messenger` may ride along,
 * and the ordered-broadcast result channel is severed between third-party apps, so `setResultData`
 * is set for AOSP correctness only, never relied on.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)
        val pending = goAsync()
        val replied = AtomicBoolean(false)

        // Exactly one terminal reply per request: an async success and a synchronous error can never
        // both fire, and the broadcast stays open until that reply has gone out.
        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) return
            Timber.i("$LOG: $replyId <- ${result.lineSequence().first()}")
            sendReply(app, replyAction, replyPackage, replyId, result)
            runCatching { pending.setResultData(result) }
            pending.finish()
        }

        scope.launch {
            reply(runCatching { handle(app, intent, replyPackage, replyId) }.getOrElse { errorLine(it) })
        }
    }

    private fun handle(context: Context, intent: Intent, replyPackage: String?, replyId: String?): String {
        checkGate(context, intent)
        val pkg = context.packageName
        return when (intent.action) {
            pkg + ACTION_LIST_SUFFIX -> "OK:" + SettingsExport.categoryLines(context)
            pkg + ACTION_EXPORT_SUFFIX -> exportState(context, intent, replyPackage, replyId)
            else -> throw AutomationError("unknown action: ${intent.action}")
        }
    }

    /** The switch and the token, checked before any work — they debug differently, so they differ here. */
    private fun checkGate(context: Context, intent: Intent) {
        if (!AutomationAuth.isEnabled(context)) throw AutomationError("automation disabled")
        if (!AutomationAuth.matches(context, intent.getStringExtra(EXTRA_TOKEN))) throw AutomationError("bad token")
    }

    /** One ZIP, at one path, with the byte count this app measured — the caller cannot stat the file. */
    private fun exportState(context: Context, intent: Intent, replyPackage: String?, replyId: String?): String {
        val items = intent.getStringExtra(EXTRA_ITEMS)
        val unknown = SettingsExport.unknownIds(items)
        if (unknown.isNotEmpty()) throw AutomationError("unknown category in items: ${unknown.joinToString(",")}")
        val selection = SettingsExport.selectionOf(items)
        val target = resolveTarget(context, intent.getStringExtra(EXTRA_PATH))
        val progress = ProgressSender(context, intent.getStringExtra(EXTRA_PROGRESS_ACTION), replyPackage, replyId)

        val counting = CountingOutputStream(target.open())
        counting.use { SettingsExport.export(context, selection, it, progress::report) }
        val bytes = target.length().takeIf { it > 0L } ?: counting.count
        progress.finish(selection.size)
        return "OK:${target.path}|$bytes|${humanSize(bytes)}|${selection.size} categories"
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
        val file = File(dir, name)
        return Target(file.absolutePath, { FileOutputStream(file) }, { file.length() })
    }

    private fun safTarget(context: Context, dir: DocumentFile, name: String): Target {
        val doc = dir.createFile(MIME_ZIP, name) ?: throw AutomationError("cannot create file in export directory")
        return Target(
            path = absolutePathOf(doc.uri),
            open = { context.contentResolver.openOutputStream(doc.uri) ?: throw AutomationError("no output stream") },
            length = { doc.length() },
        )
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

    /** Where the single ZIP goes: how to open it, and how to measure what landed. */
    private class Target(val path: String, val open: () -> OutputStream, val length: () -> Long)

    /** What actually reached the file, as a fallback when the target cannot be stat'ed afterwards. */
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
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String?,
        private val replyId: String?,
    ) {

        private var lastMs = 0L

        fun report(current: Int, total: Int, label: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastMs < MIN_PROGRESS_MS) return
            lastMs = now
            send(current, total, context.getString(R.string.eim_auto_progress, current, total, label))
        }

        fun finish(total: Int) {
            lastMs = SystemClock.elapsedRealtime()
            send(total, total, context.getString(R.string.eim_auto_progress_done, total, total))
        }

        private fun send(current: Int, total: Int, text: String) {
            if (action.isNullOrEmpty()) return
            context.sendBroadcast(
                Intent(action).apply {
                    if (!replyPackage.isNullOrEmpty()) setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("app", context.getString(R.string.app_name))
                    putExtra("text", text)
                    putExtra("current", current.toLong())
                    putExtra("total", total.toLong())
                    putExtra("unit", context.getString(R.string.eim_auto_progress_unit))
                },
            )
        }
    }
}
