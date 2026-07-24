package com.github.premnirmal.ticker.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for the "白い熊 株価表示 UI" customization page. Reads/writes the per-page (and per-element
 * quote-detail) colour, font, weight and size overrides on [AppPreferences]; every write bumps
 * [themeVersion], which the screen and `PageThemeProvider` observe so edits apply immediately.
 */
class UiSettingsViewModel(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val themeVersion: Flow<Int> = appPreferences.themeVersionFlow

    fun useDynamicColour(): Boolean = appPreferences.uiUseDynamicColour
    fun setUseDynamicColour(value: Boolean) {
        appPreferences.uiUseDynamicColour = value
    }

    fun recentColours(): List<Int> = appPreferences.uiRecentColours

    // Colour / font / weight / size for a (page, attr). Per-element uses the QUOTE_DETAIL page with
    // "<ELEMENT>_<PROP>" attribute keys.
    fun colour(page: ThemePage, attr: String): Int = appPreferences.getPageColour(page, attr)
    fun setColour(page: ThemePage, attr: String, argb: Int) {
        appPreferences.setPageColour(page, attr, argb)
    }

    fun font(page: ThemePage, attr: String): String = appPreferences.getPageFont(page, attr)
    fun setFont(page: ThemePage, attr: String, token: String) {
        appPreferences.setPageFont(page, attr, token)
    }

    fun weight(page: ThemePage, attr: String): Int = appPreferences.getPageWeight(page, attr)
    fun setWeight(page: ThemePage, attr: String, value: Int) {
        appPreferences.setPageWeight(page, attr, value)
    }

    fun setSize(page: ThemePage, attr: String, value: Float) {
        appPreferences.setPageSize(page, attr, value)
    }

    /** Effective size multiplier for the slider (page's own, else GLOBAL, else 1.0). */
    fun resolvedSize(page: ThemePage, attr: String): Float = appPreferences.resolvedSize(page, attr)

    fun language(): String = appPreferences.uiLanguageTag
    fun setLanguage(tag: String) {
        appPreferences.uiLanguageTag = tag
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
