"""Unit tests for the VERSION_CODE bump step (issue #12, `docs/spec/build.md` BUILD-015).

`bump_version_code.py` exists because release-please's generic updater has no marker for an
arbitrary, independently incrementing integer: `x-release-please-version`, `-major`, `-minor`
and `-patch` all substitute a piece of the semver value release-please just computed, and
`VERSION_CODE` needs to keep going up regardless of what that semver did (Google Play refuses any
upload whose version code is not strictly greater than the last one it accepted). These tests pin
the pure decisions (`read_version_code`, `next_version_code`, `set_version_code`), the CLI wiring
(`main`), and — the property this issue exists to guarantee — that two releases run back to back
each produce a strictly greater `VERSION_CODE` than the one before, without literally running
release-please twice. `WorkflowWiringTests` pins how `release-please.yml` calls this script.

They need no Android SDK, no release-please run, and no network — standard library only, like
`verify_release_signature.py`'s tests.
"""

import os
import re
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bump_version_code import (  # noqa: E402
    MissingVersionCode,
    main,
    next_version_code,
    read_version_code,
    set_version_code,
)

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
WORKFLOWS = os.path.join(REPO_ROOT, ".github", "workflows")
RELEASE_PLEASE_WORKFLOW = os.path.join(WORKFLOWS, "release-please.yml")

# A realistic gradle.properties, at the shape #9 committed: other Gradle settings above the
# version block, VERSION_NAME carrying its release-please marker comment, VERSION_CODE bare.
PROPERTIES_TEMPLATE = """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

android.useAndroidX=true
android.nonTransitiveRClass=true

kotlin.code.style=official

# The app's version, read by :app's build.gradle.kts (BUILD-013).
VERSION_NAME={version_name} # x-release-please-version
VERSION_CODE={version_code}
"""


def _properties(version_name="0.1.0", version_code=1):
    return PROPERTIES_TEMPLATE.format(version_name=version_name, version_code=version_code)


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _write(path, text):
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


class ReadVersionCodeTests(unittest.TestCase):
    """BUILD-015."""

    def test_reads_the_integer_off_the_line(self):
        self.assertEqual(read_version_code(_properties(version_code=7)), 7)

    def test_ignores_unrelated_lines_including_version_name(self):
        text = _properties(version_name="2.3.4", version_code=41)
        self.assertEqual(read_version_code(text), 41)

    def test_missing_line_raises(self):
        with self.assertRaises(MissingVersionCode):
            read_version_code("org.gradle.jvmargs=-Xmx2048m\n")

    def test_a_non_integer_value_is_not_matched(self):
        """A malformed line must fail loudly, not parse as some other number."""
        with self.assertRaises(MissingVersionCode):
            read_version_code("VERSION_CODE=abc\n")


class NextVersionCodeTests(unittest.TestCase):
    """BUILD-015: the value the bump step targets is always base-plus-one."""

    def test_one_more_than_the_base_branchs_value(self):
        self.assertEqual(next_version_code(_properties(version_code=1)), 2)
        self.assertEqual(next_version_code(_properties(version_code=41)), 42)


class SetVersionCodeTests(unittest.TestCase):
    """BUILD-015."""

    def test_replaces_only_the_version_code_line(self):
        before = _properties(version_name="0.1.0", version_code=1)
        after = set_version_code(before, 2)
        self.assertIn("VERSION_CODE=2", after)
        self.assertNotIn("VERSION_CODE=1\n", after)
        # Everything else — including VERSION_NAME and its marker — survives untouched.
        self.assertEqual(
            after.replace("VERSION_CODE=2", "VERSION_CODE=1"), before,
            "set_version_code changed something other than the VERSION_CODE line")

    def test_preserves_the_trailing_newline(self):
        before = _properties(version_code=1)
        self.assertTrue(before.endswith("\n"))
        after = set_version_code(before, 2)
        self.assertTrue(after.endswith("\n"))

    def test_missing_line_raises_rather_than_appending_one(self):
        with self.assertRaises(MissingVersionCode):
            set_version_code("org.gradle.jvmargs=-Xmx2048m\n", 2)


