package com.derekwinters.intervaltrainer

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase
import com.derekwinters.intervaltrainer.database.SeedDataCallback

/**
 * The one place `:app` builds the real, on-device [IntervalTrainerDatabase] (`SCHEMA-004`).
 *
 * `WorkoutService` built this inline before the home screen existed
 * ([#79](https://github.com/derekwinters/Interval-trainer-android/issues/79)); once a second
 * caller — home, reading the preset list to show (`docs/spec/screens.md` `SCREEN-002`) — needed
 * the same database, the name, the driver and the seed callback moved here so the service and the
 * rest of the app can never open two different files by one of the two call sites drifting from
 * the other.
 */
object AppDatabase {
    private const val DATABASE_NAME = "interval-trainer.db"

    /**
     * Opens the on-device database, seeding it (`SCHEMA-030`) the one time it is created from
     * nothing. [context] should be an application context; this is the only place `:app` names
     * [DATABASE_NAME] or constructs the builder.
     */
    fun open(context: Context): IntervalTrainerDatabase =
        Room.databaseBuilder<IntervalTrainerDatabase>(
            context = context.applicationContext,
            name = context.applicationContext.getDatabasePath(DATABASE_NAME).absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .addCallback(SeedDataCallback)
            .build()
}
