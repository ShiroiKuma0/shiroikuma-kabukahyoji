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
object SettingsExport : KoinComponent {

    /** An exportable settings category; [id] is the ZIP entry name, [labelRes] the checkbox label. */
    enum class Cat(val id: String, @StringRes val labelRes: Int) {
        GENERAL("general", R.string.eim_cat_general),
        APPEARANCE("appearance", R.string.eim_cat_appearance),
        WIDGETS("widgets", R.string.eim_cat_widgets),
        PORTFOLIO("portfolio", R.string.eim_cat_portfolio),
    }

    const val EXPORT_PREFIX = "shiroikuma-kabukahyoji-"
    private const val FORMAT = "kabukahyoji-export"
    private const val VERSION = 1
    private const val EXIM_PREFS = "kabukahyoji_eximport" // device-local; never exported
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
            dir.listFiles().filter {
                it.isFile && it.name?.startsWith(EXPORT_PREFIX) == true && it.name?.endsWith(".zip") == true
            }.maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    /** (message, isWarning) for the "last export" line, queried when the page/panel opens. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        if (exportDir(context) == null) return context.getString(R.string.eim_warn_nodir) to true
        val newest = latestExport(context)
            ?: return context.getString(R.string.eim_warn_none) to true
        return context.getString(R.string.eim_last, fmtTs(newest.lastModified())) to false
    }

    private fun fmtTs(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    fun exportFileName(): String =
        EXPORT_PREFIX + BuildConfig.VERSION_NAME + "-export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ---- Export -----------------------------------------------------------------------------------

    /** Writes a ZIP of the selected categories to [out]. Returns a short human summary. */
    fun export(context: Context, cats: Set<Cat>, out: OutputStream): String {
        val lines = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(JSON_INDENT))

            for (cat in Cat.entries.filter { it in cats }) {
                lines += when (cat) {
                    Cat.GENERAL -> {
                        writeEntry(zip, "${cat.id}.json", exportGeneral())
                        context.getString(cat.labelRes)
                    }
                    Cat.APPEARANCE -> {
                        writeEntry(zip, "${cat.id}.json", exportAppearance())
                        val fonts = exportFonts(context, zip)
                        context.getString(cat.labelRes) + if (fonts > 0) " (+$fonts fonts)" else ""
                    }
                    Cat.WIDGETS -> {
                        writeEntry(zip, "${cat.id}.json", exportWidgets(context))
                        context.getString(cat.labelRes)
                    }
                    Cat.PORTFOLIO -> {
                        val portfolio = stocksProvider.portfolio.value
                        writeEntry(zip, "${cat.id}.json", portfolioSerializer.serializePortfolio(portfolio))
                        context.getString(cat.labelRes) + " (${portfolio.size})"
                    }
                }
            }
        }
        return lines.joinToString("\n")
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
                (0 until arr.length()).mapNotNull { i -> Cat.entries.firstOrNull { it.id == arr.getString(i) } }
            }.getOrNull()
        }
        return fromManifest ?: Cat.entries.filter { files.containsKey("${it.id}.json") }
    }

    /** Applies the selected categories from an export ZIP. Returns a per-category summary. */
    fun import(context: Context, zip: ByteArray, cats: Set<Cat>): String {
        val files = readZip(zip)
        val lines = mutableListOf<String>()
        for (cat in Cat.entries.filter { it in cats }) {
            val json = files["${cat.id}.json"] ?: continue
            val line = runCatching {
                when (cat) {
                    Cat.GENERAL -> "${context.getString(cat.labelRes)}: ${importGeneral(String(json))}"
                    Cat.APPEARANCE -> {
                        val n = importAppearance(String(json))
                        val fonts = importFonts(context, files)
                        "${context.getString(cat.labelRes)}: $n" + if (fonts > 0) " (+$fonts fonts)" else ""
                    }
                    Cat.WIDGETS -> "${context.getString(cat.labelRes)}: ${importWidgets(context, String(json))}"
                    Cat.PORTFOLIO -> {
                        val portfolio = portfolioSerializer.deserializePortfolio(String(json))
                        stocksProvider.addPortfolio(portfolio)
                        "${context.getString(cat.labelRes)}: ${portfolio.size}"
                    }
                }
            }.getOrElse { "${context.getString(cat.labelRes)}: ✗ ${it.message}" }
            lines += line
        }
        return lines.joinToString("\n")
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
