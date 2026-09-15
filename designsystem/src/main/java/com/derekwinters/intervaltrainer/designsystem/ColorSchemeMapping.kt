package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

/**
 * Maps [DesignSystemColors] onto Material 3's [ColorScheme] roles (`DS-051`, ADR 0007 Decision 2):
 * `surface`←`bg`, `onSurface`←`fg`, `primary`←`work` (the app's accent), `outline`←`line`,
 * `surfaceVariant`←`chip`, `error`←`destructive`. This is what keeps the root `MaterialTheme` off
 * Material's baseline purple/teal defaults — including for the three stock-Material screens
 * (summary, settings, first-run; `DS-061`) that have no bespoke wrapper to carry the palette
 * instead of the `ColorScheme` roles they read directly.
 *
 * `background`/`onBackground` are mapped the same way as `surface`/`onSurface` for the same
 * reason, and the "on-" companions of the three text/icon-bearing mapped roles (`onPrimary`,
 * `onSurfaceVariant`, `onError`) are set to readable ink for this palette. `DS-051` does not name
 * any of those individually; leaving them on Material's own defaults would put unrelated ink next
 * to this palette's fills, which is the same defect `DS-051`'s own six mappings exist to prevent.
 */
fun designSystemColorScheme(colors: DesignSystemColors = DesignSystemColors.Default): ColorScheme =
    darkColorScheme(
        primary = colors.work,
        onPrimary = colors.bg,
        surface = colors.bg,
        onSurface = colors.fg,
        background = colors.bg,
        onBackground = colors.fg,
        outline = colors.line,
        surfaceVariant = colors.chip,
        onSurfaceVariant = colors.dim,
        error = colors.destructive,
        onError = colors.bg,
    )
