# Specification — Screens (`SCREEN`)

What each of the six v1 screens shows and does: home, the preset editor, the running screen, the
summary, settings, and the first-run explanation.

[`docs/spec/design-system.md`](design-system.md) is the vocabulary this page builds screens from —
the components, the spacing scale, the colour tokens, and the closed set of three layouts. This
page does not redefine any of it; it says which layout each screen uses and what an implementing
agent puts in that layout's slots. Three screens — home, the preset editor, the running screen —
have settled design prototypes under `prototypes/screens/`, and this page transcribes their
structure and behaviour as requirements. The other three — summary, settings, first-run — were
never mocked up (`docs/spec/design-system.md` `DS-061`), and this page says plainly what is decided
about each and no more.

[`docs/spec/cues.md`](cues.md) fixes what each interval kind's colour means; this page uses those
colours without restating them. [`docs/spec/timer.md`](timer.md) fixes what a workout does; this
page fixes what a screen shows about it. [`docs/spec/service.md`](service.md) fixes the notification
and the stop confirmation; this page fixes the running screen's own controls and its navigation
lock.

---

## Invariants

> **Invariant — portrait only.** No screen in v1 supports landscape. The running screen's layout in
> particular is built around a ring and a vertical rail (§3) that has no landscape counterpart
> designed for it.

> **Invariant — the running screen locks navigation while a workout is running, and releases it
> while paused.** Stated in full in §3; carried here because it governs every other screen's
> reachability, not only the running screen's own content.

> **Invariant — a screen shows only what a `:core` reducer computed for it.** Per
> [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md), the values a screen renders —
> a preset's round count and total, the summary's three values, the running screen's remaining time
> — are derived by a pure reducer in `:core`, not assembled ad hoc inside the composable. A value a
> screen shows that is not traceable to a `:core` reducer is a value about to disagree with another
> screen showing the same thing.

> **Invariant — Edit and Start are always two separate, independently tappable controls.** A row's
> Edit and Start actions are never merged into one combined tap target, and no other part of the row
> — the name, the meta line, the colour strip — is ever wired to either action as a shortcut. This is
> what `SCREEN-005`'s "a tap can never start a workout by accident" actually excludes: an
> implementation that reacts to a tap anywhere on the row, or that treats a long-press or a swipe as
> an alternate way to start or edit, would still have "two controls" while reintroducing exactly the
> accidental-start risk the row exists to rule out.

---

## 1. Home

**Layout:** list (`DS-072`). **Vocabulary:** bespoke (`DS-060`).

The settled design is `prototypes/screens/home-screen/HomeA.dc.html`
(`prototypes/screens/home-screen/previews/HomeA.png`): every row carries two always-visible
actions, and nothing else on the row is tappable, so a tap can never start a workout by accident.

- **SCREEN-001** `ScreenHeader` shows the title "Presets" and one trailing action: an icon button
  opening settings (§5).
- **SCREEN-002** The preset list is read in the schema's insertion order (`SCHEMA-012`) — no
  reordering control exists in v1.
