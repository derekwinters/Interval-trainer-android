# Cue tones, audio focus and vibration on Android 8.0 to 15

Research for issue #26. Scope: API 26 (Android 8.0) through API 35 (Android 15), producing a
short cue tone and a vibration from a foreground service while another app may be playing music
through headphones.

**Date checked: 2026-09-10.** Every claim below is followed to the developer.android.com page that
owns it, and the URL is given inline. Nothing here comes from a blog post or a forum answer.

**Source limitation.** AOSP source hosts (`android.googlesource.com`, `cs.android.com`,
`source.android.com`) are blocked by this environment's network egress proxy, so no claim below is
backed by platform source. Everything cited is the official reference or guide. Two questions that
only source could settle are listed under [Open questions](#open-questions).

---

## Answer

**Produce the tone with `SoundPool`, playing a short bundled PCM asset, built with explicit
`AudioAttributes` of `USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION`.** `SoundPool` pre-decodes the
asset into memory at load time, so `play()` does not pay decode cost or player-construction cost at
the moment the cue is due, and the reference describes it as offering "low-latency playback"
([SoundPool](https://developer.android.com/reference/android/media/SoundPool)). Load the asset once
when the service starts and wait for `OnLoadCompleteListener` before relying on it; `load()` is
asynchronous and `play()` returns `0` if the sound is not ready. Set `setMaxStreams` explicitly —
the builder's default is **1**
([SoundPool.Builder](https://developer.android.com/reference/android/media/SoundPool.Builder)).

Reject the alternatives: `ToneGenerator` takes a **legacy stream type only** and has no
`AudioAttributes` overload, so it cannot be attributed the way the rest of the app's audio is, and
its documented purpose is DTMF and telephony supervisory tones
([ToneGenerator](https://developer.android.com/reference/android/media/ToneGenerator)).
`MediaPlayer` has to be prepared per sound and carries a documented multi-state lifecycle — wrong
shape for a cue that must fire on a schedule
([MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer)). `AudioTrack` in
`MODE_STATIC` with `PERFORMANCE_MODE_LOW_LATENCY` is the lowest-latency option and is the right
answer only if measurement shows `SoundPool` is not tight enough
([AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)).

**Audio focus: request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` via `AudioFocusRequest` and abandon it
after the cue.** On Android 8.0 and above the platform performs the ducking itself — the app does
not implement it and the ducked app is not even notified
([AudioFocusRequest](https://developer.android.com/reference/android/media/AudioFocusRequest),
[Manage audio focus](https://developer.android.com/media/optimize/audio-focus)). But the guarantee
is narrower than it looks. Automatic ducking happens only if the other app itself successfully
requested audio focus, and the platform deliberately **will not** duck a player whose content type
is `CONTENT_TYPE_SPEECH` — podcasts and audiobooks get a `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`
callback instead and decide for themselves whether to duck or pause. An app can also opt out of
ducking with `setWillPauseWhenDucked(true)`. So: **the platform guarantees ducking for a
well-behaved music player, and guarantees nothing for a speech app or an app that never took
focus.** Design the cue so a pause is acceptable, not just a duck.

**Android 15 (API 35) constrains the request itself:** an app targeting API 35 or higher can only
request focus while it is the top app **or running a foreground service**, otherwise
`requestAudioFocus` returns `AUDIOFOCUS_REQUEST_FAILED`
([Behavior changes: Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)).
This app is a foreground service, so it qualifies — but the foreground service must actually be
running before the focus request, not started alongside it.

**Media volume at zero means silence, and there is no legitimate override.** `USAGE_MEDIA` binds
the cue to the media volume slider; the only documented escape hatch, `FLAG_AUDIBILITY_ENFORCED`,
is explicitly reserved for regulatory sounds like camera shutters and restricts routing to built-in
speakers and wired headphones — it is not the mechanism for this
([AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes)). A user
with media volume at zero will hear nothing. **The vibration is therefore the fallback channel, not
a nice-to-have**, and the UI should say so.

**Do Not Disturb:** only "total silence" (`INTERRUPTION_FILTER_NONE`) is documented to mute
everything — "all audio streams (except those used for phone calls) and vibrations are muted"
([NotificationManager](https://developer.android.com/reference/android/app/NotificationManager)).
Under the priority filter, media is a **user-configurable** category
(`Policy.PRIORITY_CATEGORY_MEDIA`, API 28), so behaviour depends on the user's DND settings.
Ringer silent/vibrate mode is documented in terms of the ringer, not the media stream
([AudioManager](https://developer.android.com/reference/android/media/AudioManager)).

**Vibration: `VibrationEffect` covers the whole range** (API 26, matching minSdk 26). On API 31+
get the vibrator from `VibratorManager`; below that from the deprecated `Context.VIBRATOR_SERVICE`.
Only the `android.permission.VIBRATE` normal permission is needed
([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission)). **The
important constraint:** the reference states "The app should be in the foreground for the vibration
to happen. Background apps should specify a ringtone, notification or alarm usage in order to
vibrate" ([Vibrator](https://developer.android.com/reference/android/os/Vibrator)). A foreground
service is not documented as "foreground" for this purpose, so **always pass attributes** — a
`VibrationAttributes` with an alarm usage on API 33+, or the deprecated
`AudioAttributes`-taking overload below that.

---

## Tone production options

### ToneGenerator

Source: <https://developer.android.com/reference/android/media/ToneGenerator>

- Documented purpose is narrow: "This class provides methods to play DTMF tones (ITU-T
  Recommendation Q.23), call supervisory tones (3GPP TS 22.001, CEPT) and proprietary tones (3GPP
  TS 31.111). Depending on call state and routing options, tones are mixed to the downlink audio or
  output to the speaker phone or headset."
- Single constructor, added in API level 1:
  `public ToneGenerator (int streamType, int volume)`. The parameter documentation says
  "`streamType`: The streame type used for tone playback (e.g. STREAM_MUSIC)" and "`volume`: The
  volume of the tone, given in percentage of maximum volume (from 0-100)."
- **There is no `AudioAttributes` overload and no setter.** The class is attributed by legacy
  stream type only. `AudioAttributes` "supersede the notion of stream types (see for instance
  `AudioManager.STREAM_MUSIC` or `AudioManager.STREAM_ALARM`) for defining the behavior of audio
  playback" (<https://developer.android.com/reference/android/media/AudioAttributes>), so
  `ToneGenerator` is the one option here that cannot express the app's intent in the current model.
- Only one tone at a time per instance: "only one tone can play at a time: if a tone is playing
  while this method is called, this tone is stopped and replaced by the one requested."
- `startTone(int toneType, int durationMs)` (API 5) plays for a bounded duration; `release()`
  "Releases resources associated with this ToneGenerator object. It is good practice to call this
  method when you're done using the ToneGenerator."
- No latency figure is documented anywhere on the page.

**Verdict:** rejected. Cannot be given `AudioAttributes`; the tone set is telephony-shaped.

### SoundPool with a bundled asset

Sources: <https://developer.android.com/reference/android/media/SoundPool>,
<https://developer.android.com/reference/android/media/SoundPool.Builder>

- Decode happens at load, not at play: "The SoundPool library uses the MediaCodec service to decode
  the audio into raw 16-bit PCM. This allows applications to ship with compressed streams without
  having to suffer the CPU load and latency of decompressing during playback."
- Explicitly for short sounds: "Soundpool sounds are expected to be short as they are predecoded
  into memory. Each decoded sound is internally limited to one megabyte storage, which represents
  approximately 5.6 seconds at 44.1kHz stereo (the duration is proportionally longer at lower
  sample rates or a channel mask of mono). A decoded audio sound will be truncated if it would
  exceed the per-sound one megabyte storage space." A cue tone is far inside this.
- Latency: "In addition to low-latency playback, SoundPool can also manage the number of audio
  streams being rendered at once." No numeric figure is given.
- Loading is asynchronous and must be done ahead of time: "This should typically be done early in
  the process to allow time for decompressing the audio to raw PCM format before they are needed
  for playback." Use `setOnLoadCompleteListener(SoundPool.OnLoadCompleteListener)` to know when the
  sound ID is usable.
- `play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate)`
  "Returns a non-zero streamID if successful, zero if it fails."
- **Builder defaults matter:** "If not provided, the maximum number of streams is 1 (see
  `setMaxStreams(int)` to change it), and the audio attributes have a usage value of
  `AudioAttributes.USAGE_MEDIA` (see `setAudioAttributes(AudioAttributes)` to change them)." The
  default usage happens to be the one already decided for this app, but it should be set explicitly
  anyway so the content type can be set too.
- Stream stealing is by priority then age: "If the maximum number of streams is exceeded, SoundPool
  will automatically stop a previously playing stream based first on priority and then by age
  within that priority."
- `release()` "release all the native resources in use and then set the SoundPool reference to
  null."

**Verdict:** the recommended option. Pre-decoded, attributable, built for exactly this shape of
sound.

### MediaPlayer

Source: <https://developer.android.com/reference/android/media/MediaPlayer>

- Accepts `setAudioAttributes(AudioAttributes)`, so attribution is fine.
- The cost is the lifecycle. The reference documents a full state machine (Idle, Initialized,
  Preparing, Prepared, Started, Paused, Stopped, PlaybackCompleted, Error) with per-method valid and
  invalid states; calling a method in an invalid state moves the object to `Error`.
- Preparation is a distinct step: `prepare()` transfers "to the `Prepared` state once the method
  call returns", or `prepareAsync()` "first transfers the object to the `Preparing` state after the
  call returns (which occurs almost right away) while the internal player engine continues working
  on the rest of preparation work until the preparation work completes", signalling via
  `OnPreparedListener`.
- Relevant to screen-off: `setWakeMode(Context context, int mode)` — "This function has the
  MediaPlayer access the low-level power manager service to control the device's power usage while
  playing is occurring... Use of this method requires `Manifest.permission.WAKE_LOCK` permission. By
  default, no attempt is made to keep the device awake during playback."
- No latency figure documented.

**Verdict:** workable but heavier than needed. A `MediaPlayer` held prepared and reset per cue is
more state to get wrong than a `SoundPool` sound ID, for no documented latency advantage.

### AudioTrack

Source: <https://developer.android.com/reference/android/media/AudioTrack>

- Constructed with `AudioAttributes` directly:
  `AudioTrack(AudioAttributes, AudioFormat, int, int, int)`.
- The reference names static mode as the low-latency short-sound path: "The static mode should be
  chosen when dealing with short sounds that fit in memory and that need to be played with the
  smallest latency possible. The static mode will therefore be preferred for UI and game sounds
  that are played often, and with the smallest overhead possible."
- `PERFORMANCE_MODE_LOW_LATENCY` — "Low latency performance mode for an AudioTrack", set with
  `AudioTrack.Builder.setPerformanceMode(int)`. The older `AudioAttributes.FLAG_LOW_LATENCY` is
  "deprecated in API level 26. Use `AudioTrack.Builder.setPerformanceMode(int)` with
  `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY` to control performance."
  (<https://developer.android.com/reference/android/media/AudioAttributes>)
- Cost: the app must supply raw PCM and manage buffer sizing itself
  (`getMinBufferSize(int, int, int)`).

**Verdict:** the escape hatch. Use if and only if measured `SoundPool` latency is unacceptable;
carries the PCM-generation and buffer-management burden in exchange.

### Reliability from a service with the screen off

- A foreground service is the documented mechanism for background playback. The `mediaPlayback`
  foreground service type is described as "Continue audio or video playback from the background",
  declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in the manifest, passes
  `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` to `startForeground()`, and has no runtime prerequisites
  (<https://developer.android.com/develop/background-work/services/fgs/service-types>). `specialUse`
  is the catch-all and requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property plus Play
  Console review, so it is the worse choice if `mediaPlayback` fits.
- **The real screen-off risk is scheduling, not playback.** Doze restrictions are listed as:
  "Suspends network access. **Ignores wake locks.** Defers standard `AlarmManager` alarms, including
  `setExact()` and `setWindow()`, to the next maintenance window... Doesn't let `JobScheduler` run."
  The documented exceptions are `setAndAllowWhileIdle()`, `setExactAndAllowWhileIdle()`, and
  `setAlarmClock()` — "Alarms set with `setAlarmClock()` continue to fire normally. The system exits
  Doze shortly before those alarms fire."
  (<https://developer.android.com/training/monitoring-device-state/doze-standby>) An interval timer
  that relies on a wake lock or a plain `setExact()` alarm to know when the next cue is due can drift
  or miss under Doze. **How the cue is scheduled is a separate design question from how it is
  produced, and this research does not settle it.**
- Nothing on any page consulted says a foreground service is prevented from starting audio playback
  while the screen is off.

---

## AudioAttributes and media volume

Source: <https://developer.android.com/reference/android/media/AudioAttributes>

- Attributes replace stream types: they "supersede the notion of stream types (see for instance
  `AudioManager.STREAM_MUSIC` or `AudioManager.STREAM_ALARM`) for defining the behavior of audio
  playback."
- Usage is the load-bearing field: usage is "'why' you are playing a sound... Usage information is
  more expressive than a stream type, and allows certain platforms or routing policies to use this
  information for more refined volume or routing decisions. Usage is the most important information
  to supply in `AudioAttributes`."
- **Recommended for the cue:**
  - `setUsage(AudioAttributes.USAGE_MEDIA)` — "Usage value to use when the usage is media, such as
    music, or movie soundtracks." This is the already-decided attribution and is what binds the cue
    to media volume.
  - `setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)` — "Content type value to use when
    the content type is a sound used to accompany a user action, such as a beep or sound effect
    expressing a key click, or event, such as the type of a sound for a bonus being received in a
    game." That is precisely a cue tone. Content type "is optional. But in case it is known... this
    information might be used by the audio framework to selectively configure some audio
    post-processing blocks."
  - Deliberately **not** `CONTENT_TYPE_SPEECH` — see the audio focus section, where speech content
    changes ducking behaviour.
- **Confirming the volume binding:** `AudioAttributes.getVolumeControlStream()` (API 26) "Returns
  the stream type matching this `AudioAttributes` instance for volume control. Use this method to
  derive the stream type needed to configure the volume control slider in an `Activity` with
  `Activity.setVolumeControlStream(int)` for playback conducted with these attributes." This is the
  documented API for asking the platform which volume slider a given set of attributes follows; the
  app's UI should use it rather than hard-coding `STREAM_MUSIC`.
- Do **not** mix `setLegacyStreamType` with the modern setters: "Warning: do not use this method in
  combination with setting any other attributes such as usage, content type, flags or haptic
  control, as this method will overwrite (the more accurate) information describing the use case
  previously set in the `Builder`. In general, avoid using it and prefer setting usage and content
  type directly with `setUsage(int)` and `setContentType(int)`."
  (<https://developer.android.com/reference/android/media/AudioAttributes.Builder>)
- Always set usage explicitly: "By default all types of information (usage, content type, flags)
  conveyed by an `AudioAttributes` instance are set to 'unknown'... It is recommended to configure
  the usage (with `setUsage(int)`)... before calling `build()` to override any default playback
  behavior in terms of routing and volume management."

### What media volume zero means

- `AudioManager.STREAM_MUSIC` is "Used to identify the volume of audio streams for music playback"
  and the volume index is readable with `getStreamVolume(int)`, bounded by `getStreamMinVolume(int)`
  and `getStreamMaxVolume(int)`
  (<https://developer.android.com/reference/android/media/AudioManager>). Nothing in the reference
  offers a way for an app to make a `USAGE_MEDIA` sound audible when the user has set that volume
  to its minimum.
- The one flag that sounds like an override is not one:
  `AudioAttributes.FLAG_AUDIBILITY_ENFORCED` — "Flag defining a behavior where the audibility of the
  sound will be ensured by the system. To ensure sound audibility, the system only uses built-in
  speakers or wired headphones and specifically excludes wireless audio devices. **Note this flag
  should only be used for sounds subject to regulatory behaviors in some countries, such as for
  camera shutter sound, and not for routing behaviors.**" Its documented effect is on *routing*, and
  its documented scope is regulatory sounds. It is not the answer to a quiet volume slider, and
  using it for a cue tone would be misuse.
- Apps can change the media volume themselves via `setStreamVolume`/`adjustStreamVolume`, but the
  reference warns this is "for applications that replace the platform-wide management of audio
  settings or the main telephony application" and that it "has no effect if the device implements a
  fixed volume policy as indicated by `isVolumeFixed()`". Raising the user's volume behind their
  back is not a legitimate design.

**Consequence for the app:** with media volume at zero the cue is silent, and nothing in the
platform changes that. The vibration is the only remaining channel. This should be surfaced in the
UI rather than left as a silent failure.

---

## Audio focus

Primary sources: <https://developer.android.com/reference/android/media/AudioFocusRequest>,
<https://developer.android.com/reference/android/media/AudioFocusRequest.Builder>,
<https://developer.android.com/reference/android/media/AudioManager>,
<https://developer.android.com/media/optimize/audio-focus>

### The API on 8.0 and above

- `AudioFocusRequest` was added in API level 26, exactly the app's minSdk. From the guide:
  "Beginning with Android 8.0 (API level 26), when you call `requestAudioFocus()` you must supply an
  `AudioFocusRequest` parameter... To release audio focus, call the method
  `abandonAudioFocusRequest()` which also takes an `AudioFocusRequest` as its argument. **Use the
  same `AudioFocusRequest` instance both when you request and abandon focus.**"
- `AudioManager.requestAudioFocus(AudioFocusRequest)` returns one of
  `AUDIOFOCUS_REQUEST_FAILED` (0), `AUDIOFOCUS_REQUEST_GRANTED` (1), or `AUDIOFOCUS_REQUEST_DELAYED`
  (2, API 26).
- `abandonAudioFocusRequest(AudioFocusRequest focusRequest)` (API 26): "Abandon audio focus. Causes
  the previous focus owner, if any, to receive focus."
- The request must carry attributes: "Any focus request is qualified by the `AudioAttributes`... It
  is recommended to use the same `AudioAttributes` for the request as the attributes you are using
  for audio/media playback. If no attributes are set, default attributes of
  `AudioAttributes.USAGE_MEDIA` are used." Pass the same attributes object built for the
  `SoundPool`.
- **Do not play before the grant:** "Note: applications should not play anything until granted
  focus." The guide restates this: "Call `requestAudioFocus()` immediately before starting to play
  and verify that the call returns `AUDIOFOCUS_REQUEST_GRANTED`."
- `Builder.build()` "Throws `IllegalStateException` thrown when attempting to build a focus request
  that is set to accept delayed focus, or to pause on duck, but no focus change listener was set."
  So a listener is only *required* if `setAcceptsDelayedFocusGain(true)` or
  `setWillPauseWhenDucked(true)` is used — but registering one anyway is how the app learns it lost
  focus mid-workout.

### What AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK means

From `AudioFocusRequest`: "`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`: this focus request
type is similar to `AUDIOFOCUS_GAIN_TRANSIENT` for the temporary aspect of the focus request, but it
also expresses the fact during the time you own focus, you allow another application to keep playing
at a reduced volume, 'ducked'. Examples are when playing driving directions or notifications, it's
ok for music to keep playing, but not loud enough that it would prevent the directions to be hard to
understand. A typical attenuation by the 'ducked' application is a factor of 0.2f (or -14dB)."

### What the platform guarantees, and what it does not

**Guaranteed since Android 8.0:** "When an application requested audio focus with
`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, the system will duck the current focus owner.
Note: this behavior is **new for Android O**, whereas applications targeting SDK level up to API 25
had to implement the ducking themselves when they received a focus loss of
`AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`." The guide adds: "By having the system implement
ducking, you don't have to implement ducking in your app."

**Not guaranteed.** The guide sets out exactly when automatic ducking happens
(<https://developer.android.com/media/optimize/audio-focus>). All of these must hold:

> The first, currently-playing app meets all of these criteria:
> - The app successfully requested audio focus with any type of focus gain.
> - The app is not playing audio with content type `AudioAttributes.CONTENT_TYPE_SPEECH`.
> - The app did not set `AudioFocusRequest.Builder.setWillPauseWhenDucked(true)`.
>
> A second app requests audio focus with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.

So the cue's ducking fails to be automatic in three documented cases:

1. **The other app never requested focus.** A player that just calls `play()` without taking focus
   is not tracked as the focus owner and is not ducked. Nothing forces it to; the guide is blunt
   about this for pre-Android-12 devices: "Before Android 12 (API level 31), audio focus is not
   managed by the system. So, while app developers are encouraged to comply with the audio focus
   guidelines, if an app continues to play loudly even after losing audio focus on a device running
   Android 11 (API level 30) or lower, the system can't prevent it."
2. **The other app is playing speech.** From `AudioFocusRequest`: "the system will not automatically
   duck when it detects it would be ducking spoken content: such content is detected when the
   `AudioAttributes` of the player are qualified by `AudioAttributes.CONTENT_TYPE_SPEECH`... Since
   the system will not automatically duck applications that play speech, it calls their focus
   listener instead to notify them of `AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`, so they can
   pause instead. Note that this behavior is independent of the use of `AudioFocusRequest`, but tied
   to the use of `AudioAttributes`." A podcast or audiobook app will therefore **pause**, not duck —
   and that is the platform working as designed, not a bug.
3. **The other app opted out.** "If your application requires pausing instead of ducking for any
   other reason than playing speech, you can also declare so with
   `Builder.setWillPauseWhenDucked(boolean)`, which will cause the system to call your focus
   listener instead of automatically ducking."

When ducking does happen, it is invisible to the other app: "the audio system ducks all the active
players of the first app while the second app has focus. When the second app abandons focus, it
unducks them. The first app is not notified when it loses focus, so it doesn't have to do anything."
This is why abandoning focus promptly after the cue matters — the un-ducking is tied to the abandon.

**There is no way to force it.** `AudioFocusRequest.Builder.setForceDucking(boolean)` exists but:
"Forcing ducking will only be honored when requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with an
`AudioAttributes` usage of `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY`, coming from an
accessibility service, and will be ignored otherwise."

### The Android 15 restriction

From <https://developer.android.com/about/versions/15/behavior-changes-15>: "Apps that target
Android 15 (API level 35) must be the top app or running a foreground service in order to request
audio focus. If an app attempts to request focus when it does not meet one of these requirements,
the call returns `AUDIOFOCUS_REQUEST_FAILED`." `AudioFocusRequest` repeats it. Since targetSdk will
be 35, this applies. The app satisfies it via its foreground service, but the ordering is load
bearing: `startForeground()` must have completed before the focus request.

### Related behaviours worth knowing

- **Delayed focus.** "Audio focus can be 'locked' by the system for a number of reasons: during a
  phone call, when the car to which the device is connected plays an emergency message... the
  application can request to be notified when its request is fulfilled, by flagging its request as
  accepting delayed focus, with `Builder.setAcceptsDelayedFocusGain(boolean)`." For a cue tone tied
  to a specific moment in a workout, a *delayed* grant is arguably worse than no grant — the tone
  would sound at the wrong time. Probably leave this off.
- **Fade-out on Android 12+.** The guide describes the system forcing a fade-out of the previous
  owner, but only when the second app requests `AUDIOFOCUS_GAIN` (not `..._MAY_DUCK`), so it does
  not apply to this app's request.

---

## Do Not Disturb and silent mode

### Interruption filters

Source: <https://developer.android.com/reference/android/app/NotificationManager>

- `INTERRUPTION_FILTER_NONE` — "No interruptions filter - all notifications are suppressed and **all
  audio streams (except those used for phone calls) and vibrations are muted**." This is the only
  filter documented to mute media audio and vibration outright. Under total silence the cue tone is
  silent **and** the vibration is silent.
- `INTERRUPTION_FILTER_ALARMS` — "Alarms only interruption filter - all notifications except those
  of category `Notification.CATEGORY_ALARM` are suppressed." Note this constant is documented in
  terms of *notifications*, not audio streams.
- `INTERRUPTION_FILTER_PRIORITY` — "Priority interruption filter - all notifications are suppressed
  except those that match the priority criteria." Again documented in terms of notifications, with
  the criteria coming from `NotificationManager.Policy`.
- `INTERRUPTION_FILTER_ALL` — "Normal interruption filter - no notifications are suppressed."
- The app can read the current state with `getCurrentInterruptionFilter()` and observe
  `ACTION_INTERRUPTION_FILTER_CHANGED` ("Intent that is broadcast when the state of
  `getCurrentInterruptionFilter()` changes"), which is enough to warn the user that a cue may not be
  heard.

### Media is a user-controlled DND category

Source: <https://developer.android.com/reference/android/app/NotificationManager.Policy>

- `PRIORITY_CATEGORY_MEDIA` (added API level 28) — "Media, game, voice navigation are prioritized".
- `PRIORITY_CATEGORY_ALARMS` (API 28) — "Alarms are prioritized".
- `PRIORITY_CATEGORY_SYSTEM` (API 28) — "System (catch-all for non-never suppressible sounds) are
  prioritized".

The existence of `PRIORITY_CATEGORY_MEDIA` establishes that under the priority filter, whether media
sound is muted is **a user setting**, not a fixed platform behaviour. A media-attributed cue is
therefore audible under DND priority mode if and only if the user has allowed media. The
developer-facing reference does not state the platform default for that toggle — see
[Open questions](#open-questions).

### Ringer silent and vibrate modes

Source: <https://developer.android.com/reference/android/media/AudioManager>

- `RINGER_MODE_SILENT` — "Ringer mode that will be silent and will not vibrate. (This overrides the
  vibrate setting.)"
- `RINGER_MODE_VIBRATE` — "Ringer mode that will be silent and will vibrate."
- `RINGER_MODE_NORMAL` — "Ringer mode that may be audible and may vibrate."
- `setRingerMode(int)` — "Silent mode will mute the volume and will not vibrate. Vibrate mode will
  mute the volume and vibrate. Normal mode will be audible and may vibrate according to user
  settings." Also: "From N onward, ringer mode adjustments that would toggle Do Not Disturb are not
  allowed unless the app has been granted Notification Policy Access."
- The media stream is a separate axis: `STREAM_MUSIC` is "Used to identify the volume of audio
  streams for music playback", distinct from `STREAM_RING`, `STREAM_NOTIFICATION`, `STREAM_SYSTEM`,
  `STREAM_ALARM`, `STREAM_VOICE_CALL` and `STREAM_DTMF`. The ringer-mode documentation speaks to the
  ringer, and no consulted page says silent mode mutes `STREAM_MUSIC`.

**Practical reading, stated with its uncertainty:** a `USAGE_MEDIA` cue is expected to remain
audible in ringer-silent mode, because ringer mode governs the ringer and notification streams
rather than media — that is why music keeps playing on a phone set to silent. This is a reading of
what the reference does and does not say, not a sentence anyone at Google wrote. See
[Open questions](#open-questions).

### Vibration under DND

- Under `INTERRUPTION_FILTER_NONE` vibrations are explicitly muted (quoted above).
- `VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY` (API 30) — "Flag requesting vibration effect
  to be played even under limited interruptions. **Only privileged apps can ignore user settings
  that limit interruptions, and this flag will be ignored otherwise.**"
  (<https://developer.android.com/reference/android/os/VibrationAttributes>) A normal app cannot
  bypass DND for vibration.

---

## Vibration

Sources: <https://developer.android.com/reference/android/os/Vibrator>,
<https://developer.android.com/reference/android/os/VibratorManager>,
<https://developer.android.com/reference/android/os/VibrationEffect>,
<https://developer.android.com/reference/android/os/VibrationAttributes>,
<https://developer.android.com/reference/android/content/Context>

### Permission

`android.permission.VIBRATE` — "Allows access to the vibrator. **Protection level: normal**", added
in API level 1 (<https://developer.android.com/reference/android/Manifest.permission>). Being a
normal permission it is granted at install time with no runtime prompt. Every `vibrate` overload and
`cancel()` is annotated "Requires `Manifest.permission.VIBRATE`".

### Getting a Vibrator, by API level

- **API 31+:** `Context.VIBRATOR_MANAGER_SERVICE` (added API 31) — "Use with `getSystemService(String)`
  to retrieve a `VibratorManager` for accessing the device vibrators, interacting with individual
  ones and playing synchronized effects on multiple vibrators." Then
  `VibratorManager.getDefaultVibrator()` — "Returns the default Vibrator for the device."
  `VibratorManager` itself is API 31 and notes: "If your process exits, any vibration you started
  will stop."
- **API 26–30:** `Context.VIBRATOR_SERVICE`, which is "deprecated in API level 31. Use
  `VibratorManager` to retrieve the default system vibrator."

A single version-gated helper covers both. `Vibrator.hasVibrator()` (API 11) — "True if the hardware
has a vibrator, else false" — should gate the whole feature; not every device has one.

### VibrationEffect by API level

`VibrationEffect` itself was added in **API level 26**, so it covers the entire supported range.

| API | Available |
| --- | --- |
| 26 | `VibrationEffect` class; `createOneShot(long milliseconds, int amplitude)`; `createWaveform(...)`; `DEFAULT_AMPLITUDE` (value `-1`, "The default vibration strength of the device") |
| 29 | `createPredefined(int effectId)`; `EFFECT_CLICK` (0), `EFFECT_DOUBLE_CLICK` (1), `EFFECT_TICK` (2), `EFFECT_HEAVY_CLICK` (5) |
| 30 | `VibrationAttributes`; `FLAG_BYPASS_INTERRUPTION_POLICY` |
| 31 | `VibratorManager`, `Vibrator.getId()` |
| 33 | `Vibrator.vibrate(VibrationEffect, VibrationAttributes)`; `VibrationAttributes.USAGE_ACCESSIBILITY` |

- `createOneShot` — "One shot vibrations will vibrate constantly for the specified period of time at
  the specified amplitude, and then stop." Pass `DEFAULT_AMPLITUDE` unless
  `Vibrator.hasAmplitudeControl()` ("True if the hardware can control the amplitude of the
  vibrations, otherwise false") says otherwise.
- `createPredefined` (API 29) — "Predefined effects are a set of common vibration effects that
  should be identical, regardless of the app they come from, in order to provide a cohesive
  experience for users across the entire device. They also may be custom tailored to the device
  hardware in order to provide a better experience than you could otherwise build using the generic
  building blocks." Better-feeling than a raw one-shot on 29+, but support is not universal: the
  reference notes that a negative result from the bulk query means "the system doesn't know whether
  all the effects are supported... there's no way to programmatically know whether a
  `vibrate(VibrationEffect)` call will successfully cause a vibration", and points to
  `areEffectsSupported(int)` for per-effect results.

### Can a foreground service vibrate with the screen off?

This is the finding that most affects the design. The `Vibrator` reference attaches a foreground
caveat to **every** vibrate overload:

- `vibrate(VibrationEffect vibe)` — "Vibrate with a given effect. **The app should be in the
  foreground for the vibration to happen.**"
- `vibrate(VibrationEffect vibe, VibrationAttributes attributes)` (API 33) — "**The app should be in
  the foreground for the vibration to happen. Background apps should specify a ringtone,
  notification or alarm usage in order to vibrate.**" The parameter doc adds: "For example, specify
  `VibrationAttributes.USAGE_ALARM` for alarm vibrations or `VibrationAttributes.USAGE_RINGTONE` for
  vibrations associated with incoming calls."
- `vibrate(VibrationEffect vibe, AudioAttributes attributes)` (API 21, deprecated in 33) — same
  wording, with `AudioAttributes.USAGE_ALARM` given as the example.

Nothing on the page says a *foreground service* counts as "in the foreground" for this test, and
"screen off" is not addressed at all. The documentation's own remedy is the attributes route:
**always call the attributes-taking overload with an alarm-class usage**, so the call falls under
the sentence that explicitly permits background apps to vibrate.

Concretely:

- **API 33+:** `vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))`
  (`createForUsage(int)` — "Creates a new `VibrationAttributes` instance with given usage").
- **API 26–32:** `vibrate(effect, audioAttributes)` with `AudioAttributes.USAGE_ALARM`, taking the
  method that is deprecated in 33 but is the only attributes-carrying option before it.

Note the tension with the audio decision: the *tone* is deliberately `USAGE_MEDIA` so it follows
media volume, while the *vibration* wants an alarm-class usage so it is permitted from the
background. These are different attribute systems (`AudioAttributes` for playback,
`VibrationAttributes` for haptics) and there is no requirement that they match. But the choice
should be a deliberate decision, not an accident — an alarm-usage vibration may behave differently
under DND than a media-usage one, and the platform documentation does not spell out the difference
for a non-privileged app. Flagged below.

---

## Open questions

These are things this research could **not** settle from the sources available. They are design
decisions or facts to verify on-device, and should not be guessed at.

1. **Does ringer silent mode mute a `USAGE_MEDIA` cue?** The reference documents ringer modes in
   terms of the ringer and documents `STREAM_MUSIC` as a distinct stream, but never states the
   interaction directly. The definitive answer lives in AOSP's `AudioService` /
   `AudioAttributes.SUPPRESSIBLE_USAGES` mapping, which this environment could not reach. **Verify
   on a device before relying on it.**

2. **What is the platform default for the DND "Media" priority category?**
   `Policy.PRIORITY_CATEGORY_MEDIA` establishes that it is user-controllable but the reference does
   not state the default. Needs on-device verification across at least a stock and a vendor build,
   since vendors customise DND settings.

3. **Does a foreground service satisfy the `Vibrator` "should be in the foreground" test?** The
   reference does not say. This research recommends sidestepping the question entirely by always
   passing an alarm-class usage, which the documentation explicitly permits for background apps —
   but if the team wants to know the actual rule, it needs `VibratorManagerService` source or an
   on-device test with the screen off.

4. **Which usage should the vibration declare?** `USAGE_ALARM` is the safest for reliably firing
   from the background, but it is a claim about the nature of the event that the user's DND and
   vibration-intensity settings will act on. `USAGE_MEDIA` and `USAGE_NOTIFICATION` are alternatives
   with different trade-offs. **This is a product decision, not a technical one, and is left open
   deliberately.**

5. **Is `SoundPool` latency good enough?** No numeric latency figure is published for `SoundPool`,
   `MediaPlayer` or `ToneGenerator`. The recommendation above rests on the documented architecture
   (pre-decoded PCM, no per-play preparation), not on a measured number. If the interval trainer
   needs the cue accurate to a few tens of milliseconds, that needs measuring on real hardware, and
   `AudioTrack` in `MODE_STATIC` with `PERFORMANCE_MODE_LOW_LATENCY` is the documented fallback.

6. **How is the cue scheduled?** Out of scope for issue #26, but it dominates screen-off
   reliability. Doze "ignores wake locks" and defers `setExact()` alarms to the maintenance window;
   only `setExactAndAllowWhileIdle()` and `setAlarmClock()` are documented to survive. Whatever
   drives the interval clock has to account for this, and that belongs in its own issue.

7. **Which foreground service type?** `mediaPlayback` is documented as "Continue audio or video
   playback from the background" and has no runtime prerequisites, which fits. Whether an interval
   trainer is honestly described as media playback for Play policy purposes — versus `specialUse`,
   which requires a written justification reviewed in the Play Console — is a judgement call for a
   human, not something to decide here.
