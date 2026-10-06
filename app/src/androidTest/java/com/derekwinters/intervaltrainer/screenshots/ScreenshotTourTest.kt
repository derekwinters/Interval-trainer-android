package com.derekwinters.intervaltrainer.screenshots

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.MainActivity
import com.derekwinters.intervaltrainer.TimerPhase
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.service.WorkoutClock
import com.derekwinters.intervaltrainer.service.WorkoutServiceState
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * `docs/spec/build.md` `BUILD-081`, `BUILD-082`: walks the real app through its six screens on a
 * fresh install and captures each one, status and navigation bars included, for
 * `screenshots.yml`.
 *
 * Everything goes through the real app: the activity, the seeded example presets, and the
 * foreground service started by tapping Start. The one thing the test owns is the workout clock
 * (`docs/spec/service.md` `SVC-018`). It is replaced before the activity launches, so the running
 * and summary screens show the same times on every run. Section 11's third invariant keeps this
 * test usable as a launch-and-start-a-preset smoke test, so it never publishes workout state
 * itself.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTourTest {

    private val clock = ManualClock(startMillis = 1_000_000L)

    /** `SVC-018`: installed before the compose rule launches the activity, reset afterwards. */
    private val clockRule = object : ExternalResource() {
        override fun before() = WorkoutClock.replaceForTest(clock)
        override fun after() = WorkoutClock.replaceForTest(null)
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(clockRule)
        .around(composeRule)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outputDir = File(instrumentation.targetContext.filesDir, "screenshots")

    @Test
    fun capturesTheSixScreens() {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        // 1. First run: a fresh install opens here (SCREEN-070).
        waitForText("Notifications while you work out")
        capture("01-first-run.png")

        // 2. Home, with the two seeded example presets. POST_NOTIFICATIONS is already granted, so
        // Continue goes straight to home with no system dialog.
        composeRule.onNodeWithText("Continue").performClick()
        waitForText("Short Example")
        waitForText("Long Example")
        capture("02-home.png")

        // 3. The preset editor with Short Example loaded.
        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        waitForText("SCHEDULE")
        capture("03-editor.png")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Short Example")

        // 4. Running, paused mid-workout. Short Example is warm-up 3:00, then work 1:00 and
        // recovery 2:00 three times, then cool-down 3:00 (SCHEMA-031). Advance one deadline at a
        // time, waiting for the real service to publish each interval.
        composeRule.onAllNodesWithText("Start")[0].performClick()
        waitForPhase(TimerPhase.LeadIn(0))
        advanceTo(3_000L, TimerPhase.InInterval(0)) // the lead-in (TIMER-030)
        advanceTo(180_000L, TimerPhase.InInterval(1)) // warm-up
        advanceTo(60_000L, TimerPhase.InInterval(2)) // work, round 1
        advanceTo(120_000L, TimerPhase.InInterval(3)) // recovery, round 1
        clock.advance(22_000L) // 22 s into round 2's work: 0:38 of 1:00 left
        composeRule.onNodeWithContentDescription("Pause").performClick()
        waitForTimer("the workout to pause") { it is TimerState.Paused }
        waitForContentDescription("Resume")
        capture("04-running-paused.png")

        // 5. The summary, after a confirmed stop: one round of three, 6:25 (TIMER-054).
        composeRule.onNodeWithContentDescription("Stop").performClick()
        composeRule.onNode(hasText("Stop") and hasClickAction()).performClick()
        waitForTimer("the workout to end") { it is TimerState.Ended }
        waitForText("Done")
        capture("05-summary.png")

        // 6. Settings, from home.
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Short Example")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForContentDescription("Default mute")
        capture("06-settings.png")
    }

    private fun advanceTo(millis: Long, phase: TimerPhase) {
        clock.advance(millis)
        waitForPhase(phase)
    }

    private fun waitForPhase(phase: TimerPhase) = waitForTimer("phase $phase") { timer ->
        (timer as? TimerState.Running)?.phase == phase
    }

    private fun waitForTimer(what: String, condition: (TimerState) -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
                condition(WorkoutServiceState.current.value.timer)
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Timed out waiting for $what; the timer is ${WorkoutServiceState.current.value.timer}",
                error,
            )
        }
    }

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForContentDescription(description: String) =
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }

    /** Waits for the app and the system UI to be idle, then saves the whole screen as a PNG. */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(IDLE_QUIET_MILLIS, WAIT_MILLIS)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("UiAutomation returned no screenshot for $name")
        FileOutputStream(File(outputDir, name)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "could not write $name" }
        }
        bitmap.recycle()
    }

    /** A clock that moves only when the test moves it. */
    private class ManualClock(startMillis: Long) : Clock {
        private val now = AtomicLong(startMillis)
        override fun nowMillis(): Long = now.get()
        fun advance(millis: Long) {
            now.addAndGet(millis)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 20_000L
        const val IDLE_QUIET_MILLIS = 1_000L
    }
}
