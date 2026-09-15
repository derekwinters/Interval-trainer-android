# Specification — Schema (`SCHEMA`)

What is actually stored: the `presets` table and its child `intervals` table, the seeded data a
fresh install starts with, and the contract-test policy that guards every change to any of it.

[ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md) decided that presets are stored
in Room, that the database lives in its own Kotlin Multiplatform module, that migrations are
tested on the JVM with no Android runtime, and that the schema is treated as an API — reviewed as a
diff, changed only under a rule with a test behind it. None of that is reopened here. What that ADR
deferred — the column list, how a preset's intervals are stored and ordered, and the column-level
shape of the contract-test policy — is this page's job.

There is no `workouts` table. History is out of scope for v1
([`docs/spec/timer.md`](timer.md) `TIMER-053`, `TIMER-054`), and the summary that reports a
workout's outcome is ephemeral. This page specifies presets only.

---

## Invariants

Carried forward from [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md), because
this page is the natural home for the behaviour they constrain:

> **Invariant — the upgrade-path harness exists before the first migration, not alongside it.** At
> v1 there is one schema version and no path to walk. The harness is built anyway, so the first
> schema change is made against working machinery rather than machinery stood up under the
> pressure of making it.

> **Invariant — a breaking schema change lands only with a migration and an upgrade-path test that
> proves data survival.** The test creates a database at the previous version with real rows,
> migrates it, and asserts on the rows that came through. Prose does not prove a row survived.

> **Invariant — the database module's migration tests run on the JVM, with no Android runtime.**
> No emulator, no instrumentation, no simulated Android runtime, and no schema file moved into
> assets to make some other runtime see it.

One new invariant, specific to what this page adds:

