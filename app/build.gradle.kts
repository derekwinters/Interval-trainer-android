import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyAliasPassword
            }
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

    // The unit tests run on the JVM alone (BUILD-021), so nothing here needs a device.
    testImplementation("junit:junit:4.13.2")
}
