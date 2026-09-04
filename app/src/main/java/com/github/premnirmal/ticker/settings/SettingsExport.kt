package com.github.premnirmal.ticker.settings

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.ticker.detail.QuoteElement
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.ui.FontManager
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import com.github.premnirmal.tickerwidget.BuildConfig
import com.github.premnirmal.tickerwidget.R
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Settings backup/restore engine for the "白い熊 株価表示 UI" page (same idea as the Kōjiki export):
 * a ZIP of one JSON file per category plus a manifest, written to (and re-read from) a
 * user-chosen SAF directory whose tree URI is persisted in a device-local prefs file that is
 * itself never exported. Imports merge — they only touch keys present in the file.
 */
@Suppress("TooManyFunctions") // one small export + import function per category, by design
object SettingsExport : KoinComponent {

    /** An exportable settings category; [id] is the ZIP entry name, [labelRes] the checkbox label. */
    enum class Cat(val id: String, @StringRes val labelRes: Int) {
        GENERAL("general", R.string.eim_cat_general),
        APPEARANCE("appearance", R.string.eim_cat_appearance),
        WIDGETS("widgets", R.string.eim_cat_widgets),
        PORTFOLIO("portfolio", R.string.eim_cat_portfolio),
    }

    /**
     * A separately selectable part of a [Cat] — a sub-option of the 保存復元 contract: listed with
     * its parent's id as a third field, and addressable on its own in the `items` extra.
     */
    enum class Sub(val id: String, val parent: Cat, @StringRes val labelRes: Int) {
        FONTS("appearance.fonts", Cat.APPEARANCE, R.string.eim_cat_fonts),
    }

    /** A chosen set of export parts: whole categories plus the sub-options selected under them. */
    data class Selection(val cats: Set<Cat>, val subs: Set<Sub>) {

        /** The part ids, each parent before its own children — the manifest list and `items` vocabulary. */
        val ids: List<String>
            get() = Cat.entries.filter { it in cats }.flatMap { cat ->
                listOf(cat.id) + Sub.entries.filter { it.parent == cat && it in subs }.map { it.id }
            } + Sub.entries.filter { it in subs && it.parent !in cats }.map { it.id }

        val size: Int get() = cats.size + subs.size

        val isEmpty: Boolean get() = size == 0

        companion object {
            val ALL = Selection(Cat.entries.toSet(), Sub.entries.toSet())
        }
    }

    // The family-wide backup name is "<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip" — no version, no
    // decoration, so every 白い熊 app's backups sort together in one directory.
    const val EXPORT_PREFIX = "shiroikuma-kabukahyoji_"
    private const val LEGACY_EXPORT_PREFIX = "shiroikuma-kabukahyoji-" // pre-2026-07-25 names
    private const val FORMAT = "kabukahyoji-export"
    private const val VERSION = 1
    internal const val EXIM_PREFS = "kabukahyoji_eximport" // device-local; never exported
    private const val KEY_DIR_URI = "dir_uri"
    private const val WIDGET_PREFS_PREFIX = "stocks_widget_"
    private const val FONTS_DIR_ENTRY = "fonts/"
    private const val JSON_INDENT = 2

    private val appPreferences: AppPreferences by inject()
    private val stocksProvider: StocksProvider by inject()
    private val portfolioSerializer: PortfolioSerializer by inject()
    private val widgetDataProvider: WidgetDataProvider by inject()

    // ---- Export directory + latest-export status -------------------------------------------------

