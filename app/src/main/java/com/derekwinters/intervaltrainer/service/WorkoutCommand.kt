package com.derekwinters.intervaltrainer.service

/**
 * `docs/spec/service.md` `SVC-013`'s commands into the running workout — start (naming the preset
 * to start it from), pause, resume, skip, stop, the mute toggle, and the scheduler's own tick —
 * and nothing else. This is the vocabulary a notification action, a running-screen control, or the
 * service's own tick loop sends; [WorkoutSession] is what turns each one into `:core`'s
 * [com.derekwinters.intervaltrainer.TimerEvent].
 */
sealed interface WorkoutCommand {
    /** Starts a workout from the preset [presetId] names (`TIMER-013`, `SCHEMA-004`). */
    data class Start(val presetId: String) : WorkoutCommand

    data object Pause : WorkoutCommand
    data object Resume : WorkoutCommand
    data object Skip : WorkoutCommand
    data object Stop : WorkoutCommand
    data object ToggleMute : WorkoutCommand

    /** The scheduler's own periodic tick (`SVC-012`) — never sent by a screen or the notification. */
    data object Tick : WorkoutCommand
}
