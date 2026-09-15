package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for cue selection (`docs/spec/cues.md`): every `auto`-tagged requirement in that
 * page, exercised against a [RecordingCueSink] rather than a real tone or vibration (ADR 0005).
 * `TIMER-033` and `TIMER-071` (`docs/spec/timer.md` §4, §8) are exercised here too — the timer
 * state machine has no notion of a tick or a boundary cue at all, so neither could ever have been
 * `TimerStateTest.kt`'s to assert.
 */
class CueSelectionTest {

    private val work5 = Interval(IntervalKind.WORK, durationSeconds = 5)
    private val recovery5 = Interval(IntervalKind.RECOVERY, durationSeconds = 5)
    private val coolDown6 = Interval(IntervalKind.COOL_DOWN, durationSeconds = 6)

    // ---- §1 What a cue is (CUE-001–002) --------------------------------------------------------

    /**
     * CUE-002: a cue's tone and vibration always come from the same boundary, so the two channels
     * can never disagree about what just happened.
     */
    @Test
    fun `a cue's tone and vibration always agree on what happened`() {
        assertEquals(Cue(Tone.WORK, VibrationPattern.WORK, ColorRole.WORK), boundaryCue(IntervalKind.WORK, muted = false))
        assertEquals(
            Cue(Tone.RECOVERY, VibrationPattern.RECOVERY, ColorRole.RECOVERY),
            boundaryCue(IntervalKind.RECOVERY, muted = false),
        )
        assertEquals(Cue(Tone.TICK, VibrationPattern.TICK, null), tickCue(muted = false))
        assertEquals(Cue(Tone.FINISH, VibrationPattern.FINISH, null), finishCue(muted = false))
    }

    /**
     * CUE-001: a cue occurs at exactly four kinds of moment — the start of the workout (the
     * lead-in's own first tick), the start of each interval, each countdown second, and workout
     * completion. Driving a whole two-interval workout end to end, second by second, fires exactly
     * the sequence those four kinds predict and nothing else.
     */
    @Test
    fun `a full workout fires exactly the ticks, boundaries and finish the schedule predicts`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = CueTimerState()

        state = reduceAndFireCues(state, TimerEvent.Start(listOf(work5, recovery5)), clock, sink)
        // Lead-in (deadline 3s): the remaining two ticks, then the boundary into work5 (deadline 8s).
        for (t in listOf(1_000L, 2_000L, 3_000L)) {
            clock.advanceTo(t)
            state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        }
        // work5's own countdown (deadline 8s), then the boundary into recovery5 (deadline 13s).
        for (t in listOf(5_000L, 6_000L, 7_000L, 8_000L)) {
            clock.advanceTo(t)
            state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        }
        // recovery5's own countdown (deadline 13s), then completion.
        for (t in listOf(10_000L, 11_000L, 12_000L, 13_000L)) {
            clock.advanceTo(t)
            state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        }

