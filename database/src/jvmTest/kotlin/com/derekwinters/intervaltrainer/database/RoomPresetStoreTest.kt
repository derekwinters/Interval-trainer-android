package com.derekwinters.intervaltrainer.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [RoomPresetStore] — `:core`'s [PresetStore][com.derekwinters.intervaltrainer.PresetStore]
 * (SCHEMA-004) as this module actually implements it — on the same real, bundled SQLite engine as
 * [PresetDaoTest]. [PresetDaoTest] covers the two behaviours the issue names (SCHEMA-012,
 * SCHEMA-014); this file exists so the mapping between [Preset]/[Interval] and the Room entities
 * — the other half of what this issue delivers — is exercised by something red before it works,
 * not left to manual inspection.
 */
class RoomPresetStoreTest {

    private lateinit var databaseFile: File
    private lateinit var database: IntervalTrainerDatabase
    private lateinit var store: RoomPresetStore

    @Before
    fun setUp() {
        databaseFile = File.createTempFile("preset-store-test-${UUID.randomUUID()}", ".db")
        databaseFile.deleteOnExit()
        database = Room.databaseBuilder<IntervalTrainerDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        store = RoomPresetStore(database.presetDao())
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
    }

    /**
     * A saved preset reads back with the same id, name and intervals — same kinds, same
     * durations, same order — round-tripped through Room's `TEXT` `kind` column (SCHEMA-023) and
     * `position` column (SCHEMA-025).
     */
    @Test
    fun `saves and reads back a preset with its intervals in order`() {
        val preset = Preset(
            id = "preset-1",
            name = "Short Example",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, durationSeconds = 180),
                Interval(IntervalKind.WORK, durationSeconds = 60),
                Interval(IntervalKind.RECOVERY, durationSeconds = 120),
                Interval(IntervalKind.COOL_DOWN, durationSeconds = 180),
            ),
        )

        store.save(preset)

        assertEquals(preset, store.preset("preset-1"))
    }

    /** Saving a preset again with a different interval list replaces the old one, not appends. */
    @Test
    fun `re-saving a preset replaces its interval list rather than appending to it`() {
        val original = Preset(
            id = "preset-1",
            name = "Short Example",
            intervals = listOf(Interval(IntervalKind.WARM_UP, durationSeconds = 180)),
        )
        val edited = original.copy(
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, durationSeconds = 300),
                Interval(IntervalKind.WORK, durationSeconds = 60),
            ),
        )

        store.save(original)
        store.save(edited)

        assertEquals(edited, store.preset("preset-1"))
    }

    /** `preset(id)` for an id nothing was ever saved under is `null`, not an error. */
    @Test
    fun `preset returns null for an id that was never saved`() {
        assertNull(store.preset("does-not-exist"))
    }

    /** Deleting a preset removes it from both `preset(id)` and the `presets()` list. */
    @Test
    fun `delete removes a preset from the store`() {
        store.save(Preset(id = "preset-1", name = "Short Example", intervals = emptyList()))

        store.delete("preset-1")

        assertNull(store.preset("preset-1"))
        assertEquals(emptyList<Preset>(), store.presets())
    }
}
