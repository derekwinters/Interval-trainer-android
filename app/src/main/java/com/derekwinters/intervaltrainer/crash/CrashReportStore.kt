package com.derekwinters.intervaltrainer.crash

import java.io.File

/** `docs/spec/service.md` `SVC-072`. */
class CrashReportStore(private val file: File) {
    fun read(): String? = TODO("#146: SVC-072")

    fun write(report: String): Unit = TODO("#146: SVC-072")

    fun delete(): Unit = TODO("#146: SVC-072")
}
