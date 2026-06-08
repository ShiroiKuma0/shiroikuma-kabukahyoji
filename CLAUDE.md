# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Stocks Widget (a.k.a. StockTicker) is an Android app whose centerpiece is a resizable home-screen widget showing a stock portfolio. Stocks are fetched from Yahoo Finance during trading hours. Application id `com.github.premnirmal.tickerwidget`. GPL licensed.

## Fork layer (shiroikuma.kabukahyoji)

This repository is 白い熊's personal fork of **premnirmal/StockTicker**, built and sideloaded as a
separate app (`白い熊 株価表示`) alongside any official build. It follows the same model as the other
`shiroikuma-*` Android forks: a thin, legible commit layer on top of upstream, replayed onto each
upstream release.

| Item | Value |
| --- | --- |
| Upstream | `premnirmal/StockTicker` (git remote `upstream`, HTTPS, fetch-only) |
| Fork | `git@github.com:ShiroiKuma0/shiroikuma-kabukahyoji` (remote `origin`, SSH) |
| `master` branch | Mirrors `upstream/master`, fast-forward only — never carries fork changes |
| `custom` branch | Our commit stack, rebased onto each upstream **release tag** (currently `4.0.081`) — **all development happens here** |
| `applicationId` | `shiroikuma.kabukahyoji` (via `APP_ID` in `gradle.properties` → `appIdBase`) |
| `namespace` | `com.github.premnirmal.tickerwidget` (unchanged — R/BuildConfig package; never touch) |
| App label | `白い熊 株価表示` (`app_name` in `app/src/purefoss/res/values/app_name.xml`) |
| Shipped flavor / type | `purefoss` / `release` → `:app:assemblePurefossRelease` (FOSS: no Firebase/Crashlytics/Play, no `google-services.json` needed) |
| Versioning | `versionName` = `<VERSION_NAME>+<BUILD_NUMBER>` (e.g. `4.0.081+1`); `versionCode` = `(major*100000 + minor*1000 + patch) * 100 + BUILD_NUMBER` (e.g. `40008101`). Set in `gradle.properties`; `BUILD_NUMBER` resets to 1 on each upstream rebase, +1 on every build. Upstream's native `major*1e8+minor*1e5+patch` code is **not** used — it overflows the 32-bit cap once a fork reserve is multiplied in. |
| Keystore | `~/.android-keystores/shiroikuma-kabukahyoji.jks` (alias `kabukahyoji`). Credentials in gitignored `app/keystore.properties`; never committed. |
| Build | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss` — assembles purefoss release, copies the signed APK to `~/tmp/shiroikuma-kabukahyoji_<versionName>_arm64-v8a.apk`, and bumps `BUILD_NUMBER`. (JDK 17 isn't installed; JDK 21 builds AGP fine while targeting Java 17.) |
| SDK | `local.properties` (gitignored) → `sdk.dir=/home/shiroikuma/android-sdk` |

**Workflow rules (same as the sibling forks):** never `git commit`/`git push` unprompted — build, let
白い熊 test on-device, and push to `origin` only on an explicit **"Push."** After a build, always ask
(via `AskUserQuestion`) whether to `adb push` the APK to `/sdcard/tmp/`; never `adb install`. Two
skills automate this: **build-apk** (build + optional deploy) and **upstream-new-version** (rebase the
`custom` stack onto a newer upstream release). The fork customizations are confined to
`gradle.properties`, `app/build.gradle.kts`, and `app/src/purefoss/res/values/app_name.xml`.

The rest of this document describes the **upstream** codebase (shared with premnirmal/StockTicker).

## Modules

Two Gradle modules (`settings.gradle.kts`):

- `:app` — the application. All app logic under `app/src/main/java/com/github/premnirmal/ticker/`.
- `:UI` — an Android library (`com.github.premnirmal.tickerwidget.ui`) holding the Compose theme, design tokens, and shared Compose widgets. `:app` depends on it. Keep cross-cutting theming/Compose primitives here, not in `:app`.

## Build / test / lint commands

The app has three **product flavors** in the `mobile` dimension: `dev`, `prod`, `purefoss`. `dev` is the default for local/CI work. Most Gradle tasks are flavor- and build-type-specific (e.g. `assembleDevDebug`, `bundleProdRelease`).

```bash
# Build (what CI builds)
./gradlew :app:assembleDevDebug        # APK
./gradlew :app:bundleDevDebug          # AAB

# Unit tests (Robolectric/JVM — no device needed)
./gradlew testDevDebug                  # full suite, as CI runs it
./gradlew testDevDebugUnitTest --tests "com.github.premnirmal.ticker.network.StocksApiTest"          # single class
./gradlew testDevDebugUnitTest --tests "com.github.premnirmal.ticker.network.StocksApiTest.someTest" # single method

