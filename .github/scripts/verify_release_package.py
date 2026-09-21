#!/usr/bin/env python3
"""Release gate: a published release APK is one a device will actually install (#127).

**Why this exists.** `verify_release_signature.py` proves an APK carries the
right *identity* — the pinned release certificate. It says nothing about
whether the package is *installable*. Issue #127 is exactly that gap: three
v0.2.x releases shipped an APK that passed the signature gate and that a device
still refused with "App not installed as the package seems invalid", Android's
one generic message for every parse-or-verify failure.

**The invariant this enforces.** *A published release artifact satisfies every
packaging requirement the Android installer enforces at install time.* Those
requirements are few, specific, and each has a known failure:

- `resources.arsc` must be **uncompressed and 4-byte aligned** for an app
  targeting SDK 30 or later. A compressed one is refused outright
  (`INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`).
- Native libraries in an APK declaring `extractNativeLibs="false"` must be
  **uncompressed**, because they are mapped from the APK rather than unpacked.
- Those libraries must be **16 KB aligned inside the zip**, and their ELF LOAD
  segments must carry `p_align` of at least 16 KB, or they cannot be mapped on
  a device with 16 KB memory pages. The zip side fails the install; the ELF
  side loads and then crashes at the first call into the library, which is
  worse, because it ships.
- The **signature schemes** the build intends must actually be present. A
  scheme silently dropped by a Gradle or AGP upgrade narrows the set of
  installers that will accept the artifact, and nothing else reports it.

**What this gate deliberately does not check.** The manifest's `minSdk`,
`targetSdk` and `versionCode`. Reading those means `aapt2`, which means the
Android SDK, which would make this gate — and its tests — unrunnable anywhere
but a provisioned CI runner. None of those values can fail an install on their
own, so the cost buys nothing. `verify_release_signature.py` already needs
apksigner; keeping *this* gate dependency-free is what lets a maintainer run it
against a downloaded release asset on their own laptop, which is how #127's
artifact was finally cleared.

**Everything here reads the APK file directly** — the zip central directory,
the APK Signing Block, and the ELF program headers — with the standard library
alone. No Android SDK, no apksigner, no zipalign, no network.

The decisions are pure functions, so they unit-test against bytes:
`signing_block_ids`, `schemes_present`, `elf_load_alignments`,
`inspect_entries` and `assess`. `main` wires them to the filesystem.
"""

import argparse
import hashlib
import os
import struct
import sys
import zipfile
from collections import namedtuple

EntryFacts = namedtuple("EntryFacts", "name stored size offset")
Verdict = namedtuple("Verdict", "ok reasons notes")

# APK Signing Block pair IDs. The block is a sequence of length-prefixed
# id/value pairs sitting between the last zip entry and the central directory;
# a scheme is "present" exactly when its ID appears. Reading the IDs is enough
# to report which schemes signed the APK — verifying them is apksigner's job,
# and `verify_release_signature.py` already does it.
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0
V31_BLOCK_ID = 0x1B93AD61

# The zip alignment a 16 KB-page device needs for a mapped native library, and
# the 4-byte alignment `resources.arsc` needs. Both are offsets of the entry's
# *payload*, not of its local header.
NATIVE_LIB_ALIGNMENT = 16 * 1024
RESOURCES_ARSC_ALIGNMENT = 4
RESOURCES_ARSC = "resources.arsc"

# ELF constants: PT_LOAD is the only segment type whose alignment the loader
# must satisfy from the mapped file.
PT_LOAD = 1
ELF_MAGIC = b"\x7fELF"

_STORED = 0


class MalformedApk(ValueError):
    """A file this gate cannot read as an APK with confidence."""


# --- Pure decisions ---------------------------------------------------------


def signing_block_ids(data):
    """The set of APK Signing Block pair IDs in `data` (SIGN-070).

    Returns an empty set when there is no signing block at all, which is what a
    v1-only (JAR-signed) or entirely unsigned APK looks like — both are
    reportable states rather than parse errors, so neither raises.

    Raises `MalformedApk` only when a block *is* present but its framing is
    self-inconsistent: the block carries its own size twice, at both ends, and
    a mismatch means the file is truncated or rewritten rather than merely
    unsigned. Treating that as "no schemes" would report a corrupted artifact
    as an ordinary missing-signature failure and send the reader hunting in the
    wrong place.
    """
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise MalformedApk("no zip end-of-central-directory record — not a zip file")
    central_dir_offset = struct.unpack_from("<I", data, eocd + 16)[0]

    magic_at = central_dir_offset - 24
    if magic_at < 8 or data[magic_at + 8:magic_at + 24] != SIGNING_BLOCK_MAGIC:
        return set()

    size_at_end = struct.unpack_from("<Q", data, magic_at)[0]
    block_start = central_dir_offset - size_at_end - 8
    if block_start < 0:
        raise MalformedApk("APK Signing Block size runs past the start of the file")
    size_at_start = struct.unpack_from("<Q", data, block_start)[0]
    if size_at_start != size_at_end:
        raise MalformedApk(
            "APK Signing Block declares two different sizes ({0} at its start, {1} at "
            "its end) — the file is truncated or was rewritten after signing".format(
                size_at_start, size_at_end))

    ids = set()
    cursor = block_start + 8
    limit = central_dir_offset - 24
    while cursor < limit:
        pair_length = struct.unpack_from("<Q", data, cursor)[0]
        if pair_length < 4 or cursor + 8 + pair_length > central_dir_offset:
            raise MalformedApk(
                "APK Signing Block pair at offset {0} declares an impossible length "
                "{1}".format(cursor, pair_length))
        ids.add(struct.unpack_from("<I", data, cursor + 8)[0])
        cursor += 8 + pair_length
    return ids


