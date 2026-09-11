# Interval Trainer

The language of the interval-training app: the words this repository uses for presets, workouts and
the cues that mark their boundaries. One word per concept, so an issue, a specification page and a
class name can all say the same thing and mean it.

## Language

**Preset**:
A saved, named workout definition.
_Avoid_: Routine, program, template

**Workout**:
One run of a preset, from start to completion or abandonment.
_Avoid_: Session

**Interval**:
One timed segment of a workout, with a kind and a duration. The kinds in v1 are warm-up, work, rest
and cool-down.
_Avoid_: Phase, step, period

**Round**:
One work interval followed by its rest interval.
_Avoid_: Set, rep, cycle

**Schedule**:
The flat, ordered list of intervals a preset expands into. The timer runs a schedule, never a
preset.

**Cue**:
A signal at an interval boundary or during a countdown: a tone, a vibration, a colour.
_Avoid_: Alert, alarm

**Countdown**:
The final three seconds of an interval, cued once per second.

**Summary**:
The terminal view of a workout, reporting rounds completed, total elapsed time, and whether the
workout completed or was stopped early. It is ephemeral in v1.

**Mute**:
A per-workout override that silences tones while leaving vibration. It takes its initial value from
settings and never writes back to it.
