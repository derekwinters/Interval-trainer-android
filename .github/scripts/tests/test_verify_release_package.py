"""Unit tests for the release-package gate (issue #127, `docs/spec/build.md`).

Three v0.2.x releases shipped an APK that the signature gate passed and a
device still refused, with Android's one generic message for every
parse-or-verify failure: "App not installed as the package seems invalid".
`verify_release_package.py` exists so that the specific, checkable reasons a
device refuses a package are checked before publication rather than discovered
on someone's phone.

These tests pin the pure decisions — `signing_block_ids` and `schemes_present`
(SIGN-070), `elf_load_alignments` (BUILD-072), `payload_offset` (BUILD-071) and
`assess` (BUILD-070–073) — and the wiring that has to agree with them. They
need no Android SDK, no keystore and no network.

**On constructing inputs rather than capturing them.** `fixtures/README.md`
warns, from a release that shipped with zero assets, against hand-authoring
what another tool *prints*: a transcript written from documentation certifies a
parser that has never read real output. That warning is about prose formats
owned by someone else's release notes. It does not apply here, because this
gate parses no prose. It reads the zip central directory, the APK Signing Block
and the ELF program header — three published binary standards — so a test that
builds one is building the real structure to the real specification, not a
plausible-looking imitation of a tool's output. `_apk` below emits genuine zip
records that `zipfile` itself reads back, which is the check that they are real.

That argument would still be worth distrusting on its own, so it is not on its
own: the gate was additionally run end to end against the actual published
`interval-trainer-0.2.2-release.apk` — the 19.5 MB artifact from issue #127 —
and its verdict there agreed with these constructed cases. That artifact is far
too large to commit, which is exactly why the constructed cases are what runs
in CI.
"""

import os
import struct
import sys
import unittest
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

import verify_release_package as gate  # noqa: E402


# --- Building APKs to order -------------------------------------------------


def _local_header(name, payload, stored, align):
    """One local file header plus payload, padded so the payload hits `align`.

    Alignment is expressed in the extra field, which is what zipalign itself
    pads — the same mechanism, so the offsets these tests assert are the
    offsets a device would compute.
    """
    raw = name.encode()
    method = 0 if stored else 8
    if stored:
        body = payload
    else:
        import zlib
        compressor = zlib.compressobj(6, zlib.DEFLATED, -15)
        body = compressor.compress(payload) + compressor.flush()

    import binascii
    crc = binascii.crc32(payload) & 0xFFFFFFFF

    def build(extra_length):
        return struct.pack(
            "<IHHHHHIIIHH", 0x04034B50, 20, 0, method, 0, 0,
            crc, len(body), len(payload), len(raw), extra_length)

    extra = b""
    if align:
        # `header_so_far` is where the payload would start with no extra field.
        def payload_at(extra_length):
            return _local_header.cursor + 30 + len(raw) + extra_length
        needed = 0
        while payload_at(needed) % align:
            needed += 1
        extra = b"\x00" * needed

    return build(len(extra)) + raw + extra + body, crc, len(body)


_local_header.cursor = 0


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
        _local_header.cursor = len(out)
        offset = len(out)
        record, crc, csize = _local_header(name, payload, stored, align)
        out += record
        central.append((name, crc, csize, len(payload), 0 if stored else 8, offset))

    if block_ids:
        pairs = bytearray()
        for pair_id in block_ids:
            value = b"\x00" * 8
            pairs += struct.pack("<Q", 4 + len(value)) + struct.pack("<I", pair_id) + value
        # size field = pairs + the trailing 8-byte size + 16-byte magic
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
        header = bytearray(0x40 + segments * 0x38)
        header[0:4] = gate.ELF_MAGIC
        header[4] = 2
        header[5] = 1
        struct.pack_into("<Q", header, 0x20, 0x40)
        struct.pack_into("<HH", header, 0x36, 0x38, segments)
        for index in range(segments):
            base = 0x40 + index * 0x38
            struct.pack_into("<I", header, base, gate.PT_LOAD)
            struct.pack_into("<Q", header, base + 0x30, p_align)
    else:
        header = bytearray(0x34 + segments * 0x20)
        header[0:4] = gate.ELF_MAGIC
        header[4] = 1
        header[5] = 1
        struct.pack_into("<I", header, 0x1C, 0x34)
        struct.pack_into("<HH", header, 0x2A, 0x20, segments)
        for index in range(segments):
            base = 0x34 + index * 0x20
            struct.pack_into("<I", header, base, gate.PT_LOAD)
            struct.pack_into("<I", header, base + 0x1C, p_align)
    return bytes(header)


