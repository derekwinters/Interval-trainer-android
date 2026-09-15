// :core is pure Kotlin, JVM only (ADR 0005, BUILD-014). Only the Kotlin JVM plugin is applied —
// never the Android library plugin — so android.* is not on this module's compile classpath at
// all, and an import of it is a compile error rather than a review comment.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // The unit tests run on the JVM alone (BUILD-021), so nothing here needs a device.
    testImplementation("junit:junit:4.13.2")
}
