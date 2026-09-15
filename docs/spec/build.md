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
> and the semantics-tree tests those screens need are the feature that justifies Robolectric
> (`BUILD-023`). The invariant still forbids speculation — nothing is added because it might be
> useful, and nothing added stays added once the feature it was for is gone — it no longer forbids
> a user-interface framework, annotation processing, or a test dependency needing a simulated
> Android runtime by name, because v1 now needs at least one of each.

> **Invariant — `VERSION_CODE` only ever goes up, and nothing but the release workflow changes
> it.** Google Play refuses any upload whose version code is not strictly greater than the last
> one accepted, forever — a manual edit that guesses wrong is not recoverable after the fact. The
> bump step (`BUILD-015`) is the only writer, and it always computes the next value from what the
> base branch last released rather than from whatever the release pull request's branch already
> carries, so a workflow re-run that finds it already correct is a no-op, never a second bump.

---

## 1. Project layout

- **BUILD-001** The Gradle wrapper — `gradlew`, `gradlew.bat` and both files under
  `gradle/wrapper/` — is committed, and is the only supported way to run the build.
  *(manual: asserted by the pull-request workflow, which invokes `./gradlew` and nothing else.)*
- **BUILD-002** `settings.gradle.kts` names the root project and includes exactly one module,
  `:app`, today. **This grows to four** as the v1 specification's modules are built: `:core`
  (pure Kotlin, [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md)), `:database`
  (Kotlin Multiplatform, [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md),
  [`docs/spec/schema.md`](schema.md) `SCHEMA-001`), and `:designsystem` (Compose,
  [ADR 0007](../adr/0007-a-designsystem-module-with-bespoke-colour-tokens.md)). None of the three
  exists yet; this requirement describes the module set implementation grows into, not the build as
  it stands. *(manual: a build-configuration fact; the workflow's build is the check.)*
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
  today still sets `minSdk = 24`; this requirement is ahead of that change, the same way this
  specification is ahead of every module it names. *(manual: a build-configuration fact;
  `assembleDebug` in the workflow is the check.)*
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
- **BUILD-014** `:core`, once created, applies the Kotlin JVM plugin, never the Android library
  plugin, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). `android.*` is
  not on `:core`'s compile classpath, so an import of it is a compile error rather than a review
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
  is a no-op instead of a second bump. *(auto:
  `.github/scripts/tests/test_bump_version_code.py`, standard library only, no Android SDK, no
  release-please run.)*

## 3. Tests

- **BUILD-020** `./gradlew test` runs `:app`'s JVM unit tests, and a failing test fails the build.
  *(manual: a test cannot assert the behaviour of the runner that is running it; the workflow is
  the check.)*
- **BUILD-021** The unit tests run on the JVM alone — no emulator, no connected device, no
  simulated Android runtime — so they need nothing but a JDK and the dependencies on the test
  classpath. *(manual: absence of such a dependency; adding one would show in the diff.)*
- **BUILD-022** At least one unit test exercises production Kotlin code in `:app`, so that a wrong
  or missing implementation makes it red. A test that only asserts a constant satisfies nothing.
  *(manual: satisfied by the tests for BUILD-030–033 below, which call production code.)*
- **BUILD-023** Robolectric arrives once `:designsystem`'s Compose screens exist, scoped to their
  semantics-tree tests alone ([`docs/spec/design-system.md`](design-system.md) `DS-091`, `DS-093`) —
  it is not adopted for `:core` or for `:database`, both of which are tested with no simulated
  Android runtime at all ([ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md),
  [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md)). Its `android-all` jar is
  pre-fetched and cached in continuous integration with `robolectric.offline` set, never vendored
  and never fetched live inside `./gradlew test` (`DS-092`), which is what keeps `BUILD-021`'s
  clean-checkout invariant satisfied for `:app`'s own tests while `:designsystem`'s tests use a
  simulated runtime deliberately. *(manual: a dependency-scope and continuous-integration fact.)*

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

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Project layout | BUILD-001–004 | *(manual)* |
| The app module and `:core` | BUILD-010–015 | `BUILD-015`: `.github/scripts/tests/test_bump_version_code.py`; the rest *(manual)* |
| Tests | BUILD-020–023 | *(manual)* |
| Duration formatting | BUILD-030–033 | `app/src/test/java/com/derekwinters/intervaltrainer/FormatSecondsTest.kt` |
| Continuous integration | BUILD-040–044 | *(manual)* |
| Release build and attach | BUILD-050–058 | *(manual)* |
| Release candidate | BUILD-059–065 | *(manual)* |

**39 requirements, 5 `auto` and 34 `manual`.**

The proportion is what a build skeleton looks like: almost every requirement here is a fact about
configuration, verified by the build running at all, and the only executable behaviour in the
module is the function the unit tests cover. The two added by the v1 specification —
`:core`'s Android-free build (`BUILD-014`) and Robolectric's scoped arrival (`BUILD-023`) — are
configuration facts of exactly the same kind, ahead of the modules they describe, as `BUILD-002`
and `BUILD-010`'s `minSdk` change already are. `BUILD-015`'s `VERSION_CODE` bump is different: it
runs as a Python script rather than a Gradle or Kotlin fact, so — like the release-signature gate
in [`signing.md`](signing.md) — it is `auto` rather than `manual`, tested with no Android SDK at
all. Release build and attach (`BUILD-050`–`058`) and release candidate (`BUILD-059`–`065`) are
back to the first kind: a workflow's shape and a Gradle wiring decision, checked by the build
succeeding and by reading the diff, not by a unit test asserting YAML. The latter's trigger
(`workflow_run` rather than `pull_request`, `BUILD-059`) is itself a fact `docs/spec/signing.md`
`SIGN-062`'s test checks indirectly, by scanning every workflow file rather than naming this one.
