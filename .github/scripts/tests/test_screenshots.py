"""Unit tests for the screenshot workflow (issue #161, `docs/spec/build.md` section 11).

Two kinds of test, the same split section 10's gate uses:

* `screenshot_png.py` is run directly. PNG is a published binary format, so these tests build real
  PNGs with every row filter the format defines and check that the script decodes them, re-encodes
  them as 8-bit RGB with no alpha (`BUILD-086`), and applies the Play Store size rules.
* `screenshots.yml`, `capture_screenshots.sh` and `propose_screenshots.sh` are read as text, the
  technique `test_closing_keyword_workflow.py` and `test_bump_version_code.py` already use, to pin
  the triggers, permissions, emulator, demo mode and pull-request handling (`BUILD-083`–`090`).

Standard library only: no PyYAML, no Pillow, no Android SDK, no emulator.
"""

import os
import re
import struct
import sys
import tempfile
import unittest
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = os.path.dirname(HERE)
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPTS))
sys.path.insert(0, SCRIPTS)

import screenshot_png as shots  # noqa: E402

WORKFLOW = os.path.join(REPO_ROOT, ".github", "workflows", "screenshots.yml")
CAPTURE_SCRIPT = os.path.join(SCRIPTS, "capture_screenshots.sh")
PROPOSE_SCRIPT = os.path.join(SCRIPTS, "propose_screenshots.sh")

EXPECTED_NAMES = (
    "01-first-run.png",
    "02-home.png",
    "03-editor.png",
    "04-running-paused.png",
    "05-summary.png",
    "06-settings.png",
)


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _read_bytes(path):
    with open(path, "rb") as handle:
        return handle.read()


# ---------------------------------------------------------------------------------------------
# A reference PNG writer, independent of the module under test, that can apply any row filter.


def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def _filter_row(kind, row, prev, bpp):
    out = bytearray(len(row))
    for i, x in enumerate(row):
        a = row[i - bpp] if i >= bpp else 0
        b = prev[i]
        c = prev[i - bpp] if i >= bpp else 0
        if kind == 0:
            pred = 0
        elif kind == 1:
            pred = a
        elif kind == 2:
            pred = b
        elif kind == 3:
            pred = (a + b) // 2
        else:
            pred = _paeth(a, b, c)
        out[i] = (x - pred) & 0xFF
    return bytes(out)


def _chunk(kind, data):
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def _png(width, height, pixels, channels=4, filters=(0,), bit_depth=8, color_type=None,
         interlace=0):
    """A PNG of raw `pixels` (rows concatenated), filtering row n with filters[n % len]."""
    if color_type is None:
        color_type = {3: 2, 4: 6}[channels]
    stride = width * channels
    raw = bytearray()
    prev = bytes(stride)
    for y in range(height):
        row = pixels[y * stride:(y + 1) * stride]
        kind = filters[y % len(filters)]
        raw.append(kind)
        raw += _filter_row(kind, row, prev, channels)
        prev = row
    ihdr = struct.pack(">IIBBBBB", width, height, bit_depth, color_type, 0, 0, interlace)
    return (b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr)
            + _chunk(b"IDAT", zlib.compress(bytes(raw))) + _chunk(b"IEND", b""))


def _rgba_pattern(width, height, alpha=255):
    out = bytearray()
    for y in range(height):
        for x in range(width):
            out += bytes(((x * 37 + y * 11) & 0xFF, (x * 5 + y * 71) & 0xFF,
                          (x * y + 13) & 0xFF, alpha))
    return bytes(out)


