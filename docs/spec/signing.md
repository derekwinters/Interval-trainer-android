# Specification — Release signing (`SIGN`)

How a release build of the app is signed, and what stops one being published under the wrong key.

Android identifies an installed app by the certificate that signed it. A new APK whose certificate
differs from the installed one's is **refused outright**, and the only way through is to uninstall
first — which destroys the app's data. There is no Play Store here to re-sign anything and no key
recovery for a sideloaded app, so the signing key is a one-way decision, taken once and never
revisited.

This page specifies the decision, the pinned certificate, and the gate that enforces it. It does
**not** specify how a release APK is produced: Gradle's `signingConfigs` and the release workflow
are separate work, and nothing in this repository builds a release artifact today.

---

## Invariants

> **Invariant — a published release artifact is signed by the certificate pinned in
> `.github/release-cert-sha256.txt`, and by no other key.** The failure this exists to catch is the
> quiet one. A mis-wired keystore input, a renamed secret, or a workflow copied without one does
> not fail the build: Gradle falls back to Android's debug key and emits a perfectly valid APK that
> installs, runs, and passes every other check. Nothing distinguishes it but the certificate
> inside it, and the damage appears at the *next* release, on a user's device.

> **Invariant — the release signing key never changes.** A different fingerprint in the pin file
> means a different key, and every existing install has to be removed before the new APK will go
> on. Changing the pin is not a code change; it is an announcement that everyone loses their data.

> **Invariant — the keystore and its passwords are secrets; the certificate fingerprint is not.**
> The fingerprint ships inside every APK, so it is committed in the clear and treated as public
> data. Nothing that unlocks the key is ever committed, printed, or written into an issue, a
> comment or a log.

> **Invariant — a gate that parses another tool's output is tested against at least one fixture
> captured from a real run of that tool, with the exact flags the gate passes.** A fixture written
> from documentation tests the parser against this repository's beliefs about the tool; only a
> captured one tests it against the tool.

> **Invariant — the release-signature gate that checks a build is always the current one, never
> the one contemporaneous with the code being checked.** The gate is CI-side machinery, not part of
> the app being shipped: a bug fixed in the gate must protect every release it is asked to verify,
> including one built or backfilled from a tag cut before the fix existed. A workflow step that
> checks out the tag or commit under test and then runs *that* checkout's own copy of the gate
> re-enacts whatever bug the gate had on the day that commit was made, no matter how long ago the
> real fix landed on `main`.

---

## 1. The decision

- **SIGN-001** Release builds are signed with a stable, owned keystore. The Android debug key is
  not used for a release, and neither is a key generated per build. *(manual: recorded in
  [`docs/adr/0001-release-signing-with-a-stable-keystore.md`](../adr/0001-release-signing-with-a-stable-keystore.md);
  a decision is not a runtime behaviour.)*
- **SIGN-002** The keystore and its passwords reach a build as repository secrets —
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
  `ANDROID_KEY_ALIAS_PASSWORD` — and no keystore, password or alias value is committed to this
  repository. *(manual: the absence of a value cannot be asserted by a test that would have to
  know the value.)*
- **SIGN-003** The release certificate's SHA-256 fingerprint is committed in the clear at
  `.github/release-cert-sha256.txt`, with comments saying that it is public and why it is not a
  secret.
