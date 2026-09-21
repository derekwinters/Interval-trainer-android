"""Unit tests for the release-package gate (issue #127, `docs/spec/build.md` section 10).

A `v0.2.x` release shipped an APK that the signature gate passed and a device
still refused, with Android's one generic message for every parse-or-verify
failure: "App not installed as the package seems invalid".
`verify_release_package.py` exists so that the specific, checkable reasons a
device refuses a package are checked before publication rather than discovered
on someone's phone.

These tests pin the pure decisions — `signing_block_ids` and `schemes_present`
(`SIGN-070`), `elf_load_alignments` (`BUILD-072`), `payload_offset`
(`BUILD-071`) and `assess` (`BUILD-070`–`072`) — the digest sidecar
(`BUILD-075`), and the workflow wiring that has to agree with them
(`BUILD-073`–`075`). They need no Android SDK, no keystore and no network.

**On constructing inputs rather than capturing them.** `fixtures/README.md`
warns, from a release that shipped with zero assets, against hand-authoring
what another tool *prints*: a transcript written from documentation certifies a
parser that has never read real output. That warning is about prose formats
owned by someone else's release notes, and `SIGN-035` states it for exactly
that case — apksigner's printed output. It does not reach this gate, because
this gate parses no prose. It reads the zip central directory, the APK Signing
Block and the ELF program header — three published binary standards — so a test
that builds one is building the real structure to the real specification rather
than an imitation of a tool's report. `_apk` below emits genuine zip records,
and `ConstructedApksAreRealZips` makes `zipfile` read them back, which is the
check that they are real.

That argument would be worth distrusting on its own, so it is not on its own:
the gate was additionally run end to end against the actual published
`interval-trainer-0.2.2-release.apk` — the 19.5 MB artifact from #127 — where
it cleared every packaging check and reported only the scheme set, agreeing
with the byte-by-byte analysis done by hand. That artifact is far too large to
commit, which is exactly why the constructed cases are what runs in CI.
"""

import binascii
import io
import os
import struct
import sys
import tempfile
import unittest
import zipfile
import zlib
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import verify_release_package as gate  # noqa: E402


# --- Building APKs to order -------------------------------------------------


def _local_entry(cursor, name, payload, stored, align):
    """One local file header plus payload, laid down at `cursor`.

    When `align` is set the payload is pushed onto that boundary by padding the
    local header's **extra field**, which is the same mechanism zipalign uses —
    so the offsets these tests assert are the offsets a device computes.
    """
    raw = name.encode()
    method = 0 if stored else 8
    if stored:
        body = payload
    else:
        compressor = zlib.compressobj(6, zlib.DEFLATED, -15)
        body = compressor.compress(payload) + compressor.flush()
    crc = binascii.crc32(payload) & 0xFFFFFFFF

    extra_length = 0
    if align:
        while (cursor + 30 + len(raw) + extra_length) % align:
            extra_length += 1

    header = struct.pack(
        "<IHHHHHIIIHH", 0x04034B50, 20, 0, method, 0, 0,
        crc, len(body), len(payload), len(raw), extra_length)
    record = header + raw + (b"\x00" * extra_length) + body
    return record, crc, len(body)


def _apk(entries, block_ids=(), v1=False):
    """A real zip carrying `entries`, optionally with an APK Signing Block.

    `entries` is a sequence of `(name, payload, stored, align)`. `block_ids`
    are APK Signing Block pair IDs to synthesise; `v1` adds the `META-INF/*.SF`
    entry that JAR signing leaves behind.
    """
    entries = list(entries)
    if v1:
        entries.append(("META-INF/CERT.SF", b"Signature-Version: 1.0\n", True, 0))

    out = bytearray()
    central = []
    for name, payload, stored, align in entries:
        offset = len(out)
        record, crc, csize = _local_entry(offset, name, payload, stored, align)
        out += record
        central.append((name, crc, csize, len(payload), 0 if stored else 8, offset))

    if block_ids:
        pairs = bytearray()
        for pair_id in block_ids:
            value = b"\x00" * 8
            pairs += struct.pack("<Q", 4 + len(value)) + struct.pack("<I", pair_id) + value
        # The block's own size field counts the pairs, the trailing 8-byte
        # size repeat and the 16-byte magic, but not the leading size field.
        block_size = len(pairs) + 8 + 16
        out += struct.pack("<Q", block_size) + pairs
        out += struct.pack("<Q", block_size) + gate.SIGNING_BLOCK_MAGIC

    cd_offset = len(out)
    cd = bytearray()
    for name, crc, csize, usize, method, offset in central:
        raw = name.encode()
        cd += struct.pack(
            "<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method, 0, 0,
            crc, csize, usize, len(raw), 0, 0, 0, 0, 0, offset)
        cd += raw
    out += cd
    out += struct.pack(
        "<IHHHHIIH", 0x06054B50, 0, 0, len(central), len(central),
        len(cd), cd_offset, 0)
    return bytes(out)


