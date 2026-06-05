package com.github.premnirmal.ticker.ui

import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
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

    // 白い熊 株価表示 UI — runtime theming, observed by BaseActivity.
    val useDynamicColour: Flow<Boolean> = appPreferences.uiUseDynamicColourFlow
    val accentColour: Flow<Int> = appPreferences.uiAccentFlow
    val backgroundColour: Flow<Int> = appPreferences.uiBackgroundFlow
    val textColour: Flow<Int> = appPreferences.uiTextFlow
    val gainColour: Flow<Int> = appPreferences.uiGainFlow
    val lossColour: Flow<Int> = appPreferences.uiLossFlow
    val headingFont: Flow<String> = appPreferences.uiHeadingFontFlow
    val bodyFont: Flow<String> = appPreferences.uiBodyFontFlow
    val headingWeight: Flow<Int> = appPreferences.uiHeadingWeightFlow
    val bodyWeight: Flow<Int> = appPreferences.uiBodyWeightFlow
    val textScale: Flow<Float> = appPreferences.uiTextScaleFlow

    // Per-page theming, used by PageThemeProvider. Bumps on any colour/font/weight/scale write.
    val themeVersion: Flow<Int> = appPreferences.themeVersionFlow

    fun resolvedColour(page: ThemePage, attr: String): Int {
        val value = appPreferences.getPageColour(page, attr)
        return if (value != AppPreferences.COLOUR_UNSET) value
        else appPreferences.getPageColour(ThemePage.GLOBAL, attr)
    }

    fun resolvedFontToken(page: ThemePage, attr: String): String {
        val value = appPreferences.getPageFont(page, attr)
        return if (value != AppPreferences.INHERIT_FONT) value
        else appPreferences.getPageFont(ThemePage.GLOBAL, attr)
    }

    fun resolvedWeight(page: ThemePage, attr: String): Int {
        val value = appPreferences.getPageWeight(page, attr)
        return if (value >= 0) value else appPreferences.getPageWeight(ThemePage.GLOBAL, attr)
    }

    fun resolvedSize(page: ThemePage, attr: String): Float {
        val value = appPreferences.getPageSize(page, attr)
        if (value > 0f) return value
        if (page == ThemePage.GLOBAL) return 1.0f
        val global = appPreferences.getPageSize(ThemePage.GLOBAL, attr)
        return if (global > 0f) global else 1.0f
    }
}