- **SIGN-004** Wiring a release build — Gradle's `signingConfigs`, a release workflow, an
  uploaded artifact — is specified in [`build.md`](build.md) (`BUILD-050`–`074`), not here. This
  page owns two things about a release artifact: which certificate signed it (`SIGN-010`–`055`),
  and which signature schemes it carries (`SIGN-070`–`071`). *(manual: a scope statement.)*

  *This requirement previously read that release-build wiring did not exist yet, and that
  "until that exists, no release artifact is produced at all, so there is no unsigned release in
  the meantime." That was true when it was written and has not been true since `BUILD-050`–`068`
  landed and `v0.2.0`–`v0.2.2` shipped signed APKs. It is corrected here rather than in its own
  change because issue
  [#127](https://github.com/derekwinters/Interval-trainer-android/issues/127) turned on reading
  this page for what it says about a release artifact, and a reader following the old text would
  have concluded no release artifact existed — the opposite of the situation under
  investigation.*

## 2. The pinned fingerprint

- **SIGN-010** `.github/release-cert-sha256.txt` holds exactly one fingerprint. Blank lines and
  lines beginning `#` are comments; any other count of content lines is an error rather than a
  guess at which one was meant.
- **SIGN-011** The committed fingerprint is 64 lowercase hexadecimal digits — SHA-256 over the
  DER-encoded certificate, the same 32 bytes `keytool -list -v` prints as `SHA256:` and apksigner
  prints as `certificate SHA-256 digest`.
- **SIGN-012** The committed value is the release certificate the owner generated. A typo here
  fails every future release with a mismatch that reads like a compromised key, so the exact
  expected digits are pinned by a test rather than trusted to review.
- **SIGN-013** The pin is never edited. *(manual: an invariant about the repository's history, not
  about a run.)*

## 3. Reading a fingerprint

- **SIGN-020** keytool's colon-separated uppercase form and apksigner's bare lowercase hex
  normalise to the same 64 lowercase hex digits, so either may be pasted into the pin file.
- **SIGN-021** A leading `SHA256:` or `SHA-256:` label and surrounding whitespace are ignored.
- **SIGN-022** Anything that is not 32 hex-encoded bytes — the wrong length, a non-hex character,
  or nothing at all — raises `MalformedFingerprint` rather than being compared and quietly failing
  as a mismatch.

## 4. Reading apksigner

- **SIGN-030** The gate runs `apksigner verify --print-certs --verbose` on each APK. `--verbose` is
  load-bearing: apksigner emits the `Number of signers:` header only in verbose mode, and the
  parser requires it.
- **SIGN-031** The parser reads each signer's index, certificate DN and certificate SHA-256 digest,
  including apksigner's per-SDK-range form
  (`Signer (minSdkVersion=24, maxSdkVersion=32) #1 certificate DN:`).
- **SIGN-032** The parser cross-checks the declared `Number of signers:` against the signer blocks
  it actually parsed and raises `MalformedApksignerOutput` when they disagree. A silently short
  list would let an unexamined signer through.
- **SIGN-033** Output carrying no `Number of signers:` line is rejected, naming that line. This is
  exactly the real output of `--print-certs` without `--verbose`, and it must keep failing: the
  cure for asking apksigner for the wrong format is to ask for the right one, never to loosen the
  parser, which would swallow a genuine future format drift.
- **SIGN-034** The extra lines `--verbose` adds — SHA-1 and MD5 certificate digests, key algorithm
  and size, public-key digests — are not mistaken for a certificate SHA-256.
- **SIGN-035** The single-signer fixtures under `.github/scripts/tests/fixtures/` are verbatim
  captured apksigner output, in both the verbose mode the gate asks for and the non-verbose mode it
  must reject, and each records where it came from. *(manual: provenance is a fact about how the
  file was made; a test can only assert its shape.)*
- **SIGN-036** Every `MalformedApksignerOutput` the parser raises carries the raw apksigner output
  verbatim, delimited, alongside its summary sentence. The format this parser reads belongs to
  another tool and can change without notice (SIGN-032, SIGN-033); when it does, the summary alone
  — "reported 1 signer(s) but 0 could be parsed" — names the symptom but not the cause, and the one
  CI run that hits the drift is the only chance to capture the text that explains it. Issue #120 is
  exactly that run: the real apksigner text that broke parsing was never in the log, and could not
  be recovered afterwards.
- **SIGN-037** The parser also reads a signer block labelled by which signature scheme verified it —
  `V2 Signer: certificate DN: ...` — rather than by a numeric index, alongside the `Signer #<N>`
  shape SIGN-031 already covers; neither shape's support comes at the other's expense. build-tools
  35.0.0 emits this shape instead of `Signer #1` for the same `--print-certs --verbose` command line
  when exactly one signature scheme verifies, so a numeric index is not always present to read: one
  is assigned per distinct scheme label instead, in the order first seen. This is what issue #120's
  diagnostic logging (SIGN-036) captured verbatim when it fired for real on v0.2.2's CI run,
  confirming that every one of v0.2.0, v0.2.1 and v0.2.2 shipped with no APK attached because the
  parser of the day recognised only the numeric shape and matched none of this real, validly-signed
  APK's output. `.github/scripts/tests/fixtures/apksigner-verify-print-certs-verbose-scheme-signer.txt`
  is that captured text (SIGN-035), and its certificate is verified to be this repository's actual
  release certificate — unlike the other two fixtures, which use a throwaway key. Whether two
  *simultaneously* verifying schemes for one physical signer should collapse into a single entry is
  not addressed here: no captured output has shown that case, so it is left unhandled rather than
  guessed at.

## 5. The verdict

- **SIGN-040** An APK with exactly one signer whose certificate is the pinned one passes.
- **SIGN-041** A certificate that is not the pinned one fails, and the reason names both the
  fingerprint found and the one expected.
- **SIGN-042** A signer whose DN contains `CN=Android Debug` fails as a **debug-key fallback**,
  named as such rather than reported as an anonymous mismatch. This is the dangerous case: it
  produces a valid APK that installs and runs, and only breaks the next upgrade.
- **SIGN-043** An APK with no signers fails as unsigned.
- **SIGN-044** More than one signer fails, even when the release key is among them.
- **SIGN-045** The expected fingerprint may be supplied in either keytool's or apksigner's form,
  since SIGN-020 makes them the same value.

## 6. Running the gate

- **SIGN-050** `.github/scripts/verify_release_signature.py` uses only the Python standard library.
- **SIGN-051** The decisions — `normalize_fingerprint`, `parse_apksigner_certs`, `assess` and
  `read_expected_fingerprint` — are pure functions, separate from the subprocess and filesystem
  wiring, so the whole verdict is unit-tested with no Android SDK, no keystore and no APK.
- **SIGN-052** It takes one or more APK paths, exits `0` when every one carries the pinned
  certificate and `1` otherwise, and checks all of them rather than stopping at the first failure.
- **SIGN-053** Each failure is printed as a GitHub Actions error annotation
  (`::error title=Release signature::`), so it is visible on the run without opening the log.
- **SIGN-054** apksigner is found on `PATH`, or else as the newest `build-tools/*/apksigner` under
  `ANDROID_HOME` or `ANDROID_SDK_ROOT`. Not finding it is an error, never a pass.
- **SIGN-055** A non-zero exit from apksigner is a failure of the gate, not an empty parse.

## 7. Continuous integration

- **SIGN-060** `pr.yml` runs the gate's unit tests on every pull request, so the gate is proven
  before anything trusts it.
- **SIGN-061** That step needs no Android SDK, no keystore and no new action: it runs the runner's
  `python3` over `.github/scripts/tests/`. Adding an action would add a `uses:` reference to pin
  (BUILD-043) for no gain.
- **SIGN-062** No `pull_request`-triggered workflow references the release keystore secrets. A
  pull request from anywhere must not be able to reach the release key.
- **SIGN-063** In `release-please.yml`, both `build-and-attach` and `backfill-release-apk` check
  out `main` in a second `actions/checkout` step, to a path distinct from the job's primary
  checkout of the release tag or commit, and the job's "Verify the release signature" step invokes
  `.github/scripts/verify_release_signature.py` from that second checkout — never the copy the
  primary checkout's own tree carries. The APK is still built and signed from the exact tagged
  commit; only the script that checks it is sourced independently, regardless of which tag or
  commit that is (see the invariant above). Issue #125: `backfill-release-apk`'s only checkout was
  `ref: ${{ inputs.backfill_tag }}`, so re-running the pending `backfill_tag` dispatches for
  `v0.2.0`, `v0.2.1` and `v0.2.2` after issue #122/#123's real apksigner-parsing fix (`SIGN-037`)
  landed on `main` still failed identically to before — each tag predates that fix and re-ran its
  own already-superseded copy of the gate. `build-and-attach` checks out its release tag the same
  way and has the same structural gap, though it has not caused a real failure yet: every ordinary
  release so far has been built from a commit at or after whatever fix was current on `main` at
  release time. *(auto: `.github/scripts/tests/test_verify_release_signature.py`'s
  `IndependentVerificationScriptTests`, which reads `release-please.yml` directly and pins, for
  both jobs, that the verification step's script path resolves under a checkout step whose `ref:`
  is the literal `main` and whose `path:` differs from the job's own tag/commit checkout —
  standard library only, no Android SDK, no Actions run. The end-to-end proof remains manual: a
  maintainer re-running `backfill_tag: v0.2.0`/`v0.2.1`/`v0.2.2` after this merges and confirming
  each now attaches a signed APK.)*

## 8. Which signature schemes a release carries

The certificate is only half of what a device asks. The other half is *which scheme* the signature
is in, and Android accepts a package only under a scheme its own installer implements. Nothing in
this repository stated an intention about that, so AGP's default stood: with `minSdk` at 26 it
disables v1 (JAR) signing, because v2 covers every device from Android 7.0 and a JAR signature is
then redundant. The `v0.2.x` artifacts carry v2 and nothing else.

That default is defensible and the artifacts it produced are sound — the published `v0.2.2` APK's
v2 signature was verified independently while diagnosing issue
[#127](https://github.com/derekwinters/Interval-trainer-android/issues/127), digest recomputed and
signature checked against the pinned certificate. The reason to state all three anyway is that
this app is *sideloaded*: it reaches a device through a file manager, a browser's download
handler, or a vendor's package installer, none of which AGP knows about, and each of which reports
a refusal only as Android's generic "package seems invalid". Carrying every scheme costs a few
hundred kilobytes of `META-INF` digests and removes a class of refusal that cannot be observed
from here.

- **SIGN-070** A release APK carries the v1, v2 and v3 signature schemes. v1 is detected from the
  `META-INF/*.SF` entry JAR signing leaves in the zip; v2 and v3 from their pair IDs in the APK
  Signing Block. v3.1 counts as v3 — it is an additional rotation-aware block, not a replacement.
  Checked by `.github/scripts/verify_release_package.py` (`BUILD-073`), which reads the APK
  directly and needs no Android SDK; this is a distinct question from `SIGN-040`–`045`, which ask
  *whose* certificate signed it. *(auto:
  `.github/scripts/tests/test_verify_release_package.py`.)*
- **SIGN-071** `:app`'s `release` signing config sets `enableV1Signing`, `enableV2Signing` and
  `enableV3Signing` explicitly rather than leaving AGP to choose. The gate then checks the
  artifact against a stated intention instead of against whatever the toolchain chose this week —
  a scheme silently dropped by an AGP upgrade is exactly the kind of change that ships unnoticed,
  because nothing else reports it. *(manual: a Gradle configuration fact, proved by `SIGN-070`'s
  gate passing on a real signed build.)*

---

## Traceability

| Section | IDs | Tests |
|---|---|---|
| The decision | SIGN-001–004 | *(manual)* |
| The pinned fingerprint | SIGN-010–013 | `.github/scripts/tests/test_verify_release_signature.py` |
| Reading a fingerprint | SIGN-020–022 | `.github/scripts/tests/test_verify_release_signature.py` |
| Reading apksigner | SIGN-030–037 | `.github/scripts/tests/test_verify_release_signature.py` |
| The verdict | SIGN-040–045 | `.github/scripts/tests/test_verify_release_signature.py` |
| Running the gate | SIGN-050–055 | `.github/scripts/tests/test_verify_release_signature.py` |
| Continuous integration | SIGN-060–063 | `.github/scripts/tests/test_verify_release_signature.py` |
| Which signature schemes a release carries | SIGN-070–071 | `SIGN-070`: `.github/scripts/tests/test_verify_release_package.py`; `SIGN-071` *(manual)* |

**37 requirements, 31 `auto` and 6 `manual`.**

The five manual ones are the decision itself, the handling of secrets whose values are deliberately
absent from this repository, the scope boundary, the promise never to edit the pin, and the
provenance of the captured fixtures. Everything the gate actually decides is executable.
