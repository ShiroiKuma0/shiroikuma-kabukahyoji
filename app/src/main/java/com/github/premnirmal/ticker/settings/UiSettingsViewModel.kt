package com.github.premnirmal.ticker.settings

import androidx.lifecycle.ViewModel
import com.github.premnirmal.ticker.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class ColourTarget { ACCENT, BACKGROUND, TEXT, GAIN, LOSS }

enum class FontTarget { HEADING, BODY }

data class UiSettingsState(
  val useDynamicColour: Boolean,
  val themePref: Int,
  val accent: Int,
  val background: Int,
  val text: Int,
  val gain: Int,
  val loss: Int,
  val headingFont: String,
  val bodyFont: String,
  val headingWeight: Int,
  val bodyWeight: Int,
  val textScale: Float,
  val recentColours: List<Int>,
)

@HiltViewModel
class UiSettingsViewModel @Inject constructor(
  private val appPreferences: AppPreferences
) : ViewModel() {

  private val _state = MutableStateFlow(readState())
  val state: StateFlow<UiSettingsState> = _state

  private fun readState() = UiSettingsState(
    useDynamicColour = appPreferences.uiUseDynamicColour,
    themePref = appPreferences.themePref,
    accent = appPreferences.uiAccent,
    background = appPreferences.uiBackground,
    text = appPreferences.uiText,
    gain = appPreferences.uiGain,
    loss = appPreferences.uiLoss,
    headingFont = appPreferences.uiHeadingFont,
    bodyFont = appPreferences.uiBodyFont,
    headingWeight = appPreferences.uiHeadingWeight,
    bodyWeight = appPreferences.uiBodyWeight,
    textScale = appPreferences.uiTextScale,
    recentColours = appPreferences.uiRecentColours,
  )

  private fun refresh() {
    _state.value = readState()
  }

  fun setUseDynamicColour(value: Boolean) {
    appPreferences.uiUseDynamicColour = value
    refresh()
  }

  fun setThemePref(value: Int) {
    appPreferences.themePref = value
    refresh()
  }

  fun colourFor(target: ColourTarget): Int = when (target) {
    ColourTarget.ACCENT -> appPreferences.uiAccent
    ColourTarget.BACKGROUND -> appPreferences.uiBackground
    ColourTarget.TEXT -> appPreferences.uiText
    ColourTarget.GAIN -> appPreferences.uiGain
    ColourTarget.LOSS -> appPreferences.uiLoss
  }

  fun setColour(target: ColourTarget, argb: Int) {
    when (target) {
      ColourTarget.ACCENT -> appPreferences.uiAccent = argb
      ColourTarget.BACKGROUND -> appPreferences.uiBackground = argb
      ColourTarget.TEXT -> appPreferences.uiText = argb
      ColourTarget.GAIN -> appPreferences.uiGain = argb
      ColourTarget.LOSS -> appPreferences.uiLoss = argb
    }
    refresh()
  }

  fun clearColour(target: ColourTarget) = setColour(target, AppPreferences.COLOUR_UNSET)

  fun addRecentColour(argb: Int) {
    appPreferences.addRecentColour(argb)
    refresh()
  }

  fun setFont(target: FontTarget, token: String) {
    when (target) {
      FontTarget.HEADING -> appPreferences.uiHeadingFont = token
      FontTarget.BODY -> appPreferences.uiBodyFont = token
    }
    refresh()
  }

  fun setWeight(target: FontTarget, value: Int) {
    when (target) {
      FontTarget.HEADING -> appPreferences.uiHeadingWeight = value
      FontTarget.BODY -> appPreferences.uiBodyWeight = value
    }
    refresh()
  }

  fun setTextScale(value: Float) {
    appPreferences.uiTextScale = value
    refresh()
  }
}
