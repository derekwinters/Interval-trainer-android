package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.material3.ListItem as Material3ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * `DS-015`: wraps `androidx.compose.material3.ListItem`, the stock component `DS-061` names for
 * the three stock-vocabulary screens (summary, settings, first-run) — the summary screen
 * (`docs/spec/screens.md` §4, `SCREEN-050`) is the first caller.
 *
 * `:app` cannot import `androidx.compose.material3` itself (`DS-090`: `material3` is declared
 * `implementation` on `:designsystem`, so a raw import of it inside `:app` is a compile error) —
 * the same reason [FabIconButton] wraps `FloatingActionButton` and `AlertDialog` wraps
 * `androidx.compose.material3.AlertDialog` rather than either being reached for directly. `DS-061`
 * names `ListItem` as vocabulary a stock screen uses "directly"; this is what "directly" actually
 * resolves to once that boundary is enforced by the build rather than only stated in prose.
 *
 * [headline] and [supportingText] are plain strings, the same typed-slot reasoning
 * `ScreenLayouts.kt`'s own doc comment gives for [ListLayout]'s and [FormLayout]'s slots: a caller
 * fills exactly the shape this component names, not an arbitrary composable.
 *
 * `DS-016`: [trailingContent] is the one addition to that rule, added for the settings screen's
 * own default-mute row (`docs/spec/screens.md` `SCREEN-061`) — a row that needs to show a boolean
 * value's own control at its trailing edge, not only describe it in text. It stays one composable
 * slot rather than a typed `trailing: Boolean?` pair of parameters, the same reasoning
 * [ListLayout]'s own `bottomSlot` already gives for a slot two call sites would otherwise fill two
 * different ways: this component has exactly one real caller for it, settings' default-mute row,
 * which composes `Toggle` (`DS-014`) into it — never a second, redundant switch of `StockListItem`'s
 * own — but nothing here hard-codes that composition, since a component's own file is not the
 * place a specific screen's call site belongs.
 */
@Composable
fun StockListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Material3ListItem(
        headlineContent = { Text(text = headline) },
        supportingContent = supportingText?.let { { Text(text = it) } },
        trailingContent = trailingContent,
        modifier = modifier,
    )
}
