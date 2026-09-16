package com.derekwinters.intervaltrainer.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                GeneratorFieldRow(label = "Rounds") {
                    CountStepper(count = roundCount, onCountChange = { roundCount = it })
                }
                GeneratorFieldLabel("Work")
                DurationScrollPicker(
                    minutes = workMinutes,
                    seconds = workSeconds,
                    onDurationChange = { m, s -> workMinutes = m; workSeconds = s },
                )
                GeneratorFieldLabel("Recovery")
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

@Composable
private fun GeneratorFieldLabel(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = AppTheme.colors.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.xs),
    )
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
