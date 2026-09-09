// Plugin versions are declared once, here, for the whole build, and applied in the modules that
// need them (BUILD-003). Versions are literals: nothing about this build may change without a
// commit changing it.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