        assertEquals(
            listOf(
                tickCue(false), tickCue(false), tickCue(false), boundaryCue(IntervalKind.WORK, false),
                tickCue(false), tickCue(false), tickCue(false), boundaryCue(IntervalKind.RECOVERY, false),
                tickCue(false), tickCue(false), tickCue(false), finishCue(false),
            ),
            sink.fired,
        )
        check(state.timer is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, (state.timer as TimerState.Ended).outcome)
    }

    // ---- §2 Tones (CUE-010–013, 016) ------------------------------------------------------------

    @Test
    fun `work tone sounds at the start of a work interval`() {
        assertEquals(Tone.WORK, boundaryCue(IntervalKind.WORK, muted = false).tone)
    }

    /** CUE-011: recovery, warm-up and cool-down starts all sound the recovery tone. */
    @Test
    fun `recovery tone sounds at the start of recovery, warm-up and cool-down`() {
        for (kind in listOf(IntervalKind.RECOVERY, IntervalKind.WARM_UP, IntervalKind.COOL_DOWN)) {
            assertEquals(Tone.RECOVERY, boundaryCue(kind, muted = false).tone)
        }
    }

    @Test
    fun `a countdown tick sounds the tick tone, never a boundary tone`() {
        assertEquals(Tone.TICK, tickCue(muted = false).tone)
    }

    @Test
    fun `the finish tone sounds at completion and is none of the other three`() {
        val finish = finishCue(muted = false).tone
        assertEquals(Tone.FINISH, finish)
        assertTrue(finish != Tone.WORK && finish != Tone.RECOVERY && finish != Tone.TICK)
    }

    /** CUE-016: the tone vocabulary is exactly four; selection never produces a fifth. */
    @Test
    fun `the tone vocabulary is exactly four`() {
        assertEquals(4, Tone.values().size)
        val produced = IntervalKind.values().map { boundaryCue(it, muted = false).tone }.toSet() +
            setOf(tickCue(muted = false).tone, finishCue(muted = false).tone)
        assertEquals(setOf(Tone.WORK, Tone.RECOVERY, Tone.TICK, Tone.FINISH), produced)
    }

    // ---- §3 Vibration (CUE-020–023) --------------------------------------------------------------

    /** CUE-020: mute never suppresses vibration. */
    @Test
    fun `mute does not suppress vibration`() {
        assertEquals(VibrationPattern.WORK, boundaryCue(IntervalKind.WORK, muted = true).vibration)
        assertEquals(VibrationPattern.TICK, tickCue(muted = true).vibration)
        assertEquals(VibrationPattern.FINISH, finishCue(muted = true).vibration)
    }

    /** CUE-021: work start and recovery start have distinct patterns; warm-up/cool-down use recovery's. */
    @Test
    fun `work and recovery vibration patterns are distinct, warm-up and cool-down use recovery's`() {
        val work = boundaryCue(IntervalKind.WORK, muted = false).vibration
        val recovery = boundaryCue(IntervalKind.RECOVERY, muted = false).vibration
        assertTrue(work != recovery)
        assertEquals(recovery, boundaryCue(IntervalKind.WARM_UP, muted = false).vibration)
        assertEquals(recovery, boundaryCue(IntervalKind.COOL_DOWN, muted = false).vibration)
    }

    /** CUE-022: the tick and the finish have their own patterns; the finish is neither boundary pattern. */
    @Test
    fun `tick and finish vibration patterns are their own, finish is not a boundary pattern`() {
        val tick = tickCue(muted = false).vibration
        val finish = finishCue(muted = false).vibration
        val work = boundaryCue(IntervalKind.WORK, muted = false).vibration
        val recovery = boundaryCue(IntervalKind.RECOVERY, muted = false).vibration
        assertTrue(tick != work && tick != recovery && tick != finish)
        assertTrue(finish != work && finish != recovery)
    }

    /** CUE-023: the pattern vocabulary is exactly four. */
    @Test
    fun `the vibration pattern vocabulary is exactly four`() {
        assertEquals(4, VibrationPattern.values().size)
    }

    // ---- §4 Colour (CUE-030) -----------------------------------------------------------------------

    @Test
    fun `each interval kind maps to its colour role, warm-up and cool-down sharing the neutral`() {
        assertEquals(ColorRole.WORK, boundaryCue(IntervalKind.WORK, muted = false).colorRole)
        assertEquals(ColorRole.RECOVERY, boundaryCue(IntervalKind.RECOVERY, muted = false).colorRole)
        assertEquals(ColorRole.NEUTRAL, boundaryCue(IntervalKind.WARM_UP, muted = false).colorRole)
        assertEquals(ColorRole.NEUTRAL, boundaryCue(IntervalKind.COOL_DOWN, muted = false).colorRole)
    }

    @Test
    fun `a tick and the finish carry no colour role`() {
        assertNull(tickCue(muted = false).colorRole)
        assertNull(finishCue(muted = false).colorRole)
    }

    // ---- §5 The countdown (CUE-040–045, TIMER-033, TIMER-071) --------------------------------------

    /**
     * CUE-041, TIMER-071: starting gives three ticks before the first interval begins — the first
     * one immediately, since the lead-in's deadline-minus-three-seconds is the instant it begins.
     */
    @Test
    fun `starting the workout fires three ticks before the first interval's boundary cue`() {
        val clock = CueFakeClock(1_000L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink)
        assertEquals(listOf(tickCue(false)), sink.fired)

        clock.advanceBy(1_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        clock.advanceBy(1_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)

        assertEquals(listOf(tickCue(false), tickCue(false), tickCue(false)), sink.fired)
        check(state.timer is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(0), (state.timer as TimerState.Running).phase)
    }

    /**
     * TIMER-033: the lead-in fires the countdown and no boundary cue of its own — the boundary cue
     * belongs to the interval that follows, and fires only when the lead-in's deadline is reached.
     */
    @Test
    fun `the lead-in fires no boundary cue of its own, only the countdown`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink)
        clock.advanceBy(1_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        clock.advanceBy(1_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // t=2s: still mid-lead-in

        // Three ticks so far, and nothing carrying a colour role (a boundary cue always does, CUE-030).
        assertEquals(3, sink.fired.size)
        assertTrue(sink.fired.all { it.colorRole == null })

        clock.advanceBy(1_000L) // t=3s: the lead-in's deadline
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)

        assertEquals(4, sink.fired.size)
        assertEquals(boundaryCue(IntervalKind.WORK, muted = false), sink.fired.last())
        check(state.timer is TimerState.Running)
        assertEquals(TimerPhase.InInterval(0), (state.timer as TimerState.Running).phase)
    }

    /**
     * CUE-040, TIMER-071: every interval is counted down over its own final three seconds, one
     * tick per second — not just the first one after the lead-in.
     */
    @Test
    fun `every interval in the schedule gets its own three-tick countdown`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5, recovery5)), clock, sink)
        clock.advanceTo(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5 at t=3s, deadline=8s
        sink.fired.clear()

        // work5's own countdown begins at deadline-3s and the boundary into recovery5 fires at 8s.
        for (t in listOf(5_000L, 6_000L, 7_000L, 8_000L)) {
            clock.advanceTo(t)
            state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        }

        assertEquals(
            listOf(tickCue(false), tickCue(false), tickCue(false), boundaryCue(IntervalKind.RECOVERY, false)),
            sink.fired,
        )
    }

    /**
     * CUE-042, CUE-044: the minimum interval (five seconds) does not truncate the countdown — all
     * three ticks land inside it, the earliest two seconds after it begins — and a tick never
     * coincides with the boundary cue: nothing new fires the instant before either is due.
     */
    @Test
    fun `the minimum interval fits all three ticks with a second to spare before the boundary`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5, recovery5)), clock, sink)
        clock.advanceBy(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5 at t=3s, deadline=8s
        sink.fired.clear()

        clock.advanceBy(1_999L) // t=4.999s: one millisecond before work5's first tick (deadline-3s=5s)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(0, sink.fired.size)

        clock.advanceBy(1L) // t=5s exactly
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(1, sink.fired.size)

        clock.advanceBy(999L) // t=5.999s: one millisecond before the next tick
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(1, sink.fired.size)

        clock.advanceBy(1_001L) // t=7s: the third tick
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(3, sink.fired.size)

        clock.advanceBy(999L) // t=7.999s: one millisecond before the boundary
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(3, sink.fired.size)

        clock.advanceBy(1L) // t=8s exactly: the boundary, one full second after the third tick
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        assertEquals(4, sink.fired.size)
        assertEquals(boundaryCue(IntervalKind.RECOVERY, false), sink.fired.last())
    }

    /** CUE-043: the last interval's own countdown runs into the finish cue, not a boundary cue. */
    @Test
    fun `the last interval's countdown runs into the finish, not a boundary cue`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink)
        clock.advanceBy(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5, deadline=8s
        clock.advanceBy(5_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // t=8s: completion

        assertEquals(finishCue(false), sink.fired.last())
        check(state.timer is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, (state.timer as TimerState.Ended).outcome)
    }

    /**
     * CUE-045: a countdown already running when skip is accepted is abandoned — its remaining
     * ticks never fire — and the lead-in it moves into is counted down fresh, from three.
     */
    @Test
    fun `skip abandons the current countdown and gives the next interval a fresh three-tick lead-in`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5, recovery5)), clock, sink)
        clock.advanceTo(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5, deadline=8s
        clock.advanceTo(5_000L) // work5's own first tick fires (deadline-3s)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        sink.fired.clear()

        state = reduceAndFireCues(state, TimerEvent.Skip, clock, sink) // t=5s: skip mid-interval

        // The fresh lead-in's own first tick fires immediately — nothing more of work5's countdown.
        assertEquals(listOf(tickCue(false)), sink.fired)
        check(state.timer is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(1), (state.timer as TimerState.Running).phase)

        // The fresh lead-in runs its own full three-tick countdown (the first already fired above)
        // before recovery5's boundary cue — work5's abandoned countdown contributes nothing further,
        // because the phase this reads a deadline from is the fresh lead-in's, never work5's.
        for (t in listOf(6_000L, 7_000L, 8_000L)) {
            clock.advanceTo(t)
            state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)
        }

        assertEquals(
            listOf(tickCue(false), tickCue(false), tickCue(false), boundaryCue(IntervalKind.RECOVERY, false)),
            sink.fired,
        )
    }

    /**
     * CUE-045, TIMER-045: skip pressed during the lead-in itself abandons it and starts a fresh
     * one before the next interval; repeated presses walk forward one interval at a time.
     */
    @Test
    fun `repeated skip during the lead-in walks forward one interval at a time`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5, recovery5, coolDown6)), clock, sink)
        sink.fired.clear() // drop the first lead-in's own first tick; only the walk matters here

        state = reduceAndFireCues(state, TimerEvent.Skip, clock, sink) // abandons lead-in(0), into lead-in(1)
        check(state.timer is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(1), (state.timer as TimerState.Running).phase)

        state = reduceAndFireCues(state, TimerEvent.Skip, clock, sink) // abandons lead-in(1), into lead-in(2)
        check(state.timer is TimerState.Running)
        assertEquals(TimerPhase.LeadIn(2), (state.timer as TimerState.Running).phase)

        // Each skip fired exactly one tick — its new lead-in's own first — never a boundary cue.
        assertEquals(listOf(tickCue(false), tickCue(false)), sink.fired)

        clock.advanceBy(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // lead-in(2)'s deadline: into cool-down
        assertEquals(boundaryCue(IntervalKind.COOL_DOWN, false), sink.fired.last())
    }

    /**
     * TIMER-043 and CUE-013 together: skipping the last interval ends the workout completed with
     * nothing left to lead into, and the finish cue fires immediately — there is no lead-in to run
     * a countdown in, and completion still gets its cue regardless of how the workout reached it.
     */
    @Test
    fun `skipping the last interval ends the workout and fires the finish cue immediately`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink)
        clock.advanceBy(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5
        sink.fired.clear()

        state = reduceAndFireCues(state, TimerEvent.Skip, clock, sink)

        assertEquals(listOf(finishCue(false)), sink.fired)
        check(state.timer is TimerState.Ended)
        assertEquals(WorkoutOutcome.COMPLETED, (state.timer as TimerState.Ended).outcome)
    }

    /** TIMER-050: stop ends the workout stopped-early and fires no finish cue. */
    @Test
    fun `stop fires no finish cue`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()
        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink)
        clock.advanceBy(3_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink) // into work5
        sink.fired.clear()

        state = reduceAndFireCues(state, TimerEvent.Stop, clock, sink)

        assertEquals(emptyList<Cue>(), sink.fired)
        check(state.timer is TimerState.Ended)
        assertEquals(WorkoutOutcome.STOPPED_EARLY, (state.timer as TimerState.Ended).outcome)
    }

    // ---- §6 Mute (CUE-050–053, 055) -----------------------------------------------------------------

    /** CUE-050: mute silences the tone; the vibration and the colour role are unaffected. */
    @Test
    fun `mute silences only the tone`() {
        val muted = boundaryCue(IntervalKind.WORK, muted = true)
        val unmuted = boundaryCue(IntervalKind.WORK, muted = false)
        assertNull(muted.tone)
        assertEquals(unmuted.vibration, muted.vibration)
        assertEquals(unmuted.colorRole, muted.colorRole)
    }

    /** CUE-051: effective mute is initialised from the default given at start. */
    @Test
    fun `effective mute starts from the default given at start`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()

        val state = reduceAndFireCues(
            CueTimerState(),
            TimerEvent.Start(listOf(work5)),
            clock,
            sink,
            defaultMuted = true,
        )

        assertEquals(listOf(tickCue(true)), sink.fired)
        assertEquals(true, state.muted)
    }

    /** CUE-052: toggling mute flips only the current value; there is no default for it to write back to. */
    @Test
    fun `toggling mute flips the current value and touches nothing else`() {
        val state = CueTimerState(muted = false)
        assertEquals(true, state.toggleMuted().muted)
        assertEquals(false, state.toggleMuted().toggleMuted().muted)
    }

    /**
     * CUE-053: effective mute is discarded when the workout ends. Muting mid-workout does not
     * leak into the next workout's default.
     */
    @Test
    fun `the next workout starts from its own default, not the last workout's effective mute`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()

        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink, defaultMuted = false)
        state = state.toggleMuted() // muted mid-workout
        state = reduceAndFireCues(state, TimerEvent.Stop, clock, sink)
        sink.fired.clear()

        // TIMER-017: an ended workout accepts no event: loading a fresh preset (resetting to idle)
        // is the app's job, not one of the timer's own six events.
        state = state.copy(timer = TimerState.Idle)
        state = reduceAndFireCues(state, TimerEvent.Start(listOf(work5)), clock, sink, defaultMuted = false)

        assertEquals(listOf(tickCue(false)), sink.fired)
    }

    /**
     * CUE-055: a mute change takes effect on the very next cue, including one due moments later —
     * the observable consequence of reading mute fresh at fire time rather than a value captured
     * earlier.
     */
    @Test
    fun `a mute change takes effect on the very next cue`() {
        val clock = CueFakeClock(0L)
        val sink = RecordingCueSink()

        var state = reduceAndFireCues(CueTimerState(), TimerEvent.Start(listOf(work5)), clock, sink, defaultMuted = false)
        assertEquals(listOf(tickCue(false)), sink.fired)

        state = state.toggleMuted() // muted moments before the next tick is due
        clock.advanceBy(1_000L)
        state = reduceAndFireCues(state, TimerEvent.Tick, clock, sink)

        assertEquals(listOf(tickCue(false), tickCue(true)), sink.fired)
    }
}

/** Records every cue fired, in order, in place of a real tone or vibration (ADR 0005). */
private class RecordingCueSink : CueSink {
    val fired = mutableListOf<Cue>()
    override fun fire(cue: Cue) {
        fired += cue
    }
}

/** A [Clock] this test file advances by hand, as [TimerStateTest]'s does. */
private class CueFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }

    /** Sets the clock to an absolute instant, for tests that reason in absolute seconds. */
    fun advanceTo(targetMillis: Long) {
        millis = targetMillis
    }
}
