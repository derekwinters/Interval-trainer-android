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
     * SCHEMA-012: the preset list in stored order — ascending `position`, never `rowid`, which
     * would quietly undo every reorder (schema.md's invariant on this).
     */
    @Query("SELECT * FROM presets ORDER BY position ASC")
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

    @Query("SELECT position FROM presets WHERE id = :id")
    suspend fun getPosition(id: String): Int?

    /** SCHEMA-016: one past the largest stored position, or `0` in an empty table. */
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM presets")
    suspend fun getNextPosition(): Int

    @Query("UPDATE presets SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE presets SET position = position + 1 WHERE position >= :fromPosition")
    suspend fun shiftPositionsDown(fromPosition: Int)

    /**
     * Saves a preset's row and replaces its entire interval list in one transaction: the row is
     * upserted, every existing `intervals` row for its id is deleted, and [intervals] is inserted
     * in its place — so saving an edited preset (an interval added, removed or reordered) never
     * leaves a stale row behind, and never relies on the caller diffing the two lists itself.
     *
     * SCHEMA-016: an existing preset keeps its `position`, so editing never moves it; a new one is
     * appended at the end.
     */
    @Transaction
    suspend fun savePreset(id: String, name: String, intervals: List<IntervalEntity>) {
        val position = getPosition(id) ?: getNextPosition()
        upsertPreset(PresetEntity(id = id, name = name, position = position))
        deleteIntervals(id)
        insertIntervals(intervals)
    }

    /**
     * SCHEMA-017: saves a new preset directly after [afterId] — every preset after it moves down
     * one place and the new one takes the place they vacated — or at the end if [afterId] no
     * longer exists. One transaction, so the list never reads with two presets at one position.
     */
    @Transaction
    suspend fun insertPresetAfter(afterId: String, id: String, name: String, intervals: List<IntervalEntity>) {
        val afterPosition = getPosition(afterId)
        val position = if (afterPosition == null) {
            getNextPosition()
        } else {
            shiftPositionsDown(fromPosition = afterPosition + 1)
            afterPosition + 1
        }
        upsertPreset(PresetEntity(id = id, name = name, position = position))
        deleteIntervals(id)
        insertIntervals(intervals)
    }

    /**
     * SCHEMA-015: stores [orderedIds] as the list's order, `0, 1, 2, …`, in one transaction. Only
     * `position` changes — no name and no `intervals` row (schema.md's "move rows, never rewrite
     * them" invariant), which is why this is an `UPDATE` per row rather than a re-save.
     */
    @Transaction
    suspend fun reorderPresets(orderedIds: List<String>) {
        orderedIds.forEachIndexed { position, id -> setPosition(id, position) }
    }
}
