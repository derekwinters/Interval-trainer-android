package com.derekwinters.intervaltrainer

/**
 * A saved, named workout definition: an ordered list of intervals, authored one by one
 * (`CONTEXT.md` glossary).
 *
 * [intervals]' list order is the order they run (`TIMER-001`); nothing in it is a template and
 * nothing is expanded when a workout starts. A preset stores rows and nothing else — no round
 * count, no generated group, no reference to whatever produced a row (`TIMER-004`).
 */
data class Preset(
    val id: String,
    val name: String,
    val intervals: List<Interval>,
)

/**
 * `docs/spec/screens.md` `SCREEN-019`: whether the preset editor may save this preset — only while
 * it holds at least one interval, since `TIMER-013` refuses to start a workout from one that holds
 * none (#147). The editor calls this on the preset as currently edited, never on the one it opened
 * with.
 */
fun Preset.isSavable(): Boolean = intervals.isNotEmpty()

/**
 * `docs/spec/screens.md` `SCREEN-019b` (#163): the name a duplicate of a preset called [name] gets
 * — "<name> (copy)", or, if that is in [takenNames], the first of "<name> (copy 2)",
 * "<name> (copy 3)", … that is not. A gap in the numbering is filled before counting past it.
 */
fun copyName(name: String, takenNames: Collection<String>): String {
    val taken = takenNames.toSet()
    val plain = "$name (copy)"
    if (plain !in taken) return plain
    return generateSequence(2) { it + 1 }
        .map { "$name (copy $it)" }
        .first { it !in taken }
}

/**
 * `SCREEN-019b` (#163): a duplicate of this preset as it stands — the editor calls it on the preset
 * as currently edited, so unsaved changes come with it — under [newId], named by [copyName] against
 * [takenNames]. The intervals are values, so the copy shares nothing with this preset once saved
 * (`docs/spec/schema.md` `SCHEMA-017`).
 */
fun Preset.duplicate(newId: String, takenNames: Collection<String>): Preset =
    Preset(id = newId, name = copyName(name, takenNames), intervals = intervals)
