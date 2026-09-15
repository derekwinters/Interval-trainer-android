// Plugin versions are declared once, here, for the whole build, and applied in the modules that
// need them (BUILD-003). Versions are literals: nothing about this build may change without a
// commit changing it.

plugins {
    id("com.android.application") version "8.7.3" apply false
    // The Android Gradle plugin ships application and library support from the same release
    // train, so this carries the identical literal version as com.android.application above
    // rather than being chosen independently.
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    // The Compose compiler ships from the Kotlin repository and is version-locked to it (BUILD-016),
    // so this stays the same literal as the Kotlin plugin above rather than being chosen on its own.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
