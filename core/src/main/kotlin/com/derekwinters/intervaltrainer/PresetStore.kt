package com.derekwinters.intervaltrainer

/**
 * Where presets are read and written.
 *
 * `:core` defines this seam and does not implement it (ADR 0005); `:database` implements it
 * (`SCHEMA-004`), and an in-memory fake substitutes it in `:core`'s own tests.
 *
 * The method set below is this issue's own minimal, reasonable choice — plain CRUD over
 * [Preset] — rather than something a requirement pins down; a later issue may need to add to it.
 */
interface PresetStore {
    fun presets(): List<Preset>

    fun preset(id: String): Preset?

    fun save(preset: Preset)

    fun delete(id: String)
}
