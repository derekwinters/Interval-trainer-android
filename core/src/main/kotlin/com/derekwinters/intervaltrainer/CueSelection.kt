package com.derekwinters.intervaltrainer

/** Every countdown is exactly three ticks — no more, no fewer (`CUE-040`, `CUE-041`, `TIMER-071`). */
private const val TICK_COUNT = 3

/** CUE-010, CUE-011: the tone at the start of an interval of this kind. */
private fun IntervalKind.boundaryTone(): Tone = when (this) {
    IntervalKind.WORK -> Tone.WORK
    IntervalKind.RECOVERY, IntervalKind.WARM_UP, IntervalKind.COOL_DOWN -> Tone.RECOVERY
}

/** CUE-021: the vibration pattern at the start of an interval of this kind, mirroring the tone. */
private fun IntervalKind.boundaryVibration(): VibrationPattern = when (this) {
    IntervalKind.WORK -> VibrationPattern.WORK
    IntervalKind.RECOVERY, IntervalKind.WARM_UP, IntervalKind.COOL_DOWN -> VibrationPattern.RECOVERY
}

/**
 * CUE-030: the colour role of an interval of this kind. Warm-up and cool-down share
 * [ColorRole.NEUTRAL].
 *
 * Public, not internal: [scheduleSegments] (`PresetSummary.kt`, `docs/spec/screens.md`
 * `SCREEN-004`) reads this same mapping for the home screen's colour strip, and the preset editor
 * row (`docs/spec/screens.md` `SCREEN-012`'s colour dot) reads it directly from `:app` for the
 * same reason — one mapping this file owns, not a second copy of `CUE-030` living outside it or
 * duplicated at each call site.
 */
fun IntervalKind.colorRole(): ColorRole = when (this) {
    IntervalKind.WORK -> ColorRole.WORK
    IntervalKind.RECOVERY -> ColorRole.RECOVERY
    IntervalKind.WARM_UP, IntervalKind.COOL_DOWN -> ColorRole.NEUTRAL
}

/**
 * The cue at the start of an interval of [kind] (`CUE-010`, `CUE-011`, `CUE-021`, `CUE-030`),
 * muted per [muted] (`CUE-050`) — the pure function the cue-selection invariant in
 * `docs/spec/cues.md` describes: a boundary and the current mute state in, a tone, a vibration and
 * a colour role out.
 */
fun boundaryCue(kind: IntervalKind, muted: Boolean): Cue = Cue(
    tone = if (muted) null else kind.boundaryTone(),
    vibration = kind.boundaryVibration(),
    colorRole = kind.colorRole(),
)

/** One countdown tick (`CUE-012`, `CUE-022`), muted per [muted] (`CUE-050`). No colour change. */
fun tickCue(muted: Boolean): Cue = Cue(
    tone = if (muted) null else Tone.TICK,
    vibration = VibrationPattern.TICK,
    colorRole = null,
)

/** The workout-finished cue (`CUE-013`, `CUE-022`), muted per [muted] (`CUE-050`). No colour change. */
fun finishCue(muted: Boolean): Cue = Cue(
    tone = if (muted) null else Tone.FINISH,
    vibration = VibrationPattern.FINISH,
    colorRole = null,
)

/**
 * [TimerState] plus the bookkeeping cue firing needs that the timer state machine has no reason to
 * carry (`TIMER-033`'s paragraph in `docs/spec/timer.md`): [ticksFired], how many of the current
 * phase's three countdown ticks have already sounded, so [reduceAndFireCues] does not re-fire one
 * on a later call once its instant has passed; and [muted], the workout's own effective mute
 * (`CUE-051`), which the timer state machine has no notion of at all.
 *
 * [muted] is never written back to whatever default it started from (`CUE-052`) — nothing here
 * ever reads a "default" after [reduceAndFireCues] consumes it for a [TimerEvent.Start] — and it
 * does not survive past the workout it belongs to (`CUE-053`): the *next* [TimerEvent.Start] sets
 * it fresh from whatever default that call is given, ignoring whatever this one was left at.
 */
data class CueTimerState(
    val timer: TimerState = TimerState.Idle,
    val ticksFired: Int = 0,
    val muted: Boolean = false,
)

/** CUE-052: flips effective mute. Takes no default and touches none — there is none to write back to. */
fun CueTimerState.toggleMuted(): CueTimerState = copy(muted = !muted)

/** A [Clock] that always answers [millis], so [reduce] and this file's own range check agree on "now". */
private class FixedInstant(private val millis: Long) : Clock {
    override fun nowMillis(): Long = millis
}

private fun TimerState.phaseOrNull(): TimerPhase? = when (this) {
    is TimerState.Running -> phase
    is TimerState.Paused -> phase
    else -> null
}

