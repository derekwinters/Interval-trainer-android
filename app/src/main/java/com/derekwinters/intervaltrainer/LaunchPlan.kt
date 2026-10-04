package com.derekwinters.intervaltrainer

/**
 * `docs/spec/screens.md` `SCREEN-090`, with `SCREEN-070` and `SCREEN-043`: what the app opens to,
 * and whether the crash report popup shows over it.
 */
data class LaunchPlan(val destination: StartDestination, val showCrashReport: Boolean)

/**
 * `docs/spec/screens.md` `SCREEN-090`: the popup shows exactly when a crash report exists —
 * independent of the first-run flag and of an active workout — and a report never changes the
 * destination, which stays [startDestination]'s. Pure, with no `android.*` import, and tested in
 * `LaunchPlanTest.kt`.
 */
fun launchPlan(firstRunSeen: Boolean, workoutActive: Boolean, crashReportExists: Boolean): LaunchPlan =
    LaunchPlan(
        destination = startDestination(firstRunSeen = firstRunSeen, workoutActive = workoutActive),
        showCrashReport = crashReportExists,
    )
