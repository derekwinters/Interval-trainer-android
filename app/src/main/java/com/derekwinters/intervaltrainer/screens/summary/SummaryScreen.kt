package com.derekwinters.intervaltrainer.screens.summary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.IntervalOutcome
import com.derekwinters.intervaltrainer.ScheduleEntry
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.WorkoutOutcome
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.summaryContent
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.ListLayout
import com.derekwinters.intervaltrainer.designsystem.PrimaryButton
import com.derekwinters.intervaltrainer.designsystem.StockListItem

/**
 * The summary screen (`docs/spec/screens.md` §4, `SCREEN-050`–`053`): rounds completed, total
 * elapsed time, and whether the workout completed or was stopped early — the terminal screen for
 * every ended workout (`TIMER-051`), reached the same way regardless of which of the two outcomes
 * it reports (`SCREEN-051`).
 *
 * [state] is read, never written, here — the same split every other screen in this app makes
 * (`ADR 0005`). It is [state.timer]'s own [summaryContent] (`SCREEN-054`'s own paragraph in
 * `docs/spec/screens.md`), never `state.timer`'s fields reassembled ad hoc here: this screen is
 * reached only once `MainActivity`'s own `LaunchedEffect` has already observed
 * [TimerState.Ended] on `WorkoutServiceState` (`docs/spec/service.md`'s app-wide observation
 * channel), so [state] is simply whatever that channel last published — the same final state the
 * running screen was showing the instant before it ended, not a value this screen recomputes on
 * its own. [content] is `null` only outside that state (a defensive case this screen has no
 * requirement to render anything for, since `SCREEN-051` never reaches it any other way): the list
 * renders no rows, and the one primary action below still returns to home (`SCREEN-052`).
 */
@Composable
fun SummaryScreen(
    state: CueTimerState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val content = remember(state.timer) { state.timer.summaryContent() }

    ListLayout(
        title = "Summary",
        modifier = modifier,
        bottomSlot = {
            PrimaryButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
            )
        },
    ) {
        if (content != null) {
            item {
                StockListItem(
                    headline = "Outcome",
                    supportingText = content.outcome.label(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StockListItem(
                    headline = "Rounds completed",
                    supportingText = "${content.roundsCompleted} of ${content.roundsPlanned}",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StockListItem(
                    headline = "Total time",
                    supportingText = formatSeconds(content.totalTimeMillis.toWholeSeconds()),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** `SCREEN-050`'s third value, in the words this screen shows it with — `:core`'s [WorkoutOutcome]
 * names the two cases (`TIMER-011`); the label is this screen's own rendering of them. */
private fun WorkoutOutcome.label(): String = when (this) {
    WorkoutOutcome.COMPLETED -> "Completed"
    WorkoutOutcome.STOPPED_EARLY -> "Stopped early"
}

private fun Long.toWholeSeconds(): Int = (this / 1_000L).toInt().coerceAtLeast(0)

@Preview(showBackground = true)
@Composable
private fun SummaryScreenCompletedPreview() {
    val schedule = listOf(
        ScheduleEntry(Interval(IntervalKind.WARM_UP, 180), IntervalOutcome.FINISHED),
        ScheduleEntry(Interval(IntervalKind.WORK, 60), IntervalOutcome.FINISHED),
        ScheduleEntry(Interval(IntervalKind.RECOVERY, 120), IntervalOutcome.FINISHED),
        ScheduleEntry(Interval(IntervalKind.WORK, 60), IntervalOutcome.SKIPPED),
        ScheduleEntry(Interval(IntervalKind.COOL_DOWN, 180), IntervalOutcome.FINISHED),
    )
    AppTheme {
        SummaryScreen(
            state = CueTimerState(
                timer = TimerState.Ended(
                    schedule = schedule,
                    outcome = WorkoutOutcome.COMPLETED,
                    totalTimeMillis = 600_000L,
                ),
            ),
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryScreenStoppedEarlyPreview() {
    val schedule = listOf(
        ScheduleEntry(Interval(IntervalKind.WARM_UP, 180), IntervalOutcome.FINISHED),
        ScheduleEntry(Interval(IntervalKind.WORK, 60), IntervalOutcome.FINISHED),
        ScheduleEntry(Interval(IntervalKind.RECOVERY, 120), IntervalOutcome.PENDING),
        ScheduleEntry(Interval(IntervalKind.WORK, 60), IntervalOutcome.PENDING),
    )
    AppTheme {
        SummaryScreen(
            state = CueTimerState(
                timer = TimerState.Ended(
                    schedule = schedule,
                    outcome = WorkoutOutcome.STOPPED_EARLY,
                    totalTimeMillis = 260_000L,
                ),
            ),
            onDone = {},
        )
    }
}
