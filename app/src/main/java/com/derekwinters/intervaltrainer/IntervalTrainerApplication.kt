package com.derekwinters.intervaltrainer

import android.app.Application
import android.content.Context
import android.os.Build
import com.derekwinters.intervaltrainer.crash.CrashContext
import com.derekwinters.intervaltrainer.crash.CrashRecordingHandler
import com.derekwinters.intervaltrainer.crash.CrashReportStore
import com.derekwinters.intervaltrainer.crash.formatCrashReport
import java.io.File

/**
 * `docs/spec/service.md` `SVC-070`: the process-start hook that installs the crash handler once,
 * before any activity or service is created, so `MainActivity` and `WorkoutService` — one process
 * — are both covered. Debug and release builds alike.
 */
class IntervalTrainerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val store = crashReportStore()
        val crashContext = CrashContext(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )
        Thread.setDefaultUncaughtExceptionHandler(
            CrashRecordingHandler(Thread.getDefaultUncaughtExceptionHandler()) { thread, throwable ->
                store.write(formatCrashReport(crashContext, thread.name, throwable))
            },
        )
    }
}

/**
 * `docs/spec/service.md` `SVC-072` and its invariant: the one crash-report file, in
 * `noBackupFilesDir` — app-private, and excluded from backup, so the report never leaves the
 * device on its own even though the app allows backup. The handler above writes it; the crash
 * report popup reads and deletes it (`MainActivity.kt`).
 */
fun Context.crashReportStore(): CrashReportStore =
    CrashReportStore(File(noBackupFilesDir, "crash-report.txt"))
