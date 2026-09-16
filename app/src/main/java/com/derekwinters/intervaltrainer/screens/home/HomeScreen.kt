package com.derekwinters.intervaltrainer.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.ColorRole
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.ScheduleSegment
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.presetSummary
import com.derekwinters.intervaltrainer.scheduleSegments
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import com.derekwinters.intervaltrainer.designsystem.FabIconButton
import com.derekwinters.intervaltrainer.designsystem.ListLayout
import com.derekwinters.intervaltrainer.designsystem.PrimaryButton
import com.derekwinters.intervaltrainer.designsystem.ScreenHeaderAction
import com.derekwinters.intervaltrainer.designsystem.StatusIconButton

/**
 * The home screen (`docs/spec/screens.md` §1, `SCREEN-001`–`008`): `ScreenHeader` with the title
 * "Presets" and a settings action, the preset list in insertion order, and a FAB that opens the
 * editor loaded with a new preset.
 *
 * [presets] is read once by the caller (`RoomPresetStore.presets()`, `SCHEMA-012`'s own insertion
 * order) and handed down; this composable does no I/O of its own; per this page's own invariant,
 * every value a row shows — the round count, the total, the colour-strip segments — is `:core`
 * arithmetic ([presetSummary], [scheduleSegments]) read here, not recomputed ad hoc.
 */
@Composable
fun HomeScreen(
    presets: List<Preset>,
    onOpenSettings: () -> Unit,
    onNewPreset: () -> Unit,
    onEditPreset: (presetId: String) -> Unit,
    onStartPreset: (preset: Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListLayout(
        title = "Presets",
        modifier = modifier,
        trailingAction = ScreenHeaderAction.Icon(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
        ),
        bottomSlot = {
            FabIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New preset",
                onClick = onNewPreset,
            )
        },
    ) {
        items(presets, key = { it.id }) { preset ->
            PresetRow(
                preset = preset,
                onEdit = { onEditPreset(preset.id) },
                onStart = { onStartPreset(preset) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.sm),
            )
        }
    }
}

/**
 * One home-screen row (`SCREEN-003`–`005`): the preset's name and meta line, the colour strip, and
 * exactly two controls — Edit and Start — both always visible.
 *
 * This invariant page's own safety property holds structurally, not by convention: [onEdit] and
 * [onStart] are the row's *only* two click handlers. Nothing else here — the surrounding [Column],
 * the name, the meta line, the colour strip — carries a `clickable` modifier, so there is no third
 * way, accidental or otherwise, to trigger either action (`docs/spec/screens.md`'s "Edit and Start
 * are always two separate, independently tappable controls" invariant).
 */
@Composable
fun PresetRow(
    preset: Preset,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val summary = remember(preset) { presetSummary(preset) }
    val segments = remember(preset) { scheduleSegments(preset) }
    val metaText = remember(summary) {
        val total = formatSeconds(summary.totalDurationSeconds)
        if (summary.roundCount > 0) "${summary.roundCount} rounds · $total" else total
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(spacing.md))
            .background(colors.chip)
            .padding(spacing.md),
    ) {
        if (segments.isNotEmpty()) {
            ColorStrip(
                segments = segments,
                colors = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.xs)
                    .clip(RoundedCornerShape(spacing.xs / 2)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                BasicText(
                    text = preset.name,
                    style = TextStyle(color = colors.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = metaText,
                    style = TextStyle(color = colors.dim, fontSize = 13.sp),
                    modifier = Modifier.padding(top = spacing.xs / 2),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                StatusIconButton(icon = Icons.Filled.Edit, contentDescription = "Edit", onClick = onEdit)
                PrimaryButton(text = "Start", onClick = onStart)
            }
        }
    }
}

/**
 * `SCREEN-004`'s colour strip: one segment per interval, in schedule order, sized by its share of
 * [segments]' total. `Modifier.weight` requires a strictly positive weight, which
 * [scheduleSegments]' own zero-*total* case already avoids (it returns no segments at all rather
 * than dividing by zero) — but does not, by itself, rule out one interval among several others
 * authored at zero seconds, whose own fraction would then be exactly zero; those are filtered out
 * here rather than passed to `weight(0f)`, which throws.
 */
@Composable
private fun ColorStrip(
    segments: List<ScheduleSegment>,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        segments.filter { it.fraction > 0.0 }.forEach { segment ->
            Row(
                modifier = Modifier
                    .weight(segment.fraction.toFloat())
                    .fillMaxHeight()
                    .background(segment.colorRole.toColor(colors)),
            ) {}
        }
    }
}

/** The one place `:app` maps `:core`'s [ColorRole] (`CUE-030`) to a real [Color] (`DS-050`) —
 * `:core` does not depend on `:designsystem` and has no way to know this mapping itself. */
private fun ColorRole.toColor(colors: DesignSystemColors): Color = when (this) {
    ColorRole.WORK -> colors.work
    ColorRole.RECOVERY -> colors.recovery
    ColorRole.NEUTRAL -> colors.neutral
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val presets = listOf(
        Preset(
            id = "1",
            name = "Short Example",
            intervals = buildList {
                add(Interval(IntervalKind.WARM_UP, 180))
                repeat(3) {
                    add(Interval(IntervalKind.WORK, 60))
                    add(Interval(IntervalKind.RECOVERY, 120))
                }
                add(Interval(IntervalKind.COOL_DOWN, 180))
            },
        ),
    )
    AppTheme {
        HomeScreen(
            presets = presets,
            onOpenSettings = {},
            onNewPreset = {},
            onEditPreset = {},
            onStartPreset = {},
        )
    }
}