def schemes_present(block_ids, entry_names):
    """Which signature schemes signed this APK, as a set of `"v1"`/`"v2"`/`"v3"` (SIGN-070).

    v1 (JAR signing) leaves no signing-block entry; it is a `META-INF/*.SF`
    file beside a matching signature block, so it is detected from the zip's
    own entry list instead. v3.1 implies v3 for reporting purposes — it is an
    additional rotation-aware block, not a replacement — so it is reported as
    v3 rather than as a scheme of its own.
    """
    present = set()
    if any(
            name.upper().startswith("META-INF/") and name.upper().endswith(".SF")
            for name in entry_names):
        present.add("v1")
    if V2_BLOCK_ID in block_ids:
        present.add("v2")
    if V3_BLOCK_ID in block_ids or V31_BLOCK_ID in block_ids:
        present.add("v3")
    return present


def elf_load_alignments(blob):
    """`p_align` of every PT_LOAD segment in an ELF image, with its class (BUILD-072).

    Returns `(is_64_bit, [p_align, ...])`. Only 64-bit libraries are subject to
    the 16 KB page requirement — a 16 KB-page device runs 64-bit code — so the
    class travels with the alignments rather than being rediscovered by the
    caller.

    Raises `MalformedApk` for anything that is not an ELF image, since a `.so`
    entry that is not one has no business in `lib/`.
    """
    if blob[:4] != ELF_MAGIC:
        raise MalformedApk("not an ELF image")

    is_64 = blob[4] == 2
    endian = "<" if blob[5] == 1 else ">"

    if is_64:
        ph_offset = struct.unpack_from(endian + "Q", blob, 0x20)[0]
        ph_entry_size, ph_count = struct.unpack_from(endian + "HH", blob, 0x36)
        align_at, type_at = 0x30, 0x00
    else:
        ph_offset = struct.unpack_from(endian + "I", blob, 0x1C)[0]
        ph_entry_size, ph_count = struct.unpack_from(endian + "HH", blob, 0x2A)
        align_at, type_at = 0x1C, 0x00

    alignments = []
    for index in range(ph_count):
        base = ph_offset + index * ph_entry_size
        if base + ph_entry_size > len(blob):
            raise MalformedApk("ELF program header table runs past the end of the image")
        if struct.unpack_from(endian + "I", blob, base + type_at)[0] != PT_LOAD:
            continue
        fmt = "Q" if is_64 else "I"
        alignments.append(struct.unpack_from(endian + fmt, blob, base + align_at)[0])
    return is_64, alignments


def payload_offset(data, info):
    """Where an entry's bytes actually start, past its local header (BUILD-071).

    `ZipInfo.header_offset` points at the local file header, whose length
    varies with the entry's own name and extra field — and zipalign pads using
    precisely that extra field, so the header length is exactly what alignment
    is expressed in. Computing the payload offset from the local header (rather
    than from the central directory's copy, whose extra field differs) is the
    only way to read the alignment the device will see.
    """
    name_length, extra_length = struct.unpack_from("<HH", data, info.header_offset + 26)
    return info.header_offset + 30 + name_length + extra_length


def inspect_entries(data, archive):
    """Facts about every entry, as `EntryFacts` in central-directory order."""
    return [
        EntryFacts(
            name=info.filename,
            stored=info.compress_type == _STORED,
            size=info.file_size,
            offset=payload_offset(data, info))
        for info in archive.infolist()
    ]


