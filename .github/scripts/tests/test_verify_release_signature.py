"""Unit tests for the release-signature gate (issue #10, `docs/spec/signing.md`).

Release builds of this app are signed with one stable, owned keystore rather
than Android's debug key. Android identifies an installed app by its signing
certificate: an APK signed by a different key will not install over an existing
one, and the only way through is to uninstall first, which destroys the app's
data. For a sideloaded app there is no key recovery and no Play Store to
re-sign anything, so the key is chosen once and never changed.

`verify_release_signature.py` is what makes shipping a wrongly-signed APK
impossible. The release certificate is pinned in
`.github/release-cert-sha256.txt` — public data, since it ships inside every
APK — and any artifact signed by a different key fails the gate. The dangerous
case, and the reason the gate exists at all, is a silent fall back to the debug
key: a mis-wired keystore input does not fail a build, it produces a perfectly
valid APK that installs, runs, and only breaks the *next* release, on a user's
device.

These tests pin the pure decisions — `normalize_fingerprint` (SIGN-020–022),
`parse_apksigner_certs` (SIGN-031–034), `assess` (SIGN-040–045) and
`read_expected_fingerprint` (SIGN-010–012) — the wiring that has to agree with
them (SIGN-030, SIGN-052–055), and the CI that proves the gate before anything
trusts it (SIGN-060–062). They need no Android SDK, no keystore and no APK.

A note on the apksigner samples. The single-signer transcripts are **captured,
not authored**: `fixtures/apksigner-verify-print-certs-verbose.txt` and its
`-nonverbose` sibling are verbatim apksigner 31.0.2 stdout over a real signed
APK, copied byte for byte from `derekwinters/lucas-doggiehood`, where a release
shipped with zero assets because a gate parsed hand-authored *verbose*
transcripts while its workflow invoked apksigner without `--verbose`. Twenty-two
green tests certified a parser that had never read real output. See
`fixtures/README.md`. The certificate in those transcripts is a throwaway key,
deliberately not this repository's release certificate; what they pin is
apksigner's output shape, and `CommittedFingerprintTests` pins the real one.

`TWO_SIGNER_OUTPUT` and `DEBUG_OUTPUT` remain hand-authored — neither a
two-key APK nor a debug-signed one is something this environment can produce —
and are used only for the multi-signer and debug-fallback cases.
"""

import os
import re
import subprocess
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from verify_release_signature import (  # noqa: E402
    MalformedApksignerOutput,
    MalformedFingerprint,
    SignerFacts,
    assess,
    find_apksigner,
    main,
    normalize_fingerprint,
    parse_apksigner_certs,
    print_certs,
    read_expected_fingerprint,
)

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "fixtures")
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
PIN_FILE = os.path.join(REPO_ROOT, ".github", "release-cert-sha256.txt")
WORKFLOWS = os.path.join(REPO_ROOT, ".github", "workflows")


def _captured(name):
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as handle:
        return handle.read()


CAPTURED_VERBOSE_OUTPUT = _captured("apksigner-verify-print-certs-verbose.txt")
CAPTURED_NON_VERBOSE_OUTPUT = _captured("apksigner-verify-print-certs-nonverbose.txt")

# The certificate inside those captures: a throwaway key, NOT this repository's
# release certificate (which no keystore in this environment holds).
CAPTURED_SHA256 = "10d315258da7c4c4830814ae6f876e84145f2195cfb077bc0bb04e3df0a61ed8"
CAPTURED_KEYTOOL_FORM = (
    "10:D3:15:25:8D:A7:C4:C4:83:08:14:AE:6F:87:6E:84:"
    "14:5F:21:95:CF:B0:77:BC:0B:B0:4E:3D:F0:A6:1E:D8")
CAPTURED_DN = "CN=Doggiehood Release, O=Derek Winters, L=Somewhere, C=US"

# This repository's release certificate, as the owner reported it. Public data
# (SIGN-003); the keystore and its passwords are the secrets.
RELEASE_SHA256 = "2f596b227b890f5fcec72c176f0e325623e6261f00ddb102c4f936e9da108e09"

OTHER_SHA256 = "0123456789abcdef" * 4

DEBUG_OUTPUT = """Verifies
Verified using v1 scheme (JAR signing): true
Number of signers: 1
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 certificate SHA-256 digest: {0}
""".format(OTHER_SHA256)