- **SCREEN-003** Each row shows the preset's name, and beneath it: the number of rounds it plans
  (`TIMER-060`) and the schedule's total duration, formatted as `formatSeconds` does
  (`BUILD-030`–`033`) — for example "4 rounds · 15:30". A preset with no work intervals shows no
  round count, only the total, by itself — for example just "25:00", not "0 rounds · 25:00" — since
  a round count of zero is not a fact worth stating. (The settled design's own mockup pairs this
  case with the label "Steady effort" ahead of the total; that is the mockup's illustrative preset
  *name*, not a second piece of meta text this requirement adds, and an earlier draft of this
  requirement's own example could be misread as the latter — corrected here.) The round count and
  the total are one computation, `presetSummary(preset)` in `:core`
  (`core/src/main/kotlin/com/derekwinters/intervaltrainer/PresetSummary.kt`): the preset's own
  schedule (`scheduleFrom`), the count of its `WORK` intervals against the sum of every interval's
  duration — so this row, and any other screen that ever needs the same two numbers, read one
  answer rather than two that could drift apart (this page's third invariant). *(manual: the row's
  own rendering — the name, the layout, "N rounds · total" versus a bare total — is a screen fact;
  the two numbers themselves are `:core` arithmetic, covered by
  `core/src/test/kotlin/com/derekwinters/intervaltrainer/PresetSummaryTest.kt`.)*
- **SCREEN-004** Each row carries a thin colour strip previewing the proportion of the schedule
  each interval kind occupies, using the colour tokens `CUE-030` and `DS-050` already fix — work,
  recovery, and the shared neutral for warm-up and cool-down. The strip is one segment per interval,
  in schedule order, each sized by its share of the total duration: `scheduleSegments(preset)` in
  `:core` (`PresetSummary.kt`), which reads its colour role from `CueSelection.kt`'s own
  `IntervalKind.colorRole()` (`CUE-030`) rather than a second copy of that mapping. The row itself
  only translates each `ColorRole` to the design system's actual `Color` (`DS-050`), since `:core`
  does not depend on `:designsystem` and has no way to know that mapping itself. An empty schedule,
  or one whose total duration is zero, yields no segments, and the row draws no strip rather than
  dividing by zero. *(manual: the strip's own rendering is a screen fact; the segments it draws are
  `:core` arithmetic, covered by
  `core/src/test/kotlin/com/derekwinters/intervaltrainer/PresetSummaryTest.kt`.)*
- **SCREEN-005** Each row has exactly two controls, both always visible: **Edit** and **Start**.
  Nothing else on the row responds to a tap — not the name, not the meta line, not the colour strip.
- **SCREEN-006** Tapping **Start** starts a workout from that preset immediately (`TIMER-013`) and
  navigates to the running screen.
- **SCREEN-007** Tapping **Edit** opens the preset editor (§2) loaded with that preset.
- **SCREEN-008** A floating action button (`DS-007`) opens the preset editor loaded with a new,
  empty preset.

`SCREEN-006`, `SCREEN-007` and `SCREEN-008` land ahead of what they name, the same way this
project's build has staged v1 ahead of its own pieces before now
(`docs/spec/build.md` `BUILD-018`): at the time home shipped, the preset editor (§2,
[#80](https://github.com/derekwinters/Interval-trainer-android/issues/80)) and the running screen
(§3, [#81](https://github.com/derekwinters/Interval-trainer-android/issues/81)) were not built yet,
and settings (§5) is not built yet either. Home registered a real navigation route for each of the
three — `SCREEN-006`/`007`/`008` name real destinations, not ones an implementation would have to
guess at later — landing on a placeholder composable for whichever had not shipped. `SCREEN-006`'s
"starts a workout... immediately" is real regardless of the running screen's own state: the
foreground service it starts (`docs/spec/service.md` `SVC-010`–`013`) already exists and does not
wait for a screen to show its progress. Each placeholder is replaced, route unchanged, the moment
its own issue lands — this pull request (§2, `#80`) replaces the preset editor's own placeholder;
the running screen's (`#81`) and settings' remain, for now.

*(All of §1 is manual: screen structure and navigation, not arithmetic. SCREEN-003's and
SCREEN-004's own content — the round count, the total, and the colour-strip segments — is `:core`
arithmetic, covered by `PresetSummaryTest.kt`, exactly as each requirement's own text above says;
what this page specifies is only how the row renders it.)*

## 2. The preset editor

**Layout:** list (`DS-072`). **Vocabulary:** bespoke (`DS-060`).

The settled design is `prototypes/screens/preset-editor/EditorList.dc.html`
(`.../previews/EditorList.png`), with duration entry via
`prototypes/screens/preset-editor/DurScroll.dc.html`. Per that directory's own README, this screen
replaces the fixed five-field template an earlier ticket description assumed: a preset is a freely
authored list, and a real workout's uneven interval durations cannot be expressed as one work
duration times one recovery duration times a round count (`TIMER-001`).

- **SCREEN-010** `ScreenHeader` shows a back action, the title "Edit preset", and one trailing
  action, **Save**.
- **SCREEN-011** A name field holds the preset's name, editable as plain text.
- **SCREEN-012** The schedule is a list of rows, one per interval, in schedule order
  (`SCHEMA-025`). Each row shows a colour dot for the interval's kind, the kind's name, its
  duration, a drag handle to reorder it, and a delete control.
- **SCREEN-013** Dragging a row's handle reorders it within the list; the new order is what is
  saved (`SCHEMA-025`).
- **SCREEN-014** Tapping a row's duration opens it in place for editing, using the scroll-picker
  control `DS-009` names — a flick-scrub drum for minutes and seconds
  (`prototypes/screens/preset-editor/DurScroll.dc.html`) — never steppers, typed digits, or chips.
- **SCREEN-014a** A row's *kind* is set the same way its duration is reached: while the row is open
  for editing (`SCREEN-014`), tapping its colour dot or its kind name cycles it one step through the
  fixed order warm-up → work → recovery → cool-down → back to warm-up
  (`IntervalKind.next()` in `:core`, `core/src/main/kotlin/com/derekwinters/intervaltrainer/Interval.kt`).
  Neither the settled design's `EditorList.dc.html` mockup nor its README depicts a kind control at
  all — every row in that mockup already has a kind — so this is this pull request's own filling of
  a gap `SCREEN-016`'s "immediately open... to set its kind and duration" leaves open, not a value
  recovered from an existing decision. It is numbered `SCREEN-014a`, immediately beside `SCREEN-014`
  rather than appended after `SCREEN-019`, because §2's own `SCREEN-010`–`019` range was already ten
  requirements deep — the same numbering accommodation `docs/spec/design-system.md`'s `DS-013` made
  for `ScreenHeader`'s back action — see this pull request's Deviations section.
- **SCREEN-015** A row's delete control removes it from the list immediately, with no confirmation
  and no destructive styling (`DS-004`): nothing saved is lost until **Save** is pressed.
- **SCREEN-016** `+ Interval` (`SecondaryButton`, `DS-002`) appends one new interval row to the end
  of the list, immediately open for the user to set its kind and duration — an ordinary row from
  the moment it exists (`TIMER-085`'s own phrase, true here as well as of the generator's rows). A
  freshly added row starts as a `WORK` interval at 30 seconds: a reasonable, editable starting point
  rather than a value either mockup or an earlier decision pins down — again see the Deviations
  section.
- **SCREEN-017** `+ Rounds…` (`SecondaryButton`, `DS-002`) opens the generator (`TIMER-080`–`085`):
  a round-count `CountStepper` (`DS-008`), a work-duration and a recovery-duration scroll picker
  (`DS-009`), and a trailing-recovery toggle defaulting off (`TIMER-082`). Confirming appends the
  generated rows to the end of the list (`TIMER-083`); nothing about the generator's inputs is
  remembered afterward (`TIMER-004`). This dialog reuses the row-and-picker visual treatment of
  `prototypes/screens/preset-editor/EditorScroll.dc.html`; its exact field set is the four inputs
  `TIMER-080` specifies, not that file's own warm-up and cool-down fields — see this pull request's
  Deviations section.
- **SCREEN-018** A footer shows the interval count and the schedule's total duration, formatted as
  `formatSeconds` does — for example "9 intervals · total 15:30". The interval count is the list's
  own size; the total is `presetSummary(preset).totalDurationSeconds` (`PresetSummary.kt`), the same
  reducer home's own meta line already reads (`SCREEN-003`), not a second computation of it.
  *(manual: the footer's rendering is a screen fact; the total it shows is `:core` arithmetic,
  covered by `PresetSummaryTest.kt`.)*
- **SCREEN-019** **Save** persists the name and the ordered interval list (`SCHEMA-010`,
  `SCHEMA-025`) and returns to home.

*(All of §2 is manual: screen structure, controls and navigation. SCREEN-018's own content — the
total it shows — is `:core` arithmetic, covered by `PresetSummaryTest.kt`. SCREEN-014a's cycle is
also `:core`, covered by `IntervalKindTest.kt`; and SCREEN-017's own splice onto the list is
`appendGeneratedRounds`, covered by `ScheduleGeneratorTest.kt`. What stays manual, exactly as every
other screen page here, is whether the screen itself actually renders and wires these correctly —
not the arithmetic underneath it.)*

## 3. The running screen

**Layout:** full-bleed (`DS-074`). **Vocabulary:** bespoke (`DS-060`).

The settled design is `prototypes/screens/running-screen/Main.dc.html`
(`.../previews/Main.png`): a progress ring for the current interval, a narrow vertical rail beside
it previewing the rest of the schedule with past and future intervals shrinking and dimming by
distance, and a control row below. Per that directory's README, two choices are deliberate: start,
pause and resume share one toggle control rather than three separate buttons, and what is next is
carried by the rail itself rather than a separate line of text.

### 3.1 Content

- **SCREEN-020** The ring shows the current interval's kind name, its colour (`CUE-030`), and the
  remaining time in that interval against its full duration — "0:32 of 0:45". *(manual: the ring's
  own rendering — colour, arc, digits — is a screen fact; the kind, the remaining time and the full
  duration it draws from are `:core` arithmetic, `RunningScreenContent.Active` below,
  `:core`-tested via `RunningScreenContentTest.kt`.)*
- **SCREEN-021** A "Total left" readout shows the total remaining time across the whole workout
  (`TIMER-025`). *(manual: the readout's rendering is a screen fact; the value it shows is already
  `:core`-tested via `TIMER-025`, and again directly via `RunningScreenContentTest.kt`.)*
- **SCREEN-022** A round indicator shows the round **currently in progress** against rounds
  planned — "Round 2 of 4" (`TIMER-060`). This is a live position, distinct from **rounds
  completed** (`TIMER-061`), which this screen never shows; rounds completed is reported only on
  the summary (§4). Conflating the two would misreport progress the moment a round is skipped
  (`TIMER-061`).
- **SCREEN-022a** `docs/spec/timer.md` §7 explicitly leaves "a better rendering of a non-uniform
  workout" to the running screen's own issue; `SCREEN-022`'s "currently in progress" is this pull
  request's resolution of that gap, not a value any earlier decision pins down. The round in
  progress is the **ordinal, among the schedule's work intervals, of the last one reached at or
  before the timer's current schedule position** — `currentRound(index)` in `:core`
  (`core/src/main/kotlin/com/derekwinters/intervaltrainer/Timer.kt`, beside `roundsCompleted`). A
  work interval counts the moment its own lead-in begins, and stays the round in progress through
  whatever non-work interval follows it, until the next work interval's own lead-in begins — so the
  indicator already reads "Round 2 of 4" during round 2's own get-ready countdown, matching the
  settled design's own `States.dc.html` mockup, which shows the round indicator unchanged across
  get-ready, running, paused and muted. It counts a work interval reached this way whether it was
  finished or skipped (`TIMER-061`'s finished-only rule is for rounds *completed*, a different
  question `SCREEN-022a` does not answer): skip still moves the schedule position forward, and "in
  progress" tracks position, not completion. Zero before the first work interval is reached, e.g.
  during a leading warm-up. *(manual: which round the indicator names is a screen fact; the ordinal
  itself is `:core` arithmetic — `currentRound` in `Timer.kt`, `:core`-tested via
  `RunningScreenContentTest.kt`.)*
- **SCREEN-023** The rail lists every interval in the schedule in order, the current one
  emphasised and each other one shrinking and dimming with its distance from it, per the settled
  design. It is a preview, not a control: no interval in the rail is tappable. *(manual: the rail is
  screen rendering over `TimerState.schedule` and the current schedule position directly — nothing
  here is arithmetic beyond reading an index, so there is no separate `:core` function to test.)*
- **SCREEN-024** During the lead-in (`TIMER-030`–`036`), the screen shows a distinct "get ready"
  state rather than a partially-formed interval display, per
  `prototypes/screens/running-screen/States.dc.html`. *(manual: the get-ready layout's own rendering
  is a screen fact; that the lead-in produces a different shape at all —
  `RunningScreenContent.GetReady`, carrying no full duration to show progress against — is `:core`,
  `:core`-tested via `RunningScreenContentTest.kt`.)*

`SCREEN-020`–`022`, `SCREEN-022a` and `SCREEN-024` are one `:core` function together:
`runningScreenContent(clock)` on `TimerState`
(`core/src/main/kotlin/com/derekwinters/intervaltrainer/RunningScreenContent.kt`), returning
`RunningScreenContent.GetReady` or `.Active` — two shapes, not one shape with optional fields, so a
lead-in cannot be rendered as a partially-formed interval by construction (`SCREEN-024`) — or `null`
for idle or ended, the same split `WorkoutNotificationContent` (`SVC-025`) already uses for the
notification's own content. This is the "pure state-derivation function for its own display" this
issue's own description asked for, so the running screen reads one answer rather than reassembling
`TimerState` into a display ad hoc, per this page's third invariant.

### 3.2 Controls

- **SCREEN-030** A mute icon button sits at the top of the screen and shows its own current state
  (muted or unmuted); tapping it toggles mute (`CUE-050`–`055`). It is the one control `CUE-054`
  specifies — there is no separate sound and vibration toggle here.
- **SCREEN-031** One primary control at the centre of the control row toggles between pause and
  resume (`TIMER-014`), rendered with the icon for the action it is about to take.
- **SCREEN-032** A secondary control skips to the next interval (`TIMER-040`–`045`).
- **SCREEN-033** A secondary control raises the stop confirmation (`SVC-050`).

### 3.3 The navigation lock

Decided in full on [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31),
2026-09-11.

- **SCREEN-040** While a workout is **running**, the running screen is the only reachable screen in
  the app. There is no back affordance and no navigation chrome to anywhere else.
- **SCREEN-041** While a workout is **paused**, the rest of the app — home, the preset editor,
  settings — is reachable exactly as it is with no workout active.
- **SCREEN-042** System back, while a workout is running, raises the stop confirmation
  (`SVC-053`) rather than doing nothing or leaving the screen. It does not fire this while paused.
- **SCREEN-043** Opening the app while a workout exists — from the launcher, from a cold start
  after the process was killed while the service survived, or from any other entry point — lands on
  the running screen. This holds on **every** entry, not only on tapping the notification
  (`SVC-022`).
- **SCREEN-044** The lock ends at the summary (§4), reached from either running or paused state via
  stop (`SVC-051`).
- **SCREEN-045** The lock is enforceable only inside the app. Home, recents, and other apps still
  work while a workout runs, and none of this specification's requirements claim otherwise
  (`SVC-042`).
- **SCREEN-046** The screen does not turn off from inactivity while the running screen is the
  foreground screen — covering the lead-in, running, and paused-while-viewing-it states alike. Once
  the user navigates elsewhere during a pause (`SCREEN-041`), that screen's own normal timeout
  behaviour applies; keeping the screen on is a property of the running screen being shown, not of
  the workout being active.

> **Invariant — system back while running always raises the stop confirmation, never exits or pops
> silently.** `SCREEN-042`'s own wording already says this; stated again here as an invariant
> because it is exactly the shortcut a technically-plausible implementation could take instead: a
> back handler that is conditionally *enabled* only while running (so a disabled handler while
> paused falls through to ordinary back navigation, `SCREEN-041`) is correct; a single always-on
> handler that branches on workout state inside itself, or that lets back close the app or pop the
> running screen off the stack under any condition while running, is not — there is no exit from a
> running workout except through the stop confirmation (`SCREEN-040`).

> **Invariant — the running screen is not reachable via normal back-navigation from elsewhere while
> a workout is running.** Nothing in the app — home, the preset editor, settings, the system
> launcher's own back stack — ever offers a route *into* `running` while running, only *out of* it
> via the stop confirmation. This is what makes `SCREEN-040`'s "only reachable screen" true from
> both directions: not just that nothing else can be reached from it, but that nothing else can be
> used to leave it.

`SCREEN-043`'s own mechanism, since "every entry, not only on tapping the notification" is easy to
satisfy for the one entry point that prompted it and miss the rest: `IntervalTrainerNavHost`
(`MainActivity.kt`) computes its `NavHost`'s start destination once, synchronously, from
[`WorkoutServiceState`](../../app/src/main/java/com/derekwinters/intervaltrainer/service/WorkoutService.kt)'s
own current value — the same app-wide observation channel `docs/spec/service.md`'s own invariant
names, not the notification's `PendingIntent` extra — so a cold start lands on `running` regardless
of what opened the app. The one entry point a start destination computed once at composition cannot
cover — the notification's own `PendingIntent`
(`WorkoutService.EXTRA_OPEN_RUNNING_WORKOUT`) arriving at an already-running instance of
`MainActivity` via `FLAG_ACTIVITY_SINGLE_TOP` while some other screen is already in front — is
handled by `MainActivity.onNewIntent`, which still only ever *navigates to* `running`; it does not
duplicate `WorkoutServiceState`'s own reading of whether a workout exists.

`SCREEN-044`'s summary (§4) is not built yet ([#82](https://github.com/derekwinters/Interval-trainer-android/issues/82)).
This pull request registers a real `summary` navigation route — reached, exactly as `SCREEN-044`
requires, the moment `TimerState` reaches `Ended` from either running or paused, whether that is a
confirmed stop (`SVC-051`) or the workout completing on its own (`TIMER-051`, which goes to the
summary "either way") — landing on the same placeholder-ahead-of-its-screen staging `running` and
`settings` themselves already used until their own issues landed (`docs/spec/build.md` `BUILD-018`,
§1's own paragraph on `SCREEN-006`–`008`). The route is real; the screen behind it is `#82`'s.

*(All of §3 is manual — screen content, controls and navigation are not reachable from a JVM
runner, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) — except where a
requirement above cites a `:core`-tested value it displays.)*

## 4. Summary

**Layout:** list (`DS-072`). **Vocabulary:** stock Material 3 (`DS-061`).

Never mocked up (`DS-061`); this section is deliberately the whole of what is decided.

- **SCREEN-050** The summary shows exactly three values: rounds completed against rounds planned
  (`TIMER-061`), total elapsed time (`TIMER-054`), and whether the workout completed or was stopped
  early (`TIMER-011`). There is no fourth value. *(manual: the screen's own rendering; the three
  values themselves are already `:core`-tested via `TimerStateTest.kt`, and again as
  `summaryContent()`'s own packaged shape below, via `SummaryContentTest.kt`.)*
- **SCREEN-051** The summary is the terminal screen for every ended workout, reached the same way
  whether the workout completed or was stopped early (`TIMER-051`).
- **SCREEN-052** One primary action returns to home. There is no other action on this screen in v1.
- **SCREEN-053** The summary is ephemeral (`TIMER-053`): its three values live only as long as the
  screen is shown and are not read from storage, since nothing about a finished workout is written
  down.

`SCREEN-050`'s three values are one `:core` function together: `summaryContent()` on `TimerState`
(`core/src/main/kotlin/com/derekwinters/intervaltrainer/SummaryContent.kt`), `null` outside
`TimerState.Ended` — the same "a screen shows only what a `:core` reducer computed for it" split
`runningScreenContent` (`SCREEN-020`–`022`) and `WorkoutNotificationContent`
(`docs/spec/service.md` `SVC-025`) already give their own screens, so the summary reads one answer
rather than reassembling `TimerState.Ended`'s fields ad hoc. Its rounds-completed pair is
`List<ScheduleEntry>.roundsCompleted()` (`TIMER-060`–`061`) itself, read directly rather than a
second copy of it — a skipped work interval never counts, however far skip has since moved the
schedule past it. This is a different question from `SCREEN-022a`'s round *in progress*, which
counts a skipped round as still under way until the next one begins: that question no longer
applies once a workout has ended, which is exactly why the running screen and the summary never
show the same number for it.

*(All of §4 is manual: a summary screen's rendering is not reachable from a JVM runner; the three
values it renders are `:core`-tested already, via `TIMER-011`, `TIMER-054` and `TIMER-061`
(`TimerStateTest.kt`), and again as `summaryContent()`'s own packaged shape
(`SummaryContentTest.kt`).)*

## 5. Settings

**Layout:** list (`DS-072`). **Vocabulary:** stock Material 3 (`DS-061`).

Never mocked up (`DS-061`). Its v1 scope is deliberately narrow: this screen exists because mute
needs a default to come from, and nothing else has been decided to belong here
([#30](https://github.com/derekwinters/Interval-trainer-android/issues/30), 2026-09-11:
"a settings screen with one item is a perfectly good v1").

- **SCREEN-060** Settings is reached from home's trailing header action (§1, `SCREEN-001`).
- **SCREEN-061** Settings contains exactly one item in v1: a switch for the **default mute
  state** — the value a new workout's effective mute starts from (`CUE-051`). Toggling it changes
  the default; it never affects a workout already running (`CUE-052`).
- **SCREEN-062** Settings is reachable while a workout is paused, as part of the rest of the app
  being reachable then (`SCREEN-041`); it is not reachable while a workout is running.
- **SCREEN-063** Settings contains a second item: a `ListItem` row for the notification
  permission. Its secondary text shows the permission's current state (granted or denied). A
  `Switch` is not used here — unlike `SCREEN-061`'s default-mute value, this row does not hold a
  value the app owns and can flip on tap; it reflects a system permission the app cannot itself
  grant. When the state is denied, tapping the row opens the app's page in the system settings app
  (`SVC-033`); when it is granted, the row is present but inert, since there is nothing left to do
  from here.

**Not specified.** Nothing beyond the default-mute switch and the notification-permission row is
decided for v1 settings. Candidates raised while deciding cues — a default vibration toggle, the
countdown length — are explicitly not built, and this specification does not anticipate them with
unused structure.

*(All of §5 is manual: two rows and one navigation fact.)*

## 6. First-run

**Layout:** form (`DS-076`). **Vocabulary:** stock Material 3 (`DS-061`).

Never mocked up (`DS-061`); decided on
[#31](https://github.com/derekwinters/Interval-trainer-android/issues/31), 2026-09-11, as new v1
scope the map did not originally carry.

- **SCREEN-070** The app shows this screen exactly once: on the first run after install, before any
  preset is ever started, tracked by a persisted first-run-seen flag.
- **SCREEN-071** The screen explains, in plain language and before the system permission prompt
  appears, what the notification is for — status, pause/resume and skip while the screen is off or
  another app is in front — and that the app is about to ask the system for the notification
  permission. The owner's own framing of this copy, recorded on
  [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31): *"do you want to have
  a notification while a workout is running? we need the notification permission for that. we'll
  ask if you want it."* The exact wording shown is not fixed by this specification, the same way
  [`docs/spec/cues.md`](cues.md) §9 leaves its numeric values tunable rather than pinned.
- **SCREEN-072** A single primary action (`DS-075`'s one primary action) triggers the system
  `POST_NOTIFICATIONS` permission prompt (`SVC-030`) and, once it resolves either way, proceeds to
  home. There is no way to skip past the explanation without triggering the prompt.
- **SCREEN-073** Declining the permission does not block using the app: home is reached exactly the
  same way regardless of the answer (`SVC-031`).

*(All of §6 is manual: an explanatory screen and a permission request are not reachable from a JVM
runner.)*

## 7. Storage for settings and first-run state

- **SCREEN-080** The default mute value (`SCREEN-061`) and the first-run-seen flag (`SCREEN-070`)
  are stored via Jetpack DataStore Preferences, kept separate from the `:database` module's Room
  store (`SCHEMA-001`). Neither value has a query, a relation, or a migration story complex enough
  to need a schema; DataStore's typed key-value store is the ordinary shape for exactly this, and
  keeping it out of Room means it needs no exported schema and no contract-test policy of its own.
  This is this page's own small engineering call, not a decision recovered from an issue thread —
  noted in the pull request's Deviations section.

*(Manual: a storage-mechanism choice, visible in the dependency list.)*

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Home | SCREEN-001–008 | `PresetSummaryTest.kt` (SCREEN-003's round count and total, SCREEN-004's colour-strip segments); *(manual)* SCREEN-001–008 |
| The preset editor | SCREEN-010–019, SCREEN-014a | `PresetSummaryTest.kt` (SCREEN-018's total); `IntervalKindTest.kt` (SCREEN-014a's cycle); `ScheduleGeneratorTest.kt` (SCREEN-017's splice); *(manual)* SCREEN-010–019, SCREEN-014a |
| The running screen — content | SCREEN-020–024, SCREEN-022a | `RunningScreenContentTest.kt` (SCREEN-020's ring content, SCREEN-021's total-left value again, SCREEN-022's/SCREEN-022a's round in progress, SCREEN-024's distinct get-ready shape); *(manual)* SCREEN-020–024, SCREEN-022a |
| The running screen — controls | SCREEN-030–033 | *(manual)* |
| The running screen — navigation lock | SCREEN-040–046 | *(manual)* |
| Summary | SCREEN-050–053 | `SummaryContentTest.kt` (SCREEN-050's three values, again as `summaryContent()`'s packaged shape); *(manual)* SCREEN-050–053 |
| Settings | SCREEN-060–063 | *(manual)* |
| First-run | SCREEN-070–073 | *(manual)* |
| Storage for settings and first-run state | SCREEN-080 | *(manual)* |

**49 requirements, 0 `auto` and 49 `manual`.**

**Every requirement on this page is `manual`, and that is by design, not by omission.** No
screen's layout, control set, content or navigation is assertable on a JVM runner without a
simulated Android runtime — that is
[`docs/spec/design-system.md`](design-system.md)'s territory (`DS-091`, `DS-093`), and this page
does not duplicate it. Several requirements here — a preset's round count and total, and its
colour-strip proportions (`SCREEN-003`, `SCREEN-004`, `SCREEN-018`), a row's kind-cycling
(`SCREEN-014a`), the generator's own splice (`SCREEN-017`), the running screen's own ring, round
indicator and get-ready state (`SCREEN-020`, `SCREEN-022`, `SCREEN-022a`, `SCREEN-024`), and the
timer values the running screen and the summary read (`SCREEN-021`–`022`, `SCREEN-050`) — merely
*display* or *invoke* arithmetic that lives in `:core`; that coverage counts against the `:core`
function computing the value, not against the `SCREEN` requirement that a screen renders or wires it
correctly, which is what stays `manual` here regardless. `SCREEN-021`–`022` and `SCREEN-050` cite
`TIMER-025`, `TIMER-060`, `TIMER-061`, `TIMER-011` and `TIMER-054`, already covered in
`TimerStateTest.kt`; `SCREEN-050` is additionally its own packaged `:core` shape, `summaryContent()`
(`SummaryContent.kt`, this pull request's own new `:core`, following exactly the same "pure
state-derivation function for its own display" split `runningScreenContent` and
`WorkoutNotificationContent` already established), via `SummaryContentTest.kt`; `SCREEN-018` (the
preset editor's own footer) cites `presetSummary`
(`PresetSummary.kt`), the same reducer `SCREEN-003` already reads, via `PresetSummaryTest.kt`;
`SCREEN-014a` cites `IntervalKind.next()` (`Interval.kt`), via `IntervalKindTest.kt`; `SCREEN-017`
cites `appendGeneratedRounds` (`ScheduleGenerator.kt`), via `ScheduleGeneratorTest.kt` — all three
added or extended by the preset editor itself
([#80](https://github.com/derekwinters/Interval-trainer-android/issues/80)). `SCREEN-020`, `022`,
`022a` and `024` cite `runningScreenContent` and `currentRound`
(`RunningScreenContent.kt`, `Timer.kt`), this pull request's own new `:core`, via
`RunningScreenContentTest.kt` — the same "pure state-derivation function for its own display" split
`WorkoutNotificationContent` (`docs/spec/service.md` `SVC-025`) already established for the
notification, applied here to the screen that motivated the pattern in the first place.
`SCREEN-003` and `SCREEN-004` are this page's own first new `:core` test file, added alongside home
itself ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79)):
`PresetSummary.kt` and `PresetSummaryTest.kt`, cited directly in each requirement's own text above
rather than only here. This page is honest that everything else — whether a screen's structure
actually matches this page, whether a control does what it says, whether the lock actually holds —
is a human reading the screen against this specification, the same proportion
`docs/spec/design-system.md` reports for the same reason.
