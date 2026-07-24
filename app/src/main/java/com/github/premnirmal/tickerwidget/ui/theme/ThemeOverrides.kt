package com.github.premnirmal.tickerwidget.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

/**
 * Runtime theme overrides for the "白い熊 株価表示 UI" customization page (Phase 2, global scope).
 *
 * Colours are persisted as ARGB [Int]s with the [COLOUR_UNSET] sentinel meaning "keep the active
 * (dynamic/brand) scheme value". [accent]/[background]/[text] override the Material colour scheme;
 * [gain]/[loss] override the shared stock change colours exposed through [SharedColours] via
 * CompositionLocals. The font is a token (see `FontManager`) plus an optional weight.
 */

/** Sentinel ARGB for "no override" — `0x00000001`, an invisible near-transparent value a user never picks. */
const val COLOUR_UNSET: Int = 0x00000001

/** Compose colours for [AppTheme] to apply. [Color.Unspecified] fields are left untouched. */
data class ThemeColourOverrides(
    val accent: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val text: Color = Color.Unspecified,
    val gain: Color = Color.Unspecified,
    val loss: Color = Color.Unspecified,
)

/** [COLOUR_UNSET] → [Color.Unspecified]; any other value → its ARGB [Color]. */
fun Int.toOverrideColour(): Color = if (this == COLOUR_UNSET) Color.Unspecified else Color(this)

/** Copies the given colour-scheme overrides onto a [ColorScheme], leaving unspecified fields as-is. */
fun ColorScheme.withOverrides(o: ThemeColourOverrides): ColorScheme {
    var scheme = this
    if (o.accent.isSpecified) scheme = scheme.copy(primary = o.accent, surfaceTint = o.accent)
    if (o.background.isSpecified) scheme = scheme.copy(background = o.background, surface = o.background)
    if (o.text.isSpecified) scheme = scheme.copy(onBackground = o.text, onSurface = o.text)
    return scheme
}
