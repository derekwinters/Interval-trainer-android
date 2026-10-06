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
// that does not need one. Room 2.x allows plain (non-`suspend`) DAO functions on `androidTarget()`
// only: a source set targeting a non-Android platform, which `jvm()` is, accepts only `suspend`
// DAO functions. So `PresetDao` is `suspend` throughout, and `RoomPresetStore` implements
// `:core`'s `suspend` `PresetStore` (SCHEMA-004) directly, with no `runBlocking` bridging (#166).
//
// None of this could be resolved or compiled in the sandbox this module was written in — Room and
// androidx.sqlite are published only to Google's Maven repository, which that sandbox could not
// reach, and are not mirrored on Maven Central. The `pr` workflow, which does reach it, is this
// module's first real build; see the pull request for exactly what was and was not verified.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
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
                // The DAO and the preset store are `suspend` (SCHEMA-004); the tests call them
                // from `runBlocking`. Same version :app pins.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // SCHEMA-041, SCHEMA-045 (#163): the upgrade-path test's MigrationTestHelper, at
                // the same literal as room-runtime/room-compiler. Its jvm() artifact is a plain
                // JUnit 4 rule — no Robolectric, no instrumentation (BUILD-023).
                implementation("androidx.room:room-testing:2.7.0")
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

// BUILD-020 (#144): plain `./gradlew test` must run this module's tests. In a Kotlin
// Multiplatform module with the Android library plugin, `test` is the Android Gradle Plugin's
// aggregate over the Android unit-test variants only (`testDebugUnitTest`, `testReleaseUnitTest`),
// whose source sets here are empty — so it reported NO-SOURCE and passed having run nothing.
// Every test this module has lives in `jvmTest`, which only the `jvm()` target's task runs; this
// makes the aggregate reach it, so the documented command (BUILD-041, `commands.test`) is
// unchanged and a failing DAO test fails it locally and in CI alike. Matched lazily rather than
// with `tasks.named("test")`: the Android Gradle Plugin registers `test` after this script has
// been evaluated, so `named` fails with "Task with name 'test' not found" (seen on #144's first
// CI run).
tasks.matching { it.name == "test" }.configureEach {
    dependsOn("jvmTest")
}

// SCHEMA-003: Room's own convention, exported on every compile and committed to version control,
// never shipped in the APK. This module applies Room's own Gradle plugin (`androidx.room`, pinned
// next to the `androidx.room:room-runtime`/`room-compiler` coordinates above in the root
// build.gradle.kts) rather than passing `room.schemaLocation` to `ksp {}` directly: this is a
// Kotlin Multiplatform module with both `androidTarget()` and `jvm()`, so Room's compiler runs as
// more than one KSP task (`kspDebugKotlinAndroid`, `kspReleaseKotlinAndroid`, `kspKotlinJvm`). A
// single shared `ksp { arg(...) }` path sends every one of those tasks to the exact same schema
// file, and Room's own `exportSchema` step reads that file to validate against before writing a
// new one — so when two tasks run close together, one can read the other's still-in-progress
// write and fail on truncated JSON. The plugin's `room { schemaDirectory(...) }` DSL is
// variant-aware, but not in the shape that sentence once claimed: each KSP task writes its own
// export under `build/intermediates/room/schemas/<kspTaskName>/`, and a separate
// `copyRoomSchemas` task then consolidates those into the directory configured below. So no two
// tasks ever contend for the same file, and what lands here is one committed file per database
// class — `schemas/<database class FQN>/<version>.json` — not one per variant. Confirmed from a
// real build (issue 100), not from reading the plugin alone.
room {
    schemaDirectory("$projectDir/schemas")
}
