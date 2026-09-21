# Captured apksigner output

`apksigner-verify-print-certs-verbose.txt` and `apksigner-verify-print-certs-nonverbose.txt` are
the **verbatim stdout of apksigner 31.0.2**, run over a real signed APK in the two modes this
repository's release-signature gate could have used:

```
apksigner verify --print-certs --verbose signed.apk    # -> the -verbose fixture
apksigner verify --print-certs           signed.apk    # -> the -nonverbose fixture
```

They were captured in [`derekwinters/lucas-doggiehood`](https://github.com/derekwinters/lucas-doggiehood)
(`.github/scripts/tests/fixtures/`, same filenames) and copied here byte for byte — the two files
hash identically to their originals. Nothing in them was edited, and nothing should be: a comment
header added here would make them authored files that merely look captured, which is precisely the
failure they exist to prevent.

**Why they are worth copying rather than writing.** Doggiehood shipped a release with no assets at
all. Its gate ran `apksigner verify --print-certs` without `--verbose`, while its parser required
the `Number of signers:` header that only `--verbose` prints. Twenty-two unit tests were green,
because every apksigner sample among them had been hand-authored from the tool's *documented*
output as a verbose transcript. The suite certified a parser that had never read real output and
could not have. So both modes are kept: the verbose one is the format the gate asks for and reads,
and the non-verbose one is the real output that broke that release, retained as a case the parser
is proven to **reject**.

There is no apksigner in the development environment used here and `dl.google.com` is not
reachable from it, so a captured fixture is the only honest way to test this parser.

**The certificate in these transcripts is not this repository's release certificate.** It is a
throwaway key generated solely to produce the capture. What these files pin is apksigner's output
*shape*; the correctness of the pinned release fingerprint in `.github/release-cert-sha256.txt` is
a separate test's job.

## `apksigner-verify-print-certs-verbose-scheme-signer.txt`

Verbatim stdout of `apksigner verify --print-certs --verbose`, captured from build-tools 35.0.0
against this repository's own real, correctly-signed v0.2.2 release APK, in
[run 35545900827, job 106171632586](https://github.com/derekwinters/Interval-trainer-android/actions/runs/35545900827/job/106171632586),
commit `d86fa8c` (`chore(main): release 0.2.2`). It reached this repository through issue #120's
diagnostic logging (SIGN-036), which printed it verbatim to the job log when the parser of the day
— recognising only `Signer #<N> certificate <field>:` — matched none of its lines and raised
`MalformedApksignerOutput` on a validly-signed APK. It was copied here byte for byte from that log.

**Unlike the other two fixtures, the certificate in this one *is* this repository's release
certificate**: its `V2 Signer: certificate SHA-256 digest:` line matches
`.github/release-cert-sha256.txt` exactly. That match was verified independently before this
fixture was trusted for anything.

**Why it exists.** build-tools 35.0.0's apksigner labels a signer block by which signature scheme
verified it — `V2 Signer:` — rather than by a numeric index, at least when exactly one scheme
verifies. `Signer #<N>` and `V2 Signer:` are two real, distinct shapes apksigner emits for the
*same* command line, across SDK versions; this fixture is the proof the second one is real, not
hypothetical, the same way the doggiehood capture above proved the first one's `--verbose` /
non-`--verbose` distinction was real. See issue #122.
