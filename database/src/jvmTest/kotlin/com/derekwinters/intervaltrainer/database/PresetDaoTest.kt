package com.derekwinters.intervaltrainer.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
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
     * SCHEMA-012: the preset list comes back in the order the rows were inserted — the table's
     * own row order — never a stored `position` column, which `presets` does not have.
     */
    @Test
    fun `lists presets in the order they were inserted, not id or name order`() {
        dao.upsertPreset(PresetEntity(id = "preset-c", name = "Zebra"))
        dao.upsertPreset(PresetEntity(id = "preset-a", name = "Apple"))
        dao.upsertPreset(PresetEntity(id = "preset-b", name = "Mango"))

        val ids = dao.getPresets().map { it.id }

        assertEquals(listOf("preset-c", "preset-a", "preset-b"), ids)
    }

    /**
     * SCHEMA-014, SCHEMA-021: deleting a preset removes every `intervals` row that references it.
     * [PresetDao.deletePreset] issues nothing but `DELETE FROM presets`, so this is the foreign
     * key's `ON DELETE CASCADE` doing the work, not any explicit cleanup call.
     */
    @Test
    fun `deleting a preset cascades to every one of its intervals`() {
        dao.upsertPreset(PresetEntity(id = "preset-1", name = "Short Example"))
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
    fun `deleting a preset leaves another preset's intervals alone`() {
        dao.upsertPreset(PresetEntity(id = "preset-1", name = "Short Example"))
        dao.upsertPreset(PresetEntity(id = "preset-2", name = "Long Example"))
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
     * presets, "Short Example" first and "Long Example" second — the order [SeedDataCallback]
     * inserts them in, read back per SCHEMA-012's insertion order.
     */
    @Test
    fun `onCreate seeds exactly the two endurance presets, Short Example then Long Example`() {
        val file = newTempDatabaseFile()
        val seeded = buildSeededDatabase(file)
        try {
            assertEquals(
                listOf("Short Example", "Long Example"),
                seeded.presetDao().getPresets().map { it.name },
            )
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
    fun `onCreate seeds the Short Example preset's eight intervals totalling 900 seconds`() {
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
    fun `onCreate seeds the Long Example preset's eighteen intervals totalling 2040 seconds`() {
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
    fun `reopening an already-seeded database file does not seed the presets again`() {
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
