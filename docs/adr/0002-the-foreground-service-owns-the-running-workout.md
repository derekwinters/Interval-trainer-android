# 2. The foreground service owns the running workout

- **Status:** accepted
- **Date:** 2026-09-11
- **Decided by:** @derekwinters
- **Issue:** [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31)
- **Research:** [`docs/research/foreground-service.md`](../research/foreground-service.md) (issue
  [#25](https://github.com/derekwinters/Interval-trainer-android/issues/25))
- **Specification:** the service's detailed behaviour lands with the v1 specification,
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)

## Context

v1 has to keep a timed interval workout running — and cueing on the boundary — with the screen off
or another app in front. That is the whole point of the app: a work interval can be twenty seconds,
the user is not looking at the phone, and a cue that arrives late is a cue that arrived at the wrong
exercise. So the question is not only *where* the workout state lives, but *what* on Android is
allowed to keep counting while the device is idle.

The primary-source research on
[#25](https://github.com/derekwinters/Interval-trainer-android/issues/25), recorded in
[`docs/research/foreground-service.md`](../research/foreground-service.md), settles the platform
constraints that bound the answer:

- **A foreground service does not itself keep the CPU awake.** What it buys is a process state.
  While the device is idle, AOSP's `PowerManagerService` disables an application's
  `PARTIAL_WAKE_LOCK` unless the UID is allowlisted *or* its process state is at least
  `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` — and a running foreground service qualifies. The
  service is therefore the thing that stops Doze ignoring the wake lock; the wake lock is the thing
  that keeps the clock running.
- **Exact alarms cannot do this job.** `setExactAndAllowWhileIdle()` is documented as quota-limited
  — "not more than about every minute", and "such as 15 minutes" while idle, against an AOSP
  default of 72 dispatches per hour. That is coarser than a twenty-second work interval by a wide
  margin, and no permission changes it.
- **From Android 14 a foreground service must declare a type.** Calling `startForeground()` without
  one throws `MissingForegroundServiceTypeException`, and failing a declared type's *runtime*
  prerequisites raises a `SecurityException` that can remove a running service from the foreground
  process state or crash the app. The type is a real choice with a real failure mode, not a
  manifest formality.

The ownership half of the question has the same shape. A started service outlives the activity that
started it: destroying the activity does not stop it, and swiping the task away does not stop it
either. Whatever owns the workout has to survive every one of those events, because the user
putting the phone in a pocket is the normal case rather than the edge case.

## Decision

**The foreground service owns the running workout — its schedule and its clock. Screens observe
that state and send commands; they never own the workout.** Start, pause, resume, skip, stop and
mute are commands into the service. The running screen is a view of a workout that already exists
elsewhere, not the place the workout lives. This split was taken while charting the map and was not
reopened on [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31).

**The service declares the `mediaPlayback` foreground-service type**, with the manifest permission
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`. It has **no runtime prerequisite** — nothing to ask the user
for — and it describes what the service actually does in the background, which is emit audio.
Android 15's restriction on the type (a `BOOT_COMPLETED` receiver may not start it) does not bite:
a workout is started by the user, from a visible screen. The Android 15 six-hour foreground-service
timeout does not apply to `mediaPlayback` at all.

**The timing mechanism is the foreground service plus a `PARTIAL_WAKE_LOCK` plus an in-process
scheduler on `SystemClock.elapsedRealtime()`.** All three are load-bearing and none substitutes for
another: the service raises the process state, the wake lock keeps the CPU running through Doze,
and the monotonic clock is what interval boundaries are measured against — it counts while the
device sleeps, and no wall-clock change can move it.

Four seams are recorded as invariants. Each is free to honour now and awkward to retrofit, and
together they are what keeps the rest of this decision cheap to revisit.

> **Invariant — the active workout is observable from anywhere in the app.** The observation
> channel is app-scoped, never established by or scoped to the running screen. A screen that wants
> to show workout state subscribes to it; nothing about the running screen's navigation lock may
> make the channel unreachable elsewhere.

> **Invariant — a workout holds its own schedule as replaceable data.** The timer's state is a
> schedule, an index and a deadline. It is never recomputed from the preset, and schedule expansion
> stays a pure function in `:core`, so a modified definition can be expanded and swapped in.

> **Invariant — the cue sink consults mute state at the moment a cue fires.** A value captured when
> the service starts makes the mute control on the running screen inert.

> **Invariant — a workout copies the preset's name and definition when it starts.** The summary
> names the preset, and the workout must not depend on the preset row still existing or still
> saying the same thing.

The mute invariant belongs to the mute control decided on
[#30](https://github.com/derekwinters/Interval-trainer-android/issues/30); it is repeated here
because it constrains the service rather than the screen. The copy invariant is what makes a paused
workout immune to its preset being edited or deleted, with no guard and no error state.

## What this records, and what the specification records

This ADR records the architecture: who owns the workout, which foreground-service type is declared,
what keeps the clock running, and the four invariants. **The service's detailed behaviour is
specification content and lands with the v1 specification on
[#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)** — what the notification
shows and which actions it carries, the stop confirmation, what the summary reports, the
running-screen navigation lock, and how the notification permission is explained on first run. A
reader looking for any of those should go to the specification rather than hunting here; all of it
is decided, and until the specification lands it is recorded on
[#31](https://github.com/derekwinters/Interval-trainer-android/issues/31).

## Alternatives considered

**`AlarmManager` exact alarms as the interval clock.** Rejected on the research: the
allow-while-idle exact alarm is quota-limited far coarser than a twenty-second interval, so the
mechanism cannot deliver the app's core promise however it is configured. It also drags in
permissions the app should not want — `SCHEDULE_EXACT_ALARM` is denied by default from Android 14,
and `USE_EXACT_ALARM` is auto-granted but documented for calendar and alarm-clock apps, which this
is not. `AlarmManager` remains available as a coarse backstop (a workout end, a long rest) if one
is ever wanted; it is not the clock.

**The `health` foreground-service type.** It is the closest *category* match — its documented use
case names exercise trackers. Rejected because it carries a runtime prerequisite: the app must
declare `HIGH_SAMPLING_RATE_SENSORS` or hold one of the body-sensor permissions. A timer that emits
a tone has no reason to hold a sensor permission, and holding one to satisfy a manifest type means
asking the user for something the app does not use. `mediaPlayback` asks for nothing.

**`shortService` and `specialUse`.** `shortService` caps out at about three minutes, which is
shorter than the workouts this app exists to run. `specialUse` would work, but it is the residual
bucket — the type for use cases no other type covers — and it requires a `<property>` justification
in the manifest. Choosing the residual type while a fitting one exists is choosing to explain
yourself forever.

**A screen or a view-model owning the running workout.** Rejected because the workout has to exist
when no screen does. Opening the app while a workout is running lands on the running screen on
every entry, including a cold start after the app itself was killed while the service survived;
task removal does not end the workout; and the active workout must be observable app-wide rather
than from the running screen alone. Every one of those needs state that outlives the UI, and a
view-model scoped to a screen is state that does not.

**Requesting a battery-optimisation exemption.** Not needed, and therefore not asked for. The
foreground service already keeps the partial wake lock alive through Doze and keeps the app in the
active standby bucket; the exemption would only begin to matter if the design came to depend on
unthrottled allow-while-idle alarms, which this one deliberately does not.

## Consequences

- **Removing the task does not stop the workout, and `android:stopWithTask` is not set.** Swiping
  the app out of recents is tidying, not a command to end a session: the service keeps running, the
  notification stays, cues keep firing. This is already the platform default for a started
  foreground service, so what is decided here is not to override it.
- **There is always an exit the app does not control.** The system Task Manager's **Stop** button
  kills the whole app with no callback — no chance to release the wake lock cleanly, stop the
  service tidily, or show a summary. Nothing in this design removes that exit, and the
  specification must never promise that a user cannot end a workout from outside the app.
- **Refusing the notification permission degrades control and visibility, never timing or cues.**
  The service runs without `POST_NOTIFICATIONS` and the workout is unaffected; what is lost is
  status, pause and skip in the shade, and any trace of the running workout while the app is not in
  front. That loss is real, and it is why the permission is explained before it is asked for, but
  it is not a timing loss.
- **App-wide observability keeps two likely futures cheap.** A main screen that shows the running
  workout, and an "edit the current workout" feature, both need the workout observable from outside
  the running screen and its schedule replaceable in flight. Honouring the first two invariants now
  costs nothing; retrofitting them once the running screen owns the channel is a rewrite.
- **The app can hold a wake lock after a gesture that felt like closing it.** A user who swipes the
  task away has a running service and a held wake lock they may not expect. The notification is the
  mitigation — the workout stays visible and one tap away — and it is a weaker mitigation for a
  user who declined the notification permission, for whom the workout continues with no trace in
  the shade.
- **An in-progress workout is not persisted across process death.** The workout lives in the
  service's process and nowhere else; if the process dies the workout is gone and there is no
  resume. This is consistent with the scope ruling that an in-progress workout is not persisted,
  and with the summary being ephemeral — nothing about a workout is written to the database.
- **`:core` does not exist yet.** The build today is a single `:app` module, so the invariant naming
  `:core` as the home of schedule expansion constrains where that function may be written when the
  module is created, rather than describing the build as it stands.
