package com.derekwinters.intervaltrainer.screens.crash

import androidx.compose.runtime.Composable
import com.derekwinters.intervaltrainer.crash.CrashReportChoice
import com.derekwinters.intervaltrainer.designsystem.AlertDialog

/**
 * `docs/spec/screens.md` §8, `SCREEN-091`: the crash report popup, `DS-010`–`012`'s `AlertDialog`
 * with Copy as the affirmative action on the right and Dismiss as the cancel action on the left
 * (`DS-011`). No Share action. Every way it closes reports one [CrashReportChoice]; what that
 * choice does to the report is `crashReportOutcome()`, applied by the caller (`MainActivity.kt`).
 */
@Composable
fun CrashReportDialog(onChoice: (CrashReportChoice) -> Unit) {
    AlertDialog(
        title = "App crashed",
        text = "The last launch crashed. Do you want to copy the crash logs for a bug report?",
        confirmText = "Copy",
        onConfirm = { onChoice(CrashReportChoice.COPY) },
        // SCREEN-094: back or a tap outside closes for this launch and keeps the report.
        onDismissRequest = { onChoice(CrashReportChoice.CLOSE) },
        cancelText = "Dismiss",
        onCancel = { onChoice(CrashReportChoice.DISMISS) },
    )
}