> **Invariant — an interval's position in its preset is explicit, stored data, and is never
> inferred from anything else.** Interval order is authored, not incidental: the user dragged that
> row there. It is stored in its own column and read back unchanged, unlike the *preset* list's own
> order, which is insertion order and has no column at all — the two orderings are different things
> ([ADR 0003, Amendment 2026-09-12](../adr/0003-room-with-the-schema-treated-as-an-api.md#amendment-2026-09-12)).

---

## 1. The module and the schema files

- **SCHEMA-001** The database lives in a Kotlin Multiplatform Gradle module, `:database`, with an
  `androidTarget()` and a `jvm()` target, per
  [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md). `:app` consumes the Android
  target. *(manual: a build-configuration fact.)*
- **SCHEMA-002** `:database` holds one `@Database` class, `IntervalTrainerDatabase`, at schema
  version `1`, declaring the `presets` and `intervals` entities below.
- **SCHEMA-003** The exported schema JSON is committed at `database/schemas/`, Room's own
  convention — `database/schemas/com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase/1.json`
  for this version. It ships in version control and never in the APK.
  *(manual: a file-location fact; visible in the diff and excluded from `assembleDebug`'s output.)*
- **SCHEMA-004** `:core` defines the preset store as an interface and does not implement it;
  `:database` implements it, and `:core` depends on the interface only, never on `:database`
  itself, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). A fake
  implementation is what `:core`'s own tests substitute.
  *(manual: a module-dependency fact, made a compile error by the module graph.)*

## 2. The `presets` table

- **SCHEMA-010** `presets` has exactly two columns: `id` (`TEXT`, primary key) and `name`
  (`TEXT`, not null). Nothing else is stored about a preset — no created-at timestamp, no seeded
  marker, no position — because nothing else has been decided to be worth the migration it would
  later cost. *(manual: an absence from the stored model, visible in the exported schema.)*
- **SCHEMA-011** `id` is a generated UUID string, assigned at creation, never an auto-incrementing
  integer, per [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md). It is what a
  workout's copied schedule and a preset row have in common with nothing else: neither refers back
  to the other by anything but this value, and the workout does not depend on it continuing to
  resolve (`TIMER-003`).
- **SCHEMA-012** The preset list is read in **insertion order**. No `position` column exists for
  it: the query orders on the table's own row-insertion order (its implicit `rowid`, which a `TEXT`
  primary key does not disable), never on a stored column. Reordering the preset list is out of
  scope for v1; when it arrives, it arrives as an additive migration.
  *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-013** A preset row carries no marker distinguishing a seeded preset from a user's own.
  The user may rename or delete either seeded preset exactly like one they authored, and no
  "is this the built-in one" branch exists anywhere in the code.
  *(manual: an absence, visible in the diff.)*
- **SCHEMA-014** Deleting a preset cascades to every interval row in `intervals` that references it
  (`SCHEMA-021`). Nothing else in the schema references a preset, so nothing else needs a cascade
  rule. *(auto: `PresetDaoTest.kt`.)*

## 3. The `intervals` table

A preset is an ordered list of intervals (`TIMER-001`), not a single row
([ADR 0003, Amendment 2026-09-12](../adr/0003-room-with-the-schema-treated-as-an-api.md#amendment-2026-09-12)).
`intervals` is the child table that stores that list.

- **SCHEMA-020** `intervals` has four columns: `id` (`INTEGER`, primary key, auto-generated),
  `presetId` (`TEXT`, not null, foreign key to `presets.id`), `kind` (`TEXT`, not null), and
  `durationSeconds` (`INTEGER`, not null).
- **SCHEMA-021** `presetId` is declared `ON DELETE CASCADE`, so a deleted preset's intervals are
  deleted with it (`SCHEMA-014`). No orphaned interval row can exist.
- **SCHEMA-022** An interval's own identity, unlike a preset's, is never referenced from outside
  `intervals` — nothing stores an interval's `id` anywhere else — so it is Room's default
  auto-incrementing `Long`, not a UUID. The UUID reasoning in `SCHEMA-011` is about surviving
  export, sharing and sync of a *preset*; no such case exists for one row of one preset's list.
  This is this page's own call, on a question `SCHEMA-011`'s source left open only for presets:
  the same reasoning point applies with the opposite answer once nothing outside the row needs
  the identifier.
- **SCHEMA-023** `kind` is stored as `TEXT`, one of `warm_up`, `work`, `recovery`, `cool_down` —
  the glossary's four kinds ([`CONTEXT.md`](../../CONTEXT.md)), spelled as a stable string rather
  than an integer ordinal. An ordinal is a migration hazard the moment a kind is inserted, removed
  or reordered in code; a string survives that with no schema change at all.
- **SCHEMA-024** `durationSeconds` stores a whole number of seconds. Minutes-and-seconds display
  and entry is a presentation concern (`formatSeconds`, `BUILD-030`–`033`); nothing about it implies
  a minutes column.
- **SCHEMA-025** `position` is a fifth column: `INTEGER`, not null, the interval's explicit place
  in its preset's list, per this page's own invariant above. The schedule is read ordered by
  `(presetId, position)` ascending.
- **SCHEMA-026** No `CHECK` constraint enforces the five-second minimum interval (`TIMER-070`); the
  editor is what refuses a shorter duration (`TIMER-072`). The schema stores whatever it is given
  and trusts the one writer that produces it. *(manual: an absence of a database-level constraint;
  the editor's refusal is `TIMER-072`'s test.)*
- **SCHEMA-027** An index on `(presetId, position)` supports reading a preset's schedule in order
  without a table scan. *(manual: a build-configuration fact, verified by the exported schema.)*

## 4. Seeding

Two endurance presets are seeded on first creation of the database — not one, and not the
high-intensity protocol charted and later rejected for this app
([#30](https://github.com/derekwinters/Interval-trainer-android/issues/30), corrected in
[ADR 0003's amendment](../adr/0003-room-with-the-schema-treated-as-an-api.md#amendment-2026-09-12)).

- **SCHEMA-030** Seeding runs once, in `RoomDatabase.Callback.onCreate()`, inserting both presets
  and every one of their interval rows in a single transaction. It does not run on every app
  launch and does not run again after a migration, only on a database created from nothing.
- **SCHEMA-031** The **short** preset is warm-up 3:00, then three rounds of work 1:00 and recovery
  2:00, then cool-down 3:00 — eight interval rows, total 15:00.
- **SCHEMA-032** The **full** preset is warm-up 5:00, then eight rounds of work 1:00 and recovery
  2:00, then cool-down 5:00 — eighteen interval rows, total 34:00.
- **SCHEMA-033** Both presets end their last round's recovery before cool-down begins; neither
  preset omits a trailing recovery. *(auto: `PresetDaoTest.kt`, asserting the seeded row counts,
  kinds, durations and totals in `SCHEMA-031`–`032`.)*
- **SCHEMA-034** The seeded presets' `name` values are **"Short Example"** for the short preset
  (`SCHEMA-031`) and **"Long Example"** for the full preset (`SCHEMA-032`), decided by the
  repository owner, asked directly. They are ordinary `presets.name` values with no special status
  (`SCHEMA-013`).

| | Short | Full |
|---|---|---|
| Warm-up | 3:00 | 5:00 |
| Work | 1:00 (×3) | 1:00 (×8) |
| Recovery | 2:00 (×3) | 2:00 (×8) |
| Cool-down | 3:00 | 5:00 |
| **Total** | **15:00** | **34:00** |

## 5. The contract-test policy

Restated here as requirements, from [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md).

- **SCHEMA-040** An **additive** schema change — a nullable column, or a column with a default —
  needs nothing beyond the ordinary exported-schema diff (`SCHEMA-003`) and Room's own three checks
  (the compiler's `SchemaDiffer`, `runMigrationsAndValidate`, the runtime identity hash).
- **SCHEMA-041** A **breaking** schema change — dropping a column, renaming one, narrowing a type,
  adding a non-null column with no default — is permitted **only** with a `Migration` and an
  upgrade-path test: `createDatabase` at the previous version from the committed schema, real rows
  inserted through raw SQL, `runMigrationsAndValidate` to the new version, then an assertion that
  the rows survived. *(auto: the test the change itself must add — `SchemaMigrationTest.kt`.)*
- **SCHEMA-042** If the upgrade-path test in `SCHEMA-041` cannot be written for a proposed change,
  the change is not made in that shape. The test is the gate, not a review comment.
  *(manual: a policy statement; nothing automated enforces the refusal itself.)*
- **SCHEMA-043** At v1 there is exactly one schema version and no migration to test, so
  `SchemaMigrationTest.kt` exists with nothing to assert yet — the harness invariant above, honoured
  before it has a job. It is named here, in the future tense, the same way `TimerStateTest.kt` and
  `CueSelectionTest.kt` are named ahead of their tests.
- **SCHEMA-044** A future `workouts` table, if history arrives in v2, is an **additive** change
  under this policy — a new table, not a change to `presets` or `intervals` — and needs nothing
  beyond `SCHEMA-040`.

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| The module and the schema files | SCHEMA-001–004 | *(manual)* |
| The `presets` table | SCHEMA-010–014 | `PresetDaoTest.kt` (SCHEMA-012, 014); *(manual)* SCHEMA-010–011, 013 |
| The `intervals` table | SCHEMA-020–027 | *(manual)*, all — schema shape and an absent constraint |
| Seeding | SCHEMA-030–034 | `PresetDaoTest.kt` (SCHEMA-033); *(manual)* SCHEMA-030–032, 034 |
| The contract-test policy | SCHEMA-040–044 | `SchemaMigrationTest.kt` (SCHEMA-041, 043); *(manual)* SCHEMA-042, 044 |

**27 requirements, 5 `auto` and 22 `manual`.**

**The `auto` tests do not exist yet.** There is no `:database` module in the build today —
`settings.gradle.kts` includes exactly one module, `:app` (`BUILD-002`). `PresetDaoTest.kt` and
`SchemaMigrationTest.kt` are named here so the tests that will assert `SCHEMA-012`, `SCHEMA-014`,
`SCHEMA-033`, `SCHEMA-041` and `SCHEMA-043` have one home each, at
`database/src/jvmTest/kotlin/com/derekwinters/intervaltrainer/database/`, and they are named in the
future tense on purpose, the same honesty [`docs/spec/timer.md`](timer.md) and
[`docs/spec/cues.md`](cues.md) state about their own `:core` tests.

**Why the proportion is mostly `manual`.** Most of this page is the shape of a table — a column, a
type, a foreign key, an index — which is a configuration fact the exported schema and Room's own
diff checks make visible, not something a JVM assertion adds value by re-stating. What *is*
genuinely behaviour rather than shape — insertion order surviving a read, a cascade delete actually
removing the child rows, the seed producing the right rows, and a migration proving data
survival — are exactly the five marked `auto`, and they are the ones this page's tests exist to
catch a regression in.
