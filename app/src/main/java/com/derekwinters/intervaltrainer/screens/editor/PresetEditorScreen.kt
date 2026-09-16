package com.derekwinters.intervaltrainer.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.derekwinters.intervaltrainer.ColorRole
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.appendGeneratedRounds
import com.derekwinters.intervaltrainer.colorRole
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import com.derekwinters.intervaltrainer.designsystem.DurationScrollPicker
import com.derekwinters.intervaltrainer.designsystem.ListLayout
import com.derekwinters.intervaltrainer.designsystem.ScreenHeaderAction
import com.derekwinters.intervaltrainer.designsystem.SecondaryButton
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.next
import com.derekwinters.intervaltrainer.presetSummary
import java.util.UUID

/** `SCREEN-016`: a freshly added row's starting kind and duration — a reasonable, immediately
 * editable starting point, not a value either mockup or an earlier decision pins down. */
private val NewIntervalDefaultKind = IntervalKind.WORK
private const val NewIntervalDefaultDurationSeconds = 30

/** The closed-row height used both to draw a row and to convert a drag gesture's pixel delta into
 * whole-row reordering steps (`SCREEN-013`). Rows are always this height while any drag is in
 * progress — see [PresetEditorScreen]'s own doc comment for why an open row is closed the moment a
 * drag starts. */
private val RowHeight = 52.dp

/**
 * One row of the editor's in-memory schedule (`SCREEN-012`): a stable local [id] the list uses as
 * its `LazyColumn` key, since two rows can otherwise share the same [kind]/[durationSeconds] and a
 * raw [Interval] has no identity of its own. [id] is never persisted (`RoomPresetStore.save` reads
 * only [kind] and [durationSeconds], in list order) — it exists purely so this screen's own list
 * can reorder, open and delete the right row.
 */
private data class EditorRow(
    val id: String,
    val kind: IntervalKind,
    val durationSeconds: Int,
)

private fun EditorRow.toInterval() = Interval(kind, durationSeconds)

private fun Interval.toEditorRow() = EditorRow(
    id = UUID.randomUUID().toString(),
    kind = kind,
    durationSeconds = durationSeconds,
)

/**
 * The preset editor (`docs/spec/screens.md` §2, `SCREEN-010`–`019`, `SCREEN-014a`): the name
 * field, the freely-authored interval list with drag-reorder, tap-to-edit-in-place duration, a
 * per-row kind cycle and delete, and the `+ Interval`/`+ Rounds…` bottom actions — the latter
 * opening the round generator.
 *
 * [preset] is the preset this screen opens with — either one already saved (`SCREEN-007`) or a
 * fresh, empty one carrying a freshly generated id (`SCREEN-008`); either way this composable does
 * no I/O of its own (`presetStore` calls happen in `MainActivity`'s `NavHost`, the same split
 * `HomeScreen` already uses). All editing happens in local state until [onSave] fires
 * (`SCREEN-019`); nothing here writes through as the user types, per `SCREEN-015`'s own "nothing
 * saved is lost until Save is pressed".
 *
 * **Drag reorder** (`SCREEN-013`) is index-swap-on-threshold, not a free-pixel drag: every row is
 * [RowHeight] tall while a drag is in progress, and starting a drag closes whichever row was open
 * for editing first, so the row-height arithmetic below never has to account for the taller
 * open-for-editing row. This is this screen's own reasonable implementation choice for "dragging a
 * row's handle reorders it", not a value either settled design or an existing decision pins down —
 * see this pull request's Deviations section.
 */
