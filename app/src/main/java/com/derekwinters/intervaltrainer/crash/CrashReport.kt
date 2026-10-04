package com.derekwinters.intervaltrainer.crash

/**
 * `docs/spec/service.md` `SVC-071`: the app and platform facts a crash report carries besides the
 * crash itself. Plain values, resolved by the caller from `BuildConfig` and `android.os.Build`
 * (`IntervalTrainerApplication.kt`), so [formatCrashReport] needs no `android.*` import and is
 * tested on the JVM in `CrashReportTest.kt`.
 */
data class CrashContext(
    val versionName: String,
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
)

/**
 * `docs/spec/service.md` `SVC-071`: a crash report as plain text — the app version, the Android
 * version, the crashing thread's name, then the throwable's full stack trace, causes included.
 * Plain text because the only thing that ever happens to it is being copied and pasted into a bug
 * report (`docs/spec/screens.md` `SCREEN-092`).
 */
fun formatCrashReport(context: CrashContext, threadName: String, throwable: Throwable): String =
    buildString {
        appendLine("Interval Trainer crash report")
        appendLine("App version: ${context.versionName} (${context.versionCode})")
        appendLine("Android version: ${context.androidRelease} (API ${context.sdkInt})")
        appendLine("Thread: $threadName")
        appendLine()
        append(throwable.stackTraceToString())
    }
