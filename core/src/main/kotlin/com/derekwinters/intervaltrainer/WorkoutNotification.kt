package com.derekwinters.intervaltrainer

/**
 * `SVC-025`: what the notification shows (`docs/spec/service.md` `SVC-020`) — the current
 * interval's kind and how much of it is left, in milliseconds. Nothing here is a display string:
 * turning [intervalKind] into a localised label and [remainingMillis] into `formatSeconds`'
 * `m:ss` is `:app`'s job, the same split `ADR 0005` draws for every other reducer.
 */
data class WorkoutNotificationContent(
    val intervalKind: IntervalKind,
    val remainingMillis: Long,
)

/**
 * `SVC-025`: the notification's content, derived from this [TimerState] and [clock] alone — `null`
 * when there is nothing running to show ([TimerState.Idle] or [TimerState.Ended]).
 *
 * During the lead-in, "current interval" is the interval the lead-in counts down into, and the
 * remaining time is the lead-in's own countdown, not that interval's full duration — the same
 * phase and the same [remainingMillis] a screen showing the lead-in would read (`TIMER-030`–`036`).
 */
fun TimerState.notificationContent(clock: Clock): WorkoutNotificationContent? {
    val (schedule, phase) = when (this) {
        is TimerState.Running -> schedule to phase
        is TimerState.Paused -> schedule to phase
        else -> return null
    }
    val index = when (phase) {
        is TimerPhase.LeadIn -> phase.index
        is TimerPhase.InInterval -> phase.index
    }
    return WorkoutNotificationContent(
        intervalKind = schedule[index].interval.kind,
        remainingMillis = remainingMillis(clock),
    )
}

/**
 * `SVC-026`: the one event a pause/resume toggle sends for this [TimerState] — [TimerEvent.Pause]
 * from running, [TimerEvent.Resume] from paused, `null` otherwise (no toggle applies to idle or
 * ended). The notification's action and the running screen's own toggle control both call this
 * function rather than each deciding independently, which is what makes `SVC-023` true by
 * construction.
 */
fun TimerState.toggleEvent(): TimerEvent? = when (this) {
    is TimerState.Running -> TimerEvent.Pause
    is TimerState.Paused -> TimerEvent.Resume
    else -> null
}
