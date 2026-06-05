package com.github.premnirmal.ticker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.ThemePage
import com.github.premnirmal.tickerwidget.ui.theme.AppTheme
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import com.github.premnirmal.tickerwidget.ui.theme.ThemeColourOverrides

/**
 * Wraps [content] in the effective theme for [page] (page overrides on top of global), so a single
 * screen can carry its own colours/fonts. Theme mode and Material You stay global. Reacts to changes
 * via [ThemeViewModel.themeVersion].
 */
@Composable
fun PageThemeProvider(page: ThemePage, content: @Composable () -> Unit) {
  val viewModel: ThemeViewModel = hiltViewModel()
  val themeMode by viewModel.themePref.collectAsStateWithLifecycle(initialValue = SelectedTheme.SYSTEM)
  val useDynamicColour by viewModel.useDynamicColour.collectAsStateWithLifecycle(initialValue = true)
  val version by viewModel.themeVersion.collectAsStateWithLifecycle(initialValue = 0)
  val context = LocalContext.current

  val overrides = remember(page, version) {
    ThemeColourOverrides(
      accent = viewModel.resolvedColour(page, AppPreferences.ATTR_ACCENT).toThemeColourOrUnspecified(),
      background = viewModel.resolvedColour(page, AppPreferences.ATTR_BACKGROUND).toThemeColourOrUnspecified(),
      // General (unstyled) text follows the body colour.
      text = viewModel.resolvedColour(page, AppPreferences.ATTR_BODY_COLOUR).toThemeColourOrUnspecified(),
      gain = viewModel.resolvedColour(page, AppPreferences.ATTR_GAIN).toThemeColourOrUnspecified(),
      loss = viewModel.resolvedColour(page, AppPreferences.ATTR_LOSS).toThemeColourOrUnspecified(),
    )
  }
  val typography = remember(page, version) {
    buildAppTypography(
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

  // Per-element overrides for the quote-detail page (raw values; resolved at each call site).
  val elementStyles = remember(page, version) {
    if (page == ThemePage.QUOTE_DETAIL) {
      QuoteElementStyles(
        QuoteElement.entries.associateWith { element ->
          ElementSpec(
            fontToken = viewModel.rawFont(page, element.fontAttr),
            weight = viewModel.rawWeight(page, element.weightAttr),
            colour = viewModel.rawColour(page, element.colourAttr),
            size = viewModel.rawSize(page, element.sizeAttr),
          )
        }
      )
    } else {
      null
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
