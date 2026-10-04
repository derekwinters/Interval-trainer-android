package com.derekwinters.intervaltrainer.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.ColorRole
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.colorRole
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors

/**
 * An interval kind's colour dot followed by its name — the label the preset editor's schedule rows
 * (`docs/spec/screens.md` `SCREEN-012`) and the round generator's two duration pickers
 * (`SCREEN-017`, #136) share, so the dialog cannot drift from the rows: an 8dp circle in the kind's
 * colour (`CUE-030`), `spacing.sm`, then the name 13sp bold in `colors.fg`. The name is always
 * drawn; the colour reinforces it and never carries the meaning alone (`CUE-032`).
 */
@Composable
internal fun KindLabel(kind: IntervalKind, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(kind.dotColor(colors)),
        )
        BasicText(
            text = kind.displayName(),
            style = TextStyle(color = colors.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = AppTheme.spacing.sm),
        )
    }
}

internal fun IntervalKind.displayName(): String = when (this) {
    IntervalKind.WARM_UP -> "Warm-up"
    IntervalKind.WORK -> "Work"
    IntervalKind.RECOVERY -> "Recovery"
    IntervalKind.COOL_DOWN -> "Cool-down"
}

/** The editor's one mapping from `:core`'s [IntervalKind] (via `colorRole()`, `CUE-030`) to a real
 * [Color] (`DS-050`) for a kind dot — `:core` does not depend on `:designsystem` and has no way to
 * know this mapping itself, the same reason `HomeScreen.kt`'s own `ColorRole.toColor` exists. */
internal fun IntervalKind.dotColor(colors: DesignSystemColors): Color = when (colorRole()) {
    ColorRole.WORK -> colors.work
    ColorRole.RECOVERY -> colors.recovery
    ColorRole.NEUTRAL -> colors.neutral
}
