package com.derekwinters.intervaltrainer

/**
 * Where presets are read and written.
 *
 * `:core` defines this seam and does not implement it (ADR 0005); `:database` implements it
 * (`SCHEMA-004`), and an in-memory fake substitutes it in `:core`'s own tests.
 *
 * Every operation is `suspend` (`SCHEMA-004`): Room's Kotlin Multiplatform compiler accepts only
 * `suspend` DAO functions on `:database`'s `jvm()` target, so the store above them is `suspend`
 * too. Being `suspend` does not by itself move a call off the caller's thread — a caller on the
 * main thread still chooses the dispatcher it reads on (`SVC-015`) — and no main source set
 * bridges these calls with `runBlocking`.
 *
 * The method set below is this issue's own minimal, reasonable choice — plain CRUD over
 * [Preset] — rather than something a requirement pins down; a later issue may need to add to it.
 */
interface PresetStore {
    suspend fun presets(): List<Preset>

    suspend fun preset(id: String): Preset?

    suspend fun save(preset: Preset)

    suspend fun delete(id: String)
}
