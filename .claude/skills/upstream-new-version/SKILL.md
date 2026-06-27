---
name: upstream-new-version
description: Check premnirmal/StockTicker upstream for a newer release tag and bring the 白い熊 株価表示 fork (shiroikuma.kabukahyoji) up to it. Invoked as /upstream-new-version. Fetches upstream tags, compares the latest x.y.z release tag against the custom branch base, and if newer fast-forwards master, rebases the custom patch stack onto the new tag, reconciles conflicts, resets the fork build number, builds via the build-apk skill, and stops at the Push gate for on-device verification. Use when the user asks whether there is a new upstream StockTicker version, to update/sync to upstream, bump to the new release, or rebase custom onto the latest upstream.
---

# Sync the fork to a new upstream release

One-call runbook for "there is a new upstream StockTicker; rebuild our fork on it." The goal: keep
`master` a mirror of upstream, replay our `custom` customizations onto the new upstream **release
tag**, and produce a fresh `+1` build. Read the **build-apk** skill for the build/sign/deploy detail
this one references.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** The whole rebase + build
> happens on the local tree as a scratchpad — a rebase is freely re-runnable (`git rebase --abort`,
> or reset to `origin/custom`) right up until the user says **"Push."** Build, let the user test
> on-device, and only then push. (The build's APK delivery to the phone via `/after-build` is a
> separate, automatic step — no prompt.)

## Background — how versioning works here

- The root `gradle.properties` carries the fork version: `VERSION_NAME` (tracks the upstream release
  tag), `BUILD_NUMBER` (our increment), and `APP_ID`.
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"` (e.g. `4.0.081+1`).
- Fork `versionCode` = `(major*100000 + minor*1000 + patch) * 100 + BUILD_NUMBER` (e.g. `40008101`).
  This stays monotonic across upstream bumps and fits Android's signed 32-bit cap — upstream's native
  `major*1e8+minor*1e5+patch` code does **not** fit once multiplied for a fork reserve, which is why
  we re-derive it. Caps: major ≤ 214, patch ≤ 999, build ≤ 99.
- `BUILD_NUMBER` **resets to `1`** on each new upstream version, and the `buildFoss` task bumps it
  `+1` on every subsequent local build.

## Remotes & branches

- `origin` = the fork (SSH, push): `git@github.com:ShiroiKuma0/shiroikuma-kabukahyoji`.
- `upstream` = `https://github.com/premnirmal/StockTicker` (fetch-only). Add it if missing:
  `git remote add upstream https://github.com/premnirmal/StockTicker.git`.
- `master` mirrors `upstream/master` (fast-forward only, never carries our changes).
- `custom` carries our small commit stack, rebased onto each upstream **release tag**.

StockTicker tags releases as `x.y.z` (e.g. `4.0.081`) — it auto-tags very frequently, so a "new
version" is common and usually just a patch bump. Old bare-integer tags (`90`…`99`) predate the
scheme and are ignored.

## Preflight

- Working tree clean: `git status --short` is empty (note `local.properties` and
  `app/keystore.properties` are gitignored, so they never show). If dirty, stop and ask.
- On `custom`: `git rev-parse --abbrev-ref HEAD` is `custom` (else `git checkout custom`).
- Build env is NOT in non-interactive shells — export per build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

## Step 1 — Detect a new version

```bash
git fetch upstream --tags
git fetch origin
base=$(git describe --tags --abbrev=0 custom 2>/dev/null)            # release tag custom sits on
new=$(git tag -l | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1)   # newest upstream release tag
echo "base=$base  new=$new"
```
- **`new == base`** → already on the latest upstream release. Report it and **stop**.
- **`new` newer than `base`** → scope the jump, then proceed:
  ```bash
  git log --oneline "$base".."$new"            # what upstream changed
  git rev-list --count "$base"..custom         # how many of our commits replay
  git diff --name-only "$base"..custom | sort > /tmp/ours
  git diff --name-only "$base" "$new" | sort > /tmp/theirs
  comm -12 /tmp/ours /tmp/theirs               # files in BOTH = the only possible conflicts
  ```
  Historically the overlap is just `app/build.gradle.kts` and `gradle.properties`.

## Step 2 — Fast-forward `master` (local only; push deferred)

```bash
git checkout master
git merge --ff-only upstream/master
git checkout custom
```

