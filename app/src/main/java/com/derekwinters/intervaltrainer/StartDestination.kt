package com.derekwinters.intervaltrainer

/**
 * `docs/spec/screens.md` `SCREEN-070`, `SCREEN-043`: the app's own three-way choice of where
 * `IntervalTrainerNavHost` (`MainActivity.kt`) starts, computed once, synchronously, from two
 * app-wide facts read independently of composition — whether a workout already exists
 * ([com.derekwinters.intervaltrainer.service.WorkoutServiceState], `SCREEN-043`) and whether the
 * first-run explanation has already been shown (`FirstRunStore`, `SCREEN-070`, `SCREEN-080`).
 *
 * A workout can only ever come to exist through home (`SCREEN-006`), which is itself unreachable
 * before the first-run explanation resolves (`SCREEN-072`'s "no way to skip past the explanation")
 * — so [workoutActive] and `!firstRunSeen` can only both be true if a process is somehow resumed
 * mid first-run with a workout already running, which cannot happen in v1. [startDestination]
 * still resolves that combination to [StartDestination.RUNNING] rather than leaving it undefined,
 * the same "an active workout always takes priority" rule `SCREEN-043`'s own "on every entry"
 * already states for every other screen in the app — the first-run explanation is no exception.
 *
 * This function has no `android.*` import and needs none to run, the same "pure function of state
 * and nothing else" split `WorkoutSession.handle` (`docs/spec/service.md` `SVC-014`) and
 * `notificationSettingsDeepLink` (`SVC-033`) already use for their own one isolatable piece of
 * Android-adjacent logic, tested here the same way, in `StartDestinationTest.kt`.
 */
enum class StartDestination {
    FIRST_RUN,
    RUNNING,
    HOME,
}

fun startDestination(firstRunSeen: Boolean, workoutActive: Boolean): StartDestination = when {
    workoutActive -> StartDestination.RUNNING
    !firstRunSeen -> StartDestination.FIRST_RUN
    else -> StartDestination.HOME
}
