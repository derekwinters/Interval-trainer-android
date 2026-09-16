package com.derekwinters.intervaltrainer

/** The lead-in's length, in milliseconds (`TIMER-030`). */
private const val LEAD_IN_MILLIS = 3_000L

private fun Interval.durationMillis(): Long = durationSeconds * 1_000L

/**
 * Applies [event] to [state] and returns the resulting state (`TIMER-010`–`018`). [clock] is read
 * at most once, so every requirement below sees a single, consistent "now".
 *
 * An event with no cell in `TIMER-012`'s table leaves [state] unchanged and is handled by each
 * `apply*` function below falling through to its `else -> state` branch — stated once here rather
 * than as a comment on every branch.
 */
fun reduce(state: TimerState, event: TimerEvent, clock: Clock): TimerState {
    val now = clock.nowMillis()
    return when (event) {
        is TimerEvent.Start -> applyStart(state, event.schedule, now)
        is TimerEvent.Tick -> applyTick(state, now)
        is TimerEvent.Pause -> applyPause(state, now)
        is TimerEvent.Resume -> applyResume(state, now)
        is TimerEvent.Skip -> applySkip(state, now)
        is TimerEvent.Stop -> applyStop(state, now)
    }
}

/** TIMER-013: start is accepted only from idle, and only with a non-empty schedule. */
private fun applyStart(state: TimerState, schedule: List<Interval>, now: Long): TimerState {
    if (state !is TimerState.Idle || schedule.isEmpty()) return state
    return TimerState.Running(
        schedule = schedule.map { ScheduleEntry(it) },
        phase = TimerPhase.LeadIn(index = 0),
        deadlineMillis = now + LEAD_IN_MILLIS,
        runningSinceMillis = now,
        accumulatedRunningMillis = 0L,
    )
}

/**
 * TIMER-020–022, TIMER-031: advances the clock. A deadline reached ends the lead-in and begins
 * its interval, or finishes an interval and begins the next one directly — never another
 * lead-in, which is entered only by start and skip (TIMER-031). The loop lets one very late tick
 * cross more than one deadline (e.g. after the app was suspended), each new deadline computed by
 * adding to the one before it rather than to `now`, so no overshoot is carried forward
 * (TIMER-022).
 */
private fun applyTick(state: TimerState, now: Long): TimerState {
    if (state !is TimerState.Running) return state
    var schedule = state.schedule
    var phase = state.phase
    var deadline = state.deadlineMillis

    while (now >= deadline) {
        when (val currentPhase = phase) {
            is TimerPhase.LeadIn -> {
                deadline += schedule[currentPhase.index].interval.durationMillis()
                phase = TimerPhase.InInterval(currentPhase.index)
            }

            is TimerPhase.InInterval -> {
                schedule = schedule.finishing(currentPhase.index)
                val nextIndex = currentPhase.index + 1
                if (nextIndex >= schedule.size) {
                    // TIMER-015: the last interval reaching its deadline ends the workout,
                    // timestamped at the deadline itself rather than at a late `now` (TIMER-054).
                    return TimerState.Ended(
                        schedule = schedule,
                        outcome = WorkoutOutcome.COMPLETED,
                        totalTimeMillis = state.accumulatedRunningMillis +
                            (deadline - state.runningSinceMillis),
                    )
                }
                deadline += schedule[nextIndex].interval.durationMillis()
                phase = TimerPhase.InInterval(nextIndex)
            }
        }
    }

    return state.copy(schedule = schedule, phase = phase, deadlineMillis = deadline)
}

/** TIMER-023: pause holds deadline-minus-now, and freezes the running total. */
private fun applyPause(state: TimerState, now: Long): TimerState {
    if (state !is TimerState.Running) return state
    return TimerState.Paused(
        schedule = state.schedule,
        phase = state.phase,
        remainingMillis = state.deadlineMillis - now,
        accumulatedRunningMillis = state.accumulatedRunningMillis + (now - state.runningSinceMillis),
    )
}

/** TIMER-024: resume sets a new deadline from the held remaining time; no time passes while paused. */
private fun applyResume(state: TimerState, now: Long): TimerState {
    if (state !is TimerState.Paused) return state
    return TimerState.Running(
        schedule = state.schedule,
        phase = state.phase,
        deadlineMillis = now + state.remainingMillis,
        runningSinceMillis = now,
        accumulatedRunningMillis = state.accumulatedRunningMillis,
    )
}

/**
 * TIMER-040–043, TIMER-045: abandons whatever the timer is currently counting down to — an
 * interval, or the interval a lead-in is counting into — marks it skipped, and starts a fresh
 * three-second lead-in for the one after it (TIMER-041), fresh from `now` rather than continuing
 * the abandoned countdown. Skipping (or skip-lead-in-ing past) the last interval ends the workout
 * completed (TIMER-043), the same as running out the last interval's own deadline would.
 */
