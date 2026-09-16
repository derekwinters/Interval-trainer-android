package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.CueSink
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.PresetStore
import com.derekwinters.intervaltrainer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/service.md` `SVC-014`: the pure mapping from a [WorkoutCommand] to
 * the workout state it produces, independent of whatever `Intent` or `PendingIntent` carried the
 * command in.
 */
class WorkoutSessionTest {

    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val preset = Preset(id = "p1", name = "Test preset", intervals = listOf(work60))
    private val presetStore = InMemoryPresetStore(listOf(preset))

    @Test
    fun `start resolves the preset id and copies its intervals into the schedule`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("p1"))

        check(state.timer is TimerState.Running)
        assertEquals(listOf(work60), (state.timer as TimerState.Running).schedule.map { it.interval })
    }

    @Test
    fun `start with an id that resolves to no preset changes nothing`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("no-such-preset"))

        assertEquals(TimerState.Idle, state.timer)
    }

    @Test
    fun `pause and resume round-trip through the session`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val paused = session.handle(WorkoutCommand.Pause)
        assertTrue(paused.timer is TimerState.Paused)

        val resumed = session.handle(WorkoutCommand.Resume)
        assertTrue(resumed.timer is TimerState.Running)
    }

    @Test
    fun `skip and stop reach the timer through the same session`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val stopped = session.handle(WorkoutCommand.Stop)

        assertTrue(stopped.timer is TimerState.Ended)
    }

    @Test
    fun `toggle mute flips the session's own mute state and touches nothing else`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val toggled = session.handle(WorkoutCommand.ToggleMute)

        assertEquals(true, toggled.muted)
    }

    @Test
    fun `tick with no workout running changes nothing`() {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Tick)

        assertEquals(TimerState.Idle, state.timer)
    }
}

private class InMemoryPresetStore(initial: List<Preset>) : PresetStore {
    private val presets = initial.associateBy { it.id }.toMutableMap()
    override fun presets(): List<Preset> = presets.values.toList()
    override fun preset(id: String): Preset? = presets[id]
    override fun save(preset: Preset) {
        presets[preset.id] = preset
    }
    override fun delete(id: String) {
        presets.remove(id)
    }
}

private class RecordingCueSink : CueSink {
    val fired = mutableListOf<Cue>()
    override fun fire(cue: Cue) {
        fired += cue
    }
}

private class SessionFakeClock(startMillis: Long) : Clock {
    private var millis = startMillis
    override fun nowMillis(): Long = millis
    fun advanceBy(deltaMillis: Long) {
        millis += deltaMillis
    }
}
