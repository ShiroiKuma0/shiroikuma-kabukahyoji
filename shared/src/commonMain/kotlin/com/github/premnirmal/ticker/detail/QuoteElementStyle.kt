package com.github.premnirmal.ticker.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/** Which quote-detail text element a per-element style override applies to. */
enum class QuoteElement {
    NAME, PRICE, CHANGE, STAT_LABEL, STAT_VALUE, NEWS
}

/**
 * A resolved per-element style delta for the quote-detail screen. The Android "白い熊 株価表示 UI"
 * customization page resolves the user's font token / weight / colour / size multiplier into these
 * platform-agnostic values (font families come from the Android `FontManager`) and provides them via
 * [LocalQuoteElementStyles]; the shared quote-detail composables apply them on top of their own base
 * [TextStyle] with [withElementStyle]. The local is empty on iOS, so the base styles render unchanged.
 */
data class QuoteElementStyle(
    val fontFamily: FontFamily? = null,
    val fontWeight: FontWeight? = null,
    val color: Color = Color.Unspecified,
    val scale: Float = 0f,
)

val LocalQuoteElementStyles = compositionLocalOf<Map<QuoteElement, QuoteElementStyle>> { emptyMap() }

/** Apply an element style delta to a base [TextStyle]; unset fields keep the base value. */
fun TextStyle.withElementStyle(style: QuoteElementStyle?): TextStyle {
    if (style == null) return this
    return copy(
        fontFamily = style.fontFamily ?: fontFamily,
        fontWeight = style.fontWeight ?: fontWeight,
        fontSize = if (style.scale > 0f) fontSize * style.scale else fontSize,
        lineHeight = if (style.scale > 0f && lineHeight != TextUnit.Unspecified) lineHeight * style.scale else lineHeight,
        color = if (style.color.isSpecified) style.color else color,
    )
}

/** Base [TextStyle] for [element] with the user's per-element override (if any) applied. */
@Composable
fun quoteElementStyle(element: QuoteElement, base: TextStyle): TextStyle =
    base.withElementStyle(LocalQuoteElementStyles.current[element])
