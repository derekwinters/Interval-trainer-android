package com.derekwinters.intervaltrainer

/**
 * Where a running workout's cues are emitted — the far side of cue selection
 * (`docs/spec/cues.md`).
 *
 * `:core` selects; `:app` implements this and does the emitting (`CUE-003`, ADR 0005). A
 * recording fake substitutes it in `:core`'s own tests, turning "a tone fires at each of the last
 * three seconds" into an assertion on a list.
 *
 * This issue defines the seam only, with no member yet. Cue selection (`CUE-001`–`073`: what a
 * cue actually carries — a tone, a vibration pattern, a colour role) has no implementation yet
 * and is a later issue's own question; giving this interface a member ahead of that would decide
 * cue selection's shape here instead of there. It gains one when that issue lands.
 */
interface CueSink
