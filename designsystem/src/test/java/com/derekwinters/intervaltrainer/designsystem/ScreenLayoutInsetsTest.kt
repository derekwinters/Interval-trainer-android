package com.derekwinters.intervaltrainer.designsystem

import android.graphics.Insets
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.WindowInsets.Builder as PlatformWindowInsetsBuilder
import android.view.WindowInsets.Type as PlatformWindowInsetsType

/**
 * `DS-080`: each of the three layouts in `docs/spec/design-system.md` §8 insets its own content by
 * the window's system-bar insets — the status bar and the navigation bar both — so no part of a
 * screen is laid out beneath either bar
 * ([#131](https://github.com/derekwinters/Interval-trainer-android/issues/131)).
 *
 * The fixture, and why it is built this way: Robolectric's simulated window reports no system-bar
 * insets of its own, so there is nothing to observe unless this test supplies them. It captures the
 * composition's own view ([LocalView], the `AndroidComposeView`) and dispatches a platform
 * `android.view.WindowInsets` carrying known status-bar and navigation-bar values straight to it,
 * which is what Compose's own `WindowInsetsHolder` listens on. That listener is only attached while a
 * composition actually *reads* window insets, which the layouts do not do today — hence
 * [ObserveSystemBarInsets], composed alongside the layout under test, emitting nothing and reading
 * `WindowInsets.systemBars` purely so the holder is listening and the dispatched values can be
 * read back. `the dispatched system bar insets reach the composition` asserts that read-back on its
 * own, so a failure of the fixture is distinguishable in CI from a failure of the layouts: if that
 * test is red, this file's mechanism is wrong and the layouts are not what is being reported on.
 *
 * What this cannot assert, stated rather than implied (see `docs/spec/design-system.md`'s
 * traceability note for `DS-080`): it observes the insets this test supplies, at one density and
 * one window size, with one representative arrangement of content per layout — a real device's
 * bars remain `DS-100`'s manual verification. And it says nothing about a *screen* applying an
 * inset modifier of its own, which this page's sixth invariant forbids: the screens live in
 * `:app`, which this module's test source set cannot see into (`ADR 0007`), the same reason
 * `DS-093` has no test.
 *
 * `@Config(sdk = [34])` matches [DesignSystemConsistencyTest] — one Robolectric-supported API
 * level, pinned rather than floating with `compileSdk` (`DS-092`, `BUILD-023`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenLayoutInsetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var hostView: View? = null
    private var observedTopInset: Int = NotObserved
    private var observedBottomInset: Int = NotObserved

    /**
     * Emits no layout node: it exists only to read `WindowInsets.systemBars` from inside the
     * composition, which is what attaches Compose's own insets listener to [LocalView], and to
     * record what that read returns so [showWithSystemBarInsets]'s dispatch can be checked.
     */
    @Composable
    private fun ObserveSystemBarInsets() {
        val density = LocalDensity.current
        val systemBars = WindowInsets.systemBars
        observedTopInset = systemBars.getTop(density)
        observedBottomInset = systemBars.getBottom(density)
    }

    /**
     * Shows [content] with [StatusBarInset] and [NavigationBarInset] dispatched to the composition
     * as system-bar insets, and returns the two values in pixels for the assertions to compare
     * against.
     */
    private fun showWithSystemBarInsets(content: @Composable () -> Unit): DispatchedInsets {
        composeTestRule.setContent {
            hostView = LocalView.current
            AppTheme {
                content()
                ObserveSystemBarInsets()
            }
        }
        composeTestRule.waitForIdle()

        // Read after the composition exists, so nothing here depends on the rule's density being
        // available before its first `setContent`.
        val dispatched = with(composeTestRule.density) {
            DispatchedInsets(
                topPx = StatusBarInset.roundToPx(),
                bottomPx = NavigationBarInset.roundToPx(),
            )
        }
        val view = requireNotNull(hostView) { "no composition view was captured to dispatch insets to" }
        composeTestRule.runOnUiThread {
            view.dispatchApplyWindowInsets(
                PlatformWindowInsetsBuilder()
                    .setInsets(
                        PlatformWindowInsetsType.systemBars(),
                        Insets.of(0, dispatched.topPx, 0, dispatched.bottomPx),
                    )
                    .setVisible(PlatformWindowInsetsType.systemBars(), true)
                    .build(),
            )
        }
        composeTestRule.waitForIdle()
        return dispatched
    }

    /** The fixture's own precondition — see this class's KDoc. Not `DS-080` itself. */
    @Test
    fun `the dispatched system bar insets reach the composition`() {
        val dispatched = showWithSystemBarInsets { FullBleedLayout { } }

        assertEquals(
            "the status-bar inset this test dispatched did not reach the composition; " +
                "the fixture is broken, not the layouts",
            dispatched.topPx,
            observedTopInset,
        )
        assertEquals(
            "the navigation-bar inset this test dispatched did not reach the composition; " +
                "the fixture is broken, not the layouts",
            dispatched.bottomPx,
            observedBottomInset,
        )
    }

    @Test
    fun `the full-bleed layout keeps its content clear of both system bars`() {
        val dispatched = showWithSystemBarInsets {
            FullBleedLayout {
                Text(text = TopProbe, modifier = Modifier.align(Alignment.TopStart))
                Text(text = BottomProbe, modifier = Modifier.align(Alignment.BottomStart))
            }
        }

        assertClearOfStatusBar("full-bleed", TopProbe, dispatched)
        assertClearOfNavigationBar("full-bleed", BottomProbe, dispatched)
    }

    @Test
    fun `the list layout keeps its content clear of both system bars`() {
        val dispatched = showWithSystemBarInsets {
            ListLayout(
                title = TopProbe,
                bottomSlot = { Text(text = BottomProbe) },
            ) {
                item { Text(text = "a list row") }
            }
        }

        assertClearOfStatusBar("list", TopProbe, dispatched)
        assertClearOfNavigationBar("list", BottomProbe, dispatched)
    }

    @Test
    fun `the form layout keeps its content clear of both system bars`() {
        val dispatched = showWithSystemBarInsets {
            FormLayout(
                primaryActionText = BottomProbe,
                onPrimaryAction = {},
                headerTitle = TopProbe,
            ) {
                Text(text = "a line of centred content")
            }
        }

        assertClearOfStatusBar("form", TopProbe, dispatched)
        assertClearOfNavigationBar("form", BottomProbe, dispatched)
    }

    /** `DS-080`, the status bar: [text]'s top edge starts at or past the dispatched status-bar inset. */
    private fun assertClearOfStatusBar(layout: String, text: String, dispatched: DispatchedInsets) {
        val top = composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "DS-080: the $layout layout laid \"$text\" out under the status bar — its top is at " +
                "${top}px, inside the ${dispatched.topPx}px ($StatusBarInset) status-bar inset",
            top >= dispatched.topPx.toFloat(),
        )
    }

    /**
     * `DS-080`, the navigation bar: [text]'s bottom edge is at or above the root's height less the
     * dispatched navigation-bar inset.
     */
    private fun assertClearOfNavigationBar(layout: String, text: String, dispatched: DispatchedInsets) {
        val bottom = composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.bottom
        val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().size.height
        val limit = (rootHeight - dispatched.bottomPx).toFloat()
        assertTrue(
            "DS-080: the $layout layout laid \"$text\" out under the navigation bar — its bottom " +
                "is at ${bottom}px in a ${rootHeight}px root, past the ${limit}px the " +
                "${dispatched.bottomPx}px ($NavigationBarInset) navigation-bar inset leaves it",
            bottom <= limit,
        )
    }
}

private data class DispatchedInsets(val topPx: Int, val bottomPx: Int)

/**
 * Deliberately larger than any padding the layouts already apply — `ScreenHeader`'s own vertical
 * `spacing.md` (12dp), the list layout's `spacing.lg` (16dp) bottom slot padding — so that a
 * layout passing these assertions is applying the insets rather than coincidentally clearing them
 * with spacing it already had. Kept well inside Robolectric's default window so the content under
 * test still has room to lay out.
 */
private val StatusBarInset = 64.dp
private val NavigationBarInset = 96.dp

private const val TopProbe = "top probe"
private const val BottomProbe = "bottom probe"
private const val NotObserved = -1
