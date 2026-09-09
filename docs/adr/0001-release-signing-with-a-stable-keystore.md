# 1. Release builds are signed with a stable, owned keystore

- **Status:** accepted
- **Date:** 2026-09-09
- **Decided by:** @derekwinters
- **Issue:** [#10](https://github.com/derekwinters/Interval-trainer-android/issues/10)
- **Specification:** [`docs/spec/signing.md`](../spec/signing.md)

## Context

A release build has to be signed with something, and until this was settled nothing in this
repository could produce a release APK at all. Issue #10 asked which key signs release output.

What makes the question one-way rather than adjustable is how Android treats a signing certificate.
It is the app's identity. An installed app will only accept an update signed by the *same*
certificate; an APK signed by anything else is refused at install time, with no override and no
merge. The only way to install it is to uninstall the existing app first, and uninstalling an
Android app deletes its data directory. Whatever the user had recorded is gone.

This app is distributed by sideloading. There is no Play Store account behind it, and therefore no
Play App Signing to re-sign uploads under a stable identity and no way for anyone to reset the key
if it is lost. **The key that signs the first release the user installs is the key every later
release must be signed with, forever, and there is no key recovery.**

## Decision

**Release builds are signed with a real, stable, owned keystore, created once and kept.**

The keystore and everything needed to use it reach a build as repository secrets:

| Secret | Holds |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | the alias of the signing key inside it |
| `ANDROID_KEY_ALIAS_PASSWORD` | that key's password |

They exist already; the keystore file itself lives with the owner, never in this repository and
never in a log.

The **certificate fingerprint** is a different kind of thing and is treated differently. It is
SHA-256 over the DER-encoded certificate, it ships inside every APK, and anyone holding a release
build already has it — so it is committed in the clear at
[`.github/release-cert-sha256.txt`](../../.github/release-cert-sha256.txt), where a gate can read it
without being handed anything that could sign anything.

That gate is [`.github/scripts/verify_release_signature.py`](../../.github/scripts/verify_release_signature.py).
It runs `apksigner verify --print-certs --verbose` over each built APK and fails unless every one
is signed by exactly the pinned certificate. Its own unit tests run on every pull request, so it is
proven before anything trusts it.

Debug builds are unaffected and keep the debug key: they are for development, they are never
published, and nothing upgrades into them.

## Alternatives considered

**Sign release builds with the Android debug key**, recorded as an ADR (as `chores-web-android`
does under its own ADR 0001). CI could then build installable APKs with no secret at all. Rejected:
the debug key is not stable in any meaningful sense — it is generated per developer machine and
regenerated when the local keystore expires or is lost — so a later release could easily be signed
by a *different* debug key, and every install would then have to be removed and rebuilt from
nothing. The convenience is real, but it is only survivable before anyone has data worth keeping,
and it converts a signing question into a data-loss question at exactly the moment the app becomes
useful.

**Do not build release APKs in CI yet** — tags and changelogs only until signing is decided.
Rejected as a deferral rather than an answer: the decision does not get easier later, and the cost
of getting it wrong rises with every install that exists when it is finally made.

## Consequences

- **The keystore must not be lost.** If it is, no future version of this app can ever be installed
  over an existing one. Every user must uninstall and lose their data, exactly once, and then the
  new key becomes the one that must never be lost. There is no recovery path, no support channel,
  and no re-signing authority — the keystore's backup is the whole mitigation.
- **The pinned fingerprint never changes.** Editing `.github/release-cert-sha256.txt` is not a code
  change; it is a declaration that every install is about to break. Any pull request touching it is
  either a mistake or an event that has to be announced.
- **A missing keystore does not fail a build — it produces a *different app*.** If a secret is
  renamed, mis-wired, or a workflow is copied without it, Gradle falls back to the debug key and
  emits a perfectly valid APK that installs, runs and passes every other check. Nothing
  distinguishes it but the certificate inside it, and the damage only appears at the *next*
  release, on a user's device. This is why the gate names a debug-key fallback explicitly rather
  than reporting an anonymous fingerprint mismatch, and why it runs before anything is published
  rather than after.
- **Two secrets now have to be protected** that did not exist before, and any workflow that can
  reach them can sign as this app. No `pull_request`-triggered workflow references them, and a unit
  test enforces that.
- **Producing a release APK is still to come.** Gradle's `signingConfigs` and the release workflow
  are [#11](https://github.com/derekwinters/Interval-trainer-android/issues/11). Until then this
  repository builds no release artifact at all, so there is no unsigned release in the meantime.
