package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.Tone
import com.derekwinters.intervaltrainer.VibrationPattern

/**
 * The four bundled tone assets, one per [Tone] (`CUE-016`: the vocabulary is exactly four and
 * selection never produces a fifth).
 *
 * [resourceName] is the base name of a file under `app/src/main/res/raw`, which is also the name
 * `SoundPool` loads it by — `R.raw.cue_work` for `cue_work.wav`. The name is carried here rather
 * than the resource id so that this whole file stays free of generated Android code and
 * [CueEmissionTest] can load it on a bare JVM; resolving a name to an id is
 * [SoundPoolCueSink]'s job, and that test checks separately that every name here is a file that
 * really exists and really is a container `SoundPool` can decode.
 *
 * The assets themselves are synthesised by `tools/cues/generate_tone_assets.py` from
 * `docs/spec/cues.md` §9's table. Retuning one is editing that script and re-running it, and is
 * explicitly not a specification change — which is why nothing here records a pitch or a duration.
 */
enum class ToneAsset(val resourceName: String) {
    /** `CUE-010`: the start of a work interval. */
    WORK("cue_work"),

    /** `CUE-011`: the start of a recovery interval, a warm-up or a cool-down. */
    RECOVERY("cue_recovery"),

    /** `CUE-012`: one countdown second, never one of the boundary tones. */
    TICK("cue_tick"),

    /** `CUE-013`: workout completion, none of the three above. */
    FINISH("cue_finish"),
}

/**
 * What `:app` emits for one [Cue]: which bundled asset to play, and which waveform to vibrate.
 *
 * [toneAsset] is `null` exactly when the cue was selected while muted (`CUE-050`), because mute
 * silences tones and reaches nothing else. [vibrationTimingsMillis] is never empty and never
 * varies with mute (`CUE-020`): in a muted workout it is the only channel carrying information.
 */
data class CueEmission(
    val toneAsset: ToneAsset?,
    val vibrationTimingsMillis: List<Long>,
)

/**
 * The pure half of the cue sink: a [Cue] `:core` selected in, the asset and the waveform out.
 *
 * This is a separate function rather than a few lines inside [SoundPoolCueSink] because `:app`
 * has no Robolectric (`docs/spec/build.md` `BUILD-023` scopes it to `:designsystem`), so a mapping
 * inlined into the adapter would be a mapping no JVM test could reach — the same argument
 * `docs/spec/cues.md`'s fifth invariant makes one level up, about selection against emission.
 * [SoundPoolCueSink] calls this and does nothing but carry its answer to the platform.
 */
fun emissionFor(cue: Cue): CueEmission = CueEmission(
    toneAsset = cue.tone?.asset(),
    vibrationTimingsMillis = cue.vibration.timingsMillis(),
)

/** `CUE-010`–`CUE-013`: one asset per tone, and no tone shares one with another. */
private fun Tone.asset(): ToneAsset = when (this) {
    Tone.WORK -> ToneAsset.WORK
    Tone.RECOVERY -> ToneAsset.RECOVERY
    Tone.TICK -> ToneAsset.TICK
    Tone.FINISH -> ToneAsset.FINISH
}

/**
 * `docs/spec/cues.md` §9's vibration column, in `VibrationEffect.createWaveform`'s own format: the
 * first entry is the delay before the first pulse and every entry after it alternates on and off,
 * so a pattern has an even length and ends on a pulse. Every one starts at `0` — a cue fires now,
 * and a leading delay would push every vibration late by it.
 *
 * These are §9 starting values, not requirements: what is fixed is that the two boundary patterns
 * differ in **pulse count** (`CUE-021`), that the tick and the finish have their own and the
 * finish is neither boundary pattern (`CUE-022`), and that there are exactly four (`CUE-023`).
 * Which of the boundary pair gets two pulses is §9's guess at which reads as the more urgent.
 * Retuning any of these numbers is not a specification change.
 */
private fun VibrationPattern.timingsMillis(): List<Long> = when (this) {
    VibrationPattern.WORK -> listOf(0L, 120L, 100L, 120L)
    VibrationPattern.RECOVERY -> listOf(0L, 300L)
    VibrationPattern.TICK -> listOf(0L, 40L)
    VibrationPattern.FINISH -> listOf(0L, 150L, 100L, 150L, 100L, 150L)
}
