package com.derekwinters.intervaltrainer.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.duplicate
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `saves and reads back a preset with its intervals in order`() = runBlocking<Unit> {
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
    fun `re-saving a preset replaces its interval list rather than appending to it`() = runBlocking<Unit> {
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
    fun `preset returns null for an id that was never saved`() = runBlocking<Unit> {
        assertNull(store.preset("does-not-exist"))
    }

    /** Deleting a preset removes it from both `preset(id)` and the `presets()` list. */
    @Test
    fun `delete removes a preset from the store`() = runBlocking<Unit> {
        store.save(Preset(id = "preset-1", name = "Short Example", intervals = emptyList()))

        store.delete("preset-1")

        assertNull(store.preset("preset-1"))
        assertEquals(emptyList<Preset>(), store.presets())
    }

    /**
     * docs/spec/screens.md SCREEN-019d (#164): what the editor's confirmed Delete does to the
     * store — the preset and its intervals are gone, and every other preset is exactly as it was,
     * intervals and list order included.
     */
    @Test
    fun `deleting a preset removes it and its intervals and leaves every other preset unchanged`() = runBlocking<Unit> {
        val before = Preset(
            id = "before",
            name = "Before",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, durationSeconds = 120),
                Interval(IntervalKind.WORK, durationSeconds = 45),
            ),
        )
        val doomed = Preset(
            id = "doomed",
            name = "Tabata",
            intervals = listOf(
                Interval(IntervalKind.WORK, durationSeconds = 20),
                Interval(IntervalKind.RECOVERY, durationSeconds = 10),
            ),
        )
        val after = Preset(
            id = "after",
            name = "After",
            intervals = listOf(Interval(IntervalKind.COOL_DOWN, durationSeconds = 300)),
        )
        store.save(before)
        store.save(doomed)
        store.save(after)

        store.delete("doomed")

        assertNull(store.preset("doomed"))
        assertTrue(database.presetDao().getIntervals("doomed").isEmpty())
        assertEquals(listOf(before, after), store.presets())
    }

    /** SCHEMA-015: `reorder` is the order `presets()` returns afterwards. */
    @Test
    fun `reorder changes the order presets are listed in`() = runBlocking<Unit> {
        store.save(Preset(id = "preset-1", name = "One", intervals = emptyList()))
        store.save(Preset(id = "preset-2", name = "Two", intervals = emptyList()))

        store.reorder(listOf("preset-2", "preset-1"))

        assertEquals(listOf("preset-2", "preset-1"), store.presets().map { it.id })
    }

    /**
     * SCHEMA-017, docs/spec/screens.md SCREEN-019b: a duplicate saved with `saveAfter` sits
     * directly after its original and is a deep copy — editing it leaves the original as it was,
     * and deleting the original leaves the copy intact.
     */
    @Test
    fun `a duplicate saved after its original is placed next to it and is independent of it`() = runBlocking<Unit> {
        val original = Preset(
            id = "original",
            name = "Hills",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, durationSeconds = 180),
                Interval(IntervalKind.WORK, durationSeconds = 60),
            ),
        )
        store.save(original)
        store.save(Preset(id = "other", name = "Other", intervals = emptyList()))

        val copy = original.duplicate(newId = "copy", takenNames = store.presets().map { it.name })
        store.saveAfter(copy, afterId = "original")

        assertEquals(listOf("original", "copy", "other"), store.presets().map { it.id })
        assertEquals(copy, store.preset("copy"))

        store.save(copy.copy(intervals = listOf(Interval(IntervalKind.COOL_DOWN, durationSeconds = 300))))
        assertEquals(original, store.preset("original"))

        store.delete("original")
        assertEquals(listOf(Interval(IntervalKind.COOL_DOWN, durationSeconds = 300)), store.preset("copy")?.intervals)
    }
}