TWO_SIGNER_OUTPUT = """Verifies
Number of signers: 2
Signer #1 certificate DN: CN=Interval Trainer Release, O=Derek Winters, C=US
Signer #1 certificate SHA-256 digest: {0}
Signer #2 certificate DN: CN=Somebody Else, O=Elsewhere, C=US
Signer #2 certificate SHA-256 digest: {1}
""".format(CAPTURED_SHA256, OTHER_SHA256)

# apksigner's per-SDK-range form, which carries the same signer index.
SDK_RANGE_OUTPUT = """Verifies
Number of signers: 1
Signer (minSdkVersion=24, maxSdkVersion=32) #1 certificate DN: {0}
Signer (minSdkVersion=24, maxSdkVersion=32) #1 certificate SHA-256 digest: {1}
""".format(CAPTURED_DN, CAPTURED_SHA256)


def _colon_form(bare_hex):
    """The same bytes in keytool's uppercase colon-separated shape."""
    return ":".join(
        bare_hex[index:index + 2] for index in range(0, len(bare_hex), 2)).upper()


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


class NormalizeFingerprintTests(unittest.TestCase):
    """SIGN-020–022. keytool and apksigner print the same 32 bytes differently.

    `keytool -list -v` prints `SHA256: 2F:59:...` — uppercase, colon-separated,
    behind a label — and apksigner prints bare lowercase hex. Both are SHA-256
    over the DER-encoded certificate, so normalizing is what makes either form
    safe to paste into `.github/release-cert-sha256.txt`.
    """

    def test_accepts_the_keytool_colon_form(self):
        """SIGN-020."""
        self.assertEqual(normalize_fingerprint(CAPTURED_KEYTOOL_FORM), CAPTURED_SHA256)

    def test_accepts_apksigners_bare_hex(self):
        """SIGN-020."""
        self.assertEqual(normalize_fingerprint(CAPTURED_SHA256), CAPTURED_SHA256)

    def test_the_release_fingerprint_survives_a_round_trip_through_both_forms(self):
        """SIGN-020, for the value this repository actually pins."""
        self.assertEqual(normalize_fingerprint(_colon_form(RELEASE_SHA256)), RELEASE_SHA256)

    def test_accepts_a_leading_label_and_surrounding_whitespace(self):
        """SIGN-021."""
        self.assertEqual(
            normalize_fingerprint("  SHA256: {0}\n".format(CAPTURED_KEYTOOL_FORM)),
            CAPTURED_SHA256)
        self.assertEqual(
            normalize_fingerprint("SHA-256: {0}".format(CAPTURED_SHA256)), CAPTURED_SHA256)

    def test_rejects_a_fingerprint_of_the_wrong_length(self):
        """SIGN-022."""
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("2F:59:6B:22")

    def test_rejects_a_fingerprint_that_is_not_hex(self):
        """SIGN-022."""
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("zz" + RELEASE_SHA256[2:])

    def test_rejects_an_empty_fingerprint(self):
        """SIGN-022."""
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("   \n")
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint(None)


