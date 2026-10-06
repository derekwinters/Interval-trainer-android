package com.derekwinters.intervaltrainer

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `docs/spec/brand.md` `BRAND-052`: the application's icon, as the manifest declares it, resolves
 * to an `AdaptiveIconDrawable` on API 26 and later, and on API 33 and later that drawable carries
 * the themed-icon monochrome layer (`BRAND-030`).
 *
 * This is one of the `:app` tests `docs/spec/build.md` `BUILD-023` names as allowed Robolectric:
 * what is asserted is what the platform resolves from the merged manifest and resources, which a
 * file read cannot show. The API levels are pinned — 26, `minSdk` and the first with adaptive
 * icons, and 33, the first with `AdaptiveIconDrawable.getMonochrome()` — rather than floating with
 * `compileSdk`, so they decide which `android-all` jars CI pre-fetches (`DS-092`). This file is part
 * of that cache's key in `.github/workflows/pr.yml` and `release-candidate.yml` for that reason.
 */
@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    @Test
    @Config(sdk = [26])
    fun `the application icon resolves to an adaptive icon on API 26`() {
        assertTrue(
            "The application's icon must be an AdaptiveIconDrawable on API 26 (BRAND-052), " +
                "but resolved to ${resolvedApplicationIcon().javaClass.name}.",
            resolvedApplicationIcon() is AdaptiveIconDrawable,
        )
    }

    @Test
    @Config(sdk = [33])
    fun `the application icon has a monochrome layer on API 33`() {
        val icon = resolvedApplicationIcon()
        assertTrue(
            "The application's icon must be an AdaptiveIconDrawable on API 33 (BRAND-052), " +
                "but resolved to ${icon.javaClass.name}.",
            icon is AdaptiveIconDrawable,
        )
        assertNotNull(
            "The application's adaptive icon has no monochrome layer on API 33, so themed icons " +
                "cannot show it (BRAND-030, BRAND-052).",
            (icon as AdaptiveIconDrawable).monochrome,
        )
    }

    /** The drawable for `android:icon` on `<application>`, resolved as a launcher would. */
    private fun resolvedApplicationIcon(): Drawable {
        val context = RuntimeEnvironment.getApplication()
        val iconResource = context.applicationInfo.icon
        assertNotEquals(
            "AndroidManifest.xml's <application> declares no android:icon, so every launcher " +
                "shows the system default (BRAND-052).",
            0,
            iconResource,
        )
        return checkNotNull(context.getDrawable(iconResource)) {
            "android:icon names resource $iconResource, which resolves to no drawable."
        }
    }
}
