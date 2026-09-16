package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/service.md` `SVC-025`–`026`: the notification's content and its
 * pause/resume toggle, both pure functions of [TimerState] and nothing else (`ADR 0005`).
 */
class WorkoutNotificationContentTest {

    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val recovery30 = Interval(IntervalKind.RECOVERY, durationSeconds = 30)

    // ---- SVC-025: notification content -----------------------------------------------------

    @Test
    fun `there is no content for idle`() {
        val clock = NotifFakeClock(0L)
        assertNull(TimerState.Idle.notificationContent(clock))
    }

    @Test
    fun `there is no content for ended`() {
        val clock = NotifFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val ended = reduce(running, TimerEvent.Stop, clock)

        assertNull(ended.notificationContent(clock))
    }

    @Test
    fun `content during an interval is that interval's kind and remaining time`() {
        val clock = NotifFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(3_000L) // clear the lead-in
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(10_000L) // 10s into work60

        val content = state.notificationContent(clock)

        assertEquals(WorkoutNotificationContent(IntervalKind.WORK, 50_000L), content)
    }

    @Test
    fun `content during the lead-in is the interval it counts into, and the lead-in's own countdown`() {
        val clock = NotifFakeClock(0L)
        val state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60, recovery30)), clock)
        clock.advanceBy(1_000L) // 1s into the 3s lead-in

        val content = state.notificationContent(clock)

        assertEquals(WorkoutNotificationContent(IntervalKind.WORK, 2_000L), content)
    }

    @Test
    fun `content while paused holds the remaining time at the instant pause was accepted`() {
        val clock = NotifFakeClock(0L)
        var state = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        clock.advanceBy(3_000L)
        state = reduce(state, TimerEvent.Tick, clock)
        clock.advanceBy(20_000L) // 20s into work60
        val paused = reduce(state, TimerEvent.Pause, clock)
        clock.advanceBy(500_000L) // time passes while paused; must not move the reading

        val content = paused.notificationContent(clock)

        assertEquals(WorkoutNotificationContent(IntervalKind.WORK, 40_000L), content)
    }

    // ---- SVC-026: the pause/resume toggle ---------------------------------------------------

    @Test
    fun `toggle sends pause from running`() {
        val clock = NotifFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)

        assertEquals(TimerEvent.Pause, running.toggleEvent())
    }

    @Test
    fun `toggle sends resume from paused`() {
        val clock = NotifFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val paused = reduce(running, TimerEvent.Pause, clock)

        assertEquals(TimerEvent.Resume, paused.toggleEvent())
    }

    @Test
    fun `toggle sends nothing from idle or ended`() {
        val clock = NotifFakeClock(0L)
        val running = reduce(TimerState.Idle, TimerEvent.Start(listOf(work60)), clock)
        val ended = reduce(running, TimerEvent.Stop, clock)

        assertNull(TimerState.Idle.toggleEvent())
        assertNull(ended.toggleEvent())
    }
}

private class NotifFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
