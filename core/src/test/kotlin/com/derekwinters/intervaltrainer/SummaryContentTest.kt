package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/screens.md` `SCREEN-050`–`053`: the summary screen's own pure
 * state derivation, the same "a screen shows only what a `:core` reducer computed for it" split
 * [RunningScreenContent] and `WorkoutNotificationContent` (`SVC-025`) already give their own
 * screens.
 */
class SummaryContentTest {

    private val work45 = Interval(IntervalKind.WORK, durationSeconds = 45)
    private val recovery30 = Interval(IntervalKind.RECOVERY, durationSeconds = 30)
    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)

    @Test
    fun `there is no summary content for idle`() {
        assertNull(TimerState.Idle.summaryContent())
    }

    @Test
    fun `there is no summary content for running`() {
        val clock = SummaryFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work45)), clock)

        assertNull(running.summaryContent())
    }

    @Test
    fun `there is no summary content for paused`() {
        val clock = SummaryFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work45)), clock)
        val paused = reduce(running, TimerEvent.Pause, clock)

        assertNull(paused.summaryContent())
    }

    // ---- TIMER-011, TIMER-054, TIMER-060–061: a workout that runs to completion ----------------

    @Test
    fun `a completed workout reports every round finished, total time, and the completed outcome`() {
        val clock = SummaryFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work45, recovery30, work60)), clock)
        clock.advanceBy(3_000L) // lead-in into work45
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(45_000L) // finish work45
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(30_000L) // finish recovery30
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(60_000L) // finish work60, ending the workout
        state = reduce(state, TimerEvent.Tick, clock)

        val content = state.summaryContent()

        assertEquals(
            SummaryContent(
                roundsCompleted = 2,
                roundsPlanned = 2,
                totalTimeMillis = 3_000L + 45_000L + 30_000L + 60_000L,
                outcome = WorkoutOutcome.COMPLETED,
            ),
            content,
        )
    }

    // ---- TIMER-016, TIMER-050: a workout stopped early -------------------------------------------

    @Test
    fun `a workout stopped early reports only the rounds actually finished and the stopped-early outcome`() {
        val clock = SummaryFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work45, recovery30, work60)), clock)
        clock.advanceBy(3_000L) // lead-in into work45
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(45_000L) // finish work45
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(5_000L) // 5s into recovery30, then stop
        state = reduce(state, TimerEvent.Stop, clock)

        val content = state.summaryContent()

        assertEquals(
            SummaryContent(
                roundsCompleted = 1,
                roundsPlanned = 2,
                totalTimeMillis = 3_000L + 45_000L + 5_000L,
                outcome = WorkoutOutcome.STOPPED_EARLY,
            ),
            content,
        )
    }

    // ---- TIMER-061: a skipped work interval never counts toward rounds completed ----------------

    @Test
    fun `a skipped work interval does not count as completed, even though the workout still finishes`() {
        val clock = SummaryFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work45, recovery30, work60)), clock)
        // Skip abandons work45 (marked SKIPPED, not FINISHED) and enters a fresh lead-in into
        // recovery30 (TIMER-040-041).
        state = reduce(state, TimerEvent.Skip, clock)
        clock.advanceBy(3_000L) // clear the lead-in into recovery30
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(30_000L) // finish recovery30
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(60_000L) // finish work60, ending the workout
        state = reduce(state, TimerEvent.Tick, clock)

        val content = state.summaryContent()

        assertEquals(WorkoutOutcome.COMPLETED, content?.outcome)
        // Only work60 finished; work45 was skipped and never counts toward rounds completed
        // (TIMER-061), however far skip has since moved the schedule past it.
        assertEquals(1, content?.roundsCompleted)
        assertEquals(2, content?.roundsPlanned)
    }
}

private class SummaryFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
