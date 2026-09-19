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
 */
@Composable
fun StockListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Material3ListItem(
        headlineContent = { Text(text = headline) },
        supportingContent = supportingText?.let { { Text(text = it) } },
        modifier = modifier,
    )
}
