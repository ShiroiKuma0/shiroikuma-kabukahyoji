# 白い熊 株価表示 — changelog vs stock StockTicker

Everything this fork builds on top of [premnirmal/StockTicker](https://github.com/premnirmal/StockTicker) (base: upstream release `4.1.000`).

## Major features

### 白い熊 株価表示 UI customization page
- New **UI page** (Settings → Appearance, or **long-press the main-screen cog**): a hub with Export/Import, dynamic-colour toggle, language chips, and one editor per themable page.
- **Per-page theming** for Global, Watchlist, Quote detail and Settings: accent, background, text, gain and loss colours; heading and body font, weight, colour and size multiplier. Non-global pages inherit from Global and override only what they set; every edit applies live.
- **Per-element typography on the quote-detail page**: name, price, change, stat label, stat value and news each take their own font, weight, colour and size, with an Inherit option.
- **Font imports**: bring your own `.ttf`/`.otf` (stored in app storage), plus the platform sans/serif/mono generics; the font picker renders each entry in its own glyphs.
- **Colour picker** with RGBA sliders, hex entry, preset palette and recent-colour swatches over an alpha checkerboard.
- **Dynamic colour (Material You) toggle** for Android 12+.
- **In-app language switch** — system / English / 日本語 — persisted and re-applied on launch.
- The page itself is styled kxkb-fashion: 20 sp bold accent headings underlined exactly text-wide, hairline separators between sections, 17 sp sub-headings, deep kxkb indents.

### Settings export / import
- First section of the UI page: back up **all settable items**, split into categories — **General settings** (update interval/window/days, auto-sort, rounding, notification alerts, theme), **Appearance & UI** (every per-page/per-element override, language, dynamic colour, recent colours, plus the imported font files themselves), **Widgets** (each widget's configuration), **Portfolio** (tickers & positions).
- Export format: ZIP of type-tagged per-category JSON plus a manifest; filenames `shiroikuma-kabukahyoji-<version>-export_<timestamp>.zip`.
- **Settable export directory** (SAF tree, permission persisted, stored device-locally and never exported); with it set, export is one tap, otherwise a save-as dialog. The page and panel query the directory for the **latest export** on open.
- Category checkboxes with Select all; import validates the archive and **merges** only the selected categories.
- Black, **yellow-bordered result dialogs**: export-OK closes the info dialog, the panel and the UI page in one tap; import-OK offers **Restart now / Later** (Later closes the whole chain); failures leave the panel open. Bottom button row Arcanechat-style: round pills, Cancel separated left, Import and Export on the right.

## UI & theming
- **Pure-black dark mode**: the whole Material surface ladder (background, surface, every `surfaceContainer*` step) is flattened to `#000000` over both the brand and Material-You schemes — main screen, all settings pages, dialogs, dropdown menus and bottom sheets. User background overrides still win; light mode untouched.
- **What's-new sheet** (after an update; also the tutorial and quote data-card sheets): pure black with a 2 dp yellow border, yellow text and a yellow drag handle.
- **Settings page framed** with a 2 dp yellow border on black.
- **Navigation rail icons yellow** (`#FFFF00`, dimmed when disabled), with a custom rail item that keeps the Material pill indicator and adds long-press support.
- **Long-press the cog** (rail or glass bottom bar) to open the UI customization page.
- **Launcher icon** restyled: yellow chart on black at 55 % size.
- **Quote-detail layout options**: default two-pane, or **graph on top** with stats and news split side-by-side below — selectable in the UI page's Quote detail editor, persisted, and part of the settings export. **Long-press any chart** toggles the two layouts; in the wide list-detail view, graph-on-top expands the detail to the full page width (Back or another long-press returns).
- **Double-tap fullscreen chart** on the quote-detail price chart; double-tap, long-press or Back returns.
- Japanese translations for all fork strings.

## Fixes & behavior
- GitHub new-issue form fixed and templates de-branded to this fork.
- Repo-wide detekt `autoCorrect` style pass; explicit `Locale.ROOT` in colour-hex formatting.

## Packaging
- Side-by-side app: application id `shiroikuma.kabukahyoji`, launcher label **白い熊 株価表示**; the code namespace stays `com.github.premnirmal.tickerwidget`.
- Ships the **purefoss** flavor: no Firebase, no Crashlytics, no Play services, no `google-services.json`.
- Fork versioning `versionName = <upstream tag>+<build>` (e.g. `4.1.000+8`), `versionCode = (major*100000 + minor*1000 + patch)*100 + build` — monotonic across upstream bumps and safe within Android's 32-bit cap.
- `buildFoss` Gradle task: assembles the signed release, copies the APK to `~/tmp/`, auto-increments the build number. Signing via gitignored `app/keystore.properties`.
- **arm64-v8a-only** APK (unused ABIs stripped).
