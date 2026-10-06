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
> row there. It is stored in its own column and read back unchanged. The *preset* list's own order
> is a different thing, stored in a different column on a different table (`SCHEMA-012`)
> ([ADR 0003, Amendment 2026-09-12](../adr/0003-room-with-the-schema-treated-as-an-api.md#amendment-2026-09-12)).

Two more, added with preset-list reordering and duplication
([#163](https://github.com/derekwinters/Interval-trainer-android/issues/163)):

> **Invariant — the preset list's order is read from `presets.position`, never from `rowid`.** Since
> schema version 2 the stored column is the order. Ordering on `rowid`, or on anything else that
> happens to match insertion order, would still pass a test that only inserts and reads, and would
> silently undo every reorder the user made.

> **Invariant — reordering and duplicating move rows, they never rewrite them.** A reorder changes
> `presets.position` and nothing else: no name, no interval row. A duplicate inserts a new `presets`
> row and new `intervals` rows of its own; it shares no row with the preset it was copied from, so
> editing or deleting either one never touches the other.

---

## 1. The module and the schema files

- **SCHEMA-001** The database lives in a Kotlin Multiplatform Gradle module, `:database`, with an
  `androidTarget()` and a `jvm()` target, per
  [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md). `:app` consumes the Android
  target. *(manual: a build-configuration fact.)*
- **SCHEMA-002** `:database` holds one `@Database` class, `IntervalTrainerDatabase`, at schema
  version `2`, declaring the `presets` and `intervals` entities below. Version `1` had no
  `presets.position` column; `SCHEMA-045` is the migration between the two.
- **SCHEMA-003** The exported schema JSON is committed at `database/schemas/`, Room's own
  convention — `database/schemas/com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase/2.json`
  for this version, beside `1.json` for the version before it, which stays committed because the
  upgrade-path test (`SCHEMA-041`) builds its starting database from it. It ships in version
  control and never in the APK.
  *(manual: a file-location fact; visible in the diff and excluded from `assembleDebug`'s output.)*
- **SCHEMA-004** `:core` defines the preset store as an interface and does not implement it;
  `:database` implements it, and `:core` depends on the interface only, never on `:database`
  itself, per [ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md). A fake
  implementation is what `:core`'s own tests substitute. The preset store's operations —
  `presets`, `preset`, `save`, `delete`, and, since #163, `reorder` and `saveAfter` — are `suspend`
  functions, and so is every function of
  `:database`'s DAO beneath them: Room's Kotlin Multiplatform compiler accepts only `suspend` DAO
  functions in a source set targeting a non-Android platform, and `:database`'s `jvm()` target
  (`SCHEMA-001`) is one. Being `suspend` does not move a call off the caller's thread; a caller
  on the main thread still chooses the dispatcher it reads on.
  *(manual: a module-dependency fact, made a compile error by the module graph; the `suspend`
  signatures are likewise enforced by the compiler.)*

  > **Invariant — the preset store's operations are suspend functions; no main source set bridges
  > them with `runBlocking`.**

## 2. The `presets` table

- **SCHEMA-010** `presets` has exactly three columns: `id` (`TEXT`, primary key), `name`
  (`TEXT`, not null), and `position` (`INTEGER`, not null, default `0`), the preset's place in the
  home screen's list (`SCHEMA-012`). Nothing else is stored about a preset — no created-at
  timestamp, no seeded marker — because nothing else has been decided to be worth the migration it
  would later cost. The `0` default exists only because SQLite cannot add a not-null column to a
  table that already has rows without one (`SCHEMA-045`); it is declared on the entity as well, so
  a database created fresh at version 2 and one migrated from version 1 have the same column.
  Nothing relies on it: every write states `position` explicitly (`SCHEMA-015`–`017`).
  *(manual: the shape of the stored model, visible in the exported schema.)*
- **SCHEMA-011** `id` is a generated UUID string, assigned at creation, never an auto-incrementing
  integer, per [ADR 0003](../adr/0003-room-with-the-schema-treated-as-an-api.md). It is what a
  workout's copied schedule and a preset row have in common with nothing else: neither refers back
  to the other by anything but this value, and the workout does not depend on it continuing to
  resolve (`TIMER-003`).
- **SCHEMA-012** The preset list is read in **stored order**: ascending `presets.position`. Until
  #163 it was read in insertion order, on the table's implicit `rowid`, with no column for it;
  `SCHEMA-045` turned that order into the stored one, so nobody's list changed order on upgrade.
  *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-013** A preset row carries no marker distinguishing a seeded preset from a user's own.
  The user may rename or delete either seeded preset exactly like one they authored, and no
  "is this the built-in one" branch exists anywhere in the code.
  *(manual: an absence, visible in the diff.)*
- **SCHEMA-014** Deleting a preset cascades to every interval row in `intervals` that references it
  (`SCHEMA-021`). Nothing else in the schema references a preset, so nothing else needs a cascade
  rule. *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-015** Reordering the list (`docs/spec/screens.md` `SCREEN-009`) writes the new order as
  `position` values `0, 1, 2, …` over the ids it is given, in that order, in one transaction, and
  touches no other column and no `intervals` row. The list read afterwards (`SCHEMA-012`) is that
  order, and stays that order across closing and reopening the database. The store operation is
  `PresetStore.reorder(orderedIds)`, given every preset's id. *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-016** Saving a preset that does not exist yet appends it to the end of the list: its
  `position` is one more than the largest stored, or `0` in an empty table. Saving a preset that
  already exists keeps the `position` it has — editing a preset never moves it.
  *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-017** `PresetStore.saveAfter(preset, afterId)` saves a new preset directly after the
  preset `afterId`: every preset after `afterId` moves down one place, and the new one takes the
  place they vacated, in one transaction. If `afterId` no longer exists, the new preset is appended
  as `SCHEMA-016` says. This is what a duplicate (`docs/spec/screens.md` `SCREEN-019b`) is saved
  with; the copy is a new `presets` row with its own id and new `intervals` rows of its own, so
  editing the copy leaves the original unchanged, and deleting either leaves the other intact
  (this page's third invariant). *(auto: `PresetDaoTest.kt`, `RoomPresetStoreTest.kt`.)*

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
  and every one of their interval rows in a single transaction, "Short Example" at `position` `0`
  and "Long Example" at `position` `1`. It does not run on every app
  launch and does not run again after a migration, only on a database created from nothing.
  *(auto: `PresetDaoTest.kt` reopens an already-seeded database file and asserts the preset count
  did not double — the closest a v1 schema with no migration yet can come to proving "not on a
  migration". The single-transaction insert itself is manual: `SeedDataCallback.kt` runs inside the
  same connection and transaction Room's own `onCreate` already holds open, rather than opening one
  of its own.)*
- **SCHEMA-031** The **short** preset is warm-up 3:00, then three rounds of work 1:00 and recovery
  2:00, then cool-down 3:00 — eight interval rows, total 15:00. *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-032** The **full** preset is warm-up 5:00, then eight rounds of work 1:00 and recovery
  2:00, then cool-down 5:00 — eighteen interval rows, total 34:00. *(auto: `PresetDaoTest.kt`.)*
- **SCHEMA-033** Both presets end their last round's recovery before cool-down begins; neither
  preset omits a trailing recovery. *(auto: `PresetDaoTest.kt`, asserting the seeded row counts,
  kinds, durations and totals in `SCHEMA-031`–`032`.)*
- **SCHEMA-034** The seeded presets' `name` values are **"Short Example"** for the short preset
  (`SCHEMA-031`) and **"Long Example"** for the full preset (`SCHEMA-032`), decided by the
  repository owner, asked directly. They are ordinary `presets.name` values with no special status
  (`SCHEMA-013`). *(auto: `PresetDaoTest.kt`.)*

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
- **SCHEMA-043** `SchemaMigrationTest.kt` ([#75](https://github.com/derekwinters/Interval-trainer-android/issues/75))
  exists in `:database`'s `jvmTest` source set. It was built at v1 with nothing to assert, the
  harness invariant above honoured before it had a job; since version 2 it holds the upgrade-path
  test for `SCHEMA-045`, using `androidx.room:room-testing`'s `MigrationTestHelper` on the JVM.
- **SCHEMA-045** `MIGRATION_1_2` takes version 1 to version 2: it adds `presets.position`
  (`INTEGER NOT NULL DEFAULT 0`) and backfills it from version 1's insertion order — each row's
  `position` is the number of rows with a smaller `rowid` — so the list reads in the same order after
  the upgrade as before it, numbered `0` to `n − 1`. No `intervals` row and no `name` is touched.
  The on-device builder registers it (`AppDatabase.open`). Adding a not-null column is a breaking
  change under `SCHEMA-041`, so it ships with the upgrade-path test that rule asks for: a version 1
  database built from the committed `1.json`, presets and intervals inserted through raw SQL,
  migrated and validated against `2.json`, and every row asserted on afterwards — names, interval
  rows and the backfilled order. *(auto: `SchemaMigrationTest.kt`.)*
- **SCHEMA-044** A future `workouts` table, if history arrives in v2, is an **additive** change
  under this policy — a new table, not a change to `presets` or `intervals` — and needs nothing
  beyond `SCHEMA-040`.

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| The module and the schema files | SCHEMA-001–004 | *(manual)* |
| The `presets` table | SCHEMA-010–017 | `PresetDaoTest.kt` (SCHEMA-012, 014, 015, 016, 017); `RoomPresetStoreTest.kt` (SCHEMA-017's deep copy through the store); *(manual)* SCHEMA-010–011, 013 |
| The `intervals` table | SCHEMA-020–027 | *(manual)*, all — schema shape and an absent constraint |
| Seeding | SCHEMA-030–034 | `PresetDaoTest.kt` (SCHEMA-030's once-only guarantee, SCHEMA-031–034); *(manual)* SCHEMA-030's single-transaction insert |
| The contract-test policy | SCHEMA-040–045 | `SchemaMigrationTest.kt` (SCHEMA-041, 043, 045); *(manual)* SCHEMA-040, 042, 044 |

**31 requirements, 13 `auto` and 18 `manual`.**

**`:database` and `PresetDaoTest.kt` now exist**, at
`database/src/jvmTest/kotlin/com/derekwinters/intervaltrainer/database/PresetDaoTest.kt`
(`BUILD-002`), asserting `SCHEMA-012` and `SCHEMA-014` as this table said they would, and, since
[#74](https://github.com/derekwinters/Interval-trainer-android/issues/74), the seeded row counts,
kinds, durations and totals in `SCHEMA-031`–`032`, the seeded names in `SCHEMA-034`, and that
reopening an already-seeded database file does not seed its presets again — the closest a v1
schema, with no migration to drive it through yet, can come to testing `SCHEMA-030`'s "not on a
later migration". `SchemaMigrationTest.kt` now exists too, at
`database/src/jvmTest/kotlin/com/derekwinters/intervaltrainer/database/SchemaMigrationTest.kt`
([#75](https://github.com/derekwinters/Interval-trainer-android/issues/75)), satisfying
`SCHEMA-043`. It was a documented, empty harness while there was one schema version; since
[#163](https://github.com/derekwinters/Interval-trainer-android/issues/163) added
`presets.position` and schema version 2, it holds the first real upgrade-path test (`SCHEMA-041`,
`SCHEMA-045`), built on the committed `1.json` and validated against the committed `2.json`. The
same issue extended `PresetDaoTest.kt` with stored order, reordering, appending and inserting after
a preset (`SCHEMA-012`, `SCHEMA-015`–`017`), and `RoomPresetStoreTest.kt` with a duplicate being a
deep copy through the store (`SCHEMA-017`).

**Why the proportion is mostly `manual`.** Most of this page is the shape of a table — a column, a
type, a foreign key, an index — which is a configuration fact the exported schema and Room's own
diff checks make visible, not something a JVM assertion adds value by re-stating. What *is*
genuinely behaviour rather than shape — the stored order surviving a read and a reorder, a new
preset landing at the end and a duplicate directly after its original, a cascade delete actually
removing the child rows, the seed producing the right rows and not producing them twice, and a
migration proving data survival — are exactly the thirteen marked `auto`, and they are the ones this
page's tests exist to catch a regression in.
