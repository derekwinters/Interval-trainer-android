package com.derekwinters.intervaltrainer.crash

/**
 * `docs/spec/screens.md` `SCREEN-092`–`094`: the three ways the crash report popup closes —
 * [COPY], [DISMISS], and [CLOSE] (system back or a tap outside, the dialog's own
 * `onDismissRequest`).
 */
enum class CrashReportChoice { COPY, DISMISS, CLOSE }

/** What a [CrashReportChoice] does to the report. Every choice also closes the popup. */
data class CrashReportOutcome(val copiesReport: Boolean, val deletesReport: Boolean)

/**
 * `docs/spec/screens.md` `SCREEN-092`–`094` and §8's invariant: only [CrashReportChoice.DISMISS]
 * deletes the report. Copy keeps it, so a popup closed by Copy shows again on the next launch.
 * Tested in `CrashReportPopupTest.kt`; `MainActivity.kt` applies the outcome.
 */
fun crashReportOutcome(choice: CrashReportChoice): CrashReportOutcome = when (choice) {
    CrashReportChoice.COPY -> CrashReportOutcome(copiesReport = true, deletesReport = false)
    CrashReportChoice.DISMISS -> CrashReportOutcome(copiesReport = false, deletesReport = true)
    CrashReportChoice.CLOSE -> CrashReportOutcome(copiesReport = false, deletesReport = false)
}
