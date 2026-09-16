package com.derekwinters.intervaltrainer.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's row shape for `presets` (SCHEMA-010). `id` is a generated UUID string assigned at
 * creation (SCHEMA-011); this entity does not generate it — whatever constructs one is
 * responsible for that. There is no `position` column: the preset list's order is insertion order
 * (SCHEMA-012), and there is no seeded-preset marker (SCHEMA-013). Nothing else is stored about a
 * preset.
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
)