class ParseApksignerCertsTests(unittest.TestCase):
    """SIGN-031–034."""

    def test_reads_the_dn_and_digest_of_a_single_signer_from_a_real_capture(self):
        """SIGN-031."""
        signers = parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT)
        self.assertEqual(len(signers), 1)
        self.assertEqual(signers[0].index, 1)
        self.assertEqual(signers[0].dn, CAPTURED_DN)
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_reads_the_per_sdk_range_signer_form(self):
        """SIGN-031."""
        signers = parse_apksigner_certs(SDK_RANGE_OUTPUT)
        self.assertEqual([signer.index for signer in signers], [1])
        self.assertEqual(signers[0].dn, CAPTURED_DN)
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_reads_every_signer(self):
        """SIGN-031."""
        signers = parse_apksigner_certs(TWO_SIGNER_OUTPUT)
        self.assertEqual([signer.index for signer in signers], [1, 2])
        self.assertEqual(
            [signer.sha256 for signer in signers], [CAPTURED_SHA256, OTHER_SHA256])

    def test_uppercase_digests_are_normalized(self):
        """SIGN-031."""
        signers = parse_apksigner_certs(
            CAPTURED_VERBOSE_OUTPUT.replace(CAPTURED_SHA256, CAPTURED_SHA256.upper()))
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_reports_no_signers_when_apksigner_found_none(self):
        """SIGN-031: an unsigned APK parses to an empty list, not an error."""
        self.assertEqual(parse_apksigner_certs("Number of signers: 0\n"), [])

    def test_a_signer_count_that_disagrees_with_the_blocks_raises(self):
        """SIGN-032. Format drift fails loudly rather than dropping a signer."""
        truncated = TWO_SIGNER_OUTPUT.replace(
            "Signer #2 certificate DN: CN=Somebody Else, O=Elsewhere, C=US\n", "")
        truncated = truncated.replace(
            "Signer #2 certificate SHA-256 digest: {0}\n".format(OTHER_SHA256), "")
        with self.assertRaises(MalformedApksignerOutput):
            parse_apksigner_certs(truncated)

    def test_output_without_a_signer_count_raises(self):
        """SIGN-033."""
        with self.assertRaises(MalformedApksignerOutput):
            parse_apksigner_certs("Verifies\n")

    def test_real_non_verbose_output_is_rejected(self):
        """SIGN-033, against the capture that broke a real release.

        This is apksigner's genuine `--print-certs` output with no `--verbose`:
        the DN and digest blocks are all present and the `Number of signers:`
        header is simply absent. It must keep raising. The cure for asking
        apksigner for the wrong format is to ask for the right one, never to
        teach the parser to accept a header-less one — a loosened parser would
        swallow a real future format drift.
        """
        self.assertNotIn("Number of signers:", CAPTURED_NON_VERBOSE_OUTPUT)
        self.assertIn("certificate SHA-256 digest:", CAPTURED_NON_VERBOSE_OUTPUT)
        with self.assertRaises(MalformedApksignerOutput) as raised:
            parse_apksigner_certs(CAPTURED_NON_VERBOSE_OUTPUT)
        self.assertIn("Number of signers:", str(raised.exception))

    def test_the_captured_verbose_output_carries_the_header_the_parser_needs(self):
        """SIGN-030 and SIGN-032, pinned against each other.

        The fixture is the output of the very command line `print_certs` runs,
        so this asserts the invocation and the parser agree on one format.
        """
        self.assertIn("Number of signers: 1", CAPTURED_VERBOSE_OUTPUT)
        self.assertEqual(
            [signer.sha256 for signer in parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT)],
            [CAPTURED_SHA256])

    def test_the_verbose_only_extra_lines_do_not_confuse_the_parser(self):
        """SIGN-034: SHA-1, MD5 and public-key digests are not certificates."""
        self.assertIn("Signer #1 public key SHA-256 digest:", CAPTURED_VERBOSE_OUTPUT)
        self.assertIn("Signer #1 certificate SHA-1 digest:", CAPTURED_VERBOSE_OUTPUT)
        signers = parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT)
        self.assertEqual(len(signers), 1)
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_two_different_certificates_for_one_signer_raise(self):
        """SIGN-032: contradictory output is not silently resolved."""
        contradictory = CAPTURED_VERBOSE_OUTPUT + (
            "Signer #1 certificate SHA-256 digest: {0}\n".format(OTHER_SHA256))
        with self.assertRaises(MalformedApksignerOutput):
            parse_apksigner_certs(contradictory)


