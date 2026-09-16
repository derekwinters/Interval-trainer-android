package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `CountStepper` (`DS-008`): a `−`/`+` mini-stepper used for round count only, never for a
 * duration — [DurationScrollPicker] is the vocabulary's only duration control (`DS-009`). Drawn
 * small — `docs/spec/design-system.md` says "26px in the settled prototype" — and its tap area
 * still meets the 48dp minimum touch target regardless of its drawn size, exactly as
 * `DS-005`–`007`. [ButtonSize] (26dp) is this requirement's own literal, not an ad hoc dimension
 * `DS-052` would flag: it is what `DS-008` names, the same way `DS-007` names the FAB role's 56dp.
 *
 * The `−`/`+` glyphs are plain [Text], not vector icons: `DS-008` itself calls this a "`−`/`+`
 * mini-stepper", and rendering it as two characters keeps this component free of a dependency on
 * an icon asset library that the rest of the vocabulary does not otherwise need.
 */
@Composable
fun CountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..99,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            label = "−",
            contentDescription = "Decrease rounds",
            enabled = count > range.first,
            onClick = { onCountChange((count - 1).coerceIn(range)) },
            background = colors.chip,
            ink = colors.fg,
        )
        Text(
            text = count.toString(),
            style = AppTheme.timerTypography.stat,
            color = colors.fg,
            modifier = Modifier
                .size(width = spacing.xxl, height = spacing.xxl),
            textAlign = TextAlign.Center,
        )
        StepperButton(
            label = "+",
            contentDescription = "Increase rounds",
            enabled = count < range.last,
            onClick = { onCountChange((count + 1).coerceIn(range)) },
            background = colors.chip,
            ink = colors.fg,
        )
    }
}

/** `DS-008`'s own literal drawn size for the stepper's `−`/`+` controls. */
private val ButtonSize: Dp = 26.dp

@Composable
private fun StepperButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color,
    ink: Color,
) {
    Box(
        // `minimumInteractiveComponentSize()` before `.size(ButtonSize)` (as in `IconButtons.kt`):
        // the 48dp touch target wraps the 26dp drawn box rather than replacing it.
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(ButtonSize)
            .background(color = background, shape = CircleShape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                onClickLabel = contentDescription,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) ink else ink.copy(alpha = 0.38f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CountStepperPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            var count by remember { mutableIntStateOf(4) }
            CountStepper(count = count, onCountChange = { count = it })
        }
    }
}
