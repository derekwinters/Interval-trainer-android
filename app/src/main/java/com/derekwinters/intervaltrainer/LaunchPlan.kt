package com.derekwinters.intervaltrainer

/** `docs/spec/screens.md` `SCREEN-090`. */
data class LaunchPlan(val destination: StartDestination, val showCrashReport: Boolean)

fun launchPlan(firstRunSeen: Boolean, workoutActive: Boolean, crashReportExists: Boolean): LaunchPlan =
    TODO("#146: SCREEN-090")
