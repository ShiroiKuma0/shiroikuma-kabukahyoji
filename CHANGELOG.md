# 白い熊 株価表示 — changelog vs stock StockTicker

Everything this fork builds on top of [premnirmal/StockTicker](https://github.com/premnirmal/StockTicker) (base: upstream release `4.1.004`).

## 白い熊 株価表示 4.1.004+002 — 2026-09-04

Built on upstream release `4.1.004`. Implements the 保存復元 sister-app contract **v2**, so 白い熊 応用管理 can back this app up *with its data* and restore it onto a phone that has just been wiped.

### Restore-path data loss — two real bugs, both fixed
- **The watchlist would have been lost on every automated restore.** `SharedPreferencesTickersStore.saveTickers` writes through `androidx.core.content.edit { }`, which **defaults to `commit = false`** — so the restored ticker symbols were an `apply()` in disguise, with no literal `apply()` anywhere in the file for a source grep to find. 応用管理 force-stops an app with `Process.killProcess` the instant it reports a successful import, which leaves that write nowhere to land: the Room quotes would arrive, the symbol list would not, and the restore would report success over an **empty portfolio**.
- **The portfolio itself persisted through a fire-and-forget coroutine.** `StocksProvider.addPortfolio` launches its `storage.saveQuotes` and returns, so an import could answer `OK` while the database write was still in flight.
- Fixed with an awaited `addPortfolioNow`, plus an empty `commit()` per preferences file the restore spans, which blocks on that file's write lock until any queued write has landed. The ordinary `addPortfolio` keeps its `launch` — that is what lets the UI repaint on the next frame — and `saveTickers` stays asynchronous, because it sits on the hot path with main-thread callers and making it synchronous would trade a truncated restore for an ANR.
- Audited the rest of the restore path rather than assuming: `AppPreferences` goes through blocking DataStore (durable on return, no debounce to wait out), the widget preferences already commit their own editor, and fonts are written with `File.writeBytes`.

### `<queries>` — replies had been silently discarded since Android 11
- The manifest carried **no `<queries>` element at all**, so `setPackage` on every reply broadcast failed silently: the export ran, wrote its archive correctly, and was never heard of by the caller. Both 保存復元 callers — `shiroikuma.jiyusagyoban` (which drives the batch) and `shiroikuma.oyokanri` (which restores) — are now declared. This also lets the data door read a caller's signing certificate, which is otherwise refused as unreadable.

### The gate is now open by default (contract §2)
- `automation_enabled` **defaults on** and a new `automation_require_token` **defaults off**, both decided in a single `refuse()` function so "disabled" and "bad token" cannot drift apart across entry points.
- **A token sent to an app that does not require one is ignored, never refused** — tokens outlive the settings they were pasted for, and refusing them would turn one switch being off into half a batch mysteriously failing.
- All three flag writes use `commit()`. With the default flipped, a write that never reaches disk no longer falls back to "off" — it falls back to **on**, so the gate would fail open.
- Settings rows follow: the master switch, then 「Use authorization token?」, with the token row shown **only while a token is actually being required**.

### The data door (contract §2a)
- New exported `ContentProvider` at `shiroikuma.kabukahyoji.automation` with `describe` / `export` / `import` / `cancel`. **`import` exists only here** — the broadcast receiver is exported without a permission, so an import there would let any app on the phone wipe this one.
- Callers are identified by the framework and checked three ways: an **exact package name** (never a prefix — a prefix is not an identity, and any sideloaded app may name itself `shiroikuma.anything`), a **uid cross-check** against what the kernel reports, and a **pinned signing certificate**.
- The payload moves through a **`ParcelFileDescriptor` the caller supplies** — not a path, not a URI — so the archive lands inside 応用管理's encrypted, checksummed backup instead of beside it in plaintext. The descriptor is duplicated before it leaves the binder call and closed in a `finally`. As a consequence the automation path no longer needs `MANAGE_EXTERNAL_STORAGE` at all.
- Long work runs in a `specialUse` foreground service that goes foreground as its **first statement**, before any early return: once `startForegroundService` has been called the platform requires it whatever the service then decides, so a caller retrying with a stale job id would otherwise have **killed this app mid-backup**. A stale or already-claimed job stops silently, because that job has already had its one terminal reply.
- `describe` answers from the manifest, `PackageManager` and a plain enum only — never the DI graph. A provider's `onCreate` runs before `Application.onCreate`, which is exactly the clean-phone case where a provider call is what starts the process.
- A large import is spooled to a cache file rather than into memory, and validated there before anything is applied.

### `CANCEL_EXPORT`, and archives that are never half-written
- New `<pkg>.action.CANCEL_EXPORT`, which this fork lacked entirely, on the same exported receiver — the stop path lives behind an unexported service that a third-party app cannot start, so the cancel is routed through the receiver and signalled internally. It is safe to send at any time: a cancel arriving when nothing is running is a silent no-op, not an error.
- On cancel, in order: the write loop unwinds **at an entry boundary** rather than mid-write, the partial file is deleted, `ERROR:cancelled` is sent as the terminal reply for the **original** request through the same single-fire guard, and the export guard is released.
- Every export — automated or hand-run — now writes `<name>.part` and renames only once the archive is closed and complete. 白い熊 keeps every app's backups in one directory sorted by date, where a truncated archive would otherwise silently become "the latest backup" of this app.
- The concurrent-export guard is process-local and released in a `finally`, never persisted: a persisted flag wedges the app permanently after a single crash.

### Progress
- Progress broadcasts now carry **`item`** — the category id being written — which is how the calling panel knows which row to highlight; without it a count is drawn against the wrong row.
- Progress on the data door, with the **`job_id` as the correlation id** in both `job_id` and `reply_id`, plus a 15-second heartbeat that re-sends the last true line. Justified even for a small archive: the export writes into a descriptor the caller supplied, which may be a pipe, so a category can block for as long as the caller is slow to drain it.
- Fixed a conditional `setPackage` on the progress sender. Since API 26 an implicit broadcast reaches no manifest-declared receiver at all, so omitting it does not send progress more widely — it sends none.

### Capability discovery
- Three `shiroikuma.automation.*` `<meta-data>` entries (contract 2, format 1, min\_format 1), **integer-typed**, so 応用管理 can answer "can this app be backed up" for an app that is currently frozen, without waking it.

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
