package com.github.premnirmal.ticker.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.github.premnirmal.ticker.settings.SettingsExport
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run far longer. **A binder call holds the caller** —
 * 応用管理 is drawing a list, and a slow synchronous call would freeze its UI, report no progress
 * and refuse cancellation. And **a backgrounded app writing for minutes is frozen mid-stream on
 * this phone**, which yields a truncated archive underneath a success reply: the worst failure
 * available, because it is indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FOREGROUND FIRST, before any decision that can return — including the decision to do
        // nothing. `startForegroundService` has already promised the platform we will go foreground
        // within its window, and the promise is not conditional on our finding work to do: skipping
        // it kills the process with ForegroundServiceDidNotStartInTimeException. So a caller
        // retrying with a stale job id would CRASH the app it is backing up rather than being
        // ignored. `importing` is read defensively FIRST, for the notification's sake — hoisting the
        // job-id read above this is the natural way to write it, and is exactly how several ports
        // acquired the crash on the null-intent branch. Guarded, because the start may itself be
        // refused on API 31+ from a background caller, and a throw here would be the very crash we
        // are avoiding.
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        val wentForeground = runCatching { goForeground(importing) }.isSuccess

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        // A stale or already-claimed job id stops SILENTLY. The instinct is `ERROR:unknown job`; do
        // not — that id's request has already had its one terminal reply, and a second one breaks
        // the single-reply rule the whole contract rests on.
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)

        val replied = AtomicBoolean(false)
        // A `val` holding a lambda, NOT a local `fun`. Co-located with a local-capturing anonymous
        // object it crashes AGP's lint analyser ("FirDeclaration was not found for class
        // KtProperty, fir is null") ten minutes into a release build, after Kotlin, Java and dex
        // have all succeeded. Do not tidy this back.
        val reply: (String) -> Unit = { result ->
            if (replied.compareAndSet(false, true)) {
                AutomationJobs.finish(jobId)
                // No package to aim at means nobody can hear it: since API 26 an implicit broadcast
                // reaches no manifest-declared receiver, so `setPackage(null)` is not a wider send,
                // it is no send. Skip it rather than pretending.
                if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            // Without this a backgrounded caller never hears the answer, and on a
                            // clean phone the caller may not have been launched at all.
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            putExtra(EXTRA_REPLY_ID, jobId)
                            putExtra(AutomationProvider.KEY_RESULT, result)
                        },
                    )
                }
            }
        }

        // The descriptor has left HANDOVER by now, so if we never made it foreground nothing else
        // would ever close it — and the caller is holding an `OK:<job_id>` for work that cannot run.
        // Answer rather than die quietly: a silent death here shows up only on a phone without the
        // battery-optimisation exemption, which is precisely the clean-phone case.
        if (!wentForeground) {
            runCatching { fd.close() }
            reply("ERROR:cannot go foreground")
            return stop(startId)
        }

        val progress = ProgressSender(this, progressAction, replyPackage, jobId)
        scope.launch {
            try {
                fd.use { open ->
                    if (importing) {
                        runImport(open, reply)
                    } else {
                        runExport(jobId, open, intent.getStringExtra(AutomationProvider.KEY_ITEMS), progress, reply)
                    }
                }
            } catch (t: Throwable) {
                reply("ERROR:${t.message ?: t::class.java.simpleName}")
            } finally {
                progress.stopHeartbeat()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * `specialUse` is an API 34 **value**, so this is a build-time question that still needs a
     * runtime branch: ask for the typed overload only where it exists, use the plain one below it,
     * and let the caller catch either failing. EMUI reporting `SDK_INT = 31` on a platform based on
     * Android 13 is a live hazard here rather than a curiosity — a version-derived guess is wrong in
     * both directions — and `specialUse` is known to work on that phone, so it is not downgraded.
     */
    private fun goForeground(importing: Boolean) {
        val notification = notification(importing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: ProgressSender,
        reply: (String) -> Unit,
    ) {
        val unknown = SettingsExport.unknownIds(items)
        if (unknown.isNotEmpty()) {
            reply("ERROR:unknown category in items: ${unknown.joinToString(",")}")
            return
        }
        val selection = SettingsExport.selectionOf(items)
        // The heartbeat matters here even though this export is small: §2a writes into a descriptor
        // the CALLER supplied, which may be a pipe — so a category blocks for exactly as long as
        // 応用管理 is slow to drain it, and that stall has no relation to how much data we hold.
        progress.startHeartbeat(scope)
        // Counted as it goes rather than stat'ed afterwards: the caller owns the file and we may not
        // be able to see it at all.
        val counting = CountingStream(ParcelFileDescriptor.AutoCloseOutputStream(fd))
        counting.use { out ->
            SettingsExport.export(
                context = this,
                selection = selection,
                out = out,
                onProgress = progress::report,
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            )
        }
        progress.stopHeartbeat()
        if (AutomationJobs.isCancelled(jobId)) {
            reply("ERROR:cancelled")
        } else {
            progress.finish(selection.size)
            reply("OK:${counting.written}|${selection.size} categories")
        }
    }

    /**
     * Read the whole archive before touching anything — and read it **to disk**.
     *
     * A partial read that failed halfway would otherwise import half an archive, and a half-restored
     * app is worse than one that refused. Spooling to a cache file rather than a byte array keeps
     * the guarantee identical while moving the bound from RAM to disk: this app's archive is
     * settings JSON plus whatever fonts 白い熊 has added, which is small today, but the descriptor
     * is a stream of unknown length handed over by someone else.
     *
     * [SettingsExport.import] is `suspend` and flushes every store the restore touched before it
     * returns, which is what makes the `OK` below safe: 応用管理 force-stops this process with a
     * `SIGKILL` the instant it reads that reply.
     */
    private suspend fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val spool = File(cacheDir, SPOOL_NAME)
        try {
            val bytes = spool(fd, spool)
            if (bytes == 0L) {
                reply("ERROR:empty archive")
                return
            }
            val archive = spool.readBytes()
            // Every category the archive actually carries, not every category we know about: asking
            // for one the archive lacks is how a restore ends up reporting success over nothing.
            val present = SettingsExport.categoriesIn(archive)
            if (present.isEmpty()) {
                reply("ERROR:archive carries no categories")
                return
            }
            val selection = SettingsExport.Selection(
                cats = present.toSet(),
                subs = SettingsExport.Sub.entries.filter { it.parent in present }.toSet(),
            )
            val summary = SettingsExport.import(this, archive, selection)
            reply("OK:${summary.lineSequence().count()} restored")
        } finally {
            spool.delete()
        }
    }

    private fun spool(fd: ParcelFileDescriptor, into: File): Long =
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            into.outputStream().use { out -> input.copyTo(out) }
        }

    private fun notification(importing: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.eim_auto_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(if (importing) R.string.eim_auto_importing else R.string.eim_auto_exporting))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    /**
     * Leave, having satisfied the promise `startForegroundService` made on our behalf.
     *
     * Every exit runs after the foreground call, so a bail-out that skipped [stopForeground] would
     * leave a live notification and a foreground service behind.
     */
    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /**
     * §3 progress for the data door, with the **`job_id` as the correlation id** — set in both
     * `job_id` and `reply_id` so one progress reader serves both doors.
     *
     * A throttle is not a heartbeat and they solve opposite problems: the throttle caps a chatty
     * engine at one message per 500 ms; the heartbeat covers an engine that is not chatty at all.
     * This app reports once per category, so between two categories it could go quiet — and past
     * two minutes it would be presumed dead. The heartbeat re-sends the **last true line**; it never
     * invents a moving number, because a fabricated count cannot be told apart from progress.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String?,
        private val jobId: String,
    ) {

        private var lastMs = 0L

        @Volatile
        private var last: Line? = null

        @Volatile
        private var heartbeat: Job? = null

        private class Line(val current: Int, val total: Int, val id: String?, val text: String)

        fun startHeartbeat(scope: CoroutineScope) {
            if (action.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            heartbeat = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    val line = last ?: continue
                    if (SystemClock.elapsedRealtime() - lastMs >= HEARTBEAT_MS) {
                        send(line.current, line.total, line.id, line.text)
                    }
                }
            }
        }

        fun stopHeartbeat() {
            heartbeat?.cancel()
            heartbeat = null
        }

        fun report(current: Int, total: Int, id: String, label: String) {
            val line = Line(current, total, id, context.getString(R.string.eim_auto_progress, current, total, label))
            last = line
            if (SystemClock.elapsedRealtime() - lastMs < MIN_PROGRESS_MS) return
            send(line.current, line.total, line.id, line.text)
        }

        fun finish(total: Int) {
            send(total, total, null, context.getString(R.string.eim_auto_progress_done, total, total))
        }

        private fun send(current: Int, total: Int, id: String?, text: String) {
            // Every broadcast back must carry `setPackage`, progress included: without it a
            // manifest-declared receiver hears nothing at all, and the export then runs, finishes
            // and reports correctly while every progress line is dropped in silence.
            if (action.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            lastMs = SystemClock.elapsedRealtime()
            context.sendBroadcast(
                Intent(action).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(EXTRA_REPLY_ID, jobId)
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

    /**
     * Counts bytes on their way into the caller's descriptor.
     *
     * A NAMED class holding `written` as its own property, deliberately — not an anonymous
     * `object : OutputStream()` capturing a local `var`. Combined with a local `fun` in the same
     * method, that shape crashes AGP's lint analyser outright.
     */
    private class CountingStream(private val out: OutputStream) : OutputStream() {
        var written: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            written += len
        }

        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    companion object {
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val SPOOL_NAME = "automation-import.zip"
        private const val MIN_PROGRESS_MS = 500L
        private const val HEARTBEAT_MS = 15_000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /** How long a claimed job may sit undelivered before its descriptor is reclaimed. */
        private const val UNDELIVERED_SECONDS = 60L

        private val REAPER: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "automation-handover-reaper").apply { isDaemon = true }
            }

        /**
         * Reclaim a descriptor whose service never arrived.
         *
         * A no-op in the normal case: `onStartCommand` removes the entry within milliseconds. It
         * only fires when the start was accepted and never delivered.
         */
        private fun abandon(jobId: String) {
            val stranded = HANDOVER.remove(jobId) ?: return
            runCatching { stranded.close() }
            AutomationJobs.finish(jobId)
        }

        /**
         * Claim the job and hand the descriptor over.
         *
         * @return null when the service is running, or the `ERROR:` line to answer the caller with.
         *   On a failure the descriptor is **already closed here** — the provider must not close it
         *   a second time.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): String? {
            HANDOVER[jobId] = fd
            return try {
                context.startForegroundService(intentFor(context, jobId, importing, extras))
                // A start can also be ACCEPTED and never DELIVERED — the system drops it, the
                // process is killed between the two, EMUI decides otherwise. Nothing throws, so
                // neither guard fires, and the caller's descriptor would sit here held open for the
                // life of the process while the caller waits for a reply that cannot come.
                REAPER.schedule({ abandon(jobId) }, UNDELIVERED_SECONDS, TimeUnit.SECONDS)
                null
            } catch (e: Exception) {
                // A provider `call()` is a BACKGROUND start, and API 31+ refuses one with
                // `ForegroundServiceStartNotAllowedException` unless the app is exempt from battery
                // optimisation. Left unguarded this strands the caller's open descriptor with
                // nothing alive to close it, AND throws out of `call()` across the binder as a
                // RuntimeException — which §2a forbids: a refusal is returned, never thrown. The
                // descriptor is closed HERE, so the provider must not close it again.
                HANDOVER.remove(jobId)
                runCatching { fd.close() }
                "ERROR:cannot start data service: ${e.javaClass.simpleName}"
            }
        }

        private fun intentFor(
            context: Context,
            jobId: String,
            importing: Boolean,
            extras: Bundle?,
        ): Intent =
            Intent(context, AutomationDataService::class.java).apply {
                putExtra(EXTRA_JOB, jobId)
                putExtra(EXTRA_IMPORTING, importing)
                putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                putExtra(
                    AutomationProvider.KEY_REPLY_ACTION,
                    extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                )
                putExtra(
                    AutomationProvider.KEY_REPLY_PACKAGE,
                    extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                )
                putExtra(
                    AutomationProvider.KEY_PROGRESS_ACTION,
                    extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                )
            }
    }
}
