package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Clock
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/spec/service.md` `SVC-018`: the workout clock's process-wide holder. Plain JVM: nothing
 * here reads `SystemClock`, so no simulated Android runtime is needed (`BUILD-021`).
 */
class WorkoutClockTest {

    private val fixed = object : Clock {
        override fun nowMillis(): Long = 42L
    }

    @After
    fun resetHolder() {
        WorkoutClock.replaceForTest(null)
    }

    @Test
    fun `the default is the platform's elapsed-realtime clock`() {
        assertTrue(
            "SVC-012/SVC-018: with nothing replaced, the holder must give ElapsedRealtimeClock",
            WorkoutClock.current is ElapsedRealtimeClock,
        )
    }

    @Test
    fun `a replaced clock is what the holder gives`() {
        WorkoutClock.replaceForTest(fixed)
        assertSame(fixed, WorkoutClock.current)
    }

    @Test
    fun `resetting the replacement restores the default`() {
        WorkoutClock.replaceForTest(fixed)
        WorkoutClock.replaceForTest(null)
        assertTrue(WorkoutClock.current is ElapsedRealtimeClock)
    }
}
