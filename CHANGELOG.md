# 白い熊 株価表示 — changelog vs stock StockTicker

Everything this fork builds on top of [premnirmal/StockTicker](https://github.com/premnirmal/StockTicker) (base: upstream release `4.1.004`).

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
- Export format: ZIP of type-tagged per-category JSON plus a manifest. **One ZIP per backup, always** — however many components are exported, they are entries inside that single archive.
- **Family file name** `shiroikuma-kabukahyoji_<yyyy-MM-dd_HH-mm-ss>.zip` — no version, no `-export` infix, no decoration, so every 白い熊 app's backups sort and read uniformly in one shared directory. The previous `shiroikuma-kabukahyoji-<version>-export_…` name is still recognised for the "latest export" line.
- **Sub-options**: a category can offer separately tickable parts — imported **custom fonts** are their own selectable part (`appearance.fonts`) shown indented under Appearance & UI, and independently addressable by automation.
- **Settable export directory** (SAF tree, permission persisted, stored device-locally and never exported); with it set, export is one tap, otherwise a save-as dialog. The page and panel query the directory for the **latest export** on open.
- Category checkboxes with Select all; import validates the archive and **merges** only the selected categories.
- Black, **yellow-bordered result dialogs**: export-OK closes the info dialog, the panel and the UI page in one tap; import-OK offers **Restart now / Later** (Later closes the whole chain); failures leave the panel open. Bottom button row Arcanechat-style: round pills, Cancel separated left, Import and Export on the right.

### 保存復元 automation — headless export for 白い熊 自由作業盤
- **Token-gated broadcast contract** so 自由作業盤's 保存復元 project can back up this app together with every sister app in one run: exported receiver answering `shiroikuma.kabukahyoji.action.EXPORT_STATE` and `shiroikuma.kabukahyoji.action.LIST_CATEGORIES` (no `android:permission` — the token is the gate).
- **Headless export**: the same category ZIP the Export/Import page writes, produced with no Activity and no user interaction. The export core is a single function `(selection, stream, onProgress)`; the panel and the receiver are two thin callers, so the automated path can never drift from the manual one.
- **Request extras**: `token`, optional `path` (an absolute directory that overrides the app's configured one, created if missing), optional `items` (comma-separated category / sub-option ids; absent = everything), optional `progress_action`, plus `reply_action` / `reply_package` / `reply_id`.
- **Replies** are a fresh broadcast carrying `FLAG_INCLUDE_STOPPED_PACKAGES` — on EMUI a Binder (`ResultReceiver`, `PendingIntent`, `Messenger`) is not reliably carried into another app's manifest receiver, and the ordered-broadcast result channel is severed between third-party apps, so `setResultData` is set for AOSP correctness but never relied on. Exactly one terminal reply per request, guarded by an `AtomicBoolean`; `goAsync()` holds the broadcast open while the work runs on IO.
- Reply lines: `OK:<absolute path>|<bytes>|<human size>|<n> categories` for an export, `OK:` plus one `id<TAB>label[<TAB>parent-id]` line per category for the listing, and distinct errors — `automation disabled`, `bad token`, `no-directory`, `no-storage-access`, `unknown category in items: …` — the last refusing before any file is written.
- **Progress broadcasts** with real counts, never a percentage: `区分 3/5 — 外観・UI` plus structured `current`/`total`/`unit` extras and the app label, throttled to one per 500 ms with a mandatory final one at completion.
- **Automation controls inside the Export/Import section** (never a section of their own), directly below the existing export rows: a master switch defaulting to **off**, and a token row showing the token abbreviated, copying it in full on tap, and regenerating it behind a warning that pasted copies must be updated.
- **Token infra**: 24 `SecureRandom` bytes, hex-encoded, generated lazily on first read, compared constant-time with `MessageDigest.isEqual`. Switch and token live in the device-local Export/Import prefs file that nothing exports, so the token can never travel inside a backup ZIP.

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
- **Inherited upstream CI removed** — all seven workflow files (`android`, `detekt`, `unit-tests`, `ios`, `version-code`, `issue-check`, `copilot-setup-steps`) deleted and GitHub Actions switched off repo-wide. Every run failed here and did nothing but mail a failure notice on each push: the fork has no `google-services.json`, no CI keystore and no iOS side, and the real build is `buildFoss` run locally. `.github/ISSUE_TEMPLATE/` stays.

## Packaging
- Side-by-side app: application id `shiroikuma.kabukahyoji`, launcher label **白い熊 株価表示**; the code namespace stays `com.github.premnirmal.tickerwidget`.
- Ships the **purefoss** flavor: no Firebase, no Crashlytics, no Play services, no `google-services.json`.
- Fork versioning `versionName = <upstream tag>+<NNN>` (e.g. `4.1.003+001`), `versionCode = (major*100000 + minor*1000 + patch)*100 + build` — monotonic across upstream bumps and safe within Android's 32-bit cap.
- The build counter is **zero-padded to three digits** in the versionName, so the manifest version, the APK filename and the release tag are the same string and sort in build order. `gradle.properties` keeps the raw integer, because the `buildFoss` auto-bump rewrites it by exact match.
- `buildFoss` Gradle task: assembles the signed release, copies the APK to `~/tmp/`, auto-increments the build number. Signing via gitignored `app/keystore.properties`.
- **arm64-v8a-only** APK (unused ABIs stripped).
- `MANAGE_EXTERNAL_STORAGE` declared so an automation caller may name any absolute backup directory; without the grant the app falls back to its configured SAF directory, or answers `ERROR:no-storage-access`.

## Upstream base

The fork stack is replayed onto each upstream release tag; it currently sits on `4.1.004`.

- **Rebased `4.1.003` → `4.1.004`** (26 fork commits, zero conflicts). Upstream's one code commit in that span fixes the price chart's axis labels going invisible under a forced app theme: Vico derived their colour from the system dark/light setting rather than the app's `MaterialTheme`, so a forced-dark app on a light device drew them unreadable. Both axes now take an explicit label component bound to `onSurfaceVariant`. This lands squarely in fork territory — the pure-black dark mode is exactly such a forced theme — so it is a fix the fork gains rather than merely inherits. Nothing else changed but the bot's `version.properties` bump; the overlap with the fork's changed files was empty and the whole stack replayed untouched.
- **Rebased `4.1.002` → `4.1.003`** (24 fork commits, zero conflicts). Upstream's three commits in that span touched only files the fork has never customized: a fix for the price chart's marker truncating the value line (with the marker's price format aligned to the rest of the app), more quotes fitted into the iOS compact widget, and the bot's `version.properties` bump. The overlap between the fork's changed files and upstream's was empty, so the whole stack replayed untouched.
- **Rebased `4.1.000` → `4.1.002`** (19 fork commits). Upstream's own work in that span was mostly iOS/Kotlin-Multiplatform (TodayStocks for iOS, WidgetKit widget fixes, Xcode 26 CI), plus a repo-wide detekt sweep and one Android bugfix — saved alerts not appearing when re-entering the quote-detail screen.
- **Upstream renamed the app "Stock Ticker" → "Today Stocks"**; this fork keeps its own launcher label **白い熊 株価表示**.
- Where upstream's detekt sweep restructured code the fork had only reformatted — the `buildQuoteDetails` `add()` helper and the `toMutablePreferences` cast wrapping — the fork **adopts upstream's structure** rather than forcing the older diff, keeping the fork layer to genuine behaviour changes.
- The fork's `AppTheme` remains a superset of upstream's: same Material You dynamic-colour resolution, plus the per-page colour/typography overrides and the pure-black surface ladder.
