package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/screens.md` `SCREEN-020`, `SCREEN-021` and `SCREEN-024`:
 * the running screen's own pure state derivation, the same "a screen shows only what a `:core`
 * reducer computed for it" split `WorkoutNotificationContent` (`SVC-025`) already gives the
 * notification.
 */
class RunningScreenContentTest {

    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val recovery30 = Interval(IntervalKind.RECOVERY, durationSeconds = 30)

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
                totalRemainingMillis = 40_000L,
            ),
            content,
        )
    }
}

private class RunningFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
