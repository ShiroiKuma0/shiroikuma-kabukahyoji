package com.github.premnirmal.ticker.settings

import androidx.compose.runtime.Composable
import com.github.premnirmal.ticker.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * The "白い熊 株価表示 UI" customization page. Launched from the Settings entry and from a long-press
 * on the Settings navigation item. Being a [BaseActivity], it re-themes live as the user edits.
 */
@AndroidEntryPoint
class UiSettingsActivity : BaseActivity() {

  override val simpleName: String
    get() = "UiSettingsActivity"

  @Composable
  override fun ShowContent() {
    UiSettingsScreen(onBack = { finish() })
  }
}
