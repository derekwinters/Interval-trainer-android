# Research — Enforcing user-interface consistency on the JVM

**Question.** The interface must be consistent — every button of a given action the same style, colour
and size; up and down arrows identical everywhere; counters and dialogs in the same place on every
screen — and that consistency must be **mechanically testable**, not merely documented. What actually
enforces it on Jetpack Compose with Material 3, on a JVM-only continuous-integration setup with no
emulator?

**Date checked: 2026-09-11.** Every version number and release date below was read on that date and
will drift.

**Sources.** Primary only: `developer.android.com`, the AndroidX source on
`github.com/androidx/androidx` (branch `androidx-main`), each tool's own repository
(`cashapp/paparazzi`, `takahirom/roborazzi`, `detekt/detekt`, `slackhq/compose-lints`,
`robolectric/robolectric`, `googlesamples/android-custom-lint-rules`, `android/nowinandroid`), and
Maven Central's own artifact listings for publication dates. Four documentation sites are unreachable
from this environment — `robolectric.org`, `detekt.dev`, `takahirom.github.io` and `docs.github.com` —
so every claim about Robolectric, detekt and Roborazzi's hosted docs is cited to source code or to a
README in the project's own repository instead. No blogs, no Stack Overflow.

This is a research note, not a decision. Where it names a trade-off it states the options; it does not
pick one, except in the clearly-labelled *Recommendation* section, which is a recommendation and not a
plan.

---

## Answer

**There is no first-party mechanism that checks two screens agree with each other.** Nothing in
Compose, AndroidX, Android Lint or the Android Gradle plugin asserts "the destructive button here is
the destructive button there". Everything on offer falls into one of three groups, and only the first
group actually produces agreement:

