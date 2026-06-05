package com.github.premnirmal.ticker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.github.premnirmal.ticker.AppPreferences

/**
 * Per-element typography overrides for the quote-detail page. Each element is optional: when a
 * property is left at its inherit sentinel it keeps the per-page (heading/body) style; otherwise the
 * element overrides font / weight / colour / size on top. Stored under the QUOTE_DETAIL page with
 * `<ELEMENT>_<PROP>` attribute keys, and provided to the quote-detail composables via
 * [LocalQuoteElementStyles].
 */
enum class QuoteElement(val key: String) {
  NAME("NAME"),
  TICKER("TICKER"),
  PRICE("PRICE"),
  CHANGE("CHANGE"),
  AXES("AXES"),
  STAT_LABEL("STAT_LABEL"),
  STAT_VALUE("STAT_VALUE"),
  NEWS("NEWS");

  val fontAttr get() = "${key}_FONT"
  val weightAttr get() = "${key}_WEIGHT"
  val colourAttr get() = "${key}_COLOUR"
  val sizeAttr get() = "${key}_SIZE"
}

/** Raw override values for one element (inherit sentinels preserved). */
data class ElementSpec(
  val fontToken: String,
  val weight: Int,
  val colour: Int,
  val size: Float,
)

data class QuoteElementStyles(val specs: Map<QuoteElement, ElementSpec>)

val LocalQuoteElementStyles = compositionLocalOf<QuoteElementStyles?> { null }

/** Apply the element's font/weight/size (and colour, baked into [TextStyle.color]) over [base]. */
@Composable
fun quoteElementTextStyle(element: QuoteElement, base: TextStyle): TextStyle {
  val spec = LocalQuoteElementStyles.current?.specs?.get(element) ?: return base
  val context = LocalContext.current
  val family = if (spec.fontToken == AppPreferences.INHERIT_FONT) {
    base.fontFamily
  } else {
    FontManager.fontFamilyFor(context, spec.fontToken) ?: base.fontFamily
  }
  val weight = if (spec.weight >= 0) FontWeight(spec.weight) else base.fontWeight
  val fontSize = if (spec.size > 0f) base.fontSize * spec.size else base.fontSize
  val lineHeight = if (spec.size > 0f && base.lineHeight != TextUnit.Unspecified) {
    base.lineHeight * spec.size
  } else {
    base.lineHeight
  }
  val colour = if (spec.colour != AppPreferences.COLOUR_UNSET) Color(spec.colour) else base.color
  return base.copy(fontFamily = family, fontWeight = weight, fontSize = fontSize, lineHeight = lineHeight, color = colour)
}

/** The element's override colour, or [Color.Unspecified] when unset (use the call-site default). */
@Composable
fun quoteElementColour(element: QuoteElement): Color {
  val spec = LocalQuoteElementStyles.current?.specs?.get(element) ?: return Color.Unspecified
  return if (spec.colour != AppPreferences.COLOUR_UNSET) Color(spec.colour) else Color.Unspecified
}
