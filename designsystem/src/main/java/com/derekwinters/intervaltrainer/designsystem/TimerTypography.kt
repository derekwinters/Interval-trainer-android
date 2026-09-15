package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * JetBrains Mono, bundled as a resource font (`res/font/jetbrains_mono_regular.ttf`) rather than
 * fetched at build or run time: the SIL Open Font License text that ships with it is kept
 * alongside as `designsystem/OFL-jetbrains-mono.txt`. Bundling avoids adding either a new
 * build-time Maven dependency (the downloadable Google Fonts API's `ui-text-google-fonts`
 * artifact) or a new runtime dependency on Google Play services being present on the device —
 * see this change's pull request description for why this option was chosen over that one.
 */
val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

/**
 * "tnum" is the OpenType feature tag for tabular (fixed-width) figures. JetBrains Mono's glyphs
 * are already monospaced, so this is a belt-and-braces signal rather than a correction — kept
 * because `DS-030`–`032` name "tabular figures" as a requirement in its own right, not merely a
 * side effect of the chosen typeface.
 */
private const val TABULAR_FIGURES = "tnum"

/**
 * The three timer typographic roles (`DS-030`–`033`) — the only shapes a timer value may render
 * in. Neither `docs/spec/design-system.md` nor any other page names a size (sp) for any of the
 * three; the sizes below are this implementation's reasonable choice, not a fixed requirement —
 * see this change's pull request description for that decision.
 */
data class TimerTypography(
    val large: TextStyle,
    val stat: TextStyle,
    val inline: TextStyle,
) {
    companion object {
        val Default = TimerTypography(
            // timer.large (DS-030): the large countdown display.
            large = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 64.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            // timer.stat (DS-031): the round counter and the total-remaining display.
            stat = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 20.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            // timer.inline (DS-032): an inline duration shown in a list row.
            inline = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 16.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}
