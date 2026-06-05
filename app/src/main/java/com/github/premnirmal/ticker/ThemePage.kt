package com.github.premnirmal.ticker

/**
 * The themable "pages" for the 白い熊 株価表示 UI customization. [GLOBAL] is the base; the others
 * inherit from it and override only what they set. [key] is the SharedPreferences key segment.
 */
enum class ThemePage(val key: String) {
  GLOBAL("global"),
  WATCHLIST("watchlist"),
  QUOTE_DETAIL("quote_detail"),
  SETTINGS("settings"),
}
