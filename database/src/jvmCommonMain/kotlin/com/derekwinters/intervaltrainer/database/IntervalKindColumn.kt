package com.derekwinters.intervaltrainer.database

import com.derekwinters.intervaltrainer.IntervalKind

/**
 * SCHEMA-023: `intervals.kind` is stored as one of these four stable strings, not
 * [IntervalKind]'s own enum name and not an ordinal — so renaming or reordering that enum's
 * constants in code cannot change what is already on disk. Both directions are an exhaustive
 * `when`, so a fifth kind added to the enum with no matching case here is a compile error, not a
 * runtime surprise.
 */
internal fun IntervalKind.toColumnValue(): String = when (this) {
    IntervalKind.WARM_UP -> "warm_up"
    IntervalKind.WORK -> "work"
    IntervalKind.RECOVERY -> "recovery"
    IntervalKind.COOL_DOWN -> "cool_down"
}

internal fun String.toIntervalKind(): IntervalKind = when (this) {
    "warm_up" -> IntervalKind.WARM_UP
    "work" -> IntervalKind.WORK
    "recovery" -> IntervalKind.RECOVERY
    "cool_down" -> IntervalKind.COOL_DOWN
    else -> error("Unknown intervals.kind value: $this")
}
