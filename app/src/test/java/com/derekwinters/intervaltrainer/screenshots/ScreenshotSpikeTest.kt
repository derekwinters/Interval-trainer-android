package com.derekwinters.intervaltrainer.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.IntervalOutcome
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.ScheduleEntry
import com.derekwinters.intervaltrainer.TimerPhase
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.screens.editor.PresetEditorScreen
import com.derekwinters.intervaltrainer.screens.home.HomeScreen
import com.derekwinters.intervaltrainer.screens.running.RunningScreen
import com.derekwinters.intervaltrainer.screens.settings.SettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Throwaway feasibility spike: renders the real production screens with hard-coded state and
 * writes each to app/build/screenshots/<name>.png with Roborazzi. Not a test of behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi", sdk = [34])
class ScreenshotSpikeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tabata = Preset(
        id = "tabata",
        name = "Tabata",
        intervals = listOf(Interval(IntervalKind.WARM_UP, 120)) +
            List(8) { listOf(Interval(IntervalKind.WORK, 20), Interval(IntervalKind.RECOVERY, 10)) }.flatten() +
            listOf(Interval(IntervalKind.COOL_DOWN, 120)),
    )

    private val hills = Preset(
        id = "hills",
        name = "5×3 min hill repeats",
        intervals = listOf(Interval(IntervalKind.WARM_UP, 600)) +
            List(5) { listOf(Interval(IntervalKind.WORK, 180), Interval(IntervalKind.RECOVERY, 120)) }.flatten() +
            listOf(Interval(IntervalKind.COOL_DOWN, 300)),
    )

    private val easy = Preset(
        id = "easy",
        name = "Easy 30/30",
        intervals = List(10) { listOf(Interval(IntervalKind.WORK, 30), Interval(IntervalKind.RECOVERY, 30)) }.flatten(),
    )

    private val fixedClock = object : Clock {
        override fun nowMillis(): Long = 1_000_000L
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun home() {
        composeTestRule.setContent {
            AppTheme {
                HomeScreen(
                    presets = listOf(tabata, hills, easy),
                    onOpenSettings = {},
                    onNewPreset = {},
                    onEditPreset = {},
                    onStartPreset = {},
                )
            }
        }
        capture("home")
    }

    @Test
    fun runningPausedInSecondWorkInterval() {
        // hills: index 0 warm-up, 1 work, 2 recovery, 3 work (the second work interval).
        val schedule = hills.intervals.mapIndexed { i, interval ->
            ScheduleEntry(interval, if (i < 3) IntervalOutcome.FINISHED else IntervalOutcome.PENDING)
        }
        val state = CueTimerState(
            timer = TimerState.Paused(
                schedule = schedule,
                phase = TimerPhase.InInterval(index = 3),
                remainingMillis = 97_000L,
                accumulatedRunningMillis = (3 + 600 + 180 + 120 + 83) * 1_000L,
            ),
        )
        composeTestRule.setContent {
            AppTheme {
                RunningScreen(
                    state = state,
                    clock = fixedClock,
                    onPauseResume = {},
                    onSkip = {},
                    onStopConfirmed = {},
                    onToggleMute = {},
                )
            }
        }
        capture("running")
    }

    @Test
    fun presetEditor() {
        composeTestRule.setContent {
            AppTheme {
                PresetEditorScreen(preset = hills, onSave = {}, onBack = {})
            }
        }
        capture("editor")
    }

    @Test
    fun settings() {
        composeTestRule.setContent {
            AppTheme {
                SettingsScreen(
                    defaultMuted = false,
                    onDefaultMutedChange = {},
                    notificationPermissionGranted = true,
                    onOpenNotificationSettings = {},
                    onBack = {},
                )
            }
        }
        capture("settings")
    }
}
