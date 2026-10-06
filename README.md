# Interval-trainer-android
Android app for interval exercises

## Screenshots

| First run | Home | Preset editor |
| --- | --- | --- |
| <img src="docs/screenshots/01-first-run.png" width="240" alt="First-run screen"> | <img src="docs/screenshots/02-home.png" width="240" alt="Home, with the two example presets"> | <img src="docs/screenshots/03-editor.png" width="240" alt="Preset editor with Short Example loaded"> |

| Running, paused | Summary | Settings |
| --- | --- | --- |
| <img src="docs/screenshots/04-running-paused.png" width="240" alt="Running screen, paused mid-workout"> | <img src="docs/screenshots/05-summary.png" width="240" alt="Summary after stopping a workout"> | <img src="docs/screenshots/06-settings.png" width="240" alt="Settings"> |

These are captured from the real debug build on an Android emulator by
[`screenshots.yml`](.github/workflows/screenshots.yml), and kept current by the
`docs: update screenshots` pull request it opens when a screen changes
([`docs/spec/build.md`](docs/spec/build.md) §11).

## Building

`./gradlew test` runs the unit tests; `./gradlew assembleDebug` builds the debug APK. Nothing but a
JDK 17 needs to be installed — the Gradle wrapper and the Android Gradle Plugin fetch the rest. What
the build guarantees is specified in [`docs/spec/build.md`](docs/spec/build.md).

## How work is run here

This repository has adopted [ai-sdlc](https://github.com/derekwinters/ai-sdlc), which runs its
issues, labels, triage, pull-request gates and releases. What is installed, and at which version,
is in [`.ai-sdlc/adoption.md`](.ai-sdlc/adoption.md); the repository-specific rules for working
here are in [`CLAUDE.md`](CLAUDE.md).