@Composable
fun PresetEditorScreen(
    preset: Preset,
    onSave: (Preset) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var rows by remember(preset.id) { mutableStateOf(preset.intervals.map { it.toEditorRow() }) }
    var openRowId by remember(preset.id) { mutableStateOf<String?>(null) }
    var showGeneratorDialog by remember { mutableStateOf(false) }

    var draggingRowId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = remember(density) { with(density) { RowHeight.toPx() } }

    val currentPreset = remember(preset.id, name, rows) {
        Preset(id = preset.id, name = name, intervals = rows.map { it.toInterval() })
    }
    val summary = remember(currentPreset) { presetSummary(currentPreset) }
    val footerText = remember(rows.size, summary) {
        "${rows.size} interval${if (rows.size == 1) "" else "s"} · total " +
            formatSeconds(summary.totalDurationSeconds)
    }

    ListLayout(
        title = "Edit preset",
        modifier = modifier,
        onBack = onBack,
        trailingAction = ScreenHeaderAction.TextAction(
            label = "Save",
            onClick = { onSave(currentPreset) },
        ),
    ) {
        item(key = "name-field") {
            NameField(
                name = name,
                onNameChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.sm),
            )
        }
        item(key = "schedule-label") {
            ScheduleLabel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.xs),
            )
        }
        items(rows, key = { it.id }) { row ->
            val isDragging = row.id == draggingRowId
            EditorRowView(
                row = row,
                isOpen = row.id == openRowId,
                onToggleOpen = {
                    openRowId = if (openRowId == row.id) null else row.id
                },
                onDurationChange = { minutes, seconds ->
                    val duration = minutes * 60 + seconds
                    rows = rows.map { if (it.id == row.id) it.copy(durationSeconds = duration) else it }
                },
                onCycleKind = {
                    rows = rows.map { if (it.id == row.id) it.copy(kind = it.kind.next()) else it }
                },
                onDelete = {
                    rows = rows.filterNot { it.id == row.id }
                    if (openRowId == row.id) openRowId = null
                },
                onDragStart = {
                    // Closing whichever row is open keeps every row RowHeight tall for the
                    // duration of the drag — see this file's own top-level doc comment.
                    openRowId = null
                    draggingRowId = row.id
                    dragOffsetPx = 0f
                },
                onDragDelta = { deltaY ->
                    dragOffsetPx += deltaY
                    while (dragOffsetPx > rowHeightPx / 2) {
                        val from = rows.indexOfFirst { it.id == row.id }
                        val to = from + 1
                        if (to > rows.lastIndex) break
                        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
                        dragOffsetPx -= rowHeightPx
                    }
                    while (dragOffsetPx < -rowHeightPx / 2) {
                        val from = rows.indexOfFirst { it.id == row.id }
                        val to = from - 1
                        if (to < 0) break
                        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
                        dragOffsetPx += rowHeightPx
                    }
                },
                onDragEnd = {
                    draggingRowId = null
                    dragOffsetPx = 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                    .then(if (isDragging) Modifier else Modifier.animateItem()),
            )
        }
        item(key = "add-row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                SecondaryButton(
                    text = "+ Interval",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val newRow = EditorRow(
                            id = UUID.randomUUID().toString(),
                            kind = NewIntervalDefaultKind,
                            durationSeconds = NewIntervalDefaultDurationSeconds,
                        )
                        rows = rows + newRow
                        openRowId = newRow.id
                    },
                )
                SecondaryButton(
                    text = "+ Rounds…",
                    modifier = Modifier.weight(1f),
                    onClick = { showGeneratorDialog = true },
                )
            }
        }
        item(key = "footer") {
            BasicText(
                text = footerText,
                style = TextStyle(color = AppTheme.colors.dim, fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.spacing.lg),
            )
        }
    }

    if (showGeneratorDialog) {
        RoundGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onConfirm = { roundCount, workDurationSeconds, recoveryDurationSeconds, trailingRecovery ->
                val generated = appendGeneratedRounds(
                    existing = rows.map { it.toInterval() },
                    roundCount = roundCount,
                    workDurationSeconds = workDurationSeconds,
                    recoveryDurationSeconds = recoveryDurationSeconds,
                    trailingRecovery = trailingRecovery,
                )
                rows = generated.map { it.toEditorRow() }
                showGeneratorDialog = false
            },
        )
    }
}

