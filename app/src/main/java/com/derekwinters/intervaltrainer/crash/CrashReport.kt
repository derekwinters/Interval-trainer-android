package com.derekwinters.intervaltrainer.crash

/** `docs/spec/service.md` `SVC-071`: the app and platform facts a crash report carries. */
data class CrashContext(
    val versionName: String,
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
)

/** `docs/spec/service.md` `SVC-071`. */
fun formatCrashReport(context: CrashContext, threadName: String, throwable: Throwable): String =
    TODO("#146: SVC-071")
