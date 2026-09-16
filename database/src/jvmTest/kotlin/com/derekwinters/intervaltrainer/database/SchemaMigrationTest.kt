package com.derekwinters.intervaltrainer.database

/**
 * The upgrade-path contract-test harness for [IntervalTrainerDatabase]'s schema — `docs/spec/schema.md`
 * §5 (SCHEMA-040–044), restated from `docs/adr/0003-room-with-the-schema-treated-as-an-api.md`'s
 * first invariant: *the harness exists before the first migration, not alongside it.*
 *
 * **What this file asserts today: nothing (SCHEMA-043).** At schema version 1 there is exactly one
 * schema version and no previous one to migrate from, so there is no upgrade path to walk yet and
 * nothing here would be anything but a test of Room itself. This class exists — compiled, in
 * `:database`'s `jvmTest` source set, running under plain `./gradlew test` with no Android runtime
 * (`BUILD-021`, `BUILD-023`) — so that the *first* breaking schema change is made against working
 * machinery instead of having to invent this file under the pressure of making that change.
 *
 * **What the first `@Test` here will look like, once a breaking change (SCHEMA-041) needs one:**
 * Room's contract-test tool for exactly this is `androidx.room.testing.MigrationTestHelper`, from
 * the `androidx.room:room-testing` artifact — pin it at the same `2.7.0` literal as this module's
 * `room-runtime`/`room-compiler` coordinates (`database/build.gradle.kts`). It is published for this
 * module's `jvm()` target (its Gradle module `room-testing` declares `androidTarget()`, `jvm()`,
 * `ios()`, `linux()` and `mac()` source sets), so no Robolectric and no instrumentation is needed —
 * confirmed by reading Room 2.7.0-era source directly (`room/room-testing/src/{common,jvm}Main`,
 * commit `2d71e6c2c489947a51d0835622a9f997f7412eeb` on the `androidx/androidx` GitHub mirror, eight
 * days before the 2.7.0 release), not assumed from memory or from this repository's Room-3-era
 * research note (`docs/research/room-migration-testing.md`, which documents the newer
 * `androidx.room3` line's `room3-testing` and does not by itself establish that the older `2.x`
 * `room-testing` artifact this module actually depends on has the same shape).
 *
 * The JVM actual is a plain constructor, not a factory function, and is a JUnit 4 `TestWatcher` —
 * used as a `@get:Rule`, the same as any other JUnit 4 rule, not as a class-level annotation or a
 * suspend/coroutine API:
 *
 * ```kotlin
 * actual class MigrationTestHelper(
 *     schemaDirectoryPath: java.nio.file.Path,
 *     databasePath: java.nio.file.Path,
 *     driver: androidx.sqlite.SQLiteDriver,
 *     databaseClass: kotlin.reflect.KClass<out androidx.room.RoomDatabase>,
 *     databaseFactory: () -> androidx.room.RoomDatabase = { ... }, // has a default
 *     autoMigrationSpecs: List<androidx.room.migration.AutoMigrationSpec> = emptyList(),
 * ) : org.junit.rules.TestWatcher() {
 *     fun createDatabase(version: Int): androidx.sqlite.SQLiteConnection
 *     fun runMigrationsAndValidate(
 *         version: Int,
 *         migrations: List<androidx.room.migration.Migration> = emptyList(),
 *     ): androidx.sqlite.SQLiteConnection
 * }
 * ```
 *
 * `schemaDirectoryPath` is resolved as `schemaDirectoryPath/<database FQN>/<version>.json` — the
 * same layout `SCHEMA-003` already commits to at
 * `database/schemas/com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase/`, and the
 * same directory this module's `room { schemaDirectory(...) }` block (`database/build.gradle.kts`)
 * already writes to. `databasePath` wants a real file, per the helper's own documentation — an
 * in-memory driver is meaningless here — the same `File.createTempFile(...)` shape
 * [PresetDaoTest] and [RoomPresetStoreTest] already use for their own database files.
 *
 * The test body itself, once there is a version 2 to migrate to, is the shape SCHEMA-041 names:
 *
 * ```kotlin
 * @get:Rule
 * val migrationTestHelper = MigrationTestHelper(
 *     schemaDirectoryPath = Paths.get("schemas"),
 *     databasePath = File.createTempFile("schema-migration-test", ".db").toPath(),
 *     driver = BundledSQLiteDriver(),
 *     databaseClass = IntervalTrainerDatabase::class,
 * )
 *
 * @Test
 * fun `a v1 preset survives migrating to v2`() {
 *     val v1 = migrationTestHelper.createDatabase(1)
 *     // Real rows, inserted with raw SQL — the DAOs only know the *current* schema.
 *     v1.execSQL("INSERT INTO presets (id, name) VALUES ('preset-1', 'Short Example')")
 *     v1.close()
 *
 *     val v2 = migrationTestHelper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
 *     // Assert on the rows that came through — SCHEMA-041 again: prose does not prove a row survived.
 * }
 * ```
 *
 * **What is still missing, and is not this file's job to supply.** `createDatabase(1)` above needs
 * the *committed* v1 schema JSON that `SCHEMA-003` requires — not yet in this repository, because
 * `:database`'s Room dependencies resolve only from Google's Maven repository, which the sandbox
 * that first wrote this module (and the sandbox that wrote this file) cannot reach; tracked as
 * [#100](https://github.com/derekwinters/Interval-trainer-android/issues/100). And a `MIGRATION_1_2`
 * needs an actual, decided v2 schema change, which does not exist yet either. Neither blocks this
 * harness existing (SCHEMA-043's whole point), but both block writing this file's first real
 * `@Test`.
 *
 * **SCHEMA-042.** If the upgrade-path test above cannot be written for a proposed breaking change —
 * the fixture cannot be built, or the migrated rows cannot be asserted on — the change is not made
 * in that shape. This file is the gate a change is checked against, not a review comment appended
 * after the fact.
 */
class SchemaMigrationTest
