package com.github.premnirmal.ticker

import com.github.premnirmal.ticker.network.CrumbStore
import com.github.premnirmal.ticker.settings.PreferenceStore
import com.github.premnirmal.tickerwidget.ui.theme.COLOUR_UNSET
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormat
import java.text.Format
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM

/**
 * Android entry point for the shared [UserPreferences] settings contract and [CrumbStore] crumb
 * persistence, backed by a [PreferenceStore].
 *
 * The read/write logic itself is fully shared in [SharedUserPreferences]; this class only adds the
 * Android-only extras that depend on JVM types — the `java.text` decimal formatters
 * ([selectedDecimalFormat]) and the saved app-version bookkeeping — plus the legacy preference-key
 * and widget-related constants that the rest of the `:app` module still references.
 *
 * Created by premnirmal on 2/26/16.
 */
class AppPreferences constructor(
    store: PreferenceStore,
) : SharedUserPreferences(store) {

    init {
        INSTANCE = this
    }

    val selectedDecimalFormat: Format
        get() = if (roundToTwoDecimalPlaces()) {
            DECIMAL_FORMAT_2DP
        } else {
            DECIMAL_FORMAT
        }

    // ---- 白い熊 株価表示 UI: custom per-page / per-element theming --------------------------------
    // GLOBAL is the base; the other pages inherit and override. Colours use the ARGB [COLOUR_UNSET]
    // sentinel, fonts [INHERIT_FONT], weights [INHERIT_WEIGHT], size multipliers "inherit" = 0f. The
    // five foundation colours + the global heading/body fonts and weights live in flat keys; every
    // other attribute (and all non-global pages) use per-page `UI_PAGE_<page>_<attr>` keys, with the
    // per-element quote-detail overrides keyed under the QUOTE_DETAIL page. Any write bumps
    // [themeVersionFlow] so PageThemeProvider recomputes.

    private val _themeVersion = MutableStateFlow(0)
    val themeVersionFlow: StateFlow<Int> = _themeVersion
    private fun bumpThemeVersion() {
        _themeVersion.value += 1
    }

    var uiUseDynamicColour: Boolean
        get() = store.getBoolean(UI_DYNAMIC_COLOUR, true)
        set(value) {
            store.setBoolean(UI_DYNAMIC_COLOUR, value)
            bumpThemeVersion()
        }

    // Foundation colours = the GLOBAL page's accent/background/text/gain/loss.
    var uiAccent: Int
        get() = store.getInt(UI_ACCENT, COLOUR_UNSET)
        set(value) { store.setInt(UI_ACCENT, value) }
    var uiBackground: Int
        get() = store.getInt(UI_BACKGROUND, COLOUR_UNSET)
        set(value) { store.setInt(UI_BACKGROUND, value) }
    var uiText: Int
        get() = store.getInt(UI_TEXT, COLOUR_UNSET)
        set(value) { store.setInt(UI_TEXT, value) }
    var uiGain: Int
        get() = store.getInt(UI_GAIN, COLOUR_UNSET)
        set(value) { store.setInt(UI_GAIN, value) }
    var uiLoss: Int
        get() = store.getInt(UI_LOSS, COLOUR_UNSET)
        set(value) { store.setInt(UI_LOSS, value) }

    // Global heading/body fonts + weights.
    var uiHeadingFont: String
        get() = store.getString(UI_HEADING_FONT, "").orEmpty()
        set(value) { store.setString(UI_HEADING_FONT, value) }
    var uiBodyFont: String
        get() = store.getString(UI_BODY_FONT, "").orEmpty()
        set(value) { store.setString(UI_BODY_FONT, value) }
    var uiHeadingWeight: Int
        get() = store.getInt(UI_HEADING_WEIGHT, 0)
        set(value) { store.setInt(UI_HEADING_WEIGHT, value) }
    var uiBodyWeight: Int
        get() = store.getInt(UI_BODY_WEIGHT, 0)
        set(value) { store.setInt(UI_BODY_WEIGHT, value) }

    var uiRecentColours: List<Int>
        get() = store.getString(UI_RECENT_COLOURS, "").orEmpty()
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        set(value) { store.setString(UI_RECENT_COLOURS, value.joinToString(",")) }

    fun addRecentColour(argb: Int) {
        uiRecentColours = (listOf(argb) + uiRecentColours).distinct().take(RECENT_COLOURS_MAX)
    }

    /** Persisted app language tag ("" = system). Source of truth, re-applied on launch in StocksApp. */
    var uiLanguageTag: String
        get() = store.getString(UI_LANGUAGE_TAG, "").orEmpty()
        set(value) { store.setString(UI_LANGUAGE_TAG, value) }

    // Quote-detail layout: default two-pane, or graph pinned on top with stats/news split below.
    // Toggled from the UI page or by long-pressing the chart; observed by the quote-detail hosts.
    private val _uiQuoteLayout by lazy { MutableStateFlow(store.getInt(UI_QUOTE_LAYOUT, QUOTE_LAYOUT_DEFAULT)) }
    val uiQuoteLayoutFlow: StateFlow<Int> get() = _uiQuoteLayout
    var uiQuoteLayout: Int
        get() = _uiQuoteLayout.value
        set(value) {
            _uiQuoteLayout.value = value
            store.setInt(UI_QUOTE_LAYOUT, value)
            bumpThemeVersion()
        }

    // ---- Per-page keyed access ------------------------------------------------------------------

    private fun pageKey(page: ThemePage, attr: String) = "$PAGE_KEY_PREFIX${page.key}_$attr"

    private fun globalColour(attr: String): Int = when (attr) {
        ATTR_ACCENT -> uiAccent
        ATTR_BACKGROUND -> uiBackground
        ATTR_TEXT -> uiText
        ATTR_GAIN -> uiGain
        ATTR_LOSS -> uiLoss
        else -> COLOUR_UNSET
    }

    private fun setGlobalColour(attr: String, value: Int) {
        when (attr) {
            ATTR_ACCENT -> uiAccent = value
            ATTR_BACKGROUND -> uiBackground = value
            ATTR_TEXT -> uiText = value
            ATTR_GAIN -> uiGain = value
            ATTR_LOSS -> uiLoss = value
        }
    }

    /** Raw colour for (page, attr): the page's own value, no inheritance. Element attrs live here too. */
    fun getPageColour(page: ThemePage, attr: String): Int =
        if (page == ThemePage.GLOBAL && attr in FIELD_COLOUR_ATTRS) {
            globalColour(attr)
        } else {
            store.getInt(pageKey(page, attr), COLOUR_UNSET)
        }

    fun setPageColour(page: ThemePage, attr: String, value: Int) {
        if (page == ThemePage.GLOBAL && attr in FIELD_COLOUR_ATTRS) {
            setGlobalColour(attr, value)
        } else {
            store.setInt(pageKey(page, attr), value)
        }
        if (value != COLOUR_UNSET) addRecentColour(value)
        bumpThemeVersion()
    }

    private fun globalFont(attr: String): String = when (attr) {
        ATTR_HEADING_FONT -> uiHeadingFont
        ATTR_BODY_FONT -> uiBodyFont
        else -> ""
    }

    private fun setGlobalFont(attr: String, value: String) {
        when (attr) {
            ATTR_HEADING_FONT -> uiHeadingFont = value
            ATTR_BODY_FONT -> uiBodyFont = value
        }
    }

    fun getPageFont(page: ThemePage, attr: String): String =
        if (page == ThemePage.GLOBAL && attr in GLOBAL_FONT_ATTRS) {
            globalFont(attr)
        } else {
            store.getString(pageKey(page, attr), INHERIT_FONT) ?: INHERIT_FONT
        }

    fun setPageFont(page: ThemePage, attr: String, value: String) {
        if (page == ThemePage.GLOBAL && attr in GLOBAL_FONT_ATTRS) {
            setGlobalFont(attr, value)
        } else {
            store.setString(pageKey(page, attr), value)
        }
        bumpThemeVersion()
    }

    private fun globalWeight(attr: String): Int = when (attr) {
        ATTR_HEADING_WEIGHT -> uiHeadingWeight
        ATTR_BODY_WEIGHT -> uiBodyWeight
        else -> 0
    }

    private fun setGlobalWeight(attr: String, value: Int) {
        when (attr) {
            ATTR_HEADING_WEIGHT -> uiHeadingWeight = value
            ATTR_BODY_WEIGHT -> uiBodyWeight = value
        }
    }

    fun getPageWeight(page: ThemePage, attr: String): Int =
        if (page == ThemePage.GLOBAL && attr in GLOBAL_WEIGHT_ATTRS) {
            globalWeight(attr)
        } else {
            store.getInt(pageKey(page, attr), INHERIT_WEIGHT)
        }

    fun setPageWeight(page: ThemePage, attr: String, value: Int) {
        if (page == ThemePage.GLOBAL && attr in GLOBAL_WEIGHT_ATTRS) {
            setGlobalWeight(attr, value)
        } else {
            store.setInt(pageKey(page, attr), value)
        }
        bumpThemeVersion()
    }

    // Size multiplier (Float) is stored as raw int bits — PreferenceStore has no Float accessor.
    fun getPageSize(page: ThemePage, attr: String): Float =
        Float.fromBits(store.getInt(pageKey(page, attr), INHERIT_SCALE_BITS))

    fun setPageSize(page: ThemePage, attr: String, value: Float) {
        store.setInt(pageKey(page, attr), value.toRawBits())
        bumpThemeVersion()
    }

    // ---- Resolved values (page's own, else GLOBAL, else the built-in default) -------------------

    fun resolvedColour(page: ThemePage, attr: String): Int {
        val own = getPageColour(page, attr)
        if (own != COLOUR_UNSET) return own
        return if (page != ThemePage.GLOBAL) getPageColour(ThemePage.GLOBAL, attr) else COLOUR_UNSET
    }

    fun resolvedFontToken(page: ThemePage, attr: String): String {
        val own = getPageFont(page, attr)
        if (own != INHERIT_FONT) return own
        return if (page != ThemePage.GLOBAL) getPageFont(ThemePage.GLOBAL, attr) else ""
    }

    fun resolvedWeight(page: ThemePage, attr: String): Int {
        val own = getPageWeight(page, attr)
        if (own != INHERIT_WEIGHT) return own
        return if (page != ThemePage.GLOBAL) getPageWeight(ThemePage.GLOBAL, attr) else 0
    }

    fun resolvedSize(page: ThemePage, attr: String): Float {
        val own = getPageSize(page, attr)
        if (own > 0f) return own
        if (page == ThemePage.GLOBAL) return 1.0f
        val global = getPageSize(ThemePage.GLOBAL, attr)
        return if (global > 0f) global else 1.0f
    }

    companion object {

        private lateinit var INSTANCE: AppPreferences

        fun List<String>.toCommaSeparatedString(): String {
            val builder = StringBuilder()
            for (string in this) {
                builder.append(string)
                builder.append(",")
            }
            val length = builder.length
            if (length > 1) {
                builder.deleteCharAt(length - 1)
            }
            return builder.toString()
        }

        const val SORTED_STOCK_LIST = "SORTED_STOCK_LIST"
        const val PREFS_NAME = "com.github.premnirmal.ticker"
        const val START_TIME = "START_TIME"
        const val END_TIME = "END_TIME"
        const val UPDATE_DAYS = "UPDATE_DAYS"
        const val TUTORIAL_SHOWN = "TUTORIAL_SHOWN"
        const val SETTING_AUTOSORT = "SETTING_AUTOSORT"
        const val SETTING_HIDE_HEADER = "SETTING_HIDE_HEADER"
        const val SETTING_ROUND_TWO_DP = "SETTING_ROUND_TWO_DP"
        const val SETTING_NOTIFICATION_ALERTS = "SETTING_NOTIFICATION_ALERTS"
        const val PREFERENCE_SHOWN_ADD_REMOVE_TOOLTIP = "PREFERENCE_SHOWN_ADD_REMOVE_TOOLTIP"

        const val WIDGET_BG = "WIDGET_BG"
        const val WIDGET_REFRESHING = "WIDGET_REFRESHING"
        const val TEXT_COLOR = "TEXT_COLOR"
        const val UPDATE_INTERVAL = "UPDATE_INTERVAL"
        const val LAYOUT_TYPE = "LAYOUT_TYPE"
        const val WIDGET_SIZE = "WIDGET_SIZE"

        @Deprecated("will be removed in future version")
        const val FONT_SIZE = "FONT_SIZE"
        const val BOLD_CHANGE = "BOLD_CHANGE"
        const val SHOW_CURRENCY = "SHOW_CURRENCY"
        const val SHOW_REFRESH = "SHOW_REFRESH"
        const val PERCENT = "PERCENT"
        const val CRUMB = "CRUMB"
        const val APP_VERSION_CODE = "APP_VERSION_CODE"
        const val APP_THEME = "APP_THEME"
        const val SYSTEM = 0
        const val TRANSPARENT = 1
        const val TRANSLUCENT = 2
        const val LIGHT = 1
        const val DARK = 2
        const val LIGHT_THEME = 0
        const val DARK_THEME = 1
        const val FOLLOW_SYSTEM_THEME = 2

        // 白い熊 株価表示 UI custom theming — flat (global foundation) keys
        const val UI_ACCENT = "UI_ACCENT"
        const val UI_BACKGROUND = "UI_BACKGROUND"
        const val UI_TEXT = "UI_TEXT"
        const val UI_GAIN = "UI_GAIN"
        const val UI_LOSS = "UI_LOSS"
        const val UI_HEADING_FONT = "UI_HEADING_FONT"
        const val UI_BODY_FONT = "UI_BODY_FONT"
        const val UI_HEADING_WEIGHT = "UI_HEADING_WEIGHT"
        const val UI_BODY_WEIGHT = "UI_BODY_WEIGHT"
        const val UI_DYNAMIC_COLOUR = "UI_DYNAMIC_COLOUR"
        const val UI_RECENT_COLOURS = "UI_RECENT_COLOURS"
        const val UI_LANGUAGE_TAG = "UI_LANGUAGE_TAG"
        const val UI_QUOTE_LAYOUT = "UI_QUOTE_LAYOUT"
        const val QUOTE_LAYOUT_DEFAULT = 0
        const val QUOTE_LAYOUT_GRAPH_TOP = 1
        const val PAGE_KEY_PREFIX = "UI_PAGE_"
        private const val RECENT_COLOURS_MAX = 18

        // Per-page / per-category attribute keys
        const val ATTR_ACCENT = "ACCENT"
        const val ATTR_BACKGROUND = "BACKGROUND"
        const val ATTR_TEXT = "TEXT"
        const val ATTR_GAIN = "GAIN"
        const val ATTR_LOSS = "LOSS"
        const val ATTR_HEADING_FONT = "HEADING_FONT"
        const val ATTR_BODY_FONT = "BODY_FONT"
        const val ATTR_HEADING_WEIGHT = "HEADING_WEIGHT"
        const val ATTR_BODY_WEIGHT = "BODY_WEIGHT"
        const val ATTR_HEADING_COLOUR = "HEADING_COLOUR"
        const val ATTR_BODY_COLOUR = "BODY_COLOUR"
        const val ATTR_HEADING_SIZE = "HEADING_SIZE"
        const val ATTR_BODY_SIZE = "BODY_SIZE"

        // Inherit sentinels
        const val INHERIT_FONT = "@inherit"
        const val INHERIT_WEIGHT = -1
        const val INHERIT_SCALE = 0f
        private val INHERIT_SCALE_BITS = INHERIT_SCALE.toRawBits()

        private val FIELD_COLOUR_ATTRS = setOf(ATTR_ACCENT, ATTR_BACKGROUND, ATTR_TEXT, ATTR_GAIN, ATTR_LOSS)
        private val GLOBAL_FONT_ATTRS = setOf(ATTR_HEADING_FONT, ATTR_BODY_FONT)
        private val GLOBAL_WEIGHT_ATTRS = setOf(ATTR_HEADING_WEIGHT, ATTR_BODY_WEIGHT)

        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(MEDIUM)
        val AXIS_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL dd-yyyy")

        val DECIMAL_FORMAT: Format = DecimalFormat("#,##0.00##")
        val DECIMAL_FORMAT_2DP: Format = DecimalFormat("#,##0.00")

        val SELECTED_DECIMAL_FORMAT: Format
            get() = if (::INSTANCE.isInitialized) { INSTANCE.selectedDecimalFormat } else DECIMAL_FORMAT

        val SELECTED_THEME: SelectedTheme
            get() = if (::INSTANCE.isInitialized) { INSTANCE.selectedTheme } else SelectedTheme.SYSTEM
    }
}
