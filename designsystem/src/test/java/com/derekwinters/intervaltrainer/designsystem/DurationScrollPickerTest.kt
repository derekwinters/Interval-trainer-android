package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `DS-009`, as [#133](https://github.com/derekwinters/Interval-trainer-android/issues/133) found
 * it broken: the drum drew the row *after* the given value as the selected one, and reported that
 * neighbour back as a change the moment it appeared, so a 30-second interval became 31 by being
 * looked at. These assert the picker's behaviour — what it reports and which row it treats as
 * selected — not its feel, which stays a manual judgement (`docs/spec/design-system.md`
 * Traceability).
 *
 * `@Config(sdk = [34])` for the same reason `DesignSystemConsistencyTest` gives: one pinned
 * Robolectric API level, so a `compileSdk` bump does not change which `android-all` jar this needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DurationScrollPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Shows a picker that owns its duration the way both real callers do, recording each report. */
    private class Host(minutes: Int, seconds: Int) {
        var minutes by mutableIntStateOf(minutes)
        var seconds by mutableIntStateOf(seconds)
        var shown by mutableStateOf(true)
        val reports = mutableListOf<Pair<Int, Int>>()
    }

    /** The theme values the picker resolved against, read inside the composition that showed it. */
    private lateinit var timerTypography: TimerTypography
    private var colonWidth: Dp = Dp.Unspecified

    private fun show(host: Host) {
        composeTestRule.setContent {
            AppTheme {
                timerTypography = AppTheme.timerTypography
                colonWidth = AppTheme.spacing.lg
                if (host.shown) {
                    DurationScrollPicker(
                        minutes = host.minutes,
                        seconds = host.seconds,
                        onDurationChange = { m, s ->
                            host.reports += m to s
                            host.minutes = m
                            host.seconds = s
                        },
                        modifier = Modifier.testTag(PICKER_TAG),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** The style a text node was actually laid out in — what it rendered, not what it was scaled to. */
    private fun laidOutStyle(text: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style
    }

    private fun bounds(text: String): Rect =
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `showing the picker and dismissing it untouched reports nothing and changes nothing`() {
        // The preset editor's new-interval default (SCREEN-016): the case #133 saw become 0:31.
        val host = Host(minutes = 0, seconds = 30)
        show(host)

        host.shown = false
        composeTestRule.waitForIdle()

        assertEquals("the picker reported a change nobody made", emptyList<Pair<Int, Int>>(), host.reports)
        assertEquals(0, host.minutes)
        assertEquals(30, host.seconds)
    }

    @Test
    fun `the given value is the centred full-size row level with the colon`() {
        show(Host(minutes = 0, seconds = 30))

        val colon = bounds(":")
        val selected = bounds("30")
        val below = bounds("31")
        val above = bounds("29")

        assertTrue(
            "the selected row (centre y=${selected.center.y}) is not level with the colon " +
                "(centre y=${colon.center.y})",
            abs(selected.center.y - colon.center.y) <= 1f,
        )
        assertTrue(
            "the selected row is drawn smaller (height ${selected.height}) than the row below it " +
                "(height ${below.height}) — the drum is treating its neighbour as the selected value",
            selected.height > below.height,
        )
        assertTrue(
            "the selected row is drawn smaller (height ${selected.height}) than the row above it " +
                "(height ${above.height})",
            selected.height > above.height,
        )
    }

    @Test
    fun `scrolling one drum and then the other keeps the first drum's unit`() {
        val host = Host(minutes = 0, seconds = 30)
        show(host)
        val rowHeightPx = with(composeTestRule.density) { 80.dp.toPx() }

        composeTestRule.onNodeWithText("30", useUnmergedTree = true).performTouchInput {
            swipeUp(startY = centerY, endY = centerY - rowHeightPx, durationMillis = 500)
        }
        composeTestRule.waitForIdle()
        val secondsAfterFirstScroll = host.seconds
        assertNotEquals("scrolling the seconds drum did not change the seconds", 30, secondsAfterFirstScroll)
        assertEquals("scrolling the seconds drum changed the minutes", 0, host.minutes)

        composeTestRule.onNodeWithText("00", useUnmergedTree = true).performTouchInput {
            swipeUp(startY = centerY, endY = centerY - rowHeightPx, durationMillis = 500)
        }
        composeTestRule.waitForIdle()

        assertNotEquals("scrolling the minutes drum did not change the minutes", 0, host.minutes)
        assertEquals(
            "scrolling the minutes drum overwrote the seconds with a stale value; reports were ${host.reports}",
            secondsAfterFirstScroll,
            host.seconds,
        )
    }

    @Test
    fun `the picker is sized as one field - three 40dp rows tall and two 48dp drums wide`() {
        show(Host(minutes = 0, seconds = 30))

        val picker = composeTestRule.onNodeWithTag(PICKER_TAG).fetchSemanticsNode().boundsInRoot
        val (expectedHeight, expectedWidth) = with(composeTestRule.density) {
            (40.dp * 3).toPx() to (48.dp * 2 + colonWidth).toPx()
        }

        assertEquals("the picker's height (DS-009: three 40dp rows)", expectedHeight, picker.height, 1f)
        assertEquals(
            "the picker's width (DS-009: two 48dp drums and a spacing.lg colon)",
            expectedWidth,
            picker.width,
            1f,
        )
    }

    @Test
    fun `the digits and the colon are laid out in timer inline, not timer large`() {
        show(Host(minutes = 0, seconds = 30))

        for (text in listOf("30", "00", ":")) {
            val style = laidOutStyle(text)
            assertEquals(
                "\"$text\" is not laid out at timer.inline's size (DS-009, DS-032)",
                timerTypography.inline.fontSize,
                style.fontSize,
            )
            assertEquals(
                "\"$text\" is not laid out in timer.inline's typeface (DS-009, DS-032)",
                timerTypography.inline.fontFamily,
                style.fontFamily,
            )
        }
    }

    private companion object {
        const val PICKER_TAG = "duration-picker"
    }
}
