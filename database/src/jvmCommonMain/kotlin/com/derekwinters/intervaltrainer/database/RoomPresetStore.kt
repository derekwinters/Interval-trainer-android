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
 */
class RoomPresetStore(private val dao: PresetDao) : PresetStore {

    override fun presets(): List<Preset> =
        dao.getPresets().map { it.toPreset(dao.getIntervals(it.id)) }

    override fun preset(id: String): Preset? =
        dao.getPreset(id)?.let { it.toPreset(dao.getIntervals(id)) }

    override fun save(preset: Preset) {
        val intervals = preset.intervals.mapIndexed { index, interval ->
            IntervalEntity(
                presetId = preset.id,
                kind = interval.kind.toColumnValue(),
                durationSeconds = interval.durationSeconds,
                position = index,
            )
        }
        dao.savePresetWithIntervals(PresetEntity(id = preset.id, name = preset.name), intervals)
    }

    override fun delete(id: String) {
        dao.deletePreset(id)
    }
}

private fun PresetEntity.toPreset(intervals: List<IntervalEntity>): Preset =
    Preset(
        id = id,
        name = name,
        intervals = intervals.map { Interval(it.kind.toIntervalKind(), it.durationSeconds) },
    )
