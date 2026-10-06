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

> **Invariant — no landscape layout.** No screen in v1 has a layout designed for landscape. The
> running screen enforces this: it is locked to portrait while it is shown (`SCREEN-027`), because
> its stacked strip, lists and ring (§3) have no landscape counterpart. Every other screen keeps the
> platform's default orientation behaviour; nothing locks it.

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
> — the name, the meta line, the colour strip, the drag handle — is ever wired to either action as a
> shortcut. This is what `SCREEN-005`'s "a tap can never start a workout by accident" actually
> excludes: an implementation that reacts to a tap anywhere on the row, or that treats a long-press
> or a swipe as an alternate way to start or edit, would still have "two controls" while
> reintroducing exactly the accidental-start risk the row exists to rule out. Edit and Start are the
> only controls on the row that act on the preset. The drag handle (`SCREEN-009`) only moves the row:
> it has no tap action at all, and neither it nor any gesture on it starts or edits anything.

---

## 1. Home

**Layout:** list (`DS-072`). **Vocabulary:** bespoke (`DS-060`).

The settled design is `prototypes/screens/home-screen/HomeA.dc.html`
(`prototypes/screens/home-screen/previews/HomeA.png`): every row carries two always-visible
actions, and nothing else on the row is tappable, so a tap can never start a workout by accident.

- **SCREEN-001** `ScreenHeader` shows the title "Presets" and one trailing action: an icon button
  opening settings (§5).
- **SCREEN-002** The preset list is shown in the schema's stored order (`SCHEMA-012`), which the
  drag handle on each row changes (`SCREEN-009`).
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
- **SCREEN-005** Each row has exactly two controls that act on the preset, both always visible:
  **Edit** and **Start**. Nothing else on the row responds to a tap — not the name, not the meta
  line, not the colour strip, and not the drag handle (`SCREEN-009`), which responds only to a drag.
- **SCREEN-006** Tapping **Start** starts a workout from that preset immediately (`TIMER-013`) and
  navigates to the running screen.
- **SCREEN-007** Tapping **Edit** opens the preset editor (§2) loaded with that preset.
- **SCREEN-008** A floating action button (`DS-007`) opens the preset editor loaded with a new,
  empty preset.
