package com.derekwinters.intervaltrainer

/** `SCREEN-023`: how many intervals before the current one the past list shows. */
private const val PAST_ROWS = 2

/** `SCREEN-023`: how many intervals after the current one the upcoming list shows. */
private const val UPCOMING_ROWS = 4

/** `SCREEN-025`: the current segment's alpha, and every other segment's. */
private const val CURRENT_SEGMENT_ALPHA = 1f
private const val OTHER_SEGMENT_ALPHA = 0.25f

/** `SCREEN-026`: from this many intervals on, the strip's gap narrows. */
private const val NARROW_GAP_FROM_INTERVALS = 40
private const val WIDE_GAP_DP = 3
private const val NARROW_GAP_DP = 1

/**
 * `docs/spec/screens.md` `SCREEN-023`: one row of the running screen's past or upcoming list — the
 * schedule entry at [index], its [distance] from the current interval, and the [alpha] and
 * [scale] it is drawn at.
 */
data class ScheduleRow(
    val index: Int,
    val interval: Interval,
    val distance: Int,
    val alpha: Float,
    val scale: Float,
)

/** `SCREEN-025`: one segment of the timeline strip — its interval's [kind], drawn at [alpha]. */
data class TimelineSegment(
    val kind: IntervalKind,
    val alpha: Float,
)

/**
 * `SCREEN-023`–`026`: what the running screen's schedule lists and timeline strip show, derived
 * from the schedule position alone — the same "a screen shows only what a `:core` reducer computed
 * for it" split [RunningScreenContent] gives the ring.
 *
 * [currentIndex] is the interval in progress, or during a lead-in the interval the lead-in counts
 * into (`SCREEN-024`). [past] holds up to two rows before it, oldest first; [upcoming] up to four
 * after it, next first; neither repeats the current interval. [strip] has one segment per schedule
 * entry, exactly one of them — the current one — at full alpha (the page's invariant), and
 * [stripGapDp] is the gap between segments in dp. It is a plain number so `:core` stays free of
 * Compose.
 */
data class RunningScreenSchedule(
    val currentIndex: Int,
    val past: List<ScheduleRow>,
    val upcoming: List<ScheduleRow>,
    val strip: List<TimelineSegment>,
    val stripGapDp: Int,
)

/** `SCREEN-023`: a row's alpha, `1 − 0.18 × distance`, clamped to 0.3–1.0. */
fun scheduleRowAlpha(distance: Int): Float = (1f - 0.18f * distance).coerceIn(0.3f, 1f)

/** `SCREEN-023`: a row's scale, `1 − 0.06 × distance`, clamped to 0.75–1.0. */
fun scheduleRowScale(distance: Int): Float = (1f - 0.06f * distance).coerceIn(0.75f, 1f)

/**
 * `SCREEN-023`–`026`: the running screen's schedule lists and strip for [this] [TimerState] —
 * `null` outside [TimerState.Running] and [TimerState.Paused]. Paused yields exactly what running
 * at the same position does (`SCREEN-028`).
 */
fun TimerState.runningScreenSchedule(): RunningScreenSchedule? {
    val (schedule, phase) = when (this) {
        is TimerState.Running -> schedule to phase
        is TimerState.Paused -> schedule to phase
        else -> return null
    }
    val current = when (phase) {
        is TimerPhase.LeadIn -> phase.index
        is TimerPhase.InInterval -> phase.index
    }

    fun row(index: Int): ScheduleRow {
        val distance = kotlin.math.abs(index - current)
        return ScheduleRow(
            index = index,
            interval = schedule[index].interval,
            distance = distance,
            alpha = scheduleRowAlpha(distance),
            scale = scheduleRowScale(distance),
        )
    }

    return RunningScreenSchedule(
        currentIndex = current,
        past = ((current - PAST_ROWS).coerceAtLeast(0) until current).map(::row),
        upcoming = (current + 1..(current + UPCOMING_ROWS).coerceAtMost(schedule.lastIndex)).map(::row),
        strip = schedule.mapIndexed { index, entry ->
            TimelineSegment(
                kind = entry.interval.kind,
                alpha = if (index == current) CURRENT_SEGMENT_ALPHA else OTHER_SEGMENT_ALPHA,
            )
        },
        stripGapDp = if (schedule.size >= NARROW_GAP_FROM_INTERVALS) NARROW_GAP_DP else WIDE_GAP_DP,
    )
}
