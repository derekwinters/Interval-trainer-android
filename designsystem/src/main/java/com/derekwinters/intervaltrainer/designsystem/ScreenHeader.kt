package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

/**
 * The single trailing action a [ScreenHeader] may show (`DS-020`): "an icon or a text button", so
 * this is a closed, two-case type rather than a slot that would let a call site pass more than one
 * — a `ScreenHeader` with two trailing actions is not a smaller version of this component, it is a
 * different header `docs/spec/design-system.md` does not name.
 */
sealed interface ScreenHeaderAction {
    /** A trailing icon button, in the status role (`DS-005`) — the size a header action uses. */
    data class Icon(
        val icon: ImageVector,
        val contentDescription: String,
        val onClick: () -> Unit,
    ) : ScreenHeaderAction

    /** A trailing text button — `docs/spec/screens.md`'s `SCREEN-010` "Save", for example. */
    data class TextAction(
        val label: String,
        val onClick: () -> Unit,
    ) : ScreenHeaderAction
}

/**
 * `ScreenHeader` (`DS-020`–`021`): a fixed header row — a title top-left, exactly one trailing
 * action (an icon or a text button), and an optional page-level FAB. Every one of the six v1
 * screens uses this, including the three built from stock Material 3 components — never a stock
 * `TopAppBar` (`DS-021`).
 *
 * `docs/spec/screens.md`'s two settled headers (`SCREEN-001`, `SCREEN-010`) each show only a title
 * and one trailing action; neither passes a FAB to `ScreenHeader` itself; home's own FAB
 * (`SCREEN-008`) is described as a screen-level element, not as part of its `ScreenHeader` call.
 * `DS-020` nonetheless names the FAB as part of what `ScreenHeader` "is", so this composable still
 * accepts an optional `fab` slot — rendered anchored to this row's trailing edge — for a caller
 * outside the closed set of layouts in `docs/spec/design-system.md` §8. **Resolved by `DS-079`:**
 * the list layout's own "optional bottom action or FAB" slot (`DS-071`) is where a list-layout
 * screen's FAB is actually composed — see `ListLayout` in `ScreenLayouts.kt` — and this `fab`
 * parameter is left unset by every v1 screen.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingAction: ScreenHeaderAction? = null,
    fab: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            when (trailingAction) {
                is ScreenHeaderAction.Icon -> StatusIconButton(
                    icon = trailingAction.icon,
                    contentDescription = trailingAction.contentDescription,
                    onClick = trailingAction.onClick,
                )

                is ScreenHeaderAction.TextAction -> TextButton(onClick = trailingAction.onClick) {
                    Text(text = trailingAction.label, color = colors.work)
                }

                null -> Unit
            }
        }
        if (fab != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.lg, bottom = spacing.lg),
            ) {
                fab()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScreenHeaderWithIconActionPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            ScreenHeader(
                title = "Presets",
                trailingAction = ScreenHeaderAction.Icon(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = {},
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScreenHeaderWithTextActionAndFabPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            ScreenHeader(
                title = "Edit preset",
                trailingAction = ScreenHeaderAction.TextAction(label = "Save", onClick = {}),
                fab = {
                    FabIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "New preset",
                        onClick = {},
                    )
                },
            )
        }
    }
}
