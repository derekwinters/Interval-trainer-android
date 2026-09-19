# Specification — Service (`SVC`)

What the foreground service does, observably: what its notification shows and which actions it
carries, what happens when the notification permission is refused, what task removal does, what
stop actually does to the service and the workout, and what a running or paused workout is immune
to.

[ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md) decided the
architecture — the service owns the schedule and the clock, screens only observe and send
commands, the `mediaPlayback` foreground-service type, the wake lock, the monotonic clock, and four
invariants. None of that is reopened here. This page is what that ADR deferred: the service's
detailed behaviour, decided in full on
[#31](https://github.com/derekwinters/Interval-trainer-android/issues/31) and landing here as
specification content.

This page does not restate [`docs/spec/timer.md`](timer.md)'s state machine. The events the service
sends into it — start, pause, resume, skip, stop — and what each does to the schedule are `TIMER`'s
requirements; this page is about how those events reach the timer from outside it, and about the
platform-level behaviour around the timer that `timer.md` deliberately does not cover: the process
that holds it, the notification, and the two surfaces (task removal, a device permission prompt)
that only exist because Android does.

---

## Invariants

Carried forward from [ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md),
because this page is where their observable consequences are specified:

> **Invariant — the active workout is observable from anywhere in the app.** The observation
> channel is app-scoped, never established by or scoped to the running screen.

> **Invariant — a workout holds its own schedule as replaceable data**, never recomputed from the
> preset (`TIMER-002`, `TIMER-003`).

> **Invariant — the cue sink consults mute state at the moment a cue fires, never at workout
> start.** A value captured when the service starts makes the running screen's mute control inert
> (`CUE-055`).

> **Invariant — a workout copies the preset's name and definition when it starts.** The summary
> names the preset from this copy, and the workout does not depend on the preset row still existing
> or still saying the same thing.

> **Invariant — the pause/resume toggle is one function of state, called from everywhere it
> appears.** No surface — the notification, the running screen — decides for itself whether
> "toggle" means pause or resume; each calls the same pure function over the workout's current
> state (`SVC-026`), which is what makes `SVC-023` true by construction rather than by two
> implementations agreeing to match.

---

## 1. What produces a workout's schedule, named accurately

`TIMER-002` says starting a workout takes the workout's own copy of the preset's list, and that
copy is the schedule. The function in `:core` that does this **copies** the preset's intervals, in
order, into the workout's schedule. It is not an expansion: a preset is already a flat, ordered
list (`TIMER-001`), so there is nothing to unroll and no template to fill in.

- **SVC-001** The function from a preset to a workout's schedule is a **copy**: it takes the
  preset's ordered interval list and returns it, unchanged in content and order, as the schedule
  the timer runs. It is pure, lives in `:core`, and takes no round count or generator parameters,
  because none is stored (`TIMER-004`).
- **SVC-002** This copy is a different function from the **generator** (`TIMER-080`–`085`), which
  expands a round count into rows **at authoring time**, inside the preset editor, before a preset
  is ever saved. The copy in `SVC-001` runs once, later, **at workout start**, over whatever rows
  the preset holds by then — generated, hand-authored, or both, indistinguishably (`TIMER-085`).

This is the resolution promised on this ticket's own first comment. ADR 0002's wording — "schedule
expansion stays a pure function in `:core`" — was written when the generator's authoring-time
expansion was the only thing in view; it is not wrong so much as it now names two different
moments with one word. The seam ADR 0002 was protecting is real and is `SVC-001`: a pure function
a modified preset definition can be run through again. Naming it a copy here, rather than amending
the ADR, is this specification's call — see the pull request's Deviations section.

## 2. The foreground service

- **SVC-010** The service declares the `mediaPlayback` foreground-service type, with the manifest
  permission `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, per
  [ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md). *(manual: a manifest
  fact.)*
- **SVC-011** The service acquires a `PARTIAL_WAKE_LOCK` when a workout starts and holds it for as
  long as a workout is running or paused, releasing it only when the workout ends via a confirmed
  stop (`SVC-051`) or the service process is killed from outside the app's control (`SVC-042`).
  *(manual: verified on a device with the screen off through Doze.)*
- **SVC-012** The service's scheduler runs on `SystemClock.elapsedRealtime()`, the same monotonic
  clock `TIMER-020`–`025` specify the timer's deadlines against. *(manual: a platform-clock choice;
  the arithmetic it feeds is `TIMER`'s and is `:core`-tested there.)*
- **SVC-013** Commands into the running workout — start, pause, resume, skip, stop, and the mute
  toggle — reach the service from any screen that sends them, and the service's own state is what
  every screen observing the workout reads, per the app-wide observability invariant above.
  *(manual: an architecture fact; no JVM test reaches across the service boundary.)*
- **SVC-014** The mapping from a command to the event it applies is a pure function of the
  workout's current state and the command alone, independent of whatever transport — an `Intent`
  action, a notification's `PendingIntent` — carried the command in. It also resolves a start
  command's preset id against the preset store (`SCHEMA-004`), which `:core`'s own pure `reduce`
  never does, which is why it lives in `:app` rather than `:core`; nothing about that placement
  excuses it from a test of its own. *(auto: exercised by `WorkoutSessionTest.kt`, on the JVM, with
  no `android.*` import anywhere in the class under test.)*

## 3. The notification

- **SVC-020** The notification's status content is the current interval's name and its remaining
  time.
- **SVC-021** The notification carries exactly two actions: **pause/resume** (one toggling control)
  and **skip**. It carries **no stop action** — ending a workout requires opening the app
  ([#31](https://github.com/derekwinters/Interval-trainer-android/issues/31), decided 2026-09-11).
- **SVC-022** Tapping the notification opens the running screen, whether the app process is already
  running or was killed while the service survived (`SVC-040`).
- **SVC-023** Pause/resume and skip on the notification send exactly the same commands the running
  screen's own controls send (`TIMER-014`, `TIMER-040`–`045`); the notification is a second surface
  for the same events, not a second mechanism.
- **SVC-024** There is no confirmation step on the notification's skip action. A skip from the
  notification costs one interval and cannot be undone (`TIMER-040`); this is accepted, since the
  same is true of skip from the running screen, and a workout is a lower-stakes thing to lose one
  interval of than to lose entirely (`SVC-021`'s reasoning for excluding stop).
- **SVC-025** The notification's content (`SVC-020`) is derived by a pure function of the
  workout's [`TimerState`](../../core/src/main/kotlin/com/derekwinters/intervaltrainer/TimerState.kt)
  and the clock alone, living in `:core` next to its other reducers (`ADR 0005`) rather than
  assembled ad hoc from the Android notification APIs. During the lead-in, "current interval" is
  the interval the lead-in counts down into, and the remaining time is the lead-in's own countdown,
  not that interval's full duration — the same phase and the same `remainingMillis` a screen
  showing the lead-in would read (`TIMER-030`–`036`). There is no content, and no notification, for
  idle or ended. *(auto: exercised by `WorkoutNotificationContentTest.kt`.)*
- **SVC-026** The one event the notification's pause/resume action sends is decided by the pure
  function the invariant above names — `Pause` from running, `Resume` from paused — never by which
  label the control happens to be showing. *(auto: exercised by `WorkoutNotificationContentTest.kt`.)*

*(`SVC-020`–`024` are manual: a notification's content and its actions are not reachable from a JVM
runner, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). `SVC-025`–`026`
factor the two pieces of that content that *are* pure functions of state out into `:core`, the same
split every other screen-facing reducer already gets — see this pull request's Deviations section
for why this widens the traceability table's `auto` count from the v1 specification's original
zero.)*

## 4. The notification permission

- **SVC-030** On the app's **first run** — not at first workout start — the app shows the
  first-run screen ([`docs/spec/screens.md`](screens.md) §6), explaining in plain language what the
  notification is for (status, pause/resume and skip while the screen is off or another app is in
  front) and that the system will now ask for the permission. Only after that explanation does the
  system permission prompt for `POST_NOTIFICATIONS` appear.
- **SVC-031** Whatever the answer, the workout runs unaffected: cues fire on schedule and timing is
  untouched. What is lost by declining is the notification's status, pause/resume and skip, and any
  trace of the running workout in the shade or on the lock screen while the app is not in front.
- **SVC-032** The app requests the permission **once**, at first run, and does not prompt again
  automatically at any later point in v1 — not at workout start, and not from settings.
- **SVC-033** v1 does provide a way back in, and it lives in settings
  ([`docs/spec/screens.md`](screens.md) `SCREEN-063`), not as a second automatic prompt.
  Android gives an app no way to re-trigger its own system permission dialog once the user has
  denied it (on modern Android, after a second denial the system stops showing it at all, the
  same end state as a first denial with "don't ask again"), so the settings row does not call the
  in-app permission-request API a second time. Instead it reads the permission's current state —
  granted or denied — and, when it is denied, tapping the row opens the app's page in the system
  settings app (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, or the notification-specific
  settings intent where the platform version offers one), where the user grants it through the
  system's own UI. This resolves what [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31)
  left open; decided by the repository owner, asked directly, since settings is already in v1's
  scope.

  Built (`#83`) as the notification-specific intent, always: `Settings.ACTION_APP_NOTIFICATION_SETTINGS`
  with its `Settings.EXTRA_APP_PACKAGE` extra are both API 26+, the same floor this app's own
  `minSdk` already sits at (`docs/spec/build.md` `BUILD-010`), so there is no older-platform case
  for `ACTION_APPLICATION_DETAILS_SETTINGS` to cover and no version branch to write. The one real
  decision here — which action and which extra key open the *notification* settings page rather
  than the app's general details page — is `notificationSettingsDeepLink`
  (`app/src/main/java/.../settings/NotificationSettingsDeepLink.kt`), a plain function returning
  those two string values (not the `android.provider.Settings` constants themselves, which would
  need the Android SDK on the classpath to even reference) so a JVM test
  (`NotificationSettingsDeepLinkTest.kt`) can assert them directly; the one real
  `android.content.Intent` built from them is assembled at the one call site that needs it
  (`MainActivity.kt`), which is not JVM-testable, per ADR 0005. Reading the permission's own current
  state reuses `WorkoutService.postNotification`'s own
  `NotificationManagerCompat.from(context).areNotificationsEnabled()` check
  (`Context.isNotificationPermissionGranted()`, `MainActivity.kt`) rather than a second mechanism
  for the same fact, re-read on every resume of the settings screen so the row reflects a grant
  made from the system settings page this same row just opened.

## 5. Battery optimisation

- **SVC-040** The app does not request a battery-optimisation exemption. The foreground service
  already keeps the wake lock alive through Doze and keeps the app in the active standby bucket;
  nothing in this design depends on unthrottled allow-while-idle alarms, so the exemption is not
  needed and is not asked for ([ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md)).
  *(manual: absence of a request; visible in the diff.)*

## 6. Task removal

- **SVC-041** Removing the app's task from the recents list does not stop the workout.
  `android:stopWithTask` is not set on the service, which is the platform default for a started
  foreground service and is not overridden here. The service keeps running, the notification stays,
  and cues keep firing. *(manual: a manifest fact plus a device check.)*
- **SVC-042** There remains an exit the app does not control: the system Task Manager's own **Stop**
  button kills the whole app process with no callback — no chance to release the wake lock, stop the
  service tidily, or show a summary. Nothing in this specification promises otherwise.
  *(manual: platform behaviour, outside the app's control by construction.)*

## 7. Stop

- **SVC-050** Pressing **stop** on the running screen raises a confirmation dialog
  ([`docs/spec/design-system.md`](design-system.md) `DS-010`, `DS-011`), rendered as a destructive
  action (`DS-003`). Stop is reachable only from inside the app (`SVC-021`), so this confirmation is
  the one guard against a mis-tap, on the one screen used while moving.
- **SVC-051** Confirming ends the workout with outcome *stopped early* (`TIMER-016`), stops the
  foreground service, releases the wake lock (`SVC-011`), and navigates to the summary
  (`TIMER-051`).
- **SVC-052** Cancelling the confirmation dialog changes nothing: the workout continues exactly as
  it was, running or paused, with no event sent to the timer.
- **SVC-053** System back, while the running screen is showing a workout that is running, raises the
  same stop confirmation as the stop button — it is the only exit the locked running screen offers
  ([`docs/spec/screens.md`](screens.md) §3). It does **not** fire on a back press while a workout is
  paused, since the rest of the app is reachable then and back behaves normally.

Confirming (`SVC-051`) sends `WorkoutCommand.Stop` the same way every other command reaches the
service (`SVC-013`, `SVC-014`) — the running screen's own `onConfirm` callback has no separate "stop
the service" step of its own. `WorkoutService.onStateChanged` already tears down the foreground
state and releases the wake lock the moment the command's own `TimerState.Ended` result comes back,
exactly as it does for the workout completing on its own. The running screen does not navigate to
the summary itself on confirm, either: it observes `TimerState.Ended` the same way regardless of
cause, so a confirmed stop and a natural finish reach the summary through the one code path
(`docs/spec/screens.md` §3.3's own note on `SCREEN-044`), not two call sites that have to agree.

*(All of §7 is manual: a dialog's presence and a service's shutdown are not reachable from a JVM
runner.)*

## 8. Preset edited or deleted mid-workout

- **SVC-060** A workout that is running or paused is **unaffected** by its originating preset being
  edited or deleted while it runs. It resumes, or keeps running, on the schedule copy it took at
  start (`SVC-001`, `TIMER-003`) — editing the preset changes only the **next** workout started from
  it, and deleting the preset does not touch a workout already under way, which runs to its summary
  as normal.
- **SVC-061** Neither the preset editor nor the preset list guards against editing or deleting a
  preset with an active workout. There is no error state and no confirmation specific to this case:
  `SVC-060` is what makes the guard unnecessary, not an oversight.
- **SVC-062** This needs no reconciliation logic and none is built: applying an edited definition to
  an in-flight workout — matching elapsed progress against a changed schedule — is explicitly out of
  scope for v1 ([#31](https://github.com/derekwinters/Interval-trainer-android/issues/31)). A future
  "edit the current workout" feature would pay that cost deliberately; nothing here anticipates it.

*(All of §8 is manual: an absence of a guard and a consequence of state already covered by
`TIMER-002`–`003`'s own tests.)*

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| What produces a workout's schedule | SVC-001–002 | Covered by `ScheduleTest.kt` via `TIMER-001`–`003`; the naming itself is *(manual)* |
| The foreground service | SVC-010–013 | *(manual)* |
| The foreground service — command handling | SVC-014 | `WorkoutSessionTest.kt` |
| The notification | SVC-020–024 | *(manual)* |
| The notification — content and the pause/resume toggle, as pure functions | SVC-025–026 | `WorkoutNotificationContentTest.kt` |
| The notification permission | SVC-030–033 | `NotificationSettingsDeepLinkTest.kt` (SVC-033's deep-link action/extra); *(manual)* SVC-030–033 |
| Battery optimisation | SVC-040 | *(manual)* |
| Task removal | SVC-041–042 | *(manual)* |
| Stop | SVC-050–053 | *(manual)* |
| Preset edited or deleted mid-workout | SVC-060–062 | *(manual)*, consequence of `TIMER-002`–`003` |

**28 requirements, 3 `auto` and 25 `manual`.**

**Why almost every requirement here is `manual`.** This page is almost entirely the far side of
the boundary [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) drew: a foreground
service's lifecycle, a system notification's content and actions, Doze and the wake lock, a task
being removed from recents, a confirmation dialog appearing — none of it is reachable from a JVM
runner with no emulator and no connected device, and this page does not pretend otherwise. The one
piece of arithmetic this page touches, the schedule copy in §1, already has its test: `TIMER-001`–
`003` are asserted by `ScheduleTest.kt`
([#67](https://github.com/derekwinters/Interval-trainer-android/issues/67)), and `SVC-001`–`002`
add nothing to test beyond naming that function correctly, which is what this page exists to do.

Building the service itself (issue #77) added three more: `SVC-014` (which command produces which
event) and `SVC-025`–`026` (the notification's content, and its pause/resume toggle) are every one
of them a pure function of state that has no reason to touch `android.*` to compute, so each is
pulled out and tested the same way `:core`'s own reducers are — the platform-only remainder is the
service that calls them, the wake lock, and the actual `NotificationCompat` calls, which stay
`manual` for the same reason the rest of this page is.
