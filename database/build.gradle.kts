// :database is this project's first Kotlin Multiplatform module (ADR 0003; SCHEMA-001): an
// `androidTarget()` so :app can consume it, and a `jvm()` target so its DAO tests run as plain
// JVM unit tests with no Android runtime (docs/spec/schema.md's third invariant; BUILD-021,
// BUILD-023).
//
// This module is on the Room 2.x line (`androidx.room`), not Room 3 (`androidx.room3`, the
// current stable release as of the research on #27). Both publish the same Kotlin Multiplatform
// shape the ADR and research describe — `androidTarget()` + `jvm()`, `androidx.sqlite`'s
// `BundledSQLiteDriver` — but Room 3 is suspend-only and, per its own release notes, most likely
// requires a newer Kotlin than the 2.0.21 pinned in the root build.gradle.kts. ADR 0003's
// "Consequences" section is explicit that a Kotlin upgrade "should be named as such when the
// feature issues are filed; it is not a side-effect to be absorbed quietly into the first
// database pull request" — this issue names no such upgrade, so this module stays on the line
// that does not need one. Room 2.7+'s Kotlin Multiplatform DAOs may still be plain (non-`suspend`)
// functions, which is what lets `RoomPresetStore` implement `:core`'s synchronous `PresetStore`
// (SCHEMA-004) directly, with no `runBlocking` bridging.
//
// None of this could be resolved or compiled in the sandbox this module was written in — Room and
// androidx.sqlite are published only to Google's Maven repository, which that sandbox could not
// reach, and are not mirrored on Maven Central. The `pr` workflow, which does reach it, is this
// module's first real build; see the pull request for exactly what was and was not verified.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("androidx.room:room-runtime:2.7.0")
                implementation("androidx.sqlite:sqlite-bundled:2.5.0")
            }
        }

        // :core applies only the Kotlin JVM plugin (BUILD-014) — it is not itself a Kotlin
        // Multiplatform module, so it publishes no `common` variant `commonMain` could resolve
        // against. This intermediate source set is shared by both of this module's actual
        // targets, both of which *are* plain JVM bytecode, so it is where :database's own code
        // depends on `:core`'s `PresetStore` interface and `Preset`/`Interval` types (SCHEMA-004)
        // — :core depends on nothing here, only the reverse.
        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(project(":core"))
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommonMain)
        }

        val jvmMain by getting {
            dependsOn(jvmCommonMain)
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":core"))
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

android {
    namespace = "com.derekwinters.intervaltrainer.database"
    compileSdk = 35

    defaultConfig {
        // Matches :app's own floor (BUILD-010); a module :app depends on cannot ask for less.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // KSP runs once per target that should get generated `_Impl` classes (`kspAndroid`,
    // `kspJvm`) — a target with no `ksp<Target>` dependency gets none at all.
    add("kspAndroid", "androidx.room:room-compiler:2.7.0")
    add("kspJvm", "androidx.room:room-compiler:2.7.0")
}

// SCHEMA-003: Room's own convention, exported on every compile and committed to version control,
// never shipped in the APK. This is the documented fallback KSP argument for a build that does
// not apply Room's own Gradle plugin — this module skips that plugin deliberately, to keep one
// fewer plugin-version pairing this sandbox had no way to check against a real build.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