/** `SCREEN-011`: the preset's name, plain editable text — `BasicTextField`, not a Material
 * `TextField`, since `:app` has no compile-time reach into `androidx.compose.material3` at all
 * (`DS-090`) and nothing in the vocabulary names a text-field component for this bespoke screen
 * (`DS-060`) to draw one from. */
@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(spacing.sm))
            .background(colors.chip)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            textStyle = TextStyle(color = colors.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(colors.work),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScheduleLabel(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(
            text = "SCHEDULE",
            style = TextStyle(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = "Drag to reorder · tap a duration to edit",
            style = TextStyle(color = colors.dim, fontSize = 10.sp),
        )
    }
}

/**
 * One schedule row (`SCREEN-012`): a colour dot for [EditorRow.kind] (`colorRole()`, `CUE-030`),
 * the kind's name, a drag handle, and a delete control — plus, while [isOpen], the duration
 * scroll-picker (`SCREEN-014`) and the kind-cycle tap target (`SCREEN-014a`) in place of the
 * closed row's plain duration text.
 */
@Composable
private fun EditorRowView(
    row: EditorRow,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    onDurationChange: (minutes: Int, seconds: Int) -> Unit,
    onCycleKind: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Column(modifier = modifier.background(colors.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragEnd = onDragEnd,
            )
            Row(
                modifier = Modifier
                    .padding(start = spacing.sm)
                    .then(if (isOpen) Modifier.clickable(onClick = onCycleKind) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(row.kind.dotColor(colors)),
                )
                BasicText(
                    text = row.kind.displayName(),
                    style = TextStyle(color = colors.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (!isOpen) {
                BasicText(
                    text = formatSeconds(row.durationSeconds),
                    style = TextStyle(color = colors.dim, fontSize = 12.5.sp),
                    modifier = Modifier.clickable(onClick = onToggleOpen),
                )
            } else {
                BasicText(
                    text = "▾",
                    style = TextStyle(color = colors.dim, fontSize = 12.sp),
                    modifier = Modifier
                        .padding(end = spacing.sm)
                        .clickable(onClick = onToggleOpen),
                )
            }
            DeleteControl(onDelete = onDelete)
        }
        if (isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                DurationScrollPicker(
                    minutes = row.durationSeconds / 60,
                    seconds = row.durationSeconds % 60,
                    onDurationChange = onDurationChange,
                )
            }
        }
    }
}

/** The plain-text drag handle (`SCREEN-013`) — two glyph columns, the same "no vector icon
 * dependency" choice `CountStepper.kt`'s own `−`/`+` glyphs already made. Drag starts directly off
 * this handle, per the settled design's own grip control, never off a long-press on the row. */
@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
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

/** `SCREEN-015`: a plain delete control — no confirmation, no destructive styling (`DS-004`). */
@Composable
private fun DeleteControl(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(onClickLabel = "Delete interval", onClick = onDelete),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "✕",
            style = TextStyle(color = colors.dim, fontSize = 13.sp),
        )
    }
}

private fun IntervalKind.displayName(): String = when (this) {
    IntervalKind.WARM_UP -> "Warm-up"
    IntervalKind.WORK -> "Work"
    IntervalKind.RECOVERY -> "Recovery"
    IntervalKind.COOL_DOWN -> "Cool-down"
}

/** The one place `:app` maps `:core`'s [IntervalKind] (via `colorRole()`, `CUE-030`) to a real
 * [Color] (`DS-050`) for the editor row's own colour dot — `:core` does not depend on
 * `:designsystem` and has no way to know this mapping itself, the same reason `HomeScreen.kt`'s
 * own `ColorRole.toColor` exists. */
private fun IntervalKind.dotColor(colors: DesignSystemColors): Color = when (colorRole()) {
    ColorRole.WORK -> colors.work
    ColorRole.RECOVERY -> colors.recovery
    ColorRole.NEUTRAL -> colors.neutral
}
