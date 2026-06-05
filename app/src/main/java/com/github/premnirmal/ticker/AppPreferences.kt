package com.github.premnirmal.ticker

import android.content.SharedPreferences
import android.os.Build
import android.os.Parcelable
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode
import androidx.core.content.edit
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import java.text.DecimalFormat
import java.text.Format
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Created by premnirmal on 2/26/16.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val sharedPreferences: SharedPreferences,
) {

    init {
        INSTANCE = this
    }

    fun getLastSavedVersionCode(): Int = sharedPreferences.getInt(APP_VERSION_CODE, -1)
    fun saveVersionCode(code: Int) {
        sharedPreferences.edit {
            putInt(APP_VERSION_CODE, code)
        }
    }

    val updateIntervalMs: Long
        get() {
            return when (sharedPreferences.getInt(UPDATE_INTERVAL, 1)) {
                0 -> 5 * 60 * 1000L
                1 -> 15 * 60 * 1000L
                2 -> 30 * 60 * 1000L
                3 -> 45 * 60 * 1000L
                4 -> 60 * 60 * 1000L
                else -> 15 * 60 * 1000L
            }
        }

    val selectedDecimalFormat: Format
        get() = if (roundToTwoDecimalPlaces()) {
            DECIMAL_FORMAT_2DP
        } else {
            DECIMAL_FORMAT
        }

    fun parseTime(time: String): Time {
        val split = time.split(":".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val times = intArrayOf(split[0].toInt(), split[1].toInt())
        return Time(times[0], times[1])
    }

    fun setStartTime(time: String) {
        sharedPreferences.edit {
            putString(START_TIME, time)
        }
    }

    fun setEndTime(time: String) {
        sharedPreferences.edit {
            putString(END_TIME, time)
        }
    }

    fun startTime(): Time {
        val startTimeString = sharedPreferences.getString(START_TIME, "09:30")!!
        return parseTime(startTimeString)
    }

    fun endTime(): Time {
        val endTimeString = sharedPreferences.getString(END_TIME, "16:00")!!
        return parseTime(endTimeString)
    }

    fun updateDaysRaw(): Set<String> {
        val defaultSet = setOf("1", "2", "3", "4", "5")
        var selectedDays = sharedPreferences.getStringSet(UPDATE_DAYS, defaultSet)!!
        if (selectedDays.isEmpty()) {
            selectedDays = defaultSet
        }
        return selectedDays
    }

    fun setUpdateDays(selected: Set<String>) {
        sharedPreferences.edit {
            putStringSet(UPDATE_DAYS, selected)
        }
    }

    fun updateDays(): Set<DayOfWeek> {
        val selectedDays = updateDaysRaw()
        return selectedDays.map { DayOfWeek.of(it.toInt()) }
            .toSet()
    }

    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing

    private val _isRefreshing = MutableStateFlow(sharedPreferences.getBoolean(WIDGET_REFRESHING, false))

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
        sharedPreferences.edit {
            putBoolean(WIDGET_REFRESHING, refreshing)
        }
    }

    fun setCrumb(crumb: String?) {
        sharedPreferences.edit { putString(CRUMB, crumb) }
    }

    fun getCrumb(): String? {
        return sharedPreferences.getString(CRUMB, null)
    }

    fun tutorialShown(): Boolean {
        return sharedPreferences.getBoolean(TUTORIAL_SHOWN, false)
    }

    fun setTutorialShown(shown: Boolean) {
        sharedPreferences.edit {
            putBoolean(TUTORIAL_SHOWN, shown)
        }
    }

    fun shouldPromptRate(): Boolean = Random.nextInt(0, 10) % 3 == 0

    fun roundToTwoDecimalPlaces(): Boolean = sharedPreferences.getBoolean(SETTING_ROUND_TWO_DP, true)

    fun setRoundToTwoDecimalPlaces(round: Boolean) {
        sharedPreferences.edit {
            putBoolean(SETTING_ROUND_TWO_DP, round)
        }
    }

    fun notificationAlerts(): Boolean = sharedPreferences.getBoolean(SETTING_NOTIFICATION_ALERTS, true)

    fun setNotificationAlerts(set: Boolean) {
        sharedPreferences.edit {
            putBoolean(SETTING_NOTIFICATION_ALERTS, set)
        }
    }

    private val _themePref = MutableStateFlow(sharedPreferences.getInt(APP_THEME, FOLLOW_SYSTEM_THEME))

    val themePrefFlow: Flow<Int> = _themePref

    val selectedTheme: SelectedTheme
        get() = when (_themePref.value) {
            LIGHT_THEME -> SelectedTheme.LIGHT
            DARK_THEME -> SelectedTheme.DARK
            else -> SelectedTheme.SYSTEM
        }

    var themePref: Int
        get() = _themePref.value.coerceIn(0, 2)
        set(value) {
            _themePref.value = value
            sharedPreferences.edit { putInt(APP_THEME, value) }
        }

    var updateIntervalPref: Int
        get() = sharedPreferences.getInt(UPDATE_INTERVAL, 1).coerceIn(0, 4)
        set(value) {
            sharedPreferences.edit {
                putInt(UPDATE_INTERVAL, value)
            }
        }

    @NightMode val nightMode: Int
        get() = when (themePref) {
            LIGHT_THEME -> AppCompatDelegate.MODE_NIGHT_NO
            DARK_THEME -> AppCompatDelegate.MODE_NIGHT_YES
            FOLLOW_SYSTEM_THEME -> {
                if (supportSystemNightMode) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            }
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }

    private val supportSystemNightMode: Boolean
        get() {
            return (
                Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.P && "xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) ||
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.P && "samsung".equals(Build.MANUFACTURER, ignoreCase = true)
                )
        }

    private val _showAddRemoveTooltip = MutableStateFlow(
        sharedPreferences.getInt(PREFERENCE_SHOWN_ADD_REMOVE_TOOLTIP, 0) > 5
    )

    val showAddRemoveTooltip: Flow<Boolean> = _showAddRemoveTooltip

    fun setAddRemoveTooltipShown() {
        val count = sharedPreferences.getInt(PREFERENCE_SHOWN_ADD_REMOVE_TOOLTIP, 0) + 1
        sharedPreferences.edit { putInt(PREFERENCE_SHOWN_ADD_REMOVE_TOOLTIP, count) }
        _showAddRemoveTooltip.value = count > 5
    }

    // ---- 白い熊 株価表示 UI (custom theming) -------------------------------------------------

    private val _uiUseDynamicColour = MutableStateFlow(sharedPreferences.getBoolean(UI_DYNAMIC_COLOUR, true))
    val uiUseDynamicColourFlow: Flow<Boolean> = _uiUseDynamicColour
    var uiUseDynamicColour: Boolean
        get() = _uiUseDynamicColour.value
        set(value) {
            _uiUseDynamicColour.value = value
            sharedPreferences.edit { putBoolean(UI_DYNAMIC_COLOUR, value) }
        }

    private fun colourFlow(key: String) = MutableStateFlow(sharedPreferences.getInt(key, COLOUR_UNSET))

    private val _uiAccent = colourFlow(UI_ACCENT)
    val uiAccentFlow: Flow<Int> = _uiAccent
    var uiAccent: Int
        get() = _uiAccent.value
        set(value) {
            _uiAccent.value = value
            sharedPreferences.edit { putInt(UI_ACCENT, value) }
        }

    private val _uiBackground = colourFlow(UI_BACKGROUND)
    val uiBackgroundFlow: Flow<Int> = _uiBackground
    var uiBackground: Int
        get() = _uiBackground.value
        set(value) {
            _uiBackground.value = value
            sharedPreferences.edit { putInt(UI_BACKGROUND, value) }
        }

    private val _uiText = colourFlow(UI_TEXT)
    val uiTextFlow: Flow<Int> = _uiText
    var uiText: Int
        get() = _uiText.value
        set(value) {
            _uiText.value = value
            sharedPreferences.edit { putInt(UI_TEXT, value) }
        }

    private val _uiGain = colourFlow(UI_GAIN)
    val uiGainFlow: Flow<Int> = _uiGain
    var uiGain: Int
        get() = _uiGain.value
        set(value) {
            _uiGain.value = value
            sharedPreferences.edit { putInt(UI_GAIN, value) }
        }

    private val _uiLoss = colourFlow(UI_LOSS)
    val uiLossFlow: Flow<Int> = _uiLoss
    var uiLoss: Int
        get() = _uiLoss.value
        set(value) {
            _uiLoss.value = value
            sharedPreferences.edit { putInt(UI_LOSS, value) }
        }

    private val _uiHeadingFont = MutableStateFlow(sharedPreferences.getString(UI_HEADING_FONT, "").orEmpty())
    val uiHeadingFontFlow: Flow<String> = _uiHeadingFont
    var uiHeadingFont: String
        get() = _uiHeadingFont.value
        set(value) {
            _uiHeadingFont.value = value
            sharedPreferences.edit { putString(UI_HEADING_FONT, value) }
        }

    private val _uiBodyFont = MutableStateFlow(sharedPreferences.getString(UI_BODY_FONT, "").orEmpty())
    val uiBodyFontFlow: Flow<String> = _uiBodyFont
    var uiBodyFont: String
        get() = _uiBodyFont.value
        set(value) {
            _uiBodyFont.value = value
            sharedPreferences.edit { putString(UI_BODY_FONT, value) }
        }

    private val _uiHeadingWeight = MutableStateFlow(sharedPreferences.getInt(UI_HEADING_WEIGHT, 0))
    val uiHeadingWeightFlow: Flow<Int> = _uiHeadingWeight
    var uiHeadingWeight: Int
        get() = _uiHeadingWeight.value
        set(value) {
            _uiHeadingWeight.value = value
            sharedPreferences.edit { putInt(UI_HEADING_WEIGHT, value) }
        }

    private val _uiBodyWeight = MutableStateFlow(sharedPreferences.getInt(UI_BODY_WEIGHT, 0))
    val uiBodyWeightFlow: Flow<Int> = _uiBodyWeight
    var uiBodyWeight: Int
        get() = _uiBodyWeight.value
        set(value) {
            _uiBodyWeight.value = value
            sharedPreferences.edit { putInt(UI_BODY_WEIGHT, value) }
        }

    private val _uiTextScale = MutableStateFlow(sharedPreferences.getFloat(UI_TEXT_SCALE, 1.0f))
    val uiTextScaleFlow: Flow<Float> = _uiTextScale
    var uiTextScale: Float
        get() = _uiTextScale.value
        set(value) {
            _uiTextScale.value = value
            sharedPreferences.edit { putFloat(UI_TEXT_SCALE, value) }
        }

    // Persisted app language tag (source of truth, re-applied on launch so it survives app updates).
    var uiLanguageTag: String
        get() = sharedPreferences.getString(UI_LANGUAGE_TAG, "").orEmpty()
        set(value) {
            sharedPreferences.edit { putString(UI_LANGUAGE_TAG, value) }
        }

    private val _uiQuoteLayout = MutableStateFlow(sharedPreferences.getInt(UI_QUOTE_LAYOUT, QUOTE_LAYOUT_DEFAULT))
    val uiQuoteLayoutFlow: Flow<Int> = _uiQuoteLayout
    var uiQuoteLayout: Int
        get() = _uiQuoteLayout.value
        set(value) {
            _uiQuoteLayout.value = value
            sharedPreferences.edit { putInt(UI_QUOTE_LAYOUT, value) }
            bumpThemeVersion()
        }

    private val _uiRecentColours = MutableStateFlow(
        sharedPreferences.getString(UI_RECENT_COLOURS, "").orEmpty()
            .split(',').mapNotNull { it.trim().toIntOrNull() }
    )
    val uiRecentColoursFlow: Flow<List<Int>> = _uiRecentColours
    val uiRecentColours: List<Int>
        get() = _uiRecentColours.value

    fun addRecentColour(argb: Int) {
        val updated = (listOf(argb) + _uiRecentColours.value).distinct().take(MAX_RECENT_COLOURS)
        _uiRecentColours.value = updated
        sharedPreferences.edit { putString(UI_RECENT_COLOURS, updated.joinToString(",")) }
        bumpThemeVersion()
    }

    // ---- Per-page theming (inherit from global, override per page) -------------------------------
    // GLOBAL delegates to the fields above; the other pages use keyed prefs with inherit sentinels
    // (COLOUR_UNSET / INHERIT_FONT / INHERIT_WEIGHT / INHERIT_SCALE). Any write bumps the version so
    // observers (BaseActivity uses the per-field flows; PageThemeProvider uses this version) recompute.

    private val _themeVersion = MutableStateFlow(0)
    val themeVersionFlow: Flow<Int> = _themeVersion
    fun bumpThemeVersion() {
        _themeVersion.value = _themeVersion.value + 1
    }

    private fun pageKey(page: ThemePage, attr: String) = "UI_PAGE_${page.key}_$attr"

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

    // The foundation colours are stored in the legacy global fields; per-category colours
    // (heading/body) and all non-global pages use the keyed store.
    private val fieldColourAttrs = setOf(ATTR_ACCENT, ATTR_BACKGROUND, ATTR_TEXT, ATTR_GAIN, ATTR_LOSS)

    fun getPageColour(page: ThemePage, attr: String): Int =
        if (page == ThemePage.GLOBAL && attr in fieldColourAttrs) globalColour(attr)
        else sharedPreferences.getInt(pageKey(page, attr), COLOUR_UNSET)

    fun setPageColour(page: ThemePage, attr: String, value: Int) {
        if (page == ThemePage.GLOBAL && attr in fieldColourAttrs) setGlobalColour(attr, value)
        else sharedPreferences.edit { putInt(pageKey(page, attr), value) }
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
        if (page == ThemePage.GLOBAL) globalFont(attr)
        else sharedPreferences.getString(pageKey(page, attr), INHERIT_FONT) ?: INHERIT_FONT

    fun setPageFont(page: ThemePage, attr: String, value: String) {
        if (page == ThemePage.GLOBAL) setGlobalFont(attr, value)
        else sharedPreferences.edit { putString(pageKey(page, attr), value) }
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
        if (page == ThemePage.GLOBAL) globalWeight(attr)
        else sharedPreferences.getInt(pageKey(page, attr), INHERIT_WEIGHT)

    fun setPageWeight(page: ThemePage, attr: String, value: Int) {
        if (page == ThemePage.GLOBAL) setGlobalWeight(attr, value)
        else sharedPreferences.edit { putInt(pageKey(page, attr), value) }
        bumpThemeVersion()
    }

    // Per-category text size multiplier (heading/body), keyed for every page. INHERIT_SCALE = inherit.
    fun getPageSize(page: ThemePage, attr: String): Float =
        sharedPreferences.getFloat(pageKey(page, attr), INHERIT_SCALE)

    fun setPageSize(page: ThemePage, attr: String, value: Float) {
        sharedPreferences.edit { putFloat(pageKey(page, attr), value) }
        bumpThemeVersion()
    }

    @Parcelize
    data class Time(
        val hour: Int,
        val minute: Int
    ) : Parcelable

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

        // 白い熊 株価表示 UI custom theming
        const val COLOUR_UNSET = Int.MIN_VALUE
        const val UI_DYNAMIC_COLOUR = "UI_DYNAMIC_COLOUR"
        const val UI_ACCENT = "UI_ACCENT"
        const val UI_BACKGROUND = "UI_BACKGROUND"
        const val UI_TEXT = "UI_TEXT"
        const val UI_GAIN = "UI_GAIN"
        const val UI_LOSS = "UI_LOSS"
        const val UI_HEADING_FONT = "UI_HEADING_FONT"
        const val UI_BODY_FONT = "UI_BODY_FONT"
        const val UI_HEADING_WEIGHT = "UI_HEADING_WEIGHT"
        const val UI_BODY_WEIGHT = "UI_BODY_WEIGHT"
        const val UI_TEXT_SCALE = "UI_TEXT_SCALE"
        const val UI_RECENT_COLOURS = "UI_RECENT_COLOURS"
        const val MAX_RECENT_COLOURS = 12

        // Per-page theming attributes + inherit sentinels
        const val ATTR_ACCENT = "ACCENT"
        const val ATTR_BACKGROUND = "BACKGROUND"
        const val ATTR_TEXT = "TEXT"
        const val ATTR_GAIN = "GAIN"
        const val ATTR_LOSS = "LOSS"
        const val ATTR_HEADING_FONT = "HEADING_FONT"
        const val ATTR_BODY_FONT = "BODY_FONT"
        const val ATTR_HEADING_WEIGHT = "HEADING_WEIGHT"
        const val ATTR_BODY_WEIGHT = "BODY_WEIGHT"
        const val ATTR_TEXT_SCALE = "TEXT_SCALE"
        const val ATTR_HEADING_COLOUR = "HEADING_COLOUR"
        const val ATTR_BODY_COLOUR = "BODY_COLOUR"
        const val ATTR_HEADING_SIZE = "HEADING_SIZE"
        const val ATTR_BODY_SIZE = "BODY_SIZE"
        const val UI_QUOTE_LAYOUT = "UI_QUOTE_LAYOUT"
        const val QUOTE_LAYOUT_DEFAULT = 0
        const val QUOTE_LAYOUT_GRAPH_TOP = 1
        const val UI_LANGUAGE_TAG = "UI_LANGUAGE_TAG"
        const val INHERIT_FONT = "@inherit"
        const val INHERIT_WEIGHT = -1
        const val INHERIT_SCALE = 0f

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
