package com.derekwinters.intervaltrainer

/**
 * Produces a workout's schedule: the workout's own copy of [preset]'s ordered interval list,
 * taken once, at workout start (`TIMER-002`, `SVC-001`).
 *
 * This is a **copy**, not an expansion (`docs/spec/service.md` §1): [preset] is already a flat,
 * ordered list (`TIMER-001`), so there is nothing to unroll and no template to fill in. It takes
 * no round-count or generator parameter, because none is stored on a preset to give it
 * (`TIMER-004`). It is a distinct function from the generator (`TIMER-080`–`085`, `SVC-002`),
 * which expands a round count into rows at authoring time, inside the preset editor, before a
 * preset is ever saved; this function runs once, later, over whatever rows the preset holds by
 * then, indistinguishably.
 *
 * The returned list is a fresh copy, not [preset]'s own list instance: editing or deleting the
 * preset afterwards must leave an already-started workout's schedule untouched (`TIMER-003`),
 * which a shared reference would not survive.
 */
fun scheduleFrom(preset: Preset): List<Interval> = preset.intervals.toList()
