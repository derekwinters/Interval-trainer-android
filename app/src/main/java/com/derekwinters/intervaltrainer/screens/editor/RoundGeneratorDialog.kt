package com.derekwinters.intervaltrainer.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.MINIMUM_INTERVAL_DURATION_SECONDS
import com.derekwinters.intervaltrainer.designsystem.AlertDialog
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.CountStepper
import com.derekwinters.intervaltrainer.designsystem.DurationScrollPicker
import com.derekwinters.intervaltrainer.designsystem.Toggle

/** `SCREEN-017`'s own reasonable starting values — the dialog resets to these every time it opens
 * (`TIMER-004`: nothing about a previous generator call is remembered), not values recovered from
 * an existing decision. */
private const val DefaultRoundCount = 4
private const val DefaultWorkDurationSeconds = 45
private const val DefaultRecoveryDurationSeconds = 15

/**
 * The round generator dialog (`docs/spec/screens.md` `SCREEN-017`, `TIMER-080`–`085`): a round-count
 * `CountStepper` (`DS-008`), a work-duration and a recovery-duration `DurationScrollPicker`
 * (`DS-009`), and a trailing-recovery `Toggle` (`DS-014`) defaulting off (`TIMER-082`), inside
 * `AlertDialog`'s own `content` slot (`DS-012`).
 *
 * [onConfirm] fires only while both durations are already at or above the five-second minimum
 * (`TIMER-070`) — `confirmEnabled` below gates the dialog's own "Add" action on it, the same
 * enforcement `TIMER-072` names for the editor generally, so an invalid duration never reaches
 * [com.derekwinters.intervaltrainer.appendGeneratedRounds] (which would otherwise throw) through
 * this dialog at all.
 *
 * The content scrolls ([#135](https://github.com/derekwinters/Interval-trainer-android/issues/135)).
 * `AlertDialog`'s body slot gives it whatever height is left between the title and the action row
 * and clips anything taller, so on a short window — a small phone, landscape, or a larger system
 * font — the recovery drum was sliced and the trailing-recovery toggle was unreachable. Even with
 * the drums at 120dp (#134) the dialog is roughly 570dp tall at the default font scale, more than a
 * landscape phone has, so the four controls `SCREEN-017` lists stay reachable only by scrolling to
 * them. The drums keep their fixed height, so a drag that starts on a drum still turns the drum.
 *
 * Each drum is labelled with its kind's colour dot and name ([KindLabel], `SCREEN-017`,
 * [#136](https://github.com/derekwinters/Interval-trainer-android/issues/136)) — the same label the
 * schedule rows draw — because the two drums are otherwise identical.
 */
@Composable
fun RoundGeneratorDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        roundCount: Int,
        workDurationSeconds: Int,
        recoveryDurationSeconds: Int,
        trailingRecovery: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var roundCount by remember { mutableIntStateOf(DefaultRoundCount) }
    var workMinutes by remember { mutableIntStateOf(DefaultWorkDurationSeconds / 60) }
    var workSeconds by remember { mutableIntStateOf(DefaultWorkDurationSeconds % 60) }
    var recoveryMinutes by remember { mutableIntStateOf(DefaultRecoveryDurationSeconds / 60) }
    var recoverySeconds by remember { mutableIntStateOf(DefaultRecoveryDurationSeconds % 60) }
    var trailingRecovery by remember { mutableStateOf(false) }

    val workDurationSeconds = workMinutes * 60 + workSeconds
    val recoveryDurationSeconds = recoveryMinutes * 60 + recoverySeconds
    val isValid = workDurationSeconds >= MINIMUM_INTERVAL_DURATION_SECONDS &&
        recoveryDurationSeconds >= MINIMUM_INTERVAL_DURATION_SECONDS

    AlertDialog(
        title = "+ Rounds",
        confirmText = "Add",
        onConfirm = {
            onConfirm(roundCount, workDurationSeconds, recoveryDurationSeconds, trailingRecovery)
        },
        onDismissRequest = onDismiss,
        modifier = modifier,
        cancelText = "Cancel",
        onCancel = onDismiss,
        confirmEnabled = isValid,
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                GeneratorFieldRow(label = "Rounds") {
                    CountStepper(count = roundCount, onCountChange = { roundCount = it })
                }
                GeneratorKindLabel(IntervalKind.WORK)
                DurationScrollPicker(
                    minutes = workMinutes,
                    seconds = workSeconds,
                    onDurationChange = { m, s -> workMinutes = m; workSeconds = s },
                )
                GeneratorKindLabel(IntervalKind.RECOVERY)
                DurationScrollPicker(
                    minutes = recoveryMinutes,
                    seconds = recoverySeconds,
                    onDurationChange = { m, s -> recoveryMinutes = m; recoverySeconds = s },
                )
                GeneratorFieldRow(label = "Trailing recovery") {
                    Toggle(
                        checked = trailingRecovery,
                        onCheckedChange = { trailingRecovery = it },
                        contentDescription = "Trailing recovery",
                    )
                }
            }
        },
    )
}

/** A duration picker's label (`SCREEN-017`, #136): the schedule rows' own [KindLabel] — the kind's
 * colour dot before its name — so the two identical drums tell themselves apart at a glance. */
@Composable
private fun GeneratorKindLabel(kind: IntervalKind) {
    KindLabel(kind = kind, modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.xs))
}

@Composable
private fun GeneratorFieldRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = AppTheme.colors.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        content()
    }
}