def _elf(is_64=True, p_align=16384, segments=1):
    """A minimal ELF image with `segments` PT_LOAD program headers."""
    if is_64:
        image = bytearray(0x40 + segments * 0x38)
        image[0:4] = gate.ELF_MAGIC
        image[4] = 2
        image[5] = 1
        struct.pack_into("<Q", image, 0x20, 0x40)
        struct.pack_into("<HH", image, 0x36, 0x38, segments)
        for index in range(segments):
            base = 0x40 + index * 0x38
            struct.pack_into("<I", image, base, gate.PT_LOAD)
            struct.pack_into("<Q", image, base + 0x30, p_align)
    else:
        image = bytearray(0x34 + segments * 0x20)
        image[0:4] = gate.ELF_MAGIC
        image[4] = 1
        image[5] = 1
        struct.pack_into("<I", image, 0x1C, 0x34)
        struct.pack_into("<HH", image, 0x2A, 0x20, segments)
        for index in range(segments):
            base = 0x34 + index * 0x20
            struct.pack_into("<I", image, base, gate.PT_LOAD)
            struct.pack_into("<I", image, base + 0x1C, p_align)
    return bytes(image)


GOOD_ARSC = (gate.RESOURCES_ARSC, b"arsc" * 64, True, gate.RESOURCES_ARSC_ALIGNMENT)


def _good_apk():
    return _apk(
        [("classes.dex", b"dex" * 100, False, 0), GOOD_ARSC],
        block_ids=(gate.V2_BLOCK_ID, gate.V3_BLOCK_ID), v1=True)


