package com.derekwinters.intervaltrainer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room's data-access surface for `presets` and `intervals`.
 *
 * Every function here is a plain, blocking call — never `suspend` — because `RoomPresetStore`
 * implements `:core`'s synchronous `PresetStore` interface (SCHEMA-004) directly on top of it.
 * This module stays on the Room 2.x Kotlin Multiplatform line rather than Room 3 specifically
 * because 2.x still allows this; see `database/build.gradle.kts` for the reasoning and its
 * consequences.
 */
@Dao
interface PresetDao {

    /**
     * SCHEMA-012: the preset list in insertion order — the table's own row order (`rowid`, which
     * a `TEXT` primary key does not disable) — never a stored `position` column, which `presets`
     * does not have.
     */
    @Query("SELECT * FROM presets ORDER BY rowid ASC")
    fun getPresets(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id")
    fun getPreset(id: String): PresetEntity?

    /** SCHEMA-025: a preset's intervals, in the explicit order they were authored. */
    @Query("SELECT * FROM intervals WHERE presetId = :presetId ORDER BY position ASC")
    fun getIntervals(presetId: String): List<IntervalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPreset(preset: PresetEntity)

    @Insert
    fun insertIntervals(intervals: List<IntervalEntity>)

    @Query("DELETE FROM intervals WHERE presetId = :presetId")
    fun deleteIntervals(presetId: String)

    /**
     * SCHEMA-014, SCHEMA-021: deletes only the `presets` row. Every `intervals` row referencing
     * it is removed by the foreign key's `ON DELETE CASCADE`, not by any explicit cleanup call
     * here — that directionality is exactly what `PresetDaoTest` asserts.
     */
    @Query("DELETE FROM presets WHERE id = :id")
    fun deletePreset(id: String)

    /**
     * Replaces a preset's row and its entire interval list in one transaction: [preset] is
     * upserted, every existing `intervals` row for its id is deleted, and [intervals] is inserted
     * in its place — so saving an edited preset (an interval added, removed or reordered) never
     * leaves a stale row behind, and never relies on the caller diffing the two lists itself.
     */
    @Transaction
    fun savePresetWithIntervals(preset: PresetEntity, intervals: List<IntervalEntity>) {
        upsertPreset(preset)
        deleteIntervals(preset.id)
        insertIntervals(intervals)
    }
}
