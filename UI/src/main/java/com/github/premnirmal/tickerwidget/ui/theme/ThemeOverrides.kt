package com.github.premnirmal.tickerwidget.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Runtime colour overrides for the "白い熊 株価表示 UI" customization page.
 *
 * Any field left [Color.Unspecified] keeps the value from the active (dynamic or bundled) scheme.
 * [accent]/[background]/[text] override the Material colour scheme; [gain]/[loss] override the
 * stock change colours that are exposed through [ColourPalette] via CompositionLocals.
 */
data class ThemeColourOverrides(
  val accent: Color = Color.Unspecified,
  val background: Color = Color.Unspecified,
  val text: Color = Color.Unspecified,
  val gain: Color = Color.Unspecified,
  val loss: Color = Color.Unspecified,
)