1. **Structural** — make the inconsistent thing *unwritable*. One `DestructiveButton` composable, one
   `StepperArrow`, one spacing scale, defined once; screens call those and never call `Button` with
   ad-hoc colours. Compose's own guidance for this is `MaterialTheme`'s `colorScheme`/`typography`/
   `shapes` for anything Material already has a slot for, plus a `staticCompositionLocalOf` token set
   for anything it does not
   ([custom design systems](https://developer.android.com/develop/ui/compose/designsystems/custom)).
   This costs nothing, needs no tooling, and is the only mechanism here that makes divergence
   impossible rather than detectable. Gradle's `implementation` configuration can then keep
   `material3` off feature modules' compile classpath entirely, so the raw `Button` is not reachable
   from a screen ([dependency configurations](https://developer.android.com/build/dependencies)).
2. **Regression detection** — screenshot goldens. Three JVM options exist and all three work without
   an emulator: **Paparazzi**, **Roborazzi**, and Google's own **Compose Preview Screenshot Testing**
   plugin. They prove a screen has not *changed*. They do not prove two screens *agree*, except
   insofar as you put the components side by side in one gallery image, which is the trick that makes
   them useful for this question.
3. **Prohibition** — lint or detekt rules banning raw `Color(…)` and literal `.dp` outside the token
   file. Real and achievable, but **no such rule ships anywhere**: not in AndroidX's Compose lint
   checks, not in Slack's `compose-lints`. You either write a custom Android Lint detector against an
   API Google explicitly says is not final, or you configure detekt's `ForbiddenMethodCall` /
   `ForbiddenImport` / `MagicNumber`, which needs type resolution on an Android module. This is the
   most-improvised of the three groups.

**Three findings decide the shape of the work.**

- **`./gradlew test` verifies nothing for any of the three screenshot tools.** Paparazzi and Roborazzi
  both render during the ordinary unit-test task but only *compare* when their own verify task drives
  it; Google's plugin does not use the unit-test task at all. `BUILD-041` currently runs `./gradlew
  test` and `./gradlew assembleDebug`, and would need a third command.
- **Goldens are not portable between machines.** Google's own sample app states it plainly: its
  screenshots "are recorded on CI using Linux. Other platforms may (and probably will) generate
  slightly different images, making the screenshot tests fail"
  ([nowinandroid README](https://github.com/android/nowinandroid/blob/main/README.md)). Roborazzi's
  FAQ says "there are no guarantees for identical rendering across all environments"
  ([README](https://github.com/takahirom/roborazzi/blob/main/README.md)). Paparazzi resolves a
  *different native binary per operating system and architecture* — `mac`, `mac-arm`, `win`, `linux`
  ([`PaparazziPlugin.kt`](https://github.com/cashapp/paparazzi/blob/master/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt)).
  Any golden workflow adopted here must record on CI, never on the laptop, or it will be abandoned
  exactly the way the question anticipates.
- **The build spec's third invariant forbids all of this, and would have to be amended** — see
  §6. The narrowest amendment lets in Google's screenshot plugin only, because it keeps its
  dependencies in a separate `screenshotTest` source set and off the unit-test classpath.

Accessibility is the one place where a *genuine cross-screen invariant* can be asserted on the JVM
without images: under Robolectric the Compose semantics tree is walkable, and `assertTouchHeightIsEqualTo`,
`assertHeightIsAtLeast` and the content-description assertions exist in `androidx.compose.ui.test`
([`BoundsAssertions.kt`](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)).
"Every clickable node on every screen has a touch target of at least 48dp and a non-empty label" is a
sentence about *all* screens, it is checkable in a loop, and it never needs a human to look at a PNG.
The same file provides `assertPositionInRootIsEqualTo` and `getBoundsInRoot()`, so "the counter sits
in the same place on every screen" is assertable the same way — without an image.

---

## 1. Design tokens in Compose

### What `MaterialTheme` already is

`MaterialTheme` takes exactly three subsystems — `colorScheme`, `typography` and `shapes` — and
components read from them by default while still exposing per-call overrides
([Material 3 theming](https://developer.android.com/develop/ui/compose/designsystems/material3)):

```kotlin
MaterialTheme(colorScheme = /* ... */, typography = /* ... */, shapes = /* ... */) { /* content */ }
```

The colour system is **role-based, not palette-based**: `primary`, `secondary`, `tertiary`, their
containers, the `on-` pairs and the surface family. Google's framing is that "all components have
default colors applied to them but expose flexible APIs to customize their colors if required"
(same page). The type scale is fifteen named styles (`displayLarge` … `labelSmall`), the shape scale
five (`extraSmall` … `extraLarge`).

The consistency consequence matters and is easy to miss: **a project that never passes a `colors =`
or `shape =` argument at a call site gets button-to-button agreement for free**, because every
`Button` resolves the same roles from the same `colorScheme`. Inconsistency in a Compose app is
almost always introduced by per-call-site overrides, not by the theme.

### Adding your own semantic tokens

Google documents three levels
([custom design systems](https://developer.android.com/develop/ui/compose/designsystems/custom)):

1. **Extension properties on the Material types** — cheapest, for one-off additions:
   ```kotlin
   val ColorScheme.snackbarAction: Color
       @Composable get() = if (isSystemInDarkTheme()) Red300 else Red700
   ```
2. **A `CompositionLocal` token set alongside `MaterialTheme`** — the idiom the question asks about:
   ```kotlin
   @Immutable
   data class ExtendedColors(val caution: Color, val onCaution: Color)

   val LocalExtendedColors = staticCompositionLocalOf {
       ExtendedColors(caution = Color.Unspecified, onCaution = Color.Unspecified)
   }
   ```
3. **A fully custom design system** replacing `MaterialTheme` outright, with its own `CustomColors`,
   `CustomElevation` and so on.

**So: is a custom `CompositionLocal` token set the idiomatic way to say "the destructive button is
always this"?** Partly, and the distinction is worth stating precisely.

- For a **value** Material has no role for — a "caution" colour, a spacing scale, a counter's
  monospace digit style — yes: `@Immutable data class` + `staticCompositionLocalOf` + a `@Composable`
  accessor object is exactly what Google documents, and `staticCompositionLocalOf` is the right
  variant because theme values change rarely (the page uses it in every example).
- For **"the destructive button"** — no, or not on its own. A token set makes the *colour* available
  everywhere; it does not stop a screen from putting that colour on a button of a different size,
  shape or content padding. What makes the destructive button identical everywhere is that there is
  **one composable named `DestructiveButton`** and no screen constructs the raw one. The token set is
  the vocabulary; the wrapper composable is the enforcement.

Google's own caveat pushes the same way: "Not all values may be exposed as parameters in Material
composables, in particular with `CompositionLocal` composables (such as `LocalTextStyle`). In such
cases you may need to wrap `content` lambdas in provider functions (like `ProvideTextStyle`)" (same
page). Wrapping is unavoidable; you may as well wrap once, deliberately, per semantic component.

### Making the raw component unreachable

`implementation` dependencies are not exposed to consuming modules: "the dependency isn't made
available to other modules that depend on the current module"
([dependency configurations](https://developer.android.com/build/dependencies)). A `:designsystem`
module that depends on `androidx.compose.material3` with `implementation` and exposes only
`DestructiveButton`, `PrimaryButton`, `StepperArrow` and `AppTheme` gives feature modules **no
compile-time access to `androidx.compose.material3.Button` at all**. That is a compiler error rather
than a lint warning, needs no extra tooling, and is the strongest enforcement mechanism found in this
whole investigation.

Note that `:app` in this repository is meant to stay "a thin shell" with logic in a pure-JVM `:core`;
a third module for the design system fits that shape, but it is a structural decision for the owner,
not a finding.

---

## 2. Screenshot testing on the JVM

### Side by side

| | **Paparazzi** | **Roborazzi** | **Compose Preview Screenshot Testing** |
|---|---|---|---|
| Owner | Cash App (Block) | takahirom (individual) | Google / Android Gradle plugin team |
| Android runtime used | layoutlib | Robolectric + Robolectric Native Graphics | layoutlib |
| Latest stable | **1.3.5**, published 2024-11-07 | **1.74.0**, published 2026-09-08 | none — `0.0.1-alpha15` |
| Latest anything | `2.0.0-alpha05`, 2026-05-20 | 1.74.0 | `0.0.1-alpha15` |
| Licence | Apache 2.0 | Apache 2.0 | ships with AGP (Google) |
| Emulator needed | no | no | no |
| Verifies under plain `./gradlew test` | **no** (renders only) | **no** (captures nothing) | **no** (different task entirely) |
| Downloads at test time | no — pinned Gradle deps | **yes** — Robolectric fetches `android-all` | no — pinned Gradle deps |
| Goldens live in | `src/test/snapshots` (configurable) | `build/outputs/roborazzi` by default | `src/screenshotTest<Variant>/reference/` |

Publication dates are from Maven Central's directory listings
([paparazzi](https://repo1.maven.org/maven2/app/cash/paparazzi/paparazzi/),
[roborazzi](https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/)); licences from
each repository's `LICENSE` file.

### Paparazzi

Renders "without a physical device or emulator" using layoutlib
([README](https://github.com/cashapp/paparazzi/blob/master/README.md)). Three tasks: `testDebug`
("Runs tests and generates an HTML report"), `recordPaparazziDebug` ("Saves snapshots as golden values
to a predefined source-controlled location") and `verifyPaparazziDebug` ("Runs tests and verifies
against previously-recorded golden values"). Snapshots default to `src/test/snapshots`.

Reading the plugin source settles the questions the README does not
([`PaparazziPlugin.kt`](https://github.com/cashapp/paparazzi/blob/master/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt)):

- The snapshot tests **are** ordinary JUnit tests on the standard `Test` task —
  `recordTaskProvider.configure { it.dependsOn(testTaskProvider) }` and the same for verify. The mode
  is chosen by system properties set in `doFirst`: `paparazzi.test.record`, `paparazzi.test.verify`.
  Run `./gradlew test` on its own and both are false: the tests render and write an HTML report, and
  **nothing is compared**. Verification requires `verifyPaparazziDebug`.
- Rendering artefacts are `com.android.tools.layoutlib:layoutlib-runtime:$NATIVE_LIB_VERSION:$nativeLibraryArtifactId`
  and `com.android.tools.layoutlib:layoutlib-resources:$NATIVE_LIB_VERSION`, resolved as normal Gradle
  dependencies at build time, extracted, and handed to the test JVM as
  `paparazzi.layoutlib.runtime.root` / `paparazzi.layoutlib.resources.root`. **Nothing is fetched at
  test time**, which satisfies the clean-checkout invariant in the same way any pinned dependency
  does — but the classifier is per-platform (`mac`, `mac-arm`, `win`, `linux`), which is the
  mechanical reason a Linux golden and a macOS golden differ.
- The render SDK is `android.testOptions.targetSdk`, else `compileSdk`, else a hard-coded default of
  36 — so the rendering level is pinned by the build file, not by whatever the machine has.

**Maintenance status is the concern.** 1.3.5 is the newest stable and was published 2024-11-07 — ten
months before the date checked. The 2.0 line is alive (`2.0.0-alpha05`, 2026-05-20, bundling Gradle
9.3.1 and LayoutLib 16.2.1, and requiring Java 21+ to build for consumers per the 2.0.0-alpha04 notes
on the [releases page](https://github.com/cashapp/paparazzi/releases)) but has been in alpha since
April 2023. Adopting Paparazzi today means adopting an alpha or a stable release that predates several
AGP releases.

The README recommends **Git LFS** for snapshots and shows a CI step that fails fast if PNGs were
committed outside LFS, then `git lfs pull`. It documents two known problems (Lottie; `LocalInspectionMode`
not being set globally, which affects composables like `GoogleMap()`), and says nothing at all about
fonts or cross-machine rendering — the silence is itself worth knowing.

### Roborazzi

Roborazzi captures screenshots from Robolectric tests, which are ordinary JVM unit tests. Setup, from
the [README](https://github.com/takahirom/roborazzi/blob/main/README.md): plugin
`io.github.takahirom.roborazzi`, dependencies `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`
(and `roborazzi-accessibility-check` for §4), every test class annotated
`@GraphicsMode(GraphicsMode.Mode.NATIVE)`, and for shadow/shape fidelity
`it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"`. Tasks are
`recordRoborazziDebug`, `compareRoborazziDebug`, `verifyRoborazziDebug`, `verifyAndRecordRoborazziDebug`;
equivalently `./gradlew testDebugUnitTest -Proborazzi.test.record=true`. Default output is
`build/outputs/roborazzi`, reports at `build/reports/roborazzi/index.html`. Without one of those
properties the rule captures nothing, so plain `./gradlew test` passes trivially.

Two things make it the most interesting of the three for this question:

- **It can generate the screenshot tests from your `@Preview` functions.** `roborazzi {
  generateComposePreviewRobolectricTests { enable = true; packages = listOf("com.example") } }` scans
  previews (via ComposablePreviewScanner) and generates Robolectric tests for each. The catalogue of
  previews you keep for the IDE becomes the catalogue that is screenshot-tested, with no second list
  to maintain.
- **It ships an accessibility-check module** built on Google's Accessibility Test Framework — see §4.

**It downloads at test time.** Not Roborazzi itself, but Robolectric, which it requires. Robolectric's
resolver documents the order explicitly
([`LegacyDependencyResolver.java`](https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/LegacyDependencyResolver.java)):
`robolectric-deps.properties`, then `robolectric.dependency.dir`, then `robolectric.offline`, then a
classpath properties file, "Otherwise the jars will be downloaded from Maven Central and cached
locally." And: "If you require a hermetic build, we recommend either specifying the
`robolectric.dependency.dir` system property, or providing your own `SdkProvider`." An `android-all`
jar is roughly 40 MB per API level. **A clean checkout with no network cannot run Roborazzi tests
unless the jars are vendored or pre-fetched**, which is a direct collision with this repository's
first build invariant as it is currently written.

Roborazzi is by far the most actively maintained of the three: 1.74.0 was published three days before
the date checked, and releases run at roughly one every week or two through 2026.

### Compose Preview Screenshot Testing (Google)

Google's own tool "combines the simplicity and features of composable previews with the productivity
gains of running host-side screenshot tests"
([docs](https://developer.android.com/studio/preview/compose-screenshot-testing)).

- Plugin `com.android.compose.screenshot`, version **0.0.1-alpha15**, plus
  `android.experimental.enableScreenshotTest=true`.
- Tests live in a **separate `screenshotTest` source set** (`app/src/screenshotTest/kotlin/`), not in
  `src/test`. Since alpha10 the previews to be tested are marked `@PreviewTest`
  ([release notes](https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes)).
- Tasks are `./gradlew updateDebugScreenshotTest` (write references) and
  `./gradlew validateDebugScreenshotTest` (compare, and emit
  `build/reports/screenshotTest/preview/{variant}/index.html`). **It does not run under `./gradlew
  test`.**
- Reference images go to `src/screenshotTest<Variant>/reference/`, named
  `{fully-qualified-function-name}_{hash}_{hash}_{n}.png` — so **renaming a composable orphans its
  golden** and forces a re-record, an explicitly documented limitation.
- Tolerance is configurable: `testOptions { screenshotTests { imageDifferenceThreshold = 0.0001f } }`
  (available since alpha06).
- Rendering is layoutlib in an isolated class loader; memory-hungry, hence
  `android.compose.screenshot.maxHeapSize`.
- Requirements per the docs page: AGP 9.0+ for the full experience (8.5.0+ for the Gradle tasks
  alone), Kotlin 2.2.10+ (1.9.20+ for tasks alone), JDK 17+.

**Status: not stable.** The docs say it "is still in development and its features and APIs are subject
to change substantially during the alpha phase", and the version string has been `0.0.1-alphaN` for
its whole life. It also does not support non-Android targets in a Kotlin Multiplatform project.

### Fonts and cross-machine rendering — the classic failure mode

All three render with a bundled Android framework, so they do **not** use the host's system fonts, and
that removes the largest single source of drift. What remains is the native graphics stack, and it is
not identical across operating systems. The three primary statements:

- Google's sample app: "The known correct screenshots stored in this repository are recorded on CI
  using Linux. Other platforms may (and probably will) generate slightly different images, making the
  screenshot tests fail." Its documented workaround is to run `recordRoborazziDemoDebug` on `main`
  before starting work, so that `verify` afterwards shows only your own changes
  ([nowinandroid README](https://github.com/android/nowinandroid/blob/main/README.md)).
- Roborazzi's FAQ: failures across macOS, Ubuntu and Windows are "a known issue caused by variations
  in how graphics libraries render components on different platforms… there are no guarantees for
  identical rendering across all environments", and the recommendation is to "configure your
  continuous integration (CI) environment to both record and test screenshots"
  ([README](https://github.com/takahirom/roborazzi/blob/main/README.md)).
- Paparazzi resolves a different native artifact per OS and architecture (plugin source, above).

**The workable arrangement is the same in all three cases: goldens are a CI artefact.** They are
recorded by a CI job on the one operating system CI uses, committed from there, and never recorded on
a developer machine. A per-pixel tolerance (`imageDifferenceThreshold`, Roborazzi's comparison
options, Paparazzi's `app.cash.paparazzi.*` properties including `overwriteOnMaxPercentDifference`)
softens the edge but does not remove the rule.

---

## 3. Catching inconsistency rather than regression

This is the part of the question with the least satisfying answer, and it deserves a blunt one.

### What is real and first-party

**AndroidX ships Compose lint checks, and none of them are about design tokens.** The documented
framing is only that "Compose ships with a number of lint checks by default. This helps verify the
correctness of your Compose code"
([Compose lint](https://developer.android.com/develop/ui/compose/tooling/lint)) — the page does not
even list them. The actual detector set in `compose-ui` is
[`ComposedModifierDetector`, `ConfigurationScreenWidthHeightDetector`,
`LocalContextResourcesConfigurationReadDetector`, `ModifierDeclarationDetector`,
`ModifierNodeInspectablePropertiesDetector`, `ModifierParameterDetector`,
`MultipleAwaitPointerEventScopesDetector`, `NonObservableLocaleDetector`,
`ReturnFromAwaitPointerEventScopeDetector`, `SuspiciousCompositionLocalModifierReadDetector`,
`SuspiciousModifierThenDetector`](https://github.com/androidx/androidx/tree/androidx-main/compose/ui/ui-lint/src/main/java/androidx/compose/ui/lint).
Every one is about correctness or performance. **None bans a hard-coded colour or dimension.**

**Android Lint itself runs on the JVM with no device** — "a static code scanning tool that analyzes
source files without needing to execute the app", `./gradlew lint`, configured by the `lint {}` block
with `abortOnError`, `ignoreWarnings`, `checkOnly`, and a `baseline` file
([Improve your code with lint checks](https://developer.android.com/studio/write/lint)). So lint is
the right *place* for a token rule; it just does not contain one.

### What is real but third-party

**Slack's `compose-lints`** is a maintained public rule set, and its
[rule list](https://github.com/slackhq/compose-lints/blob/main/docs/rules.md) is worth knowing:
`ComposeModifierMissing`, `ComposeModifierReused`, `ComposeModifierWithoutDefault`,
`ComposeParameterOrder`, `ComposeCompositionLocalUsage` (flags implicit `CompositionLocal` use, with
an allow-list — directly relevant if you adopt a token `CompositionLocal`), `ComposeM2Api` (opt-in;
flags Material 2 APIs in an M3 project, with an `allowed-m2-apis` option), `ComposePreviewNaming`,
`ComposePreviewPublic`, and about fifteen more. **Again: no rule about hard-coded colours, dp values
or padding literals.** The nearest thing to a design-system rule in the whole catalogue is
`ComposeM2Api`.

**detekt** can express the prohibition, with caveats. From the rule sources:

- `ForbiddenMethodCall` — "Reports all method or constructor invocations that are forbidden. This rule
  allows to set a list of forbidden `methods` or constructors." Signatures are fully qualified;
  constructors are written with `<init>`
  ([source](https://github.com/detekt/detekt/blob/main/detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ForbiddenMethodCall.kt)).
  So `androidx.compose.ui.graphics.Color` can be listed. **But the rule is annotated
  `@RequiresTypeResolution` in 1.23.8 and `RequiresAnalysisApi` on `main`** — it needs a full
  type-resolved analysis of an Android module, which is the part of detekt that is awkward to set up
  and slow to run.
- `ForbiddenImport` — "Reports all imports that are forbidden", configured with `forbiddenImports`
  (glob patterns over the fully qualified name) and `allowedImports` as exceptions, each with a
  `reason`
  ([source](https://github.com/detekt/detekt/blob/main/detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/ForbiddenImport.kt)).
  This does **not** need type resolution, and it is the cheap way to say "no feature package imports
  `androidx.compose.material3.*`" if you have not separated the design system into its own module.
- `MagicNumber` — "detects and reports usages of magic numbers in the code. Prefer defining constants
  with clear names"
  ([source](https://github.com/detekt/detekt/blob/main/detekt-rules-style/src/main/kotlin/dev/detekt/rules/style/MagicNumber.kt)).
  `16.dp` contains a numeric literal, so this fires on ad-hoc spacing — and on a great deal else,
  which is why it needs an exclusion list to be tolerable.

detekt's own release position: **1.23.8** is the newest stable (Maven Central, last updated
2025-02-21) and the 2.0 line is at `2.0.0-alpha.6` under the new `dev.detekt` coordinates (last
updated 2026-08-04). detekt runs on the JVM and needs no Android SDK or device.

**Custom Android Lint rules** are the other route. Google publishes a sample repository and API guide
at [googlesamples/android-custom-lint-rules](https://github.com/googlesamples/android-custom-lint-rules),
which carries its own warning: "the lint API is not a final API; if you rely on this be prepared to
adjust your code for the next tools release", and the lint library version must track AGP (if AGP is
*X.Y.Z*, lint is *X+23.Y.Z*). Custom checks are wired in with the `lintChecks` configuration for
local-only checks and `lintPublish` for checks shipped inside a published AAR, from a separate module
that produces a single JAR with `compileOnly` dependencies
([AGP 3.4 release notes](https://developer.android.com/build/releases/past-releases/agp-3-4-0-release-notes)).
So: achievable, documented, officially sampled — and an unstable API that you re-verify on every AGP
bump, for a one-person project.

### The component gallery

**Is a catalogue screen that is itself screenshot-tested a recognised practice?** Qualified yes.

- AndroidX maintains a catalogue app for Material 3 in its own tree
  ([`compose/material3/material3/integration-tests/material3-catalog`](https://github.com/androidx/androidx/tree/androidx-main/compose/material3/material3/integration-tests/material3-catalog)),
  so "one screen showing every component" is a first-party habit.
- Google's sample app screenshot-tests UI components as well as screens, and stores the goldens in
  the repository ([nowinandroid README](https://github.com/android/nowinandroid/blob/main/README.md)).
- Both Google's screenshot plugin (`@PreviewTest` on preview functions) and Roborazzi
  (`generateComposePreviewRobolectricTests`) are built on the assumption that **the set of `@Preview`
  functions is the catalogue**, and generate one screenshot test per preview. That is as close to an
  endorsement of the practice as tooling can give.

What is *not* documented anywhere as a practice is the specific trick that answers the question:
putting all four button variants, both arrows and the counter **in one composable, captured as one
image**, so that a divergence between two of them is visible in a single diff rather than spread
across two files that each "passed". That is an improvisation — a good one, and cheap, but nobody's
documentation tells you to do it, and it should be described as a local convention rather than a
standard.

### Honest summary of §3

| Mechanism | Real and documented? |
|---|---|
| M3 theme roles + one wrapper composable per action | Documented by Google; the only true *agreement* mechanism |
| Module boundary via `implementation` so raw M3 is unreachable | Documented Gradle/AGP behaviour, used here for a purpose nobody documents |
| Lint rule banning raw `Color(…)` / literal `.dp` | **Does not exist.** Must be written, against an unstable API |
| detekt `ForbiddenImport` / `ForbiddenMethodCall` / `MagicNumber` | Real rules, real config; using them for design tokens is improvisation |
| Compose lint checks from AndroidX / Slack | Real, maintained, and about correctness — not tokens |
| Screenshot-tested component gallery | Practice is real; "one image containing everything" is improvised |

---

## 4. Accessibility as a testable property

### The semantics tree on the JVM

Google's position on Robolectric: it "lets you run tests in a simulated Android environment inside a
JVM, without the overhead and flakiness of an emulator", and "Any UI test present in the `test` source
set runs with Robolectric" — with a Compose test given as the worked example
([Robolectric](https://developer.android.com/training/testing/local-tests/robolectric)). Setup is
`testOptions { unitTests { isIncludeAndroidResources = true } }` plus the `org.robolectric:robolectric`
test dependency. The caveat Google states in the same place: "Robolectric is not a complete
replacement for an emulator because it doesn't support all the features and APIs."

What can then be asserted, from `androidx.compose.ui.test`:

- **Touch target size.** `BoundsAssertions.kt` provides assertions on the *layout* bounds
  (`assertWidthIsEqualTo`, `assertHeightIsEqualTo`, with tolerance overloads), on the **touch bounds**
  (`assertTouchWidthIsEqualTo`, `assertTouchHeightIsEqualTo` — "Asserts that the touch bounds of this
  node has height equal to [expectedHeight]"), and the minimums `assertWidthIsAtLeast` /
  `assertHeightIsAtLeast`
  ([source](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)).
  Note an asymmetry that shapes how a test must be written: the `AtLeast` forms exist **only for
  layout bounds**. There is no `assertTouchHeightIsAtLeast`, so a minimum-touch-target assertion is
  either an exact `assertTouchHeightIsEqualTo(48.dp)` or a comparison written by hand against
  `getUnclippedBoundsInRoot()`.
  The distinction between layout bounds and touch bounds is the important one: Material 3 expands the
  touch area without expanding the drawn component, via `Modifier.minimumInteractiveComponentSize()`,
  which "Reserves at least 48.dp in size to disambiguate touch interactions if the element would
  measure smaller" and "uses the Material recommended minimum size of 48.dp x 48.dp"
  ([`InteractiveComponentSize.kt`](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/InteractiveComponentSize.kt)).
  An arrow button drawn at 24dp is therefore *correct* if its touch height is 48dp, and a test that
  asserted layout height would be asserting the wrong thing.
- **Screen position — which the question asks about directly.** The same file provides
  `assertPositionInRootIsEqualTo(expectedLeft, expectedTop)`, `assertTopPositionInRootIsEqualTo` and
  `assertLeftPositionInRootIsEqualTo`, plus `getUnclippedBoundsInRoot()` and `getBoundsInRoot()`
  returning a `DpRect`
  ([source](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/BoundsAssertions.kt)).
  **"The counter sits in the same place on every screen" is therefore directly assertable on the JVM
  without a single image**: fetch the counter's bounds on each screen and assert they agree. This is
  the clearest example in the whole note of a consistency property that screenshots would only detect
  by accident and that the semantics tree checks exactly.
- **Content descriptions and tree traversal.** Matchers compose over the tree: `hasParent`,
  `hasAnyAncestor`, `hasAnyDescendant`, `hasAnySibling`, `hasText`, `hasClickAction`; collection
  assertions `assertAll(matcher)`, `assertAny(matcher)`, `assertCountEquals(n)`; and `printToLog()`
  dumps the semantics tree
  ([Testing APIs](https://developer.android.com/develop/ui/compose/testing/apis)).
  `onAllNodes(hasClickAction()).assertAll(…)` is the shape of a genuine cross-screen invariant: it
  quantifies over every clickable node rather than naming one.

### First-party accessibility checks

Compose has them, as of **Compose 1.8.0**, in the artifact
**`androidx.compose.ui:ui-test-junit4-accessibility`**
([Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing)).
They are built on Google's Accessibility Test Framework and cover exactly the properties the question
names:

- accessibility labels (items with no label readable by accessibility services),
- **colour contrast** (low contrast between text and background),
- **touch target size**,
- traversal order.

Usage:

```kotlin
composeTestRule.enableAccessibilityChecks()
composeTestRule.onRoot().tryPerformAccessibilityChecks()
```

and severity is tunable with an explicit validator:

```kotlin
val accessibilityValidator = AccessibilityValidator()
    .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.WARNING)
composeTestRule.enableAccessibilityChecks(accessibilityValidator)
```

Once enabled, ordinary actions run the checks too — "any action (such as `performClick`) will perform
accessibility checks".

**The gap:** the page uses `createAndroidComposeRule<ComponentActivity>()` and **says nothing about
whether these checks run under Robolectric**. The Accessibility Test Framework is an Android library,
so on the JVM it needs *some* Android runtime; the natural assumption is that it works under
Robolectric exactly as other Compose tests do, but that assumption is not backed by a primary source
and should be verified by experiment before anything is built on it.

The one primary source that *does* claim JVM operation is Roborazzi's, whose
`roborazzi-accessibility-check` module "uses Accessibility Test Framework to check accessibility",
exposes `RoborazziATFAccessibilityChecker`, `checkRoboAccessibility()` and an
`AccessibilityCheckAfterTestStrategy()` that validates after every test, and reports results such as
`TextContrastCheck` and `SpeakableTextPresentCheck`
([README](https://github.com/takahirom/roborazzi/blob/main/roborazzi-accessibility-check/README.md)).
Since Roborazzi's whole premise is Robolectric on the JVM, ATF-on-the-JVM is demonstrated there even
though the module's own README does not spell out the device-free claim.

**Contrast ratio specifically** is therefore checkable on the JVM *through ATF's `TextContrastCheck`*,
against rendered pixels — not as a static calculation over the token file. A static check ("the
`onPrimary` token contrasts at least 4.5:1 with the `primary` token") is a handful of lines of pure
Kotlin against the WCAG formula and needs no framework at all; that is an ordinary `:core`-style unit
test and is the cheapest accessibility assertion available here.

---

## 5. The review loop

None of the three tools has a "approve this change" button. The loop in all of them is the same three
steps, and the only question is who performs step 1.

1. **Re-record**: `recordPaparazziDebug` / `recordRoborazziDebug` / `updateDebugScreenshotTest`.
2. **Commit the changed PNGs** to the branch.
3. **The reviewer reads the image diff in the pull request**, and the changed-file list is the record
   of what was approved.

Points that matter for a repository that reviews by pull request:

- **Where the goldens live decides whether review is possible at all.** Paparazzi defaults to
  `src/test/snapshots` and Google's plugin to `src/screenshotTest<Variant>/reference/` — both inside
  the source tree, both committed, both visible in the diff. **Roborazzi defaults to
  `build/outputs/roborazzi`, which is not committed**; Google's sample overrides this and stores
  goldens in `modulename/src/test/screenshots`
  ([nowinandroid README](https://github.com/android/nowinandroid/blob/main/README.md)). Take the
  default and there is nothing to review.
- **Recording must happen where CI records.** Because of §2's rendering drift, a golden re-recorded on
  a developer laptop will differ from CI's for reasons unrelated to the change. The two documented
  arrangements are Google's — re-record on `main` locally first, so `verify` afterwards shows only
  your own changes — and Roborazzi's — have CI do both recording and verification. For a one-person
  repository whose CI is the only Android-capable environment (as `CLAUDE.md` describes), a
  manually-triggered "record goldens" workflow that pushes the PNGs to the pull-request branch is the
  arrangement that keeps the two sides identical by construction.
- **Diff artefacts help the human decide.** Each tool emits an HTML report — Paparazzi at
  `build/reports/paparazzi/<variant>` (the task logs "See the Paparazzi report at: …"), Roborazzi at
  `build/reports/roborazzi/index.html` with comparison images in `build/test-results/roborazzi`,
  Google's plugin at `build/reports/screenshotTest/preview/{variant}/index.html`. On a failing check
  these are worth uploading as workflow artefacts; Roborazzi's own CI guide does exactly that with
  `actions/upload-artifact` and, in a sample repository, posts the comparison into a pull-request
  comment ([README, "Integrate to your GitHub
  Actions"](https://github.com/takahirom/roborazzi/blob/main/README.md)).
- **Binary files in the repository.** Paparazzi's README recommends Git LFS for snapshots and shows a
  CI guard that fails if PNGs were committed outside LFS. Whether that is worth it here depends
  entirely on how many goldens there are; a handful of small component images is not a reason to adopt
  LFS, and LFS has its own failure modes on a public repository.
- **A renamed composable silently orphans its golden** under Google's plugin, since the reference file
  name embeds the fully qualified function name — the docs list this as a limitation. The pull request
  then shows a deleted PNG and an added PNG, which is reviewable but easy to wave through.

I could not reach `docs.github.com` or `github.blog` from this environment, so this note makes **no
cited claim** about GitHub's image-diff view modes. That GitHub renders image diffs for common formats
is widely relied upon (Roborazzi's comment-based sample depends on it), but it should be confirmed
against GitHub's own documentation before being written into a specification.

---

## 6. Where this collides with this repository's build invariants

[`docs/spec/build.md`](../spec/build.md) states three invariants. Two of them are touched.

**"The build runs from a clean checkout with nothing installed but a JDK."** Paparazzi and Google's
plugin comply: their rendering artefacts are ordinary pinned Gradle dependencies. **Roborazzi does
not**, because Robolectric downloads `android-all` jars from Maven Central at test runtime unless
`robolectric.dependency.dir` or `robolectric.offline` is set (source cited in §2). It can be made to
comply by vendoring or pre-fetching those jars and setting the system property — Robolectric's own
source recommends precisely this "if you require a hermetic build" — but that is a deliberate piece of
work, not a default.

**"Every version is pinned."** All three comply, with the caveat that the versions available to pin
are `1.3.5` (nearly two years old), `0.0.1-alpha15`, or a `1.74.0` that moves every couple of weeks.

**"No test dependency that needs a device or a simulated Android runtime."** This is the one that
blocks everything, and the question asked where it would have to be amended. Three readings, from
narrowest to widest:

1. **No amendment at all** if the tool's dependencies never appear on the unit-test classpath. Google's
   Compose Preview Screenshot Testing puts its tests in a separate `screenshotTest` source set and runs
   them from `validateDebugScreenshotTest`, so `./gradlew test` (BUILD-020) stays exactly as pure as
   it is today and BUILD-021 is untouched. The invariant's phrase is "no *test dependency*", and a
   `screenshotTestImplementation` dependency is arguably not one. This reading is defensible but it
   turns on a word, and the owner should decide it rather than inherit it from a research note.
2. **Amend the third invariant only**, to distinguish "runs on a device or emulator" (still forbidden)
   from "renders the Android framework in-process on the JVM" (permitted for screenshot and
   semantics tests). This is what Paparazzi needs, and what a Compose semantics test under Robolectric
   needs.
3. **Amend BUILD-021 as well**, which says the unit tests "run on the JVM alone — no emulator, no
   connected device, no simulated Android runtime". Robolectric *is* a simulated Android runtime by
   Google's own description, so Roborazzi and any `createComposeRule` test in `src/test` require this
   requirement to be rewritten, not merely reinterpreted.

**BUILD-041 needs a change regardless of which reading wins.** It currently runs `./gradlew test` and
`./gradlew assembleDebug`. As established in §2, **none of the three tools verifies goldens under
`./gradlew test`**; the workflow would need `validateDebugScreenshotTest`, `verifyPaparazziDebug` or
`verifyRoborazziDebug` added explicitly. Without that line, screenshot tests are recorded, committed,
and never checked — the worst of all outcomes, because the repository would look as though it had the
guarantee.

---

## Recommendation

The smallest set that genuinely holds a consistent interface for a one-person project, in adoption
order. Each step is useful on its own; each later step is worth less than the one before it.

### 1. A design-system package with one composable per semantic action — adopt first

`AppTheme` wrapping `MaterialTheme` with the project's `colorScheme`, `typography` and `shapes`; a
`staticCompositionLocalOf` token set only for what Material has no role for (spacing scale, the
counter's digit style); and `DestructiveButton`, `PrimaryButton`, `StepperUpButton`,
`StepperDownButton`, `IntervalCounter` defined once. Screens never call `Button`, never write
`Color(0xFF…)`, never pass `colors =`.

**Cost:** no new dependencies, no new CI time, an hour of structure. Separating it into its own Gradle
module so `material3` is an `implementation` dependency and the raw components are off the feature
modules' compile classpath is another hour and converts the rule from a convention into a compile
error.

**Why first:** it is the only item on this list that produces *agreement* rather than *detection*, and
every later item is cheaper and more meaningful once it exists. A screenshot suite over a codebase
without it merely photographs the inconsistency.

### 2. JVM invariant tests over the semantics tree — adopt second

One test file that, for each screen, asserts over *all* nodes rather than named ones: every node with
a click action has a touch height and width of 48dp (`assertTouchHeightIsEqualTo(48.dp)`, or a
hand-written comparison, since no `AtLeast` form exists for touch bounds); every icon-only control has
a non-empty content description; the counter occupies the same `getBoundsInRoot()` on every screen
that shows one; the token colour pairs meet 4.5:1 (a pure `:core` unit test, no framework).

**Cost:** Robolectric plus `ui-test-junit4` on the test classpath — which means reading (3) in §6 and
solving Robolectric's runtime download for a hermetic build. Perhaps half a day. Runs in seconds
thereafter, produces no binary artefacts, and never needs a human to approve an image.

**Why second:** these assertions are *quantified over every screen*, which is the thing the question
asks for and the thing screenshots cannot do. If the Robolectric cost is judged too high, the contrast
test alone still fits in `:core` today with no dependency at all.

### 3. One screenshot-tested component gallery — adopt third

A single `@PreviewTest` composable showing every button variant, both arrows, the counter and a dialog
together, captured as one reference image; plus one image per screen. Google's
`com.android.compose.screenshot` plugin is the recommended vehicle **for this repository specifically**,
because it keeps its dependencies out of the unit-test classpath and leaves `./gradlew test` untouched,
which is the narrowest possible collision with the build invariants. Record on CI only. Add
`validateDebugScreenshotTest` to the pull-request workflow in the same change that adds the first
golden.

**Cost:** an alpha plugin and an experimental Gradle property; a third command in CI; committed PNGs;
and a recurring tax whenever a composable is renamed or the plugin's reference-image layout changes
between alphas. Budget a day to set up and an ongoing few minutes per intentional visual change.

**Why third and why one gallery:** a gallery image is the only screenshot artefact that shows
*disagreement between components* rather than change over time, and one image is one thing to re-record
and one thing to review.

### 4. Prohibition rules — adopt last, and only if step 1 leaks

detekt with `ForbiddenImport` (no feature package imports `androidx.compose.material3.*`) is the cheap
version and needs no type resolution. `ForbiddenMethodCall` on `androidx.compose.ui.graphics.Color` and
`MagicNumber` are the thorough version and need type resolution and an exclusion list. A custom Android
Lint detector is the thorough-and-integrated version and costs an unstable API you re-check on every
AGP bump.

**Cost:** an afternoon for the `ForbiddenImport` version; substantially more for the others, plus
detekt's own analysis time on every build.

**Why last:** if step 1's module boundary is in place, most of what these rules would catch is already
a compile error, and a rule that never fires is a rule that costs review attention for nothing.

### The strongest argument against this recommendation

**That steps 2–4 buy very little on top of step 1, and step 3 is the one most likely to be abandoned —
which is worse than never adopting it.** For a single developer with an interval timer of perhaps six
screens, one design-system file and the IDE's preview pane already deliver most of the consistency;
the reviewer and the author are the same person, so the golden-image ceremony has no second pair of
eyes to serve. Meanwhile the tool recommended in step 3 has been `0.0.1-alphaN` for its entire
existence and reserves the right to change substantially; the mature alternative has shipped no stable
release since November 2024; and both render through a stack that Google's own sample app warns will
produce different pixels on a laptop than on CI. The predictable end state is a directory of PNGs that
fail for reasons nobody can explain, an `update…ScreenshotTest` run reflexively to make the build
green, and a repository that *looks* as though it has a guarantee while having trained its only
developer to click past the one signal it produces. On that argument the honest minimum is step 1 plus
step 2 — structure, and assertions that quantify over every screen and never need a human to approve a
picture — and step 3 waits until either the plugin reaches a stable release or a second reviewer
exists to benefit from it.

---

## Open questions

1. **Do `enableAccessibilityChecks()` and the Accessibility Test Framework actually run under
   Robolectric on this stack?** Google's page neither confirms nor denies it, and Roborazzi's module
   is indirect evidence rather than a statement. This needs a five-line experiment before anything
   depends on it.
2. **Which reading of the third build invariant does the owner want** — the narrow one that admits
   Google's screenshot plugin without amendment, or an explicit amendment distinguishing an
   in-process Android framework from a device? This is a specification decision, not a research
   finding.
3. **Is a Robolectric download inside `./gradlew test` acceptable if pinned and pre-fetched?** The
   hermetic-build workaround is documented by Robolectric itself, but it adds a vendored ~40 MB
   artefact or a CI pre-fetch step, and the first build invariant deserves an explicit answer rather
   than a workaround applied quietly.
4. **Where do goldens live and who records them?** A manually-dispatched CI workflow that pushes
   re-recorded images to the pull-request branch is the arrangement that survives the rendering-drift
   problem, but it needs a workflow with write permission, which BUILD-042 currently forbids
   (`contents: read` and nothing else).
5. **Does GitHub's image diff behave as assumed in review?** `docs.github.com` is unreachable from this
   environment, so no claim here is cited. Confirm before writing a review procedure that depends on
   it.
6. **Is a separate `:designsystem` Gradle module wanted**, or does the design system live in a package
   inside `:app`? The compile-time enforcement in step 1 only exists at a module boundary; inside one
   module, Kotlin `internal` does not help and the rule falls back to detekt's `ForbiddenImport`.