## Step 3 — Rebase the `custom` stack onto the new tag

```bash
git branch "custom-pre-${new}-rebase"     # safety backstop
git rebase "$new"
```
Resolve conflicts (Step 4), then `git rebase --continue` until "Successfully rebased". If it goes
sideways: `git rebase --abort` restores the pre-rebase state; the safety branch is the deeper net.

## Step 4 — Reconcile conflicts (resolve at the root, never blindly)

Our customizations are a small, additive layer; conflicts are usually confined to:

- **`app/build.gradle.kts`** — our fork commit removed upstream's `getVersionNameFromGit()` version
  block and added the top-level fork-version vals (`forkVersionName`/`forkVersionCode`/`appIdBase`),
  the `defaultConfig` `versionCode`/`versionName` wiring, the `signingConfigs` rewrite, and the
  `buildFoss` task. If upstream edits any of those regions:
  - **Keep ours**: the fork-version vals block, `versionCode = forkVersionCode`,
    `versionName = forkVersionName`, `applicationId = appIdBase`, the keystore.properties signingConfig,
    and the `buildFoss` task.
  - **Take upstream**: any new compileSdk/minSdk/targetSdk/AGP/Kotlin/dependency changes, and any new
    `defaultConfig`/`buildConfigField` additions — port them in alongside our version wiring.
- **`gradle.properties`** — keep our fork block (`VERSION_NAME` / `BUILD_NUMBER` / `APP_ID`); take any
  upstream changes to the other Gradle flags.
- **`app/src/purefoss/res/values/app_name.xml`** — only if upstream touches it; keep `白い熊 株価表示`.

After resolving, confirm no markers remain:
```bash
git grep -nE '^(<<<<<<<|=======|>>>>>>>)' && echo "MARKERS LEFT" || echo "clean"
```

## Step 5 — Point versioning at the new upstream and reset the build number

In `gradle.properties`:
- Set `VERSION_NAME` to the **new** tag (e.g. `4.0.082`).
- **Reset `BUILD_NUMBER=1`** (the first build of the new line is `<new>+1`).

Sanity-check the script still evaluates and reports the expected version:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:help -q < /dev/null | grep '^Fork build:'
# expect: Fork build: <new>+1 (versionCode …), applicationId shiroikuma.kabukahyoji
```

## Step 6 — Verify our customizations survived

| What | Expected value | Where |
| --- | --- | --- |
| Installed app id | `shiroikuma.kabukahyoji` | `gradle.properties` → `APP_ID` (used as `appIdBase`) |
| Code namespace | `com.github.premnirmal.tickerwidget` (unchanged) | `app/build.gradle.kts` → `namespace` |
| App launcher label | `白い熊 株価表示` | `app/src/purefoss/res/values/app_name.xml` |
| Fork version logic | `forkVersionName` / `forkVersionCode` + `buildFoss` task | `app/build.gradle.kts` |
| `applicationId = appIdBase` | not the upstream literal | `app/build.gradle.kts` defaultConfig |
| Signing from keystore.properties | yes | `app/build.gradle.kts` signingConfigs |

## Step 7 — Build the new `+1`

Build via the **build-apk** skill (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
< /dev/null`), verify the APK (mtime + `aapt2` badging), then deliver it via **`/after-build`** (auto:
`/adb-push` if a phone is connected, else `/scp` to skhw — no prompt). This is
the first build of the new upstream line (`<new>+1`).

## Step 8 — Stop, then push only on "Push."

Report to the user: the jump (`$base` → `$new`), the new versionName/code, what upstream changed, and
any non-trivial reconciliation. Let them install and verify on-device. **Do NOT push. Wait for the
user to type "Push."**

After "Push.":
```bash
git push --force-with-lease origin custom    # rebase rewrote history, so force-with-lease
git push origin master                       # fast-forward mirror
git fetch origin
[ "$(git rev-parse custom)" = "$(git rev-parse origin/custom)" ] && echo "custom landed"
git branch -D "custom-pre-${new}-rebase"     # drop the safety branch once confirmed
```

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history)
  over merging, so the customization set stays easy to audit and replay.
- If upstream restructures a file we customize, port our change to the new structure rather than
  forcing the old diff.
- A build can silently ship a stale APK — always check mtime + the `aapt2` identity probe (build-apk
  Step 3). Never delete old APKs on the device.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