    private fun eximPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximPrefs(context).getString(KEY_DIR_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        eximPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply()
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    fun dirDisplayName(context: Context): String? =
        exportDir(context)?.name ?: dirUri(context)?.lastPathSegment

    private fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter { it.isFile && isExportName(it.name) }.maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    /** Our own backups: the current name family, plus the pre-2026-07-25 one so old files still count. */
    private fun isExportName(name: String?): Boolean =
        name != null && name.endsWith(".zip") &&
            (name.startsWith(EXPORT_PREFIX) || name.startsWith(LEGACY_EXPORT_PREFIX))

    /** (message, isWarning) for the "last export" line, queried when the page/panel opens. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        if (exportDir(context) == null) return context.getString(R.string.eim_warn_nodir) to true
        val newest = latestExport(context)
            ?: return context.getString(R.string.eim_warn_none) to true
        return context.getString(R.string.eim_last, fmtTs(newest.lastModified())) to false
    }

    private fun fmtTs(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ---- The category list (also the LIST_CATEGORIES reply) ---------------------------------------

    /** The `id<TAB>label[<TAB>parent-id]` lines of the automation contract's LIST_CATEGORIES reply. */
    fun categoryLines(context: Context): String =
        Cat.entries.flatMap { cat ->
            listOf("${cat.id}\t${context.getString(cat.labelRes)}") +
                Sub.entries.filter { it.parent == cat }
                    .map { "${it.id}\t${context.getString(it.labelRes)}\t${cat.id}" }
        }.joinToString("\n")

    /** Ids in the comma-separated `items` extra that name neither a category nor a sub-option. */
    fun unknownIds(items: String?): List<String> =
        splitItems(items).filter { id -> Cat.entries.none { it.id == id } && Sub.entries.none { it.id == id } }

    /** The selection named by an `items` extra; absent or empty selects everything. */
    fun selectionOf(items: String?): Selection {
        val ids = splitItems(items)
        if (ids.isEmpty()) return Selection.ALL
        return Selection(
            cats = Cat.entries.filter { it.id in ids }.toSet(),
            subs = Sub.entries.filter { it.id in ids }.toSet(),
        )
    }

    private fun splitItems(items: String?): List<String> =
        items?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    // ---- Export -----------------------------------------------------------------------------------

    /**
     * Writes a ZIP of [selection] to [out] — the headless core that both the Export/Import panel and
     * [StateExportReceiver] call. [onProgress] gets `(current, total, label)` with real counts (never
     * a percentage) before each part is written. Returns a short human summary.
     */
    fun export(
        context: Context,
        selection: Selection,
        out: OutputStream,
        onProgress: (current: Int, total: Int, id: String, label: String) -> Unit = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        val lines = mutableListOf<String>()
        val total = selection.size
        var current = 0
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(selection.ids))
            writeEntry(zip, "manifest.json", manifest.toString(JSON_INDENT))

            for (cat in Cat.entries.filter { it in selection.cats }) {
                // Between entries, never mid-write: a cancelled export unwinds at a boundary so the
                // partial file is a truncated ZIP we delete, not a torn one we might rename.
                if (isCancelled()) return@use
                onProgress(++current, total, cat.id, context.getString(cat.labelRes))
                lines += writeCategory(context, zip, cat)
            }
            for (sub in Sub.entries.filter { it in selection.subs }) {
                if (isCancelled()) return@use
                onProgress(++current, total, sub.id, context.getString(sub.labelRes))
                lines += writeSub(context, zip, sub)
            }
        }
        return lines.joinToString("\n")
    }

    private fun writeCategory(context: Context, zip: ZipOutputStream, cat: Cat): String {
        val label = context.getString(cat.labelRes)
        return when (cat) {
            Cat.GENERAL -> {
                writeEntry(zip, "${cat.id}.json", exportGeneral())
                label
            }
            Cat.APPEARANCE -> {
                writeEntry(zip, "${cat.id}.json", exportAppearance())
                label
            }
            Cat.WIDGETS -> {
                writeEntry(zip, "${cat.id}.json", exportWidgets(context))
                label
            }
            Cat.PORTFOLIO -> {
                val portfolio = stocksProvider.portfolio.value
                writeEntry(zip, "${cat.id}.json", portfolioSerializer.serializePortfolio(portfolio))
                "$label (${portfolio.size})"
            }
        }
    }

    private fun writeSub(context: Context, zip: ZipOutputStream, sub: Sub): String = when (sub) {
        Sub.FONTS -> "${context.getString(sub.labelRes)} (${exportFonts(context, zip)})"
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun exportGeneral(): String = JSONObject()
        .put("updateInterval", appPreferences.updateIntervalPref)
        .put("startTime", appPreferences.startTime().toPrefString())
        .put("endTime", appPreferences.endTime().toPrefString())
        .put("updateDays", JSONArray(appPreferences.updateDays().sorted()))
        .put("autoSort", appPreferences.autoSort())
        .put("roundToTwoDp", appPreferences.roundToTwoDecimalPlaces())
        .put("notificationAlerts", appPreferences.notificationAlerts())
        .put("theme", appPreferences.themePref)
        .toString(JSON_INDENT)

    private fun exportAppearance(): String {
        val o = JSONObject()
            .put("dynamicColour", appPreferences.uiUseDynamicColour)
            .put("language", appPreferences.uiLanguageTag)
            .put("quoteLayout", appPreferences.uiQuoteLayout)
            .put("recentColours", JSONArray(appPreferences.uiRecentColours))
        val pages = JSONObject()
        for (page in ThemePage.entries) {
            val p = JSONObject()
            attrs(page, COLOUR_BASE, "COLOUR").forEach { p.put(it, tag("c", appPreferences.getPageColour(page, it))) }
            attrs(page, FONT_BASE, "FONT").forEach { p.put(it, tag("f", appPreferences.getPageFont(page, it))) }
            attrs(page, WEIGHT_BASE, "WEIGHT").forEach { p.put(it, tag("w", appPreferences.getPageWeight(page, it))) }
            attrs(page, SIZE_BASE, "SIZE").forEach {
                p.put(it, tag("z", appPreferences.getPageSize(page, it).toDouble()))
            }
            pages.put(page.key, p)
        }
        return o.put("pages", pages).toString(JSON_INDENT)
    }

    private fun tag(t: String, v: Any): JSONObject = JSONObject().put("t", t).put("v", v)

    private fun exportFonts(context: Context, zip: ZipOutputStream): Int {
        val files = FontManager.fontsDir(context).listFiles()?.filter { it.isFile } ?: return 0
        for (file in files) {
            zip.putNextEntry(ZipEntry(FONTS_DIR_ENTRY + file.name))
            zip.write(file.readBytes())
            zip.closeEntry()
        }
        return files.size
    }

    private fun exportWidgets(context: Context): String {
        val o = JSONObject()
        for (id in widgetIds()) {
            val all = context.getSharedPreferences("$WIDGET_PREFS_PREFIX$id", Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            val w = JSONObject()
            for ((k, v) in all) {
                val e = when (v) {
                    is Boolean -> tag("b", v)
                    is Int -> tag("i", v)
                    is Long -> tag("l", v)
                    is Float -> tag("f", v.toDouble())
                    is String -> tag("s", v)
                    is Set<*> -> tag("ss", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                w.put(k, e)
            }
            o.put(id.toString(), w)
        }
        return o.toString(JSON_INDENT)
    }

    private fun widgetIds(): List<Int> =
        widgetDataProvider.getAppWidgetIds().toList() + AppWidgetManager.INVALID_APPWIDGET_ID

    // ---- Import -----------------------------------------------------------------------------------

    /** The categories present in an export ZIP (via its manifest, falling back to entry probing). */
    fun categoriesIn(zip: ByteArray): List<Cat> {
        val files = readZip(zip)
        val fromManifest = files["manifest.json"]?.let { bytes ->
            runCatching {
                val arr = JSONObject(String(bytes)).getJSONArray("categories")
                (0 until arr.length()).mapNotNull { i -> catOf(arr.getString(i)) }.distinct()
            }.getOrNull()
        }
        return fromManifest ?: Cat.entries.filter { files.containsKey("${it.id}.json") }
    }

    /** The category a part id belongs to: a [Cat] id itself, or the parent of a [Sub] id. */
    private fun catOf(id: String): Cat? =
        Cat.entries.firstOrNull { it.id == id } ?: Sub.entries.firstOrNull { it.id == id }?.parent

    /** Applies the selected parts of an export ZIP, skipping absent ones. Returns a summary. */
    /**
     * Restores [selection] from [zip], merging per key and skipping absent categories.
     *
     * `suspend` because of the portfolio: see [flushForRestore] and
     * [com.github.premnirmal.ticker.model.StocksProvider.addPortfolioNow] for why this must not
     * return until every write has actually landed.
     */
    suspend fun import(context: Context, zip: ByteArray, selection: Selection): String {
        val files = readZip(zip)
        val lines = mutableListOf<String>()
        for (cat in Cat.entries.filter { it in selection.cats }) {
            val json = files["${cat.id}.json"] ?: continue
            lines += runCatching { "${context.getString(cat.labelRes)}: ${importCategory(context, cat, String(json))}" }
                .getOrElse { "${context.getString(cat.labelRes)}: ✗ ${it.message}" }
        }
        for (sub in Sub.entries.filter { it in selection.subs }) {
            val n = when (sub) {
                Sub.FONTS -> importFonts(context, files)
            }
            if (n > 0) lines += "${context.getString(sub.labelRes)}: $n"
        }
        flushForRestore(context)
        return lines.joinToString("\n")
    }

    /**
     * Make every write this restore touched durable, before anyone is told it succeeded.
     *
     * 応用管理 force-stops this app with `Process.killProcess` — a `SIGKILL` — the instant the
     * automation import replies `OK`. Anything still queued at that moment is simply lost, and the
     * restore reports success over missing data. It is invisible in testing, because a hand-run
     * import from the Export/Import panel is followed by an orderly lifecycle that flushes properly;
     * only the automated path kills the process cold.
     *
     * What this app actually owes, audited write by write rather than by grepping for `apply()`:
     *
     * - **The settings themselves owe nothing.** [AppPreferences] writes through
     *   `DataStorePreferenceStore`, whose setter is `runBlockingPreferences { dataStore.edit { … } }`
     *   — Preferences DataStore writes temp-file-then-rename and does not resume until that is
     *   durable, so `general` and `appearance` have already landed by the time their setter returns.
     * - **The widget prefs commit their own editor** ([importWidgets]).
     * - **Fonts** go through `File.writeBytes`, which is durable on return.
     * - **The portfolio needed real work**: `addPortfolio` persists its Room rows through a
     *   fire-and-forget `coroutineScope.launch`, so [importCategory] calls `addPortfolioNow`, which
     *   awaits them.
     * - **And the watchlist was the one that would actually have been lost.** `saveTickers` reaches
     *   `SharedPreferencesTickersStore`, which uses `androidx.core.content.edit { }` —
     *   **that defaults to `commit = false`**, so the restored ticker symbols were an `apply()` in
     *   disguise, with no literal `apply()` anywhere to find. The empty `commit()` below blocks on
     *   that file's write lock until the queued write has landed, which is why it need not know
     *   which keys were pending: `SharedPreferences` keeps one in-memory map per file, the earlier
     *   `apply()` has already published into it, and `commit()` writes that whole map.
     *
     * Flushing here rather than switching `saveTickers` to `commit = true` is deliberate: that
     * setter is on the ordinary hot path (every add, every fetch) and some callers are on the main
     * thread, so swapping it would trade a truncated restore for an ANR. Both import callers — the
     * data service and the panel's `withContext(Dispatchers.IO)` — are off the main thread, so the
     * synchronous write is free here.
     */
    @SuppressLint("ApplySharedPref")
    private fun flushForRestore(context: Context) {
        // Every prefs file the restore spans, flushed whole. The widget files re-commit harmlessly.
        val files = buildList {
            add(AppPreferences.PREFS_NAME)
            widgetIds().forEach { add("$WIDGET_PREFS_PREFIX$it") }
        }
        for (name in files) {
            runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit() }
        }
    }

    private suspend fun importCategory(context: Context, cat: Cat, json: String): String = when (cat) {
        Cat.GENERAL -> importGeneral(json).toString()
        Cat.APPEARANCE -> importAppearance(json).toString()
        Cat.WIDGETS -> importWidgets(context, json).toString()
        Cat.PORTFOLIO -> {
            val portfolio = portfolioSerializer.deserializePortfolio(json)
            // Awaited, not launched — the Room write must have landed before we report success.
            stocksProvider.addPortfolioNow(portfolio)
            portfolio.size.toString()
        }
    }

    private fun importGeneral(json: String): Int {
        val o = JSONObject(json)
        var n = 0
        if (o.has("updateInterval")) {
            appPreferences.updateIntervalPref = o.getInt("updateInterval")
            n++
        }
        if (o.has("startTime")) {
            appPreferences.setStartTime(o.getString("startTime"))
            n++
        }
        if (o.has("endTime")) {
            appPreferences.setEndTime(o.getString("endTime"))
            n++
        }
        if (o.has("updateDays")) {
            val arr = o.getJSONArray("updateDays")
            appPreferences.setUpdateDays((0 until arr.length()).map { arr.getInt(it) }.toSet())
            n++
        }
        if (o.has("autoSort")) {
            appPreferences.setAutoSort(o.getBoolean("autoSort"))
            n++
        }
        if (o.has("roundToTwoDp")) {
            appPreferences.setRoundToTwoDecimalPlaces(o.getBoolean("roundToTwoDp"))
            n++
        }
        if (o.has("notificationAlerts")) {
            appPreferences.setNotificationAlerts(o.getBoolean("notificationAlerts"))
            n++
        }
        if (o.has("theme")) {
            appPreferences.themePref = o.getInt("theme")
            n++
        }
        return n
    }

    private fun importAppearance(json: String): Int {
        val o = JSONObject(json)
        var n = 0
        o.optJSONObject("pages")?.let { pages ->
            for (page in ThemePage.entries) {
                val p = pages.optJSONObject(page.key) ?: continue
                val keys = p.keys()
                while (keys.hasNext()) {
                    val attr = keys.next()
                    val e = p.optJSONObject(attr)
                    if (e != null && applyPageAttr(page, attr, e)) n++
                }
            }
        }
        // After the pages: setPageColour polluted the recents, so restore these last.
        if (o.has("dynamicColour")) {
            appPreferences.uiUseDynamicColour = o.getBoolean("dynamicColour")
            n++
        }
        if (o.has("language")) {
            appPreferences.uiLanguageTag = o.getString("language")
            n++
        }
        if (o.has("quoteLayout")) {
            appPreferences.uiQuoteLayout = o.getInt("quoteLayout")
            n++
        }
        o.optJSONArray("recentColours")?.let { arr ->
            appPreferences.uiRecentColours = (0 until arr.length()).map { arr.getInt(it) }
            n++
        }
        return n
    }

    /** Applies one type-tagged page attribute; false when the tag is unknown. */
    private fun applyPageAttr(page: ThemePage, attr: String, e: JSONObject): Boolean {
        when (e.optString("t")) {
            "c" -> appPreferences.setPageColour(page, attr, e.getInt("v"))
            "f" -> appPreferences.setPageFont(page, attr, e.getString("v"))
            "w" -> appPreferences.setPageWeight(page, attr, e.getInt("v"))
            "z" -> appPreferences.setPageSize(page, attr, e.getDouble("v").toFloat())
            else -> return false
        }
        return true
    }

    private fun importFonts(context: Context, files: Map<String, ByteArray>): Int {
        val dir = FontManager.fontsDir(context).apply { mkdirs() }
        var n = 0
        for ((name, bytes) in files) {
            val safe = name.takeIf { it.startsWith(FONTS_DIR_ENTRY) }
                ?.removePrefix(FONTS_DIR_ENTRY)?.substringAfterLast('/')
            if (!safe.isNullOrEmpty() && runCatching { File(dir, safe).writeBytes(bytes) }.isSuccess) n++
        }
        return n
    }

    @SuppressLint("ApplySharedPref")
    private fun importWidgets(context: Context, json: String): Int {
        val o = JSONObject(json)
        var n = 0
        val ids = o.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val w = o.optJSONObject(id) ?: continue
            val ed = context.getSharedPreferences("$WIDGET_PREFS_PREFIX$id", Context.MODE_PRIVATE).edit()
            val keys = w.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                w.optJSONObject(k)?.let { putTagged(ed, k, it) }
            }
            ed.commit()
            n++
        }
        return n
    }

    /** Writes one type-tagged value into a SharedPreferences editor; unknown tags are skipped. */
    private fun putTagged(ed: SharedPreferences.Editor, k: String, e: JSONObject) {
        when (e.optString("t")) {
            "b" -> ed.putBoolean(k, e.optBoolean("v"))
            "i" -> ed.putInt(k, e.optInt("v"))
            "l" -> ed.putLong(k, e.optLong("v"))
            "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
            "s" -> ed.putString(k, e.optString("v"))
            "ss" -> {
                val arr = e.optJSONArray("v") ?: JSONArray()
                ed.putStringSet(k, (0 until arr.length()).map { arr.optString(it) }.toSet())
            }
        }
    }

    /** Reads every ZIP entry into memory keyed by entry name. */
    private fun readZip(zip: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zis.copyTo(buf)
                    out[e.name] = buf.toByteArray()
                }
                e = zis.nextEntry
            }
        }
        return out
    }

    // ---- Attribute enumeration (mirrors the UI settings page) -------------------------------------

    private val COLOUR_BASE = listOf(
        AppPreferences.ATTR_ACCENT,
        AppPreferences.ATTR_BACKGROUND,
        AppPreferences.ATTR_TEXT,
        AppPreferences.ATTR_GAIN,
        AppPreferences.ATTR_LOSS,
        AppPreferences.ATTR_HEADING_COLOUR,
        AppPreferences.ATTR_BODY_COLOUR,
    )
    private val FONT_BASE = listOf(AppPreferences.ATTR_HEADING_FONT, AppPreferences.ATTR_BODY_FONT)
    private val WEIGHT_BASE = listOf(AppPreferences.ATTR_HEADING_WEIGHT, AppPreferences.ATTR_BODY_WEIGHT)
    private val SIZE_BASE = listOf(AppPreferences.ATTR_HEADING_SIZE, AppPreferences.ATTR_BODY_SIZE)

    /** The page's base attributes plus, on the quote-detail page, the per-element `<ELEM>_<suffix>` ones. */
    private fun attrs(page: ThemePage, base: List<String>, suffix: String): List<String> =
        if (page == ThemePage.QUOTE_DETAIL) base + QuoteElement.entries.map { "${it.name}_$suffix" } else base
}
