package com.derekwinters.intervaltrainer.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * SCHEMA-045 (#163): schema version 1 to 2 — adds `presets.position` and backfills it from
 * version 1's insertion order, so the list reads in the same order after the upgrade as before it.
 *
 * Each row's position is the number of rows with a smaller `rowid`: its rank in insertion order,
 * numbered `0` to `n − 1` with no gap where a preset was deleted. Nothing else is touched — no
 * `name`, no `intervals` row. `SchemaMigrationTest` is the upgrade-path test SCHEMA-041 requires of
 * this change.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `presets` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "UPDATE `presets` SET `position` = " +
                "(SELECT COUNT(*) FROM `presets` AS `earlier` WHERE `earlier`.`rowid` < `presets`.`rowid`)",
        )
    }
}

/** Every migration, in order — what a builder of [IntervalTrainerDatabase] registers. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
