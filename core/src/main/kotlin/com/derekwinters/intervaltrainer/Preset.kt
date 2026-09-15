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
