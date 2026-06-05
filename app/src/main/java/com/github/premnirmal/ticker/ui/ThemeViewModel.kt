package com.github.premnirmal.ticker.ui

import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
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
}
