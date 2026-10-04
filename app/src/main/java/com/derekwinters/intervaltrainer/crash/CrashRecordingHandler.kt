package com.derekwinters.intervaltrainer.crash

/** `docs/spec/service.md` `SVC-073`. Stub: records, and does not yet hand off. */
class CrashRecordingHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { record(thread, throwable) }
    }
}
