package com.derekwinters.intervaltrainer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.derekwinters.intervaltrainer.Cue
import com.derekwinters.intervaltrainer.CueSink
import com.derekwinters.intervaltrainer.R

/**
 * The real [CueSink] (`docs/spec/cues.md`, issue #130): the tone through `SoundPool` and the
 * vibration through `VibrationEffect`, for every [Cue] `:core` selects.
 *
 * **This class decides nothing.** [emissionFor] — a pure function, in `CueEmission.kt`, covered by
 * `CueEmissionTest` — says which asset and which waveform a [Cue] gets; everything here is the
 * carrying of that answer to the platform. That split is deliberate and load-bearing: `:app` has
 * no Robolectric (`docs/spec/build.md` `BUILD-023` scopes it to `:designsystem`), so anything
 * decided inside this class is decided where no JVM test can see it. If a cue is emitting the
 * wrong sound, the bug is in `CueEmission.kt` and there is a test to write for it; if a cue is
 * emitting nothing at all, the bug is here and only a device will show it.
 *
 * **What this class deliberately does not do** (`CUE-070`, `CUE-071`, `CUE-073`, each verified by
 * a call being absent from the diff): it does not read or raise the media volume, does not set
 * `FLAG_AUDIBILITY_ENFORCED`, does not read Do Not Disturb state or request notification policy
 * access, and does not register a becoming-noisy receiver. Every cue is emitted the same way in
 * every condition and what the user then hears is the platform's decision — this page's fourth
 * invariant. Under total silence (`INTERRUPTION_FILTER_NONE`) the platform suppresses both
 * channels and the workout runs visually; that is accepted (`CUE-072`), not worked around.
 *
 * **Lifecycle.** [WorkoutService] constructs one of these in `onCreate` and [close]s it in
 * `onDestroy`, alongside the wake lock and the database — a `SoundPool` holds decoded audio in
 * native memory for as long as it lives, so it is released where the service releases everything
 * else it owns.
 *
 * *(Manual: `SoundPool`, `Vibrator` and audio focus are not reachable from a JVM runner. `CUE-024`,
 * `CUE-060`, `CUE-061` and `CUE-070`–`CUE-073` stay `manual` in this page's traceability table and
 * are verified by a human on a device.)*
 */
class AndroidCueSink(context: Context) : CueSink, AutoCloseable {

    private val applicationContext: Context = context.applicationContext

    private val audioManager: AudioManager? =
        applicationContext.getSystemService(AudioManager::class.java)

    private val vibrator: Vibrator? = obtainVibrator(applicationContext)
        ?.takeIf { it.hasVibrator() }

    /**
     * `CUE-060`: `USAGE_MEDIA` with `CONTENT_TYPE_SONIFICATION`. Media, because that is what binds
     * a cue to the media volume slider the user already reaches for; sonification, because a cue
     * tone is precisely a sound used to accompany an event.
     */
    private val toneAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * `CUE-024`, below API 33: the attributes-carrying `vibrate` overload with an alarm usage.
     * The usage differs from [toneAttributes]' on purpose — the tone follows media volume, while
     * the vibration needs an alarm-class usage to be permitted at all from a process the platform
     * considers background (`docs/research/cue-audio.md`). Two attribute systems, one deliberate
     * difference.
     */
    private val vibrationAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        // The builder's own default is 1. Two, so a cue that fires while the previous one is still
        // sounding is mixed rather than cutting it off; nothing here ever needs more, since the
        // closest any two cues come is the one second between countdown ticks.
        .setMaxStreams(2)
        .setAudioAttributes(toneAttributes)
        .build()

    /**
     * Loaded once, here, rather than per cue: `SoundPool` decodes at load time, so the decode cost
     * is paid while the service starts instead of at the moment a cue is due.
     *
     * `load` is asynchronous and `play` returns 0 for a sound that is not decoded yet. Nothing
     * waits for the load to complete, because the only cue that could beat it is the lead-in's
     * first tick, fired in the same breath as the command that created this service; the three
     * ticks of that lead-in (`CUE-041`) then put three seconds between it and the first boundary
     * cue. A silent first tick is the whole cost, and waiting would not make it sound.
     */
    private val soundIds: Map<ToneAsset, Int> = ToneAsset.entries.associateWith { asset ->
        soundPool.load(applicationContext, asset.rawResourceId(), SOUND_PRIORITY)
    }

    private val focusLock = Any()
    private val handler = Handler(Looper.getMainLooper())

    /** The focus request currently held, so that the *same instance* is what abandons it. */
    private var heldFocusRequest: AudioFocusRequest? = null

    private val abandonFocus = Runnable { abandonFocusNow() }

    override fun fire(cue: Cue) {
        val emission = emissionFor(cue)
        emission.toneAsset?.let(::playTone)
        vibrate(emission.vibrationTimingsMillis)
    }

