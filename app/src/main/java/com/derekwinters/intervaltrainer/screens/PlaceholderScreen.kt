package com.derekwinters.intervaltrainer.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.derekwinters.intervaltrainer.designsystem.AppTheme

/**
 * A destination home (`docs/spec/screens.md` §1, `SCREEN-006`–`008`) navigates to that has no
 * screen of its own yet: settings (§5) is the one remaining unbuilt screen. The preset editor
 * (§2, `#80`), the running screen (§3, `#81`) and the summary screen (§4, `#82`) were this way
 * too, each until its own pull request replaced its placeholder with a real screen. The *route*
 * settings navigates to is real, so home's own navigation is not guessing at a destination that
 * does not exist; the composable behind that route is this one, until the issue that owns it
 * replaces it.
 *
 * Not a screen in its own right, and not asserted against `docs/spec/design-system.md`'s layouts
 * (`DS-070`–`079`) for that reason — see this pull request's Deviations section.
 */
@Composable
fun PlaceholderScreen(label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(AppTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = label, style = TextStyle(color = colors.fg))
    }
}
