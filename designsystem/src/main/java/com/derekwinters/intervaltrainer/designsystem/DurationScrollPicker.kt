package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.filter

/**
 * Duration entry (`DS-009`): a flick-scrub drum for minutes and seconds — never steppers, typed
 * digits, or chips. Two independent [ScrollDrum]s, one per unit, joined by a fixed `:` — this
 * supersedes the increment/decrement wording both [#29](https://github.com/derekwinters/Interval-trainer-android/issues/29)
 * and [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40) used before this
 * decision.
 *
 * `DS-009` also says this control reuses "the running screen's fade/shrink treatment", but the
 * running screen is not built yet (`docs/spec/design-system.md`'s own introduction: nothing beyond
 * the token layer exists in `:designsystem`, and no running-screen composable exists anywhere in
 * this build to reuse a treatment from). [ScrollDrum]'s fade/shrink below — items dim and shrink
 * with distance from the centre row — is this component's own reasonable interpretation of that
 * phrase, not a value lifted from running-screen code, since there is none yet to lift it from.
 * It is expected to be reconciled against the running screen's actual treatment once that screen is
 * built — see this change's pull request description.
 */
@Composable
fun DurationScrollPicker(
    minutes: Int,
    seconds: Int,
    onDurationChange: (minutes: Int, seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
    minutesRange: IntRange = 0..59,
    secondsRange: IntRange = 0..59,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrollDrum(
            value = minutes,
            range = minutesRange,
            onValueChange = { onDurationChange(it, seconds) },
        )
        Text(
            text = ":",
            style = AppTheme.timerTypography.large,
            color = colors.fg,
            modifier = Modifier
                .width(spacing.lg),
            textAlign = TextAlign.Center,
        )
        ScrollDrum(
            value = seconds,
            range = secondsRange,
            onValueChange = { onDurationChange(minutes, it) },
        )
    }
}

/** How many rows are visible in a drum at once — the centre row plus one above and one below. */
private const val VisibleRowCount = 3

/**
 * The height of one row in a drum, sized with headroom for [TimerTypography.large]'s 64sp digits
 * (`DS-030`) — the drum reuses that same timer typographic role, since a drum digit is a timer
 * value in the same sense the running countdown is. Not named by `docs/spec/design-system.md`; a
 * drum row is a control's own dimension in the same sense `DS-008`'s 26dp is, not an ad hoc padding
 * `DS-040` governs.
 */
private val RowHeight: Dp = 80.dp

/**
 * One scrollable "drum" of values (`00`–`59`), snapping to the centre row and fading/shrinking rows
 * away from it. Reports the settled value once scrolling stops, rather than continuously mid-fling,
 * so a caller never sees a value it did not actually land on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollDrum(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = remember(range) { range.toList() }
    val initialIndex = remember(range, values) {
        values.indexOf(value).let { if (it >= 0) it else 0 }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val rowHeightPx = remember(density) { with(density) { RowHeight.toPx() } }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect {
                val viewportCenter = listState.layoutInfo.viewportSize.height / 2f
                val centered = listState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                val settled = centered?.index?.let { values.getOrNull(it) }
                if (settled != null && settled != value) {
                    onValueChange(settled)
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .width(96.dp)
            .height(RowHeight * VisibleRowCount),
        contentPadding = PaddingValues(vertical = RowHeight * (VisibleRowCount / 2)),
    ) {
        itemsIndexed(values) { index, item ->
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val viewportCenter = listState.layoutInfo.viewportSize.height / 2f
            val itemCenter = info?.let { it.offset + it.size / 2 } ?: viewportCenter.toInt()
            val distanceRows = abs(itemCenter - viewportCenter) / rowHeightPx.coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .wrapContentHeight(Alignment.CenterVertically),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.toString().padStart(2, '0'),
                    style = AppTheme.timerTypography.large,
                    color = colors.fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        // Fade and shrink rows away from the centre, per DS-009's own phrase — see
                        // this file's top-level doc for why the exact curve is this component's own
                        // choice rather than a value reused from the (not yet built) running screen.
                        val fraction = (1f - distanceRows.coerceIn(0f, 1f))
                        alpha = 0.3f + 0.7f * fraction
                        val scale = 0.7f + 0.3f * fraction
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DurationScrollPickerPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            var minutes by remember { mutableIntStateOf(1) }
            var seconds by remember { mutableIntStateOf(30) }
            DurationScrollPicker(
                minutes = minutes,
                seconds = seconds,
                onDurationChange = { m, s ->
                    minutes = m
                    seconds = s
                },
            )
        }
    }
}
