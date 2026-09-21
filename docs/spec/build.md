# Specification — Build (`BUILD`)

The Gradle build for the Android app: the project skeleton, how it is compiled, how its tests are
run, and what continuous integration checks on a pull request.

There is no app behaviour here. This page specifies only the scaffolding everything else attaches
to, and it is deliberately the smallest scaffolding that compiles, tests and assembles.

---

## Invariants

> **Invariant — the build runs from a clean checkout with nothing installed but a JDK.** No step
> may depend on a pre-existing Android SDK, a locally installed Gradle, an Android Studio setting,
> or any file that version control does not carry. `local.properties` is ignored by git and must
> never be required.

> **Invariant — every version is pinned in the repository.** Plugin, dependency and Gradle
> distribution versions are literals. No dynamic selectors (`+`, `latest.release`), no snapshot
> repositories: a build whose inputs can change underneath it is not reproducible.

> **Invariant — a framework or a dependency arrives with the feature that justifies it, never ahead
> of one.** Amended from "the skeleton stays a skeleton" per
> [ADR 0004](../adr/0004-jetpack-compose-with-material-3.md), which needed this invariant to yield
> on its own terms rather than be broken: v1's six screens are the feature that justifies Compose,
> and the semantics-tree tests over the component vocabulary and its gallery fixture
> ([`docs/spec/design-system.md`](design-system.md) `DS-091`, `DS-095`) are the feature that
> justifies Robolectric (`BUILD-023`) — arriving with the gallery
> [#78](https://github.com/derekwinters/Interval-trainer-android/issues/78) builds, since `DS-095`
> names the gallery as exactly what those assertions run against, rather than waiting on the six
> screens themselves. The invariant still forbids speculation — nothing is added because it might be
> useful, and nothing added stays added once the feature it was for is gone — it no longer forbids
> a user-interface framework, annotation processing, or a test dependency needing a simulated
> Android runtime by name, because v1 now needs at least one of each.

> **Invariant — `VERSION_CODE` only ever goes up, and nothing but the release workflow changes
> it.** Google Play refuses any upload whose version code is not strictly greater than the last
> one accepted, forever — a manual edit that guesses wrong is not recoverable after the fact. The
> bump step (`BUILD-015`) is the only writer, and it always computes the next value from what the
> base branch last released rather than from whatever the release pull request's branch already
> carries, so a workflow re-run that finds it already correct is a no-op, never a second bump.

> **Invariant — a push that leaves no pending release pull request must not fail the release
> workflow, or block the APK attach for a release that same push did create.**
> `steps.release.outputs.pr` is empty exactly when a push needs no further release pull request —
> the ordinary state immediately after merging one. GitHub Actions template-compiles a step's
> `env:` expressions before checking that step's own `if:`, so parsing that empty string as JSON
> inside an `env:` block fails the whole job regardless of the gate, even though the job's actual
> release-creation work already succeeded
> ([#114](https://github.com/derekwinters/Interval-trainer-android/issues/114), `BUILD-066`). The
> JSON `steps.release.outputs.pr` carries is therefore only ever parsed inside a step's `run:`
> script, never inside its `env:`.

---

## 1. Project layout

- **BUILD-001** The Gradle wrapper — `gradlew`, `gradlew.bat` and both files under
  `gradle/wrapper/` — is committed, and is the only supported way to run the build.
  *(manual: asserted by the pull-request workflow, which invokes `./gradlew` and nothing else.)*
- **BUILD-002** `settings.gradle.kts` names the root project and includes four modules,
  `:app`, `:core` (pure Kotlin, [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md)),
  `:designsystem` (Compose, [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md),
  `BUILD-019`) and `:database` (Kotlin Multiplatform,
  [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md), [`docs/spec/schema.md`](schema.md)
  `SCHEMA-001`). *(manual: a build-configuration fact; the workflow's build is the check.)*
- **BUILD-003** The root `build.gradle.kts` declares each plugin's version once for the whole
  build and applies none of them itself. *(manual: as BUILD-002.)*
- **BUILD-004** Dependency repositories are `google()` and `mavenCentral()`, declared centrally in
  `settings.gradle.kts`; modules declare none of their own. Plugin resolution additionally uses the
  Gradle Plugin Portal. *(manual: enforced by `FAIL_ON_PROJECT_REPOS`, which fails the build.)*

## 2. The app module and `:core`

- **BUILD-010** `:app` applies the Android application plugin and the Kotlin Android plugin, and
  sets its namespace, application id, `compileSdk`, `minSdk` and `targetSdk` explicitly. **`minSdk`
  is raised from 24 to 26** ([#24](https://github.com/derekwinters/Interval-trainer-android/issues/24),
  decided 2026-09-10): API 26 (Android 8.0) is the floor the foreground-service notification needs,
  since notification channels — required to post any notification from API 26 onward — are what the
  service's ongoing notification depends on ([`docs/spec/service.md`](service.md) §3). The build
  sets `minSdk = 26`. *(manual: a build-configuration fact; `assembleDebug` in the workflow is the
  check.)*
- **BUILD-011** Java and Kotlin both compile to JVM bytecode target 17, so the two never disagree
  about the class-file version. *(manual: a mismatch fails compilation in the workflow.)*
- **BUILD-012** The module declares one launcher activity, and `./gradlew assembleDebug` produces a
  debug APK. *(manual: verified by the workflow; producing an APK needs the Android SDK.)*
- **BUILD-013** `gradle.properties` is the single source of truth for the app's version:
  `VERSION_NAME=0.1.0 # x-release-please-version` and `VERSION_CODE=1`. `:app`'s
  `build.gradle.kts` reads both with `findProperty`, stripping the trailing marker comment from
  `VERSION_NAME` before use — Java properties do not treat an inline `#` as a comment — and sets
  `defaultConfig.versionName` and `defaultConfig.versionCode` from them. `.github/release-please/config.json`
  declares a `generic` extra-file entry for `gradle.properties` under the `.` package, so
  release-please's generic updater rewrites the `VERSION_NAME` line (matching on its marker
  comment) on every release pull request; the manifest is the only other file that holds the
  version. `buildConfig` is turned on (`buildFeatures.buildConfig = true`), so
  `BuildConfig.VERSION_NAME` is available to the app. How `VERSION_CODE` advances on release is
  `BUILD-015`, below. *(manual: a build-configuration fact; `assembleDebug` producing an APK with
  `versionName` `0.1.0` in the workflow is the check.)*
- **BUILD-014** `:core` applies the Kotlin JVM plugin, never the Android library plugin, per
  [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). `android.*` is not on
  `:core`'s compile classpath, so an import of it is a compile error rather than a review
  comment — the invariant that module choice exists to enforce. *(manual: a build-configuration
  fact; a violation fails compilation.)*
- **BUILD-015** `VERSION_CODE` advances on every release pull request
  ([#12](https://github.com/derekwinters/Interval-trainer-android/issues/12)). release-please's
  generic updater cannot do this itself — its markers (`x-release-please-version`, `-major`,
  `-minor`, `-patch`) all substitute a piece of the semver value it just computed, and none of them
  is a freestanding counter it can increment independently of that — so a "Bump `VERSION_CODE`"
  step in `release-please.yml` runs `bump_version_code.py` after `release-please-action` opens or
  updates the release pull request. That script reads the `VERSION_CODE` last released on the base
  branch, sets the pull request's branch to one more than that, and pushes the change only if the
  value actually differs — recomputing from the base branch rather than incrementing whatever the
  pull request already carries, so a re-run the workflow makes while the pull request is still open
  is a no-op instead of a second bump. The step reads `steps.release.outputs.pr` — the JSON
  `release-please-action` emits describing the pull request it opened or updated, carrying
  `baseBranchName` and `headBranchName` — as a plain string into its `env:` and parses it with
  `jq` inside its `run:` script; it never calls `fromJSON()` on that value inside `env:` itself,
  since GitHub Actions template-compiles a step's `env:` expressions before checking that step's
  `if:`, so a `fromJSON()` there would fail the whole job on the ordinary push that has no pull
  request to bump (`steps.release.outputs.pr` empty), regardless of the `if:` gate meant to skip
  it (see the invariant above and
  [#114](https://github.com/derekwinters/Interval-trainer-android/issues/114), `BUILD-066`).
  *(auto:
  `.github/scripts/tests/test_bump_version_code.py`, standard library only, no Android SDK, no
  release-please run.)*
- **BUILD-016** The Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) is
  declared once in the root `build.gradle.kts` (`BUILD-003`), at the same literal version as the
  Kotlin Gradle plugin — the compiler ships from the Kotlin repository and is version-locked to it,
  never chosen independently. `:app` applies it alongside the Android and Kotlin plugins and turns
  on `buildFeatures.compose`. *(manual: a build-configuration fact; the workflow's build is the
  check.)*
- **BUILD-017** `:app` imports the Compose BOM (`androidx.compose:compose-bom`) as a platform and
  declares every `androidx.compose.*` artifact it needs — `ui`, `ui-graphics`,
  `ui-tooling-preview`, `foundation`, and `ui-tooling` as `debugImplementation` — through it, so one
  literal BOM version pins all of them together. `androidx.activity:activity-compose` and
  `androidx.navigation:navigation-compose`, which the BOM does not manage, carry their own literal
  versions. Every version here is a literal — the BOM's own version included — per the build's
  second invariant. **`androidx.compose.material3` is deliberately not among them:**
  [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md) narrowed ADR 0004's
  `:app`-level Material 3 dependency to `:designsystem`, declared there once as `implementation`
  (`BUILD-019`); `:app` still carries only the Compose runtime, activity and navigation artefacts
  this requirement adds, and nothing that reads `androidx.compose.material3` directly. `:app` now
  also depends on `:designsystem` itself ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79),
  the home screen, the first screen that needed its vocabulary) and gains its exposed vocabulary —
  `AppTheme`, `ScreenHeader`, `ListLayout`, the button and icon-button set — through that dependency,
  never through a `material3` import of its own; and, separately from `material3`,
  `androidx.compose.material:material-icons-extended`, which `:designsystem`'s own
  `build.gradle.kts` already notes ships icon data rather than a Material *component*, so `:app`
  depending on it directly does not touch this requirement's own module boundary. `:app` needs the
  `-extended` artifact rather than `:designsystem`'s `-core` one because the running screen's
  transport controls ([#81](https://github.com/derekwinters/Interval-trainer-android/issues/81))
  use `Pause`, `SkipNext`, `Stop`, `VolumeOff` and `VolumeUp`, none of which are in `-core`'s small
  curated icon set; `-extended` is a strict superset of `-core`; a module declares one or the
  other, never both. *(manual: a build-configuration fact; the workflow's build is the check.)*
- **BUILD-018** `:app` had exactly one activity, `MainActivity`, a `ComponentActivity` whose
  `onCreate` called `setContent` with a single Compose `NavHost` holding exactly one destination — a
  placeholder with no behaviour of its own and no `MaterialTheme` wrapper — until the home screen
  ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79),
  [`docs/spec/screens.md`](screens.md) §1) became its first real one. `MainActivity` now wraps its
  `NavHost` in `:designsystem`'s `AppTheme` (`BUILD-017`) and the graph holds `home`, the preset
  editor ([#80](https://github.com/derekwinters/Interval-trainer-android/issues/80),
  `docs/spec/screens.md` §2, `PresetEditorScreen`), the running screen
  ([#81](https://github.com/derekwinters/Interval-trainer-android/issues/81), `docs/spec/screens.md`
  §3, `RunningScreen`), and the summary screen
  ([#82](https://github.com/derekwinters/Interval-trainer-android/issues/82), `docs/spec/screens.md`
  §4, `SummaryScreen`) as real destinations, plus a placeholder destination for settings — the one
  `docs/spec/screens.md` name that is not built yet — replaced, unchanged route, the moment its own
  issue lands. This remains the shell every later screen issue adds a real destination to, not a
  screen itself: one of the six v1 screens still does not exist. Its start
  destination is no longer always `home`: `#81` reads `WorkoutServiceState` once, synchronously, at
  composition (`docs/spec/screens.md` `SCREEN-043`), so a cold start with a workout already running
  or paused lands on `running` instead. *(manual: a build-configuration/UI-shell fact with no
  computable behaviour to unit test; `assembleDebug` producing an APK that launches it is the check,
  the same as `BUILD-012`.)*
- **BUILD-019** `:designsystem` applies the Android library plugin, the Kotlin Android plugin and
  the Compose compiler plugin (`BUILD-016`) — an Android library rather than pure Kotlin like
  `:core` (`BUILD-014`), since it hosts Compose UI, per
  [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md). It imports the same
  literal Compose BOM version `:app` does (`BUILD-017`) and declares
  `androidx.compose.material3:material3` through it as `implementation`, never `api`: Gradle does
  not expose an `implementation` dependency on a consuming module's compile classpath, so a raw
  Material component reached for outside `:designsystem` is a compile error, not a convention
  someone has to remember (`DS-090`). *(manual: a build-configuration fact; a raw
  `androidx.compose.material3` import from `:app` failing to compile, once `:app` depends on this
  module, is the check.)*

## 3. Tests

- **BUILD-020** `./gradlew test` runs the JVM unit tests in every module — `:app`, `:core`,
  `:designsystem` and `:database`'s `jvm()` target — and a failing test in any of them fails the
  build. *(manual: a test cannot assert the behaviour of the runner that is running it; the
  workflow is the check.)*
- **BUILD-021** The unit tests run on the JVM alone — no emulator, no connected device, no
  simulated Android runtime — so they need nothing but a JDK and the dependencies on the test
  classpath. *(manual: absence of such a dependency; adding one would show in the diff.)*
- **BUILD-022** At least one unit test exercises production Kotlin code, so that a wrong or missing
  implementation makes it red. A test that only asserts a constant satisfies nothing. *(manual:
  satisfied by the tests for BUILD-030–033 below, which call production code now in `:core`.)*
- **BUILD-023** Robolectric is a `:designsystem`-only test dependency, scoped to its Compose
  semantics-tree tests alone ([`docs/spec/design-system.md`](design-system.md) `DS-091`, `DS-093`) —
  it is not adopted for `:core` or for `:database`, both of which are tested with no simulated
  Android runtime at all ([ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md),
  [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md)). Its `android-all` jar is
  pre-fetched into, and cached from, Robolectric's own default local Maven repository in continuous
  integration, never vendored and never fetched live inside the `./gradlew test` invocation that
  gates a pull request (`DS-092`), which is what keeps `BUILD-021`'s clean-checkout invariant
  satisfied for that invocation while
  `:designsystem`'s tests use a simulated runtime deliberately. *(manual: a dependency-scope and
  continuous-integration fact.)*

## 4. Duration formatting

`formatSeconds` is the small piece of production code the unit tests exercise. It is a placeholder
chosen to be useful and unambiguous rather than a claim about the app: interval-trainer behaviour
proper is not specified yet, and the first feature to need a real duration type may replace it.

- **BUILD-030** `formatSeconds` renders a whole number of seconds as `m:ss` — minutes unpadded and
  unbounded, seconds always two digits. `0` is `0:00`, `65` is `1:05`, `600` is `10:00`.
- **BUILD-031** Minutes are not wrapped at an hour: `3600` is `60:00`. An interval timer counts the
  time remaining, and silently restarting at zero would misreport it.
- **BUILD-032** A negative input raises `IllegalArgumentException`. There is no sensible `m:ss` for
  a negative duration, and formatting one anyway would hide the arithmetic error that produced it.
- **BUILD-033** The digits are ASCII whatever the device locale is. `String.format("%d")` renders
  in the default locale's numbering system, so a phone set to one that does not use Western digits
  would otherwise show a timer nobody else can read.

## 5. Continuous integration

- **BUILD-040** A `pr.yml` workflow runs on pull requests targeting the default branch.
  *(manual: a workflow trigger; observable only by opening a pull request.)*
- **BUILD-041** It runs `./gradlew test` and `./gradlew assembleDebug`, and fails when either
  fails. It also runs the release-signature gate's unit tests, which are Python and need neither
  the JDK nor the Android SDK (`SIGN-060` in [`signing.md`](signing.md)). *(manual: as
  BUILD-040.)*
- **BUILD-042** It grants `contents: read` and no other permission. *(manual: a workflow
  permission block; reviewed in the diff.)*
- **BUILD-043** Every `uses:` reference is a full 40-character commit SHA followed by a comment
  naming the version it pins. *(manual: checked by ai-sdlc's action-pin gate, which reads the
  workflow files; see `VAL-050`–`VAL-056` in ai-sdlc.)*
- **BUILD-044** The workflow provisions its own JDK and Android SDK rather than relying on what a
  runner image happens to carry. *(manual: as BUILD-040.)*
- **BUILD-045** The `closing-keyword` job in `.github/workflows/closing-keyword.yml` — the caller
  of the shared `derekwinters/ai-sdlc` closing-keyword action, itself out of this repository's
  reach — is skipped for a pull request that already carries the `autorelease: pending` label:
  `if: "${{ !contains(github.event.pull_request.labels.*.name, 'autorelease: pending') }}"`. The
  whole expression is quoted because the label's own text contains a `: ` that would otherwise
  read as a second YAML mapping key on the same line — an unquoted `if:` here is invalid YAML, not
  merely unconventional. release-please-action applies that label to its own release pull requests
  from the moment it opens or updates them. A release pull request aggregates many already-closed
  issues into one
  changelog body and never itself closes anything new, so its body can only ever carry a
  compare-log bullet's markdown-linked `closes [#n](...)`, never the plain `closes #n` /
  `fixes #n` / `resolves #n` the shared action's pattern matches — the gate was structurally
  unable to pass on such a pull request. Before this, a human had to notice the failing check on
  every release and apply this repository's own `no-closing-keyword` label by hand
  ([#92](https://github.com/derekwinters/Interval-trainer-android/pull/92),
  [#116](https://github.com/derekwinters/Interval-trainer-android/pull/116)); this requirement
  automates exactly that manual step, keyed off the label release-please already applies for its
  own purposes rather than a new one invented for this. This is a local, repo-specific addition to
  the caller workflow, not a change to the shared action itself — `closing-keyword.yml` carries an
  `adopt`-managed header (see this file's own comment) and is not touched by `adopt` while this
  local edit stands, the same tradeoff a hand-edit to any `adopt`-managed file makes.
  *(auto: `.github/scripts/tests/test_closing_keyword_workflow.py`, standard library only, no
  Android SDK, no real pull request.)*

## 6. Release build and attach

`release-please.yml` creates the GitHub Release; this section is the second job in that same
workflow run that builds the versioned APK and attaches it, so it exists before anyone looks for
it. A separate workflow triggered by the tag release-please pushes would never run it: that tag is
pushed with the default `GITHUB_TOKEN`, and GitHub does not start workflow runs from events that
token itself initiated, and even if it did, a tag-triggered run executes the workflow file frozen
at that tag rather than whatever is on `main` today.

- **BUILD-050** `:app`'s `release` signing config is created only when all four of
  `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
  `ANDROID_KEY_ALIAS_PASSWORD` are present in the environment — the same secrets `SIGN-002` names,
  with `ANDROID_KEYSTORE_PATH` holding the filesystem path the workflow decodes
  `ANDROID_KEYSTORE_BASE64` into. When any is missing, `release` gets no signing config at all,
  never `signingConfigs.debug`: an unsigned `assembleRelease` output is a visible failure, while a
  debug-signed one is the quiet failure `SIGN-042` exists to catch. *(manual: a build-configuration
  fact; SIGN-042/SIGN-043 are what the release gate reports when this is wrong.)*
- **BUILD-051** `onVariants` in `:app`'s `build.gradle.kts` names every variant's APK output
  `interval-trainer-<versionName>-<buildType>.apk`, so a workflow globs
  `app/build/outputs/apk/<buildType>/*.apk` without renaming it. *(manual: `assembleDebug` in
  `pr.yml` producing `interval-trainer-<versionName>-debug.apk` is the check exercised on every
  pull request; `assembleRelease` producing `interval-trainer-<versionName>-release.apk` is
  exercised only in `release-please.yml`.)*
- **BUILD-052** `release-please.yml`'s `release-please` job exposes `release_created` and
  `tag_name` as job outputs, taken from the `googleapis/release-please-action` step's own outputs
  of the same names. *(manual: a workflow output; reviewed in the diff.)*
- **BUILD-053** A `build-and-attach` job `needs: release-please` and runs only when its
  `release_created` output is `'true'`. It checks out `tag_name` — not the commit that triggered
  the run — since that is the exact commit release-please tagged, with the version bump the
  release names already applied. *(manual: as BUILD-052.)*
- **BUILD-054** `build-and-attach` provisions the JDK, Android SDK and Gradle the same way `pr.yml`
  does (`BUILD-044`), runs `./gradlew assembleRelease`, and runs
  `.github/scripts/verify_release_signature.py` (`SIGN-052`) against the result before attaching
  anything. A failed verification fails the job; nothing is uploaded. *(manual: as BUILD-052.)*
- **BUILD-055** On success, `build-and-attach` uploads `app/build/outputs/apk/release/*.apk` to the
  GitHub Release identified by `tag_name`, using the pre-installed `gh` CLI rather than a new
  action — there is nothing a dedicated upload action would do here that `gh release upload`
  does not, and every action adopted is a `uses:` reference `BUILD-043` requires pinning for no
  gain (the same reasoning `SIGN-061` applies to the signature gate's own CI step). *(manual: as
  BUILD-052.)*
- **BUILD-056** `release-please.yml` adds no `on: push: tags` trigger. *(manual: absence of a
  trigger; reviewed in the diff.)*
- **BUILD-057** `release-please.yml`'s workflow-level permissions remain exactly
  `contents: write` and `pull-requests: write`. `build-and-attach` declares no permissions block of
  its own — it inherits those two from the workflow level, which is already enough to upload a
  release asset, so nothing new is granted. *(manual: a workflow permission block; reviewed in the
  diff.)*
- **BUILD-058** Every new `uses:` reference in `release-please.yml` is a full 40-character commit
  SHA followed by a comment naming the version it pins, per `BUILD-043`. *(manual: as BUILD-043.)*

## 7. Release candidate

release-please's own pull request is the last point at which a release can be tried before it
exists: this section builds the same versioned, release-signed APK `build-and-attach` builds
(`BUILD-050`–`058`), but on that pull request rather than on the tag, so it can be installed and
checked before the merge that tags it.

- **BUILD-059** `release-candidate.yml` triggers on `workflow_run`, naming the `pr` workflow
  (`workflows: ["pr"]`, matched by `pr.yml`'s `name:` field, not its filename) and `types:
  [completed]`, rather than on `pull_request` directly. This is not a workaround for `SIGN-062`
  (`docs/spec/signing.md`) — a `workflow_run`-triggered job runs in the base repository's trust
  context, never the incoming pull request's, so it satisfies that invariant's actual intent (a
  pull request from anywhere must never be able to reach the release key) rather than merely
  evading its literal `pull_request:`-trigger pattern match. `pull_request`, which SIGN-062
  forbids from ever referencing the release keystore secrets, cannot build the signed candidate
  this section needs. *(manual: a workflow trigger; observable only by opening a pull request and
  watching `pr` complete on it.)*
- **BUILD-060** The job runs only when `github.event.workflow_run.conclusion == 'success'` and
  `github.event.workflow_run.head_branch` starts with `release-please--`, so `pr` completing on
  any other branch — including a fork, since `workflow_run` still fires for those — leaves the job
  skipped rather than merely non-required. *(manual: as BUILD-059.)*
- **BUILD-061** The job checks out `github.event.workflow_run.head_sha` — the exact commit `pr`
  just validated — rather than a ref name. `workflow_run` does not check out the triggering commit
  the way `pull_request` does, and a ref name can move between `pr` finishing and this job
  starting. *(manual: as BUILD-059.)*
- **BUILD-062** The job runs `./gradlew test` and `./gradlew assembleRelease`, then uploads
  `app/build/outputs/apk/release/*.apk` — named `interval-trainer-<versionName>-release.apk` per
  `BUILD-051` — as a workflow artifact named `app-release-candidate`. *(manual: as BUILD-059.)*
- **BUILD-063** The job provisions the JDK, Android SDK and Gradle the same way `pr.yml` does
  (`BUILD-044`) and signs the build the same way `build-and-attach` does (`BUILD-050`): it decodes
  `ANDROID_KEYSTORE_BASE64` to a file under `RUNNER_TEMP`, sets the four `ANDROID_KEYSTORE_*`/
  `ANDROID_KEY_*` values `assembleRelease` needs, and removes the decoded keystore file
  afterward regardless of outcome. *(manual: as BUILD-059.)*
- **BUILD-064** `release-candidate.yml` grants `contents: read` and no other permission — the
  workflow publishes nothing beyond the artifact upload in `BUILD-062`, which needs nothing more.
  *(manual: a workflow permission block; reviewed in the diff.)*
- **BUILD-065** Every `uses:` reference, including `actions/upload-artifact` which no other
  workflow here uses yet, is a full 40-character commit SHA followed by a comment naming the
  version it pins, per `BUILD-043`. *(manual: as BUILD-043.)*

## 8. Recovering a missed release APK

[#114](https://github.com/derekwinters/Interval-trainer-android/issues/114): the `release-please`
job failed on the push that tagged `v0.2.0`, because the "Bump VERSION_CODE" step's `env:` block
called `fromJSON()` on `steps.release.outputs.pr`, which is empty on exactly the push that just
tagged a release and needs no further release pull request. `build-and-attach` never ran as a
result, so `v0.2.0`'s GitHub Release has no signed APK attached. This section describes the fix and
the manual path that backfills what that failure skipped.

- **BUILD-066** The "Bump VERSION_CODE on the release pull request" step's `env:` carries
  `steps.release.outputs.pr` as a plain string (`PR_JSON`) and never calls `fromJSON()` on it; the
  step's `run:` script parses `PR_JSON` with `jq` for `baseBranchName` and `headBranchName` instead.
  A push with no release pull request to bump therefore leaves the step cleanly **skipped** by its
  existing `if: ${{ steps.release.outputs.pr }}` gate rather than failing the job, and
  `build-and-attach` runs normally whenever that same push's `release-please` step did create a
  release. *(auto: `.github/scripts/tests/test_bump_version_code.py`'s `WorkflowWiringTests`,
  which pins that the step's `env:` block never contains `fromJSON(` and that its `run:` script
  parses `PR_JSON` with `jq`, standard library only, no Android SDK, no release-please run.)*
- **BUILD-067** `release-please.yml` declares one `workflow_dispatch` input, `backfill_tag`,
  optional and empty by default. When set, it names an existing tag whose GitHub Release exists
  but is missing its signed APK — the state `BUILD-066`'s bug left `v0.2.0` in — and only the
  `backfill-release-apk` job runs, against that tag: it checks out the tag, builds and signs the
  release APK, verifies its signature (`SIGN-052`) and attaches it to that tag's existing Release,
  the same way `build-and-attach` does (`BUILD-050`–`055`). It carries no `needs:` on
  `release-please`, so it never depends on this same run having just created a release, and it runs
  only when `github.event_name == 'workflow_dispatch' && inputs.backfill_tag != ''` — a condition a
  `pull_request`-triggered run can never satisfy, so this does not weaken `docs/spec/signing.md`
  `SIGN-062`. The ordinary `release-please` job's own `if:` excludes this same condition, so a
  backfill dispatch runs only `backfill-release-apk` and never re-runs release-please or
  build-and-attach. *(auto: `.github/scripts/tests/test_bump_version_code.py`'s
  `BackfillDispatchTests` pins the `backfill_tag` input, that the job carries no `needs:`, and that
  it builds, verifies and uploads an APK the same way `build-and-attach` does — standard library
  only, no Android SDK, no release-please run. Actually running the job needs the release keystore
  secrets and a real GitHub Actions run, which this sandbox has neither of: end-to-end use remains
  manual, triggered once by a maintainer with repository Actions access via `workflow_dispatch`
  with `backfill_tag: v0.2.0`, to attach `v0.2.0`'s still-missing APK.)*

## 9. Keeping the verification gate current

[#125](https://github.com/derekwinters/Interval-trainer-android/issues/125): `backfill-release-apk`
checks out only the tag being backfilled (`BUILD-067`), which brings back that tag's entire
historical tree — including whatever copy of `.github/scripts/verify_release_signature.py` existed
when it was cut, never the copy on `main` today. This was proven for real: after issue
[#122](https://github.com/derekwinters/Interval-trainer-android/issues/122)/[#123](https://github.com/derekwinters/Interval-trainer-android/issues/123)'s
apksigner-parsing fix (`docs/spec/signing.md` `SIGN-037`) merged to `main`, a maintainer re-ran all
three pending `backfill_tag` dispatches — `v0.2.0`, `v0.2.1` and `v0.2.2` — and every one failed
identically to before, because each of those tags predates the fix and re-ran its own already-
superseded copy of the script. `build-and-attach` checks out its own release tag the same way
(`BUILD-053`) and has the same structural gap, though it has not caused a real failure yet: every
ordinary release so far has been built from a commit at or after whatever fix was current on `main`
at release time. This section states the fix as an invariant (`docs/spec/signing.md`, "the
release-signature gate that checks a build is always the current one") rather than only patching
the workflow YAML.

- **BUILD-068** Both `build-and-attach` and `backfill-release-apk` check out `main` in a second,
  named `actions/checkout` step, to a `path:` distinct from the job's primary checkout of the
  release tag or commit, and the "Verify the release signature" step in each job invokes
  `.github/scripts/verify_release_signature.py` from that second checkout — never the copy the
  primary checkout's own tree carries. The APK is still assembled and signed from the exact tagged
  commit (`BUILD-053`, `BUILD-067`); only the script that checks it is sourced independently of
  which tag or commit that is, per `docs/spec/signing.md` `SIGN-063`. *(auto:
  `.github/scripts/tests/test_verify_release_signature.py`'s `IndependentVerificationScriptTests`,
  which reads `release-please.yml` directly and pins, for both jobs, that the verification step's
  script path resolves under a checkout step whose `ref:` is the literal `main` and whose `path:`
  differs from the job's own tag/commit checkout — standard library only, no Android SDK, no
  Actions run. Actually proving the fix needs a maintainer re-running
  `backfill_tag: v0.2.0`/`v0.2.1`/`v0.2.2` after this merges and confirming each now attaches a
  signed APK; this sandbox can prove only the workflow's structure.)*

---

## 10. An artifact a device will install

`BUILD-050`–`068` get a signed APK built and attached. Nothing in them asks whether the thing
attached can be *installed*. Issue
[#127](https://github.com/derekwinters/Interval-trainer-android/issues/127) is that gap: a
`v0.2.x` release APK passed the release-signature gate and a device still refused it, with "App
not installed as the package seems invalid" — Android's single message for every parse-or-verify
failure, which names no cause.

**The published artifact turned out to be sound, and this section does not claim to cure that
install failure.** The published `interval-trainer-0.2.2-release.apk` was taken apart twice, by
two readers independently, with nothing but the standard library. `resources.arsc` is `STORED`
and 4-byte aligned. All twelve `lib/**/*.so` entries are `STORED` and land on 16384-byte
boundaries. Every `PT_LOAD` segment in all twelve carries `p_align` 16384, 32- and 64-bit alike.
The manifest is valid, declaring `minSdk` 26 and `targetSdk` 35 — both clear Android 16's floors.
The APK Signing Block is well-formed and its v2 signature verifies against the pinned certificate,
digest recomputed from scratch. The file's SHA-256 matches what the release page advertises. Every
packaging hypothesis in #127 is therefore **disproved** for that artifact, and the reporter's
install failure remains unexplained. That matters because three plausible fixes — forcing
zipalign, forcing 16 KB alignment, replacing the native libraries — would each have changed
something already correct and reported success.

What the investigation did find is a requirement nobody had written down: the properties a device
checks at install are checkable *here*, cheaply, with no Android SDK, and were not being checked
at all. This section writes them down and gates them. The gate is worth having whether or not a
packaging defect ever existed — it is the machinery that cleared the artifact above — and it is
this issue's own second acceptance criterion. It also makes the one remaining difference between
the artifact and a maximally-installable one, its signature-scheme set
([`signing.md`](signing.md) `SIGN-070`–`071`), a stated intention rather than a default.

> **Invariant — this gate reads the artifact, never a tool's report of it.** The zip central
> directory, the APK Signing Block and the ELF program headers are published binary formats; a
> gate that reads them needs no Android SDK, so its tests run anywhere and a maintainer can point
> it at a downloaded release asset on a laptop. That property is not a cost saving. It is what
> made #127's diagnosis possible at all, and adding a check that needs `aapt2`, `apksigner` or
> `zipalign` would spend it.

> **Invariant — a failure message says only what is true of the failure it reports.** A gate that
> overstates a consequence teaches its reader to discount it. A missing signature scheme is not
> an uninstallable package: an APK carrying v2 alone is accepted by every Android from 7.0, which
> is below this app's own `minSdk` floor. That check reports a departure from stated intent, and
> must say so rather than borrowing the installer's language from the checks next to it.

- **BUILD-070** A release APK's `resources.arsc` is stored uncompressed and begins on a 4-byte
  boundary. An app targeting SDK 30 or later whose `resources.arsc` is compressed is refused at
  install with `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`; the table is memory-mapped, so
  an unaligned one cannot be read. *(auto:
  `.github/scripts/tests/test_verify_release_package.py`.)*
- **BUILD-071** A release APK's native libraries are stored uncompressed and each begins on a
  16 KB boundary. `:app`'s `packaging { jniLibs { useLegacyPackaging = false } }` states the
  first, which is also AGP's default at this `targetSdk`; the alignment is AGP's to produce. A
  device with 16 KB memory pages maps these libraries straight out of the APK and cannot do so if
  either fails, so the package is refused. The gate asserts this from the build's stated intent
  rather than from the manifest: reading `extractNativeLibs` would need `aapt2` and therefore the
  Android SDK, which this section's first invariant declines to spend. *(auto: as `BUILD-070`.)*
- **BUILD-072** Every 64-bit native library in a release APK has ELF `PT_LOAD` segments whose
  `p_align` is at least 16384. This is a distinct property from `BUILD-071` and fails differently:
  zip misalignment refuses the install, while ELF misalignment installs cleanly and crashes at the
  first call into the library — the worse of the two, because it ships. 32-bit ABIs are exempt (a
  16 KB-page device runs 64-bit code) and must not be failed for it. These libraries arrive as
  AndroidX prebuilts, so the remedy for a failure here is a dependency version, not a build
  setting; the gate reports it rather than fixing it. *(auto: as `BUILD-070`.)*
- **BUILD-073** `.github/scripts/verify_release_package.py` checks `BUILD-070`–`072` and
  [`signing.md`](signing.md) `SIGN-070` against each built APK, and runs in every job that
  produces one: `release-please.yml`'s `build-and-attach` and `backfill-release-apk` — in both
  cases sourced from the second `main` checkout, exactly as `BUILD-068` requires of the signature
  gate and for the same reason — and `release-candidate.yml`, sourced from the checkout being
  built, since a candidate is a branch head someone chose rather than a frozen tag, and the gate
  on it is under test along with the rest of it. It uses the standard library only and needs no
  Android SDK, which is what lets its tests run in `pr.yml` alongside the signature gate's.
  *(auto: as `BUILD-070`, whose `WorkflowWiring` tests read all three workflow files directly.)*
- **BUILD-074** `release-candidate.yml` declares a `workflow_dispatch` input, `ref`, building a
  signed release APK from any branch, tag or commit and uploading it as the `app-release-candidate`
  artifact. Before it, a signed APK could only be had by merging a release pull request or waiting
  for release-please to open one, so a candidate fix and the only means of trying it sat on
  opposite sides of a release — the position #127 created, made permanent by the reporter having
  no `adb` and no PC. The keystore is safe here on the same grounds as `release-please.yml`'s
  `backfill_tag` dispatch: only someone with write access can start a `workflow_dispatch`, which
  is not the untrusted trigger [`signing.md`](signing.md) `SIGN-062` forbids the keystore to.
  *(manual: a maintainer dispatching it once and receiving an installable APK. GitHub reads
  `workflow_dispatch` only from the default branch's copy of a workflow file — "this event will
  only trigger a workflow run if the workflow file exists on the default branch" — so the first
  dispatch is necessarily **after** this merges, never on the branch that adds it.)*
- **BUILD-075** Each release APK is published with its SHA-256 beside it.
  `verify_release_package.py` writes `<apk>.sha256` next to every APK it reads, in `sha256sum`
  format, whether the verdict passes or fails; `build-and-attach` and `backfill-release-apk`
  upload that file to the same GitHub Release as the APK, and `release-candidate.yml` includes it
  in the `app-release-candidate` artifact. This costs one line and removes the candidate cause
  that is not a build defect at all: a download that arrived truncated or rewritten produces
  exactly #127's symptom, and a digest published beside the file is what lets someone with no PC
  settle it on the device. *(auto: `.github/scripts/tests/test_verify_release_package.py` covers
  both halves — that the gate writes the sidecar, and that all three workflows publish it, read
  from the workflow files directly. That a *published* release actually carries the file is
  observable only after the next tag is cut, and is not claimed here.)*

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Project layout | BUILD-001–004 | *(manual)* |
| The app module, `:core` and `:designsystem` | BUILD-010–019 | `BUILD-015`: `.github/scripts/tests/test_bump_version_code.py`; the rest *(manual)* |
| Tests | BUILD-020–023 | *(manual)* |
| Duration formatting | BUILD-030–033 | `core/src/test/kotlin/com/derekwinters/intervaltrainer/FormatSecondsTest.kt` |
| Continuous integration | BUILD-040–045 | `BUILD-045`: `.github/scripts/tests/test_closing_keyword_workflow.py`; the rest *(manual)* |
| Release build and attach | BUILD-050–058 | *(manual)* |
| Release candidate | BUILD-059–065 | *(manual)* |
| Recovering a missed release APK | BUILD-066–067 | `.github/scripts/tests/test_bump_version_code.py` |
| Keeping the verification gate current | BUILD-068 | `.github/scripts/tests/test_verify_release_signature.py` |
| An artifact a device will install | BUILD-070–075 | `BUILD-070`–`073`, `BUILD-075`: `.github/scripts/tests/test_verify_release_package.py`; `BUILD-074` *(manual)* |

**53 requirements, 14 `auto` and 39 `manual`.**

The proportion is what a build skeleton looks like: almost every requirement here is a fact about
configuration, verified by the build running at all, and the only executable behaviour outside
`:designsystem`'s own token-mapping test is the duration-formatting function `:core`'s unit tests
cover. `:core`'s Android-free build (`BUILD-014`) was one of two requirements the v1 specification
added ahead of the modules they describe; `:core` now exists, so `BUILD-014` describes the build as
it stands. `:designsystem` now exists too, so `BUILD-019` describes it as it stands rather than
ahead of it, and `BUILD-023`'s Robolectric dependency is no longer ahead of it either: the component
gallery ([`docs/spec/design-system.md`](design-system.md) `DS-095`) is what finally needs a
simulated Android runtime, and `DesignSystemConsistencyTest.kt` is real code now, not a promise
([#78](https://github.com/derekwinters/Interval-trainer-android/issues/78)). `:database` now exists
too, so `BUILD-002` and `BUILD-020` describe the build as it stands rather than ahead of it — its
own contract-test harness (`docs/spec/schema.md` `SCHEMA-040`–`044`) and seeded data
(`SCHEMA-030`–`034`) are still ahead of it, tracked on that page rather than this one.
`BUILD-016`–`019` are the same kind again, mostly not ahead of anything else: the Compose compiler,
BOM, navigation artefacts and `:designsystem`'s own module boundary they describe are wired up.
`BUILD-018`'s placeholder `NavHost` no longer describes the build as it stands, either — the home
screen ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79)) is its first real
destination, `:app` depends on `:designsystem` for real per `BUILD-017`, and the requirement's own
text now says so — but a screen's own content and controls remain no more unit-testable than the
placeholder they replaced (`docs/spec/screens.md`'s own traceability section says why), so
`BUILD-018` stays `manual` rather than gaining a test of its own.
`BUILD-015`'s `VERSION_CODE` bump is different: it
runs as a Python script rather than a Gradle or Kotlin fact, so — like the release-signature gate
in [`signing.md`](signing.md) — it is `auto` rather than `manual`, tested with no Android SDK at
all. Release build and attach (`BUILD-050`–`058`) and release candidate (`BUILD-059`–`065`) are
back to the first kind: a workflow's shape and a Gradle wiring decision, checked by the build
succeeding and by reading the diff, not by a unit test asserting YAML. The latter's trigger
(`workflow_run` rather than `pull_request`, `BUILD-059`) is itself a fact `docs/spec/signing.md`
`SIGN-062`'s test checks indirectly, by scanning every workflow file rather than naming this one.
Recovering a missed release APK (`BUILD-066`–`067`) is `auto` like `BUILD-015`, for the same
reason: what changed is a decision about *when* a step's YAML runs and what it parses, pinned by
`WorkflowWiringTests` and `BackfillDispatchTests` reading `release-please.yml`'s text directly, the
same technique `BUILD-015`'s own `WorkflowWiringTests` already used before this issue
([#114](https://github.com/derekwinters/Interval-trainer-android/issues/114)) added to it. Neither
test can exercise GitHub's actual template-compilation timing or run the backfill job for real —
that needs a genuine Actions run and, for the backfill path, the release keystore secrets — so the
fix's real proof is the next ordinary push to `main` succeeding end to end, and the backfill path's
proof is a maintainer running it once against `v0.2.0`.
`BUILD-045` is `auto` for the same reason again: `closing-keyword.yml`'s job-level `if:` is a text
fact `test_closing_keyword_workflow.py` pins by reading the file directly, the same technique
`BUILD-015`'s and `BUILD-066`/`067`'s own `WorkflowWiringTests` use for `release-please.yml`.
Nothing here can exercise the shared `derekwinters/ai-sdlc` action itself — that logic, and its
own test coverage, is out of this repository's reach — so the fix's real proof is release-please's
next release pull request landing without anyone applying `no-closing-keyword` by hand.
Keeping the verification gate current (`BUILD-068`) is the same kind of fact yet again: which
checkout a step's script comes from, pinned by `IndependentVerificationScriptTests` reading
`release-please.yml`'s text directly rather than by a unit test asserting Gradle or Kotlin
behaviour. As with `BUILD-066`–`067`, no test here can run the backfill job for real or exercise
GitHub's actual checkout behaviour, so the fix's real proof is a maintainer re-running
`backfill_tag: v0.2.0`, `v0.2.1` and `v0.2.2` after this merges and confirming each now attaches a
signed APK.
An artifact a device will install (`BUILD-070`–`075`) is the first section here that is mostly
`auto` for the ordinary reason rather than the YAML-reading one: `BUILD-070`–`072` are properties
of a file, so a test constructs a file with each property broken and asserts the gate says so.
`BUILD-073` and `BUILD-075` are half of each kind — the gate's own behaviour is exercised
directly, and where it runs and what it publishes are pinned by reading the three workflow files,
the same technique `BUILD-066`–`068` use. `BUILD-074` stays `manual` because GitHub reads the
`workflow_dispatch` trigger only from the default branch's copy of a workflow, so the dispatch
cannot be exercised until after the change adding it has merged. Two things in this section are
deliberately *not* claimed by any test: that a published release carries the `.sha256` sidecar,
which is observable only once the next tag is cut, and that any of this makes the APK install on
the device in
[#127](https://github.com/derekwinters/Interval-trainer-android/issues/127) — that needs a
physical device, and the published artifact this section examined was already sound on every axis
the gate checks.
