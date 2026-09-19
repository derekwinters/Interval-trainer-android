package com.derekwinters.intervaltrainer

/**
 * `docs/spec/screens.md` `SCREEN-020`–`022`, `SCREEN-022a`, `SCREEN-024`: what the running
 * screen's ring and round indicator show, derived from a [TimerState] and [Clock] alone — the
 * same "a screen shows only what a `:core` reducer computed for it" split this repository's own
 * invariant names, and the same shape [WorkoutNotificationContent] (`SVC-025`) already gives the
 * notification. `null` for [TimerState.Idle] and [TimerState.Ended] — there is nothing running to
 * show.
 *
 * [GetReady] and [Active] are two different shapes, not one shape with optional fields, because
 * `SCREEN-024` requires the lead-in to read as a distinct state rather than a partially-formed
 * interval display: [GetReady] carries no "full duration to show progress against" — only the
 * upcoming interval's own kind and duration, which the screen names in a caption ("Starting —
 * Work, 0:45" in the settled design), never as a ring in progress.
 *
 * Nothing here is a display string, the same split [WorkoutNotificationContent] draws: turning
 * [IntervalKind] into a localised label and a millisecond count into `formatSeconds`' `m:ss` is
 * `:app`'s job.
 */
sealed interface RunningScreenContent {
    /** `SCREEN-022`, `SCREEN-022a`: the round currently in progress, and rounds planned. */
    val roundInProgress: Int
    val roundsPlanned: Int

    /** `SCREEN-021`: the total remaining time across the whole workout (`TIMER-025`). */
    val totalRemainingMillis: Long

    /**
     * `SCREEN-024`: during the lead-in (`TIMER-030`–`036`), a distinct "get ready" state.
     * [remainingMillis] is the lead-in's own countdown; [upcomingKind] and
     * [upcomingDurationMillis] name what it is counting down into.
     */
    data class GetReady(
        val remainingMillis: Long,
        val upcomingKind: IntervalKind,
        val upcomingDurationMillis: Long,
        override val roundInProgress: Int,
        override val roundsPlanned: Int,
        override val totalRemainingMillis: Long,
    ) : RunningScreenContent

    /**
     * `SCREEN-020`: the ring's own content while actually inside a schedule interval — its
     * [kind], and [remainingMillis] against [fullDurationMillis] ("0:32 of 0:45").
     */
    data class Active(
        val kind: IntervalKind,
        val remainingMillis: Long,
        val fullDurationMillis: Long,
        override val roundInProgress: Int,
        override val roundsPlanned: Int,
        override val totalRemainingMillis: Long,
    ) : RunningScreenContent
}

/**
 * `SCREEN-020`–`022`, `SCREEN-022a`, `SCREEN-024`: the running screen's content, for [this]
 * [TimerState] read against [clock] — `null` outside [TimerState.Running] and
 * [TimerState.Paused].
 */
fun TimerState.runningScreenContent(clock: Clock): RunningScreenContent? {
    val schedule = when (this) {
        is TimerState.Running -> schedule
        is TimerState.Paused -> schedule
        else -> return null
    }
    val phase = when (this) {
        is TimerState.Running -> phase
        is TimerState.Paused -> phase
        else -> return null
    }
    val index = when (phase) {
        is TimerPhase.LeadIn -> phase.index
        is TimerPhase.InInterval -> phase.index
    }
    val interval = schedule[index].interval
    val (roundInProgress, roundsPlanned) = schedule.currentRound(index)
    val remaining = remainingMillis(clock)
    val total = totalRemainingMillis(clock)

    return when (phase) {
        is TimerPhase.LeadIn -> RunningScreenContent.GetReady(
            remainingMillis = remaining,
            upcomingKind = interval.kind,
            upcomingDurationMillis = interval.durationSeconds * 1_000L,
            roundInProgress = roundInProgress,
            roundsPlanned = roundsPlanned,
            totalRemainingMillis = total,
        )

        is TimerPhase.InInterval -> RunningScreenContent.Active(
            kind = interval.kind,
            remainingMillis = remaining,
            fullDurationMillis = interval.durationSeconds * 1_000L,
            roundInProgress = roundInProgress,
            roundsPlanned = roundsPlanned,
            totalRemainingMillis = total,
        )
    }
}
