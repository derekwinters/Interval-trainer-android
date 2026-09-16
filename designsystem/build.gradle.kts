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
    // `ui-test-junit4` hosts a composition directly under RobolectricTestRunner with no Activity
    // and no `ui-test-manifest` — that artifact only matters for `createAndroidComposeRule<T>()`,
    // which this module has no need of.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

// DS-092 / BUILD-023: left to its own defaults, Robolectric resolves its `android-all` jar by
// downloading it from Maven Central the first time a test needs it — a network fetch from inside
// whatever invokes the test, which is exactly what `docs/spec/build.md`'s clean-checkout invariant
// (BUILD-021) forbids for the `./gradlew test` invocation that gates a pull request. Both
// properties below are Robolectric's own, real names for the fix
// (https://robolectric.org/configuring/): `robolectric.dependency.dir` points it at a directory of
// already-downloaded jars in Maven layout, and `robolectric.offline` stops it from ever reaching
// past that directory. Neither is set for a local developer run — only CI sets the two environment
// variables below, once its own pre-fetch step (`.github/workflows/pr.yml`,
// `.github/workflows/release-candidate.yml`) has populated the directory and `actions/cache` has
// cached it — so a local `./gradlew test` keeps Robolectric's ordinary live-download behaviour as
// its fallback.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("ROBOLECTRIC_DEPENDENCY_DIR").orNull?.let { dependencyDir ->
        systemProperty("robolectric.dependency.dir", dependencyDir)
    }
    providers.environmentVariable("ROBOLECTRIC_OFFLINE").orNull?.let { offline ->
        systemProperty("robolectric.offline", offline)
    }
}
