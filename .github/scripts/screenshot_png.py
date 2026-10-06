#!/usr/bin/env python3
"""Re-encode the emulator's screenshots as Play Store-ready PNGs (`docs/spec/build.md` BUILD-086).

`UiAutomation.takeScreenshot()` gives an ARGB bitmap, and `Bitmap.compress` writes it as an RGBA
PNG. The Play Store wants 24-bit PNG or JPEG with no alpha channel. So each capture is decoded,
any alpha is composited over black, and the result is written as 8-bit RGB in one canonical
encoding: Sub filter on every row, one IDAT, zlib level 9, no ancillary chunks. The same pixels
therefore always give the same bytes, which the byte-wise comparison against `docs/screenshots/`
depends on (the determinism invariant in build.md section 11).

Each image is also checked against the Play Store screenshot limits as stated on issue #161: each
side between 320 and 3840 px, and the long side no more than twice the short side.

Usage:  screenshot_png.py <captured_dir> <output_dir>
Exits 0 when all six expected screenshots are present, valid and written; 1 otherwise.
Standard library only.
"""

from __future__ import annotations

import os
import struct
import sys
import zlib
from typing import NamedTuple

EXPECTED = (
    "01-first-run.png",
    "02-home.png",
    "03-editor.png",
    "04-running-paused.png",
    "05-summary.png",
    "06-settings.png",
)

MIN_SIDE = 320
MAX_SIDE = 3840
MAX_RATIO = 2

_SIGNATURE = b"\x89PNG\r\n\x1a\n"
_CHANNELS = {2: 3, 6: 4}  # colour type -> channels, for the two the device writes


class Image(NamedTuple):
    width: int
    height: int
    channels: int  # 3 (RGB) or 4 (RGBA)
    pixels: bytes  # unfiltered rows, concatenated


def _chunks(data: bytes):
    if not data.startswith(_SIGNATURE):
        raise ValueError("not a PNG file")
    pos = len(_SIGNATURE)
    while pos + 8 <= len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if len(body) != length:
            raise ValueError("truncated PNG chunk")
        yield kind, body
        pos += 12 + length
        if kind == b"IEND":
            return
    raise ValueError("PNG has no IEND chunk")


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def _unfilter(kind: int, line: bytes, prev: bytes, bpp: int) -> bytes:
    if kind == 0:
        return line
    out = bytearray(line)
    n = len(out)
    if kind == 1:
        for i in range(bpp, n):
            out[i] = (out[i] + out[i - bpp]) & 0xFF
    elif kind == 2:
        for i in range(n):
            out[i] = (out[i] + prev[i]) & 0xFF
    elif kind == 3:
        for i in range(n):
            left = out[i - bpp] if i >= bpp else 0
            out[i] = (out[i] + ((left + prev[i]) >> 1)) & 0xFF
    elif kind == 4:
        for i in range(n):
            if i >= bpp:
                out[i] = (out[i] + _paeth(out[i - bpp], prev[i], prev[i - bpp])) & 0xFF
            else:
                out[i] = (out[i] + prev[i]) & 0xFF
    else:
        raise ValueError("unknown PNG row filter {0}".format(kind))
    return bytes(out)


def decode(data: bytes) -> Image:
    """Decode an 8-bit, non-interlaced RGB or RGBA PNG. Anything else is refused, not guessed."""
    header = None
    idat = bytearray()
    for kind, body in _chunks(data):
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
    if header is None:
        raise ValueError("PNG has no IHDR chunk")
    width, height, depth, colour_type, compression, filter_method, interlace = header
    if depth != 8:
        raise ValueError("unsupported bit depth {0}; expected 8".format(depth))
    if colour_type not in _CHANNELS:
        raise ValueError("unsupported colour type {0}; expected RGB or RGBA".format(colour_type))
    if compression != 0 or filter_method != 0:
        raise ValueError("unsupported PNG compression or filter method")
    if interlace != 0:
        raise ValueError("interlaced PNGs are not supported")
    channels = _CHANNELS[colour_type]
    stride = width * channels
    raw = zlib.decompress(bytes(idat))
    if len(raw) != height * (stride + 1):
        raise ValueError("PNG image data has the wrong length")
    rows = bytearray()
    prev = bytes(stride)
    for y in range(height):
        start = y * (stride + 1)
        line = _unfilter(raw[start], raw[start + 1:start + 1 + stride], prev, channels)
        rows += line
        prev = line
    return Image(width, height, channels, bytes(rows))


