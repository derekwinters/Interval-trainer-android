package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/spec/screens.md` `SCREEN-090`, with `SCREEN-070` and `SCREEN-043`: what the app opens to
 * and whether the crash report popup shows over it. The popup shows exactly when a report exists,
 * independent of the first-run flag and of an active workout, and a report never changes the
 * destination.
 */
class LaunchPlanTest {

    private val booleans = listOf(false, true)

    @Test
    fun `the popup shows exactly when a crash report exists, whatever else is true`() {
        for (firstRunSeen in booleans) for (workoutActive in booleans) for (reportExists in booleans) {
            val plan = launchPlan(
                firstRunSeen = firstRunSeen,
                workoutActive = workoutActive,
                crashReportExists = reportExists,
            )
            assertEquals(
                "firstRunSeen=$firstRunSeen workoutActive=$workoutActive reportExists=$reportExists",
                reportExists,
                plan.showCrashReport,
            )
        }
    }

    @Test
    fun `a crash report never changes the start destination`() {
        for (firstRunSeen in booleans) for (workoutActive in booleans) for (reportExists in booleans) {
            val plan = launchPlan(
                firstRunSeen = firstRunSeen,
                workoutActive = workoutActive,
                crashReportExists = reportExists,
            )
            assertEquals(
                "firstRunSeen=$firstRunSeen workoutActive=$workoutActive reportExists=$reportExists",
                startDestination(firstRunSeen = firstRunSeen, workoutActive = workoutActive),
                plan.destination,
            )
        }
    }

    @Test
    fun `a fresh install with a crash report opens on first run with the popup over it`() {
        assertEquals(
            LaunchPlan(destination = StartDestination.FIRST_RUN, showCrashReport = true),
            launchPlan(firstRunSeen = false, workoutActive = false, crashReportExists = true),
        )
    }
}
