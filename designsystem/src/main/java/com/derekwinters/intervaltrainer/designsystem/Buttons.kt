package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/**
 * `PrimaryButton` (`DS-001`): filled, `work`-green fill, dark ink text, one fixed padding, weight
 * 700. It is the single primary action per screen — Start, Save.
 *
 * "Dark ink" reads [DesignSystemColors.bg] (the token layer's darkest tone) rather than a raw
 * black, so the text stays legible against `work` without introducing a colour the token layer
 * does not name. "Weight 700" is [FontWeight.Bold], whose numeric value is exactly 700 — not a
 * separate literal.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.work,
            contentColor = colors.bg,
            disabledContainerColor = colors.work.copy(alpha = DisabledAlpha),
            disabledContentColor = colors.bg.copy(alpha = DisabledAlpha),
        ),
        // "One fixed padding" (DS-001): a single content-padding pair, read from the spacing
        // scale, that never varies by call site.
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/**
 * `SecondaryButton` (`DS-002`): a dashed border, `chip` fill, `dim` text. Used for optional or
 * additive actions — `+ Interval`, `+ Rounds…`.
 *
 * Material's [BorderStroke] has no dashed variant, so the dashed outline is drawn directly with
 * [PathEffect.dashPathEffect] rather than reached for as a raw Material style. The stroke width and
 * the dash/gap lengths are not named by `docs/spec/design-system.md`; this implementation takes
 * them from the spacing scale (`DS-040`) directly — [Spacing.xs] for the stroke and the dash, half
 * of it for the gap — rather than inventing a value outside it (`DS-052`). The border colour reads
 * [DesignSystemColors.line], the token the palette already reserves for outlines
 * (`DS-051`: `outline`←`line`).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val shape = RoundedCornerShape(spacing.sm)
    Button(
        onClick = onClick,
        modifier = modifier.dashedBorder(
            color = colors.line,
            strokeWidth = spacing.xs,
            dashLength = spacing.xs,
            gapLength = spacing.xs / 2,
            shape = shape,
        ),
        enabled = enabled,
        shape = shape,
        border = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.chip,
            contentColor = colors.dim,
            disabledContainerColor = colors.chip.copy(alpha = DisabledAlpha),
            disabledContentColor = colors.dim.copy(alpha = DisabledAlpha),
        ),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/**
 * The destructive action styling (`DS-003`): rendered in red, reserved for a confirmed or
 * saved-state deletion — preset deletion, the stop-workout confirmation.
 *
 * `docs/spec/design-system.md` states `DS-003` as a rule about colour ("rendered in red"), not as
 * a new button shape, and does not say whether it is a variant of `PrimaryButton` or a component of
 * its own. Making it a boolean flag on `PrimaryButton` would contradict `DS-001`, which fixes
 * `PrimaryButton` to a `work`-green fill as part of what the name means. This implementation
 * therefore gives it its own composable, `DestructiveButton`, mirroring `PrimaryButton`'s shape
 * (filled, one fixed padding, weight 700) with [DesignSystemColors.destructive] in place of `work`
 * and [DesignSystemColors.fg] as ink (white reads better against the saturated destructive red than
 * the dark ink `PrimaryButton` uses against `work`'s lighter green) — see this change's pull
 * request description for that decision. `AlertDialog`'s own destructive affirmative action
 * (`DS-011`) is styled separately, as red *text*, not as this filled button (see `AlertDialog.kt`).
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(spacing.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.destructive,
            contentColor = colors.fg,
            disabledContainerColor = colors.destructive.copy(alpha = DisabledAlpha),
            disabledContentColor = colors.fg.copy(alpha = DisabledAlpha),
        ),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/** Shared disabled-state alpha for the button variants above; not a colour or a dimension. */
private const val DisabledAlpha = 0.38f

/**
 * Draws a dashed outline on top of the content, since neither `Modifier.border` nor [BorderStroke]
 * supports a dash pattern — only [PathEffect.dashPathEffect] against the shape's own outline does.
 * Kept private to this file: [SecondaryButton] is the only `DS-002` shape that needs it.
 */
private fun Modifier.dashedBorder(
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Dp,
    dashLength: Dp,
    gapLength: Dp,
    shape: Shape,
): Modifier = drawWithContent {
    drawContent()
    val strokeWidthPx = strokeWidth.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is androidx.compose.ui.graphics.Outline.Rounded ->
            androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }

        is androidx.compose.ui.graphics.Outline.Rectangle ->
            androidx.compose.ui.graphics.Path().apply { addRect(outline.rect) }

        is androidx.compose.ui.graphics.Outline.Generic -> outline.path
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            PrimaryButton(text = "Start", onClick = {}, modifier = Modifier)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SecondaryButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            SecondaryButton(text = "+ Interval", onClick = {}, modifier = Modifier)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DestructiveButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            DestructiveButton(text = "Delete preset", onClick = {}, modifier = Modifier)
        }
    }
}
