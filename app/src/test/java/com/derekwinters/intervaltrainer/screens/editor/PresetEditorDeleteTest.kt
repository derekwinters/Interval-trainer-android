package com.derekwinters.intervaltrainer.screens.editor

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `docs/spec/screens.md` `SCREEN-019c`, `SCREEN-019d` (#164): the preset editor's Delete preset
 * button and its confirmation dialog, asserted against the composition — one of the `:app` tests
 * `docs/spec/build.md` `BUILD-023` allows Robolectric for.
 *
 * The editor does no I/O of its own (its `NavHost` entry in `MainActivity` does), so "the preset is
 * deleted" here is [PresetEditorScreen] asking for it through `onDelete`, and "the preset is
 * intact" is that request never being made. That the store then removes it and leaves every other
 * preset alone is `RoomPresetStoreTest.kt`'s.
 *
 * The host activity is registered with Robolectric for the same reason, and in the same way, as
 * `RunningScreenSemanticsTest.kt` explains. `@Config(sdk = [34])` is the API level CI already
 * pre-fetches (`DS-092`); the tall screen keeps every item of the editor's lazy list composed, so
 * an absent button is absent rather than merely scrolled away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PresetEditorDeleteTest {

    private val composeTestRule = createComposeRule()

    private val hostActivity = object : ExternalResource() {
        override fun before() {
            val application = RuntimeEnvironment.getApplication()
            shadowOf(application.packageManager)
                .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity).around(composeTestRule)

    private val tabata = Preset(
        id = "tabata",
        name = "Tabata",
        intervals = listOf(
            Interval(IntervalKind.WORK, 20),
            Interval(IntervalKind.RECOVERY, 10),
        ),
    )

    private var deleteRequests = 0

    /** A saved preset: the caller passes a delete handler, as `MainActivity` does for one. */
    private fun showSaved(preset: Preset = tabata) {
        composeTestRule.setContent {
            AppTheme {
                PresetEditorScreen(
                    preset = preset,
                    onSave = {},
                    onBack = {},
                    onDuplicate = {},
                    onDelete = { deleteRequests++ },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a saved preset offers Delete preset`() {
        showSaved()

        composeTestRule.onNodeWithText("Delete preset").assertExists()
    }

    @Test
    fun `a new unsaved preset does not offer Delete preset`() {
        composeTestRule.setContent {
            AppTheme {
                PresetEditorScreen(
                    preset = Preset(id = "new-one", name = "", intervals = emptyList()),
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete preset").assertDoesNotExist()
    }

    @Test
    fun `Delete preset asks for confirmation with the exact strings`() {
        showSaved()

        composeTestRule.onNodeWithText("Delete preset").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete \"Tabata\"?").assertExists()
        composeTestRule.onNodeWithText("This can't be undone.").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()
        composeTestRule.onNodeWithText("Delete").assertExists()
        assertEquals("opening the dialog must not delete anything", 0, deleteRequests)
    }

    @Test
    fun `Cancel closes the dialog, deletes nothing and leaves the editor open`() {
        showSaved()

        composeTestRule.onNodeWithText("Delete preset").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("This can't be undone.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Edit preset").assertExists()
        composeTestRule.onNodeWithText("Delete preset").assertExists()
        assertEquals(0, deleteRequests)
    }

    @Test
    fun `Delete in the dialog asks for the preset to be deleted, once`() {
        showSaved()

        composeTestRule.onNodeWithText("Delete preset").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, deleteRequests)
    }

    /** §2's invariant: the dialog names the preset as saved, never as edited. */
    @Test
    fun `the dialog names the saved preset even after an unsaved rename`() {
        showSaved()

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delete preset").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete \"Tabata\"?").assertExists()
        composeTestRule.onNodeWithText("Delete \"Renamed\"?").assertDoesNotExist()
    }
}
