package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/screens.md` `SCREEN-023`–`026`: which intervals the running
 * screen's past and upcoming lists hold, how far each is from the current one and how it fades and
 * shrinks, which interval counts as current during a lead-in, and the timeline strip's segments,
 * alphas and gap.
 */
class RunningScreenScheduleTest {

    private val warmUp = Interval(IntervalKind.WARM_UP, durationSeconds = 60)
    private val work = Interval(IntervalKind.WORK, durationSeconds = 45)
    private val recovery = Interval(IntervalKind.RECOVERY, durationSeconds = 15)
    private val coolDown = Interval(IntervalKind.COOL_DOWN, durationSeconds = 120)

    /** Eight intervals: warm-up, three work/recovery pairs, cool-down. */
    private val eight = listOf(warmUp, work, recovery, work, recovery, work, recovery, coolDown)

    // ---- null outside running and paused -----------------------------------------------------

    @Test
    fun `there is no schedule for idle`() {
        assertNull(TimerState.Idle.runningScreenSchedule())
    }

    @Test
    fun `there is no schedule for ended`() {
        val clock = ScheduleFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(eight), clock)
        val ended = reduce(running, TimerEvent.Stop, clock)

        assertNull(ended.runningScreenSchedule())
    }

    // ---- SCREEN-023: two past rows, four upcoming rows, current not repeated ----------------

    @Test
    fun `mid-workout the past list holds the two intervals before the current one, oldest first`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        assertEquals(3, schedule.currentIndex)
        assertEquals(listOf(1, 2), schedule.past.map { it.index })
        assertEquals(listOf(work, recovery), schedule.past.map { it.interval })
        assertEquals(listOf(2, 1), schedule.past.map { it.distance })
    }

    @Test
    fun `mid-workout the upcoming list holds the four intervals after the current one, next first`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        assertEquals(listOf(4, 5, 6, 7), schedule.upcoming.map { it.index })
        assertEquals(listOf(recovery, work, recovery, coolDown), schedule.upcoming.map { it.interval })
        assertEquals(listOf(1, 2, 3, 4), schedule.upcoming.map { it.distance })
    }

    @Test
    fun `the current interval is in neither list`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        val listed = (schedule.past + schedule.upcoming).map { it.index }
        assertEquals(false, 3 in listed)
    }

    @Test
    fun `on the first interval the past list is empty and the upcoming list still holds four`() {
        val schedule = inInterval(eight, index = 0).runningScreenSchedule()!!

        assertEquals(emptyList<Int>(), schedule.past.map { it.index })
        assertEquals(listOf(1, 2, 3, 4), schedule.upcoming.map { it.index })
    }

    @Test
    fun `on the second interval the past list holds just the one before it`() {
        val schedule = inInterval(eight, index = 1).runningScreenSchedule()!!

        assertEquals(listOf(0), schedule.past.map { it.index })
        assertEquals(listOf(1), schedule.past.map { it.distance })
    }

    @Test
    fun `near the end the upcoming list just shows fewer rows`() {
        val schedule = inInterval(eight, index = 5).runningScreenSchedule()!!

        assertEquals(listOf(3, 4), schedule.past.map { it.index })
        assertEquals(listOf(6, 7), schedule.upcoming.map { it.index })
    }

    @Test
    fun `on the last interval the upcoming list is empty`() {
        val schedule = inInterval(eight, index = 7).runningScreenSchedule()!!

        assertEquals(listOf(5, 6), schedule.past.map { it.index })
        assertEquals(emptyList<Int>(), schedule.upcoming.map { it.index })
    }

    @Test
    fun `a one-interval schedule has both lists empty`() {
        val schedule = inInterval(listOf(work), index = 0).runningScreenSchedule()!!

        assertEquals(emptyList<ScheduleRow>(), schedule.past)
        assertEquals(emptyList<ScheduleRow>(), schedule.upcoming)
    }

    // ---- SCREEN-023: alpha 1 − 0.18 × distance in 0.3–1.0, scale 1 − 0.06 × distance in 0.75–1.0

    @Test
    fun `rows fade and shrink with their distance from the current interval`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        assertFloats(listOf(0.64f, 0.82f), schedule.past.map { it.alpha })
        assertFloats(listOf(0.88f, 0.94f), schedule.past.map { it.scale })
        // Distance 4 would be 0.28 alpha; it is clamped up to 0.3.
        assertFloats(listOf(0.82f, 0.64f, 0.46f, 0.3f), schedule.upcoming.map { it.alpha })
        assertFloats(listOf(0.94f, 0.88f, 0.82f, 0.76f), schedule.upcoming.map { it.scale })
    }

    @Test
    fun `row alpha is clamped to 0_3 to 1_0`() {
        assertEquals(1f, scheduleRowAlpha(0), TOLERANCE)
        assertEquals(0.82f, scheduleRowAlpha(1), TOLERANCE)
        assertEquals(0.46f, scheduleRowAlpha(3), TOLERANCE)
        assertEquals(0.3f, scheduleRowAlpha(4), TOLERANCE)
        assertEquals(0.3f, scheduleRowAlpha(10), TOLERANCE)
    }

    @Test
    fun `row scale is clamped to 0_75 to 1_0`() {
        assertEquals(1f, scheduleRowScale(0), TOLERANCE)
        assertEquals(0.94f, scheduleRowScale(1), TOLERANCE)
        assertEquals(0.76f, scheduleRowScale(4), TOLERANCE)
        assertEquals(0.75f, scheduleRowScale(5), TOLERANCE)
        assertEquals(0.75f, scheduleRowScale(10), TOLERANCE)
    }

    // ---- SCREEN-024: during a lead-in, the interval it counts into is current ---------------

    @Test
    fun `during the opening lead-in the first interval is current`() {
        val clock = ScheduleFakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(eight), clock)

        val schedule = state.runningScreenSchedule()!!

        assertEquals(0, schedule.currentIndex)
        assertEquals(emptyList<Int>(), schedule.past.map { it.index })
        assertEquals(listOf(1, 2, 3, 4), schedule.upcoming.map { it.index })
    }

    @Test
    fun `during a lead-in after a skip the interval about to start is current`() {
        val state = inInterval(eight, index = 3)
        val skipped = reduce(state, TimerEvent.Skip, ScheduleFakeClock(0L))
        // Skip starts the lead-in into index 4 (TIMER-041).
        assertEquals(TimerPhase.LeadIn(4), (skipped as TimerState.Running).phase)

        val schedule = skipped.runningScreenSchedule()!!

        assertEquals(4, schedule.currentIndex)
        assertEquals(listOf(2, 3), schedule.past.map { it.index })
        assertEquals(listOf(5, 6, 7), schedule.upcoming.map { it.index })
        assertEquals(listOf(1f, 0.25f), schedule.strip.slice(4..5).map { it.alpha })
    }

    // ---- SCREEN-028: paused shows the held position ----------------------------------------

    @Test
    fun `paused shows the same schedule as running at the same position`() {
        val clock = ScheduleFakeClock(0L)
        val running = inInterval(eight, index = 3)
        val paused = reduce(running, TimerEvent.Pause, clock)

        assertEquals(running.runningScreenSchedule(), paused.runningScreenSchedule())
    }

    // ---- SCREEN-025: one segment per entry, exactly one bright ------------------------------

    @Test
    fun `the strip has one segment per schedule entry, in schedule order, with its kind`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        assertEquals(eight.map { it.kind }, schedule.strip.map { it.kind })
    }

    @Test
    fun `only the current segment is at full alpha and every other one is at 0_25`() {
        val schedule = inInterval(eight, index = 3).runningScreenSchedule()!!

        assertEquals(
            listOf(0.25f, 0.25f, 0.25f, 1f, 0.25f, 0.25f, 0.25f, 0.25f),
            schedule.strip.map { it.alpha },
        )
    }

    @Test
    fun `exactly one segment is at full alpha at every position, lead-in and interval alike`() {
        val clock = ScheduleFakeClock(0L)
        var state: TimerState = reduce(TimerState.Idle, TimerEvent.Start(eight), clock)
        var seen = 0
        while (state !is TimerState.Ended) {
            val schedule = assertNotNullSchedule(state)
            assertEquals(1, schedule.strip.count { it.alpha == 1f })
            assertEquals(schedule.currentIndex, schedule.strip.indexOfFirst { it.alpha == 1f })
            seen++
            // Alternate: clear the lead-in with a tick, then skip into the next lead-in.
            clock.advanceBy(3_000L)
            state = reduce(state, TimerEvent.Tick, clock)
            if (state is TimerState.Ended) break
            assertEquals(1, assertNotNullSchedule(state).strip.count { it.alpha == 1f })
            seen++
            state = reduce(state, TimerEvent.Skip, clock)
        }
        assertEquals(eight.size * 2, seen)
    }

    // ---- SCREEN-026: the gap is 3dp below 40 intervals and 1dp from 40 ---------------------

    @Test
    fun `the strip gap is 3dp with 39 intervals`() {
        val schedule = inInterval(List(39) { work }, index = 0).runningScreenSchedule()!!

        assertEquals(39, schedule.strip.size)
        assertEquals(3, schedule.stripGapDp)
    }

    @Test
    fun `the strip gap is 1dp with 40 intervals`() {
        val schedule = inInterval(List(40) { work }, index = 0).runningScreenSchedule()!!

        assertEquals(40, schedule.strip.size)
        assertEquals(1, schedule.stripGapDp)
    }

    @Test
    fun `the strip gap is 3dp for a short schedule`() {
        assertEquals(3, inInterval(eight, index = 0).runningScreenSchedule()!!.stripGapDp)
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Starts [intervals], clears the lead-in, then skips forward until [index] is in progress. */
    private fun inInterval(intervals: List<Interval>, index: Int): TimerState {
        val clock = ScheduleFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(intervals), clock)
        repeat(index) { state = reduce(state, TimerEvent.Skip, clock) }
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        assertEquals(TimerPhase.InInterval(index), (state as TimerState.Running).phase)
        return state
    }

    private fun assertNotNullSchedule(state: TimerState): RunningScreenSchedule {
        val schedule = state.runningScreenSchedule()
        assertNotNull("no schedule for $state", schedule)
        return schedule!!
    }

    private fun assertFloats(expected: List<Float>, actual: List<Float>) {
        assertEquals("sizes differ: $expected vs $actual", expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals("$expected vs $actual", e, a, TOLERANCE) }
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}

private class ScheduleFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
