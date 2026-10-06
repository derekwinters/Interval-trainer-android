package com.derekwinters.intervaltrainer.database

import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.PresetStore

/**
 * `:core`'s [PresetStore] (SCHEMA-004), backed by [PresetDao]. `:core` depends on nothing here —
 * only on the interface this implements — so this is the one place a [Preset] or [Interval] is
 * translated to and from the Room row shapes in this module.
 *
 * A preset's intervals are always read and written together with it, ordered by
 * [IntervalEntity.position] on the way out and re-assigned from [Preset.intervals]' own list
 * order on the way in (SCHEMA-025) — nothing about that ordering is `:core`'s concern.
 *
 * Every operation is `suspend`, like the [PresetDao] calls it makes (SCHEMA-004); none of them is
 * bridged with `runBlocking`.
 */
class RoomPresetStore(private val dao: PresetDao) : PresetStore {

    override suspend fun presets(): List<Preset> =
        dao.getPresets().map { it.toPreset(dao.getIntervals(it.id)) }

    override suspend fun preset(id: String): Preset? =
        dao.getPreset(id)?.let { it.toPreset(dao.getIntervals(id)) }

    /** SCHEMA-016: an existing preset keeps its place in the list; a new one is appended. */
    override suspend fun save(preset: Preset) {
        dao.savePreset(id = preset.id, name = preset.name, intervals = preset.intervalEntities())
    }

    override suspend fun delete(id: String) {
        dao.deletePreset(id)
    }

    /** SCHEMA-015. */
    override suspend fun reorder(orderedIds: List<String>) {
        dao.reorderPresets(orderedIds)
    }

    /** SCHEMA-017: [preset]'s intervals become new rows of its own, keyed by its own id. */
    override suspend fun saveAfter(preset: Preset, afterId: String) {
        dao.insertPresetAfter(
            afterId = afterId,
            id = preset.id,
            name = preset.name,
            intervals = preset.intervalEntities(),
        )
    }
}

private fun Preset.intervalEntities(): List<IntervalEntity> =
    intervals.mapIndexed { index, interval ->
        IntervalEntity(
            presetId = id,
            kind = interval.kind.toColumnValue(),
            durationSeconds = interval.durationSeconds,
            position = index,
        )
    }

private fun PresetEntity.toPreset(intervals: List<IntervalEntity>): Preset =
    Preset(
        id = id,
        name = name,
        intervals = intervals.map { Interval(it.kind.toIntervalKind(), it.durationSeconds) },
    )
