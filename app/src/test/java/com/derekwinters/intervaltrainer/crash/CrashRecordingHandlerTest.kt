package com.derekwinters.intervaltrainer.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `docs/spec/service.md` `SVC-073` and its invariant: the handler records the crash, then always
 * hands off to the default handler it replaced, exactly once — whether recording succeeded or
 * threw.
 */
class CrashRecordingHandlerTest {

    private class RecordingPrevious : Thread.UncaughtExceptionHandler {
        val calls = mutableListOf<Pair<Thread, Throwable>>()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            calls += thread to throwable
        }
    }

    private val thread = Thread("crashing")
    private val crash = IllegalStateException("boom")

    @Test
    fun `records the crash, then hands off to the previous handler once`() {
        val order = mutableListOf<String>()
        val previous = Thread.UncaughtExceptionHandler { _, _ -> order += "previous" }
        val recorded = mutableListOf<Pair<Thread, Throwable>>()
        val handler = CrashRecordingHandler(previous) { t, e ->
            recorded += t to e
            order += "record"
        }

        handler.uncaughtException(thread, crash)

        assertEquals(listOf(thread to crash), recorded)
        assertEquals(listOf("record", "previous"), order)
    }

    @Test
    fun `passes the same thread and throwable to the previous handler`() {
        val previous = RecordingPrevious()
        val handler = CrashRecordingHandler(previous) { _, _ -> }

        handler.uncaughtException(thread, crash)

        assertEquals(1, previous.calls.size)
        assertSame(thread, previous.calls.single().first)
        assertSame(crash, previous.calls.single().second)
    }

    @Test
    fun `still hands off once when recording throws an exception`() {
        val previous = RecordingPrevious()
        val handler = CrashRecordingHandler(previous) { _, _ ->
            throw java.io.IOException("no space left on device")
        }

        handler.uncaughtException(thread, crash)

        assertEquals(1, previous.calls.size)
        assertSame(crash, previous.calls.single().second)
    }

    @Test
    fun `still hands off once when recording throws an error`() {
        val previous = RecordingPrevious()
        val handler = CrashRecordingHandler(previous) { _, _ ->
            throw OutOfMemoryError("formatting the trace")
        }

        handler.uncaughtException(thread, crash)

        assertEquals(1, previous.calls.size)
        assertSame(crash, previous.calls.single().second)
    }
}
