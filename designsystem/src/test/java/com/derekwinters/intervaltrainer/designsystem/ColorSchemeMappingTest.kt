package com.derekwinters.intervaltrainer.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `DS-051`: the bespoke palette mapped onto Material 3's `ColorScheme` roles. Runs on the JVM
 * alone (`BUILD-021`) — `androidx.compose.material3.darkColorScheme()` and
 * `androidx.compose.ui.graphics.Color` are plain, JVM-computable types that call into no Android
 * runtime, so this needs no Robolectric, unlike the screen-level semantics-tree tests `DS-091`
 * and `DS-093` name for a later issue.
 */
class ColorSchemeMappingTest {
    private val colors = DesignSystemColors.Default
    private val scheme = designSystemColorScheme(colors)

    @Test
    fun `surface reads bg`() {
        assertEquals(colors.bg, scheme.surface)
    }

    @Test
    fun `onSurface reads fg`() {
        assertEquals(colors.fg, scheme.onSurface)
    }

    @Test
    fun `primary reads work`() {
        assertEquals(colors.work, scheme.primary)
    }

    @Test
    fun `outline reads line`() {
        assertEquals(colors.line, scheme.outline)
    }

    @Test
    fun `surfaceVariant reads chip`() {
        assertEquals(colors.chip, scheme.surfaceVariant)
    }

    @Test
    fun `error reads destructive`() {
        assertEquals(colors.destructive, scheme.error)
    }
}
