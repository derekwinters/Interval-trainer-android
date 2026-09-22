import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// gradle.properties is the single source of truth for the app's version (BUILD-013).
// VERSION_NAME carries a trailing `# x-release-please-version` marker comment that
// release-please's generic updater rewrites on each release pull request; Java properties
// do not treat an inline `#` as a comment, so it is stripped here before use.
val versionNameProperty = (project.findProperty("VERSION_NAME") as String)
    .substringBefore("#")
    .trim()
val versionCodeProperty = (project.findProperty("VERSION_CODE") as String).trim().toInt()

// The release signing config is read from environment variables, never from a committed file
// or a Gradle property (SIGN-002, BUILD-050). `ANDROID_KEYSTORE_PATH` holds a filesystem path to
// the keystore the release workflow decodes from the `ANDROID_KEYSTORE_BASE64` secret; the other
// three are the remaining secrets by name. When any of the four is missing — a local build with
// no secrets, or a mis-wired workflow — no `release` signing config is created at all, and
// `release` is left with none, rather than falling back to `signingConfigs.debug`. An unsigned
// APK and a debug-signed one are both wrong, but only the second looks correct until the next
// release; leaving `release` unsigned keeps the failure visible instead of quiet.
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyAliasPassword = System.getenv("ANDROID_KEY_ALIAS_PASSWORD")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyAliasPassword.isNullOrBlank()

android {
    namespace = "com.derekwinters.intervaltrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.derekwinters.intervaltrainer"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeProperty
        versionName = versionNameProperty
    }

    // Java and Kotlin target the same bytecode version, so the two cannot disagree about the
    // class-file version they produce (BUILD-011).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyAliasPassword

                // Every signature scheme, stated rather than defaulted (SIGN-071, #127).
                //
                // With none of these set, AGP picks: it disables v1 whenever minSdk is 24 or
                // above, because v2 covers every device from Android 7.0 and a JAR signature is
                // then dead weight. That reasoning is correct, and the v0.2.x artifacts it
                // produced are provably sound — the published v0.2.2 APK's v2 signature was
                // verified twice independently while diagnosing #127, and its packaging checked
                // byte by byte. Android 16 requires neither v1 nor v3, so **this does not fix
                // that issue's install failure and is not claimed to**.
                //
                // The reason to write them out is narrower. This app is sideloaded, so it passes
                // through installers AGP knows nothing about, and — more to the point — a scheme
                // set that nothing states is one a toolchain upgrade can change without anything
                // reporting it. Stated here, verify_release_package.py can check the artifact
                // against an intention instead of against whatever AGP chose this week.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // Native libraries are stored uncompressed and page-aligned inside the APK, and mapped from
    // it rather than unpacked at install (BUILD-071). This is already AGP's default for
    // targetSdk 30 and above — the published v0.2.2 APK has `extractNativeLibs="false"` and all
    // twelve of its `.so` entries sit on 16 KB boundaries — so this states the requirement
    // rather than changing behaviour. It is written down because the requirement is the
    // device's, not AGP's: a 16 KB-page device cannot map a library that is compressed or
    // misaligned, and refuses to install the package. A default that happens to be right is
    // still a default, and nothing would have failed if it changed.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

// Every variant's APK is named `interval-trainer-<versionName>-<buildType>.apk` (BUILD-051), so a
// workflow can glob `app/build/outputs/apk/<buildType>/*.apk` without renaming the file itself.
// The public `VariantOutput` type (com.android.build.api.variant.VariantOutput) exposes only
// versionCode, versionName and enabled — outputFileName lives solely on the internal
// VariantOutputImpl that actually implements it, so that concrete type is what this casts to.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName.set("interval-trainer-$versionNameProperty-${variant.buildType}.apk")
            }
        }
    }
}

dependencies {
    // formatSeconds lives in :core now (ADR 0005, BUILD-014).
    implementation(project(":core"))

    // :database's Android target (SCHEMA-001): the home screen (SCREEN-002, #79) is the first
    // screen that reads a preset, via AppDatabase/RoomPresetStore.
    implementation(project(":database"))

    // :designsystem's exposed vocabulary (ADR 0007, BUILD-019): AppTheme, ScreenHeader, the
    // layouts and the button/icon-button set. The home screen (#79) is the first screen that
    // needed any of it, so this is the dependency DS-090/BUILD-017's own doc comments described
    // ahead of themselves — it is real now, and a raw `androidx.compose.material3` import inside
    // :app is a compile error from this point on, not merely a convention.
    implementation(project(":designsystem"))

    // The Compose BOM pins every androidx.compose.* artifact declared below to one literal
    // version (BUILD-017); the BOM's own version is itself a literal, per the build's second
    // invariant. androidx.compose.material3 is deliberately not declared here — see BUILD-017 and
    // ADR 0007 — so it is not managed through this platform import either.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Vector icon assets (Icons.Filled.*), for ScreenHeader's icon trailing action, the home
    // row's Edit control (SCREEN-001, SCREEN-005), and the running screen's transport controls
    // (#81) — Pause/SkipNext/Stop/VolumeOff/VolumeUp aren't in the curated -core set, so :app
    // needs the full -extended artifact (a strict superset of -core, never declared alongside
    // it). Ships icon data, not a Material *component* — depending on it directly from :app does
    // not touch the DS-090/ADR-0007 module boundary that keeps raw `material3` types out of
    // :app's reach, per :designsystem's own build.gradle.kts.
    implementation("androidx.compose.material:material-icons-extended")

    // Not managed by the Compose BOM, so each carries its own literal version (BUILD-017).
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // WorkoutService's own dependencies (SVC-010–024): NotificationCompat and the wake lock's
    // Context.getSystemService come from androidx.core; the tick loop is a coroutine.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // :database declares these as `implementation`, not `api` (database/build.gradle.kts), so
    // they do not reach :app's own compile classpath transitively — WorkoutService calls
    // `Room.databaseBuilder` and `BundledSQLiteDriver` directly (SCHEMA-004), so :app needs both
    // for itself, at the same literal versions :database already pins.
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.sqlite:sqlite-bundled:2.5.0")

    // The settings screen's default-mute value (docs/spec/screens.md SCREEN-080, DefaultMuteStore.kt):
    // Jetpack DataStore Preferences, kept separate from :database's own Room store — this one
    // value has no query, relation or migration story complex enough to need a schema.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // The unit tests run on the JVM alone (BUILD-021), so nothing here needs a device.
    testImplementation("junit:junit:4.13.2")
}

// Diagnosability (#78, #107), the same reasoning `:designsystem`'s own build file already carries:
// Gradle's default `Test` task logging collapses a failed assertion to one line — class, method
// name and source location — with no message and no value, so a test whose whole point is which
// of several conditions failed (WindowThemeDeclarationTest.kt, DS-022) reports nothing usable in
// the workflow log. `TestExceptionFormat.FULL` prints the `AssertionError`'s own message alongside
// the stack trace, in the console output `pr.yml` and `release-candidate.yml` already capture.
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
