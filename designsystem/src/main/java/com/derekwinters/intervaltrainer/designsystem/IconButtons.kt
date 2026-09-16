package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three icon-button size roles (`DS-005`–`007`). `docs/spec/design-system.md` gives status and
 * transport as *approximate* drawn sizes ("approximately 36–40dp", "approximately 50dp"); this
 * implementation picks one concrete value from within each stated range — [StatusIconButtonSize]
 * (38dp) and [TransportIconButtonSize] (50dp) — since a composable needs one, not a range. The FAB
 * role's 56dp is exact (`DS-007`) and matches Material 3's own default `FloatingActionButton` size,
 * so [FabIconButton] below wraps that component directly rather than reimplementing its size.
 */
private val StatusIconButtonSize: Dp = 38.dp
private val TransportIconButtonSize: Dp = 50.dp

/**
 * An icon button in the **status** role (`DS-005`): drawn at approximately 36–40dp
 * ([StatusIconButtonSize]), with a 48dp minimum touch target regardless of the drawn size.
 *
 * The touch target is [Modifier.minimumInteractiveComponentSize], the current Material 3 API for
 * this — the same modifier Material 3's own `IconButton` and `Checkbox` use internally — applied
 * *before* `.size(...)` in the modifier chain so it expands the reported layout size to at least
 * 48dp around the smaller drawn box rather than shrinking the drawn box to fit.
 */
@Composable
fun StatusIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DesignSystemIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        drawnSize = StatusIconButtonSize,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * An icon button in the **transport** role (`DS-006`): the running-screen controls, drawn at
 * approximately 50dp ([TransportIconButtonSize]), with the same 48dp minimum touch target as
 * [StatusIconButton].
 */
@Composable
fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DesignSystemIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        drawnSize = TransportIconButtonSize,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * An icon button in the **FAB** role (`DS-007`): 56dp, Material 3's own default
 * `FloatingActionButton` size, so this wraps that component rather than reimplementing it — it is
 * still reachable only from inside `:designsystem` (`DS-090`), which is the whole point of exposing
 * it here rather than leaving `:app` to reach for `FloatingActionButton` itself.
 */
@Composable
fun FabIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = colors.work,
        contentColor = colors.bg,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/** The shared shape behind [StatusIconButton] and [TransportIconButton]. */
@Composable
private fun DesignSystemIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    drawnSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(drawnSize)
            .background(color = colors.chip, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.fg else colors.dim,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIconButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            StatusIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransportIconButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            TransportIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Resume",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FabIconButtonPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.bg) {
            FabIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New preset",
                onClick = {},
            )
        }
    }
}
