// :designsystem is an Android library module (ADR 0007), never pure JVM like :core: it exists to
// hold Compose UI — the token layer and, later, the component vocabulary — which needs the Compose
// runtime and (for its own screen-level tests, DS-091/093) Robolectric, neither of which a pure-JVM
// module can host.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.derekwinters.intervaltrainer.designsystem"
    compileSdk = 35

    defaultConfig {
        // Matches :app's own floor (BUILD-010); a design-system module usable only down to a
        // different minSdk than the app that depends on it would be a contradiction.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric's own recommendation for a Compose semantics-tree test (DS-091): without
            // this, resource-backed values the token layer reads — the JetBrains Mono font
            // (TimerTypography.kt) among them — are not guaranteed to resolve under a simulated
            // runtime.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // The same Compose BOM version :app imports (BUILD-017), so the two modules resolve one
    // literal set of androidx.compose.* versions rather than two that could drift apart.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Vector icon assets (Icons.Filled.*) for the icon-button roles and ScreenHeader's icon
    // trailing action (DS-005–007, DS-020). This is a separate `androidx.compose.material` icons
    // artifact, not `androidx.compose.material3` — it ships icon data, not a Material *component*,
    // so depending on it does not touch the DS-090/ADR-0007 module boundary that keeps raw Material
    // widgets out of :app's reach.
    implementation("androidx.compose.material:material-icons-core")

    // material3 is declared `implementation`, never `api` (ADR 0007 Decision 1, DS-090): Gradle's
    // `implementation` dependencies are not exposed on a consuming module's compile classpath, so
    // :app has no compile-time access to any androidx.compose.material3 type — only to whatever
    // this module chooses to expose (AppTheme, and later PrimaryButton/ScreenHeader/etc). Declaring
    // this as `api` here would be the one change that undoes that enforcement outright.
    implementation("androidx.compose.material3:material3")

    // The unit tests run on the JVM alone (BUILD-021): the token layer's own tests need no
    // Android runtime, since androidx.compose.ui.graphics.Color and
    // androidx.compose.material3.ColorScheme are plain, JVM-computable types.
    testImplementation("junit:junit:4.13.2")

    // Robolectric arrives here, and only here, with the feature that justifies it: the
    // semantics-tree assertions over the component gallery (DS-091, DS-095, BUILD-023). Neither
    // :core nor :database takes this dependency (ADR 0005). `createComposeRule()` from
    // `ui-test-junit4` is, per AndroidX's own source, `createAndroidComposeRule<ComponentActivity>()`
    // under the hood: it launches a real (empty) `androidx.activity.ComponentActivity` via
    // `ActivityScenarioRule`, the same launch path `RoboMonitoringInstrumentation` resolves through
    // `PackageManager` — it does not host a composition without one, whatever an earlier draft of
    // this comment (and of `DesignSystemConsistencyTest.kt`'s own KDoc) claimed. `ui-test-manifest`
    // is exactly the artifact that declares that `ComponentActivity` for `PackageManager` to
    // resolve; without it, `startActivitySyncInternal` cannot resolve an `ActivityInfo` for the
    // launch intent and throws before any content is ever set.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// DS-092 / BUILD-023: left to its own defaults, Robolectric resolves its `android-all` jar with
// its own `MavenDependencyResolver` — downloading it from Maven Central the first time a test
// needs it, into Robolectric's own real local Maven repository (`~/.m2/repository` by default) —
// and reuses whatever it finds already there on every later resolution without touching the
// network again, no extra configuration required. A network fetch from inside the test run is
// exactly what `docs/spec/build.md`'s clean-checkout invariant (BUILD-021) forbids for the
// `./gradlew test` invocation that gates a pull request, so CI (`.github/workflows/pr.yml`,
// `.github/workflows/release-candidate.yml`) caches that same `~/.m2/repository` directory with
// `actions/cache` between runs instead of vendoring the jar.
//
// Two of Robolectric's own real configuration properties
// (https://robolectric.org/configuring/), `robolectric.dependency.dir` and `robolectric.offline`,
// look like the obvious way to also *prove* a run stayed off the network, but neither is
// compatible with the Maven-repository cache above: `robolectric.dependency.dir`
// (`LocalDependencyResolver`) reads a flat, non-Maven-layout directory that nothing populates by
// a live resolution, and `robolectric.offline` set on its own — without `dependency.dir` — falls
// back to resolving against the current working directory rather than the Maven cache at all
// (Robolectric's own `LegacyDependencyResolver`, which is still the default resolver in 4.16.1).
// So the property below is `robolectric.dependency.repo.url` instead: CI points it at a
// deliberately unreachable host only for the run that must prove the cache is warm, since
// `MavenDependencyResolver` only ever opens a connection to that URL for an artifact it cannot
// already find in `~/.m2/repository` — a warm cache never reaches it, and a cold one fails loudly
// instead of silently downloading from the real Maven Central. Not set for a local developer run,
// so a local `./gradlew test` keeps Robolectric's ordinary live-download behaviour as its
// fallback.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("ROBOLECTRIC_DEPENDENCY_REPO_URL").orNull?.let { repoUrl ->
        systemProperty("robolectric.dependency.repo.url", repoUrl)
    }

    // Diagnosability (#78, #107): the default `Test` task logging collapses a failed assertion to
    // one line — class, method name and source location — with no message and no value, which is
    // exactly why `--stacktrace` on the Gradle CLI invocation in `.github/workflows/pr.yml` and
    // `.github/workflows/release-candidate.yml` did not surface what `assertHeightIsAtLeast`
    // actually measured. `TestExceptionFormat.FULL` prints the thrown exception's own message —
    // the `AssertionError` text Compose's testing API builds, which names the node and the value it
    // measured — alongside the stack trace, in the same console output CI already captures.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
