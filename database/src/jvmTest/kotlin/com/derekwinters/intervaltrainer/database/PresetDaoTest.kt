package com.derekwinters.intervaltrainer.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [PresetDao], against a real, bundled SQLite engine
 * (`androidx.sqlite:sqlite-bundled`'s [BundledSQLiteDriver]) rather than a mock or Robolectric —
 * `docs/spec/schema.md`'s third invariant, `BUILD-021`, `BUILD-023`. No Android runtime is
 * involved anywhere in this file.
 */
class PresetDaoTest {

    private lateinit var databaseFile: File
    private lateinit var database: IntervalTrainerDatabase
    private lateinit var dao: PresetDao

    @Before
    fun setUp() {
        databaseFile = File.createTempFile("preset-dao-test-${UUID.randomUUID()}", ".db")
        databaseFile.deleteOnExit()
        database = Room.databaseBuilder<IntervalTrainerDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = database.presetDao()
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
    }

    /**
     * SCHEMA-012: the preset list comes back in stored `position` order — not insertion order,
     * id order or name order. The rows are inserted in an order that matches none of those, so a
     * query still ordering on `rowid` fails here (schema.md's "read from `presets.position`, never
     * from `rowid`" invariant).
     */
    @Test
    fun `lists presets in stored position order, not insertion, id or name order`() = runBlocking<Unit> {
        dao.upsertPreset(PresetEntity(id = "preset-c", name = "Apple", position = 2))
        dao.upsertPreset(PresetEntity(id = "preset-a", name = "Zebra", position = 1))
        dao.upsertPreset(PresetEntity(id = "preset-b", name = "Mango", position = 0))

        val ids = dao.getPresets().map { it.id }

        assertEquals(listOf("preset-b", "preset-a", "preset-c"), ids)
    }

    /**
     * SCHEMA-016: a preset saved for the first time goes to the end of the list, and re-saving an
     * existing one — an edit — keeps it where it was.
     */
    @Test
    fun `a new preset is appended at the end, and re-saving one does not move it`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = emptyList())
        dao.savePreset(id = "preset-2", name = "Two", intervals = emptyList())
        dao.savePreset(id = "preset-3", name = "Three", intervals = emptyList())

        assertEquals(listOf("preset-1", "preset-2", "preset-3"), dao.getPresets().map { it.id })
        assertEquals(listOf(0, 1, 2), dao.getPresets().map { it.position })

        dao.savePreset(id = "preset-1", name = "One, renamed", intervals = emptyList())

        assertEquals(listOf("preset-1", "preset-2", "preset-3"), dao.getPresets().map { it.id })
        assertEquals("One, renamed", dao.getPresets().first().name)
    }

    /** SCHEMA-016: a new preset appends after the largest stored position, not at the row count. */
    @Test
    fun `a new preset is appended after the largest stored position`() = runBlocking<Unit> {
        dao.upsertPreset(PresetEntity(id = "preset-1", name = "One", position = 5))

        dao.savePreset(id = "preset-2", name = "Two", intervals = emptyList())

        assertEquals(listOf("preset-1", "preset-2"), dao.getPresets().map { it.id })
    }

    /**
     * SCHEMA-015: a reorder is what the list reads afterwards, renumbered from zero, and it
     * changes nothing but `position` — names and interval rows come through untouched.
     */
    @Test
    fun `reordering persists the new order and touches nothing else`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = intervalsFor("preset-1", 180, 60))
        dao.savePreset(id = "preset-2", name = "Two", intervals = intervalsFor("preset-2", 300))
        dao.savePreset(id = "preset-3", name = "Three", intervals = emptyList())
        val intervalsBefore = dao.getIntervals("preset-1")

        dao.reorderPresets(listOf("preset-3", "preset-1", "preset-2"))

        val presets = dao.getPresets()
        assertEquals(listOf("preset-3", "preset-1", "preset-2"), presets.map { it.id })
        assertEquals(listOf(0, 1, 2), presets.map { it.position })
        assertEquals(listOf("Three", "One", "Two"), presets.map { it.name })
        assertEquals(intervalsBefore, dao.getIntervals("preset-1"))
        assertEquals(listOf(300), dao.getIntervals("preset-2").map { it.durationSeconds })
    }

    /** SCHEMA-015: the reordered list is still that order after the database is closed and reopened. */
    @Test
    fun `a reorder survives closing and reopening the database`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = emptyList())
        dao.savePreset(id = "preset-2", name = "Two", intervals = emptyList())
        dao.reorderPresets(listOf("preset-2", "preset-1"))
        database.close()

        database = Room.databaseBuilder<IntervalTrainerDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = database.presetDao()

        assertEquals(listOf("preset-2", "preset-1"), dao.getPresets().map { it.id })
    }

    /**
     * SCHEMA-017: a preset inserted after another sits directly after it; the presets that were
     * after it move down one place.
     */
    @Test
    fun `a preset inserted after another sits directly after it`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = emptyList())
        dao.savePreset(id = "preset-2", name = "Two", intervals = emptyList())
        dao.savePreset(id = "preset-3", name = "Three", intervals = emptyList())

        dao.insertPresetAfter(afterId = "preset-1", id = "copy", name = "One (copy)", intervals = emptyList())

        assertEquals(listOf("preset-1", "copy", "preset-2", "preset-3"), dao.getPresets().map { it.id })
        assertEquals(listOf(0, 1, 2, 3), dao.getPresets().map { it.position })
    }

    /** SCHEMA-017: inserting after the last preset puts the new one last. */
    @Test
    fun `a preset inserted after the last one goes last`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = emptyList())
        dao.savePreset(id = "preset-2", name = "Two", intervals = emptyList())

        dao.insertPresetAfter(afterId = "preset-2", id = "copy", name = "Two (copy)", intervals = emptyList())

        assertEquals(listOf("preset-1", "preset-2", "copy"), dao.getPresets().map { it.id })
    }

    /** SCHEMA-017: if the preset to insert after no longer exists, the new one is appended. */
    @Test
    fun `a preset inserted after a missing one is appended`() = runBlocking<Unit> {
        dao.savePreset(id = "preset-1", name = "One", intervals = emptyList())

        dao.insertPresetAfter(afterId = "gone", id = "copy", name = "Gone (copy)", intervals = emptyList())

        assertEquals(listOf("preset-1", "copy"), dao.getPresets().map { it.id })
    }

    /**
     * SCHEMA-017 and the "move rows, never rewrite them" invariant: a duplicate is a deep copy —
     * its own `presets` row and its own `intervals` rows — so editing it leaves the original's
     * rows unchanged, and deleting either leaves the other intact.
     */
    @Test
    fun `a duplicate is a deep copy with its own rows`() = runBlocking<Unit> {
        dao.savePreset(id = "original", name = "Hills", intervals = intervalsFor("original", 180, 60, 120))
        val originalIntervals = dao.getIntervals("original")

        dao.insertPresetAfter(
            afterId = "original",
            id = "copy",
            name = "Hills (copy)",
            intervals = intervalsFor("copy", 180, 60, 120),
        )
        val copyIntervals = dao.getIntervals("copy")
        assertEquals(originalIntervals.map { it.durationSeconds }, copyIntervals.map { it.durationSeconds })
        assertTrue(originalIntervals.map { it.id }.intersect(copyIntervals.map { it.id }.toSet()).isEmpty())

        dao.savePreset(id = "copy", name = "Hills, edited", intervals = intervalsFor("copy", 30))
        assertEquals(originalIntervals, dao.getIntervals("original"))
        assertEquals("Hills", dao.getPreset("original")?.name)

        dao.deletePreset("original")
        assertEquals(listOf(30), dao.getIntervals("copy").map { it.durationSeconds })
        assertEquals(listOf("copy"), dao.getPresets().map { it.id })
    }

    private fun intervalsFor(presetId: String, vararg durations: Int): List<IntervalEntity> =
        durations.mapIndexed { index, seconds ->
            IntervalEntity(presetId = presetId, kind = "work", durationSeconds = seconds, position = index)
        }

    /**
     * SCHEMA-014, SCHEMA-021: deleting a preset removes every `intervals` row that references it.
     * [PresetDao.deletePreset] issues nothing but `DELETE FROM presets`, so this is the foreign
     * key's `ON DELETE CASCADE` doing the work, not any explicit cleanup call.
     */
    @Test
    fun `deleting a preset cascades to every one of its intervals`() = runBlocking<Unit> {
        dao.upsertPreset(PresetEntity(id = "preset-1", name = "Short Example", position = 0))
        dao.insertIntervals(
            listOf(
                IntervalEntity(presetId = "preset-1", kind = "warm_up", durationSeconds = 180, position = 0),
                IntervalEntity(presetId = "preset-1", kind = "work", durationSeconds = 60, position = 1),
                IntervalEntity(presetId = "preset-1", kind = "recovery", durationSeconds = 120, position = 2),
            ),
        )

        dao.deletePreset("preset-1")

        assertTrue(dao.getIntervals("preset-1").isEmpty())
    }

    /**
     * The cascade in the previous test is specific to the deleted preset: a sibling preset's
     * intervals must survive it untouched, or the assertion above would be vacuous in a database
     * with only one preset ever inserted.
     */
    @Test
    fun `deleting a preset leaves another preset's intervals alone`() = runBlocking<Unit> {
        dao.upsertPreset(PresetEntity(id = "preset-1", name = "Short Example", position = 0))
        dao.upsertPreset(PresetEntity(id = "preset-2", name = "Long Example", position = 1))
        dao.insertIntervals(
            listOf(
                IntervalEntity(presetId = "preset-1", kind = "warm_up", durationSeconds = 180, position = 0),
                IntervalEntity(presetId = "preset-2", kind = "warm_up", durationSeconds = 300, position = 0),
            ),
        )

        dao.deletePreset("preset-1")

        assertEquals(1, dao.getIntervals("preset-2").size)
    }

    /**
     * SCHEMA-030: seeding is opt-in per [Room.databaseBuilder] call — [setUp]'s own [database]
     * never adds [SeedDataCallback], which is exactly why the tests above start from an empty
     * database. These tests build a separate, independently seeded database instead of reusing
     * [database], and clean it up themselves.
     */
    private fun newTempDatabaseFile(): File {
        val file = File.createTempFile("preset-dao-seed-test-${UUID.randomUUID()}", ".db")
        file.deleteOnExit()
        return file
    }

    private fun buildSeededDatabase(file: File): IntervalTrainerDatabase =
        Room.databaseBuilder<IntervalTrainerDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .addCallback(SeedDataCallback)
            .build()

    /**
     * SCHEMA-030, SCHEMA-034: a freshly created, seeded database has exactly the two endurance
     * presets, "Short Example" first at `position` 0 and "Long Example" second at `position` 1,
     * read back in SCHEMA-012's stored order.
     */
    @Test
    fun `onCreate seeds exactly the two endurance presets, Short Example then Long Example`() = runBlocking<Unit> {
        val file = newTempDatabaseFile()
        val seeded = buildSeededDatabase(file)
        try {
            val presets = seeded.presetDao().getPresets()
            assertEquals(listOf("Short Example", "Long Example"), presets.map { it.name })
            assertEquals(listOf(0, 1), presets.map { it.position })
        } finally {
            seeded.close()
            file.delete()
        }
    }

    /**
     * SCHEMA-031, SCHEMA-033: the Short Example preset is warm-up 3:00, three rounds of work
     * 1:00 / recovery 2:00, then cool-down 3:00 — eight rows, ending its last round's recovery
     * before cool-down, totalling 900 seconds (15:00).
     */
    @Test
    fun `onCreate seeds the Short Example preset's eight intervals totalling 900 seconds`() = runBlocking<Unit> {
        val file = newTempDatabaseFile()
        val seeded = buildSeededDatabase(file)
        try {
            val seededDao = seeded.presetDao()
            val shortExample = seededDao.getPresets().single { it.name == "Short Example" }
            val intervals = seededDao.getIntervals(shortExample.id)

            assertEquals(
                listOf("warm_up", "work", "recovery", "work", "recovery", "work", "recovery", "cool_down"),
                intervals.map { it.kind },
            )
            assertEquals(
                listOf(180, 60, 120, 60, 120, 60, 120, 180),
                intervals.map { it.durationSeconds },
            )
            assertEquals(900, intervals.sumOf { it.durationSeconds })
        } finally {
            seeded.close()
            file.delete()
        }
    }

    /**
     * SCHEMA-032, SCHEMA-033: the Long Example preset is warm-up 5:00, eight rounds of work
     * 1:00 / recovery 2:00, then cool-down 5:00 — eighteen rows, ending its last round's
     * recovery before cool-down, totalling 2040 seconds (34:00).
     */
    @Test
    fun `onCreate seeds the Long Example preset's eighteen intervals totalling 2040 seconds`() = runBlocking<Unit> {
        val file = newTempDatabaseFile()
        val seeded = buildSeededDatabase(file)
        try {
            val seededDao = seeded.presetDao()
            val longExample = seededDao.getPresets().single { it.name == "Long Example" }
            val intervals = seededDao.getIntervals(longExample.id)

            val expectedKinds = listOf("warm_up") +
                List(8) { listOf("work", "recovery") }.flatten() +
                listOf("cool_down")
            val expectedDurations = listOf(300) +
                List(8) { listOf(60, 120) }.flatten() +
                listOf(300)

            assertEquals(18, intervals.size)
            assertEquals(expectedKinds, intervals.map { it.kind })
            assertEquals(expectedDurations, intervals.map { it.durationSeconds })
            assertEquals(2040, intervals.sumOf { it.durationSeconds })
        } finally {
            seeded.close()
            file.delete()
        }
    }

    /**
     * SCHEMA-030: seeding runs "only on a database created from nothing", never again for that
     * same file — not on a later launch, not on a migration. At v1 there is no migration to
     * drive this through (SCHEMA-043), so the closest a test can come is what this test does:
     * close a freshly seeded database and reopen the same file, and confirm the row count did
     * not double.
     */
    @Test
    fun `reopening an already-seeded database file does not seed the presets again`() = runBlocking<Unit> {
        val file = newTempDatabaseFile()
        val first = buildSeededDatabase(file)
        first.close()

        val reopened = buildSeededDatabase(file)
        try {
            assertEquals(2, reopened.presetDao().getPresets().size)
        } finally {
            reopened.close()
            file.delete()
        }
    }
}
