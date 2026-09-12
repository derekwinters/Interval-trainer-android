# 3. Room from v1, with the schema treated as an API

- **Status:** accepted; amended 2026-09-12, see [Amendment, 2026-09-12](#amendment-2026-09-12)
- **Date:** 2026-09-11
- **Decided by:** @derekwinters
- **Issue:** [#32](https://github.com/derekwinters/Interval-trainer-android/issues/32)
- **Research:** [`docs/research/room-migration-testing.md`](../research/room-migration-testing.md)
  (issue [#27](https://github.com/derekwinters/Interval-trainer-android/issues/27))
- **Specification:** the `presets` columns and the rest of the storage behaviour land with the v1
  specification, [#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)

## Amendment, 2026-09-12

Two corrections, made under
[#52](https://github.com/derekwinters/Interval-trainer-android/issues/52). **Every decision this ADR
records still stands** — Room from v1, the Kotlin Multiplatform module, migration tests on the JVM
with no Android runtime, the schema treated as an API, and the contract-test policy with its three
invariants. What is corrected is a name and an assumption. The body below is edited in place only
where it would otherwise contradict this section, and each of those edits points back here.

**1. Tabata is not seeded.** This ADR was written from #32's comments, which predated
[#30](https://github.com/derekwinters/Interval-trainer-android/issues/30)'s ruling that the app
seeds **two endurance presets**, one short and one full. Tabata was rejected there explicitly, as a
high-intensity anaerobic protocol that misrepresents what this app is for. Only the name was wrong.
The decision that sentence carries — a seeded preset is an ordinary row with no marker, renameable
and deletable like any other, accepting that "restore defaults" would later need a marker added by
migration — is correct and unchanged.

**2. A preset owns an ordered list of intervals; it is not one row.** @derekwinters decided on
[the map](https://github.com/derekwinters/Interval-trainer-android/issues/22) that a preset is an
**ordered list of intervals**, each with a kind and a duration, rather than a fixed template of
warm-up, work, recovery, a round count and cool-down. The editor offers a generator that inserts
rows, and what is stored is always the flat list. This ADR assumed a preset was a single row, and
that assumption is withdrawn: **a preset owns an ordered collection of intervals, and that order is
explicit**. An interval's place in its preset is data the user authored, so the storage has to keep
it and give it back unchanged.

**The two orderings are different things, and are easy to confuse.** *Interval order within a
preset* is the one just described — authored, explicit, and preserved. *The order of presets in the
list* is what the "no `position` column" decision below is about, and that decision is unchanged:
preset list order is insertion order, reordering the list is out of scope for v1, and no column
exists for it. Stating the first is not an argument for the second, and nothing here reopens it.

**What that means for the schema is still not decided here.** The column list was already deferred
to the v1 specification on
[#33](https://github.com/derekwinters/Interval-trainer-android/issues/33), explicitly including
what is stored about intervals, and that deferral stands. How interval order is represented is
specification work; whatever it turns out to be, it is an ordinary schema change under the
contract-test policy below, with the same migration and upgrade-path test required of any other.

## Context

Presets are the first thing this app has that is worth keeping. A preset is typed in once and used
for months; nothing regenerates it, and nothing else holds a copy. Workout history is planned for
v2, which means whatever stores presets has to survive a second table arriving later — by
migration, on a device that already has data in it.

What makes that a one-way question rather than an adjustable one is how this app is distributed. It
is sideloaded. There is no store rollback, no staged release, no server copy of anyone's data, and
— per [ADR 1](0001-release-signing-with-a-stable-keystore.md) — no route that reinstalls the app
without deleting its data directory. **A bad migration reaches the device, and the data is gone.**
There is no second chance and nobody to ask for a backup.

The primary-source research on
[#27](https://github.com/derekwinters/Interval-trainer-android/issues/27), recorded in
[`docs/research/room-migration-testing.md`](../research/room-migration-testing.md), settles the
platform constraints that bound the answer:

- **Room's current stable release is Room 3, published under a new Maven group,
  `androidx.room3`.** The 2.x line still ships but Google's own Room 3.0 announcement puts it in
  maintenance mode. Room 3 is Kotlin Multiplatform, KSP-only, coroutine-only, and built on
  `androidx.sqlite`'s driver API rather than the Android framework's SQLite.
- **`MigrationTestHelper` is an `expect` class with a different constructor per platform.** The JVM
  implementation takes a schema directory path on disk, a database file and an `SQLiteDriver`; the
  Android implementation takes an `android.app.Instrumentation` and loads schemas from assets.
  **Which one a test gets is decided by the Gradle target it compiles against, not by a flag** —
  so the choice of module shape *is* the choice of how migrations are tested, made once, at the
  point the module is created.
- **The exported schema JSON is the fixture.** A test builds a database at version N from the
  committed schema for N, so there is no binary `.db` to keep per release. Room checks a schema
  change in three places of its own: the compiler's `SchemaDiffer`, `runMigrationsAndValidate` in
  tests, and the identity-hash check at runtime. There is no first-party linter for schema JSON
  diffs.

That Room is the store at all was settled while charting the map on 2026-09-10 and was not
reopened on [#32](https://github.com/derekwinters/Interval-trainer-android/issues/32); what #32
decided is the module shape, the testing, the table and the policy.

## Decision

**Presets are stored in Room from v1, and the exported schema is treated as an API** — committed,
reviewed as a diff, and changed only under a rule with a test behind it.

**The database lives in a Kotlin Multiplatform module** with an `androidTarget()` and a `jvm()`
target, holding the `@Database`. The app consumes the Android target. **Migration tests live in the
JVM target** and use `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled` — real SQLite
compiled from source, with no Android runtime involved — so they run under plain `./gradlew test`
with no emulator and no Robolectric. This is exactly how AndroidX tests Room itself.

**The schema is an API.** Exported schema JSON is committed at the Room convention, a `schemas/`
directory under the module, and those files are the per-version fixtures. Nothing else is kept: no
binary database per release, no hand-written fixture files that can drift from what the app
actually writes.

**Both halves of the schema testing exist from v1**, because they are different things and one does
not imply the other:

1. **Change detection.** The committed schema JSON means an unintended schema change appears as a
   reviewable diff in the pull request that caused it, alongside Room's own three checks.
2. **Upgrade-path execution.** `createDatabase(N)` from the committed schema for version N, real
   rows inserted through raw SQL, `runMigrationsAndValidate(M, …)`, then assertions that the data
   survived.

**The contract-test policy.** Additive changes — a nullable column, or a column with a default —
are safe and need nothing beyond the ordinary exported-schema diff. A breaking change — dropping a
column, renaming one, narrowing a type, adding a non-null column without a default — is allowed
**only** with a migration and an upgrade-path test that proves data survival. The test is the gate:
if the upgrade-path test cannot be written, the change cannot be made.

Three invariants carry that policy into the build, so it is honoured by construction rather than
remembered at review time.

> **Invariant — the upgrade-path harness exists before the first migration, not alongside it.** At
> v1 there is one schema version and no path to walk, and the harness is built anyway. It is ready
> at the moment of the first change rather than stood up under the pressure of making it.

> **Invariant — a breaking schema change lands only with a migration and an upgrade-path test that
> proves data survival.** The test creates a database at the previous version with real rows,
> migrates it, and asserts on the rows that came through. Prose does not prove a row survived.

> **Invariant — the database module's migration tests run on the JVM, with no Android runtime.**
> The module has an Android target, so it is not `:core`-pure; what is constrained is its tests.
> They compile against the JVM target and need no emulator, no instrumentation and no simulated
> Android runtime, and no schema file is moved into assets to make some other runtime see it.

## What this records, and what the specification records

The `presets` table is decided as far as this decision depends on it, and no further.

- **A preset owns an ordered collection of intervals**, each with a kind and a duration, and that
  order is explicit. A preset is not a single row: whatever shape the storage takes, a preset's
  intervals are stored with it and come back in the order the user authored. Added by
  [Amendment, 2026-09-12](#amendment-2026-09-12).
- **Identity is a generated UUID string**, assigned at creation — not an auto-incrementing
  integer. The integer is Room's default and marginally simpler, but a UUID costs nothing now and
  leaves export, sharing and sync possible later without renumbering rows. An identifier is then
  meaningful outside the one device that minted it.
- **Ordering is insertion order, with no `position` column.** Reordering the preset list is out of
  scope for v1, and an unused column is schema that has to be migrated later for no benefit. When
  reordering arrives, it arrives as a migration — which is what the harness above is for. **This is
  the order of presets in the list, not the order of intervals within a preset**, which is authored
  data and is stored — see [Amendment, 2026-09-12](#amendment-2026-09-12).
- **A seeded preset is an ordinary row, with no marker** distinguishing it from one the user made.
  (The app seeds two endurance presets, not Tabata — see
  [Amendment, 2026-09-12](#amendment-2026-09-12).) The user may rename or delete it like any
  other, and no "is this the built-in one" branch exists anywhere in the code. The
  counter-argument was accepted with open eyes and is recorded here so it is not rediscovered as
  a surprise: **without a marker there is no way to offer "restore defaults" later without
  guessing which row was the seeded one.** Restoring defaults is not a v1 feature, and a marker
  column is an additive change — cheap to add by migration under the policy above.

**The full column list is specification content and lands with the v1 specification on
[#33](https://github.com/derekwinters/Interval-trainer-android/issues/33)**, together with what is
stored about intervals and how the seed is inserted on first run. A reader looking for the columns
should go there rather than hunting here.

## Alternatives considered

**A typed JSON document in DataStore, or a plain JSON file.** Both were considered while charting
the map and rejected in favour of Room, which was not reopened on
[#32](https://github.com/derekwinters/Interval-trainer-android/issues/32). The shape of the app is
what decides it: v2 adds workout history, which is a second collection joined to presets, and the
question a store has to answer here is not "how do I write this" but "how do I change what I wrote
without losing it". Room answers that with a versioned schema, a migration and a test that runs the
upgrade; a serialised document answers it with hand-written code in the parser and nothing that
proves a row survived.

**An Android library module plus Robolectric.** Rejected on the research. Room's Gradle plugin
copies exported schemas only where *instrumented* tests can see them, never into a unit test's
assets, so the schemas would have to be put where Room's own documentation says not to put them.
Robolectric also downloads a large `android-all` SDK jar from Maven Central at test runtime unless
it is pointed at a local copy, which puts a network dependency inside `./gradlew test` and breaks
the build specification's clean-checkout invariant.

**An Android library module plus instrumented tests.** Rejected because continuous integration here
has no emulator. The tests would run only when someone remembered to run them by hand, which for a
gate that exists to catch data loss is the same as not having one.

**Deferring the upgrade-path harness until the first migration exists.** Considered seriously and
rejected deliberately. At v1 there is one schema version, so an upgrade-path test has no path to
walk and would assert nothing — a real argument for waiting. The owner chose to have the harness
ready instead, so that the first schema change is made against working machinery rather than having
to stand the machinery up while under the pressure of making that change. This is the first
invariant above.

**Forbidding breaking changes outright.** Rejected: it gives no answer to the case that will
actually arise, restructuring the interval fields, where the data can survive but the column shape
cannot. A rule with no answer for the expected case is a rule that gets broken the first time it
matters.

**Allowing breaking changes with a written justification.** Rejected as the right shape for the
wrong question. Prose does not prove a row survived, and review catches what a reviewer thinks to
look for, while the test catches what actually happens to the rows.

## Consequences

- **Room 3 requires KSP, which likely drags a Kotlin upgrade from the pinned 2.0.21** — and, with
  it, possibly AGP and Gradle. That is separate work and should be named as such when the feature
  issues are filed; it is not a side-effect to be absorbed quietly into the first database pull
  request. The research could not establish Room 3.0.x's minimum Kotlin and KSP versions from any
  primary source, so the size of that upgrade is not yet known.
- **The Robolectric plan narrows to the Compose screens.** The pure-JVM core and thin Android shell
  decision pending on [#24](https://github.com/derekwinters/Interval-trainer-android/issues/24)
  names Robolectric for the shell's screens, DAO and migration tests; the database no longer needs
  it, and that ADR should be written to say so.
- **The build specification's clean-checkout invariant is satisfied by this choice rather than
  relaxed.** No emulator, no simulated Android runtime, no network fetch inside `./gradlew test` —
  so [`docs/spec/build.md`](../spec/build.md) needs no amendment for the database, and its
  every-version-pinned invariant applies to the Room, KSP and `androidx.sqlite` coordinates like
  any other.
- **This is the project's first multiplatform module**, in a build that today is a single `:app`
  module. Two targets, a `commonMain` source set, the KSP wiring and the Room Gradle plugin's
  schema directory are all unfamiliar setup, and the first pull request that creates the module
  will be mostly Gradle rather than mostly Kotlin.
- **Adding the v2 `workouts` table is cheap by construction.** A new table is an additive change
  under the policy, and the harness that proves the upgrade already exists. That is why the v1
  workout summary can stay ephemeral — nothing about a workout is written to the database in v1,
  as [ADR 2](0002-the-foreground-service-owns-the-running-workout.md) records, and history is a
  migration away rather than a redesign.
- **Committed schema files become reviewable artefacts that must not ship in the app.** They are
  version-controlled on purpose and excluded from the APK on purpose, and a pull request whose
  schema diff nobody reads has skipped the cheapest schema review available.