def assess(entries, present, required_schemes, native_lib_alignments):
    """Whether this package is one a device will install (BUILD-070–073, SIGN-070).

    `native_lib_alignments` maps a `lib/**/*.so` entry name to the
    `(is_64_bit, [p_align, ...])` that `elf_load_alignments` read from it.
    Taking it as an argument rather than reading the archive keeps this
    decision pure, and lets a test state an ELF shape without building one.

    Collects every reason rather than returning at the first, so one run
    reports everything wrong with an artifact. A gate that stops at the first
    failure turns a single broken release into a sequence of CI round-trips.
    """
    reasons = []
    notes = []

    missing = sorted(set(required_schemes) - present)
    if missing and not present:
        reasons.append(
            "carries no signature at all; a release artifact must be signed with {0}".format(
                ", ".join(missing)))
    elif missing:
        reasons.append(
            "is signed with {0} but not {1} — a release artifact must carry every scheme "
            "the build configures, so the widest set of installers accepts it".format(
                ", ".join(sorted(present)), ", ".join(missing)))
    else:
        notes.append("signature schemes: {0}".format(", ".join(sorted(present))))

    arsc = [entry for entry in entries if entry.name == RESOURCES_ARSC]
    if not arsc:
        reasons.append(
            "no {0} — every APK built from Android resources carries one".format(
                RESOURCES_ARSC))
    for entry in arsc:
        if not entry.stored:
            reasons.append(
                "{0} is compressed; an app targeting SDK 30 or later is refused at "
                "install with INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED".format(
                    entry.name))
        if entry.offset % RESOURCES_ARSC_ALIGNMENT:
            reasons.append(
                "{0} starts at offset {1}, which is not {2}-byte aligned; it is mapped "
                "directly and cannot be read unaligned".format(
                    entry.name, entry.offset, RESOURCES_ARSC_ALIGNMENT))

    native = [
        entry for entry in entries
        if entry.name.startswith("lib/") and entry.name.endswith(".so")
    ]
    for entry in native:
        if not entry.stored:
            reasons.append(
                "{0} is compressed; a library mapped from the APK "
                "(extractNativeLibs=\"false\") must be stored uncompressed".format(
                    entry.name))
            continue
        if entry.offset % NATIVE_LIB_ALIGNMENT:
            reasons.append(
                "{0} starts at offset {1}, which is not {2}-byte aligned; a device with "
                "16 KB memory pages cannot map it and refuses the install".format(
                    entry.name, entry.offset, NATIVE_LIB_ALIGNMENT))

        shape = native_lib_alignments.get(entry.name)
        if shape is None:
            continue
        is_64, alignments = shape
        if not alignments:
            reasons.append("{0} has no PT_LOAD segment — it is not a loadable "
                           "library".format(entry.name))
        elif is_64 and min(alignments) < NATIVE_LIB_ALIGNMENT:
            reasons.append(
                "{0} has ELF LOAD segments aligned to {1}, below the {2} a 16 KB-page "
                "device requires; it installs and then crashes when first "
                "loaded".format(entry.name, min(alignments), NATIVE_LIB_ALIGNMENT))

    if native:
        notes.append("{0} native librar{1} checked".format(
            len(native), "y" if len(native) == 1 else "ies"))

    return Verdict(ok=not reasons, reasons=reasons, notes=notes)


# --- Wiring -----------------------------------------------------------------


def inspect(path, required_schemes):
    """Read one APK from disk and assess it. Returns `(Verdict, sha256_hex)`."""
    with open(path, "rb") as handle:
        data = handle.read()

    digest = hashlib.sha256(data).hexdigest()

    with zipfile.ZipFile(path) as archive:
        entries = inspect_entries(data, archive)
        alignments = {}
        for entry in entries:
            if entry.name.startswith("lib/") and entry.name.endswith(".so"):
                alignments[entry.name] = elf_load_alignments(archive.read(entry.name))

    present = schemes_present(signing_block_ids(data), [e.name for e in entries])
    return assess(entries, present, required_schemes, alignments), digest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="+", help="release APK(s) to check")
    parser.add_argument(
        "--require-schemes", default="v1,v2,v3",
        help="comma-separated signature schemes the artifact must carry "
             "(default: v1,v2,v3)")
    args = parser.parse_args(argv)

    required = [s.strip() for s in args.require_schemes.split(",") if s.strip()]

    failed = False
    for apk in args.apk:
        name = os.path.basename(apk)
        try:
            verdict, digest = inspect(apk, required)
        except (MalformedApk, zipfile.BadZipFile, OSError) as exc:
            print("::error title=Release package::{0}: {1}".format(name, exc))
            failed = True
            continue

        # Printed on success as well as failure: this is the digest a maintainer
        # checks a downloaded asset against, and #127 spent a round-trip
        # establishing that a published file was intact. It costs one line.
        print("{0}\n  sha256: {1}".format(name, digest))
        for note in verdict.notes:
            print("  {0}".format(note))

        if verdict.ok:
            print("OK: {0} satisfies every install-time packaging requirement.".format(name))
            continue

        failed = True
        for reason in verdict.reasons:
            print("::error title=Release package::{0}: {1}".format(name, reason))

    if failed:
        print(
            "\nA release artifact would be refused by the Android installer, or would "
            "crash once installed — refusing to publish it. Android reports every one "
            "of these as the same message, \"App not installed as the package seems "
            "invalid\", which is why this gate names the specific cause. See issue #127 "
            "and docs/spec/build.md.")
        return 1

    print("\nOK: every release artifact is installable.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
