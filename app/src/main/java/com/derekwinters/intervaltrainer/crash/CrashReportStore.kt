package com.derekwinters.intervaltrainer.crash

import java.io.File
import java.io.IOException

/**
 * `docs/spec/service.md` `SVC-072`: the one file a crash report lives in. Writing replaces
 * whatever the file held, so only the most recent crash is kept; [delete] is called only by the
 * crash report popup's Dismiss (`docs/spec/screens.md` `SCREEN-093` and §8's invariant).
 *
 * Plain `java.io` over a [File] the caller chooses — in the app, a file in
 * `Context.noBackupFilesDir`, so the report is app-private and never goes to a cloud backup
 * (`IntervalTrainerApplication.kt`) — so this is tested on the JVM in `CrashReportStoreTest.kt`.
 */
class CrashReportStore(private val file: File) {

    /** The recorded report, or `null` when there is none or it cannot be read. */
    fun read(): String? =
        try {
            if (file.isFile) file.readText() else null
        } catch (ignored: IOException) {
            null
        }

    fun write(report: String) {
        file.parentFile?.mkdirs()
        file.writeText(report)
    }

    fun delete() {
        file.delete()
    }
}
