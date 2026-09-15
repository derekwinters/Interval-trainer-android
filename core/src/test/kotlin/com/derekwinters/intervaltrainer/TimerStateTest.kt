package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the timer state machine (`docs/spec/timer.md` §2–7): the states and events,
 * the monotonic-clock deadline model, the three-second lead-in, skip, stop and the summary, and
 * rounds completed. Every `auto`-tagged requirement in that range is exercised here against
 * [FakeClock], so a twenty-minute workout runs in milliseconds of test time (ADR 0005).
 *
 * The minimum-interval rule (`timer.md` §8, TIMER-070–073) is deliberately not exercised here —
 * see this pull request's body for why.
 */
class TimerStateTest {

    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val recovery30 = Interval(IntervalKind.RECOVERY, durationSeconds = 30)
    private val work45 = Interval(IntervalKind.WORK, durationSeconds = 45)

    // ---- §2 The states and the events (TIMER-010–018) ----------------------------------------

    /** TIMER-010, TIMER-013, TIMER-018: start moves idle to running, entering the lead-in. */
    @Test
    fun `start moves idle to running, entering the lead-in before the first interval`() {
        val clock = FakeClock(1_000L)

        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        check(state is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(0), state.phase)
    }

    /** TIMER-013: start is accepted only when the schedule holds at least one interval. */
    @Test
    fun `start does nothing when the schedule is empty`() {
        val clock = FakeClock(0L)

        val state = reduce(TimerState.Idle, TimerEvent.Start(emptyList()), clock)

        assertEquals(TimerState.Idle, state)
    }

    /** TIMER-013 table: start has no cell outside idle, so it changes nothing from running. */
    @Test
    fun `start does nothing once already running`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        val state = reduce(running, TimerEvent.Start(listOf(work60, recovery30)), clock)

