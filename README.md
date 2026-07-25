<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="120" alt="白い熊 株価表示 icon" />

# 白い熊 株価表示

**A stocks watchlist and home-screen widget, rebuilt black-and-yellow and customizable down to every element.**

A fork of [premnirmal/StockTicker](https://github.com/premnirmal/StockTicker) with **major additions**: a full per-page / per-element UI customization page, settings export & import, a pure-black yellow-accented theme, and an in-app language switch.

Installs **side-by-side** with the official Stocks Widget (app id `shiroikuma.kabukahyoji`).

**📥 Latest release: [`4.1.000+10`](https://github.com/ShiroiKuma0/shiroikuma-kabukahyoji/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-kabukahyoji/releases)

</div>

---

## 🎨 The 白い熊 株価表示 UI page
One page to restyle the whole app — per **page** (Global, Watchlist, Quote detail, Settings) set the accent, background, text, gain and loss colours, and the heading/body font, weight, colour and size multiplier. The Quote-detail page goes further with **per-element typography**: name, price, change, stat labels, stat values and news each get their own font, weight, colour and size. Import your own `.ttf`/`.otf` fonts, pick colours with RGBA sliders / hex / recent swatches, and watch every edit apply live. Styled kxkb-fashion: text-wide underlined headings on pure black, sections split by hairline spacers. Open it from Settings → Appearance, or **long-press the cog** on the main screen.

---

## 💾 Export / Import
The first section of the UI page backs up **everything settable in the app** — general settings, appearance (including your imported fonts), widget configurations, and the portfolio — as a ZIP of per-category JSON. Pick a persistent export directory once and exports are one tap; the page shows the newest export found there every time it opens. Import merges only the categories you tick, then offers **Restart now / Later** in the fork's black, yellow-bordered dialogs.

---

## 🖤 Pure-black, yellow-accented theme
Dark mode is genuinely black — the entire Material surface ladder is flattened to `#000000`, so the watchlist, every settings page, dialogs, menus and sheets all sit on black. The what's-new sheet and the settings page are framed in yellow, the navigation rail icons are yellow, and the launcher icon is a yellow chart on black.

---

## 📈 Quote-detail extras
Two switchable layouts — the default two-pane, or **graph on top** with stats and news split below — selectable on the UI page or by **long-pressing any chart**; in the wide list-detail view, graph-on-top expands the detail to the full page width. Double-tap any price chart to open it full screen; Back returns to the detail view. The in-app language is switchable (system / English / 日本語) and persists across restarts.

---

## Built on StockTicker
A fork of [premnirmal/StockTicker](https://github.com/premnirmal/StockTicker) (app id `shiroikuma.kabukahyoji`, so it coexists with the official build). Upstream provides the excellent resizable portfolio widget, Yahoo-Finance fetching during trading hours, alerts and news feed this fork builds on. The code remains under the GPL.

## Building
```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-kabukahyoji.git
cd shiroikuma-kabukahyoji
git checkout custom
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
```
`buildFoss` assembles the signed `purefoss` release (no Firebase/Crashlytics/Play services), copies the APK to `~/tmp/shiroikuma-kabukahyoji_<version>_arm64-v8a.apk`, and bumps the build number. Signing credentials go in `app/keystore.properties`; the SDK path in `local.properties`.
