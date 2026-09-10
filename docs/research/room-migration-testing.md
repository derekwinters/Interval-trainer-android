# Research — Testing Room migrations and schema contracts on the JVM

**Question (issue #27).** How are Room migrations and schema contracts tested on the JVM, without an
emulator, on Room's current stable release?

**Date checked: 2026-09-10.** Every version number below was read on that date and will drift.

**Sources.** Primary only: `developer.android.com`, the AndroidX source on `github.com/androidx/androidx`
(branch `androidx-main`), Robolectric's own source on `github.com/robolectric/robolectric`, Maven Central
metadata, and `kotlinlang.org`. Robolectric's documentation site (`robolectric.org`) is unreachable from
this environment, so every Robolectric claim here is cited to its source code or to Google's own page
about Robolectric on `developer.android.com`. No blogs, no Stack Overflow.

This is a research note, not a decision. Where it names a trade-off it states the options; it does not
pick one.

---

## Answer

Room's current stable release is **Room 3.0.3** (2026-09-09) under the new Maven group `androidx.room3`
([release notes](https://developer.android.com/jetpack/androidx/releases/room3)). Room 3 is Kotlin
Multiplatform, KSP-only, coroutine-only, and built on `androidx.sqlite`'s driver API rather than the
Android framework's SQLite. The old `androidx.room` 2.x line is still published — 2.8.5, same day — but
Google's own announcement puts 2.x in maintenance mode
([Room 3.0 — Modernizing the Room](https://developer.android.com/blog/posts/modernizing-the-room)).
That change is what makes this question have a good answer: because Room 3 runs on the JVM as a first-class
target, migration tests can run as ordinary JVM unit tests with no Android runtime at all — not an
emulator, and not Robolectric either.

Three findings decide the shape of the work:

1. **`room3-testing`'s `MigrationTestHelper` is an `expect` class with a different constructor per
   platform.** The JVM implementation takes a **schema directory path on disk**, a database file path and
   an `SQLiteDriver`
   ([`MigrationTestHelper.jvm.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/jvmMain/kotlin/androidx/room3/testing/MigrationTestHelper.jvm.kt)).
   The **Android** implementation takes an `android.app.Instrumentation` and loads schemas from **assets**
   ([`MigrationTestHelper.android.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/androidMain/kotlin/androidx/room3/testing/MigrationTestHelper.android.kt)).
   Which one a test gets is decided by the Gradle target it compiles against, not by a flag.
2. **So the emulator-free path with the fewest moving parts is a Kotlin Multiplatform module** (say
   `:data`) with an `androidTarget()` and a `jvm()` target, holding the `@Database`, with migration tests in
   `jvmTest` using `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled` (real SQLite compiled from
   source, no Android). That is exactly how AndroidX tests Room itself:
   [`room3/integration-tests/multiplatformtestapp/src/jvmTest/.../MigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/jvmTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/MigrationTest.kt)
   constructs the helper with `schemaDirectoryPath = "schemas-ksp"`, a temp file database and
   `BundledSQLiteDriver()`.
3. **Robolectric is possible but is the awkward route.** Under Robolectric the Android
   `MigrationTestHelper` can be built — Robolectric registers an `Instrumentation` with AndroidX Test, so
   `InstrumentationRegistry.getInstrumentation()` returns one
   ([`RoboMonitoringInstrumentation.java`](https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/android/internal/RoboMonitoringInstrumentation.java))
   — but the helper then looks for `<schemaFolder>/<version>.json` **in assets**, and the Room Gradle plugin
   only copies exported schemas into **androidTest** assets, never into a unit test's
   ([`AndroidPluginIntegration.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-gradle-plugin/src/main/java/androidx/room3/gradle/integration/AndroidPluginIntegration.kt)).
   Making Robolectric see them means putting the schema directory into assets the unit test can read, which
   is the thing Room's own `@Database` KDoc warns against — "commit the schema files into your version
   control system (but don't ship them with your app!)"
   ([`Database.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-common/src/commonMain/kotlin/androidx/room3/Database.kt)).
   Robolectric also **downloads an `android-all` SDK jar from Maven Central at test runtime** unless it is
   told otherwise (see §6) — a network dependency inside `./gradlew test`.

What the test itself looks like, on either route, is the same shape: `createDatabase(N)` builds a database
from the **committed schema JSON for version N**, you insert real rows through raw SQL,
`runMigrationsAndValidate(M, migrations)` runs Room's own migration selection and then **validates the
resulting schema against the exported schema for M**, and you assert on the rows that came through. Auto
migrations need no extra wiring — the helper is given the database class and picks them up itself
([`MigrationTestHelper.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt)).
There is **no first-party linter for schema JSON diffs**; what checks a schema change is Room itself, in
three places — the compiler's `SchemaDiffer` when generating an auto migration, `runMigrationsAndValidate`
in tests, and the identity-hash check at runtime (§2).

For this repository specifically, two frictions are worth naming before anyone starts:

- The build spec's invariant "no test dependency that needs a device or a **simulated Android runtime**"
  ([`docs/spec/build.md`](../spec/build.md)) rules Robolectric out as written. The KMP/JVM route does not
  touch that invariant; the Robolectric route requires amending it. Either way it is a specification change
  to be decided, not assumed.
- The repo currently pins Kotlin 2.0.21, AGP 8.7.3 and Gradle 8.11.1. Room 3 requires KSP, and KSP's
  version must match the Kotlin version in use, so adopting Room 3 probably drags a Kotlin (and likely AGP
  and Gradle) upgrade with it. Nothing on `developer.android.com` states Room 3.0.x's minimum Kotlin/KSP
  version — see Open questions.

---

## 1. `exportSchema`, where the JSON goes, and committing it

**`exportSchema` defaults to `true`.** From the `@Database` annotation source:

```kotlin
val exportSchema: Boolean = true,
```

and its KDoc: *"Value of `exportSchema` is `true` by default, but you can disable it for databases when
you don't want to keep history of versions (like an in-memory only database)"*, together with *"If you do
export schemas then you should commit the schema files into your version control system (but don't ship
them with your app!)"* —
[`room3-common/.../Database.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-common/src/commonMain/kotlin/androidx/room3/Database.kt).

**The documented way to set the location is the Room Gradle plugin.** From
[Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions):

```kotlin
plugins {
  id("androidx.room3")
}

room3 {
  schemaDirectory("$projectDir/schemas")
}
```

The same page documents per-variant directories (`schemaDirectory("demoDebug", …)`) and the fallback for
builds not using the plugin: the `room.schemaLocation` KSP argument, passed through a
`CommandLineArgumentProvider` so Gradle tracks it as an input.

**Layout on disk.** Room writes one JSON file per version under a directory named for the database class's
fully-qualified name — the JVM helper reconstructs the path as
`schemaDirectoryPath/databaseFQN/version.json`
([`MigrationTestHelper.jvm.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/jvmMain/kotlin/androidx/room3/testing/MigrationTestHelper.jvm.kt)),
and the Android helper opens `"$assetsFolder/$version.json"` from assets
([`MigrationTestHelper.android.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/androidMain/kotlin/androidx/room3/testing/MigrationTestHelper.android.kt)).

**Committing them is not optional if you use auto migrations.** The migration guide states: *"Automated
Room migrations rely on the generated database schema for both the lower and the higher versions of the
database. If `exportSchema` is set to `false`, or if you haven't yet compiled the database with the higher
version number, then automated migrations fail."*
([source](https://developer.android.com/training/data-storage/room/migrating-db-versions)) — and the same
page says exported schemas *"must be stored in your version control system"* so lower versions can be
recreated for testing.

## 2. What a schema diff looks like, and what can lint it

**The file.** A schema file is a `SchemaBundle`: a `formatVersion` plus a `DatabaseBundle`, serialised as
pretty-printed JSON (2-space indent) by kotlinx.serialization; the current format constant is
`SCHEMA_LATEST_FORMAT_VERSION = 1`
([`SchemaBundle.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-migration/src/commonMain/kotlin/androidx/room3/migration/bundle/SchemaBundle.kt)).
The database bundle carries the version, an **identity hash**, the entities (each with its `CREATE TABLE`
SQL, fields, primary key, indices and foreign keys), views and setup queries. Because it is pretty-printed
and generated deterministically, a diff between `3.json` and `4.json` reads as a normal reviewable text
diff: a changed `version`, a changed `identityHash`, and added/removed field objects with the entity's
`createSql` string changing alongside them. Reviewing that diff is the cheapest schema review available —
it is the schema change, stated in full, in the pull request.

**What checks it.** There is no standalone "schema linter" published by AndroidX. Three mechanisms owned by
Room do the checking instead:

1. **Compile time —** `SchemaDiffer` diffs the two exported bundles to generate an `@AutoMigration`. It
   detects added/deleted/renamed tables and columns, "complex changed tables" (primary key, foreign key,
   index or FTS option changes) and view changes, and it **fails the build** rather than guessing when the
   change is ambiguous: a column or table that disappeared without a `@DeleteColumn`/`@RenameColumn`/
   `@DeleteTable`/`@RenameTable`, conflicting rename annotations, or a new NOT NULL column with no default
   ([`SchemaDiffer.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-compiler/src/main/kotlin/androidx/room3/util/SchemaDiffer.kt)).
2. **Test time —** `runMigrationsAndValidate` compares the database it just migrated against the exported
   schema for the target version and throws if they differ
   ([`MigrationTestHelper.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt)).
3. **Run time —** Room stores the identity hash in `room_master_table` and compares it on open, with the
   familiar *"Room cannot verify the data integrity. Looks like you've changed schema but forgot to update
   the version number"*, and *"A migration from $oldVersion to $newVersion was required but not found…"*
   when no path exists
   ([`RoomConnectionManager.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt)).

The practical consequence: a CI rule of the form *"if any file under `schemas/` changed in this pull
request, a migration and a migration test must have changed too"* is a repo-local convention to write, not
a tool to install. Nothing in Room ships it.

## 3. `MigrationTestHelper`: platforms, and whether it runs under Robolectric

**The API (common).** `MigrationTestHelper` is an `expect class` in `commonMain` exposing two suspend
functions:

```kotlin
public suspend fun createDatabase(version: Int): SQLiteConnection

public suspend fun runMigrationsAndValidate(
    version: Int,
    migrations: List<Migration> = emptyList(),
): SQLiteConnection
```

`createDatabase` builds the database at that schema version from the exported JSON;
`runMigrationsAndValidate` runs migrations using Room's own migration-selection algorithm, **including auto
migrations automatically when the database class declares them**, then validates the resulting schema
([`MigrationTestHelper.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt)).

**Platform actuals.** `room3-testing` publishes `androidMain`, `jvmMain`, `nativeMain` and `webMain`
implementations
([source tree](https://github.com/androidx/androidx/tree/androidx-main/room3/room3-testing/src);
[`build.gradle`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/build.gradle)
declares Android, JVM, native (iOS/macOS/Linux/tvOS/watchOS) and JS/Wasm targets, with JUnit as an API
dependency on both `androidMain` and `jvmMain`).

- **JVM actual** — `MigrationTestHelper(schemaDirectoryPath: Path, databasePath: Path, driver: SQLiteDriver,
  databaseClass: KClass<out RoomDatabase>, databaseFactory, autoMigrationSpecs) : TestWatcher()`. Schemas are
  read from the file system; the KDoc requires *"a driver that opens connection to a file database"* — an
  in-memory driver would be meaningless. Being a `TestWatcher` it works as a plain JUnit 4 `@Rule`
  ([`MigrationTestHelper.jvm.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/jvmMain/kotlin/androidx/room3/testing/MigrationTestHelper.jvm.kt)).
- **Android actual** — `MigrationTestHelper(instrumentation: Instrumentation, file: File, driver:
  SQLiteDriver, databaseClass, databaseFactory, autoMigrationSpecs)`. It opens
  `instrumentation.context.assets` for `"$assetsFolder/$version.json"`, falling back to
  `instrumentation.targetContext.assets`, and errors with *"Cannot find the schema file in the assets
  folder…"* if neither has it. Also a `TestWatcher`, with `closeWhenFinished(...)`
  ([`MigrationTestHelper.android.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/androidMain/kotlin/androidx/room3/testing/MigrationTestHelper.android.kt)).

**There is no schema-directory constructor on Android.** An Android-target test — which is what a
Robolectric unit test in an `com.android.application`/`com.android.library` module is — must go through
assets.

**Does it run under Robolectric?** Nothing forbids it, and the pieces exist:

- Robolectric registers its own `Instrumentation` with AndroidX Test in `onCreate`
  (`InstrumentationRegistry.registerInstance(this, arguments)`), so `InstrumentationRegistry
  .getInstrumentation()` returns a working instance; both `getContext()` and `getTargetContext()` return
  `RuntimeEnvironment.getApplication()`
  ([`RoboMonitoringInstrumentation.java`](https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/android/internal/RoboMonitoringInstrumentation.java)).
  Google's own page confirms AndroidX Test APIs and `@RunWith(AndroidJUnit4::class)` tests run under
  Robolectric from the `test` source set
  ([Robolectric — Android Developers](https://developer.android.com/training/testing/local-tests/robolectric)).
- The gap is assets. Because both contexts are the application context, the helper reads the **app's**
  merged assets. The Room Gradle plugin only wires schemas into androidTest assets: its task is named
  `copyRoomSchemasToAndroidTestAssets${variantName}` and is registered *"to be used as assets inputs of an
  Android Test app, enabling MigrationTestHelper to automatically pick them up"*; for unit tests it only
  applies the KSP argument provider
  ([`AndroidPluginIntegration.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-gradle-plugin/src/main/java/androidx/room3/gradle/integration/AndroidPluginIntegration.kt);
  the auto-copy first appeared in Room 2.7.0-alpha07: *"The Room Gradle Plugin will now automatically add
  the exported schemas into the Android Instrumentation Test resource sources so they can be used by the
  `MigrationTestHelper`"*, [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)).
  So a Robolectric run needs its own wiring to put `schemas/` where the unit test's Android assets are, plus
  `testOptions.unitTests.isIncludeAndroidResources = true`
  ([Robolectric — Android Developers](https://developer.android.com/training/testing/local-tests/robolectric)).
  Exactly which source set AGP merges into a unit test's assets is not settled by any primary source I could
  reach — see Open questions.
- AndroidX itself does **not** test Room under Robolectric: searching the `androidx-main` tree for
  `RobolectricTestRunner` under `room3/` returns nothing; Room's Android migration tests live in
  `androidTest`
  ([`room3/integration-tests/kotlintestapp/src/androidTest/.../MigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/kotlintestapp/src/androidTest/java/androidx/room3/integration/kotlintestapp/migration/MigrationTest.kt)),
  and its emulator-free migration tests are the KMP JVM/native ones. Room has fixed Robolectric-specific
  breakages before (2.5.2: *"Fix an issue that causes Room to throw an error when being used in a
  Robolectric test"*; 2.6.0-alpha02 the same issue after the Java→Kotlin conversion —
  [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)), which shows the
  combination is used and supported in practice, but it is not a configuration Room's own CI exercises.

**Version history worth knowing (the "2.6+/2.7+ KMP era").** Room 2.6.0 introduced the Room Gradle plugin
with `schemaDirectory`; 2.7.0-alpha07 added the automatic copy of schemas into instrumentation-test assets;
Room 2.7.0 (2025-04-09) brought full KMP with Android, JVM/desktop, iOS, macOS and Linux targets, later
watchOS/tvOS in 2.8.0
([Room release notes](https://developer.android.com/jetpack/androidx/releases/room)). Room 3.0.0
(2026-07-01) completes that move: new group `androidx.room3`, new package `androidx.room3.*`, KSP-only,
Kotlin-only code generation, suspend-only DAOs, and `androidx.sqlite` drivers instead of the SupportSQLite
APIs ([Room 3.0 release notes](https://developer.android.com/jetpack/androidx/releases/room3);
[Modernizing the Room](https://developer.android.com/blog/posts/modernizing-the-room)). The 2.x tree is no
longer present in `androidx-main`; only `room3/` is.

## 4. Testing the actual upgrade path, and a fixture per released version

The pattern, taken from AndroidX's own multiplatform migration tests
([`BaseMigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/commonTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/BaseMigrationTest.kt),
run on the JVM by
[`jvmTest/.../MigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/jvmTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/MigrationTest.kt)):

```kotlin
// 1. Create the database at the OLD version, from the committed schema JSON.
val connection = migrationTestHelper.createDatabase(1)

// 2. Put real rows in it, with raw SQL — not with DAOs, which only know the current schema.
connection.prepare("INSERT INTO MigrationEntity (pk) VALUES (?)").use {
    it.bindLong(1, 1)
    assertThat(it.step()).isFalse() // SQLITE_DONE
}
connection.close()

// 3. Migrate, and let Room validate the resulting schema against the exported schema for v2.
val connectionV2 = migrationTestHelper.runMigrationsAndValidate(2, listOf(migration))

// 4. Assert the data survived.
connectionV2.prepare("SELECT count(*) FROM MigrationEntity").use {
    assertThat(it.step()).isTrue() // SQLITE_ROW
    assertThat(it.getInt(0)).isEqualTo(1)
}
```

The JVM helper is constructed as a `@Rule` with the schema directory, a temp database file and
`BundledSQLiteDriver()` — from the AndroidX JVM test: `schemaDirectoryPath` pointing at `"schemas-ksp"`,
`databasePath` a `createTempFile("test.db")`, `driver = BundledSQLiteDriver()`, `databaseClass =
MigrationDatabase::class`. Room's Android documentation shows the same two-step shape with the Android
constructor and `AndroidSQLiteDriver()`
([Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)).

**A fixture per released schema version comes almost for free**, and this is the important structural point:
the committed `schemas/<db-fqn>/N.json` files *are* the fixtures. `createDatabase(N)` builds a real
database at version N from the JSON — you never need to check in a binary `.db` file per version, and you
never need an old build of the app to produce one. What you add per version is the *data*: a small,
deliberately chosen set of rows inserted with raw SQL at version N, ideally one function per version
(`seedV1(connection)`, `seedV2(connection)`, …) so that a version's fixture is written once and reused by
every test that starts there.

Two test shapes are worth having, and Room's documentation asks for both:

- **Each hop:** version N → N+1, with data, for every N. This is what localises a break.
- **The whole path:** version 1 → current, in one test. The docs are explicit that you *"should include a
  test that covers all migrations defined for your app's database… to ensure that there's no discrepancy
  between a recently created database instance and an earlier instance that followed the defined migration
  paths"*
  ([Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)).
  The documented form of this test creates the earliest version with the helper, then opens the database
  through `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS).build()`, because Room validates the
  schema when it opens. If every released version is to be covered, the same test parameterised over
  *"start at N, migrate to current"* for each committed N is the natural generalisation; Room provides no
  ready-made helper for it, so it is a loop the repository writes itself.

`AndroidX` also exercises the failure directions in the same file — an invalid migration that leaves the
schema wrong is expected to throw, and the `fallbackToDestructiveMigration*` behaviours have their own
tests ([`BaseMigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/commonTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/BaseMigrationTest.kt)).
Testing that a *bad* migration fails is as valuable as testing that a good one passes: it is the check that
the validation is switched on.

## 5. Auto migrations versus hand-written `Migration` objects

**Auto migrations** are declared on `@Database` and generated by the compiler from the two exported schemas:

```kotlin
@Database(
  version = 2,
  entities = [User::class],
  autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabaseV2 : RoomDatabase() { … }
```

When the diff is ambiguous — a deleted or renamed table or column — Room fails compilation and requires an
`AutoMigrationSpec` carrying `@DeleteTable` / `@RenameTable` / `@DeleteColumn` / `@RenameColumn`
([Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions);
[`SchemaDiffer.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-compiler/src/main/kotlin/androidx/room3/util/SchemaDiffer.kt)).
A spec may also implement `onPostMigrate(connection)` for work after the generated SQL runs.

**Hand-written migrations** are `Migration` objects added on the builder; in Room 3 `migrate` is a suspend
function taking an `SQLiteConnection`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
  override suspend fun migrate(connection: SQLiteConnection) {
    connection.executeSQL("CREATE TABLE `Fruit` (`id` INTEGER, `name` TEXT, PRIMARY KEY(`id`))")
  }
}
```

The docs warn to *"use full queries instead of referencing constants that represent the queries"* — a
migration must say what the schema was at that moment, not what a constant says today — and note that where
both an automated and a manual migration exist for the same version pair, **the manual one wins**
([Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)).

**How each is tested — the difference is small by design.** Both go through `runMigrationsAndValidate`:

- **Hand-written:** pass the objects — `runMigrationsAndValidate(2, listOf(MIGRATION_1_2))`.
- **Auto:** pass nothing. The helper is constructed with the database class (and any
  `autoMigrationSpecs` the database needs), and `runMigrationsAndValidate` includes the declared auto
  migrations itself
  ([`MigrationTestHelper.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt)).
  AndroidX's auto-migration tests are separate files —
  [`BaseAutoMigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/commonTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/BaseAutoMigrationTest.kt)
  with its JVM runner
  [`jvmTest/.../AutoMigrationTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/multiplatformtestapp/src/jvmTest/kotlin/androidx/room3/integration/multiplatformtestapp/test/AutoMigrationTest.kt)
  — and Android-side there is a dedicated test for specs that must be provided at runtime
  ([`ProvidedAutoMigrationSpecTest.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/integration-tests/kotlintestapp/src/androidTest/java/androidx/room3/integration/kotlintestapp/migration/ProvidedAutoMigrationSpecTest.kt)).

The thing worth stating plainly: an auto migration is generated from schemas, so a test of it is not a test
of hand-written SQL — it is a test that *the generated SQL preserves the data you care about*. That is not
tautological, because what the generated migration does with a dropped column, a renamed table or a new
NOT NULL column with a default is precisely where data quietly disappears. Both kinds need the same
insert-rows-and-assert-they-survived test.

## 6. Gradle wiring: Room + KSP + (optionally) Robolectric, with every version pinned

Versions read on 2026-09-10:

| Thing | Coordinate / id | Current stable | Source |
|---|---|---|---|
| Room 3 runtime | `androidx.room3:room3-runtime` | 3.0.3 (2026-09-09) | [room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3) |
| Room 3 compiler | `androidx.room3:room3-compiler` (via `ksp`) | 3.0.3 | as above |
| Room 3 testing | `androidx.room3:room3-testing` | 3.0.3 | [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions) shows `androidx.room3:room3-testing:3.0.2`; version tracks the release notes |
| Room Gradle plugin | `androidx.room3` | 3.0.3 | [room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3) |
| Room 2.x (maintenance) | `androidx.room:room-*` | 2.8.5 (2026-09-09) | [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) |
| SQLite driver | `androidx.sqlite:sqlite-bundled` (+ `sqlite`, `sqlite-framework`) | 2.7.1 (2026-09-09) | [androidx.sqlite release notes](https://developer.android.com/jetpack/androidx/releases/sqlite) |
| KSP | plugin `com.google.devtools.ksp`, `com.google.devtools.ksp:symbol-processing-api` | 2.3.12 latest on Central (metadata `lastUpdated` 2026-09-09); kotlinlang's quickstart shows `2.3.10` | [Maven Central metadata](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml), [KSP quickstart](https://kotlinlang.org/docs/ksp-quickstart.html) |
| Kotlin | `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.4.20 | [Maven Central metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml) |
| Robolectric | `org.robolectric:robolectric` | 4.16.1 (4.17 is in beta; metadata `lastUpdated` 2026-08-23) | [Maven Central metadata](https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml) |
| AGP | `com.android.application` | 9.4.0 (requires Gradle 9.6, JDK 17, per that page) | [AGP release notes](https://developer.android.com/build/releases/gradle-plugin) |

Google's KMP Room page gives the canonical wiring
([Room for Kotlin Multiplatform](https://developer.android.com/kotlin/multiplatform/room)) — a version
catalogue with `room3`, `sqlite` and `ksp`, plugins `com.google.devtools.ksp` and `androidx.room3`,
`implementation(libs.androidx.room3.runtime)` plus `implementation(libs.androidx.sqlite.bundled)` in
`commonMain`, one `add("ksp<Target>", libs.androidx.room3.compiler)` line **per target** (`kspAndroid`,
`kspJvm`/`kspDesktop`, …), and `room3 { schemaDirectory("$projectDir/schemas") }`. The database is built
with `.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)`. Note the per-target KSP
lines: **a target with no `ksp<Target>` dependency gets no generated code**, which is the usual reason a KMP
Room build compiles on Android and fails on JVM.

Pinning is straightforward here: every one of these is a plain version literal in the version catalogue or
`plugins {}` block, no dynamic selectors needed, which satisfies the build spec's pinning invariant. KSP is
the one coupling to watch — it must line up with the Kotlin version in use.

**`androidx.sqlite:sqlite-bundled` is what makes the JVM route real.** It ships SQLite *"compiled from
source"* with a `BundledSQLiteDriver` for non-web platforms including JVM/desktop
([androidx.sqlite release notes](https://developer.android.com/jetpack/androidx/releases/sqlite)) — a real
SQLite in the test JVM, the same engine on every developer machine and on CI, with no Android framework and
no `Robolectric` shadow of SQLite in between. Its native library is inside the published artifact, so it
needs no network at test time beyond ordinary dependency resolution.

**Robolectric's SDK jars and the network.** This is the item that matters for a hermetic `./gradlew test`.
Robolectric resolves an `android-all` jar for the SDK level under test at **test runtime**, not at Gradle
resolution time. `LegacyDependencyResolver` picks a resolver in this order
([source](https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/LegacyDependencyResolver.java)):

1. system property `robolectric-deps.properties` → properties file resolver;
2. system property `robolectric.dependency.dir`, **or** `robolectric.offline=true` → local directory
   resolver;
3. a `robolectric-deps.properties` on the classpath → properties file resolver;
4. otherwise → `MavenDependencyResolver`.

`MavenDependencyResolver` fetches over plain `HttpURLConnection` into the local Maven repository — it honours
`maven.repo.local`, then `~/.m2/settings.xml`, defaulting to `~/.m2/repository`
([source](https://github.com/robolectric/robolectric/blob/master/plugins/maven-dependency-resolver/src/main/java/org/robolectric/internal/dependency/MavenDependencyResolver.java)),
and the repository it fetches from is configurable via system properties with Maven Central as default:
`robolectric.dependency.repo.url` (default `https://repo1.maven.org/maven2`),
`robolectric.dependency.repo.id` (default `mavenCentral`), plus `.username`/`.password` and
`robolectric.dependency.proxy.host`/`.port`
([`MavenRoboSettings.java`](https://github.com/robolectric/robolectric/blob/master/plugins/maven-dependency-resolver/src/main/java/org/robolectric/MavenRoboSettings.java)).
So: **first Robolectric run needs network access to Maven Central unless the jars are pre-seeded**, after
which `~/.m2/repository` serves them. Offline CI means either priming that cache, or shipping the
`android-all` jars and pointing `robolectric.offline` / `robolectric.dependency.dir` at them.

Robolectric currently supports *"14 different versions of Android, ranging from M (API level 23) to
Baklava (API level 36)"* and notes JDK 21 is required to run tests targeting SDK 36
([README](https://github.com/robolectric/robolectric/blob/master/README.md)) — worth checking against this
project's JDK 17 target if the Robolectric route is ever taken.

## 7. "Schema as an API" for Room

The owner already runs this practice in a .NET project. Nothing here describes that project — only what the
practice becomes when the store is Room and SQLite.

The idea is that a persisted schema is a **published interface with users you cannot upgrade**: every
installed copy of the app holds a database at whatever version it last ran. You cannot redeploy those.
So the schema is under the same discipline as a public API — its shape is reviewed, its changes are
versioned, and compatibility with previously shipped versions is tested, not assumed.

Room supports this unusually well, because it already externalises the contract as a file:

- **The exported schema JSON is the contract artefact.** One file per version, committed, generated rather
  than written. It plays the role an OpenAPI document or a published assembly's public surface plays
  elsewhere. Its diff in a pull request is the schema review
  ([`SchemaBundle.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-migration/src/commonMain/kotlin/androidx/room3/migration/bundle/SchemaBundle.kt);
  [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)).
- **Version N's file is frozen once released.** A change to a *released* `N.json` is not a schema edit, it
  is a rewrite of history: the databases in the field still have the old shape, and Room's identity hash will
  say so at runtime
  ([`RoomConnectionManager.kt`](https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt)).
  "Never modify a released schema file; add a new one" is the Room form of "never change a published
  contract". A repository can make that a review rule, and could enforce it in CI by rejecting pull requests
  that modify a `schemas/**/N.json` whose version is below the current one.
- **Backward-compatibility tests are upgrade-path tests.** The .NET habit of "for each released contract
  version, assert the current code still honours it" maps onto: for each committed schema version N, create
  a database at N with representative data, migrate to current, assert the data is intact and the schema
  validates. That is `createDatabase(N)` + `runMigrationsAndValidate(current, …)`, and because the fixture
  is generated from the committed JSON, the set of tests grows automatically with the set of released
  versions rather than needing a stored database per release.
- **The consumer of the contract is the migration, not a caller.** Where an API contract test asserts
  response shapes, a schema contract test asserts that *data written by an older version of the app is still
  readable after upgrade*. So the fixtures must contain rows that exercise the interesting cases — nulls,
  defaults, rows that violate a constraint the new schema adds — not one empty happy-path row.
- **What does not carry across.** There is no consumer-driven contract testing here (there is exactly one
  consumer: the app itself), no negotiation or deprecation window (a user upgrading skips versions freely, so
  every path from any released version to current must work in one go), and no way to run "the old client
  against the new server" — the equivalent is the *data*, and the exported schema is the only record of what
  the old client wrote.

Concretely, this practice in this repository would mean: `exportSchema = true`, `schemas/` committed, a
review rule that released schema files are immutable, a migration test per hop, a test from every released
version to current, and fixture data seeded per version and kept forever.

## Open questions

1. **Which module layout does the project want?** A KMP module with `androidTarget()` + `jvm()` (migration
   tests as plain JVM tests, AndroidX's own approach) versus an Android-only module with Robolectric
   (fights the assets wiring, adds a network dependency at test time, and contradicts the current build
   spec invariant against a simulated Android runtime). This is a design decision for the owner, not a
   research finding.
2. **Exactly which assets AGP merges into a unit test's Android assets**, and therefore whether a Robolectric
   `MigrationTestHelper` can be fed schemas without shipping them in the app's `main` assets. No primary
   source I could reach states this; the AGP DSL reference page for `UnitTestOptions` did not render its
   content, and `robolectric.org` is blocked from this environment. Settle it by experiment before choosing
   the Robolectric route.
3. **Room 3.0.x's minimum Kotlin and KSP versions.** The release notes say only that KSP is required. The
   repo pins Kotlin 2.0.21, and KSP's independent versioning (2.3.x) tracks recent Kotlin releases, so
   Room 3 most likely requires a Kotlin upgrade — but the requirement is not stated on any page found.
4. **Room 3 versus staying on Room 2.8.x.** 2.8.5 is current and Google commits to patch releases, but the
   Room 3.0 announcement puts 2.x in maintenance mode. The 2.x tree has been removed from `androidx-main`,
   so its source is only on a release branch. If 2.8.x were chosen, whether `androidx.room:room-testing`
   2.8.x publishes a JVM variant with a schema-path constructor needs checking against that branch — this
   note verified it only for `room3-testing`.
5. **minSdk.** Room 2.8.0 raised Room's minSdk to API 23 and Robolectric's floor is API 23; the project's
   stated target of minSdk 26 clears both, but Room 3.0.x's own minSdk is not stated in its release notes.
6. **Whether CI should gate on `schemas/`.** Nothing off the shelf lints a schema diff. A repo-local check
   ("schema file changed ⇒ a migration and a migration test changed"; "released schema files are
   immutable") would have to be written — plausibly as one of the Python checks under `.github/scripts/`,
   which need no JDK or SDK.