        assertEquals(running, state)
    }

    /** TIMER-014: pause has no cell outside running. */
    @Test
    fun `pause does nothing from idle, paused or ended`() {
        val clock = FakeClock(0L)
        assertEquals(TimerState.Idle, reduce(TimerState.Idle, TimerEvent.Pause, clock))

        val paused = pausedAfterStart(clock)
        assertEquals(paused, reduce(paused, TimerEvent.Pause, clock))

        val ended = reduce(paused, TimerEvent.Stop, clock)
        assertEquals(ended, reduce(ended, TimerEvent.Pause, clock))
    }

    /** TIMER-014: resume has no cell outside paused. */
    @Test
    fun `resume does nothing from idle, running or ended`() {
        val clock = FakeClock(0L)
        assertEquals(TimerState.Idle, reduce(TimerState.Idle, TimerEvent.Resume, clock))

        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        assertEquals(running, reduce(running, TimerEvent.Resume, clock))

        val ended = reduce(running, TimerEvent.Stop, clock)
        assertEquals(ended, reduce(ended, TimerEvent.Resume, clock))
    }

    /** TIMER-015: the workout ends completed when the last interval reaches its deadline. */
    @Test
    fun `the workout ends completed when the last interval's deadline is reached`() {
        val clock = FakeClock(0L)
        var state: TimerState = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L) // clear the lead-in
        state = reduce(state, TimerEvent.Tick, clock)
        check(state is TimerState.Running)
        assertEquals(TimerPhase.InInterval(0), state.phase)

        clock.advanceBy(60_000L) // clear the only interval
        state = reduce(state, TimerEvent.Tick, clock)

        check(state is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, state.outcome)
    }

    /** TIMER-016: stop ends the workout stopped-early, from running and from paused. */
    @Test
    fun `stop ends the workout stopped early from running and from paused`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val endedFromRunning = reduce(running, TimerEvent.Stop, clock)
        check(endedFromRunning is TimerState.Ended)
        assertEquals(WorkoutOutcome.STOPPED_EARLY, endedFromRunning.outcome)

        val paused = reduce(running, TimerEvent.Pause, clock)
        val endedFromPaused = reduce(paused, TimerEvent.Stop, clock)
        check(endedFromPaused is TimerState.Ended)
        assertEquals(WorkoutOutcome.STOPPED_EARLY, endedFromPaused.outcome)
    }

    /** TIMER-017: a workout that has ended accepts no event. */
    @Test
    fun `an ended workout accepts no event`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val ended = reduce(running, TimerEvent.Stop, clock)

        assertEquals(ended, reduce(ended, TimerEvent.Start(listOf(work60)), clock))
        assertEquals(ended, reduce(ended, TimerEvent.Tick, clock))
        assertEquals(ended, reduce(ended, TimerEvent.Pause, clock))
        assertEquals(ended, reduce(ended, TimerEvent.Resume, clock))
        assertEquals(ended, reduce(ended, TimerEvent.Skip, clock))
        assertEquals(ended, reduce(ended, TimerEvent.Stop, clock))
    }

    /** TIMER-018: the lead-in is part of running, not a fifth state. */
    @Test
    fun `the lead-in is a phase of running, not a separate state`() {
        val clock = FakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        assertTrue(state is TimerState.Running)
    }

    // ---- §3 The deadline model (TIMER-020–025) ------------------------------------------------

    /** TIMER-020: remaining time is deadline minus now, computed fresh on demand. */
    @Test
    fun `remaining time is deadline minus now`() {
        val clock = FakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        assertEquals(3_000L, state.remainingMillis(clock))
        clock.advanceBy(1_000L)
        assertEquals(2_000L, state.remainingMillis(clock))
    }

    /** TIMER-021: a late tick reports less time remaining but does not move the deadline. */
    @Test
    fun `a late tick does not move the deadline`() {
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        check(state is TimerState.Running)
        val deadlineBefore = state.deadlineMillis

        clock.advanceBy(10_000L) // a very late tick, 10s into a 60s interval
        val late = reduce(state, TimerEvent.Tick, clock)

        check(late is TimerState.Running)
        assertEquals(deadlineBefore, late.deadlineMillis)
        assertEquals(50_000L, late.remainingMillis(clock))
    }

    /** TIMER-022: a tick past a deadline ends that interval there; the overshoot is not carried
     * into the next interval. */
    @Test
    fun `a tick past a deadline begins the next interval there, without carrying the overshoot`() {
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(3_000L) // clear the lead-in
        state = reduce(state, TimerEvent.Tick, clock)

        clock.advanceBy(60_500L) // 500ms past the first interval's deadline
        state = reduce(state, TimerEvent.Tick, clock)

        check(state is TimerState.Running)
        assertEquals(TimerPhase.InInterval(1), state.phase)
        // The first interval's deadline was at 3_000 + 60_000 = 63_000; the second's is that plus
        // its own 30s duration, not "now" (63_500) plus 30s.
        assertEquals(63_000L + 30_000L, state.deadlineMillis)
        assertEquals(IntervalOutcome.FINISHED, state.schedule[0].outcome)
    }

    /** TIMER-023: pause holds deadline-minus-now, and the deadline stops existing. */
    @Test
    fun `pause holds the remaining time at the instant it is accepted`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(1_200L)

        val paused = reduce(running, TimerEvent.Pause, clock)

        check(paused is TimerState.Paused)
        assertEquals(1_800L, paused.remainingMillis)
    }

    /** TIMER-024: resume sets a new deadline from the held remaining time; no time passes while
     * paused. */
    @Test
    fun `resume sets a new deadline from the held remaining time`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(1_200L)
        val paused = reduce(running, TimerEvent.Pause, clock)

        clock.advanceBy(500_000L) // a long time spent paused
        val resumed = reduce(paused, TimerEvent.Resume, clock)

        check(resumed is TimerState.Running)
        assertEquals(clock.nowMillis() + 1_800L, resumed.deadlineMillis)
        assertEquals(1_800L, resumed.remainingMillis(clock))
    }

    /** TIMER-025: total remaining is the current remaining plus every later interval's full
     * duration, and it does not move while paused. */
    @Test
    fun `total remaining is current remaining plus every later interval in full`() {
        val clock = FakeClock(0L)
        var state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work60, recovery30, work45)),
            clock,
        )
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(10_000L) // 10s into the first (60s) interval

        assertEquals(50_000L + 30_000L + 45_000L, state.totalRemainingMillis(clock))

        val paused = reduce(state, TimerEvent.Pause, clock)
        clock.advanceBy(999_000L)
        assertEquals(50_000L + 30_000L + 45_000L, paused.totalRemainingMillis(clock))
    }

    // ---- §4 The lead-in (TIMER-030–036) --------------------------------------------------------

    /** TIMER-030: the lead-in is three seconds. */
    @Test
    fun `the lead-in is three seconds`() {
        val clock = FakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        assertEquals(3_000L, state.remainingMillis(clock))
    }

    /** TIMER-031: the lead-in is entered on start and on skip, and at no other moment — a tick
     * that finishes one interval begins the next one directly. */
    @Test
    fun `tick moves from one interval straight into the next, with no lead-in between them`() {
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        check(state is TimerState.Running)
        assertEquals(TimerPhase.InInterval(0), state.phase)

        clock.advanceBy(60_000L)
        state = reduce(state, TimerEvent.Tick, clock)

        check(state is TimerState.Running)
        assertEquals(TimerPhase.InInterval(1), state.phase)
    }

    /** TIMER-032: the lead-in is never a schedule row — it appears in no interval list, round
     * count or total. */
    @Test
    fun `the lead-in adds no row to the schedule`() {
        val clock = FakeClock(0L)
        val schedule = listOf(work60, recovery30)

        val state = reduce(TimerState.Idle, TimerEvent.Start(schedule), clock)

        check(state is TimerState.Running)
        assertEquals(2, state.schedule.size)
        assertEquals(schedule, state.schedule.map { it.interval })
    }

    /** TIMER-034, TIMER-035: the lead-in is time on top of the schedule — while it runs, total
     * remaining counts the upcoming interval in full, so a skip adds three seconds overall. */
    @Test
    fun `total remaining counts the upcoming interval in full while its lead-in runs`() {
        val clock = FakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)

        assertEquals(3_000L + 60_000L + 30_000L, state.totalRemainingMillis(clock))
    }

    /** TIMER-035: a skip adds three seconds to the workout's wall-clock length — the interval it
     * skips into runs its full authored duration, not shortened by the lead-in in front of it. */
    @Test
    fun `a skip adds exactly three seconds to the workout's total time`() {
        val clock = FakeClock(0L)
        var state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work60, recovery30, work45)),
            clock,
        )
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock) // into work60
        clock.advanceBy(60_000L)
        state = reduce(state, TimerEvent.Tick, clock) // work60 finished, straight into recovery30
        state = reduce(state, TimerEvent.Skip, clock) // skip recovery30, lead-in into work45
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock) // work45 begins after its lead-in
        clock.advanceBy(45_000L)
        val ended = reduce(state, TimerEvent.Tick, clock)

        check(ended is TimerState.Ended)
        // Without the skip this workout would take 3_000 (start lead-in) + 60_000 + 30_000 +
        // 45_000 = 138_000; skipping recovery30 removes its 30_000 but adds one more 3_000
        // lead-in, netting 111_000.
        assertEquals(3_000L + 60_000L + 3_000L + 45_000L, ended.totalTimeMillis)
    }

    /** TIMER-036: pause and resume apply to a lead-in exactly as they do to an interval. */
    @Test
    fun `pause and resume hold and restore a lead-in's remaining time`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(1_000L)

        val paused = reduce(running, TimerEvent.Pause, clock)
        check(paused is TimerState.Paused)
        assertEquals(TimerPhase.LeadIn(0), paused.phase)
        assertEquals(2_000L, paused.remainingMillis)

        clock.advanceBy(60_000L)
        val resumed = reduce(paused, TimerEvent.Resume, clock)
        check(resumed is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(0), resumed.phase)
        assertEquals(2_000L, resumed.remainingMillis(clock))
    }

    // ---- §5 Skip (TIMER-040–045) ----------------------------------------------------------------

    /** TIMER-040, TIMER-041: skip abandons the current interval and moves to the next one after a
     * fresh three-second lead-in. */
    @Test
    fun `skip abandons the current interval and enters a fresh lead-in before the next one`() {
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(10_000L) // 10s into the first interval, well short of its deadline

        val skipped = reduce(state, TimerEvent.Skip, clock)

        check(skipped is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(1), skipped.phase)
        assertEquals(clock.nowMillis() + 3_000L, skipped.deadlineMillis)
        assertEquals(IntervalOutcome.SKIPPED, skipped.schedule[0].outcome)
    }

    /** TIMER-042: skip is accepted while paused, and leaves the timer running. */
    @Test
    fun `skip from paused leaves the timer running`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        val paused = reduce(running, TimerEvent.Pause, clock)

        val state = reduce(paused, TimerEvent.Skip, clock)

        assertTrue(state is TimerState.Running)
    }

    /** TIMER-043: skipping the last interval ends the workout completed. */
    @Test
    fun `skipping the last interval ends the workout completed`() {
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)

        val ended = reduce(state, TimerEvent.Skip, clock)

        check(ended is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, ended.outcome)
        assertEquals(IntervalOutcome.SKIPPED, ended.schedule[0].outcome)
    }

    /** TIMER-045: skip during the lead-in itself abandons that lead-in and walks forward one
     * interval at a time, never restarting the lead-in for the interval it was counting into. */
    @Test
    fun `repeated skip during the lead-in walks forward through the schedule`() {
        val clock = FakeClock(0L)
        val state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work60, recovery30, work45)),
            clock,
        )
        check(state is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(0), state.phase)

        val afterFirstSkip = reduce(state, TimerEvent.Skip, clock)
        check(afterFirstSkip is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(1), afterFirstSkip.phase)
        assertEquals(IntervalOutcome.SKIPPED, afterFirstSkip.schedule[0].outcome)
        assertEquals(IntervalOutcome.PENDING, afterFirstSkip.schedule[1].outcome)

        val afterSecondSkip = reduce(afterFirstSkip, TimerEvent.Skip, clock)
        check(afterSecondSkip is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(2), afterSecondSkip.phase)
        assertEquals(IntervalOutcome.SKIPPED, afterSecondSkip.schedule[1].outcome)
    }

    // ---- §6 Stop and the summary (TIMER-050–054) -----------------------------------------------

    /** TIMER-050: stop fires no finish cue — asserted here as "stop carries no completed
     * outcome", the only part of this observable from the state alone. */
    @Test
    fun `stop never reports the completed outcome`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        val ended = reduce(running, TimerEvent.Stop, clock)

        check(ended is TimerState.Ended)
        assertEquals(WorkoutOutcome.STOPPED_EARLY, ended.outcome)
    }

    /** TIMER-052: the ended state carries exactly the summary's three values. */
    @Test
    fun `the ended state carries rounds, total time and outcome`() {
        val clock = FakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(2_000L)

        val ended = reduce(running, TimerEvent.Stop, clock)

        check(ended is TimerState.Ended)
        assertEquals(WorkoutOutcome.STOPPED_EARLY, ended.outcome)
        assertEquals(0 to 1, ended.schedule.roundsCompleted())
        assertEquals(2_000L, ended.totalTimeMillis)
    }

    /** TIMER-054: total time is time spent running, lead-ins included, paused time excluded. */
    @Test
    fun `total time includes the lead-in and excludes paused time`() {
        val clock = FakeClock(0L)
        var state: TimerState = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L) // the lead-in, in full
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(10_000L) // 10s into the interval

        val paused = reduce(state, TimerEvent.Pause, clock)
        clock.advanceBy(500_000L) // a long time paused, must not count
        val resumed = reduce(paused, TimerEvent.Resume, clock)
        clock.advanceBy(5_000L)

        val ended = reduce(resumed, TimerEvent.Stop, clock)

        check(ended is TimerState.Ended)
        assertEquals(3_000L + 10_000L + 5_000L, ended.totalTimeMillis)
    }

    // ---- §7 Rounds completed (TIMER-060–061) ---------------------------------------------------

    /** TIMER-060: a round is a work interval; rounds planned counts only those. */
    @Test
    fun `rounds planned counts only work intervals`() {
        val schedule = listOf(
            ScheduleEntry(Interval(IntervalKind.WARM_UP, 60)),
            ScheduleEntry(work60, IntervalOutcome.FINISHED),
            ScheduleEntry(recovery30, IntervalOutcome.FINISHED),
            ScheduleEntry(work45, IntervalOutcome.FINISHED),
            ScheduleEntry(Interval(IntervalKind.COOL_DOWN, 60)),
        )

        assertEquals(2 to 2, schedule.roundsCompleted())
    }

    /** TIMER-061: a skipped work interval does not count as completed, however far skip has since
     * moved the schedule past it — a twenty-minute, ten-round workout run entirely by skip. */
    @Test
    fun `a full twenty-minute workout run entirely by skip counts no rounds completed`() {
        val rounds = (1..10).flatMap {
            listOf(Interval(IntervalKind.WORK, 60), Interval(IntervalKind.RECOVERY, 60))
        }
        val clock = FakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(rounds), clock)

        while (state is TimerState.Running) {
            state = reduce(state, TimerEvent.Skip, clock)
        }

        check(state is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, state.outcome)
        assertEquals(0 to 10, state.schedule.roundsCompleted())
    }

    /** TIMER-061: a finished work interval and a skipped one, in the same schedule — "1 of 2",
     * not derived from the schedule's index alone (which a skip advances exactly as a finish
     * does). A skipped *recovery* in between costs nothing, since only work intervals are rounds
     * at all (TIMER-060). */
    @Test
    fun `rounds completed counts a finished work interval but not a skipped one`() {
        val clock = FakeClock(0L)
        var state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work60, recovery30, work45, recovery30)),
            clock,
        )
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock) // into work60
        clock.advanceBy(60_000L)
        state = reduce(state, TimerEvent.Tick, clock) // work60 finished, into recovery30
        state = reduce(state, TimerEvent.Skip, clock) // skip recovery30, lead-in into work45
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock) // into work45
        state = reduce(state, TimerEvent.Skip, clock) // skip work45 itself this time

        check(state is TimerState.Running)
        assertEquals(1 to 2, state.schedule.roundsCompleted())
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun pausedAfterStart(clock: FakeClock): TimerState.Paused {
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val paused = reduce(running, TimerEvent.Pause, clock)
        check(paused is TimerState.Paused)
        return paused
    }
}

/**
 * A [Clock] this test file advances by hand, so a twenty-minute workout runs in milliseconds of
 * real test time rather than real wall-clock time.
 */
private class FakeClock(startMillis: Long) : Clock {
    private var millis = startMillis

    override fun nowMillis(): Long = millis

    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
