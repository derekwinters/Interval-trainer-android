package com.derekwinters.intervaltrainer.service

import androidx.annotation.VisibleForTesting
import com.derekwinters.intervaltrainer.Clock

/**
 * `docs/spec/service.md` `SVC-018`: the one place [WorkoutService] and the running screen get the
 * [Clock] that a workout's deadlines are measured against. It is [ElapsedRealtimeClock]
 * (`SVC-012`) unless instrumented test code has replaced it.
 *
 * The holder exists so the screenshot test (`docs/spec/build.md` `BUILD-082`) can drive the real
 * service on a clock it controls, which is what makes the running and summary captures
 * deterministic. Per `SVC-018`'s invariant, nothing in the app's own code calls [replaceForTest].
 */
object WorkoutClock {

    private val default: Clock = ElapsedRealtimeClock()

    @Volatile
    private var replacement: Clock? = null

    /** The clock to read: the test's replacement if one is installed, the platform's otherwise. */
    val current: Clock
        get() = replacement ?: default

    /**
     * Installs [clock] in place of the default, or restores the default when [clock] is `null`.
     * Call it before the activity launches and the service is created, because each reads
     * [current] once.
     */
    @VisibleForTesting
    fun replaceForTest(clock: Clock?) {
        replacement = clock
    }
}