class ApksignerInvocationTests(unittest.TestCase):
    """SIGN-030 and SIGN-055: the command line must produce the parsed format.

    `parse_apksigner_certs` requires the `Number of signers:` header, and
    apksigner prints it only under `--verbose`; `--print-certs` alone emits the
    DN and SHA-256 blocks and nothing else. A gate that asks for one format and
    reads another fails every real APK it is pointed at, which is how a release
    elsewhere shipped with no assets. This pins the flags, not the parse.
    """

    def _run_print_certs(self, returncode=0, stdout=None, stderr=""):
        completed = subprocess.CompletedProcess(
            args=[], returncode=returncode,
            stdout=CAPTURED_VERBOSE_OUTPUT if stdout is None else stdout, stderr=stderr)
        with mock.patch("verify_release_signature.subprocess.run",
                        return_value=completed) as run:
            output = print_certs("app-release.apk", apksigner="/usr/bin/apksigner")
        self.assertEqual(run.call_count, 1)
        return list(run.call_args[0][0]), output

    def test_the_invocation_asks_for_the_verbose_output_the_parser_reads(self):
        """SIGN-030."""
        argv, _ = self._run_print_certs()
        self.assertIn(
            "--verbose", argv,
            "apksigner prints 'Number of signers:' only under --verbose, and "
            "parse_apksigner_certs requires that line — without the flag the gate "
            "cannot read any real APK: {0}".format(argv))

    def test_the_invocation_verifies_and_prints_certs_for_the_apk(self):
        """SIGN-030."""
        argv, _ = self._run_print_certs()
        self.assertEqual(argv[0], "/usr/bin/apksigner")
        self.assertIn("verify", argv)
        self.assertIn("--print-certs", argv)
        self.assertEqual(argv[-1], "app-release.apk")

    def test_a_nonzero_apksigner_exit_is_a_failure_not_an_empty_parse(self):
        """SIGN-055."""
        with self.assertRaises(MalformedApksignerOutput):
            self._run_print_certs(returncode=1, stdout="", stderr="DOES NOT VERIFY")


class FindApksignerTests(unittest.TestCase):
    """SIGN-054: apksigner comes from PATH or from the SDK, or it is an error."""

    def test_prefers_apksigner_on_the_path(self):
        with mock.patch("verify_release_signature.shutil.which",
                        return_value="/usr/bin/apksigner"):
            self.assertEqual(find_apksigner(), "/usr/bin/apksigner")

    def test_falls_back_to_the_newest_build_tools_copy(self):
        import tempfile
        with tempfile.TemporaryDirectory() as sdk:
            for version in ("34.0.0", "35.0.0"):
                tools = os.path.join(sdk, "build-tools", version)
                os.makedirs(tools)
                open(os.path.join(tools, "apksigner"), "w").close()
            with mock.patch("verify_release_signature.shutil.which", return_value=None), \
                    mock.patch.dict(os.environ, {"ANDROID_HOME": sdk,
                                                 "ANDROID_SDK_ROOT": ""}, clear=False):
                self.assertEqual(
                    find_apksigner(),
                    os.path.join(sdk, "build-tools", "35.0.0", "apksigner"))

    def test_not_finding_apksigner_is_an_error_never_a_pass(self):
        with mock.patch("verify_release_signature.shutil.which", return_value=None), \
                mock.patch.dict(os.environ, {"ANDROID_HOME": "",
                                             "ANDROID_SDK_ROOT": ""}, clear=False):
            with self.assertRaises(OSError):
                find_apksigner()


class AssessTests(unittest.TestCase):
    """SIGN-040–045."""

    def test_the_expected_certificate_passes(self):
        """SIGN-040."""
        verdict = assess(parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT), CAPTURED_SHA256)
        self.assertTrue(verdict.ok, verdict.reasons)
        self.assertEqual(verdict.reasons, [])

    def test_a_different_certificate_fails_and_names_both_fingerprints(self):
        """SIGN-041."""
        verdict = assess(
            [SignerFacts(index=1, dn="CN=Somebody Else", sha256=OTHER_SHA256)],
            RELEASE_SHA256)
        self.assertFalse(verdict.ok)
        joined = " ".join(verdict.reasons)
        self.assertIn(OTHER_SHA256, joined)
        self.assertIn(RELEASE_SHA256, joined)

    def test_the_android_debug_certificate_fails_as_a_debug_fallback(self):
        """SIGN-042. The dangerous case gets its own name in the error."""
        verdict = assess(parse_apksigner_certs(DEBUG_OUTPUT), RELEASE_SHA256)
        self.assertFalse(verdict.ok)
        self.assertTrue(
            any("debug" in reason.lower() for reason in verdict.reasons),
            "a debug-signed release APK must be reported as a debug fallback, not "
            "merely as a fingerprint mismatch: {0}".format(verdict.reasons))

    def test_an_unsigned_apk_fails(self):
        """SIGN-043."""
        verdict = assess([], RELEASE_SHA256)
        self.assertFalse(verdict.ok)
        self.assertTrue(any("unsigned" in reason.lower() for reason in verdict.reasons))

    def test_an_extra_signer_fails_even_when_the_release_key_is_present(self):
        """SIGN-044."""
        verdict = assess(parse_apksigner_certs(TWO_SIGNER_OUTPUT), CAPTURED_SHA256)
        self.assertFalse(verdict.ok)

    def test_the_expected_fingerprint_may_be_given_in_keytool_form(self):
        """SIGN-045."""
        verdict = assess(
            parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT), CAPTURED_KEYTOOL_FORM)
        self.assertTrue(verdict.ok, verdict.reasons)


