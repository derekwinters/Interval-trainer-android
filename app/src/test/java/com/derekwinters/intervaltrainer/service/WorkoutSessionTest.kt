package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.CueSink
import com.derekwinters.intervaltrainer.Interval
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.Preset
import com.derekwinters.intervaltrainer.PresetStore
import com.derekwinters.intervaltrainer.TimerState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for `docs/spec/service.md` `SVC-014`: the pure mapping from a [WorkoutCommand] to
 * the workout state it produces, independent of whatever `Intent` or `PendingIntent` carried the
 * command in — `SVC-015`: a start command's preset lookup never runs on the thread that
 * delivered the command (#145) — and `SVC-017`: which commands count as a start that left no
 * workout, for the service to reach the foreground, leave it and stop (#147).
 *
 * Every test calls [WorkoutSession.handle] from inside [runBlocking], so the calling thread is this
 * test's own — the stand-in for `WorkoutService.onStartCommand`'s main thread.
 */
class WorkoutSessionTest {

    private val work60 = Interval(IntervalKind.WORK, durationSeconds = 60)
    private val preset = Preset(id = "p1", name = "Test preset", intervals = listOf(work60))
    private val presetStore = InMemoryPresetStore(listOf(preset))

    @Test
    fun `start resolves the preset id and copies its intervals into the schedule`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("p1"))

        check(state.timer is TimerState.Running)
        assertEquals(listOf(work60), (state.timer as TimerState.Running).schedule.map { it.interval })
    }

    @Test
    fun `start with an id that resolves to no preset changes nothing`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("no-such-preset"))

        assertEquals(TimerState.Idle, state.timer)
    }

    @Test
    fun `pause and resume round-trip through the session`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val paused = session.handle(WorkoutCommand.Pause)
        assertTrue(paused.timer is TimerState.Paused)

        val resumed = session.handle(WorkoutCommand.Resume)
        assertTrue(resumed.timer is TimerState.Running)
    }

    @Test
    fun `skip and stop reach the timer through the same session`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val stopped = session.handle(WorkoutCommand.Stop)

        assertTrue(stopped.timer is TimerState.Ended)
    }

    @Test
    fun `toggle mute flips the session's own mute state and touches nothing else`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))

        val toggled = session.handle(WorkoutCommand.ToggleMute)

        assertEquals(true, toggled.muted)
    }

    @Test
    fun `tick with no workout running changes nothing`() = runBlocking<Unit> {
        val session = WorkoutSession(presetStore, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Tick)

        assertEquals(TimerState.Idle, state.timer)
    }

    // ---- SVC-015: the preset lookup runs off the calling thread (#145) ----------------------

    @Test
    fun `start reads the preset off the calling thread and starts the workout`() = runBlocking<Unit> {
        val store = CallerThreadRefusingPresetStore(listOf(preset))
        val session = WorkoutSession(store, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("p1"))

        assertTrue(store.reads > 0)
        check(state.timer is TimerState.Running)
        assertEquals(listOf(work60), (state.timer as TimerState.Running).schedule.map { it.interval })
    }

    @Test
    fun `start with no such preset still changes nothing when read off the calling thread`() = runBlocking<Unit> {
        val store = CallerThreadRefusingPresetStore(listOf(preset))
        val session = WorkoutSession(store, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("no-such-preset"))

        assertTrue(store.reads > 0)
        assertEquals(TimerState.Idle, state.timer)
    }

    @Test
    fun `start from a preset with no intervals still changes nothing when read off the calling thread`() = runBlocking<Unit> {
        val empty = Preset(id = "empty", name = "Empty", intervals = emptyList())
        val store = CallerThreadRefusingPresetStore(listOf(empty))
        val session = WorkoutSession(store, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Start("empty"))

        assertTrue(store.reads > 0)
        assertEquals(TimerState.Idle, state.timer)
    }

    // ---- SVC-017: a start that left no workout (#147) -----------------------------------------

    private val emptyPreset = Preset(id = "empty", name = "Empty", intervals = emptyList())
    private val storeWithEmpty = InMemoryPresetStore(listOf(preset, emptyPreset))

    @Test
    fun `a start whose preset id names nothing left no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())
        val command = WorkoutCommand.Start("no-such-preset")

        val state = session.handle(command)

        assertTrue(startLeftNoWorkout(command, state))
    }

    @Test
    fun `a start from a preset with no intervals left no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())
        val command = WorkoutCommand.Start("empty")

        val state = session.handle(command)

        assertTrue(startLeftNoWorkout(command, state))
    }

    @Test
    fun `a start that starts a workout did not leave no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())
        val command = WorkoutCommand.Start("p1")

        val state = session.handle(command)

        assertFalse(startLeftNoWorkout(command, state))
    }

    /** The invariant on SVC-017: TIMER-013 refuses this start too, but a workout is still under way. */
    @Test
    fun `a start refused while a workout is running is not a start that left no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))
        val command = WorkoutCommand.Start("empty")

        val state = session.handle(command)

        assertTrue(state.timer is TimerState.Running)
        assertFalse(startLeftNoWorkout(command, state))
    }

    /** The invariant on SVC-017, from paused: home is reachable then (SCREEN-041), so Start is too. */
    @Test
    fun `a start refused while a workout is paused is not a start that left no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())
        session.handle(WorkoutCommand.Start("p1"))
        session.handle(WorkoutCommand.Pause)
        val command = WorkoutCommand.Start("no-such-preset")

        val state = session.handle(command)

        assertTrue(state.timer is TimerState.Paused)
        assertFalse(startLeftNoWorkout(command, state))
    }

    @Test
    fun `a command other than start that leaves the state idle is not a start that left no workout`() = runBlocking<Unit> {
        val session = WorkoutSession(storeWithEmpty, SessionFakeClock(0L), RecordingCueSink())

        val state = session.handle(WorkoutCommand.Tick)

        assertEquals(TimerState.Idle, state.timer)
        assertFalse(startLeftNoWorkout(WorkoutCommand.Tick, state))
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

/**
 * `SVC-015`: a [PresetStore] that refuses to be read on the thread that built it — this test's own
 * thread, standing in for the main thread — the way the on-device Room store refuses main-thread
 * reads (`AppDatabase.open` builds it without `allowMainThreadQueries()`), with the same exception
 * type Room throws. [reads] counts the reads that were allowed, so a test can tell a lookup that
 * happened off the calling thread from one that never happened at all.
 */
private class CallerThreadRefusingPresetStore(initial: List<Preset>) : PresetStore {
    private val refusedThread: Thread = Thread.currentThread()
    private val presets = initial.associateBy { it.id }

    @Volatile
    var reads = 0
        private set

    private fun checkNotRefusedThread() {
        check(Thread.currentThread() !== refusedThread) {
            "Cannot access database on the main thread since it may potentially lock the UI for a long period of time."
        }
        reads++
    }

    override fun presets(): List<Preset> {
        checkNotRefusedThread()
        return presets.values.toList()
    }

    override fun preset(id: String): Preset? {
        checkNotRefusedThread()
        return presets[id]
    }

    override fun save(preset: Preset) {
        throw UnsupportedOperationException("not used by WorkoutSession")
    }

    override fun delete(id: String) {
        throw UnsupportedOperationException("not used by WorkoutSession")
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
