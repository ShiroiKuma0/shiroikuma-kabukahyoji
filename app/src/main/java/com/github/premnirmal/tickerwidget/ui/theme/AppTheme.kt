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

/**
 * Android app theme. Delegates to the shared cross-platform [SharedAppTheme] (shared brand colour
 * scheme + `appShapes`), supplying a Material You dynamic [ColorScheme] on Android 12+ when
 * [useDynamicColour] is on.
 *
 * The "白い熊 株価表示 UI" per-page overrides are layered on top: custom accent/background/text are
 * copied onto the resolved [ColorScheme]; custom gain/loss are provided through [SharedColours]'
 * CompositionLocals so the shared quote widgets pick them up; a custom [typography] (built per page,
 * with the chosen fonts/weights/colours/sizes) re-wraps the content in a [MaterialTheme].
 * `PageThemeProvider` computes these and calls this per screen.
 */
@Composable fun AppTheme(
    theme: SelectedTheme,
    useDynamicColour: Boolean = true,
    colourOverrides: ThemeColourOverrides = ThemeColourOverrides(),
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (theme) {
        SelectedTheme.SYSTEM -> isSystemInDarkTheme()
        SelectedTheme.LIGHT -> false
        SelectedTheme.DARK -> true
    }
    val useDynamic = useDynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme: ColorScheme? = if (useDynamic) {
        if (isDark) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        null
    }

    val hasSchemeOverride =
        colourOverrides.accent.isSpecified || colourOverrides.background.isSpecified || colourOverrides.text.isSpecified
    val base = dynamicScheme ?: if (isDark) brandDarkColorScheme else brandLightColorScheme
    // 白い熊 fork: dark mode is pure black, not Material's greyish surfaces — the whole surface
    // ladder is flattened to black so pages, dialogs, menus and sheets all render on black. The
    // user's own background override (below) still wins.
    val blackened = if (isDark) base.pureBlackSurfaces() else base
    val colorSchemeOverride: ColorScheme = if (hasSchemeOverride) {
        blackened.withOverrides(colourOverrides)
    } else {
        blackened
    }

    SharedAppTheme(
        theme = theme,
        colorSchemeOverride = colorSchemeOverride,
    ) {
        CompositionLocalProvider(
            LocalGainOverride provides colourOverrides.gain,
            LocalLossOverride provides colourOverrides.loss,
        ) {
            if (typography != null) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    shapes = MaterialTheme.shapes,
                    typography = typography,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}
