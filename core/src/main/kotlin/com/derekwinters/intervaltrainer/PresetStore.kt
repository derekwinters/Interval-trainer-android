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
 * The method set below started as plain CRUD over [Preset]; [reorder] and [saveAfter] were added
 * for reordering the list and duplicating a preset (#163).
 */
interface PresetStore {
    suspend fun presets(): List<Preset>

    suspend fun preset(id: String): Preset?

    suspend fun save(preset: Preset)

    suspend fun delete(id: String)

    /**
     * `docs/spec/schema.md` `SCHEMA-015` (#163): stores [orderedIds] — every preset's id, in the
     * order home now shows them — as the list's order. Changes nothing but the order.
     */
    suspend fun reorder(orderedIds: List<String>)

    /**
     * `SCHEMA-017` (#163): saves [preset], which does not exist yet, directly after the preset
     * [afterId] in the list, or at the end if [afterId] no longer exists. What a duplicate is
     * saved with (`docs/spec/screens.md` `SCREEN-019b`).
     */
    suspend fun saveAfter(preset: Preset, afterId: String)
}
