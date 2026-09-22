package com.derekwinters.intervaltrainer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * `docs/spec/design-system.md` `DS-022`: the application declares a theme whose own body has no
 * platform action bar, so `ScreenHeader` (`DS-021`, `DS-062`) is the only header on any screen.
 *
 * This reads the manifest and the theme resource the manifest names, as files, rather than
 * resolving the theme a running activity is given: resolving one needs a simulated Android
 * runtime, and `docs/spec/build.md` `BUILD-023` scopes Robolectric to `:designsystem` alone. It is
 * the theme's *declaration* that `DS-022` and this page's fifth invariant are about — a theme that
 * inherits the absence of an action bar from a parent's name satisfies neither — so the
 * declaration is what this asserts, and the assertion is not weakened by reading it from disk.
 *
 * `#132`: before that issue, neither `<application>` nor `<activity>` declared `android:theme` at
 * all and no theme resource existed, so the platform's own default theme applied and drew an
 * action bar titled from `android:label` above every screen's own header.
 */
class WindowThemeDeclarationTest {

    @Test
    fun `the application declares a theme whose body suppresses the platform action bar`() {
        val application = manifestApplicationElement()

        val themeAttribute = application.getAttribute("android:theme")
        assertTrue(
            "AndroidManifest.xml's <application> declares no android:theme resource (found " +
                "\"$themeAttribute\"), so every activity resolves the platform's default theme " +
                "and is given its action bar above the screen's own ScreenHeader " +
                "(DS-021, DS-022).",
            themeAttribute.startsWith("@style/"),
        )

        val themeName = themeAttribute.removePrefix("@style/")
        val theme = findStyle(themeName)
        assertNotNull(
            "AndroidManifest.xml names the theme \"$themeAttribute\", but no <style " +
                "name=\"$themeName\"> is declared under app/src/main/res/values (DS-022).",
            theme,
        )

        assertEquals(
            "The theme \"$themeName\" must set android:windowActionBar to false in its own body, " +
                "not inherit the absence of an action bar from a parent's name (DS-022, and this " +
                "page's fifth invariant).",
            "false",
            itemValue(theme!!, "android:windowActionBar"),
        )
        assertEquals(
            "The theme \"$themeName\" must set android:windowNoTitle to true in its own body, " +
                "not inherit the absence of a title bar from a parent's name (DS-022, and this " +
                "page's fifth invariant).",
            "true",
            itemValue(theme, "android:windowNoTitle"),
        )
    }

    private fun manifestApplicationElement(): Element {
        val manifest = File(appModuleDirectory(), "src/main/AndroidManifest.xml")
        assertTrue("No AndroidManifest.xml at ${manifest.absolutePath}.", manifest.isFile)
        val applications = parse(manifest).getElementsByTagName("application")
        assertEquals(
            "AndroidManifest.xml must have exactly one <application> element.",
            1,
            applications.length,
        )
        return applications.item(0) as Element
    }

    /** The `<style name="[name]">` element, from any resource file under `res/values`. */
    private fun findStyle(name: String): Element? {
        val values = File(appModuleDirectory(), "src/main/res/values")
        val files = values.listFiles { file: File -> file.isFile && file.extension == "xml" }
            ?: return null
        return files.sorted()
            .flatMap { parse(it).getElementsByTagName("style").asElements() }
            .firstOrNull { it.getAttribute("name") == name }
    }

    /** The text of `[style]`'s `<item name="[itemName]">`, or `null` when it declares none. */
    private fun itemValue(style: Element, itemName: String): String? =
        style.getElementsByTagName("item").asElements()
            .firstOrNull { it.getAttribute("name") == itemName }
            ?.textContent
            ?.trim()

    private fun parse(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    /**
     * The `:app` module's own directory. Gradle runs a unit test with its working directory set to
     * the module directory, but a test run from the root project is a working directory higher, so
     * both are accepted rather than assumed.
     */
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
