package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [presetSummary] and [scheduleSegments] (`docs/spec/screens.md`
 * `SCREEN-003`–`004`). Neither needs a device or a simulated Android runtime, matching every other
 * `:core` test (ADR 0005).
 */
class PresetSummaryTest {

    // ---- presetSummary (SCREEN-003) -------------------------------------------------------

    /**
     * SCREEN-003, TIMER-060: rounds planned is the count of `WORK` intervals; the total is the sum
     * of every interval's duration, `WORK` included. Mirrors the seeded "Short Example" preset
     * (`SCHEMA-031`): warm-up 3:00, three rounds of work 1:00 / recovery 2:00, cool-down 3:00.
     */
    @Test
    fun `counts rounds as work intervals and sums every interval's duration`() {
        val preset = Preset(
            id = "preset-1",
            name = "Short Example",
            intervals = buildList {
                add(Interval(IntervalKind.WARM_UP, 180))
                repeat(3) {
                    add(Interval(IntervalKind.WORK, 60))
                    add(Interval(IntervalKind.RECOVERY, 120))
                }
                add(Interval(IntervalKind.COOL_DOWN, 180))
            },
        )

        val summary = presetSummary(preset)

        assertEquals(3, summary.roundCount)
        assertEquals(900, summary.totalDurationSeconds)
    }

    /** SCREEN-003: a preset with no `WORK` interval has a round count of zero, not an error. */
    @Test
    fun `a preset with no work intervals has a round count of zero`() {
        val preset = Preset(
            id = "preset-2",
            name = "Steady effort",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, 120),
                Interval(IntervalKind.COOL_DOWN, 180),
            ),
        )

        val summary = presetSummary(preset)

        assertEquals(0, summary.roundCount)
        assertEquals(300, summary.totalDurationSeconds)
    }

    /** SCREEN-003: an empty preset summarises to zero rounds and zero total, not an error. */
    @Test
    fun `an empty preset summarises to zero rounds and zero total`() {
        val preset = Preset(id = "preset-3", name = "Empty", intervals = emptyList())

        val summary = presetSummary(preset)

        assertEquals(0, summary.roundCount)
        assertEquals(0, summary.totalDurationSeconds)
    }

    // ---- scheduleSegments (SCREEN-004) -----------------------------------------------------

    /**
     * SCREEN-004, CUE-030: one segment per interval, in schedule order, each sized by its share of
     * the total duration, coloured by the same role `CueSelection.kt` already maps `CUE-030` to.
     */
    @Test
    fun `emits one segment per interval, in order, sized by its share of the total`() {
        val preset = Preset(
            id = "preset-4",
            name = "Two rounds",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, 20),
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 50),
            ),
        )

        val segments = scheduleSegments(preset)

        assertEquals(
            listOf(
                ScheduleSegment(ColorRole.NEUTRAL, 0.2),
                ScheduleSegment(ColorRole.WORK, 0.3),
                ScheduleSegment(ColorRole.RECOVERY, 0.5),
            ),
            segments,
        )
    }

    /** SCREEN-004, CUE-030: warm-up and cool-down both map to the shared neutral role. */
    @Test
    fun `warm-up and cool-down both map to the neutral colour role`() {
        val preset = Preset(
            id = "preset-5",
            name = "Bookends",
            intervals = listOf(
                Interval(IntervalKind.WARM_UP, 10),
                Interval(IntervalKind.COOL_DOWN, 10),
            ),
        )

        val roles = scheduleSegments(preset).map { it.colorRole }

        assertEquals(listOf(ColorRole.NEUTRAL, ColorRole.NEUTRAL), roles)
    }

    /** SCREEN-004: an empty schedule yields no segments — nothing to draw a strip for. */
    @Test
    fun `an empty schedule yields no segments`() {
        val preset = Preset(id = "preset-6", name = "Empty", intervals = emptyList())

        assertEquals(emptyList<ScheduleSegment>(), scheduleSegments(preset))
    }

    /**
     * SCREEN-004: a schedule whose total duration is zero (every interval authored at zero
     * seconds) yields no segments rather than dividing by zero.
     */
    @Test
    fun `a schedule with zero total duration yields no segments`() {
        val preset = Preset(
            id = "preset-7",
            name = "All zero",
            intervals = listOf(Interval(IntervalKind.WORK, 0), Interval(IntervalKind.RECOVERY, 0)),
        )

        assertEquals(emptyList<ScheduleSegment>(), scheduleSegments(preset))
    }

    /** SCREEN-004: every segment's fraction sums to (approximately) the whole schedule. */
    @Test
    fun `segment fractions sum to one`() {
        val preset = Preset(
            id = "preset-8",
            name = "Long Example",
            intervals = buildList {
                add(Interval(IntervalKind.WARM_UP, 300))
                repeat(8) {
                    add(Interval(IntervalKind.WORK, 60))
                    add(Interval(IntervalKind.RECOVERY, 120))
                }
                add(Interval(IntervalKind.COOL_DOWN, 300))
            },
        )

        val total = scheduleSegments(preset).sumOf { it.fraction }

        assertTrue(Math.abs(total - 1.0) < 0.0001)
    }
}
