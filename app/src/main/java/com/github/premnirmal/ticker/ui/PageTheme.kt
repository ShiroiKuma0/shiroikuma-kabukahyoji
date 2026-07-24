package com.github.premnirmal.ticker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.ticker.detail.LocalQuoteElementStyles
import com.github.premnirmal.ticker.detail.QuoteElement
import com.github.premnirmal.ticker.detail.QuoteElementStyle
import com.github.premnirmal.tickerwidget.ui.theme.AppTheme
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import com.github.premnirmal.tickerwidget.ui.theme.ThemeColourOverrides
import com.github.premnirmal.tickerwidget.ui.theme.appTypography
import com.github.premnirmal.tickerwidget.ui.theme.toOverrideColour
import org.koin.androidx.compose.koinViewModel

/**
 * Wraps [content] in the effective theme for [page] — the page's colours and heading/body typography
 * layered on the global base, plus (on the quote-detail page) the per-element style overrides provided
 * through [LocalQuoteElementStyles]. Theme mode and Material You stay global. Recomposes when the app
 * theme changes or on any "白い熊 株価表示 UI" write (via the `themeVersion` tick).
 */
@Composable
fun PageThemeProvider(page: ThemePage, content: @Composable () -> Unit) {
    val viewModel: ThemeViewModel = koinViewModel()
    val themeMode by viewModel.themePref.collectAsStateWithLifecycle(initialValue = SelectedTheme.SYSTEM)
    val version by viewModel.themeVersion.collectAsStateWithLifecycle(initialValue = 0)
    val context = LocalContext.current

    val useDynamicColour = remember(version) { viewModel.useDynamicColour() }

    val overrides = remember(page, version) {
        ThemeColourOverrides(
            accent = viewModel.resolvedColour(page, AppPreferences.ATTR_ACCENT).toOverrideColour(),
            background = viewModel.resolvedColour(page, AppPreferences.ATTR_BACKGROUND).toOverrideColour(),
            text = viewModel.resolvedColour(page, AppPreferences.ATTR_TEXT).toOverrideColour(),
            gain = viewModel.resolvedColour(page, AppPreferences.ATTR_GAIN).toOverrideColour(),
            loss = viewModel.resolvedColour(page, AppPreferences.ATTR_LOSS).toOverrideColour(),
        )
    }

    val base = appTypography()
    val typography = remember(page, version, base) {
        buildAppTypography(
            base = base,
            context = context,
            headingToken = viewModel.resolvedFontToken(page, AppPreferences.ATTR_HEADING_FONT),
            bodyToken = viewModel.resolvedFontToken(page, AppPreferences.ATTR_BODY_FONT),
            headingWeight = viewModel.resolvedWeight(page, AppPreferences.ATTR_HEADING_WEIGHT),
            bodyWeight = viewModel.resolvedWeight(page, AppPreferences.ATTR_BODY_WEIGHT),
            headingColour = viewModel.resolvedColour(page, AppPreferences.ATTR_HEADING_COLOUR),
            bodyColour = viewModel.resolvedColour(page, AppPreferences.ATTR_BODY_COLOUR),
            headingScale = viewModel.resolvedSize(page, AppPreferences.ATTR_HEADING_SIZE),
            bodyScale = viewModel.resolvedSize(page, AppPreferences.ATTR_BODY_SIZE),
        )
    }

    val elementStyles = remember(page, version) {
        if (page == ThemePage.QUOTE_DETAIL) {
            QuoteElement.entries.associateWith { element ->
                val token = viewModel.elementFont("${element.name}_FONT")
                val weight = viewModel.elementWeight("${element.name}_WEIGHT")
                val colour = viewModel.elementColour("${element.name}_COLOUR")
                val size = viewModel.elementSize("${element.name}_SIZE")
                QuoteElementStyle(
                    fontFamily = if (token == AppPreferences.INHERIT_FONT) null else FontManager.fontFamilyFor(context, token),
                    fontWeight = if (weight > 0) FontWeight(weight) else null,
                    color = colour.toOverrideColour(),
                    scale = if (size > 0f) size else 0f,
                )
            }
        } else {
            emptyMap()
        }
    }

    AppTheme(
        theme = themeMode,
        useDynamicColour = useDynamicColour,
        colourOverrides = overrides,
        typography = typography,
    ) {
        CompositionLocalProvider(LocalQuoteElementStyles provides elementStyles) {
            content()
        }
    }
}
