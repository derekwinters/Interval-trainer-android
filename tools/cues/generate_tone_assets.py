#!/usr/bin/env python3
"""Generate the four cue tone assets in ``app/src/main/res/raw`` from the numbers in
``docs/spec/cues.md`` §9.

Those numbers are a *starting point*, not a requirement — §9 says so explicitly, and says that
changing any of them is not a specification change. This script exists so that changing one is a
one-line edit and a re-run rather than an opaque binary someone has to reverse-engineer: the
committed ``.wav`` files are an output of this file, and the way to retune a cue is to edit ``TONES``
below and run it again.

Usage (from anywhere; the output path is resolved from this file's location):

    python3 tools/cues/generate_tone_assets.py            # write the assets
    python3 tools/cues/generate_tone_assets.py --check    # verify them, writing nothing

``--check`` regenerates each tone in memory and compares it with the committed file byte for byte,
exiting non-zero if any of them differs or is missing. That is the claim ``docs/spec/cues.md`` §9
makes about these binaries, and without this flag verifying it would mean overwriting the working
tree first and trusting ``git diff`` to be empty afterwards.

Standard library only — ``wave``, ``math``, ``struct``. No JDK, no Android SDK, no network.

What it produces: mono 16-bit PCM WAV at 44.1 kHz, one file per tone, each note windowed with a
short raised-cosine fade in and out so a note neither starts nor ends on a discontinuity. Without
that fade a 250 ms sine truncated mid-cycle clicks, and a click is exactly the kind of artefact that
makes a cue read as a glitch rather than a signal. The fade is also applied to each note of the
finish tone individually, which both removes the click between notes and gives the three-note run
its articulation.

``SoundPool`` pre-decodes whatever it is given into 16-bit PCM at load time
(``docs/research/cue-audio.md``), so shipping uncompressed PCM costs nothing at play time and keeps
the assets inspectable. Each file is a few tens of kilobytes; these are cues, not music.
"""

import io
import math
import os
import struct
import sys
import wave

SAMPLE_RATE_HZ = 44_100
"""44.1 kHz. Every pitch here is far below the Nyquist limit at any plausible rate; this is the rate
chosen because it is the one least likely to be resampled anywhere surprising."""

BOUNDARY_AMPLITUDE = 0.7
"""Peak amplitude of a boundary tone and of the finish, as a fraction of full scale. Headroom is
left rather than normalising to 1.0: the cue is mixed against whatever else the device is playing,
and the user's media volume is what sets the level (``CUE-060``, ``CUE-070``)."""

TICK_AMPLITUDE = BOUNDARY_AMPLITUDE / 3.0
"""``CUE-015``/§9: the countdown tick sounds at about a third the level of a boundary tone, so three
ticks and the tone after them are heard as one event with a run-up."""

FADE_MILLIS = 5.0
"""Raised-cosine fade in and out, per note. Long enough to remove the click, short enough that a
60 ms tick is still a tick."""

# docs/spec/cues.md §9, "Starting values (tunable, not specified)". Each entry is the raw resource
# name and the notes that make up the tone: (frequency in Hz, duration in milliseconds), played back
# to back. Edit these to retune; that is not a specification change.
TONES = {
    # Work start: 880 Hz, 250 ms, one note.
    "cue_work": ([(880.0, 250.0)], BOUNDARY_AMPLITUDE),
    # Recovery start (also warm-up and cool-down, CUE-011): 440 Hz, 250 ms, one note.
    "cue_recovery": ([(440.0, 250.0)], BOUNDARY_AMPLITUDE),
    # Countdown tick: 660 Hz, 60 ms, about a third the level of a boundary tone.
    "cue_tick": ([(660.0, 60.0)], TICK_AMPLITUDE),
    # Workout finished: three rising notes, 120 ms each, back to back. CUE-014's "differs in shape
    # rather than in pitch alone" is this row — three events against one, and rising.
    "cue_finish": ([(660.0, 120.0), (880.0, 120.0), (1175.0, 120.0)], BOUNDARY_AMPLITUDE),
}

OUTPUT_DIRECTORY = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app",
    "src",
    "main",
    "res",
    "raw",
)


def note_samples(frequency_hz, duration_millis, amplitude):
    """One sine note as a list of floats in [-1.0, 1.0], faded in and out."""
    total = int(round(SAMPLE_RATE_HZ * duration_millis / 1000.0))
    fade = min(int(round(SAMPLE_RATE_HZ * FADE_MILLIS / 1000.0)), total // 2)
    samples = []
    for index in range(total):
        value = math.sin(2.0 * math.pi * frequency_hz * index / SAMPLE_RATE_HZ)
        if fade > 0:
            if index < fade:
                value *= 0.5 - 0.5 * math.cos(math.pi * index / fade)
            elif index >= total - fade:
                remaining = total - 1 - index
                value *= 0.5 - 0.5 * math.cos(math.pi * remaining / fade)
        samples.append(amplitude * value)
    return samples


def wav_bytes(samples):
    """`samples` as a complete mono 16-bit PCM WAV file, as bytes."""
    frames = b"".join(
        struct.pack("<h", max(-32768, min(32767, int(round(sample * 32767.0)))))
        for sample in samples
    )
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE_HZ)
        output.writeframes(frames)
    return buffer.getvalue()


def tone_bytes(name):
    """The WAV file `TONES[name]` describes, synthesised in memory."""
    notes, amplitude = TONES[name]
    samples = []
    for frequency_hz, duration_millis in notes:
        samples.extend(note_samples(frequency_hz, duration_millis, amplitude))
    return wav_bytes(samples)


def main(argv):
    arguments = argv[1:]
    checking = arguments == ["--check"]
    if arguments and not checking:
        print("usage: generate_tone_assets.py [--check]", file=sys.stderr)
        return 2

    if not checking:
        os.makedirs(OUTPUT_DIRECTORY, exist_ok=True)

    mismatches = []
    for name, (notes, _) in sorted(TONES.items()):
        path = os.path.join(OUTPUT_DIRECTORY, name + ".wav")
        generated = tone_bytes(name)
        summary = "{} note(s), {:.0f} ms, {} bytes".format(
            len(notes), sum(duration for _, duration in notes), len(generated)
        )

        if not checking:
            with open(path, "wb") as output:
                output.write(generated)
            print("{}: {}".format(os.path.basename(path), summary))
            continue

        try:
            with open(path, "rb") as committed_file:
                committed = committed_file.read()
        except OSError as error:
            print("{}: MISSING ({})".format(os.path.basename(path), error.strerror))
            mismatches.append(name)
            continue

        if committed == generated:
            print("{}: matches ({})".format(os.path.basename(path), summary))
        else:
            print(
                "{}: DIFFERS — committed {} bytes, generated {} bytes".format(
                    os.path.basename(path), len(committed), len(generated)
                )
            )
            mismatches.append(name)

    if checking and mismatches:
        print(
            "\n{} of {} assets are not what this script produces: {}.\nEither re-run without "
            "--check to regenerate them, or find out who edited them by hand.".format(
                len(mismatches), len(TONES), ", ".join(sorted(mismatches))
            ),
            file=sys.stderr,
        )
        return 1

    if checking:
        print("\nAll {} assets match their generator, byte for byte.".format(len(TONES)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
