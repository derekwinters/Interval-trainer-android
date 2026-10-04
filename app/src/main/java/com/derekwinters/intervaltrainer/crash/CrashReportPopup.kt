package com.derekwinters.intervaltrainer.crash

/** `docs/spec/screens.md` `SCREEN-092`–`094`. */
enum class CrashReportChoice { COPY, DISMISS, CLOSE }

data class CrashReportOutcome(val copiesReport: Boolean, val deletesReport: Boolean)

fun crashReportOutcome(choice: CrashReportChoice): CrashReportOutcome = TODO("#146: SCREEN-092–094")
