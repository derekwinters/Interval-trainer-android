package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/screens.md` `SCREEN-020`–`022`, `SCREEN-022a` and `SCREEN-024`:
 * the running screen's own pure state derivation, the same "a screen shows only what a `:core`
 * reducer computed for it" split `WorkoutNotificationContent` (`SVC-025`) already gives the
 * notification.
 */
class RunningScreenContentTest {

    private val warmUp15 = Interval(IntervalKind.WARM_UP, durationSeconds = 15)
    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val recovery30 = Interval(IntervalKind.RECOVERY, durationSeconds = 30)
    private val work45 = Interval(IntervalKind.WORK, durationSeconds = 45)

    // ---- SCREEN-024: the lead-in is a distinct "get ready" shape ----------------------------

    @Test
    fun `there is no content for idle`() {
        val clock = RunningFakeClock(0L)
        assertNull(TimerState.Idle.runningScreenContent(clock))
    }

    @Test
    fun `there is no content for ended`() {
        val clock = RunningFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val ended = reduce(running, TimerEvent.Stop, clock)

        assertNull(ended.runningScreenContent(clock))
    }

    @Test
    fun `content during the lead-in is get-ready, naming the interval it counts into`() {
        val clock = RunningFakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(1_000L) // 1s into the 3s lead-in

        val content = state.runningScreenContent(clock)

        assertEquals(
            RunningScreenContent.GetReady(
                remainingMillis = 2_000L,
                upcomingKind = IntervalKind.WORK,
                upcomingDurationMillis = 60_000L,
                // The lead-in counts into work60 itself, so the round it belongs to is already
                // in progress the moment its lead-in begins (SCREEN-022a's own definition).
                roundInProgress = 1,
                roundsPlanned = 1,
                totalRemainingMillis = 2_000L + 60_000L + 30_000L,
            ),
            content,
        )
    }

    // ---- SCREEN-020: the ring's own content while inside an interval -------------------------

    @Test
    fun `content during an interval is active, with the interval's kind and remaining vs full duration`() {
        val clock = RunningFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(3_000L) // clear the lead-in
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(10_000L) // 10s into work60

        val content = state.runningScreenContent(clock)

        assertEquals(
            RunningScreenContent.Active(
                kind = IntervalKind.WORK,
                remainingMillis = 50_000L,
                fullDurationMillis = 60_000L,
                roundInProgress = 1,
                roundsPlanned = 1,
                totalRemainingMillis = 50_000L + 30_000L,
            ),
            content,
        )
    }

    @Test
    fun `content while paused holds the remaining time at the instant pause was accepted`() {
        val clock = RunningFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(20_000L) // 20s into work60
        val paused = reduce(state, TimerEvent.Pause, clock)
        clock.advanceBy(500_000L) // time passes while paused; must not move the reading

        val content = paused.runningScreenContent(clock)

        assertEquals(
            RunningScreenContent.Active(
                kind = IntervalKind.WORK,
                remainingMillis = 40_000L,
                fullDurationMillis = 60_000L,
                roundInProgress = 1,
                roundsPlanned = 1,
                totalRemainingMillis = 40_000L,
            ),
            content,
        )
    }

    // ---- SCREEN-022 / SCREEN-022a: the round in progress, distinct from rounds completed -----

    @Test
    fun `round in progress is zero during a leading warm-up, ahead of the first work interval`() {
        val clock = RunningFakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(warmUp15, work60)), clock)

        val content = state.runningScreenContent(clock) as RunningScreenContent.GetReady

        assertEquals(0, content.roundInProgress)
        assertEquals(1, content.roundsPlanned)
    }

    @Test
    fun `round in progress advances to 2 once the second work interval is reached, before it finishes`() {
        val clock = RunningFakeClock(0L)
        var state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work45, recovery30, work45, recovery30)),
            clock,
        )
        clock.advanceBy(3_000L) // clear the lead-in into round 1's work
        state = reduce(state, TimerEvent.Tick, clock)
        state = reduce(state, TimerEvent.Skip, clock) // abandon round 1's work, into recovery's lead-in
        state = reduce(state, TimerEvent.Skip, clock) // abandon recovery, into round 2's lead-in

        val content = state.runningScreenContent(clock) as RunningScreenContent.GetReady

        // Round 1's work was skipped, not finished (TIMER-061) — but it is still what "in
        // progress" has reached: the round-in-progress count is positional, not completion.
        assertEquals(2, content.roundInProgress)
        assertEquals(2, content.roundsPlanned)
    }

    @Test
    fun `round in progress stays at the last work interval reached while resting after it`() {
        val clock = RunningFakeClock(0L)
        var state = reduce(
            TimerState.Idle,
            TimerEvent.Start(listOf(work45, recovery30, work45)),
            clock,
        )
        clock.advanceBy(3_000L) // clear the lead-in into work #1
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(45_000L) // finish work #1
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(3_000L) // clear the lead-in into recovery
        state = reduce(state, TimerEvent.Tick, clock)

        val content = state.runningScreenContent(clock) as RunningScreenContent.Active

        assertEquals(IntervalKind.RECOVERY, content.kind)
        assertEquals(1, content.roundInProgress)
        assertEquals(2, content.roundsPlanned)
    }
}

private class RunningFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
