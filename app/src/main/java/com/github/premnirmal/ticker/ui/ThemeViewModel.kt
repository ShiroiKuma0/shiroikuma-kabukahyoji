package com.github.premnirmal.ticker.ui

import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Backs [com.github.premnirmal.ticker.ui.PageThemeProvider]: exposes the app theme mode, the
 * `themeVersion` tick (bumped on any "白い熊 株価表示 UI" write) and the resolved per-page / raw
 * per-element theming values.
 */
class ThemeViewModel constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val themePref: Flow<SelectedTheme>
        get() = appPreferences.themePrefFlow.map { pref ->
            when (pref) {
                AppPreferences.LIGHT_THEME -> SelectedTheme.LIGHT
                AppPreferences.DARK_THEME -> SelectedTheme.DARK
                AppPreferences.FOLLOW_SYSTEM_THEME -> SelectedTheme.SYSTEM
                else -> SelectedTheme.SYSTEM
            }
        }

    val themeVersion: Flow<Int>
        get() = appPreferences.themeVersionFlow

    fun useDynamicColour(): Boolean = appPreferences.uiUseDynamicColour

    // Resolved (page override → GLOBAL → default).
    fun resolvedColour(page: ThemePage, attr: String): Int = appPreferences.resolvedColour(page, attr)
    fun resolvedFontToken(page: ThemePage, attr: String): String = appPreferences.resolvedFontToken(page, attr)
    fun resolvedWeight(page: ThemePage, attr: String): Int = appPreferences.resolvedWeight(page, attr)
    fun resolvedSize(page: ThemePage, attr: String): Float = appPreferences.resolvedSize(page, attr)

    // Raw per-element quote-detail overrides (no inheritance).
    fun elementColour(attr: String): Int = appPreferences.getPageColour(ThemePage.QUOTE_DETAIL, attr)
    fun elementFont(attr: String): String = appPreferences.getPageFont(ThemePage.QUOTE_DETAIL, attr)
    fun elementWeight(attr: String): Int = appPreferences.getPageWeight(ThemePage.QUOTE_DETAIL, attr)
    fun elementSize(attr: String): Float = appPreferences.getPageSize(ThemePage.QUOTE_DETAIL, attr)
}
