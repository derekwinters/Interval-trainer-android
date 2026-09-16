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
}
