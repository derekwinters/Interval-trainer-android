package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * JVM unit tests for [scheduleFrom]. They need no device, no emulator and no simulated Android
 * runtime, matching every other `:core` test (ADR 0005).
 */
class ScheduleTest {

    private val intervals = listOf(
        Interval(IntervalKind.WARM_UP, durationSeconds = 180),
        Interval(IntervalKind.WORK, durationSeconds = 60),
        Interval(IntervalKind.RECOVERY, durationSeconds = 120),
        Interval(IntervalKind.WORK, durationSeconds = 60),
        Interval(IntervalKind.RECOVERY, durationSeconds = 120),
        Interval(IntervalKind.COOL_DOWN, durationSeconds = 180),
    )

    /**
     * SVC-001, TIMER-001–002: the schedule is the preset's own ordered interval list, unchanged
     * in content and order. `scheduleFrom` takes only the preset — no round count, no generator
     * parameter — because none is stored on one (TIMER-004).
     */
    @Test
    fun `returns the preset's intervals unchanged in content and order`() {
        val preset = Preset(id = "preset-1", name = "Short Example", intervals = intervals)

        val schedule = scheduleFrom(preset)

        assertEquals(intervals, schedule)
    }

    /**
     * SVC-001: the schedule is a *copy* of the preset's list, not an expansion and not a shared
     * reference — TIMER-003 depends on a workout's schedule being untouched by a later edit to
     * the preset it came from, which a shared list instance would not survive.
     */
    @Test
    fun `returns a copy rather than the preset's own list instance`() {
        val preset = Preset(id = "preset-2", name = "Single interval", intervals = intervals)

        val schedule = scheduleFrom(preset)

        assertNotSame(preset.intervals, schedule)
    }

    /** SVC-001: an empty preset's schedule is an empty list, not an error. */
    @Test
    fun `copies an empty interval list as an empty schedule`() {
        val preset = Preset(id = "preset-3", name = "Empty", intervals = emptyList())

        val schedule = scheduleFrom(preset)

        assertEquals(emptyList<Interval>(), schedule)
    }
}
