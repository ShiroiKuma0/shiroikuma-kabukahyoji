---
name: build-apk
description: Build the signed purefoss release APK for the 白い熊 株価表示 fork (shiroikuma.kabukahyoji) via the buildFoss Gradle task, then always ask whether to push it to the connected phone via adb. Always build first without asking for permission to build — the ONLY question you ever ask is the adb-push question afterward. Use whenever the user asks to build the app, build the APK, make a release build, or build and push to the phone.
---

# Build the purefoss release APK and optionally push to phone

> **Always build, then inquire about pushing — every time.** When this skill applies (the user
> asked to build, OR you just implemented code changes the user requested), run the build
> immediately and without asking permission. Do **not** ask "shall I build?" / "want me to run
> buildFoss?" — that question is wrong. The **only** question in this whole flow is the
> `AskUserQuestion` about the `adb push`, asked **after** a successful build. So: always build,
> *then* ask about the push.

> **A compile-only check never ends the flow.** A config check or `assemble…` to catch an error is
> fine as a fast intermediate step while iterating, but it is **not** "the build" and does **not**
> replace the push inquiry. Whenever the changes are ready, finish with the full signed build
> (`./gradlew buildFoss`) **and** the `AskUserQuestion` push prompt — never leave the turn at a
> compile-check.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK goes to
> `/sdcard/tmp/<apk name>` — **never** `/sdcard/Download/` or anywhere else. Create `/sdcard/tmp`
> if needed and push there. **Never delete old APKs on the device** — leave prior
> `/sdcard/tmp/shiroikuma-kabukahyoji_*.apk` in place; the version in the filename keeps builds apart.

> **Never run `adb install` (or `pm install`).** The build step may copy the APK to the phone with
> `adb push` — and only after confirming with the user — but **the user installs the APK themselves**
> from the phone's file manager. Do not install it for them under any circumstances.

> **Never `git commit` or `git push` on your own.** Building does not include committing. After
> building (and the optional `adb push`), the user tests the build themselves. **Only when the user
> explicitly says "Push"** do you then `git commit` the changes and `git push origin custom`. The
> user's **"Push"** means *commit-and-push-to-the-fork* — it is unrelated to the `adb push` file copy.

> **ALWAYS end every build by asking — via `AskUserQuestion` — whether to `adb push` the APK to
> `/sdcard/tmp/`.** Mandatory for *every* successful build, even verification builds and even when
> the user didn't mention pushing. Do **not** settle for asking in prose — fire the `AskUserQuestion`
> prompt as the final step.

## What this fork builds

Personal fork of **premnirmal/StockTicker** (an Android stocks-widget app), sideloaded side-by-side
with any official build. Identity: app id `shiroikuma.kabukahyoji`, label `白い熊 株価表示`. We ship the
**purefoss** flavor (the FOSS variant — no Firebase/Crashlytics/Google Play services, so no
`google-services.json` is needed). See `CLAUDE.md` → "Fork layer" for the full picture.

## Steps

1. **Note the output filename.** The version is driven by the root `gradle.properties`:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER|APP_ID' gradle.properties`
   - The APK will be `shiroikuma-kabukahyoji_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`
     (e.g. `shiroikuma-kabukahyoji_4.0.081+1_arm64-v8a.apk`), using the `BUILD_NUMBER` value
     **before** the build (the task bumps it afterward).
   - versionCode for that build = `(major*100000 + minor*1000 + patch) * 100 + BUILD_NUMBER`
     (e.g. `4.0.081` build `1` → `40008101`).

2. **Build** (needs JDK 17+; the default `java` on this machine is JDK 11, so export JDK 21 which
   builds AGP fine while still targeting Java 17 bytecode):
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null`
     (the `< /dev/null` guarantees it never blocks on stdin)
   - This runs `:app:assemblePurefossRelease`, copies the signed APK to `~/tmp/<apk name>`, and
     auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> <path>` and `>>> versionCode <n>`; use those to confirm the exact filename
     and code, and confirm `BUILD SUCCESSFUL`.

3. **Verify before claiming success** (incremental Gradle / R8 can silently ship a stale APK):
   - `ls -lh ~/tmp/<apk name>` — the mtime must be the current build, not a prior one.
   - Probe the packaged identity with `aapt2` (the `白い熊` label greps to 0 from `strings` on
     `resources.arsc` due to UTF-16 — trust the `aapt2` line, not a `strings` grep):
     ```bash
     aapt2=$(ls ~/android-sdk/build-tools/*/aapt2 | sort -V | tail -1)
     "$aapt2" dump badging ~/tmp/<apk name> | grep -E "package: name|versionName|application-label:"
     # expect: name='shiroikuma.kabukahyoji'  versionName='<VER>+<BUILD>'  application-label:'白い熊 株価表示'
     ```

4. **At the end of every build, ALWAYS ask** via `AskUserQuestion` whether to push the APK to the
   phone — no exceptions, no assuming, no asking only in prose. Options: "Yes, push via adb" / "No,
   just build". Fire this as soon as the build reports `BUILD SUCCESSFUL`.

5. **If yes, push directly yourself:**
   - `adb devices` — confirm a device is connected.
   - `adb shell mkdir -p /sdcard/tmp`
   - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
   - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).
   - Never `adb install` — the user installs manually from `/sdcard/tmp/`.
   - If `adb devices` is empty: leave the `~/tmp` copy in place, tell the user, and push once a
     device is connected.

## Note — push directly, do not rely on a task prompt

The `buildFoss` task (`app/build.gradle.kts`) has **no** interactive prompt — it only builds, copies
the APK to `~/tmp`, and bumps `BUILD_NUMBER`. Asking the user and running `adb push` is Claude's job
(steps 4–5), done conversationally.

## Signing

Release signing is non-interactive: `app/build.gradle.kts`'s `release` signingConfig reads
`app/keystore.properties` (falling back to `SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` /
`SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD` env vars). This fork uses
`~/.android-keystores/shiroikuma-kabukahyoji.jks` (alias `kabukahyoji`, passphrase `kabukahyoji123`);
`app/keystore.properties` is gitignored. If neither the file nor the env vars are present the build is
unsigned and the APK will not install. Recreate `app/keystore.properties` as:

```
storeFile=/home/shiroikuma/.android-keystores/shiroikuma-kabukahyoji.jks
storePassword=kabukahyoji123
keyAlias=kabukahyoji
keyPassword=kabukahyoji123
```

## Prerequisites — SDK and tags

- The Android SDK path comes from `local.properties` (gitignored): `sdk.dir=/home/shiroikuma/android-sdk`.
  Recreate it on a fresh checkout if missing.
- The build does **not** require git tags (version comes from `gradle.properties`), but a fresh clone
  has none — fetch them once with `git fetch upstream --tags` if the in-app changelog fields matter.
