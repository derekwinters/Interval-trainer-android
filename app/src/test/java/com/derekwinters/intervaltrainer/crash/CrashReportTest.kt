package com.derekwinters.intervaltrainer.crash

import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/spec/service.md` `SVC-071`: what a crash report holds — the thread's name, the app's
 * version, the Android version, and the full stack trace, causes included.
 */
class CrashReportTest {

    private val context = CrashContext(
        versionName = "0.3.1",
        versionCode = 7,
        androidRelease = "15",
        sdkInt = 35,
    )

    private fun crash(): Throwable =
        IllegalStateException("boom", IOException("disk full"))

    @Test
    fun `report names the crashing thread`() {
        val report = formatCrashReport(context, threadName = "WorkoutTick", throwable = crash())

        assertTrue(report, report.contains("Thread: WorkoutTick"))
    }

    @Test
    fun `report carries the app version name and code`() {
        val report = formatCrashReport(context, threadName = "main", throwable = crash())

        assertTrue(report, report.contains("App version: 0.3.1 (7)"))
    }

    @Test
    fun `report carries the Android release and API level`() {
        val report = formatCrashReport(context, threadName = "main", throwable = crash())

        assertTrue(report, report.contains("Android version: 15 (API 35)"))
    }

    @Test
    fun `report carries the full stack trace, causes included`() {
        val throwable = crash()
        val report = formatCrashReport(context, threadName = "main", throwable = throwable)

        assertTrue(report, report.contains("java.lang.IllegalStateException: boom"))
        assertTrue(report, report.contains("Caused by: java.io.IOException: disk full"))
        // A frame from this test class, so the trace is the real one rather than only its message.
        assertTrue(report, report.contains("at ${CrashReportTest::class.java.name}.crash"))
    }
}
