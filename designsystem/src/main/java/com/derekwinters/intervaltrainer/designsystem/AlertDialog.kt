package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.material3.AlertDialog as Material3AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * `AlertDialog` (`DS-010`–`012`): the only overlay component in the v1 vocabulary — no
 * bottom-sheet component exists (`DS-010`). Cancel sits left, the affirmative action sits right,
 * and a destructive affirmative action is rendered in red text (`DS-011`).
 *
 * Wraps `androidx.compose.material3.AlertDialog` (aliased `Material3AlertDialog` in this file to
 * avoid a name clash with this composable) so `:app` reaches `AlertDialog` from `:designsystem`
 * only, never the raw Material component (`DS-090`). Material 3's own `AlertDialog` already places
 * `dismissButton` to the left of `confirmButton`, which is exactly `DS-011`'s ordering — this
 * wrapper does not need to reimplement that layout, only to close off the raw component and apply
 * this app's tokens.
 *
 * `cancelText` is nullable because not every dialog needs a cancel action (a purely informational
 * dialog has only an affirmative "OK"); when it is non-null, `onCancel` defaults to
 * `onDismissRequest` so a plain "Cancel" does not need its own separate handler.
 *
 * `DS-012`: the body is either [text] (a plain sentence, this component's original shape) or
 * [content], arbitrary composable content for a dialog that needs to ask for more than a sentence
 * can hold — the preset editor's round generator (`docs/spec/screens.md` `SCREEN-017`) is the
 * first caller that passes one. [content] wins when both are given; passing neither renders an
 * empty body rather than failing, since a caller that wants one exact behaviour or the other
 * already has [text] and [content] to say so. [confirmEnabled] lets a caller gate the affirmative
 * action on its own dialog's validity (the generator's own duration minimum, `TIMER-072`) without
 * this component needing to know what that validity check is.
 */
@Composable
fun AlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    isConfirmDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Material3AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = colors.chip,
        titleContentColor = colors.fg,
        textContentColor = colors.dim,
        title = { Text(text = title) },
        text = {
            if (content != null) content() else Text(text = text.orEmpty())
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmText,
                    color = when {
                        !confirmEnabled -> colors.dim
                        isConfirmDestructive -> colors.destructive
                        else -> colors.work
                    },
                )
            }
        },
        dismissButton = cancelText?.let { label ->
            {
                TextButton(onClick = onCancel ?: onDismissRequest) {
                    Text(text = label, color = colors.dim)
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AlertDialogPreview() {
    AppTheme {
        AlertDialog(
            title = "Delete preset?",
            text = "This preset will be removed permanently.",
            confirmText = "Delete",
            onConfirm = {},
            onDismissRequest = {},
            cancelText = "Cancel",
            isConfirmDestructive = true,
        )
    }
}