def _entries_of(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return gate.inspect_entries(data, archive)


# --- The zips these tests build are real zips -------------------------------


class ConstructedApksAreRealZips(unittest.TestCase):
    """The premise every other test rests on (see this module's docstring)."""

    def test_zipfile_reads_back_what_apk_built(self):
        with zipfile.ZipFile(io.BytesIO(_good_apk())) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn(gate.RESOURCES_ARSC, archive.namelist())
            self.assertEqual(archive.read("classes.dex"), b"dex" * 100)

    def test_requested_alignment_actually_lands_on_the_boundary(self):
        """Otherwise an alignment test could pass on a fixture that was never aligned."""
        data = _apk([
            ("a", b"x", True, 0),
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 64, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        lib = [e for e in _entries_of(data) if e.name.endswith(".so")][0]
        self.assertEqual(lib.offset % gate.NATIVE_LIB_ALIGNMENT, 0)


# --- Signature schemes (SIGN-070) -------------------------------------------


class SigningBlockIds(unittest.TestCase):

    def test_reads_the_pair_ids_present(self):
        data = _apk([GOOD_ARSC], block_ids=(gate.V2_BLOCK_ID, gate.V3_BLOCK_ID))
        self.assertEqual(
            gate.signing_block_ids(data), {gate.V2_BLOCK_ID, gate.V3_BLOCK_ID})

    def test_no_signing_block_is_an_empty_set_not_an_error(self):
        self.assertEqual(gate.signing_block_ids(_apk([GOOD_ARSC])), set())

    def test_a_file_that_is_not_a_zip_is_rejected(self):
        with self.assertRaises(gate.MalformedApk):
            gate.signing_block_ids(b"not a zip at all")

    def test_a_signing_block_with_disagreeing_sizes_is_rejected(self):
        """Truncation must not read as 'merely unsigned' (SIGN-070)."""
        data = bytearray(_apk([GOOD_ARSC], block_ids=(gate.V2_BLOCK_ID,)))
        eocd = data.rfind(b"PK\x05\x06")
        cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
        size_at_end = struct.unpack_from("<Q", data, cd_offset - 24)[0]
        block_start = cd_offset - size_at_end - 8
        struct.pack_into("<Q", data, block_start, size_at_end + 8)
        with self.assertRaises(gate.MalformedApk) as caught:
            gate.signing_block_ids(bytes(data))
        self.assertIn("two different sizes", str(caught.exception))


class SchemesPresent(unittest.TestCase):

    def test_v2_and_v3_come_from_the_signing_block(self):
        self.assertEqual(
            gate.schemes_present({gate.V2_BLOCK_ID, gate.V3_BLOCK_ID}, []), {"v2", "v3"})

    def test_v1_comes_from_the_zip_entries(self):
        self.assertEqual(gate.schemes_present(set(), ["META-INF/CERT.SF"]), {"v1"})

    def test_v31_reports_as_v3(self):
        self.assertEqual(gate.schemes_present({gate.V31_BLOCK_ID}, []), {"v3"})

    def test_the_published_v022_shape_is_v2_only(self):
        """The artifact from #127: v2 alone, which is what this gate reports for it."""
        self.assertEqual(gate.schemes_present({gate.V2_BLOCK_ID}, []), {"v2"})


# --- ELF alignment (BUILD-072) ----------------------------------------------


class ElfLoadAlignments(unittest.TestCase):

    def test_reads_64_bit_alignment(self):
        self.assertEqual(gate.elf_load_alignments(_elf(True, 16384)), (True, [16384]))

    def test_reads_32_bit_alignment(self):
        self.assertEqual(gate.elf_load_alignments(_elf(False, 4096)), (False, [4096]))

    def test_reads_every_load_segment(self):
        self.assertEqual(
            gate.elf_load_alignments(_elf(True, 4096, segments=3)), (True, [4096] * 3))

    def test_a_non_elf_payload_is_rejected(self):
        with self.assertRaises(gate.MalformedApk):
            gate.elf_load_alignments(b"MZ\x00\x00 not an elf")


# --- Packaging verdicts (BUILD-070–072, SIGN-070) ---------------------------


class Assess(unittest.TestCase):

    def test_a_well_formed_package_passes(self):
        verdict = gate.assess(
            _entries_of(_good_apk()), {"v1", "v2", "v3"}, ["v1", "v2", "v3"], {})
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_missing_scheme_fails_and_names_it(self):
        verdict = gate.assess(_entries_of(_good_apk()), {"v2"}, ["v1", "v2", "v3"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("v1", verdict.reasons[0])
        self.assertIn("v3", verdict.reasons[0])

    def test_a_missing_scheme_is_not_described_as_an_install_failure(self):
        """BUILD-070's second invariant: a v2-only APK installs from Android 7.0.

        The scheme check reports a departure from the build's stated intent.
        Borrowing the installer's language from the checks beside it would
        state something false, and a gate that overstates one consequence
        teaches its reader to discount the rest.
        """
        verdict = gate.assess(_entries_of(_good_apk()), {"v2"}, ["v1", "v2", "v3"], {})
        text = " ".join(verdict.reasons).lower()
        for false_claim in ("refus", "cannot be installed", "will not install", "crash"):
            self.assertNotIn(false_claim, text)

    def test_compressed_resources_arsc_fails(self):
        data = _apk([(gate.RESOURCES_ARSC, b"arsc" * 64, False, 0)])
        verdict = gate.assess(_entries_of(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn(
            "INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED", " ".join(verdict.reasons))

    def test_misaligned_resources_arsc_fails(self):
        # "ab" is chosen so the second entry's local header starts at an odd
        # offset, putting its payload off a 4-byte boundary — the condition
        # under test, rather than whatever a convenient layout happens to give.
        data = _apk([("ab", b"x", True, 0), (gate.RESOURCES_ARSC, b"arsc", True, 0)])
        entries = _entries_of(data)
        arsc = [e for e in entries if e.name == gate.RESOURCES_ARSC][0]
        self.assertNotEqual(arsc.offset % 4, 0, "the fixture must actually be misaligned")
        verdict = gate.assess(entries, {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("not 4-byte aligned", " ".join(verdict.reasons))

    def test_a_missing_resources_arsc_fails(self):
        data = _apk([("classes.dex", b"dex", True, 0)])
        verdict = gate.assess(_entries_of(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("no resources.arsc", " ".join(verdict.reasons))

    def test_a_compressed_native_library_fails(self):
        data = _apk([
            GOOD_ARSC, ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, False, 0)])
        verdict = gate.assess(_entries_of(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("must be stored uncompressed", " ".join(verdict.reasons))

    def test_a_misaligned_native_library_fails(self):
        data = _apk([
            GOOD_ARSC, ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, True, 0)])
        entries = _entries_of(data)
        lib = [e for e in entries if e.name.endswith(".so")][0]
        self.assertNotEqual(lib.offset % gate.NATIVE_LIB_ALIGNMENT, 0)
        verdict = gate.assess(entries, {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("16 KB memory pages", " ".join(verdict.reasons))

    def test_an_aligned_native_library_passes(self):
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            _entries_of(data), {"v2"}, ["v2"], {"lib/arm64-v8a/libfoo.so": (True, [16384])})
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_64_bit_library_with_4kb_elf_alignment_fails(self):
        """The failure that installs cleanly and then crashes (BUILD-072)."""
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf(True, 4096) + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            _entries_of(data), {"v2"}, ["v2"], {"lib/arm64-v8a/libfoo.so": (True, [4096])})
        self.assertFalse(verdict.ok)
        self.assertIn("crashes when first loaded", " ".join(verdict.reasons))

    def test_a_32_bit_library_with_4kb_elf_alignment_passes(self):
        """16 KB pages are a 64-bit concern; a 32-bit ABI must not be failed for it."""
        data = _apk([
            GOOD_ARSC,
            ("lib/armeabi-v7a/libfoo.so", _elf(False, 4096) + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            _entries_of(data), {"v2"}, ["v2"], {"lib/armeabi-v7a/libfoo.so": (False, [4096])})
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_library_with_no_load_segment_fails(self):
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            _entries_of(data), {"v2"}, ["v2"], {"lib/arm64-v8a/libfoo.so": (True, [])})
        self.assertFalse(verdict.ok)
        self.assertIn("not a loadable library", " ".join(verdict.reasons))

    def test_every_reason_is_collected_not_just_the_first(self):
        """One run reports everything wrong, rather than one CI round-trip each."""
        data = _apk([
            (gate.RESOURCES_ARSC, b"arsc" * 64, False, 0),
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, False, 0)])
        verdict = gate.assess(_entries_of(data), set(), ["v1", "v2"], {})
        self.assertFalse(verdict.ok)
        self.assertGreaterEqual(len(verdict.reasons), 3)


# --- Wiring -----------------------------------------------------------------


class ApkOnDisk(unittest.TestCase):

    def _write(self, data, name="interval-trainer-0.0.0-release.apk"):
        directory = tempfile.mkdtemp()
        path = os.path.join(directory, name)
        with open(path, "wb") as handle:
            handle.write(data)
        return path

    def _run(self, argv):
        """`main(argv)` with its stdout captured, as `(exit_code, output)`."""
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = gate.main(argv)
        return code, buffer.getvalue()

    def test_exit_zero_for_a_good_package(self):
        code, _ = self._run([self._write(_good_apk()), "--require-schemes", "v1,v2,v3"])
        self.assertEqual(code, 0)

    def test_exit_one_for_a_bad_package(self):
        code, _ = self._run([self._write(_apk([(gate.RESOURCES_ARSC, b"arsc", False, 0)]))])
        self.assertEqual(code, 1)

    def test_a_file_that_is_not_an_apk_fails_rather_than_crashing(self):
        code, _ = self._run([self._write(b"definitely not a zip")])
        self.assertEqual(code, 1)

    def test_every_apk_is_checked_not_just_until_the_first_failure(self):
        bad = self._write(_apk([(gate.RESOURCES_ARSC, b"arsc", False, 0)]), "bad.apk")
        good = self._write(_good_apk(), "good.apk")
        code, output = self._run([bad, good, "--require-schemes", "v1,v2,v3"])
        self.assertEqual(code, 1)
        self.assertIn("bad.apk", output)
        self.assertIn("good.apk", output)

    # --- The digest sidecar (BUILD-075) ------------------------------------

    def test_the_gate_writes_a_sha256_sidecar_beside_each_apk(self):
        """BUILD-075: what a reporter with no PC checks a download against."""
        import hashlib
        data = _good_apk()
        path = self._write(data)
        code, _ = self._run([path, "--require-schemes", "v1,v2,v3"])
        self.assertEqual(code, 0)
        with open(path + ".sha256", encoding="utf-8") as handle:
            written = handle.read()
        self.assertEqual(
            written,
            "{0}  {1}\n".format(hashlib.sha256(data).hexdigest(), os.path.basename(path)),
            "the sidecar must be in sha256sum's own format, so `sha256sum -c` reads it")

    def test_the_sidecar_is_written_even_when_the_verdict_fails(self):
        """A failing artifact is the one whose identity most needs recording."""
        path = self._write(_apk([(gate.RESOURCES_ARSC, b"arsc", False, 0)]))
        code, _ = self._run([path])
        self.assertEqual(code, 1)
        self.assertTrue(os.path.exists(path + ".sha256"))

    def test_the_digest_is_printed_as_well_as_written(self):
        import hashlib
        data = _good_apk()
        path = self._write(data)
        _, output = self._run([path, "--require-schemes", "v1,v2,v3"])
        self.assertIn(hashlib.sha256(data).hexdigest(), output)

    # --- What the summary is allowed to claim (BUILD-070, invariant) -------

    def test_the_summary_for_a_scheme_shortfall_claims_no_install_failure(self):
        """A v2-only APK installs from Android 7.0; saying otherwise is false."""
        path = self._write(_apk([GOOD_ARSC], block_ids=(gate.V2_BLOCK_ID,)))
        code, output = self._run([path, "--require-schemes", "v1,v2,v3"])
        self.assertEqual(code, 1)
        self.assertNotIn("would be refused by the Android installer", output)
        self.assertNotIn("would crash once installed", output)


class WorkflowWiring(unittest.TestCase):
    """BUILD-073–075: where the gate runs and what it publishes, read from the workflows.

    The same technique `test_verify_release_signature.py`'s
    `IndependentVerificationScriptTests` uses, and for the same reason issue
    #125 established: which *checkout* a gate's script comes from is the fact
    that decides whether a fix to the gate protects the release being built,
    and nothing about running the gate locally can check it.
    """

    WORKFLOWS = os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "workflows")
    GATE = "verify_release_package.py"

    def _read(self, name):
        with open(os.path.join(self.WORKFLOWS, name), encoding="utf-8") as handle:
            return handle.read()

    def test_both_release_please_jobs_run_the_gate_from_the_main_checkout(self):
        """BUILD-073: never the copy the tag's own tree carries (cf. BUILD-068)."""
        invocations = [
            line.strip() for line in self._read("release-please.yml").splitlines()
            if self.GATE in line
        ]
        self.assertEqual(
            len(invocations), 2,
            "expected build-and-attach and backfill-release-apk each to run the gate")
        for line in invocations:
            self.assertIn(
                "verification-gate/.github/scripts/" + self.GATE, line,
                "the gate must come from the second `main` checkout, not the tag's tree")

    def test_the_release_candidate_runs_the_gate(self):
        """BUILD-073: the candidate is the artifact a human actually installs."""
        self.assertIn(self.GATE, self._read("release-candidate.yml"))

    def test_the_release_candidate_declares_a_workflow_dispatch_ref_input(self):
        """BUILD-074: otherwise a fix and the means of trying it stay on opposite
        sides of a release. Read as text, so this suite needs no YAML library."""
        text = self._read("release-candidate.yml")
        self.assertIn("workflow_dispatch:", text)
        dispatch = text.split("workflow_dispatch:", 1)[1].split("permissions:", 1)[0]
        self.assertIn("ref:", dispatch)

    def test_both_release_jobs_upload_the_digest_beside_the_apk(self):
        """BUILD-075: the digest is only useful where the download happens."""
        text = self._read("release-please.yml")
        uploads = [
            block for block in text.split("gh release upload")[1:]
        ]
        self.assertEqual(len(uploads), 2, "expected two `gh release upload` invocations")
        for block in uploads:
            command = block.split("--repo", 1)[0]
            self.assertIn(".apk.sha256", command,
                          "the release asset must be published with its digest")

    def test_the_release_candidate_artifact_carries_the_digest(self):
        """BUILD-075: the candidate is what someone with no PC installs."""
        text = self._read("release-candidate.yml")
        upload = text.split("actions/upload-artifact", 1)[1]
        self.assertIn(".sha256", upload.split("if-no-files-found", 1)[0])

    def test_the_gate_imports_only_the_standard_library(self):
        """SIGN-061's property, extended to this gate: it must run anywhere."""
        source = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))), self.GATE)
        with open(source, encoding="utf-8") as handle:
            imported = {
                line.split()[1].split(".")[0]
                for line in handle
                if line.startswith("import ") or line.startswith("from ")
            }
        self.assertTrue(imported <= set(sys.stdlib_module_names), imported)


if __name__ == "__main__":
    unittest.main()
