package com.github.premnirmal.ticker.base

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.analytics.Analytics
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.model.StocksProvider.FetchState
import com.github.premnirmal.ticker.showDialog
import com.github.premnirmal.ticker.ui.AppMessaging
import com.github.premnirmal.ticker.ui.CollectBottomSheetMessage
import com.github.premnirmal.ticker.ui.LocalAppMessaging
import com.github.premnirmal.ticker.ui.ThemeViewModel
import com.github.premnirmal.ticker.ui.buildAppTypography
import com.github.premnirmal.ticker.ui.toThemeColourOrUnspecified
import com.github.premnirmal.tickerwidget.ui.theme.AppTheme
import com.github.premnirmal.tickerwidget.ui.theme.SelectedTheme
import com.github.premnirmal.tickerwidget.ui.theme.ThemeColourOverrides
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseActivity : ComponentActivity() {

    abstract val simpleName: String
    open val subscribeToErrorEvents = true
    private var isErrorDialogShowing = false

    private val themeViewModel by viewModels<ThemeViewModel>()

    @Inject lateinit var analytics: Analytics

    @Inject lateinit var stocksProvider: StocksProvider

    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var appMessaging: AppMessaging

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { isErrorDialogShowing = it.getBoolean(IS_ERROR_DIALOG_SHOWING, false) }
        create(savedInstanceState)
        if (subscribeToErrorEvents) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    stocksProvider.fetchState.collect { state ->
                        if (state is FetchState.Failure) {
                            if (this.isActive && !isErrorDialogShowing && !isFinishing) {
                                isErrorDialogShowing = true
                                showDialog(state.displayString).setOnDismissListener { isErrorDialogShowing = false }
                                delay(500L)
                            }
                        }
                    }
                }
            }
        }
        setContent {
            val currentTheme by themeViewModel.themePref.collectAsStateWithLifecycle(
                initialValue = SelectedTheme.SYSTEM
            )
            val useDynamicColour by themeViewModel.useDynamicColour.collectAsStateWithLifecycle(initialValue = true)
            val accent by themeViewModel.accentColour.collectAsStateWithLifecycle(initialValue = AppPreferences.COLOUR_UNSET)
            val background by themeViewModel.backgroundColour.collectAsStateWithLifecycle(initialValue = AppPreferences.COLOUR_UNSET)
            val text by themeViewModel.textColour.collectAsStateWithLifecycle(initialValue = AppPreferences.COLOUR_UNSET)
            val gain by themeViewModel.gainColour.collectAsStateWithLifecycle(initialValue = AppPreferences.COLOUR_UNSET)
            val loss by themeViewModel.lossColour.collectAsStateWithLifecycle(initialValue = AppPreferences.COLOUR_UNSET)
            val headingFont by themeViewModel.headingFont.collectAsStateWithLifecycle(initialValue = "")
            val bodyFont by themeViewModel.bodyFont.collectAsStateWithLifecycle(initialValue = "")
            val headingWeight by themeViewModel.headingWeight.collectAsStateWithLifecycle(initialValue = 0)
            val bodyWeight by themeViewModel.bodyWeight.collectAsStateWithLifecycle(initialValue = 0)
            val textScale by themeViewModel.textScale.collectAsStateWithLifecycle(initialValue = 1.0f)
            val context = LocalContext.current
            val colourOverrides = ThemeColourOverrides(
                accent = accent.toThemeColourOrUnspecified(),
                background = background.toThemeColourOrUnspecified(),
                text = text.toThemeColourOrUnspecified(),
                gain = gain.toThemeColourOrUnspecified(),
                loss = loss.toThemeColourOrUnspecified(),
            )
            val typography = remember(headingFont, bodyFont, headingWeight, bodyWeight, textScale) {
                buildAppTypography(context, headingFont, bodyFont, headingWeight, bodyWeight, textScale)
            }
            AppTheme(
                theme = currentTheme,
                useDynamicColour = useDynamicColour,
                colourOverrides = colourOverrides,
                typography = typography,
            ) {
                CompositionLocalProvider(LocalAppMessaging provides appMessaging) {
                    val isDarkTheme = isSystemInDarkTheme()
                    DisposableEffect(isDarkTheme) {
                        enableEdgeToEdge()
                        onDispose {}
                    }
                    Box {
                        ShowContent()
                        CollectBottomSheetMessage()
                    }
                }
            }
        }
    }

    open fun create(savedInstanceState: Bundle?) {}

    // Apply the per-app language chosen in the 白い熊 株価表示 UI page. AppCompatDelegate stores it
    // (framework on API 33+, autoStoreLocales service below that); we wrap the context here so our
    // ComponentActivity-based screens pick it up on every API level.
    override fun attachBaseContext(newBase: Context) {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            super.attachBaseContext(newBase)
        } else {
            val localeList = LocaleList.forLanguageTags(locales.toLanguageTags())
            LocaleList.setDefault(localeList)
            val config = Configuration(newBase.resources.configuration).apply { setLocales(localeList) }
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    @Composable
    abstract fun ShowContent()

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        analytics.trackScreenView(simpleName, this)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_ERROR_DIALOG_SHOWING, isErrorDialogShowing)
    }

    companion object {
        private const val IS_ERROR_DIALOG_SHOWING = "IS_ERROR_DIALOG_SHOWING"
    }
}
