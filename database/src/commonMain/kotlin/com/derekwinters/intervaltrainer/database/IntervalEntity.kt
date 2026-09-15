package com.derekwinters.intervaltrainer.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's row shape for `intervals` (SCHEMA-020): one row per interval in a preset's ordered list.
 *
 * [id] is Room's default auto-incrementing `Long`, not a UUID (SCHEMA-022) — nothing outside this
 * table ever refers to one interval row by its own identity, unlike a preset's [PresetEntity.id].
 *
 * [presetId]'s foreign key is declared `ON DELETE CASCADE` (SCHEMA-021), so deleting the parent
 * `presets` row removes every `intervals` row that references it (SCHEMA-014) at the database
 * level — nothing in this module's own code has to remember to clean these up.
 *
 * [kind] is `TEXT`, one of `warm_up`, `work`, `recovery`, `cool_down` (SCHEMA-023) — never an
 * ordinal. `:core`'s `IntervalKind` enum is mapped to and from this column elsewhere in this
 * module (`IntervalKindColumn.kt`), so renaming or reordering that enum's constants cannot change
 * what is already on disk.
 *
 * [position] is the interval's explicit place in its preset's list (SCHEMA-025), authored data
 * rather than anything inferred; the index on `(presetId, position)` (SCHEMA-027) is what
 * `ORDER BY position` in [PresetDao.getIntervals] reads without a table scan.
 */
@Entity(
    tableName = "intervals",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "position"])],
)
data class IntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: String,
    val kind: String,
    val durationSeconds: Int,
    val position: Int,
)
