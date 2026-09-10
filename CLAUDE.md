# Interval-trainer-android

An Android app for interval exercises, built with Kotlin and Gradle. The build is specified in
[`docs/spec/build.md`](docs/spec/build.md); there is no app behaviour yet beyond the skeleton
that page describes.

## Building

`./gradlew test` runs the JVM unit tests and `./gradlew assembleDebug` builds the debug APK. Both
need the Android SDK, which the Gradle plugin fetches from Google's servers — an environment that
cannot reach them cannot build this project locally, and the `pr` workflow is then the only place
the Android build is exercised.

`python3 -m unittest discover -s .github/scripts/tests` runs the tests for the checks under
`.github/scripts/`, standard library only. They need no JDK, no Android SDK and no network, so they
run anywhere.

## How work is run here

ai-sdlc governs this repository's issues, labels, milestones, triage, pull-request gates and
releases. The workflows under `.github/workflows/` are thin callers; the rules live in ai-sdlc.
The `ai-sdlc` skill at `.claude/skills/ai-sdlc/SKILL.md` explains where everything lives — read it
before touching any of those things or a document describing them. What is installed, and at
which version, is in `.ai-sdlc/adoption.md`.

All development is delegated to the `dev` agent at `.claude/agents/dev.md`: one issue, one branch,
one pull request. It writes the specification first, watches a test fail, then implements.

The planning skills `wayfinder`, `grilling`, `domain-modeling`, `research`, `prototype` and
`setup-matt-pocock-skills` under `.claude/skills/` come from
[mattpocock/skills](https://github.com/mattpocock/skills). They were installed with
`gh skill install` at a tagged release and are kept current with `gh skill update`, which reads
the `metadata` block in each `SKILL.md`; ai-sdlc's `skills-update` workflow does not manage
them. `/setup-matt-pocock-skills` has not been run.

The test and verify commands live in `.ai-sdlc/repo-config.yml` under `commands:`. They are still
unset — wiring them to the Gradle build is its own issue — so until they are, use the commands
under *Building* above and do not guess anything else.

No private links — session links, signed URLs, anything carrying a token — ever go into a commit,
a pull request, an issue or a comment. This repository is public, so each of those is a publication.

@.ai-sdlc/house-rules.md
