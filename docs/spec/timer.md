# Specification — Timer (`TIMER`)

How a workout runs: what a preset becomes when it starts, the states the timer moves between, how
the time remaining is worked out, and what the generator that fills a preset in bulk does.

There is no template anywhere in this page. A preset is an ordered list of intervals the user
authored one at a time, a workout takes its own copy of that list when it starts, and the timer
runs the copy. Nothing is expanded, nothing is unrolled, and nothing is reconstructed from a round
count — because no round count is stored. That is the whole model, and the sections below are its
consequences.

This page fixes **what the timer does**. It does not fix **what any of it sounds like**: which cue
fires at which boundary is [`cues.md`](cues.md) (`CUE`), and the two pages meet at exactly two
places — the countdown, whose three ticks this page's minimum interval is what guarantees, and the
lead-in, which fires a countdown and no boundary cue. It does not fix **where the clock lives**
either: the running workout and its scheduler belong to the foreground service
([ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md)), and the state machine
here is a pure reducer in `:core`
([ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md)).

Three things are deliberately out of scope. **The preset editor's screens** — how a list is
authored, reordered or deleted — belong to
[#29](https://github.com/derekwinters/Interval-trainer-android/issues/29) and
[#40](https://github.com/derekwinters/Interval-trainer-android/issues/40); this page fixes only
what the editor may not produce. **Persistence** is
[ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md)'s. And **what the summary looks
like** is a screen; this page fixes only the three values it reports.

Every decision here was taken on
[#28](https://github.com/derekwinters/Interval-trainer-android/issues/28) on 2026-09-12, walked
through against a throwaway prototype on the `prototype/timer-state` branch; the vocabulary is the
glossary's, in [`CONTEXT.md`](../../CONTEXT.md).

---

## Invariants

> **Invariant — remaining time is computed from a deadline on a monotonic clock, and never
> accumulated from ticks.** Each interval has a deadline; the time remaining is that deadline minus
> the current reading of the clock, worked out fresh every time it is asked for. The tempting
> implementation — hold a counter and subtract each tick's nominal length from it — is technically
> plausible, passes a test that ticks exactly on time, and is wrong: every late tick, every dropped
> frame and every doze rounds into the counter, so the schedule drifts further behind real time the
> longer the workout runs. It is excluded here rather than discovered in a half-hour workout.

> **Invariant — the schedule contains only intervals the user authored.** The lead-in is a state
> the timer enters, never a row. Synthesising a three-second row and pushing it onto the front of
> the schedule works for starting a workout and then falls apart everywhere else: it has to be
> inserted again on every skip, it has to be excluded from the total, from the round count and from
> the interval list, and every piece of code reading the schedule has to know which rows are real.
> A schedule row means the user asked for it.

> **Invariant — the timer runs a schedule, never a preset.** A workout copies its preset's list
> when it starts and reads that copy for the rest of its life. Holding a reference to the preset
> instead would mean a workout changing under the user's feet when a preset is edited on another
> screen, and vanishing when one is deleted. The consequence for a paused workout is recorded in
> [ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md).

> **Invariant — no interval is shorter than five seconds, anywhere.** This is the load-bearing
> constraint of the page. It is what makes every countdown fit: the earliest of the three ticks
> lands two seconds after an interval begins, so no countdown can truncate and no tick can fall on
> the same instant as a boundary cue. Both of those were live questions while the minimum was
> three seconds, and both dissolve rather than being arbitrated. Anything that lowers the minimum
> re-opens them, which is why the rule belongs here and not in a validator.

> **Invariant — nothing about the generator is stored.** A preset holds rows and nothing else: no
> round count, no generated group, no link back to the generator that produced a row. Recording the
> parameters looks helpful — it would let a group be re-generated — and it is what turns a list into
> a tree, because every row then has to know whether it is still governed by something. Keeping the
> model as simple as a list is what makes a future generator with a different shape cost nothing:
> it is another function emitting ordinary rows, with no model change and no migration.

---

## 1. Presets and schedules

- **TIMER-001** A preset is an **ordered list of intervals**, each with a kind and a duration. The
  order is the order they run. Nothing in a preset is a template and nothing is expanded when a
  workout starts.
- **TIMER-002** Starting a workout takes the workout's **own copy** of the preset's list. That copy
  is the **schedule**, and everything below operates on it.
- **TIMER-003** Editing or deleting the preset after a workout has started leaves that workout's
  schedule untouched, whether it is running or paused.
- **TIMER-004** A preset stores rows and nothing else — no round count, no generated group, no
  reference to whatever produced a row. *(manual: an absence from the stored model; visible in the
  diff and in the exported schema.)*

## 2. The states and the events

Four states and six events, and nothing else moves the timer. The table is the whole machine: an
event that has no cell for the state it arrives in changes nothing and emits no cue, which is
stated once here rather than repeated as a requirement per pair.

- **TIMER-010** The timer has exactly four states: **idle**, **running**, **paused** and **ended**.
- **TIMER-011** **ended** carries an **outcome**, exactly one of *completed* or *stopped early*.
  There is no ended state without one, and there is no third outcome.
- **TIMER-012** The events are **start**, **tick**, **pause**, **resume**, **skip** and **stop**. An
  event with no cell in the table below leaves the state unchanged and emits no cue.
- **TIMER-013** **start** is accepted only in idle, and only when the schedule holds at least one
  interval. It moves to running and enters the lead-in (§4).
- **TIMER-014** **pause** is accepted only in running, and **resume** only in paused.
- **TIMER-015** The workout ends **completed** when the last interval in the schedule reaches its
  deadline.
- **TIMER-016** **stop** is accepted in running and in paused, and ends the workout with outcome
  *stopped early* (§6).
- **TIMER-017** A workout that has ended accepts no event. Returning to idle is loading a preset
  again, which is not an event of this machine.
- **TIMER-018** While running, the timer is either in the lead-in or in an interval of the schedule.
  That distinction is part of the running state, not a fifth state.

| Event | idle | running | paused | ended |
|---|---|---|---|---|
| **start** | → running, entering the lead-in before the first interval | — | — | — |
| **tick** | — | advances the clock; may cross a deadline and begin the next interval, or end the workout *completed* | — | — |
| **pause** | — | → paused, holding the remaining time | — | — |
| **resume** | — | — | → running, from the held remaining time | — |
| **skip** | — | → running, entering the lead-in before the next interval | → running, entering the lead-in before the next interval | — |
| **stop** | — | → ended, *stopped early* | → ended, *stopped early* | — |

A dash is not an error. It is an event arriving at a moment the user cannot have caused it — a
resume with nothing paused, a tick after the workout finished — and doing nothing is the correct
response to all of them.

**Deliberately unspecified:** what **skip** does while the *lead-in itself* is running. Whether it
abandons the lead-in and starts the upcoming interval immediately, restarts the lead-in, or moves
past that interval altogether was not decided on #28. It is listed here as a hole rather than
filled in.

## 3. The deadline model

- **TIMER-020** The remaining time in the current interval is **deadline minus now**, read from a
  monotonic clock and computed fresh on demand. It is never accumulated, and never adjusted by a
  tick's nominal length.
- **TIMER-021** A **late tick does not move the deadline**. It reports a smaller remaining time than
  a punctual one would have, the interval still ends when the deadline is reached, and the schedule
  does not drift.
- **TIMER-022** A tick that carries the clock **past** a deadline ends that interval at the deadline
  and begins the next one there. The overshoot is not added to the next interval and is not carried
  forward.
- **TIMER-023** **pause** holds the remaining time — the value of deadline minus now at the instant
  pause is accepted — and the deadline ceases to exist while paused.
- **TIMER-024** **resume** sets a new deadline at now plus the held remaining time, and discards the
  held value. No workout time passes while paused.
- **TIMER-025** **Total remaining** is the remaining time of the current interval plus the full
  duration of every interval after it in the schedule. While paused it is computed from the held
  remaining time, so it does not move.

## 4. The lead-in

The lead-in is the three seconds between asking for an interval and getting one. It exists because
the moment the user presses start is the one moment they are certainly holding the phone, and three
seconds is the difference between beginning a run and fumbling a phone into a pocket while the first
interval burns. Skip earns it for the same reason.

- **TIMER-030** The lead-in is **three seconds** long.
- **TIMER-031** It is entered on **start** and on **skip**, and at no other moment.
- **TIMER-032** The lead-in is a state the timer enters. It is never a row in the schedule, and it
  appears in no interval list, no round count and no total.
- **TIMER-033** A lead-in fires the countdown and **no boundary cue**. The boundary cue belongs to
  the interval that follows and fires when the lead-in's deadline is reached
  (`CUE-041`, `CUE-045`).
- **TIMER-034** A lead-in is time **on top of** the schedule: while it runs, total remaining counts
  the upcoming interval in full.
- **TIMER-035** A skip therefore **adds three seconds** to a workout's wall-clock length. This is
  accepted rather than compensated for; taking the three seconds out of the interval that follows
  would make a skipped-into interval shorter than the user authored.
- **TIMER-036** **pause** and **resume** apply to a lead-in exactly as they apply to an interval:
  the remaining lead-in is held and continues from where it stopped.

## 5. Skip

- **TIMER-040** **skip** abandons the current interval and moves to the next one in the schedule,
  which begins after a **lead-in** — the same three seconds that starting a workout gives.
- **TIMER-041** Any countdown already running when skip is accepted is **abandoned**: its remaining
  ticks do not fire. The lead-in's own countdown starts from three.
- **TIMER-042** Skip is accepted while **paused** as well as while running, and a skip from paused
  leaves the timer running.
- **TIMER-043** Skipping the **last** interval in the schedule ends the workout with outcome
  *completed*. There is nothing left to lead in to, and the schedule was not cut short by a stop.
- **TIMER-044** Skip is **not configurable in v1**. Whether it gives a lead-in becomes a setting
  later; no setting is built now, and no indirection is built in anticipation of one. *(manual: a
  scope boundary, verified by an absence in the diff.)*

## 6. Stop and the summary

- **TIMER-050** **stop** ends the workout with outcome *stopped early*, and fires **no finish cue**
  — `CUE-013` puts the finish tone at completion, and stopping was deliberate, so there is nothing
  to announce to someone who just pressed the button.
- **TIMER-051** A workout that ends, either way, goes to the **summary**. *(manual: a navigation
  fact; the summary screen is [#29](https://github.com/derekwinters/Interval-trainer-android/issues/29)
  and [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40)'s.)*
- **TIMER-052** The summary's values are exactly three: **rounds completed** (§7), **total time**,
  and **whether the workout completed or was stopped early**. There is no fourth.
- **TIMER-053** The summary is **ephemeral**. Nothing about a finished workout is written down;
  history is out of scope for v1. *(manual: an absence of persistence; visible in the schema.)*
- **TIMER-054** **Total time** is the time the workout spent running — lead-ins included, time spent
  paused excluded, since no workout time passes while paused (`TIMER-024`).

## 7. Rounds completed

A round is a work interval. Not a work interval and its recovery, which is what the word means when
describing a workout, because there is nothing in a freely authored list that makes a particular
recovery belong to a particular work interval. Counting work intervals is the only count the model
can actually support, and it is what "3 of 5" has to mean.

- **TIMER-060** A **round** is a **work** interval. Rounds planned is the number of work intervals
  in the schedule; every other kind counts towards nothing.
- **TIMER-061** Rounds completed counts **work intervals finished** against work intervals in the
  schedule, and is reported as a pair — "3 of 5".

This is accepted as good enough for v1, with a better rendering of a non-uniform workout left to the
running screen's own issues.

**Deliberately unspecified:** whether a work interval the user **skipped** counts towards rounds
completed. It was left behind rather than finished, and it was also got past; #28 settled that a
round is a work interval without settling this, so it is a hole rather than a decision.

## 8. The minimum interval

- **TIMER-070** The shortest interval that can be authored is **five seconds**.
- **TIMER-071** Every interval fires **exactly three countdown ticks**, and so does the lead-in.
  This is the consequence the minimum exists for: the earliest tick lands two seconds after an
  interval begins, so no countdown truncates and no tick coincides with a boundary cue
  (`CUE-040`, `CUE-042`, `CUE-044`).
- **TIMER-072** The editor **rejects** a duration below the minimum, zero included. There is no
  separate rule dropping zero-length intervals when a schedule is built, because none can exist to
  drop.
- **TIMER-073** **No component may emit a row shorter than the minimum**, the generator included
  (§9). The minimum is a property of every interval that reaches a schedule, not a check on one
  entry point.

## 9. The generator

The generator is how a preset gets filled in bulk without authoring twenty rows by hand. It is a
convenience over the list and nothing more: what it produces is rows, and a row it produced is
indistinguishable from one typed in.

- **TIMER-080** The generator takes **four inputs**: a round count, a work duration, a recovery
  duration, and whether to leave a **trailing recovery**.
- **TIMER-081** It emits, for each round, a work interval followed by a recovery interval — except
  that the last recovery is emitted only when the trailing-recovery flag is set. Four rounds with
  the flag off give **seven** rows, ending on work.
- **TIMER-082** Trailing recovery **defaults off**, because a cool-down usually follows the rounds
  and a workout that ends on a recovery before a cool-down has two low-effort intervals back to
  back.
- **TIMER-083** The generator **appends** to the end of the preset's list, always.
- **TIMER-084** There is **no replace mode**. Replacing the list would delete a warm-up the user had
  just authored, and what "replace" actually describes — starting a preset from a template — is a
  create-time feature that is not in v1. *(manual: an absence of a control and of a code path;
  visible in the diff.)*
- **TIMER-085** Every row the generator emits is an **ordinary row**, individually editable the
  moment it lands and indistinguishable from a hand-authored one.

Nothing the generator was given is kept — see `TIMER-004` and the invariant above it. That is what
makes the owner's future idea, a generator shaping interval ratios across a workout rather than
repeating a uniform pair, a new function rather than a new model.

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| Presets and schedules | TIMER-001–004 | `TimerStateTest.kt` (TIMER-001–003); *(manual)* TIMER-004 |
| The states and the events | TIMER-010–018 | `TimerStateTest.kt` |
| The deadline model | TIMER-020–025 | `TimerStateTest.kt` |
| The lead-in | TIMER-030–036 | `TimerStateTest.kt` |
| Skip | TIMER-040–044 | `TimerStateTest.kt` (TIMER-040–043); *(manual)* TIMER-044 |
| Stop and the summary | TIMER-050–054 | `TimerStateTest.kt` (TIMER-050, 052, 054); *(manual)* TIMER-051, 053 |
| Rounds completed | TIMER-060–061 | `TimerStateTest.kt` |
| The minimum interval | TIMER-070–073 | `ScheduleGeneratorTest.kt` (TIMER-070, 072–073); `CueSelectionTest.kt` (TIMER-071) |
| The generator | TIMER-080–085 | `ScheduleGeneratorTest.kt` (TIMER-080–083, 085); *(manual)* TIMER-084 |

**48 requirements, 43 `auto` and 5 `manual`.**

**The `auto` tests do not exist yet.** There is no implementation of any of this, and no `:core`
module to hold one — the build today is a single `:app` module (`BUILD-002`). `TimerStateTest.kt`
and `ScheduleGeneratorTest.kt` are the files those tests will live in when the timer is built, at
`core/src/test/kotlin/com/derekwinters/intervaltrainer/core/`, alongside the `CueSelectionTest.kt`
that [`cues.md`](cues.md) names; all three are named in the future tense on purpose. Nothing in this
page is covered today. A requirement marked `auto` is a promise that a JVM test *can* assert it and
*will*, not a claim that one does.

**One assertion is worth naming before it is written.** `TIMER-071` — every interval fires exactly
three countdown ticks, and so does the lead-in — is not a formality. The prototype's first build
gave the lead-in **two** ticks, because a tick landing on the very instant an interval began was
excluded by a strictly-greater-than comparison; the same off-by-one would have clipped the first
tick off any interval whose countdown started on a tick boundary. It is an easy defect to
reintroduce and a cheap one to assert against, and it is the reason this page counts ticks rather
than describing them.

**Why so few are `manual`.** Almost everything here is arithmetic over a schedule, an event and a
clock reading, which is exactly what [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md)
puts in `:core`: a fake monotonic clock turns a thirty-four-minute workout into a millisecond of
test time, so the long cases cost no more than the short ones, and a reducer with no dependency on
`android.*` can be driven event by event. The five that are not are an absence from a stored model,
an absence of a setting, an absence of a control, an absence of persistence, and one navigation
fact that belongs to a screen this page does not specify — four of the five verified by reading a
diff rather than by running anything.
