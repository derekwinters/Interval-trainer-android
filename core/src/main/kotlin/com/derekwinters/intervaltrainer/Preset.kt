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