- **SCREEN-009** Each row carries a drag handle at its leading edge, the same `⠿` grip the editor's
  interval rows use (`SCREEN-012`, `SCREEN-013`), and it works the same way: dragging it moves the
  row up or down the list, a step at a time, and the drag starts off the handle itself, never off a
  long-press on the row. When the drag ends, the list's new order is persisted (`SCHEMA-015`) and is
  the order home shows from then on, across relaunches. The handle only reorders
  ([#163](https://github.com/derekwinters/Interval-trainer-android/issues/163)).

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
  The opened row labels the picker with the text **"Duration"**, directly above it, 13sp bold in
  `colors.fg` — the same style as the round generator's "Rounds" label (`SCREEN-017`) — so the
  drum says what it sets
  ([#134](https://github.com/derekwinters/Interval-trainer-android/issues/134)).
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
  The two duration pickers tell themselves apart the way the schedule rows do (`SCREEN-012`): each
  is labelled, directly above it, with its kind's colour dot followed by its kind's name — an 8dp
  circle in `colors.work` before **"Work"** and in `colors.recovery` before **"Recovery"**
  (`CUE-030`), `spacing.sm` between dot and name, the name 13sp bold in `colors.fg`, the same style
  as the dialog's "Rounds" and "Trailing recovery" labels. The name stays visible beside the dot in
  every state; the colour reinforces it and never carries the meaning alone (`CUE-032`)
  ([#136](https://github.com/derekwinters/Interval-trainer-android/issues/136)).
  *Invariant:* draw these dots and names with the schedule rows' own kind label — one kind-to-colour
  mapping through `colorRole()` shared by both — never a colour or name written into the dialog.
- **SCREEN-018** A footer shows the interval count and the schedule's total duration, formatted as
  `formatSeconds` does — for example "9 intervals · total 15:30". The interval count is the list's
  own size; the total is `presetSummary(preset).totalDurationSeconds` (`PresetSummary.kt`), the same
  reducer home's own meta line already reads (`SCREEN-003`), not a second computation of it.
  *(manual: the footer's rendering is a screen fact; the total it shows is `:core` arithmetic,
  covered by `PresetSummaryTest.kt`.)*
- **SCREEN-019** **Save** persists the name and the ordered interval list (`SCHEMA-010`,
  `SCHEMA-025`) and returns to home. A preset with no intervals cannot be saved: Save is disabled
  (`docs/spec/design-system.md` `DS-020`) while the schedule holds no intervals, with no message,
  because `TIMER-013` refuses to start a workout from one. Whether Save is enabled is `:core`'s
  `isSavable` (`Preset.kt`), applied to the preset as currently edited. A preset saved with no
  intervals before this rule still appears on home with Start (`SCREEN-005`); starting it is
  `docs/spec/service.md` `SVC-017`'s case
  ([#147](https://github.com/derekwinters/Interval-trainer-android/issues/147)).

- **SCREEN-019a** A **Duplicate** button (`SecondaryButton`, `DS-002`) sits at the bottom of the
  editor, below the interval list and its footer (`SCREEN-018`). It is shown only for a preset that
  is already saved — opened from home's Edit (`SCREEN-007`) — and never for a new one opened from the
  floating action button (`SCREEN-008`). It shares that bottom area with the preset editor's Delete
  button when that lands
  ([#164](https://github.com/derekwinters/Interval-trainer-android/issues/164)), side by side; the
  header keeps exactly one trailing action, Save (`SCREEN-010`). Like Save, it is disabled while the
  schedule on screen holds no intervals (`SCREEN-019`), because what it does is save a preset, and a
  preset with no intervals cannot be saved.
- **SCREEN-019b** Tapping **Duplicate** saves a copy of the preset *as currently edited* — its name
  and intervals including any changes not yet saved — and then opens that copy in the editor, in
  place of the original. The original stays exactly as it was last saved: Duplicate never saves it,
  and its unsaved edits go into the copy, not into it. The copy:
  - is a new preset with its own id (`SCHEMA-011`) and its own interval rows (`SCHEMA-017`);
  - is named `<name> (copy)`, or, if a saved preset already has that name, the first of
    `<name> (copy 2)`, `<name> (copy 3)`, … that none has, where `<name>` is the name as currently
    edited — `copyName()` in `:core` (`Preset.kt`);
  - is placed directly after the original in home's list (`SCHEMA-017`).

  ([#163](https://github.com/derekwinters/Interval-trainer-android/issues/163).) *(manual: the
  button, its availability and the navigation are screen facts; the name is `:core`'s `copyName()`,
  covered by `PresetTest.kt`, and the placement and deep copy are `PresetDaoTest.kt`'s and
  `RoomPresetStoreTest.kt`'s.)*

> **Invariant — Save's enabled state follows the schedule on screen, never the preset the editor
> opened with.** It is recomputed on every edit, so deleting the last row disables Save and adding
> a row enables it; a check made once when the editor opens would let an emptied preset be saved.

> **Invariant — Duplicate copies what is on screen and never writes the original.** It reads the
> name and intervals from the editor's current state, not from the stored preset, and its only
> write is the new row. A Duplicate that saved the original first, or copied the stored version,
> would each look correct until someone duplicated a preset they were in the middle of changing.

*(All of §2 is manual: screen structure, controls and navigation. SCREEN-018's own content — the
total it shows — is `:core` arithmetic, covered by `PresetSummaryTest.kt`. SCREEN-019's rule for
when Save is enabled is `:core`'s `isSavable`, covered by `PresetTest.kt`. SCREEN-014a's cycle is
also `:core`, covered by `IntervalKindTest.kt`; and SCREEN-017's own splice onto the list is
`appendGeneratedRounds`, covered by `ScheduleGeneratorTest.kt`. What stays manual, exactly as every
other screen page here, is whether the screen itself actually renders and wires these correctly —
not the arithmetic underneath it.)*

## 3. The running screen

**Layout:** full-bleed (`DS-074`). **Vocabulary:** bespoke (`DS-060`).

The layout is the stacked one chosen on
[#151](https://github.com/derekwinters/Interval-trainer-android/issues/151), which replaced the
design of `prototypes/screens/running-screen/Main.dc.html`, whose schedule column beside the ring
ran underneath it on a 360dp phone. From top to bottom: a mute button and the "Total left"
readout, a thin timeline strip with one segment per interval, the two intervals before the current
one, a progress ring for the current interval, the four intervals after it, and the control row.
Two choices from the original prototype's README still hold: start, pause and resume share one
toggle control rather than three separate buttons, and what is next is carried by the schedule
itself rather than a separate line of text.

Every measurement below is fixed by #151, drawn at a 360 × 760dp portrait reference screen and
measured from the edges of `FullBleedLayout`. Spacing tokens are `AppTheme.spacing` (`DS-040`).

> **Invariant — exactly one timeline segment is at full alpha at any time the running screen is
> shown.** It is the current interval's segment (`SCREEN-025`), or during a lead-in the segment of
> the interval the lead-in counts into (`SCREEN-024`). No finished interval is drawn bright, and no
> state of the running screen — lead-in, running, paused — shows zero or two bright segments.

> **Invariant — the ring does not move while a workout runs.** The past and upcoming lists reserve
> their minimum heights (`SCREEN-028`) whether or not they have rows to fill them, so the ring sits
> in the same place on the first interval, the last interval and every one between. A layout that
> centres the ring by measuring how many rows happen to be shown is the technically plausible way to
> get this wrong.

### 3.1 Content

- **SCREEN-020** The ring shows the current interval's kind name, its colour (`CUE-030`), and the
  remaining time in that interval against its full duration — "0:32 of 0:45". The ring is a
  **176dp** canvas with an **11dp** stroke and round caps: a full-circle track in `line` and an arc
  in the kind's colour that depletes as the interval runs out. The countdown digits are
  `timer.large` (`DS-030`) at **51sp**. Above them sit the kind label, an 8dp dot and the 13sp bold
  kind name, and below them the "of 0:45" line in 13sp `dim`. *(manual: the ring's own rendering —
  colour, arc, digits — is a screen fact; the kind, the remaining time and the full duration it
  draws from are `:core` arithmetic, `RunningScreenContent.Active` below, `:core`-tested via
  `RunningScreenContentTest.kt`.)*
- **SCREEN-021** A "Total left" readout shows the total remaining time across the whole workout
  (`TIMER-025`): a 13sp `dim` label and a bold `timer.stat` (`DS-031`) value. It sits at the **top
  centre** of the screen, its centre line **35dp** from the top. #151 derives that as the mute
  button's (`SCREEN-030`) centre, 16dp padding plus half its 38dp drawn size; the button's 48dp
  minimum touch target (`DS-005`) actually places its drawn centre at 40dp, so the two sit 5dp
  apart. *(manual: the readout's rendering is a screen fact; the value it
  shows is already `:core`-tested via `TIMER-025`, and again directly via
  `RunningScreenContentTest.kt`.)*
- **SCREEN-023** Two lists around the ring preview the schedule. The **past list**, above the ring,
  shows the **two** intervals before the current one, oldest at the top. The **upcoming list**,
  below the ring, shows the **four** intervals after the current one, next at the top. The current
  interval is not repeated in either list; the ring shows it. Each row's distance is the absolute
  difference between its schedule position and the current one, and it is drawn at alpha
  `1 − 0.18 × distance` clamped to 0.3–1.0 and scale `1 − 0.06 × distance` clamped to 0.75–1.0,
  scaled around the row's centre. A row is a 3 × 14dp bar in the kind's colour, the kind name in
  11sp `dim` and the interval's duration in 11sp `dim`, with 6dp horizontal and 4dp vertical
  padding; it fills its list's width, name on the left and duration at the right edge. Rows are
  `spacing.sm` (8dp) apart. Where fewer than two intervals precede the current one, or fewer than
  four follow it, a list just shows fewer rows: there is no placeholder text and no "Last interval"
  line. It is a preview, not a control: no row is tappable. *(manual: the lists' rendering is a
  screen fact; which intervals each list holds, their distances, alphas and scales are `:core`
  arithmetic — `runningScreenSchedule()` below, `:core`-tested via
  `RunningScreenScheduleTest.kt`.)*
- **SCREEN-024** During the lead-in (`TIMER-030`–`036`), the screen shows a distinct "get ready"
  state rather than a partially-formed interval display, per
  `prototypes/screens/running-screen/States.dc.html`. It takes the ring's place, in the same 176dp
  slot, and its large number is `timer.large` at **51sp**. For the strip (`SCREEN-025`) and the
  lists (`SCREEN-023`), the interval the lead-in counts into **is the current interval**: its
  segment is the bright one and the lists are positioned around it, two before and four after.
  *(manual: the get-ready layout's own rendering is a screen fact; that the lead-in produces a
  different shape at all — `RunningScreenContent.GetReady`, carrying no full duration to show
  progress against — is `:core`, `:core`-tested via `RunningScreenContentTest.kt`; which interval
  counts as current during it is `runningScreenSchedule()`, `:core`-tested via
  `RunningScreenScheduleTest.kt`.)*
- **SCREEN-025** A timeline strip runs across the top of the screen, its top edge **66dp** from the
  top (#151 derives it as 12dp below the mute button's bottom edge, taking the button as 38dp tall;
  `SCREEN-021` notes the 48dp touch target that moves that edge), inset `spacing.xl` (20dp) on the left and right
  and filling the width between. It has **one segment per schedule entry**, in schedule order,
  warm-up and cool-down included, and every segment has **equal width** whatever its interval's
  duration. A segment is **8dp** tall with a **2dp** corner radius, filled with its kind's colour
  (`CUE-030`: work `work`, recovery `recovery`, warm-up and cool-down `neutral`). The current
  interval's segment is drawn at alpha **1.0** and every other segment, finished or upcoming, at
  alpha **0.25**; alpha is the only difference, so the current segment has no outline, no extra
  height and no progress fill. There is no text beside the strip — no count, no labels — and it is
  not tappable. *(manual: the strip's rendering is a screen fact; its segments, their kinds and
  their alphas are `runningScreenSchedule()`, `:core`-tested via `RunningScreenScheduleTest.kt`.)*
- **SCREEN-026** The gap between strip segments is **3dp** when the schedule has fewer than 40
  intervals and **1dp** when it has 40 or more. Segment height, radius and alpha do not change with
  the schedule's length. *(manual: the gap's rendering is a screen fact; which gap applies is
  `runningScreenSchedule()`, `:core`-tested via `RunningScreenScheduleTest.kt`.)*
- **SCREEN-027** The running screen is **locked to portrait** while it is shown, and only while it
  is shown: leaving it — to the summary, or to another screen while paused (`SCREEN-041`) —
  releases the lock, and every other screen keeps the platform's default orientation behaviour. No
  landscape layout of the running screen exists. *(manual: an activity's orientation is not
  reachable from a JVM runner.)*
- **SCREEN-028** The past list, the ring's slot and the upcoming list are one column, horizontally
  centred, filling the space from **92dp** below the top to **100dp** above the bottom, its children
  vertically centred as a group with **18dp** between them. The past list is **190dp** wide with a
  **minimum height of 64dp** and its rows bottom-aligned, so the nearest past interval sits closest
  to the ring. The ring's slot is 176dp square (`SCREEN-020`, `SCREEN-024`). The upcoming list is
  **190dp** wide with a **minimum height of 140dp** and its rows top-aligned. While paused, the
  strip, the lists and the ring show the held position and nothing on the screen differs from
  running except the pause/resume control's icon (`SCREEN-031`). *(manual: layout is a screen
  fact.)*

*Retired by [#151](https://github.com/derekwinters/Interval-trainer-android/issues/151):*
`SCREEN-022` (a "Round 2 of 4" indicator of the round in progress against rounds planned) and
`SCREEN-022a` (its definition of the round in progress as the ordinal of the last work interval
reached). "Round" read as confusing — it counted work intervals, so a work interval with no
recovery after it still counted as a round — and what was wanted was a sense of how many intervals
are left, which the timeline strip (`SCREEN-025`) shows directly. The `:core` function behind them,
`currentRound`, was removed with them. Rounds **completed** (`TIMER-061`), which only the summary
shows (§4), is a separate value and is unchanged.

`SCREEN-020`, `SCREEN-021` and `SCREEN-024`'s ring and get-ready content are one `:core` function
together: `runningScreenContent(clock)` on `TimerState`
(`core/src/main/kotlin/com/derekwinters/intervaltrainer/RunningScreenContent.kt`), returning
`RunningScreenContent.GetReady` or `.Active` — two shapes, not one shape with optional fields, so a
lead-in cannot be rendered as a partially-formed interval by construction (`SCREEN-024`) — or `null`
for idle or ended, the same split `WorkoutNotificationContent` (`SVC-025`) already uses for the
notification's own content. The running screen reads one answer rather than reassembling
`TimerState` into a display ad hoc, per this page's third invariant.

`SCREEN-023`, `SCREEN-024`'s current-interval rule, `SCREEN-025` and `SCREEN-026` are a second
`:core` function: `runningScreenSchedule()` on `TimerState`
(`core/src/main/kotlin/com/derekwinters/intervaltrainer/RunningScreenSchedule.kt`), returning the
current schedule position, the past and upcoming rows with their distances, alphas and scales, the
strip's segments with their alphas, and the strip's gap — or `null` for idle or ended. It takes no
clock, because nothing in it depends on time within an interval, only on the schedule position.

### 3.2 Controls

- **SCREEN-030** A mute icon button sits at the top of the screen and shows its own current state
  (muted or unmuted); tapping it toggles mute (`CUE-050`–`055`). It is the one control `CUE-054`
  specifies — there is no separate sound and vibration toggle here.
- **SCREEN-031** One primary control at the centre of the control row toggles between pause and
  resume (`TIMER-014`), rendered with the icon for the action it is about to take.
- **SCREEN-032** A secondary control skips to the next interval (`TIMER-040`–`045`).
- **SCREEN-033** A secondary control raises the stop confirmation (`SVC-050`).

### 3.3 Accessibility

Decided on [#162](https://github.com/derekwinters/Interval-trainer-android/issues/162),
2026-10-06: what TalkBack reads, how the screen behaves at a large system font scale, and where the
controls sit for one-handed use. Colour is already covered by `docs/spec/cues.md`'s "never hue
alone" rule and is not restated here. Every time below is written with the same `m:ss` formatter
the screen draws with (`docs/spec/build.md` §4, `formatSeconds`), so what TalkBack reads and what
the screen shows cannot drift apart.

- **SCREEN-034** The ring (`SCREEN-020`) is **one TalkBack focus stop**, whose description reads
  **"{kind}, {remaining} remaining of {duration}"** — for example "Work, 0:32 remaining of 0:45".
  The kind name, the countdown digits and the "of" line are not separate focus stops. The
  description is read when TalkBack lands on the ring; it is not announced as the digits change
  (`SCREEN-035`). The lead-in's get-ready content (`SCREEN-024`), which takes the ring's place, is
  not the ring and is not covered by this requirement. *(auto: `RunningScreenSemanticsTest.kt`.)*
- **SCREEN-035** When the current interval changes, a **polite live announcement** gives the new
  interval's kind and duration, **"{kind}, {duration}"** — for example "Recovery, 1:30". There are
  no per-tick announcements and no announcements at remaining-time marks. "The current interval"
  is `SCREEN-024`'s: during a lead-in it is the interval the lead-in counts into, so the end of a
  lead-in is not a change and is not announced. The announcement is the live region's content
  changing, so two adjacent intervals with the same kind and duration — which read identically —
  produce no announcement between them; adding words to tell them apart is not decided here.
  *(auto: `RunningScreenSemanticsTest.kt` asserts one
  polite live region holding the current interval, that it changes when the interval does, and
  that it does not change on a tick; that TalkBack actually speaks it once per change is manual.)*
- **SCREEN-036** "Total left" (`SCREEN-021`) is **one focus stop**, its label and its value read
  together. Each past and upcoming row (`SCREEN-023`) is **one focus stop** with the combined label
  **"{kind}, {duration}"** — for example "Recovery, 0:30". The timeline strip (`SCREEN-025`) is
  **hidden from TalkBack**. No "N of M" count is spoken anywhere on the screen, as none is drawn
  (`SCREEN-022`, retired). *(auto: `RunningScreenSemanticsTest.kt`.)*
- **SCREEN-037** The ring's slot and everything inside it — the ring itself, its kind label, its
  countdown digits and its "of" line, and the get-ready content that takes the slot during a
  lead-in (`SCREEN-024`) — are **fixed-size**: they do not scale with the system font scale, and
  draw at the sizes `SCREEN-020` and `SCREEN-024` give at a font scale of 1.0. Every other piece of
  text on the running screen scales with the font scale, and **nothing clips or overlaps at any
  font scale up to 2.0**, the largest Android 14 and later offer. *(manual: an emulator at font
  scale 2.0.)*
- **SCREEN-038** The controls stay where `SCREEN-030`–`033` and the control row put them: the
  transport row bottom-centre, `spacing.xxl` (24dp) above the bottom edge, and the mute button
  top-right. This is the screen's **one-handed requirement, and the current position meets it**.
  An accidental tap on stop is covered by the stop confirmation (`SVC-050`), not by moving stop
  away from the others. Every control's touch target is **at least 48dp** (`DS-005`, `DS-006`).
  *(manual: a screen fact.)*
- **SCREEN-039** *(invariant)* **The interval-change announcement is for screen readers only.**
  The announcement of `SCREEN-035` must not be a vibration, a sound or a spoken cue produced by the
  app, and it must not fire when no screen reader is running. It is an accessibility live region,
  which only an accessibility service reads aloud, so the app makes no sound of its own for it and
  nothing is announced to anyone not using one. `docs/spec/cues.md` is unchanged by it: v1's
  ruling that the app has no spoken cues still holds for everyone not using a screen reader. A
  `TextToSpeech` call, or an announcement pushed whether or not a service is listening, is the
  technically plausible way to get this wrong. *(manual: a screen-reader fact.)*

### 3.4 The navigation lock

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
`runningScreenContent` (`SCREEN-020`–`021`, `SCREEN-024`) and `WorkoutNotificationContent`
(`docs/spec/service.md` `SVC-025`) already give their own screens, so the summary reads one answer
rather than reassembling `TimerState.Ended`'s fields ad hoc. Its rounds-completed pair is
`List<ScheduleEntry>.roundsCompleted()` (`TIMER-060`–`061`) itself, read directly rather than a
second copy of it — a skipped work interval never counts, however far skip has since moved the
schedule past it. The running screen shows no round count at all (its round indicator was retired
by [#151](https://github.com/derekwinters/Interval-trainer-android/issues/151), §3), so rounds
completed appears only here.

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

- **SCREEN-060** Settings is reached from home's trailing header action (§1, `SCREEN-001`), and its
  own back action returns to home (`SettingsScreen.kt`'s `onBack`, wired in `MainActivity.kt` to
  `navController.popBackStack()` — settings has no other entry point in v1, so popping the back
  stack always lands there, the same convention the preset editor's own `onBack` already uses).
- **SCREEN-061** Settings contains exactly one item in v1: a switch for the **default mute
  state** — the value a new workout's effective mute starts from (`CUE-051`). Toggling it changes
  the default; it never affects a workout already running (`CUE-052`). The switch is
  `:designsystem`'s own `Toggle` (`design-system.md` `DS-014`), not
  `androidx.compose.material3.Switch` — see this page's own new invariant below, and this pull
  request's correction of `DS-014`'s previously-wrong claim about which component this row uses.
- **SCREEN-062** Settings is reachable while a workout is paused, as part of the rest of the app
  being reachable then (`SCREEN-041`); it is not reachable while a workout is running. This needs
  no code of its own: `SCREEN-040`'s own lock (§3.4) already makes `running` the only reachable
  screen while a workout runs, and settings is reached the same way every other screen is,
  through home's own navigation.
- **SCREEN-063** Settings contains a second item: a `ListItem` row for the notification
  permission. Its secondary text shows the permission's current state (granted or denied),
  re-read on every resume of this screen — `Context.isNotificationPermissionGranted()`
  (`MainActivity.kt`), the same `NotificationManagerCompat.from(context).areNotificationsEnabled()`
  check `WorkoutService.postNotification` (`docs/spec/service.md` `SVC-020`–`024`) already uses to
  decide whether to post at all, read again here rather than through a second mechanism for the
  same fact. Re-reading on resume, rather than once at composition, is what keeps the row current
  after the user returns from the system settings page `SVC-033`'s deep link opens — the one place
  this value can actually change while the app is not in front. A `Switch` (or `Toggle`) is not
  used here — unlike `SCREEN-061`'s default-mute value, this row does not hold a value the app owns
  and can flip on tap; it reflects a system permission the app cannot itself grant. When the state
  is denied, tapping the row opens the app's page in the system settings app (`SVC-033`); when it
  is granted, the row is present but inert (no `clickable` modifier at all), since there is nothing
  left to do from here.

> **Invariant — the default-mute value is read only at the moment a new workout starts; a running
> workout never observes it changing.** `WorkoutSession.handle`'s own `defaultMuted` parameter
> (`docs/spec/service.md` `SVC-014`) is consulted by `:core`'s `reduceAndFireCues` only for a fresh
> `TimerEvent.Start` (`CueSelection.kt`, `CUE-051`–`053`, already covered by `CueSelectionTest.kt`
> before this screen existed to read or write it); every other event keeps whatever effective mute
> the running workout already has. `WorkoutService` (`docs/spec/service.md` §3) passes its own
> cached `DefaultMuteStore` value into every `WorkoutSession.handle` call, not only `Start`'s, and
> this is what makes that safe rather than merely convenient: the branch inside `:core` that would
> ignore it for every other event already existed and was already tested, so this screen's own new
> code (`SettingsScreen.kt`, `DefaultMuteStore.kt`) never needed to re-decide, or re-test, when the
> value takes effect — only to supply a real one.

**Storage and wiring, concretely.** `docs/spec/screens.md` `SCREEN-080` names Jetpack DataStore
Preferences; `DefaultMuteStore` (`app/src/main/java/.../settings/DefaultMuteStore.kt`) is that
seam, and `DataStoreDefaultMuteStore` its one real implementation — a single named preferences
store (`"settings"`) `:database`'s own Room store never touches. `WorkoutService` reads it once,
synchronously, in `onCreate` (matching `AppDatabase.open`'s own blocking-on-`onCreate` convention),
then keeps a cached value current via a background collector, so applying a command never blocks
on storage I/O. `MainActivity.kt` reads the same store's `Flow` directly for the switch's own live
value, and writes to it from `onDefaultMutedChange`.

**Not specified.** Nothing beyond the default-mute switch and the notification-permission row is
decided for v1 settings. Candidates raised while deciding cues — a default vibration toggle, the
countdown length — are explicitly not built, and this specification does not anticipate them with
unused structure.

*(All of §5 is manual: two rows and one navigation fact, not reachable from a JVM runner without a
simulated Android runtime, the same as every other screen on this page. One small piece of new
logic this issue's own settings screen needed — the deep link `SCREEN-063` opens (`SVC-033`) —
factors out cleanly as a plain function with no `android.*` on its classpath
(`notificationSettingsDeepLink`, `app/src/main/java/.../settings/NotificationSettingsDeepLink.kt`)
and is `:core`-adjacent tested via `NotificationSettingsDeepLinkTest.kt`; everything else here —
the switch, the row, the resume-triggered re-check, and `DefaultMuteStore`'s own DataStore-backed
implementation — has no branching logic of its own to isolate from the Android APIs it calls, and
stays manual for the same reason `WorkoutService` and every other screen's own Android-only code
already does.)*

## 6. First-run

**Layout:** form (`DS-076`). **Vocabulary:** stock Material 3 (`DS-061`).

Never mocked up (`DS-061`); decided on
[#31](https://github.com/derekwinters/Interval-trainer-android/issues/31), 2026-09-11, as new v1
scope the map did not originally carry.

- **SCREEN-070** The app shows this screen exactly once: on the first run after install, before any
  preset is ever started, tracked by a persisted first-run-seen flag. Built (`#84`) as
  `FirstRunStore`/`DataStoreFirstRunStore`
  (`app/src/main/java/.../settings/FirstRunStore.kt`), the same interface-plus-one-DataStore-backed-
  implementation shape `DefaultMuteStore` already gives its own value (`SCREEN-061`) — see
  `SCREEN-080` below for where the flag actually lives. The flag is persisted the moment the
  screen's primary action (`SCREEN-072`) is tapped, **before** the system permission prompt is
  launched, not after it resolves: an app process interrupted in the gap between the tap and the
  system's own callback must still never show this screen a second time, and the explanation's own
  job — being shown and acted on — is already done the instant the button is pressed, regardless of
  how the async permission request that follows it comes back. A pending crash report's popup
  (§8, `SCREEN-090`) shows over whichever destination the app opens to, this screen included, and
  neither reads nor sets the first-run-seen flag: when this screen is shown is unchanged by it.
- **SCREEN-071** The screen explains, in plain language and before the system permission prompt
  appears, what the notification is for — status, pause/resume and skip while the screen is off or
  another app is in front — and that the app is about to ask the system for the notification
  permission. The owner's own framing of this copy, recorded on
  [#31](https://github.com/derekwinters/Interval-trainer-android/issues/31): *"do you want to have
  a notification while a workout is running? we need the notification permission for that. we'll
  ask if you want it."* The exact wording shown is not fixed by this specification, the same way
  [`docs/spec/cues.md`](cues.md) §9 leaves its numeric values tunable rather than pinned. Built
  (`#84`) as `FirstRunScreen`
  (`app/src/main/java/.../screens/firstrun/FirstRunScreen.kt`), `DS-061`'s stock-Material-3
  vocabulary, laid out with `FormLayout` (`DS-075`–`076`, no `ScreenHeader`: this screen has no back
  action).
- **SCREEN-072** A single primary action (`DS-075`'s one primary action) triggers the system
  `POST_NOTIFICATIONS` permission prompt (`SVC-030`) and, once it resolves either way, proceeds to
  home. There is no way to skip past the explanation without triggering the prompt. Built (`#84`)
  as `ActivityResultContracts.RequestPermission()` (`androidx.activity:activity-compose`), launched
  from `MainActivity.kt`'s own `first_run` route the moment `FirstRunScreen`'s `onGetStarted` fires
  — the explanation screen itself never calls an Android permission API directly, the same
  "a screen shows only what it is handed, and only ever calls back out" split every other screen in
  this app already follows (`ADR 0005`).
- **SCREEN-073** Declining the permission does not block using the app: home is reached exactly the
  same way regardless of the answer (`SVC-031`).

> **Invariant — the explanation is shown and acted on strictly before the system prompt, never
> alongside or after it.** `SCREEN-071`–`072`'s own wording already implies this order; stated here
> because "shown once" (`SCREEN-070`) and "asked once, automatically" (`docs/spec/service.md`
> `SVC-032`) are two different guarantees a technically-plausible implementation could conflate.
> `FirstRunScreen`'s primary action persists the first-run-seen flag synchronously, in the same
> callback that then launches the system prompt — not a callback chained onto the prompt's own
> result — so the flag's "shown once" guarantee never depends on how, or whether, the asynchronous
> permission request happens to resolve.

> **Invariant — declining or ignoring the notification permission changes nothing about a
> workout.** `docs/spec/service.md` `SVC-031` states this in full; restated here because it is the
> one property this whole screen, and the settings row it coexists with (`SCREEN-063`), must never
> accidentally violate: what a "no" or an ignored prompt costs is the notification's own status,
> pause/resume and skip, and its presence in the shade or on the lock screen — never workout timing
> or cue firing, and never whether home, the preset editor or a workout itself is reachable.

**Coexisting with `SCREEN-063`'s settings row (`#83`).** The first-run screen and the settings
notification-permission row read the same underlying fact —
`Context.isNotificationPermissionGranted()` (`MainActivity.kt`,
`NotificationManagerCompat.from(context).areNotificationsEnabled()`) — but never call the same
mutating API: this screen is the *only* call site in the app for
`ActivityResultContracts.RequestPermission()` (`SVC-032`'s "requests the permission once... and
does not prompt again automatically"), while the settings row (`SVC-033`) only ever reads that
state and, when it is denied, deep-links to the system's own notification-settings page — it never
re-invokes the in-app request API, which Android would not honour a second time regardless. The two
surfaces cannot race or double-prompt because only one of them ever asks.

*(All of §6 is manual — an explanatory screen and a permission request are not reachable from a JVM
runner — except `SCREEN-070`'s own start-destination consequence: whether the app lands on
`first_run`, `running` or `home` is computed by `startDestination()` in `:app`
(`com/derekwinters/intervaltrainer/StartDestination.kt`), a pure function of two booleans with no
`android.*` import, `:app`-tested via `StartDestinationTest.kt` — the same "pure function of state
and nothing else" split `WorkoutSession.handle` (`docs/spec/service.md` `SVC-014`) and
`notificationSettingsDeepLink` (`SVC-033`) already use for their own one isolatable piece.)*

## 7. Storage for settings and first-run state

- **SCREEN-080** The default mute value (`SCREEN-061`) and the first-run-seen flag (`SCREEN-070`)
  are stored via Jetpack DataStore Preferences, kept separate from the `:database` module's Room
  store (`SCHEMA-001`). Neither value has a query, a relation, or a migration story complex enough
  to need a schema; DataStore's typed key-value store is the ordinary shape for exactly this, and
  keeping it out of Room means it needs no exported schema and no contract-test policy of its own.
  This is this page's own small engineering call, not a decision recovered from an issue thread —
  noted in the pull request's Deviations section. The default-mute half of this is real now
  (`#83`): `DefaultMuteStore`/`DataStoreDefaultMuteStore`
  (`app/src/main/java/.../settings/DefaultMuteStore.kt`), a single named preferences store
  (`"settings"`). The first-run-seen flag is real now too (`#84`):
  `FirstRunStore`/`DataStoreFirstRunStore` (`app/src/main/java/.../settings/FirstRunStore.kt`),
  reading and writing the same `"settings"` store — one file, two keys, not two files. The shared
  `Context.settingsDataStore` property delegate that opens that one file is factored out into its
  own `SettingsDataStore.kt` in this pull request, rather than left as `DefaultMuteStore.kt`'s own
  private declaration: DataStore's `preferencesDataStore` delegate itself throws if two
  independently declared top-level properties both name the same file, so one shared property is
  what actually keeps this a single store rather than two that happen to agree on a name — see this
  pull request's Deviations section.

*(Manual: a storage-mechanism choice, visible in the dependency list.)*

## 8. Crash report popup

**Component:** `AlertDialog` (`DS-010`–`012`), over the start destination rather than a route of its
own. **Vocabulary:** stock Material 3 (`DS-061`).

Decided on [#146](https://github.com/derekwinters/Interval-trainer-android/issues/146): a popup,
shown before the first-run screen rather than after it, offering Copy and Dismiss and no Share.
The report it offers is the one `docs/spec/service.md` §9 records.

- **SCREEN-090** On launch, if a crash report exists (`docs/spec/service.md` `SVC-072`), the popup
  shows over whichever destination the app opens to — `first_run`, `running` or `home`
  (`SCREEN-070`, `SCREEN-043`). Whether it shows depends on the report existing and on nothing
  else: not on the first-run-seen flag, and not on whether a workout is active. It does not change
  which destination the app opens to. The report is read once, synchronously, before the first
  frame, the same convention `SCREEN-070`'s first-run-seen read uses.
- **SCREEN-091** The popup's title is "App crashed" and its text is exactly "The last launch
  crashed. Do you want to copy the crash logs for a bug report?". Its affirmative action, on the
  right, is **Copy**; its cancel action, on the left, is **Dismiss** (`DS-011`). There is no Share
  action and no third button.
- **SCREEN-092** **Copy** puts the report's full text on the clipboard as plain text and closes the
  popup for this launch. It does **not** delete the report, so the popup shows again on the next
  launch until Dismiss is pressed.
- **SCREEN-093** **Dismiss** deletes the report and closes the popup. A later launch shows no popup
  until another crash is recorded.
- **SCREEN-094** Closing the popup any other way — system back, or tapping outside it — closes it
  for this launch without copying anything and without deleting the report, like Copy without the
  copy.
- **SCREEN-095** "For this launch" lasts as long as the activity's saved state: a configuration
  change does not bring a closed popup back, and a new launch of the activity does.

> **Invariant — the report is deleted only by Dismiss.** Not by Copy, not by being shown, and not
> by being closed with back or a tap outside. A report deleted by anything short of an explicit
> Dismiss is a crash lost before the user acted on it.

*(The popup itself is manual: a dialog's presence and the clipboard are not reachable from a JVM
runner. Two pieces of it are pure functions and are tested: whether the popup shows, alongside the
start destination it does not affect (`SCREEN-090`), is `launchPlan()` in `LaunchPlan.kt`, via
`LaunchPlanTest.kt`; and what each of the three ways of closing it does to the report
(`SCREEN-092`–`094`, the invariant above) is `crashReportOutcome()` in `crash/CrashReportPopup.kt`,
via `CrashReportPopupTest.kt`.)*

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Home | SCREEN-001–009 | `PresetSummaryTest.kt` (SCREEN-003's round count and total, SCREEN-004's colour-strip segments); `PresetDaoTest.kt` (the order SCREEN-009 persists, `SCHEMA-015`); *(manual)* SCREEN-001–009 |
| The preset editor | SCREEN-010–019, SCREEN-014a, SCREEN-019a–019b | `PresetSummaryTest.kt` (SCREEN-018's total); `IntervalKindTest.kt` (SCREEN-014a's cycle); `ScheduleGeneratorTest.kt` (SCREEN-017's splice); `KindLabelTest.kt` (the kind-to-colour mapping SCREEN-012's rows and SCREEN-017's duration labels share); `PresetTest.kt` (SCREEN-019's save rule, SCREEN-019b's copy name); `PresetDaoTest.kt`, `RoomPresetStoreTest.kt` (SCREEN-019b's placement and deep copy, `SCHEMA-017`); *(manual)* SCREEN-010–019, SCREEN-014a, SCREEN-019a–019b |
| The running screen — content | SCREEN-020, SCREEN-021, SCREEN-023–028 | `RunningScreenContentTest.kt` (SCREEN-020's ring content, SCREEN-021's total-left value again, SCREEN-024's distinct get-ready shape); `RunningScreenScheduleTest.kt` (SCREEN-023's past and upcoming rows with their alphas and scales, SCREEN-024's current interval during a lead-in, SCREEN-025's segments and their alphas, SCREEN-026's gap); *(manual)* SCREEN-020, SCREEN-021, SCREEN-023–028 |
| The running screen — controls | SCREEN-030–033 | *(manual)* |
| The running screen — accessibility | SCREEN-034–039 | `RunningScreenSemanticsTest.kt` in `:app`, under Robolectric (SCREEN-034's ring description, SCREEN-035's polite live region and its silence on a tick, SCREEN-036's row labels, "Total left" as one stop and the hidden strip); *(manual)* SCREEN-035's announcement as TalkBack speaks it, SCREEN-037–039 |
| The running screen — navigation lock | SCREEN-040–046 | *(manual)* |
| Summary | SCREEN-050–053 | `SummaryContentTest.kt` (SCREEN-050's three values, again as `summaryContent()`'s packaged shape); *(manual)* SCREEN-050–053 |
| Settings | SCREEN-060–063 | `NotificationSettingsDeepLinkTest.kt` (SCREEN-063's deep-link action/extra); *(manual)* SCREEN-060–063 |
| First-run | SCREEN-070–073 | `StartDestinationTest.kt` (SCREEN-070's start-destination consequence); *(manual)* SCREEN-070–073 |
| Storage for settings and first-run state | SCREEN-080 | *(manual)* |
| Crash report popup | SCREEN-090–095 | `LaunchPlanTest.kt` (SCREEN-090's show-when-a-report-exists, independent of first run); `CrashReportPopupTest.kt` (SCREEN-092–094's copy and delete outcomes); *(manual)* SCREEN-090–095 |

**66 requirements, 3 `auto` and 63 `manual`.**

**Every requirement on this page but `SCREEN-034`–`036` is `manual`, and that is by design, not by
omission.** No screen's layout, control set, content or navigation is assertable on a JVM runner
without a simulated Android runtime — that is
[`docs/spec/design-system.md`](design-system.md)'s territory (`DS-091`, `DS-093`), and this page
does not duplicate it. The three exceptions are the running screen's TalkBack labels
([#162](https://github.com/derekwinters/Interval-trainer-android/issues/162)): what TalkBack reads
exists only in a semantics tree, so `RunningScreenSemanticsTest.kt` asserts it there, under the
Robolectric dependency `docs/spec/build.md` `BUILD-023` allows in `:app` for exactly these tests.
How the screen looks at font scale 2.0 (`SCREEN-037`) and what TalkBack actually speaks stay a
person's check on an emulator. Several requirements here — a preset's round count and total, and its
colour-strip proportions (`SCREEN-003`, `SCREEN-004`, `SCREEN-018`), a row's kind-cycling
(`SCREEN-014a`), the generator's own splice (`SCREEN-017`), the running screen's own ring,
get-ready state, schedule lists and timeline strip (`SCREEN-020`, `SCREEN-023`–`026`), and the
timer values the running screen and the summary read (`SCREEN-021`, `SCREEN-050`) — merely
*display* or *invoke* arithmetic that lives in `:core`; that coverage counts against the `:core`
function computing the value, not against the `SCREEN` requirement that a screen renders or wires it
correctly, which is what stays `manual` here regardless. `SCREEN-021` and `SCREEN-050` cite
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
([#80](https://github.com/derekwinters/Interval-trainer-android/issues/80)). `SCREEN-019`'s save
rule cites `isSavable` (`Preset.kt`), via `PresetTest.kt`
([#147](https://github.com/derekwinters/Interval-trainer-android/issues/147)). `SCREEN-020` and `024` cite `runningScreenContent`
(`RunningScreenContent.kt`), via
`RunningScreenContentTest.kt` — the same "pure state-derivation function for its own display" split
`WorkoutNotificationContent` (`docs/spec/service.md` `SVC-025`) already established for the
notification, applied here to the screen that motivated the pattern in the first place.
`SCREEN-023`–`026` cite `runningScreenSchedule()` (`RunningScreenSchedule.kt`), via
`RunningScreenScheduleTest.kt`, added with the stacked layout
([#151](https://github.com/derekwinters/Interval-trainer-android/issues/151)).
`SCREEN-003` and `SCREEN-004` are this page's own first new `:core` test file, added alongside home
itself ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79)):
`PresetSummary.kt` and `PresetSummaryTest.kt`, cited directly in each requirement's own text above
rather than only here. `SCREEN-070`'s own consequence — which of `first_run`, `running` or `home`
`MainActivity`'s `NavHost` starts on — is `startDestination()`
(`com/derekwinters/intervaltrainer/StartDestination.kt`), this pull request's own new pure function
in `:app` (not `:core`, since combining a workout's own liveness with the first-run flag is a
concern of `:app`'s NavHost, not of `:core`'s domain), taking both facts as plain booleans its
caller already resolved — the same "no `android.*` import" split `WorkoutSession.handle`
(`SVC-014`) and `notificationSettingsDeepLink` (`SVC-033`) already use — via `StartDestinationTest.kt`.
`SCREEN-019b`'s copy name cites `copyName()` (`Preset.kt`), via `PresetTest.kt`, and
`SCREEN-009`'s persisted order and `SCREEN-019b`'s placement and deep copy cite `SCHEMA-015` and
`SCHEMA-017`, via `PresetDaoTest.kt` and `RoomPresetStoreTest.kt`
([#163](https://github.com/derekwinters/Interval-trainer-android/issues/163)).
`SCREEN-012`'s and `SCREEN-017`'s colour dots both resolve through one `:app` function,
`IntervalKind.dotColor` (`screens/editor/KindLabel.kt`), via `KindLabelTest.kt`
([#136](https://github.com/derekwinters/Interval-trainer-android/issues/136)); that checks which
design-system colour each kind's dot takes, not that either screen draws it.
This page is honest that everything else — whether a screen's structure actually matches this page,
whether a control does what it says, whether the lock actually holds — is a human reading the
screen against this specification, the same proportion `docs/spec/design-system.md` reports for
the same reason.
