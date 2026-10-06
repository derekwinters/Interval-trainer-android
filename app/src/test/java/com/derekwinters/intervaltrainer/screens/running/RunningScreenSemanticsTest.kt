package com.derekwinters.intervaltrainer.screens.running

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.IntervalOutcome
import com.derekwinters.intervaltrainer.ScheduleEntry
import com.derekwinters.intervaltrainer.TimerPhase
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.runningScreenContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `docs/spec/screens.md` `SCREEN-034`–`036` (#162): what TalkBack reads on the running screen,
 * asserted against the Compose semantics tree — the only place those labels exist, which is why
 * this is one of the `:app` tests `docs/spec/build.md` `BUILD-023` allows Robolectric for.
 *
 * A "focus stop" here is what TalkBack lands on: a node in the merged semantics tree that carries a
 * content description or text and is not itself merged into an ancestor. The screen is shown
 * paused, so nothing ticks on its own and every change below is one this test makes; a change to
 * the held remaining time stands in for a tick, since the screen reads both the same way.
 *
 * `@Config(sdk = [34])`, the same pinned API level `:designsystem`'s semantics tests use, so this
 * needs no `android-all` jar CI does not already pre-fetch (`DS-092`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunningScreenSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clock = object : Clock {
        override fun nowMillis(): Long = 0L
    }

    private val intervals = listOf(
        Interval(IntervalKind.WARM_UP, 60),
        Interval(IntervalKind.WORK, 45),
        Interval(IntervalKind.RECOVERY, 30),
        Interval(IntervalKind.WORK, 45),
        Interval(IntervalKind.RECOVERY, 90),
        Interval(IntervalKind.WORK, 45),
        Interval(IntervalKind.RECOVERY, 30),
        Interval(IntervalKind.WORK, 45),
        Interval(IntervalKind.COOL_DOWN, 120),
    )

    /** Paused inside the interval at [index], with [remainingSeconds] of it left. */
    private fun pausedAt(index: Int, remainingSeconds: Int): CueTimerState = CueTimerState(
        timer = TimerState.Paused(
            schedule = intervals.mapIndexed { i, interval ->
                ScheduleEntry(interval, if (i < index) IntervalOutcome.FINISHED else IntervalOutcome.PENDING)
            },
            phase = TimerPhase.InInterval(index),
            remainingMillis = remainingSeconds * 1_000L,
            accumulatedRunningMillis = 0L,
        ),
    )

    private var state by mutableStateOf(pausedAt(index = 3, remainingSeconds = 32))

    private fun show() {
        composeTestRule.setContent {
            AppTheme {
                RunningScreen(
                    state = state,
                    clock = clock,
                    onPauseResume = {},
                    onSkip = {},
                    onStopConfirmed = {},
                    onToggleMute = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun SemanticsNode.label(): String {
        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        return (descriptions + texts).joinToString(" ") { it.trim() }
    }

    private fun SemanticsNode.isMergedIntoAncestor(): Boolean {
        var ancestor = parent
        while (ancestor != null) {
            if (ancestor.config.isMergingSemanticsOfDescendants) return true
            ancestor = ancestor.parent
        }
        return false
    }

    private fun SemanticsNode.isLiveRegion(): Boolean =
        config.getOrNull(SemanticsProperties.LiveRegion) != null

    private fun allNodes(useUnmergedTree: Boolean): List<SemanticsNode> = composeTestRule
        .onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = useUnmergedTree)
        .fetchSemanticsNodes(atLeastOneRootRequired = true)

    /** Every focus stop's label, live regions aside — `SCREEN-035`'s announcement is asserted on its own. */
    private fun focusStopLabels(): List<String> = allNodes(useUnmergedTree = false)
        .filter { !it.isMergedIntoAncestor() && !it.isLiveRegion() }
        .map { it.label() }
        .filter { it.isNotEmpty() }

    private fun liveRegions(): List<SemanticsNode> = allNodes(useUnmergedTree = false).filter { it.isLiveRegion() }

    private fun totalLeft(): String =
        formatSeconds((state.timer.runningScreenContent(clock)!!.totalRemainingMillis / 1_000L).toInt())

    @Test
    fun `the ring is one focus stop read as kind, remaining and duration`() {
        show()

        val ringStops = focusStopLabels().filter { it.contains("remaining") }
        assertEquals(
            "SCREEN-034: the ring must be exactly one focus stop reading \"{kind}, {remaining} remaining " +
                "of {duration}\". Every focus stop: ${focusStopLabels()}",
            listOf("Work, 0:32 remaining of 0:45"),
            ringStops,
        )

        // The merged tree is what accessibility services read. The unmerged tree is not checked:
        // it deliberately keeps the children `clearAndSetSemantics` replaced, so it shows what is
        // drawn, not what TalkBack reads.
        val labels = allNodes(useUnmergedTree = false).map { it.label() }
        for (part in listOf("Work", "0:32", "of 0:45")) {
            assertEquals(
                "SCREEN-034: the ring's \"$part\" must not be a node of its own. Every label: $labels",
                emptyList<String>(),
                labels.filter { it == part },
            )
        }
    }

    @Test
    fun `the interval change is one polite live region that a tick does not change`() {
        show()

        fun liveAnnouncement(): List<Pair<LiveRegionMode?, String>> =
            liveRegions().map { it.config.getOrNull(SemanticsProperties.LiveRegion) to it.label() }

        assertEquals(
            "SCREEN-035: exactly one polite live region, holding the current interval's kind and duration.",
            listOf(LiveRegionMode.Polite to "Work, 0:45"),
            liveAnnouncement(),
        )

        // A tick: the remaining time moves, the interval does not.
        state = pausedAt(index = 3, remainingSeconds = 31)
        composeTestRule.waitForIdle()
        assertEquals(
            "SCREEN-034: the ring's description follows the remaining time.",
            listOf("Work, 0:31 remaining of 0:45"),
            focusStopLabels().filter { it.contains("remaining") },
        )
        assertEquals(
            "SCREEN-035: a tick must not change the live region, so nothing is announced per tick.",
            listOf(LiveRegionMode.Polite to "Work, 0:45"),
            liveAnnouncement(),
        )

        // The interval changes.
        state = pausedAt(index = 4, remainingSeconds = 90)
        composeTestRule.waitForIdle()
        assertEquals(
            "SCREEN-035: an interval change must change the live region to the new interval.",
            listOf(LiveRegionMode.Polite to "Recovery, 1:30"),
            liveAnnouncement(),
        )
    }

    @Test
    fun `total left and each schedule row are one stop each and the strip is hidden`() {
        show()

        val expected = listOf(
            "Mute",
            "Total left ${totalLeft()}",
            // The past list, oldest first.
            "Work, 0:45",
            "Recovery, 0:30",
            "Work, 0:32 remaining of 0:45",
            // The upcoming list, next first.
            "Recovery, 1:30",
            "Work, 0:45",
            "Recovery, 0:30",
            "Work, 0:45",
            "Skip",
            "Resume",
            "Stop",
        )
        // Compared as sorted lists: this asserts which stops exist and how each reads, not
        // TalkBack's traversal order, which this page does not specify.
        assertEquals(
            "SCREEN-036: \"Total left\" and each row are one stop with a combined label, the strip " +
                "contributes no stop, and nothing else is read.",
            expected.sorted(),
            focusStopLabels().sorted(),
        )
    }
}
