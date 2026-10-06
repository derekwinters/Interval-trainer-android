package com.derekwinters.intervaltrainer.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.nio.file.Paths
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The upgrade-path contract tests for [IntervalTrainerDatabase]'s schema — `docs/spec/schema.md`
 * §5 (SCHEMA-040–045), restated from `docs/adr/0003-room-with-the-schema-treated-as-an-api.md`'s
 * invariants: *the harness exists before the first migration*, and *a breaking schema change lands
 * only with a migration and an upgrade-path test that proves data survival*.
 *
 * This file was an empty harness while there was one schema version (SCHEMA-043, #75). Version 2
 * (#163) added the not-null `presets.position` column, a breaking change under SCHEMA-041, so the
 * first real test lives here now: [MIGRATION_1_2] (SCHEMA-045).
 *
 * The shape is the one this file's KDoc named ahead of time, verified then against Room 2.7.0's own
 * source: `androidx.room.testing.MigrationTestHelper` from `androidx.room:room-testing`, whose JVM
 * actual is a JUnit 4 `TestWatcher` used as a rule, with no Robolectric and no instrumentation
 * (`BUILD-021`, `BUILD-023`, schema.md's third invariant). `createDatabase(1)` builds the starting
 * database from the committed `database/schemas/.../1.json` (SCHEMA-003); rows go in through raw
 * SQL, because the DAO only knows the current schema; `runMigrationsAndValidate(2, ...)` runs the
 * migration and checks the result against the committed `2.json`; and then the rows that came
 * through are asserted on — prose does not prove a row survived.
 *
 * Gradle runs this module's tests with the module directory as the working directory, so the
 * relative `schemas` path below is `database/schemas/`, the same directory `room { schemaDirectory }`
 * in `database/build.gradle.kts` writes to.
 */
class SchemaMigrationTest {

    // A path with no file behind it yet: the helper creates the database there itself.
    private val databaseFile: File =
        File(System.getProperty("java.io.tmpdir"), "schema-migration-test-${UUID.randomUUID()}.db")

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        schemaDirectoryPath = Paths.get("schemas"),
        databasePath = databaseFile.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = IntervalTrainerDatabase::class,
    )

    @After
    fun tearDown() {
        databaseFile.delete()
    }

    /**
     * SCHEMA-041, SCHEMA-045: every v1 preset and every one of its interval rows survives the
     * upgrade, and the list keeps the order it had — v1's insertion order, which is neither id nor
     * name order here — now stored as `position` 0, 1, 2.
     */
    @Test
    fun `migrating 1 to 2 keeps every preset and interval and stores the insertion order`() {
        val v1 = migrationTestHelper.createDatabase(1)
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('preset-z', 'Apple')")
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('preset-a', 'Zebra')")
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('preset-m', 'Mango')")
        v1.execSQL(
            "INSERT INTO intervals (presetId, kind, durationSeconds, position) VALUES " +
                "('preset-z', 'warm_up', 180, 0), ('preset-z', 'work', 60, 1), " +
                "('preset-z', 'recovery', 120, 2), ('preset-a', 'cool_down', 300, 0)",
        )
        v1.close()

        val v2 = migrationTestHelper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        try {
            assertEquals(
                listOf(
                    Triple("preset-z", "Apple", 0L),
                    Triple("preset-a", "Zebra", 1L),
                    Triple("preset-m", "Mango", 2L),
                ),
                v2.presetRows(),
            )
            assertEquals(
                listOf(
                    "preset-a|cool_down|300|0",
                    "preset-z|warm_up|180|0",
                    "preset-z|work|60|1",
                    "preset-z|recovery|120|2",
                ),
                v2.intervalRows(),
            )
        } finally {
            v2.close()
        }
    }

    /**
     * SCHEMA-045: the backfill numbers the rows 0 to n − 1 even when v1's `rowid`s have a gap
     * where a preset was deleted — the position is a row's rank, not its `rowid`.
     */
    @Test
    fun `migrating 1 to 2 numbers positions without gaps after a deleted preset`() {
        val v1 = migrationTestHelper.createDatabase(1)
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('first', 'First')")
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('deleted', 'Deleted')")
        v1.execSQL("INSERT INTO presets (id, name) VALUES ('third', 'Third')")
        v1.execSQL("DELETE FROM presets WHERE id = 'deleted'")
        v1.close()

        val v2 = migrationTestHelper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        try {
            assertEquals(
                listOf(Triple("first", "First", 0L), Triple("third", "Third", 1L)),
                v2.presetRows(),
            )
        } finally {
            v2.close()
        }
    }

    private fun SQLiteConnection.presetRows(): List<Triple<String, String, Long>> =
        prepare("SELECT id, name, position FROM presets ORDER BY position").use { statement ->
            buildList {
                while (statement.step()) {
                    add(Triple(statement.getText(0), statement.getText(1), statement.getLong(2)))
                }
            }
        }

    private fun SQLiteConnection.intervalRows(): List<String> =
        prepare(
            "SELECT presetId, kind, durationSeconds, position FROM intervals ORDER BY presetId, position",
        ).use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        listOf(
                            statement.getText(0),
                            statement.getText(1),
                            statement.getLong(2),
                            statement.getLong(3),
                        ).joinToString("|"),
                    )
                }
            }
        }
}
