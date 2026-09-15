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

android {
    namespace = "com.derekwinters.intervaltrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.derekwinters.intervaltrainer"
        minSdk = 24
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
}

dependencies {
    // The unit tests run on the JVM alone (BUILD-021), so nothing here needs a device.
    testImplementation("junit:junit:4.13.2")
}
