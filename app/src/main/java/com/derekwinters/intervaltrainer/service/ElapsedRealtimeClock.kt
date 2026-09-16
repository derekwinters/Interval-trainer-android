package com.derekwinters.intervaltrainer.service

import android.os.SystemClock
import com.derekwinters.intervaltrainer.Clock

/**
 * `SVC-012`: `:core`'s [Clock] seam, against the platform's own monotonic clock —
 * [SystemClock.elapsedRealtime], the reading `docs/spec/timer.md`'s deadlines are measured
 * against. It counts while the device sleeps and no wall-clock change can move it (`ADR 0002`).
 *
 * *(manual: a platform-clock choice; the arithmetic it feeds is `:core`'s own and is tested there
 * against a fake, per `SVC-012`.)*
 */
class ElapsedRealtimeClock : Clock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
