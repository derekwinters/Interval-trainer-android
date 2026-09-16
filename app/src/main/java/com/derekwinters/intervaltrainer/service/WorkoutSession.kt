package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.CueSink
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.PresetStore
import com.derekwinters.intervaltrainer.TimerEvent
import com.derekwinters.intervaltrainer.reduceAndFireCues
import com.derekwinters.intervaltrainer.toggleMuted

/**
 * `docs/spec/service.md` `SVC-014`: applies a [WorkoutCommand] to the workout's current
 * [CueTimerState] and holds the result — the pure mapping the foreground service drives,
 * independent of whatever `Intent` or `PendingIntent` carried the command in.
 *
 * This class has no `android.*` import and needs none to run — [presetStore], [clock] and
 * [cueSink] are `:core`'s own seams (`ADR 0005`) — which is what makes it testable on the JVM the
 * same way `:core`'s own reducer is, even though it lives in `:app` rather than `:core` (it also
 * resolves [WorkoutCommand.Start.presetId] against [presetStore], which `:core`'s pure `reduce`
 * never does).
 */
class WorkoutSession(
    private val presetStore: PresetStore,
    private val clock: Clock,
    private val cueSink: CueSink,
) {
    var state: CueTimerState = CueTimerState()
        private set

    /**
     * Applies [command], returning (and recording as [state]) the [CueTimerState] it produces. A
     * [WorkoutCommand.Start] whose [WorkoutCommand.Start.presetId] resolves to no preset changes
     * nothing — the same "no cell, no change" guard `:core`'s own `reduce` applies to every event
     * `TIMER-012`'s table has no cell for.
     */
    fun handle(command: WorkoutCommand, defaultMuted: Boolean = state.muted): CueTimerState {
        state = when (command) {
            is WorkoutCommand.Start -> {
                val preset = presetStore.preset(command.presetId)
                if (preset == null) {
                    state
                } else {
                    reduceAndFireCues(
                        state,
                        TimerEvent.Start(preset.intervals),
                        clock,
                        cueSink,
                        defaultMuted,
                    )
                }
            }

            WorkoutCommand.Pause -> reduceAndFireCues(state, TimerEvent.Pause, clock, cueSink)
            WorkoutCommand.Resume -> reduceAndFireCues(state, TimerEvent.Resume, clock, cueSink)
            WorkoutCommand.Skip -> reduceAndFireCues(state, TimerEvent.Skip, clock, cueSink)
            WorkoutCommand.Stop -> reduceAndFireCues(state, TimerEvent.Stop, clock, cueSink)
            WorkoutCommand.Tick -> reduceAndFireCues(state, TimerEvent.Tick, clock, cueSink)
            WorkoutCommand.ToggleMute -> state.toggleMuted()
        }
        return state
    }
}
