package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.and
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.or
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `DS-091`: JVM assertions over the Compose semantics tree, run under Robolectric, against the
 * component gallery (`DS-095`, [ComponentGallery]) — the fixture that exists precisely so these
 * assertions have one screen to walk rather than one per component.
 *
 * `@Config(sdk = [34])` pins one Robolectric-supported API level rather than letting it float with
 * whatever `compileSdk` (35) happens to be, so a `compileSdk` bump does not silently change which
 * `android-all` jar this test needs (`DS-092`, `BUILD-023`). Nothing here needs an `Activity`:
 * [createComposeRule] hosts the composition directly, the documented Robolectric-native path for
 * Compose UI tests, per `docs/spec/design-system.md`'s Robolectric-scoped-to-Compose-screens note
 * (ADR 0005).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignSystemConsistencyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showGallery() {
        composeTestRule.setContent {
            AppTheme {
                ComponentGallery()
            }
        }
    }

    /**
     * `DS-005`–`008`: every icon-button role and `CountStepper`'s own `−`/`+` controls meet the
     * 48dp minimum touch target regardless of drawn size. This page's fourth invariant is what makes
     * this a loop over every tagged node the gallery renders rather than one assertion on a single
     * sampled instance — [TouchTargetTag] marks every icon-button example in
     * `ComponentGallery.kt`, and [CountStepperTag]'s descendants pick up `CountStepper`'s own two
     * buttons without `CountStepper.kt` needing a test-only parameter of its own.
     */
    @Test
    fun `every touch-target-scoped control meets the 48dp minimum`() {
        showGallery()

        // The default, merged tree (not `useUnmergedTree = true`): each icon button and each of
        // CountStepper's two `−`/`+` boxes is its own click-action merge boundary, so both remain
        // independently selectable here, each with its child glyph's text merged onto it — which
        // is exactly what the content-description test below relies on too.
        val matcher = hasTestTag(TouchTargetTag) or
            (hasClickAction() and hasAnyAncestor(hasTestTag(CountStepperTag)))
        val matches = composeTestRule.onAllNodes(matcher)
        val count = matches.fetchSemanticsNodes().size

        assertTrue(
            "expected at least one touch-target-scoped control in the gallery, found none",
            count > 0,
        )
        for (index in 0 until count) {
            matches[index]
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    /**
     * `DS-091`'s third clause: every icon-only control has a non-empty content description. Scans
     * every clickable node in the gallery — not only the touch-target-tagged ones above — and skips
     * a node only when it carries its own visible text (a labelled button reads its label, not a
     * content description); everything left is "icon-only" in `DS-091`'s sense and must carry one.
     */
    @Test
    fun `every icon-only clickable control has a non-empty content description`() {
        showGallery()

        val nodes = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(
            "expected at least one clickable control in the gallery, found none",
            nodes.isNotEmpty(),
        )

        for (node in nodes) {
            val hasVisibleText = node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.toString().isNotBlank() } == true
            if (hasVisibleText) continue

            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(separator = "")
                .orEmpty()
            assertTrue(
                "icon-only clickable node ${node.id} has no non-empty content description",
                description.isNotBlank(),
            )
        }
    }

    /**
     * `DS-091`'s second clause, "a component's position in the root across the screens that share
     * it": no v1 screen exists yet to compare across (`#33`), so this checks the one thing that is
     * true today — `ScreenHeader` (`DS-020`) sits near the top of its own root, per the gallery's
     * own list layout, which is what every future screen sharing `ScreenHeader` will also be
     * asserting against once more than one screen exists. Compares `boundsInRoot` against a fixed,
     * generous threshold rather than calling `onRoot()` for the root's own size: with the dialog
     * shown by default (`DS-095`), more than one root is active, and `onRoot()` does not promise
     * which one it returns when there is more than one — `boundsInRoot` needs no such call, since
     * it is already relative to whichever root the header's own node belongs to.
     */
    @Test
    fun `the screen header sits near the top of its root`() {
        showGallery()

        val header = composeTestRule.onNodeWithText("Component gallery").fetchSemanticsNode()
        val maxHeaderTop = with(composeTestRule.density) { 200.dp.toPx() }

        assertTrue(
            "expected the ScreenHeader title near the top of its root " +
                "(top=${header.boundsInRoot.top}px, threshold=${maxHeaderTop}px)",
            header.boundsInRoot.top < maxHeaderTop,
        )
    }
}
