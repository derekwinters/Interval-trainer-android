package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

/**
 * The two test tags [DesignSystemConsistencyTest] selects by (`DS-091`), rather than a
 * content-based heuristic that would break the moment a label changes. [TouchTargetTag] marks a
 * component this page's `DS-005`–`008` names a 48dp touch-target floor for — every icon-button role
 * and, via [CountStepperTag] plus [hasAnyAncestor][androidx.compose.ui.test.hasAnyAncestor] in the
 * test, `CountStepper`'s own `−`/`+` controls, which have no external modifier of their own to tag
 * directly. Neither tag reaches into `IconButtons.kt` or `CountStepper.kt`: both already expose a
 * `modifier` parameter this file uses from the outside, so `#78` adds no test-only surface to `#73`'s
 * components.
 */
internal const val TouchTargetTag = "designsystem-gallery-touch-target"
internal const val CountStepperTag = "designsystem-gallery-count-stepper"

/**
 * The component gallery (`DS-095`): every component from the vocabulary in §1–3 of
 * `docs/spec/design-system.md` ([#73](https://github.com/derekwinters/Interval-trainer-android/issues/73))
 * composed together, so `DesignSystemConsistencyTest.kt`'s semantics-tree assertions (`DS-091`) have
 * one screen to walk rather than one per component: every button variant, all three icon-button
 * roles, `CountStepper`, the duration picker, `ScreenHeader`, `Toggle`, `StockListItem` (plain and
 * with a trailing `Toggle`, `DS-016`), and `AlertDialog`.
 *
 * This is not one of the six v1 screens (`DS-060`–`062`), and composing it proves nothing about
 * `DS-093` — there is no real screen yet for that assertion to compare against (`#33`). It still
 * fills the list layout from §8.1 rather than arranging its own header and spacing, per this page's
 * first invariant, so `ScreenHeader`'s position here is real production wiring (`DS-020`), not a
 * value this fixture invents for the test to find. `showDialog` starts `true` so `AlertDialog` is
 * part of this same composition from the first frame, rather than sitting behind a click the test
 * would have to simulate before it could inspect the dialog at all (`DS-095`).
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    var stepperCount by remember { mutableIntStateOf(4) }
    var minutes by remember { mutableIntStateOf(1) }
    var seconds by remember { mutableIntStateOf(30) }
    var toggleChecked by remember { mutableStateOf(false) }
    var trailingToggleChecked by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(true) }

    ListLayout(
        title = "Component gallery",
        modifier = modifier,
        trailingAction = ScreenHeaderAction.Icon(
            icon = Icons.Filled.Settings,
            contentDescription = "Gallery settings",
            onClick = {},
        ),
    ) {
        gallerySection(title = "Buttons (DS-001–003)") {
            PrimaryButton(text = "Start", onClick = {})
            SecondaryButton(text = "+ Interval", onClick = {})
            DestructiveButton(text = "Delete preset", onClick = {})
        }
        gallerySection(title = "Icon buttons (DS-005–007)") {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
                StatusIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Status settings",
                    onClick = {},
                    modifier = Modifier.testTag(TouchTargetTag),
                )
                TransportIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Resume",
                    onClick = {},
                    modifier = Modifier.testTag(TouchTargetTag),
                )
                FabIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New preset",
                    onClick = {},
                    modifier = Modifier.testTag(TouchTargetTag),
                )
            }
        }
        gallerySection(title = "Count stepper (DS-008)") {
            CountStepper(
                count = stepperCount,
                onCountChange = { stepperCount = it },
                modifier = Modifier.testTag(CountStepperTag),
            )
        }
        gallerySection(title = "Duration picker (DS-009)") {
            DurationScrollPicker(
                minutes = minutes,
                seconds = seconds,
                onDurationChange = { newMinutes, newSeconds ->
                    minutes = newMinutes
                    seconds = newSeconds
                },
            )
        }
        gallerySection(title = "Toggle (DS-014)") {
            Toggle(
                checked = toggleChecked,
                onCheckedChange = { toggleChecked = it },
                contentDescription = "Trailing recovery",
                modifier = Modifier.testTag(TouchTargetTag),
            )
        }
        gallerySection(title = "Stock list item (DS-015)") {
            StockListItem(headline = "Rounds completed", supportingText = "3 of 5")
        }
        gallerySection(title = "Stock list item with a trailing toggle (DS-016)") {
            StockListItem(
                headline = "Default mute",
                trailingContent = {
                    Toggle(
                        checked = trailingToggleChecked,
                        onCheckedChange = { trailingToggleChecked = it },
                        contentDescription = "Default mute",
                        modifier = Modifier.testTag(TouchTargetTag),
                    )
                },
            )
        }
        gallerySection(title = "Dialog (DS-010–012)") {
            SecondaryButton(
                text = if (showDialog) "Hide dialog" else "Show dialog",
                onClick = { showDialog = !showDialog },
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            title = "Delete preset?",
            text = "This preset will be removed permanently.",
            confirmText = "Delete",
            onConfirm = { showDialog = false },
            onDismissRequest = { showDialog = false },
            cancelText = "Cancel",
            isConfirmDestructive = true,
        )
    }
}

/** One labelled section of the gallery's list content, laid out with the spacing scale (`DS-040`). */
private fun LazyListScope.gallerySection(title: String, content: @Composable () -> Unit) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.dim,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ComponentGalleryPreview() {
    AppTheme {
        ComponentGallery()
    }
}
