#!/usr/bin/env python3
"""Release gate: a published release APK is one a device will install (#127).

**Skeleton only.** The constants and the shape of each decision are here so
that `tests/test_verify_release_package.py` fails one assertion per checked
property rather than failing to import — every test in that suite is red
against this file, which is the point of it existing as its own commit. The
behaviour lands next; nothing here decides anything.

The properties it will decide are stated in `docs/spec/build.md` section 10
(`BUILD-070`–`075`) and `docs/spec/signing.md` section 8 (`SIGN-070`).
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

SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0
V31_BLOCK_ID = 0x1B93AD61

NATIVE_LIB_ALIGNMENT = 16 * 1024
RESOURCES_ARSC_ALIGNMENT = 4
RESOURCES_ARSC = "resources.arsc"

PT_LOAD = 1
ELF_MAGIC = b"\x7fELF"

_STORED = 0


class MalformedApk(ValueError):
    """A file this gate cannot read as an APK with confidence."""


def signing_block_ids(data):
    """The set of APK Signing Block pair IDs in `data` (SIGN-070)."""
    return set()


def schemes_present(block_ids, entry_names):
    """Which signature schemes signed this APK (SIGN-070)."""
    return set()


def elf_load_alignments(blob):
    """`p_align` of every PT_LOAD segment, with the ELF class (BUILD-072)."""
    return (False, [])


def payload_offset(data, info):
    """Where an entry's bytes start, past its local header (BUILD-071)."""
    return 0


def inspect_entries(data, archive):
    """Facts about every entry, in central-directory order."""
    return []


def assess(entries, present, required_schemes, native_lib_alignments):
    """Whether this package is one a device will install (BUILD-070–072)."""
    return Verdict(ok=True, reasons=[], notes=[])


def write_digest_sidecar(path, digest):
    """Write `<apk>.sha256` beside the APK, in sha256sum format (BUILD-075)."""
    return None


def inspect(path, required_schemes):
    """Read one APK from disk and assess it. Returns `(Verdict, sha256_hex)`."""
    return Verdict(ok=True, reasons=[], notes=[]), ""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="+", help="release APK(s) to check")
    parser.add_argument("--require-schemes", default="v1,v2,v3")
    parser.parse_args(argv)
    return 0


if __name__ == "__main__":
    sys.exit(main())
