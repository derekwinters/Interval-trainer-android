package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.CueSink

/**
 * A placeholder [CueSink] that emits nothing.
 *
 * `docs/spec/cues.md`'s tone, vibration and colour-role selection is fully implemented and tested
 * in `:core` (issue #71); what remains is the real Android adapter `CueSink.kt`'s own doc comment
 * describes — "the real tone, the real vibration, the real screen colour" — and no currently filed
 * issue owns building it. This issue (#77) is the service that drives `:core`'s reducer and needs
 * *some* [CueSink] to construct [WorkoutSession] with; wiring in a no-op here rather than leaving
 * the service unbuildable is this pull request's own call — see its Deviations section — and does
 * not claim to satisfy any `CUE` requirement.
 */
object NoOpCueSink : CueSink {
    override fun fire(cue: Cue) = Unit
}