class CommittedFingerprintTests(unittest.TestCase):
    """SIGN-003 and SIGN-010–012: the pin is repo content, so a bad paste fails here.

    The fingerprint is public — it is inside every published APK — which is why
    it is committed rather than held as a secret. What it must never be is
    *wrong*: a typo would fail every release with a mismatch that reads like a
    compromised key.
    """

    def test_the_pin_file_exists_and_reads_as_one_fingerprint(self):
        """SIGN-010."""
        self.assertTrue(os.path.exists(PIN_FILE), "{0} is missing".format(PIN_FILE))
        self.assertEqual(len(read_expected_fingerprint(PIN_FILE)), 64)

    def test_the_committed_fingerprint_is_lowercase_hex(self):
        """SIGN-011."""
        fingerprint = read_expected_fingerprint(PIN_FILE)
        self.assertEqual(fingerprint, fingerprint.lower())
        self.assertRegex(fingerprint, r"^[0-9a-f]{64}$")

    def test_the_committed_fingerprint_is_the_release_certificate(self):
        """SIGN-012. The exact value, so a transcription slip fails here."""
        self.assertEqual(read_expected_fingerprint(PIN_FILE), RELEASE_SHA256)

    def test_the_bare_value_in_the_file_is_already_normalized(self):
        """SIGN-011: what is committed is the lowercase hex, not keytool's form."""
        content_lines = [
            line.strip() for line in _read(PIN_FILE).splitlines()
            if line.strip() and not line.strip().startswith("#")]
        self.assertEqual(content_lines, [RELEASE_SHA256])

    def test_the_file_says_the_fingerprint_is_public(self):
        """SIGN-003: whoever finds this file must not mistake it for a secret."""
        comments = "\n".join(
            line for line in _read(PIN_FILE).splitlines() if line.strip().startswith("#"))
        self.assertIn("public", comments.lower())

    def test_more_than_one_fingerprint_in_the_file_is_an_error(self):
        """SIGN-010: two pins is not a pin."""
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "two.txt")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("# comment\n{0}\n{1}\n".format(RELEASE_SHA256, OTHER_SHA256))
            with self.assertRaises(MalformedFingerprint):
                read_expected_fingerprint(path)


class GateExitCodeTests(unittest.TestCase):
    """SIGN-052 and SIGN-053: what the gate returns, and how it reports."""

    def _main(self, outputs):
        """Run `main` over as many APKs as `outputs` has entries."""
        apks = ["app-release-{0}.apk".format(index) for index in range(len(outputs))]
        with mock.patch("verify_release_signature.print_certs", side_effect=outputs):
            with mock.patch("sys.stdout", new=__import__("io").StringIO()) as out:
                code = main(apks + ["--expected", RELEASE_SHA256])
        return code, out.getvalue()

    def _release_signed_output(self):
        return CAPTURED_VERBOSE_OUTPUT.replace(CAPTURED_SHA256, RELEASE_SHA256)

    def test_a_correctly_signed_apk_exits_zero(self):
        """SIGN-052."""
        code, output = self._main([self._release_signed_output()])
        self.assertEqual(code, 0, output)

    def test_a_wrongly_signed_apk_exits_one_with_an_error_annotation(self):
        """SIGN-052, SIGN-053."""
        code, output = self._main([CAPTURED_VERBOSE_OUTPUT])
        self.assertEqual(code, 1)
        self.assertIn("::error title=Release signature::", output)

    def test_a_debug_signed_apk_exits_one_and_says_so(self):
        """SIGN-042, SIGN-053."""
        code, output = self._main([DEBUG_OUTPUT])
        self.assertEqual(code, 1)
        self.assertIn("::error title=Release signature::", output)
        self.assertIn("debug", output.lower())

    def test_every_apk_is_checked_rather_than_stopping_at_the_first_failure(self):
        """SIGN-052."""
        code, output = self._main([DEBUG_OUTPUT, CAPTURED_VERBOSE_OUTPUT])
        self.assertEqual(code, 1)
        self.assertEqual(output.count("::error title=Release signature::"), 2)

    def test_one_bad_apk_among_good_ones_fails_the_gate(self):
        """SIGN-052."""
        code, _ = self._main([self._release_signed_output(), DEBUG_OUTPUT])
        self.assertEqual(code, 1)

    def test_unreadable_apksigner_output_fails_rather_than_passing(self):
        """SIGN-032, SIGN-052."""
        code, output = self._main([CAPTURED_NON_VERBOSE_OUTPUT])
        self.assertEqual(code, 1)
        self.assertIn("Number of signers:", output)


