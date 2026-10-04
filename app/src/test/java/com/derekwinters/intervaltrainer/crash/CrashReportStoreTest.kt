package com.derekwinters.intervaltrainer.crash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `docs/spec/service.md` `SVC-072`: the one-file store a crash report is written to and read back
 * from. One crash is kept: a second write replaces the first.
 */
class CrashReportStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): CrashReportStore = CrashReportStore(File(folder.root, "crash-report.txt"))

    @Test
    fun `reads nothing when no crash has been recorded`() {
        assertNull(store().read())
    }

    @Test
    fun `reads back what was written`() {
        val store = store()

        store.write("first crash")

        assertEquals("first crash", store.read())
    }

    @Test
    fun `written twice, holds only the second report`() {
        val store = store()

        store.write("first crash, with a longer trace than the second")
        store.write("second crash")

        assertEquals("second crash", store.read())
        assertEquals(listOf("crash-report.txt"), folder.root.list()!!.toList())
    }

    @Test
    fun `a fresh store over the same file reads what an earlier one wrote`() {
        store().write("from the process that crashed")

        assertEquals("from the process that crashed", store().read())
    }

    @Test
    fun `delete removes the report`() {
        val store = store()
        store.write("a crash")

        store.delete()

        assertNull(store.read())
        assertFalse(File(folder.root, "crash-report.txt").exists())
    }

    @Test
    fun `delete with no report recorded does nothing`() {
        store().delete()

        assertNull(store().read())
    }
}
