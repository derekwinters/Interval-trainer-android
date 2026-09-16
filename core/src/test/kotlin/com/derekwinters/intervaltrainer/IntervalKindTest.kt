package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [IntervalKind.next] (`docs/spec/screens.md` `SCREEN-014a`): the fixed cycle a
 * freshly added or reopened row's kind advances through when its colour dot/name is tapped while
 * the row is open for editing.
 */
class IntervalKindTest {

    /** SCREEN-014a: the cycle is warm-up, work, recovery, cool-down, then back to warm-up. */
    @Test
    fun `cycles through the four kinds in a fixed order`() {
        assertEquals(IntervalKind.WORK, IntervalKind.WARM_UP.next())
        assertEquals(IntervalKind.RECOVERY, IntervalKind.WORK.next())
        assertEquals(IntervalKind.COOL_DOWN, IntervalKind.RECOVERY.next())
        assertEquals(IntervalKind.WARM_UP, IntervalKind.COOL_DOWN.next())
    }

    /** A full cycle of four taps returns to the kind that was tapped first. */
    @Test
    fun `four taps from any starting kind return to that same kind`() {
        for (start in IntervalKind.entries) {
            var kind = start
            repeat(4) { kind = kind.next() }
            assertEquals(start, kind)
        }
    }
}
