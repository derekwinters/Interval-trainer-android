package com.derekwinters.intervaltrainer.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 4dp-base spacing scale (`DS-040`): `xs` (4), `sm` (8), `md` (12), `lg` (16), `xl` (20),
 * `xxl` (24). No screen introduces a padding value outside this scale.
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
) {
    companion object {
        val Default = Spacing()
    }
}
