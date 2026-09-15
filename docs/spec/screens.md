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
  round count, only the total — for example "Steady effort · 25:00" — since a round count of zero
  is not a fact worth stating. *(manual: the row's rendering is a screen fact; the round count and
  total it displays are `:core` arithmetic, covered once a reducer test exists.)*
- **SCREEN-004** Each row carries a thin colour strip previewing the proportion of the schedule
  each interval kind occupies, using the colour tokens `CUE-030` and `DS-050` already fix — work,
  recovery, and the shared neutral for warm-up and cool-down.
- **SCREEN-005** Each row has exactly two controls, both always visible: **Edit** and **Start**.
  Nothing else on the row responds to a tap — not the name, not the meta line, not the colour strip.
- **SCREEN-006** Tapping **Start** starts a workout from that preset immediately (`TIMER-013`) and
  navigates to the running screen.
- **SCREEN-007** Tapping **Edit** opens the preset editor (§2) loaded with that preset.
- **SCREEN-008** A floating action button (`DS-007`) opens the preset editor loaded with a new,
  empty preset.

*(All of §1 is manual: screen structure and navigation, not arithmetic. SCREEN-003's own content —
the round count and the total — is `:core` arithmetic and is covered there, once that reducer
exists; what this page specifies is only how the row renders it.)*

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
- **SCREEN-015** A row's delete control removes it from the list immediately, with no confirmation
  and no destructive styling (`DS-004`): nothing saved is lost until **Save** is pressed.
- **SCREEN-016** `+ Interval` (`SecondaryButton`, `DS-002`) appends one new interval row to the end
  of the list, immediately open for the user to set its kind and duration — an ordinary row from
  the moment it exists (`TIMER-085`'s own phrase, true here as well as of the generator's rows).
- **SCREEN-017** `+ Rounds…` (`SecondaryButton`, `DS-002`) opens the generator (`TIMER-080`–`085`):
  a round-count `CountStepper` (`DS-008`), a work-duration and a recovery-duration scroll picker
  (`DS-009`), and a trailing-recovery toggle defaulting off (`TIMER-082`). Confirming appends the
  generated rows to the end of the list (`TIMER-083`); nothing about the generator's inputs is
  remembered afterward (`TIMER-004`). This dialog reuses the row-and-picker visual treatment of
  `prototypes/screens/preset-editor/EditorScroll.dc.html`; its exact field set is the four inputs
  `TIMER-080` specifies, not that file's own warm-up and cool-down fields — see this pull request's
  Deviations section.
- **SCREEN-018** A footer shows the interval count and the schedule's total duration, formatted as
  `formatSeconds` does — for example "9 intervals · total 15:30". *(manual: the footer's rendering
  is a screen fact; the total it shows is `:core` arithmetic, covered once a reducer test exists.)*
- **SCREEN-019** **Save** persists the name and the ordered interval list (`SCHEMA-010`,
  `SCHEMA-025`) and returns to home.

*(All of §2 is manual: screen structure, controls and navigation. SCREEN-018's own content — the
total it shows — is `:core` arithmetic, covered there, once that reducer exists.)*

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
  remaining time in that interval against its full duration — "0:32 of 0:45".
- **SCREEN-021** A "Total left" readout shows the total remaining time across the whole workout
  (`TIMER-025`). *(manual: the readout's rendering is a screen fact; the value it shows is already
  `:core`-tested via `TIMER-025`.)*
- **SCREEN-022** A round indicator shows the round **currently in progress** against rounds
  planned — "Round 2 of 4" (`TIMER-060`). This is a live position, distinct from **rounds
  completed** (`TIMER-061`), which this screen never shows; rounds completed is reported only on
  the summary (§4). Conflating the two would misreport progress the moment a round is skipped
  (`TIMER-061`).
- **SCREEN-023** The rail lists every interval in the schedule in order, the current one
  emphasised and each other one shrinking and dimming with its distance from it, per the settled
  design. It is a preview, not a control: no interval in the rail is tappable.
- **SCREEN-024** During the lead-in (`TIMER-030`–`036`), the screen shows a distinct "get ready"
  state rather than a partially-formed interval display, per
  `prototypes/screens/running-screen/States.dc.html`.

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

*(All of §3 is manual — screen content, controls and navigation are not reachable from a JVM
runner, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) — except where a
requirement above cites a `:core`-tested value it displays.)*

## 4. Summary

**Layout:** list (`DS-072`). **Vocabulary:** stock Material 3 (`DS-061`).

Never mocked up (`DS-061`); this section is deliberately the whole of what is decided.

- **SCREEN-050** The summary shows exactly three values: rounds completed against rounds planned
  (`TIMER-061`), total elapsed time (`TIMER-054`), and whether the workout completed or was stopped
  early (`TIMER-011`). There is no fourth value. *(manual: the screen's own rendering; the three
  values themselves are already `:core`-tested via `TimerStateTest.kt`.)*
- **SCREEN-051** The summary is the terminal screen for every ended workout, reached the same way
  whether the workout completed or was stopped early (`TIMER-051`).
- **SCREEN-052** One primary action returns to home. There is no other action on this screen in v1.
- **SCREEN-053** The summary is ephemeral (`TIMER-053`): its three values live only as long as the
  screen is shown and are not read from storage, since nothing about a finished workout is written
  down.

*(All of §4 is manual: a summary screen's rendering is not reachable from a JVM runner; the three
values it renders are `:core`-tested already, via `TIMER-011`, `TIMER-054` and `TIMER-061`.)*

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
| Home | SCREEN-001–008 | *(manual)* |
| The preset editor | SCREEN-010–019 | *(manual)* |
| The running screen — content | SCREEN-020–024 | *(manual)* |
| The running screen — controls | SCREEN-030–033 | *(manual)* |
| The running screen — navigation lock | SCREEN-040–046 | *(manual)* |
| Summary | SCREEN-050–053 | *(manual)* |
| Settings | SCREEN-060–063 | *(manual)* |
| First-run | SCREEN-070–073 | *(manual)* |
| Storage for settings and first-run state | SCREEN-080 | *(manual)* |

**47 requirements, 0 `auto` and 47 `manual`.**

**Every requirement on this page is `manual`, and that is by design, not by omission.** No
screen's layout, control set, content or navigation is assertable on a JVM runner without a
simulated Android runtime — that is
[`docs/spec/design-system.md`](design-system.md)'s territory (`DS-091`, `DS-093`), and this page
does not duplicate it. Several requirements here — a preset's round count and total
(`SCREEN-003`, `SCREEN-018`), and the timer values the running screen and the summary read
(`SCREEN-021`–`022`, `SCREEN-050`) — merely *display* arithmetic that `TIMER-025`, `TIMER-060`,
`TIMER-061`, `TIMER-011` and `TIMER-054` already cover in `TimerStateTest.kt`; that coverage counts
against those `TIMER` requirements, not against the `SCREEN` requirement that a screen renders the
value correctly, which is what stays `manual` here. This page adds no new `:core` test files of its
own; it cites the ones `timer.md` and `build.md` already name, and is honest that everything else —
whether a screen's structure actually matches this page, whether a control does what it says,
whether the lock actually holds — is a human reading the
screen against this specification, the same proportion `docs/spec/design-system.md` reports for
the same reason.
