package com.derekwinters.intervaltrainer.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.derekwinters.intervaltrainer.IntervalKind
import java.util.UUID

/**
 * SCHEMA-030: seeds the two endurance presets (SCHEMA-031, SCHEMA-032, SCHEMA-034) the one time
 * Room calls [onCreate] — when a brand-new database file has just had its tables created. Room
 * never calls this again for that same file: not on a later launch that reopens it, and not on a
 * migration, only on a database "created from nothing" (SCHEMA-030). Nothing in this class polls
 * for "have I already seeded" — Room's own `onCreate` contract is the once-only guarantee.
 *
 * This module stays on the Room 2.x Kotlin Multiplatform line (see `database/build.gradle.kts`),
 * whose [RoomDatabase.Callback] declares [onCreate] against [SQLiteConnection] — the
 * driver-based, multiplatform callback shape — rather than the legacy, Android-only
 * `SupportSQLiteDatabase` overload. A caller must attach this to the builder itself
 * (`Room.databaseBuilder<IntervalTrainerDatabase>(...).addCallback(SeedDataCallback)`); nothing
 * about `@Database` or [IntervalTrainerDatabase] wires it in on its own, so a build that never
 * calls `addCallback` — as most of this module's other tests still do — never seeds anything.
 *
 * `onCreate` fires from inside the same connection, and the same transaction, Room used to create
 * `presets` and `intervals` in the first place, so every insert below is part of that one
 * transaction with nothing further for this class to open or commit itself.
 */
public object SeedDataCallback : RoomDatabase.Callback() {

    override fun onCreate(connection: SQLiteConnection) {
        insertPreset(connection, name = "Short Example", intervals = shortExampleIntervals())
        insertPreset(connection, name = "Long Example", intervals = longExampleIntervals())
    }

    /**
     * SCHEMA-031: warm-up 3:00, then three rounds of work 1:00 / recovery 2:00, then cool-down
     * 3:00 — eight rows, 900 seconds total (SCHEMA-033: the last round's recovery ends before
     * cool-down begins).
     */
    private fun shortExampleIntervals(): List<Pair<IntervalKind, Int>> = buildList {
        add(IntervalKind.WARM_UP to 180)
        repeat(3) {
            add(IntervalKind.WORK to 60)
            add(IntervalKind.RECOVERY to 120)
        }
        add(IntervalKind.COOL_DOWN to 180)
    }

    /**
     * SCHEMA-032: warm-up 5:00, then eight rounds of work 1:00 / recovery 2:00, then cool-down
     * 5:00 — eighteen rows, 2040 seconds total (SCHEMA-033).
     */
    private fun longExampleIntervals(): List<Pair<IntervalKind, Int>> = buildList {
        add(IntervalKind.WARM_UP to 300)
        repeat(8) {
            add(IntervalKind.WORK to 60)
            add(IntervalKind.RECOVERY to 120)
        }
        add(IntervalKind.COOL_DOWN to 300)
    }

    /**
     * Inserts one preset row (SCHEMA-010) and its ordered interval rows (SCHEMA-020, SCHEMA-025)
     * with raw, bound SQL — `onCreate` runs before the [IntervalTrainerDatabase] instance itself
     * exists, so [PresetDao] is not available yet and there is nothing to inject it from
     * (Room's own documented shape for this callback, on the [SQLiteConnection] it hands in).
     * [PresetEntity.id] is a generated UUID (SCHEMA-011), assigned here exactly once per seeded
     * preset; [IntervalEntity.id] is never bound, so Room's own auto-increment default fills it
     * (SCHEMA-022).
     */
    private fun insertPreset(
        connection: SQLiteConnection,
        name: String,
        intervals: List<Pair<IntervalKind, Int>>,
    ) {
        val presetId = UUID.randomUUID().toString()
        connection.prepare("INSERT INTO presets (id, name) VALUES (?, ?)").use { statement ->
            statement.bindText(1, presetId)
            statement.bindText(2, name)
            statement.step()
        }
        intervals.forEachIndexed { position, (kind, durationSeconds) ->
            connection.prepare(
                "INSERT INTO intervals (presetId, kind, durationSeconds, position) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.bindText(1, presetId)
                statement.bindText(2, kind.toColumnValue())
                statement.bindLong(3, durationSeconds.toLong())
                statement.bindLong(4, position.toLong())
                statement.step()
            }
        }
    }
}