/**
 * Applies [event] to [state] via [reduce] and fires, through [sink], every cue the transition
 * produces — interval boundaries, the finish, and the countdown ticks due since the last call
 * (`CUE-001`) — consulting mute at the instant each cue fires, never earlier (`CUE-055`).
 *
 * [defaultMuted] is read only for a [TimerEvent.Start] that is actually accepted: it becomes the
 * workout's fresh effective mute (`CUE-051`) before anything for this call fires, so the very
 * first cue of a new workout already reflects it. Every other event keeps [state]'s current
 * [CueTimerState.muted] unless [CueTimerState.toggleMuted] changed it on a call in between —
 * exactly the observable effect `CUE-055` names: a mute change takes effect on the very next cue,
 * because the value read here is [state]'s as of *this* call, not one cached at some earlier
 * instant.
 *
 * **Ticks.** A countdown tick's instant is the current phase's deadline minus three, two or one
 * second (`CUE-040`, `CUE-041`; the same formula counts down the lead-in as any interval, and
 * `TIMER-070`'s five-second minimum is what keeps the earliest of the three from landing on or
 * before the previous boundary, `CUE-044`). Entering a *fresh* lead-in (`TimerEvent.Start` or
 * `TimerEvent.Skip`, `CUE-041`, `CUE-045`) fires its first tick immediately, because that instant
 * *is* the lead-in's own deadline minus three seconds. Every other due tick is caught by sweeping
 * the phase in force **before** this event from [CueTimerState.ticksFired] up to three, each time
 * `now` has reached that tick's threshold — which is how a [TimerEvent.Tick] call is expected to
 * behave, one second (or less) after the last one, catching up on however many of the three
 * thresholds it has newly crossed.
 *
 * **Boundaries and the finish.** Comparing the phase before this event with the phase after tells
 * the rest: entering a schedule interval (from a lead-in, `TIMER-033`, or from the interval before
 * it) fires that interval's boundary cue (`CUE-010`, `CUE-011`, `CUE-030`); the workout ending
 * *completed* — whether the last interval's own deadline was reached (`CUE-043`) or it was skipped
 * (`TIMER-043`) — fires the finish cue (`CUE-013`); ending *stopped early* fires nothing
 * (`TIMER-050`); and abandoning a countdown mid-flight by skipping fires nothing for what was
 * abandoned (`CUE-045`) — only the fresh lead-in's first tick, per the paragraph above.
 *
 * One assumption this makes explicit rather than defending against: **at most one boundary is
 * crossed per call.** A [TimerEvent.Tick] arriving late enough to cross more than one deadline in
 * a single call — the scenario `TIMER-022` already accepts rather than compensates for — is
 * assumed not to happen for cue-firing purposes, because whatever drives this function in
 * production is expected to call it at least once a second, far inside the five-second minimum
 * interval (`TIMER-070`). If it ever did happen, this fires the boundary or finish cue for wherever
 * [reduce] actually landed and nothing for whatever was skipped over in between — silently missing
 * cues, never firing wrong ones.
 */
fun reduceAndFireCues(
    state: CueTimerState,
    event: TimerEvent,
    clock: Clock,
    sink: CueSink,
    defaultMuted: Boolean = state.muted,
): CueTimerState {
    val now = clock.nowMillis()
    val previousTimer = state.timer
    val nextTimer = reduce(previousTimer, event, FixedInstant(now))

    if (nextTimer === previousTimer) {
        // TIMER-012: no cell for this (state, event) pair. Nothing changed, nothing fires.
        return state
    }

    val muted = if (event is TimerEvent.Start) defaultMuted else state.muted
    var ticksFired = state.ticksFired

    if (event is TimerEvent.Tick && previousTimer is TimerState.Running) {
        val deadline = previousTimer.deadlineMillis
        while (ticksFired < TICK_COUNT && now >= deadline - (TICK_COUNT - ticksFired) * 1_000L) {
            sink.fire(tickCue(muted))
            ticksFired++
        }
    }

    val previousPhase = previousTimer.phaseOrNull()
    val nextRunning = nextTimer as? TimerState.Running

    when {
        nextRunning != null && nextRunning.phase != previousPhase -> {
            when (val nextPhase = nextRunning.phase) {
                is TimerPhase.LeadIn -> {
                    sink.fire(tickCue(muted)) // the fresh countdown's own first tick
                    ticksFired = 1
                }

                is TimerPhase.InInterval -> {
                    val kind = nextRunning.schedule[nextPhase.index].interval.kind
                    sink.fire(boundaryCue(kind, muted))
                    ticksFired = 0
                }
            }
        }

        nextTimer is TimerState.Ended && previousTimer !is TimerState.Ended -> {
            if (nextTimer.outcome == WorkoutOutcome.COMPLETED) sink.fire(finishCue(muted))
            ticksFired = 0
        }
    }

    return CueTimerState(timer = nextTimer, ticksFired = ticksFired, muted = muted)
}
