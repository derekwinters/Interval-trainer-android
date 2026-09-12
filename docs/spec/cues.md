# Specification — Cues (`CUE`)

What the app signals at each boundary of a running workout, in each of the three channels a cue
uses: a tone, a vibration, and the colour of the running screen.

A cue exists to be understood without looking. The user is running, the phone is in a pocket, and
the only question that matters mid-effort is whether to start working or stop working — so the
vocabulary here is deliberately small, and the same shape repeats at every boundary rather than
varying by kind.

This page fixes **what is signalled and when**. It does not fix **how the tones are produced**: the
cue sink is an interface `:core` defines and `:app` implements
([ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md)), the running workout and its
clock belong to the foreground service
([ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md)), and the platform
mechanics — `SoundPool`, `VibrationEffect`, focus requests — are recorded in
[`docs/research/cue-audio.md`](../research/cue-audio.md) and
[`docs/research/foreground-service.md`](../research/foreground-service.md).

Two things are deliberately out of scope. **Actual colour values** belong to the design system on
[#40](https://github.com/derekwinters/Interval-trainer-android/issues/40); this page fixes how many
there are and what each means. **The numeric values** — pitches, durations, pattern lengths — are
given in [§9](#9-starting-values-tunable-not-specified) as a starting point that is explicitly not a
requirement.

Every decision here was taken on
[#30](https://github.com/derekwinters/Interval-trainer-android/issues/30) between 2026-09-10 and
2026-09-12, except in §5, where the minimum interval and the skip countdown were settled on
[#28](https://github.com/derekwinters/Interval-trainer-android/issues/28) and are specified in
[`timer.md`](timer.md); the interval kinds are the glossary's, in
[`CONTEXT.md`](../../CONTEXT.md).

---

## Invariants

> **Invariant — the finish cue is never mistakable for a boundary cue.** Hearing a boundary cue when
> the workout has actually ended means standing in the road waiting for a signal that will never
> come. This is the worst failure the cue system can produce, and it is why the finish differs in
> *shape* — a different number of events, a different contour — rather than being a boundary cue at
> another pitch.

> **Invariant — vibration carries every distinction the tones carry.** Mute silences tones and leaves
> vibration, so in a muted workout vibration is not a supplement to the audio: it is the only channel
> carrying information. A uniform buzz would tell a muted user that *something* happened and nothing
> more, which would make the muted mode meaningfully worse than the unmuted one.

> **Invariant — no interval kind is distinguishable by hue alone.** The interval's name is what
> carries the meaning and the colour reinforces it. The contrast requirement therefore falls on text
> against each background, not on the backgrounds against one another.

> **Invariant — the app never inspects Do Not Disturb, ringer mode, media volume, or headset
> connection state in order to decide what a cue does.** Every cue is emitted the same way in every
> condition, and what the user then hears is the platform's decision. The tempting implementation —
> detect the silent case and compensate for it, by warning, by overriding, by auto-muting, by
> pausing — is excluded here rather than argued about later.

> **Invariant — cue selection is pure and lives in `:core`.** The function from a boundary and the
> current mute state to the tone, the vibration and the colour role is arithmetic over the schedule
> and nothing else. Emission — sound, haptics, focus — is `:app`'s, on the far side of the cue-sink
> interface. A selection rule implemented inside the tone adapter is a rule no JVM test can reach.

One further invariant governs cues and is recorded elsewhere, because it constrains the service
rather than this vocabulary: **the cue sink consults mute state at the moment a cue fires, never at
workout start.** It is stated in
[ADR 0002](../adr/0002-the-foreground-service-owns-the-running-workout.md) and is not restated here.

---

## 1. What a cue is

- **CUE-001** A cue occurs at exactly four kinds of moment: the start of the workout, the start of
  each interval, each countdown second, and the completion of the workout. Nothing else in the app
  emits one.
- **CUE-002** Every cue fires a tone and a vibration together, both selected from the same boundary,
  so the two channels can never disagree about what just happened.
- **CUE-003** Selection is a pure function in `:core`; emission is `:app`'s, behind the cue-sink
  interface. *(manual: a module boundary, made a compile error by `:core`'s Kotlin JVM plugin per
  ADR 0005, not something a test of this page asserts.)*

## 2. Tones

The vocabulary is two boundary tones, a tick and a finish. Two rather than four because the only
distinction that matters while moving is binary — start working, or stop working. A tone per
interval kind would ask the user to learn four sounds to convey information context already
supplies, and warm-up and cool-down happen at the ends of a session, where the phone is far more
likely to be in hand.

- **CUE-010** A **work tone** sounds at the start of a work interval.
- **CUE-011** A **recovery tone** sounds at the start of a recovery interval, and also at the start
  of warm-up and at the start of cool-down. Both of those mean "not working yet" or "not working any
  more", which is what the recovery tone already says.
- **CUE-012** A **countdown tick** sounds once per countdown second, and is never one of the boundary
  tones.
- **CUE-013** A **finish tone** sounds at workout completion, and is none of the three above.
- **CUE-014** The finish tone differs from the boundary tones in shape rather than in pitch alone.
  *(manual: a judgement about a sound, verified by listening to it.)*
- **CUE-015** The ticks are shorter, quieter and plainly different in character from a boundary tone,
  so three ticks and the tone that follows are heard as one event with a run-up rather than as four
  separate events. *(manual: as CUE-014.)*
- **CUE-016** The tone vocabulary is exactly these four. Selection never produces a fifth.

## 3. Vibration

Vibration mirrors the tones, pattern for tone. Mirroring costs nothing — a pattern is a list of
durations — and it is what makes a muted workout carry the same information as an unmuted one.

The honest limit, recorded so the patterns are designed within it: vibration is a coarse channel, and
two pulses against one is close to the most a person can reliably tell apart through a pocket while
moving. That is a reason to hold the vocabulary to the binary already chosen, not a reason to
abandon the distinction.

- **CUE-020** Every cue fires a vibration, and mute does not suppress it.
- **CUE-021** Work start and recovery start have **distinct patterns**, differing in pulse count.
  Warm-up start and cool-down start use the recovery pattern, exactly as they use the recovery tone.
- **CUE-022** The countdown tick and the finish have their own patterns, and the finish pattern is
  neither of the boundary patterns.
- **CUE-023** The pattern vocabulary is exactly these four. Selection never produces a fifth, and no
  third boundary pattern is added.
- **CUE-024** Every vibration the app emits carries an alarm usage — `VibrationAttributes.USAGE_ALARM`
  on API 33 and above, and the `AudioAttributes.USAGE_ALARM` overload below it. The platform drops
  vibrations from a UID it considers background unless the usage is on its allowlist, and that same
  allowlist is what survives battery saver; a foreground service is not documented as satisfying the
  "should be in the foreground" test, so the attributes route is the one that is documented to work.
  *(manual: an emission fact, per [`cue-audio.md`](../research/cue-audio.md) and
  [`foreground-service.md`](../research/foreground-service.md); verified on a device with the screen
  off.)*

## 4. Colour

The screen does not copy the binary the audio and vibration carry. When the user is looking, the
extra bandwidth is free — but only where it buys something, which is why warm-up and cool-down share
one colour rather than having two of their own.

- **CUE-030** Each interval kind maps to a colour role: **work → green**, **recovery → yellow**,
  **warm-up → a neutral**, **cool-down → the same neutral**. Green and yellow are the most visually
  distant pair, because a glance must answer "am I working?" before anything else is read.
- **CUE-031** Red is not used. It was the colour associated with being stopped, and there is no
  stopped kind: recovery covers the low-effort interval whether the person walks through it or
  stands still. *(manual: absence from the palette, which is #40's to define.)*
- **CUE-032** No kind is distinguishable by hue alone. The interval's name is displayed alongside its
  colour and is what carries the meaning. *(manual: a rendering fact, checked on the running screen.)*
- **CUE-033** Text meets the contrast requirement against each of the three backgrounds. The
  requirement is on text against background, not on the backgrounds against each other. *(manual: a
  design-system check on #40.)*
- **CUE-034** Actual colour values, including the neutral's hue, are not specified here. They belong
  to the design system on
  [#40](https://github.com/derekwinters/Interval-trainer-android/issues/40). *(manual: absence.)*
- **CUE-035** Cue colours are **not** configurable in v1, and no colour-token indirection is built
  for a configurability that has not been scoped. *(manual: a scope boundary; it would show in the
  diff.)*

## 5. The countdown

The pattern is always the same — three ticks, then a tone that says what changed. A countdown that
appeared before some boundaries and not others would force the listener to work out which kind of
interval they were in before they could interpret what they had just heard, which is the opposite of
what a cue is for. It is the same three ticks at every one of them: before each interval, before the
first one when the workout starts, and before the next one after a skip.

- **CUE-040** Every interval is counted down over its final three seconds, one tick per second,
  whatever its kind.
- **CUE-041** The start of the workout is counted down too: starting gives three ticks before the
  first interval begins. It is the one moment the user is definitely holding the phone, and three
  seconds is the difference between starting a run and fumbling a phone into a pocket while the first
  interval burns.
- **CUE-042** A countdown never truncates: **every interval gets all three ticks**. This is not a
  rule cue selection enforces but a consequence of the **minimum interval**, which is what
  guarantees a countdown fits inside the interval it counts down — the number is
  [`timer.md`](timer.md)'s (`TIMER-070`, `TIMER-071`) and is not restated here. No interval short
  enough to truncate a countdown can be authored, and zero-length intervals are excluded by the same
  minimum.
- **CUE-043** The last interval's countdown runs into the **finish cue**, not a boundary cue.
- **CUE-044** A tick and the cue that follows it are consecutive events one second apart, never
  simultaneous. This holds **by construction** rather than by a tie-break: the minimum interval
  exceeds the countdown by two seconds (`TIMER-070`), so the earliest tick lands two seconds after
  an interval begins and no tick can ever fall on the same instant as a boundary cue. There is no
  collision to arbitrate and no rule deciding which of the two wins.
- **CUE-045** A **skip** is counted down too, because it gives the same three-second lead-in that
  starting the workout gives (`TIMER-031`, `TIMER-040`): three ticks, then the next interval's
  boundary cue. A countdown already running when the user skips is abandoned, and the lead-in's own
  countdown starts again from three.

## 6. Mute

Mute is one control on the running screen. It silences tones and leaves vibration, because the most
likely reason to reach for it is being somewhere quiet — exactly the case where the vibration should
survive. Muting everything would leave the workout purely visual, which defeats the point of having
cues at all.

- **CUE-050** Mute silences tones. Vibration continues unchanged, and so does colour.
- **CUE-051** Effective mute belongs to the workout and is initialised from a default held in
  settings.
- **CUE-052** Toggling mute during a workout **never** writes back to the settings default. Changing
  the default is a deliberate act in settings, not a side effect of tapping mute mid-session.
- **CUE-053** Effective mute is discarded when the workout ends. The next workout starts from the
  default again.
- **CUE-054** There is **one** control, not separate sound and vibration toggles, and the running
  screen shows its current state. A fine distinction between channels belongs in settings, not on a
  screen used while moving. *(manual: a screen fact; the running screen's layout is #29 and #40.)*
- **CUE-055** A mute change takes effect on the very next cue, including one due moments later. This
  is the observable consequence of ADR 0002's invariant that the cue sink reads mute state at fire
  time; a value captured at workout start would make the control inert.

## 7. Audio attribution

- **CUE-060** Cue tones are attributed `AudioAttributes.USAGE_MEDIA` with
  `CONTENT_TYPE_SONIFICATION` — media, because that is what binds the cue to the media volume slider
  the user already reaches for, and sonification because a cue tone is precisely "a sound used to
  accompany... an event". *(manual: an emission fact, per
  [`cue-audio.md`](../research/cue-audio.md).)*
- **CUE-061** The app requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` immediately before each cue and
  abandons the same request instance after it. The un-ducking of whatever else is playing is tied to
  the abandon, so holding focus between cues would leave a music player quiet for the whole workout.
  *(manual: as CUE-060.)*

Two notes on what the focus request does **not** guarantee, taken from the research rather than
decided here. The platform ducks automatically only for an app that itself took focus and is not
playing `CONTENT_TYPE_SPEECH`; a podcast or audiobook app is called back instead and may **pause**.
And an app targeting API 35 may request focus only while it is the top app or running a foreground
service, so `startForeground()` must have completed before the first request — which ADR 0002's
architecture already satisfies.

## 8. Silence the app does not fight

- **CUE-070** With media volume at zero the tone is silent, and the app does nothing to override it:
  it does not raise the user's volume and does not use `FLAG_AUDIBILITY_ENFORCED`, which is
  documented for regulatory sounds such as camera shutters and would be a misuse here. Vibration is
  the fallback channel, not a nicety. *(manual: absence of a call, plus a device check.)*
- **CUE-071** The app **never reads Do Not Disturb state**, never warns about it, and never requests
  notification policy access. Do Not Disturb is a setting the user turned on for a reason, and an
  interval timer has no business piercing it or second-guessing it. *(manual: absence of a call;
  visible in the diff.)*
- **CUE-072** Under total silence (`INTERRUPTION_FILTER_NONE`) both the tones **and** the vibration
  are suppressed by the platform, and the workout runs visually. This is accepted: a workout can run
  with no tones and no vibration at all, and the user finds out by noticing that nothing happened.
  *(manual: platform behaviour, verified on a device.)*
- **CUE-073** Headphones disconnecting mid-workout changes nothing. The workout does not pause, mute
  does not flip itself, and the app does not listen for the becoming-noisy broadcast at all. Cues
  follow wherever the system routes audio, so the accepted cost is that the phone may beep out loud
  in public — mitigated by mute being one tap away on a screen the user can already reach, and by
  vibration continuing regardless. *(manual: absence of a receiver, plus a device check.)*

Auto-muting on disconnection was rejected as silently changing a deliberate setting from a hardware
event, and auto-pausing as what a music player does: a workout is a timer, and losing your place in
a run because a cable moved has nothing to do with training.

## 9. Starting values (tunable, not specified)

**The numbers below are a starting point, not requirements.** They exist so that whoever implements
the cue sink is not blocked on a taste call, and they carry no identifier deliberately: nothing in
this section is pinned by a test, and **changing any of these values is not a specification change**.
What is specified is the structure above — how many tones there are, what each means, which is
subordinate to which. Fixing a pitch by decree before anything has been heard would be false
precision.

Tune them against the prototype. They are a coherent set that satisfies §2, §3 and §5, and that is
all they are.

| Cue | Tone | Vibration |
|---|---|---|
| Work start | 880 Hz, 250 ms, one note | two pulses of 120 ms, 100 ms apart |
| Recovery start | 440 Hz, 250 ms, one note | one pulse of 300 ms |
| Countdown tick | 660 Hz, 60 ms, at about a third the level of a boundary tone | one pulse of 40 ms |
| Workout finished | three rising notes — 660, 880, 1175 Hz — 120 ms each, back to back | three pulses of 150 ms, 100 ms apart |

Countdown spacing: ticks at three, two and one seconds before the boundary; the boundary cue at the
boundary itself.

Which of the two boundary patterns gets two pulses is itself part of the starting point. What was
decided is that they differ by pulse count, two against one; that work is the one with two is this
table's guess at which reads as the more urgent.

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| What a cue is | CUE-001–003 | `CueSelectionTest.kt` (CUE-001–002); *(manual)* CUE-003 |
| Tones | CUE-010–016 | `CueSelectionTest.kt` (CUE-010–013, 016); *(manual)* CUE-014–015 |
| Vibration | CUE-020–024 | `CueSelectionTest.kt` (CUE-020–023); *(manual)* CUE-024 |
| Colour | CUE-030–035 | `CueSelectionTest.kt` (CUE-030); *(manual)* CUE-031–035 |
| The countdown | CUE-040–045 | `CueSelectionTest.kt` |
| Mute | CUE-050–055 | `CueSelectionTest.kt` (CUE-050–053, 055); *(manual)* CUE-054 |
| Audio attribution | CUE-060–061 | *(manual)* |
| Silence the app does not fight | CUE-070–073 | *(manual)* |
| Starting values | *(none — see §9)* | *(not specified)* |

**39 requirements, 23 `auto` and 16 `manual`.**

**The `auto` tests do not exist yet.** There is no implementation of any of this, and no `:core`
module to hold one — the build today is a single `:app` module (`BUILD-002`). `CueSelectionTest.kt`
is the file those tests will live in when the cue selection is built, at
`core/src/test/kotlin/com/derekwinters/intervaltrainer/core/CueSelectionTest.kt`; it is named here so
they have one home rather than three, and it is named in the future tense on purpose. Nothing in
this page is covered today. A requirement marked `auto` is a promise that a JVM test *can* assert it
and *will*, not a claim that one does.

**Why the split falls where it does.** Everything above the line between selection and emission is
arithmetic: which cue fires at a given boundary, given the schedule, the elapsed time and the current
mute state, is a pure function of those inputs, and it is exactly what
[ADR 0005](../adr/0005-a-pure-jvm-core-and-a-thin-android-shell.md) puts in `:core` so a recording
cue sink can turn "a tick fires at each of the last three seconds" into an assertion on a list. A
fake clock makes a thirty-four-minute workout a millisecond of test time, so the long-workout cases
are as cheap as the short ones.

Everything below that line is not assertable on a JVM runner and is not pretended to be. No test can
hear that the finish tone is unmistakable, feel that the ticks are subordinate, confirm that a
vibration reached the actuator with the screen off, or measure the contrast of text the design system
has not yet chosen a colour for. Those are verified by a human, on a device, and keeping their number
small is the point of pushing everything else into `:core`. The sixteen are: the module boundary, two
judgements about how a sound and a haptic feel, the emission attributes for both channels, five facts
about a colour palette that belongs to another issue, the running screen's control, and the four
things the app deliberately does not do about silence — each of which is verified by an *absence* in
the diff and then a check on a device.
