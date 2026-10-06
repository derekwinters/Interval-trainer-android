package com.derekwinters.intervaltrainer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * `docs/spec/brand.md` `BRAND-002`, `BRAND-003` and that page's first invariant: the launcher
 * icon's colours are `DesignSystemColors.Default`'s tokens, never a second palette.
 *
 * Android resources cannot reference a Compose `Color`, so the icon's XML carries the hex values
 * itself. This is what makes that copy safe: every colour in the background resource and the
 * foreground VectorDrawable is compared, as a value, with the token its role names, so a token
 * change the icon does not follow fails here rather than shipping a launcher icon in the old
 * palette. A pure-JVM file read — nothing here needs a simulated runtime.
 */
class LauncherIconColourTokenTest {

    private val tokens = DesignSystemColors.Default

    @Test
    fun `the launcher background colour is the bg token`() {
        val resource = File(appModuleDirectory(), "src/main/res/values/ic_launcher_background.xml")
        assertTrue("No ${resource.path} (BRAND-051).", resource.isFile)
        val colours = parse(resource).getElementsByTagName("color")
        val background = (0 until colours.length)
            .map { colours.item(it) as Element }
            .singleOrNull { it.getAttribute("name") == "ic_launcher_background" }
        assertTrue(
            "${resource.path} declares no <color name=\"ic_launcher_background\"> (BRAND-051).",
            background != null,
        )
        assertColour("the launcher background", "bg", tokens.bg, background!!.textContent.trim())
    }

    @Test
    fun `the foreground's six elements are painted in their tokens, in order`() {
        val drawable = File(appModuleDirectory(), "src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue("No ${drawable.path} (BRAND-051).", drawable.isFile)
        val paths = parse(drawable).getElementsByTagName("path")
        val expected = listOf(
            Triple("the work arc (BRAND-010)", "work", tokens.work),
            Triple("the recovery arc (BRAND-011)", "recovery", tokens.recovery),
            Triple("the crown cap (BRAND-012)", "fg", tokens.fg),
            Triple("the crown stem (BRAND-013)", "fg", tokens.fg),
            Triple("the hand (BRAND-014)", "fg", tokens.fg),
            Triple("the centre dot (BRAND-015)", "fg", tokens.fg),
        )
        assertEquals(
            "${drawable.path} must draw exactly the six elements of BRAND-010–015, one <path> each.",
            expected.size,
            paths.length,
        )
        expected.forEachIndexed { index, (role, tokenName, token) ->
            val path = paths.item(index) as Element
            val paint = path.getAttribute("android:strokeColor").ifEmpty {
                path.getAttribute("android:fillColor")
            }
            assertColour(role, tokenName, token, paint)
        }
    }

    private fun assertColour(role: String, tokenName: String, token: Color, written: String) {
        assertEquals(
            "$role is painted \"$written\", which is not the `$tokenName` token " +
                "(${hex(token.toArgb())}). The icon's colours are the design-system tokens and are " +
                "never redefined separately (docs/spec/brand.md, first invariant, BRAND-003).",
            hex(token.toArgb()),
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
