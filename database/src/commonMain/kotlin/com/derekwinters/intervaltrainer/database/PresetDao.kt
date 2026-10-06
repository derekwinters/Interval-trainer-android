package com.derekwinters.intervaltrainer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room's data-access surface for `presets` and `intervals`.
 *
 * Every function here is `suspend` (SCHEMA-004): Room's Kotlin Multiplatform compiler accepts only
 * `suspend` DAO functions in a source set targeting a non-Android platform, and this module's
 * `jvm()` target is one. `RoomPresetStore` implements `:core`'s `suspend` `PresetStore` directly
 * on top of it, with no `runBlocking` bridging anywhere.
 */
@Dao
interface PresetDao {

    /**
     * SCHEMA-012: the preset list in insertion order — the table's own row order (`rowid`, which
     * a `TEXT` primary key does not disable) — never a stored `position` column, which `presets`
     * does not have.
     */
    @Query("SELECT * FROM presets ORDER BY rowid ASC")
    suspend fun getPresets(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPreset(id: String): PresetEntity?

    /** SCHEMA-025: a preset's intervals, in the explicit order they were authored. */
    @Query("SELECT * FROM intervals WHERE presetId = :presetId ORDER BY position ASC")
    suspend fun getIntervals(presetId: String): List<IntervalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: PresetEntity)

    @Insert
    suspend fun insertIntervals(intervals: List<IntervalEntity>)

    @Query("DELETE FROM intervals WHERE presetId = :presetId")
    suspend fun deleteIntervals(presetId: String)

    /**
     * SCHEMA-014, SCHEMA-021: deletes only the `presets` row. Every `intervals` row referencing
     * it is removed by the foreign key's `ON DELETE CASCADE`, not by any explicit cleanup call
     * here — that directionality is exactly what `PresetDaoTest` asserts.
     */
    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePreset(id: String)

    /**
     * Replaces a preset's row and its entire interval list in one transaction: [preset] is
     * upserted, every existing `intervals` row for its id is deleted, and [intervals] is inserted
     * in its place — so saving an edited preset (an interval added, removed or reordered) never
     * leaves a stale row behind, and never relies on the caller diffing the two lists itself.
     */
    @Transaction
    suspend fun savePresetWithIntervals(preset: PresetEntity, intervals: List<IntervalEntity>) {
        upsertPreset(preset)
        deleteIntervals(preset.id)
        insertIntervals(intervals)
    }
}
