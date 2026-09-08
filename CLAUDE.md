# Interval-trainer-android

An Android app for interval exercises. Kotlin and Gradle are expected; there is no code and no
build in this repository yet.

## How work is run here

ai-sdlc governs this repository's issues, labels, milestones, triage, pull-request gates and
releases. The workflows under `.github/workflows/` are thin callers; the rules live in ai-sdlc.
The `ai-sdlc` skill at `.claude/skills/ai-sdlc/SKILL.md` explains where everything lives — read it
before touching any of those things or a document describing them. What is installed, and at
which version, is in `.ai-sdlc/adoption.md`.

All development is delegated to the `dev` agent at `.claude/agents/dev.md`: one issue, one branch,
one pull request. It writes the specification first, watches a test fail, then implements.

The test and verify commands live in `.ai-sdlc/repo-config.yml` under `commands:`. They are not
yet set, because there is no Gradle project; until they are, do not guess a test command.

No private links — session links, signed URLs, anything carrying a token — ever go into a commit,
a pull request, an issue or a comment. This repository is public, so each of those is a publication.

@.ai-sdlc/house-rules.md
