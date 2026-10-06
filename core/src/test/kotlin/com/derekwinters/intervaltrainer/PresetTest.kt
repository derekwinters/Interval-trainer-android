package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [isSavable] (`docs/spec/screens.md` `SCREEN-019`): the preset editor's Save is
 * enabled only while the preset being edited holds at least one interval, since `TIMER-013` refuses
 * to start a workout from one that holds none (#147). And for [copyName] and [duplicate]
 * (`SCREEN-019b`, #163): what a duplicate is called and what it carries.
 */
class PresetTest {

    private val work30 = Interval(IntervalKind.WORK, durationSeconds = 30)

    @Test
    fun `a preset with no intervals cannot be saved`() {
        assertFalse(Preset(id = "p", name = "Empty", intervals = emptyList()).isSavable())
    }

    @Test
    fun `a preset with one interval can be saved`() {
        assertTrue(Preset(id = "p", name = "One", intervals = listOf(work30)).isSavable())
    }

    @Test
    fun `a copy is named with a plain (copy) suffix when nothing has that name`() {
        assertEquals("Hills (copy)", copyName("Hills", takenNames = listOf("Hills", "Short Example")))
    }

    @Test
    fun `a copy is numbered from 2 when the plain (copy) name is taken`() {
        assertEquals("Hills (copy 2)", copyName("Hills", takenNames = listOf("Hills", "Hills (copy)")))
    }

    @Test
    fun `a copy counts on to (copy 3) when (copy 2) is taken too`() {
        assertEquals(
            "Hills (copy 3)",
            copyName("Hills", takenNames = listOf("Hills", "Hills (copy)", "Hills (copy 2)")),
        )
    }

    @Test
    fun `a copy takes the first free number when the numbering has a gap`() {
        assertEquals(
            "Hills (copy 2)",
            copyName("Hills", takenNames = listOf("Hills", "Hills (copy)", "Hills (copy 3)")),
        )
    }

    @Test
    fun `a duplicate has the new id, the copy name and the same intervals as the preset it copies`() {
        val edited = Preset(
            id = "original",
            name = "Hills",
            intervals = listOf(work30, Interval(IntervalKind.RECOVERY, durationSeconds = 60)),
        )

        val copy = edited.duplicate(newId = "copy-id", takenNames = listOf("Hills"))

        assertEquals(Preset(id = "copy-id", name = "Hills (copy)", intervals = edited.intervals), copy)
    }
}
