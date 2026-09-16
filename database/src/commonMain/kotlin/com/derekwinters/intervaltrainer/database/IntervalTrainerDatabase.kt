package com.derekwinters.intervaltrainer.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * SCHEMA-002: the one `@Database` class, at schema version 1, declaring the `presets` and
 * `intervals` entities below.
 *
 * `exportSchema` is left at Room's own default of `true` (SCHEMA-003): the schema JSON Room
 * generates for this version belongs at `database/schemas/`, committed and never shipped in the
 * APK.
 *
 * SCHEMA-030: whoever builds this database seeds the two endurance presets by adding
 * [SeedDataCallback] to `Room.databaseBuilder<IntervalTrainerDatabase>(...)`. Nothing here does
 * that on its own — a builder call with no `addCallback` gets an empty database.
 */
@Database(
    entities = [PresetEntity::class, IntervalEntity::class],
    version = 1,
)
abstract class IntervalTrainerDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
}