private fun applySkip(state: TimerState, now: Long): TimerState {
    val (schedule, phase, accumulatedRunningMillis) = when (state) {
        is TimerState.Running -> Triple(
            state.schedule,
            state.phase,
            state.accumulatedRunningMillis + (now - state.runningSinceMillis),
        )

        is TimerState.Paused -> Triple(state.schedule, state.phase, state.accumulatedRunningMillis)
        else -> return state
    }

    val skippedIndex = when (phase) {
        is TimerPhase.LeadIn -> phase.index
        is TimerPhase.InInterval -> phase.index
    }
    val updatedSchedule = schedule.skipping(skippedIndex)
    val nextIndex = skippedIndex + 1
    if (nextIndex >= updatedSchedule.size) {
        return TimerState.Ended(
            schedule = updatedSchedule,
            outcome = WorkoutOutcome.COMPLETED,
            totalTimeMillis = accumulatedRunningMillis,
        )
    }
    return TimerState.Running(
        schedule = updatedSchedule,
        phase = TimerPhase.LeadIn(nextIndex),
        deadlineMillis = now + LEAD_IN_MILLIS,
        runningSinceMillis = now,
        accumulatedRunningMillis = accumulatedRunningMillis,
    )
}

/** TIMER-016, TIMER-050, TIMER-054: stop ends the workout stopped-early, from running or paused. */
private fun applyStop(state: TimerState, now: Long): TimerState = when (state) {
    is TimerState.Running -> TimerState.Ended(
        schedule = state.schedule,
        outcome = WorkoutOutcome.STOPPED_EARLY,
        totalTimeMillis = state.accumulatedRunningMillis + (now - state.runningSinceMillis),
    )

    is TimerState.Paused -> TimerState.Ended(
        schedule = state.schedule,
        outcome = WorkoutOutcome.STOPPED_EARLY,
        totalTimeMillis = state.accumulatedRunningMillis,
    )

    else -> state
}

private fun List<ScheduleEntry>.finishing(index: Int): List<ScheduleEntry> =
    withOutcome(index, IntervalOutcome.FINISHED)

private fun List<ScheduleEntry>.skipping(index: Int): List<ScheduleEntry> =
    withOutcome(index, IntervalOutcome.SKIPPED)

private fun List<ScheduleEntry>.withOutcome(index: Int, outcome: IntervalOutcome): List<ScheduleEntry> =
    mapIndexed { i, entry -> if (i == index) entry.copy(outcome = outcome) else entry }

/**
 * TIMER-020: the current phase's remaining time, deadline minus now, computed fresh every call —
 * never accumulated. Zero for [TimerState.Idle] and [TimerState.Ended], which have no deadline
 * and no held remaining.
 */
fun TimerState.remainingMillis(clock: Clock): Long = when (this) {
    is TimerState.Running -> deadlineMillis - clock.nowMillis()
    is TimerState.Paused -> remainingMillis
    else -> 0L
}

/**
 * TIMER-025, TIMER-034: the current phase's remaining time plus the full duration of every
 * schedule interval still ahead of it — the upcoming interval counted in full while its lead-in
 * runs, per TIMER-034. Zero for [TimerState.Idle] and [TimerState.Ended].
 */
fun TimerState.totalRemainingMillis(clock: Clock): Long {
    val (schedule, phase, remaining) = when (this) {
        is TimerState.Running -> Triple(schedule, phase, deadlineMillis - clock.nowMillis())
        is TimerState.Paused -> Triple(schedule, phase, remainingMillis)
        else -> return 0L
    }
    val upcomingIndex = when (phase) {
        is TimerPhase.LeadIn -> phase.index
        is TimerPhase.InInterval -> phase.index + 1
    }
    val laterIntervalsMillis = schedule.drop(upcomingIndex).sumOf { it.interval.durationMillis() }
    return remaining + laterIntervalsMillis
}

/**
 * TIMER-060–061: rounds completed against rounds planned, reported as a pair — "3 of 5". A round
 * is a work interval (TIMER-060); one that was skipped does not count towards it, however far
 * skip has since moved the schedule past it (TIMER-061).
 */
fun List<ScheduleEntry>.roundsCompleted(): Pair<Int, Int> {
    val workEntries = filter { it.interval.kind == IntervalKind.WORK }
    val planned = workEntries.size
    val completed = workEntries.count { it.outcome == IntervalOutcome.FINISHED }
    return completed to planned
}

/**
 * `docs/spec/screens.md` `SCREEN-022a`: the round **currently in progress** against rounds
 * planned, reported the same shape as [roundsCompleted] — a pair — but counting *position*
 * rather than completion. `docs/spec/timer.md` §7 explicitly leaves "a better rendering of a
 * non-uniform workout" to the running screen's own issue; this is that issue's resolution.
 *
 * The round in progress is the **ordinal, among the schedule's work intervals, of the last one
 * reached at or before [index]** — the schedule position the timer is currently either working
 * through or resting after, not yet the next one to come. A work interval counts the moment its
 * lead-in begins (`index` points at it during [TimerPhase.LeadIn]), and stays the round in
 * progress through whatever non-work interval follows it, until the next work interval's own
 * lead-in begins. It counts a work interval reached this way regardless of whether it was
 * finished or skipped (`TIMER-061`'s FINISHED-only rule is for rounds *completed*, a different
 * question this function does not answer) — skip still moves the schedule position forward, and
 * "in progress" tracks position. Zero before the first work interval is reached, e.g. during a
 * leading warm-up.
 */
fun List<ScheduleEntry>.currentRound(index: Int): Pair<Int, Int> {
    val planned = count { it.interval.kind == IntervalKind.WORK }
    val inProgress = take(index + 1).count { it.interval.kind == IntervalKind.WORK }
    return inProgress to planned
}
