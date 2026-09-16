package com.derekwinters.intervaltrainer

/**
 * The home screen's per-row summary values for a preset (`docs/spec/screens.md` `SCREEN-003`):
 * rounds planned (`TIMER-060`) and the schedule's total duration in seconds. Computed once here so
 * home, and any other screen that ever needs the same two numbers, read one answer rather than two
 * that could drift apart (`docs/spec/screens.md`'s third invariant, per ADR 0005's pure-`:core`
 * split).
 */
data class PresetSummary(
    val roundCount: Int,
    val totalDurationSeconds: Int,
)

/**
 * Computes [PresetSummary] for [preset]'s own schedule ([scheduleFrom]): a round is a `WORK`
 * interval (`TIMER-060`, mirroring [List.roundsCompleted]'s own definition for a workout already in
 * progress), and the total is the sum of every interval's duration, `WORK` included. An empty
 * schedule summarises to zero rounds and a zero total, not an error.
 */
fun presetSummary(preset: Preset): PresetSummary {
    val schedule = scheduleFrom(preset)
    return PresetSummary(
        roundCount = schedule.count { it.kind == IntervalKind.WORK },
        totalDurationSeconds = schedule.sumOf { it.durationSeconds },
    )
}

/**
 * One segment of the home row's colour strip (`SCREEN-004`): the colour role for one interval, and
 * the fraction (`0.0`–`1.0`) of the schedule's total duration it occupies.
 */
data class ScheduleSegment(
    val colorRole: ColorRole,
    val fraction: Double,
)

/**
 * The home row's colour-strip segments for [preset] (`SCREEN-004`): one segment per interval, in
 * schedule order — the same order [scheduleFrom] returns, so the strip reads left-to-right the same
 * way the workout runs — sized by its share of the total duration, coloured by
 * [IntervalKind.colorRole] (`CUE-030`), the same mapping [boundaryCue] already uses rather than a
 * second copy of it.
 *
 * An empty schedule, or one whose total duration is zero (every interval authored at zero
 * seconds), yields an empty list rather than dividing by zero: there is nothing to draw a strip
 * for either way.
 */
fun scheduleSegments(preset: Preset): List<ScheduleSegment> {
    val schedule = scheduleFrom(preset)
    val totalDurationSeconds = schedule.sumOf { it.durationSeconds }
    if (totalDurationSeconds <= 0) return emptyList()
    return schedule.map { interval ->
        ScheduleSegment(
            colorRole = interval.kind.colorRole(),
            fraction = interval.durationSeconds.toDouble() / totalDurationSeconds,
        )
    }
}
