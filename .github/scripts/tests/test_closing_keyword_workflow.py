"""Unit tests for the closing-keyword workflow's release-please skip (issue #117, BUILD-045).

`closing-keyword.yml` calls a shared, ai-sdlc-provided action that requires a plain
`closes #n`/`fixes #n`/`resolves #n` closing keyword in a pull request's body. release-please's
own release pull requests aggregate many already-closed issues into one changelog body and never
themselves close anything new, so their body can only ever carry a compare-log bullet's
markdown-linked `closes [#n](...)`, which never matches that pattern — the gate was structurally
unable to pass on such a pull request. Before this, a human had to notice the failing check on
every release and apply this repository's own `no-closing-keyword` label by hand (#92, #116).
release-please-action always applies the `autorelease: pending` label to its own release pull
requests, so the job is now skipped automatically whenever that label is present.

Standard library only: no PyYAML, no network, no Android SDK — this reads the workflow file as
text, the same technique `test_bump_version_code.py`'s `WorkflowWiringTests` uses for
`release-please.yml`.
"""

import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
CLOSING_KEYWORD_WORKFLOW = os.path.join(REPO_ROOT, ".github", "workflows", "closing-keyword.yml")

EXPECTED_IF = (
    'if: "${{ !contains(github.event.pull_request.labels.*.name, '
    "'autorelease: pending') }}\""
)


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


class ClosingKeywordWorkflowWiringTests(unittest.TestCase):
    """BUILD-045."""

    def _job_body(self, text, job_name):
        """The YAML lines of one named job, up to the next job at its indent."""
        lines = text.splitlines()
        start = None
        indent = None
        pattern = "{0}:".format(job_name)
        for index, line in enumerate(lines):
            if line.strip() == pattern:
                start = index
                indent = len(line) - len(line.lstrip())
                break
        self.assertIsNotNone(start, "no '{0}' job in closing-keyword.yml".format(job_name))
        body = [lines[start]]
        for line in lines[start + 1:]:
            if line.strip() and (len(line) - len(line.lstrip())) <= indent:
                break
            body.append(line)
        return "\n".join(body)

    def test_the_job_is_skipped_for_release_please_pull_requests(self):
        """The job's own `if:` excludes any pull request already labelled
        `autorelease: pending` — release-please-action applies this label to its own release
        pull requests from the moment it opens or updates them, before this check ever runs."""
        body = self._job_body(_read(CLOSING_KEYWORD_WORKFLOW), "closing-keyword")
        self.assertIn(EXPECTED_IF, body)

    def test_the_gate_still_runs_for_an_ordinary_pull_request(self):
        """The condition is a negation, not an unconditional skip: it must actually reference
        `contains(...)`, not merely mention the label somewhere, so a pull request without the
        label still runs the shared action exactly as before."""
        body = self._job_body(_read(CLOSING_KEYWORD_WORKFLOW), "closing-keyword")
        self.assertIn("!contains(", body,
                       "the condition must skip ONLY when the label is present, not the reverse")

    def test_the_step_calling_the_shared_action_is_unchanged(self):
        """This fix is local to the caller workflow; it must not touch the pinned shared action
        it calls."""
        text = _read(CLOSING_KEYWORD_WORKFLOW)
        self.assertIn(
            "derekwinters/ai-sdlc/.github/actions/closing-keyword@"
            "cde337066fdce3b688d2f9cd83a992f048278784",
            text)


if __name__ == "__main__":
    unittest.main()
