package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * The closed set of three screen layouts (`DS-070`–`079`): **list** ([ListLayout]), **full-bleed**
 * ([FullBleedLayout]), and **form** ([FormLayout]). Per this page's first invariant in
 * `docs/spec/design-system.md`, a screen does not arrange its own header, spacing and action
 * placement — it fills one of these three layouts' named slots. Each slot below is typed to the
 * shape `docs/spec/design-system.md` §8 actually names, rather than a single generic
 * `content: @Composable () -> Unit` a call site could fill with anything: a `ScreenHeader` slot is
 * expressed as `ScreenHeader`'s own parameters (so the layout itself calls `ScreenHeader`, and a
 * screen cannot substitute a raw `TopAppBar` — `DS-021`), the list layout's scrollable content is a
 * `LazyListScope` receiver (`LazyColumn`'s own content shape, matching "a collection of like items"
 * — `DS-072`), and the form layout's one primary action is expressed as `PrimaryButton`'s own
 * parameters (`DS-001`), not an arbitrary composable slot a screen could fill with any button.
 *
 * A structural check asserting a screen's root composable is one of these three (`DS-093`) is a
 * later issue's job (screen implementation, `#78`) — this file only builds the layouts themselves,
 * per [issue #76](https://github.com/derekwinters/Interval-trainer-android/issues/76).
 */

/**
 * The **list** layout (`DS-071`–`072`): `ScreenHeader`, scrollable list content, and an optional
 * bottom action or FAB. Used when a screen's primary content is a collection of like items browsed
 * top-to-bottom and acted on individually — home's preset list, the preset editor, settings,
 * summary (`DS-072`).
 *
 * `content` is `LazyListScope.() -> Unit`, `LazyColumn`'s own content shape, so the slot can only
 * hold scrollable list content, never an arbitrary composable.
 *
 * `bottomSlot` is `DS-071`'s "optional bottom action or FAB" — deliberately left as one composable
 * slot rather than two typed ones, since the requirement itself names either shape interchangeably.
 * It is anchored to the layout's bottom-trailing corner, so a `FabIconButton` (home's "new preset",
 * `SCREEN-008`) reads as a floating action, and a full-width bottom action sizes itself with its own
 * `Modifier.fillMaxWidth()`.
 *
 * **`DS-079`**: a list-layout screen's FAB or bottom action is composed through this `bottomSlot`,
 * never through `ScreenHeader`'s own optional `fab` parameter — see `ScreenHeader.kt`'s doc comment,
 * which deferred this exact question to this layout. `ScreenHeader`'s `fab` parameter is not used by
 * this composable's own call to `ScreenHeader`.
 *
 * `onBack` passes straight through to `ScreenHeader`'s own `onBack` (`DS-013`): home (`SCREEN-001`)
 * leaves it unset, the preset editor (`SCREEN-010`) passes its own back navigation.
 */
@Composable
fun ListLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingAction: ScreenHeaderAction? = null,
    bottomSlot: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(title = title, onBack = onBack, trailingAction = trailingAction)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    content = content,
                )
            }
            if (bottomSlot != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(spacing.lg),
                ) {
                    bottomSlot()
                }
            }
        }
    }
}

/**
 * The **full-bleed** layout (`DS-073`–`074`): a free composition, with no standard list scaffold.
 * Used when the screen's whole purpose is one focal element that a header-and-list frame would
 * compete with rather than support — the running screen's countdown (`DS-074`).
 *
 * What makes this a *layout* rather than simply the absence of one: it still applies the token
 * layer's own background (`colors.bg`, `DS-050`) rather than leaving Material's baseline surface
 * default in place, and it still fills the screen edge-to-edge with no scaffold insets — both
 * properties every screen gets structurally, per this page's invariant that a screen does not
 * arrange its own background. Positioning everything inside that surface is genuinely free: `content`
 * is a `BoxScope` receiver so a screen can align its own elements (a ring centred, a rail to one
 * side, a control row at the bottom) however it needs to, including composing its own `ScreenHeader`
 * inside this free composition if the screen calls for one (`DS-062`).
 */
@Composable
fun FullBleedLayout(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AppTheme.colors
    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * The **form** layout (`DS-075`–`076`): `ScreenHeader` optional, centered content, a single primary
 * action. Used when the screen asks the user to read or confirm something and take exactly one next
 * step — the first-run explanation (`DS-076`).
 *
 * `headerTitle` is nullable rather than a `header: @Composable () -> Unit` slot: when non-null this
 * composable calls `ScreenHeader` itself, so a form-layout screen with a header still cannot reach
 * for a raw `TopAppBar` (`DS-021`). `content` is a `ColumnScope` receiver, centred both ways, for
 * "centered content" (`DS-075`). The one primary action is expressed as `PrimaryButton`'s own
 * parameters rather than an `action: @Composable () -> Unit` slot, so the single action `DS-075`
 * names can only ever be a `PrimaryButton` (`DS-001`), never a different button variant.
 */
@Composable
fun FormLayout(
    primaryActionText: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String? = null,
    headerTrailingAction: ScreenHeaderAction? = null,
    primaryActionEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (headerTitle != null) {
                ScreenHeader(title = headerTitle, trailingAction = headerTrailingAction)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = content,
            )
            PrimaryButton(
                text = primaryActionText,
                onClick = onPrimaryAction,
                enabled = primaryActionEnabled,
                modifier = Modifier.padding(spacing.lg),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListLayoutPreview() {
    AppTheme {
        ListLayout(
            title = "Presets",
            trailingAction = ScreenHeaderAction.Icon(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = {},
            ),
            bottomSlot = {
                FabIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New preset",
                    onClick = {},
                )
            },
        ) {
            items(3) { index ->
                Text(
                    text = "Preset ${index + 1} · 4 rounds · 15:30",
                    color = AppTheme.colors.fg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppTheme.spacing.lg),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FullBleedLayoutPreview() {
    AppTheme {
        FullBleedLayout {
            Text(
                text = "0:32 of 0:45",
                style = AppTheme.timerTypography.large,
                color = AppTheme.colors.work,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FormLayoutPreview() {
    AppTheme {
        FormLayout(
            primaryActionText = "Get started",
            onPrimaryAction = {},
            headerTitle = "Welcome",
        ) {
            Text(
                text = "A quick explanation of how interval workouts run.",
                color = AppTheme.colors.fg,
            )
        }
    }
}
