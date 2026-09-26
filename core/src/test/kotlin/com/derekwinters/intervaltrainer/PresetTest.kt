package com.derekwinters.intervaltrainer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [isSavable] (`docs/spec/screens.md` `SCREEN-019`): the preset editor's Save is
 * enabled only while the preset being edited holds at least one interval, since `TIMER-013` refuses
 * to start a workout from one that holds none (#147).
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
}