class MainCliTests(unittest.TestCase):
    """BUILD-015: the wiring `release-please.yml` actually invokes."""

    def test_bumps_the_target_file_from_the_base_files_value(self):
        with tempfile.TemporaryDirectory() as directory:
            base_path = os.path.join(directory, "base.properties")
            target_path = os.path.join(directory, "target.properties")
            _write(base_path, _properties(version_code=1))
            _write(target_path, _properties(version_code=1))

            code = main([base_path, target_path])

            self.assertEqual(code, 0)
            self.assertEqual(read_version_code(_read(target_path)), 2)

    def test_a_rerun_against_the_same_base_is_a_no_op(self):
        """The re-run the workflow makes on every new commit to an open release PR (BUILD-015)."""
        with tempfile.TemporaryDirectory() as directory:
            base_path = os.path.join(directory, "base.properties")
            target_path = os.path.join(directory, "target.properties")
            _write(base_path, _properties(version_code=1))
            _write(target_path, _properties(version_code=1))

            main([base_path, target_path])
            once_bumped = _read(target_path)

            # The base branch has not released anything new, so running again — as the
            # workflow does on every commit pushed to the still-open release PR — must not
            # bump a second time.
            main([base_path, target_path])

            self.assertEqual(_read(target_path), once_bumped)
            self.assertEqual(read_version_code(_read(target_path)), 2)

    def test_prints_what_it_did(self):
        with tempfile.TemporaryDirectory() as directory:
            base_path = os.path.join(directory, "base.properties")
            target_path = os.path.join(directory, "target.properties")
            _write(base_path, _properties(version_code=5))
            _write(target_path, _properties(version_code=5))

            from io import StringIO
            from unittest import mock

            with mock.patch("sys.stdout", new=StringIO()) as out:
                main([base_path, target_path])
            self.assertIn("6", out.getvalue())

            with mock.patch("sys.stdout", new=StringIO()) as out:
                main([base_path, target_path])
            self.assertIn("nothing to do", out.getvalue().lower())


class TwoSuccessiveReleasesTests(unittest.TestCase):
    """BUILD-015: the property issue #12 exists to guarantee.

    Two release pull requests in a row must each carry a VERSION_CODE strictly greater than
    the one before — the property Google Play enforces on every upload. This can't literally run
    release-please twice, so it drives the same script through the two steps that stand in for
    it: open a release pull request from a released base, merge it (the base branch's file
    becomes what the pull request had), then open the next one.
    """

    def test_each_release_in_a_row_bumps_strictly_higher_than_the_last(self):
        with tempfile.TemporaryDirectory() as directory:
            base_path = os.path.join(directory, "base.properties")
            pr_path = os.path.join(directory, "pr.properties")

            # Released state before this ever ran: VERSION_CODE=1.
            _write(base_path, _properties(version_name="0.1.0", version_code=1))

            # Release 1: release-please opens a pull request from the current base branch, and
            # the bump step below rewrites its VERSION_CODE.
            _write(pr_path, _properties(version_name="0.2.0", version_code=1))
            main([base_path, pr_path])
            first_release_code = read_version_code(_read(pr_path))
            self.assertGreater(first_release_code, 1)

            # That pull request merges: the base branch now carries what it had.
            _write(base_path, _read(pr_path))

            # Release 2: a fresh pull request opens from the new base.
            _write(pr_path, _properties(version_name="0.3.0", version_code=first_release_code))
            main([base_path, pr_path])
            second_release_code = read_version_code(_read(pr_path))

            self.assertGreater(
                second_release_code, first_release_code,
                "VERSION_CODE must strictly increase from one release to the next, or a Play "
                "Store upload for the second release is rejected")

    def test_extra_workflow_reruns_between_releases_do_not_break_the_ordering(self):
        """The workflow re-runs on every commit landed while a release PR sits open (BUILD-015)."""
        with tempfile.TemporaryDirectory() as directory:
            base_path = os.path.join(directory, "base.properties")
            pr_path = os.path.join(directory, "pr.properties")

            _write(base_path, _properties(version_code=1))
            _write(pr_path, _properties(version_code=1))

            # Three re-runs while the base branch hasn't moved — as if three commits landed on
            # the open release pull request before it merged.
            for _ in range(3):
                main([base_path, pr_path])
            first_release_code = read_version_code(_read(pr_path))
            self.assertEqual(first_release_code, 2, "extra re-runs must not stack up bumps")

            _write(base_path, _read(pr_path))
            _write(pr_path, _properties(version_code=first_release_code))
            main([base_path, pr_path])
            second_release_code = read_version_code(_read(pr_path))

            self.assertGreater(second_release_code, first_release_code)


class StandardLibraryOnlyTests(unittest.TestCase):
    """The bump step runs on a bare runner with no pip install, like SIGN-050's gate."""

    STDLIB = {"argparse", "re", "sys"}

    def test_the_script_imports_only_the_standard_library(self):
        source = _read(os.path.join(os.path.dirname(HERE), "bump_version_code.py"))
        imported = set(re.findall(r"^\s*(?:import|from)\s+([A-Za-z_][A-Za-z0-9_.]*)",
                                  source, re.MULTILINE))
        self.assertTrue(imported)
        self.assertEqual(
            sorted(name for name in imported if name.split(".")[0] not in self.STDLIB), [])


