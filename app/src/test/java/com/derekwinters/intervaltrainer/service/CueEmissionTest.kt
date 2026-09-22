package com.derekwinters.intervaltrainer.service

import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.VibrationPattern
import com.derekwinters.intervaltrainer.boundaryCue
import com.derekwinters.intervaltrainer.finishCue
import com.derekwinters.intervaltrainer.tickCue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/spec/cues.md`, the emission side: what the `:app` cue sink actually emits for a [Cue] that
 * `:core` selected.
 *
 * **Why this seam exists.** `:app`'s cue sink has to do three things at once — resolve a tone to a
 * bundled asset, resolve a vibration pattern to a waveform, and drive `SoundPool`, `Vibrator` and
 * an audio focus request with the results. The last of those is not reachable from a JVM runner:
 * `:app` has no Robolectric and `docs/spec/build.md` `BUILD-023` scopes Robolectric to
 * `:designsystem` alone, so a test that wanted to observe a `SoundPool.play` call has nowhere to
 * run. The first two are arithmetic, and they are where every way of getting this wrong that is
 * not a taste call lives: two cues sharing an asset, a tick that vibrates like a boundary, a muted
 * cue that still plays a tone, a mapping that names an asset nobody committed.
 *
 * So [emissionFor] is a pure function — [Cue] in, the asset and the waveform out, no Android type
 * touched — and the adapter is the thin part that takes its answer to the platform. That split is
 * a design decision this test forced rather than a convenience: with the mapping inlined into the
 * adapter, none of the assertions below could be written at all, which is the same reasoning
 * `docs/spec/cues.md`'s fifth invariant applies one level up, to selection against emission.
 *
 * **Nothing here pins a number from §9.** The pitches, durations and pulse lengths in that section
 * are a starting point and changing one is explicitly not a specification change, so what is
 * asserted is the structure §2, §3 and §6 do fix — how many there are, which is distinct from
 * which, what mute does — and never a value. A test that pinned 880 Hz would turn retuning a cue
 * into a test failure, which is precisely the false precision §9 refuses.
 *
 * **What this does not cover, and is not pretended to.** Nothing here hears a tone or feels a
 * vibration. `CUE-014` and `CUE-015` — the finish being unmistakable, the ticks being subordinate —
 * are judgements about sound, and `CUE-024`, `CUE-060`, `CUE-061` and `CUE-070`–`CUE-073` are facts
 * about emission and about what the app deliberately does not do. All of those stay `manual` in
 * that page's traceability table and are verified by a human on a device. This test asserts that
 * the adapter asks for the right thing; whether the platform then delivers it, and whether what it
 * delivers sounds right, is not in reach here.
 */
class CueEmissionTest {

    private val work = boundaryCue(IntervalKind.WORK, muted = false)
    private val recovery = boundaryCue(IntervalKind.RECOVERY, muted = false)
    private val tick = tickCue(muted = false)
    private val finish = finishCue(muted = false)

    private val allFour = listOf(work, recovery, tick, finish)

    // ---- Tones (CUE-010–013, CUE-016) ----------------------------------------------------------

    @Test
    fun `each of the four cues emits its own tone asset`() {
        val assets = allFour.map { cue ->
            val asset = emissionFor(cue).toneAsset
            assertNotNull("An unmuted $cue must emit a tone asset (CUE-010–013).", asset)
            asset!!
        }

        assertEquals(
            "The four cues must emit four different tone assets: a tick is never one of the " +
                "boundary tones (CUE-012) and the finish is none of the other three (CUE-013). " +
                "Got $assets.",
            4,
            assets.toSet().size,
        )
        assertEquals(
            "CUE-016: the tone vocabulary is exactly these four and selection never produces a " +
                "fifth, so the four cues must between them account for every ToneAsset — an " +
                "asset no cue emits is an asset shipped in the APK for nothing, and a fifth " +
                "entry is a tone this page does not have.",
            ToneAsset.entries.toSet(),
            assets.toSet(),
        )
    }

    @Test
    fun `warm-up and cool-down emit the recovery tone asset`() {
        val recoveryAsset = emissionFor(recovery).toneAsset

        for (kind in listOf(IntervalKind.WARM_UP, IntervalKind.COOL_DOWN)) {
            assertEquals(
                "CUE-011: $kind means \"not working yet\" or \"not working any more\", which is " +
                    "what the recovery tone already says, so it emits the same asset — not a " +
                    "fifth one.",
                recoveryAsset,
                emissionFor(boundaryCue(kind, muted = false)).toneAsset,
            )
        }
    }

    // ---- Vibration (CUE-020–023) ---------------------------------------------------------------

    /**
     * `CUE-023`, over the whole pattern vocabulary rather than over the four cues that happen to
     * carry it: every [VibrationPattern] `:core` can select must emit a waveform of its own. Mute
     * does not reach vibration (`CUE-020`), so the cues here carry no tone — what is asserted is a
     * property of the pattern, not of the cue that brought it.
     */
    @Test
    fun `every vibration pattern emits its own waveform`() {
        val waveforms = VibrationPattern.entries.associateWith { pattern ->
            emissionFor(Cue(tone = null, vibration = pattern, colorRole = null))
                .vibrationTimingsMillis
        }

        assertEquals(
            "CUE-023: the pattern vocabulary is exactly these four and no two share a waveform — " +
                "vibration carries every distinction the tones carry (this page's second " +
                "invariant), and two patterns emitting the same timings collapses one of those " +
                "distinctions for a muted user, who has no other channel. Got $waveforms.",
            VibrationPattern.entries.size,
            waveforms.values.toSet().size,
        )
    }

    @Test
    fun `the two boundary vibrations differ in pulse count`() {
        val workPulses = pulseCount(emissionFor(work).vibrationTimingsMillis)
        val recoveryPulses = pulseCount(emissionFor(recovery).vibrationTimingsMillis)

        assertNotEquals(
            "CUE-021: work start and recovery start have distinct patterns, differing in pulse " +
                "count — not in pulse length alone. Vibration is a coarse channel read through a " +
                "pocket while moving, and pulse count is the distinction that survives it. Both " +
                "emitted $workPulses pulse(s).",
            workPulses,
            recoveryPulses,
        )
    }

    @Test
    fun `the finish vibration is neither boundary pattern`() {
        val finishTimings = emissionFor(finish).vibrationTimingsMillis

        assertNotEquals(
            "CUE-022: the finish pattern is neither of the boundary patterns. Feeling a boundary " +
                "pattern when the workout has ended is the failure this page's first invariant " +
                "exists to exclude — it means standing in the road waiting for a signal that " +
                "will never come.",
            emissionFor(work).vibrationTimingsMillis,
            finishTimings,
        )
        assertNotEquals(
            "CUE-022: the finish pattern is neither of the boundary patterns.",
            emissionFor(recovery).vibrationTimingsMillis,
            finishTimings,
        )
    }

    @Test
    fun `every vibration is a well-formed waveform that starts immediately`() {
        for (cue in allFour) {
            val timings = emissionFor(cue).vibrationTimingsMillis

            assertTrue(
                "A VibrationEffect waveform alternates off, on, off, on..., so a pattern must " +
                    "have an even number of entries to end on a pulse rather than on silence. " +
                    "$cue emitted $timings.",
                timings.size >= 2 && timings.size % 2 == 0,
            )
            assertEquals(
                "The first entry of a waveform is the delay before the first pulse, and a cue " +
                    "fires now: a non-zero delay would push every vibration late by it, and a " +
                    "cue that arrives late is a cue that arrives wrong. $cue emitted $timings.",
                0L,
                timings.first(),
            )
            assertTrue(
                "Every entry after the leading delay is a duration in milliseconds and must be " +
                    "positive — a zero-length pulse is not felt and a zero-length gap merges two " +
                    "pulses into one, destroying the pulse count CUE-021 distinguishes by. $cue " +
                    "emitted $timings.",
                timings.drop(1).all { it > 0L },
            )
        }
    }

    // ---- Mute (CUE-050, and CUE-020 again) -----------------------------------------------------

    /**
     * `CUE-050` and `CUE-020`, against the cues `:core` actually produces when muted rather than
     * against a [Cue] this test blanked itself: mute silences tones and leaves vibration untouched.
     */
    @Test
    fun `a muted cue emits no tone and the vibration it would have emitted anyway`() {
        val pairs = listOf(
            boundaryCue(IntervalKind.WORK, muted = false) to
                boundaryCue(IntervalKind.WORK, muted = true),
            boundaryCue(IntervalKind.RECOVERY, muted = false) to
                boundaryCue(IntervalKind.RECOVERY, muted = true),
            tickCue(muted = false) to tickCue(muted = true),
            finishCue(muted = false) to finishCue(muted = true),
        )

        for ((unmuted, muted) in pairs) {
            assertNotNull(
                "Precondition, so the assertion below is not vacuous: the unmuted $unmuted must " +
                    "emit a tone asset for muting it to have silenced anything.",
                emissionFor(unmuted).toneAsset,
            )
            assertNull(
                "CUE-050: mute silences tones. A cue `:core` selected with no tone must emit no " +
                    "tone asset, rather than the adapter re-deriving one from the vibration. " +
                    "Muted cue: $muted.",
                emissionFor(muted).toneAsset,
            )
            assertEquals(
                "CUE-020: mute does not suppress vibration. In a muted workout vibration is not " +
                    "a supplement to the audio — it is the only channel carrying information, so " +
                    "it must be the same waveform the unmuted cue emits. Muted cue: $muted.",
                emissionFor(unmuted).vibrationTimingsMillis,
                emissionFor(muted).vibrationTimingsMillis,
            )
        }
    }

    // ---- The assets themselves -----------------------------------------------------------------

    /**
     * A mapping naming an asset nobody committed is a cue that is silent on the device and says
     * nothing about it: `SoundPool.load` returns sound id 0 for a resource it cannot decode, and
     * `play(0, ...)` is a no-op. Neither the compiler nor any other test above would notice, which
     * is why this one reads `res/raw` off disk — the same approach, and for the same reason, as
     * `WindowThemeDeclarationTest`.
     */
    @Test
    fun `every tone asset names a committed file in res raw`() {
        val raw = File(appModuleDirectory(), "src/main/res/raw")
        assertTrue("No raw resource directory at ${raw.absolutePath}.", raw.isDirectory)
        val files = raw.listFiles { file: File -> file.isFile } ?: emptyArray()

        for (asset in ToneAsset.entries) {
            val matches = files.filter { it.nameWithoutExtension == asset.resourceName }
            assertEquals(
                "$asset names the raw resource \"${asset.resourceName}\", which SoundPool loads " +
                    "as R.raw.${asset.resourceName}. Exactly one file under ${raw.absolutePath} " +
                    "must have that base name — none means the cue is silent on a device and " +
                    "nothing here would have said so, and two means the resource that wins is " +
                    "whichever the packager picked. Found ${matches.map { it.name }}.",
                1,
                matches.size,
            )

            val file = matches.single()
            val header = file.readBytes().take(12).toByteArray()
            assertTrue(
                "${file.name} is ${file.length()} bytes and does not begin with a container " +
                    "SoundPool decodes (RIFF/WAVE or Ogg). An empty or placeholder asset loads " +
                    "as sound id 0 and plays nothing, silently.",
                isWaveHeader(header) || isOggHeader(header),
            )
        }
    }

    /** The number of pulses in a `VibrationEffect` waveform: its odd-indexed, "on" entries. */
    private fun pulseCount(timings: List<Long>): Int = timings.size / 2

    private fun isWaveHeader(header: ByteArray): Boolean =
        header.size >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WAVE"

    private fun isOggHeader(header: ByteArray): Boolean =
        header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "OggS"

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
