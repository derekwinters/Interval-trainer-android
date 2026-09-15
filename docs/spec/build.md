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
- **BUILD-013** Version code and version name are **not** set here. Versioning is a separate
  concern and is specified when it is built. *(manual: absence of configuration.)*
- **BUILD-014** `:core`, once created, applies the Kotlin JVM plugin, never the Android library
  plugin, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). `android.*` is
  not on `:core`'s compile classpath, so an import of it is a compile error rather than a review
  comment — the invariant that module choice exists to enforce. *(manual: a build-configuration
  fact; a violation fails compilation.)*

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

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Project layout | BUILD-001–004 | *(manual)* |
| The app module and `:core` | BUILD-010–014 | *(manual)* |
| Tests | BUILD-020–023 | *(manual)* |
| Duration formatting | BUILD-030–033 | `app/src/test/java/com/derekwinters/intervaltrainer/FormatSecondsTest.kt` |
| Continuous integration | BUILD-040–044 | *(manual)* |

**19 requirements, 4 `auto` and 15 `manual`.**

The proportion is what a build skeleton looks like: almost every requirement here is a fact about
configuration, verified by the build running at all, and the only executable behaviour in the
module is the function the unit tests cover. The two added by the v1 specification —
`:core`'s Android-free build (`BUILD-014`) and Robolectric's scoped arrival (`BUILD-023`) — are
configuration facts of exactly the same kind, ahead of the modules they describe, as `BUILD-002`
and `BUILD-010`'s `minSdk` change already are.
