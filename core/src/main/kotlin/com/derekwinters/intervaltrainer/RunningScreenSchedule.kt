package com.derekwinters.intervaltrainer

/** `docs/spec/screens.md` `SCREEN-023`: one row of the past or upcoming list. */
data class ScheduleRow(
    val index: Int,
    val interval: Interval,
    val distance: Int,
    val alpha: Float,
    val scale: Float,
)

/** `SCREEN-025`: one segment of the timeline strip. */
data class TimelineSegment(
    val kind: IntervalKind,
    val alpha: Float,
)

/** `SCREEN-023`–`026`: the running screen's schedule lists and timeline strip. */
data class RunningScreenSchedule(
    val currentIndex: Int,
    val past: List<ScheduleRow>,
    val upcoming: List<ScheduleRow>,
    val strip: List<TimelineSegment>,
    val stripGapDp: Int,
)

// Not implemented yet (#151): the tests in RunningScreenScheduleTest.kt are written first.
fun scheduleRowAlpha(distance: Int): Float = Float.NaN

fun scheduleRowScale(distance: Int): Float = Float.NaN

fun TimerState.runningScreenSchedule(): RunningScreenSchedule? = null