# Lint / style — detekt, autoCorrect is enabled (rewrites files)
./gradlew detekt                        # both :app and :UI; maxIssues=0, so any finding fails
```

Toolchain: JDK 17, Android `compileSdk`/`targetSdk` 36, `minSdk` 26, Kotlin 2.x, Jetpack Compose + Glance.

### Flavor differences (important)

`prod` includes Firebase Analytics, Crashlytics, and the Play in-app review API. `dev` and `purefoss` strip all of these (the Google Services and Crashlytics Gradle tasks are disabled for any non-`prod` variant in `app/build.gradle.kts`). This is implemented with **per-flavor source sets** — each of `app/src/{dev,prod,purefoss}/java/.../` provides its own copy of:

- `analytics/AnalyticsImpl.kt`
- `components/LoggingTree.kt`
- `home/AppReviewManager.kt`

When changing any of these, change **all three** flavor copies to keep them in sync. `prod` requires `app/google-services.json` (gitignored).

### Versioning (do not hand-edit)

`versionName` is derived at build time from the latest git tag (`git describe --tags`), and `versionCode` is computed as `major*100000000 + minor*100000 + patch`. See `buildSrc/.../GitHelpers.kt` and `VersionPropertiesTaskPlugin.kt`. The `version-code-change` workflow regenerates `app/version.properties` on tag push. To release a new version, push a `x.y.z` git tag — don't edit version numbers by hand.

### Dependency versions

Two mechanisms coexist:
- **refreshVersions** plugin manages most versions in `versions.properties` (run `./gradlew refreshVersions`). These appear in build files as typed accessors like `AndroidX.*`, `Square.*`, `Google.*`, `KotlinX.*`, `Testing.*`, `JakeWharton.*`.
- A **Gradle version catalog** at `gradle/libs.versions.toml` holds the rest, referenced as `libs.*`.

Release signing needs `app/keystore.jks` + `app/keystore.properties` (both gitignored).

## Architecture

### Dependency injection (Hilt + a legacy EntryPoint)

Hilt is the DI framework. `StocksApp` is `@HiltAndroidApp`; `AppModule` and `NetworkModule` (`@InstallIn(SingletonComponent)`) provide singletons. Activities/ViewModels use standard Hilt annotations.

Components that Hilt can't inject directly — `WorkManager` workers, `BroadcastReceiver`s, the widget providers, and `WidgetData` — instead pull dependencies through a manual entry point: `Injector.appComponent().inject(this)`, where `AppEntryPoint : LegacyComponent` (`components/AppComponent.kt`) is a Hilt `@EntryPoint`. If you add a new worker/receiver/widget needing injection, add an `inject(...)` overload to `LegacyComponent` and call `Injector.appComponent().inject(this)`.

### Data flow: quotes

`StocksProvider` (singleton, `model/`) is the in-memory source of truth for the portfolio. It exposes `tickers`, `portfolio`, `fetchState`, and `nextFetchMs` as `StateFlow`s, persists tickers/quotes via `StocksStorage` (Room), and calls `StocksApi` to fetch.

- `StocksApi` (`network/`) talks to **Yahoo Finance**, which requires a crumb/cookie auth handshake: an initial page load → cookie-consent POST → crumb fetch. The `"yahoo"`-named `OkHttpClient` (in `NetworkModule`) wires in `CrumbInterceptor`, the `YahooFinanceCookies` cookie jar, and a browser User-Agent. Endpoints live in `res/values/strings.xml` (`*_endpoint` strings), not hardcoded.
- Persistence is **Room**: `QuotesDB` (`@Database version 9`), DAO `QuoteDao`, entities in `repo/data/`, wrapped by `StocksStorage`. Schema changes require a new migration in `repo/migrations/Migrations.kt` registered in `AppModule`, plus a version bump.
- Networking stack: Retrofit + OkHttp, with kotlinx.serialization for JSON, `JsoupConverterFactory`/jsoup for HTML scraping, and SimpleXML for RSS news feeds.

### Refresh / scheduling

`AlarmScheduler` schedules the periodic `RefreshWorker` (WorkManager `CoroutineWorker`) and AlarmManager alarms. `RefreshWorker` only fetches when the network is online **and** the current time is within the configured update window (`isCurrentTimeWithinScheduledUpdateTime()` — trading-hours/day filtering), otherwise it no-ops or retries. `StocksProvider` applies exponential backoff on consecutive failures. `CleanupWorker` prunes stale data. `AppClock` abstracts the current time so scheduling logic is testable.

### Widgets

`WidgetDataProvider` (singleton) tracks per-widget config as `WidgetData` keyed by app-widget id and exposes a `StateFlow<List<WidgetData>>`. There are two widget implementations:
- `GlanceStocksWidget` — the modern Jetpack **Glance** (Compose) widget.
- `StockWidget` — the legacy `AppWidgetProvider` (RemoteViews).

Refreshes are triggered via `RefreshReceiver`. Widget configuration UI is `WidgetSettingsActivity`.

### UI (Compose)

Single-activity-style Compose app. `HomeActivity` hosts the navigation graph (`navigation/RootGraph.kt`, `HomeNavigation.kt`) with an adaptive list-detail layout (`HomeListDetail`, `ui/ListDetail.kt`, window-size classes). Each screen has its own `ViewModel` (e.g. `HomeViewModel`, `NewsFeedViewModel`, `QuoteDetailViewModel`, `WidgetsViewModel`, `SettingsViewModel`). `QuoteDetailActivity` shows per-stock detail and charts (MPAndroidChart). Theming/night-mode flows through `AppPreferences` and `ui/ThemeViewModel.kt` + the `:UI` module's theme. User prefs are stored in `SharedPreferences` (via `AppPreferences`) and DataStore.

## Tests

JVM unit tests use **Robolectric** + **Hilt testing** (`HiltTestApplication`) + Mockito-Kotlin + AssertJ + JUnit4. Extend `BaseUnitTest` (`app/src/test/java/.../BaseUnitTest.kt`), which sets up the Hilt rule and a mocked `StocksProvider`. JSON fixtures are loaded via the `Parser`/`Mocker` helpers in `app/src/test/java/.../tools` and `.../mock`. The test suite is small and focused (model/network); add fixtures alongside existing ones.

## Conventions

- Follow detekt (Kotlin style) — CI fails on any finding. Run `./gradlew detekt` (auto-correct) before pushing; see `CONTRIBUTING.md`.
- Contributions are GPL-licensed.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want Claude attribution in the history; this **overrides** the harness's default to append such a trailer. End commit messages at the last line of the body. (The existing history was scrubbed of these trailers on 2026-06-08; the global rule lives in `~/.claude/CLAUDE.md`.)
