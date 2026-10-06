package com.derekwinters.intervaltrainer.screens.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.designsystem.AppTheme

/**
 * The plain-text drag handle — two glyph columns, the same "no vector icon dependency" choice
 * `CountStepper.kt`'s own `−`/`+` glyphs already made. Drag starts directly off this handle, per
 * the settled design's own grip control, never off a long-press on the row.
 *
 * Shared by the preset editor's interval rows (`docs/spec/screens.md` `SCREEN-013`) and home's
 * preset rows (`SCREEN-009`, #163), which `SCREEN-009` says work the same way. It has no tap action
 * at all — only a drag reaches it — so on home it can never start or edit a preset (§1's Edit and
 * Start invariant).
 *
 * The gesture detector is installed once (`pointerInput(Unit)`), so it reads the three callbacks
 * through [rememberUpdatedState]: a caller whose list state is replaced while the row stays on
 * screen — home's, when its presets are reloaded — still gets the current callbacks, not the ones
 * the row was first composed with.
 */
@Composable
internal fun DragHandle(
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = modifier
            .size(32.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDragDelta(dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "⠿",
            style = TextStyle(color = colors.dim, fontSize = 16.sp),
        )
    }
}
