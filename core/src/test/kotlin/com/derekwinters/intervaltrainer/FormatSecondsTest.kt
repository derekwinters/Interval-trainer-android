package com.derekwinters.intervaltrainer

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM unit tests for [formatSeconds]. They need no device, no emulator and no simulated Android
 * runtime (BUILD-021), and they exercise production code rather than a constant (BUILD-022).
 */
class FormatSecondsTest {

    /** BUILD-030: `m:ss`, minutes unpadded, seconds always two digits. */
    @Test
    fun `formats seconds as m colon ss`() {
        assertEquals("0:00", formatSeconds(0))
        assertEquals("0:07", formatSeconds(7))
        assertEquals("0:59", formatSeconds(59))
        assertEquals("1:00", formatSeconds(60))
        assertEquals("1:05", formatSeconds(65))
        assertEquals("10:00", formatSeconds(600))
    }

    /** BUILD-031: minutes are not wrapped at an hour. */
    @Test
    fun `does not wrap minutes at an hour`() {
        assertEquals("60:00", formatSeconds(3600))
        assertEquals("90:30", formatSeconds(5430))
    }

    /** BUILD-032: a negative duration is rejected rather than formatted. */
    @Test
    fun `rejects a negative duration`() {
        assertThrows(IllegalArgumentException::class.java) { formatSeconds(-1) }
    }

    /** BUILD-033: the digits are ASCII whatever numbering system the default locale uses. */
    @Test
    fun `renders ascii digits under a non-ascii numbering locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        try {
            assertEquals("10:05", formatSeconds(605))
        } finally {
            Locale.setDefault(original)
        }
    }
}
