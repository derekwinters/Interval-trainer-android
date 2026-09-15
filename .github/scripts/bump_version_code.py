#!/usr/bin/env python3
"""Bumps `VERSION_CODE` on a release pull request (issue #12, `docs/spec/build.md` BUILD-015).

release-please's generic updater already rewrites `VERSION_NAME` in `gradle.properties`, but every
marker it understands — `x-release-please-version`, `-major`, `-minor`, `-patch` — substitutes a
piece of the *semver* value it just computed for the release. None of them is a freestanding
counter it can increment on its own behalf, so there is no marker that would make it bump
`VERSION_CODE`. Google Play requires `VERSION_CODE` to keep going up release after release
regardless of what the semver did (a patch release must still produce a code greater than the
minor release before it), so that bump needs a step of its own. That step lives in
`.github/workflows/release-please.yml`, after the `release-please-action` step that opens or
updates the release pull request; this module holds the one decision worth unit-testing out of it.

**The invariant this enforces.** `VERSION_CODE` always advances from the value the *base* branch
last released, never from whatever the release pull request's own branch already carries.
release-please re-runs this workflow every time a new commit lands on `main` while the release
pull request is still open, updating that same pull request in place rather than opening a new
one — so if the bump step instead incremented whatever the pull request branch already had, each
re-run would add another +1 on top of the last, and the final value merged would depend on how many
times the workflow happened to re-run before somebody merged it. Recomputing from the base branch's
last-released value every time makes a re-run that finds the pull request already at the right
value a no-op rather than a second bump — see `TwoSuccessiveReleasesTests` in
`.github/scripts/tests/test_bump_version_code.py`, which drives this through two releases in a row
end to end and checks each one's `VERSION_CODE` is strictly greater than the last, without
literally running release-please twice.
"""

import argparse
import re
import sys

_VERSION_CODE_LINE = re.compile(r"^VERSION_CODE=(\d+)[ \t]*$", re.MULTILINE)


class MissingVersionCode(ValueError):
    """A `gradle.properties` text has no bare `VERSION_CODE=<integer>` line."""


def read_version_code(properties_text):
    """The integer value of the `VERSION_CODE=` line in a `gradle.properties` file's text."""
    match = _VERSION_CODE_LINE.search(properties_text)
    if not match:
        raise MissingVersionCode("no 'VERSION_CODE=<integer>' line found")
    return int(match.group(1))


def next_version_code(base_properties_text):
    """One more than the value currently released on the base branch (BUILD-015)."""
    return read_version_code(base_properties_text) + 1


def set_version_code(properties_text, new_value):
    """`properties_text` with its `VERSION_CODE=` line rewritten to `new_value`.

    Raises `MissingVersionCode` rather than appending a line: a bump that cannot find the line
    it is meant to replace is a sign the file changed shape, not a reason to invent one.
    """
    if not _VERSION_CODE_LINE.search(properties_text):
        raise MissingVersionCode("no 'VERSION_CODE=<integer>' line found")
    return _VERSION_CODE_LINE.sub("VERSION_CODE={0}".format(new_value), properties_text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "base_file",
        help="gradle.properties as last released, read from the base branch")
    parser.add_argument(
        "target_file",
        help="gradle.properties on the release pull request, rewritten in place if it changes")
    args = parser.parse_args(argv)

    with open(args.base_file, encoding="utf-8") as handle:
        base_text = handle.read()
    with open(args.target_file, encoding="utf-8") as handle:
        target_text = handle.read()

    wanted = next_version_code(base_text)
    current = read_version_code(target_text)

    if current == wanted:
        print("VERSION_CODE is already {0}; nothing to do.".format(wanted))
        return 0

    with open(args.target_file, "w", encoding="utf-8") as handle:
        handle.write(set_version_code(target_text, wanted))
    print("VERSION_CODE bumped from {0} to {1}.".format(current, wanted))
    return 0


if __name__ == "__main__":
    sys.exit(main())