def _ihdr(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    length, kind = struct.unpack(">I4s", data[8:16])
    assert kind == b"IHDR"
    return struct.unpack(">IIBBBBB", data[16:16 + length])


def _chunk_kinds(data):
    kinds, pos = [], 8
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        kinds.append(kind)
        pos += 12 + length
    return kinds


class DecodeTests(unittest.TestCase):
    """BUILD-086: the script reads what `Bitmap.compress` writes, whichever row filters it used."""

    def test_every_row_filter_round_trips(self):
        pixels = _rgba_pattern(7, 10)
        for kind in range(5):
            with self.subTest(filter=kind):
                image = shots.decode(_png(7, 10, pixels, filters=(kind,)))
                self.assertEqual((image.width, image.height, image.channels), (7, 10, 4))
                self.assertEqual(image.pixels, pixels)

    def test_mixed_filters_round_trip(self):
        pixels = _rgba_pattern(9, 11)
        image = shots.decode(_png(9, 11, pixels, filters=(4, 0, 3, 1, 2)))
        self.assertEqual(image.pixels, pixels)

    def test_rgb_input_is_read_too(self):
        rgb = bytes(i & 0xFF for i in range(5 * 4 * 3))
        image = shots.decode(_png(5, 4, rgb, channels=3, filters=(1, 4)))
        self.assertEqual((image.channels, image.pixels), (3, rgb))

    def test_unsupported_formats_are_refused_not_guessed(self):
        rgb = bytes(4 * 4 * 3)
        cases = {
            "16-bit": _png(2, 2, bytes(2 * 2 * 8), channels=4, bit_depth=16, color_type=6),
            "interlaced": _png(4, 4, rgb, channels=3, interlace=1),
            "palette": _png(4, 4, bytes(16), channels=1, color_type=3),
            "not a png": b"GIF89a",
        }
        for name, data in cases.items():
            with self.subTest(case=name):
                with self.assertRaises(ValueError):
                    shots.decode(data)


class FlattenTests(unittest.TestCase):
    """BUILD-086: 8-bit RGB, no alpha channel, canonical bytes."""

    def test_output_is_eight_bit_rgb_with_no_alpha_chunk(self):
        out = shots.flatten(_png(6, 3, _rgba_pattern(6, 3), filters=(4,)))
        width, height, depth, color_type, _, _, interlace = _ihdr(out)
        self.assertEqual((width, height, depth, color_type, interlace), (6, 3, 8, 2, 0))
        self.assertNotIn(b"tRNS", _chunk_kinds(out))

    def test_opaque_pixels_are_kept_exactly(self):
        pixels = _rgba_pattern(6, 3)
        out = shots.decode(shots.flatten(_png(6, 3, pixels)))
        expected = bytearray()
        for i in range(0, len(pixels), 4):
            expected += pixels[i:i + 3]
        self.assertEqual(out.channels, 3)
        self.assertEqual(out.pixels, bytes(expected))

    def test_translucent_pixels_are_composited_over_black(self):
        out = shots.decode(shots.flatten(_png(1, 1, bytes((200, 100, 50, 128)))))
        self.assertEqual(out.pixels, bytes((100, 50, 25)))

    def test_identical_pixels_give_identical_bytes_whatever_the_input_encoding(self):
        """The determinism invariant: the comparison against docs/screenshots/ is byte-wise, so
        the encoding of a given image must not depend on how the device happened to encode it."""
        pixels = _rgba_pattern(8, 8)
        a = shots.flatten(_png(8, 8, pixels, filters=(0,)))
        b = shots.flatten(_png(8, 8, pixels, filters=(4, 3, 1)))
        self.assertEqual(a, b)


class PlayStoreLimitTests(unittest.TestCase):
    """BUILD-086: each side 320–3840 px, long side at most twice the short side."""

    def test_the_pixel_profile_passes(self):
        self.assertEqual(shots.play_store_problems(1080, 1920), [])

    def test_exactly_two_to_one_passes(self):
        self.assertEqual(shots.play_store_problems(960, 1920), [])

    def test_pixel_6_aspect_fails(self):
        self.assertTrue(shots.play_store_problems(1080, 2400))

    def test_too_small_fails(self):
        self.assertTrue(shots.play_store_problems(319, 400))

    def test_too_large_fails(self):
        self.assertTrue(shots.play_store_problems(3000, 3841))


class MainTests(unittest.TestCase):
    """BUILD-086: the directory step the workflow runs."""

    def _write_inputs(self, directory, names, width=40, height=70):
        for name in names:
            with open(os.path.join(directory, name), "wb") as handle:
                handle.write(_png(width, height, _rgba_pattern(width, height)))

    def _run(self, names, width=40, height=70, extra=()):
        with tempfile.TemporaryDirectory() as src, tempfile.TemporaryDirectory() as dst:
            self._write_inputs(src, names, width, height)
            for name in extra:
                with open(os.path.join(src, name), "w", encoding="utf-8") as handle:
                    handle.write("not an image")
            code = shots.main([src, dst], min_side=10)
            written = sorted(os.listdir(dst))
            colour_types = {n: _ihdr(_read_bytes(os.path.join(dst, n)))[3] for n in written}
        return code, written, colour_types

    def test_six_valid_captures_are_written_as_rgb(self):
        code, written, colour_types = self._run(EXPECTED_NAMES, extra=("instrument.txt",))
        self.assertEqual(code, 0)
        self.assertEqual(written, list(EXPECTED_NAMES))
        self.assertEqual(set(colour_types.values()), {2})

    def test_a_missing_capture_fails(self):
        code, _, _ = self._run(EXPECTED_NAMES[:-1])
        self.assertNotEqual(code, 0)

    def test_an_unexpected_capture_fails(self):
        code, _, _ = self._run(EXPECTED_NAMES + ("07-extra.png",))
        self.assertNotEqual(code, 0)

    def test_a_capture_outside_the_limits_fails(self):
        code, _, _ = self._run(EXPECTED_NAMES, width=40, height=90)
        self.assertNotEqual(code, 0)

    def test_the_expected_names_are_the_six_screens(self):
        self.assertEqual(tuple(shots.EXPECTED), EXPECTED_NAMES)


# ---------------------------------------------------------------------------------------------
# Workflow and script wiring, read as text.


def _job_body(text, job_name):
    """The YAML lines of one job under `jobs:`, up to the next key at the same indent."""
    lines = text.splitlines()
    start = indent = None
    for index, line in enumerate(lines):
        if line.strip() == "{0}:".format(job_name) and line.startswith("  ") and \
                not line.startswith("   "):
            start, indent = index, 2
            break
    if start is None:
        raise AssertionError("no '{0}' job in screenshots.yml".format(job_name))
    body = [lines[start]]
    for line in lines[start + 1:]:
        if line.strip() and not line.lstrip().startswith("#") and \
                (len(line) - len(line.lstrip())) <= indent:
            break
        body.append(line)
    return "\n".join(body)


def _top_level_block(text, key):
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.rstrip() == "{0}:".format(key):
            body = []
            for following in lines[index + 1:]:
                if following.strip() and not following.startswith(" ") and \
                        not following.startswith("#"):
                    break
                body.append(following)
            return "\n".join(body)
    raise AssertionError("no top-level '{0}:' in screenshots.yml".format(key))


def _without_comments(text):
    return "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("#"))


class WorkflowTriggerTests(unittest.TestCase):
    """BUILD-083."""

    def setUp(self):
        self.text = _read(WORKFLOW)
        self.on = _without_comments(_top_level_block(self.text, "on"))

    def test_push_to_main_with_exactly_the_screen_affecting_paths(self):
        self.assertRegex(self.on, r"push:\s*\n\s+branches:\s*\[main\]")
        paths = re.findall(r"^\s+- '([^']+)'\s*$", self.on, re.MULTILINE)
        self.assertEqual(sorted(paths), sorted([
            "app/src/main/**",
            "designsystem/src/main/**",
            "core/src/main/**",
            "database/src/**/main/**",
            "**/*.gradle.kts",
            "gradle/**",
            ".github/workflows/screenshots.yml",
        ]))

    def test_manual_dispatch(self):
        self.assertIn("workflow_dispatch:", self.on)

    def test_no_other_trigger(self):
        """No pull_request, schedule or release trigger: a test-only or docs-only change, or a
        pull request, never starts an emulator."""
        triggers = re.findall(r"^  ([a-z_]+):", self.on, re.MULTILINE)
        self.assertEqual(sorted(triggers), ["push", "workflow_dispatch"])


class WorkflowPermissionTests(unittest.TestCase):
    """BUILD-089."""

    def setUp(self):
        self.text = _read(WORKFLOW)

    def test_workflow_level_is_read_only(self):
        block = _without_comments(_top_level_block(self.text, "permissions")).strip()
        self.assertEqual(block, "contents: read")

    def test_capture_never_holds_a_write_permission(self):
        self.assertNotIn("write", _without_comments(_job_body(self.text, "capture")))

    def test_propose_alone_writes_and_only_on_main(self):
        body = _without_comments(_job_body(self.text, "propose"))
        self.assertRegex(body, r"permissions:\s*\n\s+contents: write\s*\n\s+pull-requests: write")
        self.assertIn("if: github.ref == 'refs/heads/main'", body)
        self.assertIn("needs: capture", body)

    def test_only_two_jobs(self):
        jobs = _top_level_block(self.text, "jobs")
        names = re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", jobs, re.MULTILINE)
        self.assertEqual(names, ["capture", "propose"])


class WorkflowCaptureTests(unittest.TestCase):
    """BUILD-084, BUILD-090."""

    def setUp(self):
        self.body = _without_comments(_job_body(_read(WORKFLOW), "capture"))

    def test_emulator_runner_is_pinned_and_configured(self):
        self.assertIn(
            "uses: reactivecircus/android-emulator-runner@"
            "a421e43855164a8197daf9d8d40fe71c6996bb0d # v2.38.0", self.body)
        for setting in ("api-level: 34", "target: google_apis", "arch: x86_64",
                        "profile: pixel\n"):
            self.assertIn(setting, self.body + "\n")
        self.assertNotIn("pixel_6", self.body)

    def test_runner_script_is_one_line_calling_the_capture_script(self):
        self.assertRegex(self.body,
                         r"\n\s+script: bash \.github/scripts/capture_screenshots\.sh \S+\n")

    def test_kvm_is_enabled_with_the_udev_rule(self):
        self.assertIn('KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"',
                      self.body)

    def test_the_test_apk_is_built(self):
        self.assertIn(":app:assembleDebugAndroidTest", self.body)

    def test_flatten_step_runs_the_script(self):
        self.assertIn("python3 .github/scripts/screenshot_png.py", self.body)

    def test_hashes_are_printed_and_the_artifact_uploaded(self):
        self.assertIn("sha256sum", self.body)
        self.assertRegex(self.body, r"name: screenshots\n")


class WorkflowPinTests(unittest.TestCase):
    """BUILD-043 for this workflow: every `uses:` is a full SHA with a version comment."""

    def test_every_uses_is_pinned(self):
        uses = re.findall(r"uses: (\S+)(.*)", _without_comments(_read(WORKFLOW)))
        self.assertTrue(uses)
        for ref, comment in uses:
            with self.subTest(uses=ref):
                self.assertRegex(ref, r"@[0-9a-f]{40}$")
                self.assertRegex(comment, r"^ # v\d")


class WorkflowTokenTests(unittest.TestCase):
    """BUILD-088."""

    def setUp(self):
        self.body = _without_comments(_job_body(_read(WORKFLOW), "propose"))

    def test_the_secret_is_used_with_a_github_token_fallback(self):
        expression = "${{ secrets.SCREENSHOTS_PR_TOKEN || github.token }}"
        self.assertGreaterEqual(self.body.count(expression), 2,
                                "both the checkout (for the push) and gh need the token")

    def test_a_missing_secret_is_announced(self):
        self.assertIn("::warning", self.body)
        self.assertIn("SCREENSHOTS_PR_TOKEN", self.body)

    def test_propose_runs_the_script(self):
        self.assertIn("bash .github/scripts/propose_screenshots.sh", self.body)


class CaptureScriptTests(unittest.TestCase):
    """BUILD-085."""

    def setUp(self):
        self.text = _without_comments(_read(CAPTURE_SCRIPT))

    def test_waits_for_boot_and_then_thirty_seconds(self):
        self.assertIn("sys.boot_completed", self.text)
        self.assertIn("sleep 30", self.text)

    def test_animations_are_off(self):
        for scale in ("window_animation_scale", "transition_animation_scale",
                      "animator_duration_scale"):
            self.assertIn(scale, self.text)
        self.assertIn('settings put global "$scale" 0', self.text)

    def test_demo_mode(self):
        self.assertIn("sysui_demo_allowed 1", self.text)
        self.assertIn("com.android.systemui.demo", self.text)
        for fragment in ("demo enter", "demo clock -e hhmm 1200",
                         "demo battery -e level 100 -e plugged false",
                         "demo notifications -e visible false"):
            self.assertIn(fragment, self.text)
        self.assertRegex(self.text, r"demo network -e wifi show -e level 4")
        self.assertRegex(self.text, r"demo network -e mobile show .*-e level 4")

    def test_demo_mode_comes_before_the_test_runs(self):
        self.assertLess(self.text.index("demo enter"), self.text.index("am instrument"))

    def test_installs_with_permissions_granted(self):
        self.assertGreaterEqual(self.text.count("install -r -g"), 2)

    def test_runs_the_screenshot_test_and_requires_ok(self):
        self.assertIn("am instrument -w", self.text)
        self.assertIn("ScreenshotTourTest", self.text)
        self.assertIn("OK (", self.text)

    def test_copies_the_pngs_out_with_run_as(self):
        self.assertIn("run-as", self.text)
        self.assertIn("files/screenshots", self.text)


class ProposeScriptTests(unittest.TestCase):
    """BUILD-087 and the invariant that the workflow never pushes to main or another PR."""

    def setUp(self):
        self.text = _without_comments(_read(PROPOSE_SCRIPT))

    def test_nothing_changed_means_nothing_opened(self):
        self.assertIn("git status --porcelain", self.text)
        status = self.text.index("git status --porcelain")
        self.assertLess(status, self.text.index("git commit"))
        self.assertLess(status, self.text.index("gh pr create"))

    def test_the_only_push_is_to_the_screenshots_branch(self):
        pushes = [line.strip() for line in self.text.splitlines() if "git push" in line]
        self.assertEqual(pushes, ['git push --force origin "HEAD:refs/heads/$branch"'])
        self.assertIn('branch="screenshots/update"', self.text)
        self.assertNotRegex(self.text, r"refs/heads/main|push[^\n]*\bmain\b")

    def test_only_the_screenshot_directory_is_committed(self):
        self.assertIn('git add -- "$dest"', self.text)
        self.assertIn('dest="docs/screenshots"', self.text)
        self.assertNotIn("git add -A", self.text)
        self.assertNotIn("git add .", self.text)

    def test_one_pull_request_titled_and_labelled(self):
        self.assertIn('title="docs: update screenshots"', self.text)
        self.assertIn('gh pr list --head "$branch"', self.text)
        self.assertIn("gh pr create", self.text)
        self.assertIn("gh pr edit", self.text)
        self.assertIn("--label no-closing-keyword", self.text)
        self.assertIn("--add-label no-closing-keyword", self.text)

    def test_the_commit_title_is_the_conventional_one(self):
        self.assertIn('git commit -m "$title"', self.text)


if __name__ == "__main__":
    unittest.main()
