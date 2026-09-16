package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * `Toggle` (`DS-014`): a bespoke on/off switch — track and knob, no Material `Switch` — for a
 * boolean value a bespoke-vocabulary screen (`DS-060`) needs to show and flip. The round
 * generator's own trailing-recovery control (`docs/spec/screens.md` `SCREEN-017`, defaulting off
 * per `TIMER-082`) is the first caller, and the only one in v1: settings' default-mute switch
 * (`SCREEN-061`) is a stock Material 3 screen (`DS-061`) and uses `androidx.compose.material3.Switch`
 * directly there, not this component — `DS-060`'s split is what decides which vocabulary a screen
 * draws from, the same way `AlertDialog` and `ScreenHeader` are shared but a stock-Material screen's
 * own controls are not drawn from this module beyond those two.
 *
 * Bespoke rather than a thin wrapper over `androidx.compose.material3.Switch` for the same reason
 * [CountStepper]'s `−`/`+` glyphs are plain text: nothing in `docs/spec/design-system.md` names a
 * toggle before this file, so its exact shape — track, knob, the colours it reads — is this
 * component's own reasonable choice, not a value recovered from an existing decision. It reads
 * `colors.work` for the on-state track (the same association `CUE-030` already gives "on" a
 * meaning for elsewhere) and `colors.chip` for off, both already-defined tokens rather than a new
 * one (`DS-052`).
 */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val trackColor = if (checked) colors.work else colors.chip
    // A local val, not `contentDescription` used directly inside the `semantics {}` block below:
    // that block's implicit receiver is `SemanticsPropertyReceiver`, which has its own
    // `contentDescription` extension var, and writing `contentDescription = contentDescription`
    // there would be ambiguous between this composable's own parameter and that receiver's
    // property of the same name.
    val description = contentDescription
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = TrackWidth, height = TrackHeight)
            .clip(TrackShape)
            .background(trackColor)
            .semantics { this.contentDescription = description }
            .clickable(
                role = Role.Switch,
                onClickLabel = description,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(KnobPadding)
                .size(KnobSize)
                .clip(CircleShape)
                .background(colors.fg),
        )
    }
}

private val TrackWidth = 44.dp
private val TrackHeight = 24.dp
private val KnobSize = 18.dp
private val KnobPadding = 3.dp
private val TrackShape = RoundedCornerShape(percent = 50)

@Preview(showBackground = true)
@Composable
private fun TogglePreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            var checked by remember { mutableStateOf(false) }
            Toggle(
                checked = checked,
                onCheckedChange = { checked = it },
                contentDescription = "Trailing recovery",
            )
        }
    }
}