def to_rgb(image: Image) -> bytes:
    """The image's pixels as RGB, with any alpha composited over black."""
    if image.channels == 3:
        return image.pixels
    px = image.pixels
    count = image.width * image.height
    rgb = bytearray(count * 3)
    if px[3::4] == b"\xff" * count:
        rgb[0::3], rgb[1::3], rgb[2::3] = px[0::4], px[1::4], px[2::4]
        return bytes(rgb)
    for i in range(count):
        alpha = px[i * 4 + 3]
        for c in range(3):
            rgb[i * 3 + c] = (px[i * 4 + c] * alpha + 127) // 255
    return bytes(rgb)


def _chunk(kind: bytes, body: bytes) -> bytes:
    return (struct.pack(">I", len(body)) + kind + body
            + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))


def encode_rgb(width: int, height: int, rgb: bytes) -> bytes:
    """The one canonical encoding: 8-bit RGB, Sub filter on every row, one IDAT, level 9."""
    stride = width * 3
    raw = bytearray()
    for y in range(height):
        row = rgb[y * stride:(y + 1) * stride]
        shifted = bytes(3) + row[:-3]
        raw.append(1)
        raw += bytes((x - s) & 0xFF for x, s in zip(row, shifted))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (_SIGNATURE + _chunk(b"IHDR", ihdr)
            + _chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + _chunk(b"IEND", b""))


def flatten(data: bytes) -> bytes:
    """Any supported PNG, re-encoded as canonical 8-bit RGB with no alpha."""
    image = decode(data)
    return encode_rgb(image.width, image.height, to_rgb(image))


def play_store_problems(width: int, height: int, min_side: int = MIN_SIDE) -> list[str]:
    """Why a width × height screenshot breaks the Play Store limits; empty when it does not."""
    problems = []
    for name, side in (("width", width), ("height", height)):
        if side < min_side:
            problems.append("{0} {1} px is under {2} px".format(name, side, min_side))
        if side > MAX_SIDE:
            problems.append("{0} {1} px is over {2} px".format(name, side, MAX_SIDE))
    short, long_ = sorted((width, height))
    if long_ > MAX_RATIO * short:
        problems.append("{0} × {1} is more than {2}:1".format(width, height, MAX_RATIO))
    return problems


def main(argv: list[str], min_side: int = MIN_SIDE) -> int:
    if len(argv) != 2:
        print("usage: screenshot_png.py <captured_dir> <output_dir>", file=sys.stderr)
        return 1
    source, destination = argv
    found = sorted(name for name in os.listdir(source) if name.endswith(".png"))
    missing = [name for name in EXPECTED if name not in found]
    unexpected = [name for name in found if name not in EXPECTED]
    failed = False
    for name in missing:
        print("missing screenshot: {0}".format(name), file=sys.stderr)
        failed = True
    for name in unexpected:
        print("unexpected screenshot: {0}".format(name), file=sys.stderr)
        failed = True
    if failed:
        return 1
    os.makedirs(destination, exist_ok=True)
    for name in EXPECTED:
        with open(os.path.join(source, name), "rb") as handle:
            data = handle.read()
        try:
            image = decode(data)
        except ValueError as error:
            print("{0}: {1}".format(name, error), file=sys.stderr)
            failed = True
            continue
        problems = play_store_problems(image.width, image.height, min_side)
        for problem in problems:
            print("{0}: {1}".format(name, problem), file=sys.stderr)
        if problems:
            failed = True
            continue
        with open(os.path.join(destination, name), "wb") as handle:
            handle.write(encode_rgb(image.width, image.height, to_rgb(image)))
        print("{0}: {1} × {2}, written as RGB".format(name, image.width, image.height))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