class StandardLibraryOnlyTests(unittest.TestCase):
    """SIGN-050: the gate runs on a bare runner with no pip install."""

    STDLIB = {
        "argparse", "collections", "glob", "io", "os", "re", "shutil",
        "subprocess", "sys", "tempfile", "unittest",
    }

    def test_the_gate_imports_only_the_standard_library(self):
        source = _read(os.path.join(os.path.dirname(HERE), "verify_release_signature.py"))
        imported = set(re.findall(r"^\s*(?:import|from)\s+([A-Za-z_][A-Za-z0-9_.]*)",
                                  source, re.MULTILINE))
        self.assertTrue(imported)
        self.assertEqual(
            sorted(name for name in imported if name.split(".")[0] not in self.STDLIB), [])


class PullRequestWorkflowTests(unittest.TestCase):
    """SIGN-060–062: the gate is proven on every PR, and never handed the key."""

    KEYSTORE_SECRETS = (
        "ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS", "ANDROID_KEY_ALIAS_PASSWORD",
    )

    def _workflow_paths(self):
        return sorted(
            os.path.join(WORKFLOWS, name) for name in os.listdir(WORKFLOWS)
            if name.endswith((".yml", ".yaml")))

    def _step_body(self, text, step_name):
        """The YAML lines of one named step, up to the next step at its indent."""
        lines = text.splitlines()
        start = None
        indent = None
        for index, line in enumerate(lines):
            if line.strip() == "- name: {0}".format(step_name):
                start = index
                indent = len(line) - len(line.lstrip())
                break
        self.assertIsNotNone(start, "no '{0}' step".format(step_name))

        body = [lines[start]]
        for line in lines[start + 1:]:
            if line.strip().startswith("- ") and (len(line) - len(line.lstrip())) == indent:
                break
            body.append(line)
        return "\n".join(body)

    def test_the_pr_workflow_runs_the_gates_unit_tests(self):
        """SIGN-060."""
        text = _read(os.path.join(WORKFLOWS, "pr.yml"))
        self.assertTrue(
            re.search(r"^\s*pull_request:", text, re.MULTILINE),
            "pr.yml is not pull_request-triggered")
        body = self._step_body(text, "Run the release-signature gate's unit tests")
        self.assertIn("unittest", body)
        self.assertIn(".github/scripts/tests", body)

    def test_that_step_needs_no_android_sdk_and_no_new_action(self):
        """SIGN-061."""
        body = self._step_body(
            _read(os.path.join(WORKFLOWS, "pr.yml")),
            "Run the release-signature gate's unit tests")
        self.assertNotIn(
            "uses:", body,
            "the unit-test step runs the runner's python3; an action would add a "
            "`uses:` reference to pin (BUILD-043) for no gain")
        self.assertIn("python3", body)

    def test_no_pull_request_triggered_workflow_references_the_release_keystore(self):
        """SIGN-062."""
        for path in self._workflow_paths():
            text = _read(path)
            if not re.search(r"^\s*pull_request(_target)?:", text, re.MULTILINE):
                continue
            for secret in self.KEYSTORE_SECRETS:
                with self.subTest(workflow=os.path.basename(path), secret=secret):
                    self.assertNotIn(
                        secret, text,
                        "{0} is pull_request-triggered and must never reach the "
                        "release keystore".format(os.path.basename(path)))


if __name__ == "__main__":
    unittest.main()
