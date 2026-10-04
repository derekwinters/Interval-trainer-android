package com.derekwinters.intervaltrainer.crash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/spec/screens.md` `SCREEN-092`–`094` and §8's invariant: what each way of closing the crash
 * report popup does to the report. Every one of them closes the popup; only Dismiss deletes.
 */
class CrashReportPopupTest {

    @Test
    fun `copy puts the report on the clipboard and keeps it`() {
        assertEquals(
            CrashReportOutcome(copiesReport = true, deletesReport = false),
            crashReportOutcome(CrashReportChoice.COPY),
        )
    }

    @Test
    fun `dismiss deletes the report and copies nothing`() {
        assertEquals(
            CrashReportOutcome(copiesReport = false, deletesReport = true),
            crashReportOutcome(CrashReportChoice.DISMISS),
        )
    }

    @Test
    fun `back or a tap outside neither copies nor deletes`() {
        assertEquals(
            CrashReportOutcome(copiesReport = false, deletesReport = false),
            crashReportOutcome(CrashReportChoice.CLOSE),
        )
    }

    @Test
    fun `only dismiss deletes the report`() {
        assertEquals(
            listOf(CrashReportChoice.DISMISS),
            CrashReportChoice.entries.filter { crashReportOutcome(it).deletesReport },
        )
    }
}
