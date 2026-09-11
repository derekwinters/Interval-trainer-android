# 5. A pure-JVM core and a thin Android shell

- **Status:** accepted
- **Date:** 2026-09-10
- **Decided by:** @derekwinters
- **Issue:** [#24](https://github.com/derekwinters/Interval-trainer-android/issues/24)
- **Research:** [`docs/research/room-migration-testing.md`](../research/room-migration-testing.md)
  (issue [#27](https://github.com/derekwinters/Interval-trainer-android/issues/27)) and
  [`docs/research/ui-consistency.md`](../research/ui-consistency.md) (issue
  [#38](https://github.com/derekwinters/Interval-trainer-android/issues/38))
- **Specification:** the module layout lands with the v1 specification,
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33); the build
  specification is [`docs/spec/build.md`](../spec/build.md)

## Context

Everything this app gets wrong, it gets wrong in logic: a schedule expanded with the rest interval
in the wrong place, a remaining time that drifts, a countdown cued at the wrong second, a state
machine that lets pause and skip disagree. None of that is Android. All of it is arithmetic and
state transitions, and all of it is the part a test can hold still.

The constraint that shapes the answer is that **continuous integration here has no emulator and no
connected device.** The pull-request workflow runs `./gradlew test` and `./gradlew assembleDebug` on
a Linux runner, and an instrumented test on that runner does not run at all — it is a test that
exists and never fails, which is worse than no test. Anything whose correctness matters therefore
has to be reachable from a plain JVM unit test, or it is verified by a human remembering to check,
which is not verification.

The owner has built this shape before: other projects here keep a platform-free core with the
platform pushed to the edge, and the practice is what this decision adopts rather than invents.

## Decision

**The app is split into a `:core` Gradle module and the `:app` Android module.**

**`:core` is pure Kotlin, JVM only.** It holds:

- the preset model,
- schedule expansion — the pure function from a preset to the flat, ordered schedule the timer runs,
- the timer state machine — running, paused, the current index, the deadline, what skip and stop do
  to each,
- formatting, including the duration formatting the build skeleton already carries,
- the screen-state reducers: the values each screen renders, derived from workout state.

It talks outward through small interfaces it defines and does not implement — **a clock, a cue sink,
a preset store**. Each is a seam the tests substitute: a fake clock makes a twenty-minute workout a
millisecond of test time, a recording cue sink turns "a tone fires at each of the last three
seconds" into an assertion on a list, and an in-memory preset store removes the database from every
test that is not about the database.

**`:app` is the shell.** Compose screens that render the state `:core` computes and forward taps as
commands; the foreground service that drives `:core`'s clock and owns the running workout, per
[ADR 0002](0002-the-foreground-service-owns-the-running-workout.md); the storage adapter; the tone
and vibration adapters. The shell's job is to be obvious — it has no branching worth testing,
because every branch worth testing was pushed into `:core`.

> **Invariant — `:core` depends on nothing in `android.*`, and a build that violates it fails.**

The invariant is the whole decision. A core that is *mostly* platform-free is a core that stops
being testable the first time someone reaches for `android.os.SystemClock` or a `Context` to read a
string, and the failure is silent: the code compiles, the tests still pass, and the module has
quietly become an Android library. Applying the Kotlin JVM plugin rather than the Android library
plugin is what makes the violation a compile error rather than a review comment — `android.*` is not
on `:core`'s compile classpath at all, so the wrong import cannot build.

## The Robolectric scope, and what changed

An earlier draft of this decision, written on
[#24](https://github.com/derekwinters/Interval-trainer-android/issues/24) on 2026-09-10, said
Robolectric tests the shell on the JVM — "screens, DAO, migrations".

**That has narrowed. Robolectric's scope is the Compose screens only.** It is not the database's
test tool, and the DAO and migration half of that sentence is withdrawn.

What changed is [#32](https://github.com/derekwinters/Interval-trainer-android/issues/32), which
decided the schema and how it is tested after this decision was drafted: **the database lives in a
Kotlin Multiplatform module with an Android target and a JVM target, and its migration and schema
tests run in the JVM target under `BundledSQLiteDriver` — real SQLite, with no Android runtime
involved at all.** They are ordinary `./gradlew test` unit tests.

The research on [#27](https://github.com/derekwinters/Interval-trainer-android/issues/27), recorded
in [`docs/research/room-migration-testing.md`](../research/room-migration-testing.md), is what made
that the better route and this one the worse. Under Robolectric the Android `MigrationTestHelper`
loads schemas **from assets**, while Room's Gradle plugin copies exported schemas only where
instrumented tests can see them — so making the Robolectric route work means putting the schema
directory somewhere Room's own documentation says not to put it ("commit the schema files into your
version control system (but don't ship them with your app!)"). The multiplatform route takes a
schema directory path on disk and needs none of that.

**Robolectric remains the route for the Compose screens**, because the semantics-tree assertions in
[`docs/research/ui-consistency.md`](../research/ui-consistency.md) are how cross-screen consistency
is asserted without an emulator, and those tests need a simulated Android runtime in-process.

**One open question travels with that, and this ADR does not settle it.** The same research records
that Robolectric **downloads an `android-all` jar from Maven Central at test time** unless
`robolectric.dependency.dir` or `robolectric.offline` is set — a network dependency inside
`./gradlew test`, which bears directly on the build specification's first invariant, that the build
runs from a clean checkout with nothing installed but a JDK. Vendoring or pre-fetching the jar is
documented by Robolectric itself as the hermetic-build answer, but it is a deliberate piece of work
with a real cost, and the research explicitly leaves the question to the owner. **It is a live
specification question for [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33),
not something decided here.** What this ADR does is keep the question small: nothing in `:core`
depends on the answer, so if Robolectric is refused, what is lost is the screen tests and not the
timer's.

## Alternatives considered

**Everything in `:app`.** One module, no boundary, Android on the classpath everywhere. It is the
default shape of an Android project and it is what the build is today. Rejected because the
boundary is exactly what is being bought: with `android.*` available, the timer's state machine will
reach for a platform clock, the reducers will reach for a `Context`, and each reach converts a test
that runs in milliseconds on the JVM into one that needs a device this project's continuous
integration does not have. The module boundary is not organisation; it is the mechanism.

**An Android library module for the logic.** A `:core` that applies the Android library plugin —
still a separate module, still mostly pure, but with the platform on its compile classpath.
Rejected for the same reason, in its sharper form: it makes the invariant unenforceable. A library
module can import `android.*` by construction, so "depends on nothing in `android.*`" becomes a
convention that a reviewer has to notice being broken, and the whole point of stating it as an
invariant is that the build notices instead. It also drags the Android Gradle plugin, a manifest and
resource processing into a module that needs none of them.

## Consequences

- **Every automatable requirement identifier lives in `:core`.** When the v1 specification is
  written, the requirements with real tests behind them — schedule expansion, the zero-length
  interval dropped, the monotonic deadline, the countdown's three cues, what pause and skip do, what
  the summary reports — are `:core` requirements. That is a constraint on how the specification is
  written, not only on where code goes: a behaviour that cannot be stated as a `:core` requirement
  is a behaviour about to be untested.
- **The service and the real sound stay manual.** Doze behaviour, wake-lock survival, the
  notification's actions, whether a tone actually comes out of the speaker at the right moment and
  with the right ducking — none of that is assertable on a JVM runner, and this decision does not
  pretend otherwise. Those requirements are marked manual and verified on a device by a human, and
  keeping their number small is the point of pushing everything else into `:core`.
- **`:core` does not exist yet.** The build today is a single `:app` module — `settings.gradle.kts`
  includes exactly one, which is `BUILD-002`. This ADR therefore constrains where code may be
  written when the modules are created; it does not describe the build as it stands, and the build
  specification needs amending on
  [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33) before it does.
- **The database is a third module, not part of `:core`.** `:core` is JVM-only and Android-free; the
  database module decided on [#32](https://github.com/derekwinters/Interval-trainer-android/issues/32)
  is multiplatform with an Android target. `:core` therefore defines the preset store as an
  interface and the database module implements it, which is the same seam the in-memory fake uses.
- **Formatting moves.** `formatSeconds` and its tests live in `:app` today, where `BUILD-030`–`033`
  put them. They are `:core` code by this decision, and the move is part of creating the module —
  including the traceability table in [`docs/spec/build.md`](../spec/build.md), which names the test
  file by path.