GOOD_ARSC = (gate.RESOURCES_ARSC, b"arsc" * 64, True, gate.RESOURCES_ARSC_ALIGNMENT)


def _good_apk():
    return _apk(
        [("classes.dex", b"dex" * 100, False, 0), GOOD_ARSC],
        block_ids=(gate.V2_BLOCK_ID, gate.V3_BLOCK_ID), v1=True)


# --- The zips these tests build are real zips -------------------------------


class ConstructedApksAreRealZips(unittest.TestCase):
    """The premise every other test rests on (see this module's docstring)."""

    def test_zipfile_reads_back_what_apk_built(self):
        import io
        data = _good_apk()
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn(gate.RESOURCES_ARSC, archive.namelist())
            self.assertEqual(archive.read("classes.dex"), b"dex" * 100)


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
        # Corrupt the size written at the block's start only.
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
        """The artifact from #127: v2 alone, which is what this gate now reports."""
        self.assertEqual(gate.schemes_present({gate.V2_BLOCK_ID}, []), {"v2"})


# --- ELF alignment (BUILD-072) ----------------------------------------------


class ElfLoadAlignments(unittest.TestCase):

    def test_reads_64_bit_alignment(self):
        self.assertEqual(gate.elf_load_alignments(_elf(True, 16384)), (True, [16384]))

    def test_reads_32_bit_alignment(self):
        self.assertEqual(gate.elf_load_alignments(_elf(False, 4096)), (False, [4096]))

    def test_reads_every_load_segment(self):
        is_64, alignments = gate.elf_load_alignments(_elf(True, 4096, segments=3))
        self.assertEqual((is_64, alignments), (True, [4096, 4096, 4096]))

    def test_a_non_elf_payload_is_rejected(self):
        with self.assertRaises(gate.MalformedApk):
            gate.elf_load_alignments(b"MZ\x00\x00 not an elf")


# --- Packaging verdicts (BUILD-070–073) -------------------------------------


