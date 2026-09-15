package com.derekwinters.intervaltrainer

/**
 * How a workout ended (`TIMER-011`). There is no ended state without one of these two, and there
 * is no third outcome.
 */
enum class WorkoutOutcome {
    COMPLETED,
    STOPPED_EARLY,
}

/**
 * Whether the timer has moved past a schedule interval, and if so how (`TIMER-005`). An interval
 * the timer has not yet reached is [PENDING]. Rounds completed (`TIMER-061`) reads this directly
 * rather than inferring it from where the schedule pointer ended up, because skip advances the
 * pointer past an interval the same way finishing one does.
 */
enum class IntervalOutcome {
    PENDING,
    FINISHED,
    SKIPPED,
}

/**
 * One interval in a running workout's schedule, paired with what became of it (`TIMER-005`).
 */
data class ScheduleEntry(
    val interval: Interval,
    val outcome: IntervalOutcome = IntervalOutcome.PENDING,
)

/**
 * Where the running timer's clock is pointed (`TIMER-018`): the three-second lead-in before a
 * schedule interval, or the interval itself. This is not a fifth [TimerState] — it is part of
 * [TimerState.Running] and [TimerState.Paused], both of which carry one.
 */
sealed interface TimerPhase {
    /**
     * Counting down to the schedule interval at [index], which has not started yet
     * (`TIMER-030`–`036`).
     */
    data class LeadIn(val index: Int) : TimerPhase

    /** Running the schedule interval at [index]. */
    data class InInterval(val index: Int) : TimerPhase
}

/**
 * The timer's four states and nothing else (`TIMER-010`).
 *
 * Total time (`TIMER-054`) is tracked across [Running] and [Paused] by [runningSinceMillis] /
 * [accumulatedRunningMillis]: the workout has already spent `accumulatedRunningMillis` running,
 * before whatever running stretch is current. [Running] records where that stretch began, on the
 * clock the reducer is given; [Paused] has no such stretch, since no workout time passes while
 * paused (`TIMER-024`).
 */
sealed interface TimerState {

    /** No workout loaded. The only state [TimerEvent.Start] is accepted in (`TIMER-013`). */
    data object Idle : TimerState

    /**
     * A workout in progress, in the lead-in or in an interval (`TIMER-018`), counting down to
     * [deadlineMillis] — deadline minus now is [remainingMillis] (`TIMER-020`).
     */
    data class Running(
        val schedule: List<ScheduleEntry>,
        val phase: TimerPhase,
        val deadlineMillis: Long,
        val runningSinceMillis: Long,
        val accumulatedRunningMillis: Long,
    ) : TimerState

    /**
     * A workout paused mid lead-in or mid interval (`TIMER-023`). [remainingMillis] is
     * deadline-minus-now at the instant pause was accepted, held because no deadline exists while
     * paused; [accumulatedRunningMillis] is frozen at the same instant.
     */
    data class Paused(
        val schedule: List<ScheduleEntry>,
        val phase: TimerPhase,
        val remainingMillis: Long,
        val accumulatedRunningMillis: Long,
    ) : TimerState

    /**
     * A workout that has ended (`TIMER-011`), carrying the summary's three values (`TIMER-052`):
     * [schedule] (rounds completed reads it, `TIMER-061`), [totalTimeMillis] (`TIMER-054`), and
     * [outcome].
     */
    data class Ended(
        val schedule: List<ScheduleEntry>,
        val outcome: WorkoutOutcome,
        val totalTimeMillis: Long,
    ) : TimerState
}

/** The six events that can move the timer, and nothing else (`TIMER-012`). */
sealed interface TimerEvent {
    /**
     * Accepted only from [TimerState.Idle], and only when [schedule] holds at least one interval
     * (`TIMER-013`).
     */
    data class Start(val schedule: List<Interval>) : TimerEvent

    /** Advances the clock; may cross a deadline (`TIMER-020`–`022`). */
    data object Tick : TimerEvent

    /** Accepted only in running (`TIMER-014`). */
    data object Pause : TimerEvent

    /** Accepted only in paused (`TIMER-014`). */
    data object Resume : TimerEvent

    /** Accepted in running and in paused (`TIMER-040`, `TIMER-042`). */
    data object Skip : TimerEvent

    /** Accepted in running and in paused (`TIMER-016`). */
    data object Stop : TimerEvent
}