    /**
     * Releases the decoded audio this sink holds in native memory. After this the sink emits
     * nothing; [WorkoutService] calls it in `onDestroy` and never afterwards fires a cue.
     */
    override fun close() {
        handler.removeCallbacks(abandonFocus)
        abandonFocusNow()
        soundPool.release()
    }

    // ---- Tones (CUE-060, CUE-061) --------------------------------------------------------------

    /**
     * `CUE-061`: focus is requested immediately before the cue and the same request instance is
     * abandoned after it. The un-ducking of whatever else is playing is tied to the abandon, so a
     * request held between cues would leave a music player quiet for the whole workout — and one
     * abandoned in the next statement would un-duck before the tone had been heard, which honours
     * the letter of the requirement and none of its point. So the abandon is posted for
     * [FOCUS_HOLD_MILLIS] and any still-held request is abandoned first, which keeps at most one
     * outstanding at a time.
     *
     * No `OnAudioFocusChangeListener` is registered. A cue is a quarter of a second long; there is
     * nothing for this app to pause or duck when it loses focus itself, and reacting to the
     * platform's conditions is exactly what this page's fourth invariant forbids.
     */
    private fun playTone(asset: ToneAsset) {
        val soundId = soundIds[asset] ?: return
        if (soundId == 0) return

        requestFocus()
        // Full volume on both channels: the tick's lower level (§9) is baked into the asset the
        // generator script produced, so attenuating here would apply it a second time.
        soundPool.play(soundId, 1f, 1f, SOUND_PRIORITY, 0, 1f)
    }

    private fun requestFocus() {
        val manager = audioManager ?: return
        val request = AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(toneAttributes)
            .build()

        synchronized(focusLock) {
            heldFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            heldFocusRequest = request
        }
        manager.requestAudioFocus(request)

        handler.removeCallbacks(abandonFocus)
        handler.postDelayed(abandonFocus, FOCUS_HOLD_MILLIS)
    }

    private fun abandonFocusNow() {
        val manager = audioManager ?: return
        val request = synchronized(focusLock) {
            heldFocusRequest.also { heldFocusRequest = null }
        }
        request?.let { manager.abandonAudioFocusRequest(it) }
    }

    // ---- Vibration (CUE-020, CUE-024) ----------------------------------------------------------

    /**
     * `CUE-024`: every vibration carries an alarm usage — `VibrationAttributes.USAGE_ALARM` on API
     * 33 and above, and the `AudioAttributes.USAGE_ALARM` overload below it. The `Vibrator`
     * reference attaches "the app should be in the foreground for the vibration to happen" to
     * every overload and does not say a foreground service satisfies that test; the documented
     * remedy for a background app is an alarm-class usage, so the attributes overload is always
     * the one called and never the bare `vibrate(effect)`.
     *
     * Mute never reaches this method (`CUE-020`) — [emissionFor] returns the same timings for a
     * muted cue as for an unmuted one, and a muted workout is the case where vibration is the only
     * channel carrying information at all.
     */
    private fun vibrate(timingsMillis: List<Long>) {
        val vibrator = vibrator ?: return
        val effect = VibrationEffect.createWaveform(timingsMillis.toLongArray(), NO_REPEAT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, vibrationAudioAttributes)
        }
    }

    private companion object {
        /**
         * How long focus is held after a cue starts sounding, before the posted abandon un-ducks
         * whatever else is playing.
         *
         * This is a **bound, not a duration**: it is not one of `docs/spec/cues.md` §9's numbers
         * and does not have to track them. It only has to sit above the longest cue tone §9
         * describes — the finish, three 120 ms notes back to back — and below the one second
         * between countdown ticks, so a cue is never un-ducked while still sounding and focus is
         * never still held when the next cue asks for it. Retuning §9 leaves this correct unless a
         * tone grows past it.
         */
        const val FOCUS_HOLD_MILLIS = 600L

        /** `SoundPool`'s own priority argument, documented as having no effect today. */
        const val SOUND_PRIORITY = 1

        /** `VibrationEffect.createWaveform`'s "do not repeat" index. */
        const val NO_REPEAT = -1

        /**
         * The raw resource each [ToneAsset] names. `R.raw.cue_work` does not compile unless
         * `cue_work.wav` is really in `res/raw`, so this `when` is also where a deleted asset
         * stops the build — and `CueEmissionTest` checks the other direction, that every name the
         * enum carries is a real, decodable file.
         */
        fun ToneAsset.rawResourceId(): Int = when (this) {
            ToneAsset.WORK -> R.raw.cue_work
            ToneAsset.RECOVERY -> R.raw.cue_recovery
            ToneAsset.TICK -> R.raw.cue_tick
            ToneAsset.FINISH -> R.raw.cue_finish
        }

        fun obtainVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                // Context.getSystemService(Class) is not deprecated; only the VIBRATOR_SERVICE
                // string constant is, and this overload avoids it.
                context.getSystemService(Vibrator::class.java)
            }
    }
}
