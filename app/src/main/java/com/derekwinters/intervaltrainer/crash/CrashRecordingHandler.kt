package com.derekwinters.intervaltrainer.crash

/**
 * `docs/spec/service.md` `SVC-073` and its invariant: the process's default uncaught-exception
 * handler, installed once at process start (`SVC-070`, `IntervalTrainerApplication.kt`). It
 * [record]s the crash, then always hands off to [previous] — the default handler it replaced —
 * exactly once, so the process dies as it would have without it.
 *
 * Anything [record] throws, [Error]s included, is swallowed: the report is lost, the crash is not.
 * The hand-off is in a `finally` so that nothing about recording can skip it.
 *
 * [previous] is never `null` on Android, which installs its own default handler before any app
 * code runs. If it were, there would be nothing to hand off to, and the stack trace is printed the
 * way the JVM itself does when no default handler exists.
 */
class CrashRecordingHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            record(thread, throwable)
        } catch (ignored: Throwable) {
            // SVC-073: failing to record a crash must never hide or change the crash itself.
        } finally {
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                throwable.printStackTrace()
            }
        }
    }
}