class Assess(unittest.TestCase):

    def _entries(self, data):
        import io
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            return gate.inspect_entries(data, archive)

    def test_a_well_formed_package_passes(self):
        verdict = gate.assess(
            self._entries(_good_apk()), {"v1", "v2", "v3"}, ["v1", "v2", "v3"], {})
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_missing_scheme_fails_and_names_it(self):
        verdict = gate.assess(
            self._entries(_good_apk()), {"v2"}, ["v1", "v2", "v3"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("v1", verdict.reasons[0])
        self.assertIn("v3", verdict.reasons[0])

    def test_compressed_resources_arsc_fails(self):
        data = _apk([(gate.RESOURCES_ARSC, b"arsc" * 64, False, 0)])
        verdict = gate.assess(self._entries(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED",
                      " ".join(verdict.reasons))

    def test_misaligned_resources_arsc_fails(self):
        # "ab" is chosen so the second entry's local header starts at an odd
        # offset, putting its payload off a 4-byte boundary — the condition
        # under test, rather than whatever a convenient layout happens to give.
        data = _apk([("ab", b"x", True, 0), (gate.RESOURCES_ARSC, b"arsc", True, 0)])
        entries = self._entries(data)
        arsc = [e for e in entries if e.name == gate.RESOURCES_ARSC][0]
        self.assertNotEqual(arsc.offset % 4, 0, "the fixture must actually be misaligned")
        verdict = gate.assess(entries, {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("not 4-byte aligned", " ".join(verdict.reasons))

    def test_a_missing_resources_arsc_fails(self):
        data = _apk([("classes.dex", b"dex", True, 0)])
        verdict = gate.assess(self._entries(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("no resources.arsc", " ".join(verdict.reasons))

    def test_a_compressed_native_library_fails(self):
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, False, 0)])
        verdict = gate.assess(self._entries(data), {"v2"}, ["v2"], {})
        self.assertFalse(verdict.ok)
        self.assertIn("must be stored uncompressed", " ".join(verdict.reasons))

    def test_a_misaligned_native_library_fails(self):
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, True, 0)])
        entries = self._entries(data)
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
        entries = self._entries(data)
        alignments = {"lib/arm64-v8a/libfoo.so": (True, [16384])}
        verdict = gate.assess(entries, {"v2"}, ["v2"], alignments)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_64_bit_library_with_4kb_elf_alignment_fails(self):
        """The failure that installs cleanly and then crashes (BUILD-072)."""
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf(True, 4096) + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            self._entries(data), {"v2"}, ["v2"],
            {"lib/arm64-v8a/libfoo.so": (True, [4096])})
        self.assertFalse(verdict.ok)
        self.assertIn("crashes when first loaded", " ".join(verdict.reasons))

    def test_a_32_bit_library_with_4kb_elf_alignment_passes(self):
        """16 KB pages are a 64-bit concern; a 32-bit ABI must not be failed for it."""
        data = _apk([
            GOOD_ARSC,
            ("lib/armeabi-v7a/libfoo.so", _elf(False, 4096) + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            self._entries(data), {"v2"}, ["v2"],
            {"lib/armeabi-v7a/libfoo.so": (False, [4096])})
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_library_with_no_load_segment_fails(self):
        data = _apk([
            GOOD_ARSC,
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, True,
             gate.NATIVE_LIB_ALIGNMENT)])
        verdict = gate.assess(
            self._entries(data), {"v2"}, ["v2"],
            {"lib/arm64-v8a/libfoo.so": (True, [])})
        self.assertFalse(verdict.ok)
        self.assertIn("not a loadable library", " ".join(verdict.reasons))

    def test_every_reason_is_collected_not_just_the_first(self):
        """One run reports everything wrong, rather than one CI round-trip each."""
        data = _apk([
            (gate.RESOURCES_ARSC, b"arsc" * 64, False, 0),
            ("lib/arm64-v8a/libfoo.so", _elf() + b"\x00" * 200, False, 0)])
        verdict = gate.assess(self._entries(data), set(), ["v1", "v2"], {})
        self.assertFalse(verdict.ok)
        self.assertGreaterEqual(len(verdict.reasons), 3)


# --- Wiring -----------------------------------------------------------------


class Main(unittest.TestCase):

    def _write(self, data):
        import tempfile
        handle = tempfile.NamedTemporaryFile(suffix=".apk", delete=False)
        handle.write(data)
        handle.close()
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def test_exit_zero_for_a_good_package(self):
        self.assertEqual(gate.main([self._write(_good_apk()), "--require-schemes", "v1,v2,v3"]), 0)

    def test_exit_one_for_a_bad_package(self):
        data = _apk([(gate.RESOURCES_ARSC, b"arsc", False, 0)])
        self.assertEqual(gate.main([self._write(data)]), 1)

    def test_a_file_that_is_not_an_apk_fails_rather_than_crashing(self):
        self.assertEqual(gate.main([self._write(b"definitely not a zip")]), 1)

    def test_every_apk_is_checked_not_just_until_the_first_failure(self):
        bad = self._write(_apk([(gate.RESOURCES_ARSC, b"arsc", False, 0)]))
        good = self._write(_good_apk())
        self.assertEqual(gate.main([bad, good, "--require-schemes", "v1,v2,v3"]), 1)


class WorkflowWiring(unittest.TestCase):
    """BUILD-073, BUILD-074: where the gate runs, read from the workflow files themselves.

    The same technique `test_verify_release_signature.py`'s
    `IndependentVerificationScriptTests` uses, and for the same reason issue #125
    established: which *checkout* a gate's script comes from is the fact that
    actually decides whether a fix to the gate protects the release being built,
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
        text = self._read("release-please.yml")
        invocations = [
            line.strip() for line in text.splitlines() if self.GATE in line
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
        dispatch = text.split("workflow_dispatch:", 1)[1]
        self.assertIn("ref:", dispatch.split("permissions:", 1)[0])

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
