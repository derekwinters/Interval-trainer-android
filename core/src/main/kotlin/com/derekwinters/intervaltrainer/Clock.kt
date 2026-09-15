package com.derekwinters.intervaltrainer

/**
 * A source of monotonic time, for the timer's deadlines (`TIMER-020`–`025`) and nothing else: no
 * wall-clock reading and no calendar.
 *
 * `:core` defines this seam and does not implement it (ADR 0005). A fake substitutes it in
 * `:core`'s own tests — turning a thirty-four-minute workout into a millisecond of test time —
 * and `:app`'s foreground service implements it against `SystemClock.elapsedRealtime()`
 * (`SVC-012`).
 */
interface Clock {
    /** The current reading, in milliseconds, of a clock that only ever moves forward. */
    fun nowMillis(): Long
}
