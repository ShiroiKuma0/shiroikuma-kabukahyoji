package com.github.premnirmal.ticker.settings

import androidx.compose.runtime.Composable
import com.github.premnirmal.ticker.base.BaseActivity

/**
 * The "白い熊 株価表示 UI" global customization page (Phase 2). A [BaseActivity], so it re-themes live
 * as the user edits colours/font. Launched from the Appearance row in Settings.
 */
class UiSettingsActivity : BaseActivity() {

    override val simpleName: String
        get() = "UiSettingsActivity"

    @Composable
    override fun ShowContent() {
        UiSettingsScreen(onBack = { finish() })
    }
}
