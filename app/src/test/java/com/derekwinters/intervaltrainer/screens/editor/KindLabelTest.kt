package com.derekwinters.intervaltrainer.screens.editor

import androidx.compose.ui.graphics.Color
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/spec/screens.md` `SCREEN-012`, `SCREEN-017` (#136): the one kind-to-colour mapping the
 * schedule rows' dots and the round generator's duration labels share, resolved through
 * `colorRole()` (`CUE-030`) onto the design system's colours (`DS-050`).
 *
 * Every colour below is distinct, so a mapping that picked the wrong field — or a hard-coded
 * colour — cannot pass by coincidence.
 */
class KindLabelTest {

    private val colors = DesignSystemColors(
        work = Color(0xFF000001),
        recovery = Color(0xFF000002),
        neutral = Color(0xFF000003),
        bg = Color(0xFF000004),
        fg = Color(0xFF000005),
        dim = Color(0xFF000006),
        line = Color(0xFF000007),
        chip = Color(0xFF000008),
        destructive = Color(0xFF000009),
    )

    @Test
    fun `work's dot is the design system's work colour`() {
        assertEquals(colors.work, IntervalKind.WORK.dotColor(colors))
    }

    @Test
    fun `recovery's dot is the design system's recovery colour`() {
        assertEquals(colors.recovery, IntervalKind.RECOVERY.dotColor(colors))
    }

    @Test
    fun `warm-up and cool-down dots are the design system's neutral`() {
        assertEquals(colors.neutral, IntervalKind.WARM_UP.dotColor(colors))
        assertEquals(colors.neutral, IntervalKind.COOL_DOWN.dotColor(colors))
    }
}
