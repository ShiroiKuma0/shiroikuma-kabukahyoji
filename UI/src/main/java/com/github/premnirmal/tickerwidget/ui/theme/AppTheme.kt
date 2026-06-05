package com.github.premnirmal.tickerwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext

@Composable fun AppTheme(
  theme: SelectedTheme,
  useDynamicColour: Boolean = true,
  colourOverrides: ThemeColourOverrides = ThemeColourOverrides(),
  typography: Typography = AppTypography,
  content: @Composable () -> Unit
) {
  val dynamicColor = useDynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val isDarkTheme = isSystemInDarkTheme()
  val baseScheme = when (theme) {
    SelectedTheme.SYSTEM -> {
      if (dynamicColor) {
        if (isDarkTheme) {
          dynamicDarkColorScheme(LocalContext.current)
        } else {
          dynamicLightColorScheme(LocalContext.current)
        }
      } else {
        if (isDarkTheme) ThemePref.Dark.colours.toColorScheme() else ThemePref.Light.colours.toColorScheme()
      }
    }

    SelectedTheme.LIGHT -> {
      if (dynamicColor) {
        dynamicLightColorScheme(LocalContext.current)
      } else {
        ThemePref.Light.colours.toColorScheme()
      }
    }
    SelectedTheme.DARK -> {
      if (dynamicColor) {
        dynamicDarkColorScheme(LocalContext.current)
      } else {
        ThemePref.Dark.colours.toColorScheme()
      }
    }
  }
  val colorScheme = baseScheme.withOverrides(colourOverrides)
  CompositionLocalProvider(
    LocalChangePositive provides colourOverrides.gain,
    LocalPositiveGreen provides colourOverrides.gain,
    LocalChangeNegative provides colourOverrides.loss,
    LocalNegativeRed provides colourOverrides.loss,
  ) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AppShapes
    ) {
      content()
    }
  }
}

private fun ColorScheme.withOverrides(o: ThemeColourOverrides): ColorScheme {
  var scheme = this
  if (o.accent.isSpecified) {
    scheme = scheme.copy(primary = o.accent, surfaceTint = o.accent)
  }
  if (o.background.isSpecified) {
    scheme = scheme.copy(background = o.background, surface = o.background)
  }
  if (o.text.isSpecified) {
    scheme = scheme.copy(onBackground = o.text, onSurface = o.text)
  }
  return scheme
}

enum class SelectedTheme {
  SYSTEM,
  LIGHT,
  DARK,
}
