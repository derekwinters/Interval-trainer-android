package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The bespoke colour tokens (`DS-050`), exposed as a `staticCompositionLocalOf` alongside
 * `MaterialTheme` — the mechanism ADR 0007 cites Google's own guidance as recommending for a value
 * Material has no role for, rather than fighting Material's own roles.
 */
val LocalDesignSystemColors = staticCompositionLocalOf { DesignSystemColors.Default }

/** The 4dp-base spacing scale (`DS-040`), exposed the same way as [LocalDesignSystemColors]. */
val LocalSpacing = staticCompositionLocalOf { Spacing.Default }

/**
 * The three timer typographic roles (`DS-030`–`033`), exposed the same way as
 * [LocalDesignSystemColors]. These are not Material `Typography` roles: they name the app's own
 * timer vocabulary, not `bodyLarge`/`titleMedium`/etc.
 */
val LocalTimerTypography = staticCompositionLocalOf { TimerTypography.Default }

/**
 * The app's root theme — named `AppTheme` per ADR 0007's own description of what
 * `:designsystem` exposes. Assembles the bespoke token layer above with a Material 3
 * `ColorScheme` mapped onto the same palette (`DS-051`), so a screen built from this module's
 * bespoke vocabulary and a screen built from stock Material 3 components (`DS-060`–`062`) both
 * read one consistent palette, each in the way that suits it.
 *
 * Mirrors the `MaterialTheme.colorScheme`-style accessor pattern: call `AppTheme { ... }` to wrap
 * content in the root theme, and read `AppTheme.colors`/`AppTheme.spacing`/`AppTheme.timerTypography`
 * from inside it.
 */
object AppTheme {
    val colors: DesignSystemColors
        @Composable get() = LocalDesignSystemColors.current

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val timerTypography: TimerTypography
        @Composable get() = LocalTimerTypography.current

    @Composable
    operator fun invoke(
        colors: DesignSystemColors = DesignSystemColors.Default,
        spacing: Spacing = Spacing.Default,
        timerTypography: TimerTypography = TimerTypography.Default,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalDesignSystemColors provides colors,
            LocalSpacing provides spacing,
            LocalTimerTypography provides timerTypography,
        ) {
            MaterialTheme(
                colorScheme = designSystemColorScheme(colors),
                content = content,
            )
        }
    }
}
