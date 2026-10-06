package com.derekwinters.intervaltrainer.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's row shape for `presets` (SCHEMA-010). `id` is a generated UUID string assigned at
 * creation (SCHEMA-011); this entity does not generate it — whatever constructs one is
 * responsible for that. There is no seeded-preset marker (SCHEMA-013).
 *
 * [position] is the preset's place in the home screen's list (SCHEMA-012), added at schema
 * version 2 (#163). Its `0` default is declared here as well as in [MIGRATION_1_2]'s
 * `ALTER TABLE`, so a database created fresh at version 2 and one migrated from version 1 have the
 * same column; nothing relies on the default, because every write in [PresetDao] states the
 * position explicitly (SCHEMA-015–017).
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val position: Int,
)
