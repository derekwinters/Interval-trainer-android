package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/spec/screens.md` `SCREEN-070`, `SCREEN-043`: the NavHost's start-destination decision,
 * combining whether a workout already exists with whether the first-run explanation has already
 * been shown, in isolation from Compose, `WorkoutServiceState` or DataStore.
 */
class StartDestinationTest {

    @Test
    fun `lands on first run when it has never been seen and no workout is active`() {
        assertEquals(
            StartDestination.FIRST_RUN,
            startDestination(firstRunSeen = false, workoutActive = false),
        )
    }

    @Test
    fun `lands on home once first run has been seen and no workout is active`() {
        assertEquals(
            StartDestination.HOME,
            startDestination(firstRunSeen = true, workoutActive = false),
        )
    }

    @Test
    fun `an active workout always wins, even if first run were somehow unseen`() {
        assertEquals(
            StartDestination.RUNNING,
            startDestination(firstRunSeen = false, workoutActive = true),
        )
        assertEquals(
            StartDestination.RUNNING,
            startDestination(firstRunSeen = true, workoutActive = true),
        )
    }
}