class WorkflowWiringTests(unittest.TestCase):
    """BUILD-015: `release-please.yml` calls this script, and only when there is a PR to bump."""

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
        self.assertIsNotNone(start, "no '{0}' step in release-please.yml".format(step_name))

        body = [lines[start]]
        for line in lines[start + 1:]:
            if line.strip().startswith("- ") and (len(line) - len(line.lstrip())) == indent:
                break
            body.append(line)
        return "\n".join(body)

    def test_the_release_please_step_is_named_so_later_steps_can_read_its_output(self):
        text = _read(RELEASE_PLEASE_WORKFLOW)
        self.assertRegex(text, r"uses:\s*googleapis/release-please-action@[0-9a-f]{40}")
        self.assertRegex(text, r"id:\s*release\b")

    def test_the_bump_step_runs_only_when_a_release_pull_request_exists(self):
        body = self._step_body(
            _read(RELEASE_PLEASE_WORKFLOW), "Bump VERSION_CODE on the release pull request")
        self.assertIn("steps.release.outputs.pr", body)

    def test_the_bump_step_calls_this_script_against_the_base_branchs_file(self):
        body = self._step_body(
            _read(RELEASE_PLEASE_WORKFLOW), "Bump VERSION_CODE on the release pull request")
        self.assertIn("bump_version_code.py", body)
        self.assertIn("baseBranchName", body)
        self.assertIn("headBranchName", body)

    def test_the_checkout_step_uses_the_release_pull_requests_branch(self):
        body = self._step_body(_read(RELEASE_PLEASE_WORKFLOW), "Check out the release pull request")
        self.assertRegex(body, r"uses:\s*actions/checkout@[0-9a-f]{40}")
        self.assertIn("headBranchName", body)
        self.assertIn("steps.release.outputs.pr", body)

    def _env_block(self, step_body):
        """The YAML lines of a step's `env:` block alone, up to `run:`/`with:` at its indent."""
        lines = step_body.splitlines()
        start = None
        indent = None
        for index, line in enumerate(lines):
            if line.strip() == "env:":
                start = index
                indent = len(line) - len(line.lstrip())
                break
        self.assertIsNotNone(start, "no 'env:' block in this step")
        block = [lines[start]]
        for line in lines[start + 1:]:
            stripped = line.strip()
            if stripped and (len(line) - len(line.lstrip())) <= indent:
                break
            block.append(line)
        return "\n".join(block)

    def test_the_bump_steps_env_block_never_calls_fromjson(self):
        """Issue #114: GitHub Actions template-compiles a step's `env:` expressions before
        checking that step's `if:`, so a `fromJSON()` call there over an empty
        `steps.release.outputs.pr` (the ordinary state right after merging a release) fails the
        whole job regardless of the `if:` gate. The JSON must never be parsed in `env:`."""
        body = self._step_body(
            _read(RELEASE_PLEASE_WORKFLOW), "Bump VERSION_CODE on the release pull request")
        env_block = self._env_block(body)
        self.assertNotIn("fromJSON(", env_block)

    def test_the_bump_step_parses_the_pr_json_inside_its_run_script(self):
        """The `baseBranchName`/`headBranchName` JSON is read in `run:`, where it is only
        evaluated once the `if:` gate has already been checked (issue #114)."""
        body = self._step_body(
            _read(RELEASE_PLEASE_WORKFLOW), "Bump VERSION_CODE on the release pull request")
        run_index = body.splitlines().index("        run: |") if "        run: |" in body else -1
        self.assertNotEqual(run_index, -1, "no 'run: |' block in the bump step")
        run_block = "\n".join(body.splitlines()[run_index:])
        self.assertIn("jq", run_block)
        self.assertIn("baseBranchName", run_block)
        self.assertIn("headBranchName", run_block)


class BackfillDispatchTests(unittest.TestCase):
    """BUILD-066/067: a manual `workflow_dispatch` path that backfills a tag's missing signed
    release APK without release-please needing to have just created that release in this run
    (issue #114's follow-up: the v0.2.0 release shipped with no APK attached)."""

    def _job_body(self, text, job_name):
        lines = text.splitlines()
        start = None
        indent = None
        pattern = "{0}:".format(job_name)
        for index, line in enumerate(lines):
            if line.strip() == pattern:
                start = index
                indent = len(line) - len(line.lstrip())
                break
        self.assertIsNotNone(start, "no '{0}' job in release-please.yml".format(job_name))
        body = [lines[start]]
        for line in lines[start + 1:]:
            if line.strip() and (len(line) - len(line.lstrip())) <= indent:
                break
            body.append(line)
        return "\n".join(body)

    def test_workflow_dispatch_declares_a_backfill_tag_input(self):
        text = _read(RELEASE_PLEASE_WORKFLOW)
        self.assertRegex(text, r"workflow_dispatch:\s*\n\s*inputs:\s*\n\s*backfill_tag:")

    def test_the_backfill_job_is_dispatch_only_and_independent_of_release_please(self):
        text = _read(RELEASE_PLEASE_WORKFLOW)
        body = self._job_body(text, "backfill-release-apk")
        self.assertNotIn("needs:", body, "the backfill job must not depend on release-please")
        self.assertIn("workflow_dispatch", body)
        self.assertIn("backfill_tag", body)
        # It builds and verifies a signed APK the same way build-and-attach does.
        self.assertIn("assembleRelease", body)
        self.assertIn("verify_release_signature.py", body)
        self.assertIn("gh release upload", body)

    def test_the_normal_release_please_job_is_skipped_during_a_backfill_dispatch(self):
        text = _read(RELEASE_PLEASE_WORKFLOW)
        body = self._job_body(text, "release-please")
        self.assertIn("backfill_tag", body)


if __name__ == "__main__":
    unittest.main()
