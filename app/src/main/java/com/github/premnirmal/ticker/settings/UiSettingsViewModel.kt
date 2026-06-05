package com.github.premnirmal.ticker.settings

import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class UiSettingsViewModel @Inject constructor(
  private val appPreferences: AppPreferences
) : ViewModel() {

  /** Bumps on any colour/font/weight/size/recent write; the screen reads values fresh on change. */
  val version: Flow<Int> = appPreferences.themeVersionFlow

  // ---- Global-only (shown on the hub) ----
  fun themePref(): Int = appPreferences.themePref
  fun setThemePref(value: Int) {
    appPreferences.themePref = value
    appPreferences.bumpThemeVersion()
  }

  fun useDynamicColour(): Boolean = appPreferences.uiUseDynamicColour
  fun setUseDynamicColour(value: Boolean) {
    appPreferences.uiUseDynamicColour = value
    appPreferences.bumpThemeVersion()
  }

  fun recentColours(): List<Int> = appPreferences.uiRecentColours

  // ---- Per-page, per-attribute ----
  fun colour(page: ThemePage, attr: String): Int = appPreferences.getPageColour(page, attr)

  fun setColour(page: ThemePage, attr: String, argb: Int) {
    appPreferences.setPageColour(page, attr, argb)
    appPreferences.addRecentColour(argb)
  }

  fun clearColour(page: ThemePage, attr: String) {
    appPreferences.setPageColour(page, attr, AppPreferences.COLOUR_UNSET)
  }

  fun fontToken(page: ThemePage, attr: String): String = appPreferences.getPageFont(page, attr)

  fun setFont(page: ThemePage, attr: String, token: String) {
    appPreferences.setPageFont(page, attr, token)
  }

  fun weight(page: ThemePage, attr: String): Int = appPreferences.getPageWeight(page, attr)

  fun setWeight(page: ThemePage, attr: String, value: Int) {
    appPreferences.setPageWeight(page, attr, value)
  }

  fun size(page: ThemePage, attr: String): Float = appPreferences.getPageSize(page, attr)

  fun setSize(page: ThemePage, attr: String, value: Float) {
    appPreferences.setPageSize(page, attr, value)
  }

  /** Effective size for the slider to display (page override, else global, else 1.0). */
  fun resolvedSize(page: ThemePage, attr: String): Float {
    val value = appPreferences.getPageSize(page, attr)
    if (value > 0f) return value
    if (page == ThemePage.GLOBAL) return 1.0f
    val global = appPreferences.getPageSize(ThemePage.GLOBAL, attr)
    return if (global > 0f) global else 1.0f
  }
}
