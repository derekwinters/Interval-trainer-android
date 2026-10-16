package com.derekwinters.intervaltrainer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * `docs/spec/brand.md` `BRAND-002`–`BRAND-005` and that page's first invariant: the launcher
 * icon's colours are fixed brand palettes — the light one shared with the Chores app, written once
 * in `BRAND-002`, and its dark variant in `BRAND-004` — and are not the design-system tokens.
 *
 * The expected values below are copied from the `BRAND-002` and `BRAND-004` tables and
 * deliberately not read from `DesignSystemColors`: the in-app theme and the icon are independent.
 * Every colour in the background resource and the foreground VectorDrawable, and in their
 * night-qualified counterparts, is compared, as a value, with the palette role its element names,
 * so a resource that drifts from the specification fails here. A pure-JVM file read — nothing here
 * needs a simulated runtime.
 */
class LauncherIconColourTest {

    /** `BRAND-002`'s palette, as `#AARRGGBB`. */
    private val background = "background" to 0xFFFFFFFF.toInt()
    private val blue = "blue" to 0xFF1361A7.toInt()
    private val grey = "grey" to 0xFF818589.toInt()

    /** `BRAND-004`'s dark palette, as `#AARRGGBB`. */
    private val darkBackground = "dark background" to 0xFF121212.toInt()
    private val darkBlue = "dark blue" to 0xFF5B9FE0.toInt()
    private val darkGrey = "dark grey" to 0xFF818589.toInt()

    @Test
    fun `the launcher background colour is the palette's background`() =
        assertBackground("values", background, "BRAND-003")

    @Test
    fun `the foreground's six elements are painted in their palette roles, in order`() =
        assertForeground("drawable", blue, grey, "BRAND-003")

    @Test
    fun `the night launcher background colour is the dark palette's background`() =
        assertBackground("values-night", darkBackground, "BRAND-005")

    @Test
    fun `the night foreground's six elements are painted in their dark palette roles, in order`() =
        assertForeground("drawable-night", darkBlue, darkGrey, "BRAND-005")

    private fun assertBackground(directory: String, colour: Pair<String, Int>, check: String) {
        val resource =
            File(appModuleDirectory(), "src/main/res/$directory/ic_launcher_background.xml")
        assertTrue("No ${resource.path} (BRAND-051, $check).", resource.isFile)
        val colours = parse(resource).getElementsByTagName("color")
        val declared = (0 until colours.length)
            .map { colours.item(it) as Element }
            .singleOrNull { it.getAttribute("name") == "ic_launcher_background" }
        assertTrue(
            "${resource.path} declares no <color name=\"ic_launcher_background\"> (BRAND-051).",
            declared != null,
        )
        assertColour("the launcher background", colour, declared!!.textContent.trim(), check)
    }

    private fun assertForeground(
        directory: String,
        blue: Pair<String, Int>,
        grey: Pair<String, Int>,
        check: String,
    ) {
        val drawable =
            File(appModuleDirectory(), "src/main/res/$directory/ic_launcher_foreground.xml")
        assertTrue("No ${drawable.path} (BRAND-051, $check).", drawable.isFile)
        val paths = parse(drawable).getElementsByTagName("path")
        val expected = listOf(
            "the work arc (BRAND-010)" to blue,
            "the recovery arc (BRAND-011)" to grey,
            "the crown cap (BRAND-012)" to blue,
            "the crown stem (BRAND-013)" to blue,
            "the hand (BRAND-014)" to blue,
            "the centre dot (BRAND-015)" to blue,
        )
        assertEquals(
            "${drawable.path} must draw exactly the six elements of BRAND-010–015, one <path> each.",
            expected.size,
            paths.length,
        )
        expected.forEachIndexed { index, (role, colour) ->
            val path = paths.item(index) as Element
            val paint = path.getAttribute("android:strokeColor").ifEmpty {
                path.getAttribute("android:fillColor")
            }
            assertColour("${drawable.path}: $role", colour, paint, check)
        }
    }

    private fun assertColour(
        role: String,
        colour: Pair<String, Int>,
        written: String,
        check: String,
    ) {
        val (name, argb) = colour
        assertEquals(
            "$role is painted \"$written\", which is not the palette's `$name` " +
                "(${hex(argb)}). The icon's colours are the fixed palettes written in " +
                "docs/spec/brand.md BRAND-002 and BRAND-004 (first invariant, $check).",
            hex(argb),
            hex(parseAndroidColour(written)),
        )
    }

    /** `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`, as Android colour resources accept them. */
    private fun parseAndroidColour(value: String): Int {
        val digits = value.removePrefix("#")
        val expanded = when (digits.length) {
            3, 4 -> digits.map { "$it$it" }.joinToString("")
            else -> digits
        }
        val argb = if (expanded.length == 6) "FF$expanded" else expanded
        require(argb.length == 8) { "\"$value\" is not an Android colour literal." }
        return argb.toLong(16).toInt()
    }

    private fun hex(argb: Int): String = "#%08X".format(argb)

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun appModuleDirectory(): File {
        var directory: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (directory != null) {
            if (File(directory, "src/main/AndroidManifest.xml").isFile) return directory
            val fromRoot = File(directory, "app")
            if (File(fromRoot, "src/main/AndroidManifest.xml").isFile) return fromRoot
            directory = directory.parentFile
        }
        throw IllegalStateException(
            "Could not find the :app module from ${System.getProperty("user.dir")}.",
        )
    }
}
