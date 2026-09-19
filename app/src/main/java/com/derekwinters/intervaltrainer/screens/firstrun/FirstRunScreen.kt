package com.derekwinters.intervaltrainer.screens.firstrun

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.FormLayout

/**
 * The first-run explanation (`docs/spec/screens.md` §6, `SCREEN-070`–`073`; `docs/spec/service.md`
 * `SVC-030`–`032`): a plain-language explanation of what the notification is for, shown exactly
 * once, before the system `POST_NOTIFICATIONS` permission prompt appears (`SCREEN-071`). The exact
 * wording is not fixed by the specification; this is this screen's own phrasing of the owner's own
 * framing on [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31) (`SCREEN-071`).
 *
 * This screen has exactly one output, [onGetStarted] — the same "a screen shows only what it is
 * handed, and only ever calls back out" split every other screen in this app already follows
 * (`ADR 0005`; see `SettingsScreen`'s own doc comment). It does not itself persist the first-run-
 * seen flag (`SCREEN-080`) or launch the system permission prompt (`SVC-030`): its caller
 * (`MainActivity.kt`'s `first_run` route) does both, in that order, the moment [onGetStarted] is
 * called — see `docs/spec/screens.md` §6's own ordering invariant for why that order matters.
 *
 * `DS-075`/`076`: `FormLayout` — centred content, one primary action — with no `ScreenHeader`
 * (`headerTitle` left `null`): this screen has no back action and nothing to navigate away from
 * except its own one button (`SCREEN-072`'s "no way to skip past the explanation"). `DS-061` names
 * this screen's vocabulary as stock Material 3, the same as settings and the summary; `:app`
 * cannot import `androidx.compose.material3` directly (`DS-090`), so its two text elements use
 * `BasicText`, the same plain-text primitive `HomeScreen` already uses for freeform text that is
 * not one of `:designsystem`'s own typed components.
 */
@Composable
fun FirstRunScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    FormLayout(
        primaryActionText = "Continue",
        onPrimaryAction = onGetStarted,
        modifier = modifier,
    ) {
        BasicText(
            text = "Notifications while you work out",
            style = TextStyle(color = colors.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = "Do you want a notification while a workout is running? It shows the current " +
                "interval and lets you pause, resume and skip, even while the screen is off or " +
                "another app is in front. We need the notification permission for that — we'll " +
                "ask you for it next.",
            style = TextStyle(color = colors.fg, fontSize = 16.sp),
            modifier = Modifier.padding(top = spacing.lg),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FirstRunScreenPreview() {
    AppTheme {
        FirstRunScreen(onGetStarted = {})
    }
}
