package com.derekwinters.intervaltrainer

/**
 * The tone vocabulary: exactly these four, and selection never produces a fifth (`CUE-016`).
 */
enum class Tone {
    /** Sounds at the start of a work interval (`CUE-010`). */
    WORK,

    /** Sounds at the start of a recovery interval, a warm-up or a cool-down (`CUE-011`). */
    RECOVERY,

    /** Sounds once per countdown second, never one of the boundary tones (`CUE-012`). */
    TICK,

    /** Sounds at workout completion, none of the three above (`CUE-013`). */
    FINISH,
}

/**
 * The vibration-pattern vocabulary: exactly these four, mirroring [Tone] pattern for tone
 * (`CUE-021`–`023`). Vibration is never suppressed by mute (`CUE-020`).
 */
enum class VibrationPattern {
    WORK,
    RECOVERY,
    TICK,
    FINISH,
}

/**
 * The colour roles an interval kind maps to (`CUE-030`). Warm-up and cool-down share [NEUTRAL]
 * rather than having one each. Actual colour values are the design system's, not this page's or
 * this type's (`CUE-034`).
 */
enum class ColorRole {
    WORK,
    RECOVERY,
    NEUTRAL,
}

/**
 * What one cue carries (`CUE-002`): a [tone] and a [vibration] selected together from the same
 * boundary, so the two channels can never disagree about what just happened.
 *
 * [tone] is `null` exactly when the cue fired while muted (`CUE-050`) — mute silences tones and
 * nothing else, so [vibration] and [colorRole] are never affected by it. [colorRole] is `null` for
 * a tick or the finish, neither of which changes the running screen's colour — only a boundary cue
 * carries one, and it is the colour role of the interval being entered (`CUE-030`).
 */
data class Cue(
    val tone: Tone?,
    val vibration: VibrationPattern,
    val colorRole: ColorRole?,
)

/**
 * Where a running workout's cues are emitted — the far side of cue selection
 * (`docs/spec/cues.md`).
 *
 * `:core` selects (see `CueSelection.kt`'s `boundaryCue`, `tickCue`, `finishCue` and
 * `reduceAndFireCues`); `:app` implements this and does the emitting — the real tone, the real
 * vibration, the real screen colour (`CUE-003`, ADR 0005). A recording fake substitutes it in
 * `:core`'s own tests, turning "a tone fires at each of the last three seconds" into an assertion
 * on a list.
 */
fun interface CueSink {
    /** Emits [cue]. Called once per cue, in the order cues fire. */
    fun fire(cue: Cue)
}
